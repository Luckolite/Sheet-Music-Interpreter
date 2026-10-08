// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric drawings of thick printed outlines; no score material. */
public class ThickCounterHarmonicsTest {
    private static final int W = 240, H = 240, X = 100, Y = 160;
    private float ellipseHeight = .65f;
    private float glyphScale = .5f;
    private int glyphDx = 0, glyphDy = 0;
    private boolean outerRules = false;
    private double ellipseTilt = 0;

    private List<ScoreNoteEvent> run(
            int gap,
            int hole,
            boolean ellipse,
            int beams,
            boolean semantic,
            boolean detached,
            boolean rule) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        int cx = X + glyphDx, cy = Y - gap * 3 / 2 + glyphDy, r = Math.round(gap * glyphScale);
        for (int y = cy - r - 1; y <= cy + r + 1; y++)
            for (int x = cx - r - 1; x <= cx + r + 1; x++) {
                double u = (x - cx) * Math.cos(ellipseTilt) + (y - cy) * Math.sin(ellipseTilt),
                        v = -(x - cx) * Math.sin(ellipseTilt) + (y - cy) * Math.cos(ellipseTilt);
                boolean filled =
                        ellipse
                                ? Math.pow(u / r, 2)
                                                + Math.pow(v / Math.max(2, r * ellipseHeight), 2)
                                        <= 1
                                : Math.abs(x - cx) + Math.abs(y - cy) <= r;
                if (filled) {
                    gray[y * W + x] = 0;
                    if (semantic) labels[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
                }
            }
        int first = -(hole / 2);
        for (int y = first; y < first + hole; y++)
            for (int x = first; x < first + hole; x++) gray[(cy + y) * W + cx + x] = (byte) 255;
        for (int y = Y - gap / 3; y <= Y + gap / 3; y++)
            for (int x = X - gap / 2; x <= X + gap / 2; x++)
                if (Math.pow((x - X) / (double) (gap / 2), 2)
                                + Math.pow((y - Y) / (double) (gap / 3), 2)
                        <= 1) {
                    gray[y * W + x] = 0;
                    labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                }
        for (int y = cy; y <= Y + 2 * gap; y++)
            for (int x = X - gap / 2 - 1; x <= X - gap / 2 + 1; x++) gray[y * W + x] = 0;
        if (detached)
            for (int y = cy + gap / 3; y < Y - gap / 3; y++)
                for (int x = X - r - 2; x <= X - r + 2; x++) gray[y * W + x] = (byte) 255;
        if (rule) for (int x = 20; x < 220; x++) gray[cy * W + x] = 0;
        if (outerRules)
            for (int dy : new int[] {-Math.round(gap * .3f), Math.round(gap * .3f)})
                for (int x = 20; x < 220; x++) gray[(cy + dy) * W + x] = 0;
        var n =
                new ScoreNoteEvent(
                        0,
                        X / (float) W,
                        0,
                        0,
                        1,
                        Y / (float) H,
                        false,
                        0,
                        beams,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        0,
                        0,
                        0,
                        0,
                        ScoreNoteEvent.CLEF_TREBLE);
        return ArtificialHarmonics.apply(
                labels,
                gray,
                W,
                H,
                List.of(new MeasureRegion(0, 1, 0, 1)),
                List.of(n),
                List.of(new PlayingTechniqueDetector.Staff(Y - 4 * gap, Y, gap, 0, 1)));
    }

    @Test
    public void thickDiamondWithSmallEnclosedCounterAcrossStaffSizes() {
        for (int gap : new int[] {12, 16, 20, 24})
            assertEquals(
                    "gap=" + gap,
                    2,
                    run(gap, 2, false, 2, true, false, false).get(0).octaveShift());
    }

    @Test
    public void crossingRuleLeavesEnclosedPaperAboveAndBelow() {
        for (int gap : new int[] {16, 20, 24})
            assertEquals(
                    "gap=" + gap, 2, run(gap, 5, false, 2, true, false, true).get(0).octaveShift());
    }

    @Test
    public void solidDiamondAndSinglePaperPixelRemainRejected() {
        for (int hole : new int[] {0, 1})
            for (int gap : new int[] {12, 16, 20, 24})
                assertEquals(0, run(gap, hole, false, 2, true, false, false).get(0).octaveShift());
    }

    @Test
    public void thickHollowOvalRemainsAnOrdinaryHead() {
        for (int gap : new int[] {12, 16, 20, 24})
            assertEquals(0, run(gap, 2, true, 2, true, false, false).get(0).octaveShift());
    }

    @Test
    public void thickRoundCounterCannotBecomeADiamond() {
        ellipseHeight = 1;
        for (int gap : new int[] {12, 16, 20, 24})
            assertEquals(
                    "gap=" + gap, 0, run(gap, 2, true, 2, true, false, false).get(0).octaveShift());
    }

    @Test
    public void roundCountersWithOffsetSizeAndRuleInterferenceRemainRejected() {
        ellipseHeight = 1;
        for (int gap : new int[] {12, 16, 20, 24})
            for (float size : new float[] {.5f, .6f, .7f, .8f})
                for (int dx : new int[] {-2, 0, 2})
                    for (int dy : new int[] {-2, 0, 2})
                        for (boolean rules : new boolean[] {false, true}) {
                            glyphScale = size;
                            glyphDx = dx;
                            glyphDy = dy;
                            outerRules = rules;
                            assertEquals(
                                    "gap=" + gap + " size=" + size + " dx=" + dx + " dy=" + dy
                                            + " rules=" + rules,
                                    0,
                                    run(gap, 2, true, 2, true, false, false).get(0).octaveShift());
                        }
    }

    @Test
    public void tiltedOvalCountersAtMultipleSizesRemainOrdinaryHeads() {
        for (int gap : new int[] {12, 16, 24})
            for (float size : new float[] {.5f, .7f})
                for (float ratio : new float[] {.5f, .75f, .9f})
                    for (int degrees : new int[] {0, 20, 40, 60})
                        for (int dx : new int[] {-2, 0, 2})
                            for (int dy : new int[] {-2, 0, 2})
                                for (boolean rules : new boolean[] {false, true}) {
                                    ellipseHeight = ratio;
                                    ellipseTilt = Math.toRadians(degrees);
                                    glyphScale = size;
                                    glyphDx = dx;
                                    glyphDy = dy;
                                    outerRules = rules;
                                    assertEquals(
                                            "gap=" + gap + " size=" + size + " ratio=" + ratio
                                                    + " angle=" + degrees + " dx=" + dx + " dy="
                                                    + dy + " rules=" + rules,
                                            0,
                                            run(gap, 2, true, 2, true, false, false)
                                                    .get(0)
                                                    .octaveShift());
                                }
    }

    @Test
    public void thickDiamondsAtMultipleSizesAndOffsetsRetainCounterEvidence() {
        List<String> failures = new ArrayList<>();
        for (int gap : new int[] {12, 16, 20, 24})
            for (float size : new float[] {.5f, .6f, .7f, .8f})
                for (int dx : new int[] {-2, 0, 2})
                    for (int dy : new int[] {-2, 0, 2})
                        for (boolean rules : new boolean[] {false, true}) {
                            glyphScale = size;
                            glyphDx = dx;
                            glyphDy = dy;
                            outerRules = rules;
                            if (run(gap, 2, false, 2, true, false, false).get(0).octaveShift() != 2)
                                failures.add(
                                        "gap=" + gap + " size=" + size + " dx=" + dx + " dy=" + dy
                                                + " rules=" + rules);
                        }
        assertTrue(failures.toString(), failures.isEmpty());
    }

    @Test
    public void counterCannotReplaceSemanticRhythmOrConnectedStemEvidence() {
        for (int gap : new int[] {16, 20, 24}) {
            assertEquals(0, run(gap, 2, false, 2, false, false, false).get(0).octaveShift());
            assertEquals(0, run(gap, 2, false, 1, true, false, false).get(0).octaveShift());
            assertEquals(0, run(gap, 2, false, 2, true, true, false).get(0).octaveShift());
        }
    }
}
