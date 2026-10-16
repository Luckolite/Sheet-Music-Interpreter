// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original touching ellipses with independently drawn shafts. */
public class TouchingRepeatedPairTest {
    static RepeatedHeadRunTest.Page pair(boolean first, boolean second, boolean down) {
        var p = new RepeatedHeadRunTest.Page();
        for (int i = 0; i < 2; i++) {
            int x = 100 + i * 20;
            if (i == 0 ? first : second) p.rect(down ? x - 10 : x + 10, down ? 136 : 50, 2, 87, 1);
            p.oval(x);
        }
        return p;
    }

    @Test
    public void twoUpStemHeadsAreSeparate() {
        assertEquals(2, pair(true, true, false).notes().size());
    }

    @Test
    public void twoDownStemHeadsAreSeparate() {
        assertEquals(2, pair(true, true, true).notes().size());
    }

    @Test
    public void missingSecondShaftDoesNotSplit() {
        assertTrue(pair(true, false, false).notes().size() < 2);
    }

    @Test
    public void missingFirstShaftDoesNotSplit() {
        assertTrue(pair(false, true, false).notes().size() < 2);
    }

    @Test
    public void stemlessPairDoesNotSplit() {
        assertTrue(pair(false, false, false).notes().size() < 2);
    }

    @Test
    public void solidBlobDoesNotSplit() {
        var p = pair(true, true, false);
        p.rect(89, 128, 43, 17, 2);
        assertTrue(p.notes().size() < 2);
    }

    @Test
    public void retainedPitch() {
        var n = pair(true, true, true).notes();
        assertEquals(2, n.size());
        assertTrue(n.stream().allMatch(x -> x.staffStep() == 1));
    }

    @Test
    public void preservesInput() {
        var p = pair(true, true, true);
        byte[] a = p.labels.clone(), b = p.gray.clone();
        p.notes();
        assertArrayEquals(a, p.labels);
        assertArrayEquals(b, p.gray);
    }

    @Test
    public void recoveredPairRetainsSingleBeam() {
        var p = pair(true, true, false);
        p.rect(110, 50, 22, 5, 5);
        var n = p.notes();
        assertEquals(2, n.size());
        assertTrue(n.toString(), n.stream().allMatch(x -> x.beamCount() == 1));
    }

    @Test
    public void semanticOnlySecondShaftIsRejected() {
        var p = pair(true, true, false);
        for (int y = 50; y < 128; y++)
            for (int x = 130; x <= 131; x++) p.gray[y * p.w + x] = (byte) 255;
        assertTrue(p.notes().size() < 2);
    }
}
