// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raster controls; no score-derived pixels. */
public class SingleBarCancellationTest {
    private JoinedSignatureSharpTest page(boolean phantom) {
        var f = new JoinedSignatureSharpTest();
        f.row(100, 0, 0, false);
        var m = f.measures.get(0);
        f.measures.set(0, new MeasureRegion(m.left(), 500f / f.W, m.top(), m.bottom()));
        if (phantom) f.measures.add(new MeasureRegion(501f / f.W, 560f / f.W, m.top(), m.bottom()));
        bar(f, 497);
        natural(f);
        f.row(310, 0, 0, false);
        return f;
    }

    private void bar(JoinedSignatureSharpTest f, int x) {
        for (int y = 100; y <= 164; y++) for (int xx = x; xx <= x + 1; xx++) f.ink(xx, y, 1);
    }

    private void natural(JoinedSignatureSharpTest f) {
        for (int y = 110; y <= 141; y++) for (int x = 513; x <= 514; x++) f.ink(x, y, 3);
        for (int y = 123; y <= 154; y++) for (int x = 522; x <= 523; x++) f.ink(x, y, 3);
        for (int cy : new int[] {124, 140})
            for (int y = cy - 1; y <= cy + 1; y++) for (int x = 514; x <= 522; x++) f.ink(x, y, 3);
    }

    @Test
    public void singleEndingBarAllowsCompleteNatural() {
        assertEquals(List.of(0), page(false).keys());
    }

    @Test
    public void courtesyOnlyRegionCannotHideNatural() {
        assertEquals(List.of(0), page(true).keys());
    }

    @Test
    public void followingPlayedNotePreservesLocalAccidental() {
        var f = page(false);
        for (int y = 129; y <= 137; y++) for (int x = 545; x <= 557; x++) f.ink(x, y, 2);
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void closedFollowingMeasureIsNotCourtesy() {
        var f = page(false);
        bar(f, 600);
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void followingRestIsNotCourtesy() {
        var f = page(false);
        for (int y = 131; y <= 137; y++) for (int x = 548; x <= 564; x++) f.ink(x, y, 1);
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void missingPhysicalBarIsNotCourtesy() {
        var f = page(false);
        for (int y = 100; y <= 164; y++)
            for (int x = 497; x <= 498; x++) {
                f.labels[y * f.W + x] = 0;
                f.gray[y * f.W + x] = (byte) 255;
            }
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void pageEndCannotInventNextMeasure() {
        var f = page(false);
        f.measures.remove(1);
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void semanticNaturalWithoutRawInkIsRejected() {
        var f = page(false);
        for (int y = 108; y <= 156; y++)
            for (int x = 512; x <= 524; x++) f.gray[y * f.W + x] = (byte) 255;
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void unlabelledClosingBarIsNotCourtesy() {
        var f = page(false);
        bar(f, 600);
        for (int y = 100; y <= 164; y++) for (int x = 600; x <= 601; x++) f.labels[y * f.W + x] = 0;
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void unlabelledFollowingRestIsNotCourtesy() {
        var f = page(false);
        for (int y = 131; y <= 137; y++) for (int x = 548; x <= 564; x++) f.gray[y * f.W + x] = 0;
        assertEquals(List.of(), f.keys());
    }

    @Test
    public void multipleNaturalsCancelTogether() {
        var f = page(false);
        for (int y = 110; y <= 154; y++)
            for (int x = 513; x <= 523; x++)
                if (f.labels[y * f.W + x] == 3) f.ink(x + 21, y - 24, 3);
        assertEquals(List.of(0), f.keys());
    }

    @Test
    public void scaledSingleBarCancellation() {
        for (float scale : new float[] {.75f, 1.25f, 1.5f}) {
            var f = page(false);
            int w = Math.round(f.W * scale), h = Math.round(f.H * scale);
            byte[] l = new byte[w * h], g = new byte[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int i =
                            Math.min(f.H - 1, (int) (y / scale)) * f.W
                                    + Math.min(f.W - 1, (int) (x / scale));
                    l[y * w + x] = f.labels[i];
                    g[y * w + x] = f.gray[i];
                }
            var keys = OmrScoreInterpreter.analyze(l, g, w, h, f.measures).keyChanges();
            assertEquals(
                    "scale=" + scale,
                    List.of(0),
                    keys.stream().map(ScoreKeyChange::fifths).toList());
        }
    }
}
