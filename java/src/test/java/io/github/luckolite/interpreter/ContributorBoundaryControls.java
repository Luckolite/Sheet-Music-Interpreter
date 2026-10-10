// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import io.github.luckolite.interpreter.ScoreCreditsDetector;
import io.github.luckolite.interpreter.ScoreCreditsDetector.Line;
import io.github.luckolite.interpreter.ScoreCreditsDetector.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Fictional, shareable continuation fixtures; no PDF, library, hint, gold or expected song. */
public final class ContributorBoundaryControls {
    private record Case(
            String name,
            String original,
            List<String> readings,
            List<String> adjusted,
            String expected,
            boolean boundaryLoss,
            boolean explicit) {}

    private static final List<Case> cases = new ArrayList<>();
    private static int checks;
    private static int failures;
    private static int boundaryFailures;

    private static void check(boolean condition, String message, boolean boundary) {
        checks++;
        if (!condition) {
            failures++;
            if (boundary) boundaryFailures++;
            System.out.println("FAIL " + message);
        }
    }

    private static List<String> three(String text) {
        return List.of(text, text, text);
    }

    private static void normal(String name, String original, String reading, String expected) {
        cases.add(new Case(name, original, three(reading), List.of(), expected, false, false));
    }

    private static void loss(String name, String original, String reading) {
        cases.add(new Case(name, original, three(reading), List.of(), original, true, false));
    }

    private static String removeFirst(String text) {
        return text.substring(Character.charCount(text.codePointAt(0)));
    }

    private static String removeLast(String text) {
        return text.substring(
                0, text.length() - Character.charCount(text.codePointBefore(text.length())));
    }

    public static void main(String[] args) {
        boolean characterize = args.length == 1 && args[0].equals("--characterize-baseline");
        for (String separator : List.of(", ", "; ", " and ", " & ")) {
            String names = "Mira North" + separator + "Tessa Brook" + separator + "Kiran Reed";
            String key =
                    separator
                            .strip()
                            .replace("&", "ampersand")
                            .replace(",", "comma")
                            .replace(";", "semicolon");
            loss(key + "-leading-letter", names, removeFirst(names));
            loss(key + "-trailing-letter", names, removeLast(names));
        }
        String astralLeading = "\uD801\uDC00ira North, Tessa Brook and Kiran Reed";
        String astralTrailing = "Mira North, Tessa Brook and Kiran Ree\uD801\uDC00";
        loss("supplementary-alphabetic-leading", astralLeading, removeFirst(astralLeading));
        loss("supplementary-alphabetic-trailing", astralTrailing, removeLast(astralTrailing));
        loss("single-contributor-leading", "Mira North", "ira North");
        loss("single-contributor-trailing", "Mira North", "Mira Nort");
        String names = "Mira North, Tessa Brook and Kiran Reed";
        normal("leading-insertion", removeFirst(names), names, names);
        normal("trailing-insertion", removeLast(names), names, names);
        normal("internal-substitution", names.replace("North", "Norrh"), names, names);
        normal("internal-deletion", names.replace("North", "Norrth"), names, names);
        normal(
                "continuation-accent",
                names,
                names.replace("Mira", "M\u00edra"),
                names.replace("Mira", "M\u00edra"));
        normal(
                "continuation-apostrophe",
                names.replace("North", "ONorth"),
                names.replace("North", "O'North"),
                names.replace("North", "O'North"));
        normal(
                "continuation-hyphen",
                names.replace("North", "NorthVale"),
                names.replace("North", "North-Vale"),
                names.replace("North", "North-Vale"));
        normal("punctuation-only", names, names + ".", names + ".");
        normal("case-only", names, names.toUpperCase(), names);
        normal("whitespace-only", names, names.replace(" ", "  "), names);
        normal("unchanged", names, names, names);
        normal(
                "two-letter-leading-outside-narrow-scope",
                names,
                names.substring(2),
                names.substring(2));
        normal(
                "loss-plus-internal-edit-outside-narrow-scope",
                names,
                removeFirst(names).replace("North", "Norrh"),
                removeFirst(names).replace("North", "Norrh"));
        cases.add(
                new Case(
                        "nonunanimous",
                        names,
                        List.of(removeFirst(names), names, removeFirst(names)),
                        List.of(),
                        names,
                        false,
                        false));
        cases.add(
                new Case(
                        "missing-reading",
                        names,
                        List.of(removeFirst(names), "", removeFirst(names)),
                        List.of(),
                        names,
                        false,
                        false));
        cases.add(
                new Case(
                        "two-readings",
                        names,
                        List.of(removeFirst(names), removeFirst(names)),
                        List.of(),
                        names,
                        false,
                        false));
        String explicit = "Music by Mira North";
        cases.add(
                new Case(
                        "explicit-role-loss-outside-guard",
                        explicit,
                        three("Music by ira North"),
                        List.of(),
                        "Music by ira North",
                        false,
                        true));
        cases.add(
                new Case(
                        "explicit-accent-unconfirmed",
                        explicit,
                        three("Music by M\u00edra North"),
                        List.of(),
                        explicit,
                        false,
                        true));
        cases.add(
                new Case(
                        "explicit-accent-confirmed",
                        explicit,
                        three("Music by M\u00edra North"),
                        three("Music by M\u00edra North"),
                        "Music by M\u00edra North",
                        false,
                        true));

        for (Case test : cases) {
            Line title = new Line("Quiet Lantern", 650, 74, 1150, 152);
            Line label = new Line("Words and Music by Elin West, Arun Vale,", 362, 161, 1442, 188);
            Line original = new Line(test.original(), 452, 183, 1376, 216);
            List<Line> input =
                    test.explicit() ? List.of(title, original) : List.of(title, label, original);
            Page page = new Page(1800, 2418, 420, input);
            check(
                    ScoreCreditsDetector.bibliographicLineCandidates(page).contains(original),
                    test.name() + " reaches continuation/credit path",
                    false);
            List<Line> merged =
                    ScoreCreditsDetector.mergeBibliographicFallbackConsensus(
                            page, original, test.readings(), test.adjusted());
            int index = input.indexOf(original);
            String expected =
                    characterize && test.boundaryLoss() ? test.readings().get(0) : test.expected();
            check(
                    merged.get(index).text().equals(expected),
                    test.name() + " expected=" + expected + " actual=" + merged.get(index).text(),
                    test.boundaryLoss());
            check(merged.size() == input.size(), test.name() + " line count", false);
            for (int i = 0; i < input.size(); i++)
                if (i != index)
                    check(
                            merged.get(i).equals(input.get(i)),
                            test.name() + " unrelated line " + i,
                            false);
            Line changed = merged.get(index);
            check(
                    changed.left() == original.left()
                            && changed.top() == original.top()
                            && changed.right() == original.right()
                            && changed.bottom() == original.bottom(),
                    test.name() + " geometry",
                    false);
            var detected =
                    ScoreCreditsDetector.detect(
                            "", List.of(new Page(1800, 2418, 420, merged)), Set.of());
            check(
                    detected.artist().isEmpty() && detected.arranger().isEmpty(),
                    test.name() + " no role drift",
                    false);
            if (!test.explicit())
                check(
                        detected.composer().startsWith("Elin West; Arun Vale; "),
                        test.name() + " contributor order",
                        false);
            if (!characterize && test.boundaryLoss()) {
                check(
                        merged.equals(page.lines()),
                        test.name() + " retained entire original page",
                        false);
                check(
                        detected.equals(ScoreCreditsDetector.detect("", List.of(page), Set.of())),
                        test.name() + " exact roles/order retained",
                        false);
            }
            System.out.println("CASE " + test.name() + " actual=" + changed.text());
        }
        System.out.println(
                "SUMMARY cases="
                        + cases.size()
                        + " checks="
                        + checks
                        + " failures="
                        + failures
                        + " boundaryFailures="
                        + boundaryFailures
                        + " characterizeBaseline="
                        + characterize);
        if (failures != 0)
            throw new AssertionError("Synthetic controls failed; do not weaken expectations");
    }
}
