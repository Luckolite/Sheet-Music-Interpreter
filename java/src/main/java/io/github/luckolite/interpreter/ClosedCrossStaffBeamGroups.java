// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Optional closed raw beam ownership across a physically braced keyboard part. */
final class ClosedCrossStaffBeamGroups {
    record Group(
            List<Integer> owners,
            List<PairedTremoloInk.Stem> stems,
            GrandStaffDynamics.Pair pair,
            int measure,
            float gap,
            ScoreBeamSource.Group source) {
        Group {
            owners = List.copyOf(owners);
            stems = List.copyOf(stems);
        }
    }

    static final class Result {
        private final byte[] gray, labels;
        private final int width, height;
        private final List<ScoreNoteEvent> notes;
        private final List<MeasureRegion> measures;
        private final ScoreBeamSource source;
        private final List<Group> groups;
        private final boolean available;

        Result(
                byte[] gray,
                byte[] labels,
                int width,
                int height,
                List<ScoreNoteEvent> notes,
                List<MeasureRegion> measures,
                ScoreBeamSource source,
                List<Group> groups,
                boolean available) {
            this.gray = gray;
            this.labels = labels;
            this.width = width;
            this.height = height;
            this.notes = List.copyOf(notes);
            this.measures = List.copyOf(measures);
            this.source = source;
            this.groups = List.copyOf(groups);
            this.available = available;
        }

        ScoreBeamSource source() {
            return source;
        }

        List<Group> groups() {
            return groups;
        }

        boolean available() {
            return available;
        }

        boolean matches(
                byte[] g, byte[] l, int w, int h, List<MeasureRegion> m, List<ScoreNoteEvent> n) {
            if (!available
                    || g != gray
                    || l != labels
                    || w != width
                    || h != height
                    || !measures.equals(m)
                    || n.size() != notes.size()) return false;
            for (int i = 0; i < n.size(); i++) if (n.get(i) != notes.get(i)) return false;
            return true;
        }

        ScorePageInterpretation project(ScorePageInterpretation score) {
            if (groups.isEmpty()
                    || !matches(gray, labels, width, height, score.measures(), score.notes()))
                return score;
            var edited = new ArrayList<>(score.notes());
            for (var group : groups)
                for (int owner : group.owners()) {
                    if (group.source().bridges().get(0) == 1) continue;
                    var n = edited.get(owner);
                    int beams = n.beamCount();
                    float base = n.unbeamedDurationBeats();
                    if (beams == 0 && base == ScoreNoteEvent.DURATION_QUARTER
                            || beams == 1 && base == 0) {
                        beams = 2;
                        base = 0;
                    }
                    edited.set(
                            owner,
                            new ScoreNoteEvent(
                                    n.measureIndex(),
                                    n.positionInMeasure(),
                                    n.staffStep(),
                                    n.staffIndex(),
                                    n.staffCount(),
                                    n.pageY(),
                                    n.tiedFromPrevious(),
                                    n.augmentationDots(),
                                    beams,
                                    n.writtenAccidental(),
                                    base,
                                    n.tupletDivisor(),
                                    n.followingRestBeats(),
                                    n.articulations(),
                                    n.clefBottomDiatonic(),
                                    true,
                                    n.leadingRestBeats(),
                                    n.compactOpening(),
                                    n.octaveShift(),
                                    n.boundaryTies(),
                                    n.tupletNormalNotes(),
                                    n.stemDirection(),
                                    n.kind(),
                                    n.visualSize()));
                }
            if (edited.equals(score.notes()))
                return score.beamSource().equals(source) ? score : score.withBeamSource(source);
            var identity = new ArrayList<Integer>();
            for (int i = 0; i < edited.size(); i++) identity.add(i);
            return score.withNotes(edited, identity).withBeamSource(source);
        }
    }

    private record Body(
            int left,
            int top,
            int right,
            int bottom,
            int count,
            float x,
            float y,
            Set<Integer> pixels) {}

    private record Node(
            int owner,
            Body body,
            PrintedBeamShafts.Shaft shaft,
            PlayingTechniqueDetector.Staff staff,
            float gap) {}

    private static final class Work {
        long left = 64_000_000;

        void take(long n) {
            if (n < 0 || n > left) throw new Refusal();
            left -= n;
        }
    }

    private static final class Refusal extends RuntimeException {}

    private ClosedCrossStaffBeamGroups() {}

    static Result capture(
            List<? extends PrintedSlurOwners.Location> heads,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            ScoreBeamSource liveBeam) {
        if (gray == null
                || labels == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || labels.length != gray.length
                || heads.size() != notes.size()
                || liveBeam.noteOrigins().size() != notes.size())
            return new Result(
                    gray, labels, width, height, notes, measures, liveBeam, List.of(), false);
        var work = new Work();
        var seedBudget = new PrintedBeamShafts.Budget(8_000_000);
        var bodies = new HashMap<Integer, Body>();
        var done = new HashSet<Integer>();
        var groups = new ArrayList<Group>();
        try {
            work.take(braceCost(staffs, measures, width, height));
            var pairs = GrandStaffDynamics.bracedPairs(staffs, measures, gray, width, height);
            for (var seed : liveBeam.groups()) {
                work.take(8L * notes.size() + 128L * seed.columns().size() + 64);
                var ids = new ArrayList<Integer>();
                int page = -1;
                boolean valid = true;
                for (var column : seed.columns()) {
                    if (column.members().size() != 1) {
                        valid = false;
                        break;
                    }
                    var anchor = column.members().get(0);
                    if (page < 0) page = anchor.pageIndex();
                    if (anchor.pageIndex() != page || anchor.noteIndex() >= notes.size()) {
                        valid = false;
                        break;
                    }
                    ids.add(anchor.noteIndex());
                }
                if (!valid
                        || ids.size() < 2
                        || ids.size() > 32
                        || ids.stream().anyMatch(done::contains)) continue;
                int first = ids.get(0),
                        last = ids.get(ids.size() - 1),
                        measure = notes.get(first).measureIndex();
                if (measure < 0 || measure >= measures.size()) continue;
                if (ids.stream()
                        .anyMatch(
                                i ->
                                        notes.get(i).measureIndex() != measure
                                                || notes.get(i).staffIndex() != 1
                                                || notes.get(i).staffCount() != 2)) continue;
                var region = measures.get(measure);
                GrandStaffDynamics.Pair pair = null;
                for (var p : pairs)
                    if (p.upper().index() == 0
                            && p.lower().index() == 1
                            && p.upper().count() == 2
                            && p.lower().count() == 2
                            && region.top() * height <= (p.upper().top() + p.upper().bottom()) * .5f
                            && region.bottom() * height
                                    >= (p.lower().top() + p.lower().bottom()) * .5f) {
                        if (pair != null) {
                            pair = null;
                            break;
                        }
                        pair = p;
                    }
                if (pair == null) continue;
                float gap = pair.lower().gap();
                var a = seed(gray, width, height, heads.get(first), gap, seedBudget);
                var b = seed(gray, width, height, heads.get(last), gap, seedBudget);
                boolean singleSeed = seed.bridges().stream().allMatch(mask -> mask == 1)
                        && seed.hooks().isEmpty()
                        && ids.stream().allMatch(owner -> notes.get(owner).beamCount() == 1);
                if (b == null && singleSeed) {
                    float lastX = heads.get(last).x();
                    int next = -1;
                    for (int i = 0; i < notes.size(); i++) {
                        var n = notes.get(i);
                        float x = heads.get(i).x();
                        if (n.measureIndex() != measure || n.staffIndex() != 0
                                || n.staffCount() != 2 || n.beamCount() != 1
                                || n.stemDirection() != -1 || x <= lastX || x - lastX > gap * 6)
                            continue;
                        if (next < 0 || x < heads.get(next).x()) next = i;
                    }
                    if (next >= 0) b = seed(gray, width, height, heads.get(next), gap, seedBudget);
                }
                if (seedBudget.refused()) throw new Refusal();
                if (a == null || b == null) continue;
                float ax = (a.left() + a.right()) * .5f, bx = (b.left() + b.right()) * .5f;
                if (bx <= ax) continue;
                float slope = (b.endY() - a.endY()) / (bx - ax);
                if (Math.abs(slope) > .35f) continue;
                float systemTop = 0, systemBottom = height - 1;
                for (var s : staffs) {
                    if (s.bottom() < pair.upper().top())
                        systemTop = Math.max(systemTop, (s.bottom() + pair.upper().top()) * .5f);
                    if (s.top() > pair.lower().bottom())
                        systemBottom =
                                Math.min(systemBottom, (s.top() + pair.lower().bottom()) * .5f);
                }
                var nodes = new ArrayList<Node>();
                boolean contradictory = false;
                for (int i = 0; i < notes.size(); i++) {
                    var n = notes.get(i);
                    var head = heads.get(i);
                    if (n.measureIndex() != measure
                            || head.owner() != i
                            || n.staffCount() != 2
                            || head.staffCount() != 2
                            || head.staffIndex() != n.staffIndex()
                            || n.kind() != ScoreNoteEvent.Kind.PITCHED
                            || n.visualSize() != ScoreNoteEvent.VisualSize.FULL
                            || head.grace()) continue;
                    var staff =
                            n.staffIndex() == 0
                                    ? pair.upper()
                                    : n.staffIndex() == 1 ? pair.lower() : null;
                    if (staff == null || head.y() < systemTop || head.y() > systemBottom) continue;
                    if (singleSeed && n.unbeamedDurationBeats() == ScoreNoteEvent.DURATION_WHOLE
                            && coincidentVoice(notes, i, false)) continue;
                    Body body = bodies.get(i);
                    if (body == null) {
                        body = body(labels, width, height, head, work);
                        if (body != null) bodies.put(i, body);
                    }
                    if (body == null) continue;
                    var shaft =
                            anchored(
                                    gray,
                                    width,
                                    height,
                                    head,
                                    staff.gap(),
                                    ax,
                                    a.endY(),
                                    slope,
                                    systemTop,
                                    systemBottom,
                                    singleSeed ? pair : null,
                                    work);
                    boolean mergedUnison = false;
                    if (shaft == null && singleSeed && n.beamCount() == 1
                            && n.stemDirection() == -1 && coincidentVoice(notes, i, true)
                            && body.right() - body.left() + 1 > staff.gap() * 1.8f) {
                        var proved = mergedUnisonShaft(gray, width, height, head, body, pair,
                                staff.gap(), ax, a.endY(), slope, systemTop, systemBottom, work);
                        if (proved != null) {
                            head = proved.head();
                            shaft = proved.shaft();
                            mergedUnison = true;
                        }
                    }
                    if (shaft == null) continue;
                    // This additive repair supports only the existing quarter/eighth/16th
                    // value tuples. Contradictory retained values never acquire a clock flag.
                    if (!(n.beamCount() == 0
                                    && n.unbeamedDurationBeats() == ScoreNoteEvent.DURATION_QUARTER
                            || (n.beamCount() == 1 || n.beamCount() == 2)
                                    && n.unbeamedDurationBeats() == 0)) {
                        contradictory = true;
                        break;
                    }
                    boolean attach = false;
                    for (var col : shaft.columns())
                        for (int y = Math.max(0, Math.round(head.y() - .2f * staff.gap()));
                                y <= Math.min(height - 1, Math.round(head.y() + .2f * staff.gap()));
                                y++)
                            if (body.pixels().contains(y * width + col.x())
                                    && (gray[y * width + col.x()] & 255) < 165) attach = true;
                    work.take(
                            20L
                                    * (body.right() - body.left() + 1)
                                    * (body.bottom() - body.top() + 1));
                    if (!attach
                            || !mergedUnison && !OmrScoreInterpreter.printedFilledComponent(
                                    gray,
                                    width,
                                    height,
                                    body.count(),
                                    body.left(),
                                    body.top(),
                                    body.right(),
                                    body.bottom(),
                                    body.x(),
                                    body.y(),
                                    staff.gap())) {
                        contradictory = true;
                        break;
                    }
                    if (opposed(gray, width, height, head, staff.gap(), work)) {
                        contradictory = true;
                        break;
                    }
                    nodes.add(new Node(i, body, shaft, staff, staff.gap()));
                }
                if (contradictory || nodes.size() < 3 || nodes.size() > 32) continue;
                nodes.sort(Comparator.comparingInt(n -> n.shaft().left()));
                // A measure may contain independent beam ensembles whose extrapolated
                // endpoint lines coincide. Select only a literal connected component
                // containing every live seed owner; the full closed proof still follows.
                var components = new ArrayList<ArrayList<Node>>();
                ArrayList<Node> component = null;
                Node previous = null;
                int inset = Math.max(1, Math.round(gap * .12f));
                for (var node : nodes) {
                    boolean connected = false;
                    if (previous != null) {
                        var pa = previous.shaft();
                        var pb = node.shaft();
                        int xa = pa.columns().get((pa.columns().size() - 1) / 2).x();
                        int xb = pb.columns().get((pb.columns().size() - 1) / 2).x();
                        int ya = pa.endY() - inset, yb = pb.endY() - inset;
                        int radius = Math.max(2, Math.round(gap * .22f));
                        long scanWidth = Math.max(0L, (long) xb - xa - 3);
                        long scanHeight =
                                Math.max(
                                        0L,
                                        (long) Math.min(height - 1, Math.max(ya, yb) + radius)
                                                - Math.max(0, Math.min(ya, yb) - radius)
                                                + 1);
                        work.take(64 + 12 * scanWidth * scanHeight);
                        connected =
                                BeamInkConnectivity.connected(
                                        gray, width, height, xa, ya, xb, yb, gap);
                    }
                    if (!connected) {
                        component = new ArrayList<>();
                        components.add(component);
                    }
                    component.add(node);
                    previous = node;
                }
                ArrayList<Node> selected = null;
                for (var part : components) {
                    var members = new HashSet<Integer>();
                    for (var node : part) members.add(node.owner());
                    if (!members.containsAll(ids)) continue;
                    if (selected != null) {
                        selected = null;
                        break;
                    }
                    selected = part;
                }
                if (selected == null) continue;
                nodes = selected;
                var owned = new HashSet<Integer>();
                for (var n : nodes) owned.add(n.owner());
                if (!owned.containsAll(ids)
                        || nodes.stream().noneMatch(n -> n.staff().index() == 0)) continue;
                var stems = new ArrayList<PairedTremoloInk.Stem>();
                var columns = new ArrayList<PrintedBeamGeometry.Column>();
                boolean collision = false;
                int prior = -1;
                for (var n : nodes) {
                    var shaft = n.shaft();
                    if (shaft.left() <= prior) {
                        collision = true;
                        break;
                    }
                    prior = shaft.right();
                    int x = shaft.columns().get((shaft.columns().size() - 1) / 2).x();
                    stems.add(
                            new PairedTremoloInk.Stem(
                                    x, Math.round(heads.get(n.owner()).y()), shaft.endY()));
                    var ends = new ArrayList<PrintedBeamGeometry.End>();
                    for (var c : shaft.columns())
                        ends.add(new PrintedBeamGeometry.End(c.x(), c.endY()));
                    // This local carrier namespace is ONE independently braced instrument;
                    // actual physical staff/page identity remains in Node and output anchors.
                    columns.add(
                            new PrintedBeamGeometry.Column(
                                    n.owner(),
                                    measure,
                                    0,
                                    1,
                                    1,
                                    shaft.left(),
                                    shaft.right(),
                                    shaft.endY(),
                                    n.gap(),
                                    8,
                                    ends,
                                    shaft.startY()));
                }
                if (collision) continue;
                long closure = closureCost(stems, gap);
                work.take(closure);
                if (!CompleteBeamGroupInk.provesShafts(gray, width, height, stems, gap, -1))
                    continue;
                var ownerOrder = nodes.stream().map(Node::owner).toList();
                List<Integer> bridges;
                if (singleSeed && literalSingleRail(ownerOrder, liveBeam, notes, work)) {
                    // The closed raw shaft proof above establishes one continuous strip.
                    // A retained literal seed and the single-beam values corroborate the strip.
                    bridges = Collections.nCopies(ownerOrder.size() - 1, 1);
                } else {
                    final var actualPair = pair;
                    // Complete, centroid-bound filled head components are non-beam carriers.
                    // They cannot supply additional rails or hooks in the shaft search domain.
                    var headCarriers = new HashSet<Integer>();
                    for (var node : nodes) {
                        work.take(node.body().pixels().size());
                        headCarriers.addAll(node.body().pixels());
                    }
                    var geo =
                            PrintedBeamGeometry.detect(
                                    gray,
                                    width,
                                    height,
                                    columns,
                                    (ca, cb, x, y) ->
                                            rule(actualPair, x, y)
                                                    || headCarriers.contains(y * width + x),
                                    new PrintedBeamGeometry.Budget(Math.max(1, work.left / 16)));
                    work.take(geo.work() * 16);
                    if (geo.budgetExceeded()) throw new Refusal();
                    if (geo.groups().size() != 1 || !geo.unlinkedColumns().isEmpty()) continue;
                    var group = geo.groups().get(0);
                    if (!group.columns().equals(ownerOrder)
                            || group.bridges().stream().anyMatch(mask -> mask != 3)
                            || !group.hooks().isEmpty()) continue;
                    bridges = group.bridges();
                }
                boolean overlap = false;
                for (var old : liveBeam.groups()) {
                    var oldIds = new HashSet<Integer>();
                    for (var c : old.columns()) for (var m : c.members()) oldIds.add(m.noteIndex());
                    if (!Collections.disjoint(oldIds, owned) && !owned.containsAll(oldIds)) {
                        overlap = true;
                        break;
                    }
                }
                if (overlap) continue;
                var sourceColumns = new ArrayList<ScoreBeamSource.Column>();
                for (int id : ownerOrder) {
                    var origin = liveBeam.noteOrigins().get(id);
                    if (origin.isEmpty() || origin.get().pageIndex() != page) {
                        valid = false;
                        break;
                    }
                    sourceColumns.add(new ScoreBeamSource.Column(List.of(origin.get())));
                }
                if (!valid) continue;
                var sg =
                        new ScoreBeamSource.Group(
                                "closed-cross-staff:" + page + ":" + ownerOrder.get(0),
                                sourceColumns,
                                bridges,
                                List.of());
                groups.add(new Group(ownerOrder, stems, pair, measure, gap, sg));
                done.addAll(owned);
            }
            var merged = new ArrayList<ScoreBeamSource.Group>();
            for (var old : liveBeam.groups()) {
                boolean replaced = false;
                for (var c : old.columns())
                    for (var m : c.members()) if (done.contains(m.noteIndex())) replaced = true;
                if (!replaced) merged.add(old);
            }
            for (var group : groups) merged.add(group.source());
            var detached = new ArrayList<ScoreBeamSource.Column>();
            for (var old : liveBeam.detachedColumns()) {
                boolean intersects =
                        old.members().stream().anyMatch(m -> done.contains(m.noteIndex()));
                if (intersects
                        && !old.members().stream().allMatch(m -> done.contains(m.noteIndex())))
                    throw new Refusal();
                if (!intersects) detached.add(old);
            }
            return new Result(
                    gray,
                    labels,
                    width,
                    height,
                    notes,
                    measures,
                    new ScoreBeamSource(merged, detached, liveBeam.noteOrigins()),
                    groups,
                    true);
        } catch (Refusal refusal) {
            return new Result(
                    gray, labels, width, height, notes, measures, liveBeam, List.of(), false);
        }
    }

    private static boolean coincidentVoice(
            List<ScoreNoteEvent> notes, int owner, boolean seekWhole) {
        var note = notes.get(owner);
        for (int i = Math.max(0, owner - 6); i < Math.min(notes.size(), owner + 7); i++) {
            var other = notes.get(i);
            if (i == owner || other.measureIndex() != note.measureIndex()
                    || other.staffIndex() != note.staffIndex()
                    || other.staffCount() != note.staffCount() || other.kind() != note.kind()
                    || other.positionInMeasure() != note.positionInMeasure()
                    || other.diatonicPitchIdentity() != note.diatonicPitchIdentity()) continue;
            if (seekWhole ? other.unbeamedDurationBeats() == ScoreNoteEvent.DURATION_WHOLE
                            && other.stemDirection() == 0
                    : other.beamCount() == 1 && other.stemDirection() == -1) return true;
        }
        return false;
    }

    private record UnisonShaft(
            PrintedSlurOwners.Location head, PrintedBeamShafts.Shaft shaft) {}

    /** A whole note and filled neighbor can be one model component at the same clock column. */
    private static UnisonShaft mergedUnisonShaft(
            byte[] gray, int width, int height, PrintedSlurOwners.Location head, Body union,
            GrandStaffDynamics.Pair pair, float gap, float ax, float ay, float slope,
            float systemTop, float systemBottom, Work work) {
        int from = Math.max(union.left(), Math.round(head.x() - gap * 1.35f));
        int to = Math.min(union.right(), Math.round(head.x() + gap * 1.35f));
        UnisonShaft found = null;
        for (int x = from; x <= to; x++) {
            var probe = new PrintedSlurOwners.GeometryHead(
                    head.owner(), x, head.y(), head.staffIndex(), head.staffCount());
            var shaft = anchored(gray, width, height, probe, gap, ax, ay, slope,
                    systemTop, systemBottom, pair, work);
            if (shaft == null) continue;
            float center = (shaft.left() + shaft.right()) * .5f + gap * .6f;
            if (Math.abs(center - head.x()) < gap * .35f
                    || Math.abs(center - head.x()) > gap * 1.35f) continue;
            int radiusX = Math.max(2, Math.round(gap * .22f));
            int radiusY = Math.max(2, Math.round(gap * .18f));
            int cx = Math.round(center), cy = Math.round(head.y());
            if (cx - radiusX < union.left() || cx + radiusX > union.right()
                    || cy - radiusY < union.top() || cy + radiusY > union.bottom()) continue;
            int hits = 0, cells = (2 * radiusX + 1) * (2 * radiusY + 1);
            work.take(cells);
            for (int dy = -radiusY; dy <= radiusY; dy++)
                for (int dx = -radiusX; dx <= radiusX; dx++)
                    if ((gray[(cy + dy) * width + cx + dx] & 255) < 165) hits++;
            if (hits < cells * .9f) continue;
            var printed = new PrintedSlurOwners.GeometryHead(
                    head.owner(), center, head.y(), head.staffIndex(), head.staffCount());
            if (found != null && (shaft.left() > found.shaft().right()
                    || found.shaft().left() > shaft.right())) return null;
            if (found == null || shaft.columns().size() > found.shaft().columns().size())
                found = new UnisonShaft(printed, shaft);
        }
        return found;
    }

    private static boolean literalSingleRail(
            List<Integer> owners, ScoreBeamSource source, List<ScoreNoteEvent> notes, Work work) {
        var remaining = new HashSet<>(owners);
        for (var group : source.groups()) {
            work.take(16L + 8L * group.columns().size());
            var members = group.columns().stream().flatMap(c -> c.members().stream())
                    .map(ScoreBeamSource.Anchor::noteIndex).toList();
            if (Collections.disjoint(members, owners)) continue;
            if (!remaining.containsAll(members) || !group.hooks().isEmpty()
                    || group.bridges().stream().anyMatch(mask -> mask != 1)) return false;
            remaining.removeAll(members);
        }
        return remaining.size() < owners.size() && owners.stream().allMatch(i -> notes.get(i).beamCount() == 1);
    }
    private static boolean rule(GrandStaffDynamics.Pair pair, int x, int y) {
        for (var s : List.of(pair.upper(), pair.lower()))
            for (int line = 0; line < 5; line++)
                if (Math.abs(y - Math.round(s.bottom() - line * s.gap()))
                        <= Math.max(1, Math.round(s.gap() * .2f))) return true;
        return false;
    }

    private static PrintedBeamShafts.Shaft seed(
            byte[] g,
            int w,
            int h,
            PrintedSlurOwners.Location n,
            float gap,
            PrintedBeamShafts.Budget budget) {
        var candidates = PrintedBeamShafts.at(g, w, h, n.x(), n.y(), gap, budget);
        return candidates.size() == 1 && candidates.get(0).direction() == 1
                ? candidates.get(0)
                : null;
    }

    private static Body body(byte[] labels, int w, int h, PrintedSlurOwners.Location n, Work work) {
        int seed = -1;
        for (int y : new int[] {(int) Math.floor(n.y()), (int) Math.ceil(n.y())})
            for (int x : new int[] {(int) Math.floor(n.x()), (int) Math.ceil(n.x())})
                if (x >= 0 && x < w && y >= 0 && y < h && labels[y * w + x] == 2) {
                    seed = y * w + x;
                    break;
                }
        if (seed < 0) return null;
        var pixels = new HashSet<Integer>();
        var queue = new ArrayDeque<Integer>();
        pixels.add(seed);
        queue.add(seed);
        int l = w, r = -1, t = h, b = -1;
        long sx = 0, sy = 0;
        while (!queue.isEmpty()) {
            work.take(10);
            int p = queue.remove(), x = p % w, y = p / w;
            l = Math.min(l, x);
            r = Math.max(r, x);
            t = Math.min(t, y);
            b = Math.max(b, y);
            sx += x;
            sy += y;
            if (pixels.size() > 4096) return null;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                    int q = yy * w + xx;
                    if (labels[q] == 2 && pixels.add(q)) queue.add(q);
                }
        }
        for (int y : new int[] {(int) Math.floor(n.y()), (int) Math.ceil(n.y())})
            for (int x : new int[] {(int) Math.floor(n.x()), (int) Math.ceil(n.x())})
                if (x >= 0
                        && x < w
                        && y >= 0
                        && y < h
                        && labels[y * w + x] == 2
                        && !pixels.contains(y * w + x)) return null;
        float cx = (float) (sx / (double) pixels.size()),
                cy = (float) (sy / (double) pixels.size());
        if (Math.round(cx) != Math.round(n.x()) || Math.round(cy) != Math.round(n.y())) return null;
        return new Body(l, t, r, b, pixels.size(), cx, cy, pixels);
    }

    private static PrintedBeamShafts.Shaft anchored(
            byte[] g,
            int w,
            int h,
            PrintedSlurOwners.Location n,
            float gap,
            float ax,
            float ay,
            float slope,
            float systemTop,
            float systemBottom,
            GrandStaffDynamics.Pair endingRule,
            Work work) {
        int edge = Math.round(n.x() - .6f * gap),
                first = Math.max(1, Math.round(edge - gap * .2f)),
                last = Math.min(w - 2, (int) Math.floor(edge + gap * .2f));
        int start = Math.round(n.y()) + Math.round(gap * .2f);
        var columns = new ArrayList<PrintedBeamShafts.Column>();
        int prior = -1, end = -1;
        for (int x = first; x <= last; x++) {
            float predicted = ay + (x - ax) * slope;
            if (predicted < systemTop || predicted > systemBottom || predicted - start < gap * 1.7f)
                continue;
            int limit =
                    Math.min(
                            (int) Math.floor(systemBottom),
                            Math.min(h - 2, (int) Math.ceil(predicted + gap * .2f) + 3));
            work.take(Math.max(0L, (long) limit - start + 1));
            int misses = 0, hits = 0, total = 0, cap = start;
            for (int y = start; y < limit; y++) {
                if ((g[y * w + x] & 255) < 165) {
                    if (endingRule != null && misses > 0 && y > predicted
                            && rule(endingRule, x, y)) break;
                    hits++;
                    misses = 0;
                    cap = y;
                } else if (++misses >= 2) break;
                total++;
            }
            if (cap - Math.round(n.y()) < gap * 1.7f
                    || hits < total * .9f
                    || cap >= limit - 1
                    || Math.abs(cap - predicted) > gap * .2f) continue;
            if (prior >= 0 && x != prior + 1) return null;
            prior = x;
            columns.add(new PrintedBeamShafts.Column(x, cap));
            end = Math.max(end, cap);
        }
        if (columns.isEmpty() || columns.size() > Math.max(3, gap * .35f)) return null;
        return new PrintedBeamShafts.Shaft(
                columns.get(0).x(), columns.get(columns.size() - 1).x(), start, end, 1, columns);
    }

    private static boolean opposed(
            byte[] g, int w, int h, PrintedSlurOwners.Location n, float gap, Work work) {
        int edge = Math.round(n.x() + gap * .6f);
        for (int x = Math.max(1, Math.round(edge - gap * .2f));
                x <= Math.min(w - 2, Math.floor(edge + gap * .2f));
                x++) {
            int hits = 0, total = 0, miss = 0;
            work.take((long) Math.ceil(gap * 2) + 1);
            for (int d = Math.round(gap * .2f); d <= gap * 2; d++) {
                int y = Math.round(n.y()) - d;
                if (y < 0) break;
                total++;
                if ((g[y * w + x] & 255) < 165) {
                    hits++;
                    miss = 0;
                } else if (++miss >= 2) break;
            }
            if (total >= gap * 1.7f && hits >= total * .9f) return true;
        }
        return false;
    }

    private static long braceCost(
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            int width,
            int height) {
        long total = 0;
        for (int i = 0; i + 1 < staffs.size(); i++) {
            var a = staffs.get(i);
            var b = staffs.get(i + 1);
            if (a.count() != b.count() || b.index() != a.index() + 1) continue;
            float left = 1;
            for (var m : measures)
                if (m.top() * height <= a.bottom() && m.bottom() * height >= b.top())
                    left = Math.min(left, m.left());
            if (left == 1) continue;
            float gap = (a.gap() + b.gap()) * .5f;
            if (!Float.isFinite(gap + a.top() + b.bottom()) || gap < 3 || gap > 64)
                throw new Refusal();
            long w = Math.round(left * width) - Math.max(0, Math.round(left * width - 14 * gap));
            long h =
                    Math.min(height, Math.round(b.bottom() + gap))
                            - Math.max(0, Math.round(a.top() - gap));
            if (w < 0 || h < 0 || w > width || h > height) throw new Refusal();
            total += 12 * w * h + 64;
            if (total > 64_000_000) throw new Refusal();
        }
        return total;
    }

    private static long closureCost(List<PairedTremoloInk.Stem> stems, float gap) {
        long span = (long) stems.get(stems.size() - 1).x() - stems.get(0).x();
        if (span < 1) return 0;
        long cost = span * ((long) Math.ceil(3 * gap) + 2) * stems.size() + span * 8 + 128;
        int radius = Math.max(2, Math.round(gap * .22f));
        for (int i = 1; i < stems.size(); i++) {
            var a = stems.get(i - 1);
            var b = stems.get(i);
            cost +=
                    12L
                            * Math.max(0, b.x() - a.x() - 3)
                            * (Math.abs((long) b.endY() - a.endY()) + 2L * radius + 1);
        }
        return cost;
    }
}
