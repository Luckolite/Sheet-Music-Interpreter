// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Shared CPU OCR pipeline. No platform bitmap, image library or SDK result types.
 * Symbol boxes are CTC alignment estimates, not measured ink contours. */
public final class PortableOcr {
    public static final String REVISION = "portable-ocr-1";

    public interface Inference {
        float[][] detect(float[] chw, int width, int height) throws Exception;

        float[][] recognize(float[] chw, int width, int height) throws Exception;

        List<String> dictionary();
    }

    private final Inference inference;

    public PortableOcr(Inference inference) {
        this.inference = Objects.requireNonNull(inference);
    }

    public OcrText read(int[] argb, int width, int height) throws Exception {
        if (width < 1
                || height < 1
                || (long) width * height > 20_000_000
                || argb.length != (long) width * height)
            throw new IllegalArgumentException("Invalid OCR raster");
        byte[] gray = opaqueGrayCopy(argb, width, height);
        return readPixels(gray == null ? argb : null, gray, width, height);
    }

    /** Snapshot only opaque equal-channel input; all other rasters retain the ARGB path. */
    private static byte[] opaqueGrayCopy(int[] pixels, int width, int height) {
        // Cheap rejection only; the copy still validates every pixel before gray dispatch.
        if (Thread.currentThread().isInterrupted()) return null;
        for (int sample = 0; sample < 3; sample++) {
            int index = sample == 0 ? 0 : sample == 1 ? pixels.length - 1 : pixels.length / 2;
            int pixel = pixels[index], value = pixel & 255;
            if (pixel != (0xff000000 | value * 0x00010101)) return null;
        }
        byte[] gray = new byte[pixels.length];
        for (int y = 0; y < height; y++) {
            // Leave cancellation and its exception type to the original normalization path.
            if (Thread.currentThread().isInterrupted()) return null;
            int end = (y + 1) * width;
            for (int i = y * width; i < end; i++) {
                int pixel = pixels[i], value = pixel & 255;
                if (pixel != (0xff000000 | value * 0x00010101)) return null;
                gray[i] = (byte) value;
            }
        }
        return gray;
    }

    /** Opaque unsigned grayscale; no ARGB storage or alpha composition is required. */
    public OcrText readGray(byte[] gray, int width, int height) throws Exception {
        if (width < 1
                || height < 1
                || (long) width * height > 20_000_000
                || gray.length != (long) width * height)
            throw new IllegalArgumentException("Invalid OCR raster");
        return readPixels(null, gray, width, height);
    }

    /** Materialize only at a platform/remote boundary that still requires opaque ARGB. */
    public static int[] opaqueGrayPixels(byte[] gray) {
        int[] pixels = new int[gray.length];
        for (int i = 0; i < pixels.length; i++) {
            int value = gray[i] & 255;
            pixels[i] = 0xff000000 | (value << 16) | (value << 8) | value;
        }
        return pixels;
    }

    private OcrText readPixels(int[] argb, byte[] gray, int width, int height) throws Exception {
        double scale = Math.min(1, 2048.0 / Math.max(width, height));
        int dw = Math.max(32, (int) Math.round(width * scale / 32) * 32);
        int dh = Math.max(32, (int) Math.round(height * scale / 32) * 32);
        float[][] prediction =
                inference.detect(
                        gray == null
                                ? normalize(
                                        argb, width, height, 0, 0, width, height, dw, dh, dw, true)
                                : normalizeGray(
                                        gray, width, height, 0, 0, width, height, dw, dh, dw, true),
                        dw,
                        dh);
        var boxes = new ArrayList<OcrText.Box>();
        for (var region : regions(prediction, width, height))
            boxes.addAll(
                    gray == null
                            ? splitStackedRows(argb, width, region)
                            : splitStackedGrayRows(gray, width, region));
        var lines = new ArrayList<OcrText.Line>();
        for (var box : boxes) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            int contentWidth = Math.max(1, (int) Math.ceil(48.0 * box.width() / box.height()));
            if (contentWidth > 4096) continue;
            int rw = Math.max(320, contentWidth);
            var probabilities =
                    inference.recognize(
                            gray == null
                                    ? normalize(
                                            argb,
                                            width,
                                            height,
                                            box.left,
                                            box.top,
                                            box.right,
                                            box.bottom,
                                            contentWidth,
                                            48,
                                            rw,
                                            false)
                                    : normalizeGray(
                                            gray,
                                            width,
                                            height,
                                            box.left,
                                            box.top,
                                            box.right,
                                            box.bottom,
                                            contentWidth,
                                            48,
                                            rw,
                                            false),
                            rw,
                            48);
            var decoded = OcrCtcDecoder.decode(probabilities, inference.dictionary(), 0);
            if (decoded.confidence() < .5f || decoded.text().isBlank()) continue;
            var words = new ArrayList<OcrText.Element>();
            for (var word : words(decoded, box, probabilities.length, rw, contentWidth)) {
                var symbols = new ArrayList<OcrText.Symbol>();
                for (var symbol : word.symbols())
                    symbols.add(
                            new OcrText.Symbol(
                                    symbol.text(), inkBounds(argb, gray, width, symbol.box())));
                words.add(
                        new OcrText.Element(
                                word.text(), inkBounds(argb, gray, width, word.box()), symbols));
            }
            if (!words.isEmpty()) lines.add(new OcrText.Line(decoded.text(), box, words));
        }
        lines.sort(
                Comparator.comparingInt((OcrText.Line l) -> l.box().top)
                        .thenComparingInt(l -> l.box().left));
        var blocks = new ArrayList<OcrText.Block>();
        var text = new StringBuilder();
        for (var line : lines) {
            if (text.length() > 0) text.append('\n');
            text.append(line.text());
            blocks.add(new OcrText.Block(line.text(), line.box(), List.of(line)));
        }
        return new OcrText(text.toString(), blocks);
    }

    /** Half-pixel bilinear resize, white alpha composition and explicit CHW normalization. */
    public static float[] normalize(
            int[] pixels,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            int outWidth,
            int outHeight,
            int paddedWidth,
            boolean detector) {
        if (width <= 0
                || height <= 0
                || (long) width * height != pixels.length
                || left < 0
                || top < 0
                || right > width
                || bottom > height
                || right <= left
                || bottom <= top
                || outWidth < 1
                || outHeight < 1
                || paddedWidth < outWidth
                || (long) paddedWidth * outHeight > 5_000_000)
            throw new IllegalArgumentException("Invalid OCR resize");
        float[] output = new float[paddedWidth * outHeight * 3];
        float[] mean = {.485f, .456f, .406f}, std = {.229f, .224f, .225f};
        int[] xs0 = new int[outWidth], xs1 = new int[outWidth];
        float[] fractions = new float[outWidth];
        for (int x = 0; x < outWidth; x++) {
            double sx =
                    Math.max(
                            left,
                            Math.min(right - 1, left + (x + .5) * (right - left) / outWidth - .5));
            xs0[x] = (int) sx;
            xs1[x] = Math.min(right - 1, xs0[x] + 1);
            fractions[x] = (float) (sx - xs0[x]);
        }
        for (int y = 0; y < outHeight; y++) {
            if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException();
            double sy =
                    Math.max(
                            top,
                            Math.min(bottom - 1, top + (y + .5) * (bottom - top) / outHeight - .5));
            int y0 = (int) sy, y1 = Math.min(bottom - 1, y0 + 1);
            float fy = (float) (sy - y0);
            int row0 = y0 * width, row1 = y1 * width;
            for (int x = 0; x < outWidth; x++) {
                int x0 = xs0[x], x1 = xs1[x];
                float fx = fractions[x];
                int p00 = pixels[row0 + x0],
                        p01 = pixels[row0 + x1],
                        p10 = pixels[row1 + x0],
                        p11 = pixels[row1 + x1];
                for (int c = 0; c < 3; c++) {
                    // Paddle OCR's reference reader supplies BGR, not RGB.
                    int shift = c * 8;
                    float a = channel(p00, shift), b = channel(p01, shift);
                    float d = channel(p10, shift), e = channel(p11, shift);
                    float value =
                            Math.round((a + (b - a) * fx) * (1 - fy) + (d + (e - d) * fx) * fy)
                                    / 255f;
                    output[c * paddedWidth * outHeight + y * paddedWidth + x] =
                            detector ? (value - mean[c]) / std[c] : (value - .5f) / .5f;
                }
            }
        }
        return output;
    }

    /** Same half-pixel interpolation and CHW math for an opaque unsigned gray raster. */
    public static float[] normalizeGray(
            byte[] pixels,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            int outWidth,
            int outHeight,
            int paddedWidth,
            boolean detector) {
        if (width <= 0
                || height <= 0
                || (long) width * height != pixels.length
                || left < 0
                || top < 0
                || right > width
                || bottom > height
                || right <= left
                || bottom <= top
                || outWidth < 1
                || outHeight < 1
                || paddedWidth < outWidth
                || (long) paddedWidth * outHeight > 5_000_000)
            throw new IllegalArgumentException("Invalid OCR resize");
        float[] output = new float[paddedWidth * outHeight * 3];
        int[] xs0 = new int[outWidth], xs1 = new int[outWidth];
        float[] fractions = new float[outWidth];
        for (int x = 0; x < outWidth; x++) {
            double sx =
                    Math.max(
                            left,
                            Math.min(right - 1, left + (x + .5) * (right - left) / outWidth - .5));
            xs0[x] = (int) sx;
            xs1[x] = Math.min(right - 1, xs0[x] + 1);
            fractions[x] = (float) (sx - xs0[x]);
        }
        for (int y = 0; y < outHeight; y++) {
            if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException();
            double sy =
                    Math.max(
                            top,
                            Math.min(bottom - 1, top + (y + .5) * (bottom - top) / outHeight - .5));
            int y0 = (int) sy, y1 = Math.min(bottom - 1, y0 + 1);
            float fy = (float) (sy - y0);
            int row0 = y0 * width, row1 = y1 * width;
            for (int x = 0; x < outWidth; x++) {
                int x0 = xs0[x], x1 = xs1[x];
                float fx = fractions[x];
                float a = pixels[row0 + x0] & 255, b = pixels[row0 + x1] & 255;
                float d = pixels[row1 + x0] & 255, e = pixels[row1 + x1] & 255;
                int rounded = Math.round((a + (b - a) * fx) * (1 - fy) + (d + (e - d) * fx) * fy);
                if (detector) {
                    for (int c = 0; c < 3; c++)
                        output[c * paddedWidth * outHeight + y * paddedWidth + x] =
                                GrayDetectorNormalization.VALUES[c][rounded];
                } else {
                    float normalized = GrayRecognizerNormalization.VALUES[rounded];
                    for (int c = 0; c < 3; c++)
                        output[c * paddedWidth * outHeight + y * paddedWidth + x] = normalized;
                }
            }
        }
        return output;
    }

    private static final class GrayDetectorNormalization {
        private static final float[][] VALUES = values();

        private static float[][] values() {
            float[] mean = {.485f, .456f, .406f}, std = {.229f, .224f, .225f};
            float[][] values = new float[3][256];
            for (int intensity = 0; intensity < 256; intensity++) {
                float value = intensity / 255f;
                for (int c = 0; c < 3; c++) values[c][intensity] = (value - mean[c]) / std[c];
            }
            return values;
        }
    }

    private static final class GrayRecognizerNormalization {
        private static final float[] VALUES = values();

        private static float[] values() {
            float[] values = new float[256];
            for (int intensity = 0; intensity < 256; intensity++) {
                float value = intensity / 255f;
                values[intensity] = (value - .5f) / .5f;
            }
            return values;
        }
    }

    private static float channel(int color, int shift) {
        int alpha = color >>> 24;
        return (((color >>> shift) & 255) * alpha + 255 * (255 - alpha)) / 255f;
    }

    private static OcrText.Box inkBounds(int[] argb, byte[] gray, int width, OcrText.Box region) {
        return gray == null ? inkBounds(argb, width, region) : inkBoundsGray(gray, width, region);
    }

    static OcrText.Box inkBoundsGray(byte[] pixels, int width, OcrText.Box region) {
        int l = region.right, r = -1, t = region.bottom, b = -1;
        for (int y = region.top; y < region.bottom; y++)
            for (int x = region.left; x < region.right; x++) {
                if ((pixels[y * width + x] & 255) < 190) {
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                }
            }
        return r < l ? region : new OcrText.Box(l, t, r + 1, b + 1);
    }

    /** Remove detector padding without searching beyond the CTC-assigned text area.
     * This is still an alignment estimate where neighboring glyphs overlap. */
    static OcrText.Box inkBounds(int[] pixels, int width, OcrText.Box region) {
        int l = region.right, r = -1, t = region.bottom, b = -1;
        for (int y = region.top; y < region.bottom; y++)
            for (int x = region.left; x < region.right; x++) {
                int color = pixels[y * width + x];
                if ((channel(color, 0) + channel(color, 8) + channel(color, 16)) / 3 < 190) {
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                }
            }
        return r < l ? region : new OcrText.Box(l, t, r + 1, b + 1);
    }

    /** A detector may enclose both stacked meter digits. Split only tall crops with
     * two substantial ink bands and a genuine empty horizontal gutter, not dots. */
    static List<OcrText.Box> splitStackedRows(int[] pixels, int width, OcrText.Box box) {
        if (box.height() < box.width() * 1.5 || box.height() < 24) return List.of(box);
        var bands = new ArrayList<int[]>();
        int start = -1, last = -1;
        int gap = Math.max(4, (int) Math.ceil(box.height() * .1));
        for (int y = box.top; y < box.bottom; y++) {
            int ink = 0;
            for (int x = box.left; x < box.right; x++) {
                int color = pixels[y * width + x];
                if ((channel(color, 0) + channel(color, 8) + channel(color, 16)) / 3 < 190) ink++;
            }
            if (ink >= Math.max(2, box.width() / 20)) {
                if (start < 0) start = y;
                else if (y - last > gap) {
                    bands.add(new int[] {start, last + 1});
                    start = y;
                }
                last = y;
            }
        }
        if (start >= 0) bands.add(new int[] {start, last + 1});
        if (bands.size() != 2 || bands.stream().anyMatch(b -> b[1] - b[0] < 6)) return List.of(box);
        int middle = (bands.get(0)[1] + bands.get(1)[0]) / 2;
        return List.of(
                new OcrText.Box(box.left, box.top, box.right, middle),
                new OcrText.Box(box.left, middle, box.right, box.bottom));
    }

    static List<OcrText.Box> splitStackedGrayRows(byte[] pixels, int width, OcrText.Box box) {
        if (box.height() < box.width() * 1.5 || box.height() < 24) return List.of(box);
        var bands = new ArrayList<int[]>();
        int start = -1, last = -1;
        int gap = Math.max(4, (int) Math.ceil(box.height() * .1));
        for (int y = box.top; y < box.bottom; y++) {
            int ink = 0;
            for (int x = box.left; x < box.right; x++) {
                if ((pixels[y * width + x] & 255) < 190) ink++;
            }
            if (ink >= Math.max(2, box.width() / 20)) {
                if (start < 0) start = y;
                else if (y - last > gap) {
                    bands.add(new int[] {start, last + 1});
                    start = y;
                }
                last = y;
            }
        }
        if (start >= 0) bands.add(new int[] {start, last + 1});
        if (bands.size() != 2 || bands.stream().anyMatch(b -> b[1] - b[0] < 6)) return List.of(box);
        int middle = (bands.get(0)[1] + bands.get(1)[0]) / 2;
        return List.of(
                new OcrText.Box(box.left, box.top, box.right, middle),
                new OcrText.Box(box.left, middle, box.right, box.bottom));
    }

    /** Bounded connected text components from the detector probability map. */
    public static List<OcrText.Box> regions(float[][] map, int width, int height) {
        if (width < 1
                || height < 1
                || map == null
                || map.length == 0
                || map[0] == null
                || map[0].length == 0)
            throw new IllegalArgumentException("Invalid OCR detection map");
        int mh = map.length, mw = map[0].length;
        if ((long) mw * mh > 5_000_000)
            throw new IllegalArgumentException("Oversize detection map");
        boolean[] mask = new boolean[mw * mh];
        int[] queue = new int[mask.length];
        for (int y = 0; y < mh; y++) {
            if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException();
            if (map[y] == null || map[y].length != mw)
                throw new IllegalArgumentException("Ragged detection map");
            for (int x = 0; x < mw; x++) {
                // CPU sigmoid kernels can overshoot an endpoint by one ULP.
                if (!Float.isFinite(map[y][x]) || map[y][x] < -0.000001f || map[y][x] > 1.000001f)
                    throw new IllegalArgumentException("Invalid detector probability");
                mask[y * mw + x] = map[y][x] > .3f;
            }
        }
        var result = new ArrayList<OcrText.Box>();
        for (int start = 0; start < mask.length; start++)
            if (mask[start]) {
                int head = 0, tail = 1;
                queue[0] = start;
                mask[start] = false;
                int l = mw, r = 0, t = mh, b = 0;
                double score = 0;
                while (head < tail) {
                    int at = queue[head++], x = at % mw, y = at / mw;
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                    score += map[y][x];
                    for (int yy = Math.max(0, y - 1); yy <= Math.min(mh - 1, y + 1); yy++)
                        for (int xx = Math.max(0, x - 1); xx <= Math.min(mw - 1, x + 1); xx++) {
                            int next = yy * mw + xx;
                            if (mask[next]) {
                                mask[next] = false;
                                queue[tail++] = next;
                            }
                        }
                }
                if (tail < 3 || r - l < 2 || b - t < 2 || score / tail < .5) continue;
                double margin = (r - l + 1.0) * (b - t + 1) * 1.5 / (2 * (r - l + b - t + 2));
                int left = Math.max(0, (int) Math.floor((l - margin) * width / mw));
                int right = Math.min(width, (int) Math.ceil((r + 1 + margin) * width / mw));
                int top = Math.max(0, (int) Math.floor((t - margin) * height / mh));
                int bottom = Math.min(height, (int) Math.ceil((b + 1 + margin) * height / mh));
                if (right > left && bottom > top)
                    result.add(new OcrText.Box(left, top, right, bottom));
                if (result.size() > 1000)
                    throw new IllegalArgumentException("Too many OCR regions");
            }
        return result;
    }

    private static List<OcrText.Element> words(
            OcrCtcDecoder.Result decoded,
            OcrText.Box line,
            int steps,
            int paddedWidth,
            int contentWidth) {
        var result = new ArrayList<OcrText.Element>();
        var symbols = new ArrayList<OcrText.Symbol>();
        var text = new StringBuilder();
        var tokens = decoded.tokens();
        for (int i = 0; i <= tokens.size(); i++) {
            if (i == tokens.size() || tokens.get(i).text().isBlank()) {
                if (!symbols.isEmpty()) {
                    var first = symbols.get(0).box();
                    var last = symbols.get(symbols.size() - 1).box();
                    result.add(
                            new OcrText.Element(
                                    text.toString(),
                                    new OcrText.Box(first.left, line.top, last.right, line.bottom),
                                    symbols));
                    symbols = new ArrayList<>();
                    text.setLength(0);
                }
                continue;
            }
            var token = tokens.get(i);
            double center = (token.startStep() + token.endStep()) * .5;
            double begin =
                    i == 0
                            ? 0
                            : (tokens.get(i - 1).startStep()
                                            + tokens.get(i - 1).endStep()
                                            + 2 * center)
                                    / 4;
            double end =
                    i + 1 == tokens.size()
                            ? steps
                            : (tokens.get(i + 1).startStep()
                                            + tokens.get(i + 1).endStep()
                                            + 2 * center)
                                    / 4;
            double unit = (double) paddedWidth / contentWidth * line.width() / steps;
            int l =
                    Math.max(
                            line.left,
                            Math.min(line.right - 1, line.left + (int) Math.floor(begin * unit)));
            int r = Math.max(l + 1, Math.min(line.right, line.left + (int) Math.ceil(end * unit)));
            symbols.add(
                    new OcrText.Symbol(token.text(), new OcrText.Box(l, line.top, r, line.bottom)));
            text.append(token.text());
        }
        return result;
    }
}
