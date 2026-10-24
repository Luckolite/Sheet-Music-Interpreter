// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.*;
import java.util.*;

/** Runs independently drawn original caller fixtures; production intake receives only raw/model inputs. */
public final class PrintedBeamIntakeOwnershipOriginals {
    static int checks;

    static void check(boolean v, String why) {
        checks++;
        if (!v) throw new AssertionError(why);
    }

    static boolean linked(ScoreBeamSource source, int a, int b) {
        for (var g : source.groups())
            for (int i = 0; i < g.bridges().size(); i++)
                if ((g.bridges().get(i) & 1) != 0) {
                    var l =
                            g.columns().get(i).members().stream()
                                    .map(ScoreBeamSource.Anchor::noteIndex)
                                    .toList();
                    var r =
                            g.columns().get(i + 1).members().stream()
                                    .map(ScoreBeamSource.Anchor::noteIndex)
                                    .toList();
                    if (l.contains(a) && r.contains(b) || l.contains(b) && r.contains(a))
                        return true;
                }
        return false;
    }

    public static void main(String[] args) throws Exception {
        var results = new ArrayList<String>();
        for (String name :
                List.of(
                        "primary_connected",
                        "head_shaft_orphan",
                        "unbeamed_nonshaft_head_rival",
                        "shared_shaft_chord",
                        "nearby_distinct_shafts")) {
            Path folder = Path.of(args[0], name);
            var fixture = Files.readAllLines(folder.resolve("truth.tsv"));
            String[] dimensions = fixture.get(0).split("\t");
            int w = Integer.parseInt(dimensions[0]), h = Integer.parseInt(dimensions[1]);
            float gap = Float.parseFloat(dimensions[2]);
            byte[] raw = Files.readAllBytes(folder.resolve("gray.bin"));
            var notes = new ArrayList<ScoreNoteEvent>();
            var entries = fixture.subList(1, fixture.size());
            for (int i = 0; i < entries.size(); i++) {
                String[] n = entries.get(i).split("\t");
                float x = Float.parseFloat(n[0]), y = Float.parseFloat(n[1]);
                notes.add(
                        new ScoreNoteEvent(
                                0,
                                x / w,
                                Math.round((70 - y) * 2 / gap),
                                0,
                                1,
                                y / h,
                                false,
                                0,
                                Integer.parseInt(n[2])));
            }
            var score = new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
            var staff = List.of(new PlayingTechniqueDetector.Staff(30, 70, gap, 0, 1));
            var result =
                    PrintedBeamSource.capture(
                            score,
                            new byte[raw.length],
                            raw,
                            w,
                            h,
                            staff,
                            0,
                            8_000_000,
                            256_000_000);
            check(
                    result.statistics().refusal().isEmpty(),
                    name + " refused " + result.statistics());
            boolean main = linked(result.source(), 0, 1);
            if (name.equals("primary_connected") || name.equals("shared_shaft_chord"))
                check(main, name + " lost true main bridge");
            else check(!main, name + " admitted ambiguous/orphan main bridge");
            if (name.equals("shared_shaft_chord")) {
                boolean found = false;
                for (var g : result.source().groups())
                    for (var c : g.columns())
                        if (c.members().stream()
                                .map(ScoreBeamSource.Anchor::noteIndex)
                                .toList()
                                .containsAll(List.of(0, 2))) found = true;
                check(found, "Shared literal shaft did not retain chord");
            }
            if (name.equals("nearby_distinct_shafts")) {
                for (var g : result.source().groups())
                    for (var c : g.columns())
                        check(
                                !c.members().stream()
                                        .map(ScoreBeamSource.Anchor::noteIndex)
                                        .toList()
                                        .containsAll(List.of(0, 2)),
                                "Nearby shafts invented chord");
            }
            if (name.equals("unbeamed_nonshaft_head_rival"))
                check(result.statistics().rivals() > 0, "Nonshaft retained rival was ignored");
            for (byte wrongLabel : new byte[] {4, 2, 1}) {
                byte[] wrong = new byte[raw.length];
                Arrays.fill(wrong, wrongLabel);
                var labelled =
                        PrintedBeamSource.capture(
                                score, wrong, raw, w, h, staff, 0, 8_000_000, 256_000_000);
                check(
                        labelled.source().equals(result.source()),
                        "Model label changed raw carrier/ownership verdict "
                                + name
                                + " label="
                                + wrongLabel);
            }
            var applied = PrintedBeamSource.apply(score, new byte[raw.length], raw, w, h, staff, 0);
            check(
                    applied.withBeamSource(score.beamSource()).equals(score),
                    "Scalar/source fields changed");
            if (!applied.beamSource().groups().isEmpty())
                check(
                        PrintedBeamSource.apply(applied, new byte[raw.length], raw, w, h, staff, 0)
                                == applied,
                        "Source capture not idempotent");
            var refused =
                    PrintedBeamSource.capture(
                            score, new byte[raw.length], raw, w, h, staff, 0, 0, 256_000_000);
            check(
                    refused.statistics().refusal().equals("budget")
                            && refused.source().groups().isEmpty()
                            && refused.source().detachedColumns().isEmpty(),
                    "Partial budget produced certainty");
            results.add(
                    name
                            + "\t"
                            + main
                            + "\t"
                            + result.source().groups().size()
                            + "\t"
                            + result.statistics().rivals());
        }
        Files.writeString(
                Path.of(args[1]), "checks\t" + checks + "\n" + String.join("\n", results) + "\n");
        System.out.println(
                "PASS " + checks + " independent original raw intake assertions across5cases");
    }
}
