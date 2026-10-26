// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original five-rule rasters with an independently specified nominal staff offset. */
public final class LocalFramePhaseOriginals {
    static final int W = 1024, H = 480, CX = 512, BOTTOM = 300;
    static int checks;

    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    static final class Fixture {
        final byte[] raw = new byte[W * H];
        final PlayingTechniqueDetector.Staff staff;
        final ScoreNoteEvent note;
        final ScorePageInterpretation score;
        final PrintedSlurOwners.GeometryHead head;
        final PrintedBeamShaftPixels.Frame frame;
        final int actualBottom;

        Fixture(int gap, double phase, boolean missing, double pitchResidual) {
            Arrays.fill(raw, (byte) 245);
            actualBottom = (int) Math.round(BOTTOM + phase * gap);
            staff = new PlayingTechniqueDetector.Staff(BOTTOM - 4 * gap, BOTTOM, gap, 0, 1);
            note =
                    new ScoreNoteEvent(
                            0,
                            (float) CX / W,
                            0,
                            0,
                            1,
                            (float) (actualBottom + pitchResidual * gap) / H,
                            false,
                            0,
                            1);
            score =
                    new ScorePageInterpretation(
                            List.of(new MeasureRegion(0, 1, 0, 1)), List.of(note));
            head = PrintedSlurContext.geometryHeads(score.notes(), score.measures(), W, H).get(0);
            for (int line = 0; line < 5; line++)
                if (!(missing && line == 2)) {
                    int y = actualBottom - (4 - line) * gap;
                    for (int x = 4; x < W - 4; x++) raw[y * W + x] = (byte) 180;
                }
            frame = new PrintedBeamShaftPixels.Frame(score, raw, W, H, 0, n -> {}, true);
        }

        PrintedBeamLocalFrame.Witness at(FaintQuarterRestInk.Budget q) {
            return PrintedBeamLocalFrame.at(frame, score, raw, W, H, 0, head, note, staff, q);
        }

        PrintedBeamSource.Capture capture(boolean duplicate) {
            return PrintedBeamSource.capture(
                    score,
                    new byte[raw.length],
                    raw,
                    W,
                    H,
                    duplicate ? List.of(staff, staff) : List.of(staff),
                    0,
                    8000000,
                    256000000,
                    64000000);
        }
    }

    static void observedEnvelope() {
        for (int gap : new int[] {8, 12, 16, 24, 32})
            for (double phase : new double[] {-.7, -.55, .55, .7}) {
                var f = new Fixture(gap, phase, false, 0);
                byte[] before = f.raw.clone();
                var q = new FaintQuarterRestInk.Budget(128000000, 64000000);
                var witness = f.at(q);
                check(witness != null, "complete five-rule offset gap=" + gap + " phase=" + phase);
                check(
                        Math.abs(witness.bottom() - f.actualBottom) < .1,
                        "actual fifth rule retained");
                check(Math.abs(witness.gap() - gap) < .1, "actual spacing retained");
                check(
                        witness.matches(
                                f.frame, f.score, f.raw, W, H, 0, f.head, f.note, f.staff, q),
                        "exact witness affinity");
                check(Arrays.equals(before, f.raw), "original pixels unchanged");
            }
        for (double phase : new double[] {-.7, .7}) {
            var missing = new Fixture(16, phase, true, 0);
            check(
                    missing.at(new FaintQuarterRestInk.Budget(128000000, 64000000)) == null,
                    "missing rule refuses");
            var wrongPitch = new Fixture(16, phase, false, .6);
            check(
                    wrongPitch.at(new FaintQuarterRestInk.Budget(128000000, 64000000)) == null,
                    "wrong pitch refuses");
        }
        for (double phase : new double[] {-.95, .95, -1, 1}) {
            var outside = new Fixture(16, phase, false, 0);
            check(
                    outside.at(new FaintQuarterRestInk.Budget(128000000, 64000000)) == null,
                    "outside existing search envelope refuses");
        }
        var f = new Fixture(16, .7, false, 0);
        var measured = new FaintQuarterRestInk.Budget(128000000, 64000000);
        check(f.at(measured) != null, "measure funded witness");
        check(
                f.at(new FaintQuarterRestInk.Budget(measured.workUsed, measured.bytesUsed)) != null,
                "exact allowance");
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
            check(q.exhausted, "sticky refusal");
        }
    }

    static void actualSourceBinding() {
        for (double phase : new double[] {-.7, .7}) {
            var f = new Fixture(16, phase, false, 0);
            for (boolean duplicate : new boolean[] {false, true}) {
                var c = f.capture(duplicate);
                check(
                        c.statistics().refusal().isEmpty(),
                        "actual Source completes within existing allowances");
                check(
                        c.statistics().bound() == (duplicate ? 0 : 1),
                        "actual Source unique binding / duplicate refusal");
                check(c.source().groups().isEmpty(), "rule-only raster invents no beam ownership");
            }
        }
        // The veto's interval must contain every result permitted by the final frame checks.
        var f = new Fixture(16, .7, false, .3);
        check(
                f.at(new FaintQuarterRestInk.Budget(128000000, 64000000)) != null,
                "permitted observed frame and pitch residual");
        check(
                !PrintedBeamBindingRange.possible(
                        f.head.y(), 0, BOTTOM, 16, 16 * .4f, 16 * .12f, .45f),
                "old pruning interval excludes legal frame");
        check(
                PrintedBeamBindingRange.possible(
                        f.head.y(), 0, BOTTOM, 16, 16 * .8f, 16 * .12f, .45f),
                "matching pruning interval retains legal frame");
        var c = f.capture(false);
        check(
                c.statistics().refusal().isEmpty() && c.statistics().bound() == 1,
                "actual Source does not prune legal frame");
    }

    public static void main(String[] args) {
        observedEnvelope();
        actualSourceBinding();
        System.out.println("PASS " + checks + " original observed phase assertions");
    }
}
