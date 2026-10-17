// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original literal written-capacity controls; no meter fill or score raster. */
public class PartialWrittenCapacityTest {
    static final int W = 1000, H = 300;
    byte[] gray = new byte[W * H];

    static MeasureRegion bar(float a, float b) {
        return new MeasureRegion(a, b, .2f, .8f);
    }

    List<MeasureRegion> raw =
            List.of(bar(.1f, .3f), bar(.31f, .5f), bar(.51f, .7f), bar(.71f, .9f));
    List<MeasureRegion> fitted =
            List.of(
                    bar(.1f, .198f),
                    bar(.202f, .3f),
                    bar(.31f, .5f),
                    bar(.51f, .7f),
                    bar(.71f, .9f));
    List<ScoreNoteEvent> notes = new ArrayList<>();
    List<ScoreRestEvent> rests = new ArrayList<>();

    static ScoreNoteEvent note(int m, float p, int beam, float duration, float rest, int art) {
        return new ScoreNoteEvent(
                m, p, 0, 0, 1, .45f, false, 0, beam, 2, duration, 1, rest, art, 30, false, 0, false,
                0, 0, 1, 1);
    }

    public PartialWrittenCapacityTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y = 100; y <= 180; y += 20) for (int x = 90; x <= 910; x++) gray[y * W + x] = 0;
        for (int x : new int[] {305, 505, 705}) line(x);
        for (int m = 0; m < 4; m++)
            for (float p : new float[] {.2f, .5f, .8f}) notes.add(note(m, p, 0, 1, 0, 0));
    }

    void line(int x) {
        for (int y = 100; y <= 180; y++) gray[y * W + x] = 0;
    }

    List<MeasureRegion> result() {
        return PrintedMeasureRhythmGuard.preservePartialWrittenBars(
                raw, fitted, notes, rests, gray, W, H);
    }

    void literalTail() {
        notes.removeIf(n -> n.measureIndex() == 0);
        notes.add(note(0, .1f, 2, 0, 0, 32768));
        notes.add(note(0, .2f, 2, 0, 0, 32768));
        notes.add(note(0, .3f, 1, 0, 2.5f, 0));
        rests.add(new ScoreRestEvent(0, .4f, .5f, .03f, 0, 1, .5));
        rests.add(new ScoreRestEvent(0, .6f, .5f, .03f, 0, 1, 1));
        rests.add(new ScoreRestEvent(0, .8f, .5f, .03f, 0, 1, 1));
    }

    @Test
    public void independentlyCompleteQuarterRunRestoresOnlyItsBar() throws Exception {
        assertEquals(raw, result());
    }

    @Test
    public void explicitGraceAndOwnedLiteralRestTailHaveThreeWrittenBeats() throws Exception {
        literalTail();
        assertEquals(raw, result());
    }

    @Test
    public void derivedGapWithoutMatchingLiteralRestsIsNotCapacity() throws Exception {
        literalTail();
        rests.clear();
        assertEquals(fitted, result());
    }

    @Test
    public void fullMeasurePlaceholderCannotEstablishCapacity() throws Exception {
        literalTail();
        rests.clear();
        rests.add(ScoreRestEvent.fullMeasure(0, .6f, .5f, .03f, 0, 1));
        assertEquals(fitted, result());
    }

    @Test
    public void explicitTupletClockIsNotAnOrdinaryCapacity() throws Exception {
        notes.set(0, notes.get(0).withTupletRatio(3, 2));
        assertEquals(fitted, result());
    }

    @Test
    public void conflictingUntouchedCapacitiesKeepFitting() throws Exception {
        notes.set(6, note(2, .2f, 0, 2, 0, 0));
        assertEquals(fitted, result());
    }

    @Test
    public void unknownWrittenDurationKeepsFitting() throws Exception {
        notes.set(0, note(0, .2f, 0, 0, 0, 0));
        assertEquals(fitted, result());
    }

    @Test
    public void unknownShaftLaneKeepsFitting() throws Exception {
        notes.set(0, notes.get(0).withStemDirection(0));
        assertEquals(fitted, result());
    }

    @Test
    public void unequalSimultaneousHeldVoiceIsAmbiguous() throws Exception {
        notes.add(note(0, .2f, 0, 2, 0, 0));
        assertEquals(fitted, result());
    }

    @Test
    public void genuineAdditionalPrintedRailKeepsItsSplit() throws Exception {
        line(200);
        assertEquals(fitted, result());
    }

    @Test
    public void incompleteOriginalSeparatorKeepsFitting() throws Exception {
        for (int y = 121; y < 140; y++) if (y % 20 != 0) gray[y * W + 505] = (byte) 255;
        assertEquals(fitted, result());
    }

    @Test
    public void oneReferenceBarIsInsufficient() throws Exception {
        fitted =
                List.of(
                        bar(.1f, .198f),
                        bar(.202f, .3f),
                        bar(.31f, .398f),
                        bar(.402f, .5f),
                        bar(.51f, .598f),
                        bar(.602f, .7f),
                        bar(.71f, .9f));
        assertEquals(fitted, result());
    }

    @Test
    public void grayOnlyPartialInflationHasNoWrittenProof() {
        assertEquals(
                fitted,
                PrintedMeasureRhythmGuard.preserveCountNeutralRows(raw, fitted, gray, W, H));
    }

    @Test
    public void capacityProofDoesNotMutateRasterOrNotes() throws Exception {
        literalTail();
        byte[] before = gray.clone();
        var saved = List.copyOf(notes);
        var savedRests = List.copyOf(rests);
        result();
        assertArrayEquals(before, gray);
        assertEquals(saved, notes);
        assertEquals(savedRests, rests);
    }
}
