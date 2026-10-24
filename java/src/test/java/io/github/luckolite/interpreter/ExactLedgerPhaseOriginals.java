// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Exact feasible-family envelope controls independent of raster hypotheses. */
public final class ExactLedgerPhaseOriginals {
    public static void main(String[] args) {
        var f =
                ExactLedgerPhase.initial(ExactLedgerPhase.R.of(100), 10)
                        .band(0, ExactLedgerPhase.R.ratio(199, 2), ExactLedgerPhase.R.ratio(201, 2))
                        .band(
                                4,
                                ExactLedgerPhase.R.ratio(279, 2),
                                ExactLedgerPhase.R.ratio(281, 2));
        var r = f.range(-2, 1);
        int comparisons = 0;
        for (int c = 398; c <= 402; c++)
            for (int g = 38; g <= 42; g++) {
                var phase = ExactLedgerPhase.R.ratio(c, 4);
                var gap = ExactLedgerPhase.R.ratio(g, 4);
                var last = phase.add(gap.mul(4));
                boolean valid =
                        last.compareTo(ExactLedgerPhase.R.ratio(279, 2)) >= 0
                                && last.compareTo(ExactLedgerPhase.R.ratio(281, 2)) <= 0;
                if (valid) {
                    var target = phase.sub(gap.mul(2));
                    if (target.compareTo(r.low()) < 0 || target.compareTo(r.high()) > 0)
                        throw new AssertionError("feasible reference model lost");
                    comparisons++;
                }
            }
        if (!r.low().equals(ExactLedgerPhase.R.of(79))
                || !r.high().equals(ExactLedgerPhase.R.of(81)))
            throw new AssertionError("exact full-family extrema");
        var point =
                f.band(0, ExactLedgerPhase.R.of(100), ExactLedgerPhase.R.of(100))
                        .band(4, ExactLedgerPhase.R.of(140), ExactLedgerPhase.R.of(140));
        if (!point.range(-2, 1).low().equals(ExactLedgerPhase.R.of(80)))
            throw new AssertionError("zero-area exact family retained");
        if (point.band(-1, ExactLedgerPhase.R.of(92), ExactLedgerPhase.R.of(93)) != null)
            throw new AssertionError("infeasible alternative retained as proof");
        for (int shade = 0; shade < 256; shade++)
            for (int row = 77; row <= 83; row++) {
                boolean complete = ExactLedgerPhase.covers(r, row, 4, shade);
                boolean all = true;
                for (int center = 316; center <= 324; center++) {
                    double c = center / 4.,
                            overlap =
                                    Math.max(
                                            0,
                                            Math.min(row + .5, c + 2.5)
                                                    - Math.max(row - .5, c - 2.5));
                    if (overlap + 1e-12 < (255 - shade) / 255.) all = false;
                }
                if (complete != all) throw new AssertionError("partial pixel-area coverage family");
                comparisons++;
            }
        System.out.println("PASS exactReferenceFamilyAndPixelAreaComparisons=" + comparisons);
    }
}
