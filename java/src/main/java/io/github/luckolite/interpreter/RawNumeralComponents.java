// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** One immutable raw-core graph per source window. No frame, glyph or musical authority is created. */
final class RawNumeralComponents {
    static final class Component {
        private final int[] pixels;
        final int left, top, right, bottom;

        Component(int[] queue, int size, int left, int top, int right, int bottom) {
            pixels = Arrays.copyOf(queue, size);
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int size() {
            return pixels.length;
        }

        int pixel(int i) {
            return pixels[i];
        }
    }

    private final List<Component> components;
    final int left, top, right, bottom;

    private RawNumeralComponents(
            List<Component> components, int left, int top, int right, int bottom) {
        this.components = List.copyOf(components);
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    List<Component> components() {
        return components;
    }

    static RawNumeralComponents capture(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            ObservedFringeContinuation.Charge charge,
            Runnable newComponent) {
        if (left > right || top > bottom)
            throw new IllegalArgumentException("raw core graph bounds");
        int w = right - left + 1, h = bottom - top + 1;
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || w < 1
                || h < 1
                || (long) w * h > 16384)
            throw new IllegalArgumentException("raw core graph bounds");
        BitSet seen = new BitSet(w * h);
        int[] queue = new int[w * h];
        var components = new ArrayList<Component>();
        for (int seed = 0; seed < w * h; seed++) {
            charge.tick();
            int x = left + seed % w, y = top + seed / w;
            if (seen.get(seed) || (gray[y * width + x] & 255) > 165) continue;
            newComponent.run();
            int take = 0, size = 1, l = width, r = -1, t = height, b = -1;
            queue[0] = seed;
            seen.set(seed);
            while (take < size) {
                charge.tick();
                int q = queue[take++], xx = left + q % w, yy = top + q / w;
                l = Math.min(l, xx);
                r = Math.max(r, xx);
                t = Math.min(t, yy);
                b = Math.max(b, yy);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = xx + dx, ny = yy + dy;
                        if (nx < left || nx > right || ny < top || ny > bottom) continue;
                        int next = (ny - top) * w + nx - left;
                        charge.tick();
                        if (!seen.get(next) && (gray[ny * width + nx] & 255) <= 165) {
                            seen.set(next);
                            queue[size++] = next;
                        }
                    }
            }
            // Replace local offsets with original raw coordinates before freezing the private
            // array.
            for (int i = 0; i < size; i++) {
                charge.tick();
                int q = queue[i];
                queue[i] = (top + q / w) * width + left + q % w;
            }
            components.add(new Component(queue, size, l, t, r, b));
        }
        return new RawNumeralComponents(components, left, top, right, bottom);
    }
}
