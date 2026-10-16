// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;

/** Original antialiased tie and slur geometry; no score-derived pixels. */
public class MicroTieTest {
    static final int W = 360, H = 260;

    static java.util.List<ScoreNoteEvent> scene(
            float gap, int span, float bow, boolean shortArc, boolean longArc) {
        return scene(gap, span, bow, shortArc, longArc, false, false, false);
    }

    static java.util.List<ScoreNoteEvent> scene(
            float gap,
            int span,
            float bow,
            boolean shortArc,
            boolean longArc,
            boolean different,
            boolean broken,
            boolean mirror) {
        return scene(gap, span, bow, shortArc, longArc, different, broken, mirror, false);
    }

    static java.util.List<ScoreNoteEvent> scene(
            float gap,
            int span,
            float bow,
            boolean shortArc,
            boolean longArc,
            boolean different,
            boolean broken,
            boolean mirror,
            boolean detached) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        byte[] labels = new byte[W * H];
        float top = 110, cy = top + 2 * gap;
        int first = 80, second = first + span;
        g.setStroke(new BasicStroke(2f));
        for (int row = 0; row < 5; row++) {
            float y = top + row * gap;
            g.draw(new Line2D.Float(15, y, W - 15, y));
            for (int x = 15; x < W - 15; x++) labels[Math.round(y) * W + x] = 4;
        }
        for (int cx : new int[] {first, second}) {
            float headY = cy + (different && cx == second ? gap * .5f : 0);
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Line2D.Float(cx - 10, headY, cx - 10, headY + gap * 3.8f));
            AffineTransform old = g.getTransform();
            g.rotate(-.35, cx, headY);
            g.fill(new Ellipse2D.Float(cx - 11, headY - 7, 22, 14));
            g.setTransform(old);
            for (int y = Math.round(headY - 10); y <= headY + 10; y++)
                for (int x = cx - 13; x <= cx + 13; x++) {
                    double xx = (x - cx) * Math.cos(.35) - (y - headY) * Math.sin(.35),
                            yy = (x - cx) * Math.sin(.35) + (y - headY) * Math.cos(.35);
                    if (xx * xx / 121 + yy * yy / 49 <= 1) labels[y * W + x] = 2;
                }
        }
        g.setStroke(new BasicStroke(1.2f));
        if (shortArc) {
            float a = first + 17, b = second - 17, y = cy - gap * .55f;
            g.draw(new QuadCurve2D.Float(a, y, (a + b) / 2, y - bow * 2, b, y));
        }
        if (longArc) {
            float a = first + 3, b = second + 60, y = cy - gap * .7f;
            g.draw(new QuadCurve2D.Float(a, y, (a + b) / 2, y - gap * 4, b, y));
        }
        if (broken) {
            g.setColor(Color.WHITE);
            g.fillRect(first + span / 2 - 10, Math.round(cy - gap * 1.6f), 20, Math.round(gap));
        }
        if (detached) {
            g.setColor(Color.BLACK);
            for (int x : new int[] {first, second})
                g.fill(new Ellipse2D.Float(x - 2.2f, cy - gap * 1.6f - 2.2f, 4.4f, 4.4f));
        }
        g.dispose();
        byte[] gray = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) gray[y * W + x] = (byte) im.getRaster().getSample(x, y, 0);
        if (mirror)
            for (int i = 0; i < gray.length / 2; i++) {
                int j = gray.length - 1 - i;
                byte t = gray[i];
                gray[i] = gray[j];
                gray[j] = t;
                t = labels[i];
                labels[i] = labels[j];
                labels[j] = t;
            }
        return OmrScoreInterpreter.analyze(
                        labels,
                        gray,
                        W,
                        H,
                        java.util.List.of(new MeasureRegion(.02f, .98f, .3f, .85f)))
                .notes();
    }

    static final float[][] CASES = {
        {16.0f, 44f, 3.0f},
        {16.0f, 44f, 4.0f},
        {16.0f, 47f, 4.0f},
        {16.0f, 50f, 3.0f},
        {16.0f, 50f, 4.0f},
        {16.0f, 53f, 3.0f},
        {16.0f, 53f, 4.0f},
        {18.0f, 44f, 3.0f},
        {18.0f, 44f, 4.0f},
        {18.0f, 47f, 4.0f},
        {18.0f, 50f, 3.0f},
        {18.0f, 50f, 4.0f},
        {18.0f, 53f, 3.0f},
        {18.0f, 53f, 4.0f},
        {20.0f, 44f, 4.0f},
        {20.0f, 47f, 4.0f},
        {20.0f, 50f, 3.0f},
        {20.0f, 50f, 4.0f},
        {20.0f, 53f, 4.0f}
    };

    @Test
    public void recognizesShortArcsAndPreservesEarlierPositives() {
        for (float[] c : CASES) {
            var n = scene(c[0], (int) c[1], c[2], true, false);
            assertEquals(2, n.size());
            assertTrue(Arrays.toString(c), n.get(1).tiedFromPrevious());
        }
    }

    @Test
    public void recognizesReflectedArcs() {
        for (float[] c : CASES) {
            var n = scene(c[0], (int) c[1], c[2], true, false, false, false, true);
            assertEquals(2, n.size());
            assertTrue(Arrays.toString(c), n.get(1).tiedFromPrevious());
        }
    }

    @Test
    public void rejectsDifferentPitch() {
        for (float[] c : CASES) {
            var n = scene(c[0], (int) c[1], c[2], true, false, true, false, false);
            assertEquals(2, n.size());
            assertFalse(Arrays.toString(c), n.get(1).tiedFromPrevious());
        }
    }

    @Test
    public void rejectsBrokenArcs() {
        for (float[] c : CASES) {
            var n = scene(c[0], (int) c[1], c[2], true, false, false, true, false);
            assertEquals(2, n.size());
            assertFalse(Arrays.toString(c), n.get(1).tiedFromPrevious());
        }
    }

    @Test
    public void rejectsFlatStrokes() {
        for (float[] c : CASES) {
            var n = scene(c[0], (int) c[1], 0, true, false);
            assertEquals(2, n.size());
            assertFalse(Arrays.toString(c), n.get(1).tiedFromPrevious());
        }
    }

    @Test
    public void rejectsLongSlurWithoutTie() {
        for (float[] c : CASES) {
            var n = scene(c[0], (int) c[1], c[2], false, true);
            assertEquals(2, n.size());
            assertFalse(Arrays.toString(c), n.get(1).tiedFromPrevious());
        }
    }
}
