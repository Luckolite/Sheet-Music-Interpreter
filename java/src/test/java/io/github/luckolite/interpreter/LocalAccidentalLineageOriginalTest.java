// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class LocalAccidentalLineageOriginalTest {
    static final int W = 420, H = 260;
    static final List<MeasureRegion> MEASURES = List.of(new MeasureRegion(0, 1, .1f, .9f));
    static final MeterChangeDetector.Crop CROP =
            new MeterChangeDetector.Crop(247, 100, 264, 148, 100, 16);

    static OmrScoreInterpreter.Analysis analyze(SharpPitchAlignmentTest.Page p) {
        return OmrScoreInterpreter.analyze(p.labels, p.gray, W, H, MEASURES);
    }

    static AcceptedMeterHeaderBounds.Source source(SharpPitchAlignmentTest.Page p) {
        return AcceptedMeterHeaderBounds.source(p.labels, p.gray, W, H, MEASURES);
    }

    static SourceLocalAccidental local(OmrScoreInterpreter.Analysis a) {
        return a.clockProducerSource().localAccidentals();
    }

    static ScoreMeterProducerSource meter(SharpPitchAlignmentTest.Page p) {
        var s = source(p);
        return ScoreMeterProducerSource.capture(
                s,
                List.of(
                        new ScoreMeterProducerSource.Observation(
                                s, 0, 0, CROP, "", 0, "UNREAD_CANDIDATE"),
                        new ScoreMeterProducerSource.Observation(
                                s, 1, 0, CROP, "3/4", 1, "CONTRARY_READER_OBSERVATION")),
                List.of());
    }

    @Test
    public void genuineNormalSelectionRetainsActualWinnerAndCompleteOriginalMembers() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var l = local(a);
        assertTrue(l.complete());
        assertEquals(1, l.headCount());
        assertEquals(1, l.headAlternatives().size());
        assertEquals(1, l.glyphs().size());
        assertFalse(l.headAlternatives().get(0).pixels().isEmpty());
        assertEquals(1, l.choices().size());
        var c = l.choices().get(0);
        assertEquals("MODEL_SUPPORTED_ACTUAL_LOCAL_ACCIDENTAL", c.status());
        assertEquals(0, c.glyph());
        assertEquals(0, c.head());
        assertEquals(a.notes().get(c.note()), c.originalNote());
        assertEquals(1, c.accidental());
        assertFalse(c.ambiguous());
        assertNotNull(l.role(source(p), CROP, 0, a.notes()));
    }

    @Test
    public void originalUnreadAndConflictingReadingsRemainExactPrefix() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var old = meter(p);
        var result =
                old.withLocalAccidentalExclusions(
                        p.labels, p.gray, W, H, MEASURES, local(a), a.notes());
        assertEquals(old.readings(), result.readings().subList(0, 2));
        assertEquals(4, result.readings().size());
        assertEquals(
                List.of(0, 1, 2, 3),
                result.readings().stream().map(ScoreMeterProducerSource.Reading::ordinal).toList());
        assertTrue(result.readings().get(2).status().contains("NOT_METER_ABSENCE_AUTHORITY"));
    }

    @Test
    public void actualContextConsumerAppendsOnlyModelFactsAndKeepsScopeUnknown() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var page =
                new ScorePageInterpretation(
                        MEASURES,
                        a.notes(),
                        0,
                        a.keyChanges(),
                        List.of(),
                        List.of(),
                        a.rests(),
                        List.of(),
                        List.of());
        var c = ScoreClockContextCapture.capture(p.labels, p.gray, W, H, page, a, null, meter(p));
        assertEquals(4, c.meters().size());
        assertEquals("3/4", c.meters().get(1).text());
        assertEquals(
                ScoreClockContextSource.MeterRoute.HEADER_MODEL_EXCLUSION,
                c.meters().get(2).route());
        assertTrue(
                c.missing().stream()
                        .anyMatch(
                                m ->
                                        m.requirement()
                                                == ScoreClockContextSource.Requirement
                                                        .METER_SCOPE));
        assertTrue(a.tremoloSource().pairs().isEmpty());
    }

    @Test
    public void adjacentPitchSharpStaysUnexplained() {
        var p = new SharpPitchAlignmentTest.Page(116, 124);
        var a = analyze(p);
        assertNull(local(a).role(source(p), CROP, 0, a.notes()));
    }

    @Test
    public void tinyDroppedGlyphIslandRemainsContraryEvidence() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        p.pixel(247, 100, 3);
        var a = analyze(p);
        assertEquals(1, a.notes().get(0).writtenAccidental());
        assertTrue(local(a).unrepresentedGlyphMembers().contains(100 * W + 247));
        assertNull(local(a).role(source(p), CROP, 0, a.notes()));
    }

    @Test
    public void sourceMutationAndForeignRasterRefuse() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var s = source(p);
        p.gray[0] = 0;
        assertNull(local(a).role(s, CROP, 0, a.notes()));
        assertNull(local(a).role(source(p), CROP, 0, a.notes()));
        var other = new SharpPitchAlignmentTest.Page(124, 124);
        other.pixel(0, 0, 5);
        assertNull(local(a).role(source(other), CROP, 0, a.notes()));
    }

    @Test
    public void changedFinalHeadRecordAndWrongMeasureRefuse() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var notes = new ArrayList<>(a.notes());
        notes.set(0, notes.get(0).withStemDirection(1));
        assertNull(local(a).role(source(p), CROP, 0, notes));
        assertNull(local(a).role(source(p), CROP, 1, a.notes()));
    }

    @Test
    public void unknownNativeProducerCannotAssertAnAccidentalRole() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        assertNull(SourceLocalAccidental.unavailable().role(source(p), CROP, 0, a.notes()));
    }

    @Test
    public void deserializedLedgerDoesNotManufactureAnIssuerCapability() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var gson = new com.google.gson.Gson();
        var copied = gson.fromJson(gson.toJson(local(a)), SourceLocalAccidental.class);
        assertNull(copied.role(source(p), CROP, 0, a.notes()));
    }

    @Test
    public void nonHeadGlyphCapabilityCannotBeUsedAsAcceptedProbeHead() throws Exception {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var collector = OmrScoreInterpreter.tremoloMemberCollector(p.gray, p.labels, W, H);
        var find =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "findComponents",
                        byte[].class,
                        int.class,
                        int.class,
                        byte.class,
                        byte[].class,
                        java.util.IdentityHashMap.class,
                        ExactComponentMembers.Collector.class);
        find.setAccessible(true);
        var glyphs = (List<?>) find.invoke(null, p.labels, W, H, (byte) 3, p.gray, null, collector);
        assertTrue(collector.seal(new ExactComponentMembers.Budget(1000000, 1000000)));
        var glyph = glyphs.get(0);
        assertNotNull(collector.acceptedGlyph(glyph));
        assertNull(collector.acceptedProbeHead(glyph, 0, 0));
        var clone =
                new com.google.gson.Gson()
                        .fromJson(new com.google.gson.Gson().toJson(glyph), glyph.getClass());
        assertNotSame(glyph, clone);
        assertNull(collector.acceptedGlyph(clone));
        assertFalse(collector.acceptedGlyph(glyph).authenticates(p.labels.clone(), p.gray, W, H));
    }

    @Test
    public void emittedVectorsCannotBePrunedByConsumer() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var l = local(analyze(p));
        try {
            l.glyphs().get(0).pixels().clear();
            fail("immutable");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            l.headAlternatives().get(0).pixels().clear();
            fail("immutable");
        } catch (UnsupportedOperationException expected) {
        }
    }

    static ExactComponentMembers.Budget budget() {
        return new ExactComponentMembers.Budget(4000000, 8000000);
    }

    static List<?> find(
            SharpPitchAlignmentTest.Page p, byte label, ExactComponentMembers.Collector c)
            throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "findComponents",
                        byte[].class,
                        int.class,
                        int.class,
                        byte.class,
                        byte[].class,
                        java.util.IdentityHashMap.class,
                        ExactComponentMembers.Collector.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(null, p.labels, W, H, label, p.gray, null, c);
    }

    static OmrScoreInterpreter.WrittenAccidentalChoice choose(
            SharpPitchAlignmentTest.Page p,
            Object head,
            List<?> glyphs,
            List<?> heads,
            boolean duplicate)
            throws Exception {
        var component = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var candidate = Class.forName(OmrScoreInterpreter.class.getName() + "$AccidentalCandidate");
        var ctor = candidate.getDeclaredConstructor(component, byte.class);
        ctor.setAccessible(true);
        var candidates = new ArrayList<Object>();
        for (var glyph : glyphs) candidates.add(ctor.newInstance(glyph, (byte) 3));
        if (duplicate) candidates.add(ctor.newInstance(glyphs.get(0), (byte) 3));
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "chooseWrittenAccidental",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        component,
                        float.class,
                        List.class,
                        byte[].class);
        m.setAccessible(true);
        return (OmrScoreInterpreter.WrittenAccidentalChoice)
                m.invoke(null, p.labels, W, H, candidates, head, 16f, heads, p.gray);
    }

    @Test
    public void equalWinnerAlternativesRetainLegacyChoiceButRefuseNewAuthority() throws Exception {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var hc = OmrScoreInterpreter.tremoloMemberCollector(p.gray, p.labels, W, H);
        var gc = OmrScoreInterpreter.tremoloMemberCollector(p.gray, p.labels, W, H);
        var sc = OmrScoreInterpreter.tremoloMemberCollector(p.gray, p.labels, W, H);
        var heads = find(p, (byte) 2, hc);
        var glyphs = find(p, (byte) 3, gc);
        find(p, (byte) 5, sc);
        assertTrue(hc.seal(budget()));
        assertTrue(gc.seal(budget()));
        assertTrue(sc.seal(budget()));
        var choice = choose(p, heads.get(0), glyphs, heads, true);
        assertEquals(1, choice.accidental());
        assertTrue(choice.ambiguous());
        assertSame(heads.get(0), choice.head());
        assertSame(glyphs.get(0), choice.glyph());
        var choices = new IdentityHashMap<Object, OmrScoreInterpreter.WrittenAccidentalChoice>();
        choices.put(heads.get(0), choice);
        var local =
                SourceLocalAccidental.capture(sc, gc, glyphs, heads, hc, choices, heads, a.notes());
        assertTrue(local.complete());
        assertTrue(local.choices().get(0).status().startsWith("UNKNOWN"));
        assertNull(local.role(source(p), CROP, 0, a.notes()));
    }

    static ExactComponentMembers.Stats stats(List<Integer> pixels) {
        int x0 = W, x1 = -1, y0 = H, y1 = -1;
        long sx = 0, sy = 0;
        for (int at : pixels) {
            int x = at % W, y = at / W;
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            y0 = Math.min(y0, y);
            y1 = Math.max(y1, y);
            sx += x;
            sy += y;
        }
        return new ExactComponentMembers.Stats(
                pixels.size(),
                x0,
                x1,
                y0,
                y1,
                sx / (float) pixels.size(),
                sy / (float) pixels.size());
    }

    static Object component(List<Integer> pixels) throws Exception {
        var s = stats(pixels);
        var cls = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor =
                cls.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        ctor.setAccessible(true);
        return ctor.newInstance(
                s.area(), s.minX(), s.maxX(), s.minY(), s.maxY(), s.centerX(), s.centerY());
    }

    @Test
    public void authenticParentResidualRemainsExplicitAndRefusesSelectedHeadRole()
            throws Exception {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var hc = OmrScoreInterpreter.tremoloMemberCollector(p.gray, p.labels, W, H);
        var gc = OmrScoreInterpreter.tremoloMemberCollector(p.gray, p.labels, W, H);
        var sc = OmrScoreInterpreter.tremoloMemberCollector(p.gray, p.labels, W, H);
        var originals = find(p, (byte) 2, hc);
        var glyphs = find(p, (byte) 3, gc);
        find(p, (byte) 5, sc);
        var left = new ArrayList<Integer>();
        var right = new ArrayList<Integer>();
        int residual = -1;
        for (int at = 0; at < p.labels.length; at++)
            if (p.labels[at] == 2) {
                if (residual < 0) {
                    residual = at;
                    continue;
                }
                if (at % W < 280) left.add(at);
                else right.add(at);
            }
        Object first = component(left), second = component(right);
        var split = hc.beginSplit(originals.get(0), p.labels, W);
        for (var entry : List.of(Map.entry(first, left), Map.entry(second, right))) {
            var builder = split.beginChild();
            for (int at : entry.getValue()) builder.add(at / W, at % W, at % W);
            split.capture(entry.getKey(), stats(entry.getValue()), builder);
        }
        assertTrue(split.commitRetainingResidual(List.of(first, second)));
        assertTrue(hc.seal(budget()));
        assertTrue(gc.seal(budget()));
        assertTrue(sc.seal(budget()));
        var heads = List.of(first, second);
        var choice = choose(p, first, glyphs, heads, false);
        assertEquals(1, choice.accidental());
        var choices = new IdentityHashMap<Object, OmrScoreInterpreter.WrittenAccidentalChoice>();
        choices.put(first, choice);
        // Deliberately adverse proposed binding: even a matching printed accidental cannot consume
        // a residual-bearing parent.
        var local =
                SourceLocalAccidental.capture(
                        sc, gc, glyphs, heads, hc, choices, List.of(first), a.notes());
        assertEquals(List.of(residual), local.headAlternatives().get(0).unresolvedParentResidual());
        assertTrue(local.choices().get(0).status().startsWith("UNKNOWN"));
        assertNull(local.role(source(p), CROP, 0, a.notes()));
    }

    @Test
    public void duplicateFinalHeadRecordIsAnAmbiguousOwner() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var notes = new ArrayList<>(a.notes());
        notes.add(a.notes().get(0));
        assertNull(local(a).role(source(p), CROP, 0, notes));
    }

    @Test
    public void olderObservationalJsonDefaultsToUnavailableCapability() {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var gson = new com.google.gson.Gson();
        var json = gson.toJsonTree(a.clockProducerSource()).getAsJsonObject();
        json.remove("localAccidentals");
        var legacy = gson.fromJson(json, ScoreClockProducerSource.class);
        var expected =
                a.clockProducerSource().withLocalAccidentals(SourceLocalAccidental.unavailable());
        assertEquals(expected, legacy);
        assertEquals(expected.hashCode(), legacy.hashCode());
        assertNull(legacy.localAccidentals().role(source(p), CROP, 0, a.notes()));
    }

    @Test
    public void duplicateSourceIdentitiesAreRetainedAsUnknownWithoutRosterPruning()
            throws Exception {
        var p = new SharpPitchAlignmentTest.Page(124, 124);
        var a = analyze(p);
        var hc = OmrScoreInterpreter.tremoloMemberCollector(p.gray, p.labels, W, H);
        var gc = OmrScoreInterpreter.tremoloMemberCollector(p.gray, p.labels, W, H);
        var sc = OmrScoreInterpreter.tremoloMemberCollector(p.gray, p.labels, W, H);
        var heads = find(p, (byte) 2, hc);
        var glyphs = find(p, (byte) 3, gc);
        find(p, (byte) 5, sc);
        assertTrue(hc.seal(budget()));
        assertTrue(gc.seal(budget()));
        assertTrue(sc.seal(budget()));
        var choice = choose(p, heads.get(0), glyphs, heads, false);
        var choices = new IdentityHashMap<Object, OmrScoreInterpreter.WrittenAccidentalChoice>();
        choices.put(heads.get(0), choice);
        var local =
                SourceLocalAccidental.capture(
                        sc,
                        gc,
                        List.of(glyphs.get(0), glyphs.get(0)),
                        List.of(heads.get(0), heads.get(0)),
                        hc,
                        choices,
                        heads,
                        a.notes());
        assertFalse(local.complete());
        assertEquals(2, local.glyphs().size());
        assertEquals(2, local.headAlternatives().size());
        assertFalse(local.headAlternatives().get(0).pixels().isEmpty());
        assertEquals(
                local.headAlternatives().get(0).pixels(), local.headAlternatives().get(1).pixels());
        assertNull(local.role(source(p), CROP, 0, a.notes()));
    }
}
