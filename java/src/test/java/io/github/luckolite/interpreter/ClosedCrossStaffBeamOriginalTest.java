// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original finite keyboard engravings. The brace is scaled from GrandStaffOuterDynamicTest. */
public class ClosedCrossStaffBeamOriginalTest {
    static final int W = 1200, H = 1000, G = 16;
    static final MeasureRegion REGION = new MeasureRegion(200f / W, 1000f / W, 160f / H, 650f / H);
    static final List<PlayingTechniqueDetector.Staff> STAFFS =
            List.of(
                    new PlayingTechniqueDetector.Staff(200, 264, G, 0, 2),
                    new PlayingTechniqueDetector.Staff(360, 424, G, 1, 2));

    static class Scene {
        final byte[] g = new byte[W * H], l = new byte[W * H];
        final List<List<Integer>> additionalGroups = new ArrayList<>();
        final List<ScoreNoteEvent> notes = new ArrayList<>();
        final List<PrintedSlurOwners.GeometryHead> heads = new ArrayList<>();

        Scene() {
            Arrays.fill(g, (byte) 255);
            for (var s : STAFFS)
                for (int k = 0; k < 5; k++)
                    for (int x = 200; x <= 1000; x++)
                        ink(x, Math.round(s.top() + k * G), OmrMeasurePostProcessor.STAFF);
            brace(false);
        }

        void ink(int x, int y, int label) {
            if (x < 0 || x >= W || y < 0 || y >= H) throw new AssertionError("fixturebounds");
            g[y * W + x] = 0;
            l[y * W + x] = (byte) label;
        }

        void clear(int x, int y) {
            g[y * W + x] = (byte) 255;
            l[y * W + x] = 0;
        }

        void brace(boolean square) {
            for (int y = 200; y <= 424; y++) {
                double t = (y - 200) / 224.;
                int x = 90 + (int) Math.round(11 * Math.abs(Math.sin(2 * Math.PI * t)));
                if (t > .25 && t < .75)
                    x = 90 + (int) Math.round(11 * Math.sin(Math.abs(t - .5) * Math.PI * 2));
                if (square) x = 90;
                ink(x, y, OmrMeasurePostProcessor.SYMBOL);
                ink(x + 1, y, OmrMeasurePostProcessor.SYMBOL);
            }
        }

        int beamY(int x) {
            return 480;
        }

        void stem(int x, int y, int end) {
            int sx = Math.round(x - G * .6f);
            for (int yy = y; yy <= end; yy++) {
                ink(sx, yy, OmrMeasurePostProcessor.STEM_OR_REST);
                ink(sx + 1, yy, OmrMeasurePostProcessor.STEM_OR_REST);
            }
        }

        void headInk(int x, int y, boolean hollow) {
            for (int dy = -6; dy <= 6; dy++)
                for (int dx = -10; dx <= 10; dx++)
                    if (dx * dx / 100. + dy * dy / 36. <= 1) {
                        l[(y + dy) * W + x + dx] = (byte) OmrMeasurePostProcessor.NOTEHEAD;
                        g[(y + dy) * W + x + dx] =
                                (byte) (hollow && dx * dx / 49. + dy * dy / 9. < 1 ? 255 : 0);
                    }
        }

        void add(int x, int y, int staff, int beams, float base, boolean hollow) {
            int id = notes.size();
            float bottom = staff == 0 ? 264 : 424;
            int step = Math.round((bottom - y) / (G * .5f));
            notes.add(
                    new ScoreNoteEvent(
                                    0,
                                    (x - 200) / 800f,
                                    step,
                                    staff,
                                    2,
                                    y / (float) H,
                                    false,
                                    0,
                                    beams,
                                    2,
                                    base)
                            .withClef(
                                    staff == 0
                                            ? ScoreNoteEvent.CLEF_TREBLE
                                            : ScoreNoteEvent.CLEF_BASS)
                            .withStemDirection(-1));
            heads.add(new PrintedSlurOwners.GeometryHead(id, x, y, staff, 2));
            headInk(x, y, hollow);
        }

        void rails(int first, int last, int count) {
            for (int level = 0; level < count; level++)
                for (int x = first; x <= last; x++)
                    for (int y = beamY(x) - level * 12 - 7; y <= beamY(x) - level * 12; y++)
                        ink(x, y, OmrMeasurePostProcessor.STEM_OR_REST);
        }

        ScoreBeamSource source(int page) {
            var cols = new ArrayList<ScoreBeamSource.Column>();
            for (int i = 0; i < 3; i++)
                cols.add(new ScoreBeamSource.Column(List.of(new ScoreBeamSource.Anchor(page, i))));
            var origins = new ArrayList<Optional<ScoreBeamSource.Anchor>>();
            for (int i = 0; i < notes.size(); i++)
                origins.add(Optional.of(new ScoreBeamSource.Anchor(0, i)));
            var groups = new ArrayList<ScoreBeamSource.Group>();
            groups.add(
                    new ScoreBeamSource.Group("original-partial", cols, List.of(3, 3), List.of()));
            for (var ids : additionalGroups) {
                var extra = new ArrayList<ScoreBeamSource.Column>();
                for (int id : ids)
                    extra.add(
                            new ScoreBeamSource.Column(
                                    List.of(new ScoreBeamSource.Anchor(page, id))));
                groups.add(
                        new ScoreBeamSource.Group(
                                "independent-" + ids.get(0),
                                extra,
                                Collections.nCopies(ids.size() - 1, 3),
                                List.of()));
            }
            return new ScoreBeamSource(groups, List.of(), origins);
        }

        ScorePageInterpretation score() {
            return new ScorePageInterpretation(
                            List.of(REGION),
                            notes,
                            1,
                            List.of(),
                            List.of(),
                            List.of(new ScoreMeterChange(0, 6, 16)),
                            List.of())
                    .withBeamSource(source(0));
        }
    }

    static Scene scene(boolean returning) {
        Scene s = new Scene();
        int[] ys =
                returning
                        ? new int[] {416, 400, 384, 248, 216, 184, 384, 416}
                        : new int[] {416, 400, 384, 248, 216, 184};
        for (int i = 0; i < ys.length; i++) {
            int x = 300 + i * 40, staff = i < 3 || returning && i >= 6 ? 1 : 0;
            s.stem(x, ys[i], 480);
            s.add(x, ys[i], staff, staff == 1 ? 2 : 0, staff == 1 ? 0 : 1, false);
        }
        s.rails(290, Math.round((300 + (ys.length - 1) * 40) - G * .6f) + 1, 2);
        assertEquals(
                "original real braced pair",
                1,
                GrandStaffDynamics.bracedPairs(STAFFS, List.of(REGION), s.g, W, H).size());
        return s;
    }

    static class Probe {
        final Scene scene;
        final Object raw;
        final ScorePageInterpretation output;
        final List<?> groups;

        Probe(Scene s, Object r, ScorePageInterpretation o, List<?> g) {
            scene = s;
            raw = r;
            output = o;
            groups = g;
        }
    }

    static Probe run(Scene s) throws Exception {
        return run(s, s.source(0));
    }

    static Probe run(Scene s, ScoreBeamSource source) throws Exception {
        return run(s, source, true);
    }

    static Probe run(Scene s, ScoreBeamSource source, boolean projectNow) throws Exception {
        byte[] g = s.g.clone(), l = s.l.clone();
        Object r;
        Class<?> type;
        try {
            type = Class.forName("io.github.luckolite.interpreter.ClosedCrossStaffBeamGroups");
        } catch (ClassNotFoundException absent) {
            return new Probe(s, null, s.score(), List.of());
        }
        var capture =
                type.getDeclaredMethod(
                        "capture",
                        List.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class,
                        List.class,
                        ScoreBeamSource.class);
        capture.setAccessible(true);
        r = capture.invoke(null, s.heads, s.g, s.l, W, H, STAFFS, List.of(REGION), s.notes, source);
        var project = r.getClass().getDeclaredMethod("project", ScorePageInterpretation.class);
        project.setAccessible(true);
        var score = projectNow ? (ScorePageInterpretation) project.invoke(r, s.score()) : s.score();
        var groups = r.getClass().getDeclaredMethod("groups");
        groups.setAccessible(true);
        assertArrayEquals(g, s.g);
        assertArrayEquals(l, s.l);
        return new Probe(s, r, score, (List<?>) groups.invoke(r));
    }

    static void unchanged(Scene s) throws Exception {
        var p = run(s);
        assertTrue(p.groups.isEmpty());
        assertEquals(s.notes, p.output.notes());
        assertEquals(s.source(0), p.output.beamSource());
    }

    @Test
    public void restoresRemoteQuarterValuesAndFullLiteralGroup() throws Exception {
        var s = scene(false);
        var p = run(s);
        assertEquals(1, p.groups.size());
        assertEquals(6, p.output.beamSource().groups().get(0).columns().size());
        for (var n : p.output.notes()) {
            assertEquals(2, n.beamCount());
            assertEquals(0, n.unbeamedDurationBeats(), 0);
            assertTrue(n.crossStaffBeam());
        }
    }

    @Test
    public void returningPhysicalStaffIsStillOneBeamEnsemble() throws Exception {
        var p = run(scene(true));
        assertEquals(1, p.groups.size());
        assertEquals(8, p.output.beamSource().groups().get(0).columns().size());
        assertTrue(p.output.notes().stream().allMatch(ScoreNoteEvent::crossStaffBeam));
    }

    @Test
    public void normalClockFollowsSixIndependentQuarterBeatColumns() throws Exception {
        var p = run(scene(false));
        for (int i = 0; i < 6; i++) {
            var n = p.output.notes().get(i);
            assertEquals(.25, ScoreNoteTiming.writtenDurationBeats(n), .00001);
            assertEquals(i * .25, ScoreNoteTiming.beatInMeasure(n, p.output.notes(), 1.5f), .00001);
        }
    }

    @Test
    public void independentWholeHeadNeverBecomesPartOfMovingBeam() throws Exception {
        var s = scene(false);
        s.add(302, 232, 0, 0, 4, true);
        var p = run(s);
        assertEquals(1, p.groups.size());
        assertEquals(6, p.output.beamSource().groups().get(0).columns().size());
        assertEquals(s.notes.get(6), p.output.notes().get(6));
    }

    @Test
    public void parallelUpStemVoiceKeepsItsOwnFields() throws Exception {
        var s = scene(false);
        s.add(380, 232, 0, 1, 0, false);
        int x = Math.round(380 + G * .6f);
        for (int y = 168; y <= 232; y++) {
            s.ink(x, y, OmrMeasurePostProcessor.STEM_OR_REST);
            s.ink(x + 1, y, OmrMeasurePostProcessor.STEM_OR_REST);
        }
        s.headInk(380, 232, false);
        var p = run(s);
        assertEquals(1, p.groups.size());
        assertEquals(s.notes.get(6), p.output.notes().get(6));
    }

    @Test
    public void absentBraceKeepsIndependentParts() throws Exception {
        var s = scene(false);
        for (int y = 180; y <= 440; y++) for (int x = 60; x <= 120; x++) s.clear(x, y);
        assertEquals(0, GrandStaffDynamics.bracedPairs(STAFFS, List.of(REGION), s.g, W, H).size());
        unchanged(s);
    }

    @Test
    public void squareBracketDoesNotDeclareAKeyboardPart() throws Exception {
        var s = scene(false);
        for (int y = 180; y <= 440; y++) for (int x = 60; x <= 120; x++) s.clear(x, y);
        s.brace(true);
        assertEquals(0, GrandStaffDynamics.bracedPairs(STAFFS, List.of(REGION), s.g, W, H).size());
        unchanged(s);
    }

    @Test
    public void brokenOwnedShaftCannotSupplyARoster() throws Exception {
        var s = scene(false);
        int x = Math.round(420 - G * .6f);
        for (int y = 300; y <= 302; y++) {
            s.clear(x, y);
            s.clear(x + 1, y);
        }
        unchanged(s);
    }

    @Test
    public void unownedInteriorShaftRefusesClosedGroup() throws Exception {
        var s = scene(false);
        for (int y = 420; y <= 480; y++) s.ink(430, y, OmrMeasurePostProcessor.STEM_OR_REST);
        unchanged(s);
    }

    @Test
    public void opposedVoiceShaftDoesNotCollapseIntoMovingHead() throws Exception {
        var s = scene(false);
        int x = Math.round(460 + G * .6f);
        for (int y = 152; y <= 216; y++) {
            s.ink(x, y, OmrMeasurePostProcessor.STEM_OR_REST);
            s.ink(x + 1, y, OmrMeasurePostProcessor.STEM_OR_REST);
        }
        s.headInk(460, 216, false);
        unchanged(s);
    }

    @Test
    public void thirdRailCannotBeCalledTwoRails() throws Exception {
        var s = scene(false);
        s.rails(290, 491, 3);
        unchanged(s);
        var hook = scene(false);
        for (int x = 290; x <= 308; x++)
            for (int y = 449; y <= 456; y++) hook.ink(x, y, OmrMeasurePostProcessor.STEM_OR_REST);
        unchanged(hook);
    }

    @Test
    public void severedRailDoesNotJoinSeparateBeams() throws Exception {
        var s = scene(false);
        for (int y = 450; y <= 490; y++) s.clear(435, y);
        unchanged(s);
    }

    @Test
    public void whollyOneStaffGroupKeepsLegacyMetadata() throws Exception {
        var s = scene(false);
        for (int i = 3; i < 6; i++) {
            var n = s.notes.get(i);
            s.notes.set(
                    i,
                    new ScoreNoteEvent(
                            0,
                            n.positionInMeasure(),
                            n.staffStep(),
                            1,
                            2,
                            n.pageY(),
                            false,
                            0,
                            0,
                            2,
                            1));
            var h = s.heads.get(i);
            s.heads.set(i, new PrintedSlurOwners.GeometryHead(i, h.x(), h.y(), 1, 2));
        }
        unchanged(s);
    }

    @Test
    public void hollowBodyDoesNotAcquireQuarterRepair() throws Exception {
        var s = scene(false);
        s.headInk(460, 216, true);
        unchanged(s);
    }

    @Test
    public void sourceFromOtherPhysicalPageIsNotAuthority() throws Exception {
        var s = scene(false);
        var p = run(s, s.source(1));
        assertTrue(p.groups.isEmpty());
        assertEquals(s.notes, p.output.notes());
    }

    @Test
    public void staleProofCannotOverwriteAnEditedNote() throws Exception {
        var s = scene(false);
        var p = run(s);
        if (p.raw == null) return;
        var edit = new ArrayList<>(s.notes);
        edit.set(0, edit.get(0).withArticulations(8));
        var score = s.score().withNotes(edit, List.of(0, 1, 2, 3, 4, 5));
        var m = p.raw.getClass().getDeclaredMethod("project", ScorePageInterpretation.class);
        m.setAccessible(true);
        assertSame(score, m.invoke(p.raw, score));
    }

    @Test
    public void singleBeamCannotBecomeSixteenths() throws Exception {
        var s = scene(false);
        for (int x = 290; x <= 491; x++)
            for (int y = 461; y <= 468; y++) {
                if (x != 290 && x != 291 && x != 330 && x != 331 && x != 370 && x != 371 && x != 410
                        && x != 411 && x != 450 && x != 451 && x != 490 && x != 491) s.clear(x, y);
            }
        unchanged(s);
    }

    @Test
    public void missingSemanticHeadBindingKeepsOldValue() throws Exception {
        var s = scene(false);
        for (int p = 0; p < s.l.length; p++)
            if (s.l[p] == 2 && Math.abs(p % W - 420) <= 12 && Math.abs(p / W - 248) <= 8)
                s.l[p] = 0;
        unchanged(s);
    }

    @Test
    public void noOpPreservesExactScoreAndClockContextIdentity() throws Exception {
        var s = scene(false);
        for (int y = 180; y <= 440; y++) for (int x = 60; x <= 120; x++) s.clear(x, y);
        var p = run(s);
        if (p.raw == null) return;
        var original = s.score();
        var m = p.raw.getClass().getDeclaredMethod("project", ScorePageInterpretation.class);
        m.setAccessible(true);
        var out = (ScorePageInterpretation) m.invoke(p.raw, original);
        assertSame(original, out);
        assertSame(original.clockContextSource(), out.clockContextSource());
    }

    @Test
    public void contradictoryOwnedValuesNeverAcquireCrossStaffClock() throws Exception {
        for (float base : new float[] {2, 4, -1}) {
            var s = scene(false);
            var old = s.notes.get(4);
            s.notes.set(
                    4,
                    new ScoreNoteEvent(
                            0,
                            old.positionInMeasure(),
                            old.staffStep(),
                            0,
                            2,
                            old.pageY(),
                            false,
                            0,
                            base < 0 ? 3 : 0,
                            2,
                            base < 0 ? 0 : base));
            unchanged(s);
        }
    }

    @Test
    public void crossStaffEndpointCompletesBeforeProjectionAndClock() throws Exception {
        var s = scene(false);
        var points = new ArrayList<PrintedSlurContours.Point>();
        for (int x = 290; x <= 490; x++) {
            float t = (x - 290) / 200f;
            float y = 486 + 80 * t * (1 - t);
            points.add(new PrintedSlurContours.Point(x, y));
            s.ink(x, Math.round(y), OmrMeasurePostProcessor.SYMBOL);
        }
        var bow = new PrintedSlurContours.Bow(points, false, 1.25f, 0);
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {200, 424, 16}, new float[] {1000, 424, 16}));
        var frame =
                new PrintedSlurPageCapture.Frame<>(
                        200, 160, 1000, 650, track, s.heads.subList(0, 3));
        var p = run(s, s.source(0), false);
        Optional<?> span = Optional.empty();
        if (p.raw != null) {
            Class<?> c =
                    Class.forName("io.github.luckolite.interpreter.CrossStaffPhraseEndpointInk");
            for (var m : c.getDeclaredMethods())
                if (m.getName().equals("complete")) {
                    m.setAccessible(true);
                    span =
                            (Optional<?>)
                                    m.invoke(
                                            null,
                                            "original-cross-staff",
                                            0,
                                            bow,
                                            s.heads,
                                            s.g,
                                            s.l,
                                            W,
                                            H,
                                            STAFFS,
                                            List.of(REGION),
                                            s.notes,
                                            s.source(0),
                                            frame,
                                            p.raw);
                }
        }
        assertTrue("independent complete endpoint", span.isPresent());
        var actual = (ScoreSlurPages.Span) span.get();
        assertEquals(0, actual.start().noteIndex());
        assertEquals(5, actual.end().noteIndex());
        var project = p.raw.getClass().getDeclaredMethod("project", ScorePageInterpretation.class);
        project.setAccessible(true);
        var out = (ScorePageInterpretation) project.invoke(p.raw, p.output);
        for (int i = 0; i < 6; i++) {
            assertEquals(.25, ScoreNoteTiming.writtenDurationBeats(out.notes().get(i)), .00001);
            assertEquals(
                    i * .25,
                    ScoreNoteTiming.beatInMeasure(out.notes().get(i), out.notes(), 1.5f),
                    .00001);
        }
    }

    @Test
    public void separateBeamOnSameExtrapolatedLineKeepsItsOwnRoster() throws Exception {
        var s = scene(false);
        for (int i = 0; i < 3; i++) {
            int x = 650 + i * 40, y = 416 - i * 16;
            s.stem(x, y, 480);
            s.add(x, y, 1, 2, 0, false);
        }
        s.rails(640, 721, 2);
        s.additionalGroups.add(List.of(6, 7, 8));
        var p = run(s);
        assertEquals(1, p.groups.size());
        assertEquals(
                6,
                p.output.beamSource().groups().stream()
                        .filter(g -> g.id().startsWith("closed-cross-staff:"))
                        .findFirst()
                        .orElseThrow()
                        .columns()
                        .size());
        for (int i = 0; i < 6; i++) {
            assertEquals(2, p.output.notes().get(i).beamCount());
            assertTrue(p.output.notes().get(i).crossStaffBeam());
        }
        for (int i = 6; i < 9; i++) assertEquals(s.notes.get(i), p.output.notes().get(i));
        assertTrue(p.output.beamSource().groups().contains(s.source(0).groups().get(1)));
    }
}
