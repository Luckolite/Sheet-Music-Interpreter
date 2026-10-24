// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Classifies a complete connected feature component, without granting pixel erasure or ownership. */
final class PrintedParenthesisShape {
    enum Side {
        LEFT,
        RIGHT
    }

    record Glyph(
            Side side,
            int left,
            int top,
            int right,
            int bottom,
            float centerX,
            float centerY,
            float gap) {}

    private PrintedParenthesisShape() {}

    static Optional<Glyph> classify(
            int[] pixels, int width, int height, float gap, boolean cropped) {
        if (cropped
                || pixels == null
                || pixels.length < 12
                || pixels.length > 4096
                || width < 3
                || height < 3
                || !Float.isFinite(gap)
                || gap < 8
                || gap > 64) return Optional.empty();
        int left = width, right = -1, top = height, bottom = -1;
        var distinct = new HashSet<Integer>();
        for (int p : pixels) {
            if (p < 0 || p >= (long) width * height || !distinct.add(p)) return Optional.empty();
            int x = p % width, y = p / width;
            left = Math.min(left, x);
            right = Math.max(right, x);
            top = Math.min(top, y);
            bottom = Math.max(bottom, y);
        }
        int rows = bottom - top + 1, columns = right - left + 1;
        if (left == 0
                || right == width - 1
                || top == 0
                || bottom == height - 1
                || rows < gap * 1.1f
                || rows > gap * 2.6f
                || rows > 128
                || columns < 2
                || columns > gap * .85f
                || columns > 64) return Optional.empty();
        int[] lo = new int[rows], hi = new int[rows], count = new int[rows];
        Arrays.fill(lo, width);
        Arrays.fill(hi, -1);
        for (int p : pixels) {
            int y = p / width - top, x = p % width;
            lo[y] = Math.min(lo[y], x);
            hi[y] = Math.max(hi[y], x);
            count[y]++;
        }
        double[] centers = new double[rows];
        int thin = 0;
        double[][] matrix = new double[3][4];
        for (int y = 0; y < rows; y++) {
            if (count[y] == 0 || count[y] != hi[y] - lo[y] + 1) return Optional.empty();
            centers[y] = (lo[y] + hi[y]) * .5;
            if (count[y] <= Math.max(3, gap * .4f)) thin++;
            double t = 2 * (y + .5) / rows - 1;
            double[] basis = {1, t, t * t};
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) matrix[i][j] += basis[i] * basis[j];
                matrix[i][3] += basis[i] * centers[y];
            }
        }
        if (thin < rows * .85) return Optional.empty();
        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int row = col + 1; row < 3; row++)
                if (Math.abs(matrix[row][col]) > Math.abs(matrix[pivot][col])) pivot = row;
            if (Math.abs(matrix[pivot][col]) < 1e-9) return Optional.empty();
            double[] swap = matrix[col];
            matrix[col] = matrix[pivot];
            matrix[pivot] = swap;
            double scale = matrix[col][col];
            for (int j = col; j < 4; j++) matrix[col][j] /= scale;
            for (int row = 0; row < 3; row++)
                if (row != col) {
                    double factor = matrix[row][col];
                    for (int j = col; j < 4; j++) matrix[row][j] -= factor * matrix[col][j];
                }
        }
        double a = matrix[0][3], b = matrix[1][3], c = matrix[2][3];
        if (Math.abs(c) < gap * .12 || Math.abs(-b / (2 * c)) > .35) return Optional.empty();
        double squared = 0;
        int outside = 0;
        for (int y = 0; y < rows; y++) {
            double t = 2 * (y + .5) / rows - 1,
                    error = Math.abs(centers[y] - a - b * t - c * t * t);
            squared += error * error;
            if (error > gap * .2) outside++;
        }
        if (Math.sqrt(squared / rows) > gap * .08 || outside > rows * .05) return Optional.empty();
        double[] bins = new double[5];
        int[] n = new int[5];
        for (int y = 0; y < rows; y++) {
            int i = Math.min(4, y * 5 / rows);
            bins[i] += centers[y];
            n[i]++;
        }
        for (int i = 0; i < 5; i++) bins[i] /= n[i];
        double sign = c > 0 ? 1 : -1, depth = sign * ((bins[0] + bins[4]) * .5 - bins[2]);
        if (depth < gap * .1
                || sign * (bins[0] - bins[1]) < depth * .1
                || sign * (bins[4] - bins[3]) < depth * .1
                || sign * (bins[1] - bins[2]) < depth * .06
                || sign * (bins[3] - bins[2]) < depth * .06) return Optional.empty();
        return Optional.of(
                new Glyph(
                        c > 0 ? Side.LEFT : Side.RIGHT,
                        left,
                        top,
                        right,
                        bottom,
                        (left + right) * .5f,
                        (top + bottom) * .5f,
                        gap));
    }
}
