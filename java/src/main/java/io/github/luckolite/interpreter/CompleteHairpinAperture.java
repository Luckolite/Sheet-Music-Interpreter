// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete column evidence for paper between two already corroborated straight arms. */
final class CompleteHairpinAperture {
    enum Role {
        ARMS,
        MERGED_TIP,
        BAR
    }

    record Band(int first, int last) {
        int width() {
            return last - first + 1;
        }
    }

    record Column(
            int x,
            int firstExamined,
            int lastExamined,
            List<Band> bands,
            Role role,
            Band barExtent) {
        Column {
            bands = List.copyOf(bands);
        }
    }

    record FringeJoin(int x, int row, int shade, int upperCore, int lowerCore) {}

    record Proof(
            List<Column> columns,
            int upperMin,
            int upperMax,
            int lowerMin,
            int lowerMax,
            int barContacts,
            List<FringeJoin> fringeJoins) {
        Proof {
            columns = List.copyOf(columns);
            fringeJoins = List.copyOf(fringeJoins);
        }
    }

    static final class Budget {
        long remaining;
        boolean refused;

        Budget(long remaining) {
            this.remaining = remaining;
        }

        boolean take(long amount) {
            if (amount < 0 || amount > remaining) {
                refused = true;
                return false;
            }
            remaining -= amount;
            return true;
        }
    }

    private CompleteHairpinAperture() {}

    static Optional<Proof> prove(
            byte[] gray,
            int w,
            int h,
            int left,
            int right,
            double[] up,
            double[] lo,
            boolean[] barColumns,
            float gap,
            Budget budget) {
        if (gray == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || left < 0
                || right >= w
                || right <= left
                || up == null
                || lo == null
                || up.length != 2
                || lo.length != 2
                || barColumns == null
                || barColumns.length != right - left + 1
                || !Float.isFinite(gap)
                || gap < 4
                || gap > 100
                || !Double.isFinite(up[0] + up[1] + lo[0] + lo[1])
                || up[0] * lo[0] >= 0) return Optional.empty();
        int n = right - left + 1;
        if (!budget.take(n * 32L + 64)) return Optional.empty();
        var columns = new ArrayList<Column>();
        int upperMin = Integer.MAX_VALUE,
                lowerMin = Integer.MAX_VALUE,
                upperMax = 0,
                lowerMax = 0,
                openColumns = 0,
                bars = 0;
        int margin = Math.max(3, (int) Math.ceil(gap * .3));
        for (int i = 0; i < n; i++) {
            int x = left + i,
                    uy = (int) Math.round(up[1] + up[0] * i),
                    ly = (int) Math.round(lo[1] + lo[0] * i),
                    first = uy - margin,
                    last = ly + margin;
            if (first < 1 || last >= h - 1 || ly < uy || last - first > gap * 3 + 2 * margin)
                return Optional.empty();
            if (!budget.take((last - first + 1) * 6L + 32)) return Optional.empty();
            boolean nearBar =
                    barColumns[i] || i > 0 && barColumns[i - 1] || i + 1 < n && barColumns[i + 1];
            if (nearBar) {
                Band bar = barExtent(gray, w, h, x, (uy + ly) / 2, gap, budget);
                if (bar != null && bar.first() <= first && bar.last() >= last) {
                    columns.add(new Column(x, first, last, List.of(), Role.BAR, bar));
                    bars++;
                    continue;
                }
                if (budget.refused || barColumns[i]) return Optional.empty();
            }
            var bands = new ArrayList<Band>();
            for (int y = first; y <= last; ) {
                if ((gray[y * w + x] & 255) >= 225) {
                    y++;
                    continue;
                }
                int start = y;
                while (y <= last && (gray[y * w + x] & 255) < 225) y++;
                bands.add(new Band(start, y - 1));
            }
            if (bands.isEmpty()
                    || bands.size() > 2
                    || bands.get(0).first() == first
                    || bands.get(bands.size() - 1).last() == last) return Optional.empty();
            Band top = bands.get(0), bottom = bands.get(bands.size() - 1);
            if (Math.abs(top.first() - uy) > margin || Math.abs(bottom.last() - ly) > margin)
                return Optional.empty();
            if (bands.size() == 2) {
                int a = top.width(), b = bottom.width();
                upperMin = Math.min(upperMin, a);
                upperMax = Math.max(upperMax, a);
                lowerMin = Math.min(lowerMin, b);
                lowerMax = Math.max(lowerMax, b);
                openColumns++;
                columns.add(new Column(x, first, last, bands, Role.ARMS, null));
            } else columns.add(new Column(x, first, last, bands, Role.MERGED_TIP, null));
        }
        // Gray fringes may merge at the real closed tip. They cannot explain a filled
        // wide interior, a new central glyph, or a reclosure beyond the open arm pair.
        if (openColumns < n * .25
                || upperMax > Math.ceil(gap * .4)
                || lowerMax > Math.ceil(gap * .4)
                || upperMax - upperMin > 2
                || lowerMax - lowerMin > 2) return Optional.empty();
        var fringeJoins = new ArrayList<FringeJoin>();
        boolean opened = false;
        int step = lo[0] > up[0] ? 1 : -1;
        for (int k = 0; k < n; k++) {
            Column c = columns.get(step > 0 ? k : n - 1 - k);
            if (c.role() == Role.BAR) continue;
            if (c.role() == Role.MERGED_TIP) {
                if (opened) return Optional.empty();
                Band tip = c.bands().get(0);
                if (tip.width() > upperMax + lowerMax) {
                    // At a sampled union, two antialiased fringes can contribute to one extra
                    // pixel between their separately measured supports. Retain that exact gray
                    // contact and both strict arm cores; opaque or wider fills cannot borrow it.
                    if (tip.width() != upperMax + lowerMax + 1
                            || !budget.take(tip.width() * 2L + 8)) return Optional.empty();
                    int row = tip.first() + upperMax,
                            shade = gray[row * w + c.x()] & 255,
                            upperCore = -1,
                            lowerCore = -1;
                    if (shade < 145 || shade >= 225) return Optional.empty();
                    for (int y = tip.first(); y < row; y++)
                        if ((gray[y * w + c.x()] & 255) < 145) {
                            upperCore = y;
                            break;
                        }
                    for (int y = row + 1; y <= tip.last(); y++)
                        if ((gray[y * w + c.x()] & 255) < 145) {
                            lowerCore = y;
                            break;
                        }
                    if (upperCore < 0 || lowerCore < 0) return Optional.empty();
                    fringeJoins.add(new FringeJoin(c.x(), row, shade, upperCore, lowerCore));
                }
            } else if (c.bands().get(1).first() - c.bands().get(0).last() - 1 >= 2) opened = true;
        }
        return Optional.of(
                new Proof(columns, upperMin, upperMax, lowerMin, lowerMax, bars, fringeJoins));
    }

    private static Band barExtent(
            byte[] g, int w, int h, int x, int center, float gap, Budget budget) {
        if ((g[center * w + x] & 255) >= 225) return null;
        int top = center, bottom = center;
        for (int direction : new int[] {-1, 1}) {
            int blank = 0, last = center;
            for (int d = 1; d <= Math.ceil(gap * 24) + 2; d++) {
                int y = center + direction * d;
                if (y < 0 || y >= h) break;
                if (!budget.take(4)) return null;
                if ((g[y * w + x] & 255) < 225) {
                    last = y;
                    blank = 0;
                } else if (++blank == 2) break;
            }
            if (direction < 0) top = last;
            else bottom = last;
        }
        int count = bottom - top + 1;
        if (count < gap * 8 || count > gap * 24) return null;
        if (!budget.take(count * 2L)) return null;
        int hits = 0;
        for (int y = top; y <= bottom; y++) if ((g[y * w + x] & 255) < 225) hits++;
        return hits >= count * .92 ? new Band(top, bottom) : null;
    }
}
