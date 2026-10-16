// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original small prefix and independent principal with staff or beam ink between shafts. */
public class StaffRuleGraceOwnershipTest {
    private boolean grace(int mode) throws Exception {
        int w = 250, h = 210;
        float gap = 14;
        byte[] g = new byte[w * h], l = new byte[g.length];
        Arrays.fill(g, (byte) 255);
        var hc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        hc.setAccessible(true);
        var dc =
                Class.forName(OmrScoreInterpreter.class.getName() + "$DetectedNote")
                        .getDeclaredConstructors()[0];
        dc.setAccessible(true);
        var ds = new ArrayList<Object>();
        var es = new ArrayList<ScoreNoteEvent>();
        int[] xs = {65, 105, 140};
        for (int i = 0; i < 3; i++) {
            int x = xs[i], y = 100, rx = i == 1 ? 5 : 10, ry = i == 1 ? 4 : 8, area = 0;
            for (int yy = y - ry; yy <= y + ry; yy++)
                for (int xx = x - rx; xx <= x + rx; xx++)
                    if (Math.pow((xx - x) / (double) rx, 2) + Math.pow((yy - y) / (double) ry, 2)
                            <= 1) {
                        g[yy * w + xx] = 30;
                        l[yy * w + xx] = 2;
                        area++;
                    }
            for (int yy = y; yy <= 142; yy++) {
                g[yy * w + x - rx + 1] = 30;
                l[yy * w + x - rx + 1] = 1;
            }
            var head = hc.newInstance(area, x - rx, x + rx, y - ry, y + ry, (float) x, (float) y);
            var e = new ScoreNoteEvent(0, x / (float) w, 2, 0, 1, y / (float) h, false, 0, 0, 2, 1);
            ds.add(dc.newInstance(e, head, gap));
            es.add(e);
        }
        for (int x = 20; x < 220; x++) {
            g[142 * w + x] = 30;
            l[142 * w + x] = 4;
        }
        if (mode == 1 || mode == 2)
            for (int x = 56; x <= 101; x++)
                for (int y = mode == 1 ? 142 : 140; y <= 142; y++) {
                    g[y * w + x] = 30;
                    if (mode == 1 || y != 142) l[y * w + x] = 1;
                }
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "markGraceHeads",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        m.invoke(null, mode == 3 ? null : l, g, w, h, ds, es);
        return (es.get(1).articulations() & NoteOrnament.GRACE) != 0;
    }

    @Test
    public void staffRuleDoesNotJoinAnOrdinaryBeam() throws Exception {
        assertTrue(grace(0));
    }

    @Test
    public void actualThinBeamRetainsMetricHead() throws Exception {
        assertFalse(grace(1));
    }

    @Test
    public void beamOverStaffRetainsMetricHead() throws Exception {
        assertFalse(grace(2));
    }

    @Test
    public void absentLabelsKeepConservativeOwnership() throws Exception {
        assertFalse(grace(3));
    }
}
