// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

final class VerifiedCourtesyKeyTail {
    static ScorePageInterpretation apply(
            ScorePageInterpretation s, byte[] labels, byte[] gray, int width, int height) {
        if (s == null || s.measures().size() < 3) return s;
        var staffs =
                ScoreDynamicsDetector.alignStaffs(
                        OmrScoreInterpreter.techniqueStaffs(
                                labels, gray, width, height, s.measures()),
                        s.notes(),
                        height);
        for (int cut = s.measures().size() - 2; cut >= 1; cut--) {
            final int index = cut;
            if (s.keyChanges().stream().noneMatch(k -> k.measureIndex() == index)) continue;
            if (s.notes().stream().anyMatch(n -> n.measureIndex() == index)
                    || s.rests().stream().anyMatch(r -> r.measureIndex() == index)
                    || s.tempoChanges().stream().anyMatch(t -> t.measureIndex() == index)
                    || s.techniqueChanges().stream().anyMatch(t -> t.measureIndex() == index)
                    || s.dynamicChanges().stream()
                            .anyMatch(
                                    d -> d.measureIndex() == index || d.endMeasureIndex() == index)
                    || s.expressiveEvents().stream()
                            .anyMatch(
                                    e ->
                                            e.start()
                                                            .filter(a -> a.measureIndex() == index)
                                                            .isPresent()
                                                    || e.end()
                                                            .filter(a -> a.measureIndex() == index)
                                                            .isPresent())
                    || s.playbackDirections().stream()
                            .anyMatch(
                                    d ->
                                            (d.measureBoundary() == index
                                                            && d.details().quarterBeatOffset() != 0)
                                                    || d.details()
                                                            .end()
                                                            .filter(
                                                                    a ->
                                                                            a.measureIndex()
                                                                                            == index
                                                                                    && a
                                                                                                    .quarterBeatOffset()
                                                                                            != 0)
                                                            .isPresent())) continue;
            var r = s.measures().get(cut);
            var prev = s.measures().get(cut - 1);
            var next = s.measures().get(cut + 1);
            if (Math.abs(r.top() - prev.top()) > (r.bottom() - r.top()) * .25f
                    || next.top() <= r.bottom()
                    || r.left() <= prev.left()) continue;
            // A conflicting signature at the receiving bar is not proven to be this courtesy.
            var keys = s.keyChanges();
            boolean conflict = false;
            for (var k : keys)
                if (k.measureIndex() == cut)
                    for (var other : keys)
                        if (other.measureIndex() == cut + 1 && other.fifths() != k.fifths())
                            conflict = true;
            if (conflict) continue;
            int verified = 0, owners = 0;
            for (var staff : staffs) {
                float cy = (staff.top() + staff.bottom()) * .5f / height;
                if (cy < r.top() || cy > r.bottom()) continue;
                owners++;
                if (CourtesyKeyTailInk.matches(
                        labels,
                        gray,
                        width,
                        height,
                        Math.round(r.left() * width),
                        Math.round(r.right() * width),
                        Math.round(staff.top()),
                        staff.gap())) verified++;
            }
            if (owners > 0 && verified == owners) s = CourtesyMeasureRemap.remove(s, cut);
        }
        return s;
    }
}
