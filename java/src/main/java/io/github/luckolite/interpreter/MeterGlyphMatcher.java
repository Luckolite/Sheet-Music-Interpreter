// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative fallback for stacked music-font numerals that ordinary text OCR omits. */
public final class MeterGlyphMatcher {
    public record Reading(String text, float topScore, float bottomScore) {}

    private record Match(int value, float score) {}

    public record Glyph(int width, int height, boolean[] ink) {}

    @FunctionalInterface
    public interface Rasterizer {
        Glyph render(String glyphs, int size);
    }

    private final Rasterizer rasterizer;

    public MeterGlyphMatcher(Rasterizer rasterizer) {
        this.rasterizer = java.util.Objects.requireNonNull(rasterizer);
    }

    public Reading read(int[] pixels, int width, int height, int middle, int firstLine, float gap) {
        return read(pixels, width, height, middle, firstLine, gap, "", "");
    }

    public Reading read(
            int[] pixels,
            int width,
            int height,
            int middle,
            int firstLine,
            float gap,
            String upperEvidence,
            String lowerEvidence) {
        return read(
                pixels, width, height, middle, firstLine, gap, upperEvidence, lowerEvidence, null);
    }

    public Reading read(
            int[] pixels,
            int width,
            int height,
            int middle,
            int firstLine,
            float gap,
            String upperEvidence,
            String lowerEvidence,
            boolean[] ignored) {
        Reading original =
                readStandard(
                        pixels,
                        width,
                        height,
                        middle,
                        firstLine,
                        gap,
                        upperEvidence,
                        lowerEvidence,
                        ignored);
        if (!original.text().isBlank()
                || original.topScore() < .65f
                || original.bottomScore() < .65f) return original;
        Reading accepted = null;
        for (int threshold : new int[] {60, 75, 90}) {
            int[] core = pixels.clone();
            for (int i = 0; i < core.length; i++)
                core[i] = (pixels[i] & 255) < threshold ? 0xff000000 : 0xffffffff;
            Reading retry =
                    readStandard(core, width, height, middle, firstLine, gap, "", "", ignored);
            if (retry.text().isBlank()
                    || (accepted != null && !accepted.text().equals(retry.text()))) return original;
            if (accepted == null
                    || Math.min(retry.topScore(), retry.bottomScore())
                            < Math.min(accepted.topScore(), accepted.bottomScore()))
                accepted = retry;
        }
        String[] digits = accepted.text().split("/");
        if ((!upperEvidence.isBlank() && !upperEvidence.equals(digits[0]))
                || (!lowerEvidence.isBlank() && !lowerEvidence.equals(digits[1]))) return original;
        return accepted;
    }

    private Reading readStandard(
            int[] pixels,
            int width,
            int height,
            int middle,
            int firstLine,
            float gap,
            String upperEvidence,
            String lowerEvidence,
            boolean[] ignored) {
        Reading original =
                readAt(
                        pixels,
                        width,
                        height,
                        middle,
                        firstLine,
                        gap,
                        upperEvidence,
                        lowerEvidence,
                        ignored);
        if (!original.text().isBlank()
                || !upperEvidence.isBlank()
                || !lowerEvidence.isBlank()
                || !Float.isFinite(gap)
                || gap < 6) return original;
        // Engraved numerals may sit below or above the staff midpoint. Keep the
        // physical rail positions fixed while testing nearby digit boundaries.
        Reading accepted = null;
        int agreeing = 0;
        int step = Math.max(1, Math.round(gap * .14f));
        int radius = Math.round(gap * .8f);
        for (int offset = -radius; offset <= radius; offset += step) {
            if (Math.abs(offset) < step) continue;
            Reading retry =
                    readAt(pixels, width, height, middle + offset, firstLine, gap, "", "", ignored);
            if (retry.text().isBlank()) continue;
            if (accepted != null && !accepted.text().equals(retry.text())) return original;
            if (accepted == null
                    || Math.min(retry.topScore(), retry.bottomScore())
                            > Math.min(accepted.topScore(), accepted.bottomScore()))
                accepted = retry;
            agreeing++;
        }
        return agreeing >= 2 ? accepted : original;
    }

    private Reading readAt(
            int[] pixels,
            int width,
            int height,
            int middle,
            int firstLine,
            float gap,
            String upperEvidence,
            String lowerEvidence,
            boolean[] ignored) {
        if (ignored != null && ignored.length != pixels.length)
            throw new IllegalArgumentException("Meter exclusion size");
        if (width < gap * 1.35f || height < 12 || middle < 6 || middle > height - 6)
            return new Reading("", 0, 0);
        Match top =
                match(
                        pixels,
                        width,
                        height,
                        0,
                        middle,
                        firstLine,
                        gap,
                        new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                        upperEvidence,
                        ignored);
        Match bottom =
                match(
                        pixels,
                        width,
                        height,
                        middle,
                        height,
                        firstLine,
                        gap,
                        new int[] {2, 4, 8, 16, 32},
                        lowerEvidence,
                        ignored);
        if (top.value() == 0 || bottom.value() == 0)
            return new Reading("", top.score(), bottom.score());
        return new Reading(top.value() + "/" + bottom.value(), top.score(), bottom.score());
    }

    private Match match(
            int[] pixels,
            int width,
            int height,
            int first,
            int last,
            int firstLine,
            float gap,
            int[] values,
            String corroborated,
            boolean[] ignored) {
        var normal =
                matches(
                        pixels,
                        width,
                        height,
                        first,
                        last,
                        firstLine,
                        gap,
                        values,
                        corroborated,
                        ignored,
                        false);
        Match winner = normal.get(0);
        if (winner.value() == 0) return winner;
        if (accepts(winner.value(), winner.score(), normal.get(1).score(), corroborated))
            return winner;
        if (winner.score() >= .80f) {
            var weighted =
                    matches(
                            pixels,
                            width,
                            height,
                            first,
                            last,
                            firstLine,
                            gap,
                            values,
                            corroborated,
                            ignored,
                            true);
            Match retry = weighted.get(0);
            if (retry.value() == winner.value()
                    && retry.score() >= winner.score() + .05f
                    && accepts(retry.value(), retry.score(), weighted.get(1).score(), corroborated))
                return retry;
        }
        // A narrow engraving can preserve the winning digit while missing a uniform-size template.
        // Keep the original identity and confidence/separation requirements for this bounded retry.
        if (winner.score() >= .80f) {
            Match compactBest = null;
            for (float ratio : new float[] {.8f, .9f}) {
                var compact =
                        matches(
                                pixels,
                                width,
                                height,
                                first,
                                last,
                                firstLine,
                                gap,
                                values,
                                corroborated,
                                ignored,
                                false,
                                ratio);
                Match retry = compact.get(0);
                if (retry.value() != winner.value()
                        || retry.score() < winner.score() + .03f
                        || !accepts(
                                retry.value(), retry.score(), compact.get(1).score(), corroborated))
                    continue;
                if (compactBest == null || retry.score() > compactBest.score()) compactBest = retry;
            }
            if (compactBest != null) return compactBest;
        }
        return new Match(0, winner.score());
    }

    private List<Match> matches(
            int[] pixels,
            int width,
            int height,
            int first,
            int last,
            int firstLine,
            float gap,
            int[] values,
            String corroborated,
            boolean[] ignored,
            boolean bold) {
        return matches(
                pixels,
                width,
                height,
                first,
                last,
                firstLine,
                gap,
                values,
                corroborated,
                ignored,
                bold,
                1f);
    }

    private List<Match> matches(
            int[] pixels,
            int width,
            int height,
            int first,
            int last,
            int firstLine,
            float gap,
            int[] values,
            String corroborated,
            boolean[] ignored,
            boolean bold,
            float widthRatio) {
        int h = last - first;
        boolean[] source = new boolean[width * h], valid = new boolean[source.length];
        int sourceCount = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < width; x++) {
                int color = pixels[(first + y) * width + x];
                source[y * width + x] = (color & 255) < 150;
                boolean onLine = false;
                for (int n = 0; n < 5; n++)
                    if (Math.abs(first + y - Math.round(firstLine + n * gap))
                            <= Math.max(1, Math.round(gap * .12f))) onLine = true;
                if (ignored != null && ignored[(first + y) * width + x]) onLine = true;
                valid[y * width + x] = !onLine;
                if (!onLine && source[y * width + x]) sourceCount++;
            }
        if (sourceCount < gap * 3) return List.of(new Match(0, 0));
        List<Match> results = new ArrayList<>();
        int minSize = Math.round(gap * 2.1f), maxSize = Math.round(gap * 5.15f);
        for (int value : values) {
            float best = 0;
            String glyphs =
                    Integer.toString(value)
                            .codePoints()
                            .map(digit -> 0xe080 + digit - '0')
                            .collect(
                                    StringBuilder::new,
                                    StringBuilder::appendCodePoint,
                                    StringBuilder::append)
                            .toString();
            for (int size = minSize; size <= maxSize; size += Math.max(2, Math.round(gap * .18f))) {
                Glyph glyph = rasterizer.render(glyphs, size);
                if (widthRatio != 1f) glyph = widthScaled(glyph, widthRatio);
                if (bold) glyph = weighted(glyph);
                int gw = glyph.width(), gh = glyph.height();
                if (gw < 1 || gh < 1 || gw > width + 8 || gh > h + 8) continue;
                boolean[] shape = glyph.ink();
                for (int dx = -4; dx <= width - gw + 4; dx++)
                    for (int dy = -4; dy <= h - gh + 4; dy++) {
                        int tp = 0, targetCount = 0;
                        for (int gy = 0; gy < gh; gy++)
                            for (int gx = 0; gx < gw; gx++) {
                                if (!shape[gy * gw + gx]) continue;
                                int x = gx + dx, y = gy + dy;
                                if (x < 0 || x >= width || y < 0 || y >= h) continue;
                                int index = y * width + x;
                                if (!valid[index]) continue;
                                targetCount++;
                                if (source[index]) tp++;
                            }
                        float score = 2f * tp / Math.max(1, sourceCount + targetCount);
                        if (score > best) best = score;
                    }
            }
            results.add(new Match(value, best));
        }
        results.sort(Comparator.comparingDouble(Match::score).reversed());
        return results;
    }

    private static Glyph widthScaled(Glyph glyph, float ratio) {
        int w = Math.max(1, Math.round(glyph.width() * ratio)), h = glyph.height();
        if (glyph.width() < 1 || h < 1) return glyph;
        boolean[] ink = new boolean[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                ink[y * w + x] =
                        glyph.ink()[
                                y * glyph.width()
                                        + Math.min(
                                                glyph.width() - 1,
                                                (int) Math.floor((x + .5) * glyph.width() / w))];
        return new Glyph(w, h, ink);
    }

    /** One-pixel orthogonal stroke expansion models heavier printed ink. */
    private static Glyph weighted(Glyph glyph) {
        int w = glyph.width() + 2, h = glyph.height() + 2;
        boolean[] ink = new boolean[w * h];
        for (int y = 0; y < glyph.height(); y++)
            for (int x = 0; x < glyph.width(); x++)
                if (glyph.ink()[y * glyph.width() + x])
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++)
                            if (Math.abs(dx) + Math.abs(dy) <= 1)
                                ink[(y + 1 + dy) * w + x + 1 + dx] = true;
        return new Glyph(w, h, ink);
    }

    static boolean accepts(int winner, float score, float runnerUp, String corroborated) {
        return score >= .84f
                && (score - runnerUp >= .05f || Integer.toString(winner).equals(corroborated));
    }
}
