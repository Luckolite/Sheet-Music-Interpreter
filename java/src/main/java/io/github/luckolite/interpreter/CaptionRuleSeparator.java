// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Observed full-width staff band and paper enclosure; original OCR geometry is never rewritten. */
final class CaptionRuleSeparator {
    static float bodyTop(
            byte[] gray,
            int w,
            int h,
            PlayingTechniqueDetector.Word word,
            float bottom,
            float gap) {
        if (gray == null
                || word == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || !Float.isFinite(bottom)
                || !Float.isFinite(gap)
                || gap < 6
                || !Float.isFinite(word.left())
                || !Float.isFinite(word.right())
                || !Float.isFinite(word.top())
                || !Float.isFinite(word.bottom())) return Float.NaN;
        int l = (int) Math.floor(word.left() * w), r = (int) Math.ceil(word.right() * w) - 1;
        int t = (int) Math.floor(word.top() * h), b = (int) Math.ceil(word.bottom() * h) - 1;
        if (l < 0
                || r >= w
                || t < 0
                || b >= h
                || l >= r
                || t >= b
                || (long) (r - l + 1) * (b - t + 1) > 65536
                || t < Math.floor(bottom - .3f * gap)
                || t > bottom
                || b <= bottom) return Float.NaN;
        // The same existing five-rule registration must hold independently at BOTH original span
        // edges.
        for (int x : new int[] {l, r}) {
            var rules = PrintedDirectionStaff.printedRules(gray, w, h, x, bottom, gap);
            if (rules == null
                    || Math.abs(rules[0] - bottom) > .2f * gap
                    || Math.abs(rules[1] - gap) > .08f * gap) return Float.NaN;
        }
        int separator = -1, last = Math.min(b, (int) Math.ceil(bottom + .3f * gap));
        boolean core = false;
        for (int y = t; y <= last; y++) {
            int nonwhite = 0, strong = 0;
            for (int x = l; x <= r; x++) {
                int v = gray[y * w + x] & 255;
                if (v < 255) nonwhite++;
                if (v < 145) strong++;
            }
            if (nonwhite == 0) {
                if (core && y > bottom) separator = y;
                break;
            }
            // Every excluded row must be the WHOLE observed rail, including its AA fringe.
            // Sparse strong ink cannot borrow a full fringe row as its own staff role.
            if (nonwhite != r - l + 1 || strong != 0 && strong != r - l + 1) return Float.NaN;
            if (strong == r - l + 1) core = true;
        }
        if (separator < 0) return Float.NaN;
        int first = -1;
        boolean strongBody = false;
        for (int y = separator + 1; y <= b; y++)
            for (int x = l; x <= r; x++) {
                int v = gray[y * w + x] & 255;
                if ((x == l || x == r || y == b) && v != 255) return Float.NaN;
                if (v < 255 && first < 0) first = y;
                if (v < 145) strongBody = true;
            }
        return first >= 0 && strongBody ? first : Float.NaN;
    }
}
