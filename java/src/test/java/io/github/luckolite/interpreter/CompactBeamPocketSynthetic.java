// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Original synthetic rails, rule crossings and independently owned ovals; no score pixels. */
public class CompactBeamPocketSynthetic {
    static final int W = 360, H = 260;
    byte[] gray = new byte[W * H];
    int[] a = {76, 170, 1}, b = {132, 146, 1};
    int left = 78, right = 93, top = 153, bottom = 166;
    float cx = 85.5f, cy = 159.5f, gap = 16;

    CompactBeamPocketSynthetic() {
        Arrays.fill(gray, (byte) 255);
        for (int x = 76; x <= 132; x++) {
            int y = Math.round(167 - 24f * (x - 76) / 56);
            for (int d = -3; d <= 3; d++) gray[(y + d) * W + x] = 35;
            if (x <= 99) for (int d = -3; d <= 3; d++) gray[(y - 16 + d) * W + x] = 35;
        }
        for (int y = 120; y <= 170; y++) gray[y * W + 76] = 35;
        for (int y = 94; y <= 146; y++) gray[y * W + 132] = 35;
        oval(85, 120, 11, 8, false);
        oval(141, 94, 11, 8, false);
        for (int y : new int[] {105, 121, 137, 153, 169})
            for (int x = 15; x < 330; x++) gray[y * W + x] = 35;
    }

    void oval(int x0, int y0, int rx, int ry, boolean hollow) {
        for (int y = y0 - ry; y <= y0 + ry; y++)
            for (int x = x0 - rx; x <= x0 + rx; x++) {
                double z =
                        (x - x0) * (x - x0) / (double) (rx * rx)
                                + (y - y0) * (y - y0) / (double) (ry * ry);
                if (z <= 1) gray[y * W + x] = (byte) (hollow && z < .5 ? 255 : 35);
            }
    }

    boolean proof() {
        return PartialBeamHeadContour.provedMergedOrCompactPocket(
                true, gray, W, H, a, b, cx, cy, left, top, right, bottom, 180, gap);
    }

    Object target() throws Exception {
        return component(180, left, right, top, bottom, cx, cy);
    }

    static Object component(int area, int l, int r, int t, int b, float x, float y)
            throws Exception {
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var k = c.getDeclaredConstructors()[0];
        k.setAccessible(true);
        return k.newInstance(area, l, r, t, b, x, y);
    }

    boolean consumer(int owners, boolean raw, boolean splitStaff) throws Exception {
        Object target = target();
        var heads = new ArrayList<>();
        heads.add(target);
        if (owners > 0) heads.add(component(275, 74, 96, 112, 128, 85, 120));
        if (owners > 1) heads.add(component(275, 130, 152, 86, 102, 141, 94));
        Class<?> st = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var ctor = st.getDeclaredConstructor(float.class, float.class, float.class);
        ctor.setAccessible(true);
        var staffs = new ArrayList<>();
        staffs.add(ctor.newInstance(105f, 169f, 16f));
        if (splitStaff) staffs.add(ctor.newInstance(30f, 94f, 16f));
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "mergedPartialBeamMaskHeads",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        return ((List<?>) m.invoke(null, raw ? gray : null, W, H, heads, staffs)).contains(target);
    }

    boolean ownShaft() throws Exception {
        for (var m : OmrScoreInterpreter.class.getDeclaredMethods())
            if (m.getName().equals("attachedRawStem") && m.getParameterCount() == 5) {
                m.setAccessible(true);
                return m.invoke(null, gray, W, H, target(), gap) != null;
            }
        throw new AssertionError();
    }

    static void check(boolean yes, String text) {
        if (!yes) throw new AssertionError(text);
        System.out.println("PASS " + text);
    }

    public static void main(String[] args) throws Exception {
        var f = new CompactBeamPocketSynthetic();
        System.out.println("proof=" + f.proof() + " consumer=" + f.consumer(2, true, false));
        check(f.proof(), "two owned raw rails cross compact paper pocket");
        check(f.consumer(2, true, false), "compact semantic pocket rejected by consumer");
        check(!f.consumer(1, true, false), "one owner preserves candidate");
        check(!f.consumer(2, false, false), "semantic-only input preserved");
        f = new CompactBeamPocketSynthetic();
        f.oval(86, 159, 9, 6, true);
        check(!f.proof(), "independent true hollow contour preserved");
        check(!f.consumer(2, true, false), "true hollow with staff overlap preserved by consumer");
        f = new CompactBeamPocketSynthetic();
        for (int x = 108; x < 117; x++)
            for (int y = 145; y < 164; y++) f.gray[y * W + x] = (byte) 255;
        check(!f.proof(), "broken primary support preserves candidate");
        f = new CompactBeamPocketSynthetic();
        f.b[2] = -1;
        check(!f.proof(), "opposed cross-staff shaft direction preserved");
        f = new CompactBeamPocketSynthetic();
        check(!f.consumer(2, true, true), "different staff owner cannot supply second shaft");
        f = new CompactBeamPocketSynthetic();
        f.oval(86, 159, 8, 6, true);
        for (int y = 159; y <= 220; y++) f.gray[y * W + 78] = 35;
        check(f.ownShaft(), "true hollow raw shaft observed");
        check(!f.consumer(2, true, false), "unflagged independently shaft-owned hollow preserved");
        f = new CompactBeamPocketSynthetic();
        f.oval(86, 159, 8, 6, true);
        for (int y = 159; y <= 220; y++) f.gray[y * W + 78] = 35;
        for (int y = 153; y < 157; y++)
            for (int x = 80; x < 86; x++) f.gray[y * W + x] = (byte) 255;
        check(f.ownShaft(), "partial hollow raw shaft observed");
        check(
                !f.consumer(2, true, false),
                "partly missing hollow contour with own shaft preserved");
        f = new CompactBeamPocketSynthetic();
        for (int y = 110; y < 176; y++)
            for (int x = 70; x < 138; x++) f.gray[y * W + x] = (byte) 255;
        f.oval(86, 159, 8, 6, true);
        check(!f.consumer(2, true, false), "nearby voice without raw shared rail preserved");
        f = new CompactBeamPocketSynthetic();
        f.b[0] = f.a[0];
        check(!f.proof(), "duplicated shaft cannot supply two owners");
    }
}
