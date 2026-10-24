// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Binds repeated local staff keys to one physically intersecting measure system. */
final class NaturalHarmonicStaffBinding {
    private NaturalHarmonicStaffBinding() {}

    static PlayingTechniqueDetector.Staff uniqueForMeasure(
            List<PlayingTechniqueDetector.Staff> staffs,
            int index,
            int count,
            MeasureRegion region,
            int height) {
        if (staffs == null
                || region == null
                || height <= 0
                || !Float.isFinite(region.top())
                || !Float.isFinite(region.bottom())
                || region.top() < 0
                || region.bottom() > 1
                || region.bottom() < region.top()) return null;
        float top = region.top() * height;
        float bottom = region.bottom() * height;
        PlayingTechniqueDetector.Staff found = null;
        for (PlayingTechniqueDetector.Staff staff : staffs) {
            if (staff.index() != index || staff.count() != count) continue;
            if (!Float.isFinite(staff.top())
                    || !Float.isFinite(staff.bottom())
                    || !Float.isFinite(staff.gap())
                    || staff.gap() <= 0) continue;
            if (staff.bottom() < top || staff.top() > bottom) continue;
            if (found != null) return null;
            found = staff;
        }
        return found;
    }
}
