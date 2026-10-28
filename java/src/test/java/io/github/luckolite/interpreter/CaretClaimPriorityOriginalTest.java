// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source-entry priority controls using actual legacy faint/paper acquisitions, before new typing. */
public class CaretClaimPriorityOriginalTest {
    static final int W = 1280, H = 1280;
    static final Class<?> GLYPH, ACCEPTED;

    static {
        try {
            GLYPH = Class.forName(NoteArticulationDetector.class.getName() + "$Glyph");
            ACCEPTED = Class.forName(NoteArticulationDetector.class.getName() + "$CaretAccepted");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static List<?> acquired(byte[] raw, List<NoteArticulationDetector.Anchor> notes, boolean paper)
            throws Exception {
        Method method =
                paper
                        ? NoteArticulationDetector.class.getDeclaredMethod(
                                "paperGlyphs",
                                byte[].class,
                                int.class,
                                int.class,
                                List.class,
                                boolean[].class,
                                int[].class)
                        : NoteArticulationDetector.class.getDeclaredMethod(
                                "faintAccentGlyphs",
                                byte[].class,
                                int.class,
                                int.class,
                                boolean[].class,
                                int[].class);
        method.setAccessible(true);
        return (List<?>)
                (paper
                        ? method.invoke(
                                null,
                                raw,
                                W,
                                H,
                                notes,
                                new boolean[raw.length],
                                new int[raw.length])
                        : method.invoke(
                                null, raw, W, H, new boolean[raw.length], new int[raw.length]));
    }

    static int field(Object g, String name) throws Exception {
        var m = GLYPH.getDeclaredMethod(name);
        m.setAccessible(true);
        return (int) m.invoke(g);
    }

    static List<?> priority(DetachedCaretBodies.Page page, Object glyph) throws Exception {
        var create = ACCEPTED.getDeclaredMethod("create", DetachedCaretBodies.Page.class);
        create.setAccessible(true);
        Object accepted = create.invoke(null, page);
        assertNotNull(accepted);
        var add = ACCEPTED.getDeclaredMethod("add", GLYPH);
        add.setAccessible(true);
        add.invoke(accepted, glyph);
        var snapshot = ACCEPTED.getDeclaredMethod("snapshot");
        snapshot.setAccessible(true);
        return (List<?>) snapshot.invoke(accepted);
    }

    static boolean blocked(DetachedCaretBodies.Page page, Object glyph, List<?> priority)
            throws Exception {
        var pay =
                NoteArticulationDetector.class.getDeclaredMethod(
                        "prepayCaretOverlap", DetachedCaretBodies.Page.class, GLYPH, List.class);
        pay.setAccessible(true);
        pay.invoke(null, page, glyph, priority);
        var test =
                NoteArticulationDetector.class.getDeclaredMethod(
                        "overlapsPrintedClaim", GLYPH, List.class);
        test.setAccessible(true);
        return (boolean) test.invoke(null, glyph, priority);
    }

    static byte[] faint(boolean independentPoint) {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        for (int x = 230; x <= 241; x++) {
            int up = 135 + Math.round(4 * (x - 230) / 11f),
                    down = 143 - Math.round(4 * (x - 230) / 11f);
            raw[up * W + x] = (byte) 170;
            raw[down * W + x] = (byte) 170;
        }
        for (int x = 239; x <= 241; x++) raw[139 * W + x] = 75;
        if (independentPoint) raw[139 * W + 230] = 75;
        return raw;
    }

    @Test
    public void actualAcceptedFaintBodyHasPriorityAtDifferentUnmarkedAttack() throws Exception {
        byte[] raw = faint(false), labels = new byte[raw.length];
        var notes =
                List.of(
                        new NoteArticulationDetector.Anchor(235.5f, 170, 14, 0),
                        new NoteArticulationDetector.Anchor(235.5f, 200, 14, 1));
        assertArrayEquals(
                new int[] {1, 0}, NoteArticulationDetector.detect(labels, raw, W, H, notes));
        List<?> glyphs = acquired(raw, notes, false);
        Object glyph =
                glyphs.stream()
                        .filter(
                                g -> {
                                    try {
                                        return field(g, "left") == 230;
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .findFirst()
                        .orElseThrow();
        var page = new DetachedCaretBodies.Page(raw, labels, W, H, notes);
        List<?> old = priority(page, glyph);
        assertSame(glyph, old.get(0));
        assertTrue(blocked(page, glyph, old));
    }

    @Test
    public void actualAcceptedPaperBodyIsNotLostByPrintedClaimFiltering() throws Exception {
        byte[] raw = new byte[W * H], labels = new byte[raw.length];
        Arrays.fill(raw, (byte) 145);
        for (int y = 233; y <= 235; y++) for (int x = 471; x <= 490; x++) raw[y * W + x] = 30;
        var notes =
                List.of(
                        new NoteArticulationDetector.Anchor(480, 260, 14, 0),
                        new NoteArticulationDetector.Anchor(480, 310, 14, 1));
        assertArrayEquals(
                new int[] {8, 0}, NoteArticulationDetector.detect(labels, raw, W, H, notes));
        Object glyph =
                acquired(raw, notes, true).stream()
                        .filter(
                                g -> {
                                    try {
                                        return field(g, "left") == 471;
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .findFirst()
                        .orElseThrow();
        var page = new DetachedCaretBodies.Page(raw, labels, W, H, notes);
        assertTrue(blocked(page, glyph, priority(page, glyph)));
    }

    @Test
    public void overlappingBoxesWithoutSharedLiteralPixelsAreNotOwnership() throws Exception {
        byte[] raw = faint(true), labels = new byte[raw.length];
        var notes =
                List.of(
                        new NoteArticulationDetector.Anchor(235.5f, 170, 14, 0),
                        new NoteArticulationDetector.Anchor(235.5f, 200, 14, 1));
        Object glyph =
                acquired(raw, notes, false).stream()
                        .filter(
                                g -> {
                                    try {
                                        return field(g, "left") == 230;
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .findFirst()
                        .orElseThrow();
        var ct = GLYPH.getDeclaredConstructors()[0];
        ct.setAccessible(true);
        Object separate = ct.newInstance(230, 139, 230, 139, 1, new int[] {139 * W + 230});
        var page = new DetachedCaretBodies.Page(raw, labels, W, H, notes);
        assertFalse(blocked(page, separate, priority(page, glyph)));
    }

    @Test
    public void bookkeepingFailureNeverCancelsLegacyAcceptance() {
        byte[] raw = DetachedCaretOriginalTest.paper();
        DetachedCaretOriginalTest.caret(raw, 107, 75);
        assertEquals(
                2,
                NoteArticulationDetector.detectWithWordBodies(
                        new byte[raw.length],
                        raw,
                        DetachedCaretOriginalTest.W,
                        DetachedCaretOriginalTest.H,
                        DetachedCaretOriginalTest.one(),
                        List.of(),
                        128,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE)[0]);
    }
}
