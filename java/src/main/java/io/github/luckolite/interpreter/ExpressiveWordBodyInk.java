// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** OCR identifies the word; original connected ink identifies the pixels that it owns. */
final class ExpressiveWordBodyInk {
    record Body(int left, int top, int right, int bottom, int count, boolean[] ink) {
        boolean contains(int pixel, int stride) {
            int x = pixel % stride - left, y = pixel / stride - top;
            int w = right - left + 1;
            return x >= 0 && x < w && y >= 0 && y <= bottom - top && ink[y * w + x];
        }
    }

    private ExpressiveWordBodyInk() {}

    static List<Body> detect(
            List<PlayingTechniqueDetector.Word> words,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<NoteArticulationDetector.Anchor> notes) {
        if (words == null
                || words.isEmpty()
                || notes.isEmpty()
                || labels == null
                || gray == null
                || gray.length != (long) width * height
                || labels.length != gray.length) return List.of();
        float[] gaps = new float[notes.size()];
        int valid = 0;
        for (var note : notes)
            if (note.staff() >= 0 && Float.isFinite(note.gap()) && note.gap() > 3)
                gaps[valid++] = note.gap();
        if (valid == 0) return List.of();
        Arrays.sort(gaps, 0, valid);
        float gap = gaps[valid / 2];
        byte[] contrasted = RestPaperTone.normalizeOrdinaryRestInk(gray, width, height, gap, 220f);
        List<Body> result = new ArrayList<>();
        for (var word : words) {
            if (word == null
                    || word.text() == null
                    || word.text().replaceAll("[^A-Za-z]", "").length() < 6
                    || ExpressiveDirectionText.parse(word.text()).isEmpty()
                    || !Float.isFinite(word.left())
                    || !Float.isFinite(word.top())
                    || !Float.isFinite(word.right())
                    || !Float.isFinite(word.bottom())
                    || word.left() < 0
                    || word.top() < 0
                    || word.right() > 1
                    || word.bottom() > 1) continue;
            int x0 = Math.max(0, Math.round(word.left() * width)),
                    x1 = Math.min(width - 1, Math.round(word.right() * width)),
                    y0 = Math.max(0, Math.round(word.top() * height)),
                    y1 = Math.min(height - 1, Math.round(word.bottom() * height));
            int w = x1 - x0 + 1, h = y1 - y0 + 1;
            if (w < gap * 3.5f || w > gap * 24 || h < gap * .65f || h > gap * 6) continue;
            boolean[] seen = new boolean[w * h];
            int[] queue = new int[w * h];
            for (int seed = 0; seed < seen.length; seed++) {
                int sx = seed % w + x0, sy = seed / w + y0;
                if (seen[seed] || (contrasted[sy * width + sx] & 255) >= 185) continue;
                int take = 0, count = 1, left = sx, right = sx, top = sy, bottom = sy;
                boolean clipped = false;
                queue[0] = seed;
                seen[seed] = true;
                while (take < count) {
                    int at = queue[take++], x = at % w, y = at / w;
                    clipped |= x == 0 || x == w - 1 || y == 0 || y == h - 1;
                    left = Math.min(left, x + x0);
                    right = Math.max(right, x + x0);
                    top = Math.min(top, y + y0);
                    bottom = Math.max(bottom, y + y0);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                            int next = ny * w + nx;
                            if (!seen[next]
                                    && (contrasted[(ny + y0) * width + nx + x0] & 255) < 185) {
                                seen[next] = true;
                                queue[count++] = next;
                            }
                        }
                }
                int bw = right - left + 1, bh = bottom - top + 1;
                if (clipped
                        || bw < gap * 3.5f
                        || bw < w * .45f
                        || bw > gap * 18
                        || bh < gap * .7f
                        || bh > gap * 3.2f
                        || bw / (float) bh < 2.1f
                        || bw / (float) bh > 12
                        || count < gap * gap
                        || count > bw * bh * .75f) continue;
                boolean[] ink = new boolean[bw * bh];
                int notation = 0;
                boolean head = false;
                for (int i = 0; i < count; i++) {
                    int x = queue[i] % w + x0, y = queue[i] / w + y0, pixel = y * width + x;
                    ink[(y - top) * bw + x - left] = true;
                    int label = labels[pixel];
                    head |=
                            label == OmrMeasurePostProcessor.NOTEHEAD
                                    || label == OmrMeasurePostProcessor.CLEF_OR_KEY;
                    if (label == OmrMeasurePostProcessor.STAFF
                            || label == OmrMeasurePostProcessor.STEM_OR_REST) notation++;
                }
                for (var note : notes)
                    if (note.x() >= left - 1
                            && note.x() <= right + 1
                            && note.y() >= top - 1
                            && note.y() <= bottom + 1) head = true;
                if (head || notation > count * .1f || counters(ink, bw, bh, gap) == 0) continue;
                result.add(new Body(left, top, right, bottom, count, ink));
            }
        }
        return List.copyOf(result);
    }

    static boolean owns(List<Body> bodies, int[] pixels, int count, int width) {
        for (var body : bodies) {
            if (body.count < count * 2 || count < 3) continue;
            boolean all = true;
            for (int i = 0; i < count; i++)
                if (!body.contains(pixels[i], width)) {
                    all = false;
                    break;
                }
            if (all) return true;
        }
        return false;
    }

    private static int counters(boolean[] ink, int w, int h, float gap) {
        boolean[] seen = new boolean[ink.length];
        int[] queue = new int[ink.length];
        int result = 0;
        for (int seed = 0; seed < ink.length; seed++) {
            if (ink[seed] || seen[seed]) continue;
            int take = 0, count = 1, left = w, right = 0, top = h, bottom = 0;
            boolean open = false;
            queue[0] = seed;
            seen[seed] = true;
            while (take < count) {
                int at = queue[take++], x = at % w, y = at / w;
                open |= x == 0 || x == w - 1 || y == 0 || y == h - 1;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int d = 0; d < 4; d++) {
                    int nx = x + (d == 0 ? -1 : d == 1 ? 1 : 0),
                            ny = y + (d == 2 ? -1 : d == 3 ? 1 : 0);
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    int next = ny * w + nx;
                    if (!seen[next] && !ink[next]) {
                        seen[next] = true;
                        queue[count++] = next;
                    }
                }
            }
            // A single enclosing ring is not the internal counter of a letter in a word.
            if (!open
                    && count <= w * h * .22f
                    && count >= Math.max(3, Math.round(gap * gap * .045f))
                    && right - left + 1 >= gap * .15f
                    && bottom - top + 1 >= gap * .2f) result++;
        }
        return result;
    }
}
