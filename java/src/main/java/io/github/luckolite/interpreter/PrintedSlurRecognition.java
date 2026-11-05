// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Final page-slot orchestration; physical page and written meter are explicit caller context. */
final class PrintedSlurRecognition {
    record Result(
            ScorePageInterpretation score,
            Map<PrintedSlurOwners.Status, Integer> statuses,
            Set<Integer> unboundOwners,
            int unprovedStaffs,
            boolean limitReached,
            boolean alreadyCaptured,
            PrintedPhraseEndpoints.Pending pending) {
        Result(
                ScorePageInterpretation score,
                Map<PrintedSlurOwners.Status, Integer> statuses,
                Set<Integer> unboundOwners,
                int unprovedStaffs,
                boolean limitReached,
                boolean alreadyCaptured) {
            this(
                    score,
                    statuses,
                    unboundOwners,
                    unprovedStaffs,
                    limitReached,
                    alreadyCaptured,
                    null);
        }

        Result withPending(PrintedPhraseEndpoints.Pending value) {
            return new Result(
                    score,
                    statuses,
                    unboundOwners,
                    unprovedStaffs,
                    limitReached,
                    alreadyCaptured,
                    value);
        }

        Result {
            Objects.requireNonNull(score);
            statuses = Map.copyOf(statuses);
            unboundOwners = Set.copyOf(unboundOwners);
        }
    }

    private PrintedSlurRecognition() {}

    private record TieEnd(int staffIndex, int staffCount, int pitch, long tick) {}

    /** Use only a page's actually retained opening meter; never invent a four-beat context. */
    static Optional<Result> captureObserved(
            ScorePageInterpretation score,
            int physicalPage,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs) {
        Objects.requireNonNull(score);
        if (physicalPage < 0) throw new IllegalArgumentException("Missing physical source page");
        ScoreMeterChange opening = null;
        for (var change : score.meterChanges()) if (change.measureIndex() == 0) opening = change;
        if (opening == null) {
            if (!score.slurSource().spans().isEmpty() || !score.slurSource().candidates().isEmpty())
                return Optional.of(new Result(score, Map.of(), Set.of(), 0, false, true));
            var heads =
                    PrintedSlurContext.geometryHeads(
                            score.notes(), score.measures(), width, height);
            var prepared =
                    PrintedSlurFrames.prepare(gray, width, height, staffs, score.notes(), heads);
            labels =
                    OmrScoreInterpreter.printedSlurLabels(
                            labels, gray, width, height, score.measures(), heads);
            var captured =
                    PrintedSlurPageCapture.captureGeometry(
                            physicalPage,
                            score.notes().size(),
                            gray,
                            labels,
                            width,
                            height,
                            prepared.frames(),
                            score.notes());
            var supplemented =
                    WholeStaffSlurRules.append(
                            physicalPage,
                            score,
                            gray,
                            labels,
                            width,
                            height,
                            staffs,
                            heads,
                            prepared,
                            captured,
                            Set.of(),
                            true);
            captured = supplemented.captured();
            var result =
                    captured.limitReached() || captured.source().candidates().isEmpty()
                            ? score
                            : score.withSlurSource(captured.source());
            return Optional.of(
                    new Result(
                                    result,
                                    captured.statuses(),
                                    supplemented.unboundOwners(),
                                    supplemented.unprovedStaffs(),
                                    captured.limitReached(),
                                    false)
                            .withPending(
                                    PrintedPhraseEndpoints.retain(
                                            score, captured, gray, width, height, physicalPage)));
        }
        return Optional.of(
                capture(
                        score,
                        physicalPage,
                        gray,
                        labels,
                        width,
                        height,
                        staffs,
                        new ScoreMeterMap(opening.quarterBeats(), score.meterChanges()),
                        score.keyChanges()));
    }

    static Result capture(
            ScorePageInterpretation score,
            int physicalPage,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            ScoreMeterMap meter,
            List<ScoreKeyChange> keys) {
        Objects.requireNonNull(score);
        Objects.requireNonNull(meter);
        Objects.requireNonNull(keys);
        if (physicalPage < 0
                || width <= 0
                || height <= 0
                || gray == null
                || gray.length != (long) width * height
                || labels != null && labels.length != gray.length)
            throw new IllegalArgumentException("Invalid explicit printed page source");
        // A retained or edited source already owns its immutable spans. Never replace it
        // by reinterpreting the same pixels with a new selection order or musical context.
        if (!score.slurSource().spans().isEmpty() || !score.slurSource().candidates().isEmpty())
            return new Result(score, Map.of(), Set.of(), 0, false, true);
        var written = ScorePlacementMap.resolveWritten(score.notes(), meter);
        var pitches = new HashMap<Integer, Integer>();
        for (int i = 0; i < score.notes().size(); i++) {
            // The decoder has already applied printed accidental state to retained notes.
            // Reuse its existing final tie-guard pitch policy; do not infer an unknown key.
            int pitch = ScoreTiePitchGuard.midi(score.notes().get(i), keys);
            if (pitch >= 0 && pitch <= 127) pitches.put(i, pitch);
        }
        var heads =
                PrintedSlurContext.heads(
                        score.notes(),
                        score.measures(),
                        written,
                        meter,
                        Map.of(),
                        pitches,
                        width,
                        height);
        var prepared = PrintedSlurFrames.prepare(gray, width, height, staffs, score.notes(), heads);
        var ties = retainedTies(score.notes(), written, heads);
        labels =
                OmrScoreInterpreter.printedSlurLabels(
                        labels, gray, width, height, score.measures(), heads);
        var captured =
                PrintedSlurPageCapture.capture(
                        physicalPage,
                        score.notes().size(),
                        gray,
                        labels,
                        width,
                        height,
                        prepared.frames(),
                        ties,
                        score.notes());
        // The bounded scanner discards all partial spans if its admission/run cap is reached.
        if (captured.limitReached())
            return new Result(
                    score,
                    captured.statuses(),
                    prepared.unboundOwners(),
                    prepared.unprovedStaffs(),
                    true,
                    false);
        var supplemented =
                WholeStaffSlurRules.append(
                        physicalPage,
                        score,
                        gray,
                        labels,
                        width,
                        height,
                        staffs,
                        heads,
                        prepared,
                        captured,
                        ties,
                        false);
        captured = supplemented.captured();
        var result = recoverDynamicContact(score, captured.source(), physicalPage, gray,
                width, height, prepared.frames());
        return new Result(
                        result,
                        captured.statuses(),
                        supplemented.unboundOwners(),
                        supplemented.unprovedStaffs(),
                        false,
                        false)
                .withPending(
                        PrintedPhraseEndpoints.retain(
                                score, captured, gray, width, height, physicalPage));
    }

    private static ScorePageInterpretation recoverDynamicContact(
            ScorePageInterpretation score, ScoreSlurSource source, int page, byte[] gray,
            int width, int height, List<PrintedSlurPageCapture.Frame<PrintedSlurOwners.Head>> frames) {
        var dynamics = new ArrayList<>(score.dynamicChanges());
        var levels = new HashMap<ScoreTechniqueChange.Attack, Integer>();
        for (int i = 0; i < dynamics.size(); i++) {
            var d = dynamics.get(i);
            if (d.direction() == 0 && (d.decibels() == ScoreDynamicsDetector.level("mf")
                    || d.decibels() == ScoreDynamicsDetector.level("m"))
                    && !d.sharedStaffs())
                levels.merge(new ScoreTechniqueChange.Attack(d.measureIndex(),
                        Float.floatToIntBits(d.positionInMeasure()), d.staffIndex(), d.staffCount()),
                        i, (a, b) -> -1);
        }
        var candidates = new ArrayList<>(source.candidates());
        for (var frame : frames) {
            var heads = frame.heads().stream()
                    .sorted(Comparator.comparingDouble(PrintedSlurOwners.Head::x)).toList();
            for (int i = 0; i + 2 < heads.size(); i++) {
                var a = heads.get(i);
                var b = heads.get(i + 1);
                var c = heads.get(i + 2);
                var first = score.notes().get(a.owner());
                Integer level = levels.get(ScoreTechniqueChange.attackOf(first));
                if (level == null || level < 0
                        || !eligibleDynamicHeads(score, a, b, c)) continue;
                var bow = dynamicRidge(gray, width, height, a, c, frame.track());
                if (bow == null || bow.above()) continue;
                var bound = PrintedSlurOwners.bind("printed-page-" + page + "-dynamic-arc-" + a.owner(),
                        page, bow, frame.heads(), gray, width, height, frame.track());
                if (bound.span().isEmpty()) continue;
                var span = bound.span().orElseThrow();
                if (span.start().noteIndex() != a.owner() || span.end().noteIndex() != c.owner()
                        || candidates.stream().anyMatch(old -> sameOwners(old, span))
                        || source.spans().stream().anyMatch(old -> sameOwners(old, span))) continue;
                candidates.add(span);
                var d = dynamics.get(level);
                if (d.decibels() == ScoreDynamicsDetector.level("m")
                        && joinedForte(gray, width, height, bow, frame.track()))
                    dynamics.set(level, new ScoreDynamicChange(d.measureIndex(), d.positionInMeasure(),
                            d.staffIndex(), d.staffCount(), d.endMeasureIndex(), d.endPosition(),
                            ScoreDynamicsDetector.level("mf"), 0, d.sharedStaffs(),
                            d.fixedTarget(), d.sharedTiming()));
            }
        }
        var slurs = new ScoreSlurSource(source.spans(), source.noteOrigins(), candidates);
        if (dynamics.equals(score.dynamicChanges()))
            return slurs.spans().isEmpty() && slurs.candidates().isEmpty()
                    ? score : score.withSlurSource(slurs);
        return new ScorePageInterpretation(score.measures(), score.notes(), score.firstMeasureNumber(),
                score.keyChanges(), score.tempoChanges(), score.meterChanges(), score.rests(),
                score.techniqueChanges(), dynamics, score.playbackDirections(), score.expressiveEvents(),
                slurs, score.openingPickup(), score.tremoloSource(), score.multiRestGroups(),
                score.beamSource(), score.clockContextSource());
    }

    /** The underside of an owned bow must retain a crossed shaft and a separate returning foot. */
    private static boolean joinedForte(
            byte[] gray, int width, int height, PrintedSlurContours.Bow bow, StaffPitchTrack track) {
        float gap = track.at(bow.start().x())[1];
        int deepest = 0, shaft = -1, top = -1;
        for (var point : bow.points()) {
            int x = point.x(), y = Math.round(point.y()), end = y;
            while (end < height && end - y < 2 * gap && (gray[end * width + x] & 255) < 140)
                end++;
            if (end - y > deepest) {
                deepest = end - y;
                shaft = x;
                top = y;
            }
        }
        if (deepest < gap * 1.35f || shaft - 2 * gap < 1 || shaft + gap >= width - 1) return false;
        int left = shaft - Math.round(gap * 1.25f), right = shaft + Math.round(gap * .85f);
        int y0 = top + Math.round(gap * .34f);
        int[] lows = new int[Math.round(gap * 2.6f)], highs = lows.clone();
        if (y0 + lows.length >= height) return false;
        int n = 0;
        for (int y = y0; n < lows.length; y++) {
            int row = y * width, x = shaft;
            while (x >= left && (gray[row + x] & 255) >= 140) x--;
            if (x < left || shaft - x > 2) break;
            int lo = x, hi = x;
            while (lo > left && (gray[row + lo - 1] & 255) < 140) lo--;
            while (hi < right && (gray[row + hi + 1] & 255) < 140) hi++;
            if (lo == left || hi == right) return false;
            lows[n] = lo;
            highs[n++] = hi;
            shaft = (lo + hi + 1) / 2;
        }
        if (n < gap * 1.8f || n > gap * 2.35f) return false;
        int mid = n / 2, stem = highs[mid] - lows[mid] + 1;
        if (stem < gap * .3f || stem > gap * .7f
                || highs[mid] - highs[n - 3] < gap * .35f) return false;
        int crossbars = 0, returnRows = 0;
        for (int i = 0; i < n; i++) {
            int w = highs[i] - lows[i] + 1;
            if (i >= gap * .15f && i < gap * .5f && w >= stem * 1.6f
                    && highs[i] >= highs[i + 4] + 1 && lows[i] <= lows[i + 4] - 1)
                crossbars++;
            if (i >= n * .68f && i < n - 3) {
                int row = (y0 + i) * width, x = lows[i] - 1;
                while (x > lows[i] - gap * .65f && (gray[row + x] & 255) >= 140) x--;
                if (x > lows[i] - gap * .65f && x <= lows[i] - 2
                        && (gray[row + x - 2] & 255) < 140) returnRows++;
            }
        }
        return crossbars >= 2 && returnRows >= 3
                && lows[n - 3] < lows[n - 7] - gap * .25f;
    }

    private static boolean sameOwners(ScoreSlurPages.Span a, ScoreSlurPages.Span b) {
        return a.start().equals(b.start()) && a.end().equals(b.end())
                && a.placement() == b.placement();
    }

    private static boolean eligibleDynamicHeads(
            ScorePageInterpretation score, PrintedSlurOwners.Head a, PrintedSlurOwners.Head b,
            PrintedSlurOwners.Head c) {
        var first = score.notes().get(a.owner());
        if (!(a.y() > b.y() && b.y() > c.y()) || !(a.x() < b.x() && b.x() < c.x())) return false;
        for (var h : List.of(a, b, c)) {
            var n = score.notes().get(h.owner());
            if (h.grace() || h.staffIndex() != a.staffIndex() || h.staffCount() != a.staffCount()
                    || n.measureIndex() != first.measureIndex() || n.stemDirection() != 1
                    || n.beamCount() != 1 || n.crossStaffBeam()) return false;
        }
        return true;
    }

    /** Keep a thin upper edge where one bounded letter branch joins its underside. */
    private static PrintedSlurContours.Bow dynamicRidge(
            byte[] gray, int width, int height, PrintedSlurOwners.Head a, PrintedSlurOwners.Head c,
            StaffPitchTrack track) {
        float gap = track.at((a.x() + c.x()) * .5f)[1];
        float span = c.x() - a.x();
        if (gap < 8 || gap > 32 || span < gap * 2.5f || span > gap * 5
                || a.y() - c.y() < gap * .65f || a.y() - c.y() > gap * 1.5f) return null;
        int left = Math.round(a.x() + gap * .35f), right = Math.round(c.x() - gap * .35f);
        if (left < 1 || right >= width - 1) return null;
        var columns = new ArrayList<PrintedSlurContours.ForeignRun>();
        int thick = 0, deep = 0, shaded = 0, previous = -1;
        for (int x = left; x <= right; x++) {
            float headLine = a.y() + (c.y() - a.y()) * (x - a.x()) / span;
            int floor = Math.round(headLine + gap * .68f);
            int limit = Math.round(headLine + gap * 1.6f);
            if (floor < 1 || limit + gap * 2 >= height) return null;
            int y = floor;
            while (y < limit && (gray[y * width + x] & 255) >= 140) y++;
            if (y == limit || previous >= 0 && Math.abs(y - previous) > 2) return null;
            previous = y;
            int tail = y;
            while (tail < y + gap * 2 && (gray[tail * width + x] & 255) < 140) tail++;
            if (tail - y > gap * .38f) {
                if (x <= left + gap * .5f || x >= right - gap * .5f) return null;
                thick++;
                if (tail - y >= gap) deep++;
            }
            int paper = gray[(y - 1) * width + x] & 255;
            if (paper >= 145 && paper <= 229) shaded++;
            columns.add(new PrintedSlurContours.ForeignRun(
                    x, List.of(new PrintedSlurContours.RawBand(y, y))));
        }
        if (deep < 2 || thick > columns.size() * .3f || shaded < columns.size() * .6f) return null;
        return PrintedSlurContours.fitPixelCells(columns, track);
    }

    private static Set<PrintedSlurOwners.Tie> retainedTies(
            List<ScoreNoteEvent> notes,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> written,
            List<PrintedSlurOwners.Head> heads) {
        // Index exact ordinary endings once; a long page must not compare every pair.
        // A collision preserves ambiguity rather than choosing a voice or note owner.
        var endings = new HashMap<TieEnd, Integer>();
        for (var head : heads) {
            var placement = written.get(head.owner());
            if (head.soundingPitch() < 0 || placement.grace()) continue;
            long duration = Math.round(placement.durationBeats() * 10080);
            if (duration <= 0 || head.attack() > Long.MAX_VALUE - duration) continue;
            var key =
                    new TieEnd(
                            head.staffIndex(),
                            head.staffCount(),
                            head.soundingPitch(),
                            head.attack() + duration);
            endings.merge(key, head.owner(), (a, b) -> -1);
        }
        var ties = new HashSet<PrintedSlurOwners.Tie>();
        for (var current : heads) {
            if (!notes.get(current.owner()).tiedFromPrevious()
                    || current.soundingPitch() < 0
                    || written.get(current.owner()).grace()) continue;
            int prior =
                    endings.getOrDefault(
                            new TieEnd(
                                    current.staffIndex(),
                                    current.staffCount(),
                                    current.soundingPitch(),
                                    current.attack()),
                            -1);
            if (prior >= 0 && prior != current.owner())
                ties.add(new PrintedSlurOwners.Tie(prior, current.owner()));
        }
        return Set.copyOf(ties);
    }
}
