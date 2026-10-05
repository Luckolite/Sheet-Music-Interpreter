// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original equation with a separate thin stroke inside padded digit bounds. */
public class TempoSlurPaddingTest {
    final int W = 320, H = 250;
    final byte[] gray = new byte[W * H];

    public TempoSlurPaddingTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y = 90; y <= 110; y++)
            for (int x = 150; x <= 163; x++)
                if (x <= 152 || y <= 92 || y >= 108) gray[y * W + x] = 0;
        for (int x = 127; x <= 141; x++) {
            gray[100 * W + x] = 0;
            gray[106 * W + x] = 0;
        }
        for (int y = 73; y <= 110; y++) gray[y * W + 113] = 0;
        for (int y = 104; y <= 116; y++)
            for (int x = 97; x <= 113; x++)
                if (Math.pow((x - 105) / 8d, 2) + Math.pow((y - 110) / 6d, 2) <= 1)
                    gray[y * W + x] = 0;
        for (int y = 108; y <= 112; y++)
            for (int x = 119; x <= 123; x++)
                if ((x - 121) * (x - 121) + (y - 110) * (y - 110) <= 4) gray[y * W + x] = 0;
    }

    List<ScoreTempoChange> detect() {
        return TempoChangeDetector.detect(
                List.of(
                        new MeasureNumberReconciler.NumberToken(
                                60, 148f / W, 86f / H, 168f / W, 133f / H)),
                gray,
                W,
                H,
                List.of(new MeasureRegion(.25f, .95f, .58f, .8f)));
    }

    void stroke(int top, int bottom) {
        for (int y = top; y <= bottom; y++) for (int x = 148; x <= 168; x++) gray[y * W + x] = 0;
    }

    @Test
    public void lowerSlurDoesNotChangeDottedQuarterIntoQuarter() {
        stroke(129, 132);
        assertEquals(1.5, detect().get(0).beatUnit(), 0);
        assertEquals(90, detect().get(0).bpm(), 0);
    }

    @Test
    public void cleanDigitsKeepTheirBeatUnit() {
        assertEquals(1.5, detect().get(0).beatUnit(), 0);
    }

    @Test
    public void sourceInkIsUnchanged() {
        stroke(129, 132);
        byte[] original = gray.clone();
        detect();
        assertArrayEquals(original, gray);
    }

    Object bounds() throws Exception {
        var m =
                TempoChangeDetector.class.getDeclaredMethod(
                        "printedDigitBounds",
                        MeasureNumberReconciler.NumberToken.class,
                        byte[].class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        return m.invoke(
                null,
                new MeasureNumberReconciler.NumberToken(60, 148f / W, 60f / H, 168f / W, 133f / H),
                gray,
                W,
                H);
    }

    @Test
    public void thinUpperStrokeIsExcludedToo() throws Exception {
        stroke(70, 72);
        var b = (MeasureNumberReconciler.NumberToken) bounds();
        assertEquals(90f / H, b.top(), .00001f);
    }

    @Test
    public void secondTallBandIsNotSilentlyDiscarded() throws Exception {
        stroke(123, 132);
        var b = (MeasureNumberReconciler.NumberToken) bounds();
        assertEquals(133f / H, b.bottom(), .00001f);
    }

    @Test
    public void nearbyBandCouldBePartOfDigits() throws Exception {
        stroke(113, 114);
        var b = (MeasureNumberReconciler.NumberToken) bounds();
        assertEquals(115f / H, b.bottom(), .00001f);
    }

    @Test
    public void paddedDigitThresholdRetainsCompleteTempoAndCallerPixels() {
        for (int shade : new int[] {125, 126}) {
            for (int y = 129; y <= 132; y++)
                for (int x = 148; x <= 168; x++) gray[y * W + x] = (byte) shade;
            byte[] original = gray.clone();
            var expected = List.of(new ScoreTempoChange(0, 0, 90, 1.5));
            var actual = detect();
            assertEquals(expected, actual);
            assertArrayEquals(original, gray);
            assertEquals(
                    Float.floatToRawIntBits(0f),
                    Float.floatToRawIntBits(actual.get(0).positionInMeasure()));
            assertEquals(
                    Double.doubleToRawLongBits(90d),
                    Double.doubleToRawLongBits(actual.get(0).bpm()));
            assertEquals(
                    Double.doubleToRawLongBits(1.5d),
                    Double.doubleToRawLongBits(actual.get(0).beatUnit()));
            assertEquals(actual, detect());
            assertArrayEquals(original, gray);
        }
    }
}
