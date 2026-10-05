// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pale printed marks and bounded scan-shade fragments. */
public class PrintedArticulationContrastTest {
    private static final int W = 960, H = 960;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];

    private void page(int paper) {
        Arrays.fill(gray, (byte) paper);
        Arrays.fill(labels, (byte) 0);
    }

    private void pixel(int x, int y, int tone) {
        gray[y * W + x] = (byte) tone;
        labels[y * W + x] = OmrMeasurePostProcessor.SYMBOL;
    }

    private void dot(int tone) {
        for (int y = 220; y < 224; y++) for (int x = 478; x < 483; x++) pixel(x, y, tone);
    }

    private void peak(int tone) {
        for (int x = 474; x <= 486; x++) {
            int y = 210 + (int) Math.round(Math.abs(x - 480) * 1.4);
            for (int d = -1; d <= 1; d++) pixel(x, y + d, tone);
        }
    }

    private int mark(boolean raw) {
        return NoteArticulationDetector.detect(
                labels,
                raw ? gray : null,
                W,
                H,
                List.of(new NoteArticulationDetector.Anchor(480, 260, 12, 0)))[0];
    }

    @Test
    public void compactShadePatchIsNotStaccato() {
        page(160);
        dot(148);
        assertEquals(0, mark(true));
    }

    @Test
    public void unevenGraySpeckIsNotStaccato() {
        page(180);
        dot(148);
        for (int y = 221; y < 224; y++)
            for (int x = 479; x < 482; x++) pixel(x, y, 110 + (x - 479) * 12 + (y - 221) * 8);
        assertEquals(0, mark(true));
    }

    @Test
    public void tinyShadePeakIsNotMarcato() {
        page(160);
        peak(148);
        assertEquals(0, mark(true));
    }

    @Test
    public void roundedUnevenShadePatchIsNotStaccato() {
        page(180);
        dot(148);
        pixel(480, 221, 110);
        pixel(481, 221, 120);
        pixel(480, 222, 130);
        assertEquals(0, mark(true));
    }

    @Test
    public void darkDotOnShadeRemainsStaccato() {
        page(180);
        dot(30);
        assertEquals(NoteArticulation.STACCATO, mark(true));
    }

    @Test
    public void uniformFaintDotOnShadeRemainsStaccato() {
        page(180);
        dot(130);
        assertEquals(NoteArticulation.STACCATO, mark(true));
    }

    @Test
    public void uniformFaintDotOnWhiteRemainsStaccato() {
        page(255);
        dot(145);
        assertEquals(NoteArticulation.STACCATO, mark(true));
    }

    @Test
    public void darkPeakOnShadeRemainsMarcato() {
        page(180);
        peak(30);
        assertEquals(NoteArticulation.MARCATO, mark(true));
    }

    @Test
    public void uniformFaintPeakOnShadeRemainsMarcato() {
        page(180);
        peak(130);
        assertEquals(NoteArticulation.MARCATO, mark(true));
    }

    @Test
    public void uniformFaintPeakOnWhiteRemainsMarcato() {
        page(255);
        peak(145);
        assertEquals(NoteArticulation.MARCATO, mark(true));
    }

    @Test
    public void grayscaleOmissionKeepsSemanticFallback() {
        page(160);
        dot(148);
        assertEquals(NoteArticulation.STACCATO, mark(false));
    }

    @Test
    public void pagesAndLabelsStayUnchanged() {
        page(180);
        dot(30);
        byte[] g = gray.clone(), l = labels.clone();
        mark(true);
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    @Test
    public void contrastIsLocalToEachGlyphAndEachPageWithMultipleOwners() {
        var first = new NoteArticulationDetector.Anchor(480, 260, 12, 0);
        var second = new NoteArticulationDetector.Anchor(480, 280, 14, 0);
        var other = new NoteArticulationDetector.Anchor(680, 260, 12, 1);
        var notes = List.of(first, second, other);
        page(160);
        dot(30);
        for (int y = 220; y < 224; y++) for (int x = 678; x < 683; x++) pixel(x, y, 148);
        byte[] originalGray = gray.clone(), originalLabels = labels.clone();
        int[] expected = {NoteArticulation.STACCATO, NoteArticulation.STACCATO, 0};
        assertArrayEquals(expected, NoteArticulationDetector.detect(labels, gray, W, H, notes));
        assertArrayEquals(
                expected,
                NoteArticulationDetector.detect(labels, gray, W, H, List.of(second, first, other)));
        assertArrayEquals(originalGray, gray);
        assertArrayEquals(originalLabels, labels);

        page(160);
        dot(148);
        originalGray = gray.clone();
        originalLabels = labels.clone();
        assertArrayEquals(new int[3], NoteArticulationDetector.detect(labels, gray, W, H, notes));
        assertArrayEquals(expected, NoteArticulationDetector.detect(labels, null, W, H, notes));
        assertArrayEquals(originalGray, gray);
        assertArrayEquals(originalLabels, labels);
    }
}
