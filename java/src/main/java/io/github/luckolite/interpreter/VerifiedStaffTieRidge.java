// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** A returning thin contour measured in an independently verified physical staff frame. */
final class VerifiedStaffTieRidge {
    private VerifiedStaffTieRidge() {}

    /** A proved rule's own curvature cannot count as a separate visible tie contour. */
    static boolean followsRule(StaffPitchTrack track, int left, int right, float[] centers) {
        if (track == null
                || !track.verified()
                || centers == null
                || centers.length < 20
                || left < 0
                || right <= left) return false;
        int visible = 0;
        int[] matches = new int[5];
        for (int i = 0; i < centers.length; i++) {
            if (!Float.isFinite(centers[i])) continue;
            float[] frame =
                    track.at(Math.round(left + (i + .5f) * (right - left) / centers.length));
            if (!Float.isFinite(frame[0] + frame[1]) || frame[1] < 5) return false;
            visible++;
            for (int rule = 0; rule < 5; rule++)
                if (Math.abs(centers[i] - (frame[0] - rule * frame[1]))
                        <= Math.max(1f, frame[1] * .18f)) matches[rule]++;
        }
        if (visible < 34) return false;
        for (int count : matches) if (count >= visible * .95f) return true;
        return false;
    }

    static boolean proved(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float ax,
            float ay,
            float bx,
            float by,
            int staffStep,
            StaffPitchTrack track) {
        return proved(
                labels, gray, width, height, left, right, ax, ay, bx, by, staffStep, track, null,
                0);
    }

    /** The whole bow must terminate inside the actual printed head bodies. */
    static boolean provedAtHeadBounds(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float ax,
            float ay,
            float bx,
            float by,
            int step,
            StaffPitchTrack track) {
        if (left < 0 || right >= width || left > ax || right < bx) return false;
        int[] bounds = {
            left, right, Math.round(ax), Math.round(ay), Math.round(bx), Math.round(by)
        };
        for (int a = left; a <= Math.round(ax); a++)
            for (int b = right; b >= Math.round(bx); b--)
                if (proved(
                        labels, gray, width, height, a, b, ax, ay, bx, by, step, track, bounds, 0))
                    return true;
        return false;
    }

    static boolean provedSystemEnd(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int min,
            int max,
            float hx,
            float hy,
            float oldGap,
            int step,
            StaffPitchTrack track,
            boolean outgoing,
            int side) {
        if (track == null || !track.verified()) return false;
        float[] at = track.at(hx);
        float residual = hy - (at[0] - step * at[1] * .5f);
        int jump = Math.max(2, Math.round(oldGap * .2f));
        for (int span = Math.round(oldGap * 1.6f);
                span <= oldGap * (outgoing ? 14 : 7);
                span += jump) {
            int left = outgoing ? min : Math.round(hx) - span,
                    right = outgoing ? Math.round(hx) + span : max;
            if (left < 0 || right >= width) continue;
            float ax = outgoing ? hx : left, bx = outgoing ? right : hx;
            float[] a = track.at(ax), b = track.at(bx);
            float ay = outgoing ? hy : a[0] - step * a[1] * .5f + residual;
            float by = outgoing ? b[0] - step * b[1] * .5f + residual : hy;
            int[] bounds = {
                left,
                right,
                Math.round(ax),
                Math.round(ay),
                Math.round(bx),
                Math.round(by),
                outgoing ? 1 : 2
            };
            for (int aa = left; aa <= Math.round(ax); aa++)
                for (int bb = right; bb >= Math.round(bx); bb--)
                    if (proved(
                            labels, gray, width, height, aa, bb, ax, ay, bx, by, step, track,
                            bounds, side)) return true;
        }
        return false;
    }

    private static boolean proved(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float ax,
            float ay,
            float bx,
            float by,
            int staffStep,
            StaffPitchTrack track,
            int[] headBounds,
            int requiredSide) {
        if (labels == null
                || gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || labels.length != gray.length
                || left < 0
                || right >= width
                || right - left < 8
                || !LocalTieStaffAlignment.same(track, ax, ay, bx, by, staffStep)) return false;
        float[] a = track.at(ax), b = track.at(bx);
        float gap = (a[1] + b[1]) * .5f;
        if (gap < 8 || right - left > gap * 6 || bx <= ax) return false;
        float ar = ay - (a[0] - staffStep * a[1] * .5f), br = by - (b[0] - staffStep * b[1] * .5f);
        int[] xs = new int[50];
        float[] base = new float[50], scale = new float[50];
        float[][] frames = new float[50][];
        for (int i = 0; i < 50; i++) {
            xs[i] = Math.round(left + (i + .5f) * (right - left) / 50f);
            frames[i] = track.at(xs[i]);
            scale[i] = frames[i][1];
            float t = Math.max(0, Math.min(1, (xs[i] - ax) / (bx - ax)));
            base[i] = frames[i][0] - staffStep * scale[i] * .5f + ar * (1 - t) + br * t;
        }
        int radius = Math.max(1, Math.round(gap * .1f));
        int[] bins = new int[5], covered = new int[5], paper = new int[50];
        float[] centers = new float[50], relative = new float[50], predicted = new float[50];
        for (int side : new int[] {-1, 1})
            for (float offset = .2f; offset <= 1.15f; offset += .15f)
                candidate:
                for (float bow = .25f; bow <= 1.8f; bow += .1f) {
                    if (requiredSide != 0 && side != requiredSide) continue;
                    if (right - left < gap * 2.6f && offset + bow > 1.6f) continue;
                    Arrays.fill(bins, 0);
                    Arrays.fill(covered, 0);
                    Arrays.fill(centers, Float.NaN);
                    Arrays.fill(relative, Float.NaN);
                    int hits = 0, obscured = 0, strong = 0, paperCount = 0;
                    for (int i = 0; i < 50; i++) {
                        float t = (i + .5f) / 50f;
                        int target =
                                Math.round(
                                        base[i]
                                                + side
                                                        * scale[i]
                                                        * (offset + bow * 4 * t * (1 - t)));
                        predicted[i] = target;
                        int x = xs[i];
                        boolean rule = false, found = false;
                        if (target >= 1 && target < height - 1 && onRule(frames[i], target)) {
                            int ink = gray[target * width + x] & 255;
                            if (ink <= 205
                                    && paper(gray, width, height, x, target, scale[i]) - ink
                                            >= 20) {
                                obscured++;
                                covered[i / 10]++;
                            }
                            continue;
                        }
                        for (int d = 0; d <= radius * 2; d++) {
                            int y = target + (d + 1) / 2 * (d % 2 == 0 ? 1 : -1);
                            if (y < 1
                                    || y >= height - 1
                                    || labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD)
                                continue;
                            int ink = gray[y * width + x] & 255;
                            if (ink > 205) continue;
                            int background = paper(gray, width, height, x, y, scale[i]);
                            if (background - ink < 20) continue;
                            if (onRule(frames[i], y)) {
                                rule = true;
                                continue;
                            }
                            int top = y,
                                    bottom = y,
                                    reach = Math.max(2, Math.round(scale[i] * .4f));
                            int limit = Math.min(205, background - 20);
                            while (top > Math.max(0, y - reach)
                                    && (gray[(top - 1) * width + x] & 255) <= limit) top--;
                            while (bottom < Math.min(height - 1, y + reach)
                                    && (gray[(bottom + 1) * width + x] & 255) <= limit) bottom++;
                            if (bottom - top > scale[i] * .6f) continue;
                            float center = (top + bottom) * .5f;
                            if (Math.abs(center - target) > radius + Math.max(1f, scale[i] * .06f))
                                continue;
                            centers[i] = center;
                            relative[i] = (center - base[i]) / scale[i];
                            hits++;
                            bins[i / 10]++;
                            covered[i / 10]++;
                            if (ink <= 165 && background - ink >= 30) strong++;
                            paper[paperCount++] = background;
                            found = true;
                            break;
                        }
                        if (!found && rule) {
                            obscured++;
                            covered[i / 10]++;
                        }
                    }
                    if (hits < 36
                            || strong < 30
                            || obscured > 14
                            || hits + obscured < 48
                            || bins[0] < 7
                            || bins[4] < 7) continue;
                    for (int k = 0; k < 5; k++) if (covered[k] < 9) continue candidate;
                    float first = mean(relative, 0, 10),
                            middle = mean(relative, 20, 30),
                            last = mean(relative, 40, 50);
                    if (!Float.isFinite(first + middle + last)
                            || side * (middle - first) < .12f
                            || side * (middle - last) < .12f) continue;
                    // A returning engraving progressively flattens at its crest.
                    // Two straight arms meeting at a corner retain their shoulder slope.
                    if (!roundedShoulders(relative, side)) continue;
                    float previous = Float.NaN;
                    int previousX = -1;
                    for (int i = 0; i < 50; i++)
                        if (Float.isFinite(relative[i])) {
                            if (Float.isFinite(previous)
                                    && xs[i] != previousX
                                    && Math.abs(relative[i] - previous) > .3f) continue candidate;
                            previous = relative[i];
                            previousX = xs[i];
                        }
                    if (closedCounterContour(
                            labels,
                            gray,
                            width,
                            height,
                            xs,
                            base,
                            scale,
                            centers,
                            predicted,
                            side,
                            headBounds,
                            track,
                            requiredSide != 0)) continue;
                    if (headBounds != null
                            && (!stopsAtHead(
                                            labels,
                                            gray,
                                            width,
                                            height,
                                            xs,
                                            centers,
                                            scale,
                                            headBounds[0],
                                            true,
                                            track)
                                    || !stopsAtHead(
                                            labels,
                                            gray,
                                            width,
                                            height,
                                            xs,
                                            centers,
                                            scale,
                                            headBounds[1],
                                            false,
                                            track))) continue;
                    Arrays.sort(paper, 0, paperCount);
                    int branchLimit = Math.min(165, Math.max(80, paper[paperCount / 2] - 20));
                    if (!TieArcBranchInk.outwardStems(
                            gray, width, height, left, right, centers, side, gap, branchLimit))
                        return true;
                }
        return false;
    }

    private static boolean stopsAtHead(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int[] xs,
            float[] centers,
            float[] scale,
            int bound,
            boolean first,
            StaffPitchTrack track) {
        int start = first ? 0 : 38, end = first ? 12 : 50, seed = -1;
        double x = 0, y = 0, xx = 0, xy = 0;
        int count = 0;
        for (int i = start; i < end; i++)
            if (Float.isFinite(centers[i])) {
                if (seed < 0 || !first) seed = i;
                x += xs[i];
                y += centers[i];
                xx += (double) xs[i] * xs[i];
                xy += (double) xs[i] * centers[i];
                count++;
            }
        double divisor = count * xx - x * x;
        if (seed < 0 || count < 5 || divisor <= 0) return false;
        double slope = (count * xy - x * y) / divisor;
        float gap = scale[seed];
        int blank = 0, inkCount = 0;
        for (int d = 1; d <= Math.max(4, Math.round(gap * .65f)); d++) {
            int atX = bound + (first ? -d : d);
            if (atX < 0 || atX >= width) return false;
            int atY = Math.round((float) (centers[seed] + slope * (atX - xs[seed]))),
                    radius = Math.max(1, Math.round(gap * .1f));
            boolean ink = false, rule = false;
            float[] frame = track.at(atX);
            for (int dy = -radius; dy <= radius; dy++) {
                int yy = atY + dy;
                if (yy < 1 || yy >= height - 1) continue;
                int at = yy * width + atX, shade = gray[at] & 255;
                if (labels[at] != OmrMeasurePostProcessor.NOTEHEAD
                        && shade <= 205
                        && paper(gray, width, height, atX, yy, gap) - shade >= 20) {
                    if (onRule(frame, yy)) {
                        rule = true;
                        continue;
                    }
                    ink = true;
                    break;
                }
            }
            if (ink) {
                blank = 0;
                if (++inkCount >= 4) return false;
            } else if (rule) blank = 0;
            else if (++blank >= 3) return true;
        }
        return false;
    }

    /** A closed glyph has a second opposite bow enclosing the same thin contour. */
    private static boolean closedCounterContour(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int[] xs,
            float[] base,
            float[] scale,
            float[] contour,
            float[] predicted,
            int side,
            int[] headBounds,
            StaffPitchTrack track,
            boolean freeSystemEnd) {
        float[] counter = new float[50], counterPixels = new float[50];
        int[] bins = new int[5];
        for (float axis = -1.2f; axis <= 1.2f; axis += .025f) {
            Arrays.fill(counter, Float.NaN);
            Arrays.fill(counterPixels, Float.NaN);
            Arrays.fill(bins, 0);
            int hits = 0, separate = 0, strong = 0;
            for (int i = 0; i < 50; i++) {
                float main = Float.isFinite(contour[i]) ? contour[i] : predicted[i];
                float target = 2 * (base[i] + axis * scale[i]) - main;
                if (Math.abs(target - main) < scale[i] * (headBounds == null ? .45f : .2f))
                    continue;
                int x = xs[i], radius = Math.max(1, Math.round(scale[i] * .1f));
                for (int d = 0; d <= radius * 2; d++) {
                    int y = Math.round(target) + (d + 1) / 2 * (d % 2 == 0 ? 1 : -1);
                    if (y < 1
                            || y >= height - 1
                            || labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) continue;
                    int ink = gray[y * width + x] & 255,
                            background = paper(gray, width, height, x, y, scale[i]);
                    if (ink > 205
                            || counterContrast(labels, gray, width, height, x, y, scale[i], track)
                                    < 20) continue;
                    int top = y,
                            bottom = y,
                            reach = Math.max(2, Math.round(scale[i] * .4f)),
                            limit = Math.min(205, background - 20);
                    while (top > Math.max(0, y - reach)
                            && labels[(top - 1) * width + x] != OmrMeasurePostProcessor.NOTEHEAD
                            && counterContrast(
                                            labels, gray, width, height, x, top - 1, scale[i],
                                            track)
                                    >= 20) top--;
                    while (bottom < Math.min(height - 1, y + reach)
                            && labels[(bottom + 1) * width + x] != OmrMeasurePostProcessor.NOTEHEAD
                            && counterContrast(
                                            labels, gray, width, height, x, bottom + 1, scale[i],
                                            track)
                                    >= 20) bottom++;
                    float center = (top + bottom) * .5f;
                    if (bottom - top > scale[i] * .6f || Math.abs(center - target) > radius + 1)
                        continue;
                    counterPixels[i] = center;
                    counter[i] = (center - base[i]) / scale[i];
                    hits++;
                    bins[i / 10]++;
                    if (ink <= 165
                            && counterContrast(labels, gray, width, height, x, y, scale[i], track)
                                    >= 30) strong++;
                    if (i >= 10
                            && i < 40
                            && Float.isFinite(contour[i])
                            && side * (contour[i] - center)
                                    >= scale[i] * (headBounds == null ? .6f : .3f)) separate++;
                    break;
                }
            }
            if (headBounds != null
                    && maskedCounterReturn(counter, side, hits, strong, separate)
                    && joinedHeadBody(
                            labels,
                            gray,
                            width,
                            height,
                            xs,
                            contour,
                            counterPixels,
                            scale,
                            headBounds[0],
                            true,
                            track,
                            headBounds[2],
                            headBounds[3],
                            headBounds.length == 6 || headBounds[6] != 2)
                    && joinedHeadBody(
                            labels,
                            gray,
                            width,
                            height,
                            xs,
                            contour,
                            counterPixels,
                            scale,
                            headBounds[1],
                            false,
                            track,
                            headBounds[4],
                            headBounds[5],
                            headBounds.length == 6 || headBounds[6] != 1)) return true;
            if (hits < (headBounds == null ? 34 : 26)
                    || strong < (headBounds == null ? 26 : 20)
                    || separate < (headBounds == null ? 20 : 14)
                    || bins[0] < (headBounds == null ? 5 : freeSystemEnd ? 1 : 2)
                    || bins[4] < (headBounds == null ? 5 : freeSystemEnd ? 1 : 2)) continue;
            float first = mean(counter, 0, 10),
                    middle = mean(counter, 20, 30),
                    last = mean(counter, 40, 50);
            float returnMinimum = headBounds == null ? .12f : .1f - .5f / scale[25];
            if (Float.isFinite(first + middle + last)
                    && -side * (middle - first) >= returnMinimum
                    && -side * (middle - last) >= returnMinimum
                    && (headBounds != null || roundedShoulders(counter, -side))) {
                // An opposite return is an ambiguous closed body unless its own
                // thin raw stroke demonstrably continues beyond a printed bound.
                // Adjacent ink and verified rules cannot establish that ownership.
                if (headBounds != null
                        && (continuesBeyondBound(
                                        labels,
                                        gray,
                                        width,
                                        height,
                                        xs,
                                        counterPixels,
                                        scale,
                                        headBounds[0],
                                        true,
                                        track,
                                        contour)
                                || continuesBeyondBound(
                                        labels,
                                        gray,
                                        width,
                                        height,
                                        xs,
                                        counterPixels,
                                        scale,
                                        headBounds[1],
                                        false,
                                        track,
                                        contour))) continue;
                return true;
            }
        }
        return false;
    }

    /** A second independent return remains ambiguous when heads obscure its joined tips. */
    private static boolean maskedCounterReturn(
            float[] values, int side, int hits, int strong, int separate) {
        if (hits < 20 || strong < 16 || separate < 14) return false;
        int first = 50, last = -1;
        int[] bins = new int[3];
        double[][] m = new double[3][4];
        int n = 0;
        for (int i = 0; i < 50; i++)
            if (Float.isFinite(values[i])) {
                first = Math.min(first, i);
                last = i;
                if (i >= 10 && i < 40) bins[(i - 10) / 10]++;
                double t = (i + .5) / 50;
                double[] v = {1, t, t * t};
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) m[a][b] += v[a] * v[b];
                    m[a][3] += v[a] * values[i];
                }
                n++;
            }
        if (first > 15 || last < 35 || bins[0] < 4 || bins[1] < 8 || bins[2] < 4) return false;
        for (int a = 0; a < 3; a++) {
            int best = a;
            for (int b = a + 1; b < 3; b++) if (Math.abs(m[b][a]) > Math.abs(m[best][a])) best = b;
            double[] swap = m[a];
            m[a] = m[best];
            m[best] = swap;
            double d = m[a][a];
            if (Math.abs(d) < 1e-8) return false;
            for (int b = a; b < 4; b++) m[a][b] /= d;
            for (int row = 0; row < 3; row++)
                if (row != a) {
                    double factor = m[row][a];
                    for (int b = a; b < 4; b++) m[row][b] -= factor * m[a][b];
                }
        }
        double c0 = m[0][3], c1 = m[1][3], c2 = m[2][3], vertex = -c1 / (2 * c2), error = 0;
        if (side * c2 < .8 || vertex < .25 || vertex > .75) return false;
        for (int i = 0; i < 50; i++)
            if (Float.isFinite(values[i])) {
                double t = (i + .5) / 50, d = values[i] - c0 - c1 * t - c2 * t * t;
                error += d * d;
            }
        return error / n <= .02;
    }

    /** Only a connected original-pixel body can join the two opposed returning strokes. */
    private static boolean joinedHeadBody(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int[] xs,
            float[] primary,
            float[] counter,
            float[] scale,
            int bound,
            boolean first,
            StaffPitchTrack track,
            int headX,
            int headY,
            boolean actualHead) {
        int a = -1, b = -1;
        for (int i = 0; i < 50; i++) {
            if (Float.isFinite(primary[i]) && (a < 0 || !first)) a = i;
            if (Float.isFinite(counter[i]) && (b < 0 || !first)) b = i;
            if (first && a >= 0 && b >= 0) break;
        }
        if (a < 0 || b < 0) return false;
        float gap = scale[a];
        int window = Math.max(4, Math.round(gap * .65f));
        int[] mask =
                actualHead ? semanticHeadBounds(labels, width, height, headX, headY, gap) : null;
        if (actualHead && mask == null) return false;
        int left = Math.max(0, first ? bound - 1 : actualHead ? mask[0] - 2 : bound - window),
                right =
                        Math.min(
                                width - 1,
                                first ? actualHead ? mask[1] + 2 : bound + window : bound + 1);
        if (xs[a] < left || xs[a] > right || xs[b] < left || xs[b] > right) return false;
        int margin = Math.max(2, Math.round(gap * .35f));
        int top = Math.max(1, Math.round(Math.min(primary[a], counter[b])) - margin),
                bottom =
                        Math.min(height - 2, Math.round(Math.max(primary[a], counter[b])) + margin);
        int rows = bottom - top + 1, columns = right - left + 1;
        if (rows <= 0 || columns <= 0) return false;
        boolean[] seen = new boolean[rows * columns];
        int[] queue = new int[seen.length];
        int read = 0, write = 0;
        int start = (Math.round(primary[a]) - top) * columns + xs[a] - left,
                targetY = Math.round(counter[b]);
        if (start < 0 || start >= seen.length) return false;
        queue[write++] = start;
        seen[start] = true;
        while (read < write) {
            int cell = queue[read++], x = left + cell % columns, y = top + cell / columns;
            if (x == xs[b] && Math.abs(y - targetY) <= 1) return true;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    int xx = x + dx, yy = y + dy;
                    if (xx < left || xx > right || yy < top || yy > bottom) continue;
                    int next = (yy - top) * columns + xx - left;
                    if (seen[next]) continue;
                    int at = yy * width + xx, ink = gray[at] & 255;
                    if (ink > 205 || paper(gray, width, height, xx, yy, gap) - ink < 20) continue;
                    // A semantic head is a justified joined-tip occlusion. A verified
                    // rule's own darkening is removed before it can supply a path.
                    if (!actualHead && labels[at] == OmrMeasurePostProcessor.NOTEHEAD) continue;
                    if (labels[at] != OmrMeasurePostProcessor.NOTEHEAD
                            && counterContrast(labels, gray, width, height, xx, yy, gap, track)
                                    < 20) continue;
                    seen[next] = true;
                    queue[write++] = next;
                }
        }
        return false;
    }

    /** Recover only the connected semantic head mask around the accepted actual head center. */
    private static int[] semanticHeadBounds(
            byte[] labels, int width, int height, int hx, int hy, float gap) {
        int radius = Math.max(2, Math.round(gap * .18f)), seed = -1;
        for (int dy = -radius; dy <= radius && seed < 0; dy++)
            for (int dx = -radius; dx <= radius; dx++) {
                int x = hx + dx, y = hy + dy;
                if (x >= 0
                        && x < width
                        && y >= 0
                        && y < height
                        && labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) {
                    seed = y * width + x;
                    break;
                }
            }
        if (seed < 0) return null;
        int left = Math.max(0, hx - Math.round(gap)),
                right = Math.min(width - 1, hx + Math.round(gap));
        int top = Math.max(0, hy - Math.round(gap * .6f)),
                bottom = Math.min(height - 1, hy + Math.round(gap * .6f));
        int columns = right - left + 1;
        boolean[] seen = new boolean[columns * (bottom - top + 1)];
        int[] queue = new int[seen.length];
        int read = 0, write = 0;
        int first = (seed / width - top) * columns + seed % width - left;
        if (first < 0 || first >= seen.length) return null;
        queue[write++] = first;
        seen[first] = true;
        int min = seed % width, max = min;
        while (read < write) {
            int at = queue[read++], x = left + at % columns, y = top + at / columns;
            min = Math.min(min, x);
            max = Math.max(max, x);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < left || xx > right || yy < top || yy > bottom) continue;
                    int next = (yy - top) * columns + xx - left;
                    if (!seen[next]
                            && labels[yy * width + xx] == OmrMeasurePostProcessor.NOTEHEAD) {
                        seen[next] = true;
                        queue[write++] = next;
                    }
                }
        }
        return write >= 4 ? new int[] {min, max} : null;
    }

    /** Other simultaneously proved rules supply the raw rule profile at this column. */
    private static int counterContrast(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int x,
            int y,
            float gap,
            StaffPitchTrack track) {
        int raw = paper(gray, width, height, x, y, gap) - (gray[y * width + x] & 255);
        float[] f = track.at(x);
        int nearest = -1;
        float offset = 0, best = Float.POSITIVE_INFINITY;
        for (int rule = 0; rule < 5; rule++) {
            float d = y - (f[0] - rule * f[1]);
            if (Math.abs(d) < best) {
                best = Math.abs(d);
                offset = d;
                nearest = rule;
            }
        }
        if (best > gap * .4f) return raw;
        int[] samples = new int[4];
        int n = 0;
        for (int rule = 0; rule < 5; rule++)
            if (rule != nearest) {
                int yy = Math.round(f[0] - rule * f[1] + offset);
                if (yy < 1
                        || yy >= height - 1
                        || labels[yy * width + x] == OmrMeasurePostProcessor.NOTEHEAD) continue;
                samples[n++] =
                        Math.max(
                                0,
                                paper(gray, width, height, x, yy, gap)
                                        - (gray[yy * width + x] & 255));
            }
        if (n < 3) return raw;
        Arrays.sort(samples, 0, n);
        return raw - samples[n / 2];
    }

    private static boolean continuesBeyondBound(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int[] xs,
            float[] centers,
            float[] scale,
            int bound,
            boolean first,
            StaffPitchTrack track,
            float[] primary) {
        int start = first ? 0 : 38, end = first ? 12 : 50, seed = -1;
        double x = 0, y = 0, xx = 0, xy = 0;
        int count = 0;
        for (int i = start; i < end; i++)
            if (Float.isFinite(centers[i])) {
                if (seed < 0 || !first) seed = i;
                float[] f = track.at(xs[i]);
                double value = (centers[i] - f[0]) / f[1];
                x += xs[i];
                y += value;
                xx += (double) xs[i] * xs[i];
                xy += xs[i] * value;
                count++;
            }
        double divisor = count * xx - x * x;
        if (seed < 0 || count < 5 || divisor <= 0) return false;
        double slope = (count * xy - x * y) / divisor;
        float gap = scale[seed];
        float[] seedFrame = track.at(xs[seed]);
        double anchor = (centers[seed] - seedFrame[0]) / seedFrame[1];
        int direction = first ? -1 : 1,
                previousTop = Math.round(centers[seed]),
                previousBottom = previousTop,
                outside = 0;
        int finalX = bound + direction * Math.max(4, Math.round(gap * .65f));
        for (int atX = xs[seed] + direction; direction * (atX - finalX) <= 0; atX += direction) {
            if (atX < 0 || atX >= width) return false;
            float[] f = track.at(atX);
            int predicted = Math.round((float) (f[0] + f[1] * (anchor + slope * (atX - xs[seed]))));
            int radius = Math.max(1, Math.round(gap * .1f)), top = -1, bottom = -1;
            for (int dy = -radius; dy <= radius; dy++) {
                int yy = predicted + dy;
                if (yy < 1 || yy >= height - 1) continue;
                int at = yy * width + atX,
                        ink = gray[at] & 255,
                        background = paper(gray, width, height, atX, yy, gap);
                if (labels[at] == OmrMeasurePostProcessor.NOTEHEAD
                        || ink > 205
                        || background - ink < 20) continue;
                int a = yy,
                        b = yy,
                        limit = Math.min(205, background - 20),
                        reach = Math.max(2, Math.round(gap * .4f));
                while (a > Math.max(0, yy - reach) && (gray[(a - 1) * width + atX] & 255) <= limit)
                    a--;
                while (b < Math.min(height - 1, yy + reach)
                        && (gray[(b + 1) * width + atX] & 255) <= limit) b++;
                if (b - a > gap * .6f
                        || onRule(f, (a + b) * .5f)
                        || a > previousBottom + 1
                        || b < previousTop - 1) continue;
                if (top >= 0 && (a != top || b != bottom)) return false;
                top = a;
                bottom = b;
            }
            if (top < 0) return false;
            // A connected tail remains owned by a closed body. Only a counter
            // that stays separate from the measured primary stroke can exempt
            // the closed-body veto as a distinct larger slur.
            float main = measuredContourAt(xs, primary, atX, gap);
            if (!Float.isFinite(main))
                main = projectedPrimaryShoulder(xs, primary, atX, gap, first, track);
            if (Float.isFinite(main) && main >= top - 1 && main <= bottom + 1) return false;
            previousTop = top;
            previousBottom = bottom;
            if (direction * (atX - bound) > 0 && ++outside >= 4) return true;
        }
        return false;
    }

    /** Interpolate only between nearby observed original-pixel ridge knots. */
    private static float measuredContourAt(int[] xs, float[] centers, int x, float gap) {
        int left = -1, right = -1;
        for (int i = 0; i < xs.length; i++)
            if (Float.isFinite(centers[i])) {
                if (xs[i] == x) return centers[i];
                if (xs[i] < x) left = i;
                if (xs[i] > x) {
                    right = i;
                    break;
                }
            }
        if (left < 0 || right < 0 || xs[right] - xs[left] > Math.max(2f, gap * .18f))
            return Float.NaN;
        float t = (x - xs[left]) / (float) (xs[right] - xs[left]);
        return centers[left] + t * (centers[right] - centers[left]);
    }

    /** A shared endpoint run cannot prove that an opposed return is a separate slur. */
    private static float projectedPrimaryShoulder(
            int[] xs, float[] centers, int atX, float gap, boolean first, StaffPitchTrack track) {
        int start = first ? 0 : 38, end = first ? 12 : 50, seed = -1, count = 0;
        double x = 0, y = 0, xx = 0, xy = 0;
        for (int i = start; i < end; i++)
            if (Float.isFinite(centers[i])) {
                if (seed < 0 || !first) seed = i;
                float[] f = track.at(xs[i]);
                double value = (centers[i] - f[0]) / f[1];
                x += xs[i];
                y += value;
                xx += (double) xs[i] * xs[i];
                xy += xs[i] * value;
                count++;
            }
        double divisor = count * xx - x * x;
        if (seed < 0
                || count < 5
                || divisor <= 0
                || Math.abs(atX - xs[seed]) > Math.max(4, gap * .65f)) return Float.NaN;
        float[] f = track.at(atX), a = track.at(xs[seed]);
        double slope = (count * xy - x * y) / divisor;
        return (float) (f[0] + f[1] * ((centers[seed] - a[0]) / a[1] + slope * (atX - xs[seed])));
    }

    /** A proven staff rule can obscure ink, but its curve cannot supply visible tie samples. */
    private static boolean onRule(float[] frame, float y) {
        for (int line = 0; line < 5; line++)
            if (Math.abs(y - (frame[0] - line * frame[1])) <= Math.max(1f, frame[1] * .18f))
                return true;
        return false;
    }

    private static int paper(byte[] gray, int width, int height, int x, int y, float gap) {
        int result = 0;
        for (int offset :
                new int[] {
                    Math.max(2, Math.round(gap * .35f)), Math.max(3, Math.round(gap * .65f))
                }) {
            if (y >= offset) result = Math.max(result, gray[(y - offset) * width + x] & 255);
            if (y + offset < height)
                result = Math.max(result, gray[(y + offset) * width + x] & 255);
        }
        return result;
    }

    private static float mean(float[] values, int start, int end) {
        float sum = 0;
        int n = 0;
        for (int i = start; i < end; i++)
            if (Float.isFinite(values[i])) {
                sum += values[i];
                n++;
            }
        return n > 0 ? sum / n : Float.NaN;
    }

    private static boolean roundedShoulders(float[] centers, int side) {
        // Compare a smooth curved ridge with the best two straight arms.
        // A corner may have the same endpoint return, but its arms fit better.
        double curved = fitError(centers, Float.NaN), corner = Double.POSITIVE_INFINITY;
        for (float vertex = .3f; vertex <= .7f; vertex += .05f)
            corner = Math.min(corner, fitError(centers, vertex));
        return Double.isFinite(curved + corner) && curved < corner * .8;
    }

    private static double fitError(float[] centers, float vertex) {
        double[][] m = new double[3][4];
        int n = 0;
        for (int i = 0; i < centers.length; i++)
            if (Float.isFinite(centers[i])) {
                double t = (i + .5) / centers.length;
                double[] v = {1, t, Float.isNaN(vertex) ? t * t : Math.abs(t - vertex)};
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) m[a][b] += v[a] * v[b];
                    m[a][3] += v[a] * centers[i];
                }
                n++;
            }
        if (n < 26) return Double.POSITIVE_INFINITY;
        for (int a = 0; a < 3; a++) {
            int best = a;
            for (int b = a + 1; b < 3; b++) if (Math.abs(m[b][a]) > Math.abs(m[best][a])) best = b;
            double[] swap = m[a];
            m[a] = m[best];
            m[best] = swap;
            double scale = m[a][a];
            if (Math.abs(scale) < 1e-8) return Double.POSITIVE_INFINITY;
            for (int b = a; b < 4; b++) m[a][b] /= scale;
            for (int row = 0; row < 3; row++)
                if (row != a) {
                    double factor = m[row][a];
                    for (int b = a; b < 4; b++) m[row][b] -= factor * m[a][b];
                }
        }
        double sum = 0;
        for (int i = 0; i < centers.length; i++)
            if (Float.isFinite(centers[i])) {
                double t = (i + .5) / centers.length;
                double error =
                        centers[i]
                                - m[0][3]
                                - m[1][3] * t
                                - m[2][3] * (Float.isNaN(vertex) ? t * t : Math.abs(t - vertex));
                sum += error * error;
            }
        return sum / n;
    }
}
