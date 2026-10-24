// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Exact producer facts retained before recognition drops component identities.
 * This ledger is not a complete rhythmic interpretation and grants no clock policy.
 */
final class ScoreClockProducerSource {
    /** Parent/original -2 denotes a captured ancestor outside this emitted-object table. */
    record Emission(
            int index,
            int parent,
            int original,
            List<Integer> pixels,
            List<Integer> finalNoteOwners,
            String disposition,
            NonNoteRole nonNoteRole) {
        Emission {
            pixels = List.copyOf(pixels);
            finalNoteOwners = List.copyOf(finalNoteOwners);
        }
    }

    record NonNoteRole(String family, int body, int core, int staff, int fringe) {}

    private final String rawSha, labelSha;
    private final List<Emission> emissions;

    record MemberAccounting(
            int semanticTarget,
            long population,
            long exported,
            long unique,
            long assignedNotes,
            long provedNonNotes) {}

    record PrintedValue(
            int note,
            int emission,
            String status,
            String detectorExit,
            float detectorValue,
            int detectorBeams,
            float finalValue,
            int finalBeams,
            boolean reachedOpaqueDefaultQuarter,
            List<String> observations,
            List<String> admissions,
            PrintedTremoloEndpointValue.WrittenValue writtenValue) {
        PrintedValue {
            observations = List.copyOf(observations);
            admissions = List.copyOf(admissions);
        }
    }

    private final List<PrintedValue> printedValues;

    List<PrintedValue> printedValues() {
        return printedValues;
    }

    private SourceLocalAccidental localAccidentals = SourceLocalAccidental.unavailable();

    SourceLocalAccidental localAccidentals() {
        return localAccidentals == null ? SourceLocalAccidental.unavailable() : localAccidentals;
    }

    ScoreClockProducerSource withLocalAccidentals(SourceLocalAccidental source) {
        var answer =
                new ScoreClockProducerSource(
                        rawSha, labelSha, emissions, finalNotes, accounting, printedValues);
        answer.localAccidentals = Objects.requireNonNull(source);
        return answer;
    }

    private final int finalNotes;
    private final MemberAccounting accounting;

    private ScoreClockProducerSource(
            String raw,
            String labels,
            List<Emission> entries,
            int notes,
            MemberAccounting accounting) {
        this(raw, labels, entries, notes, accounting, List.of());
    }

    private ScoreClockProducerSource(
            String raw,
            String labels,
            List<Emission> entries,
            int notes,
            MemberAccounting accounting,
            List<PrintedValue> values) {
        printedValues = List.copyOf(values);
        rawSha = raw;
        labelSha = labels;
        emissions = List.copyOf(entries);
        finalNotes = notes;
        this.accounting = accounting;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScoreClockProducerSource x
                && finalNotes == x.finalNotes
                && rawSha.equals(x.rawSha)
                && labelSha.equals(x.labelSha)
                && emissions.equals(x.emissions)
                && accounting.equals(x.accounting)
                && printedValues.equals(x.printedValues)
                && localAccidentals().equals(x.localAccidentals());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                rawSha,
                labelSha,
                emissions,
                finalNotes,
                accounting,
                printedValues,
                localAccidentals());
    }

    String rawSha() {
        return rawSha;
    }

    String labelSha() {
        return labelSha;
    }

    List<Emission> emissions() {
        return emissions;
    }

    int finalNotes() {
        return finalNotes;
    }

    MemberAccounting accounting() {
        return accounting;
    }

    static ScoreClockProducerSource unavailable(int notes) {
        return new ScoreClockProducerSource(
                "", "", List.of(), notes, new MemberAccounting(2, -1, 0, 0, 0, 0));
    }

    /** Capture all supplied traversal emissions, including filtered and unbound components.
     * Only object identity from the real producer binds a final note. No bbox matching.
     */
    static ScoreClockProducerSource capture(
            ExactComponentMembers.Collector collector,
            List<?> traversal,
            List<?> finalHeadOwners,
            int notes,
            ExactComponentMembers.Budget budget) {
        return capture(collector, traversal, finalHeadOwners, notes, budget, Map.of());
    }

    static ScoreClockProducerSource capture(
            ExactComponentMembers.Collector collector,
            List<?> traversal,
            List<?> finalHeadOwners,
            int notes,
            ExactComponentMembers.Budget budget,
            Map<?, ObservedFiniteRasterRoles.ComponentProof> proofs) {
        return capture(
                collector,
                traversal,
                finalHeadOwners,
                notes,
                budget,
                proofs,
                Map.of(),
                Map.of(),
                List.of());
    }

    static ScoreClockProducerSource capture(
            ExactComponentMembers.Collector collector,
            List<?> traversal,
            List<?> finalHeadOwners,
            int notes,
            ExactComponentMembers.Budget budget,
            Map<?, ObservedFiniteRasterRoles.ComponentProof> proofs,
            Map<?, OmrScoreInterpreter.PrintedValueDecision> decisions,
            Map<?, List<String>> admissions,
            List<ScoreNoteEvent> finalEvents) {
        return capture(
                collector,
                traversal,
                finalHeadOwners,
                notes,
                budget,
                proofs,
                decisions,
                admissions,
                finalEvents,
                Map.of());
    }

    static ScoreClockProducerSource capture(
            ExactComponentMembers.Collector collector,
            List<?> traversal,
            List<?> finalHeadOwners,
            int notes,
            ExactComponentMembers.Budget budget,
            Map<?, ObservedFiniteRasterRoles.ComponentProof> proofs,
            Map<?, OmrScoreInterpreter.PrintedValueDecision> decisions,
            Map<?, List<String>> admissions,
            List<ScoreNoteEvent> finalEvents,
            Map<?, PrintedTremoloEndpointValue.Premise> printedEndpointPremises) {
        var ids = new IdentityHashMap<Object, Integer>();
        for (Object object : traversal) if (!ids.containsKey(object)) ids.put(object, ids.size());
        var owners = new IdentityHashMap<Object, List<Integer>>();
        for (int n = 0; n < finalHeadOwners.size(); n++) {
            Object object = finalHeadOwners.get(n);
            if (!ids.containsKey(object)) ids.put(object, ids.size());
            owners.computeIfAbsent(object, ignored -> new ArrayList<>()).add(n);
        }
        var objects = new ArrayList<Object>(Collections.nCopies(ids.size(), null));
        for (var entry : ids.entrySet()) objects.set(entry.getValue(), entry.getKey());
        boolean snapshot = collector.revalidateForExport(budget);
        var entries = new ArrayList<Emission>();
        String raw = "", labels = "";
        for (int i = 0; i < objects.size(); i++) {
            Object object = objects.get(i);
            var members = snapshot ? collector.producerFootprint(object, budget) : null;
            List<Integer> attached = owners.getOrDefault(object, List.of());
            if (members == null) {
                entries.add(
                        new Emission(
                                i,
                                -1,
                                -1,
                                List.of(),
                                attached,
                                "UNKNOWN_MISSING_PRODUCER_TOKEN",
                                null));
                continue;
            }
            raw = members.rawSha();
            labels = members.labelSha();
            int parent =
                    members.parentIdentity() == null
                            ? -1
                            : ids.getOrDefault(members.parentIdentity(), -2);
            int original = ids.getOrDefault(members.originalIdentity(), -2);
            int[] exact = members.pixels();
            if (!budget.take(3L * exact.length, 40L * exact.length + 256 + 8L * attached.size())) {
                entries.add(
                        new Emission(
                                i,
                                -1,
                                -1,
                                List.of(),
                                attached,
                                "UNKNOWN_EXHAUSTED_SPARSE_EXPORT_BUDGET",
                                null));
                continue;
            }
            var pixels = new ArrayList<Integer>(exact.length);
            for (int pixel : exact) pixels.add(pixel);
            var proof = proofs.get(object);
            NonNoteRole role = null;
            if (proof != null
                    && proof.matches(collector.registeredProbeToken(object))
                    && proof.core() + proof.staff() + proof.fringe() == exact.length)
                role =
                        new NonNoteRole(
                                "WHOLE_FINITE_TREMOLO_BODY_COMPONENT",
                                proof.body(),
                                proof.core(),
                                proof.staff(),
                                proof.fringe());
            String disposition =
                    role == null
                            ? (attached.isEmpty()
                                    ? "UNASSIGNED_OR_FILTERED_RHYTHMIC_ROLE_UNKNOWN"
                                    : "FINAL_NOTE_BINDING_RHYTHM_AUTHORITY_UNKNOWN")
                            : (attached.isEmpty()
                                    ? "PROVED_NON_NOTE_FINITE_TREMOLO_BODY"
                                    : "CONFLICTING_FINAL_NOTE_AND_FINITE_BODY_ROLE");
            entries.add(new Emission(i, parent, original, pixels, attached, disposition, role));
        }
        long exported = 0, noteMembers = 0, nonNoteMembers = 0;
        for (var row : entries) {
            exported += row.pixels().size();
            if (!row.finalNoteOwners().isEmpty()) noteMembers += row.pixels().size();
            if (row.nonNoteRole() != null) nonNoteMembers += row.pixels().size();
        }
        long population = snapshot ? collector.targetPopulation((byte) 2, budget) : -1, unique = -1;
        int log = 64 - Long.numberOfLeadingZeros(Math.max(1, exported));
        if (exported <= Integer.MAX_VALUE
                && budget.take(exported * (log + 2L), 4L * exported + 64)) {
            int[] all = new int[(int) exported];
            int at = 0;
            for (var row : entries) for (int pixel : row.pixels()) all[at++] = pixel;
            Arrays.sort(all);
            unique = 0;
            for (int i = 0; i < all.length; i++) if (i == 0 || all[i] != all[i - 1]) unique++;
        }
        var values = new ArrayList<PrintedValue>();
        for (int n = 0; n < notes; n++) {
            Object head = n < finalHeadOwners.size() ? finalHeadOwners.get(n) : null;
            var decision = decisions.get(head);
            var event = n < finalEvents.size() ? finalEvents.get(n) : null;
            int emission = ids.getOrDefault(head, -1);
            boolean bound =
                    snapshot
                            && decision != null
                            && decision.matches(collector, head)
                            && emission >= 0
                            && !entries.get(emission).pixels().isEmpty()
                            && event != null;
            if (!bound) {
                values.add(
                        new PrintedValue(
                                n,
                                emission,
                                "UNKNOWN_MISSING_OR_FOREIGN_VALUE_ROUTE",
                                "",
                                0,
                                0,
                                event == null ? 0 : event.unbeamedDurationBeats(),
                                event == null ? 0 : event.beamCount(),
                                false,
                                List.of(),
                                List.of(),
                                null));
                continue;
            }
            var steps = new ArrayList<String>(admissions.getOrDefault(head, List.of()));
            if (Float.floatToIntBits(event.unbeamedDurationBeats())
                            != Float.floatToIntBits(decision.value())
                    || event.beamCount() != decision.inputBeamCount())
                steps.add("FINAL_EVENT_VALUE_OR_BEAM_DIFFERS_FROM_DETECTOR");
            values.add(
                    new PrintedValue(
                            n,
                            emission,
                            "OBSERVED_ROUTE_NOT_COMPLETE_WRITTEN_VALUE_AUTHORITY",
                            decision.exit().name(),
                            decision.value(),
                            decision.inputBeamCount(),
                            event.unbeamedDurationBeats(),
                            event.beamCount(),
                            decision.reachedOpaqueDefaultQuarter(),
                            decision.observations(),
                            steps,
                            PrintedTremoloEndpointValue.issue(
                                    printedEndpointPremises.get(head),
                                    decision,
                                    event,
                                    collector,
                                    head)));
        }
        return new ScoreClockProducerSource(
                raw,
                labels,
                entries,
                notes,
                new MemberAccounting(2, population, exported, unique, noteMembers, nonNoteMembers),
                values);
    }

    List<String> missingAuthorities() {
        // These facts are not supplied by a component collector or a metadata-only guide.
        return List.of(
                "AUTHENTICATED_SAME_PART_METER",
                "EXHAUSTIVE_RHYTHMIC_GLYPH_DISPOSITIONS",
                "WRITTEN_VALUE_SOURCE_PROOF",
                "COMPLETE_VOICE_AND_REST_OWNERSHIP",
                "INDEPENDENT_SOURCE_SYNCHRONIZATION_ANCHORS");
    }
}
