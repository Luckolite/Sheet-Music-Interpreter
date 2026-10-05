// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Agreement checks shared by platform-independent musical OCR consumers. */
final class MusicalOcrEvidence {
    private static final Pattern FONT_METER = Pattern.compile("[0-9]{1,2}/(?:2|4|8|16|32)");
    private static final Pattern ORNAMENT_TOKEN = Pattern.compile("(?:tr|[pd]ort?)[.,]?");
    private static final Pattern NEGATED_DIRECTION = Pattern.compile(".*\\b(non|senza)\\b.*");
    private static final Pattern COMPOUND_DIRECTION = Pattern.compile(".*\\s+.*");

    private MusicalOcrEvidence() {}

    static String fontMeter(
            String text, float upperScore, float lowerScore, Set<String> upper, Set<String> lower) {
        if (text == null
                || !FONT_METER.matcher(text).matches()
                || upperScore < .84f
                || lowerScore < .84f) return "";
        String[] parts = text.split("/");
        return (upper.isEmpty() || upper.contains(parts[0]))
                        && (lower.isEmpty() || lower.contains(parts[1]))
                ? text
                : "";
    }

    static String ornamentToken(String value) {
        if (value == null) return "";
        String token = value.trim().toLowerCase(Locale.ROOT);
        return ORNAMENT_TOKEN.matcher(token).matches()
                ? (token.startsWith("tr") ? "tr" : "port")
                : "";
    }

    static void addDirectionWords(
            OcrText text,
            java.util.List<PlayingTechniqueDetector.Word> words,
            float scale,
            int top,
            int width,
            int height) {
        for (var block : text.getTextBlocks())
            for (var line : block.getLines()) {
                if (NEGATED_DIRECTION
                        .matcher(line.getText().toLowerCase(java.util.Locale.ROOT))
                        .matches()) continue;
                if (ScoreNavigationDetector.kind(line.getText()) != null) {
                    var box = line.getBoundingBox();
                    if (box != null)
                        words.add(
                                new PlayingTechniqueDetector.Word(
                                        line.getText(),
                                        box.left / scale / width,
                                        (top + box.top / scale) / height,
                                        box.right / scale / width,
                                        (top + box.bottom / scale) / height));
                }
                if (!ExpressiveDirectionText.parse(line.getText()).isEmpty()) {
                    var box = line.getBoundingBox();
                    if (box != null)
                        words.add(
                                new PlayingTechniqueDetector.Word(
                                        line.getText(),
                                        box.left / scale / width,
                                        (top + box.top / scale) / height,
                                        box.right / scale / width,
                                        (top + box.bottom / scale) / height));
                    continue;
                }
                // Keep a compound dynamic/expression line at its printed starting slot.
                // The expression element alone begins farther right than the affected head.
                if (PlayingTechniqueDetector.technique(line.getText()) >= 0
                        && COMPOUND_DIRECTION.matcher(line.getText().trim()).matches()) {
                    var box = line.getBoundingBox();
                    if (box != null)
                        words.add(
                                new PlayingTechniqueDetector.Word(
                                        line.getText(),
                                        box.left / scale / width,
                                        (top + box.top / scale) / height,
                                        box.right / scale / width,
                                        (top + box.bottom / scale) / height));
                    continue;
                }
                for (var element : line.getElements()) {
                    if (PlayingTechniqueDetector.technique(element.getText()) < 0
                            && OctaveMarkDetector.shift(element.getText()) == 0
                            && ScoreNavigationDetector.kind(element.getText()) == null
                            && !FingeringAnnotationFilter.isFingering(element.getText())) continue;
                    var box = element.getBoundingBox();
                    if (box == null) continue;
                    words.add(
                            new PlayingTechniqueDetector.Word(
                                    element.getText(),
                                    box.left / scale / width,
                                    (top + box.top / scale) / height,
                                    box.right / scale / width,
                                    (top + box.bottom / scale) / height));
                }
            }
    }
}
