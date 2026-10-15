// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;
import static io.github.luckolite.interpreter.ScorePerformanceTimeline.*;

/** Versioned preview defaults, separate from the evidence and the written score. */
public final class ScoreExpressivePerformance {
    public static final String POLICY = "expressive-preview-v1";

    public record Policy(
            double pocoRatio,
            double ordinaryRatio,
            double moltoRatio,
            double rampBeats,
            double fermataExtra,
            double breathBeats,
            double attackGain,
            double attackSeconds,
            double pianoGain) {
        public Policy {
            for (double value : new double[] {pocoRatio, ordinaryRatio, moltoRatio})
                if (!Double.isFinite(value) || value <= 0 || value >= 1)
                    throw new IllegalArgumentException(
                            "Slowing ratio must be between zero and one");
            if (!(moltoRatio <= ordinaryRatio && ordinaryRatio <= pocoRatio))
                throw new IllegalArgumentException("Slowing strengths must be ordered");
            for (double value :
                    new double[] {
                        rampBeats, fermataExtra, breathBeats, attackGain, attackSeconds, pianoGain
                    })
                if (!Double.isFinite(value) || value <= 0)
                    throw new IllegalArgumentException("Invalid expressive policy");
        }

        public static Policy preview() {
            return new Policy(.9, .8, .6, 4, 1, .25, 1.6, .12, .55);
        }

        double ratio(Strength strength) {
            return strength == Strength.POCO
                    ? pocoRatio
                    : strength == Strength.MOLTO ? moltoRatio : ordinaryRatio;
        }
    }

    /** Already resolved sounding intervals, including tie chains. IDs are not optical positions. */
    public record Sound(
            String id, double startBeat, double endBeat, int staffIndex, int staffCount) {
        public Sound {
            if (id == null
                    || id.isBlank()
                    || !Double.isFinite(startBeat)
                    || startBeat < 0
                    || !Double.isFinite(endBeat)
                    || endBeat <= startBeat
                    || staffIndex < 0
                    || staffCount <= staffIndex)
                throw new IllegalArgumentException("Invalid expressive sound");
        }
    }

    public record Attack(
            String eventId,
            double beat,
            int staffIndex,
            int staffCount,
            double gain,
            double settledGain,
            double seconds) {
        public Attack {
            if (eventId == null
                    || eventId.isBlank()
                    || !Double.isFinite(beat)
                    || beat < 0
                    || staffIndex < 0
                    || staffCount <= staffIndex
                    || !Double.isFinite(gain)
                    || gain <= 0
                    || !Double.isFinite(settledGain)
                    || settledGain <= 0
                    || !Double.isFinite(seconds)
                    || seconds <= 0)
                throw new IllegalArgumentException("Invalid expressive attack");
        }

        /** A short emphasized attack decays back to its lane's ordinary gain (or piano for sfp). */
        public double gainAtAge(double age) {
            if (!Double.isFinite(age) || age < 0)
                throw new IllegalArgumentException("Invalid attack age");
            double progress = Math.min(1, age / seconds);
            return gain + (settledGain - gain) * progress;
        }
    }

    public record Result(
            ScorePerformanceTimeline timeline,
            Map<String, Boundary> holdOwnership,
            List<Attack> attacks,
            List<String> diagnostics,
            String policy) {
        public Result {
            holdOwnership = Map.copyOf(holdOwnership);
            attacks = List.copyOf(attacks);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record Control(double beat, Double bpm, ScoreExpressiveEvent expression) {}

    private static final Set<Kind> TEMPOS =
            EnumSet.of(
                    Kind.RITARDANDO,
                    Kind.RALLENTANDO,
                    Kind.METRIC_MODULATION,
                    Kind.RITENUTO,
                    Kind.ACCELERANDO,
                    Kind.A_TEMPO,
                    Kind.TEMPO_PRIMO,
                    Kind.SAME_TEMPO);
    private static final Set<Kind> ATTACKS =
            EnumSet.of(Kind.SFORZANDO, Kind.SFORZATO, Kind.SFORZANDO_PIANO);

    private ScoreExpressivePerformance() {}

    /** Target bindings must come from the note/rest resolver, never from a guessed visual fraction. */
    public static Result resolve(
            double openingBpm,
            ScoreMeterMap meter,
            int measures,
            List<ScoreTempoChange> numeric,
            List<ScoreExpressiveEvent> expressions,
            List<Sound> sounds,
            Map<String, Set<String>> targets,
            Policy policy) {
        Objects.requireNonNull(policy);
        double extent = meter.startBeat(measures);
        var baseline = ScorePerformanceTimeline.numeric(openingBpm, meter, numeric);
        var diagnostics = new ArrayList<String>();
        var controls = new ArrayList<Control>();
        for (var change : numeric)
            controls.add(
                    new Control(
                            meter.startBeat(change.measureIndex())
                                    + meter.beatsInMeasure(change.measureIndex())
                                            * change.positionInMeasure(),
                            (double) change.bpm(),
                            null));
        var resolved = new ArrayList<ScoreExpressiveEvent>();
        var ids = new HashSet<String>();
        var soundIds = new HashSet<String>();
        for (var sound : sounds)
            if (!soundIds.add(sound.id())) throw new IllegalArgumentException("Duplicate sound ID");
        for (var event : expressions) {
            if (!ids.add(event.eventId()))
                throw new IllegalArgumentException("Duplicate expressive event ID");
            if (!TEMPOS.contains(event.kind())
                    && !ATTACKS.contains(event.kind())
                    && event.kind() != Kind.FERMATA
                    && event.kind() != Kind.BREATH
                    && event.kind() != Kind.CAESURA) continue;
            if (event.scope() == Scope.UNRESOLVED || event.start().isEmpty()) {
                diagnostics.add(event.eventId() + ": unresolved musical ownership");
                continue;
            }
            try {
                double start = event.start().get().canonical(meter, measures).absoluteBeat(meter);
                if (start > extent) throw new IllegalArgumentException("Outside score");
                event.end().ifPresent(end -> end.canonical(meter, measures));
                resolved.add(event);
                if (TEMPOS.contains(event.kind())) controls.add(new Control(start, null, event));
            } catch (IllegalArgumentException error) {
                diagnostics.add(event.eventId() + ": " + error.getMessage());
            }
        }
        controls.sort(
                Comparator.comparingDouble(Control::beat)
                        .thenComparing(c -> c.expression() == null ? 0 : 1));
        var uniqueControls = new ArrayList<Control>();
        var seenControls = new HashSet<List<Object>>();
        for (var control : controls) {
            var event = control.expression();
            if (event != null
                    && !seenControls.add(
                            List.of(
                                    control.beat(),
                                    event.kind() == Kind.RALLENTANDO
                                            ? Kind.RITARDANDO
                                            : event.kind(),
                                    event.strength(),
                                    event.end(),
                                    event.qualifierText()))) continue;
            uniqueControls.add(control);
        }
        var atBeat = new HashMap<Double, Integer>();
        for (var control : uniqueControls)
            if (control.expression() != null && control.expression().kind() != Kind.SAME_TEMPO)
                atBeat.merge(control.beat(), 1, Integer::sum);
        controls = new ArrayList<>();
        for (var control : uniqueControls) {
            if (control.expression() != null
                    && control.expression().kind() != Kind.SAME_TEMPO
                    && atBeat.get(control.beat()) > 1)
                diagnostics.add(
                        control.expression().eventId()
                                + ": conflicting simultaneous tempo directions");
            else controls.add(control);
        }
        var curves = new ArrayList<TempoSegment>();
        double at = 0, current = openingBpm, steady = openingBpm, rampEnd = 0, target = current;
        for (int index = 0; index <= controls.size(); index++) {
            double next = index == controls.size() ? extent : controls.get(index).beat();
            if (next > extent) break;
            if (next > at) {
                if (rampEnd > at) {
                    double end = Math.min(next, rampEnd);
                    double value = current + (target - current) * (end - at) / (rampEnd - at);
                    curves.add(new TempoSegment(at, end, current, value));
                    at = end;
                    current = value;
                }
                if (next > at) {
                    curves.add(new TempoSegment(at, next, current, current));
                    at = next;
                }
            }
            if (index == controls.size()) break;
            var control = controls.get(index);
            if (control.expression() == null) {
                current = steady = target = control.bpm();
                rampEnd = at;
                continue;
            }
            var event = control.expression();
            if (event.kind() == Kind.METRIC_MODULATION) {
                double value =
                        current
                                * MetricModulationText.decode(event.qualifierText())
                                        .orElseThrow()
                                        .ratio();
                if (!Double.isFinite(value) || value < 15 || value > 1600) {
                    diagnostics.add(
                            event.eventId()
                                    + ": metric modulation is outside supported tempo range");
                    continue;
                }
                current = steady = target = value;
                rampEnd = at;
                continue;
            }
            if (event.kind() == Kind.SAME_TEMPO) continue;
            if (event.kind() == Kind.A_TEMPO || event.kind() == Kind.TEMPO_PRIMO) {
                if (event.strength() != Strength.UNSPECIFIED
                        || !event.qualifierText().replaceAll("[\\s.,;:()]+", "").isEmpty()) {
                    diagnostics.add(
                            event.eventId()
                                    + ": qualified tempo restoration requires an explicit target");
                    continue;
                }
                current = target = event.kind() == Kind.TEMPO_PRIMO ? openingBpm : steady;
                rampEnd = at;
                continue;
            }
            target =
                    Math.max(
                            15,
                            Math.min(
                                    1600,
                                    current
                                            * (event.kind() == Kind.ACCELERANDO
                                                    ? 1 / policy.ratio(event.strength())
                                                    : policy.ratio(event.strength()))));
            // A proved written span may terminate at an explicit numeric tempo.
            // Do not borrow a target through an intervening tempo instruction.
            if (event.kind() != Kind.RITENUTO && event.end().isPresent()) {
                double endBeat = event.end().orElseThrow().absoluteBeat(meter);
                Double writtenTarget = null;
                boolean ambiguous = false;
                for (int following = index + 1; following < controls.size(); following++) {
                    var endpoint = controls.get(following);
                    if (endpoint.beat() > endBeat + 1e-7) break;
                    if (endpoint.expression() != null
                            && endpoint.expression().kind() == Kind.SAME_TEMPO) continue;
                    if (Math.abs(endpoint.beat() - endBeat) > 1e-7
                            || endpoint.expression() != null) {
                        ambiguous = true;
                        break;
                    }
                    if (writtenTarget != null && Math.abs(writtenTarget - endpoint.bpm()) > 1e-7) {
                        ambiguous = true;
                        break;
                    }
                    writtenTarget = endpoint.bpm();
                }
                if (!ambiguous
                        && writtenTarget != null
                        && endBeat > at
                        && (event.kind() == Kind.ACCELERANDO
                                ? writtenTarget > current
                                : writtenTarget < current)) target = writtenTarget;
            }
            if (event.kind() == Kind.RITENUTO) {
                current = target;
                rampEnd = at;
            } else
                rampEnd =
                        Math.min(
                                extent,
                                event.end()
                                        .map(end -> end.absoluteBeat(meter))
                                        .orElse(at + policy.rampBeats()));
            if (rampEnd <= at) {
                current = target;
                rampEnd = at;
            }
        }
        var active =
                controls.stream().noneMatch(c -> c.expression() != null)
                        ? baseline
                        : new ScorePerformanceTimeline(openingBpm, curves, List.of());
        var pauses = new TreeMap<Double, Pause>();
        var attacks = new ArrayList<Attack>();
        for (var event : resolved) {
            double start = event.start().get().absoluteBeat(meter);
            if (ATTACKS.contains(event.kind())) {
                boolean proved =
                        sounds.stream()
                                .anyMatch(
                                        s ->
                                                s.staffIndex() == event.staffIndex()
                                                        && s.staffCount() == event.staffCount()
                                                        && Math.abs(s.startBeat() - start) < 1e-7);
                if (!proved) {
                    diagnostics.add(event.eventId() + ": no owned attack at anchor");
                    continue;
                }
                attacks.add(
                        new Attack(
                                event.eventId(),
                                start,
                                event.staffIndex(),
                                event.staffCount(),
                                policy.attackGain(),
                                event.kind() == Kind.SFORZANDO_PIANO ? policy.pianoGain() : 1,
                                policy.attackSeconds()));
                continue;
            }
            if (event.kind() != Kind.FERMATA
                    && event.kind() != Kind.BREATH
                    && event.kind() != Kind.CAESURA) continue;
            double release =
                    event.kind() == Kind.FERMATA
                            ? event.end().orElseThrow().absoluteBeat(meter)
                            : start;
            var bound = targets.getOrDefault(event.eventId(), Set.of());
            if (!soundIds.containsAll(bound))
                throw new IllegalArgumentException("Unknown held sound");
            if (event.kind() == Kind.FERMATA && event.scope() != Scope.REST && bound.isEmpty()) {
                diagnostics.add(event.eventId() + ": missing fermata sound binding");
                continue;
            }
            if (event.kind() == Kind.FERMATA && release <= start) {
                diagnostics.add(event.eventId() + ": empty fermata extent");
                continue;
            }
            boolean valid = true;
            for (var sound : sounds)
                if (bound.contains(sound.id())
                        && !(sound.startBeat() <= start
                                && sound.startBeat() < release
                                && sound.endBeat() >= release)) valid = false;
            if (!valid) {
                diagnostics.add(event.eventId() + ": held sound does not own release");
                continue;
            }
            var pause = pauses.computeIfAbsent(release, key -> new Pause());
            if (event.kind() == Kind.FERMATA) {
                pause.fermata =
                        Math.max(
                                pause.fermata,
                                (active.activeSecondsAtBeat(release)
                                                - active.activeSecondsAtBeat(start))
                                        * policy.fermataExtra());
                pause.targets.addAll(bound);
            } else
                pause.breath =
                        Math.max(
                                pause.breath,
                                60
                                        / bpmAt(active, release)
                                        * policy.breathBeats()
                                        * (event.kind() == Kind.CAESURA ? 2 : 1));
            // Simultaneous parts share one conductor pause. Continuing accompaniment sustains.
            for (var sound : sounds)
                if (sound.startBeat() < release && sound.endBeat() > release)
                    pause.targets.add(sound.id());
        }
        var holds = new ArrayList<Hold>();
        var ownership = new HashMap<String, Boundary>();
        for (var entry : pauses.entrySet()) {
            String id = POLICY + ":pause:" + Double.toHexString(entry.getKey());
            var pause = entry.getValue();
            holds.add(new Hold(id, entry.getKey(), pause.fermata + pause.breath, pause.targets));
            ownership.put(id, entry.getKey() == 0 ? Boundary.AFTER : Boundary.BEFORE);
        }
        return new Result(
                new ScorePerformanceTimeline(active.openingBpm(), active.tempoSegments(), holds),
                ownership,
                attacks,
                diagnostics,
                POLICY);
    }

    private static final class Pause {
        double fermata, breath;
        final Set<String> targets = new HashSet<>();
    }

    private static double bpmAt(ScorePerformanceTimeline timeline, double beat) {
        double value = timeline.openingBpm();
        for (var segment : timeline.tempoSegments()) {
            if (beat < segment.endBeat())
                return segment.startBpm()
                        + (segment.endBpm() - segment.startBpm())
                                * (beat - segment.startBeat())
                                / (segment.endBeat() - segment.startBeat());
            value = segment.endBpm();
        }
        return value;
    }
}
