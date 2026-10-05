// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

/** The same compiled probe runs with baseline and candidate record binaries. */
public final class LegacyNoteContractProbe {
    public static void main(String[] args) throws Exception {
        var constructors =
                Arrays.stream(ScoreNoteEvent.class.getConstructors())
                        .filter(c -> c.getParameterCount() <= 22)
                        .sorted(java.util.Comparator.comparingInt(Constructor::getParameterCount))
                        .toList();
        Object[] ordinary = {
            3, .61f, 8, 1, 2, .42f, true, 2, 2, 1, 1f, 5, 1.25f, 9, 22, false, .75f, false, -1, 9,
            3, -1
        };
        Object[] normalized = {
            3, .61f, 8, 1, 2, .42f, true, 9, 9, 999, Float.NaN, 5, Float.NaN, -1, 999, false,
            Float.NaN, false, -1, 9, 3, -1
        };
        for (Object[] seed : new Object[][] {ordinary, normalized}) {
            for (var constructor : constructors) {
                var note =
                        (ScoreNoteEvent)
                                constructor.newInstance(
                                        Arrays.copyOf(seed, constructor.getParameterCount()));
                StringBuilder value =
                        new StringBuilder("constructor=").append(constructor.getParameterCount());
                for (RecordComponent component : ScoreNoteEvent.class.getRecordComponents()) {
                    if (component.getName().equals("kind")) continue;
                    value.append('|')
                            .append(component.getName())
                            .append('=')
                            .append(component.getAccessor().invoke(note));
                }
                value.append("|identity=").append(note.diatonicPitchIdentity());
                value.append("|durationScale=").append(note.durationScale());
                System.out.println(value);
            }
        }
    }
}
