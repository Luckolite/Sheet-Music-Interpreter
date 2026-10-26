// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Musical credits explicitly attributed in a video's description, never its channel name. */
public final class YoutubeDescriptionCredits {
    public record Credits(String artists, String composers, String arrangers, String unresolved) {
        public Credits(String artists, String composers, String arrangers) {
            this(artists, composers, arrangers, "");
        }
    }

    private static final int ARTIST = 1, COMPOSER = 2, ARRANGER = 4;
    private static final int MAX_DESCRIPTION_LENGTH = 100_000;
    private static final Pattern CREDIT =
            Pattern.compile(
                    "(?iu)^([\\p{L} /&,+().-]{2,80}?)(?:\\s*[:=]\\s*|\\s+[–—-]\\s*|\\s+by\\s+)(.+)$");
    private static final Pattern FOLLOWING_ROLE =
            Pattern.compile(
                    "(?iu)\\s+(?:and|&)\\s+(?=(?:arranged|performed|composed|written)\\s+by\\b)");
    private static final Pattern MARKDOWN_LINK =
            Pattern.compile("\\[([^]\\r\\n]+)]\\(https?://[^)]+\\)");
    private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)\\S*");
    private static final Pattern SERVICE_DOMAIN =
            Pattern.compile(
                    "(?iu)^(?:[\\p{L}\\p{N}-]+\\.)+(?:com|net|org|io|ai|app|co|uk|dev|tech|online|site|music)(?:[/#?].*)?$");
    private static final Pattern SERVICE_TRANSCRIPTION =
            Pattern.compile(
                    "\\bAI\\b|(?iu)\\b(?:artificial intelligence|automated|automatic|transcription (?:service|tool|app)|software|platform|bot)\\b|(?iu)\\bai\\s*$");
    private static final Pattern PROSE =
            Pattern.compile(
                    "(?iu)^(?:thanks?\\b|thank you\\b|please\\b|subscribe\\b|follow\\b|listen\\b|download\\b|visit\\b|check out\\b|all rights\\b|released on\\b|auto-generated\\b|provided to youtube\\b).*");

    private YoutubeDescriptionCredits() {}

    public static Credits parse(String description) {
        Map<String, String> artists = new LinkedHashMap<>();
        Map<String, String> composers = new LinkedHashMap<>();
        Map<String, String> arrangers = new LinkedHashMap<>();
        Map<String, String> unresolved = new LinkedHashMap<>();
        if (description == null) return new Credits("", "", "");
        String bounded =
                description.substring(0, Math.min(description.length(), MAX_DESCRIPTION_LENGTH));
        int pendingRole = 0, continuationLines = 0;
        String pendingLabel = "";
        boolean automaticTrack = false;
        for (String raw : bounded.split("\\R")) {
            String line =
                    raw.replace('\u00a0', ' ')
                            .replace('\uff1a', ':')
                            .replace("&amp;", "&")
                            .replace('\u0000', ' ')
                            .trim()
                            .replaceFirst("^[\\s\\p{So}\\p{Cf}*#•>–—-]+", "")
                            .trim();
            if (line.isBlank()) {
                pendingRole = 0;
                continuationLines = 0;
                continue;
            }
            if (line.length() > 600) {
                pendingRole = 0;
                automaticTrack = false;
                continue;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("provided to youtube by ")) {
                // In YouTube's music-credit block, only the next nonempty line is track · artists.
                automaticTrack = true;
                pendingRole = 0;
                continue;
            }
            if (automaticTrack) {
                automaticTrack = false;
                String[] trackAndArtists = line.split("\\s+·\\s+");
                if (trackAndArtists.length > 1) {
                    for (int i = 1; i < trackAndArtists.length; i++)
                        add(artists, trackAndArtists[i]);
                    continue;
                }
            }
            boolean matched = false;
            for (String entry : inlineCredits(line)) {
                Matcher credit = CREDIT.matcher(entry);
                if (!credit.matches()) continue;
                matched = true;
                pendingRole = 0;
                String[] values = FOLLOWING_ROLE.split(credit.group(2));
                addCredit(
                        credit.group(1),
                        values[0],
                        entry,
                        artists,
                        composers,
                        arrangers,
                        unresolved);
                for (int i = 1; i < values.length; i++) {
                    Matcher following = CREDIT.matcher(values[i]);
                    if (following.matches())
                        addCredit(
                                following.group(1),
                                following.group(2),
                                values[i],
                                artists,
                                composers,
                                arrangers,
                                unresolved);
                }
            }
            if (matched) continue;
            String heading =
                    line.endsWith(":") ? line.substring(0, line.length() - 1).trim() : line;
            int headingRole = roles(heading);
            if (headingRole != 0
                    && (line.endsWith(":")
                            || heading.matches(
                                    "(?iu)artists?|performers?|composers?|arrangers?|songwriters?"))) {
                pendingRole = headingRole;
                pendingLabel = heading;
                continuationLines = 0;
            } else if (pendingRole != 0
                    && continuationLines++ < 12
                    && (!cleanName(line).isBlank() || URL.matcher(line).find())) {
                addCredit(
                        pendingLabel,
                        line,
                        pendingLabel + ": " + line,
                        artists,
                        composers,
                        arrangers,
                        unresolved);
            } else {
                pendingRole = 0;
            }
        }
        return new Credits(
                String.join("; ", artists.values()),
                String.join("; ", composers.values()),
                String.join("; ", arrangers.values()),
                String.join("\n", unresolved.values()));
    }

    private static List<String> inlineCredits(String line) {
        List<String> entries = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            char separator = line.charAt(i);
            boolean split = separator == '|' || separator == '•';
            if (separator == ',' || separator == ';') {
                Matcher following = CREDIT.matcher(line.substring(i + 1).trim());
                // Use the actual role grammar, so inline and separate-line credits stay aligned.
                // A comma inside a combined role label still belongs to that one credit.
                boolean combinedLabel =
                        separator == ',' && roles(line.substring(start, i).trim()) != 0;
                split = !combinedLabel && following.matches() && roles(following.group(1)) != 0;
            }
            if (split) {
                entries.add(line.substring(start, i).trim());
                start = i + 1;
            }
        }
        entries.add(line.substring(start).trim());
        return entries;
    }

    private static int roles(String label) {
        String clean =
                label.toLowerCase(Locale.ROOT)
                        .replace("(s)", "s")
                        .replace(".", "")
                        .replaceFirst("\\s+by$", "")
                        .trim();
        int result = 0;
        for (String role : clean.split("\\s*(?:[,/&+]|\\band\\b)\\s*")) {
            result |=
                    switch (role.trim()) {
                        case "artist",
                                        "artists",
                                        "original artist",
                                        "original artists",
                                        "original",
                                        "performer",
                                        "performers",
                                        "performed",
                                        "performance",
                                        "singer",
                                        "singers",
                                        "sung",
                                        "vocals",
                                        "vocal",
                                        "vocalist",
                                        "piano",
                                        "pianist",
                                        "violin",
                                        "violinist",
                                        "viola",
                                        "cello",
                                        "cellist",
                                        "guitar",
                                        "guitarist",
                                        "bass",
                                        "drums",
                                        "flute",
                                        "cover",
                                        "piano cover",
                                        "violin cover",
                                        "featuring" ->
                                ARTIST;
                        case "composer",
                                        "composers",
                                        "original composer",
                                        "original composers",
                                        "composed",
                                        "originally composed",
                                        "composition",
                                        "music",
                                        "original music",
                                        "music composed",
                                        "music written",
                                        "composer lyricist",
                                        "songwriter",
                                        "songwriters",
                                        "songwriting",
                                        "written",
                                        "writer",
                                        "writers" ->
                                COMPOSER;
                        case "arranger",
                                        "arrangers",
                                        "arranged",
                                        "arrangement",
                                        "arrangements",
                                        "arr",
                                        "transcription",
                                        "transcribed",
                                        "piano arrangement",
                                        "violin arrangement",
                                        "guitar arrangement" ->
                                ARRANGER;
                        default -> 0;
                    };
        }
        return result;
    }

    private static void addCredit(
            String label,
            String value,
            String evidence,
            Map<String, String> artists,
            Map<String, String> composers,
            Map<String, String> arrangers,
            Map<String, String> unresolved) {
        int mask = roles(label);
        String folded = label.toLowerCase(Locale.ROOT);
        if (!folded.contains("transcrib") && !folded.contains("transcription")) {
            add(mask, value, artists, composers, arrangers);
            return;
        }
        // Transcription can identify a person or a service. Keep service evidence for review.
        for (String part : value.split("\\s*[;·]\\s*")) {
            Map<String, String> candidates = new LinkedHashMap<>();
            add(candidates, part);
            if (candidates.isEmpty() && URL.matcher(part).find())
                unresolved.putIfAbsent(evidence, evidence);
            for (String name : candidates.values()) {
                if (SERVICE_DOMAIN.matcher(name).matches()
                        || SERVICE_TRANSCRIPTION.matcher(name).find())
                    unresolved.putIfAbsent(evidence, evidence);
                else add(mask, name, artists, composers, arrangers);
            }
        }
    }

    private static void add(
            int roles,
            String value,
            Map<String, String> artists,
            Map<String, String> composers,
            Map<String, String> arrangers) {
        if ((roles & ARTIST) != 0) add(artists, value);
        if ((roles & COMPOSER) != 0) add(composers, value);
        if ((roles & ARRANGER) != 0) add(arrangers, value);
    }

    private static void add(Map<String, String> names, String value) {
        for (String item : value.split("\\s*[;·]\\s*")) {
            String clean = cleanName(item);
            if (clean.isBlank()) continue;
            // Keep ambiguous band names and surname-first names intact. Full-person lists can
            // split.
            String[] people = clean.split("(?iu)\\s*(?:,)\\s*|\\s+(?:and|&)\\s+");
            boolean fullNames = people.length > 1;
            for (String person : people)
                if (person.trim().split("\\s+").length < 2) fullNames = false;
            for (String person : fullNames ? people : new String[] {clean}) {
                person = cleanName(person);
                if (!person.isBlank())
                    names.putIfAbsent(
                            Normalizer.normalize(person, Normalizer.Form.NFKC)
                                    .toLowerCase(Locale.ROOT),
                            person);
            }
        }
    }

    private static String cleanName(String value) {
        String clean = MARKDOWN_LINK.matcher(value).replaceAll("$1");
        Matcher url = URL.matcher(clean);
        if (url.find()) clean = clean.substring(0, url.start());
        clean =
                clean.replaceFirst("\\s*\\((?i:instagram|youtube|tiktok|spotify)\\)\\s*$", "")
                        .replaceAll("^[\\s:–—-]+|[\\s:;,|–—(\\[-]+$", "")
                        .replaceAll("\\s+", " ")
                        .trim();
        if (clean.length() > 180
                || clean.split("\\s+").length > 12
                || PROSE.matcher(clean).matches()
                || clean.matches("(?iu)n/?a|none|unknown|not (?:available|specified)|tba")
                || clean.indexOf('<') >= 0
                || clean.indexOf('>') >= 0
                || clean.codePoints().noneMatch(Character::isLetter)) return "";
        return clean;
    }
}
