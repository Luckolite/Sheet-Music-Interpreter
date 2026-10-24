// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Stable, note-local ownership for an independently proved printed outgoing falloff. */
public final class ScoreFallOffNotation {
    public static final String SOURCE = "printed-fall-off";
    public static final String CURVED = "falloff-v1:curved";

    private ScoreFallOffNotation() {}

    public static String target(ScoreNoteEvent note) {
        if (note.kind() != ScoreNoteEvent.Kind.PITCHED
                || note.measureIndex() < 0
                || note.staffIndex() < 0
                || note.staffIndex() >= note.staffCount()
                || !Float.isFinite(note.positionInMeasure())
                || note.positionInMeasure() < 0
                || note.positionInMeasure() > 1
                || !Float.isFinite(note.pageY())
                || note.pageY() < 0
                || note.pageY() > 1) return null;
        return "fall-off-note-v1:"
                + note.measureIndex()
                + ":"
                + note.staffIndex()
                + ":"
                + note.staffCount()
                + ":"
                + String.format(
                        Locale.ROOT, "%08x", Float.floatToRawIntBits(note.positionInMeasure()))
                + ":"
                + note.staffStep()
                + ":"
                + String.format(Locale.ROOT, "%08x", Float.floatToRawIntBits(note.pageY()));
    }

    static java.util.OptionalInt targetMeasure(String target) {
        if (target == null) return java.util.OptionalInt.empty();
        String[] fields = target.split(":", -1);
        if (fields.length != 7 || !fields[0].equals("fall-off-note-v1"))
            return java.util.OptionalInt.empty();
        try {
            int measure = Integer.parseInt(fields[1]);
            int staff = Integer.parseInt(fields[2]);
            int count = Integer.parseInt(fields[3]);
            Integer.parseInt(fields[5]);
            if (measure < 0
                    || count < 1
                    || staff < 0
                    || staff >= count
                    || !fields[4].matches("[0-9a-f]{8}")
                    || !fields[6].matches("[0-9a-f]{8}")) return java.util.OptionalInt.empty();
            float x = Float.intBitsToFloat((int) Long.parseLong(fields[4], 16));
            float y = Float.intBitsToFloat((int) Long.parseLong(fields[6], 16));
            if (!Float.isFinite(x) || x < 0 || x > 1 || !Float.isFinite(y) || y < 0 || y > 1)
                return java.util.OptionalInt.empty();
            return java.util.OptionalInt.of(measure);
        } catch (NumberFormatException invalid) {
            return java.util.OptionalInt.empty();
        }
    }

    static String offsetTarget(String target, int measures) {
        var measure = targetMeasure(target);
        if (measure.isEmpty()) return target;
        int shifted = Math.addExact(measure.getAsInt(), measures);
        if (shifted < 0)
            throw new IllegalArgumentException("Falloff target moved before score start");
        int firstColon = target.indexOf(':');
        int secondColon = target.indexOf(':', firstColon + 1);
        return target.substring(0, firstColon + 1) + shifted + target.substring(secondColon);
    }

    /** Duplicate physical identities and foreign evidence do not choose an arbitrary owner. */
    public static int owner(ScoreExpressiveEvent mark, List<ScoreNoteEvent> notes) {
        if (!CURVED.equals(mark.qualifierText())
                || mark.strength() != ScoreExpressiveEvent.Strength.UNSPECIFIED
                || mark.kind() != ScoreExpressiveEvent.Kind.FALL_OFF
                || (mark.scope() != ScoreExpressiveEvent.Scope.NOTE
                        && mark.scope() != ScoreExpressiveEvent.Scope.UNRESOLVED)
                || (mark.scope() == ScoreExpressiveEvent.Scope.NOTE && mark.start().isEmpty())
                || (mark.scope() == ScoreExpressiveEvent.Scope.UNRESOLVED
                        && mark.start().isPresent())
                || mark.end().isPresent()
                || mark.targetEventId().isEmpty()) return -1;
        if (mark.evidence().stream()
                .noneMatch(
                        e ->
                                e.sourceId().equals(SOURCE)
                                        && e.staffIndex() == mark.staffIndex()
                                        && e.staffCount() == mark.staffCount())) return -1;
        int found = -1;
        for (int i = 0; i < notes.size(); i++) {
            var note = notes.get(i);
            if (!mark.targetEventId().get().equals(target(note))) continue;
            if (found >= 0) return -1;
            found = i;
        }
        if (found < 0) return -1;
        var note = notes.get(found);
        return (mark.start().isEmpty() || note.measureIndex() == mark.start().get().measureIndex())
                        && note.staffIndex() == mark.staffIndex()
                        && note.staffCount() == mark.staffCount()
                ? found
                : -1;
    }

    /** Resolve a page-owned symbol only after the final whole-score clock is available. */
    public static Optional<ScoreAnchor> resolveFinalAttack(
            ScoreExpressiveEvent mark,
            List<ScoreNoteEvent> notes,
            int ownerIndex,
            long finalAttackTick,
            long[] measureStarts,
            int divisions) {
        if (divisions <= 0
                || measureStarts == null
                || measureStarts.length < 2
                || owner(mark, notes) != ownerIndex
                || ownerIndex < 0
                || ownerIndex >= notes.size()) return Optional.empty();
        var note = notes.get(ownerIndex);
        int measure = note.measureIndex();
        if (measure < 0
                || measure + 1 >= measureStarts.length
                || measure != targetMeasure(mark.targetEventId().orElse(null)).orElse(-1)
                || measureStarts[measure] >= measureStarts[measure + 1]
                || finalAttackTick < measureStarts[measure]
                || finalAttackTick >= measureStarts[measure + 1]) return Optional.empty();
        double beat = (finalAttackTick - measureStarts[measure]) / (double) divisions;
        var finalAnchor = new ScoreAnchor(measure, beat);
        if (mark.scope() == ScoreExpressiveEvent.Scope.NOTE) {
            var supplied = mark.start().orElseThrow();
            if (supplied.measureIndex() != measure
                    || measureStarts[measure] + Math.round(supplied.quarterBeatOffset() * divisions)
                            != finalAttackTick) return Optional.empty();
        }
        return Optional.of(finalAnchor);
    }
}
