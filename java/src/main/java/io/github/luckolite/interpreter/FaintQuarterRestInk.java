// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Literal local classification support. This does not authorize a rest or a source frame. */
final class FaintQuarterRestInk {
    private FaintQuarterRestInk() {}

    static final class Refusal extends RuntimeException {
        Refusal() {
            super(null, null, false, false);
        }
    }

    static void charge(Budget budget, long work, long bytes) {
        if (budget != null && !budget.prepay(work, bytes)) throw new Refusal();
    }

    static <T> T unsupported() {
        throw new Refusal();
    }

    static Integer boxInt(Budget budget, int value) {
        charge(budget, 1, 32);
        return Integer.valueOf(value);
    }

    static Float boxFloat(Budget budget, float value) {
        charge(budget, 1, 32);
        return Float.valueOf(value);
    }

    static <T> boolean add(Budget budget, java.util.Collection<T> collection, T value) {
        charge(budget, 1, 128);
        return collection.add(value);
    }

    static <T> boolean remove(Budget budget, java.util.List<T> list, T value) {
        // Object-search comparisons and the possible suffix shift; no new data allocation.
        if (budget != null) charge(budget, 2L * list.size() + 1, 0);
        return list.remove(value);
    }

    static <T> T set(Budget budget, java.util.List<T> list, int index, T value) {
        charge(budget, 1, 0);
        return list.set(index, value);
    }

    static <K, V> V putAbsent(Budget budget, java.util.Map<K, V> map, K key, V value) {
        charge(budget, 1, 128);
        return map.putIfAbsent(key, value);
    }

    static <T> void sort(
            Budget budget, java.util.List<T> list, java.util.Comparator<? super T> original) {
        if (budget != null) charge(budget, list.size() + 1L, 64L + 8L * list.size());
        list.sort(comparator(budget, original));
    }

    static <T> boolean removeIf(
            Budget budget,
            java.util.Collection<T> list,
            java.util.function.Predicate<? super T> original) {
        if (budget != null) charge(budget, list.size() + 1L, 64L + 8L * ((list.size() + 63L) / 64));
        return list.removeIf(predicate(budget, original));
    }

    static void fill(Budget b, int[] a, int value) {
        if (b != null && a != null) charge(b, a.length, 0);
        Arrays.fill(a, value);
    }

    static void fill(Budget b, double[] a, double value) {
        if (b != null && a != null) charge(b, a.length, 0);
        Arrays.fill(a, value);
    }

    static void fill(Budget b, byte[] a, byte value) {
        if (b != null && a != null) charge(b, a.length, 0);
        Arrays.fill(a, value);
    }

    static void fill(Budget b, boolean[] a, boolean value) {
        if (b != null && a != null) charge(b, a.length, 0);
        Arrays.fill(a, value);
    }

    static void fill(Budget b, boolean[] a, int first, int last, boolean value) {
        if (b != null) charge(b, Math.max(0L, (long) last - first), 0);
        Arrays.fill(a, first, last, value);
    }

    static void arraycopy(Budget b, Object source, int first, Object dest, int offset, int length) {
        if (b != null) charge(b, Math.max(0, length), 0);
        System.arraycopy(source, first, dest, offset, length);
    }

    static <T> java.util.Comparator<T> comparator(Budget budget, java.util.Comparator<T> original) {
        if (budget == null) return original;
        charge(budget, 1, 64);
        return (a, b) -> {
            charge(budget, 1, 0);
            return original.compare(a, b);
        };
    }

    static <T> java.util.function.Predicate<T> predicate(
            Budget budget, java.util.function.Predicate<T> original) {
        if (budget == null) return original;
        charge(budget, 1, 64);
        return value -> {
            charge(budget, 1, 0);
            return original.test(value);
        };
    }

    /** Charged helper work/storage, distinct from the caller's remaining detector work. */
    interface ParentCharge {
        void take(long work, long bytes);
    }

    static final class Budget {
        private final ParentCharge parent;
        final long workLimit, byteLimit;
        long workUsed, bytesUsed;
        boolean exhausted;

        Budget(long workLimit, long byteLimit) {
            if (workLimit < 0 || byteLimit < 0) throw new IllegalArgumentException();
            this.workLimit = workLimit;
            this.byteLimit = byteLimit;
            this.parent = null;
        }

        /** Resource readiness only; a parent charge supplies no musical evidence. */
        Budget(long workLimit, long byteLimit, ParentCharge parent) {
            if (workLimit < 0 || byteLimit < 0 || parent == null)
                throw new IllegalArgumentException();
            this.workLimit = workLimit;
            this.byteLimit = byteLimit;
            this.parent = parent;
        }

        boolean prepay(long work, long bytes) {
            if (work < 0 || bytes < 0) throw new IllegalArgumentException();
            if (exhausted || work > workLimit - workUsed || bytes > byteLimit - bytesUsed) {
                exhausted = true;
                return false;
            }
            if (parent != null) {
                try {
                    parent.take(work, bytes);
                } catch (RuntimeException | Error failure) {
                    exhausted = true;
                    throw failure;
                }
            }
            workUsed += work;
            bytesUsed += bytes;
            return true;
        }

        /** Resource readiness only; never a pixel, frame or ownership assertion. */
        boolean allocate(long work, long bytes) {
            charge(this, work, bytes);
            return true;
        }
    }

    /** Bound to one actual raw array and one unchanged proposed physical phase. */
    static final class Scan {
        private final byte[] raw;
        private final int width, height, radius;
        private final float top, bottom, gap;
        private final boolean[] rules;
        private final Budget budget;

        private Scan(
                byte[] raw,
                int width,
                int height,
                float top,
                float bottom,
                float gap,
                boolean[] rules,
                Budget budget) {
            this.raw = raw;
            this.width = width;
            this.height = height;
            this.top = top;
            this.bottom = bottom;
            this.gap = gap;
            this.rules = rules;
            this.budget = budget;
            radius = Math.max(3, Math.round(gap * .6f));
        }

        private boolean foreground(int x, int y) {
            int value = raw[y * width + x] & 255;
            if (value < 170) return true;
            if (value >= 230
                    || x < radius
                    || x + radius >= width
                    || y < radius
                    || y + radius >= height) return false;
            int brighter = 0;
            if ((raw[y * width + x - radius] & 255) >= value + 20) brighter++;
            if ((raw[y * width + x + radius] & 255) >= value + 20) brighter++;
            if ((raw[(y - radius) * width + x] & 255) >= value + 20) brighter++;
            if ((raw[(y + radius) * width + x] & 255) >= value + 20) brighter++;
            return brighter >= 2;
        }

        /** Copies metadata from the actual dark column producer; never a semantic owner. */
        Seed seed(int left, int right, int scanTop, int scanBottom, boolean[] mask, int[] columns) {
            if (left < 0
                    || right >= width
                    || left > right
                    || scanTop < 0
                    || scanBottom >= height
                    || scanTop > scanBottom
                    || mask == null
                    || mask.length != scanBottom - scanTop + 1
                    || columns == null
                    || columns.length != width) return null;
            if (!budget.prepay(64L + right - left + 1 + mask.length, 96L + mask.length))
                return null;
            if (left > 0 && columns[left - 1] >= 2 || right + 1 < width && columns[right + 1] >= 2)
                return null;
            for (int x = left; x <= right; x++)
                if (columns[x] < 2 || columns[x] > mask.length) return null;
            return new Seed(this, left, right, scanTop, scanBottom, mask.clone());
        }

        Candidate candidate(Seed seed) {
            return seed == null || seed.scan != this
                    ? null
                    : candidate(seed.left, seed.right, seed);
        }

        /** Private feasibility only. Production must use its actual producer-issued Seed. */
        Candidate candidateForMechanism(int left, int right) {
            return candidate(left, right, null);
        }

        private Candidate candidate(int seedLeft, int seedRight, Seed seed) {
            if (seedLeft < 0
                    || seedRight >= width
                    || seedLeft > seedRight
                    || seedRight - seedLeft + 1 < gap * .35f
                    || seedRight - seedLeft + 1 > gap * 2) return null;
            int left = Math.max(0, (int) Math.floor(seedLeft - gap * .65f));
            int right = Math.min(width - 1, (int) Math.ceil(seedRight + gap * .65f));
            int first = Math.max(0, (int) Math.floor(top + gap * .1f));
            int last = Math.min(height - 1, (int) Math.ceil(bottom - gap * .05f));
            int w = right - left + 1, h = last - first + 1;
            if (w < 3 || h < 3) return null;
            long area = (long) w * h;
            // Fixed local arrays, cardinal samples, crossing scans, flood and closure census.
            if (!budget.prepay(128 * area + 256L * h + 512, 6 * area + 16L * h + 512)) return null;
            byte[] support = new byte[(int) area];
            byte[] crossing = new byte[(int) area];
            int[] queue = new int[(int) area];
            int[] lo = new int[h], hi = new int[h];
            Arrays.fill(lo, -1);
            Arrays.fill(hi, -1);
            for (int y = first; y <= last; y++) {
                if (rules[y]) continue;
                int runs = 0;
                boolean prior = false;
                for (int x = left; x <= right; x++) {
                    boolean ink = foreground(x, y);
                    if (ink) {
                        support[(y - first) * w + x - left] = 1;
                        if (!prior) runs++;
                        if (lo[y - first] < 0) lo[y - first] = x;
                        hi[y - first] = x;
                    }
                    prior = ink;
                }
                if (runs > 1) lo[y - first] = -2;
            }
            for (int y = first; y <= last; y++) {
                if (!rules[y]) continue;
                int before = y - 1, after = y + 1;
                while (before >= first && rules[before]) before--;
                while (after <= last && rules[after]) after++;
                if (before < first || after > last) continue;
                int a = before - first, c = after - first;
                if (lo[a] < 0
                        || lo[c] < 0
                        || Math.max(Math.abs(lo[a] - lo[c]), Math.abs(hi[a] - hi[c]))
                                > after - before + gap * .2f) continue;
                double fraction = (double) (y - before) / (after - before);
                int start = (int) Math.floor(lo[a] * (1 - fraction) + lo[c] * fraction) - 1;
                int end = (int) Math.ceil(hi[a] * (1 - fraction) + hi[c] * fraction) + 1;
                for (int x = Math.max(left, start); x <= Math.min(right, end); x++) {
                    // A ruled crossing can use existing raw ink, never a painted white gap.
                    if ((raw[y * width + x] & 255) < 230) support[(y - first) * w + x - left] = 1;
                }
            }
            // Explicit paper at the crop boundary; unclassified gray is not paper.
            for (int y = first; y <= last; y++)
                if (!rules[y]) {
                    if ((raw[y * width + left] & 255) < 230 || (raw[y * width + right] & 255) < 230)
                        return null;
                    if (y == first || y == last)
                        for (int x = left; x <= right; x++)
                            if ((raw[y * width + x] & 255) < 230) return null;
                }
            // Every admitted rule-band fragment must connect both unique approach spans.
            for (int y = first; y <= last; y++)
                if (rules[y]) {
                    int end = y;
                    while (end < last && rules[end + 1]) end++;
                    int before = y - 1, after = end + 1;
                    if (before >= first && after <= last) {
                        boolean any = false;
                        for (int row = y; row <= end; row++)
                            for (int x = 0; x < w; x++) any |= support[(row - first) * w + x] != 0;
                        if (any) {
                            Arrays.fill(crossing, (byte) 0);
                            for (int direction = 0; direction < 2; direction++) {
                                int edge = direction == 0 ? before : after, from = 0, to = 0;
                                byte bit = (byte) (1 << direction);
                                for (int x = 0; x < w; x++) {
                                    int at = (edge - first) * w + x;
                                    if (support[at] != 0) {
                                        crossing[at] |= bit;
                                        queue[to++] = at;
                                    }
                                }
                                while (from < to) {
                                    int at = queue[from++], cx = at % w, cy = at / w + first;
                                    for (int yy = Math.max(before, cy - 1);
                                            yy <= Math.min(after, cy + 1);
                                            yy++)
                                        for (int xx = Math.max(0, cx - 1);
                                                xx <= Math.min(w - 1, cx + 1);
                                                xx++) {
                                            int next = (yy - first) * w + xx;
                                            if (support[next] != 0 && (crossing[next] & bit) == 0) {
                                                crossing[next] |= bit;
                                                queue[to++] = next;
                                            }
                                        }
                                }
                            }
                            for (int row = y; row <= end; row++)
                                for (int x = 0; x < w; x++) {
                                    int at = (row - first) * w + x;
                                    if (support[at] != 0 && crossing[at] != 3) return null;
                                }
                        }
                    }
                    y = end;
                }
            int total = 0, start = -1;
            for (int at = 0; at < support.length; at++) {
                if (support[at] != 0) {
                    total++;
                    if (start < 0) start = at;
                }
            }
            if (start < 0) return null;
            int count = 1;
            queue[0] = start;
            support[start] = 2;
            int x0 = w, x1 = -1, y0 = h, y1 = -1;
            for (int cursor = 0; cursor < count; cursor++) {
                int at = queue[cursor], x = at % w, y = at / w;
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) return null;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
                for (int yy = Math.max(0, y - 1); yy <= Math.min(h - 1, y + 1); yy++) {
                    for (int xx = Math.max(0, x - 1); xx <= Math.min(w - 1, x + 1); xx++) {
                        int next = yy * w + xx;
                        if (support[next] == 1) {
                            support[next] = 2;
                            queue[count++] = next;
                        }
                    }
                }
            }
            // Independent fragments/noise cannot be silently removed from the candidate window.
            if (count != total) return null;
            int seedPixels = 0, seedRows = 0;
            for (int y = y0; y <= y1; y++) {
                if (rules[first + y]) continue;
                if (seed != null
                        && (first + y < seed.top
                                || first + y > seed.bottom
                                || seed.mask[first + y - seed.top])) continue;
                int row = 0;
                for (int x = Math.max(x0, seedLeft - left);
                        x <= Math.min(x1, seedRight - left);
                        x++) {
                    if (support[y * w + x] == 2
                            && (raw[(first + y) * width + left + x] & 255) < 155) row++;
                }
                seedPixels += row;
                if (row >= 2) seedRows++;
            }
            if (seedPixels < 12 || seedRows < 3) return null;
            return new Candidate(this, support, left, first, w, h, x0, x1, y0, y1);
        }
    }

    static final class Seed {
        private final Scan scan;
        private final int left, right, top, bottom;
        private final boolean[] mask;

        private Seed(Scan scan, int left, int right, int top, int bottom, boolean[] mask) {
            this.scan = scan;
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.mask = mask;
        }
    }

    static Scan open(
            byte[] raw, int width, int height, float top, float bottom, float gap, Budget budget) {
        if (raw == null
                || width <= 0
                || width > 8192
                || height <= 0
                || (long) width * height > 20_000_000
                || raw.length != (long) width * height
                || budget == null
                || !Float.isFinite(top)
                || !Float.isFinite(bottom)
                || !Float.isFinite(gap)
                || top < 0
                || bottom >= height
                || top >= bottom
                || gap < 4
                || gap > 64
                || Math.abs(bottom - top - 4 * gap) > gap * .3f) return null;
        int rows = 0;
        int radius = (int) Math.ceil(gap * .35f);
        for (int line = 0; line < 5; line++) {
            int center = Math.round(top + line * gap);
            rows +=
                    Math.max(
                            0,
                            Math.min(height - 1, center + radius)
                                    - Math.max(0, center - radius)
                                    + 1);
        }
        if (!budget.prepay(3L * rows * width + 64L * rows + 256, height + 256L)) return null;
        boolean[] rules = new boolean[height];
        for (int line = 0; line < 5; line++) {
            float center = top + line * gap;
            boolean found = false, ended = false;
            for (int y = Math.max(0, (int) Math.floor(center - gap * .35f));
                    y <= Math.min(height - 1, (int) Math.ceil(center + gap * .35f));
                    y++) {
                if (Math.abs(y - center) > gap * .35f) continue;
                int total = 0, run = 0, longest = 0;
                for (int x = 0; x < width; x++) {
                    if ((raw[y * width + x] & 255) < 230) {
                        total++;
                        run++;
                        longest = Math.max(longest, run);
                    } else run = 0;
                }
                boolean rule = total > width * .25f || longest >= gap * 5;
                if (rule && ended) return null;
                if (rule) found = true;
                else if (found) ended = true;
                rules[y] = rule;
            }
        }
        return new Scan(raw, width, height, top, bottom, gap, rules, budget);
    }

    /** Shape-classification material only. Physical frame, rest and rivals remain caller gates. */
    static final class Candidate {
        private final Scan scan;
        private final byte[] support;
        final int left, top, width, height, x0, x1, y0, y1;

        private Candidate(
                Scan scan,
                byte[] support,
                int left,
                int top,
                int width,
                int height,
                int x0,
                int x1,
                int y0,
                int y1) {
            this.scan = scan;
            this.support = support;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.x0 = x0;
            this.x1 = x1;
            this.y0 = y0;
            this.y1 = y1;
        }

        byte[] classificationCopy() {
            if (!scan.budget.prepay(2L * support.length + 16, support.length + 32L)) return null;
            byte[] copy = new byte[support.length];
            Arrays.fill(copy, (byte) 255);
            for (int at = 0; at < support.length; at++) if (support[at] == 2) copy[at] = (byte) 169;
            return copy;
        }

        // Private caller material: copy the SAME raw view and alter only witnessed pixels.
        byte[] promotedView() {
            if (!scan.budget.prepay(
                    scan.raw.length + 2L * support.length + 32, scan.raw.length + 32L)) return null;
            byte[] copy = scan.raw.clone();
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    if (support[y * width + x] == 2) {
                        int at = (top + y) * scan.width + left + x;
                        if ((copy[at] & 255) >= 170) copy[at] = (byte) 169;
                    }
            return copy;
        }

        boolean[] ruleRowsCopy() {
            if (!scan.budget.prepay(height + 16L, height + 32L)) return null;
            return Arrays.copyOfRange(scan.rules, top, top + height);
        }
    }
}
