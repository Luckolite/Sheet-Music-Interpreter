// Copyright 2026 Luckolite; SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;
import org.junit.Test;

/** Original source-free caller scenes; each precode oracle is asserted directly. */
public final class ContextualMfCallerOriginalTest {
    private static void check(
            String asset, boolean expected, String literal, int l, int t, int r, int b)
            throws Exception {
        byte[] g = ContextualMfResourceFixture.read("caller/" + asset), old = g.clone();
        var word =
                new PlayingTechniqueDetector.Word(literal, l / 700f, t / 420f, r / 700f, b / 420f);
        var owner = new PlayingTechniqueDetector.Staff(112, 176, 16, 0, 1);
        var regions = List.of(new MeasureRegion(0, 1, 0, 1));
        var notes =
                List.of(
                        new ScoreNoteEvent(
                                0,
                                330 / 700f,
                                4,
                                0,
                                1,
                                144 / 420f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1));
        var result =
                ScoreDynamicsDetector.detectDirectionCore(
                        List.of(word), List.of(owner), regions, notes, g, 700, 420, g, false);
        var matching =
                result.changes().stream()
                        .filter(z -> z.direction() == 0 && z.decibels() == 0)
                        .toList();
        assertArrayEquals("source plane", old, g);
        assertEquals(asset, expected, !matching.isEmpty());
        if (expected) {
            assertEquals("one literal mf", 1, matching.size());
            var v = matching.get(0);
            assertEquals(0, v.measureIndex());
            assertEquals(0, v.staffIndex());
            assertEquals(1, v.staffCount());
        }
    }

    @Test
    public void original01() throws Exception {
        check(
                "clean-context-complete-mf-imperfect-ocr-left.gray.gz",
                true,
                "mf",
                298,
                188,
                363,
                254);
    }

    @Test
    public void original02() throws Exception {
        check(
                "clean-context-complete-mf-imperfect-ocr-right.gray.gz",
                true,
                "mf",
                288,
                188,
                352,
                254);
    }

    @Test
    public void original03() throws Exception {
        check(
                "clean-context-complete-mf-imperfect-ocr-top.gray.gz",
                true,
                "mf",
                288,
                205,
                363,
                258);
    }

    @Test
    public void original04() throws Exception {
        check(
                "clean-context-complete-mf-imperfect-ocr-bottom.gray.gz",
                true,
                "mf",
                288,
                182,
                363,
                240);
    }

    @Test
    public void original05() throws Exception {
        check(
                "clean-context-physically-truncated-mezzo-no-completion.gray.gz",
                false,
                "mf",
                298,
                188,
                363,
                254);
    }

    @Test
    public void original06() throws Exception {
        check(
                "clean-context-complete-mf-hidden-foreign-branch.gray.gz",
                false,
                "mf",
                298,
                188,
                352,
                254);
    }

    @Test
    public void original07() throws Exception {
        check(
                "clean-context-complete-mf-light-foreign-corridor.gray.gz",
                false,
                "mf",
                298,
                188,
                352,
                254);
    }

    @Test
    public void original08() throws Exception {
        check(
                "clean-context-supplied-mf-outside-independent-foreground.gray.gz",
                false,
                "mf",
                298,
                188,
                352,
                254);
    }

    @Test
    public void original09() throws Exception {
        check("clean-context-complete-mf-tall-whitepaper.gray.gz", true, "mf", 288, 196, 363, 252);
    }

    @Test
    public void original10() throws Exception {
        check(
                "clean-context-complete-mf-edge-unknown-fringe.gray.gz",
                true,
                "mf",
                288,
                196,
                363,
                252);
    }

    @Test
    public void original11() throws Exception {
        check("clean-context-supplied-f-not-mezzo-forte.gray.gz", false, "f", 288, 196, 363, 252);
    }

    @Test
    public void original12() throws Exception {
        check("clean-context-supplied-mf-only-f-glyph.gray.gz", false, "mf", 288, 196, 363, 252);
    }
}
