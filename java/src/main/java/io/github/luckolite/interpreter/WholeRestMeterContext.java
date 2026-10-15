// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A complete whole-rest plate cannot be a literal four-beat rest in a shorter silent bar. */
final class WholeRestMeterContext {
    static ScorePageInterpretation apply(
            ScorePageInterpretation s,
            byte[] gray,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs) {
        return apply(s, gray, w, h, staffs, List.of(), List.of());
    }

    static ScorePageInterpretation apply(
            ScorePageInterpretation s,
            byte[] gray,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<ScoreMeterChange> headerMeters,
            List<MeterChangeDetector.Crop> headers) {
        if (s == null || s.rests().isEmpty() || s.meterChanges().isEmpty()) return s;
        // Later tablature/annotation stages may change the selected meter. Such a change
        // invalidates OCR header evidence rather than silently reusing its geometry.
        var acceptedHeaders =
                headers.stream()
                        .filter(
                                c -> {
                                    int mi =
                                            MeterChangeDetector.followingMeasure(
                                                    c, w, h, s.measures());
                                    var source =
                                            headerMeters.stream()
                                                    .filter(m -> m.measureIndex() == mi)
                                                    .distinct()
                                                    .toList();
                                    var current =
                                            s.meterChanges().stream()
                                                    .filter(m -> m.measureIndex() == mi)
                                                    .distinct()
                                                    .toList();
                                    return source.size() == 1
                                            && current.size() == 1
                                            && source.get(0).equals(current.get(0));
                                })
                        .toList();
        var headerBars = new HashSet<Integer>();
        for (var c : acceptedHeaders)
            headerBars.add(MeterChangeDetector.followingMeasure(c, w, h, s.measures()));
        Set<Integer> shortBars = new HashSet<>();
        for (int mi = 0; mi < s.measures().size(); mi++) {
            int latest = -1;
            ScoreMeterChange selected = null;
            boolean conflict = false;
            for (var meter : s.meterChanges()) {
                if (meter.measureIndex() < 0 || meter.measureIndex() > mi) continue;
                if (meter.measureIndex() > latest) {
                    latest = meter.measureIndex();
                    selected = meter;
                    conflict = false;
                } else if (meter.measureIndex() == latest && !meter.equals(selected))
                    conflict = true;
            }
            if (conflict
                    || selected == null
                    || selected.numerator() <= 0
                    || selected.denominator() <= 0) continue;
            double span = selected.numerator() * 4d / selected.denominator();
            if (span > 0 && span < 4) shortBars.add(mi);
        }
        if (shortBars.isEmpty() && acceptedHeaders.isEmpty()) return s;
        var restStaffs =
                staffs.stream()
                        .map(
                                st ->
                                        new SixteenthRestDetector.Staff(
                                                st.top(),
                                                st.bottom(),
                                                st.gap(),
                                                st.index(),
                                                st.count()))
                        .toList();
        var decisions =
                WholeRestClassifier.detect(
                        gray,
                        w,
                        h,
                        s.measures(),
                        restStaffs,
                        s.notes(),
                        s.rests(),
                        shortBars,
                        acceptedHeaders);
        var rests = new ArrayList<>(s.rests());
        for (var d : decisions) {
            var found = d.event();
            if (!found.isFullMeasure()
                    || (!shortBars.contains(found.measureIndex())
                            && !headerBars.contains(found.measureIndex()))
                    || s.notes().stream()
                            .anyMatch(
                                    n ->
                                            n.measureIndex() == found.measureIndex()
                                                    && n.staffIndex() == found.staffIndex()
                                                    && n.staffCount() == found.staffCount()))
                continue;
            int at = -1;
            for (int i = 0; i < rests.size(); i++) {
                var old = rests.get(i);
                if (old.isFullMeasure()
                        || old.durationBeats() != 4
                        || old.measureIndex() != found.measureIndex()
                        || old.staffIndex() != found.staffIndex()
                        || old.staffCount() != found.staffCount()) continue;
                if (Math.abs(old.positionInMeasure() - found.positionInMeasure()) > .025f
                        || Math.abs(old.pageY() - found.pageY())
                                > Math.max(.001f, Math.max(old.pageHeight(), found.pageHeight())))
                    continue;
                if (at >= 0) {
                    at = -2;
                    break;
                }
                at = i;
            }
            if (at >= 0) {
                var old = rests.get(at);
                rests.set(
                        at,
                        ScoreRestEvent.fullMeasure(
                                old.measureIndex(),
                                old.positionInMeasure(),
                                old.pageY(),
                                old.pageHeight(),
                                old.staffIndex(),
                                old.staffCount()));
            }
        }
        if (rests.equals(s.rests())) return s;
        return new ScorePageInterpretation(
                s.measures(),
                s.notes(),
                s.firstMeasureNumber(),
                s.keyChanges(),
                s.tempoChanges(),
                s.meterChanges(),
                List.copyOf(rests),
                s.techniqueChanges(),
                s.dynamicChanges(),
                s.playbackDirections(),
                s.expressiveEvents(),
                s.slurSource(),
                s.openingPickup(),
                s.tremoloSource());
    }
}
