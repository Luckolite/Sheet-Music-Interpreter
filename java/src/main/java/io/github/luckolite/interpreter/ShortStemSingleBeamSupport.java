// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Raw ownership certificate for an admitted filled head on a short, continuously beamed shaft. */
final class ShortStemSingleBeamSupport {
    record Head(int left, int top, int right, int bottom, float centerY) {}

    private ShortStemSingleBeamSupport() {}

    static boolean proves(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Head own,
            int[] shaft,
            Head peer,
            int[] peerShaft,
            float gap) {
        if (labels == null
                || gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || labels.length != gray.length
                || !Float.isFinite(gap)
                || gap < 6
                || gap > 64
                || shaft == null
                || peerShaft == null
                || shaft.length != 3
                || peerShaft.length != 3
                || Math.abs(shaft[2]) != 1
                || shaft[2] != peerShaft[2]
                || Math.abs((long) shaft[0] - peerShaft[0]) < gap * 2
                || Math.abs((long) shaft[0] - peerShaft[0]) > gap * 5
                || !filledHead(labels, gray, width, height, own, gap)
                || !filledHead(labels, gray, width, height, peer, gap)
                || Math.abs(own.centerY - peer.centerY) > gap * 2) return false;
        float shortLength = (shaft[1] - own.centerY) * shaft[2];
        float peerLength = (peerShaft[1] - peer.centerY) * peerShaft[2];
        if (shortLength < gap * 1.5f
                || shortLength > gap * 2.3f
                || peerLength < gap * 2.3f
                || peerLength > gap * 5.5f
                || !continuousOwnedShaft(gray, width, height, own, shaft, gap)
                || !continuousOwnedShaft(gray, width, height, peer, peerShaft, gap)) return false;
        return PrintedSingleBeamPath.connects(gray, width, height, shaft, peerShaft, gap)
                && exclusiveHeadFreeRail(gray, width, height, shaft, peerShaft, gap, shortLength);
    }

    /** The normal 2.2-gap beam window contains this short shaft's own head. Stop before it. */
    private static boolean exclusiveHeadFreeRail(
            byte[] g, int w, int h, int[] a, int[] b, float gap, float shortLength) {
        if (a[0] > b[0]) {
            int[] swap = a;
            a = b;
            b = swap;
        }
        int span = b[0] - a[0], margin = Math.max(4, Math.round(gap * .35f));
        float inside = Math.min(gap * 1.4f, shortLength - gap * .8f);
        if (inside < gap) return false;
        int minimum = Math.max(4, (int) Math.ceil(gap * .25f));
        for (int x = a[0] + margin; x <= b[0] - margin; x++) {
            float root = a[1] + (b[1] - a[1]) * (x - a[0]) / (float) span;
            int first = Math.round(root - (a[2] > 0 ? inside : gap * .35f));
            int last = Math.round(root + (a[2] < 0 ? inside : gap * .35f));
            if (first < 1 || last >= h - 1) return false;
            int start = -1, bodies = 0;
            boolean primary = false;
            for (int y = first; y <= last + 1; y++) {
                boolean ink = y <= last && (g[y * w + x] & 255) < 205;
                if (ink && start < 0) start = y;
                if (!ink && start >= 0) {
                    int thickness = y - start;
                    if (thickness >= minimum) {
                        if (thickness > gap * .8f || start == first || y - 1 == last) return false;
                        bodies++;
                        if (start <= root + gap * .35f && y - 1 >= root - gap * .35f)
                            primary = true;
                    }
                    start = -1;
                }
            }
            if (bodies != 1 || !primary) return false;
        }
        return true;
    }

    private static boolean filledHead(
            byte[] labels, byte[] gray, int w, int h, Head head, float gap) {
        if (head == null
                || !Float.isFinite(head.centerY)
                || head.left < 0
                || head.top < 0
                || head.right >= w
                || head.bottom >= h
                || head.right < head.left
                || head.bottom < head.top
                || head.centerY < head.top
                || head.centerY > head.bottom) return false;
        int ww = head.right - head.left + 1, hh = head.bottom - head.top + 1;
        if (ww < gap * .55f || ww > gap * 1.4f || hh < gap * .4f || hh > gap * 1.15f) return false;
        int semantic = 0, supported = 0;
        int[] quadrants = new int[4];
        int mx = (head.left + head.right) / 2, my = (head.top + head.bottom) / 2;
        for (int y = head.top; y <= head.bottom; y++)
            for (int x = head.left; x <= head.right; x++)
                if (labels[y * w + x] == 2) {
                    semantic++;
                    if ((gray[y * w + x] & 255) < 205) {
                        supported++;
                        quadrants[(y > my ? 2 : 0) + (x > mx ? 1 : 0)]++;
                    }
                }
        if (semantic < gap * gap * .20f || supported < semantic * .75f) return false;
        for (int q : quadrants) if (q < 2) return false;
        return true;
    }

    /** Fixed-column continuity cannot borrow a rest's descending diagonal or an adjacent shaft. */
    private static boolean continuousOwnedShaft(
            byte[] g, int w, int h, Head head, int[] stem, float gap) {
        int x = stem[0], end = stem[1], direction = stem[2];
        float edge = direction > 0 ? head.left : head.right;
        if (x < 1 || x >= w - 1 || end < 1 || end >= h - 1 || Math.abs(x - edge) > gap * .25f)
            return false;
        int start = direction > 0 ? head.bottom + 1 : head.top - 1;
        int stop = end - direction * Math.max(4, Math.round(gap * .4f));
        if ((stop - start) * direction < gap * .6f) return false;
        for (int y = start; (stop - y) * direction >= 0; y += direction)
            if ((g[y * w + x] & 255) >= 205) return false;
        return true;
    }
}
