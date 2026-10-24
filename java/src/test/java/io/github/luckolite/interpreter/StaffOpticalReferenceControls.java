// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class StaffOpticalReferenceControls {
    static final int W = 240, H = 150;
    static int checks;

    record Fixture(
            byte[] g,
            byte[] l,
            SparseStaffContact.Snapshot p,
            RawMeasuredStaffTracks.Evidence tracks,
            ObservedStaffRasterField field,
            ExactComponentMembers.AcceptedProbeHead token) {}

    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }

    static Fixture fixture(int adverse) {
        byte[] g = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int row : new int[] {20, 34, 48, 62, 76})
            for (int x = 1; x < W - 1; x++) {
                int[] shade = {222, 154, 126, 186, 244};
                for (int k = 0; k < shade.length; k++) g[(row - 1 + k) * W + x] = (byte) shade[k];
                labels[row * W + x] = labels[(row + 1) * W + x] = OmrMeasurePostProcessor.STAFF;
            }
        List<Set<Integer>> components = new ArrayList<>();
        for (int cx : new int[] {60, 130}) {
            Set<Integer> members = new TreeSet<>();
            for (int y = 85; y <= 95; y++)
                for (int x = cx - 7; x <= cx + 7; x++)
                    if (Math.pow((x - cx) / 7.0, 2) + Math.pow((y - 90) / 5.0, 2) <= 1) {
                        g[y * W + x] = 0;
                        labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                        members.add(y * W + x);
                    }
            components.add(members);
            for (int y = 24; y <= 90; y++) g[y * W + cx + 8] = 0;
        }
        if (adverse == 1) g[22 * W + 5] = (byte) 187;
        if (adverse == 2) for (int x = 139; x < 239; x++) g[22 * W + x] = (byte) 187;
        if (adverse == 3 || adverse == 5) for (int y = 10; y <= 85; y++) g[y * W + 5] = 0;
        if (adverse == 4) for (int y = 10; y <= 85; y++) g[y * W + 28] = 0;
        if (adverse == 5) g[22 * W + 6] = (byte) 187;
        var c =
                new ExactComponentMembers.Collector(
                        g,
                        labels,
                        W,
                        H,
                        new ExactComponentMembers.Budget(2L * g.length + 4096, 4096),
                        new ExactComponentMembers.Budget(10000000, 64000000));
        if (!c.bindInput(labels, g, W, H)) throw new AssertionError("bind");
        List<Object> ids = new ArrayList<>();
        for (var set : components) {
            Object id = new Object();
            ids.add(id);
            var b = c.begin();
            int minX = W, maxX = -1, minY = H, maxY = -1;
            long sx = 0, sy = 0;
            for (int p : set) {
                b.add(p / W, p % W, p % W);
                minX = Math.min(minX, p % W);
                maxX = Math.max(maxX, p % W);
                minY = Math.min(minY, p / W);
                maxY = Math.max(maxY, p / W);
                sx += p % W;
                sy += p / W;
            }
            c.record(
                    id,
                    new ExactComponentMembers.Stats(
                            set.size(),
                            minX,
                            maxX,
                            minY,
                            maxY,
                            sx / (float) set.size(),
                            sy / (float) set.size()),
                    OmrMeasurePostProcessor.NOTEHEAD,
                    b);
        }
        if (!c.seal(new ExactComponentMembers.Budget(2L * g.length + 4096, 4096)))
            throw new AssertionError("seal");
        var tokens =
                List.of(
                        c.acceptedProbeHead(ids.get(0), 0, 0),
                        c.acceptedProbeHead(ids.get(1), 0, 0));
        var budget = new SparseStaffContact.Budget(10000000, 64000000);
        var p =
                SparseStaffContact.Snapshot.captureAuthenticated(
                        g,
                        W,
                        H,
                        tokens.get(0),
                        new SparseStaffContact.Rect(1, 1, 238, 120),
                        budget);
        var staff = new SparseStaffContact.StaffIdentity(0, 0, 20, 14);
        var owner =
                RawMeasuredStaffTracks.OwnerFootprint.fromCapturedHeadsAndShafts(
                        p,
                        staff,
                        tokens,
                        List.of(
                                new RawMeasuredStaffTracks.Shaft(68, 68, 24, 90),
                                new RawMeasuredStaffTracks.Shaft(138, 138, 24, 90)),
                        budget);
        var result = RawMeasuredStaffTracks.produce(p, staff, owner, budget);
        if (!result.complete()) {
            if (adverse == 4) return new Fixture(g, labels, p, null, null, tokens.get(0));
            throw new AssertionError("track fixture " + result.refusal());
        }
        var field = ObservedStaffRasterField.produce(p, staff, result.evidence(), budget);
        return new Fixture(g, labels, p, result.evidence(), field, tokens.get(0));
    }

    public static void main(String[] args) {
        var f = fixture(0);
        check(f.field != null, "source_bound_staff_reference_from_both_full_windows");
        check(
                f.field.hasProfileAt(22) && f.field.background(f.p, 100, 22) == 186,
                "literal_first_staff_optical_sample");
        check(
                f.field.hasProfileAt(23) && f.field.background(f.p, 100, 23) == 244,
                "literal_second_staff_optical_sample");
        check(
                f.field.background(f.p, 100, 25) == 255,
                "ordinary_background_not_inflated_into_staff");
        var altered = fixture(1);
        check(
                altered.field == null || !altered.field.hasProfileAt(22),
                "one_conflicting_external_column_refuses_profile");
        var sides = fixture(2);
        check(
                sides.field == null || !sides.field.hasProfileAt(22),
                "different_left_right_profile_never_averaged");
        var junction = fixture(3);
        check(
                junction.field != null && junction.field.hasProfileAt(22),
                "foreign_junction_not_used_as_witness_or_a_veto_on_complete_external_run");
        var partial = fixture(4);
        check(
                partial.field == null,
                "disconnected_short_runs_cannot_be_spliced_into_two_gap_witness");
        var tempting = fixture(5);
        check(
                tempting.field == null || !tempting.field.hasProfileAt(22),
                "every_eligible_witness_column_required_no_favorable_subwindow");
        var clone =
                SparseStaffContact.Snapshot.captureAuthenticated(
                        f.g,
                        W,
                        H,
                        f.token,
                        new SparseStaffContact.Rect(1, 1, 238, 120),
                        new SparseStaffContact.Budget(1000000, 64000000));
        check(
                ObservedStaffRasterField.produce(
                                clone,
                                new SparseStaffContact.StaffIdentity(0, 0, 20, 14),
                                f.tracks,
                                new SparseStaffContact.Budget(1000000, 64000000))
                        == null,
                "foreign_snapshot_identity_rejected");
        check(
                ObservedStaffRasterField.monotoneContrastToPaper(
                        new int[] {195, 185, 182, 222}, new int[] {255, 244, 186, 222}),
                "overlapping_staff_and_bar_contrast_declines_to_literal_reference");
        check(
                !ObservedStaffRasterField.monotoneContrastToPaper(
                        new int[] {195, 0, 182, 222}, new int[] {255, 244, 186, 222}),
                "foreign_dark_branch_breaks_contrast_order");
        check(
                !ObservedStaffRasterField.monotoneContrastToPaper(
                        new int[] {195, 210, 205, 250}, new int[] {255, 255, 255, 255}),
                "separate_pale_lobe_breaks_contrast_order");
        check(
                !ObservedStaffRasterField.monotoneContrastToPaper(
                        new int[] {195, 230, 250}, new int[] {255, 255, 255}),
                "no_independent_zero_residual_paper_refuses");
        check(
                !ObservedStaffRasterField.monotoneContrastToPaper(
                        new int[] {180, 244}, new int[] {154, 244}),
                "raw_staff_core_cannot_be_paper_witness");
        check(
                !ObservedStaffRasterField.monotoneContrastToPaper(
                        new int[] {200, 255}, new int[] {186, 255}),
                "brighter_than_observed_reference_is_not_foreground");
        System.out.println("staffOpticalControls=" + checks + "/" + checks);
    }
}
