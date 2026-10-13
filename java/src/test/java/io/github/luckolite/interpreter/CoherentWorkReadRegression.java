// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original fictional complete-axis agreement and identifier ambiguity controls. */
public final class CoherentWorkReadRegression {
    static int passed;

    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed++;
    }

    static List<ScoreHeaderReadConsensus.Reading> grid(
            String mode, String accepted, String other, double confidence) {
        var out = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        for (int scale : new int[] {1, 2, 4})
            for (String view : List.of("tight", "horizontal"))
                for (double factor : new double[] {1, 1.5})
                    out.add(
                            new ScoreHeaderReadConsensus.Reading(
                                    scale,
                                    view,
                                    factor,
                                    view.equals(mode) ? accepted : other,
                                    confidence));
        return out;
    }

    public static void main(String[] args) {
        var m = grid("horizontal", "SONATAIV", "SONATAIVer", .96);
        var e = grid("tight", "SonataIV", "SONATAIVer", .72);
        check(
                CoherentNumberedWorkReadConsensus.select(m, e).equals("SONATAIV"),
                "independent complete axes agree");
        check(CoherentNumberedWorkReadConsensus.select(m, null).isEmpty(), "secondary required");
        check(
                CoherentNumberedWorkReadConsensus.select(
                                m, grid("tight", "SONATAV", "SONATAIVer", .95))
                        .isEmpty(),
                "different ordinal rejected");
        check(
                CoherentNumberedWorkReadConsensus.select(
                                grid("horizontal", "SONATAIV", "SONATAV", .96), e)
                        .isEmpty(),
                "coherent primary conflict rejected");
        check(
                CoherentNumberedWorkReadConsensus.select(
                                m, grid("tight", "SONATAIV", "SONATAV", .95))
                        .isEmpty(),
                "coherent secondary conflict rejected");
        check(
                CoherentNumberedWorkReadConsensus.select(
                                grid("horizontal", "SONATAIV", "SONATAIVer", .89), e)
                        .isEmpty(),
                "all six primary reads strong");
        var damaged = grid("tight", "SONATAIV", "SONATAIVer", .72);
        damaged.set(0, new ScoreHeaderReadConsensus.Reading(1, "tight", 1, "SONATAV", .99));
        check(
                CoherentNumberedWorkReadConsensus.select(m, damaged).isEmpty(),
                "five corroborating views insufficient");
        var duplicate = new ArrayList<>(e);
        duplicate.set(1, duplicate.get(0));
        check(
                CoherentNumberedWorkReadConsensus.select(m, duplicate).isEmpty(),
                "duplicate view rejected");
        var invalid = new ArrayList<>(e);
        invalid.set(0, new ScoreHeaderReadConsensus.Reading(1, "tight", 1, "SonataIV", Double.NaN));
        check(
                CoherentNumberedWorkReadConsensus.select(m, invalid).isEmpty(),
                "invalid score rejected");
        check(
                CoherentNumberedWorkReadConsensus.select(
                                grid("horizontal", "Alice Lane", "AliceLaneX", .99),
                                grid("tight", "Alice Lane", "AliceLaneX", .99))
                        .isEmpty(),
                "person phrase cannot use work policy");
        check(
                CoherentNumberedWorkReadConsensus.select(
                                grid("horizontal", "SONATAIIII", "SONATAIIIIer", .99),
                                grid("tight", "SONATAIIII", "SONATAIIIIer", .99))
                        .isEmpty(),
                "malformed ordinal rejected");
        check(
                CoherentNumberedWorkReadConsensus.select(
                                m, grid("tight", "SONATAIV", "SONATAIVer", 0))
                        .isEmpty(),
                "zero-confidence corroboration rejected");
        check(
                CoherentNumberedWorkReadConsensus.select(
                                m, grid("tight", "SONATAIV", "SONATAIVer", .49))
                        .isEmpty(),
                "weak corroboration rejected");
        var noMode = new ArrayList<>(e);
        noMode.set(0, new ScoreHeaderReadConsensus.Reading(1, null, 1, "SonataIV", .99));
        check(CoherentNumberedWorkReadConsensus.select(m, noMode).isEmpty(), "null mode rejected");
        var impossible = new ArrayList<>(e);
        impossible.set(0, new ScoreHeaderReadConsensus.Reading(1, "tight", 1, "SonataIV", 1.01));
        check(
                CoherentNumberedWorkReadConsensus.select(m, impossible).isEmpty(),
                "impossible score rejected");
        System.out.println("passed=" + passed);
    }
}
