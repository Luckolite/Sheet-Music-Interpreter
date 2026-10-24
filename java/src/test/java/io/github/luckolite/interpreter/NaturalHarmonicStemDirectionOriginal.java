// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Directional notehead and shaft ownership controls with an explicit malformed legacy fixture. */
public final class NaturalHarmonicStemDirectionOriginal {
    private static final int W = 96, H = 80;
    private static final byte HEAD = 2;

    private record Page(byte[] gray, byte[] labels) {}

    private static Page page(boolean down, boolean branch, boolean broken) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 248);
        for (int y = 46; y <= 54; y++)
            for (int x = 35; x <= 45; x++)
                if (((x - 40) / 5.0) * ((x - 40) / 5.0) + ((y - 50) / 4.0) * ((y - 50) / 4.0)
                        <= 1.0) {
                    labels[y * W + x] = HEAD;
                    gray[y * W + x] = 20;
                }
        if (down) {
            for (int y = 50; y <= 73; y++) gray[y * W + 34] = 25;
            if (broken) gray[63 * W + 34] = (byte) 248;
            for (int y = 59; y <= 61; y++) for (int x = 28; x <= 62; x++) gray[y * W + x] = 30;
            for (int y = 69; y <= 71; y++) for (int x = 28; x <= 62; x++) gray[y * W + x] = 30;
        } else {
            for (int y = 25; y <= 50; y++) gray[y * W + 46] = 25;
            for (int y = 36; y <= 38; y++) for (int x = 28; x <= 62; x++) gray[y * W + x] = 30;
            for (int y = 26; y <= 28; y++) for (int x = 28; x <= 62; x++) gray[y * W + x] = 30;
        }
        if (branch) for (int x = 30; x <= 40; x++) gray[52 * W + x] = 30;
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

    private static Page oldBidirectionalFixture() {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 248);
        for (int y = 46; y <= 54; y++)
            for (int x = 35; x <= 45; x++)
                if (((x - 40) / 5.0) * ((x - 40) / 5.0) + ((y - 50) / 4.0) * ((y - 50) / 4.0)
                        <= 1.0) {
                    labels[y * W + x] = HEAD;
                    gray[y * W + x] = 20;
                }
        for (int y = 39; y <= 76; y++) for (int x = 44; x <= 46; x++) gray[y * W + x] = 25;
        for (int y = 59; y <= 61; y++) for (int x = 28; x <= 62; x++) gray[y * W + x] = 30;
        for (int y = 69; y <= 71; y++) for (int x = 28; x <= 62; x++) gray[y * W + x] = 30;
        return new Page(gray, labels);
    }

    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }

    public static void main(String[] args) {
        var down = assess(page(true, false, false));
        check(
                down.decision() == NaturalHarmonicRingProof.HeadDecision.OWNED
                        && down.head() != null
                        && down.head().attachedStem(),
                "a conventional left-edge downstem with two witnessed rule crossings is owned");
        var up = assess(page(false, false, false));
        check(
                up.decision() == NaturalHarmonicRingProof.HeadDecision.OWNED
                        && up.head() != null
                        && up.head().attachedStem(),
                "a conventional right-edge upstem with two witnessed rule crossings is owned");
        var broken = assess(page(true, false, true));
        check(
                broken.head() == null,
                "a paper gap in a conventional downstem cannot be bridged by rule contact");
        var branch = assess(page(true, true, false));
        check(
                branch.head() == null,
                "an independent horizontal branch through the head envelope cannot substitute for"
                        + " its shaft");
        var legacy = assess(oldBidirectionalFixture());
        check(
                legacy.decision()
                                == NaturalHarmonicRingProof.HeadDecision.UNOWNED_DARK_IN_HEAD_BOUNDS
                        && legacy.head() == null
                        && legacy.firstUnownedX() == 44
                        && legacy.firstUnownedY() == 46,
                "the old downstem-positive actually draws a right-edge stem through and on both"
                        + " sides of the head; refuse that bidirectional ownership");
        System.out.println(
                "PASS valid up/down stem ownership; broken, branch, and malformed bidirectional"
                        + " controls refused");
    }
}
