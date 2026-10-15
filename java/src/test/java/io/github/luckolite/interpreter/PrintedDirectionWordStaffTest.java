// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic partially missing rails and direction box. */
public class PrintedDirectionWordStaffTest {
    static final int W = 600, H = 350;
    static final List<PlayingTechniqueDetector.Staff> STAFFS =
            List.of(new PlayingTechniqueDetector.Staff(144, 200, 14, 0, 1));
    static final List<MeasureRegion> BARS = List.of(new MeasureRegion(.2f, .9f, .35f, .65f));
    static final List<ScoreNoteEvent> NOTES =
            List.of(new ScoreNoteEvent(0, 100f / 420, 0, 0, 1, 200f / H, false, 0, 0, 2, 1));

    static PlayingTechniqueDetector.Word word(float left, float right) {
        return new PlayingTechniqueDetector.Word("rit.", left / W, 220f / H, right / W, 234f / H);
    }

    static byte[] ink(int rails, int end) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int line = 0; line < rails; line++)
            for (int x = 222; x < end; x++) g[(200 - line * 14) * W + x] = 30;
        return g;
    }

    static PlayingTechniqueDetector.Staff find(
            byte[] g, boolean head, PlayingTechniqueDetector.Word word) {
        return PrintedDirectionWordStaff.find(
                STAFFS, BARS, head ? NOTES : List.of(), g, W, H, word);
    }

    @Test
    public void twoInteriorProofsRecoverMissingLeftRailEvidence() {
        byte[] g = ink(5, 580);
        assertNull(PrintedDirectionStaff.at(STAFFS, BARS, NOTES, g, W, H, 220, 220, 234));
        assertNotNull(find(g, true, word(220, 300)));
    }

    @Test
    public void fourRailsCannotOwnDirection() {
        assertNull(find(ink(4, 580), true, word(220, 300)));
    }

    @Test
    public void missingHeadCannotOwnDirection() {
        assertNull(find(ink(5, 580), false, word(220, 300)));
    }

    @Test
    public void centerAloneIsInsufficient() {
        assertNull(find(ink(5, 285), true, word(220, 300)));
    }

    @Test
    public void longCaptionCannotBorrowRemoteStaffProof() {
        assertNull(find(ink(5, 580), true, word(220, 400)));
    }

    @Test
    public void doesNotAlterSource() {
        byte[] g = ink(5, 580), copy = g.clone();
        find(g, true, word(220, 300));
        assertArrayEquals(copy, g);
    }

    @Test
    public void restoredDirectionKeepsLeftHandMusicalAnchor() {
        var score =
                new ScorePageInterpretation(
                        BARS,
                        NOTES,
                        1,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 4, 4)),
                        List.of(),
                        List.of(),
                        List.of());
        var result =
                ScoreExpressionDetector.apply(
                        score, List.of(word(220, 300)), STAFFS, ink(5, 580), W, H);
        assertEquals(1, result.expressiveEvents().size());
        byte[] complete = ink(5, 580);
        for (int line = 0; line < 5; line++)
            for (int x = 100; x < 222; x++) complete[(200 - line * 14) * W + x] = 30;
        var reference =
                ScoreExpressionDetector.apply(
                        score, List.of(word(220, 300)), STAFFS, complete, W, H);
        assertEquals(
                reference.expressiveEvents().get(0).start(),
                result.expressiveEvents().get(0).start());
        assertEquals(NOTES, result.notes());
    }
}
