// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceWorkCaptionTest {
    static boolean work(String value, String source, String credit, boolean photo, float staff)
            throws Exception {
        var p =
                new ScoreCreditsDetector.Page(
                        1800,
                        2400,
                        staff,
                        List.of(
                                new ScoreCreditsDetector.Line(source, 600, 230, 1200, 255),
                                new ScoreCreditsDetector.Line(credit, 500, 270, 1400, 300)),
                        false,
                        photo);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "confirmedSourceWorkCaption", String.class, List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, value, List.of(p));
    }

    @Test
    public void explicitSourceAndComposer() throws Exception {
        assertTrue(
                work(
                        "SILVER MOUNTAIN",
                        "from SILVER MOUNTAIN",
                        "Music by Morgan Vale",
                        false,
                        450));
    }

    @Test
    public void JoinedOcrWords() throws Exception {
        assertTrue(
                work(
                        "THE SILVERMOUNTAIN",
                        "from THE SILVER MOUNTAIN",
                        "Music by Morgan Vale",
                        false,
                        450));
    }

    @Test
    public void unrelatedSourceRetained() throws Exception {
        assertFalse(
                work("GOLDEN RIVER", "from SILVER MOUNTAIN", "Music by Morgan Vale", false, 450));
    }

    @Test
    public void missingComposerRetained() throws Exception {
        assertFalse(work("SILVER MOUNTAIN", "from SILVER MOUNTAIN", "Morgan Vale", false, 450));
    }

    @Test
    public void sameComposerNameRetained() throws Exception {
        assertFalse(work("MORGAN VALE", "from MORGAN VALE", "Music by Morgan Vale", false, 450));
    }

    @Test
    public void bareSourceRetained() throws Exception {
        assertFalse(work("SILVER MOUNTAIN", "SILVER MOUNTAIN", "Music by Morgan Vale", false, 450));
    }

    @Test
    public void photoRetained() throws Exception {
        assertFalse(
                work("SILVER MOUNTAIN", "from SILVER MOUNTAIN", "Music by Morgan Vale", true, 450));
    }

    @Test
    public void noNotationRetained() throws Exception {
        assertFalse(
                work("SILVER MOUNTAIN", "from SILVER MOUNTAIN", "Music by Morgan Vale", false, -1));
    }
}
