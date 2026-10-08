// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete carets with a pale arm, plus words, bows and ownership negatives. */
public class SoftCaretOwnershipTest {
    private static final int W = 1280, H = 1280;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    private void draw(int paper, boolean left, boolean right, boolean inverted, byte label) {
        Arrays.fill(gray, (byte) paper);
        Arrays.fill(labels, (byte) 0);
        for (int y = 218; y <= 237; y++) {
            int dx = Math.round((y - 218) * .38f), yy = inverted ? 455 - y : y;
            for (int thick = -1; thick <= 1; thick++) {
                if (left) {
                    int p = yy * W + 478 - dx + thick;
                    gray[p] = (byte) Math.round(paper * .82f);
                    labels[p] = label;
                }
                if (right) {
                    int p = yy * W + 478 + dx + thick;
                    gray[p] = 35;
                    labels[p] = label;
                }
            }
        }
    }

    private int mark(float x) {
        return NoteArticulationDetector.detect(
                labels, gray, W, H, List.of(new NoteArticulationDetector.Anchor(x, 268, 14, 0)))[0];
    }

    @Test
    public void lightGrayPaperPaleArmRetainsMarcato() {
        draw(230, true, true, false, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.MARCATO, mark(478));
    }

    @Test
    public void mediumPaperPaleArmRetainsMarcato() {
        draw(210, true, true, false, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.MARCATO, mark(478));
    }

    @Test
    public void shadedPaperPaleArmRetainsMarcato() {
        draw(180, true, true, false, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.MARCATO, mark(478));
    }

    @Test
    public void oneDarkArmDoesNotSupplyMarcato() {
        draw(230, false, true, false, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(0, mark(478) & NoteArticulation.MARCATO);
    }

    @Test
    public void onePaleArmDoesNotSupplyMarcato() {
        draw(230, true, false, false, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(0, mark(478) & NoteArticulation.MARCATO);
    }

    @Test
    public void aCrossbarMakesThePeakALetter() {
        draw(230, true, true, false, OmrMeasurePostProcessor.SYMBOL);
        for (int y = 229; y <= 231; y++)
            for (int x = 472; x <= 484; x++) {
                gray[y * W + x] = 35;
                labels[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
            }
        assertEquals(0, mark(478) & NoteArticulation.MARCATO);
    }

    @Test
    public void invertedBowDoesNotBecomeMarcato() {
        draw(230, true, true, true, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(0, mark(478) & NoteArticulation.MARCATO);
    }

    @Test
    public void notationOwnedCaretStaysNotation() {
        draw(230, true, true, false, OmrMeasurePostProcessor.CLEF_OR_KEY);
        assertEquals(0, mark(478));
    }

    @Test
    public void offAxisCaretStaysUnowned() {
        draw(230, true, true, false, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(0, mark(510));
    }

    @Test
    public void paleCaretAndIndependentDotBothSurvive() {
        draw(230, true, true, false, OmrMeasurePostProcessor.SYMBOL);
        for (int y = 246; y <= 250; y++)
            for (int x = 476; x <= 480; x++) {
                gray[y * W + x] = 35;
                labels[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
            }
        assertEquals(NoteArticulation.MARCATO | NoteArticulation.STACCATO, mark(478));
    }

    @Test
    public void emptyGrayPaperHasNoCaret() {
        Arrays.fill(gray, (byte) 230);
        assertEquals(0, mark(478));
    }

    @Test
    public void rawPlanesRemainImmutable() {
        draw(230, true, true, false, OmrMeasurePostProcessor.SYMBOL);
        byte[] a = gray.clone(), b = labels.clone();
        mark(478);
        assertArrayEquals(a, gray);
        assertArrayEquals(b, labels);
    }
}
