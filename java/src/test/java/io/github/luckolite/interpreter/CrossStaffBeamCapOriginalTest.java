// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original ellipse/shaft/rail geometry at the beam-junction caller, no source score pixels. */
public class CrossStaffBeamCapOriginalTest {
    static final int W = 360, H = 340;

    static class Page {
        byte[] l = new byte[W * H], g = new byte[W * H];
        int capX = 174, capY = 223;
        float gap = 14, upperTop = 60, lowerTop = 200;

        Page() {
            Arrays.fill(g, (byte) 255);
        }

        void pixel(int x, int y, int label) {
            g[y * W + x] = 0;
            l[y * W + x] = (byte) label;
        }

        void oval(int cx, int cy) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if (Math.pow((x - cx) / 10., 2) + Math.pow((y - cy) / 8., 2) <= 1)
                        pixel(x, y, 2);
        }
    }

    static Page scene(boolean shortShaft) {
        var p = new Page();
        int end = shortShaft ? 150 : 225;
        p.capY = end - 2;
        for (int y = 80; y <= end; y++) p.pixel(177, y, 1);
        for (int y = 87; y <= end + 5; y++) p.pixel(121, y, 1);
        for (int x = 121; x <= 177; x++) {
            int bottom = end + 5 - Math.round((x - 121) * 5f / 56);
            for (int y = bottom - 5; y <= bottom; y++) p.pixel(x, y, 5);
        }
        p.oval(187, 80);
        p.oval(131, 87);
        for (int y = end - 5; y <= end; y++)
            for (int x = 171; x <= 177; x++) if ((p.g[y * W + x] & 255) < 165) p.l[y * W + x] = 2;
        return p;
    }

    static boolean rejected(Page p, boolean sameSystem) throws Exception {
        var c = OmrScoreInterpreter.class;
        var components =
                c.getDeclaredMethod(
                        "findComponents", byte[].class, int.class, int.class, byte.class);
        components.setAccessible(true);
        var heads = (List<?>) components.invoke(null, p.l, W, H, (byte) 2);
        var staff = Class.forName(c.getName() + "$Staff");
        var ctor = staff.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object upper = ctor.newInstance(p.upperTop, p.upperTop + 4 * p.gap, p.gap),
                lower = ctor.newInstance(p.lowerTop, p.lowerTop + 4 * p.gap, p.gap);
        var count = staff.getDeclaredField("count");
        var index = staff.getDeclaredField("index");
        count.setAccessible(true);
        index.setAccessible(true);
        count.setInt(upper, sameSystem ? 2 : 1);
        count.setInt(lower, sameSystem ? 2 : 1);
        index.setInt(upper, 0);
        index.setInt(lower, sameSystem ? 1 : 0);
        var m =
                c.getDeclaredMethod(
                        "beamJunctionHeads",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        var removed = (List<?>) m.invoke(null, p.g, p.l, W, H, heads, List.of(upper, lower));
        for (Object h : removed) {
            var y = h.getClass().getDeclaredMethod("centerY");
            y.setAccessible(true);
            if (Math.abs((float) y.invoke(h) - p.capY) < 12) return true;
        }
        return false;
    }

    @Test
    public void longOwnedCrossStaffBeamCapIsNotAnotherHead() throws Exception {
        assertTrue(rejected(scene(false), true));
    }

    @Test
    public void ordinaryShortOwnedCapStillRejects() throws Exception {
        assertTrue(rejected(scene(true), true));
    }

    @Test
    public void differentAdmittedSystemsKeepTinyHead() throws Exception {
        assertFalse(rejected(scene(false), false));
    }

    @Test
    public void brokenMainShaftCannotOwnCap() throws Exception {
        var p = scene(false);
        for (int y = 130; y < 145; y++) p.g[y * W + 177] = (byte) 255;
        assertFalse(rejected(p, true));
    }

    @Test
    public void brokenPeerShaftCannotOwnCap() throws Exception {
        var p = scene(false);
        for (int y = 130; y < 145; y++) p.g[y * W + 121] = (byte) 255;
        assertFalse(rejected(p, true));
    }

    @Test
    public void brokenBeamCannotOwnCap() throws Exception {
        var p = scene(false);
        for (int x = 140; x < 157; x++) for (int y = 215; y < 236; y++) p.g[y * W + x] = (byte) 255;
        assertFalse(rejected(p, true));
    }

    @Test
    public void independentPrintedOvalAtCapIsPreserved() throws Exception {
        var p = scene(false);
        for (int y = 214; y <= 232; y++)
            for (int x = 165; x <= 184; x++)
                if (Math.pow((x - 174) / 10., 2) + Math.pow((y - 223) / 9., 2) <= 1)
                    p.g[y * W + x] = 0;
        assertFalse(rejected(p, true));
    }

    @Test
    public void missingMainRawBodyCannotOwnCap() throws Exception {
        var p = scene(false);
        for (int y = 72; y <= 88; y++) for (int x = 178; x <= 197; x++) p.g[y * W + x] = (byte) 255;
        assertFalse(rejected(p, true));
    }

    @Test
    public void smallPrintedGraceOvalAtCapIsPreserved() throws Exception {
        var p = scene(false);
        for (int y = 215; y <= 231; y++)
            for (int x = 166; x <= 182; x++)
                if (Math.pow((x - 174) / 6., 2) + Math.pow(((y - 223) + .4 * (x - 174)) / 3., 2)
                        <= 1) p.g[y * W + x] = 0;
        assertFalse(rejected(p, true));
    }

    @Test
    public void translatedLongOwnedCapStillRejects() throws Exception {
        var p = scene(false);
        var q = new Page();
        q.upperTop += 9;
        q.lowerTop += 9;
        q.capX += 7;
        q.capY += 9;
        for (int y = 0; y < H - 9; y++)
            for (int x = 0; x < W - 7; x++) {
                q.l[(y + 9) * W + x + 7] = p.l[y * W + x];
                q.g[(y + 9) * W + x + 7] = p.g[y * W + x];
            }
        assertTrue(rejected(q, true));
    }

    @Test
    public void mirroredUpstemCrossStaffCapStillRejects() throws Exception {
        var p = scene(false);
        var q = new Page();
        q.upperTop = H - 1 - (p.lowerTop + 4 * p.gap);
        q.lowerTop = H - 1 - (p.upperTop + 4 * p.gap);
        q.capX = W - 1 - p.capX;
        q.capY = H - 1 - p.capY;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                q.l[(H - 1 - y) * W + W - 1 - x] = p.l[y * W + x];
                q.g[(H - 1 - y) * W + W - 1 - x] = p.g[y * W + x];
            }
        assertTrue(rejected(q, true));
    }

    @Test
    public void missingPeerRawBodyCannotOwnCap() throws Exception {
        var p = scene(false);
        for (int y = 79; y <= 95; y++) for (int x = 122; x <= 141; x++) p.g[y * W + x] = (byte) 255;
        assertFalse(rejected(p, true));
    }

    @Test
    public void callerPixelsRemainUnchanged() throws Exception {
        var p = scene(false);
        var l = p.l.clone();
        var g = p.g.clone();
        rejected(p, true);
        assertArrayEquals(l, p.l);
        assertArrayEquals(g, p.g);
    }
}
