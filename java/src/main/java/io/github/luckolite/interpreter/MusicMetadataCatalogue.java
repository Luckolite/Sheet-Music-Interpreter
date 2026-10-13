// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Public work facts corroborate ownership; they never supply unprinted title characters. */
public final class MusicMetadataCatalogue {
    public record Work(String entity, int ordinal, String form, String alias) {}

    public record Witness(String entity, int ordinal, String printedForm, String printedAlias) {}

    private final Map<String, List<Work>> works;
    private final Set<String> people;
    private final Set<String> instruments;
    private static final Pattern CAPTION =
            Pattern.compile(
                    "(?iu)^(concerto|sonat[ae]|symphony|sinfonia|suite|quartet|quintet|trio|sextet|octet|prelude|fugue|étude|etude|study|waltz|polonaise|nocturne|rhapsody|fantasia|fantasy)\\h*(?:no\\.?\\h*)?([IVXLCDM]+|[0-9]{1,3})$");

    static String key(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static boolean safe(String value) {
        return value != null
                && value.length() <= 500
                && !value.isBlank()
                && value.codePoints()
                        .noneMatch(
                                c ->
                                        Character.isISOControl(c)
                                                || Character.getType(c) == Character.FORMAT
                                                || c == 0x2028
                                                || c == 0x2029);
    }

    public MusicMetadataCatalogue(
            List<Work> facts, Set<String> personNames, Set<String> instrumentNames) {
        if (facts == null
                || personNames == null
                || instrumentNames == null
                || facts.size() > 500000
                || personNames.size() > 1000000
                || instrumentNames.size() > 500000)
            throw new IllegalArgumentException("Invalid catalogue");
        var index = new HashMap<String, List<Work>>();
        for (var fact : facts) {
            if (fact == null
                    || !safe(fact.entity())
                    || !safe(fact.form())
                    || !safe(fact.alias())
                    || fact.ordinal() < 1
                    || fact.ordinal() > 999)
                throw new IllegalArgumentException("Invalid work fact");
            index.computeIfAbsent(key(fact.alias()), unused -> new ArrayList<>()).add(fact);
        }
        var frozen = new HashMap<String, List<Work>>();
        index.forEach((alias, entries) -> frozen.put(alias, List.copyOf(entries)));
        works = Collections.unmodifiableMap(frozen);
        var names = new HashSet<String>();
        for (var name : personNames) {
            if (!safe(name)) throw new IllegalArgumentException("Invalid person label");
            names.add(key(name));
        }
        people = Collections.unmodifiableSet(names);
        var instrumentKeys = new HashSet<String>();
        for (var name : instrumentNames) {
            if (!safe(name)) throw new IllegalArgumentException("Invalid instrument label");
            instrumentKeys.add(key(name));
        }
        instruments = Collections.unmodifiableSet(instrumentKeys);
    }

    /** Readers contain the complete pinned generic snapshot, without private score-derived entries. */
    public static MusicMetadataCatalogue load(
            Reader workReader, Reader personReader, Reader instrumentReader) throws IOException {
        if (workReader == null || personReader == null || instrumentReader == null)
            throw new IllegalArgumentException("Missing catalogue");
        var facts = new ArrayList<Work>();
        var names = new HashSet<String>();
        var input = new BufferedReader(workReader);
        String line;
        int rows = 0;
        while ((line = input.readLine()) != null) {
            if (++rows > 500000 || line.length() > 2000)
                throw new IOException("Work catalogue exceeds bounds");
            var fields = line.split("\\t", -1);
            if (fields.length != 4 || !fields[0].matches("Q[1-9][0-9]*"))
                throw new IOException("Invalid work row");
            // Non-integral series positions are retained in provenance but cannot attest a printed
            // integer.
            if (!fields[1].matches("[1-9][0-9]{0,2}")) continue;
            facts.add(new Work(fields[0], Integer.parseInt(fields[1]), fields[2], fields[3]));
        }
        input = new BufferedReader(personReader);
        rows = 0;
        while ((line = input.readLine()) != null) {
            if (++rows > 1000000 || line.length() > 1000)
                throw new IOException("Person catalogue exceeds bounds");
            var fields = line.split("\\t", -1);
            if (fields.length != 2 || !fields[0].matches("Q[1-9][0-9]*"))
                throw new IOException("Invalid person row");
            names.add(fields[1]);
        }
        var instruments = new HashSet<String>();
        input = new BufferedReader(instrumentReader);
        rows = 0;
        while ((line = input.readLine()) != null) {
            if (++rows > 500000 || line.length() > 1000)
                throw new IOException("Instrument catalogue exceeds bounds");
            var fields = line.split("\\t", -1);
            if (fields.length != 2 || !fields[0].matches("Q[1-9][0-9]*"))
                throw new IOException("Invalid instrument row");
            instruments.add(fields[1]);
        }
        return new MusicMetadataCatalogue(facts, names, instruments);
    }

    public boolean isPersonName(String value) {
        return value != null && people.contains(key(value));
    }

    public boolean isWorkAlias(String value) {
        return value != null && works.containsKey(key(value));
    }

    /** A repeated collection or work heading is not an instrument-part label. */
    public boolean isInstrumentPart(String value) {
        if (!safe(value)) return false;
        String[] words = value.strip().split("\\h+");
        if (words.length > 7) return false;
        for (int i = 1; i <= Math.min(4, words.length); i++) {
            String instrument = key(String.join(" ", Arrays.copyOfRange(words, 0, i)));
            if (instrument.length() < 3
                    || !instrument.matches("\\p{L}+")
                    || !instruments.contains(instrument)) continue;
            boolean suffix = true;
            for (int j = i; j < words.length; j++) {
                String token = words[j].toLowerCase(Locale.ROOT);
                if (!Set.of(
                                        "solo",
                                        "solista",
                                        "principal",
                                        "principale",
                                        "primo",
                                        "prima",
                                        "secondo",
                                        "seconda",
                                        "terzo",
                                        "terza")
                                .contains(token)
                        && ordinal(words[j]) == 0) suffix = false;
            }
            if (suffix) return true;
        }
        return false;
    }

    /** A recognizable printed work number and form must not be changed by a reread. */
    public static boolean preservesCaptionIdentifier(String rawCaption, String literalCaption) {
        if (!safe(rawCaption) || !safe(literalCaption)) return false;
        var raw = CAPTION.matcher(rawCaption.strip().replaceAll("\\h+", " "));
        var literal = CAPTION.matcher(literalCaption.strip().replaceAll("\\h+", " "));
        if (!literal.matches()) return false;
        if (!raw.matches()) return true;
        return ordinal(raw.group(2)) != 0
                && ordinal(raw.group(2)) == ordinal(literal.group(2))
                && key(raw.group(1)).equals(key(literal.group(1)));
    }

    private static int ordinal(String number) {
        if (number.matches("[1-9][0-9]{0,2}")) return Integer.parseInt(number);
        String roman = number.toUpperCase(Locale.ROOT);
        if (roman.isEmpty() || !roman.matches("(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})")) return 0;
        int total = 0, prior = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int value =
                    switch (roman.charAt(i)) {
                        case 'I' -> 1;
                        case 'V' -> 5;
                        case 'X' -> 10;
                        case 'L' -> 50;
                        case 'C' -> 100;
                        default -> 0;
                    };
            total += value < prior ? -value : value;
            prior = value;
        }
        return total;
    }

    public Witness corroborate(String literalAlias, String literalCaption) {
        if (!safe(literalAlias) || !safe(literalCaption) || isPersonName(literalAlias)) return null;
        var parsed = CAPTION.matcher(literalCaption.strip().replaceAll("\\h+", " "));
        if (!parsed.matches()) return null;
        int number = ordinal(parsed.group(2));
        if (number == 0) return null;
        String form = key(parsed.group(1));
        var entities = new HashSet<String>();
        for (var fact : works.getOrDefault(key(literalAlias), List.of())) {
            boolean typeMatches =
                    Arrays.stream(fact.form().split("[^\\p{L}]+"))
                            .anyMatch(word -> key(word).equals(form));
            if (fact.ordinal() == number && typeMatches) entities.add(fact.entity());
        }
        return entities.size() == 1
                ? new Witness(entities.iterator().next(), number, parsed.group(1), literalAlias)
                : null;
    }
}
