// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Recovers pale filled heads whose dark outline survives but whose interior was unlabelled. */
final class FadedNoteheadRecovery {
    private FadedNoteheadRecovery() {}

    record Head(int left, int top, int right, int bottom) {}

    static List<Head> find(
            byte[] labels, byte[] gray, int width, int height, float gap, int top, int bottom) {
        List<Head> result = find(labels, gray, width, height, gap, top, bottom, 150);
        for (Head head : find(labels, gray, width, height, gap, top, bottom, 170)) {
            boolean duplicate = false;
            for (Head prior : result)
                if (Math.abs(prior.left() + prior.right() - head.left() - head.right()) < gap * 1.6f
                        && Math.abs(prior.top() + prior.bottom() - head.top() - head.bottom())
                                < gap * 1.3f) {
                    duplicate = true;
                    break;
                }
            if (!duplicate) result.add(head);
        }
        return result;
    }

    private static List<Head> find(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            float gap,
            int top,
            int bottom,
            int threshold) {
        List<Head> result = new ArrayList<>();
        if (width <= 0
                || height <= 0
                || gray == null
                || labels == null
                || gray.length != (long) width * height
                || labels.length != gray.length
                || gap < 6
                || !Float.isFinite(gap)) return result;
        top = Math.max(0, top);
        bottom = Math.min(height - 1, bottom);
        if (bottom <= top) return result;
        int size = width * (bottom - top + 1);
        boolean[] seen = new boolean[size];
        int[] queue = new int[size];
        for (int seed = 0; seed < size; seed++) {
            if (seen[seed] || (gray[top * width + seed] & 255) <= threshold) continue;
            int read = 0, end = 1;
            queue[0] = seed;
            seen[seed] = true;
            int left = width, right = 0, upper = bottom, lower = top;
            while (read < end) {
                int p = queue[read++], x = p % width, y = top + p / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                upper = Math.min(upper, y);
                lower = Math.max(lower, y);
                for (int direction = 0; direction < 4; direction++) {
                    int nx = x + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
                    int ny = y + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
                    if (nx < 0 || nx >= width || ny < top || ny > bottom) continue;
                    int next = (ny - top) * width + nx;
                    if (!seen[next] && (gray[ny * width + nx] & 255) > threshold) {
                        seen[next] = true;
                        queue[end++] = next;
                    }
                }
            }
            float w = right - left + 1, h = lower - upper + 1;
            if (left == 0
                    || right == width - 1
                    || upper == top
                    || lower == bottom
                    || w < gap * .55f
                    || w > gap * 1.5f
                    || h < gap * .45f
                    || h > gap * 1.05f
                    || end < w * h * .5f
                    || w / h < .65f
                    || w / h > 2.1f) continue;
            int[] values = new int[end];
            boolean recognized = false;
            for (int i = 0; i < end; i++) {
                int p = top * width + queue[i];
                values[i] = gray[p] & 255;
                if (labels[p] == OmrMeasurePostProcessor.NOTEHEAD) recognized = true;
            }
            if (recognized) continue;
            Arrays.sort(values);
            int interior = values[end / 2];
            // Truly hollow heads and ordinary white staff cells are not this failure mode.
            if (interior > 205) continue;
            int cx = (left + right) / 2, cy = (upper + lower) / 2;
            int[] paper = new int[18];
            int count = 0;
            for (int side : new int[] {-1, 1})
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = 0; dx < 3; dx++) {
                        int x = cx + side * Math.round(gap * (1.3f + dx * .2f)),
                                y = cy + Math.round(dy * gap * .2f);
                        if (x >= 0 && x < width && y >= 0 && y < height)
                            paper[count++] = gray[y * width + x] & 255;
                    }
            if (count < 12) continue;
            Arrays.sort(paper, 0, count);
            if (paper[count * 3 / 4] < 190 || paper[count * 3 / 4] - interior < 22) continue;
            int[] rowLeft = new int[(int) h], rowRight = new int[(int) h];
            Arrays.fill(rowLeft, width);
            for (int i = 0; i < end; i++) {
                int x = queue[i] % width, y = top + queue[i] / width - upper;
                rowLeft[y] = Math.min(rowLeft[y], x);
                rowRight[y] = Math.max(rowRight[y], x);
            }
            int middle = ((int) h - 1) / 2, edge = Math.max(0, Math.round(h * .12f));
            float bow =
                    Math.max(
                            (rowLeft[edge] + rowLeft[(int) h - 1 - edge]) * .5f - rowLeft[middle],
                            rowRight[middle]
                                    - (rowRight[edge] + rowRight[(int) h - 1 - edge]) * .5f);
            // Staff rules can clip an oval's curved cap down to half a pixel.
            // A perfectly rectangular cell still has no returning side; require
            // the closed contrasted pocket and independently printed/model stem.
            if (bow < Math.max(.5f, gap * .025f)) continue;
            if (!stem(labels, gray, width, height, left - 2, cy, gap, 1)
                    && !stem(labels, gray, width, height, right + 2, cy, gap, -1)) continue;
            if (flagPocketOnOwnedStem(labels, gray, width, height, left, upper, right, lower, gap))
                continue;
            result.add(new Head(left - 2, upper - 2, right + 2, lower + 2));
        }
        return result;
    }

    /** A compact flag pocket is opposite the already recognized head on the same shaft. */
    static boolean flagPocketOnOwnedStem(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float gap) {
        return flagPocketOnOwnedStem(
                labels, gray, width, height, left, top, right, bottom, gap, null);
    }

    @FunctionalInterface
    interface StemOwner {
        boolean owns(int stemX, int headY, int direction);
    }

    /** Semantic seeds additionally require the recognized owner to use this exact shaft. */
    static boolean flagPocketOnOwnedStem(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float gap,
            StemOwner owner) {
        if (labels == null
                || gray == null
                || width < 1
                || height < 1
                || labels.length != (long) width * height
                || gray.length != labels.length
                || !Float.isFinite(gap)
                || gap < 6
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || right < left
                || bottom < top
                || right - left + 1 > gap * .85f
                || bottom - top + 1 > gap * .85f) return false;
        int cy = (top + bottom) / 2;
        for (int direction : new int[] {-1, 1}) {
            int edge = direction > 0 ? left - 2 : right + 2;
            for (int sx = Math.max(0, edge - 3); sx <= Math.min(width - 1, edge + 3); sx++) {
                int ink = 0, semantic = 0, rows = 0, headRows = 0;
                for (int distance = 0; distance <= gap * 3.5f; distance++) {
                    int y = cy + direction * distance;
                    if (y < 0 || y >= height) break;
                    rows++;
                    if ((gray[y * width + sx] & 255) < 175) ink++;
                    if (labels[y * width + sx] == OmrMeasurePostProcessor.STEM_OR_REST) semantic++;
                    if (distance < gap * 1.25f) continue;
                    int headInk = 0;
                    for (int dx = Math.max(2, Math.round(gap * .25f)); dx <= gap * 1.5f; dx++) {
                        int x = sx - direction * dx;
                        if (x >= 0
                                && x < width
                                && labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD
                                && (gray[y * width + x] & 255) < 175) headInk++;
                    }
                    headRows = headInk >= gap * .5f ? headRows + 1 : 0;
                    if (headRows >= 3
                            && ink >= rows * .85f
                            && semantic >= rows * .5f
                            && (owner == null || owner.owns(sx, y, direction))) return true;
                }
            }
        }
        return false;
    }

    private static boolean stem(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int edge,
            int cy,
            float gap,
            int direction) {
        for (int x = Math.max(0, edge - 3); x <= Math.min(width - 1, edge + 3); x++) {
            int ink = 0, semantic = 0, total = 0;
            for (int distance = Math.round(gap * .65f); distance <= gap * 2.3f; distance++) {
                int y = cy + direction * distance;
                if (y < 0 || y >= height) return false;
                total++;
                if ((gray[y * width + x] & 255) < 150) ink++;
                if (labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST) semantic++;
            }
            if (total > 0 && ink >= total * .85f && semantic >= total * .5f) return true;
        }
        return false;
    }
}
