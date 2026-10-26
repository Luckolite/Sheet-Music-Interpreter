// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original competing rule groups and exhaustive small projection controls. */
public final class LocalFrameRuleUniquenessOriginals {
    static int checks;

    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    static FaintQuarterRestInk.Budget budget() {
        return new FaintQuarterRestInk.Budget(64000000, 16L * 1024 * 1024);
    }

    static int[] projection(int... rows) {
        int[] a = new int[140];
        for (int y : rows) a[y] = 120;
        return a;
    }

    static int[] unique(int[] a, FaintQuarterRestInk.Budget q) {
        return PrintedBeamRuleCandidates.unique(a, 60, a.length, 0, 100, 20, q);
    }

    static Set<String> exhaustive(int[] rows, int[] strength) {
        var accepted = new HashSet<String>();
        for (int mask = 0; mask < (1 << rows.length); mask++)
            if (Integer.bitCount(mask) == 5) {
                int[] r = new int[5];
                int k = 0;
                for (int i = 0; i < rows.length; i++) if ((mask & (1 << i)) != 0) r[k++] = rows[i];
                float gap = (r[4] - r[0]) / 4f;
                if (gap < 20 * .88f || gap > 20 * 1.12f || Math.abs(r[4] - 100) > 20 * .8f)
                    continue;
                float error = 0;
                boolean valid = true;
                for (int i = 1; i < 4; i++) {
                    float distance = Math.abs(r[i] - (r[0] + i * gap));
                    if (distance > Math.max(2f, gap * .24f)) {
                        valid = false;
                        break;
                    }
                    error += Math.max(0f, distance - .5f);
                }
                long total = 0;
                for (int y : r) total += strength[y];
                if (valid && (float) total - error * 60 * .75f >= 60 * 4.5f)
                    accepted.add(Arrays.toString(r));
            }
        return accepted;
    }

    static void completeEnumeration() {
        for (int extra = 24; extra <= 96; extra += 4) {
            if (extra % 20 == 0) continue;
            for (int shade : new int[] {60, 120}) {
                int[] rows = {20, 40, 60, 80, 100, extra};
                Arrays.sort(rows);
                int[] a = projection(20, 40, 60, 80, 100);
                a[extra] = shade;
                int[] before = a.clone();
                var expected = exhaustive(rows, a);
                var actual = unique(a, budget());
                check(
                        (actual != null) == (expected.size() == 1),
                        "all tuples retained at extra=" + extra + " strength=" + shade);
                if (actual != null)
                    check(
                            expected.contains(Arrays.toString(actual)),
                            "unique result is sole eligible tuple");
                check(Arrays.equals(a, before), "projection is unchanged");
            }
        }
        int[] simple = projection(20, 40, 60, 80, 100);
        check(
                Arrays.equals(unique(simple, budget()), new int[] {20, 40, 60, 80, 100}),
                "single complete group");
        check(
                unique(projection(20, 40, 60, 64, 80, 100), budget()) == null,
                "interior alternatives cannot be ranked away");
        check(unique(projection(20, 40, 60, 80), budget()) == null, "missing fifth rule");
        check(unique(projection(20, 40, 57, 83, 110), budget()) == null, "irregular rule pattern");
        var measured = budget();
        check(unique(simple, measured) != null, "measured complete group");
        check(
                unique(
                                simple,
                                new FaintQuarterRestInk.Budget(
                                        measured.workUsed, measured.bytesUsed))
                        != null,
                "exact allowance");
        for (int axis = 0; axis < 2; axis++) {
            var q =
                    new FaintQuarterRestInk.Budget(
                            measured.workUsed - (axis == 0 ? 1 : 0),
                            measured.bytesUsed - (axis == 1 ? 1 : 0));
            try {
                unique(simple, q);
                throw new AssertionError("one under must refuse");
            } catch (FaintQuarterRestInk.Refusal expected) {
                checks++;
            }
            check(q.exhausted, "exhaustion is sticky");
        }
        var twice = new FaintQuarterRestInk.Budget(2 * measured.workUsed, 2 * measured.bytesUsed);
        check(
                unique(simple, twice) != null && unique(simple, twice) != null,
                "same cumulative pool funds both");
        try {
            unique(simple, twice);
            throw new AssertionError("third call has no new pool");
        } catch (FaintQuarterRestInk.Refusal expected) {
            checks++;
        }
        var parent =
                new FaintQuarterRestInk.Budget(
                        64000000,
                        16L * 1024 * 1024,
                        (w, b) -> {
                            throw new PrintedBeamSource.Limit();
                        });
        try {
            unique(simple, parent);
            throw new AssertionError("parent refusal must propagate");
        } catch (PrintedBeamSource.Limit expected) {
            checks++;
        }
        check(parent.exhausted, "parent refusal sticks");
        boolean[] candidateFunded = {false};
        var late =
                new FaintQuarterRestInk.Budget(
                        64000000,
                        16L * 1024 * 1024,
                        (w, b) -> {
                            if (candidateFunded[0]) throw new PrintedBeamSource.Limit();
                            if (b == 32L + 8L * 5) candidateFunded[0] = true;
                        });
        try {
            unique(projection(20, 40, 60, 80, 100, 125), late);
            throw new AssertionError(
                    "unfinished uniqueness proof cannot return its first candidate");
        } catch (PrintedBeamSource.Limit expected) {
            checks++;
        }
        check(
                candidateFunded[0] && late.exhausted,
                "late parent refusal follows a funded first candidate");
    }

    static PrintedBeamLocalFrame.Witness frame(int scale, int first, int last) {
        int w = 1024 * scale, h = 480 * scale;
        byte[] raw = new byte[w * h];
        Arrays.fill(raw, (byte) 245);
        int[] rows = {228, 243, 259, 276, 293, 311};
        for (int i = first; i <= last; i++)
            for (int x = 4; x < w - 4; x++) raw[rows[i] * scale * w + x] = (byte) 180;
        var note = new ScoreNoteEvent(0, .5f, 20, 0, 1, 136f / 480, false, 0, 1);
        var score =
                new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), List.of(note));
        var head = PrintedSlurContext.geometryHeads(score.notes(), score.measures(), w, h).get(0);
        var sourceFrame = new PrintedBeamShaftPixels.Frame(score, raw, w, h, 0, n -> {}, true);
        var staff = new PlayingTechniqueDetector.Staff(236 * scale, 300 * scale, 16 * scale, 0, 1);
        byte[] before = raw.clone();
        var result =
                PrintedBeamLocalFrame.at(
                        sourceFrame, score, raw, w, h, 0, head, note, staff, budget());
        check(Arrays.equals(raw, before), "original six-rule raster unchanged");
        return result;
    }

    static void actualFrames() {
        for (int scale : new int[] {1, 2}) {
            var first = frame(scale, 0, 4);
            var second = frame(scale, 1, 5);
            var both = frame(scale, 0, 5);
            check(first != null && second != null, "each individual five-rule group is admissible");
            check(
                    first.bottom() != second.bottom() || first.gap() != second.gap(),
                    "two distinct actual fitted frames");
            check(both == null, "complete competing groups refuse before overlap suppression");
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) completeEnumeration();
        actualFrames();
        System.out.println("PASS " + checks + " original all-rule uniqueness assertions");
    }
}
