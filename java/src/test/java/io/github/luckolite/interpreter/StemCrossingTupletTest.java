// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raster numeral and straight staff/stem crossings. */
public class StemCrossingTupletTest {
    static final int W = 400, H = 350;

    byte[] paper() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        return g;
    }

    void rect(byte[] g, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = 0;
    }

    void numeral(byte[] g, int x, int y) {
        rect(g, x + 8, y, 3, 20);
        for (int dy : new int[] {0, 9, 18}) rect(g, x, y + dy, 11, 2);
    }

    int[] find(byte[] g) {
        return RepeatedTupletNumeral.findJoined(
                g,
                W,
                H,
                new int[] {30, 25, 40, 44},
                200,
                280,
                220,
                220,
                20,
                b -> b[0] >= 234 && b[0] <= 236 && b[1] >= 99 && b[1] <= 101);
    }

    @Test
    public void extendedStemAndRulesDoNotCreatePrintedThree() {
        var g = paper();
        numeral(g, 30, 25);
        rect(g, 243, 80, 3, 65);
        for (int y : new int[] {100, 109, 118}) rect(g, 195, y, 95, 2);
        assertNull(find(g));
    }

    @Test
    public void actualNumeralWithoutAnExtendedStemRemains() {
        var g = paper();
        numeral(g, 30, 25);
        numeral(g, 235, 100);
        assertNotNull(find(g));
    }

    void curved(byte[] g, int x, int y) {
        String[] rows = {
            "..#######...",
            ".##########.",
            "###......###",
            "####.....###",
            "####.....###",
            "####.....###",
            ".##.....####",
            ".......####.",
            "......####..",
            "....#####...",
            "....#####...",
            "....#####...",
            "......####..",
            ".......####.",
            "##.....####.",
            "###....####.",
            "###....####.",
            "###....####.",
            ".###....###.",
            "..########..",
            "..########..",
            "....####...."
        };
        for (int yy = 0; yy < rows.length; yy++)
            for (int xx = 0; xx < rows[yy].length(); xx++)
                if (rows[yy].charAt(xx) == '#') g[(y + yy) * W + x + xx] = 0;
    }

    int[] curvedFind(byte[] g) {
        return RepeatedTupletNumeral.findJoined(
                g,
                W,
                H,
                new int[] {30, 25, 41, 46},
                200,
                280,
                220,
                220,
                20,
                b -> b[0] >= 234 && b[0] <= 236 && b[1] >= 99 && b[1] <= 101);
    }

    @Test
    public void visibleCurvedNumeralBesideLongStemRemains() {
        var g = paper();
        curved(g, 30, 25);
        curved(g, 235, 100);
        rect(g, 247, 80, 2, 65);
        assertNotNull(curvedFind(g));
    }

    @Test
    public void visibleCurvedNumeralTouchingOneLongStemRemains() {
        var g = paper();
        curved(g, 30, 25);
        curved(g, 235, 100);
        rect(g, 245, 80, 1, 65);
        assertNotNull(curvedFind(g));
    }

    @Test
    public void realCurvedNumeralCrossedByThinRuleRemains() {
        var g = paper();
        curved(g, 30, 25);
        curved(g, 235, 100);
        rect(g, 195, 109, 95, 2);
        assertNotNull(curvedFind(g));
    }

    @Test
    public void gridRejectedWithShortStemExtension() {
        var g = paper();
        numeral(g, 30, 25);
        rect(g, 243, 94, 3, 38);
        for (int y : new int[] {100, 109, 118}) rect(g, 195, y, 95, 2);
        assertNull(find(g));
    }
}
