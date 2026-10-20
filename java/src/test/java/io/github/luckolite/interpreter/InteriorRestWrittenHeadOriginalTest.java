// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** New detailed head-retention and independent endcap/shaft roles; originals unchanged. */
public final class InteriorRestWrittenHeadOriginalTest {
    @Test
    public void originalWrittenHeadSurvivesAndCannotExpandSilence() throws Exception {
        var p = MultiRestLocalScenes.pixels(4, "written");
        var physical = new SelectedMeasureOrigins();
        var g = NativeDecoderStages.geometry(p.labels(), p.gray(), 1000, 400, physical);
        assertEquals(2, g.labels()[149 * 1000 + 308]);
        var counts =
                MultiMeasureRestDetector.detect(
                        g.labels(),
                        p.gray(),
                        1000,
                        400,
                        g.measures(),
                        List.of(
                                new MeasureNumberReconciler.NumberToken(
                                        4, .437f, .21f, .452f, .2725f)));
        assertTrue(counts.isEmpty());
        var measures =
                MeasureNumberReconciler.reconcile(
                        g.measures(), List.of(), counts, g.labels(), 1000, 400);
        assertEquals(g.measures().size(), measures.size());
        var score = OmrScoreInterpreter.analyze(g.labels(), p.gray(), 1000, 400, measures);
        assertEquals(2, score.notes().size());
        assertTrue(
                score.notes().stream()
                        .anyMatch(
                                n ->
                                        n.measureIndex() == 0
                                                && n.kind() == ScoreNoteEvent.Kind.PITCHED));
    }

    private static HeavyRestBarFragmentTest.Page interior(boolean down) {
        var p = HeavyRestBarFragmentTest.page(true, true, 230, 12);
        p.head(160, 112, 6, 4);
        if (down) p.rect(154, 112, 154, 160, 1);
        else p.rect(166, 64, 166, 112, 1);
        return p;
    }

    @Test
    public void independentLongUpShaftRetainsInteriorHead() {
        var p = interior(false);
        assertEquals(2, p.normalize()[112 * 400 + 160]);
        assertEquals(2, p.analyze().notes().size());
    }

    @Test
    public void independentLongDownShaftRetainsInteriorHead() {
        var p = interior(true);
        assertEquals(2, p.normalize()[112 * 400 + 160]);
        assertEquals(2, p.analyze().notes().size());
    }

    @Test
    public void aPlateFragmentWithoutShaftStillDisappears() {
        var p = HeavyRestBarFragmentTest.page(true, true, 230, 12);
        p.fragment(160, 112);
        assertEquals(0, p.normalize()[112 * 400 + 160]);
        assertEquals(1, p.analyze().notes().size());
    }

    @Test
    public void genuineLeftEndCapIsNotAnInteriorNoteShaft() {
        var p = HeavyRestBarFragmentTest.page(true, true, 230, 12);
        assertEquals(0, p.normalize()[115 * 400 + 94]);
        assertEquals(1, p.analyze().notes().size());
    }

    @Test
    public void genuineRightEndCapIsNotAnInteriorNoteShaft() {
        var p = HeavyRestBarFragmentTest.page(true, true, 230, 12);
        p.fragment(227, 115);
        assertEquals(0, p.normalize()[115 * 400 + 227]);
        assertEquals(1, p.analyze().notes().size());
    }

    @Test
    public void detachedNearbyStrokeCannotProtectPlateFragment() {
        var p = HeavyRestBarFragmentTest.page(true, true, 230, 12);
        p.fragment(160, 112);
        p.rect(166, 64, 166, 98, 1);
        assertEquals(0, p.normalize()[112 * 400 + 160]);
        assertEquals(1, p.analyze().notes().size());
    }
}
