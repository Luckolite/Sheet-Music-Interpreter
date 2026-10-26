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

    private static final Rule PEDAL_RING =
            rule(Kind.UNRESOLVED_DIRECTION, "^\\s*let\\s+(?:the\\s+)?pedal\\s+ring[.!]?\\s*$");
    private static final Rule PEDAL_RELEASE = rule(Kind.PEDAL_UP, "senza\\s+(?:pedale|ped\\.?)");
    private static final Pattern PROGRESSION = Pattern.compile("\\b(?:a\\s+)?poco\\s+a\\s+poco\\b");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern LEADING_STRENGTH = Pattern.compile("\\b(poco|molto)\\s*$");
    private static final Pattern TRAILING_STRENGTH = Pattern.compile("^\\s*(poco|molto)\\b");
    private static final String NEGATION_WORD = "(?:no|non|not|senza|without)";
    private static final String TEMPO_MODIFIER =
            "(?:(?:a\\s+)?poco\\s+a\\s+poco|poco|molto|sempre)";
    private static final Pattern NEGATION_PREFIX =
            Pattern.compile("\\b" + NEGATION_WORD + "\\s+(?:" + TEMPO_MODIFIER + "\\s+)*$");
    private static final Pattern BARE_NEGATION_QUALIFIER =
            Pattern.compile(
                    "\\s*" + NEGATION_WORD + "(?:\\s+" + TEMPO_MODIFIER + ")*[\\s.,;:()!]*");
    private static final List<Rule> RULES =
            List.of(
                    rule(Kind.RITARDANDO, "ritardando|ritard\\.?|rit\\.?"),
                    rule(Kind.RALLENTANDO, "rallentando|rallent\\.?|rall\\.?"),
                    rule(Kind.RITENUTO, "ritenuto|riten\\.?|rite\\.?"),
                    rule(Kind.ACCELERANDO, "accelerando|accel\\.?|gradually\\s+faster"),
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
                    PEDAL_RING,
                    rule(
                            Kind.UNRESOLVED_DIRECTION,
                            "stringendo(?:\\s+sempre)?|(?:molto\\s+)?piu\\s+vivo"
                                    + "|^\\s*(?:[mpf]{1,3}\\s+)?faster\\s*[-.!]*\\s*$"
                                    + "|scherzando|agitato|festoso|espressivo|dolce(?:\\s+e\\s+cantabile)?"
                                    + "|cantabile|con\\s+(?:brio|fuoco|anima)|tranquillo|grazioso"
                                    + "|^\\s*(?:(?:ppp|pp|p|mp|mf|fff|ff|f)\\s+)?"
                                    + "(?:(?:molto|poco|sempre)\\s+)?legato[.!]?\\s*$"
                                    // English character directions need the complete OCR line;
                                    // never a prose substring.
                                    + "|^\\s*(?:sweetly|flowing|triumphantly|ethereal|with\\s+driving\\s+intensity|like\\s+an\\s+echo)[.!]?\\s*$"
                                    + "|giocoso|maestoso|appassionato|leggiero|free\\s+time|^\\s*(?:freely|gently)(?:\\s*(?:,|and)\\s*(?:freely|gently))*[.!]?\\s*$"));

    private ExpressiveDirectionText() {}

    static boolean pedalRingCaption(String printed) {
        return printed != null
                && PEDAL_RING.pattern().matcher(printed.toLowerCase(Locale.ROOT)).matches();
    }

    /** Cached positive kinds still need the original text's action-specific negation guard. */
    static boolean hasTempoNegation(ScoreExpressiveEvent event) {
        if (!changingTempo(event.kind())) return false;
        for (var evidence : event.evidence())
            if (negatesTempo(event.kind(), normalized(evidence.printedText()))) return true;
        String qualifier = normalized(event.qualifierText());
        return BARE_NEGATION_QUALIFIER.matcher(qualifier).matches()
                || negatesTempo(event.kind(), qualifier);
    }

    private static boolean negatesTempo(Kind kind, String text) {
        for (var rule : RULES) {
            if (rule.kind() != kind) continue;
            var match = rule.pattern().matcher(text);
            while (match.find())
                if (NEGATION_PREFIX.matcher(text.substring(0, match.start())).find()) return true;
        }
        return false;
    }

    private static String normalized(String printed) {
        return COMBINING_MARKS
                .matcher(Normalizer.normalize(printed, Normalizer.Form.NFD))
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'');
    }

    static boolean changingTempo(Kind kind) {
        return kind == Kind.RITARDANDO
                || kind == Kind.RALLENTANDO
                || kind == Kind.RITENUTO
                || kind == Kind.ACCELERANDO;
    }

    public static List<Direction> parse(String printed) {
        if (printed == null || printed.isBlank()) return List.of();
        if (SuddenDynamicText.parse(printed).isPresent())
            return List.of(
                    new Direction(Kind.UNRESOLVED_DIRECTION, Strength.UNSPECIFIED, printed, ""));
        String normalized = normalized(printed);
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
            Kind kind = rule.kind();
            if (changingTempo(kind) && NEGATION_PREFIX.matcher(before).find()) {
                // Negation must govern this action, rather than an independent pedal or
                // articulation modifier elsewhere in the complete phrase.
                kind = Kind.UNRESOLVED_DIRECTION;
                strength = Strength.UNSPECIFIED;
            }
            if (kind != Kind.UNRESOLVED_DIRECTION
                    || result.stream()
                            .noneMatch(value -> value.kind() == Kind.UNRESOLVED_DIRECTION))
                result.add(new Direction(kind, strength, printed, qualifiers));
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
