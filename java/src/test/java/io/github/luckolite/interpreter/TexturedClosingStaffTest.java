// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class TexturedClosingStaffTest {
    @org.junit.Test
    public void originalClosingGeometry() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        int w = 1000, h = 300;
        for (int kind = 0; kind < 3; kind++) {
            byte[] g = new byte[w * h], l = new byte[g.length];
            Arrays.fill(g, (byte) 250);
            for (int y = 70; y < 170; y++)
                for (int x = 480; x < 740; x++)
                    g[y * w + x] =
                            (byte) Math.round(200 + 20 * Math.sin(x / 3.) + 10 * Math.cos(y / 9.));
            for (int line = 0; line < 5; line++)
                for (int x = 30; x <= 770; x++) {
                    int at = (90 + line * 12) * w + x;
                    l[at] = 4;
                    if (kind != 2 || x < 480) g[at] = 20;
                }
            for (int x = 490; x < 720; x += 20)
                for (int dx = 0; dx < 3; dx++)
                    for (int y = 80; y <= 150; y++) {
                        g[y * w + x + dx] = 20;
                        l[y * w + x + dx] = 1;
                    }
            if (kind != 1)
                for (int x = 760; x <= 760; x++)
                    for (int y = 90; y <= 138; y++) {
                        g[y * w + x] = 20;
                        l[y * w + x] = 1;
                    }
            var m = OmrMeasurePostProcessor.process(l, g, w, h);
            float edge = m.get(m.size() - 1).right();
            if (kind == 2 ? edge > .55f : edge < .74f)
                throw new AssertionError("kind=" + kind + " edge=" + edge);
        }
    }
}
