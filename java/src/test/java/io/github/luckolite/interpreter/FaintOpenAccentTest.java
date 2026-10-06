// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Independently generated two-tone strokes, not scanned score pixels. */
public class FaintOpenAccentTest {
    private int detect(
            boolean reverse, boolean parallel, boolean notation, boolean paleOnly, boolean far) {
        int w = 1200, h = 1000;
        byte[] gray = new byte[w * h], labels = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int x = 0; x < 25; x++)
            for (int y = 0; y < 13; y++) {
                int axis = reverse ? 24 - x : x;
                double upper = 1 + (parallel ? 0 : 5 * axis / 24.0),
                        lower = 11 - (parallel ? 0 : 5 * axis / 24.0);
                if (Math.abs(y - upper) > 1.25 && Math.abs(y - lower) > 1.25) continue;
                int p = (100 + y) * w + 100 + x;
                gray[p] = (byte) (!paleOnly && axis < 10 ? 120 : 175);
                if (notation) labels[p] = OmrMeasurePostProcessor.NOTEHEAD;
            }
        byte[] grayCopy = gray.clone(), labelCopy = labels.clone();
        int marks =
                NoteArticulationDetector.detect(
                        labels,
                        gray,
                        w,
                        h,
                        List.of(new NoteArticulationDetector.Anchor(112, far ? 220 : 150, 16, 0)))[
                        0];
        assertArrayEquals(grayCopy, gray);
        assertArrayEquals(labelCopy, labels);
        return marks;
    }

    @Test
    public void paleJoiningArmsRestoreAccent() {
        assertTrue((detect(false, false, false, false, false) & NoteArticulation.ACCENT) != 0);
    }

    @Test
    public void reverseInkDoesNotBecomeAccent() {
        assertEquals(0, detect(true, false, false, false, false) & NoteArticulation.ACCENT);
    }

    @Test
    public void parallelInkDoesNotBecomeAccent() {
        assertEquals(0, detect(false, true, false, false, false) & NoteArticulation.ACCENT);
    }

    @Test
    public void semanticNotationRemainsVetoed() {
        assertEquals(0, detect(false, false, true, false, false) & NoteArticulation.ACCENT);
    }

    @Test
    public void noDarkSeedDoesNotBecomeAccent() {
        assertEquals(0, detect(false, false, false, true, false) & NoteArticulation.ACCENT);
    }

    @Test
    public void distantMarkIsNotAssigned() {
        assertEquals(0, detect(false, false, false, false, true) & NoteArticulation.ACCENT);
    }

    @Test
    public void rejectedComponentThenAccentKeepsIndependentPixelsAcrossReuse() throws Exception {
        int w = 1200, h = 1000;
        byte[] gray = new byte[w * h], labels = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int y = 10; y < 25; y++) for (int x = 10; x < 38; x++) gray[y * w + x] = 120;
        int count = 0;
        for (int x = 0; x < 25; x++)
            for (int y = 0; y < 13; y++) {
                double upper = 1 + 5 * x / 24.0, lower = 11 - 5 * x / 24.0;
                if (Math.abs(y - upper) > 1.25 && Math.abs(y - lower) > 1.25) continue;
                gray[(100 + y) * w + 100 + x] = (byte) (x < 10 ? 120 : 175);
                count++;
            }
        byte[] before = gray.clone(), labelsBefore = labels.clone();
        boolean[] seen = new boolean[gray.length];
        int[] queue = new int[gray.length];
        java.lang.reflect.Method faint =
                NoteArticulationDetector.class.getDeclaredMethod(
                        "faintAccentGlyphs",
                        byte[].class,
                        int.class,
                        int.class,
                        boolean[].class,
                        int[].class);
        faint.setAccessible(true);
        List<?> first = (List<?>) faint.invoke(null, gray, w, h, seen, queue);
        assertEquals(1, first.size());
        Object glyph = first.get(0);
        Class<?> type = glyph.getClass();
        java.lang.reflect.Method pixelAccessor = type.getDeclaredMethod("pixels");
        pixelAccessor.setAccessible(true);
        int[] retained = (int[]) pixelAccessor.invoke(glyph), retainedBefore = retained.clone();
        assertEquals(count, retained.length);
        int[] bounds = {100, 100, 124, 112, count};
        String[] names = {"left", "top", "right", "bottom", "count"};
        for (int i = 0; i < names.length; i++) {
            java.lang.reflect.Method accessor = type.getDeclaredMethod(names[i]);
            accessor.setAccessible(true);
            assertEquals(bounds[i], ((Integer) accessor.invoke(glyph)).intValue());
        }
        Arrays.fill(queue, Integer.MIN_VALUE);
        assertArrayEquals(retainedBefore, retained);
        List<?> repeated = (List<?>) faint.invoke(null, gray, w, h, seen, queue);
        assertEquals(1, repeated.size());
        assertArrayEquals(retainedBefore, (int[]) pixelAccessor.invoke(repeated.get(0)));
        assertTrue(
                (NoteArticulationDetector.detect(
                                        labels,
                                        gray,
                                        w,
                                        h,
                                        List.of(
                                                new NoteArticulationDetector.Anchor(
                                                        112, 150, 16, 0)))[0]
                                & NoteArticulation.ACCENT)
                        != 0);
        assertArrayEquals(before, gray);
        assertArrayEquals(labelsBefore, labels);
        Arrays.fill(gray, (byte) 255);
        assertTrue(((List<?>) faint.invoke(null, gray, w, h, seen, queue)).isEmpty());
        assertArrayEquals(retainedBefore, retained);
    }
}
