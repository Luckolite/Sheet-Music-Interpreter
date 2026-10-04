// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Bounded crops of stacked signature glyphs, including signatures inside a system. */
public final class MeterChangeDetector {
    public record Crop(int left, int top, int right, int bottom, int firstLine, float gap) {}

    private record SignatureStaff(RawStaffLineDetector.StaffLines lines, float slope) {}

    /** Engraved C / cut-C signs, independent of digit OCR; conflicting staff copies abstain. */
    public static List<ScoreMeterChange> commonTimeReadings(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes) {
        if (labels == null
                || gray == null
                || labels.length != width * height
                || gray.length != width * height) return List.of();
        var readings = new java.util.TreeMap<Integer, java.util.Set<Integer>>();
        for (var geometry : signatureStaffs(labels, gray, width, height))
            for (var reading :
                    CommonTimeMeter.candidates(
                            labels,
                            gray,
                            width,
                            height,
                            geometry.lines().top(),
                            geometry.lines().gap(),
                            geometry.slope())) {
                var crop = reading.crop();
                int measure = followingMeasure(crop, width, height, measures);
                if (measure < 0
                        || !precedesNotes(
                                crop, labels, gray, width, height, measure, measures, notes))
                    continue;
                readings.computeIfAbsent(measure, ignored -> new java.util.HashSet<>())
                        .add(reading.numerator());
            }
        var result = new ArrayList<ScoreMeterChange>();
        for (var entry : readings.entrySet())
            if (entry.getValue().size() == 1) {
                int value = entry.getValue().iterator().next();
                result.add(new ScoreMeterChange(entry.getKey(), value, value));
            }
        return List.copyOf(result);
    }

    /** Resolve contradictory staff copies only when two complete written bars support one reading. */
    static ScoreMeterChange resolveConflict(
            java.util.Set<ScoreMeterChange> choices, List<ScoreNoteEvent> notes, int nextChange) {
        ScoreMeterChange winner = null;
        for (var choice : choices) {
            int supportedBars = 0;
            for (int bar = choice.measureIndex();
                    bar < Math.min(nextChange, choice.measureIndex() + 3);
                    bar++) {
                java.util.Map<Integer, List<ScoreNoteEvent>> lanes = new java.util.HashMap<>();
                for (var note : notes)
                    if (note.measureIndex() == bar)
                        lanes.computeIfAbsent(
                                        note.staffCount() * 16 + note.staffIndex(),
                                        ignored -> new ArrayList<>())
                                .add(note);
                boolean supported = false;
                for (var lane : lanes.values()) {
                    lane.sort(
                            java.util.Comparator.comparingDouble(
                                    ScoreNoteEvent::positionInMeasure));
                    double total = 0, duration = 0;
                    float position = -1;
                    boolean reliable = true;
                    for (var note : lane) {
                        if (note.crossStaffBeam()
                                || (note.beamCount() == 0 && note.unbeamedDurationBeats() == 0)) {
                            reliable = false;
                            break;
                        }
                        if (position < 0 || note.positionInMeasure() - position > .018f) {
                            total += duration;
                            duration = 0;
                            position = note.positionInMeasure();
                        }
                        duration =
                                Math.max(
                                        duration,
                                        ScoreNoteTiming.writtenDurationBeats(note)
                                                + note.followingRestBeats()
                                                + note.leadingRestBeats());
                    }
                    if (reliable && Math.abs(total + duration - choice.quarterBeats()) < .01)
                        supported = true;
                }
                if (supported) supportedBars++;
            }
            if (supportedBars >= 2) {
                if (winner != null)
                    return null; // Equal-duration alternatives such as 3/4 and 6/8 stay ambiguous.
                winner = choice;
            }
        }
        return winner;
    }

    /**
     * Reject a lone OCR meter reading only when two later printed bars independently agree on
     * the same different duration. Sparse or partly unreadable passages remain inconclusive.
     */
    public static boolean contradictedByWrittenBars(
            ScoreMeterChange choice, List<ScoreNoteEvent> notes, int nextChange) {
        var alternatives = new ArrayList<Double>();
        for (int bar = choice.measureIndex();
                bar < Math.min(nextChange, choice.measureIndex() + 3);
                bar++) {
            List<Double> totals = reliableLaneTotals(notes, bar);
            if (totals.stream().anyMatch(total -> closeDuration(total, choice.quarterBeats())))
                return false;
            for (double total : totals) {
                if (total <= 0) continue;
                int supportingBars = 1;
                for (int later = bar + 1;
                        later < Math.min(nextChange, choice.measureIndex() + 3);
                        later++)
                    if (reliableLaneTotals(notes, later).stream()
                            .anyMatch(other -> closeDuration(other, total))) supportingBars++;
                if (supportingBars >= 2) alternatives.add(total);
            }
        }
        return !alternatives.isEmpty();
    }

    private static List<Double> reliableLaneTotals(List<ScoreNoteEvent> notes, int bar) {
        java.util.Map<Integer, List<ScoreNoteEvent>> lanes = new java.util.HashMap<>();
        for (var note : notes)
            if (note.measureIndex() == bar)
                lanes.computeIfAbsent(
                                note.staffCount() * 16 + note.staffIndex(),
                                ignored -> new ArrayList<>())
                        .add(note);
        var totals = new ArrayList<Double>();
        for (var lane : lanes.values()) {
            lane.sort(java.util.Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure));
            double total = 0, duration = 0;
            float position = -1;
            boolean reliable = true;
            for (var note : lane) {
                if (note.crossStaffBeam()
                        || (note.beamCount() == 0 && note.unbeamedDurationBeats() == 0)) {
                    reliable = false;
                    break;
                }
                if (position < 0 || note.positionInMeasure() - position > .018f) {
                    total += duration;
                    duration = 0;
                    position = note.positionInMeasure();
                }
                duration =
                        Math.max(
                                duration,
                                ScoreNoteTiming.writtenDurationBeats(note)
                                        + note.followingRestBeats()
                                        + note.leadingRestBeats());
            }
            if (reliable && position >= 0) totals.add(total + duration);
        }
        return totals;
    }

    private static boolean closeDuration(double left, double right) {
        return Math.abs(left - right) < .04;
    }

    /** Tiny stem/rest crops can OCR as 1/1, 2/1, 1/2 or 1/4. Reject those readings only
     * when multiple complete written bars contradict them. Other readings remain
     * unchanged: missed beams can make even genuine compound bars disagree. */
    public static List<ScoreMeterChange> filterWholeNoteOcrReadings(
            List<ScoreMeterChange> readings, List<ScoreNoteEvent> notes, int measureCount) {
        var sorted =
                readings.stream()
                        .sorted(java.util.Comparator.comparingInt(ScoreMeterChange::measureIndex))
                        .collect(java.util.stream.Collectors.toList());
        var result = new ArrayList<ScoreMeterChange>();
        for (int i = 0; i < sorted.size(); i++) {
            var choice = sorted.get(i);
            int next = i + 1 < sorted.size() ? sorted.get(i + 1).measureIndex() : measureCount;
            boolean suspicious =
                    choice.denominator() == 1
                            || choice.numerator() == 1
                                    && (choice.denominator() == 2 || choice.denominator() == 4);
            if (!suspicious || !contradictedByWrittenBars(choice, notes, next)) result.add(choice);
        }
        return List.copyOf(result);
    }

    public static List<Crop> candidates(byte[] labels, byte[] gray, int width, int height) {
        List<Crop> result = new ArrayList<>();
        if (labels == null
                || gray == null
                || labels.length != width * height
                || gray.length != width * height) return result;
        for (var geometry : signatureStaffs(labels, gray, width, height)) {
            var staff = geometry.lines();
            float slope = geometry.slope();
            float gap = staff.gap();
            int top = Math.max(0, staff.top()), bottom = Math.min(height - 1, staff.bottom());
            int radius = Math.max(1, Math.round(gap * .12f));
            int firstHead = width, firstSemanticHead = width;
            for (int xx = 0; xx < width; xx++) {
                int heads = 0;
                for (int yy = Math.max(0, top - Math.round(gap));
                        yy <= Math.min(height - 1, bottom + Math.round(gap));
                        yy++)
                    if (labels[yy * width + xx] == OmrMeasurePostProcessor.NOTEHEAD) heads++;
                if (heads >= Math.max(7, Math.round(gap * .48f))) {
                    if (firstHead == width) firstHead = xx;
                    int key = 0;
                    for (int x = Math.max(0, xx - Math.round(gap));
                            x <= Math.min(width - 1, xx + Math.round(gap));
                            x++)
                        for (int yy = Math.max(0, top - Math.round(gap));
                                yy <= Math.min(height - 1, bottom + Math.round(gap));
                                yy++)
                            if (labels[yy * width + x] == OmrMeasurePostProcessor.CLEF_OR_KEY)
                                key++;
                    if (key < gap * gap * .35f) {
                        firstSemanticHead = xx;
                        break;
                    }
                }
            }
            int[] ink = new int[width];
            for (int y = top; y <= bottom; y++) {
                boolean line = false;
                for (int row : staff.rows())
                    if (Math.abs(y - row) <= radius) {
                        line = true;
                        break;
                    }
                if (line) continue;
                for (int x = 0; x < width; x++) {
                    int yy = y + Math.round(slope * (x - width * .5f));
                    if (yy >= 0 && yy < height && (gray[yy * width + x] & 255) < 155) ink[x]++;
                }
            }
            int maxBlank = Math.max(1, Math.round(gap * .22f));
            for (int x = 0; x < width; x++) {
                if (ink[x] < 2) continue;
                int left = x, last = x, blanks = 0;
                for (; x < width; x++) {
                    if (ink[x] >= 2) {
                        last = x;
                        blanks = 0;
                    } else if (++blanks > maxBlank) break;
                }
                int span = last - left + 1;
                // Misaligned or curved rules can join digits to neighboring ink.
                // Recover a bounded crop only from two aligned triangular counters;
                // the OCR and note-position checks still decide its meaning.
                if (span > gap * 3.2f) {
                    for (int xx = left; xx <= last; xx++) {
                        if (ink[xx] < gap * 1.4f) continue;
                        int shift = Math.round(slope * (xx - width * .5f));
                        if (!OmrMeasurePostProcessor.stackedFourCounters(
                                gray, width, height, xx, top + shift, bottom + shift, gap))
                            continue;
                        int end = xx;
                        for (int next = xx + 1;
                                next <= Math.min(last, xx + Math.round(gap * .8f));
                                next++) {
                            int offset = Math.round(slope * (next - width * .5f));
                            if (OmrMeasurePostProcessor.stackedFourCounters(
                                    gray, width, height, next, top + offset, bottom + offset, gap))
                                end = next;
                        }
                        int center = (xx + end) / 2,
                                localTop = top + Math.round(slope * (center - width * .5f));
                        int pad = Math.max(2, Math.round(gap * .18f));
                        result.add(
                                new Crop(
                                        Math.max(0, Math.round(center - gap * 1.6f)),
                                        Math.max(0, localTop - pad),
                                        Math.min(width, Math.round(center + gap)),
                                        Math.min(height, localTop + bottom - top + pad + 1),
                                        localTop,
                                        gap));
                        if (result.size() >= 48) return List.copyOf(result);
                        xx = end + Math.round(gap);
                    }
                    if (left < firstHead - gap * .35f)
                        result.addAll(
                                stackedSymbolMeterCrops(
                                        labels,
                                        width,
                                        height,
                                        left,
                                        Math.min(
                                                last,
                                                firstHead - Math.max(1, Math.round(gap * .35f))),
                                        top,
                                        bottom,
                                        gap,
                                        slope,
                                        false));
                    if (firstSemanticHead != firstHead && left < firstSemanticHead - gap * .35f)
                        for (var crop :
                                stackedSymbolMeterCrops(
                                        labels,
                                        width,
                                        height,
                                        left,
                                        Math.min(
                                                last,
                                                firstSemanticHead
                                                        - Math.max(1, Math.round(gap * .35f))),
                                        top,
                                        bottom,
                                        gap,
                                        slope,
                                        true))
                            if (!result.contains(crop)
                                    && pairedStackedSymbols(labels, width, height, crop))
                                result.add(crop);
                    if (result.size() >= 48) return List.copyOf(result.subList(0, 48));
                }
                if (span < gap * .5f || span > gap * 3.2f) continue;
                int key = 0,
                        head = 0,
                        upperHead = 0,
                        upper = 0,
                        lower = 0,
                        symbols = 0,
                        upperSymbols = 0,
                        lowerSymbols = 0;
                int[] stemRows = new int[span];
                long upperX = 0, lowerX = 0;
                int minY = height, maxY = 0;
                for (int y = top; y <= bottom; y++)
                    for (int xx = left; xx <= last; xx++) {
                        int yy = y + Math.round(slope * (xx - width * .5f));
                        if (yy < 0 || yy >= height) continue;
                        byte label = labels[yy * width + xx];
                        if (label == OmrMeasurePostProcessor.CLEF_OR_KEY) key++;
                        if (label == OmrMeasurePostProcessor.NOTEHEAD) {
                            head++;
                            if (y < top + gap * 1.8f) upperHead++;
                        }
                        if (label == OmrMeasurePostProcessor.STEM_OR_REST) stemRows[xx - left]++;
                        if (label == OmrMeasurePostProcessor.SYMBOL) {
                            symbols++;
                            if (y < top + gap * 1.8f) upperSymbols++;
                            if (y > top + gap * 2.2f) lowerSymbols++;
                        }
                        boolean onLine = false;
                        for (int row : staff.rows())
                            if (Math.abs(y - row) <= radius) {
                                onLine = true;
                                break;
                            }
                        if (!onLine && (gray[yy * width + xx] & 255) < 155) {
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y);
                            if (y < top + gap * 1.8f) {
                                upper++;
                                upperX += xx - left;
                            }
                            if (y > top + gap * 2.2f) {
                                lower++;
                                lowerX += xx - left;
                            }
                        }
                    }
                // HOMR commonly leaves signature digits as background/rest and occasionally
                // labels a tiny part of an "8" as a head. Use raw stacked-glyph evidence; the
                // full-sized semantic head veto still excludes actual notes/chords.
                // A bottom 8 may be labelled as one or two noteheads while the
                // numerator remains ordinary meter ink. A complete preceding
                // barline allows OCR to judge this bounded signature crop.
                boolean lowerEightAfterBarline =
                        head > gap * gap * .20f
                                && upperHead < head * .1f
                                && completeBarlineBefore(
                                        gray, width, height, left, top, bottom, gap);
                if ((head > gap * gap * .20f && !lowerEightAfterBarline)
                        || upper < gap * 2
                        || lower < gap * 2
                        || maxY - minY < gap * 3.1f) continue;
                // Staggered key-signature flats form two apparent digits after staff removal.
                // Require both key evidence and staggered glyph centres; real stacked digits
                // can also be labelled as key ink, and must survive that model error.
                if (key > gap * gap * .5f
                        && key > symbols * 2
                        && Math.abs(upperX / (float) upper - lowerX / (float) lower) > gap * .5f)
                    continue;
                // A rhythmic slash has one stem through the whole staff, not two digits.
                // Require a visible slash label as well: a barline beside unknown-label
                // digits is not rhythmic slash notation.
                boolean continuousStem = false;
                if (symbols >= gap * 2 && Math.min(upperSymbols, lowerSymbols) < gap)
                    for (int count : stemRows)
                        if (count > (bottom - top + 1) * .75f) {
                            continuousStem = true;
                            break;
                        }
                if (continuousStem) continue;
                int pad = Math.max(2, Math.round(gap * .18f));
                int shiftLeft = Math.round(slope * (left - width * .5f));
                int shiftRight = Math.round(slope * (last - width * .5f));
                int firstLine =
                        staff.top() + Math.round(slope * ((left + last) * .5f - width * .5f));
                result.add(
                        new Crop(
                                Math.max(0, left - pad),
                                Math.max(0, top + Math.min(shiftLeft, shiftRight) - pad),
                                Math.min(width, last + pad + 1),
                                Math.min(
                                        height, bottom + Math.max(shiftLeft, shiftRight) + pad + 1),
                                firstLine,
                                gap));
                if (result.size() >= 48) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }

    private static boolean completeBarlineBefore(
            byte[] gray, int width, int height, int left, int top, int bottom, float gap) {
        for (int x = Math.max(0, Math.round(left - gap * 3f)); x < left - gap * .5f; x++) {
            int ink = 0, total = 0;
            for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++) {
                total++;
                if ((gray[y * width + x] & 255) < 155) ink++;
            }
            if (total >= gap * 3.8f && ink >= total * .94f) return true;
        }
        return false;
    }

    /** Recover stacked meter digits when a clef or nearby stem joins their ink projection.
     * The note-position and OCR checks still decide whether a crop is a signature. */
    private static boolean pairedStackedSymbols(byte[] labels, int width, int height, Crop crop) {
        int gap = Math.round(crop.gap()),
                shift = gap * 2,
                upper = 0,
                lower = 0,
                shared = 0,
                combined = 0;
        int margin = Math.max(1, Math.round(crop.gap() * .12f));
        for (int y = crop.firstLine() + 3; y < crop.firstLine() + shift - 3; y++) {
            if (y < 0 || y + shift >= height) continue;
            if (Math.abs(y - crop.firstLine() - gap) <= margin) continue;
            for (int x = crop.left(); x < crop.right(); x++) {
                boolean a = labels[y * width + x] == OmrMeasurePostProcessor.SYMBOL;
                boolean b = labels[(y + shift) * width + x] == OmrMeasurePostProcessor.SYMBOL;
                if (a) upper++;
                if (b) lower++;
                if (a && b) shared++;
                if (a || b) combined++;
            }
        }
        return Math.min(upper, lower) >= crop.gap() * crop.gap() * .8f
                && combined > 0
                && shared >= combined * .38f;
    }

    private static List<Crop> stackedSymbolMeterCrops(
            byte[] labels,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            float slope,
            boolean tolerateGlyphHead) {
        var result = new ArrayList<Crop>();
        int start = -1, last = -1, blanks = 0;
        int minSymbol = Math.max(3, Math.round(gap * .25f));
        for (int x = left; x <= right + 3; x++) {
            int upper = 0, lower = 0;
            if (x <= right)
                for (int y = top; y <= bottom; y++) {
                    int yy = y + Math.round(slope * (x - width * .5f));
                    if (yy < 0
                            || yy >= height
                            || labels[yy * width + x] != OmrMeasurePostProcessor.SYMBOL) continue;
                    if (y < top + gap * 1.8f) upper++;
                    if (y > top + gap * 2.2f) lower++;
                }
            if (upper >= minSymbol && lower >= minSymbol) {
                if (start < 0) start = x;
                last = x;
                blanks = 0;
            } else if (start >= 0 && ++blanks > 2) {
                int glyphWidth = last - start + 1;
                if (glyphWidth >= gap * .55f && glyphWidth <= gap * 2.6f) {
                    int head = 0, middleHead = 0, header = 0;
                    for (int xx = start; xx <= last; xx++)
                        for (int y = top; y <= bottom; y++) {
                            int yy = y + Math.round(slope * (xx - width * .5f));
                            if (yy >= 0
                                    && yy < height
                                    && labels[yy * width + xx]
                                            == OmrMeasurePostProcessor.NOTEHEAD) {
                                head++;
                                if (Math.abs(y - top - gap * 2) < gap * .4f) middleHead++;
                            }
                        }
                    for (int xx = Math.max(0, start - Math.round(gap * 6));
                            xx < start - Math.round(gap * .5f);
                            xx++)
                        for (int y = top; y <= bottom; y++) {
                            int yy = y + Math.round(slope * (xx - width * .5f));
                            if (yy >= 0
                                    && yy < height
                                    && labels[yy * width + xx]
                                            == OmrMeasurePostProcessor.CLEF_OR_KEY) header++;
                        }
                    boolean smallGlyphFragment =
                            tolerateGlyphHead
                                    && head < gap * gap * .2f
                                    && middleHead < gap * gap * .05f;
                    if ((head < gap * gap * .12f || smallGlyphFragment)
                            && header >= gap * gap * .35f) {
                        int center = (start + last) / 2, pad = Math.max(2, Math.round(gap * .2f));
                        int localTop = top + Math.round(slope * (center - width * .5f));
                        result.add(
                                new Crop(
                                        Math.max(0, start - pad),
                                        Math.max(0, localTop - pad),
                                        Math.min(width, last + pad + 1),
                                        Math.min(height, localTop + bottom - top + pad + 1),
                                        localTop,
                                        gap));
                    }
                }
                start = -1;
                last = -1;
                blanks = 0;
            }
        }
        return result;
    }

    /** Faded rules can retain semantic staff evidence while falling below the raw ink cutoff. */
    private static List<SignatureStaff> signatureStaffs(
            byte[] labels, byte[] gray, int width, int height) {
        var staffs = new ArrayList<SignatureStaff>();
        for (var raw : RawStaffLineDetector.detect(gray, width, height))
            staffs.add(new SignatureStaff(raw, 0));
        float slope = OmrMeasurePostProcessor.estimateStaffSlope(labels, width, height);
        int[] strength = new int[height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.STAFF) {
                    int row = Math.round(y - slope * (x - width * .5f));
                    if (row >= 0 && row < height) strength[row]++;
                }
        for (var candidate :
                RawStaffLineDetector.detectFromStrength(
                        strength, Math.max(10, width / 80), height)) {
            boolean represented = false;
            for (var geometry : staffs) {
                var existing = geometry.lines();
                if (Math.abs(existing.center() - candidate.center())
                        < Math.max(existing.gap(), candidate.gap()) * 2) {
                    represented = true;
                    break;
                }
            }
            if (!represented && printedStaffSupport(gray, width, height, candidate, slope))
                staffs.add(new SignatureStaff(candidate, slope));
        }
        staffs.sort(java.util.Comparator.comparingInt(item -> item.lines().top()));
        return List.copyOf(staffs);
    }

    /** Require long thin printed rules; model labels alone cannot turn text or artwork into a staff. */
    private static boolean printedStaffSupport(
            byte[] gray,
            int width,
            int height,
            RawStaffLineDetector.StaffLines staff,
            float slope) {
        int radius = Math.max(1, Math.round(staff.gap() * .15f));
        int flank = Math.max(2, Math.round(staff.gap() * .35f)), supported = 0;
        for (int row : staff.rows()) {
            int hits = 0;
            for (int x = 0; x < width; x++) {
                int center = row + Math.round(slope * (x - width * .5f));
                for (int y = Math.max(flank, center - radius);
                        y <= Math.min(height - 1 - flank, center + radius);
                        y++) {
                    int ink = gray[y * width + x] & 255;
                    if (ink <= 225
                            && (gray[(y - flank) * width + x] & 255) >= ink + 12
                            && (gray[(y + flank) * width + x] & 255) >= ink + 12) {
                        hits++;
                        break;
                    }
                }
            }
            if (hits >= Math.max(24, Math.round(width * .2f))) supported++;
        }
        return supported >= 3;
    }

    /** Signature precedes the first note of its bar; repeated multi-rest rectangles use the first. */
    public static int followingMeasure(
            Crop crop, int width, int height, List<MeasureRegion> measures) {
        float center = (crop.left() + crop.right()) * .5f / width;
        float y = (crop.top() + crop.bottom()) * .5f / height;
        int best = -1;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < measures.size(); i++) {
            MeasureRegion region = measures.get(i);
            if (y < region.top() || y > region.bottom() || center > region.right()) continue;
            float delta = Math.abs(center - region.left());
            if (center >= region.left() && center <= region.right()) delta = 0;
            if (delta > crop.gap() * 7 / width || delta >= distance) continue;
            best = i;
            distance = delta;
        }
        return best;
    }

    public static boolean precedesNotes(
            Crop crop,
            int width,
            int height,
            int measure,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes) {
        MeasureRegion region = measures.get(measure);
        for (var note : notes) {
            if (note.measureIndex() != measure) continue;
            float y = note.pageY() * height;
            if (y < crop.top() - crop.gap() * 2 || y > crop.bottom() + crop.gap() * 2) continue;
            float x =
                    (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                            * width;
            if (x < crop.left() - crop.gap() * .2f) return false;
        }
        return true;
    }

    /** A pair of chord heads and their shared stem can resemble an open C. */
    public static boolean precedesNotes(
            Crop crop,
            byte[] gray,
            int width,
            int height,
            int measure,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes) {
        return measure >= 0
                && measure < measures.size()
                && precedesNotes(crop, width, height, measure, measures, notes)
                && !overlapsRecognizedNote(crop, width, height, measure, measures, notes)
                && !belongsToNoteAccidental(crop, gray, width, height, measure, measures, notes);
    }

    public static boolean precedesNotes(
            Crop crop,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int measure,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes) {
        return crop != null
                && precedesNotes(crop, gray, width, height, measure, measures, notes)
                && !MeterHeaderInkGuard.owns(crop, labels, gray, width, height);
    }

    public static boolean belongsToNoteAccidental(
            Crop crop,
            byte[] gray,
            int width,
            int height,
            int measure,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes) {
        if (measure < 0 || measure >= measures.size() || crop == null) return false;
        MeasureRegion region = measures.get(measure);
        for (var note : notes) {
            if (note.measureIndex() != measure
                    || note.writtenAccidental() != ScoreNoteEvent.ACCIDENTAL_NATURAL) continue;
            float y = note.pageY() * height;
            if (y < crop.top() - crop.gap() || y > crop.bottom() + crop.gap()) continue;
            float x =
                    (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                            * width;
            if (crop.right() >= x || x - crop.right() > crop.gap() * 2f) continue;
            if (OmrScoreInterpreter.provesNaturalMeterInk(
                    gray,
                    width,
                    height,
                    crop.left(),
                    crop.right(),
                    y,
                    crop.gap(),
                    crop.firstLine())) return true;
        }
        return false;
    }

    static boolean overlapsRecognizedNote(
            Crop crop,
            int width,
            int height,
            int measure,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes) {
        MeasureRegion region = measures.get(measure);
        for (var note : notes) {
            if (note.measureIndex() != measure) continue;
            float y = note.pageY() * height;
            if (y < crop.top() - crop.gap() * .2f || y > crop.bottom() + crop.gap() * .2f) continue;
            float x =
                    (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                            * width;
            if (x >= crop.left() - crop.gap() * .2f && x <= crop.right() + crop.gap() * .2f)
                return true;
        }
        return false;
    }
}
