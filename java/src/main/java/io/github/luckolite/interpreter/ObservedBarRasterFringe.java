// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** PRIVATE descriptive AA assignment, not raw ink or a head/removal classifier.
 * Discovery is produced here from an authenticated local copy, never caller Body records.
 */
final class ObservedBarRasterFringe {
    private ObservedBarRasterFringe() {}

    enum Role {
        OBSERVED_BAR_CORE,
        OBSERVED_CONTRASTED_FRINGE,
        UNKNOWN
    }

    /** Exact finite body discovered from this snapshot, not supplied by a caller. */
    static final class BodyIdentity {
        private final int[] pixels;
        private final Evidence evidence;
        private final int id;

        private BodyIdentity(Evidence e, int id, int[] p) {
            evidence = e;
            this.id = id;
            pixels = p;
        }

        int size() {
            return pixels.length;
        }

        int pixel(int i) {
            return pixels[i];
        }
    }

    static final class Evidence {
        private final SparseStaffContact.Snapshot snapshot;
        private final BitSet raw, fringe;
        private final int[] bodyAt;
        private final BitSet incompleteBodies, assessedBodies;
        private final List<BodyIdentity> bodies = new ArrayList<>();
        private final MaximalRawBarExtractor.Lane lane;
        final int discoveredBodies;

        private Evidence(
                SparseStaffContact.Snapshot p,
                BitSet r,
                BitSet f,
                int[] owners,
                BitSet incomplete,
                BitSet assessed,
                int n,
                MaximalRawBarExtractor.Lane l) {
            snapshot = p;
            raw = r;
            fringe = f;
            bodyAt = owners;
            incompleteBodies = incomplete;
            assessedBodies = assessed;
            discoveredBodies = n;
            lane = l;
        }

        int bodyCount() {
            return bodies.size();
        }

        BodyIdentity body(int i) {
            return bodies.get(i);
        }

        MaximalRawBarExtractor.Lane lane() {
            return lane;
        }

        boolean bodyComplete(BodyIdentity body) {
            return body != null
                    && body.evidence == this
                    && assessedBodies.get(body.id)
                    && !incompleteBodies.get(body.id);
        }

        Role role(
                SparseStaffContact.Snapshot p,
                BodyIdentity body,
                int x,
                int y,
                SparseStaffContact.Budget b) {
            if (body == null || body.evidence != this)
                throw new SparseStaffContact.Unknown("FOREIGN_BODY_IDENTITY");
            int at = p.index(x, y);
            Role answer = role(p, x, y, b);
            return bodyAt[at] == body.id ? answer : Role.UNKNOWN;
        }

        /** Diagnostic union only; a musical caller must query the exact matching BodyIdentity. */
        Role role(SparseStaffContact.Snapshot p, int x, int y, SparseStaffContact.Budget b) {
            b.work(1);
            if (p != snapshot) throw new SparseStaffContact.Unknown("STALE_FRINGE");
            int at = p.index(x, y);
            return raw.get(at)
                    ? Role.OBSERVED_BAR_CORE
                    : fringe.get(at) ? Role.OBSERVED_CONTRASTED_FRINGE : Role.UNKNOWN;
        }
    }

    record Result(Evidence evidence, String refusal) {
        boolean complete() {
            return evidence != null;
        }
    }

    /** Complete semantic-component ROLE assignment, not musical/removal permission.
     * Caller must additionally preserve actual endpoint/shaft/foreign-owner guards
     * and bind this exact head token and exact raw body to its musical mark.
     */
    static final class MemberAssignment {
        private final ExactComponentMembers.AcceptedProbeHead head;
        private final BodyIdentity body;
        final int coreMembers, fringeMembers;

        private MemberAssignment(
                ExactComponentMembers.AcceptedProbeHead h, BodyIdentity b, int core, int fringe) {
            head = h;
            body = b;
            coreMembers = core;
            fringeMembers = fringe;
        }

        ExactComponentMembers.AcceptedProbeHead headToken() {
            return head;
        }

        BodyIdentity bodyIdentity() {
            return body;
        }
    }

    static MemberAssignment assignSemanticComponent(
            SparseStaffContact.Snapshot p,
            SparseStaffContact.StaffIdentity staff,
            ExactComponentMembers.AcceptedProbeHead head,
            Evidence evidence,
            BodyIdentity body,
            SparseStaffContact.Budget b) {
        if (head == null
                || evidence == null
                || evidence.snapshot != p
                || body == null
                || body.evidence != evidence
                || head.measure != staff.measure()
                || head.staff != staff.staff()
                || !p.rawSha.equals(head.rawSha())) {
            return null;
        }
        if (evidence.incompleteBodies.get(body.id)) {
            return null;
        }
        int n = head.stats().area();
        b.work(2L * n);
        b.memory(4L * n + 160);
        int[] members = head.pixels(new ExactComponentMembers.Budget(n, 4L * n + 32));
        if (members == null) return null;
        int core = 0, fringe = 0;
        for (int at : members) {
            Role role = evidence.role(p, body, at % p.width, at / p.width, b);
            if (role == Role.UNKNOWN) {
                return null;
            }
            if (role == Role.OBSERVED_BAR_CORE) core++;
            else fringe++;
        }
        return core == 0 ? null : new MemberAssignment(head, body, core, fringe);
    }

    static final class Profile {
        final int x, top, height, phase;
        final int[] values;
        final boolean monotone;

        Profile(int x, int t, int h, int phase, int[] v, boolean m) {
            this.x = x;
            top = t;
            height = h;
            this.phase = phase;
            values = v;
            monotone = m;
        }
    }

    private record ProfileKey(int height, int phase, int centerOffset) {}

    private static ProfileKey profileKey(Profile p, MaximalRawBarExtractor.Body body, int mid) {
        // The profile values are already normalized to the observed body rows.
        // Keeping the one-pixel raster center offset in the equivalence key split
        // otherwise identical repeated phases into singleton classes.
        return new ProfileKey(p.height, p.phase, 0);
    }

    private static boolean otherBody(short[] owner, int bodyId, int at) {
        return owner[at] >= 0 && owner[at] != bodyId;
    }

    static int sideSupport(
            Profile p,
            int sw,
            int reach,
            BitSet exact,
            Set<Integer> staff,
            short[] owner,
            int bodyId,
            boolean upper) {
        int start = upper ? reach - 1 : reach + p.height,
                stop = upper ? -1 : p.values.length,
                step = upper ? -1 : 1;
        boolean coowned = false, staffSeen = false, peerSeen = false;
        int prior = -1;
        for (int k = start; k != stop; k += step) {
            int at = (p.top - reach + k) * sw + p.x;
            if (exact.get(at))
                return 0; // the body's own continuation cannot certify its missing paper edge
            if (staff.contains(at)) {
                coowned = true;
                staffSeen = true;
                prior = -1;
                continue;
            }
            if (otherBody(owner, bodyId, at)) {
                coowned = true;
                peerSeen = true;
                prior = -1;
                continue;
            }
            int value = p.values[k];
            if (value < 185) return 0;
            if (prior >= 0 && value < prior) return 0;
            prior = value;
            return 1;
        }
        return coowned && staffSeen && peerSeen ? 2 : 0;
    }

    private static int profileSupport(
            Profile p,
            int sw,
            int reach,
            BitSet exact,
            Set<Integer> staff,
            short[] owner,
            int bodyId) {
        return sideSupport(p, sw, reach, exact, staff, owner, bodyId, true)
                | (sideSupport(p, sw, reach, exact, staff, owner, bodyId, false) << 2);
    }

    private static boolean profileMonotone(
            Profile p,
            int sw,
            int reach,
            BitSet exact,
            Set<Integer> staff,
            short[] owner,
            int bodyId) {
        int support = profileSupport(p, sw, reach, exact, staff, owner, bodyId);
        return (support & 3) != 0 && (support & 12) != 0;
    }

    static boolean witnessedOpenBoundaries(
            boolean paperAbove, boolean paperBelow, boolean typedCoownership) {
        return paperAbove && paperBelow && typedCoownership;
    }

    private static boolean profilesCompatible(
            Profile a,
            Profile z,
            int sw,
            int reach,
            BitSet exact,
            Set<Integer> staff,
            short[] owner,
            int bodyId) {
        if (a.height != z.height || a.values.length != z.values.length) return false;
        for (int k = 0; k < a.values.length; k++)
            if (a.values[k] != z.values[k]) {
                int pa = (a.top - reach + k) * sw + a.x;
                int pz = (z.top - reach + k) * sw + z.x;
                if (!staff.contains(pa)
                        && !staff.contains(pz)
                        && !otherBody(owner, bodyId, pa)
                        && !otherBody(owner, bodyId, pz)) return false;
            }
        return true;
    }

    /** Full page identity is already authenticated by Snapshot.capture; only lane-sized work follows.
     * Lane is original endpoint geometry, not a searched semantic component crop.
     */
    static Result produce(
            SparseStaffContact.Snapshot p,
            MaximalRawBarExtractor.Lane lane,
            SparseStaffContact.Budget b) {
        return produce(p, lane, Set.of(), b);
    }

    /** Independently proved staff-core pixels may be excluded from raw body discovery;
     * unknown contacts remain in the plane and therefore make discovery abstain. */
    static Result produce(
            SparseStaffContact.Snapshot p,
            MaximalRawBarExtractor.Lane lane,
            Set<Integer> observedStaffPixels,
            SparseStaffContact.Budget b) {
        try {
            return new Result(prove(p, lane, observedStaffPixels, null, false, b), null);
        } catch (SparseStaffContact.Unknown e) {
            return new Result(null, e.getMessage());
        }
    }

    /** Reuse the caller's exact authenticated discovery; no second extractor may substitute a different body. */
    static Result produce(
            SparseStaffContact.Snapshot p,
            MaximalRawBarExtractor.Lane lane,
            Set<Integer> observedStaffPixels,
            MaximalRawBarExtractor.Discovery discovery,
            boolean joined,
            SparseStaffContact.Budget b) {
        if (discovery != null && FringeProfileDiagnostics.enabled())
            traceDiscovery(p, lane, observedStaffPixels, discovery, joined);
        try {
            Evidence evidence = prove(p, lane, observedStaffPixels, discovery, joined, b);
            StringBuilder status =
                    new StringBuilder("RESULT\t")
                            .append(lane)
                            .append("\t")
                            .append(discovery == null ? "null" : discovery.status())
                            .append("\t")
                            .append(discovery == null ? "null" : discovery.bodiesSha256())
                            .append("\t")
                            .append(evidence.bodyCount());
            for (int i = 0; i < evidence.bodyCount(); i++)
                status.append("\t")
                        .append(i)
                        .append(":complete=")
                        .append(evidence.bodyComplete(evidence.body(i)));
            FringeProfileDiagnostics.record(status.toString());
            return new Result(evidence, null);
        } catch (SparseStaffContact.Unknown e) {
            FringeProfileDiagnostics.record(
                    "REFUSAL\t"
                            + lane
                            + "\t"
                            + (discovery == null ? "null" : discovery.status())
                            + "\t"
                            + (discovery == null ? "null" : discovery.bodiesSha256())
                            + "\t"
                            + e.getMessage());
            return new Result(null, e.getMessage());
        }
    }

    private static void traceDiscovery(
            SparseStaffContact.Snapshot p,
            MaximalRawBarExtractor.Lane lane,
            Set<Integer> staff,
            MaximalRawBarExtractor.Discovery d,
            boolean joined) {
        StringBuilder line =
                new StringBuilder("CALL\t")
                        .append(lane)
                        .append("\tjoined=")
                        .append(joined)
                        .append("\tgray=")
                        .append(
                                d.sourceIdentity() == null
                                        ? "null"
                                        : d.sourceIdentity().graySha256())
                        .append("\tdim=")
                        .append(
                                d.sourceIdentity() == null
                                        ? "null"
                                        : d.sourceIdentity().width()
                                                + "x"
                                                + d.sourceIdentity().height())
                        .append("\tstaff=")
                        .append(
                                d.sourceIdentity() == null
                                        ? "null"
                                        : d.sourceIdentity().staffSha256())
                        .append("\toriginalBodies=")
                        .append(
                                d.sourceIdentity() == null
                                        ? "null"
                                        : d.sourceIdentity().originalBodiesSha256())
                        .append("\tselectedBodies=")
                        .append(
                                d.sourceIdentity() == null
                                        ? "null"
                                        : d.sourceIdentity().selectedBodiesSha256())
                        .append("\tset=")
                        .append(d.bodiesSha256())
                        .append("\tstatus=")
                        .append(d.status())
                        .append("\tterminal=")
                        .append(d.terminalStage())
                        .append("\tstaffPixels=")
                        .append(staff.size());
        for (int i = 0; i < d.bodies().size(); i++) {
            var b = d.bodies().get(i);
            line.append("\tB")
                    .append(i)
                    .append("{tick=")
                    .append(b.slopeTick())
                    .append(",x=")
                    .append(b.firstX())
                    .append("..")
                    .append(b.lastX())
                    .append(",y=")
                    .append(b.minY())
                    .append("..")
                    .append(b.maxY())
                    .append(",center=")
                    .append(b.centerAtLaneMid())
                    .append(",caps=")
                    .append(b.firstCapWidth())
                    .append('/')
                    .append(b.lastCapWidth())
                    .append(",maxWidth=")
                    .append(b.maxWidth())
                    .append(",unassigned=")
                    .append(b.unassignedContactPixels())
                    .append(",n=")
                    .append(b.rawPixels().size())
                    .append(",memberSha=")
                    .append(MaximalRawBarExtractor.bodiesSha256(List.of(b)))
                    .append(",ids=")
                    .append(b.rawPixels().size() <= 2048 ? b.rawPixels() : "OMITTED_OVER_2048")
                    .append('}');
        }
        FringeProfileDiagnostics.record(line.toString());
    }

    private static String profileTrace(
            SparseStaffContact.Snapshot p,
            MaximalRawBarExtractor.Lane lane,
            MaximalRawBarExtractor.Body body,
            int bodyId,
            List<Profile> profiles,
            int reach,
            BitSet exact,
            Set<Integer> staff,
            BitSet incomplete,
            BitSet assessed,
            boolean capPair,
            BitSet candidateCore,
            BitSet raw,
            BitSet fringe,
            int sw,
            short[] owner) {
        StringBuilder line =
                new StringBuilder("BODY\t")
                        .append(lane)
                        .append("\tid=")
                        .append(bodyId)
                        .append("\ttick=")
                        .append(body.slopeTick())
                        .append("\tx=")
                        .append(body.firstX() + p.scope.left())
                        .append("..")
                        .append(body.lastX() + p.scope.left())
                        .append("\ty=")
                        .append(body.minY() + p.scope.top())
                        .append("..")
                        .append(body.maxY() + p.scope.top())
                        .append("\tn=")
                        .append(body.rawPixels().size())
                        .append("\tcaps=")
                        .append(body.firstCapWidth())
                        .append('/')
                        .append(body.lastCapWidth())
                        .append("\twidth=")
                        .append(body.maxWidth())
                        .append("\tunassigned=")
                        .append(body.unassignedContactPixels())
                        .append("\tprofiles=")
                        .append(profiles.size())
                        .append("\treach=")
                        .append(reach)
                        .append("\tcapPair=")
                        .append(capPair)
                        .append("\tincomplete=")
                        .append(incomplete.get(bodyId))
                        .append("\tassessed=")
                        .append(assessed.get(bodyId))
                        .append("\tcoreCandidates=")
                        .append(candidateCore.cardinality())
                        .append("\trawUnion=")
                        .append(raw.cardinality())
                        .append("\tfringeUnion=")
                        .append(fringe.cardinality());
        for (int profileIndex = 0; profileIndex < profiles.size(); profileIndex++) {
            if (profiles.size() > 128
                    && profileIndex != 0
                    && profileIndex != profiles.size() / 2
                    && profileIndex != profiles.size() - 1) continue;
            Profile q = profiles.get(profileIndex);
            line.append("\tP{")
                    .append("x=")
                    .append(q.x + p.scope.left())
                    .append(",y=")
                    .append(q.top + p.scope.top())
                    .append("..")
                    .append(q.top + q.height - 1 + p.scope.top())
                    .append(",h=")
                    .append(q.height)
                    .append(",phase=")
                    .append(q.phase)
                    .append(",mono=")
                    .append(q.monotone)
                    .append(",samples=");
            for (int k = 0; k < q.values.length; k++) {
                if (q.values.length > 128
                        && k != 0
                        && k != q.values.length / 2
                        && k != q.values.length - 1) continue;
                int x = q.x, y = q.top - reach + k, at = y * sw + x, value = q.values[k];
                String role =
                        exact.get(at)
                                ? "RAW"
                                : staff.contains(at)
                                        ? "STAFF"
                                        : otherBody(owner, bodyId, at)
                                                ? "OTHER_BODY"
                                                : value >= 185
                                                        ? "PAPER"
                                                        : value == q.values[0]
                                                                ? "PAPER"
                                                                : value < q.values[0]
                                                                        ? "AA"
                                                                        : "FOREIGN";
                if (k > 0) line.append(',');
                line.append(y + p.scope.top()).append(':').append(value).append(':').append(role);
            }
            line.append('}');
        }
        return line.toString();
    }

    private static Evidence prove(
            SparseStaffContact.Snapshot p,
            MaximalRawBarExtractor.Lane lane,
            Set<Integer> observedStaffPixels,
            MaximalRawBarExtractor.Discovery supplied,
            boolean joined,
            SparseStaffContact.Budget b) {
        int sw = p.scope.width(), sh = p.scope.height();
        long area = (long) sw * sh;
        if (lane.left() <= p.scope.left()
                || lane.right() >= p.scope.right()
                || lane.top() <= p.scope.top()
                || lane.bottom() >= p.scope.bottom())
            throw new SparseStaffContact.Unknown("LANE_SCOPE");
        int rad = (int) Math.rint(.5 * lane.gap());
        if (lane.top() - rad < p.scope.top() || lane.bottom() + rad > p.scope.bottom())
            throw new SparseStaffContact.Unknown("COMPLETE_EXTRACTION_SCOPE");
        long cells =
                (long) (lane.bottom() - lane.top() + 1 + 2 * rad)
                        * (lane.right() - lane.left() + 1);
        long storage =
                Math.addExact(
                        Math.multiplyExact(cells, 2048L),
                        Math.multiplyExact(lane.right() - lane.left() + 1L, 512L));
        b.work(area);
        b.memory(area + storage + 256);
        byte[] local = new byte[(int) area];
        for (int y = p.scope.top(); y <= p.scope.bottom(); y++)
            for (int x = p.scope.left(); x <= p.scope.right(); x++)
                local[p.index(x, y)] = (byte) p.observedGray(x, y);
        b.work(observedStaffPixels.size());
        b.memory(32L * observedStaffPixels.size() + 64);
        Set<Integer> localStaff = new HashSet<>(Math.max(16, observedStaffPixels.size() * 2));
        for (int pixel : observedStaffPixels) {
            int x = pixel % p.width, y = pixel / p.width;
            if (x < p.scope.left()
                    || x > p.scope.right()
                    || y < p.scope.top()
                    || y > p.scope.bottom())
                throw new SparseStaffContact.Unknown("STAFF_OUTSIDE_SNAPSHOT");
            localStaff.add((y - p.scope.top()) * sw + x - p.scope.left());
        }
        var l =
                new MaximalRawBarExtractor.Lane(
                        lane.left() - p.scope.left(),
                        lane.right() - p.scope.left(),
                        lane.top() - p.scope.top(),
                        lane.bottom() - p.scope.top(),
                        lane.gap());
        MaximalRawBarExtractor.Discovery discovered;
        if (supplied == null) {
            discovered =
                    MaximalRawBarExtractor.discover(
                            local,
                            sw,
                            sh,
                            l,
                            Math.min(b.work, MaximalRawBarExtractor.MAX_PIXEL_WORK),
                            storage,
                            localStaff);
            b.work(discovered.pixelWork());
        } else {
            if (!supplied.authenticFor(
                    p.rawSha, p.width, p.height, lane, observedStaffPixels, joined))
                throw new SparseStaffContact.Unknown("DISCOVERY_IDENTITY");
            discovered = localizeAuthenticated(p, supplied, b);
        }
        if (discovered.status() != MaximalRawBarExtractor.Status.CANDIDATES
                || discovered.bodies().isEmpty())
            throw new SparseStaffContact.Unknown("RAW_DISCOVERY_" + discovered.status());
        // Reserve logical sparse profile/table/bitset storage and all profile comparisons
        // before materialization. Max64 body count is the unchanged extractor bound.
        b.work(discovered.bodies().size());
        long count = discovered.bodies().stream().mapToLong(v -> v.rawPixels().size()).sum();
        int lw = lane.right() - lane.left() + 1;
        long profileWork =
                32L * count
                        + discovered.bodies().size()
                                * ((long) lw * lw * (3L * lane.gap() + 8) + 20L * lw * lane.gap());
        b.work(profileWork + area + count);
        b.memory(
                (3L + discovered.bodies().size()) * ((area + 7) / 8)
                        + 6 * area
                        + discovered.bodies().size() * (32L * lw * lane.gap() + 256)
                        + 64L * count
                        + 1024);
        BitSet raw = new BitSet((int) area),
                fringe = new BitSet((int) area),
                ambiguous = new BitSet((int) area),
                incompleteBodies = new BitSet(),
                assessedBodies = new BitSet();
        short[] rawRole = new short[(int) area];
        Arrays.fill(rawRole, (short) -1);
        for (int bi = 0; bi < discovered.bodies().size(); bi++)
            for (int at : discovered.bodies().get(bi).rawPixels()) {
                if (at < 0 || at >= area || rawRole[at] >= 0)
                    throw new SparseStaffContact.Unknown("DUPLICATE_OR_OUTSIDE_ROLE_MEMBER");
                rawRole[at] = (short) bi;
            }
        boolean[] trustedContactBody = new boolean[discovered.bodies().size()];
        for (int bi = 0; bi < discovered.bodies().size(); bi++)
            trustedContactBody[bi] =
                    contactOwnershipComplete(
                            local,
                            sw,
                            sh,
                            (l.left() + l.right()) / 2,
                            lane.gap(),
                            discovered.bodies().get(bi),
                            localStaff);
        boolean jointPeerContacts = false;
        if (supplied != null
                && supplied.authenticFor(
                        p.rawSha, p.width, p.height, lane, observedStaffPixels, joined)) {
            AuthenticatedPeerContactProof.Result peerProof =
                    AuthenticatedPeerContactProof.classify(
                            local, sw, sh, lane.gap(), discovered.bodies(), localStaff, b);
            jointPeerContacts = peerProof.complete();
        }
        if (jointPeerContacts) Arrays.fill(trustedContactBody, true);
        short[] profileRole = rawRole.clone();
        for (int at = 0; at < profileRole.length; at++) {
            int owner = profileRole[at];
            if (owner >= 0 && !trustedContactBody[owner]) profileRole[at] = -1;
        }
        int[] bodyAt = new int[(int) area];
        Arrays.fill(bodyAt, -1);
        int bodyId = -1;
        List<int[]> bodyIdentities = new ArrayList<>();
        int mid = (l.left() + l.right()) / 2;
        boolean paperAbove = false, paperBelow = false, typedCoownership = false;
        for (var body : discovered.bodies()) {
            bodyId++;
            boolean completeOwnedBody = trustedContactBody[bodyId];
            int[] fullMembers = new int[body.rawPixels().size()];
            int mi = 0;
            for (int at : body.rawPixels()) {
                int gx = at % sw + p.scope.left(),
                        gy = at / sw + p.scope.top(),
                        global = p.index(gx, gy);
                // BodyIdentity is compared with production's full-page row-major member IDs;
                // Evidence.role separately uses snapshot-local indices through Snapshot.index.
                fullMembers[mi++] = gy * p.width + gx;
                if (completeOwnedBody && !localStaff.contains(global)) {
                    if (bodyAt[global] != -1 && bodyAt[global] != bodyId) ambiguous.set(global);
                    else bodyAt[global] = bodyId;
                }
            }
            // Canonical full-page membership order makes identity matching independent
            // of the extractor's column-major scan and the snapshot's local origin.
            Arrays.sort(fullMembers);
            bodyIdentities.add(fullMembers);
            if (!completeOwnedBody) {
                incompleteBodies.set(bodyId);
                continue;
            } // incomplete bodies never authorize even their raw-core pixels
            int bw = body.lastX() - body.firstX() + 1;
            long coreWork, coreBytes;
            try {
                coreWork =
                        Math.multiplyExact(
                                (long) body.rawPixels().size(), Math.max(1, body.maxWidth()));
                coreBytes =
                        Math.addExact(
                                Math.multiplyExact((long) body.rawPixels().size(), 4L),
                                Math.addExact(Math.multiplyExact((long) bw, 8L), 64L));
            } catch (ArithmeticException overflow) {
                throw new SparseStaffContact.Unknown("CORE_BOUND");
            }
            b.work(coreWork);
            b.memory(coreBytes);
            int[] lo = new int[bw], hi = new int[bw];
            Arrays.fill(lo, sh);
            Arrays.fill(hi, -1);
            int[] columnCount = new int[bw];
            BitSet exact = new BitSet((int) area);
            for (int at : body.rawPixels()) {
                int x = at % sw, y = at / sw, ix = x - body.firstX();
                if (ix < 0 || ix >= bw || (local[at] & 255) >= 185)
                    throw new SparseStaffContact.Unknown("EXTRACTOR_MEMBERS");
                exact.set(at);
                columnCount[ix]++;
                lo[ix] = Math.min(lo[ix], y);
                hi[ix] = Math.max(hi[ix], y);
            }
            // Raw body membership may include a narrow attached branch accepted by the
            // finite-body envelope. Core authority is only the modal transverse width,
            // centered on the measured body axis; excess pixels stay UNKNOWN.
            int[] widthFrequency = new int[Math.max(2, body.maxWidth() + 1)];
            for (int columnWidth : columnCount)
                if (columnWidth > 0 && columnWidth < widthFrequency.length)
                    widthFrequency[columnWidth]++;
            int coreWidth = 0, coreFrequency = 0;
            for (int width = 1; width < widthFrequency.length; width++)
                if (widthFrequency[width] > coreFrequency) {
                    coreWidth = width;
                    coreFrequency = widthFrequency[width];
                }
            int[][] rowsByColumn = new int[bw][];
            for (int i = 0; i < bw; i++) rowsByColumn[i] = new int[columnCount[i]];
            int[] columnAt = new int[bw];
            for (int at : body.rawPixels()) {
                int ix = at % sw - body.firstX();
                rowsByColumn[ix][columnAt[ix]++] = at / sw;
            }
            BitSet candidateCore = new BitSet((int) area);
            for (int ix = 0; ix < bw; ix++)
                if (columnCount[ix] == coreWidth) {
                    int[] ys = rowsByColumn[ix];
                    double center = body.centerAt(body.firstX() + ix, mid);
                    for (int y : ys) {
                        int rank = 0;
                        double distance = Math.abs(y - center);
                        for (int other : ys) {
                            double otherDistance = Math.abs(other - center);
                            if (otherDistance < distance
                                    || (otherDistance == distance && other < y)) rank++;
                        }
                        if (rank < coreWidth) {
                            int gx = body.firstX() + ix + p.scope.left(),
                                    gy = y + p.scope.top(),
                                    at = p.index(gx, gy);
                            candidateCore.set(at);
                        }
                    }
                }
            List<Profile> profiles = new ArrayList<>();
            int reach = body.maxWidth();
            for (int i = 0; i < bw; i++) {
                int x = body.firstX() + i;
                if (hi[i] < lo[i] || lo[i] - reach < 0 || hi[i] + reach >= sh) {
                    incompleteBodies.set(bodyId);
                    continue;
                }
                boolean bodyInterior = x - body.firstX() >= reach && body.lastX() - x >= reach;
                boolean full = true;
                for (int y = lo[i]; y <= hi[i]; y++)
                    if (!exact.get(y * sw + x)
                            && !localStaff.contains(y * sw + x)
                            && !otherBody(profileRole, bodyId, y * sw + x)) full = false;
                if (!full) {
                    incompleteBodies.set(bodyId);
                    continue;
                }
                int height = hi[i] - lo[i] + 1;
                int[] v = new int[height + 2 * reach];
                for (int y = 0; y < v.length; y++) v[y] = local[(lo[i] - reach + y) * sw + x] & 255;
                // A typed staff/peer pixel may physically co-own this cross-section. It is
                // omitted only from the bar's own profile order; unassigned dark ink still
                // breaks the complete paper-to-body boundary proof.
                Profile candidate =
                        new Profile(
                                x,
                                lo[i],
                                height,
                                Math.floorMod(body.slopeTick() * (x - mid), 20),
                                v,
                                false);
                int support =
                        profileSupport(
                                candidate, sw, reach, exact, localStaff, profileRole, bodyId);
                boolean monotone = (support & 3) != 0 && (support & 12) != 0;
                paperAbove |= (support & 1) != 0;
                paperBelow |= (support & 4) != 0;
                typedCoownership |= (support & 10) != 0;
                if (!monotone) {
                    incompleteBodies.set(bodyId);
                    continue;
                }
                profiles.add(new Profile(x, lo[i], height, candidate.phase, v, true));
            }
            // Assess full physical endpoints; extractor cap widths locate ends but do
            // not classify grayscale. Matching observed monotone end profiles form a
            // narrow cap certificate, independent of interior width/phase classes.
            Map<Integer, Profile> profileAtX = new HashMap<>();
            for (Profile q : profiles) profileAtX.put(q.x, q);
            Profile leftCap = profileAtX.get(body.firstX()),
                    rightCap = profileAtX.get(body.lastX());
            boolean capPair =
                    body.firstCapWidth() > 0
                            && body.lastCapWidth() > 0
                            && leftCap != null
                            && rightCap != null
                            && profileMonotone(
                                    leftCap, sw, reach, exact, localStaff, profileRole, bodyId)
                            && profileMonotone(
                                    rightCap, sw, reach, exact, localStaff, profileRole, bodyId);
            if (profiles.size() != bw || !capPair) incompleteBodies.set(bodyId);
            // Group cross-sections by the existing height/slope phase.
            java.util.Map<ProfileKey, int[]> profileBounds = new java.util.HashMap<>();
            for (Profile q : profiles) {
                ProfileKey key = profileKey(q, body, mid);
                int[] bounds = profileBounds.get(key);
                if (bounds == null) profileBounds.put(key, new int[] {q.x, q.x});
                else {
                    bounds[0] = Math.min(bounds[0], q.x);
                    bounds[1] = Math.max(bounds[1], q.x);
                }
            }
            java.util.Map<ProfileKey, Integer> profileCounts = new java.util.HashMap<>();
            for (Profile q : profiles)
                profileCounts.merge(profileKey(q, body, mid), 1, Integer::sum);
            for (Profile q : profiles) {
                boolean left = false, right = false, unique = true, interiorConflict = false;
                boolean bodyInterior = q.x - body.firstX() >= reach && body.lastX() - q.x >= reach;
                ProfileKey qKey = profileKey(q, body, mid);
                // A fully observed monotone section is positive evidence on its own. Peer
                // profiles still veto conflicting evidence, but repetition is not required
                // to certify a section whose entire transverse profile and both paper
                // boundaries were independently measured.
                if (!profileMonotone(q, sw, reach, exact, localStaff, profileRole, bodyId)
                        && bodyInterior) {
                    // Repetition does not establish that a disconnected bright lobe belongs
                    // to this body. Keep the whole body UNKNOWN until every interior profile
                    // is independently monotone or has another typed ownership proof.
                    incompleteBodies.set(bodyId);
                }
                if (!q.monotone && q.x - body.firstX() >= reach && body.lastX() - q.x >= reach) {
                    // Interior nonmatching dark sections make complete-body role UNKNOWN.
                    boolean unownedDark = false;
                    for (int k = 0; k < q.values.length; k++) {
                        int at = (q.top - reach + k) * sw + q.x;
                        if (q.values[k] < 185 && !exact.get(at) && !localStaff.contains(at)) {
                            unownedDark = true;
                            break;
                        }
                    }
                    boolean unownedForeground = false;
                    if (!unownedDark)
                        for (Profile peer : profiles)
                            if (peer != q
                                    && profileMonotone(
                                            peer, sw, reach, exact, localStaff, profileRole, bodyId)
                                    && qKey.equals(profileKey(peer, body, mid))) {
                                int[] bounds = profileBounds.get(profileKey(peer, body, mid));
                                boolean peerInterior =
                                        bounds != null
                                                && peer.x - bounds[0] >= reach
                                                && bounds[1] - peer.x >= reach;
                                if (peerInterior)
                                    for (int k = 0; k < q.values.length; k++) {
                                        int at = (q.top - reach + k) * sw + q.x;
                                        if (q.values[k] < q.values[0]
                                                && q.values[k] != peer.values[k]
                                                && !exact.get(at)
                                                && !localStaff.contains(at)) {
                                            unownedForeground = true;
                                            break;
                                        }
                                    }
                                if (unownedForeground) break;
                            }
                    if (unownedDark || unownedForeground) incompleteBodies.set(bodyId);
                }
                for (Profile other : profiles)
                    if (qKey.equals(profileKey(other, body, mid))) {
                        // ALL corresponding observed columns must agree, not a favorable witness
                        // selection.
                        if (!profilesCompatible(
                                q, other, sw, reach, exact, localStaff, profileRole, bodyId)) {
                            unique = false;
                            int[] bounds = profileBounds.get(profileKey(other, body, mid));
                            boolean otherInterior =
                                    other.x - body.firstX() >= reach
                                            && body.lastX() - other.x >= reach;
                            boolean currentInterior =
                                    q.x - body.firstX() >= reach && body.lastX() - q.x >= reach;
                            if (currentInterior && otherInterior) interiorConflict = true;
                        }
                        if (other.x <= q.x - reach) left = true;
                        if (other.x >= q.x + reach) right = true;
                    }
                // Any conflicting pair of complete interior samples in the same normalized
                // phase makes the whole body unresolved. Requiring each side to have a
                // second peer allowed an isolated conflicting sample to be skipped while
                // unrelated sections still authorized whole-component removal.
                if (interiorConflict) incompleteBodies.set(bodyId);
                if (!profileMonotone(q, sw, reach, exact, localStaff, profileRole, bodyId)
                        || !unique
                        || !left
                        || !right) continue;

                for (int y = lo[q.x - body.firstX()]; y <= hi[q.x - body.firstX()]; y++) {
                    int at = y * sw + q.x;
                    if (candidateCore.get(at) && !localStaff.contains(at)) raw.set(at);
                }
                int paper = q.values[0];
                for (int k = 0; k < q.values.length; k++) {
                    int at = (q.top - reach + k) * sw + q.x, value = q.values[k];
                    if (value < 185) {
                        if (!exact.get(at)
                                && !localStaff.contains(at)
                                && !otherBody(profileRole, bodyId, at))
                            throw new SparseStaffContact.Unknown("PROFILE_FOREIGN_DARK");
                        // Dark samples outside the independently measured core are not promoted
                        // by a matching profile; attached branches remain unowned.
                        continue;
                    }
                    if (value >= paper)
                        continue; // exact paper/unknown lobe is never physical AA evidence
                    if (bodyAt[at] != -1 && bodyAt[at] != bodyId) ambiguous.set(at);
                    else {
                        fringe.set(at);
                        bodyAt[at] = bodyId;
                    }
                }
            }
            if (!incompleteBodies.get(bodyId)) {
                // A fully assessed exact finite body owns its independently derived modal-width raw
                // core.
                // Per-phase profile peers qualify optical fringe, not just a sparse subset of raw
                // ink.
                raw.or(candidateCore);
                assessedBodies.set(bodyId);
            }
            if (FringeProfileDiagnostics.enabled())
                FringeProfileDiagnostics.record(
                        profileTrace(
                                p,
                                lane,
                                body,
                                bodyId,
                                profiles,
                                reach,
                                exact,
                                localStaff,
                                incompleteBodies,
                                assessedBodies,
                                capPair,
                                candidateCore,
                                raw,
                                fringe,
                                sw,
                                rawRole));
        }
        // Co-owned sides can close an individual cross-section, but cannot bootstrap
        // a closed family with no independent exterior paper witness on both sides.
        // A joined discovery may establish the co-owned role directly when every
        // complete raw body shares at least one exact member with the independently
        // authenticated staff set. This role is independent of boundary paper; the
        // existing above/below witnesses remain mandatory and cannot be bootstrapped.
        boolean exactStaffCoownership =
                supplied != null && joined && !discovered.bodies().isEmpty();
        if (exactStaffCoownership)
            for (var body : discovered.bodies()) {
                boolean bodySharesStaff = false;
                for (int at : body.rawPixels())
                    if (localStaff.contains(at)) {
                        bodySharesStaff = true;
                        break;
                    }
                if (!bodySharesStaff) {
                    exactStaffCoownership = false;
                    break;
                }
            }
        typedCoownership |= exactStaffCoownership;
        if (!witnessedOpenBoundaries(paperAbove, paperBelow, typedCoownership)) {
            incompleteBodies.set(0, discovered.bodies().size());
            assessedBodies.clear();
        }
        raw.andNot(ambiguous);
        fringe.andNot(ambiguous);
        fringe.andNot(raw);
        Evidence evidence =
                new Evidence(
                        p,
                        raw,
                        fringe,
                        bodyAt,
                        incompleteBodies,
                        assessedBodies,
                        discovered.bodies().size(),
                        lane);
        for (int i = 0; i < bodyIdentities.size(); i++)
            evidence.bodies.add(new BodyIdentity(evidence, i, bodyIdentities.get(i)));
        return evidence;
    }

    static boolean contactOwnershipComplete(
            byte[] gray,
            int width,
            int height,
            int laneMid,
            int gap,
            MaximalRawBarExtractor.Body body,
            Set<Integer> authenticatedStaff) {
        if (body == null) return false;
        if (body.unassignedContactPixels() == 0) return true;
        FiniteContactRoles.Result roles =
                FiniteContactRoles.classify(
                        gray, width, height, laneMid, gap, body, authenticatedStaff);
        return roles.complete()
                && roles.staffEdge() + roles.capExtension() + roles.otherStroke() > 0;
    }

    private static MaximalRawBarExtractor.Discovery localizeAuthenticated(
            SparseStaffContact.Snapshot p,
            MaximalRawBarExtractor.Discovery source,
            SparseStaffContact.Budget b) {
        int sw = p.scope.width(), sh = p.scope.height();
        long members = 0;
        for (var body : source.bodies()) {
            if (body == null
                    || body.firstX() < p.scope.left()
                    || body.lastX() > p.scope.right()
                    || body.firstX() > body.lastX()
                    || body.minY() < p.scope.top()
                    || body.maxY() > p.scope.bottom()
                    || body.minY() > body.maxY()
                    || body.rawPixels().isEmpty()
                    || body.rawPixels().size() > 500_000)
                throw new SparseStaffContact.Unknown("DISCOVERY_BODY_DOMAIN");
            members = Math.addExact(members, body.rawPixels().size());
            if (members > 500_000) throw new SparseStaffContact.Unknown("DISCOVERY_MEMBER_CAP");
        }
        long area = (long) sw * sh;
        if (area > Integer.MAX_VALUE) throw new SparseStaffContact.Unknown("DISCOVERY_SCOPE_CAP");
        b.work(Math.addExact(members, source.bodies().size()));
        b.memory(
                Math.addExact(
                        (area + 7) / 8,
                        Math.addExact(64L * members, 256L * source.bodies().size() + 512)));
        BitSet seen = new BitSet((int) area);
        List<MaximalRawBarExtractor.Body> translated = new ArrayList<>();
        for (var body : source.bodies()) {
            int bw = body.length();
            int[] columnCount = new int[bw];
            long minY = Long.MAX_VALUE, maxY = Long.MIN_VALUE;
            List<Integer> pixels = new ArrayList<>(body.rawPixels().size());
            for (int global : body.rawPixels()) {
                if (global < 0 || global >= (long) p.width * p.height)
                    throw new SparseStaffContact.Unknown("DISCOVERY_PIXEL_DOMAIN");
                int gx = global % p.width, gy = global / p.width;
                if (gx < body.firstX()
                        || gx > body.lastX()
                        || gx < p.scope.left()
                        || gx > p.scope.right()
                        || gy < p.scope.top()
                        || gy > p.scope.bottom()
                        || !p.dark(gx, gy))
                    throw new SparseStaffContact.Unknown("DISCOVERY_PIXEL_MISMATCH");
                int local = p.index(gx, gy);
                if (seen.get(local))
                    throw new SparseStaffContact.Unknown("DISCOVERY_DUPLICATE_MEMBER");
                seen.set(local);
                pixels.add((gy - p.scope.top()) * sw + gx - p.scope.left());
                columnCount[gx - body.firstX()]++;
                minY = Math.min(minY, gy);
                maxY = Math.max(maxY, gy);
            }
            for (int count : columnCount)
                if (count == 0) throw new SparseStaffContact.Unknown("DISCOVERY_COLUMN_GAP");
            if (minY != body.minY()
                    || maxY != body.maxY()
                    || body.firstCapWidth() < 1
                    || body.lastCapWidth() < 1
                    || body.maxWidth() < 1)
                throw new SparseStaffContact.Unknown("DISCOVERY_METADATA_MISMATCH");
            translated.add(
                    new MaximalRawBarExtractor.Body(
                            body.slopeTick(),
                            body.firstX() - p.scope.left(),
                            body.lastX() - p.scope.left(),
                            body.centerAtLaneMid() - p.scope.top(),
                            pixels,
                            body.firstCapWidth(),
                            body.lastCapWidth(),
                            body.maxWidth(),
                            body.unassignedContactPixels(),
                            (int) minY - p.scope.top(),
                            (int) maxY - p.scope.top()));
        }
        return new MaximalRawBarExtractor.Discovery(
                source.status(), translated, source.pixelWork(), source.terminalStage());
    }
}
