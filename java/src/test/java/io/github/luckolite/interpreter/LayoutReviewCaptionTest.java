// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original layout controls for captions that cannot name contributors. */
public class LayoutReviewCaptionTest {
    static ScoreCreditsDetector.Line l(String s, float x, float y, float w, float h) {
        return new ScoreCreditsDetector.Line(s, x, y, x + w, y + h);
    }

    static boolean caption(
            ScoreCreditsDetector.Line target, float staff, ScoreCreditsDetector.Line... neighbors)
            throws Exception {
        var heading = l("Harbor Songs", 500, 80, 700, 80);
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(heading);
        lines.add(target);
        lines.addAll(List.of(neighbors));
        var p = new ScoreCreditsDetector.Page(1800, 2400, staff, lines, false, false);
        var method =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "layoutReviewCaption",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, p, heading, target, lines);
    }

    @Test
    public void incompleteThemeCaption() throws Exception {
        assertTrue(caption(l("THEME FROM", 600, 200, 250, 30), -1));
    }

    @Test
    public void completeArtistNamePreserved() throws Exception {
        assertFalse(caption(l("Theme Park", 600, 200, 250, 30), -1));
    }

    @Test
    public void selectionOcrCaption() throws Exception {
        assertTrue(caption(l("SELFCTIONS FROM", 600, 200, 250, 30), -1));
    }

    @Test
    public void clickAtStaff() throws Exception {
        assertTrue(caption(l("Click", 300, 365, 60, 25), 400));
    }

    @Test
    public void clickAwayFromStaffPreserved() throws Exception {
        assertFalse(caption(l("Click", 300, 200, 60, 25), 900));
    }

    @Test
    public void playButtonAtStaff() throws Exception {
        assertTrue(caption(l("S Play", 1300, 395, 100, 25), 400));
    }

    @Test
    public void bareRecordsPreserved() throws Exception {
        assertFalse(caption(l("REÇORDS", 900, 320, 150, 40), -1));
    }

    @Test
    public void duplicateRecordLabelSuppressed() throws Exception {
        assertTrue(
                caption(
                        l("REÇORDS", 900, 320, 150, 40),
                        -1,
                        l("Recorded by Casey Vale on NORTH RECORDS", 100, 325, 950, 30)));
    }

    @Test
    public void presentationLink() throws Exception {
        assertTrue(
                caption(
                        l("present", 760, 460, 230, 45),
                        800,
                        l("VALE & BROOK", 450, 400, 850, 45)));
    }

    @Test
    public void presentWithoutContextPreserved() throws Exception {
        assertFalse(caption(l("present", 760, 460, 230, 45), 800));
    }

    @Test
    public void guitarTabInstrument() throws Exception {
        assertTrue(
                caption(
                        l("S-Gt", 100, 350, 100, 35),
                        -1,
                        l("T", 340, 290, 30, 40),
                        l("A", 340, 340, 30, 40),
                        l("B", 340, 390, 30, 40)));
    }

    @Test
    public void guitarWithoutTabPreserved() throws Exception {
        assertFalse(caption(l("S-Gt", 100, 350, 100, 35), -1));
    }

    @Test
    public void publisherAdaptation() throws Exception {
        assertTrue(caption(l("Adp. from Example Co., Inc.", 120, 310, 350, 20), 450));
    }

    @Test
    public void publisherSeries() throws Exception {
        assertTrue(
                caption(
                        l("Festival Series of Organ Compositions", 120, 335, 350, 20),
                        450,
                        l("Adp. from Example Co., Inc.", 120, 310, 350, 20)));
    }

    @Test
    public void personalAdaptationPreserved() throws Exception {
        assertFalse(caption(l("Adapted from Morgan Vale", 120, 310, 350, 20), 450));
    }

    @Test
    public void seriesWithoutPublisherPreserved() throws Exception {
        assertFalse(caption(l("Festival Series of Organ Compositions", 120, 335, 350, 20), 450));
    }
}
