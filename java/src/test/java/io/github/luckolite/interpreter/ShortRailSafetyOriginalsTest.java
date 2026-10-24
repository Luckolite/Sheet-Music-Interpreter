// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Complete producer refusals and exact-fragment reuse contracts. */
public class ShortRailSafetyOriginalsTest {
    static ShortRailProducerOriginalsTest.Scene scene() {
        return new ShortRailProducerOriginalsTest.Scene(0, -40, 16);
    }

    static PrintedSlurContours.Result budget(ShortRailProducerOriginalsTest.Scene f, long allowance)
            throws Exception {
        Method core =
                Arrays.stream(PrintedSlurContours.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("findRaw"))
                        .findFirst()
                        .orElseThrow();
        core.setAccessible(true);
        return (PrintedSlurContours.Result)
                core.invoke(
                        null, f.gray, null, f.w, f.h, 8, 100, f.w - 9, 280, f.track, null,
                        f.hints(), null, allowance);
    }

    static boolean extension(
            PrintedSlurContours.Bow whole,
            List<PrintedSlurContours.Bow> old,
            PrintedSlurContours.HeadEvidence heads,
            long[] budget)
            throws Exception {
        Method m =
                PrintedSlurContours.class.getDeclaredMethod(
                        "ownedExtension",
                        PrintedSlurContours.Bow.class,
                        List.class,
                        PrintedSlurContours.HeadEvidence.class,
                        long[].class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, whole, old, heads, budget);
    }

    static PrintedSlurContours.Bow whole(ShortRailProducerOriginalsTest.Scene f) {
        return f.find().shortRailBows().iterator().next();
    }

    static PrintedSlurContours.Bow fragment(PrintedSlurContours.Bow b) {
        return new PrintedSlurContours.Bow(
                b.points().subList(b.points().size() / 2, b.points().size()),
                b.above(),
                b.bendInGaps(),
                b.rmsInGaps());
    }

    @Test
    public void zeroBudgetKeepsOldBows() throws Exception {
        var f = scene();
        var zero = budget(f, 0);
        var full = f.find();
        assertTrue(zero.shortRailBows().isEmpty());
        assertTrue(full.bows().containsAll(zero.bows()));
        assertFalse(full.shortRailBows().isEmpty());
    }

    @Test
    public void allBudgetPrefixesKeepOldBows() throws Exception {
        var f = scene();
        var zero = budget(f, 0);
        for (long b : new long[] {1, 127, 2048, 10000, 100000, 500000, 1000000, 4000000, 8000000})
            assertTrue(budget(f, b).bows().containsAll(zero.bows()));
    }

    @Test
    public void noHeadsCannotAcquireShortRoute() {
        var f = scene();
        var r =
                PrintedSlurContours.find(
                        f.gray, null, f.w, f.h, 8, 100, f.w - 9, 280, f.track, null, null);
        assertTrue(r.shortRailBows().isEmpty());
    }

    @Test
    public void duplicateEndpointOwnerIsAmbiguous() {
        var f = scene();
        var h = f.heads.get(0);
        f.heads.add(new PrintedSlurOwners.GeometryHead(9, h.x(), h.y(), 0, 1));
        assertTrue(f.find().shortRailBows().isEmpty());
    }

    @Test
    public void absentRawHeadBodyCannotAcquireOwner() {
        var f = scene();
        var h = f.heads.get(0);
        for (int y = (int) h.y() - 7; y <= h.y() + 7; y++)
            for (int x = (int) h.x() - 10; x <= h.x() + 10; x++) f.gray[y * f.w + x] = (byte) 255;
        assertTrue(f.find().shortRailBows().isEmpty());
    }

    @Test
    public void independentMarkMetadataCannotShiftCurve() {
        var f = scene();
        var before = whole(f);
        f.notes.clear();
        assertEquals(before.points(), whole(f).points());
    }

    @Test
    public void separateClosedCounterCannotDonateCurvePixels() {
        var f = scene();
        var before = whole(f);
        for (int y = 133; y <= 154; y++) f.gray[y * f.w + 203] = 0;
        assertEquals(before.points(), whole(f).points());
    }

    @Test
    public void tornCurveCannotBeCompletedByRail() {
        var f = scene();
        for (int x = 125; x <= 135; x++)
            for (int y = 145; y <= 170; y++) f.gray[y * f.w + x] = (byte) 255;
        assertTrue(f.find().shortRailBows().isEmpty());
    }

    @Test
    public void exactStrictUnownedFragmentCanBeReused() throws Exception {
        var f = scene();
        var w = whole(f);
        assertTrue(extension(w, List.of(fragment(w)), f.hints(), new long[] {8000000}));
    }

    @Test
    public void completeOldOwnedBowCannotBeReused() throws Exception {
        var f = scene();
        var w = whole(f);
        assertFalse(extension(w, List.of(w), f.hints(), new long[] {8000000}));
    }

    @Test
    public void inexactNearFragmentIsStillRival() throws Exception {
        var f = scene();
        var w = whole(f);
        var p = new ArrayList<>(fragment(w).points());
        var a = p.get(0);
        p.set(0, new PrintedSlurContours.Point(a.x(), a.y() + .25f));
        assertFalse(
                extension(
                        w,
                        List.of(new PrintedSlurContours.Bow(p, w.above(), 0, 0)),
                        f.hints(),
                        new long[] {8000000}));
    }

    @Test
    public void foreignTailCannotBeDiscarded() throws Exception {
        var f = scene();
        var w = whole(f);
        var p = new ArrayList<>(fragment(w).points());
        p.add(new PrintedSlurContours.Point(w.end().x() + 1, w.end().y()));
        assertFalse(
                extension(
                        w,
                        List.of(new PrintedSlurContours.Bow(p, w.above(), 0, 0)),
                        f.hints(),
                        new long[] {8000000}));
    }

    @Test
    public void contrarySideCannotBeReused() throws Exception {
        var f = scene();
        var w = whole(f);
        var p = fragment(w);
        assertFalse(
                extension(
                        w,
                        List.of(new PrintedSlurContours.Bow(p.points(), !w.above(), 0, 0)),
                        f.hints(),
                        new long[] {8000000}));
    }

    @Test
    public void allCompetingOldBowsMustClose() throws Exception {
        var f = scene();
        var w = whole(f);
        var p = new ArrayList<>(fragment(w).points());
        var a = p.get(0);
        p.set(0, new PrintedSlurContours.Point(a.x(), a.y() + .25f));
        assertFalse(
                extension(
                        w,
                        List.of(fragment(w), new PrintedSlurContours.Bow(p, w.above(), 0, 0)),
                        f.hints(),
                        new long[] {8000000}));
    }

    @Test
    public void exhaustedExtensionBudgetIsSticky() throws Exception {
        var f = scene();
        var w = whole(f);
        long[] b = {0};
        assertFalse(extension(w, List.of(fragment(w)), f.hints(), b));
        assertTrue(b[0] < 0);
        assertFalse(extension(w, List.of(fragment(w)), f.hints(), b));
    }

    @Test
    public void rawDiagnosticAndProductionBowsAgree() {
        var f = scene();
        var r = f.find();
        var d =
                PrintedSlurContours.findWithRawEvidence(
                        f.gray, null, f.w, f.h, 8, 100, f.w - 9, 280, f.track, null, f.hints());
        assertEquals(r.bows(), d.result().bows());
        assertEquals(r.shortRailBows().size(), d.result().shortRailBows().size());
        for (var b : d.result().shortRailBows()) assertFalse(d.witnesses().get(b).isEmpty());
    }
}
