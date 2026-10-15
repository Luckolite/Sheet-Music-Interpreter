// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;

/** Original-pixel preparation and agreement for one missing terminal credit stroke. */
public final class TerminalCreditStroke {
    private TerminalCreditStroke() {}

    public record Prepared(int width, int height, int[] pixels) {}

    /** Local contrast preserves faint terminal ink against a nonuniform paper background. */
    public static Prepared prepareAdaptive(int width, int height, int[] pixels) {
        if (pixels == null
                || width < 20
                || height < 16
                || width < height
                || (long) width * height != pixels.length
                || pixels.length > 4_000_000)
            throw new IllegalArgumentException("Invalid adaptive credit crop");
        if (pixels.length > 400000) return prepare(width, height, pixels);
        int block = Math.max(3, (height / 2) | 1), radius = block / 2;
        int stride = width + radius * 2 + 1;
        long cells = (long) stride * (height + radius * 2 + 1);
        if (cells > 4_000_000) return prepare(width, height, pixels);
        long[] integral = new long[(int) cells];
        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            gray[i] =
                    ((p >>> 16 & 255) * 299 + (p >>> 8 & 255) * 587 + (p & 255) * 114 + 500) / 1000;
        }
        for (int y = 0; y < height + radius * 2; y++) {
            long row = 0;
            int sy = Math.max(0, Math.min(height - 1, y - radius));
            for (int x = 0; x < width + radius * 2; x++) {
                row += gray[sy * width + Math.max(0, Math.min(width - 1, x - radius))];
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + row;
            }
        }
        int[] binary = new int[pixels.length];
        long area = (long) block * block;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                long sum =
                        integral[(y + block) * stride + x + block]
                                - integral[y * stride + x + block]
                                - integral[(y + block) * stride + x]
                                + integral[y * stride + x];
                int threshold = (int) ((sum + area / 2) / area) - 12;
                binary[y * width + x] = gray[y * width + x] <= threshold ? 0xff000000 : 0xffffffff;
            }
        removeUpperRules(binary, width, height);
        int pad = Math.max(3, height / 8), w = width + pad * 2, h = height + pad * 2;
        int[] out = new int[w * h];
        java.util.Arrays.fill(out, 0xffffffff);
        for (int y = 0; y < height; y++)
            System.arraycopy(binary, y * width, out, (y + pad) * w + pad, width);
        return new Prepared(w, h, out);
    }

    private static void removeUpperRules(int[] binary, int width, int height) {
        boolean[] seen = new boolean[binary.length];
        for (int start = 0; start < binary.length; start++) {
            if (seen[start] || !dark(binary[start])) continue;
            var pending = new ArrayDeque<Integer>();
            var component = new ArrayList<Integer>();
            pending.add(start);
            seen[start] = true;
            int left = width, right = -1, bottom = -1;
            while (!pending.isEmpty()) {
                int at = pending.removeFirst(), x = at % width, y = at / width;
                component.add(at);
                left = Math.min(left, x);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
                if (x > 0) visit(at - 1, binary, seen, pending);
                if (x + 1 < width) visit(at + 1, binary, seen, pending);
                if (y > 0) visit(at - width, binary, seen, pending);
                if (y + 1 < height) visit(at + width, binary, seen, pending);
            }
            if (right - left + 1 > width * .5f && bottom + 1 < height * .5f)
                for (int at : component) binary[at] = 0xffffffff;
        }
    }

    public static Prepared prepare(int width, int height, int[] pixels) {
        if (pixels == null
                || width < 20
                || height < 16
                || (long) width * height != pixels.length
                || pixels.length > 4_000_000)
            throw new IllegalArgumentException("Invalid credit crop");
        int[] histogram = new int[256], gray = new int[pixels.length];
        long total = 0;
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int g = ((p >>> 16 & 255) * 299 + (p >>> 8 & 255) * 587 + (p & 255) * 114 + 500) / 1000;
            gray[i] = g;
            histogram[g]++;
            total += g;
        }
        int threshold = 0, count = 0;
        long sum = 0;
        double best = -1;
        for (int t = 0; t < 256; t++) {
            count += histogram[t];
            sum += (long) t * histogram[t];
            if (count == 0) continue;
            if (count == pixels.length) break;
            double delta = (double) sum / count - (double) (total - sum) / (pixels.length - count);
            double variance = (double) count * (pixels.length - count) * delta * delta;
            if (variance > best) {
                best = variance;
                threshold = t;
            }
        }
        int pad = Math.max(3, height / 8), w = width + pad * 2, h = height + pad * 2;
        int[] out = new int[w * h];
        java.util.Arrays.fill(out, 0xffffffff);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                out[(y + pad) * w + x + pad] =
                        gray[y * width + x] <= threshold ? 0xff000000 : 0xffffffff;
        return new Prepared(w, h, out);
    }

    /** Require three agreeing original-PDF scales, with no confident contradictory reading. */
    public static String restore(
            String original,
            List<String> readings,
            List<Float> confidence,
            List<Boolean> strokeEvidence) {
        if (original == null
                || readings == null
                || confidence == null
                || strokeEvidence == null
                || (readings.size() != 3 && readings.size() != 4)
                || confidence.size() != readings.size()
                || strokeEvidence.size() != readings.size()) return original;
        String before = name(original);
        if (!before.matches("[A-Z][A-Z'’]*(?:\\h+[A-Z][A-Z'’]*){1,5}")
                || before.length() < 8
                || before.endsWith("I")
                || before.endsWith("L")) return original;
        String wanted = before + "I";
        int agreement = 0;
        for (int i = 0; i < readings.size(); i++) {
            Float score = confidence.get(i);
            if (score == null
                    || !Float.isFinite(score)
                    || score < 0
                    || score > 1
                    || !Boolean.TRUE.equals(strokeEvidence.get(i))) return original;
            if (score < .90f) continue;
            if (!wanted.equals(name(readings.get(i)))) return original;
            agreement++;
        }
        return agreement >= 3 ? wanted : original;
    }

    private static String name(String value) {
        return value == null
                ? ""
                : value.trim().replaceFirst("^[-–—]\\h*", "").replaceAll("\\h+", " ");
    }

    private record Ink(int left, int top, int right, int bottom, int count) {
        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }
    }

    public static boolean hasTerminalShortStem(int[] pixels, int width, int height) {
        if (pixels == null
                || width < 20
                || height < 16
                || (long) width * height != pixels.length
                || pixels.length > 400000) return false;
        var parts = components(pixels, width, height);
        var possible =
                parts.stream()
                        .filter(p -> p.width() < width * .4f && p.width() < p.height() * 4)
                        .toList();
        int tallest = possible.stream().mapToInt(Ink::height).max().orElse(0);
        if (tallest < 12) return false;
        var letters =
                possible.stream()
                        .filter(p -> p.height() >= tallest * .5f)
                        .sorted(Comparator.comparingInt(Ink::left))
                        .toList();
        if (letters.size() < 5) return false;
        Ink end = letters.get(letters.size() - 1), prior = letters.get(letters.size() - 2);
        if (end.width() > end.height() * .55f
                || end.count < end.width() * end.height() * .3f
                || prior.width() < tallest * .55f
                || end.left <= prior.right
                || end.left - prior.right > tallest * .6f
                || Math.abs(end.bottom - prior.bottom) > tallest * .2f) return false;
        for (Ink p : parts) {
            if (p == end) continue;
            if (p.width() < tallest * .5f
                    && p.bottom < end.top
                    && p.right >= end.left
                    && p.left <= end.right) return false;
            if (p.left > end.right && p.height() > tallest * .25f) return false;
        }
        return true;
    }

    private static ArrayList<Ink> components(int[] pixels, int width, int height) {
        boolean[] seen = new boolean[pixels.length];
        var parts = new ArrayList<Ink>();
        for (int start = 0; start < pixels.length; start++) {
            if (seen[start] || !dark(pixels[start])) continue;
            var pending = new ArrayDeque<Integer>();
            pending.add(start);
            seen[start] = true;
            int left = width, top = height, right = -1, bottom = -1, count = 0;
            while (!pending.isEmpty()) {
                int at = pending.removeFirst(), x = at % width, y = at / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                count++;
                if (x > 0) visit(at - 1, pixels, seen, pending);
                if (x + 1 < width) visit(at + 1, pixels, seen, pending);
                if (y > 0) visit(at - width, pixels, seen, pending);
                if (y + 1 < height) visit(at + width, pixels, seen, pending);
            }
            if (count >= 3) parts.add(new Ink(left, top, right, bottom, count));
        }
        return parts;
    }

    private static void visit(int at, int[] pixels, boolean[] seen, ArrayDeque<Integer> pending) {
        if (!seen[at] && dark(pixels[at])) {
            seen[at] = true;
            pending.add(at);
        }
    }

    private static boolean dark(int color) {
        return ((color >> 16 & 255) * 30 + (color >> 8 & 255) * 59 + (color & 255) * 11) / 100
                < 135;
    }
}
