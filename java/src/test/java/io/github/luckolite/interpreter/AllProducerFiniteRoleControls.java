// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class AllProducerFiniteRoleControls {
    static int checks;

    static void check(boolean b, String name) {
        if (!b) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }

    static final int W = 150, H = 130, GAP = 17;

    record Fixture(
            byte[] g,
            byte[] labels,
            ObservedFiniteRasterRoles.Evidence evidence,
            List<PairedTremoloInk.Head> heads,
            int aa,
            int staff,
            int edgeStaff,
            ExactComponentMembers.Collector collector) {}

    static Fixture fixture(boolean blankPaper) {
        return fixture(blankPaper, false);
    }

    static Fixture fixture(boolean blankPaper, boolean forgedCarrier) {
        byte[] g = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(g, (byte) (blankPaper ? 245 : 255));
        Set<Integer> staff = new HashSet<>();
        for (int row : new int[] {20, 37, 54, 71, 88})
            for (int y = row; y <= row + 1; y++)
                for (int x = 1; x < W - 1; x++) {
                    g[y * W + x] = 0;
                    staff.add(y * W + x);
                }
        for (int bi = 0; bi < 3; bi++) {
            double ax = 35,
                    ay = 53 + bi * 14,
                    bx = 64,
                    by = ay + (-.3 - .05 * bi) * 29,
                    dx = bx - ax,
                    dy = by - ay,
                    den = dx * dx + dy * dy;
            for (int x = 29; x <= 70; x++)
                for (int y = 18; y < 100; y++) {
                    double t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / den));
                    double d = Math.hypot(x - ax - t * dx, y - ay - t * dy);
                    int value = d <= 3.5 ? 0 : d <= 4.5 ? 210 : 255;
                    if (value < (g[y * W + x] & 255)) g[y * W + x] = (byte) value;
                }
        }
        var lane = new MaximalRawBarExtractor.Lane(20, 90, 15, 105, GAP);
        var d = MaximalRawBarExtractor.discoverJoined(g, W, H, lane, 12000000, 64000000, staff);
        if (d.bodies().size() != 3) throw new AssertionError("fixture discovery");
        d = d.completeStaffBridges(g, W, H, staff, GAP, d.staffBridgeWorkUpperBound(GAP));
        if (d == null) throw new AssertionError("fixture staff completion");
        var proof = d.proveFiniteFamily(GAP, W, staff, g, H, 100000000);
        if (proof == null) throw new AssertionError("fixture finite proof");
        int
                left =
                        d.bodies().stream()
                                        .mapToInt(MaximalRawBarExtractor.Body::firstX)
                                        .max()
                                        .orElseThrow()
                                + 1,
                right =
                        d.bodies().stream()
                                        .mapToInt(MaximalRawBarExtractor.Body::lastX)
                                        .min()
                                        .orElseThrow()
                                - 1;
        var body =
                d.bodies().stream()
                        .min(Comparator.comparingInt(MaximalRawBarExtractor.Body::minY))
                        .orElseThrow();
        Set<Integer> good = new TreeSet<>(), onlyStaff = new TreeSet<>();
        Map<Integer, int[]> edges = new TreeMap<>();
        for (int pixel : body.rawPixels())
            if (pixel % W >= left && pixel % W <= right) {
                good.add(pixel);
                int x = pixel % W, y = pixel / W;
                int[] e = edges.get(x);
                if (e == null) edges.put(x, new int[] {y, y});
                else {
                    e[0] = Math.min(e[0], y);
                    e[1] = Math.max(e[1], y);
                }
                if (staff.contains(pixel)) onlyStaff.add(pixel);
            }
        int alpha = 0, edgeStaff = 0;
        for (var e : edges.entrySet())
            for (int y : new int[] {e.getValue()[0] - 1, e.getValue()[1] + 1}) {
                int p = y * W + e.getKey();
                if (staff.contains(p)) {
                    if (good.add(p)) edgeStaff++;
                } else if ((g[p] & 255) >= 185 && (g[p] & 255) < 255) {
                    good.add(p);
                    alpha++;
                }
            }
        Set<Integer> paper = new TreeSet<>(good);
        int paperPixel = -1;
        for (int y = 15; y < 105 && paperPixel < 0; y++)
            for (int x = left; x <= right; x++)
                if ((g[y * W + x] & 255) == 255 && !staff.contains(y * W + x)) {
                    paperPixel = y * W + x;
                    break;
                }
        if (paperPixel >= 0) paper.add(paperPixel);
        Set<Integer> alias = new TreeSet<>(good);
        for (var other : d.bodies())
            if (other != body)
                for (int p : other.rawPixels())
                    if (p % W >= left && p % W <= right) {
                        alias.add(p);
                        break;
                    }
        if (onlyStaff.size() < 3) throw new AssertionError("fixture genuine staff overlap");
        Set<Integer> farStaff = new TreeSet<>(good);
        int far = -1;
        for (var e : edges.entrySet())
            for (int y : new int[] {e.getValue()[0] - 2, e.getValue()[1] + 2}) {
                int p = y * W + e.getKey();
                if (staff.contains(p) && !body.rawPixels().contains(p) && !good.contains(p)) {
                    far = p;
                    break;
                }
            }
        if (far < 0) throw new AssertionError("fixture needs farther staff member");
        farStaff.add(far);
        Set<Integer> tiny = new TreeSet<>();
        outer:
        for (int x = left; x <= right - 2; x++)
            for (int y = body.minY(); y <= body.maxY() - 2; y++) {
                boolean all = true;
                for (int dy = 0; dy < 3; dy++)
                    for (int dx = 0; dx < 3; dx++)
                        if (!body.rawPixels().contains((y + dy) * W + x + dx)
                                || staff.contains((y + dy) * W + x + dx)) all = false;
                if (all) {
                    for (int dy = 0; dy < 3; dy++)
                        for (int dx = 0; dx < 3; dx++) tiny.add((y + dy) * W + x + dx);
                    break outer;
                }
            }
        if (tiny.size() != 9)
            throw new AssertionError("fixture needs genuine9member core component");
        Set<Integer> tinyForeign = new TreeSet<>(tiny);
        tinyForeign.remove(tinyForeign.iterator().next());
        if (paperPixel >= 0) tinyForeign.add(paperPixel);
        List<Set<Integer>> sets =
                List.of(good, paper, alias, onlyStaff, farStaff, tiny, tinyForeign);
        for (var set : sets) for (int p : set) labels[p] = OmrMeasurePostProcessor.NOTEHEAD;
        var collector =
                new ExactComponentMembers.Collector(
                        g,
                        labels,
                        W,
                        H,
                        new ExactComponentMembers.Budget(2L * g.length + 4096, 4096),
                        new ExactComponentMembers.Budget(20000000, 64000000));
        if (!collector.bindInput(labels, g, W, H)) throw new AssertionError("bind");
        List<Object> ids = new ArrayList<>();
        for (var pixels : sets) {
            Object id = new Object();
            ids.add(id);
            var b = collector.begin();
            int minX = W, maxX = -1, minY = H, maxY = -1;
            long sx = 0, sy = 0;
            for (int p : pixels) {
                b.add(p / W, p % W, p % W);
                minX = Math.min(minX, p % W);
                maxX = Math.max(maxX, p % W);
                minY = Math.min(minY, p / W);
                maxY = Math.max(maxY, p / W);
                sx += p % W;
                sy += p / W;
            }
            collector.record(
                    id,
                    new ExactComponentMembers.Stats(
                            pixels.size(),
                            minX,
                            maxX,
                            minY,
                            maxY,
                            sx / (float) pixels.size(),
                            sy / (float) pixels.size()),
                    OmrMeasurePostProcessor.NOTEHEAD,
                    b);
        }
        if (!collector.seal(new ExactComponentMembers.Budget(2L * g.length + 4096, 4096)))
            throw new AssertionError("seal");
        List<PairedTremoloInk.Head> heads = new ArrayList<>();
        for (int i = 0; i < sets.size(); i++) {
            var token = collector.acceptedProbeHead(ids.get(i), 0, 0);
            var st = token.stats();
            heads.add(
                    new PairedTremoloInk.Head(
                            i,
                            0,
                            0,
                            st.centerX(),
                            st.centerY(),
                            GAP,
                            0,
                            new ArrayList<>(sets.get(i)),
                            true,
                            20,
                            token,
                            -1,
                            -1,
                            -1));
        }
        var budget = new SparseStaffContact.Budget(100000000, 128000000);
        var snapshot =
                SparseStaffContact.Snapshot.captureAuthenticated(
                        g,
                        W,
                        H,
                        heads.get(0).memberToken(),
                        new SparseStaffContact.Rect(20, 15, 90, 105),
                        budget);
        Set<Integer> suppliedStaff = new HashSet<>(staff);
        if (forgedCarrier) suppliedStaff.add(15 * W + left);
        var evidence =
                ObservedFiniteRasterRoles.produce(
                        g,
                        snapshot,
                        d,
                        proof,
                        suppliedStaff,
                        new SparseStaffContact.StaffIdentity(0, 0, 20, GAP),
                        heads.get(0).memberToken().labelSha(),
                        budget);
        return new Fixture(
                g, labels, evidence, heads, alpha, onlyStaff.size(), edgeStaff, collector);
    }

    static boolean owns(Fixture f, PairedTremoloInk.Head h) {
        return f.evidence != null
                && f.evidence.owns(f.g, W, H, h, new SparseStaffContact.Budget(1000000, 16000000));
    }

    public static void main(String[] args) {
        Fixture f = fixture(false);
        check(f.evidence != null, "complete_source_bound_finite_photo_authority");
        check(f.aa > 0 && f.staff > 0, "fixture_has_real_optical_and_staff_coownership");
        check(f.edgeStaff > 0, "fixture_has_staff_members_immediately_outside_the_raw_bar");
        check(owns(f, f.heads.get(0)), "all_original_component_members_receive_one_body_role");
        check(!owns(f, f.heads.get(1)), "independent_paper_member_not_promoted");
        check(!owns(f, f.heads.get(2)), "raw_core_alias_across_two_bodies_rejected");
        check(!owns(f, f.heads.get(3)), "staff_only_component_never_becomes_a_bar");
        var h = f.heads.get(0);
        check(
                !f.evidence.owns(
                        f.g.clone(), W, H, h, new SparseStaffContact.Budget(1000000, 16000000)),
                "foreign_same_value_raster_identity_rejected");
        var dropped = new ArrayList<>(h.footprint());
        dropped.remove(0);
        var bad =
                new PairedTremoloInk.Head(
                        h.id(),
                        h.measure(),
                        h.staff(),
                        h.x(),
                        h.y(),
                        h.gap(),
                        0,
                        dropped,
                        true,
                        h.staffTop(),
                        h.memberToken(),
                        -1,
                        -1,
                        -1);
        check(!owns(f, bad), "no_component_subset_authority");
        var wrong =
                new PairedTremoloInk.Head(
                        h.id(),
                        1,
                        h.staff(),
                        h.x(),
                        h.y(),
                        h.gap(),
                        0,
                        h.footprint(),
                        true,
                        h.staffTop(),
                        h.memberToken(),
                        -1,
                        -1,
                        -1);
        check(!owns(f, wrong), "foreign_staff_measure_context_rejected");
        check(!owns(f, f.heads.get(4)), "farther_staff_pixels_cannot_be_forgiven_as_an_edge_role");
        check(
                owns(f, f.heads.get(5)),
                "entire_genuine_tiny9member_component_is_bound_without_size_filtering");
        check(
                !owns(f, f.heads.get(6)),
                "tiny_component_with_one_foreign_paper_member_cannot_be_trimmed");
        check(
                fixture(false, true).evidence == null,
                "forged_or_expanded_staff_carrier_cannot_mint_member_roles");
        Fixture closed = fixture(true);
        check(closed.evidence == null, "without_independent_exterior_paper_authority_is_unknown");
        System.out.println("finitePhotoControls=" + checks + "/" + checks);
    }
}
