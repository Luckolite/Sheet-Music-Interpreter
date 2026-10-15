// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original generated DejaVu Serif numeral rasters with synthetic bracket/crossing rule. No score pixels. */
public class CrossedNumeralSerifTest {
    private byte[] raster(String[] rows) {
        byte[] g = new byte[500 * 300];
        java.util.Arrays.fill(g, (byte) 255);
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++)
                if (rows[y].charAt(x) == '#') g[(133 + y) * 500 + 240 + x] = 0;
        for (int x = 195; x <= 305; x++) {
            int cy = 153 + Math.round((x - 250) * -.1f);
            for (int y = cy; y < cy + 3; y++) g[y * 500 + x] = 0;
        }
        for (int x = 194; x <= 239; x++) g[136 * 500 + x] = 0;
        for (int x = 261; x <= 306; x++) g[136 * 500 + x] = 0;
        return g;
    }

    private Object find(byte[] g) throws Exception {
        var m = SlopedCurveThreeTest.class.getDeclaredMethod("find", byte[].class, boolean.class);
        m.setAccessible(true);
        return m.invoke(new SlopedCurveThreeTest(), g, true);
    }

    @Test
    public void detachedTwoSerifIsNotLowerBowl() throws Exception {
        assertNull(
                find(
                        raster(
                                new String[] {
                                    "........########........",
                                    "......############......",
                                    ".....####......####.....",
                                    ".....###........###..###",
                                    ".....##..........###....",
                                    ".....##..........###....",
                                    ".................###....",
                                    ".................###....",
                                    ".................##.....",
                                    "................###.....",
                                    "................##......",
                                    "...............##.......",
                                    "..............###.......",
                                    ".............###........",
                                    "............###.........",
                                    "...........###..........",
                                    "..........##............",
                                    ".........##.......##....",
                                    "........##........##....",
                                    ".......##.......########",
                                    "......##################",
                                    "########################",
                                    "####################....",
                                    "######..................",
                                    "........................"
                                })));
    }

    @Test
    public void continuousThreeBowlSurvivesCrossing() throws Exception {
        assertNotNull(
                find(
                        raster(
                                new String[] {
                                    "..........######........",
                                    ".......###########......",
                                    "......####.....####.....",
                                    "......###.......####.###",
                                    "......##.........###....",
                                    "......##.........###....",
                                    ".................###....",
                                    "................###.....",
                                    "...............###......",
                                    "...........#####........",
                                    "...........######.......",
                                    "...............####.....",
                                    ".................###....",
                                    ".................###....",
                                    "..................###...",
                                    "..................###...",
                                    ".....##...........###...",
                                    ".....##...........###...",
                                    ".....###.........####...",
                                    ".....###........########",
                                    ".....###################",
                                    "########################",
                                    "################........",
                                    "######..................",
                                    "........................"
                                })));
    }
}
