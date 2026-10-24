// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** PRIVATE raw-measured staff-contact producer, never an eraser or musical head classifier. */
final class RawMeasuredStaffTracks {
    record Shaft(int left, int right, int top, int bottom) {}

    /** Identity-bound EXCLUSION from fitting, never positive physical ink authority.
     * Exact semantic members may overhang onto AA/paper. Every one stays excluded;
     * neither darkness nor absence of darkness upgrades it to body or staff ownership.
     * Shafts still require independently complete raw ink. Bars are NOT inputs.
     */
    static final class OwnerFootprint {
        private final SparseStaffContact.Snapshot plane;
        private final SparseStaffContact.StaffIdentity staff;
        private final BitSet pixels;
        final int left, right;

        private OwnerFootprint(
                SparseStaffContact.Snapshot p,
                SparseStaffContact.StaffIdentity s,
                BitSet b,
                int l,
                int r) {
            plane = p;
            staff = s;
            pixels = b;
            left = l;
            right = r;
        }

        static OwnerFootprint fromCapturedHeadsAndShafts(
                SparseStaffContact.Snapshot p,
                SparseStaffContact.StaffIdentity staff,
                List<ExactComponentMembers.AcceptedProbeHead> heads,
                List<Shaft> shafts,
                SparseStaffContact.Budget budget) {
            int n = Math.multiplyExact(p.scope.width(), p.scope.height());
            budget.memory(2L * ((n + 7) / 8) + 1024);
            budget.work(n);
            if (heads.isEmpty() || heads.size() > 128 || shafts.isEmpty() || shafts.size() > 128)
                throw new SparseStaffContact.Unknown("ENDPOINT_DOMAIN");
            BitSet owned = new BitSet(n), shaftBits = new BitSet(n);
            int l = p.width, r = -1;
            Set<ExactComponentMembers.AcceptedProbeHead> ids =
                    Collections.newSetFromMap(new IdentityHashMap<>());
            String labelSha = null;
            List<int[]> headPixels = new ArrayList<>();
            budget.memory(32L * heads.size() + 64);
            for (var h : heads) {
                if (h == null
                        || !ids.add(h)
                        || h.measure != staff.measure()
                        || h.staff != staff.staff()
                        || !p.rawSha.equals(h.rawSha()))
                    throw new SparseStaffContact.Unknown("HEAD_IDENTITY");
                if (labelSha != null && !labelSha.equals(h.labelSha()))
                    throw new SparseStaffContact.Unknown("LABEL_IDENTITY");
                labelSha = h.labelSha();
                int area = h.stats().area();
                budget.work(area);
                budget.memory(4L * area + 64);
                var expansion = new ExactComponentMembers.Budget(area, 4L * area + 32);
                int[] sites = h.pixels(expansion);
                if (sites == null) throw new SparseStaffContact.Unknown("MISSING_MEMBERS");
                headPixels.add(sites);
                for (int z : sites) {
                    int x = z % p.width, y = z / p.width, i = p.index(x, y);
                    if (owned.get(i)) throw new SparseStaffContact.Unknown("HEAD_DUPLICATE");
                    owned.set(i);
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                }
            }
            int[] membership = new int[heads.size()];
            budget.memory(4L * heads.size() + 32);
            for (Shaft s : shafts) {
                if (s.left > s.right
                        || s.right - s.left > 2
                        || s.bottom - s.top < 2.5 * staff.gap()
                        || s.left < p.scope.left()
                        || s.right > p.scope.right()
                        || s.top < p.scope.top()
                        || s.bottom > p.scope.bottom())
                    throw new SparseStaffContact.Unknown("SHAFT_DOMAIN");
                long area = (long) (s.right - s.left + 1) * (s.bottom - s.top + 1);
                budget.work(area + heads.size());
                for (int y = s.top; y <= s.bottom; y++)
                    for (int x = s.left; x <= s.right; x++) {
                        int i = p.index(x, y);
                        if (!p.dark(x, y) || shaftBits.get(i))
                            throw new SparseStaffContact.Unknown("SHAFT_RAW_OR_DUPLICATE");
                        shaftBits.set(i);
                        owned.set(i);
                    }
                boolean touches = false;
                for (int j = 0; j < headPixels.size(); j++) {
                    boolean hit = false;
                    budget.work(headPixels.get(j).length);
                    for (int z : headPixels.get(j)) {
                        int x = z % p.width, y = z / p.width;
                        // A genuine shaft may meet a notehead along the adjacent edge while
                        // remaining a separate label component. Require exact member pixels
                        // within one raw pixel of the fully observed shaft span.
                        if (x >= s.left - 1
                                && x <= s.right + 1
                                && y >= s.top - 1
                                && y <= s.bottom + 1) hit = true;
                    }
                    if (hit) {
                        membership[j]++;
                        touches = true;
                    }
                }
                if (!touches) throw new SparseStaffContact.Unknown("FOREIGN_SHAFT");
                l = Math.min(l, s.left);
                r = Math.max(r, s.right);
            }
            for (int m : membership)
                if (m != 1) throw new SparseStaffContact.Unknown("UNOWNED_OR_AMBIGUOUS_HEAD");
            return new OwnerFootprint(p, staff, owned, l, r);
        }

        /** Exclusion is NOT evidence of ink, a head, a staff rule or safe deletion. */
        boolean excludesFromFit(
                SparseStaffContact.Snapshot p, int x, int y, SparseStaffContact.Budget b) {
            b.work(1);
            if (p != plane) throw new SparseStaffContact.Unknown("STALE_OWNER");
            return pixels.get(p.index(x, y));
        }
    }

    enum SemanticRole {
        OBSERVED_STAFF_CORE,
        UNASSIGNED_RAW_DARK,
        UNASSIGNED_NONDARK
    }

    /** Complete immutable membership partition. No removal/safe-body boolean exists.
     * A maximal raw-body extractor must independently account for UNASSIGNED_RAW_DARK;
     * nondark semantic members remain UNKNOWN, not forgiven as AA by this producer.
     */
    static final class MemberPartition {
        private final int[] ids;
        private final SemanticRole[] roles;

        private MemberPartition(int[] ids, SemanticRole[] roles) {
            this.ids = ids;
            this.roles = roles;
        }

        int size() {
            return ids.length;
        }

        int pixel(int i) {
            return ids[i];
        }

        SemanticRole role(int i) {
            return roles[i];
        }
    }

    static MemberPartition partitionSemanticMembers(
            SparseStaffContact.Snapshot p,
            SparseStaffContact.StaffIdentity staff,
            ExactComponentMembers.AcceptedProbeHead head,
            Evidence evidence,
            SparseStaffContact.Budget b) {
        if (head == null
                || evidence == null
                || evidence.plane != p
                || !evidence.staff.equals(staff)
                || head.measure != staff.measure()
                || head.staff != staff.staff()
                || !p.rawSha.equals(head.rawSha()))
            throw new SparseStaffContact.Unknown("PARTITION_IDENTITY");
        int n = head.stats().area();
        b.work(3L * n);
        b.memory(12L * n + 96);
        int[] ids = head.pixels(new ExactComponentMembers.Budget(n, 4L * n + 32));
        if (ids == null) throw new SparseStaffContact.Unknown("MISSING_MEMBERS");
        SemanticRole[] roles = new SemanticRole[n];
        for (int i = 0; i < n; i++) {
            int x = ids[i] % p.width, y = ids[i] / p.width;
            roles[i] =
                    evidence.ownsObservedNonownerPixel(p, x, y, b)
                            ? SemanticRole.OBSERVED_STAFF_CORE
                            : p.dark(x, y)
                                    ? SemanticRole.UNASSIGNED_RAW_DARK
                                    : SemanticRole.UNASSIGNED_NONDARK;
        }
        return new MemberPartition(ids, roles);
    }

    static final class Evidence {
        private final SparseStaffContact.Snapshot plane;
        final SparseStaffContact.StaffIdentity staff;
        private final int[][] first, last;
        private final BitSet observed;
        private final boolean[][] occluded;
        final int unassignedRawPixels;
        final double observedSlope, observedPhase;
        private final int ownerLeft, ownerRight;

        private Evidence(
                SparseStaffContact.Snapshot p,
                SparseStaffContact.StaffIdentity s,
                int[][] a,
                int[][] b,
                boolean[][] c,
                BitSet o,
                double slope,
                double phase,
                int unknown,
                int ownerLeft,
                int ownerRight) {
            this.ownerLeft = ownerLeft;
            this.ownerRight = ownerRight;
            unassignedRawPixels = unknown;
            plane = p;
            staff = s;
            first = a;
            last = b;
            occluded = c;
            observed = o;
            observedSlope = slope;
            observedPhase = phase;
        }

        boolean authenticForOpticalReference(
                SparseStaffContact.Snapshot p, SparseStaffContact.StaffIdentity s) {
            return plane == p && staff.equals(s);
        }

        boolean opticalWitnessFrameAt(SparseStaffContact.Snapshot p, int fullX, int[] frame) {
            if (p != plane || frame == null || frame.length != 10) return false;
            int x = fullX - plane.scope.left();
            if (x < 0 || x >= plane.scope.width() || !(fullX < ownerLeft || fullX > ownerRight))
                return false;
            for (int r = 0; r < 5; r++)
                if (occluded[r][x] || first[r][x] != frame[2 * r] || last[r][x] != frame[2 * r + 1])
                    return false;
            return true;
        }

        int opticalOwnerLeft() {
            return ownerLeft;
        }

        int opticalOwnerRight() {
            return ownerRight;
        }

        /** Independently observed straight staff stripe, for foreground co-membership ONLY.
         * Existing core ownership remains unchanged. Literal identical exterior edges
         * and five complete rails on both sides are mandatory. No geometric fill.
         */
        Set<Integer> foregroundCarrier(
                SparseStaffContact.Snapshot p,
                int left,
                int top,
                int right,
                int bottom,
                SparseStaffContact.Budget b) {
            if (p != plane) throw new SparseStaffContact.Unknown("STALE_CARRIER");
            int w = p.scope.width(), witness = (int) Math.ceil(2 * staff.gap());
            // A foreign exterior junction is UNKNOWN, not an authority source and not
            // a veto on a separate complete literal witness. Require the SAME ten edges
            // for a full two-gap consecutive witness on both sides, uniquely.
            b.work(400L * w);
            b.memory(256L * w + 1024);
            List<int[]> leftFrames = new ArrayList<>(), rightFrames = new ArrayList<>();
            int[] previous = null, tuple = new int[10];
            int run = 0, previousSide = -1;
            for (int x = p.scope.left(); x <= p.scope.right(); x++) {
                int side = x < ownerLeft ? 0 : x > ownerRight ? 1 : -1, ix = x - p.scope.left();
                boolean complete = side >= 0;
                for (int r = 0; r < 5; r++) {
                    if (first[r][ix] < 0 || occluded[r][ix]) complete = false;
                    tuple[2 * r] = first[r][ix];
                    tuple[2 * r + 1] = last[r][ix];
                }
                if (!complete) {
                    run = 0;
                    previous = null;
                    previousSide = -1;
                    continue;
                }
                if (previousSide == side && Arrays.equals(previous, tuple)) run++;
                else {
                    previous = tuple.clone();
                    run = 1;
                    previousSide = side;
                }
                if (run == witness) {
                    List<int[]> frames = side == 0 ? leftFrames : rightFrames;
                    boolean found = false;
                    for (int[] f : frames) if (Arrays.equals(f, tuple)) found = true;
                    if (!found) {
                        if (frames.size() >= 16) return null;
                        frames.add(tuple.clone());
                    }
                }
            }
            int[] chosen = null;
            for (int[] l : leftFrames)
                for (int[] r : rightFrames)
                    if (Arrays.equals(l, r)) {
                        if (chosen != null && !Arrays.equals(chosen, l)) return null;
                        chosen = l;
                    }
            if (chosen == null) return null;
            int[] lo = new int[5], hi = new int[5];
            for (int r = 0; r < 5; r++) {
                lo[r] = chosen[2 * r];
                hi[r] = chosen[2 * r + 1];
            }
            long size = 0;
            for (int r = 0; r < 5; r++) size += (long) w * (hi[r] - lo[r] + 1);
            b.memory(Math.addExact(512, Math.multiplyExact(size, 64L)));
            b.work(size);
            Set<Integer> result = new HashSet<>();
            // Scan the WHOLE witnessed corridor, including the owner span, against raw
            // ink. A missing pixel refuses; an owner is never used to excuse missing ink.
            for (int r = 0; r < 5; r++)
                for (int x = p.scope.left(); x <= p.scope.right(); x++)
                    for (int y = lo[r]; y <= hi[r]; y++) {
                        if (!p.dark(x, y)) return null;
                        if (x >= left && x <= right && y >= top && y <= bottom)
                            result.add(y * p.width + x);
                    }
            return Set.copyOf(result);
        }

        /** No center is filled in for owner-occluded columns. */
        OptionalDouble observedCenter(int rail, int fullX, SparseStaffContact.Budget b) {
            b.work(1);
            int x = fullX - plane.scope.left();
            if (rail < 0
                    || rail >= 5
                    || x < 0
                    || x >= plane.scope.width()
                    || occluded[rail][x]
                    || first[rail][x] < 0) return OptionalDouble.empty();
            return OptionalDouble.of((first[rail][x] + last[rail][x]) * .5);
        }

        boolean ownsObservedNonownerPixel(
                SparseStaffContact.Snapshot p, int x, int y, SparseStaffContact.Budget b) {
            b.work(1);
            if (p != plane) throw new SparseStaffContact.Unknown("STALE_EVIDENCE");
            return observed.get(p.index(x, y));
        }
    }

    record Result(Evidence evidence, String refusal) {
        boolean complete() {
            return evidence != null;
        }
    }

    static Result produce(
            SparseStaffContact.Snapshot p,
            SparseStaffContact.StaffIdentity staff,
            OwnerFootprint owner,
            SparseStaffContact.Budget budget) {
        try {
            return new Result(prove(p, staff, owner, budget), null);
        } catch (SparseStaffContact.Unknown e) {
            return new Result(null, e.getMessage());
        }
    }

    private static Evidence prove(
            SparseStaffContact.Snapshot p,
            SparseStaffContact.StaffIdentity staff,
            OwnerFootprint owner,
            SparseStaffContact.Budget budget) {
        if (owner == null || owner.plane != p || !owner.staff.equals(staff))
            throw new SparseStaffContact.Unknown("OWNER_IDENTITY");
        int w = p.scope.width(), h = p.scope.height();
        long area = (long) w * h;
        // Fixed O(scopeArea+5*width*height) scans plus bounded candidate-run storage.
        // Debit the worst-case run table before allocating or inspecting profiles.
        int runCapacity = (int) ((Math.ceil(staff.gap() + 2) + 1) / 2);
        long columns, runSlots, runBytes;
        try {
            columns = Math.multiplyExact(5L, w);
            runSlots = Math.multiplyExact(columns, runCapacity);
            runBytes =
                    Math.addExact(
                            Math.multiplyExact(8L, runSlots), Math.multiplyExact(4L, columns));
        } catch (ArithmeticException overflow) {
            throw new SparseStaffContact.Unknown("PROFILE_STORAGE_OVERFLOW");
        }
        budget.work(20 * area + 200L * w + 5L * w * runCapacity + 1024);
        budget.memory(Math.addExact(3 * ((area + 7) / 8) + 80L * w + 4096, runBytes));
        int witness = (int) Math.ceil(2 * staff.gap());
        if (owner.left - p.scope.left() < witness || p.scope.right() - owner.right < witness)
            throw new SparseStaffContact.Unknown("SYMMETRIC_WITNESS");
        int maxThickness = (int) Math.ceil(.35 * staff.gap()); // existing CompleteStaffRuleBeamBand
        // complete-profile cap
        int[][] first = new int[5][w], last = new int[5][w];
        boolean[][] occluded = new boolean[5][w];
        boolean[][] uncertain = new boolean[5][w];
        BitSet observed = new BitSet((int) area);
        int[] candidateFirst = new int[(int) runSlots],
                candidateLast = new int[(int) runSlots],
                candidateCount = new int[(int) columns];
        for (int r = 0; r < 5; r++) {
            Arrays.fill(first[r], -1);
            Arrays.fill(last[r], -1);
        }
        // Nearest-rail domain: less than half a gap prevents role-index swapping. No threshold
        // search.
        for (int x = p.scope.left(); x <= p.scope.right(); x++)
            for (int r = 0; r < 5; r++) {
                int ix = x - p.scope.left();
                double seed = staff.top() + r * staff.gap();
                int top = (int) Math.floor(seed - staff.gap() * .5) + 1,
                        bottom = (int) Math.ceil(seed + staff.gap() * .5) - 1;
                if (top <= p.scope.top() || bottom >= p.scope.bottom())
                    throw new SparseStaffContact.Unknown("FRAME_OUTSIDE_SCOPE");
                int column = r * w + ix, base = column * runCapacity, count = 0, active = -1;
                for (int y = top; y <= bottom; y++) {
                    int i = p.index(x, y);
                    boolean ink = p.dark(x, y) && !owner.pixels.get(i);
                    if (ink) {
                        if (active < 0) {
                            if (count >= runCapacity)
                                throw new SparseStaffContact.Unknown("PROFILE_RUN_CAPACITY");
                            active = count++;
                            candidateFirst[base + active] = y;
                        }
                        candidateLast[base + active] = y;
                    } else active = -1;
                }
                candidateCount[column] = count;
                if (count == 0) {
                    uncertain[r][ix] = true;
                    continue;
                }
                // Multiple raw runs are retained as candidates, not discarded. External
                // witness columns remain strict; only interior columns can later select a
                // unique run that agrees with the independently fitted five-rule frame.
                if (count > 1) {
                    uncertain[r][ix] = true;
                    continue;
                }
                int a = candidateFirst[base], b = candidateLast[base];
                if (b - a + 1 > maxThickness) {
                    uncertain[r][ix] = true;
                    continue;
                }
                // A complete run may not be obtained by clipping a longer unknown stroke at a
                // search edge.
                if ((a == top && p.dark(x, a - 1) && !owner.pixels.get(p.index(x, a - 1)))
                        || (b == bottom
                                && p.dark(x, b + 1)
                                && !owner.pixels.get(p.index(x, b + 1)))) {
                    uncertain[r][ix] = true;
                    continue;
                }
                first[r][ix] = a;
                last[r][ix] = b;
                // Semantic ink only occludes a measured rule where it physically joins the
                // observed nonowner run. A disconnected endpoint elsewhere in the half-gap
                // search domain does not make this full raw cross-section unknown.
                // Only a pixel that is itself part of the exact owner footprint is occluded.
                // Adjacency alone cannot transfer ownership of this independently observed
                // raw run pixel to a nearby endpoint.
                for (int y = a; y <= b; y++)
                    if (owner.pixels.get(p.index(x, y))) occluded[r][ix] = true;
                for (int y = a; y <= b; y++)
                    if (p.dark(x, y) && !owner.pixels.get(p.index(x, y)))
                        observed.set(p.index(x, y));
            }
        // A valid frame is evaluated independently of isolated cross-section
        // changes. Per-pixel authority below remains conservative: only an observed
        // center sample can be staff; an attached bump outside it stays UNKNOWN.
        // Common affine registration measured ONLY from complete external five-rule witnesses.
        // Overlap pixels, bars and owner bodies never contribute to this fit.
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int samples = 0, mid = (p.scope.left() + p.scope.right()) / 2;
        int leftRun = 0, rightRun = 0, leftMax = 0, rightMax = 0;
        for (int x = p.scope.left(); x <= p.scope.right(); x++)
            if (x < owner.left || x > owner.right) {
                int ix = x - p.scope.left();
                boolean complete = true, ownerContact = false;
                for (int r = 0; r < 5; r++) {
                    if (occluded[r][ix]) ownerContact = true;
                    if (occluded[r][ix] || uncertain[r][ix] || first[r][ix] < 0) complete = false;
                }
                if (!complete) {
                    // This column supplies no external frame sample and no staff authority.
                    // Keep all its raw ink UNKNOWN, reset the clear-witness run, and require
                    // a full independent two-gap witness on each side. A complete literal
                    // raw rail path across the column is separately mandatory below.
                    if (x < owner.left) {
                        leftRun = 0;
                    } else {
                        rightRun = 0;
                    }
                    continue;
                }
                if (x < owner.left) {
                    leftRun++;
                    leftMax = Math.max(leftMax, leftRun);
                } else {
                    rightRun++;
                    rightMax = Math.max(rightMax, rightRun);
                }
                double dx = x - mid;
                for (int r = 0; r < 5; r++) {
                    double off = (first[r][ix] + last[r][ix]) * .5 - staff.top() - r * staff.gap();
                    sx += dx;
                    sy += off;
                    sxx += dx * dx;
                    sxy += dx * off;
                    samples++;
                }
            }
        if (leftMax < witness || rightMax < witness)
            throw new SparseStaffContact.Unknown("INSUFFICIENT_CLEAR_EXTERNAL_WITNESS");
        double denominator = samples * sxx - sx * sx;
        if (samples < 10 || denominator <= 0)
            throw new SparseStaffContact.Unknown("NO_EXTERNAL_MODEL");
        double slope = (samples * sxy - sx * sy) / denominator, phase = (sy - slope * sx) / samples;
        // .65 is existing supported PairedTremoloInk slope domain, not a new favorable search.
        if (!Double.isFinite(slope) || Math.abs(slope) > .65)
            throw new SparseStaffContact.Unknown("UNSUPPORTED_TILT");
        // Within the endpoint-owner span, a genuine staff run may share the
        // nearest-rule search band with a disconnected bar component. Select only a
        // unique, complete raw run whose measured center agrees with the frame fit;
        // every competing run remains unassigned. Never do this in external witness
        // columns, where ambiguity still invalidates the frame.
        budget.work(5L * w * runCapacity);
        for (int x = Math.max(p.scope.left(), owner.left);
                x <= Math.min(p.scope.right(), owner.right);
                x++)
            for (int r = 0; r < 5; r++) {
                int ix = x - p.scope.left(), column = r * w + ix, count = candidateCount[column];
                if (count < 1) continue;
                int top = (int) Math.floor(staff.top() + r * staff.gap() - staff.gap() * .5) + 1;
                int bottom = (int) Math.ceil(staff.top() + r * staff.gap() + staff.gap() * .5) - 1;
                double expected = staff.top() + r * staff.gap() + phase + slope * (x - mid);
                int chosen = -1, matches = 0;
                for (int c = 0; c < count; c++) {
                    int a = candidateFirst[column * runCapacity + c],
                            b = candidateLast[column * runCapacity + c];
                    if (b - a + 1 > maxThickness) continue;
                    if ((a == top && p.dark(x, a - 1) && !owner.pixels.get(p.index(x, a - 1)))
                            || (b == bottom
                                    && p.dark(x, b + 1)
                                    && !owner.pixels.get(p.index(x, b + 1)))) continue;
                    double center = (a + b) * .5;
                    if (Math.abs(center - expected) <= (b - a + 1) * .5 + .5) {
                        chosen = c;
                        matches++;
                    }
                }
                if (matches != 1) {
                    first[r][ix] = -1;
                    last[r][ix] = -1;
                    uncertain[r][ix] = true;
                    continue;
                }
                int a = candidateFirst[column * runCapacity + chosen],
                        b = candidateLast[column * runCapacity + chosen];
                first[r][ix] = a;
                last[r][ix] = b;
                uncertain[r][ix] = false;
                for (int y = a; y <= b; y++) {
                    int pixel = p.index(x, y);
                    if (owner.pixels.get(pixel)) occluded[r][ix] = true;
                    else if (p.dark(x, y)) observed.set(pixel);
                }
            }
        BitSet certain = new BitSet((int) area);
        for (int x = p.scope.left(); x <= p.scope.right(); x++)
            for (int r = 0; r < 5; r++) {
                int ix = x - p.scope.left();
                double center = staff.top() + r * staff.gap() + phase + slope * (x - mid);
                if (first[r][ix] >= 0) {
                    double cy = (first[r][ix] + last[r][ix]) * .5;
                    int thick = last[r][ix] - first[r][ix] + 1;
                    if (Math.abs(cy - center) > thick * .5 + .5)
                        throw new SparseStaffContact.Unknown("INCOHERENT_RULE_GEOMETRY");
                }
                if (ix > 0
                        && first[r][ix] >= 0
                        && first[r][ix - 1] >= 0
                        && !occluded[r][ix]
                        && !occluded[r][ix - 1]
                        && (first[r][ix] > last[r][ix - 1] + 1
                                || first[r][ix - 1] > last[r][ix] + 1))
                    throw new SparseStaffContact.Unknown("DISCONNECTED_RULE");
                // Authority cannot follow the midpoint of a locally attached upper/lower fringe.
                // Query the independently external-fitted carrier against ACTUAL observed raw ink.
                // If quantization puts that coordinate on paper, this column is simply unowned;
                // the separate raw-path proof below may still establish genuine staff continuation.
                if (!occluded[r][ix] && !uncertain[r][ix] && first[r][ix] >= 0) {
                    int measuredY = (int) Math.rint(center);
                    if (measuredY >= p.scope.top()
                            && measuredY <= p.scope.bottom()
                            && observed.get(p.index(x, measuredY)))
                        certain.set(p.index(x, measuredY));
                }
            }
        // Establish raw continuation, never fill occluded columns. The model only bounds
        // the search domain; every path vertex is actual raw ink. All alternatives may
        // survive; NO vertex from an owner/ambiguous column becomes staff authority.
        budget.work(40L * w * (maxThickness + 3) + 5L * w * h);
        budget.memory(10L * h + 1024);
        for (int r = 0; r < 5; r++) {
            boolean[] previous = new boolean[h], next = new boolean[h];
            for (int ix = 0; ix < w; ix++) {
                Arrays.fill(next, false); // prepaid fixed local-height initialization below
                int x = p.scope.left() + ix;
                double center = staff.top() + r * staff.gap() + phase + slope * (x - mid);
                int lo = Math.max(p.scope.top(), (int) Math.ceil(center - maxThickness * .5 - .5));
                int hi =
                        Math.min(
                                p.scope.bottom(),
                                (int) Math.floor(center + maxThickness * .5 + .5));
                boolean reached = false;
                for (int y = lo; y <= hi; y++) {
                    if (!occluded[r][ix]
                            && !uncertain[r][ix]
                            && (y < first[r][ix] || y > last[r][ix])) continue;
                    if (!p.dark(x, y)) continue;
                    int at = y - p.scope.top();
                    if (ix == 0
                            || previous[at]
                            || (at > 0 && previous[at - 1])
                            || (at + 1 < h && previous[at + 1])) {
                        next[at] = true;
                        reached = true;
                    }
                }
                if (!reached) throw new SparseStaffContact.Unknown("NO_RAW_RULE_CONTINUATION");
                boolean[] swap = previous;
                previous = next;
                next = swap;
            }
        }

        // Retain every unknown raw pixel rather than erase it or make an unrelated body
        // certify itself. This is selective staff-core evidence, NOT whole-component ownership.
        int unknown = 0;
        for (int y = p.scope.top(); y <= p.scope.bottom(); y++)
            for (int x = p.scope.left(); x <= p.scope.right(); x++) {
                int i = p.index(x, y);
                if (p.dark(x, y) && !certain.get(i) && !owner.pixels.get(i)) unknown++;
            }
        for (int r = 0; r < 5; r++) for (int x = 0; x < w; x++) occluded[r][x] |= uncertain[r][x];
        return new Evidence(
                p,
                staff,
                first,
                last,
                occluded,
                certain,
                slope,
                phase,
                unknown,
                owner.left,
                owner.right);
    }
}
