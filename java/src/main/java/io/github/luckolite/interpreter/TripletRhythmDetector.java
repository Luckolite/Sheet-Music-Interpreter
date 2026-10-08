// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Adapted from Music Sheets: standalone package and platform-independent diagnostics.
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reads printed triplets and beamed five- or seven-in-four groups from numeral and attack geometry. */
final class TripletRhythmDetector {
    private TripletRhythmDetector() {}

    record Rhythm(List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests) {}

    /** A printed rest occupies one rhythmic slot just as a chord attack does.
     * Virtual events are used only for grouping and never returned as sounding notes. */
    static Rhythm withRests(
            List<ScoreNoteEvent> notes,
            List<ScoreRestEvent> rests,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height) {
        if (rests == null || rests.isEmpty())
            return new Rhythm(apply(notes, measures, gray, width, height), rests);
        List<ScoreNoteEvent> slots = new ArrayList<>(notes);
        List<Integer> restIndices = new ArrayList<>();
        for (int restIndex = 0; restIndex < rests.size(); restIndex++) {
            ScoreRestEvent rest = rests.get(restIndex);
            double value = rest.durationBeats();
            int beams = value == .25 ? 2 : value == .5 ? 1 : value == 1 ? 0 : -1;
            restIndices.add(restIndex);
            // Unsupported or already scaled rest values remain barriers between
            // attack columns; omitting them could join notes across real silence.
            slots.add(
                    new ScoreNoteEvent(
                            rest.measureIndex(),
                            rest.positionInMeasure(),
                            0,
                            rest.staffIndex(),
                            rest.staffCount(),
                            rest.pageY(),
                            false,
                            0,
                            Math.max(0, beams),
                            2,
                            beams <= 0 ? (float) value : 0,
                            beams < 0 ? 2 : 1));
        }
        List<ScoreNoteEvent> marked = apply(slots, measures, gray, width, height, notes.size());
        List<ScoreRestEvent> scaled = new ArrayList<>(rests);
        for (int i = 0; i < restIndices.size(); i++) {
            int index = restIndices.get(i);
            ScoreRestEvent rest = rests.get(index);
            if (marked.get(notes.size() + i).tupletDivisor() <= 1) continue;
            scaled.set(
                    index,
                    new ScoreRestEvent(
                            rest.measureIndex(),
                            rest.positionInMeasure(),
                            rest.pageY(),
                            rest.pageHeight(),
                            rest.staffIndex(),
                            rest.staffCount(),
                            rest.durationBeats() * marked.get(notes.size() + i).durationScale()));
        }
        List<ScoreNoteEvent> result = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            ScoreNoteEvent original = notes.get(i), note = marked.get(i);
            float next = 1.01f;
            for (ScoreNoteEvent other : notes)
                if (sameVoice(original, other)
                        && other.positionInMeasure() > original.positionInMeasure() + .018f)
                    next = Math.min(next, other.positionInMeasure());
            boolean first =
                    notes.stream()
                            .noneMatch(
                                    other ->
                                            sameVoice(original, other)
                                                    && other.positionInMeasure()
                                                            < original.positionInMeasure() - .018f);
            double before = 0, after = 0;
            for (int j = 0; j < rests.size(); j++) {
                ScoreRestEvent rest = rests.get(j);
                double delta = rest.durationBeats() - scaled.get(j).durationBeats();
                if (delta == 0
                        || rest.measureIndex() != note.measureIndex()
                        || rest.staffIndex() != note.staffIndex()
                        || rest.staffCount() != note.staffCount()) continue;
                if (rest.positionInMeasure() < note.positionInMeasure()
                        && (first
                                || !ScoreNoteTiming.hasIndependentSustain(original)
                                        && notes.stream()
                                                .anyMatch(
                                                        other ->
                                                                sameVoice(original, other)
                                                                        && ScoreNoteTiming
                                                                                .hasIndependentSustain(
                                                                                        other)
                                                                        && Math.abs(
                                                                                        other
                                                                                                        .positionInMeasure()
                                                                                                - rest
                                                                                                        .positionInMeasure())
                                                                                <= .018f))
                        && original.leadingRestBeats() + .001 >= rest.durationBeats())
                    before += delta;
                if (rest.positionInMeasure() > note.positionInMeasure()
                        && rest.positionInMeasure() < next
                        && (!ScoreNoteTiming.hasIndependentSustain(original)
                                || rest.positionInMeasure() > note.positionInMeasure() + .018f)
                        && original.followingRestBeats() + .001 >= rest.durationBeats())
                    after += delta;
            }
            result.add(
                    new ScoreNoteEvent(
                                    note.measureIndex(),
                                    note.positionInMeasure(),
                                    note.staffStep(),
                                    note.staffIndex(),
                                    note.staffCount(),
                                    note.pageY(),
                                    note.tiedFromPrevious(),
                                    note.augmentationDots(),
                                    note.beamCount(),
                                    note.writtenAccidental(),
                                    note.unbeamedDurationBeats(),
                                    note.tupletDivisor(),
                                    (float) Math.max(0, note.followingRestBeats() - after),
                                    note.articulations(),
                                    note.clefBottomDiatonic(),
                                    note.crossStaffBeam(),
                                    (float) Math.max(0, note.leadingRestBeats() - before),
                                    note.compactOpening(),
                                    note.octaveShift(),
                                    note.boundaryTies(),
                                    note.tupletNormalNotes())
                            .withStemDirection(note.stemDirection())
                            .withKind(note.kind()));
        }
        return new Rhythm(List.copyOf(result), List.copyOf(scaled));
    }

    /** A chord contributes one attack column, regardless of how many heads it contains. */
    private record Onset(List<Integer> indices, float position, float top, float bottom) {}

    private static List<Onset> onsets(List<ScoreNoteEvent> notes) {
        return onsets(notes, Integer.MAX_VALUE);
    }

    private static List<Onset> onsets(List<ScoreNoteEvent> notes, int virtualStart) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) order.add(i);
        order.sort(
                Comparator.comparingInt((Integer i) -> notes.get(i).measureIndex())
                        .thenComparingInt(i -> notes.get(i).staffIndex())
                        .thenComparingInt(i -> notes.get(i).staffCount())
                        .thenComparingDouble(i -> notes.get(i).positionInMeasure()));
        List<Onset> result = new ArrayList<>();
        for (int i = 0; i < order.size(); ) {
            ScoreNoteEvent first = notes.get(order.get(i));
            List<Integer> members = new ArrayList<>();
            float top = first.pageY(), bottom = top;
            int j = i;
            boolean includesRest = order.get(i) >= virtualStart;
            while (j < order.size()) {
                ScoreNoteEvent n = notes.get(order.get(j));
                boolean nextRest = order.get(j) >= virtualStart;
                // A rest beside a displaced head shares its attack column. Sounding-only
                // groups retain their narrower tolerance and cannot skip a different attack.
                float tolerance = includesRest || nextRest ? .018f : .012f;
                if (!sameVoice(first, n)
                        || n.positionInMeasure() - first.positionInMeasure() > tolerance) break;
                includesRest |= nextRest;
                members.add(order.get(j));
                top = Math.min(top, n.pageY());
                bottom = Math.max(bottom, n.pageY());
                j++;
            }
            result.add(new Onset(List.copyOf(members), first.positionInMeasure(), top, bottom));
            i = j;
        }
        return result;
    }

    private static boolean sameVoice(ScoreNoteEvent a, ScoreNoteEvent b) {
        return a.measureIndex() == b.measureIndex()
                && a.staffIndex() == b.staffIndex()
                && a.staffCount() == b.staffCount();
    }

    /** Keep independent held voices out of a moving chord's rhythmic group.
     * The three attack columns themselves remain consecutive: an intervening
     * different-value attack must not be skipped to manufacture a triplet. */
    private static Onset matching(Onset onset, List<ScoreNoteEvent> notes, ScoreNoteEvent first) {
        double value = ScoreNoteTiming.writtenDurationBeats(first);
        if (!Double.isFinite(value) || value <= 0 || value > 1) return null;
        List<Integer> members = new ArrayList<>();
        float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (int index : onset.indices()) {
            ScoreNoteEvent n = notes.get(index);
            double written = ScoreNoteTiming.writtenDurationBeats(n);
            if (!Double.isFinite(written)
                    || !sameVoice(first, n)
                    || n.beamCount() != first.beamCount()
                    || n.augmentationDots() != 0
                    || n.tupletDivisor() != 1
                    || (n.articulations() & NoteOrnament.GRACE) != 0
                    || Math.abs(written - value) > .001) continue;
            members.add(index);
            top = Math.min(top, n.pageY());
            bottom = Math.max(bottom, n.pageY());
        }
        return members.isEmpty()
                ? null
                : new Onset(List.copyOf(members), onset.position(), top, bottom);
    }

    private static List<List<Onset>> triples(
            Onset a, Onset b, Onset c, List<ScoreNoteEvent> notes, boolean beamed) {
        List<List<Onset>> result = new ArrayList<>();
        float ab = b.position() - a.position(), bc = c.position() - b.position();
        if (ab <= .012f || bc <= .012f || (beamed && Math.max(ab, bc) > Math.min(ab, bc) * 1.5f))
            return result;
        for (int index : a.indices()) {
            ScoreNoteEvent first = notes.get(index);
            if (beamed && first.beamCount() < 1) continue;
            boolean seen = false;
            for (List<Onset> group : result)
                if (group.get(0).indices().contains(index)) {
                    seen = true;
                    break;
                }
            if (seen) continue;
            Onset aa = matching(a, notes, first),
                    bb = matching(b, notes, first),
                    cc = matching(c, notes, first);
            if (aa != null && bb != null && cc != null) result.add(List.of(aa, bb, cc));
        }
        return result;
    }

    static List<ScoreNoteEvent> apply(
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height) {
        return apply(notes, measures, gray, width, height, Integer.MAX_VALUE);
    }

    private static List<ScoreNoteEvent> apply(
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height,
            int virtualStart) {
        if (notes == null
                || notes.size() < 2
                || measures == null
                || gray == null
                || width < 1
                || height < 1
                || gray.length != width * height) return notes;
        List<ScoreNoteEvent> result = new ArrayList<>(notes);
        List<Glyph> clearThrees = new ArrayList<>();
        List<Onset> groups = onsets(notes, virtualStart);
        for (int i = 0; i + 2 < groups.size(); i++) {
            boolean marked = false;
            for (List<Onset> group :
                    triples(groups.get(i), groups.get(i + 1), groups.get(i + 2), result, false)) {
                Onset a = group.get(0), b = group.get(1), c = group.get(2);
                ScoreNoteEvent first = result.get(a.indices().get(0));
                if (first.measureIndex() < 0 || first.measureIndex() >= measures.size()) continue;
                MeasureRegion region = measures.get(first.measureIndex());
                float gap =
                        Math.max(
                                4,
                                (region.bottom() - region.top())
                                        * height
                                        / (8 * first.staffCount()));
                float x1 =
                        (region.left() + a.position() * (region.right() - region.left())) * width;
                float x3 =
                        (region.left() + c.position() * (region.right() - region.left())) * width;
                if (x3 - x1 < gap * 2 || x3 - x1 > gap * 18) continue;
                float y1 = Math.min(a.top(), Math.min(b.top(), c.top())) * height;
                float y2 = Math.max(a.bottom(), Math.max(b.bottom(), c.bottom())) * height;
                Glyph numeral =
                        findOwnedNumeral(
                                gray,
                                width,
                                height,
                                x1,
                                x3,
                                y1,
                                y2,
                                gap,
                                first.beamCount() > 0,
                                Float.NaN,
                                Float.NaN,
                                3,
                                true,
                                false,
                                first,
                                result.subList(0, Math.min(virtualStart, result.size())),
                                region,
                                measures);
                if (numeral == null && virtualStart < result.size()) {
                    final List<ScoreNoteEvent> restGroupNotes = result;
                    numeral =
                            findContrastedNumeral(
                                    gray,
                                    width,
                                    height,
                                    x1,
                                    x3,
                                    y1,
                                    y2,
                                    gap,
                                    first.beamCount() > 0,
                                    Float.NaN,
                                    Float.NaN,
                                    3,
                                    false,
                                    glyph ->
                                            bracketedRestBeamOwns(
                                                    glyph,
                                                    List.of(a, b, c),
                                                    restGroupNotes,
                                                    virtualStart,
                                                    region,
                                                    measures,
                                                    gray,
                                                    width,
                                                    height,
                                                    x1,
                                                    x3,
                                                    gap));
                }
                if (numeral == null
                        && a.indices().size() == 1
                        && b.indices().size() == 1
                        && c.indices().size() == 1
                        && first.beamCount() == 2
                        && result.get(b.indices().get(0)).beamCount() == 2
                        && result.get(c.indices().get(0)).beamCount() == 2) {
                    float x2 =
                            (region.left() + b.position() * (region.right() - region.left()))
                                    * width;
                    if (BoundedSecondaryBeam.proves(
                            gray,
                            width,
                            height,
                            new float[] {x1, x2, x3},
                            new float[] {a.top() * height, b.top() * height, c.top() * height},
                            gap))
                        numeral =
                                findOwnedNumeral(
                                        gray,
                                        width,
                                        height,
                                        x1,
                                        x3,
                                        y1,
                                        y2,
                                        gap,
                                        true,
                                        Float.NaN,
                                        Float.NaN,
                                        3,
                                        true,
                                        true,
                                        first,
                                        result.subList(0, Math.min(virtualStart, result.size())),
                                        region,
                                        measures);
                }
                final List<ScoreNoteEvent> ownershipNotes =
                        result.subList(0, Math.min(virtualStart, result.size()));
                boolean recoveredJoined = false;
                boolean sharedJoinedDirection = true;
                for (Onset onset : group)
                    for (int index : onset.indices())
                        if (index >= virtualStart
                                || result.get(index).stemDirection() != first.stemDirection())
                            sharedJoinedDirection = false;
                if (numeral == null
                        && first.beamCount() > 0
                        && first.stemDirection() != 0
                        && sharedJoinedDirection) {
                    for (Glyph reference : clearThrees) {
                        int[] found =
                                RepeatedTupletNumeral.find(
                                        gray,
                                        width,
                                        height,
                                        new int[] {
                                            reference.left(),
                                            reference.top(),
                                            reference.right(),
                                            reference.bottom()
                                        },
                                        x1,
                                        x3,
                                        y1,
                                        y2,
                                        gap,
                                        box -> {
                                            Glyph glyph = new Glyph(box[0], box[1], box[2], box[3]);
                                            return !insideOtherSystem(
                                                            glyph, region, measures, width, height)
                                                    && beamOwnsNumeral(
                                                            glyph,
                                                            first,
                                                            ownershipNotes,
                                                            region,
                                                            gray,
                                                            width,
                                                            height,
                                                            x1,
                                                            x3,
                                                            gap);
                                        });
                        if (found != null) {
                            numeral = new Glyph(found[0], found[1], found[2], found[3]);
                            recoveredJoined = true;
                            break;
                        }
                    }
                }
                if (numeral == null || insideOtherSystem(numeral, region, measures, width, height))
                    continue;
                if (!recoveredJoined) {
                    boolean represented = false;
                    for (Glyph known : clearThrees)
                        if (Math.abs(
                                                known.right()
                                                        - known.left()
                                                        - numeral.right()
                                                        + numeral.left())
                                        <= gap * .1f
                                && Math.abs(
                                                known.bottom()
                                                        - known.top()
                                                        - numeral.bottom()
                                                        + numeral.top())
                                        <= gap * .1f) represented = true;
                    if (!represented && clearThrees.size() < 4) clearThrees.add(numeral);
                }
                // A finger number must not regroup attacks across two separate beams.
                // A real tuplet bracket remains authoritative across beam breaks.
                float x2 =
                        (region.left() + b.position() * (region.right() - region.left())) * width;
                float ab = b.position() - a.position(), bc = c.position() - b.position();
                if (Math.max(ab, bc) > Math.min(ab, bc) * 1.5f) {
                    var middle = result.get(b.indices().get(0));
                    var last = result.get(c.indices().get(0));
                    boolean sharedDirection = true;
                    for (var onset : List.of(a, b, c))
                        for (int index : onset.indices())
                            if (result.get(index).stemDirection() != first.stemDirection())
                                sharedDirection = false;
                    if (!sharedDirection
                            || first.stemDirection() == 0
                            || middle.stemDirection() != first.stemDirection()
                            || last.stemDirection() != first.stemDirection()
                            || !beamOwnsNumeral(
                                    numeral,
                                    first,
                                    result.subList(0, Math.min(virtualStart, result.size())),
                                    region,
                                    gray,
                                    width,
                                    height,
                                    x1,
                                    x3,
                                    gap)
                            || !PrintedTupletBeamOwner.connectedHeads(
                                    gray,
                                    width,
                                    height,
                                    x1,
                                    first.pageY() * height,
                                    x2,
                                    middle.pageY() * height,
                                    gap,
                                    first.stemDirection())
                            || !PrintedTupletBeamOwner.connectedHeads(
                                    gray,
                                    width,
                                    height,
                                    x2,
                                    middle.pageY() * height,
                                    x3,
                                    last.pageY() * height,
                                    gap,
                                    first.stemDirection())) continue;
                }
                boolean bracket =
                        bracketArm(
                                        gray,
                                        width,
                                        height,
                                        Math.round(x1 - gap * .3f),
                                        numeral.left() - 2,
                                        numeral.top(),
                                        numeral.bottom(),
                                        gap * .25f)
                                && bracketArm(
                                        gray,
                                        width,
                                        height,
                                        numeral.right() + 2,
                                        Math.round(x3 + gap * .3f),
                                        numeral.top(),
                                        numeral.bottom(),
                                        gap * .25f);
                if (first.beamCount() > 0
                        && !bracket
                        && (SeparateBeamGroups.between(
                                        gray,
                                        width,
                                        height,
                                        x1,
                                        a.top() * height,
                                        x2,
                                        b.top() * height,
                                        gap)
                                || SeparateBeamGroups.between(
                                        gray,
                                        width,
                                        height,
                                        x2,
                                        b.top() * height,
                                        x3,
                                        c.top() * height,
                                        gap))) continue;
                // Finger numbers can sit under a four-note beam, centered on its last
                // three notes. Require another finger numeral and the larger beam before
                // rejecting the apparent triplet; an explicit bracket always wins.
                if (!bracket
                        && first.beamCount() > 0
                        && a.indices().size() == 1
                        && b.indices().size() == 1
                        && c.indices().size() == 1) {
                    boolean fingering = false;
                    for (int adjacent : new int[] {i - 1, i + 3}) {
                        if (adjacent < 0 || adjacent >= groups.size()) continue;
                        Onset fourth = matching(groups.get(adjacent), result, first);
                        if (fourth == null || fourth.indices().size() != 1) continue;
                        var extra = result.get(fourth.indices().get(0));
                        if (extra.measureIndex() != first.measureIndex()) continue;
                        float xx =
                                (region.left()
                                                + fourth.position()
                                                        * (region.right() - region.left()))
                                        * width;
                        float spacing = adjacent < i ? x1 - xx : xx - x3;
                        if (spacing < (x3 - x1) * .30f || spacing > (x3 - x1) * .75f) continue;
                        float leftX = adjacent < i ? xx : x1,
                                leftY = (adjacent < i ? fourth.top() : a.top()) * height;
                        float rightX = adjacent < i ? x3 : xx,
                                rightY = (adjacent < i ? c.top() : fourth.top()) * height;
                        int side = numeral.top() > y2 ? -1 : 1;
                        if (SeparateBeamGroups.connected(
                                        gray, width, height, leftX, leftY, rightX, rightY, gap,
                                        side)
                                && hasNearbyFour(gray, width, height, numeral, gap)) {
                            fingering = true;
                            break;
                        }
                    }
                    if (fingering) continue;
                }
                // Vertically stacked small numbers assign fingers to chord tones.
                // They do not turn the surrounding three chord attacks into a tuplet.
                if (a.indices().size() > 1
                        && b.indices().size() > 1
                        && c.indices().size() > 1
                        && hasStackedFingering(gray, width, height, numeral, gap)) continue;
                for (Onset onset : List.of(a, b, c))
                    for (int index : onset.indices()) {
                        ScoreNoteEvent n = result.get(index);
                        result.set(
                                index,
                                new ScoreNoteEvent(
                                                n.measureIndex(),
                                                n.positionInMeasure(),
                                                n.staffStep(),
                                                n.staffIndex(),
                                                n.staffCount(),
                                                n.pageY(),
                                                n.tiedFromPrevious(),
                                                n.augmentationDots(),
                                                n.beamCount(),
                                                n.writtenAccidental(),
                                                n.unbeamedDurationBeats(),
                                                3,
                                                n.followingRestBeats(),
                                                n.articulations(),
                                                n.clefBottomDiatonic(),
                                                n.crossStaffBeam(),
                                                n.leadingRestBeats(),
                                                n.compactOpening(),
                                                n.octaveShift(),
                                                n.boundaryTies())
                                        .withStemDirection(n.stemDirection())
                                        .withKind(n.kind()));
                    }
                marked = true;
            }
            if (marked) i += 2;
        }
        result = mixedBracketPairs(result, measures, gray, width, height);
        result = mixedBeamedTuplets(result, measures, gray, width, height, 5, virtualStart);
        result = mixedBeamedTuplets(result, measures, gray, width, height, 7, virtualStart);
        result = boundedQuintuplets(result, measures, gray, width, height, virtualStart);
        return beamedTuplets(
                beamedTuplets(
                        beamedTuplets(result, measures, gray, width, height, 7, virtualStart),
                        measures,
                        gray,
                        width,
                        height,
                        5,
                        virtualStart),
                measures,
                gray,
                width,
                height,
                6,
                virtualStart);
    }

    private static List<ScoreNoteEvent> mixedBracketPairs(
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height) {
        List<ScoreNoteEvent> result = new ArrayList<>(notes);
        List<Onset> groups = onsets(notes);
        for (int i = 0; i + 1 < groups.size(); i++) {
            Onset a = groups.get(i), b = groups.get(i + 1);
            if (a.indices().size() != 1 || b.indices().size() != 1) continue;
            ScoreNoteEvent first = result.get(a.indices().get(0)),
                    last = result.get(b.indices().get(0));
            if (!sameVoice(first, last)
                    || first.measureIndex() < 0
                    || first.measureIndex() >= measures.size()
                    || first.augmentationDots() != 0
                    || last.augmentationDots() != 0
                    || first.tupletDivisor() != 1
                    || last.tupletDivisor() != 1
                    || ((first.articulations() | last.articulations()) & NoteOrnament.GRACE) != 0)
                continue;
            double d1 = ScoreNoteTiming.writtenDurationBeats(first),
                    d2 = ScoreNoteTiming.writtenDurationBeats(last);
            if (d1 <= 0
                    || d2 <= 0
                    || Math.max(d1, d2) > 2
                    || Math.abs(Math.max(d1, d2) / Math.min(d1, d2) - 2) > .001) continue;
            MeasureRegion region = measures.get(first.measureIndex());
            float gap =
                    Math.max(
                            4,
                            (region.bottom() - region.top()) * height / (8 * first.staffCount()));
            float x1 = (region.left() + a.position() * (region.right() - region.left())) * width;
            float x2 = (region.left() + b.position() * (region.right() - region.left())) * width;
            if (x2 - x1 < gap * 2 || x2 - x1 > gap * 18) continue;
            Glyph numeral =
                    findPrintedThree(
                            gray,
                            width,
                            height,
                            x1,
                            x2,
                            Math.min(a.top(), b.top()) * height,
                            Math.max(a.bottom(), b.bottom()) * height,
                            gap,
                            false,
                            Float.NaN,
                            Float.NaN);
            if (numeral == null || insideOtherSystem(numeral, region, measures, width, height))
                continue;
            for (int index : new int[] {a.indices().get(0), b.indices().get(0)}) {
                ScoreNoteEvent n = result.get(index);
                result.set(
                        index,
                        new ScoreNoteEvent(
                                        n.measureIndex(),
                                        n.positionInMeasure(),
                                        n.staffStep(),
                                        n.staffIndex(),
                                        n.staffCount(),
                                        n.pageY(),
                                        n.tiedFromPrevious(),
                                        n.augmentationDots(),
                                        n.beamCount(),
                                        n.writtenAccidental(),
                                        n.unbeamedDurationBeats(),
                                        3,
                                        n.followingRestBeats(),
                                        n.articulations(),
                                        n.clefBottomDiatonic(),
                                        n.crossStaffBeam(),
                                        n.leadingRestBeats(),
                                        n.compactOpening(),
                                        n.octaveShift(),
                                        n.boundaryTies())
                                .withStemDirection(n.stemDirection())
                                .withKind(n.kind()));
            }
            i++;
        }
        return result;
    }

    private static List<ScoreNoteEvent> beamedTuplets(
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height,
            int divisor,
            int virtualStart) {
        List<ScoreNoteEvent> result = new ArrayList<>(notes);
        List<Onset> groups = onsets(notes);
        for (int i = 0; i + divisor - 1 < groups.size(); i++) {
            Onset firstGroup = groups.get(i);
            for (int index : firstGroup.indices()) {
                ScoreNoteEvent first = result.get(index);
                if (first.tupletDivisor() != 1
                        || first.beamCount() < 1
                        || first.augmentationDots() != 0
                        || first.measureIndex() < 0
                        || first.measureIndex() >= measures.size()) continue;
                List<Onset> run = new ArrayList<>();
                float minimum = Float.MAX_VALUE, maximum = 0;
                boolean valid = true;
                for (int j = 0; j < divisor; j++) {
                    Onset onset = matching(groups.get(i + j), result, first);
                    if (onset == null) {
                        valid = false;
                        break;
                    }
                    if (j > 0) {
                        float delta = onset.position() - run.get(j - 1).position();
                        minimum = Math.min(minimum, delta);
                        maximum = Math.max(maximum, delta);
                    }
                    run.add(onset);
                }
                if (!valid || minimum < .012f || maximum > minimum * 1.6f) continue;
                // A longer uninterrupted run does not become a shorter tuplet merely
                // because a numeral happens to be nearby.
                MeasureRegion bar = measures.get(first.measureIndex());
                float gap =
                        Math.max(4, (bar.bottom() - bar.top()) * height / (8 * first.staffCount()));
                if (i > 0
                        && matching(groups.get(i - 1), result, first) != null
                        && firstGroup.position() - groups.get(i - 1).position() < minimum * 1.5f
                        && !(divisor == 6
                                && adjacentSix(
                                        groups, result, i - 6, first, bar, gray, width, height,
                                        gap))) continue;
                if (i + divisor < groups.size()
                        && matching(groups.get(i + divisor), result, first) != null
                        && groups.get(i + divisor).position() - run.get(divisor - 1).position()
                                < minimum * 1.5f
                        && !(divisor == 6
                                && adjacentSix(
                                        groups, result, i + 6, first, bar, gray, width, height,
                                        gap))) continue;
                float x1 =
                        (bar.left() + run.get(0).position() * (bar.right() - bar.left())) * width;
                float lastX =
                        (bar.left() + run.get(divisor - 1).position() * (bar.right() - bar.left()))
                                * width;
                if (lastX - x1 < gap * 3 || lastX - x1 > gap * 26) continue;
                float y1 = Float.MAX_VALUE, y2 = -Float.MAX_VALUE;
                for (Onset onset : run) {
                    y1 = Math.min(y1, onset.top() * height);
                    y2 = Math.max(y2, onset.bottom() * height);
                }
                if (divisor == 6) {
                    boolean separated = false;
                    for (int j = 1; j < run.size(); j++) {
                        Onset previous = run.get(j - 1), next = run.get(j);
                        float previousX =
                                (bar.left() + previous.position() * (bar.right() - bar.left()))
                                        * width;
                        float nextX =
                                (bar.left() + next.position() * (bar.right() - bar.left())) * width;
                        if (SeparateBeamGroups.between(
                                gray,
                                width,
                                height,
                                previousX,
                                previous.top() * height,
                                nextX,
                                next.top() * height,
                                gap)) {
                            separated = true;
                            break;
                        }
                    }
                    if (separated) continue;
                }
                Glyph numeral =
                        findOwnedNumeral(
                                gray,
                                width,
                                height,
                                x1,
                                lastX,
                                y1,
                                y2,
                                gap,
                                true,
                                Float.NaN,
                                Float.NaN,
                                divisor,
                                divisor == 6,
                                false,
                                first,
                                result.subList(0, Math.min(virtualStart, result.size())),
                                bar,
                                measures);
                if (numeral == null || insideOtherSystem(numeral, bar, measures, width, height))
                    continue;
                for (Onset onset : run)
                    for (int at : onset.indices()) {
                        ScoreNoteEvent n = result.get(at);
                        result.set(
                                at,
                                new ScoreNoteEvent(
                                                n.measureIndex(),
                                                n.positionInMeasure(),
                                                n.staffStep(),
                                                n.staffIndex(),
                                                n.staffCount(),
                                                n.pageY(),
                                                n.tiedFromPrevious(),
                                                n.augmentationDots(),
                                                n.beamCount(),
                                                n.writtenAccidental(),
                                                n.unbeamedDurationBeats(),
                                                divisor,
                                                n.followingRestBeats(),
                                                n.articulations(),
                                                n.clefBottomDiatonic(),
                                                n.crossStaffBeam(),
                                                n.leadingRestBeats(),
                                                n.compactOpening(),
                                                n.octaveShift(),
                                                n.boundaryTies())
                                        .withStemDirection(n.stemDirection())
                                        .withKind(n.kind()));
                    }
            }
        }
        return List.copyOf(result);
    }

    /** A non-binary ratio needs both printed membership and an independent complete staff clock. */
    private static List<ScoreNoteEvent> boundedQuintuplets(
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height,
            int virtualStart) {
        List<ScoreNoteEvent> result = new ArrayList<>(notes);
        List<Onset> groups = onsets(notes);
        for (int i = 0; i + 5 < groups.size(); i++) {
            Onset opening = groups.get(i);
            for (int index : opening.indices()) {
                ScoreNoteEvent first = result.get(index);
                if (first.staffCount() < 2
                        || first.crossStaffBeam()
                        || first.beamCount() != 2
                        || first.tupletDivisor() != 1
                        || first.augmentationDots() != 0
                        || first.measureIndex() < 0
                        || first.measureIndex() >= measures.size()) continue;
                List<Onset> run = new ArrayList<>();
                for (int j = 0; j < 5; j++) {
                    Onset onset = matching(groups.get(i + j), result, first);
                    if (onset == null
                            || !run.isEmpty()
                                    && onset.position() - run.get(run.size() - 1).position()
                                            < .012f) break;
                    run.add(onset);
                }
                if (run.size() != 5 || matching(groups.get(i + 5), result, first) == null) continue;
                double own = staffClock(groups, result, first, first.staffIndex(), virtualStart);
                double other = Double.NaN;
                for (int staff = 0; staff < first.staffCount(); staff++) {
                    if (staff == first.staffIndex()) continue;
                    double clock = staffClock(groups, result, first, staff, virtualStart);
                    if (Double.isFinite(clock) && Math.abs(clock - Math.rint(clock)) < .001) {
                        if (Double.isFinite(other) && Math.abs(other - clock) > .001) {
                            other = Double.NaN;
                            break;
                        }
                        other = clock;
                    }
                }
                double unit = ScoreNoteTiming.writtenDurationBeats(first);
                // Five-in-three is distinguishable from five-in-four only with a full other voice.
                if (!Double.isFinite(own)
                        || !Double.isFinite(other)
                        || other < 1
                        || other > 16
                        || Math.abs(own - other - unit * 2) > .001) continue;
                MeasureRegion bar = measures.get(first.measureIndex());
                float gap =
                        Math.max(4, (bar.bottom() - bar.top()) * height / (8 * first.staffCount()));
                float x1 = (bar.left() + opening.position() * (bar.right() - bar.left())) * width;
                float x2 =
                        (bar.left() + run.get(4).position() * (bar.right() - bar.left())) * width;
                float nextX =
                        (bar.left() + groups.get(i + 5).position() * (bar.right() - bar.left()))
                                * width;
                float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
                for (Onset onset : run) {
                    top = Math.min(top, onset.top() * height);
                    bottom = Math.max(bottom, onset.bottom() * height);
                }
                Glyph glyph =
                        findOwnedNumeral(
                                gray,
                                width,
                                height,
                                x1,
                                x2,
                                top,
                                bottom,
                                gap,
                                true,
                                Float.NaN,
                                Float.NaN,
                                5,
                                false,
                                false,
                                first,
                                result.subList(0, Math.min(virtualStart, result.size())),
                                bar,
                                measures);
                if (glyph == null
                        || insideOtherSystem(glyph, bar, measures, width, height)
                        || !BoundedTupletBeam.five(
                                gray,
                                width,
                                height,
                                x1,
                                x2,
                                nextX,
                                glyph.top(),
                                glyph.bottom(),
                                gap,
                                glyph.bottom() < top)) continue;
                for (Onset onset : run)
                    for (int member : onset.indices())
                        result.set(member, result.get(member).withTupletRatio(5, 3));
                break;
            }
        }
        return result;
    }

    /** Chords count once. Parallel durations and sustained voices cannot prove the other clock. */
    private static double staffClock(
            List<Onset> groups,
            List<ScoreNoteEvent> notes,
            ScoreNoteEvent first,
            int staff,
            int virtualStart) {
        double total = 0;
        boolean seen = false;
        boolean explicitRests = false;
        for (int index = virtualStart; index < notes.size(); index++) {
            ScoreNoteEvent rest = notes.get(index);
            if (rest.measureIndex() == first.measureIndex()
                    && rest.staffIndex() == staff
                    && rest.staffCount() == first.staffCount()) explicitRests = true;
        }
        for (Onset onset : groups) {
            ScoreNoteEvent sample = notes.get(onset.indices().get(0));
            if (sample.measureIndex() != first.measureIndex()
                    || sample.staffIndex() != staff
                    || sample.staffCount() != first.staffCount()) continue;
            double duration = Double.NaN, before = 0, after = 0;
            for (int index : onset.indices()) {
                ScoreNoteEvent note = notes.get(index);
                if (note.crossStaffBeam()
                        || ScoreNoteTiming.hasIndependentSustain(note)
                        || (note.articulations() & NoteOrnament.GRACE) != 0) return Double.NaN;
                double value = ScoreNoteTiming.writtenDurationBeats(note);
                if (Double.isFinite(duration) && Math.abs(duration - value) > .001)
                    return Double.NaN;
                duration = value;
                before = Math.max(before, note.leadingRestBeats());
                after = Math.max(after, note.followingRestBeats());
            }
            // The virtual slots already include attached silence. Direct note-only calls
            // instead use the preceding/following rest metadata once per attack column.
            if (!explicitRests) total += (seen ? 0 : before) + after;
            total += duration;
            seen = true;
        }
        return seen ? total : Double.NaN;
    }

    /** Subdividing a tuplet slot changes its attack count, not its printed ratio. */
    private static List<ScoreNoteEvent> mixedBeamedTuplets(
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height,
            int divisor,
            int virtualStart) {
        List<ScoreNoteEvent> result = new ArrayList<>(notes);
        List<Onset> groups = onsets(notes);
        for (int i = 0; i < groups.size(); i++) {
            ScoreNoteEvent first = result.get(groups.get(i).indices().get(0));
            if (first.measureIndex() < 0
                    || first.measureIndex() >= measures.size()
                    || first.beamCount() < 1
                    || first.beamCount() > 3
                    || first.tupletDivisor() != 1
                    || first.augmentationDots() != 0) continue;
            double unit = ScoreNoteTiming.writtenDurationBeats(first), total = 0;
            if (i + 1 < groups.size())
                for (int next : groups.get(i + 1).indices()) {
                    ScoreNoteEvent n = result.get(next);
                    double value = ScoreNoteTiming.writtenDurationBeats(n);
                    if (sameVoice(first, n)
                            && n.beamCount() > 0
                            && n.tupletDivisor() == 1
                            && n.augmentationDots() == 0
                            && Math.abs(value - unit * 2) < .001) {
                        unit = value;
                        break;
                    }
                }
            if (unit <= 0 || unit > .5) continue;
            MeasureRegion bar = measures.get(first.measureIndex());
            float gap = Math.max(4, (bar.bottom() - bar.top()) * height / (8 * first.staffCount()));
            List<Onset> run = new ArrayList<>();
            boolean subdivided = false;
            for (int j = i; j < groups.size() && j < i + divisor * 2; j++) {
                Onset raw = groups.get(j);
                List<Integer> members = new ArrayList<>();
                float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
                double value = -1;
                for (int index : raw.indices()) {
                    ScoreNoteEvent n = result.get(index);
                    double duration = ScoreNoteTiming.writtenDurationBeats(n);
                    if (!sameVoice(first, n)
                            || n.tupletDivisor() != 1
                            || n.augmentationDots() != 0
                            || n.beamCount() < 1
                            || (n.articulations() & NoteOrnament.GRACE) != 0
                            || Math.abs(duration - unit) > .001
                                    && Math.abs(duration - unit * .5) > .001) continue;
                    if (value >= 0 && Math.abs(value - duration) > .001) continue;
                    value = duration;
                    members.add(index);
                    top = Math.min(top, n.pageY());
                    bottom = Math.max(bottom, n.pageY());
                }
                if (members.isEmpty()) break;
                Onset onset = new Onset(List.copyOf(members), raw.position(), top, bottom);
                if (!run.isEmpty()) {
                    Onset previous = run.get(run.size() - 1);
                    // Engravers compress the subdivided slots; beam-group tests
                    // based on uniform head spacing can split that continuous beam.
                    if (onset.position() - previous.position() < .012f) break;
                }
                run.add(onset);
                total += value;
                subdivided |= value < unit - .001;
                if (total > unit * divisor + .001) break;
                if (!subdivided || run.size() <= divisor || Math.abs(total - unit * divisor) > .001)
                    continue;
                float x1 =
                        (bar.left() + run.get(0).position() * (bar.right() - bar.left())) * width;
                float x2 = (bar.left() + onset.position() * (bar.right() - bar.left())) * width;
                if (x2 - x1 < gap * 3 || x2 - x1 > gap * 26) break;
                float y1 = Float.POSITIVE_INFINITY, y2 = Float.NEGATIVE_INFINITY;
                for (Onset slot : run) {
                    y1 = Math.min(y1, slot.top() * height);
                    y2 = Math.max(y2, slot.bottom() * height);
                }
                Glyph numeral =
                        findOwnedNumeral(
                                gray,
                                width,
                                height,
                                x1,
                                x2,
                                y1,
                                y2,
                                gap,
                                true,
                                Float.NaN,
                                Float.NaN,
                                divisor,
                                true,
                                false,
                                first,
                                result.subList(0, Math.min(virtualStart, result.size())),
                                bar,
                                measures);
                if (numeral == null || insideOtherSystem(numeral, bar, measures, width, height))
                    break;
                for (Onset slot : run)
                    for (int index : slot.indices()) {
                        ScoreNoteEvent n = result.get(index);
                        result.set(
                                index,
                                new ScoreNoteEvent(
                                                n.measureIndex(),
                                                n.positionInMeasure(),
                                                n.staffStep(),
                                                n.staffIndex(),
                                                n.staffCount(),
                                                n.pageY(),
                                                n.tiedFromPrevious(),
                                                n.augmentationDots(),
                                                n.beamCount(),
                                                n.writtenAccidental(),
                                                n.unbeamedDurationBeats(),
                                                divisor,
                                                n.followingRestBeats(),
                                                n.articulations(),
                                                n.clefBottomDiatonic(),
                                                n.crossStaffBeam(),
                                                n.leadingRestBeats(),
                                                n.compactOpening(),
                                                n.octaveShift(),
                                                n.boundaryTies())
                                        .withStemDirection(n.stemDirection())
                                        .withKind(n.kind()));
                    }
                break;
            }
        }
        return result;
    }

    /** Between piano staves a single numeral belongs to the nearer staff.
     * Explicit cross-staff beams keep their existing grouping authority. */
    /** An actual rest slot has no shaft; the two sounding notes must own its complete bracket. */
    private static boolean bracketedRestBeamOwns(
            Glyph glyph,
            List<Onset> group,
            List<ScoreNoteEvent> notes,
            int virtualStart,
            MeasureRegion bar,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float gap) {
        int restCount = 0;
        List<ScoreNoteEvent> sounding = new ArrayList<>();
        for (Onset onset : group) {
            if (onset.indices().size() != 1) return false;
            int index = onset.indices().get(0);
            if (index >= virtualStart) restCount++;
            else sounding.add(notes.get(index));
        }
        if (restCount != 1 || sounding.size() != 2) return false;
        ScoreNoteEvent a = sounding.get(0), b = sounding.get(1);
        if (a.beamCount() < 1
                || a.beamCount() != b.beamCount()
                || a.stemDirection() == 0
                || a.stemDirection() != b.stemDirection()
                || a.crossStaffBeam()
                || b.crossStaffBeam()
                || insideOtherSystem(glyph, bar, measures, width, height)) return false;
        float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (Onset onset : group) {
            top = Math.min(top, onset.top() * height);
            bottom = Math.max(bottom, onset.bottom() * height);
        }
        // The existing bounded retry requires real deficit from local paper. Its
        // rule cleanup only removes ink; it cannot create a shaft or bracket.
        var ink =
                TupletNumeralInk.window(gray, width, height, firstX, lastX, top, bottom, gap, 165);
        if (ink == null) return false;
        int l = ink.left(), t = ink.top();
        if (!bracketArm(
                        ink.pixels(),
                        ink.width(),
                        ink.height(),
                        Math.round(firstX - gap * .3f) - l,
                        glyph.left() - 2 - l,
                        glyph.top() - t,
                        glyph.bottom() - t,
                        gap * .25f)
                || !bracketArm(
                        ink.pixels(),
                        ink.width(),
                        ink.height(),
                        glyph.right() + 2 - l,
                        Math.round(lastX + gap * .3f) - l,
                        glyph.top() - t,
                        glyph.bottom() - t,
                        gap * .25f)
                || !bracketHook(
                        ink.pixels(),
                        ink.width(),
                        ink.height(),
                        firstX - gap * .3f - l,
                        glyph.top() - t,
                        glyph.bottom() - t,
                        gap)
                || !bracketHook(
                        ink.pixels(),
                        ink.width(),
                        ink.height(),
                        lastX + gap * .3f - l,
                        glyph.top() - t,
                        glyph.bottom() - t,
                        gap)) return false;
        List<ScoreNoteEvent> heads = notes.subList(0, Math.min(virtualStart, notes.size()));
        float ax = (bar.left() + a.positionInMeasure() * (bar.right() - bar.left())) * width;
        float bx = (bar.left() + b.positionInMeasure() * (bar.right() - bar.left())) * width;
        float distance =
                PrintedTupletBeamOwner.distance(
                        ink.pixels(),
                        ink.width(),
                        ink.height(),
                        ax - l,
                        a.pageY() * height - t,
                        bx - l,
                        b.pageY() * height - t,
                        gap,
                        a.stemDirection(),
                        glyph.left() - l,
                        glyph.top() - t,
                        glyph.right() - l,
                        glyph.bottom() - t);
        return Float.isFinite(distance)
                && !hasCompetingBeamOwner(glyph, a, heads, bar, gray, width, height, gap, distance)
                && !otherSystemBeamOwns(
                        glyph, a, heads, bar, measures, gray, width, height, gap, distance);
    }

    private static Glyph findOwnedNumeral(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float firstY,
            float lastY,
            float gap,
            boolean shortNotes,
            float headX,
            float headY,
            int number,
            boolean contrasted,
            boolean boundedSecondary,
            ScoreNoteEvent first,
            List<ScoreNoteEvent> soundingHeads,
            MeasureRegion bar,
            List<MeasureRegion> measures) {
        java.util.function.Predicate<Glyph> ownership =
                glyph -> {
                    if (insideOtherSystem(glyph, bar, measures, width, height)) return false;
                    boolean physical =
                            beamOwnsNumeral(
                                    glyph,
                                    first,
                                    soundingHeads,
                                    bar,
                                    gray,
                                    width,
                                    height,
                                    firstX,
                                    lastX,
                                    gap);
                    if (!physical
                            && !ownsNumeral(glyph, first, soundingHeads, bar, gap, width, height))
                        return false;
                    float ownDistance =
                            physical
                                    ? attachedBeamDistance(
                                            glyph,
                                            first,
                                            soundingHeads,
                                            bar,
                                            gray,
                                            width,
                                            height,
                                            firstX,
                                            lastX,
                                            gap)
                                    : Float.POSITIVE_INFINITY;
                    return !otherSystemBeamOwns(
                            glyph,
                            first,
                            soundingHeads,
                            bar,
                            measures,
                            gray,
                            width,
                            height,
                            gap,
                            ownDistance);
                };
        return contrasted
                ? findContrastedNumeral(
                        gray,
                        width,
                        height,
                        firstX,
                        lastX,
                        firstY,
                        lastY,
                        gap,
                        shortNotes,
                        headX,
                        headY,
                        number,
                        boundedSecondary,
                        ownership)
                : findPrintedNumeral(
                        gray,
                        width,
                        height,
                        firstX,
                        lastX,
                        firstY,
                        lastY,
                        gap,
                        shortNotes,
                        headX,
                        headY,
                        number,
                        boundedSecondary,
                        ownership);
    }

    private static boolean beamOwnsNumeral(
            Glyph glyph,
            ScoreNoteEvent first,
            List<ScoreNoteEvent> heads,
            MeasureRegion bar,
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float gap) {
        if (first.beamCount() < 1 || first.stemDirection() == 0 || !heads.contains(first))
            return false;
        float firstHeadX =
                (bar.left() + first.positionInMeasure() * (bar.right() - bar.left())) * width;
        if (Math.abs(firstHeadX - firstX) > gap * .4f) return false;
        for (var last : heads) {
            if (last.measureIndex() != first.measureIndex()
                    || last.staffCount() != first.staffCount()
                    || last.staffIndex() != first.staffIndex()
                    || last.beamCount() < 1
                    || last.stemDirection() != first.stemDirection()) continue;
            float lastHeadX =
                    (bar.left() + last.positionInMeasure() * (bar.right() - bar.left())) * width;
            if (Math.abs(lastHeadX - lastX) > gap * .4f) continue;
            float ownDistance =
                    PrintedTupletBeamOwner.distance(
                            gray,
                            width,
                            height,
                            firstHeadX,
                            first.pageY() * height,
                            lastHeadX,
                            last.pageY() * height,
                            gap,
                            first.stemDirection(),
                            glyph.left(),
                            glyph.top(),
                            glyph.right(),
                            glyph.bottom());
            if (!Float.isFinite(ownDistance)) continue;
            if (!hasCompetingBeamOwner(
                    glyph, first, heads, bar, gray, width, height, gap, ownDistance)) return true;
        }
        return false;
    }

    /** A nearer foreign beam prevents borrowing its numeral across piano staves. */
    private static boolean hasCompetingBeamOwner(
            Glyph glyph,
            ScoreNoteEvent first,
            List<ScoreNoteEvent> heads,
            MeasureRegion bar,
            byte[] gray,
            int width,
            int height,
            float gap,
            float ownDistance) {
        float numeralX = (glyph.left() + glyph.right()) * .5f;
        for (var a : heads) {
            if (a.measureIndex() != first.measureIndex()
                    || a.staffCount() != first.staffCount()
                    || a.staffIndex() == first.staffIndex()
                    || a.beamCount() < 1
                    || a.stemDirection() == 0) continue;
            float ax = (bar.left() + a.positionInMeasure() * (bar.right() - bar.left())) * width;
            if (ax >= numeralX || numeralX - ax > gap * 26) continue;
            for (var b : heads) {
                if (b.measureIndex() != a.measureIndex()
                        || b.staffIndex() != a.staffIndex()
                        || b.staffCount() != a.staffCount()
                        || b.beamCount() != a.beamCount()
                        || b.stemDirection() != a.stemDirection()) continue;
                float bx =
                        (bar.left() + b.positionInMeasure() * (bar.right() - bar.left())) * width;
                if (bx <= numeralX || bx - ax > gap * 26) continue;
                float other =
                        PrintedTupletBeamOwner.distance(
                                gray,
                                width,
                                height,
                                ax,
                                a.pageY() * height,
                                bx,
                                b.pageY() * height,
                                gap,
                                a.stemDirection(),
                                glyph.left(),
                                glyph.top(),
                                glyph.right(),
                                glyph.bottom());
                if (Float.isFinite(other) && other <= ownDistance + gap * .1f) return true;
            }
        }
        return false;
    }

    private static float attachedBeamDistance(
            Glyph glyph,
            ScoreNoteEvent first,
            List<ScoreNoteEvent> heads,
            MeasureRegion bar,
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float gap) {
        float x = (bar.left() + first.positionInMeasure() * (bar.right() - bar.left())) * width;
        if (first.beamCount() < 1 || first.stemDirection() == 0 || Math.abs(x - firstX) > gap * .4f)
            return Float.POSITIVE_INFINITY;
        float nearest = Float.POSITIVE_INFINITY;
        for (var last : heads) {
            if (last.measureIndex() != first.measureIndex()
                    || last.staffIndex() != first.staffIndex()
                    || last.staffCount() != first.staffCount()
                    || last.beamCount() < 1
                    || last.stemDirection() != first.stemDirection()) continue;
            float lx = (bar.left() + last.positionInMeasure() * (bar.right() - bar.left())) * width;
            if (Math.abs(lx - lastX) > gap * .4f) continue;
            nearest =
                    Math.min(
                            nearest,
                            PrintedTupletBeamOwner.distance(
                                    gray,
                                    width,
                                    height,
                                    x,
                                    first.pageY() * height,
                                    lx,
                                    last.pageY() * height,
                                    gap,
                                    first.stemDirection(),
                                    glyph.left(),
                                    glyph.top(),
                                    glyph.right(),
                                    glyph.bottom()));
        }
        return nearest;
    }

    /** A next-system numeral may sit outside its staff box but still own a physical beam. */
    private static boolean otherSystemBeamOwns(
            Glyph glyph,
            ScoreNoteEvent first,
            List<ScoreNoteEvent> heads,
            MeasureRegion current,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height,
            float gap,
            float ownDistance) {
        float numeralX = (glyph.left() + glyph.right()) * .5f;
        for (var a : heads) {
            if (a.measureIndex() == first.measureIndex()
                    || a.measureIndex() < 0
                    || a.measureIndex() >= measures.size()
                    || a.beamCount() < 1
                    || a.stemDirection() == 0
                    || a.crossStaffBeam()) continue;
            var region = measures.get(a.measureIndex());
            if (!(region.top() > current.bottom() || region.bottom() < current.top())) continue;
            float ax =
                    (region.left() + a.positionInMeasure() * (region.right() - region.left()))
                            * width;
            if (ax >= numeralX || numeralX - ax > gap * 26) continue;
            for (var b : heads) {
                if (b.measureIndex() != a.measureIndex()
                        || b.staffIndex() != a.staffIndex()
                        || b.staffCount() != a.staffCount()
                        || b.beamCount() != a.beamCount()
                        || b.stemDirection() != a.stemDirection()
                        || b.crossStaffBeam()) continue;
                float bx =
                        (region.left() + b.positionInMeasure() * (region.right() - region.left()))
                                * width;
                if (bx <= numeralX || bx - ax > gap * 26) continue;
                float other =
                        PrintedTupletBeamOwner.distance(
                                gray,
                                width,
                                height,
                                ax,
                                a.pageY() * height,
                                bx,
                                b.pageY() * height,
                                gap,
                                a.stemDirection(),
                                glyph.left(),
                                glyph.top(),
                                glyph.right(),
                                glyph.bottom());
                if (Float.isFinite(other) && other + gap * .1f < ownDistance) return true;
            }
        }
        return false;
    }

    private static boolean ownsNumeral(
            Glyph glyph,
            ScoreNoteEvent first,
            List<ScoreNoteEvent> notes,
            MeasureRegion bar,
            float gap,
            int width,
            int height) {
        if (first.staffCount() < 2 || first.crossStaffBeam()) return true;
        float center = (glyph.top() + glyph.bottom()) * .5f;
        float x = (glyph.left() + glyph.right()) * .5f / width;
        float position = (x - bar.left()) / (bar.right() - bar.left());
        double own = Double.POSITIVE_INFINITY, other = Double.POSITIVE_INFINITY;
        for (ScoreNoteEvent note : notes) {
            if (note.measureIndex() != first.measureIndex()
                    || note.staffCount() != first.staffCount()
                    || Math.abs(note.positionInMeasure() - position) > .3f) continue;
            if (note.crossStaffBeam() && note.staffIndex() == first.staffIndex()) return true;
            float bottom = note.pageY() * height + note.staffStep() * gap * .5f;
            double distance = Math.max(0, Math.max(bottom - 4 * gap - center, center - bottom));
            if (note.staffIndex() == first.staffIndex()) own = Math.min(own, distance);
            else other = Math.min(other, distance);
        }
        return own <= other + gap * .35;
    }

    /** Two explicitly labelled six-note groups may be adjacent without a spacing gap. */
    private static boolean adjacentSix(
            List<Onset> groups,
            List<ScoreNoteEvent> notes,
            int start,
            ScoreNoteEvent first,
            MeasureRegion bar,
            byte[] gray,
            int width,
            int height,
            float gap) {
        if (start < 0 || start + 6 > groups.size()) return false;
        float top = Float.MAX_VALUE, bottom = 0, min = Float.MAX_VALUE, max = 0;
        for (int j = 0; j < 6; j++) {
            Onset onset = matching(groups.get(start + j), notes, first);
            if (onset == null) return false;
            top = Math.min(top, onset.top() * height);
            bottom = Math.max(bottom, onset.bottom() * height);
            if (j > 0) {
                float d = onset.position() - groups.get(start + j - 1).position();
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
        }
        if (min < .012f || max > min * 1.6f) return false;
        float x1 = (bar.left() + groups.get(start).position() * (bar.right() - bar.left())) * width;
        float x2 =
                (bar.left() + groups.get(start + 5).position() * (bar.right() - bar.left()))
                        * width;
        if (x2 - x1 < gap * 3 || x2 - x1 > gap * 26) return false;
        Glyph glyph =
                findContrastedNumeral(
                        gray, width, height, x1, x2, top, bottom, gap, true, Float.NaN, Float.NaN,
                        6);
        return glyph != null;
    }

    /** Complete printed numeral evidence overrides spurious head/beam predictions on that glyph. */
    static List<ScoreNoteEvent> withoutNumeralHeads(
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height) {
        if (notes == null
                || notes.size() < 4
                || measures == null
                || gray == null
                || width < 1
                || height < 1
                || gray.length != width * height) return notes;
        List<ScoreNoteEvent> result = new ArrayList<>(notes);
        for (ScoreNoteEvent candidate : notes) {
            if (candidate.tiedFromPrevious()
                    || candidate.measureIndex() < 0
                    || candidate.measureIndex() >= measures.size()) continue;
            MeasureRegion region = measures.get(candidate.measureIndex());
            float gap =
                    Math.max(
                            4,
                            (region.bottom() - region.top())
                                    * height
                                    / (8 * candidate.staffCount()));
            List<ScoreNoteEvent> voice = new ArrayList<>();
            for (ScoreNoteEvent n : result)
                if (n != candidate
                        && sameVoice(n, candidate)
                        && !(Math.abs(n.positionInMeasure() - candidate.positionInMeasure())
                                        <= .018f
                                && Math.abs(n.pageY() - candidate.pageY()) * height <= gap * 2.3f))
                    voice.add(n);
            List<Onset> groups = onsets(voice);
            float candidateX =
                    (region.left()
                                    + candidate.positionInMeasure()
                                            * (region.right() - region.left()))
                            * width;
            float candidateY = candidate.pageY() * height;
            for (int i = 0; i + 2 < groups.size(); i++) {
                for (List<Onset> group :
                        triples(groups.get(i), groups.get(i + 1), groups.get(i + 2), voice, true)) {
                    Onset a = group.get(0), b = group.get(1), c = group.get(2);
                    float x1 =
                            (region.left() + a.position() * (region.right() - region.left()))
                                    * width;
                    float x3 =
                            (region.left() + c.position() * (region.right() - region.left()))
                                    * width;
                    float y1 = Math.min(a.top(), Math.min(b.top(), c.top())) * height;
                    float y2 = Math.max(a.bottom(), Math.max(b.bottom(), c.bottom())) * height;
                    if (x3 - x1 < gap * 2
                            || x3 - x1 > gap * 18
                            || candidateX < x1 - gap
                            || candidateX > x3 + gap
                            || candidateY >= y1 - gap && candidateY <= y2 + gap) continue;
                    if (findPrintedThree(
                                    gray,
                                    width,
                                    height,
                                    x1,
                                    x3,
                                    y1,
                                    y2,
                                    gap,
                                    true,
                                    candidateX,
                                    candidateY)
                            != null) {
                        result.remove(candidate);
                        break;
                    }
                }
                if (!result.contains(candidate)) break;
            }
        }
        return List.copyOf(result);
    }

    /** A numeral printed inside another system cannot change this row's rhythm. */
    private static boolean insideOtherSystem(
            Glyph numeral,
            MeasureRegion current,
            List<MeasureRegion> measures,
            int width,
            int height) {
        float x = (numeral.left() + numeral.right()) * .5f / width;
        float y = (numeral.top() + numeral.bottom()) * .5f / height;
        if (y >= current.top() && y <= current.bottom()) return false;
        for (var other : measures)
            if ((other.top() > current.bottom() || other.bottom() < current.top())
                    && x >= other.left()
                    && x <= other.right()
                    && y >= other.top()
                    && y <= other.bottom()) return true;
        return false;
    }

    private record Glyph(int left, int top, int right, int bottom) {}

    private static boolean hasPrintedThree(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float firstY,
            float lastY,
            float gap,
            boolean shortNotes) {
        return findPrintedThree(
                        gray,
                        width,
                        height,
                        firstX,
                        lastX,
                        firstY,
                        lastY,
                        gap,
                        shortNotes,
                        Float.NaN,
                        Float.NaN)
                != null;
    }

    private static Glyph findPrintedThree(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float firstY,
            float lastY,
            float gap,
            boolean shortNotes,
            float headX,
            float headY) {
        return findContrastedNumeral(
                gray,
                width,
                height,
                firstX,
                lastX,
                firstY,
                lastY,
                gap,
                shortNotes,
                headX,
                headY,
                3);
    }

    private static Glyph findContrastedNumeral(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float firstY,
            float lastY,
            float gap,
            boolean shortNotes,
            float headX,
            float headY,
            int number) {
        return findContrastedNumeral(
                gray,
                width,
                height,
                firstX,
                lastX,
                firstY,
                lastY,
                gap,
                shortNotes,
                headX,
                headY,
                number,
                false);
    }

    private static Glyph findContrastedNumeral(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float firstY,
            float lastY,
            float gap,
            boolean shortNotes,
            float headX,
            float headY,
            int number,
            boolean boundedSecondary) {
        return findContrastedNumeral(
                gray,
                width,
                height,
                firstX,
                lastX,
                firstY,
                lastY,
                gap,
                shortNotes,
                headX,
                headY,
                number,
                boundedSecondary,
                glyph -> true);
    }

    private static Glyph findContrastedNumeral(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float firstY,
            float lastY,
            float gap,
            boolean shortNotes,
            float headX,
            float headY,
            int number,
            boolean boundedSecondary,
            java.util.function.Predicate<Glyph> ownership) {
        Glyph normal =
                findPrintedNumeral(
                        gray,
                        width,
                        height,
                        firstX,
                        lastX,
                        firstY,
                        lastY,
                        gap,
                        shortNotes,
                        headX,
                        headY,
                        number,
                        boundedSecondary,
                        ownership);
        if (normal != null
                && TupletNumeralInk.hasGlyphContrast(
                        gray,
                        width,
                        height,
                        normal.left(),
                        normal.top(),
                        normal.right(),
                        normal.bottom(),
                        gap)
                && (boundedSecondary
                        || validNumeralContext(
                                gray, width, height, normal, firstX, lastX, gap, number)))
            return normal;
        Glyph candidate = null;
        int candidateLevel = -1;
        for (int pass = 0; pass < 2; pass++)
            for (int level : new int[] {165, 140, 120, 190, 210}) {
                var local =
                        pass == 0
                                ? TupletNumeralInk.window(
                                        gray, width, height, firstX, lastX, firstY, lastY, gap,
                                        level)
                                : TupletNumeralInk.ruleEdgesWindow(
                                        gray, width, height, firstX, lastX, firstY, lastY, gap,
                                        level);
                if (local == null) continue;
                Glyph retry =
                        findPrintedNumeral(
                                local.pixels(),
                                local.width(),
                                local.height(),
                                firstX - local.left(),
                                lastX - local.left(),
                                firstY - local.top(),
                                lastY - local.top(),
                                gap,
                                shortNotes,
                                headX - local.left(),
                                headY - local.top(),
                                number,
                                boundedSecondary,
                                glyph ->
                                        ownership.test(
                                                new Glyph(
                                                        glyph.left() + local.left(),
                                                                glyph.top() + local.top(),
                                                        glyph.right() + local.left(),
                                                                glyph.bottom() + local.top())));
                if (retry != null
                        && TupletNumeralInk.hasGlyphContrast(
                                gray,
                                width,
                                height,
                                retry.left() + local.left(),
                                retry.top() + local.top(),
                                retry.right() + local.left(),
                                retry.bottom() + local.top(),
                                gap)) {
                    Glyph found =
                            new Glyph(
                                    retry.left() + local.left(),
                                    retry.top() + local.top(),
                                    retry.right() + local.left(),
                                    retry.bottom() + local.top());
                    if (!boundedSecondary
                            && !validNumeralContext(
                                    gray, width, height, found, firstX, lastX, gap, number))
                        continue;
                    // A single threshold can turn antialiased rest ink into two apparent
                    // bowls. A recovered numeral needs the same outline at another level;
                    // two masking passes at one threshold are not independent evidence.
                    if (candidate != null
                            && candidateLevel != level
                            && Math.abs(candidate.left() - found.left()) <= gap * .25f
                            && Math.abs(candidate.right() - found.right()) <= gap * .25f
                            && Math.abs(candidate.top() - found.top()) <= gap * .25f
                            && Math.abs(candidate.bottom() - found.bottom()) <= gap * .25f)
                        return found;
                    if (candidate == null) {
                        candidate = found;
                        candidateLevel = level;
                    }
                }
            }
        return null;
    }

    private static Glyph findPrintedNumeral(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float firstY,
            float lastY,
            float gap,
            boolean shortNotes,
            float headX,
            float headY,
            int number) {
        return findPrintedNumeral(
                gray,
                width,
                height,
                firstX,
                lastX,
                firstY,
                lastY,
                gap,
                shortNotes,
                headX,
                headY,
                number,
                false);
    }

    private static Glyph findPrintedNumeral(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float firstY,
            float lastY,
            float gap,
            boolean shortNotes,
            float headX,
            float headY,
            int number,
            boolean boundedSecondary) {
        return findPrintedNumeral(
                gray,
                width,
                height,
                firstX,
                lastX,
                firstY,
                lastY,
                gap,
                shortNotes,
                headX,
                headY,
                number,
                boundedSecondary,
                glyph -> true);
    }

    private static Glyph findPrintedNumeral(
            byte[] gray,
            int width,
            int height,
            float firstX,
            float lastX,
            float firstY,
            float lastY,
            float gap,
            boolean shortNotes,
            float headX,
            float headY,
            int number,
            boolean boundedSecondary,
            java.util.function.Predicate<Glyph> ownership) {
        float centerX = (firstX + lastX) * .5f;
        // Numerals align with the beam/stems, which can sit to one side of the
        // oval centres. Include that offset without clipping an italic 3.
        int left = Math.max(0, Math.round(centerX - gap * 1.65f));
        int right = Math.min(width - 1, Math.round(centerX + gap * 1.65f));
        int top = Math.max(0, Math.round(firstY - gap * 7));
        int bottom = Math.min(height - 1, Math.round(lastY + gap * 7));
        int localWidth = right - left + 1, localHeight = bottom - top + 1;
        if (localWidth < 3 || localHeight < 3) return null;
        boolean[] visited = new boolean[localWidth * localHeight];
        int[] queue = new int[visited.length];
        for (int origin = 0; origin < visited.length; origin++) {
            if (visited[origin]
                    || !dark(gray, width, left + origin % localWidth, top + origin / localWidth))
                continue;
            int count = 0, pending = 1;
            queue[0] = origin;
            visited[origin] = true;
            int minX = right, maxX = left, minY = bottom, maxY = top;
            while (pending > 0) {
                int current = queue[--pending];
                count++;
                int x = current % localWidth, y = current / localWidth;
                minX = Math.min(minX, left + x);
                maxX = Math.max(maxX, left + x);
                minY = Math.min(minY, top + y);
                maxY = Math.max(maxY, top + y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= localWidth || ny < 0 || ny >= localHeight) continue;
                        int next = ny * localWidth + nx;
                        if (!visited[next] && dark(gray, width, left + nx, top + ny)) {
                            visited[next] = true;
                            queue[pending++] = next;
                        }
                    }
            }
            int gw = maxX - minX + 1, gh = maxY - minY + 1;
            if (minX <= left
                    || maxX >= right
                    || minY <= top
                    || maxY >= bottom
                    || gh < gap * .7f
                    || gh > gap * 2.3f
                    || gw < gh * .30f
                    || gw > gh * .95f
                    || count < gw * gh * .15f
                    || count > gw * gh * .70f
                    || !(maxY < firstY - gap || minY > lastY + gap)) continue;
            if (Float.isFinite(headX)
                    && (headX < minX - gap * .1f
                            || headX > maxX + gap * .1f
                            || headY < minY - gap * .1f
                            || headY > maxY + gap * .1f)) continue;
            if (!(number == 7
                    ? looksLikeSeven(gray, width, minX, minY, gw, gh)
                    : number == 5
                            ? looksLikeFive(gray, width, minX, minY, gw, gh)
                            : number == 6
                                    ? TupletSixGlyph.matches(gray, width, minX, minY, gw, gh)
                                    : looksLikeThree(gray, width, minX, minY, gw, gh))) continue;
            if (TupletNumeralNeighbors.joinedText(gray, width, height, minX, minY, maxX, maxY))
                continue;
            // Quarter-note tuplets need the two bracket arms. For beamed/flagged short notes,
            // publishers routinely print only the numeral, so its shape/group alignment suffices.
            boolean bracket =
                    bracketArm(
                                    gray,
                                    width,
                                    height,
                                    Math.round(firstX - gap * .3f),
                                    minX - 2,
                                    minY,
                                    maxY,
                                    gap)
                            && bracketArm(
                                    gray,
                                    width,
                                    height,
                                    maxX + 2,
                                    Math.round(lastX + gap * .3f),
                                    minY,
                                    maxY,
                                    gap);
            // An unbracketed two-bowl shape inside five actual staff rules can be
            // a rest, not a numeral below an elevated beamed melody. Explicit
            // brackets still prove in-staff tuplets; isolated beam rules do not.
            if (number == 3
                    && !boundedSecondary
                    && !bracket
                    && insideFiveLineStaff(gray, width, height, minX, minY, maxX, maxY, gap))
                continue;
            if (shortNotes || bracket) {
                Glyph glyph = new Glyph(minX, minY, maxX, maxY);
                if (ownership.test(glyph)) return glyph;
            }
        }
        return null;
    }

    /** Validate against the untouched raster, not a retry with its staff rules erased. */
    private static boolean validNumeralContext(
            byte[] gray,
            int width,
            int height,
            Glyph glyph,
            float firstX,
            float lastX,
            float gap,
            int number) {
        if (number != 3
                || !insideFiveLineStaff(
                        gray,
                        width,
                        height,
                        glyph.left(),
                        glyph.top(),
                        glyph.right(),
                        glyph.bottom(),
                        gap)) return true;
        return bracketHook(gray, width, height, firstX, glyph.top(), glyph.bottom(), gap)
                && bracketHook(gray, width, height, lastX, glyph.top(), glyph.bottom(), gap);
    }

    private static boolean bracketHook(
            byte[] gray, int width, int height, float x, int top, int bottom, float gap) {
        for (int xx = Math.max(0, Math.round(x - gap * .4f));
                xx <= Math.min(width - 1, Math.round(x + gap * .4f));
                xx++) {
            int run = 0;
            for (int y = Math.max(0, Math.round(top - gap * .5f));
                    y <= Math.min(height - 1, Math.round(bottom + gap * .5f));
                    y++) {
                run = dark(gray, width, xx, y) ? run + 1 : 0;
                if (run >= Math.max(3, Math.round(gap * .65f))) return true;
            }
        }
        return false;
    }

    private static boolean insideFiveLineStaff(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float gap) {
        float center = (top + bottom) * .5f;
        int x0 = Math.max(0, Math.round(left - gap * 5)),
                x1 = Math.min(width - 1, Math.round(right + gap * 5));
        if (x1 - x0 < gap * 6) return false;
        var rules = new ArrayList<Integer>();
        boolean previous = false;
        for (int y = Math.max(0, Math.round(center - gap * 6));
                y <= Math.min(height - 1, Math.round(center + gap * 6));
                y++) {
            int count = 0, total = 0;
            for (int x = x0; x <= x1; x++)
                if (x < left - 1 || x > right + 1) {
                    total++;
                    if (dark(gray, width, x, y)) count++;
                }
            boolean rule = total > 0 && count >= total * .8f;
            if (rule && !previous) rules.add(y);
            previous = rule;
        }
        for (int i = 0; i + 4 < rules.size(); i++) {
            float spacing = (rules.get(i + 4) - rules.get(i)) / 4f;
            if (spacing < 3
                    || spacing < gap * .4f
                    || spacing > gap * 2
                    || center < rules.get(i)
                    || center > rules.get(i + 4)) continue;
            boolean regular = true;
            for (int j = 1; j <= 4; j++)
                if (Math.abs(rules.get(i + j) - rules.get(i) - j * spacing) > spacing * .2f)
                    regular = false;
            if (regular) return true;
        }
        return false;
    }

    /** Look for a separate, similarly sized upright glyph stacked over or under
     * this 3. The caller requires chord tones at all three candidate attacks. */
    private static boolean hasStackedFingering(
            byte[] gray, int width, int height, Glyph three, float gap) {
        float cx = (three.left() + three.right()) * .5f;
        int gh = three.bottom() - three.top() + 1;
        int left = Math.max(0, Math.round(cx - gap)),
                right = Math.min(width - 1, Math.round(cx + gap));
        int top = Math.max(0, Math.round(three.top() - gap * 2.4f));
        int bottom = Math.min(height - 1, Math.round(three.bottom() + gap * 2.4f));
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int seed = 0; seed < w * h; seed++) {
            if (seen[seed] || !dark(gray, width, left + seed % w, top + seed / w)) continue;
            int take = 0, size = 1, minX = width, maxX = -1, minY = height, maxY = -1;
            queue[0] = seed;
            seen[seed] = true;
            while (take < size) {
                int at = queue[take++], x = at % w, y = at / w;
                minX = Math.min(minX, left + x);
                maxX = Math.max(maxX, left + x);
                minY = Math.min(minY, top + y);
                maxY = Math.max(maxY, top + y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && dark(gray, width, left + nx, top + ny)) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (minX <= left || maxX >= right || minY <= top || maxY >= bottom) continue;
            int cw = maxX - minX + 1, ch = maxY - minY + 1;
            if (ch < gap * .7f
                    || ch > gap * 2.3f
                    || ch < gh * .65f
                    || ch > gh * 1.4f
                    || cw < ch * .2f
                    || cw > ch * .95f
                    || size < cw * ch * .15f
                    || size > cw * ch * .7f
                    || Math.abs((minX + maxX) * .5f - cx) > gap * .35f) continue;
            int separation =
                    maxY < three.top()
                            ? three.top() - maxY
                            : minY > three.bottom() ? minY - three.bottom() : -1;
            if (separation >= Math.max(2, gap * .2f) && separation <= gap) return true;
        }
        return false;
    }

    private static boolean hasNearbyFour(
            byte[] gray, int width, int height, Glyph three, float gap) {
        float cx = (three.left() + three.right()) * .5f;
        int gh = three.bottom() - three.top() + 1;
        int left = Math.max(0, Math.round(cx - gap * 11)),
                right = Math.min(width - 1, Math.round(cx + gap * 11));
        int top = Math.max(0, Math.round(three.top() - gap * .4f));
        int bottom = Math.min(height - 1, Math.round(three.bottom() + gap * .4f));
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int seed = 0; seed < w * h; seed++) {
            if (seen[seed] || !dark(gray, width, left + seed % w, top + seed / w)) continue;
            int take = 0, size = 1, minX = width, maxX = -1, minY = height, maxY = -1;
            queue[0] = seed;
            seen[seed] = true;
            while (take < size) {
                int at = queue[take++], x = at % w, y = at / w;
                minX = Math.min(minX, left + x);
                maxX = Math.max(maxX, left + x);
                minY = Math.min(minY, top + y);
                maxY = Math.max(maxY, top + y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && dark(gray, width, left + nx, top + ny)) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (minX <= left || maxX >= right || minY <= top || maxY >= bottom) continue;
            int cw = maxX - minX + 1, ch = maxY - minY + 1;
            if (ch < gap * .7f
                    || ch > gap * 2.3f
                    || ch < gh * .65f
                    || ch > gh * 1.4f
                    || cw < ch * .2f
                    || cw > ch * .95f
                    || size < cw * ch * .15f
                    || size > cw * ch * .7f
                    || Math.abs((minY + maxY - three.top() - three.bottom()) * .5f) > gap * .3f)
                continue;
            if (Math.abs((minX + maxX) * .5f - cx) > gap * 1.5f
                    && looksLikeFingerFour(gray, width, size, minX, maxX, minY, maxY)) return true;
        }
        return false;
    }

    private static boolean looksLikeFingerFour(
            byte[] gray, int width, int area, int minX, int maxX, int minY, int maxY) {
        int glyphWidth = maxX - minX + 1, glyphHeight = maxY - minY + 1;
        float fill = area / (float) (glyphWidth * glyphHeight);
        if (fill < .14f || fill > .66f) return false;
        int[] rows = new int[glyphHeight];
        int[] columns = new int[glyphWidth];
        int upperLeft = 0, lowerLeft = 0;
        for (int y = minY; y <= maxY; y++)
            for (int x = minX; x <= maxX; x++) {
                if ((gray[y * width + x] & 0xff) > 165) continue;
                int localX = x - minX, localY = y - minY;
                rows[localY]++;
                columns[localX]++;
                if (localX < glyphWidth * .55f && localY < glyphHeight * .58f) upperLeft++;
                if (localX < glyphWidth * .45f && localY > glyphHeight * .72f) lowerLeft++;
            }
        int rightSpine = 0;
        for (int x = Math.max(0, Math.round(glyphWidth * .52f)); x < glyphWidth; x++)
            rightSpine = Math.max(rightSpine, columns[x]);
        int middleCrossbar = 0;
        for (int y = Math.max(0, Math.round(glyphHeight * .36f));
                y <= Math.min(glyphHeight - 1, Math.round(glyphHeight * .74f));
                y++) middleCrossbar = Math.max(middleCrossbar, rows[y]);
        int topBar = 0;
        for (int y = 0; y < Math.max(1, Math.round(glyphHeight * .28f)); y++)
            topBar = Math.max(topBar, rows[y]);
        return rightSpine >= glyphHeight * .58f
                && middleCrossbar >= glyphWidth * .50f
                && topBar < glyphWidth * .68f
                && upperLeft >= Math.max(2, Math.round(area * .10f))
                && lowerLeft <= Math.max(2, Math.round(area * .16f));
    }

    private static boolean looksLikeSeven(byte[] gray, int width, int left, int top, int w, int h) {
        // Broad top bar followed by one descending diagonal, without the lower
        // bowl/base of 2, closed counters of 8, or two lobes of 3.
        int broad = 0, upper = 0, lower = 0, upperRows = 0, lowerRows = 0;
        int previous = Integer.MAX_VALUE, reverse = 0;
        for (int y = 0; y < h; y++) {
            int min = w, max = -1, count = 0;
            for (int x = 0; x < w; x++)
                if (dark(gray, width, left + x, top + y)) {
                    min = Math.min(min, x);
                    max = x;
                    count++;
                }
            if (max < 0) return false;
            if (y < h * .3f && max - min >= w * .65f) broad++;
            if (y >= h * .35f) {
                if (max - min > w * .55f || y < h * .8f && count < w * .12f) return false;
                int center = min + max;
                if (previous != Integer.MAX_VALUE && center > previous + 2) reverse++;
                previous = center;
                if (y < h * .55f) {
                    upper += center;
                    upperRows++;
                }
                if (y >= h * .8f) {
                    lower += center;
                    lowerRows++;
                }
            }
        }
        return broad >= Math.max(1, h / 12)
                && upperRows > 0
                && lowerRows > 0
                && reverse <= h / 10
                && upper / (float) upperRows - lower / (float) lowerRows >= w * .45f;
    }

    private static boolean looksLikeFive(byte[] gray, int width, int left, int top, int w, int h) {
        int bars = 0, leftStem = 0, lowerOpen = 0, lowerPocket = 0, lowerEdge = -1, foot = -1;
        for (int y = 0; y < h; y++) {
            int min = w, max = -1;
            for (int x = 0; x < w; x++)
                if (dark(gray, width, left + x, top + y)) {
                    min = Math.min(min, x);
                    max = Math.max(max, x);
                }
            // Italic fives can have a narrower cap than their lower bowl.
            if (y < h * .28f && max - min >= w * .45f) bars++;
            if (y >= h * .18f && y < h * .42f && min <= w * .4f && max <= w * .6f && max >= min)
                leftStem++;
            if (y >= h * .48f && y < h * .82f) {
                lowerEdge = Math.max(lowerEdge, max);
                if (min >= w * .45f && max >= w * .7f) lowerOpen++;
                if (hasLobePocket(gray, width, left, top + y, w)) lowerPocket++;
            }
            if (y >= h * .94f) foot = Math.max(foot, max);
        }
        return bars >= Math.max(2, h / 12)
                && leftStem >= Math.max(1, h / 12)
                && lowerOpen >= 1
                && lowerOpen + lowerPocket >= Math.max(2, h / 8)
                && lowerEdge - foot >= Math.max(1, (int) (w * .08f));
    }

    private static boolean looksLikeThree(byte[] gray, int width, int left, int top, int w, int h) {
        int[] min = new int[h], max = new int[h];
        for (int y = 0; y < h; y++) {
            min[y] = w;
            max[y] = -1;
            for (int x = 0; x < w; x++)
                if (dark(gray, width, left + x, top + y)) {
                    min[y] = Math.min(min[y], x);
                    max[y] = x;
                }
        }
        int upperOpen = 0, lowerOpen = 0, upperPocket = 0, lowerPocket = 0;
        int upperLobe = -1, lowerLobe = -1, waist = w, waistLeft = w, lowerOpenLeft = w;
        for (int y = 0; y < h; y++) {
            // Staff-rule removal can leave a blank scanline through a rest hook.
            // No ink is not an opening or an indented waist of a printed 3.
            if (max[y] < 0) continue;
            float fraction = y / (float) h;
            // A row occupies a whole pixel band; include a short opening that
            // crosses a lobe boundary instead of discarding it at small sizes.
            float nextFraction = (y + 1) / (float) h;
            if (nextFraction > .15f && fraction <= .36f + 1f / h) {
                upperLobe = Math.max(upperLobe, max[y]);
                if (min[y] >= w * .40f) upperOpen++;
                if (hasLobePocket(gray, width, left, top + y, w)) upperPocket++;
            }
            if (nextFraction > .60f && fraction <= .82f) {
                // The lower curve must bulge before the baseline; a 2 only widens at its foot.
                if (fraction <= .75f) lowerLobe = Math.max(lowerLobe, max[y]);
                // An italic lower bowl sits left of the upper bowl. Its opening
                // is relative to its own right edge, not the whole glyph width.
                if (max[y] >= w * .5f && min[y] >= Math.min(w * .40f, max[y] * .50f)) {
                    lowerOpen++;
                    lowerOpenLeft = Math.min(lowerOpenLeft, min[y]);
                }
                if (hasLobePocket(gray, width, left, top + y, w)) lowerPocket++;
            }
            if (fraction >= .37f && fraction <= .55f) {
                waist = Math.min(waist, max[y]);
                waistLeft = Math.min(waistLeft, min[y]);
            }
        }
        int required = Math.max(2, h / 12);
        // Curled terminals put ink on the left of an otherwise open lobe. Allow
        // that ink only with a wide interior pocket and a truly open row in each
        // lobe: a closed 8 and the solid upper-left stem of a 5 still fail.
        boolean upper = upperOpen >= required || upperOpen >= 1 && upperPocket >= required;
        boolean lower = lowerOpen >= required || lowerOpen >= 1 && lowerPocket >= required;
        int indentation = Math.max(1, (int) Math.floor(w * .08f));
        int foot = -1, capLeft = w;
        for (int y = 0; y < Math.max(2, (int) Math.ceil(h * .10)); y++)
            capLeft = Math.min(capLeft, min[y]);
        for (int y = (int) Math.ceil(h * .92); y < h; y++) foot = Math.max(foot, max[y]);
        int extendedSolidFoot = 0;
        for (int y = (int) Math.ceil(h * .55f); y < (int) Math.ceil(h * .92f); y++) {
            if (min[y] > w * .4f || max[y] - min[y] + 1 < w * .6f) continue;
            boolean growsFromDiagonal = false;
            for (int prior = Math.max(0, y - Math.max(2, Math.round(h * .15f))); prior < y; prior++)
                if (max[prior] >= 0
                        && max[y] - max[prior] >= Math.max(2, Math.ceil(w * .2f))
                        && min[y] <= min[prior] + indentation) growsFromDiagonal = true;
            if (!growsFromDiagonal) continue;
            int occupied = 0;
            for (int x = min[y]; x <= max[y]; x++)
                if (dark(gray, width, left + x, top + y)) occupied++;
            if (occupied >= (max[y] - min[y] + 1) * .9f) extendedSolidFoot++;
        }
        // A tilted 2's full terminal can end above the last antialiased rows.
        // Its solid foot extends beyond the preceding diagonal, not around a lower bowl.
        return extendedSolidFoot < required
                && lowerLobe - foot >= indentation
                && upper
                && lower
                && upperLobe - waist >= indentation
                && (lowerLobe - waist >= indentation
                        || lowerLobe >= w * .5f
                                && lowerOpen >= required
                                && lowerOpenLeft - waistLeft >= indentation
                                && upperLobe - capLeft >= Math.max(2, (int) Math.ceil(w * .25f)));
    }

    private static boolean hasLobePocket(byte[] gray, int width, int left, int y, int w) {
        int run = 0;
        for (int x = Math.round(w * .25f); x < w; x++) {
            if (!dark(gray, width, left + x, y)) run++;
            else {
                if (run >= Math.max(2, Math.round(w * .22f)) && x >= w * .55f) return true;
                run = 0;
            }
        }
        return false;
    }

    private static boolean bracketArm(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap) {
        left = Math.max(0, left);
        right = Math.min(width - 1, right);
        if (right - left < gap) return false;
        int occupied = 0;
        for (int x = left; x <= right; x++) {
            // Sloped brackets can rise above the numeral at their outer ends.
            // Allow half a staff space while still requiring both long arms.
            for (int y = Math.max(0, top - Math.round(gap * .5f));
                    y <= Math.min(height - 1, bottom + Math.round(gap * .5f));
                    y++)
                if (dark(gray, width, x, y)) {
                    occupied++;
                    break;
                }
        }
        return occupied >= (right - left + 1) * .76f;
    }

    private static boolean dark(byte[] gray, int width, int x, int y) {
        return (gray[y * width + x] & 0xff) <= 165;
    }
}
