// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OriginalBlockSeparationPreservationTest {
    static OcrText full(int l, int t, int r, int b) {
        var v = ContextualCharacterOwnerTest.full(null, true, OcrText.Grouping.ORIGINAL_BLOCKS);
        var box = new OcrText.Box(l, t, r, b);
        var blocks = new ArrayList<>(v.blocks());
        blocks.add(
                new OcrText.Block(
                        "Music by Original Author",
                        box,
                        List.of(new OcrText.Line("Music by Original Author", box, List.of()))));
        return new OcrText(v.text() + "\nMusic by Original Author", blocks, v.grouping());
    }

    @Test
    public void differentColumnCueDoesNotVetoActualRole() throws Exception {
        assertEquals(1, ContextualCharacterOwnerTest.collect(full(330, 76, 490, 92)).size());
    }

    @Test
    public void distantUpperCueDoesNotVetoActualRole() throws Exception {
        assertEquals(1, ContextualCharacterOwnerTest.collect(full(130, 5, 280, 20)).size());
    }

    @Test
    public void distantLowerCueDoesNotVetoActualRole() throws Exception {
        assertEquals(1, ContextualCharacterOwnerTest.collect(full(130, 230, 280, 250)).size());
    }

    @Test
    public void independentOriginalBlocksNeedNoSyntheticParentMerge() throws Exception {
        assertEquals(
                1,
                ContextualCharacterOwnerTest.collect(
                                ContextualCharacterOwnerTest.full(
                                        null, true, OcrText.Grouping.ORIGINAL_BLOCKS))
                        .size());
    }
}
