// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural header bodies, shaded paper and sharp crossbars. */
public class CurvedHeaderRawSharpTest {
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
                f.set(staff, StaffPitchTrack.linear(W, 214, G, slope));
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

    private Page normal(float slope, boolean blur, boolean tinyHead) throws Exception {
        return new Page(slope, true, 30, 117, true, blur, 0, tinyHead, false);
    }

    @Test
    public void symbolTrebleAndRawSharpRestoreUpwardHeader() throws Exception {
        assertEquals(
                List.of(new ScoreKeyChange(0, 1)),
                new Page(
                                .1f,
                                true,
                                30,
                                117,
                                true,
                                false,
                                0,
                                false,
                                false,
                                OmrMeasurePostProcessor.SYMBOL)
                        .keys());
    }

    @Test
    public void symbolTrebleAndRawSharpRestoreDownwardHeader() throws Exception {
        assertEquals(
                List.of(new ScoreKeyChange(0, 1)),
                new Page(
                                -.1f,
                                true,
                                30,
                                117,
                                true,
                                false,
                                0,
                                false,
                                false,
                                OmrMeasurePostProcessor.SYMBOL)
                        .keys());
    }

    @Test
    public void semanticClefRetainsSameRawSharpProof() throws Exception {
        assertEquals(List.of(new ScoreKeyChange(0, 1)), normal(.1f, false, false).keys());
    }

    @Test
    public void blurredMiddleStillRequiresTwoCompleteCrossbars() throws Exception {
        assertEquals(List.of(new ScoreKeyChange(0, 1)), normal(.1f, true, false).keys());
    }

    @Test
    public void tinySharpIslandCannotTruncateTheHeader() throws Exception {
        assertEquals(List.of(new ScoreKeyChange(0, 1)), normal(.1f, true, true).keys());
    }

    @Test
    public void fragmentedTrebleUsesTheLocalRuleFrame() throws Exception {
        Page p = normal(.1f, true, false);
        int first = Math.round(p.top(100) + G * 1.3f);
        for (int y = first; y < first + 3; y++)
            for (int x = 85; x <= 115; x++) {
                p.labels[y * W + x] = 0;
                p.gray[y * W + x] = (byte) 135;
            }
        assertEquals(List.of(new ScoreKeyChange(0, 1)), p.keys());
    }

    @Test
    public void absentTrackCannotInventAnOffsetHeader() throws Exception {
        assertTrue(new Page(.1f, false, 30, 117, true, false, 0, false, false).keys().isEmpty());
    }

    @Test
    public void narrowBracketCannotOwnRawKey() throws Exception {
        assertTrue(new Page(.1f, true, 2, 117, true, false, 0, false, false).keys().isEmpty());
    }

    @Test
    public void shortSymbolCannotOwnRawKey() throws Exception {
        assertTrue(new Page(.1f, true, 30, 40, true, false, 0, false, false).keys().isEmpty());
    }

    @Test
    public void completeClefWithoutSharpCannotInventKey() throws Exception {
        assertTrue(new Page(.1f, true, 30, 117, false, false, 0, false, false).keys().isEmpty());
    }

    @Test
    public void rawSharpOutsideFirstSignaturePitchCannotSeedKey() throws Exception {
        assertTrue(new Page(.1f, true, 30, 117, true, false, G * 2, false, false).keys().isEmpty());
    }

    @Test
    public void closeNoteAccidentalCannotInventKey() throws Exception {
        assertTrue(new Page(.1f, true, 30, 117, true, false, 0, false, true).keys().isEmpty());
    }

    @Test
    public void keyDetectionPreservesBothSourceArrays() throws Exception {
        Page p = normal(-.1f, true, true);
        byte[] labels = p.labels.clone(), gray = p.gray.clone();
        p.keys();
        assertArrayEquals(labels, p.labels);
        assertArrayEquals(gray, p.gray);
    }
}
