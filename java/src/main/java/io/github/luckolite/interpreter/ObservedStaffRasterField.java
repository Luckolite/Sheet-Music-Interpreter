// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Literal staff-only grayscale reference from both complete external witness windows.
 * This describes a background profile; it never mints local staff/core membership,
 * fills an occluded rail, changes a raster sample, or independently authorizes removal.
 */
final class ObservedStaffRasterField {
    private final String rawSha;
    private final int width, height, left, right;
    private final Map<Integer, Integer> profiles;

    private ObservedStaffRasterField(
            SparseStaffContact.Snapshot p, int left, int right, Map<Integer, Integer> profiles) {
        rawSha = p.rawSha;
        width = p.width;
        height = p.height;
        this.left = left;
        this.right = right;
        this.profiles = Map.copyOf(profiles);
    }

    static ObservedStaffRasterField produce(
            SparseStaffContact.Snapshot p,
            SparseStaffContact.StaffIdentity staff,
            RawMeasuredStaffTracks.Evidence evidence,
            SparseStaffContact.Budget budget) {
        if (p == null || evidence == null || !evidence.authenticForOpticalReference(p, staff))
            return null;
        int l = evidence.opticalOwnerLeft(),
                r = evidence.opticalOwnerRight(),
                minimum = (int) Math.ceil(2 * staff.gap());
        if (l - p.scope.left() < minimum || p.scope.right() - r < minimum) return null;
        Set<Integer> observedCarrier =
                evidence.foregroundCarrier(
                        p,
                        p.scope.left(),
                        p.scope.top(),
                        p.scope.right(),
                        p.scope.bottom(),
                        budget);
        if (observedCarrier == null) return null;
        int rows = p.scope.height(), columns = l - p.scope.left() + p.scope.right() - r;
        budget.work((long) rows * columns + 16L * columns * 5 + rows * 16L);
        budget.memory(rows * 8L + 2048);
        List<Integer> carrierRows = new ArrayList<>();
        for (int y = p.scope.top(); y <= p.scope.bottom(); y++)
            if (observedCarrier.contains(y * p.width + p.scope.left())) carrierRows.add(y);
        int[] frame = new int[10];
        int railCount = 0;
        for (int i = 0; i < carrierRows.size(); ) {
            int a = carrierRows.get(i), z = a;
            i++;
            while (i < carrierRows.size() && carrierRows.get(i) == z + 1) z = carrierRows.get(i++);
            if (railCount >= 5) return null;
            frame[2 * railCount] = a;
            frame[2 * railCount + 1] = z;
            railCount++;
        }
        if (railCount != 5) return null;
        List<Integer> witnessColumns = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            int begin = side == 0 ? p.scope.left() : r + 1,
                    end = side == 0 ? l - 1 : p.scope.right(),
                    run = -1,
                    before = witnessColumns.size();
            for (int x = begin; x <= end + 1; x++) {
                boolean complete = x <= end && evidence.opticalWitnessFrameAt(p, x, frame);
                if (complete) {
                    if (run < 0) run = x;
                } else {
                    if (run >= 0 && x - run >= minimum)
                        for (int at = run; at < x; at++) witnessColumns.add(at);
                    run = -1;
                }
            }
            if (witnessColumns.size() - before < minimum) return null;
        }
        int[] reference = new int[rows];
        Arrays.fill(reference, -1);
        for (int y = p.scope.top(); y <= p.scope.bottom(); y++) {
            int value = -1;
            boolean same = true;
            for (int x : witnessColumns) {
                int sample = p.observedGray(x, y);
                if (value < 0) value = sample;
                else if (value != sample) same = false;
            }
            if (same) reference[y - p.scope.top()] = value;
        }
        Map<Integer, Integer> profiles = new HashMap<>();
        int radius = (int) Math.floor(staff.gap() * .5);
        for (int rail = 0; rail < 5; rail++) {
            Double center = null;
            boolean constant = true;
            for (int x : witnessColumns) {
                var c = evidence.observedCenter(rail, x, budget);
                if (c.isEmpty()) {
                    constant = false;
                    break;
                }
                if (center == null) center = c.getAsDouble();
                else if (center.doubleValue() != c.getAsDouble()) {
                    constant = false;
                    break;
                }
            }
            if (!constant || center == null) continue;
            int seed = (int) Math.floor(center), lo = seed, hi = seed;
            if (seed < p.scope.top()
                    || seed > p.scope.bottom()
                    || reference[seed - p.scope.top()] < 0
                    || reference[seed - p.scope.top()] >= 185) continue;
            while (lo > p.scope.top()
                    && reference[lo - 1 - p.scope.top()] >= 0
                    && reference[lo - 1 - p.scope.top()] < 185) lo--;
            while (hi < p.scope.bottom()
                    && reference[hi + 1 - p.scope.top()] >= 0
                    && reference[hi + 1 - p.scope.top()] < 185) hi++;
            if (hi - lo + 1 > 3) continue;
            boolean rawOwned = true;
            for (int y = lo; y <= hi; y++)
                for (int x : witnessColumns)
                    if (!observedCarrier.contains(y * p.width + x)) rawOwned = false;
            if (!rawOwned) continue;
            int first = paperEnd(reference, p.scope.top(), lo, -1, radius),
                    last = paperEnd(reference, p.scope.top(), hi, 1, radius);
            if (first < 0 || last < 0) continue;
            for (int y = first; y <= last; y++) {
                int value = reference[y - p.scope.top()];
                if (value < 255) profiles.put(y, value);
            }
        }
        FringeProfileDiagnostics.record(
                "STAFF_OPTICAL_REFERENCE\\trows="
                        + profiles.size()
                        + " witnessColumns="
                        + witnessColumns.size()
                        + " leftWindow="
                        + p.scope.left()
                        + ".."
                        + (l - 1)
                        + " rightWindow="
                        + (r + 1)
                        + ".."
                        + p.scope.right());
        return profiles.isEmpty() ? null : new ObservedStaffRasterField(p, l, r, profiles);
    }

    private static int paperEnd(int[] values, int top, int edge, int direction, int radius) {
        int previous = values[edge - top];
        for (int n = 1; n <= radius; n++) {
            int y = edge + direction * n, index = y - top;
            if (index < 0 || index >= values.length) return -1;
            int value = values[index];
            if (value < 0 || value < previous) return -1;
            if (value == 255) return y;
            previous = value;
        }
        return -1;
    }

    int background(SparseStaffContact.Snapshot p, int x, int y) {
        if (!rawSha.equals(p.rawSha)
                || width != p.width
                || height != p.height
                || x < left
                || x > right) throw new SparseStaffContact.Unknown("STALE_STAFF_OPTICAL_REFERENCE");
        return profiles.getOrDefault(y, 255);
    }

    boolean hasProfileAt(int y) {
        return profiles.containsKey(y);
    }

    static boolean monotoneContrastToPaper(int[] observed, int[] reference) {
        if (observed == null
                || reference == null
                || observed.length != reference.length
                || observed.length == 0) return false;
        int previous = Integer.MAX_VALUE;
        for (int i = 0; i < observed.length; i++) {
            if (observed[i] < 0
                    || reference[i] < 185
                    || observed[i] > reference[i]
                    || reference[i] > 255) return false;
            int deficit = reference[i] - observed[i];
            if (deficit > previous) return false;
            if (deficit == 0) return true;
            previous = deficit;
        }
        return false;
    }
}
