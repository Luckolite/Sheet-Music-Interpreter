// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

public class RestExpressionOwnershipTest {
    static final int W = 500, H = 300;

    static byte[] rails() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 460; x++) g[y * W + x] = 0;
        return g;
    }

    static ScoreRestEvent full() {
        return ScoreRestEvent.fullMeasure(0, .5f, 116f / H, 8f / H, 0, 1);
    }

    static ScorePageInterpretation page(List<ScoreRestEvent> rests, boolean meter) {
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.04f, .92f, .25f, .65f)),
                List.of(),
                1,
                List.of(),
                List.of(),
                meter ? List.of(new ScoreMeterChange(0, 3, 4)) : List.of(),
                rests,
                List.of(),
                List.of());
    }

    static ScorePageInterpretation detect(ScorePageInterpretation p, String word, byte[] g) {
        return ScoreExpressionDetector.apply(
                p,
                List.of(new PlayingTechniqueDetector.Word(word, .42f, 60f / H, .49f, 80f / H)),
                List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                g,
                W,
                H);
    }

    @Test
    public void fullRestRitardandoUsesMeasureStart() {
        var p = page(List.of(full()), true);
        var r = detect(p, "rit.", rails());
        assertEquals(1, r.expressiveEvents().size());
        assertEquals(new ScoreAnchor(0, 0), r.expressiveEvents().get(0).start().orElseThrow());
        assertEquals(p.rests(), r.rests());
        assertEquals(p.notes(), r.notes());
    }

    @Test
    public void unknownMeterRetainsDirectionThenResolves() {
        var r = detect(page(List.of(full()), false), "rall.", rails());
        assertEquals(Scope.UNRESOLVED, r.expressiveEvents().get(0).scope());
        assertEquals(
                new ScoreAnchor(0, 0),
                ScoreExpressionDetector.resolve(r, 3)
                        .expressiveEvents()
                        .get(0)
                        .start()
                        .orElseThrow());
    }

    @Test
    public void missingPrintedRailsRejectsOwnership() {
        byte[] g = rails();
        Arrays.fill(g, (byte) 255);
        assertTrue(detect(page(List.of(full()), true), "rit.", g).expressiveEvents().isEmpty());
    }

    @Test
    public void partialRailsRejectOwnership() {
        byte[] g = rails();
        for (int x = 20; x < 460; x++) g[132 * W + x] = (byte) 255;
        assertTrue(detect(page(List.of(full()), true), "rit.", g).expressiveEvents().isEmpty());
    }

    @Test
    public void attacksDoNotAttachToRests() {
        assertTrue(
                detect(page(List.of(full()), true), "sfz", rails()).expressiveEvents().isEmpty());
    }

    @Test
    public void literalWholeInThreeFourRemainsUnresolved() {
        var rest = new ScoreRestEvent(0, .5f, 116f / H, 8f / H, 0, 1, 4);
        var r = detect(page(List.of(rest), true), "rit.", rails());
        assertEquals(1, r.expressiveEvents().size());
        assertTrue(r.expressiveEvents().get(0).start().isEmpty());
    }

    @Test
    public void exactlyAccountedLiteralRestResolves() {
        var rest = new ScoreRestEvent(0, .5f, 116f / H, 8f / H, 0, 1, 3);
        var r = detect(page(List.of(rest), true), "rall.", rails());
        assertEquals(new ScoreAnchor(0, 0), r.expressiveEvents().get(0).start().orElseThrow());
    }

    @Test
    public void underfilledLiteralRestDoesNotInventOnset() {
        var rest = new ScoreRestEvent(0, .5f, 116f / H, 8f / H, 0, 1, 1);
        var r = detect(page(List.of(rest), true), "rit.", rails());
        assertTrue(r.expressiveEvents().get(0).start().isEmpty());
    }

    @Test
    public void offsetKeepsRestTargetAndSourcePage() {
        var e = detect(page(List.of(full()), false), "rit.", rails()).expressiveEvents().get(0);
        var shifted = ScoreDynamicContinuation.offsetEvidence(e, 7, 2);
        assertTrue(shifted.targetEventId().orElseThrow().startsWith("expression-rest-column:7:"));
        assertEquals(2, shifted.evidence().get(0).pageIndex());
        assertTrue(ScoreExpressionDetector.owns(shifted));
    }

    @Test
    public void restTargetsDoNotBecomeNoteTargets() {
        var p = detect(page(List.of(full()), true), "rit.", rails());
        assertTrue(ScoreExpressionDetector.targetIndices(p, p.expressiveEvents().get(0)).isEmpty());
    }

    @Test
    public void duplicatePhysicalRestOwnersRejectAmbiguity() {
        assertTrue(
                detect(page(List.of(full(), full()), true), "rit.", rails())
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void inconsistentFullRestAndNoteCannotProveSilence() {
        var p = page(List.of(full()), true);
        var n = new ScoreNoteEvent(0, .8f, 0, 0, 1, 164f / H, false, 0, 0, 0, 1, 1, 0);
        p =
                new ScorePageInterpretation(
                        p.measures(),
                        List.of(n),
                        p.firstMeasureNumber(),
                        p.keyChanges(),
                        p.tempoChanges(),
                        p.meterChanges(),
                        p.rests(),
                        p.techniqueChanges(),
                        p.dynamicChanges());
        assertTrue(Double.isNaN(PrintedRestDirection.onset(p, 0, 0, 1, .5f, 3)));
    }
}
