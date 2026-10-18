// Copyright 2026 Luckolite; SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;
import org.junit.Test;

/** Same original localization pixels, read bounds and role oracles. */
public final class ContextualMfLocalizationOriginalTest {
    private static void check(String asset, boolean expected, int l, int t, int r, int b)
            throws Exception {
        byte[] g = ContextualMfResourceFixture.read("localization/" + asset), before = g.clone();
        var owner = new PlayingTechniqueDetector.Staff(112, 176, 16, 0, 1);
        var staffs = List.of(owner);
        var ctx =
                DynamicNotationRawContext.capture(
                        g, 700, 420, staffs, List.of(new MeasureRegion(0, 1, 0, 1)), List.of());
        var known =
                ContextualMfBodyEvidence.captureNotation(ctx, g, 700, 420, staffs, owner, l, r, 16);
        var e =
                ContextualMfBodyEvidence.inspectLocalized("mf", g, 700, 420, l, t, r, b, 16, known)
                        .body();
        assertArrayEquals(before, g);
        assertEquals(asset + " reason=" + e.reason(), expected, e.supported());
    }

    @Test
    public void original01() throws Exception {
        check("complete-mf-imperfect-ocr-left.gray.gz", true, 298, 196, 363, 252);
    }

    @Test
    public void original02() throws Exception {
        check("complete-mf-imperfect-ocr-right.gray.gz", true, 288, 196, 352, 252);
    }

    @Test
    public void original03() throws Exception {
        check("complete-mf-imperfect-ocr-top.gray.gz", true, 288, 205, 363, 252);
    }

    @Test
    public void original04() throws Exception {
        check("complete-mf-imperfect-ocr-bottom.gray.gz", true, 288, 196, 363, 240);
    }

    @Test
    public void original05() throws Exception {
        check("physically-truncated-mezzo-no-completion.gray.gz", false, 298, 196, 363, 252);
    }

    @Test
    public void original06() throws Exception {
        check("complete-mf-hidden-foreign-branch.gray.gz", false, 298, 205, 352, 240);
    }

    @Test
    public void original07() throws Exception {
        check("complete-mf-light-foreign-corridor.gray.gz", false, 298, 205, 352, 240);
    }

    @Test
    public void original08() throws Exception {
        check("supplied-mf-outside-independent-foreground.gray.gz", false, 298, 205, 352, 240);
    }
}
