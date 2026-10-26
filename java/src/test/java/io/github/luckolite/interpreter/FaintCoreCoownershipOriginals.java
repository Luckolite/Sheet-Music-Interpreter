// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Actual Source capture: a faint-only third head shares, but cannot reshape, a strong stem. */
public class FaintCoreCoownershipOriginals {
    static final int W = 280, H = 190;
    static int checks;

    static void check(boolean b, String m) {
        checks++;
        if (!b) throw new AssertionError(m);
    }

    static void rect(byte[] a, int l, int r, int t, int b, int shade) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) a[y * W + x] = (byte) shade;
    }

    static void head(byte[] a, int cx, int cy) {
        for (int y = cy - 7; y <= cy + 7; y++)
            for (int x = cx - 10; x <= cx + 10; x++)
                if ((x - cx) * (x - cx) / (9.6 * 9.6) + (y - cy) * (y - cy) / (6.4 * 6.4) <= 1)
                    a[y * W + x] = 0;
    }

    static ScoreNoteEvent note(int x, int y, int step) {
        return new ScoreNoteEvent(0, x / (float) W, step, 0, 1, y / (float) H, false, 0, 2);
    }

    static PrintedBeamSource.Capture capture(byte[] raw, boolean extra) {
        var notes = new ArrayList<>(List.of(note(65, 130, 0), note(205, 130, 0)));
        if (extra) notes.add(note(64, 106, 3));
        return PrintedBeamSource.capture(
                new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes),
                new byte[raw.length],
                raw,
                W,
                H,
                List.of(new PlayingTechniqueDetector.Staff(66, 130, 16, 0, 1)),
                0,
                8_000_000,
                256_000_000,
                64_000_000);
    }

    public static void main(String[] args) {
        for (int extension : new int[] {0, 1, 2}) {
            byte[] raw = new byte[W * H];
            Arrays.fill(raw, (byte) 255);
            for (int y = 66; y <= 130; y += 16) rect(raw, 4, W - 5, y, y, 0);
            head(raw, 65, 130);
            head(raw, 205, 130);
            head(raw, 64, 106);
            rect(raw, 75, 75, 36, 130, 0);
            rect(raw, 215, 215, 36, 130, 0);
            rect(raw, 75, 215, 36, 42, 0);
            rect(raw, 75, 215, 49, 55, 0);
            // The new head cannot reach the stem under the strict pixel domain.
            rect(raw, 74, 74, 106, 106, 190);
            // This strand is visible from the new head's scan, not the old lower head's start.
            rect(raw, 76, 76, 36 - extension, 103, 0);
            byte[] before = raw.clone();
            var baseline = capture(raw, false);
            var joined = capture(raw, true);
            check(
                    baseline.statistics().refusal().isEmpty()
                            && joined.statistics().refusal().isEmpty(),
                    "actual capture remains in original caps");
            check(
                    baseline.source().groups().size() == 1 && joined.source().groups().size() == 1,
                    "both actual captures produce one physical group " + joined.statistics());
            var old = baseline.source().groups().get(0);
            var next = joined.source().groups().get(0);
            check(old.bridges().equals(List.of(3)), "baseline is a real two-level strong profile");
            check(
                    next.bridges().equals(old.bridges()) && next.hooks().equals(old.hooks()),
                    "old strong edge/hook profile survives new co-owner");
            var members = next.columns().get(0).members();
            check(
                    members.stream()
                            .map(ScoreBeamSource.Anchor::noteIndex)
                            .toList()
                            .equals(List.of(0, 2)),
                    "new actual faint head co-owns the existing physical column "
                            + members
                            + " "
                            + joined.diagnostics());
            var evidence =
                    joined.diagnostics().stream()
                            .filter(d -> Integer.valueOf(2).equals(d.get("owner")))
                            .findFirst()
                            .orElseThrow();
            check(
                    Integer.valueOf(0).equals(evidence.get("strongShafts"))
                            && Integer.valueOf(1).equals(evidence.get("faintShafts")),
                    "actual new head is qualified ONLY through faint support");
            var oldColumn =
                    baseline.columns().stream()
                            .filter(d -> ((List<?>) d.get("owners")).contains(0))
                            .findFirst()
                            .orElseThrow();
            var newColumn =
                    joined.columns().stream()
                            .filter(d -> ((List<?>) d.get("owners")).contains(0))
                            .findFirst()
                            .orElseThrow();
            check(
                    oldColumn.get("column").equals(newColumn.get("column")),
                    "strong Source input column bounds/caps are unchanged");
            check(Arrays.equals(before, raw), "raw evidence remains immutable");
        }
        System.out.println(
                "PASS "
                        + checks
                        + " actual Source faint co-ownership/strong-column/profile assertions");
    }
}
