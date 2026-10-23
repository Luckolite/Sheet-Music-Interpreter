// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;

final class IncomingTieGridScenes {
    static final int W = 600, H = 330;

    static Class<?> type(String s) throws Exception {
        return Class.forName(OmrScoreInterpreter.class.getName() + "$" + s);
    }

    static Object make(String s, Object... a) throws Exception {
        var c = type(s).getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(a);
    }

    static Object note(int m, float x, float y, int beam, float duration) throws Exception {
        return note(m, x, y, beam, duration, 1);
    }

    static Object note(int m, float x, float y, int beam, float duration, int step)
            throws Exception {
        var e =
                new ScoreNoteEvent(
                        m,
                        m == 0 ? x / W : .1f,
                        step,
                        0,
                        1,
                        y / H,
                        false,
                        0,
                        beam,
                        2,
                        duration,
                        1,
                        0,
                        0,
                        30);
        return make(
                "DetectedNote",
                e,
                make("Component", 150, (int) x - 10, (int) x + 10, (int) y - 7, (int) y + 7, x, y),
                14f);
    }

    static void curve(byte[] g, byte[] l, int left, int span, float cy, float offset, float bow) {
        for (int x = left; x <= left + span; x++) {
            float t = (x - left) / (float) span, target = cy + offset + bow * 4 * t * (1 - t);
            for (int y = (int) Math.floor(target) - 2; y <= (int) Math.ceil(target) + 2; y++) {
                float distance = Math.abs(y - target);
                int value = Math.round(Math.min(1, Math.max(0, (distance - .75f) / 1.0f)) * 245);
                if (value < 245) {
                    g[y * W + x] = (byte) Math.min(g[y * W + x] & 255, value);
                    l[y * W + x] = 5;
                }
            }
        }
    }

    static byte[][] pixels(int clearance, int span, float bow) {
        byte[] g = new byte[W * H], l = new byte[W * H];
        Arrays.fill(g, (byte) 245);
        for (int top : new int[] {31, 191})
            for (int r = 0; r < 5; r++)
                for (int x = 25; x < W - 15; x++) {
                    g[(top + r * 14) * W + x] = 0;
                    l[(top + r * 14) * W + x] = 4;
                }
        curve(g, l, 452, 23, 80, 7, 5);
        curve(g, l, 170 - clearance - span, span, 240, 7, bow);
        int left = 170 - clearance - span;
        for (int x = left + Math.round(span * .65f) - 1;
                x <= left + Math.round(span * .65f) + 1;
                x++)
            for (int y = 247; y <= 255; y++) if ((g[y * W + x] & 255) < 225) l[y * W + x] = 2;
        return new byte[][] {l, g};
    }

    static boolean incoming(int c, int s, float b) throws Exception {
        var p = pixels(c, s, b);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasSystemEndTieArc",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        type("DetectedNote"),
                        boolean.class,
                        int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, p[0], p[1], W, H, note(1, 180, 240, 0, 2), false, 1);
    }

    static byte[][] lowContrastIncoming(byte[][] source, int clearance, int span, float bow) {
        byte[][] result = {source[0].clone(), source[1].clone()};
        int right = 170 - clearance;
        int left = right - span;
        for (int x = left; x <= right; x++) {
            float t = (x - left) / (float) span;
            float target = 240 + 7 + bow * 4 * t * (1 - t);
            for (int y = (int) Math.floor(target) - 10; y <= (int) Math.ceil(target) + 10; y++) {
                if (y < 0 || y >= H) continue;
                int at = y * W + x;
                if (result[0][at] != 2) result[1][at] = (byte) 160;
            }
            for (int y = (int) Math.floor(target) - 2; y <= (int) Math.ceil(target) + 2; y++) {
                if (y < 0 || y >= H) continue;
                int at = y * W + x;
                if (result[0][at] == 5) result[1][at] = (byte) 150;
            }
        }
        return result;
    }

    static int[] incomingStrongContrastSamples(
            byte[][] pixels, int clearance, int span, float bow) {
        int right = 170 - clearance, left = right - span, strong = 0, weakContrast = 0;
        for (int sample = 0; sample < 50; sample++) {
            float t = (sample + .5f) / 50f;
            int x = Math.round(left + t * span);
            float target = 240 + 7 + bow * 4 * t * (1 - t);
            int best = -1;
            float distance = Float.POSITIVE_INFINITY;
            for (int y = Math.round(target) - 2; y <= Math.round(target) + 2; y++) {
                int at = y * W + x;
                float currentDistance = Math.abs(y - target);
                if (pixels[0][at] != 2
                        && (pixels[1][at] & 255) <= 165
                        && currentDistance < distance) {
                    best = at;
                    distance = currentDistance;
                }
            }
            if (best < 0) continue;
            strong++;
            int y = best / W, shade = pixels[1][best] & 255, paper = 0;
            for (int offset : new int[] {3, 5, 9}) {
                paper = Math.max(paper, pixels[1][(y - offset) * W + x] & 255);
                paper = Math.max(paper, pixels[1][(y + offset) * W + x] & 255);
            }
            if (paper < shade + 20) weakContrast++;
        }
        return new int[] {strong, weakContrast};
    }
}
