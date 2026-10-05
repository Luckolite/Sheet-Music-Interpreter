// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Text semantics only: no guessed BPM, duration, staff ownership or engraving-to-beat conversion. */
public final class ExpressiveDirectionText {
    public record Direction(
            Kind kind, Strength strength, String printedText, String qualifierText) {}

    private record Rule(Kind kind, Pattern pattern) {}

    private static Rule rule(Kind kind, String expression) {
        return new Rule(kind, Pattern.compile("(?<![a-z])(?:" + expression + ")(?![a-z])"));
    }

    private static final Rule PEDAL_RELEASE = rule(Kind.PEDAL_UP, "senza\\s+(?:pedale|ped\\.?)");
    private static final Pattern PROGRESSION = Pattern.compile("\\b(?:a\\s+)?poco\\s+a\\s+poco\\b");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern LEADING_STRENGTH = Pattern.compile("\\b(poco|molto)\\s*$");
    private static final Pattern TRAILING_STRENGTH = Pattern.compile("^\\s*(poco|molto)\\b");
    private static final List<Rule> RULES =
            List.of(
                    rule(Kind.RITARDANDO, "ritardando|ritard\\.?|rit\\.?"),
                    rule(Kind.RALLENTANDO, "rallentando|rallent\\.?|rall\\.?"),
                    rule(Kind.RITENUTO, "ritenuto|riten\\.?|rite\\.?"),
                    rule(Kind.ACCELERANDO, "accelerando|accel\\.?"),
                    rule(Kind.A_TEMPO, "a\\s+tempo"),
                    rule(Kind.TEMPO_PRIMO, "tempo\\s+(?:primo|i)"),
                    rule(Kind.SAME_TEMPO, "l\\s*['’]?\\s*istesso\\s+tempo|istesso\\s+tempo"),
                    rule(Kind.CRESCENDO, "crescendo|cresc\\.?"),
                    rule(Kind.DIMINUENDO, "diminuendo|dim\\.?|decrescendo|decresc\\.?"),
                    rule(Kind.SFORZANDO_PIANO, "sfp"),
                    rule(Kind.SFORZATO, "sfz|sforzato"),
                    rule(Kind.SFORZANDO, "sf|sforzando"),
                    rule(Kind.BREATH, "breath(?:\\s+mark)?"),
                    rule(Kind.CAESURA, "caesura"),
                    rule(Kind.PEDAL_DOWN, "ped\\.?|pedale"),
                    PEDAL_RELEASE,
                    rule(
                            Kind.UNRESOLVED_DIRECTION,
                            "stringendo(?:\\s+sempre)?|(?:molto\\s+)?piu\\s+vivo"));

    private ExpressiveDirectionText() {}

    public static List<Direction> parse(String printed) {
        if (printed == null || printed.isBlank()) return List.of();
        String normalized =
                COMBINING_MARKS
                        .matcher(Normalizer.normalize(printed, Normalizer.Form.NFD))
                        .replaceAll("")
                        .toLowerCase(Locale.ROOT)
                        .replace('’', '\'');
        var result = new ArrayList<Direction>();
        int progression = -1;
        for (var rule : RULES) {
            var match = rule.pattern().matcher(normalized);
            if (!match.find()) continue;
            if (rule.kind() == Kind.PEDAL_DOWN
                    && PEDAL_RELEASE.pattern().matcher(normalized).find()) continue;
            String before = normalized.substring(0, match.start()),
                    after = normalized.substring(match.end());
            // a poco a poco describes progression; its raw phrase remains available to policy.
            Strength strength = Strength.UNSPECIFIED;
            if (progression < 0) progression = PROGRESSION.matcher(normalized).find() ? 1 : 0;
            if (progression == 0) {
                var leading = LEADING_STRENGTH.matcher(before);
                var trailing = TRAILING_STRENGTH.matcher(after);
                String modifier =
                        leading.find()
                                ? leading.group(1)
                                : trailing.find() ? trailing.group(1) : "";
                strength =
                        modifier.equals("poco")
                                ? Strength.POCO
                                : modifier.equals("molto") ? Strength.MOLTO : Strength.UNSPECIFIED;
            }
            // Keep the complete compound phrase rather than losing modifiers during tokenization.
            String qualifiers = (before + " " + after).trim();
            result.add(new Direction(rule.kind(), strength, printed, qualifiers));
        }
        MetricModulationText.parse(printed)
                .ifPresent(
                        pulses ->
                                result.add(
                                        new Direction(
                                                Kind.METRIC_MODULATION,
                                                Strength.UNSPECIFIED,
                                                printed,
                                                pulses.encode())));
        return List.copyOf(result);
    }
}
