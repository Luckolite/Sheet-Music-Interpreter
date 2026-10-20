// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent min-composited hanging-rest and ending-bar originals. */
public class EndingBarWholeRestTest {
    static final int W = 520, H = 280;

    static final class Scene {
        byte[] g = new byte[W * H];
        int gap, top, edge, left, right, first;
        MeasureRegion m;

        Scene(int gap, int phase) {
            this.gap = gap;
            top = 64 + phase;
            edge = 352 + phase;
            Arrays.fill(g, (byte) 255);
            for (int k = 0; k < 5; k++) box(20, top + k * gap, 490, top + k * gap, 100);
            int cx = edge - 2 * gap;
            left = cx - Math.round(.7f * gap);
            right = cx + Math.round(.7f * gap);
            first = top + gap + 1;
            box(left, first, right, first + Math.max(4, Math.round(.4f * gap)) - 1, 95);
            m =
                    new MeasureRegion(
                            (edge - 6f * gap) / W,
                            edge / (float) W,
                            (top - 12f) / H,
                            (top + 4f * gap + 12) / H);
        }

        void box(int l, int t, int r, int b, int ink) {
            for (int y = t; y <= b; y++)
                for (int x = l; x <= r; x++)
                    g[y * W + x] = (byte) Math.min(g[y * W + x] & 255, ink);
        }

        void bar(int kind) {
            int x = edge - 1;
            int t = top, b = top + 4 * gap;
            if (kind == 1) t = top + gap / 2;
            if (kind == 2) {
                t = top + gap + 2;
                b = t + gap / 2;
            }
            if (kind == 3) {
                t -= 4;
                b += 4;
            }
            box(x, t, x, b, 90);
            if (kind == 4) g[(top + 3 * gap + gap / 2) * W + x] = (byte) 255;
        }

        List<WholeRestClassifier.Decision> read() {
            return WholeRestClassifier.detect(
                    g,
                    W,
                    H,
                    List.of(m),
                    List.of(new SixteenthRestDetector.Staff(top, top + 4 * gap, gap, 0, 1)),
                    List.of(),
                    List.of(),
                    Set.of(0));
        }
    }

    static void full(int g, int p, boolean bar) {
        Scene s = new Scene(g, p);
        if (bar) s.bar(0);
        byte[] before = s.g.clone();
        var d = s.read();
        assertEquals(1, d.size());
        assertEquals(d.toString(), WholeRestClassifier.Status.PROVED_FULL, d.get(0).status());
        assertTrue(d.get(0).event().isFullMeasure());
        assertArrayEquals(before, s.g);
    }

    static void unresolved(Scene s) {
        var d = s.read();
        assertEquals(d.toString(), 1, d.size());
        assertEquals(d.toString(), WholeRestClassifier.Status.UNRESOLVED, d.get(0).status());
        assertFalse(d.get(0).event().isFullMeasure());
    }

    @Test
    public void endingBarsScale12() {
        full(12, 0, true);
    }

    @Test
    public void endingBarsScale16Shift1() {
        full(16, 1, true);
    }

    @Test
    public void endingBarsScale20() {
        full(20, 0, true);
    }

    @Test
    public void absentBarPreservesFullPlate() {
        full(16, 0, false);
    }

    @Test
    public void actualAugmentationDotBeforeBarRemainsLiteral() {
        Scene s = new Scene(16, 0);
        s.bar(0);
        int x = s.right + 10, y = s.top + s.gap + 7;
        s.box(x - 1, y - 1, x + 1, y + 1, 90);
        var d = s.read();
        assertEquals(1, d.size());
        assertFalse(d.toString(), d.get(0).event().isFullMeasure());
        assertEquals(6, d.get(0).event().durationBeats(), 0);
    }

    @Test
    public void attachedDotCannotBorrowBar() {
        Scene s = new Scene(16, 0);
        s.bar(0);
        s.box(s.edge - 4, s.first + 3, s.edge - 2, s.first + 5, 105);
        unresolved(s);
    }

    @Test
    public void independentHeadExcessBeforeBarStaysUnknown() {
        Scene s = new Scene(16, 0);
        s.bar(0);
        int cx = s.edge - 8, cy = s.first + 5;
        for (int y = cy - 3; y <= cy + 3; y++)
            for (int x = cx - 4; x <= cx + 4; x++)
                if ((x - cx) * (x - cx) / 16d + (y - cy) * (y - cy) / 9d <= 1)
                    s.box(x, y, x, y, 105);
        unresolved(s);
    }

    @Test
    public void incompleteBarStaysUnknown() {
        Scene s = new Scene(16, 0);
        s.bar(4);
        unresolved(s);
    }

    @Test
    public void ordinaryStemStaysUnknown() {
        Scene s = new Scene(16, 0);
        s.bar(1);
        unresolved(s);
    }

    @Test
    public void shortStrokeStaysUnknown() {
        Scene s = new Scene(16, 0);
        s.bar(2);
        unresolved(s);
    }

    @Test
    public void overlongStemStaysUnknown() {
        Scene s = new Scene(16, 0);
        s.bar(3);
        unresolved(s);
    }

    @Test
    public void prefixExcessNeverBecomesFull() {
        Scene s = new Scene(16, 0);
        s.bar(0);
        s.box(s.left - 5, s.first, s.left - 3, s.first + 6, 110);
        for (var d : s.read()) assertFalse(d.toString(), d.event().isFullMeasure());
    }
}
