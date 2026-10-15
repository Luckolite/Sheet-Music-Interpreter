// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Independent font, reflowed OCR and physically separate staff witnesses. */
final class MeterReflowEvidence {
    record Witness(int measure, MeterChangeDetector.Crop crop, String text) {}

    static boolean eligible(
            String font, float top, float bottom, Set<String> upper, Set<String> lower) {
        if (!Float.isFinite(top)
                || !Float.isFinite(bottom)
                || top < .90f
                || bottom < .90f
                || MeterOcrEvidence.singleReading(List.of(font)).isBlank()) return false;
        String[] parts = font.split("/");
        boolean invalidUpper = false;
        for (String text : upper) {
            if (!text.matches("[0-9]{1,2}")) return false;
            int n = Integer.parseInt(text);
            if (n >= 1 && n <= 32) {
                if (!text.equals(parts[0])) return false;
            } else invalidUpper = true;
        }
        for (String text : lower) if (!text.equals(parts[1])) return false;
        return invalidUpper;
    }

    static Map<Integer, String> corroborated(List<Witness> witnesses) {
        Map<Integer, Set<String>> all = new TreeMap<>(), matched = new TreeMap<>();
        for (var w : witnesses)
            all.computeIfAbsent(w.measure(), k -> new HashSet<>()).add(w.text());
        for (int i = 0; i < witnesses.size(); i++)
            for (int j = i + 1; j < witnesses.size(); j++) {
                var a = witnesses.get(i);
                var b = witnesses.get(j);
                if (a.measure() != b.measure() || !a.text().equals(b.text())) continue;
                var x = a.crop();
                var y = b.crop();
                float gap = Math.max(x.gap(), y.gap());
                if (!Float.isFinite(gap)
                        || gap < 3
                        || Math.min(x.gap(), y.gap()) < gap * .7f
                        || Math.abs(x.firstLine() - y.firstLine()) < gap * 4.5f) continue;
                int overlap = Math.min(x.right(), y.right()) - Math.max(x.left(), y.left());
                if (overlap < Math.min(x.right() - x.left(), y.right() - y.left()) * .5f) continue;
                matched.computeIfAbsent(a.measure(), k -> new HashSet<>()).add(a.text());
            }
        Map<Integer, String> result = new TreeMap<>();
        for (var entry : matched.entrySet())
            if (entry.getValue().size() == 1 && all.get(entry.getKey()).size() == 1)
                result.put(entry.getKey(), entry.getValue().iterator().next());
        return result;
    }
}
