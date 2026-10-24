// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original staff/head geometry and independently supplied normalized OCR words. */
public class NavigationAnnotationApiTest {
    @Test
    public void commonTimeUsesRawGlyphWithoutDigitOcr() {
        for (boolean cut : new boolean[] {false, true}) {
            var p = new HeaderSymbolNormalizationTest.Page(cut, true);
            p.note(210, 112, 10, 7, false);
            var score = SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h);
            assertTrue(
                    score.meterChanges().toString(),
                    score.meterChanges().stream()
                            .anyMatch(
                                    m ->
                                            m.numerator() == (cut ? 2 : 4)
                                                    && m.denominator() == (cut ? 2 : 4)));
        }
    }

    @Test
    public void explicitMeterAnnotationRemainsAuthoritative() {
        var p = new HeaderSymbolNormalizationTest.Page(true, true);
        p.note(210, 112, 10, 7, false);
        var explicit = new ScoreMeterChange(0, 3, 4);
        var a =
                new SheetInterpreter.Annotations(
                        List.of(), List.of(), List.of(), List.of(), List.of(explicit));
        assertEquals(
                List.of(explicit),
                SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h, a).meterChanges());
    }

    @Test
    public void sourceNavigationWordSurvivesPublicApi() {
        var p = new HeaderSymbolNormalizationTest.Page(false, true);
        p.eraseSign();
        p.note(210, 112, 10, 7, false);
        var a =
                new SheetInterpreter.Annotations(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new SheetInterpreter.Word("D.S. al Coda", .5f, .18f, .72f, .25f)),
                        List.of());
        var result = SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h, a);
        assertTrue(
                result.playbackDirections().toString(),
                result.playbackDirections().stream()
                        .anyMatch(d -> d.kind() == ScorePlaybackDirection.Kind.DAL_SEGNO_AL_CODA));
    }

    @Test
    public void stafflessTextCannotInventNavigation() {
        int w = 400, h = 200;
        byte[] l = new byte[w * h], g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        var a =
                new SheetInterpreter.Annotations(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new SheetInterpreter.Word("Coda", .2f, .2f, .3f, .3f)),
                        List.of());
        assertTrue(SheetInterpreter.analyze(l, g, w, h, a).playbackDirections().isEmpty());
    }

    @Test
    public void ornamentalOcrNeedsInkAndAnOwnedNote() {
        var p = new HeaderSymbolNormalizationTest.Page(false, true);
        p.eraseSign();
        p.note(210, 112, 10, 7, false);
        var a =
                new SheetInterpreter.Annotations(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new SheetInterpreter.Word("tr", .415f, .21f, .47f, .29f)),
                        List.of());
        assertTrue(
                SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h, a).notes().stream()
                        .noneMatch(
                                n -> NoteOrnament.type(n.articulations()) == NoteOrnament.TRILL));
        for (int y = 54; y < 66; y++) for (int x = 204; x < 215; x++) p.gray[y * p.w + x] = 0;
        assertTrue(
                SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h, a).notes().stream()
                        .anyMatch(n -> NoteOrnament.type(n.articulations()) == NoteOrnament.TRILL));
    }

    @Test
    public void suppliedFingeringWordRemovesItsFalseHeadButNotStaffNote() {
        var p = new HeaderSymbolNormalizationTest.Page(false, true);
        p.eraseSign();
        p.note(210, 64, 10, 7, false);
        p.note(310, 112, 10, 7, false);
        for (int y = 30; y <= 64; y++) {
            p.gray[y * p.w + 220] = 0;
            p.labels[y * p.w + 220] = 1;
        }
        var before = SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h);
        var a =
                new SheetInterpreter.Annotations(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new SheetInterpreter.Word(
                                        "L2", 195f / p.w, 45f / p.h, 228f / p.w, 76f / p.h)),
                        List.of());
        var after = SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h, a);
        assertEquals(2, before.notes().size());
        assertEquals(1, after.notes().size());
        assertEquals(112f / p.h, after.notes().get(0).pageY(), .01);
    }

    @Test
    public void optionalCallerInferenceRunsWithoutRuntimeDependency() throws Exception {
        int[] calls = {0};
        var inference =
                new PortableOcr.Inference() {
                    public float[][] detect(float[] chw, int w, int h) {
                        calls[0]++;
                        return new float[][] {{0}};
                    }

                    public float[][] recognize(float[] chw, int w, int h) {
                        throw new AssertionError("No boxes means no recognition");
                    }

                    public List<String> dictionary() {
                        return List.of("", "x");
                    }
                };
        var ocr =
                new MusicalOcr(
                        inference,
                        new MeterFontMatcher(new java.io.File("java/assets/Bravura.otf")));
        var p = new HeaderSymbolNormalizationTest.Page(false, true);
        p.eraseSign();
        p.note(210, 112, 10, 7, false);
        var without = SheetInterpreter.analyze(p.labels, p.gray, p.w, p.h);
        var with =
                SheetInterpreter.analyze(
                        p.labels, p.gray, p.w, p.h, SheetInterpreter.Annotations.EMPTY, ocr);
        var unread =
                new ScoreClockContextSource.MeterRead(
                        0,
                        0,
                        "",
                        0,
                        0,
                        new ScoreClockContextSource.RasterBox(32, 77, 62, 148),
                        80,
                        16,
                        0,
                        0,
                        "",
                        ScoreClockContextSource.MeterRoute.UNREAD_CANDIDATE,
                        false,
                        ScoreClockContextSource.Status.UNREAD);
        var context = without.clockContextSource();
        var expectedContext =
                new ScoreClockContextSource(
                        context.origin(),
                        context.noteCount(),
                        context.measureCount(),
                        context.restCount(),
                        context.partWords(),
                        context.numbers(),
                        List.of(unread),
                        context.printedValues(),
                        context.glyphLedgers(),
                        context.restValues(),
                        context.voiceGroups(),
                        context.syncAnchors(),
                        context.missing());
        assertEquals(without.withClockContextSource(expectedContext), with);
        assertTrue(with.meterChanges().isEmpty());
        assertTrue(
                with.clockContextSource().missing().stream()
                        .anyMatch(
                                missing ->
                                        missing.requirement()
                                                == ScoreClockContextSource.Requirement
                                                        .METER_SCOPE));
        assertTrue(calls[0] > 0);
    }
}
