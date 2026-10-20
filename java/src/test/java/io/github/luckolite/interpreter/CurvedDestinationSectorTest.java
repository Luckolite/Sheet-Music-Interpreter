// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real principal head lies outside the finite hook's measured continuation sector. */
public class CurvedDestinationSectorTest {
    static int read(int y) {
        var base = FiniteCurvedApproachTest.scene(18, "hook");
        byte[] g = base.gray().clone();
        var old = base.heads().get(0);
        int x = Math.round(old.x());
        for (int yy = Math.round(old.y()) - 4; yy <= Math.round(old.y()) + 4; yy++)
            for (int xx = x - 7; xx <= x + 7; xx++) g[yy * 360 + xx] = (byte) 255;
        for (int yy = y - 4; yy <= y + 4; yy++)
            for (int xx = x - 7; xx <= x + 7; xx++) {
                double q = (xx - x) * (xx - x) / 49. + (yy - y) * (yy - y) / 16.;
                if (q <= 1 && q >= .3) g[yy * 360 + xx] = 0;
            }
        var heads = List.of(new NoteSlideDetector.Head(x, y, 18, 0, 0));
        return NoteSlideDetector.detect(g, 360, 260, List.of(), heads).size();
    }

    @Test
    public void shallowerDestinationOutsideContinuationAbstains() {
        assertEquals(0, read(120));
    }

    @Test
    public void steeperDestinationOutsideContinuationAbstains() {
        assertEquals(0, read(53));
    }
}
