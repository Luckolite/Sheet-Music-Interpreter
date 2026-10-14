// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class DetachedDynamicTextTest {
    static void rect(byte[] g, int w, int l, int t, int r, int b, int value) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * w + x] = (byte) value;
    }

    @org.junit.Test
    public void originalControls() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        int checks = 0;
        for (int scale : new int[] {1, 2, 3})
            for (int kind = 0; kind < 6; kind++) {
                int w = 240 * scale, h = 160 * scale;
                byte[] g = new byte[w * h];
                Arrays.fill(g, (byte) 255);
                int l = 60 * scale, t = 60 * scale, r = 150 * scale, b = 110 * scale;
                if (kind != 5) {
                    rect(g, w, 80 * scale, 72 * scale, 85 * scale, 96 * scale, 0);
                    rect(g, w, 80 * scale, 76 * scale, 110 * scale, 81 * scale, 0);
                    rect(g, w, 110 * scale, 72 * scale, 115 * scale, 96 * scale, 0);
                }
                if (kind != 1 && kind != 5)
                    rect(
                            g,
                            w,
                            80 * scale,
                            30 * scale,
                            82 * scale,
                            (kind == 3 ? 79 : 62) * scale,
                            0);
                if (kind == 2) rect(g, w, 81 * scale, 62 * scale, 81 * scale, 74 * scale, 100);
                if (kind == 4) rect(g, w, 60 * scale, 63 * scale, 150 * scale, 72 * scale, 100);
                byte[] before = g.clone();
                var word =
                        new PlayingTechniqueDetector.Word(
                                "mf", l / (float) w, t / (float) h, r / (float) w, b / (float) h);
                var staff =
                        new PlayingTechniqueDetector.Staff(
                                10 * scale, 66 * scale, 14 * scale, 0, 1);
                var got = DynamicOcrBounds.clean(List.of(word), g, w, h, List.of(staff));
                boolean wanted = kind == 0 || kind == 1;
                if (got.isEmpty() == wanted)
                    throw new AssertionError("scale=" + scale + " kind=" + kind + " got=" + got);
                if (!Arrays.equals(g, before)) throw new AssertionError("mutated raster");
                if (wanted) {
                    var q = got.get(0);
                    if (!q.text().equals("mf")
                            || q.top() < word.top()
                            || q.bottom() > word.bottom()
                            || q.left() < word.left()
                            || q.right() > word.right())
                        throw new AssertionError("lost evidence or expanded bounds");
                }
                checks++;
            }
        System.out.println("PASS " + checks + " original detached-text/connected-stem controls");
    }
}
