// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original overlapping quarter/rest pixels with independently drawn triplet ownership. */
public final class UpQuarterCapRestTest {
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

    static byte[] groupInk(
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

    static List<ScoreNoteEvent> movingAndHeld(
            boolean foreignBeam, boolean opposite, boolean grace) {
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

    static void ellipse(byte[] g, int cx, int cy, int rx, int ry, int shade) {
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++)
                if (Math.pow((x - cx) / (double) rx, 2) + Math.pow((y - cy) / (double) ry, 2) <= 1)
                    g[y * W + x] = (byte) shade;
    }

    static byte[] pixels(
            boolean rest,
            boolean cap,
            boolean head,
            boolean shaft,
            int slope,
            boolean shortTail,
            int shade,
            boolean left,
            boolean right,
            boolean hooks,
            boolean beam,
            boolean movingShafts) {
        byte[] g = groupInk(255, left, right, hooks, beam, movingShafts, false);
        if (head) ellipse(g, 100, 140, 14, 6, shade);
        if (shaft) line(g, 114, 80, 114, 140, 1, shade);
        ellipse(g, 160, 120, 12, 6, 20);
        ellipse(g, 220, 100, 12, 6, 20);
        if (rest) {
            if (cap) ellipse(g, 96, 146, 8, 5, shade);
            int last = shortTail ? 155 : 180;
            for (int y = 145; y <= last; y++) {
                int x = Math.round(106 - slope * (y - 145) * .25f);
                g[y * W + x] = (byte) shade;
                g[y * W + x + 1] = (byte) shade;
            }
        }
        for (int j = 0; j < 5; j++) for (int x = 20; x < W - 20; x++) g[(80 + j * 20) * W + x] = 20;
        return g;
    }

    static List<ScoreNoteEvent> notes() {
        return movingAndHeld(false, false, false);
    }

    static List<ScoreRestEvent> run(byte[] g, List<ScoreNoteEvent> notes, boolean tracked) {
        byte[] before = g.clone();
        var out =
                SixteenthRestDetector.detect(
                        g,
                        W,
                        H,
                        BARS,
                        List.of(
                                new SixteenthRestDetector.Staff(
                                        80,
                                        160,
                                        G,
                                        0,
                                        2,
                                        tracked ? StaffPitchTrack.linear(W, 160, G, 0) : null)),
                        notes);
        assertArrayEquals(before, g);
        return out;
    }

    static byte[] normal() {
        return pixels(true, true, true, true, 1, false, 20, true, true, true, true, true);
    }

    static void correct(byte[] g) {
        var rest = run(g, notes(), false);
        assertEquals(rest.toString(), 1, rest.size());
        assertEquals(.5, rest.get(0).durationBeats(), 0);
    }

    static void absent(byte[] g) {
        var rest = run(g, notes(), false);
        assertTrue(rest.toString(), rest.isEmpty());
    }

    @Test
    public void overlappingQuarterAndRestRetainASeparateSilentSlot() {
        correct(normal());
    }

    @Test
    public void fullyOccludedCapStillRequiresTheCompleteVisibleTail() {
        correct(pixels(true, false, true, true, 1, false, 20, true, true, true, true, true));
    }

    @Test
    public void faintTailAndQuarterKeepTheSameWrittenValue() {
        correct(pixels(true, true, true, true, 1, false, 145, true, true, true, true, true));
    }

    @Test
    public void trackedStaffKeepsTheSamePhysicalOwners() {
        assertEquals(1, run(normal(), notes(), true).size());
    }

    @Test
    public void quarterAloneDoesNotInventARest() {
        absent(pixels(false, false, true, true, 1, false, 20, true, true, true, true, true));
    }

    @Test
    public void aShortUnterminatedTailCannotInventTheRemainingBody() {
        absent(pixels(true, true, true, true, 1, true, 20, true, true, true, true, true));
    }

    @Test
    public void aStraightShaftCannotSupplyTheRestTail() {
        absent(pixels(true, true, true, true, 0, false, 20, true, true, true, true, true));
    }

    @Test
    public void aReverseTailCannotSupplyTheRest() {
        absent(pixels(true, true, true, true, -1, false, 20, true, true, true, true, true));
    }

    @Test
    public void missingActualQuarterHeadCannotSupplyAnOwner() {
        absent(pixels(true, true, false, true, 1, false, 20, true, true, true, true, true));
    }

    @Test
    public void missingActualQuarterShaftCannotSupplyAnOwner() {
        absent(pixels(true, true, true, false, 1, false, 20, true, true, true, true, true));
    }

    @Test
    public void missingLeftBracketCannotSupplyASilentSlot() {
        absent(pixels(true, true, true, true, 1, false, 20, false, true, true, true, true));
    }

    @Test
    public void missingRightBracketCannotSupplyASilentSlot() {
        absent(pixels(true, true, true, true, 1, false, 20, true, false, true, true, true));
    }

    @Test
    public void unhookedLinesCannotSupplyTheTripletGroup() {
        absent(pixels(true, true, true, true, 1, false, 20, true, true, false, true, true));
    }

    @Test
    public void metadataBeamWithoutPrintedInkCannotSupplyTheGroup() {
        absent(pixels(true, true, true, true, 1, false, 20, true, true, true, false, true));
    }

    @Test
    public void missingPrintedMovingShaftCannotSupplyTheGroup() {
        absent(pixels(true, true, true, true, 1, false, 20, true, true, true, true, false));
    }

    @Test
    public void visibleRestAndNotesRemainUnchangedOnRepeatedRecognition() {
        byte[] g = normal(), copy = g.clone();
        var n = notes();
        var a = run(g, n, false);
        assertEquals(a, run(g, n, false));
        assertArrayEquals(copy, g);
        assertEquals(1, n.get(0).unbeamedDurationBeats(), 0);
        assertEquals(1, n.get(1).tupletDivisor());
    }

    @Test
    public void noPrintedNumeralCannotEstablishAnOccludedRest() {
        byte[] g = normal();
        for (int y = 200; y < 222; y++) for (int x = 164; x < 176; x++) g[y * W + x] = (byte) 255;
        absent(g);
    }

    @Test
    public void aClosedEightDoesNotEstablishAThreeSlotGroup() {
        byte[] g = normal();
        for (int y = 202; y < 220; y++) for (int x = 164; x < 166; x++) g[y * W + x] = 20;
        absent(g);
    }

    @Test
    public void aBeamedOwnerCannotStandForTheIndependentQuarter() {
        var n = new ArrayList<>(notes());
        n.set(
                0,
                new ScoreNoteEvent(0, .25f, -3, 0, 2, 140f / H, false, 0, 1, 2, 0, 1)
                        .withStemDirection(1));
        assertTrue(run(normal(), n, false).isEmpty());
    }

    @Test
    public void aGraceOwnerCannotStandForTheIndependentQuarter() {
        var n = new ArrayList<>(notes());
        n.set(0, n.get(0).withArticulations(NoteOrnament.GRACE));
        assertTrue(run(normal(), n, false).isEmpty());
    }

    @Test
    public void aScaledOwnerCannotStandForTheIndependentQuarter() {
        var n = new ArrayList<>(notes());
        n.set(0, n.get(0).withTupletRatio(3, 2));
        assertTrue(run(normal(), n, false).isEmpty());
    }

    @Test
    public void anotherStaffCannotSupplyTheIndependentQuarter() {
        var n = new ArrayList<>(notes());
        n.set(
                0,
                new ScoreNoteEvent(0, .25f, -3, 1, 2, 140f / H, false, 0, 0, 2, 1, 1)
                        .withStemDirection(1));
        assertTrue(run(normal(), n, false).isEmpty());
    }

    @Test
    public void aHalfNoteCannotStandForTheIndependentQuarter() {
        var n = new ArrayList<>(notes());
        n.set(
                0,
                new ScoreNoteEvent(0, .25f, -3, 0, 2, 140f / H, false, 0, 0, 2, 2, 1)
                        .withStemDirection(1));
        assertTrue(run(normal(), n, false).isEmpty());
    }

    @Test
    public void anotherStaffCannotSupplyAMovingSlot() {
        var n = new ArrayList<>(notes());
        n.set(
                2,
                new ScoreNoteEvent(0, .55f, 2, 1, 2, 100f / H, false, 0, 1, 2, 0, 1)
                        .withStemDirection(-1));
        assertTrue(run(normal(), n, false).isEmpty());
    }
}
