// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Genuine original producer and detector through the changed caller's own gate. */
public final class ProceduralNormalGateOriginal {
    static Method method(String n, int arity) {
        for (var m : OmrScoreInterpreter.class.getDeclaredMethods())
            if (m.getName().equals(n) && m.getParameterCount() == arity) {
                m.setAccessible(true);
                return m;
            }
        throw new AssertionError(n);
    }

    static int area(Object o) throws Exception {
        var f = o.getClass().getDeclaredField("area");
        f.setAccessible(true);
        return f.getInt(o);
    }

    public static void main(String[] args) throws Exception {
        var scene = CompleteSlurProceduralOriginal.scene(0, false, false, args.length > 0);
        byte[] g = scene.g(), a = new byte[g.length];
        for (int p : scene.target()) a[p] = 2;
        for (int p : scene.receiver()) a[p] = 2;
        var collector =
                new ExactComponentMembers.Collector(
                        g,
                        a,
                        240,
                        240,
                        new ExactComponentMembers.Budget(1000000, 4096),
                        new ExactComponentMembers.Budget(64000000, 30000000));
        var heads =
                (List<?>)
                        method("findComponents", 7)
                                .invoke(
                                        null,
                                        a,
                                        240,
                                        240,
                                        OmrMeasurePostProcessor.NOTEHEAD,
                                        g,
                                        new IdentityHashMap<Object, RestFigureOrigins.HeadOrigin>(),
                                        collector);
        if (!collector.seal(new ExactComponentMembers.Budget(1000000, 4096)))
            throw new AssertionError("producer");
        Object target = null;
        for (Object head : heads) {
            collector.acceptedProbeHead(head, 0, 0);
            if (area(head) == scene.target().length) target = head;
        }
        if (target == null) throw new AssertionError("target producer vector");
        Class<?> sc =
                Arrays.stream(OmrScoreInterpreter.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("Staff"))
                        .findFirst()
                        .orElseThrow();
        var constructor = sc.getDeclaredConstructor(float.class, float.class, float.class);
        constructor.setAccessible(true);
        Object staff = constructor.newInstance(80f, 160f, 20f);
        var value =
                method("detectPrintedValue", 8).invoke(null, a, g, 240, 240, target, 20f, 0, heads);
        var typed = (OmrScoreInterpreter.PrintedValueDecision) value;
        ExistingHeadSubsetPreserver.Admission noInjectedHead = (r, p) -> false;
        for (String veto : List.of("RECOVERED_FILLED_FRAGMENT_OVERRIDE", "FUTURE_HEAD_SHAPE_PROOF"))
            if (UnverifiedDefaultQuarterGate.prove(
                            collector,
                            target,
                            typed,
                            List.of(veto),
                            a,
                            g,
                            240,
                            240,
                            heads,
                            80,
                            20,
                            noInjectedHead,
                            new SlurEvidenceBudget(0, 0))
                    != null) throw new AssertionError("positive/unrecognized route must KEEP");
        if (UnverifiedDefaultQuarterGate.prove(
                        collector,
                        new Object(),
                        typed,
                        List.of(),
                        a,
                        g,
                        240,
                        240,
                        heads,
                        80,
                        20,
                        noInjectedHead,
                        new SlurEvidenceBudget(0, 0))
                != null) throw new AssertionError("foreign candidate must KEEP");
        if (UnverifiedDefaultQuarterGate.prove(
                        collector,
                        target,
                        typed,
                        List.of(),
                        a.clone(),
                        g,
                        240,
                        240,
                        heads,
                        80,
                        20,
                        noInjectedHead,
                        new SlurEvidenceBudget(0, 0))
                != null) throw new AssertionError("foreign semantic frame must KEEP");
        if (UnverifiedDefaultQuarterGate.prove(
                        collector,
                        target,
                        typed,
                        List.of(),
                        a,
                        g.clone(),
                        240,
                        240,
                        heads,
                        80,
                        20,
                        noInjectedHead,
                        new SlurEvidenceBudget(0, 0))
                != null) throw new AssertionError("foreign raw frame must KEEP");
        if (UnverifiedDefaultQuarterGate.prove(
                        collector,
                        target,
                        typed,
                        List.of("TRANSPORTED_PAIRED_BEAM_PATH_CONSULTED"),
                        a,
                        g,
                        240,
                        240,
                        heads,
                        80,
                        20,
                        noInjectedHead,
                        new SlurEvidenceBudget(0, 0))
                != null)
            throw new AssertionError(
                    "neutral consultation is not positive proof and zero-resource must KEEP");
        for (Object head : heads)
            if (head != target) {
                var t = collector.registeredProbeToken(head).stats();
                System.out.println(
                        "receiverPocket="
                                + WholeHeadPocketEvidence.proved(
                                        g, 240, 240, t.minX(), t.minY(), t.maxX(), t.maxY(), 20));
            }
        System.out.println(
                "targetBodyProposal="
                        + ProposedFilledBodyCaller.inspect(a, g, 240, 240, scene.target(), 20)
                                .core());
        try {
            var geo =
                    CompleteSlurExplanation.propose(
                            g,
                            240,
                            240,
                            scene.target(),
                            List.of(scene.receiver()),
                            80,
                            20,
                            new SlurEvidenceBudget(180000000, 30000000));
            System.out.println(
                    "geometryComplete=true faint="
                            + FaintHeadBodyPreserver.supported(
                                    g,
                                    240,
                                    240,
                                    scene.target(),
                                    20,
                                    geo::observedCarrier,
                                    new SlurEvidenceBudget(180000000, 30000000)));
        } catch (SlurEvidenceBudget.Unknown unknown) {
            System.out.println("geometry=" + unknown.getMessage());
        }
        boolean pruned =
                (Boolean)
                        method("completeDefaultQuarterSlur", 10)
                                .invoke(
                                        null, collector, a, g, 240, 240, target, staff, heads,
                                        value, List.of());
        if (pruned) throw new AssertionError("supported/unknown original must KEEP");
        System.out.println(
                "proceduralGenuineProducerHeads="
                        + heads.size()
                        + " target="
                        + area(target)
                        + " detectorExit="
                        + ((OmrScoreInterpreter.PrintedValueDecision) value).exit()
                        + " gatePruned="
                        + pruned);
    }
}
