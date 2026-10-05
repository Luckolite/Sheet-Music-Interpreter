// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated arc/dot ink and musical clocks, with no source score or font asset. */
public class ScoreFermataDetectorTest {
    static final int W = 500, H = 400;

    byte[] paper() {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        return gray;
    }

    void mark(byte[] gray, int cx, int cy, boolean inverted, boolean roof, boolean longArc) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                if (dx * dx + dy * dy <= 6) gray[(cy + dy) * W + cx + dx] = 0;
        if (!roof) return;
        int radius = longArc ? 30 : 12;
        for (int dx = -radius; dx <= radius; dx++) {
            double rise = 3 + 10 * Math.sqrt(Math.max(0, 1 - dx * dx / (double) (radius * radius)));
            int y = cy + (inverted ? 1 : -1) * (int) Math.round(rise);
            gray[y * W + cx + dx] = 0;
            gray[(y + 1) * W + cx + dx] = 0;
        }
    }

    ScoreNoteEvent note(float position, int staff, int step, int y, float duration) {
        return new ScoreNoteEvent(
                0, position, step, staff, 2, y / (float) H, false, 1, 0, 2, duration);
    }

    ScorePageInterpretation page(boolean meter, boolean chord) {
        var notes = new ArrayList<ScoreNoteEvent>();
        notes.add(note(.2f, 0, 0, 130, 1));
        notes.add(note(.78f, 0, 0, 130, 1));
        if (chord) {
            notes.add(note(.2f, 1, 0, 250, 1));
            notes.add(note(.78f, 1, 0, 250, 1));
            notes.add(note(.2f, 1, -4, 274, 1));
            notes.add(note(.7805f, 1, -4, 274, 1));
        }
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.1f, .9f, .2f, .9f)),
                notes,
                1,
                List.of(),
                List.of(),
                meter ? List.of(new ScoreMeterChange(0, 6, 8)) : List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    List<PlayingTechniqueDetector.Staff> staffs() {
        return List.of(
                new PlayingTechniqueDetector.Staff(120, 168, 12, 0, 2),
                new PlayingTechniqueDetector.Staff(240, 288, 12, 1, 2));
    }

    ScorePageInterpretation detect(
            ScorePageInterpretation page, byte[] gray, List<PlayingTechniqueDetector.Staff> staffs)
            throws Exception {
        try {
            var type = Class.forName("io.github.luckolite.interpreter.ScoreFermataDetector");
            var method =
                    type.getDeclaredMethod(
                            "withFermatas",
                            ScorePageInterpretation.class,
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            List.class);
            method.setAccessible(true);
            return (ScorePageInterpretation)
                    method.invoke(null, page, new byte[W * H], gray, W, H, staffs);
        } catch (ClassNotFoundException absent) {
            return page;
        }
    }

    ScorePageInterpretation resolve(ScorePageInterpretation page, float beats) throws Exception {
        try {
            var type = Class.forName("io.github.luckolite.interpreter.ScoreFermataDetector");
            return (ScorePageInterpretation)
                    type.getMethod("resolve", ScorePageInterpretation.class, float.class)
                            .invoke(null, page, beats);
        } catch (ClassNotFoundException absent) {
            return page;
        }
    }

    ScorePageInterpretation detected(boolean meter, boolean chord) throws Exception {
        byte[] gray = paper();
        mark(gray, 362, 106, false, true, false);
        if (chord) mark(gray, 362, 226, false, true, false);
        return detect(page(meter, chord), gray, staffs());
    }

    @Test
    public void uprightFermataBecomesAResolvedNoteEvent() throws Exception {
        var events = detected(true, false).expressiveEvents();
        assertEquals(1, events.size());
        var event = events.get(0);
        assertEquals(ScoreExpressiveEvent.Kind.FERMATA, event.kind());
        assertEquals(ScoreExpressiveEvent.Scope.NOTE, event.scope());
        assertEquals(new ScoreAnchor(0, 1.5), event.start().orElseThrow());
        assertEquals(new ScoreAnchor(1, 0), event.end().orElseThrow());
    }

    @Test
    public void chordAndOtherPartRemainSeparatelyOwned() throws Exception {
        var events = detected(true, true).expressiveEvents();
        assertEquals(2, events.size());
        assertEquals(List.of(0, 1), events.stream().map(ScoreExpressiveEvent::staffIndex).toList());
        assertEquals(ScoreExpressiveEvent.Scope.VOICE, events.get(1).scope());
        assertEquals(events.get(0).end(), events.get(1).end());
    }

    @Test
    public void isolatedDotDoesNotCreateAHold() throws Exception {
        byte[] gray = paper();
        mark(gray, 362, 106, false, false, false);
        assertTrue(detect(page(true, false), gray, staffs()).expressiveEvents().isEmpty());
    }

    @Test
    public void longSlurAndDotDoNotCreateAHold() throws Exception {
        byte[] gray = paper();
        mark(gray, 362, 106, false, true, true);
        assertTrue(detect(page(true, false), gray, staffs()).expressiveEvents().isEmpty());
    }

    @Test
    public void missingPhysicalStaffDoesNotOwnAHold() throws Exception {
        byte[] gray = paper();
        mark(gray, 362, 106, false, true, false);
        assertTrue(detect(page(true, false), gray, List.of()).expressiveEvents().isEmpty());
    }

    @Test
    public void displacedGlyphCannotClaimAnotherColumn() throws Exception {
        byte[] gray = paper();
        mark(gray, 300, 106, false, true, false);
        assertTrue(detect(page(true, false), gray, staffs()).expressiveEvents().isEmpty());
    }

    @Test
    public void noInheritedMeterIsInventedOnAContinuationPage() throws Exception {
        var events = detected(false, false).expressiveEvents();
        assertEquals(1, events.size());
        assertEquals(ScoreExpressiveEvent.Scope.UNRESOLVED, events.get(0).scope());
        assertTrue(events.get(0).start().isEmpty());
        assertTrue(events.get(0).end().isEmpty());
        assertTrue(events.get(0).targetEventId().isPresent());
    }

    @Test
    public void musicalContextResolvesContinuationWithoutEngravingFractions() throws Exception {
        var score = resolve(detected(false, false), 3);
        var event = score.expressiveEvents().get(0);
        assertEquals(new ScoreAnchor(0, 1.5), event.start().orElseThrow());
        assertEquals(new ScoreAnchor(1, 0), event.end().orElseThrow());
    }

    @Test
    public void malformedDetectorTargetIsPreservedUnchanged() throws Exception {
        var event =
                new ScoreExpressiveEvent(
                        "bad-target",
                        ScoreExpressiveEvent.Kind.FERMATA,
                        Optional.empty(),
                        Optional.empty(),
                        ScoreExpressiveEvent.Scope.UNRESOLVED,
                        0,
                        2,
                        Optional.of("printed-attack:0:0:2:NaN"),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        "fermata",
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "fermata-raw-ink", 0, .5f, 0, 2, "fermata")));
        var score = page(true, false).withExpressiveEvents(List.of(event));
        assertEquals(score, resolve(score, 3));
    }

    @Test
    public void independentlyAuthoredEventIsPreservedUnchanged() throws Exception {
        var event =
                new ScoreExpressiveEvent(
                        "author",
                        ScoreExpressiveEvent.Kind.FERMATA,
                        Optional.of(new ScoreAnchor(0, 1.5)),
                        Optional.of(new ScoreAnchor(1, 0)),
                        ScoreExpressiveEvent.Scope.NOTE,
                        0,
                        2,
                        Optional.of("custom-note"),
                        ScoreExpressiveEvent.Strength.MOLTO,
                        "fermata",
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "author", 0, .5f, 0, 2, "fermata")));
        var score = page(true, false).withExpressiveEvents(List.of(event));
        assertEquals(score, resolve(score, 3));
    }

    @Test
    public void invertedFermataPreservesItsPrintedOrientation() throws Exception {
        byte[] gray = paper();
        mark(gray, 362, 158, true, true, false);
        var events = detect(page(true, false), gray, staffs()).expressiveEvents();
        assertEquals(1, events.size());
        assertEquals("fermata inverted", events.get(0).qualifierText());
        assertEquals(new ScoreAnchor(1, 0), events.get(0).end().orElseThrow());
    }

    @Test
    public void ledgerHeadCanOwnADistantPrintedRoof() throws Exception {
        byte[] gray = paper();
        mark(gray, 362, 186, false, true, false);
        var events =
                detect(
                                page(true, true),
                                gray,
                                List.of(new PlayingTechniqueDetector.Staff(240, 288, 12, 1, 2)))
                        .expressiveEvents();
        assertEquals(1, events.size());
        assertEquals(1, events.get(0).staffIndex());
        assertEquals(ScoreExpressiveEvent.Scope.VOICE, events.get(0).scope());
    }

    @Test
    public void rebasingPreservesWrittenOwnershipAndUpdatesSourcePage() throws Exception {
        var score = detected(false, true);
        var event = score.expressiveEvents().get(1);
        var rebased = ScoreDynamicContinuation.offsetEvidence(event, 4, 2);
        assertEquals(2, rebased.evidence().get(0).pageIndex());
        assertTrue(rebased.eventId().startsWith("page:2/"));
        var notes =
                score.notes().stream()
                        .map(
                                n ->
                                        new ScoreNoteEvent(
                                                4,
                                                n.positionInMeasure(),
                                                n.staffStep(),
                                                n.staffIndex(),
                                                n.staffCount(),
                                                n.pageY(),
                                                n.tiedFromPrevious(),
                                                n.augmentationDots(),
                                                n.beamCount(),
                                                n.writtenAccidental(),
                                                n.unbeamedDurationBeats()))
                        .toList();
        var measures = Collections.nCopies(5, score.measures().get(0));
        var joined =
                new ScorePageInterpretation(
                        measures,
                        notes,
                        1,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 6, 8)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(rebased));
        var resolved = resolve(joined, Float.NaN).expressiveEvents().get(0);
        assertEquals(new ScoreAnchor(4, 1.5), resolved.start().orElseThrow());
        assertEquals(new ScoreAnchor(5, 0), resolved.end().orElseThrow());
        assertEquals(ScoreExpressiveEvent.Scope.VOICE, resolved.scope());
    }

    @Test
    public void differentChordReleaseTimesRemainUnresolved() throws Exception {
        var score = detected(true, true);
        var notes = new ArrayList<>(score.notes());
        notes.set(5, note(.7805f, 1, -4, 274, 2));
        var mixed =
                new ScorePageInterpretation(
                        score.measures(),
                        notes,
                        1,
                        List.of(),
                        List.of(),
                        score.meterChanges(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        score.expressiveEvents());
        var event = resolve(mixed, 3).expressiveEvents().get(1);
        assertEquals(ScoreExpressiveEvent.Scope.UNRESOLVED, event.scope());
        assertTrue(event.start().isEmpty());
        assertTrue(event.end().isEmpty());
    }

    @Test
    public void repeatedResolutionRetainsOrderedChordTargetsAndCaller() throws Exception {
        var score = detected(false, true);
        var originalNotes = List.copyOf(score.notes());
        var originalEvents = List.copyOf(score.expressiveEvents());
        var resolved = resolve(score, 3);
        assertEquals(
                List.of(1),
                ScoreFermataDetector.targetIndices(resolved, resolved.expressiveEvents().get(0)));
        assertEquals(
                List.of(3, 5),
                ScoreFermataDetector.targetIndices(resolved, resolved.expressiveEvents().get(1)));
        assertEquals(
                new ScoreAnchor(0, 1.5), resolved.expressiveEvents().get(1).start().orElseThrow());
        assertEquals(new ScoreAnchor(1, 0), resolved.expressiveEvents().get(1).end().orElseThrow());
        assertEquals(resolved, resolve(resolved, 3));
        assertEquals(originalNotes, score.notes());
        assertEquals(originalEvents, score.expressiveEvents());
    }
}
