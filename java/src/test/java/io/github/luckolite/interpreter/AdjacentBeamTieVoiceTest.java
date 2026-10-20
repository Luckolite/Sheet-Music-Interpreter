// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original held events with independently drawn finite shafts and one complete beam. */
public class AdjacentBeamTieVoiceTest {
    static final int W = 400, H = 220;
    final byte[] gray = new byte[W * H], labels = new byte[W * H];

    public AdjacentBeamTieVoiceTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void ink(int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = 0;
    }

    Object make(String name, Object... args) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + name)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(args);
    }

    Object note(int x, int pitch, float pos, boolean shared) throws Exception {
        int y = 80 - pitch * 7;
        ink(x - 7, y, x - 6, 145);
        if (shared) ink(x + 6, 20, x + 7, y);
        Object head = make("Component", 150, x - 7, x + 7, y - 6, y + 6, (float) x, (float) y);
        var e = new ScoreNoteEvent(0, pos, pitch, 0, 1, y / (float) H, false, 0, 0, 2, 2);
        return make("DetectedNote", e, head, 14f);
    }

    int run(int rail, boolean shared, boolean broken) throws Exception {
        var notes =
                List.of(
                        note(100, 0, .15f, false),
                        note(160, 1, .40f, shared),
                        note(210, 0, .65f, false));
        if (rail > 0) ink(153, 145 - rail + 1, 204, 145);
        if (broken)
            for (int y = 138; y <= 148; y++)
                for (int x = 176; x <= 180; x++) gray[y * W + x] = (byte) 255;
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
        return (int) m.invoke(null, notes, 2, W, labels, gray, H);
    }

    @Test
    public void completeSingleVoiceBeamPreventsOlderHeldEndpoint() throws Exception {
        assertEquals(-1, run(6, false, false));
    }

    @Test
    public void noBeamKeepsExistingSustainCandidate() throws Exception {
        assertEquals(0, run(0, false, false));
    }

    @Test
    public void thinRuleCannotChooseTheVoice() throws Exception {
        assertEquals(0, run(1, false, false));
    }

    @Test
    public void brokenRailCannotChooseTheVoice() throws Exception {
        assertEquals(0, run(6, false, true));
    }

    @Test
    public void sharedShaftColumnCannotChooseTheVoice() throws Exception {
        assertEquals(0, run(6, true, false));
    }

    @Test
    public void absentRawEvidenceKeepsExistingSustainCandidate() throws Exception {
        var notes =
                List.of(
                        note(100, 0, .15f, false),
                        note(160, 1, .40f, false),
                        note(210, 0, .65f, false));
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "previousSamePitch", List.class, int.class, int.class);
        m.setAccessible(true);
        assertEquals(0, (int) m.invoke(null, notes, 2, W));
    }
}
