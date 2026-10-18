// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original vector engraving: finite local glyph roles versus unrelated long-row support. */
public class LocalRestRuleOwnershipTest {
    static final int W = 760, H = 360;
    static final float TOP = 160, GAP = 16;

    static final class Scene {
        final BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        final Graphics2D g = image.createGraphics();

        Scene(boolean shortBow, boolean remote, boolean rest) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);
            g.setColor(Color.BLACK);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1));
            for (int k = 0; k < 5; k++)
                g.draw(new Line2D.Double(30, TOP + k * GAP, W - 30, TOP + k * GAP));
            if (shortBow) {
                head(200, 184, false);
                head(270, 200, true);
                head(350, 168, false);
                g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new QuadCurve2D.Double(206, 170, 228, 119, 267, 148));
            }
            if (remote) {
                g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new QuadCurve2D.Double(450, 170, 560, 115, 670, 170));
            }
            if (rest) {
                // A complete left-facing bulb with independent descending diagonal tail.
                g.fill(new Ellipse2D.Double(192, 146, 14, 10));
                g.setStroke(new BasicStroke(2.4f));
                g.draw(new Line2D.Double(207, 148, 196, 176));
            }
            g.dispose();
        }

        void head(int x, int y, boolean up) {
            AffineTransform saved = g.getTransform();
            g.rotate(-.35, x, y);
            g.fill(new Ellipse2D.Double(x - 8, y - 5, 16, 10));
            g.setTransform(saved);
            g.setStroke(new BasicStroke(2));
            g.draw(new Line2D.Double(x + (up ? 7 : -7), y, x + (up ? 7 : -7), y + (up ? -52 : 52)));
        }

        byte[] gray() {
            return ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer())
                    .getData()
                    .clone();
        }

        List<ScoreRestEvent> detect(boolean notes) {
            byte[] raw = gray(), before = raw.clone();
            var heads =
                    notes
                            ? List.of(note(200, 184, 5), note(270, 200, 3), note(350, 168, 7))
                            : List.<ScoreNoteEvent>of();
            var result =
                    SixteenthRestDetector.detect(
                            raw,
                            W,
                            H,
                            List.of(new MeasureRegion(.04f, .95f, .2f, .9f)),
                            List.of(new SixteenthRestDetector.Staff(TOP, TOP + 4 * GAP, GAP, 0, 1)),
                            heads);
            assertArrayEquals(before, raw);
            return result;
        }

        ScoreNoteEvent note(int x, int y, int step) {
            return new ScoreNoteEvent(
                    0, (x / (float) W - .04f) / .91f, step, 0, 1, y / (float) H, false, 0, 0);
        }
    }

    static long local(List<ScoreRestEvent> rests) {
        return rests.stream().filter(r -> r.positionInMeasure() < .42f).count();
    }

    @Test
    public void completeShortSlurIsNotAnEighthRest() {
        var r = new Scene(true, false, false).detect(true);
        assertEquals(r.toString(), 0, local(r));
    }

    @Test
    public void remoteLongSlurCannotChangeLocalShortSlurIntoRest() {
        var r = new Scene(true, true, false).detect(true);
        assertEquals(r.toString(), 0, local(r));
    }

    @Test
    public void genuineRaisedEighthSurvivesRemoteArch() {
        var r = new Scene(false, true, true).detect(false);
        assertEquals(r.toString(), 1, local(r));
        assertEquals(
                .5,
                r.stream()
                        .filter(x -> x.positionInMeasure() < .42f)
                        .findFirst()
                        .orElseThrow()
                        .durationBeats(),
                0);
    }

    @Test
    public void genuineRaisedEighthSurvivesActualRuleCrossing() {
        var r = new Scene(false, false, true).detect(false);
        assertEquals(r.toString(), 1, local(r));
        assertEquals(
                .5,
                r.stream()
                        .filter(x -> x.positionInMeasure() < .42f)
                        .findFirst()
                        .orElseThrow()
                        .durationBeats(),
                0);
    }
}
