// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original semantic clef fragments beside a three-flat header, without source flat ink. */
public class OverlappingClefFlatTest {
    static HeaderAccidentalOwnershipTest.Page page(int x, int cy, boolean printed) {
        var p = new HeaderAccidentalOwnershipTest.Page(true, 185);
        p.flat(122, 140);
        for (int y = 108; y <= 174; y++)
            for (int xx = 48; xx <= 51; xx++) p.labels[y * 420 + xx] = 0;
        p.flat(x, cy);
        if (!printed)
            for (int y = cy - 28; y <= cy + 8; y++)
                for (int xx = x; xx <= x + 14; xx++) p.gray[y * 420 + xx] = (byte) 255;
        return p;
    }

    static List<ScoreKeyChange> read(HeaderAccidentalOwnershipTest.Page p, boolean raw) {
        return OmrScoreInterpreter.analyze(
                        p.labels,
                        raw ? p.gray : null,
                        420,
                        260,
                        List.of(new MeasureRegion(85f / 420, 1, .1f, .9f)))
                .keyChanges();
    }

    @Test
    public void overlappingUnprintedClefFragmentsDoNotAddFlat() {
        for (int x : new int[] {49, 50, 51})
            for (int cy : new int[] {140, 146, 152, 158}) {
                var p = page(x, cy, false);
                var l = p.labels.clone();
                var g = p.gray.clone();
                assertEquals(
                        "x=" + x + " y=" + cy, List.of(new ScoreKeyChange(0, -3)), read(p, true));
                assertArrayEquals(l, p.labels);
                assertArrayEquals(g, p.gray);
            }
    }

    @Test
    public void separatePrintedFlatIsNotDiscarded() {
        for (int cy : new int[] {140, 146, 152, 158})
            assertEquals(List.of(new ScoreKeyChange(0, -4)), read(page(52, cy, true), true));
    }

    @Test
    public void absentSourceImageCannotDisproveSemanticFlat() {
        assertEquals(List.of(new ScoreKeyChange(0, -4)), read(page(50, 146, false), false));
    }
}
