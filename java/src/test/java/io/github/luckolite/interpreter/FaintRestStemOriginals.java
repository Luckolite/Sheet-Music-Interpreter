// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original procedural note head and separately varied shaft, not a score-pixel fixture. */
public final class FaintRestStemOriginals {
    static final int W = 160, H = 150;
    static int checks;

    static void check(boolean v, String m) {
        checks++;
        if (!v) throw new AssertionError(m);
    }

    static final class Fixture {
        final byte[] raw = new byte[W * H];
        final ScoreNoteEvent note = new ScoreNoteEvent(0, .5f, 6, 0, 1, 50f / H, false, 0, 1);
        final List<ScoreNoteEvent> notes;

        Fixture(int shade, int thickness, boolean detached, boolean broken, boolean wrongOrigin) {
            Arrays.fill(raw, (byte) 245);
            var runs = new ArrayList<int[]>();
            for (int y = 42; y <= 58; y++) {
                int first = -1, last = -1;
                for (int x = 68; x <= 92; x++) {
                    double dx = x - 80,
                            dy = y - 50,
                            u = dx * .94 - dy * .34,
                            v = dx * .34 + dy * .94;
                    if (u * u / 100 + v * v / 25 <= 1) {
                        raw[y * W + x] = 80;
                        if (first < 0) first = x;
                        last = x;
                    }
                }
                if (first >= 0) runs.add(new int[] {y, first, last});
            }
            for (int y = detached ? 80 : 50; y <= 100; y++)
                if (!broken || y < 70 || y > 76)
                    for (int x = 70; x < 70 + thickness; x++)
                        raw[y * W + x] = (byte) Math.min(raw[y * W + x] & 255, shade);
            if (wrongOrigin) runs = new ArrayList<>(List.of(new int[] {10, 10, 10}));
            var origin = RestFigureOrigins.HeadOrigin.accepted(new Object(), raw, W, H, runs);
            var map = new IdentityHashMap<ScoreNoteEvent, RestFigureOrigins.HeadOrigin>();
            map.put(note, origin);
            notes = RestFigureOrigins.bind(List.of(note), map);
        }

        boolean owns(FaintQuarterRestInk.Budget b) {
            return FaintRestStemInk.owns(raw, W, H, notes, note, 65, 85, 90, 16, 80, 50, b);
        }
    }

    public static void main(String[] args) {
        for (int shade : new int[] {180, 200, 212, 220})
            for (int width : new int[] {1, 2, 3, 4}) {
                var f = new Fixture(shade, width, false, false, false);
                var before = f.raw.clone();
                check(f.owns(null), "attached thin faint shaft shade=" + shade + " width=" + width);
                check(Arrays.equals(before, f.raw), "immutable actual plane");
                check(
                        !RestStaffRuleEdge.noteStem(f.raw, W, H, 65, 85, 90, 16, 80, 50),
                        "prior dark-column proof refuses faint shaft");
            }
        for (var f :
                new Fixture[] {
                    new Fixture(200, 3, true, false, false),
                    new Fixture(200, 3, false, true, false),
                    new Fixture(200, 7, false, false, false),
                    new Fixture(235, 3, false, false, false),
                    new Fixture(200, 3, false, false, true)
                }) check(!f.owns(null), "detached/broken/thick/low contrast/unrelated origin");
        var f = new Fixture(200, 3, false, false, false);
        check(
                !FaintRestStemInk.owns(
                        f.raw.clone(), W, H, f.notes, f.note, 65, 85, 90, 16, 80, 50, null),
                "cloned plane cannot borrow origin");
        check(
                !FaintRestStemInk.owns(
                        f.raw, W, H, List.of(f.note), f.note, 65, 85, 90, 16, 80, 50, null),
                "unowned values cannot borrow origin");
        var same = new ScoreNoteEvent(0, .5f, 6, 0, 1, 50f / H, false, 0, 1);
        check(
                !FaintRestStemInk.owns(f.raw, W, H, f.notes, same, 65, 85, 90, 16, 80, 50, null),
                "equal note cannot borrow identity");
        check(
                !FaintRestStemInk.owns(
                        f.raw, W, H, f.notes, f.note, 120, 130, 90, 16, 80, 50, null),
                "unrelated corridor");
        var q = new FaintQuarterRestInk.Budget(64000000, 16777216);
        check(f.owns(q), "measure positive");
        long work = q.workUsed, bytes = q.bytesUsed;
        check(f.owns(new FaintQuarterRestInk.Budget(work, bytes)), "exact funding");
        for (int axis = 0; axis < 2; axis++) {
            var under =
                    new FaintQuarterRestInk.Budget(
                            work - (axis == 0 ? 1 : 0), bytes - (axis == 1 ? 1 : 0));
            try {
                f.owns(under);
                throw new AssertionError("one short");
            } catch (FaintQuarterRestInk.Refusal expected) {
                checks++;
            }
            check(under.exhausted, "sticky refusal");
        }
        var twice = new FaintQuarterRestInk.Budget(work * 2, bytes * 2);
        check(f.owns(twice) && f.owns(twice), "same cumulative pool");
        try {
            f.owns(twice);
            throw new AssertionError("no third allowance");
        } catch (FaintQuarterRestInk.Refusal expected) {
            checks++;
        }
        System.out.println(
                "PASS "
                        + checks
                        + " original faint-shaft ownership assertions; work="
                        + work
                        + " bytes="
                        + bytes);
    }
}
