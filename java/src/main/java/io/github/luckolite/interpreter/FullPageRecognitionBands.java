// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.text.Normalizer;
import io.github.luckolite.interpreter.ScoreCreditsDetector.Line;

/** Finite fallback layout. Bands retain every original pixel and original coordinates. */
public final class FullPageRecognitionBands {
    private FullPageRecognitionBands() {}

    public record Band(int top, int bottom) {}

    public static boolean eligible(int width, int height, float left, float top, float scale) {
        return left == 0
                && top == 0
                && scale == 1
                && width >= 900
                && height >= 1600
                && height <= 4000
                && (long) width * height <= 10000000;
    }

    public static List<Band> layout(int height) {
        if (height < 1600 || height > 4000)
            throw new IllegalArgumentException("Unsupported full page height");
        int middle = height / 2;
        return List.of(new Band(0, middle + 64), new Band(middle - 64, height));
    }

    private static String key(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ");
    }

    public static List<Line> merge(List<Line> first, List<Line> second) {
        List<Line> output = new ArrayList<>(first);
        for (Line candidate : second) {
            boolean duplicate = false;
            for (Line existing : output) {
                if (!key(existing.text()).equals(key(candidate.text()))) continue;
                float width =
                        Math.min(
                                existing.right() - existing.left(),
                                candidate.right() - candidate.left());
                float height =
                        Math.min(
                                existing.bottom() - existing.top(),
                                candidate.bottom() - candidate.top());
                if (width > 0
                        && height > 0
                        && Math.min(existing.right(), candidate.right())
                                        - Math.max(existing.left(), candidate.left())
                                > width * .8f
                        && Math.min(existing.bottom(), candidate.bottom())
                                        - Math.max(existing.top(), candidate.top())
                                > height * .8f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) output.add(candidate);
        }
        output.sort(Comparator.comparingDouble(Line::top).thenComparingDouble(Line::left));
        return List.copyOf(output);
    }
}
