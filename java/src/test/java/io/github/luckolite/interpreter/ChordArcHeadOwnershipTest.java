// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original stacked-head geometry: filled head rims are not a sounding stem. */
public class ChordArcHeadOwnershipTest {
    static final int W = 260, H = 220;

    byte[] pixels() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        for (int[] c : new int[][] {{56, 148}, {196, 148}, {194, 130}})
            for (int y = c[1] - 6; y <= c[1] + 6; y++)
                for (int x = c[0] - 10; x <= c[0] + 10; x++)
                    if (Math.pow((x - c[0]) / 10d, 2) + Math.pow((y - c[1]) / 6d, 2) <= 1)
                        p[y * W + x] = 0;
        // Adjacent thick head rims join vertically in a low-resolution chord.
        for (int x = 184; x <= 186; x++) for (int y = 132; y <= 144; y++) p[y * W + x] = 0;
        return p;
    }

    PrintedSlurOwners.Binding bind(List<PrintedSlurOwners.GeometryHead> heads, byte[] p) {
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 160, 16}, new float[] {W - 1, 160, 16}));
        var bow =
                new PrintedSlurContours.Bow(
                        List.of(
                                new PrintedSlurContours.Point(60, 160),
                                new PrintedSlurContours.Point(125, 180),
                                new PrintedSlurContours.Point(190, 160)),
                        false,
                        1.25f,
                        0);
        return PrintedSlurOwners.bind("original-chord", 0, bow, heads, p, W, H, track);
    }

    List<PrintedSlurOwners.GeometryHead> heads() {
        return List.of(
                new PrintedSlurOwners.GeometryHead(0, 56, 148, 0, 1),
                new PrintedSlurOwners.GeometryHead(1, 194, 130, 0, 1),
                new PrintedSlurOwners.GeometryHead(2, 196, 148, 0, 1));
    }

    @Test
    public void chordHeadInkCannotStealLowerArcEndpoint() {
        var b = bind(heads(), pixels());
        assertEquals(PrintedSlurOwners.Status.GEOMETRY_OWNED, b.status());
        assertEquals(2, b.span().orElseThrow().end().noteIndex());
    }

    @Test
    public void sourceOrderDoesNotChangePhysicalOwner() {
        var heads = new ArrayList<>(heads());
        Collections.reverse(heads);
        assertEquals(2, bind(heads, pixels()).span().orElseThrow().end().noteIndex());
    }

    @Test
    public void coincidentHeadOwnershipStaysAmbiguous() {
        var heads = new ArrayList<>(heads());
        heads.add(new PrintedSlurOwners.GeometryHead(3, 196, 148, 0, 1));
        assertEquals(PrintedSlurOwners.Status.AMBIGUOUS_END, bind(heads, pixels()).status());
    }

    @Test
    public void pixelsRemainUnchanged() {
        byte[] p = pixels(), before = p.clone();
        bind(heads(), p);
        assertArrayEquals(before, p);
    }
}
