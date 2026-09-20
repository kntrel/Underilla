package com.kntrel.mc.underilla.core.inspector;

import picocli.CommandLine;

import java.util.Arrays;

enum Strategy {

    NONE("none"),
    ABSOLUTE("absolute"),
    SURFACE("surface");

    static Strategy fromName(String name) {
        for (Strategy strategy : Strategy.values()) {
            if (strategy.name_.equals(name)) {
                return strategy;
            }
        }
        return null;
    }

    private final String name_;

    Strategy(String name) {
        this.name_ = name;
    }

    static class Converter implements CommandLine.ITypeConverter<Strategy> {

        @Override
        public Strategy convert(String value) {

            Strategy result = Strategy.fromName(value);
            if (result == null) {
                String vals = Arrays.stream(Strategy.values())
                        .map(Strategy::name)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                throw new CommandLine.TypeConversionException("expected one of: " + vals);
            }

            return result;
        }
    }
}
