// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** A proved printed system can end before model-labelled photograph texture. */
final class PrintedStaffEnd {
    static Integer closingBeforeTexture(
            byte[] gray,
            int width,
            int height,
            float gap,
            List<Integer> bars,
            PrintedStaffAdmission.Rules rules) {
        if (gray == null
                || gray.length != (long) width * height
                || gap < 3
                || bars == null
                || bars.size() < 3
                || rules == null) return null;
        int end = bars.get(bars.size() - 1);
        for (int i = bars.size() - 2; i >= Math.max(1, bars.size() - 4); i--) {
            int cut = bars.get(i);
            if (end - cut < gap * 2.5f) continue;
            var left =
                    PrintedStaffAdmission.evidence(
                            gray,
                            width,
                            height,
                            Math.round(cut - gap * 4),
                            Math.round(cut - gap * .1f),
                            gap,
                            rules);
            var right =
                    PrintedStaffAdmission.evidence(
                            gray, width, height, Math.round(cut + gap * .1f), end - 1, gap, rules);
            var beyond =
                    PrintedStaffAdmission.evidence(
                            gray, width, height, Math.round(cut + gap), end - 1, gap, rules);
            if (left.columns() >= gap * 3
                    && left.alignedColumns() >= left.columns() * .7f
                    && left.longestRun() >= gap * 2.4f
                    && beyond.columns() >= gap * 2
                    && beyond.alignedColumns() <= Math.max(1, beyond.columns() * .02f)
                    && beyond.longestRun() <= 1
                    && printedClosingBar(gray, width, height, cut, gap, rules)
                    && variedBackground(gray, width, height, Math.round(cut + gap), end - 1, rules)
                    && !faintContinuation(
                            gray, width, height, Math.round(cut + gap), end - 1, gap, rules))
                return cut;
            if (left.columns() >= gap * 3
                    && left.alignedColumns() >= left.columns() * .7f
                    && left.longestRun() >= gap * 2.4f
                    && right.columns() >= gap * 2
                    && right.alignedColumns() <= 1
                    && right.longestRun() <= 1
                    && right.backgroundSamples() > 0
                    && right.darkSamples() >= right.backgroundSamples() * .85f
                    && right.backgroundRange() >= 24) return cut;
        }
        return null;
    }

    private static boolean printedClosingBar(
            byte[] gray,
            int width,
            int height,
            int cut,
            float gap,
            PrintedStaffAdmission.Rules rules) {
        int radius = Math.max(1, Math.round(gap * .6f)), flank = Math.max(2, Math.round(gap * .4f));
        for (int x = Math.max(flank, cut - radius);
                x <= Math.min(width - flank - 1, cut + radius);
                x++) {
            int top = Math.round(rules.y(0, x)), bottom = Math.round(rules.y(4, x)), hits = 0;
            if (top < 0 || bottom >= height || bottom <= top) continue;
            for (int y = top; y <= bottom; y++) {
                int ink = gray[y * width + x] & 255;
                if ((gray[y * width + x - flank] & 255) >= ink + 8
                        && (gray[y * width + x + flank] & 255) >= ink + 8) hits++;
            }
            if (hits >= (bottom - top + 1) * .85f) return true;
        }
        return false;
    }

    private static boolean variedBackground(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            PrintedStaffAdmission.Rules rules) {
        int[] hist = new int[256];
        int n = 0;
        for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++)
            for (int line = 0; line < 4; line++) {
                int y = Math.round((rules.y(line, x) + rules.y(line + 1, x)) * .5f);
                if (y >= 0 && y < height) {
                    hist[gray[y * width + x] & 255]++;
                    n++;
                }
            }
        if (n < 24) return false;
        int seen = 0, q1 = -1, q3 = -1;
        for (int v = 0; v < 256; v++) {
            seen += hist[v];
            if (q1 < 0 && seen >= n * .25f) q1 = v;
            if (seen >= n * .75f) {
                q3 = v;
                break;
            }
        }
        return q3 - q1 >= 12;
    }

    private static boolean faintContinuation(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float gap,
            PrintedStaffAdmission.Rules rules) {
        int flank = Math.max(2, Math.round(gap * .32f)),
                radius = Math.max(1, Math.round(gap * .15f));
        int run = 0;
        for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++) {
            int hits = 0;
            for (int line = 0; line < 5; line++) {
                int cy = Math.round(rules.y(line, x));
                for (int y = Math.max(flank, cy - radius);
                        y <= Math.min(height - flank - 1, cy + radius);
                        y++) {
                    int ink = gray[y * width + x] & 255;
                    if ((gray[(y - flank) * width + x] & 255) >= ink + 4
                            && (gray[(y + flank) * width + x] & 255) >= ink + 4) {
                        hits++;
                        break;
                    }
                }
            }
            run = hits >= 3 ? run + 1 : 0;
            if (run >= gap * 3) return true;
        }
        return false;
    }
}
