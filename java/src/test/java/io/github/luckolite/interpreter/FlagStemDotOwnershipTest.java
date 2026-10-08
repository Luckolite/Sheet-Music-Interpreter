// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original continuous rooted flags and independently owned neighbouring marks. */
public class FlagStemDotOwnershipTest {
    static final int W = 260, H = 210, X = 103;
    static final float G = 16;

    record Raster(byte[] labels, byte[] gray, float headY, int[] body) {}

    static Raster draw(boolean up, boolean flag, boolean disconnected, boolean independent) {
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        Arrays.fill(gray, (byte) 160);
        int head = up ? 150 : 60, end = up ? 80 : 130, dir = up ? 1 : -1;
        for (int y = Math.min(head, end) + 2; y <= Math.max(head, end) - 2; y++)
            for (int x = X - 2; x <= X + 2; x++) {
                gray[y * W + x] = 20;
                labels[y * W + x] = OmrMeasurePostProcessor.STEM_OR_REST;
            }
        for (int y = Math.min(head, end); y <= Math.max(head, end); y++) {
            gray[y * W + X] = 20;
            labels[y * W + X] = OmrMeasurePostProcessor.STEM_OR_REST;
        }
        if (flag)
            for (int d = 0; d <= 34; d++) {
                int x = X + Math.round(20 * (float) Math.sin(Math.PI * d / 34)), y = end + dir * d;
                for (int dx = 0; dx < 3; dx++) {
                    gray[y * W + x + dx] = 20;
                    labels[y * W + x + dx] = OmrMeasurePostProcessor.STEM_OR_REST;
                }
            }
        // A complete flag has a filled root shoulder adjoining the returning hook.
        if (flag)
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
                    gray[y * W + x] = 20;
                    labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                }
            }
        int by = up ? 128 : 78, bx = independent ? X - 9 : X - 2;
        int[] body = new int[30];
        int count = 0;
        for (int y = by; y < by + 6; y++)
            for (int x = bx; x < bx + 5; x++) {
                int at = y * W + x;
                body[count++] = at;
                if (independent) {
                    gray[at] = 20;
                    labels[at] = OmrMeasurePostProcessor.SYMBOL;
                }
            }
        if (disconnected)
            for (int y = up ? 138 : 69; y < (up ? 143 : 74); y++)
                for (int x = X - 3; x <= X + 3; x++) {
                    gray[y * W + x] = (byte) 160;
                    labels[y * W + x] = 0;
                }
        return new Raster(labels, gray, head, body);
    }

    static boolean owned(Raster r) {
        return OmrScoreInterpreter.flagStemOwnsDot(r.labels, r.gray, W, H, X, r.headY, G, r.body);
    }

    @Test
    public void anUpFlagOwnsItsContinuousInteriorShaftPocket() {
        assertTrue(owned(draw(true, true, false, false)));
    }

    @Test
    public void aDownFlagOwnsItsContinuousInteriorShaftPocket() {
        assertTrue(owned(draw(false, true, false, false)));
    }

    @Test
    public void aPlainShaftWithoutTheCompleteFlagIsInsufficient() {
        assertFalse(owned(draw(true, false, false, false)));
    }

    @Test
    public void aSeparateNeighbouringDotRetainsIndependentOwnership() {
        assertFalse(owned(draw(true, true, false, true)));
    }

    @Test
    public void aBrokenShaftCannotOwnTheCandidate() {
        assertFalse(owned(draw(true, true, true, false)));
    }

    @Test
    public void anUnacceptedSemanticHeadCannotOwnTheCandidate() {
        var r = draw(true, true, false, false);
        for (int i = 0; i < r.labels.length; i++)
            if (r.labels[i] == OmrMeasurePostProcessor.NOTEHEAD) r.labels[i] = 0;
        assertFalse(owned(r));
    }

    @Test
    public void aNearbyCaretOutsideTheObservedShaftIsInsufficient() {
        var r = draw(true, true, false, false);
        int[] p = {110 * W + 94, 111 * W + 93, 111 * W + 95, 112 * W + 92, 112 * W + 96};
        assertFalse(OmrScoreInterpreter.flagStemOwnsDot(r.labels, r.gray, W, H, X, r.headY, G, p));
    }

    @Test
    public void ownershipPreservesOriginalPixels() {
        var r = draw(true, true, false, false);
        byte[] a = r.gray.clone(), b = r.labels.clone();
        owned(r);
        assertArrayEquals(a, r.gray);
        assertArrayEquals(b, r.labels);
    }

    @Test
    public void aCompleteRawFlagStillOwnsAClefMislabeledShaftPocket() {
        var r = draw(true, true, false, false);
        for (int p : r.body) r.labels[p] = OmrMeasurePostProcessor.CLEF_OR_KEY;
        assertTrue(owned(r));
    }

    @Test
    public void anIndependentKeyFragmentBesideAFlagIsNotOwned() {
        var r = draw(true, true, false, true);
        for (int p : r.body) r.labels[p] = OmrMeasurePostProcessor.CLEF_OR_KEY;
        assertFalse(owned(r));
    }

    @Test
    public void aKeyStemWithoutACompleteReturningFlagIsInsufficient() {
        var r = draw(true, false, false, false);
        for (int p : r.body) r.labels[p] = OmrMeasurePostProcessor.CLEF_OR_KEY;
        assertFalse(owned(r));
    }

    @Test
    public void anIndependentSymbolInsideTheCorridorIsInsufficient() {
        var r = draw(true, true, false, false);
        for (int p : r.body) r.labels[p] = OmrMeasurePostProcessor.SYMBOL;
        assertFalse(owned(r));
    }

    @Test
    public void anotherHeadBodyInsideTheCorridorIsInsufficient() {
        var r = draw(true, true, false, false);
        for (int p : r.body) r.labels[p] = OmrMeasurePostProcessor.NOTEHEAD;
        assertFalse(owned(r));
    }

    @Test
    public void aStaffStripeInsideTheCorridorIsInsufficient() {
        var r = draw(true, true, false, false);
        for (int p : r.body) r.labels[p] = OmrMeasurePostProcessor.STAFF;
        assertFalse(owned(r));
    }
}
