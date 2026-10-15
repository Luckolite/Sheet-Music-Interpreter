// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Adapted from Music Sheets: standalone package and platform-independent diagnostics.
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Recovers long five-line staffs directly from page pixels when the semantic mask is incomplete. */
final class RawStaffLineDetector {
    private static final int DARK = 170;

    private RawStaffLineDetector() {}

    record StaffLines(int[] rows, float gap) {
        int top() {
            return rows[0];
        }

        int bottom() {
            return rows[4];
        }

        float center() {
            return (rows[0] + rows[4]) * .5f;
        }
    }

    private record Candidate(int[] rows, float gap, float score) {}

    static List<StaffLines> detect(byte[] gray, int width, int height) {
        if (gray == null || width <= 0 || height <= 0 || gray.length != width * height)
            return List.of();

        int[] rowStrength = new int[height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) if ((gray[offset + x] & 0xff) <= DARK) rowStrength[y]++;
        }

        int minimumStrength = Math.max(24, Math.round(width * .25f));
        List<StaffLines> result =
                new ArrayList<>(
                        detectFromStrength(
                                rowStrength, minimumStrength, height, false, gray, width));
        retainDominantStaffScale(result, gray, width, height);
        return List.copyOf(result);
    }

    /** Finds five-line groups in an already deskewed projection. Shared with the semantic
     * postprocessor so stray staff-labelled notation between lines cannot merge a whole system
     * into one continuous projection band. */
    static List<StaffLines> detectFromStrength(int[] rowStrength, int minimumStrength, int height) {
        return detectFromStrength(rowStrength, minimumStrength, height, true);
    }

    /** A few staff-labelled slurs can invent a sixth-to-tenth-rule group in the
     * narrow whitespace between two independently printed staves. */
    static List<StaffLines> detectFromStrength(
            int[] strength, int minimum, int height, byte[] gray, int width) {
        List<StaffLines> original = detectFromStrength(strength, minimum, height);
        if (gray == null || width <= 0 || gray.length != width * height) return original;
        List<StaffLines> result = new ArrayList<>(original);
        for (int i = 1; i + 1 < original.size(); i++) {
            StaffLines a = original.get(i - 1), b = original.get(i), c = original.get(i + 1);
            float gap = Math.max(b.gap(), Math.max(a.gap(), c.gap()));
            int above = b.top() - a.bottom(), below = c.top() - b.bottom();
            if (above <= 0 || below <= 0 || above > gap * 1.5f || below > gap * 1.5f) continue;
            if (printedRuleCount(new Candidate(b.rows(), b.gap(), 0), gray, width, height, 230) <= 1
                    && printedRuleCount(new Candidate(a.rows(), a.gap(), 0), gray, width, height)
                            >= 4
                    && printedRuleCount(new Candidate(c.rows(), c.gap(), 0), gray, width, height)
                            >= 4) result.remove(b);
        }
        rejectUnsupportedAliases(result, gray, width, height);
        return List.copyOf(result);
    }

    /** Replace only compressed semantic aliases independently contradicted by five printed rules. */
    static List<StaffLines> detectValidatedFromStrength(
            int[] strength, int minimum, int height, byte[] gray, int width) {
        List<StaffLines> original = detectFromStrength(strength, minimum, height);
        List<StaffLines> printed =
                detectFromStrength(strength, minimum, height, false, gray, width);
        List<StaffLines> result = new ArrayList<>(original);
        for (StaffLines proof : printed) {
            List<StaffLines> aliases = new ArrayList<>();
            for (StaffLines existing : result)
                if (existing.gap() < proof.gap() * .68f
                        && existing.top() >= proof.top() - proof.gap() * 2.2f
                        && existing.bottom() <= proof.bottom() + proof.gap() * 2.2f)
                    aliases.add(existing);
            if (aliases.isEmpty()) continue;
            result.removeAll(aliases);
            boolean represented = false;
            for (StaffLines existing : result)
                if (Math.abs(existing.center() - proof.center()) <= proof.gap() * 2.2f
                        && Math.abs(existing.gap() - proof.gap()) <= proof.gap() * .18f)
                    represented = true;
            if (!represented) result.add(proof);
        }
        result.sort(Comparator.comparingInt(StaffLines::top));
        rejectUnsupportedAliases(result, gray, width, height);
        return List.copyOf(result);
    }

    /** Two independent printed staves distinguish page notation from unrelated semantic texture. */
    private static void rejectUnsupportedAliases(
            List<StaffLines> staffs, byte[] gray, int width, int height) {
        if (gray == null || gray.length != (long) width * height) return;
        int proved = 0, first = height, last = -1;
        for (var staff : staffs)
            if (printedRuleCount(new Candidate(staff.rows(), staff.gap(), 0), gray, width, height)
                    >= 4) {
                proved++;
                first = Math.min(first, staff.top());
                last = Math.max(last, staff.bottom());
            }
        // The semantic model can omit every real staff while labeling page artwork.
        // Independent complete printed staves can still establish the notation envelope.
        if (proved < 2) {
            int rawProved = 0, rawFirst = height, rawLast = -1;
            for (var staff : detect(gray, width, height)) {
                if (printedRuleCount(
                                new Candidate(staff.rows(), staff.gap(), 0), gray, width, height)
                        < 4) continue;
                rawProved++;
                rawFirst = Math.min(rawFirst, staff.top());
                rawLast = Math.max(rawLast, staff.bottom());
            }
            if (rawProved >= 2) {
                proved = rawProved;
                first = rawFirst;
                last = rawLast;
            }
        }
        if (proved < 2) return;
        rejectIrregularInteriorAliases(staffs, gray, width, height);
        rejectUnsupportedInteriorTexture(staffs, gray, width, height);
        final int firstPrinted = first, lastPrinted = last;
        // Interior gaps may contain faint parts; this rejection only handles outer texture.
        staffs.removeIf(
                staff ->
                        (staff.bottom() < firstPrinted || staff.top() > lastPrinted)
                                && (printedRuleCount(
                                                        new Candidate(staff.rows(), staff.gap(), 0),
                                                        gray,
                                                        width,
                                                        height,
                                                        230)
                                                == 0
                                        || printedRuleCount(
                                                                new Candidate(
                                                                        staff.rows(),
                                                                        staff.gap(),
                                                                        0),
                                                                gray,
                                                                width,
                                                                height,
                                                                230)
                                                        <= 2
                                                && ConvergingStaffStrokes.matches(
                                                        gray,
                                                        width,
                                                        height,
                                                        staff.rows(),
                                                        staff.gap()))
                                && !hasLocalContrastedRule(staff, gray, width, height)
                                && StaffPitchTrack.detectForMeasures(
                                                gray,
                                                width,
                                                height,
                                                staff.top(),
                                                staff.bottom(),
                                                staff.gap())
                                        == null);
    }

    /** Multiple texture groups can sit between real systems; each needs its own printed support. */
    private static void rejectUnsupportedInteriorTexture(
            List<StaffLines> staffs, byte[] gray, int width, int height) {
        var proved = new ArrayList<StaffLines>();
        for (var staff : staffs)
            if (printedRuleCount(
                                    new Candidate(staff.rows(), staff.gap(), 0),
                                    gray,
                                    width,
                                    height,
                                    230)
                            >= 4
                    && hasFiveParallelLocalRules(staff, gray, width, height)) proved.add(staff);
        if (proved.size() < 2) return;
        staffs.removeIf(
                staff -> {
                    boolean above = false, below = false;
                    for (var proof : proved) {
                        above |= proof.bottom() < staff.top();
                        below |= proof.top() > staff.bottom();
                    }
                    if (!above
                            || !below
                            || proved.contains(staff)
                            || !hasBackgroundTexture(staff, gray, width, height)) return false;
                    return printedRuleCount(
                                            new Candidate(staff.rows(), staff.gap(), 0),
                                            gray,
                                            width,
                                            height,
                                            230)
                                    <= 1
                            && !hasLocalContrastedRule(staff, gray, width, height)
                            && !hasFiveParallelLocalRules(staff, gray, width, height)
                            && StaffPitchTrack.detectForMeasures(
                                            gray,
                                            width,
                                            height,
                                            staff.top(),
                                            staff.bottom(),
                                            staff.gap())
                                    == null;
                });
    }

    /** Distributed local variation supplies positive image-texture evidence, not just missing rules. */
    private static boolean hasBackgroundTexture(
            StaffLines staff, byte[] gray, int width, int height) {
        int size = Math.max(4, Math.round(staff.gap())), total = 0, textured = 0;
        for (int y = Math.max(0, staff.top()); y <= staff.bottom() && y + size <= height; y += size)
            for (int x = 0; x + size <= width; x += size * 2) {
                long sum = 0, squares = 0;
                int count = size * size;
                for (int yy = y; yy < y + size; yy++)
                    for (int xx = x; xx < x + size; xx++) {
                        int value = gray[yy * width + xx] & 255;
                        sum += value;
                        squares += (long) value * value;
                    }
                double mean = sum / (double) count;
                if (squares / (double) count - mean * mean > 25) textured++;
                total++;
            }
        return textured >= 16 && textured >= total * .2;
    }

    /** Interior semantic texture must contradict both neighboring printed scales before rejection. */
    private static void rejectIrregularInteriorAliases(
            List<StaffLines> staffs, byte[] gray, int width, int height) {
        var original = new ArrayList<>(staffs);
        for (int i = 1; i + 1 < original.size(); i++) {
            var a = original.get(i - 1);
            var b = original.get(i);
            var c = original.get(i + 1);
            if (b.gap() >= Math.min(a.gap(), c.gap()) * .85f) continue;
            int smallest = Integer.MAX_VALUE, largest = 0;
            for (int j = 1; j < 5; j++) {
                int delta = b.rows()[j] - b.rows()[j - 1];
                smallest = Math.min(smallest, delta);
                largest = Math.max(largest, delta);
            }
            if (smallest >= b.gap() * .7f || largest <= b.gap() * 1.2f) continue;
            if (printedRuleCount(new Candidate(a.rows(), a.gap(), 0), gray, width, height) < 4
                    || printedRuleCount(new Candidate(c.rows(), c.gap(), 0), gray, width, height)
                            < 4
                    || printedRuleCount(
                                    new Candidate(b.rows(), b.gap(), 0), gray, width, height, 230)
                            > 0) continue;
            if (StaffPitchTrack.detectForMeasures(gray, width, height, b.top(), b.bottom(), b.gap())
                            != null
                    || hasFiveParallelLocalRules(b, gray, width, height)) continue;
            staffs.remove(b);
        }
    }

    /** Short or pale real staves can retain five parallel rules without a long page projection. */
    private static boolean hasFiveParallelLocalRules(
            StaffLines staff, byte[] gray, int width, int height) {
        int phase = Math.max(1, Math.round(staff.gap() * .5f)),
                radius = Math.max(1, Math.round(staff.gap() * .15f));
        int flank = Math.max(2, Math.round(staff.gap() * .35f)),
                needed = Math.max(12, Math.round(staff.gap() * 4));
        for (int shift = -phase; shift <= phase; shift++) {
            int run = 0;
            for (int x = 0; x < width; x++) {
                boolean all = true;
                for (int line = 0; line < 5; line++) {
                    int center = Math.round(staff.top() + shift + line * staff.gap());
                    boolean hit = false;
                    for (int y = Math.max(flank, center - radius);
                            y <= Math.min(height - flank - 1, center + radius);
                            y++) {
                        int value = gray[y * width + x] & 255;
                        if ((gray[(y - flank) * width + x] & 255) >= value + 4
                                && (gray[(y + flank) * width + x] & 255) >= value + 4) {
                            hit = true;
                            break;
                        }
                    }
                    if (!hit) {
                        all = false;
                        break;
                    }
                }
                run = all ? run + 1 : 0;
                if (run >= needed) return true;
            }
        }
        return false;
    }

    /** Parallel local contrast preserves faint or short rules without treating unrelated texture as a staff. */
    private static boolean hasLocalContrastedRule(
            StaffLines staff, byte[] gray, int width, int height) {
        int radius = Math.max(1, Math.round(staff.gap() * .35f)),
                flank = Math.max(2, Math.round(staff.gap() * .35f));
        int[] hits = new int[width];
        for (int row : staff.rows())
            for (int x = 0; x < width; x++) {
                for (int y = Math.max(flank, row - radius);
                        y <= Math.min(height - flank - 1, row + radius);
                        y++) {
                    int value = gray[y * width + x] & 255;
                    if ((gray[(y - flank) * width + x] & 255) >= value + 4
                            && (gray[(y + flank) * width + x] & 255) >= value + 4) {
                        hits[x]++;
                        break;
                    }
                }
            }
        for (int minimum : new int[] {3, 4}) {
            int count = 0, run = 0, longest = 0;
            for (int n : hits)
                if (n >= minimum) {
                    count++;
                    longest = Math.max(longest, ++run);
                } else run = 0;
            if (count >= Math.max(24, Math.round(staff.gap() * 4))
                    && longest >= Math.max(12, Math.round(staff.gap() * (minimum == 3 ? 8 : 3))))
                return true;
        }
        return false;
    }

    private static List<StaffLines> detectFromStrength(
            int[] rowStrength, int minimumStrength, int height, boolean filterScale) {
        return detectFromStrength(rowStrength, minimumStrength, height, filterScale, null, 0);
    }

    private static List<StaffLines> detectFromStrength(
            int[] rowStrength,
            int minimumStrength,
            int height,
            boolean filterScale,
            byte[] gray,
            int width) {
        if (rowStrength == null || rowStrength.length != height || minimumStrength <= 0)
            return List.of();
        List<Integer> peaks = localPeaks(rowStrength, minimumStrength);
        List<Candidate> candidates = staffCandidates(peaks, rowStrength, minimumStrength, height);

        // Highest-confidence candidate wins when nearby notation creates multiple possible groups
        // around one staff. Staffs cannot overlap, so accepting the best groups first avoids an
        // ornament above a staff shifting all five selected rows by one position.
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        List<StaffLines> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (gray != null && !hasPrintedRules(candidate, gray, width, height)) continue;
            boolean overlaps = false;
            for (StaffLines accepted : result) {
                float margin = Math.min(candidate.gap(), accepted.gap()) * .75f;
                if (candidate.rows()[0] <= accepted.bottom() + margin
                        && candidate.rows()[4] >= accepted.top() - margin) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) result.add(new StaffLines(candidate.rows(), candidate.gap()));
        }
        if (filterScale) retainDominantStaffScale(result, null, 0, 0);
        result.sort(Comparator.comparingInt(StaffLines::top));
        return List.copyOf(result);
    }

    /** Shadows can form periodic dark projection peaks without any thin printed rules.
     * Validate before resolving overlaps so an invalid high-scoring group cannot hide a staff.
     * Partial support is sufficient when a beam obscures the middle rules. */
    private static boolean hasPrintedRules(
            Candidate candidate, byte[] gray, int width, int height) {
        return printedRuleCount(candidate, gray, width, height) == 5;
    }

    private static int printedRuleCount(Candidate candidate, byte[] gray, int width, int height) {
        return printedRuleCount(candidate, gray, width, height, DARK);
    }

    private static int printedRuleCount(
            Candidate candidate, byte[] gray, int width, int height, int inkLimit) {
        int supported = 0;
        int probe = Math.max(2, Math.round(candidate.gap() * .32f));
        int minimum = Math.max(24, Math.round(width * .12f));
        for (int y : candidate.rows()) {
            if (y < probe || y >= height - probe) continue;
            int count = 0, run = 0, longest = 0;
            for (int x = 0; x < width; x++) {
                int ink = gray[y * width + x] & 255;
                if (ink <= inkLimit
                        && (gray[(y - probe) * width + x] & 255) >= ink + 12
                        && (gray[(y + probe) * width + x] & 255) >= ink + 12) {
                    count++;
                    longest = Math.max(longest, ++run);
                } else run = 0;
            }
            // Text baselines have plenty of thin ink, but each glyph interrupts the rule.
            // Require a short continuous segment as well as aggregate page-wide support.
            if (count >= minimum && longest >= Math.max(12, Math.round(candidate.gap() * 3)))
                supported++;
        }
        return supported;
    }

    /**
     * A page is engraved at one staff scale even when its measure widths and note density vary.
     * Four stacked arpeggio beams can also make five long, regularly spaced projection peaks,
     * but their gap is much smaller than every real staff on the page. Keep the page's dominant
     * scale so those beam bands cannot become extra systems while still allowing raw recovery of
     * a staff that the model omitted completely.
     */
    private static void retainDominantStaffScale(
            List<StaffLines> staffs, byte[] gray, int width, int height) {
        if (staffs == null || staffs.size() < 4) return;
        List<Float> gaps = new ArrayList<>();
        for (StaffLines staff : staffs) gaps.add(staff.gap());
        gaps.sort(Float::compare);
        float median = gaps.get(gaps.size() / 2);
        int consistent = 0;
        for (float gap : gaps) if (gap >= median * .68f && gap <= median * 1.47f) consistent++;
        if (consistent * 2 < staffs.size()) return;
        List<StaffLines> candidates = List.copyOf(staffs);
        staffs.removeIf(
                staff ->
                        (staff.gap() < median * .68f || staff.gap() > median * 1.47f)
                                && !(gray != null
                                        && staff.gap() >= median * .55f
                                        && staff.gap() < median * .68f
                                        && connectedToStaff(
                                                staff, candidates, gray, width, height)));
    }

    /** A cue-sized part is real when a printed system rule joins it to another five-line staff. */
    static boolean connectedToStaff(
            StaffLines staff, List<StaffLines> candidates, byte[] gray, int width, int height) {
        if (gray == null) return false;
        int staffEdge = Integer.MIN_VALUE;
        for (StaffLines other : candidates) {
            if (other == staff) continue;
            StaffLines upper = staff.top() < other.top() ? staff : other,
                    lower = upper == staff ? other : staff;
            float gap = Math.max(upper.gap(), lower.gap());
            int separation = lower.top() - upper.bottom();
            if (separation <= 0 || separation > gap * 16) continue;
            int top = Math.max(0, Math.round(upper.bottom() + gap * .12f));
            int bottom = Math.min(height - 1, Math.round(lower.top() - gap * .12f));
            int radius = Math.max(2, Math.round(gap * 2));
            // The queried staff and raster stay unchanged throughout this connection search.
            // Read both edges in their original order before examining either connector.
            int upperEdge =
                    upper == staff && staffEdge != Integer.MIN_VALUE
                            ? staffEdge
                            : leftEdge(upper, gray, width, height);
            int lowerEdge =
                    lower == staff && staffEdge != Integer.MIN_VALUE
                            ? staffEdge
                            : leftEdge(lower, gray, width, height);
            staffEdge = upper == staff ? upperEdge : lowerEdge;
            for (int edge : new int[] {upperEdge, lowerEdge}) {
                if (edge < 0) continue;
                for (int x = Math.max(0, edge - radius);
                        x <= Math.min(width - 1, edge + radius);
                        x++)
                    if (OmrMeasurePostProcessor.verticalRuleAt(gray, width, x, top, bottom, gap))
                        return true;
            }
        }
        return false;
    }

    private static int leftEdge(StaffLines staff, byte[] gray, int width, int height) {
        for (int x = 0; x < width; x++) {
            int lines = 0;
            for (int row : staff.rows()) {
                boolean ink = false;
                for (int y = Math.max(0, row - 1); y <= Math.min(height - 1, row + 1); y++)
                    if ((gray[y * width + x] & 255) <= DARK) ink = true;
                if (ink) lines++;
            }
            if (lines >= 4) return x;
        }
        return -1;
    }

    private static List<Integer> localPeaks(int[] strength, int minimumStrength) {
        List<Integer> raw = new ArrayList<>();
        for (int row = 0; row < strength.length; row++) {
            if (strength[row] < minimumStrength) continue;
            boolean maximum = true, prominent = false;
            for (int check = Math.max(0, row - 2);
                    check <= Math.min(strength.length - 1, row + 2);
                    check++) {
                if (strength[check] > strength[row]) maximum = false;
                if (strength[check] < strength[row]) prominent = true;
            }
            if (maximum && prominent) raw.add(row);
        }

        // A thick or antialiased staff line may have two equally dark center rows. Keep only its
        // strongest center before looking for the five-line periodic pattern.
        List<Integer> merged = new ArrayList<>();
        for (int row : raw) {
            if (merged.isEmpty() || row - merged.get(merged.size() - 1) > 3) {
                merged.add(row);
            } else {
                int prior = merged.get(merged.size() - 1);
                if (strength[row] > strength[prior]) merged.set(merged.size() - 1, row);
            }
        }
        return merged;
    }

    private static List<Candidate> staffCandidates(
            List<Integer> peaks, int[] strength, int minimumStrength, int height) {
        List<Candidate> result = new ArrayList<>();
        float maximumGap = Math.max(40f, height * .035f);
        for (int first = 0; first + 4 < peaks.size(); first++) {
            int top = peaks.get(first);
            for (int last = first + 4; last < peaks.size(); last++) {
                int bottom = peaks.get(last);
                float gap = (bottom - top) / 4f;
                if (gap < 2.5f) continue;
                if (gap > maximumGap) break;

                float tolerance = Math.max(2f, gap * .24f);
                int[] rows = new int[] {top, -1, -1, -1, bottom};
                float error = 0f;
                int previousIndex = first;
                boolean complete = true;
                for (int line = 1; line < 4; line++) {
                    float expected = top + line * gap;
                    int bestIndex = -1;
                    float bestDistance = Float.MAX_VALUE;
                    for (int index = previousIndex + 1; index < last; index++) {
                        int row = peaks.get(index);
                        float distance = Math.abs(row - expected);
                        if (distance > tolerance) continue;
                        if (distance < bestDistance
                                || (distance == bestDistance
                                        && bestIndex >= 0
                                        && strength[row] > strength[peaks.get(bestIndex)])) {
                            bestIndex = index;
                            bestDistance = distance;
                        }
                    }
                    if (bestIndex < 0) {
                        complete = false;
                        break;
                    }
                    rows[line] = peaks.get(bestIndex);
                    previousIndex = bestIndex;
                    // Evenly spaced printed lines need not land on integer pixels. Do not
                    // penalize their unavoidable rounding enough to discard a short staff
                    // whose five rules each pass the raw-ink threshold.
                    error += Math.max(0f, bestDistance - .5f);
                }
                if (!complete) continue;

                int totalStrength = 0;
                for (int row : rows) totalStrength += strength[row];
                float score = totalStrength - error * minimumStrength * .75f;
                if (score >= minimumStrength * 4.5f) result.add(new Candidate(rows, gap, score));
            }
        }
        return result;
    }
}
