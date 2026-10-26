package io.github.luckolite.interpreter;

import java.util.*;

/** Original tilted-rule rasters, drawn from one continuous affine rule equation. */
public final class LocalFrameSlopeOriginals {
    static int checks;

    static void check(boolean v, String m) {
        checks++;
        if (!v) throw new AssertionError(m);
    }

    static final int W = 1400, H = 500, CX = 700, BOTTOM = 300;

    static final class Fixture {
        final byte[] raw = new byte[W * H];
        final PlayingTechniqueDetector.Staff staff;
        final ScoreNoteEvent note;
        final ScorePageInterpretation score;
        final PrintedSlurOwners.GeometryHead head;
        final PrintedBeamShaftPixels.Frame frame;

        Fixture(int gap, double slope, int phase, boolean missing) {
            Arrays.fill(raw, (byte) 245);
            staff = new PlayingTechniqueDetector.Staff(BOTTOM - 4 * gap, BOTTOM, gap, 0, 1);
            note = new ScoreNoteEvent(0, (float) CX / W, 0, 0, 1, (float) BOTTOM / H, false, 0, 1);
            score =
                    new ScorePageInterpretation(
                            List.of(new MeasureRegion(0, 1, 0, 1)), List.of(note));
            head = PrintedSlurContext.geometryHeads(score.notes(), score.measures(), W, H).get(0);
            for (int line = 0; line < 5; line++)
                if (!(missing && line == 2))
                    for (int x = 4; x < W - 4; x++) {
                        int y =
                                (int)
                                        Math.round(
                                                BOTTOM
                                                        - (4 - line) * gap
                                                        + slope * (x - CX)
                                                        + phase * .25);
                        int thickness = Math.max(2, (int) Math.round(gap * .12));
                        for (int dy = 0; dy < thickness; dy++)
                            if (y + dy >= 0 && y + dy < H) raw[(y + dy) * W + x] = (byte) 180;
                    }
            frame = new PrintedBeamShaftPixels.Frame(score, raw, W, H, 0, n -> {}, true);
        }

        PrintedBeamLocalFrame.Witness at(FaintQuarterRestInk.Budget q) {
            return PrintedBeamLocalFrame.at(frame, score, raw, W, H, 0, head, note, staff, q);
        }
    }

    public static void main(String[] args) {
        for (int gap : new int[] {12, 16, 24, 32})
            for (double slope : new double[] {-.018, .018})
                for (int phase : new int[] {0, 1}) {
                    var f = new Fixture(gap, slope, phase, false);
                    var before = f.raw.clone();
                    var q = new FaintQuarterRestInk.Budget(128000000, 64000000);
                    var w = f.at(q);
                    check(
                            w != null,
                            "continuous tilted five-rule frame gap="
                                    + gap
                                    + " slope="
                                    + slope
                                    + " phase="
                                    + phase);
                    check(Math.abs(w.bottom() - BOTTOM) <= 2.5, "actual head position");
                    check(Math.abs(w.gap() - gap) < .1, "actual rule scale");
                    check(Arrays.equals(before, f.raw), "immutable raster");
                }
        var f = new Fixture(16, .018, 0, false);
        var q = new FaintQuarterRestInk.Budget(128000000, 64000000);
        check(f.at(q) != null, "measured frame");
        check(
                f.at(new FaintQuarterRestInk.Budget(q.workUsed, q.bytesUsed)) != null,
                "exact measured allowance");
        for (int axis = 0; axis < 2; axis++) {
            var under =
                    new FaintQuarterRestInk.Budget(
                            q.workUsed - (axis == 0 ? 1 : 0), q.bytesUsed - (axis == 1 ? 1 : 0));
            try {
                f.at(under);
                throw new AssertionError("one under must refuse");
            } catch (FaintQuarterRestInk.Refusal expected) {
                checks++;
            }
        }
        for (double slope : new double[] {-.018, .018, .24}) {
            var missing = new Fixture(16, slope, 0, true);
            check(
                    missing.at(new FaintQuarterRestInk.Budget(128000000, 64000000)) == null,
                    "missing actual rule");
        }
        var steep = new Fixture(16, .24, 0, false);
        check(
                steep.at(new FaintQuarterRestInk.Budget(128000000, 64000000)) == null,
                "steep unsupported raster");
        System.out.println("PASS " + checks + " original physical slope assertions");
    }
}
