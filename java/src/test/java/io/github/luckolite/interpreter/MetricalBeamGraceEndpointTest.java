// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original nearby beam fragments and a separately stemmed double-beamed grace pair. */
public class MetricalBeamGraceEndpointTest {
    static final int W = 280, H = 220;

    private List<ScoreNoteEvent> run(boolean reachesGraceShaft) throws Exception {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 93; y <= 150; y++) gray[y * W + 81] = 30;
        for (int y = 110; y <= 150; y++) gray[y * W + 125] = 30;
        for (int y = 113; y <= 158; y++) gray[y * W + 150] = 30;
        for (int x = 125; x <= 150; x++)
            for (int beam = 0; beam < 2; beam++)
                for (int dy = 0; dy < 5; dy++) {
                    int y = 110 + Math.round((x - 125) * 3f / 25) + beam * 11 + dy;
                    gray[y * W + x] = 30;
                }
        // Most of a possible connecting path is dark, but its last inner columns
        // remain white unless the ordinary beam actually reaches the grace shaft.
        for (int x = 81; x <= (reachesGraceShaft ? 125 : 120); x++)
            for (int dy = 0; dy < 3; dy++) {
                int y = 93 + Math.round((x - 81) * 17f / 44) + dy;
                gray[y * W + x] = 30;
            }
        var hc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        hc.setAccessible(true);
        var dc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$DetectedNote")
                        .getDeclaredConstructors()[0];
        dc.setAccessible(true);
        var detected = new ArrayList<>();
        var events = new ArrayList<ScoreNoteEvent>();
        int[] xs = {70, 120, 145, 190}, ys = {150, 150, 158, 150};
        for (int i = 0; i < 4; i++) {
            boolean tiny = i == 1 || i == 2;
            int rx = tiny ? 5 : 11, ry = tiny ? 4 : 7;
            Object head =
                    hc.newInstance(
                            tiny ? 65 : 260,
                            xs[i] - rx,
                            xs[i] + rx,
                            ys[i] - ry,
                            ys[i] + ry,
                            (float) xs[i],
                            (float) ys[i]);
            var event =
                    new ScoreNoteEvent(
                            0,
                            xs[i] / (float) W,
                            2,
                            0,
                            1,
                            ys[i] / (float) H,
                            false,
                            0,
                            i == 1 ? 1 : 2,
                            2,
                            0);
            events.add(event);
            detected.add(dc.newInstance(event, head, 16f));
        }
        var mark =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "markPairedGraces",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        mark.setAccessible(true);
        mark.invoke(null, gray, W, H, detected, events);
        return events;
    }

    @Test
    public void nearbyDisconnectedOrdinaryInkCannotOwnTheGracePair() throws Exception {
        var notes = run(false);
        assertEquals(0, notes.get(0).articulations());
        assertTrue((notes.get(1).articulations() & NoteOrnament.GRACE) != 0);
        assertTrue((notes.get(2).articulations() & NoteOrnament.GRACE) != 0);
        assertEquals(2, notes.get(1).beamCount());
        assertEquals(2, notes.get(2).beamCount());
        assertEquals(0, notes.get(3).articulations());
    }

    @Test
    public void ordinaryBeamReachingTheGraceShaftKeepsItsAttacksMetrical() throws Exception {
        for (var note : run(true)) assertEquals(0, note.articulations() & NoteOrnament.GRACE);
    }

    private boolean sharedBeam(
            float gap, boolean strict, boolean firstEnd, int paleColumns, boolean detached)
            throws Exception {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 93; y <= 150; y++) gray[y * W + 81] = 30;
        for (int y = 110; y <= 150; y++) gray[y * W + 125] = 30;
        for (int x = 81; x <= 125; x++)
            for (int dy = 0; dy < 3; dy++) {
                int y = 93 + Math.round((x - 81) * 17f / 44) + dy;
                gray[y * W + x] = 30;
            }
        int nearSamples = Math.max(4, Math.round(gap * .3f)) - 1;
        int missing = detached ? nearSamples : paleColumns;
        for (int i = 0; i < missing; i++) {
            int x = firstEnd ? 83 + i : 123 - i;
            for (int y = 90; y <= 116; y++) gray[y * W + x] = (byte) (detached ? 255 : 170);
        }
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object first = ctor.newInstance(260, 59, 81, 143, 157, 70f, 150f),
                next = ctor.newInstance(65, 115, 125, 146, 154, 120f, 150f);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "graceStemsShareBeam",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        c,
                        float.class,
                        boolean.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, null, gray, W, H, first, next, gap, strict);
    }

    @Test
    public void exactTwoThirdsPreservesPaleEndpointColumnsAcrossStaffSizes() throws Exception {
        for (float gap : new float[] {14.3f, 16f, 24f})
            for (boolean firstEnd : new boolean[] {false, true}) {
                int pale = gap == 24f ? 2 : 1;
                assertTrue(
                        "gap=" + gap + " firstEnd=" + firstEnd,
                        sharedBeam(gap, true, firstEnd, pale, false));
            }
    }

    @Test
    public void detachedEndpointsRemainRejectedAcrossStaffSizes() throws Exception {
        for (float gap : new float[] {14.3f, 16f, 24f})
            for (boolean firstEnd : new boolean[] {false, true}) {
                assertTrue(sharedBeam(gap, false, firstEnd, 0, true));
                assertFalse(
                        "gap=" + gap + " firstEnd=" + firstEnd,
                        sharedBeam(gap, true, firstEnd, 0, true));
            }
    }
}
