// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Uses printed measure-number anchors to correct missed staffs and bad barline guesses. */
final class MeasureNumberReconciler {
    private MeasureNumberReconciler() {}

    record NumberToken(
            int value, float left, float top, float right, float bottom, float annotationLeft) {
        NumberToken(int value, float left, float top, float right, float bottom) {
            this(value, left, top, right, bottom, left);
        }
    }

    static List<MeasureRegion> reconcile(List<MeasureRegion> detected, List<NumberToken> tokens) {
        return reconcile(detected, tokens, List.of());
    }

    static List<MeasureRegion> reconcile(
            List<MeasureRegion> detected,
            List<NumberToken> tokens,
            List<NumberToken> multiMeasureRests,
            byte[] labels,
            int width,
            int height) {
        var fitted = reconcile(detected, tokens, multiMeasureRests);
        var rawRows = rows(detected);
        var fittedRows = rows(fitted);
        if (rawRows.isEmpty()
                || fittedRows.isEmpty()
                || labels == null
                || labels.length != width * height) return fitted;
        Row raw = rawRows.get(rawRows.size() - 1), last = fittedRows.get(fittedRows.size() - 1);
        if (raw.measures.size() != 1
                || last.measures.size() <= 1
                || Math.abs(raw.top - last.top) > .01f
                || !tokensInside(raw, multiMeasureRests).isEmpty()) return fitted;
        // A lone held-note coda with empty space to the closing bar is direct evidence
        // of one measure. Do not subdivide it using the density of earlier systems.
        int left = Math.max(0, Math.round(raw.left * width)),
                right = Math.min(width - 1, Math.round(raw.right * width));
        int top = Math.max(0, Math.round(raw.top * height)),
                bottom = Math.min(height - 1, Math.round(raw.bottom * height));
        int groups = 0, lastHead = -9999, firstHead = -1, finalHead = -1;
        for (int x = left; x <= right; x++) {
            int heads = 0;
            for (int y = top; y <= bottom; y++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) heads++;
            if (heads < 2) continue;
            if (x - lastHead > Math.max(5, width * .008f)) groups++;
            if (firstHead < 0) firstHead = x;
            finalHead = x;
            lastHead = x;
        }
        if (groups != 1
                || firstHead < left + (right - left) * .025f
                || finalHead > left + (right - left) * .25f) return fitted;
        List<MeasureRegion> result =
                new ArrayList<>(fitted.subList(0, fitted.size() - last.measures.size()));
        result.addAll(raw.measures);
        return List.copyOf(result);
    }

    static List<MeasureRegion> reconcile(
            List<MeasureRegion> detected,
            List<NumberToken> tokens,
            List<NumberToken> multiMeasureRests) {
        List<MeasureRegion> safeDetected = detected == null ? List.of() : detected;
        if (tokens == null || tokens.isEmpty())
            return stabilizeAndExpandUnanchored(safeDetected, multiMeasureRests);
        List<Row> anchorLayout = rows(safeDetected);
        for (Row row : anchorLayout) {
            for (NumberToken rest : assignedRestTokens(row, anchorLayout, multiMeasureRests))
                row.restExtras += rest.value - 1;
            List<MeasureRegion> expanded =
                    expandMultiMeasureRests(
                            row.measures, assignedRestTokens(row, anchorLayout, multiMeasureRests));
            row.measures.clear();
            row.measures.addAll(expanded);
        }
        // Printed-number jumps include the silent bars represented by a confirmed multi-rest.
        List<NumberToken> anchors = trustedAnchors(tokens, anchorLayout);
        if (anchors.size() < 2)
            return stabilizeAndExpandUnanchored(safeDetected, multiMeasureRests);
        // Even if semantic segmentation missed every staff, a trusted run of printed system
        // numbers can still supply approximate rows. augmentedRows intentionally supports this;
        // returning early for an empty detection list made that recovery path unreachable.
        List<Row> rows = augmentedRows(safeDetected, anchors);
        assignAnchors(rows, anchors);
        for (Row row : rows)
            for (NumberToken rest : assignedRestTokens(row, rows, multiMeasureRests))
                row.restExtras += rest.value - 1;
        interpolateMissingAnchors(rows);
        int typicalExpected = typicalExpectedCount(rows);

        List<MeasureRegion> result = new ArrayList<>();
        int previousExpected = 0;
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            int expected = expectedUntilNextRow(rows, index);
            boolean expectedFromFollowingAnchor = expected > 0;
            boolean expectedFromTypicalFinalRow = false;
            // Consecutive printed numbers establish one-measure systems directly. Preserve that
            // run on the final numbered row instead of replacing it with the page-wide median.
            if (expected == 0
                    && index + 1 == rows.size()
                    && row.anchor != null
                    && row.measures.size() == 1
                    && previousExpected == 1) expected = 1;
            // A numbered final system has no following anchor to reveal its count. When a long
            // page supplies several earlier differences and the detector returned one full-width
            // box, use their median instead of treating the entire last system as one measure.
            if (expected == 0
                    && index + 1 == rows.size()
                    && row.anchor != null
                    && row.measures.size() <= 1
                    && typicalExpected > 1
                    && knownExpectedCount(rows) >= 4
                    && matchesEarlierSystemSpan(rows, index)) {
                expected = typicalExpected;
                expectedFromTypicalFinalRow = true;
            }
            // Once consecutive printed numbers establish the page's system count, carry it
            // through a full-width trailing numbered/final system. Previously a noisy final
            // row kept 13-14 stem boxes merely because there was no later number anchor.
            boolean extremelyNoisyRow =
                    previousExpected > 0
                            && (row.measures.isEmpty()
                                    || row.measures.size() >= previousExpected * 2);
            if (expected == 0
                    && previousExpected > 0
                    && extremelyNoisyRow
                    && (row.anchor != null || index + 1 == rows.size())
                    && (row.measures.isEmpty() || matchesEarlierSystemSpan(rows, index)))
                expected = previousExpected;
            if (expected > 0) previousExpected = expected;
            List<NumberToken> rowRests = tokensInside(row, multiMeasureRests);
            // A following printed measure number already includes the logical measures hidden
            // inside a multi-rest. A final-row count inferred from visual system density does
            // not, so add those hidden measures before fitting. If the rest is centred beyond
            // the final ordinary slot, it is an additional engraved box after the usual written
            // measures rather than a replacement for the last one (Artemis 68-74).
            if (!expectedFromFollowingAnchor && expected > 0) {
                if (expectedFromTypicalFinalRow
                        && trailingRestAddsVisualSlot(row, rowRests, typicalExpected)) expected++;
                for (NumberToken rest : rowRests) expected += rest.value - 1;
            }
            result.addAll(
                    expected > 0
                            ? fitRow(row, expected, rowRests)
                            : expandMultiMeasureRests(row.measures, rowRests));
        }
        // A trusted printed-number difference is stronger than the page's dominant row count.
        // In Legendary Guardian, 72 -> 77 proves that the narrow whole-note system has five bars;
        // running dominant-count cleanup afterward collapsed it back to the common three-bar row.
        return List.copyOf(result);
    }

    private static boolean trailingRestAddsVisualSlot(
            Row row, List<NumberToken> rests, int typicalVisualCount) {
        if (rests.size() != 1 || typicalVisualCount < 2 || row.right <= row.left) return false;
        NumberToken rest = rests.get(0);
        float center = ((rest.left + rest.right) * .5f - row.left) / (row.right - row.left);
        // The centre of the ordinary final slot is 1 - 0.5/N. Halfway from there to the
        // system edge cleanly separates a compact appended rest from a rest occupying that slot.
        return center > 1f - .25f / typicalVisualCount;
    }

    private static int knownExpectedCount(List<Row> rows) {
        int count = 0;
        for (int index = 0; index < rows.size(); index++)
            if (expectedUntilNextRow(rows, index) > 0) count++;
        return count;
    }

    private static int typicalExpectedCount(List<Row> rows) {
        List<Integer> values = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            int expected = expectedUntilNextRow(rows, index);
            if (expected > 0) values.add(expected);
        }
        if (values.isEmpty()) return 0;
        values.sort(Integer::compare);
        return values.get(values.size() / 2);
    }

    /**
     * Returns the printed number of the page's first measure. This lets a PDF contain alternate
     * arrangements that restart at measure 1 without appending another full song to playback.
     * A later trusted row can establish the start even when measure 1 itself is not printed.
     */
    static int firstMeasureNumber(List<MeasureRegion> reconciled, List<NumberToken> tokens) {
        List<Row> layout = rows(reconciled == null ? List.of() : reconciled);
        List<NumberToken> anchors = trustedAnchors(tokens == null ? List.of() : tokens, layout);
        if (anchors.size() < 2)
            return singleAnchorProvingMeasureOne(layout, tokens == null ? List.of() : tokens);
        assignAnchors(layout, anchors);
        // Several independent system starts can reject one plausible-looking leading glyph.
        // Derive every candidate start from actual reconciled preceding bars, not OCR magnitude.
        java.util.Map<Integer, Integer> votes = new java.util.HashMap<>();
        int preceding = 0;
        for (Row row : layout) {
            if (row.anchor != null) {
                int candidate = row.anchor.value - preceding;
                if (candidate >= 1 && candidate <= row.anchor.value)
                    votes.merge(candidate, 1, Integer::sum);
            }
            preceding += row.measures.size();
        }
        int supported = 0, support = 1;
        boolean tied = false;
        for (var vote : votes.entrySet()) {
            if (vote.getValue() > support) {
                supported = vote.getKey();
                support = vote.getValue();
                tied = false;
            } else if (vote.getValue() == support) tied = true;
        }
        if (support >= 2 && !tied) return supported;
        int precedingMeasures = 0;
        for (Row row : layout) {
            if (row.anchor != null) {
                int first = row.anchor.value - precedingMeasures;
                return first >= 1 && first <= row.anchor.value ? first : 0;
            }
            precedingMeasures += row.measures.size();
        }
        return 0;
    }

    /**
     * A lone later system number cannot establish an arbitrary continuation page, but it can
     * prove a restart at measure one when its value exactly equals the number of preceding
     * reconciled measures plus one. This is common when a PDF contains both the solo part and a
     * later full-score arrangement, as in Sands of Destiny.
     */
    private static int singleAnchorProvingMeasureOne(List<Row> layout, List<NumberToken> tokens) {
        if (layout.isEmpty() || tokens.isEmpty()) return 0;
        for (NumberToken token : tokens) {
            float centerX = (token.left + token.right) * .5f;
            if (token.value < 1
                    || token.value > 999
                    || centerX > .22f
                    || !leftOfMatchingStaff(token, layout)) continue;
            float centerY = (token.top + token.bottom) * .5f;
            Row closest = null;
            float distance = Float.MAX_VALUE;
            for (Row row : layout) {
                float candidate = Math.abs(centerY - row.top);
                if (candidate < distance) {
                    distance = candidate;
                    closest = row;
                }
            }
            if (closest == null
                    || distance > Math.max(.045f, (closest.bottom - closest.top) * .85f)) continue;
            int preceding = 0;
            for (Row row : layout) {
                if (row == closest) break;
                preceding += row.measures.size();
            }
            if (token.value == preceding + 1) return 1;
        }
        return 0;
    }

    /** Multi-measure rests are independent optical evidence and remain meaningful even when the
     * page does not print enough system numbers to form a trusted sequence. */
    private static List<MeasureRegion> stabilizeAndExpandUnanchored(
            List<MeasureRegion> detected, List<NumberToken> multiMeasureRests) {
        List<MeasureRegion> stabilized = stabilizeSystemCounts(detected, multiMeasureRests);
        if (multiMeasureRests == null || multiMeasureRests.isEmpty()) return stabilized;
        List<MeasureRegion> result = new ArrayList<>();
        for (Row row : rows(stabilized))
            result.addAll(
                    expandMultiMeasureRests(row.measures, tokensInside(row, multiMeasureRests)));
        return List.copyOf(result);
    }

    /**
     * Printed measure numbers are uncommon on many single-instrument arrangements. In those
     * scores, a short run of beamed note stems can look like several barlines while a faint
     * real barline can disappear. Use the dominant full-width system count only when a row's
     * geometry independently proves that it contains a tiny false measure or one implausibly
     * wide merged measure. Short coda/final systems remain untouched.
     */
    private static List<MeasureRegion> stabilizeSystemCounts(
            List<MeasureRegion> source, List<NumberToken> multiMeasureRests) {
        List<Row> layout = rows(source);
        if (layout.size() < 5) return List.copyOf(source);

        int[] frequencies = new int[13];
        for (Row row : layout)
            if (row.measures.size() >= 2 && row.measures.size() < frequencies.length)
                frequencies[row.measures.size()]++;
        int dominant = 0;
        for (int count = 2; count < frequencies.length; count++)
            if (frequencies[count] > frequencies[dominant]) dominant = count;
        if (dominant < 2 || frequencies[dominant] < 3 || frequencies[dominant] * 2 < layout.size())
            return List.copyOf(source);

        List<Float> spans = new ArrayList<>();
        List<Float> widths = new ArrayList<>();
        for (Row row : layout)
            if (row.measures.size() == dominant) {
                float span = row.right - row.left;
                if (span <= 0f) continue;
                spans.add(span);
                widths.add(span / dominant);
            }
        float referenceSpan = median(spans, 0f);
        float referenceWidth = median(widths, 0f);
        if (referenceSpan <= 0f || referenceWidth <= 0f) return List.copyOf(source);

        List<MeasureRegion> result = new ArrayList<>();
        for (Row row : layout) {
            // A condensed rest can be much narrower than a written bar. Preserve its
            // boundary before expansion, or nearby sounding notes become part of the rest.
            if (multiMeasureRests != null && !tokensInside(row, multiMeasureRests).isEmpty()) {
                result.addAll(row.measures);
                continue;
            }
            int count = row.measures.size();
            float span = row.right - row.left;
            if (count == dominant || span < referenceSpan * .78f) {
                result.addAll(row.measures);
                continue;
            }
            float narrowest = Float.MAX_VALUE, widest = 0f;
            for (MeasureRegion measure : row.measures) {
                float width = measure.right() - measure.left();
                narrowest = Math.min(narrowest, width);
                widest = Math.max(widest, width);
            }
            // One false stem boundary can safely be repaired from the dominant row count. A row
            // with five extra candidates may instead contain genuinely shorter measures (as in
            // Transcendence 15-20), so never collapse it wholesale without printed anchors.
            boolean falseExtra = count == dominant + 1 && narrowest < referenceWidth * .46f;
            boolean missedBoundary = count + 1 == dominant && widest > referenceWidth * 1.65f;
            result.addAll(
                    falseExtra || missedBoundary ? fitCount(row.measures, dominant) : row.measures);
        }
        return List.copyOf(result);
    }

    private static boolean matchesEarlierSystemSpan(List<Row> rows, int index) {
        if (index <= 0 || index >= rows.size()) return false;
        List<Float> spans = new ArrayList<>();
        for (int before = 0; before < index; before++) {
            Row row = rows.get(before);
            if (!row.measures.isEmpty() && row.right > row.left) spans.add(row.right - row.left);
        }
        float reference = median(spans, 0f);
        Row row = rows.get(index);
        return reference > 0f && row.right - row.left >= reference * .78f;
    }

    static String describe(List<MeasureRegion> detected, List<NumberToken> tokens) {
        List<NumberToken> anchors = trustedAnchors(tokens, rows(detected));
        List<Row> rows = augmentedRows(detected, anchors);
        assignAnchors(rows, anchors);
        interpolateMissingAnchors(rows);
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (result.length() > 1) result.append(',');
            result.append(Math.round(row.top * 100))
                    .append(':')
                    .append(row.measures.size())
                    .append('=')
                    .append(row.anchor == null ? "-" : row.anchor.value)
                    .append('/')
                    .append(expectedUntilNextRow(rows, index));
        }
        return result.append(']').toString();
    }

    private static List<Row> rows(List<MeasureRegion> measures) {
        List<MeasureRegion> sorted = new ArrayList<>(measures);
        sorted.sort(Comparator.comparing(MeasureRegion::top).thenComparing(MeasureRegion::left));
        List<Row> rows = new ArrayList<>();
        for (MeasureRegion measure : sorted) {
            Row row = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            float tolerance = Math.max(0.012f, (measure.bottom() - measure.top()) * 0.22f);
            if (row == null
                    || Math.abs(row.top - measure.top()) > tolerance
                            && !curvedRowNeighbor(row.measures, measure)) {
                row = new Row(measure.top(), measure.bottom());
                rows.add(row);
            }
            row.measures.add(measure);
            row.left = Math.min(row.left, measure.left());
            row.right = Math.max(row.right, measure.right());
            row.bottom = Math.max(row.bottom, measure.bottom());
        }
        for (Row row : rows) row.measures.sort(Comparator.comparing(MeasureRegion::left));
        return rows;
    }

    /** Adjacent measure boxes may climb beyond the first box's fixed top tolerance. */
    private static boolean curvedRowNeighbor(List<MeasureRegion> row, MeasureRegion measure) {
        for (MeasureRegion other : row) {
            float separation =
                    Math.min(
                            Math.abs(other.left() - measure.right()),
                            Math.abs(measure.left() - other.right()));
            float overlap =
                    Math.min(other.bottom(), measure.bottom())
                            - Math.max(other.top(), measure.top());
            float shorter =
                    Math.min(other.bottom() - other.top(), measure.bottom() - measure.top());
            if (separation <= .02f
                    && Math.min(other.right(), measure.right())
                                    - Math.max(other.left(), measure.left())
                            <= .002f
                    && shorter > 0
                    && overlap >= shorter * .65f) return true;
        }
        return false;
    }

    /** Finds the longest increasing left-margin number sequence, excluding page/BPM/time text. */
    private static List<NumberToken> trustedAnchors(List<NumberToken> tokens, List<Row> layout) {
        List<NumberToken> candidates = new ArrayList<>();
        for (NumberToken token : tokens) {
            float centerX = (token.left + token.right) / 2f;
            if (token.value >= 1
                    && token.value <= 999
                    && centerX <= 0.22f
                    && leftOfMatchingStaff(token, layout)) candidates.add(token);
        }
        // A folio above the first detected system can precede the real measure number
        // in a perfectly increasing OCR sequence. It must not invent an extra system.
        if (!layout.isEmpty()) {
            Row first = layout.get(0);
            List<NumberToken> headers = List.copyOf(candidates);
            candidates.removeIf(
                    token ->
                            token.top < .065f
                                    && token.bottom < first.top - .025f
                                    && headers.stream()
                                            .anyMatch(
                                                    header ->
                                                            header != token
                                                                    && header.value > token.value
                                                                    && headerRow(header, layout)
                                                                            == 0));
        }
        candidates.sort(Comparator.comparing(NumberToken::top));
        if (candidates.isEmpty()) return List.of();
        int[] length = new int[candidates.size()];
        float[] error = new float[candidates.size()];
        int[] previous = new int[candidates.size()];
        int best = 0;
        for (int index = 0; index < candidates.size(); index++) {
            length[index] = 1;
            error[index] = 0;
            previous[index] = -1;
            for (int before = 0; before < index; before++) {
                int difference = candidates.get(index).value - candidates.get(before).value;
                float vertical = candidates.get(index).top - candidates.get(before).top;
                // Permit one missed printed anchor, but do not let a stray lyric/fingering digit
                // bridge an implausible number of visually segmented measures. This was the
                // source of the 9 -> 20 false sequence in Humoresque: it beat the real sequence
                // by length and expanded one three-measure system into eleven measures.
                float maximumVertical = Math.max(.16f, Math.min(.24f, medianStep(layout) * 2.35f));
                if (difference < 1
                        || difference > 16
                        || vertical < 0.035f
                        || (vertical > maximumVertical
                                && !adjacentDetectedRowsAgree(
                                        candidates.get(before),
                                        candidates.get(index),
                                        difference,
                                        layout))
                        || !plausiblePrintedDifference(
                                candidates.get(before), candidates.get(index), difference, layout))
                    continue;
                int candidateLength = length[before] + 1;
                float candidateError =
                        error[before]
                                + transitionCountError(
                                        candidates.get(before),
                                        candidates.get(index),
                                        difference,
                                        layout);
                if (candidateLength > length[index]
                        || (candidateLength == length[index] && candidateError < error[index])) {
                    length[index] = candidateLength;
                    error[index] = candidateError;
                    previous[index] = before;
                }
            }
            if (length[index] > length[best]
                    || (length[index] == length[best] && error[index] < error[best])) best = index;
        }
        if (length[best] < 2) return List.of();
        List<NumberToken> result = new ArrayList<>();
        for (int at = best; ; at = previous[at]) {
            result.add(candidates.get(at));
            if (previous[at] < 0) break;
        }
        java.util.Collections.reverse(result);
        return List.copyOf(result);
    }

    private static boolean adjacentDetectedRowsAgree(
            NumberToken from, NumberToken to, int difference, List<Row> layout) {
        // Grand-staff systems can be farther apart than a quarter page. Their printed
        // numbers still anchor consecutive rows when the visible bars confirm the jump.
        int first = headerRow(from, layout), next = headerRow(to, layout);
        return first >= 0 && next == first + 1 && layout.get(first).measures.size() == difference;
    }

    private static int headerRow(NumberToken token, List<Row> layout) {
        if (layout == null) return -1;
        float centerY = (token.top + token.bottom) * .5f;
        for (int index = 0; index < layout.size(); index++) {
            Row row = layout.get(index);
            if (centerY >= row.top - .035f
                    && centerY <= row.top + .012f
                    && (token.left + token.right) * .5f <= row.left + .012f) return index;
        }
        return -1;
    }

    private static boolean plausiblePrintedDifference(
            NumberToken from, NumberToken to, int printedDifference, List<Row> layout) {
        if (layout == null || layout.isEmpty()) return true;
        float fromY = (from.top + from.bottom) * .5f;
        float toY = (to.top + to.bottom) * .5f;
        int visual = 0;
        int restExtras = 0;
        boolean unsplitFullSystem = false;
        for (Row row : layout) {
            float center = (row.top + row.bottom) * .5f;
            if (center < fromY - .025f || center >= toY - .012f) continue;
            visual += row.measures.size();
            restExtras += row.restExtras;
            if (row.measures.size() <= 2 && row.right - row.left >= .45f) unsplitFullSystem = true;
        }
        if (visual <= 0 || unsplitFullSystem) return true;
        if (restExtras > 0) return printedDifference == visual;
        // Missing or false semantic barlines can roughly halve/double a count. Beyond that, the
        // OCR transition is weaker evidence than the page geometry and must not become an anchor.
        return printedDifference <= visual * 2 + 2 && printedDifference * 2 + 2 >= visual;
    }

    /** Uses detected row count only to break equal-length OCR sequences. Printed anchors remain
     * authoritative when they form a longer run, even if semantic barlines are badly damaged. */
    private static float transitionCountError(
            NumberToken from, NumberToken to, int printedDifference, List<Row> layout) {
        if (layout == null || layout.isEmpty()) return 0;
        int first = headerRow(from, layout), next = headerRow(to, layout);
        if (first < 0 || next <= first) return 0;
        int visual = 0;
        for (int index = first; index < next; index++) visual += layout.get(index).measures.size();
        return Math.abs(printedDifference - visual);
    }

    /** A system number is printed outside the staff. Tempo values, time signatures, tuplets, and
     * multi-measure-rest counts are inside it and must not become numbering anchors. */
    private static boolean leftOfMatchingStaff(NumberToken token, List<Row> layout) {
        if (layout == null || layout.isEmpty()) return (token.left + token.right) * .5f <= .11f;
        float tokenY = (token.top + token.bottom) * .5f;
        // A connected system has ONE measure-number anchor above its first staff. Part labels
        // such as "Violin 2" and "Violin 3" sit left of later staves inside the same tall box.
        // Treating those digits as system starts fabricates extra rows and shifts every bar.
        for (Row row : layout)
            if (tokenY > row.top + Math.min(.025f, (row.bottom - row.top) * .25f)
                    && tokenY < row.bottom) return false;
        Row closest = null;
        float best = Float.MAX_VALUE;
        for (Row row : layout) {
            float distance = Math.abs(tokenY - row.top);
            if (distance < best) {
                best = distance;
                closest = row;
            }
        }
        float centerX = (token.left + token.right) * .5f;
        if (closest == null || best > Math.max(.045f, (closest.bottom - closest.top) * .85f))
            // A strong left-margin sequence is also the evidence used to restore a staff row the
            // segmenter missed. Requiring an already detected row here made that recovery path
            // circular. The sequence constraints still reject page numbers and isolated OCR.
            return centerX <= .11f;
        return centerX <= closest.left + .012f;
    }

    private static List<Row> augmentedRows(
            List<MeasureRegion> detected, List<NumberToken> anchors) {
        List<Row> rows = rows(detected);
        float medianHeight = medianHeight(rows);
        List<Float> lefts = new ArrayList<>(), rights = new ArrayList<>();
        for (Row row : rows) {
            lefts.add(row.left);
            rights.add(row.right);
        }
        float medianLeft = median(lefts, 0.1f);
        float medianRight = median(rights, 0.9f);
        float rowStep = medianStep(rows);
        float matchDistance = Math.max(0.026f, rowStep * 0.34f);
        for (NumberToken anchor : anchors) {
            Row closest = null;
            float distance = matchDistance;
            for (Row row : rows) {
                float next = Math.abs(row.top - anchor.top);
                if (next < distance) {
                    closest = row;
                    distance = next;
                }
            }
            if (closest == null) {
                Row synthetic = new Row(anchor.top, Math.min(1f, anchor.top + medianHeight));
                synthetic.left = medianLeft;
                synthetic.right = medianRight;
                rows.add(synthetic);
            }
        }
        rows.sort(Comparator.comparing(row -> row.top));
        return rows;
    }

    private static void assignAnchors(List<Row> rows, List<NumberToken> anchors) {
        float rowStep = medianStep(rows);
        float matchDistance = Math.max(0.03f, rowStep * 0.4f);
        for (NumberToken anchor : anchors) {
            Row closest = null;
            float distance = matchDistance;
            for (Row row : rows) {
                if (row.anchor != null) continue;
                float next = Math.abs(row.top - anchor.top);
                if (next < distance) {
                    closest = row;
                    distance = next;
                }
            }
            if (closest != null) closest.anchor = anchor;
        }
    }

    private static void inferLeadingAnchors(List<Row> rows) {
        int first = -1, second = -1;
        for (int index = 0; index < rows.size(); index++)
            if (rows.get(index).anchor != null) {
                if (first < 0) first = index;
                else {
                    second = index;
                    break;
                }
            }
        if (first <= 0 || second < 0) return;
        int rowDistance = second - first;
        int valueDistance = rows.get(second).anchor.value - rows.get(first).anchor.value;
        if (valueDistance < 2
                || valueDistance > 12 * rowDistance
                || valueDistance % rowDistance != 0) return;
        int perRow = valueDistance / rowDistance;
        for (int index = first - 1; index >= 0; index--) {
            int value = rows.get(first).anchor.value - perRow * (first - index);
            if (value < 1) break;
            Row row = rows.get(index);
            row.anchor = new NumberToken(value, row.left, row.top, row.left, row.top);
        }
    }

    /**
     * A tiny printed system number is commonly missed even when the numbers above and below it
     * are trustworthy. Their difference constrains the total number of measures across every
     * intervening system. Distribute that total with a small dynamic fit to the detected counts,
     * then add synthetic anchors so each row can be corrected independently. Without this step,
     * the two real anchors were trusted but neither row was reconciled because they were not
     * adjacent in the row list.
     */
    private static void interpolateMissingAnchors(List<Row> rows) {
        if (rows == null || rows.size() < 3) return;
        int previousAnchor = -1;
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).anchor == null) continue;
            if (previousAnchor >= 0 && index - previousAnchor > 1) {
                Row start = rows.get(previousAnchor), end = rows.get(index);
                int total = end.anchor.value - start.anchor.value;
                int systems = index - previousAnchor;
                int[] counts = fittedSystemCounts(rows, previousAnchor, systems, total);
                if (counts != null) {
                    int value = start.anchor.value;
                    for (int offset = 1; offset < systems; offset++) {
                        value += counts[offset - 1];
                        Row row = rows.get(previousAnchor + offset);
                        row.anchor = new NumberToken(value, row.left, row.top, row.left, row.top);
                    }
                }
            }
            previousAnchor = index;
        }
    }

    private static int[] fittedSystemCounts(List<Row> rows, int start, int systems, int total) {
        if (systems < 2 || total < systems || total > systems * 16) return null;
        int[] logical = new int[systems];
        int logicalTotal = 0, restExtras = 0;
        for (int i = 0; i < systems; i++) {
            Row row = rows.get(start + i);
            logical[i] = row.measures.size() + row.restExtras;
            logicalTotal += logical[i];
            restExtras += row.restExtras;
        }
        // Exact barlines plus a verified rest explain the anchor gap without redistributing
        // its silence across the adjacent written bars (even if the rest row's number is missed).
        if (restExtras > 0 && logicalTotal == total) return logical;
        float average = total / (float) systems;
        float[][] costs = new float[systems + 1][total + 1];
        int[][] previous = new int[systems + 1][total + 1];
        for (float[] row : costs) java.util.Arrays.fill(row, Float.POSITIVE_INFINITY);
        for (int[] row : previous) java.util.Arrays.fill(row, -1);
        costs[0][0] = 0;
        for (int system = 0; system < systems; system++) {
            int detected =
                    Math.max(
                            1,
                            Math.min(
                                    16,
                                    rows.get(start + system).measures.size()
                                            + rows.get(start + system).restExtras));
            for (int used = 0; used <= total; used++) {
                if (!Float.isFinite(costs[system][used])) continue;
                for (int count = 1; count <= 16 && used + count <= total; count++) {
                    float detectedError = count - detected;
                    float averageError = count - average;
                    float next =
                            costs[system][used]
                                    + detectedError * detectedError
                                    + averageError * averageError * .35f;
                    if (next < costs[system + 1][used + count]) {
                        costs[system + 1][used + count] = next;
                        previous[system + 1][used + count] = count;
                    }
                }
            }
        }
        if (!Float.isFinite(costs[systems][total])) return null;
        int[] result = new int[systems];
        int used = total;
        for (int system = systems; system > 0; system--) {
            int count = previous[system][used];
            if (count <= 0) return null;
            result[system - 1] = count;
            used -= count;
        }
        return result;
    }

    private static int expectedUntilNextRow(List<Row> rows, int index) {
        Row row = rows.get(index);
        if (row.anchor == null || index + 1 >= rows.size() || rows.get(index + 1).anchor == null)
            return 0;
        int difference = rows.get(index + 1).anchor.value - row.anchor.value;
        return difference >= 1 && difference <= 16 ? difference : 0;
    }

    private static List<MeasureRegion> fitRow(
            Row row, int expected, List<NumberToken> multiMeasureRests) {
        int representedExtras = 0;
        for (NumberToken token : multiMeasureRests) representedExtras += token.value - 1;
        int visualExpected = expected - representedExtras;
        if (visualExpected <= 0) {
            multiMeasureRests = List.of();
            visualExpected = expected;
        }
        List<MeasureRegion> fitted;
        if (!row.measures.isEmpty()) fitted = fitCount(row.measures, visualExpected);
        else {
            if (row.right - row.left < 0.1f) return List.of();
            fitted =
                    fitCount(
                            List.of(new MeasureRegion(row.left, row.right, row.top, row.bottom)),
                            visualExpected);
        }
        List<MeasureRegion> expanded = expandMultiMeasureRests(fitted, multiMeasureRests);
        // A rest token must map back into exactly one fitted visual measure. If it cannot, keep
        // the ordinary correction path rather than returning a malformed logical system.
        return expanded.size() == expected ? expanded : fitCount(row.measures, expected);
    }

    private static List<NumberToken> tokensInside(Row row, List<NumberToken> tokens) {
        if (tokens == null || tokens.isEmpty()) return List.of();
        List<NumberToken> result = new ArrayList<>();
        for (NumberToken token : tokens) {
            float x = (token.left + token.right) * .5f;
            float y = (token.top + token.bottom) * .5f;
            if (x >= row.left
                    && x <= row.right
                    && y >= row.top - (row.bottom - row.top) * .95f
                    && y <= row.bottom) result.add(token);
        }
        return List.copyOf(result);
    }

    private static List<NumberToken> assignedRestTokens(
            Row row, List<Row> layout, List<NumberToken> tokens) {
        List<NumberToken> result = new ArrayList<>();
        for (NumberToken token : tokensInside(row, tokens)) {
            float y = (token.top + token.bottom) * .5f;
            Row closest = row;
            for (Row candidate : layout) {
                if (Math.abs(y - candidate.top) < Math.abs(y - closest.top)
                        && tokensInside(candidate, List.of(token)).size() == 1) closest = candidate;
            }
            if (closest == row) result.add(token);
        }
        return result;
    }

    /** A multi-measure rest is one printed span but several logical measures. Repeating the same
     * geometry keeps playback on that rest without inventing a barline in adjacent written music. */
    private static List<MeasureRegion> expandMultiMeasureRests(
            List<MeasureRegion> source, List<NumberToken> tokens) {
        if (source == null || source.isEmpty() || tokens == null || tokens.isEmpty())
            return source == null ? List.of() : List.copyOf(source);
        List<MeasureRegion> result = new ArrayList<>();
        for (MeasureRegion region : source) {
            NumberToken rest = null;
            for (NumberToken token : tokens) {
                float x = (token.left + token.right) * .5f;
                if (x >= region.left() && x <= region.right()) {
                    if (rest != null) return List.copyOf(source);
                    rest = token;
                }
            }
            int copies = rest == null ? 1 : rest.value;
            for (int copy = 0; copy < copies; copy++) result.add(region);
        }
        return List.copyOf(result);
    }

    private static float medianHeight(List<Row> rows) {
        List<Float> heights = new ArrayList<>();
        for (Row row : rows) heights.add(row.bottom - row.top);
        return median(heights, 0.065f);
    }

    private static float medianStep(List<Row> rows) {
        List<Float> differences = new ArrayList<>();
        for (int index = 0; index + 1 < rows.size(); index++) {
            float difference = rows.get(index + 1).top - rows.get(index).top;
            if (difference > 0.035f && difference < 0.2f) differences.add(difference);
        }
        return median(differences, 0.1f);
    }

    private static float median(List<Float> source, float fallback) {
        if (source == null || source.isEmpty()) return fallback;
        List<Float> values = new ArrayList<>(source);
        values.sort(Float::compare);
        return values.get(values.size() / 2);
    }

    private static List<MeasureRegion> fitCount(List<MeasureRegion> source, int expected) {
        if (source.isEmpty() || expected <= 0) return List.copyOf(source);
        List<MeasureRegion> result = new ArrayList<>(source);
        result.sort(Comparator.comparing(MeasureRegion::left));
        float left = result.get(0).left(), right = result.get(result.size() - 1).right();
        float targetWidth = (right - left) / expected;
        if (targetWidth <= 0.004f) return List.copyOf(source);

        // With no interior barline evidence at all, repeated "split the widest" operations are
        // order-dependent: a three-measure system becomes two quarter-width boxes followed by
        // one half-width box. Printed 27 -> 30 in Transcendence exposes exactly that case. There
        // is no detected boundary worth preserving, so divide the sole full-system region in one
        // operation and keep every inferred measure the same width.
        if (result.size() == 1 && expected > 1) {
            MeasureRegion region = result.get(0);
            float gap = Math.min(.004f, targetWidth * .04f);
            List<MeasureRegion> evenlySplit = new ArrayList<>(expected);
            for (int index = 0; index < expected; index++) {
                float start = left + (right - left) * index / expected;
                float end = left + (right - left) * (index + 1f) / expected;
                if (index > 0) start += gap / 2f;
                if (index + 1 < expected) end -= gap / 2f;
                evenlySplit.add(new MeasureRegion(start, end, region.top(), region.bottom()));
            }
            return List.copyOf(evenlySplit);
        }

        // If nearly every note stem became a candidate separator, repeatedly merging the locally
        // best pair can strand the last real measure as a tiny box. In that specific high-noise
        // case, choose the expected number of already-detected boundaries together so all groups
        // remain viable. This never invents an equal-width grid and does not run on ordinary
        // unequal engraving where there are only one or two extra candidates.
        if (expected >= 2 && result.size() >= expected * 2)
            return fitHighlyFragmentedRow(result, expected, left, right);

        // A pair of close beamed stems can create a very narrow fake measure while a faint real
        // barline elsewhere is missed. Those two errors cancel numerically, so source.size() can
        // already equal the printed count and the old early-return preserved the tiny red strip.
        // Repair only extreme interior fragments; genuine pickup/final bars and compact whole-note
        // measures remain far wider than this threshold.
        for (; ; ) {
            int tiny = -1;
            for (int index = 1; index + 1 < result.size(); index++)
                if (regionWidth(result.get(index)) < targetWidth * .32f) {
                    tiny = index;
                    break;
                }
            if (tiny < 0) break;
            int before = tiny - 1, after = tiny + 1;
            float beforeScore =
                    Math.abs(result.get(tiny).right() - result.get(before).left() - targetWidth);
            float afterScore =
                    Math.abs(result.get(after).right() - result.get(tiny).left() - targetWidth);
            int mergeAt = beforeScore <= afterScore ? before : tiny;
            MeasureRegion first = result.get(mergeAt), second = result.get(mergeAt + 1);
            result.set(
                    mergeAt,
                    new MeasureRegion(
                            first.left(),
                            second.right(),
                            Math.min(first.top(), second.top()),
                            Math.max(first.bottom(), second.bottom())));
            result.remove(mergeAt + 1);
        }

        // Printed measure numbers tell us how many bars belong on the system, not where those
        // bars are. Engraving legitimately gives a whole-note measure far less width than a dense
        // 32nd-note measure. The old equal-width refit moved every real barline and made long note
        // stems become tiny measures. Remove only the most plausible extra boundary instead.
        while (result.size() > expected) {
            int best = 0;
            float bestScore = Float.MAX_VALUE;
            for (int index = 0; index + 1 < result.size(); index++) {
                MeasureRegion first = result.get(index), second = result.get(index + 1);
                float joinedWidth = second.right() - first.left();
                float firstWidth = first.right() - first.left();
                float secondWidth = second.right() - second.left();
                float score = Math.abs(joinedWidth - targetWidth) / targetWidth;
                // A false note-stem boundary normally creates one or two undersized fragments.
                // Prefer repairing those over merging two already full-sized real measures.
                score += Math.min(firstWidth, secondWidth) >= targetWidth * .82f ? .75f : 0f;
                if (score < bestScore) {
                    bestScore = score;
                    best = index;
                }
            }
            MeasureRegion first = result.get(best), second = result.get(best + 1);
            result.set(
                    best,
                    new MeasureRegion(
                            first.left(),
                            second.right(),
                            Math.min(first.top(), second.top()),
                            Math.max(first.bottom(), second.bottom())));
            result.remove(best + 1);
        }

        // If a faint barline was missed, split only the widest merged region. Redistributing the
        // complete system destroys every correct unequal-width boundary merely to repair one gap.
        while (result.size() < expected) {
            int widest = 0;
            for (int index = 1; index < result.size(); index++)
                if (regionWidth(result.get(index)) > regionWidth(result.get(widest)))
                    widest = index;
            MeasureRegion region = result.remove(widest);
            float middle = (region.left() + region.right()) / 2f;
            float gap = Math.min(.004f, regionWidth(region) * .04f);
            if (middle - region.left() <= gap || region.right() - middle <= gap)
                return List.copyOf(source);
            result.add(
                    widest,
                    new MeasureRegion(
                            region.left(), middle - gap / 2f, region.top(), region.bottom()));
            result.add(
                    widest + 1,
                    new MeasureRegion(
                            middle + gap / 2f, region.right(), region.top(), region.bottom()));
        }
        return List.copyOf(result);
    }

    private static List<MeasureRegion> fitHighlyFragmentedRow(
            List<MeasureRegion> source, int expected, float left, float right) {
        int splitCount = expected - 1;
        int gapCount = source.size() - 1;
        float[][] costs = new float[splitCount][gapCount];
        int[][] previous = new int[splitCount][gapCount];
        for (int split = 0; split < splitCount; split++) {
            java.util.Arrays.fill(costs[split], Float.POSITIVE_INFINITY);
            java.util.Arrays.fill(previous[split], -1);
            float target = left + (right - left) * (split + 1f) / expected;
            int firstGap = split;
            int lastGap = gapCount - (splitCount - split);
            for (int gap = firstGap; gap <= lastGap; gap++) {
                float boundary = (source.get(gap).right() + source.get(gap + 1).left()) * .5f;
                float localCost = Math.abs(boundary - target);
                if (split == 0) {
                    costs[split][gap] = localCost;
                    continue;
                }
                for (int prior = split - 1; prior < gap; prior++) {
                    float candidate = costs[split - 1][prior] + localCost;
                    if (candidate < costs[split][gap]) {
                        costs[split][gap] = candidate;
                        previous[split][gap] = prior;
                    }
                }
            }
        }

        int lastSplit = splitCount - 1;
        int bestGap = -1;
        float bestCost = Float.POSITIVE_INFINITY;
        for (int gap = lastSplit; gap < gapCount; gap++)
            if (costs[lastSplit][gap] < bestCost) {
                bestCost = costs[lastSplit][gap];
                bestGap = gap;
            }
        if (bestGap < 0) return List.copyOf(source);
        int[] splitAfter = new int[splitCount];
        for (int split = lastSplit; split >= 0; split--) {
            splitAfter[split] = bestGap;
            bestGap = previous[split][bestGap];
        }

        List<MeasureRegion> result = new ArrayList<>();
        int start = 0;
        for (int group = 0; group < expected; group++) {
            int end = group < splitAfter.length ? splitAfter[group] : source.size() - 1;
            MeasureRegion first = source.get(start), last = source.get(end);
            float top = first.top(), bottom = first.bottom();
            for (int index = start + 1; index <= end; index++) {
                top = Math.min(top, source.get(index).top());
                bottom = Math.max(bottom, source.get(index).bottom());
            }
            result.add(new MeasureRegion(first.left(), last.right(), top, bottom));
            start = end + 1;
        }
        return List.copyOf(result);
    }

    private static float regionWidth(MeasureRegion region) {
        return Math.max(0f, region.right() - region.left());
    }

    private static final class Row {
        final float top;
        float bottom;
        float left = Float.MAX_VALUE;
        float right;
        final List<MeasureRegion> measures = new ArrayList<>();
        NumberToken anchor;
        int restExtras;

        Row(float top, float bottom) {
            this.top = top;
            this.bottom = bottom;
        }
    }
}
