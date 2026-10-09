// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Adds only proved shapes; kind-only reclassification preserves retained geometry and dots. */
final class ClassifiedWholeRests {
    static SixteenthRestDetector.Detection apply(
            SixteenthRestDetector.Detection baseline,
            byte[] gray,
            int w,
            int h,
            List<MeasureRegion> measures,
            List<SixteenthRestDetector.Staff> staffs,
            List<ScoreNoteEvent> classificationNotes) {
        var rests = new ArrayList<>(baseline.rests());
        var dots = new ArrayList<>(baseline.dots());
        var candidates =
                WholeRestClassifier.detect(
                        gray, w, h, measures, staffs, classificationNotes, baseline.rests());
        for (var decision : candidates) {
            if (decision.status() == WholeRestClassifier.Status.UNRESOLVED) continue;
            var candidate = decision.event();
            int found = -1;
            for (int i = 0; i < rests.size(); i++) {
                var old = rests.get(i);
                if (old.measureIndex() == candidate.measureIndex()
                        && old.staffIndex() == candidate.staffIndex()
                        && old.staffCount() == candidate.staffCount()
                        && Math.abs(old.positionInMeasure() - candidate.positionInMeasure()) < .025f
                        && Math.abs(old.pageY() - candidate.pageY())
                                < Math.max(
                                        .001f,
                                        Math.max(old.pageHeight(), candidate.pageHeight()))) {
                    if (found >= 0) {
                        found = -2;
                        break;
                    }
                    found = i;
                }
            }
            if (found == -2) continue;
            if (found >= 0) {
                var old = rests.get(found);
                if (old.durationBeats() != candidate.durationBeats()
                        || old.kind() == candidate.kind()) continue;
                var replacement =
                        new ScoreRestEvent(
                                old.measureIndex(),
                                old.positionInMeasure(),
                                old.pageY(),
                                old.pageHeight(),
                                old.staffIndex(),
                                old.staffCount(),
                                old.durationBeats(),
                                candidate.kind());
                rests.set(found, replacement);
                for (int i = 0; i < dots.size(); i++) {
                    var dot = dots.get(i);
                    if (dot.rest().equals(old))
                        dots.set(
                                i,
                                new SixteenthRestDetector.RestDot(dot.x(), dot.y(), replacement));
                }
            } else {
                rests.add(candidate);
                for (var dot : decision.dots())
                    dots.add(new SixteenthRestDetector.RestDot(dot.x(), dot.y(), candidate));
            }
        }
        return new SixteenthRestDetector.Detection(List.copyOf(rests), List.copyOf(dots));
    }
}
