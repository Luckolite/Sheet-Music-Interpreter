// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raw/semantic returning arcs and independently printed detached marks. */
public class OwnedDetachedTieRoutesTest {
    static final int W = 420, H = 260;

    static List<ScoreNoteEvent> scene(int dots, boolean semanticCurve) {
        final int a = 100, b = 194;
        final float gap = 14, headY = 138, top = 110;
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        byte[] labels = new byte[W * H];
        g.setStroke(new BasicStroke(2));
        for (int i = 0; i < 5; i++) {
            int y = Math.round(top + i * gap);
            g.draw(new Line2D.Float(15, y, W - 15, y));
            for (int x = 15; x < W - 15; x++) labels[y * W + x] = 4;
        }
        for (int cx : new int[] {a, b}) {
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Line2D.Float(cx - 10, headY, cx - 10, headY + 3.8f * gap));
            g.fill(new Ellipse2D.Float(cx - 11, headY - 7, 22, 14));
            for (int y = 131; y <= 145; y++)
                for (int x = cx - 11; x <= cx + 11; x++)
                    if ((x - cx) * (x - cx) / 121.0 + (y - headY) * (y - headY) / 49.0 <= 1)
                        labels[y * W + x] = 2;
        }
        float left = a + 4, right = b - 4, base = headY - gap * .85f;
        g.setStroke(new BasicStroke(2.6f));
        g.draw(new QuadCurve2D.Float(left, base, (left + right) / 2, base - 10, right, base));
        // Semantic evidence comes from the same authored curve, never from dots or another glyph.
        if (semanticCurve)
            for (int x = (int) left; x <= right; x++) {
                float t = (x - left) / (right - left);
                int y = Math.round(base - 20 * t * (1 - t));
                for (int yy = y - 1; yy <= y + 1; yy++)
                    if (labels[yy * W + x] == 0) labels[yy * W + x] = 5;
            }
        for (int k = 0; k < 2; k++)
            if ((dots & (1 << k)) != 0) {
                int x = k == 0 ? a : b;
                if ((dots & 4) != 0)
                    x += 40; // Independently owned neighboring mark, not over either head.
                g.fill(new Ellipse2D.Float(x - 2.2f, headY - gap * 1.6f - 2.2f, 4.4f, 4.4f));
            }
        g.dispose();
        byte[] gray = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                gray[y * W + x] = (byte) image.getRaster().getSample(x, y, 0);
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
        assertEquals("Exactly two independently authored noteheads", 2, notes.size());
        return notes;
    }

    static void bothMarked(List<ScoreNoteEvent> n) {
        assertTrue((n.get(0).articulations() & NoteArticulation.STACCATO) != 0);
        assertTrue((n.get(1).articulations() & NoteArticulation.STACCATO) != 0);
    }

    @Test
    public void semanticReturningArcWithTwoOwnedDotsKeepsSeparateAttacks() {
        var n = scene(3, true);
        bothMarked(n);
        assertFalse(n.get(1).tiedFromPrevious());
    }

    @Test
    public void rawReturningArcWithTwoOwnedDotsKeepsSeparateAttacks() {
        var n = scene(3, false);
        bothMarked(n);
        assertFalse(n.get(1).tiedFromPrevious());
    }

    @Test
    public void genuineTieWithoutDotsRemains() {
        assertTrue(scene(0, true).get(1).tiedFromPrevious());
    }

    @Test
    public void oneOwnedStartDotDoesNotInventPairDetachment() {
        assertTrue(scene(1, true).get(1).tiedFromPrevious());
    }

    @Test
    public void oneOwnedEndDotDoesNotInventPairDetachment() {
        assertTrue(scene(2, true).get(1).tiedFromPrevious());
    }

    @Test
    public void neighboringForeignMarksCannotDetachEndpoints() {
        assertTrue(scene(7, true).get(1).tiedFromPrevious());
    }
}
