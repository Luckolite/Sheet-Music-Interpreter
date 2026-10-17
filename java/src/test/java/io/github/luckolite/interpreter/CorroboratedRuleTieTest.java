// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.List;

/** Original procedural staff, heads and engraving; no score pixels or traced outlines. */
public class CorroboratedRuleTieTest {
    static final int W = 400, H = 240;

    record Geometry(
            float ruleWidth,
            int ruleShade,
            float period,
            float phase,
            int barX,
            boolean cubic,
            float depth,
            float exponent) {}

    static final Geometry CUBIC = new Geometry(1.8f, 150, 14.25f, .5f, 200, true, 0, 0);
    static final Geometry ROUND = new Geometry(3.4f, 100, 13.75f, 0, 200, false, 10, 1.2f);
    static final Geometry SHALLOW = new Geometry(2.2f, 130, 14.25f, .5f, 192, false, 11, .6f);

    record Page(byte[] labels, byte[] gray) {}

    static Page draw(Geometry shape, int mode, boolean bar, boolean different) {
        BufferedImage large = new BufferedImage(W * 4, H * 4, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = large.createGraphics();
        g.scale(4, 4);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(shape.ruleShade, shape.ruleShade, shape.ruleShade));
        g.setStroke(new BasicStroke(shape.ruleWidth));
        for (int rule = 0; rule < 5; rule++) {
            float y = 80 + rule * shape.period + shape.phase;
            g.draw(new Line2D.Float(20, y, 380, y));
        }
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(3));
        // A connected grand-staff barline continues below the upper five rules.
        if (bar) g.drawLine(shape.barX, 80, shape.barX, 190);
        for (int i = 0; i < 2; i++) {
            int x = 150 + 90 * i, y = 115 - (different && i == 1 ? 7 : 0);
            g.fill(new Ellipse2D.Float(x - 10, y - 7, 20, 14));
            g.setStroke(new BasicStroke(2));
            g.drawLine(x + 9, y, x + 9, y - 36);
        }
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (mode == 1 || mode == 2 || mode == 6) {
            Path2D.Float p = new Path2D.Float();
            if (shape.cubic) {
                p.moveTo(164, 127);
                p.curveTo(177, 146, 214, 146, 226, 127);
            } else
                for (int x = 164; x <= 226; x++) {
                    float t = (x - 164) / 62f,
                            y =
                                    128
                                            + shape.depth
                                                    * (float)
                                                            Math.pow(
                                                                    4 * t * (1 - t),
                                                                    shape.exponent);
                    if (x == 164) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
            if (mode == 2) g.setClip(0, 0, 195, H);
            g.draw(p);
            g.setClip(null);
        }
        if (mode == 3) g.draw(new Ellipse2D.Float(164, 114, 62, 28));
        if (mode == 4) {
            g.setFont(new Font("Serif", Font.BOLD, 32));
            g.drawString("u", 179, 143);
        }
        if (mode == 5) g.fillRect(163, 134, 64, 10);
        g.dispose();
        BufferedImage small = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D d = small.createGraphics();
        d.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        d.drawImage(large, 0, 0, W, H, null);
        d.dispose();
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                gray[y * W + x] = (byte) small.getRaster().getSample(x, y, 0);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                if ((gray[y * W + x] & 255) > 205) continue;
                byte kind = 5;
                for (int rule = 0; rule < 5; rule++)
                    if (Math.abs(y - (80 + rule * shape.period + shape.phase)) <= 2)
                        kind = (byte) (mode == 6 && rule < 2 && x >= 160 && x <= 230 ? 5 : 4);
                if (bar && Math.abs(x - shape.barX) <= 2 && y >= 80 && y <= 190) kind = 1;
                for (int i = 0; i < 2; i++) {
                    int hx = 150 + 90 * i, hy = 115 - (different && i == 1 ? 7 : 0);
                    if (Math.abs(x - hx - 9) <= 2 && y <= hy && y >= hy - 36) kind = 1;
                    if (Math.pow((x - hx) / 10., 2) + Math.pow((y - hy) / 7., 2) <= 1.1) kind = 2;
                }
                labels[y * W + x] = kind;
            }
        return new Page(labels, gray);
    }

    static List<MeasureRegion> measures(boolean missingBar) {
        if (missingBar)
            return List.of(
                    new MeasureRegion(.05f, .45f, .25f, .67f),
                    new MeasureRegion(.45f, .55f, .25f, .67f),
                    new MeasureRegion(.55f, .95f, .25f, .67f));
        return List.of(
                new MeasureRegion(.05f, .4875f, .25f, .67f),
                new MeasureRegion(.4875f, .95f, .25f, .67f));
    }

    static boolean tied(
            Geometry shape, int mode, boolean bar, boolean different, boolean missingBar) {
        Page p = draw(shape, mode, bar, different);
        var result = OmrScoreInterpreter.analyze(p.labels, p.gray, W, H, measures(missingBar));
        assertEquals(2, result.notes().size());
        return result.notes().get(1).tiedFromPrevious();
    }

    @Test
    public void fractionalStaffAndCubicBowCrossGrandBarline() {
        assertTrue(tied(CUBIC, 1, true, false, false));
    }

    @Test
    public void darkerRoundedBowSurvivesIndependentLighterRules() {
        assertTrue(tied(ROUND, 1, true, false, false));
    }

    @Test
    public void shallowBowUsesItsOwnCompleteContrast() {
        assertTrue(tied(SHALLOW, 1, true, false, false));
    }

    @Test
    public void plainStaffAndGrandBarlineCannotTie() {
        assertFalse(tied(CUBIC, 0, true, false, false));
    }

    @Test
    public void ordinaryStaffWithoutBowCannotTie() {
        assertFalse(tied(CUBIC, 0, false, false, false));
    }

    @Test
    public void incompleteBowCannotTie() {
        assertFalse(tied(CUBIC, 2, true, false, false));
    }

    @Test
    public void closedCounterCannotTie() {
        assertFalse(tied(CUBIC, 3, true, false, false));
    }

    @Test
    public void letterCannotTie() {
        assertFalse(tied(CUBIC, 4, true, false, false));
    }

    @Test
    public void broadBeamCannotTie() {
        assertFalse(tied(CUBIC, 5, true, false, false));
    }

    @Test
    public void slurToAnotherPitchCannotTie() {
        assertFalse(tied(CUBIC, 1, true, true, false));
    }

    @Test
    public void emptyInterveningMeasureCannotBeConsumed() {
        assertFalse(tied(CUBIC, 1, true, false, true));
    }

    @Test
    public void twoReferencesCannotReplaceThree() {
        assertFalse(tied(CUBIC, 6, true, false, false));
    }

    @Test
    public void saturatedOverlapDoesNotInventContrast() {
        var black =
                new Geometry(CUBIC.ruleWidth, 0, CUBIC.period, CUBIC.phase, CUBIC.barX, true, 0, 0);
        assertFalse(tied(black, 1, true, false, false));
    }

    @Test
    public void sourceEvidenceArraysStayUnchanged() {
        Page p = draw(CUBIC, 1, true, false);
        byte[] l = p.labels.clone(), g = p.gray.clone();
        OmrScoreInterpreter.analyze(p.labels, p.gray, W, H, measures(false));
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }
}
