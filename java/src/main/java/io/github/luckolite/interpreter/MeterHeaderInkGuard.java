// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A complete clef or grand-staff brace owns its cropped ink before numeric OCR. */
final class MeterHeaderInkGuard {
    private MeterHeaderInkGuard() {}

    static boolean owns(
            MeterChangeDetector.Crop crop, byte[] labels, byte[] gray, int width, int height) {
        if (crop == null
                || labels == null
                || gray == null
                || labels.length != (long) width * height
                || gray.length != labels.length
                || crop.gap() < 6
                || !Float.isFinite(crop.gap())) return false;
        float gap = crop.gap();
        int left = Math.max(0, Math.round(crop.left() - gap * .25f));
        int right = Math.min(width - 1, Math.round(crop.right() + gap * .25f));
        int top = Math.max(0, Math.round(crop.top() - gap * 16));
        int bottom = Math.min(height - 1, Math.round(crop.bottom() + gap * 16));
        int w = right - left + 1, h = bottom - top + 1;
        if (w <= 0 || h <= 0 || w > gap * 4.2f || h > gap * 37) return false;
        for (byte target :
                new byte[] {OmrMeasurePostProcessor.CLEF_OR_KEY, OmrMeasurePostProcessor.SYMBOL}) {
            boolean[] visited = new boolean[w * h];
            int[] queue = new int[w * h];
            for (int seed = 0; seed < visited.length; seed++) {
                int pixel = (top + seed / w) * width + left + seed % w;
                if (visited[seed] || labels[pixel] != target || (gray[pixel] & 255) >= 205)
                    continue;
                int size = 1, take = 0, minX = w, maxX = -1, minY = h, maxY = -1;
                queue[0] = seed;
                visited[seed] = true;
                while (take < size) {
                    int at = queue[take++], x = at % w, y = at / w;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                            int next = ny * w + nx, source = (top + ny) * width + left + nx;
                            if (!visited[next]
                                    && labels[source] == target
                                    && (gray[source] & 255) < 205) {
                                visited[next] = true;
                                queue[size++] = next;
                            }
                        }
                }
                // Do not infer a complete header from a glyph clipped by the bounded window.
                if (minX == 0 || maxX == w - 1 || minY == 0 || maxY == h - 1 || size < gap * gap)
                    continue;
                int x0 = left + minX, x1 = left + maxX, y0 = top + minY, y1 = top + maxY;
                if (x1 < crop.left() || x0 > crop.right() || y1 < crop.top() || y0 > crop.bottom())
                    continue;
                float glyphWidth = x1 - x0 + 1, glyphHeight = y1 - y0 + 1;
                boolean clef =
                        target == OmrMeasurePostProcessor.CLEF_OR_KEY
                                && glyphWidth >= gap * .7f
                                && glyphWidth <= gap * 3.2f
                                && glyphHeight > gap * 4.8f
                                && y0 < crop.firstLine() - gap * .4f
                                && y1 > crop.firstLine() + gap * 4.35f;
                boolean brace =
                        target == OmrMeasurePostProcessor.SYMBOL
                                && glyphWidth >= gap * .6f
                                && glyphWidth <= gap * 1.8f
                                && glyphHeight >= gap * 8
                                && glyphHeight <= gap * 20
                                && (Math.abs(y0 - crop.firstLine()) <= gap * .4f
                                        || Math.abs(y1 - (crop.firstLine() + gap * 4))
                                                <= gap * .4f);
                if (clef || brace) return true;
            }
        }
        return false;
    }
}
