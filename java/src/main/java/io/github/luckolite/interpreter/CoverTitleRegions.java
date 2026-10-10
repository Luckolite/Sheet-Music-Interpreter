// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Finds large title-row crops directly from original cover pixels. */
public final class CoverTitleRegions {
    private CoverTitleRegions() {}

    public record Region(int left, int top, int right, int bottom, int components, int threshold) {}

    record Ink(int l, int t, int r, int b, int count) {
        int w() {
            return r - l;
        }

        int h() {
            return b - t;
        }

        float cy() {
            return (t + b) * .5f;
        }
    }

    public static List<Region> regions(int w, int fullH, int[] pixels, boolean satellites) {
        return regions(
                w,
                fullH,
                pixels,
                satellites,
                .62f,
                new int[] {-90, 140, 190, 220, 1001, 1002, 1003});
    }

    public static List<Region> photographicRegions(
            int w, int fullH, int[] pixels, boolean satellites) {
        return regions(
                w,
                fullH,
                pixels,
                satellites,
                1f,
                new int[] {-90, -140, -190, 140, 190, 220, 1001, 1002, 1003});
    }

    private static List<Region> regions(
            int w, int fullH, int[] pixels, boolean satellites, float fraction, int[] thresholds) {
        if (w < 32 || fullH < 32 || pixels == null || (long) w * fullH != pixels.length)
            return List.of();
        int h = Math.round(fullH * fraction);
        var result = new ArrayList<Region>();
        int[] gray = new int[w * h];
        for (int i = 0; i < gray.length; i++) {
            int p = pixels[i];
            gray[i] = (((p >> 16) & 255) * 299 + ((p >> 8) & 255) * 587 + (p & 255) * 114) / 1000;
        }
        for (int threshold : thresholds) {
            boolean[] seen = new boolean[gray.length];
            int[] queue = new int[gray.length];
            var parts = new ArrayList<Ink>();
            for (int at = 0; at < gray.length; at++) {
                if (seen[at] || !ink(gray[at], pixels[at], threshold)) continue;
                int front = 0, end = 1;
                queue[0] = at;
                seen[at] = true;
                int l = w, t = h, r = 0, b = 0, count = 0;
                while (front < end) {
                    int p = queue[front++], x = p % w, y = p / w;
                    l = Math.min(l, x);
                    r = Math.max(r, x + 1);
                    t = Math.min(t, y);
                    b = Math.max(b, y + 1);
                    count++;
                    for (int yy = Math.max(0, y - 1); yy <= Math.min(h - 1, y + 1); yy++)
                        for (int xx = Math.max(0, x - 1); xx <= Math.min(w - 1, x + 1); xx++) {
                            int n = yy * w + xx;
                            if (!seen[n] && ink(gray[n], pixels[n], threshold)) {
                                seen[n] = true;
                                queue[end++] = n;
                            }
                        }
                }
                Ink p = new Ink(l, t, r, b, count);
                if (p.h() >= w * .008f
                        && p.h() <= w * .25f
                        && p.w() >= p.h() * .08f
                        && p.w() <= w * .92f
                        && p.count >= (long) p.w() * p.h() * .015f) parts.add(p);
            }
            parts.sort(Comparator.comparingInt(Ink::h).reversed());
            var used = new HashSet<Ink>();
            for (Ink seed : parts) {
                boolean smallSeed = seed.h() < w * .025f;
                if ((!smallSeed && used.contains(seed)) || seed.h() < w * .012f) continue;
                var row = new ArrayList<Ink>();
                row.add(seed);
                int left = seed.l, right = seed.r, top = seed.t, bottom = seed.b;
                boolean grew;
                do {
                    grew = false;
                    for (Ink p : parts)
                        if (!row.contains(p)
                                && (smallSeed || !used.contains(p))
                                && p.h() >= seed.h() * (smallSeed ? .65f : .12f)
                                && p.h() <= seed.h() * (smallSeed ? 1.5f : 2.8f)
                                && (Math.abs(p.b - seed.b) < Math.min(p.h(), seed.h()) * .45f
                                        || (Math.min(p.b, seed.b) - Math.max(p.t, seed.t)
                                                        >= Math.min(p.h(), seed.h()) * .65f
                                                && Math.abs(p.cy() - seed.cy())
                                                        < Math.max(p.h(), seed.h()) * .65f))
                                && (!satellites
                                        || p.h() >= seed.h() * .45f
                                        || p.w() <= seed.h() * .25f
                                                && (p.t <= seed.t + seed.h() * .25f
                                                                && p.b <= seed.t + seed.h() * .65f
                                                        || p.t >= seed.t + seed.h() * .45f
                                                                && p.b >= seed.b - seed.h() * .25f))
                                && Math.max(p.l - right, left - p.r)
                                        < Math.max(p.h(), seed.h()) * .75f) {
                            row.add(p);
                            left = Math.min(left, p.l);
                            right = Math.max(right, p.r);
                            top = Math.min(top, p.t);
                            bottom = Math.max(bottom, p.b);
                            grew = true;
                        }
                } while (grew);
                if (!smallSeed) used.addAll(row);
                if (row.size() < 2 || right - left < w * .025f || bottom - top > w * .25f) continue;
                int pad = Math.max(3, Math.round((bottom - top) * .08f));
                result.add(
                        new Region(
                                Math.max(0, left - pad),
                                Math.max(0, top - pad),
                                Math.min(w, right + pad),
                                Math.min(fullH, bottom + pad),
                                row.size(),
                                threshold));
            }
        }
        var split = new LinkedHashSet<Region>();
        for (var large : result) {
            float hh = large.bottom() - large.top(), ww = large.right() - large.left();
            for (var upper : result)
                for (var lower : result) {
                    if (upper == lower
                            || upper.threshold() != large.threshold()
                            || lower.threshold() != large.threshold()
                            || upper.top() >= lower.top()
                            || lower.top() - upper.bottom() > hh * .25f
                            || upper.left() <= large.left()
                            || lower.left() <= large.left()
                            || upper.right() >= large.right()
                            || lower.right() >= large.right()
                            || upper.top() < large.top()
                            || lower.bottom() > large.bottom()
                            || upper.bottom() - upper.top() >= hh * .6f
                            || lower.bottom() - lower.top() >= hh * .6f
                            || upper.right() - upper.left() >= ww * .2f
                            || lower.right() - lower.left() >= ww * .2f
                            || Math.min(upper.right(), lower.right())
                                    <= Math.max(upper.left(), lower.left())) continue;
                    int ll = Math.min(upper.left(), lower.left()),
                            rr = Math.max(upper.right(), lower.right());
                    if (ll - large.left() < ww * .2f || large.right() - rr < ww * .2f) continue;
                    split.add(
                            new Region(
                                    large.left(),
                                    large.top(),
                                    ll,
                                    large.bottom(),
                                    large.components(),
                                    large.threshold()));
                    split.add(
                            new Region(
                                    rr,
                                    large.top(),
                                    large.right(),
                                    large.bottom(),
                                    large.components(),
                                    large.threshold()));
                }
        }
        var wordColumns = new LinkedHashSet<Region>();
        for (var a : result) {
            int ah = a.bottom() - a.top();
            if (ah < w * .06f || a.right() - a.left() < ah * 2f) continue;
            int start = -1, last = -1;
            for (int x = a.left(); x < a.right(); x++) {
                int count = 0;
                for (int y = a.top(); y < Math.min(h, a.bottom()); y++)
                    if (ink(gray[y * w + x], pixels[y * w + x], a.threshold())) count++;
                if (count < Math.max(2, ah * .04f)) continue;
                if (last >= 0 && x - last > ah * .12f) {
                    int pad = Math.max(3, Math.round(ah * .08f));
                    if (last - start > ah * .6f)
                        wordColumns.add(
                                new Region(
                                        Math.max(a.left(), start - pad),
                                        a.top(),
                                        Math.min(a.right(), last + 1 + pad),
                                        a.bottom(),
                                        a.components(),
                                        a.threshold()));
                    start = x;
                }
                if (start < 0) start = x;
                last = x;
            }
            if (start >= 0 && last - start > ah * .6f) {
                int pad = Math.max(3, Math.round(ah * .08f));
                wordColumns.add(
                        new Region(
                                Math.max(a.left(), start - pad),
                                a.top(),
                                Math.min(a.right(), last + 1 + pad),
                                a.bottom(),
                                a.components(),
                                a.threshold()));
            }
        }
        result.addAll(wordColumns);
        var overlapExtensions = new LinkedHashSet<Region>();
        for (var a : result)
            for (var b : result) {
                if (a == b || a.components() < 2 || b.components() < 2) continue;
                int ah = a.bottom() - a.top(), bh = b.bottom() - b.top(), bw = b.right() - b.left();
                if (ah < w * .08f
                        || bh < ah * .4f
                        || bh > ah
                        || b.top() < a.top()
                        || b.bottom() > a.bottom()) continue;
                int overlap = Math.min(a.right(), b.right()) - Math.max(a.left(), b.left());
                int extension = Math.max(a.left() - b.left(), b.right() - a.right());
                if (overlap < bw * .6f || extension <= 0 || extension > ah * .5f) continue;
                int margin = Math.max(3, Math.round(ah * .05f));
                int ll = b.left() < a.left() ? Math.max(0, b.left() - margin) : a.left();
                int rr = b.right() > a.right() ? Math.min(w, b.right() + margin) : a.right();
                overlapExtensions.add(
                        new Region(
                                ll,
                                a.top(),
                                rr,
                                a.bottom(),
                                a.components() + b.components(),
                                a.threshold()));
            }
        result.addAll(overlapExtensions);
        result.addAll(split);
        return List.copyOf(result);
    }

    private static boolean ink(int gray, int pixel, int threshold) {
        int r = (pixel >> 16) & 255, g = (pixel >> 8) & 255, b = pixel & 255;
        if (threshold == 1003) return gray >= 30 && r > g + 20 && r > b + 10;
        if (threshold == 1001) return gray >= 120 && r > b + 25 && g > b + 15;
        if (threshold == 1002) return gray >= 120 && b > r + 15 && g > r + 8;
        return threshold < 0 ? gray <= -threshold : gray >= threshold;
    }
}
