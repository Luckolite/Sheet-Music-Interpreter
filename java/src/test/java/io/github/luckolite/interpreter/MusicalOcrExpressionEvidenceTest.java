// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MusicalOcrExpressionEvidenceTest {
    private static List<PlayingTechniqueDetector.Word> words(String phrase) {
        var box = new OcrText.Box(100, 20, 220, 40);
        var element = new OcrText.Element(phrase, box, List.of());
        var line = new OcrText.Line(phrase, box, List.of(element));
        var text = new OcrText(phrase, List.of(new OcrText.Block(phrase, box, List.of(line))));
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        MusicalOcrEvidence.addDirectionWords(text, words, 1, 0, 500, 300);
        return words;
    }

    @Test
    public void ocrAdapterKeepsCompoundSlowingAndQualifiedStrength() {
        var result = words("poco rit.");
        assertEquals(1, result.size());
        assertEquals("poco rit.", result.get(0).text());
        assertEquals(
                ScoreExpressiveEvent.Strength.POCO,
                ExpressiveDirectionText.parse(result.get(0).text()).get(0).strength());
        assertEquals(1, words("crescendo e rall.").size());
    }

    @Test
    public void ocrAdapterKeepsMetricRelationshipAndAttackButRejectsOrdinaryText() {
        assertEquals(1, words("♩. = ♩").size());
        assertEquals(1, words("sfz").size());
        assertTrue(words("write a story").isEmpty());
    }

    @Test
    public void directionMatchingKeepsDefaultRegexBoundariesAndPrintedBoxes() {
        String[] phrases = {
            "non pizzicato",
            "SENZA pizzicato",
            "non\tpizzicato",
            "nonsense pizzicato",
            "non\npizzicato",
            "p\tpizzicato",
            "p\u00A0pizzicato"
        };
        for (int i = 0; i < phrases.length; i++) {
            String phrase = phrases[i];
            var lineBox = new OcrText.Box(100, 20, 220, 40);
            var elementBox = new OcrText.Box(160, 20, 220, 40);
            var element = new OcrText.Element("pizzicato", elementBox, List.of());
            var line = new OcrText.Line(phrase, lineBox, List.of(element));
            var block = new OcrText.Block(phrase, lineBox, List.of(line));
            var text = new OcrText(phrase, List.of(block));
            var result = new ArrayList<PlayingTechniqueDetector.Word>();
            MusicalOcrEvidence.addDirectionWords(text, result, 1, 0, 500, 300);
            if (i < 3) {
                assertTrue(phrase, result.isEmpty());
            } else {
                assertEquals(phrase, 1, result.size());
                var word = result.get(0);
                boolean compound = i == 5;
                assertEquals(compound ? phrase : "pizzicato", word.text());
                assertEquals(
                        Float.floatToRawIntBits((compound ? 100f : 160f) / 500),
                        Float.floatToRawIntBits(word.left()));
                assertEquals(
                        Float.floatToRawIntBits(20f / 300), Float.floatToRawIntBits(word.top()));
                assertEquals(
                        Float.floatToRawIntBits(220f / 500), Float.floatToRawIntBits(word.right()));
                assertEquals(
                        Float.floatToRawIntBits(40f / 300), Float.floatToRawIntBits(word.bottom()));
            }
            assertSame(block, text.getTextBlocks().get(0));
            assertSame(line, block.getLines().get(0));
            assertSame(element, line.getElements().get(0));
            assertSame(lineBox, line.getBoundingBox());
            assertSame(elementBox, element.getBoundingBox());
            assertEquals(phrase, line.getText());
            assertEquals("pizzicato", element.getText());
        }
    }
}
