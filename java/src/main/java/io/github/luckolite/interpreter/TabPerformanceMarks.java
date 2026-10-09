// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Position-bound tapping, hammer/pull letters and printed vibrato; never changes note count. */
public final class TabPerformanceMarks {
    private TabPerformanceMarks() {}

    public static List<TablatureDecoder.Staff> apply(
            List<TablatureDecoder.Staff> tabs, List<TablatureDecoder.Word> words, int w, int h) {
        var out = new ArrayList<TablatureDecoder.Staff>();
        for (var t : tabs) {
            var fs = new ArrayList<>(t.frets());
            var explicitAttacks = new HashSet<AttackKey>();
            for (var word : words) {
                float x = (word.left() + word.right()) * .5f * w,
                        y = (word.top() + word.bottom()) * .5f * h;
                if (y < t.top() - t.gap() * 4 || y > t.bottom() + t.gap()) continue;
                int kind =
                        switch (word.text()) {
                            case "H" -> TabEffect.HAMMER;
                            case "P" -> TabEffect.PULL;
                            case "T" -> TabEffect.TAP;
                            default -> 0;
                        };
                boolean vibrato = word.text().equals("\uEAB2");
                if (kind == 0 && !vibrato || kind != 0 && y >= t.top()) continue;
                int best = -1;
                float distance = Float.MAX_VALUE;
                for (int i = 0; i < fs.size(); i++) {
                    var f = fs.get(i);
                    float dx = vibrato ? word.left() * w - f.x() : Math.abs(f.x() - x);
                    if (f.fret() < 0
                            || y > f.y()
                            || vibrato && dx < -.5f * t.gap()
                            || dx > (vibrato ? 2 : kind == TabEffect.TAP ? .65f : 1.2f) * t.gap())
                        continue;
                    if (!vibrato && kind != TabEffect.TAP && f.x() < x - t.gap() * .4f) continue;
                    if (dx < distance) {
                        best = i;
                        distance = dx;
                    }
                }
                if (best < 0) continue;
                var f = fs.get(best);
                int marks = f.marks();
                if (vibrato) marks |= TabEffect.VIBRATO;
                else {
                    var previous =
                            fs.stream()
                                    .filter(
                                            v ->
                                                    v.string() == f.string()
                                                            && v.fret() >= 0
                                                            && v.x() < f.x() - t.gap() * .4f
                                                            && f.x() - v.x() < t.gap() * 10)
                                    .max(Comparator.comparingDouble(TablatureDecoder.Fret::x))
                                    .orElse(null);
                    int delta =
                            kind == TabEffect.TAP
                                    ? 0
                                    : previous == null ? 99 : previous.fret() - f.fret();
                    if (Math.abs(delta) > 24
                            || kind == TabEffect.HAMMER && delta >= 0
                            || kind == TabEffect.PULL && delta <= 0) continue;
                    marks =
                            (marks & ~(TabEffect.ALL & ~(TabEffect.VIBRATO | TabEffect.PALM_MUTE)))
                                    | TabEffect.encode(kind, delta);
                }
                fs.set(best, marked(f, marks));
                if (kind != 0) explicitAttacks.add(new AttackKey(f.x(), f.string()));
            }
            fs.sort(Comparator.comparingDouble(TablatureDecoder.Fret::x));
            var last = new HashMap<Integer, TablatureDecoder.Fret>();
            for (int i = 0; i < fs.size(); i++) {
                var f = fs.get(i);
                var previous = last.get(f.string());
                if (f.fret() < 0) continue;
                // An explicitly printed tap/hammer/pull is an attack, including a same-fret tap
                // under an arc.
                if (explicitAttacks.contains(new AttackKey(f.x(), f.string()))) f = withoutTie(f);
                else if (f.tied() && previous != null && f.fret() == previous.fret())
                    f =
                            marked(
                                    f,
                                    (f.marks() & ~TabEffect.ALL)
                                            | (previous.marks() & TabEffect.ALL));
                fs.set(i, f);
                last.put(f.string(), f);
            }
            out.add(t.withFrets(fs));
        }
        return List.copyOf(out);
    }

    private record AttackKey(float x, int string) {}

    private static TablatureDecoder.Fret marked(TablatureDecoder.Fret f, int marks) {
        return new TablatureDecoder.Fret(
                f.x(),
                f.y(),
                f.string(),
                f.fret(),
                f.duration(),
                f.beams(),
                f.dots(),
                marks,
                f.tied(),
                f.tuplet(),
                f.wholeRestGlyph());
    }

    private static TablatureDecoder.Fret withoutTie(TablatureDecoder.Fret f) {
        return new TablatureDecoder.Fret(
                f.x(),
                f.y(),
                f.string(),
                f.fret(),
                f.duration(),
                f.beams(),
                f.dots(),
                f.marks(),
                false,
                f.tuplet(),
                f.wholeRestGlyph());
    }
}
