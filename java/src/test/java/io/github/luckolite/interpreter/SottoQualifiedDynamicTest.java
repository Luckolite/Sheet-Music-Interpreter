// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original semantic and raw-ink controls, independent of private scores. */
public class SottoQualifiedDynamicTest {
    @Test
    public void explicitLevelsSurviveSottoQualifier() {
        for (String level : List.of("ppp", "pp", "p", "mp", "mf", "f", "ff", "fff"))
            assertEquals(
                    ScoreDynamicsDetector.level(level),
                    ScoreDynamicsDetector.level(level + " sotto"),
                    0);
    }

    @Test
    public void completeSottoVoceAndCharacterPhrasesRetainLevel() {
        for (String suffix :
                List.of(
                        "sotto voce",
                        "sotto e dolce",
                        "sotto e cantabile",
                        "sotto e espressivo",
                        "sotto e lacrimoso"))
            assertEquals(-4, ScoreDynamicsDetector.level("mp " + suffix), 0);
    }

    @Test
    public void capitalizationAndTrailingPunctuationAreHarmless() {
        assertEquals(-4, ScoreDynamicsDetector.level(" MP SOTTO VOCE. "), 0);
    }

    @Test
    public void characterAloneDoesNotInventNumericLoudness() {
        for (String text : List.of("sotto", "sotto voce", "sotto e lacrimoso", "dolce", "voce"))
            assertTrue(text, Float.isNaN(ScoreDynamicsDetector.level(text)));
    }

    @Test
    public void ordinaryTextAndMultipleLevelsRemainRejected() {
        for (String text :
                List.of(
                        "mp sotto the rainbow",
                        "mp sotto mf",
                        "mp sotto e forte",
                        "sample mp sotto",
                        "pianissimo sotto",
                        "mp sotto voce extra",
                        "m sotto",
                        "sottop",
                        "fee")) assertTrue(text, Float.isNaN(ScoreDynamicsDetector.level(text)));
        assertFalse(ScoreDynamicsDetector.dynamicLine("fee"));
    }

    static List<PlayingTechniqueDetector.Word> read(String line, String text, boolean ink) {
        byte[] g = new byte[300 * 150];
        Arrays.fill(g, (byte) 255);
        if (ink) for (int y = 60; y < 75; y++) for (int x = 80; x < 140; x++) g[y * 300 + x] = 0;
        return ScoreDynamicsDetector.ocrWords(
                line,
                new PlayingTechniqueDetector.Word(
                        text, 80f / 300, 60f / 150, 140f / 300, 75f / 150),
                g,
                300,
                150);
    }

    @Test
    public void mergedOcrRetainsCompletePrintedEvidence() {
        var words = read("mp sotto e lacrimoso", "mp sotto e lacrimoso", true);
        assertEquals(1, words.size());
        assertEquals("mp sotto e lacrimoso", words.get(0).text());
        assertEquals(-4, ScoreDynamicsDetector.level(words.get(0).text()), 0);
    }

    @Test
    public void splitOcrLevelInQualifiedLineIsAccepted() {
        assertEquals("mp", read("mp sotto", "mp", true).get(0).text());
    }

    @Test
    public void blankPixelsStillCannotCreateDynamic() {
        assertTrue(read("mp sotto", "mp sotto", false).isEmpty());
    }
}
