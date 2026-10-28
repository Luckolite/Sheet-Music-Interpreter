// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original fixed-centerline strokes, no score-derived pixels. */
public final class BowStrokeSizeTest {
    private static final int W = 500, H = 360;

    private static byte[] scene(int kind, int pen) {
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(pen, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        var path = new Path2D.Double();
        int left = kind == 2 ? 225 : 232, right = kind == 2 ? 275 : 268;
        if (kind == 1) {
            path.moveTo(left, 124);
            path.lineTo(left, 90);
            path.lineTo(right, 90);
            path.lineTo(right, 124);
        } else {
            path.moveTo(left, 90);
            path.lineTo(250, 124);
            path.lineTo(right, 90);
        }
        if (kind == 3) {
            path.moveTo(240, 104);
            path.lineTo(260, 104);
        }
        g.draw(path);
        g.dispose();
        byte[] raw = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                raw[y * W + x] = (byte) image.getRaster().getSample(x, y, 0);
        return raw;
    }

    private static List<ScoreTechniqueChange> detect(byte[] raw) {
        var note = new ScoreNoteEvent(0, .5f, 4, 0, 1, .5f, false, 0, 0);
        return BowDirectionDetector.detect(
                new byte[W * H],
                raw,
                W,
                H,
                List.of(new PlayingTechniqueDetector.Staff(160, 216, 14, 0, 1)),
                List.of(new MeasureRegion(0, 1, 0, 1)),
                List.of(note));
    }

    @Test
    public void sameUpBowCenterlineAcrossPenWidths() {
        for (int pen : new int[] {3, 7, 11}) {
            var bows = detect(scene(0, pen));
            assertEquals("up bow pen " + pen, 1, bows.size());
            assertEquals(ScoreTechniqueChange.UP_BOW, bows.get(0).technique());
        }
    }

    @Test
    public void sameDownBowCenterlineAcrossPenWidths() {
        for (int pen : new int[] {3, 7, 11}) {
            var bows = detect(scene(1, pen));
            assertEquals("down bow pen " + pen, 1, bows.size());
            assertEquals(ScoreTechniqueChange.DOWN_BOW, bows.get(0).technique());
        }
    }

    @Test
    public void nominallyOverwideCenterlineStillRefuses() {
        for (int pen : new int[] {3, 7, 11}) assertTrue(detect(scene(2, pen)).isEmpty());
    }

    @Test
    public void crossbarStillRefuses() {
        for (int pen : new int[] {3, 7, 11}) assertTrue(detect(scene(3, pen)).isEmpty());
    }
}
