// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A complete clef or grand-staff brace owns its cropped ink before numeric OCR. */
final class MeterHeaderInkGuard {
    private MeterHeaderInkGuard() {}

    private record Glyph(
            int label,
            int area,
            int left,
            int right,
            int top,
            int bottom,
            float centerX,
            float centerY,
            boolean clipped,
            int[] pixels) {}

    private static boolean bassAlternatives(
            MeterChangeDetector.Crop crop, Glyph body, java.util.List<Glyph> glyphs) {
        float gap = crop.gap();
        int dots = 0;
        for (var other : glyphs) {
            if (other == body) continue;
            if (other.right() < crop.left()
                    || other.left() > crop.right()
                    || other.bottom() < crop.top()
                    || other.top() > crop.bottom()) continue;
            if (other.clipped()) return false;
            int w = other.right() - other.left() + 1, h = other.bottom() - other.top() + 1;
            boolean dot =
                    w >= gap * .18f
                            && w <= gap * .7f
                            && h >= gap * .18f
                            && h <= gap * .7f
                            && other.area() >= w * h * .45f
                            && other.left() >= Math.round(body.right() + gap * .10f)
                            && other.right() <= Math.round(body.right() + gap * 1.15f)
                            && other.top() >= Math.round(crop.firstLine() + gap * .1f)
                            && other.bottom() <= Math.round(crop.firstLine() + gap * 1.95f);
            if (dot) {
                dots++;
                continue;
            }
            // Mixed-label members in the existing model's finite descending-tail band are
            // retained explicitly; this is a geometric role, not a full-gray body claim.
            boolean tail =
                    other.left() >= body.left()
                            && other.right() <= body.right()
                            && other.top() >= Math.floor(crop.firstLine() + gap * 2.35f)
                            && other.bottom() <= Math.ceil(crop.firstLine() + gap * 3.4f);
            if (!tail) return false;
        }
        return dots == 2;
    }

    private record Explanation(String model, java.util.List<Integer> pixels) {}

    static final class ModelExclusion {
        private final AcceptedMeterHeaderBounds.Source source;
        private final MeterChangeDetector.Crop crop;
        private final Explanation explanation;

        private ModelExclusion(
                AcceptedMeterHeaderBounds.Source source,
                MeterChangeDetector.Crop crop,
                Explanation explanation) {
            this.source = source;
            this.crop = crop;
            this.explanation = explanation;
        }

        boolean binds(AcceptedMeterHeaderBounds.Source source, MeterChangeDetector.Crop crop) {
            return this.source == source && this.crop.equals(crop);
        }

        String model() {
            return explanation.model();
        }

        int[] pixels() {
            return explanation.pixels().stream().mapToInt(Integer::intValue).toArray();
        }
    }

    static ModelExclusion exclusion(
            AcceptedMeterHeaderBounds.Source source, MeterChangeDetector.Crop crop) {
        if (source == null || crop == null || !source.sameSnapshot()) return null;
        var explained = explain(crop, source.labels, source.gray, source.width, source.height);
        return explained == null ? null : new ModelExclusion(source, crop, explained);
    }

    static ModelExclusion localAccidentalExclusion(
            AcceptedMeterHeaderBounds.Source source,
            MeterChangeDetector.Crop crop,
            int measure,
            SourceLocalAccidental local,
            java.util.List<ScoreNoteEvent> notes) {
        if (local == null) return null;
        var role = local.role(source, crop, measure, notes);
        return role == null
                ? null
                : new ModelExclusion(
                        source,
                        crop,
                        new Explanation(
                                "EXISTING_LOCAL_ACCIDENTAL_PICKER_MODEL_NOT_METER_ABSENCE_AUTHORITY",
                                role.pixels()));
    }

    static boolean owns(
            MeterChangeDetector.Crop crop, byte[] labels, byte[] gray, int width, int height) {
        return explain(crop, labels, gray, width, height) != null;
    }

    private static Explanation explain(
            MeterChangeDetector.Crop crop, byte[] labels, byte[] gray, int width, int height) {
        if (crop == null
                || labels == null
                || gray == null
                || labels.length != (long) width * height
                || gray.length != labels.length
                || crop.gap() < 6
                || !Float.isFinite(crop.gap())) return null;
        float gap = crop.gap();
        int left = Math.max(0, Math.round(crop.left() - gap * .25f));
        int right = Math.min(width - 1, Math.round(crop.right() + gap * .25f));
        int top = Math.max(0, Math.round(crop.top() - gap * 16));
        int bottom = Math.min(height - 1, Math.round(crop.bottom() + gap * 16));
        int w = right - left + 1, h = bottom - top + 1;
        if (w <= 0 || h <= 0 || w > gap * 4.2f || h > gap * 37) return null;
        var glyphs = new java.util.ArrayList<Glyph>();
        for (byte target :
                new byte[] {OmrMeasurePostProcessor.CLEF_OR_KEY, OmrMeasurePostProcessor.SYMBOL}) {
            boolean[] visited = new boolean[w * h];
            int[] queue = new int[w * h];
            for (int seed = 0; seed < visited.length; seed++) {
                int pixel = (top + seed / w) * width + left + seed % w;
                if (visited[seed] || labels[pixel] != target || (gray[pixel] & 255) >= 205)
                    continue;
                int size = 1, take = 0, minX = w, maxX = -1, minY = h, maxY = -1;
                queue[0] = seed;
                visited[seed] = true;
                long sx = 0, sy = 0;
                while (take < size) {
                    int at = queue[take++], x = at % w, y = at / w;
                    sx += left + x;
                    sy += top + y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                            int next = ny * w + nx, source = (top + ny) * width + left + nx;
                            if (!visited[next]
                                    && labels[source] == target
                                    && (gray[source] & 255) < 205) {
                                visited[next] = true;
                                queue[size++] = next;
                            }
                        }
                }
                int x0 = left + minX, x1 = left + maxX, y0 = top + minY, y1 = top + maxY;
                boolean clipped = minX == 0 || maxX == w - 1 || minY == 0 || maxY == h - 1;
                int[] members = new int[size];
                for (int i = 0; i < size; i++)
                    members[i] = (top + queue[i] / w) * width + left + queue[i] % w;
                glyphs.add(
                        new Glyph(
                                target,
                                size,
                                x0,
                                x1,
                                y0,
                                y1,
                                sx / (float) size,
                                sy / (float) size,
                                clipped,
                                members));
                // Do not infer a complete header from a glyph clipped by the bounded window.
                if (clipped || size < gap * gap) continue;
                if (x1 < crop.left() || x0 > crop.right() || y1 < crop.top() || y0 > crop.bottom())
                    continue;
                float glyphWidth = x1 - x0 + 1, glyphHeight = y1 - y0 + 1;
                boolean clef =
                        target == OmrMeasurePostProcessor.CLEF_OR_KEY
                                && glyphWidth >= gap * .7f
                                && glyphWidth <= gap * 3.2f
                                && glyphHeight > gap * 4.8f
                                && y0 < crop.firstLine() - gap * .4f
                                && y1 > crop.firstLine() + gap * 4.35f;
                boolean brace =
                        target == OmrMeasurePostProcessor.SYMBOL
                                && glyphWidth >= gap * .6f
                                && glyphWidth <= gap * 1.8f
                                && glyphHeight >= gap * 8
                                && glyphHeight <= gap * 20
                                && (Math.abs(y0 - crop.firstLine()) <= gap * .4f
                                        || Math.abs(y1 - (crop.firstLine() + gap * 4))
                                                <= gap * .4f);
                if (clef || brace) {
                    var memberList = new java.util.ArrayList<Integer>();
                    for (int memberPixel : members) memberList.add(memberPixel);
                    return new Explanation(
                            clef
                                    ? "EXISTING_TREBLE_CLEF_GEOMETRIC_MODEL"
                                    : "EXISTING_GRAND_STAFF_BRACE_GEOMETRIC_MODEL",
                            java.util.List.copyOf(memberList));
                }
            }
        }
        // Bass candidates wait for the entire component traversal. A separate digit, clipped
        // body or unexplained source component remains a rival, even beside convincing dots.
        for (var body : glyphs) {
            if (body.label() != OmrMeasurePostProcessor.CLEF_OR_KEY
                    || body.clipped()
                    || body.area() < gap * gap
                    || body.right() < crop.left()
                    || body.left() > crop.right()
                    || body.bottom() < crop.top()
                    || body.top() > crop.bottom()) continue;
            if (OmrScoreInterpreter.bassClefHeaderModel(
                            labels,
                            gray,
                            width,
                            height,
                            body.area(),
                            body.left(),
                            body.right(),
                            body.top(),
                            body.bottom(),
                            body.centerX(),
                            body.centerY(),
                            crop.firstLine(),
                            gap)
                    && bassAlternatives(crop, body, glyphs)) {
                var selected = new java.util.ArrayList<Integer>();
                for (var glyph : glyphs)
                    if (!(glyph.right() < crop.left()
                            || glyph.left() > crop.right()
                            || glyph.bottom() < crop.top()
                            || glyph.top() > crop.bottom()))
                        for (int pixel : glyph.pixels()) selected.add(pixel);
                java.util.Collections.sort(selected);
                return new Explanation(
                        "EXISTING_BASS_CLEF_GEOMETRIC_MODEL_WITH_ALL_OBSERVED_COMPONENT_ALTERNATIVES",
                        java.util.List.copyOf(selected));
            }
        }
        return null;
    }
}
