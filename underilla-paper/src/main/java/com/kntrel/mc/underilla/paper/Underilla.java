package com.kntrel.mc.underilla.paper;

import com.kntrel.mc.underilla.core.generation.GenerationContext;
import com.kntrel.mc.underilla.core.generation.PatcherFactory;
import com.kntrel.mc.underilla.core.generation.PatchingPlan;
import com.kntrel.mc.underilla.core.profiling.Instrumenter;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.paper.cleaning.CleanBlocksTask;
import com.kntrel.mc.underilla.paper.cleaning.CleanEntitiesTask;
import com.kntrel.mc.underilla.paper.cleaning.FollowableProgressTask;
import com.kntrel.mc.underilla.paper.generation.GeneratorAccessor;
import com.kntrel.mc.underilla.paper.generation.UnderillaChunkGenerator;
import com.kntrel.mc.underilla.paper.impl.BukkitBlockFactory;
import com.kntrel.mc.underilla.paper.impl.BukkitWorldReader;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.BooleanKeys;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.IntegerKeys;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.StringKeys;
import com.kntrel.mc.underilla.paper.listener.ChunkGeneratedListener;
import com.kntrel.mc.underilla.paper.listener.ChunkProfilingListener;
import com.kntrel.mc.underilla.paper.listener.StructureEventListener;
import com.kntrel.mc.underilla.paper.listener.WorldListener;
import com.kntrel.mc.underilla.paper.preparing.ServerSetup;
import com.kntrel.mc.underilla.paper.profiling.ChunkGenerationProfiler;
import com.kntrel.mc.underilla.paper.profiling.JsonStatsRecorder;
import com.kntrel.mc.underilla.paper.selector.Selector;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.ChunkyProvider;
import org.popcraft.chunky.api.event.task.GenerationProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Underilla extends JavaPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(Underilla.class);
    private UnderillaConfig underillaConfig;
    private GenerationContext generationContext;
    private WorldReader worldSurfaceReader;
    private @Nullable WorldReader worldCavesReader;
    public static final int CHUNK_SIZE = 16;
    public static final int REGION_SIZE = 512;
    public static final int BIOME_AREA_SIZE = 4;
    public static final long MS_PER_SECOND = 1000;
    private static final String TODO = "todo";
    private static final String DOING = "doing";
    private static final String DONE = "done";
    private static final String FAILED = "failed";
    private CleanBlocksTask cleanBlocksTask;
    private CleanEntitiesTask cleanEntitiesTask;
    private StructureEventListener structureEventListener;
    private JsonStatsRecorder profilingRecorder;
    private Instrumenter instrumenter;
    private ChunkGenerationProfiler chunkGenerationProfiler;
    private final Map<String, UnderillaChunkGenerator> worldGenerators = new ConcurrentHashMap<>();

    private Function<org.bukkit.block.Biome, org.bukkit.block.Biome> endBiomeTransformer;
    private Consumer<Block> endBlockTransformer;
    private Consumer<Entity> endEntityTransformer;
    private Map<StringKeys, Runnable> endTaskActions = new EnumMap<>(StringKeys.class);

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (allStepsDone()) {
            LOGGER.info("Use the out of the surface world generator instead of Underilla because we have done all generation & cleaning steps.");
            return GeneratorAccessor.getOutOfTheSurfaceWorldGenerator(worldName, id);
        }
        if (this.worldSurfaceReader == null) {
            LOGGER.warn("No surface region directory at '{}' found", getUnderillaConfig().getSurfaceRegionPath());
            return super.getDefaultWorldGenerator(worldName, id);
        }
        ChunkGenerator outOfTheSurfaceWorldGenerator = GeneratorAccessor.getOutOfTheSurfaceWorldGenerator(worldName, id);
        WorldReader cavesBlocksWorld = getUnderillaConfig().getBoolean(BooleanKeys.TRANSFER_BLOCKS_FROM_CAVES_WORLD)
                ? worldCavesReader
                : null;
        String configuredStrategy = getUnderillaConfig().getString(StringKeys.STRATEGY);
        if (configuredStrategy == null) {
            throw new IllegalArgumentException("Patch strategy must be configured");
        }
        PatchingPlan patchingPlan = switch (configuredStrategy.trim().toUpperCase(Locale.ROOT)) {
            case "SURFACE" -> PatcherFactory.surface(worldSurfaceReader, cavesBlocksWorld, generationContext);
            case "ABSOLUTE" -> PatcherFactory.absolute(worldSurfaceReader, cavesBlocksWorld, generationContext);
            case "NONE" -> PatcherFactory.none(worldSurfaceReader, cavesBlocksWorld, generationContext);
            default -> throw new IllegalArgumentException("Unknown patch strategy: " + configuredStrategy);
        };
        LOGGER.info("Using Underilla as main world generator (with {} as outOfTheSurfaceWorldGenerator)!",
                outOfTheSurfaceWorldGenerator);
        UnderillaChunkGenerator worldGenerator = new UnderillaChunkGenerator(this.worldSurfaceReader,
                outOfTheSurfaceWorldGenerator, patchingPlan, generationContext, instrumenter, chunkGenerationProfiler);
        this.worldGenerators.put(worldName, worldGenerator);
        return worldGenerator;
    }

    @Override
    public void onEnable() {
        new Metrics(this, 24393);

        // save default config
        this.saveDefaultConfig();
        reloadConfig();

        runStepsOnEnabled();

        if (!allStepsDone()) {
            this.profilingRecorder = new JsonStatsRecorder(getDataFolder().toPath().resolve("metrics.json"));
            this.instrumenter = new Instrumenter(profilingRecorder);
            this.chunkGenerationProfiler = new ChunkGenerationProfiler(instrumenter);
            LOGGER.info("Profiling metrics will be written to '{}'", profilingRecorder.outputPath());
            generationContext = new GenerationContext(getUnderillaConfig(), new BukkitBlockFactory());
            // Loading reference world
            File surfaceRegionDirectory = getUnderillaConfig().getSurfaceRegionPath().toFile();
            try {
                this.worldSurfaceReader = new BukkitWorldReader(surfaceRegionDirectory, getUnderillaConfig().cacheSize());
                LOGGER.info("Surface region directory '{}' found.", surfaceRegionDirectory);
            } catch (NoSuchFieldException e) {
                LOGGER.warn("No surface region directory at '{}' found", surfaceRegionDirectory, e);
            }
            // Loading caves world if we should use it.
            if (getUnderillaConfig().getBoolean(BooleanKeys.TRANSFER_BLOCKS_FROM_CAVES_WORLD)
                    || getUnderillaConfig().getBoolean(BooleanKeys.TRANSFER_BIOMES_FROM_CAVES_WORLD)) {
                try {
                    LOGGER.info("Loading caves world");
                    File cavesRegionDirectory = getUnderillaConfig().getCavesRegionPath().toFile();
                    this.worldCavesReader = new BukkitWorldReader(cavesRegionDirectory, getUnderillaConfig().cacheSize());
                } catch (NoSuchFieldException e) {
                    LOGGER.warn("No caves region directory at '{}' found", getUnderillaConfig().getCavesRegionPath(), e);
                }
            }

            // Registering listeners
            if (getUnderillaConfig().getBoolean(BooleanKeys.STRUCTURES_ENABLED)) {
                structureEventListener = new StructureEventListener();
                this.getServer().getPluginManager().registerEvents(structureEventListener, this);
            }
            this.getServer().getPluginManager().registerEvents(new WorldListener(), this);
            this.getServer().getPluginManager().registerEvents(new ChunkProfilingListener(chunkGenerationProfiler), this);

            if (getUnderillaConfig().getBoolean(BooleanKeys.CLEAN_ENTITIES_ENABLED)) {
                LOGGER.info("Cleaning listener for blocks and/or entities have been init.");
                this.getServer().getPluginManager().registerEvents(new ChunkGeneratedListener(), this);
            }
        }
    }

    @Override
    public void onDisable() {
        try {
            stopTasks();
            for (Map.Entry<String, UnderillaChunkGenerator> worldGenerator : worldGenerators.entrySet()) {
                Map<String, Long> biomesPlaced = worldGenerator.getValue().getBiomesPlaced();
                LOGGER.info("Map of biomes placed in world '{}': {}", worldGenerator.getKey(), biomesPlaced.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .map(entry -> entry.getKey() + ": " + entry.getValue()).reduce((a, b) -> a + ", " + b).orElse(""));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to stop tasks or print biomes placed", e);
        } finally {
            closeProfilingRecorder();
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (underillaConfig == null) {
            underillaConfig = new UnderillaConfig(getConfig());
        } else {
            underillaConfig.reload(getConfig());
        }
        if (!allStepsDone()) {
            LOGGER.info("Config reloaded with values: {}", underillaConfig);
        }
    }

    public static Underilla getInstance() { return getPlugin(Underilla.class); }
    public static UnderillaConfig getUnderillaConfig() { return getInstance().underillaConfig; }
    public static boolean isDebugEnabled() { return getInstance().getConfig().getBoolean("debug", false); }

    private void closeProfilingRecorder() {
        if (profilingRecorder == null) {
            return;
        }
        chunkGenerationProfiler.discardAll();
        try {
            profilingRecorder.close();
        } catch (IOException e) {
            LOGGER.warn("Could not write the final profiling snapshot to {}", profilingRecorder.outputPath(), e);
        }
    }


    private void runStepsOnEnabled() {
        boolean needARestart = false;

        if (getUnderillaConfig().getString(StringKeys.STEP_DOWNLOAD_DEPENDENCY_PLUGINS).equals(TODO)) {
            needARestart = ServerSetup.downloadNeededDependencies() || needARestart;
        }
        if (getUnderillaConfig().getString(StringKeys.STEP_SETUP_PAPER_FOR_QUICK_GENERATION).equals(TODO)) {
            needARestart = ServerSetup.setupPaperWorkerthreads() || needARestart;
        }
        if (getUnderillaConfig().getString(StringKeys.STEP_SET_UNDERILLA_AS_WORLD_GENERATOR).equals(TODO)) {
            needARestart = ServerSetup.setupBukkitWorldGenerator() || needARestart;
        }
        if (needARestart) {
            LOGGER.info("Underilla have done pre generation steps. Restarting server to apply changes.");
            Bukkit.shutdownMessage();
            // Bukkit.shutdown(); // It doesn't work before the world is loaded.
            Bukkit.getServer().restart();
            // System.exit(0);
        }
    }
    public void runNextStepsAfterWorldInit() {
        if (getUnderillaConfig().getString(StringKeys.STEP_UNDERILLA_GENERATION).equals(TODO)) {
            runChunky();
        } else if (getUnderillaConfig().getString(StringKeys.STEP_UNDERILLA_GENERATION).equals(DOING)) {
            restartChunky();
        } else if (getUnderillaConfig().getString(StringKeys.STEP_CLEANING_BLOCKS).equals(TODO)) {
            runCleanBlocks();
        } else if (getUnderillaConfig().getString(StringKeys.STEP_CLEANING_BLOCKS).equals(DOING)) {
            restartCleanBlocks();
        } else if (getUnderillaConfig().getString(StringKeys.STEP_CLEANING_ENTITIES).equals(TODO)) {
            runCleanEntities();
        } else if (getUnderillaConfig().getString(StringKeys.STEP_CLEANING_ENTITIES).equals(DOING)) {
            restartCleanEntities();
        }
    }
    public boolean allStepsDone() {
        return getUnderillaConfig().getString(StringKeys.STEP_UNDERILLA_GENERATION).equals(DONE)
                && getUnderillaConfig().getString(StringKeys.STEP_CLEANING_BLOCKS).equals(DONE)
                && getUnderillaConfig().getString(StringKeys.STEP_CLEANING_ENTITIES).equals(DONE);
    }
    public void validateTask(StringKeys taskKey, boolean done) {
        getUnderillaConfig().saveNewValue(taskKey, done ? DONE : FAILED);
        if (done && endTaskActions.containsKey(taskKey)) {
            LOGGER.info("Running post action for task {}", taskKey);
            endTaskActions.get(taskKey).run();
        }
        runNextStepsAfterWorldInit();
    }
    public void validateInitServerTask(StringKeys taskKey, boolean done) {
        getUnderillaConfig().saveNewValue(taskKey, done ? DONE : FAILED);
    }
    public void validateTask(StringKeys taskKey) { validateTask(taskKey, true); }
    public void validateInitServerTask(StringKeys taskKey) { validateInitServerTask(taskKey, true); }
    public void setToDoingTask(StringKeys taskKey) { getUnderillaConfig().saveNewValue(taskKey, DOING); }

    // Custom actions -------------------------------------------------------------------------------------------------
    public Function<org.bukkit.block.Biome, org.bukkit.block.Biome> getEndBiomeTransformer() { return endBiomeTransformer; }
    public void setEndBiomeTransformer(Function<org.bukkit.block.Biome, org.bukkit.block.Biome> endBiomeTransformer) {
        this.endBiomeTransformer = endBiomeTransformer;
    }
    public boolean hasEndBiomeTransformer() { return endBiomeTransformer != null; }
    public Consumer<Block> getEndBlockTransformer() { return endBlockTransformer; }
    public void setEndBlockTransformer(Consumer<Block> endBlockTransformer) { this.endBlockTransformer = endBlockTransformer; }
    public boolean hasEndBlockTransformer() { return endBlockTransformer != null; }
    public Consumer<Entity> getEndEntityTransformer() { return endEntityTransformer; }
    public void setEndEntityTransformer(Consumer<Entity> endEntityTransformer) { this.endEntityTransformer = endEntityTransformer; }
    public boolean hasEndEntityTransformer() { return endEntityTransformer != null; }
    public void setPostTaskAction(Runnable action, StringKeys taskKey) { endTaskActions.put(taskKey, action); }

    // run tasks ------------------------------------------------------------------------------------------------------
    private void runChunky(boolean restart) {
        Chunky chunky = ChunkyProvider.get();
        // startTask(String world, String shape, double centerX, double centerZ, double radiusX, double radiusZ, String pattern)
        String worldName = getUnderillaConfig().getString(StringKeys.FINAL_WORLD_NAME);
        int minX = getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MIN_X);
        int minZ = getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MIN_Z);
        int maxX = getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MAX_X);
        int maxZ = getUnderillaConfig().getInt(IntegerKeys.GENERATION_AREA_MAX_Z);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int radiusX = (maxX - minX) / 2;
        int radiusZ = (maxZ - minZ) / 2;
        final long startTime = System.currentTimeMillis();
        // Set chunky silent
        chunky.getConfig().setSilent(true);

        chunky.getApi().onGenerationProgress(new Consumer<GenerationProgressEvent>() {
            long printTime = 0;
            long printTimeEachXMs = Underilla.MS_PER_SECOND * getUnderillaConfig().getInt(IntegerKeys.PRINT_PROGRESS_EVERY_X_SECONDS);
            @Override
            public void accept(GenerationProgressEvent generationProgressEvent) {
                if (printTime + printTimeEachXMs < System.currentTimeMillis()) {
                    printTime = System.currentTimeMillis();
                    FollowableProgressTask.printProgress(generationProgressEvent.chunks(), startTime,
                            generationProgressEvent.progress() / 100, 1, 1, "Rate: " + (int) (generationProgressEvent.rate())
                                    + ", Current: " + generationProgressEvent.x() + " " + generationProgressEvent.z());
                }
            }
        });

        chunky.getApi().onGenerationComplete(generationCompleteEvent -> {
            LOGGER.info("Chunky task for world {} has finished", worldName);
            if (structureEventListener != null) {
                LOGGER.info("Structure generation: {}", structureEventListener.getStructureCount());
            }
            validateTask(StringKeys.STEP_UNDERILLA_GENERATION);
        });

        boolean worked;
        if (restart) {
            worked = chunky.getApi().continueTask(worldName);
        } else {
            worked = chunky.getApi().startTask(worldName, "rectangle", centerX, centerZ, radiusX, radiusZ, "region");
            setToDoingTask(StringKeys.STEP_UNDERILLA_GENERATION);
        }
        if (worked) {
            LOGGER.info("Started Chunky task for world {}", worldName);
        } else {
            LOGGER.warn("Failed to start Chunky task for world {}", worldName);
            validateTask(StringKeys.STEP_UNDERILLA_GENERATION, false);
        }
    }
    private void runChunky() { runChunky(false); }
    private void runCleanBlocks(Selector selector) {
        setToDoingTask(StringKeys.STEP_CLEANING_BLOCKS);
        LOGGER.info("Starting clean blocks task");
        cleanBlocksTask = new CleanBlocksTask(2, 3, selector);
        cleanBlocksTask.run();
    }
    private void runCleanBlocks() { runCleanBlocks(getUnderillaConfig().getSelector()); }
    private void runCleanEntities(Selector selector) {
        setToDoingTask(StringKeys.STEP_CLEANING_ENTITIES);
        LOGGER.info("Starting clean entities task");
        cleanEntitiesTask = new CleanEntitiesTask(3, 3);
        cleanEntitiesTask.run();
    }
    private void runCleanEntities() { runCleanEntities(getUnderillaConfig().getSelector()); }

    // stop tasks -----------------------------------------------------------------------------------------------------
    private void stopTasks() {
        if (cleanBlocksTask != null && getUnderillaConfig().getString(StringKeys.STEP_CLEANING_BLOCKS).equals(DOING)) {
            Selector selector = cleanBlocksTask.stop();
            selector.saveIn("cleanBlocksTask");
        }
        if (cleanEntitiesTask != null && getUnderillaConfig().getString(StringKeys.STEP_CLEANING_ENTITIES).equals(DOING)) {
            Selector selector = cleanEntitiesTask.stop();
            selector.saveIn("cleanEntitiesTask");
        }
    }

    // restart tasks --------------------------------------------------------------------------------------------------
    private void restartChunky() {
        LOGGER.info("Restarting Chunky task");
        runChunky(true);
    }
    private void restartCleanBlocks() {
        LOGGER.info("Restarting clean blocks task");
        try {
            runCleanBlocks(Selector.loadFrom("cleanBlocksTask"));
        } catch (Exception e) {
            LOGGER.warn("Tasks can't be restarted from last state. Restarting from the beginning.", e);
            runCleanBlocks();
        }
    }
    private void restartCleanEntities() {
        LOGGER.info("Restarting clean entities task");
        try {
            runCleanEntities(Selector.loadFrom("cleanEntitiesTask"));
        } catch (Exception e) {
            LOGGER.warn("Tasks can't be restarted from last state. Restarting from the beginning.", e);
            runCleanEntities();
        }
    }
}
