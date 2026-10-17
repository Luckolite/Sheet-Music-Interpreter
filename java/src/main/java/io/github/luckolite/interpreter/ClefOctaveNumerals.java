// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Intrinsic bass-clef numerals, proved separately from directions and staff geometry. */
final class ClefOctaveNumerals {
    private record Numeral(int digit, float aspect, byte[] bits) {
        Numeral(int digit, float aspect, String bits) {
            this(digit, aspect, Base64.getDecoder().decode(bits));
        }
    }

    // Original 24pt SIL-OFL Bravura and Noto Serif regular/bold rasterizations, 16x24 at175.
    private static final Numeral[] NUMERALS = {
        new Numeral(
                0, 0.9166667f, "4A/gD/g++D48eDx4PHg8eDz4PPg/+D/4P/g/+Dz4PPg8eDx4/Hj8ePg++D7gD+AP"),
        new Numeral(
                1, 0.5833333f, "gP+A/+D/4P/g/+D/+P/4/5j/mP+f/5//gP+A/4D/gP+A/4D/gP+A/4D/gP/g/+D/"),
        new Numeral(
                2, 0.8333333f, "8B/wHxz8HPx/8H/wf/x//Hz8fPwAfwB/gB+AH+AA4ADwj/CP/P/8//x//H8MHwwf"),
        new Numeral(
                3, 0.8333333f, "8A/wDxx8HHx8fHx8fHx8fBB/EH/gD+APAH8Af3z8fPx//H/8f/x//Hx/fH/wD/AP"),
        new Numeral(
                4, 0.9166667f, "wA/AD+AP4A/gB+AH4DngOeA+4D74P/g/OD84Pxw/HD//////AD8APwA/AD/Af8B/"),
        new Numeral(5, 0.75f, "/H/8f/x//H/8H/wfDAAMAPwf/B8Mfgx+MP4w/vz4/Pj/+P/4//7//jx+PH7wH/Af"),
        new Numeral(
                6, 0.8333333f, "4B/gH/B88Hx8fHx8fHx8fHwAfAD/H/8f/3z/fH/8f/x8/Hz8fPx8/PB88HzgH+Af"),
        new Numeral(
                7, 0.8333333f, "8IPwg//v/+//////j/+P/wN8A3wAcABwAHwAfAAfAB+AH4Af4B/gH+Af4B/gD+AP"),
        new Numeral(
                8, 0.8333333f, "8B/wHxxwHHAc4Bzgf2B/YPx//H/wf/B/8P/w/xz/HP8P8A/wD/AP8BxwHHDwH/Af"),
        new Numeral(
                9, 0.8333333f, "8A/wD3x8fHx8fHx8f/x//Hz8fPz8//z/8P/w/wD8APz8fPx8/Hz8fHwffB/gA+AD"),
        new Numeral(
                0, 0.6666667f, "gACAAPAPeDw8ODw4DHgPeA94D3gPcA9wD/AP8A9wD3APeA94D3gMeDw4PDh4PPAP"),
        new Numeral(
                1, 0.5882353f, "gAOAA/AD/wP/A48DgAOAA4ADgAOAA4ADgAOAA4ADgAOAA4ADgAOAA4APgA/gD///"),
        new Numeral(
                2, 0.6111111f, "AAEAAfg/PHgceBx4HHAAcAB4AHgAeAA4AA4ADgAHwAfAAcAB4AA4gBzAHMD/////"),
        new Numeral(
                3, 0.6111111f, "AAEAAfg/PHgccBxwHPAAcAB4AHgAOMAP4D/gPwB4APAA8ADwAPAE8AfwB/A/fvw/"),
        new Numeral(
                4, 0.7647059f, "AB4AHgAeAB8AH4AdgByAHOAccBxwHHAcGBwMHAwcDBz//wA+AD4AHAAcABwAPoD/"),
        new Numeral(
                5, 0.64705884f, "+H/4f/x/HEAcQBxAHAAcABwAHAD8P/w/BH4AeAB4APAA8ADwAPAE8Ad4B3g/PvwP"),
        new Numeral(
                6, 0.6111111f, "AA4ADsB/+PA48DjwPEA8ABwAHAAfBv9/P/g/+B/wH/AcwBzAHMA88DzwPPD4eOA/"),
        new Numeral(
                7, 0.64705884f, "////////B/AH8AdwAHAAcAB4ADgAOAA4AD4ADgAOAA8ADwAHAAfAB8ABwAHAAeAB"),
        new Numeral(
                8, 0.6111111f, "AAEAAfg/PHgc8BzwHPAc8DxwPHD8ePgP4D/gPzh+HPAfwB/AH8AfwB/wH/A8ePg/"),
        new Numeral(
                9, 0.6111111f, "AAEAAfgPPDgceBx4H/Af8B/wH/Af8B/wPPg8+Pj/wPEA8ADwAHAAeAR4BHgcPvwP"),
        new Numeral(
                0, 0.6666667f, "8A/wD/g/fHw8eDx4P/g/+D/wP/A/8D/wP/A/8D/wP/A/+D/4P/g8eHx8fHz4P/AP"),
        new Numeral(
                1, 0.6111111f, "AA8AD8AP+A//D/8PPA8ADwAPAA8ADwAPAA8ADwAPAA8ADwAPAA8ADwAPAA/8//z/"),
        new Numeral(
                2, 0.6666667f, "+A/4D/w/PHw8eDx4PHgIeAB8AHwAPAA/AA8AD4AHwAPwAPAAeMA88Pz//P//////"),
        new Numeral(
                3, 0.6666667f, "+A/4D/w/PHw8eDx4PHgAeAB8AHyAP/AP8H/wfwB8APgA+AD4DPgP+D98P3z8P/gP"),
        new Numeral(
                4, 0.7777778f, "ABwAHAAfgB+AH4AfwB/gHGAcYBxwHDgcGBwYHBwc////////AB8AHAAfAB/Af8B/"),
        new Numeral(
                5, 0.6666667f, "+H/4f/x//H88cDxwPHAMAAwADAD8D/w/DH8MfwB4AHgA+AD4AHgPeD98P3z/P/wP"),
        new Numeral(
                6, 0.6666667f, "wD/AP/B/+Ph8cHxwPDA8ADwAPAD/P/9/f/h/+D/4P/A88DzwPPh8+Hh4eHj4f/A/"),
        new Numeral(
                7, 0.6666667f, "//////////8PeA94B3gAeAA8ADwAPAA/AA8ADwAPgA+AB4AHgAfAA8ADwAPwA/AA"),
        new Numeral(
                8, 0.6666667f, "8A/wD/g/fHg8eDx4P3g8eHx4fHj4P/AP+D/4P3x/P/g/8D/wD/A/8D/4P/j8f/g/"),
        new Numeral(
                9, 0.6666667f, "8A/wD/g/fHw8eDx4P3g/+D/4P/g/+D/4/P/8//j/wPgAeAB4AHgMfAw/DD/8D/gH"),
    };

    private record Body(int left, int top, int right, int bottom, int pixels) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
    }

    static int aboveBass(
            byte[] gray, int width, int height, int left, int right, float staffTop, float gap) {
        if (gray == null || gray.length != (long) width * height || !Float.isFinite(gap) || gap < 6)
            return 0;
        List<Body> bodies = components(gray, width, height, left, right, staffTop, gap);
        for (Body a : bodies)
            for (Body b : bodies) {
                int ah = a.height(), bh = b.height(), space = b.left - a.right;
                if (space < 1
                        || space > gap * .35f
                        || Math.abs(a.top - b.top) > gap * .15f
                        || Math.abs(a.bottom - b.bottom) > gap * .15f
                        || Math.abs((a.left + b.right) * .5f - (left + right) * .5f) > gap * .35f)
                    continue;
                if (staffTop - Math.max(a.bottom, b.bottom) > gap * .55f) continue;
                if (digit(gray, width, a) == 1
                        && digit(gray, width, b) == 5
                        && isolatedPrefix(gray, width, height, a, b, gap)) return 2;
            }
        int[] eight = BassOctaveDigit.boundsAbove(gray, width, height, left, right, staffTop, gap);
        if (eight == null) return 0;
        int ew = eight[2], eh = eight[3];
        for (Body b : bodies) {
            int space =
                    b.right <= eight[0]
                            ? eight[0] - b.right
                            : b.left >= eight[0] + ew ? b.left - eight[0] - ew : -1;
            if (space >= 1
                    && space <= eh * .5f
                    && b.height() >= eh * .65f
                    && b.height() <= eh * 1.3f
                    && Math.abs(b.top - eight[1]) <= Math.max(2, eh * .25f)
                    && Math.abs(b.bottom - (eight[1] + eh)) <= Math.max(2, eh * .25f)) return 0;
        }
        return 1;
    }

    private static List<Body> components(
            byte[] gray, int width, int height, int left, int right, float staffTop, float gap) {
        var result = new ArrayList<Body>();
        int x0 = Math.max(0, Math.round(left - gap * .6f)),
                x1 = Math.min(width - 1, Math.round(right + gap * .6f));
        int y0 = Math.max(0, Math.round(staffTop - gap * 1.9f)),
                y1 = Math.min(height - 1, Math.round(staffTop - gap * .12f));
        int rw = x1 - x0 + 1, rh = y1 - y0 + 1;
        if (rw < 1 || rh < 1) return result;
        boolean[] seen = new boolean[rw * rh];
        int[] queue = new int[seen.length];
        for (int origin = 0; origin < seen.length; origin++) {
            if (seen[origin] || (gray[(y0 + origin / rw) * width + x0 + origin % rw] & 255) > 175)
                continue;
            int count = 1, at = 0, l = rw, r = -1, t = rh, b = -1;
            boolean clipped = false;
            seen[origin] = true;
            queue[0] = origin;
            while (at < count) {
                int p = queue[at++], x = p % rw, y = p / rw;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                clipped |= x == 0 || x == rw - 1 || y == 0 || y == rh - 1;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= rw || yy < 0 || yy >= rh) continue;
                        int n = yy * rw + xx;
                        if (!seen[n] && (gray[(y0 + yy) * width + x0 + xx] & 255) <= 175) {
                            seen[n] = true;
                            queue[count++] = n;
                        }
                    }
            }
            int cw = r - l + 1, ch = b - t + 1;
            if (clipped
                    || ch < gap * .6f
                    || ch > gap * 1.5f
                    || cw < gap * .18f
                    || cw > gap * 1.1f
                    || count < cw + ch
                    || Math.abs(x0 + (l + r) * .5f - (left + right) * .5f) > gap * 1.2f) continue;
            result.add(new Body(x0 + l, y0 + t, x0 + r + 1, y0 + b + 1, count));
        }
        return result;
    }

    private static int digit(byte[] gray, int width, Body body) {
        boolean[] mask = new boolean[384];
        for (int y = 0; y < 24; y++)
            for (int x = 0; x < 16; x++)
                mask[y * 16 + x] =
                        (gray[
                                                (body.top
                                                                        + Math.min(
                                                                                body.height() - 1,
                                                                                y
                                                                                        * body
                                                                                                .height()
                                                                                        / 24))
                                                                * width
                                                        + body.left
                                                        + Math.min(
                                                                body.width() - 1,
                                                                x * body.width() / 16)]
                                        & 255)
                                <= 175;
        float[] scores = new float[10];
        float aspect = body.width() / (float) body.height();
        for (Numeral template : NUMERALS) {
            float ratio = Math.abs((float) Math.log(aspect / template.aspect));
            if (ratio > .45f) continue;
            int intersection = 0, union = 0;
            for (int k = 0; k < 384; k++) {
                boolean ink = (template.bits[k / 8] & (1 << (k % 8))) != 0;
                if (ink && mask[k]) intersection++;
                if (ink || mask[k]) union++;
            }
            float score = intersection / (float) Math.max(1, union) - ratio * .15f;
            scores[template.digit] = Math.max(scores[template.digit], score);
        }
        int best = 0;
        for (int i = 1; i < 10; i++) if (scores[i] > scores[best]) best = i;
        float runner = 0;
        for (int i = 0; i < 10; i++) if (i != best) runner = Math.max(runner, scores[i]);
        return scores[best] >= .56f && scores[best] - runner >= .07f ? best : -1;
    }

    private static boolean isolatedPrefix(
            byte[] gray, int width, int height, Body a, Body b, float gap) {
        int halo = Math.max(2, Math.round(gap * .35f)),
                vertical = Math.max(1, Math.round(gap * .12f));
        int l = a.left - halo,
                r = b.right + halo,
                t = Math.min(a.top, b.top) - vertical,
                foot = Math.max(a.bottom, b.bottom) + vertical;
        if (l < 1 || r >= width - 1 || t < 1 || foot >= height - 1) return false;
        for (int y = t; y < foot; y++)
            for (int x = l; x < r; x++)
                if ((gray[y * width + x] & 255) <= 175
                        && !(x >= a.left && x < a.right && y >= a.top && y < a.bottom)
                        && !(x >= b.left && x < b.right && y >= b.top && y < b.bottom))
                    return false;
        return true;
    }

    /** Every triggering neighbor pixel must belong to a complete numeral across a wide white corridor. */
    static boolean separateNeighbor(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int w,
            int h,
            int oldLeft,
            int oldRight) {
        int x0 = Math.max(0, left - h * 3), x1 = Math.min(width - 1, left + w + h * 3);
        int y0 = Math.max(0, top - h), y1 = Math.min(height - 1, top + h * 2);
        int rw = x1 - x0 + 1, rh = y1 - y0 + 1;
        boolean[] seen = new boolean[rw * rh], covered = new boolean[rw * rh];
        int[] queue = new int[seen.length];
        for (int origin = 0; origin < seen.length; origin++) {
            if (seen[origin] || (gray[(y0 + origin / rw) * width + x0 + origin % rw] & 255) > 175)
                continue;
            int count = 1, at = 0, l = rw, r = -1, t = rh, b = -1;
            boolean clipped = false;
            queue[0] = origin;
            seen[origin] = true;
            while (at < count) {
                int p = queue[at++], x = p % rw, y = p / rw;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                clipped |= x == 0 || x == rw - 1 || y == 0 || y == rh - 1;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= rw || yy < 0 || yy >= rh) continue;
                        int next = yy * rw + xx;
                        if (!seen[next] && (gray[(y0 + yy) * width + x0 + xx] & 255) <= 175) {
                            seen[next] = true;
                            queue[count++] = next;
                        }
                    }
            }
            int gl = x0 + l,
                    gr = x0 + r + 1,
                    gt = y0 + t,
                    gb = y0 + b + 1,
                    cw = gr - gl,
                    ch = gb - gt;
            int corridorLeft = gr <= left ? gr : left + w, corridorRight = gr <= left ? left : gl;
            if (clipped
                    || !(gr <= left || gl >= left + w)
                    || corridorRight - corridorLeft < h * .6f
                    || ch < h * .65f
                    || ch > h * 1.4f
                    || cw < h * .2f
                    || cw > h * 1.3f
                    || Math.abs(gt - top) > Math.max(2, h * .25f)
                    || Math.abs(gb - top - h) > Math.max(2, h * .25f)
                    || count < cw + ch
                    || count > cw * ch * .75f) continue;
            boolean clear = true;
            for (int y = top; y < top + h; y++)
                for (int x = corridorLeft; x < corridorRight; x++)
                    if ((gray[y * width + x] & 255) <= 175) clear = false;
            if (!clear) continue;
            for (int i = 0; i < count; i++) covered[queue[i]] = true;
        }
        int ink = 0;
        for (int y = top; y < Math.min(height, top + h); y++)
            for (int x = oldLeft; x <= oldRight; x++)
                if ((gray[y * width + x] & 255) < 175) {
                    ink++;
                    if (x < x0 || x > x1 || y < y0 || y > y1 || !covered[(y - y0) * rw + x - x0])
                        return false;
                }
        return ink > 0;
    }
}
