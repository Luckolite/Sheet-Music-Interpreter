// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;

/** Original geometric glyphs, rules and beams; no score pixels or font assets. */
public class TupletNumeralBeamBandTest {
    private static final int W = 260, H = 160;

    private static final class Page {
        final byte[] gray = new byte[W * H];

        Page() {
            Arrays.fill(gray, (byte) 255);
        }

        void rect(int left, int top, int right, int bottom) {
            for (int y = top; y <= bottom; y++)
                for (int x = left; x <= right; x++) gray[y * W + x] = 0;
        }

        void line(int x1, int y1, int x2, int y2, int radius) {
            int n = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
            for (int i = 0; i <= n; i++) {
                int x = Math.round(x1 + (x2 - x1) * i / (float) n);
                int y = Math.round(y1 + (y2 - y1) * i / (float) n);
                rect(x - radius, y - radius, x + radius, y + radius);
            }
        }

        void staff() {
            for (int y = 40; y <= 104; y += 16) rect(5, y, 254, y);
        }

        void beam() {
            rect(45, 50, 190, 56);
            for (int x : new int[] {45, 110, 190}) rect(x, 50, x + 2, 116);
        }

        void three(int dx, int dy) {
            int[][] points = {
                {103, 25}, {113, 25}, {118, 28}, {118, 30}, {113, 34}, {109, 34}, {115, 35},
                {118, 38}, {118, 40}, {113, 44}, {103, 44}
            };
            for (int i = 1; i < points.length; i++)
                line(
                        points[i - 1][0] + dx,
                        points[i - 1][1] + dy,
                        points[i][0] + dx,
                        points[i][1] + dy,
                        1);
        }

        boolean numeralBand() {
            return TupletNumeralBeamBand.matches(gray, W, H, 118, 38, 42, 34, 78, 40, 16, 165);
        }

        int bands() throws Exception {
            Class<?> staff = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
            Constructor<?> ctor =
                    staff.getDeclaredConstructor(float.class, float.class, float.class);
            ctor.setAccessible(true);
            Method method =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "thickNonHeadBands",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            staff);
            method.setAccessible(true);
            return (int)
                    method.invoke(
                            null,
                            gray,
                            new byte[gray.length],
                            W,
                            H,
                            118,
                            34,
                            78,
                            ctor.newInstance(40f, 104f, 16f));
        }
    }

    @Test
    public void touchingThreeIsNotASecondBeam() throws Exception {
        Page p = new Page();
        p.staff();
        p.beam();
        p.three(0, 0);
        assertTrue(p.numeralBand());
        assertEquals(1, p.bands());
    }

    @Test
    public void soleBeamWithoutNumeralSurvives() throws Exception {
        Page p = new Page();
        p.staff();
        p.beam();
        assertFalse(p.numeralBand());
        assertEquals(1, p.bands());
    }

    @Test
    public void genuineTwoBeamsSurvive() throws Exception {
        Page p = new Page();
        p.staff();
        p.beam();
        p.rect(45, 38, 190, 43);
        p.rect(110, 38, 112, 116);
        assertFalse(p.numeralBand());
        assertEquals(2, p.bands());
    }

    @Test
    public void shortSecondaryBeamSurvives() throws Exception {
        Page p = new Page();
        p.staff();
        p.beam();
        p.rect(110, 38, 124, 43);
        p.rect(110, 38, 112, 116);
        assertFalse(p.numeralBand());
        assertEquals(2, p.bands());
    }

    @Test
    public void numeralNearTrueSecondaryBeamDoesNotRemoveIt() throws Exception {
        Page p = new Page();
        p.staff();
        p.beam();
        p.three(-23, 0);
        p.rect(110, 38, 124, 43);
        p.rect(110, 38, 112, 116);
        assertFalse(p.numeralBand());
        assertEquals(2, p.bands());
    }

    @Test
    public void numeralJoinedToShortSecondaryBeamDoesNotRemoveIt() throws Exception {
        Page p = new Page();
        p.staff();
        p.beam();
        p.three(0, 0);
        p.rect(110, 38, 132, 43);
        p.rect(110, 38, 112, 116);
        assertFalse(p.numeralBand());
        assertEquals(2, p.bands());
    }

    @Test
    public void numeralWithoutContinuingStaffRuleDoesNotAuthorizeRemoval() {
        Page p = new Page();
        p.beam();
        p.three(0, 0);
        assertFalse(p.numeralBand());
    }

    @Test
    public void veryShortSecondaryBeamJoinedToNumeralAndStemSurvives() throws Exception {
        Page p = new Page();
        p.staff();
        p.beam();
        p.three(0, 0);
        p.rect(110, 38, 119, 43);
        p.rect(110, 38, 112, 116);
        assertFalse(p.numeralBand());
        assertEquals(2, p.bands());
    }

    @Test
    public void numeralWithoutIndependentBeamDoesNotAuthorizeRemoval() {
        Page p = new Page();
        p.staff();
        p.three(0, 0);
        assertFalse(p.numeralBand());
    }

    @Test
    public void numeralAtAnotherColumnDoesNotOwnBand() {
        Page p = new Page();
        p.staff();
        p.beam();
        p.three(-25, 0);
        assertFalse(p.numeralBand());
    }

    @Test
    public void thickRuleDoesNotSupplyThinRuleProof() {
        Page p = new Page();
        p.staff();
        p.beam();
        p.three(0, 0);
        p.rect(5, 38, 254, 43);
        assertFalse(p.numeralBand());
    }

    @Test
    public void closedEightDoesNotSupplyThreeProof() {
        Page p = new Page();
        p.staff();
        p.beam();
        p.rect(103, 25, 105, 44);
        p.rect(116, 25, 118, 44);
        p.rect(103, 25, 118, 27);
        p.rect(103, 33, 118, 35);
        p.rect(103, 42, 118, 44);
        assertFalse(p.numeralBand());
    }

    @Test
    public void slopedBeamBesideNumeralStillHasIndependentBody() {
        Page p = new Page();
        p.staff();
        p.three(0, 0);
        for (int x = 45; x <= 190; x++)
            p.rect(x, 50 + Math.round((x - 116) * .08f), x, 56 + Math.round((x - 116) * .08f));
        assertTrue(p.numeralBand());
    }

    @Test
    public void shallowSlopedStaffRuleCanOwnNumeralBand() {
        Page p = new Page();
        p.beam();
        p.three(0, 0);
        for (int x = 5; x <= 254; x++)
            for (int line = 0; line < 5; line++)
                p.rect(
                        x,
                        40 + line * 16 + Math.round((x - 116) * .05f),
                        x,
                        40 + line * 16 + Math.round((x - 116) * .05f));
        assertTrue(p.numeralBand());
    }

    @Test
    public void blankAndInvalidInputsDoNotAuthorizeRemoval() {
        Page p = new Page();
        assertFalse(p.numeralBand());
        assertFalse(TupletNumeralBeamBand.matches(null, W, H, 116, 38, 42, 34, 78, 40, 16, 165));
        assertFalse(TupletNumeralBeamBand.matches(p.gray, W, H, 1, 38, 42, 34, 78, 40, 16, 165));
    }
}
