// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Locale;

/** Complete-word OCR agreement for independently located printed dynamic bodies. */
final class PaperDynamicWord {
    private static final class LiteralPattern {
        private static final java.util.regex.Pattern LEVEL =
                java.util.regex.Pattern.compile("ppp|pp|p|mp|mf|fff|ff|f");
    }

    private PaperDynamicWord() {}

    record Crop(int left, int top, int right, int bottom) {}

    static Crop crop(PlayingTechniqueDetector.Word box, int width, int height) {
        if (width <= 0 || height <= 0 || !valid(box)) return null;
        int pad = Math.max(3, Math.round((box.bottom() - box.top()) * height * .25f));
        return new Crop(
                Math.max(0, Math.round(box.left() * width) - pad),
                Math.max(0, Math.round(box.top() * height) - pad),
                Math.min(width, Math.round(box.right() * width) + pad),
                Math.min(height, Math.round(box.bottom() * height) + pad));
    }

    static boolean unclaimed(
            PlayingTechniqueDetector.Word box, List<PlayingTechniqueDetector.Word> existing) {
        if (!valid(box)) return false;
        for (var old : existing)
            if (valid(old)
                    && old.left() < box.right()
                    && old.right() > box.left()
                    && old.top() < box.bottom()
                    && old.bottom() > box.top()) return false;
        return true;
    }

    static PlayingTechniqueDetector.Word agree(
            PlayingTechniqueDetector.Word box,
            OcrText twice,
            OcrText thrice,
            Crop crop,
            int width,
            int height,
            List<PlayingTechniqueDetector.Word> existing) {
        if (!unclaimed(box, existing) || crop == null || width <= 0 || height <= 0) return null;
        var a = reading(twice, crop, 2, width, height);
        var b = reading(thrice, crop, 3, width, height);
        if (a == null
                || b == null
                || !a.text().equals(b.text())
                || !covers(a, box)
                || !covers(b, box)) return null;
        float w = box.right() - box.left(), h = box.bottom() - box.top();
        if (Math.abs(a.left() + a.right() - b.left() - b.right()) > w * .5f
                || Math.abs(a.top() + a.bottom() - b.top() - b.bottom()) > h * .5f) return null;
        return new PlayingTechniqueDetector.Word(
                a.text(), box.left(), box.top(), box.right(), box.bottom());
    }

    private static PlayingTechniqueDetector.Word reading(
            OcrText text, Crop crop, int scale, int width, int height) {
        if (text == null) return null;
        String literal = text.text().trim().toLowerCase(Locale.ROOT);
        if (!LiteralPattern.LEVEL.matcher(literal).matches()) return null;
        PlayingTechniqueDetector.Word result = null;
        for (var block : text.blocks())
            for (var line : block.lines())
                for (var element : line.elements()) {
                    if (result != null
                            || !line.text().trim().equalsIgnoreCase(literal)
                            || !element.text().trim().equalsIgnoreCase(literal)
                            || element.box() == null) return null;
                    var r = element.box();
                    if (r.left < 0
                            || r.top < 0
                            || r.right > (crop.right() - crop.left()) * scale
                            || r.bottom > (crop.bottom() - crop.top()) * scale
                            || r.right <= r.left
                            || r.bottom <= r.top) return null;
                    result =
                            new PlayingTechniqueDetector.Word(
                                    literal,
                                    (crop.left() + r.left / (float) scale) / width,
                                    (crop.top() + r.top / (float) scale) / height,
                                    (crop.left() + r.right / (float) scale) / width,
                                    (crop.top() + r.bottom / (float) scale) / height);
                }
        return result;
    }

    private static boolean covers(
            PlayingTechniqueDetector.Word read, PlayingTechniqueDetector.Word box) {
        float w = box.right() - box.left(), h = box.bottom() - box.top();
        return Math.min(read.right(), box.right()) - Math.max(read.left(), box.left()) >= w * .85f
                && Math.min(read.bottom(), box.bottom()) - Math.max(read.top(), box.top())
                        >= h * .85f
                && read.right() - read.left() <= w * 1.55f
                && read.bottom() - read.top() <= h * 1.7f;
    }

    private static boolean valid(PlayingTechniqueDetector.Word box) {
        return box != null
                && Float.isFinite(box.left())
                && Float.isFinite(box.right())
                && Float.isFinite(box.top())
                && Float.isFinite(box.bottom())
                && box.left() >= 0
                && box.top() >= 0
                && box.right() <= 1
                && box.bottom() <= 1
                && box.left() < box.right()
                && box.top() < box.bottom();
    }
}
