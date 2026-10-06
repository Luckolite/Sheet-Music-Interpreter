// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** A pale beam's enclosing ink edges are one band, not two independent beams. */
final class OutlinedBeamInk {
    private OutlinedBeamInk() {}

    private record Band(float middle, int first, int last, boolean outlined) {}

    static int count(byte[] gray, int width, int height, int[] stem, float headY, float gap) {
        int bounded = countWindow(gray, width, height, stem, headY, gap, .5f, .85f);
        return bounded > 0
                ? bounded
                : countWindow(gray, width, height, stem, headY, gap, 1.25f, .95f);
    }

    private static int countWindow(
            byte[] gray,
            int width,
            int height,
            int[] stem,
            float headY,
            float gap,
            float outside,
            float maximumThickness) {
        if (gray == null
                || stem == null
                || gap < 6
                || Math.abs(stem[1] - headY) < gap * 2.1f
                || Math.abs(stem[1] - headY) > gap * 6) return 0;
        int top = Math.max(1, Math.round(stem[1] - (stem[2] < 0 ? outside : 1.9f) * gap));
        int bottom =
                Math.min(height - 2, Math.round(stem[1] + (stem[2] < 0 ? 1.9f : outside) * gap));
        int paper = paper(gray, width, height, stem[0], top, bottom, gap);
        if (paper < 140) return 0;
        int maximum = 0;
        for (int side : new int[] {-1, 1}) {
            List<Band> first = null;
            boolean agrees = true;
            int supported = 0;
            for (float distance : new float[] {.35f, .55f, .7f}) {
                int x = stem[0] + side * Math.round(gap * distance);
                var bands =
                        bands(gray, width, height, x, top, bottom, paper, gap, maximumThickness);
                bands.sort(
                        (a, b) ->
                                Float.compare(
                                        (a.middle - stem[1]) * -stem[2],
                                        (b.middle - stem[1]) * -stem[2]));
                if (bands.isEmpty()
                        || bands.size() > 3
                        || Math.abs(bands.get(0).middle - stem[1]) > gap * .8f
                        || !bands.get(0).outlined) continue;
                if (first != null) {
                    if (first.size() != bands.size()) {
                        agrees = false;
                        break;
                    }
                    for (int i = 0; i < bands.size(); i++)
                        if (Math.abs(first.get(i).middle - bands.get(i).middle) > gap * .25f
                                || Math.abs(
                                                (first.get(i).last - first.get(i).first)
                                                        - (bands.get(i).last - bands.get(i).first))
                                        > gap * .25f) agrees = false;
                } else first = bands;
                supported++;
            }
            if (!agrees || first == null || supported < 2) continue;
            // The outer beam must continue substantially beyond the short secondary hook.
            int farX = stem[0] + side * Math.round(gap * 1.25f);
            boolean continues = false;
            for (var band :
                    bands(gray, width, height, farX, top, bottom, paper, gap, maximumThickness))
                if (band.outlined && Math.abs(band.middle - first.get(0).middle) < gap * .45f)
                    continues = true;
            if (continues) maximum = Math.max(maximum, first.size());
        }
        return maximum;
    }

    private static List<Band> bands(
            byte[] gray,
            int width,
            int height,
            int x,
            int top,
            int bottom,
            int paper,
            float gap,
            float maximumThickness) {
        var result = new ArrayList<Band>();
        if (x < 1 || x >= width - 1) return result;
        int threshold = Math.min(225, paper - 20), dark = Math.min(165, paper - 60), start = -1;
        for (int y = top; y <= bottom + 1; y++) {
            int support = 0;
            if (y <= bottom)
                for (int col = x - 1; col <= x + 1; col++)
                    if ((gray[y * width + col] & 255) < threshold) support++;
            boolean ink = support >= 2;
            if (ink && start < 0) start = y;
            if (!ink && start >= 0) {
                int last = y - 1, size = y - start;
                if (size >= gap * .35f && size <= gap * maximumThickness) {
                    int edge = Math.max(1, Math.round(gap * .2f)),
                            a = 255,
                            b = 255,
                            center = 0,
                            count = 0;
                    for (int row = start; row <= Math.min(last, start + edge); row++)
                        a = Math.min(a, gray[row * width + x] & 255);
                    for (int row = Math.max(start, last - edge); row <= last; row++)
                        b = Math.min(b, gray[row * width + x] & 255);
                    for (int row = start + edge; row <= last - edge; row++) {
                        center += gray[row * width + x] & 255;
                        count++;
                    }
                    boolean outline =
                            a < dark
                                    && b < dark
                                    && count > 0
                                    && center / (double) count > Math.max(a, b) + 35;
                    result.add(new Band((start + last) * .5f, start, last, outline));
                }
                start = -1;
            }
        }
        return result;
    }

    private static int paper(
            byte[] gray, int width, int height, int x, int top, int bottom, float gap) {
        int[] hist = new int[256];
        int total = 0;
        int rowMargin = Math.round(gap), columnMargin = Math.round(gap * 2);
        int firstRow = Math.max(0, top - rowMargin),
                lastRow = Math.min(height - 1, bottom + rowMargin);
        int firstColumn = Math.max(0, x - columnMargin),
                lastColumn = Math.min(width - 1, x + columnMargin);
        for (int y = firstRow; y <= lastRow; y++)
            for (int col = firstColumn; col <= lastColumn; col++) {
                hist[gray[y * width + col] & 255]++;
                total++;
            }
        int seen = 0;
        for (int i = 0; i < 256; i++) {
            seen += hist[i];
            if (seen >= total * .75) return i;
        }
        return 255;
    }
}
