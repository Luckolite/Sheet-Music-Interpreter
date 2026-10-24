// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiteralStaffContrastCatalogTest {
    private LiteralStaffContrastTest.Scene scene() {
        return new LiteralStaffContrastTest.Scene();
    }

    private LiteralStaffContrast.CatalogBudget budget() {
        return new LiteralStaffContrast.CatalogBudget(128000000);
    }

    private LiteralStaffContrast.Catalog catalog(
            LiteralStaffContrastTest.Scene s, LiteralStaffContrast.CatalogBudget b) {
        return LiteralStaffContrast.catalog(s.gray, s.w, s.h, s.top, s.gap, b).orElseThrow();
    }

    private Optional<LiteralStaffContrast.Field> field(LiteralStaffContrastTest.Scene s) {
        var b = budget();
        return catalog(s, b).field(s.left, s.right, b);
    }

    @Test
    public void fullRunCanSupplyTwoDisjointClippedReferences() {
        var s = scene();
        assertTrue(field(s).isPresent());
    }

    @Test
    public void fieldMatchesOriginalIndependentObserverAtEveryCell() {
        var s = scene();
        s.set(120, 50, 0);
        s.set(121, 44, 20);
        var old = s.field();
        var now = field(s).orElseThrow();
        for (int y = now.firstY(); y <= now.lastY(); y++)
            for (int x = s.left; x <= s.right; x++) assertEquals(old.cell(x, y), now.cell(x, y));
    }

    @Test
    public void carrierRemainsUnknownAcrossAllTones() {
        for (int value : new int[] {0, 1, 20, 39, 40, 100, 200, 254}) {
            var s = scene();
            s.set(120, 44, Math.min(value, 40));
            assertNotEquals(
                    LiteralStaffContrast.Kind.OBSERVED_WHITE,
                    field(s).orElseThrow().cell(120, 44).kind());
        }
    }

    @Test
    public void fullyBlackMatchedCarrierIsNeverAnOccludedHead() {
        var s = scene();
        for (int x = 1; x < s.w - 1; x++) s.set(x, 44, 0);
        assertEquals(
                LiteralStaffContrast.Kind.MATCHED_CARRIER,
                field(s).orElseThrow().cell(120, 44).kind());
    }

    @Test
    public void weakContrastIsNotWhite() {
        var s = scene();
        s.set(120, 50, 254);
        assertEquals(
                LiteralStaffContrast.Kind.EXTRA_FRINGE,
                field(s).orElseThrow().cell(120, 50).kind());
    }

    @Test
    public void rightSideCannotBorrowLeftReference() {
        var s = scene();
        for (int x = s.right + 2; x < s.w - 1; x++) s.set(x, 26, 254);
        assertTrue(field(s).isEmpty());
    }

    @Test
    public void shortLiteralPrefixCannotQualify() {
        var s = scene();
        for (int x = 1; x < s.left - 12; x++) s.set(x, 26, 254);
        assertTrue(field(s).isEmpty());
    }

    @Test
    public void everyToneMustMatchOnBothSides() {
        var s = scene();
        for (int x = s.right + 2; x < s.w - 1; x++) s.set(x, 50, 254);
        assertTrue(field(s).isEmpty());
    }

    @Test
    public void multipleCommonProfilesStayAmbiguous() {
        var s = scene();
        for (int x = 51; x < s.left - 1; x++) s.set(x, 44, 35);
        for (int x = s.right + 2; x <= 180; x++) s.set(x, 44, 35);
        assertTrue(field(s).isEmpty());
    }

    @Test
    public void oneBrighterCellContradictsEntireField() {
        var s = scene();
        s.set(120, 44, 41);
        assertTrue(field(s).isEmpty());
    }

    @Test
    public void catalogAndFieldAreSnapshots() {
        var s = scene();
        s.set(120, 50, 0);
        byte[] before = s.gray.clone();
        var b = budget();
        var c = catalog(s, b);
        Arrays.fill(s.gray, (byte) 255);
        var f = c.field(s.left, s.right, b).orElseThrow();
        assertEquals(LiteralStaffContrast.Kind.EXTRA_CORE, f.cell(120, 50).kind());
        assertEquals(40, f.cell(120, 44).observed());
        assertNotEquals(255, before[44 * s.w + 120] & 255);
    }

    @Test
    public void sourceBytesStayExact() {
        var s = scene();
        byte[] before = s.gray.clone();
        assertTrue(field(s).isPresent());
        assertArrayEquals(before, s.gray);
    }

    @Test
    public void zeroCatalogBudgetRefusesAndStaysRefused() {
        var s = scene();
        var b = new LiteralStaffContrast.CatalogBudget(0);
        assertTrue(LiteralStaffContrast.catalog(s.gray, s.w, s.h, s.top, s.gap, b).isEmpty());
        assertTrue(b.refused());
        assertFalse(b.take(0));
    }

    @Test
    public void lateFieldRefusalCannotRecoverForAnotherCandidate() {
        var s = scene();
        var b = budget();
        var c = catalog(s, b);
        assertTrue(c.field(s.left, s.right, b).isPresent());
        assertFalse(b.take(Long.MAX_VALUE));
        assertTrue(c.field(s.left, s.right, b).isEmpty());
    }

    @Test
    public void clippedDomainCannotInventSourcePixels() {
        var s = scene();
        var b = budget();
        var c = catalog(s, b);
        assertTrue(c.field(-1, s.right, b).isEmpty());
        assertTrue(c.field(s.left, s.w, b).isEmpty());
    }
}
