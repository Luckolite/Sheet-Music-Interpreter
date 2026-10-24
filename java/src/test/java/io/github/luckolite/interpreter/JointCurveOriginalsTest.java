// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Analytic whole bowls and complete touching accents, independent of score pixels. */
public class JointCurveOriginalsTest {
    static final class Field {
        final int scale, w, h;
        final boolean above;
        final byte[] gray;
        final List<ScoreNoteEvent> notes = new ArrayList<>();
        final List<PrintedSlurOwners.GeometryHead> heads = new ArrayList<>();
        final StaffPitchTrack track;

        Field(boolean above, int scale, float phase, float lower) {
            this.above = above;
            this.scale = scale;
            w = 300 * scale;
            h = 320 * scale;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            for (int x = 8 * scale; x < w - 8 * scale; x++)
                for (int r = 0; r < 5; r++)
                    stroke(x, physical((184 - r * 14) * scale + phase), 1.1 * scale);
            for (int x = 80 * scale; x <= 168 * scale; x++) {
                double t = (x - 80. * scale) / (88 * scale);
                stroke(
                        x,
                        physical((float) ((200 + 4 * t + 56 * t * (1 - t)) * scale + phase)),
                        2.2 * scale);
            }
            for (int x = 120 * scale; x <= 148 * scale; x++) {
                double t = (x - 120. * scale) / (28 * scale);
                stroke(x, physical((float) ((lower - 14 + 7 * t) * scale + phase)), 1.4 * scale);
                stroke(x, physical((float) ((lower - 7 * t) * scale + phase)), 1.4 * scale);
            }
            float[][] locations = {{76, 180}, {173, 186}, {135, 180}};
            for (int i = 0; i < locations.length; i++) {
                float x = locations[i][0] * scale, y = physical(locations[i][1] * scale + phase);
                heads.add(new PrintedSlurOwners.GeometryHead(i, x, y, 0, 1));
                for (int yy = Math.round(y - 6 * scale); yy <= y + 6 * scale; yy++)
                    for (int xx = Math.round(x - 9 * scale); xx <= x + 9 * scale; xx++)
                        if (Math.pow((xx - x) / (8. * scale), 2)
                                        + Math.pow((yy - y) / (5. * scale), 2)
                                <= 1) gray[yy * w + xx] = 0;
                notes.add(
                        new ScoreNoteEvent(
                                0,
                                x / w,
                                7,
                                0,
                                1,
                                y / h,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1,
                                1,
                                0,
                                i == 2 ? NoteArticulation.ACCENT : 0,
                                30));
            }
            float bottom = above ? h - 1 - (184 - 56) * scale - phase : 184 * scale + phase;
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, bottom, 14 * scale},
                                    new float[] {w - 1, bottom, 14 * scale}));
        }

        float physical(float y) {
            return above ? h - 1 - y : y;
        }

        void stroke(int x, float y, double radius) {
            for (int yy = (int) Math.floor(y - radius); yy <= y + radius; yy++) {
                int v = (int) Math.round(255 * (1 - Math.max(0, 1 - Math.abs(yy - y) / radius)));
                gray[yy * w + x] = (byte) Math.min(gray[yy * w + x] & 255, v);
            }
        }

        PrintedSlurContours.HeadEvidence hints(long budget) {
            return new PrintedCurveJointRoles(notes, gray, w, h, budget)
                    .wrap(PrintedSlurOwners.headEvidence(heads, gray, w, h, track), heads, track);
        }

        PrintedSlurContours.Result find(long budget) {
            return PrintedSlurContours.find(
                    gray,
                    null,
                    w,
                    h,
                    8 * scale,
                    30 * scale,
                    w - 9 * scale,
                    h - 30 * scale,
                    track,
                    null,
                    hints(budget));
        }

        PrintedSlurPageCapture.Result capture(boolean typed) {
            return PrintedSlurPageCapture.captureGeometry(
                    0,
                    3,
                    gray,
                    null,
                    w,
                    h,
                    List.of(
                            new PrintedSlurPageCapture.Frame<>(
                                    8 * scale,
                                    30 * scale,
                                    w - 9 * scale,
                                    h - 30 * scale,
                                    track,
                                    heads)),
                    typed ? notes : null);
        }
    }

    static Field field(boolean up, float phase, float lower) {
        return new Field(up, 1, phase, lower);
    }

    static void recovers(boolean up, float phase, float lower) {
        var f = field(up, phase, lower);
        var bytes = f.gray.clone();
        var notes = List.copyOf(f.notes);
        var old = f.capture(false);
        var result = f.capture(true);
        assertFalse(result.limitReached());
        assertTrue(old.source().candidates().isEmpty());
        assertEquals(1, result.source().candidates().size());
        var slur = result.source().candidates().get(0);
        assertEquals(0, slur.start().noteIndex());
        assertEquals(1, slur.end().noteIndex());
        assertTrue(slur.id().contains("-joint-arc-"));
        assertEquals(
                up ? ScoreSlurSpan.Placement.ABOVE : ScoreSlurSpan.Placement.BELOW,
                slur.placement());
        assertArrayEquals(bytes, f.gray);
        assertEquals(notes, f.notes);
    }

    static void marks(Field f, int flags) {
        var h = f.heads.get(2);
        f.notes.set(
                2,
                new ScoreNoteEvent(
                        0,
                        h.x() / f.w,
                        7,
                        0,
                        1,
                        h.y() / f.h,
                        false,
                        0,
                        0,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        1,
                        1,
                        0,
                        flags,
                        30));
    }

    @Test
    public void wholeUpperTouchingAccent() {
        recovers(true, .5f, 213);
    }

    @Test
    public void wholeLowerTouchingAccent() {
        recovers(false, .5f, 213);
    }

    @Test
    public void upperBroaderJoin() {
        recovers(true, .5f, 214);
    }

    @Test
    public void lowerBroaderJoin() {
        recovers(false, .5f, 214);
    }

    @Test
    public void upperThreeQuarterPhase() {
        recovers(true, .75f, 212.75f);
    }

    @Test
    public void lowerThreeQuarterPhase() {
        recovers(false, .75f, 212.75f);
    }

    @Test
    public void absentTypedAccentCannotDonatePixels() {
        var f = field(true, .5f, 213);
        marks(f, 0);
        assertTrue(f.find(8000000).jointBows().isEmpty());
    }

    @Test
    public void tenutoCannotSubstituteForAccent() {
        var f = field(false, .5f, 213);
        marks(f, NoteArticulation.TENUTO);
        assertTrue(f.find(8000000).jointBows().isEmpty());
    }

    @Test
    public void absentRawAttackBodyRefuses() {
        var f = field(false, .5f, 213);
        var h = f.heads.get(2);
        for (int y = (int) h.y() - 7; y <= h.y() + 7; y++)
            for (int x = (int) h.x() - 10; x <= h.x() + 10; x++) f.gray[y * f.w + x] = (byte) 255;
        assertTrue(f.find(8000000).jointBows().isEmpty());
    }

    @Test
    public void allTypedRivalOwnersMustBeUnique() {
        var f = field(false, .5f, 213);
        var h = f.heads.get(2);
        f.heads.add(new PrintedSlurOwners.GeometryHead(3, h.x(), h.y(), 0, 1));
        f.notes.add(f.notes.get(2));
        assertTrue(f.find(8000000).jointBows().isEmpty());
    }

    @Test
    public void closedMarkCounterCannotBePartitioned() {
        var f = field(false, .5f, 213);
        for (int y = 199; y <= 214; y++) f.gray[y * f.w + 120] = 0;
        assertTrue(f.find(8000000).jointBows().isEmpty());
    }

    @Test
    public void wholeForeignAppendageIsNotDropped() {
        var f = field(false, .5f, 213);
        for (int x = 148; x <= 180; x++) f.gray[206 * f.w + x] = 0;
        assertTrue(f.find(8000000).jointBows().isEmpty());
    }

    @Test
    public void oversizedConnectedCarrierRefuses() {
        var f = field(false, .5f, 213);
        for (int y = 120; y <= 215; y++) f.gray[y * f.w + 120] = 0;
        assertTrue(f.find(8000000).jointBows().isEmpty());
    }

    @Test
    public void zeroProofBudgetRetainsEveryLegacyBow() {
        var f = field(false, .5f, 213);
        var old = f.find(0);
        assertTrue(old.jointBows().isEmpty());
        assertTrue(f.find(8000000).bows().containsAll(old.bows()));
    }

    @Test
    public void everyBudgetPrefixRetainsLegacyBows() {
        var f = field(true, .5f, 213);
        var old = f.find(0);
        for (long budget :
                new long[] {
                    1, 191, 192, 1000, 10000, 50000, 100000, 250000, 1000000, 4000000, 8000000
                }) assertTrue(f.find(budget).bows().containsAll(old.bows()));
    }

    @Test
    public void missingMetadataKeepsLegacyCapture() {
        var f = field(false, .5f, 213);
        marks(f, 0);
        assertEquals(f.capture(false), f.capture(true));
    }

    @Test
    public void rawAndDiagnosticModesHaveIdenticalWholeCells() {
        var f = field(true, .5f, 213);
        var raw = f.find(8000000);
        var diagnostic =
                PrintedSlurContours.findWithRawEvidence(
                        f.gray,
                        null,
                        f.w,
                        f.h,
                        8,
                        30,
                        f.w - 9,
                        f.h - 30,
                        f.track,
                        null,
                        f.hints(8000000));
        assertEquals(raw.bows(), diagnostic.result().bows());
        assertEquals(1, diagnostic.result().jointBows().size());
        for (var b : diagnostic.result().jointBows()) {
            var columns = diagnostic.witnesses().get(b);
            assertEquals(b.points().size(), columns.size());
            assertTrue(columns.stream().allMatch(c -> c.members().size() == 1));
        }
    }

    @Test
    public void headOrderCannotSelectAFavorableOwnerPrefix() {
        var f = field(false, .5f, 213);
        var before = f.find(8000000).bows();
        Collections.reverse(f.heads);
        assertEquals(before, f.find(8000000).bows());
    }
}
