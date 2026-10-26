// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic line-ending courtesy signatures, with no score-derived pixels. */
public final class CourtesyKeySignatureTest {
    private JoinedSignatureSharpTest page(boolean doubleBar, boolean nextRow) {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 0, 0, false);
        var m = f.measures.get(0);
        f.measures.set(
                0,
                new MeasureRegion(
                        m.left(), 500f / JoinedSignatureSharpTest.W, m.top(), m.bottom()));
        for (int y = 100; y <= 164; y++)
            for (int x : doubleBar ? new int[] {490, 491, 497, 498} : new int[] {497, 498})
                f.ink(x, y, 1);
        if (nextRow) f.row(310, 0, 0, false);
        return f;
    }

    private void natural(JoinedSignatureSharpTest f, int x, int y) {
        for (int yy = y - 22; yy <= y + 9; yy++)
            for (int xx = x - 5; xx <= x - 4; xx++) f.ink(xx, yy, 3);
        for (int yy = y - 9; yy <= y + 22; yy++)
            for (int xx = x + 4; xx <= x + 5; xx++) f.ink(xx, yy, 3);
        for (int cy : new int[] {y - 8, y + 8})
            for (int yy = cy - 1; yy <= cy + 1; yy++)
                for (int xx = x - 4; xx <= x + 4; xx++) f.ink(xx, yy, 3);
    }

    @Test
    public void completeCourtesySharpsApplyToNextRow() {
        var f = page(true, true);
        f.sharp(518, 100);
        f.sharp(539, 124);
        assertEquals(List.of(2), f.keys());
    }

    @Test
    public void courtesyEvidenceOutweighsDamagedRepeatedHeader() {
        var f = page(true, true);
        f.sharp(518, 100);
        f.sharp(539, 124);
        f.sharp(80, 310);
        assertEquals(List.of(2), f.keys());
    }

    @Test
    public void courtesyNaturalWithoutPriorRemainsUnqualified() {
        var f = page(true, true);
        natural(f, 518, 132);
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void singleBarDoesNotEstablishCourtesyContext() {
        var f = page(false, true);
        f.sharp(518, 100);
        f.sharp(539, 124);
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void wrongSharpPitchOrderIsNotAKey() {
        var f = page(true, true);
        f.sharp(518, 100);
        f.sharp(539, 108);
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void pageEndDoesNotMoveCourtesyIntoPreviousMusic() {
        var f = page(true, false);
        f.sharp(518, 100);
        f.sharp(539, 124);
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void missingCompleteSharpStrokesAreNotEnough() {
        var f = page(true, true);
        for (int y = 80; y < 125; y++) for (int x : new int[] {513, 514, 522, 523}) f.ink(x, y, 3);
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void noInputMaskIsChanged() {
        var f = page(true, true);
        f.sharp(518, 100);
        f.sharp(539, 124);
        var g = f.gray.clone();
        var l = f.labels.clone();
        f.keys();
        assertArrayEquals(g, f.gray);
        assertArrayEquals(l, f.labels);
    }
}
