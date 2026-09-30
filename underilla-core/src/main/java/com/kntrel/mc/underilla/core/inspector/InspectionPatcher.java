package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.generation.Phase;
import com.kntrel.mc.underilla.core.patch.Patcher;
import java.util.Objects;

/** Decorates one generation-phase patcher with the session's selected captures. */
public final class InspectionPatcher implements Patcher<ChunkData> {

    private final Inspector session;
    private final InspectionStage before;
    private final InspectionStage after;
    private final Patcher<ChunkData> delegate;

    InspectionPatcher(Inspector session, Phase phase, Patcher<ChunkData> delegate) {
        this.session = Objects.requireNonNull(session, "session");
        Objects.requireNonNull(phase, "phase");
        this.before = InspectionStage.before(phase);
        this.after = InspectionStage.after(phase);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void patch(ChunkData chunk) {
        if (!session.contains(chunk)) {
            delegate.patch(chunk);
            return;
        }
        if (session.includes(before)) {
            session.capture(before, chunk);
        }
        delegate.patch(chunk);
        if (session.includes(after)) {
            session.capture(after, chunk);
        }
    }
}
