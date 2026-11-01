// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Whole closed raw beam phrases can own a remote returning cross-staff slur tip. */
final class CrossStaffPhraseEndpointInk {
    private CrossStaffPhraseEndpointInk() {}

    static Optional<ScoreSlurPages.Span> complete(
            String id,
            int page,
            PrintedSlurContours.Bow bow,
            List<? extends PrintedSlurOwners.Location> heads,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> regions,
            List<ScoreNoteEvent> notes,
            ScoreBeamSource liveBeam,
            PrintedSlurPageCapture.Frame<? extends PrintedSlurOwners.Location> observedFrame,
            ClosedCrossStaffBeamGroups.Result proof) {
        if (id == null
                || id.isBlank()
                || page < 0
                || bow == null
                || bow.above()
                || observedFrame == null
                || proof == null
                || !proof.matches(gray, labels, width, height, regions, notes))
            return Optional.empty();
        var start =
                PrintedSlurOwners.capOwner(
                        bow.start(),
                        false,
                        true,
                        observedFrame.heads(),
                        gray,
                        width,
                        height,
                        observedFrame.track());
        if (start.isEmpty()) return Optional.empty();
        var byId = new HashMap<Integer, PrintedSlurOwners.Location>();
        for (var h : heads) if (byId.put(h.owner(), h) != null) return Optional.empty();
        ClosedCrossStaffBeamGroups.Group found = null;
        PrintedSlurOwners.Location ending = null;
        for (var group : proof.groups()) {
            if (group.owners().get(0) != start.get().owner()) continue;
            int last = group.owners().get(group.owners().size() - 1);
            var end = byId.get(last);
            if (end == null
                    || end.staffIndex() == start.get().staffIndex()
                    || end.staffCount() != 2
                    || start.get().staffCount() != 2) continue;
            var source = group.source();
            if (source.columns().get(0).members().get(0).pageIndex() != page) continue;
            float[] frame = observedFrame.track().at(bow.start().x());
            float gap = observedFrame.track().at(bow.end().x())[1];
            // Existing printed owner/frame agreement, not a new endpoint search radius.
            if (Math.abs(frame[0] - group.pair().lower().bottom()) > gap * 1.5f
                    || Math.abs(frame[1] - group.pair().lower().gap()) > gap * .15f) continue;
            var a = group.stems().get(0);
            var b = group.stems().get(group.stems().size() - 1);
            // The complete physical beam occupies the final gap deliberately withheld by
            // the existing shaft-cap proof. There is no increased head-distance radius.
            if (bow.start().y() < a.endY()
                    || bow.start().y() - a.endY() > gap
                    || bow.end().y() < b.endY()
                    || bow.end().y() - b.endY() > gap) continue;
            if (!PrintedSlurOwners.rawShaftCapSupports(
                    bow.end(), end, false, gray, width, height, gap, heads)) continue;
            if (found != null) return Optional.empty();
            found = group;
            ending = end;
        }
        if (found == null) return Optional.empty();
        return Optional.of(
                new ScoreSlurPages.Span(
                        id,
                        new ScoreSlurPages.Anchor(page, start.get().owner()),
                        new ScoreSlurPages.Anchor(page, ending.owner()),
                        ScoreSlurSpan.Placement.BELOW,
                        ScoreSlurSpan.LineType.SOLID,
                        List.of(
                                new ScoreSlurSpan.ArcEvidence(
                                        page,
                                        bow.start().x() / (float) width,
                                        bow.start().y() / height,
                                        bow.end().x() / (float) width,
                                        bow.end().y() / height))));
    }
}
