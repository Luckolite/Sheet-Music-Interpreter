// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

final class PaleTripleShaftFixture {
    static final int W = 280, H = 260;
    final byte[] gray = new byte[W * H], labels = new byte[W * H];
    final int tip, shade, beams, kind;

    PaleTripleShaftFixture(int tip, int shade, int beams, int kind) {
        this.tip = tip;
        this.shade = shade;
        this.beams = beams;
        this.kind = kind;
        Arrays.fill(gray, (byte) 250);
        for (int x : new int[] {100, 132})
            for (int y = kind == 3 && x == 132 ? 30 : (x == 100 ? 70 : 77);
                    y <= (kind == 3 && x == 132 ? 77 : tip + 24);
                    y++) {
                labels[y * W + x] = 1;
                gray[y * W + x] = (byte) shade;
            }
        for (int beam = 0; beam < beams; beam++)
            for (int x = 100; x <= 132; x++)
                for (int dy = 0; dy < 6; dy++) {
                    if (kind == 1 && beam < beams - 1 && x < 116
                            || kind == 2 && x >= 113 && x <= 119) continue;
                    int y = tip + beam * 9 + dy;
                    labels[y * W + x] = 5;
                    gray[y * W + x] = 25;
                }
        for (int index = 0; index < 2; index++) {
            int cx = index == 0 ? 110 : 142, cy = index == 0 ? 70 : 77;
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if (Math.pow((x - cx) / 10d, 2) + Math.pow((y - cy) / 7d, 2) <= 1) {
                        labels[y * W + x] = 2;
                        gray[y * W + x] = 80;
                    }
        }
    }

    int decoded() throws Exception {
        var root = OmrScoreInterpreter.class;
        var c = Class.forName(root.getName() + "$Component");
        var hc =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        hc.setAccessible(true);
        Object a = hc.newInstance(210, 100, 120, 63, 77, 110f, 70f),
                b = hc.newInstance(210, 132, 152, 70, 84, 142f, 77f);
        var sc = Class.forName(root.getName() + "$Staff");
        var ctor = sc.getDeclaredConstructor(float.class, float.class, float.class);
        ctor.setAccessible(true);
        Object staff = ctor.newInstance(90f, 146f, 14f);
        var method =
                root.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        sc,
                        List.class);
        method.setAccessible(true);
        return (int) method.invoke(null, labels, gray, W, H, a, staff, List.of(a, b));
    }
}
