package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.generation.NoodleCavesPolicy;
import com.kntrel.mc.underilla.core.generation.UnderillaFactory;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestDiskWorldReader;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import picocli.CommandLine;
import picocli.CommandLine.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

@Command(
        name = "inspect"
)
public class InspectorCLI implements Callable<Integer> {

    private static final int MINIMUM_Y = -64;
    private static final int MAXIMUM_Y = 320;
    private static final ID AIR_ID = new ID("minecraft", "air");
    private static final Set<ID> CAVE_BIOMES = Set.of(
            ID.of("minecraft:deep_dark"),
            ID.of("minecraft:dripstone_caves"),
            ID.of("minecraft:lush_caves"));

    @Parameters(
            index = "0",
            paramLabel = "mcaFile",
            description = "Path to the .mca file to use as reference",
            defaultValue = "./src/test/resources/mca/surface.mca"
    )
    private File file;

    @Option(
            names = "--strategy",
            description = "Merge strategy to use: none, absolute, surface",
            paramLabel = "<none|absolute|surface>",
            defaultValue = "surface",
            converter = Strategy.Converter.class
    )
    private Strategy strategy;

    @Option(
            names = "--surface-depth",
            description = "How many surface-world blocks to preserve below the reference surface",
            defaultValue = "6"
    )
    private int surfaceDepth;

    @Option(
            names = { "--output", "-o" },
            description = "Path of to which to write the output images",
            defaultValue = "./build/underilla-inspector"
    )
    private Path outputPath;

    @Option(
            names = { "--seed", "-s" },
            description = "The seed of the vanilla base world",
            defaultValue = "42"
    )
    private long seed;

    @Option(
            names = { "--chunk-lenght", "-cl" },
            description = "The length in chunks of the inspected section",
            defaultValue = "32"
    )
    private int chunkSize;

    @Option(
            names = { "--z-slice", "-z" },
            description = "Local Z coordinate of the vertical slice to inspect",
            defaultValue = "0"
    )
    private int zSlice;

    @Option(
            names = "--surface-fill",
            negatable = true,
            defaultValue = "true",
            fallbackValue = "true"
    )
    private boolean surfaceFill = true;

    @Option(
            names = "--cavers",
            negatable = true,
            defaultValue = "true",
            fallbackValue = "true"
    )
    private boolean cavers = true;

    @Option(
            names = "--features",
            negatable = true,
            defaultValue = "true",
            fallbackValue = "true"
    )
    private boolean features = true;

    @Option(
            names = "--surface-cavers",
            negatable = true,
            defaultValue = "true",
            fallbackValue = "true"
    )
    private boolean surfaceCavers = true;

    @Option(
            names = "--clean-up",
            negatable = true,
            defaultValue = "true",
            fallbackValue = "true"
    )
    private boolean cleanUp = true;

    @Option(
            names = "--show-biomes",
            description = "Overlay biome colors on the rendered inspector images"
    )
    private boolean showBiomes;

    @Override
    public Integer call() {
        try {
            this.callInner();
        } catch (Throwable e) {
            e.printStackTrace();
            return 1;
        }
        return 0;
    }

    private void callInner() {

        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (zSlice < 0 || zSlice >= GenerationConstants.CHUNK_SIZE) {
            throw new IllegalArgumentException("zSlice must be between 0 and "
                    + (GenerationConstants.CHUNK_SIZE - 1));
        }

        WorldReader reference = referenceWorld();

        WorldGenerationPlan generationPlan = plan(reference);
        StageImageSet imageSet = new StageImageSet(showBiomes);
        Set<InspectionStage> points = enabledPoints(generationPlan);
        InspectionRegion region = InspectionRegion.zSlice(
                zSlice,
                0,
                chunkSize,
                MINIMUM_Y,
                MAXIMUM_Y);

        try (ExecutorService renderExecutor = Executors.newFixedThreadPool(
                Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors())))) {
            Inspector inspection = new Inspector(
                    region,
                    points,
                    renderExecutor,
                    imageSet::stage);
            InspectorGenerator inspectorGenerator = new InspectorGenerator(
                    inspection.instrument(generationPlan),
                    new TestWorld(
                            MINIMUM_Y,
                            MAXIMUM_Y,
                            TestBlock.air("minecraft:air"),
                            new TestBiome("minecraft:plains")),
                    seed,
                    chunkSize);

            WorldSlice referenceSlice = WorldSlice.from(reference, region);
            imageSet.stage("reference", referenceSlice, 0);

            inspectorGenerator.generate();
            inspection.completion().toCompletableFuture().join();
        }
        render(imageSet);
    }

    private static Set<InspectionStage> enabledPoints(WorldGenerationPlan plan) {
        return InspectionStage.all().stream()
                .filter(point -> switch (point.phase()) {
                    case NOISE -> plan.flags().noise();
                    case SURFACE -> plan.flags().surface();
                    case CARVERS -> plan.flags().carvers();
                    case FEATURES -> plan.flags().features();
                    case LOAD -> true;
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void render(StageImageSet imageSet) {
        Path outputDirectory = outputPath.toAbsolutePath().normalize();
        try {
            if (Files.exists(outputDirectory) && !Files.isDirectory(outputDirectory)) {
                throw new IllegalArgumentException("--output must be a directory: " + outputDirectory);
            }
            Files.createDirectories(outputDirectory);
            for (Map.Entry<String, BufferedImage> image : imageSet.images().entrySet()) {
                WorldSliceRenderer.writePng(
                        image.getValue(),
                        outputDirectory.resolve(image.getKey() + ".png"));
            }
            WorldSliceRenderer.writePng(
                    imageSet.composite(),
                    outputDirectory.resolve("sequence.png"));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write inspector images to " + outputDirectory, exception);
        }
    }

    private WorldGenerationPlan plan(WorldReader reference) {
        UnderillaFactory.Builder planBuilder = switch (strategy) {
            case NONE     -> UnderillaFactory.none(reference);
            case ABSOLUTE -> UnderillaFactory.absolute(reference);
            case SURFACE  -> UnderillaFactory.surface(reference)
                    .surfaceDepth(surfaceDepth, 50, 2);
        };

        NoodleCavesPolicy cavesPolicy = surfaceCavers
                ? NoodleCavesPolicy.surface(_ -> true, true)
                : NoodleCavesPolicy.underground();

        return planBuilder
                .carvers(cavers)
                .features(features)
                .surfaceFill(surfaceFill)
                .blocks(new TestBlockFactory(TestBlock.air(AIR_ID.toString())))
                .verticalRange(MINIMUM_Y, MAXIMUM_Y)
                .chunkCacheSize(chunkSize)
                .generationArea(
                        0,
                        0,
                        Math.multiplyExact(chunkSize, GenerationConstants.CHUNK_SIZE),
                        GenerationConstants.CHUNK_SIZE)
                .preservedGeneratedBiomes(CAVE_BIOMES::contains)
                .noodleCaves(cavesPolicy)
                .build();
    }

    private WorldReader referenceWorld() {
        try {
            Path temporaryWorld = Files.createTempDirectory("underilla-inspector-");
            Path regions = Files.createDirectories(temporaryWorld.resolve("region"));
            Path referenceRegion = regions.resolve("r.0.0.mca");
            Files.copy(file.toPath(), referenceRegion);

            referenceRegion.toFile().deleteOnExit();
            regions.toFile().deleteOnExit();
            temporaryWorld.toFile().deleteOnExit();
            return new TestDiskWorldReader(
                    regions.toFile(),
                    1,
                    new TestBlockFactory(TestBlock.air(AIR_ID.toString())));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not prepare reference world from " + file, exception);
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Could not read prepared reference world from " + file, exception);
        }
    }

    static void main(String[] args) {
        int out = new CommandLine(new InspectorCLI()).execute(args);
        System.exit(out);
    }
}
