// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Recovers only word spaces from two stable ink projections of tracked uppercase text. */
public final class TrackedHeadingWordSpaces {
    private TrackedHeadingWordSpaces() {}

    public static String refine(String recognized, int[] pixels, int width, int height) {
        if (recognized == null) return null;
        String symbols = recognized.replaceAll("\\h+", "");
        int[] letters = symbols.codePoints().toArray();
        if (width < 1
                || height < 12
                || (long) width * height > 20_000_000
                || pixels == null
                || pixels.length != (long) width * height
                || letters.length < 5
                || letters.length > 60
                || symbols.codePoints().filter(Character::isLetter).count() < 5
                || !symbols.matches("[\\p{Lu}'’]+")) return recognized;
        for (int i = 0; i < letters.length; i++)
            if (!Character.isLetter(letters[i])
                    && (i == 0
                            || i == letters.length - 1
                            || !Character.isLetter(letters[i - 1])
                            || !Character.isLetter(letters[i + 1]))) return recognized;
        String selected = null;
        for (boolean light : new boolean[] {false, true}) {
            String first = project(letters, pixels, width, height, light, light ? 220 : 100);
            if (first == null) continue;
            String second = project(letters, pixels, width, height, light, light ? 235 : 140);
            if (!first.equals(second)) continue;
            if (selected != null && !selected.equals(first)) return recognized;
            selected = first;
        }
        return selected == null ? recognized : selected;
    }

    private static String project(
            int[] letters, int[] pixels, int width, int height, boolean light, int threshold) {
        boolean[] occupied = new boolean[width];
        int minimum = Math.max(2, (int) Math.ceil(height * .04));
        int ink = 0;
        for (int x = 0; x < width; x++) {
            int count = 0;
            for (int y = 0; y < height; y++)
                if (ink(pixels[y * width + x], light, threshold)) count++;
            ink += count;
            occupied[x] = count >= minimum;
        }
        if (ink > (long) width * height * .45 || ink < (long) width * height * .015) return null;
        List<int[]> spans = new ArrayList<>();
        int start = -1, bridge = Math.max(1, (int) Math.floor(height * .06));
        for (int x = 0; x <= width; x++) {
            if (x < width && occupied[x]) {
                if (start < 0) start = x;
            } else if (start >= 0) {
                if (!spans.isEmpty() && start - spans.get(spans.size() - 1)[1] <= bridge)
                    spans.get(spans.size() - 1)[1] = x;
                else spans.add(new int[] {start, x});
                start = -1;
            }
        }
        if (spans.size() != letters.length) return null;
        int[] gaps = new int[spans.size() - 1];
        for (int i = 0; i < spans.size(); i++) {
            int[] span = spans.get(i);
            int rows = 0;
            for (int y = 0; y < height; y++) {
                boolean hit = false;
                for (int x = span[0]; x < span[1] && !hit; x++)
                    hit = ink(pixels[y * width + x], light, threshold);
                if (hit) rows++;
            }
            if (Character.isLetter(letters[i])
                    && (rows < height * .55 || span[1] - span[0] < height * .10)) return null;
            if (span[1] - span[0] > height * 1.5) return null;
            if (i < gaps.length) gaps[i] = spans.get(i + 1)[0] - span[1];
        }
        int[] sorted = gaps.clone();
        Arrays.sort(sorted);
        int split = -1;
        double largest = 1.6;
        // Require at least three ordinary tracking gaps to establish the letter spacing.
        for (int i = 2; i < sorted.length - 1; i++) {
            double ratio = (double) sorted[i + 1] / Math.max(1, sorted[i]);
            if (ratio > largest) {
                largest = ratio;
                split = i;
            }
        }
        if (split < 0
                || sorted[split] < height * .15
                || sorted[split] > height * 1.1
                || sorted[split + 1] < height * .5
                || sorted[split + 1] - sorted[split] < height * .25
                || sorted[sorted.length - 1] > height * 2.5) return null;
        int boundary = sorted[split];
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < letters.length; i++) {
            result.appendCodePoint(letters[i]);
            if (i < gaps.length && gaps[i] > boundary) {
                if (!Character.isLetter(letters[i]) || !Character.isLetter(letters[i + 1]))
                    return null;
                result.append(' ');
            }
        }
        return result.toString();
    }

    private static boolean ink(int argb, boolean light, int threshold) {
        if ((argb >>> 24) < 240) return false;
        int r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
        int minimum = Math.min(r, Math.min(g, b)), maximum = Math.max(r, Math.max(g, b));
        return light ? minimum >= threshold && maximum - minimum < 24 : maximum <= threshold;
    }
}
