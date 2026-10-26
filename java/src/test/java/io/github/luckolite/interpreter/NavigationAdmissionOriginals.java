// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class NavigationAdmissionOriginals {
    static int checks, controls;

    interface R {
        void run();
    }

    static void ck(boolean v) {
        checks++;
        if (!v) throw new AssertionError("admission assertion " + checks);
    }

    static void refuse(R r) {
        boolean no = false;
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            no = true;
        }
        ck(no);
    }

    static void test(String n, R r) {
        r.run();
        controls++;
        System.out.println("PASS " + n);
    }

    public static void main(String[] args) {
        for (String code :
                List.of(
                        "AMBIGUOUS_TARGET",
                        "CONFLICTING_IDENTITY",
                        "CONFLICTING_REPEAT_COUNT",
                        "CONFLICTING_RETURNS",
                        "CROSSING_REPEATS",
                        "DUPLICATE_REPEAT_GROUP",
                        "EMPTY_DESTINATION",
                        "EMPTY_REPEAT",
                        "ENDING_OWNER",
                        "INVALID_ANCHOR",
                        "INVALID_RETURN",
                        "MARK_LIMIT",
                        "MISSING_CODA_TRIGGER",
                        "NAVIGATION_CYCLE",
                        "NULL_MARK",
                        "RETURN_PASS_OWNER",
                        "TRAVERSAL_LIMIT",
                        "UNCLOSED_REPEAT",
                        "UNKNOWN_REPEAT_POLICY",
                        "UNSUPPORTED_MEASURE_REPEAT",
                        "FUTURE_UNKNOWN"))
            test(
                    "shared classifier refuses diagnostic " + code,
                    () -> {
                        var d =
                                new ScoreNavigationTraversal.Diagnostic(
                                        "raw-ordinal-id", code, "exact retained diagnostic");
                        var result =
                                new ScoreNavigationTraversal.Result(List.of(), List.of(d), true);
                        ck(
                                result.complete()
                                        && !result.performanceQualified()
                                        && result.diagnostics().get(0) == d);
                        refuse(result::requirePerformanceQualified);
                    });
        test(
                "termination without diagnostics remains separately required",
                () -> {
                    var incomplete =
                            new ScoreNavigationTraversal.Result(List.of(), List.of(), false);
                    ck(!incomplete.performanceQualified());
                    refuse(incomplete::requirePerformanceQualified);
                    var complete = new ScoreNavigationTraversal.Result(List.of(), List.of(), true);
                    ck(complete.performanceQualified());
                    complete.requirePerformanceQualified();
                });
        var meter = new ScoreMeterMap(3, List.of());
        var source = new ScorePerformanceTimeline(120, List.of(), List.of());
        test(
                "real unsupported measure repeat remains inspectable but every pure performance consumer refuses",
                () -> {
                    var plan =
                            ScoreNavigationPlan.create(
                                    3,
                                    List.of(
                                            new ScorePlaybackDirection(
                                                    1, ScorePlaybackDirection.Kind.MEASURE_REPEAT)),
                                    meter);
                    ck(plan.traversal().complete() && !plan.traversal().performanceQualified());
                    ck(plan.sourceMeasures().equals(List.of(0, 1, 2)));
                    ck(
                            plan.traversal()
                                    .diagnostics()
                                    .get(0)
                                    .code()
                                    .equals("UNSUPPORTED_MEASURE_REPEAT"));
                    refuse(
                            () ->
                                    ScoreNavigationNoteProjection.project(
                                            List.of(),
                                            plan,
                                            meter,
                                            ScoreNavigationNoteProjection.EntryPolicy.REATTACK));
                    refuse(() -> ScoreNavigationPerformance.project(source, plan, meter));
                    refuse(() -> ScoreGainProjection.project(List.of(), plan, meter, source));
                    refuse(() -> PedalPerformance.project(List.of(), plan, meter));
                });
        test(
                "actual null and invalid anchors keep legacy route inspection without qualification",
                () -> {
                    var directions = new ArrayList<ScorePlaybackDirection>();
                    directions.add(null);
                    directions.add(new ScorePlaybackDirection(5, ScorePlaybackDirection.Kind.CODA));
                    var plan = ScoreNavigationPlan.create(3, directions, meter);
                    ck(
                            plan.traversal().complete()
                                    && plan.sourceMeasures().equals(List.of(0, 1, 2)));
                    ck(
                            plan.traversal().diagnostics().stream()
                                    .map(ScoreNavigationTraversal.Diagnostic::code)
                                    .toList()
                                    .equals(List.of("NULL_MARK", "INVALID_ANCHOR")));
                    refuse(plan.traversal()::requirePerformanceQualified);
                });
        for (int plays : new int[] {2, 3})
            test(
                    "valid repeat " + plays + " preserves actual notes holds gains pedals",
                    () -> {
                        var details =
                                new ScorePlaybackDirection.Details(
                                        0,
                                        "repeat-end",
                                        "",
                                        "",
                                        "",
                                        plays,
                                        List.of(),
                                        Optional.empty(),
                                        ScorePlaybackDirection.AfterJumpRepeats.DEFAULT,
                                        "",
                                        List.of());
                        var plan =
                                ScoreNavigationPlan.create(
                                        3,
                                        List.of(
                                                new ScorePlaybackDirection(
                                                        0,
                                                        ScorePlaybackDirection.Kind.REPEAT_START),
                                                new ScorePlaybackDirection(
                                                        3,
                                                        ScorePlaybackDirection.Kind.REPEAT_END,
                                                        details)),
                                        meter);
                        ck(plan.traversal().performanceQualified());
                        var notes =
                                ScoreNavigationNoteProjection.project(
                                        List.of(
                                                new ScoreNavigationNoteProjection.SourceNote(
                                                        "n", 7, 0, 1)),
                                        plan,
                                        meter,
                                        ScoreNavigationNoteProjection.EntryPolicy.REATTACK);
                        ck(notes.notes().size() == plays);
                        ck(
                                notes.notes().stream()
                                        .allMatch(
                                                n ->
                                                        n.sourceIndex() == 7
                                                                && n.sourceId().equals("n")));
                        for (int i = 0; i < plays; i++)
                            ck(
                                    notes.notes().get(i).startBeat() == 9 * i
                                            && notes.notes().get(i).endBeat() == 9 * i + 1);
                        var held =
                                new ScorePerformanceTimeline(
                                        120,
                                        List.of(),
                                        List.of(
                                                new ScorePerformanceTimeline.Hold(
                                                        "h", 3, 1, Set.of())));
                        var performed =
                                ScoreNavigationPerformance.project(
                                        held,
                                        plan,
                                        meter,
                                        Map.of("h", ScorePerformanceTimeline.Boundary.BEFORE),
                                        notes.targetMapper());
                        ck(
                                performed.performedBeats() == 9 * plays
                                        && performed.timeline().holds().size() == plays);
                        ck(ScoreGainProjection.project(List.of(), plan, meter, source) != null);
                        ck(PedalPerformance.project(List.of(), plan, meter).isEmpty());
                    });
        test(
                "valid linear route no diagnostics unchanged",
                () -> {
                    var plan = ScoreNavigationPlan.create(3, List.of(), meter);
                    ck(
                            plan.traversal().performanceQualified()
                                    && plan.sourceMeasures().equals(List.of(0, 1, 2)));
                    var p = ScoreNavigationPerformance.project(source, plan, meter);
                    ck(p.performedBeats() == 9 && p.timeline().holds().isEmpty());
                });
        System.out.println(
                "PASS navigation performance admission controls="
                        + controls
                        + " assertions="
                        + checks
                        + " PURE_JAVA_NO_NATIVE_MUSICAL_EXPANSION");
    }
}
