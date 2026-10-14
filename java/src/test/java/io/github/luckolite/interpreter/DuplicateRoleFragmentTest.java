// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DuplicateRoleFragmentTest {
    static boolean duplicate(
            String fragment, String credit, float left, float top, float right, float bottom)
            throws Exception {
        var a = new ScoreCreditsDetector.Line(fragment, 1200, 200, 1700, 240);
        var b = new ScoreCreditsDetector.Line(credit, left, top, right, bottom);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "duplicateRoleFragment", ScoreCreditsDetector.Line.class, List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, a, List.of(a, b));
    }

    @Test
    public void suffixCreditOverlap() throws Exception {
        assertTrue(duplicate("*arranged", "抹茶arranged", 1201, 201, 1701, 240));
    }

    @Test
    public void prefixCreditOverlap() throws Exception {
        assertTrue(duplicate("*arranged", "Arranged by Morgan Vale", 1201, 201, 1701, 240));
    }

    @Test
    public void separateCreditRetained() throws Exception {
        assertFalse(duplicate("*arranged", "Arranged by Morgan Vale", 1200, 270, 1700, 310));
    }

    @Test
    public void smallIntersectionRetained() throws Exception {
        assertFalse(duplicate("*arranged", "Arranged by Morgan Vale", 1650, 200, 2150, 240));
    }

    @Test
    public void encompassingParagraphRetained() throws Exception {
        assertFalse(duplicate("*arranged", "Arranged by Morgan Vale", 100, 190, 1750, 250));
    }

    @Test
    public void unresolvedRoleRetained() throws Exception {
        assertFalse(duplicate("*arranged", "arranged", 1200, 200, 1700, 240));
    }

    @Test
    public void differentRoleRetained() throws Exception {
        assertFalse(duplicate("*arranged", "Composed by Morgan Vale", 1200, 200, 1700, 240));
    }

    @Test
    public void contributorNameRetained() throws Exception {
        assertFalse(duplicate("Morgan Vale", "Arranged by Morgan Vale", 1200, 200, 1700, 240));
    }
}
