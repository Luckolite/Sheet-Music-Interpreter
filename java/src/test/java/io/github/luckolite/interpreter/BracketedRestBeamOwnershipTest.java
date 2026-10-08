// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original ensemble geometry: a bracketed moving voice beside a held quarter and another staff. */
public class BracketedRestBeamOwnershipTest {
    static final int W = 400, H = 500;
    static final float G = 20;
    static final List<MeasureRegion> BARS = List.of(new MeasureRegion(0, 1, .06f, .70f));
    static final String[] THREE = {
        "..#######...",
        ".##########.",
        "###......###",
        "####.....###",
        "####.....###",
        "####.....###",
        ".##.....####",
        ".......####.",
        "......####..",
        "....#####...",
        "....#####...",
        "....#####...",
        "......####..",
        ".......####.",
        "##.....####.",
        "###....####.",
        "###....####.",
        "###....####.",
        ".###....###.",
        "..########..",
        "..########..",
        "....####...."
    };

    static void set(byte[] g, int x, int y, int ink) {
        g[y * W + x] = (byte) ink;
    }

    static void line(byte[] g, int x0, int y0, int x1, int y1, int thick, int ink) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int d = 0; d <= steps; d++) {
            float f = steps == 0 ? 0 : d / (float) steps;
            int x = Math.round(x0 + f * (x1 - x0)), y = Math.round(y0 + f * (y1 - y0));
            for (int dx = -(thick / 2); dx <= thick / 2; dx++)
                for (int dy = -(thick / 2); dy <= thick / 2; dy++) set(g, x + dx, y + dy, ink);
        }
    }

    static byte[] pixels(
            int paper,
            boolean left,
            boolean right,
            boolean hooks,
            boolean beam,
            boolean stems,
            boolean closed) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) paper);
        for (int y = 0; y < THREE.length; y++)
            for (int x = 0; x < 12; x++)
                if (THREE[y].charAt(x) == '#' || closed && x < 2 && y > 1 && y < 20)
                    set(g, x + 164, y + 200, 20);
        if (left) {
            line(g, 95, 210, 155, 210, 1, 20);
            if (hooks) line(g, 95, 190, 95, 210, 1, 20);
        }
        if (right) {
            line(g, 185, 210, 230, 210, 1, 20);
            if (hooks) line(g, 230, 190, 230, 210, 1, 20);
        }
        if (beam) line(g, 148, 180, 208, 170, 5, 20);
        if (stems) {
            line(g, 148, 120, 148, 180, 3, 20);
            line(g, 208, 100, 208, 170, 3, 20);
        }
        return g;
    }

    static List<ScoreNoteEvent> notes(boolean foreignBeam, boolean opposite, boolean grace) {
        var n = new ArrayList<ScoreNoteEvent>();
        n.add(
                new ScoreNoteEvent(0, .25f, -3, 0, 2, 140f / H, false, 0, 0, 2, 1, 1)
                        .withStemDirection(1));
        n.add(
                new ScoreNoteEvent(0, .40f, 0, 0, 2, 120f / H, false, 0, 1, 2, 0, 1)
                        .withStemDirection(-1));
        n.add(
                new ScoreNoteEvent(
                                0,
                                .55f,
                                2,
                                0,
                                2,
                                100f / H,
                                false,
                                0,
                                1,
                                2,
                                0,
                                1,
                                0,
                                grace ? NoteOrnament.GRACE : 0)
                        .withStemDirection(opposite ? 1 : -1));
        if (foreignBeam) {
            n.add(
                    new ScoreNoteEvent(0, .325f, 0, 1, 2, 285f / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(1));
            n.add(
                    new ScoreNoteEvent(0, .575f, 0, 1, 2, 285f / H, false, 0, 1, 2, 0, 1)
                            .withStemDirection(1));
        } else n.add(new ScoreNoteEvent(0, .425f, 0, 1, 2, 300f / H, false, 0, 0, 2, 1, 1));
        return n;
    }

    static ScoreRestEvent rest(int staff, double value) {
        return new ScoreRestEvent(0, .25f, 120f / H, .04f, staff, 2, value);
    }

    static TripletRhythmDetector.Rhythm run(
            byte[] g, List<ScoreNoteEvent> n, List<ScoreRestEvent> rests) {
        return TripletRhythmDetector.withRests(n, rests, BARS, g, W, H);
    }

    static TripletRhythmDetector.Rhythm ordinary(byte[] g) {
        return run(g, notes(false, false, false), List.of(rest(0, .5)));
    }

    static void correct(TripletRhythmDetector.Rhythm r) {
        assertEquals(4, r.notes().size());
        assertEquals(1, r.notes().get(0).tupletDivisor());
        assertEquals(1, ScoreNoteTiming.writtenDurationBeats(r.notes().get(0)), 0);
        assertEquals(3, r.notes().get(1).tupletDivisor());
        assertEquals(3, r.notes().get(2).tupletDivisor());
        assertEquals(1, r.notes().get(3).tupletDivisor());
        assertEquals(1. / 3, r.rests().get(0).durationBeats(), 1e-8);
    }

    static void unchanged(byte[] g, List<ScoreNoteEvent> n, List<ScoreRestEvent> rest) {
        var r = run(g, n, rest);
        assertEquals(n, r.notes());
        assertEquals(rest, r.rests());
    }

    @Test
    public void actualRestAndTwoBeamHeadsOwnCompleteBracketBesideHeldQuarter() {
        correct(ordinary(pixels(255, true, true, true, true, true, false)));
    }

    @Test
    public void shadedCompletePrintedBodyStillOwnsOnlyItsMovingVoice() {
        correct(ordinary(pixels(145, true, true, true, true, true, false)));
    }

    @Test
    public void missingActualRestNeverCreatesASilentSlot() {
        unchanged(
                pixels(255, true, true, true, true, true, false),
                notes(false, false, false),
                List.of());
    }

    @Test
    public void anotherStaffCannotSupplyTheRest() {
        unchanged(
                pixels(255, true, true, true, true, true, false),
                notes(false, false, false),
                List.of(rest(1, .5)));
    }

    @Test
    public void differentWrittenRestValueCannotJoinEighths() {
        unchanged(
                pixels(255, true, true, true, true, true, false),
                notes(false, false, false),
                List.of(rest(0, 1)));
    }

    @Test
    public void missingLeftArmKeepsIndependentRhythm() {
        unchanged(
                pixels(255, false, true, true, true, true, false),
                notes(false, false, false),
                List.of(rest(0, .5)));
    }

    @Test
    public void missingRightArmKeepsIndependentRhythm() {
        unchanged(
                pixels(255, true, false, true, true, true, false),
                notes(false, false, false),
                List.of(rest(0, .5)));
    }

    @Test
    public void twoUnhookedLinesDoNotOwnRestGroup() {
        unchanged(
                pixels(255, true, true, false, true, true, false),
                notes(false, false, false),
                List.of(rest(0, .5)));
    }

    @Test
    public void semanticBeamCountWithoutPrintedBodyDoesNotOwnNumeral() {
        unchanged(
                pixels(255, true, true, true, false, true, false),
                notes(false, false, false),
                List.of(rest(0, .5)));
    }

    @Test
    public void missingRealShaftDoesNotOwnNumeral() {
        unchanged(
                pixels(255, true, true, true, true, false, false),
                notes(false, false, false),
                List.of(rest(0, .5)));
    }

    @Test
    public void oppositeSoundingDirectionsKeepIndependentOwnership() {
        unchanged(
                pixels(255, true, true, true, true, true, false),
                notes(false, true, false),
                List.of(rest(0, .5)));
    }

    @Test
    public void genuineGraceCannotBecomeThirdWrittenSlot() {
        unchanged(
                pixels(255, true, true, true, true, true, false),
                notes(false, false, true),
                List.of(rest(0, .5)));
    }

    @Test
    public void nearerActualForeignBeamRetainsItsNumeral() {
        byte[] g = pixels(255, true, true, true, true, true, false);
        line(g, 142, 285, 142, 225, 3, 20);
        line(g, 242, 285, 242, 225, 3, 20);
        line(g, 142, 225, 242, 225, 5, 20);
        unchanged(g, notes(true, false, false), List.of(rest(0, .5)));
    }

    @Test
    public void closedEightCannotRelabelTheRestAndNotes() {
        unchanged(
                pixels(255, true, true, true, true, true, true),
                notes(false, false, false),
                List.of(rest(0, .5)));
    }

    @Test
    public void shadedPaperAloneDoesNotSupplyMissingBracketArms() {
        unchanged(
                pixels(145, false, false, false, true, true, false),
                notes(false, false, false),
                List.of(rest(0, .5)));
    }

    @Test
    public void aShaftGapCannotReachTheForeignBeam() {
        byte[] g = pixels(255, true, true, true, true, true, false);
        for (int y = 135; y <= 146; y++) for (int x = 145; x <= 151; x++) set(g, x, y, 255);
        unchanged(g, notes(false, false, false), List.of(rest(0, .5)));
    }

    @Test
    public void sourceInputsAndHeldFieldsRemainUnmodified() {
        byte[] g = pixels(255, true, true, true, true, true, false), copy = g.clone();
        var n = notes(false, false, false);
        var rests = List.of(rest(0, .5));
        correct(run(g, n, rests));
        assertArrayEquals(copy, g);
        assertEquals(1, n.get(1).tupletDivisor());
        assertEquals(.5, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void reapplyingCannotScaleRestTwice() {
        byte[] g = pixels(255, true, true, true, true, true, false);
        var r = ordinary(g);
        assertEquals(r, run(g, r.notes(), r.rests()));
    }
}
