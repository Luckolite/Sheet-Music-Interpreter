// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.*;
import java.util.*;

public class UprightChordF105 {
    @org.junit.Test
    public void originalControls() {
        main(new String[0]);
    }

    static boolean test(String glyph, String font, int style, int size, boolean above) {
        int w = 120, h = 240;
        var image = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(new Font(font, style, size));
        g.drawString(glyph, 30, 60);
        g.dispose();
        byte[] gray = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) gray[y * w + x] = (byte) (image.getRGB(x, y) & 255);
        byte[] copy = gray.clone();
        boolean answer =
                ScoreDynamicsDetector.uprightChordF(
                        new PlayingTechniqueDetector.Word("f", .1f, .05f, .8f, .3f),
                        new PlayingTechniqueDetector.Staff(
                                above ? 120 : 0, above ? 216 : 96, 24, 0, 1),
                        gray,
                        w,
                        h);
        if (!Arrays.equals(gray, copy)) throw new AssertionError("mutated pixels");
        return answer;
    }

    public static void main(String[] args) {
        int checks = 0;
        for (int size : new int[] {28, 36, 44}) {
            if (!test("F", "SansSerif", Font.PLAIN, size, true))
                throw new AssertionError("upright F " + size);
            checks++;
            for (String font : new String[] {"SansSerif", "Serif", "Dialog"})
                for (int style : new int[] {Font.ITALIC, Font.BOLD | Font.ITALIC}) {
                    if (test("f", font, style, size, true))
                        throw new AssertionError("italic forte " + font + style + size);
                    checks++;
                }
            if (test("F", "SansSerif", Font.PLAIN, size, false))
                throw new AssertionError("below staff");
            checks++;
            for (String glyph : new String[] {"E", "P", "p", "mf", "ff"}) {
                if (test(glyph, "SansSerif", Font.PLAIN, size, true))
                    throw new AssertionError(glyph);
                checks++;
            }
        }
        System.out.println("PASS " + checks + " upright-F and retained-dynamic controls");
    }
}
