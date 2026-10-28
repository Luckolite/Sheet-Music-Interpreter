// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
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

    record BowMark(int owner, int technique) {}

    /** Complete finite raw bow bodies, with unique attack ownership. Never articulation bits. */
    static List<BowMark> bowMarks(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Anchor> notes,
            List<ScoreNoteEvent> events,
            List<PlayingTechniqueDetector.Staff> staffs) {
        long area = (long) width * height;
        if (width <= 0
                || height <= 0
                || area > 20_000_000
                || gray == null
                || labels == null
                || gray.length != area
                || labels.length != area
                || notes == null
                || events == null
                || staffs == null
                || notes.size() != events.size()
                || notes.size() > 4096) return List.of();
        // Same raw component threshold as ordinary articulation recognition. One bounded page
        // traversal, at most4096 complete components and no crop manufacture/whitening.
        long work = area * 20;
        if (work > 256_000_000L) return List.of();
        boolean[] seen = new boolean[gray.length];
        int[] queue = new int[gray.length];
        List<Glyph> glyphs = new ArrayList<>();
        for (int seed = 0; seed < gray.length; seed++) {
            if (seen[seed] || (gray[seed] & 255) >= 155) continue;
            int size = 1, take = 0, l = width, r = 0, t = height, b = 0;
            queue[0] = seed;
            seen[seed] = true;
            while (take < size) {
                int at = queue[take++], x = at % width, y = at / width;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= width || yy < 0 || yy >= height) continue;
                        int next = yy * width + xx;
                        if (!seen[next] && (gray[next] & 255) < 155) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (glyphs.size() == 4096) return List.of();
            glyphs.add(new Glyph(l, t, r, b, size, Arrays.copyOf(queue, size)));
        }
        // One extra geometry-index store per component/peer, plus reusable storage.
        work += (long) glyphs.size() * notes.size() * 9 + notes.size() + 32;
        if (work > 256_000_000L) return List.of();
        int[] geometryPeers = new int[notes.size()];
        List<BowMark> result = new ArrayList<>();
        for (Glyph g : glyphs) {
            if (g.count < 5
                    || g.left == 0
                    || g.top == 0
                    || g.right == width - 1
                    || g.bottom == height - 1) continue;
            int owner = -1, geometryCount = 0;
            boolean ambiguous = false;
            boolean strokeMeasured = false;
            int strokeSpanTwice = -1;
            BowCapProof capProof = null;
            boolean capMeasured = false;
            // Existing UP shape tip-band law, measured on this complete component.
            work += g.count + 16L;
            if (work > 256_000_000L) return List.of();
            double tip = 0;
            int tips = 0;
            for (int pixel : g.pixels)
                if (pixel / width >= g.bottom - (g.bottom - g.top) * .15) {
                    tip += pixel % width;
                    tips++;
                }
            double tipX = tips == 0 ? Double.NaN : tip / tips;
            for (int i = 0; i < notes.size(); i++) {
                Anchor n = notes.get(i);
                float gap = n.gap;
                if (n.staff < 0
                        || !Float.isFinite(gap)
                        || gap < 6
                        || !Float.isFinite(n.x)
                        || !Float.isFinite(n.y)) continue;
                if (n.staff >= staffs.size()) continue;
                var frame = staffs.get(n.staff);
                float above = (frame.top() - g.bottom) / gap;
                float w = g.right - g.left + 1, h = g.bottom - g.top + 1;
                if (above < -.6f
                        || above > 4.2f
                        || w < gap * .65f
                        || h < gap * .8f
                        // Staff gap and raw positions are pixel-center coordinates.
                        || g.bottom - g.top > gap * 3.5f
                        || h / w < .65f
                        || h / w > 2.2f
                        || n.y - g.bottom < gap * .7f) continue;
                if (w > gap * 3) {
                    // A peer outside both existing anchor tolerances cannot own this mark.
                    if (Math.abs(g.x() - n.x) > gap * .7f && Math.abs(tipX - n.x) > gap * .7f)
                        continue;
                    if (!strokeMeasured) {
                        if (!capMeasured) {
                            work += completeUpBowCapWork(g);
                            if (work > 256_000_000L) return List.of();
                            capProof = completeUpBowCapProof(g, width);
                            capMeasured = true;
                        }
                        // Complete finite component profile and shape proof, before allocation.
                        work +=
                                20L * (g.right - g.left + 1) * (g.bottom - g.top + 1)
                                        + 64L * g.count
                                        + 160L * (g.bottom - g.top + 1)
                                        + 384;
                        if (work > 256_000_000L) return List.of();
                        strokeSpanTwice = completeBowStrokeSpanTwice(g, width, capProof);
                        strokeMeasured = true;
                    }
                    if (strokeSpanTwice < 0 || strokeSpanTwice > 6d * gap) continue;
                }
                geometryPeers[geometryCount++] = i;
                if (Math.abs(g.x() - n.x) > gap * .7f) continue;
                if (owner >= 0) {
                    var a = events.get(owner);
                    var b = events.get(i);
                    if (a.measureIndex() != b.measureIndex()
                            || a.staffIndex() != b.staffIndex()
                            || a.staffCount() != b.staffCount()
                            || a.positionInMeasure() != b.positionInMeasure()) ambiguous = true;
                } else owner = i;
            }
            // Qualify the new anchor only when it can add a peer to the legacy domain.
            // DOWN and ambiguous shapes never acquire or compete through a V tip.
            work += 16L * geometryCount + 32;
            if (work > 256_000_000L) return List.of();
            boolean tipPotential = false, tipQualified = false;
            for (int slot = 0; slot < geometryCount; slot++) {
                Anchor peer = notes.get(geometryPeers[slot]);
                if (Math.abs(g.x() - peer.x) > peer.gap * .7f
                        && Math.abs(tipX - peer.x) <= peer.gap * .7f) tipPotential = true;
            }
            if (tipPotential) {
                long w = g.right - g.left + 1L, h = g.bottom - g.top + 1L;
                // Includes full component classification, measured-DOWN rectangle,
                // and the UP row certificate before any new arrays or scans.
                work += 32L * g.count + 64L * h + 4L * w * h + 512;
                if (work > 256_000_000L) return List.of();
                boolean downShape =
                        completeDownBowShape(g, width) || measuredDownBowShape(g, width);
                if (!downShape && !capMeasured) {
                    work += completeUpBowCapWork(g);
                    if (work > 256_000_000L) return List.of();
                    capProof = completeUpBowCapProof(g, width);
                    capMeasured = true;
                }
                // Every fully certified UP shape uses the same centroid-or-tip union.
                // Existing tolerances and all distinct-attack rivals remain unchanged.
                boolean upShape = upBowShape(g, width) || capAwareUpBowShape(g, width, capProof);
                tipQualified = upShape && !downShape && completeUpBowRows(g, width, capProof);
                if (tipQualified)
                    for (int slot = 0; slot < geometryCount; slot++) {
                        int i = geometryPeers[slot];
                        Anchor peer = notes.get(i);
                        if (Math.abs(g.x() - peer.x) <= peer.gap * .7f
                                || Math.abs(tipX - peer.x) > peer.gap * .7f) continue;
                        if (owner >= 0) {
                            var a = events.get(owner);
                            var b = events.get(i);
                            if (a.measureIndex() != b.measureIndex()
                                    || a.staffIndex() != b.staffIndex()
                                    || a.staffCount() != b.staffCount()
                                    || a.positionInMeasure() != b.positionInMeasure())
                                ambiguous = true;
                        } else owner = i;
                    }
            }
            if (owner < 0 || ambiguous) continue;
            Anchor n = notes.get(owner);
            // This includes every glyph the existing directionText grammar can use:
            // its letters reach36g left, bounded3g width, and dashes12g right.
            List<Glyph> context = new ArrayList<>();
            long contextPixels = 0;
            for (var other : glyphs)
                if (other.right >= g.left - 40 * n.gap
                        && other.left <= g.right + 14 * n.gap
                        && other.bottom >= g.y() - 3 * n.gap
                        && other.top <= g.y() + 3 * n.gap) {
                    context.add(other);
                    contextPixels += other.count;
                }
            if (context.size() > 256) continue;
            int padding = Math.max(3, Math.max(g.right - g.left + 1, g.bottom - g.top + 1) / 2);
            long background =
                    (long)
                                    (Math.min(width - 1, g.right + padding)
                                            - Math.max(0, g.left - padding)
                                            + 1)
                            * (Math.min(height - 1, g.bottom + padding)
                                    - Math.max(0, g.top - padding)
                                    + 1);
            long c = context.size();
            work +=
                    background * 4
                            + (long) g.count * 20
                            + notes.size() * 8L
                            + c * c * c
                            + contextPixels * 2;
            if (work > 256_000_000L) return List.of();
            if (nearHead(g, notes)
                    || !printedBodyContrast(g, gray, width, height)
                    || directionText(g, context, n.gap, labels)) continue;
            boolean down =
                    !tipQualified
                            && (completeDownBowShape(g, width) || measuredDownBowShape(g, width));
            if (!down && !capMeasured) {
                work += completeUpBowCapWork(g);
                if (work > 256_000_000L) return List.of();
                capProof = completeUpBowCapProof(g, width);
                capMeasured = true;
            }
            boolean up =
                    tipQualified || upBowShape(g, width) || capAwareUpBowShape(g, width, capProof);
            if (up == down) continue;
            if (up && !tipQualified) {
                // New component-only row certificate: all loops and row storage
                // prepaid before construction; the existing page cap is unchanged.
                work += 12L * g.count + 64L * (g.bottom - g.top + 1) + 128;
                if (work > 256_000_000L) return List.of();
                if (!completeUpBowRows(g, width, capProof)) continue;
            }
            result.add(
                    new BowMark(
                            owner,
                            up ? ScoreTechniqueChange.UP_BOW : ScoreTechniqueChange.DOWN_BOW));
        }
        // Conflicting directions abstain. Index exact attacks once; no quadratic
        // rescan of every output component against all other components.
        var unique = new java.util.LinkedHashMap<ScoreTechniqueChange.Attack, BowMark>();
        var conflicts = new java.util.HashSet<ScoreTechniqueChange.Attack>();
        for (var mark : result) {
            var key = ScoreTechniqueChange.attackOf(events.get(mark.owner));
            var prior = unique.putIfAbsent(key, mark);
            if (prior != null && prior.technique != mark.technique) conflicts.add(key);
        }
        conflicts.forEach(unique::remove);
        return List.copyOf(unique.values());
    }

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
                if (distance < .55 || distance > 8) continue;
                Glyph provedDot = dot;
                if (!fermataDot(
                        dot.right - dot.left + 1, dot.bottom - dot.top + 1, dot.count, note.gap)) {
                    provedDot = softFermataDot(dot, gray, width, height, note.gap);
                    if (provedDot == null || nearHead(provedDot, notes)) continue;
                }
                int direction = provedDot.y() < note.y ? -1 : 1;
                if (!connectedFermataRoof(
                        provedDot, labels, gray, width, height, note.gap, direction)) continue;
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

    /** Recover a blurred dot only around an existing dark core; roof proof stays independent. */
    private static Glyph softFermataDot(Glyph core, byte[] gray, int width, int height, float gap) {
        if (core.count < 3
                || core.right - core.left + 1 > Math.ceil(gap * .62f)
                || core.bottom - core.top + 1 > Math.ceil(gap * .62f)) return null;
        int radius = Math.max(3, Math.round(gap * .8f));
        int l = Math.max(0, Math.round(core.x()) - radius),
                r = Math.min(width - 1, Math.round(core.x()) + radius);
        int t = Math.max(0, Math.round(core.y()) - radius),
                b = Math.min(height - 1, Math.round(core.y()) + radius);
        int w = r - l + 1, h = b - t + 1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        int first = core.pixels[0], sx = first % width, sy = first / width;
        if (sx < l || sx > r || sy < t || sy > b) return null;
        queue[0] = first;
        seen[(sy - t) * w + sx - l] = true;
        int count = 1, read = 0, minX = sx, maxX = sx, minY = sy, maxY = sy;
        while (read < count) {
            int at = queue[read++], x = at % width, y = at / width;
            if (x == l || x == r || y == t || y == b) return null;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < l || nx > r || ny < t || ny > b) continue;
                    int i = (ny - t) * w + nx - l;
                    if (!seen[i] && (gray[ny * width + nx] & 255) < 205) {
                        seen[i] = true;
                        queue[count++] = ny * width + nx;
                    }
                }
        }
        if (!fermataDot(maxX - minX + 1, maxY - minY + 1, count, gap)) return null;
        return new Glyph(minX, minY, maxX, maxY, count, Arrays.copyOf(queue, count));
    }

    /** Integer outer pixels round the dot bound outward; its proved roof remains mandatory. */
    static boolean fermataDot(int w, int h, int count, float gap) {
        return Float.isFinite(gap)
                && gap > 0
                && w >= gap * .26f
                && h >= gap * .26f
                && w <= Math.ceil(gap * .62f)
                && h <= Math.ceil(gap * .62f)
                && count >= gap * gap * .065f
                && w / (float) h > .65f
                && w / (float) h < 1.55f
                && count / (float) ((long) w * h) > .6f
                && count <= (long) w * h;
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

    /** The original arm-fit law is unchanged. Only literal cap-run pixels from
     * a complete independent graph proof may also count as fit hits. Cap-only
     * pixels never fill the original arm-coverage bins. */
    private static boolean capAwareUpBowShape(Glyph g, int width, BowCapProof proof) {
        if (proof == null) return false;
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
            boolean armFit =
                    distance
                            < .14 + 1.0 / Math.max(1, Math.min(g.right - g.left, g.bottom - g.top));
            if (armFit) bins[Math.min(binCount - 1, (int) (x * binCount))] = true;
            if (armFit || proof.contains(p % width - g.left, p / width - g.top)) hits++;
        }
        int covered = 0;
        for (boolean bin : bins) if (bin) covered++;
        return hits >= g.count * .85 && covered >= Math.ceil(binCount * 10d / 12);
    }

    private static long completeUpBowCapWork(Glyph g) {
        long w = g.right - g.left + 1L, h = g.bottom - g.top + 1L;
        return 16L * w * h + 64L * g.count + 192L * h + 512;
    }

    private record BowRowRun(int first, int last) {
        boolean touches(BowRowRun other) {
            return other != null && first <= other.last + 1 && last >= other.first - 1;
        }

        int width() {
            return last - first + 1;
        }
    }

    private record BowCapProof(int firstRow, List<BowRowRun> auxiliary) {
        BowCapProof {
            auxiliary = List.copyOf(auxiliary);
        }

        boolean contains(int x, int y) {
            int row = y - firstRow;
            return row >= 0
                    && row < auxiliary.size()
                    && x >= auxiliary.get(row).first
                    && x <= auxiliary.get(row).last;
        }
    }

    private static boolean completeUpBowRows(Glyph g, int width) {
        return completeUpBowRowsStrict(g, width) || completeUpBowCapProof(g, width) != null;
    }

    private static boolean completeUpBowRows(Glyph g, int width, BowCapProof proof) {
        return completeUpBowRowsStrict(g, width) || proof != null;
    }

    /** Complete literal row-run graph. One tiny cap overtrace must join exactly
     * one already proved arm within that arm's measured prior cap width. Every
     * component pixel remains in a literal run; no midpoint split or gap fill. */
    private static BowCapProof completeUpBowCapProof(Glyph g, int width) {
        int w = g.right - g.left + 1, h = g.bottom - g.top + 1;
        if (w < 3 || h < 3 || (long) w * h > Integer.MAX_VALUE) return null;
        boolean[] ink = new boolean[w * h];
        int leftTop = g.bottom, rightTop = g.bottom;
        for (int p : g.pixels) {
            int x = p % width, y = p / width;
            ink[(y - g.top) * w + x - g.left] = true;
            if ((x - g.left) / (double) (w - 1) < .25) leftTop = Math.min(leftTop, y);
            if ((x - g.left) / (double) (w - 1) > .75) rightTop = Math.min(rightTop, y);
        }
        if (leftTop == g.bottom || rightTop == g.bottom) return null;
        var rows = new ArrayList<List<BowRowRun>>(h);
        for (int y = 0; y < h; y++) {
            var runs = new ArrayList<BowRowRun>(3);
            for (int x = 0; x < w; ) {
                if (!ink[y * w + x]) {
                    x++;
                    continue;
                }
                int first = x;
                while (x + 1 < w && ink[y * w + x + 1]) x++;
                runs.add(new BowRowRun(first, x));
                x++;
            }
            if (runs.isEmpty() || runs.size() > 3) return null;
            rows.add(runs);
        }
        int firstPair = Math.max(leftTop, rightTop) - g.top;
        BowRowRun priorLeft = null, priorRight = null, priorAux = null, priorMerged = null;
        int leftCapWidth = 0, rightCapWidth = 0, auxLeftWidth = 0, auxRightWidth = 0;
        int auxStart = -1, auxEnd = -1;
        boolean seenPair = false, joinedAux = false, merged = false;
        for (int y = 0; y < h; y++) {
            var runs = rows.get(y);
            if (y < firstPair) {
                if (runs.size() != 1 || priorMerged != null && !runs.get(0).touches(priorMerged))
                    return null;
                priorMerged = runs.get(0);
                continue;
            }
            if (merged) {
                if (runs.size() != 1 || !runs.get(0).touches(priorMerged)) return null;
                priorMerged = runs.get(0);
                continue;
            }
            if (runs.size() == 1) {
                if (!seenPair
                        || priorAux != null
                        || priorLeft == null
                        || priorRight == null
                        || !runs.get(0).touches(priorLeft)
                        || !runs.get(0).touches(priorRight)) return null;
                merged = true;
                priorMerged = runs.get(0);
                continue;
            }
            BowRowRun left = runs.get(0), right = runs.get(runs.size() - 1);
            if (!seenPair) {
                if (y != firstPair || runs.size() != 2) return null;
                if (priorMerged != null) {
                    if (leftTop < rightTop
                                    && (!left.touches(priorMerged) || right.touches(priorMerged))
                            || rightTop < leftTop
                                    && (!right.touches(priorMerged) || left.touches(priorMerged)))
                        return null;
                }
                if (left.first >= w * .25 || right.last <= w * .75) return null;
                seenPair = true;
            } else if (!left.touches(priorLeft)
                    || !right.touches(priorRight)
                    || left.touches(priorRight)
                    || right.touches(priorLeft)) return null;
            if (runs.size() == 3) {
                BowRowRun aux = runs.get(1);
                if (joinedAux
                        || auxStart < 0 && priorAux != null
                        || auxStart >= 0 && y != auxEnd + 1
                        || priorAux != null && !aux.touches(priorAux)) return null;
                if (priorLeft != null && aux.touches(priorLeft)
                        || priorRight != null && aux.touches(priorRight)
                        || priorAux != null && (left.touches(priorAux) || right.touches(priorAux)))
                    return null;
                if (auxStart < 0) {
                    auxStart = y;
                    auxLeftWidth = leftCapWidth;
                    auxRightWidth = rightCapWidth;
                    if (priorLeft != null && aux.touches(priorLeft)
                            || priorRight != null && aux.touches(priorRight)) return null;
                }
                auxEnd = y;
                priorAux = aux;
            } else if (priorAux != null) {
                boolean joinLeft = priorAux.touches(left), joinRight = priorAux.touches(right);
                if (joinLeft == joinRight) return null;
                int capWidth = joinLeft ? auxLeftWidth : auxRightWidth;
                int top = (joinLeft ? leftTop : rightTop) - g.top;
                if (capWidth < 1 || auxStart - top > capWidth || y - top > capWidth) return null;
                for (int row = auxStart; row <= auxEnd; row++) {
                    var pair = rows.get(row);
                    BowRowRun middle = pair.get(1), trunk = joinLeft ? pair.get(0) : pair.get(2);
                    if (joinLeft
                            ? middle.last - trunk.last > capWidth + 1
                            : trunk.first - middle.first > capWidth + 1) return null;
                }
                joinedAux = true;
                priorAux = null;
            }
            if (auxStart < 0) {
                leftCapWidth = Math.max(leftCapWidth, left.width());
                rightCapWidth = Math.max(rightCapWidth, right.width());
            }
            priorLeft = left;
            priorRight = right;
        }
        if (!seenPair || !merged || auxStart < 0 || !joinedAux) return null;
        double tip = 0;
        int tips = 0;
        for (int p : g.pixels)
            if (p / width >= g.bottom - (h - 1) * .15) {
                tip += p % width;
                tips++;
            }
        if (tips == 0) return null;
        double apex = (tip / tips - g.left) / (w - 1),
                ly = (leftTop - g.top) / (double) (h - 1),
                ry = (rightTop - g.top) / (double) (h - 1),
                tol = .14 + 1.0 / Math.min(w - 1, h - 1);
        if (apex < .2
                || apex > .85
                || ly > .45
                || ry > .45
                || !literalBowArmBandPath(
                        rows, leftTop - g.top, ly, (1 - ly) / apex, tol, false, w, h)
                || !literalBowArmBandPath(
                        rows, rightTop - g.top, ry, (1 - ry) / (1 - apex), tol, true, w, h))
            return null;
        var auxiliary = new ArrayList<BowRowRun>(auxEnd - auxStart + 1);
        for (int row = auxStart; row <= auxEnd; row++) auxiliary.add(rows.get(row).get(1));
        return new BowCapProof(auxStart, auxiliary);
    }

    /** Carry every actual run intersecting the old finite arm band; never bridge
     * a white row or favor one branch before its rival is checked. */
    private static boolean literalBowArmBandPath(
            List<List<BowRowRun>> rows,
            int first,
            double top,
            double slope,
            double tol,
            boolean right,
            int w,
            int h) {
        var reachable = new ArrayList<BowRowRun>();
        for (int row = first; row < h; row++) {
            double y = row / (double) (h - 1),
                    center = right ? 1 - (y - top) / slope : (y - top) / slope,
                    radius = tol * Math.sqrt(1 + slope * slope) / slope;
            int bandL = (int) Math.floor((center - radius) * (w - 1)) + 1,
                    bandR = (int) Math.ceil((center + radius) * (w - 1)) - 1;
            var next = new ArrayList<BowRowRun>();
            for (var run : rows.get(row)) {
                int a = Math.max(bandL, run.first), b = Math.min(bandR, run.last);
                if (a > b) continue;
                var actual = new BowRowRun(a, b);
                boolean connected = row == first;
                for (var prior : reachable) if (actual.touches(prior)) connected = true;
                if (connected) next.add(actual);
            }
            if (next.isEmpty()) return false;
            reachable = next;
        }
        return true;
    }

    /** Complete two-arm row support supplement; original approximate fit stays unchanged.
     * The bowMarks caller prepays 12count+64height+128 additional work.
     * No raw raster queries: only its complete immutable connected component. */
    private static boolean completeUpBowRowsStrict(Glyph g, int width) {
        int w = g.right - g.left + 1, h = g.bottom - g.top + 1;
        double tip = 0;
        int tips = 0, leftTop = g.bottom, rightTop = g.bottom;
        for (int p : g.pixels) {
            int x = p % width, y = p / width;
            if (y >= g.bottom - (h - 1) * .15) {
                tip += x;
                tips++;
            }
            if ((x - g.left) / (double) (w - 1) < .25) leftTop = Math.min(leftTop, y);
            if ((x - g.left) / (double) (w - 1) > .75) rightTop = Math.min(rightTop, y);
        }
        if (tips == 0) return false;
        double apex = (tip / tips - g.left) / (w - 1),
                ly = (leftTop - g.top) / (double) (h - 1),
                ry = (rightTop - g.top) / (double) (h - 1);
        double ls = (1 - ly) / apex,
                rs = (1 - ry) / (1 - apex),
                tol = .14 + 1.0 / Math.min(w - 1, h - 1);
        int[] minL = new int[h],
                maxL = new int[h],
                countL = new int[h],
                minR = new int[h],
                maxR = new int[h],
                countR = new int[h];
        Arrays.fill(minL, Integer.MAX_VALUE);
        Arrays.fill(minR, Integer.MAX_VALUE);
        Arrays.fill(maxL, -1);
        Arrays.fill(maxR, -1);
        for (int p : g.pixels) {
            int xx = p % width - g.left, yy = p / width - g.top;
            double x = xx / (double) (w - 1), y = yy / (double) (h - 1);
            double xl = apex * (y - ly) / (1 - ly),
                    xr = 1 - (1 - apex) * (y - ry) / (1 - ry),
                    mid = (xl + xr) * .5;
            if (x <= mid) {
                minL[yy] = Math.min(minL[yy], xx);
                maxL[yy] = Math.max(maxL[yy], xx);
                countL[yy]++;
            } else {
                minR[yy] = Math.min(minR[yy], xx);
                maxR[yy] = Math.max(maxR[yy], xx);
                countR[yy]++;
            }
        }
        boolean separated = false, merged = false;
        for (int y = 0; y < h; y++) {
            if (countL[y] > 0 && countL[y] != maxL[y] - minL[y] + 1
                    || countR[y] > 0 && countR[y] != maxR[y] - minR[y] + 1) return false;
            boolean two = countL[y] > 0 && countR[y] > 0 && maxL[y] + 1 < minR[y];
            // A complete pair can close into its thick/rounded tip once. An
            // interior crossbar cannot close then reopen the two physical arms.
            if (two) {
                if (merged) return false;
                separated = true;
            } else if (separated) merged = true;
        }
        return connectedBowArmRows(
                        minL,
                        maxL,
                        countL,
                        minR,
                        maxR,
                        countR,
                        w,
                        h,
                        leftTop - g.top,
                        ly,
                        ls,
                        tol,
                        false)
                && connectedBowArmRows(
                        minL,
                        maxL,
                        countL,
                        minR,
                        maxR,
                        countR,
                        w,
                        h,
                        rightTop - g.top,
                        ry,
                        rs,
                        tol,
                        true);
    }

    /** Each admitted interval is literal contiguous row ink. Carry every reachable
     * interval, so no favorable branch selection or missing-row bridge is hidden.
     * Bands may share ink only where their existing geometric tolerances overlap. */
    private static boolean connectedBowArmRows(
            int[] minL,
            int[] maxL,
            int[] countL,
            int[] minR,
            int[] maxR,
            int[] countR,
            int w,
            int h,
            int first,
            double top,
            double slope,
            double tol,
            boolean right) {
        int p0 = 0, q0 = -1, p1 = 0, q1 = -1;
        for (int row = first; row < h; row++) {
            double y = row / (double) (h - 1),
                    center = right ? 1 - (y - top) / slope : (y - top) / slope;
            double radius = tol * Math.sqrt(1 + slope * slope) / slope;
            int bandL = (int) Math.floor((center - radius) * (w - 1)) + 1;
            int bandR = (int) Math.ceil((center + radius) * (w - 1)) - 1;
            int a0 = 0, b0 = -1, a1 = 0, b1 = -1;
            for (int side = 0; side < 2; side++) {
                int count = side == 0 ? countL[row] : countR[row];
                if (count == 0) continue;
                int a = Math.max(bandL, side == 0 ? minL[row] : minR[row]);
                int b = Math.min(bandR, side == 0 ? maxL[row] : maxR[row]);
                if (a > b) continue;
                if (row != first
                        && !((p0 <= q0 && a <= q0 + 1 && b >= p0 - 1)
                                || (p1 <= q1 && a <= q1 + 1 && b >= p1 - 1))) continue;
                if (side == 0) {
                    a0 = a;
                    b0 = b;
                } else {
                    a1 = a;
                    b1 = b;
                }
            }
            if (a0 > b0 && a1 > b1) return false;
            p0 = a0;
            q0 = b0;
            p1 = a1;
            q1 = b1;
        }
        return true;
    }

    /** Whole-component bow-only nominal width from every literal row-run center.
     * No pen-width estimate, favorable run selection or new raster samples. */
    private static int completeBowStrokeSpanTwice(Glyph g, int width) {
        return completeBowStrokeSpanTwice(g, width, completeUpBowCapProof(g, width));
    }

    private static int completeBowStrokeSpanTwice(Glyph g, int width, BowCapProof proof) {
        boolean up = upBowShape(g, width) || capAwareUpBowShape(g, width, proof);
        boolean down = completeDownBowShape(g, width) || measuredDownBowShape(g, width);
        if (up == down || up && !completeUpBowRows(g, width, proof)) return -1;
        int w = g.right - g.left + 1, h = g.bottom - g.top + 1;
        boolean[] ink = new boolean[w * h];
        for (int p : g.pixels) ink[(p / width - g.top) * w + p % width - g.left] = true;
        if (down && !completeWideDownBowRows(ink, w, h)) return -1;
        int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
        boolean separated = false;
        for (int y = 0; y < h; y++) {
            int runs = 0;
            for (int x = 0; x < w; ) {
                if (!ink[y * w + x]) {
                    x++;
                    continue;
                }
                int left = x;
                while (x + 1 < w && ink[y * w + x + 1]) x++;
                int centerTwice = left + x;
                minimum = Math.min(minimum, centerTwice);
                maximum = Math.max(maximum, centerTwice);
                runs++;
                x++;
            }
            if (runs >= 2) separated = true;
        }
        return separated && minimum <= maximum ? maximum - minimum : -1;
    }

    /** Every complete wide down-bow pixel belongs to its connected cap, one
     * attached raster shoulder, or one of two ordered independently ending legs. */
    private static boolean completeWideDownBowRows(boolean[] ink, int width, int height) {
        int capEnd = 0, priorLeft = -1, priorRight = -1;
        for (; capEnd < height; capEnd++) {
            int[] bounds = new int[4];
            int runs = literalBowRowRuns(ink, width, capEnd, bounds);
            if (runs != 1) break;
            if (capEnd > 0 && (bounds[0] > priorRight + 1 || bounds[1] < priorLeft - 1))
                return false;
            priorLeft = bounds[0];
            priorRight = bounds[1];
        }
        if (capEnd < 2 || capEnd >= height) return false;
        for (int shoulder = 0; shoulder <= 1; shoulder++) {
            int first = capEnd + shoulder;
            if (first >= height) continue;
            if (shoulder == 1) {
                boolean attached = true;
                for (int x = 0; x < width; x++)
                    if (ink[capEnd * width + x] && (x < priorLeft - 1 || x > priorRight + 1))
                        attached = false;
                if (!attached) continue;
            }
            int[] previous = new int[4];
            if (literalBowRowRuns(ink, width, first, previous) != 2) continue;
            if (!bowIntervalTouchesRow(ink, width, first - 1, previous[0], previous[1])
                    || !bowIntervalTouchesRow(ink, width, first - 1, previous[2], previous[3]))
                continue;
            int leftRows = 1, rightRows = 1;
            boolean leftAlive = true, rightAlive = true, valid = true;
            for (int row = first + 1; row < height; row++) {
                int[] bounds = new int[4];
                int runs = literalBowRowRuns(ink, width, row, bounds);
                if (runs == 2) {
                    if (!leftAlive
                            || !rightAlive
                            || bounds[0] > previous[1] + 1
                            || bounds[1] < previous[0] - 1
                            || bounds[2] > previous[3] + 1
                            || bounds[3] < previous[2] - 1
                            || bounds[0] <= previous[3] + 1 && bounds[1] >= previous[2] - 1
                            || bounds[2] <= previous[1] + 1 && bounds[3] >= previous[0] - 1) {
                        valid = false;
                        break;
                    }
                    previous = bounds;
                    leftRows++;
                    rightRows++;
                } else if (runs == 1) {
                    boolean touchesLeft =
                            bounds[0] <= previous[1] + 1 && bounds[1] >= previous[0] - 1;
                    boolean touchesRight =
                            bounds[0] <= previous[3] + 1 && bounds[1] >= previous[2] - 1;
                    boolean left = leftAlive && touchesLeft, right = rightAlive && touchesRight;
                    if (left == right || touchesLeft && touchesRight) {
                        valid = false;
                        break;
                    }
                    if (left) {
                        previous[0] = bounds[0];
                        previous[1] = bounds[1];
                        rightAlive = false;
                        leftRows++;
                    } else {
                        previous[2] = bounds[0];
                        previous[3] = bounds[1];
                        leftAlive = false;
                        rightRows++;
                    }
                } else {
                    valid = false;
                    break;
                }
            }
            if (valid && leftRows >= capEnd && rightRows >= capEnd) return true;
        }
        return false;
    }

    /** Returns a refusal sentinel on a third run; no branch is discarded. */
    private static int literalBowRowRuns(boolean[] ink, int width, int row, int[] bounds) {
        int runs = 0;
        for (int x = 0; x < width; x++) {
            if (!ink[row * width + x]) continue;
            if (runs == 2) return 3;
            bounds[2 * runs] = x;
            while (x + 1 < width && ink[row * width + x + 1]) x++;
            bounds[2 * runs + 1] = x;
            runs++;
        }
        return runs;
    }

    private static boolean bowIntervalTouchesRow(
            boolean[] ink, int width, int row, int left, int right) {
        for (int x = Math.max(0, left - 1); x <= Math.min(width - 1, right + 1); x++)
            if (ink[row * width + x]) return true;
        return false;
    }

    /** A complete cap and two measured legs, including a thick engraved cap. */
    private static boolean measuredDownBowShape(Glyph g, int width) {
        int h = g.bottom - g.top + 1, w = g.right - g.left + 1;
        if (w < 5 || h < 5) return false;
        // The caller prepays the larger contrast rectangle. This local occupancy
        // table uses only the already extracted complete component, not gray reads.
        boolean[] ink = new boolean[w * h];
        int[] count = new int[h], left = new int[h], right = new int[h];
        java.util.Arrays.fill(left, Integer.MAX_VALUE);
        java.util.Arrays.fill(right, -1);
        for (int p : g.pixels) {
            int y = p / width - g.top, x = p % width - g.left;
            ink[y * w + x] = true;
            count[y]++;
            left[y] = Math.min(left[y], x);
            right[y] = Math.max(right[y], x);
        }
        int first = 0;
        if (count[0] < w * .9f) {
            if (count[1] < w * .9f || count[0] == 0 || left[0] < left[1] || right[0] > right[1])
                return false;
            first = 1;
        }
        int capEnd = first;
        while (capEnd < h && count[capEnd] >= w * .9f) capEnd++;
        int capRows = capEnd - first;
        if (capRows < 2) return false;
        // One raster shoulder is permitted only when each of its actual pixels
        // touches the fully observed cap above. All subsequent rows are two legs.
        for (int shoulder = 0; shoulder <= 1; shoulder++) {
            int legStart = capEnd + shoulder;
            if (h - legStart < capRows || legStart >= h) continue;
            boolean valid = true;
            if (shoulder == 1)
                for (int x = 0; x < w; x++)
                    if (ink[capEnd * w + x]) {
                        boolean attached = false;
                        for (int dx = -1; dx <= 1; dx++)
                            if (x + dx >= 0 && x + dx < w && ink[(capEnd - 1) * w + x + dx])
                                attached = true;
                        if (!attached) {
                            valid = false;
                            break;
                        }
                    }
            int lastL0 = -1, lastL1 = -1, lastR0 = -1, lastR1 = -1;
            for (int y = legStart; valid && y < h; y++) {
                int[] bounds = new int[4];
                int runs = 0;
                boolean in = false;
                for (int x = 0; x < w; x++) {
                    boolean at = ink[y * w + x];
                    if (at && !in) {
                        if (runs == 2) {
                            valid = false;
                            break;
                        }
                        bounds[runs * 2] = x;
                        in = true;
                    }
                    if (in && (!at || x == w - 1)) {
                        bounds[runs * 2 + 1] = at ? x : x - 1;
                        runs++;
                        in = false;
                    }
                }
                if (!valid || runs != 2 || bounds[1] >= w * .30f || bounds[2] <= w * .70f) {
                    valid = false;
                    break;
                }
                if (y > legStart
                        && (bounds[0] > lastL1 + 1
                                || bounds[1] < lastL0 - 1
                                || bounds[2] > lastR1 + 1
                                || bounds[3] < lastR0 - 1)) {
                    valid = false;
                    break;
                }
                lastL0 = bounds[0];
                lastL1 = bounds[1];
                lastR0 = bounds[2];
                lastR1 = bounds[3];
            }
            if (valid) return true;
        }
        return false;
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

    private static boolean ruleJoinedAccent(
            Anchor note,
            List<Anchor> notes,
            List<Glyph> context,
            byte[] labels,
            byte[] gray,
            int width,
            int height) {
        float gap = note.gap;
        if (!Float.isFinite(gap) || gap < 6 || gap > 64) return false;
        int left = Math.max(0, Math.round(note.x - gap * 1.6f)),
                right = Math.min(width - 1, Math.round(note.x + gap * 1.6f));
        int top = Math.max(0, Math.round(note.y - gap * 5.8f)),
                bottom = Math.min(height - 1, Math.round(note.y + gap * 5.8f));
        int w = right - left + 1, h = bottom - top + 1;
        if (w <= 0 || h <= 0) return false;
        boolean[] rules = new boolean[h], seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int y = top; y <= bottom; y++)
            rules[y - top] = recoveryStaffRule(gray, width, height, Math.round(note.x), y, gap);
        for (int seed = 0; seed < seen.length; seed++) {
            int sx = seed % w, sy = seed / w;
            if (seen[seed] || rules[sy] || (gray[(top + sy) * width + left + sx] & 255) >= 155)
                continue;
            int take = 0, size = 1, minX = sx, maxX = sx, minY = sy, maxY = sy;
            boolean clipped = false, touches = false;
            queue[0] = seed;
            seen[seed] = true;
            while (take < size) {
                int at = queue[take++], x = at % w, y = at / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                clipped |= x == 0 || x == w - 1 || y == 0 || y == h - 1;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                        int p = yy * w + xx;
                        if ((gray[(top + yy) * width + left + xx] & 255) >= 155) continue;
                        if (rules[yy]) {
                            touches = true;
                            continue;
                        }
                        if (!seen[p]) {
                            seen[p] = true;
                            queue[size++] = p;
                        }
                    }
            }
            float gw = maxX - minX + 1,
                    gh = maxY - minY + 1,
                    gx = left + (minX + maxX) * .5f,
                    gy = top + (minY + maxY) * .5f;
            if (clipped
                    || !touches
                    || gw < gap * .75f
                    || gw > gap * 2.1f + 1
                    || gh < gap * .35f
                    || gh > gap * 1.25f
                    || Math.abs(gx - note.x) > gap * .7f
                    || Math.abs(gy - note.y) < gap * .7f
                    || Math.abs(gy - note.y) > gap * 5.5f) continue;
            int[] pixels = new int[size];
            int notation = 0;
            for (int i = 0; i < size; i++) {
                int p = (top + queue[i] / w) * width + left + queue[i] % w;
                pixels[i] = p;
                byte label = labels[p];
                if (label == OmrMeasurePostProcessor.NOTEHEAD
                        || label == OmrMeasurePostProcessor.STEM_OR_REST
                        || label == OmrMeasurePostProcessor.CLEF_OR_KEY) notation++;
            }
            if (notation > size * .25f
                    || !OpenChevron.matches(
                            pixels, width, left + minX, top + minY, left + maxX, top + maxY))
                continue;
            Glyph g = new Glyph(left + minX, top + minY, left + maxX, top + maxY, size, pixels);
            if (nearHead(g, notes)
                    || detachedThreeOwns(g, context, width, gap)
                    || completeBowOwns(g, gray, width, height, gap)) continue;
            return true;
        }
        return false;
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
        return detectWithWordBodies(
                labels,
                gray,
                width,
                height,
                notes,
                wordBodies,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE);
    }

    static int[] detectWithWordBodies(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Anchor> notes,
            List<ExpressiveWordBodyInk.Body> wordBodies,
            long producerLimit,
            long admissionLimit,
            long storageLimit) {
        return detectWithWordBodies(
                labels,
                gray,
                width,
                height,
                notes,
                wordBodies,
                producerLimit,
                admissionLimit,
                storageLimit,
                null,
                null);
    }

    static int[] detectOwnedCarets(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Anchor> notes,
            int[] current,
            PrintedBeamSource.CaretEvidence evidence,
            long producerLimit,
            long admissionLimit,
            long storageLimit) {
        if (!evidence.inputs(labels, gray, width, height, notes)) {
            evidence.discard();
            return new int[notes.size()];
        }
        return detectWithWordBodies(
                labels,
                gray,
                width,
                height,
                notes,
                List.of(),
                producerLimit,
                admissionLimit,
                storageLimit,
                evidence,
                current);
    }

    private static int[] detectWithWordBodies(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Anchor> notes,
            List<ExpressiveWordBodyInk.Body> wordBodies,
            long producerLimit,
            long admissionLimit,
            long storageLimit,
            PrintedBeamSource.CaretEvidence evidence,
            int[] current) {
        int[] result = new int[notes.size()];
        if (notes.isEmpty() || labels == null || labels.length != width * height) return result;
        // Raw components retain symbols that the semantic model omitted. Never erase staff
        // lines: doing so would manufacture dashes/dots from beams, stems and noteheads.
        boolean raw = gray != null && gray.length == labels.length;
        boolean[] seen = new boolean[labels.length];
        int[] queue = new int[labels.length];
        List<Glyph> glyphs = new ArrayList<>();
        java.util.IdentityHashMap<Glyph, Integer> joinedOwners = new java.util.IdentityHashMap<>();
        java.util.Set<Glyph> provedJoinedAccents =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        SlurEndpointAccents.Work accentWork = new SlurEndpointAccents.Work();
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
            if (end < 3) continue;
            if (right - left > width * .025f || bottom - top > height * .018f) {
                if (raw && end <= SlurEndpointDots.MAX_PIXELS) {
                    if (accentWork.use(end))
                        for (var mark :
                                SlurEndpointAccents.partition(
                                        gray,
                                        width,
                                        height,
                                        java.util.Arrays.copyOf(queue, end),
                                        notes,
                                        accentWork)) {
                            Glyph body =
                                    new Glyph(
                                            mark.left(),
                                            mark.top(),
                                            mark.right(),
                                            mark.bottom(),
                                            mark.pixels().length,
                                            mark.pixels());
                            glyphs.add(body);
                            joinedOwners.put(body, mark.owner());
                            provedJoinedAccents.add(body);
                        }
                    SlurEndpointDots.Result split =
                            SlurEndpointDots.partition(
                                    gray,
                                    width,
                                    height,
                                    java.util.Arrays.copyOf(queue, end),
                                    notes);
                    for (SlurEndpointDots.Dot dot : split.dots()) {
                        Glyph body =
                                new Glyph(
                                        dot.left(),
                                        dot.top(),
                                        dot.right(),
                                        dot.bottom(),
                                        dot.pixels().length,
                                        dot.pixels());
                        glyphs.add(body);
                        joinedOwners.put(body, dot.owner());
                    }
                }
                continue;
            }
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
        int fringeStart = candidates.size();
        if (raw)
            candidates.addAll(
                    closedDotFringes(originalGlyphs, joinedOwners, notes, gray, width, height));
        List<Glyph> printedClaims = new ArrayList<>();
        CompoundClaims compoundClaims = new CompoundClaims(notes.size());
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
                                .collect(java.util.stream.Collectors.toList())
                        : List.of();
        List<Glyph> originalReflected =
                raw
                        ? originalGlyphs.stream()
                                .map(
                                        g ->
                                                new Glyph(
                                                        -g.right, g.top, -g.left, g.bottom, g.count,
                                                        g.pixels))
                                .collect(java.util.stream.Collectors.toList())
                        : List.of();
        int[] legacyResult = null;
        DetachedCaretBodies.Page caretPage =
                raw
                        ? new DetachedCaretBodies.Page(
                                gray,
                                labels,
                                width,
                                height,
                                notes,
                                producerLimit,
                                admissionLimit,
                                storageLimit)
                        : null;
        CaretAccepted acceptedCaretBodies = CaretAccepted.create(caretPage);
        CaretPass caret = null;
        try {
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 1) {
                    legacyResult = result;
                    if (!raw) break;

                    caret =
                            caretPass(
                                    caretPage,
                                    originalGlyphs,
                                    joinedOwners,
                                    notes,
                                    legacyResult,
                                    labels,
                                    width,
                                    height,
                                    evidence,
                                    current);
                    candidates = caret.glyphs;
                    if (candidates.isEmpty()) break;
                    if (acceptedCaretBodies == null) caretPage.refuse();
                    List<Glyph> legacyCaretPriority = acceptedCaretBodies.snapshot();
                    caretPage.a(64L + 16L * notes.size(), 16L * notes.size() + 2048);
                    caretPage.a(
                            16L * (claimedAccents.size() + legacyCaretPriority.size()) + 64,
                            16L * (claimedAccents.size() + legacyCaretPriority.size()) + 256);
                    result = new int[notes.size()];
                    compoundClaims = new CompoundClaims(notes.size());
                    claimedAccents = new ArrayList<>(claimedAccents);
                    printedClaims = new ArrayList<>(legacyCaretPriority);
                }
                for (int glyphIndex = 0; glyphIndex < candidates.size(); glyphIndex++) {
                    boolean closedDotFringe = pass == 0 && glyphIndex >= fringeStart;
                    Glyph glyph = candidates.get(glyphIndex);
                    if (pass == 1) {
                        caretPage.a(64, 128);
                        DetachedCaretCosts.admissionGlyph(
                                caretPage,
                                caretCostInk(glyph),
                                caret.context,
                                caret.notes,
                                width,
                                height,
                                evidence != null && caret.far.containsKey(glyph));
                    }
                    List<Glyph> context =
                            (pass == 0 && paperCandidates.contains(glyph))
                                    ? glyphs
                                    : originalGlyphs;
                    List<Glyph> reflectedContext =
                            (pass == 0 && paperCandidates.contains(glyph))
                                    ? reflectedGlyphs
                                    : originalReflected;
                    // A clearer subset cannot add a second meaning to an already accepted printed
                    // body.
                    if (pass == 1) prepayCaretOverlap(caretPage, glyph, printedClaims);
                    if ((pass == 1 || (pass == 0 && paperCandidates.contains(glyph)))
                            && overlapsPrintedClaim(glyph, printedClaims)) continue;
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
                    Integer joinedOwner = joinedOwners.get(glyph);
                    boolean completeRawBody =
                            pass == 0
                                    && raw
                                    && joinedOwner == null
                                    && glyphIndex >= faintAccents.size()
                                    && glyphIndex < faintAccents.size() + originalGlyphs.size();
                    for (int n = 0; n < notes.size(); n++) {
                        if (joinedOwner != null && n != joinedOwner) continue;
                        Anchor note = notes.get(n);
                        float dx = Math.abs(glyph.x() - note.x) / note.gap,
                                dy = Math.abs(glyph.y() - note.y) / note.gap;
                        if (dx > .7f || dy < .7f || dy > 8f) continue;
                        if (closedDotFringe && dy > 3f) continue;
                        boolean faint = (pass == 0 && faintAccents.contains(glyph));
                        boolean softFaint = (pass == 0 && softFaintAccents.contains(glyph));
                        float glyphWidth = glyph.right - glyph.left + 1,
                                glyphHeight = glyph.bottom - glyph.top + 1;
                        if (faint
                                && (glyphWidth < note.gap * .75f
                                        || glyphWidth > note.gap * (softFaint ? 2.6f : 2.1f) + 1
                                        || glyphHeight < note.gap * .35f
                                        || glyphHeight > note.gap * (softFaint ? 1.65f : 1.25f)))
                            continue;
                        int candidate =
                                (faint || provedJoinedAccents.contains(glyph))
                                        ? NoteArticulation.ACCENT
                                        : classify(glyph, width, note.gap, glyph.y() < note.y);
                        if (candidate == 0)
                            candidate = roundedAngular(glyph, width, note.gap, glyph.y() < note.y);
                        if (candidate == NoteArticulation.MARCATO
                                && interiorCrossbar(glyph, width, .45f)) continue;
                        // A faint rounded dash needs nearby note ownership, not a lyric
                        // extender several spaces beyond the staff.
                        if (candidate == 0 && dy <= 3f && roundedTenuto(glyph, width, note.gap))
                            candidate = NoteArticulation.TENUTO;
                        // Apply the existing compact raster law to the complete raw body before
                        // dark-core extraction can discard its literal antialiased boundary cells.
                        // These upper bounds only skip bodies that cannot be staccato under that
                        // law.
                        if (candidate == 0
                                && completeRawBody
                                && glyphWidth <= note.gap * .62f
                                && glyphHeight <= note.gap * .62f
                                && rasterCoreMark(glyph, width, note.gap)
                                        == NoteArticulation.STACCATO)
                            candidate = NoteArticulation.STACCATO;
                        if (closedDotFringe) candidate = NoteArticulation.STACCATO;
                        // A blurred detached body must prove its existing shape again on dark ink.
                        // Keep all ownership, semantic notation, word, staff and dot guards below.
                        boolean recoveredCore = false;
                        if (candidate == 0 && printedCore != null) {
                            candidate = classify(printedCore, width, note.gap, glyph.y() < note.y);
                            if (candidate == 0)
                                candidate =
                                        roundedAngular(
                                                printedCore, width, note.gap, glyph.y() < note.y);
                            if (candidate == 0) {
                                int raster = rasterCoreMark(printedCore, width, note.gap);
                                // Horizontal recovery still needs close note ownership, not a lyric
                                // extender.
                                if (raster != NoteArticulation.TENUTO || dy <= 3f)
                                    candidate = raster;
                            }
                            if (candidate == NoteArticulation.MARCATO
                                    && (interiorCrossbar(printedCore, width, .45f)
                                            || interiorCrossbar(glyph, width, .45f))) candidate = 0;
                            recoveredCore = candidate != 0;
                        }
                        if (pass == 1
                                && (candidate != NoteArticulation.MARCATO || glyph.y() >= note.y))
                            continue;
                        if (raw
                                && candidate != 0
                                && detachedThreeOwns(glyph, originalGlyphs, width, note.gap))
                            continue;
                        if (raw
                                && candidate != 0
                                && glyph.y() < note.y
                                && completeBowOwns(glyph, gray, width, height, note.gap)) continue;
                        if (raw && !faint && candidate != 0) {
                            if (bodyContrast < 0)
                                bodyContrast =
                                        printedBodyContrast(glyph, gray, width, height) ? 1 : 0;
                            if (bodyContrast == 0) continue;
                        }
                        if (raw
                                && candidate == NoteArticulation.TENUTO
                                && (recoveredCore || (pass == 0 && paperCandidates.contains(glyph)))
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
                                        && (pass == 1
                                                        && evidence != null
                                                        && caret.far.containsKey(glyph)
                                                ? evidence.proves(
                                                        n,
                                                        note,
                                                        glyph.left,
                                                        glyph.top,
                                                        glyph.right,
                                                        glyph.bottom)
                                                : beamOwnedMarcato(
                                                        glyph, note, gray, width, height));
                        if (candidate == 0
                                || dy > 6f && !stemOwnedDot && !stemOwnedMarcato
                                || dy > 5.5f
                                        && candidate != NoteArticulation.MARCATO
                                        && !stemOwnedDot) continue;
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
                                && (pass == 0 && paperCandidates.contains(glyph))
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
                                && endingNumberDot(
                                        glyph, context, note.gap, gray, labels, width, height))
                            continue;
                        if (semanticNotation
                                && (!raw
                                        || (candidate != NoteArticulation.STACCATO
                                                && (candidate != NoteArticulation.STACCATISSIMO
                                                        || !filledTaper(
                                                                glyph, width, glyph.y() < note.y)))
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
                        if (candidate == NoteArticulation.TENUTO && nearHead(glyph, notes))
                            continue;
                        if (raw
                                && candidate == NoteArticulation.TENUTO
                                && ShadedLedgerMarkOwnership.provedWhite(
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
                                && (pass == 0 && paperCandidates.contains(glyph))
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
                                && (pass == 0 && paperCandidates.contains(glyph))
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
                                        || compactLedgerStaff(glyph, note, gray, width, height)))
                            continue;
                        if (raw
                                && candidate == NoteArticulation.TENUTO
                                && completeRasterLedger(glyph, note, notes, gray, width, height))
                            continue;
                        if (raw
                                && candidate == NoteArticulation.TENUTO
                                && glyph.bottom - glyph.top + 1
                                        <= Math.max(2, Math.round(note.gap * .23f))
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
                                        note.gap)
                                && !paperSeparatedTenuto(glyph, note, notes, gray, width, height))
                            continue;
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
                                                                        glyph, context, note,
                                                                        width),
                                                        closedDotFringe)))) continue;
                        // A small off-axis dot beside a head is a duration dot, not staccato.
                        if (candidate == NoteArticulation.STACCATO && dx > .35f) continue;
                        if (raw && !joinedOwners.containsKey(glyph))
                            compoundClaims.eligible(glyph, n, candidate, note, gray, width, height);
                        double score = dx * 3 + dy;
                        if (score < distance) {
                            distance = score;
                            best = n;
                            mark = candidate;
                        }
                    }
                    if (best < 0) continue;
                    if (pass == 1 && caret.blocked[best]) continue;
                    boolean ownedFar =
                            pass == 1 && evidence != null && caret.far.containsKey(glyph);
                    if (ownedFar
                            && (!evidence.sameAttack(best, caret.far.get(glyph))
                                    || !evidence.proves(
                                            best,
                                            notes.get(best),
                                            glyph.left,
                                            glyph.top,
                                            glyph.right,
                                            glyph.bottom))) continue;
                    if ((mark == NoteArticulation.STACCATO
                                    || mark == NoteArticulation.STACCATISSIMO
                                    || mark == NoteArticulation.TENUTO)
                            && ExpressiveWordBodyInk.owns(
                                    wordBodies, glyph.pixels, glyph.count, width)) continue;
                    if (ownedFar) evidence.claim(best);
                    if ((pass == 0 && faintAccents.contains(glyph))) claimedAccents.add(glyph);
                    if (!(pass == 0 && paperCandidates.contains(glyph))) printedClaims.add(glyph);
                    if (pass == 0 && acceptedCaretBodies != null) acceptedCaretBodies.add(glyph);
                    compoundClaims.claim(glyph, best, mark);
                    Anchor owner = notes.get(best);
                    // One mark on a chord affects that chord, never another staff or another onset.
                    for (int n = 0; n < notes.size(); n++) {
                        Anchor note = notes.get(n);
                        if (note.staff == owner.staff
                                && Math.abs(note.x - owner.x) < owner.gap * .45f) {
                            result[n] |= mark;
                            compoundClaims.assignment(n, mark);
                        }
                    }
                }
                if (pass == 0) {
                    if (raw) compoundClaims.resolve(result, notes, gray, width, height);
                    if (raw)
                        for (int i = 0; i < notes.size(); i++) {
                            Anchor note = notes.get(i);
                            if ((result[i] & NoteArticulation.ACCENT) == 0
                                    && ruleJoinedAccent(
                                            note,
                                            notes,
                                            originalGlyphs,
                                            labels,
                                            gray,
                                            width,
                                            height))
                                for (int j = 0; j < notes.size(); j++)
                                    if (notes.get(j).staff == note.staff
                                            && Math.abs(notes.get(j).x - note.x) < note.gap * .45f)
                                        result[j] |= NoteArticulation.ACCENT;
                            if ((result[i] & NoteArticulation.MARCATO) == 0
                                    && ruleJoinedMarcato(note, labels, gray, width, height))
                                for (int j = 0; j < notes.size(); j++)
                                    if (notes.get(j).staff == note.staff
                                            && Math.abs(notes.get(j).x - note.x) < note.gap * .45f)
                                        result[j] |= NoteArticulation.MARCATO;
                            if ((result[i] & NoteArticulation.STACCATISSIMO) == 0
                                    && ruleJoinedWedge(
                                            note,
                                            notes,
                                            originalGlyphs,
                                            gray,
                                            labels,
                                            width,
                                            height))
                                for (int j = 0; j < notes.size(); j++)
                                    if (notes.get(j).staff == note.staff
                                            && Math.abs(notes.get(j).x - note.x) < note.gap * .45f)
                                        result[j] |= NoteArticulation.STACCATISSIMO;
                            if ((result[i] & NoteArticulation.STACCATO) == 0
                                    && textJoinedDot(
                                            note, originalGlyphs, labels, gray, width, height))
                                for (int j = 0; j < notes.size(); j++)
                                    if (notes.get(j).staff == note.staff
                                            && Math.abs(notes.get(j).x - note.x) < note.gap * .45f)
                                        result[j] |= NoteArticulation.STACCATO;
                        }
                } else {
                    compoundClaims.resolve(result, notes, gray, width, height);
                    if (!caretPage.finish(gray, labels, width, height, notes)) {
                        if (evidence != null) evidence.discard();
                        return legacyResult;
                    }
                    for (int i = 0; i < result.length; i++)
                        if (!caret.blocked[i]) legacyResult[i] |= result[i];
                    result = legacyResult;
                }
            }
        } catch (DetachedCaretBodies.Refusal refusal) {
            if (evidence != null) evidence.discard();
            if (legacyResult == null) throw refusal;
            return legacyResult;
        }
        return result;
    }

    /** Optional bookkeeping records actual accepted bodies without changing any legacy claims. */
    private static final class CaretAccepted {
        final DetachedCaretBodies.Page page;
        Glyph[] bodies = new Glyph[0];
        int count;

        private CaretAccepted(DetachedCaretBodies.Page page) {
            this.page = page;
        }

        static CaretAccepted create(DetachedCaretBodies.Page page) {
            if (page == null || page.failed) return null;
            try {
                page.p(64, 128);
                return new CaretAccepted(page);
            } catch (DetachedCaretBodies.Refusal refusal) {
                return null;
            }
        }

        void add(Glyph glyph) {
            if (page.failed) return;
            try {
                page.p(8, 0);
                if (count == bodies.length) {
                    long next = Math.max((long) count + 1, Math.max(8L, 2L * bodies.length));
                    if (next > Integer.MAX_VALUE) page.refuse();
                    page.p(2 * next + 2L * count + 64, 8 * next + 64);
                    bodies = Arrays.copyOf(bodies, (int) next);
                }
                bodies[count++] = glyph;
            } catch (DetachedCaretBodies.Refusal refusal) {
                bodies = null;
                count = 0;
            }
        }

        List<Glyph> snapshot() {
            page.p(2L * count + 64, 8L * count + 256);
            return java.util.Collections.unmodifiableList(
                    Arrays.asList(Arrays.copyOf(bodies, count)));
        }
    }

    private static void prepayCaretOverlap(
            DetachedCaretBodies.Page page, Glyph candidate, List<Glyph> claimed) {
        page.a(32L * claimed.size() + 64, 0);
        for (Glyph body : claimed) {
            if (candidate.right < body.left
                    || candidate.left > body.right
                    || candidate.bottom < body.top
                    || candidate.top > body.bottom) continue;
            page.a(
                    DetachedCaretCosts.sum(
                            64,
                            32L * candidate.count,
                            DetachedCaretCosts.mul(
                                    4, DetachedCaretCosts.mul(candidate.count, body.count))),
                    0);
        }
    }

    private record CaretPass(
            DetachedCaretBodies.Page page,
            List<Glyph> glyphs,
            boolean[] blocked,
            java.util.IdentityHashMap<Glyph, Integer> far,
            DetachedCaretCosts.Ink[] context,
            DetachedCaretCosts.Note[] notes) {}

    private static DetachedCaretCosts.Ink caretCostInk(Glyph g) {
        return new DetachedCaretCosts.Ink(g.left, g.top, g.right, g.bottom, g.count);
    }

    /** Optional eligibility, never a replacement for common classification or role guards. */
    private static int caretRawKind(
            Glyph g,
            int width,
            float gap,
            DetachedCaretBodies.Page page,
            boolean complete,
            int height) {
        page.p(64, 128);
        var cost = caretCostInk(g);
        DetachedCaretCosts.producer(page, DetachedCaretCosts.classify(cost));
        int kind = classify(g, width, gap, true);
        if (kind == 0) {
            page.p(64, 128);
            DetachedCaretCosts.producer(page, DetachedCaretCosts.rounded(cost));
            kind = roundedAngular(g, width, gap, true);
        }
        if (!complete) return kind; // Any old raw kind abstains; no unused contrast is evaluated.
        if (kind == NoteArticulation.MARCATO) {
            page.p(64, 128);
            DetachedCaretCosts.producer(page, DetachedCaretCosts.crossbar(cost));
            if (interiorCrossbar(g, width, .45f)) return 0;
        }
        if (kind != 0) {
            page.p(64, 128);
            DetachedCaretCosts.producer(page, DetachedCaretCosts.contrast(cost, width, height));
            if (!printedBodyContrast(g, page.raw, width, height)) return 0;
        }
        return kind;
    }

    private static int caretRoot(int[] roots, int n) {
        while (roots[n] != n) n = roots[n];
        return n;
    }

    private static CaretPass caretPass(
            DetachedCaretBodies.Page page,
            List<Glyph> originals,
            java.util.IdentityHashMap<Glyph, Integer> joined,
            List<Anchor> notes,
            int[] legacy,
            byte[] labels,
            int width,
            int height,
            PrintedBeamSource.CaretEvidence evidence,
            int[] current) {
        int N = notes.size(), G = originals.size();
        page.p(32L * (N + G) + 256, 128L * (N + G) + 2048);
        int[] roots = new int[N];
        boolean[] blocked = new boolean[N], marked = new boolean[N];
        Integer[] sorted = new Integer[N];
        float maxGap = 0;
        var noteCosts = new DetachedCaretCosts.Note[N];
        for (int i = 0; i < N; i++) {
            page.p(16, 0);
            Anchor n = notes.get(i);
            if (!Float.isFinite(n.x)
                    || !Float.isFinite(n.y)
                    || !Float.isFinite(n.gap)
                    || n.x < 0
                    || n.x >= width
                    || n.y < 0
                    || n.y >= height
                    || n.gap < 3
                    || n.gap > 64) page.refuse();
            roots[i] = i;
            sorted[i] = i;
            maxGap = Math.max(maxGap, n.gap);
            noteCosts[i] = new DetachedCaretCosts.Note(n.x, n.y, n.gap, n.staff);
        }
        // Transitive attack exclusion is conservative; every anchor remains an admission rival.
        for (int i = 0; i < N; i++)
            for (int j = i + 1; j < N; j++) {
                page.p(8, 0);
                Anchor a = notes.get(i), b = notes.get(j);
                if (a.staff == b.staff && Math.abs(a.x - b.x) < Math.max(a.gap, b.gap) * .45f) {
                    page.p(4L * N + 8, 0);
                    int x = caretRoot(roots, i), y = caretRoot(roots, j);
                    roots[y] = x;
                }
            }
        page.p(4L * N * N + 16L * N, 0);
        for (int i = 0; i < N; i++)
            if ((legacy[i] | (current == null ? 0 : current[i])) != 0)
                marked[caretRoot(roots, i)] = true;
        for (int i = 0; i < N; i++) blocked[i] = marked[caretRoot(roots, i)];
        java.util.Arrays.sort(
                sorted,
                (a, b) -> {
                    page.p(4, 0);
                    return Float.compare(notes.get(a).x, notes.get(b).x);
                });
        var context = new DetachedCaretCosts.Ink[G];
        for (int i = 0; i < G; i++) {
            page.p(8, 0);
            context[i] = caretCostInk(originals.get(i));
        }
        float[] gaps = new float[N];
        boolean[] empty = new boolean[N];
        int[] kinds = new int[N];
        List<Glyph> out = new ArrayList<>();
        var farBodies = new java.util.IdentityHashMap<Glyph, Integer>();
        for (Glyph seed : originals) {
            page.p(16, 0);
            if (joined.containsKey(seed) || seed.count < 3) continue;
            int notation = 0;
            boolean allRaw = true;
            for (int p : seed.pixels) {
                page.p(8, 0);
                if (p < 0 || p >= page.raw.length) page.refuse();
                if ((page.raw[p] & 255) >= 155) allRaw = false;
                byte label = labels[p];
                if (label == OmrMeasurePostProcessor.NOTEHEAD
                        || label == OmrMeasurePostProcessor.STEM_OR_REST
                        || label == OmrMeasurePostProcessor.CLEF_OR_KEY
                        || label == OmrMeasurePostProcessor.STAFF) notation++;
            }
            if (!allRaw || notation > seed.count * .25f) continue;
            float sx = seed.x(), sy = seed.y();
            int low = 0, hi = N;
            while (low < hi) {
                page.p(8, 0);
                int mid = (low + hi) >>> 1;
                if (notes.get(sorted[mid]).x < sx - .7f * maxGap) low = mid + 1;
                else hi = mid;
            }
            int groups = 0;
            for (int k = low; k < N; k++) {
                page.p(16, 0);
                int index = sorted[k];
                Anchor n = notes.get(index);
                if (n.x > sx + .7f * maxGap) break;
                float dy = (n.y - sy) / n.gap;
                if (dy < .7f
                        || dy > 8
                        || Math.abs(sx - n.x) > .7f * n.gap
                        || seed.right - seed.left + 1 > 2.1f * n.gap + 1
                        || seed.bottom - seed.top + 1 > 1.7f * n.gap) continue;
                int group = 0;
                while (group < groups) {
                    page.p(4, 0);
                    if (Float.floatToIntBits(gaps[group]) == Float.floatToIntBits(n.gap)) break;
                    group++;
                }
                if (group == groups) {
                    gaps[group] = n.gap;
                    empty[group] = false;
                    kinds[group] = caretRawKind(seed, width, n.gap, page, false, height);
                    groups++;
                }
                if (!blocked[index]) empty[group] = true;
            }
            for (int group = 0; group < groups; group++) {
                page.p(8, 0);
                boolean originalMarcato =
                        evidence != null && kinds[group] == NoteArticulation.MARCATO;
                if (!empty[group] || kinds[group] != 0 && !originalMarcato) continue;
                var body = page.grow(seed.pixels[0], gaps[group]);
                if (body == null
                        || body.pixels().length < seed.count
                        || body.pixels().length == seed.count && !originalMarcato) continue;
                float x = (body.left() + body.right()) * .5f,
                        y = (body.top() + body.bottom()) * .5f;
                boolean far = false;
                for (Anchor rival : notes) {
                    page.p(8, 0);
                    float dy = (rival.y - y) / rival.gap;
                    if (Math.abs(x - rival.x) <= rival.gap * .7f && dy > 6 && dy <= 8) far = true;
                }
                if (far && evidence == null) continue; // Old whole-body refusal remains unchanged.
                if (originalMarcato && !far) continue;
                page.p(8L * body.pixels().length + 64, 128);
                int semantic = 0;
                for (int p : body.pixels()) {
                    byte label = labels[p];
                    if (label == OmrMeasurePostProcessor.NOTEHEAD
                            || label == OmrMeasurePostProcessor.STEM_OR_REST
                            || label == OmrMeasurePostProcessor.CLEF_OR_KEY
                            || label == OmrMeasurePostProcessor.STAFF) semantic++;
                }
                if (semantic > body.pixels().length * .25f) continue;
                Glyph glyph =
                        new Glyph(
                                body.left(),
                                body.top(),
                                body.right(),
                                body.bottom(),
                                body.pixels().length,
                                body.pixels());
                if (caretRawKind(glyph, width, gaps[group], page, true, height)
                        != NoteArticulation.MARCATO) continue;
                int farOwner = -1;
                if (far) {
                    if (!evidence.take(16L * notes.size() + 64, 0)) continue;
                    boolean valid = true;
                    int firstOwner = -1;
                    for (int i = 0; i < notes.size(); i++) {
                        var rival = notes.get(i);
                        float dy = (rival.y - y) / rival.gap;
                        if (Math.abs(x - rival.x) > rival.gap * .7f || dy <= 6 || dy > 8) continue;
                        if (!evidence.proves(
                                        i, rival, glyph.left, glyph.top, glyph.right, glyph.bottom)
                                || firstOwner >= 0 && !evidence.sameAttack(firstOwner, i))
                            valid = false;
                        if (firstOwner < 0) firstOwner = i;
                    }
                    if (!valid) continue;
                    farOwner = firstOwner;
                }
                page.p(
                        DetachedCaretCosts.sum(
                                64,
                                DetachedCaretCosts.mul(
                                        4, DetachedCaretCosts.mul(glyph.count, glyph.count))),
                        4L * glyph.count + 64);
                Arrays.sort(glyph.pixels);
                boolean duplicate = false;
                for (Glyph other : out) {
                    page.p(8L + 4L * glyph.count, 0);
                    if (other.left == glyph.left
                            && other.top == glyph.top
                            && other.right == glyph.right
                            && other.bottom == glyph.bottom
                            && Arrays.equals(other.pixels, glyph.pixels)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    page.p(64, 128);
                    out.add(glyph);
                    if (far) {
                        page.p(64, 128);
                        farBodies.put(glyph, farOwner);
                    }
                }
            }
        }
        page.p(64, 128);
        return new CaretPass(page, out, blocked, farBodies, context, noteCosts);
    }

    /** A contrast subset cannot turn ink from a complete printed three into an accent. */
    private static boolean detachedThreeOwns(
            Glyph glyph, List<Glyph> originals, int width, float gap) {
        if (TupletNumeralBeamBand.detachedThree(
                glyph.pixels, width, glyph.left, glyph.top, glyph.right, glyph.bottom, gap))
            return true;
        for (Glyph parent : originals) {
            int pw = parent.right - parent.left + 1, ph = parent.bottom - parent.top + 1;
            if (pw < gap * .35f
                    || pw > gap * 1.6f
                    || ph < gap * .7f
                    || ph > gap * 2.2f
                    || parent.left - gap * .5f > glyph.left
                    || parent.right < glyph.right
                    || parent.top - 1 > glyph.top
                    || parent.bottom + 1 < glyph.bottom) continue;
            // Engraved threes may print their two left terminals as separate islands.
            // Only small islands inside the body's vertical extent may join that body.
            List<Glyph> parts = new ArrayList<>();
            parts.add(parent);
            int left = parent.left, top = parent.top, bottom = parent.bottom, count = parent.count;
            for (Glyph part : originals) {
                if (part == parent
                        || part.left < parent.left - gap * .5f
                        || part.right > parent.right
                        || part.right > parent.left + pw * .55f
                        || part.top < parent.top - 1
                        || part.bottom > parent.bottom + 1
                        || part.right - part.left + 1 > gap * .6f
                        || part.bottom - part.top + 1 > ph * .5f) continue;
                parts.add(part);
                left = Math.min(left, part.left);
                top = Math.min(top, part.top);
                bottom = Math.max(bottom, part.bottom);
                count += part.count;
            }
            int[] owned = new int[count];
            int used = 0;
            for (Glyph part : parts) {
                System.arraycopy(part.pixels, 0, owned, used, part.count);
                used += part.count;
            }
            if (!TupletNumeralBeamBand.detachedThree(
                    owned, width, left, top, parent.right, bottom, gap)) continue;
            Arrays.sort(owned);
            boolean subset = true;
            for (int pixel : glyph.pixels)
                if (Arrays.binarySearch(owned, pixel) < 0) {
                    subset = false;
                    break;
                }
            if (subset) return true;
        }
        return false;
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
                // A flag can be sliced at both ends by two independent rules.
                // The broad face of a real wedge may overlap the second rule,
                // but it must end there: retain the same paper test on that side.
                int opposite = direction < 0 ? gy0 - 1 : gy1 + 1;
                if (opposite >= top && opposite <= bottom && rules[opposite - top]) {
                    while (opposite >= top && opposite <= bottom && rules[opposite - top])
                        opposite += direction;
                    boolean broadContinues = opposite < top || opposite > bottom;
                    if (!broadContinues)
                        for (int x = Math.max(0, gx0 - 1); x <= Math.min(width - 1, gx1 + 1); x++)
                            broadContinues |= (gray[opposite * width + x] & 255) < 155;
                    if (broadContinues) continue;
                }
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
                    && RoundedAngularArticulation.matchesRuleJoinedOpen(
                            glyph.pixels,
                            width,
                            glyph.left,
                            glyph.top,
                            glyph.right,
                            glyph.bottom,
                            rules,
                            top)
                    && !ruledCaretCrossbar(glyph, width, gray)
                    && (Math.abs(glyph.y() - note.y) <= gap * 3f
                            || beamOwnedMarcato(glyph, note, gray, width, height, true)
                            || terminatedStemMarcato(glyph, note, gray, width, height)))
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
        // These are necessary, target-only conditions. The original per-run
        // conditions below still decide each period and dash, in original order.
        List<Glyph> periods = new ArrayList<>(), possibleDashes = new ArrayList<>();
        for (Glyph g : glyphs) {
            float h = g.bottom - g.top + 1, w = g.right - g.left + 1;
            if (g.right < target.left && w <= gap * .4f && h <= gap * .4f) periods.add(g);
            if (g.left <= target.right + gap * 12
                    && Math.abs(g.y() - target.y()) <= gap * .15f
                    && h <= gap * .25f
                    && w >= gap * .55f
                    && w <= gap * 1.8f) possibleDashes.add(g);
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
                for (Glyph period : periods) {
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
            for (Glyph g : possibleDashes)
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

    /** Optional local acquisition; existing candidates and all later role guards retain priority. */
    private static List<Glyph> closedDotFringes(
            List<Glyph> originals,
            java.util.Map<Glyph, Integer> joined,
            List<Anchor> notes,
            byte[] gray,
            int width,
            int height) {
        return closedDotFringes(originals, joined, notes, gray, width, height, 262144, 1000000);
    }

    private static List<Glyph> closedDotFringes(
            List<Glyph> originals,
            java.util.Map<Glyph, Integer> joined,
            List<Anchor> notes,
            byte[] gray,
            int width,
            int height,
            long cellsLeft,
            long pairsLeft) {
        List<Glyph> added = new ArrayList<>();
        // One invocation funds at most 262144 local cells (including histogram setup)
        // and 1000000 owner comparisons. Every ROI allocation/scan is prepaid.
        float maximumGap = 0;
        for (Anchor n : notes)
            if (Float.isFinite(n.gap) && n.gap > 0 && n.gap <= 64)
                maximumGap = Math.max(maximumGap, n.gap);
        for (Glyph core : originals) {
            int cw = core.right - core.left + 1, ch = core.bottom - core.top + 1;
            if (joined.containsKey(core) || core.count < 3 || Math.max(cw, ch) > maximumGap * .62f)
                continue;
            if (pairsLeft < notes.size()) return List.of();
            pairsLeft -= notes.size();
            Anchor owner = null;
            for (Anchor n : notes) {
                if (!Float.isFinite(n.gap) || n.gap <= 0 || n.gap > 64) continue;
                float dx = Math.abs(core.x() - n.x) / n.gap, dy = Math.abs(core.y() - n.y) / n.gap;
                if (dx <= .7f && dy >= .7f && dy <= 3f) {
                    if (owner != null) {
                        owner = null;
                        break;
                    }
                    owner = n;
                }
            }
            if (owner == null || Math.max(cw, ch) > owner.gap * .62f) continue;
            float gap = owner.gap;
            int pad = (int) Math.ceil(gap);
            int l = core.left - pad,
                    r = core.right + pad,
                    t = core.top - pad,
                    b = core.bottom + pad;
            if (l <= 0 || t <= 0 || r >= width - 1 || b >= height - 1) continue;
            int w = r - l + 1, h = b - t + 1, area = w * h;
            if (cellsLeft < area + 256L) return List.of();
            cellsLeft -= area + 256L;
            // A local cell is read at most for histogram, flood, row/column proof and
            // radial proof; each proof has <=8 neighbor visits. Peak scratch <=32*area
            // bytes plus histogram, and retained literal pixels <=4*funded cells.
            if (classify(core, width, gap, core.y() < owner.y) != 0
                    || roundedAngular(core, width, gap, core.y() < owner.y) != 0
                    || roundedTenuto(core, width, gap)
                    || rasterCoreMark(core, width, gap) != 0) continue;
            Glyph existingCore = printedCore(core, gray, width, height);
            if (existingCore != null
                    && (classify(existingCore, width, gap, core.y() < owner.y) != 0
                            || roundedAngular(existingCore, width, gap, core.y() < owner.y) != 0
                            || rasterCoreMark(existingCore, width, gap) != 0)) continue;
            int[] histogram = new int[256], queue = new int[area];
            byte[] state = new byte[area];
            for (int y = t; y <= b; y++)
                for (int x = l; x <= r; x++) histogram[gray[y * width + x] & 255]++;
            int target = (area * 3 + 3) / 4, seen = 0, paper = 0;
            while (paper < 255 && (seen += histogram[paper]) < target) paper++;
            int boundary = paper - Math.max(8, Math.round(paper * .05f));
            int minimum = 255, maximum = 0;
            for (int p : core.pixels) {
                int q = (p / width - t) * w + p % width - l;
                state[q] = 1;
                minimum = Math.min(minimum, gray[p] & 255);
                maximum = Math.max(maximum, gray[p] & 255);
            }
            if (minimum > paper * .5f || maximum >= boundary) continue;
            int size = 1, at = 0;
            queue[0] = (core.pixels[0] / width - t) * w + core.pixels[0] % width - l;
            state[queue[0]] |= 2;
            boolean refused = false;
            int left = w, right = 0, top = h, bottom = 0;
            while (at < size && !refused) {
                int q = queue[at++], x = q % w, y = q / w, p = (y + t) * width + x + l;
                if (x == 0
                        || y == 0
                        || x == w - 1
                        || y == h - 1
                        || (gray[p] & 255) < 155 && (state[q] & 1) == 0) {
                    refused = true;
                    break;
                }
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int next = ny * w + nx;
                        if ((state[next] & 2) == 0
                                && (gray[(ny + t) * width + nx + l] & 255) < boundary) {
                            state[next] |= 2;
                            queue[size++] = next;
                        }
                    }
            }
            if (refused) continue;
            int bw = right - left + 1, bh = bottom - top + 1;
            if (bw < gap * .26f
                    || bh < gap * .26f
                    || bw > gap * .62f
                    || bh > gap * .62f
                    || size + 1 < gap * gap * .065f
                    || size < bw * bh * .75f
                    || bw / (float) bh <= .65f
                    || bw / (float) bh >= 1.55f) continue;
            for (int y = top; y <= bottom && !refused; y++) {
                int first = -1, last = -1, count = 0;
                for (int x = left; x <= right; x++)
                    if ((state[y * w + x] & 2) != 0) {
                        if (first < 0) first = x;
                        last = x;
                        count++;
                    }
                if (count > 0 && last - first + 1 != count) refused = true;
            }
            for (int x = left; x <= right && !refused; x++) {
                int first = -1, last = -1, count = 0;
                for (int y = top; y <= bottom; y++)
                    if ((state[y * w + x] & 2) != 0) {
                        if (first < 0) first = y;
                        last = y;
                        count++;
                    }
                if (count > 0 && last - first + 1 != count) refused = true;
            }
            if (refused) continue;
            float cx = core.x() - l, cy = core.y() - t;
            at = 0;
            int reached = 0;
            for (int p : core.pixels) {
                int q = (p / width - t) * w + p % width - l;
                state[q] |= 4;
                queue[reached++] = q;
            }
            while (at < reached) {
                int q = queue[at++], x = q % w, y = q / w;
                float radius = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                int tone = gray[(y + t) * width + x + l] & 255;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int next = ny * w + nx;
                        float nextRadius = (nx - cx) * (nx - cx) + (ny - cy) * (ny - cy);
                        if ((state[next] & 2) != 0
                                && (state[next] & 4) == 0
                                && nextRadius >= radius
                                && (gray[(ny + t) * width + nx + l] & 255) >= tone) {
                            state[next] |= 4;
                            queue[reached++] = next;
                        }
                    }
            }
            if (reached != size) continue;
            int[] pixels = new int[size];
            for (int i = 0; i < size; i++)
                pixels[i] = (queue[i] / w + t) * width + queue[i] % w + l;
            Glyph body = new Glyph(left + l, top + t, right + l, bottom + t, size, pixels);
            if (rasterCoreMark(body, width, gap) != NoteArticulation.STACCATO) continue;
            // The recovered centroid must retain the same unique geometric owner.
            if (pairsLeft < notes.size()) return List.of();
            pairsLeft -= notes.size();
            Anchor bodyOwner = null;
            for (Anchor n : notes) {
                if (!Float.isFinite(n.gap) || n.gap <= 0) continue;
                float dx = Math.abs(body.x() - n.x) / n.gap, dy = Math.abs(body.y() - n.y) / n.gap;
                if (dx <= .7f && dy >= .7f && dy <= 3f) {
                    if (bodyOwner != null) {
                        bodyOwner = null;
                        break;
                    }
                    bodyOwner = n;
                }
            }
            if (bodyOwner == owner) added.add(body);
        }
        return added;
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

    /** Joint attribution only after both complete raw members pass ALL original
     * per-note gates. No new glyph classification, contrast or frame preference.
     */
    private static final class CompoundClaims {
        private long work = 2_000_000, bytes = 8_000_000;
        private boolean disabled;
        private final int size;
        private final int[] accents, tenutos;
        private final java.util.IdentityHashMap<Glyph, java.util.BitSet[]> eligible =
                new java.util.IdentityHashMap<>();
        private final List<CompoundClaim> claims = new ArrayList<>();
        // Per detect invocation, exact Glyph identity and one unchanged raw plane.
        private Glyph[] contrastGlyphs = new Glyph[0];
        private byte[] contrastValues = new byte[0];
        private int contrastCount, contrastWidth, contrastHeight;
        private byte[] contrastPlane;
        private final java.util.IdentityHashMap<Glyph, Boolean> unresolved =
                new java.util.IdentityHashMap<>();

        private record CompoundClaim(Glyph glyph, int owner, int mark) {}

        private record Proposal(CompoundClaim accent, CompoundClaim tenuto, int owner) {}

        CompoundClaims(int n) {
            size = n;
            if (!take(2L * n, 8L * n + 256)) {
                accents = tenutos = null;
                return;
            }
            accents = new int[n];
            tenutos = new int[n];
        }

        private boolean take(long w, long b) {
            if (disabled || w < 0 || b < 0 || w > work || b > bytes) {
                disabled = true;
                return false;
            }
            work -= w;
            bytes -= b;
            return true;
        }

        void eligible(Glyph g, int n, int mark, Anchor note, byte[] gray, int width, int height) {
            if (disabled || (mark != NoteArticulation.ACCENT && mark != NoteArticulation.TENUTO))
                return;
            if (!compoundContrast(g, gray, width, height)) return;
            // Trust only the positive mark already returned by the existing
            // caller after ALL its gates; do not replace its faint/rounded
            // classifier with a different one. Add raw contrast and whole-CC
            // reproof before any joint attribution.
            // compoundContrast is exactly printedBodyContrast's 8-bit order
            // statistics, cached only for this existing Glyph/raw invocation.
            var bits = eligible.get(g);
            if (bits == null) {
                if (!take(1, 16L * ((size + 63) / 64) + 160)) return;
                bits =
                        new java.util.BitSet[] {
                            new java.util.BitSet(size), new java.util.BitSet(size)
                        };
                eligible.put(g, bits);
            }
            bits[mark == NoteArticulation.ACCENT ? 0 : 1].set(n);
        }

        /** Same paper75th/minimum/upper-median and final inequalities as the
         * unchanged printedBodyContrast; histogram order statistics avoid sort.
         * The caller never writes gray, and this private cache cannot escape
         * one detect invocation. Different raw identity/dimensions abstain.
         */
        private boolean compoundContrast(Glyph g, byte[] gray, int width, int height) {
            if (contrastPlane != null
                    && (contrastPlane != gray
                            || contrastWidth != width
                            || contrastHeight != height)) {
                disabled = true;
                return false;
            }
            if (!take(contrastCount + 1L, 0)) return false;
            for (int i = 0; i < contrastCount; i++)
                if (contrastGlyphs[i] == g) return contrastValues[i] == 1;
            int padding = Math.max(3, Math.max(g.right - g.left + 1, g.bottom - g.top + 1) / 2);
            int x0 = Math.max(0, g.left - padding), x1 = Math.min(width - 1, g.right + padding);
            int y0 = Math.max(0, g.top - padding), y1 = Math.min(height - 1, g.bottom + padding);
            long scope = (long) (x1 - x0 + 1) * (y1 - y0 + 1);
            // Four debits per visited window/body pixel plus fixed histogram
            // initialization/scans. Two 256-bin int arrays and headers prepaid.
            if (!take(4 * scope + 4L * g.count + 2048, 2112)) return false;
            if (contrastCount == contrastGlyphs.length) {
                long capacity = Math.max((long) contrastCount + 1, 2L * contrastGlyphs.length + 8);
                if (capacity > Integer.MAX_VALUE
                        || !take(2 * capacity + 2L * contrastCount, 9 * capacity + 48)) {
                    disabled = true;
                    return false;
                }
                contrastGlyphs = java.util.Arrays.copyOf(contrastGlyphs, (int) capacity);
                contrastValues = java.util.Arrays.copyOf(contrastValues, (int) capacity);
            }
            int[] background = new int[256], tones = new int[256];
            int samples = 0;
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++) {
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
            for (int i = 0; i < g.count; i++) tones[gray[g.pixels[i]] & 255]++;
            int minimum = 0;
            while (tones[minimum] == 0) minimum++;
            int median = 255;
            cumulative = 0;
            for (int tone = 0; tone < 256; tone++) {
                cumulative += tones[tone];
                if (cumulative > g.count / 2) {
                    median = tone;
                    break;
                }
            }
            boolean proved =
                    minimum <= paper * .5f
                            || (median - minimum <= Math.max(8, Math.round(paper * .05f))
                                    && paper - median >= 25);
            contrastPlane = gray;
            contrastWidth = width;
            contrastHeight = height;
            contrastGlyphs[contrastCount] = g;
            contrastValues[contrastCount++] = (byte) (proved ? 1 : 2);
            return proved;
        }

        void claim(Glyph g, int owner, int mark) {
            if (disabled || (mark != NoteArticulation.ACCENT && mark != NoteArticulation.TENUTO))
                return;
            if (take(1, 96)) claims.add(new CompoundClaim(g, owner, mark));
        }

        void assignment(int n, int mark) {
            if (!take(1, 0)) return;
            if ((mark & NoteArticulation.ACCENT) != 0) accents[n]++;
            if ((mark & NoteArticulation.TENUTO) != 0) tenutos[n]++;
        }

        void resolve(int[] result, List<Anchor> notes, byte[] gray, int width, int height) {
            if (disabled) return;
            var proposals = new ArrayList<Proposal>();
            var degree = new java.util.IdentityHashMap<Glyph, Integer>();
            for (var a : claims) {
                if (!take(1, 0)) return;
                if (a.mark != NoteArticulation.ACCENT) continue;
                for (var t : claims) {
                    if (!take(1, 0)) return;
                    if (t.mark != NoteArticulation.TENUTO) continue;
                    Proposal p = proposal(a, t, notes, gray, width, height);
                    if (disabled) return;
                    if (p != null) {
                        if (!take(2, 192)) return;
                        proposals.add(p);
                        degree.merge(a.glyph, 1, Integer::sum);
                        degree.merge(t.glyph, 1, Integer::sum);
                    }
                }
            }
            if (!take(2L * size, 8L * size + 64)) return;
            int[] ac = accents.clone(), tc = tenutos.clone();
            for (var p : proposals) {
                if (!take(1, 0)) return;
                if (unresolved.containsKey(p.accent.glyph)
                        || unresolved.containsKey(p.tenuto.glyph)
                        || degree.get(p.accent.glyph) != 1
                        || degree.get(p.tenuto.glyph) != 1) continue;
                if (!take(4L * size, 0)) return;
                adjust(ac, notes, p.accent.owner, -1);
                adjust(tc, notes, p.tenuto.owner, -1);
                adjust(ac, notes, p.owner, 1);
                adjust(tc, notes, p.owner, 1);
            }
            if (!take(size, 0)) return;
            // Commit only after all work succeeds; exhausted/ambiguous metadata
            // never changes the preexisting recognition result.
            for (int n = 0; n < size; n++)
                result[n] =
                        (result[n] & ~(NoteArticulation.ACCENT | NoteArticulation.TENUTO))
                                | (ac[n] > 0 ? NoteArticulation.ACCENT : 0)
                                | (tc[n] > 0 ? NoteArticulation.TENUTO : 0);
        }

        private Proposal unresolved(CompoundClaim a, CompoundClaim t) {
            if (take(2, 128)) {
                unresolved.put(a.glyph, Boolean.TRUE);
                unresolved.put(t.glyph, Boolean.TRUE);
            }
            return null;
        }

        /** Equality of the exact existing chord-assignment sets, not proximity
         * or a selected representative. The caller prepays both full scans.
         */
        private boolean sameAssignment(List<Anchor> notes, int first, int second) {
            if (first == second) return true;
            Anchor a = notes.get(first), b = notes.get(second);
            if (a.staff != b.staff) return false;
            for (Anchor n : notes) {
                boolean before = n.staff == a.staff && Math.abs(n.x - a.x) < a.gap * .45f;
                boolean after = n.staff == b.staff && Math.abs(n.x - b.x) < b.gap * .45f;
                if (before != after) return false;
            }
            return true;
        }

        private void adjust(int[] counts, List<Anchor> notes, int owner, int delta) {
            Anchor a = notes.get(owner);
            for (int n = 0; n < size; n++)
                if (notes.get(n).staff == a.staff && Math.abs(notes.get(n).x - a.x) < a.gap * .45f)
                    counts[n] += delta;
        }

        private Proposal proposal(
                CompoundClaim a,
                CompoundClaim t,
                List<Anchor> notes,
                byte[] gray,
                int width,
                int height) {
            Glyph ga = a.glyph, gt = t.glyph;
            if (ga == gt || Math.abs(ga.left - gt.left) > 1 || Math.abs(ga.right - gt.right) > 1)
                return null;
            int direction = gt.y() > ga.y() ? 1 : -1;
            if (direction > 0 ? ga.bottom >= gt.top : gt.bottom >= ga.top) return null;
            var aa = eligible.get(ga);
            var tt = eligible.get(gt);
            if (aa == null || tt == null) return null;
            if (!take(size, 0)) return null;
            int owner = -1;
            for (int n = aa[0].nextSetBit(0); n >= 0; n = aa[0].nextSetBit(n + 1))
                if (tt[1].get(n)) {
                    Anchor note = notes.get(n);
                    if (direction > 0 ? note.y <= gt.bottom : note.y >= gt.top) continue;
                    float separation = Math.abs(gt.y() - ga.y());
                    // Existing pairedTenuto proposal constants, unchanged.
                    if (Math.abs(gt.x() - ga.x()) > note.gap * .2f
                            || separation < note.gap * .35f
                            || separation > note.gap * 1.2f) continue;
                    if (owner >= 0
                            && (note.staff != notes.get(owner).staff
                                    || Math.abs(note.x - notes.get(owner).x) >= note.gap * .45f))
                        return unresolved(a, t);
                    owner = n;
                }
            if (owner < 0) return null;
            if (!take(4L * size + 2, 0)) return null;
            if (sameAssignment(notes, a.owner, owner) && sameAssignment(notes, t.owner, owner)) {
                // No new attribution is needed. Do NOT claim this unproved
                // potential pair false or remove a degree edge optimistically:
                // both members become unresolved and cannot be borrowed by
                // any competing new assignment. Their legacy bits stay intact.
                return unresolved(a, t);
            }
            int l = Math.min(ga.left, gt.left),
                    r = Math.max(ga.right, gt.right),
                    top = Math.min(ga.top, gt.top),
                    bottom = Math.max(ga.bottom, gt.bottom);
            if (l < 0 || r >= width || top < 0 || bottom >= height) return unresolved(a, t);
            int pad = (int) Math.ceil(notes.get(owner).gap);
            int sl = Math.max(0, l - pad),
                    sr = Math.min(width - 1, r + pad),
                    st = Math.max(0, top - pad),
                    sb = Math.min(height - 1, bottom + pad);
            long scope = (long) (sr - sl + 1) * (sb - st + 1);
            if (!take(80 * scope + 8L * (ga.count + gt.count), 32 * scope + 2048)) return null;
            // This is the EXISTING raw185 faint-chevron connectivity domain,
            // not a change to raw155 ink(), classifier limits or paper tone.
            // Recollect COMPLETE parents; boundary contact always abstains.
            Glyph pa = completeRaw185(ga, gray, width, height, sl, st, sr, sb);
            Glyph pt = completeRaw185(gt, gray, width, height, sl, st, sr, sb);
            // Incomplete parent observation is UNKNOWN for every attribution
            // involving either member; another partner cannot certify it false.
            if (pa == null || pt == null) return unresolved(a, t);
            if (!OpenChevron.matches(
                            pa.pixels, width, pa.left, pa.top, pa.right, pa.bottom, pa.count)
                    || classify(pt, width, notes.get(owner).gap, pt.y() < notes.get(owner).y)
                            != NoteArticulation.TENUTO) return null;
            if (Math.abs(pa.left - pt.left) > 1 || Math.abs(pa.right - pt.right) > 1) return null;
            l = Math.min(pa.left, pt.left);
            r = Math.max(pa.right, pt.right);
            top = Math.min(pa.top, pt.top);
            bottom = Math.max(pa.bottom, pt.bottom);
            long area = (long) (r - l + 1) * (bottom - top + 1);
            java.util.BitSet members = new java.util.BitSet((int) area);
            for (Glyph g : List.of(pa, pt))
                for (int at : g.pixels) {
                    int x = at % width, y = at / width;
                    if (x < l || x > r || y < top || y > bottom || (gray[at] & 255) >= 185)
                        return null;
                    int local = (y - top) * (r - l + 1) + x - l;
                    if (members.get(local)) return null;
                    members.set(local);
                }
            // Every raw pixel in the complete two-part rectangle must belong to
            // these exact components. No unknown flag/beam/text ink is cropped.
            for (int y = top; y <= bottom; y++)
                for (int x = l; x <= r; x++)
                    if ((gray[y * width + x] & 255) < 185
                            && !members.get((y - top) * (r - l + 1) + x - l)) return null;
            return new Proposal(a, t, owner);
        }

        private Glyph completeRaw185(
                Glyph original, byte[] gray, int width, int height, int l, int t, int r, int b) {
            int sw = r - l + 1, area = sw * (b - t + 1);
            boolean[] seen = new boolean[area];
            int[] queue = new int[area];
            int seed = original.pixels[0];
            if ((gray[seed] & 255) >= 185) return null;
            int count = 1, read = 0;
            queue[0] = seed;
            seen[(seed / width - t) * sw + seed % width - l] = true;
            int left = width, right = -1, top = height, bottom = -1;
            while (read < count) {
                int at = queue[read++], x = at % width, y = at / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0
                                || xx >= width
                                || yy < 0
                                || yy >= height
                                || (gray[yy * width + xx] & 255) >= 185) continue;
                        if (xx < l || xx > r || yy < t || yy > b) return null;
                        int local = (yy - t) * sw + xx - l;
                        if (!seen[local]) {
                            seen[local] = true;
                            queue[count++] = yy * width + xx;
                        }
                    }
            }
            for (int at : original.pixels) {
                int x = at % width, y = at / width;
                if (x < l || x > r || y < t || y > b || !seen[(y - t) * sw + x - l]) return null;
            }
            return new Glyph(
                    left, top, right, bottom, count, java.util.Arrays.copyOf(queue, count));
        }
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

    /** A positively proved short crossing owns its complete observed thin vertical envelope. */
    private static boolean finiteShaftCrossing(
            byte[] gray,
            int width,
            int height,
            Anchor note,
            Glyph mark,
            int shaftX,
            int row,
            int start,
            int maximumThickness) {
        int limit = Math.max(1, Math.round(note.gap * 1.5f));
        int minX = Math.round(note.x) - limit, maxX = Math.round(note.x) + limit;
        int left = shaftX, right = shaftX;
        while (left > 0 && left >= minX && (gray[row * width + left - 1] & 255) < 225) left--;
        while (right + 1 < width && right <= maxX && (gray[row * width + right + 1] & 255) < 225)
            right++;
        if (left < 1 || right >= width - 1 || left < minX || right > maxX) return false;
        for (int centre = Math.max(maximumThickness, row - maximumThickness);
                centre <= Math.min(height - 1 - maximumThickness, row + maximumThickness);
                centre++) {
            if ((gray[centre * width + Math.round(note.x)] & 255) >= 155
                    || !shortLedgerStroke(
                            gray,
                            width,
                            height,
                            note.x,
                            centre,
                            note.gap,
                            mark.bottom + 1,
                            start,
                            note.gap)) continue;
            int shafts = 0;
            boolean complete = true;
            for (int xx = left; xx <= right && complete; xx++) {
                for (int yy = Math.min(row, centre); yy <= Math.max(row, centre); yy++)
                    if ((gray[yy * width + xx] & 255) >= 225) {
                        complete = false;
                        break;
                    }
                int top = row, bottom = row;
                while (top > 0
                        && row - top <= maximumThickness
                        && (gray[(top - 1) * width + xx] & 255) < 225) top--;
                while (bottom + 1 < height
                        && bottom - row <= maximumThickness
                        && (gray[(bottom + 1) * width + xx] & 255) < 225) bottom++;
                if (bottom - top + 1 > maximumThickness) {
                    if (Math.abs(xx - shaftX) >= maximumThickness || ++shafts > maximumThickness)
                        complete = false;
                } else if (top == 0 || bottom == height - 1) complete = false;
            }
            if (complete) return true;
        }
        return false;
    }

    /** An unbeamed quarter can own a distant ruled caret through its complete thin shaft. */
    private static boolean terminatedStemMarcato(
            Glyph mark, Anchor note, byte[] gray, int width, int height) {
        float gap = note.gap;
        if (mark.bottom >= note.y
                || note.y - mark.y() > gap * 6f
                || PrintedStemDirection.detect(gray, width, height, note.x, note.y, gap) != 1)
            return false;
        int headRow = Math.round(note.y), headX = Math.round(note.x);
        int start = Math.round(note.y - gap * .3f);
        int maxWidth = Math.max(1, (int) Math.ceil(gap * .25f));
        for (int offset = Math.round(gap * .3f); offset <= Math.round(gap * .85f); offset++) {
            int x = headX + offset;
            if (x < 1 || x >= width - 1 || start < 0 || headRow >= height) continue;
            boolean connected = true;
            for (int xx = headX; xx <= x; xx++)
                if ((gray[headRow * width + xx] & 255) >= 155) connected = false;
            if (!connected) continue;
            int y = start;
            boolean thin = true;
            while (y > mark.bottom && (gray[y * width + x] & 255) < 155) {
                if (y < note.y - gap * .7f
                        && !thinIndependentRule(gray, width, height, x, y, gap)
                        && !finiteShaftCrossing(
                                gray, width, height, note, mark, x, y, start, maxWidth)) {
                    int left = x, right = x;
                    while (left > 0
                            && x - left <= maxWidth
                            && (gray[y * width + left - 1] & 255) < 155) left--;
                    while (right + 1 < width
                            && right - x <= maxWidth
                            && (gray[y * width + right + 1] & 255) < 155) right++;
                    if (right - left + 1 > maxWidth) {
                        thin = false;
                        break;
                    }
                }
                y--;
            }
            int end = y + 1;
            if (!thin
                    || y < 2
                    || y <= mark.bottom
                    || start - end + 1 < gap * 2.5f
                    || end - mark.bottom > gap * 2.5f) continue;
            boolean cap = true;
            for (int yy = y - 2; yy <= y; yy++)
                for (int xx = x - 1; xx <= x + 1; xx++)
                    if ((gray[yy * width + xx] & 255) < 225) cap = false;
            if (cap) return true;
        }
        return false;
    }

    /** A crossbar closes an already observed open interior; a thick peak does not. */
    private static boolean ruledCaretCrossbar(Glyph mark, int width, byte[] gray) {
        int h = mark.bottom - mark.top + 1, w = mark.right - mark.left + 1;
        boolean opened = false;
        int bridges = 0;
        for (int y = mark.top + (int) Math.ceil(h * .2f);
                y <= mark.top + (int) Math.floor(h * .8f);
                y++) {
            int left = Integer.MAX_VALUE, right = -1;
            for (int p : mark.pixels)
                if (p / width == y) {
                    left = Math.min(left, p % width);
                    right = Math.max(right, p % width);
                }
            // Independently removed rule rows supply no glyph evidence.
            if (right < left) continue;
            boolean paper = false, bridge = true;
            for (int x = left; x <= right; x++) {
                int ink = gray[y * width + x] & 255;
                if (x > left && x < right && ink >= 225) paper = true;
                if (ink >= 225) bridge = false;
            }
            if (paper) {
                opened = true;
                bridges = 0;
            } else if (opened && bridge && right - left + 1 >= w * .45f) {
                if (++bridges >= 2) return true;
            } else bridges = 0;
        }
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

    /** Original beyond-ink/independent-rule law, with optional work paid before use. */
    static boolean ownedCaretBeyond(
            byte[] gray,
            int width,
            int height,
            Anchor note,
            int left,
            int right,
            int endpoint,
            int thickness,
            int separation,
            PrintedBeamSource.CaretEvidence evidence) {
        int look = Math.min(Math.max(3, Math.round(note.gap * .6f)), separation - 1);
        if (look < 3) return false;
        for (int x = left; x <= right; x++) {
            if (!evidence.take(8L * look + 32, 0)) return false;
            int beyond = 0;
            for (int d = 1; d < 1 + look; d++) {
                int y = endpoint - d;
                if (y < 0 || y >= height) {
                    beyond = look;
                    break;
                }
                if ((gray[y * width + x] & 255) < 155) {
                    // Covers horizontalRuleInk, both sides and all parallel-rule probes.
                    long g = (long) Math.ceil(note.gap);
                    if (!evidence.take(1024L * g * g + 8192, 512)) return false;
                    if (!thinIndependentRule(gray, width, height, x, y, note.gap)) beyond++;
                }
            }
            if (beyond <= look * .25f) return true;
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
        return shelteredDot(
                glyph, labels, gray, width, height, gap, distance, independentlyOwned, false);
    }

    private static boolean shelteredDot(
            Glyph glyph,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            float gap,
            float distance,
            boolean independentlyOwned,
            boolean certifiedClosedFringe) {
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
                // Only the new tail has a closed-fringe certificate proving that every
                // raw<155 pixel belongs to its actual original complete core. Gray
                // antialiasing supplies shape, not the core's independent darkness.
                int medianIndex = tone.length / 2;
                if (certifiedClosedFringe) {
                    int coreSize = 0;
                    while (coreSize < tone.length && tone[coreSize] < 155) coreSize++;
                    if (coreSize >= 3) medianIndex = coreSize / 2;
                }
                // Near independently dark dots retain the existing <=130 law;
                // distant ownership and actual curved roofs still veto below/above.
                if (tone[medianIndex] > 130
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
                    && !angularFermataRoof(
                            new Glyph(minX, minY, maxX, maxY, count, Arrays.copyOf(queue, count)),
                            w,
                            gap,
                            direction < 0)
                    && curvedRoof(queue, count, w, minX, maxX, gap, direction)) return true;
        }
        return false;
    }

    /** A rounded roof widens near its crown; a narrow two-arm caret does not. */
    private static boolean angularFermataRoof(Glyph g, int width, float gap, boolean above) {
        if (classify(g, width, gap, above) != NoteArticulation.MARCATO) return false;
        int h = g.bottom - g.top + 1, w = g.right - g.left + 1;
        int[] left = new int[h], right = new int[h];
        Arrays.fill(left, Integer.MAX_VALUE);
        Arrays.fill(right, -1);
        for (int p : g.pixels) {
            int y = p / width - g.top, x = p % width;
            left[y] = Math.min(left[y], x);
            right[y] = Math.max(right[y], x);
        }
        int rows = 0;
        double span = 0;
        for (int y = 0; y < h; y++) {
            float depth = (above ? y : h - 1 - y) / (float) Math.max(1, h - 1);
            if (depth >= .4f && depth <= .6f && right[y] >= left[y]) {
                span += right[y] - left[y] + 1;
                rows++;
            }
        }
        return rows >= 2 && span / rows <= w * .62f;
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

    /** Exact emitted glyph and every required ledger in a complete raw five-rule
     * raster frame. Unsupported/faint/partial marks and rival owners keep TENUTO. */
    private static boolean completeRasterLedger(
            Glyph glyph, Anchor note, List<Anchor> notes, byte[] gray, int width, int height) {
        for (Anchor other : notes) {
            if (other == note
                    || Math.abs(other.x - note.x) < .1f * note.gap
                            && Math.abs(other.y - note.y) < .1f * note.gap) continue;
            if (!Float.isFinite(other.x + other.y + other.gap) || other.gap < 6) return false;
            float distance = Math.abs(glyph.y() - other.y) / other.gap;
            if (Math.abs(glyph.x() - other.x) <= other.gap * .7f
                    && distance >= .7f
                    && distance <= 3.2f) return false;
        }
        var proof =
                RasterPhaseLedgerOwnership.prove(
                        gray, width, height, glyph.pixels, note.x, note.y, note.gap);
        return proof != null
                && proof.matches(gray, width, height, glyph.pixels, note.x, note.y, note.gap);
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

    /** A complete finite dash cannot borrow disconnected flank ink to become a staff rule.
     * The existing dark-core classifier and all earlier ownership guards have already passed.
     * Account for its entire component at the same threshold used by the rule veto; retain
     * independent parallel staff rules even when a dropout separates the candidate row.
     */
    private static boolean paperSeparatedTenuto(
            Glyph core, Anchor note, List<Anchor> notes, byte[] gray, int width, int height) {
        float gap = note.gap;
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || !Float.isFinite(gap)
                || !Float.isFinite(note.x)
                || !Float.isFinite(note.y)
                || gap < 6
                || gap > Math.min(width, height)
                || note.staff < 0
                || core.left < 1
                || core.top < 1
                || core.right >= width - 1
                || core.bottom >= height - 1
                || core.count < 1
                || core.pixels.length != core.count) return false;
        int maxWidth = (int) Math.floor(gap * 1.65f + 1),
                maxHeight = (int) Math.floor(gap * .4f + 1);
        if (core.right - core.left + 1 > maxWidth || core.bottom - core.top + 1 > maxHeight)
            return false;
        int left = (int) Math.max(0L, (long) core.left - maxWidth),
                right = (int) Math.min(width - 1L, (long) core.right + maxWidth),
                top = (int) Math.max(0L, (long) core.top - maxHeight),
                bottom = (int) Math.min(height - 1L, (long) core.bottom + maxHeight);
        int w = right - left + 1, h = bottom - top + 1;
        long cells = (long) w * h;
        if (cells < 1 || cells > Integer.MAX_VALUE) return false;
        int seed = core.pixels[0], sx = seed % width, sy = seed / width;
        if (sx < left || sx > right || sy < top || sy > bottom || (gray[seed] & 255) >= 235)
            return false;
        boolean[] seen = new boolean[(int) cells];
        int[] queue = new int[(int) cells];
        int read = 0, size = 1, minX = sx, maxX = sx, minY = sy, maxY = sy;
        queue[0] = (sy - top) * w + sx - left;
        seen[queue[0]] = true;
        while (read < size) {
            int at = queue[read++], x = left + at % w, y = top + at / w;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            if (maxX - minX + 1 > maxWidth || maxY - minY + 1 > maxHeight) return false;
            if (x == 0 || x == width - 1 || y == 0 || y == height - 1) return false;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if ((gray[yy * width + xx] & 255) >= 235) continue;
                    if (xx < left || xx > right || yy < top || yy > bottom) return false;
                    int next = (yy - top) * w + xx - left;
                    if (!seen[next]) {
                        seen[next] = true;
                        queue[size++] = next;
                    }
                }
        }
        // Every original core pixel and every foreground island in the full body is accounted.
        for (int pixel : core.pixels) {
            int x = pixel % width, y = pixel / width;
            if (x < left || x > right || y < top || y > bottom || !seen[(y - top) * w + x - left])
                return false;
        }
        for (int y = minY; y <= maxY; y++)
            for (int x = minX; x <= maxX; x++)
                if ((gray[y * width + x] & 255) < 235 && !seen[(y - top) * w + x - left])
                    return false;
        // Full paper on every side proves finite bounds rather than a clipped line.
        for (int x = minX - 1; x <= maxX + 1; x++)
            if ((gray[(minY - 1) * width + x] & 255) < 235
                    || (gray[(maxY + 1) * width + x] & 255) < 235) return false;
        for (int y = minY; y <= maxY; y++)
            if ((gray[y * width + minX - 1] & 255) < 235
                    || (gray[y * width + maxX + 1] & 255) < 235) return false;
        float cx = (minX + maxX) * .5f, cy = (minY + maxY) * .5f;
        if (Math.abs(cx - note.x) > gap * .7f
                || Math.abs(cy - note.y) < gap * .7f
                || Math.abs(cy - note.y) > gap * 3f) return false;
        // A bent foreign staff can defeat the old glyph-centered row grid. Its raw
        // independent rails and actual fifth-member support must be accounted first.
        if (FiniteTenutoStaffCounter.inspect(
                        gray, width, height, core.pixels, minX, minY, maxX, maxY, gap, notes)
                != FiniteTenutoStaffCounter.Decision.CLEAR) return false;
        // The current row already passed the original raw/contrasted rule veto. Four other
        // independent parallel raw rules preserve staff ownership despite a short paper dropout.
        for (int first = 0; first < 5; first++) {
            boolean complete = true;
            for (int line = 0; line < 5; line++) {
                if (line == first) continue;
                int y = Math.round(cy + (line - first) * gap);
                int x = Math.round(cx);
                boolean parallel =
                        completeStaffRail(gray, width, height, x, y, gap, -1, -1)
                                || (horizontalRuleInk(gray, width, height, x, y, gap, 235, .50f)
                                        && contrastedHorizontalRule(
                                                gray, width, height, x, y, gap));
                if (!parallel) {
                    complete = false;
                    break;
                }
            }
            if (complete
                    && !registeredCounterBorrowsFourRails(
                            gray, width, height, note, cx, minY, maxY, cy, gap, first))
                return false;
        }
        return true;
    }

    /** Reject a shifted counter only after its four supporting rows are independently
     * accounted for by the complete, raw-confirmed registered owner staff. Unknown staffs
     * and extrapolated frames retain the original conservative counter. */
    private static boolean registeredCounterBorrowsFourRails(
            byte[] gray,
            int width,
            int height,
            Anchor note,
            float cx,
            int bodyTop,
            int bodyBottom,
            float cy,
            float gap,
            int first) {
        StaffPitchTrack track = note.track;
        if (track == null || !track.verified()) return false;
        int near = Math.max(3, Math.round(gap * 1.5f)), far = Math.round(gap * 4f);
        int x = Math.round(cx), ownerX = Math.round(note.x);
        if (x - far < 0
                || x + far >= width
                || ownerX - far < 0
                || ownerX + far >= width
                || !track.verifiedAt(x - far)
                || !track.verifiedAt(x + far)
                || !track.verifiedAt(ownerX - far)
                || !track.verifiedAt(ownerX + far)) return false;
        float[] frame = track.at(cx);
        // Reuse the existing tracked-ledger registration contract, without extrapolation.
        if (!Float.isFinite(frame[0] + frame[1])
                || frame[1] < 8
                || Math.abs(frame[1] - gap) > gap * .2f) return false;
        if (bodyBottom >= frame[0] - 4 * frame[1] && bodyTop <= frame[0]) return false;
        // A genuinely long independent candidate-row rail remains a conservative veto,
        // including an unregistered foreign staff which shares other physical rules.
        if (horizontalRuleInk(gray, width, height, x, Math.round(cy), gap, 235, .85f)
                && contrastedHorizontalRule(gray, width, height, x, Math.round(cy), gap))
            return false;
        if (!registeredFiveRawRules(gray, width, height, track, x, gap)
                || !registeredFiveRawRules(gray, width, height, track, ownerX, gap)) return false;
        int reach = Math.max(1, Math.round(gap * .15f)) + 1;
        boolean[] used = new boolean[5];
        for (int line = 0; line < 5; line++) {
            if (line == first) continue;
            int row = Math.round(cy + (line - first) * gap), member = -1;
            for (int k = 0; k < 5; k++) {
                int registered = Math.round(frame[0] - (4 - k) * frame[1]);
                // This is exactly completeStaffRail's search radius plus its ink band.
                if (Math.abs(registered - row) <= reach) {
                    if (member >= 0) return false;
                    member = k;
                }
            }
            if (member < 0 || used[member]) return false;
            used[member] = true;
            for (int direction : new int[] {-1, 1}) {
                int owned = 0, independent = 0, samples = 0;
                for (int d = near; d <= far; d++) {
                    int xx = x + direction * d;
                    float[] f = track.at(xx);
                    if (!Float.isFinite(f[0] + f[1])
                            || f[1] < 8
                            || Math.abs(f[1] - gap) > gap * .2f) return false;
                    int seed = Math.round(f[0] - (4 - member) * f[1]);
                    if (row - reach < 0 || row + reach >= height || seed < 0 || seed >= height)
                        return false;
                    samples++;
                    boolean accounted = false, foreign = false;
                    for (int yy = row - reach; yy <= row + reach; yy++)
                        if ((gray[yy * width + xx] & 255) < 235
                                && sameVerticalInk(gray, width, xx, yy, seed)) accounted = true;
                    // Account for independent foreground across the SAME complete search
                    // and ink domain as attributed support. An opposite-edge foreign rule
                    // retains the old veto; the existing .50 bilateral budget is unchanged.
                    for (int yy = row - reach; yy <= row + reach; yy++) {
                        if ((gray[yy * width + xx] & 255) >= 235) continue;
                        boolean registered = false;
                        for (int k = 0; k < 5; k++) {
                            int sy = Math.round(f[0] - (4 - k) * f[1]);
                            if (sy >= 0
                                    && sy < height
                                    && sameVerticalInk(gray, width, xx, yy, sy)) {
                                registered = true;
                                break;
                            }
                        }
                        if (!registered) foreign = true;
                    }
                    if (accounted) owned++;
                    if (foreign) independent++;
                }
                if (samples == 0 || owned < samples * .50f || independent >= samples * .50f)
                    return false;
            }
        }
        return true;
    }

    /** Complete raw rails, sampled in the already observed physical frame at each column. */
    private static boolean registeredFiveRawRules(
            byte[] gray, int width, int height, StaffPitchTrack track, int x, float gap) {
        return FiniteTenutoStaffCounter.registeredFiveRawRules(gray, width, height, track, x, gap);
    }

    /** Positive continuous raw membership; no paper is removed or recolored. */
    private static boolean sameVerticalInk(byte[] gray, int width, int x, int first, int last) {
        for (int y = Math.min(first, last); y <= Math.max(first, last); y++)
            if ((gray[y * width + x] & 255) >= 235) return false;
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
                && RoundedAngularArticulation.matchesOpen(
                        g.pixels, width, g.left, g.top, g.right, g.bottom, 1)
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
