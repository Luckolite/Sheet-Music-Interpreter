// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original dense-beam/row fixtures; numeral outline derives from the Apache-2.0 low-ledger test. */
public class DenseLedgerRowTupletTest {
    static final int W = 1100, H = 400;

    record Fixture(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes) {}

    static void rect(
            byte[] g, int w, int h, float scale, boolean upper, int x, int y, int rw, int rh) {
        for (int yy = Math.round(y * scale);
                yy < Math.max(Math.round(y * scale) + 1, Math.round((y + rh) * scale));
                yy++)
            for (int xx = Math.round(x * scale);
                    xx < Math.max(Math.round(x * scale) + 1, Math.round((x + rw) * scale));
                    xx++) {
                int py = upper ? h - 1 - yy : yy;
                if (xx >= 0 && xx < w && py >= 0 && py < h) g[py * w + xx] = 0;
            }
    }

    static Fixture fixture(float scale, boolean upper, String mode) {
        int w = Math.round(W * scale), h = Math.round(H * scale);
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        boolean noStaff = mode.equals("noStaff") || mode.equals("falseFive"),
                missing = mode.equals("missingRail");
        if (!noStaff)
            for (int y = 200; y <= 280; y += 20)
                if (!missing || y != 220) rect(g, w, h, scale, upper, 100, y, 930, 2);
        if (mode.equals("falseFive"))
            for (int y = 200; y <= 248; y += 12) rect(g, w, h, scale, upper, 430, y, 600, 6);
        for (int x : new int[] {200, 250, 300}) {
            rect(g, w, h, scale, upper, x - 5, 327, 11, 6);
            rect(g, w, h, scale, upper, upper ? x - 6 : x + 5, 240, 2, 91);
        }
        if (!mode.equals("noBeam")) {
            rect(g, w, h, scale, upper, upper ? 194 : 205, 240, 102, 10);
            rect(g, w, h, scale, upper, upper ? 194 : 205, 254, 102, 10);
        }
        if (!mode.equals("noNumeral"))
            for (int y = 0; y < 24; y++)
                for (int x = 0; x < 14; x++) {
                    boolean ink =
                            y < 3 && x >= 3 && x <= 11
                                    || y >= 2 && y < 10 && x >= 11
                                    || y >= 10 && y < 13 && x >= 5 && x <= 12
                                    || y >= 13 && y < 22 && x >= 11
                                    || y >= 21 && x >= 2 && x <= 11
                                    || y >= 2 && y <= 4 && x <= 3
                                    || y >= 19 && y <= 21 && x <= 2
                                    || mode.equals("eight") && x < 3;
                    if (ink) rect(g, w, h, scale, upper, 243 + x, 166 + y, 1, 1);
                }
        if (mode.equals("disagree")) {
            for (int yy = Math.round(190 * scale); yy < Math.round(300 * scale); yy++)
                for (int xx = Math.round(720 * scale); xx < Math.round(1031 * scale); xx++)
                    g[yy * w + xx] = (byte) 255;
            for (int y = 206; y <= 286; y += 20) rect(g, w, h, scale, upper, 720, y, 311, 2);
        }
        if (mode.equals("wrongPhase")) {
            for (int yy = Math.round(190 * scale); yy < Math.round(325 * scale); yy++)
                for (int xx = Math.round(430 * scale); xx < Math.round(1031 * scale); xx++)
                    g[yy * w + xx] = (byte) 255;
            for (int y = 220; y <= 300; y += 20) rect(g, w, h, scale, upper, 430, y, 601, 2);
        }
        int[] xs =
                mode.equals("oneWitness")
                        ? new int[] {540}
                        : mode.equals("disagree") ? new int[] {540, 900} : new int[] {540, 640};
        var ns = new ArrayList<ScoreNoteEvent>();
        int step = upper ? 13 : -5;
        for (int x : new int[] {200, 250, 300}) {
            float py = upper ? h - 1 - 330 * scale : 330 * scale;
            ns.add(
                    new ScoreNoteEvent(
                                    0,
                                    (x - 120) / 300f,
                                    mode.equals("ordinaryPitch") ? 0 : step,
                                    0,
                                    1,
                                    py / h,
                                    false,
                                    0,
                                    2,
                                    2,
                                    0f)
                            .withStemDirection(upper ? -1 : 1));
        }
        int regionShift = mode.equals("otherRow") ? 180 : 0;
        int headShift = mode.equals("wrongPhase") ? 20 : 0;
        for (int x : xs) {
            int shift = headShift + (mode.equals("disagree") && x == 900 ? 6 : 0);
            rect(g, w, h, scale, upper, x - 5, 277 + shift, 11, 6);
            float py = upper ? h - 1 - (280 + shift) * scale : (280 + shift) * scale;
            int count = mode.equals("parallelPart") ? 2 : 1,
                    index = mode.equals("parallelPart") ? 1 : 0;
            ns.add(
                    new ScoreNoteEvent(
                            1,
                            (x - 430) / 600f,
                            upper ? 8 : 0,
                            index,
                            count,
                            py / h,
                            false,
                            0,
                            0,
                            2,
                            1f));
        }
        if (mode.equals("crossPart")) {
            var n = ns.get(3);
            ns.set(
                    3,
                    new ScoreNoteEvent(
                            n.measureIndex(),
                            n.positionInMeasure(),
                            n.staffStep(),
                            n.staffIndex(),
                            n.staffCount(),
                            n.pageY(),
                            false,
                            0,
                            0,
                            2,
                            1,
                            1,
                            0,
                            0,
                            30,
                            true,
                            0,
                            false,
                            0,
                            0,
                            1,
                            0,
                            ScoreNoteEvent.Kind.PITCHED));
        }
        float top = upper ? (h - 340 * scale) / h : 180f / H,
                bottom = upper ? (h - 180 * scale) / h : 340f / H;
        var measures =
                List.of(
                        new MeasureRegion(120f / W, 420f / W, top, bottom),
                        new MeasureRegion(
                                (mode.equals("disconnected") ? 650f : 430f) / W,
                                1030f / W,
                                top + regionShift / (float) H,
                                bottom + regionShift / (float) H));
        return new Fixture(g, w, h, measures, ns);
    }

    static List<ScoreNoteEvent> read(Fixture f) {
        return TripletRhythmDetector.withRests(
                        f.notes(), List.of(), f.measures(), f.gray(), f.width(), f.height())
                .notes();
    }

    static void ratios(Fixture f, int expected) {
        var out = read(f);
        assertEquals(f.notes().size(), out.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(expected, out.get(i).tupletDivisor());
            assertEquals(expected == 3 ? 2 : 1, out.get(i).tupletNormalNotes());
            assertEquals(
                    f.notes().get(i).withTupletRatio(expected, expected == 3 ? 2 : 1), out.get(i));
        }
        for (int i = 3; i < out.size(); i++) assertEquals(f.notes().get(i), out.get(i));
    }

    static void assertContextReachesLedgerFallback(String mode) throws Exception {
        var f = fixture(1, false, mode);
        byte[] crop = new byte[141 * 121];
        for (int y = 180; y <= 300; y++)
            System.arraycopy(f.gray(), y * W + 180, crop, (y - 180) * 141, 141);
        assertTrue(
                "Old local proof must be absent",
                RawStaffLineDetector.detect(crop, 141, 121).isEmpty());
        assertTrue(
                PrintedTupletBeamOwner.connectedHeads(f.gray(), W, H, 200, 330, 300, 330, 20, 1));
        float firstBottom = mode.equals("wrongPhase") ? 300 : 280;
        var one = PrintedDirectionStaff.printedRules(f.gray(), W, H, 540, firstBottom, 20);
        assertNotNull("First context must independently have five rails", one);
        assertEquals(firstBottom, one[0], 1);
        if (mode.equals("disagree")) {
            var two = PrintedDirectionStaff.printedRules(f.gray(), W, H, 900, 286, 20);
            assertNotNull("Contradictory context must independently have five rails", two);
            assertEquals(286, two[0], 1);
            assertTrue(Math.abs(one[0] - two[0]) > 20 * .2f);
        }
        var type =
                Arrays.stream(TripletRhythmDetector.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("Glyph"))
                        .findFirst()
                        .orElseThrow();
        var ctor = type.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        ctor.setAccessible(true);
        Object glyph = ctor.newInstance(243, 166, 256, 189);
        var method =
                Arrays.stream(TripletRhythmDetector.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("ledgerBeamOwns"))
                        .findFirst()
                        .orElseThrow();
        method.setAccessible(true);
        var args =
                new ArrayList<Object>(
                        List.of(
                                glyph,
                                f.notes().get(0),
                                f.notes(),
                                f.measures().get(0),
                                f.gray(),
                                W,
                                H,
                                200f,
                                300f,
                                20f));
        if (method.getParameterCount() == 11) args.add(f.measures());
        assertEquals(false, method.invoke(null, args.toArray()));
    }

    @Test
    public void denseBeamUsesIndependentlyPrintedRow() {
        ratios(fixture(1, false, "positive"), 3);
    }

    @Test
    public void smallerRasterPhaseRetainsContext() {
        ratios(fixture(.8f, false, "positive"), 3);
    }

    @Test
    public void largerRasterPhaseRetainsContext() {
        ratios(fixture(1.2f, false, "positive"), 3);
    }

    @Test
    public void mirroredHighLedgerContext() {
        ratios(fixture(1, true, "positive"), 3);
    }

    @Test
    public void absentStaffAbstains() {
        ratios(fixture(1, false, "noStaff"), 1);
    }

    @Test
    public void absentBeamAbstains() {
        ratios(fixture(1, false, "noBeam"), 1);
    }

    @Test
    public void absentNumeralNeverFillsCapacity() {
        ratios(fixture(1, false, "noNumeral"), 1);
    }

    @Test
    public void closedEightIsNotThree() {
        ratios(fixture(1, false, "eight"), 1);
    }

    @Test
    public void missingRailAbstains() {
        ratios(fixture(1, false, "missingRail"), 1);
    }

    @Test
    public void beamOnlyFiveThickRailsAbstain() {
        ratios(fixture(1, false, "falseFive"), 1);
    }

    @Test
    public void neighboringRowCannotProveThisStaff() {
        ratios(fixture(1, false, "otherRow"), 1);
    }

    @Test
    public void parallelPartCannotSupplyWitness() {
        ratios(fixture(1, false, "parallelPart"), 1);
    }

    @Test
    public void crossStaffWitnessAbstains() {
        ratios(fixture(1, false, "crossPart"), 1);
    }

    @Test
    public void wrongHeadRowPhaseAbstains() throws Exception {
        assertContextReachesLedgerFallback("wrongPhase");
        ratios(fixture(1, false, "wrongPhase"), 1);
    }

    @Test
    public void aSingleCompleteColumnIsInsufficient() {
        ratios(fixture(1, false, "oneWitness"), 1);
    }

    @Test
    public void disconnectedMeasureContextAbstains() {
        ratios(fixture(1, false, "disconnected"), 1);
    }

    @Test
    public void ordinaryStaffPitchCannotBorrowLedgerWindow() {
        ratios(fixture(1, false, "ordinaryPitch"), 1);
    }

    @Test
    public void completeButDisagreeingRailsAbstain() throws Exception {
        assertContextReachesLedgerFallback("disagree");
        ratios(fixture(1, false, "disagree"), 1);
    }

    @Test
    public void uniformOrdinaryBarWithoutGlyphIsNotFilled() {
        var f = fixture(1, false, "noNumeral");
        byte[] g = f.gray();
        for (int y = 238; y < 337; y++) for (int x = 170; x < 411; x++) g[y * W + x] = (byte) 255;
        for (int y : new int[] {240, 260, 280}) rect(g, W, H, 1, false, 100, y, 321, 2);
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 12; i++) {
            int x = 200 + 15 * i;
            rect(g, W, H, 1, false, x - 4, 327, 9, 6);
            rect(g, W, H, 1, false, x + 4, 240, 2, 91);
            notes.add(
                    new ScoreNoteEvent(0, (x - 120) / 300f, -5, 0, 1, 330f / H, false, 0, 2, 2, 0)
                            .withStemDirection(1));
        }
        for (int i = 0; i < 4; i++) {
            rect(g, W, H, 1, false, 204 + i * 45, 240, 32, 10);
            rect(g, W, H, 1, false, 204 + i * 45, 254, 32, 10);
        }
        notes.addAll(f.notes().subList(3, 5));
        var out = read(new Fixture(g, W, H, f.measures(), notes));
        assertEquals(notes, out);
        assertEquals(
                3,
                out.subList(0, 12).stream()
                        .mapToDouble(ScoreNoteTiming::writtenDurationBeats)
                        .sum(),
                1e-8);
    }
}
