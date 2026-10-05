// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Serializes complete actual output records/indices from original pitched fixture variations. */
public final class Pitched902HelperParityProbe {
    private static ScoreNoteEvent rich(ScoreNoteEvent n, int i) {
        return new ScoreNoteEvent(
                n.measureIndex(),
                n.positionInMeasure(),
                n.staffStep(),
                n.staffIndex(),
                n.staffCount(),
                n.pageY(),
                (i & 1) != 0,
                i % 3,
                n.beamCount(),
                n.writtenAccidental(),
                n.unbeamedDurationBeats(),
                i % 2 == 0 ? 5 : 3,
                .125f * i,
                i % 2 == 0 ? NoteArticulation.STACCATO : NoteArticulation.TENUTO,
                n.clefBottomDiatonic(),
                false,
                .0625f * i,
                (i & 1) != 0,
                0,
                i % 4,
                i % 2 == 0 ? 4 : 2,
                i % 3 - 1,
                ScoreNoteEvent.Kind.PITCHED);
    }

    private static Object invoke(Object owner, String name, Class<?>[] parameters, Object... args)
            throws Exception {
        Method m = owner.getClass().getDeclaredMethod(name, parameters);
        m.setAccessible(true);
        return m.invoke(owner, args);
    }

    public static void main(String[] args) throws Exception {
        var values = new ArrayList<Object>();
        for (String text : List.of("8va", "15ma", "8vb", "(8va)--------", "15", "8va suffix"))
            for (int i = 0; i < 4; i++) {
                var g = OctaveMarkDetectorTest.page();
                OctaveMarkDetectorTest.dash(g, 120, 370, text.equals("8vb") ? 390 : 55);
                int staff = text.equals("8vb") ? 1 : 0;
                var notes =
                        List.of(
                                rich(OctaveMarkDetectorTest.note(150, staff), i),
                                rich(OctaveMarkDetectorTest.note(300, staff), i + 1),
                                rich(OctaveMarkDetectorTest.note(500, staff), i + 2));
                values.add(
                        OctaveMarkDetectorTest.apply(
                                g,
                                List.of(
                                        OctaveMarkDetectorTest.word(
                                                text, 80, text.equals("8vb") ? 370 : 40)),
                                notes));
            }
        for (boolean diamond : new boolean[] {false, true})
            for (boolean down : new boolean[] {false, true})
                for (int i = 0; i < 4; i++) {
                    var fixture = new ArtificialHarmonicsTest();
                    invoke(
                            fixture,
                            down ? "downStem" : "shape",
                            new Class<?>[] {boolean.class},
                            diamond);
                    var stopped =
                            (ScoreNoteEvent)
                                    invoke(
                                            fixture,
                                            "note",
                                            new Class<?>[] {int.class, float.class},
                                            0,
                                            160f);
                    var upper =
                            (ScoreNoteEvent)
                                    invoke(
                                            fixture,
                                            "note",
                                            new Class<?>[] {int.class, float.class},
                                            3,
                                            130f);
                    values.add(
                            invoke(
                                    fixture,
                                    "apply",
                                    new Class<?>[] {List.class},
                                    List.of(rich(stopped, i), rich(upper, i + 1))));
                }
        Method find =
                CrossRowPortamentoTest.class.getDeclaredMethod(
                        "find",
                        boolean.class,
                        boolean.class,
                        int.class,
                        int.class,
                        boolean.class,
                        boolean.class,
                        int.class,
                        String.class,
                        boolean.class,
                        boolean.class,
                        boolean.class);
        find.setAccessible(true);
        for (int source : new int[] {9, 12})
            for (boolean chord : new boolean[] {false, true})
                for (boolean tie : new boolean[] {false, true})
                    values.add(
                            find.invoke(
                                    null,
                                    true,
                                    true,
                                    source,
                                    source == 12 ? 9 : 12,
                                    false,
                                    chord,
                                    5,
                                    "port",
                                    false,
                                    tie,
                                    false));
        String serialized = values.toString();
        String digest =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(serialized.getBytes(StandardCharsets.UTF_8)));
        System.out.println("fixtures=" + values.size() + " sha256=" + digest);
        System.out.println(serialized);
    }
}
