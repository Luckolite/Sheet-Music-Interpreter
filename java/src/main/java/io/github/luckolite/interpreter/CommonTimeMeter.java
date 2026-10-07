// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Raw, open-right C signs following a printed clef. Does not infer a tempo or beat unit. */
final class CommonTimeMeter {
    record Reading(MeterChangeDetector.Crop crop, int numerator, int denominator) {}

    static List<Reading> candidates(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int firstLine,
            float gap,
            float slope) {
        if (labels == null
                || gray == null
                || labels.length != width * height
                || gray.length != width * height
                || gap < 6
                || !Float.isFinite(gap)) return List.of();
        var found = new ArrayList<Reading>();
        int middle = Math.round(firstLine + 2 * gap);
        int radius = Math.max(1, Math.round(gap * .12f));
        int[] columns = new int[width];
        for (int x = 0; x < width; x++)
            for (int y = Math.round(middle - 1.65f * gap);
                    y <= Math.round(middle + 1.65f * gap);
                    y++) {
                if (!rule(y, firstLine, gap, radius) && ink(gray, width, height, x, y, slope))
                    columns[x]++;
            }
        int maxBlank = Math.max(1, Math.round(gap * .20f));
        for (int x = 0; x < width; x++) {
            if (columns[x] < 2) continue;
            int left = x, last = x, blanks = 0;
            for (; x < width; x++) {
                if (columns[x] >= 2) {
                    last = x;
                    blanks = 0;
                } else if (++blanks > maxBlank) break;
            }
            int span = last - left + 1;
            if (span < gap * .9f
                    || span > gap * 2.7f
                    || !precedingClef(labels, width, height, left, firstLine, gap, slope)) continue;
            int meter = read(gray, width, height, left, last, firstLine, gap, slope);
            if (meter == 0) continue;
            int localTop = firstLine + Math.round(slope * ((left + last) * .5f - width * .5f));
            int pad = Math.max(2, Math.round(gap * .2f));
            found.add(
                    new Reading(
                            new MeterChangeDetector.Crop(
                                    Math.max(0, left - pad),
                                    Math.max(0, localTop - pad),
                                    Math.min(width, last + pad + 1),
                                    Math.min(height, Math.round(localTop + gap * 4) + pad + 1),
                                    localTop,
                                    gap),
                            meter,
                            meter));
        }
        return List.copyOf(found);
    }

    /** 4 means C, 2 means C with a central continuous cut; zero is not proven. */
    static int read(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int firstLine,
            float gap,
            float slope) {
        if (gray == null
                || gray.length != width * height
                || gap < 6
                || right <= left
                || left < 0
                || right >= width) return 0;
        float mid = firstLine + 2 * gap;
        int span = right - left + 1;
        int radius = Math.max(1, Math.round(gap * .12f));
        int top = height, bottom = -1, heads = 0;
        for (int y = Math.round(mid - gap * 1.65f); y <= Math.round(mid + gap * 1.65f); y++) {
            if (rule(y, firstLine, gap, radius)) continue;
            for (int x = left; x <= right; x++)
                if (ink(gray, width, height, x, y, slope)) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    heads++;
                }
        }
        if (top > mid - gap * .75f
                || bottom < mid + gap * .75f
                || bottom - top > gap * 3.25f
                || heads < gap * gap * .35f) return 0;
        // The right opening must be genuinely empty; a whole note or 0 is closed here.
        int open = 0, total = 0;
        for (int y = Math.round(mid - gap * .30f); y <= Math.round(mid + gap * .30f); y++) {
            if (rule(y, firstLine, gap, radius)) continue;
            for (int x = Math.round(left + span * .72f); x <= right; x++) {
                total++;
                if (ink(gray, width, height, x, y, slope)) open++;
            }
        }
        if (total == 0
                || open > total * .16f
                        && !openChannel(
                                gray, width, height, left, right, mid, gap, firstLine, slope))
            return 0;
        // Both right terminals and both left shoulders must be present, not a 3 or rest.
        if (count(
                                gray,
                                width,
                                height,
                                left + span * .55f,
                                right,
                                mid - gap * 1.35f,
                                mid - gap * .35f,
                                firstLine,
                                gap,
                                slope)
                        < gap * .65f
                || count(
                                gray,
                                width,
                                height,
                                left + span * .55f,
                                right,
                                mid + gap * .35f,
                                mid + gap * 1.35f,
                                firstLine,
                                gap,
                                slope)
                        < gap * .65f
                || count(
                                gray,
                                width,
                                height,
                                left,
                                left + span * .35f,
                                mid - gap * .75f,
                                mid - gap * .18f,
                                firstLine,
                                gap,
                                slope)
                        < gap * .65f
                || count(
                                gray,
                                width,
                                height,
                                left,
                                left + span * .35f,
                                mid + gap * .18f,
                                mid + gap * .75f,
                                firstLine,
                                gap,
                                slope)
                        < gap * .65f) return 0;
        // The C's left arc crosses the middle while its interior remains open.
        if (count(
                        gray,
                        width,
                        height,
                        left,
                        left + span * .35f,
                        mid - gap * .30f,
                        mid + gap * .30f,
                        firstLine,
                        gap,
                        slope)
                < gap * .35f) return 0;
        boolean cut = false;
        for (int x = Math.round(left + span * .32f); x <= Math.round(left + span * .68f); x++) {
            int present = 0, rows = 0;
            for (int y = Math.round(mid - gap * 1.15f); y <= Math.round(mid + gap * 1.15f); y++) {
                if (rule(y, firstLine, gap, radius)) continue;
                rows++;
                if (ink(gray, width, height, x, y, slope)) present++;
            }
            if (rows > 0
                    && present >= rows * .90f
                    && separatedCut(gray, width, height, left, x, mid, gap, slope, firstLine)) {
                cut = true;
                break;
            }
        }
        // A bold solid patch cannot be a C; allow only a narrow cut in the interior.
        int cavity =
                count(
                        gray,
                        width,
                        height,
                        left + span * .36f,
                        left + span * .70f,
                        mid - gap * .55f,
                        mid + gap * .55f,
                        firstLine,
                        gap,
                        slope);
        if (cavity > gap * gap * (cut ? .30f : .09f)) return 0;
        return cut ? 2 : 4;
    }

    /** A cut-C has a separate left bowl, white channel, and vertical cut in both halves.
     * A quarter rest's zig-zag spine and lower hook cannot provide that topology. */
    private static boolean separatedCut(
            byte[] gray,
            int width,
            int height,
            int left,
            int cut,
            float mid,
            float gap,
            float slope,
            int firstLine) {
        int[] supported = new int[2], total = new int[2];
        int radius = Math.max(1, Math.round(gap * .12f));
        for (int y = Math.round(mid - gap * .7f); y <= Math.round(mid + gap * .7f); y++) {
            if (rule(y, firstLine, gap, radius)) continue;
            int half = y < mid ? 0 : 1;
            total[half]++;
            if (!ink(gray, width, height, cut, y, slope)) continue;
            boolean bowl = false, channel = false;
            for (int x = left; x < cut - Math.max(1, Math.round(gap * .10f)); x++) {
                if (ink(gray, width, height, x, y, slope)) bowl = true;
                else if (bowl) channel = true;
            }
            if (channel) supported[half]++;
        }
        return total[0] > 0
                && total[1] > 0
                && supported[0] >= total[0] * .65f
                && supported[1] >= total[1] * .65f;
    }

    private static boolean precedingClef(
            byte[] labels, int width, int height, int left, int firstLine, float gap, float slope) {
        int rowCount = 0, pixels = 0;
        int lo = Math.max(0, Math.round(left - gap * 13));
        int hi = Math.max(0, Math.round(left - gap * .45f));
        for (int y = Math.round(firstLine - gap); y <= Math.round(firstLine + gap * 5); y++) {
            boolean row = false;
            for (int x = lo; x < hi; x++) {
                int yy = y + Math.round(slope * (x - width * .5f));
                if (yy >= 0
                        && yy < height
                        && labels[yy * width + x] == OmrMeasurePostProcessor.CLEF_OR_KEY) {
                    row = true;
                    pixels++;
                }
            }
            if (row) rowCount++;
        }
        return rowCount >= gap * 3.2f && pixels >= gap * gap;
    }

    /** A circular upper terminal may enter the central window while a lower channel stays open. */
    private static boolean openChannel(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float mid,
            float gap,
            int firstLine,
            float slope) {
        int radius = Math.max(1, Math.round(gap * .12f));
        int x0 = Math.round(left + (right - left + 1) * .72f),
                needed = Math.max(2, (int) Math.ceil(gap * .12f));
        int clear = 0;
        for (int y = Math.round(mid - gap * .30f); y <= Math.round(mid + gap * .30f); y++) {
            if (rule(y, firstLine, gap, radius)) continue;
            int dark = 0;
            for (int x = x0; x <= right; x++) if (ink(gray, width, height, x, y, slope)) dark++;
            if (dark <= (right - x0 + 1) * .16f) {
                if (++clear >= needed) return true;
            } else clear = 0;
        }
        return false;
    }

    private static int count(
            byte[] gray,
            int width,
            int height,
            float left,
            float right,
            float top,
            float bottom,
            int firstLine,
            float gap,
            float slope) {
        int n = 0, radius = Math.max(1, Math.round(gap * .12f));
        for (int y = Math.round(top); y <= Math.round(bottom); y++) {
            if (rule(y, firstLine, gap, radius)) continue;
            for (int x = Math.max(0, Math.round(left));
                    x <= Math.min(width - 1, Math.round(right));
                    x++) if (ink(gray, width, height, x, y, slope)) n++;
        }
        return n;
    }

    private static boolean rule(int y, int firstLine, float gap, int radius) {
        for (int i = 0; i < 5; i++) if (Math.abs(y - (firstLine + i * gap)) <= radius) return true;
        return false;
    }

    private static boolean ink(byte[] gray, int width, int height, int x, int y, float slope) {
        int yy = y + Math.round(slope * (x - width * .5f));
        return x >= 0 && x < width && yy >= 0 && yy < height && (gray[yy * width + x] & 255) < 155;
    }
}
