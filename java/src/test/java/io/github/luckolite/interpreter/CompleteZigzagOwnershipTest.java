// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently drawn complete zigzag with a semantic oval at its right lobe. */
public class CompleteZigzagOwnershipTest {
    static final int W = 480, H = 280;
    final byte[] gray = new byte[W * H], labels = new byte[W * H];
    static final List<MeasureRegion> M = List.of(new MeasureRegion(.04f, .96f, .15f, .9f));

    CompleteZigzagOwnershipTest page(boolean owner) {
        Arrays.fill(gray, (byte) 255);
        for (int y = 120; y <= 200; y += 20) for (int x = 20; x < 460; x++) ink(x, y, 25, 4);
        if (owner) {
            oval(245, 123, 12, 7, true, 2);
            for (int y = 124; y <= 181; y++) ink(233, y, 25, 1);
        }
        return this;
    }

    void zigzag(boolean faded) {
        for (int x = 215; x <= 260; x++) {
            float cy =
                    x <= 230
                            ? 84 + (x - 215) * .8f
                            : x <= 245 ? 96 - (x - 230) * .8f : 84 + (x - 245) * .8f;
            int shade = faded && x % 6 < 2 ? 178 : 60;
            for (int y = Math.round(cy) - 2; y <= Math.round(cy) + 2; y++) ink(x, y, shade, 5);
            ink(x, Math.round(cy) - 3, 201, 5);
            ink(x, Math.round(cy) + 3, 201, 5);
        }
        oval(254, 91, 7, 4, false, 2);
    }

    void ink(int x, int y, int shade, int label) {
        gray[y * W + x] = (byte) shade;
        labels[y * W + x] = (byte) label;
    }

    void oval(int cx, int cy, int rx, int ry, boolean raw, int label) {
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++)
                if (Math.pow((x - cx) / (double) rx, 2) + Math.pow((y - cy) / (double) ry, 2)
                        <= 1) {
                    labels[y * W + x] = (byte) label;
                    if (raw) gray[y * W + x] = 35;
                }
    }

    boolean demoted() {
        return OmrScoreInterpreter.normalizeHeaderSymbols(labels, gray, W, H, M)[91 * W + 254] != 2;
    }

    @Test
    public void fadedCompleteGlyphOwnsRightLobeFragmentNearRealNote() {
        page(true);
        zigzag(true);
        assertTrue(demoted());
    }

    @Test
    public void darkCompleteGlyphOwnsRightLobeFragmentNearRealNote() {
        page(true);
        zigzag(false);
        assertTrue(demoted());
    }

    @Test
    public void realCloseFilledNoteIsPreserved() {
        page(true);
        oval(254, 91, 7, 4, true, 2);
        assertFalse(demoted());
    }

    @Test
    public void twoRealCloseHeadsArePreserved() {
        page(true);
        oval(235, 91, 7, 4, true, 2);
        oval(254, 91, 7, 4, true, 2);
        assertFalse(demoted());
    }

    @Test
    public void realStemmedGraceIsPreserved() {
        page(true);
        zigzag(true);
        for (int y = 51; y <= 91; y++) ink(261, y, 30, 1);
        assertFalse(demoted());
    }

    @Test
    public void partialZigzagIsPreserved() {
        page(true);
        zigzag(true);
        for (int y = 70; y < 110; y++)
            for (int x = 210; x < 245; x++) {
                gray[y * W + x] = (byte) 255;
                labels[y * W + x] = 0;
            }
        assertFalse(demoted());
    }

    @Test
    public void noIndependentPrincipalIsPreserved() {
        page(false);
        zigzag(true);
        assertFalse(demoted());
    }

    @Test
    public void noteLabelsCannotProvideSymbolAuthority() {
        page(true);
        zigzag(true);
        for (int i = 0; i < labels.length; i++) if (labels[i] == 5) labels[i] = 2;
        assertFalse(demoted());
    }

    @Test
    public void rawAndSemanticInputsStayReadOnly() {
        page(true);
        zigzag(true);
        var g = gray.clone();
        var l = labels.clone();
        demoted();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }
}
