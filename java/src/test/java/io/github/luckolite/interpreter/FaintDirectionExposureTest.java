// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original five-rule scenes; a nonclipping exposure shift preserves their optical contrast. */
public final class FaintDirectionExposureTest {
    @Test
    public void additiveExposurePreservesTheSameFiveRules() {
        float[] reference = FaintDirectionRailsTest.read(5, 198, 180, 0, false, false, false, 0);
        assertNotNull(reference);
        for (int shift : new int[] {20, 40, 50}) {
            float[] exposed =
                    FaintDirectionRailsTest.read(
                            5, 198 + shift, 180 + shift, 0, false, false, false, 0);
            assertNotNull("same contrast after exposure shift " + shift, exposed);
            assertArrayEquals(reference, exposed, .001f);
        }
    }

    @Test
    public void brightWeakContrastStillRefuses() {
        assertNull(FaintDirectionRailsTest.read(5, 248, 238, 0, false, false, false, 0));
    }

    @Test
    public void brightFourRulesStillRefuse() {
        assertNull(FaintDirectionRailsTest.read(4, 248, 230, 0, false, false, false, 0));
    }

    @Test
    public void brightUnilateralRulesStillRefuse() {
        assertNull(FaintDirectionRailsTest.read(5, 248, 230, 0, true, false, false, 0));
    }

    @Test
    public void brightBroadBandsStillRefuse() {
        assertNull(FaintDirectionRailsTest.read(5, 248, 230, 5, false, false, false, 0));
    }

    @Test
    public void brightWrongPhaseStillRefuses() {
        assertNull(FaintDirectionRailsTest.read(5, 248, 230, 0, false, false, true, 0));
    }

    @Test
    public void brightBlankPaperStillRefuses() {
        assertNull(FaintDirectionRailsTest.read(0, 248, 230, 0, false, false, false, 0));
    }

    private static ScorePageInterpretation detect(String text, boolean head, int rails) {
        int width = 600, height = 350;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 248);
        for (int line = 0; line < rails; line++)
            for (int x = 100; x < 580; x++) gray[(200 - line * 14) * width + x] = (byte) 230;
        var notes =
                head
                        ? List.of(
                                new ScoreNoteEvent(
                                        0, 100f / 420, 0, 0, 1, 200f / height, false, 0, 0, 2, 1))
                        : List.<ScoreNoteEvent>of();
        var score =
                new ScorePageInterpretation(
                        List.of(new MeasureRegion(.2f, .9f, .35f, .65f)),
                        notes,
                        1,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 1, 4)),
                        List.of(),
                        List.of(),
                        List.of());
        byte[] before = gray.clone();
        var result =
                ScoreExpressionDetector.apply(
                        score,
                        List.of(
                                new PlayingTechniqueDetector.Word(
                                        text,
                                        220f / width,
                                        220f / height,
                                        280f / width,
                                        234f / height)),
                        List.of(new PlayingTechniqueDetector.Staff(144, 200, 14, 0, 1)),
                        gray,
                        width,
                        height);
        assertArrayEquals(before, gray);
        assertEquals(notes, result.notes());
        return result;
    }

    @Test
    public void exposedTempoWordsKeepTheirWrittenColumn() {
        for (String text : List.of("rit.", "a tempo")) {
            var events = detect(text, true, 5).expressiveEvents();
            assertEquals(text, 1, events.size());
            assertEquals(
                    text.equals("rit.")
                            ? ScoreExpressiveEvent.Kind.RITARDANDO
                            : ScoreExpressiveEvent.Kind.A_TEMPO,
                    events.get(0).kind());
            assertEquals(new ScoreAnchor(0, 0), events.get(0).start().orElseThrow());
        }
    }

    @Test
    public void exposedTempoStillRequiresAWrittenHead() {
        assertTrue(detect("rit.", false, 5).expressiveEvents().isEmpty());
    }

    @Test
    public void exposedTempoStillRequiresFiveRules() {
        assertTrue(detect("a tempo", true, 4).expressiveEvents().isEmpty());
    }
}
