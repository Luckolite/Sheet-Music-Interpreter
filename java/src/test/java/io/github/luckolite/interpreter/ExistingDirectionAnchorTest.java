// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExistingDirectionAnchorTest {
    @Test
    public void widerWordBoxCannotReplaceAlreadyProvedLeftAnchor() {
        int width = 1000, height = 650;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 180; x < 820; x++) gray[(300 + line * 14) * width + x] = 20;
        var first =
                new ScoreNoteEvent(
                        0, (340f - 200) / 600, 4, 0, 1, 328f / height, false, 0, 0, 2, 1, 1, 0, 0);
        var second =
                new ScoreNoteEvent(
                        0, (430f - 200) / 600, 4, 0, 1, 328f / height, false, 0, 0, 2, 1, 1, 0, 0);
        var score =
                new ScorePageInterpretation(
                        List.of(new MeasureRegion(.2f, .8f, .43f, .64f)),
                        List.of(first, second),
                        1,
                        List.of(),
                        List.of(),
                        List.of());
        var result =
                ScoreExpressionDetector.apply(
                        score,
                        List.of(
                                new PlayingTechniqueDetector.Word(
                                        "dolce", .36f, .59f, .49f, .615f)),
                        List.of(new PlayingTechniqueDetector.Staff(300, 356, 14, 0, 1)),
                        gray,
                        width,
                        height);
        assertEquals(1, result.expressiveEvents().size());
        String target = result.expressiveEvents().get(0).targetEventId().orElseThrow();
        assertEquals(
                "expression-column:0:0:1:" + Float.floatToIntBits(first.positionInMeasure()),
                target);
        assertEquals(score.notes(), result.notes());
    }
}
