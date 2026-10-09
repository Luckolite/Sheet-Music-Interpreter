// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Connected rest components inside a merged vertical projection. */
final class SeparatedRestInk {
    record Body(int left, int right, int top, int bottom, int[] pixels) {
        Body(int left, int right, int top, int bottom) {
            this(left, right, top, bottom, null);
        }

        byte[] owned(
                byte[] gray, byte[] scratch, int width, int height, boolean[] line, int lineTop) {
            if (pixels == null) return gray;
            int span = right - left + 1;
            boolean[] member = new boolean[span * (bottom - top + 1)];
            for (int pixel : pixels)
                member[(pixel / width - top) * span + pixel % width - left] = true;
            byte[] owned = scratch;
            for (int y = Math.max(0, top - 1); y <= Math.min(height - 1, bottom + 1); y++) {
                if (y >= lineTop && y - lineTop < line.length && line[y - lineTop]) continue;
                for (int x = left; x <= right; x++)
                    if (y < top || y > bottom || !member[(y - top) * span + x - left])
                        owned[y * width + x] = (byte) 255;
            }
            return owned;
        }

        void restore(byte[] scratch, byte[] gray, int width, int height) {
            if (pixels == null) return;
            for (int y = Math.max(0, top - 1); y <= Math.min(height - 1, bottom + 1); y++)
                System.arraycopy(
                        gray, y * width + left, scratch, y * width + left, right - left + 1);
        }
    }

    static boolean represented(
            Body body,
            SixteenthRestDetector.Staff staff,
            List<MeasureRegion> measures,
            List<ScoreRestEvent> rests,
            int width,
            int height) {
        float x = (body.left() + body.right()) * .5f, y = (body.top() + body.bottom()) * .5f;
        for (var rest : rests) {
            if (rest.staffIndex() != staff.index()
                    || rest.staffCount() != staff.count()
                    || rest.measureIndex() < 0
                    || rest.measureIndex() >= measures.size()) continue;
            var measure = measures.get(rest.measureIndex());
            float restX =
                    (measure.left() + rest.positionInMeasure() * (measure.right() - measure.left()))
                            * width;
            if (Math.abs(restX - x) < staff.gap() * .65f
                    && Math.abs(rest.pageY() * height - y)
                            < Math.max(
                                    staff.gap() * .5f,
                                    Math.max(
                                                    body.bottom() - body.top() + 1,
                                                    rest.pageHeight() * height)
                                            * .5f)) return true;
        }
        return false;
    }

    static List<Body> find(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            boolean[] line,
            float gap) {
        if (gray == null
                || gray.length != (long) width * height
                || gap < 4
                || left < 0
                || right >= width
                || top < 0
                || bottom >= height
                || right < left
                || bottom < top
                || line.length != bottom - top + 1
                || right - left + 1 > gap * 6) return List.of();
        int firstVisible = top, lastVisible = bottom;
        while (firstVisible <= bottom && line[firstVisible - top]) firstVisible++;
        while (lastVisible >= top && line[lastVisible - top]) lastVisible--;
        int span = right - left + 1, size = span * (bottom - top + 1);
        boolean[] visited = new boolean[size];
        int[] queue = new int[size];
        List<Body> result = new ArrayList<>();
        for (int seed = 0; seed < size; seed++) {
            int sx = left + seed % span, sy = top + seed / span;
            if (visited[seed] || line[sy - top] || (gray[sy * width + sx] & 255) >= 170) continue;
            int count = 1, cursor = 0;
            queue[0] = seed;
            visited[seed] = true;
            int l = right, r = left, t = bottom, b = top;
            while (cursor < count) {
                int pixel = queue[cursor++], x = left + pixel % span, y = top + pixel / span;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                for (int direction = -1; direction <= 1; direction++) {
                    int yy = y + direction;
                    // Preserve component continuity through an already verified staff rule.
                    if (direction != 0)
                        while (yy >= top && yy <= bottom && line[yy - top]) yy += direction;
                    if (yy < top || yy > bottom || Math.abs(yy - y) > Math.ceil(gap * .4f) + 1)
                        continue;
                    int reach = direction == 0 ? 1 : Math.max(1, Math.abs(yy - y));
                    for (int xx = Math.max(left, x - reach);
                            xx <= Math.min(right, x + reach);
                            xx++) {
                        int next = (yy - top) * span + xx - left;
                        if (!visited[next]
                                && !line[yy - top]
                                && (gray[yy * width + xx] & 255) < 170) {
                            visited[next] = true;
                            queue[count++] = next;
                        }
                    }
                }
            }
            // Never crop a continuing stem or a band-edge glyph into a complete rest.
            if (t <= firstVisible
                    || b >= lastVisible
                    || r - l + 1 < gap * .7f
                    || r - l + 1 > gap * 1.6f
                    || b - t + 1 < gap * 1.3f
                    || b - t + 1 > gap * 3.6f) continue;
            int[] pixels = new int[count];
            for (int i = 0; i < count; i++)
                pixels[i] = (top + queue[i] / span) * width + left + queue[i] % span;
            result.add(new Body(l, r, t, b, pixels));
        }
        return List.copyOf(result);
    }
}
