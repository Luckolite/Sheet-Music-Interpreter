// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Attach isolated ornaments to one written note, respecting staff and measure geometry. */
final class PortableNoteOrnaments {
    /** Right and bottom are exclusive, matching Android crop bounds. */
    static final class Bounds {
        int left, top, right, bottom;

        Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        Bounds(Bounds other) {
            this(other.left, other.top, other.right, other.bottom);
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        float exactCenterX() {
            return (left + right) * .5f;
        }

        float exactCenterY() {
            return (top + bottom) * .5f;
        }

        void union(Bounds other) {
            left = Math.min(left, other.left);
            top = Math.min(top, other.top);
            right = Math.max(right, other.right);
            bottom = Math.max(bottom, other.bottom);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Bounds b
                    && left == b.left
                    && top == b.top
                    && right == b.right
                    && bottom == b.bottom;
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, top, right, bottom);
        }
    }

    record Anchor(float x, float y, float gap, int staff, int measure) {}

    record Found(Bounds bounds, int marks, int noteIndex) {}

    private PortableNoteOrnaments() {}

    static List<ScoreNoteEvent> apply(
            PortableOrnamentGlyphs recognizer,
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            List<PlayingTechniqueDetector.Word> trills) {
        var staffs =
                ScoreDynamicsDetector.alignStaffs(
                        OmrScoreInterpreter.techniqueStaffs(labels, gray, w, h, measures),
                        notes,
                        h);
        List<Anchor> anchors = new ArrayList<>();
        for (var note : notes) {
            var region = measures.get(note.measureIndex());
            float x =
                    (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                            * w;
            int best = -1;
            float distance = Float.MAX_VALUE;
            for (int i = 0; i < staffs.size(); i++) {
                var staff = staffs.get(i);
                if (staff.index() != note.staffIndex()) continue;
                float d = Math.abs(note.pageY() * h - (staff.top() + staff.bottom()) * .5f);
                if (d < distance) {
                    distance = d;
                    best = i;
                }
            }
            anchors.add(
                    new Anchor(
                            x,
                            note.pageY() * h,
                            best < 0 ? 12 : staffs.get(best).gap(),
                            best,
                            note.measureIndex()));
        }
        int[] marks = new int[notes.size()];
        for (var found : detect(recognizer, gray, w, h, staffs, anchors, trills))
            marks[found.noteIndex()] |= found.marks();
        var slideStaffs =
                staffs.stream()
                        .map(s -> new NoteSlideDetector.Staff(s.top(), s.bottom(), s.gap()))
                        .toList();
        var slideHeads =
                anchors.stream()
                        .map(n -> new NoteSlideDetector.Head(n.x, n.y, n.gap, n.staff, n.measure))
                        .toList();
        // Share only preparation; the two detectors own separate mutable working rasters.
        byte[] sharedClean =
                gray != null && gray.length == (long) w * h && slideHeads.size() >= 2
                        ? NoteSlideDetector.removeStaffLines(gray, w, h, slideStaffs)
                        : null;
        for (var gliss :
                sharedClean == null
                        ? WaveGlissDetector.detect(gray, w, h, slideStaffs, slideHeads)
                        : WaveGlissDetector.detectWithRemovedStaffLines(
                                w, h, slideHeads, sharedClean.clone()))
            if (marks[gliss.sourceIndex()] == 0
                    && NoteOrnament.type(notes.get(gliss.sourceIndex()).articulations()) == 0)
                marks[gliss.sourceIndex()] = NoteOrnament.GLISSANDO;
        for (var slide :
                sharedClean == null
                        ? NoteSlideDetector.detect(gray, w, h, slideStaffs, slideHeads)
                        : NoteSlideDetector.detectWithRemovedStaffLines(
                                gray, w, h, slideStaffs, slideHeads, sharedClean))
            if (marks[slide.noteIndex()] == 0)
                marks[slide.noteIndex()] =
                        NoteOrnament.SLIDE
                                | (slide.direction() < 0 ? NoteOrnament.FROM_ABOVE : 0)
                                | (slide.connected() ? NoteOrnament.FROM_PREVIOUS : 0);
        boolean[] textHeads = new boolean[notes.size()];
        for (var word : trills)
            if (word.text().equals("port") && ScoreDynamicsDetector.containsInk(word, gray, w, h)) {
                float left = word.left() * w,
                        right = word.right() * w,
                        cy = (word.top() + word.bottom()) * .5f * h;
                int owner = -1;
                double best = Double.MAX_VALUE;
                for (int i = 0; i < anchors.size(); i++) {
                    var n = anchors.get(i);
                    if (n.staff < 0
                            || n.x < right - n.gap * .2f
                            || n.x - right > n.gap * 6
                            || Math.abs(n.y - cy) > n.gap * 3) continue;
                    double distance = Math.abs(n.x - right) + Math.abs(n.y - cy);
                    if (distance < best) {
                        best = distance;
                        owner = i;
                    }
                }
                if (owner < 0) continue;
                var n = anchors.get(owner);
                Anchor prior = null;
                for (var a : anchors)
                    if (a.staff == n.staff
                            && a.measure == n.measure
                            && a.x < left + n.gap * .3f
                            && a.x < n.x - n.gap
                            && (prior == null || a.x > prior.x)) prior = a;
                if (prior != null
                        && Math.abs(prior.y - n.y) > n.gap * .25f
                        && marks[owner] == 0
                        && NoteSlideDetector.connection(
                                gray,
                                w,
                                h,
                                new NoteSlideDetector.Head(
                                        prior.x, prior.y, prior.gap, prior.staff, prior.measure),
                                new NoteSlideDetector.Head(n.x, n.y, n.gap, n.staff, n.measure))) {
                    marks[owner] =
                            NoteOrnament.SLIDE
                                    | NoteOrnament.FROM_PREVIOUS
                                    | (prior.y < n.y ? NoteOrnament.FROM_ABOVE : 0);
                    // The closed italic o can itself look like an open notehead to the model.
                    // Remove only heads inside this confirmed, note-owned instruction word.
                    for (int i = 0; i < anchors.size(); i++) {
                        var a = anchors.get(i);
                        if (i != owner
                                && a.staff == n.staff
                                && a.x > left
                                && a.x < right
                                && a.y > word.top() * h
                                && a.y < word.bottom() * h) textHeads[i] = true;
                    }
                }
            }
        for (var found : CrossRowPortamento.find(gray, w, h, staffs, measures, notes, trills)) {
            int target = found.targetIndex();
            if (marks[target] == 0
                    && NoteOrnament.type(notes.get(target).articulations()) == NoteOrnament.NONE)
                marks[target] =
                        NoteOrnament.SLIDE
                                | NoteOrnament.FROM_PREVIOUS
                                | (found.fromAbove() ? NoteOrnament.FROM_ABOVE : 0);
        }
        List<ScoreNoteEvent> result = new ArrayList<>(notes.size());
        for (int i = 0; i < notes.size(); i++)
            if (!textHeads[i])
                result.add(notes.get(i).withArticulations(notes.get(i).articulations() | marks[i]));
        return List.copyOf(result);
    }

    static List<Found> detect(
            PortableOrnamentGlyphs recognizer,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<Anchor> notes) {
        return detect(recognizer, gray, width, height, staffs, notes, List.of());
    }

    static List<Found> detect(
            PortableOrnamentGlyphs recognizer,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<Anchor> notes,
            List<PlayingTechniqueDetector.Word> trills) {
        var boxes = boxes(gray, width, height, staffs);
        List<Found> found = new ArrayList<>();
        var repeatedTrills = new PageTrillEvidence(recognizer, gray, width, height, boxes, trills);
        Set<Bounds> textTrills = new HashSet<>();
        for (var word : trills)
            if (word.text().equals("tr")
                    && ScoreDynamicsDetector.containsInk(word, gray, width, height)) {
                Bounds r =
                        new Bounds(
                                Math.max(0, Math.round(word.left() * width)),
                                Math.max(0, Math.round(word.top() * height)),
                                Math.min(width, Math.round(word.right() * width)),
                                Math.min(height, Math.round(word.bottom() * height)));
                if (r.width() > 0 && r.height() > 0) {
                    boxes.add(r);
                    textTrills.add(r);
                }
            }
        for (Bounds box : boxes) {
            // Ledger lines through an open head can resemble a compressed mordent.
            // A component already containing a written head cannot ornament another note.
            boolean noteInk = false;
            for (var note : notes)
                if (note.x >= box.left
                        && note.x < box.right
                        && note.y >= box.top
                        && note.y < box.bottom) {
                    noteInk = true;
                    break;
                }
            if (noteInk) continue;
            var match =
                    textTrills.contains(box) || repeatedTrills.confirmed(box)
                            ? new PortableOrnamentGlyphs.Match(NoteOrnament.TRILL, 1, 1)
                            : recognizer.match(gray, width, box);
            if (!match.accepted() && repeatedTrills.recognizes(gray, width, box, match))
                match = new PortableOrnamentGlyphs.Match(NoteOrnament.TRILL, 1, 1);
            if (!match.accepted() && match.kind() == NoteOrnament.TRILL && match.score() > .25f) {
                // In small italic print the r's terminal and the period can be tiny detached
                // islands. Recheck the complete word without relaxing the glyph threshold.
                Bounds joined =
                        inkBounds(
                                gray,
                                width,
                                new Bounds(
                                        Math.max(0, box.left - 1),
                                        Math.max(0, box.top - 1),
                                        Math.min(width, box.right + Math.round(box.height() * .7f)),
                                        Math.min(height, box.bottom + 2)));
                if (joined != null) {
                    var full = recognizer.match(gray, width, joined);
                    if (full.kind() == NoteOrnament.TRILL && full.accepted()) {
                        box = joined;
                        match = full;
                    }
                }
            }
            if (!match.accepted()) continue;
            int owner = -1;
            double best = Double.MAX_VALUE;
            boolean delayed = false;
            for (int i = 0; i < notes.size(); i++) {
                var n = notes.get(i);
                if (n.staff < 0) continue;
                var s = staffs.get(n.staff);
                // Ornament symbols belong above the staff, not in lyrics/dynamics below it.
                if (box.bottom > s.top() + s.gap() * .1 || box.bottom < s.top() - s.gap() * 5.5)
                    continue;
                float dx = (box.exactCenterX() - n.x) / n.gap,
                        dy = (n.y - box.exactCenterY()) / n.gap;
                boolean turn =
                        match.kind() == NoteOrnament.TURN
                                || match.kind() == NoteOrnament.INVERTED_TURN;
                boolean between = turn && dx > 1 && dx < 4;
                if (Math.abs(dx) > 1.25 && !between || dy < .65 || dy > 10) continue;
                if (between) {
                    boolean later = false;
                    for (var other : notes)
                        if (other.staff == n.staff
                                && other.measure == n.measure
                                && other.x > n.x + n.gap * .5
                                && other.x < box.exactCenterX() + n.gap * .6) later = true;
                    if (later) continue;
                }
                double distance = Math.abs(dx) * 3 + Math.abs(dy) * .3;
                if (distance < best) {
                    best = distance;
                    owner = i;
                    delayed = between;
                }
            }
            if (owner < 0) continue;
            var anchor = notes.get(owner);
            int marks = match.kind() | (delayed ? NoteOrnament.DELAYED : 0);
            // Accidentals beside/above/below an ornament modify its auxiliary pitch only.
            for (Bounds other : boxes) {
                if (other == box
                        || Math.abs(other.exactCenterX() - box.exactCenterX()) > anchor.gap * .9)
                    continue;
                boolean upper = other.bottom <= box.top;
                boolean lower = other.top >= box.bottom;
                if (!upper && !lower) continue;
                float distance = upper ? box.top - other.bottom : other.top - box.bottom;
                if (distance > anchor.gap * 1.5 || other.height() > anchor.gap * 2.2) continue;
                var accidental = recognizer.accidental(gray, width, other);
                if (accidental.accepted())
                    marks = NoteOrnament.withAccidental(marks, upper, accidental.kind() - 2);
            }
            // At small print sizes a sharp's stems/crossbars can rasterize as
            // separate components, each too narrow to be a standalone symbol.
            // Verify their combined shape in the bounded auxiliary-sign slot.
            for (boolean upper : new boolean[] {true, false})
                if (NoteOrnament.accidental(marks, upper) == ScoreNoteEvent.ACCIDENTAL_FROM_KEY) {
                    float gap = anchor.gap;
                    var staff = staffs.get(anchor.staff);
                    Bounds slot =
                            new Bounds(
                                    Math.max(0, Math.round(box.exactCenterX() - gap * .65f)),
                                    Math.max(
                                            0,
                                            Math.round(
                                                    upper ? box.top - gap * 2.5f : box.bottom + 1)),
                                    Math.min(width, Math.round(box.exactCenterX() + gap * .65f)),
                                    Math.min(
                                            height,
                                            Math.round(
                                                    upper
                                                            ? box.top - 1
                                                            : Math.min(
                                                                    staff.top() - gap * .15f,
                                                                    box.bottom + gap * 2.5f))));
                    Bounds ink = inkBounds(gray, width, slot);
                    if (ink == null || ink.height() < gap * .5f || ink.height() > gap * 2.2f)
                        continue;
                    var accidental = recognizer.accidental(gray, width, ink);
                    if (accidental.accepted())
                        marks = NoteOrnament.withAccidental(marks, upper, accidental.kind() - 2);
                }
            boolean duplicate = false;
            for (var prior : found) if (prior.noteIndex() == owner) duplicate = true;
            if (!duplicate) found.add(new Found(box, marks, owner));
        }
        return List.copyOf(found);
    }

    private static Bounds inkBounds(byte[] gray, int width, Bounds area) {
        int left = area.right, right = area.left, top = area.bottom, bottom = area.top;
        for (int y = area.top; y < area.bottom; y++)
            for (int x = area.left; x < area.right; x++)
                if ((gray[y * width + x] & 255) < 150) {
                    left = Math.min(left, x);
                    right = Math.max(right, x + 1);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y + 1);
                }
        return right <= left ? null : new Bounds(left, top, right, bottom);
    }

    static List<Bounds> boxes(
            byte[] gray, int w, int h, List<PlayingTechniqueDetector.Staff> staffs) {
        boolean[] seen = new boolean[gray.length];
        int[] queue = new int[gray.length];
        List<Bounds> parts = new ArrayList<>();
        for (int p = 0; p < gray.length; p++) {
            if (seen[p] || (gray[p] & 255) >= 150) continue;
            int start = 0, end = 1;
            queue[0] = p;
            seen[p] = true;
            int l = p % w, r = l, t = p / w, b = t;
            while (start < end) {
                int at = queue[start++], x = at % w, y = at / w;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && (gray[next] & 255) < 150) {
                            seen[next] = true;
                            queue[end++] = next;
                        }
                    }
            }
            if (end < 5) continue;
            for (var s : staffs)
                if (b <= s.top() + s.gap() * .1
                        && b >= s.top() - s.gap() * 7
                        && r - l <= s.gap() * 4.8
                        && b - t <= s.gap() * 2.8
                        && b - t >= s.gap() * .3
                        && r - l >= s.gap() * .15) {
                    parts.add(new Bounds(l, t, r + 1, b + 1));
                    break;
                }
        }
        // The letters in tr may be separate components. Merge only letters on the same
        // baseline; dots and vertically placed auxiliary accidentals remain separate.
        parts.sort(Comparator.comparingInt(r -> r.left));
        List<Bounds> joined = new ArrayList<>();
        for (var b : parts) {
            Bounds prior = null;
            for (var a : joined)
                if (b.left >= a.left
                        && b.left - a.right < Math.min(a.height(), b.height()) * .32
                        && Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top)
                                > Math.min(a.height(), b.height()) * .55) prior = a;
            if (prior == null) joined.add(new Bounds(b));
            else prior.union(b);
        }
        return joined;
    }
}
