// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.security.MessageDigest;
import java.util.*;

/** Same-acquisition OCR parent/child identity removes duplicate strength observations only. */
final class SuddenDynamicSource {
    static final class Root {
        final byte[] raw, digest;
        final int width, height;
        final Thread thread = Thread.currentThread();

        Root(byte[] raw, int width, int height) {
            this.raw = raw;
            this.width = width;
            this.height = height;
            digest = validImage(raw, width, height) ? hash(raw) : null;
        }

        boolean current(byte[] gray, int w, int h) {
            return digest != null
                    && thread == Thread.currentThread()
                    && raw == gray
                    && width == w
                    && height == h
                    && Arrays.equals(digest, hash(gray));
        }
    }

    private record Box(int left, int top, int right, int bottom) {
        boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        int area() {
            return (right - left) * (bottom - top);
        }
    }

    static final class Group {
        private final Root root;
        private final OcrText read;
        private final OcrText.Line parent;
        private final Box qualifier, child, whole;

        private Group(
                Root root, OcrText read, OcrText.Line parent, Box qualifier, Box child, Box whole) {
            this.root = root;
            this.read = read;
            this.parent = parent;
            this.qualifier = qualifier;
            this.child = child;
            this.whole = whole;
        }
    }

    /** Cached line identity is meaningful only within the exact read crop and scale. */
    static final class ReadFrame {
        final OcrText.Line parent;
        final int left, top, right, bottom;
        final float scale;

        ReadFrame(OcrText.Line parent, int left, int top, int right, int bottom, float scale) {
            this.parent = parent;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.scale = scale;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ReadFrame f
                    && parent == f.parent
                    && left == f.left
                    && top == f.top
                    && right == f.right
                    && bottom == f.bottom
                    && Float.floatToIntBits(scale) == Float.floatToIntBits(f.scale);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    System.identityHashCode(parent),
                    left,
                    top,
                    right,
                    bottom,
                    Float.floatToIntBits(scale));
        }
    }

    private SuddenDynamicSource() {}

    private static boolean validImage(byte[] r, int w, int h) {
        return r != null && w > 0 && h > 0 && (long) w * h == r.length && r.length <= 20_000_000;
    }

    private static byte[] hash(byte[] r) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(r);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static Box mapped(OcrText.Box b, int x, int y, float scale, int w, int h) {
        if (b == null
                || !Float.isFinite(scale)
                || scale <= 0
                || b.left < 0
                || b.top < 0
                || b.right <= b.left
                || b.bottom <= b.top) return null;
        int l = (int) Math.floor(x + b.left / scale),
                t = (int) Math.floor(y + b.top / scale),
                r = (int) Math.ceil(x + b.right / scale),
                v = (int) Math.ceil(y + b.bottom / scale);
        return l < 0 || t < 0 || r > w || v > h ? null : new Box(l, t, r, v);
    }

    static Group capture(
            Root root,
            OcrText read,
            OcrText.Line parent,
            int cropX,
            int cropY,
            int cropRight,
            int cropBottom,
            float scale) {
        if (root == null
                || root.digest == null
                || root.thread != Thread.currentThread()
                || read == null
                || read.grouping() == OcrText.Grouping.UNKNOWN
                || parent == null
                || parent.elements().size() != 2
                || cropX < 0
                || cropY < 0
                || cropRight > root.width
                || cropBottom > root.height
                || cropRight <= cropX
                || cropBottom <= cropY) return null;
        int identities = 0;
        for (var block : read.blocks())
            for (var line : block.lines()) if (line == parent) identities++;
        var parsed = SuddenDynamicText.parse(parent.text());
        if (identities != 1 || parsed.isEmpty()) return null;
        var mark = parsed.get();
        var q = parent.elements().get(mark.before() ? 0 : 1);
        var c = parent.elements().get(mark.before() ? 1 : 0);
        if (!text(q.text()).equals(text(mark.qualifier())) || !text(c.text()).equals(mark.level()))
            return null;
        Box qualifier = mapped(q.box(), cropX, cropY, scale, root.width, root.height),
                child = mapped(c.box(), cropX, cropY, scale, root.width, root.height),
                whole = mapped(parent.box(), cropX, cropY, scale, root.width, root.height);
        Box crop = new Box(cropX, cropY, cropRight, cropBottom);
        if (qualifier == null
                || child == null
                || whole == null
                || !contains(crop, whole)
                || !contains(crop, child)
                || !contains(crop, qualifier)
                || whole.area() > 65536
                || !contains(whole, qualifier)
                || !contains(whole, child)
                || mark.before() && qualifier.right > child.left
                || !mark.before() && child.right > qualifier.left) return null;
        return new Group(root, read, parent, qualifier, child, whole);
    }

    private static boolean contains(Box a, Box b) {
        return b.left >= a.left && b.right <= a.right && b.top >= a.top && b.bottom <= a.bottom;
    }

    private static Box wordBox(PlayingTechniqueDetector.Word word, int w, int h) {
        if (word == null
                || !Float.isFinite(word.left() + word.top() + word.right() + word.bottom())
                || word.left() < 0
                || word.top() < 0
                || word.right() > 1
                || word.bottom() > 1
                || word.left() >= word.right()
                || word.top() >= word.bottom()) return null;
        return new Box(
                Math.round(word.left() * w),
                Math.round(word.top() * h),
                Math.round(word.right() * w),
                Math.round(word.bottom() * h));
    }

    private static boolean overlaps(Box a, Box b) {
        return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
    }

    /** One bounded scan per observed parent; word comparisons never rescan raw pixels. */
    private static final class Ink {
        final Box box;
        final int stride;
        final int[] sum;

        Ink(Box box, byte[] gray, int width) {
            this.box = box;
            stride = box.right - box.left + 1;
            sum = new int[stride * (box.bottom - box.top + 1)];
            for (int y = box.top; y < box.bottom; y++) {
                int row = 0, at = (y - box.top + 1) * stride;
                for (int x = box.left; x < box.right; x++) {
                    if ((gray[y * width + x] & 255) < 145) row++;
                    int column = x - box.left + 1;
                    sum[at + column] = sum[at - stride + column] + row;
                }
            }
        }

        int count(Box b) {
            int l = Math.max(box.left, b.left) - box.left;
            int t = Math.max(box.top, b.top) - box.top;
            int r = Math.min(box.right, b.right) - box.left;
            int bottom = Math.min(box.bottom, b.bottom) - box.top;
            if (l >= r || t >= bottom) return 0;
            return sum[bottom * stride + r]
                    - sum[t * stride + r]
                    - sum[bottom * stride + l]
                    + sum[t * stride + l];
        }
    }

    static List<PlayingTechniqueDetector.Word> normalize(
            List<PlayingTechniqueDetector.Word> words,
            Root root,
            List<Group> groups,
            byte[] gray,
            int w,
            int h) {
        if (root == null
                || words == null
                || groups == null
                || words.size() > 256
                || groups.size() > 128
                || !root.current(gray, w, h)) return words;
        // Conflicting physical parent roles refuse the entire acquisition before any removal.
        // Equivalent repeated observations of exactly the same two bodies are one role proof.
        for (int i = 0; i < groups.size(); i++) {
            var a = groups.get(i);
            if (a == null || a.root != root) continue;
            for (int j = i + 1; j < groups.size(); j++) {
                var b = groups.get(j);
                if (b == null || b.root != root || !overlaps(a.whole, b.whole)) continue;
                if (!a.qualifier.equals(b.qualifier)
                        || !a.child.equals(b.child)
                        || !text(a.parent.text()).equals(text(b.parent.text()))) return words;
            }
        }
        boolean[] remove = new boolean[words.size()];
        int remaining = 262144;
        boolean changed = false;
        for (var group : groups) {
            if (group == null || group.root != root) continue;
            int area = group.whole.area();
            if (area > remaining) return words;
            remaining -= area;
            // Actual OCR hierarchy owns one qualifier and one strength body. OCR boxes may
            // cut antialiased fringe; do not invent a new box or demand pixel-complete OCR.
            var ink = new Ink(group.whole, gray, w);
            if (ink.count(group.child) < 4 || ink.count(group.qualifier) < 4) continue;
            int chosen = -1;
            boolean ambiguous = false;
            Box chosenBox = null;
            for (int i = 0; i < words.size(); i++) {
                var word = words.get(i);
                if (!text(word.text()).equals(text(group.parent.text()))) continue;
                Box b = wordBox(word, w, h);
                if (b == null || !contains(group.child, b)) continue;
                // Match the existing detector's containsInk admission; a weak child cannot
                // suppress a stronger accepted parent merely through four isolated pixels.
                if (ink.count(b) < Math.max(4, b.area() * .035)) continue;
                if (chosen >= 0 && !b.equals(chosenBox)) {
                    ambiguous = true;
                    break;
                }
                if (chosen < 0) {
                    chosen = i;
                    chosenBox = b;
                }
            }
            if (chosen < 0 || ambiguous) continue;
            for (int i = 0; i < words.size(); i++) {
                if (i == chosen) continue;
                var word = words.get(i);
                if (!text(word.text()).equals(text(group.parent.text()))) continue;
                Box b = wordBox(word, w, h);
                if (b == null || !contains(group.whole, b)) continue;
                if (!contains(b, chosenBox)) continue;
                var prefix =
                        new Box(
                                Math.max(b.left, group.qualifier.left),
                                Math.max(b.top, group.qualifier.top),
                                Math.min(b.right, group.qualifier.right),
                                Math.min(b.bottom, group.qualifier.bottom));
                boolean hasPrefix = ink.count(prefix) > 0;
                if (!hasPrefix && !b.equals(chosenBox)) continue;
                remove[i] = true;
                changed = true;
            }
        }
        if (!changed || !root.current(gray, w, h)) return words;
        var out = new ArrayList<PlayingTechniqueDetector.Word>();
        for (int i = 0; i < words.size(); i++) if (!remove[i]) out.add(words.get(i));
        return List.copyOf(out);
    }
}
