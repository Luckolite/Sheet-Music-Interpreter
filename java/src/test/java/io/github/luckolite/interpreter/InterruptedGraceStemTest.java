// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original reduced shafts separated from an outer beam by a short white gap. */
public class InterruptedGraceStemTest {
    static final int W = 300, H = 240;

    static List<ScoreNoteEvent> run(int rails, int blanks, boolean fullPair, boolean outer)
            throws Exception {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 110; y <= 150; y++) gray[y * W + 105] = 40;
        for (int y = 112; y <= 158; y++) gray[y * W + 130] = 40;
        for (int x = 105; x <= 130; x++)
            for (int b = 0; b < rails; b++)
                for (int dy = 0; dy < 5; dy++)
                    gray[(110 + Math.round((x - 105) * 2f / 25) + b * 11 + dy) * W + x] = 40;
        if (outer) {
            for (int x = 80; x <= 145; x++) for (int y = 90; y <= 94; y++) gray[y * W + x] = 40;
            for (int y = 95; y < 110 - blanks; y++) gray[y * W + 105] = 40;
        }
        byte[] before = gray.clone();
        var hc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        hc.setAccessible(true);
        var dc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$DetectedNote")
                        .getDeclaredConstructors()[0];
        dc.setAccessible(true);
        var events = new ArrayList<ScoreNoteEvent>();
        var detected = new ArrayList<Object>();
        for (float x : new float[] {100, 125, 180}) {
            boolean principal = x == 180, full = principal || fullPair;
            float y = x == 125 ? 158 : 150;
            int rx = full ? 11 : 5, ry = full ? 7 : 4;
            var head =
                    hc.newInstance(
                            full ? 260 : 65,
                            (int) x - rx,
                            (int) x + rx,
                            (int) y - ry,
                            (int) y + ry,
                            x,
                            y);
            var event =
                    new ScoreNoteEvent(
                            0,
                            x / W,
                            2,
                            0,
                            1,
                            y / H,
                            false,
                            0,
                            principal ? 0 : 1,
                            2,
                            principal ? 1 : 0);
            events.add(event);
            detected.add(dc.newInstance(event, head, 16f));
        }
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "markPairedGraces",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        m.invoke(null, gray, W, H, detected, events);
        assertArrayEquals(before, gray);
        return events;
    }

    @Test
    public void twoBlankBridgeCannotHideDoubleGrace() throws Exception {
        var n = run(2, 2, false, true);
        assertEquals(2, n.get(0).beamCount());
        assertEquals(2, n.get(1).beamCount());
    }

    @Test
    public void threeBlankBridgeCannotHideDoubleGrace() throws Exception {
        var n = run(2, 3, false, true);
        assertEquals(2, n.get(0).beamCount());
        assertEquals(2, n.get(1).beamCount());
    }

    @Test
    public void singleRailCannotGainSecondBeam() throws Exception {
        var n = run(1, 2, false, true);
        assertEquals(1, n.get(0).beamCount());
        assertEquals(1, n.get(1).beamCount());
    }

    @Test
    public void solidOuterStemDoesNotProveCompactPair() throws Exception {
        var n = run(2, 0, false, true);
        assertEquals(1, n.get(0).beamCount());
        assertEquals(1, n.get(1).beamCount());
    }

    @Test
    public void ordinaryHeadsStayMetrical() throws Exception {
        var n = run(2, 2, true, true);
        assertEquals(1, n.get(0).beamCount());
        assertEquals(1, n.get(1).beamCount());
    }

    @Test
    public void originalPairRemainsRecognized() throws Exception {
        var n = run(2, 2, false, false);
        assertEquals(2, n.get(0).beamCount());
        assertEquals(2, n.get(1).beamCount());
    }
}
