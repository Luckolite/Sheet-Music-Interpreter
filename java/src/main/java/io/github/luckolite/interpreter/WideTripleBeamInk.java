// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Three independent aligned cores across a slightly wider full-size beam pair. */
final class WideTripleBeamInk {
    private WideTripleBeamInk() {}

    static int count(byte[] gray, int w, int h, int[] a, int[] b, float gap) {
        return count(gray, w, h, a, b, gap, 3);
    }

    /** Four complete aligned cores, proved away from each attached shaft. */
    static int countFour(byte[] gray, int w, int h, int[] a, int[] b, float gap) {
        return count(gray, w, h, a, b, gap, 4);
    }

    private static int count(byte[] gray, int w, int h, int[] a, int[] b, float gap, int expected) {
        if (gray == null
                || w < 3
                || h < 3
                || gray.length != (long) w * h
                || !Float.isFinite(gap)
                || gap < 8
                || a == null
                || b == null
                || a.length < 3
                || b.length < 3
                || Math.abs(a[2]) != 1
                || a[2] != b[2]
                || a[0] < 1
                || a[0] >= w - 1
                || b[0] < 1
                || b[0] >= w - 1
                || a[1] < 1
                || a[1] >= h - 1
                || b[1] < 1
                || b[1] >= h - 1) return 0;
        int span = Math.abs(a[0] - b[0]);
        if (span < gap * (expected == 4 ? 1.2f : 3)
                || span > gap * 4
                || Math.abs(a[1] - b[1]) > gap * .75f) return 0;
        for (float fraction : new float[] {.5f, .75f}) {
            float[] previous = null;
            boolean okay = true;
            for (float f : new float[] {.25f, .5f, .75f}) {
                int x = Math.round(a[0] + (b[0] - a[0]) * f);
                float end = a[1] + (b[1] - a[1]) * f;
                float inside = expected == 4 ? 3.4f : 2.4f;
                int top = Math.max(1, Math.round(end - (a[2] < 0 ? .35f : inside) * gap)),
                        bottom =
                                Math.min(h - 2, Math.round(end + (a[2] < 0 ? inside : .35f) * gap));
                int threshold = BeamInkThreshold.at(gray, w, h, x, top, bottom, gap),
                        darkest = threshold;
                for (int y = top; y <= bottom; y++)
                    darkest = Math.min(darkest, gray[y * w + x] & 255);
                threshold = darkest + Math.round((threshold - darkest) * fraction);
                float[] cores = null;
                int coreCount = 0, start = -1;
                for (int y = top; y <= bottom + 1; y++) {
                    boolean ink =
                            y <= bottom
                                    && (gray[y * w + x - 1] & 255) < threshold
                                    && (gray[y * w + x] & 255) < threshold
                                    && (gray[y * w + x + 1] & 255) < threshold;
                    if (ink && start < 0) start = y;
                    if (!ink && start >= 0) {
                        int size = y - start;
                        if (size >= Math.max(3, (int) Math.ceil(gap * .3f)) && size <= gap * .8f) {
                            float core = (start + y - 1) * .5f - end;
                            if (cores == null) cores = new float[expected];
                            if (coreCount < expected) cores[coreCount] = core;
                            coreCount++;
                        }
                        start = -1;
                    }
                }
                if (coreCount != expected) {
                    okay = false;
                    break;
                }
                float[] current = cores;
                for (int i = 0; i < expected; i++) {
                    if (i > 0
                            && (current[i] - current[i - 1] < gap * .3f
                                    || current[i] - current[i - 1] > gap * .9f)) okay = false;
                    if (previous != null && Math.abs(current[i] - previous[i]) > gap * .22f)
                        okay = false;
                }
                previous = current;
            }
            if (okay) return expected;
        }
        return 0;
    }
}
