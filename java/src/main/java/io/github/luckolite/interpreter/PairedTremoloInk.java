// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Private candidate: three floating strokes between two independently proved stems. */
final class PairedTremoloInk {
    // Diagnostic trace call sites are retained as no-ops for stable control flow.
    private static void trace(String message) {
        FringeProfileDiagnostics.record("PAIRED\t" + message);
    }

    private PairedTremoloInk() {}

    record Stem(int x, int headY, int endY) {}

    record Head(
            int id,
            int measure,
            int staff,
            double x,
            double y,
            double gap,
            int direction,
            List<Integer> footprint,
            boolean removalEligible,
            double staffTop,
            ExactComponentMembers.AcceptedProbeHead memberToken,
            int shaftX,
            int shaftHeadY,
            int shaftEndY) {
        Head {
            footprint = List.copyOf(footprint);
        }

        Head(
                int id,
                int measure,
                int staff,
                double x,
                double y,
                double gap,
                int direction,
                List<Integer> footprint) {
            this(
                    id,
                    measure,
                    staff,
                    x,
                    y,
                    gap,
                    direction,
                    footprint,
                    true,
                    Double.NaN,
                    null,
                    -1,
                    -1,
                    -1);
        }

        Head(
                int id,
                int measure,
                int staff,
                double x,
                double y,
                double gap,
                int direction,
                List<Integer> footprint,
                boolean removalEligible) {
            this(
                    id,
                    measure,
                    staff,
                    x,
                    y,
                    gap,
                    direction,
                    footprint,
                    removalEligible,
                    Double.NaN,
                    null,
                    -1,
                    -1,
                    -1);
        }

        Head(int id, int measure, int staff, double x, double y, double gap, int direction) {
            this(
                    id,
                    measure,
                    staff,
                    x,
                    y,
                    gap,
                    direction,
                    List.of(),
                    false,
                    Double.NaN,
                    null,
                    -1,
                    -1,
                    -1);
        }

        Head(
                int id,
                int measure,
                int staff,
                double x,
                double y,
                double gap,
                int direction,
                List<Integer> footprint,
                boolean removalEligible,
                double staffTop,
                ExactComponentMembers.AcceptedProbeHead memberToken,
                int[] shaft) {
            this(
                    id,
                    measure,
                    staff,
                    x,
                    y,
                    gap,
                    direction,
                    footprint,
                    removalEligible,
                    staffTop,
                    memberToken,
                    shaft == null ? -1 : shaft[0],
                    shaft == null ? -1 : shaft[1],
                    shaft == null ? -1 : shaft[2]);
        }
    }

    record Pair(
            List<Integer> firstIds,
            List<Integer> secondIds,
            List<Integer> railHeadIds,
            List<Mark> marks,
            int strokeCount,
            boolean unresolvedPhysicalSource) {
        Pair {
            firstIds = List.copyOf(firstIds);
            secondIds = List.copyOf(secondIds);
            railHeadIds = List.copyOf(railHeadIds);
            marks = List.copyOf(marks);
            if (strokeCount < 1 || strokeCount > 8)
                throw new IllegalArgumentException("Invalid stroke count");
        }

        Pair(
                List<Integer> firstIds,
                List<Integer> secondIds,
                List<Integer> railHeadIds,
                List<Mark> marks) {
            this(firstIds, secondIds, railHeadIds, marks, 3, false);
        }

        Pair(int firstId, int secondId, List<Integer> railHeadIds, List<Mark> marks) {
            this(List.of(firstId), List.of(secondId), railHeadIds, marks, 3, false);
        }

        int firstId() {
            return firstIds.get(0);
        }

        int secondId() {
            return secondIds.get(0);
        }
    }

    record Band(double center, int thickness) {}

    static final class Mark {
        private final int left, right, bodyLeft, bodyRight;
        private final double slope;
        private final List<Band> bands;
        private final int[] rawBodyPixels;
        private final List<int[]> rawBodyGroups;
        private final boolean exactBody;
        private final FringeBinding fringe;
        private final ObservedFiniteRasterRoles.Evidence finiteRoles;
        private final java.util.IdentityHashMap<ObservedBarRasterFringe.BodyIdentity, Boolean>
                bodyIdentityMatches = new java.util.IdentityHashMap<>();
        private boolean bodyIdentityCacheReserved;

        Mark(int left, int right, double slope, List<Band> bands) {
            this(left, right, slope, bands, left, right, new int[0]);
        }

        Mark(int left, int right, double slope, List<Band> bands, int bodyLeft, int bodyRight) {
            this(left, right, slope, bands, bodyLeft, bodyRight, new int[0]);
        }

        Mark(
                int left,
                int right,
                double slope,
                List<Band> bands,
                int bodyLeft,
                int bodyRight,
                int[] rawBodyPixels) {
            this(left, right, slope, bands, bodyLeft, bodyRight, rawBodyPixels, List.of(), null);
        }

        Mark(
                int left,
                int right,
                double slope,
                List<Band> bands,
                int bodyLeft,
                int bodyRight,
                int[] rawBodyPixels,
                FringeBinding fringe) {
            this(left, right, slope, bands, bodyLeft, bodyRight, rawBodyPixels, List.of(), fringe);
        }

        Mark(
                int left,
                int right,
                double slope,
                List<Band> bands,
                int bodyLeft,
                int bodyRight,
                int[] rawBodyPixels,
                List<int[]> rawBodyGroups,
                FringeBinding fringe) {
            this(
                    left,
                    right,
                    slope,
                    bands,
                    bodyLeft,
                    bodyRight,
                    rawBodyPixels,
                    rawBodyGroups,
                    fringe,
                    null);
        }

        Mark(
                int left,
                int right,
                double slope,
                List<Band> bands,
                int bodyLeft,
                int bodyRight,
                int[] rawBodyPixels,
                List<int[]> rawBodyGroups,
                FringeBinding fringe,
                ObservedFiniteRasterRoles.Evidence finiteRoles) {
            this.finiteRoles = finiteRoles;
            if (finiteRoles != null && !finiteRoles.matchesGroups(rawBodyGroups))
                throw new IllegalArgumentException("Finite role body identity");
            this.left = left;
            this.right = right;
            this.slope = slope;
            this.bands = List.copyOf(bands);
            this.bodyLeft = bodyLeft;
            this.bodyRight = bodyRight;
            this.rawBodyPixels = rawBodyPixels.clone();
            this.rawBodyGroups = rawBodyGroups.stream().map(int[]::clone).toList();
            this.fringe = fringe;
            this.exactBody = rawBodyPixels.length > 0;
            if (exactBody)
                for (int i = 1; i < this.rawBodyPixels.length; i++)
                    if (this.rawBodyPixels[i - 1] >= this.rawBodyPixels[i])
                        throw new IllegalArgumentException(
                                "raw body membership must be sorted and unique");
        }

        int left() {
            return left;
        }

        int right() {
            return right;
        }

        double slope() {
            return slope;
        }

        List<Band> bands() {
            return bands;
        }

        int bodyLeft() {
            return bodyLeft;
        }

        int bodyRight() {
            return bodyRight;
        }

        int rawBodyPixelCount() {
            return rawBodyPixels.length;
        }

        boolean hasExactBody() {
            return exactBody;
        }

        boolean containsRawBodyPixel(int pixel) {
            return exactBody && java.util.Arrays.binarySearch(rawBodyPixels, pixel) >= 0;
        }

        boolean containsWholeRawBody(ObservedBarRasterFringe.BodyIdentity body) {
            if (body == null || !exactBody || body.size() == 0) return false;
            Boolean cached = bodyIdentityMatches.get(body);
            if (cached != null) return cached;
            boolean matched = false;
            for (int[] group : rawBodyGroups)
                if (group.length == body.size()) {
                    // The extractor walks columns while the profile copier may use a
                    // local snapshot origin. Compare authenticated members, not order.
                    boolean same = true;
                    for (int i = 0; i < group.length; i++)
                        if (java.util.Arrays.binarySearch(group, body.pixel(i)) < 0) {
                            same = false;
                            break;
                        }
                    if (same) {
                        matched = true;
                        break;
                    }
                }
            bodyIdentityMatches.put(body, matched);
            return matched;
        }

        private boolean prechargeBodyIdentityScan() {
            long work = fringe.bodies.size();
            try {
                for (var body : fringe.bodies)
                    if (!bodyIdentityMatches.containsKey(body)) {
                        work = Math.addExact(work, rawBodyGroups.size());
                        for (int[] group : rawBodyGroups)
                            if (group.length == body.size()) {
                                int log =
                                        32
                                                - Integer.numberOfLeadingZeros(
                                                        Math.max(1, group.length));
                                work =
                                        Math.addExact(
                                                work,
                                                Math.multiplyExact((long) body.size(), log + 1L));
                            }
                    }
                if (!bodyIdentityCacheReserved) {
                    fringe.budget.memory(
                            Math.addExact(64L, Math.multiplyExact(48L, fringe.bodies.size())));
                    bodyIdentityCacheReserved = true;
                }
                fringe.budget.work(work);
                return true;
            } catch (RuntimeException exhausted) {
                trace("fringeAssignmentRefuse=BODY_IDENTITY_BUDGET");
                return false;
            }
        }

        ObservedFiniteRasterRoles.ComponentProof proveWholeFiniteComponent(
                byte[] gray, int w, int h, Head head) {
            if (finiteRoles == null || fringe == null) return null;
            try {
                return finiteRoles.proveWholeComponent(gray, w, h, head, fringe.budget());
            } catch (RuntimeException unknown) {
                return null;
            }
        }

        ObservedBarRasterFringe.MemberAssignment assignFringe(Head head) {
            if (fringe == null
                    || head == null
                    || head.memberToken == null
                    || head.measure != fringe.staff.measure()
                    || head.staff != fringe.staff.staff()
                    || !Double.isFinite(head.staffTop)
                    || head.staffTop != fringe.staff.top()
                    || Math.abs(head.gap - fringe.staff.gap()) > .001
                    || !head.memberToken.labelSha().equals(fringe.labelSha)) {
                trace("fringeAssignmentRefuse=CONTEXT head=" + (head == null ? -1 : head.id));
                return null;
            }
            if (!prechargeBodyIdentityScan()) return null;
            ObservedBarRasterFringe.MemberAssignment found = null;
            try {
                for (var body : fringe.bodies)
                    if (containsWholeRawBody(body)) {
                        var a =
                                ObservedBarRasterFringe.assignSemanticComponent(
                                        fringe.snapshot,
                                        fringe.staff,
                                        head.memberToken,
                                        fringe.evidence,
                                        body,
                                        fringe.budget);
                        if (a != null) {
                            if (found != null) return null;
                            found = a;
                        } else
                            trace(
                                    "fringeAssignmentRefuse=MEMBER_ROLE head="
                                            + head.id
                                            + " body="
                                            + body.size());
                    }
            } catch (RuntimeException unknown) {
                trace("fringeAssignmentRefuse=" + unknown.getMessage() + " head=" + head.id);
                return null;
            }
            if (found == null) trace("fringeAssignmentRefuse=NO_EXACT_BODY head=" + head.id);
            return found;
        }

        boolean hasCompleteFringeBodyAuthority(Head owner) {
            if (fringe == null) return owner == null || owner.memberToken == null;
            if (owner == null
                    || owner.memberToken == null
                    || owner.measure != fringe.staff.measure()
                    || owner.staff != fringe.staff.staff()
                    || !Double.isFinite(owner.staffTop)
                    || owner.staffTop != fringe.staff.top()
                    || Math.abs(owner.gap - fringe.staff.gap()) > .001
                    || !owner.memberToken.labelSha().equals(fringe.labelSha)
                    || !owner.memberToken.rawSha().equals(fringe.snapshot.rawSha)) return false;
            if (!prechargeBodyIdentityScan()) return false;
            boolean found = false;
            for (var body : fringe.bodies)
                if (containsWholeRawBody(body)) {
                    found = true;
                    if (!fringe.evidence.bodyComplete(body)) return false;
                }
            return found;
        }

        boolean ownsFringePixel(
                ObservedBarRasterFringe.MemberAssignment assignment, Head head, int pixel) {
            if (fringe == null
                    || assignment == null
                    || head == null
                    || head.memberToken == null
                    || assignment.headToken() != head.memberToken
                    || !containsWholeRawBody(assignment.bodyIdentity())) return false;
            try {
                return fringe.evidence.role(
                                fringe.snapshot,
                                assignment.bodyIdentity(),
                                pixel % fringe.snapshot.width,
                                pixel / fringe.snapshot.width,
                                fringe.budget)
                        == ObservedBarRasterFringe.Role.OBSERVED_CONTRASTED_FRINGE;
            } catch (RuntimeException unknown) {
                return false;
            }
        }

        boolean coreContains(double x, double y) {
            if (x < bodyLeft || x > bodyRight) return false;
            double center = (left + right) * .5;
            for (Band band : bands)
                if (Math.abs(y - band.center - slope * (x - center)) <= band.thickness * .5)
                    return true;
            return false;
        }

        boolean contains(double x, double y, double gap) {
            if (x < left || x > right) return false;
            double center = (left + right) * .5;
            for (Band band : bands)
                if (Math.abs(y - band.center - slope * (x - center))
                        <= band.thickness * .5 + gap * .1) return true;
            return false;
        }
    }

    record FringeBinding(
            SparseStaffContact.Snapshot snapshot,
            ObservedBarRasterFringe.Evidence evidence,
            List<ObservedBarRasterFringe.BodyIdentity> bodies,
            SparseStaffContact.StaffIdentity staff,
            String labelSha,
            SparseStaffContact.Budget budget) {}

    private record ContactProof(
            SparseStaffContact.Snapshot snapshot,
            RawMeasuredStaffTracks.Evidence evidence,
            SparseStaffContact.Budget queryBudget) {}

    private static final long MAX_LANE_CONTACT_WORK = 6_000_000L;
    private static final long MAX_LANE_CONTACT_BYTES = 32L * 1024 * 1024;

    private static int rounded(double value) {
        return (int) Math.rint(value);
    }

    private static boolean valid(byte[] gray, int w, int h, double gap) {
        return gray != null
                && w > 0
                && h > 0
                && gray.length == (long) w * h
                && Double.isFinite(gap)
                && gap >= 5;
    }

    private static boolean dark(byte[] gray, int w, int x, int y) {
        return (gray[y * w + x] & 255) < 185;
    }

    /** Returns evidence only. Callers must retain the pair's semantics before any head removal. */
    static List<Pair> legacyPairs(byte[] gray, int w, int h, List<Head> input) {
        List<Head> heads = input.stream().sorted(Comparator.comparingDouble(Head::x)).toList();
        java.util.Map<Integer, Stem> stems = new java.util.HashMap<>();
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (Head head : heads) {
            if (!ids.add(head.id)) throw new IllegalArgumentException("Duplicate head identity");
            Stem anchor = stem(gray, w, h, head.x, head.y, head.gap, head.direction);
            // Unclassified heads still need an independent-stem check before rejection.
            if (head.direction == 0) {
                anchor = stem(gray, w, h, head.x, head.y, head.gap, 1);
                if (anchor == null) anchor = stem(gray, w, h, head.x, head.y, head.gap, -1);
            }
            if (anchor != null) stems.put(head.id, anchor);
        }
        List<Pair> result = new ArrayList<>();
        for (int i = 0; i < heads.size(); i++) {
            Head a = heads.get(i);
            if (a.direction == 0 || !stems.containsKey(a.id)) continue;
            for (int j = i + 1; j < heads.size(); j++) {
                Head b = heads.get(j);
                if (b.x - a.x > 5 * a.gap) break;
                if (a.measure != b.measure
                        || a.staff != b.staff
                        || a.direction != b.direction
                        || Math.abs(a.gap - b.gap) > .1 * a.gap
                        || !stems.containsKey(b.id)) continue;
                List<Mark> marks = find(gray, w, h, stems.get(a.id), stems.get(b.id), a.gap);
                if (marks.isEmpty()) continue;
                List<Integer> rejected = new ArrayList<>();
                boolean safe = true;
                for (Head other : heads) {
                    if (other.measure != a.measure
                            || other.staff != a.staff
                            || other.x <= a.x
                            || other.x >= b.x) continue;
                    if (stems.containsKey(other.id)
                            || marks.stream().noneMatch(m -> m.contains(other.x, other.y, a.gap))) {
                        safe = false;
                        break;
                    }
                    rejected.add(other.id);
                }
                if (safe) result.add(new Pair(a.id, b.id, rejected, marks));
            }
        }
        java.util.Map<Integer, Integer> owners = new java.util.HashMap<>();
        for (Pair pair : result) {
            owners.merge(pair.firstId(), 1, Integer::sum);
            owners.merge(pair.secondId(), 1, Integer::sum);
        }
        return result.stream()
                .filter(p -> owners.get(p.firstId()) == 1 && owners.get(p.secondId()) == 1)
                .toList();
    }

    /** Add complete common-shaft endpoints; old accepted pairs retain priority. */
    static final class Discovery {
        private final byte[] gray;
        private final int width, height;
        private final List<Head> roster;
        private final List<Pair> pairs;
        private final java.util.IdentityHashMap<Pair, EndpointScope> scopes =
                new java.util.IdentityHashMap<>();

        private Discovery(byte[] gray, int w, int h, List<Head> roster, List<Pair> pairs) {
            this.gray = gray;
            width = w;
            height = h;
            this.roster = List.copyOf(roster);
            this.pairs = List.copyOf(pairs);
            for (Pair pair : pairs)
                if (pair.unresolvedPhysicalSource()
                        && !pair.marks().isEmpty()
                        && pair.marks().stream()
                                .allMatch(mark -> mark.fringe != null && mark.hasExactBody())) {
                    var endpoints = new ArrayList<Head>();
                    boolean valid = true;
                    for (var side : List.of(pair.firstIds(), pair.secondIds()))
                        for (int id : side) {
                            var matches = roster.stream().filter(head -> head.id() == id).toList();
                            if (matches.size() != 1 || matches.get(0).memberToken() == null) {
                                valid = false;
                                break;
                            }
                            endpoints.add(matches.get(0));
                        }
                    if (valid)
                        scopes.put(pair, new EndpointScope(this, pair, List.copyOf(endpoints)));
                }
        }

        List<Pair> pairs() {
            return pairs;
        }

        EndpointScope endpointScope(Pair pair, byte[] gray, int w, int h) {
            return this.gray == gray && width == w && height == h ? scopes.get(pair) : null;
        }
    }

    static final class EndpointScope {
        private final Discovery source;
        private final Pair pair;
        private final List<Head> endpoints;

        private EndpointScope(Discovery source, Pair pair, List<Head> endpoints) {
            this.source = source;
            this.pair = pair;
            this.endpoints = endpoints;
        }

        List<Head> endpoints() {
            return endpoints;
        }

        boolean binds(
                Discovery discovery,
                Pair actual,
                ExactComponentMembers.Collector collector,
                java.util.Map<Integer, ?> components,
                byte[] gray,
                int w,
                int h) {
            if (source != discovery
                    || pair != actual
                    || source.endpointScope(actual, gray, w, h) != this) return false;
            for (var head : endpoints)
                if (head.memberToken() != collector.registeredProbeToken(components.get(head.id()))
                        || !head.memberToken().authenticates(gray, w, h)) return false;
            for (var mark : pair.marks())
                if (mark.fringe == null
                        || mark.fringe.snapshot().width != w
                        || mark.fringe.snapshot().height != h
                        || !mark.fringe.snapshot().rawSha.equals(collector.rawSha)
                        || !mark.fringe.labelSha().equals(collector.labelSha)) return false;
            return true;
        }
    }

    static Discovery discover(byte[] gray, int w, int h, List<Head> input) {
        return new Discovery(gray, w, h, input, detectPairs(gray, w, h, input));
    }

    static List<Pair> pairs(byte[] gray, int w, int h, List<Head> input) {
        return discover(gray, w, h, input).pairs();
    }

    private static List<Pair> detectPairs(byte[] gray, int w, int h, List<Head> input) {
        trace("pairs.input=" + input.size() + " image=" + w + "x" + h);
        List<Pair> result = new ArrayList<>(legacyPairs(gray, w, h, input));
        if (gray == null || gray.length != (long) w * h || input.size() > 2048)
            return List.copyOf(result);
        var groups = new java.util.LinkedHashMap<String, List<Head>>();
        var shafts = new java.util.HashMap<String, Stem>();
        var ids = new java.util.HashSet<Integer>();
        for (Head head : input) {
            if (!ids.add(head.id)) throw new IllegalArgumentException("Duplicate head identity");
            if (head.direction == 0
                    || head.footprint.isEmpty()
                    || head.footprint.size() > 4096
                    || !Double.isFinite(head.gap)
                    || head.gap < 5
                    || head.gap > 64) continue;
            Stem shaft = stem(gray, w, h, head.x, head.y, head.gap, head.direction);
            if (shaft == null) {
                trace("head=" + head.id + " stem=NONE");
                continue;
            }
            trace(
                    "head="
                            + head.id
                            + " xy="
                            + head.x
                            + ","
                            + head.y
                            + " gap="
                            + head.gap
                            + " dir="
                            + head.direction
                            + " footprint="
                            + head.footprint.size()
                            + " token="
                            + (head.memberToken != null)
                            + " staffTop="
                            + head.staffTop
                            + " shaft="
                            + shaft.x
                            + ","
                            + shaft.headY
                            + ".."
                            + shaft.endY);
            String key =
                    head.measure
                            + ":"
                            + head.staff
                            + ":"
                            + head.direction
                            + ":"
                            + shaft.x
                            + ":"
                            + shaft.endY;
            // Carry this same measured shaft into contact certification. The caller's
            // earlier attachment proposal can use a different raster column.
            // Full raw continuity and exact member contact are still rechecked.
            Head grouped =
                    new Head(
                            head.id,
                            head.measure,
                            head.staff,
                            head.x,
                            head.y,
                            head.gap,
                            head.direction,
                            head.footprint,
                            head.removalEligible,
                            head.staffTop,
                            head.memberToken,
                            new int[] {shaft.x, shaft.headY, shaft.endY});
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(grouped);
            shafts.putIfAbsent(key, shaft);
        }
        trace("groups=" + groups.size() + " keys=" + groups.keySet());
        if (groups.size() > 2048) return List.copyOf(result);
        List<String> keys = new ArrayList<>(groups.keySet());
        keys.sort(Comparator.comparingInt(k -> shafts.get(k).x));
        List<Pair> added = new ArrayList<>();
        var joinedWorkLedger = new JoinedLaneWorkBudget();
        var contactBudgetByLane = new java.util.HashMap<String, SparseStaffContact.Budget>();
        var refusedLanes = new java.util.HashSet<String>();
        var lanes = new java.util.HashMap<Integer, String>();
        for (Head head : input) lanes.put(head.id, head.measure + ":" + head.staff);
        for (int i = 0; i < keys.size(); i++)
            for (int j = i + 1; j < keys.size(); j++) {
                List<Head> first = groups.get(keys.get(i)), second = groups.get(keys.get(j));
                Head a = first.get(0), b = second.get(0);
                Stem left = shafts.get(keys.get(i)), right = shafts.get(keys.get(j));
                if (a.measure != b.measure
                        || a.staff != b.staff
                        || a.direction != b.direction
                        || Math.abs(a.gap - b.gap) > .1 * a.gap
                        || right.x - left.x < 1.4 * a.gap
                        || right.x - left.x > 5 * a.gap
                        || first.size() > 128
                        || second.size() > 128) continue;
                var endpointIds = new java.util.HashSet<Integer>();
                for (Head x : first) endpointIds.add(x.id);
                for (Head x : second) endpointIds.add(x.id);
                if (result.stream()
                        .anyMatch(
                                p ->
                                        p.firstIds.stream().anyMatch(endpointIds::contains)
                                                || p.secondIds.stream()
                                                        .anyMatch(endpointIds::contains))) continue;
                int minY = Integer.MAX_VALUE, maxY = -1;
                for (Head x : first) {
                    minY = Math.min(minY, rounded(x.y));
                    maxY = Math.max(maxY, rounded(x.y));
                }
                for (Head x : second) {
                    minY = Math.min(minY, rounded(x.y));
                    maxY = Math.max(maxY, rounded(x.y));
                }
                minY = Math.min(minY, Math.min(left.endY, right.endY));
                maxY = Math.max(maxY, Math.max(left.endY, right.endY));
                long charge =
                        (long) (right.x - left.x + 1) * (maxY - minY + 1)
                                + 24L * (right.x - left.x + 1) * (maxY - minY + rounded(a.gap) + 3);
                for (Head x : input) charge += x.footprint.size();
                String lane = lanes.get(a.id);
                if (refusedLanes.contains(lane)) continue;
                if (!joinedWorkLedger.charge(lane, charge)) {
                    refusedLanes.add(lane);
                    continue;
                }
                trace(
                        "lane="
                                + lane
                                + " shafts="
                                + left.x
                                + ","
                                + right.x
                                + " y="
                                + minY
                                + ".."
                                + maxY
                                + " gap="
                                + a.gap);
                if (foreignShaft(
                        gray,
                        w,
                        h,
                        rounded(left.x + .18 * a.gap),
                        rounded(right.x - .18 * a.gap),
                        minY,
                        maxY,
                        a.gap)) {
                    trace("reject=foreignShaft");
                    continue;
                }
                SparseStaffContact.Budget contactBudget =
                        contactBudgetByLane.computeIfAbsent(
                                lane,
                                ignored ->
                                        new SparseStaffContact.Budget(
                                                MAX_LANE_CONTACT_WORK, MAX_LANE_CONTACT_BYTES));
                long contactWorkBefore = contactBudget.work;
                ContactProof laneContactProof =
                        contactProof(gray, w, h, a.gap, first, second, contactBudget);
                trace("contactProofBeforeDiscovery=" + (laneContactProof != null));
                Set<Integer> carrier = null;
                if (laneContactProof != null)
                    try {
                        carrier =
                                laneContactProof.evidence.foregroundCarrier(
                                        laneContactProof.snapshot,
                                        Math.min(left.x, right.x),
                                        Math.max(1, minY - rounded(a.gap)),
                                        Math.max(left.x, right.x),
                                        Math.min(h - 2, maxY + rounded(a.gap)),
                                        laneContactProof.queryBudget);
                    } catch (RuntimeException unknown) {
                    }
                long contactWorkUsed = contactWorkBefore - contactBudget.work;
                if (!joinedWorkLedger.chargePage(contactWorkUsed)) {
                    refusedLanes.add(lane);
                    continue;
                }
                boolean joined = carrier != null && !carrier.isEmpty();
                if (joined) {
                    int laneTop = Math.max(1, minY - rounded(a.gap)),
                            laneBottom = Math.min(h - 2, maxY + rounded(a.gap));
                    var workLane =
                            new MaximalRawBarExtractor.Lane(
                                    left.x, right.x, laneTop, laneBottom, rounded(a.gap));
                    if (!joinedWorkLedger.activateJoined(
                            lane, MaximalRawBarExtractor.joinedWorkBudgetFor(workLane))) {
                        refusedLanes.add(lane);
                        continue;
                    }
                }
                long helperAllowance = joinedWorkLedger.helperAllowance(lane);
                if (helperAllowance < 1) {
                    refusedLanes.add(lane);
                    continue;
                }
                long[] bodyBudget = {helperAllowance};
                List<Mark> complete =
                        maximalRawMarks(
                                gray,
                                w,
                                h,
                                left,
                                right,
                                a.gap,
                                minY,
                                maxY,
                                bodyBudget,
                                laneContactProof,
                                carrier,
                                contactBudget,
                                first,
                                second);
                trace(
                        "lane="
                                + lane
                                + " completeMarks="
                                + complete.size()
                                + " remainingBudget="
                                + bodyBudget[0]);
                long helperWork =
                        Math.min(helperAllowance, Math.max(0L, helperAllowance - bodyBudget[0]));
                if (!joinedWorkLedger.chargeHelper(lane, helperAllowance, helperWork)) {
                    refusedLanes.add(lane);
                    continue;
                }
                if (joinedWorkLedger.remaining(lane) <= 0) {
                    refusedLanes.add(lane);
                    continue;
                }
                if (complete.isEmpty()) continue;
                List<Head> middle = new ArrayList<>();
                boolean safe = true;
                for (Head other : input) {
                    if (endpointIds.contains(other.id)
                            || other.measure != a.measure
                            || other.staff != a.staff
                            || other.x <= left.x
                            || other.x >= right.x) continue;
                    if (other.direction != 0
                            || !other.removalEligible
                            || other.footprint.isEmpty()
                            || other.footprint.size() > 4096
                            || stem(gray, w, h, other.x, other.y, other.gap, 1) != null
                            || stem(gray, w, h, other.x, other.y, other.gap, -1) != null) {
                        safe = false;
                        break;
                    }
                    middle.add(other);
                }
                if (!safe) continue;
                // A complete three-stroke interpretation must cover every original rail pixel;
                // never assemble one component from different competing mark interpretations.
                List<Mark> proved = new ArrayList<>();
                for (Mark m : complete) {
                    long lookupCost =
                            34 - Integer.numberOfLeadingZeros(Math.max(1, m.rawBodyPixelCount()));
                    long semanticCharge =
                            middle.stream()
                                    .mapToLong(t -> t.footprint.size() * (lookupCost + 2))
                                    .sum();
                    if (!joinedWorkLedger.charge(lane, semanticCharge)) {
                        refusedLanes.add(lane);
                        break;
                    }
                    ContactProof contactProof = laneContactProof;
                    boolean all = true;
                    for (Head other : middle) {
                        if (!ownsExactBodyFootprint(
                                gray, w, h, m, other.footprint, a.gap, contactProof, other))
                            all = false;
                    }
                    if (all) proved.add(m);
                }
                trace("lane=" + lane + " middle=" + middle.size() + " proved=" + proved.size());
                if (!proved.isEmpty()) {
                    int strokes = proved.get(0).bands().size();
                    if (strokes < 2 || strokes > 3) continue;
                    added.add(
                            new Pair(
                                    first.stream().map(Head::id).sorted().toList(),
                                    second.stream().map(Head::id).sorted().toList(),
                                    middle.stream().map(Head::id).toList(),
                                    proved,
                                    strokes,
                                    true));
                }
            }
        var use = new java.util.HashMap<Integer, Integer>();
        added.removeIf(p -> refusedLanes.contains(lanes.get(p.firstId())));
        for (Pair p : added)
            for (List<Integer> side : List.of(p.firstIds, p.secondIds))
                for (int id : side) use.merge(id, 1, Integer::sum);
        for (Pair p : added)
            if (p.firstIds.stream().allMatch(id -> use.get(id) == 1)
                    && p.secondIds.stream().allMatch(id -> use.get(id) == 1)) result.add(p);
        return List.copyOf(result);
    }

    /** Discover complete finite raw bodies over the whole eligible pair lane, independent of a proposal. */
    private static List<Mark> maximalRawMarks(
            byte[] gray,
            int w,
            int h,
            Stem left,
            Stem right,
            double gap,
            int minY,
            int maxY,
            long[] budget,
            ContactProof proof,
            java.util.Set<Integer> carrier,
            SparseStaffContact.Budget laneEvidenceBudget,
            List<Head> first,
            List<Head> second) {
        int gapPx = rounded(gap), laneLeft = left.x, laneRight = right.x;
        if (laneRight - laneLeft < 1 || laneRight - laneLeft > 5 * gap) return List.of();
        int top = Math.max(1, minY - gapPx), bottom = Math.min(h - 2, maxY + gapPx);
        if (top >= bottom) return List.of();
        var lane = new MaximalRawBarExtractor.Lane(laneLeft, laneRight, top, bottom, gapPx);
        var staffPixels = observedStaffPixels(proof, laneLeft, top, laneRight, bottom, w);
        boolean joined = carrier != null && !carrier.isEmpty();
        if (joined) staffPixels = carrier;
        var discovery =
                joined
                        ? MaximalRawBarExtractor.discoverJoined(
                                gray,
                                w,
                                h,
                                lane,
                                budget[0],
                                MaximalRawBarExtractor.MAX_SCRATCH_BYTES,
                                staffPixels)
                        : MaximalRawBarExtractor.discover(
                                gray,
                                w,
                                h,
                                lane,
                                budget[0],
                                MaximalRawBarExtractor.MAX_SCRATCH_BYTES,
                                staffPixels);
        trace("staffJoinedRoute=" + joined + " carrier=" + (carrier == null ? -1 : carrier.size()));
        budget[0] -= discovery.pixelWork();
        trace(
                "bodyDiscovery lane="
                        + laneLeft
                        + ".."
                        + laneRight
                        + "/"
                        + top
                        + ".."
                        + bottom
                        + " status="
                        + discovery.status()
                        + " bodies="
                        + discovery.bodies().size()
                        + " work="
                        + discovery.pixelWork()
                        + " remaining="
                        + budget[0]);
        for (var b : discovery.bodies())
            trace(
                    "body x="
                            + b.firstX()
                            + ".."
                            + b.lastX()
                            + " slope="
                            + b.slopeTick()
                            + " n="
                            + b.rawPixels().size()
                            + " caps="
                            + b.firstCapWidth()
                            + "/"
                            + b.lastCapWidth()
                            + " max="
                            + b.maxWidth());
        discovery = removeEndpointOwnedBodyAliases(discovery, gray, w, h, first, second, budget);
        if (discovery == null) return List.of();
        MaximalRawBarExtractor.FiniteFamilyProof finiteProof = null;
        if (joined) {
            long familyCost = discovery.observedFamilyWorkUpperBound(w, staffPixels);
            if (familyCost == Long.MAX_VALUE || familyCost > budget[0]) return List.of();
            budget[0] -= familyCost;
            if (!discovery.twoOrThreeObservedFamily(gapPx, w, staffPixels)) {
                long completionCost = discovery.staffBridgeWorkUpperBound(gapPx);
                if (completionCost == Long.MAX_VALUE || completionCost > budget[0])
                    return List.of();
                budget[0] -= completionCost;
                discovery =
                        discovery.completeStaffBridges(
                                gray, w, h, staffPixels, gapPx, completionCost);
                if (discovery == null) return List.of();
                long finiteCost = discovery.finiteFamilyWorkUpperBound(w, staffPixels);
                if (finiteCost == Long.MAX_VALUE || finiteCost > budget[0]) return List.of();
                budget[0] -= finiteCost;
                finiteProof =
                        discovery.proveFiniteFamily(gapPx, w, staffPixels, gray, h, finiteCost);
                if (finiteProof == null) return List.of();
            }
        } else if (!discovery.twoOrThreeSameShape(gapPx)) return List.of();
        // Contacts remain UNKNOWN until independent RuleEvidence is available.
        long rawCount = discovery.bodies().stream().mapToLong(b -> b.rawPixels().size()).sum();
        if (rawCount <= 0 || rawCount > 500_000) return List.of();
        int sortLog = 32 - Integer.numberOfLeadingZeros((int) rawCount);
        long materializationWork;
        try {
            materializationWork = Math.multiplyExact(rawCount, sortLog + 2L);
        } catch (ArithmeticException overflow) {
            return List.of();
        }
        if (materializationWork > budget[0]) return List.of();
        // Two int arrays coexist (sort source plus immutable Mark copy), capped at 8 MiB.
        long bodyStorage = rawCount * 8L + 256;
        if (bodyStorage > 8L * 1024 * 1024) return List.of();
        budget[0] -= materializationWork;
        int[] bodyPixels = new int[(int) rawCount];
        int n = 0;
        for (var body : discovery.bodies())
            for (int pixel : body.rawPixels()) bodyPixels[n++] = pixel;
        java.util.Arrays.sort(bodyPixels);
        for (int i = 1; i < bodyPixels.length; i++)
            if (bodyPixels[i - 1] == bodyPixels[i]) return List.of();
        int mid = (laneLeft + laneRight) / 2;
        List<MaximalRawBarExtractor.Body> bodies =
                discovery.bodies().stream()
                        .sorted(Comparator.comparingInt(b -> b.centerAt(mid, mid)))
                        .toList();
        double slope = bodies.get(0).slopeTick() * .05;
        int bodyLeft =
                bodies.stream()
                        .mapToInt(MaximalRawBarExtractor.Body::firstX)
                        .min()
                        .orElse(laneLeft);
        int bodyRight =
                bodies.stream()
                        .mapToInt(MaximalRawBarExtractor.Body::lastX)
                        .max()
                        .orElse(laneRight);
        double bodyCenter = (bodyLeft + bodyRight) * .5;
        List<Band> bands =
                bodies.stream()
                        .map(
                                b ->
                                        new Band(
                                                b.centerAtLaneMid()
                                                        + b.slopeTick() * .05 * (bodyCenter - mid),
                                                b.maxWidth()))
                        .toList();
        long groupSortWork = 0;
        List<int[]> rawGroups = new ArrayList<>();
        for (var body : bodies) {
            int[] group = body.rawPixels().stream().mapToInt(Integer::intValue).toArray();
            int log = 32 - Integer.numberOfLeadingZeros(Math.max(1, group.length));
            groupSortWork += ((long) group.length) * log;
            rawGroups.add(group);
        }
        if (groupSortWork > budget[0]) return List.of();
        budget[0] -= groupSortWork;
        rawGroups.forEach(java.util.Arrays::sort);
        FringeBinding fringe =
                observeFringe(
                        gray,
                        w,
                        h,
                        lane,
                        first,
                        second,
                        rawGroups,
                        discovery,
                        joined,
                        staffPixels,
                        laneEvidenceBudget);
        ObservedFiniteRasterRoles.Evidence finiteRoles = null;
        if (finiteProof != null) {
            if (fringe == null) return List.of();
            var staffField =
                    proof == null
                            ? null
                            : ObservedStaffRasterField.produce(
                                    proof.snapshot(),
                                    proof.evidence().staff,
                                    proof.evidence(),
                                    fringe.budget());
            finiteRoles =
                    ObservedFiniteRasterRoles.produce(
                            gray,
                            fringe.snapshot(),
                            discovery,
                            finiteProof,
                            staffPixels,
                            fringe.staff(),
                            fringe.labelSha(),
                            staffField,
                            fringe.budget());
            trace("finiteRasterRoles=" + (finiteRoles != null));
            if (finiteRoles == null) return List.of();
        }
        return List.of(
                new Mark(
                        bodyLeft,
                        bodyRight,
                        slope,
                        bands,
                        bodyLeft,
                        bodyRight,
                        bodyPixels,
                        rawGroups,
                        fringe,
                        finiteRoles));
    }

    /** Re-discover complete bodies and optical fringe from the same authenticated lane snapshot. */
    private static FringeBinding observeFringe(
            byte[] gray,
            int w,
            int h,
            MaximalRawBarExtractor.Lane lane,
            List<Head> first,
            List<Head> second,
            List<int[]> retainedRawGroups,
            MaximalRawBarExtractor.Discovery discovery,
            boolean joined,
            java.util.Set<Integer> observedStaffPixels,
            SparseStaffContact.Budget budget) {
        Head base = !first.isEmpty() ? first.get(0) : !second.isEmpty() ? second.get(0) : null;
        if (base == null
                || base.memberToken == null
                || !Double.isFinite(base.staffTop)
                || budget == null
                || observedStaffPixels == null) return null;
        int pad = (int) Math.ceil(.5 * lane.gap()) + 1;
        var scope =
                new SparseStaffContact.Rect(
                        Math.max(0, lane.left() - 1),
                        Math.max(0, lane.top() - pad),
                        Math.min(w - 1, lane.right() + 1),
                        Math.min(h - 1, lane.bottom() + pad));
        try {
            var snapshot =
                    SparseStaffContact.Snapshot.captureAuthenticated(
                            gray, w, h, base.memberToken, scope, budget);
            var result =
                    ObservedBarRasterFringe.produce(
                            snapshot, lane, observedStaffPixels, discovery, joined, budget);
            if (!result.complete()) {
                trace("fringeProofRefuse=PRODUCER_" + result.refusal());
                return null;
            }
            List<ObservedBarRasterFringe.BodyIdentity> matching = new ArrayList<>();
            for (int i = 0; i < result.evidence().bodyCount(); i++) {
                var body = result.evidence().body(i);
                boolean same = false;
                if (body.size() > 0) {
                    for (int[] group : retainedRawGroups)
                        if (group.length == body.size()) {
                            same = true;
                            int mismatch = -1;
                            for (int j = 0; j < body.size(); j++)
                                if (java.util.Arrays.binarySearch(group, body.pixel(j)) < 0) {
                                    same = false;
                                    mismatch = j;
                                    break;
                                }
                            if (same) break;
                        }
                }
                if (same) matching.add(body);
            }
            if (matching.isEmpty()) {
                trace("fringeProofRefuse=BODY_IDENTITY_MATCH");
                return null;
            }
            trace("fringeProofMatch=" + matching.size() + " of " + result.evidence().bodyCount());
            return new FringeBinding(
                    snapshot,
                    result.evidence(),
                    List.copyOf(matching),
                    new SparseStaffContact.StaffIdentity(
                            base.measure, base.staff, base.staffTop, base.gap),
                    base.memberToken.labelSha(),
                    budget);
        } catch (RuntimeException unknown) {
            trace("fringeProofRefuse=" + unknown.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * An independently discovered raw body wholly inside an exact accepted endpoint
     * component is semantic endpoint ink, not an additional tremolo stroke. Mixed
     * endpoint/body membership remains ambiguous and refuses the lane. This does not
     * erase endpoint pixels or authorize their removal.
     */
    static MaximalRawBarExtractor.Discovery removeEndpointOwnedBodyAliases(
            MaximalRawBarExtractor.Discovery discovery,
            byte[] gray,
            int w,
            int h,
            List<Head> first,
            List<Head> second,
            long[] budget) {
        if (discovery == null || discovery.status() != MaximalRawBarExtractor.Status.CANDIDATES)
            return discovery;
        List<Head> allEndpoints = new ArrayList<>(first.size() + second.size());
        allEndpoints.addAll(first);
        allEndpoints.addAll(second);
        if (allEndpoints.stream().anyMatch(e -> e.memberToken == null)) {
            // Legacy callers have no authenticated component tokens. Preserve the
            // independent raw-body route only when its exact pixels do not overlap
            // any supplied endpoint pixels; never infer ownership from a box.
            if (allEndpoints.stream()
                    .anyMatch(
                            e ->
                                    e.memberToken != null
                                            || e.footprint.isEmpty()
                                            || e.footprint.size() > 4096)) return null;
            long endpointPixels = 0, bodyPixels = 0;
            for (Head e : allEndpoints) endpointPixels += e.footprint.size();
            for (MaximalRawBarExtractor.Body body : discovery.bodies())
                bodyPixels += body.rawPixels().size();
            long charge = endpointPixels + bodyPixels;
            if (charge > budget[0] || endpointPixels * 8L + 1024 > 8L * 1024 * 1024) return null;
            budget[0] -= charge;
            java.util.Set<Integer> exact =
                    new java.util.HashSet<>(
                            (int) Math.min(Integer.MAX_VALUE, endpointPixels * 2 + 1));
            for (Head e : allEndpoints)
                for (int pixel : e.footprint) if (!exact.add(pixel)) return null;
            for (MaximalRawBarExtractor.Body body : discovery.bodies())
                for (int pixel : body.rawPixels()) if (exact.contains(pixel)) return null;
            return discovery;
        }
        long endpointCount = 0;
        List<Head> endpoints = new ArrayList<>(first.size() + second.size());
        endpoints.addAll(first);
        endpoints.addAll(second);
        for (Head endpoint : endpoints) {
            if (endpoint.memberToken == null
                    || !endpoint.memberToken.authenticates(gray, w, h)
                    || endpoint.memberToken.measure != endpoint.measure
                    || endpoint.memberToken.staff != endpoint.staff) {
                trace("endpointBodyFilter=TOKEN_IDENTITY");
                return null;
            }
            if (endpoint.measure != endpoints.get(0).measure
                    || endpoint.staff != endpoints.get(0).staff
                    || !endpoint.memberToken
                            .labelSha()
                            .equals(endpoints.get(0).memberToken.labelSha())) {
                trace("endpointBodyFilter=TOKEN_CONTEXT");
                return null;
            }
            long size = endpoint.memberToken.stats().area();
            if (size <= 0 || size > 500_000 || endpointCount > 500_000 - size) {
                trace("endpointBodyFilter=TOKEN_SIZE");
                return null;
            }
            endpointCount += size;
        }
        long bodyPixels = 0;
        for (MaximalRawBarExtractor.Body body : discovery.bodies())
            bodyPixels += body.rawPixels().size();
        int lookupLog = 32 - Integer.numberOfLeadingZeros((int) Math.max(1, endpointCount));
        long sortWork, lookupWork, overlapWork, totalWork, bytes;
        try {
            sortWork = Math.multiplyExact(endpointCount, lookupLog);
            lookupWork =
                    Math.multiplyExact(
                            bodyPixels, Math.multiplyExact((long) endpoints.size(), lookupLog));
            overlapWork =
                    Math.multiplyExact(
                            endpointCount,
                            Math.multiplyExact(Math.max(0, endpoints.size() - 1L), lookupLog));
            totalWork =
                    Math.addExact(
                            endpointCount,
                            Math.addExact(sortWork, Math.addExact(lookupWork, overlapWork)));
            bytes =
                    Math.addExact(
                            Math.multiplyExact(endpointCount, 4L),
                            Math.addExact(
                                    Math.multiplyExact((long) endpoints.size(), 32L),
                                    Math.multiplyExact((long) discovery.bodies().size(), 64L)));
        } catch (ArithmeticException overflow) {
            return null;
        }
        if (totalWork > budget[0] || bytes > 8L * 1024 * 1024) {
            trace("endpointBodyFilter=BUDGET");
            return null;
        }
        // Debit before allocation and scans. Refusals must not make the same work free.
        budget[0] -= totalWork;
        var memory = new ExactComponentMembers.Budget(endpointCount, bytes);
        List<int[]> ownedByEndpoint = new ArrayList<>(endpoints.size());
        for (Head endpoint : endpoints) {
            int[] pixels = endpoint.memberToken.pixels(memory);
            if (pixels == null) {
                trace("endpointBodyFilter=MEMBER_READ");
                return null;
            }
            java.util.Arrays.sort(pixels);
            for (int i = 1; i < pixels.length; i++) if (pixels[i - 1] == pixels[i]) return null;
            ownedByEndpoint.add(pixels);
        }
        if (memory.work != 0) {
            trace("endpointBodyFilter=MEMBER_WORK");
            return null;
        }
        for (int i = 0; i < ownedByEndpoint.size(); i++)
            for (int j = 0; j < i; j++) {
                int[] current = ownedByEndpoint.get(i), prior = ownedByEndpoint.get(j);
                for (int pixel : current)
                    if (java.util.Arrays.binarySearch(prior, pixel) >= 0) {
                        trace("endpointBodyFilter=OVERLAP");
                        return null;
                    }
            }
        List<MaximalRawBarExtractor.Body> retained = new ArrayList<>();
        for (MaximalRawBarExtractor.Body body : discovery.bodies()) {
            int totalMembers = 0, matches = 0;
            boolean anyOverlap = false;
            for (int[] endpointPixels : ownedByEndpoint) {
                int memberCount = 0;
                for (int pixel : body.rawPixels())
                    if (java.util.Arrays.binarySearch(endpointPixels, pixel) >= 0) memberCount++;
                if (memberCount > 0) {
                    anyOverlap = true;
                    totalMembers += memberCount;
                    if (memberCount == body.rawPixels().size()) matches++;
                }
            }
            if (!anyOverlap) {
                if (body.rawPixels().size() <= 16)
                    trace(
                            "endpointBodyMembership=UNOWNED_SMALL count="
                                    + body.rawPixels().size()
                                    + " pixels="
                                    + body.rawPixels());
                retained.add(body);
            } else if (totalMembers != body.rawPixels().size() || matches != 1) {
                trace("endpointBodyMembership=AMBIGUOUS");
                return null;
            } else trace("endpointBodyMembership=ENDPOINT_ONLY pixels=" + body.rawPixels().size());
        }
        // Rebind the selected-body digest while retaining the original authenticated
        // raster/lane/staff provenance. The three-argument constructor drops that
        // identity and makes the exact same-call Discovery unusable by downstream proof.
        return discovery.withBodies(retained, discovery.pixelWork() + totalWork);
    }

    static boolean ownsExactBodyFootprint(
            byte[] gray, int w, int h, Mark mark, List<Integer> footprint, double gap) {
        return ownsExactBodyFootprint(gray, w, h, mark, footprint, gap, null);
    }

    private static boolean ownsExactBodyFootprint(
            byte[] gray,
            int w,
            int h,
            Mark mark,
            List<Integer> footprint,
            double gap,
            ContactProof proof) {
        return ownsExactBodyFootprint(gray, w, h, mark, footprint, gap, proof, null);
    }

    static boolean ownsExactBodyFootprint(
            byte[] gray,
            int w,
            int h,
            Mark mark,
            List<Integer> footprint,
            double gap,
            ContactProof proof,
            Head owner) {
        if (gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || mark == null
                || !mark.hasExactBody()
                || footprint == null
                || footprint.isEmpty()) return false;
        if (mark.finiteRoles != null)
            return mark.fringe != null
                    && mark.finiteRoles.owns(gray, w, h, owner, mark.fringe.budget());
        if (!mark.hasCompleteFringeBodyAuthority(owner)) {
            trace("footprintRefuse=INCOMPLETE_BODY_PROFILE");
            return false;
        }
        boolean needsFringe = false;
        for (int pixel : footprint)
            if (pixel >= 0 && pixel < gray.length && (gray[pixel] & 255) >= 185) needsFringe = true;
        var assignment = needsFringe ? mark.assignFringe(owner) : null;
        boolean core = false;
        for (int pixel : footprint) {
            if (pixel < 0 || pixel >= (long) w * h) return false;
            int x = pixel % w, y = pixel / w;
            boolean dark = dark(gray, w, x, y);
            boolean fringe = mark.ownsFringePixel(assignment, owner, pixel);
            // Optical fringe is admitted only after the exact whole semantic component
            // has been assigned to this complete body profile; no subset is trimmed.
            if ((!fringe && !mark.contains(x, y, gap)) || (!dark && !fringe)) {
                trace(
                        "footprintRefuse=GEOMETRY_OR_ROLE pixel="
                                + pixel
                                + " gray="
                                + (gray[pixel] & 255)
                                + " fringe="
                                + fringe);
                return false;
            }
            if (!mark.containsRawBodyPixel(pixel)
                    && !fringe
                    && (proof == null
                            || !proof.evidence.ownsObservedNonownerPixel(
                                    proof.snapshot, x, y, proof.queryBudget))) {
                trace("footprintRefuse=UNOWNED_DARK pixel=" + pixel);
                return false;
            }
            if (mark.coreContains(x, y) && dark) core = true;
        }
        return core;
    }

    private static ContactProof contactProof(
            byte[] gray,
            int w,
            int h,
            double gap,
            List<Head> first,
            List<Head> second,
            SparseStaffContact.Budget laneBudget) {
        try {
            List<Head> endpoints = new ArrayList<>(first.size() + second.size());
            endpoints.addAll(first);
            endpoints.addAll(second);
            if (endpoints.isEmpty()) {
                trace("contactProofRefuse=EMPTY_ENDPOINTS");
                return null;
            }
            Head base = endpoints.get(0);
            if (base.memberToken == null || !Double.isFinite(base.staffTop)) {
                trace("contactProofRefuse=BASE_IDENTITY");
                return null;
            }
            int left = w, right = -1, ownerTop = h, ownerBottom = -1;
            List<ExactComponentMembers.AcceptedProbeHead> tokens = new ArrayList<>();
            List<RawMeasuredStaffTracks.Shaft> shafts = new ArrayList<>();
            for (Head head : endpoints) {
                if (head.measure != base.measure
                        || head.staff != base.staff
                        || head.memberToken == null
                        || head.staffTop != base.staffTop
                        || Math.abs(head.gap - gap) > .001
                        || head.shaftX < 1
                        || head.shaftHeadY < 0
                        || head.shaftEndY < 0) {
                    trace("contactProofRefuse=ENDPOINT_CONTEXT");
                    return null;
                }
                tokens.add(head.memberToken);
                var stats = head.memberToken.stats();
                left = Math.min(left, stats.minX());
                right = Math.max(right, stats.maxX());
                ownerTop = Math.min(ownerTop, stats.minY());
                ownerBottom = Math.max(ownerBottom, stats.maxY());
                int top = Math.min(head.shaftHeadY, head.shaftEndY),
                        bottom = Math.max(head.shaftHeadY, head.shaftEndY);
                ownerTop = Math.min(ownerTop, top);
                ownerBottom = Math.max(ownerBottom, bottom);
                left = Math.min(left, head.shaftX);
                right = Math.max(right, head.shaftX);
                shafts.add(new RawMeasuredStaffTracks.Shaft(head.shaftX, head.shaftX, top, bottom));
            }
            // Several accepted chord heads may share one physically continuous
            // shaft. Merge only overlapping spans on the same observed column;
            // the owner producer rechecks every raw pixel and rejects any gap.
            shafts.sort(
                    Comparator.comparingInt(RawMeasuredStaffTracks.Shaft::left)
                            .thenComparingInt(RawMeasuredStaffTracks.Shaft::top));
            List<RawMeasuredStaffTracks.Shaft> physicalShafts = new ArrayList<>();
            for (var shaft : shafts) {
                if (!physicalShafts.isEmpty()) {
                    var prior = physicalShafts.get(physicalShafts.size() - 1);
                    if (prior.left() == shaft.left()
                            && prior.right() == shaft.right()
                            && shaft.top() <= prior.bottom()) {
                        physicalShafts.set(
                                physicalShafts.size() - 1,
                                new RawMeasuredStaffTracks.Shaft(
                                        prior.left(),
                                        prior.right(),
                                        Math.min(prior.top(), shaft.top()),
                                        Math.max(prior.bottom(), shaft.bottom())));
                        continue;
                    }
                }
                physicalShafts.add(shaft);
            }
            int pad = (int) Math.ceil(4 * gap) + 2;
            int l = Math.max(1, left - pad), r = Math.min(w - 2, right + pad);
            // The snapshot must contain the complete exclusion operands, including
            // ledger-line heads and stems extending outside the five staff rails.
            // This grants no rule/head authority; all raw ownership checks remain.
            int t = Math.max(1, Math.min((int) Math.floor(base.staffTop - gap), ownerTop - 1));
            int b =
                    Math.min(
                            h - 2,
                            Math.max((int) Math.ceil(base.staffTop + 5 * gap), ownerBottom + 1));
            long area = (long) (r - l + 1) * (b - t + 1);
            if (r <= l || b <= t || area > 250_000) return null;
            var identity =
                    new SparseStaffContact.StaffIdentity(
                            base.measure, base.staff, base.staffTop, gap);
            var rect = new SparseStaffContact.Rect(l, t, r, b);
            var snapshot =
                    SparseStaffContact.Snapshot.captureAuthenticated(
                            gray, w, h, base.memberToken, rect, laneBudget);
            var owner =
                    RawMeasuredStaffTracks.OwnerFootprint.fromCapturedHeadsAndShafts(
                            snapshot, identity, tokens, physicalShafts, laneBudget);
            var result = RawMeasuredStaffTracks.produce(snapshot, identity, owner, laneBudget);
            trace(
                    "contactProofProducerComplete="
                            + result.complete()
                            + " refusal="
                            + result.refusal());
            return result.complete()
                    ? new ContactProof(snapshot, result.evidence(), laneBudget)
                    : null;
        } catch (RuntimeException unknown) {
            trace(
                    "contactProofException="
                            + unknown.getClass().getSimpleName()
                            + ":"
                            + unknown.getMessage());
            return null;
        }
    }

    private static java.util.Set<Integer> observedStaffPixels(
            ContactProof proof, int left, int top, int right, int bottom, int width) {
        if (proof == null) return null;
        var scope = proof.snapshot.scope;
        int scanLeft = Math.max(left, scope.left()), scanRight = Math.min(right, scope.right());
        int scanTop = Math.max(top, scope.top()), scanBottom = Math.min(bottom, scope.bottom());
        if (scanRight < scanLeft || scanBottom < scanTop) return java.util.Set.of();
        long area = (long) (scanRight - scanLeft + 1) * (scanBottom - scanTop + 1);
        if (area < 1 || area > 250_000) return null;
        try {
            // Set.copyOf materializes a second backing table while the mutable set is live.
            proof.queryBudget.memory(Math.addExact(Math.multiplyExact(area, 64L), 512L));
            java.util.Set<Integer> owned = new java.util.HashSet<>();
            for (int y = scanTop; y <= scanBottom; y++)
                for (int x = scanLeft; x <= scanRight; x++)
                    if (proof.evidence.ownsObservedNonownerPixel(
                            proof.snapshot, x, y, proof.queryBudget)) owned.add(y * width + x);
            trace(
                    "staffOwnedPixels="
                            + owned.size()
                            + " inspected="
                            + area
                            + " rows="
                            + owned.stream().map(p -> p / width).distinct().sorted().toList());
            return java.util.Set.copyOf(owned);
        } catch (RuntimeException unknown) {
            trace("staffMask=UNKNOWN");
            return null;
        }
    }

    private static boolean foreignShaft(
            byte[] g, int w, int h, int left, int right, int top, int bottom, double gap) {
        if (left < 0 || right >= w || top < 0 || bottom >= h) return true;
        for (int x = left; x <= right; x++) {
            int run = 0;
            for (int y = top; y <= bottom; y++) {
                run = dark(g, w, x, y) ? run + 1 : 0;
                if (run >= 2.5 * gap) return true;
            }
        }
        return false;
    }

    /** direction is the note's stem direction: +1 up, -1 down. */
    static Stem stem(byte[] gray, int w, int h, double cx, double cy, double gap, int direction) {
        if (!valid(gray, w, h, gap)
                || !Double.isFinite(cx)
                || !Double.isFinite(cy)
                || Math.abs(direction) != 1
                || cx < 0
                || cx >= w
                || cy < 0
                || cy >= h) return null;
        List<Stem> candidates = new ArrayList<>();
        int start = rounded(cy),
                first = rounded(cx + direction * .25 * gap),
                last = rounded(cx + direction * .95 * gap);
        if (start < 0 || start >= h) return null;
        int longest = 0;
        for (int x = first; direction * (last - x) >= 0; x += direction) {
            if (x < 1 || x >= w - 1) continue;
            boolean attached = false;
            for (int y = Math.max(0, start - rounded(.2 * gap));
                    y <= Math.min(h - 1, start + rounded(.2 * gap));
                    y++) if (dark(gray, w, x, y)) attached = true;
            if (!attached) continue;
            int misses = 0, end = start, ink = 0;
            for (int distance = 0; distance < rounded(9 * gap); distance++) {
                int y = start - direction * distance;
                if (y < 0 || y >= h) break;
                if (dark(gray, w, x, y)) {
                    ink++;
                    end = y;
                    misses = 0;
                } else if (++misses > Math.max(1, rounded(.15 * gap))) break;
            }
            int length = Math.abs(end - start);
            if (length < 2.5 * gap || ink / (double) (length + 1) < .85) continue;
            candidates.add(new Stem(x, start, end));
            longest = Math.max(longest, length);
        }
        final double minimum = longest - .15 * gap;
        List<Stem> shaft =
                candidates.stream()
                        .filter(s -> Math.abs(s.endY - s.headY) >= minimum)
                        .sorted(Comparator.comparingInt(Stem::x))
                        .toList();
        if (shaft.isEmpty()) return null;
        // Center the entire shaft so a one-column tie cannot change flank clearance.
        return shaft.get((shaft.size() - 1) / 2);
    }

    static List<Mark> find(byte[] gray, int w, int h, Stem left, Stem right, double gap) {
        return findWindow(gray, w, h, left, right, gap, false, java.util.Set.of());
    }

    private static List<Mark> findWindow(
            byte[] gray,
            int w,
            int h,
            Stem left,
            Stem right,
            double gap,
            boolean finiteHull,
            java.util.Set<Integer> endpointPixels) {
        if (left == null || right == null) return List.of();
        int distance = right.x - left.x;
        return findWindowAt(
                gray,
                w,
                h,
                left,
                right,
                gap,
                finiteHull,
                endpointPixels,
                rounded(left.x + distance * .30),
                rounded(left.x + distance * .75));
    }

    private static List<Mark> findWindowAt(
            byte[] gray,
            int w,
            int h,
            Stem left,
            Stem right,
            double gap,
            boolean finiteHull,
            java.util.Set<Integer> endpointPixels,
            int firstColumn,
            int lastColumn) {
        if (!valid(gray, w, h, gap) || left == null || right == null) return List.of();
        int distance = right.x - left.x;
        if (distance < 1.4 * gap || distance > 5 * gap) return List.of();
        int direction = left.endY > left.headY ? 1 : -1;
        for (Stem stem : List.of(left, right)) {
            if ((stem.endY - stem.headY) * direction < 2.5 * gap
                    || stem.x < 1
                    || stem.x >= w - 1
                    || Math.min(stem.headY, stem.endY) < 0
                    || Math.max(stem.headY, stem.endY) >= h) return List.of();
            int ink = 0, total = Math.abs(stem.endY - stem.headY) + 1;
            for (int y = Math.min(stem.headY, stem.endY); y <= Math.max(stem.headY, stem.endY); y++)
                if (dark(gray, w, stem.x - 1, y)
                        || dark(gray, w, stem.x, y)
                        || dark(gray, w, stem.x + 1, y)) ink++;
            if (ink / (double) total < .85) return List.of();
        }
        int x0 = firstColumn, x1 = lastColumn;
        if (x0 < left.x || x1 > right.x || x0 > x1) return List.of();
        double center = (x0 + x1) * .5;
        int first = Math.max(1, rounded(Math.min(left.endY, right.endY) - .4 * gap));
        int last =
                Math.min(
                        h - 2,
                        rounded(
                                (finiteHull
                                                ? Math.max(left.headY, right.headY)
                                                : Math.min(left.headY, right.headY))
                                        - .7 * gap));
        if (direction > 0) {
            first =
                    Math.max(
                            1,
                            rounded(
                                    (finiteHull
                                                    ? Math.min(left.headY, right.headY)
                                                    : Math.max(left.headY, right.headY))
                                            + .7 * gap));
            last = Math.min(h - 2, rounded(Math.max(left.endY, right.endY) + .4 * gap));
        }
        List<Mark> marks = new ArrayList<>();
        for (int sign : new int[] {-1, 1})
            for (int tick = 2; tick <= 13; tick++) {
                double slope = sign * tick * .05;
                List<Band> bands = new ArrayList<>();
                int runStart = -1;
                for (int y = first; y <= last + 1; y++) {
                    int ink = 0;
                    if (y <= last)
                        for (int x = x0; x <= x1; x++) {
                            int yy = rounded(y + slope * (x - center));
                            if (yy >= 0 && yy < h && dark(gray, w, x, yy)) ink++;
                        }
                    boolean covered = y <= last && ink / (double) (x1 - x0 + 1) >= .85;
                    if (covered && runStart < 0) runStart = y;
                    if (!covered && runStart >= 0) {
                        int thickness = y - runStart;
                        if (thickness >= (finiteHull ? 1 : .20 * gap) && thickness <= .75 * gap)
                            bands.add(new Band((runStart + y - 1) * .5, thickness));
                        runStart = -1;
                    }
                }
                for (int i = 0; i + 2 < bands.size(); i++) {
                    List<Band> three = bands.subList(i, i + 3);
                    double d1 = three.get(1).center - three.get(0).center,
                            d2 = three.get(2).center - three.get(1).center;
                    if (d1 < .5 * gap
                            || d1 > 1.1 * gap
                            || d2 < .5 * gap
                            || d2 > 1.1 * gap
                            || Math.abs(d1 - d2) > .2 * gap) continue;
                    boolean bounded = true;
                    for (int x :
                            new int[] {rounded(left.x + .18 * gap), rounded(right.x - .18 * gap)}) {
                        if (x < 0 || x >= w) {
                            bounded = false;
                            break;
                        }
                        for (Band band : three) {
                            int yy = rounded(band.center + slope * (x - center)), run = 0;
                            for (int y = Math.max(0, yy - rounded(.45 * gap));
                                    y <= Math.min(h - 1, yy + rounded(.45 * gap));
                                    y++) {
                                // The owned endpoint is retained, not mistaken for a connecting
                                // stroke.
                                // Only its complete original semantic pixels may interrupt flank
                                // ink.
                                run =
                                        dark(gray, w, x, y)
                                                        && !(finiteHull
                                                                && endpointPixels.contains(
                                                                        y * w + x))
                                                ? run + 1
                                                : 0;
                                if (run
                                        >= (finiteHull
                                                ? Math.min(band.thickness, .20 * gap)
                                                : .20 * gap)) bounded = false;
                            }
                        }
                    }
                    if (bounded) marks.add(new Mark(x0, x1, slope, three));
                }
            }
        return List.copyOf(marks);
    }
}
