// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Tighten literal dynamic OCR to its printed ink; reject a clipped continuing note stem. */
final class DynamicOcrBounds {
    static List<PlayingTechniqueDetector.Word> clean(
            List<PlayingTechniqueDetector.Word> words,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs) {
        if (gray == null || gray.length != width * height) return List.copyOf(words);
        var result = new ArrayList<PlayingTechniqueDetector.Word>();
        for (var word : words) {
            if (!Float.isFinite(ScoreDynamicsDetector.level(word.text()))) {
                result.add(word);
                continue;
            }
            int left = Math.max(0, Math.round(word.left() * width)),
                    right = Math.min(width, Math.round(word.right() * width));
            int top = Math.max(0, Math.round(word.top() * height)),
                    bottom = Math.min(height, Math.round(word.bottom() * height));
            int[] histogram = new int[256];
            int area = 0;
            for (int y = top; y < bottom; y++)
                for (int x = left; x < right; x++) {
                    histogram[gray[y * width + x] & 255]++;
                    area++;
                }
            int paper = 255, seen = 0;
            for (int value = 0; value < 256; value++)
                if ((seen += histogram[value]) >= area * .85f) {
                    paper = value;
                    break;
                }
            int threshold = Math.min(145, Math.max(60, paper - 40));
            int minX = right, maxX = left - 1, minY = bottom, maxY = top - 1, ink = 0;
            for (int y = top; y < bottom; y++)
                for (int x = left; x < right; x++)
                    if ((gray[y * width + x] & 255) < threshold) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        ink++;
                    }
            if (ink < 4 || maxX <= minX || maxY <= minY) continue;
            float gap = Float.POSITIVE_INFINITY,
                    nearest = Float.POSITIVE_INFINITY,
                    center = (minY + maxY) * .5f;
            for (var staff : staffs) {
                float distance = Math.abs(center - (staff.top() + staff.bottom()) * .5f);
                if (distance < nearest) {
                    nearest = distance;
                    gap = staff.gap();
                }
            }
            if (Float.isFinite(gap)
                    && continuingStem(
                            gray, width, height, minX, maxX, top, bottom, gap, threshold)) {
                int detached =
                        detachedTextTop(gray, width, left, right, top, bottom, gap, threshold);
                if (detached <= top) continue;
                var lower =
                        new PlayingTechniqueDetector.Word(
                                word.text(),
                                word.left(),
                                detached / (float) height,
                                word.right(),
                                word.bottom());
                result.addAll(clean(List.of(lower), gray, width, height, staffs));
                continue;
            }
            result.add(
                    new PlayingTechniqueDetector.Word(
                            word.text(),
                            minX / (float) width,
                            minY / (float) height,
                            (maxX + 1) / (float) width,
                            (maxY + 1) / (float) height));
        }
        return List.copyOf(result);
    }

    /** A literal may include separate notation; only a unique, independently matched whole component refines it. */
    static List<PlayingTechniqueDetector.Word> refineComponents(
            List<PlayingTechniqueDetector.Word> words,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            java.util.function.Predicate<PlayingTechniqueDetector.Word> verified) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || words.size() > 256) return words;
        var result = new ArrayList<PlayingTechniqueDetector.Word>(words);
        int scanRemaining = 262144, matchRemaining = 262144, matchesRemaining = 64;
        for (var word : words) {
            String token = ScoreDynamicsDetector.glyphLevelText(word.text());
            if (token == null || !token.matches("f{2,3}|p{2,3}|m[fp]")) continue;
            if (!Float.isFinite(word.left())
                    || !Float.isFinite(word.right())
                    || !Float.isFinite(word.top())
                    || !Float.isFinite(word.bottom())) continue;
            int left = Math.max(0, Math.round(word.left() * width));
            int right = Math.min(width, Math.round(word.right() * width));
            int top = Math.max(0, Math.round(word.top() * height));
            int bottom = Math.min(height, Math.round(word.bottom() * height));
            long area = (long) (right - left) * (bottom - top);
            if (left >= right || top >= bottom || area > 65536) continue;
            if (area > scanRemaining) return words;
            scanRemaining -= (int) area;
            int[] histogram = new int[256];
            for (int y = top; y < bottom; y++)
                for (int x = left; x < right; x++) histogram[gray[y * width + x] & 255]++;
            int paper = 255, count = 0;
            for (int tone = 0; tone < 256; tone++)
                if ((count += histogram[tone]) >= area * .85f) {
                    paper = tone;
                    break;
                }
            int threshold = Math.min(145, Math.max(60, paper - 40));
            int cw = right - left, ch = bottom - top;
            boolean[] seen = new boolean[(int) area];
            int[] queue = new int[(int) area];
            PlayingTechniqueDetector.Word unique = null;
            boolean ambiguous = false;
            for (int start = 0; start < area; start++) {
                if (seen[start]
                        || (gray[(top + start / cw) * width + left + start % cw] & 255)
                                >= threshold) continue;
                int read = 0,
                        size = 1,
                        minX = right,
                        maxX = left - 1,
                        minY = bottom,
                        maxY = top - 1;
                queue[0] = start;
                seen[start] = true;
                boolean clipped = false;
                while (read < size) {
                    int p = queue[read++], x = left + p % cw, y = top + p / cw;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                                clipped = true;
                                continue;
                            }
                            if ((gray[ny * width + nx] & 255) >= threshold) continue;
                            if (nx < left || nx >= right || ny < top || ny >= bottom) {
                                clipped = true;
                                continue;
                            }
                            int next = (ny - top) * cw + nx - left;
                            if (!seen[next]) {
                                seen[next] = true;
                                queue[size++] = next;
                            }
                        }
                }
                if (clipped || size < 4 || minX >= maxX || minY >= maxY) continue;
                var candidate =
                        new PlayingTechniqueDetector.Word(
                                word.text(),
                                minX / (float) width,
                                minY / (float) height,
                                (maxX + 1) / (float) width,
                                (maxY + 1) / (float) height);
                int pixels = (maxX - minX + 1) * (maxY - minY + 1);
                float nearest = Float.POSITIVE_INFINITY, gap = Float.NaN;
                float center = (minY + maxY) * .5f;
                for (var staff : staffs) {
                    float distance = Math.abs(center - (staff.top() + staff.bottom()) * .5f);
                    if (distance < nearest) {
                        nearest = distance;
                        gap = staff.gap();
                    }
                }
                if (!Float.isFinite(gap)) continue;
                long span = Math.max(8, Math.round(gap * 1.5f));
                long charge = pixels * 3L + (maxX - minX + 1) * 2L * (span + 2);
                if (charge > matchRemaining || matchesRemaining == 0) return words;
                matchRemaining -= (int) charge;
                matchesRemaining--;
                // Reuse the existing contrast/continuing-stem rejection before a shape comparison.
                var clean = clean(List.of(candidate), gray, width, height, staffs);
                if (clean.size() != 1
                        || !clean.get(0).equals(candidate)
                        || !verified.test(candidate)) continue;
                if (unique != null && !unique.equals(candidate)) ambiguous = true;
                unique = candidate;
            }
            if (!ambiguous && unique != null) {
                var accepted = unique;
                result.removeIf(
                        old ->
                                old.text().equals(word.text())
                                        && old.left() < word.right()
                                        && old.right() > word.left()
                                        && old.top() < word.bottom()
                                        && old.bottom() > word.top());
                result.add(accepted);
            }
        }
        return List.copyOf(result);
    }

    private static boolean continuingStem(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            int threshold) {
        int span = Math.max(8, Math.round(gap * 1.5f));
        for (int x = left; x <= right; x++)
            for (int side : new int[] {-1, 1}) {
                int anchor = side < 0 ? top : bottom - 1, hits = 0;
                for (int offset = -2; offset < span; offset++) {
                    int y = anchor + side * offset;
                    if (y >= 0 && y < height && (gray[y * width + x] & 255) < threshold) hits++;
                }
                if (hits >= (span + 2) * .90f) return true;
            }
        return false;
    }

    /** A shallow clipped notation edge may be separated from all text by a full blank band. */
    private static int detachedTextTop(
            byte[] gray,
            int width,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            int threshold) {
        int limit = top + Math.min(Math.round(gap * .75f), (bottom - top) / 4);
        int required = Math.max(2, Math.round(gap * .15f)), blank = 0;
        boolean priorInk = false;
        for (int y = top; y < Math.min(bottom, limit); y++) {
            boolean ink = false;
            for (int x = left; x < right; x++)
                if ((gray[y * width + x] & 255) < threshold) {
                    ink = true;
                    break;
                }
            if (ink) {
                priorInk = true;
                blank = 0;
            } else if (priorInk && ++blank >= required) return y + 1;
        }
        return top;
    }
}
