// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bounded decoded-data bridge; shares the app's expressive policy and navigation projection. */
public final class PerformanceBridge {
    private PerformanceBridge() {}

    private static int count(DataInputStream in, int max) throws IOException {
        int value = in.readInt();
        if (value < 0 || value > max) throw new IOException("Invalid performance count");
        return value;
    }

    private static String text(DataInputStream in) throws IOException {
        int bytes = count(in, 4096);
        byte[] raw = in.readNBytes(bytes);
        if (raw.length != bytes) throw new EOFException();
        String value = new String(raw, StandardCharsets.UTF_8);
        if (!Arrays.equals(raw, value.getBytes(StandardCharsets.UTF_8)) || value.isBlank())
            throw new IOException("Invalid performance identity");
        return value;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Usage: PerformanceBridge request.expr output.json");
        var path = Path.of(args[0]);
        if (Files.size(path) > 64L * 1024 * 1024)
            throw new IOException("Performance request too large");
        Map<String, Object> output;
        try (var in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != 0x45585031) throw new IOException("Not an EXP1 request");
            double bpm = in.readDouble();
            int measures = count(in, 100000);
            var durations = new ArrayList<Double>();
            for (int i = 0; i < measures; i++) durations.add(in.readDouble());
            var meter = ScoreMeterMap.fromPerformedDurations(durations);
            var numeric = new ArrayList<ScoreTempoChange>();
            int n = count(in, 100000);
            for (int i = 0; i < n; i++)
                numeric.add(
                        new ScoreTempoChange(
                                in.readInt(), in.readFloat(), in.readDouble(), in.readDouble()));
            var sounds = new ArrayList<ScoreExpressivePerformance.Sound>();
            n = count(in, 250000);
            for (int i = 0; i < n; i++)
                sounds.add(
                        new ScoreExpressivePerformance.Sound(
                                text(in),
                                in.readDouble(),
                                in.readDouble(),
                                in.readInt(),
                                in.readInt()));
            var bindings = new HashMap<String, Set<String>>();
            n = count(in, 250000);
            for (int i = 0; i < n; i++) {
                String id = text(in);
                int members = count(in, 250000);
                var targets = new HashSet<String>();
                for (int j = 0; j < members; j++)
                    if (!targets.add(text(in))) throw new IOException("Duplicate held sound");
                if (bindings.put(id, targets) != null)
                    throw new IOException("Duplicate expression binding");
            }
            var directions = ScoreSemanticWire.readDirections(in, measures);
            var expressions = ScoreSemanticWire.readExpressions(in, measures);
            if (in.read() != -1) throw new IOException("Trailing performance bytes");
            var realized =
                    ScoreExpressivePerformance.resolve(
                            bpm,
                            meter,
                            measures,
                            numeric,
                            expressions,
                            sounds,
                            bindings,
                            ScoreExpressivePerformance.Policy.preview());
            var plan = ScoreNavigationPlan.create(measures, directions, meter);
            plan.traversal().requirePerformanceQualified();
            var source = new ArrayList<ScoreNavigationNoteProjection.SourceNote>();
            for (int i = 0; i < sounds.size(); i++) {
                var sound = sounds.get(i);
                source.add(
                        new ScoreNavigationNoteProjection.SourceNote(
                                sound.id(), i, sound.startBeat(), sound.endBeat()));
            }
            var notes =
                    ScoreNavigationNoteProjection.project(
                            source,
                            plan,
                            meter,
                            ScoreNavigationNoteProjection.EntryPolicy.REATTACK);
            var performed =
                    ScoreNavigationPerformance.project(
                            realized.timeline(),
                            plan,
                            meter,
                            realized.holdOwnership(),
                            notes.targetMapper());
            var attacks = new ArrayList<ScoreExpressivePerformance.Attack>();
            var pedal = PedalPerformance.resolve(expressions, meter, measures);
            var pedalSpans = PedalPerformance.project(pedal.spans(), plan, meter);
            for (var occurrence : plan.traversal().occurrences()) {
                double start = occurrence.start().absoluteBeat(meter),
                        end = occurrence.end().absoluteBeat(meter);
                for (var attack : realized.attacks())
                    if (attack.beat() >= start && attack.beat() < end)
                        attacks.add(
                                new ScoreExpressivePerformance.Attack(
                                        occurrence.occurrenceId() + "/" + attack.eventId(),
                                        occurrence.performanceStartBeat() + attack.beat() - start,
                                        attack.staffIndex(),
                                        attack.staffCount(),
                                        attack.gain(),
                                        attack.settledGain(),
                                        attack.seconds()));
            }
            output = new LinkedHashMap<>();
            output.put("policy", realized.policy());
            var diagnostics = new ArrayList<>(realized.diagnostics());
            diagnostics.addAll(pedal.diagnostics());
            output.put("diagnostics", diagnostics);
            output.put("openingBpm", performed.timeline().openingBpm());
            output.put("segments", performed.timeline().tempoSegments());
            output.put("holds", performed.timeline().holds());
            output.put("attacks", attacks);
            output.put("pedalPolicy", PedalPerformance.POLICY);
            output.put("pedalSpans", pedalSpans);
            output.put("pedalControls", PedalPerformance.controls(pedalSpans));
            output.put("notes", notes.notes());
            output.put("navigationOccurrences", plan.traversal().occurrences());
            output.put("performedBeats", performed.performedBeats());
            output.put("durationSeconds", performed.durationSeconds());
        }
        Files.writeString(Path.of(args[1]), Main.json(output) + "\n", StandardCharsets.UTF_8);
    }
}
