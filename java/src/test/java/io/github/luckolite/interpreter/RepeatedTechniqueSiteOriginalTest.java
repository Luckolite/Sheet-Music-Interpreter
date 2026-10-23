// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original source-site examples; printed repeats do not mean redundant persistent state. */
public final class RepeatedTechniqueSiteOriginalTest {
    static ScoreTechniqueChange t(int m, float p, int staff, int count, int kind) {
        return new ScoreTechniqueChange(m, p, staff, count, kind);
    }

    static ScoreTechniqueChange t(int m, float p) {
        return t(m, p, 0, 1, 4);
    }

    static ScoreNoteEvent n(int m, float p, int staff, int count) {
        return new ScoreNoteEvent(
                m,
                p,
                0,
                staff,
                count,
                .4f,
                false,
                0,
                0,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                1,
                1,
                0);
    }

    static List<ScoreTechniqueChange> plan(List<ScoreTechniqueChange> ts, List<ScoreNoteEvent> ns) {
        return ScoreTechniqueSelection.distinctInstructions(ts, ns);
    }

    @Test
    public void sameLabelInLaterBarIsPrintedAgain() {
        var ts = List.of(t(0, .1f), t(4, .1f));
        assertEquals(ts, plan(ts, List.of(n(0, .2f, 0, 1), n(4, .2f, 0, 1))));
    }

    @Test
    public void twoAffectedColumnsInOneBarRemainDistinct() {
        var ts = List.of(t(0, .1f), t(0, .6f));
        assertEquals(ts, plan(ts, List.of(n(0, .2f, 0, 1), n(0, .8f, 0, 1))));
    }

    @Test
    public void duplicateOcrReadingsOfOneAffectedColumnCollapse() {
        var ts = List.of(t(0, .1f), t(0, .15f));
        assertEquals(List.of(ts.get(0)), plan(ts, List.of(n(0, .2f, 0, 1))));
    }

    @Test
    public void independentStaffAndTopologyAreNotInterchangeable() {
        var ts = List.of(t(0, .1f, 0, 1, 4), t(0, .1f, 0, 2, 4), t(0, .1f, 1, 2, 4));
        assertEquals(ts, plan(ts, List.of(n(0, .2f, 0, 1), n(0, .2f, 0, 2), n(0, .2f, 1, 2))));
    }

    @Test
    public void unownedLabelsUseExactPrintedSites() {
        var a = t(0, .1f);
        var b = t(0, .10001f);
        assertEquals(List.of(a, b), plan(List.of(a, a, b), List.of()));
    }

    @Test
    public void foreignTopologyPreventsColumnCoalescing() {
        var ts = List.of(t(0, .1f), t(0, .15f));
        assertEquals(ts, plan(ts, List.of(n(0, .2f, 0, 1), n(0, .8f, 0, 2))));
    }

    @Test
    public void bowAttackEvidenceIsPreservedVerbatim() {
        var t = t(0, .1f, 0, 1, ScoreTechniqueChange.UP_BOW);
        assertEquals(List.of(t, t), plan(List.of(t, t), List.of(n(0, .2f, 0, 1))));
    }

    @Test
    public void differentInstructionsAtOneColumnSurviveWithoutMutation() {
        var ts = List.of(t(0, .1f), t(0, .1f, 0, 1, ScoreTechniqueChange.ARCO));
        assertEquals(ts, plan(ts, List.of(n(0, .2f, 0, 1))));
        assertEquals(2, ts.size());
    }

    @Test
    public void contradictoryColumnNeverDropsTheFinalInstruction() {
        var a = t(0, .1f);
        var b = t(0, .15f, 0, 1, ScoreTechniqueChange.ARCO);
        var ts = List.of(a, b, a);
        assertEquals(ts, plan(ts, List.of(n(0, .2f, 0, 1))));
    }

    @Test
    public void signedZeroDenotesOneUnownedPrintedSite() {
        var a = t(0, 0f);
        var b = t(0, -0f);
        assertEquals(List.of(a), plan(List.of(a, b), List.of()));
    }
}
