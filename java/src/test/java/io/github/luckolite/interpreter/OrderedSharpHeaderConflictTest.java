// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural sharp/staff drawings; exercises the production key API directly. */
public class OrderedSharpHeaderConflictTest {
    static final int W = 640, H = 260;

    static class Page {
        final byte[] labels = new byte[W * H], gray = new byte[W * H];

        Page(int secondPitch, boolean second, boolean clef, boolean narrow) {
            Arrays.fill(gray, (byte) 255);
            for (int y = 100; y <= 164; y += 16) for (int x = 10; x < 630; x++) ink(x, y, 4);
            if (clef) for (int y = 70; y <= 180; y++) for (int x = 30; x <= 55; x++) ink(x, y, 3);
            sharp(80, 100, false);
            if (second) sharp(101, secondPitch, narrow);
        }

        void ink(int x, int y, int label) {
            labels[y * W + x] = (byte) label;
            gray[y * W + x] = 0;
        }

        void sharp(int x, int y, boolean narrow) {
            for (int xx :
                    narrow
                            ? new int[] {x - 5, x - 4, x, x + 1}
                            : new int[] {x - 5, x - 4, x + 3, x + 4})
                for (int yy = y - 22; yy <= y + 22; yy++) ink(xx, yy, 3);
            for (int cy : new int[] {y - 5, y + 5})
                for (int yy = narrow ? cy - 2 : cy - 1; yy <= (narrow ? cy + 3 : cy + 1); yy++)
                    for (int xx = x - 8; xx <= x + 7; xx++) ink(xx, yy, 3);
        }

        void natural() {
            for (int y = 110; y <= 141; y++) for (int x = 75; x <= 76; x++) ink(x, y, 3);
            for (int y = 123; y <= 154; y++) for (int x = 84; x <= 85; x++) ink(x, y, 3);
            for (int cy : new int[] {124, 140})
                for (int y = cy - 1; y <= cy + 1; y++) for (int x = 76; x <= 84; x++) ink(x, y, 3);
        }

        void clearSymbols() {
            for (int y = 50; y < 190; y++)
                for (int x = 60; x < 140; x++) {
                    labels[y * W + x] =
                            (byte) (y >= 100 && y <= 164 && (y - 100) % 16 == 0 ? 4 : 0);
                    gray[y * W + x] = (byte) (labels[y * W + x] == 4 ? 0 : 255);
                }
        }

        void flat(int x, int y) {
            for (int yy = y - 28; yy <= y + 7; yy++)
                for (int xx = x; xx <= x + 2; xx++) ink(xx, yy, 3);
            for (int yy = y - 7; yy <= y + 7; yy++)
                for (int xx = x + 2; xx <= x + 12; xx++) {
                    double radius = Math.pow((xx - x - 3) / 9d, 2) + Math.pow((yy - y) / 7d, 2);
                    if (radius <= 1 && radius >= .35) ink(xx, yy, 3);
                }
        }

        @SuppressWarnings("unchecked")
        List<ScoreKeyChange> keys(boolean clipAtFirstSharp) throws Exception {
            Class<?> o = OmrScoreInterpreter.class;
            Constructor<?> sc = Class.forName(o.getName() + "$Staff").getDeclaredConstructors()[0];
            sc.setAccessible(true);
            Object staff = sc.newInstance(100f, 164f, 16f);
            Constructor<?> ac =
                    Class.forName(o.getName() + "$AccidentalCandidate")
                            .getDeclaredConstructors()[0];
            ac.setAccessible(true);
            List<Object> candidates = new ArrayList<>();
            Method components = method("findComponents");
            for (Object c : (List<?>) components.invoke(null, labels, W, H, (byte) 3))
                candidates.add(ac.newInstance(c, (byte) 3));
            List<Object> heads = new ArrayList<>();
            if (clipAtFirstSharp) {
                Constructor<?> c =
                        Class.forName(o.getName() + "$Component").getDeclaredConstructors()[0];
                c.setAccessible(true);
                heads.add(c.newInstance(100, 70, 86, 117, 129, 78f, 123f));
            }
            return (List<ScoreKeyChange>)
                    method("detectKeyChangesWithHeaders")
                            .invoke(
                                    null,
                                    labels,
                                    gray,
                                    W,
                                    H,
                                    List.of(
                                            new MeasureRegion(
                                                    180f / W, 620f / W, 65f / H, 190f / H)),
                                    List.of(staff),
                                    candidates,
                                    heads,
                                    new ArrayList<>());
        }
    }

    static Method method(String name) {
        Method m =
                Arrays.stream(OmrScoreInterpreter.class.getDeclaredMethods())
                        .filter(x -> x.getName().equals(name))
                        .findFirst()
                        .orElseThrow();
        m.setAccessible(true);
        return m;
    }

    @Test
    public void orderedCompleteSharpCohortResolvesFlatBowlConflict() throws Exception {
        assertEquals(
                List.of(new ScoreKeyChange(0, 2)), new Page(121, true, true, true).keys(false));
    }

    @Test
    public void intactTwoSharpHeaderIsPreserved() throws Exception {
        assertEquals(
                List.of(new ScoreKeyChange(0, 2)), new Page(124, true, true, false).keys(false));
    }

    @Test
    public void neighboringPrintedPhasesArePreserved() throws Exception {
        for (int phase : new int[] {120, 122, 123, 124, 125, 126, 127, 128})
            assertEquals(
                    "phase " + phase,
                    List.of(new ScoreKeyChange(0, 2)),
                    new Page(phase, true, true, true).keys(false));
    }

    @Test
    public void plainSingleSharpRemainsSingle() throws Exception {
        assertEquals(
                List.of(new ScoreKeyChange(0, 1)), new Page(121, false, true, false).keys(false));
    }

    @Test
    public void noCompleteClefDoesNotAdmitLeftHeader() throws Exception {
        assertEquals(List.of(), new Page(121, true, false, true).keys(false));
    }

    @Test
    public void headBeforeHeaderPreventsBorrowedSignature() throws Exception {
        assertEquals(List.of(), new Page(121, true, true, true).keys(true));
    }

    @Test
    public void headerInputsRemainUnchanged() throws Exception {
        Page p = new Page(121, true, true, true);
        byte[] l = p.labels.clone(), g = p.gray.clone();
        p.keys(false);
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }

    @Test
    public void orderedGenuineFlatHeaderRemainsFlat() throws Exception {
        Page p = new Page(121, false, true, false);
        p.clearSymbols();
        p.flat(80, 132);
        p.flat(101, 108);
        assertEquals(List.of(new ScoreKeyChange(0, -2)), p.keys(false));
    }

    @Test
    public void explicitNaturalHeaderRemainsNatural() throws Exception {
        Page p = new Page(121, false, true, false);
        p.clearSymbols();
        p.natural();
        assertEquals(List.of(new ScoreKeyChange(0, 0)), p.keys(false));
    }

    @Test
    public void loneConflictingGlyphDoesNotProveSharpHeader() throws Exception {
        Page p = new Page(121, false, true, false);
        p.clearSymbols();
        p.sharp(101, 121, true);
        assertEquals(List.of(), p.keys(false));
    }

    @Test
    public void wrongPitchOrderDoesNotResolveConflict() throws Exception {
        assertEquals(List.of(), new Page(140, true, true, true).keys(false));
    }

    @Test
    public void naturalPrefixPreventsSharpCohortOverride() throws Exception {
        Page p = new Page(121, false, true, false);
        p.clearSymbols();
        p.natural();
        p.sharp(108, 100, false);
        p.sharp(129, 121, true);
        assertEquals(List.of(), p.keys(false));
    }

    @Test
    public void distantStaffCannotCompleteHeaderCohort() throws Exception {
        assertEquals(
                List.of(new ScoreKeyChange(0, 1)), new Page(210, true, true, true).keys(false));
    }
}
