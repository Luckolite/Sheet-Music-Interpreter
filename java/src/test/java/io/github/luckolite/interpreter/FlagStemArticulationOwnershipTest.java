// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original dark shaft pockets with separately printed neighbouring dots. */
public class FlagStemArticulationOwnershipTest {
    static final int W = 1280, H = 1280, X = 480;
    static final float G = 16;

    record Raster(byte[] labels, byte[] gray, int head) {}

    static Raster draw(boolean up, boolean separateDot) {
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        Arrays.fill(gray, (byte) 160);
        int head = up ? 500 : 430, end = up ? 430 : 500, dir = up ? 1 : -1;
        for (int y = Math.min(head, end) + 2; y <= Math.max(head, end) - 2; y++)
            for (int x = X - 2; x <= X + 2; x++) {
                int at = y * W + x;
                gray[at] = 110;
                labels[at] = OmrMeasurePostProcessor.STEM_OR_REST;
            }
        for (int y = Math.min(head, end); y <= Math.max(head, end); y++) {
            int at = y * W + X;
            gray[at] = 110;
            labels[at] = OmrMeasurePostProcessor.STEM_OR_REST;
        }
        for (int d = 0; d <= 34; d++) {
            int x = X + Math.round(20 * (float) Math.sin(Math.PI * d / 34)), y = end + dir * d;
            for (int dx = 0; dx < 3; dx++) {
                int at = y * W + x + dx;
                gray[at] = 20;
                labels[at] = OmrMeasurePostProcessor.STEM_OR_REST;
            }
        }
        for (int d = 1; d <= 5; d++)
            for (int x = X; x <= X + 5; x++) {
                int at = (end + dir * d) * W + x;
                gray[at] = 20;
                labels[at] = OmrMeasurePostProcessor.STEM_OR_REST;
            }
        for (int y = head - 4; y <= head + 4; y++)
            for (int x = X - 4; x <= X + 4; x++) {
                double a = (x - X) / 4., b = (y - head) / 4.;
                if (a * a + b * b <= 1) {
                    int at = y * W + x;
                    gray[at] = 20;
                    labels[at] = OmrMeasurePostProcessor.NOTEHEAD;
                }
            }
        int pocket = up ? 478 : 446;
        for (int y = pocket; y < pocket + 6; y++)
            for (int x = X - 2; x <= X + 2; x++) gray[y * W + x] = 20;
        int dot = up ? head + 19 : head - 22;
        if (separateDot)
            for (int y = dot; y < dot + 5; y++)
                for (int x = X - 2; x <= X + 2; x++) {
                    int at = y * W + x;
                    gray[at] = 20;
                    labels[at] = OmrMeasurePostProcessor.SYMBOL;
                }
        return new Raster(labels, gray, head);
    }

    static int marks(Raster r) {
        return NoteArticulationDetector.detect(
                r.labels,
                r.gray,
                W,
                H,
                List.of(new NoteArticulationDetector.Anchor(X, r.head, G, 0)))[0];
    }

    @Test
    public void anUpFlagShaftPocketCannotAddStaccato() {
        assertEquals(0, marks(draw(true, false)));
    }

    @Test
    public void aDownFlagShaftPocketCannotAddStaccato() {
        assertEquals(0, marks(draw(false, false)));
    }

    @Test
    public void aSeparateDotBesideAFlagRetainsStaccato() {
        assertEquals(NoteArticulation.STACCATO, marks(draw(true, true)));
    }

    @Test
    public void originalSourcePlanesRemainUnchanged() {
        var r = draw(true, true);
        var gray = r.gray.clone();
        var labels = r.labels.clone();
        marks(r);
        assertArrayEquals(gray, r.gray);
        assertArrayEquals(labels, r.labels);
    }

    static Raster mislabeled(boolean up, boolean independent) {
        var r = draw(up, independent);
        int pocket = up ? 478 : 446;
        for (int y = pocket; y < pocket + 6; y++)
            for (int x = X - 2; x <= X + 2; x++)
                r.labels[y * W + x] = OmrMeasurePostProcessor.CLEF_OR_KEY;
        return r;
    }

    @Test
    public void anUpFlagOwnsItsKeyMislabeledShaftPocket() {
        assertEquals(0, marks(mislabeled(true, false)));
    }

    @Test
    public void aDownFlagOwnsItsKeyMislabeledShaftPocket() {
        assertEquals(0, marks(mislabeled(false, false)));
    }

    @Test
    public void anIndependentDotSurvivesTheMislabeledShaftRepair() {
        assertEquals(NoteArticulation.STACCATO, marks(mislabeled(true, true)));
    }

    @Test
    public void aTrueRoundDotProtrudingBeyondTheShaftRetainsStaccato() {
        var r = mislabeled(true, false);
        int cx = X + 1, cy = 480;
        var points = new java.util.ArrayList<Integer>();
        for (int y = cy - 4; y <= cy + 4; y++)
            for (int x = cx - 4; x <= cx + 4; x++)
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= 16) {
                    int at = y * W + x;
                    r.gray[at] = 20;
                    r.labels[at] = OmrMeasurePostProcessor.CLEF_OR_KEY;
                    points.add(at);
                }
        int[] body = points.stream().mapToInt(Integer::intValue).toArray();
        assertFalse(
                OmrScoreInterpreter.flagStemOwnsDot(r.labels, r.gray, W, H, X, r.head, G, body));
        assertEquals(NoteArticulation.STACCATO, marks(r));
    }
}
