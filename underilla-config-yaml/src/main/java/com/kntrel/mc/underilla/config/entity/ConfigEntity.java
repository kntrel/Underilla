package com.kntrel.mc.underilla.config.entity;

public sealed interface ConfigEntity permits World, Chunk, Biome, Block, Structure, Entity {}
