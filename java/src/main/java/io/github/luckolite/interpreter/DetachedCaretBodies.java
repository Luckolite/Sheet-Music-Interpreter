// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Optional literal components on an immutable, same-entry source. Never replacement gray. */
final class DetachedCaretBodies {
    private DetachedCaretBodies() {}

    static final class Refusal extends RuntimeException {
        private Refusal() {
            super(null, null, false, false);
        }
    }

    private static final Refusal REFUSAL = new Refusal();

    record Body(int left, int top, int right, int bottom, int[] pixels) {}

    static final class Page {
        final byte[] raw, labels;
        final int width, height;
        final Object frame;
        final Thread thread = Thread.currentThread();
        final long stageLimit;
        long producer, admission, bytes;
        volatile boolean failed;
        boolean finished;
        int[] stamp, queue;
        byte[] state;
        int generation;

        Page(byte[] raw, byte[] labels, int width, int height, Object frame) {
            this(
                    raw,
                    labels,
                    width,
                    height,
                    frame,
                    raw == null ? 2_000_000 : Math.max(2_000_000, 2L * raw.length),
                    raw == null ? 2_000_000 : Math.max(2_000_000, 2L * raw.length),
                    8_000_000);
        }

        /** Explicit budgets support exact-boundary original controls; production uses defaults. */
        Page(
                byte[] raw,
                byte[] labels,
                int width,
                int height,
                Object frame,
                long producerBudget,
                long admissionBudget,
                long storage) {
            this.raw = raw;
            this.labels = labels;
            this.width = width;
            this.height = height;
            this.frame = frame;
            stageLimit = raw == null ? 2_000_000 : Math.max(2_000_000, 2L * raw.length);
            producer = Math.min(stageLimit, producerBudget);
            admission = Math.min(stageLimit, admissionBudget);
            bytes = Math.min(8_000_000, storage);
            if (raw == null
                    || labels == null
                    || width < 3
                    || height < 3
                    || (long) width * height != raw.length
                    || labels.length != raw.length
                    || raw.length > 20_000_000
                    || frame == null
                    || producerBudget < 0
                    || admissionBudget < 0
                    || storage < 0) failed = true;
            if (!failed) {
                if (producer < 64 || bytes < 320) failed = true;
                else {
                    producer -= 64;
                    bytes -= 320;
                }
            }
        }

        void refuse() {
            failed = true;
            throw REFUSAL;
        }

        private void check() {
            if (failed || finished || Thread.currentThread() != thread) refuse();
        }

        void p(long work, long storage) {
            check();
            if (work < 0 || storage < 0 || work > producer || storage > bytes) refuse();
            producer -= work;
            bytes -= storage;
        }

        void a(long work, long storage) {
            check();
            if (work < 0 || storage < 0 || work > admission || storage > bytes) refuse();
            admission -= work;
            bytes -= storage;
        }

        boolean finish(byte[] raw, byte[] labels, int width, int height, Object frame) {
            if (failed
                    || finished
                    || Thread.currentThread() != thread
                    || this.raw != raw
                    || this.labels != labels
                    || this.width != width
                    || this.height != height
                    || this.frame != frame) {
                failed = true;
                return false;
            }
            finished = true;
            return true;
        }

        Body grow(int seed, float gap) {
            p(32, 0);
            if (!Float.isFinite(gap) || gap < 3 || gap > 64 || seed < 0 || seed >= raw.length)
                refuse();
            p(2, 0);
            if ((raw[seed] & 255) >= 155) return null;
            int sx = seed % width, sy = seed / width, radius = (int) Math.ceil(gap * 2);
            int l = Math.max(0, sx - radius), r = Math.min(width - 1, sx + radius);
            int t = Math.max(0, sy - radius), b = Math.min(height - 1, sy + radius);
            int w = r - l + 1, h = b - t + 1, area = w * h;
            if (stamp == null || stamp.length < area) {
                p(3L * area + 64, 9L * area + 128);
                stamp = new int[area];
                queue = new int[area];
                state = new byte[area];
            }
            if (generation == Integer.MAX_VALUE) {
                p(stamp.length, 0);
                Arrays.fill(stamp, 0);
                generation = 0;
            }
            int mark = ++generation, read = 0, size = 1;
            queue[0] = seed;
            int origin = (sy - t) * w + sx - l;
            stamp[origin] = mark;
            state[origin] = 1;
            int gl = sx, gr = sx, gt = sy, gb = sy;
            while (read < size) {
                p(16, 0);
                int point = queue[read++], x = point % width, y = point / width;
                gl = Math.min(gl, x);
                gr = Math.max(gr, x);
                gt = Math.min(gt, y);
                gb = Math.max(gb, y);
                // A component cannot shrink back inside either unchanged angular classifier.
                if (gr - gl + 1 > gap * 2.1f + 1 || gb - gt + 1 > gap * 1.7f) return null;
                // Refuse the complete body, never return a clipped/reboxed component.
                if (x == l || x == r || y == t || y == b) return null;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        p(4, 0);
                        int xx = x + dx, yy = y + dy, index = (yy - t) * w + xx - l;
                        if (stamp[index] == mark) continue;
                        stamp[index] = mark;
                        boolean yes = ink(xx, yy, gap);
                        state[index] = (byte) (yes ? 1 : 2);
                        if (yes) queue[size++] = yy * width + xx;
                    }
            }
            p(2L * size + 32, 4L * size + 64);
            return new Body(gl, gt, gr, gb, Arrays.copyOf(queue, size));
        }

        private boolean ink(int x, int y, float gap) {
            p(2, 0);
            int value = raw[y * width + x] & 255;
            if (value < 155) return true;
            // No enclosing >=210 paper can be thirty tones above a value above 225.
            if (value > 225) return false;
            int radius = Math.max(2, (int) Math.ceil(gap * .75));
            return section(x, y, radius, gap, true) || section(x, y, radius, gap, false);
        }

        /** Exact nonshaft enclosing-section law of PrintedBeamRawContrast, transposed as needed. */
        private boolean section(int x, int y, int radius, float gap, boolean horizontal) {
            p(12L * radius + 48, 0);
            int center = horizontal ? x : y, limit = horizontal ? width : height;
            if (center - radius < 0 || center + radius >= limit) return false;
            int stride = horizontal ? 1 : width, base = y * width + x;
            int value = raw[base] & 255, requiredPaper = Math.max(210, value + 30);
            if (requiredPaper > 255) return false;
            int a = 0, b = 0;
            for (int d = 1; d <= radius; d++)
                if ((raw[base - d * stride] & 255) >= requiredPaper) {
                    a = -d;
                    break;
                }
            for (int d = 1; d <= radius; d++)
                if ((raw[base + d * stride] & 255) >= requiredPaper) {
                    b = d;
                    break;
                }
            if (a == 0 || b == 0) return false;
            int paper = Math.min(raw[base + a * stride] & 255, raw[base + b * stride] & 255),
                    minimum = 255;
            for (int d = a + 1; d < b; d++)
                minimum = Math.min(minimum, raw[base + d * stride] & 255);
            if (minimum > 229 || paper - minimum < 30 || 2 * value >= paper + minimum) return false;
            int left = 0, right = 0, threshold = paper + minimum;
            while (left > a && 2 * (raw[base + (left - 1) * stride] & 255) < threshold) left--;
            while (right < b && 2 * (raw[base + (right + 1) * stride] & 255) < threshold) right++;
            return left > a && right < b && right - left + 1 <= Math.max(3, Math.ceil(gap));
        }
    }
}
