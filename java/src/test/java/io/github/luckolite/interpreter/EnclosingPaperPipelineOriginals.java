// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original two-note raster; a separated accent crosses a former fixed paper sample. */
public final class EnclosingPaperPipelineOriginals {
    static final int W = 240, H = 180;
    static int checks;

    static void check(boolean v, String m) {
        checks++;
        if (!v) throw new AssertionError(m);
    }

    static PrintedBeamSource.Capture scene(boolean foreign) {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 245);
        int gap = 14;
        for (int y = 74; y <= 130; y += 14) for (int x = 4; x < W - 4; x++) raw[y * W + x] = 0;
        for (int cx : new int[] {70, 140}) {
            for (int y = 124; y <= 136; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if ((x - cx) * (x - cx) / (8.6 * 8.6) + (y - 130) * (y - 130) / (5.6 * 5.6)
                            <= 1) raw[y * W + x] = 0;
            int stem = Math.round(cx + gap * .6f);
            for (int y = 45; y <= 130; y++)
                for (int x = stem; x <= stem + 1; x++)
                    raw[y * W + x] = (byte) Math.min(raw[y * W + x] & 255, 180);
        }
        for (int y = 45; y <= 49; y++) for (int x = 78; x <= 149; x++) raw[y * W + x] = (byte) 180;
        if (foreign) for (int x = 89; x <= 94; x++) raw[97 * W + x] = 80;
        var notes =
                List.of(
                        new ScoreNoteEvent(0, 70f / W, 0, 0, 1, 130f / H, false, 0, 1),
                        new ScoreNoteEvent(0, 140f / W, 0, 0, 1, 130f / H, false, 0, 1));
        var score = new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
        byte[] before = raw.clone();
        var cap =
                PrintedBeamSource.capture(
                        score,
                        new byte[raw.length],
                        raw,
                        W,
                        H,
                        List.of(new PlayingTechniqueDetector.Staff(74, 130, 14, 0, 1)),
                        0,
                        8000000,
                        256000000,
                        64000000);
        check(Arrays.equals(before, raw), "immutable original scene");
        return cap;
    }

    public static void main(String[] args) {
        for (boolean foreign : new boolean[] {false, true}) {
            var c = scene(foreign);
            System.out.println("foreign=" + foreign + " " + c.statistics());
            check(c.statistics().refusal().isEmpty(), "complete source allowance");
            check(
                    c.source().groups().size() == 1,
                    "two printed notes remain one connected beam with foreign=" + foreign);
        }
        System.out.println("PASS " + checks + " complete source scene assertions");
    }
}
