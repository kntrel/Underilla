package com.kntrel.mc.underilla.paper.preparing;

import com.kntrel.mc.underilla.paper.Underilla;
import com.kntrel.mc.underilla.paper.io.UnderillaConfig.StringKeys;
import fr.formiko.utils.FLUFiles;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerSetup.class);

    private ServerSetup() {}

    public static boolean setupPaperWorkerthreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        LOGGER.info("Available cores: {}", cores);
        // LOGGER.info("config/paper-global.yml should be edit to: chunk-system.worker-threads: {}", cores -
        // 1));

        FileConfiguration paperGlobalConfig = getPaperGlobalFileConfiguration();
        String key = "chunk-system.worker-threads";
        if (paperGlobalConfig.getInt(key, -1) == cores - 1) {
            LOGGER.info("The worker threads are already set to {}", cores - 1);
            return false;
        } else {
            paperGlobalConfig.set(key, cores - 1);
            LOGGER.info("Set the worker threads to {}", cores - 1);
            try {
                paperGlobalConfig.save(getPaperGlobalFile());
                Underilla.getInstance().validateInitServerTask(StringKeys.STEP_SETUP_PAPER_FOR_QUICK_GENERATION);
            } catch (Exception e) {
                LOGGER.error("Error saving paper-global.yml", e);
                Underilla.getInstance().validateInitServerTask(StringKeys.STEP_SETUP_PAPER_FOR_QUICK_GENERATION, false);
                return false;
            }
            return true;
        }
    }

    public static boolean downloadNeededDependencies() {
        File plugins = new File(Underilla.getInstance().getServer().getWorldContainer(), "plugins");
        boolean shouldDownloadVoidWorldGenerator = true;
        boolean shouldDownloadChunky = true;
        boolean error = false;
        for (File file : plugins.listFiles()) {
            if (file.getName().matches("VoidWorldGenerator.*.jar")) {
                shouldDownloadVoidWorldGenerator = false;
            }
            if (file.getName().matches("Chunky.*.jar")) {
                shouldDownloadChunky = false;
            }
        }

        LOGGER.info("Downloading needed dependencies to {} VoidWorldGenerator: {}, Chunky: {}", plugins.getAbsolutePath(),
                shouldDownloadVoidWorldGenerator, shouldDownloadChunky);

        if (shouldDownloadVoidWorldGenerator) {
            String voidWorldGeneratorVersion = Underilla.getInstance().getConfig().getString("voidWorldGeneratorVersion", "1.3.12");
            error = !FLUFiles.download(
                    String.format("https://github.com/HydrolienF/VoidWorldGenerator/releases/download/%s/VoidWorldGenerator-%s.jar",
                            voidWorldGeneratorVersion, voidWorldGeneratorVersion),
                    String.format("%s/VoidWorldGenerator-%s.jar", plugins.getAbsolutePath(), voidWorldGeneratorVersion)) || error;
            LOGGER.info("Downloaded VoidWorldGenerator {} errors", error ? "with" : "without");
        }
        if (shouldDownloadChunky) {
            String chunkyVersion = Underilla.getInstance().getConfig().getString("chunkyVersion", "1.4.55");
            error = !FLUFiles
                    .download(
                            String.format("https://hangarcdn.papermc.io/plugins/pop4959/Chunky/versions/%s/PAPER/Chunky-Bukkit-%s.jar",
                                    chunkyVersion, chunkyVersion),
                            String.format("%s/Chunky-%s.jar", plugins.getAbsolutePath(), chunkyVersion))
                    || error;
            LOGGER.info("Downloaded Chunky {} errors", error ? "with" : "without");
        }
        Underilla.getInstance().validateInitServerTask(StringKeys.STEP_DOWNLOAD_DEPENDENCY_PLUGINS, !error);
        return shouldDownloadVoidWorldGenerator || shouldDownloadChunky;
    }

    public static boolean setupBukkitWorldGenerator() {
        // Read the bukkit.yml file
        FileConfiguration bukkitConfig = getBukkitFileConfiguration();
        String key = "worlds." + Underilla.getUnderillaConfig().getString(StringKeys.FINAL_WORLD_NAME) + ".generator";
        String currentWorldGenerator = bukkitConfig.getString(key, "");
        if ("Underilla".equals(currentWorldGenerator)) {
            LOGGER.info("The world generator is already set to Underilla");
            return false;
        } else {
            bukkitConfig.set(key, "Underilla");
            LOGGER.info("Set the world generator to Underilla");
            try {
                bukkitConfig.save(getBukkitFile());
                Underilla.getInstance().validateInitServerTask(StringKeys.STEP_SET_UNDERILLA_AS_WORLD_GENERATOR);
            } catch (Exception e) {
                LOGGER.error("Error saving bukkit.yml", e);
                Underilla.getInstance().validateInitServerTask(StringKeys.STEP_SET_UNDERILLA_AS_WORLD_GENERATOR, false);
                return false;
            }
            return true;
        }
    }
    public static FileConfiguration getBukkitFileConfiguration() { return YamlConfiguration.loadConfiguration(getBukkitFile()); }
    public static File getBukkitFile() { return new File(Underilla.getInstance().getServer().getWorldContainer(), "bukkit.yml"); }
    public static FileConfiguration getPaperGlobalFileConfiguration() { return YamlConfiguration.loadConfiguration(getPaperGlobalFile()); }
    public static File getPaperGlobalFile() {
        return new File(Underilla.getInstance().getServer().getWorldContainer(), "config/paper-global.yml");
    }
}
