// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.regex.Pattern;
/** Preserves a caption beside an already proved metronome mark; never supplies a tempo. */
final class PrintedTempoCaption {
    record Caption(String text, int measureIndex) {}
    private static final Pattern LINE = Pattern.compile(
            "^([\\p{L}][\\p{L}\\p{M} ,.'’–—-]{1,79}?)\\s+([♩♪♫♬•●J√])([.·]{0,2})\\s*=\\s*([0-9]{2,3})\\s*$");
    private PrintedTempoCaption() {}
    static Caption from(PlayingTechniqueDetector.Word word, ScorePageInterpretation score) {
        if (word == null || score == null || word.text() == null
                || !Float.isFinite(word.left()) || !Float.isFinite(word.right())
                || !Float.isFinite(word.top()) || !Float.isFinite(word.bottom())
                || word.left() < 0 || word.right() > 1 || word.top() < 0 || word.bottom() > 1
                || word.left() >= word.right() || word.top() >= word.bottom()) return null;
        var match = LINE.matcher(word.text().trim());
        if (!match.matches()) return null;
        String text = match.group(1).trim();
        if (text.split("\\s+").length > 8) return null;
        int rate = Integer.parseInt(match.group(4));
        if (rate < 30 || rate > 400) return null;
        double writtenUnit = switch (match.group(2)) {
            case "♩" -> 1;
            case "♪", "♫" -> .5;
            case "♬" -> .25;
            default -> Double.NaN; // OCR dots/stems need the existing optical tempo proof.
        };
        writtenUnit *= 2 - Math.pow(.5, match.group(3).length());
        Caption result = null;
        for (var tempo : score.tempoChanges()) {
            int m = tempo.measureIndex();
            if (m < 0 || m >= score.measures().size()
                    || Math.abs(tempo.bpm() - rate * tempo.beatUnit()) > 1e-6
                    || Double.isFinite(writtenUnit) && Math.abs(tempo.beatUnit() - writtenUnit) > 1e-6) continue;
            var region = score.measures().get(m);
            float x = region.left() + tempo.positionInMeasure() * (region.right() - region.left());
            float reach = Math.max(.01f, region.bottom() - region.top()) * 3;
            if (x < word.left() - 1e-6f || x > word.right() + 1e-6f
                    || word.bottom() >= region.bottom() || word.top() < region.top() - reach) continue;
            if (result != null) return null;
            result = new Caption(text, m);
        }
        return result;
    }
}
