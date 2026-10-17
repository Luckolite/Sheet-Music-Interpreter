// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class UpperLedgerAdversarialProbe {
    static final int W = 640, H = 600;

    static class Page {
        byte[] g = new byte[W * H], l = new byte[W * H];

        Page(String kind) {
            Arrays.fill(g, (byte) 255);
            for (int top : new int[] {135, 455})
                for (int i = 0; i < 5; i++) line(20, 620, top + i * 16, 1, 4);
            for (int i = 1; i <= 5; i++) {
                if (kind.equals("missing" + i)) continue;
                line(130, 170, 455 - i * 16, 1, 5);
            }
            for (int y : new int[] {375, 399, 423}) head(150, y);
            for (int y = 375; y <= 479; y++)
                for (int x = 139; x <= 140; x++) {
                    g[y * W + x] = 0;
                    if (l[y * W + x] != 2) l[y * W + x] = 1;
                }
            head(450, 487);
            line(439, 440, 487, 33, 1);
            head(450, 167);
            line(439, 440, 167, 33, 1);
            if (kind.equals("neighbor")) {
                for (int i = 0; i < 5; i++) line(20, 620, 311 + i * 16, 1, 4);
                head(470, 343);
                line(459, 460, 343, 33, 1);
            }
            if (kind.startsWith("broad")) {
                int side = kind.equals("broadLeft") ? 0 : kind.equals("broadRight") ? 1 : 2;
                for (int y = 391; y <= 407; y++)
                    for (int x = (side == 1 ? 162 : 130); x <= (side == 0 ? 138 : 170); x++) {
                        g[y * W + x] = 0;
                        l[y * W + x] = 0;
                    }
            }
            if (kind.equals("longRule")) line(110, 190, 407, 1, 5);
            if (kind.equals("shiftedRail")) {
                erase(130, 170, 407, 407);
                line(130, 170, 413, 1, 5);
            }
            if (kind.equals("disconnectedFlanks")) {
                erase(130, 141, 407, 407);
                erase(159, 170, 407, 407);
            }
            if (kind.equals("glyphPlus") || kind.equals("glyphBox") || kind.equals("glyphFork")) {
                erase(138, 162, 367, 383);
                for (int y = 367; y <= 383; y++) for (int x = 138; x <= 162; x++) l[y * W + x] = 0;
                if (kind.equals("glyphPlus")) {
                    line(140, 160, 374, 3, 2);
                    line(149, 151, 368, 15, 2);
                }
                if (kind.equals("glyphBox")) {
                    line(140, 160, 368, 2, 2);
                    line(140, 160, 381, 2, 2);
                    line(140, 141, 368, 15, 2);
                    line(159, 160, 368, 15, 2);
                }
                if (kind.equals("glyphFork")) {
                    line(140, 160, 374, 3, 2);
                    line(140, 142, 368, 7, 2);
                    line(158, 160, 368, 7, 2);
                }
            }
        }

        void erase(int a, int b, int c, int d) {
            for (int y = c; y <= d; y++)
                for (int x = a; x <= b; x++) {
                    g[y * W + x] = (byte) 255;
                    l[y * W + x] = 0;
                }
        }

        void line(int a, int b, int c, int h, int label) {
            for (int y = c; y < c + h; y++)
                for (int x = a; x <= b; x++) {
                    g[y * W + x] = 0;
                    l[y * W + x] = (byte) label;
                }
        }

        void head(int cx, int cy) {
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = cx - 11; x <= cx + 11; x++)
                    if (Math.pow((x - cx) / 11d, 2) + Math.pow((y - cy) / 7d, 2) <= 1) {
                        g[y * W + x] = 0;
                        l[y * W + x] = 2;
                    }
        }

        void run(String k) {
            var notes =
                    OmrScoreInterpreter.extract(
                            l,
                            g,
                            W,
                            H,
                            List.of(
                                    new MeasureRegion(.03f, .97f, .15f, .38f),
                                    new MeasureRegion(.03f, .97f, .46f, .90f)));
            System.out.print(k + " ");
            for (var n : notes)
                System.out.print(n.measureIndex() + ":" + n.staffStep() + ":" + n.pageY() + " ");
            System.out.println();
        }
    }

    public static void main(String[] args) {
        for (String k :
                new String[] {
                    "missing1",
                    "missing3",
                    "missing4",
                    "missing5",
                    "neighbor",
                    "broadLeft",
                    "broadRight",
                    "broadBoth",
                    "longRule",
                    "shiftedRail",
                    "disconnectedFlanks",
                    "glyphPlus",
                    "glyphBox",
                    "glyphFork"
                }) new Page(k).run(k);
    }
}
