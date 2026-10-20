// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class EndingBarEnvelopeTest {
    static EndingBarWholeRestTest.Scene scene(int gap, int phase) {
        var s = new EndingBarWholeRestTest.Scene(gap, phase);
        s.bar(0);
        return s;
    }

    static void envelope(EndingBarWholeRestTest.Scene s, int topExtension, int bottomExtension) {
        int x = s.edge - 1;
        s.box(x, s.top - topExtension, x, s.top + 4 * s.gap + bottomExtension, 95);
        s.box(x + 1, s.top - topExtension, x + 1, s.top + 4 * s.gap + bottomExtension, 193);
    }

    static void full(EndingBarWholeRestTest.Scene s) {
        var d = s.read();
        assertEquals(1, d.size());
        assertEquals(d.toString(), WholeRestClassifier.Status.PROVED_FULL, d.get(0).status());
    }

    static void unknown(EndingBarWholeRestTest.Scene s) {
        var d = s.read();
        assertEquals(1, d.size());
        assertEquals(d.toString(), WholeRestClassifier.Status.UNRESOLVED, d.get(0).status());
    }

    @Test
    public void observedEnvelopeAtScale16() {
        var s = scene(16, 0);
        envelope(s, 1, 2);
        full(s);
    }

    @Test
    public void observedEnvelopeAtScale20Shift1() {
        var s = scene(20, 1);
        envelope(s, 2, 2);
        full(s);
    }

    @Test
    public void observedEnvelopeAtScale12() {
        var s = scene(12, 0);
        envelope(s, 1, 1);
        full(s);
    }

    @Test
    public void detachedInkInEndpointWindowIsNotContinuation() {
        var s = scene(16, 0);
        envelope(s, 0, 0);
        s.box(s.edge, s.top - 3, s.edge, s.top - 3, 193);
        unknown(s);
    }

    @Test
    public void overlongEnvelopeLacksObservedPaper() {
        var s = scene(16, 0);
        envelope(s, 4, 4);
        unknown(s);
    }

    @Test
    public void incompleteCoreCannotBorrowPaleEnvelope() {
        var s = scene(16, 0);
        envelope(s, 1, 1);
        s.g[(s.top + 2 * s.gap + 4) * EndingBarWholeRestTest.W + s.edge - 1] = (byte) 193;
        unknown(s);
    }

    @Test
    public void tooWideEnvelopeIsNotThinBar() {
        var s = scene(16, 0);
        envelope(s, 1, 1);
        s.box(s.edge - 3, s.top, s.edge + 1, s.top + 4 * s.gap, 193);
        unknown(s);
    }

    @Test
    public void attachedDotOutsideEnvelopeRemainsUnknown() {
        var s = scene(16, 0);
        envelope(s, 1, 1);
        s.box(s.edge - 4, s.first + 3, s.edge - 2, s.first + 5, 105);
        unknown(s);
    }

    @Test
    public void detachedSmallDotPreservesLiteral() {
        var s = scene(16, 0);
        envelope(s, 1, 1);
        int x = s.right + 10, y = s.top + s.gap + 7;
        s.box(x - 1, y - 1, x + 1, y + 1, 90);
        var d = s.read();
        assertEquals(1, d.size());
        assertFalse(d.toString(), d.get(0).event().isFullMeasure());
        assertEquals(6, d.get(0).event().durationBeats(), 0);
    }

    @Test
    public void headExcessKeepsAmbiguity() {
        var s = scene(16, 0);
        envelope(s, 1, 1);
        int cx = s.edge - 8, cy = s.first + 5;
        for (int y = cy - 3; y <= cy + 3; y++)
            for (int x = cx - 4; x <= cx + 4; x++)
                if ((x - cx) * (x - cx) / 16d + (y - cy) * (y - cy) / 9d <= 1)
                    s.box(x, y, x, y, 105);
        unknown(s);
    }

    @Test
    public void stemEndingInsideStaffIsNotBar() {
        var s = new EndingBarWholeRestTest.Scene(16, 0);
        s.bar(1);
        s.box(s.edge, s.top + 8, s.edge, s.top + 4 * s.gap, 193);
        unknown(s);
    }

    @Test
    public void prefixExcessRemainsUnaccepted() {
        var s = scene(16, 0);
        envelope(s, 1, 1);
        s.box(s.left - 5, s.first, s.left - 3, s.first + 6, 110);
        for (var d : s.read()) assertFalse(d.toString(), d.event().isFullMeasure());
    }

    @Test
    public void endpointWindowAtImageEdgeAbstains() {
        var s = scene(16, 0);
        envelope(s, 1, 1);
        int shift = 62;
        byte[] moved = new byte[s.g.length];
        java.util.Arrays.fill(moved, (byte) 255);
        for (int y = shift; y < EndingBarWholeRestTest.H; y++)
            System.arraycopy(
                    s.g,
                    y * EndingBarWholeRestTest.W,
                    moved,
                    (y - shift) * EndingBarWholeRestTest.W,
                    EndingBarWholeRestTest.W);
        s.g = moved;
        s.top -= shift;
        s.first -= shift;
        s.m =
                new MeasureRegion(
                        s.m.left(),
                        s.m.right(),
                        0,
                        (s.top + 4f * s.gap + 12) / EndingBarWholeRestTest.H);
        for (var d : s.read()) assertFalse(d.toString(), d.event().isFullMeasure());
    }
}
