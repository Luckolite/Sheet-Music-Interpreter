// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Independent rounded-bulb and diagonal-shaft proof on the unchanged raster. */
final class RawBeamedTwoBulbRests {
    record Core(float x, float y) {}

    record Edge(int y, float x, int stroke) {}

    record Glyph(int left, int top, int right, int bottom, Core upper, Core lower) {}

    static final class Budget {
        long left;
        boolean refused;

        Budget(long amount) {
            left = amount;
        }

        boolean take(long amount) {
            if (amount < 0 || left < amount) {
                refused = true;
                return false;
            }
            left -= amount;
            return true;
        }
    }

    static SixteenthRestDetector.Detection add(
            SixteenthRestDetector.Detection current,
            byte[] g,
            int w,
            int h,
            List<MeasureRegion> bars,
            List<SixteenthRestDetector.Staff> staffs,
            List<ScoreNoteEvent> notes) {
        return add(current, g, w, h, bars, staffs, notes, 8_000_000);
    }

    static SixteenthRestDetector.Detection add(
            SixteenthRestDetector.Detection current,
            byte[] g,
            int w,
            int h,
            List<MeasureRegion> bars,
            List<SixteenthRestDetector.Staff> staffs,
            List<ScoreNoteEvent> notes,
            long allowance) {
        if (current == null
                || g == null
                || g.length != (long) w * h
                || w < 3
                || h < 3
                || w > 8192
                || h > 16384
                || bars == null
                || staffs == null
                || notes == null) return current;
        Budget budget = new Budget(allowance);
        var result = new ArrayList<>(current.rests());
        for (var before : notes) {
            if (before == null
                    || before.beamCount() < 1
                    || before.crossStaffBeam()
                    || (before.articulations() & NoteOrnament.GRACE) != 0
                    || before.measureIndex() < 0
                    || before.measureIndex() >= bars.size()) continue;
            ScoreNoteEvent after = null;
            for (var n : notes)
                if (n != null
                        && n.measureIndex() == before.measureIndex()
                        && n.staffIndex() == before.staffIndex()
                        && n.staffCount() == before.staffCount()
                        && (n.articulations() & NoteOrnament.GRACE) == 0
                        && n.positionInMeasure() > before.positionInMeasure() + .018f
                        && (after == null || n.positionInMeasure() < after.positionInMeasure()))
                    after = n;
            if (after == null || after.beamCount() < 1 || after.crossStaffBeam()) continue;
            final ScoreNoteEvent next = after;
            if (result.stream()
                    .anyMatch(
                            r ->
                                    r.measureIndex() == before.measureIndex()
                                            && r.staffIndex() == before.staffIndex()
                                            && r.staffCount() == before.staffCount()
                                            && r.durationBeats() == .25
                                            && r.positionInMeasure() > before.positionInMeasure()
                                            && r.positionInMeasure() < next.positionInMeasure()))
                continue;
            MeasureRegion bar = bars.get(before.measureIndex());
            float middle = (before.pageY() + after.pageY()) * h * .5f;
            SixteenthRestDetector.Staff frame = null;
            float distance = Float.MAX_VALUE;
            for (var s : staffs)
                if (s.pitchTrack() == null
                        && s.index() == before.staffIndex()
                        && s.count() == before.staffCount()
                        && (s.top() + s.bottom()) * .5f >= bar.top() * h
                        && (s.top() + s.bottom()) * .5f <= bar.bottom() * h) {
                    float d = Math.abs((s.top() + s.bottom()) * .5f - middle);
                    if (d < distance) {
                        distance = d;
                        frame = s;
                    }
                }
            if (frame == null) continue;
            float gap = frame.gap();
            if (!Float.isFinite(gap) || gap < 8 || gap > 64) continue;
            float span = (bar.right() - bar.left()) * w,
                    ax = bar.left() * w + before.positionInMeasure() * span,
                    bx = bar.left() * w + after.positionInMeasure() * span;
            if (bx - ax < gap * 3 || bx - ax > gap * 18) continue;
            int left = Math.max(0, Math.round(ax + gap * .65f)),
                    right = Math.min(w - 1, Math.round(bx - gap * .65f));
            int top =
                    Math.max(0, Math.round(Math.min(before.pageY(), after.pageY()) * h - gap * 3));
            int bottom =
                    Math.min(
                            h - 1,
                            Math.round(Math.max(before.pageY(), after.pageY()) * h + gap * 4));
            for (var body : find(g, w, h, left, top, right, bottom, gap, budget)) {
                float cx = (body.left + body.right) * .5f, cy = (body.top + body.bottom) * .5f;
                boolean occupied = false;
                for (var n : notes)
                    if (n != null
                            && n.measureIndex() == before.measureIndex()
                            && n.staffIndex() == before.staffIndex()
                            && n.staffCount() == before.staffCount()) {
                        float x = bar.left() * w + n.positionInMeasure() * span;
                        if (x >= body.left - gap * .25f
                                && x <= body.right + gap * .25f
                                && n.pageY() * h >= body.top - gap * .25f
                                && n.pageY() * h <= body.bottom + gap * .25f) {
                            occupied = true;
                            break;
                        }
                    }
                if (occupied || dot(g, w, h, body, gap, budget)) continue;
                var rest =
                        new ScoreRestEvent(
                                before.measureIndex(),
                                (cx - bar.left() * w) / span,
                                cy / h,
                                (body.bottom - body.top + 1f) / h,
                                before.staffIndex(),
                                before.staffCount(),
                                .25);
                if (!BeamedRestVoice.owns(rest, before, notes, bar, g, w, h, gap)) continue;
                int duplicate = -1;
                boolean blocked = false;
                for (int i = 0; i < result.size(); i++) {
                    var old = result.get(i);
                    if (old.measureIndex() != rest.measureIndex()
                            || old.staffIndex() != rest.staffIndex()
                            || old.staffCount() != rest.staffCount()) continue;
                    float ox = bar.left() * w + old.positionInMeasure() * span,
                            oy = old.pageY() * h,
                            oh = old.pageHeight() * h;
                    if (Math.abs(ox - cx) > gap * .55f
                            || Math.abs(oy - cy) > Math.max(oh, body.bottom - body.top + 1) * .5f)
                        continue;
                    if (old.durationBeats() == .25) {
                        blocked = true;
                        break;
                    }
                    if (old.kind() != ScoreRestEvent.Kind.LITERAL
                            || old.durationBeats() != 1
                            || body.upper.y < oy - oh * .5f
                            || body.lower.y > oy + oh * .5f) {
                        blocked = true;
                        break;
                    }
                    duplicate = i;
                }
                if (blocked) continue;
                if (duplicate < 0) result.add(rest);
                else result.set(duplicate, rest);
            }
            if (budget.refused) return current;
        }
        if (budget.refused || result.equals(current.rests())) return current;
        result.sort(
                Comparator.comparingInt(ScoreRestEvent::measureIndex)
                        .thenComparingDouble(ScoreRestEvent::positionInMeasure));
        return new SixteenthRestDetector.Detection(
                List.copyOf(result),
                current.dots().stream().filter(d -> result.contains(d.rest())).toList());
    }

    static List<Glyph> find(byte[] g, int w, int h, int l, int t, int r, int b, float gap) {
        return find(g, w, h, l, t, r, b, gap, new Budget(8_000_000));
    }

    private static List<Glyph> find(
            byte[] g, int w, int h, int l, int t, int r, int b, float gap, Budget budget) {
        if (g == null
                || g.length != (long) w * h
                || w < 3
                || h < 3
                || !Float.isFinite(gap)
                || gap < 8
                || gap > 64
                || l < 0
                || t < 0
                || r >= w
                || b >= h
                || r < l
                || b < t
                || r - l > gap * 18
                || b - t > gap * 16) return List.of();
        int radius = Math.max(2, Math.round(gap * .22f)), sw = r - l + 1, sh = b - t + 1;
        boolean[] core = new boolean[sw * sh];
        for (int y = t + radius; y <= b - radius; y++)
            for (int x = l + radius; x <= r - radius; x++) {
                if (!budget.take(1)) return List.of();
                if ((g[y * w + x] & 255) >= 170) continue;
                boolean filled = true;
                for (int dy = -radius; dy <= radius && filled; dy++)
                    for (int dx = -radius; dx <= radius; dx++)
                        if (dx * dx + dy * dy <= radius * radius) {
                            if (!budget.take(1)) return List.of();
                            if ((g[(y + dy) * w + x + dx] & 255) >= 170) {
                                filled = false;
                                break;
                            }
                        }
                core[(y - t) * sw + x - l] = filled;
            }
        var centers = new ArrayList<Core>();
        int[] queue = new int[core.length];
        for (int seed = 0; seed < core.length; seed++)
            if (core[seed]) {
                int count = 1, cursor = 0, minX = r, maxX = l, minY = b, maxY = t;
                long sx = 0, sy = 0;
                queue[0] = seed;
                core[seed] = false;
                while (cursor < count) {
                    int p = queue[cursor++], x = l + p % sw, y = t + p / sw;
                    sx += x;
                    sy += y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < l || xx > r || yy < t || yy > b) continue;
                            int at = (yy - t) * sw + xx - l;
                            if (core[at]) {
                                core[at] = false;
                                queue[count++] = at;
                            }
                        }
                }
                if (maxX - minX <= gap * .55f && maxY - minY <= gap * .55f)
                    centers.add(new Core(sx / (float) count, sy / (float) count));
                if (centers.size() > 64) {
                    budget.refused = true;
                    return List.of();
                }
            }
        centers.sort(Comparator.comparingDouble(Core::y));
        var result = new ArrayList<Glyph>();
        for (int i = 0; i < centers.size(); i++)
            for (int j = i + 1; j < centers.size(); j++) {
                Core a = centers.get(i), c = centers.get(j);
                float dy = c.y - a.y, dx = c.x - a.x;
                if (dy < gap * .7f || dy > gap * 1.3f || dx > gap * .12f || dx < -gap * .45f)
                    continue;
                if (!budget.take((long) Math.ceil(gap * gap * (64 + gap * 8)))) return List.of();
                Glyph glyph = prove(g, w, h, a, c, gap);
                if (glyph != null
                        && result.stream()
                                .noneMatch(
                                        old ->
                                                Math.abs(old.upper.x - a.x) < gap * .3f
                                                        && Math.abs(old.upper.y - a.y) < gap * .3f))
                    result.add(glyph);
            }
        return result;
    }

    private static Glyph prove(byte[] g, int w, int h, Core a, Core b, float gap) {
        var samples = new ArrayList<Edge>();
        float previous = Float.NaN;
        for (int y = Math.round(b.y + gap * .45f);
                y <= Math.min(h - 1, Math.round(b.y + gap * 1.8f));
                y++) {
            if (rule(g, w, h, y, Math.round(b.x), gap)) continue;
            int left = Math.max(0, Math.round(b.x - gap * .05f)),
                    right = Math.min(w - 1, Math.round(b.x + gap));
            Edge chosen = null;
            for (int x = left; x <= right; ) {
                if ((g[y * w + x] & 255) >= 170) {
                    x++;
                    continue;
                }
                int start = x;
                while (x <= right && (g[y * w + x] & 255) < 170) x++;
                if (x - start <= gap * .5f) {
                    chosen = new Edge(y, (start + x - 1) * .5f, x - start);
                    break;
                }
            }
            if (chosen == null) {
                if (!samples.isEmpty()) break;
                continue;
            }
            if (Float.isFinite(previous)
                    && (chosen.x > previous + 1 || previous - chosen.x > gap * .25f)) break;
            samples.add(chosen);
            previous = chosen.x;
        }
        if (samples.size() < Math.max(4, Math.ceil(gap * .4f))) return null;
        int lastY = samples.get(samples.size() - 1).y, firstY = samples.get(0).y;
        if (lastY - firstY < gap * .5f || lastY - b.y < gap * .8f || lastY - b.y > gap * 1.9f)
            return null;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (var p : samples) {
            double y = p.y - firstY;
            sx += y;
            sy += p.x;
            sxx += y * y;
            sxy += y * p.x;
        }
        double n = samples.size(), den = n * sxx - sx * sx;
        if (den <= 0) return null;
        double slope = (n * sxy - sx * sy) / den, intercept = (sy - slope * sx) / n;
        if (slope > -.025 || slope < -.55) return null;
        for (var p : samples)
            if (Math.abs(p.x - (intercept + slope * (p.y - firstY))) > Math.max(1, gap * .08f))
                return null;
        for (var p : new Core[] {a, b}) {
            double offset = intercept + slope * (p.y - firstY) - p.x;
            if (offset < gap * .25f || offset > gap) return null;
        }
        int visible = 0, connected = 0, axisRadius = Math.max(1, Math.round(gap * .15f));
        int top = Math.max(0, Math.round(a.y - gap * .4f));
        for (int y = Math.round(a.y - gap * .15f); y <= lastY; y++) {
            if (rule(g, w, h, y, Math.round(b.x), gap)) continue;
            visible++;
            int x = (int) Math.round(intercept + slope * (y - firstY)), ink = 0;
            for (int xx = Math.max(0, x - axisRadius); xx <= Math.min(w - 1, x + axisRadius); xx++)
                if ((g[y * w + xx] & 255) < 170) ink++;
            if (ink >= 2) connected++;
        }
        if (visible < gap || connected < visible * .9f) return null;
        int[] strokes = samples.stream().mapToInt(Edge::stroke).sorted().toArray();
        int requiredClear = Math.max(3, strokes[strokes.length / 2]);
        int clear = 0;
        for (int y = lastY + 1; y <= Math.min(h - 1, lastY + Math.round(gap * 1.5f)); y++) {
            if (rule(g, w, h, y, Math.round(b.x), gap)) continue;
            int x = (int) Math.round(intercept + slope * (y - firstY));
            boolean paper = true;
            for (int xx = Math.max(0, x - 1); xx <= Math.min(w - 1, x + 1); xx++)
                if ((g[y * w + xx] & 255) < 170) paper = false;
            if (!paper) break;
            if (++clear >= requiredClear) break;
        }
        if (clear < requiredClear) return null;
        int left = Math.max(0, (int) Math.floor(Math.min(a.x, b.x) - gap * .5f));
        int
                right =
                        Math.min(
                                w - 1,
                                (int) Math.ceil(intercept + slope * (top - firstY) + gap * .25f)),
                outside = 0;
        if (!completeUpperCap(g, w, h, a, b, gap, left, top, right, intercept, slope, firstY))
            return null;
        for (int y = top; y <= lastY; y++) {
            if (rule(g, w, h, y, Math.round(b.x), gap)) continue;
            double axis = intercept + slope * (y - firstY);
            for (int x = left; x <= right; x++)
                if ((g[y * w + x] & 255) < 170) {
                    boolean bulb = false;
                    for (var p : new Core[] {a, b})
                        if (Math.pow((x - p.x) / (gap * .5), 2)
                                        + Math.pow((y - p.y) / (gap * .4), 2)
                                <= 1) bulb = true;
                    if (!bulb
                            && Math.abs(x - axis) > gap * .25f
                            && ++outside > Math.max(2, gap * .2f)) return null;
                }
        }
        return new Glyph(left, top, right, lastY, a, b);
    }

    /** The selected upper bulb must own a closed cap, not the lower part of a larger glyph. */
    private static boolean completeUpperCap(
            byte[] g,
            int w,
            int h,
            Core upper,
            Core lower,
            float gap,
            int left,
            int top,
            int right,
            double intercept,
            double slope,
            int firstY) {
        if (left <= 0 || right >= w - 1 || top <= 0) return false;
        int l = Math.max(0, left - 1),
                r = Math.min(w - 1, right + 1),
                t = Math.max(0, top - (int) Math.ceil(gap));
        int bottom = Math.min(h - 1, Math.round(upper.y + gap * .4f)),
                sw = r - l + 1,
                sh = bottom - t + 1;
        if (sw < 1 || sh < 1 || top <= t) return false;
        boolean[] staff = new boolean[sh], seen = new boolean[sw * sh];
        int[] queue = new int[seen.length];
        for (int y = t; y <= bottom; y++)
            staff[y - t] = exactRuleRow(g, w, h, y, Math.round(lower.x), gap);
        int sy = Math.round(upper.y), count = 0, cursor = 0;
        // Rule contact can hide a bulb-to-shaft neck. Both independently proved roles own the cap.
        for (int sx :
                new int[] {
                    Math.round(upper.x), (int) Math.round(intercept + slope * (sy - firstY))
                })
            for (int y = Math.max(top, sy - 1); y <= Math.min(bottom, sy + 1); y++)
                for (int x = Math.max(left, sx - 1); x <= Math.min(right, sx + 1); x++)
                    if (!staff[y - t] && (g[y * w + x] & 255) < 170) {
                        int at = (y - t) * sw + x - l;
                        if (!seen[at]) {
                            seen[at] = true;
                            queue[count++] = at;
                        }
                    }
        if (count == 0) return false;
        while (cursor < count) {
            int at = queue[cursor++], x = l + at % sw, y = t + at / sw;
            for (int dy = -1; dy <= 1; dy++) {
                int yy = y + dy, skipped = 0;
                while (yy >= t && yy <= bottom && staff[yy - t]) {
                    if (dy == 0) break;
                    yy += dy;
                    skipped++;
                }
                if (yy < t) {
                    if (skipped > 0) return false;
                    continue;
                }
                if (yy > bottom || staff[yy - t]) continue;
                int reach = skipped == 0 ? 1 : 1 + (int) Math.ceil(.55 * skipped);
                for (int xx = Math.max(l, x - reach); xx <= Math.min(r, x + reach); xx++) {
                    if ((g[yy * w + xx] & 255) >= 170) continue;
                    if (xx < left || xx > right || yy < top) return false;
                    int next = (yy - t) * sw + xx - l;
                    if (!seen[next]) {
                        seen[next] = true;
                        queue[count++] = next;
                    }
                }
            }
        }
        return true;
    }

    /** Cap connectivity removes actual rule ink, preserving paper next to a padded rule band. */
    private static boolean exactRuleRow(byte[] g, int w, int h, int y, int cx, float gap) {
        for (int direction : new int[] {-1, 1}) {
            int samples = 0, ink = 0;
            for (int dx = Math.round(gap * 2); dx <= Math.round(gap * 4); dx++) {
                int x = cx + direction * dx;
                if (x < 0 || x >= w) continue;
                samples++;
                if ((g[y * w + x] & 255) < 170) ink++;
            }
            if (samples < Math.max(6, Math.round(gap)) || ink < samples * .85f) return false;
        }
        return true;
    }

    private static boolean rule(byte[] g, int w, int h, int y, int x, float gap) {
        return FlagRootStaffRule.matches(g, w, h, y, x, gap, 170);
    }

    private static boolean dot(byte[] g, int w, int h, Glyph body, float gap, Budget budget) {
        int radius = Math.max(1, Math.round(gap * .12f));
        if (!budget.take((long) Math.ceil(gap * gap * (16 + gap * 6)))) return true;
        for (int y = Math.max(radius, Math.round(body.upper.y - gap * .3f));
                y <= Math.min(h - 1 - radius, Math.round(body.lower.y + gap * .3f));
                y++)
            for (int x = Math.max(radius, Math.round(body.right + gap * .25f));
                    x <= Math.min(w - 1 - radius, Math.round(body.right + gap));
                    x++) {
                if ((g[y * w + x] & 255) >= 170) continue;
                boolean filled = true;
                for (int dy = -radius; dy <= radius && filled; dy++)
                    for (int dx = -radius; dx <= radius; dx++)
                        if (dx * dx + dy * dy <= radius * radius
                                && (g[(y + dy) * w + x + dx] & 255) >= 170) {
                            filled = false;
                            break;
                        }
                if (filled) return true;
            }
        return false;
    }
}
