// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural ink and authored note columns; contains no score scans. */
public class ScoreArpeggioTest {
    final int W = 260, H = 250;
    final float GAP = 12;

    byte[] paper() {
        var b = new byte[W * H];
        Arrays.fill(b, (byte) 255);
        return b;
    }

    void dot(byte[] b, int x, int y) {
        if (x >= 0 && x < W && y >= 0 && y < H) b[y * W + x] = 0;
    }

    void line(byte[] b, int x1, int y1, int x2, int y2) {
        int length = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= length; i++) {
            double f = length == 0 ? 0 : (double) i / length;
            int x = (int) Math.round(x1 + (x2 - x1) * f), y = (int) Math.round(y1 + (y2 - y1) * f);
            dot(b, x, y);
            dot(b, x + 1, y);
        }
    }

    void wave(byte[] b) {
        for (int y = 50; y < 110; y++) {
            double phase = ((y - 50) % 12) / 12.;
            int x = 60 + (int) Math.round(phase < .5 ? phase * 12 : (1 - phase) * 12);
            line(b, x, y, x, y + 1);
        }
    }

    ScorePageInterpretation page() {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int step : new int[] {0, 3, 7})
            notes.add(
                    new ScoreNoteEvent(
                            0, .35f, step, 0, 2, (100 - step * 6f) / H, false, 0, 0, 0, 2));
        notes.add(new ScoreNoteEvent(0, .35f, 0, 1, 2, 190f / H, false, 0, 0, 0, 2));
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(0, 1, 0, 1)),
                notes,
                1,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    ScoreExpressiveEvent.Evidence evidence() {
        return new ScoreExpressiveEvent.Evidence(
                "printed-arpeggio-wave", 8, .24f, 0, 2, "arpeggio");
    }

    ScorePageInterpretation captured() {
        return ScoreArpeggioNotation.capture(page(), List.of(0, 1, 2), evidence());
    }

    @Test
    public void allChordMembersRetainedWithoutPerformanceSpread() {
        var before = page();
        var after = captured();
        assertEquals(before.notes(), after.notes());
        assertEquals(1, after.expressiveEvents().size());
        var event = after.expressiveEvents().get(0);
        assertEquals(ScoreExpressiveEvent.Scope.UNRESOLVED, event.scope());
        assertTrue(event.start().isEmpty());
        assertEquals("", event.qualifierText());
        var plan = ScoreArpeggioNotation.plan(after, List.of(0, 1, 2, 3), 4);
        assertEquals(3, plan.size());
        assertEquals(
                Set.of(0, 1, 2),
                new HashSet<>(
                        plan.stream().map(ScoreArpeggioNotation.Member::sourceIndex).toList()));
        assertEquals(1, plan.stream().map(ScoreArpeggioNotation.Member::attack).distinct().count());
    }

    @Test
    public void partialChordRejected() {
        assertTrue(
                ScoreArpeggioNotation.capture(page(), List.of(0, 1), evidence())
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void adjacentStaffCannotBeClaimed() {
        assertTrue(
                ScoreArpeggioNotation.capture(page(), List.of(0, 1, 2, 3), evidence())
                        .expressiveEvents()
                        .isEmpty());
        assertTrue(ScoreArpeggioNotation.plan(captured(), List.of(3), 4).isEmpty());
    }

    @Test
    public void unknownMeterDoesNotInventClock() {
        assertTrue(ScoreArpeggioNotation.plan(captured(), List.of(0, 1, 2), Float.NaN).isEmpty());
    }

    @Test
    public void duplicateDetectionIsIdempotent() {
        var a = captured();
        assertEquals(a, ScoreArpeggioNotation.capture(a, List.of(0, 1, 2), evidence()));
    }

    @Test
    public void opticalChordOwnershipAndNotationEndToEnd() {
        var b = paper();
        wave(b);
        var before = page();
        var detected =
                ScoreArpeggioDetector.apply(
                        before,
                        b,
                        W,
                        H,
                        List.of(
                                new PlayingTechniqueDetector.Staff(52, 100, 12, 0, 2),
                                new PlayingTechniqueDetector.Staff(152, 200, 12, 1, 2)),
                        8);
        assertEquals(1, detected.expressiveEvents().size());
        assertEquals(before.notes(), detected.notes());
        assertEquals(3, ScoreArpeggioNotation.plan(detected, List.of(0, 1, 2, 3), 4).size());
        var resolved = ScoreArpeggioNotation.resolve(detected, 4);
        assertEquals(ScoreExpressiveEvent.Scope.NOTE, resolved.expressiveEvents().get(0).scope());
        assertTrue(resolved.expressiveEvents().get(0).end().isEmpty());
    }

    @Test
    public void assemblyRebasesEvidenceAndColumnTogether() {
        var e = captured().expressiveEvents().get(0);
        var shifted = ScoreArpeggioNotation.offsetEvidence(e, 5, 9);
        assertEquals(9, shifted.evidence().get(0).pageIndex());
        assertTrue(shifted.targetEventId().get().startsWith("arpeggio-column-v1:5:"));
        assertEquals(e.staffIndex(), shifted.staffIndex());
    }

    @Test
    public void filteredExportCannotInventPartialChordRoll() {
        assertTrue(ScoreArpeggioNotation.plan(captured(), List.of(1, 2), 4).isEmpty());
        assertTrue(ScoreArpeggioNotation.plan(captured(), List.of(1), 4).isEmpty());
        assertEquals(3, ScoreArpeggioNotation.plan(captured(), List.of(0, 1, 2), 4).size());
    }

    @Test
    public void staffRailsThroughWaveDoNotSwallowIt() {
        var b = paper();
        wave(b);
        for (int y = 52; y <= 100; y += 12) line(b, 10, y, 220, y);
        assertTrue(ScoreArpeggioDetector.wave(b, W, H, 58, 69, 50, 109, GAP));
        var detected =
                ScoreArpeggioDetector.apply(
                        page(),
                        b,
                        W,
                        H,
                        List.of(
                                new PlayingTechniqueDetector.Staff(52, 100, 12, 0, 2),
                                new PlayingTechniqueDetector.Staff(152, 200, 12, 1, 2)),
                        8);
        assertEquals(1, detected.expressiveEvents().size());
        assertEquals(3, ScoreArpeggioNotation.plan(detected, List.of(0, 1, 2, 3), 4).size());
    }

    @Test
    public void orphanExtractedStaffCannotFallBack() {
        var a = captured();
        var onlyOther =
                page().withNotes(List.of(page().notes().get(3)), List.of(3))
                        .withExpressiveEvents(a.expressiveEvents());
        assertTrue(ScoreArpeggioNotation.plan(onlyOther, List.of(0), 4).isEmpty());
    }

    @Test
    public void opticalJitterDoesNotDropChordHeads() {
        var a = page();
        var notes = new ArrayList<>(a.notes());
        notes.set(1, new ScoreNoteEvent(0, .3507f, 3, 0, 2, 82f / H, false, 0, 0, 0, 2));
        notes.set(2, new ScoreNoteEvent(0, .3511f, 7, 0, 2, 58f / H, false, 0, 0, 0, 2));
        a = a.withNotes(notes, List.of(0, 1, 2, 3));
        var b = ScoreArpeggioNotation.capture(a, List.of(0, 1, 2), evidence());
        assertEquals(1, b.expressiveEvents().size());
        assertEquals(3, ScoreArpeggioNotation.plan(b, List.of(0, 1, 2, 3), 4).size());
        assertEquals(notes, b.notes());
        var reordered = new ArrayList<>(b.notes());
        Collections.swap(reordered, 0, 2);
        b = b.withNotes(reordered, List.of(2, 1, 0, 3));
        b = ScoreArpeggioNotation.capture(b, List.of(0, 1, 2), evidence());
        assertEquals(1, b.expressiveEvents().size());
        assertEquals(3, ScoreArpeggioNotation.plan(b, List.of(0, 1, 2, 3), 4).size());
    }

    @Test
    public void reorderedCaptureDoesNotDuplicateMembership() {
        var a = page();
        var changed = new ArrayList<>(a.notes());
        Collections.swap(changed, 0, 2);
        a = a.withNotes(changed, List.of(2, 1, 0, 3));
        var first = ScoreArpeggioNotation.capture(a, List.of(0, 1, 2), evidence());
        var second = ScoreArpeggioNotation.capture(first, List.of(2, 1, 0), evidence());
        assertEquals(1, second.expressiveEvents().size());
        assertEquals(3, ScoreArpeggioNotation.plan(second, List.of(0, 1, 2, 3), 4).size());
    }

    @Test
    public void duplicatedSourceHeadInvalidatesTarget() {
        var a = captured();
        var changed = new ArrayList<>(a.notes());
        changed.add(a.notes().get(0));
        a = a.withNotes(changed, List.of(0, 1, 2, 3, -1));
        assertTrue(ScoreArpeggioNotation.plan(a, List.of(0, 1, 2, 3, 4), 4).isEmpty());
    }

    @Test
    public void removedHeadInvalidatesCompleteColumn() {
        var a = captured();
        a =
                a.withNotes(
                        List.of(a.notes().get(0), a.notes().get(1), a.notes().get(3)),
                        List.of(0, 1, 3));
        assertTrue(ScoreArpeggioNotation.plan(a, List.of(0, 1, 2), 4).isEmpty());
    }

    @Test
    public void evidenceFromAnotherStaffCannotResolve() {
        var a = captured();
        var e = a.expressiveEvents().get(0);
        e =
                new ScoreExpressiveEvent(
                        e.eventId(),
                        e.kind(),
                        e.start(),
                        e.end(),
                        e.scope(),
                        e.staffIndex(),
                        e.staffCount(),
                        e.targetEventId(),
                        e.strength(),
                        e.qualifierText(),
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "printed-arpeggio-wave", 8, .24f, 1, 2, "arpeggio")));
        a = a.withExpressiveEvents(List.of(e));
        assertTrue(ScoreArpeggioNotation.plan(a, List.of(0, 1, 2, 3), 4).isEmpty());
    }

    @Test
    public void connectedStraightSpineWithPeriodicFlagsIsNotWave() {
        for (int period : new int[] {10, 12, 16})
            for (int maxWidth : new int[] {5, 6, 7})
                for (boolean left : new boolean[] {true, false}) {
                    var b = paper();
                    for (int y = 50; y <= 109; y++) {
                        double phase = ((y - 50) % period) / (double) period;
                        double triangle = phase < .5 ? phase * 2 : (1 - phase) * 2;
                        int width = 2 + (int) Math.round((maxWidth - 2) * triangle);
                        for (int j = 0; j < width; j++) dot(b, 62 + (left ? j : -j), y);
                    }
                    assertFalse(ScoreArpeggioDetector.wave(b, W, H, 55, 69, 50, 109, GAP));
                }
    }

    @Test
    public void undirectedWaveAccepted() {
        var b = paper();
        wave(b);
        assertTrue(ScoreArpeggioDetector.wave(b, W, H, 58, 69, 50, 109, GAP));
    }

    @Test
    public void stemCrossedWaveRejected() {
        var b = paper();
        wave(b);
        line(b, 62, 50, 62, 109);
        assertFalse(ScoreArpeggioDetector.wave(b, W, H, 58, 69, 50, 109, GAP));
    }

    @Test
    public void barlineBraceAccidentalsAndRestContoursRejected() {
        var bar = paper();
        line(bar, 62, 50, 62, 109);
        assertFalse(ScoreArpeggioDetector.wave(bar, W, H, 58, 69, 50, 109, GAP));
        var brace = paper();
        line(brace, 65, 50, 60, 79);
        line(brace, 60, 79, 65, 109);
        assertFalse(ScoreArpeggioDetector.wave(brace, W, H, 58, 69, 50, 109, GAP));
        var sharp = paper();
        line(sharp, 61, 72, 61, 96);
        line(sharp, 65, 70, 65, 94);
        line(sharp, 59, 78, 68, 76);
        line(sharp, 59, 88, 68, 86);
        assertFalse(ScoreArpeggioDetector.wave(sharp, W, H, 58, 69, 50, 109, GAP));
        var flat = paper();
        line(flat, 61, 65, 61, 97);
        line(flat, 61, 80, 66, 84);
        line(flat, 66, 84, 61, 95);
        assertFalse(ScoreArpeggioDetector.wave(flat, W, H, 58, 69, 50, 109, GAP));
        var rest = paper();
        line(rest, 63, 70, 67, 78);
        line(rest, 67, 78, 60, 86);
        line(rest, 60, 86, 65, 94);
        assertFalse(ScoreArpeggioDetector.wave(rest, W, H, 58, 69, 50, 109, GAP));
        var eighth = paper();
        line(eighth, 65, 76, 61, 95);
        line(eighth, 61, 80, 66, 76);
        assertFalse(ScoreArpeggioDetector.wave(eighth, W, H, 58, 69, 50, 109, GAP));
    }
}
