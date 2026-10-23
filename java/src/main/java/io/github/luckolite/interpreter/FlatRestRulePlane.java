// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Five contrasted horizontal raster rules can establish a flat rest view independently of a track. */
final class FlatRestRulePlane {
    private FlatRestRulePlane() {}

    static boolean proved(byte[] gray, int width, int height, int[] rows, float gap) {
        if (gray == null
                || width < 3
                || height < 3
                || width > 8192
                || height > 16384
                || gray.length != (long) width * height
                || rows == null
                || rows.length != 5
                || !Float.isFinite(gap)
                || gap < 4
                || gap > 64) return false;
        int flank = Math.max(2, Math.round(gap * .32f)),
                required = Math.max(24, (int) Math.ceil(width * .6f));
        for (int i = 0; i < 5; i++) {
            if (rows[i] < flank
                    || rows[i] >= height - flank
                    || i > 0 && Math.abs(rows[i] - rows[i - 1] - gap) > 1) return false;
            if (columns(gray, width, rows[i], flank) < required) return false;
        }
        // A sixth neighboring long rule leaves the five-line phase ambiguous.
        for (int outside : new int[] {Math.round(rows[0] - gap), Math.round(rows[4] + gap)})
            for (int y = Math.max(flank, outside - Math.round(gap * .2f));
                    y <= Math.min(height - 1 - flank, outside + Math.round(gap * .2f));
                    y++) if (columns(gray, width, y, flank) >= required) return false;
        return true;
    }

    /** A raw flat view may only replace sub-line deformation of its own seed frame. */
    static boolean trackFitsSeed(SixteenthRestDetector.Staff staff, int width) {
        if (staff == null
                || width < 3
                || width > 8192
                || !Float.isFinite(staff.top())
                || !Float.isFinite(staff.bottom())
                || !Float.isFinite(staff.gap())
                || staff.gap() < 4
                || staff.gap() > 64) return false;
        if (staff.pitchTrack() == null) return true;
        float edge = staff.gap() * .15f, scale = staff.gap() * .05f;
        for (int x = 0; x < width; x++) {
            float[] local = staff.pitchTrack().at(x);
            if (!Float.isFinite(local[0])
                    || !Float.isFinite(local[1])
                    || Math.abs(local[0] - staff.bottom()) > edge
                    || Math.abs(local[0] - 4 * local[1] - staff.top()) > edge
                    || Math.abs(local[1] - staff.gap()) > scale) return false;
        }
        return true;
    }

    private static int columns(byte[] gray, int width, int y, int flank) {
        int count = 0;
        for (int x = 0; x < width; x++) {
            int ink = gray[y * width + x] & 255;
            if (ink < 170
                    && (gray[(y - flank) * width + x] & 255) >= ink + 12
                    && (gray[(y + flank) * width + x] & 255) >= ink + 12) count++;
        }
        return count;
    }
}
