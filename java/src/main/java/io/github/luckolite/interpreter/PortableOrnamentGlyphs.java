// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Platform-independent ornament templates and bilinear grayscale comparison. */
final class PortableOrnamentGlyphs {
    static final int W = 48, H = 40;

    private record Template(int kind, float aspect, float[] mask) {}

    record Match(int kind, float score, float margin) {
        boolean accepted() {
            return kind != 0 && score >= .50f && margin >= .07f
                    || kind == NoteOrnament.TRILL && score >= .44f && margin >= .18f;
        }
    }

    /** One internal read-only raster query; never retain it across a caller operation. */
    static final class Query {
        private byte[] gray;
        private int width, left, top, right, bottom;
        private float[] pixels;
        private float aspect;
    }

    private final List<Template> ornaments = new ArrayList<>(), accidentals = new ArrayList<>();

    void add(byte[] gray, int width, int height, int kind, boolean accidental) {
        if (gray == null || width < 1 || height < 1 || gray.length != (long) width * height)
            throw new IllegalArgumentException("Invalid glyph raster");
        float[] pixels = mask(gray, width, 0, 0, width, height);
        float aspect = width / (float) height;
        List<Template> list = accidental ? accidentals : ornaments;
        list.add(new Template(kind, aspect, pixels));
        if (!accidental && (kind == NoteOrnament.MORDENT || kind == NoteOrnament.INVERTED_MORDENT))
            for (float factor : new float[] {.6f, .75f, .9f})
                list.add(new Template(kind, aspect * factor, pixels));
    }

    Match match(byte[] gray, int width, PortableNoteOrnaments.Bounds bounds) {
        return match(gray, width, bounds, (Query) null);
    }

    /** Internal single-call sharing; ornament recovery still evaluates each sample normally. */
    Match match(byte[] gray, int width, PortableNoteOrnaments.Bounds bounds, Query query) {
        Match original = match(gray, width, bounds, ornaments, query);
        if (original.accepted()
                || ornaments.isEmpty()
                || !MordentContour.matches(gray, width, bounds)) return original;
        int w = bounds.width(), h = bounds.height();
        int[] tones = new int[256];
        for (int y = bounds.top; y < bounds.bottom; y++)
            for (int x = bounds.left; x < bounds.right; x++) tones[gray[y * width + x] & 255]++;
        int low = percentile(tones, w * h, .1), high = percentile(tones, w * h, .95);
        if (high - low < 60) return original;
        byte[] normalized = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                normalized[y * w + x] =
                        (byte)
                                Math.max(
                                        0,
                                        Math.min(
                                                255,
                                                Math.round(
                                                        ((gray[
                                                                                        (bounds.top
                                                                                                                + y)
                                                                                                        * width
                                                                                                + bounds.left
                                                                                                + x]
                                                                                & 255)
                                                                        - low)
                                                                * 255f
                                                                / (high - low))));
        List<Template> choices = new ArrayList<>(ornaments);
        choices.add(COMPACT_MORDENT);
        Match recovered =
                match(normalized, w, new PortableNoteOrnaments.Bounds(0, 0, w, h), choices);
        return recovered.kind() == NoteOrnament.MORDENT && recovered.accepted()
                ? recovered
                : original;
    }

    /** Template-only comparison for other glyph families; excludes ornament-specific recovery. */
    Match templateMatch(byte[] gray, int width, PortableNoteOrnaments.Bounds bounds) {
        return match(gray, width, bounds, ornaments);
    }

    private static int percentile(int[] tones, int count, double fraction) {
        int total = 0;
        for (int value = 0; value < tones.length; value++) {
            total += tones[value];
            if (total >= count * fraction) return value;
        }
        return 255;
    }

    private static final Template COMPACT_MORDENT = compactMordent();

    /** Original procedural two-cycle stroke for compact, optically heavy printing. */
    private static Template compactMordent() {
        int w = 44, h = 24;
        byte[] gray = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double center = 11.5 - 6 * Math.sin(4 * Math.PI * x / (w - 1));
                double ink = Math.max(0, Math.min(1, 7.5 - Math.abs(y - center)));
                gray[y * w + x] = (byte) Math.round(255 * (1 - ink));
            }
        return new Template(NoteOrnament.MORDENT, w / (float) h, mask(gray, w, 0, 0, w, h));
    }

    Match accidental(byte[] gray, int width, PortableNoteOrnaments.Bounds bounds) {
        return match(gray, width, bounds, accidentals);
    }

    private Match match(
            byte[] gray, int width, PortableNoteOrnaments.Bounds bounds, List<Template> choices) {
        return match(gray, width, bounds, choices, null);
    }

    private Match match(
            byte[] gray,
            int width,
            PortableNoteOrnaments.Bounds bounds,
            List<Template> choices,
            Query query) {
        if (gray == null
                || width < 1
                || bounds.left < 0
                || bounds.top < 0
                || bounds.right > width
                || bounds.bottom > gray.length / width
                || bounds.width() < 1
                || bounds.height() < 1) return new Match(0, 0, 0);
        float[] candidate;
        float aspect;
        if (query != null) {
            if (query.pixels == null
                    || query.gray != gray
                    || query.width != width
                    || query.left != bounds.left
                    || query.top != bounds.top
                    || query.right != bounds.right
                    || query.bottom != bounds.bottom) {
                query.pixels =
                        mask(gray, width, bounds.left, bounds.top, bounds.right, bounds.bottom);
                query.aspect = bounds.width() / (float) bounds.height();
                query.gray = gray;
                query.width = width;
                query.left = bounds.left;
                query.top = bounds.top;
                query.right = bounds.right;
                query.bottom = bounds.bottom;
            }
            candidate = query.pixels;
            aspect = query.aspect;
        } else {
            candidate = mask(gray, width, bounds.left, bounds.top, bounds.right, bounds.bottom);
            aspect = bounds.width() / (float) bounds.height();
        }
        Map<Integer, Float> scores = new HashMap<>();
        for (var t : choices) {
            double ratio = Math.abs(Math.log(aspect / t.aspect));
            if (ratio > .45) continue;
            float overlap = 0, total = 0;
            for (int i = 0; i < candidate.length; i++) {
                overlap += Math.min(candidate[i], t.mask[i]);
                total += Math.max(candidate[i], t.mask[i]);
            }
            scores.merge(
                    t.kind, overlap / Math.max(.001f, total) - (float) ratio * .20f, Math::max);
        }
        int kind = 0;
        float best = 0, second = 0;
        for (var e : scores.entrySet())
            if (e.getValue() > best) {
                second = best;
                best = e.getValue();
                kind = e.getKey();
            } else second = Math.max(second, e.getValue());
        return new Match(kind, best, best - second);
    }

    private static float[] mask(byte[] gray, int width, int left, int top, int right, int bottom) {
        float[] values = new float[W * H];
        for (int y = 0; y < H; y++) {
            float sy = top + (y + .5f) * (bottom - top) / H - .5f;
            int y0 = Math.max(top, Math.min(bottom - 1, (int) Math.floor(sy)));
            int y1 = Math.min(bottom - 1, y0 + 1);
            float fy = Math.max(0, Math.min(1, sy - y0));
            int upperRow = y0 * width, lowerRow = y1 * width;
            for (int x = 0; x < W; x++) {
                float sx = left + (x + .5f) * (right - left) / W - .5f;
                int x0 = Math.max(left, Math.min(right - 1, (int) Math.floor(sx)));
                int x1 = Math.min(right - 1, x0 + 1);
                float fx = Math.max(0, Math.min(1, sx - x0));
                float shade =
                        (gray[upperRow + x0] & 255) * (1 - fx) * (1 - fy)
                                + (gray[upperRow + x1] & 255) * fx * (1 - fy)
                                + (gray[lowerRow + x0] & 255) * (1 - fx) * fy
                                + (gray[lowerRow + x1] & 255) * fx * fy;
                values[y * W + x] = 1 - shade / 255f;
            }
        }
        return values;
    }
}
