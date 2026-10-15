// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.function.Predicate;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic numeral, bracket and crossing rule; no source-score pixels. */
public class SlopedCurveThreeTest {
    private byte[] ink(char numeral, boolean bracket) {
        var image =
                new java.awt.image.BufferedImage(
                        500, 300, java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        var graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, 500, 300);
        graphics.setColor(java.awt.Color.BLACK);
        graphics.setFont(new java.awt.Font("Serif", java.awt.Font.PLAIN, 32));
        graphics.drawString(String.valueOf(numeral), 243, 156);
        graphics.dispose();
        byte[] g = new byte[500 * 300];
        for (int y = 0; y < 300; y++)
            for (int x = 0; x < 500; x++) g[y * 500 + x] = (byte) (image.getRGB(x, y) & 255);
        for (int x = 195; x <= 305; x++) {
            int cy = 153 + Math.round((x - 250) * -.1f);
            for (int y = cy; y < cy + 3; y++) g[y * 500 + x] = 0;
        }
        if (bracket) {
            for (int x = 194; x <= 239; x++) g[136 * 500 + x] = 0;
            for (int x = 261; x <= 306; x++) g[136 * 500 + x] = 0;
        }
        return g;
    }

    private Object find(byte[] g, boolean owned) throws Exception {
        return find(g, owned, Float.NaN);
    }

    private Object find(byte[] g, boolean owned, float headX) throws Exception {
        var m =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "findPrintedNumeral",
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        boolean.class,
                        float.class,
                        float.class,
                        int.class,
                        boolean.class,
                        Predicate.class);
        m.setAccessible(true);
        return m.invoke(
                null,
                g,
                500,
                300,
                200f,
                300f,
                180f,
                180f,
                20f,
                true,
                headX,
                140f,
                3,
                false,
                (Predicate<Object>) (x -> owned));
    }

    @Test
    public void joinedThree() throws Exception {
        assertNotNull(find(ink('3', true), true));
    }

    @Test
    public void joinedTwoAbstains() throws Exception {
        assertNull(find(ink('2', true), true));
    }

    @Test
    public void joinedFiveAbstains() throws Exception {
        assertNull(find(ink('5', true), true));
    }

    @Test
    public void joinedEightAbstains() throws Exception {
        assertNull(find(ink('8', true), true));
    }

    @Test
    public void missingBracketAbstains() throws Exception {
        assertNull(find(ink('3', false), true));
    }

    @Test
    public void ownershipAbstains() throws Exception {
        assertNull(find(ink('3', true), false));
    }

    @Test
    public void pixelsUnchanged() throws Exception {
        var g = ink('3', true);
        var old = g.clone();
        find(g, true);
        assertArrayEquals(old, g);
    }
}
