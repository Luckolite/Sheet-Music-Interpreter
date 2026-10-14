// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete raw page, final note slots and explicit page/key/meter context. */
public class PrintedSlurRecognitionTest {
    private static final int W = 1200, H = 350;

    private record Fixture(byte[] gray, ScorePageInterpretation score) {}

    private Fixture fixture(boolean samePitch, boolean tied) {
        var gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int x = 30; x < W - 30; x++)
            for (int line = 0; line < 5; line++) gray[(210 - line * 10) * W + x] = 35;
        for (int x = 200; x <= 350; x++) {
            double t = 2.0 * (x - 200) / 150 - 1;
            gray[(int) Math.round(115 + 14 * t * t) * W + x] = 0;
        }
        int lastY = samePitch ? 145 : 155;
        for (int y = 142; y <= 148; y++) for (int x = 196; x <= 204; x++) gray[y * W + x] = 0;
        for (int y = lastY - 3; y <= lastY + 3; y++)
            for (int x = 346; x <= 354; x++) gray[y * W + x] = 0;
        var notes = List.of(note(200, 145, 13, false), note(350, lastY, samePitch ? 13 : 11, tied));
        return new Fixture(
                gray,
                new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes, 1));
    }

    private ScoreNoteEvent note(int x, int y, int step, boolean tied) {
        return new ScoreNoteEvent(
                        0,
                        x / (float) W,
                        step,
                        0,
                        1,
                        y / (float) H,
                        tied,
                        0,
                        0,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        1)
                .withClef(ScoreNoteEvent.CLEF_TREBLE);
    }

    private PrintedSlurRecognition.Result capture(Fixture f, int page, List<ScoreKeyChange> keys) {
        return PrintedSlurRecognition.capture(
                f.score(),
                page,
                f.gray(),
                null,
                W,
                H,
                List.of(new PlayingTechniqueDetector.Staff(170, 210, 10, 0, 1)),
                new ScoreMeterMap(4, List.of()),
                keys);
    }

    @Test
    public void realRawPageContextRetainsOnePhraseAndOriginalPhysicalOwners() {
        var f = fixture(false, false);
        var before = f.gray().clone();
        var r = capture(f, 9, List.of(new ScoreKeyChange(0, 0)));
        assertEquals(1, r.score().slurSource().spans().size());
        var span = r.score().slurSource().spans().get(0);
        assertEquals(new ScoreSlurPages.Anchor(9, 0), span.start());
        assertEquals(new ScoreSlurPages.Anchor(9, 1), span.end());
        assertEquals(f.score().notes(), r.score().notes());
        assertArrayEquals(before, f.gray());
        assertEquals(Set.of(), r.unboundOwners());
        assertFalse(r.limitReached());
    }

    @Test
    public void absentKeyCannotBeInventedFromNaturalLookingHeads() {
        var f = fixture(false, false);
        var r = capture(f, 9, List.of());
        assertEquals(f.score().notes(), r.score().notes());
        assertEquals(1, r.score().slurSource().candidates().size());
        assertEquals(
                Integer.valueOf(1),
                r.statuses().get(PrintedSlurOwners.Status.UNPITCHED_UNRESOLVED));
    }

    @Test
    public void samePitchReattackDoesNotAcquireASoundingTie() {
        var f = fixture(true, false);
        var r = capture(f, 9, List.of(new ScoreKeyChange(0, 0)));
        assertEquals(f.score().notes(), r.score().notes());
        assertEquals(1, r.score().slurSource().candidates().size());
        assertEquals(
                Integer.valueOf(1),
                r.statuses().get(PrintedSlurOwners.Status.SAME_PITCH_UNRESOLVED));
        assertFalse(r.score().notes().get(1).tiedFromPrevious());
    }

    @Test
    public void exactDecodedTieIsExcludedFromPhraseCapture() {
        var f = fixture(true, true);
        var r = capture(f, 9, List.of(new ScoreKeyChange(0, 0)));
        assertSame(f.score(), r.score());
        assertEquals(
                Integer.valueOf(1),
                r.statuses().get(PrintedSlurOwners.Status.TIE_ALREADY_RETAINED));
    }

    @Test
    public void recaptureCannotOverwriteRetainedOrEditedSource() {
        var f = fixture(false, false);
        var first = capture(f, 9, List.of(new ScoreKeyChange(0, 0)));
        var again =
                capture(new Fixture(f.gray(), first.score()), 2, List.of(new ScoreKeyChange(0, 7)));
        assertTrue(again.alreadyCaptured());
        assertSame(first.score(), again.score());
        assertEquals(9, again.score().slurSource().spans().get(0).start().pageIndex());
    }

    @Test
    public void joiningPagesUsesSelectionOrderAndKeepsTheirPhysicalNumbers() {
        var f = fixture(false, false);
        var a = capture(f, 9, List.of(new ScoreKeyChange(0, 0))).score().slurSource();
        var b = capture(f, 2, List.of(new ScoreKeyChange(0, 0))).score().slurSource();
        var joined = ScoreSlurSource.join(List.of(a, b));
        assertEquals(2, joined.resolve().resolved().size());
        assertEquals(new ScoreSlurPages.Anchor(2, 0), joined.noteOrigins().get(2).orElseThrow());
    }

    @Test
    public void capturedCompleteSourceSurvivesItsActualWire() throws Exception {
        var f = fixture(false, false);
        var source = capture(f, 9, List.of(new ScoreKeyChange(0, 0))).score().slurSource();
        var bytes = new ByteArrayOutputStream();
        ScoreSlurSourceWire.write(new DataOutputStream(bytes), source);
        var restored =
                ScoreSlurSourceWire.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 2);
        assertEquals(source, restored);
    }

    @Test(expected = IllegalArgumentException.class)
    public void pageIdentityCannotDefaultToZero() {
        capture(fixture(false, false), -1, List.of(new ScoreKeyChange(0, 0)));
    }

    @Test(expected = NullPointerException.class)
    public void missingMeterCannotDefaultToFourFour() {
        var f = fixture(false, false);
        PrintedSlurRecognition.capture(
                f.score(), 9, f.gray(), null, W, H, List.of(), null, List.of());
    }

    @Test
    public void inheritedMeterRetainsGeometryWithoutInventingMusicalContext() {
        var f = fixture(false, false);
        var result =
                PrintedSlurRecognition.captureObserved(
                                f.score(),
                                9,
                                f.gray(),
                                null,
                                W,
                                H,
                                List.of(new PlayingTechniqueDetector.Staff(170, 210, 10, 0, 1)))
                        .orElseThrow();
        assertEquals(1, result.score().slurSource().candidates().size());
        assertTrue(result.score().slurSource().spans().isEmpty());
        assertEquals(f.score().notes(), result.score().notes());
        assertTrue(f.score().slurSource().spans().isEmpty());
    }

    @Test
    public void observedOpeningKeyAndMeterNeedNoSavedSongMetadata() {
        var f = fixture(false, false);
        var score =
                new ScorePageInterpretation(
                        f.score().measures(),
                        f.score().notes(),
                        1,
                        List.of(new ScoreKeyChange(0, 0)),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 3, 4)),
                        List.of(),
                        List.of(),
                        List.of());
        var result =
                PrintedSlurRecognition.captureObserved(
                                score,
                                9,
                                f.gray(),
                                null,
                                W,
                                H,
                                List.of(new PlayingTechniqueDetector.Staff(170, 210, 10, 0, 1)))
                        .orElseThrow();
        assertEquals(1, result.score().slurSource().spans().size());
        assertEquals(score.notes(), result.score().notes());
        assertEquals(9, result.score().slurSource().spans().get(0).start().pageIndex());
    }

    @Test
    public void geometryHeadsDoNotContainAFabricatedMusicalClockOrPitch() {
        var f = fixture(false, false);
        var heads = PrintedSlurContext.geometryHeads(f.score().notes(), f.score().measures(), W, H);
        assertEquals(2, heads.size());
        assertEquals(
                Set.of("owner", "x", "y", "staffIndex", "staffCount"),
                Arrays.stream(PrintedSlurOwners.GeometryHead.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    public void deferredSourceRoundTripAndRecapturePreserveOriginalPage() throws Exception {
        var f = fixture(false, false);
        var first =
                PrintedSlurRecognition.captureObserved(
                                f.score(),
                                19,
                                f.gray(),
                                null,
                                W,
                                H,
                                List.of(new PlayingTechniqueDetector.Staff(170, 210, 10, 0, 1)))
                        .orElseThrow()
                        .score();
        var data = new ByteArrayOutputStream();
        ScoreSlurSourceWire.write(new DataOutputStream(data), first.slurSource());
        var saved =
                ScoreSlurSourceWire.read(
                        new DataInputStream(new ByteArrayInputStream(data.toByteArray())), 2);
        assertEquals(first.slurSource(), saved);
        var again =
                PrintedSlurRecognition.captureObserved(
                                first,
                                20,
                                f.gray(),
                                null,
                                W,
                                H,
                                List.of(new PlayingTechniqueDetector.Staff(170, 210, 10, 0, 1)))
                        .orElseThrow();
        assertTrue(again.alreadyCaptured());
        assertSame(first, again.score());
        assertEquals(19, saved.candidates().get(0).start().pageIndex());
    }

    @Test
    public void geometryCaptureDoesNotEngraveAnUnclassifiedArc() throws Exception {
        var f = fixture(false, false);
        var score =
                PrintedSlurRecognition.captureObserved(
                                f.score(),
                                19,
                                f.gray(),
                                null,
                                W,
                                H,
                                List.of(new PlayingTechniqueDetector.Staff(170, 210, 10, 0, 1)))
                        .orElseThrow()
                        .score();
        assertTrue(score.slurSource().engravingSpans(List.of(0, 1)).isEmpty());
        assertFalse(score.notes().get(1).tiedFromPrevious());
    }
}
