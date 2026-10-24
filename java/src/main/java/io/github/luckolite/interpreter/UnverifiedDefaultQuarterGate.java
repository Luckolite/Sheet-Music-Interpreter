// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Revision of the opaque default-quarter admission, not a reinterpretation of
 * that old fallback as conic evidence. Every unsupported/unknown case is KEEP. */
final class UnverifiedDefaultQuarterGate {
    static final class Proof {
        private final ExactComponentMembers.Collector source;
        private final Object candidate;
        private final ExactComponentMembers.ProducerFootprint target;
        private final List<ExactComponentMembers.ProducerFootprint> receiverDependencies;
        private final CompleteSlurExplanation.Geometry geometry;

        private Proof(
                ExactComponentMembers.Collector s,
                Object c,
                ExactComponentMembers.ProducerFootprint t,
                List<ExactComponentMembers.ProducerFootprint> r,
                CompleteSlurExplanation.Geometry g) {
            source = s;
            candidate = c;
            target = t;
            receiverDependencies = List.copyOf(r);
            geometry = g;
        }

        boolean matches(ExactComponentMembers.Collector s, Object c) {
            return source == s && candidate == c;
        }

        boolean exclusiveOwnership() {
            return false;
        }

        int explainedTargetMembers() {
            return geometry.target().length;
        }

        int usedMembers() {
            return geometry.used().length;
        }
    }

    static Proof prove(
            ExactComponentMembers.Collector source,
            Object candidate,
            OmrScoreInterpreter.PrintedValueDecision value,
            List<String> admissions,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<?> nearbyHeads,
            float nativeTop,
            float gap,
            ExistingHeadSubsetPreserver.Admission existingHeadAdmission,
            SlurEvidenceBudget budget) {
        if (source == null
                || value == null
                || admissions == null
                || nearbyHeads == null
                || existingHeadAdmission == null
                || !value.reachedOpaqueDefaultQuarter()
                || value.inputBeamCount() != 0
                || !value.matches(source, candidate)
                || !source.sameInputFrame(labels, gray, width, height)) return null;
        for (String fact : value.observations())
            if (!fact.equals("NO_OPEN_CENTER_OBSERVED_NOT_A_FILLED_PROOF")
                    && !fact.equals("NO_SEMANTIC_STEM_OBSERVED")) return null;
        for (String admission : admissions)
            if (!admission.equals("TRANSPORTED_PAIRED_BEAM_PATH_CONSULTED")) return null;
        // These tokens come from true original/split production, never a bbox
        // adapter. A missing nearby token or non-pocket competing head is KEEP.
        var exactBudget = new ExactComponentMembers.Budget(budget.work, budget.bytes);
        var target = source.producerFootprint(candidate, exactBudget);
        charge(budget, exactBudget);
        if (target == null) return null;
        var targetToken = source.registeredProbeToken(candidate);
        if (targetToken == null) return null;
        var receiverPixels = new ArrayList<int[]>();
        var receivers = new ArrayList<ExactComponentMembers.ProducerFootprint>();
        for (Object other : nearbyHeads) {
            if (other == candidate) continue;
            var token = source.registeredProbeToken(other);
            if (token == null || !token.authenticates(gray, width, height)) return null;
            var stats = token.stats();
            if (!WholeHeadPocketEvidence.proved(
                    gray,
                    width,
                    height,
                    stats.minX(),
                    stats.minY(),
                    stats.maxX(),
                    stats.maxY(),
                    gap)) return null;
            exactBudget = new ExactComponentMembers.Budget(budget.work, budget.bytes);
            var receiver = source.producerFootprint(other, exactBudget);
            charge(budget, exactBudget);
            if (receiver == null) return null;
            receivers.add(receiver);
            receiverPixels.add(receiver.pixels());
        }
        if (receivers.isEmpty()) return null;
        int[] members = target.pixels();
        var full = targetToken.stats();
        var region =
                new ExistingHeadSubsetPreserver.Region(
                        full.area(),
                        full.minX(),
                        full.maxX(),
                        full.minY(),
                        full.maxY(),
                        full.centerX(),
                        full.centerY());
        if (existingHeadAdmission.supported(region, members)) return null;
        var geometry =
                CompleteSlurExplanation.propose(
                        gray, width, height, members, receiverPixels, nativeTop, gap, budget);
        if (ExistingHeadSubsetPreserver.supported(
                members, width, gap, budget, existingHeadAdmission)) return null;
        if (FaintHeadBodyPreserver.supported(
                gray, width, height, members, gap, geometry::observedCarrier, budget)) return null;
        // All complete-used members/profile dependencies were read from these
        // exact arrays. Rehash at the final boundary; stale bytes refuse reuse.
        exactBudget = new ExactComponentMembers.Budget(budget.work, budget.bytes);
        boolean unchanged = source.revalidateForExport(exactBudget);
        charge(budget, exactBudget);
        return unchanged ? new Proof(source, candidate, target, receivers, geometry) : null;
    }

    private static void charge(
            SlurEvidenceBudget destination, ExactComponentMembers.Budget source) {
        destination.use(destination.work - source.work, destination.bytes - source.bytes);
    }
}
