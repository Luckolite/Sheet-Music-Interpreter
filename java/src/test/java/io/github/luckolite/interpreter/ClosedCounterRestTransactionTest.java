// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.*;
import java.util.*;

public class ClosedCounterRestTransactionTest {
    static final List<MeasureRegion> M = List.of(new MeasureRegion(0, 1, 0, 1));

    static void inspect(
            byte[] raw,
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> result,
            ClosedCounterRestInvocation scope,
            boolean edge)
            throws Exception {
        Method method =
                Arrays.stream(SixteenthRestDetector.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("inspectOwnedCounter"))
                        .findFirst()
                        .orElseThrow();
        method.setAccessible(true);
        method.invoke(
                null,
                raw,
                raw,
                400,
                240,
                M,
                notes,
                new SixteenthRestDetector.Staff(80, 120, 10, 0, 1),
                80,
                120,
                new boolean[41],
                140,
                147,
                result,
                new ArrayList<SixteenthRestDetector.RestDot>(),
                false,
                true,
                true,
                edge,
                false,
                false,
                false,
                100f / 240,
                false,
                scope);
    }

    static ScoreRestEvent event() {
        return new ScoreRestEvent(0, .3f, .4f, .1f, 0, 1, .5f);
    }

    @Test
    public void workingAcceptanceAndDeferredRepresentedStayLegacyUntilCommit() throws Exception {
        byte[] raw = ClosedCounterRestInkTest.bowl();
        var notes = ClosedCounterRestInkTest.notes(raw);
        var scope = ClosedCounterRestInvocation.open(raw, 400, 240, notes);
        var old = new ArrayList<ScoreRestEvent>();
        inspect(raw, notes, old, null, false);
        assertEquals(1, old.size());
        var working = new ArrayList<ScoreRestEvent>();
        inspect(raw, notes, working, scope, false);
        assertEquals(old, working);
        boolean[] mask = new boolean[41];
        mask[14] = mask[15] = true;
        var bodies = SeparatedRestInk.find(raw, 400, 240, 140, 147, 80, 120, mask, 10);
        assertFalse(bodies.isEmpty());
        for (var b : bodies)
            assertEquals(
                    SeparatedRestInk.represented(
                            b,
                            new SixteenthRestDetector.Staff(80, 120, 10, 0, 1),
                            M,
                            old,
                            400,
                            240),
                    SeparatedRestInk.represented(
                            b,
                            new SixteenthRestDetector.Staff(80, 120, 10, 0, 1),
                            M,
                            working,
                            400,
                            240));
        assertTrue(
                scope.finish(new SixteenthRestDetector.Detection(working, List.of()))
                        .rests()
                        .isEmpty());
        assertEquals(1, working.size());
    }

    @Test
    public void onlyExactEventsAndExplicitProducerDotAliasesAreRemoved() throws Exception {
        byte[] raw = ClosedCounterRestInkTest.bowl();
        var notes = ClosedCounterRestInkTest.notes(raw);
        var scope = ClosedCounterRestInvocation.open(raw, 400, 240, notes);
        var source = event();
        scope.record(source);
        var staff =
                new SixteenthRestDetector.Staff(
                        80, 120, 10, 0, 1, StaffPitchTrack.linear(400, 120, 10, 0));
        Method m =
                Arrays.stream(SixteenthRestDetector.class.getDeclaredMethods())
                        .filter(x -> x.getName().equals("sourceCounterRest"))
                        .findFirst()
                        .orElseThrow();
        m.setAccessible(true);
        var mapped = (ScoreRestEvent) m.invoke(null, source, staff, M, 400, 240, 0, 240, scope);
        var dotAlias = (ScoreRestEvent) m.invoke(null, source, staff, M, 400, 240, 0, 240, scope);
        assertNotSame(mapped, dotAlias);
        var equal =
                new ScoreRestEvent(
                        mapped.measureIndex(),
                        mapped.positionInMeasure(),
                        mapped.pageY(),
                        mapped.pageHeight(),
                        mapped.staffIndex(),
                        mapped.staffCount(),
                        mapped.durationBeats());
        assertEquals(mapped, equal);
        var linked = new SixteenthRestDetector.RestDot(3, 4, dotAlias);
        var unrelated = new SixteenthRestDetector.RestDot(3, 4, equal);
        var result =
                scope.finish(
                        new SixteenthRestDetector.Detection(
                                List.of(mapped, equal), List.of(linked, unrelated)));
        assertEquals(1, result.rests().size());
        assertSame(equal, result.rests().get(0));
        assertSame(unrelated, result.dots().get(0));
        assertEquals(1, result.dots().size());
    }

    @Test
    public void bookkeepingOrLateFinalizationRefusalReturnsWholeLegacy() {
        for (boolean late : new boolean[] {false, true}) {
            byte[] raw = ClosedCounterRestInkTest.bowl();
            var scope =
                    ClosedCounterRestInvocation.open(
                            raw, 400, 240, ClosedCounterRestInkTest.notes(raw));
            var e = event();
            var legacy =
                    new SixteenthRestDetector.Detection(
                            List.of(e), List.of(new SixteenthRestDetector.RestDot(0, 0, e)));
            if (late) scope.record(e);
            scope.budget.take(scope.budget.workLimit - scope.budget.workUsed, 0);
            if (!late) scope.record(e);
            assertSame(legacy, scope.finish(legacy));
            assertEquals(1, legacy.rests().size());
            assertEquals(1, legacy.dots().size());
        }
    }

    @Test
    public void refusedUnclassifiedAndDuplicateGuardsRetainOldScheduling() throws Exception {
        for (boolean refusal : new boolean[] {false, true}) {
            byte[] raw = ClosedCounterRestInkTest.bowl();
            var notes = ClosedCounterRestInkTest.notes(raw);
            var scope = ClosedCounterRestInvocation.open(raw, 400, 240, notes);
            if (refusal) scope.budget.take(scope.budget.workLimit - scope.budget.workUsed, 0);
            var old = new ArrayList<ScoreRestEvent>();
            inspect(raw, notes, old, null, false);
            var working = new ArrayList<ScoreRestEvent>();
            inspect(raw, notes, working, scope, true);
            assertEquals(old, working);
            var legacy = new SixteenthRestDetector.Detection(working, List.of());
            if (refusal) assertSame(legacy, scope.finish(legacy));
            var duplicateScope = ClosedCounterRestInvocation.open(raw, 400, 240, notes);
            var duplicate = new ArrayList<ScoreRestEvent>(old);
            inspect(raw, notes, duplicate, duplicateScope, true);
            assertEquals(old, duplicate);
            var d = new SixteenthRestDetector.Detection(duplicate, List.of());
            assertSame(d, duplicateScope.finish(d));
        }
        byte[] dark = PartialBowlRestOriginalTest.bowl(false);
        var notes = ClosedCounterRestInkTest.notes(dark);
        var scope = ClosedCounterRestInvocation.open(dark, 400, 240, notes);
        var old = new ArrayList<ScoreRestEvent>();
        inspect(dark, notes, old, null, false);
        var now = new ArrayList<ScoreRestEvent>();
        inspect(dark, notes, now, scope, false);
        assertEquals(old, now);
        var legacy = new SixteenthRestDetector.Detection(now, List.of());
        assertSame(legacy, scope.finish(legacy));
        assertEquals(
                1,
                ClosedCounterFalsifiersTest.oldDeepLowered(
                                ClosedCounterFalsifiersTest.originalEighth())
                        .size());
    }
}
