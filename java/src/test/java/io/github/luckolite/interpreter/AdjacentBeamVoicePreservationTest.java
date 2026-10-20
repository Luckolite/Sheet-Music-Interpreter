// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdjacentBeamVoicePreservationTest {
    static final int W = 400, H = 220;
    byte[] g = new byte[W * H], labels = new byte[W * H];

    public AdjacentBeamVoicePreservationTest() {
        Arrays.fill(g, (byte) 255);
    }

    void ink(int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = 0;
    }

    Object make(String n, Object... a) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + n)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(a);
    }

    Object note(int x, int pitch, float pos, int direction) throws Exception {
        int y = 80 - pitch * 7;
        if (direction < 0) ink(x + 6, 20, x + 7, y);
        else ink(x - 7, y, x - 6, 145);
        var h = make("Component", 150, x - 7, x + 7, y - 6, y + 6, (float) x, (float) y);
        return make(
                "DetectedNote",
                new ScoreNoteEvent(0, pos, pitch, 0, 1, y / (float) H, false, 0, 0, 2, 2),
                h,
                14f);
    }

    int previous(List<Object> ns) throws Exception {
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
        return (int) m.invoke(null, ns, ns.size() - 1, W, labels, g, H);
    }

    @Test
    public void opposingOlderVoiceAndCurrentSimultaneousChordRemainEligible() throws Exception {
        Object old = note(100, 0, .15f, -1),
                before = note(160, 1, .4f, 1),
                sibling = note(197, -5, .65f, -1),
                now = note(210, 0, .65f, 1);
        ink(153, 140, 204, 145);
        assertEquals(0, previous(List.of(old, before, sibling, now)));
    }

    @Test
    public void precedingChordRepresentativeOrderCannotChangeEligibility() throws Exception {
        Object old = note(100, 0, .15f, -1),
                upper = note(146, -5, .4f, -1),
                lower = note(160, 1, .4f, 1),
                now = note(210, 0, .65f, 1);
        ink(153, 140, 204, 145);
        assertEquals(0, previous(List.of(old, upper, lower, now)));
        assertEquals(0, previous(List.of(old, lower, upper, now)));
    }

    @Test
    public void independentTieVoiceProofRemainsAvailable() throws Exception {
        var ns = List.of(note(100, 0, .15f, 1), note(160, 1, .4f, -1), note(210, 0, .65f, 1));
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "independentTieVoice",
                        List.class,
                        int.class,
                        int.class,
                        byte[].class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        assertEquals(true, m.invoke(null, ns, 0, 2, g, W, H));
        assertEquals(0, previous(ns));
    }
}
