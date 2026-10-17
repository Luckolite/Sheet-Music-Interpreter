// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class QuantizedGracePairBeamTest {
    static final int W = 400, H = 240;

    static void ink(byte[] l, byte[] g, int x, int y, int label) {
        l[y * W + x] = (byte) label;
        g[y * W + x] = 0;
    }

    static void oval(
            byte[] l, byte[] g, float cx, float cy, float rx, float ry, int label, boolean gray) {
        for (int y = (int) Math.ceil(cy - ry); y <= cy + ry; y++)
            for (int x = (int) Math.ceil(cx - rx); x <= cx + rx; x++)
                if (Math.pow((x - cx) / rx, 2) + Math.pow((y - cy) / ry, 2) <= 1) {
                    l[y * W + x] = (byte) label;
                    if (gray) g[y * W + x] = 0;
                }
    }

    /** Independent ellipses and parallel rails; segmentation envelopes are rasterized separately. */
    static List<ScoreNoteEvent> run(
            int gap, float phase, int labelWidth, int beams, boolean principal, boolean large) {
        byte[] l = new byte[W * H], g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        int bottom = 150;
        for (int j = 0; j < 5; j++)
            for (int x = 15; x < W - 15; x++) ink(l, g, x, bottom - j * gap, 4);
        float ax = 100 + phase,
                bx = ax + gap * 1.18f,
                ay = Math.round(bottom - gap * .25f),
                by = Math.round(ay + gap * .48f);
        int sx = Math.round(ax + gap * .42f),
                tx = Math.round(bx + gap * .42f),
                sy = Math.round(ay - gap * 2.1f),
                ty = sy + Math.round(gap * .2f);
        for (int k = 0; k < 2; k++) {
            float x = k == 0 ? ax : bx, y = k == 0 ? ay : by;
            oval(l, g, x, y, large ? gap * .7f : gap * .49f, gap * .38f, 2, true);
            oval(l, g, x, y, (labelWidth - 1) / 2f, gap * .38f, 2, false);
            int xx = k == 0 ? sx : tx, top = k == 0 ? sy : ty;
            for (int yy = top; yy <= y; yy++) ink(l, g, xx, yy, 1);
        }
        for (int x = sx; x <= tx; x++)
            for (int b = 0; b < beams; b++)
                for (int d = 0; d < 3; d++) {
                    int y =
                            sy
                                    + Math.round((x - sx) * (ty - sy) / (float) (tx - sx))
                                    + b * Math.round(gap * .47f)
                                    + d;
                    g[y * W + x] = 0;
                }
        if (principal) {
            float x = bx + gap * 1.65f, y = by + gap * .4f;
            oval(l, g, x, y, gap * .77f, gap * .51f, 2, true);
            for (int yy = Math.round(y - gap * 3.8f); yy <= y; yy++)
                ink(l, g, Math.round(x + gap * .7f), yy, 1);
        }
        byte[] beforeL = l.clone(), beforeG = g.clone();
        var out =
                OmrScoreInterpreter.analyze(
                                l, g, W, H, List.of(new MeasureRegion(0, 1, .02f, .98f)))
                        .notes();
        assertArrayEquals(beforeL, l);
        assertArrayEquals(beforeG, g);
        return out;
    }

    // Exercise the size guard independently of the analyze-level role proof above.
    static boolean acceptedEnvelope(int width, boolean existingGracePair) throws Exception {
        var hc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        var dc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$DetectedNote")
                        .getDeclaredConstructors()[0];
        hc.setAccessible(true);
        dc.setAccessible(true);
        var head = hc.newInstance(138, 100, 100 + width - 1, 144, 155, 107f, 150f);
        var event =
                new ScoreNoteEvent(0, .4f, 1, 0, 1, .5f, false, 0, 1, 2, 0)
                        .withArticulations(NoteOrnament.GRACE);
        Object detected = dc.newInstance(event, head, 15f);
        try {
            var m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "reducedPairedGrace", dc.getDeclaringClass(), boolean.class);
            m.setAccessible(true);
            return (boolean) m.invoke(null, detected, existingGracePair);
        } catch (NoSuchMethodException priorSignature) {
            var m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "reducedPairedGrace", dc.getDeclaringClass());
            m.setAccessible(true);
            return (boolean) m.invoke(null, detected);
        }
    }

    static void assertDouble(List<ScoreNoteEvent> n) {
        assertEquals(3, n.size());
        for (int i = 0; i < 2; i++) {
            assertTrue((n.get(i).articulations() & NoteOrnament.GRACE) != 0);
            assertEquals(2, n.get(i).beamCount());
        }
        assertEquals(0, n.get(2).articulations() & NoteOrnament.GRACE);
    }

    @Test
    public void halfPixelPairAtFifteenPixelStaffKeepsBothPrintedRails() {
        assertDouble(run(15, .5f, 16, 2, true, false));
    }

    @Test
    public void halfPixelPairAtSeventeenPixelStaffKeepsBothPrintedRails() {
        assertDouble(run(17, .5f, 18, 2, true, false));
    }

    @Test
    public void halfPixelPairAtNineteenPixelStaffKeepsBothPrintedRails() {
        assertDouble(run(19, .5f, 20, 2, true, false));
    }

    @Test
    public void integerAndQuarterPixelPhasesRetainBothPrintedRails() {
        for (int gap : new int[] {15, 17, 19})
            for (float phase : new float[] {0, .25f, .75f})
                assertDouble(run(gap, phase, (int) Math.ceil(gap * 1.05f), 2, true, false));
    }

    @Test
    public void narrowerExistingPairRemainsSupported() {
        for (int gap : new int[] {15, 17, 19})
            for (float phase : new float[] {0, .5f})
                assertDouble(run(gap, phase, (int) Math.floor(gap * 1.05f), 2, true, false));
    }

    @Test
    public void anotherWholePixelBeyondEnvelopeIsNotAdmitted() throws Exception {
        var n = run(15, 0, 17, 2, true, false);
        assertEquals(3, n.size());
        assertTrue(n.stream().noneMatch(a -> (a.articulations() & NoteOrnament.GRACE) != 0));
        assertFalse(acceptedEnvelope(17, true));
        assertEquals(0, n.get(1).beamCount());
    }

    @Test
    public void ordinaryUniformPairCannotBorrowTheRoundingAllowance() {
        var n = run(15, .5f, 16, 2, false, false);
        assertEquals(2, n.size());
        assertTrue(n.stream().noneMatch(a -> (a.articulations() & NoteOrnament.GRACE) != 0));
        assertEquals(0, n.get(1).beamCount());
    }

    @Test
    public void ordinaryFullSizeTouchingHeadsAreNotConvertedToGrace() {
        var n = run(15, .5f, 24, 2, true, true);
        assertTrue(n.stream().noneMatch(a -> (a.articulations() & NoteOrnament.GRACE) != 0));
    }

    @Test
    public void aSinglePrintedRailCannotBePromotedToTwo() {
        var n = run(15, .5f, 16, 1, true, false);
        assertEquals(3, n.size());
        assertTrue(n.stream().allMatch(a -> a.beamCount() < 2));
    }

    @Test
    public void ordinaryChordWithTwoRailsRetainsMetricalOwnership() {
        byte[] l = new byte[W * H], g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 90; y <= 150; y += 15) for (int x = 15; x < W - 15; x++) ink(l, g, x, y, 4);
        for (int y : new int[] {128, 150}) oval(l, g, 100, y, 11, 7, 2, true);
        oval(l, g, 145, 150, 11, 7, 2, true);
        for (int y = 80; y <= 150; y++) {
            ink(l, g, 111, y, 1);
            ink(l, g, 156, y, 1);
        }
        for (int x = 111; x <= 156; x++)
            for (int b = 0; b < 2; b++) for (int d = 0; d < 3; d++) ink(l, g, x, 80 + b * 8 + d, 5);
        var n =
                OmrScoreInterpreter.analyze(
                                l, g, W, H, List.of(new MeasureRegion(0, 1, .02f, .98f)))
                        .notes();
        assertEquals(3, n.size());
        assertTrue(n.stream().noneMatch(a -> (a.articulations() & NoteOrnament.GRACE) != 0));
    }
}
