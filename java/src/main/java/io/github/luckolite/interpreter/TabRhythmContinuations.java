// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Existing tied frets need a connecting arc; synthesizing a fret also needs a detached stem. */
public final class TabRhythmContinuations {
    private TabRhythmContinuations() {}

    public static List<TablatureDecoder.Staff> apply(
            List<TablatureDecoder.Staff> tabs, byte[] gray, int w, int h) {
        byte[] clean = TabFretRaster.clean(gray, w, h, tabs);
        var out = new ArrayList<TablatureDecoder.Staff>();
        for (var sourceTab : tabs) {
            if (sourceTab.standardTop() >= 0 || sourceTab.frets().isEmpty()) {
                out.add(sourceTab);
                continue;
            }
            var tab = existingTargets(sourceTab, clean, w, h);
            var slots = new ArrayList<Float>();
            int start = -1;
            for (int x = 0; x <= w; x++) {
                int longest = 0, run = 0;
                if (x < w)
                    for (int y = Math.max(0, Math.round(tab.bottom() + tab.gap() * .45f));
                            y < Math.min(h, Math.round(tab.bottom() + tab.gap() * 3.1f));
                            y++) {
                        if ((gray[y * w + x] & 255) < 180) {
                            run++;
                            longest = Math.max(longest, run);
                        } else run = 0;
                    }
                boolean stem = longest >= tab.gap() * .8f;
                if (stem && start < 0) start = x;
                if (!stem && start >= 0) {
                    if (x - start < tab.gap() * .25f) slots.add((start + x - 1) * .5f);
                    start = -1;
                }
            }
            // The curved end of a flag is not a second rhythmic stem.
            var separated = new ArrayList<Float>();
            for (float slot : slots) {
                if (!separated.isEmpty()
                        && slot - separated.get(separated.size() - 1) < tab.gap() * .75f) {
                    float prior = separated.get(separated.size() - 1),
                            a = Float.MAX_VALUE,
                            b = Float.MAX_VALUE;
                    for (var f : tab.frets()) {
                        a = Math.min(a, Math.abs(f.x() - prior));
                        b = Math.min(b, Math.abs(f.x() - slot));
                    }
                    if (b < a) separated.set(separated.size() - 1, slot);
                } else separated.add(slot);
            }
            slots = separated;
            var frets = new ArrayList<>(tab.frets());
            frets.sort(Comparator.comparingDouble(TablatureDecoder.Fret::x));
            var stemOwnership = new HashMap<Float, Boolean>();
            var chordBars = new HashSet<Integer>();
            for (var a : tab.frets())
                for (var b : tab.frets())
                    if (a.string() != b.string()
                            && a.fret() >= -1
                            && b.fret() >= -1
                            && (a.marks() & NoteOrnament.GRACE) == 0
                            && (b.marks() & NoteOrnament.GRACE) == 0
                            && Math.abs(a.x() - b.x()) < tab.gap() * .4f)
                        chordBars.add(barIndex(tab, a.x()));
            for (float slot : slots)
                for (int string = 0; string < tab.stringCount(); string++) {
                    if (frets.stream()
                            .anyMatch(
                                    f ->
                                            (f.marks() & NoteOrnament.GRACE) != 0
                                                    && Math.abs(f.x() - slot) < tab.gap() * .45f))
                        continue;
                    TablatureDecoder.Fret previous = null, current = null;
                    for (var f : frets)
                        if (f.string() == string && f.fret() >= -1) {
                            if (Math.abs(f.x() - slot) < tab.gap() * .45f) current = f;
                            else if (f.x() < slot && (previous == null || f.x() > previous.x()))
                                previous = f;
                        }
                    if (previous == null
                            || previous.fret() < 0
                            || current != null && current.fret() < 0
                            || (previous.marks() & NoteOrnament.GRACE) != 0
                            || current != null && previous.fret() != current.fret()
                            || slot - previous.x() > tab.gap() * 15) continue;
                    // An explicit target attack/effect or ornament must retain its onset.
                    // Matching non-attack modulation may continue; a source tap may ring
                    // into an unannotated target. Never erase a newly changed target mark.
                    int modulation = TabEffect.VIBRATO | TabEffect.PALM_MUTE;
                    if (current != null
                            && current.marks() != 0
                            && ((current.marks() & ~modulation) != 0
                                    || (current.marks() & modulation)
                                            != (previous.marks() & modulation))) continue;
                    // Explicit targets already own a printed onset. Only a newly synthesized
                    // blank continuation needs independent detached-stem ownership.
                    if (current == null
                            && !stemOwnership.computeIfAbsent(
                                    slot, x -> ownedBlankStem(gray, w, h, tab, x))) continue;
                    if (!chordBars.contains(barIndex(tab, slot))) {
                        boolean occupied = false;
                        float last = -1;
                        for (var f : frets)
                            if (f.fret() >= -1 && (f.marks() & NoteOrnament.GRACE) == 0) {
                                if (Math.abs(f.x() - slot) < tab.gap() * .45f) occupied = true;
                                else if (f.x() < slot) last = Math.max(last, f.x());
                            }
                        // A monophonic legato arc must not create a second voice under a note on
                        // another string.
                        if (current == null && occupied || previous.x() < last - tab.gap() * .45f)
                            continue;
                    }
                    float end = current == null ? slot : current.x();
                    if (!arc(clean, w, h, previous.x(), end, previous.y(), tab.gap())) continue;
                    if (current != null) {
                        frets.remove(current);
                        frets.add(
                                new TablatureDecoder.Fret(
                                        current.x(),
                                        current.y(),
                                        string,
                                        current.fret(),
                                        current.duration(),
                                        current.beams(),
                                        current.dots(),
                                        current.marks(),
                                        true,
                                        current.tuplet()));
                    } else
                        frets.add(
                                new TablatureDecoder.Fret(
                                        slot,
                                        tab.top() + string * tab.gap(),
                                        string,
                                        previous.fret(),
                                        0,
                                        0,
                                        0,
                                        previous.marks(),
                                        true));
                }
            frets.sort(Comparator.comparingDouble(TablatureDecoder.Fret::x));
            out.add(tab.withFrets(frets));
        }
        return List.copyOf(out);
    }

    /** A printed target already supplies its onset. An unstemmed whole-note tie must not add one. */
    private static TablatureDecoder.Staff existingTargets(
            TablatureDecoder.Staff tab, byte[] clean, int w, int h) {
        var frets = new ArrayList<>(tab.frets());
        frets.sort(Comparator.comparingDouble(TablatureDecoder.Fret::x));
        var previous = new HashMap<Integer, TablatureDecoder.Fret>();
        for (int i = 0; i < frets.size(); i++) {
            var current = frets.get(i);
            var before = previous.put(current.string(), current);
            if (current.tied()
                    || before == null
                    || before.fret() < 0
                    || current.fret() != before.fret()
                    || current.x() - before.x() < tab.gap() * 2
                    || current.x() - before.x() > tab.gap() * 15
                    || barIndex(tab, current.x()) - barIndex(tab, before.x()) > 1
                    || Math.abs(current.y() - before.y()) > tab.gap() * .2f
                    || Math.abs(current.y() - tab.top() - current.string() * tab.gap())
                            > tab.gap() * .2f
                    || current.marks() != before.marks()
                    || (current.marks() & NoteOrnament.GRACE) != 0
                    || TabEffect.kind(current.marks()) != 0
                    || blockedBetween(frets, before.x(), current.x(), tab.gap())) continue;
            if (!existingArc(
                    clean, w, h, before.x(), current.x(), current.y(), tab.gap(), tab.bars()))
                continue;
            var tied =
                    new TablatureDecoder.Fret(
                            current.x(),
                            current.y(),
                            current.string(),
                            current.fret(),
                            current.duration(),
                            current.beams(),
                            current.dots(),
                            current.marks(),
                            true,
                            current.tuplet(),
                            current.wholeRestGlyph());
            frets.set(i, tied);
            previous.put(current.string(), tied);
        }
        return tab.withFrets(frets);
    }

    private static boolean blockedBetween(
            List<TablatureDecoder.Fret> frets, float from, float to, float gap) {
        for (var f : frets)
            if (f.x() > from + gap * .35f
                    && f.x() < to - gap * .35f
                    && (f.fret() < 0
                            || (f.marks() & NoteOrnament.GRACE) != 0
                            || TabEffect.kind(f.marks()) != 0)) return true;
        return false;
    }

    /** Follow one bowed contour outside both fret glyphs, never borrowing the next string's arc. */
    static boolean existingArc(
            byte[] clean,
            int w,
            int h,
            float from,
            float to,
            float cy,
            float gap,
            List<Float> bars) {
        int left = Math.max(0, Math.round(from + gap * .75f)),
                right = Math.min(w - 1, Math.round(to - gap * .85f));
        int low = Math.max(1, Math.round(gap * .08f)),
                high = Math.round(gap * .95f),
                endpoint = Math.round(gap * .6f);
        if (right - left < gap * .5f || high <= low) return false;
        for (int direction : new int[] {-1, 1}) {
            int[] peak = new int[high + 1],
                    origin = new int[high + 1],
                    crest = new int[high + 1],
                    sourceX = new int[high + 1];
            Arrays.fill(peak, -1);
            int[] nextPeak = new int[high + 1],
                    nextOrigin = new int[high + 1],
                    nextCrest = new int[high + 1],
                    nextSourceX = new int[high + 1];
            int sourceEnd = Math.min(right, Math.round(from + gap * 1.15f));
            int targetStart = Math.max(left, Math.round(to - gap * 1.30f));
            int skipped = 0;
            for (int x = left; x <= right; x++) {
                boolean masked = false;
                for (float bar : bars)
                    if (Math.abs(x - bar) <= gap * .16f) {
                        masked = true;
                        break;
                    }
                if (masked) {
                    skipped++;
                    continue;
                }
                Arrays.fill(nextPeak, -1);
                Arrays.fill(nextOrigin, 0);
                Arrays.fill(nextCrest, 0);
                Arrays.fill(nextSourceX, 0);
                int step = Math.max(1, Math.min(Math.round(gap * .25f), 2 * (skipped + 1)));
                skipped = 0;
                boolean[] contour = contourOffsets(clean, w, h, x, cy, direction, low, high);
                for (int j = low; j <= high; j++) {
                    if (!contour[j]) continue;
                    // Wide fret glyphs can move the visible endpoint within this finite outer
                    // window.
                    if (x <= sourceEnd && j <= endpoint) {
                        nextPeak[j] = j;
                        nextOrigin[j] = j;
                        nextCrest[j] = x;
                        nextSourceX[j] = x;
                    }
                    for (int k = Math.max(low, j - step); k <= Math.min(high, j + step); k++)
                        if (peak[k] >= 0) {
                            int top = Math.max(peak[k], j);
                            int rise = top - origin[k], bestRise = nextPeak[j] - nextOrigin[j];
                            if (rise > bestRise
                                    || rise == bestRise && sourceX[k] < nextSourceX[j]) {
                                nextPeak[j] = top;
                                nextOrigin[j] = origin[k];
                                nextCrest[j] = j > peak[k] ? x : crest[k];
                                nextSourceX[j] = sourceX[k];
                            }
                        }
                }
                int[] swap = peak;
                peak = nextPeak;
                nextPeak = swap;
                swap = origin;
                origin = nextOrigin;
                nextOrigin = swap;
                swap = crest;
                crest = nextCrest;
                nextCrest = swap;
                swap = sourceX;
                sourceX = nextSourceX;
                nextSourceX = swap;
                // Courtesy parentheses and wide digits can end the owned arc before the fixed
                // inset.
                // Accept only a connected returning contour inside this finite outer target window.
                if (x >= targetStart)
                    for (int j = low; j <= Math.min(high, endpoint); j++)
                        if (peak[j] >= 0
                                && x - sourceX[j] >= gap * .5f
                                && peak[j] - Math.max(origin[j], j) >= gap * .16f
                                && crest[j] > sourceX[j] + (x - sourceX[j]) * .15f
                                && crest[j] < x - (x - sourceX[j]) * .15f) return true;
            }
        }
        return false;
    }

    /** A thin antialiased stroke can divide one column's ink between two pale pixels. */
    private static boolean[] contourOffsets(
            byte[] clean, int w, int h, int x, float cy, int direction, int low, int high) {
        boolean[] result = new boolean[high + 1];
        int mass = 0;
        long weighted = 0;
        for (int j = low; j <= high + 1; j++) {
            int y = Math.round(cy) + direction * j,
                    value = j <= high && y >= 0 && y < h ? clean[y * w + x] & 255 : 255;
            if (value < 245) {
                int ink = 255 - value;
                mass += ink;
                weighted += (long) j * ink;
            } else if (mass > 0) {
                if (mass > 45) result[Math.round(weighted / (float) mass)] = true;
                mass = 0;
                weighted = 0;
            }
        }
        return result;
    }

    /** A blank fret cannot be invented from a short letter stroke beneath the tab.
     * Tall rhythmic shafts may carry flags/beams. A shorter shaft must instead be
     * a complete narrow isolated component, with no adjacent text-like ink. */
    private static boolean ownedBlankStem(
            byte[] gray, int w, int h, TablatureDecoder.Staff tab, float slot) {
        float gap = tab.gap(), bottom = tab.bottom();
        int x = Math.round(slot);
        int y0 = Math.max(0, Math.round(bottom + gap * .45f)),
                y1 = Math.min(h - 1, Math.round(bottom + gap * 3.1f) - 1);
        if (x < 0 || x >= w || y1 < y0) return false;
        int longest = 0, run = 0, start = -1;
        for (int y = y0; y <= y1; y++) {
            if ((gray[y * w + x] & 255) < 180) {
                run++;
                if (run > longest) {
                    longest = run;
                    start = y - run + 1;
                }
            } else run = 0;
        }
        if (longest >= gap * 1.2f) return true;
        if (longest < gap * .8f || start < 0) return false;
        int x0 = Math.max(0, Math.round(slot - gap)), x1 = Math.min(w - 1, Math.round(slot + gap));
        int cw = x1 - x0 + 1, ch = y1 - y0 + 1;
        boolean[] seen = new boolean[cw * ch];
        int[] queue = new int[cw * ch];
        int seed = (start - y0) * cw + x - x0, read = 0, end = 0;
        queue[end++] = seed;
        seen[seed] = true;
        int minX = x, maxX = x, minY = start, maxY = start;
        while (read < end) {
            int value = queue[read++], px = x0 + value % cw, py = y0 + value / cw;
            minX = Math.min(minX, px);
            maxX = Math.max(maxX, px);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = px + dx, ny = py + dy;
                    if (nx < x0 || nx > x1 || ny < y0 || ny > y1) continue;
                    int next = (ny - y0) * cw + nx - x0;
                    if (!seen[next] && (gray[ny * w + nx] & 255) < 180) {
                        seen[next] = true;
                        queue[end++] = next;
                    }
                }
        }
        if (minX == x0
                || maxX == x1
                || minY == y0
                || maxY == y1
                || maxX - minX + 1 > gap * .18f
                || maxY - minY + 1 < gap * .8f) return false;
        // A short isolated shaft cannot borrow a neighboring letter or small glyph.
        int marginX = Math.max(1, Math.round(gap * .25f)),
                marginY = Math.max(1, Math.round(gap * .1f));
        for (int py = Math.max(0, minY - marginY); py <= Math.min(h - 1, maxY + marginY); py++)
            for (int px = Math.max(0, minX - marginX);
                    px <= Math.min(w - 1, maxX + marginX);
                    px++) {
                if ((gray[py * w + px] & 255) >= 180) continue;
                if (px < x0 || px > x1 || py < y0 || py > y1 || !seen[(py - y0) * cw + px - x0])
                    return false;
            }
        return true;
    }

    private static int barIndex(TablatureDecoder.Staff t, float x) {
        int index = 0;
        for (float bar : t.bars()) if (bar < x) index++;
        return index;
    }

    static boolean arc(byte[] clean, int w, int h, float from, float to, float cy, float gap) {
        if (to - from < gap * .8f) return false;
        float inset = to - from < gap * 2 ? gap * .18f : gap * .35f;
        int left = Math.max(0, Math.round(from + inset)),
                right = Math.min(w - 1, Math.round(to - inset));
        if (right - left < gap * .25f) return false;
        for (int direction : new int[] {-1, 1}) {
            boolean end = false;
            for (int x = Math.max(0, Math.round(to - gap * .45f));
                    x <= Math.min(w - 1, Math.round(to + gap * .1f));
                    x++)
                for (int j = Math.round(gap * .08f); j <= Math.round(gap * .85f); j++) {
                    int y = Math.round(cy) + direction * j;
                    if (y >= 0 && y < h && (clean[y * w + x] & 255) < 180) end = true;
                }
            if (!end) continue;
            int hit = 0, total = 0;
            float min = Float.MAX_VALUE, max = 0;
            for (int x = left; x <= right; x++) {
                total++;
                float closest = Float.MAX_VALUE;
                for (int j = Math.round(gap * .08f); j <= Math.round(gap * 1.8f); j++) {
                    int y = Math.round(cy) + direction * j;
                    if (y >= 0 && y < h && (clean[y * w + x] & 255) < 180)
                        closest = Math.min(closest, j);
                }
                if (closest < Float.MAX_VALUE) {
                    hit++;
                    min = Math.min(min, closest);
                    max = Math.max(max, closest);
                }
            }
            // A flat leftover string fringe cannot prove a tie; require curved vertical travel.
            if (hit >= total * .65f && max - min >= gap * .07f) return true;
        }
        return false;
    }
}
