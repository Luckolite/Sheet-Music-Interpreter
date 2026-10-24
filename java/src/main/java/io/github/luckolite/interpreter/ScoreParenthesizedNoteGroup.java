// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** One print-only pair and its exact ordered members; it implies no ghost-note playback. */
public final class ScoreParenthesizedNoteGroup {
    public static final String SOURCE = "printed-parenthesized-note-group";
    private static final String TARGET = "parenthesis-group-v1:", PAIR = "parenthesis-pair-v1:";

    public record Member(int measure, int staff, int count, int xBits, int step, int yBits) {
        public Member {
            float x = Float.intBitsToFloat(xBits), y = Float.intBitsToFloat(yBits);
            if (measure < 0
                    || count < 1
                    || staff < 0
                    || staff >= count
                    || !Float.isFinite(x)
                    || x < 0
                    || x > 1
                    || !Float.isFinite(y)
                    || y < 0
                    || y > 1) throw new IllegalArgumentException("Invalid group member");
        }

        String token() {
            return measure
                    + ","
                    + staff
                    + ","
                    + count
                    + ","
                    + hex(xBits)
                    + ","
                    + step
                    + ","
                    + hex(yBits);
        }

        Member offset(int amount) {
            return new Member(Math.addExact(measure, amount), staff, count, xBits, step, yBits);
        }

        static Member of(ScoreNoteEvent n) {
            if (n.kind() != ScoreNoteEvent.Kind.PITCHED
                    || (n.articulations() & NoteOrnament.GRACE) != 0)
                throw new IllegalArgumentException("Group needs ordinary pitched members");
            return new Member(
                    n.measureIndex(),
                    n.staffIndex(),
                    n.staffCount(),
                    Float.floatToRawIntBits(n.positionInMeasure()),
                    n.staffStep(),
                    Float.floatToRawIntBits(n.pageY()));
        }
    }

    public record Pair(
            List<Member> members,
            float leftX,
            float leftY,
            float rightX,
            float rightY,
            float leftHeight,
            float rightHeight) {
        public Pair {
            members = List.copyOf(members);
            if (members.size() < 2
                    || members.size() > 64
                    || !Float.isFinite(leftX + leftY + rightX + rightY + leftHeight + rightHeight)
                    || leftX < -2
                    || leftX > -.45f
                    || rightX < .45f
                    || rightX > 2
                    || Math.abs(leftY) > .65f
                    || Math.abs(rightY) > .65f
                    || leftHeight < 1.1f
                    || leftHeight > 2.6f
                    || rightHeight < 1.1f
                    || rightHeight > 2.6f)
                throw new IllegalArgumentException("Invalid printed parenthesis pair");
            var first = members.get(0);
            float last = -1;
            for (var member : members) {
                float x = Float.intBitsToFloat(member.xBits());
                if (member.measure() != first.measure()
                        || member.staff() != first.staff()
                        || member.count() != first.count()
                        || x <= last)
                    throw new IllegalArgumentException(
                            "Group members must be ordered in one source bar/lane");
                last = x;
            }
        }

        public String target() {
            return TARGET + members.get(0).token();
        }

        public String qualifier() {
            return PAIR
                    + bits(leftX)
                    + ":"
                    + bits(leftY)
                    + ":"
                    + bits(rightX)
                    + ":"
                    + bits(rightY)
                    + ":"
                    + bits(leftHeight)
                    + ":"
                    + bits(rightHeight)
                    + ":"
                    + String.join("/", members.stream().map(Member::token).toList());
        }

        Pair offset(int amount) {
            return new Pair(
                    members.stream().map(m -> m.offset(amount)).toList(),
                    leftX,
                    leftY,
                    rightX,
                    rightY,
                    leftHeight,
                    rightHeight);
        }
    }

    private ScoreParenthesizedNoteGroup() {}

    private static String hex(int bits) {
        return String.format(Locale.ROOT, "%08x", bits);
    }

    private static String bits(float value) {
        return hex(Float.floatToRawIntBits(value));
    }

    private static int unhex(String value) {
        if (!value.matches("[0-9a-f]{8}")) throw new IllegalArgumentException();
        return (int) Long.parseLong(value, 16);
    }

    private static Member member(String value) {
        String[] p = value.split(",", -1);
        if (p.length != 6) throw new IllegalArgumentException();
        var member =
                new Member(
                        Integer.parseInt(p[0]),
                        Integer.parseInt(p[1]),
                        Integer.parseInt(p[2]),
                        unhex(p[3]),
                        Integer.parseInt(p[4]),
                        unhex(p[5]));
        if (!member.token().equals(value))
            throw new IllegalArgumentException("Noncanonical member");
        return member;
    }

    public static Optional<Pair> decode(String qualifier) {
        if (qualifier == null || qualifier.length() > 4096 || !qualifier.startsWith(PAIR))
            return Optional.empty();
        try {
            String[] p = qualifier.substring(PAIR.length()).split(":", -1);
            if (p.length != 7) return Optional.empty();
            String[] list = p[6].split("/", -1);
            if (list.length < 2 || list.length > 64) return Optional.empty();
            var members = Arrays.stream(list).map(ScoreParenthesizedNoteGroup::member).toList();
            var pair =
                    new Pair(
                            members,
                            Float.intBitsToFloat(unhex(p[0])),
                            Float.intBitsToFloat(unhex(p[1])),
                            Float.intBitsToFloat(unhex(p[2])),
                            Float.intBitsToFloat(unhex(p[3])),
                            Float.intBitsToFloat(unhex(p[4])),
                            Float.intBitsToFloat(unhex(p[5])));
            return pair.qualifier().equals(qualifier) ? Optional.of(pair) : Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    static OptionalInt targetMeasure(String target) {
        if (target == null || !target.startsWith(TARGET) || target.length() > 256)
            return OptionalInt.empty();
        try {
            return OptionalInt.of(member(target.substring(TARGET.length())).measure());
        } catch (IllegalArgumentException invalid) {
            return OptionalInt.empty();
        }
    }

    static String offsetTarget(String target, int amount) {
        if (targetMeasure(target).isEmpty()) return target;
        return TARGET + member(target.substring(TARGET.length())).offset(amount).token();
    }

    static String offsetQualifier(String qualifier, int amount) {
        var decoded = decode(qualifier);
        return decoded.isEmpty() ? qualifier : decoded.get().offset(amount).qualifier();
    }

    /** Partial selection, a duplicate slot, a changed note, or a grace reinterpretation refuses the pair. */
    static Optional<List<Integer>> owners(Pair pair, List<ScoreNoteEvent> notes) {
        var found = new ArrayList<Integer>();
        for (var wanted : pair.members()) {
            int owner = -1;
            for (int i = 0; i < notes.size(); i++) {
                Member candidate;
                try {
                    candidate = Member.of(notes.get(i));
                } catch (IllegalArgumentException invalid) {
                    continue;
                }
                if (candidate.equals(wanted)) {
                    if (owner >= 0) return Optional.empty();
                    owner = i;
                }
            }
            if (owner < 0) return Optional.empty();
            found.add(owner);
        }
        var expected = new HashSet<>(pair.members());
        var first = pair.members().get(0);
        var last = pair.members().get(pair.members().size() - 1);
        float left = Float.intBitsToFloat(first.xBits()),
                right = Float.intBitsToFloat(last.xBits());
        for (var note : notes)
            if (note.measureIndex() == first.measure()
                    && note.staffIndex() == first.staff()
                    && note.staffCount() == first.count()
                    && note.positionInMeasure() >= left
                    && note.positionInMeasure() <= right) {
                try {
                    if (!expected.contains(Member.of(note))) return Optional.empty();
                } catch (IllegalArgumentException invalid) {
                    return Optional.empty();
                }
            }
        return Optional.of(List.copyOf(found));
    }

    public static Optional<List<Integer>> owners(
            ScoreExpressiveEvent mark, List<ScoreNoteEvent> notes) {
        if (mark.kind() != ScoreExpressiveEvent.Kind.PARENTHESIZED_NOTE_GROUP
                || mark.evidence().stream()
                        .noneMatch(
                                e ->
                                        e.sourceId().equals(SOURCE)
                                                && e.staffIndex() == mark.staffIndex()
                                                && e.staffCount() == mark.staffCount()))
            return Optional.empty();
        var pair = decode(mark.qualifierText());
        if (pair.isEmpty() || !pair.get().target().equals(mark.targetEventId().orElse("")))
            return Optional.empty();
        return owners(pair.get(), notes);
    }

    static Pair capture(
            PrintedParenthesisGroups.Group group,
            List<ScoreNoteEvent> notes,
            List<? extends PrintedSlurOwners.Location> physicalHeads) {
        var members = group.members().stream().map(i -> Member.of(notes.get(i))).toList();
        var a = physicalHeads.stream().filter(h -> h.owner() == group.members().get(0)).toList();
        var b =
                physicalHeads.stream()
                        .filter(h -> h.owner() == group.members().get(group.members().size() - 1))
                        .toList();
        if (a.size() != 1 || b.size() != 1)
            throw new IllegalArgumentException("Ambiguous physical endpoint");
        var l = group.left();
        var r = group.right();
        return new Pair(
                members,
                (l.centerX() - a.get(0).x()) / l.gap(),
                (l.centerY() - a.get(0).y()) / l.gap(),
                (r.centerX() - b.get(0).x()) / r.gap(),
                (r.centerY() - b.get(0).y()) / r.gap(),
                (l.bottom() - l.top() + 1) / l.gap(),
                (r.bottom() - r.top() + 1) / r.gap());
    }
}
