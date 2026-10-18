// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete five-rule/ledger chains; no score raster or production predicate copy. */
public class CompleteLedgerTenutoOwnershipTest {
    private static final int W = 1000, H = 400, GAP = 16, X = 260;

    private static void box(
            byte[] gray, byte[] labels, int l, int t, int r, int b, int tone, int role) {
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                int p = y * W + x;
                gray[p] = (byte) Math.min(gray[p] & 255, tone);
                labels[p] = (byte) role;
            }
    }

    private static int mark(boolean aboveStaff) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        int head = aboveStaff ? 208 : 176, between = aboveStaff ? 224 : 160;
        int first = aboveStaff ? 240 : 80;
        for (int n = 0; n < 5; n++)
            box(
                    gray,
                    labels,
                    80,
                    first + n * GAP,
                    620,
                    first + n * GAP,
                    200,
                    OmrMeasurePostProcessor.STAFF);
        // The note owns its own ledger and EVERY intervening ledger before the nearest staff line.
        box(gray, labels, X - 10, head - 1, X + 10, head + 1, 0, OmrMeasurePostProcessor.SYMBOL);
        box(
                gray,
                labels,
                X - 10,
                between - 1,
                X + 10,
                between + 1,
                0,
                OmrMeasurePostProcessor.SYMBOL);
        for (int dy = -6; dy <= 6; dy++)
            for (int dx = -9; dx <= 9; dx++)
                if (dx * dx / 81.0 + dy * dy / 36.0 <= 1)
                    box(
                            gray,
                            labels,
                            X + dx,
                            head + dy,
                            X + dx,
                            head + dy,
                            0,
                            OmrMeasurePostProcessor.NOTEHEAD);
        // Shaft points away from the intervening ledger; it does not join that separate glyph.
        box(
                gray,
                labels,
                X + 9,
                aboveStaff ? head - 40 : head,
                X + 9,
                aboveStaff ? head : head + 40,
                0,
                OmrMeasurePostProcessor.STEM_OR_REST);
        byte[] g = gray.clone(), l = labels.clone();
        int result =
                NoteArticulationDetector.detect(
                        labels,
                        gray,
                        W,
                        H,
                        List.of(new NoteArticulationDetector.Anchor(X, head, GAP, 0)))[0];
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
        return result;
    }

    @Test
    public void completeUpperAndLowerChainsKeepInterveningLedgerRole() {
        assertEquals(0, mark(true) & NoteArticulation.TENUTO);
        assertEquals(0, mark(false) & NoteArticulation.TENUTO);
    }
}
