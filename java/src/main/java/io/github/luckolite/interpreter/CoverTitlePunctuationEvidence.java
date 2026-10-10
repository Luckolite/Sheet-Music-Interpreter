// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.text.Normalizer;
import java.util.*;

/** Original-PDF consensus may add one printed terminal title apostrophe only. */
public final class CoverTitlePunctuationEvidence {
    private CoverTitlePunctuationEvidence() {}

    public record Reading(
            int sourceMultiplier, String text, int[] originalPixels, int width, int height) {
        public Reading(int multiplier, String text) {
            this(multiplier, text, null, 0, 0);
        }
    }

    private static String key(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .strip()
                .replaceAll("\\h+", " ");
    }

    public static boolean isOnePrintedApostropheAddition(String before, String after) {
        if (before == null || after == null) return false;
        String a = key(before), b = key(after);
        if (b.contains("''") || !b.endsWith("'")) return false;
        int terminal = b.length() - 1;
        if (!b.substring(0, terminal).equals(a)) return false;
        int letters = 0, j = terminal;
        while (j > 0 && Character.isLetter(b.codePointBefore(j))) {
            j -= Character.charCount(b.codePointBefore(j));
            letters++;
        }
        return letters >= 3;
    }

    public static boolean hasUpperTrailingInk(
            ScoreCreditsDetector.Line prior, CoverTitleRegions.Region region, Reading reading) {
        if (prior == null || region == null || reading == null) return false;
        int scale = reading.sourceMultiplier(), width = reading.width(), height = reading.height();
        int[] pixels = reading.originalPixels();
        if (!Set.of(1, 2, 4).contains(scale)
                || pixels == null
                || width < 8
                || height < 12
                || (long) width * height != pixels.length
                || pixels.length > 8000000
                || width != (region.right() - region.left()) * scale
                || height != (region.bottom() - region.top()) * scale) return false;
        if (region.threshold() != 1001 && region.threshold() != 1002) return false;
        float h = prior.height() * scale, priorTop = (prior.top() - region.top()) * scale;
        int start = (int) Math.ceil((prior.right() - region.left()) * scale);
        if (start <= 0 || start >= width - 3 * scale) return false;
        int left = width, right = -1, top = height, bottom = -1, count = 0;
        for (int y = 0; y < height; y++)
            for (int x = start; x < width; x++) {
                int p = pixels[y * width + x], r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                int gray = (r * 299 + g * 587 + b * 114) / 1000;
                boolean ink =
                        gray >= 160
                                && (region.threshold() == 1001
                                        ? r > b + 25 && g > b + 15
                                        : b > r + 15 && g > r + 8);
                if (!ink) continue;
                count++;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
            }
        int w = right - left + 1, hh = bottom - top + 1;
        return count >= 12 * scale * scale
                && w >= 3 * scale
                && w <= h * .35f
                && hh >= h * .08f
                && hh <= h * .45f
                && top >= priorTop - h * .05f
                && top <= priorTop + h * .35f
                && bottom <= priorTop + h * .6f
                && right < width - 2 * scale
                && count >= w * hh * .15f;
    }

    public static ScoreCreditsDetector.Line refine(
            ScoreCreditsDetector.Page page,
            ScoreCreditsDetector.Line prior,
            CoverTitleRegions.Region region,
            List<Reading> readings) {
        if (!ScoreCreditsDetector.locatedCoverHeadingCandidates(page).contains(prior)
                || region == null
                || readings == null
                || readings.size() != 3) return null;
        if (region.threshold() != 1001 && region.threshold() != 1002) return null;
        Set<Integer> scales = new HashSet<>();
        String candidate = null;
        for (var reading : readings) {
            if (reading == null
                    || reading.text() == null
                    || !scales.add(reading.sourceMultiplier())
                    || !hasUpperTrailingInk(prior, region, reading)) return null;
            if (candidate == null) candidate = reading.text();
            else if (!key(candidate).equals(key(reading.text()))) return null;
        }
        if (!scales.equals(Set.of(1, 2, 4))
                || !isOnePrintedApostropheAddition(prior.text(), candidate)) return null;
        float h = prior.height(), w = prior.right() - prior.left();
        if (region.left() < 0
                || region.top() < 0
                || region.right() > page.width()
                || region.bottom() > page.height()
                || Math.abs(region.left() - prior.left()) > h * .25f
                || Math.abs(region.right() - prior.right()) > h * .35f
                || Math.abs(region.top() - prior.top()) > h * .25f
                || Math.abs(region.bottom() - prior.bottom()) > h * .25f
                || region.right() - region.left() < w * .9f) return null;
        return new ScoreCreditsDetector.Line(
                candidate.strip(), prior.left(), prior.top(), prior.right(), prior.bottom());
    }
}
