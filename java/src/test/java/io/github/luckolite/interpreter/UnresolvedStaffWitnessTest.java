// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original paired-staff repeat rasters; no private score geometry or pixels. */
public class UnresolvedStaffWitnessTest {
    final int w = 600, h = 400;
    final List<MeasureRegion> measures =
            List.of(
                    new MeasureRegion(.15f, .49f, .15f, .82f),
                    new MeasureRegion(.51f, .92f, .15f, .82f));
    final List<PlayingTechniqueDetector.Staff> staffs =
            List.of(
                    new PlayingTechniqueDetector.Staff(80, 128, 12, 0, 2),
                    new PlayingTechniqueDetector.Staff(240, 288, 12, 1, 2));

    void rect(byte[] g, int x, int y, int ww, int hh) {
        for (int yy = y; yy < y + hh; yy++) for (int xx = x; xx < x + ww; xx++) g[yy * w + xx] = 0;
    }

    void dot(byte[] g, int x, int y) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                if (dx * dx + dy * dy <= 5) g[(y + dy) * w + x + dx] = 0;
    }

    byte[] page(boolean outgoing) {
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int top : new int[] {80, 240}) {
            for (int line = 0; line < 5; line++) rect(g, 40, top + line * 12, 520, 1);
            rect(g, outgoing ? 546 : 82, top, 2, 49);
            rect(g, outgoing ? 555 : 70, top, 6, 49);
            dot(g, outgoing ? 534 : 94, top + 18);
            dot(g, outgoing ? 534 : 94, top + 30);
        }
        return g;
    }

    ScorePageInterpretation apply(ScorePageInterpretation score, boolean outgoing) {
        return PrintedRepeatNavigation.apply(score, List.of(), staffs, page(outgoing), w, h, 2);
    }

    void check(boolean outgoing) {
        assertEquals(2, PrintedRepeatGlyphs.detect(page(outgoing), w, h, staffs, measures).size());
        var score = apply(new ScorePageInterpretation(measures, List.of(), 1), outgoing);
        assertTrue(score.playbackDirections().isEmpty());
        assertEquals(1, score.expressiveEvents().size());
        var e = score.expressiveEvents().get(0);
        assertEquals(ScoreExpressiveEvent.Scope.UNRESOLVED, e.scope());
        assertEquals(Optional.of(new ScoreAnchor(outgoing ? 2 : 0, 0)), e.start());
        assertEquals(2, e.evidence().size());
        assertEquals(
                Set.of(0, 1),
                new HashSet<>(
                        e.evidence().stream()
                                .map(ScoreExpressiveEvent.Evidence::staffIndex)
                                .toList()));
    }

    @Test
    public void pairedOpeningRetainsBothWitnesses() {
        check(false);
    }

    @Test
    public void pairedClosingRetainsBothWitnesses() {
        check(true);
    }

    @Test
    public void repeatedApplicationDoesNotDuplicateEvidence() {
        var a = apply(new ScorePageInterpretation(measures, List.of(), 1), false);
        assertEquals(a, apply(a, false));
    }

    ScoreExpressiveEvent event(String id, int staff, String text) {
        return new ScoreExpressiveEvent(
                id,
                ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                Optional.empty(),
                Optional.empty(),
                ScoreExpressiveEvent.Scope.UNRESOLVED,
                staff,
                2,
                Optional.empty(),
                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                text,
                List.of(new ScoreExpressiveEvent.Evidence(id, 2, .5f, staff, 2, text)));
    }

    @Test
    public void endingWitnessesCombineWithoutResolving() {
        var events = new ArrayList<ScoreExpressiveEvent>();
        PrintedRepeatNavigation.addUnresolved(events, event("ending", 0, "1."));
        PrintedRepeatNavigation.addUnresolved(events, event("ending", 1, "1."));
        assertEquals(1, events.size());
        assertEquals(2, events.get(0).evidence().size());
        assertFalse(events.get(0).start().isPresent());
    }

    @Test
    public void distinctSignsRemainSeparate() {
        var events = new ArrayList<ScoreExpressiveEvent>();
        PrintedRepeatNavigation.addUnresolved(events, event("a", 0, "1."));
        PrintedRepeatNavigation.addUnresolved(events, event("b", 1, "2."));
        assertEquals(2, events.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void conflictingIdentityStillFails() {
        var events = new ArrayList<ScoreExpressiveEvent>();
        PrintedRepeatNavigation.addUnresolved(events, event("a", 0, "1."));
        PrintedRepeatNavigation.addUnresolved(events, event("a", 1, "2."));
    }
}
