// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Optical cue prefixes require paired printed instructions and a proved head-size transition. */
public final class PrintedCueSize {
    public static final String SOURCE = "printed-cue-instruction";
    private static final Set<String> INSTRUMENTS =
            Set.of(
                    "guitar",
                    "piano",
                    "violin",
                    "viola",
                    "cello",
                    "flute",
                    "oboe",
                    "clarinet",
                    "bassoon",
                    "trumpet",
                    "trombone",
                    "horn",
                    "bass",
                    "harp",
                    "organ",
                    "saxophone");

    public record Passage(
            List<Integer> notes,
            int playOwner,
            PlayingTechniqueDetector.Word cue,
            PlayingTechniqueDetector.Word play) {
        public Passage {
            notes = List.copyOf(notes);
        }
    }

    private record Head(int index, ScoreNoteEvent note, float x) {}

    private PrintedCueSize() {}

    private static String token(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replaceAll("[.:]+$", "");
    }

    /** Measured head core; spanning staff rails and narrow shafts cannot supply its width. */
    static OptionalDouble width(
            ScoreNoteEvent n, MeasureRegion region, float gap, byte[] gray, int width, int height) {
        if (!Float.isFinite(gap)
                || gap < 6
                || gap > Math.min(width, height) / 8f
                || !Float.isFinite(n.pageY())
                || n.pageY() <= 0
                || n.pageY() >= 1
                || !Float.isFinite(n.positionInMeasure())
                || n.positionInMeasure() < 0
                || n.positionInMeasure() > 1) return OptionalDouble.empty();
        int x =
                Math.round(
                        (region.left() + n.positionInMeasure() * (region.right() - region.left()))
                                * width);
        int y = Math.round(n.pageY() * height), radius = Math.round(gap * .4f);
        int cap = (int) Math.ceil(gap * 2.25f);
        if (x - cap < 0 || x + cap >= width || y - radius < 0 || y + radius >= height)
            return OptionalDouble.empty();
        var spans = new ArrayList<Integer>();
        for (int row = y - radius; row <= y + radius; row++) {
            if ((gray[row * width + x] & 255) >= 165) continue;
            int left = x, right = x;
            while (left > x - cap && (gray[row * width + left - 1] & 255) < 165) left--;
            while (right < x + cap && (gray[row * width + right + 1] & 255) < 165) right++;
            int span = right - left + 1;
            if (left == x - cap || right == x + cap || span < gap * .45f || span > gap * 2.1f)
                continue;
            spans.add(span);
        }
        if (spans.size() < Math.ceil(gap * .3f)) return OptionalDouble.empty();
        Collections.sort(spans);
        return OptionalDouble.of(spans.get((spans.size() - 1) * 3 / 4));
    }

    static List<Passage> detect(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height) {
        if (gray == null || width <= 0 || height <= 0 || gray.length != (long) width * height)
            return List.of();
        var candidates = new ArrayList<Passage>();
        for (var cue : new LinkedHashSet<>(words)) {
            if (!INSTRUMENTS.contains(token(cue.text()))) continue;
            var staff =
                    PrintedDirectionStaff.at(
                            staffs,
                            score.measures(),
                            score.notes(),
                            gray,
                            width,
                            height,
                            cue.left() * width,
                            cue.top() * height,
                            cue.bottom() * height);
            if (staff == null) continue;
            var plays = new ArrayList<PlayingTechniqueDetector.Word>();
            for (var play : new LinkedHashSet<>(words)) {
                if (!token(play.text()).equals("play")
                        || play.left() <= cue.right()
                        || (play.left() - cue.left()) * width > staff.gap() * 24) continue;
                var owner =
                        PrintedDirectionStaff.at(
                                staffs,
                                score.measures(),
                                score.notes(),
                                gray,
                                width,
                                height,
                                play.left() * width,
                                play.top() * height,
                                play.bottom() * height);
                if (owner != null
                        && staff.index() == owner.index()
                        && staff.count() == owner.count()
                        && Math.abs(staff.top() + staff.bottom() - owner.top() - owner.bottom())
                                <= Math.min(staff.gap(), owner.gap()) * 4
                        && Math.abs(staff.gap() - owner.gap()) <= staff.gap() * .15f)
                    plays.add(play);
            }
            if (plays.size() != 1) continue;
            var play = plays.get(0);
            var heads = new ArrayList<Head>();
            float center = (staff.top() + staff.bottom()) * .5f / height;
            for (int i = 0; i < score.notes().size(); i++) {
                var n = score.notes().get(i);
                if (n.staffIndex() != staff.index()
                        || n.staffCount() != staff.count()
                        || n.crossStaffBeam()
                        || n.measureIndex() < 0
                        || n.measureIndex() >= score.measures().size()) continue;
                var region = score.measures().get(n.measureIndex());
                if (center < region.top() - staff.gap() / height
                        || center > region.bottom() + staff.gap() / height) continue;
                float x =
                        (region.left() + n.positionInMeasure() * (region.right() - region.left()))
                                * width;
                heads.add(new Head(i, n, x));
            }
            heads.sort(Comparator.comparingDouble(Head::x));
            Head first = null, principal = null;
            for (var h : heads) {
                if (first == null && h.x() >= cue.left() * width - staff.gap() * .5f) first = h;
                if (principal == null && h.x() >= play.left() * width - staff.gap() * .5f)
                    principal = h;
            }
            if (first == null
                    || principal == null
                    || first.x() >= principal.x()
                    || first.x() - cue.left() * width > staff.gap() * 5
                    || principal.x() - play.left() * width > staff.gap() * 5
                    || !ordinary(principal.note())) continue;
            var normal =
                    width(
                            principal.note(),
                            score.measures().get(principal.note().measureIndex()),
                            staff.gap(),
                            gray,
                            width,
                            height);
            if (normal.isEmpty()
                    || normal.getAsDouble() < staff.gap() * 1.1
                    || normal.getAsDouble() > staff.gap() * 1.8) continue;
            boolean valid = true;
            var small = new ArrayList<Integer>();
            int previousMeasure = -1;
            for (var h : heads) {
                if (h.x() < first.x() || h.x() >= principal.x()) continue;
                var measured =
                        width(
                                h.note(),
                                score.measures().get(h.note().measureIndex()),
                                staff.gap(),
                                gray,
                                width,
                                height);
                if (!ordinary(h.note())
                        || h.note().measureIndex() < previousMeasure
                        || measured.isEmpty()
                        || measured.getAsDouble() < staff.gap() * .5
                        || measured.getAsDouble() > staff.gap() * 1.1
                        || measured.getAsDouble() > normal.getAsDouble() * .8) {
                    valid = false;
                    break;
                }
                previousMeasure = h.note().measureIndex();
                small.add(h.index());
            }
            if (valid && !small.isEmpty())
                candidates.add(new Passage(small, principal.index(), cue, play));
        }
        // Conflicting printed scopes cannot silently overwrite one another.
        var use = new HashMap<Integer, Integer>();
        for (var p : candidates) for (int i : p.notes()) use.merge(i, 1, Integer::sum);
        return candidates.stream()
                .filter(p -> p.notes().stream().allMatch(i -> use.get(i) == 1))
                .toList();
    }

    private static boolean ordinary(ScoreNoteEvent n) {
        return n.kind() == ScoreNoteEvent.Kind.PITCHED
                && (n.articulations() & NoteOrnament.GRACE) == 0
                && ScoreNoteTiming.writtenDurationBeats(n) > 0;
    }

    static ScorePageInterpretation applySizes(
            ScorePageInterpretation score, List<Passage> passages) {
        if (passages.isEmpty()) return score;
        var notes = new ArrayList<>(score.notes());
        for (var p : passages)
            for (int i : p.notes())
                notes.set(i, notes.get(i).withVisualSize(ScoreNoteEvent.VisualSize.CUE));
        return score.withNotes(
                notes, java.util.stream.IntStream.range(0, notes.size()).boxed().toList());
    }

    /** Retain printed words on exact source columns; musical clocks are resolved separately. */
    static ScorePageInterpretation apply(ScorePageInterpretation score, List<Passage> passages) {
        var sized = applySizes(score, passages);
        var events = new ArrayList<>(sized.expressiveEvents());
        for (var p : passages) {
            addCaption(events, score.notes().get(p.notes().get(0)), p.cue());
            addCaption(events, score.notes().get(p.playOwner()), p.play());
        }
        return sized.withExpressiveEvents(events);
    }

    /** Final guide assembly runs after analysis, so visual metadata never enters audio timing. */
    static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height) {
        var passages = detect(score, words, staffs, gray, width, height);
        if (passages.isEmpty()) return score;
        return ScoreExpressionDetector.resolve(apply(score, passages), Float.NaN);
    }

    private static void addCaption(
            List<ScoreExpressiveEvent> events,
            ScoreNoteEvent n,
            PlayingTechniqueDetector.Word word) {
        String column =
                n.measureIndex()
                        + ":"
                        + n.staffIndex()
                        + ":"
                        + n.staffCount()
                        + ":"
                        + Float.floatToIntBits(
                                n.positionInMeasure() == 0 ? 0 : n.positionInMeasure());
        String id = "printed-cue:" + column + ":word:" + word.text().strip();
        if (events.stream().anyMatch(e -> e.eventId().equals(id))) return;
        events.add(
                new ScoreExpressiveEvent(
                        id,
                        ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                        Optional.empty(),
                        Optional.empty(),
                        ScoreExpressiveEvent.Scope.UNRESOLVED,
                        n.staffIndex(),
                        n.staffCount(),
                        Optional.of("expression-column:" + column),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        "",
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        SOURCE,
                                        0,
                                        word.left(),
                                        n.staffIndex(),
                                        n.staffCount(),
                                        word.text().strip()))));
    }

    public static boolean owns(ScoreExpressiveEvent event) {
        if (event.kind() != ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION
                || !event.qualifierText().isEmpty()
                || event.evidence().size() != 1
                || event.targetEventId().isEmpty()
                || !event.targetEventId().get().startsWith("expression-column:")) return false;
        var e = event.evidence().get(0);
        return e.sourceId().equals(SOURCE)
                && e.staffIndex() == event.staffIndex()
                && e.staffCount() == event.staffCount()
                && (INSTRUMENTS.contains(token(e.printedText()))
                        || token(e.printedText()).equals("play"));
    }
}
