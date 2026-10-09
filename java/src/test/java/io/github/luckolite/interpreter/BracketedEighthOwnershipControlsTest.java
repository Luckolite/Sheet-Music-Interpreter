// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural checks require physical beams and consistent voice evidence. */
public final class BracketedEighthOwnershipControlsTest {
    static final int W = BracketedLowerEighthRestTest.W, H = BracketedLowerEighthRestTest.H;

    static BracketedLowerEighthRestTest.Page page() {
        return new BracketedLowerEighthRestTest.Page(true, true, true, false);
    }

    static List<ScoreNoteEvent> notes() {
        return List.of(note(255, 128, 0, -1), note(335, 112, 0, -1));
    }

    static ScoreNoteEvent note(int x, int y, int staff, int direction) {
        return new ScoreNoteEvent(0, x / (float) W, 2, staff, 1, y / (float) H, false, 0, 1)
                .withStemDirection(direction);
    }

    static List<ScoreRestEvent> read(BracketedLowerEighthRestTest.Page p, List<ScoreNoteEvent> n) {
        return SixteenthRestDetector.detect(
                        p.gray,
                        W,
                        H,
                        List.of(new MeasureRegion(0, 1, .1f, .9f)),
                        List.of(new SixteenthRestDetector.Staff(80, 144, 16, 0, 1)),
                        n)
                .stream()
                .filter(
                        r ->
                                Math.abs(r.positionInMeasure() - 180f / W) < .03f
                                        && r.pageY() > 150f / H)
                .toList();
    }

    static void erase(
            BracketedLowerEighthRestTest.Page p, int left, int top, int right, int bottom) {
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) p.gray[y * W + x] = (byte) 255;
    }

    @Test
    public void beamMetadataWithoutPrintedBeamIsInsufficient() {
        var p = page();
        erase(p, 245, 174, 327, 183);
        assertTrue(read(p, notes()).isEmpty());
    }

    @Test
    public void aBrokenBeamCannotProveOwnership() {
        var p = page();
        erase(p, 280, 170, 290, 183);
        assertTrue(read(p, notes()).isEmpty());
    }

    @Test
    public void disconnectedFirstShaftCannotProveOwnership() {
        var p = page();
        erase(p, 245, 139, 247, 153);
        assertTrue(read(p, notes()).isEmpty());
    }

    @Test
    public void aThickBracketCannotReplaceTheAttachedBeam() {
        var p = page();
        erase(p, 245, 174, 327, 183);
        p.line(173, 206, 360, 206, 5);
        assertTrue(read(p, notes()).isEmpty());
    }

    @Test
    public void oneRecognizedHeadIsInsufficient() {
        assertTrue(read(page(), List.of(notes().get(0))).isEmpty());
    }

    @Test
    public void anotherStaffCannotSupplyTheProof() {
        assertTrue(read(page(), List.of(note(255, 128, 1, -1), note(335, 112, 1, -1))).isEmpty());
    }

    @Test
    public void oppositeStemMetadataCannotSupplyTheProof() {
        assertTrue(read(page(), List.of(note(255, 128, 0, 1), note(335, 112, 0, 1))).isEmpty());
    }

    @Test
    public void crossStaffPairCannotSupplyTheProof() {
        assertTrue(
                read(page(), notes().stream().map(ScoreNoteEvent::withCrossStaffBeam).toList())
                        .isEmpty());
    }

    @Test
    public void gracePairCannotSupplyTheProof() {
        assertTrue(
                read(
                                page(),
                                notes().stream()
                                        .map(n -> n.withArticulations(NoteOrnament.GRACE))
                                        .toList())
                        .isEmpty());
    }

    @Test
    public void aPrecedingAttachedPairAlsoProvesTheRest() {
        var p = page();
        erase(p, 230, 100, 350, 183);
        p.oval(50, 128, 11, 7);
        p.oval(130, 112, 11, 7);
        p.line(40, 128, 40, 178, 2);
        p.line(120, 112, 120, 174, 2);
        p.line(40, 178, 120, 174, 5);
        var result = read(p, List.of(note(50, 128, 0, -1), note(130, 112, 0, -1)));
        assertEquals(result.toString(), 1, result.size());
        assertEquals(.5, result.get(0).durationBeats(), 0);
    }
}
