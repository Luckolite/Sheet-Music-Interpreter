// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** An accepted physical rest survives repairs to earlier logical bars only. */
public final class RecognizedMultiRestReindexOriginalTest {
    record Scene(
            byte[] labels,
            byte[] gray,
            List<MeasureRegion> raw,
            List<MeasureRegion> logical,
            RecognizedMultiRestOrigins origins,
            MeasureRegion parent,
            int count) {}

    private static Scene scene(int count, boolean seal) throws Exception {
        var p = MultiRestLocalScenes.pixels(count, "plain");
        var physical = new SelectedMeasureOrigins();
        var geometry = NativeDecoderStages.geometry(p.labels(), p.gray(), 1000, 400, physical);
        var raw = geometry.measures();
        var origins = new RecognizedMultiRestOrigins(physical);
        var token = new MeasureNumberReconciler.NumberToken(count, .437f, .21f, .452f, .2725f);
        var counts =
                MultiMeasureRestDetector.detect(
                        geometry.labels(), p.gray(), 1000, 400, raw, List.of(token), origins);
        assertTrue(counts.stream().anyMatch(t -> t.value() == count));
        var logical =
                MeasureNumberReconciler.reconcile(
                        raw, List.of(), counts, geometry.labels(), 1000, 400);
        var parent = logical.get(0);
        assertNotNull(origins.admission(parent));
        if (seal) origins.expanded(raw, logical);
        return new Scene(geometry.labels(), p.gray(), raw, logical, origins, parent, count);
    }

    private static MeasureRegion prefix() {
        return new MeasureRegion(.1f, .7f, .8f, .95f);
    }

    private static List<MeasureRegion> shifted(Scene s) {
        var m = new ArrayList<>(s.logical());
        m.add(0, prefix());
        return m;
    }

    private static void expected(Scene s, List<MeasureRegion> logical, int start) {
        s.origins().reindexed(logical);
        assertTrue(s.origins().expansion(s.parent(), start, start + s.count()));
        var before = OmrScoreInterpreter.analyze(s.labels(), s.gray(), 1000, 400, logical);
        var after =
                RecognizedMultiRestProjection.apply(
                        before, logical, s.labels(), s.gray(), 1000, 400, s.origins());
        assertEquals(before.notes(), after.notes());
        var groups =
                PrintedMultiRestGroups.capture(
                        after, logical, s.labels(), s.gray(), 1000, 400, s.origins());
        assertEquals(List.of(new ScoreMultiRestGroup(start, s.count(), 0, 1)), groups);
    }

    @Test
    public void insertedEarlierBarRetainsFourBarRest() throws Exception {
        var s = scene(4, true);
        expected(s, shifted(s), 1);
    }

    @Test
    public void insertedEarlierBarRetainsTwoBarRest() throws Exception {
        var s = scene(2, true);
        expected(s, shifted(s), 1);
    }

    @Test
    public void removedEarlierBarRetainsRest() throws Exception {
        var s = scene(4, true);
        s.origins().expanded(s.raw(), shifted(s));
        expected(s, s.logical(), 0);
    }

    @Test
    public void unchangedRunIsIdempotent() throws Exception {
        var s = scene(4, true);
        s.origins().reindexed(s.logical());
        expected(s, s.logical(), 0);
    }

    @Test
    public void equalGeometryCannotBorrowParentIdentity() throws Exception {
        var s = scene(4, true);
        var m = new ArrayList<>(s.logical());
        var p = s.parent();
        for (int i = 0; i < s.count(); i++)
            m.set(i, new MeasureRegion(p.left(), p.right(), p.top(), p.bottom()));
        s.origins().reindexed(m);
        assertFalse(s.origins().expansion(s.parent(), 0, 4));
    }

    @Test
    public void changedCountIsRejected() throws Exception {
        var s = scene(4, true);
        var m = new ArrayList<>(s.logical());
        m.remove(0);
        s.origins().reindexed(m);
        assertFalse(s.origins().expansion(s.parent(), 0, 3));
    }

    @Test
    public void disjointRepetitionIsRejected() throws Exception {
        var s = scene(4, true);
        var m = new ArrayList<>(s.logical());
        m.add(s.parent());
        s.origins().reindexed(m);
        assertFalse(s.origins().expansion(s.parent(), 0, 4));
    }

    @Test
    public void unexpandedAdmissionCannotGainNewRun() throws Exception {
        var s = scene(4, true);
        s.origins().expanded(s.raw(), s.raw());
        s.origins().reindexed(s.logical());
        assertFalse(s.origins().expansion(s.parent(), 0, 4));
    }

    @Test
    public void unsealedAdmissionCannotGainNewRun() throws Exception {
        var s = scene(4, false);
        s.origins().reindexed(s.logical());
        assertFalse(s.origins().expansion(s.parent(), 0, 4));
    }

    @Test
    public void invalidListCannotKeepStaleProof() throws Exception {
        var s = scene(4, true);
        s.origins().reindexed(null);
        assertFalse(s.origins().expansion(s.parent(), 0, 4));
    }

    @Test
    public void oversizedListCannotKeepStaleProof() throws Exception {
        var s = scene(4, true);
        s.origins().reindexed(Collections.nCopies(4097, prefix()));
        assertFalse(s.origins().expansion(s.parent(), 0, 4));
    }

    @Test
    public void rejectedRunCannotBeResurrectedByAnotherReindex() throws Exception {
        var s = scene(4, true);
        var m = new ArrayList<>(s.logical());
        m.remove(0);
        s.origins().reindexed(m);
        s.origins().reindexed(s.logical());
        assertFalse(s.origins().expansion(s.parent(), 0, 4));
    }
}
