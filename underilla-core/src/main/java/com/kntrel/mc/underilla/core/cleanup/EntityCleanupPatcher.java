package com.kntrel.mc.underilla.core.cleanup;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.Entity;
import com.kntrel.mc.underilla.core.patch.Patcher;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Removes configured entity types and transforms the entities that remain. */
public final class EntityCleanupPatcher implements Patcher<ChunkData> {

    private final Predicate<Entity> shouldRemove;
    private final Consumer<Entity> survivingEntityTransformer;

    public EntityCleanupPatcher(
            Predicate<Entity> shouldRemove,
            Consumer<Entity> survivingEntityTransformer
    ) {
        this.shouldRemove = Objects.requireNonNull(shouldRemove, "shouldRemove");
        this.survivingEntityTransformer = Objects.requireNonNull(
                survivingEntityTransformer, "survivingEntityTransformer");
    }

    @Override
    public void patch(ChunkData targetChunk) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        for (Entity entity : targetChunk.entities()) {
            if (shouldRemove.test(entity)) {
                entity.remove();
            } else {
                survivingEntityTransformer.accept(entity);
            }
        }
    }
}
