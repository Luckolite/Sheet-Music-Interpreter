// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original long finite beam bodies with independent rule and image-boundary controls. */
public final class LongFiniteBeamBudgetTest {
    private static final class Page {
        final int gap, width, height, first, last;
        final byte[] gray, labels;

        Page(int gap) {
            this.gap = gap;
            width = gap * 100;
            height = gap * 14;
            gray = new byte[width * height];
            labels = new byte[gray.length];
            Arrays.fill(gray, (byte) 255);
            for (int y = 4 * gap; y <= 8 * gap; y += gap) rect(0, width - 1, y, y + 1, 4);
            first = Math.round(4 * gap - gap * .32f);
            last = 4 * gap + 1;
            rect(gap * 6, gap * 94, first, last, 5);
        }

        void rect(int left, int right, int top, int bottom, int label) {
            for (int y = top; y <= bottom; y++)
                for (int x = left; x <= right; x++) {
                    gray[y * width + x] = 0;
                    labels[y * width + x] = (byte) label;
                }
        }

        int count(int x) throws Exception {
            var staff = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
            var constructor = staff.getDeclaredConstructor(float.class, float.class, float.class);
            constructor.setAccessible(true);
            var method =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "thickNonHeadBands",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            staff);
            method.setAccessible(true);
            return (int)
                    method.invoke(
                            null,
                            gray,
                            labels,
                            width,
                            height,
                            x,
                            first - gap,
                            last + gap,
                            constructor.newInstance(4f * gap, 8f * gap, (float) gap));
        }
    }

    @Test
    public void nearBothEndsAndCenterRetainOneLongBeamAtSeveralStaffScales() throws Exception {
        for (int gap : new int[] {10, 16, 24}) {
            var page = new Page(gap);
            for (int position : new int[] {13, 50, 87})
                assertEquals("gap=" + gap + " position=" + position, 1, page.count(position * gap));
        }
    }

    @Test
    public void aFullWidthThickRuleStillHasNoFiniteBeamEnds() throws Exception {
        var page = new Page(16);
        page.rect(0, page.width - 1, page.first, page.last, 4);
        assertEquals(0, page.count(50 * page.gap));
    }

    @Test
    public void oneVisibleEndRemainsInsufficient() throws Exception {
        var page = new Page(16);
        page.rect(0, page.gap * 6, page.first, page.last, 4);
        assertEquals(0, page.count(50 * page.gap));
    }

    @Test
    public void blankPaperAfterBothEndsCannotProvideRuleWitnesses() throws Exception {
        var page = new Page(16);
        for (int y = 4 * page.gap; y <= 4 * page.gap + 1; y++)
            for (int x = 0; x < page.width; x++)
                if (x < page.gap * 6 || x > page.gap * 94)
                    page.gray[y * page.width + x] = (byte) 255;
        assertEquals(0, page.count(50 * page.gap));
    }

    @Test
    public void sourcePixelsArePreserved() throws Exception {
        var page = new Page(16);
        var gray = page.gray.clone();
        var labels = page.labels.clone();
        page.count(50 * page.gap);
        assertArrayEquals(gray, page.gray);
        assertArrayEquals(labels, page.labels);
    }
}
