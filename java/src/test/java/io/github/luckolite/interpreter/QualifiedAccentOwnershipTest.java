// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class QualifiedAccentOwnershipTest {
    static AccentedHeadRestOwnershipTest.Page filled() {
        var p = new AccentedHeadRestOwnershipTest.Page();
        for (int x = 132; x <= 139; x++) {
            double u = (x - 111) / 28.;
            for (int y = 108; y <= 124; y++)
                if (y >= 109 + 7 * u && y <= 123 - 7 * u) p.pixel(x, y, 40);
        }
        return p;
    }

    @Test
    public void publishedFilledTipGeometryHasCompleteOwnedShaft() {
        assertTrue(filled().owned());
    }

    @Test
    public void trueRasterEdgeClippingCannotOwn() {
        var p = filled();
        byte[] old = p.gray.clone();
        Arrays.fill(p.gray, (byte) 240);
        for (int y = 0; y < 240; y++)
            for (int x = 0; x < 145; x++) p.gray[y * 256 + x] = old[y * 256 + x + 111];
        var note =
                new ScoreNoteEvent(0, 15f / 256, 2, 0, 1, 185f / 240, false, 0, 0, 2, 2)
                        .withStemDirection(1);
        var runs = new ArrayList<int[]>();
        for (int[] r : p.runs) runs.add(new int[] {r[0], r[1] - 111, r[2] - 111});
        var notes =
                RestFigureOrigins.bind(
                        List.of(note),
                        Map.of(
                                note,
                                RestFigureOrigins.HeadOrigin.accepted(
                                        new Object(), p.gray, 256, 240, runs)));
        assertFalse(
                AccentedHeadRestOwnership.owns(
                        p.gray, 256, 240, notes, note, 0, 29, 108, 151, new boolean[44], 108, 16));
    }
}
