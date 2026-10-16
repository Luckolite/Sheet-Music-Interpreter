// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rectangle fixtures; no score pixels or font dependencies. */
public class RepeatedTupletStemEvidenceTest {
    private static final int W = 200, H = 200;

    private byte[] paper() {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        return gray;
    }

    private void rect(byte[] gray, int left, int top, int right, int bottom, int value) {
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) gray[y * W + x] = (byte) value;
    }

    private boolean retains(byte[] gray, boolean[] covered) throws Exception {
        Method method = RepeatedTupletNumeral.class.getDeclaredMethod(
                "visibleBeyondLongStem", byte[].class, int.class, int.class,
                int.class, int.class, int.class, int.class, float.class, boolean[].class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, gray, W, H, 100, 100, 12, 17, 13.7f, covered);
    }

    @Test public void upperStemEndingAtBeamIsNotNumeralEvidence() throws Exception {
        byte[] gray = paper();
        rect(gray, 107, 70, 110, 114, 0);
        rect(gray, 107, 110, 160, 114, 0);
        assertFalse(retains(gray, new boolean[17]));
    }

    @Test public void lowerStemStartingAtBeamIsNotNumeralEvidence() throws Exception {
        byte[] gray = paper();
        rect(gray, 107, 102, 110, 145, 0);
        rect(gray, 107, 102, 160, 106, 0);
        assertFalse(retains(gray, new boolean[17]));
    }

    @Test public void grayStemStillRequiresIndependentGlyphInk() throws Exception {
        byte[] gray = paper();
        rect(gray, 107, 70, 110, 114, 170);
        rect(gray, 107, 110, 160, 114, 170);
        assertFalse(retains(gray, new boolean[17]));
    }

    @Test public void independentGlyphInkBesideStemSurvives() throws Exception {
        byte[] gray = paper();
        rect(gray, 107, 70, 110, 114, 0);
        rect(gray, 107, 110, 160, 114, 0);
        rect(gray, 100, 102, 102, 104, 0);
        assertTrue(retains(gray, new boolean[17]));
    }

    @Test public void maskedBeamInkCannotSupplyGlyphEvidence() throws Exception {
        byte[] gray = paper();
        rect(gray, 107, 70, 110, 114, 0);
        rect(gray, 100, 102, 102, 104, 0);
        boolean[] covered = new boolean[17];
        covered[2] = covered[3] = covered[4] = true;
        assertFalse(retains(gray, covered));
    }

    @Test public void compactGlyphWithoutExternalShaftIsPreserved() throws Exception {
        byte[] gray = paper();
        rect(gray, 107, 100, 110, 114, 0);
        assertTrue(retains(gray, new boolean[17]));
    }
}
