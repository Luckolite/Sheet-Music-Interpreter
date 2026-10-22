// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** A short accepted rest can be lowered inside the complete beam of its moving voice. */
public class BeamedRestVoiceTest {
    private static final int W = 440, H = 260;
    private static final MeasureRegion BAR = new MeasureRegion(0, 1, 0, 1);

    private static final class Scene {
        byte[] gray = new byte[W * H];
        List<ScoreNoteEvent> notes = new ArrayList<>();
        ScoreRestEvent rest = new ScoreRestEvent(0, 205f / W, 134f / H, 38f / H, 0, 1, .25);

        Scene() {
            Arrays.fill(gray, (byte) 255);
            for (int x : new int[] {165, 250}) {
                oval(x, 145);
                for (int y = 84; y <= 145; y++) {
                    gray[y * W + x + 8] = 0;
                    gray[y * W + x + 9] = 0;
                }
                notes.add(
                        new ScoreNoteEvent(
                                        0,
                                        x / (float) W,
                                        -1,
                                        0,
                                        1,
                                        145f / H,
                                        false,
                                        0,
                                        x == 165 ? 1 : 2)
                                .withStemDirection(1));
            }
            for (int x = 173; x <= 259; x++) for (int y = 84; y <= 88; y++) gray[y * W + x] = 0;
        }

        void oval(int cx, int cy) {
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = cx - 9; x <= cx + 9; x++)
                    if ((x - cx) * (x - cx) / 81d + (y - cy) * (y - cy) / 49d <= 1)
                        gray[y * W + x] = 0;
        }

        boolean owns() {
            byte[] before = gray.clone();
            boolean result = BeamedRestVoice.owns(rest, notes.get(0), notes, BAR, gray, W, H, 14);
            assertArrayEquals(before, gray);
            return result;
        }

        void mirror() {
            byte[] next = new byte[gray.length];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) next[(H - 1 - y) * W + W - 1 - x] = gray[y * W + x];
            gray = next;
            for (int i = 0; i < notes.size(); i++) {
                var n = notes.get(i);
                notes.set(
                        i,
                        new ScoreNoteEvent(
                                        0,
                                        (W - 1 - n.positionInMeasure() * W) / W,
                                        1,
                                        0,
                                        1,
                                        (H - 1 - 145f) / H,
                                        false,
                                        0,
                                        n.beamCount())
                                .withStemDirection(-1));
            }
            notes.sort(Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure));
            rest =
                    new ScoreRestEvent(
                            0,
                            (W - 1 - rest.positionInMeasure() * W) / W,
                            (H - 1 - 134f) / H,
                            38f / H,
                            0,
                            1,
                            .25);
        }
    }

    @Test
    public void completeAttachedBeamOwnsTheLoweredShortRest() {
        assertTrue(new Scene().owns());
    }

    @Test
    public void downwardBeamOwnsItsRaisedShortRest() {
        Scene s = new Scene();
        s.mirror();
        assertTrue(s.owns());
    }

    @Test
    public void anInterruptedBeamCannotClaimTheRest() {
        Scene s = new Scene();
        for (int x = 207; x <= 220; x++)
            for (int y = 82; y <= 90; y++) s.gray[y * W + x] = (byte) 255;
        assertFalse(s.owns());
    }

    @Test
    public void semanticBeamMetadataAloneCannotClaimTheRest() {
        Scene s = new Scene();
        Arrays.fill(s.gray, (byte) 255);
        assertFalse(s.owns());
    }

    @Test
    public void longRestRemainsOutsideThisShortBeamRule() {
        Scene s = new Scene();
        s.rest = new ScoreRestEvent(0, 205f / W, 134f / H, 38f / H, 0, 1, 2);
        assertFalse(s.owns());
    }

    @Test
    public void anInterveningAttackPreventsBorrowingAnotherPair() {
        Scene s = new Scene();
        s.notes.add(
                new ScoreNoteEvent(0, 205f / W, 2, 0, 1, 145f / H, false, 0, 1)
                        .withStemDirection(1));
        assertFalse(s.owns());
    }

    @Test
    public void aNonBeamedFollowingNoteDoesNotProveTheVoice() {
        Scene s = new Scene();
        s.notes.set(
                1,
                new ScoreNoteEvent(0, 250f / W, -1, 0, 1, 145f / H, false, 0, 0)
                        .withStemDirection(1));
        assertFalse(s.owns());
    }

    @Test
    public void opposingCoincidentVoicePreventsReassignment() {
        Scene s = new Scene();
        s.oval(165, 165);
        for (int y = 165; y <= 225; y++) {
            s.gray[y * W + 157] = 0;
            s.gray[y * W + 156] = 0;
        }
        s.notes.add(
                new ScoreNoteEvent(0, 165f / W, -4, 0, 1, 165f / H, false, 0, 1)
                        .withStemDirection(-1));
        assertFalse(s.owns());
    }

    @Test
    public void aRestBeforeThePairKeepsItsOwnVoice() {
        Scene s = new Scene();
        s.rest = new ScoreRestEvent(0, 140f / W, 134f / H, 38f / H, 0, 1, .25);
        assertFalse(s.owns());
    }

    @Test
    public void aRestOnTheFollowingAttackIsNotSeparateSilence() {
        Scene s = new Scene();
        s.rest = new ScoreRestEvent(0, 250f / W, 134f / H, 38f / H, 0, 1, .25);
        assertFalse(s.owns());
    }
}
