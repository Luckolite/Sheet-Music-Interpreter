// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original complete model-mask/raw-page synthetic; calls actual full extraction. */
public final class DenseHeldTieExtractionTest {
    static Map<String, Object> run(int width, boolean fragments, boolean differentPitch) {
        int height = 500, first = 300, last = 1080, y = 220;
        float gap = 14;
        byte[] labels = new byte[width * height], gray = new byte[labels.length];
        Arrays.fill(gray, (byte) 255);
        for (int row = 192; row <= 248; row += 14)
            for (int x = 80; x <= 1520; x++) {
                labels[row * width + x] = 4;
                gray[row * width + x] = 0;
            }
        for (int x : new int[] {first, last}) {
            int cy = differentPitch && x == last ? y - 7 : y;
            for (int dx = -13; dx <= 13; dx++)
                for (int dy = -7; dy <= 7; dy++) {
                    double shape = dx * dx / (13.0 * 13) + dy * dy / (7.0 * 7);
                    if (shape <= 1.15) {
                        labels[(cy + dy) * width + x + dx] = 2;
                        if (shape >= .65) gray[(cy + dy) * width + x + dx] = 0;
                    }
                }
        }
        int left = first + 14, right = last - 14;
        Set<Integer> samples = new HashSet<>();
        for (int i = 0; i < 50; i++)
            samples.add(Math.round(left + (i + .5f) / 50 * (right - left)));
        for (int x = left; x <= right; x++) {
            boolean draw = !fragments;
            if (fragments)
                for (int sample : samples)
                    if (Math.abs(x - sample) <= 1) {
                        draw = true;
                        break;
                    }
            if (!draw) continue;
            float t = (x - left) / (float) (right - left);
            int cy = Math.round(y + gap * (.5f + 1.1f * 4 * t * (1 - t)));
            for (int dy = -1; dy <= 1; dy++) {
                gray[(cy + dy) * width + x] = 0;
                labels[(cy + dy) * width + x] = 5;
            }
        }
        var measures =
                List.of(
                        new MeasureRegion(160f / width, 848f / width, .30f, .62f),
                        new MeasureRegion(848f / width, 1504f / width, .30f, .62f));
        byte[] oldGray = gray.clone(), oldLabels = labels.clone();
        var notes = OmrScoreInterpreter.extract(labels, gray, width, height, measures);
        if (!Arrays.equals(oldGray, gray) || !Arrays.equals(oldLabels, labels))
            throw new AssertionError("Caller pixels changed");
        return Map.of(
                "width",
                width,
                "fragments",
                fragments,
                "differentPitch",
                differentPitch,
                "notes",
                notes,
                "noteCount",
                notes.size(),
                "tieContinuations",
                notes.stream().filter(ScoreNoteEvent::tiedFromPrevious).count());
    }

    @org.junit.Test
    public void longHeldTiesDependOnConnectedInkRatherThanPagePadding() {
        for (int width : new int[] {1600, 2400}) {
            for (int mode = 0; mode < 3; mode++) {
                var result = run(width, mode == 1, mode == 2);
                org.junit.Assert.assertEquals(
                        "Both written heads retained", 2, result.get("noteCount"));
                org.junit.Assert.assertEquals(
                        "Only a connected same-pitch curve ties",
                        mode == 0 ? 1L : 0L,
                        result.get("tieContinuations"));
            }
        }
    }
}
