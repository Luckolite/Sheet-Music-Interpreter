// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Converts the segmentation model's semantic pixel labels into the measure rectangles used by the viewer. */
final class OmrMeasurePostProcessor {
    static final byte STEM_OR_REST = 1;
    static final byte NOTEHEAD = 2;
    static final byte CLEF_OR_KEY = 3;
    static final byte STAFF = 4;
    static final byte SYMBOL = 5;

    /** Upper violin writing can place a head this far beyond the five staff lines. */
    private static final float MAX_HEAD_LEDGER_GAPS = 6.75f;

    /** Faint scanned barlines are commonly mid-gray after PDF rendering. */
    private static final int RAW_BARLINE_DARK = 205;

    /** Two staves in one grand staff are close; consecutive compact violin systems are not. */
    private static final float MAX_GRAND_STAFF_SEPARATION_GAPS = 6.75f;

    /** A printed bracket/shared barline is stronger evidence than whitespace. Duet and orchestral
     * layouts can leave a wider gap between connected staves than a compact grand staff does. */
    private static final float MAX_CONNECTED_STAFF_SEPARATION_GAPS = 16f;

    private OmrMeasurePostProcessor() {}

    static List<MeasureRegion> process(byte[] labels, int width, int height) {
        return process(labels, null, width, height);
    }

    static List<MeasureRegion> process(byte[] labels, byte[] gray, int width, int height) {
        return process(labels, gray, width, height, labels);
    }

    /** Reframe playable headers without reclassifying numeral stems as barlines.
     * Geometry keeps the original segmentation; only header trimming uses the cleaned labels. */
    static List<MeasureRegion> process(
            byte[] labels, byte[] gray, int width, int height, byte[] headerLabels) {
        if (labels == null || width <= 0 || height <= 0 || labels.length != width * height)
            return List.of();
        if (headerLabels == null || headerLabels.length != labels.length) return List.of();
        if (gray != null && gray.length != labels.length) gray = null;
        List<StaffRun> staffs = findStaffs(labels, gray, width, height);
        if (staffs.isEmpty()) return List.of();
        List<SystemRun> systems = mergeAlignedStaffs(staffs, labels, gray, width, height);
        List<MeasureRegion> result = new ArrayList<>();
        for (int i = 0; i < systems.size(); i++) {
            SystemRun system = systems.get(i);
            // Header trimming must see the same ledger range as note recognition.
            // Stop halfway to adjacent systems so their heads cannot trim this header.
            float headTop = system.top - system.gap * MAX_HEAD_LEDGER_GAPS;
            float headBottom = system.bottom + system.gap * MAX_HEAD_LEDGER_GAPS;
            if (i > 0) headTop = Math.max(headTop, (systems.get(i - 1).bottom + system.top) / 2f);
            if (i + 1 < systems.size())
                headBottom = Math.min(headBottom, (system.bottom + systems.get(i + 1).top) / 2f);
            addMeasures(headerLabels, gray, width, height, system, result, headTop, headBottom);
        }
        // Systems already run top to bottom and their boundaries left to right.
        // Sorting tilted measure boxes by their top edge reverses an uphill row.
        return List.copyOf(result);
    }

    private static List<StaffRun> findStaffs(byte[] labels, byte[] gray, int width, int height) {
        float slope = estimateStaffSlope(labels, width, height);
        int[] rowStrength = new int[height];
        float centerX = width / 2f;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (labels[y * width + x] == STAFF) {
                    int deskewedY = Math.round(y - slope * (x - centerX));
                    if (deskewedY >= 0 && deskewedY < height) rowStrength[deskewedY]++;
                }
        int minimumStrength = Math.max(10, width / 80);
        byte[] semanticGray = gray;
        if (gray != null && slope != 0f) {
            semanticGray = new byte[gray.length];
            Arrays.fill(semanticGray, (byte) 255);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) {
                    int originalY = y + Math.round(slope * (x - width * .5f));
                    if (originalY >= 0 && originalY < height)
                        semanticGray[y * width + x] = gray[originalY * width + x];
                }
        }
        List<RawStaffLineDetector.StaffLines> semanticStaffs =
                slope == 0f
                        ? RawStaffLineDetector.detectFromStrength(
                                rowStrength, minimumStrength, height, gray, width)
                        : RawStaffLineDetector.detectValidatedFromStrength(
                                rowStrength, minimumStrength, height, semanticGray, width);

        List<StaffRun> result = new ArrayList<>();
        List<StaffRun> shortCandidates = new ArrayList<>();
        for (RawStaffLineDetector.StaffLines semantic : semanticStaffs) {
            int[] rows = semantic.rows();
            float gap = semantic.gap();
            StaffPitchTrack track =
                    StaffPitchTrack.detectForMeasures(gray, width, height, rows[0], rows[4], gap);
            int top = Math.max(0, Math.round(rows[0] - gap * 0.65f));
            int bottom = Math.min(height - 1, Math.round(rows[4] + gap * 0.65f));
            int[] columns = new int[width];
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    if (labels[y * width + x] == STAFF) {
                        int deskewedY = Math.round(y - slope * (x - centerX));
                        if (deskewedY >= top && deskewedY <= bottom) columns[x]++;
                    }
            int total = Arrays.stream(columns).sum();
            int left = percentileColumn(columns, total, 0.012f);
            int right = percentileColumn(columns, total, 0.988f);
            if (gray != null) {
                int[] printed = rawStaffColumns(gray, width, height, rows, gap, slope);
                int printedTotal = Arrays.stream(printed).sum();
                int printedLeft = percentileColumn(printed, printedTotal, .012f);
                int printedRight = percentileColumn(printed, printedTotal, .988f);
                if (printedLeft < left && continuousStaffExtension(printed, printedLeft, left))
                    left = printedLeft;
                if (printedRight > right && continuousStaffExtension(printed, right, printedRight))
                    right = printedRight;
                int continuedRight = continuousPrintedRight(printed, right, gap);
                if (clippedClosingHead(
                        labels, width, height, right, continuedRight, rows, gap, slope))
                    right = continuedRight;
                // Very faded horizontal rules may disappear while the closing
                // bar remains clear. A verified full-height bar can preserve
                // those final notes without guessing a regular measure width.
                List<Integer> outer =
                        findBoundaries(
                                labels, gray, width, height, rows, gap, left, width - 1, slope,
                                track);
                if (outer.size() > 2) {
                    int closing = outer.get(outer.size() - 2);
                    if (closing > right && closing - right < width * .25f) right = closing;
                }
                int curvedRight =
                        CurvedStaffTail.closingBar(gray, width, height, right, rows[4], gap, slope);
                if (clippedClosingHead(labels, width, height, right, curvedRight, rows, gap, slope))
                    right = curvedRight;
                if (track != null) {
                    int[] trackedColumns =
                            rawStaffColumns(gray, width, height, rows, gap, slope, track);
                    int trackedRight = continuousPrintedRight(trackedColumns, right, gap);
                    if (clippedClosingHead(
                            labels, width, height, right, trackedRight, rows, gap, slope))
                        right = trackedRight;
                }
            }
            if (right - left >= Math.max(width / 4, Math.round(gap * 18f))
                    || ShortFinalStaff.proved(gray, width, height, rows, gap, left, right, slope)) {
                List<Integer> boundaries =
                        findBoundaries(
                                labels, gray, width, height, rows, gap, left, right, slope, track);
                if (boundaries.size() >= 2)
                    result.add(
                            new StaffRun(
                                    rows[0], rows[4], gap, left, right, boundaries, slope, track));
            } else if (slope == 0f && right - left >= gap * 9) {
                List<Integer> boundaries =
                        findBoundaries(
                                labels, gray, width, height, rows, gap, left, right, slope, track);
                if (boundaries.size() >= 2)
                    shortCandidates.add(
                            new StaffRun(
                                    rows[0], rows[4], gap, left, right, boundaries, slope, track));
            }
        }
        recoverRawStaffs(labels, gray, width, height, result, 0f);
        // A tilted system may lose every staff label while retaining clear printed rules.
        if (gray != null && Math.abs(slope) > .001f)
            recoverRawStaffs(labels, gray, width, height, result, slope);
        for (StaffRun cue : shortCandidates) {
            boolean partner = false;
            for (StaffRun full : result)
                if (insetCueStaff(gray, width, height, cue, full)) partner = true;
            if (partner
                    && countLabel(
                                    labels,
                                    width,
                                    height,
                                    NOTEHEAD,
                                    cue.left,
                                    cue.right,
                                    Math.round(cue.top - cue.gap * 2),
                                    Math.round(cue.bottom + cue.gap * 2))
                            >= cue.gap * cue.gap * .4f) result.add(cue);
        }
        List<StaffRun> snapshot = List.copyOf(result);
        for (StaffRun proved : snapshot) {
            if (proved.track == null) continue;
            float minimumTop = Float.MAX_VALUE, maximumBottom = -Float.MAX_VALUE;
            for (float fraction :
                    new float[] {
                        proved.left / (float) width,
                        .15f,
                        .35f,
                        .5f,
                        .65f,
                        .85f,
                        proved.right / (float) width
                    }) {
                float[] at = proved.track.at(width * fraction);
                minimumTop = Math.min(minimumTop, at[0] - 4 * at[1]);
                maximumBottom = Math.max(maximumBottom, at[0]);
            }
            float topLimit = minimumTop - proved.gap * .7f,
                    bottomLimit = maximumBottom + proved.gap * .7f;
            for (StaffRun other : snapshot) {
                if (other == proved
                        || other.track != null
                        || other.top < topLimit
                        || other.bottom > bottomLimit) continue;
                // A cue can occupy the vertical envelope without sharing the curved printed rules.
                // Duplicate projections must cover most of the same horizontal staff span.
                int overlap =
                        Math.min(other.right, proved.right) - Math.max(other.left, proved.left);
                if (overlap < (proved.right - proved.left) * .75f) continue;
                if (other.gap < proved.gap * .68f
                        || Math.abs(other.gap - proved.gap) < proved.gap * .18f)
                    result.remove(other);
            }
        }
        result.sort(Comparator.comparingInt(StaffRun::top));
        return result;
    }

    /** A short cue above one full-size bar can omit a bracket and clef. */
    private static boolean insetCueStaff(
            byte[] gray, int width, int height, StaffRun cue, StaffRun full) {
        if (gray == null
                || cue.slope != 0f
                || full.slope != 0f
                || cue.gap < full.gap * .6f
                || cue.gap > full.gap * .9f
                || full.top <= cue.bottom
                || full.top - cue.bottom > full.gap * 9
                || full.right - full.left < width * .4f
                || cue.right - cue.left > (full.right - full.left) * .85f
                || cue.right - cue.left < cue.gap * 9
                || cue.left - full.left < full.gap * 8
                || Math.abs(cue.right - full.right) > full.gap
                || cue.boundaries.size() < 2) return false;
        for (int boundary : cue.boundaries) {
            boolean shared = false;
            // The opening can inset one space; projection trimming adds a small fringe.
            float tolerance = boundary == cue.boundaries.get(0) ? 1.5f : .65f;
            for (int other : full.boundaries)
                if (Math.abs(boundary - other) <= full.gap * tolerance) shared = true;
            if (!shared) return false;
        }
        for (int boundary : full.boundaries) {
            if (boundary <= cue.left + full.gap || boundary >= cue.right - full.gap) continue;
            boolean shared = false;
            for (int other : cue.boundaries)
                if (Math.abs(boundary - other) <= full.gap * .65f) shared = true;
            if (!shared) return false;
        }
        int start = cue.left + Math.round(cue.gap), end = cue.right - Math.round(cue.gap * .5f);
        int radius = Math.max(1, Math.round(cue.gap * .18f));
        for (int line = 0; line < 5; line++) {
            int y = Math.round(cue.top + line * cue.gap), hits = 0;
            for (int x = start; x <= end; x++)
                if (thinHorizontalInk(gray, width, height, x, y, radius, cue.gap)) hits++;
            if (hits < (end - start + 1) * .8f) return false;
        }
        return true;
    }

    /**
     * A photographed or scanned page can keep perfectly readable staff lines in the source while
     * the model labels only the flatter systems. Recover only strong page-spanning five-line groups,
     * leaving the semantic result in charge wherever it already found the system.
     */
    private static void recoverRawStaffs(
            byte[] labels, byte[] gray, int width, int height, List<StaffRun> result, float slope) {
        List<Float> semanticCenters = new ArrayList<>();
        for (StaffRun staff : result) semanticCenters.add((staff.top + staff.bottom) * .5f);
        semanticCenters.sort(Float::compare);
        float semanticSystemStep = typicalSystemStep(semanticCenters);
        byte[] detectionGray = gray;
        // Deskew detection only; boundaries and note coordinates stay on the original page.
        if (slope != 0f) {
            detectionGray = new byte[gray.length];
            Arrays.fill(detectionGray, (byte) 255);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) {
                    int originalY = y + Math.round(slope * (x - width * .5f));
                    if (originalY >= 0 && originalY < height)
                        detectionGray[y * width + x] = gray[originalY * width + x];
                }
        }
        List<RawStaffLineDetector.StaffLines> rawStaffs =
                RawStaffLineDetector.detect(detectionGray, width, height);
        for (RawStaffLineDetector.StaffLines raw : rawStaffs) {
            // A curved physical row can make two page-wide projection peaks.
            // The established five-rule track owns every overlapping phase.
            boolean duplicateCurve = false;
            for (int index = 0; index < result.size(); index++) {
                StaffRun existing = result.get(index);
                if (Math.abs(existing.gap - raw.gap()) >= existing.gap * .18f
                        || raw.top() >= existing.bottom
                        || raw.bottom() <= existing.top
                        || Math.abs(raw.center() - (existing.top + existing.bottom) * .5f)
                                <= Math.max(raw.gap() * 2.2f, height * .008f)) continue;
                StaffPitchTrack track =
                        existing.track == null
                                ? StaffPitchTrack.detectForMeasures(
                                        gray,
                                        width,
                                        height,
                                        existing.top,
                                        existing.bottom,
                                        existing.gap)
                                : existing.track;
                if (track == null) continue;
                for (float fraction : new float[] {.15f, .35f, .65f, .85f}) {
                    float[] physical = track.at(width * fraction);
                    if (Math.abs(physical[0] - raw.bottom()) < existing.gap * .6f)
                        duplicateCurve = true;
                }
                if (duplicateCurve) {
                    int[] rows = new int[5];
                    for (int j = 0; j < 5; j++)
                        rows[j] = Math.round(existing.bottom - (4 - j) * existing.gap);
                    List<Integer> boundaries =
                            findBoundaries(
                                    labels,
                                    gray,
                                    width,
                                    height,
                                    rows,
                                    existing.gap,
                                    existing.left,
                                    existing.right,
                                    existing.slope,
                                    track);
                    result.set(
                            index,
                            new StaffRun(
                                    existing.top,
                                    existing.bottom,
                                    existing.gap,
                                    existing.left,
                                    existing.right,
                                    boundaries,
                                    existing.slope,
                                    track));
                    break;
                }
            }
            if (duplicateCurve) continue;
            int representedIndex = -1;
            for (int index = 0; index < result.size(); index++) {
                StaffRun existing = result.get(index);
                float center = (existing.top + existing.bottom) * .5f;
                if (Math.abs(center - raw.center()) <= Math.max(raw.gap() * 2.2f, height * .008f)) {
                    representedIndex = index;
                    break;
                }
            }

            // The second pass recovers omitted systems without replacing accepted geometry.
            if (slope != 0f && representedIndex >= 0) continue;
            int[] columns = rawStaffColumns(gray, width, height, raw.rows(), raw.gap(), slope);
            int total = Arrays.stream(columns).sum();
            int left = percentileColumn(columns, total, .012f);
            int right = percentileColumn(columns, total, .988f);
            int continuedRight = continuousPrintedRight(columns, right, raw.gap());
            if (clippedClosingHead(
                    labels, width, height, right, continuedRight, raw.rows(), raw.gap(), slope))
                right = continuedRight;
            if (right - left < Math.max(width / 4, Math.round(raw.gap() * 18f))) continue;
            StaffPitchTrack rawTrack =
                    StaffPitchTrack.detectForMeasures(
                            gray, width, height, raw.top(), raw.bottom(), raw.gap());
            List<Integer> boundaries =
                    findBoundaries(
                            labels,
                            gray,
                            width,
                            height,
                            raw.rows(),
                            raw.gap(),
                            left,
                            right,
                            slope,
                            rawTrack);
            if (representedIndex >= 0) {
                StaffRun existing = result.get(representedIndex);
                if (existing.gap < raw.gap() * .68f && rawTrack != null && boundaries.size() >= 2) {
                    result.set(
                            representedIndex,
                            new StaffRun(
                                    raw.top(),
                                    raw.bottom(),
                                    raw.gap(),
                                    left,
                                    right,
                                    boundaries,
                                    slope,
                                    rawTrack));
                    continue;
                }
                // A nearby complete raw staff can independently recover a bar
                // rejected by the shifted semantic frame. Both raw and tracked
                // five-rule frames must prove the same new bar; retain every
                // original boundary and all original vertical/stem guards.
                if (existing.boundaries.size() > 2
                        && existing.track == null
                        && Math.abs(raw.top() - existing.top) > existing.gap * .5f
                        && Math.abs(raw.top() - existing.top) <= existing.gap * 1.5f
                        && Math.abs(raw.gap() - existing.gap) <= existing.gap * .18f) {
                    StaffPitchTrack local =
                            StaffPitchTrack.detectForMeasures(
                                    gray,
                                    width,
                                    height,
                                    existing.top,
                                    existing.bottom,
                                    existing.gap);
                    if (local != null) {
                        int[] originalRows = new int[5];
                        for (int j = 0; j < 5; j++)
                            originalRows[j] = Math.round(existing.bottom - (4 - j) * existing.gap);
                        List<Integer> tracked =
                                findBoundaries(
                                        labels,
                                        gray,
                                        width,
                                        height,
                                        originalRows,
                                        existing.gap,
                                        existing.left,
                                        existing.right,
                                        existing.slope,
                                        local);
                        List<Integer> merged =
                                corroboratedInnerBars(
                                        existing.boundaries, boundaries, tracked, existing.gap);
                        if (merged.size() > existing.boundaries.size()) {
                            result.set(
                                    representedIndex,
                                    new StaffRun(
                                            existing.top,
                                            existing.bottom,
                                            existing.gap,
                                            existing.left,
                                            existing.right,
                                            merged,
                                            existing.slope,
                                            existing.track));
                            continue;
                        }
                    }
                }
                // A global semantic deskew can find the staff but still miss its raw vertical
                // bars. Replace only a completely unsplit semantic row with a conservative raw
                // result; busier semantic layouts keep their existing evidence. The printed-number
                // reconciler can reduce false raw boundaries while retaining real unequal bar
                // positions, so final systems need this evidence too.
                if (existing.boundaries.size() == 2
                        && boundaries.size() > 2
                        && boundaries.size() <= 9)
                    result.set(
                            representedIndex,
                            new StaffRun(
                                    existing.top,
                                    existing.bottom,
                                    existing.gap,
                                    left,
                                    right,
                                    boundaries,
                                    existing.slope,
                                    existing.track));
                else if (Math.abs(raw.top() - existing.top) <= existing.gap * .5f
                        && Math.abs(raw.gap() - existing.gap) <= existing.gap * .18f) {
                    // The mask may fade before the printed staff ends. Preserve
                    // inner barlines, but let matching raw five-line geometry
                    // retain the header and final notes at the outer edges.
                    int expandedLeft = existing.left;
                    int expandedRight = existing.right;
                    if (left < expandedLeft && expandedLeft - left <= existing.gap * 4f)
                        expandedLeft = left;
                    if (right > expandedRight
                            && (right - expandedRight <= existing.gap * 4f
                                    || continuousStaffExtension(columns, expandedRight, right)))
                        expandedRight = right;
                    if (expandedLeft != existing.left || expandedRight != existing.right) {
                        List<Integer> extended = new ArrayList<>(existing.boundaries);
                        extended.set(0, expandedLeft);
                        extended.set(extended.size() - 1, expandedRight);
                        result.set(
                                representedIndex,
                                new StaffRun(
                                        existing.top,
                                        existing.bottom,
                                        existing.gap,
                                        expandedLeft,
                                        expandedRight,
                                        List.copyOf(extended),
                                        existing.slope,
                                        existing.track));
                    }
                }
            } else if (boundaries.size() >= 2
                    && (!betweenAdjacentSemanticSystems(
                                    raw.center(), semanticCenters, semanticSystemStep)
                            || RawStaffLineDetector.connectedToStaff(
                                    raw, rawStaffs, detectionGray, width, height)))
                result.add(
                        new StaffRun(
                                raw.top(),
                                raw.bottom(),
                                raw.gap(),
                                left,
                                right,
                                boundaries,
                                slope,
                                rawTrack));
        }
    }

    /**
     * Dense beams can form five page-wide horizontal peaks at the same scale as the staff. They
     * usually sit between two already recognized consecutive systems. A genuinely omitted staff
     * instead creates a roughly double-sized hole in the semantic sequence. Reject only the
     * former so raw recovery remains available for missing rows.
     */
    private static boolean betweenAdjacentSemanticSystems(
            float center, List<Float> centers, float typicalStep) {
        if (centers == null
                || centers.size() < 3
                || !Float.isFinite(typicalStep)
                || typicalStep <= 0) return false;
        for (int index = 0; index + 1 < centers.size(); index++) {
            float before = centers.get(index), after = centers.get(index + 1);
            if (center <= before || center >= after) continue;
            float span = after - before;
            return span <= typicalStep * 1.45f
                    && center - before >= typicalStep * .20f
                    && after - center >= typicalStep * .20f;
        }
        return false;
    }

    private static float typicalSystemStep(List<Float> centers) {
        if (centers == null || centers.size() < 3) return Float.NaN;
        List<Float> gaps = new ArrayList<>();
        for (int index = 0; index + 1 < centers.size(); index++) {
            float gap = centers.get(index + 1) - centers.get(index);
            if (gap > 0) gaps.add(gap);
        }
        if (gaps.size() < 2) return Float.NaN;
        gaps.sort(Float::compare);
        // The upper-middle gap ignores the smaller separation between staves in a grand staff.
        return gaps.get((gaps.size() * 3) / 4);
    }

    private static int[] rawStaffColumns(
            byte[] gray, int width, int height, int[] rows, float gap) {
        return rawStaffColumns(gray, width, height, rows, gap, 0f);
    }

    private static int[] rawStaffColumns(
            byte[] gray, int width, int height, int[] rows, float gap, float slope) {
        return rawStaffColumns(gray, width, height, rows, gap, slope, null);
    }

    private static int[] rawStaffColumns(
            byte[] gray,
            int width,
            int height,
            int[] rows,
            float gap,
            float slope,
            StaffPitchTrack track) {
        int[] columns = new int[width];
        int radius = Math.max(2, Math.round(gap * .30f));
        for (int x = 0; x < width; x++) {
            float[] physical = track == null ? null : track.at(x);
            for (int line = 0; line < 5; line++) {
                int printedRow =
                        physical == null
                                ? Math.round(rows[line] + slope * (x - width * .5f))
                                : Math.round(physical[0] - (4 - line) * physical[1]);
                if (thinHorizontalInk(gray, width, height, x, printedRow, radius, gap))
                    columns[x]++;
            }
        }
        return columns;
    }

    static float estimateStaffSlope(byte[] labels, int width, int height) {
        // Sampling alternate rows biases thin rules toward horizontal. Keep every
        // row, and reuse only staff pixels while testing candidate angles.
        int count = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x += 3) if (labels[y * width + x] == STAFF) count++;
        if (count == 0) return 0f;
        int[] xs = new int[count], ys = new int[count];
        int index = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x += 3)
                if (labels[y * width + x] == STAFF) {
                    xs[index] = x;
                    ys[index++] = y;
                }
        float bestSlope = 0f;
        float centerX = width / 2f;
        int[] projection = new int[height];
        long horizontalScore = staffProjectionScore(xs, ys, height, centerX, 0f, projection);
        long bestScore = horizontalScore;
        // Camera angles and book gutters can exceed the old roughly three-degree range.
        for (int step = -24; step <= 24; step++) {
            if (step == 0) continue; // Horizontal score was computed above.
            float slope = step * 0.006f;
            long score = staffProjectionScore(xs, ys, height, centerX, slope, projection);
            if (score > bestScore) {
                bestScore = score;
                bestSlope = slope;
            }
        }
        // A slight photographic tilt can lie halfway between the coarse angles.
        // At page width it still moves a rule by several pixels and can hide a row.
        float coarseSlope = bestSlope;
        for (int step = -5; step <= 5; step++) {
            if (step == 0) continue; // The coarse winner was already scored.
            float slope = coarseSlope + step * .0006f;
            long score = staffProjectionScore(xs, ys, height, centerX, slope, projection);
            if (score > bestScore) {
                bestScore = score;
                bestSlope = slope;
            }
        }
        return bestScore > horizontalScore * 1.05 ? bestSlope : 0f;
    }

    private static long staffProjectionScore(
            int[] xs, int[] ys, int height, float centerX, float slope) {
        int[] projection = new int[height];
        for (int i = 0; i < xs.length; i++) {
            int row = Math.round(ys[i] - slope * (xs[i] - centerX));
            if (row >= 0 && row < height) projection[row]++;
        }
        long score = 0;
        for (int value : projection) score += (long) value * value;
        return score;
    }

    private static long staffProjectionScore(
            int[] xs, int[] ys, int height, float centerX, float slope, int[] projection) {
        Arrays.fill(projection, 0);
        for (int i = 0; i < xs.length; i++) {
            int row = Math.round(ys[i] - slope * (xs[i] - centerX));
            if (row >= 0 && row < height) projection[row]++;
        }
        long score = 0;
        for (int value : projection) score += (long) value * value;
        return score;
    }

    /** Recover a trimmed ending only when it excludes an actual detected head.
     * A decorative or courtesy-only tail must not create another timed measure. */
    private static boolean clippedClosingHead(
            byte[] labels,
            int width,
            int height,
            int right,
            int end,
            int[] rows,
            float gap,
            float slope) {
        if (end <= right) return false;
        int left = Math.max(0, Math.round(right - gap * 2f));
        int shift = Math.round(slope * ((right + end) * .5f - width * .5f));
        int top = Math.max(0, Math.round(rows[0] + shift - gap * MAX_HEAD_LEDGER_GAPS));
        int bottom = Math.min(height - 1, Math.round(rows[4] + shift + gap * MAX_HEAD_LEDGER_GAPS));
        int w = end - left + 1, h = bottom - top + 1;
        if (w <= 0 || h <= 0) return false;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int i = 0; i < seen.length; i++) {
            if (seen[i] || labels[(top + i / w) * width + left + i % w] != NOTEHEAD) continue;
            int read = 0, size = 1, minX = w, maxX = 0, minY = h, maxY = 0;
            long sumX = 0;
            seen[i] = true;
            queue[0] = i;
            while (read < size) {
                int at = queue[read++], x = at % w, y = at / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                sumX += x;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && labels[(top + ny) * width + left + nx] == NOTEHEAD) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            float center = left + sumX / (float) size;
            int headWidth = maxX - minX + 1, headHeight = maxY - minY + 1;
            int inset = Math.max(1, Math.round(gap * .12f));
            if (size >= gap * gap * .14f
                    && headWidth >= gap * .35f
                    && headWidth <= gap * 2.5f
                    && headHeight >= gap * .25f
                    && headHeight <= gap * 1.6f
                    && left + maxX > right - inset
                    && center <= end - inset) return true;
        }
        return false;
    }

    /** Follow at least four printed rules, bridging short ink interruptions. */
    private static int continuousPrintedRight(int[] columns, int right, float gap) {
        int last = right, missing = 0;
        for (int x = right + 1; x < columns.length; x++) {
            if (columns[x] >= 4) {
                last = x;
                missing = 0;
            } else if (++missing > Math.max(2, Math.round(gap))) break;
        }
        return last;
    }

    private static int percentileColumn(int[] counts, int total, float percentile) {
        if (total <= 0) return percentile < 0.5f ? 0 : Math.max(0, counts.length - 1);
        int target = Math.max(1, Math.round(total * percentile)), accumulated = 0;
        for (int x = 0; x < counts.length; x++) {
            accumulated += counts[x];
            if (accumulated >= target) return x;
        }
        return counts.length - 1;
    }

    private static List<Integer> findBoundaries(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int[] rows,
            float gap,
            int left,
            int right,
            float slope) {
        return findBoundaries(labels, gray, width, height, rows, gap, left, right, slope, null);
    }

    private static List<Integer> findBoundaries(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int[] seedRows,
            float gap,
            int left,
            int right,
            float slope,
            StaffPitchTrack track) {
        float centerX = width / 2f;
        int span = Math.max(1, Math.round(seedRows[4] - seedRows[0] + gap * 0.5f));
        List<Integer> candidates = new ArrayList<>();
        boolean inRun = false;
        int runStart = 0;
        for (int x = left + 1; x <= right; x++) {
            float shift = slope * (x - centerX);
            int[] rows = seedRows;
            if (track != null) {
                float[] physical = track.at(x);
                rows = new int[5];
                for (int line = 0; line < 5; line++)
                    rows[line] = Math.round(physical[0] - (4 - line) * physical[1] - shift);
            }
            int top = Math.max(0, Math.round(rows[0] + shift - gap * 0.25f));
            int bottom = Math.min(height - 1, Math.round(rows[4] + shift + gap * 0.25f));
            int covered = 0;
            for (int y = top; y <= bottom; y++) {
                boolean ink = false;
                for (int dx = -1; dx <= 1 && !ink; dx++) {
                    int checkX = x + dx;
                    ink =
                            checkX >= 0
                                    && checkX < width
                                    && isVerticalInk(labels[y * width + checkX]);
                }
                if (ink) covered++;
            }
            boolean touchesTop =
                    hasVerticalInk(
                            labels,
                            width,
                            height,
                            x,
                            2,
                            top,
                            Math.min(bottom, Math.round(top + gap)));
            boolean touchesBottom =
                    hasVerticalInk(
                            labels,
                            width,
                            height,
                            x,
                            2,
                            Math.max(top, Math.round(bottom - gap)),
                            bottom);
            // the segmentation model's stem/rest mask often shortens true barlines to stem height.
            // Its generic
            // symbol mask retains more of the original line, so combine both semantic outputs
            // and rely on the absence of an attached notehead to reject ordinary note stems.
            boolean semanticCandidate =
                    covered >= Math.max(gap * 1.65f, span * 0.36f) && (touchesTop || touchesBottom);
            int rawColumn =
                    gray != null && semanticCandidate
                            ? rawBarlineColumn(
                                    gray,
                                    width,
                                    height,
                                    x,
                                    rows,
                                    gap,
                                    shift,
                                    slope,
                                    RAW_BARLINE_DARK,
                                    track == null ? 12 : 32)
                            : Integer.MIN_VALUE;
            // A one-pixel staff-row bias can exclude the lower edge of a short
            // printed rule. Retry only when the original raw page still shows
            // an isolated full-height line at this semantic candidate.
            if (gray != null
                    && semanticCandidate
                    && rawColumn == Integer.MIN_VALUE
                    && isolatedFullHeightRule(gray, width, height, x, rows, gap, shift)) {
                int[] adjustedRows = rows.clone();
                for (int index = 0; index < adjustedRows.length; index++) adjustedRows[index]--;
                rawColumn =
                        rawBarlineColumn(
                                gray,
                                width,
                                height,
                                x,
                                adjustedRows,
                                gap,
                                shift,
                                slope,
                                RAW_BARLINE_DARK,
                                track == null ? 12 : 32);
            }
            // A nearly complete semantic rule can survive a scan whose raw core
            // is slightly paler. Keep all raw continuity, space and branch gates,
            // plus note ownership, instead of accepting the semantic trace alone.
            if (gray != null
                    && rawColumn == Integer.MIN_VALUE
                    && touchesTop
                    && touchesBottom
                    && covered >= span * .85f)
                rawColumn =
                        rawBarlineColumn(
                                gray,
                                width,
                                height,
                                x,
                                rows,
                                gap,
                                shift,
                                slope,
                                220,
                                track == null ? 12 : 32);
            boolean rawSpansStaff = rawColumn != Integer.MIN_VALUE;
            boolean semanticBar = semanticCandidate && (gray == null || rawSpansStaff);
            // Note ownership can only veto a proven bar; it cannot create one. Avoid scanning
            // a large head neighborhood and connected stems at every non-bar column.
            boolean attachedHead = false;
            if (semanticBar) {
                attachedHead =
                        countLabel(
                                        labels,
                                        width,
                                        height,
                                        NOTEHEAD,
                                        Math.round(x - gap * .88f),
                                        Math.round(x + gap * .88f),
                                        Math.round(top - gap * 1.5f),
                                        Math.round(bottom + gap * 1.5f))
                                >= Math.max(3, Math.round(gap * .65f));
                if (attachedHead
                        && gray != null
                        && isolatedFullHeightRule(gray, width, height, x, rows, gap, shift))
                    attachedHead =
                            headTouchesColumn(
                                    labels,
                                    gray,
                                    width,
                                    height,
                                    x,
                                    Math.round(top - gap * 1.5f),
                                    Math.round(bottom + gap * 1.5f),
                                    rows,
                                    gap,
                                    shift);
                if (!attachedHead)
                    attachedHead =
                            distantHeadOnSameStem(labels, gray, width, height, x, top, bottom, gap);
                if (!attachedHead
                        && gray != null
                        && countLabel(
                                        labels,
                                        width,
                                        height,
                                        STEM_OR_REST,
                                        Math.round(x - gap * 1.2f),
                                        x + 2,
                                        top,
                                        bottom)
                                >= gap * .6f
                        && !isolatedFullHeightRule(gray, width, height, x, rows, gap, shift))
                    attachedHead =
                            symbolHeadTouchesColumn(labels, width, height, x, top, bottom, gap);
            }
            // Validate stem ownership at the same printed column that proved the rule.
            // A semantic halo can lie a pixel beyond the long stem's labelled edge.
            if (semanticBar && !attachedHead && rawSpansStaff && rawColumn != x)
                attachedHead =
                        distantHeadOnSameStem(
                                labels, gray, width, height, rawColumn, top, bottom, gap);
            if (semanticBar
                    && !attachedHead
                    && rawSpansStaff
                    && rawColumn != x
                    && countLabel(
                                    labels,
                                    width,
                                    height,
                                    STEM_OR_REST,
                                    Math.round(rawColumn - gap * 1.2f),
                                    rawColumn + 2,
                                    top,
                                    bottom)
                            >= gap * .6f
                    && !isolatedFullHeightRule(gray, width, height, rawColumn, rows, gap, shift))
                attachedHead =
                        symbolHeadTouchesColumn(labels, width, height, rawColumn, top, bottom, gap);
            // Raw pixels validate a semantic candidate, but never create one by themselves:
            // aligned note stems can span all five lines on dense music such as Humoresque.
            if (semanticBar && !attachedHead && gray != null)
                attachedHead =
                        ClosedHeadBarlineGuard.attached(
                                gray,
                                width,
                                height,
                                rawColumn,
                                Math.round(rows[0] + shift),
                                Math.round(rows[4] + shift),
                                gap);
            boolean bar = semanticBar && !attachedHead;
            if (bar && !inRun) {
                inRun = true;
                runStart = x;
            }
            if ((!bar || x == right) && inRun) {
                int runEnd = bar && x == right ? x : x - 1;
                candidates.add((runStart + runEnd) / 2);
                inRun = false;
            }
        }

        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(left);
        int minimumGap = Math.max(Math.round(gap * 2.5f), width / 55);
        for (int candidate : candidates) {
            if (candidate - boundaries.get(boundaries.size() - 1) < minimumGap) continue;
            if (right - candidate < minimumGap) continue;
            boundaries.add(candidate);
        }
        boundaries.add(right);
        // With no internal barline, the semantic staff extent is still one trustworthy measure;
        // unlike the retired detector, this never invents evenly spaced subdivisions.
        return boundaries.size() > 32 ? List.of() : List.copyOf(boundaries);
    }

    /** Merge only inner bars independently agreed by two printed five-rule frames. */
    private static List<Integer> corroboratedInnerBars(
            List<Integer> original, List<Integer> raw, List<Integer> tracked, float gap) {
        if (original.size() < 3
                || raw.size() < 3
                || tracked.size() < 3
                || gap < 3
                || !Float.isFinite(gap)) return original;
        List<Integer> merged = new ArrayList<>(original);
        int left = original.get(0), right = original.get(original.size() - 1);
        for (int i = 1; i < tracked.size() - 1; i++) {
            int x = tracked.get(i);
            if (x - left < gap * 2.5f || right - x < gap * 2.5f) continue;
            boolean second = false, near = false;
            for (int j = 1; j < raw.size() - 1; j++)
                if (Math.abs(raw.get(j) - x) < gap * .5f) second = true;
            for (int old : merged) if (Math.abs(old - x) < gap * 2.5f) near = true;
            if (second && !near) merged.add(x);
        }
        merged.sort(Integer::compareTo);
        return merged.size() <= 32 ? List.copyOf(merged) : original;
    }

    private static boolean isVerticalInk(byte label) {
        return label == STEM_OR_REST || label == SYMBOL;
    }

    private static boolean isolatedFullHeightRule(
            byte[] gray, int w, int h, int x, int[] rows, float gap, float shift) {
        int top = Math.round(rows[0] + shift),
                bottom = Math.round(rows[4] + shift),
                hit = 0,
                outside = 0,
                samples = 0;
        for (int y = Math.max(0, top); y <= Math.min(h - 1, bottom); y++) {
            boolean dark = false;
            for (int dx = -1; dx <= 1; dx++)
                if (x + dx >= 0 && x + dx < w && (gray[y * w + x + dx] & 255) < 150) dark = true;
            if (dark) hit++;
        }
        for (int d = Math.max(3, Math.round(gap * .3f)); d <= gap; d++)
            for (int y : new int[] {top - d, bottom + d}) {
                if (y < 0 || y >= h) continue;
                samples++;
                boolean dark = false;
                for (int dx = -1; dx <= 1; dx++)
                    if (x + dx >= 0 && x + dx < w && (gray[y * w + x + dx] & 255) < 150)
                        dark = true;
                if (dark) outside++;
            }
        return hit >= (bottom - top + 1) * .95f && outside <= samples * .15f;
    }

    /** Dense engraving can put an unrelated head inside the old broad stem-veto box.
     * Require an actual ink connection, ignoring the staff lines that join everything. */
    private static boolean headTouchesColumn(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int x,
            int top,
            int bottom,
            int[] rows,
            float gap,
            float shift) {
        float[] printedRows = connectionStaffRows(gray, width, height, x, rows, gap, shift);
        boolean verifiedPrintedRows = printedRows != null;
        if (printedRows == null) {
            printedRows = new float[5];
            for (int line = 0; line < 5; line++) printedRows[line] = rows[line] + shift;
        }
        for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++) {
            float nearestRule = Float.POSITIVE_INFINITY;
            for (float row : printedRows) nearestRule = Math.min(nearestRule, Math.abs(y - row));
            if (nearestRule <= Math.max(1, gap * .14f)) continue;
            // A verified printed rule has an antialiased fringe. It can join a
            // nearby head to a real bar, but a head on the bar itself still owns
            // its stem even within that fringe.
            boolean fringe = verifiedPrintedRows && nearestRule <= Math.max(1, gap * .25f);
            // Semantic candidates include the two-pixel halo around a thin column.
            // Start from each real column, or its white halo falsely looks disconnected.
            for (int origin = x - 2; origin <= x + 2; origin++)
                for (int direction : new int[] {-1, 1})
                    for (int distance = 0; distance <= gap * .88f; distance++) {
                        int xx = origin + direction * distance;
                        if (xx < 0
                                || xx >= width
                                || (gray[y * width + xx] & 255) > RAW_BARLINE_DARK) break;
                        if (labels[y * width + xx] == NOTEHEAD
                                && (!fringe || Math.abs(xx - x) <= Math.max(2, gap * .2f)))
                            return true;
                    }
        }
        return false;
    }

    /** Use thin bilateral raw rules when semantic centers drift into the spaces.
     * Require all five lines; short ledger rules and nearby beams cannot relocate a staff. */
    private static float[] connectionStaffRows(
            byte[] gray, int width, int height, int x, int[] rows, float gap, float shift) {
        float[] original = new float[5], refined = new float[5];
        for (int line = 0; line < 5; line++) original[line] = rows[line] + shift;
        int inner = Math.max(3, Math.round(gap * .45f));
        int outer = Math.max(inner + 3, Math.round(gap * 2f));
        if (x - outer < 0 || x + outer >= width) return null;
        for (int line = 0; line < 5; line++) {
            int top = Math.max(0, (int) Math.floor(original[line] - gap * .32f));
            int bottom = Math.min(height - 1, (int) Math.ceil(original[line] + gap * .32f));
            int first = -1, last = -1;
            for (int y = top; y <= bottom; y++) {
                int left = 0, right = 0;
                for (int d = inner; d <= outer; d++) {
                    if ((gray[y * width + x - d] & 255) < 150) left++;
                    if ((gray[y * width + x + d] & 255) < 150) right++;
                }
                if (Math.min(left, right) < (outer - inner + 1) * .8f) continue;
                if (last >= 0 && y != last + 1) return null;
                if (first < 0) first = y;
                last = y;
            }
            if (first < 0 || last - first + 1 > Math.max(2, gap * .3f)) return null;
            refined[line] = (first + last) / 2f;
            if (line > 0 && Math.abs(refined[line] - refined[line - 1] - gap) > gap * .2f)
                return null;
        }
        return refined;
    }

    /** High ledger heads still veto their own long stem, but not an unrelated bar on the next row. */
    private static boolean distantHeadOnSameStem(
            byte[] labels, int width, int height, int x, int top, int bottom, float gap) {
        return distantHeadOnSameStem(labels, null, width, height, x, top, bottom, gap);
    }

    private static boolean distantHeadOnSameStem(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int x,
            int top,
            int bottom,
            float gap) {
        for (int direction : new int[] {-1, 1}) {
            int start = direction < 0 ? top : bottom;
            int misses = 0;
            for (int distance = 1;
                    distance <= gap * MAX_CONNECTED_STAFF_SEPARATION_GAPS * 2;
                    distance++) {
                int y = start + direction * distance;
                if (y < 0 || y >= height) break;
                boolean stem = false, headTouchesStem = false;
                for (int dx = -2; dx <= 2; dx++) {
                    int xx = x + dx;
                    if (xx < 0 || xx >= width) continue;
                    byte label = labels[y * width + xx];
                    if (isVerticalInk(label) || label == NOTEHEAD || label == STAFF) stem = true;
                    headTouchesStem |= label == NOTEHEAD;
                }
                if (stem) misses = 0;
                else if (++misses > Math.max(2, gap * .28f)) break;
                if (distance < gap * 1.4f || !headTouchesStem) continue;
                if (countLabel(
                                        labels,
                                        width,
                                        height,
                                        NOTEHEAD,
                                        Math.round(x - gap * .88f),
                                        Math.round(x + gap * .88f),
                                        y - 2,
                                        y + 2)
                                >= Math.max(3, Math.round(gap * .65f))
                        && (gray == null || rawDistantStem(gray, width, height, x, start, y, gap)))
                    return true;
            }
        }
        return false;
    }

    /** A semantic bridge through paper cannot claim ownership of a separately printed barline. */
    private static boolean rawDistantStem(
            byte[] gray, int width, int height, int x, int from, int to, float gap) {
        int first = Math.min(from, to),
                last = Math.max(from, to),
                surround = Math.max(4, Math.round(gap * 2));
        if (first < 0 || last >= height) return false;
        int[] tones = new int[256], limits = new int[last - first + 1];
        for (int y = first; y <= last; y++) {
            Arrays.fill(tones, 0);
            int n = 0;
            for (int xx = Math.max(0, x - surround);
                    xx <= Math.min(width - 1, x + surround);
                    xx++) {
                tones[gray[y * width + xx] & 255]++;
                n++;
            }
            int paper = 0, seen = tones[0], target = (n * 3 + 3) / 4;
            while (seen < target && paper < 255) seen += tones[++paper];
            limits[y - first] = Math.min(RAW_BARLINE_DARK, Math.max(0, paper - 12));
        }
        for (int origin = Math.max(0, x - 2); origin <= Math.min(width - 1, x + 2); origin++) {
            int hits = 0, longest = 0, run = 0;
            for (int y = first; y <= last; y++) {
                if ((gray[y * width + origin] & 255) <= limits[y - first]) {
                    hits++;
                    longest = Math.max(longest, ++run);
                } else run = 0;
            }
            int span = last - first + 1;
            if (hits >= span * .8f && longest >= span * .5f) return true;
        }
        return false;
    }

    /** A harmonic diamond may be classed as SYMBOL while its long stem looks like a barline. */
    private static boolean symbolHeadTouchesColumn(
            byte[] labels, int width, int height, int x, int top, int bottom, float gap) {
        int left = Math.max(0, x - Math.round(gap * 1.7f));
        int right = Math.min(width - 1, x + Math.round(gap * .5f));
        int first = Math.max(0, top - Math.round(gap * .6f));
        int last = Math.min(height - 1, bottom + Math.round(gap * 1.7f));
        int localWidth = right - left + 1, localHeight = last - first + 1;
        boolean[] seen = new boolean[localWidth * localHeight];
        int[] queue = new int[seen.length];
        for (int origin = 0; origin < seen.length; origin++) {
            if (seen[origin]
                    || labels[(first + origin / localWidth) * width + left + origin % localWidth]
                            != SYMBOL) continue;
            int read = 0, write = 0;
            queue[write++] = origin;
            seen[origin] = true;
            int minX = right, maxX = left, minY = last, maxY = first, area = 0;
            boolean clipped = false;
            while (read < write) {
                int at = queue[read++], xx = at % localWidth, yy = at / localWidth;
                int px = left + xx, py = first + yy;
                area++;
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
                minY = Math.min(minY, py);
                maxY = Math.max(maxY, py);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = xx + dx, ny = yy + dy;
                        if (nx < 0 || nx >= localWidth || ny < 0 || ny >= localHeight) {
                            int outsideX = left + nx, outsideY = first + ny;
                            if (outsideX >= 0
                                    && outsideX < width
                                    && outsideY >= 0
                                    && outsideY < height
                                    && labels[outsideY * width + outsideX] == SYMBOL)
                                clipped = true;
                            continue;
                        }
                        int next = ny * localWidth + nx;
                        if (!seen[next] && labels[(first + ny) * width + left + nx] == SYMBOL) {
                            seen[next] = true;
                            queue[write++] = next;
                        }
                    }
            }
            int w = maxX - minX + 1, h = maxY - minY + 1;
            // A cropped tie/beam fragment can have head-sized bounds. Only a complete
            // symbolic component can prove ownership of the nearby vertical stroke.
            if (!clipped
                    && minX <= x + 2
                    && maxX >= x - 2
                    && w >= gap * .65f
                    && w <= gap * 1.9f
                    && h >= gap * .55f
                    && h <= gap * 1.5f
                    && area >= gap * gap * .32f
                    && (minY + maxY) * .5f >= top - gap * .5f
                    && (minY + maxY) * .5f <= bottom + gap * 1.5f) return true;
        }
        return false;
    }

    /** Correct a local staff offset only when all five parallel printed rules support it. */
    private static int printedRuleOffset(
            byte[] gray, int width, int height, int centerX, int[] rows, float gap, float shift) {
        int reach = Math.max(8, Math.round(gap * 3)), skip = Math.max(2, Math.round(gap * .6f));
        int separation = Math.max(2, Math.round(gap * .35f)),
                radius = Math.max(1, Math.round(gap * .08f));
        int search = Math.max(1, Math.round(gap * 1.3f)), best = 0;
        double bestScore = -1;
        for (int delta = -search; delta <= search; delta++) {
            double minimum = 1, total = 0;
            for (int line = 0; line < 5; line++) {
                int y = Math.round(rows[line] + shift) + delta, ink = 0, samples = 0;
                if (y - separation - radius < 0 || y + separation + radius >= height) {
                    minimum = 0;
                    break;
                }
                for (int x = Math.max(0, centerX - reach);
                        x <= Math.min(width - 1, centerX + reach);
                        x++) {
                    if (Math.abs(x - centerX) < skip) continue;
                    samples++;
                    boolean found = false;
                    for (int dy = -radius; dy <= radius; dy++) {
                        int at = y + dy;
                        int paper =
                                ((gray[(at - separation) * width + x] & 255)
                                                + (gray[(at + separation) * width + x] & 255))
                                        / 2;
                        if (paper - (gray[at * width + x] & 255) >= 20) {
                            found = true;
                            break;
                        }
                    }
                    if (found) ink++;
                }
                double coverage = samples == 0 ? 0 : ink / (double) samples;
                minimum = Math.min(minimum, coverage);
                // A later line cannot raise the minimum coverage of this candidate.
                if (minimum < .55) break;
                total += coverage;
            }
            if (minimum < .55) continue;
            double score = minimum * 2 + total / 5 - Math.abs(delta) * .003;
            if (score > bestScore) {
                bestScore = score;
                best = delta;
            }
        }
        return best;
    }

    /** A note stem can be tall in the semantic mask, but unlike a barline it does not form a
     * nearly continuous raw-ink path through both outer staff lines. */
    private static boolean rawBarlineSpansStaff(
            byte[] gray,
            int width,
            int height,
            int centerX,
            int[] rows,
            float gap,
            float shift,
            float slope) {
        return rawBarlineColumn(gray, width, height, centerX, rows, gap, shift, slope)
                != Integer.MIN_VALUE;
    }

    private static int rawBarlineColumn(
            byte[] gray,
            int width,
            int height,
            int centerX,
            int[] rows,
            float gap,
            float shift,
            float slope) {
        return rawBarlineColumn(
                gray, width, height, centerX, rows, gap, shift, slope, RAW_BARLINE_DARK);
    }

    private static int rawBarlineColumn(
            byte[] gray,
            int width,
            int height,
            int centerX,
            int[] rows,
            float gap,
            float shift,
            float slope,
            int inkLimit) {
        return rawBarlineColumn(
                gray, width, height, centerX, rows, gap, shift, slope, inkLimit, 12);
    }

    private static int rawBarlineColumn(
            byte[] gray,
            int width,
            int height,
            int centerX,
            int[] rows,
            float gap,
            float shift,
            float slope,
            int inkLimit,
            int contrast) {
        shift += printedRuleOffset(gray, width, height, centerX, rows, gap, shift);
        int top = Math.max(0, Math.round(rows[0] + shift - gap * .12f));
        int bottom = Math.min(height - 1, Math.round(rows[4] + shift + gap * .12f));
        if (bottom <= top) return Integer.MIN_VALUE;
        // Gray paper must not supply the missing parts of a rest's vertical stroke.
        int[] inkCutoff = new int[bottom - top + 1], paperTones = new int[bottom - top + 1];
        int surround = Math.max(4, Math.round(gap * 2));
        int[] tones = new int[256];
        for (int y = top; y <= bottom; y++) {
            java.util.Arrays.fill(tones, 0);
            int count = 0;
            for (int x = Math.max(0, centerX - surround);
                    x <= Math.min(width - 1, centerX + surround);
                    x++) {
                tones[gray[y * width + x] & 255]++;
                count++;
            }
            // Isolated bright texture is not the paper tone against which to judge ink.
            int paper = 0, seen = tones[0], target = Math.max(1, (count * 3 + 3) / 4);
            while (seen < target && paper < 255) seen += tones[++paper];
            paperTones[y - top] = paper;
        }
        // On an exact staff-rule row, all horizontal samples can be ink. Include
        // neighboring paper rows so the rule does not break a genuine barline.
        int paperRadius = Math.max(1, Math.round(gap * .4f));
        for (int i = 0; i < inkCutoff.length; i++) {
            int paper = 0;
            for (int j = Math.max(0, i - paperRadius);
                    j <= Math.min(paperTones.length - 1, i + paperRadius);
                    j++) paper = Math.max(paper, paperTones[j]);
            inkCutoff[i] = Math.min(inkLimit, Math.max(0, paper - contrast));
        }
        int darkRows = 0, longest = 0, current = 0;
        boolean touchesTop = false, touchesBottom = false;
        int edgeBand = Math.max(2, Math.round(gap * .34f));
        for (int y = top; y <= bottom; y++) {
            boolean dark = false;
            for (int x = Math.max(0, centerX - 2); x <= Math.min(width - 1, centerX + 2); x++)
                if ((gray[y * width + x] & 0xff) <= inkCutoff[y - top]) {
                    dark = true;
                    break;
                }
            if (dark) {
                darkRows++;
                current++;
                longest = Math.max(longest, current);
                if (y <= top + edgeBand) touchesTop = true;
                if (y >= bottom - edgeBand) touchesBottom = true;
            } else current = 0;
        }
        int span = bottom - top + 1;
        if (!(touchesTop && touchesBottom && darkRows >= span * .68f && longest >= span * .48f))
            return Integer.MIN_VALUE;
        if (stackedFourCounters(gray, width, height, centerX, top, bottom, gap)
                || threeOverFourCounters(gray, width, height, centerX, top, bottom, gap))
            return Integer.MIN_VALUE;
        // A rest plus the five horizontal staff lines can satisfy the aggregate
        // coverage test while leaving an entire staff space empty. A barline
        // must also cross each of the four spaces between those lines. Ignore
        // the horizontal lines themselves so they cannot supply that evidence.
        // Test fixed perpendicular and upright axes: book shear can tilt the
        // staff while leaving its barlines vertical. Each axis must independently
        // cross all four spaces; never follow a different dark pixel on each row.
        // Choosing a different dark pixel on every row follows the diagonal stem
        // of a multi-flag rest and can incorrectly make it look like a barline.
        int lineMargin = Math.max(1, Math.round(gap * .14f));
        for (float ruleSlope : slope == 0f ? new float[] {0f} : new float[] {slope, 0f}) {
            for (int origin = centerX - 2; origin <= centerX + 2; origin++) {
                int covered = 0, sampled = 0, branched = 0, widthSamples = 0;
                boolean everySpace = true;
                for (int line = 0; line < 4; line++) {
                    int start = Math.max(0, Math.round(rows[line] + shift) + lineMargin + 1);
                    int end =
                            Math.min(
                                    height - 1,
                                    Math.round(rows[line + 1] + shift) - lineMargin - 1);
                    int spaceCovered = 0;
                    for (int y = start; y <= end; y++) {
                        boolean awayFromRule =
                                y - (rows[line] + shift) > gap * .29f
                                        && rows[line + 1] + shift - y > gap * .29f;
                        if (awayFromRule) widthSamples++;
                        int x = Math.round(origin - ruleSlope * (y - (top + bottom) * .5f));
                        if (x >= 0
                                && x < width
                                && (gray[y * width + x] & 0xff) <= inkCutoff[y - top]) {
                            spaceCovered++;
                            if (!awayFromRule) continue;
                            int reach = Math.max(3, Math.round(gap * .65f));
                            // Estimate the adjacent paper tone. Dark paper must not
                            // turn every thin line into a page-wide branch.
                            int paper = 0;
                            for (int dx = -surround; dx <= surround; dx++)
                                if (x + dx >= 0 && x + dx < width)
                                    paper = Math.max(paper, gray[y * width + x + dx] & 255);
                            int branchDark = Math.min(RAW_BARLINE_DARK, Math.max(0, paper - 25));
                            for (int direction = -1; direction <= 1; direction += 2) {
                                int distance = 1;
                                while (distance <= reach
                                        && x + direction * distance >= 0
                                        && x + direction * distance < width
                                        && (gray[y * width + x + direction * distance] & 0xff)
                                                <= branchDark) distance++;
                                if (distance > reach) {
                                    branched++;
                                    break;
                                }
                            }
                        }
                    }
                    int samples = Math.max(0, end - start + 1);
                    covered += spaceCovered;
                    sampled += samples;
                    if (spaceCovered < samples * .55f) everySpace = false;
                }
                // Stacked meter digits can contain one continuous vertical stroke. Their
                // wide branches occupy many staff-space rows; a thin bar may intersect
                // an occasional beam or slur, but does not have that repeated width.
                if (sampled > 0
                        && everySpace
                        && covered >= sampled * .90f
                        && branched <= widthSamples * .35f
                        && !oscillatingVerticalStroke(
                                gray, width, height, origin, rows, gap, shift, ruleSlope, top,
                                inkCutoff)) return origin;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** A thick arpeggio can have a straight overlapping core; its ink edges still oscillate.
     * Ignore staff-rule rows and require repeated reversals, not one beam/head intersection.
     * The axis follows the tested bar slope, so a straight tilted rule is not a wave. */
    private static boolean oscillatingVerticalStroke(
            byte[] gray,
            int width,
            int height,
            int origin,
            int[] rows,
            float gap,
            float shift,
            float slope,
            int cutoffTop,
            int[] cutoffs) {
        int radius = Math.max(3, Math.round(gap * .45f)), turns = 0, direction = 0;
        double extreme = Double.NaN,
                minimum = Double.POSITIVE_INFINITY,
                maximum = Double.NEGATIVE_INFINITY;
        double excursion = Math.max(1.1, gap * .08);
        float centerY = (rows[0] + rows[4]) * .5f + shift;
        for (int y = Math.max(0, Math.round(rows[0] + shift));
                y <= Math.min(height - 1, Math.round(rows[4] + shift));
                y++) {
            boolean rule = false;
            for (int row : rows)
                if (Math.abs(y - row - shift) <= Math.max(1, gap * .18f)) rule = true;
            if (rule) continue;
            int axis = Math.round(origin - slope * (y - centerY));
            int left = axis, right = axis, cutoff = cutoffs[y - cutoffTop];
            if (axis < 0 || axis >= width || (gray[y * width + axis] & 255) > cutoff) continue;
            while (left > Math.max(0, axis - radius)
                    && (gray[y * width + left - 1] & 255) <= cutoff) left--;
            while (right < Math.min(width - 1, axis + radius)
                    && (gray[y * width + right + 1] & 255) <= cutoff) right++;
            if (left == axis - radius || right == axis + radius) continue;
            double center = (left + right) * .5 - axis;
            minimum = Math.min(minimum, center);
            maximum = Math.max(maximum, center);
            if (Double.isNaN(extreme)) {
                extreme = center;
                continue;
            }
            if (direction == 0) {
                if (Math.abs(center - extreme) >= excursion) {
                    direction = center > extreme ? 1 : -1;
                    extreme = center;
                }
            } else if (direction * (center - extreme) >= 0) extreme = center;
            else if (Math.abs(center - extreme) >= excursion) {
                turns++;
                direction = -direction;
                extreme = center;
            }
        }
        return turns >= 4 && maximum - minimum >= gap * .15;
    }

    private static boolean hasVerticalInk(
            byte[] labels, int width, int height, int centerX, int radiusX, int top, int bottom) {
        for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++)
            for (int x = Math.max(0, centerX - radiusX);
                    x <= Math.min(width - 1, centerX + radiusX);
                    x++) if (isVerticalInk(labels[y * width + x])) return true;
        return false;
    }

    /** A stacked pair of fours can have a continuous right stroke. Its two
     * aligned triangular counters are stronger evidence than that stroke. */
    static boolean stackedFourCounters(
            byte[] gray, int width, int height, int column, int top, int bottom, float gap) {
        var counters = triangularStaffCounters(gray, width, height, column, top, bottom, gap);
        if (counters.size() != 2) return false;
        float[] a = counters.get(0), b = counters.get(1);
        return Math.abs(a[0] - b[0]) <= gap * .25f
                && b[1] - a[1] >= gap * 1.65f
                && b[1] - a[1] <= gap * 2.35f
                && a[1] < (top + bottom) * .5f - gap * .2f
                && b[1] > (top + bottom) * .5f + gap * .2f;
    }

    private static List<float[]> triangularStaffCounters(
            byte[] gray, int width, int height, int column, int top, int bottom, float gap) {
        return triangularStaffCounters(gray, width, height, column, top, bottom, gap, false);
    }

    private static List<float[]> triangularStaffCounters(
            byte[] gray,
            int width,
            int height,
            int column,
            int top,
            int bottom,
            float gap,
            boolean allowClipped) {
        int left = Math.max(0, column - Math.round(gap * 1.6f)),
                right = Math.min(width - 1, column + Math.round(gap * .5f));
        int first = Math.max(0, top - Math.round(gap * .2f)),
                last = Math.min(height - 1, bottom + Math.round(gap * .2f));
        int w = right - left + 1, h = last - first + 1;
        if (w < 5 || h < 10) return List.of();
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        List<float[]> counters = new ArrayList<>();
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (gray[(first + seed / w) * width + left + seed % w] & 255) <= 160)
                continue;
            int read = 0, write = 0;
            queue[write++] = seed;
            seen[seed] = true;
            int minX = w, maxX = -1, minY = h, maxY = -1;
            int[] rows = new int[h];
            while (read < write) {
                int at = queue[read++], x = at % w, y = at / w;
                rows[y]++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                        int next = yy * w + xx;
                        if (!seen[next] && (gray[(first + yy) * width + left + xx] & 255) > 160) {
                            seen[next] = true;
                            queue[write++] = next;
                        }
                    }
            }
            int cw = maxX - minX + 1, ch = maxY - minY + 1;
            // A staff rule can clip the top of the four's counter. Its surviving
            // widening pocket still corroborates the two open bowls above it.
            boolean clipped =
                    allowClipped
                            && Math.abs(first + minY - (top + bottom) * .5f - gap) <= gap * .2f
                            && ch >= Math.floor(gap * .15f);
            if (minX == 0
                    || maxX == w - 1
                    || minY == 0
                    || maxY == h - 1
                    || write < gap * gap * .05f
                    || write > gap * gap * .4f
                    || cw < gap * .2f
                    || cw > gap
                    || (ch < gap * .2f && !clipped)
                    || ch > gap * .85f
                    || left + maxX > column + gap * .3f) continue;
            int half = ch / 2;
            float upper = 0, lower = 0;
            for (int j = 0; j < half; j++) {
                upper += rows[minY + j];
                lower += rows[maxY - j];
            }
            // Parallel double bars enclose rectangular spaces, not widening counters.
            if ((lower - upper) / Math.max(1, half) < Math.max(1f, gap * (clipped ? .05f : .1f)))
                continue;
            counters.add(new float[] {left + (minX + maxX) * .5f, first + (minY + maxY) * .5f});
        }
        return counters;
    }

    /** A three above a four has two open left bowls over one enclosed triangular counter. */
    static boolean threeOverFourCounters(
            byte[] gray, int width, int height, int column, int top, int bottom, float gap) {
        var counters = triangularStaffCounters(gray, width, height, column, top, bottom, gap, true);
        if (counters.size() != 1) return false;
        float y = counters.get(0)[1], middle = (top + bottom) * .5f;
        if (y < middle + gap * .2f || y > middle + gap * 1.6f) return false;
        for (int bowl = 0; bowl < 2; bowl++) {
            int support = 0;
            int first = Math.max(0, Math.round(top + gap * (bowl + .2f)));
            int last = Math.min(height - 1, Math.round(top + gap * (bowl + .88f)));
            for (int row = first; row <= last; row++)
                if (separatedNumeralInk(gray, width, row, column, gap)) support++;
            if (support < Math.max(2, Math.floor(gap * .22f))) return false;
        }
        return true;
    }

    private static boolean separatedNumeralInk(
            byte[] gray, int width, int y, int column, float gap) {
        int left = Math.max(0, column - Math.round(gap * 1.4f));
        int right = Math.min(width - 1, column + Math.round(gap * .65f));
        int previousStart = -1, previousEnd = -1;
        for (int x = left; x <= right; ) {
            if ((gray[y * width + x] & 255) > 160) {
                x++;
                continue;
            }
            int start = x;
            while (x <= right && (gray[y * width + x] & 255) <= 160) x++;
            int end = x - 1, span = end - start + 1;
            if (start == left || end == right) {
                previousStart = -1;
                continue;
            }
            if (previousStart >= 0) {
                int earlier = previousEnd - previousStart + 1, space = start - previousEnd - 1;
                float center = (previousStart + previousEnd) * .5f;
                if (earlier >= gap * .15f
                        && earlier <= gap * .8f
                        && span >= gap * .15f
                        && span <= gap * .8f
                        && space >= gap * .12f
                        && space <= gap * .6f
                        && center >= column - gap * 1.1f
                        && center <= column - gap * .25f
                        && start <= column + gap * .15f
                        && end >= column - gap * .15f) return true;
            }
            previousStart = start;
            previousEnd = end;
        }
        return false;
    }

    private static int countLabel(
            byte[] labels,
            int width,
            int height,
            byte wanted,
            int left,
            int right,
            int top,
            int bottom) {
        int count = 0;
        for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++)
            for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++)
                if (labels[y * width + x] == wanted) count++;
        return count;
    }

    private static List<SystemRun> mergeAlignedStaffs(
            List<StaffRun> staffs, byte[] gray, int width, int height) {
        return mergeAlignedStaffs(staffs, null, gray, width, height);
    }

    private static List<SystemRun> mergeAlignedStaffs(
            List<StaffRun> staffs, byte[] labels, byte[] gray, int width, int height) {
        List<SystemRun> systems = new ArrayList<>();
        for (StaffRun staff : staffs) {
            if (!systems.isEmpty()) {
                SystemRun previous = systems.get(systems.size() - 1);
                float verticalGap = staff.top - previous.bottom;
                float gap = Math.max(previous.lastStaff.gap, staff.gap);
                boolean compactAligned =
                        verticalGap <= gap * MAX_GRAND_STAFF_SEPARATION_GAPS
                                && aligned(previous.boundaries, staff.boundaries, width, gap);
                boolean visiblyConnected =
                        gray != null
                                && verticalGap <= gap * MAX_CONNECTED_STAFF_SEPARATION_GAPS
                                && connectedByVerticalRule(
                                        gray, width, height, previous, staff, gap);
                StaffRun cue = previous.lastStaff;
                boolean insetCue =
                        labels != null
                                && insetCueStaff(gray, width, height, cue, staff)
                                && countLabel(
                                                labels,
                                                width,
                                                height,
                                                NOTEHEAD,
                                                cue.left,
                                                cue.right,
                                                Math.round(cue.top - cue.gap * 2),
                                                Math.round(cue.bottom + cue.gap * 2))
                                        >= cue.gap * cue.gap * .4f;
                if ((compactAligned && (gray == null || verticalGap < 0))
                        || visiblyConnected
                        || insetCue) {
                    List<Integer> upperBoundaries = previous.boundaries,
                            lowerBoundaries = staff.boundaries;
                    if (gray != null) {
                        upperBoundaries =
                                ClosedHeadBarlineGuard.withoutOwnedByOtherStaff(
                                        previous.boundaries,
                                        staff.boundaries,
                                        gray,
                                        width,
                                        height,
                                        Math.round(staff.top),
                                        Math.round(staff.bottom),
                                        staff.gap);
                        lowerBoundaries =
                                ClosedHeadBarlineGuard.withoutOwnedByOtherStaff(
                                        staff.boundaries,
                                        previous.boundaries,
                                        gray,
                                        width,
                                        height,
                                        Math.round(previous.lastStaff.top),
                                        Math.round(previous.lastStaff.bottom),
                                        previous.lastStaff.gap);
                    }
                    previous.bottom = staff.bottom;
                    previous.lastStaff = staff;
                    previous.gap = (previous.gap + staff.gap) / 2f;
                    previous.boundaries =
                            mergeBoundaries(upperBoundaries, lowerBoundaries, width, previous.gap);
                    continue;
                }
            }
            systems.add(new SystemRun(staff));
        }
        return systems;
    }

    /**
     * Finds a bracket or a barline that physically crosses the whitespace between two staves.
     * Looking only beside already plausible boundaries avoids treating lyrics, dynamics, or one
     * unusually long note stem as a system connector. The raw page is used because the model often
     * labels the upper and lower pieces of one shared barline independently.
     */
    private static boolean connectedByVerticalRule(
            byte[] gray, int width, int height, SystemRun upper, StaffRun lower, float gap) {
        int top = Math.max(0, Math.round(upper.bottom + gap * .12f));
        int bottom = Math.min(height - 1, Math.round(lower.top - gap * .12f));
        if (bottom - top < Math.max(3, Math.round(gap * .45f))) return false;
        List<Integer> candidates =
                new ArrayList<>(upper.boundaries.size() + lower.boundaries.size());
        candidates.addAll(upper.boundaries);
        candidates.addAll(lower.boundaries);
        int horizontalTolerance = Math.max(2, Math.round(gap * 1.65f));
        boolean[] checked = new boolean[width];
        for (int candidate : candidates) {
            int left = Math.max(0, candidate - horizontalTolerance);
            int right = Math.min(width - 1, candidate + horizontalTolerance);
            for (int x = left; x <= right; x++) {
                if (checked[x]) continue;
                checked[x] = true;
                if (verticalRuleAt(gray, width, x, top, bottom, gap)
                        && (horizontalStaffBeside(
                                        gray,
                                        width,
                                        height,
                                        x,
                                        upper.lastStaff.top,
                                        upper.lastStaff.gap,
                                        upper.lastStaff.slope)
                                || staggeredCueBracket(
                                        gray, width, height, x, upper.lastStaff, lower, gap))
                        && horizontalStaffBeside(
                                gray, width, height, x, lower.top, lower.gap, lower.slope))
                    return true;
            }
        }
        return false;
    }

    /** A reduced staff can enter after the first bar of its full-sized partner.
     * Its system bracket still starts at the partner's left edge. Require the
     * nested extent, shared bars and bounded bracket ends before joining it. */
    private static boolean staggeredCueBracket(
            byte[] gray, int width, int height, int x, StaffRun upper, StaffRun lower, float gap) {
        if (upper.slope != 0
                || lower.slope != 0
                || upper.gap < lower.gap * .6f
                || upper.gap > lower.gap * .9f
                || upper.left < lower.left + gap * 8
                || upper.right - upper.left > (lower.right - lower.left) * .85f
                || Math.abs(upper.right - lower.right) > gap * 2
                || Math.abs(x - lower.left) > gap * 1.7f
                || upper.boundaries.size() < 3) return false;
        for (int boundary : upper.boundaries) {
            boolean shared = false;
            for (int other : lower.boundaries)
                if (Math.abs(boundary - other) <= gap * .9f) {
                    shared = true;
                    break;
                }
            if (!shared) return false;
        }
        if (!horizontalStaffBeside(
                gray, width, height, upper.left, upper.top, upper.gap, upper.slope)) return false;
        int top = Math.max(0, Math.round(upper.top)),
                bottom = Math.min(height - 1, Math.round(lower.bottom));
        if (!verticalRuleAt(gray, width, x, top, bottom, gap)) return false;
        int margin = Math.max(3, Math.round(gap * .75f));
        // A page crease continues beyond the system; a bracket ends beside its rules.
        if (top - margin < 0 || bottom + margin >= height) return false;
        int radius = Math.max(1, Math.round(gap * .16f));
        for (int row : new int[] {top - margin, bottom + margin})
            for (int column = Math.max(0, x - radius);
                    column <= Math.min(width - 1, x + radius);
                    column++) if ((gray[row * width + column] & 255) < 205) return false;
        return true;
    }

    /** A crease can cross every system, but it does not join their five printed rules. */
    private static boolean horizontalStaffBeside(
            byte[] gray, int width, int height, int x, float top, float gap, float slope) {
        int radius = Math.max(2, Math.round(gap * .25f));
        for (int side : new int[] {-1, 1}) {
            int supported = 0;
            for (int line = 0; line < 5; line++) {
                int samples = 0, hits = 0;
                for (int offset = Math.round(gap * 2); offset <= Math.round(gap * 6); offset++) {
                    int xx = x + side * offset;
                    if (xx < 0 || xx >= width) continue;
                    samples++;
                    int yy = Math.round(top + line * gap + slope * (xx - width * .5f));
                    if (thinHorizontalInk(gray, width, height, xx, yy, radius, gap)) hits++;
                }
                if (samples >= gap * 2 && hits >= samples * .35f) supported++;
            }
            if (supported >= 3) return true;
        }
        return false;
    }

    private static boolean thinHorizontalInk(
            byte[] gray, int width, int height, int x, int row, int radius, float gap) {
        int flank = Math.max(2, Math.round(gap * .32f));
        for (int y = Math.max(flank, row - radius);
                y <= Math.min(height - 1 - flank, row + radius);
                y++) {
            int ink = gray[y * width + x] & 255;
            if (ink <= 170
                    && (gray[(y - flank) * width + x] & 255) >= ink + 12
                    && (gray[(y + flank) * width + x] & 255) >= ink + 12) return true;
        }
        return false;
    }

    static boolean verticalRuleAt(
            byte[] gray, int width, int centerX, int top, int bottom, float gap) {
        int radius = Math.max(1, Math.round(gap * .16f));
        int edgeBand = Math.max(2, Math.round(gap * .42f));
        int rows = bottom - top + 1, darkRows = 0, longest = 0, run = 0, blanks = 0;
        boolean touchesTop = false, touchesBottom = false;
        for (int y = top; y <= bottom; y++) {
            int ink = 255;
            for (int x = Math.max(0, centerX - radius);
                    x < Math.min(width, centerX + radius + 1);
                    x++) ink = Math.min(ink, gray[y * width + x] & 0xff);
            // A colored/scanned background can be darker than the absolute ink threshold for
            // the entire page. A connector must also be a narrow stroke with lighter paper on
            // BOTH sides, not merely a dark column through otherwise unconnected systems.
            int leftPaper = 0, rightPaper = 0;
            int flank = Math.max(radius + 2, Math.round(gap * .85f));
            for (int offset = radius + 1; offset <= flank; offset++) {
                if (centerX - offset >= 0)
                    leftPaper = Math.max(leftPaper, gray[y * width + centerX - offset] & 0xff);
                if (centerX + offset < width)
                    rightPaper = Math.max(rightPaper, gray[y * width + centerX + offset] & 0xff);
            }
            boolean dark =
                    ink <= RAW_BARLINE_DARK && leftPaper - ink >= 24 && rightPaper - ink >= 24;
            if (dark) {
                darkRows++;
                run += blanks + 1;
                blanks = 0;
                longest = Math.max(longest, run);
                if (y <= top + edgeBand) touchesTop = true;
                if (y >= bottom - edgeBand) touchesBottom = true;
            } else if (++blanks > Math.max(1, Math.round(gap * .18f))) {
                run = 0;
                blanks = 0;
            }
        }
        return touchesTop && touchesBottom && darkRows >= rows * .66f && longest >= rows * .72f;
    }

    /** A barline can be faint on only one stave of a grand staff. Once the staves are known to
     * align, retain the union of their independently detected boundaries instead of selecting
     * whichever stave happened to produce the larger list. */
    private static List<Integer> mergeBoundaries(
            List<Integer> first, List<Integer> second, int width, float gap) {
        int tolerance = Math.max(Math.round(gap * 2.2f), width / 38);
        List<Integer> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        combined.sort(Integer::compare);
        List<Integer> merged = new ArrayList<>();
        int total = combined.get(0), count = 1;
        for (int index = 1; index < combined.size(); index++) {
            int value = combined.get(index);
            if (value - Math.round(total / (float) count) <= tolerance) {
                total += value;
                count++;
            } else {
                merged.add(Math.round(total / (float) count));
                total = value;
                count = 1;
            }
        }
        merged.add(Math.round(total / (float) count));
        // The per-stave detector already rejects implausibly busy systems. Keep that same safety
        // ceiling after merging rather than allowing two noisy masks to amplify each other.
        if (merged.size() > 32)
            return first.size() >= second.size() ? List.copyOf(first) : List.copyOf(second);
        return List.copyOf(merged);
    }

    private static boolean aligned(
            List<Integer> first, List<Integer> second, int width, float gap) {
        int smaller = Math.min(first.size(), second.size());
        if (smaller < 2 || Math.abs(first.size() - second.size()) > 1) return false;
        int tolerance = Math.max(Math.round(gap * 2.2f), width / 38);
        int matches = 0;
        for (int value : first) {
            for (int other : second)
                if (Math.abs(value - other) <= tolerance) {
                    matches++;
                    break;
                }
        }
        return matches >= Math.ceil(smaller * 0.72f);
    }

    private static void addMeasures(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            SystemRun system,
            List<MeasureRegion> output,
            float headTop,
            float headBottom) {
        List<Integer> boundaries = system.boundaries;
        for (int index = 0; index + 1 < boundaries.size(); index++) {
            int rawLeft = boundaries.get(index), rawRight = boundaries.get(index + 1);
            float shiftLeft = system.slope * (rawLeft - width / 2f);
            float shiftRight = system.slope * (rawRight - width / 2f);
            float top =
                    Math.max(
                            0f,
                            (system.top + Math.min(shiftLeft, shiftRight) - system.gap * 2.1f)
                                    / height);
            float bottom =
                    Math.min(
                            1f,
                            (system.bottom + Math.max(shiftLeft, shiftRight) + system.gap * 2.1f)
                                    / height);
            if (system.firstStaff.track != null) {
                float[] a = system.firstStaff.track.at(rawLeft),
                        b = system.firstStaff.track.at(rawRight);
                top =
                        Math.max(
                                0,
                                (Math.min(a[0] - 4 * a[1], b[0] - 4 * b[1]) - system.gap * 2.1f)
                                        / height);
            }
            if (system.lastStaff.track != null) {
                float[] a = system.lastStaff.track.at(rawLeft),
                        b = system.lastStaff.track.at(rawRight);
                bottom = Math.min(1, (Math.max(a[0], b[0]) + system.gap * 2.1f) / height);
            }
            int inset = Math.max(2, Math.round(system.gap * 0.55f));
            int playableLeft = rawLeft + inset;
            if (index == 0) {
                // Only the compact header immediately after the left staff edge can be a clef,
                // key, or time signature. Searching the entire first measure lets an isolated
                // class-3 mistake beside a later note chop most of that measure away.
                int headerLimit =
                        Math.min(
                                rawRight,
                                rawLeft + Math.max(Math.round(system.gap * 8f), width / 30));
                int headerRight =
                        rightmostLabel(
                                labels,
                                width,
                                height,
                                CLEF_OR_KEY,
                                rawLeft,
                                headerLimit,
                                Math.round(system.top - system.gap * 2f),
                                Math.round(system.bottom + system.gap * 2f));
                if (headerRight >= 0)
                    playableLeft =
                            Math.max(
                                    playableLeft,
                                    headerRight + Math.max(2, Math.round(system.gap * 0.8f)));
                // A stray accidental/clef label beside the first note must not
                // crop that note out of the playable measure. A substantial
                // notehead in the header window is the stopping point.
                int firstHead =
                        firstHeaderHead(
                                labels,
                                width,
                                height,
                                rawLeft,
                                headerLimit,
                                Math.round(headTop),
                                Math.round(headBottom),
                                system.gap);
                if (firstHead >= 0)
                    playableLeft =
                            Math.min(
                                    playableLeft,
                                    firstHead - Math.max(2, Math.round(system.gap * .12f)));
            }
            int playableRight = rawRight - inset;
            if (index + 2 == boundaries.size()
                    && index > 0
                    && courtesySignatureTail(
                            labels,
                            gray,
                            width,
                            height,
                            rawLeft,
                            rawRight,
                            system.firstStaff.top,
                            system.firstStaff.bottom,
                            system.gap,
                            Math.round(headTop),
                            Math.round(headBottom))) continue;
            // A complete key/meter header can contain a vertical numeral that the model
            // classifies as a barline. It may enclose a tiny symbol-only pocket before the
            // first printed note. That pocket has no musical time and must not add a bar.
            if (index == 0
                    && boundaries.size() > 2
                    && playableRight - playableLeft <= system.gap * 4f
                    && countLabel(
                                    labels,
                                    width,
                                    height,
                                    CLEF_OR_KEY,
                                    rawLeft,
                                    rawRight,
                                    Math.round(system.top - system.gap),
                                    Math.round(system.bottom + system.gap))
                            >= system.gap * 2f
                    && countLabel(
                                    labels,
                                    width,
                                    height,
                                    NOTEHEAD,
                                    playableLeft,
                                    playableRight,
                                    Math.round(headTop),
                                    Math.round(headBottom))
                            <= Math.max(1, Math.round(system.gap * .1f))
                    && countLabel(
                                    labels,
                                    width,
                                    height,
                                    STEM_OR_REST,
                                    playableLeft,
                                    playableRight,
                                    Math.round(system.top),
                                    Math.round(system.bottom))
                            == 0) continue;
            if (playableRight - playableLeft >= Math.max(6, Math.round(system.gap * 2.2f)))
                output.add(
                        new MeasureRegion(
                                playableLeft / (float) width,
                                playableRight / (float) width,
                                top,
                                bottom));
        }
    }

    /** A short key-only tail beyond a printed double bar is a courtesy header, not musical time. */
    static boolean courtesySignatureTail(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int staffTop,
            int staffBottom,
            float gap,
            int headTop,
            int headBottom) {
        if (gray == null
                || right - left > gap * 6
                || right - left < gap
                || countLabel(labels, width, height, NOTEHEAD, left, right, headTop, headBottom) > 0
                || countLabel(labels, width, height, CLEF_OR_KEY, left, right, headTop, headBottom)
                        < gap * gap * 2) return false;
        int bands = 0, first = -1, last = -1;
        boolean inBand = false;
        for (int x = Math.max(0, Math.round(left - gap * 1.3f));
                x <= Math.min(width - 1, Math.round(left + gap * .9f));
                x++) {
            int ink = 0;
            for (int y = Math.max(0, staffTop); y <= Math.min(height - 1, staffBottom); y++)
                if ((gray[y * width + x] & 255) < 165) ink++;
            boolean bar = ink >= (staffBottom - staffTop + 1) * .9f;
            if (bar && !inBand) {
                bands++;
                if (first < 0) first = x;
                last = x;
            }
            inBand = bar;
        }
        return bands == 2 && last - first >= gap * .2f && last - first <= gap * 1.3f;
    }

    private static int rightmostLabel(
            byte[] labels,
            int width,
            int height,
            byte wanted,
            int left,
            int right,
            int top,
            int bottom) {
        for (int x = Math.min(width - 1, right); x >= Math.max(0, left); x--)
            for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++)
                if (labels[y * width + x] == wanted) return x;
        return -1;
    }

    private static boolean continuousStaffExtension(int[] columns, int left, int right) {
        int supported = 0;
        for (int x = left; x <= right; x++) if (columns[x] >= 4) supported++;
        return supported >= (right - left + 1) * .90f;
    }

    private static int firstHeaderHead(
            byte[] labels,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap) {
        for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++) {
            int pixels = 0;
            for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++)
                if (labels[y * width + x] == NOTEHEAD) pixels++;
            if (pixels >= Math.max(2, Math.round(gap * .20f))
                    && countLabel(
                                    labels,
                                    width,
                                    height,
                                    NOTEHEAD,
                                    x,
                                    Math.round(x + gap * 1.4f),
                                    top,
                                    bottom)
                            >= Math.max(5, Math.round(gap * gap * .16f))) {
                // The bottom curl of a treble clef can be labelled as a head.
                // A tall clef body at the same x keeps it inside the header.
                int first = -1, last = -1, clefPixels = 0;
                for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++)
                    for (int xx = Math.max(0, Math.round(x - gap * .7f));
                            xx <= Math.min(width - 1, Math.round(x + gap * 1.7f));
                            xx++)
                        if (labels[y * width + xx] == CLEF_OR_KEY) {
                            if (first < 0) first = y;
                            last = y;
                            clefPixels++;
                        }
                if (last - first > gap * 4f && clefPixels > gap * gap * 1.6f) continue;
                return x;
            }
        }
        return -1;
    }

    private record StaffRun(
            int top,
            int bottom,
            float gap,
            int left,
            int right,
            List<Integer> boundaries,
            float slope,
            StaffPitchTrack track) {
        StaffRun(
                int top,
                int bottom,
                float gap,
                int left,
                int right,
                List<Integer> boundaries,
                float slope) {
            this(top, bottom, gap, left, right, boundaries, slope, null);
        }
    }

    private static final class SystemRun {
        final int top;
        int bottom;
        float gap;
        List<Integer> boundaries;
        final float slope;
        final StaffRun firstStaff;
        // Connector evidence belongs to the adjacent staff, not the system's average scale.
        StaffRun lastStaff;

        SystemRun(StaffRun staff) {
            this.firstStaff = staff;
            this.top = staff.top;
            this.bottom = staff.bottom;
            this.gap = staff.gap;
            this.boundaries = staff.boundaries;
            this.slope = staff.slope;
            this.lastStaff = staff;
        }
    }
}
