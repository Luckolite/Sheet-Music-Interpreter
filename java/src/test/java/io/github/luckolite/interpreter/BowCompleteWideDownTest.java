// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original whole wide-body controls, preserving genuinely unequal leg terminations. */
public final class BowCompleteWideDownTest {
    static final int W = 500, H = 360;

    static byte[] scene(int pen, int kind) {
        var im = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(pen, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        var p = new Path2D.Double();
        p.moveTo(232, 118);
        p.lineTo(232, 90);
        p.lineTo(268, 90);
        p.lineTo(268, 124);
        g.draw(p);
        g.setStroke(new BasicStroke(1));
        if (kind == 1) g.drawLine(250, 90, 250, 109);
        if (kind == 2) g.drawLine(250, 90, 250, 115);
        if (kind == 3) g.drawLine(232, 110, 268, 110);
        if (kind == 4) g.drawLine(232, 122, 268, 122);
        g.dispose();
        byte[] a = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) a[y * W + x] = (byte) im.getRaster().getSample(x, y, 0);
        return a;
    }

    static List<ScoreTechniqueChange> detect(byte[] raw) {
        return BowDirectionDetector.detect(
                new byte[W * H],
                raw,
                W,
                H,
                List.of(new PlayingTechniqueDetector.Staff(160, 216, 14, 0, 1)),
                List.of(new MeasureRegion(0, 1, 0, 1)),
                List.of(new ScoreNoteEvent(0, .5f, 4, 0, 1, .5f, false, 0, 0)));
    }

    @Test
    public void literalUnequalLegsMayEndIndependently() {
        for (int pen : new int[] {3, 7, 11}) {
            var a = detect(scene(pen, 0));
            assertEquals("pen" + pen, 1, a.size());
            assertEquals(ScoreTechniqueChange.DOWN_BOW, a.get(0).technique());
        }
    }

    @Test
    public void interiorThirdLegCannotBecomeABow() {
        for (int kind : new int[] {1, 2})
            assertTrue("kind" + kind, detect(scene(7, kind)).isEmpty());
    }

    @Test
    public void joiningCrossbarCannotBeIgnored() {
        assertTrue(detect(scene(7, 3)).isEmpty());
    }

    @Test
    public void terminatedLegCannotBeJoinedAgain() {
        assertTrue(detect(scene(7, 4)).isEmpty());
    }
}
