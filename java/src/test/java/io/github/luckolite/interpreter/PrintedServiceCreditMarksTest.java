// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original fictional bylines, marks and person initials. */
public class PrintedServiceCreditMarksTest {
    private static ScoreCreditsDetector.Line line(String s, float l, float t, float r, float b) {
        return new ScoreCreditsDetector.Line(s, l, t, r, b);
    }

    private static final ScoreCreditsDetector.Line CREDIT =
            line("Transcribed by f NacreNotation", 1180, 300, 1690, 344);

    private static ScoreCreditsDetector.Page page(ScoreCreditsDetector.Line credit) {
        return new ScoreCreditsDetector.Page(
                1800, 2500, 500, List.of(line("Paper Lantern Waltz", 610, 70, 1190, 130), credit));
    }

    private static List<ScoreCreditsDetector.Line> words() {
        return List.of(
                line("Transcribed", 1180, 300, 1370, 340),
                line("by", 1378, 302, 1416, 340),
                line("f", 1440, 302, 1456, 340),
                line("NacreNotation", 1478, 302, 1690, 344));
    }

    private static int[] blank(int w, int h) {
        int[] p = new int[w * h];
        Arrays.fill(p, 0xffffffff);
        return p;
    }

    private static void rect(int[] p, int w, int l, int t, int r, int b) {
        for (int y = t; y < b; y++) for (int x = l; x < r; x++) p[y * w + x] = 0xff000000;
    }

    private static int[] wave(int w, int h) {
        int[] p = blank(w, h);
        if (w != 60) return p;
        rect(p, w, 4, 23, 9, 31);
        rect(p, w, 15, 16, 20, 38);
        rect(p, w, 26, 6, 34, 48);
        rect(p, w, 40, 16, 45, 38);
        rect(p, w, 51, 23, 56, 31);
        return p;
    }

    @Test
    public void actualGapSelectionRequiresWholeDetachedMark() {
        var candidates = PrintedServiceCreditMarks.candidates(page(CREDIT), words());
        assertEquals(2, candidates.size());
        assertEquals(22, candidates.get(0).right() - candidates.get(0).left());
        assertEquals(60, candidates.get(1).right() - candidates.get(1).left());
        var calls = new ArrayList<String>();
        var retained =
                PrintedServiceCreditMarks.retain(
                        page(CREDIT),
                        words(),
                        (l, t, w, h) -> {
                            calls.add(l + "," + t + "," + w + "," + h);
                            return wave(w, h);
                        });
        assertEquals(List.of("1417,295,22,54", "1417,295,60,54"), calls);
        assertEquals("NacreNotation", retained.reviewCredits().get(0).value());
        assertTrue(retained.lines().contains(CREDIT));
        var result =
                ScoreCreditsDetector.detect("Paper Lantern Waltz", List.of(retained), Set.of());
        assertEquals("", result.arranger());
        assertEquals(List.of("NacreNotation"), result.unclassifiedCredits());
    }

    @Test
    public void serviceMarkNeedNotBecomeAnOcrLetter() {
        var credit = line("Transcribed by NacreNotation", 1180, 300, 1690, 344);
        var w = new ArrayList<>(words());
        w.remove(2);
        var retained =
                PrintedServiceCreditMarks.retain(page(credit), w, (l, t, cw, ch) -> wave(cw, ch));
        assertEquals("NacreNotation", retained.reviewCredits().get(0).value());
    }

    @Test
    public void noPixelProofLeavesPrintedBylineAndRoleUntouched() {
        var original = page(CREDIT);
        assertSame(
                original,
                PrintedServiceCreditMarks.retain(original, words(), (l, t, w, h) -> blank(w, h)));
        var result =
                ScoreCreditsDetector.detect("Paper Lantern Waltz", List.of(original), Set.of());
        assertEquals("f NacreNotation", result.arranger());
    }

    @Test
    public void aGenuineInitialRemainsPartOfThePersonName() {
        var credit = line("Transcribed by F. Rowan", 1180, 300, 1690, 344);
        var w =
                List.of(
                        words().get(0),
                        words().get(1),
                        line("F.", 1440, 302, 1456, 340),
                        line("Rowan", 1478, 302, 1690, 344));
        var retained =
                PrintedServiceCreditMarks.retain(
                        page(credit),
                        w,
                        (l, t, cw, ch) -> {
                            int[] p = blank(cw, ch);
                            if (cw == 60) {
                                rect(p, cw, 15, 6, 21, 48);
                                rect(p, cw, 15, 6, 45, 12);
                                rect(p, cw, 15, 24, 39, 30);
                            }
                            return p;
                        });
        assertTrue(retained.reviewCredits().isEmpty());
        assertEquals(
                "F. Rowan",
                ScoreCreditsDetector.detect("Paper Lantern Waltz", List.of(retained), Set.of())
                        .arranger());
    }

    @Test
    public void multiwordServicePayloadKeepsEveryPrintedWord() {
        var credit = line("Transcribed by f Nacre Notation", 1180, 300, 1690, 344);
        var w =
                List.of(
                        words().get(0),
                        words().get(1),
                        words().get(2),
                        line("Nacre", 1478, 302, 1570, 344),
                        line("Notation", 1580, 302, 1690, 344));
        var retained =
                PrintedServiceCreditMarks.retain(page(credit), w, (l, t, cw, ch) -> wave(cw, ch));
        assertEquals("Nacre Notation", retained.reviewCredits().get(0).value());
    }

    @Test
    public void unlabelledLogoCannotAssignOrRemoveAContributorRole() {
        var credit = line("NacreNotation", 1180, 300, 1690, 344);
        assertTrue(PrintedServiceCreditMarks.candidates(page(credit), words()).isEmpty());
    }

    @Test
    public void wordBoxesFromAnotherLineCannotAuthorizeTheMark() {
        var shifted =
                words().stream()
                        .map(
                                w ->
                                        line(
                                                w.text(),
                                                w.left(),
                                                w.top() + 150,
                                                w.right(),
                                                w.bottom() + 150))
                        .toList();
        assertTrue(PrintedServiceCreditMarks.candidates(page(CREDIT), shifted).isEmpty());
    }

    @Test
    public void anUnboundedGapCannotRequestRasterPixels() {
        var w =
                List.of(
                        words().get(0),
                        words().get(1),
                        line("NacreNotation", 1670, 302, 1690, 344));
        assertTrue(PrintedServiceCreditMarks.candidates(page(CREDIT), w).isEmpty());
    }

    @Test
    public void aCreditBelowTheStaffIsNotHeaderEvidence() {
        var p = new ScoreCreditsDetector.Page(1800, 2500, 200, List.of(CREDIT));
        assertTrue(PrintedServiceCreditMarks.candidates(p, words()).isEmpty());
    }

    @Test
    public void oldPageConstructorsRetainAnEmptyImmutableEvidenceList() {
        var p = page(CREDIT);
        assertEquals(List.of(), p.reviewCredits());
        assertThrows(
                UnsupportedOperationException.class,
                () -> p.reviewCredits().add(new ScoreCreditsDetector.ReviewCredit(CREDIT, "x")));
        assertEquals(
                List.of(),
                new ScoreCreditsDetector.Page(1800, 2500, 500, p.lines(), false, false, null)
                        .reviewCredits());
    }

    @Test
    public void staleEvidenceDoesNotOverrideAnotherCreditLine() {
        var person = line("Transcribed by Rowan Moss", 1180, 300, 1690, 344);
        var p =
                new ScoreCreditsDetector.Page(
                        1800,
                        2500,
                        500,
                        page(person).lines(),
                        false,
                        false,
                        List.of(new ScoreCreditsDetector.ReviewCredit(CREDIT, "NacreNotation")));
        assertEquals(
                "Rowan Moss",
                ScoreCreditsDetector.detect("Paper Lantern Waltz", List.of(p), Set.of())
                        .arranger());
    }

    @Test
    public void nonfiniteGeometryCannotRequestPixels() {
        var p = new ScoreCreditsDetector.Page(Float.NaN, 2500, 500, List.of(CREDIT));
        assertTrue(PrintedServiceCreditMarks.candidates(p, words()).isEmpty());
        p = page(line(CREDIT.text(), 1180, Float.NaN, 1690, 344));
        assertTrue(PrintedServiceCreditMarks.candidates(p, words()).isEmpty());
        var w = new ArrayList<>(words());
        w.set(1, line("by", 1378, 302, Float.POSITIVE_INFINITY, 340));
        assertTrue(PrintedServiceCreditMarks.candidates(page(CREDIT), w).isEmpty());
    }

    @Test
    public void forgedValueOnTheExactLineCannotOverrideThePrintedContributor() {
        var p =
                new ScoreCreditsDetector.Page(
                        1800,
                        2500,
                        500,
                        page(CREDIT).lines(),
                        false,
                        false,
                        List.of(new ScoreCreditsDetector.ReviewCredit(CREDIT, "Different Person")));
        var result = ScoreCreditsDetector.detect("Paper Lantern Waltz", List.of(p), Set.of());
        assertEquals("f NacreNotation", result.arranger());
        assertFalse(result.unclassifiedCredits().contains("Different Person"));
    }

    @Test
    public void staleWordPayloadCannotRequestPixelsForADifferentFinalLine() {
        var p = page(line("Transcribed by f Different Person", 1180, 300, 1690, 344));
        assertSame(
                p,
                PrintedServiceCreditMarks.retain(
                        p,
                        words(),
                        (l, t, w, h) -> {
                            fail("stale words must be rejected before pixel access");
                            return wave(w, h);
                        }));
    }

    @Test
    public void literalPayloadAllowsHorizontalSpaceAndCaseOnly() {
        assertTrue(
                PrintedServiceCreditMarks.isLiteralPayload(
                        line("Transcription\tby\u00a0f Nacre   Notation", 1180, 300, 1690, 344),
                        "NACRE Notation"));
        assertTrue(PrintedServiceCreditMarks.isLiteralPayload(CREDIT, "f NacreNotation"));
        assertFalse(PrintedServiceCreditMarks.isLiteralPayload(CREDIT, "Nacre"));
        assertFalse(PrintedServiceCreditMarks.isLiteralPayload(CREDIT, " NacreNotation"));
        assertFalse(
                PrintedServiceCreditMarks.isLiteralPayload(
                        line("Transcribed by extra f NacreNotation", 1180, 300, 1690, 344),
                        "NacreNotation"));
        assertFalse(
                PrintedServiceCreditMarks.isLiteralPayload(
                        line("Transcribed by\nNacreNotation", 1180, 300, 1690, 344),
                        "NacreNotation"));
    }
}
