// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class CompactGraceAccidentalScaleTest {
    static List<ScoreNoteEvent> notes(int type, int mode, int transform) {
        var p = new GracePrefixGeometryTest.Page();
        if ((mode & 8) == 0) {
            p.head(45, 160, 11, 7, false);
            p.stem(56, 160, 48, true);
        }
        boolean full = (mode & 5) != 0;
        int rx = full ? 11 : 7, ry = full ? 7 : 5;
        p.head(110, 136, rx, ry, false);
        p.stem(110 + rx, 136, (mode & 4) != 0 ? 48 : 30, true);
        p.head(135, 144, rx, ry, false);
        p.stem(135 + rx, 144, (mode & 4) != 0 ? 48 : 30, true);
        if ((mode & 16) == 0)
            for (int x = 110 + rx; x <= 135 + rx; x++) {
                int y = 106 + Math.round(8 * (x - 110 - rx) / 25f);
                for (int z = 0; z < 3; z++) {
                    p.pixel(x, y + z, 1);
                    p.pixel(x, y + 7 + z, 1);
                }
            }
        int principalY = (mode & 128) != 0 ? 136 : 152;
        if ((mode & 8) == 0) {
            p.head(170, principalY, 11, 7, false);
            p.stem(181, principalY, 48, true);
        }
        if (type == 1 && (mode & 64) == 0) {
            for (int y = 122; y <= 150; y++)
                for (int x : new int[] {90, 91, 95, 96}) p.pixel(x, y, 3);
            for (int top : new int[] {131, 139})
                for (int y = top; y <= top + 2; y++)
                    for (int x = 89; x <= 97; x++) p.pixel(x, y, 3);
        }
        if (type == 0) {
            for (int y = 121; y <= 140; y++) for (int x = 90; x <= 91; x++) p.pixel(x, y, 3);
            for (int y = 131; y <= 151; y++) for (int x = 96; x <= 97; x++) p.pixel(x, y, 3);
            for (int top : new int[] {131, 139})
                for (int y = top; y <= top + 2; y++)
                    for (int x = 90; x <= 97; x++) p.pixel(x, y, 3);
        }
        if (type == -1) {
            for (int y = 117; y <= 141; y++) for (int x = 89; x <= 90; x++) p.pixel(x, y, 3);
            for (int y = 131; y <= 140; y++) for (int x = 96; x <= 97; x++) p.pixel(x, y, 3);
            for (int top : new int[] {131, 139})
                for (int y = top; y <= top + 2; y++)
                    for (int x = 89; x <= 97; x++) p.pixel(x, y, 3);
        }
        if (type == 3)
            for (int y = 122; y <= 150; y++) for (int x = 90; x <= 91; x++) p.pixel(x, y, 3);
        if (type == 1 && (mode & 64) != 0) {
            for (int y = 116; y <= 156; y++)
                for (int x : new int[] {90, 91, 97, 98}) p.pixel(x, y, 3);
            for (int top : new int[] {129, 140})
                for (int y = top; y <= top + 3; y++)
                    for (int x = 88; x <= 100; x++) p.pixel(x, y, 3);
        }
        int w = GracePrefixGeometryTest.W, h = GracePrefixGeometryTest.H;
        byte[] l = p.l, g = p.g;
        if (transform == 1) {
            byte[] ll = new byte[l.length * 4], gg = new byte[g.length * 4];
            for (int y = 0; y < h * 2; y++)
                for (int x = 0; x < w * 2; x++) {
                    ll[y * w * 2 + x] = l[y / 2 * w + x / 2];
                    gg[y * w * 2 + x] = g[y / 2 * w + x / 2];
                }
            l = ll;
            g = gg;
            w *= 2;
            h *= 2;
        }
        if (transform == 2) {
            byte[] ll = new byte[l.length], gg = new byte[g.length];
            Arrays.fill(gg, (byte) 255);
            for (int y = 0; y < h; y++)
                for (int x = 23; x < w; x++) {
                    ll[y * w + x] = l[y * w + x - 23];
                    gg[y * w + x] = g[y * w + x - 23];
                }
            l = ll;
            g = gg;
        }
        byte[] lb = l.clone(), gb = g.clone();
        var result = OmrScoreInterpreter.analyze(l, g, w, h, GracePrefixGeometryTest.M).notes();
        org.junit.Assert.assertArrayEquals(lb, l);
        org.junit.Assert.assertArrayEquals(gb, g);
        return result;
    }

    static ScoreNoteEvent first(int t, int m, int tr) {
        var n = notes(t, m, tr);
        org.junit.Assert.assertEquals((m & 8) != 0 ? 2 : 4, n.size());
        return n.get((m & 8) != 0 ? 0 : 1);
    }

    static void grace(ScoreNoteEvent n) {
        org.junit.Assert.assertNotEquals(0, n.articulations() & NoteOrnament.GRACE);
    }

    @org.junit.Test
    public void reducedSharpKeepsPrintedPitch() {
        var n = first(1, 0, 0);
        grace(n);
        org.junit.Assert.assertEquals(1, n.writtenAccidental());
    }

    @org.junit.Test
    public void doubleScaleReducedSharp() {
        org.junit.Assert.assertEquals(1, first(1, 0, 1).writtenAccidental());
    }

    @org.junit.Test
    public void translatedReducedSharp() {
        org.junit.Assert.assertEquals(1, first(1, 0, 2).writtenAccidental());
    }

    @org.junit.Test
    public void genuineNaturalIsPreserved() {
        var n = first(0, 0, 0);
        grace(n);
        org.junit.Assert.assertEquals(0, n.writtenAccidental());
    }

    @org.junit.Test
    public void genuineFlatIsPreserved() {
        var n = first(-1, 0, 0);
        grace(n);
        org.junit.Assert.assertEquals(-1, n.writtenAccidental());
    }

    @org.junit.Test
    public void alreadyRecognizedSharpIsPreserved() {
        org.junit.Assert.assertEquals(1, first(1, 64, 0).writtenAccidental());
    }

    @org.junit.Test
    public void adjacentDifferentPitchGraceDoesNotBorrowSharp() {
        var n = notes(1, 0, 0).get(2);
        grace(n);
        org.junit.Assert.assertEquals(2, n.writtenAccidental());
    }

    @org.junit.Test
    public void differentPrincipalPitchIsUnaffected() {
        org.junit.Assert.assertEquals(2, notes(1, 0, 0).get(3).writtenAccidental());
    }

    @org.junit.Test
    public void absentGlyphDoesNotAddAccidental() {
        org.junit.Assert.assertEquals(2, first(2, 0, 0).writtenAccidental());
    }

    @org.junit.Test
    public void ordinaryHeadIsNotRescaled() {
        var n = first(1, 1, 0);
        org.junit.Assert.assertEquals(0, n.articulations() & NoteOrnament.GRACE);
        org.junit.Assert.assertEquals(2, n.writtenAccidental());
    }

    @org.junit.Test
    public void noPrincipalCannotProveGraceScale() {
        var n = first(1, 8, 0);
        org.junit.Assert.assertEquals(0, n.articulations() & NoteOrnament.GRACE);
        org.junit.Assert.assertEquals(2, n.writtenAccidental());
    }

    @org.junit.Test
    public void fullLengthOrdinaryStemCannotProveGraceScale() {
        var n = first(1, 4, 0);
        org.junit.Assert.assertEquals(0, n.articulations() & NoteOrnament.GRACE);
        org.junit.Assert.assertEquals(2, n.writtenAccidental());
    }

    @org.junit.Test
    public void unbeamedOrnamentKeepsExistingPolicy() {
        org.junit.Assert.assertEquals(2, first(1, 16, 0).writtenAccidental());
    }

    @org.junit.Test
    public void singleSpineIsNotAnAccidental() {
        org.junit.Assert.assertEquals(2, first(3, 0, 0).writtenAccidental());
    }

    @org.junit.Test
    public void printedGraceAccidentalEntersSameBarPitchState() {
        var n = notes(1, 128, 0);
        org.junit.Assert.assertEquals(1, n.get(1).writtenAccidental());
        org.junit.Assert.assertEquals(1, n.get(3).writtenAccidental());
    }
}
