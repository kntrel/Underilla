package com.kntrel.mc.underilla.core.reference;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.patch.Patcher;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import java.util.Objects;

/** Copies entities from the corresponding chunk in a reference world. */
public final class ReferenceWorldEntityPatcher implements Patcher<ChunkData> {

    private final WorldReader referenceWorld;

    public ReferenceWorldEntityPatcher(WorldReader referenceWorld) {
        this.referenceWorld = Objects.requireNonNull(referenceWorld, "referenceWorld");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        referenceWorld.readChunk(targetChunk.getChunkX(), targetChunk.getChunkZ())
                .map(ChunkReader::getEntities)
                .ifPresent(entities -> entities.forEach(targetChunk::addEntity));
    }
}
