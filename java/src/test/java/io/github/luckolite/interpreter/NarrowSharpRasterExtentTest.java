// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rectangle-drawn sharp; no publisher glyph, score crop, or model pixels. */
public class NarrowSharpRasterExtentTest {
    static final int W = 80, H = 64;

    static final class Drawing {
        final byte[] labels = new byte[W * H];

        void box(int x, int y, int w, int h) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) labels[yy * W + xx] = 3;
        }

        void sharp() {
            box(11, 13, 2, 26);
            box(16, 10, 2, 26);
            box(10, 19, 9, 3);
            box(10, 28, 9, 3);
        }
    }

    Object construct(String name, Object... args) throws Exception {
        var type = Class.forName(OmrScoreInterpreter.class.getName() + "$" + name);
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    Object candidate(Drawing d) throws Exception {
        int area = 0;
        long sx = 0, sy = 0;
        int l = W, r = -1, t = H, b = -1;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if (d.labels[y * W + x] == 3) {
                    area++;
                    sx += x;
                    sy += y;
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                }
        return construct(
                "AccidentalCandidate",
                construct("Component", area, l, r, t, b, sx / (float) area, sy / (float) area),
                (byte) 3);
    }

    boolean recognized(Drawing d, float gap) throws Exception {
        var ac = Class.forName(OmrScoreInterpreter.class.getName() + "$AccidentalCandidate");
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "isSharpGlyph", byte[].class, int.class, int.class, ac, float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, d.labels, W, H, candidate(d), gap);
    }

    int selected(Drawing d, float gap) throws Exception {
        var co = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectWrittenAccidental",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        co,
                        float.class);
        m.setAccessible(true);
        Object head = construct("Component", 100, 31, 43, 20, 29, 37f, 24.5f);
        return (int) m.invoke(null, d.labels, W, H, List.of(candidate(d)), head, gap);
    }

    @Test
    public void existingFractionalGapControlRecognizesCompleteNarrowSharp() throws Exception {
        var d = new Drawing();
        d.sharp();
        assertTrue(recognized(d, 13.8f));
        assertEquals(1, selected(d, 13.8f));
    }

    @Test
    public void subpixelStaffGapDoesNotDiscardSameCompleteNinePixelSharp() throws Exception {
        var d = new Drawing();
        d.sharp();
        assertTrue(recognized(d, 13.875f));
    }

    @Test
    public void integerStaffGapAdmitsCompleteRoundedMinimumExtent() throws Exception {
        var d = new Drawing();
        d.sharp();
        assertTrue(recognized(d, 14f));
    }

    @Test
    public void localHeadReceivesCompleteSharpAtRoundedMinimumExtent() throws Exception {
        var d = new Drawing();
        d.sharp();
        assertEquals(1, selected(d, 14f));
    }

    @Test
    public void substantiallyNarrowerExtentStaysRejected() throws Exception {
        var d = new Drawing();
        d.box(11, 13, 2, 26);
        d.box(15, 10, 2, 26);
        d.box(10, 19, 8, 3);
        d.box(10, 28, 8, 3);
        assertFalse(recognized(d, 14f));
    }

    @Test
    public void disconnectedParallelSpinesStayRejected() throws Exception {
        var d = new Drawing();
        d.box(11, 13, 2, 26);
        d.box(16, 10, 2, 26);
        assertFalse(recognized(d, 14f));
    }

    @Test
    public void oneCrossbarCannotBecomeSharp() throws Exception {
        var d = new Drawing();
        d.box(11, 13, 2, 26);
        d.box(16, 10, 2, 26);
        d.box(10, 19, 9, 3);
        assertFalse(recognized(d, 14f));
    }

    @Test
    public void naturalShapedOffsetSpinesStayRejected() throws Exception {
        var d = new Drawing();
        d.box(11, 10, 2, 22);
        d.box(16, 18, 2, 21);
        d.box(10, 19, 9, 3);
        d.box(10, 28, 9, 3);
        assertFalse(recognized(d, 14f));
    }
}
