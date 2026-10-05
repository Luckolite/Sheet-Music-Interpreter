// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Isolate a dynamic from a separate clipped stem; callers must independently verify the glyph. */
final class DynamicStemCrop {
    static DynamicBarlineCrop.Candidate crop(
            PlayingTechniqueDetector.Word word,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || !Float.isFinite(ScoreDynamicsDetector.level(word.text()))) return null;
        int left = Math.max(0, Math.round(word.left() * width)),
                right = Math.min(width, Math.round(word.right() * width));
        int top = Math.max(0, Math.round(word.top() * height)),
                bottom = Math.min(height, Math.round(word.bottom() * height));
        if (right <= left || bottom <= top) return null;
        float cy = (top + bottom) * .5f, gap = 0;
        for (var upper : staffs)
            for (var lower : staffs)
                if (lower.index() == upper.index() + 1
                        && lower.count() == upper.count()
                        && upper.bottom() < cy
                        && cy < lower.top()) gap = Math.max(upper.gap(), lower.gap());
        if (!Float.isFinite(gap) || gap <= 0 || right - left > gap * 6 || bottom - top > gap * 3)
            return null;
        int cw = right - left, ch = bottom - top;
        boolean[] seen = new boolean[cw * ch];
        byte[] clean = new byte[cw * ch];
        Arrays.fill(clean, (byte) 255);
        int[] queue = new int[cw * ch];
        boolean removed = false;
        for (int p = 0; p < seen.length; p++) {
            if (seen[p] || (gray[(top + p / cw) * width + left + p % cw] & 255) >= 145) continue;
            int start = 0, size = 1;
            queue[0] = p;
            seen[p] = true;
            boolean clipped = false;
            while (start < size) {
                int at = queue[start++], x = at % cw, y = at / cw;
                if (y == 0 || y == ch - 1) {
                    int direction = y == 0 ? -1 : 1,
                            hits = 0,
                            span = Math.max(8, Math.round(gap * 1.5f));
                    for (int offset = -2; offset < span; offset++) {
                        int py = top + y + direction * offset;
                        if (py >= 0 && py < height && (gray[py * width + left + x] & 255) < 145)
                            hits++;
                    }
                    if (hits >= (span + 2) * .9f) clipped = true;
                }
                size = visitNeighbor(at - 1, x, y, cw, width, top, left, seen, gray, queue, size);
                size = visitNeighbor(at + 1, x, y, cw, width, top, left, seen, gray, queue, size);
                size = visitNeighbor(at - cw, x, y, cw, width, top, left, seen, gray, queue, size);
                size = visitNeighbor(at + cw, x, y, cw, width, top, left, seen, gray, queue, size);
            }
            if (clipped) {
                removed = true;
                continue;
            }
            for (int i = 0; i < size; i++) {
                int at = queue[i];
                clean[at] = gray[(top + at / cw) * width + left + at % cw];
            }
        }
        if (!removed) return null;
        int minX = cw, minY = ch, maxX = -1, maxY = -1;
        for (int p = 0; p < clean.length; p++)
            if ((clean[p] & 255) < 145) {
                minX = Math.min(minX, p % cw);
                maxX = Math.max(maxX, p % cw);
                minY = Math.min(minY, p / cw);
                maxY = Math.max(maxY, p / cw);
            }
        int minimumInk = Math.max(4, Math.round(gap * .35f)), edgeInk = 0;
        boolean outgoing = false;
        for (int y = 0; y < ch; y++)
            if ((clean[y * cw + cw - 1] & 255) < 145) {
                edgeInk++;
                if (right < width)
                    for (int dy = -1; dy <= 1; dy++) {
                        int py = top + y + dy;
                        if (py >= 0 && py < height && (gray[py * width + right] & 255) < 145)
                            outgoing = true;
                    }
            }
        if (outgoing && edgeInk < minimumInk) {
            maxX = -1;
            for (int x = 0; x < cw; x++) {
                int ink = 0;
                for (int y = 0; y < ch; y++) if ((clean[y * cw + x] & 255) < 145) ink++;
                if (ink >= minimumInk) maxX = x;
            }
            minY = ch;
            for (int y = 0; y < ch; y++) {
                int ink = 0;
                for (int x = minX; x <= maxX; x++) if ((clean[y * cw + x] & 255) < 145) ink++;
                if (ink >= minimumInk) {
                    minY = y;
                    break;
                }
            }
        }
        int gw = maxX - minX + 1, gh = maxY - minY + 1;
        if (gw < gap * .8f || gw > gap * 6 || gh < gap * .65f || gh > gap * 3) return null;
        byte[] result = new byte[gw * gh];
        for (int y = 0; y < gh; y++)
            System.arraycopy(clean, (minY + y) * cw + minX, result, y * gw, gw);
        return new DynamicBarlineCrop.Candidate(
                new PlayingTechniqueDetector.Word(
                        word.text(),
                        (left + minX) / (float) width,
                        (top + minY) / (float) height,
                        (left + maxX + 1) / (float) width,
                        (top + maxY + 1) / (float) height),
                result,
                gw,
                gh);
    }

    private static int visitNeighbor(
            int next,
            int x,
            int y,
            int cw,
            int width,
            int top,
            int left,
            boolean[] seen,
            byte[] gray,
            int[] queue,
            int size) {
        if (next < 0
                || next >= seen.length
                || Math.abs(next % cw - x) + Math.abs(next / cw - y) != 1
                || seen[next]) return size;
        if ((gray[(top + next / cw) * width + left + next % cw] & 255) >= 145) return size;
        seen[next] = true;
        queue[size++] = next;
        return size;
    }
}
