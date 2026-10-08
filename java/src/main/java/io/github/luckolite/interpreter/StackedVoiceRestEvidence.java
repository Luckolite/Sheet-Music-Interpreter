// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** A complete upper rest can establish a second silent voice above a complete lower rest. */
final class StackedVoiceRestEvidence {
    static boolean above(
            List<ScoreRestEvent> rests,
            int measure,
            int staff,
            int staffCount,
            MeasureRegion region,
            int width,
            int height,
            int left,
            int right,
            int lowerTop,
            float gap) {
        if (rests == null
                || region == null
                || width <= 0
                || height <= 0
                || !Float.isFinite(gap)
                || gap < 4
                || right < left) return false;
        float center = (left + right) * .5f;
        for (ScoreRestEvent rest : rests) {
            if (rest.measureIndex() != measure
                    || rest.staffIndex() != staff
                    || rest.staffCount() != staffCount
                    || rest.durationBeats() < .25
                    || !Double.isFinite(rest.durationBeats())
                    || rest.pageHeight() <= 0) continue;
            float x =
                    (region.left() + rest.positionInMeasure() * (region.right() - region.left()))
                            * width;
            float bottom = (rest.pageY() + rest.pageHeight() * .5f) * height;
            float separation = lowerTop - bottom;
            if (Float.isFinite(x)
                    && Float.isFinite(separation)
                    && Math.abs(x - center) <= gap * .65f
                    && separation >= gap * .7f
                    && separation <= gap * 7) return true;
        }
        return false;
    }
}
