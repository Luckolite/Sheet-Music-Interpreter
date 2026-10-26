// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.*;
import java.util.*;

/** Original, positive complete-path and already-spent invocation remainder controls. */
public final class EighthPositiveControls {
    static int checks;

    static void check(boolean b, String s) {
        checks++;
        if (!b) throw new AssertionError(s);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        for (String name :
                List.of("015-eighth-g14.25-p0.5-t200-b1.0", "020-eighth-g18.0-p0.5-t170-b0.6")) {
            var a = EighthCallerControls.original(root.resolve(name + ".bin"));
            byte[] saved = a.raw().clone();
            var prior = new SixteenthRestDetector.Detection(List.of(), List.of());
            var q = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
            var actual = EighthCallerControls.finish(a, List.of(), prior, q);
            check(
                    actual.rests().size() == 1 && actual.rests().get(0).durationBeats() == .5,
                    "complete original positive");
            check(
                    EighthCallerControls.finishWithStaffs(
                                    a,
                                    List.of(),
                                    prior,
                                    new FaintQuarterRestInk.Budget(64_000_000, 64_000_000),
                                    List.of(a.frame(), a.frame()))
                            .equals(actual),
                    "identical fully-completed physical witness FIRST only");
            var otherFrame =
                    new SixteenthRestDetector.Staff(
                            a.frame().top(), a.frame().bottom(), a.frame().gap(), 0, 1);
            check(
                    EighthCallerControls.finishWithStaffs(
                                    a,
                                    List.of(),
                                    prior,
                                    new FaintQuarterRestInk.Budget(64_000_000, 64_000_000),
                                    List.of(a.frame(), otherFrame))
                            == prior,
                    "distinct declared frames contend without value alias or accepted prefix");
            var rest = actual.rests().get(0);
            var exact = new FaintQuarterRestInk.Budget(q.workUsed, q.bytesUsed);
            check(
                    EighthCallerControls.finish(a, List.of(), prior, exact).equals(actual),
                    "exact whole optional pass");
            check(exact.workUsed == q.workUsed && exact.bytesUsed == q.bytesUsed, "exact counters");
            for (int axis = 0; axis < 2; axis++) {
                var low =
                        new FaintQuarterRestInk.Budget(
                                q.workUsed - (axis == 0 ? 1 : 0),
                                q.bytesUsed - (axis == 1 ? 1 : 0));
                check(
                        EighthCallerControls.finish(a, List.of(), prior, low) == prior,
                        "oneunder no accepted prefix");
                check(
                        low.exhausted
                                && low.workUsed <= low.workLimit
                                && low.bytesUsed <= low.byteLimit,
                        "atomic counters");
            }
            var full =
                    new SixteenthRestDetector.Detection(
                            List.of(ScoreRestEvent.fullMeasure(0, .1f, .5f, .1f, 0, 1)), List.of());
            check(
                    EighthCallerControls.finish(
                                    a,
                                    List.of(),
                                    full,
                                    new FaintQuarterRestInk.Budget(64_000_000, 64_000_000))
                            == full,
                    "actual full rest veto");
            var same = new SixteenthRestDetector.Detection(List.of(rest), List.of());
            check(
                    EighthCallerControls.finish(
                                    a,
                                    List.of(),
                                    same,
                                    new FaintQuarterRestInk.Budget(64_000_000, 64_000_000))
                            == same,
                    "existing literal geometry wins");
            for (var unsupported :
                    List.of(
                            new SixteenthRestDetector.Detection(
                                    Collections.singletonList(null), List.of()),
                            new SixteenthRestDetector.Detection(
                                    List.of(new ScoreRestEvent(-1, .8f, .5f, .1f, 0, 1, 1)),
                                    List.of()),
                            new SixteenthRestDetector.Detection(
                                    List.of(new ScoreRestEvent(0, .8f, Float.NaN, .1f, 0, 1, 1)),
                                    List.of()),
                            new SixteenthRestDetector.Detection(
                                    List.of(new ScoreRestEvent(0, .8f, .5f, .1f, 0, 0, 1)),
                                    List.of())))
                check(
                        EighthCallerControls.finish(
                                        a,
                                        List.of(),
                                        unsupported,
                                        new FaintQuarterRestInk.Budget(64_000_000, 64_000_000))
                                == unsupported,
                        "unknown complete rest roster refuses");
            var head =
                    new ScoreNoteEvent(
                            0, rest.positionInMeasure(), 0, 0, 1, rest.pageY(), false, 0);
            check(
                    EighthCallerControls.finish(
                                    a,
                                    List.of(head),
                                    prior,
                                    new FaintQuarterRestInk.Budget(64_000_000, 64_000_000))
                            == prior,
                    "head rivalry");
            var unknown = new ScoreNoteEvent(-1, .1f, 0, 0, 1, .1f, false, 0);
            check(
                    EighthCallerControls.finish(
                                    a,
                                    List.of(unknown),
                                    prior,
                                    new FaintQuarterRestInk.Budget(64_000_000, 64_000_000))
                            == prior,
                    "unknown anywhere in full roster");
            float lowerY = rest.pageY() + rest.pageHeight() * .5f + a.frame().gap() * 1.2f / a.h();
            var held =
                    new ScoreNoteEvent(
                            0,
                            rest.positionInMeasure(),
                            0,
                            0,
                            1,
                            lowerY,
                            false,
                            0,
                            0,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            2);
            check(ScoreNoteTiming.hasIndependentSustain(held), "authentic held-voice policy");
            check(
                    EighthCallerControls.finish(
                                            a,
                                            List.of(held),
                                            prior,
                                            new FaintQuarterRestInk.Budget(64_000_000, 64_000_000))
                                    .rests()
                                    .size()
                            == 1,
                    "held voice below remains admissible through old guard");
            var unresolvedVoice =
                    new ScoreNoteEvent(0, rest.positionInMeasure(), 0, 0, 1, lowerY, false, 0);
            check(
                    EighthCallerControls.finish(
                                    a,
                                    List.of(unresolvedVoice),
                                    prior,
                                    new FaintQuarterRestInk.Budget(64_000_000, 64_000_000))
                            == prior,
                    "unproved overlapping voice still refused");
            var quarter = new ScoreRestEvent(0, .8f, rest.pageY(), rest.pageHeight(), 0, 1, 1);
            var completedQuarter = new SixteenthRestDetector.Detection(List.of(quarter), List.of());
            var spent = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
            check(spent.prepay(52_590_000, 50_790_000), "prior quarter spending");
            var after = EighthCallerControls.finish(a, List.of(), completedQuarter, spent);
            check(
                    after.rests().size() == 2 && after.rests().get(0) == quarter,
                    "same pool retains completed quarter object");
            for (int axis = 0; axis < 2; axis++) {
                var cumulative =
                        new FaintQuarterRestInk.Budget(
                                spent.workUsed - (axis == 0 ? 1 : 0),
                                spent.bytesUsed - (axis == 1 ? 1 : 0));
                check(cumulative.prepay(52_590_000, 50_790_000), "same pre-existing spending");
                check(
                        EighthCallerControls.finish(a, List.of(), completedQuarter, cumulative)
                                == completedQuarter,
                        "cumulative oneunder returns exact quarter Detection");
                check(
                        cumulative.exhausted
                                && cumulative.workUsed <= cumulative.workLimit
                                && cumulative.bytesUsed <= cumulative.byteLimit,
                        "cumulative atomic counters");
            }
            check(Arrays.equals(saved, a.raw()), "all caller paths preserve raw");
        }
        System.out.println(
                "PASS "
                        + checks
                        + " original complete-path/remainder assertions; two positive physical witnesses, no newly added source credit");
    }
}
