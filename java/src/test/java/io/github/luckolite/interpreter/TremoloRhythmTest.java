// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original notation geometry for independent tremolo and rhythm beams. */
public class TremoloRhythmTest {
    private static final int W = 360, H = 240;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    private void ink(int x, int y, int label) {
        gray[y * W + x] = 0;
        labels[y * W + x] = (byte) label;
    }

    private void build(boolean beamed, int strokes, boolean hook, boolean hollow) {
        Arrays.fill(gray, (byte) 255);
        for (int y = 80; y <= 128; y += 12) for (int x = 16; x < 344; x++) ink(x, y, 4);
        for (int cx : new int[] {80, 180}) {
            for (int y = 92; y <= 104; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if (Math.pow((x - cx) / 10., 2) + Math.pow((y - 98) / 6., 2) <= 1
                            && (!hollow
                                    || Math.pow((x - cx) / 6., 2) + Math.pow((y - 98) / 3., 2) > 1))
                        ink(x, y, 2);
            for (int y = 98; y <= 174; y++) ink(cx - 10, y, 1);
            for (int n = 0; n < strokes; n++)
                for (int x = hook ? cx - 10 : cx - 20; x <= cx; x++)
                    for (int dy = -2; dy <= 2; dy++)
                        ink(x, 157 - n * 12 + dy - Math.round((x - (cx - 10)) * .2f), 5);
        }
        if (beamed) for (int x = 70; x <= 170; x++) for (int y = 170; y <= 174; y++) ink(x, y, 5);
    }

    private List<ScoreNoteEvent> notes() {
        return OmrScoreInterpreter.analyze(
                        labels, gray, W, H, List.of(new MeasureRegion(.03f, .97f, .1f, .9f)))
                .notes();
    }

    @Test
    public void eighthNotesWithOneStrokeKeepEighthDurationAndRepeatSixteenths() {
        build(true, 1, false, false);
        var notes = notes();
        assertEquals(2, notes.size());
        for (var n : notes) {
            assertEquals(1, n.beamCount());
            assertEquals(.5, ScoreNoteTiming.writtenDurationBeats(n), 0);
            assertEquals(.25, NoteOrnament.tremoloBeats(n.articulations()), 0);
        }
    }

    @Test
    public void unmarkedBeamsKeepOrdinaryRhythm() {
        build(true, 0, false, false);
        for (var n : notes()) {
            assertEquals(1, n.beamCount());
            assertEquals(0, NoteOrnament.tremoloBeams(n.articulations()));
        }
    }

    @Test
    public void aOneSidedBeamHookIsNotTremolo() {
        build(true, 1, true, false);
        for (var n : notes()) assertEquals(0, NoteOrnament.tremoloBeams(n.articulations()));
    }

    @Test
    public void twoLongBeamsAreSixteenthsWithoutTremolo() {
        build(true, 0, false, false);
        for (int x = 70; x <= 170; x++) for (int y = 157; y <= 161; y++) ink(x, y, 5);
        for (var n : notes()) {
            assertEquals(2, n.beamCount());
            assertEquals(0, NoteOrnament.tremoloBeams(n.articulations()));
        }
    }

    @Test
    public void threeStrokesOnAQuarterPreserveOneBeat() {
        build(false, 3, false, false);
        assertEquals(2, notes().size());
        for (var n : notes()) {
            assertEquals(1, ScoreNoteTiming.writtenDurationBeats(n), 0);
            assertEquals(.125, NoteOrnament.tremoloBeats(n.articulations()), 0);
        }
    }

    @Test
    public void threeStrokesOnAHalfPreserveTwoBeats() {
        build(false, 3, false, true);
        assertEquals(2, notes().size());
        for (var n : notes()) {
            assertEquals(2, ScoreNoteTiming.writtenDurationBeats(n), 0);
            assertEquals(.125, NoteOrnament.tremoloBeats(n.articulations()), 0);
        }
    }

    @Test
    public void aStemStrokeMaskIslandIsNotAnotherPitch() {
        build(true, 1, false, false);
        for (int y = 155; y <= 159; y++) for (int x = 61; x <= 64; x++) labels[y * W + x] = 2;
        var notes = notes();
        assertEquals(2, notes.size());
        for (var n : notes()) assertEquals(5, n.staffStep());
    }

    @Test
    public void articulationAndTremoloMetadataCoexist() {
        int marks =
                NoteOrnament.withTremolo(NoteArticulation.ACCENT | NoteArticulation.STACCATO, 2);
        assertEquals(2, NoteOrnament.tremoloBeams(marks));
        assertEquals(
                NoteArticulation.ACCENT | NoteArticulation.STACCATO,
                NoteOrnament.withTremolo(marks, 0));
    }

    @Test
    public void aStrokeOnAStaffRuleIsNotAnotherPitch() {
        build(true, 1, false, false);
        // Move the complete written group up: the stroke now crosses the bottom rule.
        Arrays.fill(gray, (byte) 255);
        Arrays.fill(labels, (byte) 0);
        for (int y = 80; y <= 128; y += 12) for (int x = 16; x < 344; x++) ink(x, y, 4);
        for (int cx : new int[] {80, 180}) {
            for (int y = 62; y <= 74; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if (Math.pow((x - cx) / 10., 2) + Math.pow((y - 68) / 6., 2) <= 1) ink(x, y, 2);
            for (int y = 68; y <= 144; y++) ink(cx - 10, y, 1);
            for (int x = cx - 20; x <= cx; x++)
                for (int dy = -2; dy <= 2; dy++)
                    ink(x, 127 + dy - Math.round((x - (cx - 10)) * .2f), 5);
            for (int y = 126; y <= 130; y++)
                for (int x = cx - 19; x <= cx - 13; x++) labels[y * W + x] = 2;
        }
        for (int x = 70; x <= 170; x++) for (int y = 140; y <= 144; y++) ink(x, y, 5);
        var notes = notes();
        assertEquals(2, notes.size());
        for (var n : notes()) {
            assertEquals(10, n.staffStep());
            assertEquals(.5, ScoreNoteTiming.writtenDurationBeats(n), 0);
        }
    }

    @Test
    public void nearbyThinRuleDoesNotDoubleTheTremoloRate() {
        build(true, 1, false, false);
        for (int y = 152; y <= 153; y++) for (int x = 16; x < 344; x++) ink(x, y, 4);
        var notes = notes();
        assertEquals(2, notes.size());
        for (var n : notes()) assertEquals(.25, NoteOrnament.tremoloBeats(n.articulations()), 0);
    }

    @Test
    public void broadStrokeMaskIslandsDoNotBecomeChordTones() {
        build(true, 1, false, false);
        for (int y = 150; y <= 163; y++)
            for (int x = 62; x <= 78; x++) if ((gray[y * W + x] & 255) < 170) labels[y * W + x] = 2;
        byte[] rawBefore = gray.clone(), maskBefore = labels.clone();
        var notes = notes();
        assertEquals(2, notes.size());
        for (var n : notes()) {
            assertEquals(.5, ScoreNoteTiming.writtenDurationBeats(n), 0);
            assertEquals(.25, NoteOrnament.tremoloBeats(n.articulations()), 0);
        }
        assertArrayEquals(rawBefore, gray);
        assertArrayEquals(maskBefore, labels);
    }

    private void broadIsland() {
        build(true, 1, false, false);
        for (int y = 150; y <= 163; y++)
            for (int x = 62; x <= 78; x++) if ((gray[y * W + x] & 255) < 170) labels[y * W + x] = 2;
    }

    @Test
    public void oneSidedRawStrokeCannotGainTremoloFromBroadMask() {
        broadIsland();
        for (int y = 150; y <= 163; y++)
            for (int x = 60; x <= 68; x++) gray[y * W + x] = (byte) 255;
        var found = notes();
        assertEquals(2, found.size());
        assertTrue(found.get(0).positionInMeasure() < found.get(1).positionInMeasure());
        assertEquals(0, NoteOrnament.tremoloBeams(found.get(0).articulations()));
        assertEquals(2, NoteOrnament.tremoloBeams(found.get(1).articulations()));
    }

    @Test
    public void erasedRawStrokeCannotGainTremoloFromBroadMask() {
        broadIsland();
        for (int y = 150; y <= 163; y++)
            for (int x = 60; x <= 80; x++) gray[y * W + x] = (byte) 255;
        for (int y = 150; y <= 163; y++) gray[y * W + 70] = 0;
        var found = notes();
        assertEquals(2, found.size());
        assertTrue(found.get(0).positionInMeasure() < found.get(1).positionInMeasure());
        assertEquals(0, NoteOrnament.tremoloBeams(found.get(0).articulations()));
        assertEquals(2, NoteOrnament.tremoloBeams(found.get(1).articulations()));
    }

    private void genuineChordCompetitor(boolean hollow) throws Exception {
        build(true, 1, false, false);
        int area = 0;
        for (int y = 154; y <= 160; y++)
            for (int x = 70; x <= 86; x++)
                if (Math.pow((x - 78) / 8., 2) + Math.pow((y - 157) / 3., 2) <= 1) {
                    labels[y * W + x] = 2;
                    area++;
                    gray[y * W + x] =
                            (byte)
                                    (hollow
                                                    && Math.pow((x - 78) / 4., 2)
                                                                    + Math.pow((y - 157) / 1.5, 2)
                                                            < 1
                                            ? 255
                                            : 0);
                }
        byte[] rawBefore = gray.clone(), maskBefore = labels.clone();
        var component = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = component.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var principal = ctor.newInstance(184, 71, 90, 92, 104, 80.054344f, 98f);
        var competitor = ctor.newInstance(area, 70, 86, 154, 160, 78f, 157f);
        var staffClass = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var staffCtor = staffClass.getDeclaredConstructor(float.class, float.class, float.class);
        staffCtor.setAccessible(true);
        var staff = staffCtor.newInstance(80f, 128f, 12f);
        var fragmentsMethod =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "stemSlashFragments",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        fragmentsMethod.setAccessible(true);
        List<?> fragments =
                (List<?>)
                        fragmentsMethod.invoke(
                                null, gray, W, H, List.of(principal, competitor), List.of(staff));
        assertFalse(
                "A genuine chord head remains an independent competitor",
                fragments.contains(competitor));
        var countMethod =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "tremoloStrokeCounts",
                        byte[].class,
                        int.class,
                        int.class,
                        component,
                        float.class,
                        List.class);
        countMethod.setAccessible(true);
        int[] counts =
                (int[])
                        countMethod.invoke(
                                null, gray, W, H, principal, 12f, List.of(principal, competitor));
        assertEquals(0, counts[0]);
        assertArrayEquals(rawBefore, gray);
        assertArrayEquals(maskBefore, labels);
    }

    @Test
    public void genuineFilledChordCompetitorSurvivesBroadStrokeFilter() throws Exception {
        genuineChordCompetitor(false);
    }

    @Test
    public void genuineHollowChordCompetitorSurvivesBroadStrokeFilter() throws Exception {
        genuineChordCompetitor(true);
    }
}
