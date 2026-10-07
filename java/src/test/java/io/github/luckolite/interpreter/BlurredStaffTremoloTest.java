// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original downward stem and a locally broadened rule crossing both stem wings. */
public class BlurredStaffTremoloTest {
    static final int W = 480, H = 240, G = 16, X = 220, TOP = 80;

    byte[] image(boolean real, boolean complete, int paper) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        for (int y = TOP; y <= TOP + 4 * G; y += G) {
            if (!complete && y < TOP + 2 * G) continue;
            for (int x = 20; x < W - 20; x++)
                for (int dy = -1; dy <= 1; dy++) p[(y + dy) * W + x] = 60;
        }
        int[] blur = {100, 80, 65, 80, 100};
        for (int x = X - 14; x <= X + 14; x++)
            for (int dy = -2; dy <= 2; dy++) p[(112 + dy) * W + x] = (byte) blur[dy + 2];
        for (int y = 82; y <= 142; y++) p[y * W + X] = 20;
        for (int y = 82; y <= 94; y++) for (int x = X; x <= X + 21; x++) p[y * W + x] = 20;
        if (real)
            for (int x = X - 13; x <= X + 13; x++)
                for (int dy = -2; dy <= 2; dy++)
                    p[(112 + Math.round((x - X) * .2f) + dy) * W + x] = 20;
        return p;
    }

    int[] counts(byte[] p) throws Exception {
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        ctor.setAccessible(true);
        Object head = ctor.newInstance(286, X, X + 21, 82, 94, 230f, 88f);
        var staffType = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var sc = staffType.getDeclaredConstructor(float.class, float.class, float.class);
        sc.setAccessible(true);
        Object staff = sc.newInstance((float) TOP, (float) (TOP + 4 * G), (float) G);
        Method m;
        Object frame;
        try {
            m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "tremoloStrokeCounts",
                            byte[].class,
                            int.class,
                            int.class,
                            c,
                            staffType,
                            List.class);
            frame = staff;
        } catch (NoSuchMethodException e) {
            m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "tremoloStrokeCounts",
                            byte[].class,
                            int.class,
                            int.class,
                            c,
                            float.class,
                            List.class);
            frame = (float) G;
        }
        m.setAccessible(true);
        return (int[]) m.invoke(null, p, W, H, head, frame, List.of(head));
    }

    @Test
    public void blurredPrintedRuleCannotBecomeTremolo() throws Exception {
        assertEquals(0, counts(image(false, true, 140))[0]);
    }

    @Test
    public void actualSlantedCrossStrokeIsPreserved() throws Exception {
        assertEquals(1, counts(image(true, true, 140))[0]);
    }

    @Test
    public void incompleteStaffCannotEraseTheStroke() throws Exception {
        assertEquals(1, counts(image(false, false, 140))[0]);
    }

    @Test
    public void pixelsAreNeverChanged() throws Exception {
        var p = image(false, true, 140);
        var old = p.clone();
        counts(p);
        assertArrayEquals(old, p);
    }

    int farBeams(boolean real, int paper) throws Exception {
        byte[] p = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        for (int y = 134; y <= 198; y++) p[y * W + X] = 20;
        for (int y = 134; y <= 146; y++) for (int x = X; x <= X + 21; x++) p[y * W + x] = 20;
        for (int cy : real ? new int[] {184, 198} : new int[] {8, 22, 36})
            for (int x = X - 35; x <= X + 35; x++)
                for (int dy = -2; dy <= 2; dy++) p[(cy + dy) * W + x] = 20;
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        ctor.setAccessible(true);
        Object head = ctor.newInstance(286, X, X + 21, 134, 146, 230f, 140f);
        var st = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var sc = st.getDeclaredConstructor(float.class, float.class, float.class);
        sc.setAccessible(true);
        Object staff = sc.newInstance(132f, 196f, 16f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "beamsBeyondTremolo",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        st);
        m.setAccessible(true);
        return (int) m.invoke(null, p, labels, W, H, head, staff);
    }

    @Test
    public void shadedPaperCannotExtendStemToForeignUpperBars() throws Exception {
        assertEquals(0, farBeams(false, 140));
    }

    @Test
    public void actualFarBeamsRemainVisibleOnShadedPaper() throws Exception {
        assertEquals(2, farBeams(true, 140));
    }

    @Test
    public void actualFarBeamsRemainVisibleOnBrightPaper() throws Exception {
        assertEquals(2, farBeams(true, 250));
    }
}
