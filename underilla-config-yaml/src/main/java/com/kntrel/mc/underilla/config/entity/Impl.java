package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.BiomeSnapshot;
import com.kntrel.mc.underilla.core.api.scope.BlockSnapshot;
import com.kntrel.mc.underilla.core.api.scope.ChunkSnapshot;
import com.kntrel.mc.underilla.core.api.scope.EntitySnapshot;
import com.kntrel.mc.underilla.core.api.scope.StructureSnapshot;
import com.kntrel.mc.underilla.core.api.scope.WorldSnapshot;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable snapshot-backed implementations of configuration entities. */
interface Impl {
    record WorldValue(WorldSnapshot snapshot) implements World {
        public WorldValue { Objects.requireNonNull(snapshot, "snapshot"); }
        @Override public ID dimension() { return snapshot.dimension(); }
        @Override public long seed() { return snapshot.seed(); }
        @Override public int minY() { return snapshot.minHeight(); }
        @Override public int maxY() { return snapshot.maxHeight(); }
    }

    record ChunkValue(ChunkSnapshot snapshot, World world) implements Chunk {
        public ChunkValue {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(world, "world");
        }
        @Override public int x() { return snapshot.x(); }
        @Override public int z() { return snapshot.z(); }
    }

    record BiomeValue(BiomeSnapshot snapshot, Set<ID> tags) implements Biome {
        public BiomeValue {
            Objects.requireNonNull(snapshot, "snapshot");
            tags = Set.copyOf(tags);
        }
        @Override public ID id() { return snapshot.id(); }
        @Override public int cellX() { return snapshot.cellX(); }
        @Override public int cellY() { return snapshot.cellY(); }
        @Override public int cellZ() { return snapshot.cellZ(); }
    }

    record BlockValue(BlockSnapshot snapshot, Set<ID> tags, boolean waterlogged, Map<String, ?> state) implements Block {
        public BlockValue {
            Objects.requireNonNull(snapshot, "snapshot");
            tags = Set.copyOf(tags);
            state = copyState(state);
        }
        @Override public ID id() { return snapshot.id(); }
        @Override public int x() { return snapshot.x(); }
        @Override public int y() { return snapshot.y(); }
        @Override public int z() { return snapshot.z(); }
        @Override public boolean air() { return snapshot.isAir(); }
        @Override public boolean solid() { return snapshot.isSolid(); }
        @Override public boolean waterloggable() { return snapshot.isWaterloggable(); }
    }

    record EntityValue(EntitySnapshot snapshot, Set<ID> tags) implements Entity {
        public EntityValue {
            Objects.requireNonNull(snapshot, "snapshot");
            tags = Set.copyOf(tags);
        }
        @Override public ID id() { return snapshot.id(); }
    }

    record StructureValue(StructureSnapshot snapshot, Set<ID> tags) implements Structure {
        public StructureValue {
            Objects.requireNonNull(snapshot, "snapshot");
            tags = Set.copyOf(tags);
        }
        @Override public ID id() { return snapshot.id(); }
        @Override public Structure.Origin origin() { return Structure.Origin.of(snapshot.origin()); }
        @Override public Structure.Bounds bounds() { return Structure.Bounds.of(snapshot.bounds()); }
    }

    record StructureOrigin(int x, int y, int z) implements Structure.Origin {}

    record StructureBounds(StructureSnapshot.Bounds snapshot) implements Structure.Bounds {
        public StructureBounds { Objects.requireNonNull(snapshot, "snapshot"); }
        @Override public double minX() { return snapshot.minX(); }
        @Override public double minY() { return snapshot.minY(); }
        @Override public double minZ() { return snapshot.minZ(); }
        @Override public double maxX() { return snapshot.maxX(); }
        @Override public double maxY() { return snapshot.maxY(); }
        @Override public double maxZ() { return snapshot.maxZ(); }
    }

    private static Map<String, ?> copyState(Map<String, ?> state) {
        Map<String, ?> copy = Map.copyOf(state);
        for (Object value : copy.values()) {
            if (!(value instanceof Boolean || value instanceof Integer || value instanceof String)) {
                throw new IllegalArgumentException("Block state values must be Boolean, Integer, or String");
            }
        }
        return copy;
    }
}
