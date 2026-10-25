// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Private page-owned evidence. No raster sampling; only saved scanner truth is traversed. */
final class PrintedBeamShaftPixels {
    interface Charge {
        void take(long units);
    }

    static final class IdentityFault extends IllegalArgumentException {}

    static final class Frame {
        private final Object source;
        private final byte[] raw;
        private final int width, height, page;
        final Charge charge;

        Frame(Object source, byte[] raw, int width, int height, int page, Charge charge) {
            if (source == null
                    || raw == null
                    || width < 3
                    || height < 3
                    || (long) width * height != raw.length
                    || raw.length > 32000000
                    || page < 0
                    || charge == null) throw new IdentityFault();
            charge.take(64);
            this.source = source;
            this.raw = raw;
            this.width = width;
            this.height = height;
            this.page = page;
            this.charge = charge;
        }

        void check(byte[] raw, int width, int height) {
            if (this.raw != raw || this.width != width || this.height != height)
                throw new IdentityFault();
        }

        void take(long units) {
            charge.take(units);
        }
    }

    static final class Scan {
        final Frame frame;
        private final int left, right, start, end, direction;
        private final List<PrintedBeamShafts.Column> columns;
        private final int[] rows;

        Scan(
                Frame frame,
                int left,
                int right,
                int start,
                int end,
                int direction,
                List<PrintedBeamShafts.Column> columns,
                int[] rows) {
            this.frame = frame;
            this.left = left;
            this.right = right;
            this.start = start;
            this.end = end;
            this.direction = direction;
            int width = right - left + 1, height = Math.abs(end - start) + 1;
            if (width < 1 || width > 22 || height < 1 || height > 769 || rows.length != height)
                throw new IdentityFault();
            frame.take((long) height * (width + 2) + columns.size() + 48L);
            if (columns.size() != width) throw new IdentityFault();
            for (int i = 0; i < width; i++)
                if (columns.get(i).x() != left + i) throw new IdentityFault();
            for (int r = 0; r < height; r++) {
                int y = start + direction * r, allowed = 0;
                for (int i = 0; i < width; i++)
                    if (direction > 0 ? y <= columns.get(i).endY() : y >= columns.get(i).endY())
                        allowed |= 1 << i;
                if ((rows[r] & ~allowed) != 0) throw new IdentityFault();
            }
            this.columns = List.copyOf(columns);
            this.rows = rows.clone();
        }

        boolean matches(PrintedBeamShafts.Shaft shaft) {
            return shaft.pixels() == this
                    && shaft.left() == left
                    && shaft.right() == right
                    && shaft.startY() == start
                    && shaft.endY() == end
                    && shaft.direction() == direction
                    && shaft.columns().equals(columns);
        }

        int row(int y) {
            return direction > 0 ? y - start : start - y;
        }

        boolean has(int x, int y) {
            int r = row(y), b = x - left;
            return r >= 0
                    && r < rows.length
                    && b >= 0
                    && b <= right - left
                    && (rows[r] & (1 << b)) != 0;
        }
    }

    static final class Certificate {
        final Frame frame;
        final Object candidate, binding;
        final int owner;
        final PrintedBeamShafts.Shaft shaft;
        private final int[] connected;

        private Certificate(
                Frame frame,
                Object candidate,
                Object binding,
                int owner,
                PrintedBeamShafts.Shaft shaft,
                int[] connected) {
            this.frame = frame;
            this.candidate = candidate;
            this.binding = binding;
            this.owner = owner;
            this.shaft = shaft;
            this.connected = connected;
        }

        boolean has(int x, int y) {
            Scan s = shaft.pixels();
            int r = s.row(y), b = x - s.left;
            return r >= 0
                    && r < connected.length
                    && b >= 0
                    && b <= s.right - s.left
                    && (connected[r] & (1 << b)) != 0;
        }
    }

    static Certificate certify(
            Frame frame,
            Object candidate,
            Object binding,
            int owner,
            PrintedBeamShafts.Shaft shaft,
            int rootX,
            int rootY) {
        Scan s = shaft.pixels();
        if (candidate == null
                || binding == null
                || owner < 0
                || s == null
                || s.frame != frame
                || !s.matches(shaft)
                || !s.has(rootX, rootY)) throw new IdentityFault();
        int width = s.right - s.left + 1,
                rows = s.rows.length,
                area = Math.multiplyExact(width, rows);
        // Before every flood allocation: pop + eight neighbors + copies/headers; no new pixels
        // read.
        frame.take(Math.addExact(12L * area, 2L * rows + 96));
        int[] connected = new int[rows], queue = new int[area];
        int from = 0, to = 0;
        int rootRow = s.row(rootY), rootBit = rootX - s.left;
        connected[rootRow] |= 1 << rootBit;
        queue[to++] = rootRow * width + rootBit;
        while (from < to) {
            int p = queue[from++], r = p / width, x = p % width;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nr = r + dy, nx = x + dx;
                    if (nr < 0 || nr >= rows || nx < 0 || nx >= width) continue;
                    int bit = 1 << nx;
                    if ((s.rows[nr] & bit) == 0 || (connected[nr] & bit) != 0) continue;
                    connected[nr] |= bit;
                    queue[to++] = nr * width + nx;
                }
        }
        return new Certificate(frame, candidate, binding, owner, shaft, connected);
    }

    static final class Targets {
        final Frame frame;
        final int columnId;
        private final List<Certificate> certificates;

        Targets(Frame frame, int columnId, List<Certificate> certificates) {
            if (frame == null || columnId < 0) throw new IdentityFault();
            frame.take(certificates.size() + 32L);
            this.frame = frame;
            this.columnId = columnId;
            this.certificates = List.copyOf(certificates);
            for (var c : this.certificates) {
                if (c == null || c.frame != frame) throw new IdentityFault();
                frame.take(c.shaft.columns().size() + 1L);
                if (!c.shaft.pixels().matches(c.shaft)) throw new IdentityFault();
            }
        }

        boolean has(byte[] raw, int width, int height, int columnId, int x, int y) {
            frame.check(raw, width, height);
            if (this.columnId != columnId) throw new IdentityFault();
            // Each membership check and each original candidate examined consumes page metadata
            // work.
            frame.take(certificates.size() + 1L);
            for (var c : certificates) if (c.has(x, y)) return true;
            return false;
        }
    }
}
