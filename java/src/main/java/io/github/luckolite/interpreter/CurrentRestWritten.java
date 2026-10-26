// Copyright 2026 Music Sheets contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Current declarations only. No Original registry or native/producer claim.
 * Actual effective-frame/grid admission remains the consuming writer's job. */
public final class CurrentRestWritten {
    private CurrentRestWritten() {}

    public record Placement(double onsetBeats, double durationBeats) {
        public Placement {
            if (!Double.isFinite(onsetBeats)
                    || onsetBeats < 0
                    || !Double.isFinite(durationBeats)
                    || durationBeats <= 0)
                throw new IllegalArgumentException("Invalid Current rest OWN value");
        }
    }

    public static Map<Integer, Placement> validated(
            Map<Integer, Placement> input, ScorePageInterpretation fullCurrent) {
        Objects.requireNonNull(input, "Explicit Current rest declaration map required");
        Objects.requireNonNull(fullCurrent, "Full Current score required");
        if (input.size() > fullCurrent.rests().size() || fullCurrent.rests().size() > 1_000_000)
            throw new IllegalArgumentException("Current rest OWN count outside domain");
        var out = new TreeMap<Integer, Placement>();
        for (var entry : input.entrySet()) {
            Integer owner = entry.getKey();
            var clock = entry.getValue();
            if (owner == null || owner < 0 || owner >= fullCurrent.rests().size() || clock == null)
                throw new IllegalArgumentException("Foreign Current rest OWN owner");
            var rest = fullCurrent.rests().get(owner);
            if (rest.kind() != ScoreRestEvent.Kind.LITERAL
                    || rest.measureIndex() < 0
                    || rest.measureIndex() >= fullCurrent.measures().size()
                    || !Double.isFinite(rest.durationBeats())
                    || rest.durationBeats() <= 0
                    || Double.doubleToRawLongBits(clock.durationBeats())
                            != Double.doubleToRawLongBits(rest.durationBeats()))
                throw new IllegalArgumentException(
                        "Current rest OWN differs from its actual literal record");
            out.put(owner, clock);
        }
        return Collections.unmodifiableMap(out);
    }
}
