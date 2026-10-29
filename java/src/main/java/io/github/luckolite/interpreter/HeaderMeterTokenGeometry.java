// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Numeric opening-meter token dimensions; placement remains in the registered source frame. */
public final class HeaderMeterTokenGeometry {
    private HeaderMeterTokenGeometry() {}

    public static boolean digitSized(int value, float width, float height, float gap) {
        if (value < 1
                || value > 32
                || !Float.isFinite(width)
                || !Float.isFinite(height)
                || !Float.isFinite(gap)
                || gap <= 0) return false;
        int digits = value >= 10 ? 2 : 1;
        return height >= gap * .55f
                && height <= gap * 2.8f
                && width >= gap * .35f
                && width <= gap * 3.2f * digits;
    }
}
