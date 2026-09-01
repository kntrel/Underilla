package com.kntrel.mc.underilla.paper.io;

import java.nio.file.Path;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfiguration;

final class RegionPathResolver {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern DIMENSION_PATH_PATTERN = Pattern.compile("[a-z0-9/._-]+");

    private RegionPathResolver() {}

    static Path resolve(FileConfiguration configuration, String section) {
        return resolve(section,
                configuration.getString(section + ".regionPath"),
                configuration.getString(section + ".worldPath"),
                configuration.getString(section + ".dimension"));
    }

    static Path resolveEntities(FileConfiguration configuration, String section) {
        return resolveEntities(section,
                configuration.getString(section + ".entitiesPath"),
                configuration.getString(section + ".worldPath"),
                configuration.getString(section + ".dimension"));
    }

    static Path resolve(String section, String configuredRegionPath, String configuredWorldPath,
            String configuredDimension) {
        String regionPath = nonBlank(configuredRegionPath);
        if (regionPath != null) {
            return Path.of(regionPath).normalize();
        }

        String worldPath = nonBlank(configuredWorldPath);
        String dimension = nonBlank(configuredDimension);
        if (worldPath == null || dimension == null) {
            throw new IllegalArgumentException("Configuration section '" + section
                    + "' must define regionPath or both worldPath and dimension.");
        }
        return fromWorldAndDimension(worldPath, dimension, "region");
    }

    static Path resolveEntities(String section, String configuredEntitiesPath, String configuredWorldPath,
            String configuredDimension) {
        String entitiesPath = nonBlank(configuredEntitiesPath);
        if (entitiesPath != null) {
            return Path.of(entitiesPath).normalize();
        }

        String worldPath = nonBlank(configuredWorldPath);
        String dimension = nonBlank(configuredDimension);
        if (worldPath == null && dimension == null) {
            return null;
        }
        if (worldPath == null || dimension == null) {
            throw new IllegalArgumentException("Configuration section '" + section
                    + "' must define entitiesPath or both worldPath and dimension for entity data.");
        }
        return fromWorldAndDimension(worldPath, dimension, "entities");
    }

    private static Path fromWorldAndDimension(String worldPath, String dimension, String dataDirectory) {
        int separator = dimension.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : dimension.substring(0, separator);
        String dimensionPath = separator < 0 ? dimension : dimension.substring(separator + 1);
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()
                || !DIMENSION_PATH_PATTERN.matcher(dimensionPath).matches()
                || hasUnsafeSegment(dimensionPath)) {
            throw new IllegalArgumentException("Invalid dimension identifier '" + dimension + "'.");
        }

        return Path.of(worldPath)
                .resolve("dimensions")
                .resolve(namespace)
                .resolve(dimensionPath)
                .resolve(dataDirectory)
                .normalize();
    }

    private static boolean hasUnsafeSegment(String path) {
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static String nonBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
