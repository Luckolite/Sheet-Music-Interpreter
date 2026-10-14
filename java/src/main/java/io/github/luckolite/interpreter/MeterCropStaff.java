// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Refine only a nearby meter staff from complete local printed rails. */
final class MeterCropStaff {
    static MeterChangeDetector.Crop refine(byte[] gray, int w, int h, MeterChangeDetector.Crop c) {
        if (c == null
                || gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || !Float.isFinite(c.gap())
                || c.gap() < 6
                || c.gap() > Math.min(w, h) / 8f
                || c.left() < 0
                || c.right() > w
                || c.top() < 0
                || c.bottom() > h
                || c.left() >= c.right()
                || c.top() >= c.bottom()) return c;
        float hint = c.firstLine() + 4 * c.gap();
        var a = PrintedDirectionStaff.printedRules(gray, w, h, c.left() - c.gap(), hint, c.gap());
        var b = PrintedDirectionStaff.printedRules(gray, w, h, c.right() + c.gap(), hint, c.gap());
        if (a == null && b == null) return c;
        if (a != null
                && b != null
                && (Math.abs((a[0] - 4 * a[1]) - (b[0] - 4 * b[1])) > c.gap() * .15f
                        || Math.abs(a[1] - b[1]) > c.gap() * .06f)) return c;
        float bottom = a == null ? b[0] : b == null ? a[0] : (a[0] + b[0]) * .5f;
        float gap = a == null ? b[1] : b == null ? a[1] : (a[1] + b[1]) * .5f;
        int first = Math.round(bottom - 4 * gap);
        if (Math.abs(first - c.firstLine()) > c.gap() * .35f
                || Math.abs(gap - c.gap()) > c.gap() * .12f
                || first < c.top()
                || first + 4 * gap >= c.bottom()) return c;
        return new MeterChangeDetector.Crop(c.left(), c.top(), c.right(), c.bottom(), first, gap);
    }
}
