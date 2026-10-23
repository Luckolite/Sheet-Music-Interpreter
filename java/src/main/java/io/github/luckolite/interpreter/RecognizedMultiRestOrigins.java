// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Invocation-local semantic fact from an actual accepted multi-rest, not AA ownership. */
final class RecognizedMultiRestOrigins {
    record Admission(
            Object invocation,
            MeasureNumberReconciler.NumberToken token,
            MultiMeasureRestDetector.RestBarCandidate candidate,
            MeasureRegion parent,
            int rawIndex) {}

    private final SelectedMeasureOrigins physical;
    private final IdentityHashMap<MeasureRegion, Admission> entries = new IdentityHashMap<>();
    private Object invocation;
    private byte[] labels, gray;
    private int w, h;
    private List<MeasureRegion> raw, rawOrder = List.of();
    private boolean incomplete = true;
    private final IdentityHashMap<MeasureRegion, int[]> expandedRuns = new IdentityHashMap<>();
    private boolean sealed;

    RecognizedMultiRestOrigins(SelectedMeasureOrigins physical) {
        this.physical = physical;
    }

    void begin(byte[] l, byte[] g, int width, int height, List<MeasureRegion> m) {
        entries.clear();
        expandedRuns.clear();
        sealed = false;
        invocation = new Object();
        labels = l;
        gray = g;
        w = width;
        h = height;
        raw = m;
        rawOrder = List.of();
        incomplete =
                l == null
                        || g == null
                        || w < 1
                        || h < 1
                        || (long) w * h > 32000000
                        || l.length != (long) w * h
                        || g.length != l.length
                        || m == null
                        || m.size() > 4096
                        || physical == null;
        if (incomplete) return;
        var unique = new IdentityHashMap<MeasureRegion, Boolean>();
        for (var parent : m)
            if (parent == null || unique.put(parent, Boolean.TRUE) != null) {
                incomplete = true;
                return;
            }
        rawOrder = List.copyOf(m);
    }

    void admitted(
            MeasureNumberReconciler.NumberToken token,
            MultiMeasureRestDetector.RestBarCandidate candidate,
            int index) {
        if (incomplete) return;
        if (token == null
                || candidate == null
                || candidate.measureIndex() != index
                || index < 0
                || index >= rawOrder.size()
                || token.value() < 2
                || token.value() > 32
                || entries.size() >= 4096) {
            incomplete = true;
            return;
        }
        var parent = rawOrder.get(index);
        if (entries.containsKey(parent)) {
            incomplete = true;
            return;
        }
        entries.put(parent, new Admission(invocation, token, candidate, parent, index));
    }

    void expanded(List<MeasureRegion> input, List<MeasureRegion> logical) {
        expandedRuns.clear();
        sealed = false;
        if (incomplete || input != raw || logical == null || logical.size() > 4096) return;
        var seen = new IdentityHashMap<MeasureRegion, Boolean>();
        for (int first = 0; first < logical.size(); ) {
            var parent = logical.get(first);
            int end = first + 1;
            while (end < logical.size() && logical.get(end) == parent) end++;
            if (seen.put(parent, Boolean.TRUE) != null) {
                expandedRuns.remove(parent);
                first = end;
                continue;
            }
            var a = admission(parent);
            if (a != null && end - first == a.token().value())
                expandedRuns.put(parent, new int[] {first, end});
            first = end;
        }
        sealed = true;
    }

    /** Relocate only a previously accepted identity run after earlier bars are repaired. */
    void reindexed(List<MeasureRegion> logical) {
        var accepted = new IdentityHashMap<>(expandedRuns);
        expandedRuns.clear();
        if (incomplete || !sealed || logical == null || logical.size() > 4096) return;
        var seen = new IdentityHashMap<MeasureRegion, Boolean>();
        for (int first = 0; first < logical.size(); ) {
            var parent = logical.get(first);
            int end = first + 1;
            while (end < logical.size() && logical.get(end) == parent) end++;
            if (seen.put(parent, Boolean.TRUE) != null) {
                expandedRuns.remove(parent);
                first = end;
                continue;
            }
            var previous = accepted.get(parent);
            if (parent != null && previous != null && end - first == previous[1] - previous[0])
                expandedRuns.put(parent, new int[] {first, end});
            first = end;
        }
    }

    boolean expansion(MeasureRegion parent, int first, int end) {
        var run = expandedRuns.get(parent);
        return sealed && run != null && run[0] == first && run[1] == end;
    }

    boolean matches(byte[] l, byte[] g, int width, int height) {
        if (incomplete
                || !sealed
                || labels != l
                || gray != g
                || width != w
                || height != h
                || raw == null
                || raw.size() != rawOrder.size()) return false;
        for (int i = 0; i < rawOrder.size(); i++) if (raw.get(i) != rawOrder.get(i)) return false;
        return true;
    }

    Admission admission(MeasureRegion parent) {
        var a = entries.get(parent);
        return a != null && a.invocation() == invocation ? a : null;
    }

    SelectedMeasureOrigins.Origin physical(MeasureRegion parent) {
        return physical == null ? null : physical.origin(parent, labels, gray, w, h);
    }
}
