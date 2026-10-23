// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Procedural original ink only; validates crop containment without OCR readings. */
public final class PrintedSystemNumberOcrCropRepairTest {
    private record Page(int width, int height, byte[] labels, byte[] gray) {}

    private static Page page() {
        int w = 680, h = 520;
        byte[] labels = new byte[w * h], gray = new byte[w * h];
        Arrays.fill(gray, (byte) 185);
        for (int line = 0; line < 5; line++)
            for (int x = 45; x < w - 35; x++) {
                int y = Math.round(160 + line * 10 + .061f * (x - w * .5f));
                labels[y * w + x] = 4;
                gray[y * w + x] = 40;
            }
        return new Page(w, h, labels, gray);
    }

    private static List<MeasureRegion> measures() {
        return List.of(
                new MeasureRegion(.3f, .54f, .25f, .42f),
                new MeasureRegion(.55f, .88f, .25f, .42f));
    }

    private static PrintedSystemNumberOcr.Crop crop(Page p) {
        return PrintedSystemNumberOcr.candidates(p.labels, p.gray, p.width, p.height, measures())
                .get(0);
    }

    private static void seven(Page p, int x, int y) {
        // 12x18 single connected glyph-like component, dark raw ink semantics.
        for (int dx = 0; dx < 12; dx++)
            for (int t = 0; t < 2; t++) p.gray[(y + t) * p.width + x + dx] = 40;
        for (int i = 0; i < 15; i++)
            for (int t = 0; t < 2; t++) p.gray[(y + 2 + i) * p.width + x + 10 - i / 2 + t] = 40;
    }

    private static void inkBox(Page p, int x, int y, int width, int height) {
        for (int dy = 0; dy < height; dy++)
            for (int dx = 0; dx < width; dx++) p.gray[(y + dy) * p.width + x + dx] = 40;
    }

    @Test
    public void extendsOnlyToCompleteCrossingGlyphBoxWithMargin() {
        Page p = page();
        var before = crop(p);
        int inkLeft = before.left() - 5, inkTop = before.top() + 8;
        seven(p, inkLeft, inkTop);
        var after = crop(p);
        assertEquals(inkLeft - 1, after.left());
        assertEquals(before.top(), after.top());
        assertEquals(before.right(), after.right());
        assertEquals(before.bottom(), after.bottom());
        assertTrue(
                before.left()
                        > inkLeft); // Baseline clipped five columns of the physical component.
    }

    @Test
    public void componentWhollyInsideCropDoesNotChangeBounds() {
        Page p = page();
        var before = crop(p);
        seven(p, before.left() + 4, before.top() + 8);
        assertEquals(before, crop(p));
    }

    @Test
    public void giantBoundaryConnectedInkIsRejected() {
        Page p = page();
        var before = crop(p);
        int x = before.left() - 5;
        for (int y = before.top() + 2; y < before.bottom() - 2; y++)
            for (int dx = 0; dx < 25; dx++) p.gray[y * p.width + x + dx] = 40;
        assertEquals(before, crop(p));
    }

    @Test
    public void clippedVerticalRuleTouchingTopAndBottomIsRejected() {
        Page p = page();
        var before = crop(p);
        int x = before.left() - 1;
        for (int y = before.top(); y < before.bottom(); y++)
            for (int dx = 0; dx < 3; dx++) p.gray[y * p.width + x + dx] = 40;
        assertEquals(before, crop(p));
    }

    @Test
    public void noncrossingHaloInkCannotExpandCrop() {
        Page p = page();
        var before = crop(p);
        seven(p, before.left() - 18, before.top() + 8);
        assertEquals(before, crop(p));
    }

    @Test
    public void twoCompleteCrossingComponentsCauseAbstention() {
        Page p = page();
        var before = crop(p);
        int x = before.left() - 4;
        inkBox(p, x, before.top() + 4, 8, 9);
        inkBox(p, x, before.top() + 18, 8, 9);
        assertEquals(before, crop(p));
    }

    @Test
    public void cropDiscoveryNeverChangesPixelPlanes() {
        Page p = page();
        byte[] labels = p.labels.clone();
        byte[] gray = p.gray.clone();
        var before = crop(p);
        seven(p, before.left() - 5, before.top() + 8);
        labels = p.labels.clone();
        gray = p.gray.clone();
        PrintedSystemNumberOcr.candidates(p.labels, p.gray, p.width, p.height, measures());
        assertArrayEquals(labels, p.labels);
        assertArrayEquals(gray, p.gray);
    }
}
