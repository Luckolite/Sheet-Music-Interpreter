// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Procedural originals only. No score pixels, cached candidate identity or witness. */
public final class DefaultQuarterGuardOriginals {
    static int passed;

    static void check(boolean v, String reason) {
        if (!v) throw new AssertionError(reason);
        passed++;
    }

    static void unknown(Runnable r, String reason) {
        try {
            r.run();
            throw new AssertionError(reason);
        } catch (SlurEvidenceBudget.Unknown expected) {
            passed++;
        }
    }

    static SlurEvidenceBudget budget() {
        return new SlurEvidenceBudget(180000000, 30000000);
    }

    static Method method(String n, int arity) {
        for (var m : OmrScoreInterpreter.class.getDeclaredMethods())
            if (m.getName().equals(n) && m.getParameterCount() == arity) {
                m.setAccessible(true);
                return m;
            }
        throw new AssertionError(n);
    }

    static byte[] rails() {
        byte[] g = new byte[250 * 200];
        Arrays.fill(g, (byte) 255);
        for (int y = 60; y <= 124; y += 16)
            for (int d = 0; d < 2; d++) for (int x = 20; x <= 220; x++) g[(y + d) * 250 + x] = 20;
        for (int y = 60; y <= 125; y++)
            for (int x : new int[] {20, 21, 219, 220}) g[y * 250 + x] = 20;
        return g;
    }

    public static void main(String[] args) throws Exception {
        var g = rails();
        var frame = LiteralStaffCarrierFamily.observe(g, 250, 200, 60, 16, budget());
        check(frame.completeFrames() == 4, "all finite alternatives retained");
        check(frame.observed(76 * 250 + 100), "literal carrier consensus");
        check(!frame.observed(78 * 250 + 100), "paper not rail");
        var broken = g.clone();
        broken[76 * 250 + 100] = (byte) 255;
        broken[77 * 250 + 100] = (byte) 255;
        unknown(
                () -> LiteralStaffCarrierFamily.observe(broken, 250, 200, 60, 16, budget()),
                "broken corridor cannot qualify");
        var six = g.clone();
        for (int x = 20; x <= 220; x++) six[140 * 250 + x] = 20;
        unknown(
                () -> LiteralStaffCarrierFamily.observe(six, 250, 200, 60, 16, budget()),
                "sixth phase retained");
        unknown(
                () ->
                        LiteralStaffCarrierFamily.observe(
                                g, 250, 200, 60, 16, new SlurEvidenceBudget(0, 0)),
                "zero work stays unknown");
        for (Class<?> c :
                new Class<?>[] {
                    UnverifiedDefaultQuarterGate.Proof.class,
                    CompleteSlurExplanation.Geometry.class,
                    OmrScoreInterpreter.PrintedValueDecision.class
                })
            for (var ctor : c.getDeclaredConstructors())
                check(Modifier.isPrivate(ctor.getModifiers()), "authority constructor private");
        // Genuine original traversal, never a source-token reconstructed from bounds.
        int w = 80, h = 70;
        var scene = FaintHeadBodyOriginals.scene(false, false, false, false, 1, 0);
        byte[] raw = scene.gray().clone(), labels = new byte[raw.length];
        for (int p : scene.members()) labels[p] = 2;
        var collector =
                new ExactComponentMembers.Collector(
                        raw,
                        labels,
                        w,
                        h,
                        new ExactComponentMembers.Budget(100000, 10000),
                        new ExactComponentMembers.Budget(1000000, 1000000));
        var origins = new IdentityHashMap<Object, RestFigureOrigins.HeadOrigin>();
        var heads =
                (List<?>)
                        method("findComponents", 7)
                                .invoke(
                                        null,
                                        labels,
                                        w,
                                        h,
                                        OmrMeasurePostProcessor.NOTEHEAD,
                                        raw,
                                        origins,
                                        collector);
        check(heads.size() == 1, "one genuine complete producer head");
        check(collector.seal(new ExactComponentMembers.Budget(100000, 10000)), "producer sealed");
        Object head = heads.get(0);
        var token = collector.acceptedProbeHead(head, 0, 0);
        check(token != null && token.authenticates(raw, w, h), "authentic traversal token");
        check(!token.authenticates(raw.clone(), w, h), "foreign equal raw array refused");
        check(!collector.sameInputFrame(labels.clone(), raw, w, h), "foreign equal mask refused");
        var footprint =
                collector.producerFootprint(head, new ExactComponentMembers.Budget(100000, 100000));
        check(
                footprint != null && footprint.pixels().length == scene.members().length,
                "complete member vector preserved");
        int[] tamper = footprint.pixels();
        tamper[0] = -1;
        check(footprint.pixels()[0] >= 0, "immutable exported vector");
        check(
                collector.producerFootprint(
                                new Object(), new ExactComponentMembers.Budget(100000, 100000))
                        == null,
                "unregistered identity stays unknown");
        check(
                collector.producerFootprint(head, new ExactComponentMembers.Budget(0, 0)) == null,
                "source-token zero budget refuses");
        raw[scene.members()[0]] ^= 1;
        check(
                !collector.revalidateForExport(new ExactComponentMembers.Budget(100000, 10000)),
                "changed raw byte refuses final proof");
        check(!token.authenticates(raw, w, h), "stale token invalidated");
        // Existing admission callback is evaluated on exact source subsets. A small
        // supported body prevents pruning even when the whole component is larger.
        var subset = new ArrayList<Integer>();
        for (int y = 20; y <= 29; y++) for (int x = 20; x <= 35; x++) subset.add(y * w + x);
        boolean found =
                ExistingHeadSubsetPreserver.supported(
                        subset.stream().mapToInt(Integer::intValue).toArray(),
                        w,
                        17,
                        budget(),
                        (r, p) ->
                                r.left() == 20
                                        && r.right() == 28
                                        && r.top() == 20
                                        && r.bottom() == 26);
        check(found, "original-size9x7 independent subset KEEP");
        // Faint, partial, curve-attached, shaft-free originals exercise the additive
        // low-contrast guard; a literal one-level contrast remains sufficient.
        FaintHeadBodyOriginals.main(new String[0]);
        System.out.println("PASS defaultQuarterSourceCarrierSubsetAndProvenanceControls=" + passed);
    }
}
