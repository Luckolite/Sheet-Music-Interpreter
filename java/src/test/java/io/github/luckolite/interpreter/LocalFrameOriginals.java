// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Independent generated raw rules; no commercial score assets. */
public final class LocalFrameOriginals {
    static int checks;

    static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }

    static final int W = 280, H = 210;

    static final class Fixture {
        final byte[] raw = new byte[W * H];
        final PlayingTechniqueDetector.Staff staff =
                new PlayingTechniqueDetector.Staff(66, 130, 16, 0, 1);
        final ScoreNoteEvent note = new ScoreNoteEvent(0, 65f / W, 0, 0, 1, 130f / H, false, 0, 1);
        final ScorePageInterpretation score =
                new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), List.of(note));
        final PrintedSlurOwners.GeometryHead head =
                PrintedSlurContext.geometryHeads(score.notes(), score.measures(), W, H).get(0);
        final PrintedBeamShaftPixels.Frame frame;

        Fixture(String mode) {
            Arrays.fill(raw, (byte) 245);
            for (int line = 0; line < 6; line++) {
                if (mode.equals("missing") && line == 2) continue;
                if (mode.equals("uniform")) continue;
                int y = 66 + 16 * line;
                for (int x = 4; x < W - 4; x++)
                    if (line == 5 || (x + line) % 5 != 0) {
                        if (mode.equals("dashes") && x % 20 >= 5) continue;
                        for (int yy = y - (mode.equals("thick") ? 4 : 0);
                                yy <= y + (mode.equals("thick") ? 4 : 0);
                                yy++) raw[yy * W + x] = (byte) 190;
                    }
            }
            frame = new PrintedBeamShaftPixels.Frame(score, raw, W, H, 0, n -> {}, true);
        }

        PrintedBeamLocalFrame.Witness at(FaintQuarterRestInk.Budget budget) {
            return PrintedBeamLocalFrame.at(frame, score, raw, W, H, 0, head, note, staff, budget);
        }
    }

    static void affinity() {
        var f = new Fixture("normal");
        var q = new FaintQuarterRestInk.Budget(128000000, 64000000);
        var w = f.at(q);
        check(w != null, "five thin bilateral rules");
        check(
                w.matches(f.frame, f.score, f.raw, W, H, 0, f.head, f.note, f.staff, q),
                "exact query");
        check(
                !w.matches(
                        new PrintedBeamShaftPixels.Frame(f.score, f.raw, W, H, 0, n -> {}, true),
                        f.score,
                        f.raw,
                        W,
                        H,
                        0,
                        f.head,
                        f.note,
                        f.staff,
                        q),
                "frame borrowing");
        var cloneScore = new ScorePageInterpretation(f.score.measures(), f.score.notes());
        check(
                !w.matches(f.frame, cloneScore, f.raw, W, H, 0, f.head, f.note, f.staff, q),
                "score borrowing");
        check(
                !w.matches(f.frame, f.score, f.raw.clone(), W, H, 0, f.head, f.note, f.staff, q),
                "raw borrowing");
        check(
                !w.matches(f.frame, f.score, f.raw, H, W, 0, f.head, f.note, f.staff, q),
                "dimensions borrowing");
        check(
                !w.matches(f.frame, f.score, f.raw, W, H, 1, f.head, f.note, f.staff, q),
                "page borrowing");
        var h =
                new PrintedSlurOwners.GeometryHead(
                        f.head.owner(),
                        f.head.x(),
                        f.head.y(),
                        f.head.staffIndex(),
                        f.head.staffCount());
        check(
                !w.matches(f.frame, f.score, f.raw, W, H, 0, h, f.note, f.staff, q),
                "head value substitute");
        var n = new ScoreNoteEvent(0, 65f / W, 0, 0, 1, 130f / H, false, 0, 1);
        check(
                !w.matches(f.frame, f.score, f.raw, W, H, 0, f.head, n, f.staff, q),
                "note value substitute");
        var s = new PlayingTechniqueDetector.Staff(66, 130, 16, 0, 1);
        check(
                !w.matches(f.frame, f.score, f.raw, W, H, 0, f.head, f.note, s, q),
                "staff value substitute");
        check(
                !w.matches(
                        f.frame,
                        f.score,
                        f.raw,
                        W,
                        H,
                        0,
                        f.head,
                        f.note,
                        f.staff,
                        new FaintQuarterRestInk.Budget(128000000, 64000000)),
                "meter borrowing");
        try {
            PrintedBeamLocalFrame.at(
                    f.frame, cloneScore, f.raw, W, H, 0, f.head, f.note, f.staff, q);
            throw new AssertionError("wrong source");
        } catch (PrintedBeamShaftPixels.IdentityFault expected) {
            checks++;
        }
        try {
            PrintedBeamLocalFrame.at(f.frame, f.score, f.raw, H, W, 0, f.head, f.note, f.staff, q);
            throw new AssertionError("wrong dimensions");
        } catch (PrintedBeamShaftPixels.IdentityFault expected) {
            checks++;
        }
        try {
            PrintedBeamLocalFrame.at(f.frame, f.score, f.raw, W, H, 1, f.head, f.note, f.staff, q);
            throw new AssertionError("wrong page");
        } catch (PrintedBeamShaftPixels.IdentityFault expected) {
            checks++;
        }
        try {
            PrintedBeamLocalFrame.at(
                    f.frame, f.score, f.raw.clone(), W, H, 0, f.head, f.note, f.staff, q);
            throw new AssertionError("wrong raw");
        } catch (PrintedBeamShaftPixels.IdentityFault expected) {
            checks++;
        }
        check(
                StaffPitchTrack.forPrintedSlurs(f.raw, W, H, 66, 130, 16) == null,
                "anchored binding does not mint fresh six-rule track");
        var otherHead = new PrintedSlurOwners.GeometryHead(0, f.head.x(), f.head.y() + 8, 0, 1);
        check(
                PrintedBeamLocalFrame.at(
                                f.frame, f.score, f.raw, W, H, 0, otherHead, f.note, f.staff, q)
                        == null,
                "wrong pitch phase");
    }

    static void profileAndResources() {
        for (String mode : new String[] {"normal", "missing", "thick", "uniform", "dashes"}) {
            var f = new Fixture(mode);
            byte[] before = f.raw.clone();
            var q = new FaintQuarterRestInk.Budget(128000000, 64000000);
            var w = f.at(q);
            check((w != null) == mode.equals("normal"), "profile " + mode);
            check(Arrays.equals(before, f.raw), "profile immutable " + mode);
        }
        var f = new Fixture("normal");
        var measured = new FaintQuarterRestInk.Budget(128000000, 64000000);
        check(f.at(measured) != null, "measure positive");
        long work = measured.workUsed, bytes = measured.bytesUsed;
        check(f.at(new FaintQuarterRestInk.Budget(work, bytes)) != null, "exact quotas");
        for (int axis = 0; axis < 2; axis++) {
            var q =
                    new FaintQuarterRestInk.Budget(
                            work - (axis == 0 ? 1 : 0), bytes - (axis == 1 ? 1 : 0));
            try {
                f.at(q);
                throw new AssertionError("one short");
            } catch (FaintQuarterRestInk.Refusal expected) {
                checks++;
            }
            check(q.exhausted, "sticky exhaustion");
            try {
                FaintQuarterRestInk.charge(q, 0, 0);
                throw new AssertionError("revival");
            } catch (FaintQuarterRestInk.Refusal expected) {
                checks++;
            }
        }
        long[] remaining = {work + (bytes + 7) / 8};
        var shared =
                new FaintQuarterRestInk.Budget(
                        128000000,
                        64000000,
                        (ww, bb) -> {
                            long n = ww + (bb + 7) / 8;
                            if (n > remaining[0]) throw new PrintedBeamSource.Limit();
                            remaining[0] -= n;
                        });
        check(f.at(shared) != null, "funded parent");
        try {
            f.at(shared);
            throw new AssertionError("fresh independent funds");
        } catch (PrintedBeamSource.Limit expected) {
            checks++;
        }
        check(shared.exhausted, "parent failure sticky");
        System.out.println("profile budget work=" + work + " bytes=" + bytes);
    }

    static void drawRect(byte[] raw, int l, int r, int t, int b, int shade) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) raw[y * W + x] = (byte) shade;
    }

    static void drawHead(byte[] raw, int cx, int cy) {
        for (int y = cy - 7; y <= cy + 7; y++)
            for (int x = cx - 10; x <= cx + 10; x++)
                if ((x - cx) * (x - cx) / (9.6 * 9.6) + (y - cy) * (y - cy) / (6.4 * 6.4) <= 1)
                    raw[y * W + x] = 0;
    }

    static void unboundMiddle() {
        for (boolean actual : new boolean[] {false, true}) {
            byte[] raw = new byte[W * H];
            Arrays.fill(raw, (byte) 255);
            for (int y = 68; y <= 132; y += 16) drawRect(raw, 4, W - 5, y, y, 0);
            for (int x : new int[] {65, 205}) {
                drawHead(raw, x, 132);
                drawRect(raw, x + 10, x + 10, 36, 132, 0);
            }
            drawRect(raw, 75, 215, 36, 42, 0);
            if (actual) drawHead(raw, 135, 124);
            var notes =
                    List.of(
                            new ScoreNoteEvent(0, 65f / W, 0, 0, 1, 132f / H, false, 0, 1),
                            new ScoreNoteEvent(0, 135f / W, 0, 0, 1, 124.2f / H, false, 0, 1),
                            new ScoreNoteEvent(0, 205f / W, 0, 0, 1, 132f / H, false, 0, 1));
            var score = new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
            byte[] before = raw.clone();
            var c =
                    PrintedBeamSource.capture(
                            score,
                            new byte[raw.length],
                            raw,
                            W,
                            H,
                            List.of(new PlayingTechniqueDetector.Staff(66, 130, 16, 0, 1)),
                            0,
                            8000000,
                            256000000,
                            64000000);
            check(c.statistics().refusal().isEmpty(), "unbound middle budget");
            check(c.statistics().bound() == 2, "middle remains genuinely unbound");
            check(
                    c.source().groups().size() == (actual ? 0 : 1),
                    "unbound raw head veto, blank proposal does not veto");
            check(Arrays.equals(raw, before), "unbound middle raw immutable");
        }
    }

    static void sourceBindings() {
        var f = new Fixture("normal");
        for (boolean duplicate : new boolean[] {false, true}) {
            var staffs = duplicate ? List.of(f.staff, f.staff) : List.of(f.staff);
            var c =
                    PrintedBeamSource.capture(
                            f.score,
                            new byte[f.raw.length],
                            f.raw,
                            W,
                            H,
                            staffs,
                            0,
                            8000000,
                            256000000,
                            64000000);
            check(c.statistics().refusal().isEmpty(), "binding source budget");
            check(
                    c.statistics().bound() == (duplicate ? 0 : 1),
                    "unique anchored binding or full lane ambiguity");
            check(
                    c.source().groups().isEmpty(),
                    "binding does not invent raw head or shaft ownership");
        }
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        for (int y = 66; y <= 130; y += 16) drawRect(raw, 4, W - 5, y, y, 0);
        for (int x : new int[] {65, 205}) {
            drawHead(raw, x, 130);
            drawRect(raw, x + 10, x + 10, 36, 130, 0);
        }
        drawRect(raw, 75, 215, 36, 42, 0);
        var notes =
                List.of(
                        new ScoreNoteEvent(0, 65f / W, 0, 0, 1, 130f / H, false, 0, 1),
                        new ScoreNoteEvent(0, 205f / W, 0, 0, 1, 130f / H, false, 0, 1));
        var score = new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
        for (boolean duplicate : new boolean[] {false, true}) {
            var staffs = duplicate ? List.of(f.staff, f.staff) : List.of(f.staff);
            var c =
                    PrintedBeamSource.capture(
                            score,
                            new byte[raw.length],
                            raw,
                            W,
                            H,
                            staffs,
                            0,
                            8000000,
                            256000000,
                            64000000);
            check(c.statistics().refusal().isEmpty(), "old binding source budget");
            check(
                    c.statistics().bound() == (duplicate ? 0 : 2),
                    "old binding positive and ambiguity retained");
            check(
                    c.source().groups().size() == (duplicate ? 0 : 1),
                    "old whole beam and ambiguous lane policy retained");
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("unbound-only")) {
            unboundMiddle();
            return;
        }
        affinity();
        profileAndResources();
        unboundMiddle();
        sourceBindings();
        System.out.println("LocalFrameOriginals PASS " + checks);
    }
}
