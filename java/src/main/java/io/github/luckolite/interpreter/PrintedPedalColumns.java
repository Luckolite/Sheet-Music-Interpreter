// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Optical pedal columns, before inherited meter or playback is resolved. */
final class PrintedPedalColumns {
    record Located(
            PrintedPedalGlyphs.OwnedMark source,
            Optional<PedalBracketAnchors.Hook> hook,
            String reason) {}

    static Located locate(
            PrintedPedalGlyphs.OwnedMark owned,
            ScorePageInterpretation score,
            byte[] gray,
            int width,
            int height) {
        var mark = owned.mark();
        var staff = owned.staff();
        float gap = staff.gap(), cy = (staff.top() + staff.bottom()) * .5f / height;
        var candidates = new ArrayList<PedalBracketAnchors.Hook>();
        for (int i = 0; i < score.measures().size(); i++) {
            var m = score.measures().get(i);
            if (cy < m.top() || cy > m.bottom()) continue;
            float l = m.left() * width, r = m.right() * width;
            if (mark.left() < l - gap || mark.right() > r + gap) continue;
            if (mark.kind() == 2) {
                if (Math.abs(mark.right() - r) <= gap * .65f
                        && barline(gray, width, height, r, staff))
                    candidates.add(new PedalBracketAnchors.Hook(i + 1, 0, 0, 0));
            } else {
                float tolerance = Math.max(.000001f, Math.min(gap * .06f, 1.5f) / (r - l));
                for (var n : score.notes())
                    if (n.measureIndex() == i
                            && n.staffIndex() == staff.index()
                            && n.staffCount() == staff.count()
                            && (n.articulations() & NoteOrnament.GRACE) == 0) {
                        float x = l + n.positionInMeasure() * (r - l);
                        if (x >= mark.left() && x <= mark.right())
                            candidates.add(
                                    new PedalBracketAnchors.Hook(
                                            i, 1, n.positionInMeasure(), tolerance));
                    }
                for (var rest : score.rests())
                    if (rest.measureIndex() == i
                            && rest.staffIndex() == staff.index()
                            && rest.staffCount() == staff.count()) {
                        float x = l + rest.positionInMeasure() * (r - l);
                        float margin = rest.isFullMeasure() ? gap * .65f : 0;
                        if (x >= mark.left() - margin && x <= mark.right() + margin)
                            candidates.add(
                                    new PedalBracketAnchors.Hook(
                                            i, 2, rest.positionInMeasure(), tolerance));
                    }
            }
        }
        // Chord heads may have subpixel optical offsets but must identify one printed column.
        var unique = new ArrayList<PedalBracketAnchors.Hook>();
        for (var c : candidates)
            if (unique.stream()
                    .noneMatch(
                            x ->
                                    x.measure() == c.measure()
                                            && x.kind() == c.kind()
                                            && Math.abs(x.position() - c.position())
                                                    <= Math.max(x.tolerance(), c.tolerance())))
                unique.add(c);
        return new Located(
                owned,
                unique.size() == 1 ? Optional.of(unique.get(0)) : Optional.empty(),
                unique.size() == 1
                        ? "proved-column"
                        : unique.isEmpty() ? "no-column" : "ambiguous-columns");
    }

    private static boolean barline(
            byte[] g, int w, int h, float x, PlayingTechniqueDetector.Staff s) {
        int top = Math.max(0, Math.round(s.top())),
                bottom = Math.min(h - 1, Math.round(s.bottom()));
        for (int xx = Math.max(0, Math.round(x - s.gap() * .8f));
                xx <= Math.min(w - 1, Math.round(x + s.gap() * .8f));
                xx++) {
            int hits = 0;
            for (int y = top; y <= bottom; y++) if ((g[y * w + xx] & 255) < 175) hits++;
            if (hits >= (bottom - top + 1) * .9f) return true;
        }
        return false;
    }
}
