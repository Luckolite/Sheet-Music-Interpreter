// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Bounded raw-ink proof of a closed beam group with no unowned interior stem. */
final class CompleteBeamGroupInk {
    static boolean proves(
            byte[] gray, int w, int h, float[] xs, float[] ys, float gap, int direction) {
        if (gray == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || xs == null
                || ys == null
                || xs.length != ys.length
                || xs.length < 2
                || xs.length > 32
                || !Float.isFinite(gap)
                || gap < 6
                || Math.abs(direction) != 1) return false;
        var stems = new ArrayList<PairedTremoloInk.Stem>();
        for (int i = 0; i < xs.length; i++) {
            if (i > 0 && xs[i] - xs[i - 1] < gap * .7f) return false;
            var s = PairedTremoloInk.stem(gray, w, h, xs[i], ys[i], gap, direction);
            if (s == null) return false;
            stems.add(s);
        }
        var first = stems.get(0);
        var last = stems.get(stems.size() - 1);
        if (last.x() - first.x() < gap || last.x() - first.x() > gap * 40) return false;
        float slope = (last.endY() - first.endY()) / (float) (last.x() - first.x());
        if (Math.abs(slope) > .35f) return false;
        int inset = Math.max(1, Math.round(gap * .12f));
        for (int i = 0; i < stems.size(); i++) {
            var s = stems.get(i);
            float line = first.endY() + (s.x() - first.x()) * slope;
            if (Math.abs(s.endY() - line) > gap * .2f) return false;
            if (i > 0) {
                var prev = stems.get(i - 1);
                if (!BeamInkConnectivity.connected(
                        gray,
                        w,
                        h,
                        prev.x(),
                        prev.endY() + direction * inset,
                        s.x(),
                        s.endY() + direction * inset,
                        gap)) return false;
            }
        }
        int thick = 0, totalRail = 0, core = Math.max(2, Math.round(gap * .12f));
        for (int x = first.x() + 2; x < last.x() - 1; x++) {
            int edge = Math.round(first.endY() + (x - first.x()) * slope), ink = 0;
            totalRail++;
            for (int d = 0; d < core; d++) {
                int y = edge + direction * d;
                if (y >= 0 && y < h && (gray[y * w + x] & 255) < 165) ink++;
            }
            if (ink == core) thick++;
        }
        if (totalRail == 0) return false;
        if (thick < totalRail * .9f) {
            // Test one complete interior ray; never combine it column by column with the edge.
            long work = (long) totalRail * core;
            if (work <= 0 || work > (long) w * h) return false;
            int insetThick = 0;
            for (int x = first.x() + 2; x < last.x() - 1; x++) {
                int edge = Math.round(first.endY() + (x - first.x()) * slope), ink = 0;
                for (int d = 0; d < core; d++) {
                    long y = (long) edge + (long) direction * (inset + (long) d);
                    if (y >= 0 && y < h && (gray[(int) (y * w + x)] & 255) < 165) ink++;
                }
                if (ink == core) insetThick++;
            }
            if (insetThick < totalRail * .9f) return false;
        }
        // A group continuing past either outer owner is incomplete.
        for (int side : new int[] {-1, 1}) {
            int edge = side < 0 ? first.x() : last.x(), dark = 0, total = 0;
            for (int d = Math.round(gap * .35f); d <= gap; d++) {
                int x = edge + side * d,
                        y = Math.round(first.endY() + (x - first.x()) * slope) + direction * inset;
                if (x < 0 || x >= w || y < 0 || y >= h) return false;
                total++;
                if ((gray[y * w + x] & 255) < 165) dark++;
            }
            if (total == 0 || dark > total * .2f) return false;
        }
        // Trace every vertical shaft from the beam back toward the heads, including shafts
        // whose head the model missed. Every long shaft must belong to a supplied owner.
        for (int x = first.x() + 1; x < last.x(); x++) {
            int y0 = Math.round(first.endY() + (x - first.x()) * slope) + direction * inset;
            int run = 0, misses = 0;
            for (int d = 0; d <= gap * 3; d++) {
                int y = y0 + direction * d;
                if (y < 0 || y >= h) break;
                if ((gray[y * w + x] & 255) < 165) {
                    run = d;
                    misses = 0;
                } else if (++misses > 1) break;
            }
            if (run >= gap * 2) {
                boolean owned = false;
                for (var s : stems)
                    if (Math.abs(s.x() - x) <= Math.max(2, gap * .2f)) {
                        owned = true;
                        break;
                    }
                if (!owned) return false;
            }
        }
        return true;
    }
}
