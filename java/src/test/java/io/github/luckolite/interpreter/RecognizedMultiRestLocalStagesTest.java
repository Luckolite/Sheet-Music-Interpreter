// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real pure local producer chain used by the Android caller; no Bitmap/inference claim. */
public final class RecognizedMultiRestLocalStagesTest {
    private static void check(int n, boolean remote) throws Exception {
        var p = MultiRestLocalScenes.pixels(n, "plain");
        byte[] l = p.labels().clone(), g = p.gray().clone();
        var physical = new SelectedMeasureOrigins();
        var geometry = NativeDecoderStages.geometry(p.labels(), p.gray(), 1000, 400, physical);
        List<MeasureRegion> raw = geometry.measures();
        SelectedMeasureOrigins bound = physical;
        if (remote) {
            raw =
                    raw.stream()
                            .map(m -> new MeasureRegion(m.left(), m.right(), m.top(), m.bottom()))
                            .toList();
            bound = null;
        }
        var origins = bound == null ? null : new RecognizedMultiRestOrigins(bound);
        var t = new MeasureNumberReconciler.NumberToken(n, .437f, .21f, .452f, .2725f);
        var counts =
                MultiMeasureRestDetector.detect(
                        geometry.labels(), p.gray(), 1000, 400, raw, List.of(t), origins);
        assertTrue(counts.stream().anyMatch(c -> c.value() == n));
        var measures =
                MeasureNumberReconciler.reconcile(
                        raw, List.of(), counts, geometry.labels(), 1000, 400);
        if (origins != null) origins.expanded(raw, measures);
        measures =
                PrintedMeasureRhythmGuard.reconcile(
                        raw, measures, geometry.labels(), p.gray(), 1000, 400);
        var before = OmrScoreInterpreter.analyze(geometry.labels(), p.gray(), 1000, 400, measures);
        var score =
                RecognizedMultiRestProjection.apply(
                        before, measures, geometry.labels(), p.gray(), 1000, 400, origins);
        assertEquals(before.notes(), score.notes());
        assertEquals(before.keyChanges(), score.keyChanges());
        assertEquals(before.tremoloSource(), score.tremoloSource());
        for (var r : before.rests()) assertTrue(score.rests().stream().anyMatch(x -> x == r));
        if (remote) {
            assertSame(before, score);
            assertFalse(
                    score.rests().stream()
                            .anyMatch(r -> r.measureIndex() < n && r.isFullMeasure()));
        } else {
            assertEquals(
                    n,
                    score.rests().stream()
                            .filter(r -> r.measureIndex() < n && r.isFullMeasure())
                            .count());
            var rhythm =
                    TripletRhythmDetector.withRests(
                            score.notes(), score.rests(), measures, p.gray(), 1000, 400);
            assertEquals(score.rests(), rhythm.rests());
        }
        assertArrayEquals(l, p.labels());
        assertArrayEquals(g, p.gray());
    }

    @Test
    public void actualLocalFourStageChainSerializesRests() throws Exception {
        check(4, false);
    }

    @Test
    public void actualLocalTwoStageChainDoesNotReexpand() throws Exception {
        check(2, false);
    }

    @Test
    public void remoteValueOnlyGeometryCannotBorrowLocalOrigin() throws Exception {
        check(4, true);
    }

    @Test
    public void legacyGeometryOverloadKeepsSameOutputValues() throws Exception {
        var p = MultiRestLocalScenes.pixels(4, "plain");
        var a = NativeDecoderStages.geometry(p.labels(), p.gray(), 1000, 400);
        var b =
                NativeDecoderStages.geometry(
                        p.labels(), p.gray(), 1000, 400, new SelectedMeasureOrigins());
        assertEquals(a.measures(), b.measures());
        assertArrayEquals(a.labels(), b.labels());
    }
}
