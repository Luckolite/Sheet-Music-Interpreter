// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete treble headers located inside a permissive first rectangle. */
public class InsideOpeningClefTest {
    static final int W = 520, H = 260;

    static byte[][] page(boolean clef) {
        var p = new HeaderAccidentalOwnershipTest.Page(clef, 185);
        p.flat(122, 140);
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < 420; x++) {
                labels[y * W + x + 100] = p.labels[y * 420 + x];
                gray[y * W + x + 100] = p.gray[y * 420 + x];
            }
        for (int y = 95; y <= 169; y++)
            for (int x = 5; x <= 24; x++) {
                labels[y * W + x] = 3;
                gray[y * W + x] = 0;
            }
        return new byte[][] {labels, gray};
    }

    @Test
    public void boundedOpeningClefRetainsAllFlats() {
        for (int edge : new int[] {64, 68, 72, 76}) {
            var p = page(true);
            var labels = p[0].clone();
            var gray = p[1].clone();
            var a =
                    OmrScoreInterpreter.analyze(
                            p[0],
                            p[1],
                            W,
                            H,
                            List.of(new MeasureRegion(edge / (float) W, 1, .1f, .9f)));
            assertEquals("edge=" + edge, List.of(new ScoreKeyChange(0, -3)), a.keyChanges());
            assertEquals(2, a.notes().size());
            assertArrayEquals(labels, p[0]);
            assertArrayEquals(gray, p[1]);
        }
    }

    @Test
    public void absentClefCannotEstablishHeader() {
        for (int edge : new int[] {64, 68, 72, 76}) {
            var p = page(false);
            var a =
                    OmrScoreInterpreter.analyze(
                            p[0],
                            p[1],
                            W,
                            H,
                            List.of(new MeasureRegion(edge / (float) W, 1, .1f, .9f)));
            assertTrue(a.keyChanges().isEmpty());
        }
    }
}
