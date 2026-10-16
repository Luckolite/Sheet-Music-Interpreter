// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;

/** Original procedural shafts, hooks, beams, and staff crossings. */
public class SharedBareStemTest {
    @org.junit.Test
    public void originalRecognitionControls() throws Exception {
        main(new String[0]);
    }

    static final int W = 240, H = 240;

    static boolean check(int gap, int direction, boolean rules, int branch) throws Exception {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        int cx = 120, cy = 120, x = cx - direction * gap / 2, end = cy + direction * gap * 3;
        for (int y = Math.min(cy, end); y <= Math.max(cy, end); y++) gray[y * W + x] = 0;
        if (rules)
            for (int dy = -2; dy <= 2; dy++)
                for (int xx = 0; xx < W; xx++) gray[(cy + dy * gap) * W + xx] = 0;
        if (branch > 0)
            for (int dy = 0; dy < gap; dy++)
                for (int dx = 0; dx <= gap; dx++) {
                    if (branch == 2 || dx <= Math.max(2, gap - dy))
                        gray[(end - direction * dy) * W + x + dx] = 0;
                }
        Class<?> cls =
                Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component");
        Constructor<?> constructor = cls.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object head =
                constructor.newInstance(
                        80, cx - gap / 2, cx + gap / 2, cy - gap / 3, cy + gap / 3, (float) cx,
                        (float) cy);
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "sharedBareStemClear",
                        byte[].class,
                        int.class,
                        int.class,
                        cls,
                        float.class,
                        int[].class);
        m.setAccessible(true);
        return (boolean)
                m.invoke(null, gray, W, H, head, (float) gap, new int[] {x, end, direction});
    }

    public static void main(String[] args) throws Exception {
        int count = 0;
        for (int gap : new int[] {8, 12, 18, 24})
            for (int direction : new int[] {-1, 1})
                for (boolean rules : new boolean[] {false, true})
                    for (int branch = 0; branch < 3; branch++) {
                        boolean actual = check(gap, direction, rules, branch);
                        if (actual != (branch == 0))
                            throw new AssertionError(
                                    "gap="
                                            + gap
                                            + " direction="
                                            + direction
                                            + " rules="
                                            + rules
                                            + " branch="
                                            + branch
                                            + " actual="
                                            + actual);
                        count++;
                    }
        System.out.println(count + " original bare-stem/flag/beam checks passed");
    }
}
