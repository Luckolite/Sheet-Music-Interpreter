// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class TallDisplacedChordBeamTest extends BareQuarterStemTest {
    @org.junit.Test
    public void preservesBeamsBeyondFourGapShaftTrace() throws Exception {
        for (boolean displaced : new boolean[] {false, true})
            for (boolean beam : new boolean[] {false, true}) {
                byte[] l = new byte[W * H], g = new byte[W * H];
                Arrays.fill(g, (byte) 255);
                rect(l, g, 109, 124, 2, 141, 1, 0);
                if (beam) {
                    rect(l, g, 109, 260, 75, 5, 5, 0);
                    rect(l, g, 181, 180, 2, 85, 1, 0);
                    oval(l, g, 192, 180);
                }
                List<Object> notes = new ArrayList<>();
                for (int y : new int[] {124, 140, 164}) {
                    oval(l, g, 120, y);
                    notes.add(
                            construct(
                                    detected,
                                    new Class[] {ScoreNoteEvent.class, component, float.class},
                                    event(y, beam ? 1 : 0),
                                    head(120, y),
                                    G));
                }
                if (displaced) {
                    oval(l, g, 98, 172);
                    notes.add(
                            construct(
                                    detected,
                                    new Class[] {ScoreNoteEvent.class, component, float.class},
                                    event(172, 0),
                                    head(98, 172),
                                    G));
                }
                var m =
                        OmrScoreInterpreter.class.getDeclaredMethod(
                                "reconcileSingleShaftChords",
                                List.class,
                                byte[].class,
                                byte[].class,
                                int.class,
                                int.class);
                m.setAccessible(true);
                var results = (List<?>) m.invoke(null, notes, l, g, W, H);
                var e = detected.getDeclaredMethod("event");
                e.setAccessible(true);
                for (Object result : results) {
                    var n = (ScoreNoteEvent) e.invoke(result);
                    if (n.beamCount() != (beam ? 1 : 0))
                        throw new AssertionError(
                                "displaced="
                                        + displaced
                                        + " beam="
                                        + beam
                                        + " result="
                                        + n.beamCount());
                }
            }
    }
}
