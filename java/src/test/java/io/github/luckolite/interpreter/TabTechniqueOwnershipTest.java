// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic OCR boxes; no imported score data. */
public class TabTechniqueOwnershipTest {
    private static final int W = 500, H = 400;

    @Test
    public void halfAndThirtySecondLabelsRemainDurationOnlyInTheProductionPipeline() {
        var words =
                List.of(
                        word("5", 100, 140), word("Q", 100, 105),
                        word("7", 160, 140), word("H", 160, 105),
                        word("9", 220, 140), word("T", 220, 105));
        var tabs = pipeline(words);
        var fs = tabs.get(0).frets();
        assertEquals(3, fs.size());
        assertEquals(1, fs.get(0).duration(), 0);
        assertEquals(2, fs.get(1).duration(), 0);
        assertEquals(3, fs.get(2).beams());
        for (var f : fs) assertEquals("Rhythm label changed fret " + f.fret(), 0, f.marks());
        var score =
                TablatureDecoder.apply(
                        new ScorePageInterpretation(List.of(), List.of()), tabs, W, H);
        assertEquals(3, score.notes().size());
        for (var n : score.notes()) assertEquals(0, n.articulations());
    }

    @Test
    public void printedHammerPullAndTapStillOwnTheirExplicitTechniqueLane() {
        var tabs =
                pipeline(
                        List.of(
                                word("5", 100, 140), word("T", 100, 105),
                                word("7", 160, 140), word("H", 160, 105),
                                word("5", 220, 140), word("P", 220, 105)));
        var fs = tabs.get(0).frets();
        assertEquals(TabEffect.TAP, TabEffect.kind(fs.get(0).marks()));
        assertEquals(TabEffect.HAMMER, TabEffect.kind(fs.get(1).marks()));
        assertEquals(TabEffect.PULL, TabEffect.kind(fs.get(2).marks()));
        for (var f : fs) assertEquals(0, f.duration(), 0);
    }

    @Test
    public void hammerAboveASeparateRhythmLaneRemainsATechnique() {
        var tabs =
                pipeline(
                        List.of(
                                word("5", 100, 140),
                                word("Q", 100, 105),
                                word("7", 160, 140),
                                word("Q", 160, 105),
                                word("H", 160, 75)));
        assertEquals(TabEffect.HAMMER, TabEffect.kind(tabs.get(0).frets().get(1).marks()));
        assertEquals(1, tabs.get(0).frets().get(1).duration(), 0);
    }

    @Test
    public void trailingVibratoBelongsOnlyToTheLastFretOfATechniqueChain() {
        for (String text : List.of("5h7~", "7p5~", "14/16~~", "16\\14~", "5h7p5~")) {
            var tabs = pipeline(List.of(word(text, 200, 140, 120)));
            var fs = tabs.get(0).frets();
            assertEquals(text, text.equals("5h7p5~") ? 3 : 2, fs.size());
            for (int i = 0; i < fs.size(); i++)
                assertEquals(
                        text + " token " + i,
                        i == fs.size() - 1,
                        (fs.get(i).marks() & TabEffect.VIBRATO) != 0);
            assertTrue(TabEffect.kind(fs.get(1).marks()) != 0);
            var score =
                    TablatureDecoder.apply(
                            new ScorePageInterpretation(List.of(), List.of()), tabs, W, H);
            assertEquals(fs.size(), score.notes().size());
            for (int i = 0; i < score.notes().size(); i++)
                assertEquals(
                        i == score.notes().size() - 1,
                        (score.notes().get(i).articulations() & TabEffect.VIBRATO) != 0);
        }
    }

    @Test
    public void singleBendsAndHarmonicsKeepTheirTrailingVibrato() {
        for (String text : List.of("7~", "7b9~", "7b9r7~", "<7>~")) {
            var fs = TabNotation.parse(text, 100, 180, 140, 0);
            assertEquals(1, fs.size());
            assertTrue((fs.get(0).marks() & TabEffect.VIBRATO) != 0);
        }
    }

    public static void main(String[] args) {
        var fs =
                pipeline(
                                List.of(
                                        word("5", 100, 140), word("Q", 100, 105),
                                        word("7", 160, 140), word("H", 160, 105),
                                        word("9", 220, 140), word("T", 220, 105)))
                        .get(0)
                        .frets();
        for (var f : fs)
            System.out.printf(
                    "fret=%d duration=%.3f beams=%d effect=%s gain=%.2f tied=%s%n",
                    f.fret(),
                    f.duration(),
                    f.beams(),
                    TabEffect.name(f.marks()),
                    TabEffect.gain(f.marks()),
                    f.tied());
        for (String text : List.of("5h7~", "14/16~"))
            for (var f : pipeline(List.of(word(text, 200, 140, 120))).get(0).frets())
                System.out.printf(
                        "text=%s fret=%d effect=%s vibrato=%s%n",
                        text,
                        f.fret(),
                        TabEffect.name(f.marks()),
                        (f.marks() & TabEffect.VIBRATO) != 0);
    }

    private static List<TablatureDecoder.Staff> pipeline(List<TablatureDecoder.Word> words) {
        var staff = new TablatureDecoder.Staff(140, 20, -1, List.of(), List.of(20f, 480f));
        var tabs = TablatureDecoder.withWords(List.of(staff), words, W, H);
        byte[] pixels = new byte[W * H];
        Arrays.fill(pixels, (byte) 255);
        return TabNotation.rasterRhythm(tabs, pixels, W, H, words);
    }

    private static TablatureDecoder.Word word(String text, float x, float y) {
        return word(text, x, y, 12);
    }

    private static TablatureDecoder.Word word(String text, float x, float y, float width) {
        return new TablatureDecoder.Word(
                text, (x - width / 2) / W, (y - 6) / H, (x + width / 2) / W, (y + 6) / H);
    }
}
