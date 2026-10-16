// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class SharedHeadRecognitionTest {
    @org.junit.Test
    public void originalRecognitionControls() throws Exception {
        main(new String[0]);
    }

    @org.junit.Test
    public void labelsWithoutRawPixelsKeepLegacyHeads() {
        recognize(false, false);
        for (int i = 0; i < labels.length; i++) if (labels[i] == 5) labels[i] = 1;
        var notes = OmrScoreInterpreter.extract(
                labels, null, W, H, List.of(new MeasureRegion(.025f, .975f, .1f, .85f)));
        org.junit.Assert.assertEquals(8, notes.size());
        org.junit.Assert.assertTrue(notes.stream().anyMatch(n -> n.beamCount() > 0));
    }

    static final int W = 800, H = 280;
    static byte[] labels, gray;

    static void rect(int x, int y, int w, int h, int label) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) {
                labels[yy * W + xx] = (byte) label;
                gray[yy * W + xx] = 0;
            }
    }

    static void head(int x, int y) {
        for (int yy = y - 7; yy <= y + 7; yy++)
            for (int xx = x - 10; xx <= x + 10; xx++)
                if ((xx - x) * (xx - x) / 100f + (yy - y) * (yy - y) / 49f <= 1) {
                    labels[yy * W + xx] = 2;
                    gray[yy * W + xx] = 0;
                }
    }

    static List<ScoreNoteEvent> recognize(boolean independent, boolean flagged) {
        labels = new byte[W * H];
        gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int i = 0; i < 5; i++) rect(20, 80 + 16 * i, 760, 1, 4);
        for (int i = 0; i < 8; i++) {
            int x = 80 + i * 85, y = i % 2 == 0 ? 144 : 128;
            rect(x + 9, 88, 2, y - 87, 1);
            if (i % 2 == 0) {
                rect(x + 9, 88, 87, 6, 5);
                if (independent) {
                    rect(x - 10, y, 2, 49, 1);
                    if (flagged) rect(x - 8, 180, 13, 12, 5);
                }
            }
            head(x, y);
        }
        return OmrScoreInterpreter.extract(
                labels, gray, W, H, List.of(new MeasureRegion(.025f, .975f, .1f, .85f)));
    }

    public static void main(String[] args) {
        for (boolean independent : new boolean[] {false, true}) {
            var notes = recognize(independent, false);
            long quarters =
                    notes.stream()
                            .filter(n -> n.beamCount() == 0 && n.unbeamedDurationBeats() == 1)
                            .count();
            long eighths = notes.stream().filter(n -> n.beamCount() == 1).count();
            System.out.println(
                    "independent="
                            + independent
                            + " notes="
                            + notes.size()
                            + " quarters="
                            + quarters
                            + " eighths="
                            + eighths);
            if (quarters != (independent ? 4 : 0) || eighths != 8)
                throw new AssertionError("Shared-head recognition mismatch");
        }
        var flagged = recognize(true, true);
        if (flagged.stream().anyMatch(n -> n.beamCount() == 0 && n.unbeamedDurationBeats() == 1))
            throw new AssertionError("Flagged stem became quarter");
        System.out.println("Original shared-head recognition and single-stem/flag controls passed");
    }
}
