// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Original synthetic raster evidence; contains no imported score pixels or expected song data. */
public class TabBoundaryTiesTest {
    private static final int W = 320, H = 280;
    private static final float GAP = 12;

    private record Fixture(
            byte[] gray, List<TablatureDecoder.Staff> tabs, ScorePageInterpretation score) {}

    @Test
    public void matchingConnectedShouldersHoldSameStringAcrossAdjacentSystems() {
        var f = fixture(true, true, -1, 7, 2);
        var result = run(f);
        assertFalse(result.notes().get(0).tiedFromPrevious());
        assertTrue(result.notes().get(1).tiedFromPrevious());
        assertEquals(f.score().notes().size(), result.notes().size());
        assertEquals(f.score().measures(), result.measures());
        assertEquals(0, result.notes().get(1).boundaryTies());
        var before = f.score().notes().get(1);
        var after = result.notes().get(1);
        // Source field preservation includes fields absent from older constructors.
        assertEquals(
                new ScoreNoteEvent(
                        before.measureIndex(),
                        before.positionInMeasure(),
                        before.staffStep(),
                        before.staffIndex(),
                        before.staffCount(),
                        before.pageY(),
                        true,
                        before.augmentationDots(),
                        before.beamCount(),
                        before.writtenAccidental(),
                        before.unbeamedDurationBeats(),
                        before.tupletDivisor(),
                        before.followingRestBeats(),
                        before.articulations(),
                        before.clefBottomDiatonic(),
                        before.crossStaffBeam(),
                        before.leadingRestBeats(),
                        before.compactOpening(),
                        before.octaveShift(),
                        before.boundaryTies(),
                        before.tupletNormalNotes(),
                        before.stemDirection(),
                        before.kind()),
                after);
    }

    @Test
    public void oneSidedIncomingNeverCreatesTie() {
        assertUntied(fixture(false, true, -1, 7, 2));
    }

    @Test
    public void oneSidedOutgoingNeverCreatesTie() {
        assertUntied(fixture(true, false, -1, 7, 2));
    }

    @Test
    public void oppositeShoulderDirectionsDoNotJoin() {
        assertUntied(fixture(true, true, 1, 7, 2));
    }

    @Test
    public void changedFretIsANewAttack() {
        assertUntied(fixture(true, true, -1, 8, 2));
    }

    @Test
    public void unisonTransferredToDifferentStringIsANewAttack() {
        assertUntied(fixture(true, true, -1, 7, 3));
    }

    @Test
    public void differentTuningDoesNotJoin() {
        var f = fixture(true, true, -1, 7, 2);
        var tabs = new ArrayList<>(f.tabs());
        var old = tabs.get(1);
        tabs.set(
                1,
                new TablatureDecoder.Staff(
                        old.top(),
                        old.gap(),
                        -1,
                        old.frets(),
                        old.bars(),
                        6,
                        List.of(65, 60, 56, 51, 46, 41)));
        assertUntied(new Fixture(f.gray(), tabs, f.score()));
    }

    @Test
    public void interveningEmptySystemBreaksAdjacency() {
        var f = fixture(true, true, -1, 7, 2);
        var tabs = new ArrayList<>(f.tabs());
        tabs.add(new TablatureDecoder.Staff(112, GAP, -1, List.of(), List.of(40f, 160f, 280f)));
        assertUntied(new Fixture(f.gray(), tabs, f.score()));
    }

    @Test
    public void pairedStandardNotationOwnsItsTies() {
        var f = fixture(true, true, -1, 7, 2);
        var old = f.tabs().get(1);
        var paired = new TablatureDecoder.Staff(old.top(), GAP, 120, old.frets(), old.bars());
        assertUntied(new Fixture(f.gray(), List.of(f.tabs().get(0), paired), f.score()));
    }

    @Test
    public void flatRuleCannotProveOutgoingShoulder() {
        var f = fixture(false, true, -1, 7, 2);
        for (int x = 226; x <= 278; x++) f.gray()[58 * W + x] = 0;
        assertUntied(f);
    }

    @Test
    public void disconnectedOutgoingFragmentsCannotProveTie() {
        var f = fixture(true, true, -1, 7, 2);
        for (int x = 246; x <= 250; x++)
            for (int y = 46; y < 60; y++) f.gray()[y * W + x] = (byte) 255;
        assertUntied(f);
    }

    @Test
    public void neighboringStringArcCannotBeBorrowed() {
        var f = fixture(false, false, -1, 7, 2);
        bow(f.gray(), 226, 278, 48, 1, 7);
        bow(f.gray(), 45, 53, 172, 1, 3);
        assertUntied(f);
    }

    @Test
    public void shortSteepBowedShoulderStillReturnsToItsOwnString() {
        var f = fixture(true, false, -1, 7, 2);
        bow(f.gray(), 46, 53, 184, -1, 5);
        assertTrue(run(f).notes().get(1).tiedFromPrevious());
    }

    @Test
    public void verticalParenthesisLikeEdgeCannotProveIncomingShoulder() {
        var f = fixture(true, false, -1, 7, 2);
        for (int y = 174; y <= 182; y++) {
            int x = 54 + Math.round(2 * (float) Math.sin((y - 174) * Math.PI / 8));
            f.gray()[y * W + x] = 0;
        }
        assertUntied(f);
    }

    @Test
    public void slopingConnectedFragmentDoesNotReturnToTheString() {
        var f = fixture(true, false, -1, 7, 2);
        for (int x = 45; x <= 53; x++) {
            int y = 182 - Math.round((x - 45) * .625f);
            f.gray()[y * W + x] = 0;
        }
        assertUntied(f);
    }

    @Test
    public void longBowEndpointSamplingStaysLocalToStringSpacing() {
        var f = fixture(false, true, -1, 7, 2);
        bow(f.gray(), 226, 278, 60, -1, 7, 3.5f);
        assertTrue(run(f).notes().get(1).tiedFromPrevious());
    }

    @Test
    public void filledIncomingStrokeRetainsItsBowedCenterline() {
        var f = fixture(true, false, -1, 7, 2);
        for (int x = 45; x <= 53; x++) {
            int depth = 2 + (int) Math.round(3 * Math.sin(Math.PI * (x - 45) / 8));
            for (int distance = 2; distance <= depth; distance++)
                f.gray()[(184 - distance) * W + x] = 0;
        }
        assertTrue(run(f).notes().get(1).tiedFromPrevious());
    }

    @Test
    public void ambiguousNoteMappingCannotBeRepaired() {
        var f = fixture(true, true, -1, 7, 2);
        var notes = new ArrayList<>(f.score().notes());
        notes.add(notes.get(1));
        assertUntied(withNotes(f, notes));
    }

    @Test
    public void laterAttackOnAnotherStringInterruptsWholeChordBoundary() {
        var f = fixture(true, true, -1, 7, 2);
        var notes = new ArrayList<>(f.score().notes());
        notes.add(note(0, .85f, 72f / H));
        assertUntied(withNotes(f, notes));
    }

    @Test
    public void explicitRestAfterSourceInterruptsTie() {
        var f = fixture(true, true, -1, 7, 2);
        var score =
                new ScorePageInterpretation(
                        f.score().measures(),
                        f.score().notes(),
                        1,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new ScoreRestEvent(0, .9f, 60f / H, .02f, 0, 1, 1)));
        assertUntied(new Fixture(f.gray(), f.tabs(), score));
    }

    @Test
    public void muteGraceOrEffectCannotBecomeSustainEvidence() {
        for (int fret : new int[] {-1, -2}) {
            var f = fixture(true, true, -1, 7, 2);
            var source = f.tabs().get(0);
            var old = source.frets().get(0);
            var altered = new TablatureDecoder.Fret(old.x(), old.y(), old.string(), fret);
            assertUntied(withSourceFret(f, altered));
        }
        var f = fixture(true, true, -1, 7, 2);
        var old = f.tabs().get(0).frets().get(0);
        assertUntied(
                withSourceFret(
                        f,
                        new TablatureDecoder.Fret(
                                old.x(),
                                old.y(),
                                old.string(),
                                old.fret(),
                                0,
                                0,
                                0,
                                TabEffect.encode(TabEffect.HAMMER, -2))));
        assertUntied(
                withSourceFret(
                        f,
                        new TablatureDecoder.Fret(
                                old.x(),
                                old.y(),
                                old.string(),
                                old.fret(),
                                0,
                                0,
                                0,
                                NoteOrnament.GRACE)));
    }

    @Test
    public void outgoingPureTapMaySustainIntoAnUnannotatedTarget() {
        var f = fixture(true, true, -1, 7, 2);
        var old = f.tabs().get(0).frets().get(0);
        int tap = TabEffect.encode(TabEffect.TAP, 0);
        f =
                withSourceFret(
                        f,
                        new TablatureDecoder.Fret(
                                old.x(), old.y(), old.string(), old.fret(), 0, 0, 0, tap));
        var notes = new ArrayList<>(f.score().notes());
        notes.set(0, notes.get(0).withArticulations(tap));
        var result = run(withNotes(f, notes));
        assertTrue(result.notes().get(1).tiedFromPrevious());
        assertEquals(tap, result.notes().get(0).articulations());
    }

    @Test
    public void incomingExplicitTapHammerOrPullRemainsANewAttack() {
        for (int effect :
                new int[] {
                    TabEffect.encode(TabEffect.TAP, 0),
                    TabEffect.encode(TabEffect.HAMMER, -2),
                    TabEffect.encode(TabEffect.PULL, 2)
                }) {
            var f = fixture(true, true, -1, 7, 2);
            var tabs = new ArrayList<>(f.tabs());
            var target = tabs.get(1);
            var old = target.frets().get(0);
            tabs.set(
                    1,
                    new TablatureDecoder.Staff(
                            target.top(),
                            GAP,
                            -1,
                            List.of(
                                    new TablatureDecoder.Fret(
                                            old.x(),
                                            old.y(),
                                            old.string(),
                                            old.fret(),
                                            0,
                                            0,
                                            0,
                                            effect)),
                            target.bars()));
            assertUntied(new Fixture(f.gray(), tabs, f.score()));
            var notes = new ArrayList<>(f.score().notes());
            notes.set(1, notes.get(1).withArticulations(effect));
            assertUntied(withNotes(f, notes));
        }
    }

    @Test
    public void outgoingTapWithAdditionalPerformanceMarksIsNotPureSustainEvidence() {
        var f = fixture(true, true, -1, 7, 2);
        var old = f.tabs().get(0).frets().get(0);
        int tap = TabEffect.encode(TabEffect.TAP, 0);
        for (int extra :
                new int[] {TabEffect.VIBRATO, TabEffect.PALM_MUTE, NoteOrnament.GRACE, 1}) {
            assertUntied(
                    withSourceFret(
                            f,
                            new TablatureDecoder.Fret(
                                    old.x(),
                                    old.y(),
                                    old.string(),
                                    old.fret(),
                                    0,
                                    0,
                                    0,
                                    tap | extra)));
            var notes = new ArrayList<>(f.score().notes());
            notes.set(0, notes.get(0).withArticulations(tap | extra));
            assertUntied(withNotes(f, notes));
        }
    }

    @Test
    public void pageOuterEvidenceIsTypedAndDoesNotAloneCreateATie() {
        var f = fixture(true, true, -1, 7, 2);
        var firstOnly =
                new ScorePageInterpretation(
                        List.of(f.score().measures().get(0)), List.of(f.score().notes().get(0)));
        var result = TabBoundaryTies.apply(firstOnly, List.of(f.tabs().get(0)), f.gray(), W, H);
        assertEquals(TabTieIdentity.encode(4, 2, 6, 7, 55), result.notes().get(0).boundaryTies());
        assertFalse(result.notes().get(0).tiedFromPrevious());
        assertEquals(
                firstOnly.notes().get(0).withBoundaryTies(result.notes().get(0).boundaryTies()),
                result.notes().get(0));
        var n = f.score().notes().get(1);
        var incoming = copy(n, 0, n.leadingRestBeats(), n.followingRestBeats());
        var secondOnly =
                new ScorePageInterpretation(
                        List.of(f.score().measures().get(1)), List.of(incoming));
        result = TabBoundaryTies.apply(secondOnly, List.of(f.tabs().get(1)), f.gray(), W, H);
        assertEquals(TabTieIdentity.encode(1, 2, 6, 7, 55), result.notes().get(0).boundaryTies());
        assertFalse(result.notes().get(0).tiedFromPrevious());
    }

    @Test
    public void matchingTypedCrossPageShouldersResolveAfterPitchAndVoiceChecks() {
        var notes = producedPagePair(fixture(true, true, -1, 7, 2));
        var resolved = ScoreBoundaryTies.resolve(notes, List.of(), List.of(1));
        assertTrue(resolved.get(1).tiedFromPrevious());
    }

    @Test
    public void pureTapSourceRetainsTypedCrossPageSustainEvidence() {
        var f = fixture(true, true, -1, 7, 2);
        var old = f.tabs().get(0).frets().get(0);
        int tap = TabEffect.encode(TabEffect.TAP, 0);
        f =
                withSourceFret(
                        f,
                        new TablatureDecoder.Fret(
                                old.x(), old.y(), old.string(), old.fret(), 0, 0, 0, tap));
        var notes = new ArrayList<>(f.score().notes());
        notes.set(0, notes.get(0).withArticulations(tap));
        var pair = producedPagePair(withNotes(f, notes));
        assertTrue(
                ScoreBoundaryTies.resolve(pair, List.of(), List.of(1)).get(1).tiedFromPrevious());
        assertEquals(tap, pair.get(0).articulations());
    }

    @Test
    public void crossPageUnisonOnDifferentStringRemainsANewAttack() {
        // Standard string2 fret7 and string3 fret12 are both MIDI62. The
        // same note pitch alone must not collapse this transfer to a new string.
        var notes = producedPagePair(fixture(true, true, -1, 12, 3));
        assertEquals(notes.get(0).diatonicPitchIdentity(), notes.get(1).diatonicPitchIdentity());
        assertFalse(
                TabTieIdentity.compatible(
                        notes.get(0).boundaryTies(), notes.get(1).boundaryTies()));
        assertFalse(
                ScoreBoundaryTies.resolve(notes, List.of(), List.of(1)).get(1).tiedFromPrevious());
    }

    @Test
    public void pageOuterIdentityUsesSuppliedTuningPlusCapo() {
        var f = fixture(true, false, -1, 7, 2);
        var score =
                new ScorePageInterpretation(
                        List.of(f.score().measures().get(0)), List.of(f.score().notes().get(0)));
        var result =
                TabBoundaryTies.apply(
                        score,
                        List.of(f.tabs().get(0)),
                        f.gray(),
                        W,
                        H,
                        new int[] {62, 57, 53, 48, 43, 38},
                        3);
        assertEquals(TabTieIdentity.encode(4, 2, 6, 7, 56), result.notes().get(0).boundaryTies());
    }

    @Test
    public void unrelatedSideRestsDoNotDiscardProvenJoin() {
        var f = fixture(true, true, -1, 7, 2);
        var a = f.score().notes().get(0);
        var b = f.score().notes().get(1);
        var result = run(withNotes(f, List.of(copy(a, 0, 1, 0), copy(b, 1, 0, 1))));
        assertTrue(result.notes().get(1).tiedFromPrevious());
        assertEquals(1, result.notes().get(0).leadingRestBeats(), 0);
        assertEquals(1, result.notes().get(1).followingRestBeats(), 0);
    }

    private static List<ScoreNoteEvent> producedPagePair(Fixture f) {
        var firstScore =
                new ScorePageInterpretation(
                        List.of(f.score().measures().get(0)), List.of(f.score().notes().get(0)));
        var secondScore =
                new ScorePageInterpretation(
                        List.of(f.score().measures().get(1)),
                        List.of(copy(f.score().notes().get(1), 0, 0, 0)));
        var first =
                TabBoundaryTies.apply(firstScore, List.of(f.tabs().get(0)), f.gray(), W, H)
                        .notes()
                        .get(0);
        var second =
                TabBoundaryTies.apply(secondScore, List.of(f.tabs().get(1)), f.gray(), W, H)
                        .notes()
                        .get(0);
        assertTrue(TabTieIdentity.typed(first.boundaryTies()));
        assertTrue(TabTieIdentity.typed(second.boundaryTies()));
        return List.of(first, copy(second, 1, 0, 0));
    }

    private static ScoreNoteEvent copy(
            ScoreNoteEvent n, int measure, float leading, float following) {
        return new ScoreNoteEvent(
                measure,
                n.positionInMeasure(),
                n.staffStep(),
                n.staffIndex(),
                n.staffCount(),
                n.pageY(),
                n.tiedFromPrevious(),
                n.augmentationDots(),
                n.beamCount(),
                n.writtenAccidental(),
                n.unbeamedDurationBeats(),
                n.tupletDivisor(),
                following,
                n.articulations(),
                n.clefBottomDiatonic(),
                n.crossStaffBeam(),
                leading,
                n.compactOpening(),
                n.octaveShift(),
                n.boundaryTies(),
                n.tupletNormalNotes(),
                n.stemDirection(),
                n.kind());
    }

    @Test
    public void threeSegmentChainRetainsBothSystemJoins() {
        var f = chain(false);
        var result = TabBoundaryTies.apply(f.score(), f.tabs(), f.gray(), W, 440);
        assertFalse(result.notes().get(0).tiedFromPrevious());
        assertTrue(result.notes().get(1).tiedFromPrevious());
        assertTrue(result.notes().get(2).tiedFromPrevious());
        assertEquals(3, result.notes().size());
    }

    @Test
    public void alreadyHeldMiddleSegmentStillProvesItsOutgoingJoin() {
        var f = chain(true);
        var result = TabBoundaryTies.apply(f.score(), f.tabs(), f.gray(), W, 440);
        assertTrue(result.notes().get(1).tiedFromPrevious());
        assertTrue(result.notes().get(2).tiedFromPrevious());
        assertEquals(f.score().notes().get(1), result.notes().get(1));
    }

    @Test
    public void threeSegmentChainStopsAtAChangedFinalFret() {
        var f = chain(false);
        var tabs = new ArrayList<>(f.tabs());
        var last = tabs.get(2);
        var old = last.frets().get(0);
        tabs.set(
                2,
                new TablatureDecoder.Staff(
                        last.top(),
                        GAP,
                        -1,
                        List.of(new TablatureDecoder.Fret(old.x(), old.y(), old.string(), 8)),
                        last.bars()));
        var result = TabBoundaryTies.apply(f.score(), tabs, f.gray(), W, 440);
        assertTrue(result.notes().get(1).tiedFromPrevious());
        assertFalse(result.notes().get(2).tiedFromPrevious());
    }

    @Test
    public void undeclaredInteriorRuleCannotJoinTheOutgoingBowToOtherInk() {
        var f = chain(false);
        for (int y = 176; y <= 236; y++) f.gray()[y * W + 160] = 0;
        var result = TabBoundaryTies.apply(f.score(), f.tabs(), f.gray(), W, 440);
        assertTrue(result.notes().get(1).tiedFromPrevious());
        assertFalse(result.notes().get(2).tiedFromPrevious());
    }

    @Test
    public void explicitlySuppliedTuningTakesPrecedenceLikeTheDecoder() {
        var f = fixture(true, true, -1, 7, 2);
        var tabs = new ArrayList<>(f.tabs());
        var old = tabs.get(1);
        tabs.set(
                1,
                new TablatureDecoder.Staff(
                        old.top(),
                        GAP,
                        -1,
                        old.frets(),
                        old.bars(),
                        6,
                        List.of(65, 60, 56, 51, 46, 41)));
        var result =
                TabBoundaryTies.apply(
                        f.score(), tabs, f.gray(), W, H, new int[] {64, 59, 55, 50, 45, 40}, 2);
        assertTrue(result.notes().get(1).tiedFromPrevious());
        assertFalse(
                TabBoundaryTies.apply(f.score(), tabs, f.gray(), W, H, new int[] {64, 59}, 2)
                        .notes()
                        .get(1)
                        .tiedFromPrevious());
    }

    private static Fixture chain(boolean middleAlreadyHeld) {
        int height = 440;
        byte[] gray = new byte[W * height];
        Arrays.fill(gray, (byte) 255);
        var tabs = new ArrayList<TablatureDecoder.Staff>();
        var measures = new ArrayList<MeasureRegion>();
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 3; i++) {
            int top = 36 + i * 140;
            float y = top + 24;
            float x = i == 0 ? 220 : 62;
            var fret =
                    new TablatureDecoder.Fret(x, y, 2, 7, 0, 0, 0, 0, i == 1 && middleAlreadyHeld);
            var bars = i == 1 ? List.of(40f, 280f) : List.of(40f, 160f, 280f);
            // The long middle segment occupies one enclosing bar. Its raster
            // must not contain an undeclared interior bar through the bow.
            rules(gray, top, bars);
            tabs.add(new TablatureDecoder.Staff(top, GAP, -1, List.of(fret), bars));
            float left = i == 0 ? 160 : 40, right = i == 2 ? 160 : 280;
            measures.add(
                    new MeasureRegion(
                            left / W, right / W, (top - GAP) / height, (top + 72f) / height));
            notes.add(
                    new ScoreNoteEvent(
                            i,
                            (x - left) / (right - left),
                            -1,
                            0,
                            1,
                            y / height,
                            i == 1 && middleAlreadyHeld,
                            0,
                            0,
                            0,
                            4,
                            1,
                            0,
                            0,
                            ScoreNoteEvent.CLEF_TREBLE));
            if (i > 0) bow(gray, 45, 53, y, -1, 3);
            if (i < 2) bow(gray, i == 0 ? 226 : 68, 278, y, -1, 7);
        }
        return new Fixture(gray, tabs, new ScorePageInterpretation(measures, notes));
    }

    private static Fixture withSourceFret(Fixture f, TablatureDecoder.Fret fret) {
        var old = f.tabs().get(0);
        var next = new TablatureDecoder.Staff(old.top(), GAP, -1, List.of(fret), old.bars());
        return new Fixture(f.gray(), List.of(next, f.tabs().get(1)), f.score());
    }

    private static Fixture withNotes(Fixture f, List<ScoreNoteEvent> notes) {
        return new Fixture(
                f.gray(), f.tabs(), new ScorePageInterpretation(f.score().measures(), notes));
    }

    private static ScorePageInterpretation run(Fixture f) {
        return TabBoundaryTies.apply(f.score(), f.tabs(), f.gray(), W, H);
    }

    private static void assertUntied(Fixture f) {
        var result = run(f);
        assertFalse(result.notes().get(1).tiedFromPrevious());
        assertTrue(result.notes().stream().allMatch(n -> n.boundaryTies() == 0));
    }

    private static Fixture fixture(
            boolean outgoing,
            boolean incoming,
            int incomingDirection,
            int targetFret,
            int targetString) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        rules(gray, 36);
        rules(gray, 160);
        if (outgoing) bow(gray, 226, 278, 60, -1, 7);
        float targetY = 160 + targetString * GAP;
        if (incoming) bow(gray, 45, 53, targetY, incomingDirection, 3);
        var source =
                new TablatureDecoder.Staff(
                        36,
                        GAP,
                        -1,
                        List.of(new TablatureDecoder.Fret(220, 60, 2, 7)),
                        List.of(40f, 160f, 280f));
        var target =
                new TablatureDecoder.Staff(
                        160,
                        GAP,
                        -1,
                        List.of(new TablatureDecoder.Fret(62, targetY, targetString, targetFret)),
                        List.of(40f, 160f, 280f));
        var measures =
                List.of(
                        new MeasureRegion(160f / W, 280f / W, 24f / H, 108f / H),
                        new MeasureRegion(40f / W, 160f / W, 148f / H, 232f / H));
        var score =
                new ScorePageInterpretation(
                        measures,
                        List.of(note(0, .5f, 60f / H), note(1, 22f / 120, targetY / H)),
                        1);
        return new Fixture(gray, List.of(source, target), score);
    }

    private static ScoreNoteEvent note(int measure, float position, float y) {
        return new ScoreNoteEvent(
                measure,
                position,
                -1,
                0,
                1,
                y,
                false,
                1,
                0,
                0,
                2,
                3,
                0,
                0,
                ScoreNoteEvent.CLEF_TREBLE,
                false,
                0,
                true,
                1,
                0,
                2,
                -1,
                ScoreNoteEvent.Kind.PITCHED);
    }

    private static void rules(byte[] gray, int top) {
        rules(gray, top, List.of(40f, 160f, 280f));
    }

    private static void rules(byte[] gray, int top, List<Float> bars) {
        for (int string = 0; string < 6; string++)
            for (int x = 40; x <= 280; x++) gray[(top + string * 12) * W + x] = 0;
        for (float bar : bars)
            for (int y = top; y <= top + 60; y++) gray[y * W + Math.round(bar)] = 0;
    }

    private static void bow(byte[] gray, int left, int right, float cy, int direction, float rise) {
        bow(gray, left, right, cy, direction, rise, 2);
    }

    private static void bow(
            byte[] gray, int left, int right, float cy, int direction, float rise, float endpoint) {
        int previousY = (int) Math.round(cy + direction * endpoint);
        for (int x = left; x <= right; x++) {
            float t = (x - left) / (float) (right - left);
            int y = (int) Math.round(cy + direction * (endpoint + rise * Math.sin(Math.PI * t)));
            // A steep original glyph must remain a connected raster path:
            // sampling only one pixel per column can leave artificial gaps.
            int steps = Math.max(1, Math.abs(y - previousY));
            for (int step = 0; step <= steps; step++) {
                float fraction = step / (float) steps;
                int px = x == left ? x : Math.round(x - 1 + fraction);
                int py = Math.round(previousY + (y - previousY) * fraction);
                gray[py * W + px] = 0;
            }
            previousY = y;
        }
    }
}
