// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete logical-font digits and finite two-segment carets; no score pixels. */
public class FinitePageMarcatoOriginalTest {
    static final int W = 1600, H = 2000;

    static int read(BufferedImage im, float gap) {
        byte[] g = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) g[y * W + x] = (byte) im.getRaster().getSample(x, y, 0);
        return NoteArticulationDetector.detect(
                new byte[g.length],
                g,
                W,
                H,
                java.util.List.of(new NoteArticulationDetector.Anchor(128, 164, gap, 0)))[0];
    }

    static BufferedImage canvas() {
        var i = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = i.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.dispose();
        return i;
    }

    static BufferedImage caret(double scale, double phase, boolean down, boolean aa) {
        var i = canvas();
        var g = i.createGraphics();
        g.setColor(Color.BLACK);
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                aa ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setStroke(
                new BasicStroke(
                        (float) (2.8 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        var p = new Path2D.Double();
        p.moveTo(128 - 7 * scale + phase, down ? 100 : 100 + 12 * scale);
        p.lineTo(128 + phase, down ? 100 + 12 * scale : 100);
        p.lineTo(128 + 7 * scale + phase, down ? 100 : 100 + 12 * scale);
        g.draw(p);
        g.dispose();
        return i;
    }

    @Test
    public void completeCaretsPreservePhasesScalesAndAntialiasing() {
        for (double s : new double[] {.75, 1, 1.25})
            for (double p : new double[] {0, .25, .5, .75})
                for (boolean aa : new boolean[] {false, true})
                    assertEquals(
                            "caret " + s + "/" + p + "/" + aa,
                            2,
                            read(caret(s, p, false, aa), (float) (16 * s)));
    }

    @Test
    public void reversedCaretsRemainUpBows() {
        for (double s : new double[] {.75, 1, 1.25})
            for (double p : new double[] {0, .5})
                assertEquals(
                        "upbow " + s + "/" + p, 0, read(caret(s, p, true, true), (float) (16 * s)));
    }

    @Test
    public void completeNumeralsAreNotDetachedMarcato() throws Exception {
        for (String font : java.util.List.of("Serif", "SansSerif", "Monospaced"))
            for (int style :
                    new int[] {Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC})
                for (int size : new int[] {18, 24, 30}) {
                    var i = canvas();
                    var g = i.createGraphics();
                    g.setColor(Color.BLACK);
                    g.setRenderingHint(
                            RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setFont(new Font(font, style, size));
                    g.drawString("5", 123, 115);
                    g.dispose();
                    assertEquals(font + "/" + style + "/" + size, 0, read(i, 16));
                }
    }
}
