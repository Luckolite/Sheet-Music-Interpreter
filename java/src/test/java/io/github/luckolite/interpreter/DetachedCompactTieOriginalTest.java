// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic tapered bows and full-component counterexamples, never score pixels. */
public final class DetachedCompactTieOriginalTest {
    static final int W = 220, H = 180;

    static final class Scene {
        final byte[] labels = new byte[W * H], gray = new byte[W * H];
        final float gap, slope, top, bottom;
        final int step, side;
        final DetachedCompactTieRidge.Head first, last;

        Scene(float gap, int side, float slope, int dx, int dy, String kind) {
            this.gap = gap;
            this.side = side;
            this.slope = slope;
            top = 60 + dy;
            bottom = top + gap * 4;
            step = side < 0 ? 6 : 2;
            Arrays.fill(gray, (byte) 245);
            for (int line = 0; line < 5; line++)
                for (int x = 15; x < W - 15; x++) {
                    int y = Math.round(top + line * gap + slope * (x - W * .5f));
                    ink(x, y, 0, 4);
                    ink(x, y + 1, 0, 4);
                }
            float ax = 70 + dx, bx = ax + gap * 2.8f;
            first = head(ax);
            last = head(bx);
            if (!kind.equals("empty")) bow(kind, 0);
            if (kind.equals("two")) bow("round", side * gap * .5f);
            if (kind.equals("branch")) {
                int x = (first.right() + 2 + Math.round(last.x())) / 2;
                int cy = Math.round(base(x) + side * gap * 1.85f);
                for (int d = 0; d <= gap; d++) ink(x, cy + side * d, 0, 5);
            }
            if (kind.equals("letter")) {
                int a = first.right() + Math.round(gap * .14f),
                        b = Math.round(last.x() + gap * .14f);
                for (int x : new int[] {a, b}) {
                    int y = Math.round(base(x) + side * gap * 1.35f);
                    for (int d = 0; d <= gap; d++) ink(x, y - side * d, 0, 5);
                }
            }
            if (kind.equals("phrase")) {
                int right = first.right() + Math.round(gap * .14f);
                int y = Math.round(base(right) + side * gap * 1.35f);
                for (int x = 0; x <= right; x++) ink(x, y, 0, 5);
            }
            if (kind.equals("hole")) bow("counter", 0);
            if (kind.equals("break")) {
                int middle = Math.round((first.right() + last.x()) * .5f);
                for (int y = 0; y < H; y++)
                    for (int x = middle - 2; x <= middle + 2; x++) {
                        if (labels[y * W + x] != 5) continue;
                        labels[y * W + x] = 0;
                        gray[y * W + x] = (byte) 245;
                    }
            }
            if (kind.equals("head"))
                for (int i = 0; i < labels.length; i++) if (labels[i] == 5) labels[i] = 2;
        }

        private float base(float x) {
            return bottom - step * gap * .5f + slope * (x - W * .5f);
        }

        private DetachedCompactTieRidge.Head head(float x) {
            float y = base(x);
            int radius = Math.round(gap * .7f);
            for (int yy = Math.round(y - gap * .5f); yy <= y + gap * .5f; yy++)
                for (int xx = Math.round(x - radius); xx <= x + radius; xx++)
                    if (Math.pow((xx - x) / radius, 2) + Math.pow((yy - y) / (gap * .5f), 2) <= 1)
                        ink(xx, yy, 0, 2);
            return new DetachedCompactTieRidge.Head(
                    Math.round(x) - radius, Math.round(x) + radius, x, y);
        }

        void ink(int x, int y, int value, int label) {
            if (x < 0 || y < 0 || x >= W || y >= H) return;
            int at = y * W + x;
            gray[at] = (byte) Math.min(gray[at] & 255, value);
            labels[at] = (byte) label;
        }

        private void bow(String kind, float shift) {
            int a = first.right() + Math.round(gap * .14f), b = Math.round(last.x() + gap * .14f);
            for (int x = a; x <= b; x++) {
                float t = (x - a) / (float) (b - a);
                float shape =
                        kind.equals("straight")
                                ? 0
                                : kind.equals("angular")
                                        ? 1 - Math.abs(2 * t - 1)
                                        : 4 * t * (1 - t);
                float bow = kind.equals("counter") ? -.5f : .5f;
                float target = base(x) + side * gap * (1.35f + bow * shape) + shift;
                if (kind.equals("inside")) target -= side * gap * 2;
                float radius = (.6f + 1.5f * (float) Math.sin(Math.PI * t)) * gap / 14;
                for (int y = (int) Math.floor(target - 3); y <= target + 3; y++) {
                    float distance = Math.abs(y - target);
                    int value = Math.round(245 * Math.min(1, Math.max(0, distance - radius + .5f)));
                    if (value < 245) ink(x, y, value, 5);
                }
            }
        }

        StaffPitchTrack track() {
            return StaffPitchTrack.forPrintedSlurs(gray, W, H, top, bottom, gap);
        }

        boolean result() {
            return DetachedCompactTieRidge.proved(labels, gray, W, H, first, last, step, track());
        }
    }

    private static Scene scene(float gap, int side, String kind) {
        return new Scene(gap, side, 0, 0, 0, kind);
    }

    @Test
    public void aboveStaffBowIsComplete() {
        assertTrue(scene(14, -1, "round").result());
    }

    @Test
    public void belowStaffBowIsComplete() {
        assertTrue(scene(14, 1, "round").result());
    }

    @Test
    public void smallerScaleUsesSameProof() {
        assertTrue(scene(10, -1, "round").result());
    }

    @Test
    public void largerScaleUsesSameProof() {
        assertTrue(scene(18, -1, "round").result());
    }

    @Test
    public void unprovedSlopedFrameAbstains() {
        var s = new Scene(14, -1, .025f, 0, 0, "round");
        assertNull(s.track());
        assertFalse(s.result());
    }

    @Test
    public void independentlyVerifiedSlopedSamplesRetainBow() {
        var s = new Scene(14, -1, .025f, 0, 0, "round");
        var samples = new ArrayList<float[]>();
        for (int x : new int[] {30, 145, 195}) {
            for (int rule = 0; rule < 5; rule++) {
                int y = Math.round(s.top + rule * s.gap + s.slope * (x - W * .5f));
                assertEquals(4, s.labels[y * W + x]);
                assertEquals(0, s.gray[y * W + x]);
                assertEquals(4, s.labels[(y + 1) * W + x]);
                assertEquals(0, s.gray[(y + 1) * W + x]);
            }
            samples.add(
                    new float[] {x, Math.round(s.bottom + s.slope * (x - W * .5f)) + .5f, s.gap});
        }
        var track = StaffPitchTrack.fromVerifiedSamples(samples);
        assertTrue(
                DetachedCompactTieRidge.proved(
                        s.labels, s.gray, W, H, s.first, s.last, s.step, track));
    }

    @Test
    public void translatedBowUsesSameProof() {
        assertTrue(new Scene(14, -1, 0, 9, 8, "round").result());
    }

    @Test
    public void staffRulesAloneCannotSupplyBow() {
        assertFalse(scene(14, -1, "empty").result());
    }

    @Test
    public void straightHyphenCannotSupplyBow() {
        assertFalse(scene(14, -1, "straight").result());
    }

    @Test
    public void angularWedgeCannotSupplyBow() {
        assertFalse(scene(14, -1, "angular").result());
    }

    @Test
    public void closedGlyphCannotSupplyBow() {
        assertFalse(scene(14, -1, "hole").result());
    }

    @Test
    public void letterStemsCannotBeCroppedAway() {
        assertFalse(scene(14, -1, "letter").result());
    }

    @Test
    public void branchCannotBeCroppedAway() {
        assertFalse(scene(14, -1, "branch").result());
    }

    @Test
    public void largerPhraseCannotBeCroppedIntoBow() {
        assertFalse(scene(14, -1, "phrase").result());
    }

    @Test
    public void brokenBowCannotBeAssembled() {
        assertFalse(scene(14, -1, "break").result());
    }

    @Test
    public void noteheadMaskCannotBeBorrowed() {
        assertFalse(scene(14, -1, "head").result());
    }

    @Test
    public void twoEligibleBowsAreAmbiguous() {
        assertFalse(scene(14, -1, "two").result());
    }

    @Test
    public void interiorStaffFragmentIsNotAnOutsideBow() {
        assertFalse(scene(14, -1, "inside").result());
    }

    @Test
    public void unknownPhysicalTrackAbstains() {
        var s = scene(14, -1, "round");
        assertFalse(
                DetachedCompactTieRidge.proved(
                        s.labels, s.gray, W, H, s.first, s.last, s.step, null));
    }

    @Test
    public void differentPhysicalPitchCannotBorrowBow() {
        var s = scene(14, -1, "round");
        assertFalse(
                DetachedCompactTieRidge.proved(
                        s.labels,
                        s.gray,
                        W,
                        H,
                        s.first,
                        new DetachedCompactTieRidge.Head(
                                s.last.left(),
                                s.last.right(),
                                s.last.x(),
                                s.last.y() + s.gap * .5f),
                        s.step,
                        s.track()));
    }

    private static Scene localWash(int ink, boolean speckles) {
        var s = scene(14, -1, "round");
        for (int y = 32; y < 59; y++)
            for (int x = 60; x < 135; x++) {
                int at = y * W + x;
                s.gray[at] =
                        (byte)
                                (s.labels[at] == 5
                                        ? Math.round(ink + (170 - ink) * (s.gray[at] & 255) / 245f)
                                        : 170);
            }
        if (speckles)
            for (int x = 60; x < 135; x++) {
                var ys = new ArrayList<Integer>();
                for (int y = 32; y < 59; y++)
                    if (s.labels[y * W + x] == 5 && (s.gray[y * W + x] & 255) < 165) ys.add(y);
                if (!ys.isEmpty()) s.gray[ys.get(ys.size() / 2) * W + x] = 20;
            }
        assertNotNull(s.track());
        return s;
    }

    @Test
    public void weakLocalContrastCannotSupplyContour() {
        assertFalse(localWash(160, false).result());
    }

    @Test
    public void wellContrastedBowInsideLocalShadeRemainsValid() {
        assertTrue(localWash(100, false).result());
    }

    @Test
    public void isolatedDarkSpecklesCannotReplaceLocalStrokeContrast() {
        assertFalse(localWash(160, true).result());
    }

    @Test
    public void sourceArraysRemainExact() {
        var s = scene(14, -1, "round");
        var l = s.labels.clone();
        var g = s.gray.clone();
        assertTrue(s.result());
        assertArrayEquals(l, s.labels);
        assertArrayEquals(g, s.gray);
    }
}
