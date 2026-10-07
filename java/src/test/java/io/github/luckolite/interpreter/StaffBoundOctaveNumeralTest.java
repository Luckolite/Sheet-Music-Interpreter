// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Original offset natural spines crossed by a rule, beside interrupted horizontal ink. */
public class StaffBoundOctaveNumeralTest {
    private static final int W = 320, H = 320;

    private static byte[] natural(int shade) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 90; y <= 110; y++) gray[y * W + 57] = (byte) shade;
        for (int y = 96; y <= 116; y++) gray[y * W + 64] = (byte) shade;
        for (int x = 57; x <= 64; x++) {
            gray[96 * W + x] = (byte) shade;
            gray[106 * W + x] = (byte) shade;
        }
        // A staff rule partitions the natural's counter into two apparent holes.
        for (int x = 52; x <= 69; x++) gray[101 * W + x] = (byte) shade;
        for (int x = 76; x < 210; x += 8)
            for (int xx = x; xx < x + 3; xx++) gray[93 * W + xx] = (byte) shade;
        return gray;
    }

    private static byte[] eight(int top) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int cy : new int[] {top + 5, top + 14})
            for (int y = cy - 5; y <= cy + 5; y++)
                for (int x = 55; x <= 65; x++) {
                    double r = Math.pow((x - 60) / 5d, 2) + Math.pow((y - cy) / 5d, 2);
                    if (r <= 1.1 && r >= .3) gray[y * W + x] = 0;
                }
        for (int x = 76; x < 210; x += 8)
            for (int xx = x; xx < x + 3; xx++) gray[(top + 3) * W + xx] = 0;
        return gray;
    }

    private static List<ScoreNoteEvent> apply(
            byte[] gray, List<PlayingTechniqueDetector.Staff> staffs, int noteY) {
        var note = new ScoreNoteEvent(0, .4f, 2, 0, 1, noteY / (float) H, false, 0, 0, 2, 1);
        return OctaveMarkDetector.apply(
                List.of(),
                staffs,
                List.of(new MeasureRegion(0, 1, 0, 1)),
                List.of(note),
                gray,
                W,
                H);
    }

    private static List<PlayingTechniqueDetector.Staff> overlapping() {
        return List.of(
                new PlayingTechniqueDetector.Staff(80, 128, 12, 0, 1),
                new PlayingTechniqueDetector.Staff(150, 198, 12, 0, 1));
    }

    @Test
    public void naturalInsideUpperStaffCannotRaiseTheLowerStaff() {
        assertEquals(0, apply(natural(0), overlapping(), 174).get(0).octaveShift());
    }

    @Test
    public void numeralShapeInsideUpperStaffCannotBecomeALowerDirection() {
        assertEquals(0, apply(eight(90), overlapping(), 174).get(0).octaveShift());
    }

    @Test
    public void fadedNaturalStillBelongsToItsPrintedStaff() {
        assertEquals(0, apply(natural(120), overlapping(), 174).get(0).octaveShift());
    }

    @Test
    public void realBareUpperDirectionAboveBothStaffsSurvives() {
        assertEquals(1, apply(eight(30), overlapping(), 105).get(0).octaveShift());
    }

    @Test
    public void realDirectionBetweenStaffsSurvives() {
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(50, 98, 12, 0, 1),
                        new PlayingTechniqueDetector.Staff(220, 268, 12, 0, 1));
        assertEquals(1, apply(eight(155), staffs, 244).get(0).octaveShift());
    }

    @Test
    public void realBareLowerDirectionBelowAllStaffsSurvives() {
        assertEquals(-1, apply(eight(230), overlapping(), 174).get(0).octaveShift());
    }

    @Test
    public void suppliedCompleteOctaveWordsKeepTheirExistingContract() {
        var note = new ScoreNoteEvent(0, .4f, 2, 0, 1, 174f / H, false, 0, 0, 2, 1);
        var word = new PlayingTechniqueDetector.Word("8va", .15f, .29f, .5f, .35f);
        var result =
                OctaveMarkDetector.apply(
                        List.of(word),
                        overlapping(),
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        List.of(note),
                        null,
                        W,
                        H);
        assertEquals(1, result.get(0).octaveShift());
    }

    @Test
    public void sourcePixelsRemainUnchanged() {
        byte[] gray = natural(0), copy = gray.clone();
        apply(gray, overlapping(), 174);
        assertArrayEquals(copy, gray);
    }
}
