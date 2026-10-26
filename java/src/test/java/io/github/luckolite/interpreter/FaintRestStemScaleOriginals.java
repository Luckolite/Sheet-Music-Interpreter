// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Independent scale/phase scenes, including disconnected and under-supported ownership. */
public final class FaintRestStemScaleOriginals {
    static int checks;

    static void check(boolean v, String m) {
        checks++;
        if (!v) throw new AssertionError(m);
    }

    static final int W = 240, H = 240;

    static final class Scene {
        final byte[] raw = new byte[W * H];
        final ScoreNoteEvent note;
        final List<ScoreNoteEvent> notes;
        final float gap, cx, cy;
        final int left, right, minY;

        Scene(float gap, float phase, int shade, boolean detached, boolean incompleteHead) {
            this.gap = gap;
            cx = 100 + phase;
            cy = 50 + phase;
            left = Math.round(cx - gap * .95f);
            right = Math.round(cx + gap * .3f);
            minY = Math.round(cy + gap * 2.5f);
            Arrays.fill(raw, (byte) 245);
            var runs = new ArrayList<int[]>();
            double major = gap * .625, minor = gap * .3125;
            for (int y = (int) Math.floor(cy - gap); y <= Math.ceil(cy + gap); y++) {
                int a = -1, b = -1;
                for (int x = (int) Math.floor(cx - gap); x <= Math.ceil(cx + gap); x++) {
                    double dx = x - cx,
                            dy = y - cy,
                            u = dx * .94 - dy * .34,
                            v = dx * .34 + dy * .94;
                    if (u * u / (major * major) + v * v / (minor * minor) <= 1) {
                        raw[y * W + x] = 80;
                        if (a < 0) a = x;
                        b = x;
                    }
                }
                if (a >= 0) runs.add(new int[] {y, a, b});
            }
            int sx = Math.round(cx - gap * .625f), thickness = Math.max(1, Math.round(gap * .125f));
            for (int y = Math.round(detached ? cy + gap * 1.8f : cy);
                    y <= minY + Math.round(gap * .7f);
                    y++)
                for (int x = sx; x < sx + thickness; x++)
                    raw[y * W + x] = (byte) Math.min(raw[y * W + x] & 255, shade);
            note = new ScoreNoteEvent(0, cx / W, 0, 0, 1, cy / H, false, 0, 1);
            if (incompleteHead)
                runs =
                        new ArrayList<>(
                                List.of(
                                        new int[] {
                                            Math.round(cy), Math.round(cx), Math.round(cx)
                                        }));
            var origin = RestFigureOrigins.HeadOrigin.accepted(new Object(), raw, W, H, runs);
            var map = new IdentityHashMap<ScoreNoteEvent, RestFigureOrigins.HeadOrigin>();
            map.put(note, origin);
            notes = RestFigureOrigins.bind(List.of(note), map);
        }

        boolean owns() {
            return FaintRestStemInk.owns(
                    raw, W, H, notes, note, left, right, minY, gap, cx, cy, null);
        }
    }

    public static void main(String[] args) {
        for (float gap : new float[] {8, 12, 13.75f, 16, 24, 32})
            for (float phase : new float[] {0, .25f, .5f})
                for (int shade : new int[] {180, 212, 220}) {
                    var scene = new Scene(gap, phase, shade, false, false);
                    byte[] before = scene.raw.clone();
                    check(
                            scene.owns(),
                            "faint attachment g=" + gap + " phase=" + phase + " shade=" + shade);
                    check(Arrays.equals(before, scene.raw), "source immutable");
                    check(
                            !new Scene(gap, phase, shade, true, false).owns(),
                            "independent detached glyph");
                    check(
                            !new Scene(gap, phase, shade, false, true).owns(),
                            "one semantic point cannot claim full head");
                }
        System.out.println("PASS " + checks + " scale/phase ownership assertions");
    }
}
