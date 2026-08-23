package com.kntrel.mc.underilla.core.api;

/** Logging boundary implemented by the hosting platform. */
public interface GenerationLogger {

    void warning(String message);

    void error(String message, Throwable cause);
}
