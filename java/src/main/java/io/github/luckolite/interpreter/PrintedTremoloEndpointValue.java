// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Printed-value premise for detector-qualified tremolo endpoints. Complete producer membership
 * is a source fact. The ellipse/contact stage remains a geometric model; clock authority also
 * requires independent assembled part, meter, voice, rest, and synchronization evidence.
 */
final class PrintedTremoloEndpointValue {
    record WrittenValue(
            String route,
            float baseBeats,
            int selectedMembers,
            int parentMembers,
            int parentLeaves,
            String selectedSha256,
            String parentSha256,
            String contourEvidence,
            List<String> affirmativePredicates) {
        WrittenValue {
            affirmativePredicates = List.copyOf(affirmativePredicates);
        }
    }

    static final class Premise {
        private final ExactComponentMembers.Collector source;
        private final Object identity;
        private final PairedTremoloInk.Head head;
        private final ExactComponentMembers.ProducerPartition partition;
        private final LobeExtentProposal.Core model;
        private final boolean matches, completeInterior;
        private final List<Integer> contraryMembers;
        private final String refusal;

        private Premise(
                ExactComponentMembers.Collector source,
                Object identity,
                PairedTremoloInk.Head head,
                ExactComponentMembers.ProducerPartition partition,
                LobeExtentProposal.Core model,
                boolean matches,
                boolean complete,
                List<Integer> contrary,
                String refusal) {
            this.source = source;
            this.identity = identity;
            this.head = head;
            this.partition = partition;
            this.model = model;
            this.matches = matches;
            completeInterior = complete;
            contraryMembers = List.copyOf(contrary);
            this.refusal = refusal;
        }

        String refusal() {
            return refusal;
        }

        List<Integer> contraryMembers() {
            return contraryMembers;
        }
    }

    static Map<Object, Premise> capture(
            PairedTremoloInk.Discovery discovery,
            PairedTremoloInk.Pair pair,
            Map<Integer, ?> components,
            ExactComponentMembers.Collector collector,
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            ExactComponentMembers.Budget budget) {
        if (discovery == null
                || pair == null
                || collector == null
                || budget == null
                || labels == null
                || gray == null
                || labels.length != (long) w * h
                || gray.length != labels.length) return Map.of();
        var scope = discovery.endpointScope(pair, gray, w, h);
        if (scope == null
                || !scope.binds(discovery, pair, collector, components, gray, w, h)
                || !collector.revalidateForExport(budget)
                || !budget.take(70_000_000 + 2L * labels.length, 16L * 1024 * 1024))
            return Map.of();
        var partitions = new ArrayList<ExactComponentMembers.ProducerPartition>();
        var byHead = new IdentityHashMap<Object, ExactComponentMembers.ProducerPartition>();
        for (var head : scope.endpoints()) {
            Object identity = components.get(head.id());
            var partition = collector.producerPartition(identity, budget);
            if (partition == null) return Map.of();
            partitions.add(partition);
            byHead.put(identity, partition);
        }
        float gap = (float) scope.endpoints().get(0).gap();
        if (scope.endpoints().stream()
                .anyMatch(
                        head ->
                                Double.doubleToRawLongBits(head.gap())
                                        != Double.doubleToRawLongBits((double) gap)))
            return Map.of();
        var models =
                LobeExtentProposal.deriveCohort(
                        labels, gray, w, h, partitions, gap, new StringBuilder());
        var result = new IdentityHashMap<Object, Premise>();
        for (var head : scope.endpoints()) {
            Object identity = components.get(head.id());
            var partition = byHead.get(identity);
            var st = head.memberToken().stats();
            boolean matches =
                    FilledSparseBeamHead.matches(gray, w, h, st.centerX(), st.centerY(), gap);
            boolean complete =
                    FilledSparseBeamHead.completeInterior(
                            gray,
                            w,
                            h,
                            new SymbolStemHeadRecovery.Head(
                                    st.minX(),
                                    st.minY(),
                                    st.maxX(),
                                    st.maxY(),
                                    st.area(),
                                    st.centerX(),
                                    st.centerY()),
                            gap);
            var model = models == null ? null : models.get(identity);
            String refusal = "";
            var contrary = new ArrayList<Integer>();
            int paper =
                    InteriorThicknessOwnership.observedPaper(
                            gray, w, h, st.minX(), st.minY(), st.maxX(), st.maxY(), gap);
            for (var leaf : partition.leaves())
                if (leaf.identity() == identity)
                    for (int pixel : leaf.pixels())
                        if (paper < 210 || (gray[pixel] & 255) > paper - 25) contrary.add(pixel);
            if (!partition.residual().isEmpty()) refusal = "UNKNOWN_PARENT_RESIDUAL";
            else if (model == null) refusal = "UNKNOWN_BODY_CONTACT_OR_EXTERNAL_DEPENDENCY_MODEL";
            else if (!matches || !complete) refusal = "UNKNOWN_OR_CONTRARY_FILLED_INTERIOR";
            else if (!contrary.isEmpty()) refusal = "UNEXPLAINED_LIGHT_SOURCE_MEMBERS";
            else if (WholeHeadPocketEvidence.proved(
                            gray, w, h, st.minX(), st.minY(), st.maxX(), st.maxY(), gap)
                    || WholeHeadPocketEvidence.hasEnclosedOpening(
                            gray,
                            w,
                            h,
                            st.minX(),
                            st.minY(),
                            st.maxX(),
                            st.maxY(),
                            st.centerX(),
                            st.centerY(),
                            gap)
                    || ShadedHollowPocket.proved(
                            gray, w, h, st.minX(), st.minY(), st.maxX(), st.maxY(), gap))
                refusal = "CONTRARY_CLOSED_OR_RELATIVE_LIGHT_COUNTER";
            result.put(
                    identity,
                    new Premise(
                            collector, identity, head, partition, model, matches, complete,
                            contrary, refusal));
        }
        return Collections.unmodifiableMap(result);
    }

    private static String memberDigest(List<Integer> pixels) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            for (int pixel : pixels) {
                digest.update((byte) (pixel >>> 24));
                digest.update((byte) (pixel >>> 16));
                digest.update((byte) (pixel >>> 8));
                digest.update((byte) pixel);
            }
            return RasterEvidenceIdentity.hex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static WrittenValue issue(
            Premise premise,
            OmrScoreInterpreter.PrintedValueDecision decision,
            ScoreNoteEvent event,
            ExactComponentMembers.Collector collector,
            Object identity) {
        if (premise == null
                || decision == null
                || event == null
                || premise.source != collector
                || premise.identity != identity
                || !premise.refusal.isEmpty()
                || !premise.matches
                || !premise.completeInterior
                || premise.model == null
                || !premise.contraryMembers.isEmpty()
                || !decision.matches(collector, identity)
                || !decision.unchangedUnrecoveredQuarterCaller()
                || premise.head.memberToken() != collector.registeredProbeToken(identity)
                || event.measureIndex() != premise.head.measure()
                || event.staffIndex() != premise.head.staff()
                || event.unbeamedDurationBeats() != 1f
                || event.beamCount() != 0
                || event.augmentationDots() != 0
                || event.kind() != ScoreNoteEvent.Kind.PITCHED
                || event.visualSize() != ScoreNoteEvent.VisualSize.FULL
                || (event.articulations() & NoteOrnament.GRACE) != 0) return null;
        return new WrittenValue(
                "AFFIRMATIVE_FILLED_INTERIOR_SOURCE_BOUND_ENDPOINT_CONTACT",
                1f,
                premise.head.memberToken().stats().area(),
                premise.partition.parentPixels().size(),
                premise.partition.leaves().size(),
                memberDigest(
                        premise.partition.leaves().stream()
                                .filter(leaf -> leaf.identity() == identity)
                                .findFirst()
                                .orElseThrow()
                                .pixels()),
                memberDigest(premise.partition.parentPixels()),
                "GEOMETRIC_ELLIPSE_AND_SOURCE_BOUND_CONTACT_MODEL",
                List.of(
                        "FilledSparseBeamHead.matches",
                        "FilledSparseBeamHead.completeInterior",
                        "GENUINE_COMPLETE_PARENT_CHILD_PARTITION_NO_RESIDUAL",
                        "QUALIFIED_PHYSICAL_TREMOLO_ENDPOINT"));
    }
}
