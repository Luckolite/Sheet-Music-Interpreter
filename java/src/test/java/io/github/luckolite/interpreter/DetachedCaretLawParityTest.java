// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.LongConsumer;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original photometric comparator and refusal-only raw-kind contract controls. */
public class DetachedCaretLawParityTest {
    @Test
    public void enclosingSectionIsTheUnchangedBooleanLaw() throws Exception {
        var old =
                PrintedBeamRawContrast.class.getDeclaredMethod(
                        "enclosingSection",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        double.class,
                        boolean.class,
                        boolean.class,
                        LongConsumer.class);
        old.setAccessible(true);
        var current =
                DetachedCaretBodies.Page.class.getDeclaredMethod(
                        "section", int.class, int.class, int.class, float.class, boolean.class);
        current.setAccessible(true);
        LongConsumer charge = n -> {};
        int comparisons = 0, w = 80, h = 80;
        for (int paper : new int[] {200, 210, 240, 255})
            for (float gap : new float[] {3, 14, 32})
                for (boolean horizontal : new boolean[] {true, false})
                    for (int band = 1; band <= 4; band++)
                        for (int tone = 0; tone < 256; tone++) {
                            byte[] raw = new byte[w * h];
                            Arrays.fill(raw, (byte) paper);
                            for (int y = 30; y <= 50; y++)
                                for (int x = 40 - band / 2; x < 40 - band / 2 + band; x++)
                                    raw[(horizontal ? y * w + x : x * w + y)] = (byte) tone;
                            var page =
                                    new DetachedCaretBodies.Page(
                                            raw, new byte[raw.length], w, h, this);
                            int radius = Math.max(2, (int) Math.ceil(gap * .75));
                            boolean
                                    before =
                                            (boolean)
                                                    old.invoke(
                                                            null,
                                                            raw,
                                                            w,
                                                            h,
                                                            40,
                                                            40,
                                                            radius,
                                                            (double) gap,
                                                            horizontal,
                                                            false,
                                                            charge),
                                    after =
                                            (boolean)
                                                    current.invoke(
                                                            page, 40, 40, radius, gap, horizontal);
                            assertEquals(
                                    "paper=" + paper + " gap=" + gap + " tone=" + tone,
                                    before,
                                    after);
                            comparisons++;
                        }
        assertEquals(24576, comparisons);
    }

    @Test
    public void alreadyReturnedRawKindAbstainsWithoutUnusedContrast() throws Exception {
        int w = 2048, h = 1600;
        byte[] raw = new byte[w * h], labels = new byte[raw.length];
        Arrays.fill(raw, (byte) 255);
        for (int x = 95; x <= 119; x++) {
            int y = 100 + Math.abs(x - 107);
            raw[y * w + x] = (byte) (x < 107 ? 130 : 150);
            raw[(y + 1) * w + x] = (byte) (x < 107 ? 130 : 150);
        }
        var points = new ArrayList<Integer>();
        for (int y = 100; y <= 113; y++)
            for (int x = 95; x <= 119; x++)
                if ((raw[y * w + x] & 255) < 155)
                    points.add(y * w + x); // Include the literal lower antialiased row as well.
        for (int x = 95; x <= 119; x++) if ((raw[114 * w + x] & 255) < 155) points.add(114 * w + x);
        Class<?> g = Class.forName(NoteArticulationDetector.class.getName() + "$Glyph");
        var constructor =
                g.getDeclaredConstructor(
                        int.class, int.class, int.class, int.class, int.class, int[].class);
        constructor.setAccessible(true);
        Object glyph =
                constructor.newInstance(
                        95,
                        100,
                        119,
                        114,
                        points.size(),
                        points.stream().mapToInt(Integer::intValue).toArray());
        var contrast =
                NoteArticulationDetector.class.getDeclaredMethod(
                        "printedBodyContrast", g, byte[].class, int.class, int.class);
        contrast.setAccessible(true);
        assertFalse((boolean) contrast.invoke(null, glyph, raw, w, h));
        var method =
                NoteArticulationDetector.class.getDeclaredMethod(
                        "caretRawKind",
                        g,
                        int.class,
                        float.class,
                        DetachedCaretBodies.Page.class,
                        boolean.class,
                        int.class);
        method.setAccessible(true);
        var page = new DetachedCaretBodies.Page(raw, labels, w, h, this);
        int kind = (int) method.invoke(null, glyph, w, 14f, page, false, h);
        assertEquals(2, kind);
        var ink = new DetachedCaretCosts.Ink(95, 100, 119, 114, points.size());
        long expected =
                64
                        + 64
                        + DetachedCaretCosts.classify(ink).work()
                        + 64
                        + DetachedCaretCosts.rounded(ink).work();
        assertEquals(expected, page.stageLimit - page.producer);
    }
}
