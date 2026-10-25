package com.kntrel.mc.underilla.config.entity;

public sealed interface Entity
permits
        World,
        Chunk,
        Biome,
        Block
{}
