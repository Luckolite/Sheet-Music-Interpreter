// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Independent original rest/fragment counterexamples; no target score material. */
public final class FadedUpStemRestAdversesTest {
    private static final int W = 240, H = 220;
    private static final float GAP = 16;
    private static final int L = 90, T = 112, R = 110, B = 126;

    private static final class Scene {
        final byte[] labels = new byte[W * H], gray = new byte[W * H];

        Scene() {
            Arrays.fill(gray, (byte) 250);
        }

        void pixel(int x, int y, int role, int shade) {
            labels[y * W + x] = (byte) role;
            gray[y * W + x] = (byte) shade;
        }

        void oval() {
            for (int y = T; y <= B; y++)
                for (int x = L; x <= R; x++)
                    if ((x - 100) * (x - 100) / 100.0 + (y - 119) * (y - 119) / 49.0 <= 1)
                        pixel(x, y, 2, 105);
        }

        void vertical(int x, int first, int last, int role, int shade) {
            for (int y = first; y <= last; y++)
                for (int dx = 0; dx < 2; dx++) pixel(x + dx, y, role, shade);
        }

        boolean protects() {
            byte[] oldLabels = labels.clone(), oldGray = gray.clone();
            boolean value =
                    FadedUpStemNoteSupport.proves(
                            labels,
                            gray,
                            W,
                            H,
                            L,
                            T,
                            R,
                            B,
                            GAP,
                            new FadedUpStemNoteSupport.Budget(200000));
            assertArrayEquals(oldLabels, labels);
            assertArrayEquals(oldGray, gray);
            return value;
        }
    }

    @Test
    public void aSlurAlongCompleteBodyDoesNotSupplyAnUpShaft() {
        Scene s = new Scene();
        s.oval();
        for (int x = 96; x <= 146; x++) {
            int y = 100 - Math.round(8 * (float) Math.sin((x - 96) * Math.PI / 50));
            s.pixel(x, y, 1, 90);
            s.pixel(x, y + 1, 1, 90);
        }
        assertFalse(s.protects());
    }

    @Test
    public void aHorizontalBeamFragmentDoesNotSupplyAnUpShaft() {
        Scene s = new Scene();
        s.oval();
        for (int y = 85; y <= 89; y++) for (int x = 95; x <= 145; x++) s.pixel(x, y, 1, 90);
        assertFalse(s.protects());
    }

    @Test
    public void longErasedShaftCannotBeBridgedByItsTwoEnds() {
        Scene s = new Scene();
        s.oval();
        s.vertical(109, 68, 73, 1, 100);
        s.vertical(109, 111, 119, 1, 100);
        // Exactly 37 empty rows, unlike a short washed interruption.
        for (int y = 74; y <= 110; y++) assertEquals(250, s.gray[y * W + 109] & 255);
        assertFalse(s.protects());
    }

    @Test
    public void detachedNeighborShaftCannotOwnTheBody() {
        Scene s = new Scene();
        s.oval();
        s.vertical(128, 68, 119, 1, 100);
        assertFalse(s.protects());
    }

    @Test
    public void semanticShaftOnPaperCannotProtect() {
        Scene s = new Scene();
        s.oval();
        s.vertical(109, 68, 111, 1, 250);
        assertFalse(s.protects());
    }

    @Test
    public void unlabelledRawScratchCannotSupplySemanticShaft() {
        Scene s = new Scene();
        s.oval();
        s.vertical(109, 68, 111, 0, 100);
        assertFalse(s.protects());
    }

    @Test
    public void genuineQuarterRestHookRetainsRestAuthority() {
        var p = new CompactQuarterRestTest.Page(16, true, true, true, false);
        var rests = p.detect();
        assertEquals("Original complete zigzag/hook must really classify", 1, rests.size());
        assertEquals(1, rests.get(0).durationBeats(), 0);
        // Existing original model-head patch on the hook has no actual up shaft.
        assertFalse(
                FadedUpStemNoteSupport.proves(
                        p.labels,
                        p.gray,
                        600,
                        280,
                        151,
                        138,
                        157,
                        146,
                        16,
                        new FadedUpStemNoteSupport.Budget(200000)));
    }

    @Test
    public void genuineEighthRestBulbAndDiagonalTailRemainARest() {
        final int w = 800, h = 240;
        byte[] gray = new byte[w * h], labels = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 20; x <= 780; x++) {
                int at = Math.round(80 + line * 14.25f) * w + x;
                gray[at] = 0;
                labels[at] = 4;
            }
        // Original procedural engraving: filled bulb with descending diagonal tail.
        for (int y = 95; y <= 105; y++)
            for (int x = 171; x <= 183; x++)
                if ((x - 177) * (x - 177) / 36.0 + (y - 100) * (y - 100) / 25.0 <= 1) {
                    gray[y * w + x] = 0;
                    labels[y * w + x] = 2;
                }
        for (int y = 97; y <= 123; y++)
            for (int dx = 0; dx < 2; dx++) {
                int x = 185 - (y - 97) * 10 / 26 + dx;
                gray[y * w + x] = 0;
                labels[y * w + x] = 1;
            }
        var rests =
                SixteenthRestDetector.detect(
                        gray,
                        w,
                        h,
                        List.of(new MeasureRegion(0, 1, .2f, .8f)),
                        List.of(new SixteenthRestDetector.Staff(80, 137, 14.25f, 0, 1)),
                        List.of());
        assertEquals("Complete original rest must really classify", 1, rests.size());
        assertEquals(.5, rests.get(0).durationBeats(), 0);
        assertFalse(
                FadedUpStemNoteSupport.proves(
                        labels,
                        gray,
                        w,
                        h,
                        171,
                        95,
                        183,
                        105,
                        14.25f,
                        new FadedUpStemNoteSupport.Budget(200000)));
    }
}
