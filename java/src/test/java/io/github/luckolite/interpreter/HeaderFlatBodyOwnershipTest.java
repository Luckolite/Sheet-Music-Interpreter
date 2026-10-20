// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** A complete accidental label can own a NOTEHEAD-painted lower tip; stage-only ownership controls. */
public final class HeaderFlatBodyOwnershipTest {
    static HeaderFlatHeadTest.Page page(boolean clef, int offset) {
        var p = new HeaderFlatHeadTest.Page(clef);
        p.flat(offset, true);
        for (int y = 115; y <= 154; y++)
            for (int x = 80 + offset; x <= 100 + offset; x++) {
                int k = y * HeaderFlatHeadTest.W + x;
                if (p.labels[k] == 2 || p.labels[k] == 5) p.labels[k] = 3;
            }
        for (int y = 149; y <= 154; y++)
            for (int x = 82 + offset; x <= 91 + offset; x++) {
                int k = y * HeaderFlatHeadTest.W + x;
                if (p.labels[k] == 3 && (p.gray[k] & 255) < 180) p.labels[k] = 2;
            }
        return p;
    }

    static Object call(String name, Object... args) throws Exception {
        for (var m : OmrScoreInterpreter.class.getDeclaredMethods())
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                return m.invoke(null, args);
            }
        throw new AssertionError(name);
    }

    static boolean owner(HeaderFlatHeadTest.Page p, int x) throws Exception {
        int w = HeaderFlatHeadTest.W, h = HeaderFlatHeadTest.H;
        var heads = (List<?>) call("findComponents", p.labels, w, h, (byte) 2);
        Object target = null;
        for (Object head : heads) {
            Field f = head.getClass().getDeclaredField("centerX");
            f.setAccessible(true);
            if (Math.abs(((Number) f.get(head)).floatValue() - x) < 10) {
                assertNull("unique actual CC", target);
                target = head;
            }
        }
        assertNotNull("tip must actually be admitted to head list", target);
        var staffs = call("findStaffs", p.labels, p.gray, w, h, HeaderFlatHeadTest.M);
        var glyphs = call("findComponents", p.labels, w, h, (byte) 3);
        return (boolean) call("isHeaderFlatHead", p.labels, p.gray, w, h, target, staffs, glyphs);
    }

    @Test
    public void completeUpperFlatBodyOwnsOnlyPaintedLowerTip() throws Exception {
        var p = page(true, 0);
        assertTrue(owner(p, 85));
    }

    @Test
    public void noClefCannotGiveBodyOwnership() throws Exception {
        assertFalse(owner(page(false, 0), 85));
    }

    @Test
    public void distantAccidentalCannotGiveBodyOwnership() throws Exception {
        assertFalse(owner(page(true, 150), 235));
    }

    @Test
    public void missingRawSpineCannotGiveBodyOwnership() throws Exception {
        var p = page(true, 0);
        for (int y = 115; y <= 133; y++)
            for (int x = 82; x <= 84; x++) {
                p.gray[y * HeaderFlatHeadTest.W + x] = (byte) 255;
                p.labels[y * HeaderFlatHeadTest.W + x] = 0;
            }
        assertFalse(owner(p, 85));
    }

    @Test
    public void naturalSecondSpineCannotGiveFlatOwnership() throws Exception {
        var p = page(true, 0);
        for (int y = 115; y <= 145; y++) for (int x = 95; x <= 97; x++) p.pixel(x, y, 3);
        assertFalse(owner(p, 85));
    }

    @Test
    public void rawAndLabelArraysPreserved() throws Exception {
        var p = page(true, 0);
        var l = p.labels.clone();
        var g = p.gray.clone();
        owner(p, 85);
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }
}
