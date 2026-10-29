// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RetainedStackLobeOriginalTest {
    static class Scene {
        int w = 360, h = 260, gap = 16;
        byte[] gray = new byte[w * h], labels = new byte[w * h];

        Scene() {
            this(3, true);
        }

        Scene(int count, boolean shaft) {
            Arrays.fill(gray, (byte) 255);
            for (int y = 80; y <= 144; y += 16) for (int x = 20; x < 340; x++) gray[y * w + x] = 0;
            if (count >= 3) ellipse(180, 128, 8, 8);
            if (count >= 2) ellipse(180, 144, 8, 8);
            ellipse(178, 160, 10, 9);
            if (shaft) for (int y = 90; y <= 160; y++) gray[y * w + 188] = 0;
        }

        void ellipse(int cx, int cy, int rx, int ry) {
            for (int y = cy - ry; y <= cy + ry; y++)
                for (int x = cx - rx; x <= cx + rx; x++)
                    if (Math.pow((x - cx) / (double) rx, 2) + Math.pow((y - cy) / (double) ry, 2)
                            <= 1) {
                        labels[y * w + x] = 2;
                        gray[y * w + x] = 0;
                    }
        }

        Object head() throws Exception {
            int n = 0, l = w, r = 0, t = h, b = 0;
            long sx = 0, sy = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (labels[y * w + x] == 2) {
                        n++;
                        l = Math.min(l, x);
                        r = Math.max(r, x);
                        t = Math.min(t, y);
                        b = Math.max(b, y);
                        sx += x;
                        sy += y;
                    }
            var ctor =
                    Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                            .getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return ctor.newInstance(n, l, r, t, b, sx / (float) n, sy / (float) n);
        }

        Object staff() throws Exception {
            var ctor =
                    Class.forName(OmrScoreInterpreter.class.getName() + "$Staff")
                            .getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return ctor.newInstance(80f, 144f, 16f);
        }

        List<?> split(Object head, ExactComponentMembers.Collector c) throws Exception {
            var m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "splitStackedHeads",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            List.class,
                            List.class,
                            ExactComponentMembers.Collector.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(null, labels, gray, w, h, List.of(head), List.of(staff()), c);
        }

        List<?> split() throws Exception {
            var m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "splitStackedHeads",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            List.class,
                            List.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(null, labels, gray, w, h, List.of(head()), List.of(staff()));
        }
    }

    @Test
    public void retainedTallLobeKeepsAllThreeIndependentOvals() throws Exception {
        assertEquals(3, new Scene().split().size());
    }

    static ExactComponentMembers.Budget budget() {
        return new ExactComponentMembers.Budget(8000000, 8000000);
    }

    static int value(Object c, String name) throws Exception {
        var f = c.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(c);
    }

    static float coordinate(Object c, String name) throws Exception {
        var f = c.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getFloat(c);
    }

    static ExactComponentMembers.Collector capture(Scene s, Object parent) throws Exception {
        var c = new ExactComponentMembers.Collector(s.gray, s.labels, s.w, s.h, budget(), budget());
        assertTrue(c.bindInput(s.labels, s.gray, s.w, s.h));
        var builder = c.begin();
        for (int y = 0; y < s.h; y++)
            for (int x = 0; x < s.w; ) {
                if (s.labels[y * s.w + x] != 2) {
                    x++;
                    continue;
                }
                int first = x;
                while (x < s.w && s.labels[y * s.w + x] == 2) x++;
                builder.add(y, first, x - 1);
            }
        c.record(
                parent,
                new ExactComponentMembers.Stats(
                        value(parent, "area"),
                        value(parent, "minX"),
                        value(parent, "maxX"),
                        value(parent, "minY"),
                        value(parent, "maxY"),
                        coordinate(parent, "centerX"),
                        coordinate(parent, "centerY")),
                (byte) 2,
                builder);
        return c;
    }

    @Test
    public void anOrdinaryTwoHeadStackRemainsTwo() throws Exception {
        assertEquals(2, new Scene(2, true).split().size());
    }

    @Test
    public void aSingleHeadAndItsShaftStaySingle() throws Exception {
        assertEquals(1, new Scene(1, true).split().size());
    }

    @Test
    public void semanticLobesWithoutACommonPrintedShaftAreNotRefined() throws Exception {
        assertTrue(new Scene(3, false).split().size() < 3);
    }

    @Test
    public void absentPrintedOvalInkCannotCertifyRetainedRefinement() throws Exception {
        var s = new Scene();
        var parts = s.split();
        assertEquals(3, parts.size());
        Arrays.fill(s.gray, (byte) 255);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "provedRetainedHeadPair",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        float.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(null, s.gray, s.w, s.h, parts.subList(1, 3), (float) s.gap));
    }

    @Test
    public void sourcePlanesStayUnchanged() throws Exception {
        var s = new Scene();
        var g = s.gray.clone();
        var l = s.labels.clone();
        s.split();
        assertArrayEquals(g, s.gray);
        assertArrayEquals(l, s.labels);
    }

    @Test
    public void everyRefinedLeafKeepsAnExactDisjointOriginalPartition() throws Exception {
        var s = new Scene();
        Object parent = s.head();
        var c = capture(s, parent);
        var parts = s.split(parent, c);
        assertEquals(3, parts.size());
        assertTrue(c.seal(budget()));
        Set<Integer> all = new HashSet<>();
        for (Object part : parts) {
            var footprint = c.producerFootprint(part, budget());
            assertNotNull(footprint);
            assertSame(parent, footprint.originalIdentity());
            for (int pixel : footprint.pixels()) {
                assertTrue("Overlapping children", all.add(pixel));
                assertEquals(2, s.labels[pixel]);
            }
            var partition = c.producerPartition(part, budget());
            assertNotNull(partition);
            assertTrue(partition.residual().isEmpty());
            assertTrue(partition.authenticates(s.labels, s.gray, s.w, s.h, budget()));
        }
        assertEquals(value(parent, "area"), all.size());
        assertNull(c.producerFootprint(parent, budget()));
    }

    @Test
    public void exhaustedObserverCannotManufactureSourcePartitions() throws Exception {
        var s = new Scene();
        var c =
                new ExactComponentMembers.Collector(
                        s.gray,
                        s.labels,
                        s.w,
                        s.h,
                        new ExactComponentMembers.Budget(0, 0),
                        new ExactComponentMembers.Budget(0, 0));
        var parts = s.split(s.head(), c);
        assertFalse(c.seal(budget()));
        for (Object part : parts) assertNull(c.producerFootprint(part, budget()));
    }

    @Test
    public void mutationRefusesAPreviouslyCapturedPartition() throws Exception {
        var s = new Scene();
        Object parent = s.head();
        var c = capture(s, parent);
        var parts = s.split(parent, c);
        assertTrue(c.seal(budget()));
        s.gray[0] = 0;
        assertNull(c.producerPartition(parts.get(0), budget()));
    }

    @Test
    public void aLongShaftAndFlagDoNotBecomeThreeHeads() throws Exception {
        var s = new Scene();
        Arrays.fill(s.labels, (byte) 0);
        Arrays.fill(s.gray, (byte) 255);
        for (int y = 110; y <= 168; y++)
            for (int x = 178; x <= 181; x++) {
                s.labels[y * s.w + x] = 2;
                s.gray[y * s.w + x] = 0;
            }
        for (int y = 110; y <= 116; y++)
            for (int x = 178; x <= 190; x++) {
                s.labels[y * s.w + x] = 2;
                s.gray[y * s.w + x] = 0;
            }
        assertEquals(1, s.split().size());
    }

    @Test
    public void separatedStaffObjectsAreNotRefinedAsACompactChord() throws Exception {
        var s = new Scene();
        Arrays.fill(s.labels, (byte) 0);
        Arrays.fill(s.gray, (byte) 255);
        s.ellipse(180, 50, 8, 8);
        s.ellipse(180, 128, 8, 8);
        s.ellipse(180, 210, 8, 8);
        assertEquals(1, s.split().size());
    }

    @Test
    public void aDynamicHookCannotSupplyFilledOvalCores() throws Exception {
        var s = new Scene();
        var parts = s.split();
        Arrays.fill(s.gray, (byte) 255);
        for (int y = 90; y <= 168; y++) s.gray[y * s.w + 188] = 0;
        for (int y = 128; y <= 131; y++) for (int x = 175; x <= 196; x++) s.gray[y * s.w + x] = 0;
        for (int y = 144; y <= 146; y++) for (int x = 176; x <= 192; x++) s.gray[y * s.w + x] = 0;
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "provedRetainedHeadPair",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        float.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(null, s.gray, s.w, s.h, parts.subList(1, 3), (float) s.gap));
    }
}
