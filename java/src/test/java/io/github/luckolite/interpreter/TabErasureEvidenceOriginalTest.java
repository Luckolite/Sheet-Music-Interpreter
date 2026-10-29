// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rules, note pixels and typed tokens; no score or device material. */
public class TabErasureEvidenceOriginalTest {
    private static final int W = 500, H = 400;

    private byte[] raster(boolean grayscale) {
        byte[] data = new byte[W * H];
        Arrays.fill(data, (byte) (grayscale ? 255 : 0));
        for (int y = 80; y <= 180; y += 20)
            Arrays.fill(data, y * W + 20, y * W + 480, (byte) (grayscale ? 0 : 2));
        for (int y = 106; y <= 112; y++)
            Arrays.fill(data, y * W + 148, y * W + 158, (byte) (grayscale ? 0 : 1));
        return data;
    }

    private TablatureDecoder.Staff candidate(float standardTop) {
        return new TablatureDecoder.Staff(80, 20, standardTop, List.of(), List.of());
    }

    @Test
    public void geometryOnlyCandidatePreservesGrayAndLabelNotePixels() {
        for (boolean grayscale : new boolean[] {false, true}) {
            byte[] data = raster(grayscale), before = data.clone();
            byte[] result =
                    TablatureDecoder.withoutTabs(data, W, H, List.of(candidate(-1)), grayscale);
            assertArrayEquals("Unconfirmed rules cannot erase optical notation", before, result);
            assertArrayEquals(before, data);
        }
    }

    @Test
    public void detectedRulesRemainCandidatesAfterOcrButDoNotOwnOpticalPixels() {
        byte[] gray = raster(true), before = gray.clone();
        var tabs = TablatureDecoder.withWords(TablatureDecoder.detect(gray, W, H), List.of(), W, H);
        tabs = TabNotation.rasterRhythm(tabs, gray, W, H, List.of());
        assertEquals(1, tabs.size());
        assertEquals(6, tabs.get(0).stringCount());
        assertTrue(tabs.get(0).frets().isEmpty());
        assertTrue(tabs.get(0).bars().isEmpty());
        assertTrue(tabs.get(0).tuning().isEmpty());
        assertArrayEquals(before, TablatureDecoder.withoutTabs(gray, W, H, tabs, true));
        assertArrayEquals(before, gray);
    }

    @Test
    public void nearbyStandardStaffDoesNotByItselfAuthorizeErasure() {
        byte[] data = raster(true);
        assertArrayEquals(
                data, TablatureDecoder.withoutTabs(data, W, H, List.of(candidate(20)), true));
    }

    @Test
    public void unconfirmedCandidateCannotDiscardExistingEmptyMeasureGeometry() {
        var score =
                new ScorePageInterpretation(
                        List.of(new MeasureRegion(.1f, .9f, .05f, .18f)), List.of(), 9);
        assertEquals(score, TablatureDecoder.apply(score, List.of(candidate(-1)), W, H));
    }

    @Test
    public void mixedCandidatesMaskOnlyTheConfirmedBand() {
        byte[] data = raster(true), before = data.clone();
        Arrays.fill(data, 300 * W, 301 * W, (byte) 0);
        before = data.clone();
        var confirmed =
                new TablatureDecoder.Staff(
                        280, 10, -1, List.of(new TablatureDecoder.Fret(250, 280, 0, 0)), List.of());
        byte[] result =
                TablatureDecoder.withoutTabs(data, W, H, List.of(candidate(-1), confirmed), true);
        assertEquals("Unconfirmed band retains the printed note", 0, result[109 * W + 152]);
        assertEquals(255, result[300 * W + 250] & 255);
        assertArrayEquals(before, data);
    }

    @Test
    public void numericOpenMuteAndRestTokensKeepSixAndSevenStringSupport() {
        for (int strings : new int[] {6, 7})
            for (int fret : new int[] {0, 3, -1, -2}) {
                var token = new TablatureDecoder.Fret(250, 80, 0, fret, 1, 0, 0, 0);
                var tab =
                        new TablatureDecoder.Staff(
                                80, 20, -1, List.of(token), List.of(), strings, List.of());
                byte[] gray = raster(true);
                assertEquals(
                        255,
                        TablatureDecoder.withoutTabs(gray, W, H, List.of(tab), true)[109 * W + 152]
                                & 255);
                var result =
                        TablatureDecoder.apply(
                                new ScorePageInterpretation(List.of(), List.of()),
                                List.of(tab),
                                W,
                                H);
                assertEquals(1, result.measures().size());
                assertEquals(fret == -2 ? 0 : 1, result.notes().size());
                assertEquals(fret == -2 ? 1 : 0, result.rests().size());
                if (fret == -1)
                    assertEquals(ScoreNoteEvent.Kind.UNPITCHED, result.notes().get(0).kind());
            }
    }

    @Test
    public void blankBarredOrExplicitlyTunedTabsStillMaskRules() {
        for (int strings : new int[] {6, 7}) {
            var tuning =
                    strings == 6
                            ? List.of(64, 59, 55, 50, 45, 40)
                            : List.of(64, 59, 55, 50, 45, 40, 35);
            for (var tab :
                    List.of(
                            new TablatureDecoder.Staff(
                                    80, 20, -1, List.of(), List.of(20f), strings, List.of()),
                            new TablatureDecoder.Staff(
                                    80, 20, -1, List.of(), List.of(20f, 480f), strings, List.of()),
                            new TablatureDecoder.Staff(
                                    80, 20, -1, List.of(), List.of(), strings, tuning))) {
                assertEquals(
                        255,
                        TablatureDecoder.withoutTabs(raster(true), W, H, List.of(tab), true)[
                                        109 * W + 152]
                                & 255);
            }
        }
    }

    @Test
    public void barredPairedTabStillReconcilesPrintedBarCuts() {
        var input = List.of(new MeasureRegion(.04f, .96f, .03f, .15f));
        var tab = new TablatureDecoder.Staff(80, 20, 20, List.of(), List.of(20f, 250f, 480f));
        var result = TablatureDecoder.reconcileMeasures(input, List.of(tab), W, H);
        assertEquals(2, result.size());
        assertEquals(.5f, result.get(0).right(), 0);
        assertEquals(.5f, result.get(1).left(), 0);
    }
}
