// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

/** Original raster equations: OCR may retain only the numeral and its closing delimiter. */
public final class ClosedTempoDigitsTest {
    private PlayingTechniqueDetector.Word word(String text) {
        return new PlayingTechniqueDetector.Word(
                text, 164f / 500, 60f / 260, 190f / 500, 93f / 260);
    }

    private TempoUnitNoteTest.Page page() {
        var page = new TempoUnitNoteTest.Page();
        // An independent closing parenthesis to the right of the digit, inside its OCR box.
        page.rect(184, 60, 2, 4, (byte) 5);
        page.rect(186, 64, 2, 24, (byte) 5);
        page.rect(184, 88, 2, 4, (byte) 5);
        return page;
    }

    private List<MeasureNumberReconciler.NumberToken> admitted(
            TempoUnitNoteTest.Page page, String text) {
        return TempoChangeDetector.withIsolatedDigits(
                List.of(), List.of(word(text)), page.gray, page.w, page.h);
    }

    @Test
    public void singleClosingParenthesisAndBracketKeepTheProvedEquation() {
        for (String text : new String[] {"96)", "96]", "120)", "120]"}) {
            var page = page();
            var result = admitted(page, text);
            assertEquals(text, 1, result.size());
            assertEquals(text.startsWith("120") ? 120 : 96, result.get(0).value());
        }
    }

    @Test
    public void malformedNumeralsAndProseNeverEnterTheIsolatedRoute() {
        for (String text :
                new String[] {
                    "C96)", "(96)", "96))", "96)]", "96)) allegro", "96] prose",
                    "96/8]", "96.5)", "96-120)", "1960)", "9)", "20)"
                }) {
            assertTrue(text, admitted(page(), text).isEmpty());
        }
    }

    @Test
    public void missingBeatOrEqualsStillRejectsClosingDelimiter() {
        for (String text : new String[] {"96)", "96]"}) {
            var noBeat = page();
            noBeat.erase(99, 44, 24, 54);
            assertTrue(admitted(noBeat, text).isEmpty());
            var noEquals = page();
            noEquals.erase(130, 72, 21, 13);
            assertTrue(admitted(noEquals, text).isEmpty());
        }
    }

    @Test
    public void dottedQuarterUnitAndExistingPlainNumeralRemainTheSame() {
        var page = page();
        page.rect(124, 85, 3, 3, (byte) 5);
        var closed = admitted(page, "96)");
        var plain = admitted(page, "96");
        assertEquals(1, closed.size());
        assertEquals(plain, closed);
        var measures = List.of(new MeasureRegion(.04f, .96f, 100f / 260, .9f));
        assertEquals(
                List.of(new ScoreTempoChange(0, 0, 144, 1.5)),
                TempoChangeDetector.detect(closed, page.gray, page.w, page.h, measures));
    }

    @Test
    public void repeatedAdmissionPreservesRasterAndDoesNotDuplicateTheMark() {
        var page = page();
        byte[] before = page.gray.clone();
        var once = admitted(page, "96)");
        assertEquals(1, once.size());
        assertEquals(
                once,
                TempoChangeDetector.withIsolatedDigits(
                        once, List.of(word("96)")), page.gray, page.w, page.h));
        assertArrayEquals(before, page.gray);
    }
}
