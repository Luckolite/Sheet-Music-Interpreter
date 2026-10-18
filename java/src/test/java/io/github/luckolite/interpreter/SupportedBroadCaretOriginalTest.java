// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.geom.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently authored broad finite caret arms, same raster/page contract. */
public class SupportedBroadCaretOriginalTest {
    @Test
    public void broadArmsRemainMarcato() {
        for (double half : new double[] {12, 14})
            for (double phase : new double[] {0, .5}) {
                var i = FinitePageMarcatoOriginalTest.canvas();
                var g = i.createGraphics();
                g.setColor(Color.BLACK);
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                var p = new Path2D.Double();
                p.moveTo(128 - half + phase, 112);
                p.lineTo(128 + phase, 100);
                p.lineTo(128 + half + phase, 112);
                g.draw(p);
                g.dispose();
                int flag = FinitePageMarcatoOriginalTest.read(i, 16);
                System.out.println("half=" + half + " phase=" + phase + " flag=" + flag);
                assertEquals(2, flag);
            }
    }
}
