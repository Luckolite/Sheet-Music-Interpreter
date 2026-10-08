// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original sharp drawings whose semantic masks retain only crossbar fragments. */
public class FragmentedSignatureSharpTest {
    private static final int W = JoinedSignatureSharpTest.W;

    private void fragments(JoinedSignatureSharpTest f, int top, int index, boolean both) {
        int[] dy = {0, 24, -8, 16};
        int x = 80 + index * 21, y = top + dy[index];
        for (int yy = y - 22; yy <= y + 22; yy++)
            for (int xx = x - 8; xx <= x + 7; xx++) f.labels[yy * W + xx] = 0;
        for (int cy : both ? new int[] {y - 5, y + 5} : new int[] {y - 5})
            for (int yy = cy - 1; yy <= cy + 1; yy++)
                for (int xx = x - 8; xx <= x + 7; xx++) f.labels[yy * W + xx] = 3;
    }

    private JoinedSignatureSharpTest damaged() {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 4, 0, false);
        fragments(f, 100, 0, true);
        fragments(f, 100, 1, true);
        return f;
    }

    @Test
    public void fragmentedSharpsStillCountInTheFirstHeader() {
        assertEquals(List.of(4), damaged().keys());
    }

    @Test
    public void severalFragmentsOfOneSharpCountOnlyOnce() {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 1, 0, false);
        fragments(f, 100, 0, true);
        assertEquals(List.of(1), f.keys());
    }

    @Test
    public void oneSurvivingCrossbarCanSeedACompletePrintedSharp() {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 1, 0, false);
        fragments(f, 100, 0, false);
        assertEquals(List.of(1), f.keys());
    }

    @Test
    public void aDamagedRepeatedHeaderKeepsThePrintedKey() {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 4, 0, false);
        f.row(310, 4, 0, false);
        fragments(f, 310, 0, true);
        fragments(f, 310, 1, true);
        assertEquals(List.of(4), f.keys());
    }

    @Test
    public void aRealKeyReductionStillSurvives() {
        var f = damaged();
        f.row(310, 2, 0, false);
        assertEquals(List.of(4, 2), f.keys());
    }

    @Test
    public void maskFragmentsWithoutPrintedSpinesCannotCreateASharp() {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 1, 0, false);
        fragments(f, 100, 0, true);
        for (int y = 78; y <= 122; y++)
            for (int x = 72; x <= 87; x++)
                f.gray[y * W + x] = f.labels[y * W + x] == 3 ? (byte) 0 : (byte) 255;
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void missingSourceInkCannotBeRecoveredFromFragments() {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 1, 0, false);
        fragments(f, 100, 0, true);
        for (int y = 78; y <= 122; y++)
            for (int x = 72; x <= 87; x++) f.gray[y * W + x] = (byte) 255;
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void completePrintedSharpSurvivesTinySemanticMask() {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 1, 0, false);
        for (int y = 78; y <= 122; y++) for (int x = 72; x <= 87; x++) f.labels[y * W + x] = 0;
        for (int y = 94; y <= 96; y++) for (int x = 75; x <= 77; x++) f.labels[y * W + x] = 3;
        assertEquals(List.of(1), f.keys());
    }

    @Test
    public void tinySemanticSpecksDoNotSeedAnExtraKeyGlyph() {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 1, 0, false);
        for (int y = 78; y <= 122; y++) {
            for (int x = 72; x <= 87; x++) {
                f.labels[y * W + x] = 0;
                f.gray[y * W + x] = (byte) 255;
            }
        }
        for (int y = 94; y <= 96; y++) {
            for (int x = 75; x <= 77; x++) {
                f.labels[y * W + x] = 3;
                f.gray[y * W + x] = 0;
            }
        }
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void reconstructionPreservesBothInputImages() {
        var f = damaged();
        var labels = f.labels.clone();
        var gray = f.gray.clone();
        f.keys();
        assertArrayEquals(labels, f.labels);
        assertArrayEquals(gray, f.gray);
    }
}
