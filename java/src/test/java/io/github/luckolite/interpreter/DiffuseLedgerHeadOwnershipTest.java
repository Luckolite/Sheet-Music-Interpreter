// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Procedural shaded surfaces, independent ledger rules and note bodies; no score pixels. */
public class DiffuseLedgerHeadOwnershipTest {
    static final int W = 500, H = 300;
    byte[] labels = new byte[W * H], gray = new byte[W * H];

    DiffuseLedgerHeadOwnershipTest(boolean unused) {
        this();
    }

    public DiffuseLedgerHeadOwnershipTest() {
        Arrays.fill(gray, (byte) 255);
        for (int i = 0; i < 5; i++)
            for (int x = 40; x <= 460; x++) {
                gray[(100 + i * 16) * W + x] = 0;
                labels[(100 + i * 16) * W + x] = 4;
            }
        for (int y = 172; y <= 240; y++)
            for (int x = 145; x <= 255; x++)
                gray[y * W + x] = (byte) Math.round(180 - 40 * Math.cos((y - 212) * Math.PI / 8));
    }

    void oval(int x, int y, int rx, int ry, int ink, boolean hollow) {
        for (int yy = y - ry; yy <= y + ry; yy++)
            for (int xx = x - rx; xx <= x + rx; xx++) {
                double d =
                        (xx - x) * (xx - x) / (double) (rx * rx)
                                + (yy - y) * (yy - y) / (double) (ry * ry);
                if (d <= 1) {
                    labels[yy * W + xx] = 2;
                    if (ink >= 0 && (!hollow || d >= .45)) gray[yy * W + xx] = (byte) ink;
                }
            }
    }

    void ledgers(int value) {
        for (int y : new int[] {180, 196, 212})
            for (int x = 182; x <= 218; x++) gray[y * W + x] = (byte) value;
    }

    List<ScoreNoteEvent> read() {
        return OmrScoreInterpreter.extract(
                labels, gray, W, H, List.of(new MeasureRegion(.08f, .92f, .28f, .62f)));
    }

    @Test
    public void diffuseRidgesCannotOwnASemanticLedgerOval() {
        oval(200, 212, 6, 4, -1, false);
        assertTrue(read().toString(), read().isEmpty());
    }

    @Test
    public void blackLowLedgerHeadSurvives() {
        oval(200, 212, 8, 5, 0, false);
        ledgers(0);
        assertEquals(1, read().size());
    }

    @Test
    public void genuineHollowLedgerBodySurvives() {
        oval(200, 212, 11, 7, 0, true);
        ledgers(0);
        assertEquals(1, read().size());
    }

    @Test
    public void ownPrintedStemPreservesLedgerQuarter() {
        oval(200, 212, 8, 5, 0, false);
        ledgers(0);
        for (int y = 155; y <= 212; y++) for (int x = 207; x <= 209; x++) gray[y * W + x] = 0;
        assertEquals(1, read().size());
    }

    @Test
    public void faintLedgerOnWhitePaperSurvives() {
        for (int y = 172; y <= 240; y++)
            for (int x = 145; x <= 255; x++) gray[y * W + x] = (byte) 255;
        oval(200, 212, 8, 5, 180, false);
        ledgers(180);
        assertEquals(1, read().size());
    }

    @Test
    public void thinShadedLedgerPreservesAPaleBody() {
        for (int y = 172; y <= 240; y++)
            for (int x = 145; x <= 255; x++) gray[y * W + x] = (byte) 180;
        oval(200, 212, 8, 5, 145, false);
        ledgers(160);
        assertEquals(1, read().size());
    }

    @Test
    public void ordinaryNearStaffBodyIsPreserved() {
        oval(300, 177, 6, 6, 0, false);
        assertEquals(1, read().size());
    }

    void softLedgerBody(boolean hollow) {
        for (int y = 172; y <= 240; y++)
            for (int x = 145; x <= 255; x++) gray[y * W + x] = (byte) 180;
        oval(200, 212, 6, 4, -1, false);
        for (int y = 204; y <= 220; y++)
            for (int x = 189; x <= 211; x++) {
                double r = Math.sqrt((x - 200) * (x - 200) / 81.0 + (y - 212) * (y - 212) / 36.0);
                double amount =
                        hollow ? Math.max(0, 1 - Math.abs(r - .7) / .4) : Math.max(0, 1 - r);
                gray[y * W + x] = (byte) Math.round(180 - 40 * amount);
            }
        for (int y : new int[] {180, 196})
            for (int x = 182; x <= 218; x++) gray[y * W + x] = (byte) 160;
    }

    @Test
    public void softHollowBodyWithOccludedLedgerSurvives() {
        softLedgerBody(true);
        for (int x = 182; x < 194; x++) gray[212 * W + x] = (byte) 160;
        assertEquals(1, read().size());
    }

    @Test
    public void paleBodyWithBrokenThinLedgerSurvives() {
        softLedgerBody(false);
        for (int x = 182; x <= 218; x++) if (x < 191 || x > 209) gray[212 * W + x] = (byte) 160;
        assertEquals(1, read().size());
    }

    @Test
    public void thinPrintedShaftOnShadedPaperSurvives() {
        softLedgerBody(false);
        for (int y = 155; y <= 212; y++) gray[y * W + 207] = 80;
        assertEquals(1, read().size());
    }

    @Test
    public void acquisitionDoesNotModifyInputPixels() {
        oval(200, 212, 6, 4, -1, false);
        byte[] l = labels.clone(), g = gray.clone();
        read();
        assertArrayEquals(l, labels);
        assertArrayEquals(g, gray);
    }
}
