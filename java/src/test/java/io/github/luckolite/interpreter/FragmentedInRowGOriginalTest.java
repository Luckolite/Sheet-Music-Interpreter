// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;

/** Original OFL in-row changes and literal role rivals; actual producer traversal supplies members. */
public class FragmentedInRowGOriginalTest {
    int lastClef(int mode) throws Exception {
        var p = new WholeClefSlotOriginalTest.Page();
        int[] initial = p.glyph("\uE050", 75, 80, true);
        p.glyph("\uE088", initial[0] + (initial[2] - initial[0] - 18) / 2, 54, false);
        int[] reset = null;
        if (mode == 0 || mode == 1 || mode >= 7) reset = p.glyph("\uE050", 300, 80, true);
        if (mode == 2) p.glyph("\uE262", 310, 108, false);
        if (mode == 3) p.glyph("\uE260", 310, 98, false);
        if (mode == 4)
            for (int i = 0; i < 4; i++) p.glyph("\uE260", 285 + i * 22, 92 + (i % 2) * 14, false);
        if (mode == 5)
            for (int y = 80; y <= 193; y++)
                for (int x = 312; x <= 313; x++) {
                    p.gray[y * 500 + x] = 0;
                    p.labels[y * 500 + x] = 3;
                }
        if (mode == 6) p.glyph("\uE088", 310, 54, false);
        int top = initial[4] - 48, bottom = initial[4] + 16, headY = bottom - 32;
        for (int line = 0; line < 5; line++)
            for (int x = 50; x < 470; x++) {
                int q = (top + line * 16) * 500 + x;
                p.gray[q] = 0;
                // The model splits only the in-row G at genuine raw staff crossings.
                if (p.labels[q] != 3 || mode != 1 && x >= 295 && x <= 350) p.labels[q] = 4;
            }
        Object staff = p.staffCtor.newInstance((float) top, (float) bottom, 16f);
        var detected = new ArrayList<Object>();
        for (int x : mode == 8 ? new int[] {220, 315, 420} : new int[] {220, 420}) {
            for (int y = headY - 5; y <= headY + 5; y++)
                for (int xx = x - 7; xx <= x + 7; xx++)
                    if ((xx - x) * (xx - x) / 49.0 + (y - headY) * (y - headY) / 25.0 <= 1) {
                        p.gray[y * 500 + xx] = 0;
                        p.labels[y * 500 + xx] = 2;
                    }
            Object h =
                    p.component.newInstance(
                            100, x - 7, x + 7, headY - 5, headY + 5, (float) x, (float) headY);
            detected.add(
                    p.detected.newInstance(
                            new ScoreNoteEvent(0, x / 500f, 4, 0, 1, headY / 300f, false, 0, 0),
                            h,
                            16f));
        }
        if (mode == 11)
            for (int x = reset[0]; x <= reset[2]; x++) p.gray[top * 500 + x] = (byte) 255;
        int n = p.gray.length;
        var collector =
                new ExactComponentMembers.Collector(
                        p.gray,
                        p.labels,
                        500,
                        300,
                        new ExactComponentMembers.Budget(2L * n + 4096, 4096),
                        new ExactComponentMembers.Budget(128L * n, 128L * n));
        var find =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "findComponents",
                        byte[].class,
                        int.class,
                        int.class,
                        byte.class,
                        byte[].class,
                        java.util.IdentityHashMap.class,
                        ExactComponentMembers.Collector.class);
        find.setAccessible(true);
        var glyphs =
                (List<?>) find.invoke(null, p.labels, 500, 300, (byte) 3, p.gray, null, collector);
        assertTrue(collector.seal(new ExactComponentMembers.Budget(2L * n + 4096, 4096)));
        var headers = new ArrayList<Object>();
        if (mode == 7)
            for (Object c : glyphs) {
                var f = c.getClass().getDeclaredField("minX");
                f.setAccessible(true);
                if (f.getInt(c) >= 295) {
                    headers.add(c);
                    break;
                }
            }
        if (mode == 7) assertEquals(1, headers.size());
        if (mode == 10) p.gray[299 * 500 + 499] = 0;
        byte[] savedG = p.gray.clone(), savedL = p.labels.clone();
        Method apply;
        Object[] args;
        try {
            apply =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "applyPrintedClefs",
                            List.class,
                            List.class,
                            List.class,
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            ExactComponentMembers.Collector.class,
                            List.class);
            args =
                    new Object[] {
                        detected,
                        List.of(staff),
                        glyphs,
                        p.labels,
                        p.gray,
                        500,
                        300,
                        mode == 9 ? null : collector,
                        headers
                    };
        } catch (NoSuchMethodException old) {
            apply =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "applyPrintedClefs",
                            List.class,
                            List.class,
                            List.class,
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class);
            args = new Object[] {detected, List.of(staff), glyphs, p.labels, p.gray, 500, 300};
        }
        apply.setAccessible(true);
        var out = (List<?>) apply.invoke(null, args);
        var event = p.dt.getDeclaredMethod("event");
        event.setAccessible(true);
        assertArrayEquals(savedG, p.gray);
        assertArrayEquals(savedL, p.labels);
        assertEquals(
                "initial complete G8 admission",
                37,
                ((ScoreNoteEvent) event.invoke(out.get(0))).clefBottomDiatonic());
        return ((ScoreNoteEvent) event.invoke(out.get(out.size() - 1))).clefBottomDiatonic();
    }

    @Test
    public void fragmentedPlainGResetsRaisedClef() throws Exception {
        assertEquals(30, lastClef(0));
    }

    @Test
    public void oldWholeGResetIsUnchanged() throws Exception {
        assertEquals(30, lastClef(1));
    }

    @Test
    public void sharpIsNotAReset() throws Exception {
        assertEquals(37, lastClef(2));
    }

    @Test
    public void flatIsNotAReset() throws Exception {
        assertEquals(37, lastClef(3));
    }

    @Test
    public void keySignatureIsNotAReset() throws Exception {
        assertEquals(37, lastClef(4));
    }

    @Test
    public void bracketIsNotAReset() throws Exception {
        assertEquals(37, lastClef(5));
    }

    @Test
    public void fingeringIsNotAReset() throws Exception {
        assertEquals(37, lastClef(6));
    }

    @Test
    public void claimedKeyRolePreventsFragmentConsumption() throws Exception {
        assertEquals(37, lastClef(7));
    }

    @Test
    public void sharedHeadPreventsFragmentConsumption() throws Exception {
        assertEquals(37, lastClef(8));
    }

    @Test
    public void missingProducerFallsBack() throws Exception {
        assertEquals(37, lastClef(9));
    }

    @Test
    public void changedSourceHashFallsBack() throws Exception {
        assertEquals(37, lastClef(10));
    }

    @Test
    public void brokenRawConnectionFallsBack() throws Exception {
        assertEquals(37, lastClef(11));
    }
}
