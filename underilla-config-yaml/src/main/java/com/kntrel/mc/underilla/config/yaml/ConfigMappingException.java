package com.kntrel.mc.underilla.config.yaml;

/** Invalid YAML or a setting that cannot be mapped into the configuration model. */
public final class ConfigMappingException extends IllegalArgumentException {
    private final String path;

    public ConfigMappingException(String path, String message) {
        super(path + ": " + message);
        this.path = path;
    }

    public ConfigMappingException(String path, String message, Throwable cause) {
        super(path + ": " + message, cause);
        this.path = path;
    }

    /** A dotted YAML path, including zero-based list indices, or $ for the document root. */
    public String path() { return path; }
}
