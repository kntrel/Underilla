package com.kntrel.mc.underilla.paper.impl;

import com.kntrel.mc.underilla.core.api.GenerationLogger;
import com.kntrel.mc.underilla.paper.Underilla;

public final class PaperGenerationLogger implements GenerationLogger {

    @Override
    public void warning(String message) { Underilla.warning(message); }

    @Override
    public void error(String message, Throwable cause) { Underilla.error(message, cause); }
}
