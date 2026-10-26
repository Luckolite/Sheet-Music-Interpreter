// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original whole-Source fields with independently drawn head/shaft alternatives. */
public final class InteriorHeadBeamOriginals {
    static final int W = 280, H = 210;
    static int checks;

    static void check(boolean v, String label) {
        checks++;
        if (!v) throw new AssertionError(label);
    }

    static void rect(byte[] raw, int l, int r, int t, int b, int shade) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) raw[y * W + x] = (byte) shade;
    }

    static void head(byte[] raw, int cx) {
        for (int y = 124; y <= 136; y++)
            for (int x = cx - 10; x <= cx + 10; x++)
                if ((x - cx) * (x - cx) / (9.6 * 9.6) + (y - 130) * (y - 130) / (6.4 * 6.4) <= 1)
                    raw[y * W + x] = 0;
    }

    static byte[] raw(int shade, String kind) {
        byte[] r = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        for (int y = 66; y <= 130; y += 16) rect(r, 4, W - 5, y, y, shade);
        for (int cx : new int[] {65, 205}) {
            head(r, cx);
            rect(r, cx + 10, cx + 10, 36, 130, shade);
        }
        rect(r, 75, 215, 36, 42, shade);
        if (!kind.equals("blank")) head(r, 135);
        if (kind.equals("owned")) rect(r, 145, 145, 36, 130, shade);
        if (kind.equals("opposite")) rect(r, 125, 125, 130, 182, shade);
        if (kind.equals("shortDisconnected")) rect(r, 145, 145, 65, 105, shade);
        if (kind.equals("rivals")) {
            rect(r, 145, 145, 36, 130, shade);
            rect(r, 125, 125, 130, 182, shade);
        }
        return r;
    }

    static PrintedBeamSource.Capture capture(byte[] raw, boolean middle, boolean duplicate) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int x : new int[] {65, 135, 205})
            if (middle || x != 135)
                notes.add(new ScoreNoteEvent(0, x / (float) W, 0, 0, 1, 130f / H, false, 0, 1));
        if (duplicate) notes.add(notes.get(1));
        var score = new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
        byte[] labels = new byte[raw.length], before = raw.clone();
        var c =
                PrintedBeamSource.capture(
                        score,
                        labels,
                        raw,
                        W,
                        H,
                        List.of(new PlayingTechniqueDetector.Staff(66, 130, 16, 0, 1)),
                        0,
                        8_000_000,
                        256_000_000,
                        64_000_000);
        check(c.statistics().refusal().isEmpty(), "no resource refusal");
        check(Arrays.equals(before, raw), "raw immutable");
        check(Arrays.equals(labels, new byte[labels.length]), "labels immutable");
        return c;
    }

    static List<Integer> owners(ScoreBeamSource.Group group) {
        var r = new ArrayList<Integer>();
        for (var c : group.columns()) for (var a : c.members()) r.add(a.noteIndex());
        return r;
    }

    public static void main(String[] args) {
        for (int shade : new int[] {0, 190, 225}) {
            var ordinary = capture(raw(shade, "blank"), false, false);
            check(ordinary.source().groups().size() == 1, "ordinary two-head whole Source remains");
            var gap = capture(raw(shade, "missing"), true, false);
            check(gap.statistics().bodies() == 3, "all three actual heads authenticated");
            check(gap.statistics().shaftOwners() == 2, "middle shaft unproved");
            check(gap.source().groups().isEmpty(), "unowned interior head cannot be skipped");
            check(
                    gap.source().detachedColumns().isEmpty(),
                    "unknown group does not fabricate isolated flags");
            var blank = capture(raw(shade, "blank"), true, false);
            check(blank.statistics().bodies() == 2, "blank proposal does not authenticate");
            check(blank.source().groups().size() == 1, "unproved proposal cannot veto actual beam");
            var owned = capture(raw(shade, "owned"), true, false);
            check(owned.statistics().shaftOwners() == 3, "all three actual shafts owned");
            check(owned.source().groups().size() == 1, "complete three-column beam remains");
            check(
                    owners(owned.source().groups().get(0)).equals(List.of(0, 1, 2)),
                    "complete real order");
            var opposite = capture(raw(shade, "opposite"), true, false);
            check(
                    opposite.statistics().shaftOwners() == 3,
                    "opposite voice has independent unique shaft");
            check(
                    opposite.source().groups().size() == 1,
                    "known opposite voice does not block actual beam");
            check(
                    owners(opposite.source().groups().get(0)).equals(List.of(0, 2)),
                    "skip independently proved opposite voice only");
        }
        var detached = capture(raw(0, "shortDisconnected"), true, false);
        check(
                detached.source().groups().isEmpty(),
                "disconnected shaft is not owned interior column");
        var rivals = capture(raw(0, "rivals"), true, false);
        check(rivals.source().groups().isEmpty(), "ambiguous middle ownership cannot be skipped");
        var duplicate = capture(raw(0, "missing"), true, true);
        check(
                duplicate.source().groups().isEmpty(),
                "duplicate unknown heads not collapsed into permission");
        System.out.println("PASS " + checks + " original whole-Source assertions");
    }
}
