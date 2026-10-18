// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source-free original semantic seed scenes; crop generation is NOT numeral acceptance. */
public final class MeterComponentSeedsTest {
    static final int W = 600, H = 300, FIRST = 110;
    static final float GAP = 14;

    static final class Page {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];

        Page() {
            Arrays.fill(gray, (byte) 255);
            for (int k = 0; k < 5; k++)
                for (int x = 20; x < W - 20; x++) pixel(x, FIRST + k * 14, 0, 4);
            four(235, 111);
            four(235, 139);
            // One complete semantic component may span paper between the raw stacked glyphs.
            for (int y = 111; y <= 165; y++) for (int x = 235; x <= 255; x++) labels[y * W + x] = 5;
        }

        void pixel(int x, int y, int tone, int label) {
            gray[y * W + x] = (byte) tone;
            labels[y * W + x] = (byte) label;
        }

        void raw(int x, int y) {
            gray[y * W + x] = 0;
        }

        void stroke(int x0, int y0, int x1, int y1) {
            int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
            for (int i = 0; i <= n; i++)
                for (int dx = 0; dx < 3; dx++)
                    for (int dy = 0; dy < 2; dy++)
                        raw(
                                Math.round(x0 + (x1 - x0) * i / (float) n) + dx,
                                Math.round(y0 + (y1 - y0) * i / (float) n) + dy);
        }

        void four(int x, int y) {
            stroke(x + 14, y, x, y + 17);
            stroke(x, y + 17, x + 19, y + 17);
            stroke(x + 14, y, x + 14, y + 25);
        }

        List<MeterChangeDetector.Crop> seeds() {
            return MeterComponentSeeds.crops(
                    labels, gray, W, H, List.of(new MeterComponentSeeds.Hint(FIRST - 4, GAP, 0)));
        }
    }

    @Test
    public void completeStackedFourSeedRecoversIndependentLocalPhase() {
        var p = new Page();
        var crops = p.seeds();
        assertEquals(1, crops.size());
        var c = crops.get(0);
        assertEquals(FIRST, c.firstLine());
        assertEquals(GAP, c.gap(), .05);
        assertTrue(c.left() < 235 && c.right() > 255 && c.top() <= 111 && c.bottom() > 165);
    }

    @Test
    public void componentBoundsAreOnlySeedsNeverAuthoredMeterValues() {
        var p = new Page();
        var crops = p.seeds();
        assertEquals(1, crops.size());
        assertEquals(MeterChangeDetector.Crop.class, crops.get(0).getClass());
    }

    @Test
    public void rawRulesAreRequiredOnBothSides() {
        var p = new Page();
        for (int x = 20; x < W - 20; x++) p.gray[(FIRST + 2 * 14) * W + x] = (byte) 255;
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void semanticStaffLabelsCannotSupplyMissingPrintedRules() {
        var p = new Page();
        Arrays.fill(p.gray, (byte) 255);
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void incompleteOrClippedComponentsCannotBorrowAdjacentInk() {
        var p = new Page();
        for (int y = 139; y <= 165; y++) for (int x = 235; x <= 255; x++) p.labels[y * W + x] = 0;
        assertTrue(p.seeds().isEmpty());
        p = new Page();
        for (int y = 0; y <= 111; y++) p.labels[y * W + 235] = 5;
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void aCompleteClefOrBraceIsNotThisComponentSeed() {
        var p = new Page();
        for (int y = 75; y <= 195; y++) for (int x = 235; x <= 255; x++) p.labels[y * W + x] = 5;
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void compactDynamicsCannotBecomeAFullStaffStack() {
        var p = new Page();
        Arrays.fill(p.labels, (byte) 0);
        for (int y = 120; y <= 141; y++) for (int x = 237; x <= 245; x++) p.labels[y * W + x] = 5;
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void anOwnedHeadInsideTheBodyVetoesTheNewSeed() {
        var p = new Page();
        for (int y = 120; y <= 130; y++) for (int x = 241; x <= 246; x++) p.labels[y * W + x] = 2;
        assertTrue(p.seeds().isEmpty());
    }

    @Test
    public void actualPrecedesNotesEligibilityStillRunsBeforeTheBudget() {
        var p = new Page();
        var measures = List.of(new MeasureRegion(.1f, .9f, .2f, .85f));
        var note = new ScoreNoteEvent(0, .1f, 3, 0, 1, 130f / H, false, 0, 0, 2, 1);
        var all = MeterChangeDetector.candidates(p.labels, p.gray, W, H, measures, List.of(note));
        assertTrue(all.stream().noneMatch(c -> c.left() <= 255 && c.right() >= 235));
    }

    @Test
    public void foreignStaffHintCannotInventAConvenientPhase() {
        var p = new Page();
        assertTrue(
                MeterComponentSeeds.crops(
                                p.labels,
                                p.gray,
                                W,
                                H,
                                List.of(new MeterComponentSeeds.Hint(40, GAP, 0)))
                        .isEmpty());
    }

    @Test
    public void anArtworkRectangleIsNotAcceptedAsAStackedFourMeter() {
        var p = new Page();
        Arrays.fill(p.gray, (byte) 255);
        for (int k = 0; k < 5; k++)
            for (int x = 20; x < W - 20; x++) p.gray[(FIRST + k * 14) * W + x] = 0;
        for (int y = 111; y <= 165; y++) for (int x = 235; x <= 255; x++) p.raw(x, y);
        var values =
                MeterChangeDetector.rasterMeterCandidates(
                        p.labels,
                        p.gray,
                        W,
                        H,
                        List.of(new MeasureRegion(.1f, .9f, .2f, .85f)),
                        List.of());
        assertTrue(values.isEmpty());
    }

    @Test
    public void duplicateHintsAndInputsAreNotMutatedOrDuplicated() {
        var p = new Page();
        byte[] a = p.gray.clone(), b = p.labels.clone();
        var hint = new MeterComponentSeeds.Hint(FIRST - 4, GAP, 0);
        var crops = MeterComponentSeeds.crops(p.labels, p.gray, W, H, List.of(hint, hint));
        assertEquals(1, crops.size());
        assertArrayEquals(a, p.gray);
        assertArrayEquals(b, p.labels);
    }
}
