// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original short key-flat and neighboring numeral terminals; no score pixels. */
public class KeyFlatCueBassTest {
    static final int W = 300, H = 220;
    byte[] l = new byte[W * H], g = new byte[W * H];

    void rect(int x, int y, int w, int h, int label) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) {
                l[yy * W + xx] = (byte) label;
                g[yy * W + xx] = 0;
            }
    }

    Object obj(String name, Object... args) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + name)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(args);
    }

    int clef(boolean flat, boolean dots) throws Exception {
        Arrays.fill(g, (byte) 255);
        for (int y = 80; y <= 144; y += 16) rect(15, y, 270, 1, 4);
        rect(100, 82, 3, 40, 3);
        if (flat) {
            rect(100, 104, 19, 3, 3);
            rect(116, 105, 3, 14, 3);
            rect(100, 118, 19, 3, 3);
        }
        if (dots) {
            rect(125, 89, 5, 5, 5);
            rect(125, 101, 5, 5, 5);
        }
        int area = 0;
        long sx = 0, sy = 0;
        for (int y = 82; y <= 121; y++)
            for (int x = 100; x <= 118; x++)
                if (l[y * W + x] == 3) {
                    area++;
                    sx += x;
                    sy += y;
                }
        Object
                key =
                        obj(
                                "Component",
                                area,
                                100,
                                118,
                                82,
                                121,
                                sx / (float) area,
                                sy / (float) area),
                treble = obj("Component", 700, 40, 70, 60, 170, 55f, 112f),
                head = obj("Component", 120, 212, 228, 114, 126, 220f, 120f),
                staff = obj("Staff", 80f, 144f, 16f),
                note =
                        obj(
                                "DetectedNote",
                                new ScoreNoteEvent(0, .5f, 3, 0, 1, 120f / H, false, 0, 0),
                                head,
                                16f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "applyPrintedClefs",
                        List.class,
                        List.class,
                        List.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        var result =
                (List<?>)
                        m.invoke(
                                null,
                                List.of(note),
                                List.of(staff),
                                List.of(treble, key),
                                l,
                                g,
                                W,
                                H);
        var field = note.getClass().getDeclaredField("event");
        field.setAccessible(true);
        return ((ScoreNoteEvent) field.get(result.get(0))).clefBottomDiatonic();
    }

    @Test
    public void keyFlatBesideMeterCannotBecomeCueBass() throws Exception {
        assertEquals(30, clef(true, true));
    }

    @Test
    public void keyFlatWithoutTerminalsKeepsTreble() throws Exception {
        assertEquals(30, clef(true, false));
    }
}
