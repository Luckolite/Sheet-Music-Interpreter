// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Same-pass completion of declined phrase caps from independently captured literal attacks. */
final class PrintedPhraseEndpoints {
    static final long MAX_WORK = 128L * 1024 * 1024;

    record Pending(
            ScorePageInterpretation original,
            List<PrintedSlurPageCapture.Declined> declined,
            String rawSha,
            int width,
            int height,
            int page) {
        Pending {
            declined = List.copyOf(declined);
        }
    }

    static Pending retain(
            ScorePageInterpretation score,
            PrintedSlurPageCapture.Result captured,
            byte[] gray,
            int w,
            int h,
            int page) {
        if (captured.limitReached()
                || captured.declined().isEmpty()
                || captured.declined().size() > 4096
                || gray == null
                || gray.length != (long) w * h
                || gray.length > MAX_WORK / 2) return null;
        return new Pending(
                score, captured.declined(), ScoreClockContextCapture.digest(gray), w, h, page);
    }

    static ScorePageInterpretation complete(
            ScorePageInterpretation score,
            Pending pending,
            PrintedBeamSource.Capture capture,
            byte[] gray,
            byte[] labels,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs,
            int page,
            ClosedCrossStaffBeamGroups.Result closed,
            List<? extends PrintedSlurOwners.Location> heads) {
        if (pending == null
                || pending.declined().size() > 4096
                || capture == null
                || !capture.statistics().refusal().isEmpty()
                || pending.page() != page
                || pending.width() != w
                || pending.height() != h
                || gray == null
                || gray.length != (long) w * h
                || gray.length > MAX_WORK / 2
                || !pending.original().notes().equals(score.notes())
                || !pending.original().measures().equals(score.measures())
                || !pending.rawSha().equals(ScoreClockContextCapture.digest(gray))) return score;
        boolean crossReady =
                closed != null
                        && !closed.groups().isEmpty()
                        && closed.matches(gray, labels, w, h, score.measures(), score.notes());
        ScoreBeamSource liveSource = crossReady ? closed.source() : capture.source();
        long members = 0;
        for (var group : liveSource.groups())
            for (var col : group.columns()) {
                members += col.members().size();
                if (members > MAX_WORK) return score;
            }
        long oldEvidence = 0;
        for (var arc : score.slurSource().spans()) {
            oldEvidence += 1L + arc.evidence().size();
            if (oldEvidence > MAX_WORK) return score;
        }
        for (var arc : score.slurSource().candidates()) {
            oldEvidence += 1L + arc.evidence().size();
            if (oldEvidence > MAX_WORK) return score;
        }
        long notes = score.notes().size(),
                groups = liveSource.groups().size(),
                declines = pending.declined().size();
        // Includes both endpoint maps, per-group member/rival scans, source indexing and both page
        // hashes.
        long work =
                2L * gray.length
                        + 8_000_000L
                        + 8 * oldEvidence
                        + declines
                                * (8 * members
                                        + 8 * notes
                                        + 8 * groups * notes
                                        + 8 * groups * score.measures().size()
                                        + 8 * groups);
        if (work < 0 || work > MAX_WORK) return score;
        // Prepay the unchanged endpoint ink probes, including exposed-shaft head exclusions.
        for (var observed : pending.declined())
            for (var cap : List.of(observed.bow().start(), observed.bow().end())) {
                var frame = observed.frame();
                double gap = frame.track().at(cap.x())[1];
                if (!Double.isFinite(gap) || gap <= 0) return score;
                long near = 0;
                for (var head : frame.heads()) {
                    double dx = Math.abs(head.x() - cap.x()) / gap;
                    double clear =
                            (observed.bow().above() ? head.y() - cap.y() : cap.y() - head.y())
                                    / gap;
                    if (dx <= 1.1 && clear >= -.6 && clear <= 11) near++;
                }
                double cells = Math.ceil(gap * .4) + 3,
                        rows = Math.ceil(gap * 11) + 3,
                        body = Math.ceil(gap * 2) + 3;
                double cost =
                        2
                                * (near
                                                * (cells * rows * (frame.heads().size() + 1d)
                                                        + 4 * body * body)
                                        + 4 * body * body);
                if (!Double.isFinite(cost) || cost > MAX_WORK - work) return score;
                work += (long) Math.ceil(cost);
            }
        if (crossReady)
            for (var observed : pending.declined()) {
                if (observed.bow().above()
                        || observed.status() != PrintedSlurOwners.Status.NO_END_OWNER) continue;
                var cap = observed.bow().end();
                double gap = observed.frame().track().at(cap.x())[1];
                for (var group : closed.groups()) {
                    int owner = group.owners().get(group.owners().size() - 1);
                    var end = heads.get(owner);
                    var stem = group.stems().get(group.stems().size() - 1);
                    if (cap.y() < stem.endY()
                            || cap.y() - stem.endY() > gap
                            || Math.abs(end.x() - .6 * gap - cap.x()) > .4 * gap) continue;
                    double cost =
                            (Math.ceil(.4 * gap) + 3)
                                    * (Math.ceil(Math.abs(cap.y() - end.y()) + gap) + 3)
                                    * (heads.size() + 1d);
                    if (!Double.isFinite(cost) || cost > MAX_WORK - work) return score;
                    work += (long) Math.ceil(cost);
                }
            }
        if (crossReady) {
            // Charge whole-curve scans and their raw cap checks before adding any new evidence.
            for (var group : closed.groups()) {
                if (group.source().bridges().get(0) != 1) continue;
                double gap = group.gap();
                var start = group.stems().get(0);
                var end = group.stems().get(group.stems().size() - 1);
                double scan = (end.x() - start.x() + 1d) * (16 + Math.ceil(gap * 5));
                double cap = (Math.ceil(gap * .4) + 3)
                        * (Math.abs(start.headY() - start.endY())
                                + Math.abs(end.headY() - end.endY()) + 2 * gap + 6)
                        * (heads.size() + 1d);
                double cost = (scan + cap) * (declines + 1);
                if (!Double.isFinite(cost) || cost > MAX_WORK - work) return score;
                work += (long) Math.ceil(cost);
            }
        }
        var ids = new HashSet<String>();
        var evidence = new HashSet<List<ScoreSlurSpan.ArcEvidence>>();
        for (var arc : score.slurSource().spans()) {
            ids.add(arc.id());
            evidence.add(arc.evidence());
        }
        for (var arc : score.slurSource().candidates()) {
            ids.add(arc.id());
            evidence.add(arc.evidence());
        }
        var additions = new ArrayList<ScoreSlurPages.Span>();
        var beamVeto = new ObservedStemBeam(score.notes(), gray, w, h);
        for (var observed : pending.declined()) {
            var arc = unisonStart(observed, page, score, heads, staffs, gray, w, h);
            if (arc.isEmpty()) arc = terminal(observed, page, score, liveSource, gray, w, h, beamVeto);
            if (arc.isEmpty()
                    && crossReady
                    && observed.status() == PrintedSlurOwners.Status.NO_END_OWNER
                    && !observed.bow().above())
                arc =
                        CrossStaffPhraseEndpointInk.complete(
                                observed.id(),
                                page,
                                observed.bow(),
                                heads,
                                gray,
                                labels,
                                w,
                                h,
                                staffs,
                                score.measures(),
                                score.notes(),
                                liveSource,
                                observed.frame(),
                                closed);
            if (beamVeto.refused()) return score;
            if (arc.isEmpty()) continue;
            if (ids.add(arc.get().id()) && evidence.add(arc.get().evidence()))
                additions.add(arc.get());
        }
        if (crossReady) {
            for (var group : closed.groups()) {
                if (group.source().bridges().get(0) != 1) continue;
                int start = group.owners().get(0);
                int end = group.owners().get(group.owners().size() - 1);
                if (sameSpan(score.slurSource().spans(), start, end)
                        || sameSpan(score.slurSource().candidates(), start, end)
                        || sameSpan(additions, start, end)) continue;
                StaffPitchTrack track = null;
                for (var observed : pending.declined())
                    if (observed.frame().heads().stream().anyMatch(head -> head.owner() == start)) {
                        track = observed.frame().track();
                        break;
                    }
                var recovered = CrossStaffPhraseEndpointInk.recoverClosed(group, page, gray, w, h,
                        heads, track);
                if (recovered.isPresent() && ids.add(recovered.get().id())
                        && evidence.add(recovered.get().evidence())) additions.add(recovered.get());
            }
        }
        if (additions.isEmpty()) return score;
        var candidates = new ArrayList<>(score.slurSource().candidates());
        candidates.addAll(additions);
        var source =
                ScoreSlurSource.capture(
                        score.slurSource().spans(),
                        candidates,
                        List.of(new ScoreSlurPages.Page(page, score.notes().size())));
        var result = score.withSlurSource(source);
        if (!result.notes().equals(score.notes())
                || !result.measures().equals(score.measures())
                || !result.rests().equals(score.rests()))
            throw new IllegalStateException("Phrase metadata changed clock owners");
        return result.withClockContextSource(score.clockContextSource());
    }

    /** Separate the stemmed attack from a coincident stemless unison using its exposed shaft. */
    private static Optional<ScoreSlurPages.Span> unisonStart(
            PrintedSlurPageCapture.Declined observed, int page, ScorePageInterpretation score,
            List<? extends PrintedSlurOwners.Location> heads,
            List<PlayingTechniqueDetector.Staff> staffs, byte[] gray, int w, int h) {
        if (observed.status() != PrintedSlurOwners.Status.AMBIGUOUS_START
                && observed.status() != PrintedSlurOwners.Status.NO_START_OWNER)
            return Optional.empty();
        var bow = observed.bow();
        var track = observed.frame().track();
        var end = PrintedSlurOwners.capOwner(bow.end(), bow.above(), false,
                observed.frame().heads(), gray, w, h, track);
        if (end.isEmpty()) return Optional.empty();
        var b = end.get();
        var notes = score.notes();
        var last = notes.get(b.owner());
        var cap = bow.start();
        float gap = track.at(cap.x())[1];
        var possible = PrintedSlurOwners.displacedUnisonCap(cap, bow.above(), heads, heads,
                notes, gray, w, h, gap);
        if (possible.isEmpty()) return Optional.empty();
        var start = possible.get();
        var n = notes.get(start.owner());
        if (start.x() >= b.x() || start.staffCount() != b.staffCount()
                || n.measureIndex() > last.measureIndex()
                || last.measureIndex() - n.measureIndex() > 2) return Optional.empty();
        if (start.staffIndex() != b.staffIndex()) {
            if (!bow.above() || start.staffIndex() != 1 || b.staffIndex() != 0
                    || b.staffCount() != 2) return Optional.empty();
            var bar = score.measures().get(n.measureIndex());
            var lastBar = score.measures().get(last.measureIndex());
            if (Math.abs(bar.top() - lastBar.top()) * h > gap
                    || Math.abs(bar.bottom() - lastBar.bottom()) * h > gap)
                return Optional.empty();
            boolean braced = false;
            for (var pair : GrandStaffDynamics.bracedPairs(staffs, score.measures(), gray, w, h))
                if (start.y() >= pair.lower().top() - gap * 3
                        && start.y() <= pair.lower().bottom() + gap * 3
                        && Math.abs(track.at(b.x())[0] - pair.upper().bottom()) <= gap)
                    braced = true;
            if (!braced) return Optional.empty();
        }
        return Optional.of(new ScoreSlurPages.Span(observed.id(),
                new ScoreSlurPages.Anchor(page, start.owner()),
                new ScoreSlurPages.Anchor(page, b.owner()),
                bow.above() ? ScoreSlurSpan.Placement.ABOVE : ScoreSlurSpan.Placement.BELOW,
                ScoreSlurSpan.LineType.SOLID, List.of(new ScoreSlurSpan.ArcEvidence(page,
                        cap.x() / (float) w, cap.y() / h,
                        bow.end().x() / (float) w, bow.end().y() / h))));
    }

    private static boolean sameSpan(List<ScoreSlurPages.Span> spans, int start, int end) {
        return spans.stream().anyMatch(arc -> arc.start().noteIndex() == start
                && arc.end().noteIndex() == end);
    }
    static Optional<ScoreSlurPages.Span> terminal(
            PrintedSlurPageCapture.Declined observed,
            int page,
            ScorePageInterpretation score,
            ScoreBeamSource beams,
            byte[] gray,
            int w,
            int h,
            ObservedStemBeam beamVeto) {
        var frame = observed.frame();
        var bow = observed.bow();
        if (observed.status() != PrintedSlurOwners.Status.NO_START_OWNER
                && observed.status() != PrintedSlurOwners.Status.NO_END_OWNER)
            return Optional.empty();
        var ae =
                PrintedSlurOwners.rawCapEvidence(
                        bow.start(), bow.above(), true, frame.heads(), gray, w, h, frame.track());
        var be =
                PrintedSlurOwners.rawCapEvidence(
                        bow.end(), bow.above(), false, frame.heads(), gray, w, h, frame.track());
        var a = ae.owner();
        var b = be.owner();
        if (a.isEmpty())
            a =
                    terminalOwner(
                            bow.start(),
                            bow.above(),
                            true,
                            frame,
                            score,
                            beams,
                            page,
                            w,
                            h,
                            gray,
                            ae.possibleOwners());
        if (b.isEmpty())
            b =
                    terminalOwner(
                            bow.end(),
                            bow.above(),
                            false,
                            frame,
                            score,
                            beams,
                            page,
                            w,
                            h,
                            gray,
                            be.possibleOwners());
        if (a.isEmpty()
                || b.isEmpty()
                || a.get().owner() == b.get().owner()
                || a.get().x() >= b.get().x()
                || a.get().staffIndex() != b.get().staffIndex()
                || a.get().staffCount() != b.get().staffCount()) return Optional.empty();
        var span =
                new ScoreSlurPages.Span(
                        observed.id(),
                        new ScoreSlurPages.Anchor(page, a.get().owner()),
                        new ScoreSlurPages.Anchor(page, b.get().owner()),
                        bow.above() ? ScoreSlurSpan.Placement.ABOVE : ScoreSlurSpan.Placement.BELOW,
                        ScoreSlurSpan.LineType.SOLID,
                        List.of(
                                new ScoreSlurSpan.ArcEvidence(
                                        page,
                                        bow.start().x() / (float) w,
                                        bow.start().y() / h,
                                        bow.end().x() / (float) w,
                                        bow.end().y() / h)));
        if (beamVeto.owns(bow, span, frame.heads(), frame.track()) || beamVeto.refused())
            return Optional.empty();
        return Optional.of(span);
    }

    static Optional<PrintedSlurOwners.Location> terminalOwner(
            PrintedSlurContours.Point cap,
            boolean above,
            boolean start,
            PrintedSlurPageCapture.Frame<? extends PrintedSlurOwners.Location> frame,
            ScorePageInterpretation score,
            ScoreBeamSource beams,
            int page,
            int width,
            int height,
            byte[] gray,
            Set<Integer> possibleOwners) {
        var byOwner = new HashMap<Integer, PrintedSlurOwners.Location>();
        for (var head : frame.heads()) byOwner.put(head.owner(), head);
        var matches = new LinkedHashMap<Integer, PrintedSlurOwners.Location>();
        var needsHeadEdge = new HashSet<Integer>();
        for (var group : beams.groups()) {
            if (group.columns().size() < 2
                    || group.columns().size() > 32
                    || !group.hooks().isEmpty()) continue;
            var columns = new ArrayList<List<PrintedSlurOwners.Location>>();
            boolean valid = true;
            int measure = -1, staff = -1, count = -1;
            float previous = Float.NEGATIVE_INFINITY;
            for (var column : group.columns()) {
                var members = new ArrayList<PrintedSlurOwners.Location>();
                float left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
                for (var anchor : column.members()) {
                    var head = byOwner.get(anchor.noteIndex());
                    if (anchor.pageIndex() != page
                            || head == null
                            || anchor.noteIndex() >= score.notes().size()) {
                        valid = false;
                        break;
                    }
                    var n = score.notes().get(anchor.noteIndex());
                    if (head.grace() || n.kind() != ScoreNoteEvent.Kind.PITCHED) {
                        valid = false;
                        break;
                    }
                    if (measure < 0) {
                        measure = n.measureIndex();
                        staff = n.staffIndex();
                        count = n.staffCount();
                    }
                    if (n.measureIndex() != measure
                            || n.staffIndex() != staff
                            || n.staffCount() != count) {
                        valid = false;
                        break;
                    }
                    left = Math.min(left, head.x());
                    right = Math.max(right, head.x());
                    members.add(head);
                }
                if (!valid || left <= previous) {
                    valid = false;
                    break;
                }
                previous = right;
                columns.add(members);
            }
            if (!valid || measure < 0 || measure >= score.measures().size()) continue;
            var end = columns.get(start ? 0 : columns.size() - 1);
            var neighbor = columns.get(start ? 1 : columns.size() - 2);
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY, other = 0;
            for (var head : end) {
                min = Math.min(min, head.x());
                max = Math.max(max, head.x());
            }
            for (var head : neighbor) other += head.x();
            other /= neighbor.size();
            var region = score.measures().get(measure);
            float inner = ((min + max) * .5f + other) * .5f;
            float lo = start ? region.left() * width : inner,
                    hi = start ? inner : region.right() * width;
            if (!(lo <= cap.x() && cap.x() <= hi)) continue;
            boolean outward = start ? cap.x() <= min : cap.x() >= max;

            float[] local = frame.track().at(cap.x());
            float center = local[0] - 2 * local[1];
            boolean overlap = false;
            for (int ri = 0; ri < score.measures().size(); ri++)
                if (ri != measure) {
                    var otherRegion = score.measures().get(ri);
                    if (center >= otherRegion.top() * height
                            && center <= otherRegion.bottom() * height
                            && cap.x() >= otherRegion.left() * width
                            && cap.x() <= otherRegion.right() * width) overlap = true;
                }
            if (overlap) continue;
            var ids = new HashSet<Integer>();
            for (var head : end) ids.add(head.owner());
            if (!ids.containsAll(possibleOwners)) continue;
            boolean rival = false;
            for (var head : frame.heads())
                if (score.notes().get(head.owner()).measureIndex() == measure
                        && head.x() >= lo
                        && head.x() <= hi
                        && !ids.contains(head.owner())) rival = true;
            if (rival) continue;
            PrintedSlurOwners.Location chosen = null;
            boolean sameOuter = false;
            for (var head : end) {
                if (chosen == null || (above ? head.y() < chosen.y() : head.y() > chosen.y())) {
                    chosen = head;
                    sameOuter = false;
                } else if (head.y() == chosen.y()) sameOuter = true;
            }
            if (chosen == null || sameOuter) continue;
            float gap = frame.track().at(cap.x())[1];
            double clear = (above ? chosen.y() - cap.y() : cap.y() - chosen.y()) / gap;
            boolean rawShaft = !outward && end.size() == 1 && clear <= 11
                    && PrintedSlurOwners.rawShaftCapSupports(
                            cap, chosen, above, gray, width, height, gap, frame.heads());
            if (clear < -.6 || clear > 8.5 && !rawShaft
                    || !outward && end.size() < 2 && !rawShaft) continue;
            matches.put(chosen.owner(), chosen);
            if (!outward && !rawShaft) needsHeadEdge.add(chosen.owner());
        }
        if (matches.size() != 1) return Optional.empty();
        var chosen = matches.values().iterator().next();
        if (needsHeadEdge.contains(chosen.owner())
                && !PrintedSlurOwners.rawHeadCapSupports(
                        cap,
                        chosen,
                        above,
                        start,
                        gray,
                        width,
                        height,
                        frame.track().at(cap.x())[1])) return Optional.empty();
        return Optional.of(chosen);
    }
}
