// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Synthetic tempo symbols with a separate score stem entering the bottom of the search crop. */
public class ClippedTempoStemTest {
    private ScoreTempoChange reading(boolean dotted, boolean lowerStem) {
        int width = 300, height = 160;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int y = 42; y <= 79; y++) for (int x = 113; x <= 114; x++) gray[y * width + x] = 0;
        for (int y = 74; y <= 84; y++)
            for (int x = 99; x <= 115; x++)
                if (Math.pow((x - 107) / 8.0, 2) + Math.pow((y - 79) / 5.0, 2) <= 1)
                    gray[y * width + x] = 0;
        if (dotted)
            for (int y = 77; y <= 81; y++) for (int x = 121; x <= 125; x++) gray[y * width + x] = 0;
        if (lowerStem)
            for (int y = 83; y <= 130; y++)
                for (int x = 121; x <= 123; x++) gray[y * width + x] = 0;
        for (int y : new int[] {70, 78})
            for (int yy = y; yy < y + 3; yy++)
                for (int x = 140; x < 154; x++) gray[yy * width + x] = 0;
        for (int y = 60; y <= 80; y++) for (int x = 170; x <= 173; x++) gray[y * width + x] = 0;
        var result =
                TempoChangeDetector.detect(
                        List.of(
                                new MeasureNumberReconciler.NumberToken(
                                        180,
                                        170f / width,
                                        60f / height,
                                        180f / width,
                                        80f / height)),
                        gray,
                        width,
                        height,
                        List.of(new MeasureRegion(.2f, .9f, .65f, .95f)));
        assertEquals(1, result.size());
        return result.get(0);
    }

    @Test
    public void clippedScoreStemDoesNotDotPlainQuarter() {
        var tempo = reading(false, true);
        assertEquals(1.0, tempo.beatUnit(), .001);
        assertEquals(180.0, tempo.bpm(), .001);
    }

    @Test
    public void completeDotSurvivesNearbyClippedStem() {
        var tempo = reading(true, true);
        assertEquals(1.5, tempo.beatUnit(), .001);
        assertEquals(270.0, tempo.bpm(), .001);
    }

    @Test
    public void plainQuarterWithoutNearbyInkStaysPlain() {
        assertEquals(1.0, reading(false, false).beatUnit(), .001);
    }
}
