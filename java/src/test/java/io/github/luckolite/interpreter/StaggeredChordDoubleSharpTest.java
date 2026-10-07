// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric chord and staggered accidentals, without score pixels. */
public class StaggeredChordDoubleSharpTest extends TouchingChordAccidentalTest {
    void cross(int x, int y, boolean missingArm) {
        for (int yy = 0; yy < 18; yy++)
            for (int xx = 0; xx < 18; xx++)
                if ((Math.abs(xx - yy) <= 4 || Math.abs(xx + yy - 17) <= 4)
                        && !(missingArm && yy > 11))
                    rect(
                            x + xx,
                            y - 9 + yy,
                            x + xx,
                            y - 9 + yy,
                            yy >= 7 && yy <= 9 ? 1 : xx < 8 ? 3 : 5);
    }

    void chord(boolean companion, boolean missingArm) {
        cross(202, 136, missingArm);
        sharp(230, 120);
        head(250, 136);
        if (companion) head(250, 120);
    }

    @Test
    public void completeDistantCrossBelongsToLowerChordTone() {
        chord(true, false);
        var n = notes();
        assertEquals(2, n.size());
        assertEquals(ScoreNoteEvent.ACCIDENTAL_DOUBLE_SHARP, accidental(n, 136));
        assertEquals(ScoreNoteEvent.ACCIDENTAL_SHARP, accidental(n, 120));
    }

    @Test
    public void distantCrossRequiresTheCompanionChordTone() {
        chord(false, false);
        assertNotEquals(ScoreNoteEvent.ACCIDENTAL_DOUBLE_SHARP, accidental(notes(), 136));
    }

    @Test
    public void missingCrossArmsAreNotRecovered() {
        chord(true, true);
        assertNotEquals(ScoreNoteEvent.ACCIDENTAL_DOUBLE_SHARP, accidental(notes(), 136));
    }

    @Test
    public void distantRecoveryPreservesInputs() {
        chord(true, false);
        var l = labels.clone();
        var g = gray.clone();
        notes();
        assertArrayEquals(l, labels);
        assertArrayEquals(g, gray);
    }
}
