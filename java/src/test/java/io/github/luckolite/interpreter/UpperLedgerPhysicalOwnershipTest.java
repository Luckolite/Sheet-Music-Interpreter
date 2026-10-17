// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original closed/partial bodies and independent neighboring notes; no score material. */
public class UpperLedgerPhysicalOwnershipTest {
    static List<ScoreNoteEvent> notes(UpperLedgerAdversarialProbe.Page p) {
        byte[] g = p.g.clone(), l = p.l.clone();
        var ns =
                OmrScoreInterpreter.extract(
                        p.l,
                        p.g,
                        640,
                        600,
                        List.of(
                                new MeasureRegion(.03f, .97f, .15f, .38f),
                                new MeasureRegion(.03f, .97f, .46f, .90f)));
        assertArrayEquals(g, p.g);
        assertArrayEquals(l, p.l);
        return ns;
    }

    static List<ScoreNoteEvent> body(UpperLedgerAdversarialProbe.Page p) {
        return notes(p).stream()
                .filter(n -> n.pageY() * 600 >= 367 && n.pageY() * 600 <= 383)
                .toList();
    }

    static void phases(boolean hollow) {
        for (int off = -2; off <= 3; off++) {
            var p = UpperLedgerOwnershipChallenge.scene(hollow ? "hollow" : "filled");
            for (int y = 368; y <= 382; y++)
                for (int x = 139; x <= 161; x++) {
                    double q = (x - 150) * (x - 150) / 121. + (y - 375) * (y - 375) / 49.;
                    if (q <= 1) p.l[y * 640 + x] = (byte) (y == 375 + off ? 0 : 2);
                }
            var ns = body(p);
            assertEquals("one physical oval with off-center semantic cut=" + off, 1, ns.size());
            assertEquals(
                    0,
                    ns.get(0)
                            .staffIndex()); /* Cut -2 has an inherited one-step pitch error; this test asserts single physical/staff ownership only. */
            assertEquals(hollow ? 2 : 1, ns.get(0).unbeamedDurationBeats(), 0);
        }
    }

    @Test
    public void filledOffsetsKeepOneOwner() {
        phases(false);
    }

    @Test
    public void hollowOffsetsKeepOneOwner() {
        phases(true);
    }

    @Test
    public void brokenLeftContourDoesNotCreateAnotherOwner() {
        var p = UpperLedgerOwnershipChallenge.scene("hollow");
        for (int y = 371; y <= 373; y++)
            for (int x = 137; x <= 143; x++) p.g[y * 640 + x] = (byte) 255;
        assertEquals(1, body(p).size());
    }

    @Test
    public void brokenRightContourDoesNotCreateAnotherOwner() {
        var p = UpperLedgerOwnershipChallenge.scene("hollow");
        for (int y = 371; y <= 373; y++)
            for (int x = 158; x <= 163; x++) p.g[y * 640 + x] = (byte) 255;
        assertEquals(1, body(p).size());
    }

    @Test
    public void missingSemanticRuleDoesNotDuplicatePrintedBody() {
        var p = UpperLedgerOwnershipChallenge.scene("hollow");
        for (int x = 20; x <= 620; x++) p.l[375 * 640 + x] = 0;
        assertEquals(1, body(p).size());
    }

    @Test
    public void intactNeighborRetainsItsActualStaffAndPitch() {
        var p = new UpperLedgerAdversarialProbe.Page("neighbor");
        p.head(150, 375);
        var ns = body(p);
        assertEquals(1, ns.size());
        assertEquals(0, ns.get(0).staffIndex());
        assertEquals(0, ns.get(0).staffStep());
        assertEquals(.625, ns.get(0).pageY(), .000001);
    }

    @Test
    public void neighboringIndependentWholeBodiesKeepBothPitchOwners() {
        var p = new UpperLedgerAdversarialProbe.Page("neighbor");
        p.head(150, 375);
        var ns =
                notes(p).stream()
                        .filter(n -> n.pageY() * 600 >= 367 && n.pageY() * 600 <= 407)
                        .toList();
        assertEquals(2, ns.size());
        var a = ns.stream().filter(n -> n.pageY() < .64).findFirst().orElseThrow();
        var b = ns.stream().filter(n -> n.pageY() > .64).findFirst().orElseThrow();
        assertEquals(0, a.staffIndex());
        assertEquals(0, a.staffStep());
        assertEquals(1, b.staffIndex());
        assertEquals(15, b.staffStep());
    }

    @Test
    public void plusWithoutOvalIsNotAnotherUpperNote() {
        assertTrue(body(new UpperLedgerAdversarialProbe.Page("glyphPlus")).isEmpty());
    }

    @Test
    public void boxCounterIsNotAnotherUpperNote() {
        assertTrue(body(new UpperLedgerAdversarialProbe.Page("glyphBox")).isEmpty());
    }

    @Test
    public void forkWithoutClosedBodyIsNotAnotherUpperNote() {
        assertTrue(body(new UpperLedgerAdversarialProbe.Page("glyphFork")).isEmpty());
    }

    @Test
    public void halfCounterCannotProveCompleteOwnBody() {
        var p = UpperLedgerOwnershipChallenge.scene("hollow");
        assertFalse(
                ClosedLedgerHeadBody.proved(
                        p.g, 640, 600, 140, 374, 160, 382, 16, new int[] {139, 479, 1}));
    }

    @Test
    public void twoPocketsNeedTheirIndependentPrintedRail() {
        var p = UpperLedgerOwnershipChallenge.scene("hollow");
        for (int x = 130; x <= 138; x++) p.g[375 * 640 + x] = (byte) 255;
        assertFalse(
                ClosedLedgerHeadBody.proved(
                        p.g, 640, 600, 139, 368, 161, 382, 16, new int[] {139, 479, 1}));
    }

    @Test
    public void malformedAndClippedBodyProofAbstains() {
        byte[] g = new byte[100];
        assertFalse(ClosedLedgerHeadBody.proved(null, 10, 10, 1, 1, 8, 8, 16, null));
        assertFalse(ClosedLedgerHeadBody.proved(g, 10, 10, 0, 1, 8, 8, 16, null));
        assertFalse(ClosedLedgerHeadBody.proved(g, 10, 10, 1, 1, 8, 8, Float.NaN, null));
    }

    @Test
    public void completeHollowBodyIncludesBothEnclosedPockets() {
        var p = UpperLedgerOwnershipChallenge.scene("hollow");
        assertTrue(
                ClosedLedgerHeadBody.proved(
                        p.g, 640, 600, 139, 368, 161, 382, 16, new int[] {139, 479, 1}));
    }

    @Test
    public void cornerInkNeedsItsIndependentOwnShaft() {
        var p = UpperLedgerOwnershipChallenge.scene("hollow");
        assertFalse(ClosedLedgerHeadBody.proved(p.g, 640, 600, 139, 368, 161, 382, 16, null));
    }

    @Test
    public void secondDarkCornerCannotSupplyRoundedBody() {
        var p = UpperLedgerOwnershipChallenge.scene("hollow");
        p.g[368 * 640 + 140] = 0;
        assertFalse(
                ClosedLedgerHeadBody.proved(
                        p.g, 640, 600, 139, 368, 161, 382, 16, new int[] {139, 479, 1}));
    }
}
