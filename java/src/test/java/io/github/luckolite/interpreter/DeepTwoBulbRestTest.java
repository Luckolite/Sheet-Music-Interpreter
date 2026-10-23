// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original displaced two-bulb rests between independently attached beam owners. */
public class DeepTwoBulbRestTest {
    private static final int W = 440, H = 300;

    private static final class Scene {
        byte[] g = new byte[W * H];
        List<ScoreNoteEvent> notes = new ArrayList<>();

        Scene(boolean down) {
            Arrays.fill(g, (byte) 255);
            for (int row : new int[] {80, 94, 108, 122, 136})
                for (int x = 12; x < W - 12; x++) g[row * W + x] = 0;
            for (int y = 125; y <= 163; y++) {
                int x = 210 - (y - 125) * 10 / 38;
                g[y * W + x] = 0;
                g[y * W + x + 1] = 0;
            }
            oval(204, 128, 5, 4);
            oval(200, 142, 5, 4);
            int[] ys = down ? new int[] {90, 104} : new int[] {154, 140};
            for (int i = 0; i < 2; i++) {
                int x = i == 0 ? 165 : 250,
                        y = ys[i],
                        stem = x + (down ? -8 : 8),
                        tip = down ? 183 : 112;
                oval(x, y, 9, 7);
                for (int yy = Math.min(y, tip); yy <= Math.max(y, tip); yy++) {
                    g[yy * W + stem] = 0;
                    g[yy * W + stem + (down ? -1 : 1)] = 0;
                }
                notes.add(
                        new ScoreNoteEvent(
                                        0,
                                        x / (float) W,
                                        down ? 6 : 0,
                                        0,
                                        1,
                                        y / (float) H,
                                        false,
                                        0,
                                        2)
                                .withStemDirection(down ? -1 : 1));
            }
            int tip = down ? 183 : 112;
            for (int x = down ? 156 : 173; x <= (down ? 242 : 259); x++)
                for (int y = tip; y <= tip + 4; y++) g[y * W + x] = 0;
        }

        void oval(int cx, int cy, int rx, int ry) {
            for (int y = cy - ry; y <= cy + ry; y++)
                for (int x = cx - rx; x <= cx + rx; x++)
                    if ((x - cx) * (x - cx) / (double) (rx * rx)
                                    + (y - cy) * (y - cy) / (double) (ry * ry)
                            <= 1) g[y * W + x] = 0;
        }

        List<ScoreRestEvent> rests() {
            byte[] prior = g.clone();
            var result =
                    SixteenthRestDetector.detect(
                            g,
                            W,
                            H,
                            List.of(new MeasureRegion(0, 1, .1f, .9f)),
                            List.of(new SixteenthRestDetector.Staff(80, 136, 14, 0, 1)),
                            notes);
            assertArrayEquals(prior, g);
            return result;
        }

        void proved() {
            var r = rests();
            assertEquals(r.toString(), 1, r.size());
            assertEquals(.25, r.get(0).durationBeats(), 0);
        }

        void notSixteenth() {
            assertTrue(rests().stream().noneMatch(r -> r.durationBeats() == .25));
        }
    }

    @Test
    public void completeTwoBulbsRetainTheWholeLoweredTail() {
        new Scene(false).proved();
    }

    @Test
    public void downwardBeamCanOwnTheSameLoweredRest() {
        new Scene(true).proved();
    }

    @Test
    public void anUnownedGlyphCannotCreateSilence() {
        Scene s = new Scene(false);
        s.notes.clear();
        s.notSixteenth();
    }

    @Test
    public void semanticBeamFlagsCannotReplaceRawBeamInk() {
        Scene s = new Scene(false);
        for (int x = 170; x <= 260; x++)
            for (int y = 110; y <= 117; y++) s.g[y * W + x] = (byte) 255;
        s.notSixteenth();
    }

    @Test
    public void anInterruptedBeamDoesNotClaimTheRest() {
        Scene s = new Scene(false);
        for (int x = 208; x <= 224; x++)
            for (int y = 110; y <= 117; y++) s.g[y * W + x] = (byte) 255;
        s.notSixteenth();
    }

    @Test
    public void aPrintedHeadInsideTheGlyphKeepsItsOwnership() {
        Scene s = new Scene(false);
        s.oval(200, 142, 9, 7);
        s.notes.add(new ScoreNoteEvent(0, 200f / W, 0, 0, 1, 142f / H, false, 0, 1));
        s.notSixteenth();
    }

    @Test
    public void missingTailDoesNotProveACompleteTwoBulbRest() {
        Scene s = new Scene(false);
        for (int y = 149; y <= 164; y++)
            for (int x = 195; x <= 214; x++) s.g[y * W + x] = (byte) 255;
        s.notSixteenth();
    }

    @Test
    public void oneBulbCannotBecomeTwo() {
        Scene s = new Scene(false);
        for (int y = 123; y <= 133; y++)
            for (int x = 197; x <= 209; x++) s.g[y * W + x] = (byte) 255;
        s.notSixteenth();
    }

    @Test
    public void anotherStaffCannotAuthorizeTheDeepVoice() {
        Scene s = new Scene(false);
        s.notes.replaceAll(
                n ->
                        new ScoreNoteEvent(
                                        0, n.positionInMeasure(), 0, 1, 2, n.pageY(), false, 0, 2)
                                .withStemDirection(1));
        s.notSixteenth();
    }

    @Test
    public void anotherMeasureCannotAuthorizeTheDeepVoice() {
        Scene s = new Scene(false);
        s.notes.replaceAll(
                n ->
                        new ScoreNoteEvent(
                                        1, n.positionInMeasure(), 0, 0, 1, n.pageY(), false, 0, 2)
                                .withStemDirection(1));
        s.notSixteenth();
    }
}
