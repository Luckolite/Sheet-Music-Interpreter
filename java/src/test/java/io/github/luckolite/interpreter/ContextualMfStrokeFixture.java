// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original procedural italic mf strokes; no commercial raster/font/OCR outputs. */
final class ContextualMfStrokeFixture {
    static final int W = 1000, H = 700, G = 16;
    byte[] p;

    void line(int x, int y, int xx, int yy) {
        int n = Math.max(Math.abs(xx - x), Math.abs(yy - y));
        for (int i = 0; i <= n; i++) {
            int a = Math.round(x + (xx - x) * i / (float) Math.max(1, n)),
                    b = Math.round(y + (yy - y) * i / (float) Math.max(1, n));
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) p[(b + dy) * W + a + dx] = 20;
        }
    }

    void draw(int legs, boolean cross) {
        p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        line(420, 206, 405, 242);
        line(420, 206, 421, 202);
        line(421, 202, 424, 200);
        line(424, 200, 427, 201);
        line(427, 201, 429, 204);
        line(405, 242, 403, 246);
        line(403, 246, 399, 247);
        line(399, 247, 397, 245);
        if (cross) line(370, 219, 425, 219);
        for (int i = 0; i < legs; i++) line(370 + i * 14, 219, 363 + i * 14, 236);
    }

    PlayingTechniqueDetector.Word word(String text) {
        return new PlayingTechniqueDetector.Word(text, 359f / W, 197f / H, 433f / W, 252f / H);
    }
}
