// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Finite adjacent-row evidence for elevated UP marks, without a broader distance cap. */
final class ElevatedBowSupport {
    private static final long LIMIT = 64_000_000L;
    private final byte[] gray;
    private final int width, height;
    private final List<NoteArticulationDetector.Anchor> anchors;
    private final List<ScoreNoteEvent> events;
    private final List<PlayingTechniqueDetector.Staff> staffs;
    private final List<MeasureRegion> measures;
    private final Map<Integer, Integer> predecessors = new HashMap<>();
    private final Map<Column, Boolean> envelopes = new HashMap<>();
    private final Map<Column, Optional<float[]>> rails = new HashMap<>();
    private long work;

    private record Column(int frame, int x) {}

    ElevatedBowSupport(
            byte[] gray,
            int width,
            int height,
            List<NoteArticulationDetector.Anchor> anchors,
            List<ScoreNoteEvent> events,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures) {
        this.gray = gray;
        this.width = width;
        this.height = height;
        this.anchors = anchors;
        this.events = events;
        this.staffs = staffs;
        this.measures = measures;
    }

    boolean reserve(long amount) {
        if (amount < 0 || amount > LIMIT - work) return false;
        work += amount;
        return true;
    }

    private boolean valid(PlayingTechniqueDetector.Staff s) {
        return s != null
                && Float.isFinite(s.top())
                && Float.isFinite(s.bottom())
                && Float.isFinite(s.gap())
                && s.top() >= 0
                && s.bottom() < height
                && s.top() < s.bottom()
                && s.gap() >= 6
                && s.gap() <= Math.min(width, height) / 8f;
    }

    private int predecessor(int frame) {
        if (predecessors.containsKey(frame)) return predecessors.get(frame);
        if (staffs.size() > 4096 || !reserve(24L * staffs.size() + 64)) return -1;
        var lower = staffs.get(frame);
        int result = -1;
        if (valid(lower) && lower.index() == 0 && lower.count() == 1) {
            for (int j = 0; j < staffs.size(); j++) {
                var s = staffs.get(j);
                if (!valid(s)) {
                    result = -1;
                    break;
                }
                if (j == frame) continue;
                if (s.top() <= lower.bottom() && s.bottom() >= lower.top()) {
                    result = -1;
                    break;
                }
                if (s.bottom() < lower.top()
                        && (result < 0 || s.bottom() > staffs.get(result).bottom())) result = j;
            }
            if (result >= 0) {
                var upper = staffs.get(result);
                if (upper.index() != 0 || upper.count() != 1) result = -1;
                else
                    for (int j = 0; j < staffs.size(); j++) {
                        if (j == result) continue;
                        var s = staffs.get(j);
                        if (s.top() <= upper.bottom() && s.bottom() >= upper.top()) {
                            result = -1;
                            break;
                        }
                    }
            }
        }
        predecessors.put(frame, result);
        return result;
    }

    private boolean interval(int top, int bottom, float upperBottom, float lowerTop) {
        return upperBottom < lowerTop
                && top > upperBottom
                && bottom < lowerTop
                && top > (upperBottom + lowerTop) * .5f;
    }

    boolean possible(int left, int top, int right, int bottom, int owner) {
        if (!reserve(48)) return false;
        if (measures == null
                || measures.isEmpty()
                || measures.size() > 4096
                || owner < 0
                || owner >= anchors.size()) return false;
        int lower = anchors.get(owner).staff();
        if (lower < 0 || lower >= staffs.size()) return false;
        int upper = predecessor(lower);
        return upper >= 0
                && interval(top, bottom, staffs.get(upper).bottom(), staffs.get(lower).top());
    }

    // The measure must belong to an actual same-call anchor on this exact physical frame.
    // No metadata title/part alias, padding, or inferred leading/trailing measure is used.
    private boolean envelope(Column key) {
        if (envelopes.containsKey(key)) return envelopes.get(key);
        if (!reserve(64L * anchors.size() + 64)) return false;
        var frame = staffs.get(key.frame());
        boolean found = false;
        for (int i = 0; i < anchors.size(); i++) {
            var a = anchors.get(i);
            var e = events.get(i);
            if (a.staff() != key.frame()
                    || e.crossStaffBeam()
                    || e.kind() != ScoreNoteEvent.Kind.PITCHED
                    || e.staffIndex() != frame.index()
                    || e.staffCount() != frame.count()
                    || e.measureIndex() < 0
                    || e.measureIndex() >= measures.size()) continue;
            var m = measures.get(e.measureIndex());
            float x = (m.left() + (m.right() - m.left()) * e.positionInMeasure()) * width;
            float y = e.pageY() * height;
            float cy = (frame.top() + frame.bottom()) * .5f;
            if (!Float.isFinite(m.left())
                    || !Float.isFinite(m.right())
                    || !Float.isFinite(m.top())
                    || !Float.isFinite(m.bottom())
                    || m.left() < 0
                    || m.right() > 1
                    || m.left() >= m.right()
                    || m.top() < 0
                    || m.bottom() > 1
                    || m.top() >= m.bottom()
                    || !Float.isFinite(x)
                    || !Float.isFinite(y)
                    || Float.floatToIntBits(x) != Float.floatToIntBits(a.x())
                    || Float.floatToIntBits(y) != Float.floatToIntBits(a.y())
                    || key.x() < m.left() * width
                    || key.x() > m.right() * width
                    || x < m.left() * width
                    || x > m.right() * width
                    || y < m.top() * height
                    || y > m.bottom() * height
                    || cy < m.top() * height
                    || cy > m.bottom() * height) continue;
            found = true;
            break;
        }
        envelopes.put(key, found);
        return found;
    }

    private float[] rules(Column key) {
        if (!reserve(16)) return null;
        if (rails.containsKey(key)) return rails.get(key).orElse(null);
        if (!envelope(key)) return null;
        var s = staffs.get(key.frame());
        // Covers strict plus narrow/wide faint fallback: 3 x 9 slope crops and
        // 3 shifted five-rule scans per crop, including bilateral contrast scans.
        // Both dimensions overbound every unchanged helper crop; debit before allocation.
        long w = (long) Math.ceil(11d * s.gap()) + 3;
        long h = (long) Math.ceil(7d * s.gap()) + 3;
        if (!reserve(512L * w * h + 4096)) return null;
        var r =
                PrintedDirectionStaff.printedRules(
                        gray, width, height, key.x(), s.bottom(), s.gap());
        if (r == null
                || r.length < 2
                || !Float.isFinite(r[0])
                || !Float.isFinite(r[1])
                || Math.abs(r[0] - s.bottom()) > s.gap() * 1.5f
                || Math.abs(r[1] - s.gap()) > s.gap() * .15f) r = null;
        rails.put(key, Optional.ofNullable(r));
        return r;
    }

    boolean owns(int left, int top, int right, int bottom, int owner) {
        if (!reserve(32)) return false;
        if (!possible(left, top, right, bottom, owner)) return false;
        var a = anchors.get(owner);
        int lower = a.staff(), upper = predecessor(lower);
        int glyphX = Math.round((left + right) * .5f), anchorX = Math.round(a.x());
        var lg = rules(new Column(lower, glyphX));
        var la = rules(new Column(lower, anchorX));
        var ug = rules(new Column(upper, glyphX));
        var ua = rules(new Column(upper, anchorX));
        return lg != null
                && la != null
                && ug != null
                && ua != null
                && interval(top, bottom, ug[0], lg[0] - lg[1] * 4)
                && interval(top, bottom, ua[0], la[0] - la[1] * 4);
    }
}
