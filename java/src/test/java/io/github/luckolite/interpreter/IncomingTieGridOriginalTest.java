// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original anti-aliased returning bows, a tiny mask fringe, and two source owners. */
public final class IncomingTieGridOriginalTest {
    static final int W = IncomingTieGridScenes.W, H = IncomingTieGridScenes.H;

    private static boolean tied(byte[][] pixels, int pitch, boolean interrupt, boolean foreign)
            throws Exception {
        Object before = IncomingTieGridScenes.note(0, 440, 80, 1, 0),
                after = IncomingTieGridScenes.note(1, 180, 240, 0, 2, pitch);
        if (foreign) {
            var e = new ScoreNoteEvent(1, .1f, 1, 1, 2, 240f / H, false, 0, 0, 2, 2, 1, 0, 0, 30);
            after =
                    IncomingTieGridScenes.make(
                            "DetectedNote",
                            e,
                            IncomingTieGridScenes.make(
                                    "Component", 150, 170, 190, 233, 247, 180f, 240f),
                            14f);
        }
        var notes = new ArrayList<Object>();
        notes.add(before);
        if (interrupt) notes.add(IncomingTieGridScenes.note(0, 490, 80, 1, 0, 2));
        notes.add(after);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "markTieContinuations",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        var out = (List<?>) m.invoke(null, pixels[0], pixels[1], W, H, notes, List.of());
        assertEquals(notes.size(), out.size());
        var event = out.get(out.size() - 1).getClass().getDeclaredMethod("event");
        event.setAccessible(true);
        return ((ScoreNoteEvent) event.invoke(out.get(out.size() - 1))).tiedFromPrevious();
    }

    private static void positive(int phase) throws Exception {
        var p = IncomingTieGridScenes.pixels(phase, 35, 5);
        var l = p[0].clone();
        var g = p[1].clone();
        assertTrue(tied(p, 1, false, false));
        assertArrayEquals(l, p[0]);
        assertArrayEquals(g, p[1]);
    }

    @Test
    public void fringePhaseOneKeepsPairedTie() throws Exception {
        positive(1);
    }

    @Test
    public void fringePhaseTwoKeepsPairedTie() throws Exception {
        positive(2);
    }

    @Test
    public void fringePhaseThreeKeepsPairedTie() throws Exception {
        positive(3);
    }

    @Test
    public void fringePhaseFourKeepsPairedTie() throws Exception {
        positive(4);
    }

    @Test
    public void fringePhaseFiveKeepsPairedTie() throws Exception {
        positive(5);
    }

    private static void erase(byte[][] p, int left, int right, int top, int bottom) {
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                int at = y * W + x;
                p[0][at] = 0;
                p[1][at] = (byte) 245;
                if (y == 247 || y == 87) {
                    p[0][at] = 4;
                    p[1][at] = 0;
                }
            }
    }

    @Test
    public void incomingBowAloneCannotTie() throws Exception {
        var p = IncomingTieGridScenes.pixels(1, 35, 5);
        erase(p, 451, 477, 88, 97);
        assertFalse(tied(p, 1, false, false));
    }

    @Test
    public void outgoingBowAloneCannotTie() throws Exception {
        var p = IncomingTieGridScenes.pixels(1, 35, 5);
        erase(p, 130, 170, 248, 257);
        assertFalse(tied(p, 1, false, false));
    }

    @Test
    public void differentPitchCannotTie() throws Exception {
        assertFalse(tied(IncomingTieGridScenes.pixels(1, 35, 5), 2, false, false));
    }

    @Test
    public void interveningMelodyAttackCannotBeSkipped() throws Exception {
        assertFalse(tied(IncomingTieGridScenes.pixels(1, 35, 5), 1, true, false));
    }

    @Test
    public void foreignStaffCannotBorrowBow() throws Exception {
        assertFalse(tied(IncomingTieGridScenes.pixels(1, 35, 5), 1, false, true));
    }

    @Test
    public void realHeadSizedOcclusionCannotSupplyContinuity() throws Exception {
        var p = IncomingTieGridScenes.pixels(1, 35, 5);
        for (int x = 144; x <= 156; x++) for (int y = 248; y <= 256; y++) p[0][y * W + x] = 2;
        assertFalse(tied(p, 1, false, false));
    }

    @Test
    public void brokenBowCannotSupplyContinuity() throws Exception {
        var p = IncomingTieGridScenes.pixels(1, 35, 5);
        erase(p, 144, 156, 248, 257);
        assertFalse(tied(p, 1, false, false));
    }

    @Test
    public void straightIncomingStrokeCannotTie() throws Exception {
        var p = IncomingTieGridScenes.pixels(1, 35, 0);
        assertFalse(tied(p, 1, false, false));
    }

    @Test
    public void plainBowStillTiesWithoutMaskFringe() throws Exception {
        var p = IncomingTieGridScenes.pixels(1, 35, 5);
        for (int i = 0; i < p[0].length; i++) if (p[0][i] == 2) p[0][i] = 5;
        assertTrue(tied(p, 1, false, false));
    }

    @Test
    public void lowLocalContrastCannotCompleteTheIncomingTie() throws Exception {
        byte[][] original = IncomingTieGridScenes.pixels(1, 35, 5);
        assertTrue(tied(original, 1, false, false));
        byte[] originalLabels = original[0].clone(), originalGray = original[1].clone();
        byte[][] lowContrast = IncomingTieGridScenes.lowContrastIncoming(original, 1, 35, 5);
        assertArrayEquals(originalLabels, lowContrast[0]);
        assertArrayEquals(originalLabels, original[0]);
        assertArrayEquals(originalGray, original[1]);
        int[] evidence = IncomingTieGridScenes.incomingStrongContrastSamples(lowContrast, 1, 35, 5);
        assertTrue("the raw arc retains at least thirty strong samples", evidence[0] >= 30);
        assertTrue(
                "the incoming run is locally under the twenty-level contrast requirement",
                evidence[1] >= 30);
        assertFalse(tied(lowContrast, 1, false, false));
    }
}
