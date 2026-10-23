// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.regex.*;

/** Complete sudden dynamic labels; no prose search, geometry or musical timing. */
public final class SuddenDynamicText {
    public record Mark(String level, String qualifier, boolean before) {}

    private static final String LEVEL = "(ppp|pp|p|mp|mf|fff|ff|f)";
    private static final Pattern PREFIX =
            Pattern.compile(
                    "^(subito\\.?|sub\\.?)\\s*" + LEVEL + "[.,:;]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUFFIX =
            Pattern.compile(
                    "^" + LEVEL + "\\s+(subito\\.?|sub\\.?)[,:;]?$", Pattern.CASE_INSENSITIVE);

    private SuddenDynamicText() {}

    public static Optional<Mark> parse(String text) {
        if (text == null) return Optional.empty();
        var prefix = PREFIX.matcher(text.trim());
        if (prefix.matches())
            return Optional.of(
                    new Mark(prefix.group(2).toLowerCase(Locale.ROOT), prefix.group(1), true));
        var suffix = SUFFIX.matcher(text.trim());
        return suffix.matches()
                ? Optional.of(
                        new Mark(suffix.group(1).toLowerCase(Locale.ROOT), suffix.group(2), false))
                : Optional.empty();
    }
}
