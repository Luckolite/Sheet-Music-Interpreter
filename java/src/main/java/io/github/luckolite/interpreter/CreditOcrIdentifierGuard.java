// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** A stable alternate OCR reading cannot replace printed work numbers. */
public final class CreditOcrIdentifierGuard {
    private CreditOcrIdentifierGuard() {}

    private static final Pattern DIGITS = Pattern.compile("\\p{Nd}+");
    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final Pattern ROMAN =
            Pattern.compile("M{0,4}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})");

    private static List<String> numbers(String text) {
        var out = new ArrayList<String>();
        var m = DIGITS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC));
        while (m.find()) out.add(m.group());
        return out;
    }

    private static List<String> roman(String text) {
        var out = new ArrayList<String>();
        var m = WORD.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC));
        while (m.find()) {
            var word = m.group().toUpperCase(Locale.ROOT);
            if (word.length() >= 2 && ROMAN.matcher(word).matches()) out.add(word);
        }
        return out;
    }

    public static boolean preservesIdentifiers(String original, String candidate) {
        return original != null
                && candidate != null
                && numbers(original).equals(numbers(candidate))
                && roman(original).equals(roman(candidate));
    }
}
