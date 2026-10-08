// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural header bodies, shaded paper and sharp crossbars. */
public class CurvedHeaderOwnershipTest {
    private static final int W = 800, H = 400, G = 16;

    private static final class Page {
        final byte[] labels = new byte[W * H], gray = new byte[W * H];
        final float slope;
        final Object staff;

        Page(
                float slope,
                boolean tracked,
                int clefWidth,
                int clefHeight,
                boolean sharp,
                boolean blur,
                int sharpOffset,
                boolean tinyHead,
                boolean closeNote)
                throws Exception {
            this(
                    slope,
                    tracked,
                    clefWidth,
                    clefHeight,
                    sharp,
                    blur,
                    sharpOffset,
                    tinyHead,
                    closeNote,
                    OmrMeasurePostProcessor.CLEF_OR_KEY);
        }

        Page(
                float slope,
                boolean tracked,
                int clefWidth,
                int clefHeight,
                boolean sharp,
                boolean blur,
                int sharpOffset,
                boolean tinyHead,
                boolean closeNote,
                byte clefLabel)
                throws Exception {
            this.slope = slope;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) gray[y * W + x] = (byte) (134 + (x * 17 + y * 11) % 4);
            for (int x = 20; x < W - 20; x++)
                for (int line = 0; line < 5; line++)
                    ink(x, Math.round(150 + G * line + slope * (x - W * .5f)), 4, 45);
            int clefTop = Math.round(top(100) - 28);
            for (int y = clefTop; y < clefTop + clefHeight; y++)
                for (int x = 100 - clefWidth / 2; x <= 100 + clefWidth / 2; x++)
                    ink(x, y, clefLabel, 45);
            int pitch = Math.round(top(146)) + sharpOffset;
            if (sharp) {
                if (blur)
                    for (int y = pitch - 4; y <= pitch + 4; y++)
                        for (int x = 142; x <= 149; x++) gray[y * W + x] = 86;
                for (int x : new int[] {141, 142, 149, 150})
                    for (int y = pitch - 22; y <= pitch + 22; y++) gray[y * W + x] = 55;
                for (int cy : new int[] {pitch - 5, pitch + 5})
                    for (int y = cy - 1; y <= cy + 1; y++)
                        for (int x = 138; x <= 153; x++) gray[y * W + x] = 55;
                // No complete semantic sharp survives; only a small connected spine seed.
                for (int y = pitch - 10; y <= pitch - 7; y++)
                    for (int x = 141; x <= 142; x++) labels[y * W + x] = 3;
                if (tinyHead)
                    for (int y = pitch + 4; y <= pitch + 6; y++)
                        for (int x = 151; x <= 153; x++) labels[y * W + x] = 2;
            }
            int hx = closeNote ? 164 : 300, hy = Math.round(top(hx) + G * 2);
            for (int y = hy - 5; y <= hy + 5; y++)
                for (int x = hx - 8; x <= hx + 8; x++)
                    if ((x - hx) * (x - hx) / 64.0 + (y - hy) * (y - hy) / 25.0 <= 1)
                        ink(x, y, 2, 45);
            Class<?> sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
            Constructor<?> ctor = sc.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            staff = ctor.newInstance(150f, 214f, 16f);
            if (tracked) {
                Field f = sc.getDeclaredField("pitchTrack");
                f.setAccessible(true);
                f.set(
                        staff,
                        StaffPitchTrack.fromVerifiedSamples(
                                List.of(
                                        new float[] {0, 214 - slope * W * .5f, G},
                                        new float[] {W - 1, 214 + slope * (W - 1 - W * .5f), G})));
            }
        }

        float top(float x) {
            return 150 + slope * (x - W * .5f);
        }

        void ink(int x, int y, int label, int tone) {
            labels[y * W + x] = (byte) label;
            gray[y * W + x] = (byte) tone;
        }

        @SuppressWarnings("unchecked")
        List<ScoreKeyChange> keys() throws Exception {
            Class<?> root = OmrScoreInterpreter.class;
            Method components =
                    root.getDeclaredMethod(
                            "findComponents", byte[].class, int.class, int.class, byte.class);
            components.setAccessible(true);
            List<Object> heads = (List<Object>) components.invoke(null, labels, W, H, (byte) 2);
            Class<?> ac = Class.forName(root.getName() + "$AccidentalCandidate");
            Constructor<?> ctor = ac.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            List<Object> candidates = new ArrayList<>();
            for (byte label :
                    new byte[] {
                        OmrMeasurePostProcessor.NOTEHEAD,
                        OmrMeasurePostProcessor.CLEF_OR_KEY,
                        OmrMeasurePostProcessor.SYMBOL
                    })
                for (Object c : (List<Object>) components.invoke(null, labels, W, H, label))
                    candidates.add(ctor.newInstance(c, label));
            Method detect =
                    root.getDeclaredMethod(
                            "detectKeyChanges",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            List.class,
                            List.class,
                            List.class,
                            List.class);
            detect.setAccessible(true);
            return (List<ScoreKeyChange>)
                    detect.invoke(
                            null,
                            labels,
                            gray,
                            W,
                            H,
                            List.of(new MeasureRegion(180f / W, .95f, .1f, .9f)),
                            List.of(staff),
                            candidates,
                            heads);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean owned(Page p, boolean main) throws Exception {
        Class<?> root = OmrScoreInterpreter.class;
        Method c =
                root.getDeclaredMethod(
                        "findComponents", byte[].class, int.class, int.class, byte.class);
        c.setAccessible(true);
        List<Object>
                heads =
                        (List<Object>)
                                c.invoke(null, p.labels, W, H, OmrMeasurePostProcessor.NOTEHEAD),
                clefs =
                        (List<Object>)
                                c.invoke(null, p.labels, W, H, OmrMeasurePostProcessor.CLEF_OR_KEY),
                symbols =
                        (List<Object>)
                                c.invoke(null, p.labels, W, H, OmrMeasurePostProcessor.SYMBOL);
        Object chosen = null;
        for (Object h : heads) {
            Method x = h.getClass().getDeclaredMethod("centerX");
            x.setAccessible(true);
            float value = ((Number) x.invoke(h)).floatValue();
            if (main ? value > 200 : value < 160) chosen = h;
        }
        assertNotNull(chosen);
        Method call =
                root.getDeclaredMethod(
                        "isOwnedHeaderCrossbar",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        chosen.getClass(),
                        List.class,
                        List.class,
                        List.class,
                        List.class);
        call.setAccessible(true);
        return (Boolean)
                call.invoke(
                        null,
                        p.labels,
                        p.gray,
                        W,
                        H,
                        chosen,
                        List.of(p.staff),
                        heads,
                        clefs,
                        symbols);
    }

    @Test
    public void blurredSharpOwnsItsTinyIslandInBothAngles() throws Exception {
        for (float s : new float[] {-.1f, .1f})
            assertTrue(owned(new Page(s, true, 30, 117, true, true, 0, true, false), false));
    }

    @Test
    public void nearbyPlayedNoteAccidentalKeepsItsIsland() throws Exception {
        assertFalse(owned(new Page(.1f, true, 30, 117, true, true, 0, true, true), false));
    }

    @Test
    public void realFollowingOvalRemains() throws Exception {
        assertFalse(owned(new Page(.1f, true, 30, 117, true, true, 0, true, false), true));
    }

    @Test
    public void unprovedStaffTrackCannotDeleteHead() throws Exception {
        assertFalse(owned(new Page(.1f, false, 30, 117, true, true, 0, true, false), false));
    }

    @Test
    public void wrongSignaturePitchKeepsTheIsland() throws Exception {
        assertFalse(owned(new Page(.1f, true, 30, 117, true, true, G * 2, true, false), false));
    }

    @Test
    public void originalSourceArraysRemain() throws Exception {
        Page p = new Page(-.1f, true, 30, 117, true, true, 0, true, false);
        byte[] mask = p.labels.clone(), gray = p.gray.clone();
        owned(p, false);
        assertArrayEquals(mask, p.labels);
        assertArrayEquals(gray, p.gray);
    }

    @Test
    public void completeSharpMaskBodyIsStillAnOwnedSymbol() throws Exception {
        Page p = new Page(-.1f, true, 30, 117, true, true, 0, true, false);
        int pitch = Math.round(p.top(146));
        for (int y = pitch - 10; y <= pitch + 10; y++)
            for (int x = 141; x <= 152; x++)
                if ((p.gray[y * W + x] & 255) < 60) p.labels[y * W + x] = 2;
        assertTrue(owned(p, false));
    }

    private Page twoSemanticCrossbars(boolean following) throws Exception {
        Page p = new Page(.1f, true, 30, 117, true, true, 0, true, false);
        int pitch = Math.round(p.top(146));
        for (int y = pitch - 9; y <= pitch + 9; y++)
            for (int x = 140; x <= 153; x++)
                p.labels[y * W + x] = (byte) (y == pitch ? 0 : OmrMeasurePostProcessor.NOTEHEAD);
        if (!following)
            for (int y = 0; y < H; y++)
                for (int x = 220; x < 380; x++)
                    if (p.labels[y * W + x] == OmrMeasurePostProcessor.NOTEHEAD)
                        p.labels[y * W + x] = 0;
        return p;
    }

    @Test
    public void twoKeyCrossbarsCannotTruncateEachOthersFollowingNote() throws Exception {
        assertTrue(owned(twoSemanticCrossbars(true), false));
    }

    @Test
    public void twoKeyCrossbarsAloneCannotProveTheFollowingAttack() throws Exception {
        assertFalse(owned(twoSemanticCrossbars(false), false));
    }

    private Page fullSymbolMask(boolean closeNote, int clefHeight) throws Exception {
        Page p = new Page(.1f, true, 30, clefHeight, false, true, 0, false, closeNote);
        int pitch = Math.round(p.top(148));
        // A complete two-spine/two-bridge sharp remains in paper pixels. Its
        // pale connected middle was labeled as one head-sized semantic body.
        for (int y = pitch - 23; y <= pitch + 23; y++)
            for (int x = 137; x <= 158; x++) {
                p.labels[y * W + x] = 0;
                p.gray[y * W + x] = (byte) (134 + (x * 17 + y * 11) % 4);
            }
        for (int y = pitch - 10; y <= pitch + 10; y++)
            for (int x = 137; x <= 158; x++) {
                p.labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                p.gray[y * W + x] = 86;
            }
        for (int x : new int[] {140, 141, 154, 155})
            for (int y = pitch - 23; y <= pitch + 23; y++) p.gray[y * W + x] = 55;
        for (int cy : new int[] {pitch - 5, pitch + 5})
            for (int y = cy - 1; y <= cy + 1; y++)
                for (int x = 137; x <= 158; x++) p.gray[y * W + x] = 55;
        return p;
    }

    @Test
    public void completeBroadSharpMaskUsesTheSamePrintedBodyProof() throws Exception {
        assertTrue(owned(fullSymbolMask(false, 117), false));
    }

    @Test
    public void broadInlineSharpBesideAttackCannotProveKeyOwnership() throws Exception {
        assertFalse(owned(fullSymbolMask(true, 117), false));
    }

    @Test
    public void broadMaskWithoutFollowingAttackCannotProveKeyOwnership() throws Exception {
        Page p = fullSymbolMask(false, 117);
        for (int y = 0; y < H; y++)
            for (int x = 220; x < 380; x++)
                if (p.labels[y * W + x] == OmrMeasurePostProcessor.NOTEHEAD)
                    p.labels[y * W + x] = 0;
        assertFalse(owned(p, false));
    }

    @Test
    public void adjacentPlayedOvalSurvivesBroadSymbolBounds() throws Exception {
        Page p = fullSymbolMask(false, 117);
        for (int y = 0; y < H; y++)
            for (int x = 220; x < 380; x++)
                if (p.labels[y * W + x] == OmrMeasurePostProcessor.NOTEHEAD)
                    p.labels[y * W + x] = 0;
        int hx = 194, hy = Math.round(p.top(hx) + G * 2);
        for (int y = hy - 5; y <= hy + 5; y++)
            for (int x = hx - 8; x <= hx + 8; x++)
                if ((x - hx) * (x - hx) / 64.0 + (y - hy) * (y - hy) / 25.0 <= 1)
                    p.ink(x, y, 2, 45);
        assertTrue(owned(p, false));
        Class<?> root = OmrScoreInterpreter.class;
        Method c =
                root.getDeclaredMethod(
                        "findComponents", byte[].class, int.class, int.class, byte.class);
        c.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> heads =
                (List<Object>) c.invoke(null, p.labels, W, H, OmrMeasurePostProcessor.NOTEHEAD);
        @SuppressWarnings("unchecked")
        List<Object> clefs =
                (List<Object>) c.invoke(null, p.labels, W, H, OmrMeasurePostProcessor.CLEF_OR_KEY);
        @SuppressWarnings("unchecked")
        List<Object> symbols =
                (List<Object>) c.invoke(null, p.labels, W, H, OmrMeasurePostProcessor.SYMBOL);
        Object oval = null;
        for (Object h : heads) {
            Method x = h.getClass().getDeclaredMethod("centerX");
            x.setAccessible(true);
            if (((Number) x.invoke(h)).floatValue() > 180) oval = h;
        }
        assertNotNull(oval);
        Method call =
                root.getDeclaredMethod(
                        "isOwnedHeaderCrossbar",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        oval.getClass(),
                        List.class,
                        List.class,
                        List.class,
                        List.class);
        call.setAccessible(true);
        assertFalse(
                (Boolean)
                        call.invoke(
                                null,
                                p.labels,
                                p.gray,
                                W,
                                H,
                                oval,
                                List.of(p.staff),
                                heads,
                                clefs,
                                symbols));
    }

    @Test
    public void shortHeaderShapeCannotOwnBroadMask() throws Exception {
        assertFalse(owned(fullSymbolMask(false, 55), false));
    }

    @Test
    public void noteOnAnotherRowCannotProveHeaderOwnership() throws Exception {
        Page p = new Page(-.1f, true, 30, 117, true, true, 0, true, false);
        for (int y = 0; y < H; y++)
            for (int x = 220; x < 380; x++) if (p.labels[y * W + x] == 2) p.labels[y * W + x] = 0;
        for (int y = 330; y <= 340; y++) for (int x = 292; x <= 308; x++) p.ink(x, y, 2, 45);
        assertFalse(owned(p, false));
    }
}
