// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Source ownership of a pale shaft whose flag already passed the ordinary hook proof. */
final class FadedFlagShaft {
    private FadedFlagShaft() {}

    static boolean matches(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float headY,
            float gap,
            int axis,
            int end,
            boolean upward) {
        if (gray == null
                || labels == null
                || width <= 0
                || height <= 0
                || (long) width * height > gray.length
                || (long) width * height > labels.length
                || !Float.isFinite(gap)
                || !Float.isFinite(headY)
                || gap < 6
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || right - left + 1 < gap * .85f
                || right - left + 1 > gap * 1.65f
                || bottom - top + 1 < gap * .50f
                || bottom - top + 1 > gap * 1.20f
                || Math.min(Math.abs(axis - left), Math.abs(axis - right)) > gap * .30f)
            return false;
        int center = Math.round(headY), direction = upward ? -1 : 1;
        int length = (end - center) * direction;
        if (center < top
                || center > bottom
                || end < 0
                || end >= height
                || length < gap * 2.3f
                || length > gap * 6.5f) return false;
        int flank = Math.max(3, Math.round(gap * .35f));
        if (axis - flank < 0 || axis + flank >= width) return false;
        // The component needs an actual filled body, not just a predicted rectangle.
        int body = 0;
        for (int y = top; y <= bottom; y++) {
            int paper =
                    Math.max(
                            gray[y * width + Math.max(0, left - flank)] & 255,
                            gray[y * width + Math.min(width - 1, right + flank)] & 255);
            for (int x = left; x <= right; x++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD
                        && paper - (gray[y * width + x] & 255) >= 16) body++;
        }
        if (body < Math.max(8, Math.round(gap * gap * .08f))) return false;
        int semantic = 0, missing = 0;
        for (int step = 0; step <= length; step++) {
            int y = center + direction * step, ink = 255;
            boolean own = false;
            for (int dx = -1; dx <= 1; dx++) {
                int x = axis + dx;
                ink = Math.min(ink, gray[y * width + x] & 255);
                own |= labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST;
            }
            if (own) semantic++;
            int paper =
                    Math.max(
                            gray[y * width + axis - flank] & 255,
                            gray[y * width + axis + flank] & 255);
            if (paper - ink >= 16) missing = 0;
            else if (++missing > 1) return false;
        }
        return semantic >= (length + 1) * .70f;
    }
}
