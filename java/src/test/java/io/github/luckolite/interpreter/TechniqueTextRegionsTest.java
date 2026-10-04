// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural written-head and annotation geometry. */
public class TechniqueTextRegionsTest {
    private static final List<MeasureRegion> MEASURES =
            List.of(new MeasureRegion(.1f, .8f, .19f, .31f));
    private static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(200, 240, 10, 0, 1);

    private static ScoreNoteEvent note(float position, int step, float floor) {
        return new ScoreNoteEvent(
                0,
                position,
                step,
                0,
                1,
                (floor - step * 5) / 1000,
                false,
                0,
                0,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                1,
                0,
                0,
                0,
                ScoreNoteEvent.CLEF_TREBLE);
    }

    private static List<ScoreNoteEvent> tilted() {
        return List.of(note(.1f, 2, 263), note(.4f, 5, 258.8f), note(.7f, 8, 254.6f));
    }

    @Test
    public void independentWrittenHeadsRecoverALocalStaffAndBoundedStrip() {
        var regions = TechniqueTextRegions.above(List.of(STAFF), MEASURES, tilted(), 1000, 1000);
        assertEquals(1, regions.size());
        var r = regions.get(0);
        assertEquals(-.02f, r.slope(), .00001f);
        assertEquals(263, r.floor(170), .001f);
        assertTrue(r.left() >= 0 && r.right() <= 1000 && r.top() < r.bottom());
        assertEquals(223, TechniqueTextRegions.local(STAFF, 170, regions).top(), .001f);
    }

    @Test
    public void twoHeadsAndSameColumnChordCannotEstablishASlope() {
        assertTrue(
                TechniqueTextRegions.above(
                                List.of(STAFF), MEASURES, tilted().subList(0, 2), 1000, 1000)
                        .isEmpty());
        assertTrue(
                TechniqueTextRegions.above(
                                List.of(STAFF),
                                MEASURES,
                                List.of(note(.3f, 2, 250), note(.3f, 5, 250), note(.3f, 8, 250)),
                                1000,
                                1000)
                        .isEmpty());
    }

    @Test
    public void ConflictingWrittenPositionsDoNotMoveTheFrame() {
        var notes = List.of(note(.1f, 2, 248), note(.4f, 5, 259), note(.7f, 8, 248));
        assertTrue(
                TechniqueTextRegions.above(List.of(STAFF), MEASURES, notes, 1000, 1000).isEmpty());
    }

    @Test
    public void distantRegisterAndCrossStaffHeadsAreExcluded() {
        assertTrue(
                TechniqueTextRegions.above(
                                List.of(STAFF),
                                MEASURES,
                                List.of(note(.1f, 2, 280), note(.4f, 5, 280), note(.7f, 8, 280)),
                                1000,
                                1000)
                        .isEmpty());
        var notes = List.of(tilted().get(0), tilted().get(1), tilted().get(2).withCrossStaffBeam());
        assertTrue(
                TechniqueTextRegions.above(List.of(STAFF), MEASURES, notes, 1000, 1000).isEmpty());
    }

    @Test
    public void emptyOrOutOfRegionEvidencePreservesOriginalFrame() {
        assertEquals(STAFF, TechniqueTextRegions.local(STAFF, 900, List.of()));
        var regions = TechniqueTextRegions.above(List.of(STAFF), MEASURES, tilted(), 1000, 1000);
        assertEquals(STAFF, TechniqueTextRegions.local(STAFF, 900, regions));
    }

    private static OcrText reading(String lineText, String token, OcrText.Box box) {
        var line =
                new OcrText.Line(
                        lineText, box, List.of(new OcrText.Element(token, box, List.of())));
        return new OcrText(lineText, List.of(new OcrText.Block(lineText, box, List.of(line))));
    }

    @Test
    public void twoScalesMustAgreeOnACompleteWordAtTheSamePrintedLocation() {
        var region =
                TechniqueTextRegions.above(List.of(STAFF), MEASURES, tilted(), 1000, 1000).get(0);
        var a =
                TechniqueTextRegions.readings(
                        reading("pizz.", "pizz.", new OcrText.Box(20, 20, 50, 35)),
                        region,
                        1,
                        1000,
                        1000);
        var b =
                TechniqueTextRegions.readings(
                        reading("PIZZ", "PIZZ", new OcrText.Box(40, 40, 100, 70)),
                        region,
                        2,
                        1000,
                        1000);
        assertEquals(a, TechniqueTextRegions.consensus(a, b, 10, 1000, 1000));
        var wrong =
                TechniqueTextRegions.readings(
                        reading("arco", "arco", new OcrText.Box(40, 40, 100, 70)),
                        region,
                        2,
                        1000,
                        1000);
        assertTrue(TechniqueTextRegions.consensus(a, wrong, 10, 1000, 1000).isEmpty());
        var other =
                TechniqueTextRegions.readings(
                        reading("pizz", "pizz", new OcrText.Box(100, 40, 160, 70)),
                        region,
                        2,
                        1000,
                        1000);
        assertTrue(TechniqueTextRegions.consensus(a, other, 10, 1000, 1000).isEmpty());
    }

    @Test
    public void negationMissingBoxesAndClippedOrPartialWordsAreRejected() {
        var region =
                TechniqueTextRegions.above(List.of(STAFF), MEASURES, tilted(), 1000, 1000).get(0);
        assertTrue(
                TechniqueTextRegions.readings(
                                reading("non pizz.", "pizz.", new OcrText.Box(20, 20, 50, 35)),
                                region,
                                1,
                                1000,
                                1000)
                        .isEmpty());
        assertTrue(
                TechniqueTextRegions.readings(
                                reading("piz", "piz", new OcrText.Box(20, 20, 50, 35)),
                                region,
                                1,
                                1000,
                                1000)
                        .isEmpty());
        assertTrue(
                TechniqueTextRegions.readings(reading("pizz", "pizz", null), region, 1, 1000, 1000)
                        .isEmpty());
        assertTrue(
                TechniqueTextRegions.readings(
                                reading("pizz", "pizz", new OcrText.Box(-5, 20, 50, 35)),
                                region,
                                1,
                                1000,
                                1000)
                        .isEmpty());
    }

    private static final PlayingTechniqueDetector.Staff COLUMN_STAFF =
            new PlayingTechniqueDetector.Staff(128, 192, 16, 0, 1);
    private static final List<MeasureRegion> COLUMN_MEASURES =
            List.of(new MeasureRegion(.125f, .875f, .125f, .375f));

    private static ScoreNoteEvent horizontalColumnHead(float position) {
        return new ScoreNoteEvent(
                0,
                position,
                0,
                0,
                1,
                .203125f,
                false,
                0,
                0,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                1,
                0,
                0,
                0,
                ScoreNoteEvent.CLEF_TREBLE);
    }

    private static TechniqueTextRegions.Region horizontalColumnRegion() {
        return new TechniqueTextRegions.Region(
                0, COLUMN_STAFF, 116, 73, 908, 152, 512, 208, 0, 128, 896);
    }

    @Test
    public void manyChordHeadsRetainTheCompleteHorizontalRegion() {
        var notes = new java.util.ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 96; i++)
            notes.add(horizontalColumnHead(new float[] {0, .5f, 1}[i % 3]));
        var before = List.copyOf(notes);
        var regions =
                TechniqueTextRegions.above(
                        List.of(COLUMN_STAFF), COLUMN_MEASURES, notes, 1024, 1024);
        assertEquals(List.of(horizontalColumnRegion()), regions);
        assertEquals(
                new PlayingTechniqueDetector.Staff(144, 208, 16, 0, 1),
                TechniqueTextRegions.local(COLUMN_STAFF, 512, regions));
        assertEquals(before, notes);
    }

    @Test
    public void theLastThirdColumnStillEstablishesTheCompleteRegion() {
        var notes = new java.util.ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 64; i++) notes.add(horizontalColumnHead(i % 2 == 0 ? 0 : 1));
        var before = List.copyOf(notes);
        assertTrue(
                TechniqueTextRegions.above(
                                List.of(COLUMN_STAFF), COLUMN_MEASURES, notes, 1024, 1024)
                        .isEmpty());
        assertEquals(before, notes);
        notes.add(horizontalColumnHead(.5f));
        before = List.copyOf(notes);
        assertEquals(
                List.of(horizontalColumnRegion()),
                TechniqueTextRegions.above(
                        List.of(COLUMN_STAFF), COLUMN_MEASURES, notes, 1024, 1024));
        assertEquals(before, notes);
    }
}
