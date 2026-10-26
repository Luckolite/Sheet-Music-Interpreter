// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Literal key-text evidence, independent of score rendering and filename storage. */
public final class ScoreKeyTextHints {
    private static final String PITCH = "[A-G](?:(?:#|b|\\u266f|\\u266d)|\\h+(?:sharp|flat))?";
    private static final String NAMED = PITCH + "\\h+(?:major|minor|maj|min)";
    private static final Pattern LABELED =
            Pattern.compile(
                    "(?im)\\b(?:key(?:\\h+of)?|tonality)\\b\\h*[:=\\-]?\\h*("
                            + PITCH
                            + "(?:\\h*(?:major|minor|maj|min|m))?)\\b");
    private static final Pattern PHRASED =
            Pattern.compile("(?im)\\b(?:in(?:\\h+the)?\\h+key\\h+of|in)\\h+(" + NAMED + ")\\b");
    private static final Pattern STANDALONE = Pattern.compile("(?im)^\\h*(" + NAMED + ")\\h*$");
    // An uppercase terminal pitch is a conventional title shorthand. A lowercase English
    // article, or a pitch-like prefix followed by ordinary words, is not key evidence.
    private static final Pattern MAJOR_SHORTHAND =
            Pattern.compile(
                    "(?m)(?i:\\b(?:in|key\\h+of))\\h+([A-G](?:#|b|\\u266f|\\u266d)?)"
                            + "(?=\\h*(?:$|[,.;:)\\]–—|]|-(?:\\h|$)))");

    private ScoreKeyTextHints() {}

    /** Returns the literal key spelling; callers retain their established normalization. */
    public static String printedKey(String text) {
        for (Pattern pattern : new Pattern[] {LABELED, PHRASED, STANDALONE}) {
            String key = match(pattern, text);
            if (!key.isBlank()) return key;
        }
        return majorShorthand(text);
    }

    public static String majorShorthand(String text) {
        String pitch = match(MAJOR_SHORTHAND, text);
        return pitch.isBlank() ? "" : pitch + " Major";
    }

    /** Printed evidence belongs to the document and takes precedence over a filename guess. */
    public static String preferPrinted(String printedText, String titleHint) {
        String printed = printedKey(printedText);
        return printed.isBlank() ? titleHint == null ? "" : titleHint : printed;
    }

    private static String match(Pattern pattern, String text) {
        if (text == null) return "";
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}
