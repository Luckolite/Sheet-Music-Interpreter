// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural geometry transported unchanged from pre-exposure ownership oracles. */
public class LocalSharpHeaderOwnershipTest {
    static final int W = 640, H = 260;

    static void lowerRules(OrderedSharpHeaderConflictTest.Page p) {
        for (int y = 180; y <= 244; y += 16) for (int x = 10; x < 630; x++) p.ink(x, y, 4);
    }

    static void head(OrderedSharpHeaderConflictTest.Page p, int x, int y) {
        for (int yy = -4; yy <= 4; yy++)
            for (int xx = -6; xx <= 6; xx++)
                if (xx * xx / 36. + yy * yy / 16. <= 1) p.ink(x + xx, y + yy, 2);
    }

    static void rawSharp(byte[] g, int x, int y) {
        for (int xx : new int[] {x - 5, x - 4, x, x + 1})
            for (int yy = y - 22; yy <= y + 22; yy++) g[yy * W + xx] = 0;
        for (int cy : new int[] {y - 5, y + 5})
            for (int yy = cy - 2; yy <= cy + 3; yy++)
                for (int xx = x - 8; xx <= x + 7; xx++) g[yy * W + xx] = 0;
    }

    @SuppressWarnings("unchecked")
    static List<ScoreKeyChange> keys(OrderedSharpHeaderConflictTest.Page p) throws Exception {
        Class<?> o = OmrScoreInterpreter.class;
        var sc = Class.forName(o.getName() + "$Staff").getDeclaredConstructors()[0];
        sc.setAccessible(true);
        var ac = Class.forName(o.getName() + "$AccidentalCandidate").getDeclaredConstructors()[0];
        ac.setAccessible(true);
        var components =
                o.getDeclaredMethod(
                        "findComponents", byte[].class, int.class, int.class, byte.class);
        components.setAccessible(true);
        List<Object> candidates = new ArrayList<>();
        for (Object c : (List<?>) components.invoke(null, p.labels, W, H, (byte) 3))
            candidates.add(ac.newInstance(c, (byte) 3));
        List<?> heads = (List<?>) components.invoke(null, p.labels, W, H, (byte) 2);
        byte[] labels = p.labels.clone(), gray = p.gray.clone();
        var result =
                (List<ScoreKeyChange>)
                        OrderedSharpHeaderConflictTest.method("detectKeyChangesWithHeaders")
                                .invoke(
                                        null,
                                        p.labels,
                                        p.gray,
                                        W,
                                        H,
                                        List.of(
                                                new MeasureRegion(
                                                        250f / W, 620f / W, 65f / H, 256f / H)),
                                        List.of(
                                                sc.newInstance(100f, 164f, 16f),
                                                sc.newInstance(180f, 244f, 16f)),
                                        candidates,
                                        heads,
                                        new ArrayList<>());
        assertArrayEquals(labels, p.labels);
        assertArrayEquals(gray, p.gray);
        return result;
    }

    @Test
    public void foreignLocalAccidentalCannotCompleteUpperHeader() throws Exception {
        for (int phase : new int[] {122, 123, 124, 125}) {
            var p = new OrderedSharpHeaderConflictTest.Page(121, false, true, false);
            p.clearSymbols();
            lowerRules(p);
            p.sharp(80, 100, false);
            p.sharp(101, phase + 56, true);
            head(p, 300, 132);
            head(p, 300, phase + 56);
            assertEquals("foreign phase " + phase, List.of(new ScoreKeyChange(0, 1)), keys(p));
        }
    }

    @Test
    public void semanticCenterCannotBorrowForeignRawCrossbars() throws Exception {
        var p = new OrderedSharpHeaderConflictTest.Page(121, false, true, false);
        p.clearSymbols();
        lowerRules(p);
        p.sharp(80, 100, false);
        p.sharp(101, 171, true);
        for (int y = 140; y < 220; y++)
            for (int x = 90; x < 112; x++)
                p.gray[y * W + x] =
                        (byte)
                                ((y >= 100 && y <= 164 && (y - 100) % 16 == 0
                                                || y >= 180 && y <= 244 && (y - 180) % 16 == 0)
                                        ? 0
                                        : 255);
        rawSharp(p.gray, 101, 179);
        head(p, 300, 132);
        head(p, 300, 179);
        assertEquals(List.of(), keys(p));
    }

    @Test
    public void genuineLowerHeaderSurvivesStemsCrossingUpperCore() throws Exception {
        var p = new OrderedSharpHeaderConflictTest.Page(121, false, false, false);
        p.clearSymbols();
        lowerRules(p);
        for (int y = 150; y < 260; y++) for (int x = 30; x <= 55; x++) p.ink(x, y, 3);
        p.sharp(80, 180, false);
        p.sharp(101, 201, true);
        head(p, 300, 132);
        head(p, 300, 212);
        assertEquals(List.of(new ScoreKeyChange(0, 2)), keys(p));
    }
}
