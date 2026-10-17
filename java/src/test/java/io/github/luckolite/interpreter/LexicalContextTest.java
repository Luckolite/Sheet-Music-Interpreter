// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original OCR block contexts: an exact supported word is not necessarily a direction. */
public final class LexicalContextTest {
    static final int W = 500, H = 300;

    static byte[] rails() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 460; x++) g[y * W + x] = 0;
        return g;
    }

    static ScorePageInterpretation score() {
        var n = new ScoreNoteEvent(0, .27f, 0, 0, 1, 164f / H, false, 0, 0, 0, 1, 1, 0);
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.04f, .92f, .25f, .65f)),
                List.of(n),
                1,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    static List<PlayingTechniqueDetector.Word> collect(String word, int kind) {
        var wb = new OcrText.Box(139, kind == 2 ? 195 : 54, 239, kind == 2 ? 215 : 72);
        var other = new OcrText.Box(130, kind == 2 ? 220 : 76, 280, kind == 2 ? 240 : 92);
        String context =
                kind == 0
                        ? "Music by Original Author"
                        : kind == 1
                                ? "Copyright Original Publisher"
                                : "Verse 1: these are the sung words";
        var lines =
                List.of(
                        new OcrText.Line(word, wb, List.of()),
                        new OcrText.Line(context, other, List.of()));
        var box = new OcrText.Box(130, kind == 2 ? 195 : 54, 280, kind == 2 ? 240 : 92);
        var txt =
                new OcrText(
                        word + "\n" + context,
                        List.of(new OcrText.Block(word + "\n" + context, box, lines)));
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        MusicalOcrEvidence.addDirectionWords(txt, words, 1, 0, W, H);
        return words;
    }

    static ScorePageInterpretation apply(String word, int kind) {
        return ScoreExpressionDetector.apply(
                score(),
                collect(word, kind),
                List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                rails(),
                W,
                H);
    }

    @Test
    public void titleWithinExplicitTitleCreditBlockIsNotDirection() {
        for (String w :
                List.of(
                        "Flowing",
                        "Triumphantly",
                        "Ethereal",
                        "With driving intensity",
                        "Like an echo")) assertTrue(w, apply(w, 0).expressiveEvents().isEmpty());
    }

    @Test
    public void supportedWordWithinPublisherCreditBlockIsNotDirection() {
        for (String w :
                List.of(
                        "Flowing",
                        "Triumphantly",
                        "Ethereal",
                        "With driving intensity",
                        "Like an echo")) assertTrue(w, apply(w, 1).expressiveEvents().isEmpty());
    }

    @Test
    public void completeSupportedLyricLineBelowStaffIsNotDirection() {
        for (String w :
                List.of(
                        "Flowing",
                        "Triumphantly",
                        "Ethereal",
                        "With driving intensity",
                        "Like an echo")) assertTrue(w, apply(w, 2).expressiveEvents().isEmpty());
    }

    public static void main(String[] args) {
        for (int k = 0; k < 3; k++)
            for (String w :
                    List.of(
                            "Flowing",
                            "Triumphantly",
                            "Ethereal",
                            "With driving intensity",
                            "Like an echo")) {
                var d = apply(w, k);
                System.out.println(
                        k
                                + ","
                                + w
                                + ",collected="
                                + collect(w, k).size()
                                + ",events="
                                + d.expressiveEvents().size()
                                + ",notesExact="
                                + score().notes().equals(d.notes()));
            }
    }
}
