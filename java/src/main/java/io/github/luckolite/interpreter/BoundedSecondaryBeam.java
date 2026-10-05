// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Untouched raster proof for a three-attack secondary beam inside a longer beam. */
final class BoundedSecondaryBeam {
    private record Stem(int x, float headY, int direction) {}

    private record Rail(float y, float slope) {}

    private static final class ProbeSigns {
        private static final int[] VALUES = {-1, 1};
    }

    static boolean proves(byte[] gray, int width, int height, float[] xs, float[] ys, float gap) {
        if (gray == null
                || width < 1
                || height < 1
                || (long) width * height != gray.length
                || xs == null
                || ys == null
                || xs.length != 3
                || ys.length != 3
                || !Float.isFinite(gap)
                || gap < 6) return false;
        for (int i = 0; i < 3; i++)
            if (!Float.isFinite(xs[i])
                    || !Float.isFinite(ys[i])
                    || xs[i] < 0
                    || xs[i] >= width
                    || ys[i] < 0
                    || ys[i] >= height) return false;
        if (xs[1] - xs[0] < gap * .8f
                || xs[2] - xs[1] < gap * .8f
                || xs[2] - xs[0] < gap * 2
                || xs[2] - xs[0] > gap * 12) return false;
        for (int direction : ProbeSigns.VALUES) {
            Stem[] stems = new Stem[3];
            for (int i = 0; i < 3; i++)
                stems[i] = stem(gray, width, height, xs[i], ys[i], gap, direction);
            if (stems[0] == null || stems[1] == null || stems[2] == null) continue;
            if (stems[1].x() - stems[0].x() < gap * .7f || stems[2].x() - stems[1].x() < gap * .7f)
                continue;
            for (int distance = Math.round(gap * 1.7f); distance <= gap * 5; distance++) {
                float y = stems[0].headY() + direction * distance;
                for (int angle = -8; angle <= 8; angle++) {
                    Rail secondary = new Rail(y, angle * .04f);
                    if (!rail(gray, width, height, stems, secondary, gap)) continue;
                    if (!stops(gray, width, height, stems, secondary, gap)) continue;
                    for (int separation = Math.round(gap * .4f);
                            separation <= gap * 1.15f;
                            separation++) {
                        Rail main = new Rail(y + direction * separation, secondary.slope());
                        if (rail(gray, width, height, stems, main, gap)
                                && separated(gray, width, height, stems, secondary, main, gap)
                                && continues(gray, width, height, stems, main, gap)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static Stem stem(byte[] g, int w, int h, float hx, float hy, float gap, int direction) {
        int best = 0, bestX = -1;
        for (int x = Math.max(0, Math.round(hx - gap * .85f));
                x <= Math.min(w - 1, Math.round(hx + gap * .85f));
                x++) {
            int ink = 0, blanks = 0;
            for (int d = 0; d <= gap * 5; d++) {
                int y = Math.round(hy) + direction * d;
                if (y < 0 || y >= h) break;
                if (dark(g, w, h, x, y)) {
                    ink++;
                    blanks = 0;
                } else if (++blanks > 1) break;
                if (d >= gap * 2 && ink >= d * .85f && d > best) {
                    best = d;
                    bestX = x;
                }
            }
        }
        return bestX < 0 ? null : new Stem(bestX, hy, direction);
    }

    private static boolean rail(byte[] g, int w, int h, Stem[] stems, Rail rail, float gap) {
        int a = stems[0].x(), b = stems[2].x();
        if (b - a < gap * 2) return false;
        int radius = Math.max(1, Math.round(gap * .08f));
        int covered = 0, total = 0;
        for (int x = a + 2; x <= b - 2; x++) {
            total++;
            if (core(g, w, h, x, rail.y() + (x - a) * rail.slope(), radius)) covered++;
        }
        if (total == 0 || covered < total * .94f) return false;
        for (Stem stem : stems) {
            float y = rail.y() + (stem.x() - a) * rail.slope();
            float d = stem.direction() * (y - stem.headY());
            if (d < gap * 1.5f || d > gap * 5) return false;
            int dark = 0, totalStem = 0;
            for (int distance = 0; distance <= Math.abs(y - stem.headY()); distance++) {
                totalStem++;
                if (dark(g, w, h, stem.x(), Math.round(stem.headY()) + stem.direction() * distance))
                    dark++;
            }
            if (dark < totalStem * .85f) return false;
        }
        return true;
    }

    private static boolean stops(byte[] g, int w, int h, Stem[] stems, Rail rail, float gap) {
        int radius = Math.max(1, Math.round(gap * .08f));
        for (int side : ProbeSigns.VALUES) {
            int edge = stems[side < 0 ? 0 : 2].x(), covered = 0, total = 0;
            for (int d = Math.round(gap * .35f); d <= gap; d++) {
                int x = edge + side * d;
                if (x < 0 || x >= w) return false;
                total++;
                if (core(g, w, h, x, rail.y() + (x - stems[0].x()) * rail.slope(), radius))
                    covered++;
            }
            if (total == 0 || covered > total * .25f) return false;
        }
        return true;
    }

    private static boolean separated(
            byte[] g, int w, int h, Stem[] stems, Rail a, Rail b, float gap) {
        int clear = 0, total = 0;
        for (int x = stems[0].x() + Math.round(gap * .4f); x < stems[2].x() - gap * .4f; x++) {
            if (Math.abs(x - stems[1].x()) < gap * .2f) continue;
            total++;
            float y = (a.y() + b.y()) * .5f + (x - stems[0].x()) * a.slope();
            if (!dark(g, w, h, x, Math.round(y))) clear++;
        }
        return total > 0 && clear >= total * .7f;
    }

    private static boolean continues(byte[] g, int w, int h, Stem[] stems, Rail main, float gap) {
        int radius = Math.max(1, Math.round(gap * .08f));
        for (int side : ProbeSigns.VALUES) {
            int edge = stems[side < 0 ? 0 : 2].x(), covered = 0, total = 0;
            for (int d = Math.round(gap * .35f); d <= gap; d++) {
                int x = edge + side * d;
                if (x < 0 || x >= w) break;
                total++;
                if (core(g, w, h, x, main.y() + (x - stems[0].x()) * main.slope(), radius))
                    covered++;
            }
            if (total > gap * .5f && covered >= total * .94f) return true;
        }
        return false;
    }

    private static boolean core(byte[] g, int w, int h, int x, float y, int radius) {
        int center = Math.round(y);
        for (int d = -radius; d <= radius; d++) if (!dark(g, w, h, x, center + d)) return false;
        return true;
    }

    private static boolean dark(byte[] g, int w, int h, int x, int y) {
        return x >= 0 && x < w && y >= 0 && y < h && (g[y * w + x] & 255) < 165;
    }
}
