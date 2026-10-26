// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original complete ordinary-head composition, never a saved score fragment. */
public class FaintSourceOriginals {
    static int checks;
    static final int W = 280, H = 190;

    static void check(boolean b, String m) {
        checks++;
        if (!b) throw new AssertionError(m);
    }

    static void rect(byte[] a, int l, int r, int t, int b, int v) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) a[y * W + x] = (byte) v;
    }

    static byte[] raw(int shade, String variant) {
        byte[] a = new byte[W * H];
        Arrays.fill(a, (byte) 255);
        for (int y = 66; y <= 130; y += 16) rect(a, 4, W - 5, y, y, shade);
        for (int cx : new int[] {65, 205}) {
            if (!variant.equals("barline"))
                for (int y = 124; y <= 136; y++)
                    for (int x = cx - 10; x <= cx + 10; x++)
                        if ((x - cx) * (x - cx) / (9.6 * 9.6) + (y - 130) * (y - 130) / (6.4 * 6.4)
                                <= 1) a[y * W + x] = 0;
            rect(a, cx + 10, cx + 10, 36, 130, shade);
        }
        if (!variant.equals("staffOnly")) rect(a, 75, 215, 36, 42, shade);
        if (variant.equals("disconnected")) rect(a, 75, 75, 119, 121, 255);
        if (variant.equals("oneWhiteRow")) rect(a, 75, 75, 119, 119, 255);
        if (variant.equals("gap")) rect(a, 130, 133, 36, 42, 255);
        if (variant.equals("rivals"))
            for (int cx : new int[] {65, 205}) rect(a, cx - 10, cx - 10, 130, 178, shade);
        if (variant.equals("sameSideRivals") || variant.equals("sameSideDetached"))
            for (int cx : new int[] {65, 205}) {
                rect(a, cx + 13, cx + 13, 36, 130, shade);
                if (variant.equals("sameSideRivals")) rect(a, cx + 10, cx + 13, 123, 123, shade);
            }
        return a;
    }

    static ScorePageInterpretation score() {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int x : new int[] {65, 205})
            notes.add(new ScoreNoteEvent(0, x / (float) W, 0, 0, 1, 130f / H, false, 0, 1));
        return new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
    }

    static PrintedBeamSource.Capture capture(byte[] a) {
        return PrintedBeamSource.capture(
                score(),
                new byte[a.length],
                a,
                W,
                H,
                List.of(new PlayingTechniqueDetector.Staff(66, 130, 16, 0, 1)),
                0,
                8_000_000,
                256_000_000,
                64_000_000);
    }

    public static void main(String[] args) {
        for (int shade : new int[] {0, 172, 190, 212, 225}) {
            byte[] a = raw(shade, "normal"), before = a.clone();
            var c = capture(a);
            check(
                    c.statistics().refusal().isEmpty(),
                    "no refusal shade=" + shade + " " + c.statistics());
            check(
                    c.source().groups().size() == 1,
                    "whole literal faint composition shade=" + shade + " " + c.statistics());
            check(c.source().groups().get(0).bridges().equals(List.of(1)), "exact primary level");
            check(Arrays.equals(a, before), "original raw untouched");
        }
        for (String variant :
                new String[] {
                    "barline",
                    "disconnected",
                    "oneWhiteRow",
                    "gap",
                    "staffOnly",
                    "rivals",
                    "sameSideRivals"
                }) {
            var c = capture(raw(190, variant));
            check(c.source().groups().isEmpty(), "false composition refuses " + variant);
        }
        var detachedRun = capture(raw(190, "sameSideDetached"));
        check(
                detachedRun.statistics().shaftOwners() == 2
                        && detachedRun.statistics().rivals() == 0,
                "unattached run does not become a fabricated head-owner rival; whole beam may remain uncertain");
        byte[] a = raw(190, "normal");
        var refused =
                PrintedBeamSource.capture(
                        score(),
                        new byte[a.length],
                        a,
                        W,
                        H,
                        List.of(new PlayingTechniqueDetector.Staff(66, 130, 16, 0, 1)),
                        0,
                        0,
                        256_000_000,
                        64_000_000);
        check(
                !refused.statistics().refusal().isEmpty() && refused.source().groups().isEmpty(),
                "whole optional phase rolls back at shaft quota");
        System.out.println(
                "PASS " + checks + " original whole-source faint/head-neck/false-ink controls");
    }
}
