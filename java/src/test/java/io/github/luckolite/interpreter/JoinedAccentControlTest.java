// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic geometry, without score-derived pixels. */
public class JoinedAccentControlTest {
    static final int W = 1000, H = 500;

    byte[] paper() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        return p;
    }

    void line(byte[] p, int x0, int y0, int x1, int y1) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) Math.max(1, n));
            int y = y0 + Math.round((y1 - y0) * i / (float) Math.max(1, n));
            p[y * W + x] = 0;
        }
    }

    void rules(byte[] p) {
        for (int y = 68; y <= 116; y += 12) line(p, 30, y, 970, y);
    }

    void chevron(byte[] p, boolean reverse) {
        for (int d = 0; d < 2; d++) {
            line(p, reverse ? 112 : 90, 80 + d, reverse ? 90 : 112, 84 + d);
            line(p, reverse ? 90 : 112, 84 + d, reverse ? 112 : 90, 88 + d);
        }
    }

    int accent(byte[] p, byte[] labels) {
        return NoteArticulationDetector.detect(
                        labels,
                        p,
                        W,
                        H,
                        List.of(new NoteArticulationDetector.Anchor(100, 110, 12, 0)))[0]
                & NoteArticulation.ACCENT;
    }

    @Test
    public void recoversChevronTouchingUpperRule() {
        byte[] p = paper();
        rules(p);
        chevron(p, false);
        assertEquals(NoteArticulation.ACCENT, accent(p, new byte[W * H]));
    }

    @Test
    public void preservesIsolatedChevron() {
        byte[] p = paper();
        chevron(p, false);
        assertEquals(NoteArticulation.ACCENT, accent(p, new byte[W * H]));
    }

    @Test
    public void rejectsReverseChevron() {
        byte[] p = paper();
        rules(p);
        chevron(p, true);
        assertEquals(0, accent(p, new byte[W * H]));
    }

    @Test
    public void rejectsSingleArm() {
        byte[] p = paper();
        rules(p);
        line(p, 90, 80, 112, 85);
        assertEquals(0, accent(p, new byte[W * H]));
    }

    @Test
    public void rejectsBeamStemJunction() {
        byte[] p = paper();
        rules(p);
        line(p, 90, 80, 112, 85);
        line(p, 112, 85, 112, 115);
        assertEquals(0, accent(p, new byte[W * H]));
    }

    @Test
    public void rejectsSemanticNotehead() {
        byte[] p = paper();
        rules(p);
        chevron(p, false);
        byte[] labels = new byte[W * H];
        for (int y = 81; y <= 89; y++)
            for (int x = 90; x <= 112; x++)
                if (p[y * W + x] == 0) labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
        assertEquals(0, accent(p, labels));
    }

    @Test
    public void rejectsStaffOnly() {
        byte[] p = paper();
        rules(p);
        assertEquals(0, accent(p, new byte[W * H]));
    }

    @Test
    public void recoversLowerRuleJoinedChevron() {
        byte[] p = paper();
        rules(p);
        byte[] shape = paper();
        chevron(shape, false);
        for (int y = 80; y <= 89; y++)
            for (int x = 90; x <= 112; x++) if (shape[y * W + x] == 0) p[(184 - y) * W + x] = 0;
        int mark =
                NoteArticulationDetector.detect(
                        new byte[W * H],
                        p,
                        W,
                        H,
                        List.of(new NoteArticulationDetector.Anchor(100, 74, 12, 0)))[0];
        assertEquals(NoteArticulation.ACCENT, mark & NoteArticulation.ACCENT);
    }

    @Test
    public void doesNotPropagateToDifferentStaff() {
        byte[] p = paper();
        rules(p);
        chevron(p, false);
        int[] marks =
                NoteArticulationDetector.detect(
                        new byte[W * H],
                        p,
                        W,
                        H,
                        List.of(
                                new NoteArticulationDetector.Anchor(100, 110, 12, 0),
                                new NoteArticulationDetector.Anchor(100, 300, 12, 1)));
        assertEquals(NoteArticulation.ACCENT, marks[0] & NoteArticulation.ACCENT);
        assertEquals(0, marks[1] & NoteArticulation.ACCENT);
    }

    @Test
    public void rejectsLetterE() {
        byte[] p = paper();
        rules(p);
        line(p, 90, 80, 90, 89);
        line(p, 90, 80, 112, 80);
        line(p, 90, 84, 108, 84);
        line(p, 90, 89, 112, 89);
        assertEquals(0, accent(p, new byte[W * H]));
    }

    @Test
    public void rejectsCrossedArms() {
        byte[] p = paper();
        rules(p);
        line(p, 90, 80, 112, 89);
        line(p, 90, 89, 112, 80);
        assertEquals(0, accent(p, new byte[W * H]));
    }
}
