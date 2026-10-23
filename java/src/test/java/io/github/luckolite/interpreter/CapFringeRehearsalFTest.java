// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original cap teeth on existing licensed-font source-free whole glyph fixtures. */
public class CapFringeRehearsalFTest {
    static BoxedRehearsalFFixture scene(String name, boolean top, boolean bottom) throws Exception {
        var s = new BoxedRehearsalFFixture(name);
        if (top) {
            ink(s, 100, 90, 102, 90);
            ink(s, 114, 90, 117, 90);
        }
        if (bottom) {
            ink(s, 100, 123, 103, 123);
            ink(s, 113, 123, 116, 123);
        }
        return s;
    }

    static void ink(BoxedRehearsalFFixture s, int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) s.gray[y * 400 + x] = 45;
    }

    static void paper(BoxedRehearsalFFixture s, int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) s.gray[y * 400 + x] = (byte) 255;
    }

    static void rehearsal(BoxedRehearsalFFixture s) {
        for (String token : new String[] {"F", "f"}) assertTrue(s.detect(token).isEmpty());
    }

    static void keep(BoxedRehearsalFFixture s) {
        var d = s.detect("f");
        assertEquals(1, d.size());
        assertEquals(3, d.get(0).decibels(), 0);
    }

    @Test
    public void completeTopCapOwnsItsFiniteMultiRunFringe() throws Exception {
        rehearsal(scene("boxed-capital", true, false));
    }

    @Test
    public void completeBottomCapOwnsItsFiniteMultiRunFringe() throws Exception {
        rehearsal(scene("boxed-capital", false, true));
    }

    @Test
    public void bothCompleteCapsRetainAllTheirFringePixels() throws Exception {
        rehearsal(scene("boxed-capital", true, true));
    }

    @Test
    public void attachedForeignCapLetterIsNotAFringe() throws Exception {
        var s = scene("boxed-capital", true, true);
        ink(s, 107, 90, 107, 97);
        ink(s, 105, 94, 110, 95);
        keep(s);
    }

    @Test
    public void attachedForeignCapDotHasIndependentDepth() throws Exception {
        var s = scene("boxed-capital", true, true);
        for (int y = 89; y <= 94; y++)
            for (int x = 105; x <= 111; x++)
                if ((x - 108) * (x - 108) + (y - 91) * (y - 91) <= 9) s.gray[y * 400 + x] = 45;
        keep(s);
    }

    @Test
    public void connectorFromCapToWholeGlyphPreventsSeparation() throws Exception {
        var s = scene("boxed-capital", true, true);
        ink(s, 104, 89, 105, 102);
        keep(s);
    }

    @Test
    public void anOpenSideCannotBeCompletedFromTheCaps() throws Exception {
        var s = scene("boxed-capital", true, true);
        paper(s, 93, 104, 96, 108);
        keep(s);
    }

    @Test
    public void doubleHoleIsNotOneRehearsalFrame() throws Exception {
        var s = scene("boxed-capital", true, true);
        ink(s, 109, 89, 110, 124);
        keep(s);
    }

    @Test
    public void exteriorSideBranchRemainsUnknownInk() throws Exception {
        var s = scene("boxed-capital", true, true);
        ink(s, 89, 105, 94, 106);
        keep(s);
    }

    @Test
    public void completeEditorialCapCannotEraseGenuineItalicF() throws Exception {
        keep(scene("boxed-italic-f", true, false));
    }

    @Test
    public void independentInteriorDotIsNotPartOfTheFrameOrF() throws Exception {
        var s = scene("boxed-capital", true, true);
        ink(s, 117, 116, 118, 117);
        keep(s);
    }

    @Test
    public void identicalObservedFringesCannotAcquireDifferentRoles() throws Exception {
        var a = scene("boxed-capital", true, true);
        var b = scene("boxed-capital", true, true);
        assertArrayEquals(a.gray, b.gray);
        assertEquals(a.detect("f"), b.detect("f"));
    }
}
