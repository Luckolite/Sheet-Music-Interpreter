// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.List;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exact pixel-center finite stroke originals; no AA or score pixels. */
public final class BowCoordinateExtentTest {
    static double dist2(double x, double y, double ax, double ay, double bx, double by) {
        double dx = bx - ax,
                dy = by - ay,
                u = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
        double xx = x - ax - u * dx, yy = y - ay - u * dy;
        return xx * xx + yy * yy;
    }

    static void check(
            String name,
            int scale,
            double phase,
            int lineHeight,
            int kind,
            int expected,
            int expectedExtent) {
        int W = 500 * scale, H = 360 * scale;
        byte[] r = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(r, (byte) 255);
        double top = (125 - lineHeight + phase) * scale,
                tip = (125 + phase) * scale,
                left = (237 + phase) * scale,
                right = (263 + phase) * scale,
                cx = (250 + phase) * scale,
                rad = 2 * scale;
        int min = H, max = -1;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                boolean at =
                        dist2(x, y, left, top, cx, tip) <= rad * rad
                                || dist2(x, y, cx, tip, right, top) <= rad * rad;
                if (kind == 3) at |= dist2(x, y, cx, tip, cx, (90 + phase) * scale) <= rad * rad;
                if (at) {
                    r[y * W + x] = 0;
                    min = Math.min(min, y);
                    max = Math.max(max, y);
                }
            }
        byte[] before = r.clone();
        var ns = new ArrayList<NoteArticulationDetector.Anchor>();
        var es = new ArrayList<ScoreNoteEvent>();
        if (kind == 1 || kind == 2) {
            ns.add(
                    new NoteArticulationDetector.Anchor(
                            (float) ((244 + phase) * scale),
                            (float) ((180 + phase) * scale),
                            14 * scale,
                            0));
            ns.add(
                    new NoteArticulationDetector.Anchor(
                            (float) ((256 + phase) * scale),
                            (float) ((180 + phase) * scale),
                            14 * scale,
                            0));
            es.add(new ScoreNoteEvent(0, .4f, 4, 0, 1, .5f, false, 0, 0));
            es.add(new ScoreNoteEvent(0, kind == 1 ? .6f : .4f, 4, 0, 1, .5f, false, 0, 0));
        } else {
            ns.add(
                    new NoteArticulationDetector.Anchor(
                            (float) cx, (float) ((180 + phase) * scale), 14 * scale, 0));
            es.add(new ScoreNoteEvent(0, .5f, 4, 0, 1, .5f, false, 0, 0));
        }
        var got =
                NoteArticulationDetector.bowMarks(
                        labels,
                        r,
                        W,
                        H,
                        ns,
                        es,
                        List.of(
                                new PlayingTechniqueDetector.Staff(
                                        (float) ((160 + phase) * scale),
                                        (float) ((216 + phase) * scale),
                                        14 * scale,
                                        0,
                                        1)));
        assertEquals(name + " measured coordinate extent", expectedExtent, max - min);
        assertEquals(name, expected, got.size());
        assertArrayEquals(before, r);
        assertArrayEquals(new byte[W * H], labels);
        if (!got.isEmpty()) assertEquals(ScoreTechniqueChange.UP_BOW, got.get(0).technique());
    }

    @Test
    public void atLimitAtUnitScale() {
        check("atLimitAtUnitScale", 1, 0, 45, 0, 1, 49);
    }

    @Test
    public void atLimitAtDoubleScale() {
        check("atLimitAtDoubleScale", 2, 0, 45, 0, 1, 98);
    }

    @Test
    public void halfPixelTranslationAtUnitScale() {
        check("halfPixelTranslationAtUnitScale", 1, 0.5, 45, 0, 1, 48);
    }

    @Test
    public void equivalentHalfPixelTranslationAtDoubleScale() {
        check("equivalentHalfPixelTranslationAtDoubleScale", 2, 0.5, 45, 0, 1, 98);
    }

    @Test
    public void aboveLimitAtUnitScale() {
        check("aboveLimitAtUnitScale", 1, 0, 46, 0, 0, 50);
    }

    @Test
    public void aboveLimitAtDoubleScale() {
        check("aboveLimitAtDoubleScale", 2, 0, 46, 0, 0, 100);
    }

    @Test
    public void aboveLimitHalfPixelAtUnitScale() {
        check("aboveLimitHalfPixelAtUnitScale", 1, 0.5, 47, 0, 0, 50);
    }

    @Test
    public void aboveLimitHalfPixelAtDoubleScale() {
        check("aboveLimitHalfPixelAtDoubleScale", 2, 0.5, 47, 0, 0, 102);
    }

    @Test
    public void legacyWithinLimitAtUnitScale() {
        check("legacyWithinLimitAtUnitScale", 1, 0, 44, 0, 1, 48);
    }

    @Test
    public void legacyWithinLimitAtDoubleScale() {
        check("legacyWithinLimitAtDoubleScale", 2, 0, 44, 0, 1, 96);
    }

    @Test
    public void competingAttacksStillAbstainAtBoundary() {
        check("competingAttacksStillAbstainAtBoundary", 1, 0, 45, 1, 0, 49);
    }

    @Test
    public void sameExactAttackChordAtBoundary() {
        check("sameExactAttackChordAtBoundary", 1, 0, 45, 2, 1, 49);
    }

    @Test
    public void thirdArmStillRefusesAtBoundary() {
        check("thirdArmStillRefusesAtBoundary", 1, 0, 45, 3, 0, 49);
    }

    static void rejectKnownOversizeCenterline(int centerlineHeight) {
        int W = 500, H = 360;
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D c = image.createGraphics();
        c.setColor(Color.WHITE);
        c.fillRect(0, 0, W, H);
        c.setColor(Color.BLACK);
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        c.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        var path = new Path2D.Double();
        path.moveTo(237, 124 - centerlineHeight);
        path.lineTo(250, 124);
        path.lineTo(263, 124 - centerlineHeight);
        c.draw(path);
        c.dispose();
        byte[] raw = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                raw[y * W + x] = (byte) image.getRaster().getSample(x, y, 0);
        byte[] before = raw.clone(), labels = new byte[W * H];
        var got =
                NoteArticulationDetector.bowMarks(
                        labels,
                        raw,
                        W,
                        H,
                        List.of(new NoteArticulationDetector.Anchor(250, 180, 14, 0)),
                        List.of(new ScoreNoteEvent(0, .5f, 4, 0, 1, .5f, false, 0, 0)),
                        List.of(new PlayingTechniqueDetector.Staff(160, 216, 14, 0, 1)));
        assertTrue("known centerline height " + centerlineHeight, got.isEmpty());
        assertArrayEquals(before, raw);
        assertArrayEquals(new byte[W * H], labels);
    }

    @Test
    public void nominalFiftyTwoPixelCenterlineCannotHideInLongColumnRuns() {
        rejectKnownOversizeCenterline(52);
    }

    @Test
    public void nominalFiftySixPixelCenterlineCannotHideInLongColumnRuns() {
        rejectKnownOversizeCenterline(56);
    }
}
