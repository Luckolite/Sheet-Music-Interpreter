// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original procedural faint opening and independently shifted regional frame. */
public class AgreedRegionalExtentTest {
    static final int W = 720, H = 480;

    record Page(byte[] labels, byte[] gray) {}

    static Page page(float slope, int rules, int tone, boolean texture, boolean fragmented) {
        return page(slope, rules, tone, texture, fragmented, 0);
    }

    static Page page(
            float slope, int rules, int tone, boolean texture, boolean fragmented, float curve) {
        byte[] g = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 55; x <= 675; x++) {
                int y = Math.round(100 + line * 12 + .02f * (x - W * .5f));
                g[y * W + x] = 40;
                labels[y * W + x] = 4;
                y =
                        Math.round(
                                300
                                        + line * 12
                                        + slope * (x - W * .5f)
                                        + curve * (x - W * .5f) * (x - W * .5f) / (W * W));
                if (x >= 240 || line < rules && (!fragmented || x % 20 < 15))
                    g[y * W + x] = (byte) (x < 240 ? tone : 40);
                labels[(300 + line * 12) * W + x] = 4;
            }
        // Semantic-only vertical confusion joins the five-rule envelope; printed
        // evidence must still independently support any regional replacement.
        for (int y = 300; y <= 348; y++) for (int x = 600; x <= 615; x++) labels[y * W + x] = 4;
        if (texture)
            for (int y = 292; y <= 356; y++)
                for (int x = 55; x < 240; x++) g[y * W + x] = (byte) 210;
        for (int x : new int[] {55, 350, 675})
            for (int dy = 0; dy <= 48; dy++) {
                int y =
                        Math.round(
                                300
                                        + dy
                                        + slope * (x - W * .5f)
                                        + curve * (x - W * .5f) * (x - W * .5f) / (W * W));
                g[y * W + x] = 40;
                labels[y * W + x] = 1;
            }
        for (int cx : new int[] {150, 400})
            for (int y = 319; y <= 324; y++)
                for (int x = cx - 5; x <= cx + 5; x++) {
                    g[y * W + x] = 0;
                    labels[y * W + x] = 2;
                }
        return new Page(labels, g);
    }

    static List<MeasureRegion> read(Page p) {
        return OmrMeasurePostProcessor.process(p.labels, p.gray, W, H).stream()
                .filter(m -> m.top() > .5f)
                .toList();
    }

    static void retained(Page p) {
        var m = read(p);
        assertFalse(m.isEmpty());
        assertTrue(m.toString(), m.get(0).left() * W < 145);
        assertTrue(m.get(0).right() * W > 330);
        assertTrue(m.stream().anyMatch(a -> a.left() * W > 350));
    }

    static void unchangedSemantic(Page p) {
        var m = read(p);
        assertEquals(2, m.size());
        assertEquals(71, m.get(0).left() * W, .001);
        assertEquals(343, m.get(0).right() * W, .001);
        assertEquals(357, m.get(1).left() * W, .001);
        assertEquals(668, m.get(1).right() * W, .001);
    }

    @Test
    public void faintFiveRuleOpeningSurvivesRegionalReplacement() {
        retained(page(.003f, 5, 210, false, false));
    }

    @Test
    public void nearbySlopeAlsoKeepsItsPrintedOpening() {
        retained(page(.006f, 5, 210, false, false));
    }

    @Test
    public void veryPaleCompleteOpeningRemainsPlayable() {
        retained(page(.003f, 5, 235, false, false));
    }

    @Test
    public void fourRulesKeepExistingSemanticGeometry() {
        unchangedSemantic(page(.003f, 4, 210, false, false));
    }

    @Test
    public void semanticOnlyOpeningKeepsExistingGeometry() {
        unchangedSemantic(page(.003f, 0, 210, false, false));
    }

    @Test
    public void denseTextureKeepsExistingGeometry() {
        unchangedSemantic(page(.003f, 5, 210, true, false));
    }

    @Test
    public void shortDisconnectedMarksKeepExistingGeometry() {
        unchangedSemantic(page(.003f, 5, 210, false, true));
    }

    @Test
    public void sourcePacketsRemainUnchanged() {
        var p = page(.003f, 5, 210, false, false);
        var l = p.labels.clone();
        var g = p.gray.clone();
        read(p);
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }

    @Test
    public void upwardCurvedFaintOpeningKeepsNotes() {
        retained(page(.018f, 5, 210, false, false, 24));
    }

    @Test
    public void downwardCurvedFaintOpeningKeepsNotes() {
        retained(page(-.018f, 5, 210, false, false, -24));
    }
}
