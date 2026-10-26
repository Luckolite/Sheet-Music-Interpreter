// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original partially occluded affine staffs; no score images or injected producer witnesses. */
public final class LocalSamplesOriginals {
    static final int H = 480, BOTTOM = 300;
    static int checks;

    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    static FaintQuarterRestInk.Budget budget() {
        return new FaintQuarterRestInk.Budget(128000000, 64000000);
    }

    static final class Fixture {
        final int width, gap;
        final float cx;
        final byte[] raw;
        final PlayingTechniqueDetector.Staff staff;
        final ScoreNoteEvent note;
        final ScorePageInterpretation score;
        final PrintedSlurOwners.GeometryHead head;
        final PrintedBeamShaftPixels.Frame frame;

        Fixture(int width, int gap, float cx, double slope, String mode) {
            this.width = width;
            this.gap = gap;
            this.cx = cx;
            raw = new byte[width * H];
            Arrays.fill(raw, (byte) 245);
            staff = new PlayingTechniqueDetector.Staff(BOTTOM - 4 * gap, BOTTOM, gap, 0, 1);
            note = new ScoreNoteEvent(0, cx / width, 0, 0, 1, (float) BOTTOM / H, false, 0, 1);
            score =
                    new ScorePageInterpretation(
                            List.of(new MeasureRegion(0, 1, 0, 1)), List.of(note));
            head =
                    PrintedSlurContext.geometryHeads(score.notes(), score.measures(), width, H)
                            .get(0);
            for (int line = 0; line < 5; line++)
                for (int x = 4; x < width - 4; x++) {
                    int y = (int) Math.round(BOTTOM - (4 - line) * gap + slope * (x - cx));
                    if (y >= 0 && y < H && !(mode.equals("missing") && line == 2))
                        raw[y * width + x] = (byte) 220;
                }
            int l = (int) Math.floor(head.x() - gap * .8f),
                    r = (int) Math.ceil(head.x() + gap * .8f);
            int a = Math.max(0, Math.round(l - gap * 12)),
                    b = Math.max(0, Math.round(l - gap * .75f));
            int c = Math.min(width, Math.round(r + gap * .75f)),
                    d = Math.min(width, Math.round(r + gap * 12));
            int flank = Math.max(2, Math.round(gap * .32f));
            if (mode.equals("partial")) {
                // Both surviving quarters are on the right: their true centers
                // cannot be replaced with the centers of the complete flanks.
                cover(a, a + 3 * (b - a) / 4, slope, flank);
                cover(c, c + 3 * (d - c) / 4, slope, flank);
            } else if (mode.equals("one-side")) cover(c, d, slope, flank);
            else if (mode.equals("conflict")) {
                Arrays.fill(raw, (byte) 245);
                draw(a, a + (b - a) / 4, 0);
                draw(a + 3 * (b - a) / 4, b, Math.round(gap * .6f));
                draw(c, d, 0);
            }
            frame = new PrintedBeamShaftPixels.Frame(score, raw, width, H, 0, n -> {}, true);
        }

        void cover(int a, int b, double slope, int flank) {
            for (int x = a; x < b; x++) {
                int center = (int) Math.round(BOTTOM - 2 * gap + slope * (x - cx));
                for (int y = Math.max(0, center - flank - 1);
                        y <= Math.min(H - 1, center + flank + 1);
                        y++) raw[y * width + x] = (byte) 220;
            }
        }

        void draw(int a, int b, int shift) {
            for (int line = 0; line < 5; line++)
                for (int x = a; x < b; x++)
                    raw[(BOTTOM - (4 - line) * gap + shift) * width + x] = (byte) 220;
        }

        PrintedBeamLocalFrame.Witness at(FaintQuarterRestInk.Budget q) {
            return PrintedBeamLocalFrame.at(frame, score, raw, width, H, 0, head, note, staff, q);
        }
    }

    static void geometry() {
        for (int gap : new int[] {12, 15, 16, 24, 32})
            for (double slope : new double[] {-.03, 0, .03}) {
                var f = new Fixture(1019, gap, 509.25f, slope, "partial");
                byte[] before = f.raw.clone();
                var q = budget();
                var w = f.at(q);
                check(w != null, "partial raw affine staff gap=" + gap + " slope=" + slope);
                check(
                        Math.abs(w.bottom() - BOTTOM) < 1.25,
                        "actual sample centers retain head phase");
                check(Math.abs(w.gap() - gap) < .1, "actual rule spacing retained");
                check(
                        w.matches(
                                f.frame, f.score, f.raw, f.width, H, 0, f.head, f.note, f.staff, q),
                        "actual source/query affinity retained");
                check(Arrays.equals(f.raw, before), "raw original is unchanged");
            }
        for (String mode : new String[] {"missing", "one-side", "conflict"}) {
            var f = new Fixture(1019, 16, 509.25f, 0, mode);
            byte[] before = f.raw.clone();
            check(f.at(budget()) == null, "unknown/contradictory family refuses: " + mode);
            check(Arrays.equals(before, f.raw), "negative raw unchanged");
        }
        var clipped = new Fixture(1019, 16, 8, 0, "partial");
        check(clipped.at(budget()) == null, "missing physical flank refuses");
    }

    static void resources() {
        var f = new Fixture(1019, 16, 509.25f, 0, "partial");
        var measured = budget();
        check(f.at(measured) != null, "measured complete query");
        check(
                f.at(new FaintQuarterRestInk.Budget(measured.workUsed, measured.bytesUsed)) != null,
                "exact complete allowance");
        for (int axis = 0; axis < 2; axis++) {
            var q =
                    new FaintQuarterRestInk.Budget(
                            measured.workUsed - (axis == 0 ? 1 : 0),
                            measured.bytesUsed - (axis == 1 ? 1 : 0));
            try {
                f.at(q);
                throw new AssertionError("one-under must refuse");
            } catch (FaintQuarterRestInk.Refusal expected) {
                checks++;
            }
            check(q.exhausted, "quota refusal remains sticky");
        }
        var twice = new FaintQuarterRestInk.Budget(2 * measured.workUsed, 2 * measured.bytesUsed);
        check(f.at(twice) != null && f.at(twice) != null, "two queries share one cumulative pool");
        try {
            f.at(twice);
            throw new AssertionError("third query has no new pool");
        } catch (FaintQuarterRestInk.Refusal expected) {
            checks++;
        }
        boolean[] firstFit = {false};
        int[] after = {0};
        var late =
                new FaintQuarterRestInk.Budget(
                        128000000,
                        64000000,
                        (work, bytes) -> {
                            if (firstFit[0] && ++after[0] == 6) throw new PrintedBeamSource.Limit();
                            if (bytes == 48) firstFit[0] = true;
                        });
        try {
            f.at(late);
            throw new AssertionError("unfinished all-pair consensus cannot return its first fit");
        } catch (PrintedBeamSource.Limit expected) {
            checks++;
        }
        check(firstFit[0] && late.exhausted, "late parent refusal after a first pair fit");
    }

    static void typedCandidates() {
        int[] strength = new int[140];
        for (int row : new int[] {20, 40, 60, 80, 100}) strength[row] = 120;
        var one = PrintedBeamRuleCandidates.inspect(strength, 60, 140, 0, 100, 20, budget());
        check(!one.ambiguous && one.rows != null, "unique typed candidate");
        strength[64] = 120;
        var two = PrintedBeamRuleCandidates.inspect(strength, 60, 140, 0, 100, 20, budget());
        check(two.ambiguous && two.rows == null, "competing interior tuples are explicit");
        Arrays.fill(strength, 0);
        var none = PrintedBeamRuleCandidates.inspect(strength, 60, 140, 0, 100, 20, budget());
        check(!none.ambiguous && none.rows == null, "missing group differs from ambiguity");
    }

    static void source() {
        int width = 1024, gap = 16;
        byte[] raw = new byte[width * H];
        Arrays.fill(raw, (byte) 245);
        for (int line = 0; line < 5; line++)
            for (int x = 4; x < width - 4; x++)
                raw[(BOTTOM - (4 - line) * gap) * width + x] = (byte) 220;
        // Only the first head's left whole-flank projection loses its middle rule.
        for (int x = 175; x < 310; x++)
            for (int y = 262; y <= 274; y++) raw[y * width + x] = (byte) 220;
        for (int cx : new int[] {380, 640}) {
            for (int y = 294; y <= 306; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if ((x - cx) * (x - cx) / (9.6 * 9.6) + (y - 300) * (y - 300) / (6.4 * 6.4)
                            <= 1) raw[y * width + x] = 0;
            for (int y = 180; y <= 300; y++) raw[y * width + cx + 10] = 0;
        }
        for (int y = 180; y <= 186; y++) for (int x = 390; x <= 650; x++) raw[y * width + x] = 0;
        var notes =
                List.of(
                        new ScoreNoteEvent(0, 380f / width, 0, 0, 1, 300f / H, false, 0, 1),
                        new ScoreNoteEvent(0, 640f / width, 0, 0, 1, 300f / H, false, 0, 1));
        var score = new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
        byte[] before = raw.clone();
        var c =
                PrintedBeamSource.capture(
                        score,
                        new byte[raw.length],
                        raw,
                        width,
                        H,
                        List.of(new PlayingTechniqueDetector.Staff(236, 300, 16, 0, 1)),
                        0,
                        8000000,
                        256000000,
                        64000000);
        check(
                c.statistics().refusal().isEmpty() && !c.statistics().flagRefused(),
                "real Source completes within retained allowances");
        check(c.statistics().bound() == 2, "real Source binds both observed heads");
        if (c.source().groups().size() != 1)
            System.out.println(
                    "SCENE " + c.statistics() + " " + c.diagnostics() + " " + c.columns());
        check(c.source().groups().size() == 1, "real Source retains one physical two-note beam");
        check(Arrays.equals(before, raw), "Source leaves original pixels unchanged");
    }

    public static void main(String[] args) {
        geometry();
        resources();
        typedCandidates();
        source();
        System.out.println("PASS " + checks + " original local sampling assertions");
    }
}
