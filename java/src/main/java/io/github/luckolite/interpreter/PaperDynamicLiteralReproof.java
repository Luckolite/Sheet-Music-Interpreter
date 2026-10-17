// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Explicit reproof of one existing literal; acquisition dedup competitors remain intact. */
final class PaperDynamicLiteralReproof {
    @FunctionalInterface
    interface Reader {
        OcrText read(PaperDynamicWord.Crop crop, int scale) throws Exception;
    }

    record Proof(
            PlayingTechniqueDetector.Word word,
            int seedIndex,
            PlayingTechniqueDetector.Word exactSeed,
            PaperDynamicWord.Crop crop,
            OcrText twice,
            OcrText thrice) {}

    static Proof verify(
            List<PlayingTechniqueDetector.Word> existing,
            int seedIndex,
            PlayingTechniqueDetector.Word expected,
            Reader reader,
            byte[] gray,
            int width,
            int height)
            throws Exception {
        if (existing == null
                || seedIndex < 0
                || seedIndex >= existing.size()
                || reader == null
                || expected == null) return null;
        var seed = existing.get(seedIndex);
        if (seed == null
                || seed.text() == null
                || expected.text() == null
                || !same(seed, expected)
                || !seed.text().matches("ppp|pp|p|mp|mf|f|ff|fff")) return null;
        var competitors = new ArrayList<>(existing);
        competitors.remove(
                seedIndex); // Exactly one indexed seed, never all equal/overlapping words.
        if (!PaperDynamicWord.unclaimed(seed, competitors)) return null;
        var crop = PaperDynamicWord.crop(seed, width, height);
        if (crop == null) return null;
        var twice = reader.read(crop, 2);
        var thrice = reader.read(crop, 3);
        var agreed = PaperDynamicWord.agree(seed, twice, thrice, crop, width, height, competitors);
        if (agreed == null
                || !agreed.text().equals(seed.text())
                || !ScoreDynamicsDetector.containsInk(agreed, gray, width, height)) return null;
        return new Proof(agreed, seedIndex, seed, crop, twice, thrice);
    }

    private static boolean same(PlayingTechniqueDetector.Word a, PlayingTechniqueDetector.Word b) {
        return a.text().equals(b.text())
                && Float.floatToIntBits(a.left()) == Float.floatToIntBits(b.left())
                && Float.floatToIntBits(a.top()) == Float.floatToIntBits(b.top())
                && Float.floatToIntBits(a.right()) == Float.floatToIntBits(b.right())
                && Float.floatToIntBits(a.bottom()) == Float.floatToIntBits(b.bottom());
    }
}
