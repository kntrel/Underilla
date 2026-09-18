package com.kntrel.mc.underilla.core.reader;

import com.jkantrell.nbt.tag.CompoundTag;
import java.util.Objects;

/** Read-only snapshot of an entity stored in a reference-world entity region. */
public record EntityView(CompoundTag tag, int dataVersion) {

    public EntityView {
        tag = Objects.requireNonNull(tag, "tag").clone();
    }

    @Override
    public CompoundTag tag() {
        return tag.clone();
    }
}
