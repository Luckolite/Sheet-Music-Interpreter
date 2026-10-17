// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A sparse oval's filled interior is independent of an adjacent printed rail. */
final class FilledSparseBeamHead {
    static boolean matches(byte[] gray, int w, int h, float x, float y, float gap) {
        if (gray == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || !Float.isFinite(x + y + gap)
                || gap < 8) return false;
        float rx = gap * .69f, ry = gap * .4f;
        int reach = (int) Math.ceil(gap);
        if (x - reach < 0 || x + reach >= w || y - reach < 0 || y + reach >= h) return false;
        int total = 0, dark = 0;
        for (int dy = -reach; dy <= reach; dy++)
            for (int dx = -reach; dx <= reach; dx++) {
                if (dx * dx / (rx * rx) + (dy + .4f * dx) * (dy + .4f * dx) / (ry * ry) > .45f)
                    continue;
                int ink = gray[Math.round(y + dy) * w + Math.round(x + dx)] & 255;
                total++;
                if (ink >= 225) return false;
                if (ink < 205) dark++;
            }
        return total >= Math.ceil(gap * gap * .18f) && dark >= total * .8f;
    }

    static boolean rootedEnds(
            byte[] labels, byte[] gray, int w, int h, int[] a, int[] b, float gap) {
        if (labels == null
                || gray == null
                || labels.length != gray.length
                || gray.length != (long) w * h
                || a == null
                || b == null
                || a.length != 3
                || b.length != 3
                || !Float.isFinite(gap)
                || gap < 8
                || Math.abs(a[2]) != 1
                || a[2] != b[2]
                || a[0] == b[0]) return false;
        return cap(labels, gray, w, h, a, Integer.signum(b[0] - a[0]), gap)
                && cap(labels, gray, w, h, b, Integer.signum(a[0] - b[0]), gap);
    }

    private static boolean cap(
            byte[] labels, byte[] gray, int w, int h, int[] stem, int side, float gap) {
        int near = Math.max(2, (int) Math.ceil(gap * .2f)),
                far = Math.max(near + 1, (int) Math.ceil(gap * .45f));
        int thick = Math.max(4, (int) Math.ceil(gap * .25f));
        int top = Math.round(stem[1] - gap * (stem[2] < 0 ? .2f : .8f));
        int bottom = Math.round(stem[1] + gap * (stem[2] < 0 ? .8f : .2f));
        for (int d = near; d <= far; d++) {
            int x = stem[0] + side * d;
            if (x < 0 || x >= w || top < 0 || bottom + thick >= h) return false;
            boolean found = false;
            for (int y = top; y <= bottom; y++) {
                int symbolic = 0;
                boolean ink = true;
                for (int dy = 0; dy < thick; dy++) {
                    if ((gray[(y + dy) * w + x] & 255) >= 205) ink = false;
                    if (labels[(y + dy) * w + x] == 5) symbolic++;
                }
                if (ink && symbolic * 2 >= thick) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
