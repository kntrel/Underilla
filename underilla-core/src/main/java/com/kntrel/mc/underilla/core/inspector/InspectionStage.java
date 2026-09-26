package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.generation.Phase;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** A named, ordered observation point around one Underilla generation patcher. */
public record InspectionStage(Phase phase, State state) {

    /** Whether a capture happens before or after Underilla's patcher at a generation phase. */
    public enum State {
        BEFORE_PATCH,
        AFTER_PATCH;

        int order() {
            return this.ordinal();
        }
    }

    public static InspectionStage before(Phase phase) {
        return new InspectionStage(phase, State.BEFORE_PATCH);
    }

    public static InspectionStage after(Phase phase) {
        return new InspectionStage(phase, State.AFTER_PATCH);
    }

    public InspectionStage {
        if (phase == null) {
            throw new NullPointerException("phase");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
    }

    /** Returns all before/after points in normal generation order. */
    public static Set<InspectionStage> all() {
        Set<InspectionStage> points = new LinkedHashSet<>();
        Arrays.stream(Phase.values()).forEach(phase ->
                Arrays.stream(State.values()).forEach(timing ->
                        points.add(new InspectionStage(phase, timing))));
        return Set.copyOf(points);
    }

    /** The stable order used in a rendered sequence. */
    public int order() {
        return phase.order() * State.values().length + state.order();
    }

    /** A human-readable label such as {@code "after carvers"}. */
    public String label() {
        String state = switch (this.state) {
            case BEFORE_PATCH -> "before";
            case AFTER_PATCH -> "after";
        };
        return state + " " + this.phase.name().toLowerCase(Locale.ROOT) + " patch";
    }
}
