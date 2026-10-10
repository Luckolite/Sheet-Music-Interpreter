// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Original-ink evidence for a detached waveform mark beside a printed credit. */
public final class DetachedWaveformCreditMark {
    private DetachedWaveformCreditMark() {}

    private record Part(int left, int top, int right, int bottom, int area) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        float centerY() {
            return (top + bottom) * .5f;
        }
    }

    /** Does not identify text, a contributor role, or a service from its name. */
    public static boolean matches(int[] argb, int width, int height) {
        if (argb == null
                || width < 16
                || height < 16
                || width > 384
                || height > 384
                || (long) width * height != argb.length) return false;
        List<Part> dark = parts(argb, width, height, 128);
        List<Part> light = parts(argb, width, height, 208);
        if (!waveform(dark, width, height)
                || !waveform(light, width, height)
                || dark.size() != light.size()) return false;
        for (int i = 0; i < dark.size(); i++) {
            Part a = dark.get(i), b = light.get(i);
            if (Math.abs(a.left - b.left) > 2
                    || Math.abs(a.top - b.top) > 2
                    || Math.abs(a.right - b.right) > 2
                    || Math.abs(a.bottom - b.bottom) > 2) return false;
        }
        return true;
    }

    private static List<Part> parts(int[] pixels, int width, int height, int threshold) {
        boolean[] seen = new boolean[pixels.length];
        int[] queue = new int[pixels.length];
        var result = new ArrayList<Part>();
        for (int start = 0; start < pixels.length; start++) {
            if (seen[start] || !dark(pixels[start], threshold)) continue;
            int begin = 0, end = 0;
            queue[end++] = start;
            seen[start] = true;
            int left = width, top = height, right = 0, bottom = 0, area = 0;
            while (begin < end) {
                int at = queue[begin++], x = at % width, y = at / width;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x + 1);
                bottom = Math.max(bottom, y + 1);
                area++;
                if (x > 0) end = visit(at - 1, pixels, seen, queue, end, threshold);
                if (x + 1 < width) end = visit(at + 1, pixels, seen, queue, end, threshold);
                if (y > 0) end = visit(at - width, pixels, seen, queue, end, threshold);
                if (y + 1 < height) end = visit(at + width, pixels, seen, queue, end, threshold);
            }
            if (area >= Math.max(3, width * height / 1000))
                result.add(new Part(left, top, right, bottom, area));
            if (result.size() > 9) return List.of();
        }
        result.sort(Comparator.comparingInt(Part::left));
        return result;
    }

    private static int visit(
            int at, int[] pixels, boolean[] seen, int[] queue, int end, int threshold) {
        if (!seen[at] && dark(pixels[at], threshold)) {
            seen[at] = true;
            queue[end++] = at;
        }
        return end;
    }

    private static boolean dark(int pixel, int threshold) {
        return (pixel >>> 24) >= 240
                && (pixel >>> 16 & 255) < threshold
                && (pixel >>> 8 & 255) < threshold
                && (pixel & 255) < threshold;
    }

    private static boolean waveform(List<Part> parts, int width, int height) {
        int n = parts.size();
        if (n < 5 || n > 9 || n % 2 == 0) return false;
        Part peak = parts.get(n / 2), first = parts.get(0), last = parts.get(n - 1);
        if (peak.height() < height * .55f
                || peak.height() > height * .95f
                || first.left < 1
                || last.right >= width
                || peak.top < 1
                || peak.bottom >= height
                || last.right - first.left < width * .65f
                || last.right - first.left > width * .95f
                || peak.height() < first.height() * 2.8f
                || peak.height() < last.height() * 2.8f) return false;
        for (int i = 0; i < n; i++) {
            Part p = parts.get(i), mirror = parts.get(n - 1 - i);
            if (p.height() < 3
                    || p.width() < 2
                    || p.area < p.width() * p.height() * .35f
                    || Math.abs(p.centerY() - peak.centerY()) > peak.height() * .13f
                    || Math.abs(p.height() - mirror.height()) > Math.max(2, peak.height() * .12f))
                return false;
            if (i != n / 2 && p.width() > p.height() * .85f) return false;
            if (i < n / 2 && parts.get(i + 1).height() < p.height() * 1.35f) return false;
            if (i > n / 2 && p.height() > parts.get(i - 1).height() * .75f) return false;
            // Separate stems can share an antialiased boundary column, but not a body.
            if (i > 0 && p.left < parts.get(i - 1).right - 1) return false;
        }
        return true;
    }
}
