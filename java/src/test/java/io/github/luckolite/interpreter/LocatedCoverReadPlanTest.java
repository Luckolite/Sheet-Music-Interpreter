// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original fictional headings; no library titles or artwork. */
public class LocatedCoverReadPlanTest {
    private final ScoreCreditsDetector.Line first =
            new ScoreCreditsDetector.Line("MOON", 120, 260, 780, 512);
    private final ScoreCreditsDetector.Line caption =
            new ScoreCreditsDetector.Line("IGHT SUITE", 153, 743, 741, 882);

    private ScoreCreditsDetector.Page page(boolean photo) {
        return new ScoreCreditsDetector.Page(1800, 1013, -1, List.of(first, caption), false, photo);
    }

    private List<ScoreCreditsDetector.Line> coarse() {
        return List.of(new ScoreCreditsDetector.Line("MOON", 118, 258, 782, 514), caption);
    }

    @Test
    public void photographicCoarseTitleCannotSkipUnresolvedCaption() {
        assertTrue(LocatedCoverHeadingEvidence.needsOriginalReads(page(true), coarse()));
    }

    @Test
    public void ordinarySuccessfulTitleKeepsExistingSkip() {
        assertFalse(LocatedCoverHeadingEvidence.needsOriginalReads(page(false), coarse()));
    }

    @Test
    public void unchangedTitleRetainsExistingReads() {
        assertTrue(
                LocatedCoverHeadingEvidence.needsOriginalReads(page(false), page(false).lines()));
    }

    @Test
    public void completeOriginalConsensusRepairsOnlyRetainedCaption() {
        var p = page(true);
        var proof =
                LocatedCoverHeadingExpansion.confirm(
                        p,
                        caption,
                        List.of("LIGHT SUITE", "LIGHT SUITE", "LIGHT SUITE"),
                        List.of(.99f, .99f, .99f));
        assertNotNull(proof);
        var merged = new ScoreCreditsDetector.Page(1800, 1013, -1, coarse(), false, true);
        var out = LocatedCoverHeadingEvidence.merge(merged, List.of(proof));
        assertEquals(coarse().get(0), out.get(0));
        assertEquals("LIGHT SUITE", out.get(1).text());
    }

    @Test
    public void separatelyChangedCaptionIsNotOverwritten() {
        var p = page(true);
        var proof =
                LocatedCoverHeadingExpansion.confirm(
                        p,
                        caption,
                        List.of("LIGHT SUITE", "LIGHT SUITE", "LIGHT SUITE"),
                        List.of(.99f, .99f, .99f));
        assertNotNull(proof);
        var different = new ScoreCreditsDetector.Line("NIGHT SUITE", 153, 743, 741, 882);
        var merged =
                new ScoreCreditsDetector.Page(
                        1800, 1013, -1, List.of(first, different), false, true);
        assertEquals(merged.lines(), LocatedCoverHeadingEvidence.merge(merged, List.of(proof)));
    }
}
