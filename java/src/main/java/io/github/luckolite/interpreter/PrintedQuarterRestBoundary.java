// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** A complete quarter-rest contour cannot also subdivide its printed measure. */
final class PrintedQuarterRestBoundary {
    private PrintedQuarterRestBoundary() {}

    static boolean owns(byte[] gray, int w, int h, float x, float top, float bottom, float gap) {
        if (gray == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || !Float.isFinite(x)
                || !Float.isFinite(top)
                || !Float.isFinite(bottom)
                || !Float.isFinite(gap)
                || gap < 6
                || gap > Math.min(w, h) / 8f
                || Math.abs(bottom - top - gap * 4) > gap * .25f) return false;
        var narrow = read(gray, w, h, x, top, bottom, gap, 4);
        if (narrow == null) return false;
        var wide = read(gray, w, h, x, top, bottom, gap, 5);
        return wide != null
                && Math.abs(narrow[0] - wide[0]) <= gap * .15f
                && Math.abs(narrow[1] - wide[1]) <= gap * .15f
                && Math.abs(narrow[2] - wide[2]) <= gap * .15f;
    }

    private static float[] read(
            byte[] gray, int w, int h, float x, float top, float bottom, float gap, int radius) {
        int left = Math.round(x - gap * radius), right = Math.round(x + gap * radius);
        int first = Math.round(top - gap * 2), last = Math.round(bottom + gap * 2);
        if (left < 0 || right >= w || first < 0 || last >= h) return null;
        int cw = right - left + 1, ch = last - first + 1;
        byte[] crop = new byte[cw * ch];
        for (int y = 0; y < ch; y++)
            System.arraycopy(gray, (first + y) * w + left, crop, y * cw, cw);
        var staff = new SixteenthRestDetector.Staff(top - first, bottom - first, gap, 0, 1);
        var rests =
                SixteenthRestDetector.detect(
                        crop,
                        cw,
                        ch,
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        List.of(staff),
                        List.of());
        float[] found = null;
        for (var rest : rests) {
            if (rest.kind() != ScoreRestEvent.Kind.LITERAL || rest.durationBeats() != 1) continue;
            float cx = left + rest.positionInMeasure() * cw;
            float cy = first + rest.pageY() * ch, extent = rest.pageHeight() * ch;
            if (Math.abs(cx - x) > gap * .4f
                    || extent < gap * 2.1f
                    || extent > gap * 3.6f
                    || cy - extent * .5f < top - gap * .25f
                    || cy + extent * .5f > bottom + gap * .1f) continue;
            if (found != null) return null;
            found = new float[] {cx, cy, extent};
        }
        return found;
    }
}
