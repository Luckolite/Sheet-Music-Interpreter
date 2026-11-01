// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original full caller: printed stem metadata arrives after the early legacy beam pass. */
public class OpposedBeamCallerOrderOriginalTest {
    static final int W = 600, H = 310, G = 14;
    static final MeasureRegion REGION = new MeasureRegion(.02f, .98f, .02f, .98f);

    static class Page {
        final byte[] l = new byte[W * H], g = new byte[W * H];

        Page(boolean beam, boolean stem) {
            Arrays.fill(g, (byte) 255);
            for (int top : new int[] {40, 166})
                for (int k = 0; k < 5; k++) rect(20, top + k * G, 560, 1, 4);
            rect(20, 40, 1, 183, 1);
            if (stem) rect(232, 68, 2, 73, 1);
            rect(338, 140, 2, 55, 1);
            if (beam) rect(232, 138, 108, 5, 5);
            // A genuinely independent two-rail bass run interleaves in source x order.
            rect(288, 156, 2, 67, 1);
            rect(308, 156, 2, 53, 1);
            rect(288, 166, 22, 5, 5);
            rect(288, 156, 22, 5, 5);
            oval(240, 68);
            oval(280, 222);
            oval(300, 208);
            oval(330, 194);
        }

        void rect(int x, int y, int w, int h, int kind) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) {
                    g[yy * W + xx] = 0;
                    l[yy * W + xx] = (byte) kind;
                }
        }

        void oval(int cx, int cy) {
            for (int y = -7; y <= 7; y++)
                for (int x = -9; x <= 9; x++)
                    if (x * x / 81. + y * y / 49. <= 1) rect(cx + x, cy + y, 1, 1, 2);
        }

        List<ScoreNoteEvent> read() {
            byte[] ll = l.clone(), gg = g.clone();
            var result = OmrScoreInterpreter.analyze(l, g, W, H, List.of(REGION)).notes();
            assertArrayEquals(ll, l);
            assertArrayEquals(gg, g);
            assertEquals("four semantic head bodies", 4, result.size());
            return result;
        }
    }

    static ScoreNoteEvent at(List<ScoreNoteEvent> notes, int x, int y) {
        var found =
                notes.stream()
                        .filter(
                                n ->
                                        Math.abs(
                                                                (REGION.left()
                                                                                        + (REGION
                                                                                                                .right()
                                                                                                        - REGION
                                                                                                                .left())
                                                                                                * n
                                                                                                        .positionInMeasure())
                                                                                * W
                                                                        - x)
                                                        < 1
                                                && Math.abs(n.pageY() * H - y) < 1)
                        .toList();
        assertEquals("unique original head " + x + "," + y + " records=" + notes, 1, found.size());
        return found.get(0);
    }

    @Test
    public void normalAnalyzeMarksOpposedPairAfterDirections() {
        var notes = new Page(true, true).read();
        var first = at(notes, 240, 68);
        var second = at(notes, 330, 194);
        assertEquals(1, first.beamCount());
        assertEquals(1, second.beamCount());
        assertEquals(-1, first.stemDirection());
        assertEquals(1, second.stemDirection());
        assertEquals(0, first.staffIndex());
        assertEquals(1, second.staffIndex());
        assertEquals(2, first.staffCount());
        assertEquals(2, second.staffCount());
        assertEquals(
                "independent first bass literal two rails " + notes,
                2,
                at(notes, 280, 222).beamCount());
        assertEquals(
                "independent second bass literal two rails " + notes,
                2,
                at(notes, 300, 208).beamCount());
        assertTrue("normal postdirection first owner", first.crossStaffBeam());
        assertTrue("normal postdirection second owner", second.crossStaffBeam());
        assertFalse(at(notes, 280, 222).crossStaffBeam());
        assertFalse(at(notes, 300, 208).crossStaffBeam());
    }

    @Test
    public void missingLiteralRailDoesNotGainLateAuthority() {
        var n = new Page(false, true).read();
        assertFalse(at(n, 240, 68).crossStaffBeam());
        assertFalse(at(n, 330, 194).crossStaffBeam());
    }

    @Test
    public void missingOwnedShaftDoesNotGainLateAuthority() {
        var n = new Page(true, false).read();
        assertFalse(at(n, 240, 68).crossStaffBeam());
        assertFalse(at(n, 330, 194).crossStaffBeam());
    }
}
