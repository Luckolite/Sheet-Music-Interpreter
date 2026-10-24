// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Source-bound roles for universally proved finite bars at a witnessed staff bridge.
 * Raw/staff membership is exact. Optical authority is restricted to one immediately
 * adjacent pixel below independently observed exterior paper. The entire interbar
 * profile must have one paper maximum; a foreign dark/pale trough refuses.
 */
final class ObservedFiniteRasterRoles {
    private ObservedFiniteRasterRoles() {}

    static final class Evidence {
        private final SparseStaffContact.Snapshot snapshot;
        private final SparseStaffContact.StaffIdentity staff;
        private final String labels;
        private final List<Set<Integer>> raw, fringe, staffEdges;
        private final Set<Integer> staffPixels;
        private final int left, right;

        private Evidence(
                SparseStaffContact.Snapshot p,
                SparseStaffContact.StaffIdentity s,
                String labels,
                List<Set<Integer>> raw,
                List<Set<Integer>> fringe,
                List<Set<Integer>> staffEdges,
                Set<Integer> carrier,
                int left,
                int right) {
            snapshot = p;
            staff = s;
            this.labels = labels;
            this.raw = raw.stream().map(Set::copyOf).toList();
            this.fringe = fringe.stream().map(Set::copyOf).toList();
            this.staffEdges = staffEdges.stream().map(Set::copyOf).toList();
            staffPixels = Set.copyOf(carrier);
            this.left = left;
            this.right = right;
        }

        boolean matchesGroups(List<int[]> groups) {
            if (groups.size() != raw.size()) return false;
            Set<Integer> matched = new HashSet<>();
            for (int[] group : groups) {
                int match = -1;
                for (int i = 0; i < raw.size(); i++)
                    if (raw.get(i).size() == group.length) {
                        boolean all = true;
                        for (int p : group)
                            if (!raw.get(i).contains(p)) {
                                all = false;
                                break;
                            }
                        if (all) {
                            if (match >= 0) return false;
                            match = i;
                        }
                    }
                if (match < 0 || !matched.add(match)) return false;
            }
            return true;
        }

        boolean owns(
                byte[] gray,
                int w,
                int h,
                PairedTremoloInk.Head head,
                SparseStaffContact.Budget budget) {
            return proveWholeComponent(gray, w, h, head, budget) != null;
        }

        ComponentProof proveWholeComponent(
                byte[] gray,
                int w,
                int h,
                PairedTremoloInk.Head head,
                SparseStaffContact.Budget budget) {
            if (head == null
                    || head.memberToken() == null
                    || head.measure() != staff.measure()
                    || head.staff() != staff.staff()
                    || head.staffTop() != staff.top()
                    || Math.abs(head.gap() - staff.gap()) > .001
                    || !head.memberToken().authenticates(gray, w, h)
                    || !snapshot.rawSha.equals(head.memberToken().rawSha())
                    || !labels.equals(head.memberToken().labelSha())) return null;
            int n = head.memberToken().stats().area();
            if (n < 1 || n > 4096) return null;
            budget.work(8L * n * raw.size());
            budget.memory(12L * n + 256);
            int[] actual =
                    head.memberToken().pixels(new ExactComponentMembers.Budget(n, 4L * n + 32));
            if (actual == null || actual.length != n || head.footprint().size() != n) return null;
            Set<Integer> declared = new HashSet<>(head.footprint());
            if (declared.size() != n) return null;
            for (int p : actual)
                if (!declared.contains(p) || p % w < left || p % w > right) return null;
            int selected = -1, selectedCore = 0, selectedFringe = 0, selectedStaff = 0;
            for (int i = 0; i < raw.size(); i++) {
                boolean all = true;
                int core = 0, alpha = 0, staffCount = 0;
                for (int p : actual) {
                    if (raw.get(i).contains(p)) {
                        if (staffPixels.contains(p)) staffCount++;
                        else core++;
                    } else if (staffEdges.get(i).contains(p)) staffCount++;
                    else if (fringe.get(i).contains(p)) alpha++;
                    else {
                        all = false;
                        break;
                    }
                }
                if (all && core > 0) {
                    if (selected >= 0) return null;
                    selected = i;
                    selectedCore = core;
                    selectedFringe = alpha;
                    selectedStaff = staffCount;
                }
            }
            FringeProfileDiagnostics.record(
                    "FINITE_COMPONENT_ROLE\\thead="
                            + head.id()
                            + " area="
                            + n
                            + " body="
                            + selected
                            + " core="
                            + selectedCore
                            + " staff="
                            + selectedStaff
                            + " fringe="
                            + selectedFringe);
            return selected >= 0
                    ? new ComponentProof(
                            head.memberToken(),
                            selected,
                            selectedCore,
                            selectedStaff,
                            selectedFringe)
                    : null;
        }
    }

    static final class ComponentProof {
        private final ExactComponentMembers.AcceptedProbeHead token;
        private final int body, core, staff, fringe;

        private ComponentProof(
                ExactComponentMembers.AcceptedProbeHead token,
                int body,
                int core,
                int staff,
                int fringe) {
            this.token = token;
            this.body = body;
            this.core = core;
            this.staff = staff;
            this.fringe = fringe;
        }

        boolean matches(ExactComponentMembers.AcceptedProbeHead actual) {
            return actual != null && actual == token;
        }

        int body() {
            return body;
        }

        int core() {
            return core;
        }

        int staff() {
            return staff;
        }

        int fringe() {
            return fringe;
        }
    }

    static Evidence produce(
            byte[] gray,
            SparseStaffContact.Snapshot p,
            MaximalRawBarExtractor.Discovery d,
            MaximalRawBarExtractor.FiniteFamilyProof proof,
            Set<Integer> carrier,
            SparseStaffContact.StaffIdentity staff,
            String labels,
            SparseStaffContact.Budget budget) {
        return produce(gray, p, d, proof, carrier, staff, labels, null, budget);
    }

    static Evidence produce(
            byte[] gray,
            SparseStaffContact.Snapshot p,
            MaximalRawBarExtractor.Discovery d,
            MaximalRawBarExtractor.FiniteFamilyProof proof,
            Set<Integer> carrier,
            SparseStaffContact.StaffIdentity staff,
            String labels,
            ObservedStaffRasterField field,
            SparseStaffContact.Budget budget) {
        if (gray == null
                || p == null
                || d == null
                || proof == null
                || staff == null
                || labels == null
                || budget == null
                || carrier == null
                || carrier.isEmpty()
                || d.bodies().size() < 2
                || d.bodies().size() > 3) return null;
        if (!proof.matches(p, d)
                || !d.authenticFor(
                        p.rawSha, p.width, p.height, d.sourceIdentity().lane(), carrier, true))
            return null;
        int gap = (int) Math.rint(staff.gap()),
                mid = (d.sourceIdentity().lane().left() + d.sourceIdentity().lane().right()) / 2,
                reach = Math.min(64, (int) Math.rint(.75 * gap) + 1);
        long members = d.bodies().stream().mapToLong(b -> b.rawPixels().size()).sum();
        budget.work(80L * members + 512L * 512);
        budget.memory(160L * members + 65536);
        for (var body : d.bodies())
            if (!ObservedBarRasterFringe.contactOwnershipComplete(
                    gray, p.width, p.height, mid, gap, body, carrier)) return null;
        List<Set<Integer>> raw = new ArrayList<>(),
                fringe = new ArrayList<>(),
                staffEdges = new ArrayList<>();
        List<Map<Integer, int[]>> edges = new ArrayList<>();
        int left = 0, right = p.width - 1;
        for (var body : d.bodies()) {
            if (body.firstCapWidth() < 1 || body.lastCapWidth() < 1) return null;
            Set<Integer> set = new HashSet<>(body.rawPixels());
            if (set.size() != body.rawPixels().size()) return null;
            raw.add(set);
            fringe.add(new HashSet<>());
            staffEdges.add(new HashSet<>());
            Map<Integer, int[]> e = new TreeMap<>();
            for (int pixel : set) {
                int x = pixel % p.width, y = pixel / p.width;
                if (p.observedGray(x, y) >= 185) return null;
                int[] a = e.get(x);
                if (a == null) e.put(x, new int[] {y, y});
                else {
                    a[0] = Math.min(a[0], y);
                    a[1] = Math.max(a[1], y);
                }
            }
            edges.add(e);
            left = Math.max(left, body.firstX());
            right = Math.min(right, body.lastX());
        }
        if (right <= left) return null;
        for (int x = left; x <= right; x++) {
            final int column = x;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < edges.size(); i++) order.add(i);
            order.sort(Comparator.comparingInt(i -> edges.get(i).get(column)[0]));
            int top = edges.get(order.get(0)).get(x)[0],
                    bottom = edges.get(order.get(order.size() - 1)).get(x)[1];
            if (!outerPaper(p, carrier, x, top, -1, reach, field)
                    || !outerPaper(p, carrier, x, bottom, 1, reach, field)) return null;
            for (int k = 1; k < order.size(); k++) {
                int above = edges.get(order.get(k - 1)).get(x)[1],
                        below = edges.get(order.get(k)).get(x)[0];
                if (below <= above) return null;
                boolean descending = false;
                int previous = -1;
                for (int y = above + 1; y < below; y++) {
                    int at = y * p.width + x;
                    if (carrier.contains(at)) continue;
                    int value = p.observedGray(x, y);
                    int background = field == null ? 255 : field.background(p, x, y);
                    if (background < 185
                            || value > background
                            || value < 185 && (field == null || !field.hasProfileAt(y)))
                        return null;
                    int deficit = background - value;
                    if (previous >= 0) {
                        if (deficit > previous) descending = true;
                        else if (descending && deficit < previous) return null;
                    }
                    previous = deficit;
                }
            }
            for (int i = 0; i < edges.size(); i++)
                for (int y : new int[] {edges.get(i).get(x)[0] - 1, edges.get(i).get(x)[1] + 1}) {
                    int at = y * p.width + x;
                    int value = p.observedGray(x, y);
                    int background = field == null ? 255 : field.background(p, x, y);
                    if (carrier.contains(at)) staffEdges.get(i).add(at);
                    else if (value >= 185 && value < background) fringe.get(i).add(at);
                }
        }
        return new Evidence(p, staff, labels, raw, fringe, staffEdges, carrier, left, right);
    }

    private static boolean outerPaper(
            SparseStaffContact.Snapshot p,
            Set<Integer> staff,
            int x,
            int edge,
            int direction,
            int reach,
            ObservedStaffRasterField field) {
        int previous = Integer.MAX_VALUE;
        for (int n = 1; n <= reach; n++) {
            int y = edge + direction * n;
            if (y < p.scope.top() || y > p.scope.bottom()) return false;
            int at = y * p.width + x;
            if (staff.contains(at)) continue;
            int value = p.observedGray(x, y),
                    background = field == null ? 255 : field.background(p, x, y);
            if (background < 185
                    || value > background
                    || value < 185 && (field == null || !field.hasProfileAt(y))) return false;
            int deficit = background - value;
            if (deficit > previous) return false;
            if (deficit == 0) return true;
            previous = deficit;
        }
        return false;
    }
}
