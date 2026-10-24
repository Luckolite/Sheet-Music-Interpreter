// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PrintedValueRouteOriginalTest {
    private static final int W = 96, H = 96;

    private static Object component(int area) throws Exception {
        Class<?> c = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor.newInstance(area, 40, 54, 42, 50, 47f, 46f);
    }

    private static OmrScoreInterpreter.PrintedValueDecision decision(
            byte[] l, byte[] g, Object head, int beams) throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectPrintedValue",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        head.getClass(),
                        float.class,
                        int.class,
                        List.class);
        m.setAccessible(true);
        return (OmrScoreInterpreter.PrintedValueDecision)
                m.invoke(null, l, g, W, H, head, 10f, beams, List.of(head));
    }

    @Test
    public void literalDefaultIsNotFilledProofEvenForDarkOrEmptyPixels() throws Exception {
        for (boolean paint : new boolean[] {false, true}) {
            byte[] l = new byte[W * H], g = new byte[W * H];
            Arrays.fill(g, (byte) 255);
            for (int y = 42; y <= 50; y++)
                for (int x = 40; x <= 54; x++) {
                    g[y * W + x] = 0;
                    if (paint) l[y * W + x] = 2;
                }
            var d = decision(l, g, component(135), 0);
            assertTrue(d.reachedOpaqueDefaultQuarter());
            assertEquals(1f, d.value(), 0f);
            assertTrue(d.observations().contains("NO_OPEN_CENTER_OBSERVED_NOT_A_FILLED_PROOF"));
        }
    }

    @Test
    public void semanticOpenHalfWholeAndBeamedExitsStayDistinct() throws Exception {
        for (boolean stem : new boolean[] {false, true}) {
            byte[] l = new byte[W * H];
            if (stem)
                for (int y = 20; y <= 42; y++) l[y * W + 54] = OmrMeasurePostProcessor.STEM_OR_REST;
            var d = decision(l, null, component(30), 0);
            assertFalse(d.reachedOpaqueDefaultQuarter());
            assertEquals(
                    stem
                            ? OmrScoreInterpreter.PrintedValueExit.OPEN_WITH_STEM_HALF
                            : OmrScoreInterpreter.PrintedValueExit.OPEN_WITHOUT_STEM_WHOLE,
                    d.exit());
            assertEquals(stem ? 2f : 4f, d.value(), 0f);
            assertTrue(d.observations().contains("SEMANTIC_FILL_ONLY_NO_VALID_RAW_PLANE"));
        }
        var d = decision(new byte[W * H], null, component(135), 2);
        assertEquals(OmrScoreInterpreter.PrintedValueExit.BEAMED_INPUT, d.exit());
        assertFalse(d.reachedOpaqueDefaultQuarter());
        assertEquals(2, d.inputBeamCount());
    }

    @Test
    public void onlyRealDetectorMintsImmutableReceipt() throws Exception {
        assertTrue(
                Arrays.stream(
                                OmrScoreInterpreter.PrintedValueDecision.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        var d = decision(new byte[W * H], null, component(135), 0);
        assertThrows(UnsupportedOperationException.class, () -> d.observations().clear());
    }

    @Test
    public void receiptCannotBorrowEqualValueForeignSourceOrHead() throws Exception {
        byte[] l = new byte[W * H], g = new byte[W * H];
        for (int y = 42; y <= 50; y++) for (int x = 40; x <= 54; x++) l[y * W + x] = 2;
        Object h = component(135), foreign = component(135);
        var d = decision(l, g, h, 0);
        var b = new ExactComponentMembers.Budget(100000, 100000);
        var c = new ExactComponentMembers.Collector(g, l, W, H, b, b);
        assertTrue(c.bindInput(l, g, W, H));
        var a = c.begin();
        for (int y = 42; y <= 50; y++) a.add(y, 40, 54);
        c.record(h, new ExactComponentMembers.Stats(135, 40, 54, 42, 50, 47, 46), (byte) 2, a);
        assertTrue(c.seal(b));
        assertTrue(d.matches(c, h));
        assertFalse(d.matches(c, foreign));
        var other = new ExactComponentMembers.Collector(g.clone(), l.clone(), W, H, b, b);
        assertFalse(d.matches(other, h));
    }
}
