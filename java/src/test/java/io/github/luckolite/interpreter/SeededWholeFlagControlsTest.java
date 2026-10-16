// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original flag/stem arrangements, using bundled SIL-OFL Bravura glyphs. */
public class SeededWholeFlagControlsTest {
    void reject(int code, boolean leftStem) throws Exception {
        var p = new CrossedWholeHeadTest.Page();
        int[] box = p.glyph(code, 120, 72);
        p.rect(leftStem ? box[0] : box[2], Math.max(0, box[1] - 12), 2, box[3] - box[1] + 25, 1);
        for (int y = box[1]; y <= box[3]; y += 2)
            for (int x = box[0]; x <= box[2]; x += 2)
                if ((p.gray[y * 400 + x] & 255) >= 205)
                    assertNull(
                            "flag seed " + x + "," + y,
                            SeededWholeHeadRecovery.recover(p.gray, 400, 240, 16, x, y));
    }

    @Test
    public void upFlagWithLeftStemCannotBecomeWhole() throws Exception {
        reject(0xe240, true);
    }

    @Test
    public void downFlagWithRightStemCannotBecomeWhole() throws Exception {
        reject(0xe241, false);
    }
}
