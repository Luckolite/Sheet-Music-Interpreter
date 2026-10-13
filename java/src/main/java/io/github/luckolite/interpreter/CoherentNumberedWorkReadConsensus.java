// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Two readers must independently repeat a complete numbered caption on a full crop axis. */
public final class CoherentNumberedWorkReadConsensus {
    private CoherentNumberedWorkReadConsensus() {}

    private static String clean(String text) {
        return text.strip().replaceAll("\\h+", " ");
    }

    private static String key(String text) {
        return clean(text).toLowerCase(Locale.ROOT);
    }

    private static boolean valid(List<ScoreHeaderReadConsensus.Reading> reads) {
        if (reads == null || reads.size() != 12) return false;
        var seen = new HashSet<String>();
        for (var r : reads) {
            if (r == null
                    || r.text() == null
                    || r.text().length() > 1000
                    || r.text()
                            .codePoints()
                            .anyMatch(
                                    c ->
                                            Character.isISOControl(c)
                                                    || Character.getType(c) == Character.FORMAT
                                                    || c == 0x2028
                                                    || c == 0x2029)
                    || !Double.isFinite(r.confidence())
                    || r.confidence() < 0
                    || r.confidence() > 1
                    || !Set.of(1, 2, 4).contains(r.scale())
                    || r.mode() == null
                    || !Set.of("tight", "horizontal").contains(r.mode())
                    || !(r.widthFactor() == 1 || r.widthFactor() == 1.5)
                    || !seen.add(r.scale() + ":" + r.mode() + ":" + r.widthFactor())) return false;
        }
        return true;
    }

    private static Map<String, String> axes(
            List<ScoreHeaderReadConsensus.Reading> reads, boolean primary) {
        var out = new LinkedHashMap<String, String>();
        for (String axis : List.of("tight", "horizontal", "1.0", "1.5")) {
            var subset =
                    reads.stream()
                            .filter(
                                    r ->
                                            r.mode().equals(axis)
                                                    || Double.toString(r.widthFactor())
                                                            .equals(axis))
                            .toList();
            if (subset.size() != 6
                    || subset.stream().map(r -> key(r.text())).distinct().count() != 1
                    || subset.stream().anyMatch(r -> r.confidence() < (primary ? .9 : .5)))
                continue;
            String literal = clean(subset.get(0).text());
            if (!MusicMetadataCatalogue.preservesCaptionIdentifier(literal, literal)) continue;
            out.putIfAbsent(key(literal), literal);
        }
        return out;
    }

    public static String select(
            List<ScoreHeaderReadConsensus.Reading> medium,
            List<ScoreHeaderReadConsensus.Reading> secondary) {
        if (!valid(medium) || !valid(secondary)) return "";
        var primary = axes(medium, true);
        var corroboration = axes(secondary, false);
        // One strong sample cannot defeat a complete axis. Two coherent literal
        // alternatives are ambiguous even when the public catalogue prefers one.
        if (primary.size() != 1 || corroboration.size() != 1) return "";
        String k = primary.keySet().iterator().next();
        return corroboration.containsKey(k) ? primary.get(k) : "";
    }
}
