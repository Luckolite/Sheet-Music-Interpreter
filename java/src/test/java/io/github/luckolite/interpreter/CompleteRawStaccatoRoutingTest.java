// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original literal compact dots; no score pixels or inferred boundary fill. */
public class CompleteRawStaccatoRoutingTest {
    static final int W = 1000, H = 1000;

    static byte[] scene(int count, int edge, int core) {
        byte[] r = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        int[][] order = {
            {481, 231}, {482, 231}, {481, 232}, {482, 232}, {481, 230}, {482, 230}, {480, 231},
            {483, 231}, {480, 232}, {483, 232}, {481, 233}, {482, 233}, {483, 233}, {480, 230},
            {483, 230}, {480, 233}
        };
        for (int i = 0; i < count; i++) {
            int x = order[i][0], y = order[i][1];
            r[y * W + x] = (byte) (i < 4 ? core : edge);
        }
        return r;
    }

    static int marks(byte[] r, float x, float y, float gap) {
        return NoteArticulationDetector.detect(
                new byte[r.length],
                r,
                W,
                H,
                List.of(new NoteArticulationDetector.Anchor(x, y, gap, 0)))[0];
    }

    @Test
    public void existingRasterLawUsesCompleteBodyBeforeCoreShrink() {
        assertEquals(4, marks(scene(13, 150, 30), 481.5f, 260, 14.25f));
    }

    @Test
    public void threeExistingCompactPositiveControlsRemain() {
        assertEquals(4, marks(scene(16, 30, 30), 481.5f, 260, 14.25f));
        assertEquals(4, marks(scene(13, 30, 30), 481.5f, 260, 14.25f));
        assertEquals(4, marks(scene(14, 150, 30), 481.5f, 260, 14.25f));
    }

    @Test
    public void fractionalAreaBoundaryDoesNotRelaxExistingLaw() {
        assertEquals(0, marks(scene(12, 150, 30), 481.5f, 260, 14.25f));
        assertEquals(4, marks(scene(13, 150, 30), 481.5f, 260, 14.5f));
        assertEquals(0, marks(scene(13, 150, 30), 481.5f, 260, 14.7f));
        assertEquals(4, marks(scene(15, 150, 30), 481.5f, 260, 14.7f));
    }

    @Test
    public void singlePixelAndIncompleteBodyRemainTooSmall() {
        assertEquals(0, marks(scene(1, 150, 30), 481.5f, 260, 14.25f));
        assertEquals(0, marks(scene(11, 150, 30), 481.5f, 260, 14.25f));
    }

    @Test
    public void originalContrastGateStillRefusesUnevenLightBody() {
        assertEquals(0, marks(scene(13, 154, 140), 481.5f, 260, 14.25f));
    }

    @Test
    public void augmentationPlacementIsNotStaccato() {
        assertEquals(0, marks(scene(13, 150, 30), 466, 231.5f, 14.25f));
    }

    @Test
    public void semanticAcceptedHeadCannotBeBorrowedByAnotherAnchor() {
        byte[] r = scene(13, 150, 30), labels = new byte[r.length];
        for (int i = 0; i < r.length; i++)
            if ((r[i] & 255) < 155) labels[i] = OmrMeasurePostProcessor.NOTEHEAD;
        var ns =
                List.of(
                        new NoteArticulationDetector.Anchor(481.5f, 231.5f, 14.25f, 0),
                        new NoteArticulationDetector.Anchor(481.5f, 260, 14.25f, 0));
        assertArrayEquals(new int[] {0, 0}, NoteArticulationDetector.detect(labels, r, W, H, ns));
        assertArrayEquals(
                new int[] {0, 0}, NoteArticulationDetector.detect(labels, null, W, H, ns));
    }

    @Test
    public void adjacentThreeLetterWordStillOwnsPunctuation() {
        byte[] r = scene(13, 150, 30);
        for (int l : new int[] {442, 454, 466})
            for (int y = 220; y <= 233; y++)
                for (int x = l; x <= l + 7; x++)
                    if (y <= 221 || y >= 232 || x <= l + 1 || x >= l + 6) r[y * W + x] = 30;
        assertEquals(0, marks(r, 481.5f, 260, 14.25f));
    }

    @Test
    public void inputPlanesAndNonbasicBitsRemainIndependent() {
        byte[] r = scene(13, 150, 30), copy = r.clone(), labels = new byte[r.length];
        assertEquals(0, NoteArticulationDetector.detect(labels, r, W, H, List.of()).length);
        assertEquals(4, marks(r, 481.5f, 260, 14.25f));
        assertArrayEquals(copy, r);
        assertArrayEquals(new byte[r.length], labels);
    }

    @Test
    public void candidatePrefixMatchesMembershipForEveryPartitionSize() {
        for (int faintCount = 0; faintCount < 5; faintCount++)
            for (int rawCount = 0; rawCount < 7; rawCount++)
                for (int paperCount = 0; paperCount < 5; paperCount++) {
                    List<Object> faint = new ArrayList<>(),
                            original = new ArrayList<>(),
                            paper = new ArrayList<>();
                    Set<Object> joined = Collections.newSetFromMap(new IdentityHashMap<>());
                    for (int n = 0; n < faintCount; n++) faint.add(new Object());
                    for (int n = 0; n < rawCount; n++) {
                        Object glyph = new Object();
                        original.add(glyph);
                        if (n % 2 != 0) joined.add(glyph);
                    }
                    for (int n = 0; n < paperCount; n++) paper.add(new Object());
                    List<Object> candidates = new ArrayList<>(faint);
                    candidates.addAll(original);
                    candidates.addAll(paper);
                    for (int index = 0; index < candidates.size(); index++) {
                        Object glyph = candidates.get(index);
                        boolean before = original.contains(glyph) && !joined.contains(glyph);
                        boolean after =
                                index >= faint.size()
                                        && index < faint.size() + original.size()
                                        && !joined.contains(glyph);
                        assertEquals(before, after);
                    }
                }
    }

    @Test
    public void normalizedPaperPartitionCannotEnterRawBodyRoute() {
        byte[] r = scene(13, 87, 15);
        for (int p = 0; p < r.length; p++) if ((r[p] & 255) == 255) r[p] = (byte) 150;
        // Raw paper joins the page; only a separate normalized component remains.
        // Its legacy rejection must not acquire this complete-raw-body fallback.
        assertEquals(0, marks(r, 481.5f, 260, 14.25f));
    }
}
