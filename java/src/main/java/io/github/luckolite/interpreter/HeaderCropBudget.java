/* SPDX-License-Identifier: Apache-2.0 */
package io.github.luckolite.interpreter;

/** Keep both width variants of an original PDF row within the OCR bitmap budget. */
public final class HeaderCropBudget {
    private HeaderCropBudget() {}

    public static final long MAX_PIXELS = 20_000_000;

    public record Size(int width, int height) {}

    public record Plan(int left, int top, int right, int bottom, int width, int height) {
        public double scaleX() {
            return (double) width / (right - left);
        }

        public double scaleY() {
            return (double) height / (bottom - top);
        }

        public boolean reduced() {
            return width != right - left || height != bottom - top;
        }
    }

    public static Size fit(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Invalid crop dimensions");
        long expanded = Math.round(width * 1.5) * height;
        if (expanded <= MAX_PIXELS) return new Size(width, height);
        double ratio = Math.sqrt((double) MAX_PIXELS / expanded);
        int w = Math.max(1, (int) Math.floor(width * ratio)),
                h = Math.max(1, (int) Math.floor(height * ratio));
        if (Math.round(w * 1.5) > MAX_PIXELS) w = (int) Math.floor(MAX_PIXELS / 1.5);
        h = (int) Math.min(h, MAX_PIXELS / Math.round(w * 1.5));
        return new Size(w, h);
    }

    public static Plan plan(
            int pageWidth,
            int pageHeight,
            float left,
            float top,
            float right,
            float bottom,
            int multiplier,
            String mode) {
        if (pageWidth < 1
                || pageHeight < 1
                || !(multiplier == 1 || multiplier == 2 || multiplier == 4)
                || !("tight".equals(mode) || "horizontal".equals(mode))
                || !Float.isFinite(left)
                || !Float.isFinite(top)
                || !Float.isFinite(right)
                || !Float.isFinite(bottom)
                || right <= left
                || bottom <= top)
            throw new IllegalArgumentException("Invalid original crop geometry");
        float padding = Math.max(4, (bottom - top) * (mode.equals("tight") ? .1f : .3f));
        if (mode.equals("horizontal")) {
            float extra = Math.max(4, bottom - top) - padding;
            left -= extra;
            right += extra;
        }
        int l = Math.max(0, (int) ((left - padding) * multiplier)),
                t = Math.max(0, (int) ((top - padding) * multiplier));
        int r =
                Math.min(
                        Math.multiplyExact(pageWidth, multiplier),
                        (int) ((right + padding) * multiplier));
        int b =
                Math.min(
                        Math.multiplyExact(pageHeight, multiplier),
                        (int) ((bottom + padding) * multiplier));
        if (r <= l || b <= t) throw new IllegalArgumentException("Empty original crop");
        // Apply the highest-resolution budget consistently to every requested scale.
        // Lower scales remain distinct rather than becoming duplicate capped views.
        int l4 = Math.max(0, (int) ((left - padding) * 4)),
                t4 = Math.max(0, (int) ((top - padding) * 4));
        int r4 = Math.min(Math.multiplyExact(pageWidth, 4), (int) ((right + padding) * 4));
        int b4 = Math.min(Math.multiplyExact(pageHeight, 4), (int) ((bottom + padding) * 4));
        Size largest = fit(r4 - l4, b4 - t4);
        int w = Math.max(1, (int) Math.floor((r - l) * (double) largest.width() / (r4 - l4)));
        int h = Math.max(1, (int) Math.floor((b - t) * (double) largest.height() / (b4 - t4)));
        Size size = fit(w, h);
        return new Plan(l, t, r, b, size.width(), size.height());
    }
}
