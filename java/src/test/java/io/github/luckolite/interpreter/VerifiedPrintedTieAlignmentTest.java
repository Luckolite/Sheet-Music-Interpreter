// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original short beamed pair with measured perspective slope and independent raw bow. */
public class VerifiedPrintedTieAlignmentTest {
    static final int W = 800, H = 400, STEP = 5, AX = 150, BX = 230;

    static StaffPitchTrack track(boolean verified) {
        return verified
                ? StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 250, 20}, new float[] {600, 340, 20}))
                : StaffPitchTrack.linear(W, 250, 20, .15f);
    }

    static float y(StaffPitchTrack t, float x) {
        float[] f = t.at(x);
        return f[0] - STEP * f[1] * .5f;
    }

    static Object make(String name, Object... args) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + name)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(args);
    }

    static Object note(float position, int x, float y, int step) throws Exception {
        var e = new ScoreNoteEvent(0, position, step, 0, 1, y / H, false, 0, 1, 2, 0, 1, 0, 0, 30);
        return make(
                "DetectedNote",
                e,
                make(
                        "Component",
                        100,
                        x - 8,
                        x + 8,
                        Math.round(y) - 5,
                        Math.round(y) + 5,
                        (float) x,
                        y),
                20f);
    }

    static int candidate(boolean bow, boolean verified, boolean wrongPhase, boolean unlikePitch)
            throws Exception {
        var t = track(verified);
        float ay = y(t, AX), by = y(t, BX) + (wrongPhase ? 8 : 0);
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 210);
        for (int x = 0; x < W; x++)
            for (int rule = 0; rule < 5; rule++) {
                int yy = Math.round(t.at(x)[0] - 20 * rule);
                gray[yy * W + x] = 40;
                labels[yy * W + x] = 4;
            }
        if (bow)
            for (int x = AX + 8; x <= BX - 8; x++) {
                float q = (x - AX - 8) / (float) (BX - AX - 16);
                int yy = Math.round(y(t, x) - 20 * (.5f + .5f * 4 * q * (1 - q)));
                for (int d = -1; d <= 1; d++) {
                    gray[(yy + d) * W + x] = 30;
                    labels[(yy + d) * W + x] = 5;
                }
            }
        Object staff = make("Staff", 170f, 250f, 20f);
        var field = staff.getClass().getDeclaredField("pitchTrack");
        field.setAccessible(true);
        field.set(staff, t);
        var notes =
                List.of(note(.3f, AX, ay, STEP), note(.6f, BX, by, unlikePitch ? STEP + 1 : STEP));
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "previousSamePitch",
                        List.class,
                        int.class,
                        int.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        List.class);
        method.setAccessible(true);
        return (int) method.invoke(null, notes, 1, W, labels, gray, H, List.of(staff));
    }

    @Test
    public void provedShortBowSelectsTheSlopedPrintedPredecessor() throws Exception {
        assertEquals(0, candidate(true, true, false, false));
    }

    @Test
    public void matchingRulePhaseAloneCannotSelectTheBeamedPredecessor() throws Exception {
        assertEquals(-1, candidate(false, true, false, false));
    }

    @Test
    public void unverifiedSlopeCannotOverrideTheRawGeometryGuard() throws Exception {
        assertEquals(-1, candidate(true, false, false, false));
    }

    @Test
    public void anArcCannotRepairDisagreeingPrintedPhase() throws Exception {
        assertEquals(-1, candidate(true, true, true, false));
    }

    @Test
    public void differentWrittenPitchCannotShareTheArc() throws Exception {
        assertEquals(-1, candidate(true, true, false, true));
    }
}
