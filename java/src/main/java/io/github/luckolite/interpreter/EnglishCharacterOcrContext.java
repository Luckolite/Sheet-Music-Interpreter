// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reject explicit local prose ownership before OCR lines lose their parent context. */
final class EnglishCharacterOcrContext {
    private static final Pattern CHARACTER =
            Pattern.compile(
                    "\\s*(?:flowing|triumphantly|ethereal|with\\s+driving\\s+intensity|like\\s+an\\s+echo)[.!]?\\s*");
    private static final Pattern PROSE =
            Pattern.compile(
                    "^(?:music\\s+by\\b|(?:composed|arranged|written|lyrics)\\s+by\\b|copyright\\b|publisher\\b|published\\s+by\\b|verse\\s+\\d+\\b|chorus\\s*[:\\d]).*",
                    Pattern.DOTALL);

    private static String normalized(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    static boolean character(String s) {
        return s != null && CHARACTER.matcher(normalized(s)).matches();
    }

    static boolean admitted(PlayingTechniqueDetector.Word word) {
        return word != null
                && (!character(word.text())
                        || word.admission()
                                == PlayingTechniqueDetector.WordAdmission.CONTEXT_QUALIFIED);
    }

    static boolean qualifies(OcrText text, OcrText.Block owner, OcrText.Line candidate) {
        // Detection boxes are not paragraphs, and absence of prose is not evidence of a musical
        // role.
        if (text.grouping() != OcrText.Grouping.ORIGINAL_BLOCKS
                || ownedByProse(text, owner, candidate)) return false;
        var a = candidate.box();
        if (!valid(a)) return false;
        for (var line : owner.lines()) {
            if (line == candidate
                    || !normalized(line.text()).matches("(?:ppp|pp|p|mp|mf|f|ff|fff)")) continue;
            var b = line.box();
            if (!valid(b)) continue;
            int overlap = Math.min(a.right, b.right) - Math.max(a.left, b.left);
            int gap = Math.max(0, Math.max(a.top, b.top) - Math.min(a.bottom, b.bottom));
            int h = Math.max(a.height(), b.height());
            boolean aligned =
                    Math.abs(a.left - b.left) <= h
                            || Math.abs(a.exactCenterX() - b.exactCenterX()) <= h;
            if (overlap > 0 && aligned && gap <= h * .5f) return true;
        }
        return false;
    }

    static boolean ownedByProse(OcrText text, OcrText.Block owner, OcrText.Line candidate) {
        if (!character(candidate.text())) return false;
        var a = candidate.box();
        if (!valid(a)) return false;
        for (var block : text.blocks()) {
            if (block != owner && text.grouping() == OcrText.Grouping.UNKNOWN) continue;
            for (var line : block.lines()) {
                if (line == candidate || !PROSE.matcher(normalized(line.text())).matches())
                    continue;
                var b = line.box();
                if (!valid(b)) continue;
                // A nearby aligned sibling carries explicit prose ownership; a distant line or
                // column does not.
                int overlap = Math.min(a.right, b.right) - Math.max(a.left, b.left);
                int gap = Math.max(0, Math.max(a.top, b.top) - Math.min(a.bottom, b.bottom));
                int h = Math.max(a.height(), b.height());
                boolean aligned =
                        Math.abs(a.left - b.left) <= h
                                || Math.abs(a.exactCenterX() - b.exactCenterX()) <= h;
                if (overlap >= Math.min(a.width(), b.width()) * .6f && aligned && gap <= h * .5f)
                    return true;
            }
        }
        return false;
    }

    private static boolean valid(OcrText.Box b) {
        return b != null && b.width() > 0 && b.height() > 0;
    }
}
