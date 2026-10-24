// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Genuine reader observations and original number-producer routes. Part labels, numbering and
 * sampled word ink remain separate facts; this class never claims complete voices or a clock.
 */
final class ScorePartProducerSource {
    record WordReading(
            int ordinal,
            String text,
            float left,
            float top,
            float right,
            float bottom,
            int system,
            int headerSystem,
            String canonicalPart,
            String status,
            List<Integer> sampledInk) {
        WordReading {
            sampledInk = List.copyOf(sampledInk);
        }
    }

    record PartReading(int system, String canonicalPart, List<Integer> words, String status) {
        PartReading {
            words = List.copyOf(words);
        }
    }

    record NumberReading(MeasureNumberReconciler.ProducerNumber route, int word, String status) {}

    record Data(
            String rawSha,
            List<MeasureNumberReconciler.ProducerRow> rows,
            List<WordReading> words,
            List<PartReading> parts,
            List<NumberReading> numbers,
            int originalNumberOriginCandidate,
            List<Integer> unboundHeaderWords,
            List<String> missingAuthorities) {
        Data {
            rows = List.copyOf(rows);
            words = List.copyOf(words);
            parts = List.copyOf(parts);
            numbers = List.copyOf(numbers);
            unboundHeaderWords = List.copyOf(unboundHeaderWords);
            missingAuthorities = List.copyOf(missingAuthorities);
        }
    }

    private final Data data;

    private ScorePartProducerSource(Data data) {
        this.data = data;
    }

    Data data() {
        return data;
    }

    private static boolean bounds(float l, float t, float r, float b) {
        return Float.isFinite(l)
                && Float.isFinite(t)
                && Float.isFinite(r)
                && Float.isFinite(b)
                && l >= 0
                && t >= 0
                && r <= 1
                && b <= 1
                && l < r
                && t < b;
    }

    private static boolean same(float a, float b) {
        return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b);
    }

    private static String part(String value) {
        if (!OpeningPartTempo.part(value)) return "";
        String v = value.strip().toLowerCase(Locale.ROOT).replaceAll("\\h+", " ");
        var aliases =
                Map.of(
                        "pno.",
                        "piano",
                        "vln.",
                        "violin",
                        "vla.",
                        "viola",
                        "vc.",
                        "cello",
                        "fl.",
                        "flute",
                        "cl.",
                        "clarinet",
                        "ob.",
                        "oboe",
                        "bsn.",
                        "bassoon");
        int blank = v.indexOf(' ');
        String first = blank < 0 ? v : v.substring(0, blank);
        return aliases.getOrDefault(first, first) + (blank < 0 ? "" : v.substring(blank));
    }

    static ScorePartProducerSource capture(
            ScorePageAnnotationSource source,
            byte[] gray,
            int w,
            int h,
            List<MeasureRegion> measures) {
        var rows = MeasureNumberReconciler.producerRows(measures);
        var words = new ArrayList<WordReading>();
        var parts = new ArrayList<PartReading>();
        var numberRows = new ArrayList<NumberReading>();
        var unknownHeader = new ArrayList<Integer>();
        boolean bound = source != null && source.authenticates(gray, w, h);
        long work = 2_000_000, stored = 1_000_000;
        int wordCount = source == null ? 0 : source.words().size(),
                numberCount = source == null ? 0 : source.numbers().size();
        long contextWork =
                (long) wordCount * measures.size()
                        + (long) numberCount * numberCount
                        + (long) wordCount * numberCount;
        if (contextWork > 4_000_000) {
            for (int i = 0; i < wordCount; i++) {
                var v = source.words().get(i);
                words.add(
                        new WordReading(
                                i,
                                v.text(),
                                v.left(),
                                v.top(),
                                v.right(),
                                v.bottom(),
                                -1,
                                -1,
                                "",
                                "UNKNOWN_CONTEXT_WORK_BUDGET",
                                List.of()));
            }
            for (int i = 0; i < numberCount; i++) {
                var token = source.numbers().get(i);
                numberRows.add(
                        new NumberReading(
                                new MeasureNumberReconciler.ProducerNumber(
                                        i,
                                        token,
                                        -1,
                                        false,
                                        false,
                                        0,
                                        "UNKNOWN_CONTEXT_WORK_BUDGET"),
                                -1,
                                "UNKNOWN_CONTEXT_WORK_BUDGET"));
            }
            return new ScorePartProducerSource(
                    new Data(
                            source.rawSha(),
                            rows,
                            words,
                            List.of(),
                            numberRows,
                            0,
                            List.of(),
                            List.of(
                                    "UNKNOWN_CONTEXT_WORK_BUDGET",
                                    "PART_METER_VOICE_REST_SOURCE_AUTHORITY")));
        }
        List<ScorePageAnnotationSource.ReaderWord> original =
                source == null ? List.of() : source.words();
        for (int i = 0; i < original.size(); i++) {
            var word = original.get(i);
            if (!bounds(word.left(), word.top(), word.right(), word.bottom())) {
                words.add(
                        new WordReading(
                                i,
                                word.text(),
                                word.left(),
                                word.top(),
                                word.right(),
                                word.bottom(),
                                -1,
                                -1,
                                "",
                                "UNKNOWN_INVALID_OR_ABSENT_READER_BOUNDS",
                                List.of()));
                continue;
            }
            var local =
                    new PlayingTechniqueDetector.Word(
                            word.text(), word.left(), word.top(), word.right(), word.bottom());
            int header = MeasureNumberReconciler.producerHeaderRow(local, measures);
            int owner = -1;
            float cy = (word.top() + word.bottom()) * .5f;
            for (var row : rows)
                if (cy >= row.top() && cy <= row.bottom() && word.right() <= row.left()) {
                    if (owner >= 0) {
                        owner = -2;
                        break;
                    }
                    owner = row.row();
                }
            String canonical = part(word.text()),
                    status =
                            bound
                                    ? "ORIGINAL_READER_WORD_OUTSIDE_PART_LABEL_SLOT"
                                    : "UNKNOWN_SOURCE_FRAME";
            var ink = new ArrayList<Integer>();
            if (bound && owner >= 0) {
                int left = Math.max(0, (int) Math.floor(word.left() * w)),
                        right = Math.min(w - 1, (int) Math.ceil(word.right() * w)),
                        top = Math.max(0, (int) Math.floor(word.top() * h)),
                        bottom = Math.min(h - 1, (int) Math.ceil(word.bottom() * h));
                long area = (long) (right - left + 1) * (bottom - top + 1);
                if (area < 1 || area > 250000 || area > work || stored < area) {
                    status = "UNKNOWN_WORD_SAMPLE_BUDGET";
                } else {
                    work -= area;
                    for (int y = top; y <= bottom; y++)
                        for (int x = left; x <= right; x++)
                            if ((gray[y * w + x] & 255) < 165) ink.add(y * w + x);
                    stored -= ink.size();
                    status =
                            ink.isEmpty()
                                    ? "UNKNOWN_LABEL_WITHOUT_SOURCE_INK"
                                    : canonical.isEmpty()
                                            ? "UNKNOWN_LEFT_MARGIN_LABEL_RIVAL"
                                            : "SOURCE_BOUND_PART_LABEL_READING";
                }
            }
            if (header >= 0) unknownHeader.add(i);
            words.add(
                    new WordReading(
                            i,
                            word.text(),
                            word.left(),
                            word.top(),
                            word.right(),
                            word.bottom(),
                            owner,
                            header,
                            canonical,
                            status,
                            ink));
        }
        for (var row : rows) {
            var labels = words.stream().filter(word -> word.system() == row.row()).toList();
            var identities = new HashSet<String>();
            var indices = new ArrayList<Integer>();
            boolean unknown = labels.isEmpty();
            for (var word : labels) {
                indices.add(word.ordinal());
                if (!word.status().equals("SOURCE_BOUND_PART_LABEL_READING")) unknown = true;
                else identities.add(word.canonicalPart());
            }
            String canonical = identities.size() == 1 ? identities.iterator().next() : "";
            parts.add(
                    new PartReading(
                            row.row(),
                            canonical,
                            indices,
                            !unknown && identities.size() == 1
                                    ? "CONSISTENT_SOURCE_LABEL_OBSERVATION"
                                    : "UNKNOWN_OR_CONFLICTING_PART_LABELS"));
        }
        List<MeasureNumberReconciler.NumberToken> tokens =
                source == null ? List.of() : source.numbers();
        for (var route : MeasureNumberReconciler.producerNumberRoutes(measures, tokens)) {
            int witness = -1;
            var token = route.token();
            for (int i = 0; i < original.size(); i++) {
                var word = original.get(i);
                if (word.text().strip().equals(Integer.toString(token.value()))
                        && same(word.left(), token.left())
                        && same(word.top(), token.top())
                        && same(word.right(), token.right())
                        && same(word.bottom(), token.bottom())) {
                    if (witness >= 0) {
                        witness = -2;
                        break;
                    }
                    witness = i;
                }
            }
            String status =
                    bound
                                    && witness >= 0
                                    && bounds(
                                            token.left(),
                                            token.top(),
                                            token.right(),
                                            token.bottom())
                            ? "BOUND_ORIGINAL_NUMBER_AND_WORD_READING"
                            : "UNKNOWN_UNBOUND_OR_AMBIGUOUS_NUMBER_READING";
            if (status.startsWith("BOUND")) unknownHeader.remove(Integer.valueOf(witness));
            numberRows.add(new NumberReading(route, witness, status));
        }
        int origin = bound ? MeasureNumberReconciler.firstMeasureNumber(measures, tokens) : 0;
        return new ScorePartProducerSource(
                new Data(
                        source == null ? "" : source.rawSha(),
                        rows,
                        words,
                        parts,
                        numberRows,
                        origin,
                        unknownHeader,
                        List.of(
                                "GENUINE_DOCUMENT_PAGE_ENVELOPE_AND_ORDER",
                                "PART_CONTINUITY_ADJUDICATION",
                                "METER_INHERITANCE_WITH_RIVALS",
                                "COMPLETE_VOICE_REST_AND_SYNCHRONIZATION_SOURCE")));
    }
}
