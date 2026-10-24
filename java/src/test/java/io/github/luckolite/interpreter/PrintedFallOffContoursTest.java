// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedFallOffContoursTest {
    static final int W = 320, H = 220;
    static final float GAP = 14;

    private record Scene(
            byte[] gray,
            int[] pixels,
            int left,
            int right,
            int top,
            int bottom,
            List<PrintedSlurOwners.GeometryHead> heads,
            StaffPitchTrack track) {}

    private Scene scene(double phase, double scale) {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        int left = 100,
                right = left + (int) Math.round(24 * scale) - 1,
                top = 90,
                bottom = top + (int) Math.round(24 * scale) - 1;
        var pixels = new ArrayList<Integer>();
        // Original analytic quarter-ellipse cells; no published score pixels or fixtures.
        for (int x = left; x <= right; x++) {
            double u = (x - left + .5) / (right - left + 1);
            double before = (x - left) / (double) (right - left + 1),
                    after = (x - left + 1) / (double) (right - left + 1);
            double first =
                    bottom + .5 - (bottom - top + 1) * Math.sqrt(1 - before * before) + phase;
            double last = bottom + .5 - (bottom - top + 1) * Math.sqrt(1 - after * after) + phase;
            int lo = (int) Math.floor(first - .6), hi = (int) Math.ceil(last + .6);
            for (int y = lo; y <= hi; y++) {
                pixels.add(y * W + x);
                raw[y * W + x] = 0;
            }
        }
        // Source ellipse is independently visible and spatially separate from the gesture.
        float hx = left - 16, hy = top - 7;
        paintHead(raw, hx, hy);
        int actualTop = pixels.stream().mapToInt(p -> p / W).min().orElseThrow();
        int actualBottom = pixels.stream().mapToInt(p -> p / W).max().orElseThrow();
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 116, GAP}, new float[] {W - 1, 116, GAP}));
        return new Scene(
                raw,
                pixels.stream().mapToInt(i -> i).toArray(),
                left,
                right,
                actualTop,
                actualBottom,
                List.of(new PrintedSlurOwners.GeometryHead(17, hx, hy, 0, 1)),
                track);
    }

    private void paintHead(byte[] raw, float x, float y) {
        for (int yy = Math.round(y) - 7; yy <= y + 7; yy++)
            for (int xx = Math.round(x) - 10; xx <= x + 10; xx++)
                if (Math.pow((xx - x) / 9, 2) + Math.pow((yy - y) / 5, 2) <= 1)
                    raw[yy * W + xx] = 0;
    }

    private PrintedFallOffContours.Fall classify(Scene s) {
        return PrintedFallOffContours.classify(
                s.pixels,
                W,
                s.left,
                s.right,
                s.top,
                s.bottom,
                s.heads,
                s.track,
                s.gray,
                H,
                new PrintedFallOffContours.Budget(128000000));
    }

    private Scene pixels(Scene s, int[] pixels) {
        return new Scene(s.gray, pixels, s.left, s.right, s.top, s.bottom, s.heads, s.track);
    }

    private Scene heads(Scene s, List<PrintedSlurOwners.GeometryHead> heads) {
        return new Scene(s.gray, s.pixels, s.left, s.right, s.top, s.bottom, heads, s.track);
    }

    @Test
    public void fullAuthoredQuarterBowHasOneExactOwner() {
        var f = classify(scene(0, 1));
        assertNotNull(f);
        assertEquals(17, f.owner());
        assertTrue(f.rightY() > f.leftY());
    }

    @Test
    public void secondRasterPhaseKeepsOwner() {
        assertNotNull(classify(scene(.35, 1)));
    }

    @Test
    public void thirdRasterPhaseKeepsOwner() {
        assertNotNull(classify(scene(-.35, 1)));
    }

    @Test
    public void smallerCompleteBowKeepsOwner() {
        assertNotNull(classify(scene(0, .85)));
    }

    @Test
    public void largerCompleteBowKeepsOwner() {
        assertNotNull(classify(scene(0, 1.2)));
    }

    @Test
    public void pixelOrderDoesNotChooseAResult() {
        var s = scene(0, 1);
        int[] p = s.pixels.clone();
        for (int i = 0; i < p.length / 2; i++) {
            int v = p[i];
            p[i] = p[p.length - i - 1];
            p[p.length - i - 1] = v;
        }
        assertEquals(classify(s), classify(pixels(s, p)));
    }

    @Test
    public void duplicateSourceIsAmbiguous() {
        var s = scene(0, 1);
        var h = s.heads.get(0);
        assertNull(
                classify(
                        heads(
                                s,
                                List.of(
                                        h,
                                        new PrintedSlurOwners.GeometryHead(
                                                18, h.x(), h.y(), 0, 1)))));
    }

    @Test
    public void nearbyDuplicateSourceIsAmbiguous() {
        var s = scene(0, 1);
        var h = s.heads.get(0);
        assertNull(
                classify(
                        heads(
                                s,
                                List.of(
                                        h,
                                        new PrintedSlurOwners.GeometryHead(
                                                18, h.x() + 1, h.y(), 0, 1)))));
    }

    @Test
    public void differentStaffDoesNotHidePhysicalDuplicate() {
        var s = scene(0, 1);
        var h = s.heads.get(0);
        assertNull(
                classify(
                        heads(
                                s,
                                List.of(
                                        h,
                                        new PrintedSlurOwners.GeometryHead(
                                                18, h.x(), h.y(), 0, 2)))));
    }

    @Test
    public void foreignHeadOnTheTailRefuses() {
        var s = scene(0, 1);
        var h = new ArrayList<>(s.heads);
        h.add(new PrintedSlurOwners.GeometryHead(18, s.right, s.bottom, 0, 1));
        assertNull(classify(heads(s, h)));
    }

    @Test
    public void foreignHeadOrderDoesNotSelectSource() {
        var s = scene(0, 1);
        var h = new ArrayList<>(s.heads);
        h.add(0, new PrintedSlurOwners.GeometryHead(18, s.right, s.bottom, 0, 1));
        assertNull(classify(heads(s, h)));
    }

    @Test
    public void absentRawSourceBodyRefuses() {
        var s = scene(0, 1);
        Arrays.fill(s.gray, (byte) 255);
        assertNull(classify(s));
    }

    @Test
    public void noSourceHeadRefuses() {
        var s = scene(0, 1);
        assertNull(classify(heads(s, List.of())));
    }

    @Test
    public void sourceAfterCurveIsIncomingAndRefuses() {
        var s = scene(0, 1);
        assertNull(
                classify(
                        heads(
                                s,
                                List.of(
                                        new PrintedSlurOwners.GeometryHead(
                                                17, s.right + 16, s.top - 7, 0, 1)))));
    }

    @Test
    public void sourceBelowGestureRefuses() {
        var s = scene(0, 1);
        assertNull(
                classify(
                        heads(
                                s,
                                List.of(
                                        new PrintedSlurOwners.GeometryHead(
                                                17, s.left - 16, s.bottom + 7, 0, 1)))));
    }

    @Test
    public void farSourceRefuses() {
        var s = scene(0, 1);
        assertNull(
                classify(
                        heads(
                                s,
                                List.of(
                                        new PrintedSlurOwners.GeometryHead(
                                                17, s.left - 60, s.top - 7, 0, 1)))));
    }

    @Test
    public void duplicatePixelsRefuse() {
        var s = scene(0, 1);
        int[] p = Arrays.copyOf(s.pixels, s.pixels.length + 1);
        p[p.length - 1] = p[0];
        assertNull(classify(pixels(s, p)));
    }

    @Test
    public void missingWholeColumnRefuses() {
        var s = scene(0, 1);
        assertNull(
                classify(
                        pixels(
                                s,
                                Arrays.stream(s.pixels)
                                        .filter(p -> p % W != s.left + 8)
                                        .toArray())));
    }

    @Test
    public void detachedForeignDotRefusesCompleteColumns() {
        var s = scene(0, 1);
        int[] p = Arrays.copyOf(s.pixels, s.pixels.length + 1);
        p[p.length - 1] = (s.top + 10) * W + s.left + 4;
        assertNull(classify(pixels(s, p)));
    }

    @Test
    public void upwardCurveRefuses() {
        var s = scene(0, 1);
        int[] p =
                Arrays.stream(s.pixels).map(v -> (s.top + s.bottom - v / W) * W + v % W).toArray();
        assertNull(classify(pixels(s, p)));
    }

    @Test
    public void straightDiagonalIsNotCurvedFall() {
        var s = scene(0, 1);
        var p = new ArrayList<Integer>();
        for (int x = s.left; x <= s.right; x++) {
            int y = s.top + x - s.left;
            for (int k = 0; k < 3; k++) p.add((y + k) * W + x);
        }
        assertNull(classify(pixels(s, p.stream().mapToInt(i -> i).toArray())));
    }

    @Test
    public void sharpElbowIsNotCurvedFall() {
        var s = scene(0, 1);
        var p = new ArrayList<Integer>();
        for (int x = s.left; x <= s.right; x++) {
            int y = s.top + (x - s.left < 12 ? 0 : (x - s.left - 12) * 2);
            for (int k = 0; k < 3; k++) p.add((y + k) * W + x);
        }
        assertNull(classify(pixels(s, p.stream().mapToInt(i -> i).toArray())));
    }

    @Test
    public void wholeClosedCounterRefuses() {
        var s = scene(0, 1);
        var p = new ArrayList<Integer>();
        for (int x = s.left; x <= s.right; x++)
            for (int y = s.top; y <= s.bottom; y++)
                if (x == s.left || x == s.right || y == s.top || y == s.bottom) p.add(y * W + x);
        assertNull(classify(pixels(s, p.stream().mapToInt(i -> i).toArray())));
    }

    @Test
    public void branchCannotBeDiscardedForNiceBow() {
        var s = scene(0, 1);
        var p = new HashSet<Integer>();
        for (int v : s.pixels) p.add(v);
        for (int y = s.top; y <= s.bottom; y++) p.add(y * W + s.left + 8);
        assertNull(classify(pixels(s, p.stream().mapToInt(i -> i).toArray())));
    }

    @Test
    public void heavyCurvedBodyIsNotThinFall() {
        var s = scene(0, 1);
        var p = new HashSet<Integer>();
        for (int v : s.pixels)
            for (int k = -4; k <= 4; k++)
                if (v / W + k >= s.top && v / W + k <= s.bottom) p.add(v + k * W);
        assertNull(classify(pixels(s, p.stream().mapToInt(i -> i).toArray())));
    }

    @Test
    public void noBudgetCannotProduceAnOwner() {
        var s = scene(0, 1);
        var b = new PrintedFallOffContours.Budget(0);
        assertNull(
                PrintedFallOffContours.classify(
                        s.pixels, W, s.left, s.right, s.top, s.bottom, s.heads, s.track, s.gray, H,
                        b));
        assertTrue(b.refused());
    }

    @Test
    public void resourceRefusalIsSticky() {
        var b = new PrintedFallOffContours.Budget(4);
        assertFalse(b.take(5));
        assertFalse(b.take(0));
        assertTrue(b.refused());
    }

    @Test
    public void negativeResourceRequestRefuses() {
        var b = new PrintedFallOffContours.Budget(4);
        assertFalse(b.take(-1));
        assertFalse(b.take(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidBudgetIsRejected() {
        new PrintedFallOffContours.Budget(128000001);
    }

    @Test
    public void sourceBytesRemainExact() {
        var s = scene(0, 1);
        byte[] before = s.gray.clone();
        assertNotNull(classify(s));
        assertArrayEquals(before, s.gray);
    }

    private List<PrintedSlurPageCapture.Frame<PrintedSlurOwners.GeometryHead>> frames(Scene s) {
        return List.of(new PrintedSlurPageCapture.Frame<>(0, 0, W - 1, H - 1, s.track, s.heads));
    }

    @Test
    public void fullProducerRecoversOneAuthoredFall() {
        var s = scene(0, 1);
        var found =
                PrintedFallOffContours.discover(
                        s.gray, W, H, frames(s), new PrintedFallOffContours.Budget(128000000));
        assertEquals(1, found.size());
        assertEquals(17, found.get(0).owner());
    }

    @Test
    public void fullProducerKeepsRawBytes() {
        var s = scene(0, 1);
        byte[] before = s.gray.clone();
        assertEquals(
                1,
                PrintedFallOffContours.discover(
                                s.gray,
                                W,
                                H,
                                frames(s),
                                new PrintedFallOffContours.Budget(128000000))
                        .size());
        assertArrayEquals(before, s.gray);
    }

    @Test
    public void fullProducerZeroAllowanceEmitsNoPrefix() {
        var s = scene(0, 1);
        var b = new PrintedFallOffContours.Budget(0);
        assertTrue(PrintedFallOffContours.discover(s.gray, W, H, frames(s), b).isEmpty());
        assertTrue(b.refused());
    }

    @Test
    public void lateFinalFrameRefusalClearsEarlierCandidate() {
        var s = scene(0, 1);
        var probe = new PrintedFallOffContours.Budget(128000000);
        assertEquals(1, PrintedFallOffContours.discover(s.gray, W, H, frames(s), probe).size());
        long used = 128000000 - probe.remaining();
        var b = new PrintedFallOffContours.Budget(used + 1);
        var both = List.of(frames(s).get(0), frames(s).get(0));
        assertTrue(PrintedFallOffContours.discover(s.gray, W, H, both, b).isEmpty());
        assertTrue(b.refused());
    }

    private Scene calibratedScene() {
        var old = scene(0, 1);
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        for (int x = 1; x < W - 1; x++)
            for (int k = 0; k < 5; k++)
                for (int dy = -1; dy <= 1; dy++)
                    raw[(32 + k * 14 + dy) * W + x] = (byte) (dy == 0 ? 40 : 90);
        int[] p = Arrays.stream(old.pixels).map(v -> v - 35 * W).toArray();
        for (int v : p) raw[v] = 0;
        paintHead(raw, 84, 48);
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 88, GAP}, new float[] {W - 1, 88, GAP}));
        return new Scene(
                raw,
                p,
                old.left,
                old.right,
                old.top - 35,
                old.bottom - 35,
                List.of(new PrintedSlurOwners.GeometryHead(17, 84, 48, 0, 1)),
                track);
    }

    private List<PrintedFallOffContours.Fall> calibrated(Scene s) {
        return PrintedFallOffContours.discoverCalibrated(
                s.gray,
                W,
                H,
                frames(s),
                new LiteralStaffContrast.CatalogBudget(128000000),
                new PrintedFallOffContours.Budget(128000000));
    }

    @Test
    public void calibratedProducerRecoversAuthoredBowAcrossRules() {
        var f = calibrated(calibratedScene());
        assertEquals(1, f.size());
        assertEquals(17, f.get(0).owner());
    }

    @Test
    public void calibratedProducerPreservesSourcePixels() {
        var s = calibratedScene();
        byte[] before = s.gray.clone();
        assertEquals(1, calibrated(s).size());
        assertArrayEquals(before, s.gray);
    }

    @Test
    public void calibratedProducerRejectsDuplicateSource() {
        var s = calibratedScene();
        var h = s.heads.get(0);
        assertTrue(
                calibrated(
                                heads(
                                        s,
                                        List.of(
                                                h,
                                                new PrintedSlurOwners.GeometryHead(
                                                        18, h.x(), h.y(), 0, 1))))
                        .isEmpty());
    }

    @Test
    public void calibratedProducerNeedsRealRawHead() {
        var s = calibratedScene();
        for (int y = 41; y <= 55; y++)
            for (int x = 74; x <= 94; x++)
                s.gray[y * W + x] = (byte) (y >= 45 && y <= 47 ? (y == 46 ? 40 : 90) : 255);
        assertTrue(calibrated(s).isEmpty());
    }

    @Test
    public void calibratedReferenceContradictionRejectsWholeField() {
        var s = calibratedScene();
        s.gray[60 * W + 110] = (byte) 255;
        assertTrue(calibrated(s).isEmpty());
    }

    @Test
    public void calibratedProducerZeroReferenceAllowanceCannotEmit() {
        var s = calibratedScene();
        var refs = new LiteralStaffContrast.CatalogBudget(0);
        assertTrue(
                PrintedFallOffContours.discoverCalibrated(
                                s.gray,
                                W,
                                H,
                                frames(s),
                                refs,
                                new PrintedFallOffContours.Budget(128000000))
                        .isEmpty());
        assertTrue(refs.refused());
    }

    @Test
    public void calibratedProducerZeroContourAllowanceCannotEmit() {
        var s = calibratedScene();
        var work = new PrintedFallOffContours.Budget(0);
        assertTrue(
                PrintedFallOffContours.discoverCalibrated(
                                s.gray,
                                W,
                                H,
                                frames(s),
                                new LiteralStaffContrast.CatalogBudget(128000000),
                                work)
                        .isEmpty());
        assertTrue(work.refused());
    }

    @Test
    public void lateReferenceRefusalClearsEarlierCalibratedCandidate() {
        var s = calibratedScene();
        var refs = new LiteralStaffContrast.CatalogBudget(128000000);
        assertEquals(
                1,
                PrintedFallOffContours.discoverCalibrated(
                                s.gray,
                                W,
                                H,
                                frames(s),
                                refs,
                                new PrintedFallOffContours.Budget(128000000))
                        .size());
        long used = 128000000 - refs.remaining();
        var bounded = new LiteralStaffContrast.CatalogBudget(used + 1);
        assertTrue(
                PrintedFallOffContours.discoverCalibrated(
                                s.gray,
                                W,
                                H,
                                List.of(frames(s).get(0), frames(s).get(0)),
                                bounded,
                                new PrintedFallOffContours.Budget(128000000))
                        .isEmpty());
        assertTrue(bounded.refused());
    }

    @Test
    public void lateContourRefusalClearsEarlierCalibratedCandidate() {
        var s = calibratedScene();
        var work = new PrintedFallOffContours.Budget(128000000);
        assertEquals(
                1,
                PrintedFallOffContours.discoverCalibrated(
                                s.gray,
                                W,
                                H,
                                frames(s),
                                new LiteralStaffContrast.CatalogBudget(128000000),
                                work)
                        .size());
        long used = 128000000 - work.remaining();
        var bounded = new PrintedFallOffContours.Budget(used + 1);
        assertTrue(
                PrintedFallOffContours.discoverCalibrated(
                                s.gray,
                                W,
                                H,
                                List.of(frames(s).get(0), frames(s).get(0)),
                                new LiteralStaffContrast.CatalogBudget(128000000),
                                bounded)
                        .isEmpty());
        assertTrue(bounded.refused());
    }
}
