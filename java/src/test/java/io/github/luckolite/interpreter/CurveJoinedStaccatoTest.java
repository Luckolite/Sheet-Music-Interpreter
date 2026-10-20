// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original connected dot/slur topology; no score-derived coordinates or pixels. */
public class CurveJoinedStaccatoTest {
    static final int W = 500, H = 300;

    static List<ScoreNoteEvent> scene(int bulbs, float stroke, boolean middle, boolean branch) {
        final int left = 110, right = 250;
        final float gap = 16, headY = 138, base = headY - gap * 1.3f;
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        byte[] labels = new byte[W * H];
        g.setStroke(new BasicStroke(2));
        for (int k = 0; k < 5; k++) {
            int y = 170 + 16 * k;
            g.draw(new Line2D.Float(20, y, W - 20, y));
            for (int x = 20; x < W - 20; x++) labels[y * W + x] = 4;
        }
        for (int x : new int[] {left, right}) {
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Line2D.Float(x - 10, headY, x - 10, headY + 64));
            g.fill(new Ellipse2D.Float(x - 11, headY - 7, 22, 14));
            for (int yy = 131; yy <= 145; yy++)
                for (int xx = x - 11; xx <= x + 11; xx++)
                    if ((xx - x) * (xx - x) / 121.0 + (yy - headY) * (yy - headY) / 49.0 <= 1)
                        labels[yy * W + xx] = 2;
        }
        // Above-staff authored notes require their two physical ledger lines.
        byte[] beforeLedger = samples(image);
        g.setStroke(new BasicStroke(2));
        for (int cx : new int[] {left, right})
            for (int yy : new int[] {138, 154}) {
                g.draw(new Line2D.Float(cx - 18, yy, cx + 18, yy));
                for (int xx = cx - 18; xx <= cx + 18; xx++)
                    if (labels[yy * W + xx] != 2) labels[yy * W + xx] = 4;
            }
        byte[] afterLedger = samples(image);
        for (int p = 0; p < afterLedger.length; p++)
            assertTrue((afterLedger[p] & 255) <= (beforeLedger[p] & 255));
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(
                new QuadCurve2D.Float(
                        left + 1.5f, base, (left + right) / 2f, base - 12, right - 1.5f, base));
        byte[] beforeBulbs = samples(image);
        if ((bulbs & 1) != 0) g.fill(new Ellipse2D.Float(left - 3.5f, base - 3.5f, 7, 7));
        if ((bulbs & 2) != 0) g.fill(new Ellipse2D.Float(right - 3.5f, base - 3.5f, 7, 7));
        if (middle) g.fill(new Ellipse2D.Float((left + right) / 2f - 3.5f, base - 6 - 3.5f, 7, 7));
        if (branch) {
            g.setStroke(new BasicStroke(2.3f));
            g.draw(new Line2D.Float(left - 2, base - 1, left - 18, base - 17));
        }
        g.dispose();
        byte[] gray = samples(image);
        for (int i = 0; i < gray.length; i++)
            assertTrue(
                    "Black additions cannot erase preexisting curve/staff/head ink",
                    (gray[i] & 255) <= (beforeBulbs[i] & 255));
        byte[] oldGray = gray.clone(), oldLabels = labels.clone();
        var notes =
                OmrScoreInterpreter.analyze(
                                labels,
                                gray,
                                W,
                                H,
                                List.of(new MeasureRegion(.02f, .98f, .3f, .85f)))
                        .notes();
        assertArrayEquals(oldGray, gray);
        assertArrayEquals(oldLabels, labels);
        assertEquals("Two separate authored noteheads", 2, notes.size());
        return notes;
    }

    static byte[] samples(BufferedImage im) {
        byte[] a = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) a[y * W + x] = (byte) im.getRaster().getSample(x, y, 0);
        return a;
    }

    static boolean dot(ScoreNoteEvent n) {
        return (n.articulations() & NoteArticulation.STACCATO) != 0;
    }

    @Test
    public void connectedRoundBulbsKeepBothStaccatoAttacks() {
        var n = scene(3, 2.3f, false, false);
        assertTrue("left owned dot " + n, dot(n.get(0)));
        assertTrue("right owned dot " + n, dot(n.get(1)));
        assertFalse(n.get(1).tiedFromPrevious());
    }

    @Test
    public void oneConnectedBulbDoesNotInventPairDetachment() {
        var n = scene(1, 2.3f, false, false);
        assertTrue("single owned dot " + n, dot(n.get(0)));
        assertFalse(dot(n.get(1)));
        assertTrue(n.get(1).tiedFromPrevious());
    }

    @Test
    public void bareFiniteTieEndsAreNotDots() {
        var n = scene(0, 2.3f, false, false);
        assertFalse(dot(n.get(0)));
        assertFalse(dot(n.get(1)));
        assertTrue(n.get(1).tiedFromPrevious());
    }

    @Test
    public void roundedThickTieCapsAreNotDots() {
        var n = scene(0, 4, false, false);
        assertFalse(dot(n.get(0)));
        assertFalse(dot(n.get(1)));
        assertTrue(n.get(1).tiedFromPrevious());
    }

    @Test
    public void middleBulbHasNoEndpointMarkOwner() {
        var n = scene(0, 2.3f, true, false);
        assertFalse(dot(n.get(0)));
        assertFalse(dot(n.get(1)));
        assertTrue(n.get(1).tiedFromPrevious());
    }

    @Test
    public void unknownExtraTerminalBranchCannotProvePairDetachment() {
        var n = scene(3, 2.3f, false, true);
        assertFalse("Complete component has unknown extra branch", dot(n.get(0)) && dot(n.get(1)));
        assertTrue(n.get(1).tiedFromPrevious());
    }
}
