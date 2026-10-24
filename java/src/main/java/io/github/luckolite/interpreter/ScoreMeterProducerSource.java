// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Preserves genuine meter-reader observations, including rivals and unread proposals.
 * Accepted OCR plus sampled support is a meter-reading fact, never rhythmic completeness
 * or an authenticated cross-page part identity.
 */
final class ScoreMeterProducerSource {
    record Observation(
            AcceptedMeterHeaderBounds.Source source,
            int ordinal,
            int measure,
            MeterChangeDetector.Crop crop,
            String text,
            int votes,
            String route,
            MeterHeaderInkGuard.ModelExclusion exclusion) {
        Observation(
                AcceptedMeterHeaderBounds.Source source,
                int ordinal,
                int measure,
                MeterChangeDetector.Crop crop,
                String text,
                int votes,
                String route) {
            this(source, ordinal, measure, crop, text, votes, route, null);
        }
    }

    static Observation headerExclusion(
            AcceptedMeterHeaderBounds.Source source,
            int ordinal,
            int measure,
            MeterChangeDetector.Crop crop) {
        if (source == null || measure < 0 || measure >= source.measures.size()) return null;
        var exclusion = MeterHeaderInkGuard.exclusion(source, crop);
        return exclusion == null
                ? null
                : new Observation(
                        source,
                        ordinal,
                        measure,
                        crop,
                        "",
                        0,
                        "SOURCE_BOUND_HEADER_MODEL_EXCLUSION",
                        exclusion);
    }

    record Reading(
            int ordinal,
            int measure,
            MeterChangeDetector.Crop crop,
            String text,
            int votes,
            String route,
            List<Integer> sampledSupport,
            boolean acceptedByOriginalProducer,
            String status,
            String exclusionModel) {
        Reading {
            sampledSupport = List.copyOf(sampledSupport);
            exclusionModel = Objects.requireNonNullElse(exclusionModel, "");
        }
    }

    private final String rawSha, labelSha;
    private final int width, height;
    private final List<Reading> readings;

    private ScoreMeterProducerSource(String raw, String labels, int w, int h, List<Reading> rows) {
        rawSha = raw;
        labelSha = labels;
        width = w;
        height = h;
        readings = List.copyOf(rows);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScoreMeterProducerSource x
                && width == x.width
                && height == x.height
                && rawSha.equals(x.rawSha)
                && labelSha.equals(x.labelSha)
                && readings.equals(x.readings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawSha, labelSha, width, height, readings);
    }

    String rawSha() {
        return rawSha;
    }

    String labelSha() {
        return labelSha;
    }

    List<Reading> readings() {
        return readings;
    }

    ScoreMeterProducerSource withLocalAccidentalExclusions(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            java.util.List<MeasureRegion> measures,
            SourceLocalAccidental local,
            java.util.List<ScoreNoteEvent> notes) {
        var source = AcceptedMeterHeaderBounds.source(labels, gray, width, height, measures);
        if (source == null || !rawSha.equals(source.rawSha) || !labelSha.equals(source.labelSha))
            return this;
        var rows = new java.util.ArrayList<>(readings);
        int ordinal = readings.stream().mapToInt(Reading::ordinal).max().orElse(-1) + 1;
        for (var original : readings) {
            if (original.crop() == null
                    || original.measure() < 0
                    || original.measure() >= measures.size()) continue;
            var exclusion =
                    MeterHeaderInkGuard.localAccidentalExclusion(
                            source, original.crop(), original.measure(), local, notes);
            if (exclusion == null) continue;
            int extraOrdinal = ordinal++;
            var observation =
                    new Observation(
                            source,
                            0,
                            original.measure(),
                            original.crop(),
                            "",
                            0,
                            "SOURCE_BOUND_HEADER_MODEL_EXCLUSION",
                            exclusion);
            for (var extra :
                    capture(source, java.util.List.of(observation), java.util.List.of()).readings())
                rows.add(
                        new Reading(
                                extraOrdinal,
                                extra.measure(),
                                extra.crop(),
                                extra.text(),
                                extra.votes(),
                                extra.route(),
                                extra.sampledSupport(),
                                extra.acceptedByOriginalProducer(),
                                extra.status(),
                                extra.exclusionModel()));
        }
        return new ScoreMeterProducerSource(rawSha, labelSha, width, height, rows);
    }

    static ScoreMeterProducerSource unavailable() {
        return new ScoreMeterProducerSource("", "", 0, 0, List.of());
    }

    static ScoreMeterProducerSource capture(
            AcceptedMeterHeaderBounds.Source source,
            List<Observation> observations,
            List<ScoreMeterChange> accepted) {
        boolean bound = source != null && observations.size() <= 128 && source.sameSnapshot();
        List<Reading> rows = new ArrayList<>();
        int remaining = 1_000_000;
        for (int i = 0; i < observations.size(); i++) {
            var observed = observations.get(i);
            boolean chosen =
                    accepted.stream()
                            .anyMatch(
                                    v ->
                                            v.measureIndex() == observed.measure()
                                                    && observed.text()
                                                            .equals(
                                                                    v.numerator()
                                                                            + "/"
                                                                            + v.denominator()));
            String status = "UNKNOWN_SOURCE_FRAME", model = "";
            int[] members = null;
            if (bound
                    && observed.source() == source
                    && observed.ordinal() == i
                    && observed.measure() >= 0
                    && observed.measure() < source.measures.size()) {
                if (observed.crop() == null) status = "UNKNOWN_READING_WITHOUT_SAMPLED_BOUNDS";
                else if (observed.exclusion() != null
                        && observed.exclusion().binds(source, observed.crop())) {
                    members = observed.exclusion().pixels();
                    if (members.length > remaining) {
                        members = null;
                        status = "UNKNOWN_MEMBER_BUDGET";
                    } else {
                        remaining -= members.length;
                        model = observed.exclusion().model();
                        status =
                                "BOUND_MODEL_SUPPORTED_HEADER_EXCLUSION_NOT_METER_ABSENCE_AUTHORITY";
                    }
                } else if (observed.text().isBlank() || observed.votes() <= 0)
                    status = "UNKNOWN_UNREAD_OR_AMBIGUOUS_CANDIDATE";
                else {
                    members = AcceptedMeterHeaderBounds.sampledMembers(source, observed.crop());
                    if (members == null) status = "UNKNOWN_MISSING_SAMPLED_SUPPORT";
                    else if (members.length > remaining) {
                        members = null;
                        status = "UNKNOWN_MEMBER_BUDGET";
                    } else {
                        remaining -= members.length;
                        status = "BOUND_READER_SAMPLE_NOT_COMPONENT_OWNERSHIP";
                    }
                }
            }
            var pixels = new ArrayList<Integer>();
            if (members != null) for (int pixel : members) pixels.add(pixel);
            rows.add(
                    new Reading(
                            i,
                            observed.measure(),
                            observed.crop(),
                            observed.text(),
                            observed.votes(),
                            observed.route(),
                            pixels,
                            chosen,
                            status,
                            model));
        }
        return new ScoreMeterProducerSource(
                bound ? source.rawSha : "",
                bound ? source.labelSha : "",
                source == null ? 0 : source.width,
                source == null ? 0 : source.height,
                rows);
    }

    /** Only two genuinely separate printed staff copies can corroborate a reading here.
     * Unknown or conflicting observations in the same logical measure remain vetoes.
     * This does not authorize inheritance into another page/part.
     */
    private static boolean disjointSorted(List<Integer> a, List<Integer> b) {
        int i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            int x = a.get(i), y = b.get(j);
            if (x == y) return false;
            if (x < y) i++;
            else j++;
        }
        return true;
    }

    List<ScoreMeterChange> corroboratedReadings() {
        if (readings.stream().anyMatch(r -> r.measure() < 0)) return List.of();
        var measures = new TreeSet<Integer>();
        for (var row : readings) if (row.measure() >= 0) measures.add(row.measure());
        var result = new ArrayList<ScoreMeterChange>();
        long workRemaining = 8_000_000;
        for (int measure : measures) {
            var group = readings.stream().filter(r -> r.measure() == measure).toList();
            if (group.stream()
                    .anyMatch(
                            r ->
                                    !r.acceptedByOriginalProducer()
                                            || !r.status()
                                                    .equals(
                                                            "BOUND_READER_SAMPLE_NOT_COMPONENT_OWNERSHIP")))
                continue;
            Set<String> texts = new HashSet<>();
            for (var row : group) texts.add(row.text());
            if (texts.size() != 1) continue;
            boolean separate = false, exhausted = false;
            pairs:
            for (var a : group)
                for (var b : group)
                    if (a.ordinal() < b.ordinal()) {
                        if (Math.abs(a.crop().firstLine() - b.crop().firstLine())
                                < 4 * Math.max(a.crop().gap(), b.crop().gap())) continue;
                        long work = (long) a.sampledSupport().size() + b.sampledSupport().size();
                        if (work > workRemaining) {
                            exhausted = true;
                            break pairs;
                        }
                        workRemaining -= work;
                        if (!disjointSorted(a.sampledSupport(), b.sampledSupport())) continue;
                        separate = true;
                        break pairs;
                    }
            if (exhausted) continue;
            if (!separate) continue;
            try {
                String[] pair = texts.iterator().next().split("/");
                if (pair.length != 2) continue;
                result.add(
                        new ScoreMeterChange(
                                measure, Integer.parseInt(pair[0]), Integer.parseInt(pair[1])));
            } catch (IllegalArgumentException invalid) {
            }
        }
        return List.copyOf(result);
    }
}
