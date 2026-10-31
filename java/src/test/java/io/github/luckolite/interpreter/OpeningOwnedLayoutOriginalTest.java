// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete two-head opening; source member extent, never a barline inference. */
public final class OpeningOwnedLayoutOriginalTest {
    static final int W = 1200, H = 400;
    static final List<PlayingTechniqueDetector.Staff> STAFFS = OpeningBeamPickupTest.STAFFS;
    static final List<MeterChangeDetector.Crop> HEADERS = OpeningBeamPickupTest.HEADERS;
    static final List<ScoreMeterChange> METERS = List.of(new ScoreMeterChange(0, 3, 8));

    static ExactComponentMembers.Budget budget() {
        return new ExactComponentMembers.Budget(256000000, 128L * 1024 * 1024);
    }

    record Fixture(
            byte[] gray,
            byte[] labels,
            ScorePageInterpretation score,
            OmrScoreInterpreter.Analysis analysis) {}

    static Object component(ExactComponentMembers.Stats st) throws Exception {
        var c =
                Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component")
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(
                st.area(), st.minX(), st.maxX(), st.minY(), st.maxY(), st.centerX(), st.centerY());
    }

    static Fixture fixture(int right) throws Exception {
        return fixture(right, -1);
    }

    static Fixture fixture(int right, int strayX) throws Exception {
        return fixture(right, strayX, false);
    }

    static Fixture fixture(int right, int strayX, boolean mixed) throws Exception {
        byte[] g = new byte[W * H], l = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        var notes = new ArrayList<ScoreNoteEvent>();
        var components = new ArrayList<Object>();
        var stats = new ArrayList<ExactComponentMembers.Stats>();
        for (int lane = 0; lane < (mixed ? 2 : 1); lane++) {
            int offset = lane * 150;
            int[] stems = mixed && lane == 0 ? new int[] {100, 170} : new int[] {100, 150};
            for (int stem : stems) {
                OpeningBeamPickupTest.box(g, stem - 1, 90 + offset, stem + 1, 170 + offset, 0);
                OpeningBeamPickupTest.box(g, stem + 1, 86 + offset, stem + 15, 94 + offset, 0);
                OpeningBeamPickupTest.box(l, stem + 1, 86 + offset, stem + 15, 94 + offset, 2);
                notes.add(
                        new ScoreNoteEvent(
                                        0,
                                        (stem + 8 - 80) / (float) (right - 80),
                                        0,
                                        lane,
                                        mixed ? 2 : 1,
                                        (90f + offset) / H,
                                        false,
                                        0,
                                        2,
                                        2,
                                        0)
                                .withStemDirection(-1));
                var st =
                        new ExactComponentMembers.Stats(
                                135, stem + 1, stem + 15, 86 + offset, 94 + offset, stem + 8,
                                90 + offset);
                stats.add(st);
                components.add(component(st));
            }
            OpeningBeamPickupTest.box(g, 99, 168 + offset, stems[1] + 1, 172 + offset, 0);
            OpeningBeamPickupTest.box(g, 99, 158 + offset, stems[1] + 1, 162 + offset, 0);
        }
        var regions =
                List.of(
                        new MeasureRegion(80f / W, right / (float) W, .1f, .9f),
                        new MeasureRegion((right + 20f) / W, (right + 430f) / W, .1f, .9f),
                        new MeasureRegion((right + 450f) / W, (right + 860f) / W, .1f, .9f));
        var score =
                new ScorePageInterpretation(
                        regions, notes, 0, List.of(), List.of(), METERS, List.of());
        if (strayX >= 0) l[90 * W + strayX] = 2;
        var collector = new ExactComponentMembers.Collector(g, l, W, H, budget(), budget());
        assertTrue("bind original frame", collector.bindInput(l, g, W, H));
        var decisions = new IdentityHashMap<Object, OmrScoreInterpreter.PrintedValueDecision>();
        for (int i = 0; i < components.size(); i++) {
            var st = stats.get(i);
            var rows = collector.begin();
            for (int y = st.minY(); y <= st.maxY(); y++) rows.add(y, st.minX(), st.maxX());
            collector.record(components.get(i), st, (byte) 2, rows);
            var m =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "detectPrintedValue",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            components.get(i).getClass(),
                            float.class,
                            int.class,
                            List.class);
            m.setAccessible(true);
            var decision =
                    (OmrScoreInterpreter.PrintedValueDecision)
                            m.invoke(null, l, g, W, H, components.get(i), 16f, 2, components);
            decisions.put(components.get(i), decision);
        }
        assertTrue("seal original members", collector.seal(budget()));
        var producer =
                ScoreClockProducerSource.capture(
                        collector,
                        components,
                        components,
                        notes.size(),
                        budget(),
                        Map.of(),
                        decisions,
                        Map.of(),
                        notes);
        assertEquals("genuine original emission roster", notes.size(), producer.emissions().size());
        for (int i = 0; i < notes.size(); i++) {
            var e = producer.emissions().get(i);
            assertEquals(i, e.original());
            assertEquals(-1, e.parent());
            assertEquals(List.of(i), e.finalNoteOwners());
            assertEquals(135, e.pixels().size());
        }
        var base =
                new OmrScoreInterpreter.Analysis(
                        notes,
                        List.of(),
                        List.of(),
                        ScoreTremoloSource.empty(notes.size()),
                        producer);
        var context = ScoreClockContextCapture.capture(l, g, W, H, score, base, null, null);
        assertEquals(W, context.origin().width());
        assertEquals(H, context.origin().height());
        var analysis =
                new OmrScoreInterpreter.Analysis(
                        notes, List.of(), List.of(), base.tremoloSource(), producer, context);
        return new Fixture(g, l, score.withClockContextSource(context), analysis);
    }

    static ScorePageInterpretation apply(
            Fixture f, List<MeterChangeDetector.Crop> headers, OmrScoreInterpreter.Analysis a) {
        return OpeningBeamPickup.apply(
                f.score(),
                f.labels(),
                f.gray(),
                W,
                H,
                f.score().notes().get(0).staffCount() == 2
                        ? List.of(
                                new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 2),
                                new PlayingTechniqueDetector.Staff(250, 314, 16, 1, 2))
                        : STAFFS,
                METERS,
                headers,
                a);
    }

    static ScoreClockProducerSource producer(Fixture f, List<ScoreClockProducerSource.Emission> es)
            throws Exception {
        var c =
                ScoreClockProducerSource.class.getDeclaredConstructor(
                        String.class,
                        String.class,
                        List.class,
                        int.class,
                        ScoreClockProducerSource.MemberAccounting.class,
                        List.class);
        c.setAccessible(true);
        var p = f.analysis().clockProducerSource();
        return (ScoreClockProducerSource)
                c.newInstance(
                        p.rawSha(),
                        p.labelSha(),
                        es,
                        p.finalNotes(),
                        p.accounting(),
                        p.printedValues());
    }

    static Fixture emissions(Fixture f, List<ScoreClockProducerSource.Emission> es)
            throws Exception {
        var a = f.analysis();
        return new Fixture(
                f.gray(),
                f.labels(),
                f.score(),
                new OmrScoreInterpreter.Analysis(
                        a.notes(),
                        a.keyChanges(),
                        a.rests(),
                        a.tremoloSource(),
                        producer(f, es),
                        a.clockContextSource()));
    }

    static ScoreClockProducerSource.Emission e(
            ScoreClockProducerSource.Emission e,
            int parent,
            int original,
            List<Integer> pixels,
            List<Integer> owners) {
        return new ScoreClockProducerSource.Emission(
                e.index(), parent, original, pixels, owners, e.disposition(), e.nonNoteRole());
    }

    static void unchanged(
            String name,
            Fixture f,
            List<MeterChangeDetector.Crop> hs,
            OmrScoreInterpreter.Analysis a) {
        assertSame(name, f.score(), apply(f, hs, a));
    }

    static Object layout(Fixture f, int width, int height) throws Exception {
        var m =
                OpeningBeamPickup.class.getDeclaredMethod(
                        "ownedLayout",
                        ScorePageInterpretation.class,
                        OmrScoreInterpreter.Analysis.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        return m.invoke(
                null,
                f.score(),
                f.analysis(),
                f.labels(),
                f.gray(),
                width,
                height,
                STAFFS,
                HEADERS);
    }

    @Test
    public void centroidRedCompleteExtentAndLegacyPositive() throws Exception {
        var f = fixture(213);
        assertEquals("tail centroid RED", 55, 213 - 158, 0);
        assertEquals("complete owned ink tail", 48, 213 - 165, 0);
        unchanged("legacy has no source extent", f, HEADERS, null);
        var out = apply(f, HEADERS, f.analysis());
        assertNotSame("authenticated extent gain", f.score(), out);
        assertEquals(2, out.notes().size());
        for (int i = 0; i < 2; i++)
            assertEquals(
                    "only compact semantic change",
                    f.score().notes().get(i).withCompactOpening(),
                    out.notes().get(i));
        assertEquals(ScoreClockContextSource.unavailable(2, 3, 0), out.clockContextSource());
        assertEquals(.5, ScoreBoundaryClock.resolve(out, 1.5f).openingQuarterBeats(), 0);
        var legacy = fixture(200);
        assertNotSame(legacy.score(), apply(legacy, HEADERS, null));
    }

    @Test
    public void allAcceptedHeadersAreUniversalAndCoverageStartsEarliest() throws Exception {
        var f = fixture(213);
        var same = new MeterChangeDetector.Crop(69, 97, 91, 167, 100, 16);
        for (var hs : List.of(List.of(HEADERS.get(0), same), List.of(same, HEADERS.get(0))))
            assertNotSame(f.score(), apply(f, hs, f.analysis()));
        var far = new MeterChangeDetector.Crop(39, 97, 59, 167, 100, 16);
        for (var hs : List.of(List.of(HEADERS.get(0), far), List.of(far, HEADERS.get(0))))
            unchanged("every owned header distance", f, hs, f.analysis());
        var stray = fixture(213, 90); // earliest owned header's coverage includes this pixel.
        unchanged(
                "earliest coverage stray", stray, List.of(same, HEADERS.get(0)), stray.analysis());
    }

    @Test
    public void authenticationMappingAndCompleteMemberRefusals() throws Exception {
        for (String mode :
                List.of(
                        "grayHash",
                        "labelHash",
                        "shared",
                        "split",
                        "child",
                        "overlap",
                        "empty",
                        "outside",
                        "nonHead",
                        "clipped",
                        "stray",
                        "duplicateOriginal",
                        "changedFinal",
                        "unavailable")) {
            var f = fixture(213);
            var es = new ArrayList<>(f.analysis().clockProducerSource().emissions());
            var a = es.get(0);
            var b = es.get(1);
            switch (mode) {
                case "grayHash" -> f.gray()[0] = 0;
                case "labelHash" -> f.labels()[0] = 2;
                case "shared" -> {
                    es.set(0, e(a, -1, 0, a.pixels(), List.of(0, 1)));
                    f = emissions(f, es);
                }
                case "split" -> {
                    es.set(0, e(a, 1, 1, a.pixels(), a.finalNoteOwners()));
                    f = emissions(f, es);
                }
                case "child" -> {
                    es.add(
                            new ScoreClockProducerSource.Emission(
                                    2, 0, 0, List.of(), List.of(), "child", null));
                    f = emissions(f, es);
                }
                case "overlap" -> {
                    es.add(
                            new ScoreClockProducerSource.Emission(
                                    2,
                                    -1,
                                    2,
                                    List.of(a.pixels().get(0)),
                                    List.of(),
                                    "other",
                                    null));
                    f = emissions(f, es);
                }
                case "empty" -> {
                    es.set(1, e(b, -1, 1, List.of(), b.finalNoteOwners()));
                    f = emissions(f, es);
                }
                case "outside" -> {
                    var pixels = new ArrayList<>(b.pixels());
                    pixels.add(0);
                    es.set(1, e(b, -1, 1, pixels, b.finalNoteOwners()));
                    f = emissions(f, es);
                }
                case "nonHead" -> {
                    var pixels = new ArrayList<>(b.pixels());
                    pixels.add(95 * W + 158);
                    es.set(1, e(b, -1, 1, pixels, b.finalNoteOwners()));
                    f = emissions(f, es);
                }
                case "clipped" -> {
                    var pixels = new ArrayList<>(a.pixels());
                    pixels.add(b.pixels().get(0));
                    es.set(0, e(a, -1, 0, pixels, a.finalNoteOwners()));
                    f = emissions(f, es);
                }
                case "stray" -> f = fixture(213, 166);
                case "duplicateOriginal" -> {
                    var old = f.analysis();
                    f =
                            new Fixture(
                                    f.gray(),
                                    f.labels(),
                                    f.score(),
                                    new OmrScoreInterpreter.Analysis(
                                            List.of(old.notes().get(0), old.notes().get(0)),
                                            old.keyChanges(),
                                            old.rests(),
                                            old.tremoloSource(),
                                            old.clockProducerSource(),
                                            old.clockContextSource()));
                }
                case "changedFinal" -> {
                    var ns = new ArrayList<>(f.score().notes());
                    ns.set(0, ns.get(0).withCompactOpening());
                    f =
                            new Fixture(
                                    f.gray(),
                                    f.labels(),
                                    f.score().withNotes(ns, List.of(0, 1)),
                                    f.analysis());
                }
                case "unavailable" -> {
                    var old = f.analysis();
                    f =
                            new Fixture(
                                    f.gray(),
                                    f.labels(),
                                    f.score(),
                                    new OmrScoreInterpreter.Analysis(
                                            old.notes(), old.keyChanges(), old.rests()));
                }
            }
            unchanged(mode, f, HEADERS, f.analysis());
        }
        var f = fixture(213);
        assertNull("reshaped grid rejected by exact origin", layout(f, 600, 800));
        assertSame(
                "same byte length is not same grid",
                f.score(),
                OpeningBeamPickup.apply(
                        f.score(),
                        f.labels(),
                        f.gray(),
                        600,
                        800,
                        STAFFS,
                        METERS,
                        HEADERS,
                        f.analysis()));
    }

    @Test
    public void extentFallbackAuthenticatesEveryOpeningLane() throws Exception {
        var headers =
                List.of(HEADERS.get(0), new MeterChangeDetector.Crop(70, 247, 90, 317, 250, 16));
        var valid = fixture(213, -1, true);
        assertNotSame(
                "mixed centroid and extent lanes",
                valid.score(),
                apply(valid, headers, valid.analysis()));
        var stray = fixture(213, 188, true);
        unchanged(
                "earlier centroid lane cannot hide unbound ink", stray, headers, stray.analysis());
    }

    @Test
    public void finalCaptureStrictMappingDocumentBindingAndBoundary() throws Exception {
        var f = fixture(213);
        assertEquals(
                "original source observations",
                2,
                f.analysis().clockContextSource().printedValues().size());
        var out = apply(f, HEADERS, f.analysis());
        assertNotSame(f.score(), out);
        assertFalse(out.openingPickup());
        assertEquals(ScoreClockContextSource.unavailable(2, 3, 0), out.clockContextSource());
        var captured =
                ScoreClockContextCapture.capture(
                        f.labels(), f.gray(), W, H, out, f.analysis(), null, null);
        assertEquals(
                "compact edits cannot borrow original full-record observations",
                0,
                captured.printedValues().size());
        assertEquals(f.analysis().clockContextSource().origin(), captured.origin());
        var bound =
                ScoreClockContextAssembly.bindDocument(
                        out.withClockContextSource(captured), "1".repeat(64), 0, 1);
        assertTrue(bound.clockContextSource().origin().boundDocument());
        assertEquals(.5, ScoreBoundaryClock.resolve(bound, 1.5f).openingQuarterBeats(), 0);
        assertEquals(1.5, ScoreBoundaryClock.resolve(bound, 1.5f).closingQuarterBeats(), 0);
    }

    @Test
    public void musicalAndRosterGuardsRemain() throws Exception {
        var broken = fixture(213);
        OpeningBeamPickupTest.box(broken.gray(), 120, 168, 130, 172, 255);
        unchanged("broken primary", broken, HEADERS, broken.analysis());
        var f = fixture(213);
        unchanged("no owned header", f, List.of(), f.analysis());
        assertSame(
                "conflicting header meter",
                f.score(),
                OpeningBeamPickup.apply(
                        f.score(),
                        f.labels(),
                        f.gray(),
                        W,
                        H,
                        STAFFS,
                        List.of(new ScoreMeterChange(0, 4, 4)),
                        HEADERS,
                        f.analysis()));
        for (var invalid :
                List.of(
                        f.score().notes().get(0).withLeadingRest(.25f),
                        f.score().notes().get(0).withBoundaryTies(1),
                        f.score().notes().get(0).withCrossStaffBeam())) {
            var altered = new ArrayList<>(f.score().notes());
            altered.set(0, invalid);
            var bad =
                    new Fixture(
                            f.gray(),
                            f.labels(),
                            f.score().withNotes(altered, List.of(0, 1)),
                            f.analysis());
            unchanged("musical guard", bad, HEADERS, bad.analysis());
        }
        var rs =
                new ScorePageInterpretation(
                        f.score().measures(),
                        f.score().notes(),
                        0,
                        List.of(),
                        List.of(),
                        METERS,
                        List.of(new ScoreRestEvent(0, .9f, .5f, .1f, 0, 1, .25)));
        var resting = new Fixture(f.gray(), f.labels(), rs, f.analysis());
        unchanged("opening rest", resting, HEADERS, resting.analysis());
        var wide =
                new ScorePageInterpretation(
                        List.of(
                                f.score().measures().get(0),
                                new MeasureRegion(233f / W, 433f / W, .1f, .9f),
                                new MeasureRegion(453f / W, 653f / W, .1f, .9f)),
                        f.score().notes(),
                        0,
                        List.of(),
                        List.of(),
                        METERS,
                        List.of());
        var clock = new Fixture(f.gray(), f.labels(), wide, f.analysis());
        unchanged("not short layout clock", clock, HEADERS, clock.analysis());
        var ns = List.of(f.score().notes().get(0));
        var missing =
                new Fixture(
                        f.gray(), f.labels(), f.score().withNotes(ns, List.of(0)), f.analysis());
        unchanged("incomplete head roster", missing, HEADERS, missing.analysis());
    }
}
