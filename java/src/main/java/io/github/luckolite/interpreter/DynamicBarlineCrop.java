// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Remove only independently verified inter-staff barlines for a second glyph comparison. */
final class DynamicBarlineCrop {
    record Candidate(PlayingTechniqueDetector.Word word, byte[] gray, int width, int height) {}

    static Candidate crop(
            PlayingTechniqueDetector.Word word,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs) {
        if (gray == null || width < 1 || height < 1 || gray.length != (long) width * height)
            return null;
        int l = Math.max(0, Math.round(word.left() * width)),
                r = Math.min(width, Math.round(word.right() * width)),
                t = Math.max(0, Math.round(word.top() * height)),
                b = Math.min(height, Math.round(word.bottom() * height));
        if (r <= l || b <= t || !Float.isFinite(ScoreDynamicsDetector.level(word.text())))
            return null;
        float cy = (t + b) * .5f, gap = 0;
        boolean[] line = new boolean[r - l];
        for (var upper : staffs)
            for (var lower : staffs) {
                if (lower.index() != upper.index() + 1
                        || lower.count() != upper.count()
                        || lower.top() <= upper.bottom()
                        || cy <= upper.bottom()
                        || cy >= lower.top()) continue;
                float g = Math.max(upper.gap(), lower.gap());
                int top = Math.max(0, Math.round(upper.top())),
                        bottom = Math.min(height - 1, Math.round(lower.bottom()));
                if (bottom - top < g * 8 || bottom - top > g * 24) continue;
                float requiredBarlineHits = (bottom - top + 1) * .92f;
                for (int x = l; x < r; x++) {
                    int hits = 0;
                    for (int y = top; y <= bottom; y++)
                        if ((gray[y * width + x] & 255) < 145) hits++;
                    if (hits >= requiredBarlineHits) {
                        line[x - l] = true;
                        gap = g;
                    }
                }
            }
        if (gap == 0) return null;
        int total = 0;
        for (boolean hit : line) if (hit) total++;
        if (total > gap * .35f) return null;
        int cw = r - l, ch = b - t;
        byte[] clean = new byte[cw * ch];
        Arrays.fill(clean, (byte) 255);
        for (int y = 0; y < ch; y++)
            for (int x = 0; x < cw; x++)
                if (!line[x]) clean[y * cw + x] = gray[(t + y) * width + l + x];
        int minX = cw, maxX = -1, minY = ch, maxY = -1;
        int minimumRowInk = Math.max(3, Math.round(gap * .35f));
        for (int y = 0; y < ch; y++) {
            int ink = 0, rowMinX = cw, rowMaxX = -1;
            for (int x = 0; x < cw; x++)
                if ((clean[y * cw + x] & 255) < 145) {
                    ink++;
                    rowMinX = Math.min(rowMinX, x);
                    rowMaxX = Math.max(rowMaxX, x);
                }
            if (ink < minimumRowInk) continue;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minX = Math.min(minX, rowMinX);
            maxX = Math.max(maxX, rowMaxX);
        }
        int gw = maxX - minX + 1, gh = maxY - minY + 1;
        if (gw < gap * .8f || gh < gap * .65f || gh > gap * 3 || gw > gap * 6) return null;
        byte[] result = new byte[gw * gh];
        for (int y = 0; y < gh; y++)
            System.arraycopy(clean, (minY + y) * cw + minX, result, y * gw, gw);
        return new Candidate(
                new PlayingTechniqueDetector.Word(
                        word.text(),
                        (l + minX) / (float) width,
                        (t + minY) / (float) height,
                        (l + maxX + 1) / (float) width,
                        (t + maxY + 1) / (float) height),
                result,
                gw,
                gh);
    }
}
