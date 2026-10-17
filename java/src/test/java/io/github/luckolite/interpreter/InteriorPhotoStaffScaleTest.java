// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public class InteriorPhotoStaffScaleTest {
    static final int W = 800, H = 1000;

    static byte[][] page(boolean faint, int fakeGap) {
        byte[] l = new byte[W * H], g = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                g[y * W + x] =
                        (byte) Math.round(160 + 20 * Math.sin(x / 13d) + 10 * Math.cos(y / 80d));
        for (int top : new int[] {80, 280, 420, 620, 820}) {
            int gap = top == 420 ? fakeGap : 14;
            for (int n = 0; n < 5; n++)
                for (int x = 30; x < 770; x++) {
                    int i = (top + n * gap) * W + x;
                    l[i] = 4;
                    if (top != 420) g[i] = 0;
                    else if (faint) g[i] = (byte) ((g[i] & 255) - 6);
                }
        }
        return new byte[][] {l, g};
    }

    static int staffs(byte[][] p) throws Exception {
        var m = new ArrayList<MeasureRegion>();
        for (int top : new int[] {80, 280, 420, 620, 820})
            m.add(new MeasureRegion(.03f, .97f, (top - 25f) / H, (top + 100f) / H));
        var f =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "findStaffs", byte[].class, byte[].class, int.class, int.class, List.class);
        f.setAccessible(true);
        return ((List<?>) f.invoke(null, p[0], p[1], W, H, m)).size();
    }

    @Test
    public void unsupportedEnlargedPhotoRowIsRejected() throws Exception {
        assertEquals(4, staffs(page(false, 19)));
    }

    @Test
    public void faintPrintedEnlargedStaffRemains() throws Exception {
        assertEquals(5, staffs(page(true, 19)));
    }

    @Test
    public void sameScaleHiddenInteriorRemains() throws Exception {
        assertEquals(5, staffs(page(false, 14)));
    }
}
