// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Conditional metadata transport, not accepted musical-role evidence. */
public final class PhysicalMemberReplacementTest {
    private byte[] l = new byte[160 * 200], g = new byte[l.length];

    private SelectedMeasureOrigins fresh() {
        var p = new SelectedMeasureOrigins();
        p.begin(l, g, 160, 200);
        return p;
    }

    @Test
    public void boundsReplacementRetainsLiteralCreationRows() {
        var p = fresh();
        var old = new Object();
        var next = new Object();
        int[] r = {60, 72, 84, 96, 108};
        p.born(old, r, 12, 20, 120, .025f, null);
        r[0] = 1;
        p.replaced(old, next, 12, 18, 130, .025f, null);
        var m = p.member(next, 12, 18, 130, .025f, null);
        assertArrayEquals(new int[] {60, 72, 84, 96, 108}, m.rows);
        assertSame(next, m.owner);
        assertEquals(18, m.left);
        assertEquals(130, m.right);
        assertNotNull(m.physicalRows(125, 160));
    }

    @Test
    public void unregisteredPredecessorStaysUnknown() {
        var p = fresh();
        var n = new Object();
        p.replaced(new Object(), n, 12, 20, 120, 0, null);
        assertNull(p.member(n, 12, 20, 120, 0, null).rows);
    }

    @Test
    public void equalButDistinctPredecessorStaysUnknown() {
        var p = fresh();
        String old = new String("a"), other = new String("a");
        p.born(old, new int[] {60, 72, 84, 96, 108}, 12, 20, 120, 0, null);
        var n = new Object();
        p.replaced(other, n, 12, 20, 120, 0, null);
        assertNull(p.member(n, 12, 20, 120, 0, null).rows);
    }

    @Test
    public void changedSlopeStaysUnknown() {
        var p = fresh();
        var a = new Object();
        var n = new Object();
        p.born(a, new int[] {60, 72, 84, 96, 108}, 12, 20, 120, 0, null);
        p.replaced(a, n, 12, 20, 120, .01f, null);
        assertNull(p.member(n, 12, 20, 120, .01f, null).rows);
    }

    @Test
    public void changedGapStaysUnknown() {
        var p = fresh();
        var a = new Object();
        var n = new Object();
        p.born(a, new int[] {60, 72, 84, 96, 108}, 12, 20, 120, 0, null);
        p.replaced(a, n, 13, 20, 120, 0, null);
        assertNull(p.member(n, 13, 20, 120, 0, null).rows);
    }

    @Test
    public void changedTrackStaysUnknown() {
        var p = fresh();
        var a = new Object();
        var n = new Object();
        var t = StaffPitchTrack.linear(160, 108, 12, 0);
        p.born(a, new int[] {60, 72, 84, 96, 108}, 12, 20, 120, 0, null);
        p.replaced(a, n, 12, 20, 120, 0, t);
        assertNull(p.member(n, 12, 20, 120, 0, t).rows);
    }

    @Test
    public void resetInvalidatesBirthPredecessor() {
        var p = fresh();
        var a = new Object();
        var n = new Object();
        p.born(a, new int[] {60, 72, 84, 96, 108}, 12, 20, 120, 0, null);
        p.begin(l, g, 160, 200);
        p.replaced(a, n, 12, 20, 120, 0, null);
        assertNull(p.member(n, 12, 20, 120, 0, null).rows);
    }
}
