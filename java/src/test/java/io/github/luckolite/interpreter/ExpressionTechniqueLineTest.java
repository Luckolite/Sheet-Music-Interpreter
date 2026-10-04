// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original annotation geometry; contains no source score data. */
public class ExpressionTechniqueLineTest {
    private static final List<MeasureRegion> MEASURES =
            List.of(new MeasureRegion(.1f, .9f, .19f, .25f));
    private static final List<ScoreNoteEvent> NOTES =
            List.of(new ScoreNoteEvent(0, .125f, 2, 0, 1));
    private static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(200, 240, 10, 0, 1);

    private static PlayingTechniqueDetector.Word word(String text, float top, float bottom) {
        return new PlayingTechniqueDetector.Word(text, .201f, top, .33f, bottom);
    }

    private static List<ScoreTechniqueChange> detect(String text, float top, float bottom) {
        return PlayingTechniqueDetector.detect(
                List.of(word(text, top, bottom)), List.of(STAFF), MEASURES, NOTES, 1000, 1000);
    }

    @Test
    public void readsCompleteDynamicExpressionPhrases() {
        for (String dynamic : List.of("ppp", "pp", "p", "mp", "mf", "fff", "ff", "f")) {
            assertEquals(
                    ScoreTechniqueChange.CANTABILE,
                    PlayingTechniqueDetector.technique(dynamic + " cantabile"));
            assertEquals(
                    ScoreTechniqueChange.SOSTENUTO,
                    PlayingTechniqueDetector.technique(dynamic + " sostenuto."));
        }
    }

    @Test
    public void rejectsNegationProseSubstringsAndMultipleDirections() {
        for (String text :
                List.of(
                        "non pizz",
                        "non cantabile",
                        "mf cantabile title",
                        "title cantabile",
                        "pizza",
                        "f cantabilissimo",
                        "f cantabile sostenuto",
                        "pizz/arco",
                        "m cantabile"))
            assertEquals(text, -1, PlayingTechniqueDetector.technique(text));
    }

    @Test
    public void belowStaffPhraseAnchorsAtFirstAffectedHead() {
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .125f, 0, 1, ScoreTechniqueChange.CANTABILE)),
                detect("mf cantabile", .255f, .268f));
    }

    @Test
    public void belowStaffSingleExpressionAlsoSurvives() {
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .125f, 0, 1, ScoreTechniqueChange.SOSTENUTO)),
                detect("sostenuto", .255f, .268f));
    }

    @Test
    public void belowExpressionAllowsTheExistingHalfGapBoxPhaseTolerance() {
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .125f, 0, 1, ScoreTechniqueChange.CANTABILE)),
                detect("f cantabile", .235f, .252f));
        assertTrue(detect("cantabile", .231f, .249f).isEmpty());
    }

    @Test
    public void directionsTooFarBelowAndTechniqueWordsBelowRemainExcluded() {
        assertTrue(detect("cantabile", .29f, .31f).isEmpty());
        assertTrue(detect("pizz", .255f, .268f).isEmpty());
        assertTrue(detect("arco", .255f, .268f).isEmpty());
    }

    @Test
    public void ambiguousInterStaffExpressionIsNotAssigned() {
        var staffs = List.of(STAFF, new PlayingTechniqueDetector.Staff(280, 320, 10, 1, 2));
        assertTrue(
                PlayingTechniqueDetector.detect(
                                List.of(word("cantabile", .255f, .265f)),
                                staffs,
                                MEASURES,
                                NOTES,
                                1000,
                                1000)
                        .isEmpty());
    }

    @Test
    public void unambiguousBelowDirectionUsesUpperStaffInASystem() {
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(200, 240, 10, 0, 2),
                        new PlayingTechniqueDetector.Staff(300, 340, 10, 1, 2));
        var notes = List.of(new ScoreNoteEvent(0, .125f, 2, 0, 2));
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .125f, 0, 2, ScoreTechniqueChange.MARCATO)),
                PlayingTechniqueDetector.detect(
                        List.of(word("f marcato", .249f, .259f)),
                        staffs,
                        MEASURES,
                        notes,
                        1000,
                        1000));
    }

    @Test
    public void existingAboveDirectionsAndDuplicateSuppressionStayExact() {
        var annotation = word("arco", .175f, .19f);
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .125f, 0, 1, ScoreTechniqueChange.ARCO)),
                PlayingTechniqueDetector.detect(
                        List.of(annotation, annotation),
                        List.of(STAFF),
                        MEASURES,
                        NOTES,
                        1000,
                        1000));
    }

    @Test
    public void punctuationAndDefaultWhitespaceKeepWholeTokenSemantics() {
        String[] inputs = {
            null,
            "",
            "\t\r\n",
            ":;PIZZ.,;",
            " \tMF\tCANTABILE.\r\n",
            "f  sostenuto.;",
            "ppp\narco",
            "mp\u000barco",
            "mf\u00a0cantabile",
            "\u2003arco",
            " f cantabile \t title ",
            "ff",
            "mf\t"
        };
        int[] expected = {
            -1,
            -1,
            -1,
            ScoreTechniqueChange.PIZZICATO,
            ScoreTechniqueChange.CANTABILE,
            ScoreTechniqueChange.SOSTENUTO,
            ScoreTechniqueChange.ARCO,
            ScoreTechniqueChange.ARCO,
            -1,
            -1,
            -1,
            -1,
            -1
        };
        for (int i = 0; i < inputs.length; i++)
            assertEquals(
                    String.valueOf(inputs[i]),
                    expected[i],
                    PlayingTechniqueDetector.technique(inputs[i]));
    }
}
