// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Objects;

/** Corrects decoder illumination while leaving the original segmentation raster authoritative. */
public final class PaperBackgroundWhitening {
    public static final String MODEL_ASSET = "paper_field_v1.onnx";
    public static final String MODEL_SHA256 =
            "dc27aec9f1d29cc423510277992523991fbbb568770a3acf73875107476ab460";
    public static final String RASTER_POLICY = "original-segmentation-paper-field-decoder-v1";
    private static final int MAX_PIXELS = 20_000_000;
    private static final int FIELD_SIDE = 960;
    private static final int RADIUS = 12;

    public interface Inference {
        Fields run(float[] features, int width, int height) throws Exception;
    }

    public record Fields(float[][] paper, float[][] contrast) {}

    public record Result(
            byte[] decoderGray, boolean changed, String rawSha256, String decoderSha256) {}

    private PaperBackgroundWhitening() {}

    public static Result apply(byte[] original, int width, int height, Inference inference)
            throws Exception {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(inference, "inference");
        long size = (long) width * height;
        if (width < 1 || height < 1 || size > MAX_PIXELS || size != original.length) {
            throw new IllegalArgumentException("Invalid grayscale dimensions");
        }
        String rawHash = rasterSha256(original, width, height);
        if (bright(original) || bright(close(original, width, height))) {
            return new Result(original, false, rawHash, rawHash);
        }
        double scale = Math.min(1., FIELD_SIDE / (double) Math.max(width, height));
        int fieldWidth = Math.max(8, (int) Math.rint(width * scale));
        int fieldHeight = Math.max(8, (int) Math.rint(height * scale));
        float[] small = resizeArea(original, width, height, fieldWidth, fieldHeight);
        float[] paperEstimate = close(small, fieldWidth, fieldHeight);
        int fieldSize = fieldWidth * fieldHeight;
        float[] features = new float[2 * fieldSize];
        for (int i = 0; i < fieldSize; i++) {
            features[i] = small[i] / 255f;
            features[fieldSize + i] = paperEstimate[i] / 255f;
        }
        Fields fields = inference.run(features, fieldWidth, fieldHeight);
        validate(fields.paper(), fieldWidth, fieldHeight, .23f);
        validate(fields.contrast(), fieldWidth, fieldHeight, .35f);
        byte[] output = new byte[original.length];
        boolean changed = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                float paper =
                        interpolate(fields.paper(), fieldWidth, fieldHeight, x, y, width, height);
                float contrast =
                        interpolate(
                                fields.contrast(), fieldWidth, fieldHeight, x, y, width, height);
                float ink = Math.max(0f, 1f - (original[index] & 255) / (255f * paper));
                float restored = 255f * (1f - ink / contrast);
                int value = Math.max(0, Math.min(255, (int) Math.rint(restored)));
                output[index] = (byte) value;
                changed |= output[index] != original[index];
            }
        }
        return new Result(
                changed ? output : original,
                changed,
                rawHash,
                changed ? rasterSha256(output, width, height) : rawHash);
    }

    public static String rasterSha256(byte[] gray, int width, int height) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(ByteBuffer.allocate(8).putInt(width).putInt(height).array());
        byte[] hash = digest.digest(gray);
        StringBuilder hex = new StringBuilder(64);
        for (byte value : hash) {
            int unsigned = value & 255;
            hex.append(Character.forDigit(unsigned >>> 4, 16));
            hex.append(Character.forDigit(unsigned & 15, 16));
        }
        return hex.toString();
    }

    private static void validate(float[][] field, int width, int height, float minimum) {
        if (field == null || field.length != height) {
            throw new IllegalArgumentException("Invalid paper field height");
        }
        for (float[] row : field) {
            if (row == null || row.length != width) {
                throw new IllegalArgumentException("Invalid paper field width");
            }
            for (float value : row) {
                if (!Float.isFinite(value) || value < minimum - .00001f || value > 1.00001f) {
                    throw new IllegalArgumentException("Invalid paper field value");
                }
            }
        }
    }

    private static boolean bright(byte[] gray) {
        int[] histogram = new int[256];
        for (byte value : gray) histogram[value & 255]++;
        return quantile(histogram, gray.length, .1) > .94
                && quantile(histogram, gray.length, .5) > .98;
    }

    private static double quantile(int[] histogram, int count, double fraction) {
        double position = (count - 1) * fraction;
        int low = (int) position;
        int high = (int) Math.ceil(position);
        int cumulative = 0;
        float lower = 0f;
        for (int value = 0; value < 256; value++) {
            int previous = cumulative;
            cumulative += histogram[value];
            if (previous <= low && cumulative > low) lower = value / 255f;
            if (cumulative > high) {
                return lower + (value / 255f - lower) * (position - low);
            }
        }
        throw new IllegalArgumentException("Empty grayscale histogram");
    }

    private static byte[] close(byte[] source, int width, int height) {
        byte[] temporary = new byte[source.length];
        byte[] result = new byte[source.length];
        filter(source, temporary, width, height, false, true);
        filter(temporary, result, width, height, true, true);
        filter(result, temporary, width, height, false, false);
        filter(temporary, result, width, height, true, false);
        return result;
    }

    private static float[] close(float[] source, int width, int height) {
        float[] temporary = new float[source.length];
        float[] result = new float[source.length];
        filter(source, temporary, width, height, false, true);
        filter(temporary, result, width, height, true, true);
        filter(result, temporary, width, height, false, false);
        filter(temporary, result, width, height, true, false);
        return result;
    }

    private static void filter(
            byte[] source,
            byte[] output,
            int width,
            int height,
            boolean vertical,
            boolean maximum) {
        int length = vertical ? height : width;
        int lines = vertical ? width : height;
        int step = vertical ? width : 1;
        int[] queue = new int[length];
        for (int line = 0; line < lines; line++) {
            int base = vertical ? line : line * width;
            int head = 0, tail = 0, next = 0;
            for (int position = 0; position < length; position++) {
                int right = Math.min(length - 1, position + RADIUS);
                while (next <= right) {
                    int value = source[base + next * step] & 255;
                    while (tail > head) {
                        int previous = source[base + queue[tail - 1] * step] & 255;
                        if (maximum ? previous > value : previous < value) break;
                        tail--;
                    }
                    queue[tail++] = next++;
                }
                while (queue[head] < position - RADIUS) head++;
                output[base + position * step] = source[base + queue[head] * step];
            }
        }
    }

    private static void filter(
            float[] source,
            float[] output,
            int width,
            int height,
            boolean vertical,
            boolean maximum) {
        int length = vertical ? height : width;
        int lines = vertical ? width : height;
        int step = vertical ? width : 1;
        int[] queue = new int[length];
        for (int line = 0; line < lines; line++) {
            int base = vertical ? line : line * width;
            int head = 0, tail = 0, next = 0;
            for (int position = 0; position < length; position++) {
                int right = Math.min(length - 1, position + RADIUS);
                while (next <= right) {
                    float value = source[base + next * step];
                    while (tail > head) {
                        float previous = source[base + queue[tail - 1] * step];
                        if (maximum ? previous > value : previous < value) break;
                        tail--;
                    }
                    queue[tail++] = next++;
                }
                while (queue[head] < position - RADIUS) head++;
                output[base + position * step] = source[base + queue[head] * step];
            }
        }
    }

    private static float[] resizeArea(
            byte[] source, int width, int height, int targetWidth, int targetHeight) {
        float[] result = new float[targetWidth * targetHeight];
        double scaleX = width / (double) targetWidth;
        double scaleY = height / (double) targetHeight;
        for (int y = 0; y < targetHeight; y++) {
            double top = y * scaleY, bottom = (y + 1) * scaleY;
            for (int x = 0; x < targetWidth; x++) {
                double left = x * scaleX, right = (x + 1) * scaleX;
                double sum = 0.;
                for (int sy = (int) top; sy < Math.min(height, Math.ceil(bottom)); sy++) {
                    double wy = Math.min(bottom, sy + 1.) - Math.max(top, sy);
                    for (int sx = (int) left; sx < Math.min(width, Math.ceil(right)); sx++) {
                        double wx = Math.min(right, sx + 1.) - Math.max(left, sx);
                        sum += (source[sy * width + sx] & 255) * wx * wy;
                    }
                }
                result[y * targetWidth + x] = (float) (sum / (scaleX * scaleY));
            }
        }
        return result;
    }

    private static float interpolate(
            float[][] source,
            int width,
            int height,
            int x,
            int y,
            int targetWidth,
            int targetHeight) {
        double px = Math.max(0., (x + .5) * width / targetWidth - .5);
        double py = Math.max(0., (y + .5) * height / targetHeight - .5);
        int left = (int) px, top = (int) py;
        int right = Math.min(width - 1, left + 1), bottom = Math.min(height - 1, top + 1);
        float fx = (float) (px - left), fy = (float) (py - top);
        float upper = source[top][left] * (1f - fx) + source[top][right] * fx;
        float lower = source[bottom][left] * (1f - fx) + source[bottom][right] * fx;
        return upper * (1f - fy) + lower * fy;
    }
}
