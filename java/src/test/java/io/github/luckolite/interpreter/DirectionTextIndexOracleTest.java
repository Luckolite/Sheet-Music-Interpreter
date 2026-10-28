// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;

/** Semantic oracle for period/dash indexing: the unmodified recognition126 grammar is retained below. */
public final class DirectionTextIndexOracleTest {
    static final int W = 2048, H = 512;
    final byte[] labels = new byte[W * H];

    record Sample(int left, int top, int right, int bottom, int count, int[] pixels) {
        float x() {
            return (left + right) * .5f;
        }

        float y() {
            return (top + bottom) * .5f;
        }
    }

    record Case(Sample target, List<Sample> context) {}

    static Sample rect(int l, int t, int r, int b) {
        int[] p = new int[(r - l + 1) * (b - t + 1)];
        int n = 0;
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) p[n++] = y * W + x;
        return new Sample(l, t, r, b, n, p);
    }

    static Sample mirror(Sample a) {
        return new Sample(-a.right, a.top, -a.left, a.bottom, a.count, a.pixels);
    }

    static Sample shift(Sample a, int x) {
        return new Sample(a.left + x, a.top, a.right + x, a.bottom, a.count, a.pixels);
    }

    static Case transformed(Case a, boolean reflect, int shift) {
        var identity = new IdentityHashMap<Sample, Sample>();
        java.util.function.Function<Sample, Sample> f =
                s -> identity.computeIfAbsent(s, z -> reflect ? mirror(z) : shift(z, shift));
        return new Case(f.apply(a.target), a.context.stream().map(f).toList());
    }

    static List<Sample> letters(int lastRight, int bottom, float gap) {
        int width = Math.max(2, Math.round(gap * .45f)),
                step = Math.max(width + 1, Math.round(gap * .8f)),
                height = Math.max(2, Math.round(gap));
        var out = new ArrayList<Sample>();
        for (int i = 2; i >= 0; i--) {
            int r = lastRight - i * step;
            out.add(rect(r - width + 1, bottom - height + 1, r, bottom));
        }
        return out;
    }

    boolean indexed(Case input, float gap) throws Exception {
        Class<?> g = Class.forName(NoteArticulationDetector.class.getName() + "$Glyph");
        var ctor =
                g.getDeclaredConstructor(
                        int.class, int.class, int.class, int.class, int.class, int[].class);
        ctor.setAccessible(true);
        var map = new IdentityHashMap<Sample, Object>();
        java.util.function.Function<Sample, Object> convert =
                s -> {
                    if (map.containsKey(s)) return map.get(s);
                    try {
                        Object v =
                                ctor.newInstance(
                                        s.left, s.top, s.right, s.bottom, s.count, s.pixels);
                        map.put(s, v);
                        return v;
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                };
        var target = convert.apply(input.target);
        var context = input.context.stream().map(convert).toList();
        var method =
                NoteArticulationDetector.class.getDeclaredMethod(
                        "directionText", g, List.class, float.class, byte[].class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, target, context, gap, labels);
    }

    void parity(Case c, float gap, String name) throws Exception {
        assertEquals(
                name + " gap=" + gap, legacy(c.target, c.context, gap, labels), indexed(c, gap));
    }

    static Case period(float gap, int periodDistance) {
        int end = 1000, bottom = 301;
        var list = letters(end, bottom, gap);
        int targetLeft = end + Math.max(3, Math.round(gap * 1.5f));
        var target = rect(targetLeft, 300, targetLeft + Math.max(2, Math.round(gap)), 302);
        int x = end + periodDistance;
        list.add(rect(x, bottom - 1, x + 1, bottom));
        return new Case(target, list);
    }

    static Case dash(float gap, int middleDistance) {
        int end = 800,
                height = Math.max(1, (int) Math.floor(gap * .2f)),
                bottom = Math.round(300 + (height - 1) * .5f);
        var list = letters(end, bottom, gap);
        int width = Math.max(2, Math.round(gap * .9f)), space = Math.max(2, Math.round(gap * .75f));
        int x = end + space;
        var first = rect(x, 300, x + width - 1, 300 + height - 1);
        list.add(first);
        x = first.right + middleDistance;
        var second = rect(x, 300, x + width - 1, 300 + height - 1);
        list.add(second);
        x = second.right + space;
        var target = rect(x, 300, x + width - 1, 300 + height - 1);
        list.add(target);
        return new Case(target, list);
    }

    @Test
    public void periodsDashesAndTargetMembershipMatchTheOriginal() throws Exception {
        for (float gap : new float[] {7, 10, 14, 14.25f, 20, 32}) {
            Case p = period(gap, Math.max(1, Math.round(gap * .2f)));
            assertTrue(legacy(p.target, p.context, gap, labels));
            parity(p, gap, "period");
            Sample dot = p.context.get(p.context.size() - 1);
            for (int pixel : dot.pixels) labels[pixel] = OmrMeasurePostProcessor.NOTEHEAD;
            parity(p, gap, "notation-labelled period remains eligible");
            Arrays.fill(labels, (byte) 0);
            Case d = dash(gap, Math.round(gap * 2));
            assertTrue(legacy(d.target, d.context, gap, labels));
            parity(d, gap, "target itself is third dash");
            for (var part : d.context.subList(3, d.context.size()))
                for (int pixel : part.pixels) labels[pixel] = OmrMeasurePostProcessor.STAFF;
            parity(d, gap, "notation-labelled dashes remain eligible");
            Arrays.fill(labels, (byte) 0);
            var without = new ArrayList<>(d.context);
            without.remove(d.target);
            parity(new Case(d.target, without), gap, "target absent");
        }
    }

    @Test
    public void equalLeftSortsRetainOriginalOrder() throws Exception {
        var letters = letters(991, 301, 14);
        Sample shortDash = rect(1000, 300, 1007, 302),
                longDash = rect(1000, 300, 1021, 302),
                next = rect(1071, 300, 1081, 302),
                target = rect(1100, 300, 1112, 302);
        var a = new ArrayList<>(letters);
        a.addAll(List.of(longDash, shortDash, next, target));
        var b = new ArrayList<>(letters);
        b.addAll(List.of(shortDash, longDash, next, target));
        assertFalse(legacy(target, a, 14, labels));
        assertTrue(legacy(target, b, 14, labels));
        parity(new Case(target, a), 14, "long before short");
        parity(new Case(target, b), 14, "short before long");
    }

    @Test
    public void strictFloatGapBoundariesAndReflectionRemainExact() throws Exception {
        int cases = 0;
        for (int delta : new int[] {2, 3, 4, 5, 6, 8, 10, 14, 20, 28, 42, 63, 90, 140}) {
            for (float coefficient :
                    new float[] {
                        .15f, .2f, .25f, .3f, .4f, .45f, .5f, .55f, .65f, 1.8f, 2.2f, 3f, 4.5f, 12f,
                        36f
                    }) {
                float middle = delta / coefficient;
                for (float gap : new float[] {Math.nextDown(middle), middle, Math.nextUp(middle)})
                    if (gap >= 3 && gap <= 64) {
                        Case p = period(gap, delta), d = dash(gap, delta);
                        parity(p, gap, "periodboundary");
                        parity(d, gap, "dashboundary");
                        parity(transformed(p, true, 0), gap, "reflectedperiod");
                        parity(transformed(d, true, 0), gap, "reflecteddash");
                        parity(transformed(d, false, -1500), gap, "negative-coordinate context");
                        cases += 5;
                    }
            }
        }
        assertTrue(cases > 1000);
    }

    @Test
    public void denseUnrelatedBodiesAndCopiedTargetValuesMatch() throws Exception {
        Random random = new Random(0x4D415243L);
        for (int run = 0; run < 100; run++) {
            float gap = new float[] {7, 14, 14.25f, 20, 32}[run % 5];
            Case base =
                    run % 2 == 0
                            ? period(gap, Math.round(gap * .2f))
                            : dash(gap, Math.round(gap * 2));
            var context = new ArrayList<>(base.context);
            for (int i = 0; i < 200; i++) {
                int l = 100 + random.nextInt(1500),
                        t = 30 + random.nextInt(400),
                        w = 1 + random.nextInt(35),
                        h = 1 + random.nextInt(35);
                context.add(rect(l, t, l + w - 1, t + h - 1));
            }
            Collections.shuffle(context, random);
            parity(new Case(base.target, context), gap, "dense" + run);
            Sample targetCopy =
                    new Sample(
                            base.target.left,
                            base.target.top,
                            base.target.right,
                            base.target.bottom,
                            base.target.count,
                            base.target.pixels.clone());
            parity(new Case(targetCopy, context), gap, "equal-valued distinct target" + run);
        }
    }

    private static boolean legacy(Sample target, List<Sample> glyphs, float gap, byte[] labels) {
        List<Sample> letters = new ArrayList<>();
        for (Sample g : glyphs) {
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
        letters.sort(java.util.Comparator.comparingInt(Sample::left));
        for (int i = 0; i + 2 < letters.size(); i++) {
            Sample first = letters.get(i), last = first;
            int count = 1;
            for (int j = i + 1; j < letters.size(); j++) {
                Sample next = letters.get(j);
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
                for (Sample period : glyphs) {
                    if (period.left > last.right
                            && period.right < target.left
                            && period.left - last.right < gap * .45f
                            && Math.abs(period.bottom - last.bottom) < gap * .3f
                            && period.right - period.left + 1 <= gap * .4f
                            && period.bottom - period.top + 1 <= gap * .4f) return true;
                }
            // Require a continuous run of thin dashes back to the word. Three
            // isolated tenutos elsewhere on the page do not constitute text.
            List<Sample> dashes = new ArrayList<>();
            for (Sample g : glyphs)
                if (g.left >= last.right
                        && g.left <= target.right + gap * 12
                        && Math.abs(g.y() - target.y()) <= gap * .15f
                        && g.bottom - g.top + 1 <= gap * .25f
                        && g.right - g.left + 1 >= gap * .55f
                        && g.right - g.left + 1 <= gap * 1.8f) dashes.add(g);
            dashes.sort(java.util.Comparator.comparingInt(Sample::left));
            float edge = last.right;
            int matched = 0;
            for (Sample dash : dashes) {
                if (dash.left - edge > gap * 4.5f) break;
                edge = dash.right;
                matched++;
            }
            if (matched >= 3 && edge >= target.right - gap * .2f) return true;
        }
        return false;
    }
}
