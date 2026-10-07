// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Rejects an inferred separator contradicted by two complete written voices. */
final class PrintedMeasureRhythmGuard {
    private PrintedMeasureRhythmGuard() {}

    static List<MeasureRegion> reconcile(
            List<MeasureRegion> raw,
            List<MeasureRegion> fitted,
            byte[] labels,
            byte[] gray,
            int width,
            int height) {
        raw = rejectOverlappingRows(raw);
        fitted = rejectOverlappingRows(fitted);
        fitted = preservePrintedBoundaries(raw, fitted, gray, width, height);
        fitted = rejectUnprintedRows(raw, fitted, labels, gray, width, height);
        final var guarded = fitted;
        boolean split = raw.stream().anyMatch(region -> fragments(region, guarded).size() == 2);
        if (!split && mergedOpening(raw, fitted).isEmpty()) return fitted;
        var notes = OmrScoreInterpreter.analyze(labels, gray, width, height, raw).notes();
        var preserved = preserveWrittenOpening(raw, fitted, notes);
        if (!split) return preserved;
        var aligned = alignPrintedSeparators(raw, preserved, notes, gray, width, height);
        return preserveCompleteRuns(raw, aligned, notes, gray, width, height);
    }

    /** Ledger lines below a staff can resemble a short, overlapping second system. */
    static List<MeasureRegion> rejectOverlappingRows(List<MeasureRegion> regions) {
        return regions.stream()
                .filter(
                        candidate -> {
                            float height = candidate.bottom() - candidate.top();
                            if (height <= 0) return true;
                            var upper =
                                    regions.stream()
                                            .filter(
                                                    other ->
                                                            other.top() < candidate.top() - .01f
                                                                    && Math.min(
                                                                                            other
                                                                                                    .bottom(),
                                                                                            candidate
                                                                                                    .bottom())
                                                                                    - candidate
                                                                                            .top()
                                                                            > .2f
                                                                                    * Math.min(
                                                                                            height,
                                                                                            other
                                                                                                            .bottom()
                                                                                                    - other
                                                                                                            .top()))
                                            .toList();
                            long crossedSeparators =
                                    upper.stream()
                                            .filter(
                                                    other ->
                                                            other.right() > candidate.left() + .01f
                                                                    && other.right()
                                                                            < candidate.right()
                                                                                    - .01f)
                                            .map(MeasureRegion::right)
                                            .distinct()
                                            .count();
                            return crossedSeparators < 2;
                        })
                .toList();
    }

    /** Number OCR may propose a missing row, but text in a footer is not a staff. */
    static List<MeasureRegion> rejectUnprintedRows(
            List<MeasureRegion> raw,
            List<MeasureRegion> fitted,
            byte[] labels,
            byte[] gray,
            int width,
            int height) {
        if (raw.isEmpty()
                || labels == null
                || gray == null
                || labels.length != width * height
                || gray.length != width * height) return fitted;
        List<MeasureRegion> result = new ArrayList<>();
        for (var region : fitted) {
            if (raw.stream()
                    .anyMatch(r -> r.top() <= region.bottom() && r.bottom() >= region.top())) {
                result.add(region);
                continue;
            }
            int left = Math.max(0, Math.round(region.left() * width));
            int right = Math.min(width, Math.round(region.right() * width));
            int top = Math.max(0, Math.round(region.top() * height));
            int bottom = Math.min(height, Math.round(region.bottom() * height));
            int w = right - left, h = bottom - top, staff = 0;
            if (w <= 0 || h <= 0) continue;
            byte[] crop = new byte[w * h];
            for (int y = top; y < bottom; y++)
                for (int x = left; x < right; x++) {
                    int at = y * width + x;
                    if (labels[at] == OmrMeasurePostProcessor.STAFF) staff++;
                    crop[(y - top) * w + x - left] = gray[at];
                }
            if (staff >= w * 2 || !RawStaffLineDetector.detect(crop, w, h).isEmpty())
                result.add(region);
        }
        return List.copyOf(result);
    }

    /** OCR counts cannot erase separators visibly spanning the original measure row. */
    static List<MeasureRegion> preservePrintedBoundaries(
            List<MeasureRegion> raw,
            List<MeasureRegion> fitted,
            byte[] gray,
            int width,
            int height) {
        if (gray == null || gray.length != width * height) return fitted;
        List<MeasureRegion> result = new ArrayList<>();
        for (var region : fitted) {
            var pieces = fragments(region, raw);
            boolean complete =
                    pieces.size() > 1
                            && Math.abs(pieces.get(0).left() - region.left()) < .006f
                            && Math.abs(pieces.get(pieces.size() - 1).right() - region.right())
                                    < .006f;
            for (int i = 1; complete && i < pieces.size(); i++) {
                float cut = (pieces.get(i - 1).right() + pieces.get(i).left()) * .5f;
                complete = printedBarline(region, cut, gray, width, height);
            }
            if (complete) result.addAll(pieces);
            else result.add(region);
        }
        return List.copyOf(result);
    }

    /** A short, note-bearing opening bar must not be merged to match later row counts. */
    static List<MeasureRegion> preserveWrittenOpening(
            List<MeasureRegion> raw, List<MeasureRegion> fitted, List<ScoreNoteEvent> notes) {
        var pieces = mergedOpening(raw, fitted);
        if (pieces.isEmpty()
                || notes.stream().noneMatch(n -> n.measureIndex() == 0 && n.compactOpening()))
            return fitted;
        List<MeasureRegion> result = new ArrayList<>(pieces);
        result.addAll(fitted.subList(1, fitted.size()));
        return List.copyOf(result);
    }

    private static List<MeasureRegion> mergedOpening(
            List<MeasureRegion> raw, List<MeasureRegion> fitted) {
        if (raw.size() < 2 || fitted.isEmpty()) return List.of();
        var combined = fitted.get(0);
        if (!sameRow(raw.get(0), combined)
                || combined.left() > raw.get(0).left() + .006f
                || combined.left() < raw.get(0).left() - .02f) return List.of();
        List<MeasureRegion> pieces = new ArrayList<>();
        for (var region : raw) {
            if (!sameRow(region, combined) || region.right() > combined.right() + .006f) break;
            pieces.add(region);
        }
        if (pieces.size() < 2
                || Math.abs(pieces.get(pieces.size() - 1).right() - combined.right()) > .006f)
            return List.of();
        return pieces;
    }

    static List<MeasureRegion> alignPrintedSeparators(
            List<MeasureRegion> raw,
            List<MeasureRegion> fitted,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height) {
        if (gray == null || gray.length != width * height) return fitted;
        List<MeasureRegion> result = new ArrayList<>(fitted);
        for (int index = 0; index < raw.size(); index++) {
            final int measure = index;
            // On a grand staff, a barline crosses the empty space between staves.
            // A single-staff note stem can be just as tall as its measure region.
            boolean singleStaff =
                    notes.stream()
                                    .filter(n -> n.measureIndex() == measure)
                                    .map(ScoreNoteEvent::staffIndex)
                                    .distinct()
                                    .count()
                            < 2;
            var region = raw.get(index);
            var pieces = fragments(region, result);
            if (pieces.size() != 2
                    || Math.abs(pieces.get(0).left() - region.left()) > .006f
                    || Math.abs(pieces.get(1).right() - region.right()) > .006f) continue;
            if (singleStaff) {
                Float verified =
                        PrintedVoiceMeasureCut.resolve(region, notes, measure, gray, width, height);
                if (verified != null) {
                    float spacing = Math.min(.004f, (region.right() - region.left()) * .04f);
                    int at = result.indexOf(pieces.get(0));
                    result.set(
                            at,
                            new MeasureRegion(
                                    region.left(),
                                    verified - spacing * .5f,
                                    region.top(),
                                    region.bottom()));
                    result.set(
                            at + 1,
                            new MeasureRegion(
                                    verified + spacing * .5f,
                                    region.right(),
                                    region.top(),
                                    region.bottom()));
                }
                continue;
            }
            int top = Math.max(0, Math.round(region.top() * height));
            int bottom = Math.min(height - 1, Math.round(region.bottom() * height));
            float span = region.right() - region.left();
            int left = Math.max(0, Math.round((region.left() + span * .15f) * width));
            int right = Math.min(width - 1, Math.round((region.right() - span * .15f) * width));
            float inferred = (pieces.get(0).right() + pieces.get(1).left()) * .5f * width;
            int best = -1;
            double bestScore = 0;
            for (int x = left; x <= right; x++) {
                int ink = 0;
                for (int y = top; y <= bottom; y++) if ((gray[y * width + x] & 255) < 180) ink++;
                if (ink <= (bottom - top + 1) * .55f) continue;
                double score = ink - Math.abs(x - inferred) * .02;
                if (score > bestScore) {
                    best = x;
                    bestScore = score;
                }
            }
            if (best < 0) continue;
            float cut = best / (float) width, gap = Math.min(.004f, span * .04f);
            int at = result.indexOf(pieces.get(0));
            result.set(
                    at,
                    new MeasureRegion(region.left(), cut - gap / 2, region.top(), region.bottom()));
            result.set(
                    at + 1,
                    new MeasureRegion(
                            cut + gap / 2, region.right(), region.top(), region.bottom()));
        }
        return List.copyOf(result);
    }

    private record Span(double beats, int groups, boolean allBeamed) {}

    static List<MeasureRegion> preserveCompleteRuns(
            List<MeasureRegion> raw,
            List<MeasureRegion> fitted,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height) {
        List<MeasureRegion> result = new ArrayList<>(fitted);
        for (int index = 0; index < raw.size(); index++) {
            MeasureRegion region = raw.get(index);
            List<MeasureRegion> pieces = fragments(region, result);
            if (pieces.size() != 2
                    || Math.abs(pieces.get(0).left() - region.left()) > .006f
                    || Math.abs(pieces.get(1).right() - region.right()) > .006f) continue;
            float cut = (pieces.get(0).right() + pieces.get(1).left()) * .5f;
            if (printedBarline(region, cut, gray, width, height)) continue;
            List<Span> voices = spans(notes, index);
            boolean complete = false;
            for (Span voice : voices) {
                if (!voice.allBeamed || voice.groups < 8 || voice.beats < .5 || voice.beats > 16)
                    continue;
                if (voices.stream().filter(other -> same(other.beats, voice.beats)).count() < 2)
                    continue;
                int matchingBars = 0;
                for (int neighbor = 0; neighbor < raw.size(); neighbor++) {
                    if (neighbor == index
                            || !sameRow(raw.get(neighbor), region)
                            || fragments(raw.get(neighbor), fitted).size() != 1) continue;
                    if (spans(notes, neighbor).stream()
                            .anyMatch(other -> same(other.beats, voice.beats))) matchingBars++;
                }
                if (matchingBars >= 2) {
                    complete = true;
                    break;
                }
            }
            if (!complete) continue;
            int at = result.indexOf(pieces.get(0));
            result.removeAll(pieces);
            result.add(at, region);
        }
        return List.copyOf(result);
    }

    private static boolean same(double a, double b) {
        return Math.abs(a - b) < .01;
    }

    private static boolean sameRow(MeasureRegion a, MeasureRegion b) {
        return Math.abs(a.top() - b.top()) < .006f && Math.abs(a.bottom() - b.bottom()) < .006f;
    }

    private static List<MeasureRegion> fragments(MeasureRegion region, List<MeasureRegion> fitted) {
        return fitted.stream()
                .filter(
                        part ->
                                sameRow(region, part)
                                        && part.left() >= region.left() - .006f
                                        && part.right() <= region.right() + .006f)
                .sorted(Comparator.comparingDouble(MeasureRegion::left))
                .collect(java.util.stream.Collectors.toList());
    }

    private static List<Span> spans(List<ScoreNoteEvent> notes, int measure) {
        List<Span> result = new ArrayList<>();
        var lanes =
                notes.stream()
                        .filter(n -> n.measureIndex() == measure)
                        .map(n -> n.staffCount() * 16 + n.staffIndex())
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());
        for (int lane : lanes) {
            var voice =
                    notes.stream()
                            .filter(
                                    n ->
                                            n.measureIndex() == measure
                                                    && n.staffCount() * 16 + n.staffIndex() == lane)
                            .sorted(Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure))
                            .collect(java.util.stream.Collectors.toList());
            double beats = 0;
            int groups = 0;
            boolean beamed = true, valid = true;
            for (int start = 0; start < voice.size(); ) {
                int end = start;
                double duration = 0, rest = 0, leading = 0;
                while (end < voice.size()
                        && voice.get(end).positionInMeasure() - voice.get(start).positionInMeasure()
                                <= .018f) {
                    var note = voice.get(end++);
                    double written = ScoreNoteTiming.writtenDurationBeats(note);
                    if (!Double.isFinite(written)
                            || (note.articulations() & NoteOrnament.GRACE) != 0) valid = false;
                    duration = Math.max(duration, written);
                    rest = Math.max(rest, note.followingRestBeats());
                    leading = Math.max(leading, note.leadingRestBeats());
                    beamed &= note.beamCount() > 0 && rest == 0 && leading == 0;
                }
                beats += duration + rest + (groups == 0 ? leading : 0);
                groups++;
                start = end;
            }
            if (valid) result.add(new Span(beats, groups, beamed));
        }
        return result;
    }

    private static boolean printedBarline(
            MeasureRegion region, float cut, byte[] gray, int width, int height) {
        if (gray == null || gray.length != width * height) return true;
        int top = Math.max(0, Math.round(region.top() * height));
        int bottom = Math.min(height - 1, Math.round(region.bottom() * height));
        int center = Math.round(cut * width), radius = Math.max(3, Math.round(width * .006f));
        for (int x = Math.max(0, center - radius); x <= Math.min(width - 1, center + radius); x++) {
            int ink = 0;
            for (int y = top; y <= bottom; y++) if ((gray[y * width + x] & 255) < 180) ink++;
            if (ink > (bottom - top + 1) * .55f) return true;
        }
        // Duet/orchestral separators may stop at each staff instead of crossing
        // the intervening whitespace. Require complete columns on two actual
        // five-line staffs; one note stem or a few isolated strokes cannot qualify.
        int left = Math.max(0, Math.round(region.left() * width));
        int right = Math.min(width - 1, Math.round(region.right() * width));
        int cropWidth = right - left + 1, cropHeight = bottom - top + 1;
        if (cropWidth < 24 || cropHeight < 20) return false;
        byte[] crop = new byte[cropWidth * cropHeight];
        for (int y = top; y <= bottom; y++)
            System.arraycopy(gray, y * width + left, crop, (y - top) * cropWidth, cropWidth);
        var staffs = RawStaffLineDetector.detect(crop, cropWidth, cropHeight);
        if (staffs.size() < 2) return false;
        for (int x = Math.max(left, center - radius); x <= Math.min(right, center + radius); x++) {
            int supported = 0;
            for (var staff : staffs) {
                int ink = 0;
                for (int y = staff.top() + top; y <= staff.bottom() + top; y++) {
                    boolean dark = false;
                    for (int dx = -1; dx <= 1; dx++)
                        if (x + dx >= 0 && x + dx < width && (gray[y * width + x + dx] & 255) < 180)
                            dark = true;
                    if (dark) ink++;
                }
                if (ink >= (staff.bottom() - staff.top() + 1) * .90f) supported++;
            }
            if (supported >= 2) return true;
        }
        return false;
    }
}
