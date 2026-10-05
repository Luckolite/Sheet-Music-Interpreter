// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Anchors explicit navigation words and already-verified glyphs to printed bar boundaries. */
final class ScoreNavigationDetector {
    private static final Pattern NAVIGATION_SEPARATORS = Pattern.compile("[\\s.,:;]");

    record Glyph(ScorePlaybackDirection.Kind kind, float centerX, float top, float bottom) {}

    private record Phrase(
            ScorePlaybackDirection.Kind kind, float left, float right, float top, float bottom) {
        Glyph glyph() {
            return new Glyph(kind, (left + right) * .5f, top, bottom);
        }
    }

    private ScoreNavigationDetector() {}

    static ScorePlaybackDirection.Kind kind(String text) {
        if (text == null) return null;
        if (text.trim().equals("𝄋")) return ScorePlaybackDirection.Kind.SEGNO;
        if (text.trim().equals("𝄌")) return ScorePlaybackDirection.Kind.CODA;
        String clean = NAVIGATION_SEPARATORS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
        return switch (clean) {
            case "dsalcoda", "dalsegnoalcoda" -> ScorePlaybackDirection.Kind.DAL_SEGNO_AL_CODA;
            case "dc", "dacapo" -> ScorePlaybackDirection.Kind.DA_CAPO;
            case "ds", "dalsegno" -> ScorePlaybackDirection.Kind.DAL_SEGNO;
            case "dcalfine", "dacapoalfine" -> ScorePlaybackDirection.Kind.DA_CAPO_AL_FINE;
            case "dsalfine", "dalsegnoalfine" -> ScorePlaybackDirection.Kind.DAL_SEGNO_AL_FINE;
            case "dcalcoda", "dacapoalcoda" -> ScorePlaybackDirection.Kind.DA_CAPO_AL_CODA;
            case "fine" -> ScorePlaybackDirection.Kind.FINE;
            case "tocoda" -> ScorePlaybackDirection.Kind.TO_CODA;
            case "coda" -> ScorePlaybackDirection.Kind.CODA;
            case "segno" -> ScorePlaybackDirection.Kind.SEGNO;
            default -> null;
        };
    }

    static List<ScorePlaybackDirection> detect(
            List<PlayingTechniqueDetector.Word> input,
            List<Glyph> glyphs,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            int width,
            int height) {
        if (width <= 0 || height <= 0 || staffs == null || measures == null) return List.of();
        var marks = new ArrayList<Glyph>();
        if (glyphs != null) marks.addAll(glyphs);
        var phrases = new ArrayList<Phrase>();
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        if (input != null) words.addAll(input);
        words.sort(
                Comparator.comparingDouble(PlayingTechniqueDetector.Word::top)
                        .thenComparingDouble(PlayingTechniqueDetector.Word::left));
        for (var word : words) {
            var value = kind(word.text());
            Phrase longest =
                    value == null
                            ? null
                            : new Phrase(
                                    value, word.left(), word.right(), word.top(), word.bottom());
            // OCR may split D.C./D.S., al, and Fine/Coda. Prefer the complete
            // phrase over its plain-jump prefix; join immediate same-line words only.
            var line = new ArrayList<PlayingTechniqueDetector.Word>();
            line.add(word);
            for (var next : words)
                if (next != word
                        && next.left() >= word.right()
                        && Math.abs((next.top() + next.bottom() - word.top() - word.bottom()) * .5f)
                                <= Math.max(next.bottom() - next.top(), word.bottom() - word.top())
                                        * .5f) line.add(next);
            line.sort(Comparator.comparingDouble(PlayingTechniqueDetector.Word::left));
            String text = word.text();
            float right = word.right(), bottom = word.bottom();
            for (int i = 1; i < Math.min(5, line.size()); i++) {
                var next = line.get(i);
                float h = Math.max(bottom - word.top(), next.bottom() - next.top());
                if ((next.left() - right) * width > h * height * 1.3f) break;
                text += " " + next.text();
                right = next.right();
                bottom = Math.max(bottom, next.bottom());
                value = kind(text);
                if (value != null)
                    longest = new Phrase(value, word.left(), right, word.top(), bottom);
            }
            if (longest != null) phrases.add(longest);
        }
        // A token proved to be inside a longer instruction is not a second mark.
        // Do not suppress a neighboring independent sign by a loose distance rule.
        for (var phrase : phrases)
            if (phrases.stream()
                    .noneMatch(
                            other ->
                                    other != phrase
                                            && other.left() <= phrase.left()
                                            && other.right() >= phrase.right()
                                            && (other.left() < phrase.left()
                                                    || other.right() > phrase.right())
                                            && Math.abs(other.bottom() - phrase.bottom())
                                                    <= Math.max(
                                                                    other.bottom() - other.top(),
                                                                    phrase.bottom() - phrase.top())
                                                            * .5f)) marks.add(phrase.glyph());
        var result = new ArrayList<ScorePlaybackDirection>();
        for (var mark : marks) {
            PlayingTechniqueDetector.Staff owner = null;
            float best = Float.MAX_VALUE;
            for (var staff : staffs) {
                float distance = (staff.top() - mark.bottom() * height) / staff.gap();
                // OCR boxes and staff rules round independently on the raster.
                // Keep the six-space bound with at most one physical pixel of tolerance.
                if (distance < -1.2f || distance > 6 + 1f / staff.gap()) continue;
                if (Math.abs(distance) < best) {
                    best = Math.abs(distance);
                    owner = staff;
                }
            }
            if (owner == null) continue;
            int picked = -1, firstOnStaff = -1;
            float nearest = Float.MAX_VALUE;
            float center = (owner.top() + owner.bottom()) * .5f / height;
            for (int m = 0; m < measures.size(); m++) {
                var bar = measures.get(m);
                if (center < bar.top() - owner.gap() / height
                        || center > bar.bottom() + owner.gap() / height) continue;
                if (firstOnStaff < 0) firstOnStaff = m;
                float distance =
                        mark.centerX() < bar.left()
                                ? bar.left() - mark.centerX()
                                : mark.centerX() > bar.right() ? mark.centerX() - bar.right() : 0;
                if (distance < nearest) {
                    nearest = distance;
                    picked = m;
                }
            }
            boolean outgoing = mark.kind().outgoing();
            // A destination may precede the clef and a full seven-accidental signature.
            float headerAllowance = !outgoing && picked == firstOnStaff ? 16 : 8;
            if (picked < 0 || nearest * width > owner.gap() * headerAllowance) continue;
            // Expanded multi-rests share a printed rectangle. Outgoing directions
            // occur after its final logical bar, not after its first repetition.
            if (outgoing)
                while (picked + 1 < measures.size()
                        && measures.get(picked).equals(measures.get(picked + 1))) picked++;
            var direction = new ScorePlaybackDirection(picked + (outgoing ? 1 : 0), mark.kind());
            if (!result.contains(direction)) result.add(direction);
        }
        result.sort(
                Comparator.comparingInt(ScorePlaybackDirection::measureBoundary)
                        .thenComparingInt(d -> d.kind().wireId()));
        return List.copyOf(result);
    }
}
