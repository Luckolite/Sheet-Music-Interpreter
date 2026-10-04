// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;

public final class TempoChangeDetectorTest {
    @Test
    public void openingTempoLineMayStartLeftOfTheFirstBar() {
        int w = 300, h = 240;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int x = 104; x <= 116; x++) {
            gray[48 * w + x] = 0;
            gray[54 * w + x] = 0;
        }
        var token =
                new MeasureNumberReconciler.NumberToken(
                        120, 120f / w, 42f / h, 150f / w, 60f / h, 65f / w);
        assertEquals(
                List.of(new ScoreTempoChange(0, 0, 120)),
                TempoChangeDetector.detect(
                        List.of(token),
                        gray,
                        w,
                        h,
                        List.of(new MeasureRegion(.40f, .9f, .27f, .48f))));
    }

    @Test
    public void outOfBoundsLaterTempoLineDoesNotJumpToAnotherRow() {
        int w = 300, h = 400;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int x = 104; x <= 116; x++) {
            gray[248 * w + x] = 0;
            gray[254 * w + x] = 0;
        }
        var token =
                new MeasureNumberReconciler.NumberToken(
                        120, 120f / w, 242f / h, 150f / w, 260f / h, 65f / w);
        assertTrue(
                TempoChangeDetector.detect(
                                List.of(token),
                                gray,
                                w,
                                h,
                                List.of(
                                        new MeasureRegion(.4f, .9f, .2f, .35f),
                                        new MeasureRegion(.4f, .9f, .67f, .85f)))
                        .isEmpty());
    }

    @Test
    public void longDirectionStartsAtItsWordsEvenOnTheTopSystem() {
        int w = 600, h = 400;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int x = 380; x <= 393; x++) {
            gray[48 * w + x] = 0;
            gray[54 * w + x] = 0;
        }
        var changes =
                TempoChangeDetector.detect(
                        List.of(
                                new MeasureNumberReconciler.NumberToken(
                                        88, 400f / w, 42f / h, 425f / w, 60f / h, 210f / w)),
                        gray,
                        w,
                        h,
                        List.of(
                                new MeasureRegion(.05f, .32f, .17f, .40f),
                                new MeasureRegion(.34f, .65f, .17f, .40f),
                                new MeasureRegion(.67f, .95f, .17f, .40f)));
        assertEquals(List.of(new ScoreTempoChange(1, 0, 88)), changes);
    }

    @Test
    public void connectedLetterStrokesBesideDigitsAreNotAnEqualsSign() {
        int width = 300, height = 240;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int x = 104; x <= 116; x++) {
            gray[48 * width + x] = 0;
            gray[54 * width + x] = 0;
        }
        for (int y = 48; y <= 54; y++) gray[y * width + 104] = 0;
        assertTrue(
                TempoChangeDetector.detect(
                                List.of(
                                        new MeasureNumberReconciler.NumberToken(
                                                101,
                                                120f / width,
                                                42f / height,
                                                150f / width,
                                                60f / height)),
                                gray,
                                width,
                                height,
                                List.of(new MeasureRegion(.2f, .9f, .27f, .48f)))
                        .isEmpty());
    }

    @Test
    public void titleDigitsFarAboveStaffAreNeverATempoEvenWithTwoInkBars() {
        int width = 300, height = 240;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int x = 104; x <= 116; x++) {
            gray[12 * width + x] = 0;
            gray[18 * width + x] = 0;
        }
        assertTrue(
                TempoChangeDetector.detect(
                                List.of(
                                        new MeasureNumberReconciler.NumberToken(
                                                101,
                                                120f / width,
                                                6f / height,
                                                150f / width,
                                                24f / height)),
                                gray,
                                width,
                                height,
                                List.of(new MeasureRegion(.2f, .9f, .4f, .48f)))
                        .isEmpty());
    }

    @Test
    public void noteEqualsNumberMarkBecomesATempoChange() {
        int width = 300, height = 240;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 0xff);
        for (int x = 104; x <= 116; x++) {
            gray[48 * width + x] = 0;
            gray[54 * width + x] = 0;
        }
        List<ScoreTempoChange> changes =
                TempoChangeDetector.detect(
                        List.of(
                                new MeasureNumberReconciler.NumberToken(
                                        136,
                                        120f / width,
                                        42f / height,
                                        150f / width,
                                        60f / height)),
                        gray,
                        width,
                        height,
                        List.of(
                                new MeasureRegion(.20f, .55f, .27f, .48f),
                                new MeasureRegion(.56f, .90f, .27f, .48f)));

        assertEquals(List.of(new ScoreTempoChange(0, 0f, 136)), changes);
    }

    @Test
    public void barePrintedMeasureNumberIsNotATempo() {
        int width = 300, height = 240;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 0xff);
        assertTrue(
                TempoChangeDetector.detect(
                                List.of(
                                        new MeasureNumberReconciler.NumberToken(
                                                85, .18f, .20f, .22f, .25f)),
                                gray,
                                width,
                                height,
                                List.of(new MeasureRegion(.20f, .90f, .27f, .48f)))
                        .isEmpty());
    }

    @Test
    public void inStaffGlyphWithTwoHorizontalStrokesCannotBecome47Bpm() {
        int width = 300, height = 240;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 0xff);
        for (int x = 104; x <= 116; x++) {
            gray[72 * width + x] = 0;
            gray[78 * width + x] = 0;
        }
        List<ScoreTempoChange> changes =
                TempoChangeDetector.detect(
                        List.of(
                                new MeasureNumberReconciler.NumberToken(
                                        47,
                                        120f / width,
                                        66f / height,
                                        150f / width,
                                        84f / height)),
                        gray,
                        width,
                        height,
                        List.of(
                                new MeasureRegion(.2f, .9f, .27f, .40f),
                                new MeasureRegion(.2f, .9f, .45f, .60f)));
        assertTrue("Clef-like OCR must not override the selected tempo", changes.isEmpty());
    }

    private static byte[] numberedTempoPage(int width, int height, boolean equation) {
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int y : new int[] {90, 99, 108, 117, 126})
            for (int x = 30; x < width - 20; x++) gray[y * width + x] = 0;
        if (equation)
            for (int x = 104; x <= 116; x++) {
                gray[48 * width + x] = 0;
                gray[54 * width + x] = 0;
            }
        return gray;
    }

    @Test
    public void numberedStaffPageWithoutTempoEquationsRetainsEmptyResultAndPixels() {
        for (int width : new int[] {300, 600}) {
            byte[] gray = numberedTempoPage(width, 240, false), before = gray.clone();
            var tokens =
                    List.of(
                            new MeasureNumberReconciler.NumberToken(3, .18f, .20f, .22f, .25f),
                            new MeasureNumberReconciler.NumberToken(85, .35f, .20f, .39f, .25f),
                            new MeasureNumberReconciler.NumberToken(401, .58f, .20f, .62f, .25f));
            assertEquals(
                    List.of(),
                    TempoChangeDetector.detect(
                            tokens,
                            gray,
                            width,
                            240,
                            List.of(new MeasureRegion(.1f, .93f, .375f, .6f))));
            assertArrayEquals(before, gray);
        }
    }

    @Test
    public void delayedFirstTempoEquationRetainsOrderDeduplicationBitsAndPixels() {
        int width = 300, height = 240;
        byte[] gray = numberedTempoPage(width, height, true), before = gray.clone();
        var first =
                new MeasureNumberReconciler.NumberToken(
                        120, 120f / width, 42f / height, 150f / width, 60f / height, 65f / width);
        var second =
                new MeasureNumberReconciler.NumberToken(
                        136, 120f / width, 42f / height, 150f / width, 60f / height, 65f / width);
        var tokens =
                List.of(
                        new MeasureNumberReconciler.NumberToken(7, .2f, .2f, .3f, .3f),
                        new MeasureNumberReconciler.NumberToken(85, .7f, .03f, .8f, .1f),
                        first,
                        first,
                        second,
                        new MeasureNumberReconciler.NumberToken(401, .2f, .2f, .3f, .3f));
        var expected = List.of(new ScoreTempoChange(0, 0, 120), new ScoreTempoChange(0, 0, 136));
        var actual =
                TempoChangeDetector.detect(
                        tokens,
                        gray,
                        width,
                        height,
                        List.of(new MeasureRegion(.4f, .9f, .27f, .6f)));
        assertEquals(expected, actual);
        assertArrayEquals(before, gray);
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(
                    Float.floatToRawIntBits(expected.get(i).positionInMeasure()),
                    Float.floatToRawIntBits(actual.get(i).positionInMeasure()));
            assertEquals(
                    Double.doubleToRawLongBits(expected.get(i).bpm()),
                    Double.doubleToRawLongBits(actual.get(i).bpm()));
            assertEquals(
                    Double.doubleToRawLongBits(expected.get(i).beatUnit()),
                    Double.doubleToRawLongBits(actual.get(i).beatUnit()));
        }
    }
}
