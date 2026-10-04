// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two beamed groups with a simultaneous rest in another voice. */
public class QuarterRestAttackOwnershipTest {
    private boolean separate(
            double duration,
            float position,
            int staff,
            int direction,
            boolean bridge,
            int targetBeams,
            boolean sameGroup,
            int metadata) {
        return separate(
                duration,
                position,
                staff,
                direction,
                bridge,
                targetBeams,
                sameGroup,
                metadata,
                false);
    }

    private boolean separate(
            double duration,
            float position,
            int staff,
            int direction,
            boolean bridge,
            int targetBeams,
            boolean sameGroup,
            int metadata,
            boolean down) {
        int w = 600, h = 280;
        float gap = 16;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 240);
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int x : new int[] {100, 160, 220, 280}) {
            for (int y = 144; y <= 156; y++)
                for (int dx = -8; dx <= 8; dx++)
                    if (dx * dx / 64. + (y - 150) * (y - 150) / 36. <= 1) gray[y * w + x + dx] = 0;
            for (int y = down ? 150 : 80; y <= (down ? 220 : 150); y++)
                for (int dx = down ? -10 : 8; dx <= (down ? -8 : 10); dx++)
                    gray[y * w + x + dx] = 0;
            int d = x >= 220 ? direction : metadata;
            notes.add(
                    new ScoreNoteEvent(
                                    0,
                                    x / (float) w,
                                    0,
                                    x >= 220 ? staff : 0,
                                    2,
                                    150 / (float) h,
                                    false,
                                    0,
                                    x <= 160 ? targetBeams : 1,
                                    2,
                                    0,
                                    1)
                            .withStemDirection(d));
        }
        if (bridge)
            for (int y = down ? 216 : 78; y <= (down ? 222 : 84); y++)
                for (int x = down ? 91 : 109; x <= (down ? 271 : 289); x++)
                    if (down ? (x <= 151 || x >= 211) : (x <= 169 || x >= 229)) gray[y * w + x] = 0;
        var target = notes.get(sameGroup ? 0 : 1);
        var rest = new ScoreRestEvent(0, position, 70 / (float) h, 46 / (float) h, 0, 2, duration);
        return OmrScoreInterpreter.restIsSeparateAttack(
                rest, target, notes, new MeasureRegion(0, 1, 0, 1), gray, w, h, gap);
    }

    @Test
    public void quarterRestAtNextBeamedAttackCannotPauseTheVoice() {
        assertFalse(separate(1, 220f / 600, 0, 1, true, 1, false, 1));
    }

    @Test
    public void dottedQuarterRestAtNextAttackAlsoStaysSeparate() {
        assertFalse(separate(1.5, 220f / 600, 0, 1, true, 1, false, 1));
    }

    @Test
    public void oneContinuousGroupKeepsItsAttackingColumn() {
        assertFalse(separate(1, 160f / 600, 0, 1, true, 1, true, 1));
    }

    @Test
    public void earlyUnassignedDirectionsUseAttachedPrintedShafts() {
        assertFalse(separate(1, 220f / 600, 0, 0, true, 1, false, 0));
    }

    @Test
    public void downwardGroupsKeepTheirAttackingColumns() {
        assertFalse(separate(1, 220f / 600, 0, -1, true, 1, false, -1, true));
    }

    @Test
    public void earlyDownwardShaftsAlsoUsePhysicalEvidence() {
        assertFalse(separate(1, 220f / 600, 0, 0, true, 1, false, 0, true));
    }

    @Test
    public void anotherStaffCannotTakeTheRest() {
        assertTrue(separate(1, 220f / 600, 1, 1, true, 1, false, 1));
    }

    @Test
    public void oppositeVoiceCannotTakeTheRest() {
        assertTrue(separate(1, 220f / 600, 0, -1, true, 1, false, 1));
    }

    @Test
    public void disconnectedShaftsDoNotProveAnAttackingVoice() {
        assertTrue(separate(1, 220f / 600, 0, 1, false, 1, false, 1));
    }

    @Test
    public void aRestBetweenAttackColumnsRemainsAvailable() {
        assertTrue(separate(1, 190f / 600, 0, 1, true, 1, false, 1));
    }

    @Test
    public void anEighthRestKeepsItsOwnExistingPath() {
        assertTrue(separate(.5, 220f / 600, 0, 1, true, 1, false, 1));
    }

    @Test
    public void anUnbeamedQuarterDoesNotClaimTheMovingRest() {
        assertTrue(separate(1, 220f / 600, 0, 1, true, 0, false, 1));
    }
}
