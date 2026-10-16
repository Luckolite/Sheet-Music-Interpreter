// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original full-size stemmed notes and asymmetric connecting arc; no score pixels. */
public final class StemmedSlurHeadTest {
    static final int W = 240, H = 240;

    static class Page {
        byte[] gray = new byte[W * H];
        List<Object> heads = new ArrayList<>();

        Page() throws Exception {
            Arrays.fill(gray, (byte) 255);
            oval(100, 140, 10, 6, true);
            oval(140, 126, 10, 6, true);
        }

        void pixel(int x, int y) {
            gray[y * W + x] = 0;
        }

        Object component(int area, int l, int r, int t, int b, float x, float y) throws Exception {
            Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
            Constructor<?> ctor =
                    c.getDeclaredConstructor(
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            float.class,
                            float.class);
            ctor.setAccessible(true);
            return ctor.newInstance(area, l, r, t, b, x, y);
        }

        void oval(int x, int y, int rx, int ry, boolean stem) throws Exception {
            int area = 0;
            for (int yy = y - ry; yy <= y + ry; yy++)
                for (int xx = x - rx; xx <= x + rx; xx++)
                    if (Math.pow((xx - x) / (double) rx, 2) + Math.pow((yy - y) / (double) ry, 2)
                            <= 1) {
                        pixel(xx, yy);
                        area++;
                    }
            if (stem) for (int yy = y - 30; yy <= y; yy++) pixel(x + rx - 1, yy);
            heads.add(component(area, x - rx, x + rx, y - ry, y + ry, x, y));
        }

        void slur() throws Exception {
            int area = 0, l = W, r = 0, t = H, b = 0;
            long sx = 0, sy = 0;
            for (int x = 105; x <= 133; x++) {
                double f = (x - 105) / 28.;
                int y = (int) Math.round(148 - 8 * f + 6 * 4 * f * (1 - f));
                for (int dy = -1; dy <= 1; dy++) {
                    pixel(x, y + dy);
                    if (x >= 108 && x <= 122) {
                        area++;
                        l = Math.min(l, x);
                        r = Math.max(r, x);
                        t = Math.min(t, y + dy);
                        b = Math.max(b, y + dy);
                        sx += x;
                        sy += y + dy;
                    }
                }
            }
            heads.add(component(area, l, r, t, b, sx / (float) area, sy / (float) area));
        }

        List<?> rejected(boolean raw) throws Exception {
            Class<?> s = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
            Constructor<?> ctor = s.getDeclaredConstructor(float.class, float.class, float.class);
            ctor.setAccessible(true);
            Method m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "stemmedSlurHeads",
                            byte[].class,
                            int.class,
                            int.class,
                            List.class,
                            List.class);
            m.setAccessible(true);
            return (List<?>)
                    m.invoke(
                            null,
                            raw ? gray : null,
                            W,
                            H,
                            heads,
                            List.of(ctor.newInstance(84f, 140f, 14f)));
        }
    }

    @Test
    public void asymmetricSlurIsNotAThirdNote() throws Exception {
        var p = new Page();
        p.slur();
        Object h = p.heads.get(2);
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "rawSlurBowl",
                        byte[].class,
                        int.class,
                        int.class,
                        h.getClass(),
                        float.class,
                        int.class,
                        boolean.class);
        m.setAccessible(true);
        assertTrue("raw curve", (boolean) m.invoke(null, p.gray, W, H, h, 14f, 100, true));
        assertEquals(List.of(h), p.rejected(true));
    }

    @Test
    public void missingRawEvidencePreservesCandidate() throws Exception {
        var p = new Page();
        p.slur();
        assertTrue(p.rejected(false).isEmpty());
    }

    @Test
    public void missingLeftAnchorDoesNotProveConnector() throws Exception {
        var p = new Page();
        p.slur();
        p.heads.remove(0);
        assertTrue(p.rejected(true).isEmpty());
    }

    @Test
    public void missingRightAnchorDoesNotProveConnector() throws Exception {
        var p = new Page();
        p.slur();
        p.heads.remove(1);
        assertTrue(p.rejected(true).isEmpty());
    }

    @Test
    public void smallIndependentFilledNoteRemains() throws Exception {
        var p = new Page();
        p.oval(112, 153, 5, 3, false);
        assertTrue(p.rejected(true).isEmpty());
    }

    @Test
    public void smallStemmedNoteRemains() throws Exception {
        var p = new Page();
        p.oval(112, 153, 5, 3, true);
        assertTrue(p.rejected(true).isEmpty());
    }

    @Test
    public void shadedScanKeepsShapeEvidence() throws Exception {
        var p = new Page();
        p.slur();
        for (int i = 0; i < p.gray.length; i++)
            p.gray[i] = (byte) ((p.gray[i] & 255) == 0 ? 55 : 175);
        assertEquals(1, p.rejected(true).size());
    }

    @Test
    public void doesNotModifySource() throws Exception {
        var p = new Page();
        p.slur();
        var g = p.gray.clone();
        p.rejected(true);
        assertArrayEquals(g, p.gray);
    }
}
