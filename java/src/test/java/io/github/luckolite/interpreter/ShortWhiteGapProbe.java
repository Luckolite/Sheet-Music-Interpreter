// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.nio.file.*;

/** Original shorter white separations around an independent horizontal rail. */
public class ShortWhiteGapProbe {
    static boolean connected(byte[] g, int sx, int sy, int tx, int ty) {
        int w = 320, h = 360;
        boolean[] seen = new boolean[w * h];
        int[] q = new int[g.length];
        int at = 0, n = 1;
        q[0] = sy * w + sx;
        seen[q[0]] = true;
        while (at < n) {
            int z = q[at++], x = z % w, y = z / w;
            if (x == tx && y == ty) return true;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                    int p = yy * w + xx;
                    if (!seen[p] && (g[p] & 255) < 170) {
                        seen[p] = true;
                        q[n++] = p;
                    }
                }
        }
        return false;
    }

    public static void main(String[] a) throws Exception {
        StringBuilder text = new StringBuilder();
        for (int above : new int[] {1, 2})
            for (int below : new int[] {1, 2})
                for (boolean staff : new boolean[] {false, true}) {
                    var d = TallChordAdversarialTest.independentVoices(false);
                    for (int y = 179; y <= 185; y++)
                        for (int x = 100; x <= 101; x++) d.pixel(x, y, 1);
                    for (int y = 181 - above; y <= 180; y++)
                        for (int x = 100; x <= 101; x++) {
                            d.gray[y * d.W + x] = (byte) 255;
                            d.labels[y * d.W + x] = 0;
                        }
                    for (int y = 184; y <= 183 + below; y++)
                        for (int x = 100; x <= 101; x++) {
                            d.gray[y * d.W + x] = (byte) 255;
                            d.labels[y * d.W + x] = 0;
                        }
                    if (staff)
                        for (int y = 181; y <= 183; y++)
                            for (int x = 20; x <= 300; x++) d.pixel(x, y, 4);
                    else
                        for (int y = 181; y <= 183; y++)
                            for (int x = 100; x <= 101; x++) {
                                d.gray[y * d.W + x] = (byte) 255;
                                d.labels[y * d.W + x] = 0;
                            }
                    boolean raw = connected(d.gray, 100, 178, 100, 186);
                    int beam = d.run().get(0).beamCount();
                    text.append("white=")
                            .append(above)
                            .append('+')
                            .append(below)
                            .append(" staff=")
                            .append(staff)
                            .append(" raw8Connected=")
                            .append(raw)
                            .append(" upperBeam=")
                            .append(beam)
                            .append('\n');
                }
        Files.writeString(Path.of(a[0]), text.toString());
        System.out.print(text);
    }
}
