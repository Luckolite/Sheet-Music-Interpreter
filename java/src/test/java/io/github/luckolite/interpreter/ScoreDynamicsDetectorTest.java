// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreDynamicsDetectorTest {
    @org.junit.Test
    public void joinedDynamicAndDirectionRetainBothTokens() {
        org.junit.Assert.assertEquals(
                java.util.List.of("mf", "cresc"), ScoreDynamicsDetector.joinedDirection("mfcresc"));
        org.junit.Assert.assertEquals(
                java.util.List.of("p", "dim"), ScoreDynamicsDetector.joinedDirection("pdim."));
        org.junit.Assert.assertTrue(ScoreDynamicsDetector.joinedDirection("performance").isEmpty());
    }

    @Test
    public void packedChangesAndGradualDirectionAreDistinctFromOrdinaryWords() {
        assertEquals(List.of("mf", "mp"), ScoreDynamicsDetector.packedLevels("mfmp"));
        assertEquals(List.of("f", "mp"), ScoreDynamicsDetector.packedLevels("fmp"));
        assertTrue(ScoreDynamicsDetector.packedLevels("fff").isEmpty());
        assertTrue(ScoreDynamicsDetector.packedLevels("poco").isEmpty());
        assertTrue(ScoreDynamicsDetector.dynamicLine("mf cresc poco a poco"));
        assertFalse(ScoreDynamicsDetector.dynamicLine("a poem for me"));
    }

    @Test
    public void standardDynamicsAreOrderedAndLyricsAreRejected() {
        float old = -30;
        for (String text : List.of("ppp", "pp", "p", "mp", "m", "mf", "f", "ff", "fff")) {
            float level = ScoreDynamicsDetector.level(text);
            assertTrue(level > old);
            old = level;
        }
        assertTrue(ScoreDynamicsDetector.dynamicLine("pp mf f"));
        assertTrue(ScoreDynamicsDetector.dynamicLine("subito pp"));
        assertFalse(ScoreDynamicsDetector.dynamicLine("I am far from home"));
        assertTrue(Float.isNaN(ScoreDynamicsDetector.level("pizz.")));
    }

    @Test
    public void lettersBelongToNearbyStaffAndNeverBecomeNotes() {
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(80, 120, 10, 0, 2),
                        new PlayingTechniqueDetector.Staff(220, 260, 10, 1, 2));
        var bars =
                List.of(
                        new MeasureRegion(.1f, .5f, .2f, .7f),
                        new MeasureRegion(.5f, .9f, .2f, .7f));
        var words =
                List.of(
                        new PlayingTechniqueDetector.Word("pp", .2f, .33f, .23f, .36f),
                        new PlayingTechniqueDetector.Word("ff", .55f, .69f, .59f, .73f),
                        new PlayingTechniqueDetector.Word("f", .5f, .02f, .55f, .05f));
        var found = ScoreDynamicsDetector.detect(words, staffs, bars, List.of(), null, 1000, 400);
        assertEquals(2, found.size());
        assertEquals(0, found.get(0).staffIndex());
        assertEquals(-12, found.get(0).decibels(), 0);
        assertEquals(1, found.get(1).staffIndex());
        assertEquals(1, found.get(1).measureIndex());
    }

    @Test
    public void longWedgesCrossMeasuresButShortAccentsAndSlursAreNotHairpins() {
        int w = 600, h = 240;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        wedge(gray, w, 120, 360, 160, 20, false);
        wedge(gray, w, 380, 540, 160, 16, true);
        wedge(gray, w, 70, 87, 154, 9, false);
        var staffs = List.of(new PlayingTechniqueDetector.Staff(80, 120, 10, 0, 1));
        var bars =
                List.of(
                        new MeasureRegion(.1f, .5f, .3f, .55f),
                        new MeasureRegion(.5f, .95f, .3f, .55f));
        var found = ScoreDynamicsDetector.detect(List.of(), staffs, bars, List.of(), gray, w, h);
        assertEquals(2, found.size());
        assertEquals(1, found.get(0).direction());
        assertEquals(1, found.get(0).endMeasureIndex());
        assertEquals(-1, found.get(1).direction());
        int[] upper = new int[100], lower = new int[100];
        for (int x = 0; x < 100; x++) {
            upper[x] = (int) (10 + 10 * Math.sin(x * Math.PI / 100));
            lower[x] = upper[x] + 2;
        }
        assertEquals(0, ScoreDynamicsDetector.hairpinDirection(upper, lower, 10));
    }

    private static void wedge(
            byte[] gray, int w, int left, int right, int center, int spread, boolean closing) {
        for (int x = left; x <= right; x++) {
            int offset =
                    Math.round(
                            spread
                                    * (closing ? (right - x) : (x - left))
                                    / (float) (right - left)
                                    / 2);
            gray[(center - offset) * w + x] = 0;
            gray[(center + offset) * w + x] = 0;
        }
    }

    @Test
    public void textCrescendoCrossesRowsAndStopsAtNextPrintedDynamic() {
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(80, 120, 10, 0, 1),
                        new PlayingTechniqueDetector.Staff(220, 260, 10, 0, 1));
        var bars =
                List.of(
                        new MeasureRegion(.1f, .9f, .2f, .32f),
                        new MeasureRegion(.1f, .9f, .55f, .67f));
        var words =
                List.of(
                        new PlayingTechniqueDetector.Word("p", .15f, .34f, .18f, .38f),
                        new PlayingTechniqueDetector.Word("cresc.", .22f, .34f, .32f, .38f),
                        new PlayingTechniqueDetector.Word("ff", .16f, .69f, .20f, .73f));
        var changes = ScoreDynamicsDetector.detect(words, staffs, bars, List.of(), null, 1000, 400);
        var ramp = changes.stream().filter(c -> c.direction() == 1).findFirst().orElseThrow();
        assertEquals(0, ramp.measureIndex());
        assertEquals(1, ramp.endMeasureIndex());
        assertTrue(ramp.positionInMeasure() > changes.get(0).positionInMeasure());
        assertEquals(0, ScoreDynamicsDetector.textDirection("pizz."));
    }

    @Test
    public void ocrCannotCreateADynamicInAnEmptyWhiteGap() {
        byte[] gray = new byte[100 * 100];
        Arrays.fill(gray, (byte) 255);
        var word = new PlayingTechniqueDetector.Word("f", .2f, .2f, .4f, .4f);
        assertFalse(ScoreDynamicsDetector.containsInk(word, gray, 100, 100));
        for (int y = 22; y < 38; y++) for (int x = 28; x < 31; x++) gray[y * 100 + x] = 0;
        assertTrue(ScoreDynamicsDetector.containsInk(word, gray, 100, 100));
    }

    @Test
    public void curlyBraceIsNotAConnectingBarOrSquareEnsembleBracket() {
        int w = 300, h = 300;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int y = 50; y <= 250; y++) {
            double t = (y - 50) / 200.0;
            int x = 70 + (int) Math.round(7 * Math.abs(Math.sin(2 * Math.PI * t)));
            // inward shoulders, outward cusp midway
            if (t > .25 && t < .75)
                x = 70 + (int) Math.round(7 * Math.sin(Math.abs(t - .5) * Math.PI * 2));
            for (int dx = 0; dx < 2; dx++) gray[y * w + x + dx] = 0;
        }
        assertTrue(GrandStaffDynamics.hasBrace(gray, w, h, 150, 50, 250, 10));
        Arrays.fill(gray, (byte) 255);
        for (int y = 50; y <= 250; y++) gray[y * w + 70] = 0;
        for (int x = 70; x < 82; x++) {
            gray[50 * w + x] = 0;
            gray[250 * w + x] = 0;
        }
        assertFalse(GrandStaffDynamics.hasBrace(gray, w, h, 150, 50, 250, 10));
    }

    @Test
    public void staffAlignmentRetainsEquidistantVotesAndOriginalCallerOrder() {
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(90, 110, 20, 8, 9),
                        new PlayingTechniqueDetector.Staff(190, 210, 20, 8, 9),
                        new PlayingTechniqueDetector.Staff(290, 310, 20, 8, 9));
        var notes =
                List.of(
                        new ScoreNoteEvent(0, .1f, 0, 1, 2, .15f, false),
                        new ScoreNoteEvent(0, .2f, 0, 0, 2, .10f, false),
                        new ScoreNoteEvent(0, .3f, 0, 1, 2, .15f, false),
                        new ScoreNoteEvent(0, .4f, 0, 2, 3, .30f, false),
                        new ScoreNoteEvent(0, .5f, 0, 7, 8, .90f, false));
        var staffSnapshot = List.copyOf(staffs);
        var noteSnapshot = List.copyOf(notes);
        assertEquals(
                List.of(
                        new PlayingTechniqueDetector.Staff(90, 110, 20, 1, 2),
                        new PlayingTechniqueDetector.Staff(190, 210, 20, 1, 2),
                        new PlayingTechniqueDetector.Staff(290, 310, 20, 2, 3)),
                ScoreDynamicsDetector.alignStaffs(staffs, notes, 1000));
        assertEquals(staffSnapshot, staffs);
        assertEquals(noteSnapshot, notes);
        assertEquals(staffs, ScoreDynamicsDetector.alignStaffs(staffs, List.of(), 1000));
        assertTrue(ScoreDynamicsDetector.alignStaffs(List.of(), null, 1000).isEmpty());
        assertThrows(
                NullPointerException.class,
                () -> ScoreDynamicsDetector.alignStaffs(null, null, 1000));
        assertThrows(
                NullPointerException.class,
                () -> ScoreDynamicsDetector.alignStaffs(staffs, null, 1000));
        assertThrows(
                NullPointerException.class,
                () ->
                        ScoreDynamicsDetector.alignStaffs(
                                Arrays.asList((PlayingTechniqueDetector.Staff) null),
                                List.of(),
                                1000));
        var equalVotes =
                List.of(
                        new ScoreNoteEvent(0, .1f, 0, 1, 2, .10f, false),
                        new ScoreNoteEvent(0, .2f, 0, 0, 2, .10f, false));
        assertEquals(
                new PlayingTechniqueDetector.Staff(90, 110, 20, 0, 2),
                ScoreDynamicsDetector.alignStaffs(staffs, equalVotes, 1000).get(0));
    }

    @Test
    public void staffAlignmentKeepsNonfiniteAndSignedZeroComparisonSemantics() {
        float nan = Float.intBitsToFloat(0x7fc00123);
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(nan, nan, 1, 7, 4),
                        new PlayingTechniqueDetector.Staff(-0.0f, 0.0f, 1, 8, 4),
                        new PlayingTechniqueDetector.Staff(
                                Float.POSITIVE_INFINITY,
                                Float.POSITIVE_INFINITY,
                                Float.POSITIVE_INFINITY,
                                9,
                                4));
        var notes =
                List.of(
                        new ScoreNoteEvent(0, .1f, 0, 1, 2, -0.0f, false),
                        new ScoreNoteEvent(0, .2f, 0, 1, 2, Float.NaN, false),
                        new ScoreNoteEvent(0, .3f, 0, 1, 2, Float.POSITIVE_INFINITY, false));
        var aligned = ScoreDynamicsDetector.alignStaffs(staffs, notes, 1000);
        for (int s = 0; s < staffs.size(); s++) {
            assertEquals(1, aligned.get(s).index());
            assertEquals(2, aligned.get(s).count());
            assertEquals(
                    Float.floatToRawIntBits(staffs.get(s).top()),
                    Float.floatToRawIntBits(aligned.get(s).top()));
            assertEquals(
                    Float.floatToRawIntBits(staffs.get(s).bottom()),
                    Float.floatToRawIntBits(aligned.get(s).bottom()));
            assertEquals(
                    Float.floatToRawIntBits(staffs.get(s).gap()),
                    Float.floatToRawIntBits(aligned.get(s).gap()));
        }
        assertThrows(
                NullPointerException.class,
                () ->
                        ScoreDynamicsDetector.alignStaffs(
                                List.of(staffs.get(0)),
                                Arrays.asList((ScoreNoteEvent) null),
                                1000));
    }

    @Test
    public void directionFloorsReuseNotesAndRetainOwnerIdentityAndNonfiniteBounds()
            throws Exception {
        var first = new PlayingTechniqueDetector.Staff(80, 120, 10, 0, 2);
        var equal = new PlayingTechniqueDetector.Staff(80, 120, 10, 0, 2);
        var lower = new PlayingTechniqueDetector.Staff(220, 260, 10, 1, 2);
        var staffs = new ArrayList<>(List.of(first, equal, lower));
        var bars = List.of(new MeasureRegion(.1f, .9f, .2f, .7f));
        var notes =
                List.of(
                        new ScoreNoteEvent(0, .1f, 0, 0, 2, .4375f, false),
                        new ScoreNoteEvent(0, .2f, 0, 1, 2, .75f, false),
                        new ScoreNoteEvent(-1, .3f, 0, 0, 2, .9f, false),
                        new ScoreNoteEvent(2, .4f, 0, 0, 2, .9f, false),
                        new ScoreNoteEvent(0, .5f, 0, 0, 3, .9f, false),
                        new ScoreNoteEvent(0, .6f, 0, 0, 2, -0.0f, false));
        var staffSnapshot = List.copyOf(staffs);
        var noteSnapshot = List.copyOf(notes);
        var barSnapshot = List.copyOf(bars);
        int[] noteBits = notes.stream().mapToInt(n -> Float.floatToRawIntBits(n.pageY())).toArray();
        var counted = new DirectionCountingNotes(notes);
        Object cache = directionFloorCache(staffs, counted, bars, 400);
        assertEquals(0, counted.reads);
        float[][] boxes = {
            {180, 184}, {319, 324}, {124, 128}, {79, 77},
            {Float.NaN, Float.NaN}, {Float.NEGATIVE_INFINITY, -5}, {120, -0.0f}, {125, 126}
        };
        for (float[] box : boxes) {
            assertSame(
                    ScoreDynamicsDetector.directionOwner(staffs, box[0], box[1], notes, bars, 400),
                    cachedDirectionOwner(cache, box[0], box[1]));
            assertEquals(staffs.size() * notes.size(), counted.reads);
        }
        assertSame(first, cachedDirectionOwner(cache, 180, 184));
        assertSame(lower, cachedDirectionOwner(cache, 319, 324));
        var field = cache.getClass().getDeclaredField("floors");
        field.setAccessible(true);
        float[] floors = (float[]) field.get(cache);
        assertEquals(Float.floatToRawIntBits(190f), Float.floatToRawIntBits(floors[0]));
        assertEquals(Float.floatToRawIntBits(190f), Float.floatToRawIntBits(floors[1]));
        assertEquals(Float.floatToRawIntBits(315f), Float.floatToRawIntBits(floors[2]));
        assertEquals(staffSnapshot, staffs);
        assertEquals(noteSnapshot, notes);
        assertEquals(barSnapshot, bars);
        assertArrayEquals(
                noteBits,
                notes.stream().mapToInt(n -> Float.floatToRawIntBits(n.pageY())).toArray());

        // An equal record replacing a slot must not inherit the previous object's key.
        var replacement = new PlayingTechniqueDetector.Staff(80, 120, 10, 0, 2);
        staffs.set(0, replacement);
        assertSame(replacement, cachedDirectionOwner(cache, 180, 184));
        assertEquals((staffs.size() + 1) * notes.size(), counted.reads);

        float payloadNaN = Float.intBitsToFloat(0x7fc00123);
        var unusual =
                List.of(
                        new ScoreNoteEvent(0, .1f, 0, 0, 2, payloadNaN, false),
                        new ScoreNoteEvent(0, .2f, 0, 1, 2, Float.POSITIVE_INFINITY, false));
        var unusualCounted = new DirectionCountingNotes(unusual);
        Object unusualCache = directionFloorCache(staffs, unusualCounted, bars, 400);
        for (float[] box : boxes) {
            assertSame(
                    ScoreDynamicsDetector.directionOwner(
                            staffs, box[0], box[1], unusual, bars, 400),
                    cachedDirectionOwner(unusualCache, box[0], box[1]));
            assertEquals(staffs.size() * unusual.size(), unusualCounted.reads);
        }
    }

    @Test
    public void directionFloorsStayLazyAndPreserveDirectMutationAndFailureOrder() throws Exception {
        var staff = new PlayingTechniqueDetector.Staff(80, 120, 10, 0, 1);
        var staffs = List.of(staff);
        var bars = List.of(new MeasureRegion(.1f, .9f, .2f, .7f));
        var ordinary = List.of(new PlayingTechniqueDetector.Word("allegro", .1f, .1f, .2f, .2f));
        var empty =
                ScoreDynamicsDetector.detectDirectionCore(
                        ordinary, staffs, bars, null, null, 100, 400, null, false);
        assertTrue(empty.changes().isEmpty());
        assertTrue(empty.events().isEmpty());
        Object emptyCache = directionFloorCache(List.of(), null, null, 400);
        assertNull(cachedDirectionOwner(emptyCache, 180, 184));

        var mutable = new ArrayList<>(List.of(new ScoreNoteEvent(0, .1f, 0, 0, 1, .4375f, false)));
        assertSame(
                staff, ScoreDynamicsDetector.directionOwner(staffs, 180, 184, mutable, bars, 400));
        assertSame(
                staff,
                cachedDirectionOwner(directionFloorCache(staffs, mutable, bars, 400), 180, 184));
        mutable.clear();
        assertNull(ScoreDynamicsDetector.directionOwner(staffs, 180, 184, mutable, bars, 400));
        assertNull(cachedDirectionOwner(directionFloorCache(staffs, mutable, bars, 400), 180, 184));

        var counted =
                new DirectionCountingNotes(
                        List.of(new ScoreNoteEvent(0, .1f, 0, 0, 1, .4375f, false)));
        var invalidStaffs = Arrays.asList(staff, (PlayingTechniqueDetector.Staff) null);
        Object invalidCache = directionFloorCache(invalidStaffs, counted, bars, 400);
        assertThrows(
                NullPointerException.class, () -> cachedDirectionOwner(invalidCache, 180, 184));
        assertEquals(0, counted.reads);
        assertThrows(
                NullPointerException.class,
                () ->
                        ScoreDynamicsDetector.directionOwner(
                                invalidStaffs, 180, 184, counted, bars, 400));
        assertEquals(0, counted.reads);

        var nullNotes = Arrays.asList((ScoreNoteEvent) null);
        Object nullCache = directionFloorCache(staffs, nullNotes, bars, 400);
        assertThrows(NullPointerException.class, () -> cachedDirectionOwner(nullCache, 180, 184));
        assertThrows(
                NullPointerException.class,
                () -> ScoreDynamicsDetector.directionOwner(staffs, 180, 184, nullNotes, bars, 400));
        var matching = List.of(new ScoreNoteEvent(0, .1f, 0, 0, 1, .4375f, false));
        var nullBars = Arrays.asList((MeasureRegion) null);
        Object nullBarCache = directionFloorCache(staffs, matching, nullBars, 400);
        assertThrows(
                NullPointerException.class, () -> cachedDirectionOwner(nullBarCache, 180, 184));
        assertThrows(
                NullPointerException.class,
                () ->
                        ScoreDynamicsDetector.directionOwner(
                                staffs, 180, 184, matching, nullBars, 400));
    }

    private static final class DirectionCountingNotes extends AbstractList<ScoreNoteEvent> {
        private final List<ScoreNoteEvent> values;
        int reads;

        DirectionCountingNotes(List<ScoreNoteEvent> values) {
            this.values = values;
        }

        @Override
        public ScoreNoteEvent get(int index) {
            reads++;
            return values.get(index);
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    private static Object directionFloorCache(
            List<PlayingTechniqueDetector.Staff> staffs,
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> bars,
            int height)
            throws Exception {
        Class<?> type = Class.forName(ScoreDynamicsDetector.class.getName() + "$DirectionFloors");
        var constructor =
                type.getDeclaredConstructor(List.class, List.class, List.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(staffs, notes, bars, height);
    }

    private static PlayingTechniqueDetector.Staff cachedDirectionOwner(
            Object cache, float top, float bottom) throws Exception {
        var method = cache.getClass().getDeclaredMethod("owner", float.class, float.class);
        method.setAccessible(true);
        try {
            return (PlayingTechniqueDetector.Staff) method.invoke(cache, top, bottom);
        } catch (java.lang.reflect.InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw wrapper;
        }
    }
}
