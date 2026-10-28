// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural complete bows and foreign branches; no score crops or coordinates. */
public class BowArmCompletenessTest {
    static final int W = 500, H = 360;

    static ScoreNoteEvent note() {
        return new ScoreNoteEvent(
                0,
                .5f,
                4,
                0,
                1,
                .5f,
                false,
                0,
                0,
                2,
                1,
                1,
                0,
                0,
                30,
                false,
                0,
                false,
                0,
                0,
                1,
                1,
                ScoreNoteEvent.Kind.PITCHED);
    }

    static byte[] scene(int kind) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D c = im.createGraphics();
        c.setColor(Color.WHITE);
        c.fillRect(0, 0, W, H);
        c.setColor(Color.BLACK);
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        c.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        if (kind == 0) {
            p.moveTo(236, 79);
            p.curveTo(236, 103, 261, 103, 261, 79);
            p.lineTo(257, 124);
        }
        if (kind == 1) {
            p.moveTo(236, 79);
            p.lineTo(250, 124);
            p.lineTo(264, 79);
        }
        if (kind == 2) {
            p.moveTo(230, 85);
            p.lineTo(250, 124);
            p.lineTo(261, 79);
        }
        if (kind == 3) {
            p.moveTo(236, 79);
            p.curveTo(239, 104, 240, 124, 250, 124);
            p.curveTo(260, 124, 261, 104, 264, 79);
        }
        if (kind == 4) {
            p.moveTo(236, 89);
            p.lineTo(250, 124);
            p.lineTo(264, 79);
        }
        if (kind == 5) {
            p.moveTo(226, 85);
            p.lineTo(250, 121);
            p.lineTo(274, 85);
        }
        if (kind == 6) {
            p.moveTo(236, 79);
            p.lineTo(250, 124);
            p.lineTo(264, 79);
            p.moveTo(259, 96);
            p.lineTo(250, 90);
        }
        if (kind == 7) {
            c.setFont(new Font("Serif", Font.ITALIC, 62));
            c.drawString("f", 233, 125);
        }
        if (kind == 8) {
            c.translate(.35, .35);
            p.moveTo(236, 79);
            p.lineTo(250, 124);
            p.lineTo(264, 79);
        }
        if (kind == 9) {
            c.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            p.moveTo(236, 79);
            p.lineTo(250, 124);
            p.lineTo(264, 79);
        }
        if (kind == 10) {
            p.moveTo(236, 79);
            p.lineTo(250, 124);
            p.lineTo(264, 79);
        }
        if (kind == 11) {
            p.moveTo(236, 79);
            p.lineTo(250, 124);
            p.lineTo(264, 79);
            p.moveTo(241, 95);
            p.lineTo(259, 95);
        }
        if (kind == 12) {
            p.moveTo(236, 79);
            p.lineTo(250, 124);
            p.lineTo(264, 79);
            p.moveTo(259, 96);
            p.lineTo(269, 110);
            p.lineTo(279, 96);
        }
        c.draw(p);
        if (kind == 10) {
            c.setColor(Color.WHITE);
            c.fillRect(0, 101, W, 2);
        }
        c.dispose();
        byte[] r = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) r[y * W + x] = (byte) im.getRaster().getSample(x, y, 0);
        return r;
    }

    static List<ScoreTechniqueChange> read(int kind) {
        byte[] raw = scene(kind),
                labels = new byte[W * H],
                before = raw.clone(),
                semantic = labels.clone();
        var result =
                BowDirectionDetector.detect(
                        labels,
                        raw,
                        W,
                        H,
                        List.of(new PlayingTechniqueDetector.Staff(160, 240, 20, 0, 1)),
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        List.of(note()));
        assertArrayEquals(before, raw);
        assertArrayEquals(semantic, labels);
        return result;
    }

    static void up(int kind) {
        assertEquals(
                List.of(new ScoreTechniqueChange(0, .5f, 0, 1, ScoreTechniqueChange.UP_BOW)),
                read(kind));
    }

    @Test
    public void falseNumeralFour() {
        assertTrue(read(0).isEmpty());
    }

    @Test
    public void validStraightV() {
        up(1);
    }

    @Test
    public void validSlantedV() {
        up(2);
    }

    @Test
    public void validRoundedU() {
        up(3);
    }

    @Test
    public void validUnequalArmTops() {
        up(4);
    }

    @Test
    public void validShallowV() {
        up(5);
    }

    @Test
    public void falseInteriorSpur() {
        assertTrue(read(6).isEmpty());
    }

    @Test
    public void falseItalicF() {
        assertTrue(read(7).isEmpty());
    }

    @Test
    public void validFractionalRasterPhase() {
        up(8);
    }

    @Test
    public void validThickV() {
        up(9);
    }

    @Test
    public void disconnectedRowsDoNotCompleteBow() {
        assertTrue(read(10).isEmpty());
    }

    @Test
    public void falseCompleteInteriorCrossbar() {
        assertTrue(read(11).isEmpty());
    }

    @Test
    public void falseRivalBranch() {
        assertTrue(read(12).isEmpty());
    }
}
