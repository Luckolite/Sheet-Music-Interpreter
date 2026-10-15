// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original fictional lettering; no private score material. */
public class LocatedCoverBoundaryRetentionTest {
    private final ScoreCreditsDetector.Line original =
            new ScoreCreditsDetector.Line("HARBOR SONG", 150, 700, 740, 840);
    private final ScoreCreditsDetector.Page page =
            new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(original), false, true);
    private final List<Float> high = List.of(.98f, .99f, .98f);

    private LocatedCoverHeadingEvidence narrow(String value) {
        return LocatedCoverHeadingEvidence.fromOriginal(
                page, original, List.of(value, value, value), high);
    }

    @Test
    public void leadingClippingRequiresExpandedPixels() {
        var cropped = narrow("ARBOR SONG");
        assertNotNull(cropped);
        assertTrue(LocatedCoverHeadingExpansion.needsRead(original, cropped));
        var retained =
                LocatedCoverHeadingExpansion.preserveOriginal(
                        page,
                        original,
                        cropped,
                        List.of("HARBOR SONG", "HARBOR SONG", "HARBOR SONG"),
                        high);
        assertNotNull(retained);
        assertEquals("HARBOR SONG", retained.confirmed().text());
        assertEquals(List.of(original), LocatedCoverHeadingEvidence.merge(page, List.of(retained)));
    }

    @Test
    public void trailingClippingAlsoRequiresExpansion() {
        assertTrue(LocatedCoverHeadingExpansion.needsRead(original, narrow("HARBOR SON")));
    }

    @Test
    public void internalCorrectionDoesNotNeedBoundaryFallback() {
        var damaged = new ScoreCreditsDetector.Line("HARBR SONG", 150, 700, 740, 840);
        var p = new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(damaged), false, true);
        var fixed =
                LocatedCoverHeadingEvidence.fromOriginal(
                        p, damaged, List.of("HARBOR SONG", "HARBOR SONG", "HARBOR SONG"), high);
        assertNotNull(fixed);
        assertFalse(LocatedCoverHeadingExpansion.needsRead(damaged, fixed));
    }

    @Test
    public void disagreementCannotPreserveOriginal() {
        assertNull(
                LocatedCoverHeadingExpansion.preserveOriginal(
                        page,
                        original,
                        narrow("ARBOR SONG"),
                        List.of("HARBOR SONG", "ARBOR SONG", "HARBOR SONG"),
                        high));
    }

    @Test
    public void weakExpandedReadCannotPreserveOriginal() {
        assertNull(
                LocatedCoverHeadingExpansion.preserveOriginal(
                        page,
                        original,
                        narrow("ARBOR SONG"),
                        List.of("HARBOR SONG", "HARBOR SONG", "HARBOR SONG"),
                        List.of(.99f, .94f, .99f)));
    }

    @Test
    public void expandedDifferentTitleCannotPreserveOriginal() {
        assertNull(
                LocatedCoverHeadingExpansion.preserveOriginal(
                        page,
                        original,
                        narrow("ARBOR SONG"),
                        List.of("HARBOR LONG", "HARBOR LONG", "HARBOR LONG"),
                        high));
    }

    @Test
    public void ordinaryPageCannotUsePhotoPreservation() {
        var p = new ScoreCreditsDetector.Page(1800, 1000, -1, List.of(original), false, false);
        assertNull(
                LocatedCoverHeadingExpansion.preserveOriginal(
                        p,
                        original,
                        narrow("ARBOR SONG"),
                        List.of("HARBOR SONG", "HARBOR SONG", "HARBOR SONG"),
                        high));
    }

    @Test
    public void neighboringTextBlocksExpansion() {
        var neighbor = new ScoreCreditsDetector.Line("OTHER", 120, 700, 149, 840);
        var p =
                new ScoreCreditsDetector.Page(
                        1800, 1000, -1, List.of(original, neighbor), false, true);
        assertNull(
                LocatedCoverHeadingExpansion.preserveOriginal(
                        p,
                        original,
                        narrow("ARBOR SONG"),
                        List.of("HARBOR SONG", "HARBOR SONG", "HARBOR SONG"),
                        high));
    }
}
