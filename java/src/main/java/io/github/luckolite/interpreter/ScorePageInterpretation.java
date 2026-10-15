// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Cacheable geometry, logical measure start, and notes for one undecorated PDF page. */
public record ScorePageInterpretation(
        List<MeasureRegion> measures,
        List<ScoreNoteEvent> notes,
        int firstMeasureNumber,
        List<ScoreKeyChange> keyChanges,
        List<ScoreTempoChange> tempoChanges,
        List<ScoreMeterChange> meterChanges,
        List<ScoreRestEvent> rests,
        List<ScoreTechniqueChange> techniqueChanges,
        List<ScoreDynamicChange> dynamicChanges,
        List<ScorePlaybackDirection> playbackDirections,
        List<ScoreExpressiveEvent> expressiveEvents,
        ScoreSlurSource slurSource,
        boolean openingPickup) {
    /** Legacy callers do not carry independent opening-number proof. */
    public ScorePageInterpretation(
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int first,
            List<ScoreKeyChange> keys,
            List<ScoreTempoChange> tempos,
            List<ScoreMeterChange> meters,
            List<ScoreRestEvent> rests,
            List<ScoreTechniqueChange> techniques,
            List<ScoreDynamicChange> dynamics,
            List<ScorePlaybackDirection> directions,
            List<ScoreExpressiveEvent> expressions,
            ScoreSlurSource slurs) {
        this(
                measures,
                notes,
                first,
                keys,
                tempos,
                meters,
                rests,
                techniques,
                dynamics,
                directions,
                expressions,
                slurs,
                false);
    }

    public ScorePageInterpretation withOpeningPickup(boolean proof) {
        return new ScorePageInterpretation(
                measures,
                notes,
                firstMeasureNumber,
                keyChanges,
                tempoChanges,
                meterChanges,
                rests,
                techniqueChanges,
                dynamicChanges,
                playbackDirections,
                expressiveEvents,
                slurSource,
                proof);
    }

    public ScorePageInterpretation(
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int first,
            List<ScoreKeyChange> keys,
            List<ScoreTempoChange> tempos,
            List<ScoreMeterChange> meters,
            List<ScoreRestEvent> rests,
            List<ScoreTechniqueChange> techniques,
            List<ScoreDynamicChange> dynamics,
            List<ScorePlaybackDirection> directions,
            List<ScoreExpressiveEvent> expressions) {
        this(
                measures,
                notes,
                first,
                keys,
                tempos,
                meters,
                rests,
                techniques,
                dynamics,
                directions,
                expressions,
                ScoreSlurSource.empty(notes == null ? 0 : notes.size()));
    }

    public ScorePageInterpretation withSlurSource(ScoreSlurSource source) {
        return new ScorePageInterpretation(
                measures,
                notes,
                firstMeasureNumber,
                keyChanges,
                tempoChanges,
                meterChanges,
                rests,
                techniqueChanges,
                dynamicChanges,
                playbackDirections,
                expressiveEvents,
                source,
                openingPickup);
    }

    /** Explicit source owners preserve original printed endpoints through a note edit. */
    public ScorePageInterpretation withNotes(
            List<ScoreNoteEvent> edited, List<Integer> sourceOwners) {
        if (edited.size() != sourceOwners.size())
            throw new IllegalArgumentException("Edited notes and source owners disagree");
        return new ScorePageInterpretation(
                measures,
                edited,
                firstMeasureNumber,
                keyChanges,
                tempoChanges,
                meterChanges,
                rests,
                techniqueChanges,
                dynamicChanges,
                playbackDirections,
                expressiveEvents,
                slurSource.edited(sourceOwners),
                openingPickup);
    }

    public ScorePageInterpretation(
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int first,
            List<ScoreKeyChange> keys,
            List<ScoreTempoChange> tempos,
            List<ScoreMeterChange> meters,
            List<ScoreRestEvent> rests,
            List<ScoreTechniqueChange> techniques,
            List<ScoreDynamicChange> dynamics,
            List<ScorePlaybackDirection> directions) {
        this(
                measures,
                notes,
                first,
                keys,
                tempos,
                meters,
                rests,
                techniques,
                dynamics,
                directions,
                List.of());
    }

    public ScorePageInterpretation(
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int first,
            List<ScoreKeyChange> keys,
            List<ScoreTempoChange> tempos,
            List<ScoreMeterChange> meters,
            List<ScoreRestEvent> rests,
            List<ScoreTechniqueChange> techniques,
            List<ScoreDynamicChange> dynamics) {
        this(measures, notes, first, keys, tempos, meters, rests, techniques, dynamics, List.of());
    }

    public ScorePageInterpretation withPlaybackDirections(List<ScorePlaybackDirection> directions) {
        return new ScorePageInterpretation(
                measures,
                notes,
                firstMeasureNumber,
                keyChanges,
                tempoChanges,
                meterChanges,
                rests,
                techniqueChanges,
                dynamicChanges,
                directions,
                expressiveEvents,
                slurSource,
                openingPickup);
    }

    public ScorePageInterpretation withExpressiveEvents(List<ScoreExpressiveEvent> events) {
        return new ScorePageInterpretation(
                measures,
                notes,
                firstMeasureNumber,
                keyChanges,
                tempoChanges,
                meterChanges,
                rests,
                techniqueChanges,
                dynamicChanges,
                playbackDirections,
                events,
                slurSource,
                openingPickup);
    }

    public ScorePageInterpretation(
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int first,
            List<ScoreKeyChange> keys,
            List<ScoreTempoChange> tempos,
            List<ScoreMeterChange> meters,
            List<ScoreRestEvent> rests,
            List<ScoreTechniqueChange> techniques) {
        this(measures, notes, first, keys, tempos, meters, rests, techniques, List.of());
    }

    public ScorePageInterpretation(
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int first,
            List<ScoreKeyChange> keys,
            List<ScoreTempoChange> tempos,
            List<ScoreMeterChange> meters,
            List<ScoreRestEvent> rests) {
        this(measures, notes, first, keys, tempos, meters, rests, List.of());
    }

    public ScorePageInterpretation(
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int firstMeasureNumber,
            List<ScoreKeyChange> keys,
            List<ScoreTempoChange> tempos,
            List<ScoreMeterChange> meters) {
        this(measures, notes, firstMeasureNumber, keys, tempos, meters, List.of());
    }

    public ScorePageInterpretation(
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int firstMeasureNumber,
            List<ScoreKeyChange> keys,
            List<ScoreTempoChange> tempos) {
        this(measures, notes, firstMeasureNumber, keys, tempos, List.of());
    }

    public ScorePageInterpretation(List<MeasureRegion> measures, List<ScoreNoteEvent> notes) {
        this(measures, notes, 0, List.of(), List.of());
    }

    public ScorePageInterpretation(
            List<MeasureRegion> measures, List<ScoreNoteEvent> notes, int firstMeasureNumber) {
        this(measures, notes, firstMeasureNumber, List.of(), List.of());
    }

    public ScorePageInterpretation {
        measures = measures == null ? List.of() : List.copyOf(measures);
        notes = notes == null ? List.of() : List.copyOf(notes);
        java.util.Objects.requireNonNull(slurSource);
        if (slurSource.noteOrigins().size() != notes.size())
            throw new IllegalArgumentException("Slur identities do not match retained score notes");
        firstMeasureNumber = Math.max(0, firstMeasureNumber);
        if (pickupConflictsWithOpening(openingPickup, firstMeasureNumber, measures))
            throw new IllegalArgumentException("Pickup proof conflicts with source opening");
        keyChanges = keyChanges == null ? List.of() : List.copyOf(keyChanges);
        tempoChanges = tempoChanges == null ? List.of() : List.copyOf(tempoChanges);
        meterChanges = meterChanges == null ? List.of() : List.copyOf(meterChanges);
        rests = rests == null ? List.of() : List.copyOf(rests);
        techniqueChanges = techniqueChanges == null ? List.of() : List.copyOf(techniqueChanges);
        dynamicChanges = dynamicChanges == null ? List.of() : List.copyOf(dynamicChanges);
        playbackDirections =
                playbackDirections == null ? List.of() : List.copyOf(playbackDirections);
        expressiveEvents = expressiveEvents == null ? List.of() : List.copyOf(expressiveEvents);
        var expressiveIds = new java.util.HashSet<String>();
        for (var event : expressiveEvents)
            if (!expressiveIds.add(event.eventId()))
                throw new IllegalArgumentException("Duplicate expressive event identity");
        for (var direction : playbackDirections)
            if (direction.measureBoundary() > measures.size())
                throw new IllegalArgumentException("Playback direction is outside the page");
    }

    private static boolean pickupConflictsWithOpening(
            boolean openingPickup, int firstMeasureNumber, List<?> measures) {
        return openingPickup && (firstMeasureNumber > 1 || measures.isEmpty());
    }

    /** Each boundary belongs to the page containing the sign's incoming/outgoing bar. */
    public static List<ScorePlaybackDirection> directionsOnPage(
            List<ScorePlaybackDirection> changes, int firstMeasure, int measureAfterLast) {
        if (firstMeasure < 0 || measureAfterLast < firstMeasure)
            throw new IllegalArgumentException("Invalid page measure range");
        if (changes == null || changes.isEmpty() || firstMeasure == measureAfterLast)
            return List.of();
        return changes.stream()
                .filter(
                        change -> {
                            int boundary = change.measureBoundary();
                            boolean outgoing =
                                    change.kind().outgoing()
                                            && change.details().quarterBeatOffset() == 0;
                            return outgoing
                                    ? boundary > firstMeasure && boundary <= measureAfterLast
                                    : boundary >= firstMeasure && boundary < measureAfterLast;
                        })
                .map(change -> change.offset(-firstMeasure))
                .collect(java.util.stream.Collectors.toList());
    }

    /** Retain each expression once, preserving source identities and full cross-page endpoints. */
    public static List<ScoreExpressiveEvent> expressionsStartingOnPage(
            List<ScoreExpressiveEvent> events, int firstMeasure, int measureAfterLast) {
        return expressionsStartingOnPage(events, firstMeasure, measureAfterLast, false);
    }

    /** The final output page also owns terminal releases at the score-end anchor. */
    public static List<ScoreExpressiveEvent> expressionsStartingOnPage(
            List<ScoreExpressiveEvent> events,
            int firstMeasure,
            int measureAfterLast,
            boolean finalPage) {
        if (firstMeasure < 0 || measureAfterLast < firstMeasure)
            throw new IllegalArgumentException("Invalid page measure range");
        if (events == null || events.isEmpty() || firstMeasure == measureAfterLast)
            return List.of();
        // Anchorless evidence remains unresolved and is retained once on the first
        // output page. Its original physical-page evidence is never reinterpreted.
        return events.stream()
                .filter(
                        event ->
                                event.start().isEmpty()
                                        ? firstMeasure == 0
                                        : event.start().get().measureIndex() >= firstMeasure
                                                && (event.start().get().measureIndex()
                                                                < measureAfterLast
                                                        || finalPage
                                                                && event.start()
                                                                        .get()
                                                                        .equals(
                                                                                new ScoreAnchor(
                                                                                        measureAfterLast,
                                                                                        0))))
                .map(event -> event.offset(-firstMeasure))
                .toList();
    }

    /** Keep a cross-page hairpin once, with its full endpoint relative to its starting page. */
    public static List<ScoreDynamicChange> dynamicsStartingOnPage(
            List<ScoreDynamicChange> changes, int firstMeasure, int measureAfterLast) {
        if (firstMeasure < 0 || measureAfterLast < firstMeasure)
            throw new IllegalArgumentException("Invalid page measure range");
        if (changes == null || changes.isEmpty()) return List.of();
        return changes.stream()
                .filter(
                        change ->
                                change.measureIndex() >= firstMeasure
                                        && change.measureIndex() < measureAfterLast)
                .map(change -> change.offset(-firstMeasure))
                .collect(java.util.stream.Collectors.toList());
    }
}
