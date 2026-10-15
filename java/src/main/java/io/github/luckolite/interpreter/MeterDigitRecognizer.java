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

    /** Sparse detector evidence is usable only with six strict agreeing slot recognitions. */
    static String readSparse(
            int[] pixels,
            int width,
            int height,
            int middle,
            float gap,
            List<String> upperReadings,
            List<String> lowerReadings,
            PortableOcr.Inference inference)
            throws Exception {
        String upper = unanimousDigit(upperReadings), lower = unanimousDigit(lowerReadings);
        if (upper.isEmpty() || lower.isEmpty()) return "";
        String verified = read(pixels, width, height, middle, gap, lower, inference);
        return verified.equals(upper + "/" + lower) ? verified : "";
    }

    private static String unanimousDigit(List<String> readings) {
        if (readings == null) return "";
        String selected = "";
        for (String value : readings) {
            if (value == null) return "";
            if (value.isBlank()) continue;
            if (!value.matches("[0-9]{1,2}") || !selected.isEmpty() && !selected.equals(value))
                return "";
            selected = value;
        }
        return selected;
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
        boolean[] rule = new boolean[height];
        boolean compact = false;
        for (int y = top; y < bottom; ) {
            int start = y;
            while (y < bottom && fullWidthInk(pixels, width, y)) y++;
            if (y > start && y - start <= Math.max(1, Math.round(gap * .18f))) {
                java.util.Arrays.fill(rule, start, y, true);
                compact = true;
            }
            if (y == start) y++;
        }

        for (int y = top; y < bottom; y++)
            for (int x = 0; x < width; x++)
                if (!rule[y] && (pixels[y * width + x] & 255) < 150) {
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
        for (int margin : compact ? new int[] {1, 3, 5} : new int[] {-1, 1, 3}) {
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

    /** Only thin ink running through both crop edges may be excluded from slot bounds.
     * Pixels stay unchanged; recognition still sees the printed ink inside each tight crop. */
    private static boolean fullWidthInk(int[] pixels, int width, int y) {
        if ((pixels[y * width] & 255) >= 150 || (pixels[y * width + width - 1] & 255) >= 150)
            return false;
        int ink = 0;
        for (int x = 0; x < width; x++) if ((pixels[y * width + x] & 255) < 150) ink++;
        return ink >= width * .9f;
    }

    /** Three strict direct readings may recover a clipped prefix in one weak detector reading. */
    static boolean permitsDirect(String direct, List<String> upperReadings) {
        if (direct == null || !direct.matches("[0-9]{1,2}/(2|4|8|16|32)")) return false;
        String upper = direct.substring(0, direct.indexOf('/'));
        int votes = 0;
        String weak = "";
        for (String reading : upperReadings) {
            if (reading == null || reading.isBlank()) continue;
            if (!reading.matches("[0-9]{1,2}")) return false;
            if (reading.equals(upper)) continue;
            if (!weak.isEmpty() && !weak.equals(reading)) return false;
            weak = reading;
            votes++;
        }
        return votes == 0 || votes == 1 && weak.length() < upper.length() && upper.endsWith(weak);
    }
}
