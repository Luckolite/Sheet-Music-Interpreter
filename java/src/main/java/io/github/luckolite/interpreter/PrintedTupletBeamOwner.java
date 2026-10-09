// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** A numeral beside a continuous beam belongs to its attached shafts, even between staves. */
final class PrintedTupletBeamOwner {
    private PrintedTupletBeamOwner() {}

    private record Tip(int x, int y) {}

    static boolean owns(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int stemDirection,
            int numeralLeft,
            int numeralTop,
            int numeralRight,
            int numeralBottom) {
        return Float.isFinite(
                distance(
                        gray,
                        width,
                        height,
                        firstX,
                        firstY,
                        lastX,
                        lastY,
                        gap,
                        stemDirection,
                        numeralLeft,
                        numeralTop,
                        numeralRight,
                        numeralBottom));
    }

    static float distance(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int stemDirection,
            int numeralLeft,
            int numeralTop,
            int numeralRight,
            int numeralBottom) {
        return distanceWithBounds(
                gray,
                width,
                height,
                firstX,
                firstY,
                lastX,
                lastY,
                gap,
                stemDirection,
                numeralLeft,
                numeralTop,
                numeralRight,
                numeralBottom,
                false);
    }

    /** The caller must already prove a complete rest-containing bracket. */
    static float bracketedDistance(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int stemDirection,
            int numeralLeft,
            int numeralTop,
            int numeralRight,
            int numeralBottom) {
        return distanceWithBounds(
                gray,
                width,
                height,
                firstX,
                firstY,
                lastX,
                lastY,
                gap,
                stemDirection,
                numeralLeft,
                numeralTop,
                numeralRight,
                numeralBottom,
                true);
    }

    private static float distanceWithBounds(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int stemDirection,
            int numeralLeft,
            int numeralTop,
            int numeralRight,
            int numeralBottom,
            boolean bracketed) {
        float nearest = Float.POSITIVE_INFINITY;
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != width * height
                || !Float.isFinite(gap)
                || gap < 4
                || Math.abs(stemDirection) != 1
                || lastX - firstX < gap * 2
                || lastX - firstX > gap * 26) return Float.POSITIVE_INFINITY;
        int travel = -stemDirection;
        List<Tip> first = tips(gray, width, height, firstX, firstY, gap, stemDirection, 1);
        List<Tip> last = tips(gray, width, height, lastX, lastY, gap, stemDirection, -1);
        float numeralX = (numeralLeft + numeralRight) * .5f;
        for (Tip a : first)
            for (Tip b : last) {
                if (b.x - a.x < gap || Math.abs(b.y - a.y) > gap * 3) continue;
                float fraction = (numeralX - a.x) / (b.x - a.x);
                if (fraction < (bracketed ? -.15f : .15f) || fraction > (bracketed ? 1.15f : .85f))
                    continue;
                float beamY = a.y + fraction * (b.y - a.y);
                float distance = travel > 0 ? numeralTop - beamY : beamY - numeralBottom;
                if (distance < Math.max(2, gap * .2f) || distance > gap * 1.8f) continue;
                int covered = 0, total = 0;
                for (int x = a.x + 2; x < b.x - 1; x++) {
                    int y = Math.round(a.y + (b.y - a.y) * (x - a.x) / (float) (b.x - a.x));
                    total++;
                    if (core(gray, width, height, x, y, gap)) covered++;
                }
                if (total > 0
                        && covered >= total * .94f
                        && BeamInkConnectivity.connected(
                                gray, width, height, a.x, a.y, b.x, b.y, gap))
                    nearest = Math.min(nearest, distance);
            }
        return nearest;
    }

    /** Both shafts must join the same uninterrupted thick beam. */
    static boolean connectedHeads(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float firstY,
            float lastX,
            float lastY,
            float gap,
            int direction) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != width * height
                || !Float.isFinite(gap)
                || gap < 4
                || Math.abs(direction) != 1
                || lastX - firstX < gap * .8f
                || lastX - firstX > gap * 26) return false;
        for (var a : tips(gray, width, height, firstX, firstY, gap, direction, 1))
            for (var b : tips(gray, width, height, lastX, lastY, gap, direction, -1)) {
                if (b.x - a.x < gap * .5f || Math.abs(b.y - a.y) > gap * 3) continue;
                int covered = 0, total = 0;
                for (int x = a.x + 2; x < b.x - 1; x++) {
                    int y = Math.round(a.y + (b.y - a.y) * (x - a.x) / (float) (b.x - a.x));
                    total++;
                    if (core(gray, width, height, x, y, gap)) covered++;
                }
                if (total > 0
                        && covered >= total * .94f
                        && BeamInkConnectivity.connected(
                                gray, width, height, a.x, a.y, b.x, b.y, gap)) return true;
            }
        return false;
    }

    private static List<Tip> tips(
            byte[] gray,
            int width,
            int height,
            float headX,
            float headY,
            float gap,
            int stemDirection,
            int outward) {
        var result = new ArrayList<Tip>();
        // A chord's shared attack x can be the shaft edge of its displaced second.
        // Require the same continuous shaft/beam evidence over the whole oval-side range.
        int center = Math.round(headX + stemDirection * gap * .3f), travel = -stemDirection;
        int radius = Math.max(1, Math.round(gap * .5f));
        for (int x = center - radius; x <= center + radius; x++) {
            if (x < 0 || x >= width) continue;
            int hits = 0, total = 0, blank = 0;
            for (int d = Math.round(gap * .6f); d <= gap * 6; d++) {
                int y = Math.round(headY) + travel * d;
                if (y < 0 || y >= height) break;
                total++;
                if ((gray[y * width + x] & 255) < 165) {
                    hits++;
                    blank = 0;
                } else if (++blank > Math.max(1, Math.round(gap * .16f))) break;
                if (total >= gap * 1.4f
                        && hits >= total * .92f
                        && outwardBeam(gray, width, height, x, y, gap, outward))
                    result.add(new Tip(x, y));
            }
        }
        return result;
    }

    private static boolean outwardBeam(
            byte[] gray, int width, int height, int x, int y, float gap, int side) {
        for (int angle = -10; angle <= 10; angle++) {
            int hits = 0, total = 0;
            for (int d = Math.round(gap * .25f); d <= gap * 1.35f; d++) {
                total++;
                if (core(gray, width, height, x + side * d, Math.round(y + angle * .06f * d), gap))
                    hits++;
            }
            if (total > 0 && hits >= total * .94f) return true;
        }
        return false;
    }

    private static boolean core(byte[] gray, int width, int height, int x, int y, float gap) {
        int radius = Math.max(1, Math.round(gap * .08f));
        if (x < 0 || x >= width || y - radius < 0 || y + radius >= height) return false;
        for (int dy = -radius; dy <= radius; dy++)
            if ((gray[(y + dy) * width + x] & 255) >= 165) return false;
        return true;
    }
}
