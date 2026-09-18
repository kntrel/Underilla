package com.kntrel.mc.underilla.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IDTest {

    @Test
    void assumesMinecraftNamespaceWhenItIsOmitted() {
        assertEquals(ID.of("minecraft", "stone"), ID.of("stone"));
    }

    @Test
    void parsesAndRendersNamespacedIdentifiers() {
        ID id = ID.of("example:blocks/polished_stone");

        assertEquals("example", id.namespace());
        assertEquals("blocks/polished_stone", id.value());
        assertEquals("example:blocks/polished_stone", id.toString());
    }

    @Test
    void rejectsMissingAndUnsupportedParts() {
        assertThrows(IllegalArgumentException.class, () -> ID.of(":stone"));
        assertThrows(IllegalArgumentException.class, () -> ID.of("minecraft:"));
        assertThrows(IllegalArgumentException.class, () -> ID.of("mine craft:stone"));
        assertThrows(IllegalArgumentException.class, () -> ID.of("minecraft:Stone"));
        assertThrows(IllegalArgumentException.class, () -> ID.of("minecraft:stone:block"));
    }
}
