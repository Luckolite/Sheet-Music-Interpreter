// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original reconciliation-to-detector control; glyph acceptances are explicit input contracts. */
public class PaddedFpBoundsOriginalTest {
    @Test
    public void paddedOcrOwnsQualificationButAcceptedGlyphUnionOwnsAdmissionBounds() {
        int width = 1000, height = 400;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int y = 138; y < 146; y++) {
            for (int x = 488; x < 494; x++) gray[y * width + x] = 0;
            for (int x = 506; x < 512; x++) gray[y * width + x] = 0;
        }
        var literal = new PlayingTechniqueDetector.Word("fp", .485f, .30f, .515f, .41f);
        var words = ScoreDynamicsDetector.ocrWords("fp", literal, gray, width, height);
        assertEquals(List.of(literal), words);
        var f = new PlayingTechniqueDetector.Word("f", .485f, .34f, .504f, .37f);
        var p = new PlayingTechniqueDetector.Word("p", .496f, .34f, .515f, .37f);
        assertTrue((literal.bottom() - literal.top()) * height > 3 * 10);
        var evidence = new CompoundDynamicEvidence(words);
        evidence.accepted("f", f, f);
        evidence.accepted("p", p, p);
        var refined = new ArrayList<>(List.of(p));
        evidence.finish(refined);
        assertEquals(1, refined.size());
        var compound = refined.get(0);
        assertEquals(.485f, compound.left(), 0);
        assertEquals(.34f, compound.top(), 0);
        assertEquals(.515f, compound.right(), 0);
        assertEquals(.37f, compound.bottom(), 0);
        assertEquals(.30f, literal.top(), 0);
        assertEquals(.41f, literal.bottom(), 0);
        var staffs = List.of(new PlayingTechniqueDetector.Staff(80, 120, 10, 0, 1));
        var measures = List.of(new MeasureRegion(.1f, .9f, .1f, .4f));
        var notes =
                List.of(
                        new ScoreNoteEvent(
                                0,
                                .5f,
                                2,
                                0,
                                1,
                                .2f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1f));
        assertTrue(
                ScoreDynamicsDetector.detect(words, staffs, measures, notes, null, width, height)
                        .isEmpty());
        var changes =
                ScoreDynamicsDetector.detect(refined, staffs, measures, notes, null, width, height);
        assertEquals(2, changes.size());
        assertEquals(3f, changes.get(0).decibels(), 0);
        assertEquals(-8f, changes.get(1).decibels(), 0);
        for (var change : changes) {
            assertEquals(0, change.measureIndex());
            assertEquals(.5f, change.positionInMeasure(), 0);
            assertEquals(0, change.staffIndex());
            assertEquals(1, change.staffCount());
        }
        var repeated = new ArrayList<>(refined);
        new CompoundDynamicEvidence(refined).finish(repeated);
        assertEquals(refined, repeated);
    }
}
