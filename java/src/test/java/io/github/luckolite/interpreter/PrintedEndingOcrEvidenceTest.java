// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedEndingOcrEvidenceTest {
    private static List<PlayingTechniqueDetector.Word> read(
            String text, OcrText.Box box, List<OcrText.Element> elements) {
        var line = new OcrText.Line(text, box, elements);
        var ocr = new OcrText(text, List.of(new OcrText.Block(text, box, List.of(line))));
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        MusicalOcrEvidence.addDirectionWords(ocr, words, 2, 100, 1000, 1000);
        return words;
    }

    @Test
    public void combinedSelectorsRetainWholeLineAndCoordinates() {
        var words =
                read(
                        "1., 2.",
                        new OcrText.Box(20, 10, 100, 30),
                        List.of(
                                new OcrText.Element(
                                        "1.", new OcrText.Box(20, 10, 40, 30), List.of()),
                                new OcrText.Element(
                                        "2.", new OcrText.Box(60, 10, 100, 30), List.of())));
        assertEquals(1, words.size());
        assertEquals("1., 2.", words.get(0).text());
        assertEquals(.01f, words.get(0).left(), .00001f);
        assertEquals(.105f, words.get(0).top(), .00001f);
        assertEquals(.05f, words.get(0).right(), .00001f);
        assertEquals(.115f, words.get(0).bottom(), .00001f);
        assertNull(ScoreNavigationDetector.kind(words.get(0).text()));
    }

    @Test
    public void singleEndingWithNoElementBoxesIsRetained() {
        var words = read("3.", new OcrText.Box(20, 10, 40, 30), List.of());
        assertEquals(1, words.size());
        assertEquals(List.of(3), PrintedEndingBrackets.passes(words.get(0).text()));
    }

    @Test
    public void missingLineBoxPreservesExistingElementFallback() {
        var words =
                read(
                        "3",
                        null,
                        List.of(
                                new OcrText.Element(
                                        "3", new OcrText.Box(20, 10, 40, 30), List.of())));
        assertEquals(1, words.size());
        assertEquals("3", words.get(0).text());
    }
}
