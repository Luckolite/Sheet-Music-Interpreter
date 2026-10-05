// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScorePedalDetectorTest {
    @Test
    public void flattenedNativeAudioRegionsDoNotLoseThePrintedHookClock() {
        var original = page();
        var flattened =
                new ScorePageInterpretation(
                                List.of(
                                        new MeasureRegion(0, 1, 0, 1),
                                        new MeasureRegion(0, 1, 0, 1)),
                                original.notes())
                        .withExpressiveEvents(original.expressiveEvents());
        var resolved = ScorePedalDetector.resolve(flattened, new ScoreMeterMap(4, List.of()));
        assertEquals(
                new ScoreAnchor(0, 2), resolved.expressiveEvents().get(0).start().orElseThrow());
        assertEquals(
                new ScoreAnchor(1, 0), resolved.expressiveEvents().get(1).start().orElseThrow());
    }

    private static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(160, 200, 10, 1, 2);
    private static final List<MeasureRegion> MEASURES =
            List.of(
                    new MeasureRegion(.1f, .5f, .25f, .45f),
                    new MeasureRegion(.5f, .9f, .25f, .45f));

    private static ScoreNoteEvent note(int measure, float position, int step) {
        return new ScoreNoteEvent(
                measure,
                position,
                step,
                1,
                2,
                (200 - step * 5f) / 500,
                false,
                0,
                0,
                99,
                1,
                1,
                0,
                0,
                0,
                false,
                0,
                false,
                0,
                0);
    }

    private static List<ScoreNoteEvent> notes(int offset) {
        return List.of(
                note(offset, .12f, 0),
                note(offset, .25f, 2),
                note(offset, .6f, 4),
                note(offset, .86f, 6),
                note(offset + 1, .1f, 0));
    }

    private static ScorePageInterpretation page() {
        var page = new ScorePageInterpretation(MEASURES, notes(0));
        return ScorePedalDetector.withBrackets(
                page,
                List.of(new PedalBracketDetector.Bracket(340, 499, 270, 0, 10, 1, 2)),
                List.of(STAFF),
                1000,
                500);
    }

    @Test
    public void unknownMeterLeavesBothHooksUnresolved() {
        var page = ScorePedalDetector.resolve(page(), null);
        assertEquals(2, page.expressiveEvents().size());
        for (var event : page.expressiveEvents()) {
            assertTrue(event.start().isEmpty());
            assertEquals(ScoreExpressiveEvent.Scope.UNRESOLVED, event.scope());
        }
    }

    @Test
    public void knownMeterResolvesButRetainsEvidenceAndNotes() {
        var original = page();
        var resolved = ScorePedalDetector.resolve(original, new ScoreMeterMap(4, List.of()));
        assertEquals(
                new ScoreAnchor(0, 2), resolved.expressiveEvents().get(0).start().orElseThrow());
        assertEquals(
                new ScoreAnchor(1, 0), resolved.expressiveEvents().get(1).start().orElseThrow());
        assertEquals(original.notes(), resolved.notes());
        assertEquals(
                original.expressiveEvents().get(0).eventId(),
                resolved.expressiveEvents().get(0).eventId());
        assertEquals(
                original.expressiveEvents().get(0).evidence(),
                resolved.expressiveEvents().get(0).evidence());
    }

    @Test
    public void offsetPageRangeDoesNotBindToOverlappingGeometryOnEarlierPage() {
        var original = page();
        var measures = new ArrayList<>(MEASURES);
        measures.addAll(MEASURES);
        var allNotes = new ArrayList<>(notes(0));
        allNotes.addAll(notes(2));
        var events =
                original.expressiveEvents().stream()
                        .map(e -> ScorePedalDetector.offsetEvidence(e, 2, 1))
                        .toList();
        var combined = new ScorePageInterpretation(measures, allNotes).withExpressiveEvents(events);
        var resolved = ScorePedalDetector.resolve(combined, new ScoreMeterMap(4, List.of()));
        assertEquals(
                new ScoreAnchor(2, 2), resolved.expressiveEvents().get(0).start().orElseThrow());
        assertEquals(
                new ScoreAnchor(3, 0), resolved.expressiveEvents().get(1).start().orElseThrow());
        assertEquals(1, resolved.expressiveEvents().get(0).evidence().get(0).pageIndex());
        assertTrue(resolved.expressiveEvents().get(0).eventId().startsWith("page:1/"));
    }

    @Test
    public void staleCachedAnchorsClearWhenMeterIsUnavailable() {
        var known = ScorePedalDetector.resolve(page(), new ScoreMeterMap(4, List.of()));
        var unknown = ScorePedalDetector.resolve(known, null);
        assertTrue(unknown.expressiveEvents().get(0).start().isEmpty());
        assertTrue(unknown.expressiveEvents().get(0).end().isEmpty());
    }

    @Test
    public void missingStaffWitnessDoesNotInventOwnership() {
        var page = page();
        var missing =
                new ScorePageInterpretation(MEASURES, List.of())
                        .withExpressiveEvents(page.expressiveEvents());
        for (var event :
                ScorePedalDetector.resolve(missing, new ScoreMeterMap(4, List.of()))
                        .expressiveEvents()) assertTrue(event.start().isEmpty());
    }

    @Test
    public void repeatedDetectionKeepsStableSourceIdentity() {
        var page = page();
        var second =
                ScorePedalDetector.withBrackets(
                        page,
                        List.of(new PedalBracketDetector.Bracket(340, 499, 270, 0, 10, 1, 2)),
                        List.of(STAFF),
                        1000,
                        500);
        assertEquals(page.expressiveEvents(), second.expressiveEvents());
    }

    @Test
    public void unrelatedPedalEvidenceIsPreserved() {
        var external =
                new ScoreExpressiveEvent(
                        "imported",
                        ScoreExpressiveEvent.Kind.PEDAL_DOWN,
                        Optional.of(new ScoreAnchor(0, 0)),
                        Optional.of(new ScoreAnchor(1, 0)),
                        ScoreExpressiveEvent.Scope.PART,
                        1,
                        2,
                        Optional.of("another-importer"),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        "",
                        List.of(new ScoreExpressiveEvent.Evidence("external", 0, .4f, 1, 2, "")));
        var original = page();
        var events = new ArrayList<>(original.expressiveEvents());
        events.add(external);
        var resolved =
                ScorePedalDetector.resolve(
                        original.withExpressiveEvents(events), new ScoreMeterMap(4, List.of()));
        assertEquals(external, resolved.expressiveEvents().get(2));
    }

    @Test
    public void cachedSemanticWireRetainsDeferredHooksAndCanResolveLater() throws Exception {
        var original = page();
        var bytes = new ByteArrayOutputStream();
        ScoreSemanticWire.writeExpressions(
                new DataOutputStream(bytes), original.expressiveEvents(), 2);
        var events =
                ScoreSemanticWire.readExpressions(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 2);
        assertEquals(original.expressiveEvents(), events);
        var resolved =
                ScorePedalDetector.resolve(
                        original.withExpressiveEvents(events), new ScoreMeterMap(4, List.of()));
        assertEquals(
                new ScoreAnchor(0, 2), resolved.expressiveEvents().get(0).start().orElseThrow());
        assertEquals(
                new ScoreAnchor(1, 0), resolved.expressiveEvents().get(1).start().orElseThrow());
    }

    @Test
    public void pairedHooksKeepDistinctClocksAndDoNotEscapeTheResolveCall() {
        var original =
                ScorePedalDetector.withBrackets(
                        new ScorePageInterpretation(MEASURES, notes(0)),
                        List.of(
                                new PedalBracketDetector.Bracket(340, 444, 270, 0, 10, 1, 2),
                                new PedalBracketDetector.Bracket(148, 340, 270, 0, 10, 1, 2)),
                        List.of(STAFF),
                        1000,
                        500);
        var generated = original.expressiveEvents();
        assertEquals(4, generated.size());
        var external =
                new ScoreExpressiveEvent(
                        "external-between-hooks",
                        ScoreExpressiveEvent.Kind.PEDAL_DOWN,
                        Optional.of(new ScoreAnchor(0, 0)),
                        Optional.of(new ScoreAnchor(1, 0)),
                        ScoreExpressiveEvent.Scope.PART,
                        1,
                        2,
                        Optional.of("external-target"),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        "unchanged",
                        List.of(new ScoreExpressiveEvent.Evidence("external", 0, .4f, 1, 2, "")));
        var events =
                List.of(
                        generated.get(0),
                        external,
                        generated.get(1),
                        generated.get(2),
                        generated.get(3));
        var page = original.withExpressiveEvents(events);
        var beforeNotes = List.copyOf(page.notes());
        var beforeEvents = List.copyOf(page.expressiveEvents());
        var meter = new ScoreMeterMap(4, List.of());
        var expected =
                List.of(
                        proved(generated.get(0), new ScoreAnchor(0, 2), new ScoreAnchor(0, 3)),
                        external,
                        proved(generated.get(1), new ScoreAnchor(0, 3), null),
                        proved(generated.get(2), new ScoreAnchor(0, 0), new ScoreAnchor(0, 2)),
                        proved(generated.get(3), new ScoreAnchor(0, 2), null));
        var resolved = ScorePedalDetector.resolve(page, meter);
        assertEquals(expected, resolved.expressiveEvents());
        assertEquals(page.withExpressiveEvents(expected), resolved);
        assertSame(external, resolved.expressiveEvents().get(1));
        var unknown = ScorePedalDetector.resolve(resolved, (ScoreMeterMap) null);
        for (var event : unknown.expressiveEvents()) {
            if (event == external) continue;
            assertTrue(event.start().isEmpty());
            assertTrue(event.end().isEmpty());
            assertEquals(ScoreExpressiveEvent.Scope.UNRESOLVED, event.scope());
        }
        var noWitness =
                new ScorePageInterpretation(MEASURES, List.of()).withExpressiveEvents(events);
        for (var event : ScorePedalDetector.resolve(noWitness, meter).expressiveEvents()) {
            if (event == external) continue;
            assertTrue(event.start().isEmpty());
            assertTrue(event.end().isEmpty());
        }
        assertEquals(expected, ScorePedalDetector.resolve(unknown, meter).expressiveEvents());
        assertEquals(beforeNotes, page.notes());
        assertEquals(beforeEvents, page.expressiveEvents());
    }

    private static ScoreExpressiveEvent proved(
            ScoreExpressiveEvent event, ScoreAnchor start, ScoreAnchor end) {
        return new ScoreExpressiveEvent(
                event.eventId(),
                event.kind(),
                Optional.of(start),
                Optional.ofNullable(end),
                ScoreExpressiveEvent.Scope.PART,
                event.staffIndex(),
                event.staffCount(),
                event.targetEventId(),
                event.strength(),
                event.qualifierText(),
                event.evidence());
    }

    @Test
    public void malformedOrMismatchedHookOwnershipKeepsCompleteOriginalEvents() {
        var original = page();
        var down = original.expressiveEvents().get(0);
        var up = original.expressiveEvents().get(1);
        assertTrue(ScorePedalDetector.owns(down));
        assertTrue(ScorePedalDetector.owns(up));
        var target = down.targetEventId().orElseThrow();
        var malformedTargets = new ArrayList<String>();
        malformedTargets.add(null);
        malformedTargets.add("external-target");
        malformedTargets.add("pedal-hooks-v1:broken");
        malformedTargets.add(target + ":extra");
        int[] fields = {1, 1, 2, 3, 4, 10};
        String[] invalid = {
            "-1",
            "2147483648",
            "3",
            Integer.toString(Float.floatToIntBits(Float.NaN)),
            Integer.toString(Float.floatToIntBits(-.01f)),
            "0"
        };
        for (int i = 0; i < fields.length; i++) {
            var pieces = target.split(":");
            pieces[fields[i]] = invalid[i];
            malformedTargets.add(String.join(":", pieces));
        }
        var unowned = new ArrayList<ScoreExpressiveEvent>();
        for (var badTarget : malformedTargets)
            unowned.add(
                    ownershipVariant(
                            down,
                            unowned.size(),
                            down.kind(),
                            badTarget,
                            down.staffIndex(),
                            down.staffCount(),
                            down.evidence()));
        unowned.add(
                ownershipVariant(
                        down,
                        unowned.size(),
                        ScoreExpressiveEvent.Kind.RITARDANDO,
                        target,
                        down.staffIndex(),
                        down.staffCount(),
                        down.evidence()));
        unowned.add(
                ownershipVariant(down, unowned.size(), down.kind(), target, 0, 2, down.evidence()));
        unowned.add(
                ownershipVariant(down, unowned.size(), down.kind(), target, 1, 3, down.evidence()));
        unowned.add(
                ownershipVariant(
                        down,
                        unowned.size(),
                        down.kind(),
                        target,
                        1,
                        2,
                        List.of(new ScoreExpressiveEvent.Evidence("external", 0, .4f, 1, 2, ""))));
        unowned.add(
                ownershipVariant(
                        down,
                        unowned.size(),
                        down.kind(),
                        target,
                        1,
                        2,
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "printed-pedal-bracket", 0, .4f, 0, 2, ""))));
        var events = new ArrayList<ScoreExpressiveEvent>();
        events.add(down);
        events.addAll(unowned);
        events.add(up);
        var source = original.withExpressiveEvents(events);
        var beforeNotes = List.copyOf(source.notes());
        var beforeEvents = List.copyOf(source.expressiveEvents());
        var expected = new ArrayList<ScoreExpressiveEvent>();
        expected.add(proved(down, new ScoreAnchor(0, 2), new ScoreAnchor(1, 0)));
        expected.addAll(unowned);
        expected.add(proved(up, new ScoreAnchor(1, 0), null));
        var resolved = ScorePedalDetector.resolve(source, new ScoreMeterMap(4, List.of()));
        assertEquals(source.withExpressiveEvents(expected), resolved);
        for (int i = 0; i < unowned.size(); i++) {
            assertFalse(ScorePedalDetector.owns(unowned.get(i)));
            assertSame(unowned.get(i), resolved.expressiveEvents().get(i + 1));
        }
        assertEquals(beforeNotes, source.notes());
        assertEquals(beforeEvents, source.expressiveEvents());
        try {
            ScorePedalDetector.owns(null);
            fail("Null event must retain its original failure");
        } catch (NullPointerException expectedFailure) {
        }
    }

    private static ScoreExpressiveEvent ownershipVariant(
            ScoreExpressiveEvent event,
            int ordinal,
            ScoreExpressiveEvent.Kind kind,
            String target,
            int staff,
            int count,
            List<ScoreExpressiveEvent.Evidence> evidence) {
        return new ScoreExpressiveEvent(
                "ownership-invalid:" + ordinal,
                kind,
                Optional.empty(),
                Optional.empty(),
                ScoreExpressiveEvent.Scope.UNRESOLVED,
                staff,
                count,
                Optional.ofNullable(target),
                event.strength(),
                event.qualifierText(),
                evidence);
    }
}
