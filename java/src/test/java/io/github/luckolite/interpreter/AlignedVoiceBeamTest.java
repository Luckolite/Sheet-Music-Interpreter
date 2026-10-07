// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original offset heads have nearly collinear, discontinuous opposing stems. */
public class AlignedVoiceBeamTest {
    static final int W = 360, H = 260, G = 16;
    byte[] gray, labels;

    void rect(int l, int r, int t, int b, int kind) {
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                gray[y * W + x] = 30;
                labels[y * W + x] = (byte) kind;
            }
    }

    void clear(int l, int r, int t, int b) {
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                gray[y * W + x] = (byte) 250;
                labels[y * W + x] = 0;
            }
    }

    void oval(int cx, int cy) {
        for (int y = cy - 8; y <= cy + 8; y++)
            for (int x = cx - 12; x <= cx + 12; x++)
                if (Math.pow((x - cx) / 12., 2) + Math.pow((y - cy) / 8., 2) <= 1)
                    rect(x, x, y, y, 2);
    }

    void draw() {
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        oval(200, 88);
        oval(178, 152);
        rect(188, 190, 88, 113, 1);
        rect(188, 246, 106, 113, 1);
        rect(190, 192, 130, 152, 1);
        rect(188, 246, 130, 137, 1);
        rect(130, 270, 120, 121, 0);
    }

    int proof(boolean mirror) {
        if (!mirror)
            return InwardVoiceBeamOwnership.count(
                    gray, labels, W, H, G, 88, 152, 188, 212, 80, 96, 88, 166, 190, 144, 160, 152);
        byte[] g = gray.clone(), l = labels.clone();
        for (int i = 0; i < g.length; i++) {
            gray[g.length - 1 - i] = g[i];
            labels[l.length - 1 - i] = l[i];
        }
        return InwardVoiceBeamOwnership.count(
                gray,
                labels,
                W,
                H,
                G,
                H - 1 - 152,
                H - 1 - 88,
                W - 1 - 212,
                W - 1 - 188,
                H - 1 - 96,
                H - 1 - 80,
                H - 1 - 88,
                W - 1 - 190,
                W - 1 - 166,
                H - 1 - 160,
                H - 1 - 144,
                H - 1 - 152);
    }

    @Test
    public void independentCollinearStemsKeepSingleBeam() {
        draw();
        assertEquals(1, proof(false));
    }

    @Test
    public void mirroredIndependentStemsKeepSingleBeam() {
        draw();
        assertEquals(1, proof(true));
    }

    @Test
    public void staffRulesDoNotJoinStems() {
        draw();
        rect(130, 270, 116, 117, 0);
        assertEquals(1, proof(false));
    }

    @Test
    public void sourcePlanesAreReadOnly() {
        draw();
        byte[] g = gray.clone(), l = labels.clone();
        assertEquals(1, proof(false));
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    @Test
    public void realContinuousStemKeepsSecondBeam() {
        draw();
        rect(188, 190, 114, 137, 1);
        assertEquals(-1, proof(false));
    }

    @Test
    public void singleConnectingShaftPixelAbstains() {
        draw();
        rect(189, 189, 114, 129, 1);
        assertEquals(-1, proof(false));
    }

    @Test
    public void missingOwnShaftAbstains() {
        draw();
        clear(185, 194, 99, 103);
        assertEquals(-1, proof(false));
    }

    @Test
    public void missingOtherShaftAbstains() {
        draw();
        clear(185, 196, 138, 142);
        assertEquals(-1, proof(false));
    }

    @Test
    public void missingOtherRailAbstains() {
        draw();
        clear(186, 248, 130, 137);
        assertEquals(-1, proof(false));
    }

    @Test
    public void missingOwnRailAbstains() {
        draw();
        clear(186, 248, 106, 113);
        assertEquals(-1, proof(false));
    }

    @Test
    public void genuineThirdRailAbstains() {
        draw();
        rect(188, 246, 117, 123, 1);
        assertEquals(-1, proof(false));
    }

    @Test
    public void touchingBandsAbstain() {
        draw();
        rect(188, 246, 114, 129, 1);
        assertEquals(-1, proof(false));
    }

    @Test
    public void broadBlobAbstains() {
        draw();
        rect(170, 246, 106, 137, 1);
        assertEquals(-1, proof(false));
    }

    @Test
    public void thinRulesAloneAbstain() {
        draw();
        clear(186, 248, 106, 113);
        rect(130, 270, 109, 110, 0);
        assertEquals(-1, proof(false));
    }

    @Test
    public void detachedForeignStrokeAbstains() {
        draw();
        clear(185, 196, 138, 144);
        assertEquals(-1, proof(false));
    }

    @Test
    public void invalidPlaneAbstains() {
        draw();
        labels = new byte[3];
        assertEquals(-1, proof(false));
    }
}
