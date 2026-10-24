// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import io.github.luckolite.interpreter.ScoreClockContextSource.*;

/** Adapts genuine producer emissions, never nominal totals, into the cache context tail.
 * Sampled part/meter ink and the endpoint ellipse/contact model remain separately named.
 * No complete-voice, rest, synchronized-clock, or cross-page part claim is issued here.
 */
final class ScoreClockContextCapture {
    private ScoreClockContextCapture() {}

    static String digest(byte[] bytes) {
        try {
            return RasterEvidenceIdentity.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static String members(List<Integer> pixels) {
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            for (int p : pixels) {
                hash.update((byte) (p >>> 24));
                hash.update((byte) (p >>> 16));
                hash.update((byte) (p >>> 8));
                hash.update((byte) p);
            }
            return RasterEvidenceIdentity.hex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static Box box(float l, float t, float r, float b) {
        return Float.isFinite(l)
                        && Float.isFinite(t)
                        && Float.isFinite(r)
                        && Float.isFinite(b)
                        && l >= 0
                        && t >= 0
                        && r <= 1
                        && b <= 1
                        && l < r
                        && t < b
                ? new Box(l, t, r, b)
                : Box.unknown();
    }

    static ScoreClockContextSource capture(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            ScorePageInterpretation finalScore,
            OmrScoreInterpreter.Analysis analysis,
            ScorePartProducerSource parts,
            ScoreMeterProducerSource meter) {
        return capture(labels, gray, width, height, finalScore, analysis, parts, meter, gray);
    }

    /** Annotation evidence stays tied to the original source when tab rows are suppressed. */
    static ScoreClockContextSource capture(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            ScorePageInterpretation finalScore,
            OmrScoreInterpreter.Analysis analysis,
            ScorePartProducerSource parts,
            ScoreMeterProducerSource meter,
            byte[] annotationGray) {
        try {
            var result =
                    captureBounded(
                            labels,
                            gray,
                            width,
                            height,
                            finalScore,
                            analysis,
                            parts,
                            meter,
                            annotationGray);
            // The producer obeys the same hard payload bound as every cache/transport writer.
            ScoreClockContextWire.encode(result);
            return result;
        } catch (IllegalArgumentException | java.io.IOException unsupportedMetadata) {
            // Unsupported observation size cannot change or suppress otherwise valid score notes.
            var unknown =
                    ScoreClockContextSource.unavailable(
                            finalScore.notes().size(),
                            finalScore.measures().size(),
                            finalScore.rests().size());
            var missing = new ArrayList<>(unknown.missing());
            missing.add(new Missing(Requirement.CAPTURE_BUDGET, -1));
            return new ScoreClockContextSource(
                    unknown.origin(),
                    unknown.noteCount(),
                    unknown.measureCount(),
                    unknown.restCount(),
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
    }

    private static ScoreClockContextSource captureBounded(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            ScorePageInterpretation finalScore,
            OmrScoreInterpreter.Analysis analysis,
            ScorePartProducerSource parts,
            ScoreMeterProducerSource meter,
            byte[] annotationGray) {
        if (labels == null
                || gray == null
                || labels.length != (long) width * height
                || gray.length != labels.length
                || width <= 0
                || height <= 0)
            return ScoreClockContextSource.unavailable(
                    finalScore.notes().size(),
                    finalScore.measures().size(),
                    finalScore.rests().size());
        String raw = digest(gray), semantic = digest(labels);
        String annotationRaw =
                annotationGray != null && annotationGray.length == gray.length
                        ? digest(annotationGray)
                        : "";
        var origin = new Origin("", -1, 0, width, height, raw, semantic, annotationRaw);
        var words = new ArrayList<PartWord>();
        var numbers = new ArrayList<NumberRead>();
        var meters = new ArrayList<MeterRead>();
        var values = new ArrayList<PrintedValue>();
        var ledgers = new ArrayList<GlyphLedger>();
        boolean exhausted = false;
        if (parts != null
                && !annotationRaw.isEmpty()
                && parts.data().rawSha().equals(annotationRaw)) {
            var data = parts.data();
            for (var word : data.words()) {
                var row = data.rows().stream().filter(r -> r.row() == word.system()).findFirst();
                int first = row.isPresent() ? row.get().measures().get(0) : -1;
                int after =
                        row.isPresent()
                                ? row.get().measures().get(row.get().measures().size() - 1) + 1
                                : -1;
                var role =
                        word.status().equals("UNKNOWN_INVALID_OR_ABSENT_READER_BOUNDS")
                                        || word.system() == -2
                                ? WordRole.UNKNOWN_LABEL_RIVAL
                                : word.system() < 0
                                        ? WordRole.OUTSIDE_PART_SLOT
                                        : word.canonicalPart().isEmpty()
                                                ? WordRole.UNKNOWN_LABEL_RIVAL
                                                : WordRole.PART_LABEL;
                Status status =
                        word.status().equals("SOURCE_BOUND_PART_LABEL_READING")
                                ? Status.OBSERVATION
                                : word.status().startsWith("UNKNOWN")
                                        ? Status.UNKNOWN
                                        : Status.OBSERVATION;
                words.add(
                        new PartWord(
                                word.ordinal(),
                                Math.max(-1, word.system()),
                                first,
                                after,
                                word.text(),
                                digest(word.text().getBytes(StandardCharsets.UTF_8)),
                                word.canonicalPart(),
                                box(word.left(), word.top(), word.right(), word.bottom()),
                                role,
                                word.sampledInk().size(),
                                word.sampledInk().isEmpty() ? "" : members(word.sampledInk()),
                                status));
            }
            for (var observed : data.numbers()) {
                var route = observed.route();
                var token = route.token();
                NumberRoute kind =
                        route.disposition().equals("ACTUAL_SINGLE_LATER_ANCHOR_MEASURE_ONE_RETURN")
                                ? NumberRoute.SINGLE_MEASURE_ONE_RETURN
                                : route.assigned()
                                        ? NumberRoute.TRUSTED_ASSIGNED
                                        : route.headerRow() < 0
                                                ? NumberRoute.OUTSIDE_HEADER
                                                : NumberRoute.UNASSIGNED_HEADER_OBSERVATION;
                numbers.add(
                        new NumberRead(
                                route.ordinal(),
                                route.headerRow(),
                                Integer.toString(token.value()),
                                token.value(),
                                route.originCandidate(),
                                box(token.left(), token.top(), token.right(), token.bottom()),
                                kind,
                                observed.status().startsWith("BOUND")
                                        ? Status.OBSERVATION
                                        : Status.UNKNOWN));
            }
            for (int index : data.unboundHeaderWords()) {
                var word = data.words().get(index);
                numbers.add(
                        new NumberRead(
                                data.numbers().size() + index,
                                word.headerSystem(),
                                word.text(),
                                0,
                                0,
                                box(word.left(), word.top(), word.right(), word.bottom()),
                                NumberRoute.UNREAD_HEADER_WORD,
                                Status.UNREAD));
            }
        }
        if (meter != null && meter.rawSha().equals(raw) && meter.labelSha().equals(semantic)) {
            if (analysis != null)
                meter =
                        meter.withLocalAccidentalExclusions(
                                labels,
                                gray,
                                width,
                                height,
                                finalScore.measures(),
                                analysis.clockProducerSource().localAccidentals(),
                                finalScore.notes());
            for (var read : meter.readings()) {
                var crop = read.crop();
                int numerator = 0, denominator = 0;
                if (read.text().matches("[0-9]{1,3}/[0-9]{1,3}")) {
                    var split = read.text().split("/");
                    numerator = Integer.parseInt(split[0]);
                    denominator = Integer.parseInt(split[1]);
                    if (numerator > 128 || denominator > 128) {
                        numerator = 0;
                        denominator = 0;
                    }
                }
                boolean bounded =
                        read.status().equals("BOUND_READER_SAMPLE_NOT_COMPONENT_OWNERSHIP");
                meters.add(
                        new MeterRead(
                                read.ordinal(),
                                read.measure(),
                                read.text(),
                                numerator,
                                denominator,
                                crop == null
                                        ? new RasterBox(-1, -1, -1, -1)
                                        : new RasterBox(
                                                crop.left(),
                                                crop.top(),
                                                crop.right(),
                                                crop.bottom()),
                                crop == null ? -1 : crop.firstLine(),
                                crop == null ? 0 : crop.gap(),
                                read.votes(),
                                read.sampledSupport().size(),
                                read.sampledSupport().isEmpty()
                                        ? ""
                                        : members(read.sampledSupport()),
                                !read.exclusionModel().isEmpty()
                                        ? MeterRoute.HEADER_MODEL_EXCLUSION
                                        : crop == null
                                                ? MeterRoute.RASTER_UNBOUNDED
                                                : read.text().isBlank()
                                                        ? MeterRoute.UNREAD_CANDIDATE
                                                        : MeterRoute.READER_BOUNDED,
                                read.acceptedByOriginalProducer(),
                                !read.exclusionModel().isEmpty() || bounded
                                        ? Status.OBSERVATION
                                        : read.text().isBlank() ? Status.UNREAD : Status.UNKNOWN));
            }
        }
        var producer = analysis == null ? null : analysis.clockProducerSource();
        if (producer != null
                && producer.rawSha().equals(raw)
                && producer.labelSha().equals(semantic)) {
            // Exact full-record equality is allowed only with a unique original and final owner.
            // Changed or ambiguous downstream records retain missing authority rather than
            // borrowing it.
            var used = new HashSet<Integer>();
            var originalCounts = new HashMap<ScoreNoteEvent, Integer>();
            var finalOwners = new HashMap<ScoreNoteEvent, Integer>();
            for (var n : analysis.notes()) originalCounts.merge(n, 1, Integer::sum);
            for (int n = 0; n < finalScore.notes().size(); n++) {
                var event = finalScore.notes().get(n);
                if (finalOwners.containsKey(event)) finalOwners.put(event, -2);
                else finalOwners.put(event, n);
            }
            int valueBudget = 100000 - words.size() - numbers.size() - meters.size() - 16;
            if (producer.printedValues().size() > valueBudget) exhausted = true;
            for (var read : producer.printedValues()) {
                if (exhausted) break;
                if (read.note() >= analysis.notes().size()) continue;
                var original = analysis.notes().get(read.note());
                if (originalCounts.getOrDefault(original, 0) != 1) continue;
                int owner = finalOwners.getOrDefault(original, -1);
                if (owner < 0 || !used.add(owner)) continue;
                var printed = read.writtenValue();
                ValueRoute route =
                        printed != null
                                ? ValueRoute.FILLED_PHYSICAL_TREMOLO_ENDPOINT
                                : read.reachedOpaqueDefaultQuarter()
                                        ? ValueRoute.OPAQUE_DEFAULT
                                        : ValueRoute.UNKNOWN;
                int flags = 0;
                for (var admission : read.admissions())
                    flags |= admission.equals("TRANSPORTED_PAIRED_BEAM_PATH_CONSULTED") ? 1 : 2;
                values.add(
                        new PrintedValue(
                                owner,
                                original.measureIndex(),
                                original.staffIndex(),
                                original.staffCount(),
                                printed != null ? ValueKind.QUARTER : ValueKind.UNKNOWN,
                                read.finalValue(),
                                original.augmentationDots(),
                                read.finalBeams(),
                                route,
                                read.detectorExit(),
                                flags,
                                printed == null ? 0 : printed.selectedMembers(),
                                printed == null ? 0 : printed.parentMembers(),
                                printed == null ? 0 : printed.parentLeaves(),
                                0,
                                printed == null ? "" : printed.selectedSha256(),
                                printed == null ? "" : printed.parentSha256(),
                                printed == null
                                        ? Model.NONE
                                        : Model.GEOMETRIC_ELLIPSE_SOURCE_CONTACT,
                                printed == null ? Status.OBSERVATION : Status.SOURCE_PREMISE));
            }
            var accounting = producer.accounting();
            if (accounting.exported() > 2_000_000) exhausted = true;
            else if (accounting.population() >= 0 && accounting.unique() >= 0) {
                var all = new ArrayList<Integer>();
                for (var emission : producer.emissions()) all.addAll(emission.pixels());
                Collections.sort(all);
                long unknown =
                        Math.max(
                                0,
                                accounting.unique()
                                        - accounting.assignedNotes()
                                        - accounting.provedNonNotes());
                ledgers.add(
                        new GlyphLedger(
                                -1,
                                accounting.semanticTarget(),
                                accounting.population(),
                                accounting.unique(),
                                accounting.assignedNotes(),
                                accounting.provedNonNotes(),
                                0,
                                unknown,
                                members(all),
                                "GENUINE_NOTEHEAD_LABEL_EMISSIONS_NOT_ALL_RHYTHMIC_LABELS",
                                Status.OBSERVATION));
            }
        } else if (analysis != null
                && analysis.clockContextSource().origin().rasterSha256().equals(raw)
                && analysis.clockContextSource().origin().labelsSha256().equals(semantic)) {
            // Managed native analysis transports the compact issuer records, not private objects.
            // Rebind only unique, entirely unchanged final note records in this same raster frame.
            var context = analysis.clockContextSource();
            var counts = new HashMap<ScoreNoteEvent, Integer>();
            var finalOwners = new HashMap<ScoreNoteEvent, Integer>();
            for (var n : analysis.notes()) counts.merge(n, 1, Integer::sum);
            for (int n = 0; n < finalScore.notes().size(); n++) {
                var event = finalScore.notes().get(n);
                if (finalOwners.containsKey(event)) finalOwners.put(event, -2);
                else finalOwners.put(event, n);
            }
            for (var value : context.printedValues()) {
                var event = analysis.notes().get(value.noteIndex());
                int owner = finalOwners.getOrDefault(event, -1);
                if (owner < 0 || counts.getOrDefault(event, 0) != 1) continue;
                values.add(
                        new PrintedValue(
                                owner,
                                value.measure(),
                                value.staff(),
                                value.staffs(),
                                value.kind(),
                                value.baseBeats(),
                                value.dots(),
                                value.beams(),
                                value.route(),
                                value.originalExit(),
                                value.admissionFlags(),
                                value.selectedMembers(),
                                value.parentMembers(),
                                value.parentLeaves(),
                                value.residualMembers(),
                                value.selectedSha256(),
                                value.parentSha256(),
                                value.model(),
                                value.status()));
            }
            ledgers.addAll(context.glyphLedgers());
            exhausted =
                    context.missing().stream()
                            .anyMatch(m -> m.requirement() == Requirement.CAPTURE_BUDGET);
        }
        var missing = new ArrayList<Missing>();
        // Local observed words, numbers, meters, and selected printed values do not close these.
        for (var requirement : Requirement.values())
            if (requirement != Requirement.CAPTURE_BUDGET)
                missing.add(new Missing(requirement, -1));
        if (exhausted) missing.add(new Missing(Requirement.CAPTURE_BUDGET, -1));
        return new ScoreClockContextSource(
                origin,
                finalScore.notes().size(),
                finalScore.measures().size(),
                finalScore.rests().size(),
                words,
                numbers,
                meters,
                values,
                ledgers,
                List.of(),
                List.of(),
                List.of(),
                missing);
    }
}
