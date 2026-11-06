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
        var accepted = bow;
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
            var curve = bow;
            if (!ownsCaps(curve, start.get(), end, a, b, gray, width, height, gap, heads)) {
                if (group.source().bridges().get(0) != 1
                        || Math.abs(b.x() - bow.end().x()) > gap * 1.4f) continue;
                curve = closedBow(gray, width, height, observedFrame.track(), a, b, gap);
                if (curve == null
                        || !ownsCaps(curve, start.get(), end, a, b, gray, width, height, gap, heads))
                    continue;
            }
            accepted = curve;
            if (found != null) return Optional.empty();
            found = group;
            ending = end;
        }
        if (found == null) return Optional.empty();
        return Optional.of(span(id, page, start.get(), ending, accepted, width, height));
    }

    private static ScoreSlurPages.Span span(
            String id, int page, PrintedSlurOwners.Location start,
            PrintedSlurOwners.Location end, PrintedSlurContours.Bow bow,
            int width, int height) {
        return new ScoreSlurPages.Span(
                        id,
                        new ScoreSlurPages.Anchor(page, start.owner()),
                        new ScoreSlurPages.Anchor(page, end.owner()),
                        ScoreSlurSpan.Placement.BELOW,
                        ScoreSlurSpan.LineType.SOLID,
                        List.of(
                                new ScoreSlurSpan.ArcEvidence(
                                        page,
                                        bow.start().x() / (float) width,
                                        bow.start().y() / height,
                                        bow.end().x() / (float) width,
                                        bow.end().y() / height)));
    }

    static Optional<ScoreSlurPages.Span> recoverClosed(
            ClosedCrossStaffBeamGroups.Group group, int page, byte[] gray, int width, int height,
            List<? extends PrintedSlurOwners.Location> heads, StaffPitchTrack track) {
        if (group.source().bridges().get(0) != 1 || track == null || !track.verified())
            return Optional.empty();
        int last = group.owners().size() - 1;
        var start = heads.get(group.owners().get(0));
        var end = heads.get(group.owners().get(last));
        if (start.staffIndex() != group.pair().lower().index()
                || end.staffIndex() != group.pair().upper().index()) return Optional.empty();
        var a = group.stems().get(0);
        var b = group.stems().get(last);
        var bow = closedBow(gray, width, height, track, a, b, group.gap());
        if (bow == null
                || !ownsCaps(bow, start, end, a, b, gray, width, height, group.gap(), heads))
            return Optional.empty();
        return Optional.of(span("printed-page-" + page + "-cross-staff-arc-" + start.owner(),
                page, start, end, bow, width, height));
    }

    private static boolean ownsCaps(
            PrintedSlurContours.Bow bow, PrintedSlurOwners.Location start,
            PrintedSlurOwners.Location end, PairedTremoloInk.Stem a, PairedTremoloInk.Stem b,
            byte[] gray, int width, int height, float gap,
            List<? extends PrintedSlurOwners.Location> heads) {
        return !bow.above()
                && Math.abs(bow.start().x() - a.x()) <= gap * .4f
                && Math.abs(bow.end().x() - b.x()) <= gap * .4f
                && bow.start().y() >= a.endY() && bow.start().y() - a.endY() <= gap
                && bow.end().y() >= b.endY() && bow.end().y() - b.endY() <= gap
                && PrintedSlurOwners.rawShaftCapSupports(
                        bow.start(), start, false, gray, width, height, gap, heads)
                && PrintedSlurOwners.rawShaftCapSupports(
                        bow.end(), end, false, gray, width, height, gap, heads);
    }

    /** Sample only independent thin ink below a completely proved cross-staff rail. */
    private static PrintedSlurContours.Bow closedBow(
            byte[] gray, int width, int height, StaffPitchTrack track,
            PairedTremoloInk.Stem a, PairedTremoloInk.Stem b, float gap) {
        int span = b.x() - a.x();
        if (span < gap * 3 || span > Math.min(511, gap * 32)
                || !track.verifiedAt(a.x()) || !track.verifiedAt(b.x())) return null;
        var columns = new ArrayList<PrintedSlurContours.ForeignRun>();
        int ambiguous = 0;
        for (int x = a.x(); x <= b.x(); x++) {
            float rail = a.endY() + (b.endY() - a.endY()) * (x - a.x()) / (float) span;
            float[] frame = track.at(x);
            var bands = new ArrayList<PrintedSlurContours.RawBand>();
            int low = Math.round(rail + gap * .21f), high = Math.round(rail + gap * 2.7f);
            if (low < 0 || high >= height) return null;
            for (int y = low; y <= high;) {
                while (y <= high && (gray[y * width + x] & 255) >= 165) y++;
                int start = y;
                while (y <= high && (gray[y * width + x] & 255) < 165) y++;
                if (start >= y || start == low || y > high || y - start > gap * .46f) continue;
                float mid = (start + y - 1) * .5f;
                int step = Math.round((frame[0] - mid) / frame[1]);
                if (step >= 0 && step <= 4
                        && Math.abs(mid - (frame[0] - step * frame[1])) <= gap * .2f) continue;
                bands.add(new PrintedSlurContours.RawBand(start, y - 1));
            }
            if (bands.size() == 1) columns.add(new PrintedSlurContours.ForeignRun(x, bands));
            if (bands.size() > 1) ambiguous++;
        }
        if (ambiguous > span * .03f || columns.size() < span * .85f) return null;
        return PrintedSlurContours.fitPixelCells(columns, track);
    }
}
