// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A bounded symbol window, retaining the existing threshold on unshaded paper. */
final class ShadedInkWindow {
    static int limit(
            byte[] gray, int w, int h, int left, int top, int right, int bottom, int normal) {
        return limit(gray, w, h, left, top, right, bottom, normal, 70, 35);
    }

    static int flatSpineLimit(
            byte[] gray, int w, int h, int left, int top, int right, int bottom, int normal) {
        return limit(gray, w, h, left, top, right, bottom, normal, 100, 50);
    }

    private static int limit(
            byte[] gray,
            int w,
            int h,
            int left,
            int top,
            int right,
            int bottom,
            int normal,
            int maxInk,
            int contrast) {
        if (gray == null) return normal;
        int[] histogram = new int[256];
        int n = 0;
        for (int y = Math.max(0, top); y <= Math.min(h - 1, bottom); y++)
            for (int x = Math.max(0, left); x <= Math.min(w - 1, right); x++) {
                histogram[gray[y * w + x] & 255]++;
                n++;
            }
        if (n == 0) return normal;
        int ink = 0, paper = 255, sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += histogram[i];
            if (sum >= n * .02) {
                ink = i;
                break;
            }
        }
        sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += histogram[i];
            if (sum >= n * .9) {
                paper = i;
                break;
            }
        }
        if (paper >= 185 || paper < 60 || ink >= maxInk || paper - ink < contrast) return normal;
        return Math.min(paper - 12, Math.round(ink + (paper - ink) * .55f));
    }
}
