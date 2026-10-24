// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently drawn heads and returning bows; side clearance is measured from visible ink. */
public class HeadInkEdgeSlurOriginalsTest {
    static final class Field {
        final int w, h, scale;
        final float gap;
        final byte[] gray;
        final boolean above;
        final PrintedSlurContours.Bow bow;
        final StaffPitchTrack track;
        final ArrayList<PrintedSlurOwners.Head> heads = new ArrayList<>();

        Field(boolean above, boolean start, int scale, float phase) {
            this.above = above;
            this.scale = scale;
            w = 320 * scale;
            h = 400 * scale;
            gap = 20 * scale;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            float center = (160 + phase) * scale;
            heads.add(new PrintedSlurOwners.Head(3, 96 * scale, center, 0, 1, 1, 0, 67));
            heads.add(new PrintedSlurOwners.Head(8, 224 * scale, center, 0, 1, 1, 100, 72));
            for (var head : heads) draw(head);
            var points = new ArrayList<PrintedSlurContours.Point>();
            for (int x = 108 * scale; x <= 212 * scale; x++) {
                double t = (x - 108. * scale) / (104 * scale);
                double first = start ? 115 : 130, last = start ? 130 : 115;
                double y = first + (last - first) * t - 35 * 4 * t * (1 - t);
                if (!above) y = 320 - y;
                float yy = (float) ((y + phase) * scale);
                points.add(new PrintedSlurContours.Point(x, yy));
                gray[Math.round(yy) * w + x] = 30;
            }
            bow = new PrintedSlurContours.Bow(points, above, 1.75f, 0);
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, 300 * scale, gap},
                                    new float[] {w - 1, 300 * scale, gap}));
        }

        void draw(PrintedSlurOwners.Head head) {
            for (int y = Math.round(head.y() - 8 * scale); y <= head.y() + 8 * scale; y++)
                for (int x = Math.round(head.x() - 11 * scale); x <= head.x() + 11 * scale; x++)
                    if (Math.pow((x - head.x()) / (10. * scale), 2)
                                    + Math.pow((y - head.y()) / (7. * scale), 2)
                            <= 1) gray[y * w + x] = 0;
        }

        void erase(int index) {
            var head = heads.get(index);
            for (int y = Math.round(head.y() - 10 * scale); y <= head.y() + 10 * scale; y++)
                for (int x = Math.round(head.x() - 14 * scale); x <= head.x() + 14 * scale; x++)
                    gray[y * w + x] = (byte) 255;
        }

        PrintedSlurOwners.Result result() {
            return result(Set.of());
        }

        PrintedSlurOwners.Result result(Set<PrintedSlurOwners.Tie> ties) {
            byte[] before = gray.clone();
            var original = List.copyOf(heads);
            var r =
                    PrintedSlurOwners.resolve(
                            "original-ink-edge", 2, bow, heads, ties, gray, w, h, track);
            assertArrayEquals(before, gray);
            assertEquals(original, heads);
            return r;
        }

        void resolved() {
            var r = result();
            assertEquals(PrintedSlurOwners.Status.RESOLVED, r.status());
            var span = r.span().orElseThrow();
            assertEquals(3, span.start().noteIndex());
            assertEquals(8, span.end().noteIndex());
        }

        void replace(int index, float dx, float dy, int staff, int count, long attack, int pitch) {
            var a = heads.get(index);
            heads.set(
                    index,
                    new PrintedSlurOwners.Head(
                            a.owner(),
                            a.x() + dx * scale,
                            a.y() + dy * scale,
                            staff,
                            count,
                            1,
                            attack,
                            pitch));
        }
    }

    @Test
    public void upperEndUsesActualFacingInk() {
        new Field(true, false, 1, 0).resolved();
    }

    @Test
    public void lowerEndUsesActualFacingInk() {
        new Field(false, false, 1, 0).resolved();
    }

    @Test
    public void upperStartUsesActualFacingInk() {
        new Field(true, true, 1, 0).resolved();
    }

    @Test
    public void lowerStartUsesActualFacingInk() {
        new Field(false, true, 1, 0).resolved();
    }

    @Test
    public void doubleDensityKeepsPhysicalClearance() {
        new Field(true, false, 2, 0).resolved();
    }

    @Test
    public void tripleDensityKeepsPhysicalClearance() {
        new Field(false, true, 3, 0).resolved();
    }

    @Test
    public void quarterPixelHeadCentroidKeepsOwner() {
        new Field(true, false, 1, .25f).resolved();
    }

    @Test
    public void halfPixelHeadCentroidKeepsOwner() {
        new Field(false, false, 1, .5f).resolved();
    }

    @Test
    public void missingHeadBodyCannotSupplyAnEdge() {
        var f = new Field(true, false, 1, 0);
        f.erase(1);
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, f.result().status());
    }

    @Test
    public void bareRuleCannotSupplyAnEdge() {
        var f = new Field(true, false, 1, 0);
        f.erase(1);
        for (int x = 210; x <= 238; x++) f.gray[160 * f.w + x] = 0;
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, f.result().status());
    }

    @Test
    public void thinShaftCannotSupplyAnEdge() {
        var f = new Field(true, false, 1, 0);
        f.erase(1);
        for (int y = 153; y <= 167; y++) f.gray[y * f.w + 224] = 0;
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, f.result().status());
    }

    @Test
    public void tooDistantObservedInkStillRefuses() {
        var f = new Field(true, false, 1, 0);
        f.erase(1);
        f.replace(1, 0, 10, 0, 1, 100, 72);
        f.draw(f.heads.get(1));
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, f.result().status());
    }

    @Test
    public void farSideHeadCannotBorrowFacingInk() {
        var f = new Field(true, false, 1, 0);
        f.erase(1);
        f.replace(1, -25, 0, 0, 1, 100, 72);
        f.draw(f.heads.get(1));
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, f.result().status());
    }

    @Test
    public void horizontalGapStillRefuses() {
        var f = new Field(true, false, 1, 0);
        f.erase(1);
        f.replace(1, 9, 0, 0, 1, 100, 72);
        f.draw(f.heads.get(1));
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, f.result().status());
    }

    @Test
    public void twoRawHeadOwnersRemainAmbiguous() {
        var f = new Field(true, false, 1, 0);
        f.heads.add(new PrintedSlurOwners.Head(9, 225, 160, 0, 1, 2, 100, 74));
        f.draw(f.heads.get(2));
        assertEquals(PrintedSlurOwners.Status.AMBIGUOUS_END, f.result().status());
    }

    @Test
    public void reversedWrittenClockStillRefuses() {
        var f = new Field(true, false, 1, 0);
        f.replace(1, 0, 0, 0, 1, 0, 72);
        assertEquals(PrintedSlurOwners.Status.REVERSED_CLOCK, f.result().status());
    }

    @Test
    public void samePitchStillNeedsMusicalProof() {
        var f = new Field(true, false, 1, 0);
        f.replace(1, 0, 0, 0, 1, 100, 67);
        assertEquals(PrintedSlurOwners.Status.SAME_PITCH_UNRESOLVED, f.result().status());
    }

    @Test
    public void retainedTieKeepsPrecedence() {
        var f = new Field(true, false, 1, 0);
        f.replace(1, 0, 0, 0, 1, 100, 67);
        assertEquals(
                PrintedSlurOwners.Status.TIE_ALREADY_RETAINED,
                f.result(Set.of(new PrintedSlurOwners.Tie(3, 8))).status());
    }

    @Test
    public void incompatibleTieStillRefuses() {
        var f = new Field(true, false, 1, 0);
        assertEquals(
                PrintedSlurOwners.Status.TIE_PITCH_CONFLICT,
                f.result(Set.of(new PrintedSlurOwners.Tie(3, 8))).status());
    }

    @Test
    public void differentPhysicalStaffStillRefuses() {
        var f = new Field(true, false, 1, 0);
        f.replace(1, 0, 0, 1, 2, 100, 72);
        assertEquals(PrintedSlurOwners.Status.DIFFERENT_LANES, f.result().status());
    }

    @Test
    public void whiteBodyMetadataCannotSupplyInk() {
        var f = new Field(true, false, 1, 0);
        f.erase(0);
        assertEquals(PrintedSlurOwners.Status.NO_START_OWNER, f.result().status());
    }

    @Test
    public void headOrderCannotChangeTheBoundSpan() {
        var f = new Field(true, false, 1, 0);
        Collections.reverse(f.heads);
        f.resolved();
    }

    static Field thinHead(boolean detachedLine) {
        var f = new Field(true, false, 1, 0);
        f.erase(1);
        for (int y = 157; y <= 163; y++)
            for (int x = 214; x <= 234; x++)
                if (Math.pow((x - 224) / 10., 2) + Math.pow((y - 160) / 3., 2) <= 1)
                    f.gray[y * f.w + x] = 0;
        if (detachedLine) for (int x = 212; x <= 236; x++) f.gray[153 * f.w + x] = 0;
        return f;
    }

    @Test
    public void smallObservedBodyDoesNotBorrowUnseenHeight() {
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, thinHead(false).result().status());
    }

    @Test
    public void detachedRuleCannotMoveTheFacingEdge() {
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, thinHead(true).result().status());
    }

    @Test
    public void faintInkCannotBorrowTheStrictBodyThreshold() {
        var f = new Field(true, false, 1, 0);
        for (int i = 0; i < f.gray.length; i++) if (f.gray[i] == 0) f.gray[i] = (byte) 165;
        assertTrue(f.result().span().isEmpty());
    }

    @Test
    public void unsupportedNearEdgeCannotBeSuppliedByWhitePixels() {
        var f = new Field(true, false, 1, 0);
        f.erase(1);
        for (int y = 159; y <= 161; y++) for (int x = 218; x <= 230; x++) f.gray[y * f.w + x] = 0;
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, f.result().status());
    }
}
