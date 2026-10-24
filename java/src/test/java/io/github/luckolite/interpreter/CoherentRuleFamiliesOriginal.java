// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Full caller controls for two coherent ledger crossings plus a measured staff crossing. */
public final class CoherentRuleFamiliesOriginal {
    private static final int W = 128, H = 144;
    private static final byte HEAD = 2;

    private record Page(byte[] gray, byte[] labels) {}

    private static Page page(boolean unmatchedForeignBand) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        java.util.Arrays.fill(gray, (byte) 248);
        for (int y = 52; y <= 62; y++)
            for (int x = 54; x <= 66; x++) {
                double oval =
                        ((x - 60) / 6.0) * ((x - 60) / 6.0) + ((y - 57) / 5.0) * ((y - 57) / 5.0);
                if (oval <= 1.0) {
                    labels[y * W + x] = HEAD;
                    gray[y * W + x] = 20;
                }
            }
        for (int y = 22; y <= 59; y++) gray[y * W + 59] = 24;
        for (int y = 23; y <= 24; y++) for (int x = 43; x <= 77; x++) gray[y * W + x] = 32;
        for (int y = 40; y <= 41; y++) for (int x = 43; x <= 77; x++) gray[y * W + x] = 32;
        int[] rails = {57, 74, 91, 108, 125};
        for (int y0 : rails)
            for (int y = y0; y <= y0 + 1; y++) for (int x = 8; x <= 119; x++) gray[y * W + x] = 30;
        if (unmatchedForeignBand) for (int x = 43; x <= 77; x++) gray[47 * W + x] = 28;
        return new Page(gray, labels);
    }

    private static NaturalHarmonicRingProof.HeadAssessment assess(Page page) {
        var note =
                new NaturalHarmonicRingProof.NoteIdentity(
                        "ledger-staff", 0, 0, 1, 2, 60f, 57f, 17f, 57f, .5f, .5f);
        return NaturalHarmonicRingProof.assessRawHeadAndStem(
                page.gray,
                page.labels,
                W,
                H,
                HEAD,
                note,
                new NaturalHarmonicRingProof.Budget(2_000_000, 8_000_000));
    }

    public static void main(String[] args) {
        var positive = assess(page(false));
        if (positive.decision() != NaturalHarmonicRingProof.HeadDecision.OWNED
                || positive.head() == null
                || !positive.head().attachedStem())
            throw new AssertionError(
                    "coherent ledger pair plus measured staff crossing should preserve shaft: "
                            + positive);
        var foreign = assess(page(true));
        if (foreign.decision() != NaturalHarmonicRingProof.HeadDecision.ATTACHED_STEM_NOT_PROVEN
                || foreign.head() != null)
            throw new AssertionError(
                    "unmatched broad foreground band must remain unowned: " + foreign);
        System.out.println("coherent ledger/staff coexistence and unmatched-band refusal PASS");
    }
}
