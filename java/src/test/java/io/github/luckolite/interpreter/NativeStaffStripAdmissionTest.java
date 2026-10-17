// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeStaffStripAdmissionTest {
    @Test
    public void originalBlockAndRawMpCannotBypassTypedFullPageProducer() throws Exception {
        var english = new OcrText.Line("Flowing", new OcrText.Box(139, 195, 239, 215), List.of());
        var mp = new OcrText.Line("mp", new OcrText.Box(139, 174, 159, 191), List.of());
        var block =
                new OcrText.Block(
                        "Flowing\nmp", new OcrText.Box(139, 174, 239, 215), List.of(english, mp));
        var strip = new OcrText("Flowing\nmp", List.of(block), OcrText.Grouping.ORIGINAL_BLOCKS);
        var historical = new ArrayList<PlayingTechniqueDetector.Word>();
        MusicalOcrEvidence.addDirectionWords(strip, historical, 1, 0, 500, 300);
        assertEquals(1, historical.size());
        assertTrue(
                "historical collector intentionally preserved",
                EnglishCharacterOcrContext.admitted(historical.get(0)));
        var actualBoundary = NativeDirectionOcrCache.legacyStaffStripWords(historical);
        assertTrue(actualBoundary.isEmpty());
        var score = LexicalContextTest.score();
        var gray = LexicalContextTest.rails();
        var out =
                ScoreExpressionDetector.apply(
                        score, actualBoundary, NativeEnglishPositiveTest.staffs(), gray, 500, 300);
        assertTrue(out.expressiveEvents().isEmpty());
        assertEquals(
                score.notes(),
                PrintedWordArticulations.apply(
                        actualBoundary,
                        NativeEnglishPositiveTest.staffs(),
                        score.measures(),
                        score.notes(),
                        new byte[500 * 300],
                        gray,
                        500,
                        300));
        assertTrue(
                ExpressiveWordBodyInk.detect(
                                actualBoundary,
                                new byte[500 * 300],
                                gray,
                                500,
                                300,
                                List.of(new NoteArticulationDetector.Anchor(139, 164, 16, 0)))
                        .isEmpty());
    }

    @Test
    public void oldNonEnglishStaffStripWordsRetainExactEvidence() {
        var old = new PlayingTechniqueDetector.Word("dolce", .278f, .65f, .478f, .717f);
        var filtered = NativeDirectionOcrCache.legacyStaffStripWords(List.of(old));
        assertEquals(List.of(old), filtered);
        assertSame(old, filtered.get(0));
        var score =
                ScoreExpressionDetector.apply(
                        LexicalContextTest.score(),
                        filtered,
                        NativeEnglishPositiveTest.staffs(),
                        LexicalContextTest.rails(),
                        500,
                        300);
        assertEquals(1, score.expressiveEvents().size());
    }
}
