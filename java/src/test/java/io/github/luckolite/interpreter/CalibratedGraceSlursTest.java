// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent short bowed connections over literal gray rules; no recorded score pixels. */
public class CalibratedGraceSlursTest {
    static final class Scene {
        final int w = 320, h = 220;
        final boolean above;
        final byte[] gray = new byte[w * h];
        final List<PrintedSlurOwners.GeometryHead> heads = new ArrayList<>();
        final List<ScoreNoteEvent> notes = new ArrayList<>();
        final StaffPitchTrack track;

        Scene(boolean above, float phase, float bend) {
            this.above = above;
            Arrays.fill(gray, (byte) 255);
            for (int x = 4; x < w - 4; x++)
                for (int k = 0; k < 5; k++)
                    for (int y = 93; y < 164; y++) {
                        double d = Math.abs(y - (100 + phase + 14 * k));
                        int v = (int) Math.round(255 - (255 - 40) * Math.max(0, 1 - d / 2.2));
                        int p = physical(y) * w + x;
                        gray[p] = (byte) Math.min(gray[p] & 255, v);
                    }
            head(0, 130, 92 + phase, true);
            head(1, 161, 99 + phase, false);
            for (int y = 64; y <= 92; y++) paint(above ? 125 : 135, physical(y + phase), 1);
            for (int y = 99; y <= 143; y++) {
                paint(above ? 169 : 152, physical(y + phase), 1);
                paint(above ? 170 : 153, physical(y + phase), 1);
            }
            for (int x = 127; x <= 153; x++) {
                double t = (x - 127) / 26.;
                paint(x, physical((float) (102 + 8 * t + 4 * bend * t * (1 - t) + phase)), 2.2);
            }
            float bottom = above ? h - 1 - (100 + phase) : 156 + phase;
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(new float[] {0, bottom, 14}, new float[] {w - 1, bottom, 14}));
        }

        int physical(int y) {
            return above ? h - 1 - y : y;
        }

        float physical(float y) {
            return above ? h - 1 - y : y;
        }

        void paint(int x, float y, double radius) {
            for (int yy = (int) Math.floor(y - radius); yy <= y + radius; yy++) {
                double opacity = Math.max(0, 1 - Math.abs(yy - y) / radius);
                int p = yy * w + x;
                gray[p] = (byte) Math.round((gray[p] & 255) * (1 - opacity));
            }
        }

        void head(int owner, float x, float y, boolean grace) {
            float yy = physical(y);
            heads.add(new PrintedSlurOwners.GeometryHead(owner, x, yy, 0, 1, grace));
            double rx = grace ? 5.5 : 8, ry = grace ? 3.5 : 4.5;
            for (int row = (int) Math.floor(yy - ry); row <= yy + ry; row++)
                for (int col = (int) Math.floor(x - rx); col <= x + rx; col++)
                    if (Math.pow((col - x) / rx, 2) + Math.pow((row - yy) / ry, 2) <= 1)
                        gray[row * w + col] = 0;
            int direction = grace ? 1 : -1;
            if (above) direction = -direction;
            notes.add(
                    new ScoreNoteEvent(
                            0,
                            x / w,
                            above ? (grace ? 8 : 9) : (grace ? 9 : 8),
                            0,
                            1,
                            yy / h,
                            false,
                            0,
                            1,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            0,
                            1,
                            0,
                            grace ? NoteOrnament.GRACE : 0,
                            30,
                            false,
                            0,
                            false,
                            0,
                            0,
                            1,
                            direction));
        }

        PrintedSlurPageCapture.Frame<PrintedSlurOwners.GeometryHead> frame() {
            return new PrintedSlurPageCapture.Frame<>(4, 35, w - 5, h - 30, track, heads);
        }

        List<CalibratedGraceSlurs.Observed> discover(long budget) {
            return new CalibratedGraceSlurs(notes, gray, w, h, budget).discover(List.of(frame()));
        }

        PrintedSlurPageCapture.Result capture(boolean metadata) {
            return PrintedSlurPageCapture.captureGeometry(
                    0, notes.size(), gray, null, w, h, List.of(frame()), metadata ? notes : null);
        }
    }

    static Scene positive() {
        return new Scene(true, 0, 3.5f);
    }

    static void recovers(boolean above, float phase, float bend) {
        var f = new Scene(above, phase, bend);
        var bytes = f.gray.clone();
        var notes = List.copyOf(f.notes);
        assertTrue(f.capture(false).source().candidates().isEmpty());
        var r = f.capture(true);
        assertFalse(r.limitReached());
        assertEquals(1, r.source().candidates().size());
        var c = r.source().candidates().get(0);
        assertEquals(0, c.start().noteIndex());
        assertEquals(1, c.end().noteIndex());
        assertTrue(c.id().contains("-grace-cell-"));
        assertEquals(
                above ? ScoreSlurSpan.Placement.ABOVE : ScoreSlurSpan.Placement.BELOW,
                c.placement());
        assertArrayEquals(bytes, f.gray);
        assertEquals(notes, f.notes);
    }

    static void note(Scene f, int owner, int marks, int stem) {
        var n = f.notes.get(owner);
        f.notes.set(
                owner,
                new ScoreNoteEvent(
                        n.measureIndex(),
                        n.positionInMeasure(),
                        n.staffStep(),
                        0,
                        1,
                        n.pageY(),
                        false,
                        0,
                        1,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        0,
                        1,
                        0,
                        marks,
                        30,
                        false,
                        0,
                        false,
                        0,
                        0,
                        1,
                        stem));
    }

    @Test
    public void upperShortBowUsesCompletePixelSupport() {
        recovers(true, 0, 3.5f);
    }

    @Test
    public void upperQuarterPhaseKeepsActualHeads() {
        recovers(true, .25f, 3.5f);
    }

    @Test
    public void upperHalfPhaseKeepsActualHeads() {
        recovers(true, .5f, 3.5f);
    }

    @Test
    public void upperThreeQuarterPhaseKeepsActualHeads() {
        recovers(true, .75f, 3.5f);
    }

    @Test
    public void lowerQuarterPhaseCrossingIsRecovered() {
        recovers(false, .25f, 6);
    }

    @Test
    public void lowerHalfPhaseCrossingIsRecovered() {
        recovers(false, .5f, 6);
    }

    @Test
    public void absentGraceRoleCannotUseTheNewRoute() {
        var f = positive();
        note(f, 0, 0, -1);
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void unresolvedPrincipalStemCannotUseTheNewRoute() {
        var f = positive();
        note(f, 1, 0, 0);
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void clusteredGraceHeadsAreNotSilentlyReducedToOne() {
        var f = positive();
        f.head(2, 115, 94, true);
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void ambiguousNextPrincipalIsNotFirstWins() {
        var f = positive();
        f.head(2, 162, 101, false);
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void absentRawGraceBodyRefuses() {
        var f = positive();
        var h = f.heads.get(0);
        for (int y = (int) h.y() - 5; y <= h.y() + 5; y++)
            for (int x = (int) h.x() - 7; x <= h.x() + 7; x++) f.gray[y * f.w + x] = (byte) 255;
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void absentRawPrincipalBodyRefuses() {
        var f = positive();
        var h = f.heads.get(1);
        for (int y = (int) h.y() - 6; y <= h.y() + 6; y++)
            for (int x = (int) h.x() - 9; x <= h.x() + 9; x++) f.gray[y * f.w + x] = (byte) 255;
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void brokenRawPrincipalShaftRefuses() {
        var f = positive();
        for (int y = 110; y <= 139; y += 2)
            for (int x = 168; x <= 171; x++) f.gray[f.physical(y) * f.w + x] = (byte) 255;
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void contradictoryStaffToneRefuses() {
        var f = positive();
        f.gray[f.physical(100) * f.w + 110] = 41;
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void oneSidedReferenceCannotReplaceTheOtherSide() {
        var f = positive();
        int y = Math.round(f.track.at(130)[0] - 4 * 14 - 14 * .48f);
        for (int x = 174; x < f.w - 1; x++) f.gray[y * f.w + x] = (byte) 254;
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void straightConnectorIsNotReturningBow() {
        assertTrue(new Scene(false, 0, 0).discover(8000000).isEmpty());
    }

    @Test
    public void closedCounterContactIsNotAThinBow() {
        var f = positive();
        for (int x = 134; x <= 144; x++)
            for (int y = 104; y <= 114; y++)
                if (x == 134 || x == 144 || y == 104 || y == 114)
                    f.gray[f.physical(y) * f.w + x] = 0;
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void unownedLongAppendageCannotSupplyATermination() {
        var f = positive();
        for (int y = 110; y <= 128; y++) f.gray[f.physical(y) * f.w + 140] = 0;
        assertTrue(f.discover(8000000).isEmpty());
    }

    @Test
    public void zeroAndRefusedBudgetsNeverPublishAPrefix() {
        var f = positive();
        for (long n : new long[] {0, 1, 64, 1024, 10000, 100000, 500000})
            assertTrue(f.discover(n).isEmpty());
    }

    @Test
    public void lateFinalFrameRefusalClearsEarlierCandidate() {
        var f = positive();
        var full = new CalibratedGraceSlurs(f.notes, f.gray, f.w, f.h);
        assertEquals(1, full.discover(List.of(f.frame())).size());
        long spent = 8000000 - full.workRemaining();
        var limited = new CalibratedGraceSlurs(f.notes, f.gray, f.w, f.h, spent + 256);
        assertTrue(limited.discover(List.of(f.frame(), f.frame())).isEmpty());
    }

    @Test
    public void headOrderKeepsStableSourceIdentity() {
        var f = positive();
        var before = f.capture(true).source();
        Collections.reverse(f.heads);
        assertEquals(before, f.capture(true).source());
    }

    @Test
    public void alreadyCapturedValidBowKeepsItsOldRecord() {
        var f = new Scene(false, 0, 4.5f);
        var before = f.capture(false).source();
        assertEquals(1, before.candidates().size());
        assertEquals(before, f.capture(true).source());
    }
}
