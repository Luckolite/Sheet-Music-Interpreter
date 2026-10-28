// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Conservative geometry-only prepayment. Full original contexts still reach original guards. */
final class DetachedCaretCosts {
    private DetachedCaretCosts() {}

    record Ink(int left, int top, int right, int bottom, int count) {
        long w() {
            return (long) right - left + 1;
        }

        long h() {
            return (long) bottom - top + 1;
        }
    }

    record Note(float x, float y, float gap, int staff) {}

    record Bound(long work, long bytes) {}

    static long add(long a, long b) {
        return a < 0 || b < 0 || a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    static long mul(long a, long b) {
        return a < 0 || b < 0 || a != 0 && b > Long.MAX_VALUE / a ? Long.MAX_VALUE : a * b;
    }

    static long sum(long a, long b) {
        return add(a, b);
    }

    static long sum(long a, long b, long c) {
        return add(sum(a, b), c);
    }

    static long sum(long a, long b, long c, long d) {
        return add(sum(a, b, c), d);
    }

    static long sum(long a, long b, long c, long d, long e) {
        return add(sum(a, b, c, d), e);
    }

    static long sum(long a, long b, long c, long d, long e, long f) {
        return add(sum(a, b, c, d, e), f);
    }

    static long sum(long a, long b, long c, long d, long e, long f, long g) {
        return add(sum(a, b, c, d, e, f), g);
    }

    static long sum(long a, long b, long c, long d, long e, long f, long g, long h) {
        return add(sum(a, b, c, d, e, f, g), h);
    }

    static Bound classify(Ink g) {
        long p = g.count, t = Math.max(g.w(), g.h()), a = mul(g.w(), g.h());
        return new Bound(sum(128, mul(48, p), mul(32, t), mul(4, a)), sum(256, mul(16, t)));
    }

    static Bound rounded(Ink g) {
        long p = g.count, t = Math.max(g.w(), g.h()), a = mul(g.w(), g.h());
        return new Bound(
                sum(128, mul(64, p), mul(448, t), mul(12, a), mul(4, mul(p, g.h()))),
                sum(1024, mul(160, t), mul(4, a), mul(8, p)));
    }

    static Bound crossbar(Ink g) {
        return new Bound(sum(32, mul(4, mul(g.count, g.h())), mul(8, Math.max(g.w(), g.h()))), 0);
    }

    private static long rectangle(Ink g, int width, int height) {
        long pad = Math.max(3, Math.max(g.w(), g.h()) / 2);
        return mul(
                Math.min(width - 1, g.right + pad) - Math.max(0, g.left - pad) + 1,
                Math.min(height - 1, g.bottom + pad) - Math.max(0, g.top - pad) + 1);
    }

    static Bound contrast(Ink g, int width, int height) {
        return new Bound(
                sum(
                        512,
                        mul(8, rectangle(g, width, height)),
                        mul(4, mul(g.count, g.count)),
                        mul(8, g.count)),
                sum(2048, mul(8, g.count)));
    }

    static void producer(DetachedCaretBodies.Page p, Bound b) {
        p.p(b.work, b.bytes);
    }

    static void admission(DetachedCaretBodies.Page p, Bound b) {
        p.a(b.work, b.bytes);
    }

    private static Bound numeral(Ink g) {
        long w = g.w(), h = g.h(), a = mul(w, h), triples = 0;
        int separation = Math.max(2, (int) h / 10);
        // Closed integer loop bound of detachedLeftLobes, independent of every ink outcome.
        for (int upper = Math.max(1, (int) h / 8); upper <= h * .45; upper++)
            for (int waist = upper + separation; waist <= h * .65; waist++) {
                long first = waist + separation, last = (long) Math.floor(h * .86);
                if (last >= first) triples = add(triples, last - first + 1);
            }
        return new Bound(
                sum(mul(16, a), mul(16, g.count), mul(64, h), mul(32 + 4 * h, triples), 256),
                sum(a, mul(32, h), 256));
    }

    private static void fundNumeral(DetachedCaretBodies.Page page, Ink g) {
        page.a(sum(128, mul(16, mul(g.h(), g.h()))), 128);
        admission(page, numeral(g));
    }

    private static void detachedThree(
            DetachedCaretBodies.Page page, Ink target, Ink[] context, float gap) {
        page.a(sum(128, mul(64, context.length)), 256);
        fundNumeral(page, target);
        for (Ink parent : context) {
            long pw = parent.w(), ph = parent.h();
            if (pw < gap * .35f
                    || pw > gap * 1.6f
                    || ph < gap * .7f
                    || ph > gap * 2.2f
                    || parent.left - gap * .5f > target.left
                    || parent.right < target.right
                    || parent.top - 1 > target.top
                    || parent.bottom + 1 < target.bottom) continue;
            page.a(mul(64, context.length), 0);
            long count = parent.count, parts = 1;
            int left = parent.left, top = parent.top, bottom = parent.bottom;
            for (Ink part : context) {
                if (part == parent
                        || part.left < parent.left - gap * .5f
                        || part.right > parent.right
                        || part.right > parent.left + pw * .55f
                        || part.top < parent.top - 1
                        || part.bottom > parent.bottom + 1
                        || part.w() > gap * .6f
                        || part.h() > ph * .5f) continue;
                parts++;
                count = add(count, part.count);
                left = Math.min(left, part.left);
                top = Math.min(top, part.top);
                bottom = Math.max(bottom, part.bottom);
            }
            if (count > Integer.MAX_VALUE) page.refuse();
            page.a(64, 128);
            Ink owned = new Ink(left, top, parent.right, bottom, (int) count);
            long binary = 64 - Long.numberOfLeadingZeros(Math.max(1, count));
            page.a(
                    sum(
                            mul(4, mul(count, count)),
                            mul(16, mul(target.count, binary + 1)),
                            mul(16, count),
                            mul(64, parts),
                            256),
                    sum(mul(16, count), mul(128, parts), 512));
            fundNumeral(page, owned);
        }
    }

    private static Ink reflect(Ink g) {
        return new Ink(-g.right, g.top, -g.left, g.bottom, g.count);
    }

    /** Preflight and original full-context scans are separately funded. */
    private static void words(DetachedCaretBodies.Page page, Ink target, Ink[] context, float gap) {
        long G = context.length;
        page.a(sum(128, mul(192, G)), 256);
        long E = 1, EL = 0, LL = 0, RL = 0, LP = 0, RP = 0, D = 0, RD = 0, embeddedPixels = 0;
        Ink mirror = reflect(target);
        float tx = (target.left + target.right) * .5f, ty = (target.top + target.bottom) * .5f;
        for (Ink g : context) {
            long w = g.w(), h = g.h();
            float x = (g.left + g.right) * .5f, y = (g.top + g.bottom) * .5f;
            if (Math.abs(x - tx) <= gap * 12
                    && Math.abs(g.bottom - target.bottom) <= gap * .45f
                    && h >= gap * .5f
                    && h <= gap * 2.2f
                    && w >= gap * .25f
                    && w <= gap * 3f) {
                E++;
                embeddedPixels = add(embeddedPixels, g.count);
                page.a(64, 128);
                Bound c = classify(g), r = rounded(g);
                page.a(add(c.work, r.work), add(c.bytes, r.bytes));
            }
            boolean letter =
                    h >= gap * .5f
                            && h <= gap * 2.2f
                            && w >= gap * .25f
                            && w <= gap * 3f
                            && g.top <= ty + gap * .15f
                            && g.bottom + 1 >= ty - gap * .15f;
            if (letter && g.right <= target.left + gap * .2f && target.left - g.right <= gap * 36) {
                LL++;
                LP = add(LP, g.count);
            }
            if (letter && -g.left <= mirror.left + gap * .2f && mirror.left + g.left <= gap * 36) {
                RL++;
                RP = add(RP, g.count);
            }
            boolean dash =
                    Math.abs(y - ty) <= gap * .15f
                            && h <= gap * .25f
                            && w >= gap * .55f
                            && w <= gap * 1.8f;
            if (dash && g.left <= target.right + gap * 12) D++;
            if (dash && -g.right <= mirror.right + gap * 12) RD++;
        }
        long directTail = mul(LL, sum(mul(2, G), mul(D, D), mul(2, D))),
                mirrorTail = mul(RL, sum(mul(2, G), mul(RD, RD), mul(2, RD)));
        long loops = sum(mul(E, E), mul(LL, LL), mul(RL, RL), directTail, mirrorTail);
        long sortObjects = sum(E, LL, RL, mul(LL, D), mul(RL, RD));
        page.a(
                sum(mul(16, loops), mul(8, sum(embeddedPixels, LP, RP)), 512),
                sum(mul(128, sortObjects), 512));
    }

    static void admissionGlyph(
            DetachedCaretBodies.Page page,
            Ink g,
            Ink[] context,
            Note[] notes,
            int width,
            int height) {
        long a = mul(g.w(), g.h()),
                p = g.count,
                t = Math.max(g.w(), g.h()),
                rect = rectangle(g, width, height);
        // Original printedCore runs once per glyph, even when no head ultimately owns it.
        page.a(sum(mul(4, rect), mul(32, a), mul(16, p), 1024), sum(mul(12, a), mul(8, p), 2048));
        page.a(mul(128, notes.length), 0);
        for (Note n : notes) {
            float dx = Math.abs((g.left + g.right) * .5f - n.x) / n.gap,
                    dy = Math.abs((g.top + g.bottom) * .5f - n.y) / n.gap;
            if (dx > .7f || dy < .7f || dy > 8f) continue;
            page.a(64, 192);
            Bound c = classify(g), r = rounded(g), x = crossbar(g);
            // Both common body and possible dark-core recovery, plus roundedTenuto/rasterCore.
            page.a(
                    sum(mul(2, add(c.work, r.work)), mul(3, x.work), mul(64, p), mul(128, t), 1024),
                    sum(mul(2, add(c.bytes, r.bytes)), mul(32, t), 1024));
            if ((g.top + g.bottom) * .5f >= n.y)
                continue; // Scope gate follows common recovery in caller.
            if (dy > 6)
                page.refuse(); // Entire-body preflight must have accounted for every far rival.
            detachedThree(page, g, context, n.gap);
            long pad = Math.max(3, Math.round(n.gap * 3)),
                    bw = Math.min(width - 1, g.right + pad) - Math.max(0, g.left - pad) + 1,
                    bh = Math.min(height - 1, g.bottom + pad) - Math.max(0, g.top - pad) + 1;
            long area = mul(bw, bh), margin = Math.max(4, Math.round(n.gap));
            long hist = mul(4 * margin + 1, bh + 2 * margin);
            page.a(
                    sum(mul(64, area), mul(4, hist), mul(256, p), 2048),
                    sum(mul(9, area), mul(256, p), 2048));
            page.a(64, 128);
            admission(page, contrast(g, width, height));
            words(page, g, context, n.gap);
        }
    }
}
