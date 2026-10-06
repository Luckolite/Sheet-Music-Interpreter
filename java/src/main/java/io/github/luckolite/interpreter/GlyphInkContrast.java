// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Soft glyph masks relative to the local paper, shared by Android and native readers. */
final class GlyphInkContrast {
    private GlyphInkContrast() {}

    static float[] mask(float[] luminance) {
        int[] histogram = new int[256];
        for (float value : luminance) histogram[Math.max(0, Math.min(255, Math.round(value)))]++;
        float inkThreshold = luminance.length * .10f;
        float paperThreshold = luminance.length * .85f;
        int paper = 255, ink = 255, sum = 0;
        boolean inkFound = false;
        for (int i = 0; i < histogram.length; i++) {
            sum += histogram[i];
            if (!inkFound && sum >= inkThreshold) {
                ink = i;
                inkFound = true;
            }
            if (sum >= paperThreshold) {
                paper = i;
                break;
            }
        }
        float[] mask = new float[luminance.length];
        // A uniform patch or paper grain is not ink. Keep real faded printing
        // soft without amplifying tiny differences into black characters.
        float range = paper - ink;
        if (range < 32) return mask;
        for (int i = 0; i < mask.length; i++)
            mask[i] = Math.max(0, Math.min(1, (paper - luminance[i]) / range));
        return mask;
    }

    private static int percentile(int[] histogram, int count, float fraction) {
        int sum = 0;
        for (int i = 0; i < histogram.length; i++) {
            sum += histogram[i];
            if (sum >= count * fraction) return i;
        }
        return 255;
    }
}
