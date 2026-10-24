// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete raw155 glyph and independent five-rail/required-ledger raster phase.
 * Unused context stays unknown. This does not erase ink or infer a note pitch. */
final class RasterPhaseLedgerOwnership {
    private static final class Unknown extends RuntimeException {}

    private record Profile(int lo, int hi) {
        double middle() {
            return (lo + hi) * .5;
        }

        int width() {
            return hi - lo + 1;
        }
    }

    private static final class Source {
        final byte[] raw;
        final int w, h;
        final Map<Integer, Byte> read = new HashMap<>();
        int work = 1_000_000;

        Source(byte[] g, int width, int height) {
            raw = g;
            w = width;
            h = height;
        }

        int at(int x, int y) {
            if (--work < 0 || x < 0 || y < 0 || x >= w || y >= h) throw new Unknown();
            int p = y * w + x;
            Byte prior = read.putIfAbsent(p, raw[p]);
            if (prior != null && prior != raw[p] || read.size() > 30_000) throw new Unknown();
            return raw[p] & 255;
        }

        boolean unchanged(byte[] g, int width, int height) {
            if (g != raw || width != w || height != h) return false;
            for (var e : read.entrySet()) if (g[e.getKey()] != e.getValue()) return false;
            return true;
        }
    }

    static final class Proof {
        private final Source source;
        private final int[] members;
        private final float nx, ny, gap;
        final int requiredLedgers, staffColumns, referenceMaximumRows;
        final double predictedLow, predictedHigh;

        private Proof(
                Source s,
                int[] p,
                float x,
                float y,
                float g,
                int ledgers,
                int columns,
                int maximum,
                double low,
                double high) {
            source = s;
            members = p.clone();
            nx = x;
            ny = y;
            gap = g;
            requiredLedgers = ledgers;
            staffColumns = columns;
            referenceMaximumRows = maximum;
            predictedLow = low;
            predictedHigh = high;
        }

        boolean matches(byte[] g, int w, int h, int[] p, float x, float y, float nativeGap) {
            return x == nx
                    && y == ny
                    && nativeGap == gap
                    && Arrays.equals(p, members)
                    && source.unchanged(g, w, h);
        }
    }

    static Proof prove(
            byte[] gray, int width, int height, int[] members, float nx, float ny, float gap) {
        try {
            return inspect(gray, width, height, members, nx, ny, gap);
        } catch (Unknown | ArithmeticException refused) {
            return null;
        }
    }

    private static Proof inspect(
            byte[] gray, int w, int h, int[] members, float nx, float ny, float gap) {
        if (gray == null
                || w <= 0
                || h <= 0
                || (long) w * h != gray.length
                || members == null
                || members.length < 3
                || !Float.isFinite(nx + ny + gap)
                || gap < 8
                || gap > 64) return null;
        var source = new Source(gray, w, h);
        var exact = new HashSet<Integer>();
        int l = w, r = -1, t = h, b = -1;
        for (int p : members) {
            if (p < 0 || p >= gray.length || !exact.add(p) || source.at(p % w, p / w) >= 155)
                return null;
            l = Math.min(l, p % w);
            r = Math.max(r, p % w);
            t = Math.min(t, p / w);
            b = Math.max(b, p / w);
        }
        double cy = (t + b) * .5, cx = (l + r) * .5;
        if (Math.abs(cx - nx) > gap * .5
                || r - l + 1 < gap * .6
                || r - l + 1 > gap * 2
                || Math.abs(cy - ny) < gap * .7
                || Math.abs(cy - ny) > gap * 3.2) return null;
        // Authenticate the whole emitted raw155 component, including overlapping
        // bounds and every unseen extension. A partial/clipped vector stays KEEP.
        var pending = new ArrayDeque<Integer>();
        var all = new HashSet<Integer>();
        pending.add(members[0]);
        all.add(members[0]);
        while (!pending.isEmpty()) {
            int p = pending.remove(), x = p % w, y = p / w;
            if (x == 0 || x == w - 1 || y == 0 || y == h - 1) return null;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int q = p + dy * w + dx;
                    if (source.at(x + dx, y + dy) < 155 && all.add(q)) {
                        if (!exact.contains(q)) return null;
                        pending.add(q);
                    }
                }
        }
        if (!all.equals(exact)) return null;
        int direction = cy > ny ? 1 : -1;
        int corridorL = (int) Math.round(nx - gap * 1.5),
                corridorR = (int) Math.round(nx + gap * 1.5);
        if (corridorL < 1 || corridorR >= w - 1) return null;
        for (int first = 1; first <= 3; first++) {
            double expected = direction * cy + first * gap;
            ExactLedgerPhase family =
                    ExactLedgerPhase.initial(
                            ExactLedgerPhase.R.ratio(direction * (t + b), 2)
                                    .add(ExactLedgerPhase.R.of(gap).mul(first)),
                            gap);
            int maximum = 0;
            boolean good = true;
            // EVERY column of EVERY one of the five rails is independently
            // bounded by its complete normal profile and bright flanks. No
            // occluded rail or best four-member fit is substituted.
            for (int x = corridorL; x <= corridorR && good; x++)
                for (int k = 0; k < 5; k++) {
                    double y = cy + direction * (first + k) * gap;
                    Profile p = profile(source, x, y, gap);
                    if (p == null || p.width() > Math.ceil(gap * .35)) {
                        good = false;
                        break;
                    }
                    maximum = Math.max(maximum, p.width());
                    family =
                            family.band(
                                    k,
                                    ExactLedgerPhase.R.ratio(direction * (p.lo + p.hi) - 1, 2),
                                    ExactLedgerPhase.R.ratio(direction * (p.lo + p.hi) + 1, 2));
                    if (family == null) {
                        good = false;
                        break;
                    }
                }
            if (!good) continue;
            // A sixth coextensive rail or competing shifted stack is retained
            // as an unresolved registration, never trimmed to five members.
            if (coextensiveRail(
                            source, corridorL, corridorR, cy + direction * (first - 1) * gap, gap)
                    || coextensiveRail(
                            source, corridorL, corridorR, cy + direction * (first + 5) * gap, gap))
                continue;
            double approximateHeadSteps = (cy - ny) * direction / gap + first;
            int halfStep = (int) Math.round(2 * approximateHeadSteps), ledgers = halfStep / 2;
            if (ledgers < first || ledgers > 6 || halfStep * .5 - first < .7) continue;
            // All independently required intervening ledgers, including the
            // closest own-head stripe, constrain phase BEFORE the target does.
            for (int j = 1; j <= ledgers && good; j++) {
                if (j == first) continue;
                double expectedY = cy + direction * (first - j) * gap;
                int leftGood = 0,
                        rightGood = 0,
                        interiorGood = 0,
                        quarter = Math.max(2, (r - l + 1) / 4);
                for (int x = l; x <= r; x++) {
                    Profile p = profile(source, x, expectedY, gap);
                    boolean outer = x < l + quarter || x > r - quarter;
                    if (p != null && p.width() <= maximum) {
                        family =
                                family.band(
                                        -j,
                                        ExactLedgerPhase.R.ratio(direction * (p.lo + p.hi) - 1, 2),
                                        ExactLedgerPhase.R.ratio(direction * (p.lo + p.hi) + 1, 2));
                        if (family == null) {
                            good = false;
                            break;
                        }
                        if (x < l + quarter) leftGood++;
                        if (x > r - quarter) rightGood++;
                        if (!outer) interiorGood++;
                    } else if (j != ledgers && !outer) {
                        good = false;
                        break;
                    }
                    // At the closest own ledger, the accepted note body may
                    // cooccupy the middle columns. It cannot replace the two
                    // independently observed finite ledger flanks.
                }
                if (leftGood < 2 || rightGood < 2 || family == null) {
                    good = false;
                    break;
                }
                if (j != ledgers && interiorGood < r - l + 1 - 2 * quarter) {
                    good = false;
                    break;
                }
                if (!finiteLedger(source, family, -j, direction, nx, gap, l, r, maximum)) {
                    good = false;
                    break;
                }
            }
            if (!good || family == null) continue;
            // Every remaining reference phase must register the known head on
            // the same half-space and require the same complete ledger stack.
            if (!family.headRegistered(ny, direction, halfStep)) continue;
            var range = family.range(-first, direction);
            double low = range.lowValue(), high = range.highValue();
            // A sampled normal band has one pixel of phase uncertainty. Gray
            // glyph members are covered raster samples, not fully black unit
            // squares. For EVERY reference phase, the strip must account for
            // at least each member's observed darkness as pixel-area coverage.
            // The width envelope is calibrated by all five complete rails.
            for (int p : members)
                if (!ExactLedgerPhase.covers(range, p / w, maximum, source.at(p % w, p / w))) {
                    good = false;
                    break;
                }
            if (!good || b - t + 1 > maximum) continue;
            for (int x = l; x <= r; x++) {
                int count = 0, lo = h, hi = -1;
                for (int p : members)
                    if (p % w == x) {
                        count++;
                        lo = Math.min(lo, p / w);
                        hi = Math.max(hi, p / w);
                    }
                if (count == 0 || hi - lo + 1 != count || count > maximum) {
                    good = false;
                    break;
                }
            }
            if (!good || !finiteLedger(source, family, -first, direction, nx, gap, l, r, maximum))
                continue;
            if (source.unchanged(gray, w, h))
                return new Proof(
                        source,
                        members,
                        nx,
                        ny,
                        gap,
                        ledgers,
                        corridorR - corridorL + 1,
                        maximum,
                        low,
                        high);
        }
        return null;
    }

    private static Profile profile(Source s, int x, double center, float gap) {
        int lo = (int) Math.round(center - gap * .5),
                hi = (int) Math.round(center + gap * .5),
                first = -1,
                last = -1,
                runs = 0;
        boolean before = false;
        for (int y = lo; y <= hi; y++) {
            boolean ink = s.at(x, y) < 155;
            if (ink && !before) {
                runs++;
                first = y;
            }
            if (ink) last = y;
            before = ink;
        }
        if (runs != 1 || first <= lo || last >= hi) return null;
        boolean above = false, below = false;
        for (int y = first - 1; y >= lo; y--)
            if (s.at(x, y) >= 225) {
                above = true;
                break;
            }
        for (int y = last + 1; y <= hi; y++)
            if (s.at(x, y) >= 225) {
                below = true;
                break;
            }
        return above && below ? new Profile(first, last) : null;
    }

    private static boolean coextensiveRail(
            Source s, int left, int right, double center, float gap) {
        for (int x = left; x <= right; x++) {
            Profile p = profile(s, x, center, gap);
            if (p == null || p.width() > Math.ceil(gap * .35)) return false;
        }
        return true;
    }

    private static boolean finiteLedger(
            Source s,
            ExactLedgerPhase family,
            int k,
            int direction,
            float nx,
            float gap,
            int l,
            int r,
            int maximum) {
        var range = family.range(k, direction);
        int top = range.low().sub(ExactLedgerPhase.R.ratio(1, 2)).ceil(),
                bottom = range.high().add(ExactLedgerPhase.R.ratio(1, 2)).floor(),
                inner = Math.max(1, (r - l + 1) / 4),
                x0 = l + inner,
                x1 = r - inner;
        if (top > bottom) return false;
        int unionLeft = s.w, unionRight = -1;
        for (int y = top; y <= bottom; y++) {
            for (int x = x0; x <= x1; x++) if (s.at(x, y) >= 155) return false;
            int left = x0, right = x1;
            while (s.at(left - 1, y) < 155) {
                left--;
                if (nx - left > gap * 1.5) return false;
            }
            while (s.at(right + 1, y) < 155) {
                right++;
                if (right - nx > gap * 1.5) return false;
            }
            if (right - left + 1 < gap * .6
                    || right - left + 1 > gap * 2
                    || Math.abs((left + right) * .5 - nx) > gap * .5) return false;
            unionLeft = Math.min(unionLeft, left);
            unionRight = Math.max(unionRight, right);
        }
        // Required stripes from this one head must share both finite caps up
        // to one raster pixel. A separately short tenuto keeps its own role.
        return Math.abs(unionLeft - l) <= 1 && Math.abs(unionRight - r) <= 1;
    }
}
