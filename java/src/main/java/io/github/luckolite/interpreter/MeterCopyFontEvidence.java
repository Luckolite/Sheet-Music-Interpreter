// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;

/** Independent complete printed staff copies may resolve an otherwise unproved OCR conflict. */
final class MeterCopyFontEvidence {
    record Copy(int measure, MeterChangeDetector.Crop crop) {}

    record Reading(String text, float topScore, float bottomScore) {}

    @FunctionalInterface
    interface FontReader {
        Reading read(int[] pixels, int width, int height, int middle, int firstLine, float gap)
                throws Exception;
    }

    private MeterCopyFontEvidence() {}

    static ScoreMeterChange resolve(
            int measure,
            Set<ScoreMeterChange> choices,
            List<Copy> copies,
            byte[] gray,
            int width,
            int height,
            FontReader font)
            throws Exception {
        if (choices == null
                || choices.size() < 2
                || copies == null
                || copies.size() < 2
                || font == null
                || gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height) return null;
        for (var choice : choices)
            if (choice == null || choice.measureIndex() != measure) return null;
        String agreement = null;
        var accepted = new java.util.ArrayList<MeterChangeDetector.Crop>();
        var groups = new java.util.ArrayList<Integer>();
        int independent = 0;
        for (var copy : copies) {
            if (copy == null
                    || copy.measure() != measure
                    || !completeStaff(gray, width, height, copy.crop())) return null;
            var crop = copy.crop();
            int sameGroup = -1;
            for (int i = 0; i < accepted.size(); i++) {
                var previous = accepted.get(i);
                float gap = Math.min(previous.gap(), crop.gap());
                if (Math.abs(previous.gap() - crop.gap()) > gap * .12f
                        || Math.abs(
                                        (previous.left()
                                                        + previous.right()
                                                        - crop.left()
                                                        - crop.right())
                                                * .5f)
                                > gap * .3f
                        || Math.abs(
                                        (previous.right() - previous.left())
                                                - (crop.right() - crop.left()))
                                > gap * .5f) return null;
                if (Math.abs(previous.firstLine() - crop.firstLine()) <= 6 * gap) {
                    if (sameGroup >= 0 && sameGroup != groups.get(i)) return null;
                    sameGroup = groups.get(i);
                }
            }
            // A refinement must match every member of one physical staff group.
            // It cannot bridge two groups or extend a group by transitive overlap.
            if (sameGroup >= 0)
                for (int i = 0; i < accepted.size(); i++)
                    if (groups.get(i) == sameGroup) {
                        var previous = accepted.get(i);
                        float gap = Math.min(previous.gap(), crop.gap());
                        if (Math.abs(previous.firstLine() - crop.firstLine()) > 6 * gap)
                            return null;
                    }
            int cw = crop.right() - crop.left(), ch = crop.bottom() - crop.top();
            int[] pixels = new int[cw * ch];
            for (int y = 0; y < ch; y++)
                for (int x = 0; x < cw; x++) {
                    int v = gray[(crop.top() + y) * width + crop.left() + x] & 255;
                    pixels[y * cw + x] = 0xff000000 | v * 0x10101;
                }
            MeterCropRaster.prepare(
                    pixels, cw, ch, crop.top(), crop.firstLine(), crop.gap(), false);
            var reading =
                    font.read(
                            pixels,
                            cw,
                            ch,
                            Math.round(crop.firstLine() + 2 * crop.gap()) - crop.top(),
                            crop.firstLine() - crop.top(),
                            crop.gap());
            if (reading == null
                    || !Float.isFinite(reading.topScore())
                    || !Float.isFinite(reading.bottomScore())) return null;
            String supported =
                    MusicalOcrEvidence.fontMeter(
                            reading.text(),
                            reading.topScore(),
                            reading.bottomScore(),
                            Set.of(),
                            Set.of());
            if (supported.isBlank() || agreement != null && !agreement.equals(supported))
                return null;
            agreement = supported;
            accepted.add(crop);
            groups.add(sameGroup < 0 ? independent++ : sameGroup);
        }
        if (independent < 2) return null;
        for (var choice : choices)
            if ((choice.numerator() + "/" + choice.denominator()).equals(agreement)) return choice;
        return null;
    }

    private static boolean completeStaff(byte[] gray, int w, int h, MeterChangeDetector.Crop c) {
        if (c == null
                || !Float.isFinite(c.gap())
                || c.gap() < 6
                || c.gap() > Math.min(w, h) / 8f
                || c.left() < 0
                || c.right() > w
                || c.top() < 0
                || c.bottom() > h
                || c.left() >= c.right()
                || c.top() >= c.bottom()
                || c.firstLine() < c.top()
                || c.firstLine() + 4 * c.gap() >= c.bottom()) return false;
        float hint = c.firstLine() + 4 * c.gap();
        var a = PrintedDirectionStaff.printedRules(gray, w, h, c.left() - c.gap(), hint, c.gap());
        var b = PrintedDirectionStaff.printedRules(gray, w, h, c.right() + c.gap(), hint, c.gap());
        if (a == null && b == null) return false;
        if (a != null
                && b != null
                && (Math.abs((a[0] - 4 * a[1]) - (b[0] - 4 * b[1])) > c.gap() * .15f
                        || Math.abs(a[1] - b[1]) > c.gap() * .06f)) return false;
        float bottom = a == null ? b[0] : b == null ? a[0] : (a[0] + b[0]) * .5f;
        float gap = a == null ? b[1] : b == null ? a[1] : (a[1] + b[1]) * .5f;
        return Math.abs((bottom - 4 * gap) - c.firstLine()) <= c.gap() * .35f
                && Math.abs(gap - c.gap()) <= c.gap() * .12f;
    }
}
