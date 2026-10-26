// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Adapted from Music Sheets: standalone package and public API visibility.
package io.github.luckolite.interpreter;

/** Pure logical-measure mapping for PDFs that contain continuation or alternate-arrangement pages. */
public final class ScorePageTimeline {
    public record PageRange(int firstPage, int pageAfterLast) {}

    private ScorePageTimeline() {}

    /** Physical trailing pages can be excluded without changing the arrangement's final bar. */
    public static int lastEnabledPage(int[] counts, PageRange range) {
        if (counts == null || range == null) return -1;
        for (int page = Math.min(counts.length, range.pageAfterLast()) - 1;
                page >= Math.max(0, range.firstPage());
                page--) if (counts[page] > 0) return page;
        return -1;
    }

    public static java.util.List<PageRange> arrangements(int[] counts, int[] starts) {
        java.util.List<PageRange> result = new java.util.ArrayList<>();
        if (counts == null) return result;
        int first = 0;
        while (first < counts.length) {
            while (first < counts.length && counts[first] <= 0) first++;
            if (first == counts.length) break;
            int end = first + 1;
            while (end < counts.length) {
                int boundary = arrangementRestartBoundary(end, counts, starts);
                if (boundary > first) {
                    end = boundary;
                    break;
                }
                end++;
            }
            result.add(new PageRange(first, end));
            first = end;
        }
        return java.util.List.copyOf(result);
    }

    /** Explicit choice stays stable when the viewport moves; unavailable choices fall back. */
    public static PageRange selectedArrangement(int[] counts, int[] starts, int preferredPage) {
        var ranges = arrangements(counts, starts);
        for (var range : ranges)
            if (preferredPage >= range.firstPage() && preferredPage < range.pageAfterLast())
                return range;
        return ranges.isEmpty() ? new PageRange(0, 0) : ranges.get(0);
    }

    public static int[] offsets(int[] measureCounts, int[] firstMeasureNumbers) {
        if (measureCounts == null) return new int[0];
        int[] result = new int[measureCounts.length];
        int previousEnd = 0;
        for (int page = 0; page < measureCounts.length; page++) {
            int printed =
                    firstMeasureNumbers != null && page < firstMeasureNumbers.length
                            ? firstMeasureNumbers[page]
                            : 0;
            int proposed = printed > 0 ? printed - 1 : previousEnd;
            // A slightly overlapping printed number usually reflects a split/extra detected
            // bar on the preceding page, not the start of another arrangement.
            result[page] =
                    page > 0 && !restart(page, measureCounts, firstMeasureNumbers)
                            ? Math.max(previousEnd, proposed)
                            : proposed;
            previousEnd = result[page] + Math.max(0, measureCounts[page]);
        }
        return result;
    }

    public static int measureCount(int[] measureCounts, int[] firstMeasureNumbers) {
        return measureCount(
                measureCounts,
                firstMeasureNumbers,
                new PageRange(0, measureCounts == null ? 0 : measureCounts.length));
    }

    public static int measureCount(
            int[] measureCounts, int[] firstMeasureNumbers, PageRange range) {
        int[] offsets = offsets(measureCounts, firstMeasureNumbers);
        int firstPage =
                range == null ? 0 : Math.max(0, Math.min(offsets.length, range.firstPage()));
        int pageAfterLast =
                range == null
                        ? offsets.length
                        : Math.max(firstPage, Math.min(offsets.length, range.pageAfterLast()));
        int maximum = 0, played = 0;
        for (int page = firstPage; page < pageAfterLast; page++) {
            if (page > firstPage && restart(page, measureCounts, firstMeasureNumbers)) played = 0;
            played += Math.max(0, measureCounts[page]);
            maximum = Math.max(maximum, played);
        }
        return maximum;
    }

    public static int pageForMeasure(
            int logicalMeasure, int anchorPage, int[] measureCounts, int[] firstMeasureNumbers) {
        int[] offsets = offsets(measureCounts, firstMeasureNumbers);
        int best = -1, bestDistance = Integer.MAX_VALUE;
        for (int page = 0; page < offsets.length; page++) {
            int count = Math.max(0, measureCounts[page]);
            if (logicalMeasure < offsets[page] || logicalMeasure >= offsets[page] + count) continue;
            int distance = Math.abs(page - anchorPage);
            if (distance < bestDistance) {
                best = page;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static int[] playbackOffsets(int[] counts, int[] starts, PageRange range) {
        int[] offsets = offsets(counts, starts);
        int origin = range.firstPage() < offsets.length ? offsets[range.firstPage()] : 0;
        for (int p = 0; p < offsets.length; p++) offsets[p] -= origin;
        // Printed numbers locate and separate arrangements. Only actual score measures
        // occupy playback time; a numbering typo or disabled page cannot invent silence.
        int played = 0;
        for (int p = range.firstPage(); p < Math.min(counts.length, range.pageAfterLast()); p++) {
            offsets[p] = played;
            played += Math.max(0, counts[p]);
        }
        return offsets;
    }

    public static int pageForMeasure(int measure, int[] counts, int[] starts, PageRange range) {
        int[] offsets = playbackOffsets(counts, starts, range);
        for (int p = range.firstPage(); p < range.pageAfterLast(); p++)
            if (measure >= offsets[p] && measure < offsets[p] + counts[p]) return p;
        return -1;
    }

    private static boolean restart(int page, int[] counts, int[] starts) {
        if (starts == null || page >= starts.length || starts[page] <= 0 || counts[page] <= 0)
            return false;
        int previous = page - 1;
        while (previous >= 0 && counts[previous] == 0) previous--;
        if (previous < 0) return false;
        int prior = previous < starts.length ? starts[previous] : 0;
        // Require a genuine backwards reset; a one-bar overlap at a page break is not one.
        return starts[page] == 1 || (prior > 0 && starts[page] < prior);
    }

    /** Keep the first enabled part. More piano notes later must not silently replace it. */
    public static PageRange primaryArrangement(
            int[] measureCounts, int[] firstMeasureNumbers, int[] noteCounts, int[] staffBreadths) {
        var ranges = arrangements(measureCounts, firstMeasureNumbers);
        return ranges.isEmpty() ? new PageRange(0, 0) : ranges.get(0);
    }

    /** A following printed number can place an unnumbered page at a part restart. */
    private static int arrangementRestartBoundary(int page, int[] counts, int[] starts) {
        if (restart(page, counts, starts)) return page;
        if (starts == null
                || page <= 1
                || page >= starts.length
                || page >= counts.length
                || starts[page] <= 1
                || counts[page - 1] <= 0
                || starts[page - 1] > 0) return -1;
        int prior = page - 2;
        while (prior >= 0 && counts[prior] <= 0) prior--;
        if (prior < 0
                || prior >= starts.length
                || starts[prior] <= 0
                || starts[page] >= starts[prior]) return -1;
        // Allow a few extra detected bar slices on the unknown page, but do not
        // split an ordinary unnumbered continuation with a later large number.
        return starts[page] <= counts[page - 1] + 5 ? page - 1 : -1;
    }
}
