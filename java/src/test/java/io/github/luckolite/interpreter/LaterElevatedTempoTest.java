// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original printed rule and quarter-note equations. */
public class LaterElevatedTempoTest {
    final int W = 480, H = 500;
    byte[] g = new byte[W * H];

    LaterElevatedTempoTest(boolean unused) {
        Arrays.fill(g, (byte) 255);
    }

    public LaterElevatedTempoTest() {
        this(false);
    }

    void box(int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = 0;
    }

    List<ScoreTempoChange> read(boolean staff, boolean note, int top, int left) {
        if (staff) for (int y = 250; y <= 298; y += 12) box(30, y, 420, 1);
        box(left - 18, top + 6, 12, 2);
        box(left - 18, top + 12, 12, 2);
        box(left, top, 3, 18);
        box(left + 12, top, 3, 18);
        if (note) {
            box(left - 30, top - 12, 2, 28);
            for (int y = top + 9; y <= top + 16; y++)
                for (int x = left - 39; x <= left - 29; x++)
                    if (Math.pow((x - (left - 34)) / 5d, 2) + Math.pow((y - (top + 13)) / 4d, 2)
                            <= 1) g[y * W + x] = 0;
        }
        return TempoChangeDetector.detect(
                List.of(
                        new MeasureNumberReconciler.NumberToken(
                                96,
                                left / (float) W,
                                top / (float) H,
                                (left + 16f) / W,
                                (top + 18f) / H,
                                (left - 40f) / W)),
                g,
                W,
                H,
                List.of(
                        new MeasureRegion(30f / W, 450f / W, 35f / H, 105f / H),
                        new MeasureRegion(180f / W, 450f / W, 244f / H, 304f / H)));
    }

    @Test
    public void completeElevatedEquationFindsLaterSystem() {
        assertEquals(List.of(new ScoreTempoChange(1, 0, 96)), read(true, true, 145, 220));
    }

    @Test
    public void absentBeatGlyphDoesNotExpand() {
        assertTrue(read(true, false, 145, 220).isEmpty());
    }

    @Test
    public void absentPrintedStaffDoesNotExpand() {
        assertTrue(read(false, true, 145, 220).isEmpty());
    }

    @Test
    public void remoteEquationDoesNotExpand() {
        assertTrue(read(true, true, 125, 220).isEmpty());
    }

    @Test
    public void middleOfRowDoesNotBorrowStart() {
        assertTrue(read(true, true, 145, 350).isEmpty());
    }

    @Test
    public void insideStaffRejected() {
        assertTrue(read(true, true, 258, 220).isEmpty());
    }

    @Test
    public void ambiguousPrintedRowsRejected() throws Exception {
        read(false, true, 187, 220);
        for (int top : new int[] {220, 280})
            for (int y = top; y <= top + 48; y += 12) box(30, y, 420, 1);
        var token =
                new MeasureNumberReconciler.NumberToken(
                        96, 220f / W, 187f / H, 236f / W, 205f / H, 180f / W);
        var method =
                TempoChangeDetector.class.getDeclaredMethod(
                        "provedElevatedSystemTempo",
                        MeasureNumberReconciler.NumberToken.class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        List.class);
        method.setAccessible(true);
        assertEquals(
                -1,
                method.invoke(
                        null,
                        token,
                        g,
                        W,
                        H,
                        202,
                        List.of(
                                new MeasureRegion(180f / W, 450f / W, 220f / H, 268f / H),
                                new MeasureRegion(180f / W, 450f / W, 280f / H, 328f / H))));
    }
}
