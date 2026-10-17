// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original ellipses and two ledger rules with independently faded inner ink. */
public class PaleInnerLedgerContrastTest {
    private RemoteLedgerEvidenceTest.Page page(boolean above, int kind) {
        var p = new RemoteLedgerEvidenceTest.Page(above, false, true, true, false);
        Arrays.fill(p.labels, (byte) 0);
        Arrays.fill(p.gray, (byte) 250);
        for (int y = 128; y <= 192; y += 16) p.rule(14, 465, y, 4);
        int cy = above ? 96 : 224, inner = above ? 112 : 208;
        p.head(105, cy, 11, 7, false);
        for (int y = cy - 7; y <= cy + 7; y++)
            for (int x = 94; x <= 116; x++)
                if (p.labels[y * 480 + x] == 2) p.gray[y * 480 + x] = (byte) 120;
        for (int x = 86; x <= 124; x++) {
            if (p.labels[cy * 480 + x] != 2) p.labels[cy * 480 + x] = 4;
            p.gray[cy * 480 + x] = (byte) 150;
        }
        int shaft = above ? 94 : 116;
        for (int y = Math.min(cy, above ? 144 : 176); y <= Math.max(cy, above ? 144 : 176); y++) {
            if (p.labels[y * 480 + shaft] != 2) p.labels[y * 480 + shaft] = 1;
            if ((p.gray[y * 480 + shaft] & 255) > 232) p.gray[y * 480 + shaft] = (byte) 232;
        }
        if (kind != 1) {
            int row = kind == 5 ? inner + (above ? 8 : -8) : inner;
            int top = kind == 4 ? row - 7 : row, bottom = kind == 4 ? row + 7 : row;
            for (int y = top; y <= bottom; y++)
                for (int x = 86; x <= 124; x++) {
                    if (kind == 2 && x > 105 || kind == 3 && x >= 102 && x <= 109) continue;
                    p.labels[y * 480 + x] = 4;
                    p.gray[y * 480 + x] = (byte) 215;
                }
        }
        if (kind == 6)
            for (int y = inner - 8; y <= inner + 8; y++)
                for (int x = 102; x <= 107; x++) p.gray[y * 480 + x] = (byte) 120;
        p.head(300, 176, 11, 7, false);
        p.stem(311, 176, 128);
        return p;
    }

    @Test
    public void upperSecondLedgerKeepsItsPaleInnerRule() {
        var n = page(true, 0).remote();
        assertNotNull(n);
        assertEquals(12, n.staffStep());
    }

    @Test
    public void lowerSecondLedgerKeepsItsPaleInnerRule() {
        var n = page(false, 0).remote();
        assertNotNull(n);
        assertEquals(-4, n.staffStep());
    }

    @Test
    public void missingInnerRuleCannotPromoteHead() {
        assertNull(page(true, 1).remote());
    }

    @Test
    public void oneSidedRuleCannotProveLedger() {
        assertNull(page(true, 2).remote());
    }

    @Test
    public void disconnectedRuleCannotProveLedger() {
        assertNull(page(true, 3).remote());
    }

    @Test
    public void broadPaleBandCannotProveLedger() {
        assertNull(page(true, 4).remote());
    }

    @Test
    public void wrongPhaseRuleCannotProveLedger() {
        assertNull(page(true, 5).remote());
    }

    @Test
    public void broadVerticalCrossingCannotProveLedger() {
        assertNull(page(true, 6).remote());
    }

    @Test
    public void inputsArePreserved() {
        var p = page(true, 0);
        byte[] g = p.gray.clone(), l = p.labels.clone();
        p.remote();
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.labels);
    }
}
