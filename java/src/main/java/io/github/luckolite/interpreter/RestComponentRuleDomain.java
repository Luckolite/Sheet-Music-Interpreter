// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Local physical-rule evidence for the optional separated-component route only. */
final class RestComponentRuleDomain {
    private RestComponentRuleDomain() {}

    record Domain(int left, int right, boolean[] line, boolean registered) {}

    // Explicit original-raster overload used by source-free role fixtures.
    static Domain bind(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            boolean[] line,
            float printedCenter,
            float gap) {
        return bind(gray, gray, width, height, left, right, top, bottom, line, printedCenter, gap);
    }

    // A photometric view may only use the unchanged original plane actually carried by
    // accepted HeadOrigins. No presumed inverse illumination or source reconstruction.
    static Domain bind(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            boolean[] line,
            float printedCenter,
            float gap,
            java.util.List<ScoreNoteEvent> notes) {
        byte[] original = null;
        if (notes != null)
            for (var note : notes) {
                var origin = RestFigureOrigins.origin(notes, note, gray, width, height);
                if (origin == null) continue;
                if (origin.originalGray == null
                        || origin.originalGray.length != (long) width * height)
                    return new Domain(left, right, line, false);
                if (original != null && original != origin.originalGray)
                    return new Domain(left, right, line, false);
                original = origin.originalGray;
            }
        if (original == null) return new Domain(left, right, line, false);
        return bind(
                gray, original, width, height, left, right, top, bottom, line, printedCenter, gap);
    }

    private static Domain bind(
            byte[] gray,
            byte[] original,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            boolean[] line,
            float printedCenter,
            float gap) {
        if (gray == null
                || (long) width * height != gray.length
                || width <= 0
                || height <= 0
                || left < 0
                || right >= width
                || left > right
                || top < 0
                || bottom >= height
                || top > bottom
                || line == null
                || line.length != bottom - top + 1
                || !Float.isFinite(gap)
                || gap < 4
                || !Float.isFinite(printedCenter)) return null;
        Domain legacy = new Domain(left, right, line, false);
        // Only remove extrapolated rows. Existing masks within the owner's five-rule
        // envelope, including their already-admitted AA edges, remain exactly unchanged.
        float first = printedCenter - gap * 2, last = printedCenter + gap * 2, edge = gap * .35f;
        if (original == null || original.length != gray.length || first < 0 || last >= height)
            return legacy;
        boolean outside = false;
        for (int i = 0; i < line.length; i++)
            if (line[i] && (top + i < first - edge || top + i > last + edge)) {
                outside = true;
                break;
            }
        if (!outside || !five(gray, original, width, height, left, right, printedCenter, gap))
            return legacy;
        boolean[] owned = line.clone();
        for (int i = 0; i < owned.length; i++)
            if (top + i < first - edge || top + i > last + edge) owned[i] = false;

        // Restoring original ink can join previously separated projection runs. Follow it
        // to real paper columns, never clip a continuing curve back to the old proposal.
        int l = left, r = right;
        while (l > 0 && ink(gray, width, l - 1, top, bottom, owned)) {
            if (r - (l - 1) + 1 > gap * 6) return null;
            l--;
        }
        while (r + 1 < width && ink(gray, width, r + 1, top, bottom, owned)) {
            if ((r + 1) - l + 1 > gap * 6) return null;
            r++;
        }
        if (l == 0 || r == width - 1 || r - l + 1 > gap * 6) return null;
        // The complete restored interval needs the same independent exterior evidence.
        if ((l != left || r != right)
                && !five(gray, original, width, height, l, r, printedCenter, gap)) return null;
        return new Domain(l, r, owned, true);
    }

    private static boolean ink(byte[] gray, int width, int x, int top, int bottom, boolean[] line) {
        for (int y = top; y <= bottom; y++)
            if (!line[y - top] && (gray[y * width + x] & 255) < 170) return true;
        return false;
    }

    private static boolean five(
            byte[] gray,
            byte[] original,
            int width,
            int height,
            int left,
            int right,
            float center,
            float gap) {
        // Reuse the existing five-gap long-rule requirement, now on BOTH local exteriors.
        // No body pixel or remote row contributes to these independent witnesses.
        int reach = (int) Math.ceil(gap * 5), radius = Math.round(gap * .2f);
        if (reach < 1 || (long) left - reach < 0 || (long) right + reach >= width) return false;
        int previous = -1;
        for (int k = 0; k < 5; k++) {
            int expected = Math.round(center + (k - 2) * gap);
            if (expected - radius < 0 || expected + radius >= height) return false;
            int first = -1, last = -1;
            for (int y = expected - radius; y <= expected + radius; y++) {
                boolean both = true;
                for (int x = left - reach; x < left && both; x++)
                    if ((gray[y * width + x] & 255) >= 170
                            || (original[y * width + x] & 255) >= 170) both = false;
                for (int x = right + 1; x <= right + reach && both; x++)
                    if ((gray[y * width + x] & 255) >= 170
                            || (original[y * width + x] & 255) >= 170) both = false;
                if (!both) continue;
                if (last >= 0 && y != last + 1) return false; // competing disjoint stripe supports
                if (first < 0) first = y;
                last = y;
            }
            if (first < 0 || first <= previous) return false;
            previous = last;
        }
        return true;
    }
}
