// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated marks; no score scans, font assets or song coordinates. */
public class TabGestureMarksTest {
    @Test
    public void tallPdfCompoundCannotHideSeparateChordFrets() {
        var upper = new TablatureDecoder.Word("5", .19f, .33f, .21f, .36f);
        var lower = new TablatureDecoder.Word("5", .19f, .58f, .21f, .61f);
        var tall = new TablatureDecoder.Word("55", .19f, .33f, .21f, .61f);
        var result = TabTextSource.disjoint(List.of(tall, upper, lower));
        assertEquals(List.of(upper, lower), result);
        var left = new TablatureDecoder.Word("1", .19f, .33f, .20f, .36f);
        var right = new TablatureDecoder.Word("2", .20f, .33f, .21f, .36f);
        var normal = new TablatureDecoder.Word("12", .19f, .33f, .21f, .36f);
        assertEquals(List.of(normal), TabTextSource.disjoint(List.of(normal, left, right)));
    }

    private final int w = 500, h = 400;
    private final TablatureDecoder.Fret fret = new TablatureDecoder.Fret(100, 140, 0, 5);

    private TablatureDecoder.Staff staff(List<TablatureDecoder.Fret> frets) {
        return new TablatureDecoder.Staff(140, 20, -1, frets, List.of(20f, 480f));
    }

    private TablatureDecoder.Word word(String s, float x, float y) {
        return new TablatureDecoder.Word(s, (x - 7) / w, (y - 5) / h, (x + 7) / w, (y + 5) / h);
    }

    private void line(byte[] g, int x0, int y0, int x1, int y1) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++)
            g[
                            Math.round(y0 + (y1 - y0) * i / (float) n) * w
                                    + Math.round(x0 + (x1 - x0) * i / (float) n)] =
                    0;
    }

    private byte[] arrow(boolean head, boolean release) {
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        line(g, 107, 132, 122, 129);
        line(g, 122, 129, 141, 116);
        line(g, 141, 116, 150, 100);
        if (head) {
            line(g, 150, 100, 142, 111);
            line(g, 150, 100, 158, 111);
        }
        if (release) {
            line(g, 150, 100, 165, 118);
            line(g, 165, 118, 188, 132);
        }
        return g;
    }

    private int marks(byte[] g, String amount) {
        var tabs =
                TabGestureMarks.apply(
                        List.of(staff(List.of(fret))), List.of(word(amount, 150, 85)), g, w, h);
        assertEquals(1, tabs.get(0).frets().size());
        return tabs.get(0).frets().get(0).marks();
    }

    @Test
    public void originalIntegerEncodingRetainsItsBytesAndPitch() {
        int m = TabEffect.encode(TabEffect.BEND, 2);
        assertEquals((4 << 19) | (34 << 23), m);
        assertEquals(2, TabEffect.semitones(m), 0);
        assertEquals(2, TabEffect.pitchOffset(m, 1, 2), 0);
    }

    @Test
    public void attachedFullArrowBendsOneAttack() {
        int m = marks(arrow(true, false), "full");
        assertEquals(TabEffect.BEND, TabEffect.kind(m));
        assertEquals(2, TabEffect.semitones(m), 0);
    }

    @Test
    public void quarterToneIsHalfASemitone() {
        int m = marks(arrow(true, false), "1/4");
        assertEquals(.5, TabEffect.semitones(m), 0);
        assertEquals(.5, TabEffect.pitchOffset(m, 1, 2), 0);
    }

    @Test
    public void returnStrokeReachesOriginalPitch() {
        int m = marks(arrow(true, true), "½");
        assertEquals(TabEffect.BEND_RELEASE, TabEffect.kind(m));
        assertEquals(1, TabEffect.pitchOffset(m, 1, 2), 0);
        assertEquals(0, TabEffect.pitchOffset(m, 2, 2), 0);
    }

    @Test
    public void plainCurveAndDetachedAmountAreNotBends() {
        assertEquals(0, marks(arrow(false, false), "full"));
        byte[] blank = new byte[w * h];
        Arrays.fill(blank, (byte) 255);
        assertEquals(0, marks(blank, "1/4"));
    }

    @Test
    public void differentStringOwnerMustBeUnambiguous() {
        var other = new TablatureDecoder.Fret(101, 160, 1, 5);
        var tabs =
                TabGestureMarks.apply(
                        List.of(staff(List.of(fret, other))),
                        List.of(word("full", 150, 85)),
                        arrow(true, false),
                        w,
                        h);
        assertEquals(TabEffect.BEND, TabEffect.kind(tabs.get(0).frets().get(0).marks()));
        assertEquals(0, tabs.get(0).frets().get(1).marks());
    }

    @Test
    public void whammyIsExplicitAndSigned() {
        for (double d : new double[] {-4, -.5, .5, 2}) {
            int m = TabEffect.encodePitch(TabEffect.BAR_RELEASE, d);
            assertEquals(d, TabEffect.pitchOffset(m, 1, 2), 0);
            assertEquals(0, TabEffect.pitchOffset(m, 2, 2), 0);
        }
        var tabs =
                TabGestureMarks.apply(
                        List.of(staff(List.of(fret))),
                        List.of(word("full", 150, 85), word("w/bar", 175, 65)),
                        arrow(true, false),
                        w,
                        h);
        assertEquals(TabEffect.BAR, TabEffect.kind(tabs.get(0).frets().get(0).marks()));
    }

    @Test
    public void strumOrdersStringsWithoutChangingFretsOrRhythm() {
        var chord =
                List.of(
                        fret,
                        new TablatureDecoder.Fret(100, 180, 2, 5),
                        new TablatureDecoder.Fret(100, 240, 5, 5));
        for (String s : List.of("\uE846", "\uE847")) {
            var out =
                    TabGestureMarks.apply(
                                    List.of(staff(chord)), List.of(word(s, 100, 105)), null, w, h)
                            .get(0)
                            .frets();
            assertEquals(3, out.size());
            assertEquals(s.equals("\uE846") ? 0 : 2, TabEffect.strumIndex(out.get(0).marks()));
            assertEquals(s.equals("\uE846") ? 2 : 0, TabEffect.strumIndex(out.get(2).marks()));
            for (int i = 0; i < 3; i++) {
                assertEquals(chord.get(i).fret(), out.get(i).fret());
                assertEquals(chord.get(i).duration(), out.get(i).duration(), 0);
                assertEquals(0, TabEffect.pitchOffset(out.get(i).marks(), 1, 2), 0);
            }
        }
    }

    @Test
    public void strumDoesNotOverwriteABendOrMarkASingleNote() {
        int m = TabEffect.encode(TabEffect.BEND, 2);
        var bent = new TablatureDecoder.Fret(100, 140, 0, 5, 1, 0, 0, m);
        var chord = List.of(bent, new TablatureDecoder.Fret(100, 180, 2, 5));
        var out =
                TabGestureMarks.apply(
                                List.of(staff(chord)),
                                List.of(word("downstroke", 100, 105)),
                                null,
                                w,
                                h)
                        .get(0)
                        .frets();
        assertEquals(m, out.get(0).marks());
        assertEquals(0, out.get(1).marks());
        assertEquals(
                0,
                TabGestureMarks.apply(
                                List.of(staff(List.of(fret))),
                                List.of(word("upstroke", 100, 105)),
                                null,
                                w,
                                h)
                        .get(0)
                        .frets()
                        .get(0)
                        .marks());
    }

    @Test
    public void tokenIngressKeepsFractionsAndDirectionalGlyphsWhole() {
        var tokens = TabTextSource.tokens("1/4 ½ w/bar \uE846 \uE847 downstroke");
        assertTrue(
                tokens.containsAll(List.of("1/4", "½", "w/bar", "\uE846", "\uE847", "downstroke")));
    }

    @Test
    public void signedWhammyDownArrowRequiresBarOwnership() {
        byte[] g = arrow(true, false), flipped = new byte[g.length];
        for (int y = 0; y < h; y++) System.arraycopy(g, y * w, flipped, (h - 1 - y) * w, w);
        var f = new TablatureDecoder.Fret(100, 259, 5, 5);
        var t = new TablatureDecoder.Staff(159, 20, -1, List.of(f), List.of(20f, 480f));
        var out =
                TabGestureMarks.apply(
                        List.of(t),
                        List.of(word("-1/4", 150, 314), word("w/bar", 175, 334)),
                        flipped,
                        w,
                        h);
        int m = out.get(0).frets().get(0).marks();
        assertEquals(TabEffect.BAR, TabEffect.kind(m));
        assertEquals(-.5, TabEffect.semitones(m), 0);
        var unsigned =
                TabGestureMarks.apply(
                        List.of(t),
                        List.of(word("1/4", 150, 314), word("w/bar", 175, 334)),
                        flipped,
                        w,
                        h);
        assertEquals(-.5, TabEffect.semitones(unsigned.get(0).frets().get(0).marks()), 0);
        assertEquals(
                0,
                TabGestureMarks.apply(List.of(t), List.of(word("-1/4", 150, 314)), flipped, w, h)
                        .get(0)
                        .frets()
                        .get(0)
                        .marks());
    }

    @Test
    public void scannedStrumArrowNeedsHeadAndChord() {
        var chord = List.of(fret, new TablatureDecoder.Fret(100, 240, 5, 5));
        for (boolean up : List.of(true, false)) {
            byte[] g = new byte[w * h];
            Arrays.fill(g, (byte) 255);
            line(g, 100, 80, 100, 120);
            int tip = up ? 80 : 120, base = up ? 86 : 114;
            line(g, 100, tip, 94, base);
            line(g, 100, tip, 106, base);
            var out =
                    TabGestureMarks.apply(List.of(staff(chord)), List.of(), g, w, h).get(0).frets();
            assertEquals(
                    up ? TabEffect.STRUM_UP : TabEffect.STRUM_DOWN,
                    TabEffect.kind(out.get(0).marks()));
            assertEquals(up ? 0 : 1, TabEffect.strumIndex(out.get(0).marks()));
        }
        byte[] stem = new byte[w * h];
        Arrays.fill(stem, (byte) 255);
        line(stem, 100, 80, 100, 120);
        assertEquals(
                0,
                TabGestureMarks.apply(List.of(staff(chord)), List.of(), stem, w, h)
                        .get(0)
                        .frets()
                        .get(0)
                        .marks());
    }

    @Test
    public void invalidFractionAndNonFinitePitchReject() {
        for (double d : new double[] {.25, Double.NaN, Double.POSITIVE_INFINITY, 13}) {
            assertThrows(
                    IllegalArgumentException.class, () -> TabEffect.encodePitch(TabEffect.BEND, d));
        }
    }

    @Test
    public void graphicalGestureCarriesThroughAProvedSameFretTie() {
        var continuation = new TablatureDecoder.Fret(250, 140, 0, 5, 1, 0, 0, 0, true);
        var tabs =
                TabGestureMarks.apply(
                        List.of(staff(List.of(fret, continuation))),
                        List.of(word("1/4", 150, 85)),
                        arrow(true, false),
                        w,
                        h);
        var out = tabs.get(0).frets();
        assertEquals(.5, TabEffect.semitones(out.get(0).marks()), 0);
        assertEquals(out.get(0).marks(), out.get(1).marks());
        assertTrue(out.get(1).tied());
    }
}
