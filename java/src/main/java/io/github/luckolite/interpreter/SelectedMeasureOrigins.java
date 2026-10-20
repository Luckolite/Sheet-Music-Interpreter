// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Invocation-local producer identity, never a claim that any predicted row is ink. */
public final class SelectedMeasureOrigins {
    static final class PhysicalMember {
        final Object owner;
        final int[] rows;
        final float gap, slope;
        final int left, right;
        final StaffPitchTrack track;

        PhysicalMember(
                Object owner,
                int[] rows,
                float gap,
                int left,
                int right,
                float slope,
                StaffPitchTrack track) {
            this.owner = owner;
            this.rows = rows == null ? null : rows.clone();
            this.gap = gap;
            this.left = left;
            this.right = right;
            this.slope = slope;
            this.track = track;
        }

        float[] physicalRows(int x, int width) {
            if (rows == null
                    || rows.length != 5
                    || !Float.isFinite(gap)
                    || gap < 3
                    || x < left
                    || x > right) return null;
            float[] out = new float[5];
            if (track != null) {
                float[] at = track.at(x);
                if (at == null || at.length != 2 || !Float.isFinite(at[0] + at[1]) || at[1] < 3)
                    return null;
                for (int i = 0; i < 5; i++) out[i] = at[0] - (4 - i) * at[1];
            } else {
                if (!Float.isFinite(slope)) return null;
                for (int i = 0; i < 5; i++) out[i] = rows[i] + slope * (x - width * .5f);
            }
            for (int i = 1; i < 5; i++) if (!(out[i] > out[i - 1])) return null;
            return out;
        }
    }

    static final class Origin {
        final Object invocation, system;
        final byte[] labels, gray;
        final int width, height, rawLeft, rawRight;
        final List<PhysicalMember> members;

        Origin(
                Object invocation,
                Object system,
                byte[] labels,
                byte[] gray,
                int width,
                int height,
                List<PhysicalMember> members,
                int rawLeft,
                int rawRight) {
            this.invocation = invocation;
            this.system = system;
            this.labels = labels;
            this.gray = gray;
            this.width = width;
            this.height = height;
            this.members = List.copyOf(members);
            this.rawLeft = rawLeft;
            this.rawRight = rawRight;
        }
    }

    private final IdentityHashMap<MeasureRegion, Origin> origins = new IdentityHashMap<>();
    private final IdentityHashMap<Object, PhysicalMember> births = new IdentityHashMap<>();
    private Object invocation;
    private byte[] sourceLabels, sourceGray, finalLabels;
    private int width, height;
    private boolean incomplete;

    public SelectedMeasureOrigins() {}

    void begin(byte[] labels, byte[] gray, int width, int height) {
        origins.clear();
        births.clear();
        invocation = new Object();
        finalLabels = null;
        sourceLabels = labels;
        sourceGray = gray;
        this.width = width;
        this.height = height;
        incomplete =
                labels == null
                        || gray == null
                        || width <= 0
                        || height <= 0
                        || labels.length != (long) width * height
                        || gray.length != labels.length;
    }

    void born(
            Object owner,
            int[] rows,
            float gap,
            int left,
            int right,
            float slope,
            StaffPitchTrack track) {
        if (owner == null || births.size() >= 4096 || births.containsKey(owner)) {
            incomplete = true;
            return;
        }
        births.put(owner, new PhysicalMember(owner, rows, gap, left, right, slope, track));
    }

    void replaced(
            Object previous,
            Object replacement,
            float gap,
            int left,
            int right,
            float slope,
            StaffPitchTrack track) {
        if (births.containsKey(replacement)) return;
        PhysicalMember original = births.get(previous);
        if (original == null
                || original.rows == null
                || Float.floatToRawIntBits(original.gap) != Float.floatToRawIntBits(gap)
                || Float.floatToRawIntBits(original.slope) != Float.floatToRawIntBits(slope)
                || original.track != track) return;
        born(replacement, original.rows, gap, left, right, slope, track);
    }

    PhysicalMember member(
            Object owner, float gap, int left, int right, float slope, StaffPitchTrack track) {
        PhysicalMember member = births.get(owner);
        return member != null
                ? member
                : new PhysicalMember(owner, null, gap, left, right, slope, track);
    }

    void emitted(
            Object system,
            List<PhysicalMember> members,
            MeasureRegion created,
            int rawLeft,
            int rawRight) {
        if (incomplete
                || invocation == null
                || system == null
                || members == null
                || members.isEmpty()
                || members.size() > 32
                || created == null
                || rawLeft < 0
                || rawRight >= width
                || rawLeft >= rawRight
                || origins.size() >= 4096
                || origins.containsKey(created)) {
            incomplete = true;
            return;
        }
        origins.put(
                created,
                new Origin(
                        invocation,
                        system,
                        sourceLabels,
                        sourceGray,
                        width,
                        height,
                        members,
                        rawLeft,
                        rawRight));
    }

    Origin origin(MeasureRegion region, byte[] labels, byte[] gray, int width, int height) {
        if (incomplete
                || finalLabels != labels
                || sourceGray != gray
                || this.width != width
                || this.height != height) return null;
        Origin origin = origins.get(region);
        return origin != null && origin.invocation == invocation ? origin : null;
    }

    void prepared(byte[] labels) {
        finalLabels = labels;
        if (labels == null || labels.length != (long) width * height) incomplete = true;
    }

    public boolean incomplete() {
        return incomplete;
    }
}
