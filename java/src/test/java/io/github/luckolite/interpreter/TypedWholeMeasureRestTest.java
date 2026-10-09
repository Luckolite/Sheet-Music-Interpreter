// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original abstract independent silence, meter changes, and chord/triplet clocks. */
public class TypedWholeMeasureRestTest {
    static ScoreRestEvent full() {
        return ScoreRestEvent.fullMeasure(0, .5f, .15f, .03f, 0, 1);
    }

    @Test
    public void legacyRestRecordDefaultsLiteral() {
        var r = new ScoreRestEvent(0, .5f, .15f, .03f, 0, 1, 4);
        assertEquals(ScoreRestEvent.Kind.LITERAL, r.kind());
        assertEquals(4, r.resolvedDurationBeats(3), 0);
    }

    @Test
    public void typedFullMeasureJsonWritesRetainedKindAndBase() throws Exception {
        var json = Main.json(full());
        assertTrue(json.contains("\"kind\":\"FULL_MEASURE\""));
        assertTrue(json.contains("\"durationBeats\":4.0"));
        assertEquals(3, full().resolvedDurationBeats(3), 0);
    }

    @Test
    public void legacyNullRecordKindDefaultsLiteral() {
        assertEquals(
                ScoreRestEvent.Kind.LITERAL,
                new ScoreRestEvent(0, .5f, .15f, .03f, 0, 1, 4, null).kind());
    }

    static List<ScoreNoteEvent> moving(int count) {
        var out = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < count; i++)
            out.add(
                    new ScoreNoteEvent(0, .1f + i * .22f, 0, 0, 1, .6f, false, 0, 0, 2, 1)
                            .withStemDirection(-1));
        return out;
    }

    static void span(float beats, List<ScoreNoteEvent> n) {
        var r = full();
        assertEquals(0, ScoreRestTiming.beatInMeasure(r, List.of(r), n, beats), 0);
        assertEquals(beats, r.resolvedDurationBeats(beats), 0);
        assertTrue(ScoreRestTiming.active(r, List.of(r), n, 0, 0, beats));
        assertTrue(ScoreRestTiming.active(r, List.of(r), n, 0, beats - .001f, beats));
        assertFalse(ScoreRestTiming.active(r, List.of(r), n, 0, beats, beats));
        assertFalse(ScoreRestTiming.active(r, List.of(r), n, 1, 0, beats));
        assertEquals(4, r.durationBeats(), 0);
    }

    @Test
    public void emptyThreeFourUsesThreeBeatsAndWholeGlyph() {
        span(new ScoreMeterChange(0, 3, 4).quarterBeats(), List.of());
    }

    @Test
    public void emptyFourFourUsesFourBeatsAndWholeGlyph() {
        span(new ScoreMeterChange(0, 4, 4).quarterBeats(), List.of());
    }

    @Test
    public void emptySixEightUsesThreeQuarterBeatsAndWholeGlyph() {
        span(new ScoreMeterChange(0, 6, 8).quarterBeats(), List.of());
    }

    @Test
    public void independentThreeFourVoiceSpansSoundingLowerNotes() {
        span(3, moving(3));
    }

    @Test
    public void independentFourFourVoiceSpansSoundingLowerNotes() {
        span(4, moving(4));
    }

    @Test
    public void independentSixEightVoiceSpansSoundingLowerNotes() {
        span(new ScoreMeterChange(0, 6, 8).quarterBeats(), moving(3));
    }

    @Test
    public void explicitPickupSpanIsOneBeatWithoutChangingWholeGlyph() {
        span(1, moving(1));
    }

    @Test
    public void resolverFollowsMeterChanges() {
        var map =
                new ScoreMeterMap(
                        4,
                        List.of(
                                new ScoreMeterChange(1, 3, 4),
                                new ScoreMeterChange(2, 6, 8),
                                new ScoreMeterChange(3, 5, 8)));
        var r = full();
        double[] expected = {4, 3, 3, 2.5};
        for (int i = 0; i < expected.length; i++)
            assertEquals(
                    expected[i],
                    r.withMeasureIndex(i).resolvedDurationBeats(map.beatsInMeasure(i)),
                    0);
    }

    @Test
    public void literalWholeDurationRemainsFourInsideEightBeatBar() {
        var r = new ScoreRestEvent(0, .5f, .4f, .03f, 0, 1, 4);
        assertFalse(r.isFullMeasure());
        assertEquals(4, r.resolvedDurationBeats(8), 0);
        assertEquals(4, ScoreRestTiming.beatInMeasure(r, List.of(r), List.of(), 8), 0);
        assertFalse(ScoreRestTiming.active(r, List.of(r), List.of(), 0, 0, 8));
    }

    @Test
    public void dottedWholeDurationDoesNotBecomeFullMeasure() {
        var r = new ScoreRestEvent(0, .5f, .4f, .03f, 0, 1, 6);
        assertFalse(r.isFullMeasure());
        assertEquals(6, r.resolvedDurationBeats(8), 0);
        assertNotEquals(r, full());
    }

    @Test
    public void oldConstructorsRemainLiteral() {
        assertEquals(
                ScoreRestEvent.Kind.LITERAL, new ScoreRestEvent(0, .5f, .3f, .02f, 0, 1).kind());
        assertEquals(.25, new ScoreRestEvent(0, .5f, .3f, .02f, 0, 1).durationBeats(), 0);
        assertEquals(
                ScoreRestEvent.Kind.LITERAL, new ScoreRestEvent(0, .5f, .3f, .02f, 0, 1, 4).kind());
    }

    @Test
    public void fullKindSurvivesMeasureRebase() {
        var r = full().withMeasureIndex(7);
        assertTrue(r.isFullMeasure());
        assertEquals(7, r.measureIndex());
        assertEquals(full().pageY(), r.pageY(), 0);
        assertEquals(full().pageHeight(), r.pageHeight(), 0);
        assertEquals(3, r.resolvedDurationBeats(3), 0);
    }

    @Test
    public void unresolvedOrInvalidMeasureSpanCannotLightRest() {
        for (float beats : new float[] {Float.NaN, Float.POSITIVE_INFINITY, 0, -1}) {
            assertTrue(Double.isNaN(full().resolvedDurationBeats(beats)));
            assertTrue(
                    Double.isNaN(
                            ScoreRestTiming.beatInMeasure(
                                    full(), List.of(full()), moving(1), beats)));
            assertFalse(ScoreRestTiming.active(full(), List.of(full()), moving(1), 0, 0, beats));
        }
    }

    @Test
    public void literalShortRestStillCannotOverlapSoundingVoice() {
        var r = new ScoreRestEvent(0, .5f, .55f, .08f, 0, 1, 1);
        assertTrue(
                Double.isNaN(ScoreRestTiming.beatInMeasure(r, List.of(r, full()), moving(4), 4)));
    }

    @Test
    public void fullRestDoesNotConsumeLiteralGap() {
        var r = new ScoreRestEvent(0, .6f, .55f, .08f, 0, 1, 1);
        assertEquals(3, ScoreRestTiming.beatInMeasure(r, List.of(r, full()), List.of(), 4), 0);
    }

    @Test
    public void explicitFullRestNeverAddsSilenceToAnyOtherAttack() {
        for (var n : moving(4)) assertFalse(OmrScoreInterpreter.restIsSeparateAttack(full(), n));
        var literal = new ScoreRestEvent(0, .5f, .15f, .03f, 0, 1, 4);
        assertTrue(OmrScoreInterpreter.restIsSeparateAttack(literal, moving(4).get(3)));
    }

    @Test
    public void independentHalfChordAndSixTripletChordsKeepEveryOnset() {
        var ns = new ArrayList<ScoreNoteEvent>();
        for (int member = 0; member < 2; member++)
            ns.add(
                    new ScoreNoteEvent(
                                    0, .1f, member, 0, 1, .64f + member * .03f, false, 0, 0, 2, 2)
                            .withStemDirection(-1));
        for (int attack = 0; attack < 6; attack++)
            for (int member = 0; member < 2; member++)
                ns.add(
                        new ScoreNoteEvent(
                                        0,
                                        .30f + attack * .10f,
                                        member,
                                        0,
                                        1,
                                        .64f + member * .03f,
                                        false,
                                        0,
                                        1,
                                        2,
                                        0,
                                        3)
                                .withStemDirection(-1));
        span(4, ns);
        for (int i = 0; i < ns.size(); i++) {
            var n = ns.get(i);
            assertFalse(OmrScoreInterpreter.restIsSeparateAttack(full(), n));
            assertEquals(0, n.leadingRestBeats(), 0);
            assertEquals(0, n.followingRestBeats(), 0);
            assertEquals(
                    i < 2 ? 0 : 2 + (i - 2) / 2 / 3.,
                    ScoreNoteTiming.beatInMeasure(n, ns, 4),
                    1e-7);
            assertEquals(
                    i < 2 ? 2 : 1. / 3,
                    ScoreNoteTiming.resolvedWrittenDurationBeats(n, ns, 4),
                    1e-7);
        }
    }
}
