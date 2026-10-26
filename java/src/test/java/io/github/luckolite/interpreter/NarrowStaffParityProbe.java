// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import java.security.*;

/** Original procedural valid/invalid inputs; old/new process output compared externally. */
public class NarrowStaffParityProbe {
    record Case(
            String name, byte[] gray, int width, int height, float top, float bottom, float gap) {}

    static byte[] image(int width, int height, float top, float gap, boolean bend) {
        byte[] g = new byte[width * height];
        Arrays.fill(g, (byte) 235);
        for (int x = 0; x < width; x++)
            for (int line = 0; line < 5; line++) {
                float t = x / (float) Math.max(1, width - 1);
                int y = Math.round(top + line * gap - (bend ? 8 * t * t : 0));
                if (y >= 0 && y < height) g[y * width + x] = 45;
            }
        return g;
    }

    static String sha(byte[] b) throws Exception {
        return b == null
                ? "null"
                : HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    public static void main(String[] args) throws Exception {
        var list = new ArrayList<Case>();
        for (float gap : new float[] {3, 14.25f, 32}) {
            int at = Math.max(80, Math.round(gap * 10));
            for (int width : new int[] {at - 1, at, at + 1})
                for (boolean bend : new boolean[] {false, true}) {
                    int height = (int) Math.ceil(128 + 4 * gap);
                    list.add(
                            new Case(
                                    "boundary-g" + gap + "-w" + width + "-bend" + bend,
                                    image(width, height, 64, gap, bend),
                                    width,
                                    height,
                                    64,
                                    64 + 4 * gap,
                                    gap));
                }
        }
        for (float gap : new float[] {8, 16}) {
            int width = Math.max(80, Math.round(gap * 10));
            int height = (int) Math.ceil(128 + 4 * gap);
            list.add(
                    new Case(
                            "exact-g" + gap,
                            image(width, height, 64, gap, true),
                            width,
                            height,
                            64,
                            64 + 4 * gap,
                            gap));
        }
        int w = 1200, h = 350;
        byte[] curved = new byte[w * h];
        Arrays.fill(curved, (byte) 230);
        for (int x = 30; x < w - 30; x++)
            for (int line = 0; line < 5; line++) {
                int y = Math.round(210f - 45f * x * x / (w * (float) w) - line * 10);
                curved[y * w + x] = 50;
                curved[(y + 1) * w + x] = 50;
            }
        list.add(new Case("wide-positive-curvature", curved, w, h, 155, 195, 10));
        byte[] g = image(80, 200, 60, 12, false);
        list.add(new Case("null-gray", null, 80, 200, 60, 108, 12));
        list.add(new Case("short-gray", new byte[1], 80, 200, 60, 108, 12));
        list.add(new Case("zero-width", new byte[0], 0, 200, 60, 108, 12));
        list.add(new Case("negative-width", new byte[0], -1, 200, 60, 108, 12));
        list.add(new Case("zero-height", new byte[0], 80, 0, 60, 108, 12));
        list.add(new Case("negative-height", new byte[0], 80, -1, 60, 108, 12));
        for (float value :
                new float[] {
                    Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1, 0, 2.999f
                }) list.add(new Case("gap-" + value, g, 80, 200, 60, 108, value));
        for (float value :
                new float[] {
                    Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1, 0, 108, 250
                }) list.add(new Case("top-" + value, g, 80, 200, value, 108, 12));
        for (float value :
                new float[] {
                    Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1, 0, 60, 250
                }) list.add(new Case("bottom-" + value, g, 80, 200, 60, value, 12));
        var method =
                StaffPitchTrack.class.getDeclaredMethod(
                        "detect",
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        boolean.class,
                        boolean.class);
        method.setAccessible(true);
        int count = 0, tracks = 0, exceptions = 0;
        for (var c : list)
            for (int mode = 0; mode < 4; mode++) {
                String before = sha(c.gray);
                String result;
                try {
                    var track =
                            (StaffPitchTrack)
                                    method.invoke(
                                            null,
                                            c.gray,
                                            c.width,
                                            c.height,
                                            c.top,
                                            c.bottom,
                                            c.gap,
                                            (mode & 1) != 0,
                                            (mode & 2) != 0);
                    if (track == null) result = "NULL";
                    else {
                        tracks++;
                        var b = new StringBuilder("TRACK");
                        for (float x :
                                new float[] {
                                    0, c.width * .25f, c.width * .5f, c.width * .75f, c.width - 1
                                })
                            for (float n : track.at(x))
                                b.append('/').append(Float.floatToRawIntBits(n));
                        result = b.toString();
                    }
                } catch (InvocationTargetException error) {
                    var e = error.getCause();
                    exceptions++;
                    result = "THROW:" + e.getClass().getName() + ":" + e.getMessage();
                }
                if (!before.equals(sha(c.gray)))
                    throw new AssertionError("input mutation " + c.name);
                System.out.println((count++) + "\t" + c.name + "\tmode=" + mode + "\t" + result);
            }
        System.out.println(
                "FINISHED cases=" + count + " tracks=" + tracks + " exceptions=" + exceptions);
    }
}
