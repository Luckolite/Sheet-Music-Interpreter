// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Preserve one literal fp only when its independent glyph observations agree. */
final class CompoundDynamicEvidence {
    private static final class Candidate {
        final PlayingTechniqueDetector.Word literal;
        final List<PlayingTechniqueDetector.Word> members = new ArrayList<>();
        PlayingTechniqueDetector.Word forte, piano;
        boolean ambiguous;

        Candidate(PlayingTechniqueDetector.Word literal) {
            this.literal = literal;
        }
    }

    private final List<Candidate> candidates = new ArrayList<>();

    CompoundDynamicEvidence(List<PlayingTechniqueDetector.Word> originals) {
        for (var word : originals) {
            if (!literal(word) || !valid(word)) continue;
            boolean repeated = false;
            for (var candidate : candidates) if (candidate.literal == word) repeated = true;
            if (!repeated) candidates.add(new Candidate(word));
        }
        for (int i = 0; i < candidates.size(); i++)
            for (int j = i + 1; j < candidates.size(); j++)
                if (overlaps(candidates.get(i).literal, candidates.get(j).literal)) {
                    candidates.get(i).ambiguous = true;
                    candidates.get(j).ambiguous = true;
                }
    }

    static boolean literal(PlayingTechniqueDetector.Word word) {
        return word != null
                && word.text() != null
                && word.text().trim().toLowerCase(Locale.ROOT).matches("fp[.,:;]?");
    }

    static boolean qualified(PlayingTechniqueDetector.Word word) {
        return literal(word)
                && valid(word)
                && word.admission() == PlayingTechniqueDetector.WordAdmission.FP_GLYPH_QUALIFIED;
    }

    /** Called after an existing natural glyph acceptance, never as a new recognizer. */
    void accepted(
            String glyph,
            PlayingTechniqueDetector.Word box,
            PlayingTechniqueDetector.Word recognized) {
        if (!valid(box) || recognized == null) return;
        for (var candidate : candidates) {
            if (!contains(candidate.literal, box)) {
                if (overlaps(candidate.literal, box)) candidate.ambiguous = true;
                continue;
            }
            candidate.members.add(recognized);
            if ("f".equals(glyph)) {
                if (candidate.forte != null && !sameBox(candidate.forte, box))
                    candidate.ambiguous = true;
                candidate.forte = box;
            } else if ("p".equals(glyph)) {
                if (candidate.piano != null && !sameBox(candidate.piano, box))
                    candidate.ambiguous = true;
                candidate.piano = box;
            } else candidate.ambiguous = true;
        }
    }

    /** Replace only this literal and exact naturally accepted contributor objects. */
    void finish(List<PlayingTechniqueDetector.Word> result) {
        for (var candidate : candidates) {
            var f = candidate.forte;
            var p = candidate.piano;
            boolean paired =
                    f != null
                            && p != null
                            && f.left() < p.left()
                            && f.right() < p.right()
                            && f.right() > p.left()
                            && f.top() < p.bottom()
                            && f.bottom() > p.top();
            if (candidate.ambiguous || (!qualified(candidate.literal) && !paired)) {
                // An old qualification cannot escape an explicitly conflicting new observation.
                if (qualified(candidate.literal))
                    result.removeIf(word -> word == candidate.literal);
                continue;
            }
            result.removeIf(word -> word == candidate.literal || member(candidate, word));
            var word = candidate.literal;
            // The original OCR box owns qualification; only accepted glyph ink owns output bounds.
            float left = paired ? Math.min(f.left(), p.left()) : word.left();
            float top = paired ? Math.min(f.top(), p.top()) : word.top();
            float right = paired ? Math.max(f.right(), p.right()) : word.right();
            float bottom = paired ? Math.max(f.bottom(), p.bottom()) : word.bottom();
            result.add(
                    new PlayingTechniqueDetector.Word(
                            "fp",
                            left,
                            top,
                            right,
                            bottom,
                            PlayingTechniqueDetector.WordAdmission.FP_GLYPH_QUALIFIED));
        }
    }

    private static boolean member(Candidate candidate, PlayingTechniqueDetector.Word word) {
        for (var member : candidate.members) if (member == word) return true;
        return false;
    }

    static boolean valid(PlayingTechniqueDetector.Word word) {
        return word != null
                && Float.isFinite(word.left())
                && Float.isFinite(word.top())
                && Float.isFinite(word.right())
                && Float.isFinite(word.bottom())
                && word.left() >= 0
                && word.top() >= 0
                && word.right() <= 1
                && word.bottom() <= 1
                && word.left() < word.right()
                && word.top() < word.bottom();
    }

    private static boolean contains(
            PlayingTechniqueDetector.Word outer, PlayingTechniqueDetector.Word inner) {
        return outer.left() <= inner.left()
                && outer.right() >= inner.right()
                && outer.top() <= inner.top()
                && outer.bottom() >= inner.bottom();
    }

    private static boolean overlaps(
            PlayingTechniqueDetector.Word a, PlayingTechniqueDetector.Word b) {
        return a.left() < b.right()
                && a.right() > b.left()
                && a.top() < b.bottom()
                && a.bottom() > b.top();
    }

    private static boolean sameBox(
            PlayingTechniqueDetector.Word a, PlayingTechniqueDetector.Word b) {
        return a.left() == b.left()
                && a.right() == b.right()
                && a.top() == b.top()
                && a.bottom() == b.bottom();
    }
}
