// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original API ownership amendments, using the independently procedural header fixture. */
public class OpeningPartTempoAmendmentTest {
    static void laterEquation(OpeningPartTempoTest.Page p, int value, int x, int y) {
        p.equation(value, x, y);
        p.erase(x - 42, y - 14, 18, 31);
        p.box(x - 30, y - 12, 2, 26);
        for (int yy = y + 7; yy <= y + 13; yy++)
            for (int xx = x - 39; xx <= x - 29; xx++)
                if (Math.pow((xx - (x - 34)) / 5d, 2) + Math.pow((yy - (y + 10)) / 3d, 2) <= 1)
                    p.g[yy * 480 + xx] = 0;
    }

    static OpeningPartTempoTest.Page withLater() {
        var p = new OpeningPartTempoTest.Page();
        laterEquation(p, 110, 340, 233);
        return p;
    }

    static void unordered(OpeningPartTempoTest.Page p) {
        for (int y = 410; y <= 458; y += 12) p.box(30, y, 420, 1);
        var opening = p.measures.get(0);
        p.measures =
                new ArrayList<>(
                        List.of(
                                new MeasureRegion(180f / 480, 450f / 480, 398f / 500, 470f / 500),
                                opening));
    }

    static List<ScoreTempoChange> ordinary(OpeningPartTempoTest.Page p) {
        return TempoChangeDetector.detect(p.tokens, p.g, 480, 500, p.measures);
    }

    static void openingAndLater(OpeningPartTempoTest.Page p, int owner) {
        var old = ordinary(p);
        assertEquals(1, old.size());
        assertTrue(old.get(0).positionInMeasure() > 0);
        var expected = new ArrayList<>(old);
        expected.add(new ScoreTempoChange(owner, 0, 96));
        expected.sort(
                Comparator.comparingInt(ScoreTempoChange::measureIndex)
                        .thenComparingDouble(ScoreTempoChange::positionInMeasure));
        assertEquals(expected, p.read());
    }

    @Test
    public void laterFirstBarChangeDoesNotSuppressOpeningContext() {
        openingAndLater(withLater(), 0);
    }

    @Test
    public void laterFirstBarChangeUsesPhysicalOpeningOnUnorderedInput() {
        var p = withLater();
        unordered(p);
        openingAndLater(p, 1);
    }

    @Test
    public void unrelatedProseStillBlocksDespiteOwnedLaterEquation() {
        var p = withLater();
        p.word("Example", 375, 180, 440, 196);
        assertEquals(ordinary(p), p.read());
    }

    @Test
    public void anotherUnownedHighEquationStillBlocksDespiteOwnedLater() {
        var p = withLater();
        p.equation(90, 340, 110);
        assertEquals(ordinary(p), p.read());
    }

    @Test
    public void missingLaterBeatGlyphCannotWaiveInterveningEquation() {
        var p = withLater();
        p.erase(298, 219, 16, 34);
        assertFalse(p.read().stream().anyMatch(e -> e.bpm() == 96));
        assertEquals(ordinary(p), p.read());
    }

    @Test
    public void existingOpeningOnUnorderedInputIsNotDuplicated() {
        var p = new OpeningPartTempoTest.Page();
        p.erase(178, 96, 60, 34);
        p.tokens.clear();
        p.words.remove(3);
        p.equation(96, 220, 160);
        unordered(p);
        var expected = ordinary(p);
        assertEquals(1, expected.size());
        assertEquals(1, expected.get(0).measureIndex());
        assertEquals(0f, expected.get(0).positionInMeasure(), 0);
        assertEquals(expected, p.read());
    }

    @Test
    public void duplicateInputDoesNotDuplicateUnorderedExistingOpening() {
        var p = new OpeningPartTempoTest.Page();
        p.erase(178, 96, 60, 34);
        p.tokens.clear();
        p.words.remove(3);
        p.equation(96, 220, 160);
        p.tokens.add(p.tokens.get(0));
        p.words.add(p.words.get(3));
        unordered(p);
        assertEquals(ordinary(p), p.read());
        assertEquals(1, p.read().size());
    }

    @Test
    public void laterPhysicalSystemAtIndexZeroDoesNotSuppressOpening() {
        var p = new OpeningPartTempoTest.Page();
        unordered(p);
        p.equation(110, 220, 350);
        var old = ordinary(p);
        assertEquals(List.of(new ScoreTempoChange(0, 0, 110)), old);
        assertEquals(List.of(old.get(0), new ScoreTempoChange(1, 0, 96)), p.read());
    }
}
