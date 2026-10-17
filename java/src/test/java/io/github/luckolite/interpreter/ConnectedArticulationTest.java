// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent articulation on a real note near a pale printed word baseline. */
public final class ConnectedArticulationTest {
    static final int W = 1000, H = 1000;
    static final float GAP = 12;

    static byte[][] scene(boolean bridge, boolean tenuto) {
        byte[] g = new byte[W * H], l = new byte[W * H];
        Arrays.fill(g, (byte) 205);
        for (int c = 162; c <= 246; c += 14)
            for (int y = 121; y <= 143; y++)
                for (int x = c - 8; x <= c + 8; x++) {
                    double xx = (x - c - (143 - y) * .12) / 7.,
                            yy = (y - 132) / 10.,
                            r = xx * xx + yy * yy;
                    if (r >= .55 && r <= 1.3) {
                        g[y * W + x] = (byte) 155;
                        l[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
                    }
                }
        for (int y = 140; y <= 142; y++)
            for (int x = 160; x <= 251; x++) {
                g[y * W + x] = (byte) 155;
                l[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
            }
        if (bridge)
            for (int y = 143; y <= 155; y++) {
                g[y * W + 211] = (byte) 155;
                l[y * W + 211] = OmrMeasurePostProcessor.SYMBOL;
            }
        if (tenuto) {
            for (int y = 154; y <= 155; y++)
                for (int x = 205; x <= 217; x++) {
                    g[y * W + x] = 35;
                    l[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
                }
        } else
            for (int y = 153; y <= 157; y++)
                for (int x = 209; x <= 213; x++) {
                    g[y * W + x] = 35;
                    l[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
                }
        return new byte[][] {g, l};
    }

    static List<PlayingTechniqueDetector.Word> words() {
        return List.of(
                new PlayingTechniqueDetector.Word(
                        "flowing",
                        .149f,
                        .111f,
                        .265f,
                        .163f,
                        PlayingTechniqueDetector.WordAdmission.CONTEXT_QUALIFIED));
    }

    static int marks(byte[][] s, boolean filtered) {
        var a = List.of(new NoteArticulationDetector.Anchor(211, 179, GAP, 0));
        return filtered
                ? NoteArticulationDetector.detectWithWordBodies(
                        s[1],
                        s[0],
                        W,
                        H,
                        a,
                        ExpressiveWordBodyInk.detect(words(), s[1], s[0], W, H, a))[0]
                : NoteArticulationDetector.detect(s[1], s[0], W, H, a)[0];
    }

    @Test
    public void detachedGenuineStaccatoSurvives() {
        var s = scene(false, false);
        assertEquals(NoteArticulation.STACCATO, marks(s, false));
        assertEquals(NoteArticulation.STACCATO, marks(s, true));
    }

    @Test
    public void paleTouchCannotReassignGenuineStaccatoToWord() {
        var s = scene(true, false);
        assertEquals(NoteArticulation.STACCATO, marks(s, false));
        assertEquals(NoteArticulation.STACCATO, marks(s, true));
    }

    @Test
    public void paleTouchCannotReassignGenuineTenutoToWord() {
        var s = scene(true, true);
        assertEquals(NoteArticulation.TENUTO, marks(s, false));
        assertEquals(NoteArticulation.TENUTO, marks(s, true));
    }

    @Test
    public void actualPostpassPreservesGenuineStaccato() {
        var s = scene(true, false);
        var n =
                new ScoreNoteEvent(
                        0,
                        .211f,
                        0,
                        0,
                        1,
                        .179f,
                        false,
                        1,
                        1,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        0,
                        3,
                        0,
                        NoteArticulation.STACCATO,
                        ScoreNoteEvent.CLEF_TREBLE);
        assertEquals(
                List.of(n),
                PrintedWordArticulations.apply(
                        words(),
                        List.of(new PlayingTechniqueDetector.Staff(150, 198, GAP, 0, 1)),
                        List.of(new MeasureRegion(0, 1, .15f, .21f)),
                        List.of(n),
                        s[1],
                        s[0],
                        W,
                        H));
    }

    public static void main(String[] args) {
        for (boolean bridge : new boolean[] {false, true})
            for (boolean t : new boolean[] {false, true}) {
                var s = scene(bridge, t);
                System.out.println(
                        "bridge="
                                + bridge
                                + ",tenuto="
                                + t
                                + ",before="
                                + marks(s, false)
                                + ",after="
                                + marks(s, true));
            }
    }
}
