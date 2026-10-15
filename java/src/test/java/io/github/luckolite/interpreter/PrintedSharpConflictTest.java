// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original header records and symbol bounds; no score-derived pixels or identities. */
public class PrintedSharpConflictTest {
    static Class<?> c, st, g;
    static Method apply, accidental;

    static {
        try {
            String n = OmrScoreInterpreter.class.getName();
            c = Class.forName(n + "$Component");
            st = Class.forName(n + "$Staff");
            g = Class.forName(n + "$SignatureGlyph");
            apply =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "corroboratePrintedSharpHeader",
                            List.class,
                            List.class,
                            c,
                            st,
                            boolean.class,
                            float.class,
                            float.class);
            apply.setAccessible(true);
            accidental = g.getDeclaredMethod("accidental");
            accidental.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Object make(Class<?> type, Class<?>[] types, Object... args) throws Exception {
        var constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    static Object box(int x, int y) throws Exception {
        return make(
                c,
                new Class[] {
                    int.class, int.class, int.class, int.class, int.class, float.class, float.class
                },
                100,
                x - 3,
                x + 3,
                y - 10,
                y + 10,
                (float) x,
                (float) y);
    }

    static Object glyph(int x, int family, int y, boolean body) throws Exception {
        return make(
                g,
                new Class[] {float.class, int.class, float.class, c},
                (float) x,
                family,
                (float) y,
                body ? box(x, y) : null);
    }

    static int result(int length, String mutation, int semanticFamily) throws Exception {
        int[] ys = {100, 115, 95, 110, 125, 105, 120};
        List<Object> raw = new ArrayList<>();
        for (int i = 0; i < length; i++)
            raw.add(
                    glyph(
                            105 + 12 * i + (mutation.equals("spacing") && i == 1 ? 8 : 0),
                            mutation.equals("mixed") && i == 1 ? -1 : 1,
                            ys[i] + (mutation.equals("pitch") && i == 1 ? 10 : 0),
                            !(mutation.equals("partial") && i == 1)));
        List<Object> semantic = new ArrayList<>();
        semantic.add(glyph(117, semanticFamily, 115, false));
        Object staff =
                make(st, new Class[] {float.class, float.class, float.class}, 100f, 140f, 10f);
        apply.invoke(
                null,
                semantic,
                raw,
                mutation.equals("clef") ? null : box(87, 120),
                staff,
                !mutation.equals("row"),
                90f,
                mutation.equals("clipped") ? 118f : 200f);
        return (int) accidental.invoke(semantic.get(0));
    }

    @Test
    public void completeOrderedHeadersRepairConflictingFlat() throws Exception {
        for (int n = 2; n <= 7; n++) assertEquals(1, result(n, "", -1));
    }

    @Test
    public void incompleteAndConflictingRawEvidenceCannotOverride() throws Exception {
        for (String mutation :
                List.of("spacing", "mixed", "pitch", "partial", "clef", "row", "clipped"))
            assertEquals(mutation, -1, result(4, mutation, -1));
    }

    @Test
    public void oneSharpCannotEstablishSequence() throws Exception {
        assertEquals(-1, result(1, "", -1));
    }

    @Test
    public void naturalAndAlreadySharpRemainUnchanged() throws Exception {
        assertEquals(0, result(4, "", 0));
        assertEquals(1, result(4, "", 1));
    }
}
