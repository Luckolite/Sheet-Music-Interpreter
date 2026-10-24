// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Printed expressive semantics and evidence, separate from versioned playback realization. */
public record ScoreExpressiveEvent(
        String eventId,
        Kind kind,
        Optional<ScoreAnchor> start,
        Optional<ScoreAnchor> end,
        Scope scope,
        int staffIndex,
        int staffCount,
        Optional<String> targetEventId,
        Strength strength,
        String qualifierText,
        List<Evidence> evidence) {
    public enum Kind {
        UNRESOLVED_DIRECTION,
        RITARDANDO,
        RALLENTANDO,
        RITENUTO,
        ACCELERANDO,
        A_TEMPO,
        TEMPO_PRIMO,
        SAME_TEMPO,
        FERMATA,
        BREATH,
        CAESURA,
        SFORZANDO,
        SFORZATO,
        SFORZANDO_PIANO,
        PEDAL_DOWN,
        PEDAL_UP,
        ARPEGGIO,
        CRESCENDO,
        DIMINUENDO,
        METRIC_MODULATION,
        NATURAL_HARMONIC,
        FALL_OFF,
        PARENTHESIZED_NOTE_GROUP
    }

    public enum Scope {
        SCORE,
        PART,
        VOICE,
        NOTE,
        REST,
        UNRESOLVED
    }

    public enum Strength {
        UNSPECIFIED,
        POCO,
        MOLTO
    }

    public record Evidence(
            String sourceId,
            int pageIndex,
            float visualX,
            int staffIndex,
            int staffCount,
            String printedText) {
        public Evidence {
            Objects.requireNonNull(sourceId);
            Objects.requireNonNull(printedText);
            if (sourceId.isBlank()
                    || pageIndex < 0
                    || !Float.isFinite(visualX)
                    || visualX < 0
                    || visualX > 1
                    || staffCount < 1
                    || staffIndex < 0
                    || staffIndex >= staffCount)
                throw new IllegalArgumentException("Invalid expressive evidence");
        }
    }

    public ScoreExpressiveEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(targetEventId);
        Objects.requireNonNull(strength);
        Objects.requireNonNull(qualifierText);
        evidence = List.copyOf(evidence);
        if (eventId.isBlank()
                || evidence.isEmpty()
                || staffCount < 1
                || staffIndex < 0
                || staffIndex >= staffCount
                || targetEventId.filter(target -> target.isBlank()).isPresent())
            throw new IllegalArgumentException("Invalid expressive event identity or ownership");
        if (end.isPresent() && (!start.isPresent() || end.get().compareTo(start.get()) < 0))
            throw new IllegalArgumentException("Invalid expressive span");
        if (scope != Scope.UNRESOLVED && !start.isPresent())
            throw new IllegalArgumentException("Resolved scope needs a musical anchor");
        if (kind == Kind.METRIC_MODULATION && MetricModulationText.decode(qualifierText).isEmpty())
            throw new IllegalArgumentException("Metric modulation needs a validated pulse pair");
        if (kind == Kind.NATURAL_HARMONIC
                && ((scope != Scope.NOTE && scope != Scope.UNRESOLVED)
                        || (scope == Scope.NOTE && start.isEmpty())
                        || (scope == Scope.UNRESOLVED && start.isPresent())
                        || targetEventId.isEmpty()
                        || end.isPresent()))
            throw new IllegalArgumentException("Natural harmonic needs one exact note owner");
        if (kind == Kind.FERMATA
                && scope != Scope.UNRESOLVED
                && (!end.isPresent() || !targetEventId.isPresent()))
            throw new IllegalArgumentException("Resolved fermata needs its held event and release");
        if (kind == Kind.FALL_OFF
                && ((scope != Scope.NOTE && scope != Scope.UNRESOLVED)
                        || (scope == Scope.NOTE && start.isEmpty())
                        || (scope == Scope.UNRESOLVED && start.isPresent())
                        || targetEventId.isEmpty()
                        || end.isPresent()
                        || strength != Strength.UNSPECIFIED
                        || !ScoreFallOffNotation.CURVED.equals(qualifierText)
                        || ScoreFallOffNotation.targetMeasure(targetEventId.orElse(null))
                                .isEmpty()))
            throw new IllegalArgumentException(
                    "Falloff needs one exact note owner and printed shape");
        if (kind == Kind.PARENTHESIZED_NOTE_GROUP) {
            var pair =
                    ScoreParenthesizedNoteGroup.decode(qualifierText)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Invalid parenthesized group"));
            if ((scope != Scope.NOTE && scope != Scope.UNRESOLVED)
                    || (scope == Scope.NOTE && start.isEmpty())
                    || (scope == Scope.UNRESOLVED && start.isPresent())
                    || end.isPresent()
                    || strength != Strength.UNSPECIFIED
                    || !targetEventId.orElse("").equals(pair.target())
                    || pair.members().get(0).staff() != staffIndex
                    || pair.members().get(0).count() != staffCount
                    || start.filter(a -> a.measureIndex() != pair.members().get(0).measure())
                            .isPresent())
                throw new IllegalArgumentException(
                        "Group needs its exact ordered members and printed pair");
        }
    }

    /** Source identity/evidence survive concatenation; traversal IDs are assigned by projection. */
    public ScoreExpressiveEvent offset(int measures) {
        return new ScoreExpressiveEvent(
                eventId,
                kind,
                start.map(a -> a.offset(measures)),
                end.map(a -> a.offset(measures)),
                scope,
                staffIndex,
                staffCount,
                kind == Kind.NATURAL_HARMONIC
                        ? targetEventId.map(
                                target ->
                                        ScoreNaturalHarmonicNotation.offsetTarget(target, measures))
                        : kind == Kind.FALL_OFF
                                ? targetEventId.map(
                                        target ->
                                                ScoreFallOffNotation.offsetTarget(target, measures))
                                : kind == Kind.PARENTHESIZED_NOTE_GROUP
                                        ? targetEventId.map(
                                                target ->
                                                        ScoreParenthesizedNoteGroup.offsetTarget(
                                                                target, measures))
                                        : targetEventId,
                strength,
                kind == Kind.PARENTHESIZED_NOTE_GROUP
                        ? ScoreParenthesizedNoteGroup.offsetQualifier(qualifierText, measures)
                        : qualifierText,
                evidence);
    }
}
