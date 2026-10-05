// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class Typed902CrossRowPortamentoTest {
    private static final int W = 1280, H = 900;

    private static List<CrossRowPortamento.Found> find(
            boolean out,
            boolean in,
            int sourceStep,
            int targetStep,
            boolean wrongSlope,
            boolean chord,
            int rails,
            String label,
            boolean sameRow,
            boolean tie,
            boolean curved,
            int kindMask) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        float gap = 14;
        for (int base : new int[] {256, 506})
            for (int line = 0; line < rails; line++)
                for (int x = 180; x < 1190; x++) gray[(base - line * 14) * W + x] = 30;
        float sy = 256 - sourceStep * 7, ty = 506 - targetStep * 7;
        int sign = sourceStep > targetStep ? 1 : -1;
        if (wrongSlope) sign = -sign;
        if (out)
            for (int x = 1081; x < 1168; x++) {
                float d = x - 1070;
                int y =
                        Math.round(
                                curved
                                        ? sy - 30 - 20 * (float) Math.sin(d / 98 * Math.PI)
                                        : sy + (sign > 0 ? 0 : 12) + d * .30f * sign);
                gray[y * W + x] = 30;
            }
        if (in)
            for (int x = 207; x < 249; x++) {
                float d = x - 260;
                int y =
                        Math.round(
                                curved
                                        ? ty - 30 - 15 * (float) Math.sin((d + 55) / 55 * Math.PI)
                                        : ty + (sign > 0 ? 0 : 12) + d * .30f * sign);
                gray[y * W + x] = 30;
            }
        for (int y = 135; y < 158; y++) for (int x = 1090; x < 1146; x++) gray[y * W + x] = 30;
        var a = new MeasureRegion(.65f, .92f, .2f, .30f);
        var b = new MeasureRegion(.15f, .5f, sameRow ? .2f : .45f, sameRow ? .30f : .57f);
        var n1 =
                new ScoreNoteEvent(
                                0,
                                (1070f / W - a.left()) / (a.right() - a.left()),
                                sourceStep,
                                0,
                                1,
                                sy / H,
                                false,
                                0,
                                0,
                                2,
                                1,
                                1,
                                0,
                                0)
                        .withKind(
                                (kindMask & 1) != 0
                                        ? ScoreNoteEvent.Kind.UNPITCHED
                                        : ScoreNoteEvent.Kind.PITCHED);
        var n2 =
                new ScoreNoteEvent(
                                1,
                                (260f / W - b.left()) / (b.right() - b.left()),
                                targetStep,
                                0,
                                1,
                                ty / H,
                                tie,
                                0,
                                0,
                                2,
                                3,
                                1,
                                0,
                                0)
                        .withKind(
                                (kindMask & 2) != 0
                                        ? ScoreNoteEvent.Kind.UNPITCHED
                                        : ScoreNoteEvent.Kind.PITCHED);
        var notes = new ArrayList<>(List.of(n1, n2));
        // A nearer occurrence is a physical barrier even when its beam crosses staves.
        if ((kindMask & 4) != 0)
            notes.add(
                    new ScoreNoteEvent(
                            0,
                            n1.positionInMeasure() + .05f,
                            sourceStep,
                            0,
                            1,
                            sy / H,
                            false,
                            0,
                            0,
                            2,
                            1,
                            1,
                            0,
                            0,
                            30,
                            (kindMask & 16) != 0,
                            0,
                            false,
                            0,
                            0,
                            1,
                            1,
                            ScoreNoteEvent.Kind.UNPITCHED));
        if ((kindMask & 8) != 0)
            notes.add(
                    new ScoreNoteEvent(
                            1,
                            n2.positionInMeasure() - .05f,
                            targetStep,
                            0,
                            1,
                            ty / H,
                            false,
                            0,
                            0,
                            2,
                            1,
                            1,
                            0,
                            0,
                            30,
                            (kindMask & 16) != 0,
                            0,
                            false,
                            0,
                            0,
                            1,
                            -1,
                            ScoreNoteEvent.Kind.UNPITCHED));

        if (chord)
            notes.add(
                    new ScoreNoteEvent(
                            0,
                            n1.positionInMeasure(),
                            sourceStep - 2,
                            0,
                            1,
                            (sy + 14) / H,
                            false,
                            0,
                            0,
                            2,
                            1,
                            1,
                            0,
                            0));
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(200, 256, 14, 0, 1),
                        new PlayingTechniqueDetector.Staff(450, 506, 14, 0, 1));
        var word =
                new PlayingTechniqueDetector.Word(label, 1090f / W, 135f / H, 1146f / W, 158f / H);
        var before = gray.clone();
        var result =
                CrossRowPortamento.find(gray, W, H, staffs, List.of(a, b), notes, List.of(word));
        assertArrayEquals(before, gray);
        return result;
    }

    private List<CrossRowPortamento.Found> found(int mask) {
        return find(true, true, 12, 9, false, false, 5, "port", false, false, false, mask);
    }

    @Test
    public void pitchedControlKeepsTheSameSourceIndices() {
        var r = found(0);
        assertEquals(1, r.size());
        assertEquals(0, r.get(0).sourceIndex());
        assertEquals(1, r.get(0).targetIndex());
    }

    @Test
    public void unpitchedSourceDoesNotProducePortamento() {
        assertTrue(found(1).isEmpty());
    }

    @Test
    public void unpitchedTargetDoesNotProducePortamento() {
        assertTrue(found(2).isEmpty());
    }

    @Test
    public void twoUnpitchedEndpointsDoNotProducePortamento() {
        assertTrue(found(3).isEmpty());
    }

    @Test
    public void nearerUnpitchedDepartureCannotBorrowFartherPitchedHead() {
        assertTrue(found(4).isEmpty());
    }

    @Test
    public void nearerUnpitchedArrivalCannotBorrowFartherPitchedHead() {
        assertTrue(found(8).isEmpty());
    }

    @Test
    public void crossStaffUnpitchedDepartureRemainsTemporalBarrier() {
        assertTrue(found(4 | 16).isEmpty());
    }

    @Test
    public void crossStaffUnpitchedArrivalRemainsTemporalBarrier() {
        assertTrue(found(8 | 16).isEmpty());
    }
}
