// Copyright 2026 Music Sheets contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Original procedural data only. Uses actual standalone expressive/navigation consumers; no app/engine. */
public final class CurrentRestAudioContextTest {
    static ScoreExpressiveEvent event(String id, Kind kind, String evidence) {
        return new ScoreExpressiveEvent(
                id,
                kind,
                Optional.empty(),
                Optional.empty(),
                Scope.UNRESOLVED,
                0,
                2,
                Optional.of(
                        (kind == Kind.FERMATA ? "printed-rest:" : "expression-rest-column:")
                                + "0:0:2:"
                                + Float.floatToIntBits(.4f)),
                Strength.UNSPECIFIED,
                "",
                List.of(new Evidence(evidence, 0, .4f, 0, 2, "original synthetic")));
    }

    static ScorePageInterpretation score(
            List<ScoreRestEvent> rests, List<ScoreExpressiveEvent> events, int measures) {
        var ms = new ArrayList<MeasureRegion>();
        for (int i = 0; i < measures; i++) ms.add(new MeasureRegion(0, 1, 0, 1));
        return new ScorePageInterpretation(
                ms,
                List.of(
                        new ScoreNoteEvent(
                                0,
                                .1f,
                                0,
                                0,
                                2,
                                .3f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1f),
                        new ScoreNoteEvent(
                                0,
                                .8f,
                                2,
                                1,
                                2,
                                .6f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1f)),
                1,
                List.of(),
                List.of(),
                List.of(),
                rests,
                List.of(),
                List.of(),
                List.of(),
                events);
    }

    static ScorePageInterpretation fixture() {
        return score(
                List.of(
                        new ScoreRestEvent(0, .4f, .3f, .025f, 0, 2, 1),
                        new ScoreRestEvent(0, .7f, .3f, .025f, 0, 2, 1)),
                List.of(event("current-rest-fermata", Kind.FERMATA, "fermata-rest-raw-ink")),
                1);
    }

    static CurrentRestAudioContext.Prepared prepare(ScorePageInterpretation s, double at)
            throws IOException {
        return CurrentRestAudioContext.prepare(
                s,
                Map.of(
                        0,
                        new CurrentRestWritten.Placement(at, 1),
                        1,
                        new CurrentRestWritten.Placement(at == 1 ? 2 : 1, 1)),
                3,
                new ScoreBoundaryClock(3, 3));
    }

    // The actual source-owner producer is iteration in full Current. No selected-index/value
    // search.
    static CurrentRestAudioContext.Selection select(
            CurrentRestAudioContext.Prepared p, boolean rests) {
        var rs = new ArrayList<ScoreRestEvent>();
        var ro = new ArrayList<Integer>();
        for (int i = 0; i < p.source().rests().size(); i++)
            if (rests) {
                rs.add(p.source().rests().get(i));
                ro.add(i);
            }
        var es = new ArrayList<ScoreExpressiveEvent>();
        var eo = new ArrayList<Integer>();
        for (int i = 0; i < p.source().expressiveEvents().size(); i++) {
            es.add(p.source().expressiveEvents().get(i));
            eo.add(i);
        }
        var source = p.source();
        var s =
                new ScorePageInterpretation(
                        source.measures(),
                        source.notes(),
                        source.firstMeasureNumber(),
                        source.keyChanges(),
                        source.tempoChanges(),
                        source.meterChanges(),
                        rs,
                        source.techniqueChanges(),
                        source.dynamicChanges(),
                        source.playbackDirections(),
                        es);
        return new CurrentRestAudioContext.Selection(source, s, ro, eo);
    }

    record Consumed(
            ScoreExpressivePerformance.Result expressive,
            ScoreNavigationPerformance.Result navigation) {}

    static Consumed consume(
            CurrentRestAudioContext.Prepared p, CurrentRestAudioContext.Selection selected)
            throws IOException {
        return consume(
                p,
                selected,
                List.of(
                        new ScoreExpressivePerformance.Sound("authored-note-0", 0, 1, 0, 2),
                        new ScoreExpressivePerformance.Sound("authored-note-1", 2, 3, 1, 2)));
    }

    static Consumed consume(
            CurrentRestAudioContext.Prepared p,
            CurrentRestAudioContext.Selection selected,
            List<ScoreExpressivePerformance.Sound> sounds)
            throws IOException {
        var projection = CurrentRestAudioContext.project(p, selected);
        var expressive =
                ScoreExpressivePerformance.resolve(
                        120,
                        p.frame(),
                        p.source().measures().size(),
                        List.of(),
                        projection.score().expressiveEvents(),
                        sounds,
                        Map.of(),
                        ScoreExpressivePerformance.Policy.preview());
        var plan =
                ScoreNavigationPlan.create(
                        p.source().measures().size(),
                        projection.score().playbackDirections(),
                        p.frame());
        var navigation =
                ScoreNavigationPerformance.project(
                        expressive.timeline(),
                        plan,
                        p.frame(),
                        expressive.holdOwnership(),
                        (t, o) -> Optional.empty());
        return new Consumed(expressive, navigation);
    }

    static String identity(Consumed p) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(
                                        (p.navigation().timeline().tempoSegments()
                                                        + "|"
                                                        + p.navigation().timeline().holds())
                                                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void literalOwnChangesActualPerformanceAndResultIdentityWithSameNotes()
            throws Exception {
        var full = fixture();
        var a = prepare(full, 1);
        var b = prepare(full, 2);
        var pa = consume(a, select(a, true));
        var pb = consume(b, select(b, true));
        assertSame(full, a.fullCurrent());
        assertSame(full, b.fullCurrent());
        assertEquals(a.source().notes(), b.source().notes());
        assertEquals(2, pa.navigation().timeline().holds().get(0).beat(), 0);
        assertEquals(3, pb.navigation().timeline().holds().get(0).beat(), 0);
        assertEquals(
                1.5,
                pa.navigation()
                        .timeline()
                        .secondsAtBeat(2, ScorePerformanceTimeline.Boundary.AFTER),
                0);
        assertEquals(
                1,
                pb.navigation()
                        .timeline()
                        .secondsAtBeat(2, ScorePerformanceTimeline.Boundary.AFTER),
                0);
        assertNotEquals(identity(pa), identity(pb));
        assertEquals(identity(pb), identity(consume(b, select(b, true))));
    }

    @Test
    public void emptyMapRetainsLegacyDispatchWithoutNewFramePolicy() throws Exception {
        assertNull(CurrentRestAudioContext.prepare(null, Map.of(), Float.NaN, null));
    }

    @Test
    public void bindingIsFullCurrentBeforeSelectionAndAmbiguityCannotDisappear() throws Exception {
        var r = fixture().rests().get(0);
        var s = score(List.of(r, r), fixture().expressiveEvents(), 1);
        assertThrows(IOException.class, () -> prepare(s, 1));
    }

    @Test
    public void equalValuedLiteralOwnersRemainSeparateInRealIterator() throws Exception {
        var r = fixture().rests().get(0);
        var s = score(List.of(r, r), List.of(), 1);
        var p = prepare(s, 1);
        var selection = select(p, true);
        assertEquals(List.of(0, 1), selection.restOwners());
        assertEquals(2, CurrentRestAudioContext.project(p, selection).score().rests().size());
    }

    @Test
    public void omittedLocalRestMarksDisappearButRealScoreTempoRemains() throws Exception {
        var p = prepare(fixture(), 1);
        assertTrue(
                CurrentRestAudioContext.project(p, select(p, false))
                        .score()
                        .expressiveEvents()
                        .isEmpty());
        var s =
                score(
                        fixture().rests(),
                        List.of(event("tempo", Kind.RITARDANDO, "printed-expression-word")),
                        1);
        p = prepare(s, 1);
        var out = CurrentRestAudioContext.project(p, select(p, false));
        assertEquals(1, out.score().expressiveEvents().size());
        assertEquals(Scope.SCORE, out.score().expressiveEvents().get(0).scope());
        assertEquals(
                new ScoreAnchor(0, 1), out.score().expressiveEvents().get(0).start().orElseThrow());
    }

    @Test
    public void breathAndCaesuraUseDeclaredReleaseAndActualConsumer() throws Exception {
        for (var k : List.of(Kind.BREATH, Kind.CAESURA)) {
            var p =
                    prepare(
                            score(
                                    fixture().rests(),
                                    List.of(event("symbol", k, "printed-expression-symbol")),
                                    1),
                            1);
            assertEquals(
                    new ScoreAnchor(0, 2), p.byExpression().get(0).event().start().orElseThrow());
            assertFalse(consume(p, select(p, true)).expressive().timeline().holds().isEmpty());
        }
    }

    @Test
    public void unsupportedRolesAndMissingDeclaredTargetRefuse() throws Exception {
        assertThrows(
                IOException.class,
                () ->
                        prepare(
                                score(
                                        fixture().rests(),
                                        List.of(
                                                event(
                                                        "bad",
                                                        Kind.ARPEGGIO,
                                                        "printed-expression-word")),
                                        1),
                                1));
        assertThrows(
                IOException.class,
                () ->
                        CurrentRestAudioContext.prepare(
                                fixture(),
                                Map.of(1, new CurrentRestWritten.Placement(1, 1)),
                                3,
                                new ScoreBoundaryClock(3, 3)));
        assertThrows(
                IOException.class,
                () ->
                        prepare(
                                score(
                                        fixture().rests(),
                                        List.of(
                                                event(
                                                        "bad",
                                                        Kind.BREATH,
                                                        "printed-expression-word")),
                                        1),
                                1));
    }

    @Test
    public void sourceEvidenceDoesNotFabricateAnotherAuthorityDomain() throws Exception {
        assertThrows(
                IOException.class,
                () ->
                        prepare(
                                score(
                                        fixture().rests(),
                                        List.of(
                                                event(
                                                        "unknown",
                                                        Kind.FERMATA,
                                                        "source-native-original")),
                                        1),
                                1));
    }

    @Test
    public void exactBoundaryAndClosingPrecedenceReachRealConsumer() throws Exception {
        double opening = Math.nextDown(3.0), closing = Math.nextDown(opening);
        var r = new ScoreRestEvent(0, .4f, .3f, .025f, 0, 2, closing - 1);
        var s = score(List.of(r), fixture().expressiveEvents(), 1);
        var p =
                CurrentRestAudioContext.prepare(
                        s,
                        Map.of(0, new CurrentRestWritten.Placement(1, closing - 1)),
                        3,
                        new ScoreBoundaryClock(opening, closing));
        assertEquals(
                Double.doubleToRawLongBits(closing),
                Double.doubleToRawLongBits(
                        p.frame()
                                .quarterBeatsInMeasure(
                                        0))); // Explicit rest-only performed selection; do not
                                              // invent or round a NOTE clock outside the shortened
                                              // frame.
        var result = consume(p, select(p, true), List.of());
        assertEquals(closing, result.navigation().timeline().holds().get(0).beat(), 0);
        assertEquals(new ScoreAnchor(1, 0), p.byExpression().get(0).event().end().orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScoreAnchor(0, Math.nextUp(closing)).canonical(p.frame(), 1));
    }

    @Test
    public void independentLaterExactSpanAvoidsCumulativeSubtractionLoss() throws Exception {
        double a = Math.nextDown(3.0), b = Math.nextDown(2.5);
        var meter = new ScoreMeterMap(3, List.of()).withExactBoundaryQuarterBeats(a, b, 2);
        assertEquals(
                Double.doubleToRawLongBits(b),
                Double.doubleToRawLongBits(meter.quarterBeatsInMeasure(1)));
        assertEquals(new ScoreAnchor(2, 0), new ScoreAnchor(1, b).canonical(meter, 2));
    }

    @Test
    public void changedMeterBoundsAndUnchangedAnchorLimitsRemain() throws Exception {
        var base = new ScoreMeterMap(3, List.of(new ScoreMeterChange(1, 5, 8)));
        var meter = base.withExactBoundaryQuarterBeats(Math.nextDown(3.0), Math.nextDown(2.5), 3);
        assertEquals(2.5, meter.quarterBeatsInMeasure(1), 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> base.withExactBoundaryQuarterBeats(3, 2.6, 3));
        assertThrows(IllegalArgumentException.class, () -> new ScoreAnchor(0, Math.nextUp(128.0)));
    }

    @Test
    public void wrongDurationOutOfFrameAndFullMeasureLiteralRefuse() throws Exception {
        assertThrows(
                IOException.class,
                () ->
                        CurrentRestAudioContext.prepare(
                                fixture(),
                                Map.of(0, new CurrentRestWritten.Placement(1, 2)),
                                3,
                                new ScoreBoundaryClock(3, 3)));
        assertThrows(IOException.class, () -> prepare(fixture(), 3));
        var s =
                score(
                        List.of(ScoreRestEvent.fullMeasure(0, .4f, .3f, .025f, 0, 2)),
                        fixture().expressiveEvents(),
                        1);
        assertThrows(
                IOException.class,
                () ->
                        CurrentRestAudioContext.prepare(
                                s,
                                Map.of(0, new CurrentRestWritten.Placement(0, 4)),
                                4,
                                new ScoreBoundaryClock(4, 4)));
    }

    @Test
    public void projectionRefusesForeignDuplicateAndOutOfRangePorts() throws Exception {
        var p = prepare(fixture(), 1);
        var s = select(p, true);
        assertThrows(
                IOException.class,
                () ->
                        CurrentRestAudioContext.project(
                                p,
                                new CurrentRestAudioContext.Selection(
                                        fixture(),
                                        s.score(),
                                        s.restOwners(),
                                        s.expressionOwners())));
        assertThrows(
                IOException.class,
                () ->
                        CurrentRestAudioContext.project(
                                p,
                                new CurrentRestAudioContext.Selection(
                                        p.source(),
                                        s.score(),
                                        List.of(0, 0),
                                        s.expressionOwners())));
        assertThrows(
                IOException.class,
                () ->
                        CurrentRestAudioContext.project(
                                p,
                                new CurrentRestAudioContext.Selection(
                                        p.source(),
                                        s.score(),
                                        List.of(0, 99),
                                        s.expressionOwners())));
    }

    @Test
    public void contextAndPortsCannotBeMutatedOrPubliclyConstructed() throws Exception {
        var p = prepare(fixture(), 1);
        assertEquals(0, CurrentRestAudioContext.Prepared.class.getConstructors().length);
        assertThrows(UnsupportedOperationException.class, () -> p.byExpression().clear());
        var selected = select(p, true);
        assertThrows(UnsupportedOperationException.class, () -> selected.restOwners().clear());
    }

    @Test
    public void scanPrepayExactOneUnderOverZeroOverflow() throws Exception {
        assertEquals(0, CurrentRestAudioContext.checkedScanWork(0, Long.MAX_VALUE));
        assertEquals(16777215, CurrentRestAudioContext.checkedScanWork(1, 16777215));
        assertEquals(16777216, CurrentRestAudioContext.checkedScanWork(4096, 4096));
        assertThrows(IOException.class, () -> CurrentRestAudioContext.checkedScanWork(1, 16777217));
        assertThrows(
                IOException.class,
                () -> CurrentRestAudioContext.checkedScanWork(Long.MAX_VALUE, 2));
        assertThrows(IOException.class, () -> CurrentRestAudioContext.checkedScanWork(-1, 1));
    }

    @Test
    public void rawRowsPreserveNegativeZeroAndRejectPrecisionOrFutureVersion() throws Exception {
        var row = CurrentRestWrittenFields.row("0", "8000000000000000", "3ff0000000000000");
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(row.clock().onsetBeats()));
        assertEquals(3, CurrentRestStateVersion.read("{\"version\":3}"));
        assertThrows(
                IOException.class,
                () -> CurrentRestWrittenFields.row("00", "0000000000000000", "3ff0000000000000"));
        assertThrows(
                IOException.class,
                () -> CurrentRestWrittenFields.row("0", "7ff0000000000000", "3ff0000000000000"));
        assertThrows(IOException.class, () -> CurrentRestStateVersion.read("{\"version\":4}"));
        assertThrows(
                IOException.class,
                () -> CurrentRestStateVersion.read("{\"version\":3.0000000000000001}"));
    }

    @Test
    public void legacyMeterAnchorModeAndFloatBranchesRemainUnchanged() throws Exception {
        var m =
                new ScoreMeterMap(3, List.of())
                        .withBoundaryQuarterBeats(Math.nextDown(3.0), Math.nextDown(2.5), 2);
        assertFalse(m.exactAnchorMode());
        assertEquals((double) (float) Math.nextDown(3.0), m.anchorQuarterBeatsInMeasure(0), 0);
        assertEquals(new ScoreAnchor(1, 0), new ScoreAnchor(0, 3).canonical(m, 2));
    }
}
