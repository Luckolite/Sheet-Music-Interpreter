// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original semantic staff clipped before a head, with separately faded printed rules. */
public class PaleClosingExtentTest {
    static final int W = 400, H = 240;

    static class Page {
        byte[] labels = new byte[W * H], gray = new byte[W * H];

        Page(int kind) {
            Arrays.fill(gray, (byte) 250);
            for (int line = 0; line < 5; line++) {
                int row = 80 + line * 16;
                for (int x = 20; x <= 220; x++) {
                    labels[row * W + x] = 4;
                    gray[row * W + x] = 80;
                }
                if (kind != 1 && !(kind == 2 && line >= 3))
                    for (int x = 221; x <= 330; x++) {
                        if (kind == 4 && x >= 230 && x <= 255) continue;
                        for (int y = kind == 3 ? row - 6 : row;
                                y <= (kind == 3 ? row + 6 : row);
                                y++) gray[y * W + x] = (byte) 200;
                    }
            }
            head(100, 112);
            head(kind == 4 ? 280 : 230, 64);
        }

        void head(int cx, int cy) {
            for (int y = cy - 6; y <= cy + 6; y++)
                for (int x = cx - 8; x <= cx + 8; x++)
                    if (Math.pow((x - cx) / 8d, 2) + Math.pow((y - cy) / 6d, 2) <= 1) {
                        labels[y * W + x] = 2;
                        gray[y * W + x] = 100;
                    }
            for (int y = cy; y <= cy + 44; y++) {
                if (labels[y * W + cx - 8] != 2) labels[y * W + cx - 8] = 1;
                gray[y * W + cx - 8] = 100;
            }
            if (cy == 64) for (int x = cx - 13; x <= cx + 13; x++) gray[cy * W + x] = 100;
        }

        boolean target() {
            var m = OmrMeasurePostProcessor.process(labels, gray, W, H);
            return OmrScoreInterpreter.analyze(labels, gray, W, H, m).notes().stream()
                    .anyMatch(n -> n.staffStep() == 10);
        }
    }

    @Test
    public void independentlyFadedFiveRulesRetainFinalHead() {
        assertTrue(new Page(0).target());
    }

    @Test
    public void absentContinuationCannotExtendSemanticStaff() {
        assertFalse(new Page(1).target());
    }

    @Test
    public void threeFadedRulesCannotProveContinuation() {
        assertFalse(new Page(2).target());
    }

    @Test
    public void broadBandsCannotProveContinuation() {
        assertFalse(new Page(3).target());
    }

    @Test
    public void longBlankCannotBridgeToDistantHead() {
        assertFalse(new Page(4).target());
    }

    @Test
    public void sourcePixelsRemainUnchanged() {
        var p = new Page(0);
        byte[] l = p.labels.clone(), g = p.gray.clone();
        p.target();
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }
}
