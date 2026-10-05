// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Conservative evidence from OCR of a verified, horizontally reflowed signature crop. */
public final class MeterOcrEvidence {
    private static final Pattern FRACTION_TOKEN = Pattern.compile("[0-9/|]+");
    private static final Pattern COMPLETE_FRACTION = Pattern.compile("[0-9]{1,2}[/|][0-9]{1,2}");
    private static final Pattern PUNCTUATION_TOKEN = Pattern.compile("[/|.,:]+");
    private static final Pattern ATTACHED_DENOMINATOR = Pattern.compile("[/|][0-9]{1,2}");
    private static final Pattern DIGITS = Pattern.compile("[0-9]{1,2}");
    private static final Pattern FRACTION_SEPARATOR = Pattern.compile("[/|]");

    private MeterOcrEvidence() {}

    public record Token(String text, int left, int top, int right, int bottom) {}

    /** Match the staff cleaner's ink decision before adding reconstructed black strokes. */
    public static boolean ink(int luminance) {
        return luminance >= 0 && luminance < 135;
    }

    public static String horizontalFraction(List<Token> input) {
        var tokens = new ArrayList<Token>();
        for (Token token : input)
            if (token != null
                    && token.text() != null
                    && token.right() > token.left()
                    && token.bottom() > token.top()
                    && FRACTION_TOKEN.matcher(token.text().trim()).matches()) tokens.add(token);
        tokens.sort(Comparator.comparingInt(Token::left));
        var choices = new HashSet<String>();
        for (int i = 0; i < tokens.size(); i++) {
            Token previous = tokens.get(i);
            String text = previous.text().trim();
            addFraction(choices, text);
            // OCR can split a printed fraction into '3', '/8' or '3', '/', '8'.
            for (int j = i + 1; j < Math.min(tokens.size(), i + 3); j++) {
                Token next = tokens.get(j);
                int overlap =
                        Math.min(previous.bottom(), next.bottom())
                                - Math.max(previous.top(), next.top());
                int height =
                        Math.min(previous.bottom() - previous.top(), next.bottom() - next.top());
                int gap = next.left() - previous.right();
                if (gap < 0 || gap > height * 1.5f || overlap < height * .65f) break;
                text += next.text().trim();
                addFraction(choices, text);
                previous = next;
            }
        }
        return choices.size() == 1 ? choices.iterator().next() : "";
    }

    private static void addFraction(HashSet<String> choices, String text) {
        // Never guess a slash from a '1', or turn arbitrary letters into digits.
        if (!COMPLETE_FRACTION.matcher(text).matches()) return;
        String[] parts = FRACTION_SEPARATOR.split(text, 0);
        int numerator = Integer.parseInt(parts[0]), denominator = Integer.parseInt(parts[1]);
        if (numerator >= 1
                && numerator <= 32
                && denominator >= 1
                && denominator <= 32
                && (denominator & (denominator - 1)) == 0)
            choices.add(numerator + "/" + denominator);
    }

    /** Read digits from known numerator/denominator slots in a generated horizontal crop. */
    public static String reflowedFraction(
            List<Token> tokens, int numeratorEnd, int denominatorStart) {
        if (tokens == null || numeratorEnd <= 0 || denominatorStart <= numeratorEnd) return "";
        var upper = new HashSet<String>();
        var lower = new HashSet<String>();
        for (var token : tokens) {
            if (token == null
                    || token.text() == null
                    || token.right() <= token.left()
                    || token.bottom() <= token.top()) continue;
            String text = token.text().trim();
            if (PUNCTUATION_TOKEN.matcher(text).matches()) continue;
            float center = token.left() * .5f + token.right() * .5f;
            // OCR may attach the drawn separator to the denominator, e.g. '/8'.
            if (center > denominatorStart && ATTACHED_DENOMINATOR.matcher(text).matches())
                text = text.substring(1);
            if (!DIGITS.matcher(text).matches()) return "";
            if (center < numeratorEnd) upper.add(text);
            else if (center > denominatorStart) lower.add(text);
            else return "";
        }
        return upper.size() == 1 && lower.size() == 1
                ? singleReading(List.of(upper.iterator().next() + "/" + lower.iterator().next()))
                : "";
    }

    /** One scale may be corroborated by another staff, but must still be a valid meter. */
    public static String singleReading(List<String> readings) {
        if (readings == null || readings.size() != 1 || readings.get(0) == null) return "";
        var valid = new HashSet<String>();
        addFraction(valid, readings.get(0));
        return valid.size() == 1 ? valid.iterator().next() : "";
    }

    /** Require two agreeing renderings and reject any competing valid reading. */
    public static String consensus(List<String> readings) {
        String result = "";
        int votes = 0;
        for (String reading : readings) {
            if (reading == null || reading.isBlank()) continue;
            var valid = new HashSet<String>();
            addFraction(valid, reading);
            if (valid.size() != 1) continue;
            String meter = valid.iterator().next();
            if (!result.isEmpty() && !result.equals(meter)) return "";
            result = meter;
            votes++;
        }
        return votes >= 2 ? result : "";
    }

    /** Three matching renderings are decisive; remaining scale variants cannot improve confidence. */
    public static String decisiveConsensus(List<String> readings) {
        String result = "";
        int votes = 0;
        for (String reading : readings) {
            if (reading == null || reading.isBlank()) continue;
            var valid = new HashSet<String>();
            addFraction(valid, reading);
            if (valid.size() != 1) continue;
            String meter = valid.iterator().next();
            if (!result.isEmpty() && !result.equals(meter)) return "";
            result = meter;
            votes++;
        }
        return votes >= 3 ? result : "";
    }
}
