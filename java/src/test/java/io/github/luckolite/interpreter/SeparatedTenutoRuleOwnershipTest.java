// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural finite-mark versus rule ownership oracles, authored before a repair. */
public class SeparatedTenutoRuleOwnershipTest {
    private static final int W = 1000, H = 400, GAP = 16, HEAD = 160, MARK = 192;
    private static final int[] FOUR = {220, 260, 300, 340};

    private static final class Page {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];
        final List<NoteArticulationDetector.Anchor> notes = new ArrayList<>();

        Page() {
            Arrays.fill(gray, (byte) 255);
        }

        void pixel(int x, int y, int tone, int role) {
            if (x < 0 || x >= W || y < 0 || y >= H) throw new AssertionError("fixture bounds");
            int at = y * W + x;
            gray[at] = (byte) Math.min(gray[at] & 255, tone);
            if (role != 0) labels[at] = (byte) role;
        }

        void box(int left, int top, int right, int bottom, int tone, int role) {
            for (int y = top; y <= bottom; y++)
                for (int x = left; x <= right; x++) pixel(x, y, tone, role);
        }

        void head(int x, int y, int staff) {
            notes.add(new NoteArticulationDetector.Anchor(x, y, GAP, staff));
            for (int dy = -6; dy <= 6; dy++)
                for (int dx = -9; dx <= 9; dx++)
                    if (dx * dx / 81.0 + dy * dy / 36.0 <= 1)
                        pixel(x + dx, y + dy, 0, OmrMeasurePostProcessor.NOTEHEAD);
        }

        void dash(int x, int y) {
            box(x - 10, y - 1, x + 10, y + 1, 0, OmrMeasurePostProcessor.SYMBOL);
        }

        void dot(int x, int y) {
            for (int dy = -3; dy <= 3; dy++)
                for (int dx = -3; dx <= 3; dx++)
                    if (dx * dx + dy * dy <= 12)
                        pixel(x + dx, y + dy, 0, OmrMeasurePostProcessor.SYMBOL);
        }

        void portato(int x) {
            head(x, HEAD, 0);
            dot(x, HEAD + 21);
            dash(x, MARK);
        }

        void fiveRails(int first, int tone, boolean dropout) {
            for (int n = 0; n < 5; n++)
                for (int x = 80; x <= 620; x++) {
                    if (dropout && x % 29 <= 1) continue;
                    pixel(x, first + n * GAP, tone, OmrMeasurePostProcessor.STAFF);
                }
        }

        int[] detect() {
            byte[] beforeGray = gray.clone(), beforeLabels = labels.clone();
            int[] result = NoteArticulationDetector.detect(labels, gray, W, H, notes);
            assertArrayEquals(beforeGray, gray);
            assertArrayEquals(beforeLabels, labels);
            return result;
        }
    }

    private static void each(int expected, int[] actual) {
        for (int mark : actual) assertEquals(expected, mark);
    }

    @Test
    public void separatedPortatoInteriorsRetainBothMarks() {
        Page p = new Page();
        for (int x : FOUR) p.portato(x);
        each(NoteArticulation.STACCATO | NoteArticulation.TENUTO, p.detect());
    }

    @Test
    public void separatedTenutosDoNotRequireDots() {
        Page p = new Page();
        for (int x : FOUR) {
            p.head(x, HEAD, 0);
            p.dash(x, MARK);
        }
        each(NoteArticulation.TENUTO, p.detect());
    }

    @Test
    public void continuousFiveRuleInkDoesNotBecomeTenuto() {
        Page p = new Page();
        p.head(260, HEAD, 0);
        p.fiveRails(MARK, 0, false);
        assertEquals(0, p.detect()[0] & NoteArticulation.TENUTO);
    }

    @Test
    public void faintContinuousRuleRejectsItsDarkCentralPatch() {
        Page p = new Page();
        p.head(260, HEAD, 0);
        p.fiveRails(MARK, 200, false);
        p.dash(260, MARK);
        assertEquals(0, p.detect()[0] & NoteArticulation.TENUTO);
    }

    @Test
    public void shortPaperDropoutsDoNotTurnStaffIntoMarks() {
        Page p = new Page();
        p.head(260, HEAD, 0);
        p.fiveRails(MARK, 200, true);
        p.dash(260, MARK);
        assertEquals(0, p.detect()[0] & NoteArticulation.TENUTO);
    }

    @Test
    public void unrelatedDotDoesNotGiveStaffASecondRole() {
        Page p = new Page();
        p.head(260, HEAD, 0);
        p.fiveRails(MARK, 200, false);
        p.dash(260, MARK);
        p.dot(271, HEAD + 21);
        assertEquals(0, p.detect()[0] & NoteArticulation.TENUTO);
    }

    @Test
    public void wordWithExtenderDashesIsNotARepeatedArticulation() {
        Page p = new Page();
        p.head(300, HEAD, 0);
        for (int x : new int[] {140, 156, 172}) {
            p.box(x, 185, x + 2, 199, 0, 0);
            p.box(x, 185, x + 10, 187, 0, 0);
            p.box(x, 191, x + 9, 193, 0, 0);
            p.box(x, 197, x + 10, 199, 0, 0);
        }
        for (int x : new int[] {220, 260, 300, 340, 380, 420}) p.dash(x, MARK);
        assertEquals(0, p.detect()[0] & NoteArticulation.TENUTO);
    }

    @Test
    public void foreignStaffAttacksCannotSupplyRepeatedMarkOwners() {
        Page p = new Page();
        p.head(220, HEAD, 0);
        for (int x : new int[] {220, 260, 300, 340, 380, 420}) {
            p.dash(x, MARK);
            p.head(x, 288, 1);
        }
        assertEquals(0, p.detect()[0] & NoteArticulation.TENUTO);
    }

    @Test
    public void finitePortatoAtOuterSideDoesNotNeedTwoNeighborRuns() {
        Page p = new Page();
        for (int x : new int[] {52, 92, 132, 172}) p.portato(x);
        each(NoteArticulation.STACCATO | NoteArticulation.TENUTO, p.detect());
    }

    @Test
    public void connectedBeamCornerCannotSupplyDetachedTenuto() {
        Page p = new Page();
        p.head(260, HEAD, 0);
        p.dash(260, MARK);
        p.box(270, HEAD - 40, 272, MARK, 0, OmrMeasurePostProcessor.STEM_OR_REST);
        p.box(270, HEAD - 40, 330, HEAD - 37, 0, OmrMeasurePostProcessor.STEM_OR_REST);
        assertEquals(0, p.detect()[0] & NoteArticulation.TENUTO);
    }

    @Test
    public void independentAboveAndBelowPortatosRetainOwnership() {
        Page p = new Page();
        p.head(220, HEAD, 0);
        p.dot(220, HEAD + 21);
        p.dash(220, MARK);
        p.head(380, HEAD, 0);
        p.dot(380, HEAD - 21);
        p.dash(380, HEAD - 32);
        each(NoteArticulation.STACCATO | NoteArticulation.TENUTO, p.detect());
    }
}
