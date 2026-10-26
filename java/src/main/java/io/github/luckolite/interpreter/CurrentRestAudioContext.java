// Copyright 2026 Music Sheets contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.IOException;
import java.util.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Literal owned full-Current rest clocks. No Original, persistence, Song or audio-engine dependency. */
public final class CurrentRestAudioContext {
    private CurrentRestAudioContext() {}

    public static final int MAX_EXPRESSIONS = 262144;
    public static final long TARGET_SCAN_LIMIT = 16_777_216L;

    public record Bound(int restOwner, Scope scope, ScoreExpressiveEvent event) {}

    public record Projection(ScorePageInterpretation score, Set<Integer> currentRestOccurrences) {
        public Projection {
            currentRestOccurrences = Set.copyOf(currentRestOccurrences);
        }
    }

    /** Owner ordinals must be emitted by the actual full-Current selection iteration. */
    public record Selection(
            ScorePageInterpretation input,
            ScorePageInterpretation score,
            List<Integer> restOwners,
            List<Integer> expressionOwners) {
        public Selection {
            restOwners = List.copyOf(restOwners);
            expressionOwners = List.copyOf(expressionOwners);
            if (restOwners.size() != score.rests().size()
                    || expressionOwners.size() != score.expressiveEvents().size())
                throw new IllegalArgumentException("Incomplete owned playback projection");
        }
    }

    /** Construction is private: only validated admission creates this context. */
    public static final class Prepared {
        private final ScorePageInterpretation fullCurrent, source;
        private final ScoreBoundaryClock boundary;
        private final float nominal;
        private final ScoreMeterMap frame;
        private final Map<Integer, Bound> byExpression;
        private final long scanWork;

        private Prepared(
                ScorePageInterpretation fullCurrent,
                ScorePageInterpretation source,
                ScoreBoundaryClock boundary,
                float nominal,
                ScoreMeterMap frame,
                Map<Integer, Bound> byExpression,
                long scanWork) {
            this.fullCurrent = fullCurrent;
            this.source = source;
            this.boundary = boundary;
            this.nominal = nominal;
            this.frame = frame;
            this.byExpression = Collections.unmodifiableMap(new TreeMap<>(byExpression));
            this.scanWork = scanWork;
        }

        public ScorePageInterpretation fullCurrent() {
            return fullCurrent;
        }

        public ScorePageInterpretation source() {
            return source;
        }

        public ScoreBoundaryClock boundary() {
            return boundary;
        }

        public float nominal() {
            return nominal;
        }

        public ScoreMeterMap frame() {
            return frame;
        }

        public Map<Integer, Bound> byExpression() {
            return byExpression;
        }

        public long scanWork() {
            return scanWork;
        }
    }

    public static long checkedScanWork(long dependent, long rests) throws IOException {
        if (dependent < 0 || rests < 0)
            throw new IOException("Negative Current target scan domain");
        final long work;
        try {
            work = Math.multiplyExact(dependent, rests);
        } catch (ArithmeticException overflow) {
            throw new IOException("Current target scan overflow", overflow);
        }
        if (work > TARGET_SCAN_LIMIT) throw new IOException("Current target scan budget exceeded");
        return work;
    }

    static boolean dependent(ScoreExpressiveEvent event) {
        String target = event.targetEventId().orElse("");
        return target.startsWith("printed-rest")
                || target.startsWith("expression-rest")
                || event.scope() == Scope.REST;
    }

    static long prepay(ScorePageInterpretation current) throws IOException {
        if (current.expressiveEvents().size() > MAX_EXPRESSIONS)
            throw new IOException("Current expression namespace outside bounds");
        long dependent = 0;
        for (var event : current.expressiveEvents()) if (dependent(event)) dependent++;
        return checkedScanWork(dependent, current.rests().size());
    }

    /** Null means the exact legacy empty-declaration route; no new frame or target policy. */
    public static Prepared prepare(
            ScorePageInterpretation current,
            Map<Integer, CurrentRestWritten.Placement> declarations,
            float nominal,
            ScoreBoundaryClock captured)
            throws IOException {
        Objects.requireNonNull(declarations);
        if (declarations.isEmpty()) return null;
        long work = prepay(current);
        try {
            return prepareValidated(
                    current,
                    CurrentRestWritten.validated(declarations, current),
                    nominal,
                    captured,
                    work);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Unsupported declared Current rest audio context", invalid);
        }
    }

    // Trusted same-package adapters call this only after prepay and the exact shared row validator.
    // This lets the app compute its nominal Song scalar at its original position after validation.
    static Prepared prepareValidated(
            ScorePageInterpretation current,
            Map<Integer, CurrentRestWritten.Placement> declarations,
            float nominal,
            ScoreBoundaryClock captured,
            long work) {
        var boundary =
                captured.retained() ? captured : ScoreBoundaryClock.resolve(current, nominal);
        if (!boundary.retained())
            throw new IllegalArgumentException("Current rest clock needs an effective frame");
        var frame =
                new ScoreMeterMap(nominal, current.meterChanges())
                        .withExactBoundaryQuarterBeats(
                                boundary.openingQuarterBeats(),
                                boundary.closingQuarterBeats(),
                                current.measures().size());
        for (var entry : declarations.entrySet()) {
            var rest = current.rests().get(entry.getKey());
            var clock = entry.getValue();
            double span = frame.quarterBeatsInMeasure(rest.measureIndex()),
                    onset = clock.onsetBeats(),
                    duration = clock.durationBeats(),
                    release = onset + duration;
            if (!Double.isFinite(span)
                    || span <= 0
                    || onset > span
                    || duration > span - onset
                    || !Double.isFinite(release)
                    || release <= onset
                    || release > span)
                throw new IllegalArgumentException(
                        "Declared Current rest interval outside actual frame");
            new ScoreAnchor(rest.measureIndex(), onset).canonical(frame, current.measures().size());
            new ScoreAnchor(rest.measureIndex(), release)
                    .canonical(frame, current.measures().size());
        }
        var events = new ArrayList<ScoreExpressiveEvent>();
        var bound = new TreeMap<Integer, Bound>();
        var ids = new HashSet<String>();
        int ordinal = 0;
        for (var event : current.expressiveEvents()) {
            if (!ids.add(event.eventId()))
                throw new IllegalArgumentException("Duplicate Current expressive occurrence ID");
            if (!dependent(event)) {
                events.add(event);
                ordinal++;
                continue;
            }
            int owner;
            boolean releaseRole = false;
            Scope scope;
            if (ScoreRestFermataDetector.owns(event)) {
                var owners = ScoreRestFermataDetector.targetIndices(current, event);
                if (owners.size() != 1)
                    throw new IllegalArgumentException(
                            "Ambiguous or unresolved Current rest fermata target");
                owner = owners.get(0);
                scope = Scope.REST;
            } else {
                var target = ScoreExpressionDetector.currentRestTarget(current, event);
                if (target.isEmpty())
                    throw new IllegalArgumentException(
                            "Unsupported dependent Current rest evidence or grammar");
                owner = target.get().owner();
                releaseRole = target.get().release();
                scope = target.get().scope();
            }
            var rest = current.rests().get(owner);
            var clock = declarations.get(owner);
            if (clock == null || rest.isFullMeasure())
                throw new IllegalArgumentException(
                        "Dependent Current rest lacks declared literal OWN clock");
            double onset = clock.onsetBeats(), release = onset + clock.durationBeats();
            int measure = rest.measureIndex();
            var start =
                    new ScoreAnchor(measure, releaseRole ? release : onset)
                            .canonical(frame, current.measures().size());
            Optional<ScoreAnchor> end;
            if (event.kind() == Kind.FERMATA)
                end =
                        Optional.of(
                                new ScoreAnchor(measure, release)
                                        .canonical(frame, current.measures().size()));
            else {
                if (PrintedTempoSpans.hasOpen(event))
                    throw new IllegalArgumentException(
                            "Open dependent Current rest direction span");
                end =
                        PrintedTempoSpans.preserveEnd(
                                event,
                                start,
                                frame,
                                current.measures().size(),
                                current.tempoChanges());
                if (event.end().isPresent() && end.isEmpty())
                    throw new IllegalArgumentException(
                            "Dependent Current direction lost its declared end");
            }
            var resolved =
                    new ScoreExpressiveEvent(
                            event.eventId(),
                            event.kind(),
                            Optional.of(start),
                            end,
                            scope,
                            event.staffIndex(),
                            event.staffCount(),
                            event.targetEventId(),
                            event.strength(),
                            event.qualifierText(),
                            event.evidence());
            events.add(resolved);
            bound.put(ordinal, new Bound(owner, scope, resolved));
            ordinal++;
        }
        return new Prepared(
                current,
                current.withExpressiveEvents(events),
                boundary,
                nominal,
                frame,
                bound,
                work);
    }

    public static Projection project(Prepared prepared, Selection projection) throws IOException {
        if (projection.input() != prepared.source())
            throw new IOException("Different Current projection context");
        var score = projection.score();
        int measures = prepared.fullCurrent().measures().size();
        if (score.measures().size() != measures)
            throw new IOException("Current projection changed measure namespace");
        var meter =
                new ScoreMeterMap(prepared.nominal(), score.meterChanges())
                        .withExactBoundaryQuarterBeats(
                                prepared.boundary().openingQuarterBeats(),
                                prepared.boundary().closingQuarterBeats(),
                                measures);
        for (int m = 0; m < measures; m++)
            if (meter.quarterBeatsInMeasure(m) != prepared.frame().quarterBeatsInMeasure(m))
                throw new IOException("Current conversion and audio frame differ");
        var selected = new BitSet(prepared.fullCurrent().rests().size());
        for (int i = 0; i < projection.restOwners().size(); i++) {
            int owner = projection.restOwners().get(i);
            if (owner < 0
                    || owner >= prepared.fullCurrent().rests().size()
                    || selected.get(owner)
                    || !score.rests().get(i).equals(prepared.fullCurrent().rests().get(owner)))
                throw new IOException("Invalid composed Current rest owner port");
            selected.set(owner);
        }
        var events = new ArrayList<ScoreExpressiveEvent>();
        var own = new TreeSet<Integer>();
        var seen = new BitSet(prepared.source().expressiveEvents().size());
        for (int i = 0; i < projection.expressionOwners().size(); i++) {
            int owner = projection.expressionOwners().get(i);
            if (owner < 0
                    || owner >= prepared.source().expressiveEvents().size()
                    || seen.get(owner)
                    || !score.expressiveEvents()
                            .get(i)
                            .equals(prepared.source().expressiveEvents().get(owner)))
                throw new IOException("Invalid Current expression occurrence port");
            seen.set(owner);
            var binding = prepared.byExpression().get(owner);
            // Real SCORE tempo policy remains global; local marks follow actual selected rest
            // membership.
            if (binding != null
                    && binding.scope() != Scope.SCORE
                    && !selected.get(binding.restOwner())) continue;
            if (binding != null) own.add(events.size());
            events.add(score.expressiveEvents().get(i));
        }
        return new Projection(score.withExpressiveEvents(events), own);
    }
}
