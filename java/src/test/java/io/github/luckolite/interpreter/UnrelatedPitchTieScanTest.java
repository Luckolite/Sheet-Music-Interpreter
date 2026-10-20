// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-voice endpoint eligibility; no score pixels or decoder fixture data. */
public class UnrelatedPitchTieScanTest {
    static final int W = 400, H = 220;
    byte[] gray = new byte[W * H], labels = new byte[W * H];

    public UnrelatedPitchTieScanTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void ink(int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = 0;
    }

    Object make(String n, Object... a) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + n)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(a);
    }

    Object note(int x, int pitch, float pos, int direction, boolean held) throws Exception {
        int y = 80 - pitch * 7;
        for (int dy = -6; dy <= 6; dy++)
            for (int dx = -7; dx <= 7; dx++) {
                double q = dx * dx / 49d + dy * dy / 36d;
                if (q <= 1 && (!held || q >= .45)) ink(x + dx, y + dy, x + dx, y + dy);
            }
        if (direction < 0) ink(x + 6, 20, x + 7, y);
        else ink(x - 7, y, x - 6, 145);
        var h = make("Component", 150, x - 7, x + 7, y - 6, y + 6, (float) x, (float) y);
        var e =
                new ScoreNoteEvent(
                        0,
                        pos,
                        pitch,
                        0,
                        1,
                        y / (float) H,
                        false,
                        0,
                        held ? 0 : 1,
                        2,
                        held ? 2 : 0);
        return make("DetectedNote", e, h, 14f);
    }

    List<Object> scene(boolean decoy) throws Exception {
        // The held upper voice runs from .15 to .65 (two written quarter beats).
        // The lower voice reaches the same pitch on its second beamed eighth.
        Object held = note(100, 0, .15f, -1, true),
                before = note(160, 1, .525f, 1, false),
                now = note(210, 0, .65f, 1, false);
        Object other = decoy ? note(130, 2, .4f, 1, false) : null;
        ink(153, 140, 204, 145);
        // A returning curve can connect the held head to the convergence note. This
        // endpoint selector must leave actual arc acceptance to its existing caller.
        for (int x = 107; x <= 203; x++) {
            float t = (x - 107) / 96f;
            int y = Math.round(85 + 20 * 4 * t * (1 - t));
            ink(x, y, x, y + 1);
        }
        return decoy ? List.of(held, other, before, now) : List.of(held, before, now);
    }

    int previous(List<Object> n) throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "previousSamePitch",
                        List.class,
                        int.class,
                        int.class,
                        byte[].class,
                        byte[].class,
                        int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, n, n.size() - 1, W, labels, gray, H);
    }

    @Test
    public void heldOpposingEndpointRemainsEligibleWithoutOtherOlderPitch() throws Exception {
        assertEquals(0, previous(scene(false)));
    }

    @Test
    public void unrelatedOlderPitchCannotStopScanBeforeHeldMatchingEndpoint() throws Exception {
        assertEquals(0, previous(scene(true)));
    }
}
