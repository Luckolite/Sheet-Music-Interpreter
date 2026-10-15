// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Source-raster qualification of a complete short opening beam and silent companion staves. */
final class OpeningBeamPickup {
    static ScorePageInterpretation apply(
            ScorePageInterpretation s,
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<ScoreMeterChange> headerMeters,
            List<MeterChangeDetector.Crop> headers) {
        if (s == null
                || s.firstMeasureNumber() > 1
                || s.measures().size() < 3
                || gray == null
                || labels == null
                || gray.length != (long) w * h
                || labels.length != gray.length) return s;
        var meters =
                s.meterChanges().stream().filter(m -> m.measureIndex() == 0).distinct().toList();
        var selectedHeaders =
                headerMeters.stream().filter(m -> m.measureIndex() == 0).distinct().toList();
        if (meters.size() != 1
                || selectedHeaders.size() != 1
                || !selectedHeaders.get(0).equals(meters.get(0))) return s;
        var opening = s.notes().stream().filter(n -> n.measureIndex() == 0).toList();
        if (opening.isEmpty()) return s;
        int count = opening.get(0).staffCount();
        if (count < 1 || count > 16) return s;
        var region = s.measures().get(0);
        if (opening.stream()
                .anyMatch(
                        n ->
                                n.staffCount() != count
                                        || n.staffIndex() < 0
                                        || n.staffIndex() >= count
                                        || n.kind() != ScoreNoteEvent.Kind.PITCHED
                                        || n.beamCount() < 1
                                        || n.beamCount() > 4
                                        || n.tiedFromPrevious()
                                        || n.crossStaffBeam()
                                        || n.boundaryTies() != 0
                                        || n.leadingRestBeats() > 0
                                        || n.followingRestBeats() > 0
                                        || (n.articulations() & NoteOrnament.GRACE) != 0)) return s;
        for (int lane = 0; lane < count; lane++) {
            final int staff = lane;
            var notes =
                    opening.stream()
                            .filter(n -> n.staffIndex() == staff)
                            .sorted(Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure))
                            .toList();
            var rests =
                    s.rests().stream()
                            .filter(r -> r.measureIndex() == 0 && r.staffIndex() == staff)
                            .toList();
            if (notes.isEmpty()) {
                if (rests.size() != 1
                        || !rests.get(0).isFullMeasure()
                        || rests.get(0).staffCount() != count) return s;
                continue;
            }
            if (!rests.isEmpty() || notes.size() < 2) return s;
            var frames =
                    staffs.stream()
                            .filter(
                                    t ->
                                            t.index() == staff
                                                    && t.count() == count
                                                    && t.top() >= region.top() * h
                                                    && t.bottom() <= region.bottom() * h)
                            .toList();
            if (frames.size() != 1) return s;
            var frame = frames.get(0);
            float gap = frame.gap();
            if (!Float.isFinite(gap) || gap < 6) return s;
            var ownedHeaders =
                    headers.stream()
                            .filter(
                                    c ->
                                            Math.abs(c.firstLine() - frame.top()) < gap * .4f
                                                    && MeterChangeDetector.followingMeasure(
                                                                    c, w, h, s.measures())
                                                            == 0)
                            .toList();
            if (ownedHeaders.isEmpty()
                    || ownedHeaders.stream().map(MeterChangeDetector.Crop::right).distinct().count()
                            != 1) return s;
            int headerRight = ownedHeaders.get(0).right(), direction = notes.get(0).stemDirection();
            float[] xs = new float[notes.size()], ys = new float[notes.size()];
            for (int i = 0; i < notes.size(); i++) {
                var n = notes.get(i);
                if (n.stemDirection() != direction) return s;
                xs[i] =
                        (region.left() + n.positionInMeasure() * (region.right() - region.left()))
                                * w;
                ys[i] = n.pageY() * h;
            }
            if (xs[0] <= headerRight
                    || xs[0] - headerRight > gap * 3
                    || region.right() * w - xs[xs.length - 1] > gap * 3) return s;
            if (!CompleteBeamGroupInk.proves(gray, w, h, xs, ys, gap, direction)) return s;
            // Every segmented head in the usable opening lane must have a decoded owner.
            int top = Math.max(0, Math.round(frame.top() - gap * 3)),
                    bottom = Math.min(h - 1, Math.round(frame.bottom() + gap * 3));
            for (int y = top; y <= bottom; y++)
                for (int x = Math.max(0, headerRight);
                        x < Math.min(w, Math.round(region.right() * w));
                        x++) {
                    if (labels[y * w + x] != OmrMeasurePostProcessor.NOTEHEAD) continue;
                    boolean owned = false;
                    for (int i = 0; i < xs.length; i++)
                        if (Math.abs(x - xs[i]) <= gap && Math.abs(y - ys[i]) <= gap * .65f) {
                            owned = true;
                            break;
                        }
                    if (!owned) return s;
                }
        }
        var edited = new ArrayList<ScoreNoteEvent>();
        var owners = new ArrayList<Integer>();
        for (int i = 0; i < s.notes().size(); i++) {
            var n = s.notes().get(i);
            edited.add(n.measureIndex() == 0 ? n.withCompactOpening() : n);
            owners.add(i);
        }
        var candidate = s.withNotes(edited, owners);
        float nominal = (float) meters.get(0).quarterBeats();
        return Double.isFinite(ScoreOpeningDuration.provedQuarterBeats(candidate, nominal))
                ? candidate
                : s;
    }
}
