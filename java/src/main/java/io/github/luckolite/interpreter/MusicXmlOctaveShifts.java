// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Retained note clocks, not page geometry, delimit written octave directions. */
public final class MusicXmlOctaveShifts {
    public record Note(int staff, int voice, long start, long end, int shift, boolean grace) {}

    public record Span(int staff, int voice, long start, long end, int shift) {}

    private MusicXmlOctaveShifts() {}

    public static List<Span> plan(List<Note> notes) {
        var lanes = new TreeMap<Long, TreeMap<Long, int[]>>();
        for (var n : notes) {
            if (n.staff < 1
                    || n.voice < 1
                    || n.start < 0
                    || n.end < n.start
                    || n.shift < -2
                    || n.shift > 2
                    || (!n.grace && n.end == n.start))
                throw new IllegalArgumentException("Invalid retained octave direction clock");
            if (n.grace) continue;
            var edges =
                    lanes.computeIfAbsent(
                            ((long) n.staff << 32) | n.voice, ignored -> new TreeMap<>());
            edges.computeIfAbsent(n.start, ignored -> new int[5])[n.shift + 2]++;
            edges.computeIfAbsent(n.end, ignored -> new int[5])[n.shift + 2]--;
        }
        var spans = new ArrayList<Span>();
        for (var lane : lanes.entrySet()) {
            int[] active = new int[5];
            Long previous = null;
            for (var edge : lane.getValue().entrySet()) {
                long at = edge.getKey();
                if (previous != null && at > previous) {
                    int selected = 0, kinds = 0;
                    for (int i = 0; i < 5; i++)
                        if (active[i] != 0) {
                            kinds++;
                            selected = i - 2;
                        }
                    if (kinds > 1)
                        throw new IllegalArgumentException(
                                "Conflicting simultaneous staff octave shifts");
                    if (kinds == 1 && selected != 0) {
                        int last = spans.size() - 1;
                        if (last >= 0
                                && spans.get(last).staff == (int) (lane.getKey() >> 32)
                                && spans.get(last).voice == (int) (long) lane.getKey()
                                && spans.get(last).end == previous
                                && spans.get(last).shift == selected) {
                            var old = spans.get(last);
                            spans.set(
                                    last, new Span(old.staff, old.voice, old.start, at, selected));
                        } else
                            spans.add(
                                    new Span(
                                            (int) (lane.getKey() >> 32),
                                            (int) (long) lane.getKey(),
                                            previous,
                                            at,
                                            selected));
                    }
                }
                for (int i = 0; i < 5; i++) active[i] += edge.getValue()[i];
                previous = at;
            }
        }
        for (var n : notes)
            if (n.grace) {
                var shifts = new HashSet<Integer>();
                for (var span : spans)
                    if (span.staff == n.staff
                            && span.voice == n.voice
                            && span.start <= n.start
                            && n.start <= span.end) shifts.add(span.shift);
                if ((n.shift == 0 && !shifts.isEmpty())
                        || (n.shift != 0 && (shifts.size() != 1 || !shifts.contains(n.shift))))
                    throw new IllegalArgumentException(
                            "Grace octave direction lacks an unambiguous principal span");
            }
        return List.copyOf(spans);
    }

    /** Attach the applied source role to the actual emitted node; user data is never serialized. */
    public static void mark(org.w3c.dom.Element note, int shift) {
        if (shift < -2 || shift > 2) throw new IllegalArgumentException("Invalid octave shift");
        note.setUserData("music-sheets-octave-shift", shift, null);
    }

    private record Active(int shift, String staff, int number) {}

    /** Insert directions in final note order, including grace notes and sliced voices. */
    public static void apply(org.w3c.dom.Document document) {
        var parts = document.getElementsByTagName("part");
        for (int pi = 0; pi < parts.getLength(); pi++) {
            var part = (org.w3c.dom.Element) parts.item(pi);
            var nodes = part.getElementsByTagName("note");
            var notes = new ArrayList<org.w3c.dom.Element>();
            var last = new HashMap<String, org.w3c.dom.Element>();
            for (int i = 0; i < nodes.getLength(); i++) {
                var note = (org.w3c.dom.Element) nodes.item(i);
                notes.add(note);
                last.put(text(note, "voice", "1"), note);
            }
            var active = new HashMap<String, Active>();
            var used = new HashSet<Integer>();
            var numbers = new LinkedHashMap<String, Integer>();
            for (var note : notes) {
                Object value = note.getUserData("music-sheets-octave-shift");
                if (value instanceof Integer && (Integer) value != 0) {
                    String voice = text(note, "voice", "1");
                    numbers.putIfAbsent(voice, numbers.size() + 1);
                }
            }
            if (numbers.size() > 16)
                throw new IllegalArgumentException("Too many octave direction voices");
            for (var note : notes) {
                String voice = text(note, "voice", "1"), staff = text(note, "staff", null);
                Object retained = note.getUserData("music-sheets-octave-shift");
                int shift = retained instanceof Integer ? (Integer) retained : 0;
                var old = active.get(voice);
                if (old != null && (old.shift != shift || !Objects.equals(old.staff, staff))) {
                    direction(note, note, voice, old, true);
                    used.remove(old.number);
                    active.remove(voice);
                }
                if (shift != 0 && !active.containsKey(voice)) {
                    int number = numbers.get(voice);
                    var next = new Active(shift, staff, number);
                    direction(note, note, voice, next, false);
                    used.add(number);
                    active.put(voice, next);
                }
                if (last.get(voice) == note && active.containsKey(voice)) {
                    var end = active.remove(voice);
                    direction(note, note.getNextSibling(), voice, end, true);
                    used.remove(end.number);
                }
            }
        }
    }

    private static String text(org.w3c.dom.Element node, String name, String fallback) {
        var found = node.getElementsByTagName(name);
        return found.getLength() == 0 ? fallback : found.item(0).getTextContent();
    }

    private static void direction(
            org.w3c.dom.Element note,
            org.w3c.dom.Node before,
            String voice,
            Active active,
            boolean stop) {
        var doc = note.getOwnerDocument();
        var direction = doc.createElement("direction");
        direction.setAttribute("placement", active.shift > 0 ? "above" : "below");
        var type = doc.createElement("direction-type");
        direction.appendChild(type);
        var mark = doc.createElement("octave-shift");
        type.appendChild(mark);
        mark.setAttribute("type", stop ? "stop" : active.shift > 0 ? "down" : "up");
        mark.setAttribute("size", Integer.toString(Math.abs(active.shift) * 7 + 1));
        mark.setAttribute("number", Integer.toString(active.number));
        var v = doc.createElement("voice");
        v.setTextContent(voice);
        direction.appendChild(v);
        if (active.staff != null) {
            var s = doc.createElement("staff");
            s.setTextContent(active.staff);
            direction.appendChild(s);
        }
        note.getParentNode().insertBefore(direction, before);
    }
}
