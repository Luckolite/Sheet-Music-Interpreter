// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Recognition-only fallback for two already located musical numeral slots. */
final class MeterDigitRecognizer {
    private MeterDigitRecognizer() {}

    static String read(
            int[] pixels,
            int width,
            int height,
            int middle,
            float gap,
            String corroboratedLower,
            PortableOcr.Inference inference)
            throws Exception {
        if (pixels == null
                || width < 2
                || height < 4
                || (long) width * height != pixels.length
                || middle < 2
                || middle > height - 2
                || !Float.isFinite(gap)
                || gap < 3
                || inference == null
                || corroboratedLower == null
                || !corroboratedLower.matches("2|4|8|16|32")) return "";
        String upper = slot(pixels, width, height, 0, middle, gap, inference);
        if (upper.isEmpty()) return "";
        String lower = slot(pixels, width, height, middle, height, gap, inference);
        if (!lower.equals(corroboratedLower)) return "";
        return MeterOcrEvidence.singleReading(List.of(upper + "/" + lower));
    }

    private static String slot(
            int[] pixels,
            int width,
            int height,
            int top,
            int bottom,
            float gap,
            PortableOcr.Inference inference)
            throws Exception {
        int left = width, right = -1, first = bottom, last = -1, ink = 0;
        for (int y = top; y < bottom; y++)
            for (int x = 0; x < width; x++)
                if ((pixels[y * width + x] & 255) < 150) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    first = Math.min(first, y);
                    last = Math.max(last, y);
                    ink++;
                }
        if (ink < gap * 2
                || last - first + 1 < gap * 1.2f
                || right - left + 1 < gap * .3f
                || right - left + 1 > gap * 2.6f) return "";
        String selected = "";
        for (int margin : new int[] {-1, 1, 3}) {
            int x0 = margin < 0 ? 0 : Math.max(0, left - margin);
            int x1 = margin < 0 ? width : Math.min(width, right + margin + 1);
            int y0 = margin < 0 ? top : Math.max(top, first - margin);
            int y1 = margin < 0 ? bottom : Math.min(bottom, last + margin + 1);
            int content = (int) Math.ceil(48d * (x1 - x0) / (y1 - y0)),
                    padded = Math.max(320, content);
            if (content < 1 || content > 4096) return "";
            var prediction =
                    inference.recognize(
                            PortableOcr.normalize(
                                    pixels, width, height, x0, y0, x1, y1, content, 48, padded,
                                    false),
                            padded,
                            48);
            var decoded = OcrCtcDecoder.decode(prediction, inference.dictionary(), 0);
            if (!Float.isFinite(decoded.confidence())
                    || decoded.confidence() < .98f
                    || !decoded.text().matches("[0-9]{1,2}")) return "";
            if (!selected.isEmpty() && !selected.equals(decoded.text())) return "";
            selected = decoded.text();
        }
        return selected;
    }
}
