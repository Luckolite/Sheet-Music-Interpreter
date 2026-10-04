// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original shaft/chord drawings; no score pixels or musical identities. */
public class BareQuarterStemTest {
    static final int W = 240, H = 280;
    static final float G = 16;
    static Class<?> component, staff, detected;

    static {
        try {
            component = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
            staff = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
            detected = Class.forName(OmrScoreInterpreter.class.getName() + "$DetectedNote");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Object construct(Class<?> c, Class<?>[] t, Object... args) throws Exception {
        var x = c.getDeclaredConstructor(t);
        x.setAccessible(true);
        return x.newInstance(args);
    }

    static Object head(int x, int y) throws Exception {
        return construct(
                component,
                new Class[] {
                    int.class, int.class, int.class, int.class, int.class, float.class, float.class
                },
                300,
                x - 11,
                x + 11,
                y - 9,
                y + 9,
                (float) x,
                (float) y);
    }

    static void rect(byte[] labels, byte[] gray, int x, int y, int w, int h, int label, int ink) {
        for (int j = y; j < y + h; j++)
            for (int i = x; i < x + w; i++) {
                labels[j * W + i] = (byte) label;
                gray[j * W + i] = (byte) ink;
            }
    }

    static void oval(byte[] labels, byte[] gray, int x, int y) {
        for (int j = y - 9; j <= y + 9; j++)
            for (int i = x - 11; i <= x + 11; i++)
                if ((i - x) * (i - x) / 121f + (j - y) * (j - y) / 81f <= 1)
                    rect(labels, gray, i, j, 1, 1, 2, 0);
    }

    static ScoreNoteEvent event(int y, int beams) {
        return new ScoreNoteEvent(
                0,
                .4f,
                Math.round((204 - y) / 8f),
                1,
                2,
                y / (float) H,
                false,
                0,
                beams,
                2,
                beams == 0 ? 1 : 0,
                1,
                0,
                0,
                18,
                false,
                0,
                false,
                0,
                0,
                1);
    }

    @Test
    public void oppositeVoiceDoesNotHideTheBareDownChord() throws Exception {
        byte[] l = new byte[W * H], g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        rect(l, g, 109, 140, 2, 90, 1, 0);
        rect(l, g, 130, 70, 2, 55, 1, 0);
        for (int y : new int[] {124, 140, 196}) oval(l, g, 120, y);
        List<Object> notes = new ArrayList<>();
        for (int y : new int[] {124, 140, 196})
            notes.add(
                    construct(
                            detected,
                            new Class[] {ScoreNoteEvent.class, component, float.class},
                            event(y, y == 140 ? 0 : 2),
                            head(120, y),
                            G));
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "reconcileSingleShaftChords",
                        List.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        var result = (List<?>) m.invoke(null, notes, l, g, W, H);
        var e = detected.getDeclaredMethod("event");
        e.setAccessible(true);
        assertEquals(2, ((ScoreNoteEvent) e.invoke(result.get(0))).beamCount());
        assertEquals(0, ((ScoreNoteEvent) e.invoke(result.get(1))).beamCount());
        assertEquals(0, ((ScoreNoteEvent) e.invoke(result.get(2))).beamCount());
        assertEquals(1, ((ScoreNoteEvent) e.invoke(result.get(2))).unbeamedDurationBeats(), 0);
    }

    @Test
    public void whiteCapCannotJoinASeparateBeam() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        assertTrue(
                PaleBeamRecovery.crossesBlankCap(
                        g, W, H, new int[] {130, 90, -1}, new int[] {131, 76, -1}, G));
    }

    @Test
    public void detectorCannotBorrowDetachedInkThroughWhiteCap() throws Exception {
        assertEquals(0, detachedInkCount(false));
    }

    @Test
    public void detectorRetainsALegitimateFaintAttachedShaft() throws Exception {
        assertEquals(1, detachedInkCount(true));
    }

    private int detachedInkCount(boolean faintConnection) throws Exception {
        byte[] l = new byte[W * H], g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        oval(l, g, 120, 140);
        rect(l, g, 130, 90, 2, 51, 1, 0);
        g[89 * W + 130] = (byte) 239;
        g[89 * W + 131] = (byte) 210;
        for (int y = 76; y <= 84; y++) rect(l, g, 100, y, 57 - (y - 76) * 3, 1, 5, 0);
        g[85 * W + 131] = (byte) 233;
        if (faintConnection) for (int y = 86; y <= 88; y++) g[y * W + 131] = (byte) 230;
        Object s =
                construct(
                        staff, new Class[] {float.class, float.class, float.class}, 140f, 204f, G);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        component,
                        staff,
                        boolean.class);
        m.setAccessible(true);
        return (int) m.invoke(null, l, g, W, H, head(120, 140), s, false);
    }

    @Test
    public void continuousFaintShaftRemainsRecoverable() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 76; y <= 90; y++) g[y * W + 131] = (byte) 230;
        assertFalse(
                PaleBeamRecovery.crossesBlankCap(
                        g, W, H, new int[] {130, 90, -1}, new int[] {131, 76, -1}, G));
    }

    @Test
    public void singleRasterSeamDoesNotRejectAttachedInk() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 76; y <= 90; y++) if (y != 85) g[y * W + 131] = (byte) 230;
        assertFalse(
                PaleBeamRecovery.crossesBlankCap(
                        g, W, H, new int[] {130, 90, -1}, new int[] {131, 76, -1}, G));
    }

    @Test
    public void staffLabelSuppressionIsNotWhitePaper() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 76; y <= 90; y++) g[y * W + 130] = 0;
        assertFalse(
                PaleBeamRecovery.crossesBlankCap(
                        g, W, H, new int[] {130, 90, -1}, new int[] {131, 76, -1}, G));
    }

    @Test
    public void unrelatedOrInvalidTraceIsNotProof() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        assertFalse(PaleBeamRecovery.crossesBlankCap(g, W, H, null, new int[] {131, 76, -1}, G));
        assertFalse(
                PaleBeamRecovery.crossesBlankCap(
                        g, W, H, new int[] {130, 90, -1}, new int[] {131, 76, 1}, G));
        assertFalse(
                PaleBeamRecovery.crossesBlankCap(
                        g, W, H, new int[] {130, 90, -1}, new int[] {160, 76, -1}, G));
    }
}
