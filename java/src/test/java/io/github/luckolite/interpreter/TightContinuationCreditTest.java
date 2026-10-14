// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original split role/name boxes and independent pixel agreements. */
public class TightContinuationCreditTest {
    static ScoreCreditsDetector.Page page(String label, float staff, boolean photo) {
        return new ScoreCreditsDetector.Page(
                1800,
                2400,
                staff,
                List.of(
                        new ScoreCreditsDetector.Line("Harbor Waltz", 500, 70, 1300, 140),
                        new ScoreCreditsDetector.Line(label, 1260, 170, 1600, 200),
                        new ScoreCreditsDetector.Line("Morga Vale", 1270, 205, 1640, 235)),
                false,
                photo);
    }

    static List<Float> high = List.of(.995f, .996f, .997f);

    static List<ScoreCreditsDetector.Line> merge(
            ScoreCreditsDetector.Page p, String value, List<Float> confidence) {
        return ScoreCreditsDetector.mergeTightCreditRepairConsensus(
                p, p.lines().get(2), List.of(value, value, value), confidence);
    }

    @Test
    public void composerContinuationRepairs() {
        assertEquals(
                "Morgan Vale",
                merge(page("Music by", 400, false), "Morgan Vale", high).get(2).text());
    }

    @Test
    public void lyricContinuationRepairs() {
        assertEquals(
                "Morgan Vale",
                merge(page("Lyrics by", 400, false), "Morgan Vale", high).get(2).text());
    }

    @Test
    public void arrangerContinuationRepairs() {
        assertEquals(
                "Morgan Vale",
                merge(page("Arranged by", 400, false), "Morgan Vale", high).get(2).text());
    }

    @Test
    public void missingStaffStillUsesExplicitLabel() {
        assertEquals(
                "Morgan Vale",
                merge(page("Lyrics by", -1, false), "Morgan Vale", high).get(2).text());
    }

    @Test
    public void unrelatedLabelCannotEnableRepair() {
        var p = page("Notes from", 400, false);
        assertEquals(p.lines(), merge(p, "Morgan Vale", high));
    }

    @Test
    public void photographDoesNotEnableRepair() {
        var p = page("Lyrics by", -1, true);
        assertEquals(p.lines(), merge(p, "Morgan Vale", high));
    }

    @Test
    public void lowConfidenceRejected() {
        var p = page("Music by", 400, false);
        assertEquals(p.lines(), merge(p, "Morgan Vale", List.of(.99f, .97f, .99f)));
    }

    @Test
    public void conflictingReadingRejected() {
        var p = page("Music by", 400, false);
        assertEquals(
                p.lines(),
                ScoreCreditsDetector.mergeTightCreditRepairConsensus(
                        p,
                        p.lines().get(2),
                        List.of("Morgan Vale", "Morgan Vale", "Morga Vale"),
                        high));
    }

    @Test
    public void newContributorCannotBeInserted() {
        var p = page("Music by", 400, false);
        assertEquals(p.lines(), merge(p, "Morgan Vale and Casey Brook", high));
    }

    @Test
    public void identifierCannotBeInserted() {
        var p = page("Music by", 400, false);
        assertEquals(p.lines(), merge(p, "Morgan Vale 2", high));
    }
}
