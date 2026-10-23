// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Converts finalized retained notes and explicit musical evidence into printed arc owners. */
final class PrintedSlurContext {
    private static final int TICKS_PER_BEAT = 10080;

    private PrintedSlurContext() {}

    static List<PrintedSlurOwners.Head> heads(
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> written,
            ScoreMeterMap meter,
            Map<Integer, Integer> voices,
            Map<Integer, Integer> soundingPitches,
            int width,
            int height) {
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid source pixel dimensions");
        written = ScorePlacementMap.validatedWritten(written, notes);
        validateEvidence(voices, notes.size(), 0, Integer.MAX_VALUE, "voice");
        validateEvidence(soundingPitches, notes.size(), -1, 127, "pitch");
        var result = new ArrayList<PrintedSlurOwners.Head>();
        for (int owner = 0; owner < notes.size(); owner++) {
            var note = notes.get(owner);
            var placement = written.get(owner);
            // Missing written timing is unresolved evidence, never a fabricated first attack.
            if (placement == null) continue;
            if (note.measureIndex() >= measures.size())
                throw new IllegalArgumentException("Printed head has no retained measure");
            var region = measures.get(note.measureIndex());
            if (!Float.isFinite(region.left() + region.right() + region.top() + region.bottom())
                    || region.left() < 0
                    || region.right() > 1
                    || region.right() <= region.left()
                    || region.top() < 0
                    || region.bottom() > 1
                    || region.bottom() <= region.top())
                throw new IllegalArgumentException("Printed head has invalid measure geometry");
            float x =
                    (region.left() + (region.right() - region.left()) * note.positionInMeasure())
                            * width;
            float y = note.pageY() * height;
            if (!Float.isFinite(x + y) || x < 0 || x >= width || y < 0 || y >= height)
                throw new IllegalArgumentException("Printed head lies outside its source pixels");
            double beat = meter.startBeat(note.measureIndex()) + placement.onsetBeats();
            if (!Double.isFinite(beat) || beat < 0 || beat * TICKS_PER_BEAT >= Long.MAX_VALUE)
                throw new IllegalArgumentException("Printed head attack exceeds its written clock");
            int voice = voices.getOrDefault(owner, 0);
            // A grace owner inherits the proved principal lane, never a default lane of its own.
            if (placement.grace()) voice = voices.getOrDefault(placement.principalIndex(), 0);
            int graceOrder =
                    !placement.grace()
                            ? 0
                            : placement.afterGrace()
                                    ? Math.addExact(placement.ordinal(), 1)
                                    : placement.ordinal() - 1000000;
            int pitch =
                    note.kind() == ScoreNoteEvent.Kind.UNPITCHED
                            ? -1
                            : soundingPitches.getOrDefault(owner, -1);
            result.add(
                    new PrintedSlurOwners.Head(
                            owner,
                            x,
                            y,
                            note.staffIndex(),
                            note.staffCount(),
                            voice,
                            Math.round(beat * TICKS_PER_BEAT),
                            graceOrder,
                            pitch));
        }
        return List.copyOf(result);
    }

    static List<PrintedSlurOwners.GeometryHead> geometryHeads(
            List<ScoreNoteEvent> notes, List<MeasureRegion> measures, int width, int height) {
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid source pixel dimensions");
        var result = new ArrayList<PrintedSlurOwners.GeometryHead>();
        for (int owner = 0; owner < notes.size(); owner++) {
            var note = notes.get(owner);
            if (note.measureIndex() >= measures.size())
                throw new IllegalArgumentException("Printed head has no retained measure");
            var region = measures.get(note.measureIndex());
            if (!Float.isFinite(region.left() + region.right() + region.top() + region.bottom())
                    || region.left() < 0
                    || region.right() > 1
                    || region.right() <= region.left()
                    || region.top() < 0
                    || region.bottom() > 1
                    || region.bottom() <= region.top())
                throw new IllegalArgumentException("Printed head has invalid measure geometry");
            float x =
                    (region.left() + (region.right() - region.left()) * note.positionInMeasure())
                            * width;
            float y = note.pageY() * height;
            if (!Float.isFinite(x + y) || x < 0 || x >= width || y < 0 || y >= height)
                throw new IllegalArgumentException("Printed head lies outside its source pixels");
            result.add(
                    new PrintedSlurOwners.GeometryHead(
                            owner,
                            x,
                            y,
                            note.staffIndex(),
                            note.staffCount(),
                            (note.articulations() & NoteOrnament.GRACE) != 0));
        }
        return List.copyOf(result);
    }

    private static void validateEvidence(
            Map<Integer, Integer> values, int count, int minimum, int maximum, String kind) {
        Objects.requireNonNull(values);
        for (var entry : values.entrySet())
            if (entry.getKey() == null
                    || entry.getKey() < 0
                    || entry.getKey() >= count
                    || entry.getValue() == null
                    || entry.getValue() < minimum
                    || entry.getValue() > maximum)
                throw new IllegalArgumentException(
                        "Invalid explicit printed " + kind + " ownership");
    }
}
