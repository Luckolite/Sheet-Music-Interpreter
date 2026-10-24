// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedParenthesisGroupsTest {
    private static final int W = 320, H = 220;

    private record Scene(
            byte[] raw,
            List<ScoreNoteEvent> notes,
            List<PrintedSlurPageCapture.Frame<PrintedSlurOwners.GeometryHead>> frames,
            List<PrintedParenthesisGroups.Candidate> glyphs) {}

    private ScoreNoteEvent note(int measure, float x, float y, int staff, int count) {
        return new ScoreNoteEvent(
                measure,
                x / W,
                6,
                staff,
                count,
                y / H,
                false,
                0,
                0,
                0,
                2f,
                1,
                0,
                0,
                30,
                false,
                0,
                false,
                0,
                0,
                1,
                -1,
                ScoreNoteEvent.Kind.PITCHED);
    }

    private Scene scene() {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        var heads =
                List.of(
                        new PrintedSlurOwners.GeometryHead(0, 100, 90, 0, 1),
                        new PrintedSlurOwners.GeometryHead(1, 140, 62, 0, 1),
                        new PrintedSlurOwners.GeometryHead(2, 180, 76, 0, 1));
        var notes = new ArrayList<ScoreNoteEvent>();
        for (var h : heads) {
            notes.add(note(0, h.x(), h.y(), 0, 1));
            for (int y = (int) h.y() - 5; y <= h.y() + 5; y++)
                for (int x = (int) h.x() - 9; x <= h.x() + 9; x++)
                    if (Math.pow((x - h.x()) / 9., 2) + Math.pow((y - h.y()) / 5., 2) <= 1)
                        raw[y * W + x] = 0;
        }
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 116, 14}, new float[] {W - 1, 116, 14}));
        var frame = new PrintedSlurPageCapture.Frame<>(1, 1, W - 2, H - 2, track, heads);
        var left =
                new PrintedParenthesisShape.Glyph(
                        PrintedParenthesisShape.Side.LEFT, 80, 77, 86, 101, 83, 89, 14);
        var right =
                new PrintedParenthesisShape.Glyph(
                        PrintedParenthesisShape.Side.RIGHT, 194, 64, 200, 88, 197, 76, 14);
        return new Scene(
                raw,
                List.copyOf(notes),
                List.of(frame),
                List.of(
                        new PrintedParenthesisGroups.Candidate(0, left),
                        new PrintedParenthesisGroups.Candidate(0, right)));
    }

    private List<PrintedParenthesisGroups.Group> bind(Scene s) {
        return bind(s, List.of(), 128000000);
    }

    private List<PrintedParenthesisGroups.Group> bind(
            Scene s, List<PlayingTechniqueDetector.Word> words, long budget) {
        return PrintedParenthesisGroups.bind(
                s.glyphs,
                List.of(),
                s.frames,
                s.raw,
                W,
                H,
                s.notes,
                words,
                new PrintedParenthesisGroups.Budget(budget));
    }

    @Test
    public void onePairOwnsAllThreeSequentialNotes() {
        var g = bind(scene());
        assertEquals(1, g.size());
        assertEquals(List.of(0, 1, 2), g.get(0).members());
    }

    @Test
    public void mixedPitchHeightsKeepEndpointGlyphHeights() {
        var g = bind(scene()).get(0);
        assertEquals(89, g.left().centerY(), 0);
        assertEquals(76, g.right().centerY(), 0);
    }

    @Test
    public void bindingDoesNotMutateRawOrSourceNotes() {
        var s = scene();
        byte[] old = s.raw.clone();
        var notes = List.copyOf(s.notes);
        bind(s);
        assertArrayEquals(old, s.raw);
        assertEquals(notes, s.notes);
    }

    @Test
    public void missingRightBracketCannotCloseGroup() {
        var s = scene();
        assertTrue(bind(new Scene(s.raw, s.notes, s.frames, s.glyphs.subList(0, 1))).isEmpty());
    }

    @Test
    public void sameSideGlyphCannotCloseGroup() {
        var s = scene();
        var g = s.glyphs.get(1).glyph();
        var q =
                new PrintedParenthesisShape.Glyph(
                        PrintedParenthesisShape.Side.LEFT,
                        g.left(),
                        g.top(),
                        g.right(),
                        g.bottom(),
                        g.centerX(),
                        g.centerY(),
                        g.gap());
        assertTrue(
                bind(new Scene(
                                s.raw,
                                s.notes,
                                s.frames,
                                List.of(
                                        s.glyphs.get(0),
                                        new PrintedParenthesisGroups.Candidate(0, q))))
                        .isEmpty());
    }

    @Test
    public void duplicatePhysicalFramesCannotOwnSameHeads() {
        var s = scene();
        assertTrue(
                bind(new Scene(s.raw, s.notes, List.of(s.frames.get(0), s.frames.get(0)), s.glyphs))
                        .isEmpty());
    }

    @Test
    public void absentFirstHeadInkRefuses() {
        var s = scene();
        for (int y = 80; y < 101; y++) for (int x = 88; x < 112; x++) s.raw[y * W + x] = (byte) 255;
        assertTrue(bind(s).isEmpty());
    }

    @Test
    public void absentMiddleHeadInkRefusesPhantomMember() {
        var s = scene();
        for (int y = 52; y < 73; y++) for (int x = 128; x < 152; x++) s.raw[y * W + x] = (byte) 255;
        assertTrue(bind(s).isEmpty());
    }

    @Test
    public void ocrOverlappingOneBracketRefuses() {
        assertTrue(
                bind(
                                scene(),
                                List.of(
                                        new PlayingTechniqueDetector.Word(
                                                "text", 79f / W, 76f / H, 87f / W, 102f / H)),
                                128000000)
                        .isEmpty());
    }

    @Test
    public void unrelatedOcrDoesNotRemoveGroup() {
        assertEquals(
                1,
                bind(
                                scene(),
                                List.of(
                                        new PlayingTechniqueDetector.Word(
                                                "text", .8f, .8f, .9f, .9f)),
                                128000000)
                        .size());
    }

    @Test
    public void memberInAnotherMeasureRefuses() {
        var s = scene();
        var n = new ArrayList<>(s.notes);
        n.set(1, note(1, 140, 62, 0, 1));
        assertTrue(bind(new Scene(s.raw, n, s.frames, s.glyphs)).isEmpty());
    }

    @Test
    public void memberInAnotherStaffRefuses() {
        var s = scene();
        var n = new ArrayList<>(s.notes);
        n.set(1, note(0, 140, 62, 1, 2));
        assertTrue(bind(new Scene(s.raw, n, s.frames, s.glyphs)).isEmpty());
    }

    @Test
    public void graceInsidePairCannotInventOrdinaryGroup() {
        var s = scene();
        var f = s.frames.get(0);
        var h = new ArrayList<>(f.heads());
        h.set(1, new PrintedSlurOwners.GeometryHead(1, 140, 62, 0, 1, true));
        var altered =
                new PrintedSlurPageCapture.Frame<>(
                        f.left(), f.top(), f.right(), f.bottom(), f.track(), h);
        assertTrue(bind(new Scene(s.raw, s.notes, List.of(altered), s.glyphs)).isEmpty());
    }

    @Test
    public void duplicateEndpointOwnerIsAmbiguous() {
        var s = scene();
        var f = s.frames.get(0);
        var h = new ArrayList<>(f.heads());
        h.add(h.get(0));
        var altered =
                new PrintedSlurPageCapture.Frame<>(
                        f.left(), f.top(), f.right(), f.bottom(), f.track(), h);
        assertTrue(bind(new Scene(s.raw, s.notes, List.of(altered), s.glyphs)).isEmpty());
    }

    @Test
    public void nearbyCompetingLeftBracketRefusesBothProposals() {
        var s = scene();
        var gs = new ArrayList<>(s.glyphs);
        gs.add(
                new PrintedParenthesisGroups.Candidate(
                        0,
                        new PrintedParenthesisShape.Glyph(
                                PrintedParenthesisShape.Side.LEFT, 79, 77, 85, 101, 82, 89, 14)));
        assertTrue(bind(new Scene(s.raw, s.notes, s.frames, gs)).isEmpty());
    }

    @Test
    public void identicalCrossRouteGlyphIsOnePhysicalBracket() {
        var s = scene();
        assertEquals(
                1,
                PrintedParenthesisGroups.bind(
                                s.glyphs,
                                List.of(s.glyphs.get(0)),
                                s.frames,
                                s.raw,
                                W,
                                H,
                                s.notes,
                                List.of(),
                                new PrintedParenthesisGroups.Budget(128000000))
                        .size());
    }

    @Test
    public void zeroBudgetCannotLeavePartialGroup() {
        assertTrue(bind(scene(), List.of(), 0).isEmpty());
    }

    @Test
    public void lateBudgetRefusalCannotLeaveAnEarlierGroup() {
        assertTrue(bind(scene(), List.of(), 14500).isEmpty());
    }
}
