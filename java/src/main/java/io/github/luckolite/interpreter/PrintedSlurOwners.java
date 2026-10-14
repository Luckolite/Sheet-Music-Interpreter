// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Binds a proved raw bow to explicit printed heads and written attacks, independently of ties. */
final class PrintedSlurOwners {
    interface Location {
        int owner();

        float x();

        float y();

        int staffIndex();

        int staffCount();
    }

    /** Pixel ownership only; no invented voice, attack or pitch. */
    record GeometryHead(int owner, float x, float y, int staffIndex, int staffCount)
            implements Location {
        GeometryHead {
            if (owner < 0
                    || !Float.isFinite(x + y)
                    || x < 0
                    || y < 0
                    || staffCount < 1
                    || staffIndex < 0
                    || staffIndex >= staffCount)
                throw new IllegalArgumentException("Invalid printed geometry ownership");
        }
    }

    record Head(
            int owner,
            float x,
            float y,
            int staffIndex,
            int staffCount,
            int voice,
            long attack,
            int graceOrder,
            int soundingPitch)
            implements Location {
        Head(
                int owner,
                float x,
                float y,
                int staffIndex,
                int staffCount,
                int voice,
                long attack,
                int soundingPitch) {
            this(owner, x, y, staffIndex, staffCount, voice, attack, 0, soundingPitch);
        }

        Head {
            if (owner < 0
                    || !Float.isFinite(x + y)
                    || x < 0
                    || y < 0
                    || staffCount < 1
                    || staffIndex < 0
                    || staffIndex >= staffCount
                    || voice < 0
                    || attack < 0
                    || graceOrder < -1000000
                    || graceOrder > 1000000
                    || soundingPitch < -1
                    || soundingPitch > 127)
                throw new IllegalArgumentException("Invalid printed head ownership");
        }
    }

    record Tie(int startOwner, int endOwner) {
        Tie {
            if (startOwner < 0 || endOwner < 0 || startOwner == endOwner)
                throw new IllegalArgumentException("Invalid retained tie owners");
        }
    }

    enum Status {
        RESOLVED,
        GEOMETRY_OWNED,
        NO_START_OWNER,
        NO_END_OWNER,
        AMBIGUOUS_START,
        AMBIGUOUS_END,
        DIFFERENT_LANES,
        VOICE_UNRESOLVED,
        REVERSED_CLOCK,
        SAME_OWNER,
        TIE_ALREADY_RETAINED,
        TIE_PITCH_CONFLICT,
        SAME_PITCH_UNRESOLVED,
        UNPITCHED_UNRESOLVED
    }

    record Result(Status status, Optional<ScoreSlurPages.Span> span) {
        Result {
            Objects.requireNonNull(status);
            Objects.requireNonNull(span);
            if ((status == Status.RESOLVED) != span.isPresent())
                throw new IllegalArgumentException("Slur resolution status and source disagree");
        }
    }

    record Binding(Status status, Optional<ScoreSlurPages.Span> span) {
        Binding {
            Objects.requireNonNull(status);
            Objects.requireNonNull(span);
            if ((status == Status.GEOMETRY_OWNED) != span.isPresent())
                throw new IllegalArgumentException("Geometry binding status disagrees with source");
        }
    }

    private record Candidate(Location head, double distance) {}

    private PrintedSlurOwners() {}

    static Result resolve(
            String id,
            int page,
            PrintedSlurContours.Bow bow,
            List<Head> heads,
            Set<Tie> ties,
            byte[] gray,
            int width,
            int height,
            StaffPitchTrack track) {
        Objects.requireNonNull(ties);
        var binding = bind(id, page, bow, heads, gray, width, height, track);
        if (binding.span().isEmpty()) return absent(binding.status());
        var arc = binding.span().orElseThrow();
        var byOwner = new HashMap<Integer, Head>();
        for (var head : heads) byOwner.put(head.owner(), head);
        Head a = byOwner.get(arc.start().noteIndex()), b = byOwner.get(arc.end().noteIndex());
        // A printed slur may connect voices within the same proved physical staff.
        // Unique raw endpoints and written order identify it without assigning a voice.
        int order = Long.compare(a.attack(), b.attack());
        if (order == 0) order = Integer.compare(a.graceOrder(), b.graceOrder());
        if (order >= 0) return absent(Status.REVERSED_CLOCK);
        if (a.soundingPitch() < 0 || b.soundingPitch() < 0)
            return absent(Status.UNPITCHED_UNRESOLVED);
        if (ties.contains(new Tie(a.owner(), b.owner())))
            return absent(
                    a.soundingPitch() == b.soundingPitch()
                            ? Status.TIE_ALREADY_RETAINED
                            : Status.TIE_PITCH_CONFLICT);
        if (a.soundingPitch() == b.soundingPitch()) return absent(Status.SAME_PITCH_UNRESOLVED);
        return new Result(Status.RESOLVED, Optional.of(arc));
    }

    static Binding bind(
            String id,
            int page,
            PrintedSlurContours.Bow bow,
            List<? extends Location> heads,
            byte[] gray,
            int width,
            int height,
            StaffPitchTrack track) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(bow);
        if (id.isBlank() || page < 0)
            throw new IllegalArgumentException("Invalid source arc identity");
        if (gray == null
                || gray.length != (long) width * height
                || width <= 0
                || height <= 0
                || track == null
                || !track.verified())
            throw new IllegalArgumentException(
                    "Printed slur ownership needs its verified source pixels");
        var ids = new HashSet<Integer>();
        for (var head : heads)
            if (!ids.add(head.owner()))
                throw new IllegalArgumentException("Duplicate printed head identity");
        var start = candidates(bow.start(), bow.above(), true, heads, gray, width, height, track);
        var end = candidates(bow.end(), bow.above(), false, heads, gray, width, height, track);
        if (start.isEmpty()) return new Binding(Status.NO_START_OWNER, Optional.empty());
        if (end.isEmpty()) return new Binding(Status.NO_END_OWNER, Optional.empty());
        if (ambiguous(start)) return new Binding(Status.AMBIGUOUS_START, Optional.empty());
        if (ambiguous(end)) return new Binding(Status.AMBIGUOUS_END, Optional.empty());
        Location a = start.get(0).head(), b = end.get(0).head();
        if (a.owner() == b.owner()) return new Binding(Status.SAME_OWNER, Optional.empty());
        if (a.staffIndex() != b.staffIndex() || a.staffCount() != b.staffCount())
            return new Binding(Status.DIFFERENT_LANES, Optional.empty());
        var span =
                new ScoreSlurPages.Span(
                        id,
                        new ScoreSlurPages.Anchor(page, a.owner()),
                        new ScoreSlurPages.Anchor(page, b.owner()),
                        bow.above() ? ScoreSlurSpan.Placement.ABOVE : ScoreSlurSpan.Placement.BELOW,
                        ScoreSlurSpan.LineType.SOLID,
                        List.of(
                                new ScoreSlurSpan.ArcEvidence(
                                        page,
                                        bow.start().x() / (float) width,
                                        bow.start().y() / height,
                                        bow.end().x() / (float) width,
                                        bow.end().y() / height)));
        return new Binding(Status.GEOMETRY_OWNED, Optional.of(span));
    }

    private static Result absent(Status status) {
        return new Result(status, Optional.empty());
    }

    /** Bounded per-column head hints; no synthetic pixels or full-page mask allocation. */
    static PrintedSlurContours.HeadEvidence headEvidence(
            List<? extends Location> heads,
            byte[] gray,
            int width,
            int height,
            StaffPitchTrack track) {
        record Visible(Location head, float gap) {}
        var columns = new ArrayList<List<Visible>>(Collections.nCopies(width, null));
        for (var head : heads) {
            float gap = track.at(head.x())[1];
            if (!headBody(gray, width, height, head, gap)) continue;
            var visible = new Visible(head, gap);
            for (int x = Math.max(0, Math.round(head.x() - gap * .72f));
                    x < width && x <= head.x() + gap * .72f;
                    x++) {
                if (columns.get(x) == null) columns.set(x, new ArrayList<>());
                columns.get(x).add(visible);
            }
        }
        return new PrintedSlurContours.HeadEvidence() {
            private boolean inside(Visible visible, int x, int y) {
                double dx = (x - visible.head().x()) / (visible.gap() * .72);
                double dy = (y - visible.head().y()) / (visible.gap() * .55);
                return dx * dx + dy * dy <= 1;
            }

            public boolean contains(int x, int y) {
                if (x < 0
                        || x >= width
                        || y < 0
                        || y >= height
                        || (gray[y * width + x] & 255) > 205
                        || columns.get(x) == null) return false;
                for (var visible : columns.get(x)) if (inside(visible, x, y)) return true;
                return false;
            }

            public boolean closes(PrintedSlurContours.Point cap, int x, int y, boolean first) {
                if (!contains(x, y)) return false;
                for (var visible : columns.get(x)) {
                    var head = visible.head();
                    if (!inside(visible, x, y)
                            || Math.abs(cap.x() - head.x()) > visible.gap() * .85f
                            || Math.abs(cap.y() - head.y()) > visible.gap() * 2
                            || (first ? cap.x() + 1 < head.x() : cap.x() - 1 > head.x())) continue;
                    return true;
                }
                return false;
            }
        };
    }

    private static boolean ambiguous(List<Candidate> values) {
        return values.size() > 1 && values.get(1).distance() - values.get(0).distance() < .2;
    }

    private static List<Candidate> candidates(
            PrintedSlurContours.Point cap,
            boolean above,
            boolean start,
            List<? extends Location> heads,
            byte[] gray,
            int width,
            int height,
            StaffPitchTrack track) {
        float gap = track.at(cap.x())[1];
        var result = new ArrayList<Candidate>();
        for (var head : heads) {
            if (head.x() >= width || head.y() >= height) continue;
            double dx = Math.abs(head.x() - cap.x()) / gap;
            double clearance = (above ? head.y() - cap.y() : cap.y() - head.y()) / gap;
            if (dx > 1.1 || clearance < -.2 || clearance > 8.5) continue;
            boolean shaft = shaftSupports(cap, head, above, gray, width, height, gap);
            boolean headEdge =
                    dx <= .85
                            && clearance <= 2
                            && (start ? cap.x() >= head.x() : cap.x() <= head.x())
                            && headBody(gray, width, height, head, gap);
            if (dx > .45 && !shaft && !headEdge) continue;
            // A connected source shaft is stronger ownership than a neighboring dense head.
            double score = shaft ? -.35 + dx * .15 + clearance * .015 : dx + clearance * .03;
            result.add(new Candidate(head, score));
        }
        result.sort(
                Comparator.comparingDouble(Candidate::distance)
                        .thenComparingInt(c -> c.head().owner()));
        return result;
    }

    private static boolean headBody(byte[] gray, int width, int height, Location head, float gap) {
        int rows = 0, minimum = Math.max(3, (int) Math.ceil(gap * .25f));
        for (int y = Math.max(0, Math.round(head.y() - gap * .4f));
                y < height && y <= head.y() + gap * .4f;
                y++) {
            int columns = 0;
            for (int x = Math.max(0, Math.round(head.x() - gap * .65f));
                    x < width && x <= head.x() + gap * .65f;
                    x++) if ((gray[y * width + x] & 255) < 165) columns++;
            if (columns >= minimum) rows++;
        }
        return rows >= minimum;
    }

    private static boolean shaftSupports(
            PrintedSlurContours.Point cap,
            Location head,
            boolean above,
            byte[] gray,
            int width,
            int height,
            float gap) {
        int direction = above ? -1 : 1;
        float edge = head.x() + (above ? .6f : -.6f) * gap;
        if (Math.abs(edge - cap.x()) > gap * .4f) return false;
        float distance = direction * (cap.y() - head.y()) - gap;
        if (distance < gap * .8f) return false;
        int first = Math.round(head.y() + direction * gap * .15f);
        int last = Math.round(head.y() + direction * distance);
        for (int x = Math.max(0, Math.round(edge - gap * .2f));
                x < width && x <= edge + gap * .2f;
                x++) {
            int total = 0, hits = 0;
            for (int y = Math.min(first, last); y <= Math.max(first, last); y++) {
                if (y < 0 || y >= height) {
                    total = 0;
                    break;
                }
                total++;
                if ((gray[y * width + x] & 255) < 165) hits++;
            }
            if (total >= gap * .75f && hits >= total * .9f) return true;
        }
        return false;
    }
}
