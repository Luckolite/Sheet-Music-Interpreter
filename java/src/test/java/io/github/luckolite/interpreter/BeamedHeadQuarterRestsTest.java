// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural quarter zigzag, returning hook, beamed oval and ruled paper. */
public class BeamedHeadQuarterRestsTest {
    static final int W = 700, H = 450;

    static class Page {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];
        final float g, base, staffTop, bodyTop, hx, hy;
        final boolean beam;

        Page(
                float gap,
                boolean body,
                boolean zigzag,
                boolean hook,
                int shaft,
                boolean beam,
                int shade) {
            this(gap, body, zigzag, hook, shaft, beam, shade, 250, 90);
        }

        Page(
                float gap,
                boolean body,
                boolean zigzag,
                boolean hook,
                int shaft,
                boolean beam,
                int shade,
                float originX,
                float originY) {
            this(gap, body, zigzag, hook, shaft, beam, shade, originX, originY, .6f);
        }

        Page(
                float gap,
                boolean body,
                boolean zigzag,
                boolean hook,
                int shaft,
                boolean beam,
                int shade,
                float originX,
                float originY,
                float headPhase) {
            this(gap, body, zigzag, hook, shaft, beam, shade, originX, originY, headPhase, 2.4f);
        }

        Page(
                float gap,
                boolean body,
                boolean zigzag,
                boolean hook,
                int shaft,
                boolean beam,
                int shade,
                float originX,
                float originY,
                float headPhase,
                float bodyPhase) {
            g = gap;
            this.base = originX;
            this.staffTop = originY;
            this.beam = beam;
            bodyTop = staffTop + bodyPhase * g;
            hx = base + .75f * g;
            hy = bodyTop + headPhase * g;
            Arrays.fill(gray, (byte) 240);
            for (int j = 0; j < 5; j++)
                for (int x = 25; x < W - 25; x++) pixel(x, Math.round(staffTop + j * g), 4, 0);
            int[][] shape = {
                {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 8}, {7, 10}, {7, 11}, {6, 11},
                {6, 11}, {5, 11}, {5, 11}, {4, 10}, {4, 9}, {5, 9}, {6, 9}, {7, 10}, {8, 11},
                {6, 12}, {4, 13}, {3, 13}, {2, 13}, {2, 6}, {3, 6}, {3, 6}, {4, 7}, {5, 7}, {6, 8},
                {7, 9}, {8, 9}
            };
            if (body)
                for (int row = 0; row < shape.length; row++) {
                    int a = shape[row][0], b = shape[row][1];
                    if (!zigzag && row < 19) {
                        a = 5;
                        b = 8;
                    }
                    if (!hook && row >= 19) {
                        a = 7;
                        b = 9;
                    }
                    for (int y = Math.round(bodyTop + row * g / 10);
                            y <= Math.round(bodyTop + (row + 1) * g / 10);
                            y++)
                        for (int x = Math.round(base + a * g / 14);
                                x <= Math.round(base + b * g / 14);
                                x++) pixel(x, y, 0, shade);
                }
            oval(hx, hy, .70f * g, .45f * g, shade);
            oval(hx, hy + 3.5f * g, .70f * g, .45f * g, shade);
            if (shaft != 0) {
                int x = Math.round(hx + shaft * .65f * g);
                int t = Math.round(shaft == 1 ? hy - 3.5f * g : hy),
                        b = Math.round(shaft == 1 ? hy + 3.5f * g : hy + 7 * g);
                for (int y = t; y <= b; y++)
                    for (int dx = 0; dx <= 1; dx++) pixel(x + dx, y, 1, shade);
                if (beam)
                    for (int y = t; y <= t + Math.max(2, Math.round(g * .18f)); y++)
                        for (int xx = x; xx <= x + 5 * g; xx++) pixel(xx, y, 1, shade);
            }
            for (int y = Math.round(staffTop + 5 * g); y < hy + 3.5f * g; y += Math.round(g))
                for (int x = Math.round(hx - 1.05f * g); x <= hx + 1.05f * g; x++)
                    pixel(x, y, 4, 0);
        }

        void pixel(int x, int y, int label, int shade) {
            if (x >= 0 && x < W && y >= 0 && y < H) {
                gray[y * W + x] = (byte) shade;
                labels[y * W + x] = (byte) label;
            }
        }

        void oval(float x, float y, float rx, float ry, int shade) {
            for (int yy = (int) (y - ry); yy <= y + ry; yy++)
                for (int xx = (int) (x - rx); xx <= x + rx; xx++)
                    if (Math.pow((xx - x) / rx, 2) + Math.pow((yy - y) / ry, 2) <= 1)
                        pixel(xx, yy, 2, shade);
        }

        List<BeamedHeadQuarterRests.Candidate> read() throws Exception {
            var before = gray.clone();
            var oldLabels = labels.clone();
            var m = new MeasureRegion(.03f, .97f, .03f, .95f);
            var upper =
                    new ScoreNoteEvent(
                                    0,
                                    (hx / W - .03f) / .94f,
                                    0,
                                    0,
                                    1,
                                    hy / H,
                                    false,
                                    0,
                                    beam ? 1 : 0,
                                    2,
                                    beam ? 0 : 1)
                            .withStemDirection(1);
            var lower =
                    new ScoreNoteEvent(
                                    0,
                                    (hx / W - .03f) / .94f,
                                    -7,
                                    0,
                                    1,
                                    (hy + 3.5f * g) / H,
                                    false,
                                    0,
                                    beam ? 1 : 0,
                                    2,
                                    beam ? 0 : 1)
                            .withStemDirection(1);
            var result =
                    BeamedHeadQuarterRests.find(
                            labels,
                            gray,
                            W,
                            H,
                            new ScorePageInterpretation(List.of(m), List.of(upper, lower)));
            assertArrayEquals(before, gray);
            assertArrayEquals(oldLabels, labels);
            return result;
        }
    }

    static Page positive(float g) {
        return new Page(g, true, true, true, 1, true, 0);
    }

    @Test
    public void originalOccludedQuarterKeepsItsVisibleLowerHook() throws Exception {
        assertEquals(1, positive(16).read().size());
    }

    @Test
    public void aLargerPrintedRestHasTheSameContour() throws Exception {
        assertEquals(1, positive(22).read().size());
    }

    @Test
    public void aSmallerPrintedRestHasTheSameContour() throws Exception {
        assertEquals(1, positive(12).read().size());
    }

    @Test
    public void faintInkStillNeedsTheReturningHook() throws Exception {
        assertEquals(1, new Page(16, true, true, true, 1, true, 145).read().size());
    }

    @Test
    public void bareBeamedChordCannotInventAQuarterRest() throws Exception {
        assertTrue(new Page(16, false, true, true, 1, true, 0).read().isEmpty());
    }

    @Test
    public void bareLargerChordCannotInventAQuarterRest() throws Exception {
        assertTrue(new Page(22, false, true, true, 1, true, 0).read().isEmpty());
    }

    @Test
    public void bareSmallerChordCannotInventAQuarterRest() throws Exception {
        assertTrue(new Page(12, false, true, true, 1, true, 0).read().isEmpty());
    }

    @Test
    public void missingHookCannotBeCompletedByANotehead() throws Exception {
        assertTrue(new Page(16, true, true, false, 1, true, 0).read().isEmpty());
    }

    @Test
    public void missingZigzagCannotBeCompletedByANotehead() throws Exception {
        assertTrue(new Page(16, true, false, true, 1, true, 0).read().isEmpty());
    }

    @Test
    public void aMissingShaftCannotTrustPredictedDirection() throws Exception {
        assertTrue(new Page(16, true, true, true, 0, true, 0).read().isEmpty());
    }

    @Test
    public void actualDownwardShaftCanOwnAContactDespiteUnassignedMetadata() throws Exception {
        assertEquals(1, new Page(16, true, true, true, -1, true, 0).read().size());
    }

    @Test
    public void quarterOwnerDoesNotUseBeamedContactProof() throws Exception {
        assertTrue(new Page(16, true, true, true, 1, false, 0).read().isEmpty());
    }

    @Test
    public void interruptedShaftCannotOwnTheOverlappingHead() throws Exception {
        var p = positive(16);
        for (int y = Math.round(p.hy - p.g * 2); y <= p.hy - p.g; y++)
            for (int x = Math.round(p.hx + .4f * p.g); x <= p.hx + .9f * p.g; x++)
                p.pixel(x, y, 0, 240);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void localRectangleCannotSupplyAQuarterHook() throws Exception {
        var p = new Page(16, false, true, true, 1, true, 0);
        for (int y = Math.round(p.hy + p.g); y <= p.hy + 1.35f * p.g; y++)
            for (int x = 250; x <= 250 + p.g; x++) p.pixel(x, y, 0, 0);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void independentRasterPhasesPreserveTheSamePrintedRest() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            for (float x : new float[] {250, 250.3f, 250.7f})
                for (float y : new float[] {90, 90.3f, 90.7f}) {
                    var p = new Page(gap, true, true, true, 1, true, 0, x, y);
                    assertEquals("gap=" + gap + " x=" + x + " y=" + y, 1, p.read().size());
                }
    }

    @Test
    public void incompleteHookIsRejectedAtIndependentRasterPhases() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            for (float x : new float[] {250, 250.3f, 250.7f})
                for (float y : new float[] {90, 90.3f, 90.7f})
                    assertTrue(
                            "gap=" + gap + " x=" + x + " y=" + y,
                            new Page(gap, true, true, false, 1, true, 0, x, y).read().isEmpty());
    }

    @Test
    public void beamedChordsRemainNotesAcrossIndependentRasterPhases() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            for (float x : new float[] {250, 250.3f, 250.7f})
                for (float y : new float[] {90, 90.3f, 90.7f})
                    assertTrue(
                            "gap=" + gap + " x=" + x + " y=" + y,
                            new Page(gap, false, true, true, 1, true, 0, x, y).read().isEmpty());
    }

    @Test
    public void interruptedLowerElbowCannotBeInterpolatedAcrossPaper() throws Exception {
        var p = positive(16);
        for (int y = Math.round(p.hy + p.g); y <= p.hy + 1.25f * p.g; y++)
            for (int x = 249; x < 272; x++) p.pixel(x, y, 0, 240);
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void detachedUpwardFlagCannotSupplyLowerZigzagAndHook() throws Exception {
        var p = new Page(16, false, true, true, 1, true, 0);
        for (int y = Math.round(p.hy - 3.1f * p.g); y <= p.hy - 1.6f * p.g; y++) {
            int x = Math.round(p.hx + .7f * p.g + (y - (p.hy - 3.1f * p.g)) * .35f);
            for (int dx = 0; dx < 4; dx++) p.pixel(x + dx, y, 0, 0);
        }
        assertTrue(p.read().isEmpty());
    }

    @Test
    public void middleOverlapRetainsItsIndependentUpperStrokeAndLowerHook() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            assertEquals(
                    "gap=" + gap,
                    1,
                    new Page(gap, true, true, true, 1, true, 0, 250, 90, 1.1f).read().size());
    }

    @Test
    public void middleOverlapStillRequiresTheLowerHook() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            assertTrue(
                    new Page(gap, true, true, false, 1, true, 0, 250, 90, 1.1f).read().isEmpty());
    }

    @Test
    public void middleOverlapStillRequiresAnIndependentUpperStroke() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            assertTrue(
                    new Page(gap, true, false, true, 1, true, 0, 250, 90, 1.1f).read().isEmpty());
    }

    @Test
    public void rawShaftCanProveOwnershipBeforeStemMetadataIsAssigned() throws Exception {
        var p = positive(16);
        var m = new MeasureRegion(.03f, .97f, .03f, .95f);
        var upper =
                new ScoreNoteEvent(
                        0, (p.hx / W - .03f) / .94f, 0, 0, 1, p.hy / H, false, 0, 1, 2, 0);
        var lower =
                new ScoreNoteEvent(
                        0,
                        (p.hx / W - .03f) / .94f,
                        -7,
                        0,
                        1,
                        (p.hy + 3.5f * p.g) / H,
                        false,
                        0,
                        1,
                        2,
                        0);
        var staffs =
                List.of(
                        new SixteenthRestDetector.Staff(
                                p.staffTop, p.staffTop + 4 * p.g, p.g, 0, 1));
        var original = p.gray.clone();
        var found =
                BeamedHeadQuarterRests.detect(
                        p.gray, W, H, List.of(m), staffs, List.of(upper, lower));
        assertEquals(1, found.size());
        assertEquals(1, found.get(0).durationBeats(), 0);
        assertArrayEquals(original, p.gray);
    }

    @Test
    public void actualDetectorRetainsOneRestWithoutEditingItsCallerPlane() throws Exception {
        var p = positive(16);
        var m = new MeasureRegion(.03f, .97f, .03f, .95f);
        var upper =
                new ScoreNoteEvent(
                        0, (p.hx / W - .03f) / .94f, 0, 0, 1, p.hy / H, false, 0, 1, 2, 0);
        var lower =
                new ScoreNoteEvent(
                        0,
                        (p.hx / W - .03f) / .94f,
                        -7,
                        0,
                        1,
                        (p.hy + 3.5f * p.g) / H,
                        false,
                        0,
                        1,
                        2,
                        0);
        var original = p.gray.clone();
        var found =
                SixteenthRestDetector.detect(
                        p.gray,
                        W,
                        H,
                        List.of(m),
                        List.of(
                                new SixteenthRestDetector.Staff(
                                        p.staffTop, p.staffTop + 4 * p.g, p.g, 0, 1)),
                        List.of(upper, lower));
        assertEquals(found.toString(), 1, found.size());
        assertEquals(1, found.get(0).durationBeats(), 0);
        assertArrayEquals(original, p.gray);
    }

    @Test
    public void coveredLowerHookHasAnIndependentVisibleUpperQuarter() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            for (float phase : new float[] {2.1f, 2.6f, 3.1f})
                assertEquals(
                        "gap=" + gap + " phase=" + phase,
                        1,
                        new Page(gap, true, true, true, 1, true, 0, 250, 90, phase).read().size());
    }

    @Test
    public void coveredFootBareChordsCannotInventUpperQuarterInk() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            for (float phase : new float[] {2.1f, 2.6f, 3.1f})
                for (int shaft : new int[] {1, -1})
                    assertTrue(
                            "gap=" + gap + " phase=" + phase + " shaft=" + shaft,
                            new Page(gap, false, true, true, shaft, true, 0, 250, 90, phase)
                                    .read()
                                    .isEmpty());
    }

    @Test
    public void coveredFootRequiresTheVisibleUpperZigzag() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            for (float phase : new float[] {2.1f, 2.6f, 3.1f})
                assertTrue(
                        "gap=" + gap + " phase=" + phase,
                        new Page(gap, true, false, true, 1, true, 0, 250, 90, phase)
                                .read()
                                .isEmpty());
    }

    @Test
    public void coveredFootCannotBridgeMissingUpperInkThroughPaper() throws Exception {
        for (float gap : new float[] {12, 16, 22}) {
            var p = new Page(gap, true, true, true, 1, true, 0, 250, 90, 2.6f);
            for (int y = Math.round(p.bodyTop + .4f * gap); y <= p.bodyTop + .75f * gap; y++)
                for (int x = 240; x < 270; x++) p.pixel(x, y, 0, 240);
            assertTrue("gap=" + gap, p.read().isEmpty());
        }
    }

    @Test
    public void coveredFootCannotTrustAnAbsentPrintedShaft() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            assertTrue(new Page(gap, true, true, true, 0, true, 0, 250, 90, 2.6f).read().isEmpty());
    }

    @Test
    public void coveredFootCannotReadANaturalAsAQuarter() throws Exception {
        for (float gap : new float[] {12, 16, 22}) {
            var p = new Page(gap, false, true, true, 1, true, 0, 250, 90, 2.6f);
            for (int y = Math.round(p.bodyTop); y < p.hy - .6f * gap; y++)
                for (int dx = 0; dx < 2; dx++) {
                    p.pixel(Math.round(p.base + .15f * gap) + dx, y, 0, 0);
                    p.pixel(Math.round(p.base + .65f * gap) + dx, y, 0, 0);
                }
            for (float height : new float[] {.7f, 1.25f})
                for (int y = Math.round(p.bodyTop + height * gap);
                        y < p.bodyTop + (height + .18f) * gap;
                        y++)
                    for (int x = Math.round(p.base + .15f * gap); x <= p.base + .65f * gap; x++)
                        p.pixel(x, y, 0, 0);
            assertTrue("gap=" + gap, p.read().isEmpty());
        }
    }

    @Test
    public void downwardOwnersPreserveTheirOriginalVisibleHooks() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            for (float phase : new float[] {.6f, 1.1f, 2.1f, 2.6f, 3.1f})
                assertEquals(
                        "gap=" + gap + " phase=" + phase,
                        1,
                        new Page(gap, true, true, true, -1, true, 0, 250, 90, phase).read().size());
    }

    @Test
    public void downwardUpperContactsStillRequireOriginalHookInk() throws Exception {
        for (float gap : new float[] {12, 16, 22})
            for (float phase : new float[] {.6f, 1.1f})
                assertTrue(
                        "gap=" + gap + " phase=" + phase,
                        new Page(gap, true, true, false, -1, true, 0, 250, 90, phase)
                                .read()
                                .isEmpty());
    }

    @Test
    public void deeperMiddleContactRequiresBothIndependentEnds() throws Exception {
        for (float gap : new float[] {12, 16, 22}) {
            assertEquals(
                    "gap=" + gap,
                    1,
                    new Page(gap, true, true, true, 1, true, 0, 250, 90, 1.49f).read().size());
            assertTrue(
                    new Page(gap, true, true, false, 1, true, 0, 250, 90, 1.49f).read().isEmpty());
            assertTrue(
                    new Page(gap, true, false, true, 1, true, 0, 250, 90, 1.49f).read().isEmpty());
        }
    }

    static void dots(Page p, int count) {
        for (int i = 0; i < count; i++)
            p.oval(
                    p.base + (1.95f + i * .8f) * p.g,
                    p.staffTop + 1.5f * p.g,
                    .18f * p.g,
                    .18f * p.g,
                    0);
    }

    static SixteenthRestDetector.Detection actual(Page p, float slope) {
        var m = new MeasureRegion(.03f, .97f, .03f, .95f);
        var upper =
                new ScoreNoteEvent(
                        0,
                        (p.hx / W - .03f) / .94f,
                        0,
                        0,
                        1,
                        (p.hy + shift(p.hx, slope)) / H,
                        false,
                        0,
                        1,
                        2,
                        0);
        var lower =
                new ScoreNoteEvent(
                        0,
                        (p.hx / W - .03f) / .94f,
                        -7,
                        0,
                        1,
                        (p.hy + 3.5f * p.g + shift(p.hx, slope)) / H,
                        false,
                        0,
                        1,
                        2,
                        0);
        StaffPitchTrack track =
                slope == 0
                        ? null
                        : StaffPitchTrack.detect(
                                p.gray, W, H, p.staffTop, p.staffTop + 4 * p.g, p.g);
        if (slope != 0) assertNotNull(track);
        var before = p.gray.clone();
        var oldLabels = p.labels.clone();
        var found =
                SixteenthRestDetector.detectWithDots(
                        p.gray,
                        W,
                        H,
                        List.of(m),
                        List.of(
                                new SixteenthRestDetector.Staff(
                                        p.staffTop, p.staffTop + 4 * p.g, p.g, 0, 1, track)),
                        List.of(upper, lower));
        assertArrayEquals(before, p.gray);
        assertArrayEquals(oldLabels, p.labels);
        return found;
    }

    static float shift(float x, float slope) {
        return slope * x / W - slope / 2;
    }

    static void warp(Page p, float slope) {
        var gray = p.gray.clone();
        var labels = p.labels.clone();
        Arrays.fill(p.gray, (byte) 240);
        Arrays.fill(p.labels, (byte) 0);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int yy = y + Math.round(shift(x, slope));
                if (yy >= 0 && yy < H) {
                    p.gray[yy * W + x] = gray[y * W + x];
                    p.labels[yy * W + x] = labels[y * W + x];
                }
            }
    }

    @Test
    public void actualDetectorKeepsOneRestAcrossContactPositions() {
        for (float gap : new float[] {12, 16, 22})
            for (float phase : new float[] {.6f, 1.1f, 1.49f, 2.1f, 2.6f, 3.1f}) {
                var p = new Page(gap, true, true, true, 1, true, 0, 250, 90, phase);
                var found = actual(p, 0);
                assertEquals(
                        "gap=" + gap + " phase=" + phase + " " + found, 1, found.rests().size());
                assertEquals(1, found.rests().get(0).durationBeats(), 0);
            }
    }

    @Test
    public void aContactQuarterRetainsItsOneAugmentationDot() {
        for (float gap : new float[] {12, 16, 22}) {
            var p = new Page(gap, true, true, true, 1, true, 0, 250, 90, .6f, .6f);
            dots(p, 1);
            var found = actual(p, 0);
            assertEquals(found.toString(), 1, found.rests().size());
            assertEquals(found.toString(), 1.5, found.rests().get(0).durationBeats(), 0);
            assertEquals(1, found.dots().size());
            assertEquals(found.rests().get(0), found.dots().get(0).rest());
            assertEquals(p.base + 1.95f * gap, found.dots().get(0).x(), 1);
        }
    }

    @Test
    public void aContactQuarterRetainsBothAugmentationDots() {
        for (float gap : new float[] {12, 16, 22}) {
            var p = new Page(gap, true, true, true, 1, true, 0, 250, 90, .6f, .6f);
            dots(p, 2);
            var found = actual(p, 0);
            assertEquals(found.toString(), 1, found.rests().size());
            assertEquals(found.toString(), 1.75, found.rests().get(0).durationBeats(), 0);
            assertEquals(2, found.dots().size());
        }
    }

    @Test
    public void aCurvedContactQuarterAndDotMapBackToOriginalCoordinates() {
        for (float slope : new float[] {-24, 24}) {
            var p = new Page(16, true, true, true, 1, true, 0, 250, 90, .6f, .6f);
            dots(p, 1);
            for (int j = 0; j < 5; j++)
                for (int x = 25; x < W - 25; x++)
                    p.pixel(x, Math.round(p.staffTop + j * p.g) + 1, 4, 0);
            warp(p, slope);
            var found = actual(p, slope);
            assertEquals(found.toString(), 1, found.rests().size());
            assertEquals(1.5, found.rests().get(0).durationBeats(), 0);
            assertEquals(1, found.dots().size());
            float dx = p.base + 1.95f * p.g, dy = p.staffTop + 1.5f * p.g + shift(dx, slope);
            assertEquals(dx, found.dots().get(0).x(), 1.5);
            assertEquals(dy, found.dots().get(0).y(), 1.5);
        }
    }

    @Test
    public void invalidPixelFramesCannotProduceQuarterCandidates() {
        assertTrue(
                BeamedHeadQuarterRests.detect(null, W, H, List.of(), List.of(), List.of())
                        .isEmpty());
        assertTrue(
                BeamedHeadQuarterRests.detect(new byte[1], W, H, List.of(), List.of(), List.of())
                        .isEmpty());
        assertTrue(
                BeamedHeadQuarterRests.detect(
                                new byte[1],
                                Integer.MAX_VALUE,
                                Integer.MAX_VALUE,
                                List.of(),
                                List.of(),
                                List.of())
                        .isEmpty());
        assertTrue(
                BeamedHeadQuarterRests.detect(new byte[1], -1, H, List.of(), List.of(), List.of())
                        .isEmpty());
    }

    @Test
    public void aNonfiniteStaffCannotOwnAnOtherwiseValidContour() {
        var p = positive(16);
        var m = new MeasureRegion(.03f, .97f, .03f, .95f);
        var n =
                new ScoreNoteEvent(
                        0, (p.hx / W - .03f) / .94f, 0, 0, 1, p.hy / H, false, 0, 1, 2, 0);
        for (float gap : new float[] {Float.NaN, Float.POSITIVE_INFINITY})
            assertTrue(
                    BeamedHeadQuarterRests.detect(
                                    p.gray,
                                    W,
                                    H,
                                    List.of(m),
                                    List.of(
                                            new SixteenthRestDetector.Staff(
                                                    p.staffTop, p.staffTop + 4 * p.g, gap, 0, 1)),
                                    List.of(n))
                            .isEmpty());
    }

    @Test
    public void measuredDownwardShaftWidthSurvivesSubpixelStaffSeeds() {
        for (float printedGap : new float[] {16.5f, 16.75f, 17})
            for (float phase : new float[] {.6f, 1.1f}) {
                var p = new Page(printedGap, true, true, true, -1, true, 0, 250, 90, phase);
                int shaft = Math.round(p.hx - .65f * p.g);
                for (int y = Math.round(p.hy); y <= p.hy + 7 * p.g; y++)
                    p.pixel(shaft + 2, y, 1, 0);
                var m = new MeasureRegion(.03f, .97f, .03f, .95f);
                var upper =
                        new ScoreNoteEvent(
                                0, (p.hx / W - .03f) / .94f, 0, 0, 1, p.hy / H, false, 0, 1, 2, 0);
                var lower =
                        new ScoreNoteEvent(
                                0,
                                (p.hx / W - .03f) / .94f,
                                -7,
                                0,
                                1,
                                (p.hy + 3.5f * p.g) / H,
                                false,
                                0,
                                1,
                                2,
                                0);
                var before = p.gray.clone();
                var found =
                        SixteenthRestDetector.detect(
                                p.gray,
                                W,
                                H,
                                List.of(m),
                                List.of(
                                        new SixteenthRestDetector.Staff(
                                                p.staffTop + 1, p.staffTop + 67, 16.5f, 0, 1)),
                                List.of(upper, lower));
                assertEquals(
                        "printed gap=" + printedGap + " phase=" + phase + " " + found,
                        1,
                        found.size());
                assertEquals(1, found.get(0).durationBeats(), 0);
                assertArrayEquals(before, p.gray);
            }
    }
}
