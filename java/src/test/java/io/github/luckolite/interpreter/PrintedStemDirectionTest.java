// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedStemDirectionTest {
    private byte[] image(int direction, boolean broken, boolean rules) {
        byte[] gray = new byte[180 * 180];
        Arrays.fill(gray, (byte) 255);
        for (int y = 77; y <= 83; y++)
            for (int x = 75; x <= 85; x++)
                if ((x - 80) * (x - 80) / 25f + (y - 80) * (y - 80) / 9f <= 1)
                    gray[y * 180 + x] = 0;
        if (rules)
            for (int y : new int[] {60, 70, 80, 90, 100})
                for (int x = 10; x < 170; x++) gray[y * 180 + x] = 0;
        for (int sign : new int[] {-1, 1})
            if (direction == 0 || direction == sign) {
                int x = sign > 0 ? 86 : 74;
                for (int d = 0; d <= 35; d++)
                    if (!broken || d < 8 || d > 13) gray[(80 - sign * d) * 180 + x] = 0;
            }
        return gray;
    }

    @Test
    public void readsAttachedUpAndDownShaftsAcrossStaffRules() {
        for (boolean rules : new boolean[] {false, true}) {
            assertEquals(
                    1, PrintedStemDirection.detect(image(1, false, rules), 180, 180, 80, 80, 10));
            assertEquals(
                    -1, PrintedStemDirection.detect(image(-1, false, rules), 180, 180, 80, 80, 10));
        }
    }

    @Test
    public void ambiguousAndDisconnectedShaftsRemainUnknown() {
        assertEquals(0, PrintedStemDirection.detect(image(0, false, true), 180, 180, 80, 80, 10));
        assertEquals(0, PrintedStemDirection.detect(image(1, true, true), 180, 180, 80, 80, 10));
        assertEquals(0, PrintedStemDirection.detect(null, 180, 180, 80, 80, 10));
    }

    @Test
    public void noteCopiesKeepPrintedDirectionAndNonbinaryTuplets() {
        var n =
                new ScoreNoteEvent(0, .3f, 4, 0, 2, .5f, false, 0, 2)
                        .withStemDirection(-1)
                        .withTupletRatio(5, 3);
        var copies =
                List.of(
                        n.withArticulations(1),
                        n.withClef(24),
                        n.withOctaveShift(1),
                        n.withBoundaryTies(3),
                        n.withLeadingRest(1),
                        n.withCompactOpening(),
                        n.withCrossStaffBeam(),
                        n.withTupletRatio(5, 3));
        for (var copy : copies) {
            assertEquals(-1, copy.stemDirection());
            assertEquals(5, copy.tupletDivisor());
            assertEquals(3, copy.tupletNormalNotes());
        }
        assertEquals(0, new ScoreNoteEvent(0, .3f, 4, 0, 2, .5f, false).stemDirection());
    }
}
