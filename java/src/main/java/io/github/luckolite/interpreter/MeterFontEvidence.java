// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.LinkedHashSet;
import java.util.Set;

/** Retains contradictory OCR observations before considering a font-only fallback. */
public final class MeterFontEvidence {
    private final Set<String> upper = new LinkedHashSet<>(), lower = new LinkedHashSet<>();
    private boolean veto;

    public void numerator(String value) {
        if (value != null && value.matches("[0-9]{1,2}")) upper.add(value);
    }

    public void denominator(String value) {
        if (value != null && value.matches("[0-9]{1,2}")) lower.add(value);
    }

    public void fraction(String value) {
        if (value == null || !value.matches("[0-9]{1,2}/[0-9]{1,2}")) return;
        String[] parts = value.split("/");
        numerator(parts[0]);
        denominator(parts[1]);
    }

    public void veto() {
        veto = true;
    }

    public boolean permits(String value) {
        if (veto || value == null || !value.matches("[0-9]{1,2}/(?:2|4|8|16|32)")) return false;
        String[] parts = value.split("/");
        return (upper.isEmpty() || upper.size() == 1 && upper.contains(parts[0]))
                && (lower.isEmpty() || lower.size() == 1 && lower.contains(parts[1]));
    }
}
