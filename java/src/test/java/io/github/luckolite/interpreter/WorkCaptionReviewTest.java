// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorkCaptionReviewTest {
    static ScoreCreditsDetector.Line l(String s, float x, float y, float w, float h) {
        return new ScoreCreditsDetector.Line(s, x, y, x + w, y + h);
    }

    static boolean caption(ScoreCreditsDetector.Line target, ScoreCreditsDetector.Line... extra)
            throws Exception {
        var heading = l("Harbor Waltz", 500, 80, 800, 80);
        var lines = new ArrayList<ScoreCreditsDetector.Line>(List.of(heading, target));
        lines.addAll(List.of(extra));
        var p = new ScoreCreditsDetector.Page(1800, 2400, 500, lines, false, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "layoutReviewCaption",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, heading, target, lines);
    }

    @Test
    public void quotedWorkWithOwnByline() throws Exception {
        assertTrue(
                caption(
                        l("\"A Quiet Morning\"", 1200, 260, 400, 30),
                        l("Written by Morgan Vale", 1200, 295, 400, 30)));
    }

    @Test
    public void quotedNameWithoutBylinePreserved() throws Exception {
        assertFalse(caption(l("\"The Wanderers\"", 1200, 260, 400, 30)));
    }

    @Test
    public void centeredMovieSubtitle() throws Exception {
        assertTrue(caption(l("Harbor: The Movie", 600, 190, 600, 40)));
    }

    @Test
    public void separateMovieNamedContributorPreserved() throws Exception {
        assertFalse(caption(l("Harbor: The Movie", 1300, 190, 400, 40)));
    }

    @Test
    public void prepareAtStaff() throws Exception {
        assertTrue(caption(l("Prepare", 200, 460, 120, 25)));
    }

    @Test
    public void prepareAwayFromStaffPreserved() throws Exception {
        assertFalse(caption(l("Prepare", 200, 200, 120, 25)));
    }

    @Test
    public void damagedSustainedInstructionAfterTempo() throws Exception {
        assertTrue(
                caption(
                        l("ently, sustained, as in distance", 220, 480, 450, 25),
                        l("Somberly (= 90)", 210, 450, 250, 25)));
    }

    @Test
    public void sustainedWithoutTempoPreserved() throws Exception {
        assertFalse(caption(l("ently, sustained, as in distance", 220, 480, 450, 25)));
    }

    @Test
    public void musicalPlayCaption() throws Exception {
        assertTrue(caption(l("IN A NEW MUSICAL P[AY", 500, 700, 800, 80)));
    }

    @Test
    public void musicalPersonPreserved() throws Exception {
        assertFalse(caption(l("Morgan Play", 500, 700, 800, 80)));
    }
}
