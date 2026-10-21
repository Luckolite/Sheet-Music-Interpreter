// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original token/ink/staff operands, not a glyph classifier or private-score replay. */
public class CompoundFpPreservationOriginalTest {
    private static final int W = 1000, H = 400;
    private final List<PlayingTechniqueDetector.Staff> staffs =
            List.of(new PlayingTechniqueDetector.Staff(80, 120, 10, 0, 1));
    private final List<MeasureRegion> measures = List.of(new MeasureRegion(.1f, .9f, .1f, .4f));

    private PlayingTechniqueDetector.Word fp(String text) {
        return new PlayingTechniqueDetector.Word(text, .485f, .34f, .515f, .37f);
    }

    private byte[] ink() {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        // Two independent ink patches ensure both halves contain literal observed ink.
        for (int y = 138; y < 146; y++) for (int x = 488; x < 494; x++) gray[y * W + x] = 0;
        for (int y = 138; y < 146; y++) for (int x = 506; x < 512; x++) gray[y * W + x] = 0;
        return gray;
    }

    private List<ScoreNoteEvent> notes() {
        var result = new ArrayList<ScoreNoteEvent>();
        for (float onset : new float[] {0f, .25f, .5f, .75f})
            result.add(
                    new ScoreNoteEvent(
                            0,
                            onset,
                            2,
                            0,
                            1,
                            .2f,
                            false,
                            0,
                            0,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            1f));
        return List.copyOf(result);
    }

    private List<PlayingTechniqueDetector.Word> qualified(PlayingTechniqueDetector.Word literal) {
        var f = new PlayingTechniqueDetector.Word("f", .485f, .34f, .504f, .37f);
        var p = new PlayingTechniqueDetector.Word("p", .496f, .34f, .515f, .37f);
        var evidence = new CompoundDynamicEvidence(List.of(literal));
        // Input contract: these are already accepted independent component matches.
        // This tests reconciliation, not the template matcher producing that evidence.
        evidence.accepted("f", f, f);
        evidence.accepted("p", p, p);
        // Existing overlap replacement has already deleted f; observations retain its evidence.
        var result = new ArrayList<>(List.of(p));
        evidence.finish(result);
        assertEquals(1, result.size());
        var got = result.get(0);
        assertEquals("fp", got.text());
        assertEquals(literal.left(), got.left(), 0);
        assertEquals(literal.right(), got.right(), 0);
        assertEquals(literal.top(), got.top(), 0);
        assertEquals(literal.bottom(), got.bottom(), 0);
        return result;
    }

    private List<ScoreDynamicChange> detect(List<PlayingTechniqueDetector.Word> words) {
        return ScoreDynamicsDetector.detect(words, staffs, measures, notes(), null, W, H);
    }

    private void orderedFp(List<ScoreDynamicChange> changes) {
        assertEquals(2, changes.size());
        assertEquals(3f, changes.get(0).decibels(), 0);
        assertEquals(-8f, changes.get(1).decibels(), 0);
        for (var c : changes) {
            assertEquals(0, c.measureIndex());
            assertEquals(.5f, c.positionInMeasure(), .00001f);
            assertEquals(0, c.staffIndex());
            assertEquals(1, c.staffCount());
            assertEquals(0, c.direction());
            assertEquals(c.measureIndex(), c.endMeasureIndex());
            assertEquals(c.positionInMeasure(), c.endPosition(), 0);
        }
    }

    /** Test-only bridge: unchanged actual producer records, never a hand-built dynamic pair. */
    public static List<ScoreDynamicChange> producedFpChanges() {
        var fixture = new CompoundFpPreservationOriginalTest();
        var words = ScoreDynamicsDetector.ocrWords("fp", fixture.fp("fp"), fixture.ink(), W, H);
        assertEquals(1, words.size());
        var changes = fixture.detect(fixture.qualified(words.get(0)));
        fixture.orderedFp(changes);
        return changes;
    }

    public static List<ScoreNoteEvent> producedNotes() {
        return new CompoundFpPreservationOriginalTest().notes();
    }

    @Test
    public void singleLiteralRetainsOriginalBoundsAndProducesOrderedSameAttack() {
        var literal = fp("fp");
        var gray = ink();
        var copy = gray.clone();
        var words = ScoreDynamicsDetector.ocrWords("fp", literal, gray, W, H);
        assertEquals(List.of(literal), words);
        assertArrayEquals(copy, gray);
        assertTrue(detect(words).isEmpty());
        orderedFp(detect(qualified(words.get(0))));
    }

    @Test
    public void repeatedOcrAndDetectionAreStableWithoutDuplicateChanges() {
        var gray = ink();
        var first = ScoreDynamicsDetector.ocrWords("fp", fp("fp"), gray, W, H);
        assertEquals(1, first.size());
        var second = ScoreDynamicsDetector.ocrWords("fp", first.get(0), gray, W, H);
        assertEquals(first, second);
        var q1 = qualified(first.get(0));
        var q2 = qualified(second.get(0));
        assertEquals(q1, q2);
        assertEquals(detect(q1), detect(q2));
        orderedFp(detect(q2));
        var repeated = new ArrayList<>(q1);
        new CompoundDynamicEvidence(q1).finish(repeated);
        assertEquals(q1, repeated);
        orderedFp(detect(repeated));
    }

    @Test
    public void separatePrintedFAndPKeepTheirDistinctPositions() {
        var f = new PlayingTechniqueDetector.Word("f", .25f, .34f, .27f, .37f);
        var p = new PlayingTechniqueDetector.Word("p", .65f, .34f, .67f, .37f);
        var both = detect(List.of(f, p));
        var one = detect(List.of(f));
        var two = detect(List.of(p));
        assertEquals(2, both.size());
        assertEquals(List.of(one.get(0), two.get(0)), both);
        assertTrue(both.get(0).positionInMeasure() < both.get(1).positionInMeasure());
        assertEquals(3f, both.get(0).decibels(), 0);
        assertEquals(-8f, both.get(1).decibels(), 0);
    }

    @Test
    public void ordinaryWordsAndMalformedCompoundsDoNotCreateFp() {
        for (String text : List.of("fpx", "fpiano", "performance", "f/p"))
            assertTrue(text, ScoreDynamicsDetector.ocrWords(text, fp(text), ink(), W, H).isEmpty());
        assertTrue(
                ScoreDynamicsDetector.ocrWords("a poem fp home", fp("fp"), ink(), W, H).isEmpty());
    }

    @Test
    public void MissingInkCannotAuthorizeCompound() {
        byte[] paper = new byte[W * H];
        Arrays.fill(paper, (byte) 255);
        assertTrue(ScoreDynamicsDetector.ocrWords("fp", fp("fp"), paper, W, H).isEmpty());
    }

    @Test
    public void OtherPackedLevelsAndSustainedForteKeepExistingTokens() {
        assertEquals(List.of("mf", "mp"), ScoreDynamicsDetector.packedLevels("mfmp"));
        assertEquals(List.of("f", "mp"), ScoreDynamicsDetector.packedLevels("fmp"));
        assertEquals(List.of("f", "p"), ScoreDynamicsDetector.packedLevels("fp"));
        for (String text : List.of("mfmp", "fmp")) {
            var words = ScoreDynamicsDetector.ocrWords(text, fp(text), ink(), W, H);
            assertEquals(
                    ScoreDynamicsDetector.packedLevels(text),
                    words.stream().map(PlayingTechniqueDetector.Word::text).toList());
            assertTrue(words.get(0).right() <= words.get(1).left());
        }
        var fff = ScoreDynamicsDetector.ocrWords("fff", fp("fff"), ink(), W, H);
        assertEquals(1, fff.size());
        assertEquals("fff", fff.get(0).text());
    }

    @Test
    public void MissingHalfCannotTurnChildPIntoQualifiedCompound() {
        var literal = fp("fp");
        var p = new PlayingTechniqueDetector.Word("p", .496f, .34f, .515f, .37f);
        var evidence = new CompoundDynamicEvidence(List.of(literal));
        evidence.accepted("p", p, p);
        var result = new ArrayList<>(List.of(p));
        evidence.finish(result);
        assertEquals(List.of(p), result);
    }

    @Test
    public void OverlappingOriginalOwnersCannotSupplyUniqueCompoundIdentity() {
        var a = fp("fp");
        var b = new PlayingTechniqueDetector.Word("fp", .48f, .335f, .52f, .375f);
        var f = new PlayingTechniqueDetector.Word("f", .485f, .34f, .504f, .37f);
        var p = new PlayingTechniqueDetector.Word("p", .496f, .34f, .515f, .37f);
        var evidence = new CompoundDynamicEvidence(List.of(a, b));
        evidence.accepted("f", f, f);
        evidence.accepted("p", p, p);
        var result = new ArrayList<>(List.of(f, p));
        evidence.finish(result);
        assertEquals(List.of(f, p), result);
    }

    @Test
    public void CompetingChildOrReversedOrderCannotBecomeOneFp() {
        var f = new PlayingTechniqueDetector.Word("f", .485f, .34f, .504f, .37f);
        var otherF = new PlayingTechniqueDetector.Word("f", .486f, .34f, .505f, .37f);
        var p = new PlayingTechniqueDetector.Word("p", .496f, .34f, .515f, .37f);
        var evidence = new CompoundDynamicEvidence(List.of(fp("fp")));
        evidence.accepted("f", f, f);
        evidence.accepted("f", otherF, otherF);
        evidence.accepted("p", p, p);
        var result = new ArrayList<>(List.of(f, otherF, p));
        evidence.finish(result);
        assertEquals(List.of(f, otherF, p), result);
        var leftP = new PlayingTechniqueDetector.Word("p", .485f, .34f, .498f, .37f);
        var rightF = new PlayingTechniqueDetector.Word("f", .5f, .34f, .515f, .37f);
        evidence = new CompoundDynamicEvidence(List.of(fp("fp")));
        evidence.accepted("p", leftP, leftP);
        evidence.accepted("f", rightF, rightF);
        result = new ArrayList<>(List.of(leftP, rightF));
        evidence.finish(result);
        assertEquals(List.of(leftP, rightF), result);
    }

    @Test
    public void SeparatePrintedTokensAreNotAnOriginalCompoundOwner() {
        var f = new PlayingTechniqueDetector.Word("f", .485f, .34f, .504f, .37f);
        var p = new PlayingTechniqueDetector.Word("p", .496f, .34f, .515f, .37f);
        var evidence = new CompoundDynamicEvidence(List.of(f, p));
        evidence.accepted("f", f, f);
        evidence.accepted("p", p, p);
        var result = new ArrayList<>(List.of(f, p));
        evidence.finish(result);
        assertEquals(List.of(f, p), result);
    }

    @Test
    public void NonfiniteOrEmptyOriginalBoundsNeverAuthorizeFp() {
        var f = new PlayingTechniqueDetector.Word("f", .485f, .34f, .504f, .37f);
        var p = new PlayingTechniqueDetector.Word("p", .496f, .34f, .515f, .37f);
        for (var bad :
                List.of(
                        new PlayingTechniqueDetector.Word("fp", Float.NaN, .34f, .515f, .37f),
                        new PlayingTechniqueDetector.Word("fp", .485f, .34f, .485f, .37f))) {
            var evidence = new CompoundDynamicEvidence(List.of(bad));
            evidence.accepted("f", f, f);
            evidence.accepted("p", p, p);
            var result = new ArrayList<>(List.of(f, p));
            evidence.finish(result);
            assertEquals(List.of(f, p), result);
        }
    }

    @Test
    public void OneWideOcrFpCannotMergeTwoSeparatedPrintedLevels() {
        var wide = new PlayingTechniqueDetector.Word("fp", .25f, .34f, .75f, .37f);
        var f = new PlayingTechniqueDetector.Word("f", .25f, .34f, .27f, .37f);
        var p = new PlayingTechniqueDetector.Word("p", .73f, .34f, .75f, .37f);
        var evidence = new CompoundDynamicEvidence(List.of(wide));
        evidence.accepted("f", f, f);
        evidence.accepted("p", p, p);
        var result = new ArrayList<>(List.of(f, p));
        evidence.finish(result);
        assertEquals(List.of(f, p), result);
        var changes = detect(result);
        assertEquals(2, changes.size());
        assertTrue(changes.get(0).positionInMeasure() < changes.get(1).positionInMeasure());
    }

    @Test
    public void PreviouslyQualifiedFpSurvivesActualChildReplacementOnSecondPass() {
        var original = qualified(fp("fp")).get(0);
        var child = new PlayingTechniqueDetector.Word("p", .496f, .34f, .515f, .37f);
        var evidence = new CompoundDynamicEvidence(List.of(original));
        evidence.accepted("p", child, child);
        // Mirrors the original generic overlap remove/add: only the new child remains.
        var result = new ArrayList<>(List.of(child));
        evidence.finish(result);
        assertEquals(List.of(original), result);
        orderedFp(detect(result));
    }
}
