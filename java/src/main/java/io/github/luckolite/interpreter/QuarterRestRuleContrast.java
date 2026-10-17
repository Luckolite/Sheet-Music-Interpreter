// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A bounded quarter body may remain darker than every independently proved staff crossing. */
final class QuarterRestRuleContrast {
    record View(
            byte[] gray,
            int width,
            int height,
            float top,
            float bottom,
            float gap,
            int left,
            int right,
            int first,
            int last) {}

    private QuarterRestRuleContrast() {}

    static View view(
            byte[] original,
            int width,
            int height,
            SixteenthRestDetector.Staff staff,
            int left,
            int right,
            int first,
            int last,
            int lineTop,
            boolean[] staffLines) {
        if (original == null
                || width <= 0
                || height <= 0
                || (long) width * height != original.length
                || staff == null
                || !Float.isFinite(staff.top())
                || !Float.isFinite(staff.bottom())
                || !Float.isFinite(staff.gap())
                || staff.gap() < 4
                || staffLines == null
                || lineTop < 0
                || (long) lineTop + staffLines.length > height
                || left < 0
                || right >= width
                || left > right
                || first < 0
                || last >= height
                || first > last) return null;
        float gap = staff.gap();
        int bodyWidth = right - left + 1, bodyHeight = last - first + 1;
        if (bodyWidth < gap * .7f
                || bodyWidth > gap * 1.6f
                || bodyHeight < gap * 2.1f
                || bodyHeight > gap * 3.6f) return null;
        // The unchanged two-flank proof requires a unique aligned five-rule frame.
        float[] frame = QuarterRestLocalBody.frame(original, width, height, left, right, staff);
        if (frame == null) return null;
        float bottom = frame[0], localGap = frame[1], top = bottom - 4 * localGap;
        if (first < top + localGap * .2f
                || first > top + localGap * 1.1f
                || last + 1 < Math.floor(bottom - localGap * 1.2f)
                || last > bottom - localGap * .1f) return null;
        int margin = Math.max(4, Math.round(localGap * 2));
        int x0 = left - margin, x1 = right + margin;
        int y0 = Math.max(0, (int) Math.floor(top - localGap * .5f));
        int y1 = Math.min(height - 1, (int) Math.ceil(bottom + localGap * .5f));
        if (x0 < 0 || x1 >= width || y0 > first || y1 < last) return null;
        int cw = x1 - x0 + 1, ch = y1 - y0 + 1;
        byte[] gray = new byte[cw * ch];
        for (int y = 0; y < ch; y++)
            System.arraycopy(original, (y0 + y) * width + x0, gray, y * cw, cw);
        int[] owned = new int[5];
        boolean[] crossed = new boolean[5];
        int blank = Math.max(2, Math.round(localGap * .5f));
        for (int y = first; y <= last; y++) {
            // Preserve every observed non-rule row; recover only ink the caller's mask erased.
            if (y < lineTop || y - lineTop >= staffLines.length || !staffLines[y - lineTop])
                continue;
            int rule = Math.round((y - top) / localGap);
            if (rule < 0 || rule >= 5 || Math.abs(y - (top + rule * localGap)) > localGap * .35f)
                continue;
            float l = median(original, width, y, x0, left - blank);
            float r = median(original, width, y, right + blank + 1, x1 + 1);
            if (l >= 225 && r >= 225) continue;
            crossed[rule] = true;
            // A saturated or one-sided stripe cannot supply separable body evidence.
            float background = Math.min(l, r);
            if (background < 16 || l >= 225 || r >= 225) return null;
            int supported = 0;
            for (int x = left; x <= right; x++) {
                int ink = original[y * width + x] & 255;
                int value =
                        background - ink >= 12
                                ? Math.min(255, Math.round(ink * 255f / background))
                                : 255;
                gray[(y - y0) * cw + x - x0] = (byte) value;
                if (value < 170) supported++;
            }
            owned[rule] = Math.max(owned[rule], supported);
        }
        int count = 0;
        for (int rule = 0; rule < 5; rule++)
            if (crossed[rule]) {
                if (owned[rule] < 2) return null;
                count++;
            }
        if (count < 2
                || !CompactQuarterRestContour.connectedInk(
                        gray, cw, left - x0, right - x0, first - y0, last - y0)) return null;
        return new View(
                gray,
                cw,
                ch,
                staff.top() - y0,
                staff.bottom() - y0,
                staff.gap(),
                left - x0,
                right - x0,
                first - y0,
                last - y0);
    }

    private static float median(byte[] gray, int width, int y, int left, int right) {
        if (right <= left) return 255;
        int[] histogram = new int[256];
        for (int x = left; x < right; x++) histogram[gray[y * width + x] & 255]++;
        int count = 0, target = (right - left + 1) / 2;
        for (int i = 0; i < 256; i++) if ((count += histogram[i]) >= target) return i;
        return 255;
    }
}
