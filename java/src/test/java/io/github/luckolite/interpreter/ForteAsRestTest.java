// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ForteAsRestTest {
    private ForteHookHeadTest letter(boolean cross, boolean foot) {
        var page = new ForteHookHeadTest();
        page.letter(cross, foot);
        return page;
    }

    private boolean forte(ForteHookHeadTest page) {
        return ForteRestGuard.owns(page.gray, 420, 300, 190, 214, 179, 216, 16);
    }

    @Test
    public void completeGlyphHasSeparateCrossStrokeAndFoot() {
        assertTrue(forte(letter(true, true)));
    }

    @Test
    public void missingCrossStrokeDoesNotExcludeRest() {
        assertFalse(forte(letter(false, true)));
    }

    @Test
    public void missingLowerFootDoesNotExcludeRest() {
        assertFalse(forte(letter(true, false)));
    }

    @Test
    public void loneForteCannotAddSilenceToHeldVoice() {
        var page = letter(true, true);
        var held = new ScoreNoteEvent(0, .1f, 4, 0, 1, .4f, false, 0, 0, 2, 4);
        var rests =
                SixteenthRestDetector.detect(
                        page.gray,
                        420,
                        300,
                        List.of(new MeasureRegion(0, 1, .1f, .98f)),
                        List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                        List.of(held));
        assertTrue(rests.toString(), rests.isEmpty());
    }

    @Test
    public void guardPreservesSourcePixels() {
        var page = letter(true, true);
        var copy = page.gray.clone();
        forte(page);
        assertArrayEquals(copy, page.gray);
    }
}
