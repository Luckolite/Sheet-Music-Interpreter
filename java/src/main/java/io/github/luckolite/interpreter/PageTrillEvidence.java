// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Page-local agreement supplements, but never replaces, the generic glyph evidence. */
final class PageTrillEvidence {
    private record Sample(PortableNoteOrnaments.Bounds bounds, PortableOrnamentGlyphs glyph) {}

    private final List<Sample> samples = new ArrayList<>();
    private final Set<PortableNoteOrnaments.Bounds> confirmed = new HashSet<>();

    PageTrillEvidence(
            PortableOrnamentGlyphs recognizer,
            byte[] gray,
            int width,
            int height,
            List<PortableNoteOrnaments.Bounds> boxes,
            List<PlayingTechniqueDetector.Word> words) {
        for (var word : words) {
            if (!word.text().equals("tr")
                    || !ScoreDynamicsDetector.containsInk(word, gray, width, height)) continue;
            float left = word.left() * width,
                    right = word.right() * width,
                    top = word.top() * height,
                    bottom = word.bottom() * height;
            PortableNoteOrnaments.Bounds best = null;
            float area = 0;
            for (var box : boxes) {
                float overlap =
                        Math.max(0, Math.min(right, box.right) - Math.max(left, box.left))
                                * Math.max(
                                        0, Math.min(bottom, box.bottom) - Math.max(top, box.top));
                float size = box.width() * box.height();
                if (overlap < size * .9f || (right - left) * (bottom - top) > size * 3.5f) continue;
                var weak = recognizer.match(gray, width, box);
                if (weak.kind() != NoteOrnament.TRILL || weak.score() < .30f) continue;
                if (size > area) {
                    area = size;
                    best = box;
                }
            }
            if (best == null || !confirmed.add(best) || samples.size() >= 32) continue;
            byte[] crop = new byte[best.width() * best.height()];
            for (int y = 0; y < best.height(); y++)
                System.arraycopy(
                        gray,
                        (best.top + y) * width + best.left,
                        crop,
                        y * best.width(),
                        best.width());
            var glyph = new PortableOrnamentGlyphs();
            glyph.add(crop, best.width(), best.height(), NoteOrnament.TRILL, false);
            samples.add(new Sample(best, glyph));
        }
    }

    boolean confirmed(PortableNoteOrnaments.Bounds box) {
        return confirmed.contains(box);
    }

    boolean recognizes(
            byte[] gray,
            int width,
            PortableNoteOrnaments.Bounds box,
            PortableOrnamentGlyphs.Match weak) {
        if (weak.kind() != NoteOrnament.TRILL || weak.score() < .30f) return false;
        int agreements = 0;
        PortableOrnamentGlyphs.Query query = null;
        for (var sample : samples) {
            // The same OCR hit seen in two crop passes is not independent evidence.
            if (sample.bounds.equals(box)) continue;
            if (query == null) query = new PortableOrnamentGlyphs.Query();
            if (sample.glyph.match(gray, width, box, query).score() >= .82f && ++agreements >= 2)
                return true;
        }
        return false;
    }
}
