// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Source-free controls for literal five-rail evidence at a shaft crossing. */
public final class FiveRailCrossingOriginal {
    private static final int W = 96, H = 80;
    private static final byte HEAD = 2;

    private record Page(byte[] gray, byte[] labels) {}

    private static Page page(boolean missingRail, boolean foreignLobe, boolean brokenCrossing) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        java.util.Arrays.fill(gray, (byte) 248);
        for (int y = 46; y <= 54; y++)
            for (int x = 35; x <= 45; x++) {
                double oval =
                        ((x - 40) / 5.0) * ((x - 40) / 5.0) + ((y - 50) / 4.0) * ((y - 50) / 4.0);
                if (oval <= 1.0) {
                    labels[y * W + x] = HEAD;
                    gray[y * W + x] = 20;
                }
            }
        // A complete attached shaft climbs left of the head through two rules.
        for (int y = 25; y <= 50; y++) gray[y * W + 34] = 25;
        int[][] rails = {{20, 21}, {30, 31}, {40, 41}, {50, 51}, {60, 61}};
        for (int i = 0; i < rails.length; i++) {
            for (int y = rails[i][0]; y <= rails[i][1]; y++)
                for (int x = 8; x <= 87; x++) {
                    if (missingRail && i == 4 && x >= 65) continue;
                    if (brokenCrossing && i == 2 && x == 48) continue;
                    gray[y * W + x] = 30;
                }
        }
        // A bright-side foreign lobe changes the literal profile in only one clear window.
        if (foreignLobe) for (int y = 18; y <= 20; y++) gray[y * W + 65] = 30;
        return new Page(gray, labels);
    }

    private static NaturalHarmonicRingProof.HeadAssessment assess(Page page) {
        var note =
                new NaturalHarmonicRingProof.NoteIdentity(
                        "synthetic-note", 0, 0, 1, 2, 40f, 50f, 10f, 20f, .5f, .5f);
        return NaturalHarmonicRingProof.assessRawHeadAndStem(
                page.gray,
                page.labels,
                W,
                H,
                HEAD,
                note,
                new NaturalHarmonicRingProof.Budget(2_000_000, 8_000_000));
    }

    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }

    public static void main(String[] args) {
        var positive = assess(page(false, false, false));
        check(
                positive.decision() == NaturalHarmonicRingProof.HeadDecision.OWNED
                        && positive.head() != null
                        && positive.head().attachedStem(),
                "same measured five-rail frame plus raw crossing should preserve the attached"
                        + " shaft");
        var missing = assess(page(true, false, false));
        check(
                missing.decision() == NaturalHarmonicRingProof.HeadDecision.ATTACHED_STEM_NOT_PROVEN
                        && missing.head() == null,
                "missing exterior rail witness must remain unknown");
        var foreign = assess(page(false, true, false));
        check(
                foreign.decision() == NaturalHarmonicRingProof.HeadDecision.ATTACHED_STEM_NOT_PROVEN
                        && foreign.head() == null,
                "a one-sided foreign lobe must break literal frame identity");
        var broken = assess(page(false, false, true));
        check(
                broken.decision() == NaturalHarmonicRingProof.HeadDecision.ATTACHED_STEM_NOT_PROVEN
                        && broken.head() == null,
                "paper break through crossing must not be filled");
        System.out.println("4 literal five-rail staff-crossing controls PASS");
    }
}
