// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.IOException;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TremoloClockPolicyOriginalTest {
    private static ScoreNoteEvent note(float position, int pitch, float written) {
        return new ScoreNoteEvent(0, position, pitch, 0, 1, .4f, false, 0, 0, 2, written);
    }

    private static ScoreTremoloSource source(ScoreTremoloSource.ClockPolicy policy, int count) {
        List<ScoreTremoloSource.Anchor> first = List.of(new ScoreTremoloSource.Anchor(0, 0));
        List<ScoreTremoloSource.Anchor> second = List.of(new ScoreTremoloSource.Anchor(0, 1));
        var evidence = List.of(new ScoreTremoloSource.Evidence(0, .1f, .2f, .3f, .4f));
        var pair = new ScoreTremoloSource.Pair("pair", first, second, 3, evidence, policy);
        List<Optional<ScoreTremoloSource.Anchor>> origins = new ArrayList<>();
        for (int i = 0; i < count; i++)
            origins.add(Optional.of(new ScoreTremoloSource.Anchor(0, i)));
        return new ScoreTremoloSource(List.of(pair), origins);
    }

    private static List<ScoreNoteEvent> two(float a, float b) {
        return List.of(note(0, 0, a), note(.5f, 2, b));
    }

    @Test
    public void unresolvedPhysicalPairKeepsOrdinaryWrittenAdvances() {
        var notes = two(2, 1);
        var result =
                ScoreTremoloTiming.resolveClocks(
                        notes, source(ScoreTremoloSource.ClockPolicy.UNRESOLVED, 2));
        assertTrue(result.advances().isEmpty());
        assertEquals(1, result.unresolvedClockPairs().size());
        assertEquals(2, ScoreNoteTiming.writtenDurationBeats(notes.get(0)), 0);
        assertEquals(1, ScoreNoteTiming.writtenDurationBeats(notes.get(1)), 0);
    }

    @Test
    public void explicitSharedDurationHalvesBothEndpoints() {
        var notes = two(2, 2);
        var result =
                ScoreTremoloTiming.resolveClocks(
                        notes, source(ScoreTremoloSource.ClockPolicy.SHARED_DURATION, 2));
        assertTrue(result.unresolvedClockPairs().isEmpty());
        assertEquals(1d, result.advances().get(notes.get(0)), 0);
        assertEquals(1d, result.advances().get(notes.get(1)), 0);
    }

    @Test
    public void explicitIndependentEndpointsRetainTheirOwnDurations() {
        var notes = two(2, 1);
        var result =
                ScoreTremoloTiming.resolveClocks(
                        notes,
                        source(ScoreTremoloSource.ClockPolicy.INDEPENDENT_ENDPOINT_DURATIONS, 2));
        assertEquals(2d, result.advances().get(notes.get(0)), 0);
        assertEquals(1d, result.advances().get(notes.get(1)), 0);
        assertTrue(result.unresolvedClockPairs().isEmpty());
    }

    @Test
    public void mixedChordMembersMustAgreeWithinEachAttack() {
        var notes = List.of(note(0, 0, 2), note(0, 4, 2), note(.5f, 1, 1), note(.5f, 5, 1));
        var pair =
                new ScoreTremoloSource.Pair(
                        "mixed",
                        List.of(
                                new ScoreTremoloSource.Anchor(0, 0),
                                new ScoreTremoloSource.Anchor(0, 1)),
                        List.of(
                                new ScoreTremoloSource.Anchor(0, 2),
                                new ScoreTremoloSource.Anchor(0, 3)),
                        3,
                        List.of(new ScoreTremoloSource.Evidence(0, .1f, .2f, .3f, .4f)),
                        ScoreTremoloSource.ClockPolicy.INDEPENDENT_ENDPOINT_DURATIONS);
        var src =
                new ScoreTremoloSource(
                        List.of(pair),
                        List.of(
                                Optional.of(new ScoreTremoloSource.Anchor(0, 0)),
                                Optional.of(new ScoreTremoloSource.Anchor(0, 1)),
                                Optional.of(new ScoreTremoloSource.Anchor(0, 2)),
                                Optional.of(new ScoreTremoloSource.Anchor(0, 3))));
        var result = ScoreTremoloTiming.resolveClocks(notes, src);
        assertEquals(2d, result.advances().get(notes.get(0)), 0);
        assertEquals(2d, result.advances().get(notes.get(1)), 0);
        assertEquals(1d, result.advances().get(notes.get(2)), 0);
        assertEquals(1d, result.advances().get(notes.get(3)), 0);
        var invalid = List.of(note(0, 0, 2), note(0, 4, 1), note(.5f, 1, 1), note(.5f, 5, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScoreTremoloTiming.resolveClocks(invalid, src));
    }

    @Test
    public void ambiguousRetainedIdentityStaysUnresolved() {
        var src = source(ScoreTremoloSource.ClockPolicy.SHARED_DURATION, 3);
        var original = List.of(note(0, 0, 2), note(.5f, 2, 2), note(0, 0, 2));
        var retained = List.of(note(0, 0, 2), note(.5f, 2, 2));
        var remapped = src.retainFinalHeads(original, retained);
        assertEquals(1, remapped.resolve().unresolved().size());
        assertEquals(
                ScoreTremoloSource.ClockPolicy.SHARED_DURATION,
                remapped.resolve().unresolved().get(0).clockPolicy());
        var clock = ScoreTremoloTiming.resolveClocks(retained, remapped);
        assertTrue(clock.advances().isEmpty());
        assertEquals(1, clock.unresolvedClockPairs().size());
    }

    @Test
    public void policySurvivesPageJoinEditRetentionResolveAndDeletedEndpoint() {
        var src = source(ScoreTremoloSource.ClockPolicy.UNRESOLVED, 2);
        var onPage = src.onPage(7);
        var joined = ScoreTremoloSource.join(List.of(onPage));
        var retained = joined.retainFinalHeads(two(2, 2), two(2, 2));
        assertEquals(
                ScoreTremoloSource.ClockPolicy.UNRESOLVED, retained.pairs().get(0).clockPolicy());
        var edited = retained.edited(List.of(1, 0));
        assertEquals(
                ScoreTremoloSource.ClockPolicy.UNRESOLVED, edited.pairs().get(0).clockPolicy());
        assertEquals(1, edited.resolve().resolved().get(0).first().get(0).intValue());
        assertEquals(
                ScoreTremoloSource.ClockPolicy.UNRESOLVED,
                edited.resolve().resolved().get(0).source().clockPolicy());
        var deleted = retained.edited(List.of(0, -1));
        assertEquals(1, deleted.resolve().unresolved().size());
        assertEquals(
                ScoreTremoloSource.ClockPolicy.UNRESOLVED,
                deleted.resolve().unresolved().get(0).clockPolicy());
        assertEquals(
                1,
                ScoreTremoloTiming.resolveClocks(List.of(two(2, 2).get(0)), deleted)
                        .unresolvedClockPairs()
                        .size());
    }

    @Test
    public void legacyTrs1ReadsAsLegacyAndNewPoliciesRoundTripWithoutDowngrade() throws Exception {
        var legacy = source(ScoreTremoloSource.ClockPolicy.LEGACY_SHARED_DURATION, 2);
        byte[] v1 = ScoreTremoloWire.encode(legacy);
        assertEquals(0x54525331, java.nio.ByteBuffer.wrap(v1).getInt());
        assertEquals(legacy, ScoreTremoloWire.decode(v1, 2));
        var unresolved = source(ScoreTremoloSource.ClockPolicy.UNRESOLVED, 2);
        byte[] v2 = ScoreTremoloWire.encode(unresolved);
        assertEquals(0x54525332, java.nio.ByteBuffer.wrap(v2).getInt());
        assertEquals(unresolved, ScoreTremoloWire.decode(v2, 2));
        assertThrows(IOException.class, () -> ScoreTremoloWire.encodeTrs1(unresolved));
        byte[] badPolicy = v2.clone();
        badPolicy[48] = (byte) 255;
        assertThrows(IOException.class, () -> ScoreTremoloWire.decode(badPolicy, 2));
    }

    @Test
    public void fiveArgumentApiAndLegacyWireKeepSharedClockBehavior() {
        var a = new ScoreTremoloSource.Anchor(0, 0);
        var b = new ScoreTremoloSource.Anchor(0, 1);
        var pair =
                new ScoreTremoloSource.Pair(
                        "old",
                        List.of(a),
                        List.of(b),
                        3,
                        List.of(new ScoreTremoloSource.Evidence(0, .1f, .2f, .3f, .4f)));
        assertEquals(ScoreTremoloSource.ClockPolicy.LEGACY_SHARED_DURATION, pair.clockPolicy());
        var notes = two(2, 2);
        var result =
                ScoreTremoloTiming.resolveClocks(
                        notes,
                        new ScoreTremoloSource(
                                List.of(pair), List.of(Optional.of(a), Optional.of(b))));
        assertEquals(1d, result.advances().get(notes.get(0)), 0);
        assertEquals(1d, result.advances().get(notes.get(1)), 0);
    }

    @Test
    public void unresolvedOwnershipSurvivesButPerformanceConsumersAbstain() {
        var unknown = source(ScoreTremoloSource.ClockPolicy.UNRESOLVED, 2);
        assertEquals(1, unknown.resolve().resolved().size());
        assertFalse(unknown.hasPerformancePairs());
        assertTrue(unknown.performanceResolution().resolved().isEmpty());
        assertEquals(
                unknown.resolve(), source(ScoreTremoloSource.ClockPolicy.UNRESOLVED, 2).resolve());
        var legacy = source(ScoreTremoloSource.ClockPolicy.LEGACY_SHARED_DURATION, 2);
        assertTrue(legacy.hasPerformancePairs());
        assertEquals(legacy.resolve(), legacy.performanceResolution());
    }

    @Test
    public void guideGateCannotDowngradeUnresolvedSource() throws Exception {
        var unknown = source(ScoreTremoloSource.ClockPolicy.UNRESOLVED, 2);
        var bytes = new java.io.ByteArrayOutputStream();
        try {
            ScoreTremoloWire.write(new java.io.DataOutputStream(bytes), unknown, false);
            fail();
        } catch (IOException expected) {
            assertEquals(0, bytes.size());
        }
        byte[] encoded = ScoreTremoloWire.encode(unknown);
        try {
            ScoreTremoloWire.read(
                    new java.io.DataInputStream(new java.io.ByteArrayInputStream(encoded)),
                    2,
                    false);
            fail();
        } catch (IOException expected) {
        }
        assertEquals(
                unknown,
                ScoreTremoloWire.read(
                        new java.io.DataInputStream(new java.io.ByteArrayInputStream(encoded)),
                        2,
                        true));
    }

    @Test
    public void legacyTimingMatchesPriorImplementationExactly() {
        for (float duration : new float[] {.1f, .5f, 1f, 2f}) {
            for (float second : new float[] {duration, Math.nextUp(duration), duration * 2}) {
                var notes = two(duration, second);
                var legacy = source(ScoreTremoloSource.ClockPolicy.LEGACY_SHARED_DURATION, 2);
                java.util.IdentityHashMap<ScoreNoteEvent, Double> expected;
                try {
                    expected = LegacyTremoloClockReference.advances(notes, legacy);
                } catch (IllegalArgumentException priorRefusal) {
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> ScoreTremoloTiming.advances(notes, legacy));
                    continue;
                }
                var actual = ScoreTremoloTiming.advances(notes, legacy);
                assertEquals(expected.size(), actual.size());
                for (var note : notes) assertEquals(expected.get(note), actual.get(note));
            }
        }
    }
}
