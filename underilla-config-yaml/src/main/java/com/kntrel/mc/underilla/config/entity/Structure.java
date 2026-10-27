package com.kntrel.mc.underilla.config.entity;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.api.scope.StructureSnapshot;
import java.util.Set;

/** Read-only structure candidate selected by the operation's evaluation contract. */
public non-sealed interface Structure extends ConfigEntity, Identifiable, Taggable {
    static Structure of(StructureSnapshot snapshot, Set<ID> tags) { return new Impl.StructureValue(snapshot, tags); }

    @Prop ID id();
    @Prop default String namespace() { return id().namespace(); }
    @Prop default String name() { return id().value(); }
    @Prop Set<ID> tags();
    /** Null when the placement origin is unavailable. */
    @Prop Origin origin();
    /** Null when the placement bounds are unavailable. */
    @Prop Bounds bounds();

    interface Origin {
        static Origin of(StructureSnapshot.Origin snapshot) {
            return snapshot == null ? null : new Impl.StructureOrigin(snapshot.x(), snapshot.y(), snapshot.z());
        }

        @Prop int x();
        @Prop int y();
        @Prop int z();
    }

    interface Bounds {
        static Bounds of(StructureSnapshot.Bounds snapshot) {
            return snapshot == null ? null : new Impl.StructureBounds(snapshot);
        }

        @Prop double minX();
        @Prop double minY();
        @Prop double minZ();
        @Prop double maxX();
        @Prop double maxY();
        @Prop double maxZ();
        @Prop default double width() { return maxX() - minX(); }
        @Prop default double height() { return maxY() - minY(); }
        @Prop default double depth() { return maxZ() - minZ(); }
    }
}
