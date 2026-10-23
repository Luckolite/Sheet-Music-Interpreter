// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.function.Consumer;

/** Raw thin returning bows in a verified physical staff frame; not musical slur ownership. */
final class PrintedSlurContours {
    private static final int MAX_RUNS = 200000;

    record Point(int x, float y) {}

    record Bow(List<Point> points, boolean above, float bendInGaps, float rmsInGaps) {
        Bow {
            points = List.copyOf(points);
        }

        Point start() {
            return points.get(0);
        }

        Point end() {
            return points.get(points.size() - 1);
        }
    }

    record Result(List<Bow> bows, boolean limitReached) {
        Result {
            bows = List.copyOf(bows);
        }
    }

    record Rejection(int left, float top, int right, float bottom, String reason) {}

    interface HeadEvidence {
        boolean contains(int x, int y);

        boolean closes(Point cap, int x, int y, boolean first);

        default long returningOwnerCost() {
            return Long.MAX_VALUE;
        }

        default boolean ownsReturningBow(Bow bow) {
            return false;
        }
    }

    record RawBand(int first, int last) {}

    record RawColumn(int x, int first, List<Integer> rawProfile, List<RawBand> members) {
        RawColumn {
            rawProfile = List.copyOf(rawProfile);
            members = List.copyOf(members);
        }
    }

    record RawResult(
            Result result,
            Map<Bow, List<RawColumn>> witnesses,
            Map<Bow, List<RawColumn>> selectedContourWitnesses) {
        RawResult {
            witnesses = Collections.unmodifiableMap(new IdentityHashMap<>(witnesses));
            selectedContourWitnesses =
                    Collections.unmodifiableMap(new IdentityHashMap<>(selectedContourWitnesses));
        }
    }

    private interface RawSink {
        void accept(Bow bow, List<Run> members, List<Run> selected);
    }

    private record Run(
            int x,
            float y,
            float rectified,
            float gap,
            float upper,
            float lower,
            List<RawBand> members) {}

    private record Frames(float[] bottoms, float[] gaps) {}

    private PrintedSlurContours() {}

    static Result find(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            StaffPitchTrack track) {
        return find(gray, labels, width, height, left, top, right, bottom, track, null);
    }

    static Result find(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            StaffPitchTrack track,
            Consumer<Rejection> audit) {
        return find(gray, labels, width, height, left, top, right, bottom, track, audit, null);
    }

    static Result find(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            StaffPitchTrack track,
            Consumer<Rejection> audit,
            HeadEvidence heads) {
        return findRaw(
                gray, labels, width, height, left, top, right, bottom, track, audit, heads, null);
    }

    static RawResult findWithRawEvidence(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            StaffPitchTrack track,
            Consumer<Rejection> audit,
            HeadEvidence heads) {
        var witnesses = new IdentityHashMap<Bow, List<RawColumn>>();
        var selectedWitnesses = new IdentityHashMap<Bow, List<RawColumn>>();
        Result result =
                findRaw(
                        gray,
                        labels,
                        width,
                        height,
                        left,
                        top,
                        right,
                        bottom,
                        track,
                        audit,
                        heads,
                        (bow, members, selected) -> {
                            witnesses.put(bow, rawColumns(gray, width, members));
                            selectedWitnesses.put(bow, rawColumns(gray, width, selected));
                        });
        return new RawResult(result, witnesses, selectedWitnesses);
    }

    private static Result findRaw(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            StaffPitchTrack track,
            Consumer<Rejection> audit,
            HeadEvidence heads,
            RawSink rawSink) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || labels != null && labels.length != gray.length
                || track == null
                || !track.verified()
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || left >= right
                || top >= bottom) return new Result(List.of(), false);
        var runs = new ArrayList<Run>();
        float[] bottoms = new float[width], gaps = new float[width];
        for (int x = 0; x < width; x++) {
            float[] at = track.at(x);
            bottoms[x] = at[0];
            gaps[x] = at[1];
        }
        var frames = new Frames(bottoms, gaps);
        int[] parents = new int[8192];
        List<Integer> previous = List.of(),
                beforePrevious = List.of(),
                threeBack = List.of(),
                fourBack = List.of(),
                fiveBack = List.of(),
                sixBack = List.of();
        for (int x = left; x <= right; x++) {
            float base = bottoms[x], gap = gaps[x];
            if (!Float.isFinite(base + gap) || gap < 5 || gap > 100)
                return new Result(List.of(), false);
            var current = new ArrayList<Integer>();
            int maxThickness = Math.max(2, Math.round(gap * .42f));
            for (int y = top; y <= bottom; ) {
                if (!ink(gray, labels, width, height, x, y, base, gap, frames, heads)) {
                    y++;
                    continue;
                }
                int first = y;
                while (y <= bottom
                        && ink(gray, labels, width, height, x, y, base, gap, frames, heads)) y++;
                int last = y - 1;
                if (last - first + 1 > maxThickness
                        && (last - first + 1 > gap * 1.4f
                                || !thinAcross(
                                        gray, width, height, x, (first + last) / 2, maxThickness)))
                    continue;
                if (runs.size() == MAX_RUNS) return new Result(List.of(), true);
                if (runs.size() == parents.length)
                    parents = Arrays.copyOf(parents, Math.min(MAX_RUNS, parents.length * 2));
                float center = (first + last) * .5f;
                // Removing a rail can split one physical stroke into two runs in the same
                // column. Rejoin only a continuous thin raw stroke across that actual rail.
                if (!current.isEmpty()) {
                    int priorId = current.get(current.size() - 1);
                    Run prior = runs.get(priorId);
                    int priorTop = Math.round(base + prior.upper() * gap);
                    int priorBottom = Math.round(base + prior.lower() * gap);
                    boolean continuous = first - priorBottom <= maxThickness + 1;
                    boolean rail = false;
                    for (int yy = priorBottom + 1; yy < first && continuous; yy++) {
                        if ((gray[yy * width + x] & 255) > 205
                                || heads != null && heads.contains(x, yy)
                                || labels != null
                                        && (labels[yy * width + x]
                                                        == OmrMeasurePostProcessor.NOTEHEAD
                                                || labels[yy * width + x]
                                                        == OmrMeasurePostProcessor.CLEF_OR_KEY))
                            continuous = false;
                        for (int rule = 0; rule < 5; rule++)
                            if (Math.abs(yy - (base - rule * gap)) <= Math.max(2, gap * .3f)
                                    && followsRail(
                                            gray, width, height, x, yy, base, gap, rule, frames))
                                rail = true;
                    }
                    int thickness = last - priorTop + 1;
                    if (continuous
                            && rail
                            && (thickness <= maxThickness
                                    || thickness <= gap * 1.4f
                                            && thinAcross(
                                                    gray,
                                                    width,
                                                    height,
                                                    x,
                                                    (priorTop + last) / 2,
                                                    maxThickness))) {
                        center = (priorTop + last) * .5f;
                        runs.set(
                                priorId,
                                new Run(
                                        x,
                                        center,
                                        (center - base) / gap,
                                        gap,
                                        prior.upper(),
                                        (last - base) / gap,
                                        rawSink == null
                                                ? List.of()
                                                : appendRaw(prior.members(), first, last)));
                        link(priorId, previous, runs, parents, 1, gray, width, height, frames);
                        continue;
                    }
                }
                int id = runs.size();
                parents[id] = id;
                runs.add(
                        new Run(
                                x,
                                center,
                                (center - base) / gap,
                                gap,
                                (first - base) / gap,
                                (last - base) / gap,
                                rawSink == null ? List.of() : List.of(new RawBand(first, last))));
                current.add(id);
                boolean connected =
                        link(id, previous, runs, parents, 1, gray, width, height, frames);
                // A single missing raster column can bridge a thin contour; longer gaps cannot.
                if (!connected)
                    connected =
                            link(id, beforePrevious, runs, parents, 2, gray, width, height, frames);
                if (!connected) {
                    if (!bridgeRule(id, threeBack, runs, parents, gray, labels, width, track)
                            && !bridgeRule(id, fourBack, runs, parents, gray, labels, width, track)
                            && !bridgeRule(id, fiveBack, runs, parents, gray, labels, width, track))
                        bridgeRule(id, sixBack, runs, parents, gray, labels, width, track);
                }
            }
            sixBack = fiveBack;
            fiveBack = fourBack;
            fourBack = threeBack;
            threeBack = beforePrevious;
            beforePrevious = previous;
            previous = current;
        }
        var components = new LinkedHashMap<Integer, List<Run>>();
        for (int i = 0; i < runs.size(); i++)
            components
                    .computeIfAbsent(root(i, parents), ignored -> new ArrayList<>())
                    .add(runs.get(i));
        var bows = new ArrayList<Bow>();
        var fittedSelections = rawSink == null ? null : new IdentityHashMap<Bow, List<Run>>();
        for (var component : components.values()) {
            Bow bow = fit(component, audit, fittedSelections);
            List<Run> selected = fittedSelections == null ? null : fittedSelections.remove(bow);
            if (bow == null) continue;
            if (!capped(gray, labels, width, height, bow, track, true, heads, frames))
                reject(component, "uncapped-start", audit);
            else if (!capped(gray, labels, width, height, bow, track, false, heads, frames))
                reject(component, "uncapped-end", audit);
            else {
                bows.add(bow);
                if (rawSink != null) rawSink.accept(bow, component, selected);
            }
        }
        boolean fallbackLimit =
                orientedFallback(
                        gray, labels, width, height, left, top, right, bottom, track, heads, frames,
                        bows, rawSink);
        return new Result(bows, fallbackLimit);
    }

    /** Additive complete-ribbon route. Legacy axis tests and every old bow stay unchanged. */
    private static boolean orientedFallback(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            StaffPitchTrack track,
            HeadEvidence heads,
            Frames frames,
            List<Bow> existing,
            RawSink sink) {
        var runs = new ArrayList<Run>();
        var parents = new int[MAX_RUNS];
        var legacy = List.copyOf(existing);
        var planned = new ArrayList<RibbonCandidate>();
        boolean incomplete = false;
        long[] boldWork = {8_000_000};
        var previous = new ArrayList<Integer>();
        for (int x = left; x <= right; x++) {
            var current = new ArrayList<Integer>();
            float base = frames.bottoms()[x], gap = frames.gaps()[x];
            for (int y = top; y <= bottom; ) {
                if (!ink(gray, labels, width, height, x, y, base, gap, frames, heads)) {
                    y++;
                    continue;
                }
                int first = y;
                while (y <= bottom
                        && ink(gray, labels, width, height, x, y, base, gap, frames, heads)) y++;
                int last = y - 1;
                if (runs.size() == MAX_RUNS) return true;
                int id = runs.size();
                parents[id] = id;
                var run =
                        new Run(
                                x,
                                (first + last) * .5f,
                                ((first + last) * .5f - base) / gap,
                                gap,
                                (first - base) / gap,
                                (last - base) / gap,
                                List.of(new RawBand(first, last)));
                runs.add(run);
                current.add(id);
                // Literal adjacent-column contact only: never manufacture a missing column.
                for (int other : previous) {
                    Run p = runs.get(other);
                    int a = p.members().get(0).first(), b = p.members().get(0).last();
                    if (first <= b + 1 && last >= a - 1)
                        parents[root(id, parents)] = root(other, parents);
                }
            }
            previous = current;
        }
        var components = new LinkedHashMap<Integer, List<Run>>();
        for (int i = 0; i < runs.size(); i++)
            components.computeIfAbsent(root(i, parents), k -> new ArrayList<>()).add(runs.get(i));
        for (var component : components.values()) {
            if (component.size() < 12) continue;
            int lo = component.get(0).x(), hi = component.get(component.size() - 1).x();
            if (lo <= left || hi >= right || component.size() != hi - lo + 1) continue;
            boolean complete = true, needs = false;
            int normalMaximum = Integer.MAX_VALUE;
            for (int i = 0; i < component.size(); i++) {
                Run r = component.get(i);
                RawBand band = r.members().get(0);
                int count = band.last() - band.first() + 1,
                        maximum = Math.max(2, Math.round(r.gap() * .42f));
                normalMaximum = Math.min(normalMaximum, maximum);
                // Same absolute acquisition bound. Multiple runs/branches or clipping abstain.
                if (r.x() != lo + i
                        || band.first() <= top
                        || band.last() >= bottom
                        || count > r.gap() * 1.4f) {
                    complete = false;
                    break;
                }
                if (count > maximum
                        && !thinAcross(
                                gray,
                                width,
                                height,
                                r.x(),
                                (band.first() + band.last()) / 2,
                                maximum)) {
                    needs = true;
                    // Width is certified globally below, never by choosing a local angle.
                }
            }
            if (!complete || !needs) continue;
            var allCells = new HashSet<Long>();
            for (Run run : component)
                for (RawBand band : run.members())
                    for (int y = band.first(); y <= band.last(); y++)
                        allCells.add(FiniteSlurRibbon.pixel(run.x(), y));
            var proof = FiniteSlurRibbon.certify(allCells, normalMaximum);
            incomplete |= proof.limitReached();
            boolean bold =
                    !proof.complete() && boldReturning(component, heads, normalMaximum, boldWork);
            incomplete |= boldWork[0] < 0;
            if (!proof.complete() && !bold) continue;
            // Existing complete returning-curvature grammar independently supplies the role.
            var selected = new IdentityHashMap<Bow, List<Run>>();
            Bow bow = fit(component, null, selected);
            if (bow == null
                    || !capped(gray, labels, width, height, bow, track, true, heads, frames)
                    || !capped(gray, labels, width, height, bow, track, false, heads, frames))
                continue;
            boolean overlaps = false;
            for (Bow old : legacy)
                if (old.start().x() <= hi && old.end().x() >= lo) {
                    overlaps = true;
                    break;
                }
            if (overlaps) continue; // Never replace an old interpretation or choose between rivals.
            planned.add(new RibbonCandidate(bow, component, selected.get(bow)));
        }
        // A partial search cannot exclude an unsearched rival. Keep all legacy evidence,
        // but publish no new candidate from that incomplete search.
        if (incomplete) return true;
        planned.sort(
                Comparator.comparingInt((RibbonCandidate c) -> c.bow().start().x())
                        .thenComparingInt(c -> c.bow().end().x()));
        for (int i = 0; i < planned.size(); ) {
            int j = i + 1, rightEdge = planned.get(i).bow().end().x();
            while (j < planned.size() && planned.get(j).bow().start().x() <= rightEdge) {
                rightEdge = Math.max(rightEdge, planned.get(j).bow().end().x());
                j++;
            }
            // Entire overlap groups abstain: no first-winner/scan-order selection.
            if (j == i + 1) {
                var c = planned.get(i);
                existing.add(c.bow());
                if (sink != null) sink.accept(c.bow(), c.component(), c.selected());
            }
            i = j;
        }
        return false;
    }

    /** Complete engraved taper: both actual edges return, one coherent thickening, owned caps. */
    private static boolean boldReturning(
            List<Run> component, HeadEvidence heads, int thinMaximum, long[] work) {
        if (heads == null) return false;
        if (component.size() > 8192) {
            work[0] = -1;
            return false;
        }
        long ownerCost = heads.returningOwnerCost();
        if (ownerCost == Long.MAX_VALUE) return false;
        if (ownerCost < 0 || ownerCost > 4_000_000) {
            work[0] = -1;
            return false;
        }
        long cost = 64L * component.size() + 2L * ownerCost;
        if (cost > work[0]) {
            work[0] = -1;
            return false;
        }
        work[0] -= cost;
        int n = component.size();
        if (n < 12) return false;
        int[] sizes = new int[n];
        var upper = new ArrayList<Run>(n);
        var lower = new ArrayList<Run>(n);
        int maximum = 0;
        for (int i = 0; i < n; i++) {
            Run run = component.get(i);
            RawBand band = run.members().get(0);
            int size = band.last() - band.first() + 1;
            sizes[i] = size;
            maximum = Math.max(maximum, size);
            upper.add(
                    new Run(
                            run.x(),
                            band.first(),
                            run.upper(),
                            run.gap(),
                            run.upper(),
                            run.upper(),
                            List.of()));
            lower.add(
                    new Run(
                            run.x(),
                            band.last(),
                            run.lower(),
                            run.gap(),
                            run.lower(),
                            run.lower(),
                            List.of()));
        }
        if (maximum <= thinMaximum || sizes[0] > thinMaximum || sizes[n - 1] > thinMaximum)
            return false;
        // A raster row at each edge has at most one-row phase uncertainty. There must be
        // a single increase/decrease profile consistent with ALL observed widths.
        int[] prefix = new int[n], suffix = new int[n];
        int need = 0;
        for (int i = 0; i < n; i++) {
            need = Math.max(need, sizes[i] - 1);
            prefix[i] = need <= sizes[i] + 1 ? need : Integer.MAX_VALUE;
            if (i > 0 && prefix[i - 1] == Integer.MAX_VALUE) prefix[i] = Integer.MAX_VALUE;
        }
        need = 0;
        for (int i = n - 1; i >= 0; i--) {
            need = Math.max(need, sizes[i] - 1);
            suffix[i] = need <= sizes[i] + 1 ? need : Integer.MAX_VALUE;
            if (i + 1 < n && suffix[i + 1] == Integer.MAX_VALUE) suffix[i] = Integer.MAX_VALUE;
        }
        boolean taper = false;
        for (int i = n / 4; i <= n * 3 / 4; i++)
            if (prefix[i] != Integer.MAX_VALUE && suffix[i] != Integer.MAX_VALUE) taper = true;
        if (!taper) return false;
        Bow center = fit(component, null, null),
                top = fit(upper, null, null),
                bottom = fit(lower, null, null);
        return center != null
                && top != null
                && bottom != null
                && center.above() == top.above()
                && center.above() == bottom.above()
                && heads.ownsReturningBow(center);
    }

    private record RibbonCandidate(Bow bow, List<Run> component, List<Run> selected) {}

    private static List<RawColumn> rawColumns(byte[] gray, int width, List<Run> runs) {
        if (runs == null) throw new IllegalStateException("Missing selected contour identity");
        var columns = new ArrayList<RawColumn>();
        for (var run : runs) {
            int first = run.members().get(0).first(),
                    last = run.members().get(run.members().size() - 1).last();
            var profile = new ArrayList<Integer>();
            for (int y = first; y <= last; y++) profile.add(gray[y * width + run.x()] & 255);
            columns.add(new RawColumn(run.x(), first, profile, run.members()));
        }
        return List.copyOf(columns);
    }

    private static List<RawBand> appendRaw(List<RawBand> old, int first, int last) {
        var result = new ArrayList<RawBand>(old);
        result.add(new RawBand(first, last));
        return List.copyOf(result);
    }

    private static boolean ink(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int x,
            int y,
            float base,
            float gap,
            Frames frames,
            HeadEvidence heads) {
        int at = y * width + x, shade = gray[at] & 255;
        if (shade > 205
                || labels != null
                        && (labels[at] == OmrMeasurePostProcessor.NOTEHEAD
                                || labels[at] == OmrMeasurePostProcessor.CLEF_OR_KEY)) return false;
        if (heads != null && heads.contains(x, y)) return false;
        // A staff's curvature is not a second printed contour. All five rails are excluded.
        for (int rule = 0; rule < 5; rule++)
            if (Math.abs(y - (base - rule * gap)) <= Math.max(2, gap * .3f)
                    && followsRail(gray, width, height, x, y, base, gap, rule, frames))
                return false;
        int distance = Math.max(2, Math.round(gap * .65f));
        int paper =
                Math.max(
                        gray[Math.max(0, y - distance) * width + x] & 255,
                        gray[Math.min(height - 1, y + distance) * width + x] & 255);
        return paper >= shade + 20;
    }

    private static boolean followsRail(
            byte[] gray,
            int width,
            int height,
            int x,
            int y,
            float base,
            float gap,
            int rule,
            Frames frames) {
        float residual = y - (base - rule * gap);
        int left = 0, right = 0;
        for (int side = -1; side <= 1; side += 2)
            for (int step = 0; step < 3; step++) {
                float distance = 1 + step * .5f;
                int xx = Math.round(x + side * gap * distance);
                if (xx < 0 || xx >= width) continue;
                int yy = Math.round(frames.bottoms()[xx] - rule * frames.gaps()[xx] + residual);
                if (yy < 0 || yy >= height) continue;
                // Verified frames can round a thin rail onto either neighbouring raster row.
                boolean dark = false;
                for (int dy = -1; dy <= 1; dy++)
                    if (yy + dy >= 0
                            && yy + dy < height
                            && (gray[(yy + dy) * width + xx] & 255) <= 205) dark = true;
                if (dark) {
                    if (side < 0) left++;
                    else right++;
                }
            }
        return left == 3 || right == 3 || left >= 2 && right >= 2;
    }

    /** A steep thin stroke has a long vertical projection but a short transverse width. */
    private static boolean thinAcross(
            byte[] gray, int width, int height, int x, int y, int maximum) {
        if (y < 0 || y >= height) return false;
        int left = x, right = x;
        while (left > Math.max(0, x - maximum) && (gray[y * width + left - 1] & 255) <= 205) left--;
        while (right < Math.min(width - 1, x + maximum)
                && (gray[y * width + right + 1] & 255) <= 205) right++;
        return right - left + 1 <= maximum;
    }

    private static boolean link(
            int id,
            List<Integer> earlier,
            List<Run> runs,
            int[] parents,
            int dx,
            byte[] gray,
            int width,
            int height,
            Frames frames) {
        Run current = runs.get(id);
        boolean connected = false;
        for (int other : earlier) {
            Run previous = runs.get(other);
            float separation =
                    Math.max(
                            0,
                            Math.max(
                                    current.upper() - previous.lower(),
                                    previous.upper() - current.lower()));
            float maximum = dx == 1 ? .38f : .45f;
            boolean connects = Math.abs(current.rectified() - previous.rectified()) <= maximum;
            // Projection centres can jump where one stroke is split by a real staff rail.
            // Away from such an occlusion, ribbon edges cannot merge a nearby numeral.
            if (!connects && separation <= maximum) {
                int x = (current.x() + previous.x()) / 2;
                for (int rule = 0; rule < 5 && !connects; rule++) {
                    if ((current.rectified() + rule) * (previous.rectified() + rule) >= 0) continue;
                    int y = Math.round(frames.bottoms()[x] - rule * frames.gaps()[x]);
                    connects =
                            y >= 0
                                    && y < height
                                    && followsRail(
                                            gray,
                                            width,
                                            height,
                                            x,
                                            y,
                                            frames.bottoms()[x],
                                            frames.gaps()[x],
                                            rule,
                                            frames);
                }
            }
            if (connects) {
                parents[root(id, parents)] = root(other, parents);
                connected = true;
            }
        }
        return connected;
    }

    /** A staff/beam occlusion cannot manufacture a new phrase endpoint in the middle of a bow. */
    private static boolean capped(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            Bow bow,
            StaffPitchTrack track,
            boolean first,
            HeadEvidence heads,
            Frames frames) {
        var points = bow.points();
        Point end = first ? points.get(0) : points.get(points.size() - 1);
        Point inner =
                first
                        ? points.get(Math.min(7, points.size() - 1))
                        : points.get(Math.max(0, points.size() - 8));
        double slope = (end.y() - inner.y()) / (end.x() - inner.x());
        float gap = track.at(end.x())[1];
        int blank = 0, dark = 0;
        for (int d = 1; d <= Math.max(5, Math.round(gap * .9f)); d++) {
            int x = end.x() + (first ? -d : d);
            if (x < 0 || x >= width) return false;
            int y = (int) Math.round(end.y() + slope * (x - end.x()));
            if (y < 1 || y >= height - 1) return false;
            int radius = Math.max(1, Math.round(gap * .12f));
            if (heads != null)
                for (int dy = -radius; dy <= radius; dy++)
                    if (heads.closes(end, x, y + dy, first)) return true;
            float[] staff = track.at(x);
            boolean rule = false;
            for (int r = 0; r < 5; r++)
                if (Math.abs(y - (staff[0] - r * staff[1])) <= Math.max(1, gap * .18f)) rule = true;
            // Proximity to a subpixel rail cannot erase real white termination pixels.
            // Actual dark rail occlusion still resets the cap's blank evidence.
            if (rule && (gray[y * width + x] & 255) <= 205) {
                blank = 0;
                continue;
            }
            boolean present = false, occluded = false;
            for (int dy = -radius; dy <= radius; dy++) {
                int yy = y + dy, at = yy * width + x;
                if (at < 0 || at >= gray.length) continue;
                boolean staffInk = false;
                for (int r = 0; r < 5; r++)
                    if (Math.abs(yy - (staff[0] - r * staff[1])) <= Math.max(2, gap * .3f)
                            && followsRail(
                                    gray, width, height, x, yy, staff[0], staff[1], r, frames))
                        staffInk = true;
                if (staffInk) continue;
                if (labels != null
                        && (labels[at] == OmrMeasurePostProcessor.NOTEHEAD
                                || labels[at] == OmrMeasurePostProcessor.CLEF_OR_KEY)) {
                    occluded = true;
                    continue;
                }
                if ((gray[at] & 255) <= 205) present = true;
            }
            if (occluded) {
                blank = 0;
                continue;
            }
            if (present) {
                blank = 0;
                if (++dark >= 3) return false;
            } else if (++blank >= Math.max(2, Math.round(gap * .18f))) return true;
        }
        return false;
    }

    /** Longer gaps are permitted only where the entire missing path crosses one verified dark rail. */
    private static boolean bridgeRule(
            int id,
            List<Integer> earlier,
            List<Run> runs,
            int[] parents,
            byte[] gray,
            byte[] labels,
            int width,
            StaffPitchTrack track) {
        Run b = runs.get(id);
        for (int other : earlier) {
            Run a = runs.get(other);
            int distance = b.x() - a.x();
            if (distance < 3
                    || distance > 6
                    || Math.abs(b.rectified() - a.rectified()) > .25f * distance) continue;
            for (int rule = 0; rule < 5; rule++) {
                if ((a.rectified() + rule) * (b.rectified() + rule) >= 0) continue;
                boolean supported = true;
                for (int step = 1; step < distance && supported; step++) {
                    int x = a.x() + step;
                    float[] staff = track.at(x);
                    float relative =
                            a.rectified() + (b.rectified() - a.rectified()) * step / distance;
                    float y = staff[0] + relative * staff[1];
                    if (Math.abs(y - (staff[0] - rule * staff[1])) > Math.max(2, staff[1] * .3f)) {
                        supported = false;
                        break;
                    }
                    boolean ink = false;
                    for (int dy = -1; dy <= 1; dy++) {
                        int row = Math.round(y) + dy, at = row * width + x;
                        if (row < 0 || at >= gray.length) continue;
                        if (labels != null
                                && (labels[at] == OmrMeasurePostProcessor.NOTEHEAD
                                        || labels[at] == OmrMeasurePostProcessor.CLEF_OR_KEY))
                            continue;
                        if ((gray[at] & 255) <= 205) ink = true;
                    }
                    supported = ink;
                }
                if (supported) {
                    parents[root(id, parents)] = root(other, parents);
                    return true;
                }
            }
        }
        return false;
    }

    private static int root(int id, int[] parents) {
        while (parents[id] != id) {
            parents[id] = parents[parents[id]];
            id = parents[id];
        }
        return id;
    }

    private static Bow reject(List<Run> component, String reason, Consumer<Rejection> audit) {
        if (audit == null || component.isEmpty()) return null;
        int left = component.get(0).x(), right = component.get(component.size() - 1).x();
        if (right - left < component.get(0).gap() * 1.6f) return null;
        float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (var run : component) {
            top = Math.min(top, run.y());
            bottom = Math.max(bottom, run.y());
        }
        audit.accept(new Rejection(left, top, right, bottom, reason));
        return null;
    }

    /** Reuse the released returning-slur grammar for independently complete raw column profiles.
     * The caller owns every sample and independently verifies finite strip width/endpoints. */
    static boolean completeReturningProfile(int[] low, int[] high, int first, int last, float gap) {
        if (low == null
                || high == null
                || low.length != high.length
                || first < 0
                || last >= low.length
                || last - first < 3
                || last - first >= 1024
                || !Float.isFinite(gap)
                || gap < 5
                || gap > 64) return false;
        var runs = new ArrayList<Run>();
        for (int x = first; x <= last; x++) {
            if (low[x] < 0 || high[x] < low[x]) return false;
            float middle = (low[x] + high[x]) * .5f;
            runs.add(
                    new Run(
                            x,
                            middle,
                            middle / gap,
                            gap,
                            low[x],
                            high[x],
                            List.of(new RawBand(low[x], high[x]))));
        }
        Bow bow = fit(runs, null, null);
        return bow != null && bow.above();
    }

    private static Bow fit(
            List<Run> component,
            Consumer<Rejection> audit,
            IdentityHashMap<Bow, List<Run>> fittedSelections) {
        if (component.size() < 12) return null;
        var columns = new LinkedHashMap<Integer, List<Run>>();
        float gap = 0;
        for (var run : component) {
            columns.computeIfAbsent(run.x(), ignored -> new ArrayList<>()).add(run);
            gap += run.gap();
        }
        gap /= component.size();
        int left = component.get(0).x(), right = component.get(component.size() - 1).x();
        int span = right - left;
        if (span < gap * 1.6f || columns.size() < (span + 1) * .8f)
            return reject(component, "short-or-gapped", audit);
        // Closed glyphs and joined branches carry two separate contours in the same columns.
        long branched = columns.values().stream().filter(column -> column.size() > 1).count();
        if (branched > Math.max(1, columns.size() * .035f)) {
            int first = Integer.MAX_VALUE, last = -1;
            boolean central = false;
            for (var entry : columns.entrySet())
                if (entry.getValue().size() > 1) {
                    first = Math.min(first, entry.getKey());
                    last = Math.max(last, entry.getKey());
                    if (entry.getKey() > left + span * .2f && entry.getKey() < right - span * .2f)
                        central = true;
                }
            // Small terminal attachments can be excluded while the same visible-coverage
            // requirement still proves the bow. A competing central contour remains ambiguous.
            if (central || columns.size() - branched < (span + 1) * .8f)
                return reject(
                        component,
                        "branched:" + branched + "/" + columns.size() + ":" + first + ".." + last,
                        audit);
        }
        var chosen = new ArrayList<Run>();
        for (var column : columns.values()) {
            if (column.size() != 1) continue;
            chosen.add(column.get(0));
        }
        double[][] quadratic = new double[3][4],
                elliptical = new double[3][4],
                hinge = new double[3][4];
        for (var run : chosen) {
            double t = 2.0 * (run.x() - left) / span - 1;
            accumulate(quadratic, new double[] {1, t, t * t}, run.rectified());
            accumulate(
                    elliptical,
                    new double[] {1, t, Math.sqrt(Math.max(0, 1 - t * t))},
                    run.rectified());
            accumulate(hinge, new double[] {1, t, Math.abs(t)}, run.rectified());
        }
        double[] q = solve(quadratic), e = solve(elliptical), h = solve(hinge);
        if (q == null || e == null || h == null) return reject(component, "singular-fit", audit);
        boolean ellipse =
                squaredError(chosen, left, span, e, true)
                        < squaredError(chosen, left, span, q, false);
        double[] shape = ellipse ? e : q;
        double bend = ellipse ? -shape[2] : shape[2];
        if (Math.abs(bend) < .24 || Math.abs(bend) > 12)
            return reject(component, "not-bowed", audit);
        double squared = 0, hinged = 0;
        var errors = new ArrayList<Double>();
        for (var run : chosen) {
            double t = 2.0 * (run.x() - left) / span - 1;
            double error = run.rectified() - value(shape, t, ellipse);
            double other = run.rectified() - (h[0] + h[1] * t + h[2] * Math.abs(t));
            squared += error * error;
            hinged += other * other;
            errors.add(Math.abs(error));
        }
        double rms = Math.sqrt(squared / chosen.size());
        errors.sort(Double::compare);
        if (rms > .15
                || errors.get((int) ((errors.size() - 1) * .95)) > .27
                || hinged < squared * .45) return reject(component, "fit-or-sharp-hinge", audit);
        // Require the returning curvature over both outer quarters, not just a hooked tail.
        double leftSlope = localSlope(chosen, left, span, true);
        double rightSlope = localSlope(chosen, left, span, false);
        if (!Double.isFinite(leftSlope + rightSlope)
                || Math.signum(rightSlope - leftSlope) != Math.signum(bend)
                || Math.abs(rightSlope - leftSlope) < Math.abs(bend) * 1.6)
            return reject(component, "not-returning", audit);
        var points = chosen.stream().map(run -> new Point(run.x(), run.y())).toList();
        var bow = new Bow(points, bend > 0, (float) Math.abs(bend), (float) rms);
        if (fittedSelections != null) fittedSelections.put(bow, List.copyOf(chosen));
        return bow;
    }

    private static double value(double[] shape, double t, boolean ellipse) {
        return shape[0]
                + shape[1] * t
                + shape[2] * (ellipse ? Math.sqrt(Math.max(0, 1 - t * t)) : t * t);
    }

    private static double squaredError(
            List<Run> runs, int left, int span, double[] shape, boolean ellipse) {
        double result = 0;
        for (var run : runs) {
            double t = 2.0 * (run.x() - left) / span - 1;
            double error = run.rectified() - value(shape, t, ellipse);
            result += error * error;
        }
        return result;
    }

    private static double localSlope(List<Run> runs, int left, int span, boolean first) {
        double n = 0, x = 0, y = 0, xx = 0, xy = 0;
        for (var run : runs) {
            double t = 2.0 * (run.x() - left) / span - 1;
            if (first ? t > -.4 : t < .4) continue;
            n++;
            x += t;
            y += run.rectified();
            xx += t * t;
            xy += t * run.rectified();
        }
        double denominator = n * xx - x * x;
        return n < 3 || denominator <= 1e-8 ? Double.NaN : (n * xy - x * y) / denominator;
    }

    private static void accumulate(double[][] matrix, double[] basis, double value) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) matrix[row][col] += basis[row] * basis[col];
            matrix[row][3] += basis[row] * value;
        }
    }

    private static double[] solve(double[][] matrix) {
        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int row = col + 1; row < 3; row++)
                if (Math.abs(matrix[row][col]) > Math.abs(matrix[pivot][col])) pivot = row;
            if (Math.abs(matrix[pivot][col]) < 1e-8) return null;
            double[] swap = matrix[col];
            matrix[col] = matrix[pivot];
            matrix[pivot] = swap;
            double divisor = matrix[col][col];
            for (int k = col; k < 4; k++) matrix[col][k] /= divisor;
            for (int row = 0; row < 3; row++)
                if (row != col) {
                    double factor = matrix[row][col];
                    for (int k = col; k < 4; k++) matrix[row][k] -= factor * matrix[col][k];
                }
        }
        return new double[] {matrix[0][3], matrix[1][3], matrix[2][3]};
    }
}
