// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** An elongated ribbon with repeated alternating bends, bound to two consecutive printed heads. */
final class WaveGlissDetector {
    record Link(int sourceIndex, int targetIndex) {}

    private WaveGlissDetector() {}

    static List<Link> detect(
            byte[] gray,
            int width,
            int height,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads) {
        if (gray == null
                || gray.length != (long) width * height
                || heads == null
                || heads.size() < 2) return List.of();
        return detectWithRemovedStaffLines(
                width,
                height,
                heads,
                NoteSlideDetector.removeStaffLines(gray, width, height, staffs));
    }

    /** Consumes an owned raster: flood traversal replaces its ink with white. */
    static List<Link> detectWithRemovedStaffLines(
            int width, int height, List<NoteSlideDetector.Head> heads, byte[] clean) {
        int[] queue = new int[clean.length];
        List<Link> result = new ArrayList<>();
        for (int origin = 0; origin < clean.length; origin++) {
            if ((clean[origin] & 255) >= 150) continue;
            int count = 1, read = 0;
            queue[0] = origin;
            clean[origin] = (byte) 255;
            int left = width, right = 0, top = height, bottom = 0;
            double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
            while (read < count) {
                int at = queue[read++], x = at % width, y = at / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                sx += x;
                sy += y;
                sxx += (double) x * x;
                syy += (double) y * y;
                sxy += (double) x * y;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= width || yy < 0 || yy >= height) continue;
                        int next = yy * width + xx;
                        if ((clean[next] & 255) < 150) {
                            clean[next] = (byte) 255;
                            queue[count++] = next;
                        }
                    }
            }
            if (count < 16 || right - left < 8 || bottom - top < 6) continue;
            double cx = sx / count,
                    cy = sy / count,
                    vx = sxx / count - cx * cx,
                    vy = syy / count - cy * cy,
                    cov = sxy / count - cx * cy;
            double angle = .5 * Math.atan2(2 * cov, vx - vy),
                    ux = Math.cos(angle),
                    uy = Math.sin(angle);
            double length = Math.hypot(right - left, bottom - top);
            double residual = Math.sqrt(Math.max(0, (vx + vy - Math.hypot(vx - vy, 2 * cov)) / 2));
            if (Math.abs(ux) < .12 || Math.abs(uy) < .08 || length < 20 || residual > 5) continue;
            int source = -1, target = -1;
            double best = Double.POSITIVE_INFINITY;
            double slope = uy / ux;
            double leftY = cy + (left - cx) * slope, rightY = cy + (right - cx) * slope;
            for (int a = 0; a < heads.size(); a++) {
                var first = heads.get(a);
                float gap = first.gap();
                if (first.staff() < 0
                        || first.x() > left - gap * .35f
                        || left - first.x() > gap * 3
                        || length < gap * 2
                        || length > gap * 12
                        || residual > gap * .28
                        || Math.hypot(left - first.x(), leftY - first.y()) > gap * 3) continue;
                for (int b = 0; b < heads.size(); b++) {
                    var last = heads.get(b);
                    if (last.staff() != first.staff()
                            || last.measure() != first.measure()
                            || last.x() < right + gap * .25f
                            || last.x() - right > gap * 3
                            || Math.hypot(last.x() - right, last.y() - rightY) > gap * 3
                            || (last.y() - first.y()) * slope <= 0) continue;
                    boolean between = false;
                    for (int k = 0; k < heads.size(); k++) {
                        var other = heads.get(k);
                        if (k != a
                                && k != b
                                && other.staff() == first.staff()
                                && other.measure() == first.measure()
                                && other.x() >= first.x() - gap * .5f
                                && other.x() <= last.x() + gap * .5f) {
                            between = true;
                            break;
                        }
                    }
                    if (between) continue;
                    double score =
                            Math.hypot(left - first.x(), leftY - first.y())
                                    + Math.hypot(last.x() - right, last.y() - rightY);
                    if (score < best) {
                        best = score;
                        source = a;
                        target = b;
                    }
                }
            }
            if (source < 0) continue;
            float gap = heads.get(source).gap();
            if (!alternatingRibbon(queue, count, width, cx, cy, ux, uy, gap)) continue;
            Link link = new Link(source, target);
            if (!result.contains(link)) result.add(link);
        }
        return List.copyOf(result);
    }

    private static boolean alternatingRibbon(
            int[] pixels,
            int count,
            int width,
            double cx,
            double cy,
            double ux,
            double uy,
            float gap) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            int x = pixels[i] % width, y = pixels[i] / width;
            double along = (x - cx) * ux + (y - cy) * uy;
            min = Math.min(min, along);
            max = Math.max(max, along);
        }
        int bins = (int) Math.ceil(max - min) + 1;
        if (bins < 12 || bins > gap * 15) return false;
        double[] sums = new double[bins], low = new double[bins], high = new double[bins];
        int[] counts = new int[bins];
        Arrays.fill(low, Double.POSITIVE_INFINITY);
        Arrays.fill(high, Double.NEGATIVE_INFINITY);
        for (int i = 0; i < count; i++) {
            int x = pixels[i] % width, y = pixels[i] / width;
            int bin = Math.min(bins - 1, (int) Math.floor((x - cx) * ux + (y - cy) * uy - min));
            double normal = -(x - cx) * uy + (y - cy) * ux;
            sums[bin] += normal;
            counts[bin]++;
            low[bin] = Math.min(low[bin], normal);
            high[bin] = Math.max(high[bin], normal);
        }
        int sign = 0, changes = 0, covered = 0;
        double threshold = Math.max(.65, gap * .055);
        for (int i = 2; i < bins - 2; i++) {
            if (counts[i] == 0) continue;
            if (high[i] - low[i] > gap * .55) return false;
            covered++;
            double sum = 0;
            int samples = 0;
            for (int j = Math.max(0, i - 1); j <= Math.min(bins - 1, i + 1); j++)
                if (counts[j] > 0) {
                    sum += sums[j] / counts[j];
                    samples++;
                }
            double normal = sum / samples;
            int next = normal > threshold ? 1 : normal < -threshold ? -1 : 0;
            if (next != 0) {
                if (sign != 0 && next != sign) changes++;
                sign = next;
            }
        }
        return covered >= (bins - 4) * .9 && changes >= 4;
    }
}
