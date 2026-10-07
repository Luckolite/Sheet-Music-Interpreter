// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Separate short inward shafts keep the rails that terminate on their own side. */
final class InwardVoiceBeamOwnership {
    private InwardVoiceBeamOwnership() {}

    static int count(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            float gap,
            float staffTop,
            float staffBottom,
            int left,
            int right,
            int top,
            int bottom,
            float centerY,
            int otherLeft,
            int otherRight,
            int otherTop,
            int otherBottom,
            float otherCenterY) {
        if (gray == null
                || labels == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || labels.length != gray.length
                || !Float.isFinite(gap)
                || gap < 8
                || gap > height * .15f
                || !Float.isFinite(centerY)
                || !Float.isFinite(otherCenterY)
                || !Float.isFinite(staffTop)
                || !Float.isFinite(staffBottom)
                || staffBottom <= staffTop
                || left < 0
                || right >= width
                || top < 0
                || bottom >= height
                || otherLeft < 0
                || otherRight >= width
                || otherTop < 0
                || otherBottom >= height
                || left > right
                || top > bottom
                || otherLeft > otherRight
                || otherTop > otherBottom
                || centerY < top
                || centerY > bottom
                || otherCenterY < otherTop
                || otherCenterY > otherBottom
                || right - left < gap
                || otherRight - otherLeft < gap
                || centerY < staffTop - gap * 1.5f
                || centerY > staffBottom + gap * 1.5f
                || otherCenterY < staffTop - gap * 1.5f
                || otherCenterY > staffBottom + gap * 1.5f) return -1;
        float dy = otherCenterY - centerY;
        if (Math.abs(dy) < gap * 2.2f || Math.abs(dy) > gap * 4.2f) return -1;
        int direction = dy > 0 ? 1 : -1;
        int edge = direction > 0 ? left : right;
        int otherEdge = direction > 0 ? otherRight : otherLeft;
        // Inward shafts must be independent and horizontally separated.
        if ((otherEdge - edge) * direction < gap * 1.5f
                || (otherEdge - edge) * direction > gap * 5f) return -1;
        int threshold =
                BeamInkThreshold.at(
                                gray,
                                width,
                                height,
                                (left + right) / 2,
                                Math.round(centerY - gap * 5),
                                Math.round(centerY + gap * 5),
                                gap)
                        + 5;
        int tolerance = Math.max(1, Math.round(gap * .15f));
        int first = (direction > 0 ? bottom : top) + direction;
        int last = Math.round(centerY + direction * gap * 2.1f);
        int otherFirst = (direction > 0 ? otherTop : otherBottom) - direction;
        if ((otherFirst - first) * direction < gap * .5f) return -1;
        for (int x = edge - tolerance; x <= edge + tolerance; x++) {
            if (x - Math.round(gap * 1.2f) < 0 || x + Math.round(gap * 1.2f) >= width) continue;
            int railEnd = -1, lastRailRow = -1, bandCount = 0, run = 0, blanks = 0;
            for (int y = first; (last - y) * direction >= 0; y += direction) {
                if (y < 0 || y >= height) break;
                int outward = -direction;
                boolean rail =
                        ink(gray, width, height, x, y, threshold)
                                && labels[y * width + x] != OmrMeasurePostProcessor.NOTEHEAD
                                && ink(
                                        gray,
                                        width,
                                        height,
                                        x + outward * Math.round(gap * .6f),
                                        y,
                                        threshold)
                                && ink(
                                        gray,
                                        width,
                                        height,
                                        x + outward * Math.round(gap * .9f),
                                        y,
                                        threshold)
                                && !ink(
                                        gray,
                                        width,
                                        height,
                                        x - outward * Math.round(gap * .55f),
                                        y,
                                        threshold);
                if (rail) {
                    run++;
                    blanks = 0;
                    lastRailRow = y;
                } else if (++blanks > Math.max(1, Math.round(gap * .15f))) {
                    if (run >= Math.max(3, Math.round(gap * .18f))) {
                        bandCount++;
                        railEnd = lastRailRow;
                    }
                    run = 0;
                }
            }
            if (run >= Math.max(3, Math.round(gap * .18f))) {
                bandCount++;
                railEnd = lastRailRow;
            }
            if (bandCount != 1
                    || railEnd < 0
                    || !connected(
                            gray,
                            width,
                            height,
                            x,
                            Math.round(centerY),
                            railEnd,
                            direction,
                            threshold)) continue;
            for (int otherX = otherEdge - tolerance; otherX <= otherEdge + tolerance; otherX++) {
                int foreignRun = 0, foreignStart = -1;
                for (int y = railEnd + direction;
                        (otherFirst - y) * direction >= 0;
                        y += direction) {
                    if (y < 0 || y >= height) break;
                    boolean cross =
                            ink(gray, width, height, x, y, threshold)
                                    && ink(
                                            gray,
                                            width,
                                            height,
                                            x - direction * Math.round(gap * .7f),
                                            y,
                                            threshold)
                                    && !ink(
                                            gray,
                                            width,
                                            height,
                                            otherX + direction * Math.round(gap * .5f),
                                            y,
                                            threshold)
                                    && horizontal(gray, width, height, x, otherX, y, threshold);
                    if (cross) {
                        if (foreignRun++ == 0) foreignStart = y;
                        if (foreignRun >= Math.max(4, Math.round(gap * .25f))
                                && connected(
                                        gray,
                                        width,
                                        height,
                                        otherX,
                                        Math.round(otherCenterY),
                                        y,
                                        -direction,
                                        threshold)
                                && narrowShaft(
                                        gray,
                                        width,
                                        height,
                                        otherX,
                                        otherFirst,
                                        y,
                                        -direction,
                                        gap,
                                        threshold,
                                        Math.max(2, Math.round(gap * .1f)))
                                && !narrowShaft(
                                        gray,
                                        width,
                                        height,
                                        x,
                                        railEnd + direction,
                                        foreignStart - direction,
                                        direction,
                                        gap,
                                        threshold,
                                        1)) return 1;
                    } else {
                        foreignRun = 0;
                        foreignStart = -1;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean connected(
            byte[] gray,
            int width,
            int height,
            int x,
            int first,
            int last,
            int direction,
            int threshold) {
        int blanks = 0;
        for (int y = first; (last - y) * direction >= 0; y += direction) {
            if (ink(gray, width, height, x, y, threshold)) blanks = 0;
            else if (++blanks > 1) return false;
        }
        return true;
    }

    private static boolean narrowShaft(
            byte[] gray,
            int width,
            int height,
            int x,
            int first,
            int last,
            int direction,
            float gap,
            int threshold,
            int minimum) {
        int offset = Math.max(2, Math.round(gap * .35f)), run = 0;
        for (int y = first; (last - y) * direction >= 0; y += direction) {
            if (ink(gray, width, height, x, y, threshold)
                    && !ink(gray, width, height, x - offset, y, threshold)
                    && !ink(gray, width, height, x + offset, y, threshold)) {
                if (++run >= minimum) return true;
            } else run = 0;
        }
        return false;
    }

    private static boolean horizontal(
            byte[] gray, int width, int height, int first, int last, int y, int threshold) {
        int hits = 0, count = 0;
        for (int x = Math.min(first, last); x <= Math.max(first, last); x++) {
            count++;
            if (ink(gray, width, height, x, y, threshold)) hits++;
        }
        return hits >= count * .95f;
    }

    private static boolean ink(byte[] gray, int width, int height, int x, int y, int threshold) {
        return x >= 0
                && x < width
                && y >= 0
                && y < height
                && (gray[y * width + x] & 255) < threshold;
    }
}
