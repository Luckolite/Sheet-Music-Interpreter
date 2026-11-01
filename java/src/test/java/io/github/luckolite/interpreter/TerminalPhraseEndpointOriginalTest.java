// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.stream.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original ordinary heads, shafts, full beam strips and outward phrase caps. */
public class TerminalPhraseEndpointOriginalTest {
    static final int W = 280, H = 210;
    static final float GAP = 16;

    static class Scene {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];
        final List<PrintedSlurOwners.GeometryHead> heads = new ArrayList<>();
        final List<ScoreNoteEvent> notes = new ArrayList<>();
        final boolean above;
        final List<PlayingTechniqueDetector.Staff> staffs;
        final PrintedSlurPageCapture.Frame<PrintedSlurOwners.GeometryHead> frame;
        ScorePageInterpretation score;
        PrintedSlurPageCapture.Result captured;
        PrintedPhraseEndpoints.Pending pending;

        Scene(
                boolean end,
                boolean above,
                boolean beam,
                boolean chord,
                boolean rival,
                boolean far,
                boolean overlap,
                boolean tie) {
            this.above = above;
            Arrays.fill(gray, (byte) 255);
            for (int y = 66; y <= 130; y += 16) rect(4, W - 5, y, y);
            int[] xs = tie ? new int[] {65, 205} : new int[] {65, 135, 205};
            for (int i = 0; i < xs.length; i++)
                head(xs[i], tie ? 130 : 130 - i * 8, beam ? 1 : 0, true, tie && i == 1);
            if (chord) head(65, 114, beam ? 1 : 0, true, false);
            if (rival) head(65, 154, 0, false, false);
            if (beam) rect(75, 215, 36, 42);
            int left = far ? 5 : (chord ? 70 : (end ? 75 : 45)), right = (end || chord) ? 235 : 195;
            for (int x = left; x <= right; x++) {
                double t = (x - left) / (double) (right - left);
                int y =
                        (int)
                                Math.round(
                                        (1 - t) * (chord ? 148 : (end ? 155 : 160))
                                                + t * 140
                                                + 20 * 4 * t * (1 - t));
                ink(x, y, 0);
            }
            var regions = new ArrayList<MeasureRegion>();
            regions.add(new MeasureRegion(40f / W, 240f / W, 0, 1));
            if (overlap) regions.add(regions.get(0));
            score =
                    new ScorePageInterpretation(regions, notes)
                            .withClockContextSource(
                                    ScoreClockContextSource.unavailable(
                                            notes.size(), regions.size(), 0));
            float top = above ? H - 1 - 130 : 66, bottom = above ? H - 1 - 66 : 130;
            staffs = List.of(new PlayingTechniqueDetector.Staff(top, bottom, GAP, 0, 1));
            var track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, bottom, GAP},
                                    new float[] {W - 1, bottom, GAP}));
            frame = new PrintedSlurPageCapture.Frame<>(0, 0, W - 1, H - 1, track, heads);
        }

        int px(int x) {
            return above ? W - 1 - x : x;
        }

        int py(int y) {
            return above ? H - 1 - y : y;
        }

        void ink(int x, int y, int label) {
            int p = py(y) * W + px(x);
            gray[p] = 0;
            if (label != 0) labels[p] = (byte) label;
        }

        void rect(int l, int r, int t, int b) {
            for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) ink(x, y, 0);
        }

        void head(int x, int y, int beams, boolean up, boolean tied) {
            int owner = notes.size();
            float yy = py(y);
            float bottom = above ? H - 1 - 66 : 130;
            int step = Math.round((bottom - yy) * 2 / GAP);
            heads.add(new PrintedSlurOwners.GeometryHead(owner, px(x), yy, 0, 1));
            notes.add(
                    new ScoreNoteEvent(
                            0,
                            (px(x) - 40) / 200f,
                            step,
                            0,
                            1,
                            yy / H,
                            tied,
                            0,
                            beams,
                            2,
                            beams == 0 ? 1 : 0,
                            1,
                            0,
                            0,
                            30,
                            false,
                            0,
                            false,
                            0,
                            0,
                            1,
                            above ? -1 : 1,
                            ScoreNoteEvent.Kind.PITCHED,
                            ScoreNoteEvent.VisualSize.FULL));
            for (int yy0 = y - 7; yy0 <= y + 7; yy0++)
                for (int xx = x - 11; xx <= x + 11; xx++)
                    if (Math.pow((xx - x) / 9.6, 2) + Math.pow((yy0 - y) / 6.4, 2) <= 1)
                        ink(xx, yy0, 2);
            if (up) rect(x + 10, x + 10, 36, y);
            else rect(x - 10, x - 10, y, 195);
        }

        void observe() {
            captured =
                    PrintedSlurPageCapture.captureGeometry(
                            0, notes.size(), gray, labels, W, H, List.of(frame), notes);
            assertFalse(captured.limitReached());
            assertFalse(captured.declined().isEmpty());
            assertTrue(captured.source().spans().isEmpty());
            assertTrue(captured.source().candidates().isEmpty());
            pending = PrintedPhraseEndpoints.retain(score, captured, gray, W, H, 0);
            assertNotNull(pending);
        }

        ScorePageInterpretation complete() {
            return PrintedBeamSource.apply(score, labels, gray, W, H, staffs, 0, pending);
        }
    }

    static ScoreSlurSource classify(ScorePageInterpretation score, boolean exactTie) {
        var written =
                ScorePlacementMap.resolveWritten(score.notes(), new ScoreMeterMap(4, List.of()));
        var values = new ArrayList<ScoreSlurClassification.Note>();
        for (int i = 0; i < score.notes().size(); i++) {
            var p = written.get(i);
            int midi =
                    ScoreTiePitchGuard.midi(
                            score.notes().get(i), List.of(new ScoreKeyChange(0, 0)));
            long attack = Math.round(p.onsetBeats() * 10080),
                    release = Math.round((p.onsetBeats() + p.durationBeats()) * 10080);
            values.add(new ScoreSlurClassification.Note(i, attack, 0, midi, release, 0, 1));
        }
        return ScoreSlurClassification.resolve(
                score.slurSource(),
                values,
                exactTie ? Set.of(new ScoreSlurClassification.Tie(0, 1)) : Set.of());
    }

    static Scene positive(boolean end, boolean above, boolean chord) {
        var s = new Scene(end, above, true, chord, false, false, false, false);
        s.observe();
        var legacy = PrintedBeamSource.apply(s.score, s.labels, s.gray, W, H, s.staffs, 0);
        assertFalse(legacy.beamSource().groups().isEmpty());
        assertTrue(legacy.slurSource().candidates().isEmpty());
        var got = s.complete();
        assertEquals(s.notes, got.notes());
        assertEquals(s.score.measures(), got.measures());
        assertSame(s.score.clockContextSource(), got.clockContextSource());
        assertTrue(got.slurSource().spans().isEmpty());
        assertEquals(1, got.slurSource().candidates().size());
        var classified = classify(got, false);
        assertEquals(1, classified.spans().size());
        assertTrue(classified.candidates().isEmpty());
        assertEquals(
                1,
                classified
                        .engravingSpans(IntStream.range(0, s.notes.size()).boxed().toList())
                        .size());
        return s;
    }

    @Test
    public void firstOutwardCapUsesCompleteFirstLiteralAttack() {
        positive(false, false, false);
    }

    @Test
    public void lastOutwardCapUsesCompleteLastLiteralAttack() {
        positive(true, false, false);
    }

    @Test
    public void upperOutwardCapKeepsPhysicalOrientation() {
        positive(false, true, false);
    }

    @Test
    public void sharedStemChoosesOutsideFacingChordHead() {
        positive(false, false, true);
    }

    @Test
    public void prepopulatedBeamSourceCannotIssueNewProof() {
        var s = new Scene(false, false, true, false, false, false, false, false);
        s.observe();
        var old = PrintedBeamSource.apply(s.score, s.labels, s.gray, W, H, s.staffs, 0);
        assertSame(
                old, PrintedBeamSource.apply(old, s.labels, s.gray, W, H, s.staffs, 0, s.pending));
    }

    @Test
    public void noteEditRefusesPendingOriginalOwnership() {
        var s = new Scene(false, false, true, false, false, false, false, false);
        s.observe();
        var edits = new ArrayList<>(s.notes);
        edits.set(0, edits.get(0).withOctaveShift(1));
        s.score = s.score.withNotes(edits, IntStream.range(0, edits.size()).boxed().toList());
        assertTrue(s.complete().slurSource().candidates().isEmpty());
    }

    @Test
    public void sourceMutationRefusesPendingFrame() {
        var s = new Scene(false, false, true, false, false, false, false, false);
        s.observe();
        s.gray[0] = (byte) 254;
        assertTrue(s.complete().slurSource().candidates().isEmpty());
    }

    @Test
    public void ordinaryUnbeamedQuartersHaveNoGroupAuthority() {
        var s = new Scene(false, false, false, false, false, false, false, false);
        s.observe();
        var got = s.complete();
        assertTrue(got.beamSource().groups().isEmpty());
        assertTrue(got.slurSource().candidates().isEmpty());
    }

    @Test
    public void farCapOutsideMeasureIsNotNearestHeadFallback() {
        var s = new Scene(false, false, true, false, false, true, false, false);
        s.observe();
        var got = s.complete();
        assertFalse(got.beamSource().groups().isEmpty());
        assertTrue(got.slurSource().candidates().isEmpty());
    }

    @Test
    public void overlappingMeasureOwnershipRefusesNewEndpoint() {
        var s = new Scene(false, false, true, false, false, false, true, false);
        s.observe();
        assertTrue(s.complete().slurSource().candidates().isEmpty());
    }

    @Test
    public void independentSimultaneousVoiceCannotBorrowBeamColumn() {
        var s = new Scene(false, false, true, false, true, false, false, false);
        s.observe();
        assertTrue(s.complete().slurSource().candidates().isEmpty());
    }

    @Test
    public void actualSamePitchTieRemainsTieNotForcedSlur() {
        var s = new Scene(false, false, true, false, false, false, false, true);
        s.observe();
        var got = s.complete();
        assertEquals(1, got.slurSource().candidates().size());
        assertTrue(got.notes().get(1).tiedFromPrevious());
        var classified = classify(got, true);
        assertTrue(classified.spans().isEmpty());
        assertTrue(classified.candidates().isEmpty());
    }
}
