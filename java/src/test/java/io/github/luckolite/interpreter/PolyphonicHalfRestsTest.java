// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural rectangle, ruled paper and an independently beamed lower voice. */
public class PolyphonicHalfRestsTest {
    static final int W = 640, H = 400;

    static class Page {
        final byte[] gray = new byte[W * H];
        final boolean rectangle, owner;
        final List<ScoreNoteEvent> extraNotes = new ArrayList<>();

        Page(boolean rectangle, boolean owner) {
            this.rectangle = rectangle;
            this.owner = owner;
            Arrays.fill(gray, (byte) 245);
            for (int y = 100; y <= 164; y += 16) box(30, y, W - 30, y);
            if (rectangle) box(300, 93, 320, 99);
            if (owner) {
                oval(310, 108, 11, 7);
                oval(392, 124, 11, 7);
                box(319, 52, 320, 108);
                box(401, 52, 402, 124);
                box(319, 52, 402, 55);
            }
        }

        void box(int l, int t, int r, int b) {
            for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = 0;
        }

        void oval(int cx, int cy, int rx, int ry) {
            for (int y = cy - ry; y <= cy + ry; y++)
                for (int x = cx - rx; x <= cx + rx; x++)
                    if (Math.pow((x - cx) / (double) rx, 2) + Math.pow((y - cy) / (double) ry, 2)
                            <= 1) gray[y * W + x] = 0;
        }

        List<ScoreRestEvent> detect() {
            List<ScoreNoteEvent> notes =
                    new ArrayList<>(
                            owner
                                    ? List.of(
                                            new ScoreNoteEvent(
                                                            0, 310f / W, 7, 0, 1, 108f / H, false,
                                                            0, 1)
                                                    .withStemDirection(1),
                                            new ScoreNoteEvent(
                                                            0, 392f / W, 5, 0, 1, 124f / H, false,
                                                            0, 1)
                                                    .withStemDirection(1))
                                    : List.of());
            notes.addAll(extraNotes);
            return SixteenthRestDetector.detect(
                    gray,
                    W,
                    H,
                    List.of(new MeasureRegion(0, 1, .1f, .9f)),
                    List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                    notes);
        }

        List<ScoreRestEvent> target() {
            var result = new ArrayList<ScoreRestEvent>();
            for (var rest : detect())
                if (Math.abs(rest.positionInMeasure() - 310f / W) < .035f
                        && Math.abs(rest.pageY() - 96f / H) < .045f) result.add(rest);
            return result;
        }
    }

    @Test
    public void independentHalfRectangleSurvivesLowerBeamedHeadAndShaft() {
        var rests = new Page(true, true).target();
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(2, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void isolatedDisplacedHalfRectangleStillWorks() {
        var rests = new Page(true, false).target();
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(2, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void beamedHeadAloneDoesNotInventTheOtherVoiceRest() {
        assertTrue(new Page(false, true).target().isEmpty());
    }

    @Test
    public void thinTenutoAboveTheHeadIsNotAHalfRest() {
        var page = new Page(false, true);
        page.box(300, 98, 320, 99);
        assertTrue(page.target().isEmpty());
    }

    @Test
    public void interpretationPreservesOriginalInk() {
        var page = new Page(true, true);
        var before = page.gray.clone();
        page.detect();
        assertArrayEquals(before, page.gray);
    }

    @Test
    public void unlabelledOvalOnTheRuleIsNotAnIndependentRest() {
        var page = new Page(false, true);
        page.oval(310, 96, 11, 7);
        assertTrue(page.target().isEmpty());
    }

    @Test
    public void anotherWrittenHeadCannotSupplyTheRectangle() {
        var page = new Page(false, true);
        page.oval(310, 96, 11, 7);
        page.extraNotes.add(new ScoreNoteEvent(0, 310f / W, 9, 0, 1, 96f / H, false, 0, 1));
        assertTrue(page.target().isEmpty());
    }

    @Test
    public void supportingRuleMustContinueOnBothSides() {
        var page = new Page(true, true);
        for (int x = 270; x < 300; x++) page.gray[100 * W + x] = (byte) 245;
        assertTrue(page.target().isEmpty());
    }

    @Test
    public void excessiveSolidHeightDoesNotBecomeAHalfRest() {
        var page = new Page(true, true);
        page.box(300, 84, 320, 99);
        assertTrue(page.target().isEmpty());
    }

    @Test
    public void backwardSecondaryBeamIsNotTheOtherVoiceHalfRest() {
        var page = new Page(true, true);
        page.box(250, 78, 320, 83);
        assertTrue(page.target().isEmpty());
    }

    @Test
    public void independentPlateWithOnlyForwardPrimaryBeamSurvives() {
        var page = new Page(true, true);
        page.box(319, 78, 390, 83);
        assertEquals(1, page.target().size());
    }

    @Test
    public void independentSittingPlateTouchingTheHeadAboveSurvives() {
        var page = new Page(false, false);
        page.box(300, 109, 320, 115);
        page.oval(310, 103, 11, 7);
        page.box(319, 52, 320, 103);
        page.box(319, 52, 402, 55);
        page.extraNotes.add(
                new ScoreNoteEvent(0, 310f / W, 8, 0, 1, 103f / H, false, 0, 1)
                        .withStemDirection(1));
        var rests = page.target();
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(2, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void independentSittingPlateTouchingTheHeadCloseAboveSurvives() {
        var page = new Page(false, false);
        page.box(300, 109, 320, 115);
        page.oval(310, 105, 11, 7);
        page.box(319, 52, 320, 105);
        page.box(319, 52, 402, 55);
        page.extraNotes.add(
                new ScoreNoteEvent(0, 310f / W, 8, 0, 1, 105f / H, false, 0, 1)
                        .withStemDirection(1));
        var rests = page.target();
        assertEquals(rests.toString(), 1, rests.size());
        assertEquals(2, rests.get(0).durationBeats(), 0);
    }

    @Test
    public void croppedUpperEdgeOfASecondWrittenHeadIsNotARest() {
        var page = new Page(false, true);
        page.oval(310, 121, 11, 7);
        page.extraNotes.add(new ScoreNoteEvent(0, 310f / W, 5, 0, 1, 121f / H, false, 0, 1));
        assertTrue(page.target().isEmpty());
    }

    @Test
    public void croppedUpperEdgeOfItsOwnWrittenHeadIsNotARest() {
        var page = new Page(false, false);
        page.oval(310, 119, 11, 7);
        page.box(300, 119, 301, 172);
        page.extraNotes.add(
                new ScoreNoteEvent(0, 310f / W, 5, 0, 1, 119f / H, false, 0, 1)
                        .withStemDirection(-1));
        assertTrue(page.target().isEmpty());
    }
}
