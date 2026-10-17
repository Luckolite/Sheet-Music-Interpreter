// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class EnglishFlatAdmissionTest {
    static ScorePageInterpretation apply(List<PlayingTechniqueDetector.Word> words) {
        return ScoreExpressionDetector.apply(
                LexicalContextTest.score(),
                words,
                List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                LexicalContextTest.rails(),
                500,
                300);
    }

    @Test
    public void exactUnclassifiedFlatSpoofCannotBecomeDirection() {
        for (String term : EnglishContextOwnershipTest.TERMS) {
            var word = new PlayingTechniqueDetector.Word(term, .278f, .65f, .478f, .717f);
            assertEquals(PlayingTechniqueDetector.WordAdmission.UNCLASSIFIED, word.admission());
            assertFalse(EnglishCharacterOcrContext.admitted(word));
            var out = apply(List.of(word));
            assertTrue(out.expressiveEvents().isEmpty());
            assertEquals(LexicalContextTest.score().notes(), out.notes());
        }
    }

    @Test
    public void actualRichCollectorQualifiesCompleteContextSafeLine() {
        for (String term : EnglishContextOwnershipTest.TERMS) {
            var line = new OcrText.Line(term, new OcrText.Box(139, 195, 239, 215), List.of());
            var text =
                    new OcrText(
                            term,
                            List.of(
                                    EnglishContextOwnershipTest.block(
                                            line,
                                            new OcrText.Line(
                                                    "mf",
                                                    new OcrText.Box(139, 174, 159, 191),
                                                    List.of()))),
                            OcrText.Grouping.ORIGINAL_BLOCKS);
            var words = EnglishContextOwnershipTest.collect(text);
            assertEquals(1, words.size());
            assertTrue(EnglishCharacterOcrContext.admitted(words.get(0)));
            assertEquals(
                    PlayingTechniqueDetector.WordAdmission.CONTEXT_QUALIFIED,
                    words.get(0).admission());
            assertEquals(1, apply(words).expressiveEvents().size());
        }
    }

    @Test
    public void oldItalianFlatAdmissionUnchanged() {
        var word = new PlayingTechniqueDetector.Word("dolce", .278f, .65f, .478f, .717f);
        assertTrue(EnglishCharacterOcrContext.admitted(word));
        assertEquals(1, apply(List.of(word)).expressiveEvents().size());
    }

    @Test
    public void loneRichAndPortableLineCannotInventMusicalRole() {
        for (var grouping : OcrText.Grouping.values())
            for (String term : EnglishContextOwnershipTest.TERMS) {
                var line = new OcrText.Line(term, new OcrText.Box(139, 195, 239, 215), List.of());
                var text =
                        new OcrText(
                                term, List.of(EnglishContextOwnershipTest.block(line)), grouping);
                var words = EnglishContextOwnershipTest.collect(text);
                assertEquals(1, words.size());
                assertFalse(EnglishCharacterOcrContext.admitted(words.get(0)));
                assertTrue(apply(words).expressiveEvents().isEmpty());
            }
    }
}
