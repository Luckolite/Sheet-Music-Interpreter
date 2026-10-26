// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;
import static io.github.luckolite.interpreter.ScorePerformanceTimeline.*;

public final class ScoreExpressivePerformanceTest {
    private static final ScoreMeterMap METER = new ScoreMeterMap(4, List.of());

    private static ScoreExpressiveEvent event(
            String id, Kind kind, double start, Double end, Scope scope, int staff) {
        return new ScoreExpressiveEvent(
                id,
                kind,
                Optional.of(new ScoreAnchor((int) (start / 4), start % 4)),
                end == null
                        ? Optional.empty()
                        : Optional.of(new ScoreAnchor((int) (end / 4), end % 4)),
                scope,
                staff,
                2,
                kind == Kind.FERMATA ? Optional.of("source-" + id) : Optional.empty(),
                Strength.UNSPECIFIED,
                "",
                List.of(new Evidence("original-synthetic", 0, .5f, staff, 2, kind.name())));
    }

    private static ScoreExpressivePerformance.Result resolve(
            List<ScoreExpressiveEvent> expressions,
            List<ScoreExpressivePerformance.Sound> sounds,
            Map<String, Set<String>> targets) {
        return ScoreExpressivePerformance.resolve(
                120,
                METER,
                3,
                List.of(),
                expressions,
                sounds,
                targets,
                ScoreExpressivePerformance.Policy.preview());
    }

    @Test
    public void gradualSlowingIntegratesReciprocalTempoAndRestores() {
        var result =
                resolve(
                        List.of(
                                event("rit", Kind.RITARDANDO, 0, 4., Scope.SCORE, 0),
                                event("restore", Kind.A_TEMPO, 8, null, Scope.SCORE, 0)),
                        List.of(),
                        Map.of());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(10 * Math.log(1.25), result.timeline().activeSecondsAtBeat(4), 1e-10);
        assertEquals(
                10 * Math.log(1.25) + 2.5 + 2, result.timeline().activeSecondsAtBeat(12), 1e-10);
        assertEquals(
                6, result.timeline().positionAtSeconds(10 * Math.log(1.25) + 1.25).beat(), 1e-8);
    }

    @Test
    public void ritenutoIsImmediateAndSameTempoDoesNotRestore() {
        var result =
                resolve(
                        List.of(
                                event("rite", Kind.RITENUTO, 4, null, Scope.SCORE, 0),
                                event("same", Kind.SAME_TEMPO, 8, null, Scope.SCORE, 0)),
                        List.of(),
                        Map.of());
        assertEquals(7, result.timeline().activeSecondsAtBeat(12), 1e-9);
    }

    @Test
    public void repeatedStaffDepictionsDoNotCompoundSlowing() {
        var one = event("rit-top", Kind.RITARDANDO, 0, 4., Scope.PART, 0);
        var two = event("rit-bottom", Kind.RITARDANDO, 0, 4., Scope.PART, 1);
        assertEquals(
                resolve(List.of(one), List.of(), Map.of()).timeline().tempoSegments(),
                resolve(List.of(one, two), List.of(), Map.of()).timeline().tempoSegments());
    }

    @Test
    public void noteAndRestFermatasShareOnePauseWithoutAttachingRestToNote() {
        var note = new ScoreExpressivePerformance.Sound("n", 0, 4, 0, 2);
        var result =
                resolve(
                        List.of(
                                event("note", Kind.FERMATA, 0, 4., Scope.NOTE, 0),
                                event("rest", Kind.FERMATA, 0, 4., Scope.REST, 1)),
                        List.of(note),
                        Map.of("note", Set.of("n")));
        assertEquals(1, result.timeline().holds().size());
        assertEquals(2, result.timeline().holds().get(0).seconds(), 0);
        assertEquals(Set.of("n"), result.timeline().holds().get(0).sustainedTargets());
        assertEquals(8, result.timeline().secondsAtBeat(12, Boundary.AFTER), 0);
        assertTrue(result.timeline().positionAtSeconds(3).holdId().isPresent());
        assertEquals(Boundary.BEFORE, result.holdOwnership().values().iterator().next());
    }

    @Test
    public void breathPausesFollowingAttackButReleasesPrecedingNote() {
        var result =
                resolve(
                        List.of(event("breath", Kind.BREATH, 4, null, Scope.PART, 0)),
                        List.of(
                                new ScoreExpressivePerformance.Sound("before", 0, 4, 0, 2),
                                new ScoreExpressivePerformance.Sound("next", 4, 8, 0, 2),
                                new ScoreExpressivePerformance.Sound("accompaniment", 0, 8, 1, 2)),
                        Map.of());
        assertEquals(.125, result.timeline().holds().get(0).seconds(), 0);
        assertEquals(Set.of("accompaniment"), result.timeline().holds().get(0).sustainedTargets());
        assertEquals(2, result.timeline().secondsAtBeat(4, Boundary.BEFORE), 0);
        assertEquals(2.125, result.timeline().secondsAtBeat(4, Boundary.AFTER), 0);
    }

    @Test
    public void terminalHoldRemainsInDuration() {
        var result =
                resolve(
                        List.of(event("final", Kind.FERMATA, 8, 12., Scope.NOTE, 0)),
                        List.of(new ScoreExpressivePerformance.Sound("n", 8, 12, 0, 2)),
                        Map.of("final", Set.of("n")));
        assertEquals(8, result.timeline().secondsAtBeat(12, Boundary.AFTER), 0);
    }

    @Test
    public void missingFermataBindingIsExplicitAndDoesNotInventSound() {
        var result =
                resolve(
                        List.of(event("hold", Kind.FERMATA, 0, 4., Scope.NOTE, 0)),
                        List.of(),
                        Map.of());
        assertTrue(result.timeline().holds().isEmpty());
        assertEquals(1, result.diagnostics().size());
    }

    @Test
    public void sforzandoIsOwnedShortAttackAndSfpSettlesQuietly() {
        var sounds = List.of(new ScoreExpressivePerformance.Sound("n", 0, 4, 0, 2));
        var sf =
                resolve(
                                List.of(event("sf", Kind.SFORZANDO, 0, null, Scope.NOTE, 0)),
                                sounds,
                                Map.of())
                        .attacks()
                        .get(0);
        assertEquals(1.6, sf.gainAtAge(0), 0);
        assertEquals(1, sf.gainAtAge(.12), 1e-9);
        var sfp =
                resolve(
                                List.of(event("sfp", Kind.SFORZANDO_PIANO, 0, null, Scope.NOTE, 0)),
                                sounds,
                                Map.of())
                        .attacks()
                        .get(0);
        assertEquals(.55, sfp.gainAtAge(.12), 1e-9);
        assertTrue(
                resolve(
                                List.of(event("wrong", Kind.SFORZATO, 0, null, Scope.NOTE, 1)),
                                sounds,
                                Map.of())
                        .attacks()
                        .isEmpty());
    }

    @Test
    public void numericTempoInterruptsRampAndBecomesRestorationTempo() {
        var result =
                ScoreExpressivePerformance.resolve(
                        120,
                        METER,
                        3,
                        List.of(new ScoreTempoChange(0, .5f, 100)),
                        List.of(
                                event("rit", Kind.RITARDANDO, 0, 4., Scope.SCORE, 0),
                                event("rite", Kind.RITENUTO, 4, null, Scope.SCORE, 0),
                                event("restore", Kind.A_TEMPO, 8, null, Scope.SCORE, 0)),
                        List.of(),
                        Map.of(),
                        ScoreExpressivePerformance.Policy.preview());
        assertEquals(
                2 * 60. / 80,
                result.timeline().activeSecondsAtBeat(6) - result.timeline().activeSecondsAtBeat(4),
                1e-9);
        assertEquals(
                2 * 60. / 100,
                result.timeline().activeSecondsAtBeat(10)
                        - result.timeline().activeSecondsAtBeat(8),
                1e-9);
    }

    @Test
    public void noExpressionExactlyPreservesNumericTimeline() {
        var changes = List.of(new ScoreTempoChange(1, 0, 90));
        var result =
                ScoreExpressivePerformance.resolve(
                        120,
                        METER,
                        3,
                        changes,
                        List.of(),
                        List.of(),
                        Map.of(),
                        ScoreExpressivePerformance.Policy.preview());
        assertEquals(
                ScorePerformanceTimeline.numeric(120, METER, changes).tempoSegments(),
                result.timeline().tempoSegments());
    }

    @Test
    public void negatedRecognizedAndLegacyTempoDirectionsDoNotSlowPreview() {
        for (String text :
                List.of("senza rall.", "no rit.", "non ritardando", "without rallentando")) {
            var direction = ExpressiveDirectionText.parse(text).get(0);
            var parsed = event("parsed", direction.kind(), 0, null, Scope.SCORE, 0);
            var actual =
                    new ScoreExpressiveEvent(
                            parsed.eventId(),
                            parsed.kind(),
                            parsed.start(),
                            parsed.end(),
                            parsed.scope(),
                            parsed.staffIndex(),
                            parsed.staffCount(),
                            parsed.targetEventId(),
                            direction.strength(),
                            direction.qualifierText(),
                            parsed.evidence());
            assertEquals(
                    text,
                    4,
                    resolve(List.of(actual), List.of(), Map.of()).timeline().activeSecondsAtBeat(8),
                    0);
        }
        var raw = event("legacy", Kind.RALLENTANDO, 0, null, Scope.SCORE, 0);
        var legacy =
                new ScoreExpressiveEvent(
                        raw.eventId(),
                        raw.kind(),
                        raw.start(),
                        raw.end(),
                        raw.scope(),
                        raw.staffIndex(),
                        raw.staffCount(),
                        raw.targetEventId(),
                        raw.strength(),
                        "senza",
                        raw.evidence());
        var result = resolve(List.of(legacy), List.of(), Map.of());
        assertEquals(4, result.timeline().activeSecondsAtBeat(8), 0);
        assertTrue(
                result.diagnostics().stream().anyMatch(value -> value.contains("negated tempo")));
        assertTrue(
                resolve(List.of(raw), List.of(), Map.of()).timeline().activeSecondsAtBeat(8) > 4);
    }

    @Test
    public void cachedFullEvidenceNegatesOnlyItsOwnTempoAction() {
        for (String text :
                List.of("senza rall.", "senza molto rall.", "no poco rall.", "non rallentando")) {
            var result =
                    resolve(List.of(literalTempo(Kind.RALLENTANDO, text, "")), List.of(), Map.of());
            assertEquals(text, 4, result.timeline().activeSecondsAtBeat(8), 0);
            assertTrue(
                    result.diagnostics().stream()
                            .anyMatch(value -> value.contains("negated tempo")));
        }
        var acceleration =
                resolve(
                        List.of(literalTempo(Kind.ACCELERANDO, "not accelerando", "")),
                        List.of(),
                        Map.of());
        assertEquals(4, acceleration.timeline().activeSecondsAtBeat(8), 0);
    }

    @Test
    public void cachedPedalArticulationAndNonTroppoQualifiersKeepPositiveTempo() {
        String[][] cases = {
            {"rall. senza pedale", "senza pedale"},
            {"non legato, rallentando", "non legato,"},
            {"rall. non troppo", "non troppo"},
            {"rall. non legato", "non legato"},
            {"rall. senza accelerando", "senza accelerando"}
        };
        for (String[] value : cases) {
            var result =
                    resolve(
                            List.of(literalTempo(Kind.RALLENTANDO, value[0], value[1])),
                            List.of(),
                            Map.of());
            assertTrue(value[0], result.timeline().activeSecondsAtBeat(8) > 4);
            assertTrue(
                    value[0],
                    result.diagnostics().stream()
                            .noneMatch(message -> message.contains("negated tempo")));
        }
        var result =
                resolve(
                        List.of(literalTempo(Kind.RITARDANDO, "rit. non troppo", "non troppo")),
                        List.of(),
                        Map.of());
        assertTrue(result.timeline().activeSecondsAtBeat(8) > 4);
    }

    private static ScoreExpressiveEvent literalTempo(Kind kind, String printed, String qualifier) {
        var original = event("literal", kind, 0, null, Scope.SCORE, 0);
        return new ScoreExpressiveEvent(
                original.eventId(),
                original.kind(),
                original.start(),
                original.end(),
                original.scope(),
                original.staffIndex(),
                original.staffCount(),
                original.targetEventId(),
                original.strength(),
                qualifier,
                List.of(new Evidence("original-literal", 0, .5f, 0, 2, printed)));
    }

    @Test
    public void conflictingSimultaneousDirectionsRemainExplicit() {
        var result =
                resolve(
                        List.of(
                                event("gradual", Kind.RITARDANDO, 0, 4., Scope.SCORE, 0),
                                event("immediate", Kind.RITENUTO, 0, null, Scope.SCORE, 1)),
                        List.of(),
                        Map.of());
        assertEquals(6, result.timeline().activeSecondsAtBeat(12), 0);
        assertEquals(2, result.diagnostics().size());
    }

    @Test
    public void punctuationDoesNotTurnRestorationIntoAnUnknownQualifier() {
        var plain = event("restore", Kind.A_TEMPO, 4, null, Scope.SCORE, 0);
        var punctuated =
                new ScoreExpressiveEvent(
                        plain.eventId(),
                        plain.kind(),
                        plain.start(),
                        plain.end(),
                        plain.scope(),
                        plain.staffIndex(),
                        plain.staffCount(),
                        plain.targetEventId(),
                        plain.strength(),
                        ".",
                        plain.evidence());
        var result =
                resolve(
                        List.of(event("rit", Kind.RITARDANDO, 0, 4., Scope.SCORE, 0), punctuated),
                        List.of(),
                        Map.of());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(
                4,
                result.timeline().activeSecondsAtBeat(12)
                        - result.timeline().activeSecondsAtBeat(4),
                1e-9);
    }
}
