// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent role countercases reuse the frozen original raster, never production predicates. */
public class PhraseComponentOwnershipOriginalTest {
    private byte[] page() throws Exception {
        var m =
                SmallPhraseApproachOriginalTest.class.getDeclaredMethod(
                        "page", boolean.class, boolean.class);
        m.setAccessible(true);
        return (byte[]) m.invoke(new SmallPhraseApproachOriginalTest(), true, true);
    }

    private ArrayList<NoteSlideDetector.Head> heads() {
        return new ArrayList<>(
                List.of(
                        new NoteSlideDetector.Head(60, 100, 12, 0, 0),
                        new NoteSlideDetector.Head(88, 140, 12, 0, 0),
                        new NoteSlideDetector.Head(114, 100, 12, 0, 0)));
    }

    private int[] component() {
        var ps = new ArrayList<Integer>();
        for (int x = 64; x <= 84; x++) {
            double t = (x - 64) / 20.0;
            int y = (int) Math.round(88 + 14 * t * t);
            ps.add(y * 240 + x);
            ps.add((y + 1) * 240 + x);
        }
        return ps.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean owned(byte[] p, List<NoteSlideDetector.Head> hs, int[] ps) {
        return PhraseOwnedSlideComponents.owned(
                p,
                240,
                200,
                List.of(new NoteSlideDetector.Staff(115, 163, 12)),
                hs,
                2,
                ps,
                ps.length);
    }

    @Test
    public void completePixelIdentityCannotBeReplacedByPartialArc() throws Exception {
        var p = page();
        var ps = component();
        assertTrue(owned(p, heads(), ps));
        assertFalse(owned(p, heads(), Arrays.copyOf(ps, ps.length / 2)));
    }

    @Test
    public void twoEqualPhysicalEndpointCandidatesRemainAmbiguous() throws Exception {
        var hs = heads();
        hs.add(new NoteSlideDetector.Head(88, 140, 12, 0, 0));
        assertFalse(owned(page(), hs, component()));
    }

    @Test
    public void precedingOtherMeasureDoesNotOwnCurrentApproach() throws Exception {
        var hs = heads();
        hs.set(0, new NoteSlideDetector.Head(60, 100, 12, 0, 1));
        hs.set(1, new NoteSlideDetector.Head(88, 140, 12, 0, 1));
        assertFalse(owned(page(), hs, component()));
    }

    @Test
    public void visibleConnectedBranchCannotBeIgnoredAsTheOriginalArc() throws Exception {
        var p = page();
        for (int y = 92; y <= 105; y++) p[y * 240 + 74] = 0;
        assertFalse(owned(p, heads(), component()));
    }

    @Test
    public void typedEndpointsWithoutPrintedHeadBodiesCannotOwnPhrase() throws Exception {
        var m =
                SmallPhraseApproachOriginalTest.class.getDeclaredMethod(
                        "page", boolean.class, boolean.class);
        m.setAccessible(true);
        byte[] p = (byte[]) m.invoke(new SmallPhraseApproachOriginalTest(), true, false);
        assertFalse(owned(p, heads(), component()));
    }

    @Test
    public void competingPhysicalStaffEndpointCannotBeHidden() throws Exception {
        var p = page();
        for (int x = 8; x < 232; x++) for (int j = 0; j < 5; j++) p[(40 + 12 * j) * 240 + x] = 0;
        var hs = heads();
        hs.add(new NoteSlideDetector.Head(88, 140, 12, 1, 0));
        var ps = component();
        assertFalse(
                PhraseOwnedSlideComponents.owned(
                        p,
                        240,
                        200,
                        List.of(
                                new NoteSlideDetector.Staff(115, 163, 12),
                                new NoteSlideDetector.Staff(40, 88, 12)),
                        hs,
                        2,
                        ps,
                        ps.length));
    }
}
