// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original independent quarter-bow versus approach roles; no score pixels or production copies. */
public class SmallPhraseApproachOriginalTest {
    private static final int W = 240, H = 200;

    private byte[] page(boolean curve, boolean pair) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int x = 8; x < 232; x++) for (int j = 0; j < 5; j++) gray[(115 + 12 * j) * W + x] = 0;
        // A finite thin quarter-bow, or a genuinely straight isolated approach.
        for (int x = 64; x <= 84; x++) {
            double t = (x - 64) / 20.0;
            int y = (int) Math.round(88 + 14 * (curve ? t * t : t));
            gray[y * W + x] = 0;
            gray[(y + 1) * W + x] = 0;
        }
        for (var n : heads(pair)) {
            int cx = Math.round(n.x()), cy = Math.round(n.y());
            for (int y = cy - 4; y <= cy + 4; y++)
                for (int x = cx - 6; x <= cx + 6; x++)
                    if ((x - cx) * (x - cx) / 36.0 + (y - cy) * (y - cy) / 16.0 <= 1)
                        gray[y * W + x] = 0;
            for (int y = cy; y <= cy + 38; y++) gray[y * W + cx - 6] = 0;
        }
        return gray;
    }

    private List<NoteSlideDetector.Head> heads(boolean pair) {
        var out = new ArrayList<NoteSlideDetector.Head>();
        if (pair) {
            out.add(new NoteSlideDetector.Head(60, 100, 12, 0, 0));
            out.add(new NoteSlideDetector.Head(88, 140, 12, 0, 0));
        }
        out.add(new NoteSlideDetector.Head(114, 100, 12, 0, 0));
        return out;
    }

    private int found(boolean curve, boolean pair) {
        var r =
                NoteSlideDetector.detect(
                        page(curve, pair),
                        W,
                        H,
                        List.of(new NoteSlideDetector.Staff(115, 163, 12)),
                        heads(pair));
        System.out.println("curve=" + curve + " pair=" + pair + " actual=" + r);
        return r.size();
    }

    @Test
    public void completeOrdinaryPhraseMustNotBecomeNextAttackApproach() {
        assertEquals(0, found(true, true));
    }

    @Test
    public void curvedOrphanApproachRemainsAdmitted() {
        assertEquals(1, found(true, false));
    }

    @Test
    public void straightApproachNearOtherNotesRemainsAdmitted() {
        assertEquals(1, found(false, true));
    }
}
