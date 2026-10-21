// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original text and annotation geometry, no score data. */
public class QualifiedTechniqueCharacterTest {
    @Test
    public void bowedReturnKeepsIndependentCharacter() {
        assertEquals(
                ScoreTechniqueChange.ARCO, PlayingTechniqueDetector.technique("arco scherzando"));
        assertEquals(
                ScoreTechniqueChange.PIZZICATO,
                PlayingTechniqueDetector.technique("pizzicato dolce"));
    }

    @Test
    public void punctuationAndAsciiWhitespaceKeepCompletePhrase() {
        assertEquals(
                ScoreTechniqueChange.ARCO,
                PlayingTechniqueDetector.technique(" ;ARCO,\tSCHERZANDO.; "));
    }

    @Test
    public void negationAndProseNeverSupplyTechnique() {
        for (String s :
                List.of(
                        "non arco scherzando",
                        "arco non scherzando",
                        "arco scherzando title",
                        "title arco scherzando",
                        "arco pizza",
                        "arco mf",
                        "arco pizz",
                        "arco sostenuto",
                        "arco cantabile"))
            assertEquals(s, -1, PlayingTechniqueDetector.technique(s));
    }

    @Test
    public void pickupOwnsSwitchAndCharacterTextStaysAvailable() {
        var staff = List.of(new PlayingTechniqueDetector.Staff(120, 160, 10, 0, 1));
        var bars = List.of(new MeasureRegion(.1f, .9f, .2f, .4f));
        var notes = List.of(new ScoreNoteEvent(0, .875f, 1, 0, 1));
        var word = new PlayingTechniqueDetector.Word("arco scherzando", .801f, .19f, .96f, .22f);
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .875f, 0, 1, ScoreTechniqueChange.ARCO)),
                PlayingTechniqueDetector.detect(List.of(word), staff, bars, notes, 1000, 500));
        assertEquals("arco", ExpressiveDirectionText.parse(word.text()).get(0).qualifierText());
    }

    @Test
    public void belowStaffTextCannotTriggerPhysicalMode() {
        assertTrue(
                PlayingTechniqueDetector.detect(
                                List.of(
                                        new PlayingTechniqueDetector.Word(
                                                "arco scherzando", .4f, .35f, .6f, .38f)),
                                List.of(new PlayingTechniqueDetector.Staff(120, 160, 10, 0, 1)),
                                List.of(new MeasureRegion(.1f, .9f, .2f, .4f)),
                                List.of(),
                                1000,
                                500)
                        .isEmpty());
    }
}
