// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.regex.*;

/** Conservative numeric tab syntax and explicit rhythm; never invents a duration from spacing. */
public final class TabNotation {
    private TabNotation() {}

    public static List<TablatureDecoder.Fret> parse(
            String text, float left, float right, float y, int string) {
        text = text.trim();
        if (text.matches("\\([0-9]{1,2}\\)")) text = text.substring(1, text.length() - 1);
        var out = new ArrayList<TablatureDecoder.Fret>();
        int flags = 0;
        if (text.endsWith("~")) {
            flags |= TabEffect.VIBRATO;
            text = text.replaceAll("~+$", "");
        }
        var harmonic = Pattern.compile("<([0-9]{1,2})>").matcher(text);
        if (harmonic.matches()) {
            int f = Integer.parseInt(harmonic.group(1));
            if (TabEffect.harmonicOffset(f) < 0) return List.of();
            return List.of(
                    fret(
                            (left + right) / 2,
                            y,
                            string,
                            f,
                            flags | TabEffect.encode(TabEffect.HARMONIC, 0)));
        }
        var bend =
                Pattern.compile("([0-9]{1,2})[bB]([0-9]{1,2})(?:[rR]([0-9]{1,2}))?").matcher(text);
        if (bend.matches()) {
            int f = Integer.parseInt(bend.group(1)), to = Integer.parseInt(bend.group(2));
            if (f > 36
                    || to > 36
                    || to <= f
                    || to - f > 12
                    || bend.group(3) != null && Integer.parseInt(bend.group(3)) != f)
                return List.of();
            return List.of(
                    fret(
                            left + (right - left) * bend.group(1).length() / (2 * text.length()),
                            y,
                            string,
                            f,
                            flags
                                    | TabEffect.encode(
                                            bend.group(3) == null
                                                    ? TabEffect.BEND
                                                    : TabEffect.BEND_RELEASE,
                                            to - f)));
        }
        if (!text.matches("(?:[0-9]{1,2}|[xX])(?:[/\\\\hHpP](?:[0-9]{1,2}))*")) return List.of();
        var tokens = Pattern.compile("[0-9]{1,2}|[xX]").matcher(text);
        int previous = -1, lastEnd = 0;
        while (tokens.find()) {
            int f = tokens.group().equalsIgnoreCase("x") ? -1 : Integer.parseInt(tokens.group());
            if (f > 36) return List.of();
            int marks = flags;
            if (previous >= 0 && tokens.start() > lastEnd) {
                char op = text.charAt(lastEnd);
                int kind =
                        op == 'h' || op == 'H'
                                ? TabEffect.HAMMER
                                : op == 'p' || op == 'P' ? TabEffect.PULL : TabEffect.SLIDE;
                if (kind == TabEffect.HAMMER && f <= previous
                        || kind == TabEffect.PULL && f >= previous) return List.of();
                if (Math.abs(previous - f) > 24) return List.of();
                marks |= TabEffect.encode(kind, previous - f);
            }
            float x =
                    left + (right - left) * (tokens.start() + tokens.end()) / (2f * text.length());
            out.add(fret(x, y, string, f, marks));
            previous = f;
            lastEnd = tokens.end();
        }
        return out;
    }

    private static TablatureDecoder.Fret fret(float x, float y, int string, int f, int marks) {
        return new TablatureDecoder.Fret(x, y, string, f, 0, 0, 0, marks);
    }

    /** Q/E/S/T, H/W duration labels and Unicode note/rest glyphs in a separate rhythm lane. */
    public static float duration(String text) {
        String s = text.replace(".", "").trim();
        return switch (s) {
            case "W", "𝅝", "𝄻", "\uE4E3" -> 4;
            case "H", "𝅗𝅥", "𝄼", "\uE4E4" -> 2;
            case "Q", "♩", "𝅘𝅥", "𝄽", "\uE4E5" -> 1;
            case "E", "♪", "𝅘𝅥𝅮", "𝄾", "\uE4E6" -> .5f;
            case "S", "𝅘𝅥𝅯", "𝄿", "\uE4E7" -> .25f;
            case "T", "𝅘𝅥𝅰", "𝅀", "\uE4E8" -> .125f;
            default -> 0;
        };
    }

    private static boolean wholeRestGlyph(String text) {
        String s = text.replace(".", "").trim();
        return s.equals("\uE4E3") || s.equals("𝄻");
    }

    public static boolean rest(String s) {
        return s.codePoints()
                .anyMatch(c -> c >= 0x1d13b && c <= 0x1d140 || c >= 0xe4e3 && c <= 0xe4e8);
    }

    public static List<TablatureDecoder.Staff> rhythmWords(
            List<TablatureDecoder.Staff> tabs, List<TablatureDecoder.Word> words, int w, int h) {
        var result = new ArrayList<TablatureDecoder.Staff>();
        for (var t : tabs) {
            var fs = new ArrayList<>(t.frets());
            var deferred = new ArrayList<TablatureDecoder.Fret>();
            if (t.standardTop() < 0)
                for (var word : words) {
                    float x = (word.left() + word.right()) * .5f * w,
                            y = (word.top() + word.bottom()) * .5f * h,
                            d = duration(word.text());
                    if ((word.text().equals("H") || word.text().equals("T"))
                            && words.stream()
                                    .noneMatch(
                                            v ->
                                                    v.text().matches("[QESW]\\.?")
                                                            && Math.abs(
                                                                            (v.top() + v.bottom())
                                                                                            * .5f
                                                                                            * h
                                                                                    - y)
                                                                    < t.gap())) continue;
                    if (d == 0 || y < t.top() - t.gap() * 3 || y > t.bottom() + t.gap() * 3)
                        continue;
                    if (!rest(word.text())
                            && y >= t.top() - t.gap() * .6f
                            && y <= t.bottom() + t.gap() * .6f) continue;
                    int dots = Math.min(2, (int) word.text().chars().filter(c -> c == '.').count());
                    if (rest(word.text())) {
                        var value =
                                new TablatureDecoder.Fret(
                                        x,
                                        y,
                                        0,
                                        -2,
                                        d,
                                        0,
                                        dots,
                                        0,
                                        false,
                                        1,
                                        wholeRestGlyph(word.text()));
                        if (fs.stream().noneMatch(f -> Math.abs(f.x() - x) < t.gap() * .4f))
                            fs.add(value);
                        else if (value.wholeRestGlyph() && dots == 0) deferred.add(value);
                    } else
                        for (int i = 0; i < fs.size(); i++) {
                            var f = fs.get(i);
                            if (f.fret() != -2 && Math.abs(f.x() - x) < t.gap() * .6f)
                                fs.set(i, withRhythm(f, d, 0, dots));
                        }
                }
            if (t.standardTop() < 0)
                for (var word : words) {
                    if (word.text().isBlank()) continue;
                    int cp = word.text().codePointAt(0);
                    float x = (word.left() + word.right()) * .5f * w,
                            y = (word.top() + word.bottom()) * .5f * h;
                    if (cp == 0xe1e7) {
                        boolean restDot = false;
                        for (var values : List.of(fs, deferred))
                            for (int i = 0; i < values.size(); i++) {
                                var f = values.get(i);
                                if (f.wholeRestGlyph()
                                        && f.x() < x
                                        && x - f.x() < t.gap() * 1.6f
                                        && Math.abs(y - f.y()) < t.gap() * .6f) {
                                    values.set(
                                            i,
                                            withRhythm(
                                                    f,
                                                    f.duration(),
                                                    f.beams(),
                                                    Math.min(2, f.dots() + 1)));
                                    restDot = true;
                                }
                            }
                        if (restDot)
                            continue; // The silent lane owns its dot, including above the generic
                                      // rhythm lane.
                    }
                    if (y < t.top() - t.gap() * 2 || y > t.bottom() + t.gap() * 3.5f) continue;
                    if (cp == 0xe241 || cp == 0xe243 || cp == 0xe245)
                        for (int i = 0; i < fs.size(); i++) {
                            var f = fs.get(i);
                            if (f.fret() != -2
                                    && Math.abs(word.left() * w - f.x()) < t.gap() * .7f
                                    && y > t.bottom())
                                fs.set(i, withRhythm(f, 0, 1 + (cp - 0xe241) / 2, f.dots()));
                        }
                    if (cp == 0xe1e7) {
                        float closest = Float.MAX_VALUE, target = -1;
                        for (var f : fs)
                            if (f.x() < x && x - f.x() < t.gap() * 1.6f && x - f.x() < closest) {
                                closest = x - f.x();
                                target = f.x();
                            }
                        if (target >= 0)
                            for (int i = 0; i < fs.size(); i++) {
                                var f = fs.get(i);
                                if (Math.abs(f.x() - target) < t.gap() * .3f)
                                    fs.set(
                                            i,
                                            withRhythm(
                                                    f,
                                                    f.duration(),
                                                    f.beams(),
                                                    Math.min(2, f.dots() + 1)));
                            }
                    }
                    if (cp == 0xe4a2)
                        for (int i = 0; i < fs.size(); i++) {
                            var f = fs.get(i);
                            if (f.fret() >= 0
                                    && Math.abs(f.x() - x) < t.gap() * .4f
                                    && y < f.y()
                                    && f.y() - y < t.gap() * 2)
                                fs.set(
                                        i,
                                        new TablatureDecoder.Fret(
                                                f.x(),
                                                f.y(),
                                                f.string(),
                                                f.fret(),
                                                f.duration(),
                                                f.beams(),
                                                f.dots(),
                                                f.marks() | NoteArticulation.STACCATO));
                        }
                }
            if (t.standardTop() < 0)
                for (var word : words)
                    if (word.text().equals("3")) {
                        float x = (word.left() + word.right()) * .5f * w,
                                y = (word.top() + word.bottom()) * .5f * h;
                        if (y < t.bottom() + t.gap() * 1.5f
                                || y > t.bottom() + t.gap() * 4.5f
                                || (word.bottom() - word.top()) * h > t.gap() * .9f) continue;
                        var candidates = new ArrayList<TablatureDecoder.Fret>();
                        for (var f : fs) if (f.fret() >= 0) candidates.add(f);
                        candidates.sort(Comparator.comparingDouble(f -> Math.abs(f.x() - x)));
                        if (candidates.size() < 3) continue;
                        var three = new ArrayList<>(candidates.subList(0, 3));
                        three.sort(Comparator.comparingDouble(TablatureDecoder.Fret::x));
                        float a = three.get(1).x() - three.get(0).x(),
                                b = three.get(2).x() - three.get(1).x();
                        if (a < t.gap() * .6f
                                || b < t.gap() * .6f
                                || Math.max(a, b) > Math.min(a, b) * 1.7f
                                || Math.abs(three.get(1).x() - x) > t.gap() * .5f) continue;
                        for (var f : three) {
                            fs.remove(f);
                            fs.add(
                                    new TablatureDecoder.Fret(
                                            f.x(),
                                            f.y(),
                                            f.string(),
                                            f.fret(),
                                            f.duration(),
                                            f.beams(),
                                            f.dots(),
                                            f.marks(),
                                            f.tied(),
                                            3));
                        }
                    }
            // Same-column whole rests are retained only when a separate full-bar voice is proved.
            for (var rest : deferred)
                if (TablatureDecoder.fullMeasureRest(t.withFrets(fs), rest)) fs.add(rest);
            fs.sort(Comparator.comparingDouble(TablatureDecoder.Fret::x));
            result.add(t.withFrets(fs));
        }
        return List.copyOf(result);
    }

    static TablatureDecoder.Fret withRhythm(
            TablatureDecoder.Fret f, float duration, int beams, int dots) {
        if (f.fret() != -2 && duration > 0 && duration < .25f && beams == 0) {
            beams = duration == .125f ? 3 : 4;
            duration = 0;
        }
        return new TablatureDecoder.Fret(
                f.x(),
                f.y(),
                f.string(),
                f.fret(),
                duration,
                beams,
                dots,
                f.marks(),
                f.tied(),
                f.tuplet(),
                f.wholeRestGlyph());
    }

    public static List<TablatureDecoder.Staff> rasterRhythm(
            List<TablatureDecoder.Staff> tabs,
            byte[] gray,
            int w,
            int h,
            List<TablatureDecoder.Word> words) {
        var result = new ArrayList<TablatureDecoder.Staff>();
        for (var t : rasterRhythm(tabs, gray, w, h)) {
            var fs = new ArrayList<>(t.frets());
            for (var word : words)
                if (word.text().equals("\uE1E7")) {
                    float x = (word.left() + word.right()) * .5f * w,
                            y = (word.top() + word.bottom()) * .5f * h;
                    if (y < t.top() - t.gap() || y > t.bottom() + t.gap() * 3.5f) continue;
                    TablatureDecoder.Fret nearest = null;
                    for (var f : fs)
                        if (f.x() < x
                                && x - f.x() < t.gap() * 1.6f
                                && (nearest == null || f.x() > nearest.x())) nearest = f;
                    if (nearest != null && nearest.tied() && nearest.dots() == 0) {
                        float target = nearest.x();
                        for (int i = 0; i < fs.size(); i++) {
                            var f = fs.get(i);
                            if (f.tied()
                                    && Math.abs(f.x() - target) < t.gap() * .3f
                                    && f.dots() == 0)
                                fs.set(i, withRhythm(f, f.duration(), f.beams(), 1));
                        }
                    }
                }
            result.add(t.withFrets(fs));
        }
        return TabPerformanceMarks.apply(List.copyOf(result), words, w, h);
    }

    /** External stems and beams used by common tab engraving; ambiguous/no-stem values stay unknown. */
    public static List<TablatureDecoder.Staff> rasterRhythm(
            List<TablatureDecoder.Staff> tabs, byte[] gray, int w, int h) {
        tabs = TabRhythmContinuations.apply(tabs, gray, w, h);
        var result = new ArrayList<TablatureDecoder.Staff>();
        for (var t : tabs) {
            var frets = new ArrayList<TablatureDecoder.Fret>();
            for (var f : t.frets()) {
                if (t.standardTop() >= 0 || f.duration() > 0 || f.beams() > 0 || f.fret() == -2) {
                    frets.add(f);
                    continue;
                }
                int beams = -1;
                float stemDuration = 1;
                float bestDistance = Float.MAX_VALUE;
                for (int direction : new int[] {1, -1}) {
                    float edge = t.top() + (direction == 1 ? (t.stringCount() - 1) * t.gap() : 0);
                    for (int x = Math.max(0, Math.round(f.x() - t.gap() * .3f));
                            x <= Math.min(w - 1, Math.round(f.x() + t.gap() * .3f));
                            x++) {
                        int start = Math.round(edge + direction * t.gap() * .55f),
                                end = start,
                                ink = 0;
                        // Short quarter stems start farther from the string block.
                        for (int skip = 0; skip < Math.round(t.gap() * 1.5f); skip++) {
                            int y = start + direction * skip;
                            if (y < 0 || y >= h) break;
                            if ((gray[y * w + x] & 255) < 180) {
                                start = y;
                                break;
                            }
                        }
                        for (int j = 0; j < Math.round(t.gap() * 3.3f); j++) {
                            int y = start + direction * j;
                            if (y < 0 || y >= h) break;
                            if ((gray[y * w + x] & 255) < 180) {
                                ink++;
                                end = y;
                            } else if (j > t.gap() * .4f) break;
                        }
                        if (ink < t.gap() * .85f) continue;
                        int count = 0, lastBeam = -100000;
                        for (int y = Math.min(start, end); y <= Math.max(start, end); y++) {
                            int across = 0, total = 0;
                            for (int dx = Math.round(t.gap() * .15f);
                                    dx <= Math.round(t.gap() * .45f);
                                    dx++) {
                                total++;
                                boolean hit = x + dx < w && (gray[y * w + x + dx] & 255) < 180;
                                hit |= x - dx >= 0 && (gray[y * w + x - dx] & 255) < 180;
                                if (hit) across++;
                            }
                            boolean b = total > 0 && across >= total * .8f;
                            if (b) {
                                if (y - lastBeam > t.gap() * .16f) count++;
                                lastBeam = y;
                            }
                        }
                        if (count <= 4 && Math.abs(x - f.x()) < bestDistance) {
                            beams = count;
                            stemDuration = ink < t.gap() * 1.45f ? 2 : 1;
                            bestDistance = Math.abs(x - f.x());
                        }
                    }
                    if (beams >= 0) break;
                }
                frets.add(
                        beams < 0
                                ? f
                                : withRhythm(f, beams == 0 ? stemDuration : 0, beams, f.dots()));
            }
            result.add(t.withFrets(frets));
        }
        // A lone unstemmed chord is a whole note only within otherwise explicitly rhythmic tab.
        boolean rhythmic =
                result.stream()
                        .flatMap(t -> t.frets().stream())
                        .anyMatch(f -> f.duration() > 0 || f.beams() > 0);
        if (rhythmic)
            for (int ti = 0; ti < result.size(); ti++) {
                var t = result.get(ti);
                if (t.standardTop() >= 0) continue;
                var frets = new ArrayList<>(t.frets());
                for (int bi = 0; bi + 1 < t.bars().size(); bi++) {
                    float left = t.bars().get(bi), right = t.bars().get(bi + 1);
                    var group = new ArrayList<TablatureDecoder.Fret>();
                    for (var f : frets) if (f.x() > left && f.x() < right) group.add(f);
                    if (group.isEmpty()
                            || group.stream()
                                    .anyMatch(
                                            f -> f.fret() < 0 || f.duration() > 0 || f.beams() > 0))
                        continue;
                    float first = group.get(0).x();
                    if (group.stream().anyMatch(f -> Math.abs(f.x() - first) > t.gap() * .5f))
                        continue;
                    for (var f : group) {
                        frets.remove(f);
                        frets.add(withRhythm(f, 4, 0, f.dots()));
                    }
                }
                frets.sort(Comparator.comparingDouble(TablatureDecoder.Fret::x));
                result.set(ti, t.withFrets(frets));
            }
        return List.copyOf(result);
    }
}
