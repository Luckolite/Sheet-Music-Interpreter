// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectedEndingRoofTest {
    private List<PrintedEndingBrackets.Mark> read(
            boolean precedingRoof, int hookWidth, boolean selector) {
        byte[] gray = new byte[600 * 240];
        Arrays.fill(gray, (byte) 255);
        for (int y = 40; y < 42; y++)
            for (int x = precedingRoof ? 180 : 306; x < 552; x++) gray[y * 600 + x] = 0;
        for (int y = 40; y < 91; y++)
            for (int x = 306; x < 306 + hookWidth; x++) gray[y * 600 + x] = 0;
        return PrintedEndingBrackets.detect(
                gray,
                600,
                240,
                List.of(new PlayingTechniqueDetector.Staff(120, 168, 12, 0, 1)),
                List.of(
                        new MeasureRegion(.1f, .5f, .45f, .8f),
                        new MeasureRegion(.51f, .92f, .45f, .8f)),
                selector
                        ? List.of(
                                new PlayingTechniqueDetector.Word(
                                        "2.", 310 / 600f, 45 / 240f, 330 / 600f, 60 / 240f))
                        : List.of());
    }

    @Test
    public void ordinaryEndingRetained() {
        assertEquals(1, read(false, 2, true).size());
    }

    @Test
    public void connectedPriorRoofKeepsSecondEnding() {
        var marks = read(true, 2, true);
        assertEquals(1, marks.size());
        assertEquals(1, marks.get(0).start());
        assertEquals(2, marks.get(0).end());
        assertEquals(List.of(2), marks.get(0).passes());
    }

    @Test
    public void roofWithoutHookAbstains() {
        assertTrue(read(true, 0, true).isEmpty());
    }

    @Test
    public void hookWithoutSelectorAbstains() {
        assertTrue(read(true, 2, false).isEmpty());
    }

    @Test
    public void broadInteriorShapeAbstains() {
        assertTrue(read(true, 10, true).isEmpty());
    }
}
