// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Adapted from Music Sheets: standalone package and platform-independent diagnostics.
package io.github.luckolite.interpreter;

/** Persistent playing instruction for one printed staff; positions are in engraving space. */
public record ScoreTechniqueChange(
        int measureIndex, float positionInMeasure, int staffIndex, int staffCount, int technique) {
    public static final int ARCO = 0, PIZZICATO = 1;
    public static final int CANTABILE = 2, SOSTENUTO = 3, MARCATO = 4, ORDINARIO = 5;
    public static final int UP_BOW = 6, DOWN_BOW = 7;

    public static boolean isBow(int technique) {
        return technique == UP_BOW || technique == DOWN_BOW;
    }

    public static boolean isExpression(int technique) {
        return technique >= CANTABILE && technique <= ORDINARIO;
    }

    /** Canonical exact float site; signed zero denotes the same physical attack. */
    public record Attack(int measure, int positionBits, int staff, int staffCount) {}

    private static Attack attack(int measure, float position, int staff, int count) {
        return new Attack(
                measure, Float.floatToIntBits(position == 0 ? 0 : position), staff, count);
    }

    public Attack attack() {
        return attack(measureIndex, positionInMeasure, staffIndex, staffCount);
    }

    public static Attack attackOf(ScoreNoteEvent note) {
        return attack(
                note.measureIndex(),
                note.positionInMeasure(),
                note.staffIndex(),
                note.staffCount());
    }

    /** Preindex once for bounded exporters; contradictory directions never become last-wins. */
    public static java.util.Map<Attack, Integer> bowMap(
            java.util.List<ScoreTechniqueChange> changes) {
        java.util.Map<Attack, Integer> result = new java.util.HashMap<>();
        if (changes != null)
            for (var change : changes)
                if (isBow(change.technique))
                    result.merge(change.attack(), change.technique, (a, b) -> a.equals(b) ? a : -1);
        return java.util.Map.copyOf(result);
    }

    public static int bowFor(ScoreNoteEvent note, java.util.List<ScoreTechniqueChange> changes) {
        return note == null ? -1 : bowMap(changes).getOrDefault(attackOf(note), -1);
    }

    public ScoreTechniqueChange {
        if (measureIndex < 0
                || !Float.isFinite(positionInMeasure)
                || positionInMeasure < 0
                || positionInMeasure > 1
                || staffCount < 1
                || staffIndex < 0
                || staffIndex >= staffCount
                || technique < 0
                || technique > DOWN_BOW)
            throw new IllegalArgumentException("Invalid playing-technique change");
    }
}
