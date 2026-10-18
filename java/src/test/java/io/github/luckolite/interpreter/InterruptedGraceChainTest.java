// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original vector head/shaft/beam scene; no commercial score pixels or coordinates. */
public class InterruptedGraceChainTest {
    static final int W = 320, H = 240;

    static List<ScoreNoteEvent> scene(
            boolean link, boolean doubleBeam, boolean principal, boolean fullFirst)
            throws Exception {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        var hc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        hc.setAccessible(true);
        var dc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$DetectedNote")
                        .getDeclaredConstructors()[0];
        dc.setAccessible(true);
        var detected = new ArrayList<Object>();
        var events = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 4; i++) {
            boolean large = i == 3 ? principal : i == 0 && fullFirst;
            int x = new int[] {100, 125, 150, 190}[i], y = i == 1 ? 145 : 150;
            int rx = large ? 11 : 5, ry = large ? 7 : 4, area = 0;
            for (int yy = y - ry; yy <= y + ry; yy++)
                for (int xx = x - rx; xx <= x + rx; xx++)
                    if ((xx - x) * (xx - x) / (double) (rx * rx)
                                    + (yy - y) * (yy - y) / (double) (ry * ry)
                            <= 1) {
                        gray[yy * W + xx] = 40;
                        labels[yy * W + xx] = 2;
                        area++;
                    }
            int top = large ? 95 : (i == 1 ? 105 : 110);
            for (int yy = top; yy <= y; yy++) {
                gray[yy * W + x + rx] = (byte) (i == 1 ? 181 : 40);
                labels[yy * W + x + rx] = 1;
            }
            var event =
                    new ScoreNoteEvent(
                            0,
                            x / (float) W,
                            2,
                            0,
                            1,
                            y / (float) H,
                            false,
                            0,
                            i == 3 ? 0 : 2,
                            2,
                            i == 3 ? 1 : 0);
            events.add(event);
            detected.add(
                    dc.newInstance(
                            event,
                            hc.newInstance(
                                    area, x - rx, x + rx, y - ry, y + ry, (float) x, (float) y),
                            16f));
        }
        for (int segment = 0; segment < 2; segment++)
            if (link || segment == 1) {
                int left = 105 + 25 * segment, right = left + 25;
                int ya = segment == 0 ? 110 : 105, yb = segment == 0 ? 105 : 110;
                for (int x = left; x <= right; x++) {
                    int y = ya + Math.round((x - left) * (yb - ya) / 25f);
                    for (int beam = 0; beam < (doubleBeam ? 2 : 1); beam++)
                        for (int dy = 0; dy < 5; dy++) {
                            gray[(y + 11 * beam + dy) * W + x] = 40;
                            labels[(y + 11 * beam + dy) * W + x] = 1;
                        }
                }
            }
        byte[] before = gray.clone();
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "markGraceHeads",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        method.setAccessible(true);
        method.invoke(null, labels, gray, W, H, detected, events);
        assertArrayEquals(before, gray);
        return events;
    }

    static boolean grace(ScoreNoteEvent n) {
        return (n.articulations() & NoteOrnament.GRACE) != 0;
    }

    @Test
    public void middlePaleShaftDoesNotOrphanFirstOwnedDoubleBeamGrace() throws Exception {
        var n = scene(true, true, true, false);
        assertTrue("first of complete three-head beam chain", grace(n.get(0)));
        assertTrue(grace(n.get(1)));
        assertTrue(grace(n.get(2)));
        assertFalse(grace(n.get(3)));
    }

    @Test
    public void disconnectedFirstHeadIsNotPromotedByFollowingGraceFlags() throws Exception {
        assertFalse(grace(scene(false, true, true, false).get(0)));
    }

    @Test
    public void singleBeamDoesNotSupplyNewChainProof() throws Exception {
        assertFalse(grace(scene(true, false, true, false).get(0)));
    }

    @Test
    public void uniformSmallPhraseWithoutLargerPrincipalRemainsUnpromoted() throws Exception {
        assertFalse(grace(scene(true, true, false, false).get(0)));
    }

    @Test
    public void fullSizeFirstHeadIsNotBorrowedIntoGraceTail() throws Exception {
        assertFalse(grace(scene(true, true, true, true).get(0)));
    }
}
