// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Minimal standalone RED/GREEN fixture: one literal arm fades while geometry stays identical. */
public class DetachedCaretBodyOriginalRegressionTest {
    private int detect(int leftTone) {
        int w = 2048, h = 1600;
        byte[] raw = new byte[w * h], labels = new byte[raw.length];
        Arrays.fill(raw, (byte) 255);
        for (int x = 95; x <= 119; x++) {
            int y = 100 + Math.abs(x - 107);
            raw[y * w + x] = (byte) (x < 107 ? leftTone : 75);
            raw[(y + 1) * w + x] = (byte) (x < 107 ? leftTone : 75);
        }
        return NoteArticulationDetector.detect(
                labels, raw, w, h, List.of(new NoteArticulationDetector.Anchor(107, 160, 14, 0)))[
                0];
    }

    @Test
    public void originalDarkCaretIsRetained() {
        assertEquals(2, detect(75));
    }

    @Test
    public void completeFadedArmRetainsMarcato() {
        assertEquals(2, detect(195));
    }
}
