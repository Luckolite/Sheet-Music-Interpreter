// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original whole-analyze key cancellation scenes, no source-score pixels. */
public final class CancellationAuthorityTest {
    static void natural(JoinedSignatureSharpTest f, int x, int y, boolean fragment) {
        for (int yy = y - 22; yy <= y + 9; yy++)
            for (int xx = x - 5; xx <= x - 4; xx++) f.ink(xx, yy, 3);
        for (int yy = y - 9; yy <= y + 22; yy++)
            for (int xx = x + 4; xx <= x + 5; xx++) f.ink(xx, yy, 3);
        for (int cy : new int[] {y - 8, y + 8})
            for (int yy = cy - 1; yy <= cy + 1; yy++)
                for (int xx = x - 4; xx <= x + 4; xx++) f.ink(xx, yy, 3);
        if (fragment) {
            for (int yy = y - 22; yy <= y + 22; yy++)
                for (int xx = x - 5; xx <= x + 5; xx++)
                    if (f.labels[yy * JoinedSignatureSharpTest.W + xx] == 3)
                        f.labels[yy * JoinedSignatureSharpTest.W + xx] = 0;
            for (int yy = y - 9; yy <= y - 7; yy++)
                for (int xx = x - 4; xx <= x + 4; xx++)
                    f.labels[yy * JoinedSignatureSharpTest.W + xx] = 3;
            for (int yy = y - 6; yy <= y; yy++)
                for (int xx = x - 5; xx <= x - 4; xx++)
                    f.labels[yy * JoinedSignatureSharpTest.W + xx] = 3;
        }
    }

    static void flat(JoinedSignatureSharpTest f, int x, int y) {
        for (int yy = y - 28; yy <= y + 7; yy++)
            for (int xx = x; xx <= x + 2; xx++) f.ink(xx, yy, 3);
        for (int yy = y - 7; yy <= y + 7; yy++)
            for (int xx = x + 2; xx <= x + 12; xx++) {
                double r = Math.pow((xx - x - 3) / 9d, 2) + Math.pow((yy - y) / 7d, 2);
                if (r <= 1 && r >= .35) f.ink(xx, yy, 3);
            }
    }

    static JoinedSignatureSharpTest fixture(
            int prior, boolean ordinary, boolean raw, int... lanes) {
        var f = new JoinedSignatureSharpTest();
        f.row(100, Math.max(0, prior), 0, false);
        if (prior < 0) {
            int[] dy = {32, 8, 40, 16, 48, 24, 56};
            for (int i = 0; i < -prior; i++) flat(f, 80 + 21 * i, 100 + dy[i]);
        }
        var m = f.measures.get(0);
        f.measures.set(
                0,
                new MeasureRegion(
                        m.left(), 500f / JoinedSignatureSharpTest.W, m.top(), m.bottom()));
        if (!ordinary) {
            for (int yy = 100; yy <= 164; yy++)
                for (int xx : new int[] {490, 491, 497, 498}) f.ink(xx, yy, 1);
            for (int i = 0; i < lanes.length; i++) natural(f, 518 + 21 * i, 100 + lanes[i], raw);
        }
        f.row(310, 0, 0, false);
        if (ordinary)
            for (int i = 0; i < lanes.length; i++) natural(f, 80 + 21 * i, 310 + lanes[i], raw);
        return f;
    }

    static List<Integer> scene(int prior, boolean ordinary, boolean raw, int... lanes) {
        return fixture(prior, ordinary, raw, lanes).keys();
    }

    @Test
    public void courtesyCompleteOneSharp() {
        assertEquals(List.of(1, 0), scene(1, false, false, 0));
    }

    @Test
    public void courtesyCompleteThreeSharps() {
        assertEquals(List.of(3, 0), scene(3, false, false, 0, 24, -8));
    }

    @Test
    public void courtesyPartialThreeSharps() {
        assertEquals(List.of(3), scene(3, false, false, 0));
    }

    @Test
    public void courtesyRepeatedPitchDoesNotFillCount() {
        assertEquals(List.of(3), scene(3, false, false, 0, 0, 24));
    }

    @Test
    public void courtesyWrongNaturalLane() {
        assertEquals(List.of(1), scene(1, false, false, 32));
    }

    @Test
    public void courtesyCompleteOneFlat() {
        assertEquals(List.of(-1, 0), scene(-1, false, false, 32));
    }

    @Test
    public void courtesyCompleteThreeFlats() {
        assertEquals(List.of(-3, 0), scene(-3, false, false, 32, 8, 40));
    }

    @Test
    public void courtesyPartialThreeFlats() {
        assertEquals(List.of(-3), scene(-3, false, false, 32));
    }

    @Test
    public void ordinaryCompleteThreeSharps() {
        assertEquals(List.of(3, 0), scene(3, true, false, 0, 24, -8));
    }

    @Test
    public void ordinaryPartialThreeSharps() {
        assertEquals(List.of(3), scene(3, true, false, 0));
    }

    @Test
    public void ordinaryWrongLane() {
        assertEquals(List.of(1), scene(1, true, false, 32));
    }

    @Test
    public void ordinaryCompleteThreeFlats() {
        assertEquals(List.of(-3, 0), scene(-3, true, false, 32, 8, 40));
    }

    @Test
    public void courtesyUnknownPriorRemainsUnknown() {
        assertEquals(List.of(), scene(0, false, false, 0));
    }

    @Test
    public void ordinaryUnknownPriorRemainsUnknown() {
        assertEquals(List.of(), scene(0, true, false, 0));
    }

    @Test
    public void rawCourtesyCompleteOneSharp() {
        assertEquals(List.of(1, 0), scene(1, false, true, 0));
    }

    @Test
    public void rawCourtesyPartialThreeSharps() {
        assertEquals(List.of(3), scene(3, false, true, 0));
    }

    static JoinedSignatureSharpTest splitNaturalFixture() {
        var f = fixture(1, false, false, 0);
        for (int y = 78; y <= 122; y++)
            for (int x = 513; x <= 523; x++)
                if (f.labels[y * JoinedSignatureSharpTest.W + x] == 3)
                    f.labels[y * JoinedSignatureSharpTest.W + x] = 0;
        for (int y = 91; y <= 93; y++)
            for (int x = 514; x <= 522; x++) f.labels[y * JoinedSignatureSharpTest.W + x] = 3;
        for (int y = 99; y <= 109; y++)
            for (int x = 513; x <= 514; x++) f.labels[y * JoinedSignatureSharpTest.W + x] = 3;
        for (int y = 107; y <= 109; y++)
            for (int x = 514; x <= 522; x++) f.labels[y * JoinedSignatureSharpTest.W + x] = 3;
        return f;
    }

    @Test
    public void laterCompleteRawNaturalOwnsEarlierIncompleteFragment() {
        assertEquals(List.of(1, 0), splitNaturalFixture().keys());
    }

    @Test
    public void independentNaturalCannotBeDiscardedAsNearbyFragment() {
        var f = splitNaturalFixture();
        natural(f, 539, 132, false);
        assertEquals(List.of(1), f.keys());
    }
}
