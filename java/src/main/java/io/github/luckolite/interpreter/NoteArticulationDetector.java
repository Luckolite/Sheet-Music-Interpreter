// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Conservative isolated-ink recognition. Unknown marks never shorten a note. */
final class NoteArticulationDetector {
    record Anchor(float x, float y, float gap, int staff, StaffPitchTrack track) {
        Anchor(float x, float y, float gap, int staff) {
            this(x, y, gap, staff, null);
        }
    }

    private record Glyph(int left, int top, int right, int bottom, int count, int[] pixels) {
        float x() {
            return (left + right) * .5f;
        }

        float y() {
            return (top + bottom) * .5f;
        }
    }

    private NoteArticulationDetector() {}

    record FermataMark(int noteIndex, float x, float y, boolean inverted) {}

    /** The same independently proved roof that shelters a dot also preserves its hold symbol. */
    static List<FermataMark> fermataMarks(
            byte[] labels, byte[] gray, int width, int height, List<Anchor> notes) {
        if (notes.isEmpty()
                || labels == null
                || gray == null
                || labels.length != (long) width * height
                || gray.length != labels.length) return List.of();
        boolean[] seen = new boolean[gray.length];
        int[] queue = new int[gray.length];
        List<FermataMark> result = new ArrayList<>();
        for (int seed = 0; seed < gray.length; seed++) {
            if (seen[seed] || (gray[seed] & 255) >= 155) continue;
            int read = 0, size = 1;
            queue[0] = seed;
            seen[seed] = true;
            int left = width, right = 0, top = height, bottom = 0;
            while (read < size) {
                int at = queue[read++], x = at % width, y = at / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!seen[next] && (gray[next] & 255) < 155) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (size < 3 || right - left > width * .025f || bottom - top > height * .018f) continue;
            Glyph dot =
                    new Glyph(left, top, right, bottom, size, java.util.Arrays.copyOf(queue, size));
            if (nearHead(dot, notes)) continue;
            int owner = -1;
            double best = Double.POSITIVE_INFINITY;
            boolean inverted = false;
            for (int i = 0; i < notes.size(); i++) {
                var note = notes.get(i);
                if (note.staff < 0 || note.gap <= 0 || Math.abs(dot.x() - note.x) > note.gap * .9f)
                    continue;
                double distance = Math.abs(dot.y() - note.y) / note.gap;
                if (distance < .55
                        || distance > 8
                        || classify(dot, width, note.gap, dot.y() < note.y)
                                != NoteArticulation.STACCATO) continue;
                int direction = dot.y() < note.y ? -1 : 1;
                if (!connectedFermataRoof(dot, labels, gray, width, height, note.gap, direction))
                    continue;
                double score = Math.abs(dot.x() - note.x) / note.gap * 3 + distance;
                if (score < best) {
                    best = score;
                    owner = i;
                    inverted = direction > 0;
                }
            }
            if (owner >= 0) result.add(new FermataMark(owner, dot.x(), dot.y(), inverted));
        }
        return List.copyOf(result);
    }

    /** Recover an angular mark whose mask was mistaken for a small notehead.
     * Staff rules may cross its tip or feet; exclude only proven horizontal rules. */
    static boolean marcatoAtHead(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float gap,
            boolean above) {
        if (gray == null || gray.length != width * height) return false;
        int padding = Math.max(3, Math.round(gap * 1.1f));
        int x0 = Math.max(0, left - padding), x1 = Math.min(width - 1, right + padding);
        int y0 = Math.max(0, top - padding), y1 = Math.min(height - 1, bottom + padding);
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        boolean[] rules = new boolean[h], seen = new boolean[w * h];
        for (int y = y0; y <= y1; y++)
            rules[y - y0] = recoveryStaffRule(gray, width, height, (left + right) / 2, y, gap);
        int[] queue = new int[w * h];
        int[] pixels = null;
        int pixelCount = 0;
        int gx0 = width, gx1 = 0, gy0 = height, gy1 = 0;
        for (int origin = 0; origin < seen.length; origin++) {
            int ox = origin % w, oy = origin / w;
            if (seen[origin] || rules[oy] || (gray[(y0 + oy) * width + x0 + ox] & 255) >= 155)
                continue;
            int size = 1, take = 0;
            queue[0] = origin;
            seen[origin] = true;
            boolean clipped = false;
            while (take < size) {
                int at = queue[take++], x = at % w, y = at / w;
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) clipped = true;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next]
                                && !rules[ny]
                                && (gray[(y0 + ny) * width + x0 + nx] & 255) < 155) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (clipped || size < 3) continue;
            int partLeft = w, partRight = 0, partTop = h, partBottom = 0;
            for (int i = 0; i < size; i++) {
                int x = queue[i] % w, y = queue[i] / w;
                partLeft = Math.min(partLeft, x);
                partRight = Math.max(partRight, x);
                partTop = Math.min(partTop, y);
                partBottom = Math.max(partBottom, y);
            }
            if (partRight - partLeft + 1 < gap * .2f && partBottom - partTop + 1 > gap * .45f)
                continue;
            for (int i = 0; i < size; i++) {
                int x = x0 + queue[i] % w, y = y0 + queue[i] / w;
                gx0 = Math.min(gx0, x);
                gx1 = Math.max(gx1, x);
                gy0 = Math.min(gy0, y);
                gy1 = Math.max(gy1, y);
                int pixel = y * width + x;
                if (pixels == null) pixels = new int[Math.min(10, queue.length)];
                else if (pixelCount == pixels.length) {
                    int capacity =
                            (int)
                                    Math.min(
                                            queue.length,
                                            Math.max(
                                                    (long) pixelCount + 1,
                                                    (long) pixels.length + (pixels.length >> 1)));
                    pixels = java.util.Arrays.copyOf(pixels, capacity);
                }
                pixels[pixelCount++] = pixel;
            }
        }
        if (pixelCount == 0 || right < gx0 || left > gx1 || bottom < gy0 || top > gy1) return false;
        Glyph glyph =
                new Glyph(
                        gx0,
                        gy0,
                        gx1,
                        gy1,
                        pixelCount,
                        java.util.Arrays.copyOf(pixels, pixelCount));
        if (interiorCrossbar(glyph, width, .45f)) return false;
        if (classify(glyph, width, gap, above) == NoteArticulation.MARCATO) return true;
        // Removing staff stripes can trim both the peak and feet of a small caret.
        // Only relax its aspect ratio when both edges were actually cut by rules.
        float gw = gx1 - gx0 + 1, gh = gy1 - gy0 + 1;
        boolean trimmed = gy0 > y0 && gy1 < y1 && rules[gy0 - y0 - 1] && rules[gy1 - y0 + 1];
        return trimmed
                && gw >= gap * .5f
                && gw <= gap * 1.3f
                && gh >= gap * .6f
                && gh <= gap * 1.7f
                && gh / gw >= .6f
                && chevronVertical(glyph, width, above);
    }

    /** A handwritten up-bow can leave its rounded tip in the notehead mask. */
    static boolean upBowAtHead(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float gap) {
        if (gray == null || gray.length != width * height || gap <= 0) return false;
        int pad = Math.max(3, Math.round(gap * 2.5f));
        int x0 = Math.max(0, left - pad), x1 = Math.min(width - 1, right + pad);
        int y0 = Math.max(0, top - pad),
                y1 = Math.min(height - 1, bottom + Math.max(2, Math.round(gap * .3f)));
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int sy = Math.max(y0, top); sy <= Math.min(y1, bottom); sy++)
            for (int sx = Math.max(x0, left); sx <= Math.min(x1, right); sx++) {
                int origin = (sy - y0) * w + sx - x0;
                if (seen[origin] || (gray[sy * width + sx] & 255) >= 155) continue;
                int size = 1, take = 0;
                queue[0] = origin;
                seen[origin] = true;
                boolean clipped = false;
                int gx0 = width, gx1 = 0, gy0 = height, gy1 = 0;
                while (take < size) {
                    int at = queue[take++], x = at % w, y = at / w;
                    if (x == 0 || x == w - 1 || y == 0 || y == h - 1) clipped = true;
                    gx0 = Math.min(gx0, x + x0);
                    gx1 = Math.max(gx1, x + x0);
                    gy0 = Math.min(gy0, y + y0);
                    gy1 = Math.max(gy1, y + y0);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                            int next = ny * w + nx;
                            if (!seen[next] && (gray[(y0 + ny) * width + x0 + nx] & 255) < 155) {
                                seen[next] = true;
                                queue[size++] = next;
                            }
                        }
                }
                float gw = gx1 - gx0 + 1, gh = gy1 - gy0 + 1;
                if (clipped
                        || gw < gap * .65f
                        || gw > gap * 3f
                        || gh < gap * .8f
                        || gh > gap * 3.5f
                        || gh / gw < .65f
                        || gh / gw > 2.2f) continue;
                // The mistaken head must be the bottom tip, not a note attached to an arm.
                if ((top + bottom) * .5f < gy0 + gh * .55f) continue;
                int[] pixels = new int[size];
                for (int i = 0; i < size; i++)
                    pixels[i] = (y0 + queue[i] / w) * width + x0 + queue[i] % w;
                if (upBowShape(new Glyph(gx0, gy0, gx1, gy1, size, pixels), width)) return true;
            }
        return false;
    }

    private static boolean upBowShape(Glyph g, int width) {
        double tip = 0;
        int tips = 0;
        for (int p : g.pixels)
            if (p / width >= g.bottom - (g.bottom - g.top) * .15) {
                tip += p % width;
                tips++;
            }
        if (tips == 0) return false;
        double apex = (tip / tips - g.left) / Math.max(1, g.right - g.left);
        if (apex < .2 || apex > .85) return false;
        int leftTop = g.bottom, rightTop = g.bottom;
        for (int p : g.pixels) {
            double x = (p % width - g.left) / (double) Math.max(1, g.right - g.left);
            if (x < .25) leftTop = Math.min(leftTop, p / width);
            if (x > .75) rightTop = Math.min(rightTop, p / width);
        }
        double leftY = (leftTop - g.top) / (double) Math.max(1, g.bottom - g.top);
        double rightY = (rightTop - g.top) / (double) Math.max(1, g.bottom - g.top);
        if (leftY > .45 || rightY > .45) return false;
        int hits = 0;
        int binCount = Math.min(12, g.right - g.left + 1);
        boolean[] bins = new boolean[binCount];
        for (int p : g.pixels) {
            double x = (p % width - g.left) / (double) Math.max(1, g.right - g.left);
            double y = (p / width - g.top) / (double) Math.max(1, g.bottom - g.top);
            double expected =
                    x < apex
                            ? leftY + (1 - leftY) * x / apex
                            : rightY + (1 - rightY) * (1 - x) / (1 - apex);
            double slope = x < apex ? (1 - leftY) / apex : (1 - rightY) / (1 - apex);
            double distance = Math.abs(y - expected) / Math.sqrt(1 + slope * slope);
            // Independently rounded stroke edges need a full raster pixel of
            // tolerance. Precision and complete coverage still prove both arms.
            if (distance < .14 + 1.0 / Math.max(1, Math.min(g.right - g.left, g.bottom - g.top))) {
                hits++;
                bins[Math.min(binCount - 1, (int) (x * binCount))] = true;
            }
        }
        int covered = 0;
        for (boolean bin : bins) if (bin) covered++;
        return hits >= g.count * .85 && covered >= Math.ceil(binCount * 10d / 12);
    }

    /** A contrasted component owns only its own bow pixels, never another mark on the note. */
    private static boolean completeBowOwns(
            Glyph fragment, byte[] gray, int width, int height, float gap) {
        int pad = Math.max(3, Math.round(gap * 3));
        int left = Math.max(0, fragment.left - pad),
                right = Math.min(width - 1, fragment.right + pad);
        int top = Math.max(0, fragment.top - pad),
                bottom = Math.min(height - 1, fragment.bottom + pad);
        int w = right - left + 1, h = bottom - top + 1;
        int threshold =
                Math.min(
                        235,
                        BeamInkThreshold.at(
                                        gray,
                                        width,
                                        height,
                                        Math.round(fragment.x()),
                                        top,
                                        bottom,
                                        gap)
                                + 15);
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int origin : fragment.pixels) {
            int x = origin % width, y = origin / width;
            if (x < left || x > right || y < top || y > bottom || (gray[origin] & 255) >= threshold)
                continue;
            int seed = (y - top) * w + x - left;
            if (seen[seed]) continue;
            int take = 0, size = 1;
            queue[0] = seed;
            seen[seed] = true;
            int l = width, r = 0, t = height, b = 0;
            boolean clipped = false;
            while (take < size) {
                int at = queue[take++], xx = at % w, yy = at / w;
                clipped |= xx == 0 || xx == w - 1 || yy == 0 || yy == h - 1;
                l = Math.min(l, left + xx);
                r = Math.max(r, left + xx);
                t = Math.min(t, top + yy);
                b = Math.max(b, top + yy);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = xx + dx, ny = yy + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next]
                                && (gray[(top + ny) * width + left + nx] & 255) < threshold) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            float gw = r - l + 1, gh = b - t + 1;
            if (clipped
                    || size < 5
                    || gw < gap * .65f
                    || gw > gap * 3
                    || gh < gap * .8f
                    || gh > gap * 3.5f
                    || gh / gw < .65f
                    || gh / gw > 2.2f) continue;
            int[] pixels = new int[size];
            for (int i = 0; i < size; i++)
                pixels[i] = (top + queue[i] / w) * width + left + queue[i] % w;
            Glyph complete = new Glyph(l, t, r, b, size, pixels);
            if (upBowShape(complete, width) || completeDownBowShape(complete, width)) return true;
        }
        return false;
    }

    private static boolean completeDownBowShape(Glyph g, int width) {
        float gw = g.right - g.left, gh = g.bottom - g.top;
        int hits = 0, interior = 0;
        boolean[] cap = new boolean[10], legL = new boolean[10], legR = new boolean[10];
        for (int pixel : g.pixels) {
            float x = (pixel % width - g.left) / Math.max(1, gw),
                    y = (pixel / width - g.top) / Math.max(1, gh);
            if (y < .35f || x < .30f || x > .70f) hits++;
            else interior++;
            if (y < .35f) cap[Math.min(9, (int) (x * 10))] = true;
            if (x < .30f) legL[Math.min(9, (int) (y * 10))] = true;
            if (x > .70f) legR[Math.min(9, (int) (y * 10))] = true;
        }
        int a = 0, b = 0, c = 0;
        for (int i = 0; i < 10; i++) {
            if (cap[i]) a++;
            if (legL[i]) b++;
            if (legR[i]) c++;
        }
        // One quantized shoulder pixel may cross the cap/leg boundary.
        return hits >= g.count * .9f && interior < g.count * .08f + 1 && a >= 9 && b >= 9 && c >= 9;
    }

    private static boolean recoveryStaffRule(
            byte[] gray, int width, int height, int x, int y, float gap) {
        if (horizontalRuleInk(gray, width, height, x, y, gap, 155, .85f)) return true;
        // A cue staff may start less than four spaces before its first mark.
        // Shorter horizontal support is sufficient only with two parallel rules.
        if (!shortRuleInk(gray, width, height, x, y, gap)) return false;
        int neighbors = 0;
        for (int offset : new int[] {-2, -1, 1, 2})
            if (shortRuleInk(gray, width, height, x, Math.round(y + offset * gap), gap))
                neighbors++;
        return neighbors >= 2;
    }

    private static boolean shortRuleInk(
            byte[] gray, int width, int height, int x, int y, float gap) {
        int near = Math.max(3, Math.round(gap * .8f)), far = Math.round(gap * 2f);
        for (int direction : new int[] {-1, 1}) {
            int hits = 0, samples = 0;
            for (int d = near; d <= far; d++) {
                int xx = x + direction * d;
                if (xx < 0 || xx >= width) return false;
                samples++;
                for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++)
                    if ((gray[yy * width + xx] & 255) < 155) {
                        hits++;
                        break;
                    }
            }
            if (samples == 0 || hits < samples * .85f) return false;
        }
        return true;
    }

    static int[] detect(byte[] labels, byte[] gray, int width, int height, List<Anchor> notes) {
        return detectWithWordBodies(labels, gray, width, height, notes, List.of());
    }

    static int[] detectWithWordBodies(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Anchor> notes,
            List<ExpressiveWordBodyInk.Body> wordBodies) {
        int[] result = new int[notes.size()];
        if (notes.isEmpty() || labels == null || labels.length != width * height) return result;
        // Raw components retain symbols that the semantic model omitted. Never erase staff
        // lines: doing so would manufacture dashes/dots from beams, stems and noteheads.
        boolean raw = gray != null && gray.length == labels.length;
        boolean[] seen = new boolean[labels.length];
        int[] queue = new int[labels.length];
        List<Glyph> glyphs = new ArrayList<>();
        for (int p = 0; p < labels.length; p++) {
            if (seen[p] || !ink(labels, gray, p, raw)) continue;
            int start = 0, end = 1;
            queue[0] = p;
            seen[p] = true;
            int left = p % width, right = left, top = p / width, bottom = top;
            while (start < end) {
                int point = queue[start++], x = point % width, y = point / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!seen[next] && ink(labels, gray, next, raw)) {
                            seen[next] = true;
                            queue[end++] = next;
                        }
                    }
            }
            if (end < 3 || right - left > width * .025f || bottom - top > height * .018f) continue;
            glyphs.add(
                    new Glyph(left, top, right, bottom, end, java.util.Arrays.copyOf(queue, end)));
        }
        List<Glyph> faintAccents =
                new ArrayList<>(
                        raw ? faintAccentGlyphs(gray, width, height, seen, queue) : List.of());
        SoftMarks softMarks =
                raw
                        ? softShadedDirectionBodies(gray, width, height, notes, seen, queue)
                        : new SoftMarks(List.of(), List.of());
        List<Glyph> softFaintAccents = softMarks.accents;
        if (raw) {
            faintAccents.addAll(shadedAccentGlyphs(gray, width, height, notes, seen, queue));
            faintAccents.addAll(softFaintAccents);
        }
        List<Glyph> originalGlyphs = List.copyOf(glyphs);
        List<Glyph> paperCandidates =
                new ArrayList<>(
                        raw ? paperGlyphs(gray, width, height, notes, seen, queue) : List.of());
        paperCandidates.addAll(softMarks.peaks);
        glyphs.addAll(paperCandidates);
        List<Glyph> candidates = new ArrayList<>(faintAccents), claimedAccents = new ArrayList<>();
        candidates.addAll(glyphs);
        List<Glyph> printedClaims = new ArrayList<>();
        // A peak-shaped first letter needs word context on its right as well.
        // Reflect positions once; semantic pixel references remain unchanged.
        List<Glyph> reflectedGlyphs =
                raw
                        ? glyphs.stream()
                                .map(
                                        g ->
                                                new Glyph(
                                                        -g.right, g.top, -g.left, g.bottom, g.count,
                                                        g.pixels))
                                .toList()
                        : List.of();
        List<Glyph> originalReflected =
                raw
                        ? originalGlyphs.stream()
                                .map(
                                        g ->
                                                new Glyph(
                                                        -g.right, g.top, -g.left, g.bottom, g.count,
                                                        g.pixels))
                                .toList()
                        : List.of();
        for (Glyph glyph : candidates) {
            List<Glyph> context = paperCandidates.contains(glyph) ? glyphs : originalGlyphs;
            List<Glyph> reflectedContext =
                    paperCandidates.contains(glyph) ? reflectedGlyphs : originalReflected;
            // A clearer subset cannot add a second meaning to an already accepted printed body.
            if (paperCandidates.contains(glyph) && overlapsPrintedClaim(glyph, printedClaims))
                continue;
            // A dark arm belongs to the recovered complete mark, not a separate dash.
            boolean fragment = false;
            for (Glyph accent : claimedAccents) {
                if (accent == glyph
                        || glyph.left < accent.left
                        || glyph.right > accent.right
                        || glyph.top < accent.top
                        || glyph.bottom > accent.bottom) continue;
                for (int pixel : accent.pixels)
                    if (pixel == glyph.pixels[0]) {
                        fragment = true;
                        break;
                    }
                if (fragment) break;
            }
            if (fragment) continue;
            // Semantic notation normally vetoes an articulation. A raw isolated
            // dot may have been mislabeled as a head and rejected by the pitch
            // reader; it can still be staccato if it is not an accepted head.
            int notation = 0;
            for (int pixel : glyph.pixels) {
                byte label = labels[pixel];
                if (label == OmrMeasurePostProcessor.NOTEHEAD
                        || label == OmrMeasurePostProcessor.STEM_OR_REST
                        || label == OmrMeasurePostProcessor.CLEF_OR_KEY
                        || label == OmrMeasurePostProcessor.STAFF) notation++;
            }
            boolean semanticNotation = notation > glyph.count * .25f;
            Glyph printedCore = raw ? printedCore(glyph, gray, width, height) : null;
            // Contrast belongs to the glyph, not the candidate note.
            int bodyContrast = -1;
            int best = -1, mark = 0;
            double distance = Double.MAX_VALUE;
            for (int n = 0; n < notes.size(); n++) {
                Anchor note = notes.get(n);
                float dx = Math.abs(glyph.x() - note.x) / note.gap,
                        dy = Math.abs(glyph.y() - note.y) / note.gap;
                if (dx > .7f || dy < .7f || dy > 8f) continue;
                boolean faint = faintAccents.contains(glyph);
                boolean softFaint = softFaintAccents.contains(glyph);
                float glyphWidth = glyph.right - glyph.left + 1,
                        glyphHeight = glyph.bottom - glyph.top + 1;
                if (faint
                        && (glyphWidth < note.gap * .75f
                                || glyphWidth > note.gap * (softFaint ? 2.6f : 2.1f) + 1
                                || glyphHeight < note.gap * .35f
                                || glyphHeight > note.gap * (softFaint ? 1.65f : 1.25f))) continue;
                int candidate =
                        faint
                                ? NoteArticulation.ACCENT
                                : classify(glyph, width, note.gap, glyph.y() < note.y);
                if (candidate == 0)
                    candidate = roundedAngular(glyph, width, note.gap, glyph.y() < note.y);
                if (candidate == NoteArticulation.MARCATO && interiorCrossbar(glyph, width, .45f))
                    continue;
                // A faint rounded dash needs nearby note ownership, not a lyric
                // extender several spaces beyond the staff.
                if (candidate == 0 && dy <= 3f && roundedTenuto(glyph, width, note.gap))
                    candidate = NoteArticulation.TENUTO;
                // A blurred detached body must prove its existing shape again on dark ink.
                // Keep all ownership, semantic notation, word, staff and dot guards below.
                boolean recoveredCore = false;
                if (candidate == 0 && printedCore != null) {
                    candidate = classify(printedCore, width, note.gap, glyph.y() < note.y);
                    if (candidate == 0)
                        candidate =
                                roundedAngular(printedCore, width, note.gap, glyph.y() < note.y);
                    if (candidate == 0) {
                        int raster = rasterCoreMark(printedCore, width, note.gap);
                        // Horizontal recovery still needs close note ownership, not a lyric
                        // extender.
                        if (raster != NoteArticulation.TENUTO || dy <= 3f) candidate = raster;
                    }
                    if (candidate == NoteArticulation.MARCATO
                            && (interiorCrossbar(printedCore, width, .45f)
                                    || interiorCrossbar(glyph, width, .45f))) candidate = 0;
                    recoveredCore = candidate != 0;
                }
                if (raw
                        && candidate != 0
                        && glyph.y() < note.y
                        && completeBowOwns(glyph, gray, width, height, note.gap)) continue;
                if (raw && !faint && candidate != 0) {
                    if (bodyContrast < 0)
                        bodyContrast = printedBodyContrast(glyph, gray, width, height) ? 1 : 0;
                    if (bodyContrast == 0) continue;
                }
                if (raw
                        && candidate == NoteArticulation.TENUTO
                        && (recoveredCore || paperCandidates.contains(glyph))
                        && continuedPrintedCurve(
                                recoveredCore ? printedCore : glyph,
                                gray,
                                labels,
                                width,
                                height,
                                note.gap)) continue;
                if (raw
                        && candidate == NoteArticulation.STACCATISSIMO
                        && descenderWord(glyph, context, labels, note.gap)) continue;
                if (recoveredCore
                        && candidate == NoteArticulation.STACCATISSIMO
                        && embeddedFragmentWord(glyph, context, labels, note.gap)) continue;
                boolean stemOwnedDot =
                        raw
                                && candidate == NoteArticulation.STACCATO
                                && dy > 5.5f
                                && longStemDot(glyph, note, gray, width, height);
                boolean stemOwnedMarcato =
                        raw
                                && candidate == NoteArticulation.MARCATO
                                && dy > 6f
                                && beamOwnedMarcato(glyph, note, gray, width, height);
                if (candidate == 0
                        || dy > 6f && !stemOwnedDot && !stemOwnedMarcato
                        || dy > 5.5f && candidate != NoteArticulation.MARCATO && !stemOwnedDot)
                    continue;
                if (raw
                        && (candidate == NoteArticulation.TENUTO
                                || candidate == NoteArticulation.STACCATO
                                || candidate == NoteArticulation.STACCATISSIMO)
                        && directionText(glyph, context, note.gap, labels)) continue;
                if (raw
                        && (candidate == NoteArticulation.MARCATO
                                || candidate == NoteArticulation.STACCATISSIMO)
                        && (embeddedDirectionWord(glyph, context, note.gap, labels, width)
                                || directionText(glyph, context, note.gap, labels)
                                || directionText(
                                        new Glyph(
                                                -glyph.right,
                                                glyph.top,
                                                -glyph.left,
                                                glyph.bottom,
                                                glyph.count,
                                                glyph.pixels),
                                        reflectedContext,
                                        note.gap,
                                        labels))) continue;
                if (raw
                        && candidate == NoteArticulation.STACCATO
                        && embeddedTextDot(glyph, context, note.gap, labels)) continue;
                if (raw
                        && paperCandidates.contains(glyph)
                        && candidate == NoteArticulation.STACCATO
                        && ShadedItalicFCap.proved(
                                gray,
                                labels,
                                width,
                                height,
                                note.gap,
                                glyph.left,
                                glyph.top,
                                glyph.right,
                                glyph.bottom)) continue;
                if (raw
                        && candidate == NoteArticulation.STACCATO
                        && initialsPunctuation(glyph, context, note.gap, labels)) continue;
                if (raw
                        && candidate == NoteArticulation.TENUTO
                        && continuedDashRow(glyph, context, notes, note)) continue;
                if (raw
                        && candidate == NoteArticulation.STACCATO
                        && endingNumberDot(glyph, context, note.gap, gray, labels, width, height))
                    continue;
                if (semanticNotation
                        && (!raw
                                || (candidate != NoteArticulation.STACCATO
                                        && (candidate != NoteArticulation.STACCATISSIMO
                                                || !filledTaper(glyph, width, glyph.y() < note.y)))
                                || nearHead(glyph, notes))) continue;
                if (raw
                        && semanticNotation
                        && candidate == NoteArticulation.STACCATISSIMO
                        && attachedMarkStem(glyph, note, gray, width, height)) continue;
                if (raw
                        && semanticNotation
                        && candidate == NoteArticulation.STACCATO
                        && OmrScoreInterpreter.flagStemOwnsDot(
                                labels,
                                gray,
                                width,
                                height,
                                note.x,
                                note.y,
                                note.gap,
                                glyph.pixels)) continue;
                if (candidate == NoteArticulation.TENUTO && nearHead(glyph, notes)) continue;
                if (raw
                        && paperCandidates.contains(glyph)
                        && candidate == NoteArticulation.TENUTO
                        && ShadedLedgerMarkOwnership.proved(
                                gray,
                                width,
                                height,
                                note.x,
                                note.y,
                                note.gap,
                                glyph.left,
                                glyph.top,
                                glyph.right,
                                glyph.bottom)) continue;
                if (raw
                        && paperCandidates.contains(glyph)
                        && candidate == NoteArticulation.TENUTO
                        && TrackedLedgerMarkOwnership.proved(
                                gray,
                                width,
                                height,
                                note.x,
                                note.y,
                                note.gap,
                                glyph.left,
                                glyph.top,
                                glyph.right,
                                glyph.bottom,
                                note.track)) continue;
                if (raw
                        && candidate == NoteArticulation.TENUTO
                        && (ledgerStackDash(glyph, note, gray, width, height)
                                || compactLedgerStaff(glyph, note, gray, width, height))) continue;
                if (raw
                        && candidate == NoteArticulation.TENUTO
                        && glyph.bottom - glyph.top + 1 <= Math.max(2, Math.round(note.gap * .23f))
                        && horizontalRuleInk(
                                gray,
                                width,
                                height,
                                Math.round(glyph.x()),
                                Math.round(glyph.y()),
                                note.gap,
                                235,
                                .50f)
                        && contrastedHorizontalRule(
                                gray,
                                width,
                                height,
                                Math.round(glyph.x()),
                                Math.round(glyph.y()),
                                note.gap)) continue;
                if (candidate == NoteArticulation.STACCATO
                        && (durationDot(glyph, notes)
                                || (raw
                                        && shelteredDot(
                                                recoveredCore ? printedCore : glyph,
                                                labels,
                                                gray,
                                                width,
                                                height,
                                                note.gap,
                                                dy,
                                                stemOwnedDot
                                                        || pairedTenuto(
                                                                glyph, context, note, width)))))
                    continue;
                // A small off-axis dot beside a head is a duration dot, not staccato.
                if (candidate == NoteArticulation.STACCATO && dx > .35f) continue;
                double score = dx * 3 + dy;
                if (score < distance) {
                    distance = score;
                    best = n;
                    mark = candidate;
                }
            }
            if (best < 0) continue;
            if ((mark == NoteArticulation.STACCATO
                            || mark == NoteArticulation.STACCATISSIMO
                            || mark == NoteArticulation.TENUTO)
                    && ExpressiveWordBodyInk.owns(wordBodies, glyph.pixels, glyph.count, width))
                continue;
            if (faintAccents.contains(glyph)) claimedAccents.add(glyph);
            if (!paperCandidates.contains(glyph)) printedClaims.add(glyph);
            Anchor owner = notes.get(best);
            // One mark on a chord affects that chord, never another staff or another onset.
            for (int n = 0; n < notes.size(); n++) {
                Anchor note = notes.get(n);
                if (note.staff == owner.staff && Math.abs(note.x - owner.x) < owner.gap * .45f)
                    result[n] |= mark;
            }
        }
        if (raw)
            for (int i = 0; i < notes.size(); i++) {
                Anchor note = notes.get(i);
                if ((result[i] & NoteArticulation.MARCATO) == 0
                        && ruleJoinedMarcato(note, labels, gray, width, height))
                    for (int j = 0; j < notes.size(); j++)
                        if (notes.get(j).staff == note.staff
                                && Math.abs(notes.get(j).x - note.x) < note.gap * .45f)
                            result[j] |= NoteArticulation.MARCATO;
                if ((result[i] & NoteArticulation.STACCATISSIMO) == 0
                        && ruleJoinedWedge(
                                note, notes, originalGlyphs, gray, labels, width, height))
                    for (int j = 0; j < notes.size(); j++)
                        if (notes.get(j).staff == note.staff
                                && Math.abs(notes.get(j).x - note.x) < note.gap * .45f)
                            result[j] |= NoteArticulation.STACCATISSIMO;
                if ((result[i] & NoteArticulation.STACCATO) == 0
                        && textJoinedDot(note, originalGlyphs, labels, gray, width, height))
                    for (int j = 0; j < notes.size(); j++)
                        if (notes.get(j).staff == note.staff
                                && Math.abs(notes.get(j).x - note.x) < note.gap * .45f)
                            result[j] |= NoteArticulation.STACCATO;
            }
        return result;
    }

    /** A filled wedge can meet a staff rule at its pointed end. */
    private static boolean ruleJoinedWedge(
            Anchor note,
            List<Anchor> notes,
            List<Glyph> glyphs,
            byte[] gray,
            byte[] labels,
            int width,
            int height) {
        float gap = note.gap;
        if (!Float.isFinite(gap)
                || gap < 6
                || gap > Math.min(width, height)
                || !Float.isFinite(note.x)
                || !Float.isFinite(note.y)
                || note.x < 0
                || note.x >= width
                || note.y < 0
                || note.y >= height) return false;
        int left = Math.max(0, Math.round(note.x - gap * 1.2f));
        int right = Math.min(width - 1, Math.round(note.x + gap * 1.2f));
        for (int direction : new int[] {-1, 1}) {
            int top = Math.max(0, Math.round(note.y + (direction < 0 ? -5.5f : .7f) * gap));
            int bottom =
                    Math.min(height - 1, Math.round(note.y + (direction < 0 ? -.7f : 5.5f) * gap));
            int w = right - left + 1, h = bottom - top + 1;
            if (w < 3 || h < 3) continue;
            boolean[] rules = new boolean[h];
            boolean any = false;
            for (int y = top; y <= bottom; y++) {
                rules[y - top] =
                        thinIndependentRule(gray, width, height, Math.round(note.x), y, gap);
                any |= rules[y - top];
            }
            if (!any) continue;
            boolean[] seen = new boolean[w * h];
            int[] queue = new int[w * h];
            for (int origin = 0; origin < seen.length; origin++) {
                int ox = origin % w, oy = origin / w;
                if (seen[origin]
                        || rules[oy]
                        || (gray[(top + oy) * width + left + ox] & 255) >= 155) continue;
                int size = 1, take = 0;
                queue[0] = origin;
                seen[origin] = true;
                int gx0 = width, gx1 = 0, gy0 = height, gy1 = 0;
                boolean clipped = false;
                while (take < size) {
                    int at = queue[take++], x = at % w, y = at / w;
                    clipped |= x == 0 || x == w - 1 || y == 0 || y == h - 1;
                    gx0 = Math.min(gx0, left + x);
                    gx1 = Math.max(gx1, left + x);
                    gy0 = Math.min(gy0, top + y);
                    gy1 = Math.max(gy1, top + y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                            int next = ny * w + nx;
                            if (!seen[next]
                                    && !rules[ny]
                                    && (gray[(top + ny) * width + left + nx] & 255) < 155) {
                                seen[next] = true;
                                queue[size++] = next;
                            }
                        }
                }
                // Only the tip may be trimmed. A beam or a sliced oval must not
                // acquire a wedge simply because it intersects a horizontal line.
                int tip = direction < 0 ? gy1 + 1 : gy0 - 1;
                if (clipped || size < 3 || tip < top || tip > bottom || !rules[tip - top]) continue;
                // A curved eighth-note flag can leave a triangular fragment
                // after its staff stripe is removed. A wedge ends at the rule;
                // it has no ink continuing through that rule toward the head.
                int beyond = tip;
                while (beyond >= top && beyond <= bottom && rules[beyond - top])
                    beyond -= direction;
                boolean continues = beyond < top || beyond > bottom;
                if (!continues)
                    for (int x = Math.max(0, gx0 - 1); x <= Math.min(width - 1, gx1 + 1); x++)
                        continues |= (gray[beyond * width + x] & 255) < 155;
                if (continues) continue;
                int[] pixels = new int[size];
                for (int i = 0; i < size; i++)
                    pixels[i] = (top + queue[i] / w) * width + left + queue[i] % w;
                Glyph glyph = new Glyph(gx0, gy0, gx1, gy1, size, pixels);
                if (Math.abs(glyph.x() - note.x) > gap * .7f
                        || nearHead(glyph, notes)
                        || classify(glyph, width, gap, direction < 0)
                                != NoteArticulation.STACCATISSIMO
                        || !filledTaper(glyph, width, direction < 0)
                        || directionText(glyph, glyphs, gap, labels)) continue;
                return true;
            }
        }
        return false;
    }

    /** Recover an upward angular mark joined to independently long, thin staff rules. */
    private static boolean ruleJoinedMarcato(
            Anchor note, byte[] labels, byte[] gray, int width, int height) {
        float gap = note.gap;
        if (!Float.isFinite(gap)
                || !Float.isFinite(note.x)
                || !Float.isFinite(note.y)
                || gap < 6
                || gap > Math.min(width, height)
                || note.x < 0
                || note.x >= width
                || note.y < 0
                || note.y >= height) return false;
        int left = Math.max(0, Math.round(note.x - gap * 4)),
                right = Math.min(width - 1, Math.round(note.x + gap * 4));
        int top = Math.max(0, Math.round(note.y - gap * 8)),
                bottom = Math.min(height - 1, Math.round(note.y - gap * .7f));
        int w = right - left + 1, h = bottom - top + 1;
        if (w < 3 || h < 3) return false;
        boolean[] rules = new boolean[h];
        boolean any = false;
        for (int y = top; y <= bottom; y++) {
            rules[y - top] = thinIndependentRule(gray, width, height, Math.round(note.x), y, gap);
            if (!rules[y - top])
                rules[y - top] = occludedStaffRule(gray, width, height, Math.round(note.x), y, gap);
            any |= rules[y - top];
        }
        if (!any) return false;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        List<Glyph> fragments = new ArrayList<>(), marks = new ArrayList<>();
        for (int origin = 0; origin < seen.length; origin++) {
            int ox = origin % w, oy = origin / w;
            if (seen[origin] || rules[oy] || (gray[(top + oy) * width + left + ox] & 255) >= 155)
                continue;
            int size = 1, take = 0;
            queue[0] = origin;
            seen[origin] = true;
            boolean clipped = false, joined = false;
            int gx0 = width, gx1 = 0, gy0 = height, gy1 = 0, notation = 0;
            while (take < size) {
                int at = queue[take++], x = at % w, y = at / w;
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) clipped = true;
                int pixel = (top + y) * width + left + x;
                gx0 = Math.min(gx0, left + x);
                gx1 = Math.max(gx1, left + x);
                gy0 = Math.min(gy0, top + y);
                gy1 = Math.max(gy1, top + y);
                byte label = labels[pixel];
                if (label == OmrMeasurePostProcessor.NOTEHEAD
                        || label == OmrMeasurePostProcessor.STEM_OR_REST
                        || label == OmrMeasurePostProcessor.CLEF_OR_KEY) notation++;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h || rules[ny]) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && (gray[(top + ny) * width + left + nx] & 255) < 155) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
                for (int direction = -1; direction <= 1; direction += 2) {
                    int ny = y + direction, distance = 1;
                    // A caret foot can end on a rule, without continuing through it.
                    if (ny >= 0
                            && ny < h
                            && rules[ny]
                            && (gray[(top + ny) * width + left + x] & 255) < 155) joined = true;
                    while (ny >= 0
                            && ny < h
                            && rules[ny]
                            && distance <= Math.ceil(gap * .25f) + 1) {
                        ny += direction;
                        distance++;
                    }
                    if (distance == 1
                            || distance > Math.ceil(gap * .25f) + 1
                            || ny < 0
                            || ny >= h
                            || rules[ny]) continue;
                    for (int dx = -distance; dx <= distance; dx++) {
                        int nx = x + dx;
                        if (nx < 0 || nx >= w) continue;
                        int next = ny * w + nx;
                        if ((gray[(top + ny) * width + left + nx] & 255) >= 155) continue;
                        // A beam can meet the far side of a staff stripe. Keep
                        // that long horizontal body out of the caret component.
                        if (longHorizontalBody(gray, width, height, left + nx, top + ny, gap))
                            continue;
                        joined = true;
                        if (!seen[next]) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
                }
            }
            float gw = gx1 - gx0 + 1, gh = gy1 - gy0 + 1;
            if (clipped || notation > size * .25f || gw > gap * 3f || gh > gap * 2.2f) continue;
            int[] pixels = new int[size];
            for (int i = 0; i < size; i++) {
                int x = left + queue[i] % w, y = top + queue[i] / w;
                pixels[i] = y * width + x;
            }
            Glyph glyph = new Glyph(gx0, gy0, gx1, gy1, size, pixels);
            fragments.add(glyph);
            if (joined
                    && Math.abs(glyph.x() - note.x) <= gap * .7f
                    && gw >= gap * .5f
                    && gw <= gap * 2.1f + 1
                    && gh >= gap * .6f
                    && gh <= gap * 1.7f
                    && gh / gw >= .45f) marks.add(glyph);
        }
        // Reflect only geometry for the existing text-run veto. Pixel references still
        // address the original labels; no source ink or shape classifier is modified.
        List<Glyph> reflected = new ArrayList<>();
        for (Glyph glyph : fragments)
            reflected.add(
                    new Glyph(
                            -glyph.right,
                            glyph.top,
                            -glyph.left,
                            glyph.bottom,
                            glyph.count,
                            glyph.pixels));
        for (Glyph glyph : marks) {
            if (directionText(glyph, fragments, gap, labels)
                    || directionText(
                            new Glyph(
                                    -glyph.right,
                                    glyph.top,
                                    -glyph.left,
                                    glyph.bottom,
                                    glyph.count,
                                    glyph.pixels),
                            reflected,
                            gap,
                            labels)) continue;
            int[] mirrored = new int[glyph.count];
            for (int i = 0; i < mirrored.length; i++) {
                int pixel = glyph.pixels[i];
                mirrored[i] = (glyph.top + glyph.bottom - pixel / width) * width + pixel % width;
            }
            if (upBowShape(
                            new Glyph(
                                    glyph.left,
                                    glyph.top,
                                    glyph.right,
                                    glyph.bottom,
                                    glyph.count,
                                    mirrored),
                            width)
                    && !interiorCrossbar(glyph, width, .45f)
                    && (Math.abs(glyph.y() - note.y) <= gap * 3f
                            || beamOwnedMarcato(glyph, note, gray, width, height, true)))
                return true;
        }
        return false;
    }

    /** Recover a round dot fused into an upright letter above a crowded chord. */
    private static boolean textJoinedDot(
            Anchor note, List<Glyph> glyphs, byte[] labels, byte[] gray, int width, int height) {
        float gap = note.gap;
        if (gap < 10) return false;
        // Independent neighboring letters establish text, not a beam or accidental.
        int letters = 0;
        for (Glyph g : glyphs) {
            if (g.right >= note.x - gap * .55f
                    || g.right < note.x - gap * 6f
                    || Math.abs(g.bottom - (note.y - gap * .5f)) > gap * .45f
                    || g.bottom - g.top < gap * .8f
                    || g.bottom - g.top > gap * 2.8f) continue;
            int notation = 0;
            for (int p : g.pixels)
                if (labels[p] == OmrMeasurePostProcessor.NOTEHEAD
                        || labels[p] == OmrMeasurePostProcessor.STEM_OR_REST
                        || labels[p] == OmrMeasurePostProcessor.CLEF_OR_KEY
                        || labels[p] == OmrMeasurePostProcessor.STAFF) notation++;
            if (notation < g.count * .25f) letters++;
        }
        if (letters < 2) return false;
        int clear = Math.max(2, Math.round(gap * .1f));
        for (int top = Math.max(clear, Math.round(note.y - gap * 1.8f));
                top <= note.y - gap * .8f;
                top++)
            for (int h = Math.max(5, Math.round(gap * .3f)); h <= gap * .6f; h++) {
                if (top + h + clear >= height) continue;
                for (int left = Math.max(1, Math.round(note.x - gap * .3f));
                        left <= note.x + gap * .3f;
                        left++)
                    for (int w = Math.max(4, Math.round(gap * .2f)); w <= gap * .4f; w++) {
                        int right = left + w - 1;
                        if (right + 1 >= width) continue;
                        if (Math.abs((left + right) * .5f - note.x) > gap * .25f) continue;
                        boolean blank = true;
                        for (int y = top - clear; y < top + h + clear && blank; y++)
                            if (y < top || y >= top + h)
                                for (int x = left; x <= right; x++)
                                    if ((gray[y * width + x] & 255) < 155) {
                                        blank = false;
                                        break;
                                    }
                        if (!blank) continue;
                        int[] rows = new int[h];
                        int count = 0;
                        for (int y = 0; y < h; y++)
                            for (int x = left; x <= right; x++)
                                if ((gray[(top + y) * width + x] & 255) < 155) {
                                    rows[y]++;
                                    count++;
                                }
                        if (rows[0] == 0
                                || rows[h - 1] == 0
                                || rows[0] > w * .65f
                                || rows[h - 1] > w * .65f
                                || rows[h / 2] < w * .9f
                                || rows[(h - 1) / 2] < w * .9f
                                || count < w * h * .65f) continue;
                        boolean rounded = true;
                        for (int y = 1; y <= h / 2; y++) if (rows[y] < rows[y - 1]) rounded = false;
                        for (int y = (h + 1) / 2; y < h; y++)
                            if (rows[y] > rows[y - 1]) rounded = false;
                        if (!rounded) continue;
                        // The intrusion must meet a continuous text upright on one side.
                        for (int side : new int[] {-1, 1}) {
                            int x = side < 0 ? left - 1 : right + 1, hits = 0;
                            for (int y = top - clear; y < top + h + clear; y++)
                                if ((gray[y * width + x] & 255) < 155) hits++;
                            if (hits >= h + clear * 2) return true;
                        }
                    }
            }
        return false;
    }

    /** A detached round serif inside the height and spacing of a word is text. */
    private static boolean embeddedTextDot(
            Glyph target, List<Glyph> glyphs, float gap, byte[] labels) {
        List<Glyph> bodies = new ArrayList<>();
        for (Glyph g : glyphs) {
            float h = g.bottom - g.top + 1, w = g.right - g.left + 1;
            if (g == target
                    || Math.abs(g.x() - target.x()) > gap * 8
                    || h < gap * .5f
                    || h > gap * 2.2f
                    || w < gap * .25f
                    || w > gap * 2.2f
                    || target.y() < g.top - gap * .1f
                    || target.y() > g.bottom + gap * .1f) continue;
            int notation = 0;
            for (int pixel : g.pixels)
                if (labels[pixel] == OmrMeasurePostProcessor.NOTEHEAD
                        || labels[pixel] == OmrMeasurePostProcessor.STEM_OR_REST
                        || labels[pixel] == OmrMeasurePostProcessor.CLEF_OR_KEY
                        || labels[pixel] == OmrMeasurePostProcessor.STAFF) notation++;
            if (notation <= g.count * .25f) bodies.add(g);
        }
        bodies.sort(java.util.Comparator.comparingInt(Glyph::left));
        for (int i = 0; i < bodies.size(); i++) {
            Glyph first = bodies.get(i), last = first;
            int count = 0;
            boolean left = false, right = false;
            for (int j = i; j < bodies.size(); j++) {
                Glyph next = bodies.get(j);
                if (Math.abs(next.bottom - first.bottom) > gap * .35f) continue;
                if (next.left - last.right > gap * .65f) break;
                count++;
                left |= next.right < target.left;
                right |= next.left > target.right;
                last = next;
            }
            if (count >= 4 && left && right) return true;
        }
        return false;
    }

    /** An angular fragment inside a continuous word is still a letter. */
    private static boolean embeddedDirectionWord(
            Glyph target, List<Glyph> glyphs, float gap, byte[] labels, int width) {
        List<Glyph> letters = new ArrayList<>();
        letters.add(target);
        for (Glyph g : glyphs) {
            float h = g.bottom - g.top + 1, w = g.right - g.left + 1;
            if (g == target
                    || Math.abs(g.x() - target.x()) > gap * 12
                    || Math.abs(g.bottom - target.bottom) > gap * .45f
                    || h < gap * .5f
                    || h > gap * 2.2f
                    || w < gap * .25f
                    || w > gap * 3f
                    || classify(g, width, gap, true) == NoteArticulation.MARCATO
                    || roundedAngular(g, width, gap, true) == NoteArticulation.MARCATO) continue;
            int notation = 0;
            for (int pixel : g.pixels)
                if (labels[pixel] == OmrMeasurePostProcessor.NOTEHEAD
                        || labels[pixel] == OmrMeasurePostProcessor.STEM_OR_REST
                        || labels[pixel] == OmrMeasurePostProcessor.CLEF_OR_KEY
                        || labels[pixel] == OmrMeasurePostProcessor.STAFF) notation++;
            if (notation <= g.count * .25f) letters.add(g);
        }
        letters.sort(java.util.Comparator.comparingInt(Glyph::left));
        for (int i = 0; i < letters.size(); i++) {
            int count = 0;
            boolean left = false, right = false, includes = false;
            Glyph last = letters.get(i);
            for (int j = i; j < letters.size(); j++) {
                Glyph next = letters.get(j);
                if (next.left - last.right > gap * .65f) break;
                count++;
                includes |= next == target;
                left |= next.right < target.left;
                right |= next.left > target.right;
                last = next;
            }
            if (includes && count >= 4 && left && right) return true;
        }
        return false;
    }

    /** A text word and its spaced extension dashes are not note articulations. */
    private static boolean directionText(
            Glyph target, List<Glyph> glyphs, float gap, byte[] labels) {
        List<Glyph> letters = new ArrayList<>();
        for (Glyph g : glyphs) {
            float h = g.bottom - g.top + 1, w = g.right - g.left + 1;
            if (g == target
                    || g.right > target.left + gap * .2f
                    || target.left - g.right > gap * 36
                    || h < gap * .5f
                    || h > gap * 2.2f
                    || w < gap * .25f
                    || w > gap * 3f
                    || g.top > target.y() + gap * .15f
                    || g.bottom + 1 < target.y() - gap * .15f) continue;
            int notation = 0;
            for (int p : g.pixels)
                if (labels[p] == OmrMeasurePostProcessor.NOTEHEAD
                        || labels[p] == OmrMeasurePostProcessor.STEM_OR_REST
                        || labels[p] == OmrMeasurePostProcessor.CLEF_OR_KEY
                        || labels[p] == OmrMeasurePostProcessor.STAFF) notation++;
            if (notation <= g.count * .25f) letters.add(g);
        }
        letters.sort(java.util.Comparator.comparingInt(Glyph::left));
        for (int i = 0; i + 2 < letters.size(); i++) {
            Glyph first = letters.get(i), last = first;
            int count = 1;
            for (int j = i + 1; j < letters.size(); j++) {
                Glyph next = letters.get(j);
                if (next.left - last.right > gap * .65f
                        || Math.abs(next.bottom - first.bottom) > gap * .45f) break;
                last = next;
                count++;
            }
            if (count < 3) continue;
            if (target.left - last.right < gap * .65f) return true;
            // Abbreviated directions can end with a period and just one extender.
            // Require that punctuation rather than treating every dash near text as text.
            if (target.left - last.right < gap * 2.2f)
                for (Glyph period : glyphs) {
                    if (period.left > last.right
                            && period.right < target.left
                            && period.left - last.right < gap * .45f
                            && Math.abs(period.bottom - last.bottom) < gap * .3f
                            && period.right - period.left + 1 <= gap * .4f
                            && period.bottom - period.top + 1 <= gap * .4f) return true;
                }
            // Require a continuous run of thin dashes back to the word. Three
            // isolated tenutos elsewhere on the page do not constitute text.
            List<Glyph> dashes = new ArrayList<>();
            for (Glyph g : glyphs)
                if (g.left >= last.right
                        && g.left <= target.right + gap * 12
                        && Math.abs(g.y() - target.y()) <= gap * .15f
                        && g.bottom - g.top + 1 <= gap * .25f
                        && g.right - g.left + 1 >= gap * .55f
                        && g.right - g.left + 1 <= gap * 1.8f) dashes.add(g);
            dashes.sort(java.util.Comparator.comparingInt(Glyph::left));
            float edge = last.right;
            int matched = 0;
            for (Glyph dash : dashes) {
                if (dash.left - edge > gap * 4.5f) break;
                edge = dash.right;
                matched++;
            }
            if (matched >= 3 && edge >= target.right - gap * .2f) return true;
        }
        return false;
    }

    /** Two initials with two aligned periods prove punctuation, rather than note dots. */
    private static boolean initialsPunctuation(
            Glyph target, List<Glyph> glyphs, float gap, byte[] labels) {
        List<Glyph> letters = new ArrayList<>(), periods = new ArrayList<>();
        for (Glyph glyph : glyphs) {
            if (Math.abs(glyph.x() - target.x()) > gap * 7f
                    || Math.abs(glyph.bottom - target.bottom) > gap * .45f) continue;
            float w = glyph.right - glyph.left + 1, h = glyph.bottom - glyph.top + 1;
            if (w <= gap * .62f && h <= gap * .62f && w / h > .65f && w / h < 1.55f) {
                periods.add(glyph);
                continue;
            }
            if (h < gap * .65f || h > gap * 2.2f || w < gap * .25f || w > gap * 3f) continue;
            int notation = 0;
            for (int pixel : glyph.pixels)
                if (labels[pixel] == OmrMeasurePostProcessor.NOTEHEAD
                        || labels[pixel] == OmrMeasurePostProcessor.STEM_OR_REST
                        || labels[pixel] == OmrMeasurePostProcessor.CLEF_OR_KEY
                        || labels[pixel] == OmrMeasurePostProcessor.STAFF) notation++;
            if (notation <= glyph.count * .25f) letters.add(glyph);
        }
        for (Glyph first : letters)
            for (Glyph second : letters) {
                if (second.left <= first.right || second.left - first.right > gap * 1.3f) continue;
                for (Glyph firstPeriod : periods) {
                    if (!periodAfterInitial(firstPeriod, first, gap)
                            || firstPeriod.right >= second.left
                            || firstPeriod.left - first.right > gap * .65f) continue;
                    for (Glyph secondPeriod : periods)
                        if (periodAfterInitial(secondPeriod, second, gap)
                                && secondPeriod.left - second.right <= gap * .65f
                                && (target == firstPeriod || target == secondPeriod)) return true;
                }
            }
        return false;
    }

    /** A beam-covered rule needs continuous ink to a proved uncovered staff section. */
    private static boolean occludedStaffRule(
            byte[] gray, int width, int height, int x, int y, float gap) {
        if ((gray[y * width + x] & 255) >= 155) return false;
        for (int side : new int[] {-1, 1})
            for (int spaces = 4; spaces <= 14; spaces++) {
                int proof = x + side * Math.round(gap * spaces);
                if (proof < 0 || proof >= width) break;
                if (!edgeStaffRule(gray, width, height, proof, y, gap, 2)) continue;
                int hits = 0, samples = Math.abs(proof - x) + 1;
                for (int xx = Math.min(x, proof); xx <= Math.max(x, proof); xx++)
                    if ((gray[y * width + xx] & 255) < 155) hits++;
                if (hits >= samples * .95f) return true;
            }
        return false;
    }

    private static boolean longHorizontalBody(
            byte[] gray, int width, int height, int x, int y, float gap) {
        int left = x, right = x, reach = Math.max(3, Math.round(gap * 2));
        while (left > 0 && x - left < reach && (gray[y * width + left - 1] & 255) < 155) left--;
        while (right + 1 < width && right - x < reach && (gray[y * width + right + 1] & 255) < 155)
            right++;
        return right - left + 1 >= gap * 1.5f;
    }

    /** Italic capitals can overhang a detached baseline period horizontally. */
    private static boolean periodAfterInitial(Glyph period, Glyph letter, float gap) {
        return period.x() > letter.x()
                && period.left >= letter.right - gap * .25f
                && period.left - letter.right <= gap * .65f;
    }

    /** A continued text or pedal line has regular spacing independent of note onsets. */
    private static boolean continuedDashRow(
            Glyph target, List<Glyph> glyphs, List<Anchor> notes, Anchor owner) {
        float gap = owner.gap;
        List<Glyph> row = new ArrayList<>();
        for (Glyph g : glyphs)
            if (Math.abs(g.y() - target.y()) <= gap * .15f
                    && Math.abs(g.x() - target.x()) <= gap * 45f
                    && g.bottom - g.top + 1 <= gap * .25f
                    && g.right - g.left + 1 >= gap * .55f
                    && g.right - g.left + 1 <= gap * 1.8f
                    && Math.abs((g.right - g.left) - (target.right - target.left)) <= gap * .3f)
                row.add(g);
        row.sort(java.util.Comparator.comparingInt(Glyph::left));
        for (int start = 0; start + 4 < row.size(); start++) {
            float step = row.get(start + 1).x() - row.get(start).x();
            if (step < gap * 1.15f || step > gap * 4.5f) continue;
            int end = start + 1;
            while (end + 1 < row.size()
                    && Math.abs(row.get(end + 1).x() - row.get(end).x() - step) <= gap * .2f) end++;
            if (end - start < 4 || row.get(end).x() - row.get(start).x() < gap * 8f) continue;
            boolean contains = false;
            int unowned = 0;
            for (int i = start; i <= end; i++) {
                Glyph dash = row.get(i);
                contains |= dash == target;
                boolean owned = false;
                for (Anchor n : notes)
                    if (n.staff == owner.staff
                            && Math.abs(n.x - dash.x()) <= gap * .7f
                            && Math.abs(n.y - dash.y()) <= gap * 5.5f) {
                        owned = true;
                        break;
                    }
                if (!owned) unowned++;
            }
            // Repeated tenutos follow the notes. An extender must have several
            // independently spaced dashes with no possible note owner.
            if (contains && unowned >= 3 && unowned * 2 >= end - start + 1) return true;
        }
        return false;
    }

    /** Number punctuation under an ending bracket is not a performance dot. */
    private static boolean endingNumberDot(
            Glyph dot,
            List<Glyph> glyphs,
            float gap,
            byte[] gray,
            byte[] labels,
            int width,
            int height) {
        for (Glyph digit : glyphs) {
            float h = digit.bottom - digit.top + 1, w = digit.right - digit.left + 1;
            if (digit.right >= dot.left
                    || dot.left - digit.right > gap * .7f
                    || Math.abs(digit.bottom - dot.bottom) > gap * .25f
                    || h < gap * .9f
                    || h > gap * 2.5f
                    || w < gap * .3f
                    || w > gap * 1.5f) continue;
            int notation = 0;
            for (int p : digit.pixels)
                if (labels[p] == OmrMeasurePostProcessor.NOTEHEAD
                        || labels[p] == OmrMeasurePostProcessor.STEM_OR_REST
                        || labels[p] == OmrMeasurePostProcessor.STAFF) notation++;
            if (notation > digit.count * .25f) continue;
            for (int top = Math.max(0, Math.round(digit.top - gap)); top < digit.top; top++) {
                for (int left = Math.max(0, Math.round(digit.left - gap));
                        left < digit.left;
                        left++) {
                    int right = Math.min(width - 1, Math.round(dot.right + gap * 3)),
                            bottom = Math.min(height - 1, Math.round(top + gap));
                    int horizontal = 0, vertical = 0;
                    for (int x = left; x <= right; x++)
                        if ((gray[top * width + x] & 255) < 155) horizontal++;
                    if (horizontal < (right - left + 1) * .95f) continue;
                    for (int y = top; y <= bottom; y++)
                        if ((gray[y * width + left] & 255) < 155) vertical++;
                    if (vertical >= (bottom - top + 1) * .95f) return true;
                }
            }
        }
        return false;
    }

    /** Paper shade must not supply the body of an otherwise plausible mark.
     * Dark printed cores and uniformly faint printed ink retain their evidence. */
    private static boolean printedBodyContrast(Glyph glyph, byte[] gray, int width, int height) {
        int padding =
                Math.max(
                        3,
                        Math.max(glyph.right - glyph.left + 1, glyph.bottom - glyph.top + 1) / 2);
        int[] background = new int[256], tones = new int[glyph.count];
        int samples = 0;
        for (int y = Math.max(0, glyph.top - padding);
                y <= Math.min(height - 1, glyph.bottom + padding);
                y++)
            for (int x = Math.max(0, glyph.left - padding);
                    x <= Math.min(width - 1, glyph.right + padding);
                    x++) {
                background[gray[y * width + x] & 255]++;
                samples++;
            }
        int paper = 255, cumulative = 0;
        for (int tone = 0; tone < 256; tone++) {
            cumulative += background[tone];
            if (cumulative >= Math.ceil(samples * .75)) {
                paper = tone;
                break;
            }
        }
        for (int i = 0; i < tones.length; i++) tones[i] = gray[glyph.pixels[i]] & 255;
        java.util.Arrays.sort(tones);
        int minimum = tones[0], median = tones[tones.length / 2];
        return minimum <= paper * .5f
                || (median - minimum <= Math.max(8, Math.round(paper * .05f))
                        && paper - median >= 25);
    }

    /** Antialiased letters can split into peak-shaped arms on one word baseline.
     * A recovered wedge inside a close word needs both flanks and a multi-letter span. */
    private static boolean embeddedFragmentWord(
            Glyph target, List<Glyph> glyphs, byte[] labels, float gap) {
        List<Glyph> letters = new ArrayList<>();
        letters.add(target);
        for (Glyph g : glyphs) {
            int w = g.right - g.left + 1, h = g.bottom - g.top + 1;
            if (g == target
                    || Math.abs(g.x() - target.x()) > gap * 6
                    || Math.abs(g.bottom - target.bottom) > gap * .45f
                    || h < gap * .5f
                    || h > gap * 2.2f
                    || w < gap * .25f
                    || w > gap * 3f) continue;
            int notation = 0;
            for (int p : g.pixels)
                if (labels[p] == OmrMeasurePostProcessor.NOTEHEAD
                        || labels[p] == OmrMeasurePostProcessor.STEM_OR_REST
                        || labels[p] == OmrMeasurePostProcessor.STAFF
                        || labels[p] == OmrMeasurePostProcessor.CLEF_OR_KEY) notation++;
            if (notation <= g.count * .25f) letters.add(g);
        }
        letters.sort(java.util.Comparator.comparingInt(Glyph::left));
        for (int i = 0; i < letters.size(); i++) {
            Glyph first = letters.get(i), last = first;
            int count = 0;
            boolean includes = false, left = false, right = false;
            for (int j = i; j < letters.size(); j++) {
                Glyph next = letters.get(j);
                if (next.left - last.right > gap * .65f) break;
                count++;
                includes |= next == target;
                left |= next.right < target.left;
                right |= next.left > target.right;
                if (includes && left && right && count >= 4 && next.right - first.left >= gap * 3)
                    return true;
                last = next;
            }
        }
        return false;
    }

    /** A lower serif/shaft beneath three adjacent letter bodies remains text.
     * Descenders share the word's baseline even when their detached stroke sits below it. */
    private static boolean descenderWord(
            Glyph target, List<Glyph> glyphs, byte[] labels, float gap) {
        if (target.right - target.left + 1 > gap * .85f
                || target.bottom - target.top + 1 < gap * .6f) return false;
        List<Glyph> letters = new ArrayList<>();
        for (Glyph g : glyphs) {
            float w = g.right - g.left + 1, h = g.bottom - g.top + 1;
            if (g == target
                    || Math.abs(g.x() - target.x()) > gap * 6
                    || w < gap * .25f
                    || w > gap * 3
                    || h < gap * .5f
                    || h > gap * 2.2f
                    || target.bottom - g.bottom < gap * .2f
                    || target.bottom - g.bottom > gap * .9f
                    || g.top > target.top + gap * .25f
                    || Math.min(g.bottom, target.bottom) - Math.max(g.top, target.top) + 1
                            < gap * .2f) continue;
            int notation = 0;
            for (int p : g.pixels)
                if (labels[p] == OmrMeasurePostProcessor.NOTEHEAD
                        || labels[p] == OmrMeasurePostProcessor.STEM_OR_REST
                        || labels[p] == OmrMeasurePostProcessor.STAFF
                        || labels[p] == OmrMeasurePostProcessor.CLEF_OR_KEY) notation++;
            if (notation <= g.count * .25f) letters.add(g);
        }
        letters.add(target);
        letters.sort(java.util.Comparator.comparingInt(Glyph::left));
        for (int i = 0; i < letters.size(); i++) {
            Glyph last = letters.get(i);
            int bodies = 0, minBottom = Integer.MAX_VALUE, maxBottom = 0;
            boolean includes = false;
            for (int j = i; j < letters.size(); j++) {
                Glyph next = letters.get(j);
                if (next.left - last.right > gap * .65f) break;
                includes |= next == target;
                if (next != target) {
                    bodies++;
                    minBottom = Math.min(minBottom, next.bottom);
                    maxBottom = Math.max(maxBottom, next.bottom);
                }
                if (includes && bodies >= 3 && maxBottom - minBottom <= gap * .4f) return true;
                last = next;
            }
        }
        return false;
    }

    /** A dark slur island is still part of one pale curve on both sides.
     * Follow connected, locally contrasted ink rather than manufacturing a detached dash. */
    private static boolean continuedPrintedCurve(
            Glyph core, byte[] gray, byte[] labels, int width, int height, float gap) {
        int pad = Math.max(3, Math.round(gap * 2));
        int l = Math.max(0, core.left - pad), r = Math.min(width - 1, core.right + pad);
        int t = Math.max(0, core.top - Math.round(gap * .8f));
        int b = Math.min(height - 1, core.bottom + Math.round(gap * .8f));
        int w = r - l + 1, h = b - t + 1;
        int[] histogram = new int[256];
        int samples = 0, cumulative = 0, paper = 255;
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                histogram[gray[y * width + x] & 255]++;
                samples++;
            }
        for (int tone = 0; tone < 256; tone++) {
            cumulative += histogram[tone];
            if (cumulative >= Math.ceil(samples * .75)) {
                paper = tone;
                break;
            }
        }
        int cutoff = paper - Math.max(25, Math.round(paper * .2f));
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        int take = 0, size = 0;
        for (int p : core.pixels) {
            int at = (p / width - t) * w + p % width - l;
            if (!seen[at] && (gray[p] & 255) < cutoff) {
                seen[at] = true;
                queue[size++] = at;
            }
        }
        boolean left = false, right = false;
        while (take < size) {
            int at = queue[take++], x = at % w, y = at / w;
            left |= x + l <= core.left - gap;
            right |= x + l >= core.right + gap;
            if (left && right) return true;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int next = ny * w + nx, p = (ny + t) * width + nx + l;
                    if (!seen[next]
                            && (gray[p] & 255) < cutoff
                            && labels[p] != OmrMeasurePostProcessor.NOTEHEAD) {
                        seen[next] = true;
                        queue[size++] = next;
                    }
                }
        }
        return false;
    }

    /** A detached printed core may lose one boundary pixel to antialiasing.
     * Keep the existing scaled dot bounds and require a compact filled body. */
    private static int rasterCoreMark(Glyph g, int width, float gap) {
        int w = g.right - g.left + 1, h = g.bottom - g.top + 1;
        float density = g.count / (float) (w * h);
        if (w >= gap * .26f
                && w <= gap * .62f
                && h >= gap * .26f
                && h <= gap * .62f
                && g.count + 1 >= gap * gap * .065f
                && w / (float) h > .65f
                && w / (float) h < 1.55f
                && density >= .75f) return NoteArticulation.STACCATO;
        // The fitted column thickness below proves a dash even when tilt expands its box.
        if (w < gap * .65f || w > gap * 1.65f + 1 || h > gap * .4f + 1 || density < .45f) return 0;
        int[] low = new int[w], high = new int[w], count = new int[w];
        java.util.Arrays.fill(low, h);
        for (int p : g.pixels) {
            int x = p % width - g.left, y = p / width - g.top;
            low[x] = Math.min(low[x], y);
            high[x] = Math.max(high[x], y);
            count[x]++;
        }
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int columns = 0, thick = 0;
        for (int x = 1; x < w - 1; x++) {
            if (count[x] == 0 || high[x] - low[x] + 1 != count[x] || w / (float) count[x] < 3.5f)
                return 0;
            double y = (low[x] + high[x]) * .5;
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
            columns++;
            if (count[x] >= 2) thick++;
        }
        if (columns < 3 || thick < columns * .8) return 0;
        double denominator = columns * sxx - sx * sx;
        double slope = denominator == 0 ? 0 : (columns * sxy - sx * sy) / denominator;
        if (Math.abs(slope) * (w - 1) > gap * .25f) return 0;
        double intercept = (sy - slope * sx) / columns;
        for (int x = 1; x < w - 1; x++) {
            if (Math.abs((low[x] + high[x]) * .5 - (intercept + slope * x)) > .65
                    || high[x] - low[x] + 1 > Math.max(3, Math.ceil(gap * .25f))) return 0;
        }
        return NoteArticulation.TENUTO;
    }

    /** Recover one complete dark body inside a detached blurred component. */
    private static Glyph printedCore(Glyph glyph, byte[] gray, int width, int height) {
        int padding =
                Math.max(
                        3,
                        Math.max(glyph.right - glyph.left + 1, glyph.bottom - glyph.top + 1) / 2);
        int[] histogram = new int[256];
        int samples = 0, minimum = 255;
        for (int y = Math.max(0, glyph.top - padding);
                y <= Math.min(height - 1, glyph.bottom + padding);
                y++)
            for (int x = Math.max(0, glyph.left - padding);
                    x <= Math.min(width - 1, glyph.right + padding);
                    x++) {
                histogram[gray[y * width + x] & 255]++;
                samples++;
            }
        int paper = 255, cumulative = 0;
        for (int tone = 0; tone < 256; tone++) {
            cumulative += histogram[tone];
            if (cumulative >= Math.ceil(samples * .75)) {
                paper = tone;
                break;
            }
        }
        for (int pixel : glyph.pixels) minimum = Math.min(minimum, gray[pixel] & 255);
        if (paper - minimum < 60 || minimum > paper * .5f) return null;
        int cutoff = Math.min(154, minimum + Math.round((paper - minimum) * .50f));
        int w = glyph.right - glyph.left + 1, h = glyph.bottom - glyph.top + 1;
        boolean[] ink = new boolean[w * h], seen = new boolean[w * h];
        int total = 0;
        for (int pixel : glyph.pixels)
            if ((gray[pixel] & 255) < cutoff) {
                ink[(pixel / width - glyph.top) * w + pixel % width - glyph.left] = true;
                total++;
            }
        if (total < 3) return null;
        int[] queue = new int[w * h], best = null;
        int bestSize = 0;
        for (int seed = 0; seed < ink.length; seed++) {
            if (!ink[seed] || seen[seed]) continue;
            int read = 0, size = 1;
            queue[0] = seed;
            seen[seed] = true;
            while (read < size) {
                int at = queue[read++], x = at % w, y = at / w;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int next = ny * w + nx;
                        if (ink[next] && !seen[next]) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (size > bestSize) {
                bestSize = size;
                best = java.util.Arrays.copyOf(queue, size);
            }
        }
        if (bestSize < 3 || bestSize < total * .85f) return null;
        int left = width, right = 0, top = height, bottom = 0;
        for (int i = 0; i < best.length; i++) {
            int x = glyph.left + best[i] % w, y = glyph.top + best[i] / w;
            left = Math.min(left, x);
            right = Math.max(right, x);
            top = Math.min(top, y);
            bottom = Math.max(bottom, y);
            best[i] = y * width + x;
        }
        return new Glyph(left, top, right, bottom, bestSize, best);
    }

    /** Recover detached dark bodies when photographed paper joined the raw component.
     * Original components and all later shape, notation and ownership proofs remain in force. */
    private static List<Glyph> paperGlyphs(
            byte[] gray, int width, int height, List<Anchor> notes, boolean[] seen, int[] queue) {
        float[] gaps = new float[notes.size()];
        int valid = 0;
        for (Anchor note : notes)
            if (Float.isFinite(note.gap) && note.gap > 0) gaps[valid++] = note.gap;
        if (valid == 0) return List.of();
        java.util.Arrays.sort(gaps, 0, valid);
        byte[] normalized = RestPaperTone.normalize(gray, width, height, gaps[valid / 2]);
        if (normalized == gray) return List.of();
        java.util.Arrays.fill(seen, false);
        List<Glyph> result = new ArrayList<>();
        for (int seed = 0; seed < gray.length; seed++) {
            if (seen[seed] || (normalized[seed] & 255) >= 200) continue;
            int read = 0, size = 1;
            queue[0] = seed;
            seen[seed] = true;
            int left = seed % width, right = left, top = seed / width, bottom = top;
            while (read < size) {
                int at = queue[read++], x = at % width, y = at / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!seen[next] && (normalized[next] & 255) < 200) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (size < 3 || right - left > width * .025f || bottom - top > height * .018f) continue;
            Glyph glyph =
                    new Glyph(left, top, right, bottom, size, java.util.Arrays.copyOf(queue, size));
            // This additional path needs shaded paper and an original dark seed.
            int padding = Math.max(3, Math.max(right - left + 1, bottom - top + 1) / 2);
            int[] histogram = new int[256];
            int samples = 0;
            for (int y = Math.max(0, top - padding);
                    y <= Math.min(height - 1, bottom + padding);
                    y++)
                for (int x = Math.max(0, left - padding);
                        x <= Math.min(width - 1, right + padding);
                        x++) {
                    histogram[gray[y * width + x] & 255]++;
                    samples++;
                }
            int total = 0, paper = 255;
            for (int tone = 0; tone < 256; tone++)
                if ((total += histogram[tone]) >= Math.ceil(samples * .75)) {
                    paper = tone;
                    break;
                }
            if (paper < 96 || paper >= 185) continue;
            int dark = 0;
            for (int pixel : glyph.pixels) if ((gray[pixel] & 255) < 155) dark++;
            if (dark < 3) continue;
            result.add(glyph);
        }
        return result;
    }

    private static boolean overlapsPrintedClaim(Glyph candidate, List<Glyph> claimed) {
        for (Glyph body : claimed) {
            if (candidate.right < body.left
                    || candidate.left > body.right
                    || candidate.bottom < body.top
                    || candidate.top > body.bottom) continue;
            for (int pixel : candidate.pixels)
                for (int accepted : body.pixels) if (pixel == accepted) return true;
        }
        return false;
    }

    private static boolean ink(byte[] labels, byte[] gray, int p, boolean raw) {
        return raw ? (gray[p] & 255) < 155 : labels[p] == OmrMeasurePostProcessor.SYMBOL;
    }

    private static boolean durationDot(Glyph glyph, List<Anchor> notes) {
        for (Anchor note : notes) {
            float dx = (glyph.x() - note.x) / note.gap,
                    dy = Math.abs(glyph.y() - note.y) / note.gap;
            if (dx > .4f && dx < 1.8f && dy < .6f) return true;
        }
        return false;
    }

    private static boolean nearHead(Glyph glyph, List<Anchor> notes) {
        for (Anchor note : notes)
            if (Math.abs(glyph.x() - note.x) < note.gap * 1.2f
                    && Math.abs(glyph.y() - note.y) < note.gap * .7f) return true;
        return false;
    }

    /** A distant marcato needs its head's shaft terminating at a thick attached beam. */
    private static boolean beamOwnedMarcato(
            Glyph mark, Anchor note, byte[] gray, int width, int height) {
        return beamOwnedMarcato(mark, note, gray, width, height, false);
    }

    private static boolean beamOwnedMarcato(
            Glyph mark, Anchor note, byte[] gray, int width, int height, boolean ruleJoined) {
        if (interiorCrossbar(mark, width)) return false;
        int direction = mark.y() < note.y ? -1 : 1;
        int edge = direction < 0 ? mark.bottom : mark.top;
        int head = Math.round(note.y + direction * note.gap * .3f);
        int thickness = Math.max(3, (int) Math.ceil(note.gap * .25f));
        int reach = Math.max(4, Math.round(note.gap * 1.5f));
        for (int side : new int[] {-1, 1})
            for (int offset = Math.round(note.gap * .3f);
                    offset <= Math.round(note.gap * .85f);
                    offset++) {
                int x = Math.round(note.x) + side * offset;
                if (x < 1 || x >= width - 1) continue;
                for (int separation =
                                ruleJoined
                                        ? -Math.round(note.gap * .25f)
                                        : Math.max(1, Math.round(note.gap * .25f));
                        separation <= Math.round(note.gap * 2.5f);
                        separation++) {
                    int endpoint = edge - direction * separation;
                    if (head < 0
                            || head >= height
                            || endpoint < 0
                            || endpoint + thickness >= height
                            || direction * (endpoint - head) < note.gap * (ruleJoined ? 2.5f : 3f))
                        continue;
                    int hits = 0, total = Math.abs(endpoint - head) + 1;
                    for (int y = Math.min(head, endpoint); y <= Math.max(head, endpoint); y++)
                        if ((gray[y * width + x] & 255) < 155) hits++;
                    if (hits < total * .95f) continue;
                    boolean joined = true;
                    for (int d = 0; d < 3; d++) {
                        int row = direction < 0 ? endpoint + thickness + d : endpoint - 1 - d;
                        if (row < 0 || row >= height || (gray[row * width + x] & 255) >= 155)
                            joined = false;
                    }
                    if (!joined) continue;
                    // The beam must meet the glyph-facing shaft endpoint, not merely
                    // cross a longer shaft or staff rule on the way toward the mark.
                    int firstBeyond = direction < 0 ? 1 : thickness;
                    int look =
                            ruleJoined && separation <= Math.round(note.gap * .25f)
                                    ? Math.max(3, Math.round(note.gap * .6f))
                                    : Math.min(
                                            Math.max(3, Math.round(note.gap * .6f)),
                                            separation - firstBeyond);
                    if (look < 3) continue;
                    int beyondInk = 0;
                    for (int d = firstBeyond; d < firstBeyond + look; d++) {
                        int row = endpoint + direction * d;
                        if (row < 0 || row >= height) {
                            beyondInk = look;
                            break;
                        }
                        if ((gray[row * width + x] & 255) < 155
                                && !thinIndependentRule(gray, width, height, x, row, note.gap))
                            beyondInk++;
                    }
                    for (int beamSide : new int[] {-1, 1}) {
                        int end = x + beamSide * reach;
                        if (end < 0 || end >= width) continue;
                        boolean thick = true;
                        for (int dy = 0; dy < thickness && thick; dy++) {
                            int row = endpoint + dy;
                            int ink = 0;
                            for (int d = 0; d <= reach; d++)
                                if ((gray[row * width + x + beamSide * d] & 255) < 155) ink++;
                            if (ink < (reach + 1) * .95f) thick = false;
                        }
                        if (thick) {
                            if (beyondInk > look * .25f) continue;
                            return true;
                        }
                    }
                }
            }
        return false;
    }

    /** An independently long, thin rule is not a shaft continuing past its beam. */
    private static boolean thinIndependentRule(
            byte[] gray, int width, int height, int x, int y, float gap) {
        if (!horizontalRuleInk(gray, width, height, x, y, gap, 155, .95f))
            return edgeStaffRule(gray, width, height, x, y, gap);
        int limit = Math.max(2, (int) Math.ceil(gap * .25f));
        for (int side : new int[] {-1, 1}) {
            int thin = 0;
            for (int sample = 0; sample < 5; sample++) {
                int xx = x + side * Math.round(gap * (2f + sample * .5f));
                if (xx < 0 || xx >= width || (gray[y * width + xx] & 255) >= 155) continue;
                int top = y, bottom = y;
                while (top > 0 && (gray[(top - 1) * width + xx] & 255) < 155 && y - top <= limit)
                    top--;
                while (bottom + 1 < height
                        && (gray[(bottom + 1) * width + xx] & 255) < 155
                        && bottom - y <= limit) bottom++;
                if (bottom - top + 1 <= limit) thin++;
            }
            // Two separated columns on each side establish thinness even when
            // a sloped beam or text stroke intersects other samples of the rule.
            if (thin < 2) return edgeStaffRule(gray, width, height, x, y, gap);
        }
        return true;
    }

    /** At a staff end, one long thin side plus two parallel rules proves the line. */
    private static boolean edgeStaffRule(
            byte[] gray, int width, int height, int x, int y, float gap) {
        return edgeStaffRule(gray, width, height, x, y, gap, 1);
    }

    private static boolean edgeStaffRule(
            byte[] gray, int width, int height, int x, int y, float gap, int tolerance) {
        for (int side : new int[] {-1, 1}) {
            if (!thinRuleSide(gray, width, height, x, y, gap, side)) continue;
            int parallel = 0;
            for (int offset : new int[] {-2, -1, 1, 2}) {
                boolean found = false;
                for (int delta = -tolerance; delta <= tolerance; delta++)
                    found |=
                            thinRuleSide(
                                    gray,
                                    width,
                                    height,
                                    x,
                                    Math.round(y + offset * gap) + delta,
                                    gap,
                                    side);
                if (found) parallel++;
            }
            if (parallel >= 2) return true;
        }
        return false;
    }

    private static boolean thinRuleSide(
            byte[] gray, int width, int height, int x, int y, float gap, int side) {
        if (y < 0 || y >= height) return false;
        int near = Math.round(gap * 2f), far = Math.round(gap * 4f), ink = 0;
        int thin = 0, limit = Math.max(2, (int) Math.ceil(gap * .25f));
        for (int distance = near; distance <= far; distance++) {
            int xx = x + side * distance;
            if (xx < 0 || xx >= width) return false;
            if ((gray[y * width + xx] & 255) >= 155) continue;
            ink++;
            int top = y, bottom = y;
            while (top > 0 && (gray[(top - 1) * width + xx] & 255) < 155 && y - top <= limit) top--;
            while (bottom + 1 < height
                    && (gray[(bottom + 1) * width + xx] & 255) < 155
                    && bottom - y <= limit) bottom++;
            if (bottom - top + 1 <= limit) thin++;
        }
        int samples = far - near + 1;
        // Nearby heads may intersect a proved rule; most of the long side
        // must still be thin, and two parallel rules remain mandatory.
        return ink >= samples * .95f && thin >= samples * .6f;
    }

    /** A letter's interior crossbar is not the two open arms of a distant caret. */
    private static boolean interiorCrossbar(Glyph mark, int width) {
        return interiorCrossbar(mark, width, .65f);
    }

    private static boolean interiorCrossbar(Glyph mark, int width, float minimumSpan) {
        int w = mark.right - mark.left + 1, h = mark.bottom - mark.top + 1, consecutive = 0;
        // Rasterized arms can meet for several rows near a small caret's
        // peak. A crossbar must cross the open lower half of its interior.
        for (int y = mark.top + (int) Math.ceil(h * .5f);
                y <= mark.top + (int) Math.floor(h * .8f);
                y++) {
            int left = Integer.MAX_VALUE, right = -1, count = 0;
            for (int pixel : mark.pixels)
                if (pixel / width == y) {
                    left = Math.min(left, pixel % width);
                    right = Math.max(right, pixel % width);
                    count++;
                }
            int span = right < 0 ? 0 : right - left + 1;
            if (span >= w * minimumSpan && count >= span * .9f) {
                if (++consecutive >= 2) return true;
            } else consecutive = 0;
        }
        return false;
    }

    /** A long printed stem and attached beam can own a dot beyond the usual head radius. */
    private static boolean longStemDot(Glyph dot, Anchor note, byte[] gray, int width, int height) {
        int direction = dot.y() < note.y ? -1 : 1;
        int from = Math.round(note.y + direction * note.gap * .3f),
                to = Math.round(dot.y() - direction * note.gap * .65f);
        if (from < 0 || to < 0 || from >= height || to >= height) return false;
        for (int side : new int[] {-1, 1})
            for (int offset = Math.round(note.gap * .3f);
                    offset <= Math.round(note.gap * .85f);
                    offset++) {
                int x = Math.round(note.x) + side * offset;
                if (x < 1 || x >= width - 1) continue;
                int hits = 0, total = Math.abs(to - from) + 1;
                for (int y = Math.min(from, to); y <= Math.max(from, to); y++)
                    if ((gray[y * width + x] & 255) < 155) hits++;
                if (hits < total * .95f) continue;
                for (int y = Math.max(0, to - Math.round(note.gap * .3f));
                        y <= Math.min(height - 1, to + Math.round(note.gap * .3f));
                        y++)
                    for (int beamSide : new int[] {-1, 1}) {
                        int reach = Math.round(note.gap * 1.7f), end = x + beamSide * reach;
                        if (end < 0 || end >= width) continue;
                        int ink = 0;
                        for (int d = 0; d <= reach; d++)
                            if ((gray[y * width + x + beamSide * d] & 255) < 155) ink++;
                        if (ink >= (reach + 1) * .95f) return true;
                    }
            }
        return false;
    }

    /** A detached dash beyond a dot supplies independent portato evidence. */
    private static boolean pairedTenuto(Glyph dot, List<Glyph> glyphs, Anchor note, int width) {
        int direction = dot.y() < note.y ? -1 : 1;
        for (Glyph glyph : glyphs)
            if (glyph != dot
                    && Math.abs(glyph.x() - dot.x()) <= note.gap * .2f
                    && direction * (glyph.y() - dot.y()) >= note.gap * .35f
                    && direction * (glyph.y() - dot.y()) <= note.gap * 1.2f
                    && classify(glyph, width, note.gap, glyph.y() < note.y)
                            == NoteArticulation.TENUTO) return true;
        return false;
    }

    /** The dot under a fermata arch is not a staccato instruction. */
    private static boolean shelteredDot(
            Glyph glyph,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            float gap,
            float distance,
            boolean independentlyOwned) {
        for (int direction : new int[] {-1, 1}) {
            int occupied = 0;
            for (int bin = -2; bin <= 2; bin++) {
                int x = Math.round(glyph.x() + bin * gap * .3f);
                boolean found = false;
                for (int d = Math.max(1, Math.round(gap * .35f));
                        d <= Math.round(gap * 1.45f);
                        d++) {
                    int y = Math.round(glyph.y()) + direction * d;
                    if (x < 0 || x >= width || y < 0 || y >= height) continue;
                    int p = y * width + x;
                    // Nearby notation does not shelter a fermata dot.
                    if (labels[p] == OmrMeasurePostProcessor.NOTEHEAD
                            || labels[p] == OmrMeasurePostProcessor.STAFF
                            || labels[p] == OmrMeasurePostProcessor.STEM_OR_REST) continue;
                    if (horizontalStaffInk(gray, width, height, x, y, gap)) continue;
                    if ((gray[p] & 255) < 155) found = true;
                }
                if (found) occupied++;
            }
            if (occupied == 5) {
                if (distance > 2.5f
                        && !independentlyOwned
                        && !longStraightShelter(glyph, gray, width, height, gap, direction))
                    return true;
                int[] tone = new int[glyph.pixels.length];
                for (int i = 0; i < tone.length; i++) tone[i] = gray[glyph.pixels[i]] & 255;
                java.util.Arrays.sort(tone);
                // Only a near, independently dark dot may override the original
                // shelter veto. Gray scan grain and distant text dots abstain.
                if (tone[tone.length / 2] > 130
                        || connectedFermataRoof(glyph, labels, gray, width, height, gap, direction))
                    return true;
            }
        }
        return false;
    }

    /** A fermata roof is one compact centered curve, not unrelated scan specks or a long slur. */
    private static boolean connectedFermataRoof(
            Glyph dot,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            float gap,
            int direction) {
        int l = Math.max(0, Math.round(dot.x() - gap * 1.8f)),
                r = Math.min(width - 1, Math.round(dot.x() + gap * 1.8f));
        int a = Math.round(dot.y() + direction * gap * .30f),
                b = Math.round(dot.y() + direction * gap * 1.7f);
        int t = Math.max(0, Math.min(a, b)), bottom = Math.min(height - 1, Math.max(a, b));
        int w = r - l + 1, h = bottom - t + 1;
        if (w < 3 || h < 3) return false;
        boolean[] valid = new boolean[w * h], seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int y = t; y <= bottom; y++)
            for (int x = l; x <= r; x++) {
                int p = y * width + x;
                byte label = labels[p];
                valid[(y - t) * w + x - l] =
                        (gray[p] & 255) < 155
                                && label != OmrMeasurePostProcessor.NOTEHEAD
                                && label != OmrMeasurePostProcessor.STAFF
                                && label != OmrMeasurePostProcessor.STEM_OR_REST
                                && !horizontalStaffInk(gray, width, height, x, y, gap);
            }
        for (int seed = 0; seed < valid.length; seed++) {
            if (seen[seed] || !valid[seed]) continue;
            int read = 0, count = 1, minX = w, maxX = 0, minY = h, maxY = 0;
            boolean clipped = false;
            queue[0] = seed;
            seen[seed] = true;
            while (read < count) {
                int at = queue[read++], x = at % w, y = at / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (x == 0 || x == w - 1 || (direction < 0 ? y == 0 : y == h - 1)) clipped = true;
                // A one-pixel antialias gap must not split a genuine roof.
                for (int dy = -2; dy <= 2; dy++)
                    for (int dx = -2; dx <= 2; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && valid[next]) {
                            seen[next] = true;
                            queue[count++] = next;
                        }
                    }
            }
            if (!clipped
                    && count >= gap
                    && maxX - minX >= gap
                    && maxY - minY >= gap * .20f
                    && count < (maxX - minX + 1) * (maxY - minY + 1) * .55f
                    && Math.abs(l + (minX + maxX) * .5f - dot.x()) <= gap * .35f
                    && l + minX <= dot.x() - gap * .45f
                    && l + maxX >= dot.x() + gap * .45f
                    && curvedRoof(queue, count, w, minX, maxX, gap, direction)) return true;
        }
        return false;
    }

    /** Both ends of an arch turn toward its dot; a ledger stripe or beam does not. */
    private static boolean curvedRoof(
            int[] pixels, int count, int width, int left, int right, float gap, int direction) {
        double[] sums = new double[3];
        int[] sizes = new int[3];
        for (int i = 0; i < count; i++) {
            int x = pixels[i] % width, y = pixels[i] / width;
            float position = (x - left) / (float) Math.max(1, right - left);
            int band =
                    position <= .2f
                            ? 0
                            : position >= .8f ? 2 : position >= .4f && position <= .6f ? 1 : -1;
            if (band >= 0) {
                sums[band] += y;
                sizes[band]++;
            }
        }
        if (sizes[0] == 0 || sizes[1] == 0 || sizes[2] == 0) return false;
        double center = sums[1] / sizes[1];
        return -direction * (sums[0] / sizes[0] - center) >= gap * .12f
                && -direction * (sums[2] / sizes[2] - center) >= gap * .12f;
    }

    /** A long shallow beam/slur is not the compact arch belonging to a fermata dot. */
    private static boolean longStraightShelter(
            Glyph dot, byte[] gray, int width, int height, float gap, int direction) {
        int reach = Math.round(gap * 2), center = Math.round(dot.x());
        if (center - reach < 0 || center + reach >= width) return false;
        for (int d = Math.max(1, Math.round(gap * .35f)); d <= Math.round(gap * 1.45f); d++) {
            int middle = Math.round(dot.y()) + direction * d;
            for (int rise = -4; rise <= 4; rise++) {
                int hits = 0, total = 0;
                for (int x = center - reach; x <= center + reach; x++) {
                    int y = middle + Math.round((x - center) * rise * .05f);
                    if (y < 1 || y >= height - 1) continue;
                    total++;
                    if ((gray[y * width + x] & 255) < 155
                            || (gray[(y - 1) * width + x] & 255) < 155
                            || (gray[(y + 1) * width + x] & 255) < 155) hits++;
                }
                if (total >= reach * 2 && hits >= total * .95f) return true;
            }
        }
        return false;
    }

    /** A missed semantic staff stripe must not become a fermata roof over a dot. */
    private static boolean horizontalStaffInk(
            byte[] gray, int width, int height, int x, int y, float gap) {
        return horizontalRuleInk(gray, width, height, x, y, gap, 155, .85f);
    }

    /** A tapered dark part of an attached shaft does not supply a detached wedge. */
    private static boolean attachedMarkStem(
            Glyph glyph, Anchor note, byte[] gray, int width, int height) {
        float gap = note.gap;
        if (!Float.isFinite(gap)
                || gap < 6
                || glyph.right - glyph.left + 1 > gap * .45f
                || glyph.bottom - glyph.top + 1 < gap * .4f) return false;
        int direction = glyph.y() < note.y ? -1 : 1;
        int head = Math.round(note.y), end = direction < 0 ? glyph.top : glyph.bottom;
        if (Math.abs(end - head) < gap * .9f
                || Math.abs(glyph.x() - note.x) < gap * .3f
                || Math.abs(glyph.x() - note.x) > gap * .85f) return false;
        int first = Math.min(head, end), last = Math.max(head, end);
        if (first < 0 || last >= height) return false;
        int threshold =
                BeamInkThreshold.at(gray, width, height, Math.round(glyph.x()), first, last, gap)
                        + 5;
        for (int x = Math.max(1, glyph.left - 1); x <= Math.min(width - 2, glyph.right + 1); x++) {
            int hit = 0, longestGap = 0, missing = 0;
            for (int y = first; y <= last; y++) {
                boolean ink = false;
                for (int dx = -1; dx <= 1; dx++)
                    if ((gray[y * width + x + dx] & 255) < threshold) ink = true;
                if (ink) {
                    hit++;
                    missing = 0;
                } else longestGap = Math.max(longestGap, ++missing);
            }
            if (hit >= (last - first + 1) * .95f && longestGap <= 1) return true;
        }
        return false;
    }

    /** A short dark dash can be only the surviving core of a faded ledger line. */
    private static boolean ledgerStackDash(
            Glyph glyph, Anchor note, byte[] gray, int width, int height) {
        float gap = note.gap;
        if (!Float.isFinite(gap)
                || gap < 6
                || glyph.bottom - glyph.top + 1 > gap * .25f
                || Math.abs(glyph.x() - note.x) > gap * .5f) return false;
        float distance = (glyph.y() - note.y) / gap;
        if (Math.abs(distance) < .7f
                || Math.abs(distance) > 3.2f
                || Math.abs(distance * 2 - Math.round(distance * 2)) > .25f) return false;
        int direction = distance < 0 ? -1 : 1;
        int stemTop = Math.round(Math.min(glyph.y(), glyph.y() + direction * 2 * gap) - gap * .5f);
        int stemBottom =
                Math.round(Math.max(glyph.y(), glyph.y() + direction * 2 * gap) + gap * .5f);
        for (int line = 0; line < 3; line++)
            if (!shortLedgerStroke(
                    gray,
                    width,
                    height,
                    note.x,
                    glyph.y() + direction * line * gap,
                    gap,
                    stemTop,
                    stemBottom,
                    gap)) return false;
        return true;
    }

    /** Compact outer ledgers must continue into a complete, thin five-rule staff.
     * Isolated short dashes retain their articulation meaning without that evidence. */
    private static boolean compactLedgerStaff(
            Glyph glyph, Anchor note, byte[] gray, int width, int height) {
        float gap = note.gap;
        if (!Float.isFinite(gap)
                || gap < 6
                || glyph.bottom - glyph.top + 1 > gap * .25f
                || glyph.right - glyph.left + 1 < gap * 1.3f
                || Math.abs(glyph.x() - note.x) > gap * .5f) return false;
        float distance = (glyph.y() - note.y) / gap;
        if (Math.abs(distance) < .7f
                || Math.abs(distance) > 3.2f
                || Math.abs(distance * 2 - Math.round(distance * 2)) > .25f) return false;
        int direction = distance < 0 ? -1 : 1;
        float span = Math.min(gap, Math.max(gap * .6f, (glyph.right - glyph.left) * .5f));
        int stemTop = Math.round(Math.min(glyph.y(), glyph.y() + direction * 2 * gap) - gap * .5f);
        int stemBottom =
                Math.round(Math.max(glyph.y(), glyph.y() + direction * 2 * gap) + gap * .5f);
        for (int start = 1; start <= 3; start++) {
            boolean complete = true;
            for (int line = 0; line < start; line++)
                if (!shortLedgerStroke(
                        gray,
                        width,
                        height,
                        note.x,
                        glyph.y() + direction * line * gap,
                        gap,
                        stemTop,
                        stemBottom,
                        span)) {
                    complete = false;
                    break;
                }
            if (!complete) continue;
            for (int line = 0; line < 5; line++) {
                int x = Math.round(note.x),
                        y = Math.round(glyph.y() + direction * (start + line) * gap);
                // Raw broad rails need both horizontal continuity and bright vertical flanks.
                if (!completeStaffRail(gray, width, height, x, y, gap, stemTop, stemBottom)) {
                    complete = false;
                    break;
                }
            }
            if (complete) return true;
        }
        return false;
    }

    private static boolean completeStaffRail(
            byte[] gray,
            int width,
            int height,
            int x,
            int y,
            float gap,
            int stemTop,
            int stemBottom) {
        int radius = Math.max(1, Math.round(gap * .15f));
        for (int dy = -radius; dy <= radius; dy++) {
            int row = y + dy;
            if (horizontalRuleInk(gray, width, height, x, row, gap, 225, .85f)
                    && contrastedHorizontalRule(gray, width, height, x, row, gap)
                    && shortLedgerStroke(
                            gray, width, height, x, row, gap, stemTop, stemBottom, gap))
                return true;
        }
        return false;
    }

    private static boolean shortLedgerStroke(
            byte[] gray,
            int width,
            int height,
            float cx,
            float cy,
            float gap,
            int stemTop,
            int stemBottom,
            float halfSpan) {
        int left = Math.round(cx - halfSpan),
                right = Math.round(cx + halfSpan),
                y = Math.round(cy),
                off = Math.max(2, Math.round(gap * .25f));
        if (left < 0 || right >= width || y - off - 1 < 0 || y + off + 1 >= height) return false;
        int hits = 0, thin = 0, shaft = 0;
        for (int x = left; x <= right; x++) {
            boolean ink = false;
            for (int yy = y - 1; yy <= y + 1; yy++)
                if ((gray[yy * width + x] & 255) < 225) {
                    ink = true;
                    break;
                }
            if (!ink) continue;
            hits++;
            if ((gray[(y - off) * width + x] & 255) >= 225
                    && (gray[(y + off) * width + x] & 255) >= 225) thin++;
            else if (stemTop >= 0 && stemBottom < height) {
                int support = 0;
                for (int yy = stemTop; yy <= stemBottom; yy++)
                    if ((gray[yy * width + x] & 255) < 225) support++;
                if (support >= (stemBottom - stemTop + 1) * .85f) shaft++;
            }
        }
        return hits >= (right - left + 1) * .9f
                && shaft <= gap * .65f
                && thin >= (hits - shaft) * .8f;
    }

    /** Broad shaded paper is not a printed rule across the flanks of a thin dash. */
    private static boolean contrastedHorizontalRule(
            byte[] gray, int width, int height, int x, int y, float gap) {
        int near = Math.max(3, Math.round(gap * 1.5f)),
                far = Math.round(gap * 4f),
                flank = Math.max(2, Math.round(gap * .3f));
        for (int direction : new int[] {-1, 1}) {
            int hits = 0, samples = 0;
            for (int d = near; d <= far; d++) {
                int xx = x + direction * d;
                if (xx < 0 || xx >= width) return false;
                samples++;
                boolean ink = false;
                for (int yy = Math.max(flank, y - 1);
                        yy <= Math.min(height - 1 - flank, y + 1);
                        yy++) {
                    int tone = gray[yy * width + xx] & 255;
                    int paper =
                            Math.max(
                                    gray[(yy - flank) * width + xx] & 255,
                                    gray[(yy + flank) * width + xx] & 255);
                    if (tone < 235 && paper >= tone + 12) {
                        ink = true;
                        break;
                    }
                }
                if (ink) hits++;
            }
            if (samples == 0 || hits < samples * .50f) return false;
        }
        return true;
    }

    private static boolean horizontalRuleInk(
            byte[] gray,
            int width,
            int height,
            int x,
            int y,
            float gap,
            int threshold,
            float coverage) {
        int near = Math.max(3, Math.round(gap * 1.5f)), far = Math.round(gap * 4f);
        for (int direction : new int[] {-1, 1}) {
            int hits = 0, samples = 0;
            for (int d = near; d <= far; d++) {
                int xx = x + direction * d;
                if (xx < 0 || xx >= width) return false;
                samples++;
                for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++)
                    if ((gray[yy * width + xx] & 255) < threshold) {
                        hits++;
                        break;
                    }
            }
            if (samples == 0 || hits < samples * coverage) return false;
        }
        return true;
    }

    /** Antialiased outer pixels need not fill an entire bounding-box row. */
    private static boolean roundedTenuto(Glyph g, int width, float gap) {
        int w = g.right - g.left + 1, h = g.bottom - g.top + 1;
        if (w < gap * .65f || w > gap * 1.65f + 1 || h > gap * .4f + 1 || w / (float) h < 3.5f)
            return false;
        int[] rows = new int[h];
        for (int p : g.pixels) rows[p / width - g.top]++;
        int top = 0, bottom = h - 1;
        if (rows[top] < w * .25f) top++;
        if (bottom > top && rows[bottom] < w * .25f) bottom--;
        int count = 0, wide = 0;
        for (int y = top; y <= bottom; y++) {
            count += rows[y];
            if (rows[y] >= w * .75f) wide++;
        }
        return bottom >= top && wide >= 2 && count > w * (bottom - top + 1) * .7f;
    }

    private static int classify(Glyph g, int width, float gap, boolean above) {
        float w = g.right - g.left + 1, h = g.bottom - g.top + 1;
        float density = g.count / (w * h);
        if (w >= gap * .26f
                && w <= gap * .62f
                && h >= gap * .26f
                && h <= gap * .62f
                && g.count >= gap * gap * .065f
                && w / h > .65f
                && w / h < 1.55f
                && density > .6f) return NoteArticulation.STACCATO;
        if (w >= gap * .65f && w <= gap * 1.65f && h <= gap * .4f && w / h >= 3.5f && density > .7f)
            return NoteArticulation.TENUTO;
        // A fractional staff gap and two rasterized outer edges can make an
        // otherwise complete accent one pixel wider than its scaled bound.
        if (w >= gap * .75f
                && w <= gap * 2.1f + 1
                && h >= gap * .35f
                && h <= gap * 1.25f
                && w / h >= 1.25f
                && fit(g, width, 0)) return NoteArticulation.ACCENT;
        // A V above a note is an up-bow. Only an upward peak above / downward peak below
        // may be marcato; flat-topped down-bow squares fail the two-line fit.
        if (w >= gap * .5f
                && w <= gap * 1.3f
                && h >= gap * .6f
                && h <= gap * 1.7f
                && h / w >= .8f
                && chevronVertical(g, width, above)) return NoteArticulation.MARCATO;
        if (w >= gap * .18f
                && w <= gap * .65f
                && h >= gap * .7f
                && h <= gap * 1.65f
                && (h / w >= 1.8f || (h + 1 >= 1.8f * (w - 1) && filledTaper(g, width, above)))
                && density > .45f
                && density < .82f
                && wedge(g, width, above)) return NoteArticulation.STACCATISSIMO;
        return 0;
    }

    private static boolean chevronVertical(Glyph g, int width, boolean above) {
        return fit(g, width, above ? 1 : 2);
    }

    /** Both precision and coverage matter: a slur, text letter or isolated slash is not a >. */
    /** Rounded-arm recovery is only for detached marks, never the head-demotion path. */
    private static int roundedAngular(Glyph g, int width, float gap, boolean above) {
        float w = g.right - g.left + 1, h = g.bottom - g.top + 1;
        if (w >= gap * .75f
                && w <= gap * 2.1f + 1
                && h >= gap * .35f
                && h <= gap * 1.25f
                && w / h >= 1.25f
                && (RoundedAngularArticulation.matches(
                                g.pixels, width, g.left, g.top, g.right, g.bottom, 0)
                        || TiltedOpenChevron.matches(
                                g.pixels, width, g.left, g.top, g.right, g.bottom)))
            return NoteArticulation.ACCENT;
        if (w >= gap * .5f
                && w <= gap * 1.3f
                && h >= gap * .6f
                && h <= gap * 1.7f
                && h / w >= .8f
                && RoundedAngularArticulation.matches(
                        g.pixels, width, g.left, g.top, g.right, g.bottom, above ? 1 : 2))
            return NoteArticulation.MARCATO;
        // Detached carets can have broad arms. Keep the two-arm ink proof and
        // open interior instead of identifying them by a narrow aspect ratio.
        if (above
                && w >= gap * .5f
                && w <= gap * 2.1f + 1
                && h >= gap * .6f
                && h <= gap * 1.7f
                && h / w >= .45f
                && openPeak(g, width)
                && !interiorCrossbar(g, width, .45f)) return NoteArticulation.MARCATO;
        return 0;
    }

    private static boolean openPeak(Glyph glyph, int width) {
        int[] mirrored = new int[glyph.count];
        for (int i = 0; i < mirrored.length; i++) {
            int pixel = glyph.pixels[i];
            mirrored[i] = (glyph.top + glyph.bottom - pixel / width) * width + pixel % width;
        }
        return upBowShape(
                new Glyph(glyph.left, glyph.top, glyph.right, glyph.bottom, glyph.count, mirrored),
                width);
    }

    private static boolean fit(Glyph g, int width, int kind) {
        int hits = 0;
        int binCount = Math.min(12, kind == 0 ? g.bottom - g.top + 1 : g.right - g.left + 1);
        boolean[] bins = new boolean[binCount];
        for (int p : g.pixels) {
            double x = (p % width - g.left) / (double) Math.max(1, g.right - g.left);
            double y = (p / width - g.top) / (double) Math.max(1, g.bottom - g.top);
            double a, b;
            if (kind == 0) {
                a = x;
                b = y;
            } else {
                a = kind == 1 ? 1 - y : y;
                b = x;
            }
            double expected = 1 - 2 * Math.abs(b - .5);
            // Small printed accents have antialiased, rounded stroke edges. Allow less than
            // one source pixel of edge rounding while retaining two-arm coverage.
            double tolerance =
                    .22 + .75 / Math.max(1, kind == 0 ? g.right - g.left : g.bottom - g.top);
            if (Math.abs(a - expected) < tolerance) {
                hits++;
                bins[Math.min(binCount - 1, (int) (b * binCount))] = true;
            }
        }
        int covered = 0;
        for (boolean bin : bins) if (bin) covered++;
        return hits >= g.count * .78 && covered >= Math.ceil(binCount * 10d / 12);
    }

    /** Complete chevron recovery may retain a pale bridge on darker photographed paper. */
    private static List<Glyph> shadedAccentGlyphs(
            byte[] gray, int width, int height, List<Anchor> notes, boolean[] seen, int[] queue) {
        float[] gaps = new float[notes.size()];
        int n = 0;
        for (Anchor a : notes) if (Float.isFinite(a.gap) && a.gap > 0) gaps[n++] = a.gap;
        if (n == 0) return List.of();
        java.util.Arrays.sort(gaps, 0, n);
        byte[] paper = RestPaperTone.normalize(gray, width, height, gaps[n / 2]);
        if (paper == gray) return List.of();
        List<Glyph> result = new ArrayList<>();
        for (Glyph glyph : faintAccentGlyphs(paper, width, height, seen, queue, 235, 200)) {
            int pad =
                    Math.max(
                            3,
                            Math.max(glyph.right - glyph.left + 1, glyph.bottom - glyph.top + 1)
                                    / 2);
            int[] hist = new int[256];
            int count = 0;
            for (int y = Math.max(0, glyph.top - pad);
                    y <= Math.min(height - 1, glyph.bottom + pad);
                    y++)
                for (int x = Math.max(0, glyph.left - pad);
                        x <= Math.min(width - 1, glyph.right + pad);
                        x++) {
                    hist[gray[y * width + x] & 255]++;
                    count++;
                }
            int total = 0, tone = 255;
            for (int v = 0; v < 256; v++)
                if ((total += hist[v]) >= Math.ceil(count * .75)) {
                    tone = v;
                    break;
                }
            if (tone >= 96 && tone < 185 && printedBodyContrast(glyph, gray, width, height))
                result.add(glyph);
        }
        return result;
    }

    private record SoftMarks(List<Glyph> accents, List<Glyph> peaks) {}

    /** Soft paper retains complete chevrons and carets without widening their meaning. */
    private static SoftMarks softShadedDirectionBodies(
            byte[] gray, int width, int height, List<Anchor> notes, boolean[] seen, int[] queue) {
        float[] gaps = new float[notes.size()];
        int n = 0;
        for (Anchor note : notes)
            if (Float.isFinite(note.gap) && note.gap > 0) gaps[n++] = note.gap;
        if (n == 0) return new SoftMarks(List.of(), List.of());
        java.util.Arrays.sort(gaps, 0, n);
        float gap = gaps[n / 2];
        byte[] paper = RestPaperTone.normalizeOrdinaryRestInk(gray, width, height, gap);
        if (paper == gray) return new SoftMarks(List.of(), List.of());
        // Shape connectivity and dark seed evidence use their independently established tones.
        byte[] seedInk = RestPaperTone.normalize(gray, width, height, gap);
        List<Glyph> accents = new ArrayList<>(), peaks = new ArrayList<>();
        java.util.Arrays.fill(seen, false);
        for (int seed = 0; seed < paper.length; seed++) {
            if (seen[seed] || (paper[seed] & 255) >= 185) continue;
            int take = 0,
                    size = 1,
                    left = seed % width,
                    right = left,
                    top = seed / width,
                    bottom = top,
                    dark = 0,
                    softDark = 0;
            queue[0] = seed;
            seen[seed] = true;
            while (take < size) {
                int at = queue[take++], x = at % width, y = at / width;
                if ((seedInk[at] & 255) < 200) dark++;
                if ((paper[at] & 255) < 155) softDark++;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!seen[next] && (paper[next] & 255) < 185) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (size < 8
                    || softDark < Math.max(3, Math.round(size * .18f) - 1)
                    || right - left > width * .025f
                    || bottom - top > height * .018f) continue;
            boolean accent = OpenChevron.matches(queue, width, left, top, right, bottom, size);
            if (dark < Math.max(3, Math.round(size * .18f) - 1)
                    && (!accent
                            || !connectedStrongSeedStroke(
                                    queue, size, width, left, top, right, bottom, seedInk, gap)))
                continue;
            Glyph glyph =
                    new Glyph(left, top, right, bottom, size, java.util.Arrays.copyOf(queue, size));
            boolean peak =
                    !interiorCrossbar(glyph, width, .45f)
                            && (classify(glyph, width, gap, true) == NoteArticulation.MARCATO
                                    || roundedAngular(glyph, width, gap, true)
                                            == NoteArticulation.MARCATO);
            if (!accent && !peak) continue;
            int pad = Math.max(3, Math.max(right - left + 1, bottom - top + 1) / 2);
            int[] histogram = new int[256];
            int count = 0;
            for (int y = Math.max(0, top - pad); y <= Math.min(height - 1, bottom + pad); y++)
                for (int x = Math.max(0, left - pad); x <= Math.min(width - 1, right + pad); x++) {
                    histogram[gray[y * width + x] & 255]++;
                    count++;
                }
            int cumulative = 0, tone = 255;
            for (int value = 0; value < 256; value++)
                if ((cumulative += histogram[value]) >= Math.ceil(count * .75)) {
                    tone = value;
                    break;
                }
            if (tone < 96 || tone >= 240 || !printedBodyContrast(glyph, gray, width, height))
                continue;
            if (accent) accents.add(glyph);
            if (peak) peaks.add(glyph);
        }
        return new SoftMarks(accents, peaks);
    }

    /** Pale blur may enlarge a complete chevron; only a connected strong stroke can replace its proportional seed budget. */
    private static boolean connectedStrongSeedStroke(
            int[] pixels,
            int count,
            int stride,
            int left,
            int top,
            int right,
            int bottom,
            byte[] seedInk,
            float gap) {
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] ink = new boolean[w * h], seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int i = 0; i < count; i++)
            if ((seedInk[pixels[i]] & 255) < 200)
                ink[(pixels[i] / stride - top) * w + pixels[i] % stride - left] = true;
        for (int origin = 0; origin < ink.length; origin++) {
            if (!ink[origin] || seen[origin]) continue;
            int take = 0, size = 1, l = w, r = 0, t = h, b = 0;
            double x = 0, y = 0, xx = 0, yy = 0, xy = 0;
            queue[0] = origin;
            seen[origin] = true;
            while (take < size) {
                int at = queue[take++], px = at % w, py = at / w;
                l = Math.min(l, px);
                r = Math.max(r, px);
                t = Math.min(t, py);
                b = Math.max(b, py);
                x += px;
                y += py;
                xx += (double) px * px;
                yy += (double) py * py;
                xy += (double) px * py;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = px + dx, ny = py + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && ink[next]) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (size < Math.max(3, Math.ceil(gap * .4f))
                    || Math.max(r - l + 1, b - t + 1) < gap * .45f) continue;
            double vx = xx / size - x * x / (size * (double) size),
                    vy = yy / size - y * y / (size * (double) size),
                    cov = xy / size - x * y / (size * (double) size);
            double difference = Math.hypot(vx - vy, 2 * cov),
                    major = (vx + vy + difference) * .5,
                    minor = (vx + vy - difference) * .5;
            if (major >= 3 * Math.max(.25, minor)) return true;
        }
        return false;
    }

    /** Recover only complete open chevrons; pale ink never becomes a dot or dash. */
    private static List<Glyph> faintAccentGlyphs(
            byte[] gray, int width, int height, boolean[] seen, int[] queue) {
        return faintAccentGlyphs(gray, width, height, seen, queue, 185, 155);
    }

    private static List<Glyph> faintAccentGlyphs(
            byte[] gray,
            int width,
            int height,
            boolean[] seen,
            int[] queue,
            int threshold,
            int darkThreshold) {
        java.util.Arrays.fill(seen, false);
        List<Glyph> result = new ArrayList<>();
        for (int p = 0; p < gray.length; p++) {
            if (seen[p] || (gray[p] & 255) >= threshold) continue;
            int start = 0, end = 1;
            queue[0] = p;
            seen[p] = true;
            int left = p % width, right = left, top = p / width, bottom = top, dark = 0;
            while (start < end) {
                int point = queue[start++], x = point % width, y = point / width;
                if ((gray[point] & 255) < darkThreshold) dark++;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!seen[next] && (gray[next] & 255) < threshold) {
                            seen[next] = true;
                            queue[end++] = next;
                        }
                    }
            }
            // A complete independently proved pair can lose one dark edge pixel
            // to antialiasing. Round its core budget to actual pixels; retain the
            // absolute dark seed and every chevron, size, ownership and notation guard.
            if (end < 8
                    || dark < Math.max(3, Math.round(end * .18f) - 1)
                    || right - left > width * .025f
                    || bottom - top > height * .018f) continue;
            if (OpenChevron.matches(queue, width, left, top, right, bottom, end)) {
                int[] pixels = java.util.Arrays.copyOf(queue, end);
                result.add(new Glyph(left, top, right, bottom, end, pixels));
            }
        }
        return result;
    }

    private static boolean wedge(Glyph g, int width, boolean above) {
        int broad = 0, tip = 0;
        for (int p : g.pixels) {
            double y = (p / width - g.top) / (double) Math.max(1, g.bottom - g.top);
            if (!above) y = 1 - y;
            if (y < .33) broad++;
            if (y > .67) tip++;
        }
        return broad >= tip * 1.6 && tip > 0;
    }

    /** A mislabeled wedge must be solid and taper continuously toward its note. */
    private static boolean filledTaper(Glyph glyph, int width, boolean above) {
        int h = glyph.bottom - glyph.top + 1;
        int[] rows = new int[h], spans = new int[h];
        int[] left = new int[h], right = new int[h];
        java.util.Arrays.fill(left, Integer.MAX_VALUE);
        java.util.Arrays.fill(right, -1);
        for (int pixel : glyph.pixels) {
            int row = pixel / width - glyph.top, x = pixel % width;
            if (!above) row = h - 1 - row;
            rows[row]++;
            left[row] = Math.min(left[row], x);
            right[row] = Math.max(right[row], x);
        }
        // A fractional outer edge can leave a partial row above the broad
        // face. Ignore only that single partial row, inside the following face;
        // a neck or a separate scan speck must still fail the continuous taper.
        int first =
                h >= 3
                                && rows[0] > 0
                                && rows[1] >= 4
                                && rows[0] <= rows[1] * .8f
                                && left[0] >= left[1]
                                && right[0] <= right[1]
                        ? 1
                        : 0;
        int decreases = 0;
        for (int row = first; row < h; row++) {
            spans[row] = right[row] - left[row] + 1;
            if (rows[row] == 0 || rows[row] < spans[row] * .9f) return false;
            if (row > first) {
                if (spans[row] > spans[row - 1] + 1) return false;
                if (spans[row] < spans[row - 1]) decreases++;
            }
        }
        return spans[first] >= spans[h - 1] * 2 && decreases >= 2;
    }
}
