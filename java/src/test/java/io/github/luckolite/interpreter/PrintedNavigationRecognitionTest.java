// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original synthetic diagnostics; no source-score pixels or geometry are embedded. */
public class PrintedNavigationRecognitionTest {
    static int checks = 0;
    static List<String> failures = new ArrayList<>();

    static void check(boolean ok, String name) {
        checks++;
        if (!ok) failures.add(name);
    }

    static PrintedRepeatGlyphs.Mark repeat(int at, boolean out) {
        return new PrintedRepeatGlyphs.Mark(at, out, .5f, 0, 1);
    }

    static PrintedEndingBrackets.Mark ending(int start, int end, Integer... passes) {
        return new PrintedEndingBrackets.Mark(
                start, end, List.of(passes), Arrays.toString(passes), .5f, 0, 1);
    }

    static PrintedRepeatNavigation.Result group(
            List<PrintedRepeatGlyphs.Mark> bars, PrintedEndingBrackets.Mark... ends) {
        return PrintedRepeatNavigation.assemble(bars, List.of(ends), 2);
    }

    static void rect(byte[] gray, int w, int x, int y, int ww, int hh) {
        for (int yy = y; yy < y + hh; yy++)
            for (int xx = x; xx < x + ww; xx++) gray[yy * w + xx] = 0;
    }

    static byte[] page() {
        byte[] gray = new byte[600 * 240];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++) rect(gray, 600, 40, 120 + line * 12, 520, 1);
        return gray;
    }

    static List<PlayingTechniqueDetector.Staff> staff =
            List.of(new PlayingTechniqueDetector.Staff(120, 168, 12, 0, 1));
    static List<MeasureRegion> measures =
            List.of(
                    new MeasureRegion(.15f, .49f, .45f, .75f),
                    new MeasureRegion(.51f, .92f, .45f, .75f));

    static List<PrintedRepeatGlyphs.Mark> read(byte[] gray) {
        return PrintedRepeatGlyphs.detect(gray, 600, 240, staff, measures);
    }

    static void dot(byte[] gray, int x, int y) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                if (dx * dx + dy * dy <= 5) gray[(y + dy) * 600 + x + dx] = 0;
    }

    static byte[] scaled(byte[] gray, float scale, float slope) {
        int w = Math.round(600 * scale), h = Math.round(240 * scale);
        byte[] out = new byte[w * h];
        Arrays.fill(out, (byte) 255);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int xx = Math.min(599, (int) (x / scale)),
                        yy = (int) (y / scale) - Math.round((xx - 300) * slope);
                if (yy >= 0 && yy < 240) out[y * w + x] = gray[yy * 600 + xx];
            }
        return out;
    }

    @org.junit.Test
    public void originalSyntheticNavigationControls() {
        checks = 0;
        failures.clear();
        main(new String[0]);
    }

    public static void main(String[] args) {
        var bars = List.of(repeat(1, false), repeat(5, true));
        var ordinary = group(bars);
        check(
                ordinary.directions().size() == 2 && ordinary.unresolved().isEmpty(),
                "ordinary repeat");
        var three = group(bars, ending(4, 5, 1, 2), ending(5, 6, 3));
        check(
                three.directions().size() == 4
                        && three.directions().stream().allMatch(d -> d.details().totalPlays() == 3),
                "three passes from coverage");
        var route = ScoreNavigationPlan.create(7, three.directions()).sourceMeasures();
        check(route.equals(List.of(0, 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 5, 6)), "three pass route");
        check(
                group(bars, ending(4, 5, 1), ending(5, 6, 3)).directions().isEmpty(),
                "missing pass rejected");
        check(
                group(bars, ending(4, 5, 1, 2), ending(5, 6, 2, 3)).directions().isEmpty(),
                "duplicate pass rejected");
        check(
                group(bars, ending(4, 5, 1)).directions().isEmpty(),
                "partial cross-page ending unresolved");
        check(
                group(bars, ending(4, 5, 2), ending(5, 6, 1, 3)).directions().isEmpty(),
                "early pass cannot skip its only backward edge");
        check(
                group(bars, ending(4, 5, 1), ending(5, 6, 2), ending(6, 7, 3))
                        .directions()
                        .isEmpty(),
                "multiple endings need additional backward edge proof");
        check(
                three.directions().stream()
                        .allMatch(
                                d ->
                                        d.details().evidence().size() == 1
                                                && d.details().evidence().get(0).pageIndex() == 2),
                "source page evidence preserved");
        check(
                !group(List.of(repeat(5, true))).unresolved().isEmpty(),
                "unpaired closing unresolved");
        check(
                !group(List.of(repeat(1, false))).unresolved().isEmpty(),
                "unpaired opening unresolved");
        check(
                group(bars, ending(3, 4, 1), ending(5, 6, 2)).directions().isEmpty(),
                "wrong closing endpoint rejected");
        check(PrintedEndingBrackets.passes("1., 2.").equals(List.of(1, 2)), "punctuation selector");
        check(
                PrintedEndingBrackets.passes("129.").isEmpty()
                        && PrintedEndingBrackets.passes("12101").isEmpty(),
                "out of bounds fingerings");
        check(
                PrintedEndingBrackets.passes("3").equals(List.of(3)),
                "single selector needs separate bracket proof");
        var gray = page();
        rect(gray, 600, 70, 120, 6, 49);
        rect(gray, 600, 82, 120, 2, 49);
        dot(gray, 94, 138);
        dot(gray, 94, 150);
        check(
                read(gray).size() == 1
                        && read(gray).get(0).boundary() == 0
                        && !read(gray).get(0).outgoing(),
                "forward repeat ink");
        var dots = page();
        dot(dots, 94, 138);
        dot(dots, 94, 150);
        check(read(dots).isEmpty(), "paired dots without bars");
        var single = page();
        rect(single, 600, 70, 120, 6, 49);
        rect(single, 600, 82, 120, 2, 49);
        dot(single, 94, 138);
        check(read(single).isEmpty(), "single dot rejected");
        var doublebar = page();
        rect(doublebar, 600, 70, 120, 2, 49);
        rect(doublebar, 600, 82, 120, 2, 49);
        dot(doublebar, 94, 138);
        dot(doublebar, 94, 150);
        check(read(doublebar).isEmpty(), "equal thin bars rejected");
        var bracket = page();
        rect(bracket, 600, 306, 40, 246, 2);
        rect(bracket, 600, 306, 40, 2, 51);
        var words =
                List.of(
                        new PlayingTechniqueDetector.Word(
                                "1., 2.", 310 / 600f, 45 / 240f, 350 / 600f, 60 / 240f));
        var endings = PrintedEndingBrackets.detect(bracket, 600, 240, staff, measures, words);
        check(
                endings.size() == 1 && endings.get(0).start() == 1 && endings.get(0).end() == 2,
                "open bracket with selectors");
        check(
                PrintedEndingBrackets.detect(page(), 600, 240, staff, measures, words).isEmpty(),
                "digits without bracket rejected");
        var roof = page();
        rect(roof, 600, 306, 40, 246, 2);
        check(
                PrintedEndingBrackets.detect(roof, 600, 240, staff, measures, words).isEmpty(),
                "roof without hook rejected");
        var backwards = page();
        rect(backwards, 600, 546, 120, 2, 49);
        rect(backwards, 600, 555, 120, 6, 49);
        dot(backwards, 534, 138);
        dot(backwards, 534, 150);
        var back = read(backwards);
        check(
                back.size() == 1 && back.get(0).outgoing() && back.get(0).boundary() == 2,
                "backward repeat ink");
        rect(bracket, 600, 550, 40, 2, 51);
        check(
                PrintedEndingBrackets.detect(bracket, 600, 240, staff, measures, words).size() == 1,
                "closed ending bracket");
        check(
                PrintedRepeatGlyphs.detect(
                                scaled(gray, .75f, -.02f),
                                450,
                                180,
                                List.of(new PlayingTechniqueDetector.Staff(90, 126, 9, 0, 1)),
                                measures)
                        .isEmpty(),
                "subsampled incomplete staff rails remain unproved");
        // Two-pixel original rules retain all five rails through nearest-neighbor downsampling.
        for (int line = 0; line < 5; line++) {
            rect(gray, 600, 40, 120 + line * 12, 520, 2);
            rect(backwards, 600, 40, 120 + line * 12, 520, 2);
        }
        for (float scale : new float[] {.75f, 1f, 1.5f, 2f})
            for (float slope : new float[] {-.02f, 0, .02f}) {
                int w = Math.round(600 * scale), h = Math.round(240 * scale);
                var scaledStaff =
                        List.of(
                                new PlayingTechniqueDetector.Staff(
                                        120 * scale, 168 * scale, 12 * scale, 0, 1));
                var marks =
                        PrintedRepeatGlyphs.detect(
                                scaled(gray, scale, slope), w, h, scaledStaff, measures);
                check(
                        marks.size() == 1 && marks.get(0).boundary() == 0,
                        "forward scale/skew " + scale + "/" + slope);
                marks =
                        PrintedRepeatGlyphs.detect(
                                scaled(backwards, scale, slope), w, h, scaledStaff, measures);
                check(
                        marks.size() == 1 && marks.get(0).boundary() == 2,
                        "backward scale/skew " + scale + "/" + slope);
                var markedWords =
                        List.of(
                                new PlayingTechniqueDetector.Word(
                                        "1., 2.",
                                        310 / 600f,
                                        (45 + 10 * slope) / 240f,
                                        350 / 600f,
                                        (60 + 50 * slope) / 240f));
                check(
                        PrintedEndingBrackets.detect(
                                                scaled(bracket, scale, slope),
                                                w,
                                                h,
                                                scaledStaff,
                                                measures,
                                                markedWords)
                                        .size()
                                == 1,
                        "bracket scale/skew " + scale + "/" + slope);
            }
        var expanded = new ArrayList<MeasureRegion>();
        for (int i = 0; i < 12; i++) expanded.add(measures.get(0));
        expanded.add(measures.get(1));
        var expandedBack = PrintedRepeatGlyphs.detect(backwards, 600, 240, staff, expanded);
        check(
                expandedBack.size() == 1 && expandedBack.get(0).boundary() == 13,
                "outgoing follows expanded logical measures");
        var expandedEnding =
                PrintedEndingBrackets.detect(bracket, 600, 240, staff, expanded, words);
        check(
                expandedEnding.size() == 1
                        && expandedEnding.get(0).start() == 12
                        && expandedEnding.get(0).end() == 13,
                "ending uses expanded logical measure indexes");
        var duplicateBars = new ArrayList<>(bars);
        duplicateBars.add(new PrintedRepeatGlyphs.Mark(1, false, .5f, 1, 2));
        duplicateBars.add(new PrintedRepeatGlyphs.Mark(5, true, .5f, 1, 2));
        check(
                group(duplicateBars, ending(4, 5, 1, 2), ending(5, 6, 3)).directions().size() == 4,
                "repeated staff marks share one navigation route");
        var multiBars = List.of(repeat(0, false), repeat(3, true), repeat(4, true));
        var multi = group(multiBars, ending(2, 3, 1), ending(3, 4, 2), ending(4, 5, 3));
        check(
                multi.unresolved().isEmpty() && multi.directions().size() == 6,
                "three distinct endings own two backward edges");
        check(
                ScoreNavigationPlan.create(6, multi.directions())
                        .sourceMeasures()
                        .equals(List.of(0, 1, 2, 0, 1, 3, 0, 1, 4, 5)),
                "distinct ending edge traversal");
        var detached =
                group(
                        List.of(repeat(0, false), repeat(3, true), repeat(5, true)),
                        ending(2, 3, 1),
                        ending(3, 4, 2),
                        ending(4, 6, 3));
        check(
                detached.directions().isEmpty() && !detached.unresolved().isEmpty(),
                "unrelated backward edge cannot fill missing ending edge");
        var nested =
                group(
                        List.of(
                                repeat(0, false),
                                repeat(1, false),
                                repeat(4, true),
                                repeat(5, true),
                                repeat(7, true)),
                        ending(3, 4, 1),
                        ending(4, 5, 2),
                        ending(5, 6, 3));
        check(
                nested.unresolved().isEmpty() && nested.directions().size() == 8,
                "alternate inner edges preserve outer open repeat");
        var nestedRoute = ScoreNavigationPlan.create(8, nested.directions()).traversal();
        check(
                nestedRoute.complete()
                        && nestedRoute.diagnostics().isEmpty()
                        && nestedRoute
                                .sourceMeasures()
                                .equals(
                                        List.of(
                                                0, 1, 2, 3, 1, 2, 4, 1, 2, 5, 6, 0, 1, 2, 3, 1, 2,
                                                4, 1, 2, 5, 6, 7)),
                "nested alternate routes reset inner passes on outer repeat");
        if (!failures.isEmpty()) throw new AssertionError(failures);
        System.out.println("PASS " + checks + " original synthetic controls");
    }
}
