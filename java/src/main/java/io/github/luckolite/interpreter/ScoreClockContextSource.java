// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Cacheable source facts for assembled clock adjudication. A source observation, sampled
 * contour model, producer partition, and musical clock authority are distinct premises.
 * This data contains no manually asserted complete-score flag and never selects a clock.
 */
public record ScoreClockContextSource(
        Origin origin,
        int noteCount,
        int measureCount,
        int restCount,
        List<PartWord> partWords,
        List<NumberRead> numbers,
        List<MeterRead> meters,
        List<PrintedValue> printedValues,
        List<GlyphLedger> glyphLedgers,
        List<RestValue> restValues,
        List<VoiceGroup> voiceGroups,
        List<SyncAnchor> syncAnchors,
        List<Missing> missing) {
    public enum Status {
        UNKNOWN,
        OBSERVATION,
        SOURCE_PREMISE,
        CONTRARY,
        UNREAD,
        UNSUPPORTED
    }

    public enum WordRole {
        OUTSIDE_PART_SLOT,
        PART_LABEL,
        UNKNOWN_LABEL_RIVAL
    }

    public enum NumberRoute {
        OUTSIDE_HEADER,
        TRUSTED_ASSIGNED,
        SINGLE_MEASURE_ONE_RETURN,
        UNASSIGNED_HEADER_OBSERVATION,
        UNREAD_HEADER_WORD
    }

    public enum MeterRoute {
        RASTER_UNBOUNDED,
        READER_BOUNDED,
        UNREAD_CANDIDATE,
        UNKNOWN,
        HEADER_MODEL_EXCLUSION
    }

    public enum ValueKind {
        UNKNOWN,
        WHOLE,
        HALF,
        QUARTER,
        BEAMED
    }

    public enum ValueRoute {
        UNKNOWN,
        OPAQUE_DEFAULT,
        OPEN_WITH_STEM,
        OPEN_WITHOUT_STEM,
        BEAM_INPUT,
        FILLED_PHYSICAL_TREMOLO_ENDPOINT
    }

    public enum Model {
        NONE,
        GEOMETRIC_ELLIPSE_SOURCE_CONTACT
    }

    public enum RestKind {
        UNKNOWN,
        FULL_BAR,
        WHOLE,
        HALF,
        QUARTER,
        EIGHTH,
        SIXTEENTH,
        THIRTY_SECOND,
        SIXTY_FOURTH
    }

    public enum GroupKind {
        ATTACK_CHORD,
        REST,
        GRACE
    }

    public enum SyncKind {
        SAME_PRINTED_ATTACK_COLUMN,
        SHARED_BEAM_BOUNDARY
    }

    public enum Requirement {
        DOCUMENT_ORIGIN,
        PART_CONTINUITY,
        METER_SCOPE,
        PRINTED_VALUES,
        RHYTHMIC_GLYPH_CLOSURE,
        VOICE_OWNERSHIP,
        REST_OWNERSHIP,
        SYNCHRONIZED_ANCHORS,
        CAPTURE_BUDGET
    }

    public record Origin(
            String documentSha256,
            int physicalPageIndex,
            int physicalPageCount,
            int width,
            int height,
            String rasterSha256,
            String labelsSha256,
            String annotationRasterSha256) {
        public Origin(
                String documentSha256,
                int physicalPageIndex,
                int physicalPageCount,
                int width,
                int height,
                String rasterSha256,
                String labelsSha256) {
            this(
                    documentSha256,
                    physicalPageIndex,
                    physicalPageCount,
                    width,
                    height,
                    rasterSha256,
                    labelsSha256,
                    rasterSha256);
        }

        public Origin {
            annotationRasterSha256 =
                    Objects.requireNonNullElse(annotationRasterSha256, rasterSha256);
            hash(annotationRasterSha256);
            hash(documentSha256);
            hash(rasterSha256);
            hash(labelsSha256);
            integer(physicalPageIndex, -1, 9999);
            integer(physicalPageCount, 0, 10000);
            integer(width, 0, 20000000);
            integer(height, 0, 20000000);
            if ((width == 0) != (height == 0)
                    || (long) width * height > 20000000L
                    || (physicalPageCount > 0 && physicalPageIndex >= physicalPageCount))
                throw new IllegalArgumentException("Clock origin dimensions/page");
        }

        public static Origin unavailable() {
            return new Origin("", -1, 0, 0, 0, "", "");
        }

        public boolean boundDocument() {
            return !documentSha256.isEmpty()
                    && physicalPageIndex >= 0
                    && physicalPageIndex < physicalPageCount
                    && !rasterSha256.isEmpty()
                    && width > 0;
        }
    }

    public record Box(float left, float top, float right, float bottom) {
        public Box {
            finite(left, 0, 1);
            finite(top, 0, 1);
            finite(right, 0, 1);
            finite(bottom, 0, 1);
            if (left > right || top > bottom) throw new IllegalArgumentException("Clock box");
        }

        public static Box unknown() {
            return new Box(0, 0, 0, 0);
        }
    }

    public record RasterBox(int left, int top, int right, int bottom) {
        public RasterBox {
            if (left == -1 && top == -1 && right == -1 && bottom == -1) {
            } else {
                integer(left, 0, 20000000);
                integer(top, 0, 20000000);
                integer(right, left, 20000000);
                integer(bottom, top, 20000000);
            }
        }

        public static RasterBox unknown() {
            return new RasterBox(-1, -1, -1, -1);
        }

        boolean known() {
            return left >= 0;
        }
    }

    public record PartWord(
            int ordinal,
            int system,
            int firstMeasure,
            int measureAfterLast,
            String text,
            String textSha256,
            String canonicalPart,
            Box bounds,
            WordRole role,
            int sampledMembers,
            String sampledSha256,
            Status status) {
        public PartWord {
            integer(ordinal, 0, 9999);
            integer(system, -1, 9999);
            integer(firstMeasure, -1, 10000);
            integer(measureAfterLast, -1, 10000);
            if (firstMeasure >= 0 && measureAfterLast <= firstMeasure)
                throw new IllegalArgumentException("Part measure span");
            boundedText(text, 1024);
            hash(textSha256);
            boundedText(canonicalPart, 160);
            Objects.requireNonNull(bounds);
            Objects.requireNonNull(role);
            integer(sampledMembers, 0, 1000000);
            hash(sampledSha256);
            Objects.requireNonNull(status);
            if (status == Status.SOURCE_PREMISE
                    && (role != WordRole.PART_LABEL
                            || canonicalPart.isBlank()
                            || sampledMembers == 0
                            || sampledSha256.isEmpty()
                            || bounds.left() == bounds.right()
                            || bounds.top() == bounds.bottom()))
                throw new IllegalArgumentException("Unbound part premise");
        }
    }

    public record NumberRead(
            int ordinal,
            int system,
            String text,
            int value,
            int originCandidate,
            Box bounds,
            NumberRoute route,
            Status status) {
        public NumberRead {
            integer(ordinal, 0, 19999);
            integer(system, -1, 9999);
            boundedText(text, 1024);
            integer(value, 0, 9999);
            integer(originCandidate, -10000, 10000);
            Objects.requireNonNull(bounds);
            Objects.requireNonNull(route);
            Objects.requireNonNull(status);
        }
    }

    public record MeterRead(
            int ordinal,
            int measure,
            String text,
            int numerator,
            int denominator,
            RasterBox bounds,
            float firstLine,
            float gap,
            int votes,
            int sampledMembers,
            String sampledSha256,
            MeterRoute route,
            boolean originalProducerAccepted,
            Status status) {
        public MeterRead {
            integer(ordinal, 0, 9999);
            integer(measure, -1, 10000);
            boundedText(text, 1024);
            integer(numerator, 0, 128);
            integer(denominator, 0, 128);
            Objects.requireNonNull(bounds);
            finite(firstLine, -1, 20000000);
            finite(gap, 0, 20000000);
            integer(votes, 0, 10000);
            integer(sampledMembers, 0, 1000000);
            hash(sampledSha256);
            Objects.requireNonNull(route);
            Objects.requireNonNull(status);
            if (status == Status.SOURCE_PREMISE
                    && (!originalProducerAccepted
                            || numerator == 0
                            || denominator == 0
                            || !bounds.known()
                            || sampledMembers == 0
                            || sampledSha256.isEmpty()))
                throw new IllegalArgumentException("Unbound meter premise");
        }
    }

    public record PrintedValue(
            int noteIndex,
            int measure,
            int staff,
            int staffs,
            ValueKind kind,
            float baseBeats,
            int dots,
            int beams,
            ValueRoute route,
            String originalExit,
            int admissionFlags,
            int selectedMembers,
            int parentMembers,
            int parentLeaves,
            int residualMembers,
            String selectedSha256,
            String parentSha256,
            Model model,
            Status status) {
        public PrintedValue {
            integer(noteIndex, 0, 999999);
            integer(measure, 0, 9999);
            integer(staff, 0, 127);
            integer(staffs, 1, 128);
            if (staff >= staffs) throw new IllegalArgumentException("Value staff");
            Objects.requireNonNull(kind);
            finite(baseBeats, 0, 32);
            integer(dots, 0, 4);
            integer(beams, 0, 8);
            Objects.requireNonNull(route);
            boundedText(originalExit, 256);
            integer(admissionFlags, 0, 65535);
            integer(selectedMembers, 0, 1000000);
            integer(parentMembers, selectedMembers, 1000000);
            integer(parentLeaves, 0, 10000);
            integer(residualMembers, 0, parentMembers);
            hash(selectedSha256);
            hash(parentSha256);
            Objects.requireNonNull(model);
            Objects.requireNonNull(status);
            if (status == Status.SOURCE_PREMISE
                    && (selectedMembers == 0
                            || parentLeaves == 0
                            || residualMembers != 0
                            || selectedSha256.isEmpty()
                            || parentSha256.isEmpty()))
                throw new IllegalArgumentException("Unbound printed premise");
        }
    }

    public record GlyphLedger(
            int measure,
            int semanticLabel,
            long population,
            long coveredUnique,
            long noteMembers,
            long nonRhythmicMembers,
            long restMembers,
            long unknownMembers,
            String coverageSha256,
            String producerRoute,
            Status status) {
        public GlyphLedger {
            integer(measure, -1, 9999);
            integer(semanticLabel, 0, 255);
            count(population);
            count(coveredUnique);
            count(noteMembers);
            count(nonRhythmicMembers);
            count(restMembers);
            count(unknownMembers);
            if (coveredUnique > population
                    || noteMembers + nonRhythmicMembers + restMembers + unknownMembers
                            > coveredUnique)
                throw new IllegalArgumentException("Clock member accounting");
            hash(coverageSha256);
            boundedText(producerRoute, 256);
            Objects.requireNonNull(status);
            if (status == Status.SOURCE_PREMISE
                    && (coveredUnique != population
                            || unknownMembers != 0
                            || noteMembers + nonRhythmicMembers + restMembers != coveredUnique
                            || coverageSha256.isEmpty()))
                throw new IllegalArgumentException("Incomplete glyph ledger premise");
        }
    }

    public record RestValue(
            int restIndex,
            int measure,
            int staff,
            int staffs,
            RestKind kind,
            float baseBeats,
            int dots,
            String membersSha256,
            String producerRoute,
            Status status) {
        public RestValue {
            integer(restIndex, 0, 999999);
            integer(measure, 0, 9999);
            integer(staff, 0, 127);
            integer(staffs, 1, 128);
            if (staff >= staffs) throw new IllegalArgumentException("Rest staff");
            Objects.requireNonNull(kind);
            finite(baseBeats, 0, 64);
            integer(dots, 0, 4);
            hash(membersSha256);
            boundedText(producerRoute, 256);
            Objects.requireNonNull(status);
        }
    }

    public record VoiceGroup(
            int measure,
            int staff,
            int staffs,
            String voiceId,
            int ordinal,
            GroupKind kind,
            List<Integer> noteIndices,
            List<Integer> restIndices,
            String membersSha256,
            String sourceRoute,
            Status status) {
        public VoiceGroup {
            integer(measure, 0, 9999);
            integer(staff, 0, 127);
            integer(staffs, 1, 128);
            if (staff >= staffs) throw new IllegalArgumentException("Voice staff");
            boundedText(voiceId, 256);
            integer(ordinal, 0, 999999);
            Objects.requireNonNull(kind);
            noteIndices = indices(noteIndices, 128);
            restIndices = indices(restIndices, 128);
            hash(membersSha256);
            boundedText(sourceRoute, 256);
            Objects.requireNonNull(status);
            if (status == Status.SOURCE_PREMISE
                    && (voiceId.isBlank()
                            || membersSha256.isEmpty()
                            || sourceRoute.isBlank()
                            || (kind == GroupKind.REST
                                    ? (noteIndices.size() != 0 || restIndices.isEmpty())
                                    : (noteIndices.isEmpty() || !restIndices.isEmpty()))))
                throw new IllegalArgumentException("Unbound voice group premise");
        }
    }

    public record SyncAnchor(
            int measure,
            String firstVoice,
            int firstThroughOrdinal,
            String secondVoice,
            int secondThroughOrdinal,
            SyncKind kind,
            Box bounds,
            String membersSha256,
            String sourceRoute,
            Status status) {
        public SyncAnchor {
            integer(measure, 0, 9999);
            boundedText(firstVoice, 256);
            boundedText(secondVoice, 256);
            integer(firstThroughOrdinal, 0, 999999);
            integer(secondThroughOrdinal, 0, 999999);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(bounds);
            hash(membersSha256);
            boundedText(sourceRoute, 256);
            Objects.requireNonNull(status);
            if (status == Status.SOURCE_PREMISE && firstVoice.equals(secondVoice))
                throw new IllegalArgumentException("Self sync anchor");
        }
    }

    public record Missing(Requirement requirement, int measure) {
        public Missing {
            Objects.requireNonNull(requirement);
            integer(measure, -1, 9999);
        }
    }

    public ScoreClockContextSource {
        Objects.requireNonNull(origin);
        integer(noteCount, 0, 1000000);
        integer(measureCount, 0, 10000);
        integer(restCount, 0, 1000000);
        partWords = copy(partWords, 10000);
        numbers = copy(numbers, 20000);
        meters = copy(meters, 10000);
        printedValues = copy(printedValues, 100000);
        glyphLedgers = copy(glyphLedgers, 100000);
        restValues = copy(restValues, 100000);
        voiceGroups = copy(voiceGroups, 100000);
        syncAnchors = copy(syncAnchors, 100000);
        missing = copy(missing, 100000);
        if ((long) partWords.size()
                        + numbers.size()
                        + meters.size()
                        + printedValues.size()
                        + glyphLedgers.size()
                        + restValues.size()
                        + voiceGroups.size()
                        + syncAnchors.size()
                        + missing.size()
                > 100000) throw new IllegalArgumentException("Clock context fact limit");
        for (var p : partWords)
            if (p.firstMeasure() >= measureCount && p.firstMeasure() >= 0
                    || p.measureAfterLast() > measureCount)
                throw new IllegalArgumentException("Part outside page");
        for (var m : meters)
            if (m.measure() > measureCount
                    || (m.bounds().known()
                            && (origin.width() == 0
                                    || m.bounds().right() >= origin.width()
                                    || m.bounds().bottom() >= origin.height())))
                throw new IllegalArgumentException("Meter outside source");
        Set<Integer> values = new HashSet<>();
        for (var v : printedValues)
            if (v.noteIndex() >= noteCount
                    || v.measure() >= measureCount
                    || !values.add(v.noteIndex()))
                throw new IllegalArgumentException("Printed value owner");
        Set<Integer> rests = new HashSet<>();
        for (var v : restValues)
            if (v.restIndex() >= restCount
                    || v.measure() >= measureCount
                    || !rests.add(v.restIndex()))
                throw new IllegalArgumentException("Rest value owner");
        for (var g : glyphLedgers)
            if (g.measure() >= measureCount)
                throw new IllegalArgumentException("Ledger outside page");
        Set<String> groups = new HashSet<>();
        for (var g : voiceGroups) {
            if (g.measure() >= measureCount
                    || !groups.add(g.measure() + "|" + g.voiceId() + "|" + g.ordinal()))
                throw new IllegalArgumentException("Voice group identity");
            for (int n : g.noteIndices())
                if (n >= noteCount) throw new IllegalArgumentException("Voice note owner");
            for (int n : g.restIndices())
                if (n >= restCount) throw new IllegalArgumentException("Voice rest owner");
        }
        for (var a : syncAnchors)
            if (a.measure() >= measureCount)
                throw new IllegalArgumentException("Sync outside page");
        Set<Missing> unique = new HashSet<>();
        for (var m : missing)
            if (m.measure() >= measureCount || !unique.add(m))
                throw new IllegalArgumentException("Missing context identity");
    }

    public static ScoreClockContextSource unavailable(int notes, int measures, int rests) {
        var missing = new ArrayList<Missing>();
        for (var r : Requirement.values())
            if (r != Requirement.CAPTURE_BUDGET) missing.add(new Missing(r, -1));
        return new ScoreClockContextSource(
                Origin.unavailable(),
                notes,
                measures,
                rests,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                missing);
    }

    public void validateCounts(int notes, int measures, int rests) {
        if (noteCount != notes || measureCount != measures || restCount != rests)
            throw new IllegalArgumentException("Clock context page counts");
    }

    public boolean hasFacts() {
        return !partWords.isEmpty()
                || !numbers.isEmpty()
                || !meters.isEmpty()
                || !printedValues.isEmpty()
                || !glyphLedgers.isEmpty()
                || !restValues.isEmpty()
                || !voiceGroups.isEmpty()
                || !syncAnchors.isEmpty()
                || !origin.equals(Origin.unavailable());
    }

    private static <T> List<T> copy(List<T> v, int max) {
        Objects.requireNonNull(v);
        if (v.size() > max) throw new IllegalArgumentException("Clock collection");
        return List.copyOf(v);
    }

    private static List<Integer> indices(List<Integer> v, int max) {
        v = copy(v, max);
        Set<Integer> s = new HashSet<>();
        for (int n : v)
            if (n < 0 || n >= 1000000 || !s.add(n))
                throw new IllegalArgumentException("Clock index set");
        return v;
    }

    private static void hash(String v) {
        Objects.requireNonNull(v);
        if (!v.isEmpty() && !v.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Clock source hash");
    }

    private static void boundedText(String v, int max) {
        Objects.requireNonNull(v);
        if (v.length() > max || v.indexOf(0) >= 0) throw new IllegalArgumentException("Clock text");
    }

    private static void integer(long v, long lo, long hi) {
        if (v < lo || v > hi) throw new IllegalArgumentException("Clock integer");
    }

    private static void finite(float v, float lo, float hi) {
        if (!Float.isFinite(v) || v < lo || v > hi)
            throw new IllegalArgumentException("Clock float");
    }

    private static void count(long v) {
        integer(v, 0, 20000000);
    }
}
