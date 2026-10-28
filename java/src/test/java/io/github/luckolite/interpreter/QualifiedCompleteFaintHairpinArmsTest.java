// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original complete compact wedges; a separately inked printed level is required. */
public class QualifiedCompleteFaintHairpinArmsTest {
    static final int W = 400, H = 240;
    static final float GAP = 10;

    static byte[] page(boolean reverse, float phase, String mode, int span, int opening) {
        byte[] r = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        for (int x = 130; x <= 130 + span; x++) {
            int xx = reverse ? 260 + span - x : x;
            int d = (int) Math.round(opening * (x - 130) / (double) span);
            int a = (int) Math.round(132 - d + phase), b = (int) Math.round(132 + d + phase);
            r[a * W + xx] = 0;
            r[b * W + xx] = 0;
            if (mode.equals("filled")) for (int y = a; y <= b; y++) r[y * W + xx] = 0;
            if (mode.equals("extra")) r[132 * W + xx] = 0;
        }
        if (!mode.equals("unprinted"))
            for (int y = 126; y < 138; y++) for (int x = 110; x < 117; x++) r[y * W + x] = 0;
        if (mode.equals("gap")) for (int y = 110; y < 155; y++) r[y * W + 150] = (byte) 255;
        return r;
    }

    static PlayingTechniqueDetector.Word word(String text, int left, int top, int right) {
        return new PlayingTechniqueDetector.Word(
                text, left / (float) W, top / (float) H, right / (float) W, (top + 12) / (float) H);
    }

    static List<ScoreDynamicChange> ramps(byte[] r, PlayingTechniqueDetector.Word word) {
        return ScoreDynamicsDetector.detect(
                        word == null ? List.of() : List.of(word),
                        List.of(new PlayingTechniqueDetector.Staff(60, 100, GAP, 0, 1)),
                        List.of(new MeasureRegion(.1f, .9f, .2f, .45f)),
                        List.of(),
                        r,
                        W,
                        H)
                .stream()
                .filter(c -> c.direction() != 0)
                .toList();
    }

    @Test
    public void completeCompactGeometryCanReplaceCoarseAspect() {
        var a = ramps(page(false, 0, "normal", 39, 8), word("p", 110, 126, 118));
        assertEquals(a.toString(), 1, a.size());
        assertEquals(1, a.get(0).direction());
    }

    @Test
    public void completeQualifiedMirrorsAndPhases() {
        for (boolean reverse : new boolean[] {false, true})
            for (float phase : new float[] {0, .25f, .5f, .75f}) {
                var a = ramps(page(reverse, phase, "normal", 39, 8), word("p", 110, 126, 118));
                assertEquals(a.toString(), 1, a.size());
                assertEquals(reverse ? -1 : 1, a.get(0).direction());
            }
    }

    @Test
    public void ordinaryQualifiedShortVectorRemainsExact() {
        var a = ramps(page(false, 0, "normal", 40, 7), word("p", 110, 126, 118));
        assertEquals(1, a.size());
        assertEquals((130f / W - .1f) / .8f, a.get(0).positionInMeasure(), .000001f);
        assertEquals((170f / W - .1f) / .8f, a.get(0).endPosition(), .000001f);
    }

    @Test
    public void isolatedCompactOperatorHasNoPrintedRole() {
        assertTrue(ramps(page(false, 0, "normal", 39, 8), null).isEmpty());
    }

    @Test
    public void unprintedWordCannotBorrowTheWedge() {
        assertTrue(ramps(page(false, 0, "unprinted", 39, 8), word("p", 110, 126, 118)).isEmpty());
    }

    @Test
    public void paddedWordCannotBorrowTheWedge() {
        assertTrue(ramps(page(false, 0, "unprinted", 39, 8), word("p", 110, 126, 171)).isEmpty());
    }

    @Test
    public void styleWordIsNotALevel() {
        assertTrue(ramps(page(false, 0, "normal", 39, 8), word("dolce", 110, 126, 118)).isEmpty());
    }

    @Test
    public void otherRowLevelCannotQualify() {
        assertTrue(ramps(page(false, 0, "normal", 39, 8), word("p", 110, 155, 118)).isEmpty());
    }

    @Test
    public void distantLevelCannotQualify() {
        assertTrue(ramps(page(false, 0, "normal", 39, 8), word("p", 50, 126, 58)).isEmpty());
    }

    @Test
    public void filledCompactWedgeIsNotOpen() {
        assertTrue(ramps(page(false, 0, "filled", 39, 8), word("p", 110, 126, 118)).isEmpty());
    }

    @Test
    public void thirdArmCannotBeErased() {
        assertTrue(ramps(page(false, 0, "extra", 39, 8), word("p", 110, 126, 118)).isEmpty());
    }

    @Test
    public void whiteColumnIsNeverBridged() {
        assertTrue(ramps(page(false, 0, "gap", 39, 8), word("p", 110, 126, 118)).isEmpty());
    }

    @Test
    public void originalMinimumSpanStillApplies() {
        assertTrue(ramps(page(false, 0, "normal", 28, 8), word("p", 110, 126, 118)).isEmpty());
    }

    @Test
    public void originalMaximumOpeningStillApplies() {
        assertTrue(ramps(page(false, 0, "normal", 39, 9), word("p", 110, 126, 118)).isEmpty());
    }

    @Test
    public void fullComponentPrefixIsRecheckedAfterGrowth() throws Exception {
        byte[] r = page(false, 0, "unprinted", 39, 8);
        r[132 * W + 130] = (byte) 210;
        r[132 * W + 131] = (byte) 210;
        for (int x = 128; x <= 129; x++) for (int y = 132; y <= 133; y++) r[y * W + x] = 0;
        var p = word("p", 126, 126, 130);
        var prefix =
                ScoreDynamicsDetector.class.getDeclaredMethod(
                        "precedingPrintedLevel",
                        List.class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class);
        prefix.setAccessible(true);
        assertTrue((Boolean) prefix.invoke(null, List.of(p), r, W, H, 132, 124, 140, GAP));
        assertFalse((Boolean) prefix.invoke(null, List.of(p), r, W, H, 128, 124, 140, GAP));
        assertTrue(ramps(r, p).isEmpty());
    }

    @Test
    public void ordinaryAndPrintedLevelObservationsDoNotShareAdmissionKeys() {
        byte[] r = page(false, 0, "unprinted", 59, 7);
        for (int y = 0; y < r.length; y++) if ((r[y] & 255) < 145) r[y] = (byte) 210;
        int[] seed = new int[35];
        int n = 0, top = H, bottom = 0;
        for (int x = 150; x <= 184; x++) {
            int y = 132 - (int) Math.round(7 * (x - 130) / 59d);
            r[y * W + x] = 0;
            seed[n++] = y * W + x;
            top = Math.min(top, y);
            bottom = Math.max(bottom, y);
        }
        var staff = new PlayingTechniqueDetector.Staff(60, 100, GAP, 0, 1);
        var p = new CompleteFaintHairpinArms.Page(r, W, H, List.of(staff));
        p.candidate(staff, seed, n, 150, 184, top, bottom);
        p.compactCandidate(staff, seed, n, 150, 184, top, bottom);
        var shapes = p.finish();
        assertEquals(2, shapes.size());
        assertFalse(shapes.get(0).requiresPrintedLevel());
        assertTrue(shapes.get(1).requiresPrintedLevel());
    }
}
