// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Original synthetic text and geometry, created for redistribution under Apache-2.0. */
public final class CurvedTitleCompletionSyntheticTest {
    private static int checks;

    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Synthetic check " + checks);
    }

    private static ScoreCreditsDetector.Line line(
            String text, float x, float y, float width, float height) {
        return new ScoreCreditsDetector.Line(text, x, y, x + width, y + height);
    }

    private static ScoreCreditsDetector.Page page() {
        return new ScoreCreditsDetector.Page(
                1600,
                1200,
                -1,
                List.of(
                        line("MEADOW UNDER", 480, 170, 240, 120),
                        line("STARLIGHT", 960, 150, 180, 60)),
                false,
                true,
                List.of());
    }

    private static CurvedTitleCompletion.Reading reading(
            int scale, double factor, String text, double confidence) {
        var words = new ArrayList<ScoreCreditsDetector.Line>();
        int index = 0;
        for (String word : text.split(" ")) {
            words.add(line(word, 12 + index * 118, 9, 108, 56));
            index++;
        }
        var nativeLine =
                new CurvedTitleCompletion.NativeLine(
                        line(text, 12, 9, (index - 1) * 118 + 108, 56), words);
        return new CurvedTitleCompletion.Reading(
                scale,
                factor,
                1600,
                600,
                new double[] {0, 0, 230},
                40,
                new double[] {450, 130, 1180, 330},
                600,
                80,
                text,
                confidence,
                List.of(nativeLine));
    }

    private static List<CurvedTitleCompletion.Reading> readings(double factor) {
        return List.of(
                reading(1, factor, "MEADOW UNDER THE STARLIGHT", .99),
                reading(2, factor, "MEADOW UNDER THE STARLIGHT", .99),
                reading(4, factor, "MEADOW UNDER THE STARLIGHT", .99));
    }

    public static void main(String[] args) {
        var original = page();
        var inputs = readings(1);
        var proposal = CurvedTitleCompletion.propose(original, inputs);
        check(proposal != null);
        check(proposal.requiresReview());
        check(proposal.literalOriginal().equals("MEADOW UNDER"));
        check(proposal.suggestedText().equals("MEADOW UNDER THE STARLIGHT"));
        check(original.lines().get(0).text().equals("MEADOW UNDER"));
        check(proposal.sourceFragments().size() == 2);
        check(
                CurvedTitleCompletion.select("Imported cover", List.of(original), proposal)
                        .accepted());
        check(CurvedTitleCompletion.propose(original, inputs.subList(0, 2)) == null);
        check(CurvedTitleCompletion.propose(original, readings(1.5)) != null);
        var mixed = new ArrayList<>(inputs);
        mixed.set(2, reading(4, 1.5, "MEADOW UNDER THE STARLIGHT", .99));
        check(CurvedTitleCompletion.propose(original, mixed) == null);
        var disagree = new ArrayList<>(inputs);
        disagree.set(2, reading(4, 1, "MEADOW UNDER THE SUNRISE", .99));
        check(CurvedTitleCompletion.propose(original, disagree) == null);
        var low = new ArrayList<>(inputs);
        low.set(2, reading(4, 1, "MEADOW UNDER THE STARLIGHT", .94));
        check(CurvedTitleCompletion.propose(original, low) == null);
        var duplicate = new ArrayList<>(inputs);
        duplicate.add(inputs.get(0));
        check(CurvedTitleCompletion.propose(original, duplicate) == null);
        var dualWidth = new ArrayList<>(inputs);
        dualWidth.addAll(readings(1.5));
        check(CurvedTitleCompletion.propose(original, dualWidth) != null);
        dualWidth.add(inputs.get(0));
        check(CurvedTitleCompletion.propose(original, dualWidth) == null);
        var missingAnchor =
                new ScoreCreditsDetector.Page(
                        1600, 1200, -1, List.of(original.lines().get(0)), false, true);
        check(CurvedTitleCompletion.propose(missingAnchor, inputs) == null);
        var notation =
                new ScoreCreditsDetector.Page(1600, 1200, 500, original.lines(), false, true);
        check(CurvedTitleCompletion.propose(notation, inputs) == null);
        var plain = new ScoreCreditsDetector.Page(1600, 1200, -1, original.lines(), false, false);
        check(CurvedTitleCompletion.propose(plain, inputs) == null);
        var first = inputs.get(0);
        var displaced =
                new CurvedTitleCompletion.Reading(
                        2,
                        1,
                        1600,
                        600,
                        new double[] {0, 0, 430},
                        40,
                        new double[] {450, 330, 1180, 530},
                        600,
                        80,
                        first.portable(),
                        .99,
                        first.nativeLines());
        var drift = new ArrayList<>(inputs);
        drift.set(1, displaced);
        check(CurvedTitleCompletion.propose(original, drift) == null);
        var later =
                new ScoreCreditsDetector.Page(
                        1600,
                        2400,
                        650,
                        List.of(
                                line("DIFFERENT WORK", 180, 30, 1160, 130),
                                line("Composed by", 180, 330, 420, 35),
                                line("MEADOW UNDER THE STARLIGHT", 180, 390, 1160, 45)),
                        true,
                        false);
        var selection =
                CurvedTitleCompletion.select("Imported cover", List.of(original, later), proposal);
        check(!selection.accepted());
        check(selection.pages().equals(List.of(original, later)));
        System.out.println("checks=" + checks);
    }

    @org.junit.Test
    public void originalFictionalCurvedTitleControls() {
        checks = 0;
        main(new String[0]);
        org.junit.Assert.assertEquals(21, checks);
    }
}
