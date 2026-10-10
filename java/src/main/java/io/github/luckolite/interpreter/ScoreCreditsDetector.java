// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bibliographic header evidence. Musical interpretation and caller-owned metadata stay separate. */
public final class ScoreCreditsDetector {
    private static final Pattern CLEAN_HORIZONTAL_SPACE = Pattern.compile("\\h+");
    private static final Pattern FOLD_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern FOLD_NON_NAMES = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern TEMPO_TITLE_NAME =
            Pattern.compile("(?iu)^(?:allegro|andante|adagio|largo|lento|presto)$");
    private static final Pattern FROM_SUBTITLE = Pattern.compile("(?iu)^from\\h+.+");

    public record Line(String text, float left, float top, float right, float bottom) {
        float height() {
            return Math.max(1, bottom - top);
        }

        float center() {
            return (left + right) / 2;
        }
    }

    public record Page(
            float width,
            float height,
            float notationTop,
            List<Line> lines,
            boolean creditsOnly,
            boolean photographicCover) {
        public Page(
                float width,
                float height,
                float notationTop,
                List<Line> lines,
                boolean creditsOnly) {
            this(width, height, notationTop, lines, creditsOnly, false);
        }

        public Page(float width, float height, float notationTop, List<Line> lines) {
            this(width, height, notationTop, lines, false);
        }

        public Page {
            lines = List.copyOf(lines);
        }
    }

    public record Result(
            String title,
            String artist,
            String composer,
            String arranger,
            List<String> unclassifiedCredits) {}

    private static final List<String> DISPLAY_ROLE_LABELS =
            List.of(
                    "music by",
                    "composed by",
                    "arranged by",
                    "performed by",
                    "words by",
                    "lyrics by");

    /** Large centered printed names can have explicit labels below decorative cover bands. */
    private static Line displayRoleName(Page page, Line label) {
        if (page.creditsOnly
                || page.width <= 0
                || page.height <= 0
                || label.height() < page.width * .008f
                || label.height() > page.width * .04f
                || label.top < page.height * .2f
                || label.bottom > page.height * .72f
                || Math.abs(label.center() - page.width * .5f) > page.width * .12f) return null;
        List<Line> matches =
                page.lines.stream()
                        .filter(
                                name ->
                                        name != label
                                                && nameText(name.text)
                                                && credit(name.text) == null
                                                && !LEGAL.matcher(name.text).find()
                                                && name.top >= label.bottom
                                                && name.top - label.bottom <= label.height() * 1.2f
                                                && name.height() >= label.height() * 1.8f
                                                && name.height() <= page.width * .12f
                                                && name.bottom <= page.height * .8f
                                                && name.right - name.left >= page.width * .3f
                                                && name.right - name.left <= page.width * .85f
                                                && Math.abs(name.center() - label.center())
                                                        <= page.width * .07f)
                        .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static boolean exactDisplayRoleLabel(String text) {
        return DISPLAY_ROLE_LABELS.contains(clean(text).toLowerCase(Locale.ROOT));
    }

    /** A near label is only a request for pixels; it never assigns a contributor role. */
    public static List<Line> displayRoleLabelCandidates(Page page) {
        return page.lines.stream()
                .filter(
                        line -> {
                            String value = clean(line.text).toLowerCase(Locale.ROOT);
                            return value.length() >= 7
                                    && !exactDisplayRoleLabel(value)
                                    && displayRoleName(page, line) != null
                                    && DISPLAY_ROLE_LABELS.stream()
                                            .anyMatch(label -> editDistance(value, label) == 1);
                        })
                .toList();
    }

    public static List<Line> mergeDisplayRoleLabel(
            Page page, Line original, List<String> readings, List<Float> confidence) {
        if (!displayRoleLabelCandidates(page).contains(original)
                || readings == null
                || readings.size() != 3
                || confidence == null
                || confidence.size() != 3
                || confidence.stream()
                        .anyMatch(v -> v == null || !Float.isFinite(v) || v < .98f || v > 1))
            return page.lines;
        String text = readings.get(0) == null ? "" : clean(readings.get(0));
        if (!exactDisplayRoleLabel(text)
                || editDistance(
                                clean(original.text).toLowerCase(Locale.ROOT),
                                text.toLowerCase(Locale.ROOT))
                        != 1
                || readings.stream().anyMatch(v -> v == null || !clean(v).equalsIgnoreCase(text)))
            return page.lines;
        List<Line> result = new ArrayList<>(page.lines);
        result.set(
                result.indexOf(original),
                new Line(text, original.left, original.top, original.right, original.bottom));
        return List.copyOf(result);
    }

    private static boolean inDisplayRoleCredit(Page page, Line line) {
        for (Line label : page.lines)
            if (exactDisplayRoleLabel(label.text)) {
                Line name = displayRoleName(page, label);
                if (name != null && (line.equals(label) || line.equals(name))) return true;
            }
        return false;
    }

    /** Read a printed movement subtitle from original pixels, including its small ordinal suffix. */
    public static List<Line> originalMovementOrdinalCandidates(Page page) {
        if (page.creditsOnly || page.notationTop <= 0) return List.of();
        Line heading = chooseTitle("", page, page.lines, page.notationTop);
        if (heading == null) return List.of();
        return page.lines.stream()
                .filter(
                        line ->
                                clean(line.text).matches("(?iu)^[1-9]\\h+movement$")
                                        && line.bottom < page.notationTop
                                        && line.top >= heading.bottom - heading.height() * .2f
                                        && line.top - heading.bottom < heading.height() * 1.2f
                                        && line.height() >= heading.height() * .25f
                                        && line.height() <= heading.height() * .8f
                                        && Math.abs(line.center() - heading.center())
                                                < heading.height() * 1.6f)
                .toList();
    }

    public static List<Line> mergeOriginalMovementOrdinal(
            Page page, Line original, List<String> readings) {
        if (!originalMovementOrdinalCandidates(page).contains(original)
                || readings == null
                || readings.size() != 3
                || readings.stream().anyMatch(v -> v == null || v.isBlank())) return page.lines;
        String text = clean(readings.get(0));
        if (readings.stream().anyMatch(v -> !clean(v).equalsIgnoreCase(text))
                || !text.matches("(?iu)^[1-9](?:st|nd|rd|th)\\h+movement$")
                || !CreditOcrIdentifierGuard.preservesIdentifiers(original.text, text))
            return page.lines;
        return mergeEmbeddedMovementCaptions(
                page,
                page.lines,
                List.of(
                        new Line(
                                text,
                                original.left,
                                original.top,
                                original.right,
                                original.bottom)));
    }

    public record ProfileCreditEvidence(Line credit, Line profile, String correctedText) {}

    private static String profileReadingKey(String text) {
        return text == null ? "" : clean(text).replaceAll("\\h+", "").toLowerCase(Locale.ROOT);
    }

    private static String printedProfileHandle(String text) {
        Matcher url =
                Pattern.compile(
                                "(?iu)^\\(?(?:https?://)?(?:www\\.)?youtube\\.com/([\\p{L}\\p{N}_]{3,80})\\)?$")
                        .matcher(profileReadingKey(text));
        return url.matches() ? url.group(1) : "";
    }

    /** Only a nearby printed profile can corroborate an extra third terminal letter. */
    public static List<ProfileCreditEvidence> profileCreditCandidates(Page page) {
        List<ProfileCreditEvidence> result = new ArrayList<>();
        for (Line line : page.lines) {
            Credit role = credit(line.text);
            if (role == null
                    || role.suffix
                    || role.value.isBlank()
                    || !inCreditHeader(page, line)
                    || line.height() < 16
                    || LEGAL.matcher(line.text).find()) continue;
            List<ProfileCreditEvidence> matches = new ArrayList<>();
            for (Line profile : page.lines) {
                String handle = printedProfileHandle(profile.text);
                if (handle.isEmpty()
                        || profile.top < line.bottom
                        || profile.top - line.bottom > line.height() * 1.5f
                        || profile.height() < line.height() * .4f
                        || profile.height() > line.height() * 1.5f
                        || Math.abs(profile.right - line.right) > line.height()
                        || Math.min(profile.right, line.right) - Math.max(profile.left, line.left)
                                < (profile.right - profile.left) * .8f) continue;
                Matcher name =
                        Pattern.compile(
                                        "(?<!\\p{L})([\\p{Lu}][\\p{L}'’-]{1,40})\\h+([\\p{Lu}][\\p{L}'’-]{2,40})(?!\\p{L})")
                                .matcher(line.text);
                StringBuffer corrected = new StringBuffer();
                int changes = 0;
                while (name.find()) {
                    String surname = name.group(2);
                    int[] points = surname.codePoints().toArray();
                    String shorter = surname;
                    if (points.length >= 3
                            && points[points.length - 1] == points[points.length - 2]
                            && points[points.length - 2] == points[points.length - 3]) {
                        shorter =
                                surname.substring(
                                        0, surname.offsetByCodePoints(surname.length(), -1));
                        if (handle.matches(
                                "(?iu)" + Pattern.quote(name.group(1) + shorter) + "\\d{0,8}"))
                            changes++;
                        else shorter = surname;
                    }
                    name.appendReplacement(
                            corrected,
                            Matcher.quoteReplacement(
                                    name.group()
                                                    .substring(
                                                            0,
                                                            name.group().length()
                                                                    - surname.length())
                                            + shorter));
                }
                name.appendTail(corrected);
                String text = corrected.toString();
                Credit after = credit(text);
                if (changes == 1
                        && after != null
                        && after.role == role.role
                        && sameConsensusContributorCount(role.value, after.value)
                        && CreditOcrIdentifierGuard.preservesIdentifiers(line.text, text))
                    matches.add(new ProfileCreditEvidence(line, profile, text));
            }
            if (matches.size() == 1) result.add(matches.get(0));
        }
        return List.copyOf(result);
    }

    /** The complete printed link, including all digits, must survive three original reads. */
    public static List<Line> mergeProfileCredit(
            Page page,
            ProfileCreditEvidence evidence,
            List<String> readings,
            List<Float> confidence) {
        if (evidence == null
                || !profileCreditCandidates(page).contains(evidence)
                || readings == null
                || readings.size() != 3
                || confidence == null
                || confidence.size() != 3
                || confidence.stream()
                        .anyMatch(v -> v == null || !Float.isFinite(v) || v < .9f || v > 1)
                || readings.stream()
                        .anyMatch(
                                v ->
                                        !profileReadingKey(v)
                                                .equals(profileReadingKey(evidence.profile.text))))
            return page.lines;
        List<Line> result = new ArrayList<>(page.lines);
        Line line = evidence.credit;
        result.set(
                result.indexOf(line),
                new Line(evidence.correctedText, line.left, line.top, line.right, line.bottom));
        return List.copyOf(result);
    }

    /** Request original pixels for a one-letter error in a standalone suffix label. */
    public static List<Line> standaloneArrangementLabelCandidates(Page page) {
        if (page.creditsOnly || page.notationTop <= 0) return List.of();
        Line title = chooseTitle("", page, page.lines, page.notationTop);
        return page.lines.stream()
                .filter(
                        line -> {
                            String value = clean(line.text).toLowerCase(Locale.ROOT);
                            if (!value.matches("arr\\p{L}{5,10}")
                                    || editDistance(value, "arrangement") != 1
                                    || line.height() < 16
                                    || line.height() > page.width * .05f
                                    || line.bottom >= page.notationTop
                                    || !inCreditHeader(page, line)) return false;
                            Line name = nearestName(line, page.lines, title, true);
                            return name != null
                                    && inCreditHeader(page, name)
                                    && line.top - name.bottom >= 0
                                    && line.top - name.bottom
                                            <= Math.max(line.height(), name.height()) * 1.5f
                                    && Math.abs(line.center() - name.center())
                                            <= Math.max(line.height(), name.height());
                        })
                .toList();
    }

    /** The role exists only after all three original-PDF samples read its real label. */
    public static List<Line> mergeStandaloneArrangementLabel(
            Page page, Line original, List<String> readings) {
        if (!standaloneArrangementLabelCandidates(page).contains(original)
                || readings == null
                || readings.size() != 3
                || readings.stream()
                        .anyMatch(
                                value ->
                                        value == null
                                                || !clean(value).equalsIgnoreCase("Arrangement")))
            return page.lines;
        List<Line> result = new ArrayList<>(page.lines);
        result.set(
                result.indexOf(original),
                new Line(
                        clean(readings.get(0)),
                        original.left,
                        original.top,
                        original.right,
                        original.bottom));
        return List.copyOf(result);
    }

    /** Keep vertical caption isolation while checking a primary heading's outer letters. */
    public static boolean bibliographicHorizontalHeadingNeeded(
            Page page, Line original, List<Line> merged) {
        return !page.creditsOnly
                && wordedOpeningHeading(page, original)
                && merged.contains(original);
    }

    public record Refinement(float left, float top, float right, float bottom, float scale) {}

    public record WordRefinement(Line word, String hint, Refinement region) {}

    /** Existing printed credit labels and centered captions eligible for a second reading. */
    public static List<Line> bibliographicLineCandidates(Page page) {
        return page.lines.stream().filter(l -> bibliographicLineCandidate(page, l)).toList();
    }

    /** Keep outer glyphs only for an already eligible printed contributor line. */
    public static boolean bibliographicCreditLineNeedsHorizontalMargins(Page page, Line line) {
        if (!page.lines.contains(line) || !bibliographicLineCandidate(page, line)) return false;
        return credit(clean(line.text)) != null
                || misspelledArrangementCredit(clean(line.text)) != null
                || printedCreditContinuation(page, line) != null;
    }

    /** Prominent already located cover headings; no contributor role is inferred. */
    public static List<Line> locatedCoverHeadingCandidates(Page page) {
        if (page.creditsOnly
                || page.notationTop > 0 && !page.photographicCover
                || page.width <= 0
                || page.height <= 0) return List.of();
        return page.lines.stream()
                .filter(
                        l ->
                                l.top < page.height * (page.photographicCover ? .9f : .3f)
                                        && l.top >= 0
                                        && l.height()
                                                >= page.width
                                                        * (page.photographicCover ? .025f : .04f)
                                        && l.height() <= page.width * .25f
                                        && l.right - l.left
                                                >= page.width * (page.photographicCover ? .2f : .3f)
                                        && l.right - l.left <= page.width * .95f
                                        && clean(l.text).length()
                                                >= (page.photographicCover ? 3 : 6)
                                        && clean(l.text).length() <= 80
                                        && titleText(l.text)
                                        && credit(l.text) == null
                                        && !DATED_AUTHOR.matcher(clean(l.text)).matches())
                .toList();
    }

    /** Three original-PDF readings must agree before becoming title-selection evidence. */
    public static Line locatedCoverHeadingReading(Page page, Line original, List<String> readings) {
        if (!locatedCoverHeadingCandidates(page).contains(original)
                || readings == null
                || readings.size() != 3
                || readings.stream().anyMatch(s -> s == null || s.isBlank())) return null;
        String text = clean(readings.get(0));
        String key = text.toLowerCase(Locale.ROOT).replaceAll("\\h+", " ");
        if (readings.stream()
                        .anyMatch(
                                v ->
                                        !clean(v).toLowerCase(Locale.ROOT)
                                                .replaceAll("\\h+", " ")
                                                .equals(key))
                || !titleText(text)
                || credit(text) != null
                || !CreditOcrIdentifierGuard.preservesIdentifiers(original.text, text)
                || !preservesHeadingShape(original.text, text)) return null;
        return new Line(text, original.left, original.top, original.right, original.bottom);
    }

    private static boolean bibliographicLineCandidate(Page page, Line line) {
        float limit = page.notationTop > 0 ? page.notationTop : page.height * .62f;
        boolean locatedRecordingCredit =
                frontAudioArrangementCredit(page, line)
                        || frontInstrumentalArrangementCredit(page, line)
                        || recordingPanelLine(page, line)
                                && recordingArrangementCredit(line.text) != null
                        || line.top >= page.height * .8f
                                && inCreditHeader(page, line)
                                && isExplicitFooterCredit(line.text);
        if (line.top >= limit && !locatedRecordingCredit
                || line.height() < 16
                || line.right - line.left < 32
                || line.height() > page.width * .25f
                || clean(line.text).length() > 180) return false;
        Credit label = credit(clean(line.text));
        if (label == null) label = misspelledArrangementCredit(clean(line.text));
        if (label != null
                && !label.suffix
                && !label.value.isBlank()
                && line.height() <= page.width * .09f) return true;
        if (printedCreditContinuation(page, line) != null) return true;
        String value = clean(line.text);
        if (joinedOpeningHeading(page, line) || wordedOpeningHeading(page, line)) return true;
        return value.startsWith("(")
                && value.endsWith(")")
                && value.length() < 80
                && Math.abs(line.center() - page.width * .5f) < page.width * .2f;
    }

    /** Reuse printed byline geometry before allowing an unlabeled continuation to be reread. */
    private static Credit printedCreditContinuation(Page page, Line candidate) {
        if (!inCreditHeader(page, candidate)
                || !nameText(candidate.text)
                || credit(candidate.text) != null) return null;
        Line heading = chooseTitle("", page, page.lines, page.notationTop);
        for (Line label : page.lines) {
            Credit attribution = credit(clean(label.text));
            if (attribution == null || attribution.suffix || !inCreditHeader(page, label)) continue;
            String value = clean(attribution.value);
            Line prior = label;
            if (value.isEmpty()) {
                prior = nearestName(label, page.lines, heading, false);
                if (prior == null || !inCreditHeader(page, prior)) continue;
                if (prior.equals(candidate))
                    return new Credit(attribution.role, clean(candidate.text), false);
                value = clean(prior.text);
            }
            for (int part = 0; part < 6; part++) {
                Line next = nearestName(prior, page.lines, heading, false);
                boolean grouped =
                        next != null
                                && (attribution.role == Role.COMPOSER
                                        || attribution.role == Role.ARRANGER
                                        || attribution.role == Role.LYRICS)
                                && continuesPrintedNameGroup(value, prior, next);
                if (next == null
                        || !inCreditHeader(page, next)
                        || !(value.endsWith(",")
                                || value.matches("(?iu).*\\b(?:and|&)\\h*$")
                                || clean(next.text).matches("(?iu)^(?:and|&)\\h+.+")
                                || grouped)) break;
                if (next.equals(candidate))
                    return new Credit(attribution.role, clean(candidate.text), false);
                value += (grouped ? "; " : " ") + clean(next.text);
                prior = next;
            }
        }
        return null;
    }

    private static final Pattern LOWERCASE_INITIAL_NAME =
            Pattern.compile("(?<![\\p{L}\\p{N}])l(\\p{Ll}{2,})\\h+(\\p{Lu}\\p{L}{2,})(?!\\p{L})");

    private static boolean labelledContributorHeader(Page page, Line line) {
        Credit value = credit(clean(line.text));
        return value != null
                && !value.suffix
                && inCreditHeader(page, line)
                && creditNameText(value.value)
                && LOWERCASE_INITIAL_NAME.matcher(value.value).find();
    }

    private static boolean sameUppercasePublisherName(
            String text, String name, boolean allowNativeLowercaseInitial) {
        String tail = clean(name).substring(1).toUpperCase(Locale.ROOT);
        return Pattern.compile(
                        "(?<![\\p{L}\\p{N}])"
                                + (allowNativeLowercaseInitial ? "(?:I|l)" : "I")
                                + Pattern.quote(tail)
                                + "\\h+MUSIC\\b")
                .matcher(clean(text))
                .find();
    }

    /** Footer text can corroborate a labelled name's letters; it never assigns a contributor role. */
    public static List<Line> contributorCaseCorroborationCandidates(Page page) {
        List<Line> result = new ArrayList<>();
        for (Line footer : page.lines) {
            if (footer.top < page.height * .75f
                    || footer.bottom > page.height
                    || footer.height() < 16
                    || footer.height() > page.width * .035f
                    || footer.right - footer.left < 32
                    || clean(footer.text).length() > 220
                    || !clean(footer.text)
                            .matches("(?iu)^(?:copyright\\b|all\\h+rights\\h+for\\b).+")) continue;
            boolean matches = false;
            for (Line header : page.lines)
                if (labelledContributorHeader(page, header)) {
                    Matcher name = LOWERCASE_INITIAL_NAME.matcher(credit(clean(header.text)).value);
                    while (name.find())
                        if (sameUppercasePublisherName(footer.text, name.group(), true)) {
                            matches = true;
                            break;
                        }
                    if (matches) break;
                }
            if (matches) result.add(footer);
        }
        return List.copyOf(result);
    }

    public static List<Line> mergeContributorCaseCorroboration(
            Page page, Line footer, List<String> readings) {
        if (!contributorCaseCorroborationCandidates(page).contains(footer)
                || readings == null
                || readings.size() != 3
                || readings.stream().anyMatch(v -> v == null || v.isBlank())) return page.lines;
        String source = clean(readings.get(0)), key = normalizedBibliographicReading(source);
        if (readings.stream().anyMatch(v -> !normalizedBibliographicReading(v).equals(key))
                || !CreditOcrIdentifierGuard.preservesIdentifiers(footer.text, source))
            return page.lines;
        List<Line> merged = new ArrayList<>(page.lines);
        for (int i = 0; i < merged.size(); i++) {
            Line header = merged.get(i);
            if (!labelledContributorHeader(page, header)) continue;
            String before = clean(header.text);
            Matcher name = LOWERCASE_INITIAL_NAME.matcher(before);
            StringBuffer updated = new StringBuffer();
            boolean changed = false;
            while (name.find()) {
                String value = name.group();
                if (sameUppercasePublisherName(source, value, false)) {
                    value = "I" + value.substring(1);
                    changed = true;
                }
                name.appendReplacement(updated, Matcher.quoteReplacement(value));
            }
            name.appendTail(updated);
            if (!changed) continue;
            String after = updated.toString();
            Credit original = credit(before), candidate = credit(after);
            if (candidate == null
                    || original.role != candidate.role
                    || !sameConsensusContributorCount(original.value, candidate.value)
                    || !CreditOcrIdentifierGuard.preservesIdentifiers(before, after)) continue;
            merged.set(i, new Line(after, header.left, header.top, header.right, header.bottom));
        }
        return List.copyOf(merged);
    }

    /** Select a prominent opening heading for conservative source-pixel word-gap analysis. */
    public static Line trackedHeadingWordSpaceCandidate(Page page) {
        if (page.creditsOnly || page.width <= 0 || page.height <= 0) return null;
        float limit = page.notationTop > 0 ? page.notationTop : page.height * .62f;
        Line heading = chooseTitle("", page, page.lines, limit);
        if (heading == null
                || heading.top > page.height * .2f
                || heading.bottom > limit
                || heading.height() < page.width * .025f
                || heading.height() > page.width * .09f
                || heading.right - heading.left < heading.height() * 6
                || Math.abs(heading.center() - page.width * .5f) > page.width * .2f
                || !Float.isFinite(heading.left)
                || !Float.isFinite(heading.top)
                || !Float.isFinite(heading.right)
                || !Float.isFinite(heading.bottom)
                || heading.left < 0
                || heading.top < 0
                || heading.right > page.width
                || heading.bottom > page.height
                || !clean(heading.text).matches("[\\p{Lu}'’\\h]+")
                || letterCount(heading.text) < 5
                || letterCount(heading.text) > 60) return null;
        return heading;
    }

    /** Pixel spacing cannot change a letter, punctuation mark, or credit field. */
    public static List<Line> mergeTrackedHeadingWordSpaces(
            Page page, Line original, String refined) {
        if (original == null
                || !original.equals(trackedHeadingWordSpaceCandidate(page))
                || refined == null
                || refined.equals(original.text)
                || !refined.matches("[\\p{Lu}'’\\h]+")
                || !original.text.replaceAll("\\h+", "").equals(refined.replaceAll("\\h+", "")))
            return page.lines;
        List<Line> merged = new ArrayList<>(page.lines);
        int index = merged.indexOf(original);
        if (index < 0) return page.lines;
        merged.set(
                index,
                new Line(refined, original.left, original.top, original.right, original.bottom));
        return List.copyOf(merged);
    }

    /** Joined opening-heading letters may gain spaces only after three pixel readings agree. */
    private static boolean joinedOpeningHeading(Page page, Line line) {
        String value = clean(line.text);
        if (page.creditsOnly
                || page.notationTop <= 0
                || line.bottom >= page.notationTop
                || line.top > page.height * .2f
                || line.height() < page.width * .025f
                || line.height() > page.width * .09f
                || Math.abs(line.center() - page.width * .5f) > page.width * .2f
                || value.length() < 6
                || value.length() > 80
                || !value.codePoints().allMatch(Character::isLetter)
                || !titleText(value)) return false;
        return line.equals(chooseTitle("", page, page.lines, page.notationTop));
    }

    /** A large primary opening heading may be reread while retaining a printed word anchor. */
    private static boolean wordedOpeningHeading(Page page, Line line) {
        String value = clean(line.text);
        if (page.creditsOnly
                || page.notationTop <= 0
                || line.bottom >= page.notationTop
                || line.top > page.height * .2f
                || line.height() < page.width * .025f
                || line.height() > page.width * .09f
                || Math.abs(line.center() - page.width * .5f) > page.width * .2f
                || value.length() < 6
                || value.length() > 80
                || credit(value) != null
                || !titleText(value)) return false;
        return line.equals(chooseTitle("", page, page.lines, page.notationTop));
    }

    /** Three confident original readings can recover an unreadable primary score heading. */
    public static List<Line> mergeHighConfidenceOriginalHeading(
            Page page, Line original, List<String> readings, List<Float> confidence) {
        if (!wordedOpeningHeading(page, original)
                || readings == null
                || confidence == null
                || readings.size() != 3
                || confidence.size() != 3
                || readings.stream().anyMatch(value -> value == null || value.isBlank())
                || confidence.stream()
                        .anyMatch(
                                value ->
                                        value == null
                                                || !Float.isFinite(value)
                                                || value < .98f
                                                || value > 1f)) return page.lines;
        String candidate = clean(readings.get(0)), key = normalizedBibliographicReading(candidate);
        if (readings.stream().anyMatch(value -> !normalizedBibliographicReading(value).equals(key))
                || !titleText(candidate)
                || credit(candidate) != null
                || !CreditOcrIdentifierGuard.preservesIdentifiers(original.text, candidate)
                || !preservesHeadingShape(original.text, candidate)) return page.lines;
        List<Line> merged = new ArrayList<>(page.lines);
        int index = merged.indexOf(original);
        if (index < 0) return page.lines;
        merged.set(
                index,
                new Line(candidate, original.left, original.top, original.right, original.bottom));
        return List.copyOf(merged);
    }

    private static boolean sharesHeadingWord(String before, String after) {
        Set<String> words = new LinkedHashSet<>();
        Matcher first =
                Pattern.compile("\\p{L}{4,}").matcher(normalizedBibliographicReading(before));
        while (first.find()) words.add(first.group());
        Matcher next = Pattern.compile("\\p{L}{4,}").matcher(normalizedBibliographicReading(after));
        while (next.find()) if (words.contains(next.group())) return true;
        return headingLetters(before).equals(headingLetters(after))
                || nearbyHeadingLetters(before, after);
    }

    /** Long fragmented headings may retain every letter position except two OCR substitutions. */
    private static boolean nearbyHeadingLetters(String before, String after) {
        int[] first =
                normalizedBibliographicReading(before)
                        .replaceAll("[^\\p{L}]", "")
                        .codePoints()
                        .toArray();
        int[] next =
                normalizedBibliographicReading(after)
                        .replaceAll("[^\\p{L}]", "")
                        .codePoints()
                        .toArray();
        if (first.length < 12 || first.length != next.length) return false;
        int differences = 0;
        for (int i = 0; i < first.length; i++)
            if (first[i] != next[i] && ++differences > 2) return false;
        return true;
    }

    /** A secondary reader cannot silently remove a leading initial/article or introduce mixed quotes. */
    private static boolean preservesHeadingShape(String before, String after) {
        String first = headingFirstWord(before), next = headingFirstWord(after);
        if (!first.isEmpty()
                && (first.codePointCount(0, first.length()) == 1
                        || first.equals("an")
                        || first.equals("the"))
                && !first.equals(next)) return false;
        return !mixedHeadingQuotes(after) || mixedHeadingQuotes(before);
    }

    private static String headingFirstWord(String value) {
        Matcher word =
                Pattern.compile("^[\\p{Punct}“”‘’\\h]*(\\p{L}+)(?:\\h|$)")
                        .matcher(normalizedBibliographicReading(value));
        return word.find() ? word.group(1) : "";
    }

    private static boolean mixedHeadingQuotes(String value) {
        return value.indexOf('"') >= 0 && (value.indexOf('“') >= 0 || value.indexOf('”') >= 0);
    }

    private static String headingLetters(String value) {
        return normalizedBibliographicReading(value).replaceAll("\\s+", "");
    }

    private static String normalizedBibliographicReading(String text) {
        return Normalizer.normalize(clean(text), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'');
    }

    /** Crop adjustment follows an unresolved original reading; stable native text is retained. */
    public static List<Line> mergeBibliographicFallbackConsensus(
            Page page, Line original, List<String> baseline, List<String> adjusted) {
        List<Line> first = mergeBibliographicLineConsensus(page, original, baseline);
        if (!first.equals(page.lines)) {
            if (!diacriticOnlyContributorChange(page, original, first)) return first;
            List<Line> confirmation = mergeBibliographicLineConsensus(page, original, adjusted);
            int index = page.lines.indexOf(original);
            return !confirmation.equals(page.lines)
                            && normalizedBibliographicReading(first.get(index).text)
                                    .equals(
                                            normalizedBibliographicReading(
                                                    confirmation.get(index).text))
                    ? first
                    : page.lines;
        }
        if (!bibliographicFallbackNeeded(page, original, baseline)) return page.lines;
        return mergeBibliographicLineConsensus(page, original, adjusted);
    }

    /** A tighter original-PDF crop is reserved for a still-unresolved primary heading. */
    public static boolean bibliographicTightHeadingNeeded(
            Page page, Line original, List<String> baseline, List<String> adjusted) {
        if (!(joinedOpeningHeading(page, original) || wordedOpeningHeading(page, original)))
            return false;
        return mergeBibliographicFallbackConsensus(page, original, baseline, adjusted)
                        .equals(page.lines)
                && bibliographicFallbackNeeded(page, original, baseline);
    }

    /** Avoid an extra render/model pass when original consensus is accepted or stable. */
    public static boolean bibliographicFallbackNeeded(
            Page page, Line original, List<String> baseline) {
        if (!page.lines.contains(original)
                || !bibliographicLineCandidate(page, original)
                || baseline == null
                || baseline.size() != 3
                || baseline.stream().anyMatch(s -> s == null || s.isBlank())) return false;
        List<Line> first = mergeBibliographicLineConsensus(page, original, baseline);
        if (!first.equals(page.lines)) return diacriticOnlyContributorChange(page, original, first);
        String nativeKey = normalizedBibliographicReading(original.text);
        return !baseline.stream()
                .allMatch(s -> normalizedBibliographicReading(s).equals(nativeKey));
    }

    /** Accent-only name repairs need the independently selected tighter pixel band. */
    private static boolean diacriticOnlyContributorChange(
            Page page, Line original, List<Line> first) {
        int index = page.lines.indexOf(original);
        if (index < 0 || first.equals(page.lines)) return false;
        Credit before = credit(clean(original.text)), after = credit(clean(first.get(index).text));
        if (before == null || after == null || before.role != after.role) return false;
        String a = normalizedBibliographicReading(before.value),
                b = normalizedBibliographicReading(after.value);
        return !a.equals(b) && withoutDiacritics(a).equals(withoutDiacritics(b));
    }

    private static String withoutDiacritics(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /** A near arrangement label is only eligible evidence; detect() still requires a real label. */
    private static Credit misspelledArrangementCredit(String value) {
        Matcher m =
                Pattern.compile("(?iu)^(arr[\\p{L}]{5}|transcr[\\p{L}]{3,5})\\h+by\\h+(.+)$")
                        .matcher(clean(value));
        if (!m.matches()) return null;
        String word = m.group(1).toLowerCase(Locale.ROOT), canonical = "arranged";
        if (word.startsWith("transcr"))
            return editDistance(word, "transcribed") == 1 && creditNameText(m.group(2))
                    ? new Credit(Role.ARRANGER, m.group(2), false)
                    : null;
        if (word.length() != canonical.length()) return null;
        int changes = 0;
        for (int i = 0; i < canonical.length(); i++)
            if (word.charAt(i) != canonical.charAt(i)) changes++;
        return changes >= 1 && changes <= 2 ? new Credit(Role.ARRANGER, m.group(2), false) : null;
    }

    /** A terminal sentence period may vary across an explicitly labelled name reading. */
    private static String bibliographicConsensusReadingKey(Line original, String reading) {
        String value = clean(reading);
        Credit before = credit(clean(original.text)), after = credit(value);
        if (before != null
                && after != null
                && !before.suffix
                && !after.suffix
                && before.role == after.role
                && value.matches("(?s).*\\p{L}{3,}\\."))
            value = value.substring(0, value.length() - 1);
        return normalizedBibliographicReading(value);
    }

    /** Three resamples of one model must agree, retain roles, contributors and work identifiers. */
    private static boolean preservesBibliographicIdentifiers(
            Page page, Line original, String candidate) {
        if (CreditOcrIdentifierGuard.preservesIdentifiers(original.text, candidate)) return true;
        if (!wordedOpeningHeading(page, original)) return false;
        String[] before = clean(original.text).split("\\s+"),
                after = clean(candidate).split("\\s+");
        if (before.length != after.length) return false;
        for (int i = 0; i < before.length; i++) {
            String a = before[i], b = after[i];
            if (a.length() >= 5
                    && b.length() >= 4
                    && a.matches("\\p{L}+\\p{Nd}\\p{L}{2,}")
                    && b.codePoints().allMatch(Character::isLetter)
                    && !a.matches("(?iu)^(?:op|opus|bwv|rv|hwv|kv|qv|woo|hob)\\p{Nd}.*")
                    && editDistance(a.toLowerCase(Locale.ROOT), b.toLowerCase(Locale.ROOT)) <= 2)
                before[i] = b;
        }
        return CreditOcrIdentifierGuard.preservesIdentifiers(String.join(" ", before), candidate);
    }

    /** Only a primary title can tolerate one omitted letter in the third original-pixel reading. */
    private static String headingSingleLetterLossConsensus(
            Page page, Line original, List<String> readings) {
        if (!wordedOpeningHeading(page, original)) return null;
        for (int i = 0; i < 3; i++)
            for (int j = i + 1; j < 3; j++) {
                String value = clean(readings.get(i)), key = normalizedBibliographicReading(value);
                if (!key.equals(normalizedBibliographicReading(readings.get(j)))) continue;
                String other = normalizedBibliographicReading(readings.get(3 - i - j));
                int[] full = key.codePoints().toArray(), shorter = other.codePoints().toArray();
                if (full.length != shorter.length + 1) continue;
                int at = 0;
                while (at < shorter.length && full[at] == shorter[at]) at++;
                if (!Character.isLetter(full[at])) continue;
                boolean rest = true;
                for (int k = at; k < shorter.length; k++)
                    if (full[k + 1] != shorter[k]) {
                        rest = false;
                        break;
                    }
                if (rest
                        && CreditOcrIdentifierGuard.preservesIdentifiers(
                                value, readings.get(3 - i - j))) return value;
            }
        return null;
    }

    private static String bibliographicConsensusCandidate(
            Page page, Line original, List<String> readings) {
        String value = clean(readings.get(0)),
                key = bibliographicConsensusReadingKey(original, value);
        if (readings.stream()
                .allMatch(s -> bibliographicConsensusReadingKey(original, s).equals(key)))
            return value;
        return headingSingleLetterLossConsensus(page, original, readings);
    }

    public static List<Line> mergeBibliographicLineConsensus(
            Page page, Line original, List<String> readings) {
        if (!page.lines.contains(original)
                || !bibliographicLineCandidate(page, original)
                || readings == null
                || readings.size() != 3
                || readings.stream().anyMatch(s -> s == null || s.isBlank())) return page.lines;
        String consensus = bibliographicConsensusCandidate(page, original, readings);
        if (consensus == null) return page.lines;
        String candidate = clean(consensus),
                key = bibliographicConsensusReadingKey(original, candidate);
        if (key.equals(bibliographicConsensusReadingKey(original, original.text)))
            return page.lines;
        // Prefer an actual punctuation-free sample when the other two readings agree on every
        // letter.
        if (candidate.endsWith("."))
            for (String reading : readings) {
                String value = clean(reading);
                if (!value.endsWith(".")
                        && bibliographicConsensusReadingKey(original, value).equals(key)) {
                    candidate = value;
                    break;
                }
            }
        if (!preservesBibliographicIdentifiers(page, original, candidate)) return page.lines;
        Credit before = credit(clean(original.text)), after = credit(candidate);
        boolean correctedArrangementLabel =
                insertedTranscriptionLetterCredit(clean(original.text)) != null;
        if (before == null) {
            before = misspelledArrangementCredit(clean(original.text));
            correctedArrangementLabel = before != null;
        }
        if (before != null) {
            if (before.suffix
                    || after == null
                    || after.suffix
                    || before.role != after.role
                    || after.value.isBlank()) return page.lines;
            String firstLabel =
                    clean(original.text)
                            .substring(0, clean(original.text).length() - before.value.length());
            String nextLabel = candidate.substring(0, candidate.length() - after.value.length());
            boolean sameLabel =
                    normalizedBibliographicReading(firstLabel)
                            .equals(normalizedBibliographicReading(nextLabel));
            String confirmedRoleLabel =
                    clean(original.text).toLowerCase(Locale.ROOT).startsWith("transcr")
                            ? "transcribed by"
                            : "arranged by";
            if (!(sameLabel
                            || correctedArrangementLabel
                                    && normalizedBibliographicReading(nextLabel)
                                            .equals(confirmedRoleLabel))
                    || creditNames(before.value).isEmpty()
                    || !sameConsensusContributorCount(before.value, after.value)) return page.lines;
        } else if (printedCreditContinuation(page, original) != null) {
            if (after != null
                    || !creditNameText(candidate)
                    || deletesContinuationBoundaryLetter(clean(original.text), candidate)
                    || !sameConsensusContributorCount(clean(original.text), candidate))
                return page.lines;
        } else if (joinedOpeningHeading(page, original)) {
            String[] words = candidate.split("\\s+");
            if (after != null
                    || words.length < 2
                    || words.length > 4
                    || Arrays.stream(words)
                            .anyMatch(
                                    w ->
                                            w.length() < 2
                                                    || !w.codePoints()
                                                            .allMatch(Character::isLetter))
                    || !headingLetters(original.text).equals(headingLetters(candidate)))
                return page.lines;
        } else if (wordedOpeningHeading(page, original)) {
            if (after != null
                    || !titleText(candidate)
                    || !sharesHeadingWord(original.text, candidate)
                    || !preservesHeadingShape(original.text, candidate)) return page.lines;
        } else if (!(candidate.startsWith("(") && candidate.endsWith(")"))) return page.lines;
        List<Line> merged = new ArrayList<>(page.lines);
        merged.set(
                merged.indexOf(original),
                new Line(candidate, original.left, original.top, original.right, original.bottom));
        return List.copyOf(merged);
    }

    private static float coverTitleLimit(Page page) {
        return page.height * (page.photographicCover ? .9f : .62f);
    }

    /** Merge only a complete, pixel-read cover title and retain independent credits/captions. */
    public static List<Line> mergePixelCoverTitle(String hint, Page page, List<Line> cropped) {
        if (page.notationTop > 0 && !page.photographicCover) return page.lines;
        List<Line> eligible =
                cropped.stream()
                        .filter(
                                l ->
                                        l.top < coverTitleLimit(page)
                                                && (titleText(l.text)
                                                        || clean(l.text)
                                                                .matches("(?iu)\\p{L}{1,2}"))
                                                && !DATED_AUTHOR.matcher(clean(l.text)).matches()
                                                && !paragraphLine(page, l, cropped))
                        .toList();
        var selected = PrintedCoverTitleEvidence.select(hint, page.width, eligible);
        if (selected == null) return page.lines;
        // A verified stacked heading has two full-height outer fragments and
        // two smaller words in their shared gap. Preserve their source reading
        // as one heading so later title/name filters do not drop short words.
        var parts = selected.lines();
        if (parts.size() == 4
                && parts.get(0).right <= parts.get(3).left
                && Math.abs(parts.get(0).top - parts.get(3).top)
                        < Math.max(parts.get(0).height(), parts.get(3).height()) * .2f) {
            float left = parts.stream().map(Line::left).min(Float::compare).orElse(0f);
            float top = parts.stream().map(Line::top).min(Float::compare).orElse(0f);
            float right = parts.stream().map(Line::right).max(Float::compare).orElse(0f);
            float bottom = parts.stream().map(Line::bottom).max(Float::compare).orElse(0f);
            selected =
                    new PrintedCoverTitleEvidence.Evidence(
                            selected.text(),
                            List.of(new Line(selected.text(), left, top, right, bottom)));
        }
        final var selectedParts = selected.lines();
        String chosen = fold(selected.text());
        // A shorter hint cannot truncate a longer heading already printed on the page.
        if (page.lines.stream()
                .anyMatch(
                        l ->
                                titleText(l.text)
                                        && fold(l.text).startsWith(chosen)
                                        && fold(l.text).length() > chosen.length()
                                        && selectedParts.stream()
                                                .anyMatch(
                                                        s ->
                                                                l.height() >= s.height() * .8f
                                                                        && Math.abs(l.top - s.top)
                                                                                < s.height()
                                                                                        * .5f)))
            return page.lines;
        // A matching import label cannot override a printed work identifier.
        for (Line original : page.lines)
            if (titleText(original.text))
                for (Line next : selected.lines()) {
                    float w =
                            Math.max(
                                    0,
                                    Math.min(original.right, next.right)
                                            - Math.max(original.left, next.left));
                    float h =
                            Math.max(
                                    0,
                                    Math.min(original.bottom, next.bottom)
                                            - Math.max(original.top, next.top));
                    if (original.height() >= next.height() * .65f
                            && original.height() <= next.height() * 1.5f
                            && w * h
                                    >= Math.max(
                                                    1,
                                                    (original.right - original.left)
                                                            * original.height())
                                            * .65f
                            && !CreditOcrIdentifierGuard.preservesIdentifiers(
                                    original.text, next.text)) return page.lines;
                }
        List<Line> merged = new ArrayList<>();
        for (Line original : page.lines) {
            boolean replaced =
                    titleText(original.text)
                            && selectedParts.stream()
                                    .anyMatch(
                                            next -> {
                                                float w =
                                                        Math.max(
                                                                0,
                                                                Math.min(original.right, next.right)
                                                                        - Math.max(
                                                                                original.left,
                                                                                next.left));
                                                float h =
                                                        Math.max(
                                                                0,
                                                                Math.min(
                                                                                original.bottom,
                                                                                next.bottom)
                                                                        - Math.max(
                                                                                original.top,
                                                                                next.top));
                                                return original.height() >= next.height() * .65f
                                                        && original.height() <= next.height() * 1.5f
                                                        && w * h
                                                                >= Math.max(
                                                                                1,
                                                                                (original.right
                                                                                                - original.left)
                                                                                        * original
                                                                                                .height())
                                                                        * .65f;
                                            });
            if (!replaced) merged.add(original);
        }
        merged.addAll(selected.lines());
        merged.sort(Comparator.comparingDouble((Line l) -> l.top).thenComparingDouble(l -> l.left));
        return List.copyOf(merged);
    }

    /** OCR may join a large heading and a smaller byline from another column. */
    public static List<Line> splitOcrColumns(Line original, List<Line> words) {
        List<Line> rows = splitPrintedRows(original, words);
        if (rows.size() > 1) {
            List<Line> split = new ArrayList<>();
            for (Line row : rows)
                split.addAll(
                        splitOcrColumns(
                                row,
                                words.stream()
                                        .filter(
                                                w ->
                                                        w.top >= row.top
                                                                && w.bottom <= row.bottom
                                                                && w.left >= row.left
                                                                && w.right <= row.right)
                                        .toList()));
            return List.copyOf(split);
        }
        if (words.size() < 3) return List.of(original);
        List<Line> groups = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < words.size(); i++) {
            Line previous = words.get(i - 1), next = words.get(i);
            float gap = next.left - previous.right;
            boolean smallerColumn =
                    next.height() < previous.height() * .65f
                            && gap > previous.height() * .6f
                            && previous.right - words.get(start).left > previous.height() * 3f
                            && i + 1 < words.size()
                            && Math.abs(words.get(i + 1).height() - next.height())
                                    < next.height() * .3f;
            if (!smallerColumn) continue;
            groups.add(wordGroup(words, start, i));
            start = i;
        }
        if (groups.isEmpty()) return List.of(original);
        groups.add(wordGroup(words, start, words.size()));
        return List.copyOf(groups);
    }

    private static List<Line> splitPrintedRows(Line original, List<Line> words) {
        float tallest = words.stream().map(Line::height).max(Float::compare).orElse(0f);
        if (words.size() < 4 || tallest < 12 || original.height() < tallest * 1.45f)
            return List.of(original);
        List<List<Line>> groups = new ArrayList<>();
        for (Line word : words.stream().sorted(Comparator.comparingDouble(Line::top)).toList()) {
            List<Line> found = null;
            for (List<Line> group : groups) {
                Line row = wordGroup(group, 0, group.size());
                float overlap = Math.min(word.bottom, row.bottom) - Math.max(word.top, row.top);
                if (overlap >= Math.min(word.height(), row.height()) * .5f
                        && Math.abs((word.top + word.bottom - row.top - row.bottom) * .5f)
                                <= Math.max(word.height(), row.height()) * .45f) {
                    found = group;
                    break;
                }
            }
            if (found == null) {
                found = new ArrayList<>();
                groups.add(found);
            }
            found.add(word);
            if (groups.size() > 5) return List.of(original);
        }
        if (groups.size() < 2) return List.of(original);
        List<Line> rows = new ArrayList<>();
        for (List<Line> group : groups) {
            if (group.size() < 2) return List.of(original);
            group.sort(Comparator.comparingDouble(Line::left));
            Line row = wordGroup(group, 0, group.size());
            if (letterCount(row.text) < 4) return List.of(original);
            rows.add(row);
        }
        rows.sort(Comparator.comparingDouble(Line::top));
        for (int i = 1; i < rows.size(); i++)
            if (rows.get(i).top < rows.get(i - 1).bottom) return List.of(original);
        String joined = String.join(" ", rows.stream().map(Line::text).toList());
        return joined.replaceAll("\\s+", "").equals(original.text.replaceAll("\\s+", ""))
                ? List.copyOf(rows)
                : List.of(original);
    }

    private static Line wordGroup(List<Line> words, int first, int end) {
        StringBuilder text = new StringBuilder();
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY, right = 0, bottom = 0;
        for (int i = first; i < end; i++) {
            Line word = words.get(i);
            if (text.length() > 0) text.append(' ');
            text.append(word.text);
            left = Math.min(left, word.left);
            top = Math.min(top, word.top);
            right = Math.max(right, word.right);
            bottom = Math.max(bottom, word.bottom);
        }
        return new Line(text.toString(), left, top, right, bottom);
    }

    private enum Role {
        ARTIST,
        COMPOSER,
        ARRANGER,
        LYRICS
    }

    private record Credit(Role role, String value, boolean suffix) {}

    private static final String RECORDING_INSTRUMENT =
            "(?:additional\\h+)?"
                    + "(?:(?:electric|acoustic)(?:\\h+(?:and|&)\\h+(?:electric|acoustic))?\\h+)?"
                    + "(?:brass|bass|bodhr[aá]n|(?:violin|fiddle|viola|cello|piano|guitar|flute|clarinet|vocal|keyboard|drumset|drum|percussion)s?)";
    private static final Pattern RECORDING_PERSON =
            Pattern.compile(
                    "(?iu)^(.{3,100}?)[,;.]\\h*"
                            + RECORDING_INSTRUMENT
                            + "(?:(?:\\h*[,;./]\\h*(?:(?:and|&)\\h+)?|\\h+(?:and|&)\\h+)"
                            + RECORDING_INSTRUMENT
                            + ")*$");
    private static final Pattern RECORDING_WORK_ROLES =
            Pattern.compile(
                    "(?iu)^((?:arranged|recorded|produced)"
                            + "(?:(?:,\\h*(?:(?:and|&)\\h+)?|\\h+(?:and|&)\\h+)(?:arranged|recorded|produced)){1,2})"
                            + "\\h+by[\\h.:]+(.+)$");
    private static final Pattern CREDIT =
            Pattern.compile(
                    "(?iu)^(?:(?:(?:and|&)\\h+)?(?:\"[^\"]+\"|“[^”]+”)\\h+)?"
                            + "(?:[\\p{L}()][\\p{L}().-]{1,18}\\h+(?=(?:arr|transcr)))?"
                            + "(as\\h+(?:originally\\h+)?(?:performed|recorded|played)\\h+by|originally\\h+recorded\\h+by"
                            + "|performed\\h+by|recorded\\h+by|played\\h+by|featuru?ing|feat\\.?|artists?"
                            + "|words\\h+(?:and|&)\\h+music\\h+by|words\\h+by|music\\h+(?:and|&)\\h+lyr?ics\\h+by|music\\h+by|composed\\h+by|written\\h+by|composers?|by"
                            + "|using\\h+the\\h+arrangement\\h+of|(?:and\\h+the\\h+)?adaptation\\h+by|(?:and\\h+the\\h+)?arrangement\\h+for\\h+[^:;]{1,60}?\\h+by"
                            + "|arranged\\h+for\\h+[^:;]{1,60}?\\h+by|arranged\\h+by|arrangements?\\h+by|transcribed\\h+by|transc(?:r)?iption\\h+by|transcr\\.?\\h+by|transcr\\.?|arrangers?|arr\\.?\\h+by|ar\\.\\h+by|arr\\.?|arrangements?"
                            + "|(?:additional\\h+)?lyrics\\h+by)\\b[\\h.:=–—-]*(.*)$");
    private static final Pattern COMBINED_COMPOSITION_ARRANGEMENT =
            Pattern.compile(
                    "(?iu)^(?:(?:music|score)(?:\\h+(?:and|&)\\h+(?:title|theme)\\h+song)?\\h+)?"
                            + "((?:composed|written|arranged|conducted)(?:(?:,\\h*(?:(?:and|&)\\h+)?|\\h+(?:and|&)\\h+)"
                            + "(?:composed|written|arranged|conducted)){1,3})\\h+by(?:\\b|(?-i:(?=\\p{Lu})))[\\h.:=–—-]*(.*)$");
    private static final Pattern NOT_TITLE =
            Pattern.compile(
                    "(?iu)^(?:from\\b|(?:for\\h+(?:(?:[0-9]+|one|two|three|four|five|six|seven|eight)\\h+)?)?(?:(?:soprano|alto|tenor|bass|treble|descant)\\h+)?(?:violin|viola|viol|cello|piano|harp|guitar|accordion|flute|clarinet|oboe|bassoon|trumpet|trombone|horn|tuba|saxophone|recorder|vibraphone|synth(?:esizer)?|percussion|drums|orchestra|strings|voices?|vocals?|choir|score|tablature|tab)s?"
                            + "(?:\\h*(?:[0-9]+|[IVX]+)|\\h+(?:solo|duet|trio|quartet|part|upper\\h+staff|lower\\h+staff|staff|SATB|SSA|SSAA|TTBB)(?:\\h.*)?|\\h*[-–]\\h*(?:upper|lower)\\h+staff)?$"
                            + "|(?:allegro|andante|adagio|adagietto|moderato|moderate(?:ly)?|largo|lento|presto|prestissimo|slowly|quickly|fast|slow|play|arco|pizz\\.?|dolce|rit\\.?|dim\\.?|cantabile|sostenuto|deliberately|passionately|euphoric|tenderly|con\\h|with\\h)\\b"
                            + "|(?:to\\h+coda|coda|d\\.?s\\.?|d\\.?c\\.?)(?:\\h|$)"
                            + "|scan\\h+to|track\\h+[0-9]|tempo\\b|key\\b|time\\h+signature\\b)");
    private static final Pattern LEGAL =
            Pattern.compile(
                    "(?iu)(?:copyright|all rights|downloaded|sharing and|photocopy|publishing|pub designee|musescore\\.com|https?://|www\\.|©)"
                            + "|^\\h*available\\h+(?:from|at|on)\\h+[\\p{L}\\p{N}][\\p{L}\\p{N}.-]*\\.[\\p{L}]{2,24}(?:/\\S*)?[.!]?\\h*$");
    private static final Pattern FEATURED =
            Pattern.compile("(?iu)^(.{3,80}?)[\\h,.;]+(?:[FÉ]EAT(?:URING)?)[\\h,.;:]+(.{3,80})$");
    private static final Pattern DATED_AUTHOR =
            Pattern.compile(
                    "(?iu)^([\\p{L} .’'–-]{3,80}?)\\h*\\((?:1[5-9][0-9]{2}|20[0-9]{2})\\h*[-–—]\\h*(?:1[5-9][0-9]{2}|20[0-9]{2})\\)\\h*$");
    private static final Pattern REVIEW_DIRECTION =
            Pattern.compile("(?iu)^(?:a\\h+tempo|rit(?:ard(?:ando)?)?\\.?|rall(?:entando)?\\.?)$");
    private static final Pattern INITIALLED_NAME =
            Pattern.compile("^(?:\\p{Lu}\\.\\h*){1,3}\\p{Lu}[\\p{L}’'-]+$");
    private static final Pattern MOVEMENT_SUBTITLE =
            Pattern.compile(
                    "(?iu)^(?:(?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|[1-9](?:st|nd|rd|th)|[Il]st|[IVX]+)\\h+movement(?:\\h+excerpt)?(?:\\h+[\"“][^\"”]{2,80}[\"”])?"
                            + "|(?:op(?:us)?\\.?(?:\\h+posth(?:umous)?\\.?)?|WoO|BWV|K\\.?|L\\.?|D\\.?|RV|HWV|Hob\\.?)\\h*[0-9]+[a-z]?(?:\\h*[,;]?\\h*no\\.?\\h*[0-9]+[a-z]?)?)$");
    private static final Pattern CATALOGUE_MOVEMENT =
            Pattern.compile(
                    "(?iu)^(?:sonat[ae]|symphony|concerto|quartet)\\h+(?:op(?:us)?\\.?|K\\.?|BWV|D\\.?|L\\.?|RV|HWV)\\h*[0-9]+[a-z]?"
                            + "(?:\\h*[,;]?\\h*no\\.?\\h*[0-9]+)?\\h*\\(([1-9])(?:°|º|st|nd|rd|th)?\\h+(?:mvt\\.?|movement)\\)$");
    private static final Pattern MOVEMENT_FRAGMENT =
            Pattern.compile("(?iu)^\\(?([1-9])(?:°|º|0|st|nd|rd|th)?\\h+(?:mvt\\.?|movement)\\)?$");

    private ScoreCreditsDetector() {}

    /** Accept only source-corroborated heading spaces; preserve all other header evidence. */
    public static List<Line> mergeHeaderTitleSpacing(String hint, Page page, List<Line> cropped) {
        if (cropped.isEmpty() || page.notationTop < 32) return page.lines;
        Line full = chooseTitle(hint, page, page.lines, page.notationTop);
        Line crop =
                chooseTitle(
                        hint,
                        new Page(page.width, page.height, page.notationTop, cropped),
                        cropped,
                        page.notationTop);
        if (full == null
                || crop == null
                || !clean(crop.text).equalsIgnoreCase(clean(hint))
                || clean(full.text).equalsIgnoreCase(clean(crop.text))
                || !clean(full.text)
                        .replaceAll("\\h+", "")
                        .equalsIgnoreCase(clean(crop.text).replaceAll("\\h+", "")))
            return page.lines;
        float height = Math.max(full.height(), crop.height());
        if (height <= 0
                || Math.min(full.height(), crop.height()) < height * .75f
                || Math.abs((full.top + full.bottom - crop.top - crop.bottom) * .5f) > height * .3f
                || Math.abs(full.center() - crop.center()) > height * .5f
                || Math.min(full.right, crop.right) - Math.max(full.left, crop.left)
                        < Math.min(full.right - full.left, crop.right - crop.left) * .85f)
            return page.lines;
        int index = page.lines.indexOf(full);
        if (index < 0) return page.lines;
        List<Line> result = new ArrayList<>(page.lines);
        result.set(index, new Line(clean(crop.text), full.left, full.top, full.right, full.bottom));
        return List.copyOf(result);
    }

    /** Select OCR context from printed heading evidence, never synthesize a title from the hint. */
    public static boolean preferHeaderCrop(Page page, List<Line> cropped, String hint) {
        if (cropped.isEmpty() || page.notationTop < 32) return false;
        Line full = chooseTitle(hint, page, page.lines, page.notationTop);
        Line crop =
                chooseTitle(
                        hint,
                        new Page(page.width, page.height, page.notationTop, cropped),
                        cropped,
                        page.notationTop);
        if (crop == null) {
            // Full-page OCR can turn an upright small instrument label backwards.
            // A crop that reads the same ink as a known label can establish that
            // there is no title here, even though it has no title candidate itself.
            if (full == null
                    || same(full.text, hint)
                    || headerHintMatches(full.text, hint)
                    || full.height() >= page.width * .03f) return false;
            for (Line label : cropped)
                if (instrumentOrPlayLabel(label.text)
                        && label.top < page.notationTop
                        && label.height() >= full.height() * .5f
                        && label.height() <= full.height() * 1.5f
                        && Math.abs((label.top + label.bottom - full.top - full.bottom) * .5f)
                                < full.height() * .7f
                        && Math.abs(label.center() - full.center()) < full.height() * .7f
                        && Math.min(label.right, full.right) - Math.max(label.left, full.left)
                                > Math.min(label.right - label.left, full.right - full.left) * .6f)
                    return true;
            return false;
        }
        float minimumHeight =
                full == null && letterCount(crop.text) >= 4
                        ? page.width * .009f
                        : page.width * .012f;
        if (crop.top > page.height * .35f || crop.height() < minimumHeight) return false;
        if (full != null) {
            // A matching hint cannot invent title text. It can corroborate a
            // second OCR reading of the same ink when only I/l was confused.
            if (corroboratedTitleStem(full, crop, hint)) return true;
            String a = fold(full.text), b = fold(crop.text);
            if (editDistance(a, b) <= Math.max(a.length(), b.length()) * .3f) return false;
            if (headerHintMatches(full.text, hint)) return false;
        }
        return full == null || headerHintMatches(crop.text, hint);
    }

    private static boolean corroboratedTitleStem(Line full, Line crop, String hint) {
        if (!clean(crop.text).equalsIgnoreCase(clean(hint))) return false;
        String before = clean(full.text).toLowerCase(Locale.ROOT),
                after = clean(crop.text).toLowerCase(Locale.ROOT);
        if (before.length() != after.length()) return false;
        int changed = 0;
        for (int i = 0; i < before.length(); i++)
            if (before.charAt(i) != after.charAt(i)) {
                char a = before.charAt(i), b = after.charAt(i);
                if (!((a == 'i' && b == 'l') || (a == 'l' && b == 'i')) || ++changed > 1)
                    return false;
            }
        float height = Math.max(full.height(), crop.height());
        return changed == 1
                && height > 0
                && Math.min(full.height(), crop.height()) >= height * .75f
                && Math.abs((full.top + full.bottom - crop.top - crop.bottom) * .5f) <= height * .3f
                && Math.abs(full.center() - crop.center()) <= height * .5f
                && Math.min(full.right, crop.right) - Math.max(full.left, crop.left)
                        >= Math.min(full.right - full.left, crop.right - crop.left) * .85f;
    }

    private static boolean instrumentOrPlayLabel(String text) {
        return clean(text).equalsIgnoreCase("play")
                || clean(text)
                        .matches(
                                "(?iu)^(?:(?:soprano|alto|tenor|bass|treble|descant)\\h+)?"
                                        + "(?:violin|viola|cello|piano|harp|guitar|flute|clarinet|oboe|bassoon|trumpet|trombone|horn|tuba|saxophone|recorder|percussion|drums|voices?|vocals?)"
                                        + "(?:\\h+(?:solo|duet|part))?$");
    }

    private static boolean headerHintMatches(String text, String hint) {
        String a = fold(text), b = fold(hint);
        return a.length() >= 4
                && b.length() >= 4
                && (a.equals(b)
                        || b.startsWith(a)
                        || a.startsWith(b)
                        || editDistance(a, b) <= Math.max(a.length(), b.length()) * .12f);
    }

    /** Text-layer strings are source evidence only when they contain an explicit role. */
    public static boolean isPrintedCredit(String text) {
        return credit(text) != null;
    }

    /** Replace the OCR context above the staff while retaining cover/footer evidence. */
    public static List<Line> mergeHeaderCrop(
            List<Line> full, List<Line> cropped, float cropBottom) {
        if (cropped.isEmpty()) return List.copyOf(full);
        List<Line> merged = new ArrayList<>();
        for (Line line : full) if (line.top >= cropBottom) merged.add(line);
        merged.addAll(cropped);
        return List.copyOf(merged);
    }

    /** Explicit footer roles can be read without treating lyrics or bare bylines as credits. */
    public static boolean isExplicitFooterCredit(String text) {
        String value = clean(text);
        return !LEGAL.matcher(value).find()
                && credit(value) != null
                && !Pattern.compile("(?iu)(?:@|\\(c\\))\\h*(?:1[5-9]|20)[0-9]{2}\\b")
                        .matcher(value)
                        .find()
                && !value.matches("(?iu)^(?:this\\h+)?arrangement\\h+(?:1[5-9]|20)[0-9]{2}\\b.*")
                && !value.matches("(?iu)^by\\h+.*")
                && !value.matches("(?iu)^(?:additional\\h+)?lyrics\\h+by\\b.*");
    }

    public static List<Line> mergeEmbeddedCredits(List<Line> original, List<Line> embedded) {
        List<Line> merged = new ArrayList<>(original);
        List<Line> datedLines = joinDateBoxes(original);
        for (Line source : embedded) {
            Credit sourceCredit = credit(source.text);
            if (sourceCredit == null
                    && !source.text.contains("(cid:")
                    && source.text.codePoints().noneMatch(c -> c >= 0xe000 && c <= 0xf8ff)) {
                Matcher sourceDate = DATED_AUTHOR.matcher(clean(source.text));
                String printedName =
                        sourceDate.matches() ? sourceDate.group(1) : clean(source.text);
                if (printedName.matches("[\\p{L} .’'–-]{3,80}")
                        && printedName.split("\\h+").length >= 2) {
                    for (int index = 0; index < merged.size(); index++) {
                        Line prior = merged.get(index);
                        Matcher priorDate = DATED_AUTHOR.matcher(clean(prior.text));
                        String oldName =
                                priorDate.matches() ? priorDate.group(1) : clean(prior.text);
                        // Restore only diacritics/punctuation of an already dated
                        // byline. Identical letters and nearby source geometry
                        // prevent a different name from acquiring an inferred role.
                        boolean dated =
                                datedLines.stream()
                                        .anyMatch(
                                                l -> {
                                                    Matcher m = DATED_AUTHOR.matcher(clean(l.text));
                                                    return m.matches()
                                                            && m.group(1).equals(oldName)
                                                            && l.top == prior.top
                                                            && l.left == prior.left;
                                                });
                        if (!dated
                                || !same(oldName, printedName)
                                || oldName.equals(printedName)
                                || source.height() < prior.height() * .65f
                                || source.height() > prior.height() * 1.5f
                                || Math.min(prior.right, source.right)
                                                - Math.max(prior.left, source.left)
                                        < Math.min(
                                                        prior.right - prior.left,
                                                        source.right - source.left)
                                                * .6f
                                || Math.abs(
                                                (prior.top
                                                                + prior.bottom
                                                                - source.top
                                                                - source.bottom)
                                                        * .5f)
                                        > Math.max(prior.height(), source.height()) * .35f)
                            continue;
                        String suffix =
                                priorDate.matches()
                                        ? clean(prior.text).substring(oldName.length())
                                        : "";
                        merged.set(
                                index,
                                new Line(
                                        printedName + suffix,
                                        prior.left,
                                        prior.top,
                                        prior.right,
                                        prior.bottom));
                    }
                }
            }
            if (sourceCredit == null
                    || sourceCredit.value.isBlank()
                    || source.text.contains("(cid:")
                    || source.text.codePoints().anyMatch(c -> c >= 0xe000 && c <= 0xf8ff)) continue;
            List<Line> overlaps =
                    original.stream()
                            .filter(
                                    l ->
                                            credit(l.text) != null
                                                    && Math.min(l.right, source.right)
                                                                    - Math.max(l.left, source.left)
                                                            > Math.min(
                                                                            l.right - l.left,
                                                                            source.right
                                                                                    - source.left)
                                                                    * .35f
                                                    && Math.min(l.bottom, source.bottom)
                                                                    - Math.max(l.top, source.top)
                                                            > Math.min(l.height(), source.height())
                                                                    * .35f
                                                    && Math.abs(
                                                                    (l.top
                                                                                    + l.bottom
                                                                                    - source.top
                                                                                    - source.bottom)
                                                                            * .5f)
                                                            < Math.max(l.height(), source.height())
                                                                    * .65f)
                            .toList();
            // Native PDF rows can span independent left/right credit columns.
            // Preserve OCR's separate labels instead of attaching the other role
            // label to a person's name or erasing an empty multiline label.
            boolean separateRoles = false;
            for (int a = 0; a < overlaps.size(); a++)
                for (int b = a + 1; b < overlaps.size(); b++) {
                    Line first = overlaps.get(a), second = overlaps.get(b);
                    float gap = Math.max(first.left - second.right, second.left - first.right);
                    if (credit(first.text).role != credit(second.text).role
                            && gap > Math.max(first.height(), second.height()) * 2f)
                        separateRoles = true;
                }
            if (separateRoles) continue;
            if (overlaps.stream().anyMatch(l -> same(l.text, source.text))) continue;
            merged.removeAll(overlaps);
            if (merged.stream()
                    .noneMatch(
                            l ->
                                    same(l.text, source.text)
                                            && Math.abs(l.top - source.top)
                                                    < source.height() * .3f)) merged.add(source);
        }
        return List.copyOf(merged);
    }

    /** Request word bounds only when native text has joined the located title to another column. */
    public static boolean needsEmbeddedHeadingWords(Page page, List<Line> embedded) {
        float limit = page.notationTop > 0 ? page.notationTop : page.height * .62f;
        Line heading = chooseTitle("", page, page.lines, limit);
        if (heading == null) return false;
        String[] start = clean(heading.text).split("\\h+");
        if (start.length < 2) return false;
        String prefix = fold(start[0] + " " + start[1]);
        if (prefix.length() < 6) return false;
        return embedded.stream()
                .anyMatch(
                        line ->
                                line.top < limit
                                        && fold(line.text).contains(prefix)
                                        && line.right - line.left
                                                > (heading.right - heading.left) * 1.2f
                                        && Math.abs(
                                                        (line.top
                                                                        + line.bottom
                                                                        - heading.top
                                                                        - heading.bottom)
                                                                * .5f)
                                                < heading.height() * .35f
                                        && Math.min(line.right, heading.right)
                                                        - Math.max(line.left, heading.left)
                                                > (heading.right - heading.left) * .7f);
    }

    /** Separate mixed PDF columns using located title ink, baseline, and word size. */
    public static List<Line> mergeEmbeddedHeadingWords(
            Page page, List<Line> original, List<Line> words, List<Line> embedded) {
        if (!needsEmbeddedHeadingWords(page, embedded)) return List.copyOf(original);
        float limit = page.notationTop > 0 ? page.notationTop : page.height * .62f;
        Line heading = chooseTitle("", page, original, limit);
        if (heading == null) return List.copyOf(original);
        List<Line> located =
                words.stream()
                        .filter(
                                word ->
                                        word.top < limit
                                                && word.left
                                                        >= heading.left - heading.height() * .2f
                                                && word.right
                                                        <= heading.right + heading.height() * .2f
                                                && word.height() >= heading.height() * .6f
                                                && word.height() <= heading.height() * 1.1f
                                                && Math.abs(word.top - heading.top)
                                                        < heading.height() * .25f)
                        .distinct()
                        .sorted(Comparator.comparingDouble(Line::left))
                        .toList();
        if (located.size() < 3 || located.size() > 20) return List.copyOf(original);
        String[] prefix = clean(heading.text).split("\\h+");
        if (prefix.length < 2
                || !same(prefix[0], located.get(0).text)
                || !same(prefix[1], located.get(1).text)) return List.copyOf(original);
        if (located.get(located.size() - 1).right - located.get(0).left
                < (heading.right - heading.left) * .85f) return List.copyOf(original);
        StringBuilder title = new StringBuilder();
        Line previous = null;
        for (Line word : located) {
            if (previous != null) {
                if (word.left - previous.right > heading.height() * 1.1f
                        || word.left < previous.right - heading.height() * .15f)
                    return List.copyOf(original);
                title.append(' ');
            }
            title.append(clean(word.text));
            // A standalone punctuation glyph has a short ink box. Require the
            // previous word's baseline and an immediate gap before retaining it.
            for (Line punctuation : words)
                if (clean(punctuation.text).matches("[.,:;]")
                        && punctuation.left >= word.right
                        && punctuation.left - word.right < word.height() * .25f
                        && Math.abs(punctuation.bottom - word.bottom) < word.height() * .12f
                        && (located.indexOf(word) + 1 >= located.size()
                                || punctuation.right
                                        <= located.get(located.indexOf(word) + 1).left)) {
                    title.append(clean(punctuation.text));
                    break;
                }
            previous = word;
        }
        if (!titleText(title.toString())) return List.copyOf(original);
        List<Line> captions =
                embedded.stream()
                        .filter(
                                line ->
                                        (MOVEMENT_SUBTITLE.matcher(clean(line.text)).matches()
                                                        || CATALOGUE_MOVEMENT
                                                                .matcher(clean(line.text))
                                                                .matches())
                                                && line.height() >= heading.height() * .3f
                                                && line.height() < heading.height() * .65f
                                                && line.left >= heading.left
                                                && line.right <= heading.right
                                                && line.top >= heading.top
                                                && line.top
                                                        <= heading.bottom + heading.height() * .3f
                                                && Math.abs(line.center() - heading.center())
                                                        < (heading.right - heading.left) * .2f)
                        .distinct()
                        .sorted(Comparator.comparingDouble(Line::top))
                        .toList();
        for (Line caption : captions) title.append(' ').append(clean(caption.text));
        List<Line> merged = new ArrayList<>(original);
        merged.removeIf(
                line ->
                        line != heading
                                && captions.stream()
                                        .anyMatch(
                                                caption ->
                                                        !fold(line.text).isEmpty()
                                                                && fold(caption.text)
                                                                        .startsWith(fold(line.text))
                                                                && Math.abs(
                                                                                line.center()
                                                                                        - caption
                                                                                                .center())
                                                                        < Math.max(
                                                                                        line
                                                                                                .height(),
                                                                                        caption
                                                                                                .height())
                                                                                * 2
                                                                && Math.min(
                                                                                        line.bottom,
                                                                                        caption.bottom)
                                                                                - Math.max(
                                                                                        line.top,
                                                                                        caption.top)
                                                                        > Math.min(
                                                                                        line
                                                                                                .height(),
                                                                                        caption
                                                                                                .height())
                                                                                * .5f));
        int index = merged.indexOf(heading);
        if (index >= 0)
            merged.set(
                    index,
                    new Line(
                            title.toString(),
                            heading.left,
                            heading.top,
                            heading.right,
                            heading.bottom));
        return List.copyOf(merged);
    }

    /** Repair an already located heading from nearby PDF text, without inventing a new title. */
    public static List<Line> mergeEmbeddedTitleSpellings(
            Page page, List<Line> original, List<Line> embedded) {
        float limit = page.notationTop > 0 ? page.notationTop : page.height * .62f;
        Line heading = chooseTitle("", page, original, limit);
        if (heading == null) return List.copyOf(original);
        List<Line> merged = new ArrayList<>(original);
        // A sharp/flat glyph can split a heading into OCR boxes and disappear.
        // Recover the key only when every remaining word agrees with PDF text
        // on the same baseline, including the independently located heading.
        for (Line source : embedded) {
            Matcher key =
                    Pattern.compile(
                                    "(?iu)^(.+?)\\h+in\\h+[A-G](?:[#♯b♭]|\\h+(?:flat|sharp))?\\h+(major|minor)$")
                            .matcher(clean(source.text));
            if (!key.matches()
                    || source.top >= limit
                    || source.height() < heading.height() * .65f
                    || source.height() > heading.height() * 1.5f
                    || !titleText(source.text)
                    || source.text.contains("(cid:")
                    || source.text.codePoints().anyMatch(c -> c >= 0xe000 && c <= 0xf8ff)) continue;
            List<Line> fragments =
                    original.stream()
                            .filter(
                                    l ->
                                            l.top < limit
                                                    && (titleText(l.text)
                                                            || hyphenTitleFragment(l.text)))
                            .filter(
                                    l ->
                                            !DATED_AUTHOR.matcher(clean(l.text)).matches()
                                                    && l.left
                                                            >= source.left - heading.height() * .15f
                                                    && l.right
                                                            <= source.right
                                                                    + heading.height() * .15f
                                                    && l.height() >= heading.height() * .4f
                                                    && l.height() <= heading.height() * 1.4f
                                                    && Math.abs(
                                                                    (l.top
                                                                                    + l.bottom
                                                                                    - source.top
                                                                                    - source.bottom)
                                                                            * .5f)
                                                            < Math.max(l.height(), source.height())
                                                                    * .35f)
                            .sorted(java.util.Comparator.comparingDouble(Line::left))
                            .toList();
            if (!fragments.contains(heading) || fragments.size() < 2) continue;
            StringBuilder located = new StringBuilder();
            for (Line fragment : fragments) {
                if (located.length() > 0 && !hyphenTitleFragment(fragment.text))
                    located.append(' ');
                located.append(clean(fragment.text));
            }
            String prefix = key.group(1) + " in";
            if (key.group(1).split("[\\h-]+").length < 2
                    || !(same(located.toString(), prefix)
                            || same(located.toString(), prefix + " " + key.group(2)))) continue;
            float left = fragments.get(0).left, right = fragments.get(fragments.size() - 1).right;
            float top = fragments.stream().map(Line::top).min(Float::compare).orElse(heading.top);
            float bottom =
                    fragments.stream().map(Line::bottom).max(Float::compare).orElse(heading.bottom);
            merged.removeAll(fragments);
            merged.add(new Line(source.text, left, top, right, bottom));
            return mergeEmbeddedSourceCaptions(page, merged, embedded);
        }
        for (Line source : embedded) {
            if (source.top >= limit
                    || !titleText(source.text)
                    || DATED_AUTHOR.matcher(clean(source.text)).matches()
                    || source.text.contains("(cid:")
                    || source.text.codePoints().anyMatch(c -> c >= 0xe000 && c <= 0xf8ff)) continue;
            for (int index = 0; index < merged.size(); index++) {
                Line prior = merged.get(index);
                if (prior != heading || !titleText(prior.text)) continue;
                String before = fold(prior.text), after = fold(source.text);
                String prefix = clean(prior.text), full = clean(source.text);
                // PDF extraction can omit a closing quotation glyph even when OCR
                // read it. A spelling repair must retain that printed punctuation.
                if (quotationCount(full) < quotationCount(prefix)) continue;
                // Recover only a printed key suffix following an exact OCR heading.
                // Its text-layer box must contain the existing heading on that baseline;
                // filenames and nearby credit lines cannot supply these missing words.
                boolean keySuffix =
                        prefix.split("\\h+").length >= 3
                                && full.regionMatches(true, 0, prefix, 0, prefix.length())
                                && full.substring(Math.min(prefix.length(), full.length()))
                                        .matches(
                                                "(?iu)^\\h+in\\h+[A-G](?:[#♯b♭]|\\h+(?:flat|sharp))?\\h+(?:major|minor)$")
                                && source.left <= prior.left + prior.height() * .15f
                                && source.right >= prior.right - prior.height() * .15f
                                && source.right - source.left <= (prior.right - prior.left) * 2
                                && source.height() <= prior.height() * 1.5f;
                if (before.length() < 4
                        || after.length() < 4
                        || !keySuffix
                                && (Math.abs(before.length() - after.length()) > 2
                                        || editDistance(before, after) > 2)
                        || source.height() < prior.height() * .65f
                        || source.height() > prior.height() * 2
                        || Math.min(prior.right, source.right) - Math.max(prior.left, source.left)
                                < Math.min(prior.right - prior.left, source.right - source.left)
                                        * .6f
                        || !keySuffix
                                && Math.abs(prior.center() - source.center())
                                        > Math.max(prior.height(), source.height()) * 1.5f
                        || Math.abs((prior.top + prior.bottom - source.top - source.bottom) * .5f)
                                > Math.max(prior.height(), source.height()) * .35f) continue;
                // Keep the OCR geometry so text-layer ascent/descent cannot change
                // title ranking or pull a neighboring name into the heading.
                Line repaired =
                        new Line(
                                clean(source.text),
                                prior.left,
                                prior.top,
                                prior.right,
                                prior.bottom);
                merged.set(index, repaired);
                return mergeEmbeddedSourceCaptions(page, merged, embedded);
            }
        }
        return mergeEmbeddedSourceCaptions(page, merged, embedded);
    }

    /** Recover a clipped caption only when OCR located its prefix on the printed baseline. */
    private static List<Line> mergeEmbeddedSourceCaptions(
            Page page, List<Line> original, List<Line> embedded) {
        float limit = page.notationTop > 0 ? page.notationTop : page.height * .62f;
        Line heading = chooseTitle("", page, original, limit);
        if (heading == null) return List.copyOf(original);
        List<Line> merged = new ArrayList<>(original);
        for (Line source : embedded) {
            String caption = clean(source.text);
            String[] tokens = caption.split("\\h+");
            boolean recognized =
                    caption.matches(
                            "(?iu)^(?:suite\\h+from\\h+the\\h+motion\\h+picture|.+\\h+soundtrack|"
                                    + "(?:(?:live|concert)\\h+performance|solo|easy|original|piano|violin|guitar|tavern|epic|wedding|concert)\\h+version(?:\\h+[0-9]+)?)$");
            if (!recognized
                    || tokens.length < 2
                    || tokens.length > 12
                    || tokens[0].length() < 4
                    || !titleText(caption)
                    || source.top >= limit) continue;
            for (int index = 0; index < merged.size(); index++) {
                Line prior = merged.get(index);
                String[] fragment = clean(prior.text).split("\\h+");
                if (prior == heading
                        || fragment.length < 2
                        || fragment.length > tokens.length
                        || !same(fragment[0], tokens[0])
                        || !titleText(prior.text)
                        || Math.abs(prior.left - source.left)
                                > Math.max(prior.height(), source.height()) * .3f
                        || prior.right > source.right + prior.height() * .2f
                        || source.height() < prior.height() * .65f
                        || source.height() > prior.height() * 1.5f
                        || Math.abs((prior.top + prior.bottom - source.top - source.bottom) * .5f)
                                > Math.max(prior.height(), source.height()) * .35f) continue;
                String prefix = String.join(" ", java.util.Arrays.copyOf(tokens, fragment.length));
                if (editDistance(fold(prior.text), fold(prefix)) > 2) continue;
                // Overlapping title/caption ink can become a single tall OCR box.
                // Separate it only when both the caption prefix and a long title
                // prefix independently agree with contained PDF text geometry.
                for (Line printed : embedded) {
                    if (printed == source
                            || !titleText(printed.text)
                            || DATED_AUTHOR.matcher(clean(printed.text)).matches()
                            || printed.top >= source.top
                            || source.top >= printed.bottom
                            || printed.height() <= source.height() * 1.2f
                            || printed.height() < heading.height() * .65f
                            || printed.height() > heading.height() * .9f
                            || heading.top > printed.top + heading.height() * .2f
                            || heading.bottom < source.bottom - source.height() * .2f
                            || heading.left > printed.left + heading.height() * .2f
                            || heading.right < printed.right - heading.height() * .2f
                            || source.top < printed.bottom - printed.height() * .3f
                            || source.top - printed.bottom >= printed.height() * 1.2f
                            || source.height() < printed.height() * .55f
                            || source.height() > printed.height()
                            || Math.abs(source.center() - printed.center())
                                    >= printed.height() * .8f
                            || source.right - source.left >= (printed.right - printed.left) * 2.5f)
                        continue;
                    String located = fold(heading.text), full = fold(printed.text);
                    int shared = 0;
                    while (shared < Math.min(located.length(), full.length())
                            && located.charAt(shared) == full.charAt(shared)) shared++;
                    if (shared < Math.max(6, full.length() * .6f)) continue;
                    merged.set(merged.indexOf(heading), printed);
                    heading = printed;
                    break;
                }
                if (source.top < heading.bottom - heading.height() * .3f
                        || source.top - heading.bottom >= heading.height() * 1.2f
                        || source.height() < heading.height() * .55f
                        || source.height() > heading.height()
                        || Math.abs(source.center() - heading.center()) >= heading.height() * .8f
                        || source.right - source.left >= (heading.right - heading.left) * 2.5f)
                    continue;
                merged.set(index, source);
                break;
            }
        }
        return List.copyOf(merged);
    }

    /** Complete a catalogue caption only where OCR independently located its movement number. */
    public static List<Line> mergeEmbeddedMovementCaptions(
            Page page, List<Line> original, List<Line> embedded) {
        float limit = page.notationTop > 0 ? page.notationTop : page.height * .62f;
        Line heading = chooseTitle("", page, original, limit);
        if (heading == null) return List.copyOf(original);
        original = mergePrintedMovementOrdinals(heading, limit, original, embedded);
        for (Line source : embedded) {
            Matcher caption = CATALOGUE_MOVEMENT.matcher(clean(source.text));
            if (!caption.matches()
                    || source.top >= limit
                    || source.top < heading.bottom
                    || source.top - heading.bottom >= heading.height() * 1.2f
                    || source.height() < heading.height() * .25f
                    || source.height() > heading.height() * .8f
                    || Math.abs(source.center() - heading.center()) >= heading.height() * 1.6f
                    || source.right - source.left > heading.right - heading.left) continue;
            for (int index = 0; index < original.size(); index++) {
                Line prior = original.get(index);
                Matcher fragment = MOVEMENT_FRAGMENT.matcher(clean(prior.text));
                if (!fragment.matches()
                        || !fragment.group(1).equals(caption.group(1))
                        || source.left > prior.left + prior.height() * .15f
                        || source.right < prior.right - prior.height() * .15f
                        || source.right - source.left > (prior.right - prior.left) * 3.5f
                        || source.height() < prior.height() * .65f
                        || source.height() > prior.height() * 1.7f
                        || Math.abs((source.top + source.bottom - prior.top - prior.bottom) * .5f)
                                > Math.max(source.height(), prior.height()) * .35f) continue;
                List<Line> merged = new ArrayList<>(original);
                merged.set(index, source);
                return List.copyOf(merged);
            }
        }
        return List.copyOf(original);
    }

    private static List<Line> mergePrintedMovementOrdinals(
            Line heading, float limit, List<Line> original, List<Line> embedded) {
        List<Line> merged = new ArrayList<>(original);
        Pattern bareMovement = Pattern.compile("(?iu)^([1-9])\\h+movement$");
        for (int index = 0; index < original.size(); index++) {
            Line prior = original.get(index);
            Matcher bare = bareMovement.matcher(clean(prior.text));
            if (!bare.matches()
                    || prior.top >= limit
                    || prior.top < heading.bottom - heading.height() * .2f
                    || prior.top - heading.bottom >= heading.height() * 1.2f
                    || prior.height() < heading.height() * .25f
                    || prior.height() > heading.height() * .8f
                    || Math.abs(prior.center() - heading.center()) >= heading.height() * 1.6f)
                continue;
            String number = bare.group(1);
            String suffix =
                    switch (number) {
                        case "1" -> "st";
                        case "2" -> "nd";
                        case "3" -> "rd";
                        default -> "th";
                    };
            for (Line source : embedded) {
                String text = clean(source.text);
                boolean full = text.matches("(?iu)" + number + suffix + "\\h+movement");
                if (!full && !text.equalsIgnoreCase(clean(prior.text))) continue;
                if (source.height() < prior.height() * .65f
                        || source.height() > prior.height() * 1.7f
                        || Math.min(source.right, prior.right) - Math.max(source.left, prior.left)
                                < (prior.right - prior.left) * .8f
                        || Math.abs((source.top + source.bottom - prior.top - prior.bottom) * .5f)
                                > Math.max(source.height(), prior.height()) * .65f) continue;
                // PDF text can expose a superscript suffix separately from its
                // baseline. Require the suffix printed inside the same source
                // caption; the movement number alone never supplies it.
                boolean superscript =
                        embedded.stream()
                                .anyMatch(
                                        l ->
                                                clean(l.text).equalsIgnoreCase(suffix)
                                                        && l.height() >= prior.height() * .2f
                                                        && l.height() <= source.height() * .8f
                                                        && l.left >= source.left
                                                        && l.right
                                                                < source.left
                                                                        + (source.right
                                                                                        - source.left)
                                                                                * .4f
                                                        && l.top
                                                                >= source.top
                                                                        - source.height() * .4f
                                                        && l.bottom
                                                                <= source.top
                                                                        + source.height() * .8f);
                if (!full && !superscript) continue;
                merged.set(
                        index,
                        new Line(
                                number + suffix + " Movement",
                                prior.left,
                                prior.top,
                                prior.right,
                                prior.bottom));
                break;
            }
        }
        return List.copyOf(merged);
    }

    /** Small printed credits need their own larger OCR image, not spelling guesses. */
    public static List<Refinement> refinementRegions(Page page) {
        List<Refinement> regions = new ArrayList<>();
        if (page.width <= 0 || page.height <= 0) return List.of();
        Line heading =
                chooseTitle(
                        "",
                        page,
                        page.lines,
                        page.notationTop > 0 ? page.notationTop : page.height * .62f);
        for (Line line : page.lines) {
            Credit role = credit(line.text);
            if ((role == null && suspectedArrangementName(line.text).isEmpty())
                    || LEGAL.matcher(line.text).find()
                    || !inCreditHeader(page, line)
                    || line.height() >= page.width * .025f) continue;
            Line name =
                    role != null && role.value.isBlank()
                            ? nearestName(line, page.lines, heading, role.suffix)
                            : null;
            float padding = Math.max(12, line.height() * 1.6f);
            float left =
                    Math.max(
                            0, Math.min(line.left, name == null ? line.left : name.left) - padding);
            float top =
                    Math.max(0, Math.min(line.top, name == null ? line.top : name.top) - padding);
            float right =
                    Math.min(
                            page.width,
                            Math.max(line.right, name == null ? line.right : name.right) + padding);
            float bottom =
                    Math.min(
                            page.height,
                            Math.max(line.bottom, name == null ? line.bottom : name.bottom)
                                    + padding);
            if (role != null && role.suffix && name != null) {
                // A boxed name above "Arrangement" needs a tighter, asymmetric crop.
                // Wide frame context can make OCR clip its final descender.
                left = Math.max(0, name.left - Math.max(page.width / 60, name.height() * 1.4f));
                top = Math.max(0, name.top - Math.max(page.width / 72, name.height() * 1.15f));
                right =
                        Math.min(
                                page.width,
                                name.right + Math.max(page.width / 40, name.height() * 2));
                bottom =
                        Math.min(
                                page.height,
                                name.bottom + Math.max(page.width / 22.5f, name.height() * 3.5f));
            }
            float scale =
                    Math.min(
                            4,
                            Math.min(
                                    Math.max(3, 80 / line.height()),
                                    3000 / Math.max(right - left, bottom - top)));
            if (scale > 1.15f) regions.add(new Refinement(left, top, right, bottom, scale));
        }
        return List.copyOf(regions);
    }

    /** Recheck accented explicit credits with tightly bounded source ink. */
    public static List<Line> accentedCreditLines(Page page) {
        return page.lines.stream()
                .filter(
                        line -> {
                            Credit role = credit(line.text);
                            return role != null
                                    && !role.value.isBlank()
                                    && inCreditHeader(page, line)
                                    && line.height() < page.width * .025f
                                    && !LEGAL.matcher(line.text).find()
                                    && !Normalizer.normalize(line.text, Normalizer.Form.NFD)
                                            .equals(
                                                    Normalizer.normalize(
                                                                    line.text, Normalizer.Form.NFD)
                                                            .replaceAll("\\p{M}+", ""));
                        })
                .limit(2)
                .toList();
    }

    public static Refinement tightCreditRegion(Page page, Line line) {
        float padding = Math.max(3, line.height() * .2f);
        return new Refinement(
                Math.max(0, line.left - padding),
                Math.max(0, line.top - padding),
                Math.min(page.width, line.right + padding),
                Math.min(page.height, line.bottom + padding),
                3);
    }

    /** Two agreeing source reads may change accents, never base letters or punctuation. */
    public static List<Line> mergeCreditAccentConsensus(
            List<Line> original, Line prior, List<Line> first, List<Line> second) {
        Credit role = credit(prior.text);
        if (role == null || role.value.isBlank()) return List.copyOf(original);
        String a = accentCreditReading(prior, first), b = accentCreditReading(prior, second);
        if (a.isEmpty() || !a.equalsIgnoreCase(b) || a.equals(prior.text))
            return List.copyOf(original);
        List<Line> result = new ArrayList<>(original);
        int index = result.indexOf(prior);
        if (index >= 0)
            result.set(index, new Line(a, prior.left, prior.top, prior.right, prior.bottom));
        return List.copyOf(result);
    }

    private static String accentCreditReading(Line prior, List<Line> readings) {
        Credit role = credit(prior.text);
        return readings.stream()
                .filter(
                        line -> {
                            Credit next = credit(line.text);
                            return next != null
                                    && next.role == role.role
                                    && accentLetters(line.text).equals(accentLetters(prior.text))
                                    && Math.abs(line.center() - prior.center())
                                            < Math.max(
                                                    prior.height(),
                                                    (prior.right - prior.left) * .1f)
                                    && Math.abs(
                                                    (line.top
                                                                    + line.bottom
                                                                    - prior.top
                                                                    - prior.bottom)
                                                            * .5f)
                                            < prior.height() * .7f
                                    && line.height() > prior.height() * .5f
                                    && line.height() < prior.height() * 1.5f
                                    && Math.min(line.right, prior.right)
                                                    - Math.max(line.left, prior.left)
                                            > (prior.right - prior.left) * .8f;
                        })
                .map(line -> clean(line.text))
                .distinct()
                .reduce((a, b) -> "")
                .orElse("");
    }

    private static String accentLetters(String text) {
        return Normalizer.normalize(clean(text), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    /** A larger image is not automatically a better reading: preserve lost source letters. */
    public static List<Line> mergeRefinement(
            List<Line> original, List<Line> refined, Refinement region) {
        List<Line> merged = new ArrayList<>(original);
        for (Line next : refined) {
            Line prior =
                    original.stream()
                            .filter(
                                    line ->
                                            line.right > region.left
                                                    && line.left < region.right
                                                    && line.bottom > region.top
                                                    && line.top < region.bottom
                                                    && Math.min(line.right, next.right)
                                                                    - Math.max(line.left, next.left)
                                                            > Math.min(
                                                                            line.right - line.left,
                                                                            next.right - next.left)
                                                                    * .35f
                                                    && Math.min(line.bottom, next.bottom)
                                                                    - Math.max(line.top, next.top)
                                                            > Math.min(line.height(), next.height())
                                                                    * .35f
                                                    && Math.abs(
                                                                    (line.top
                                                                                    + line.bottom
                                                                                    - next.top
                                                                                    - next.bottom)
                                                                            / 2)
                                                            < Math.max(line.height(), next.height())
                                                                    * .65f)
                            .min(
                                    Comparator.comparingDouble(
                                            line -> Math.abs(line.center() - next.center())))
                            .orElse(null);
            // Enlargement recovers clipped letters, but equal-length substitutions are
            // not stronger evidence than the original (italic r/t and arr. are common).
            Credit recoveredRole = credit(next.text);
            boolean recoveredArrangement =
                    prior != null
                            && recoveredRole != null
                            && recoveredRole.role == Role.ARRANGER
                            && !suspectedArrangementName(prior.text).isEmpty()
                            && same(suspectedArrangementName(prior.text), recoveredRole.value);
            if (prior != null
                    && refinementLetterCount(next.text) <= refinementLetterCount(prior.text)
                    && !recoveredArrangement) continue;
            if (prior != null
                    && !recoveredArrangement
                    && !retainsSourceLetters(prior.text, next.text)) continue;
            if (prior != null) merged.remove(prior);
            merged.add(next);
        }
        return List.copyOf(merged);
    }

    private static long letterCount(String text) {
        return text.codePoints().filter(Character::isLetterOrDigit).count();
    }

    // Modifier letters include apostrophe-like marks. A changed punctuation
    // reading is not evidence that enlargement recovered a clipped base letter.
    private static long refinementLetterCount(String text) {
        return text.codePoints()
                .filter(
                        c ->
                                Character.isLetterOrDigit(c)
                                        && Character.getType(c) != Character.MODIFIER_LETTER)
                .count();
    }

    private static long quotationCount(String text) {
        return text.codePoints()
                .filter(c -> c == '"' || c == '“' || c == '”' || c == '«' || c == '»')
                .count();
    }

    private static boolean retainsSourceLetters(String original, String enlarged) {
        int[] before = fold(original).codePoints().toArray(),
                after = fold(enlarged).codePoints().toArray();
        int index = 0;
        for (int value : after) if (index < before.length && before[index] == value) index++;
        return index == before.length;
    }

    /** Tiny cover lettering needs two independent enlarged readings for separator repairs. */
    public static List<Line> coverSeparatorLines(String hint, Page page) {
        if (page.notationTop > 0 || page.width <= 0 || page.height <= 0) return List.of();
        Line heading = chooseTitle(hint, page, page.lines, page.height * .62f);
        return page.lines.stream()
                .filter(
                        line ->
                                line.height() > 0
                                        && line.height() < page.width * .025f
                                        && clean(line.text).length() >= 4
                                        && clean(line.text).length() <= 180
                                        && (line == heading
                                                || FEATURED.matcher(clean(line.text)).matches()))
                .limit(2)
                .toList();
    }

    public static Refinement coverSeparatorRegion(Page page, Line line) {
        float padding = Math.max(12, line.height() * .6f);
        return new Refinement(
                Math.max(0, line.left - padding),
                Math.max(0, line.top - padding),
                Math.min(page.width, line.right + padding),
                Math.min(page.height, line.bottom + padding),
                3);
    }

    /** Keep letters and other punctuation; never replace text with a caller hint. */
    public static List<Line> mergeCoverSeparatorConsensus(
            List<Line> original, Line prior, List<Line> first, List<Line> second) {
        String a = coverSeparatorReading(prior, first), b = coverSeparatorReading(prior, second);
        if (a.isEmpty() || !a.equalsIgnoreCase(b) || a.equals(prior.text))
            return List.copyOf(original);
        // Equal zoom readings can still split FEAT into two words. Preserve the
        // independently located attribution label rather than losing both artists.
        if (FEATURED.matcher(clean(prior.text)).matches() && !FEATURED.matcher(a).matches())
            return List.copyOf(original);
        List<Line> result = new ArrayList<>(original);
        int index = result.indexOf(prior);
        if (index >= 0)
            result.set(index, new Line(a, prior.left, prior.top, prior.right, prior.bottom));
        return List.copyOf(result);
    }

    private static String coverSeparatorReading(Line prior, List<Line> readings) {
        String key = separatorLetters(prior.text);
        return readings.stream()
                .filter(
                        line ->
                                separatorLetters(line.text).equals(key)
                                        && Math.abs(line.center() - prior.center())
                                                < Math.max(
                                                        prior.height(),
                                                        (prior.right - prior.left) * .1f)
                                        && Math.abs(
                                                        (line.top
                                                                        + line.bottom
                                                                        - prior.top
                                                                        - prior.bottom)
                                                                * .5f)
                                                < prior.height()
                                        && Math.min(line.right, prior.right)
                                                        - Math.max(line.left, prior.left)
                                                > (prior.right - prior.left) * .8f)
                .map(line -> clean(line.text))
                .distinct()
                .reduce((a, b) -> "")
                .orElse("");
    }

    public static List<Line> coverApostropheLines(Page page) {
        if (page.notationTop > 0 || page.width <= 0 || page.height <= 0) return List.of();
        return page.lines.stream()
                .filter(
                        line ->
                                line.height() < page.width * .025f
                                        && FEATURED.matcher(clean(line.text)).matches()
                                        && !interiorApostrophes(line.text).isEmpty())
                .limit(2)
                .toList();
    }

    /** Repair only an apostrophe absent in both reads; retain located spaces and labels. */
    public static List<Line> mergeCoverApostropheConsensus(
            List<Line> original, Line prior, List<Line> first, List<Line> second) {
        if (!FEATURED.matcher(clean(prior.text)).matches()) return List.copyOf(original);
        String a = apostropheReading(prior, first), b = apostropheReading(prior, second);
        if (a.isEmpty() || b.isEmpty()) return List.copyOf(original);
        Set<Integer> kept = new LinkedHashSet<>(interiorApostrophes(a));
        kept.addAll(interiorApostrophes(b));
        StringBuilder repaired = new StringBuilder();
        int letters = 0;
        int[] points = prior.text.codePoints().toArray();
        for (int i = 0; i < points.length; i++) {
            int c = points[i];
            boolean interior =
                    (c == '\'' || c == '’')
                            && i > 0
                            && i + 1 < points.length
                            && Character.isLetter(points[i - 1])
                            && Character.isLetter(points[i + 1]);
            if (!interior || kept.contains(letters)) repaired.appendCodePoint(c);
            if (Character.isLetterOrDigit(c)) letters++;
        }
        List<Line> result = new ArrayList<>(original);
        int index = result.indexOf(prior);
        if (index >= 0)
            result.set(
                    index,
                    new Line(
                            repaired.toString(), prior.left, prior.top, prior.right, prior.bottom));
        return List.copyOf(result);
    }

    private static Set<Integer> interiorApostrophes(String text) {
        Set<Integer> positions = new LinkedHashSet<>();
        int letters = 0;
        int[] points = text.codePoints().toArray();
        for (int i = 0; i < points.length; i++) {
            int c = points[i];
            if ((c == '\'' || c == '’')
                    && i > 0
                    && i + 1 < points.length
                    && Character.isLetter(points[i - 1])
                    && Character.isLetter(points[i + 1])) positions.add(letters);
            if (Character.isLetterOrDigit(c)) letters++;
        }
        return positions;
    }

    private static String apostropheReading(Line prior, List<Line> readings) {
        String key = clean(prior.text).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        return readings.stream()
                .filter(
                        line ->
                                clean(line.text)
                                                .toLowerCase(Locale.ROOT)
                                                .replaceAll("[^\\p{L}\\p{N}]", "")
                                                .equals(key)
                                        && Math.abs(line.center() - prior.center())
                                                < Math.max(
                                                        prior.height(),
                                                        (prior.right - prior.left) * .1f)
                                        && Math.abs(
                                                        (line.top
                                                                        + line.bottom
                                                                        - prior.top
                                                                        - prior.bottom)
                                                                * .5f)
                                                < prior.height() * .7f
                                        && line.height() > prior.height() * .5f
                                        && line.height() < prior.height() * 1.5f
                                        && Math.min(line.right, prior.right)
                                                        - Math.max(line.left, prior.left)
                                                > (prior.right - prior.left) * .8f)
                .map(line -> clean(line.text))
                .distinct()
                .reduce((a, b) -> "")
                .orElse("");
    }

    private static String separatorLetters(String text) {
        return clean(text)
                .toLowerCase(Locale.ROOT)
                .replaceAll("(?<=\\p{L})['’](?=\\p{L})", "")
                .replaceAll("\\h+", "");
    }

    /** Recover omitted all-I ordinal bars from the original heading pixels. */
    public static List<WordRefinement> romanTitleWordRegions(
            String hint, Page page, List<Line> words) {
        Line heading =
                chooseTitle(
                        hint,
                        page,
                        page.lines,
                        page.notationTop > 0 ? page.notationTop : page.height * .62f);
        if (heading == null) return List.of();
        var headingWords =
                words.stream()
                        .filter(
                                w ->
                                        w.left >= heading.left - 5
                                                && w.right <= heading.right + 5
                                                && w.top >= heading.top - 5
                                                && w.bottom <= heading.bottom + 5)
                        .sorted(Comparator.comparingDouble(Line::center))
                        .toList();
        String[] hints = clean(hint).split("\\h+");
        var regions = new ArrayList<WordRefinement>();
        for (int i = 1; i + 1 < headingWords.size() && regions.size() < 2; i++) {
            Line word = headingWords.get(i);
            if (!word.text.matches("[Il1]{1,4}")
                    || word.height() < 12
                    || letterCount(headingWords.get(i - 1).text) < 3
                    || letterCount(headingWords.get(i + 1).text) < 3) continue;
            String expected = "";
            if (hints.length == headingWords.size()) {
                if (!hints[i].matches("I{2,5}") || word.text.length() >= hints[i].length())
                    continue;
                expected = hints[i];
            }
            float padding = word.height() * .55f;
            regions.add(
                    new WordRefinement(
                            word,
                            expected,
                            new Refinement(
                                    Math.max(0, word.left - padding),
                                    word.top,
                                    Math.min(page.width, word.right + padding),
                                    word.bottom,
                                    1)));
        }
        return List.copyOf(regions);
    }

    public static List<Line> applyRomanTitleWord(
            List<Line> lines, WordRefinement word, int originalPixelBars) {
        if (originalPixelBars < (word.hint.isEmpty() ? 3 : 2)
                || originalPixelBars > 5
                || !word.hint.isEmpty() && !word.hint.equals("I".repeat(originalPixelBars))
                || !word.word.text.matches("[Il1]{1,4}")
                || word.word.text.length() >= originalPixelBars) return List.copyOf(lines);
        Pattern token =
                Pattern.compile("(?<!\\p{L})" + Pattern.quote(word.word.text) + "(?!\\p{L})");
        var result = new ArrayList<Line>();
        for (Line line : lines) {
            boolean contains =
                    word.word.left >= line.left - 5
                            && word.word.right <= line.right + 5
                            && word.word.top >= line.top - 5
                            && word.word.bottom <= line.bottom + 5;
            result.add(
                    new Line(
                            contains
                                    ? token.matcher(line.text)
                                            .replaceFirst("I".repeat(originalPixelBars))
                                    : line.text,
                            line.left,
                            line.top,
                            line.right,
                            line.bottom));
        }
        return List.copyOf(result);
    }

    public static List<WordRefinement> tripleCreditStemRegions(Page page, List<Line> words) {
        if (page.notationTop <= 0) return List.of();
        var result = new ArrayList<WordRefinement>();
        for (Line word : words) {
            if (!clean(word.text).matches("[\\p{L}'’\\-]{3,}ll")
                    || word.height() <= 0
                    || word.height() >= page.width * .03f) continue;
            for (Line line : page.lines)
                if (credit(line.text) != null
                        && !credit(line.text).value.isBlank()
                        && inCreditHeader(page, line)
                        && clean(line.text).endsWith(clean(word.text))
                        && word.left >= line.left - 3
                        && word.right <= line.right + 3
                        && word.top >= line.top - 3
                        && word.bottom <= line.bottom + 3) {
                    float pad = Math.max(3, line.height() * .2f);
                    float l = Math.max(0, line.left - pad),
                            t = Math.max(0, line.top - pad),
                            r = Math.min(page.width, line.right + pad),
                            b = Math.min(page.height, line.bottom + pad);
                    if (Math.max(r - l, b - t) * 6 <= 3000)
                        result.add(new WordRefinement(word, "", new Refinement(l, t, r, b, 5)));
                    break;
                }
        }
        return result.stream().limit(2).toList();
    }

    public static List<Line> mergeTripleCreditStem(
            Page page, Line word, List<Line> first, List<Line> second, boolean threePixelStems) {
        if (!threePixelStems || tripleCreditStemRegions(page, List.of(word)).isEmpty())
            return List.copyOf(page.lines);
        var result = new ArrayList<Line>();
        for (Line line : page.lines) {
            boolean contains =
                    credit(line.text) != null
                            && clean(line.text).endsWith(clean(word.text))
                            && word.left >= line.left - 3
                            && word.right <= line.right + 3
                            && word.top >= line.top - 3
                            && word.bottom <= line.bottom + 3;
            String prefix = clean(line.text);
            // The two source crops must read the unchanged byline plus exactly one i/I.
            boolean a = contains && terminalCreditIReading(line, prefix, first),
                    b = contains && terminalCreditIReading(line, prefix, second);
            result.add(
                    new Line(
                            a && b ? prefix + "i" : line.text,
                            line.left,
                            line.top,
                            line.right,
                            line.bottom));
        }
        return List.copyOf(result);
    }

    private static boolean terminalCreditIReading(Line prior, String prefix, List<Line> readings) {
        return readings.size() == 1
                && clean(readings.get(0).text).equalsIgnoreCase(prefix + "i")
                && Math.abs(readings.get(0).center() - prior.center()) <= prior.height()
                && Math.abs(
                                (readings.get(0).top
                                                + readings.get(0).bottom
                                                - prior.top
                                                - prior.bottom)
                                        / 2)
                        <= prior.height() * .5f;
    }

    /** Only a tightly aligned multi-name continuation justifies inspecting punctuation. */
    public static List<Line> creditCommaWords(Page page, List<Line> words) {
        if (page.notationTop <= 0) return List.of();
        var result = new ArrayList<Line>();
        for (Line line : page.lines) {
            Credit label = credit(line.text);
            if (label == null
                    || label.role != Role.COMPOSER
                            && label.role != Role.ARRANGER
                            && label.role != Role.LYRICS
                    || !clean(line.text).endsWith(".")
                    || creditNames(label.value).isEmpty()
                    || !inCreditHeader(page, line)) continue;
            boolean continuation =
                    page.lines.stream()
                            .anyMatch(
                                    next -> {
                                        float h = Math.max(line.height(), next.height()),
                                                gap = next.top - line.bottom;
                                        return next != line
                                                && inCreditHeader(page, next)
                                                && credit(next.text) == null
                                                && nameText(next.text)
                                                && !NOT_TITLE.matcher(next.text).find()
                                                && !LEGAL.matcher(next.text).find()
                                                && creditNames(clean(next.text)).size() >= 2
                                                && h >= 8
                                                && gap >= 0
                                                && gap <= h * .45f
                                                && Math.min(line.height(), next.height())
                                                        >= h * .85f
                                                && Math.abs(line.center() - next.center())
                                                        <= h * .5f;
                                    });
            if (!continuation) continue;
            for (Line word : words)
                if (clean(word.text).matches("[\\p{L}'’\\-]{3,}\\.")
                        && clean(line.text).endsWith(clean(word.text))
                        && word.left >= line.left - 3
                        && Math.abs(word.right - line.right) <= 3
                        && word.top >= line.top - 3
                        && word.bottom <= line.bottom + 3) result.add(word);
        }
        return result.stream().distinct().limit(3).toList();
    }

    public static List<Line> mergeCreditComma(Page page, Line word, boolean originalPixelComma) {
        if (!originalPixelComma || !creditCommaWords(page, List.of(word)).contains(word))
            return List.copyOf(page.lines);
        var result = new ArrayList<Line>();
        for (Line line : page.lines) {
            boolean contains =
                    word.left >= line.left - 3
                            && Math.abs(word.right - line.right) <= 3
                            && word.top >= line.top - 3
                            && word.bottom <= line.bottom + 3
                            && clean(line.text).endsWith(clean(word.text));
            String text = contains ? line.text.replaceFirst("\\.\\h*$", ",") : line.text;
            result.add(new Line(text, line.left, line.top, line.right, line.bottom));
        }
        return List.copyOf(result);
    }

    /** A pixel-supported second terminal l is re-read only inside an actual credit/byline. */
    public static List<Line> creditStemWords(Page page, List<Line> words) {
        if (page.notationTop <= 0) return List.of();
        Result detected = detect("", List.of(page), Set.of());
        return words.stream()
                .filter(
                        word ->
                                clean(word.text).matches("[\\p{L}'’\\-]{3,}l[,.;:]?")
                                        && !clean(word.text).matches(".*ll[,.;:]?")
                                        && word.top < page.notationTop
                                        && word.height() > 0
                                        && word.height() < page.width * .03f
                                        && page.lines.stream()
                                                .anyMatch(
                                                        line ->
                                                                word.left >= line.left - 3
                                                                        && word.right
                                                                                <= line.right + 3
                                                                        && word.top >= line.top - 3
                                                                        && word.bottom
                                                                                <= line.bottom + 3
                                                                        && (credit(line.text)
                                                                                        != null
                                                                                || detected
                                                                                        .unclassifiedCredits
                                                                                        .contains(
                                                                                                clean(
                                                                                                        line.text)))))
                .limit(3)
                .toList();
    }

    public static List<Line> mergeCreditStemWord(List<Line> lines, Line word, String reading) {
        String source = clean(word.text);
        if (!source.matches("[\\p{L}'’\\-]{3,}l[,.;:]?")
                || source.matches(".*ll[,.;:]?")
                || !source.replaceFirst("l([,.;:]?)$", "ll$1").equals(clean(reading)))
            return List.copyOf(lines);
        var result = new ArrayList<Line>();
        Pattern token = Pattern.compile("(?<!\\p{L})" + Pattern.quote(source) + "(?!\\p{L})");
        for (Line line : lines) {
            boolean contains =
                    word.left >= line.left - 3
                            && word.right <= line.right + 3
                            && word.top >= line.top - 3
                            && word.bottom <= line.bottom + 3;
            result.add(
                    new Line(
                            contains
                                    ? token.matcher(line.text)
                                            .replaceFirst(Matcher.quoteReplacement(clean(reading)))
                                    : line.text,
                            line.left,
                            line.top,
                            line.right,
                            line.bottom));
        }
        return List.copyOf(result);
    }

    public static List<WordRefinement> titleWordRegions(String hint, Page page, List<Line> words) {
        Line heading =
                chooseTitle(
                        hint,
                        page,
                        page.lines,
                        page.notationTop > 0 ? page.notationTop : page.height * .62f);
        if (heading == null) return List.of();
        List<Line> headingWords =
                words.stream()
                        .filter(
                                word ->
                                        word.left >= heading.left - 5
                                                && word.right <= heading.right + 5
                                                && word.top >= heading.top - 5
                                                && word.bottom <= heading.bottom + 5)
                        .sorted(Comparator.comparingDouble(Line::center))
                        .toList();
        String[] hints = clean(hint).split("\\h+");
        if (hints.length != headingWords.size()) return List.of();
        List<WordRefinement> regions = new ArrayList<>();
        for (int i = 0; i < hints.length && regions.size() < 3; i++) {
            Line word = headingWords.get(i);
            String source = fold(word.text), expected = fold(hints[i]);
            if (source.equals(expected)
                    || source.length() != expected.length()
                    || editDistance(source, expected) > 2
                    || !word.text.matches("[\\p{L}01]+")
                    || !hints[i].matches("\\p{L}+")) continue;
            float padding = Math.max(3, word.height() * .24f);
            Line context = source.length() == 1 ? heading : word;
            float left = Math.max(0, context.left - padding),
                    top = Math.max(0, context.top - padding),
                    right = Math.min(page.width, context.right + padding),
                    bottom = Math.min(page.height, context.bottom + padding);
            regions.add(
                    new WordRefinement(
                            word,
                            hints[i],
                            new Refinement(
                                    left,
                                    top,
                                    right,
                                    bottom,
                                    Math.min(3, 3000 / Math.max(right - left, bottom - top)))));
        }
        return List.copyOf(regions);
    }

    public static List<Line> titleGlyphWords(String hint, Page page, List<Line> words) {
        Line heading =
                chooseTitle(
                        hint,
                        page,
                        page.lines,
                        page.notationTop > 0 ? page.notationTop : page.height * .62f);
        if (heading == null) return List.of();
        return words.stream()
                .filter(
                        word ->
                                word.text.equals("1")
                                        && word.left >= heading.left - 5
                                        && word.right <= heading.right + 5
                                        && word.top >= heading.top - 5
                                        && word.bottom <= heading.bottom + 5)
                .limit(3)
                .toList();
    }

    /** OCR needs neighboring words for isolated I/1; select its mapped position afterward. */
    public static String refinedWordReading(
            WordRefinement word, List<Line> words, List<Line> lines) {
        if (!words.isEmpty()) {
            Line nearest =
                    words.stream()
                            .min(
                                    Comparator.comparingDouble(
                                            line ->
                                                    Math.abs(line.center() - word.word.center())
                                                            + Math.abs(
                                                                    (line.top
                                                                                    + line.bottom
                                                                                    - word.word.top
                                                                                    - word.word
                                                                                            .bottom)
                                                                            / 2)))
                            .orElse(null);
            if (nearest != null
                    && Math.abs(nearest.center() - word.word.center())
                            < Math.max(word.word.height(), word.word.right - word.word.left))
                return clean(nearest.text);
            return "";
        }
        return lines.stream()
                .max(Comparator.comparingLong(line -> letterCount(line.text)))
                .map(Line::text)
                .orElse("");
    }

    /** The hint can break an OCR tie only when an independent enlarged reading agrees. */
    public static String corroborateTitleWord(String original, String enlarged, String hint) {
        if (!original.matches("[\\p{L}01]+")
                || !enlarged.matches("\\p{L}+")
                || !hint.matches("\\p{L}+")) return original;
        int[] source = original.codePoints().toArray(),
                zoom = enlarged.toLowerCase(Locale.ROOT).codePoints().toArray(),
                expected = hint.toLowerCase(Locale.ROOT).codePoints().toArray();
        if (source.length != zoom.length || source.length != expected.length) return original;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < source.length; i++) {
            int chosen = zoom[i] == expected[i] ? zoom[i] : Character.toLowerCase(source[i]);
            result.appendCodePoint(
                    Character.isUpperCase(source[i])
                                    || !Character.isLetter(source[i])
                                            && Character.isUpperCase(hint.codePointAt(i))
                            ? Character.toUpperCase(chosen)
                            : chosen);
        }
        return result.toString();
    }

    public static List<Line> applyTitleWord(
            List<Line> lines, WordRefinement word, String enlarged) {
        String corrected = corroborateTitleWord(word.word.text, enlarged, word.hint);
        if (corrected.equals(word.word.text)) return List.copyOf(lines);
        List<Line> result = new ArrayList<>();
        Pattern token =
                Pattern.compile("(?<!\\p{L})" + Pattern.quote(word.word.text) + "(?!\\p{L})");
        for (Line line : lines) {
            boolean contains =
                    word.word.left >= line.left - 5
                            && word.word.right <= line.right + 5
                            && word.word.top >= line.top - 5
                            && word.word.bottom <= line.bottom + 5;
            String text =
                    contains
                            ? token.matcher(line.text)
                                    .replaceFirst(Matcher.quoteReplacement(corrected))
                            : line.text;
            result.add(new Line(text, line.left, line.top, line.right, line.bottom));
        }
        return List.copyOf(result);
    }

    /** Separate the boxed name from its suffix label so the frame cannot hide descenders. */
    public static List<Refinement> frameRegions(Page page) {
        List<Refinement> regions = new ArrayList<>();
        Line heading =
                chooseTitle(
                        "",
                        page,
                        page.lines,
                        page.notationTop > 0 ? page.notationTop : page.height * .62f);
        for (Line line : page.lines) {
            Credit role = credit(line.text);
            if (role == null
                    || !role.suffix
                    || !inCreditHeader(page, line)
                    || LEGAL.matcher(line.text).find()) continue;
            Line name = nearestName(line, page.lines, heading, true);
            if (name == null) continue;
            float padding = page.width * .01f;
            regions.add(
                    new Refinement(
                            Math.max(0, name.left - padding),
                            Math.max(0, name.top - padding),
                            Math.min(page.width, name.right + padding),
                            Math.min(page.height, name.bottom + padding),
                            3));
        }
        return List.copyOf(regions);
    }

    /** Product advertisements identify other publications, not contributors to this score. */
    public static boolean isProductCatalogue(Page page) {
        String text =
                page.lines.stream()
                        .map(line -> clean(line.text))
                        .reduce((a, b) -> a + " " + b)
                        .orElse("");
        if (!Pattern.compile(
                        "(?iu)\\bmore\\h+(?:great\\h+)?(?:selections|titles|books|music)\\b"
                                + "|\\bprices\\b.{0,80}\\bavailability\\b.{0,80}\\bchange\\b")
                .matcher(text)
                .find()) return false;
        Pattern product =
                Pattern.compile(
                        "(?iu)^\\h*(00[0-9]{6})\\h+.*\\b(?:book|audio|tracks|accompaniment|edition)\\b");
        Set<String> codes = new LinkedHashSet<>();
        for (Line line : page.lines) {
            Matcher match = product.matcher(clean(line.text));
            if (match.find()) codes.add(match.group(1));
        }
        return codes.size() >= 3;
    }

    private static boolean containsPrintedPhrase(String text, String phrase) {
        String source =
                clean(text).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        String target =
                clean(phrase).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        return !target.isEmpty() && (" " + source + " ").contains(" " + target + " ");
    }

    private static String currentCataloguePerformer(
            Page page, List<Page> pages, String title, String hint) {
        List<Line> lines =
                page.lines.stream().sorted(Comparator.comparingDouble(Line::top)).toList();
        Pattern product =
                Pattern.compile(
                        "(?iu)^\\h*00[0-9]{6}\\h+.*\\b(?:book|audio|tracks|accompaniment|edition)\\b");
        boolean currentListed = false;
        for (String candidate : List.of(title, hint)) {
            if (letterCount(candidate) < 4) continue;
            boolean printed =
                    pages.stream()
                            .limit(2)
                            .filter(p -> !isProductCatalogue(p))
                            .anyMatch(
                                    p ->
                                            p.lines.stream()
                                                    .anyMatch(
                                                            l ->
                                                                    l.top < p.height * .5f
                                                                            && l.height()
                                                                                    >= p.width
                                                                                            * .009f
                                                                            && titleText(l.text)
                                                                            && containsPrintedPhrase(
                                                                                    l.text,
                                                                                    candidate)));
            if (!printed) continue;
            int priorProduct = -1;
            for (int i = 0; i < lines.size(); i++)
                if (product.matcher(clean(lines.get(i).text)).find()) {
                    for (int j = priorProduct + 1; j < i; j++) {
                        Line heading = lines.get(j);
                        String value = clean(heading.text);
                        if (heading.height() >= lines.get(i).height() * .9f
                                && Math.abs(heading.left - lines.get(i).left) <= page.width * .03f
                                && heading.top >= lines.get(i).top - page.height * .18f
                                && !value.matches(".*[•·;].*")
                                && !value.matches(
                                        "(?iu)^(?:all\\h+works|more\\h+(?:great\\h+)?selections)\\b.*")
                                && containsPrintedPhrase(value, candidate)) currentListed = true;
                    }
                    priorProduct = i;
                }
        }
        if (!currentListed) return "";
        Pattern scope =
                Pattern.compile(
                        "(?iu)^all\\h+(?:works|songs|pieces)\\h+as\\h+arranged(?:\\h+and\\h+|\\h*&\\h*)performed\\h+by\\h+(.+)$");
        for (Line line : lines) {
            Matcher match = scope.matcher(clean(line.text));
            if (match.matches() && creditNameText(match.group(1))) return clean(match.group(1));
        }
        return "";
    }

    private static boolean openingScoreRestoresCoverSpaces(
            Page page, Line heading, String before, String printed) {
        return !page.creditsOnly
                && page.notationTop > 0
                && heading != null
                && heading.bottom < page.notationTop
                && heading.top <= page.height * .15f
                && heading.height() >= page.width * .025f
                && heading.right - heading.left >= page.width * .3f
                && Math.abs(heading.center() - page.width * .5f) <= page.width * .15f
                && clean(printed).split("\\h+").length > clean(before).split("\\h+").length
                && clean(printed).split("\\h+").length <= 10
                && headingLetters(before).equals(headingLetters(printed))
                && CreditOcrIdentifierGuard.preservesIdentifiers(before, printed);
    }

    public static Result detect(String titleHint, List<Page> pages, Set<String> knownNames) {
        String fallback = clean(titleHint);
        Set<String> artists = new LinkedHashSet<>(), composers = new LinkedHashSet<>();
        Set<String> arrangers = new LinkedHashSet<>(), other = new LinkedHashSet<>();
        String title = "";
        boolean titleConfirmed = false;
        boolean titleFromOpeningCover = false;
        for (Page page : pages) {
            if (page.width <= 0 || page.height <= 0) continue;
            if (isProductCatalogue(page)) {
                String performer = currentCataloguePerformer(page, pages, title, fallback);
                if (!performer.isEmpty()) {
                    addNames(artists, creditNames(performer));
                    addNames(arrangers, creditNames(performer));
                }
                continue;
            }
            List<Line> lines =
                    joinDateBoxes(page.lines).stream()
                            .filter(l -> !clean(l.text).isEmpty())
                            .sorted(
                                    Comparator.comparingDouble((Line l) -> l.top)
                                            .thenComparingDouble(l -> l.left))
                            .toList();
            float titleLimit =
                    page.photographicCover
                            ? coverTitleLimit(page)
                            : page.notationTop > 0 ? page.notationTop : page.height * .62f;
            // Artwork backgrounds can contain five horizontal bands that look
            // like a staff. A page containing only one large centered text line
            // can still identify its cover heading below those bands.
            if (lines.size() == 1) {
                Line isolated = lines.get(0);
                if (titleText(isolated.text)
                        && isolated.height() >= page.width * .05f
                        && isolated.right - isolated.left >= page.width * .3f
                        && isolated.right - isolated.left <= page.width * .85f
                        && Math.abs(isolated.center() - page.width * .5f) < page.width * .15f
                        && isolated.bottom <= page.height * .95f
                        && isolated.top >= titleLimit) titleLimit = page.height * .95f;
            }
            titleLimit = Math.min(titleLimit, chordLyricsStart(page, lines));
            List<Line> titleEvidence = withoutContainedTitleFragments(page, lines, titleLimit);
            List<Line> titleLines = withoutDedications(page, titleEvidence, titleLimit);
            Line heading = chooseTitle(fallback, page, titleLines, titleLimit);
            String pageTitle =
                    heading == null ? "" : joinTitle(page, heading, titleLines, titleLimit);
            if (heading != null && page.photographicCover) {
                String stacked = photographicStackedTitle(page, heading, titleLines, titleLimit);
                if (!stacked.isEmpty()) pageTitle = stacked;
            }
            if (page.notationTop <= 0 && !same(fallback, pageTitle)) {
                String coverTitle =
                        corroboratedCoverTitle(fallback, page, titleEvidence, titleLimit);
                if (!coverTitle.isEmpty()) pageTitle = coverTitle;
            }
            boolean confirmed = corroboratesTitleHint(fallback, pageTitle);
            // Later notation pages may open a new section rather than identify the
            // document. Recover an absent title only from a corroborated or prominent
            // heading; staff-sized section captions cannot replace the import hint.
            boolean recoverLaterTitle =
                    title.isEmpty()
                            && heading != null
                            && heading.bottom <= titleLimit
                            && (confirmed
                                    || heading.height() >= page.width * .03f
                                    || heading.height() >= page.width * .015f
                                            && heading.right - heading.left >= page.width * .2f
                                            && Math.abs(heading.center() - page.width * .5f)
                                                    < page.width * .15f);
            if ((!page.creditsOnly || recoverLaterTitle)
                    && !pageTitle.isEmpty()
                    && (title.isEmpty()
                            || (!titleConfirmed
                                    && confirmed
                                    && !fold(title).startsWith(fold(pageTitle))))) {
                title = preferSpelling(fallback, pageTitle);
                titleConfirmed = confirmed;
                titleFromOpeningCover =
                        !page.creditsOnly && (page.notationTop <= 0 || page.photographicCover);
            } else if (titleFromOpeningCover
                    && openingScoreRestoresCoverSpaces(page, heading, title, pageTitle)) {
                title = preferSpelling(fallback, pageTitle);
                titleConfirmed = confirmed;
                titleFromOpeningCover = false;
            }
            Matcher coverPerformer =
                    Pattern.compile(
                                    "(?iu)^.{3,180}\\((?:(?:violin|piano|guitar|flute|cello|vocal)\\h+)?cover\\)\\h+([\\p{L}][\\p{L}\\p{N} ._'’&-]{2,80})$")
                            .matcher(pageTitle);
            if (!page.creditsOnly && coverPerformer.matches())
                addNames(artists, List.of(canonicalName(coverPerformer.group(1), knownNames)));
            Set<Line> claimed = new LinkedHashSet<>(), scopedClaimed = new LinkedHashSet<>();
            for (Line line : lines) {
                if (scopedClaimed.contains(line)) continue;
                String recordingPerformer = recordingPerformer(page, line, lines);
                if (!recordingPerformer.isEmpty()) {
                    addNames(artists, creditNames(recordingPerformer));
                    claimed.add(line);
                    continue;
                }
                ScopedArrangement scoped = resolveScopedArrangement(page, line, lines);
                if (scoped != null) {
                    String value =
                            scoped.credit.value.replaceFirst(
                                    "\\h*\\((?:1[6-9]|20)\\d{2}\\)\\h*$", "");
                    addNames(arrangers, creditNames(canonicalName(value, knownNames)));
                    claimed.addAll(scoped.lines);
                    scopedClaimed.addAll(scoped.lines);
                    continue;
                }
                if (photographicReviewByline(page, line, heading)
                        || photographicReviewHeadingName(page, line, heading, pageTitle)) {
                    other.add(clean(line.text));
                    claimed.add(line);
                    continue;
                }
                if (LEGAL.matcher(line.text).find() || !inCreditHeader(page, line)) continue;
                if (line != heading && INITIALLED_NAME.matcher(clean(line.text)).matches())
                    other.add(clean(line.text));
                Matcher featured = FEATURED.matcher(clean(line.text));
                if (featured.matches() && !collectionDescription(featured.group(2))) {
                    addNames(
                            artists,
                            List.of(
                                    canonicalName(
                                            featured.group(1).replaceAll("[,.;]+$", "").strip(),
                                            knownNames),
                                    canonicalName(
                                            featured.group(2)
                                                    .replaceAll("(?<=\\h)[•·]+(?=\\p{L})", ""),
                                            knownNames)));
                    claimed.add(line);
                    continue;
                }
                List<Credit> inlineRoles = distinctInlineRoles(line.text);
                if (!inlineRoles.isEmpty()) {
                    for (Credit role : inlineRoles) {
                        String name = canonicalName(clean(role.value), knownNames);
                        switch (role.role) {
                            case ARTIST -> addNames(artists, creditNames(name));
                            case COMPOSER -> addNames(composers, creditNames(name));
                            case ARRANGER -> addNames(arrangers, creditNames(name));
                            case LYRICS -> addNames(other, creditNames(name));
                        }
                    }
                    claimed.add(line);
                    continue;
                }
                Credit credit = credit(line.text);
                if (credit == null) credit = accompanimentArrangementCredit(page, line, lines);
                if (credit == null) continue;
                Matcher embeddedArrangement =
                        Pattern.compile("(?iu)^(.+?)[,;]\\h*(arranged\\h+by\\h+.+)$")
                                .matcher(clean(line.text));
                if (embeddedArrangement.matches()) other.add(clean(embeddedArrangement.group(1)));
                if (credit.role == Role.COMPOSER && clean(line.text).matches("(?iu)^by\\h+.+")) {
                    for (Line prior : lines)
                        if (clean(prior.text)
                                        .matches(
                                                "(?iu)^(?:adp\\.?|adapted)\\h+from\\h+(?:the\\h+)?arrangement$")
                                && line.top >= prior.bottom
                                && line.top - prior.bottom < prior.height() * 1.5f
                                && Math.abs(line.left - prior.left) < prior.height()) {
                            credit = new Credit(Role.ARRANGER, credit.value, false);
                            claimed.add(prior);
                            break;
                        }
                }
                String value =
                        separateDatedAttributionColumn(page, line, clean(credit.value), lines);
                boolean inline = !value.isEmpty();
                Line continuation = line;
                if (value.isEmpty()) {
                    Line overlapping =
                            credit.role == Role.ARTIST
                                    ? overlappingArtistHeading(line, lines, heading)
                                    : null;
                    if (overlapping != null) {
                        other.add(clean(overlapping.text));
                        claimed.add(line);
                        continue;
                    }
                    continuation =
                            exactDisplayRoleLabel(line.text) ? displayRoleName(page, line) : null;
                    if (continuation == null)
                        continuation = nearestName(line, lines, heading, credit.suffix);
                    if (continuation != null && inCreditHeader(page, continuation)) {
                        value = clean(continuation.text);
                        claimed.add(continuation);
                    }
                }
                if (value.isEmpty()
                        || credit.role == Role.ARTIST && collectionDescription(value)
                        || countedCollectionList(value)) continue;
                // A comma-terminated byline may continue in the same column. Labels in a
                // neighboring column cannot become a composer or arranger's surname.
                for (int part = 0; part < 6; part++) {
                    Line next = nearestName(continuation, lines, heading, false);
                    boolean grouped =
                            next != null
                                    && (credit.role == Role.COMPOSER
                                            || credit.role == Role.ARRANGER
                                            || credit.role == Role.LYRICS)
                                    && continuesPrintedNameGroup(value, continuation, next);
                    if (next == null
                            || !inCreditHeader(page, next)
                            || claimed.contains(next)
                            || !(value.endsWith(",")
                                    || value.matches("(?iu).*\\b(?:and|&)\\h*$")
                                    || clean(next.text).matches("(?iu)^(?:and|&)\\h+.+")
                                    || grouped)) break;
                    value += (grouped ? "; " : " ") + clean(next.text);
                    claimed.add(next);
                    continuation = next;
                }
                if (credit.role == Role.ARTIST
                        && clean(line.text).matches("(?iu).*\\brecorded\\h+by\\b.*"))
                    value =
                            value.replaceFirst(
                                            "\\h+(?i:on)\\h+(?:[\\p{Lu}\\p{N} .&'’–-]{2,}|(?i:.*\\b(?:records|recordings|label)\\b.*))$",
                                            "")
                                    .strip();
                // A year following an explicit attribution dates the contribution,
                // rather than forming part of the person's name.
                value =
                        canonicalName(
                                value.replaceFirst("\\h*\\((?:1[6-9]|20)\\d{2}\\)\\h*$", ""),
                                knownNames);
                if (credit.role == Role.ARRANGER
                        && (clean(line.text)
                                        .matches(
                                                "(?iu).*\\btrans(?:cribed|c(?:r)?iption)\\h+by\\b.*")
                                || insertedTranscriptionLetterCredit(line.text) != null)
                        && (value.matches("[a-z0-9][a-z0-9.-]*\\.[a-z]{2,24}")
                                || value.matches("(?iu)songscription(?:\\h+AI)?"))) {
                    other.add(value);
                    claimed.add(line);
                    continue;
                }
                if ((!inline && (same(value, title) || same(value, pageTitle)))
                        || !creditNameText(value)) continue;
                switch (credit.role) {
                    case ARTIST -> addNames(artists, creditNames(value));
                    case COMPOSER ->
                            addNames(
                                    composers,
                                    clean(line.text).matches("^[\"“].*")
                                            ? quotedWorkComposerNames(value)
                                            : creditNames(value));
                    case ARRANGER -> addNames(arrangers, creditNames(value));
                    case LYRICS -> addNames(other, creditNames(value));
                }
                if (credit.role == Role.COMPOSER
                        && combinedCompositionArrangement(line.text) != null)
                    addNames(arrangers, creditNames(value));
                claimed.add(line);
            }
            // Unlabeled nearby names are evidence for review, not automatic artist roles.
            if (heading != null && (!page.creditsOnly || datedMovementOpening(page, heading)))
                for (Line line : lines) {
                    boolean datedAuthor = DATED_AUTHOR.matcher(clean(line.text)).matches();
                    if (page.creditsOnly && !datedAuthor) continue;
                    // A separate side column may start above a shorter catalogue
                    // heading while its birth/death line overlaps that heading.
                    boolean sideDatedAuthor =
                            datedAuthor
                                    && line.bottom >= heading.top
                                    && line.top <= heading.bottom
                                    && (line.left >= heading.right || line.right <= heading.left);
                    if (claimed.contains(line)
                            || line == heading
                            || line.top < heading.bottom
                                    && !sideHeaderReviewName(page, heading, line)
                                    && (!datedAuthor
                                            || line.top < heading.bottom - heading.height() * .2f
                                                    && !sideDatedAuthor)
                            || line.top > Math.min(titleLimit, heading.bottom + page.height * .18f)
                            || !nameText(line.text)
                            || credit(line.text) != null
                            || NOT_TITLE.matcher(line.text).find()
                            || LEGAL.matcher(line.text).find()
                            || REVIEW_DIRECTION.matcher(clean(line.text)).matches()
                            || publicationAudioAccessBadge(page, line, lines)
                            || compoundInstrumentationCaption(line.text)
                            || instructionalDocumentHeading(page, heading, lines)
                                    && paragraphLine(page, line, lines)
                            || staffCharacterDirection(page, line, lines, titleLimit)
                            || line.height() >= heading.height() * .85f
                                    && !DATED_AUTHOR.matcher(clean(line.text)).matches()) continue;
                    String name = canonicalName(clean(line.text), knownNames);
                    Matcher dated = DATED_AUTHOR.matcher(clean(line.text));
                    if (dated.matches()) {
                        // Lifespan dates identify the person, not their contribution.
                        // Keep that printed name available for role review.
                        name = canonicalName(dated.group(1), knownNames);
                        if (datedScoreAuthorContext(page, heading, line, titleLines)) {
                            addNames(composers, List.of(name));
                            other.remove(name);
                            continue;
                        }
                    }
                    if (!same(name, title)
                            && !composers.contains(name)
                            && !arrangers.contains(name)
                            && !artists.contains(name)) other.add(name);
                }
        }
        splitCorroboratedContributors(artists);
        splitCorroboratedContributors(composers);
        splitCorroboratedContributors(arrangers);
        return new Result(
                title.isEmpty() ? fallback : title,
                String.join("; ", artists),
                String.join("; ", composers),
                String.join("; ", arrangers),
                List.copyOf(other));
    }

    /** Smaller names beside an opening score heading remain evidence for role review. */
    private static boolean sideHeaderReviewName(Page page, Line heading, Line line) {
        float headerEnd = page.notationTop > 0 ? page.notationTop : page.height * .2f;
        if (page.creditsOnly
                || page.notationTop <= 0
                        && (page.photographicCover || heading.top > page.height * .15f)
                || headerEnd >= page.height * .3f
                || heading.bottom >= headerEnd
                || Math.abs(heading.center() - page.width * .5f) >= page.width * .2f
                || line.top < heading.top
                || line.top >= heading.bottom
                || line.bottom > headerEnd
                || line.height() < page.width * .008f
                || line.height() >= heading.height() * .85f
                || line.right - line.left >= page.width * .35f
                || !(line.left >= heading.right + line.height()
                        || line.right <= heading.left - line.height())) return false;
        return properReviewName(line.text);
    }

    private static boolean properReviewName(String text) {
        String name =
                clean(text)
                        .replaceFirst("\\h*\\((?:1[5-9]|20)\\d{2}\\)\\h*$", "")
                        .replaceFirst("[,.;]+$", "");
        String word =
                "(?:\\p{Lu}[\\p{Ll}\\p{M}]*(?:['’–-]\\p{Lu}?[\\p{Ll}\\p{M}]+)*|\\p{Lu}{2,}|\\p{Lu}\\.)";
        return name.matches(word + "(?:\\h+" + word + "){0,5}")
                && Arrays.stream(name.split("\\h+"))
                        .noneMatch(value -> NOT_TITLE.matcher(value).matches())
                && !compoundInstrumentationCaption("for " + name);
    }

    // A spatially stacked publication badge is not an unlabeled contributor.
    // Individual names and explicit performer credits are handled normally.
    private static boolean publicationAudioAccessBadge(Page page, Line line, List<Line> lines) {
        String caption = clean(line.text).toLowerCase(Locale.ROOT);
        if (line.height() <= 0
                || line.height() > page.width * .04f
                || !(caption.equals("audio")
                        || caption.equals("access")
                        || caption.equals("playback")
                        || publicationIncludedCaption(caption))) return false;
        Line audio = null, access = null, included = null;
        for (Line nearby : lines) {
            if (Math.min(Math.abs(nearby.right - line.right), Math.abs(nearby.left - line.left))
                            > Math.max(line.height(), nearby.height()) * 1.5f
                    || Math.abs(nearby.top - line.top) > line.height() * 5) continue;
            String value = clean(nearby.text);
            if (value.equalsIgnoreCase("audio")) audio = nearby;
            else if (value.equalsIgnoreCase("access")) access = nearby;
            else if (publicationIncludedCaption(value.toLowerCase(Locale.ROOT))) included = nearby;
        }
        return audio != null
                && access != null
                && included != null
                && audio.bottom <= access.top + audio.height() * .1f
                && access.top - audio.bottom < Math.max(audio.height(), access.height()) * 1.5f
                && access.bottom <= included.top + access.height() * .1f
                && included.top - access.bottom
                        < Math.max(access.height(), included.height()) * 1.5f
                && (!caption.equals("playback")
                        || line.top >= included.bottom
                                && line.top - included.bottom < line.height() * 1.5f);
    }

    /** OCR damage does not change the meaning of an otherwise complete spatial badge. */
    private static boolean publicationIncludedCaption(String value) {
        return value.matches("[a-z]{6,10}") && editDistance(value, "included") <= 2;
    }

    private static boolean datedMovementOpening(Page page, Line heading) {
        return page.notationTop > 0
                && heading.height() >= page.width * .03f
                && heading.top < page.height * .15f
                && heading.bottom < page.notationTop * .65f
                && heading.right - heading.left > Math.max(heading.height() * 2, page.width * .08f)
                && Math.abs(heading.center() - page.width * .5f) < page.width * .2f;
    }

    // Score-header authorship is supported by musical work details, not dates alone.
    private static boolean datedScoreAuthorContext(
            Page page, Line heading, Line name, List<Line> header) {
        if (page.creditsOnly && !datedMovementOpening(page, heading)
                || page.notationTop <= 0
                || name.bottom > page.notationTop
                || !header.contains(name)
                || heading.bottom >= page.notationTop
                || instructionalDocumentHeading(page, heading, header)) return false;
        Pattern catalogue =
                Pattern.compile(
                        "(?iu)\\b(?:op(?:us)?\\.?|WoO|BWV|QV|KV|K\\.?|L\\.?|D\\.?|RV|HWV|Hob\\.?)\\h*(?:posth(?:umous)?\\.?\\h*)?[0-9]+");
        Pattern sourceWork =
                Pattern.compile(
                        "(?iu)^from\\h+(?:(?:the|a)\\h+)?(?:orchestral\\h+)?(?:suite|sonat[ae]|duet|concerto|opera|symphony|[\\p{L}’'-]+\\h+(?:suite|sonat[ae]|duet|concerto|opera|symphony))\\b.*");
        for (Line context : header) {
            if (context.bottom >= page.notationTop
                    || context.top < heading.top - heading.height()
                    || context.top > name.bottom + page.height * .08f
                    || LEGAL.matcher(context.text).find()) continue;
            String value = clean(context.text);
            String instrumentation =
                    value.replaceFirst(
                            "(?iu)^(?:fantasia|fantasy|sonat[ae]|concerto|duet)\\h+(?=for\\h+)",
                            "");
            boolean instrument =
                    value.matches(
                            "(?iu)^(?:flute|piano|violin|viola|cello|guitar|clarinet|oboe|bassoon|trumpet|saxophone|strings)(?:\\h*(?:I{1,3}|[1-3]))?$");
            boolean namedForm =
                    context == heading
                            && value.matches(
                                    "(?iu).*\\b(?:dance|rondo|sonat[ae]|suite|concerto|symphony|quartet|duet|cs[aá]rd[aá]s|mazurka|polonaise|waltz|gavotte|minuet)\\b.*");
            if (centeredSourceWorkCaption(page, heading, name, context)
                    || catalogue.matcher(value).find()
                    || compoundInstrumentationCaption(instrumentation)
                    || sourceWork.matcher(value).matches()
                    || instrument
                    || namedForm) return true;
        }
        return false;
    }

    /** A centered source-work caption supports the dated name in a score header. */
    private static boolean centeredSourceWorkCaption(
            Page page, Line heading, Line name, Line context) {
        if (context == heading
                || context == name
                || context.top < heading.bottom - heading.height() * .1f
                || context.bottom >= page.notationTop
                || context.top > name.bottom
                || context.height() < Math.max(12, page.width * .008f)
                || context.height() >= heading.height() * .95f
                || context.right - context.left < (heading.right - heading.left) * .4f
                || context.right - context.left > page.width * .8f
                || Math.abs(context.center() - heading.center())
                        > Math.max(page.width * .08f, (heading.right - heading.left) * .25f)
                || LEGAL.matcher(context.text).find()
                || credit(context.text) != null) return false;
        Matcher source = Pattern.compile("(?iu)^from\\h+(.{3,100})$").matcher(clean(context.text));
        if (!source.matches()) return false;
        String work = source.group(1).replaceFirst("(?iu)^(?:the|a|an)\\h+(?=\\p{Lu})", "");
        if (work.matches(
                "(?iu).*\\b(?:press(?:es)?|publishers?|publishing|websites?|editions?|printed)\\b.*"))
            return false;
        return work.matches("(?u)^\\p{Lu}[\\p{L}\\p{M}’'–-]*(?:\\h+[\\p{L}\\p{M}’'–-]+){1,9}$");
    }

    private static Line chooseTitle(String hint, Page page, List<Line> lines, float limit) {
        Line best = null;
        double bestScore = -1;
        float lyricsStart = chordLyricsStart(page, lines);
        float maximum = 1;
        for (Line l : lines)
            if (l.top < limit && (titleText(l.text) || prominentTempoTitle(page, l)))
                maximum = Math.max(maximum, l.height());
        for (Line l : lines) {
            String key = fold(l.text), source = fold(hint);
            // Existing imports may already have a byline saved as the title. A
            // smaller name cannot override the printed heading merely by matching it.
            boolean confirmedHint =
                    key.length() >= 5
                            && (source.equals(key) || source.startsWith(key))
                            && (page.notationTop <= 0
                                    || l.height() >= maximum * .85f
                                    || instructionalDocumentHeading(page, l, lines));
            if (l.top >= lyricsStart
                    || (l.top >= limit && !(page.notationTop <= 0 && confirmedHint))
                    || !(titleText(l.text) || prominentTempoTitle(page, l))
                    || repeatedChordDiagramLabel(l, lines)
                    || staffBoundaryLabel(page, l)
                    || continuationFingeringBodyTitle(page, l, lines)
                    || repeatedStaffDynamicsBodyTitle(page, l, lines)
                    || (!corroboratesTitleHint(hint, l.text)
                            && staffSizedBodyTitle(page, l, lines, limit))
                    || DATED_AUTHOR.matcher(clean(l.text)).matches()
                    || paragraphLine(page, l, lines)
                    || clippedInstrumentLabel(page, l)
                    || (!confirmedHint && staffCharacterDirection(page, l, lines, limit))) continue;
            double score =
                    4 * l.height() / maximum
                            + Math.max(0, 1 - Math.abs(l.center() / page.width - .5) * 2)
                            + .2 * (1 - l.top / page.height);
            if (confirmedHint || instructionalDocumentHeading(page, l, lines)) score += 8;
            if (prominentArticleHeading(page, l, lines, limit, maximum)) score += 1.5;
            if (score > bestScore) {
                best = l;
                bestScore = score;
            }
        }
        return best;
    }

    /** Local section/direction pairs and narrow body marks are not document headings. */
    private static boolean staffSizedBodyTitle(
            Page page, Line line, List<Line> lines, float limit) {
        if (page.photographicCover || page.notationTop <= 0 || line.height() >= page.width * .025f)
            return false;
        float width = line.right - line.left;
        // A late near-square recognition box can be a cluster of music symbols.
        // Header words, including short headings, remain eligible above this body band.
        if (compactStaffBodyMark(page, line)) return true;
        // A narrow margin label beside separately located body debris identifies a part.
        if (line.top >= page.height * .10f
                && line.left < page.width * .06f
                && line.center() < page.width * .15f
                && width < page.width * .06f
                && lines.stream()
                        .anyMatch(other -> other != line && compactStaffBodyMark(page, other)))
            return true;
        if (line.height() >= page.width * .02f
                || line.center() >= page.width * .35f
                || width >= page.width * .30f
                || line.bottom >= limit
                || limit - line.bottom >= page.height * .12f) return false;
        for (Line direction : lines) {
            if (direction == line
                    || direction.top < line.bottom
                    || direction.top - line.bottom > line.height() * 2.5f
                    || Math.abs(direction.left - line.left) > line.height()
                    || direction.height() < line.height() * .6f
                    || direction.height() > line.height() * 1.8f) continue;
            if (staffCharacterDirection(page, direction, lines, limit)) return true;
        }
        return false;
    }

    private static boolean compactStaffBodyMark(Page page, Line line) {
        return line.height() < page.width * .025f
                && line.top >= page.height * .15f
                && line.bottom <= page.notationTop
                && page.notationTop - line.bottom <= Math.max(line.height() * 4, page.height * .04f)
                && line.right - line.left <= line.height() * 1.5f
                && letterCount(line.text) <= 4;
    }

    /** Small part-label boxes crossing the first staff cannot identify the document. */
    private static boolean staffBoundaryLabel(Page page, Line line) {
        return !page.photographicCover
                && page.notationTop > 0
                && line.top < page.notationTop
                && line.bottom >= page.notationTop
                && line.height() < page.width * .025f
                && line.right - line.left < page.width * .18f
                && line.center() < page.width * .35f;
    }

    /** Repeated finger-number rows locate notation when photographed staves are missed. */
    private static boolean continuationFingeringBodyTitle(
            Page page, Line candidate, List<Line> lines) {
        if (page.creditsOnly || page.notationTop > 0 || candidate.height() >= page.width * .025f)
            return false;
        List<Line> fingers =
                lines.stream()
                        .filter(
                                line ->
                                        clean(line.text).matches("[0-4](?:[0-4]|\\h+[0-4])*")
                                                && line.height() < page.width * .025f
                                                && line.top > page.height * .08f
                                                && line.bottom < page.height * .88f)
                        .sorted(Comparator.comparingDouble(Line::top))
                        .toList();
        if (fingers.size() < 12) return false;
        List<Line> groups =
                fingers.stream()
                        .filter(
                                line ->
                                        clean(line.text).replaceAll("\\h+", "").length() >= 3
                                                && line.right - line.left > page.width * .05f)
                        .toList();
        if (groups.size() < 4) return false;
        float first = groups.get(0).top, last = groups.get(groups.size() - 1).top;
        if (last - first < page.height * .15f) return false;
        int rows = 1;
        float prior = first;
        for (Line line : groups)
            if (line.top - prior > Math.max(line.height() * 3, page.height * .025f)) {
                rows++;
                prior = line.top;
            }
        if (rows < 3) return false;
        // Finger labels sit beneath the staff. Leave room for the staff and its
        // expression marking, while preserving headings above that body region.
        float digitHeight =
                groups.stream()
                        .map(Line::height)
                        .sorted()
                        .skip(groups.size() / 2)
                        .findFirst()
                        .orElse(1f);
        return candidate.top >= first - digitHeight * 6;
    }

    /** Repeated dynamic labels on a staff row distinguish small body instructions. */
    private static boolean repeatedStaffDynamicsBodyTitle(
            Page page, Line candidate, List<Line> lines) {
        if (candidate.height() >= page.width * .025f || candidate.center() >= page.width * .35f)
            return false;
        return lines.stream()
                        .filter(
                                line ->
                                        line.left > candidate.right + candidate.height()
                                                && clean(line.text)
                                                        .matches(
                                                                "(?iu)^(?:p{1,3}|mp|mf|f{1,3}|sfz)$")
                                                && line.height() > candidate.height() * .5f
                                                && line.height() < candidate.height() * 1.8f
                                                && Math.min(line.bottom, candidate.bottom)
                                                                - Math.max(line.top, candidate.top)
                                                        > Math.min(
                                                                        line.height(),
                                                                        candidate.height())
                                                                * .5f)
                        .limit(2)
                        .count()
                == 2;
    }

    private static boolean instructionalDocumentHeading(Page page, Line heading, List<Line> lines) {
        if (!clean(heading.text).equals(clean(heading.text).toUpperCase(Locale.ROOT))
                || page.notationTop < page.height * .45f
                || heading.top > page.height * .08f
                || heading.height() < page.width * .015f
                || heading.right - heading.left < heading.height() * 5
                || clean(heading.text).split("\\h+").length < 2
                || DATED_AUTHOR.matcher(clean(heading.text)).matches()) return false;
        return lines.stream()
                        .filter(
                                l ->
                                        l.top > heading.bottom
                                                && l.bottom < page.notationTop
                                                && l.bottom < heading.bottom + page.height * .20f
                                                && clean(l.text).length() >= 50
                                                && clean(l.text).split("\\h+").length >= 9
                                                && l.right - l.left > page.width * .45f)
                        .limit(3)
                        .count()
                == 3;
    }

    private static boolean prominentArticleHeading(
            Page page, Line line, List<Line> lines, float limit, float maximum) {
        if (page.notationTop > 0
                || line.height() < maximum * .70f
                || line.height() < page.width * .04f) return false;
        for (Line article : lines) {
            if (!clean(article.text)
                    .matches("(?iu)^(?:a|an|the|das|die|der|le|la|les|el|los|las|il|un|une|uno)$"))
                continue;
            if (article.top >= limit
                    || article.height() < maximum * .70f
                    || article.height() < page.width * .04f) continue;
            for (Line work : lines) {
                if (work == article
                        || (line != article && line != work)
                        || work.top < article.bottom
                        || work.top >= limit
                        || work.top - article.bottom >= article.height() * .8f
                        || work.height() < article.height() * .85f
                        || work.height() > article.height() * 1.15f
                        || Math.abs(work.center() - article.center())
                                >= Math.max(work.height(), article.height()) * .5f
                        || !titleText(work.text)
                        || INITIALLED_NAME.matcher(clean(work.text)).matches()
                        || paragraphLine(page, work, lines)
                        || letterCount(work.text) < 4) continue;
                return true;
            }
        }
        return false;
    }

    private static boolean repeatedChordDiagramLabel(Line line, List<Line> lines) {
        if (!clean(line.text).equalsIgnoreCase("chord")) return false;
        boolean repeated =
                lines.stream()
                        .anyMatch(
                                other ->
                                        other != line
                                                && clean(other.text).equalsIgnoreCase("chord")
                                                && Math.abs(other.left - line.left)
                                                        < line.height());
        if (!repeated) return false;
        return lines.stream()
                .anyMatch(
                        label ->
                                clean(label.text).equalsIgnoreCase("chord")
                                        && lines.stream()
                                                .anyMatch(
                                                        symbol ->
                                                                clean(symbol.text)
                                                                                .matches(
                                                                                        "[A-G](?:#|b)?(?:m|maj|dim|sus)?\\d*")
                                                                        && symbol.bottom
                                                                                <= label.top
                                                                        && label.top - symbol.bottom
                                                                                < label.height() * 3
                                                                        && Math.abs(
                                                                                        symbol.left
                                                                                                - label.left)
                                                                                < label.height()));
    }

    private static List<Line> joinDateBoxes(List<Line> input) {
        List<Line> lines = new ArrayList<>(input);
        for (int i = 0; i < lines.size(); i++) {
            Line start = lines.get(i);
            if (!clean(start.text).matches("(?iu).+\\((?:1[5-9]|20)\\d{2}\\h*[-–—]?")) continue;
            for (int j = 0; j < lines.size(); j++) {
                Line end = lines.get(j);
                if (i == j
                        || !clean(end.text).matches("[-–—]?\\h*(?:1[5-9]|20)\\d{2}\\)")
                        || end.left < start.right - start.height() * .2f
                        || end.left - start.right > start.height() * 1.5f
                        || Math.abs((start.top + start.bottom - end.top - end.bottom) * .5f)
                                > start.height() * .3f
                        || end.height() < start.height() * .7f
                        || end.height() > start.height() * 1.4f) continue;
                String combined = clean(start.text) + clean(end.text);
                if (!DATED_AUTHOR.matcher(combined).matches()) continue;
                lines.set(
                        i,
                        new Line(
                                combined,
                                start.left,
                                Math.min(start.top, end.top),
                                end.right,
                                Math.max(start.bottom, end.bottom)));
                lines.remove(j);
                if (j < i) i--;
                break;
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            Line name = lines.get(i);
            if (!clean(name.text).matches("[\\p{L} .’'–-]{3,80}") || !titleText(name.text))
                continue;
            for (int j = 0; j < lines.size(); j++) {
                Line date = lines.get(j);
                String combined = clean(name.text) + " " + clean(date.text);
                if (i == j
                        || !clean(date.text)
                                .matches(
                                        "\\((?:1[5-9]|20)\\d{2}\\h*[-–—]\\h*(?:1[5-9]|20)\\d{2}\\)")
                        || !DATED_AUTHOR.matcher(combined).matches()
                        || date.top < name.bottom
                        || date.top - name.bottom > name.height() * 1.2f
                        || Math.abs(date.right - name.right) > name.height() * .5f
                        || date.height() < name.height() * .7f
                        || date.height() > name.height() * 1.4f) continue;
                lines.set(
                        i,
                        new Line(
                                combined,
                                name.left,
                                name.top,
                                Math.max(name.right, date.right),
                                date.bottom));
                lines.remove(j);
                if (j < i) i--;
                break;
            }
        }
        return lines;
    }

    private static float chordLyricsStart(Page page, List<Line> lines) {
        int sections = 0, chords = 0;
        float first = page.height;
        Line section = null;
        for (Line line : lines) {
            String text = clean(line.text);
            if (text.matches(
                    "(?iu)^\\[(?:intro|verse|chorus|bridge|break|outro)(?:\\h+\\d+)?\\]$")) {
                sections++;
                if (line.top < first) {
                    first = line.top;
                    section = line;
                }
            }
            if (text.matches(
                    "(?:[A-G](?:#|b)?(?:m|maj|dim|sus)?\\d*(?:/[A-G](?:#|b)?)?)(?:\\h+(?:[A-G](?:#|b)?(?:m|maj|dim|sus)?\\d*(?:/[A-G](?:#|b)?)?))*"))
                chords++;
        }
        if (sections < 2 || chords < 3) return page.height;
        // A continuation page can begin with several lyric lines before its
        // first section label. Their body-sized aligned text is not a heading.
        int preceding = 0;
        float bodyStart = first;
        for (Line line : lines)
            if (line.top < first
                    && Math.abs(line.left - section.left) < section.height() * 2
                    && line.height() >= section.height() * .7f
                    && line.height() <= section.height() * 1.4f) {
                preceding++;
                bodyStart = Math.min(bodyStart, line.top);
            }
        return preceding >= 3 ? bodyStart : first;
    }

    private static boolean prominentTempoTitle(Page page, Line line) {
        if (!TEMPO_TITLE_NAME.matcher(clean(line.text)).matches()) return false;
        boolean sourceSubtitle =
                page.lines.stream()
                        .anyMatch(
                                next ->
                                        FROM_SUBTITLE.matcher(clean(next.text)).matches()
                                                && next.top >= line.bottom
                                                && next.top - line.bottom < line.height() * 1.2f
                                                && Math.abs(next.center() - line.center())
                                                        < line.height());
        return line.height() >= page.height * (sourceSubtitle ? .01f : .02f)
                && Math.abs(line.center() - page.width * .5f) < page.width * .15f
                && line.bottom
                        < (page.notationTop > 0
                                ? page.notationTop - page.height * .03f
                                : page.height * .25f);
    }

    private static boolean paragraphLine(Page page, Line line, List<Line> lines) {
        if (clean(line.text).split("\\h+").length < 12 || line.right - line.left < page.width * .5f)
            return false;
        if (line.text.contains("•") || line.text.contains("·")) return true;
        for (Line next : lines) {
            float gap = Math.max(line.top - next.bottom, next.top - line.bottom);
            if (next == line
                    || gap < 0
                    || gap > Math.max(line.height(), next.height())
                    || Math.abs(line.left - next.left) > line.height() * 2
                    || next.height() < line.height() * .7f
                    || next.height() > line.height() * 1.4f
                    || clean(next.text).split("\\h+").length < 3) continue;
            if (clean(line.text)
                            .matches("(?iu)^(?:when|try|notice|later|in\\h+this|use\\h+the)\\b.*")
                    && clean(line.text).split("\\h+").length >= 18
                    && next.top >= line.bottom
                    && clean(next.text).endsWith(".")) return true;
            if (clean(next.text).split("\\h+").length < 12) continue;
            if ((line.text + " " + next.text).matches("(?s).*\\.\\h+\\p{Lu}.*")) return true;
        }
        return false;
    }

    private static boolean clippedInstrumentLabel(Page page, Line line) {
        String key = fold(line.text);
        if (line.left > page.width * .04f || line.center() > page.width * .1f || key.length() < 3)
            return false;
        for (String instrument :
                List.of(
                        "violin",
                        "viola",
                        "cello",
                        "piano",
                        "flute",
                        "guitar",
                        "clarinet",
                        "recorder"))
            if (instrument.length() > key.length() && instrument.endsWith(key)) return true;
        return false;
    }

    private static boolean staffCharacterDirection(
            Page page, Line line, List<Line> lines, float limit) {
        if (line.center() >= page.width * .35f
                || line.height() >= page.height * .03f
                || !clean(line.text)
                        .matches(
                                "(?iu)^(?:stately|relaxed|gentle|gently|freely|lively|flowing|springy|reverently|sustained|playfully|warmly|mod[ec]rat[ec]ly|poco\\h+rubato)\\b.*"))
            return false;
        if (line.top >= limit - page.height * .12f) return true;
        // Pale notation can hide the opening staff. An immediately following instrument
        // label also identifies a performance direction without trusting that boundary.
        for (Line next : lines) {
            float gap = next.top - line.bottom;
            if (gap >= 0
                    && gap < line.height()
                    && Math.abs(next.center() - line.center()) < line.height() * 3
                    && clean(next.text)
                            .matches(
                                    "(?iu)^(?:piano|flute|guitar|(?:soprano\\h+)?recorder|violin|viola|cello|clarinet)$"))
                return true;
        }
        return false;
    }

    private static boolean corroboratesTitleHint(String hint, String printed) {
        if (same(hint, printed)) return true;
        String expected = titleHintWords(hint), source = titleHintWords(printed);
        if (expected.split(" ").length < 3) return false;
        // Every substantive hint word must be printed in order. Article placement
        // and a printed continuation do not make a clearer title unconfirmed.
        return expected.length() >= 8
                && (source.equals(expected) || source.startsWith(expected + " "));
    }

    private static String titleHintWords(String value) {
        return Normalizer.normalize(clean(value), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("(?iu)\\b(?:the|an|a)\\b", " ")
                .strip()
                .replaceAll(" +", " ");
    }

    private static List<Line> withoutDedications(Page page, List<Line> lines, float limit) {
        if (page.notationTop <= 0) return lines;
        Set<Line> dedication = new LinkedHashSet<>();
        for (Line label : lines) {
            if (label.top >= limit
                    || !clean(label.text)
                            .matches(
                                    "(?iu)^(?:(?:to|for)\\h+(?:my|our)\\h+.+|dedicated\\h+to\\h+.+)$"))
                continue;
            boolean headingBelow =
                    lines.stream()
                            .anyMatch(
                                    l ->
                                            l.top > label.bottom
                                                    && l.top < limit
                                                    && l.top - label.bottom < page.height * .16f
                                                    && l.height() >= label.height() * 1.5f
                                                    && titleText(l.text)
                                                    && !DATED_AUTHOR
                                                            .matcher(clean(l.text))
                                                            .matches());
            if (!headingBelow) continue;
            dedication.add(label);
            for (Line name : lines)
                if (name != label
                        && name.top >= label.bottom
                        && name.top - label.bottom < Math.max(name.height(), label.height()) * .8f
                        && Math.abs(name.center() - label.center())
                                < Math.max(name.height(), label.height()) * 2
                        && name.height() < label.height() * 2
                        && titleText(name.text)) dedication.add(name);
        }
        return lines.stream().filter(l -> !dedication.contains(l)).toList();
    }

    private static String corroboratedCoverTitle(
            String hint, Page page, List<Line> lines, float limit) {
        String target = fold(hint);
        if (target.isEmpty()) return "";
        List<Line> candidates =
                lines.stream()
                        .filter(
                                l ->
                                        l.top < limit
                                                && l.height() >= page.width * .025f
                                                && titleText(l.text)
                                                && !DATED_AUTHOR.matcher(clean(l.text)).matches())
                        .toList();
        for (int start = 0; start < candidates.size(); start++) {
            Line previous = candidates.get(start);
            if (start > 0 && same(hint, previous.text)) {
                Line above = candidates.get(start - 1);
                float height = Math.max(above.height(), previous.height());
                // A complete, independently located printed title can sit below a
                // separate cover-name row. Do not prepend that row solely because
                // both use large lettering. The hint supplies no missing characters.
                if (above.bottom <= previous.top
                        && previous.top - above.bottom < height * .8f
                        && Math.min(above.height(), previous.height()) >= height * .85f
                        && Math.abs(above.center() - previous.center()) < height * 1.6f)
                    return clean(previous.text);
            }
            StringBuilder text = new StringBuilder(clean(previous.text));
            float largest = previous.height();
            if (!target.startsWith(fold(text.toString()))) continue;
            for (int next = start + 1; next < candidates.size() && next <= start + 5; next++) {
                Line line = candidates.get(next);
                largest = Math.max(largest, line.height());
                // Cover typography may vary in size and alignment, but each printed
                // fragment must overlap horizontally and continue the complete hint.
                if (line.height() < largest * .4f
                        || previous.height() < largest * .4f
                        || line.top - previous.bottom > largest * .9f
                        || Math.min(line.right, previous.right)
                                <= Math.max(line.left, previous.left)) break;
                text.append(' ').append(clean(line.text));
                String joined = text.toString();
                if (same(hint, joined)) return joined;
                if (!target.startsWith(fold(joined))) break;
                previous = line;
            }
        }
        var printed = PrintedCoverTitleEvidence.select(hint, page.width, candidates);
        return printed == null ? "" : printed.text();
    }

    /** Bare photograph bylines are retained for review without assigning a contributor role. */
    private static boolean photographicReviewByline(Page page, Line line, Line heading) {
        if (!page.photographicCover
                || page.creditsOnly
                || heading == null
                || line.top < heading.bottom
                || line.top - heading.bottom > page.height * .2f
                || line.bottom > page.height
                || line.height() < 16
                || line.height() > heading.height() * 1.5f
                || Math.abs(line.center() - heading.center()) > page.width * .15f) return false;
        Matcher byline = Pattern.compile("(?iu)^by\\h+(.{3,80})$").matcher(clean(line.text));
        return byline.matches()
                && creditNameText(byline.group(1))
                && credit(byline.group(1)) == null
                && !NOT_TITLE.matcher(byline.group(1)).find()
                && !LEGAL.matcher(line.text).find();
    }

    /** A separate centered cover name remains review evidence above the work heading. */
    private static boolean photographicReviewHeadingName(
            Page page, Line line, Line heading, String pageTitle) {
        if (!page.photographicCover
                || page.creditsOnly
                || heading == null
                || line == heading
                || line.top < 0
                || heading.top - line.bottom < page.height * .15f
                || line.height() < page.width * .025f
                || line.height() >= heading.height() * .85f
                || line.right - line.left < page.width * .2f
                || line.right - line.left > page.width * .8f
                || Math.abs(line.center() - heading.center()) > page.width * .1f
                || credit(line.text) != null
                || LEGAL.matcher(line.text).find()
                || containsPrintedPhrase(pageTitle, line.text)) return false;
        return properReviewName(line.text);
    }

    /** A prominent photographed heading may continue in a tightly aligned vertical stack. */
    private static String photographicStackedTitle(
            Page page, Line heading, List<Line> lines, float limit) {
        if (!page.photographicCover
                || page.creditsOnly
                || heading.height() < page.width * .06f
                || heading.right - heading.left < page.width * .2f) return "";
        List<Line> group = new ArrayList<>();
        group.add(heading);
        Line prior = heading;
        for (int part = 0; part < 3; part++) {
            final Line previous = prior;
            List<Line> candidates =
                    lines.stream()
                            .filter(
                                    line ->
                                            !group.contains(line)
                                                    && line.top >= previous.bottom
                                                    && line.bottom <= limit
                                                    && line.top - previous.bottom
                                                            <= Math.max(
                                                                            previous.height(),
                                                                            line.height())
                                                                    * .6f
                                                    && line.height() >= heading.height() * .45f
                                                    && line.height() <= heading.height() * 1.15f
                                                    && line.right - line.left >= page.width * .2f
                                                    && Math.min(
                                                                    Math.abs(
                                                                            line.left
                                                                                    - heading.left),
                                                                    Math.abs(
                                                                            line.center()
                                                                                    - heading
                                                                                            .center()))
                                                            <= Math.max(
                                                                            line.height(),
                                                                            heading.height())
                                                                    * .35f
                                                    && clean(line.text).length() >= 3
                                                    && titleText(line.text)
                                                    && credit(line.text) == null
                                                    && !DATED_AUTHOR
                                                            .matcher(clean(line.text))
                                                            .matches()
                                                    && !INITIALLED_NAME
                                                            .matcher(clean(line.text))
                                                            .matches()
                                                    && !paragraphLine(page, line, lines))
                            .sorted(Comparator.comparingDouble(Line::top))
                            .toList();
            if (candidates.isEmpty()) break;
            Line next = candidates.get(0);
            // Overlapping competing captions require review rather than an arbitrary join.
            if (candidates.size() > 1 && candidates.get(1).top < next.bottom) return "";
            group.add(next);
            prior = next;
        }
        return group.size() < 2
                ? ""
                : String.join(
                        " ", group.stream().map(line -> titlePunctuation(line.text)).toList());
    }

    /** A smaller OCR box inside a complete heading describes the same printed ink. */
    private static List<Line> withoutContainedTitleFragments(
            Page page, List<Line> lines, float limit) {
        List<Line> headings =
                lines.stream()
                        .filter(
                                line ->
                                        line.top < limit
                                                && line.height() >= page.width * .025f
                                                && titleText(line.text)
                                                && credit(line.text) == null)
                        .toList();
        Set<Line> envelopes = new LinkedHashSet<>();
        for (Line outer : headings) {
            List<Line> rows =
                    headings.stream()
                            .filter(
                                    line ->
                                            line != outer
                                                    && line.height() >= outer.height() * .25f
                                                    && line.height() <= outer.height() * .85f
                                                    && line.right - line.left
                                                            >= (outer.right - outer.left) * .25f
                                                    && line.left >= outer.left
                                                    && line.right <= outer.right
                                                    && line.top >= outer.top
                                                    && line.bottom <= outer.bottom)
                            .sorted(Comparator.comparingDouble(Line::top))
                            .toList();
            for (int index = 0; index < rows.size(); index++)
                for (int next = index + 1; next < rows.size(); next++) {
                    Line first = rows.get(index), second = rows.get(next);
                    float height = Math.max(first.height(), second.height());
                    if (second.top >= first.bottom - Math.min(first.height(), second.height()) * .1f
                            && second.top - first.bottom <= height * .8f
                            && Math.abs(first.center() - second.center()) <= height * .35f)
                        envelopes.add(outer);
                }
        }
        // A box spanning separately located title rows is a merged OCR envelope,
        // rather than proof that those complete rows are duplicate fragments.
        return lines.stream()
                .filter(
                        line ->
                                !envelopes.contains(line)
                                        && headings.stream()
                                                .noneMatch(
                                                        outer ->
                                                                outer != line
                                                                        && !envelopes.contains(
                                                                                outer)
                                                                        && outer.height()
                                                                                >= line.height()
                                                                                        * 1.25f
                                                                        && outer.right - outer.left
                                                                                >= (line.right
                                                                                                - line.left)
                                                                                        * 1.25f
                                                                        && line.left >= outer.left
                                                                        && line.right <= outer.right
                                                                        && line.top >= outer.top
                                                                        && line.bottom
                                                                                <= outer.bottom))
                .toList();
    }

    private static String joinTitle(Page page, Line heading, List<Line> lines, float limit) {
        List<Line> title = new ArrayList<>();
        title.add(heading);
        float rowLeft = heading.left, rowRight = heading.right;
        Set<Line> row = new LinkedHashSet<>();
        row.add(heading);
        // OCR can split a heading at punctuation. Grow only along its baseline;
        // a distant credit or a bullet on another line cannot become a title fragment.
        boolean grew;
        do {
            grew = false;
            for (Line line : lines)
                if (!row.contains(line)
                        && line.top < limit
                        && (titleText(line.text) || hyphenTitleFragment(line.text))
                        && !DATED_AUTHOR.matcher(clean(line.text)).matches()
                        && line.height() >= heading.height() * .4f
                        && line.height() <= heading.height() * 1.4f
                        && Math.abs((line.top + line.bottom - heading.top - heading.bottom) * .5f)
                                < heading.height() * .3f
                        && Math.max(line.left - rowRight, rowLeft - line.right)
                                < heading.height() * 1.2f) {
                    row.add(line);
                    rowLeft = Math.min(rowLeft, line.left);
                    rowRight = Math.max(rowRight, line.right);
                    grew = true;
                }
        } while (grew);
        float rowCenter = (rowLeft + rowRight) * .5f;
        for (Line l : lines) {
            boolean movement =
                    (MOVEMENT_SUBTITLE.matcher(clean(l.text)).matches()
                                    || CATALOGUE_MOVEMENT.matcher(clean(l.text)).matches())
                            && l.top >= heading.bottom - heading.height() * .2f
                            && l.top - heading.bottom < heading.height() * 1.2f
                            && Math.abs(l.center() - rowCenter)
                                    < Math.max(heading.height() * 1.6f, (rowRight - rowLeft) * .2f);
            String caption = clean(l.text);
            int captionWords = caption.split("\\h+").length;
            boolean versionCaption =
                    caption.matches(
                            "(?iu)^(?:(?:live|concert)\\h+performance|solo|easy|original|piano|violin|guitar|tavern|epic|wedding|concert)\\h+version(?:\\h+[0-9]+)?$");
            boolean sourceCaption =
                    captionWords >= 2
                            && captionWords <= 12
                            && (caption.matches(
                                            "(?iu).*\\b(?:soundtrack|opening\\h+[0-9]+|suite\\h+from\\h+the\\h+motion\\h+picture)$")
                                    || captionWords >= 3 && caption.matches("(?iu).+['’]s\\h+.+")
                                    || versionCaption)
                            && l.top >= heading.bottom - heading.height() * .3f
                            && l.top - heading.bottom < heading.height() * 1.2f
                            && l.height() >= heading.height() * (versionCaption ? .4f : .55f)
                            && l.height() <= heading.height()
                            && Math.abs(l.center() - rowCenter) < heading.height() * .8f
                            && l.right - l.left < (rowRight - rowLeft) * 2.5f;
            boolean parenthesized =
                    caption.startsWith("(")
                            && caption.endsWith(")")
                            && caption.length() < 80
                            && l.height() >= heading.height() * .4f
                            && l.top
                                    >= heading.bottom
                                            - Math.min(l.height(), heading.height()) * .15f
                            && (l.top + l.bottom) * .5f >= heading.bottom
                            && l.top - heading.bottom < heading.height() * .9f
                            && Math.abs(l.center() - rowCenter) < heading.height() * .8f
                            && l.right - l.left <= rowRight - rowLeft;
            boolean sameRow = row.contains(l);
            if (l == heading
                    || l.top >= limit
                    || (!sameRow && staffCharacterDirection(page, l, lines, limit))
                    || !(titleText(l.text) || sameRow && hyphenTitleFragment(l.text))
                    || DATED_AUTHOR.matcher(clean(l.text)).matches()
                    || (!movement
                            && !sourceCaption
                            && !parenthesized
                            && !sameRow
                            && l.height() < heading.height() * .85)
                    || l.height() < heading.height() * (movement ? .25 : .4)
                    || l.height() > heading.height() * 1.4) continue;
            boolean wrapped =
                    Math.abs(l.center() - heading.center())
                                    < Math.max(l.height(), heading.height()) * 1.6
                            && (Math.abs(l.top - heading.bottom) < heading.height() * .8
                                    || Math.abs(heading.top - l.bottom) < heading.height() * .8);
            if (sameRow
                    || movement
                    || sourceCaption
                    || parenthesized
                    || (wrapped && !INITIALLED_NAME.matcher(clean(l.text)).matches())) title.add(l);
        }
        title.sort(
                (a, b) ->
                        Math.abs(a.top - b.top) < Math.min(a.height(), b.height()) * .3
                                ? Float.compare(a.left, b.left)
                                : Float.compare(a.top, b.top));
        StringBuilder assembled = new StringBuilder();
        for (Line l : title) {
            String fragment =
                    MOVEMENT_SUBTITLE.matcher(clean(l.text)).matches()
                            ? clean(l.text).replaceFirst("(?iu)^[Il]st(?=\\h+movement)", "1st")
                            : titlePunctuation(l.text);
            if (assembled.length() > 0 && !(row.contains(l) && hyphenTitleFragment(fragment)))
                assembled.append(' ');
            assembled.append(fragment);
        }
        // OCR may insert a space after an otherwise unspaced letter/slash pair.
        // Preserve explicitly spaced separators and all attribution values.
        String joined = assembled.toString().replaceAll("(?<=\\p{L})/\\h+(?=\\p{L})", "/");
        Matcher enclosingQuotes = Pattern.compile("^[\"“]([^\"“”]+)[\"”]$").matcher(joined);
        return enclosingQuotes.matches() ? enclosingQuotes.group(1) : joined;
    }

    private static boolean hyphenTitleFragment(String value) {
        String text = clean(value);
        return text.startsWith("-") && text.length() > 1 && titleText(text.substring(1));
    }

    private static boolean continuesPrintedNameGroup(String value, Line prior, Line next) {
        // Explicit separators already continue a list; adding a second separator
        // would create an empty name and collapse the parsed contributor group.
        if (value.endsWith(",")
                || value.matches("(?iu).*\\b(?:and|&)\\h*$")
                || clean(next.text).matches("(?iu)^(?:and|&)\\h+.+")) return false;
        float height = Math.max(prior.height(), next.height()), gap = next.top - prior.bottom;
        return height >= 8
                && gap >= -Math.min(prior.height(), next.height()) * .25f
                && gap <= height * .45f
                && (next.top + next.bottom) * .5f >= prior.bottom
                && Math.min(prior.height(), next.height()) >= height * .75f
                && Math.abs(prior.center() - next.center()) <= height * .5f
                && creditNames(value).size() >= 2
                && creditNames(clean(next.text)).size() >= 2;
    }

    private static Line overlappingArtistHeading(Line label, List<Line> lines, Line heading) {
        // Skewed lettering in artwork can surround a small attribution label in
        // its axis-aligned OCR box. Do not skip it and attach a later caption.
        for (Line candidate : lines) {
            float overlap =
                    Math.min(label.right, candidate.right) - Math.max(label.left, candidate.left);
            if (candidate != label
                    && candidate != heading
                    && candidate.top < label.top
                    && candidate.bottom > label.bottom
                    && candidate.height() >= label.height() * 3
                    && overlap >= (label.right - label.left) * .8f
                    && nameText(candidate.text)
                    && !LEGAL.matcher(candidate.text).find()) return candidate;
        }
        return null;
    }

    /** Separate adjacent explicit role clauses without guessing unlabeled contributor roles. */
    /** An accompaniment creator is scoped to the same score's adjacent explicit arrangement. */
    private static Credit accompanimentArrangementCredit(Page page, Line line, List<Line> lines) {
        if (page.notationTop <= 0 || line.bottom >= page.notationTop || line.height() < 16)
            return null;
        Matcher match =
                Pattern.compile(
                                "(?iu)^(?:piano|keyboard|organ|guitar|violin|cello|flute|harp)\\h+accomp(?:animent)?\\.?[\\h.:]+by[\\h.:]+(?:by\\h+)?(.{3,140})$")
                        .matcher(clean(line.text));
        if (!match.matches()
                || !creditNameText(match.group(1))
                || credit(match.group(1)) != null
                || NOT_TITLE.matcher(match.group(1)).find()
                || LEGAL.matcher(line.text).find()) return null;
        for (Line prior : lines) {
            Credit explicit = credit(prior.text);
            float height = Math.max(prior.height(), line.height());
            float overlap = Math.min(prior.right, line.right) - Math.max(prior.left, line.left);
            if (explicit != null
                    && explicit.role == Role.ARRANGER
                    && creditNameText(explicit.value)
                    && prior.bottom <= line.top
                    && line.top - prior.bottom <= height * 1.8f
                    && Math.min(prior.height(), line.height()) >= height * .6f
                    && Math.abs(prior.center() - line.center()) <= height * 2
                    && overlap >= Math.min(prior.right - prior.left, line.right - line.left) * .7f)
                return new Credit(Role.ARRANGER, clean(match.group(1)), false);
        }
        return null;
    }

    private static List<Credit> distinctInlineRoles(String value) {
        String text = clean(value);
        Matcher labels =
                Pattern.compile(
                                "(?iu)(?<!\\p{L})(?:music\\h+by|lyrics\\h+by|words\\h+by|arranged\\h+by|performed\\h+by)\\b[\\h.:=–—-]*")
                        .matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (labels.find()) starts.add(labels.start());
        if (starts.size() < 2 || starts.size() > 4 || starts.get(0) != 0) return List.of();
        List<Credit> roles = new ArrayList<>();
        Set<Role> distinct = new LinkedHashSet<>();
        for (int i = 0; i < starts.size(); i++) {
            String part =
                    text.substring(
                                    starts.get(i),
                                    i + 1 < starts.size() ? starts.get(i + 1) : text.length())
                            .strip();
            part = part.replaceFirst("[;,]+$", "").strip();
            Credit role = credit(part);
            if (role == null
                    || !creditNameText(role.value)
                    || credit(role.value) != null
                    || NOT_TITLE.matcher(role.value).find()
                    || !distinct.add(role.role)) return List.of();
            roles.add(role);
        }
        return List.copyOf(roles);
    }

    private static Line nearestName(Line label, List<Line> lines, Line title, boolean preceding) {
        Line best = null;
        double distance = Double.MAX_VALUE;
        for (Line l : lines) {
            if (l == label
                    || l == title
                    || !nameText(l.text)
                    || credit(l.text) != null
                    || NOT_TITLE.matcher(l.text).find()
                    || LEGAL.matcher(l.text).find()) continue;
            float gap = preceding ? label.top - l.bottom : l.top - label.bottom;
            float commonWidth = Math.min(label.right, l.right) - Math.max(label.left, l.left);
            boolean tiltedBelow =
                    !preceding
                            && gap < 0
                            && gap >= -Math.min(label.height(), l.height()) * .85f
                            && l.bottom > label.bottom
                            && (l.top + l.bottom) * .5f > (label.top + label.bottom) * .5f
                            && Math.min(label.height(), l.height())
                                    >= Math.max(label.height(), l.height()) * .55f
                            && commonWidth
                                    >= Math.min(label.right - label.left, l.right - l.left) * .8f
                            && Math.abs(l.center() - label.center())
                                    <= Math.max(label.height(), l.height()) * .75f;
            if (gap < -Math.min(label.height(), l.height()) * .35 && !tiltedBelow
                    || gap > Math.max(label.height(), l.height()) * 3.8) continue;
            // Left aligned and centered labels both occur. Either consistent anchor is enough.
            float alignment =
                    Math.min(
                            Math.min(
                                    Math.abs(l.left - label.left),
                                    Math.abs(l.center() - label.center())),
                            Math.abs(l.right - label.right));
            if (alignment > Math.max(label.height(), l.height()) * 4) continue;
            double score = Math.max(0, gap) + alignment * .7;
            if (score < distance) {
                best = l;
                distance = score;
            }
        }
        return best;
    }

    // These prefixes request a closer OCR look; they never establish a role.
    private static String suspectedArrangementName(String value) {
        Matcher m = Pattern.compile("(?iu)^ar[cft][.,:]?\\h+(.+)$").matcher(clean(value));
        return m.matches() && creditNameText(m.group(1)) ? m.group(1) : "";
    }

    private static Credit combinedCompositionArrangement(String value) {
        Matcher combined = COMBINED_COMPOSITION_ARRANGEMENT.matcher(clean(value));
        if (!combined.matches()) return null;
        String roles = combined.group(1).toLowerCase(Locale.ROOT);
        if (!roles.contains("arranged")
                || !(roles.contains("composed") || roles.contains("written"))) return null;
        return new Credit(Role.COMPOSER, combined.group(2), false);
    }

    private static boolean collectionDescription(String value) {
        return clean(value)
                        .matches(
                                "(?iu)^(?:(?:all|these|the)\\h+)?[0-9]+\\h+(?:original\\h+)?(?:songs|tracks|pieces|works)\\b.*")
                || clean(value)
                        .matches(
                                "(?iu)^(?:my|our|your|this|the)\\h+(?:album|book|collection)\\b.*");
    }

    private static boolean countedCollectionList(String value) {
        return clean(value)
                        .matches(
                                "(?iu)^.{0,40}\\b[0-9]+\\h+(?:\\p{L}+\\h+){0,3}"
                                        + "(?:songs|tracks|pieces|works|hits)\\b.*\\b(?:including|includes|featuring)\\b.*$")
                || clean(value)
                        .matches(
                                "(?iu)^of\\h+[0-9]+\\h+(?:[\\p{L}-]+\\h+){1,6}(?:including|includes|featuring)\\b.*$");
    }

    private static Credit credit(String value) {
        String text = clean(value);
        Credit recordingArrangement = recordingArrangementCredit(text);
        if (recordingArrangement != null) return recordingArrangement;
        Credit scoped = quotedScopedArrangementCredit(text);
        if (scoped != null) return scoped;
        // A quoted work describes the scope of this attribution, not a person.
        // Incomplete labels must wait for their located continuation and byline.
        if (scopedArrangementStart(text)) return null;
        Matcher collectionComposer =
                Pattern.compile(
                                "(?iu)^(?:all\\h+)?(?:songs|music|works|pieces|tracks)\\h+(?:were\\h+)?(?:written|composed)(?:\\h+(?:and|&)\\h+produced)?\\h+by[\\h.:]+(.+)$")
                        .matcher(text);
        if (collectionComposer.matches() && creditNameText(collectionComposer.group(1)))
            return new Credit(Role.COMPOSER, collectionComposer.group(1), false);
        Matcher basedArrangement =
                Pattern.compile(
                                "(?iu)^based\\h+on\\h+(?:the\\h+)?(?:arr\\.?|arrangement)\\h+by[\\h.:]+(.+?)(?:\\h+of(?:\\h+.*)?)?$")
                        .matcher(text);
        if (basedArrangement.matches() && creditNameText(basedArrangement.group(1)))
            return new Credit(Role.ARRANGER, basedArrangement.group(1), false);
        Credit combined = combinedCompositionArrangement(text);
        if (combined != null) return combined;
        if (text.startsWith("(") || text.startsWith("["))
            text = text.substring(1).replaceFirst("[\\])]$", "");
        if (text.endsWith(")") && !text.contains("(")) text = text.substring(0, text.length() - 1);
        Matcher embedded =
                Pattern.compile("(?iu)^.+?[,;]\\h*(arranged\\h+by\\h+.+)$").matcher(text);
        if (embedded.matches()) text = embedded.group(1);
        Credit insertedTranscription = insertedTranscriptionLetterCredit(text);
        if (insertedTranscription != null) return insertedTranscription;
        Matcher m = CREDIT.matcher(text);
        if (!m.matches()) {
            Matcher suffix =
                    Pattern.compile("(?iu)^(\\p{IsHan}{2,20})\\h*(?:arranged|arrangement)$")
                            .matcher(text);
            return suffix.matches() ? new Credit(Role.ARRANGER, suffix.group(1), false) : null;
        }
        String label = m.group(1).toLowerCase(Locale.ROOT);
        // Bare "by" can be the surviving end of a converter or publisher label.
        // Malformed values remain review evidence, rather than becoming composers.
        if (label.equals("by") && m.group(2).matches(".*[<>:|].*")) return null;
        Role role =
                label.startsWith("arr")
                                || label.startsWith("ar.")
                                || label.startsWith("transc")
                                || label.contains("arrangement")
                                || label.contains("adaptation")
                        ? Role.ARRANGER
                        : label.equals("words by")
                                        || label.startsWith("lyrics")
                                        || label.startsWith("additional")
                                ? Role.LYRICS
                                : label.contains("performed")
                                                || label.contains("recorded")
                                                || label.contains("played")
                                                || label.startsWith("feat")
                                                || label.startsWith("artist")
                                        ? Role.ARTIST
                                        : Role.COMPOSER;
        String attributedValue = m.group(2);
        if (role == Role.ARRANGER && label.matches("(?iu)arr\\.?|ar\\.|arrangements?"))
            attributedValue = attributedValue.replaceFirst("(?iu)^by[\\h.:]+", "");
        return new Credit(
                role, attributedValue, label.equals("arrangement") || label.equals("arrangements"));
    }

    /** Exact attribution grammar can survive one extra OCR letter in its printed role verb. */
    private static Credit insertedTranscriptionLetterCredit(String value) {
        Matcher match =
                Pattern.compile("(?iu)^(transcr[\\p{L}]{5})\\h+by\\h+(.+)$").matcher(clean(value));
        if (!match.matches()
                || editDistance(match.group(1).toLowerCase(Locale.ROOT), "transcribed") != 1
                || !creditNameText(match.group(2))) return null;
        return new Credit(Role.ARRANGER, match.group(2), false);
    }

    private static Credit recordingArrangementCredit(String value) {
        String text = clean(value);
        if ((text.startsWith("(\"") || text.startsWith("(“")) && text.endsWith(")"))
            text = clean(text.substring(1, text.length() - 1));
        Matcher work = Pattern.compile("^(?:\"[^\"]+\"|“[^”]+”)\\h+(.+)$").matcher(text);
        if (work.matches()) text = work.group(1);
        Matcher match = RECORDING_WORK_ROLES.matcher(text);
        return match.matches()
                        && match.group(1).toLowerCase(Locale.ROOT).contains("arranged")
                        && creditNameText(match.group(2))
                ? new Credit(Role.ARRANGER, clean(match.group(2)), false)
                : null;
    }

    private static boolean recordingProductionContext(String value) {
        Matcher match = RECORDING_WORK_ROLES.matcher(clean(value));
        if (!match.matches() || !creditNameText(match.group(2))) return false;
        String roles = match.group(1).toLowerCase(Locale.ROOT);
        return roles.contains("recorded") && roles.contains("produced");
    }

    private record ScopedArrangement(Credit credit, List<Line> lines) {}

    private static boolean scopedArrangementStart(String value) {
        return clean(value).matches("(?iu)^(?:audio\\h+)?arrangements?\\h+for\\h+[\"“].*$");
    }

    private static Credit quotedScopedArrangementCredit(String value) {
        Matcher match =
                Pattern.compile(
                                "(?iu)^(?:audio\\h+)?arrangements?\\h+for\\h+"
                                        + "(?:\"[^\"]+\"|“[^”]+”)(?:\\h*(?:,|and|&)\\h*(?:\"[^\"]+\"|“[^”]+”))*"
                                        + "\\h+by[\\h.:]+(.+)$")
                        .matcher(clean(value));
        return match.matches()
                        && creditNameText(match.group(1))
                        && !countedCollectionList(match.group(1))
                ? new Credit(Role.ARRANGER, clean(match.group(1)), false)
                : null;
    }

    private static ScopedArrangement resolveScopedArrangement(
            Page page, Line start, List<Line> lines) {
        if (!scopedArrangementStart(start.text) || LEGAL.matcher(start.text).find()) return null;
        boolean smallAudio =
                !page.creditsOnly
                        && page.width > 0
                        && page.height > 0
                        && start.top >= 0
                        && start.bottom <= page.height
                        && start.height() < page.width * .025f
                        && clean(start.text).matches("(?iu)^audio\\h+arrangements?\\h+for\\h+.*$");
        if (!inCreditHeader(page, start) && !smallAudio) return null;
        String text = clean(start.text);
        List<Line> group = new ArrayList<>();
        group.add(start);
        Credit role = quotedScopedArrangementCredit(text);
        for (int part = 0; role == null && part < 3; part++) {
            Line prior = group.get(group.size() - 1);
            Line next =
                    lines.stream()
                            .filter(
                                    line ->
                                            !group.contains(line)
                                                    && line.top >= prior.bottom
                                                    && line.top - prior.bottom
                                                            < Math.max(
                                                                            prior.height(),
                                                                            line.height())
                                                                    * 1.5f
                                                    && line.height() > prior.height() * .6f
                                                    && line.height() < prior.height() * 1.8f
                                                    && !LEGAL.matcher(line.text).find()
                                                    && (Math.abs(line.center() - prior.center())
                                                                    < Math.max(
                                                                            prior.height(),
                                                                            line.height())
                                                            || Math.abs(line.left - prior.left)
                                                                    < Math.max(
                                                                            prior.height(),
                                                                            line.height()))
                                                    && Math.min(line.right, prior.right)
                                                                    - Math.max(
                                                                            line.left, prior.left)
                                                            > Math.min(
                                                                            line.right - line.left,
                                                                            prior.right
                                                                                    - prior.left)
                                                                    * .65f)
                            .min(Comparator.comparingDouble(Line::top))
                            .orElse(null);
            if (next == null) break;
            text += " " + clean(next.text);
            if (text.length() > 360) break;
            group.add(next);
            role = quotedScopedArrangementCredit(text);
        }
        return role == null ? null : new ScopedArrangement(role, List.copyOf(group));
    }

    private static boolean titleText(String text) {
        String start =
                titlePunctuation(text).replaceFirst("^[\\[(\"“]+", "").replaceFirst("[\\])]+$", "");
        return nameText(text)
                && credit(text) == null
                && !start.isEmpty()
                && Character.isLetterOrDigit(start.codePointAt(0))
                && !NOT_TITLE.matcher(start).find()
                && !start.matches("(?iu)^soli\\h*$")
                && !start.matches(
                        "(?iu)^(?:p{1,3}|mp|mf|f{1,3}|sfz)\\h+(?:with|con|cresc\\.?|dim\\.?|rit\\.?)\\b.*")
                && !repeatedDynamics(start)
                && !dynamicSymbolDebris(start)
                && !FEATURED.matcher(clean(text)).matches()
                && !LEGAL.matcher(text).find();
    }

    private static String titlePunctuation(String text) {
        String value = clean(text);
        // A decorative mark can be read as a closing bracket before the heading.
        // Remove it only when separated from the letters and no opening partner
        // exists in the same line. Parenthesized subtitles keep their punctuation.
        if (value.matches("^[)\\]}]\\h+\\p{L}.*")) {
            char opener = value.charAt(0) == ')' ? '(' : value.charAt(0) == ']' ? '[' : '{';
            if (value.indexOf(opener) < 0) value = value.substring(1).stripLeading();
        }
        // OCR occasionally duplicates the final double quote. Single quotes and
        // apostrophes in titles or names are never changed here.
        return value.replaceFirst("([\"”])\\1+$", "$1");
    }

    private static boolean dynamicSymbolDebris(String text) {
        return text.matches("(?iu)^(?:p{1,3}|mp|mf|f{1,3}|sfz)(?![\\p{L}\\p{N}]).*")
                && text.codePoints().filter(Character::isLetter).count() * 2 < text.length();
    }

    private static boolean repeatedDynamics(String text) {
        Matcher dynamics =
                Pattern.compile(
                                "(?iu)(?<![\\p{L}\\p{N}])(?:p{1,3}|mp|mf|f{1,3}|sfz)(?![\\p{L}\\p{N}])")
                        .matcher(text);
        return dynamics.find() && dynamics.find();
    }

    private static boolean nameText(String value) {
        String v = clean(value);
        return v.length() >= 3
                && v.length() < 220
                && v.codePoints().filter(Character::isLetter).count() >= 3
                && !v.matches(".*[=♩♪].*")
                && !v.matches("(?iu)^(?:p|pp|ppp|mp|mf|f|ff|fff|sfz|cresc\\.?|rit\\.?|dim\\.?)$");
    }

    private static boolean creditNameText(String value) {
        String text = clean(value);
        if (text.startsWith(",") || text.startsWith(";")) return false;
        return nameText(text) || text.matches("\\p{IsHan}{2,20}");
    }

    private static boolean compoundInstrumentationCaption(String value) {
        String text = clean(value);
        if (!text.matches("(?iu)^for\\h+.*")) return false;
        String[] parts =
                text.replaceFirst("(?iu)^for\\h+", "").split("(?iu)\\h+(?:and|with|&)\\h+");
        return parts.length > 1
                && List.of(parts).stream().allMatch(part -> NOT_TITLE.matcher(part).matches());
    }

    private static String preferSpelling(String hint, String printed) {
        return clean(hint).equalsIgnoreCase(clean(printed)) ? clean(hint) : printed;
    }

    /** A dated credited name cannot absorb an independently read distant byline. */
    private static String separateDatedAttributionColumn(
            Page page, Line original, String value, List<Line> lines) {
        if (page.photographicCover
                || page.notationTop <= 0
                || original.bottom > page.notationTop
                || original.left >= page.width * .4f
                || original.right - original.left < page.width * .65f) return value;
        int end = value.indexOf(')');
        if (end < 0 || end + 1 >= value.length()) return value;
        String dated = value.substring(0, end + 1).strip(),
                suffix = value.substring(end + 1).strip();
        Matcher person = DATED_AUTHOR.matcher(dated);
        if (!person.matches() || !creditNameText(person.group(1)) || !creditNameText(suffix))
            return value;
        for (Line separate : lines) {
            float height = Math.max(original.height(), separate.height());
            float overlap =
                    Math.min(original.bottom, separate.bottom)
                            - Math.max(original.top, separate.top);
            if (separate == original
                    || separate.left < page.width * .55f
                    || separate.left - original.left < page.width * .4f
                    || Math.abs(separate.right - original.right) > height * .5f
                    || overlap < Math.min(original.height(), separate.height()) * .65f
                    || separate.height() < original.height() * .65f
                    || separate.height() > original.height() * 1.5f
                    || credit(separate.text) != null
                    || !clean(separate.text).equalsIgnoreCase(suffix)) continue;
            return dated;
        }
        return value;
    }

    private static String canonicalName(String value, Set<String> names) {
        Matcher dates = DATED_AUTHOR.matcher(clean(value));
        if (dates.matches()) value = clean(dates.group(1));
        String key = fold(value), match = null;
        int allowance = key.length() < 8 ? 0 : Math.min(2, key.length() / 9);
        for (String known : names) {
            String candidate = fold(known);
            if (Math.abs(key.length() - candidate.length()) > allowance
                    || editDistance(key, candidate) > allowance) continue;
            if (match != null && !same(match, known)) return value;
            match = known;
        }
        return match == null ? value : match;
    }

    private static boolean inCreditHeader(Page page, Line line) {
        return inDisplayRoleCredit(page, line)
                || line.top < (page.notationTop > 0 ? page.notationTop : page.height * .85f)
                || line.top >= page.height * .8f
                        && line.bottom <= page.height
                        && isExplicitFooterCredit(line.text)
                || frontAudioArrangementCredit(page, line)
                || frontInstrumentalArrangementCredit(page, line)
                || recordingPanelLine(page, line) && recordingArrangementCredit(line.text) != null;
    }

    // Album artwork may look like staves. An explicit small recording credit
    // on either opening page remains attribution evidence below that boundary.
    private static boolean frontAudioArrangementCredit(Page page, Line line) {
        return frontNamedArrangementCredit(page, line, "audio");
    }

    private static boolean frontInstrumentalArrangementCredit(Page page, Line line) {
        return frontNamedArrangementCredit(page, line, "instrumental");
    }

    private static boolean frontNamedArrangementCredit(Page page, Line line, String qualifier) {
        Credit role = credit(line.text);
        return !page.creditsOnly
                && page.width > 0
                && page.height > 0
                && line.top >= 0
                && line.bottom <= page.height
                && line.height() < page.width * .025f
                && role != null
                && role.role == Role.ARRANGER
                && creditNameText(role.value)
                && !countedCollectionList(role.value)
                && clean(line.text).matches("(?iu)^" + qualifier + "\\h+arrangements?\\h+by\\h+.+")
                && !LEGAL.matcher(line.text).find();
    }

    private static boolean recordingPanelLine(Page page, Line line) {
        return !page.creditsOnly
                && page.width > 0
                && page.height > 0
                && line.top >= page.height * .5f
                && line.bottom <= page.height * .9f
                && line.height() < page.width * .025f
                && !LEGAL.matcher(line.text).find();
    }

    private static String recordingPerformerName(String text) {
        Matcher qualified = RECORDING_PERSON.matcher(clean(text));
        return qualified.matches()
                        && creditNameText(qualified.group(1))
                        && credit(qualified.group(1)) == null
                ? clean(qualified.group(1))
                : "";
    }

    private static String recordingPerformer(Page page, Line line, List<Line> lines) {
        if (!recordingPanelLine(page, line)) return "";
        String name = recordingPerformerName(line.text);
        if (name.isEmpty()) return "";
        if (recordingContextAbove(page, line, lines)) return name;
        Line cursor = line;
        // Follow only consecutive named instrument credits in the same recording
        // column. The production anchor supplies context, never a performer name.
        for (int part = 0; part < 8; part++) {
            Line prior = cursor;
            Line next =
                    lines.stream()
                            .filter(
                                    candidate ->
                                            candidate != prior
                                                    && candidate.top >= prior.bottom
                                                    && candidate.top - prior.bottom
                                                            < Math.max(
                                                                            prior.height(),
                                                                            candidate.height())
                                                                    * 2
                                                    && candidate.height() < prior.height() * 1.8f
                                                    && candidate.height() > prior.height() * .6f
                                                    && (Math.abs(candidate.left - prior.left)
                                                                    < Math.max(
                                                                            prior.height(),
                                                                            candidate.height())
                                                            || Math.abs(
                                                                            candidate.center()
                                                                                    - prior
                                                                                            .center())
                                                                    < Math.max(
                                                                            prior.height(),
                                                                            candidate.height())))
                            .min(Comparator.comparingDouble(Line::top))
                            .orElse(null);
            if (next == null) break;
            if (frontAudioArrangementCredit(page, next)
                    || recordingPanelLine(page, next)
                            && (recordingProductionContext(next.text)
                                    || recordingArrangementCredit(next.text) != null)) return name;
            if (!recordingPanelLine(page, next) || recordingPerformerName(next.text).isEmpty())
                break;
            cursor = next;
        }
        return "";
    }

    private static boolean recordingColumnAligned(Line first, Line second) {
        float height = Math.max(first.height(), second.height());
        return Math.min(
                        Math.min(
                                Math.abs(first.left - second.left),
                                Math.abs(first.right - second.right)),
                        Math.abs(first.center() - second.center()))
                < height * 1.2f;
    }

    private static boolean recordingStudioAnchor(String text) {
        return clean(text)
                .matches(
                        "(?iu)^(?:recorded\\h+(?:and|&)\\h+produced|produced\\h+(?:and|&)\\h+recorded)"
                                + "\\h+at(?:\\h+.+)?$");
    }

    private static boolean recordingVenueLine(String value) {
        String text = clean(value);
        return creditNameText(text)
                && credit(text) == null
                && !LEGAL.matcher(text).find()
                && text.matches("(?iu).*\\b(?:studios?|music|sound|audio|recordings)\\b.*")
                && !text.matches("(?iu).*\\b(?:by|supplied|access|included|visit|contact)\\b.*");
    }

    private static Line previousRecordingPanelLine(Page page, Line line, List<Line> lines) {
        return lines.stream()
                .filter(
                        prior ->
                                prior != line
                                        && prior.bottom <= line.top
                                        && line.top - prior.bottom
                                                < Math.max(line.height(), prior.height()) * 2
                                        && prior.height() > line.height() * .6f
                                        && prior.height() < line.height() * 1.8f
                                        && recordingColumnAligned(line, prior))
                .max(Comparator.comparingDouble(Line::bottom))
                .orElse(null);
    }

    private static boolean recordingContextAbove(Page page, Line line, List<Line> lines) {
        Line cursor = line;
        for (int part = 0; part < 8; part++) {
            Line prior = previousRecordingPanelLine(page, cursor, lines);
            if (prior == null || !recordingPanelLine(page, prior)) return false;
            if (recordingStudioAnchor(prior.text)
                    || recordingProductionContext(prior.text)
                    || recordingArrangementCredit(prior.text) != null
                    || frontAudioArrangementCredit(page, prior)) return true;
            if (!recordingPerformerName(prior.text).isEmpty()) {
                cursor = prior;
                continue;
            }
            if (recordingVenueLine(prior.text)) {
                Line anchor = previousRecordingPanelLine(page, prior, lines);
                return anchor != null
                        && recordingPanelLine(page, anchor)
                        && recordingStudioAnchor(anchor.text);
            }
            return false;
        }
        return false;
    }

    private static List<String> quotedWorkComposerNames(String value) {
        // Each quoted work introduces a new attribution clause. Its title and
        // anonymous traditional origin are not part of the preceding person's name.
        Matcher works = Pattern.compile("[\"“][^\"”]+[\"”]\\h*([^\"“]*)").matcher(value);
        if (!works.find()) return creditNames(value);
        List<String> names = new ArrayList<>();
        String first = attributionEnd(value.substring(0, works.start()));
        if (nameText(first)) names.addAll(creditNames(first));
        do {
            Credit next = credit(attributionEnd(works.group(1)));
            if (next != null && next.role == Role.COMPOSER && nameText(next.value))
                names.addAll(creditNames(clean(next.value)));
        } while (works.find());
        return List.copyOf(names);
    }

    private static String attributionEnd(String value) {
        return clean(value)
                .replaceFirst("[\\h,;]+$", "")
                .replaceFirst("(?iu)\\h+and$", "")
                .replaceFirst("[\\h,;]+$", "")
                .strip();
    }

    /** A dropped comma may appear as a period between full names in an existing list. */
    /** Repeated crops of the same boundary cannot justify deleting its existing letter. */
    private static boolean deletesContinuationBoundaryLetter(String original, String candidate) {
        if (original.isEmpty()) return false;
        int first = original.codePointAt(0), last = original.codePointBefore(original.length());
        return (Character.isAlphabetic(first)
                        && original.substring(Character.charCount(first)).equals(candidate))
                || (Character.isAlphabetic(last)
                        && original.substring(0, original.length() - Character.charCount(last))
                                .equals(candidate));
    }

    private static boolean sameConsensusContributorCount(String before, String after) {
        List<String> original = creditNames(before), candidate = creditNames(after);
        if (original.size() == candidate.size()) {
            if (original.size() == 1 && (before.contains(",") || after.contains(",")))
                return before.replaceAll("[^,;/&]", "").equals(after.replaceAll("[^,;/&]", ""));
            return true;
        }
        if (original.size() < 2 || candidate.size() != original.size() + 1) return false;
        int count = 0, expanded = 0;
        for (String part : original) {
            String[] pieces = part.split("\\.\\h+", -1);
            if (pieces.length == 1) {
                count++;
                continue;
            }
            if (pieces.length != 2 || ++expanded > 1) return false;
            for (String piece : pieces) {
                String[] words = clean(piece).split("\\h+");
                if (words.length < 2
                        || words.length > 4
                        || words[words.length - 1].length() < 3
                        || !Character.isUpperCase(words[0].codePointAt(0))) return false;
                for (String word : words) if (!word.matches("[\\p{L}][\\p{L}'’\\-]*")) return false;
            }
            count += 2;
        }
        return expanded == 1 && count == candidate.size();
    }

    private static List<String> creditNames(String value) {
        String[] parts = value.split("(?iu)\\h*(?:[,;/]\\h*(?:and\\b|&)?|\\band\\b|&)\\h*");
        if (parts.length < 2) return List.of(value);
        List<String> names = new ArrayList<>();
        for (String part : parts) {
            String name = clean(part);
            // Full personal names make a list unambiguous. Preserve band punctuation
            // when short sides could be one stage name, such as Earth, Wind & Fire.
            if (!nameText(name) || name.split("\\h+").length < 2) return List.of(value);
            names.add(name);
        }
        return List.copyOf(names);
    }

    private static void splitCorroboratedContributors(Set<String> destination) {
        List<String> original = new ArrayList<>(destination);
        destination.clear();
        for (String value : original) {
            String[] parts = value.split("(?iu)\\h+and\\h+", -1);
            if (parts.length != 2
                    || Arrays.stream(parts)
                            .anyMatch(p -> !nameText(p) || p.matches(".*[,;/&].*"))) {
                addNames(destination, List.of(value));
                continue;
            }
            boolean corroborated =
                    Arrays.stream(parts)
                            .anyMatch(
                                    part ->
                                            original.stream()
                                                    .anyMatch(
                                                            other ->
                                                                    !other.equals(value)
                                                                            && same(part, other)
                                                                            && clean(other)
                                                                                            .split(
                                                                                                    "\\h+")
                                                                                            .length
                                                                                    >= 2));
            addNames(
                    destination,
                    corroborated ? List.of(clean(parts[0]), clean(parts[1])) : List.of(value));
        }
    }

    private static void addNames(Set<String> destination, List<String> names) {
        for (String name : names) {
            String existing =
                    destination.stream()
                            .filter(value -> same(value, name))
                            .findFirst()
                            .orElse(null);
            if (existing == null) {
                destination.add(name);
                continue;
            }
            // Later printed credits may retain an internal apostrophe that an
            // earlier OCR reading omitted. Preserve letters, spacing and order.
            String apostrophes = "(?<=\\p{L})['’](?=\\p{L})";
            String unpunctuated = clean(name).replaceAll(apostrophes, "");
            if (!unpunctuated.equals(clean(name))
                    && unpunctuated.equalsIgnoreCase(clean(existing))) {
                List<String> ordered = new ArrayList<>(destination);
                int index = ordered.indexOf(existing);
                ordered.set(index, name);
                destination.clear();
                destination.addAll(ordered);
            }
        }
    }

    private static int editDistance(String a, String b) {
        int[] row = new int[b.length() + 1];
        for (int i = 0; i <= b.length(); i++) row[i] = i;
        for (int i = 1; i <= a.length(); i++) {
            int diagonal = row[0];
            row[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int previous = row[j];
                row[j] =
                        Math.min(
                                Math.min(row[j] + 1, row[j - 1] + 1),
                                diagonal + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                diagonal = previous;
            }
        }
        return row[b.length()];
    }

    private static boolean same(String a, String b) {
        return !fold(a).isEmpty() && fold(a).equals(fold(b));
    }

    private static String fold(String value) {
        return FOLD_NON_NAMES
                .matcher(
                        FOLD_MARKS
                                .matcher(Normalizer.normalize(clean(value), Normalizer.Form.NFKD))
                                .replaceAll("")
                                .toLowerCase(Locale.ROOT))
                .replaceAll("");
    }

    private static String clean(String value) {
        return value == null ? "" : CLEAN_HORIZONTAL_SPACE.matcher(value.strip()).replaceAll(" ");
    }
}
