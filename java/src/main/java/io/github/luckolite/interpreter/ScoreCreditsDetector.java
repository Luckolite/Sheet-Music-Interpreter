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

    /** Printed payload beside a pixel-verified service mark; the OCR line stays unchanged. */
    public record ReviewCredit(Line line, String value) {}

    public record Page(
            float width,
            float height,
            float notationTop,
            List<Line> lines,
            boolean creditsOnly,
            boolean photographicCover,
            List<ReviewCredit> reviewCredits) {
        public Page(
                float width,
                float height,
                float notationTop,
                List<Line> lines,
                boolean creditsOnly,
                boolean photographicCover) {
            this(width, height, notationTop, lines, creditsOnly, photographicCover, List.of());
        }

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
            reviewCredits = reviewCredits == null ? List.of() : List.copyOf(reviewCredits);
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

    private static final Pattern MOTION_PICTURE_SOURCE_CAPTION =
            Pattern.compile("(?i)^suite\\h+([a-z]{4})\\h+the\\h+motion\\h+picture$");

    private static String motionPictureConnector(String value) {
        Matcher match = MOTION_PICTURE_SOURCE_CAPTION.matcher(clean(value));
        return match.matches() ? match.group(1).toLowerCase(Locale.ROOT) : "";
    }

    /** Caption boxes may overlap a heading slightly while their baselines remain separate. */
    private static boolean nearbySourceCaptionGeometry(Page page, Line heading, Line caption) {
        return !page.creditsOnly
                && !page.photographicCover
                && Float.isFinite(page.notationTop)
                && page.notationTop > 0
                && Float.isFinite(page.width)
                && Float.isFinite(page.height)
                && page.width > 0
                && page.height > 0
                && validCoverLine(page, heading)
                && validCoverLine(page, caption)
                && heading.top < page.height * .2f
                && heading.height() >= page.width * .025f
                && caption != heading
                && caption.bottom < page.notationTop
                && caption.top >= heading.bottom - heading.height() * .35f
                && caption.top - heading.bottom < heading.height() * 1.2f
                && (caption.top + caption.bottom) * .5f >= heading.bottom - heading.height() * .05f
                && caption.height() >= heading.height() * .55f
                && caption.height() <= heading.height()
                && Math.abs(caption.center() - heading.center()) < heading.height() * .8f
                && caption.right - caption.left < (heading.right - heading.left) * 2.5f;
    }

    private static List<Line> nearbyMotionPictureSourceCaptions(Page page, Line heading) {
        return page.lines.stream()
                .filter(
                        line ->
                                nearbySourceCaptionGeometry(page, heading, line)
                                        && !motionPictureConnector(line.text).isEmpty())
                .toList();
    }

    private static boolean sameBaselineHeadingFragment(Line heading, Line other) {
        return (other.right <= heading.left || other.left >= heading.right)
                && Math.max(other.left - heading.right, heading.left - other.right)
                        < heading.height() * 1.2f
                && other.height() >= heading.height() * .4f
                && other.height() <= heading.height() * 1.4f
                && Math.abs((other.top + other.bottom - heading.top - heading.bottom) * .5f)
                        < heading.height() * .3f;
    }

    /** A caption cannot choose between separately located plausible heading owners. */
    private static boolean competingSourceCaptionHeading(Page page, Line heading, Line caption) {
        return page.lines.stream()
                .anyMatch(
                        other ->
                                other != heading
                                        && other != caption
                                        && titleText(other.text)
                                        && credit(other.text) == null
                                        && !LEGAL.matcher(other.text).find()
                                        && !DATED_AUTHOR.matcher(clean(other.text)).matches()
                                        && motionPictureConnector(other.text).isEmpty()
                                        && !sameBaselineHeadingFragment(heading, other)
                                        && nearbySourceCaptionGeometry(page, other, caption));
    }

    /** A near source-caption connector requests original pixels, never a guessed title.
     * Callers must restrict nomination and refinement to the first page.
     */
    public static List<Line> nearbySourceCaptionCandidates(Page page) {
        if (page.creditsOnly || page.photographicCover || page.notationTop <= 0) return List.of();
        Line heading = chooseTitle("", page, page.lines, page.notationTop);
        if (heading == null) return List.of();
        List<Line> captions = nearbyMotionPictureSourceCaptions(page, heading);
        if (captions.size() != 1) return List.of();
        Line caption = captions.get(0);
        String connector = motionPictureConnector(caption.text);
        return editDistance(connector, "from") == 1
                        && !competingSourceCaptionHeading(page, heading, caption)
                ? List.of(caption)
                : List.of();
    }

    /** Exactly three strong original readings must establish the complete printed caption. */
    public static List<Line> mergeNearbySourceCaptionConsensus(
            Page page, Line original, List<String> readings, List<Float> confidence) {
        if (!nearbySourceCaptionCandidates(page).contains(original)
                || readings == null
                || readings.size() != 3
                || confidence == null
                || confidence.size() != 3
                || confidence.stream()
                        .anyMatch(v -> v == null || !Float.isFinite(v) || v < .95f || v > 1)
                || readings.stream()
                        .anyMatch(v -> v == null || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0))
            return page.lines;
        String observed =
                Normalizer.normalize(readings.get(0), Normalizer.Form.NFC)
                        .replaceAll("\\h+", " ")
                        .strip();
        if (!motionPictureConnector(observed).equals("from")
                || readings.stream()
                        .anyMatch(
                                v ->
                                        !Normalizer.normalize(v, Normalizer.Form.NFC)
                                                .replaceAll("\\h+", " ")
                                                .strip()
                                                .equals(observed))
                || !CreditOcrIdentifierGuard.preservesIdentifiers(original.text, observed))
            return page.lines;
        List<Line> merged = new ArrayList<>(page.lines);
        merged.set(
                merged.indexOf(original),
                new Line(observed, original.left, original.top, original.right, original.bottom));
        return List.copyOf(merged);
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

    private static final Pattern COVER_FOOTER_CAPTION =
            Pattern.compile(
                    "(?iu)\\b(?:copyright|rights|reserved|publish(?:ed|er|ers|ing)?|press(?:es)?|websites?|printed|edition|editions|version|versions|volume|volumes|book|books|series|collection|collections|piano|violin|guitar|vocal|cello|flute|orchestra|score|scores|arranged|composed|performed|transcribed|music|lyrics|words|by|for|from|www|https?)\\b");

    /** Located literal text below a simple opening cover; it establishes no contributor role.
     * Callers must restrict this route to the first page. Photographic classification is unchanged.
     */
    public static List<Line> coverFooterReviewCandidates(Page page) {
        if (page.creditsOnly
                || page.photographicCover
                || !Float.isFinite(page.notationTop)
                || page.notationTop > 0
                || !Float.isFinite(page.width)
                || !Float.isFinite(page.height)
                || page.width <= 0
                || page.height <= 0
                || page.lines.stream().anyMatch(l -> !validCoverLine(page, l))) return List.of();
        List<Line> headings =
                page.lines.stream()
                        .filter(
                                l ->
                                        l.top < page.height * .15f
                                                && l.bottom < page.height * .25f
                                                && l.height() >= page.width * .04f
                                                && l.right - l.left >= page.width * .3f
                                                && l.right - l.left <= page.width * .95f
                                                && Math.abs(l.center() - page.width * .5f)
                                                        < page.width * .12f
                                                && titleText(l.text)
                                                && credit(l.text) == null
                                                && !LEGAL.matcher(l.text).find())
                        .toList();
        if (headings.size() != 1) return List.of();
        Line heading = headings.get(0);
        List<Line> footers =
                page.lines.stream()
                        .filter(
                                l ->
                                        !l.equals(heading)
                                                && l.top >= page.height * .85f
                                                && l.bottom <= page.height * .98f
                                                && l.height() >= page.width * .025f
                                                && l.height() <= heading.height() * 1.05f
                                                && l.right - l.left >= page.width * .3f
                                                && l.right - l.left <= page.width * .9f
                                                && Math.abs(l.center() - page.width * .5f)
                                                        < page.width * .12f
                                                && l.top - heading.bottom >= page.height * .5f
                                                && coverFooterText(l.text))
                        .toList();
        if (footers.size() != 1) return List.of();
        Line footer = footers.get(0);
        if (page.lines.stream()
                        .anyMatch(
                                l ->
                                        !l.equals(heading)
                                                && !l.equals(footer)
                                                && l.height() >= page.width * .02f
                                                && clean(l.text)
                                                                .codePointCount(
                                                                        0, clean(l.text).length())
                                                        > 2)
                || page.lines.stream().filter(l -> sameCoverBox(l, footer)).count() != 1)
            return List.of();
        return List.of(footer);
    }

    private static boolean validCoverLine(Page page, Line line) {
        return line.text != null
                && Float.isFinite(line.left)
                && Float.isFinite(line.top)
                && Float.isFinite(line.right)
                && Float.isFinite(line.bottom)
                && line.left >= 0
                && line.top >= 0
                && line.right > line.left
                && line.bottom > line.top
                && line.right <= page.width
                && line.bottom <= page.height;
    }

    private static boolean sameCoverBox(Line first, Line second) {
        return first.left == second.left
                && first.top == second.top
                && first.right == second.right
                && first.bottom == second.bottom;
    }

    private static boolean coverFooterText(String text) {
        if (text == null || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) return false;
        String value = Normalizer.normalize(clean(text), Normalizer.Form.NFC);
        int words = value.split("\\h+").length;
        return words >= 2
                && words <= 5
                && value.codePointCount(0, value.length()) <= 80
                && properReviewName(value)
                && credit(value) == null
                && !LEGAL.matcher(value).find()
                && !COVER_FOOTER_CAPTION.matcher(value).find();
    }

    /** Three strong original-PDF readings may refine the retained literal; failed reads retain it. */
    public static List<Line> mergeCoverFooterReviewConsensus(
            Page page, Line original, List<String> readings, List<Float> confidence) {
        if (!coverFooterReviewCandidates(page).contains(original)
                || readings == null
                || readings.size() != 3
                || confidence == null
                || confidence.size() != 3
                || confidence.stream()
                        .anyMatch(v -> v == null || !Float.isFinite(v) || v < .95f || v > 1)
                || readings.stream().anyMatch(v -> !coverFooterText(v))) return page.lines;
        String text = clean(readings.get(0));
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        if (readings.stream()
                        .anyMatch(
                                v ->
                                        !Normalizer.normalize(clean(v), Normalizer.Form.NFC)
                                                .equals(normalized))
                || !anchoredCoverFooterRefinement(original.text, normalized)) return page.lines;
        List<Line> result = new ArrayList<>(page.lines);
        result.set(
                result.indexOf(original),
                new Line(text, original.left, original.top, original.right, original.bottom));
        return List.copyOf(result);
    }

    private static boolean anchoredCoverFooterRefinement(String original, String reading) {
        String before = Normalizer.normalize(clean(original), Normalizer.Form.NFC);
        String[] sourceWords = before.split("\\h+"), readWords = reading.split("\\h+");
        String first = sourceWords[0], nextFirst = readWords[0];
        if (first.codePointCount(0, first.length()) < 4
                || !first.equals(nextFirst)
                || !CreditOcrIdentifierGuard.preservesIdentifiers(before, reading)) return false;
        boolean sameBoundaries = sourceWords.length == readWords.length;
        if (sameBoundaries)
            for (int i = 0; i < sourceWords.length; i++)
                sameBoundaries &=
                        sourceWords[i].codePointCount(0, sourceWords[i].length())
                                == readWords[i].codePointCount(0, readWords[i].length());
        // Only a run of adjacent bare single-letter fragments may collapse;
        // an isolated initial or any punctuated initial remains a separate word.
        boolean fragments =
                sourceWords.length >= 4
                        && readWords.length == 2
                        && sourceWords[sourceWords.length - 1].codePointCount(
                                        0, sourceWords[sourceWords.length - 1].length())
                                >= 3;
        for (int i = 1; i < sourceWords.length - 1; i++)
            fragments &=
                    sourceWords[i].codePointCount(0, sourceWords[i].length()) == 1
                            && Character.isLetter(sourceWords[i].codePointAt(0));
        if (!sameBoundaries && !fragments) return false;
        int[] a = before.replaceAll("\\h+", "").codePoints().toArray();
        int[] b = reading.replaceAll("\\h+", "").codePoints().toArray();
        if (a.length != b.length) return false;
        int offset = 0;
        for (String word : sourceWords) {
            if (word.matches("\\p{L}\\p{M}*\\.?") && a[offset] != b[offset]) return false;
            offset += word.codePointCount(0, word.length());
        }
        int differences = 0;
        for (int i = 0; i < a.length; i++)
            if (a[i] != b[i]) {
                if (++differences > 1
                        || i == 0
                        || i == a.length - 1
                        || !Character.isLetter(a[i])
                        || !Character.isLetter(b[i])
                        || a[i] > Character.MAX_VALUE
                        || b[i] > Character.MAX_VALUE
                        || hasCoverAccent(a[i])
                        || hasCoverAccent(b[i])
                        || i + 1 < a.length && (isCoverMark(a[i + 1]) || isCoverMark(b[i + 1]))
                        || !(a[i] == 'I' && b[i] == 'L' || a[i] == 'L' && b[i] == 'I'))
                    return false;
            }
        return true;
    }

    private static boolean hasCoverAccent(int point) {
        return Normalizer.normalize(new String(Character.toChars(point)), Normalizer.Form.NFD)
                .codePoints()
                .anyMatch(ScoreCreditsDetector::isCoverMark);
    }

    private static boolean isCoverMark(int point) {
        int type = Character.getType(point);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
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
        String roleOnly = arrangementRoleOnlyConsensus(original, readings);
        if (roleOnly != null) return roleOnly;
        return headingSingleLetterLossConsensus(page, original, readings);
    }

    /** A damaged role word may vary while every already printed name stays literal. */
    private static String arrangementRoleOnlyConsensus(Line original, List<String> readings) {
        if (credit(clean(original.text)) != null
                || misspelledArrangementCredit(clean(original.text)) == null
                || readings == null
                || readings.size() != 3) return null;
        Pattern format = Pattern.compile("(?iu)^(arr[\\p{L}]{4,6})\\h+by\\h+(.+)$");
        if (Pattern.compile("\\R").matcher(original.text).find()) return null;
        Matcher source = format.matcher(original.text.replaceAll("\\h+", " ").trim());
        if (!source.matches()) return null;
        if (source.group(1)
                .toLowerCase(Locale.ROOT)
                .matches("arrange|arranges|arranger|arrangers|arranging|arrangement|arrangements"))
            return null;
        String payload = source.group(2), observedExact = null;
        for (String reading : readings) {
            if (reading == null || Pattern.compile("\\R").matcher(reading).find()) return null;
            String text = reading.replaceAll("\\h+", " ").trim();
            Matcher match = format.matcher(text);
            if (!match.matches() || !match.group(2).equals(payload)) return null;
            String label = match.group(1).toLowerCase(Locale.ROOT);
            if (label.matches(
                    "arrange|arranges|arranger|arrangers|arranging|arrangement|arrangements"))
                return null;
            boolean near = false;
            if (label.length() == 8) {
                int changes = 0;
                for (int i = 0; i < 8; i++) if (label.charAt(i) != "arranged".charAt(i)) changes++;
                near = changes <= 2;
            } else if (label.length() == 9) {
                for (int i = 0; i < 9; i++)
                    if ((label.substring(0, i) + label.substring(i + 1)).equals("arranged"))
                        near = true;
            }
            if (!near) return null;
            if (label.equals("arranged")) observedExact = text;
        }
        return observedExact;
    }

    /** Reread damaged contributor punctuation or an uppercase explicit credit in its tight box. */

    /** Cover letterspacing is recoverable without changing a single printed letter. */
    public static List<Line> overlappingAccentCreditCandidates(Page page) {
        if (page.photographicCover || page.creditsOnly || page.width <= 0 || page.height <= 0)
            return List.of();
        return page.lines.stream()
                .filter(
                        l ->
                                l.top >= 0
                                        && l.height() > 0
                                        && l.height() < page.width * .04f
                                        && l.bottom
                                                < (page.notationTop > 0
                                                        ? page.notationTop
                                                        : page.height * .3f)
                                        && !withoutDiacritics(clean(l.text)).equals(clean(l.text))
                                        && (credit(clean(l.text)) != null
                                                || clean(l.text)
                                                        .matches("(?iu)^produced\\h+by\\h+.+"))
                                        && creditOverlapRegion(page, l) != null)
                .limit(4)
                .toList();
    }

    public static Refinement creditOverlapRegion(Page page, Line line) {
        float top = line.top;
        for (Line other : page.lines) {
            if (other == line
                    || other.top >= line.top
                    || other.bottom < line.top
                    || other.bottom - line.top > line.height() * .1f
                    || other.left >= line.right
                    || other.right <= line.left
                    || !clean(other.text).matches(".*\\p{L}{2}.*")) continue;
            top = Math.max(top, other.bottom + page.width / 1800f);
        }
        if (top <= line.top || top - line.top > line.height() * .15f) return null;
        return new Refinement(line.left, top, line.right, line.bottom, 3);
    }

    private static boolean confidentAccentReadings(List<String> text, List<Float> confidence) {
        return text != null
                && confidence != null
                && text.size() == 3
                && confidence.size() == 3
                && text.stream().allMatch(t -> t != null && !t.isBlank())
                && confidence.stream()
                        .allMatch(c -> c != null && Float.isFinite(c) && c >= .98f && c <= 1);
    }

    public static List<Line> mergeOverlappingAccentCredit(
            Page page,
            Line original,
            List<String> expanded,
            List<Float> expandedConfidence,
            List<String> separated,
            List<Float> separatedConfidence) {
        if (!overlappingAccentCreditCandidates(page).contains(original)
                || !confidentAccentReadings(expanded, expandedConfidence)
                || !confidentAccentReadings(separated, separatedConfidence)) return page.lines;
        String candidate = clean(expanded.get(0)), before = clean(original.text);
        if (expanded.stream().anyMatch(t -> !clean(t).equals(candidate))
                || separated.stream().anyMatch(t -> !clean(t).equals(candidate))
                || !withoutDiacritics(before).equals(withoutDiacritics(candidate))
                || before.equals(candidate)) return page.lines;
        var result = new ArrayList<>(page.lines);
        result.set(
                result.indexOf(original),
                new Line(candidate, original.left, original.top, original.right, original.bottom));
        return List.copyOf(result);
    }

    public static List<Line> coverSpacingCandidates(Page page) {
        if (page.notationTop > 0 && !page.photographicCover || page.creditsOnly) return List.of();
        return page.lines.stream()
                .filter(
                        line -> {
                            String value = clean(line.text);
                            String letters = value.replaceAll("\\h", "");
                            if (line.top < 0
                                    || line.bottom > page.height * .85f
                                    || line.height() < page.width * .012f
                                    || line.height() > page.width * .1f
                                    || !value.matches("[\\p{Lu}\\h]+")
                                    || letters.length() < 6
                                    || letters.length() > 60) return false;
                            String[] words = value.split("\\h+");
                            return words.length == 1 && letters.length() >= 8
                                    || words.length >= 3
                                            && Arrays.stream(words)
                                                            .filter(w -> w.length() <= 2)
                                                            .count()
                                                    >= 3;
                        })
                .limit(4)
                .toList();
    }

    public static Refinement coverSpacingRegion(Page page, Line line) {
        float scale = page.width / 1800f;
        return new Refinement(
                Math.max(0, line.left - 12 * scale),
                Math.max(0, line.top - 10 * scale),
                Math.min(page.width, line.right + 20 * scale),
                Math.min(page.height, line.bottom + 10 * scale),
                3);
    }

    public static List<Line> mergeCoverSpacingConsensus(
            Page page, Line original, List<String> readings, List<Float> confidence) {
        if (!coverSpacingCandidates(page).contains(original)
                || readings == null
                || readings.size() != 3
                || confidence == null
                || confidence.size() != 3
                || readings.stream().anyMatch(r -> r == null || r.isBlank())
                || confidence.stream()
                        .anyMatch(c -> c == null || !Float.isFinite(c) || c < .98f || c > 1))
            return page.lines;
        String candidate = clean(readings.get(0));
        if (!candidate.matches("[\\p{Lu}\\h]+")
                || readings.stream().anyMatch(r -> !clean(r).equals(candidate))
                || !candidate
                        .replaceAll("\\h", "")
                        .equals(clean(original.text).replaceAll("\\h", ""))
                || candidate.equals(clean(original.text))) return page.lines;
        var result = new ArrayList<>(page.lines);
        result.set(
                result.indexOf(original),
                new Line(candidate, original.left, original.top, original.right, original.bottom));
        return List.copyOf(result);
    }

    public static List<Line> tightCreditRepairCandidates(Page page) {
        if (page.photographicCover) return List.of();
        return page.lines.stream()
                .filter(
                        line ->
                                line.bottom
                                                < (page.notationTop > 0
                                                        ? page.notationTop
                                                        : page.height * .62f)
                                        && line.height() > 0
                                        && line.height()
                                                < page.width
                                                        * (presentedNameLines(page).contains(line)
                                                                ? .12f
                                                                : .04f)
                                        && (page.notationTop > 0
                                                        && (puncturedContributor(page, line)
                                                                || uppercasePrefixCredit(
                                                                        page, line))
                                                || printedCreditContinuation(page, line) != null
                                                || conversionServiceCandidate(line.text)
                                                || pairedCollectionContributor(page, line)
                                                || presentedNameLines(page).contains(line)))
                .toList();
    }

    private static boolean countedInstrumentAudioCaption(String text) {
        return clean(text)
                .matches(
                        "(?iu)^[0-9]{1,2}\\h*(?:violin|piano|flute|cello|guitar|vocal)\\h+sol[o0b]s\\h+with\\h+authentic\\h+CD\\h+tracks$");
    }

    private static boolean pairedCollectionContributor(Page page, Line line) {
        if (page.notationTop > 0
                || page.photographicCover
                || page.creditsOnly
                || !clean(line.text).matches("\\p{Lu}{5,20}")
                || line.left < page.width * .65f
                || line.height() > page.width * .03f) return false;
        boolean caption =
                page.lines.stream()
                        .anyMatch(
                                l -> l.bottom < line.top && countedInstrumentAudioCaption(l.text));
        return caption
                && page.lines.stream()
                        .anyMatch(
                                l ->
                                        l != line
                                                && l.top < line.top
                                                && Math.abs(l.right - line.right)
                                                        < page.width * .025f
                                                && Math.abs(line.top - l.bottom)
                                                        < line.height() * .5f
                                                && clean(l.text).matches(".*\\p{Ll}.*")
                                                && credit(l.text) == null);
    }

    private static boolean oneInsertedCapital(String before, String after) {
        if (!after.matches("\\p{Lu}{6,21}") || after.length() != before.length() + 1) return false;
        for (int i = 0; i < after.length(); i++)
            if ((after.substring(0, i) + after.substring(i + 1)).equals(before)) return true;
        return false;
    }

    private static boolean conversionServiceCandidate(String text) {
        Matcher m =
                Pattern.compile("(?iu)^([a-z]{6,11})\\h+by\\h+([\\p{L}][\\p{L} .’'-]{2,80})$")
                        .matcher(clean(text));
        return m.matches() && editDistance(m.group(1).toLowerCase(Locale.ROOT), "converted") <= 3;
    }

    private static boolean matchingConversionRepair(String before, String after) {
        if (!conversionServiceCandidate(before)
                || !after.matches("(?iu)^converted\\h+by\\h+[\\p{L}][\\p{L} .’'-]{2,80}$"))
            return false;
        String a = before.replaceFirst("(?iu)^\\S+\\h+by\\h+", ""),
                b = after.replaceFirst("(?iu)^converted\\h+by\\h+", "");
        return a.split("\\h+").length == b.split("\\h+").length
                && editDistance(fold(a), fold(b)) <= Math.min(3, Math.max(1, fold(a).length() / 4));
    }

    private static boolean uppercasePrefixCredit(Page page, Line line) {
        Credit role = credit(clean(line.text));
        return role != null
                && !role.suffix
                && bibliographicLineCandidate(page, line)
                && role.value.equals(role.value.toUpperCase(Locale.ROOT))
                && !creditNames(role.value).isEmpty()
                && role.value.matches(".*\\p{Lu}{2,}$");
    }

    private static boolean puncturedContributor(Page page, Line line) {
        String before = clean(line.text);
        int p = before.indexOf('(');
        if (p < 1
                || p != before.lastIndexOf('(')
                || before.indexOf(')') >= 0
                || before.charAt(p - 1) != ' '
                || p + 1 >= before.length()
                || !Character.isUpperCase(before.codePointAt(p + 1))) return false;
        String repaired = before.substring(0, p) + before.substring(p + 1);
        if (!properReviewName(repaired)) return false;
        Line heading = chooseTitle("", page, page.lines, page.notationTop);
        return heading != null
                && line != heading
                && contextualOriginalAuthor(
                        page,
                        heading,
                        new Line(repaired, line.left, line.top, line.right, line.bottom),
                        page.lines);
    }

    public static List<Line> mergeTightCreditRepairConsensus(
            Page page, Line original, List<String> readings, List<Float> confidence) {
        if (!tightCreditRepairCandidates(page).contains(original)
                || readings == null
                || readings.size() != 3
                || confidence == null
                || confidence.size() != 3
                || readings.stream().anyMatch(r -> r == null || r.isBlank())
                || confidence.stream()
                        .anyMatch(c -> c == null || !Float.isFinite(c) || c < .98f || c > 1))
            return page.lines;
        String before = clean(original.text), candidate = clean(readings.get(0));
        if (readings.stream().anyMatch(r -> !clean(r).equals(candidate))) return page.lines;
        if (presentedNameLines(page).contains(original)) {
            if (!before.matches("\\p{Lu}\\p{Ll}{2,20}")
                    || !candidate.matches("\\p{Lu}\\p{Ll}{2,20}")
                    || editDistance(fold(before), fold(candidate)) > 2) return page.lines;
        } else if (pairedCollectionContributor(page, original)) {
            if (!oneInsertedCapital(before, candidate)) return page.lines;
        } else if (conversionServiceCandidate(before)) {
            if (!matchingConversionRepair(before, candidate)) return page.lines;
        } else if (puncturedContributor(page, original)) {
            if (!candidate.equals(before.replace("(", ""))) return page.lines;
        } else if (printedCreditContinuation(page, original) != null) {
            return mergeBibliographicLineConsensus(page, original, readings);
        } else {
            if (!candidate.startsWith(before)
                    || candidate.length() != before.length() + 1
                    || !Character.isLetter(candidate.charAt(candidate.length() - 1)))
                return page.lines;
            return mergeBibliographicLineConsensus(page, original, readings);
        }
        var merged = new ArrayList<>(page.lines);
        merged.set(
                merged.indexOf(original),
                new Line(candidate, original.left, original.top, original.right, original.bottom));
        return List.copyOf(merged);
    }

    /** A separately expanded original-PDF crop may resolve a case-mismatched edge echo. */
    public static List<Line> creditBoundaryEchoCandidates(Page page) {
        return page.lines.stream()
                .filter(
                        line ->
                                bibliographicLineCandidate(page, line)
                                        && printedCreditContinuation(page, line) != null
                                        && boundaryEchoText(clean(line.text)))
                .toList();
    }

    private static boolean boundaryEchoText(String text) {
        if (!text.matches(".*\\b[\\p{Lu}]{3,}[\\p{Ll}]$")) return false;
        int last = text.codePointBefore(text.length());
        int prior = text.codePointBefore(text.length() - Character.charCount(last));
        return Character.toUpperCase(last) == prior;
    }

    public static List<Line> mergeCreditBoundaryEchoConsensus(
            Page page, Line original, List<String> readings, List<Float> confidence) {
        if (!creditBoundaryEchoCandidates(page).contains(original)
                || readings == null
                || readings.size() != 3
                || confidence == null
                || confidence.size() != 3
                || confidence.stream()
                        .anyMatch(c -> c == null || !Float.isFinite(c) || c < .98f || c > 1)
                || readings.stream().anyMatch(r -> r == null || r.isBlank())) return page.lines;
        String before = clean(original.text), candidate = clean(readings.get(0));
        int last = before.codePointBefore(before.length());
        if (!candidate.equals(before.substring(0, before.length() - Character.charCount(last)))
                || readings.stream().anyMatch(r -> !clean(r).equals(candidate))
                || !preservesBibliographicIdentifiers(page, original, candidate)
                || !sameConsensusContributorCount(before, candidate)) return page.lines;
        var merged = new ArrayList<>(page.lines);
        merged.set(
                merged.indexOf(original),
                new Line(candidate, original.left, original.top, original.right, original.bottom));
        return List.copyOf(merged);
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
                            + "|arranged\\h+for\\h+[^:;]{1,60}?\\h+by|arranged\\h+by|arrangements?\\h+by|transcribed\\h+by|transc(?:r)?iption\\h+by|transcr\\.?\\h+by|transcr\\.?|arrangers?|arr[.,]?\\h+by|ar\\.\\h+by|arr\\.?|arrangements?"
                            + "|(?:additional\\h+)?lyrics?\\h+by)\\b[\\h.:=–—-]*(.*)$");
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
            if (repeatedPrintedHeader(page, full, crop, cropped)) return true;
        }
        return full == null || headerHintMatches(crop.text, hint);
    }

    /** Two separate printed copies can corroborate a centered title missed by full-page OCR. */
    private static boolean repeatedPrintedHeader(
            Page page, Line full, Line crop, List<Line> cropped) {
        if (page.width <= 0
                || crop.top > page.height * .2f
                || crop.bottom >= page.notationTop
                || crop.height() < page.width * .022f
                || crop.right - crop.left < page.width * .16f
                || Math.abs(crop.center() - page.width * .5f) > page.width * .12f
                || Math.abs(full.center() - page.width * .5f) < page.width * .28f
                || full.right - full.left > (crop.right - crop.left) * .4f
                || full.height() > crop.height() * 1.25f) return false;
        for (Line repeat : cropped) {
            if (repeat == crop
                    || repeat.top < crop.bottom
                    || repeat.top - crop.bottom > crop.height() * 2
                    || repeat.bottom >= page.notationTop
                    || repeat.height() < page.width * .009f
                    || repeat.height() > crop.height() * .75f
                    || repeat.left < crop.right
                    || !clean(repeat.text).equalsIgnoreCase(clean(crop.text))) continue;
            return true;
        }
        return false;
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
                && !value.matches("(?iu)^(?:additional\\h+)?lyrics?\\h+by\\b.*");
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
        if (educationalMethodCatalogue(page)) return true;
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

    /** A lone name-shaped photograph heading has no independent work-title evidence. */
    private static String isolatedPhotographicHeading(String fallback, List<Page> pages) {
        if (fallback.isEmpty() || pages.size() != 1) return "";
        Page p = pages.get(0);
        if (!p.photographicCover
                || p.creditsOnly
                || p.notationTop > 0
                || !p.reviewCredits.isEmpty()) return "";
        List<Line> text = p.lines.stream().filter(l -> !clean(l.text).isEmpty()).toList();
        if (text.size() != 1) return "";
        Line l = text.get(0);
        String value = clean(l.text);
        int words = value.split("\\h+").length;
        if (words < 2
                || words > 3
                || !properReviewName(value)
                || credit(value) != null
                || LEGAL.matcher(value).find()
                || NOT_TITLE.matcher(value).find()
                || same(fallback, value)
                || corroboratesTitleHint(fallback, value)
                || l.top < 0
                || l.bottom > p.height * .5f
                || l.height() < p.width * .025f) return "";
        return value;
    }

    public static Result detect(String titleHint, List<Page> pages, Set<String> knownNames) {
        String fallback = clean(titleHint);
        String isolatedPhotoName = isolatedPhotographicHeading(fallback, pages);
        if (!isolatedPhotoName.isEmpty())
            return new Result(fallback, "", "", "", List.of(isolatedPhotoName));
        String narrativeArtwork = ambiguousNarrativeArtwork(fallback, pages);
        if (!narrativeArtwork.isEmpty()) return new Result(fallback, "", "", "", List.of());
        Set<String> artists = new LinkedHashSet<>(), composers = new LinkedHashSet<>();
        Set<String> arrangers = new LinkedHashSet<>(), other = new LinkedHashSet<>();
        List<RepeatedCredit> repeatedCredits = new ArrayList<>();
        List<RepeatedCredit> inferredCredits = new ArrayList<>();
        Set<String> traditionalWorks = new LinkedHashSet<>();
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
            Line versionCaption =
                    page == pages.get(0)
                            ? nearbyVersionTitleCaption(
                                    page, heading, titleLines, titleLimit, knownNames)
                            : null;
            JoinedTitle joinedTitle =
                    heading == null
                            ? new JoinedTitle("", List.of())
                            : joinTitleEvidence(
                                    page,
                                    heading,
                                    titleLines,
                                    titleLimit,
                                    page == pages.get(0),
                                    knownNames);
            String pageTitle = joinedTitle.text;
            if (heading != null
                    && page.notationTop > 0
                    && lines.stream()
                            .anyMatch(
                                    l ->
                                            l.top >= heading.top
                                                    && l.bottom < page.notationTop
                                                    && clean(l.text)
                                                            .matches("(?iu)^traditional[.]?$")))
                traditionalWorks.add(pageTitle);
            List<Line> selectedStackedLines = List.of();
            String selectedStackedTitle = "";
            if (heading != null && page.photographicCover) {
                String stacked = photographicStackedTitle(page, heading, titleLines, titleLimit);
                if (!stacked.isEmpty()
                        && !containsVersionTitleCaption(pageTitle, heading, versionCaption)) {
                    pageTitle = stacked;
                    selectedStackedTitle = stacked;
                    selectedStackedLines =
                            photographicStackedTitleLines(page, heading, titleLines, titleLimit);
                }
            }
            if (page.notationTop <= 0
                    && !same(fallback, pageTitle)
                    && !containsVersionTitleCaption(pageTitle, heading, versionCaption)) {
                JoinedTitle coverTitle =
                        corroboratedCoverTitleEvidence(fallback, page, titleEvidence, titleLimit);
                if (!coverTitle.text.isEmpty()) {
                    pageTitle = coverTitle.text;
                    joinedTitle = coverTitle;
                }
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
            List<Line> coverFooterReview =
                    page == pages.get(0) ? coverFooterReviewCandidates(page) : List.of();
            for (Line line : lines) {
                if (scopedClaimed.contains(line)) continue;
                if (selectedStackedLines.contains(line) && same(pageTitle, selectedStackedTitle)) {
                    claimed.add(line);
                    continue;
                }
                if (page.photographicCover
                        && (same(clean(line.text), pageTitle) || same(clean(line.text), fallback))
                        && clean(line.text).matches("(?iu)^by\\h+.*")) {
                    claimed.add(line);
                    continue;
                }
                if (heading != null
                        && page.notationTop <= 0
                        && !page.creditsOnly
                        && line.bottom <= heading.top
                        && properReviewName(line.text)
                        && clean(line.text).split("\\h+").length >= 2
                        && line.height() >= heading.height() * .7f
                        && Math.abs(line.center() - heading.center()) < page.width * .08f
                        && same(pageTitle, fallback)
                        && !same(line.text, pageTitle)
                        && credit(line.text) == null
                        && !LEGAL.matcher(line.text).find()) {
                    other.add(canonicalName(clean(line.text), knownNames));
                    claimed.add(line);
                    continue;
                }

                if (clean(line.text)
                                .matches("(?iu)^converted\\h+by\\h+[\\p{L}][\\p{L} .’'-]{2,80}$")
                        && inCreditHeader(page, line)) {
                    other.add(clean(line.text).replaceFirst("(?iu)^converted\\h+by\\h+", ""));
                    claimed.add(line);
                    continue;
                }
                if (coverFooterReview.contains(line)) {
                    other.add(
                            photographicReviewByline(page, line, heading)
                                    ? clean(line.text).replaceFirst("(?iu)^by\\h+", "")
                                    : clean(line.text));
                    claimed.add(line);
                    continue;
                }
                ReviewCredit service =
                        page.reviewCredits.stream()
                                .filter(
                                        e ->
                                                line.equals(e.line)
                                                        && PrintedServiceCreditMarks
                                                                .isLiteralPayload(line, e.value)
                                                        && credit(line.text) != null
                                                        && inCreditHeader(page, line))
                                .findFirst()
                                .orElse(null);
                if (service != null) {
                    other.add(clean(service.value));
                    claimed.add(line);
                    continue;
                }
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
                    other.add(
                            photographicReviewByline(page, line, heading)
                                    ? clean(line.text).replaceFirst("(?iu)^by\\h+", "")
                                    : clean(line.text));
                    claimed.add(line);
                    continue;
                }
                if (LEGAL.matcher(line.text).find() || !inCreditHeader(page, line)) continue;
                if (line != heading
                        && INITIALLED_NAME.matcher(clean(line.text)).matches()
                        && !(page.notationTop > 0 && notationReviewLabel(page, line))
                        && (!numberedMovementSubtitle(page, heading, line)
                                || knownNames.stream().anyMatch(n -> same(n, line.text))
                                || page.reviewCredits.stream()
                                        .anyMatch(r -> intersects(r.line, line))))
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
                Line arrangementPrelude = adaptedArrangementPrelude(line, lines);
                if (credit.role == Role.COMPOSER && arrangementPrelude != null) {
                    credit = new Credit(Role.ARRANGER, credit.value, false);
                    claimed.add(arrangementPrelude);
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
                repeatedCredits.add(
                        new RepeatedCredit(pageTitle, credit.role, Set.copyOf(creditNames(value))));
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
            if (heading != null)
                for (Line line : lines) {
                    boolean datedAuthor = DATED_AUTHOR.matcher(clean(line.text)).matches();
                    boolean contextAuthor = contextualOriginalAuthor(page, heading, line, lines);
                    if (page.creditsOnly
                            && !contextAuthor
                            && !(datedAuthor && datedMovementOpening(page, heading))) continue;
                    // A separate side column may start above a shorter catalogue
                    // heading while its birth/death line overlaps that heading.
                    boolean sideDatedAuthor =
                            datedAuthor
                                    && line.bottom >= heading.top
                                    && line.top <= heading.bottom
                                    && (line.left >= heading.right || line.right <= heading.left);
                    if (claimed.contains(line)
                            || joinedTitle.lines.contains(line)
                                    && same(pageTitle, joinedTitle.text)
                                    && knownNames.stream().noneMatch(n -> same(n, line.text))
                                    && page.reviewCredits.stream()
                                            .noneMatch(r -> intersects(r.line, line))
                            || line.equals(versionCaption)
                                    && containsPrintedPhrase(title, line.text)
                            || line == heading
                            || line.top < heading.bottom
                                    && !contextAuthor
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
                            || unlabelledReviewNoise(page, heading, line, lines)
                            || publicationAudioAccessBadge(page, line, lines)
                            || numberedMovementSubtitle(page, heading, line)
                                    && knownNames.stream().noneMatch(n -> same(n, line.text))
                                    && page.reviewCredits.stream()
                                            .noneMatch(r -> intersects(r.line, line))
                            || alignedChordReviewFragment(page, line, lines)
                                    && knownNames.stream().noneMatch(n -> same(n, line.text))
                                    && page.reviewCredits.stream()
                                            .noneMatch(r -> intersects(r.line, line))
                            || compoundInstrumentationCaption(line.text)
                            || centeredWorkSubtitle(page, heading, line, knownNames)
                            || collectionOpeningSection(page, heading, line, lines, knownNames)
                            || instructionalDocumentHeading(page, heading, lines)
                                    && paragraphLine(page, line, lines)
                            || staffCharacterDirection(page, line, lines, titleLimit)
                            || line.height() >= heading.height() * .85f
                                    && !DATED_AUTHOR.matcher(clean(line.text)).matches()) continue;
                    String name = canonicalName(clean(line.text), knownNames);
                    if (!datedAuthor && contextAuthor) {
                        String author =
                                clean(line.text)
                                        .replaceFirst("(?iu)^(?:and|&)\\h+", "")
                                        .replaceFirst("\\h*\\((?:1[5-9]|20)[0-9]{2}\\)\\h*$", "");
                        List<String> inferredNames =
                                contextualAuthorNames(canonicalName(author, knownNames));
                        if (conflictingLyricByline(page, lines, inferredNames)) {
                            addNames(other, inferredNames);
                            continue;
                        }
                        addNames(composers, inferredNames);
                        inferredCredits.add(
                                new RepeatedCredit(
                                        pageTitle, Role.COMPOSER, Set.copyOf(inferredNames)));
                        continue;
                    }
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

        Set<String> separatedReview = new LinkedHashSet<>();
        for (String value : other) {
            Matcher producer =
                    Pattern.compile("(?iu)^(?:(.{3,100}?),\\h*)?produced\\h+by\\h+(.{3,100})$")
                            .matcher(value);
            Matcher workCredit =
                    Pattern.compile(
                                    "(?iu)^(?:film|movie|anime)\\h*:\\h*.+?\\h+[-–—]\\h+(.{3,100})$")
                            .matcher(value);
            if (producer.matches()) {
                if (producer.group(1) != null)
                    addNames(separatedReview, creditNames(producer.group(1)));
                addNames(separatedReview, creditNames(producer.group(2)));
            } else if (workCredit.matches())
                addNames(separatedReview, creditNames(workCredit.group(1)));
            else separatedReview.add(value);
        }
        other.clear();
        other.addAll(separatedReview);
        for (String candidate : List.copyOf(composers)) {
            List<RepeatedCredit> inferred =
                    inferredCredits.stream().filter(e -> e.names.contains(candidate)).toList();
            if (!inferred.isEmpty()
                    && inferred.stream()
                            .allMatch(e -> traditionalWorks.stream().anyMatch(t -> same(t, e.work)))
                    && repeatedCredits.stream()
                            .noneMatch(
                                    e -> e.role == Role.COMPOSER && e.names.contains(candidate))) {
                composers.remove(candidate);
                other.add(candidate);
            }
        }
        preferExpandedInferredName(composers, inferredCredits, repeatedCredits);
        preferExplicitCoauthorNames(composers, inferredCredits, repeatedCredits);
        repairRepeatedCreditDrops(composers, Role.COMPOSER, repeatedCredits);
        repairRepeatedCreditDrops(arrangers, Role.ARRANGER, repeatedCredits);
        repairRepeatedCreditDrops(artists, Role.ARTIST, repeatedCredits);
        splitCorroboratedContributors(artists);
        splitCorroboratedContributors(composers);
        splitCorroboratedContributors(arrangers);
        // The OCR producer can retain one separate name on the opening cover only.
        if (!pages.isEmpty()) {
            Page opening = pages.get(0);
            List<String> retained =
                    opening.reviewCredits.stream()
                            .map(
                                    evidence ->
                                            CurvedCoverCreditReview.retainedValue(
                                                    opening, evidence))
                            .filter(value -> !value.isEmpty())
                            .toList();
            if (retained.size() == 1) {
                String value = retained.get(0);
                if (!containsPrintedPhrase(title.isEmpty() ? fallback : title, value)
                        && artists.stream().noneMatch(name -> containsPrintedPhrase(name, value))
                        && composers.stream().noneMatch(name -> containsPrintedPhrase(name, value))
                        && arrangers.stream().noneMatch(name -> containsPrintedPhrase(name, value))
                        && other.stream().noneMatch(name -> containsPrintedPhrase(name, value)))
                    other.add(value);
            }
        }
        inferRepeatedCoverAuthor(pages, composers, arrangers, artists, other);
        restoreReviewHeaderSpacing(other, pages, title, fallback);
        Set<String> typographicReview = new LinkedHashSet<>();
        for (String value : other) typographicReview.add(normalizeRaisedOReview(value, pages));
        other.clear();
        other.addAll(typographicReview);
        for (Page p : pages) {
            List<Line> presented = presentedNameLines(p);
            if (presented.size() == 2) {
                other.removeIf(v -> presented.stream().anyMatch(l -> same(l.text, v)));
                String name = clean(presented.get(0).text) + " " + clean(presented.get(1).text);
                if (artists.stream().noneMatch(n -> same(n, name))
                        && composers.stream().noneMatch(n -> same(n, name))
                        && arrangers.stream().noneMatch(n -> same(n, name))) other.add(name);
            }
        }
        for (Page page : pages) {
            List<Line> name = photographicBandLeader(page);
            if (name.size() == 3) {
                other.removeIf(value -> name.stream().anyMatch(l -> same(l.text, value)));
                String combined = clean(name.get(0).text) + " " + clean(name.get(1).text);
                if (artists.stream().noneMatch(n -> same(n, combined))
                        && composers.stream().noneMatch(n -> same(n, combined))
                        && arrangers.stream().noneMatch(n -> same(n, combined)))
                    other.add(combined);
            }
        }

        String finalTitle = title.isEmpty() ? fallback : title;
        other.removeIf(
                value ->
                        value.matches("[A-Z]{8,}")
                                && Arrays.stream(finalTitle.toUpperCase(Locale.ROOT).split("\\h+"))
                                        .anyMatch(
                                                word ->
                                                        word.equals(value)
                                                                || word.equals(value + "S"))
                                && pages.stream()
                                        .anyMatch(
                                                p ->
                                                        p.notationTop <= 0
                                                                && !p.photographicCover
                                                                && p.lines.stream()
                                                                        .anyMatch(
                                                                                l ->
                                                                                        same(
                                                                                                        l.text,
                                                                                                        value)
                                                                                                && l
                                                                                                                .height()
                                                                                                        >= p.width
                                                                                                                * .04f))
                                && !knownNames.stream().anyMatch(n -> same(n, value))
                                && repeatedCredits.stream()
                                        .noneMatch(
                                                e ->
                                                        e.names.stream()
                                                                .anyMatch(n -> same(n, value))));
        other.removeIf(
                value ->
                        !knownNames.stream().anyMatch(name -> same(name, value))
                                && repeatedCredits.stream()
                                        .noneMatch(
                                                e ->
                                                        e.names.stream()
                                                                .anyMatch(
                                                                        name -> same(name, value)))
                                && pages.stream()
                                        .noneMatch(
                                                p ->
                                                        p.reviewCredits.stream()
                                                                .anyMatch(
                                                                        r -> same(r.value, value)))
                                && pages.stream()
                                        .anyMatch(
                                                page ->
                                                        !page.creditsOnly
                                                                && page.notationTop <= 0
                                                                && page.lines.stream()
                                                                        .anyMatch(
                                                                                line ->
                                                                                        same(
                                                                                                        line.text,
                                                                                                        value)
                                                                                                && collectionListEntry(
                                                                                                        page,
                                                                                                        line,
                                                                                                        page.lines))));
        Set<String> repeatedInferredNames = new LinkedHashSet<>();
        for (String name : composers)
            if (inferredCredits.stream().filter(e -> e.names.contains(name)).count() >= 1)
                repeatedInferredNames.add(name);
        discardRepeatedRoleSpellingEcho(
                other, pages, artists, composers, arrangers, repeatedInferredNames);
        return new Result(
                title.isEmpty() ? fallback : title,
                String.join("; ", artists),
                String.join("; ", composers),
                String.join("; ", arrangers),
                List.copyOf(other));
    }

    /** A standalone narrative quotation does not identify the artwork's work title. */

    /** A classical cover author is corroborated by a notated author-work running header. */
    private static void inferRepeatedCoverAuthor(
            List<Page> pages,
            Set<String> composers,
            Set<String> arrangers,
            Set<String> artists,
            Set<String> other) {
        if (pages.isEmpty() || !composers.isEmpty()) return;
        Page cover = pages.get(0);
        if (cover.photographicCover || cover.creditsOnly || cover.notationTop > 0) return;
        Set<String> authors = new LinkedHashSet<>(), subtitles = new LinkedHashSet<>();
        for (Line work : cover.lines) {
            if (!clean(work.text)
                    .matches(
                            "(?iu)^(?:sonata|concerto|symphony|suite)\\h+in\\h+[A-G](?:[- ]?(?:flat|sharp))?\\h+(?:major|minor)$"))
                continue;
            for (Line name : cover.lines) {
                String value = clean(name.text);
                if (name.bottom >= work.top
                        || work.top - name.bottom > cover.height * .08f
                        || Math.abs(name.center() - work.center()) > cover.width * .08f
                        || name.height() < work.height() * .5f
                        || name.height() > work.height() * 1.2f
                        || !properReviewName(value)
                        || value.split("\\h+").length < 2
                        || arrangers.stream().anyMatch(n -> same(n, value))
                        || artists.stream().anyMatch(n -> same(n, value))) continue;
                String[] words = value.split("\\h+");
                String surname = words[words.length - 1];
                for (Page page : pages) {
                    if (page.notationTop <= 0 || page.photographicCover) continue;
                    for (Line header : page.lines) {
                        if (header.bottom >= page.notationTop || header.top > page.height * .2f)
                            continue;
                        String[] parts = clean(header.text).split("\\h+[-–—]\\h+", 3);
                        if (parts.length < 2
                                || !(same(parts[0], value) || same(parts[0], surname))
                                || !same(parts[1], work.text)) continue;
                        authors.add(value);
                        if (parts.length == 3)
                            for (Line subtitle : cover.lines)
                                if (same(subtitle.text, parts[2])
                                        && subtitle.top >= work.bottom
                                        && subtitle.top - work.bottom < cover.height * .06f
                                        && Math.abs(subtitle.center() - work.center())
                                                < cover.width * .08f)
                                    subtitles.add(clean(subtitle.text));
                    }
                }
            }
        }
        if (authors.size() != 1) return;
        String author = authors.iterator().next();
        addNames(composers, List.of(author));
        other.removeIf(
                value ->
                        same(value, author)
                                || subtitles.stream().anyMatch(subtitle -> same(value, subtitle)));
    }

    private static String ambiguousNarrativeArtwork(String hint, List<Page> pages) {
        if (hint.isEmpty() || pages.size() != 1) return "";
        Page page = pages.get(0);
        if (page.creditsOnly
                || page.notationTop > 0
                || !page.reviewCredits.isEmpty()
                || !Float.isFinite(page.width)
                || !Float.isFinite(page.height)
                || !Float.isFinite(page.notationTop)
                || page.width <= 0
                || page.height <= 0) return "";
        List<Line> lines =
                page.lines.stream()
                        .filter(l -> !clean(l.text).isEmpty())
                        .sorted(Comparator.comparingDouble((Line l) -> l.top))
                        .toList();
        if (lines.size() != 2) return "";
        for (Line line : lines) {
            if (!Float.isFinite(line.left)
                    || !Float.isFinite(line.top)
                    || !Float.isFinite(line.right)
                    || !Float.isFinite(line.bottom)
                    || line.left < 0
                    || line.top < 0
                    || line.right > page.width
                    || line.bottom > page.height
                    || line.right <= line.left
                    || line.bottom <= line.top
                    || line.height() < page.width * .04f
                    || line.height() > page.width * .14f
                    || line.right - line.left < page.width * .2f
                    || line.right - line.left > page.width * .9f
                    || Math.abs(line.center() - page.width * .5f) > page.width * .08f
                    || credit(line.text) != null
                    || LEGAL.matcher(line.text).find()
                    || line.text
                            .codePoints()
                            .anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029))
                return "";
        }
        Line first = lines.get(0), second = lines.get(1);
        String opening = clean(first.text), reply = clean(second.text);
        if (first.top < page.height * .18f
                || second.bottom > page.height * .9f
                || second.top < first.bottom
                || second.top - first.bottom > first.height() * 1.5f
                || second.height() < first.height() * .75f
                || second.height() > first.height() * 1.25f
                || opening.split("\\h+").length < 5
                || reply.split("\\h+").length < 3
                || (opening + " " + reply).split("\\h+").length < 9
                || !opening.matches(
                        "(?iu)^(?:I|we|he|she|they|you)\\h+.+\\h+"
                                + "(?:said|says|asked|whispered|told\\h+(?:me|us|him|her|them))[:;]?$"))
            return "";
        return opening + " " + reply;
    }

    /** Smaller names beside an opening score heading remain evidence for role review. */

    /** Parenthetical source-work and catalogue subtitles occupy the heading column. */

    /** A collection's opening movement label belongs to the staff, not its contributors. */
    private static boolean collectionOpeningSection(
            Page page, Line heading, Line line, List<Line> lines, Set<String> knownNames) {
        if (page.photographicCover
                || page.notationTop <= 0
                || heading == null
                || line.top <= heading.bottom
                || line.bottom > page.notationTop
                || line.top < page.notationTop - page.height * .09f
                || line.center() > page.width * .4f
                || line.height() >= heading.height() * .85f
                || knownNames.stream().anyMatch(n -> same(n, line.text))
                || page.reviewCredits.stream().anyMatch(r -> intersects(r.line, line)))
            return false;
        boolean collection =
                lines.stream()
                        .anyMatch(
                                l ->
                                        l.bottom <= heading.bottom + page.height * .035f
                                                && clean(l.text)
                                                        .matches(
                                                                "(?iu).*\\b(?:medley|suite)\\b.*"));
        if (!collection) return false;
        return lines.stream()
                .anyMatch(
                        l ->
                                l != line
                                        && l.top > heading.bottom
                                        && Math.abs(l.top - line.top) < page.height * .045f
                                        && l.center() < page.width * .4f
                                        && clean(l.text)
                                                .matches(
                                                        "(?iu)^(?:andante|allegro|adagio|moderato|slowly|fanfare|con\\h+bravura|sostenuto)\\b.*"));
    }

    private static boolean centeredWorkSubtitle(
            Page page, Line heading, Line line, Set<String> knownNames) {
        String text = clean(line.text);
        if (page.photographicCover
                || page.creditsOnly
                || page.notationTop <= 0
                || line.top < heading.bottom
                || line.bottom >= page.notationTop
                || line.top - heading.bottom > heading.height() * 1.25f
                || line.height() < heading.height() * .35f
                || line.height() >= heading.height() * .85f
                || Math.abs(heading.center() - page.width * .5f) > page.width * .12f
                || Math.abs(line.center() - heading.center()) > heading.height() * .8f
                || credit(text) != null
                || knownNames.stream()
                        .anyMatch(name -> same(name, text.replaceAll("^[()]|[()]$", "")))
                || page.reviewCredits.stream().anyMatch(review -> intersects(review.line, line)))
            return false;
        return text.matches("^\\([^()]+\\)$")
                || text.matches(
                        "(?iu)^(?:sonat[ae]|concerto|symphony|suite)\\b.*\\b(?:K|KV|BWV|RV|HWV|WoO|op)[.]?\\h*[0-9]+.*");
    }

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
        return name.matches(
                        word
                                + "(?:(?:\\h+(?:van|von|de|del|della|di|da|dos|du|la|le)){0,2}\\h+"
                                + word
                                + "){0,5}")
                && Arrays.stream(name.split("\\h+"))
                        .noneMatch(value -> NOT_TITLE.matcher(value).matches())
                && !compoundInstrumentationCaption("for " + name);
    }

    // A spatially stacked publication badge is not an unlabeled contributor.
    // Individual names and explicit performer credits are handled normally.
    private static boolean publicationAudioAccessBadge(Page page, Line line, List<Line> lines) {
        String caption = clean(line.text).toLowerCase(Locale.ROOT);
        if (line.height() > 0 && line.height() <= page.width * .04f) {
            if (playbackControlSequence(caption)) return true;
            if (caption.equals("playback")
                    && lines.stream()
                            .anyMatch(
                                    other ->
                                            playbackControlSequence(other.text)
                                                    && Math.abs(other.top - line.bottom)
                                                            < page.height * .08f
                                                    && Math.abs(other.center() - line.center())
                                                            < page.width * .2f)) return true;
        }
        if (line.height() <= 0
                || line.height() > page.width * .04f
                || !(caption.equals("audio")
                        || caption.matches("access{1,2}")
                        || caption.equals("playback")
                        || publicationIncludedCaption(caption))) return false;
        Line audio = null, access = null, included = null;
        for (Line nearby : lines) {
            if (Math.min(Math.abs(nearby.right - line.right), Math.abs(nearby.left - line.left))
                            > Math.max(line.height(), nearby.height()) * 1.5f
                    || Math.abs(nearby.top - line.top) > line.height() * 5) continue;
            String value = clean(nearby.text);
            if (value.equalsIgnoreCase("audio")) audio = nearby;
            else if (value.toLowerCase(Locale.ROOT).matches("access{1,2}")) access = nearby;
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

    /** Part designations precede the first system and identify instruments rather than works. */
    private static boolean instrumentalPartHeading(Page page, Line line) {
        if (page.notationTop <= 0
                || line.top > page.notationTop
                || line.bottom > page.notationTop + page.height * .025f) return false;
        String[] words = clean(line.text).split("\\h+");
        if (words.length != 2
                || !words[0].matches("(?iu)violino?|viola|violoncello|flauto|oboe|fagotto|corno"))
            return false;
        String part = fold(words[1]);
        return part.matches("solo|primo|secondo|i|ii|iii")
                || part.length() >= 9 && editDistance(part, "principale") <= 1;
    }

    /** Numbered work labels may sit beside, rather than above, an engraved opening staff. */
    private static JoinedTitle engravedPartWorkTitle(
            Page page, Line heading, List<Line> lines, Set<String> knownNames) {
        if (heading == null
                || page.photographicCover
                || page.notationTop <= 0
                || page.notationTop > page.height * .25f
                || heading.center() > page.width * .35f
                || knownNames.stream().anyMatch(n -> same(n, heading.text))
                || lines.stream().noneMatch(l -> instrumentalPartHeading(page, l))) return null;
        Pattern work =
                Pattern.compile(
                        "(?iu)^(concerto|sonata|sinfonia)\\h*(?:no\\.?\\h*)?([IVX]{1,6}|[1-9][0-9]?)$");
        var labels =
                lines.stream()
                        .filter(
                                l ->
                                        l != heading
                                                && work.matcher(clean(l.text)).matches()
                                                && l.top >= heading.bottom
                                                && l.top - heading.bottom < page.height * .035f
                                                && l.bottom <= page.notationTop + page.height * .06f
                                                && l.left < page.width * .2f
                                                && l.left <= heading.left + page.width * .08f
                                                && l.right >= heading.right
                                                && l.right < page.width * .5f
                                                && l.height() >= heading.height() * .8f
                                                && l.height() <= heading.height() * 2.2f)
                        .toList();
        if (labels.size() != 1) return null;
        Line label = labels.get(0);
        Matcher match = work.matcher(clean(label.text));
        if (!match.matches()) return null;
        return new JoinedTitle(
                clean(heading.text) + " " + match.group(1) + " " + match.group(2),
                List.of(heading, label));
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
                    || instrumentalPartHeading(page, l)
                    || (!confirmedHint && staffCharacterDirection(page, l, lines, limit))) continue;
            double score =
                    4 * l.height() / maximum
                            + Math.max(0, 1 - Math.abs(l.center() / page.width - .5) * 2)
                            + .2 * (1 - l.top / page.height);
            if (confirmedHint || instructionalDocumentHeading(page, l, lines)) score += 8;
            if (engravedPartWorkTitle(page, l, lines, Set.of()) != null) score += 8;
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

    /** Small chord-like readings need two separate clean chord anchors on the same row. */
    private static boolean alignedChordReviewFragment(Page page, Line line, List<Line> lines) {
        if (page.notationTop <= 0
                || line.height() <= 0
                || line.height() > page.width * .035f
                || line.right - line.left > page.width * .14f) return false;
        String chord = "[A-G](?:#|b|♯|♭)?(?:m|maj|dim|sus)?[0-9]*(?:/[A-G](?:#|b|♯|♭)?)?";
        String fragment = "(?:" + chord + "|[A-G](?:[^\\s]m[0-9]*|[$h][0-9]*))";
        if (!clean(line.text).matches(fragment + "(?:\\h+" + fragment + ")*")) return false;
        int anchors = 0;
        float left = line.left, right = line.right;
        for (Line other : lines) {
            if (other == line
                    || other.height() < line.height() * .65f
                    || other.height() > line.height() * 1.5f
                    || Math.abs(other.bottom - line.bottom) > line.height() * .6f
                    || other.left < line.right && other.right > line.left
                    || !clean(other.text).matches(chord + "(?:\\h+" + chord + ")*")) continue;
            anchors++;
            left = Math.min(left, other.left);
            right = Math.max(right, other.right);
        }
        return anchors >= 2 && right - left >= page.width * .2f;
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

    /** Educational catalogue headings plus a broad instrument inventory identify advertisements. */
    private static boolean educationalMethodCatalogue(Page page) {
        String text = page.lines.stream().map(l -> clean(l.text)).reduce("", (a, b) -> a + " " + b);
        if (!text.matches("(?isu).*\\beducational\\h+library\\b.*")
                || !text.matches("(?isu).*\\belementary\\h+methods\\b.*")
                || !text.matches("(?isu).*\\bsalient\\h+features\\b.*")) return false;
        int instruments = 0;
        for (String instrument :
                List.of(
                        "flute",
                        "clarinet",
                        "oboe",
                        "bassoon",
                        "saxophone",
                        "trumpet",
                        "violin",
                        "viola",
                        "trombone",
                        "guitar"))
            if (Pattern.compile("(?iu)\\b" + instrument + "\\b").matcher(text).find())
                instruments++;
        return instruments >= 5;
    }

    /** Dense aligned prose beneath a preface heading remains prose even across a false staff row. */
    private static boolean prefaceColumnParagraph(Page page, Line line, List<Line> lines) {
        if (page.photographicCover
                || credit(line.text) != null
                || line.height() > page.height * .025f
                || clean(line.text).split("\\h+").length < 4
                || line.right - line.left < page.width * .16f) return false;
        boolean heading =
                lines.stream()
                        .anyMatch(
                                h ->
                                        clean(h.text)
                                                        .matches(
                                                                "(?iu)^(?:preface|préface|vorwort|foreword|prefacio)$")
                                                && h.bottom <= line.top
                                                && line.top - h.bottom < page.height * .22f
                                                && h.height() > line.height() * 1.1f
                                                && Math.abs(h.center() - line.center())
                                                        < page.width * .09f);
        if (!heading) return false;
        var column =
                lines.stream()
                        .filter(
                                l ->
                                        credit(l.text) == null
                                                && Math.abs(l.left - line.left) < page.width * .03f
                                                && Math.abs(l.top - line.top) < page.height * .16f
                                                && l.height() > line.height() * .6f
                                                && l.height() < line.height() * 1.5f
                                                && l.right - l.left > page.width * .16f
                                                && clean(l.text).split("\\h+").length >= 4)
                        .sorted(Comparator.comparingDouble(l -> l.top))
                        .toList();
        int adjacent = 0;
        for (int i = 1; i < column.size(); i++) {
            Line a = column.get(i - 1), b = column.get(i);
            float gap = b.top - a.bottom;
            if (gap >= -Math.min(a.height(), b.height()) * .2f
                    && gap < Math.max(a.height(), b.height()) * 1.25f) adjacent++;
        }
        return column.contains(line) && adjacent >= 4;
    }

    private static boolean paragraphLine(Page page, Line line, List<Line> lines) {
        if (prefaceColumnParagraph(page, line, lines)) return true;
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
        String key =
                fold(
                        page.notationTop > 0
                                ? clean(line.text)
                                        .replaceFirst("(?iu)\\h+(?:[ivx]{1,4}|[1-9]|u)$", "")
                                : line.text);
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
            if (instrument.length() > key.length()
                    && instrument.endsWith(key)
                    && (key.equals(fold(line.text)) || instrument.length() == key.length() + 1))
                return true;
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
        return corroboratedCoverTitleEvidence(hint, page, lines, limit).text;
    }

    private static JoinedTitle corroboratedCoverTitleEvidence(
            String hint, Page page, List<Line> lines, float limit) {
        String target = fold(hint);
        if (target.isEmpty()) return new JoinedTitle("", List.of());
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
                    return new JoinedTitle(clean(previous.text), List.of(previous));
            }
            StringBuilder text = new StringBuilder(clean(previous.text));
            List<Line> selected = new ArrayList<>();
            selected.add(previous);
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
                selected.add(line);
                String joined = text.toString();
                if (same(hint, joined)) return new JoinedTitle(joined, List.copyOf(selected));
                if (!target.startsWith(fold(joined))) break;
                previous = line;
            }
        }
        var printed = PrintedCoverTitleEvidence.select(hint, page.width, candidates);
        return printed == null
                ? new JoinedTitle("", List.of())
                : new JoinedTitle(printed.text(), printed.lines());
    }

    /** Bare photograph bylines are retained for review without assigning a contributor role. */
    private static String normalizeRaisedOReview(String value, List<Page> pages) {
        if (!value.matches("\\p{Lu}[°º]\\p{Ll}{1,12}\\p{Lu}\\p{Ll}{2,20}")) return value;
        for (Page p : pages) {
            if (p.photographicCover
                    || p.creditsOnly
                    || p.notationTop <= 0
                    || p.lines.stream()
                            .noneMatch(
                                    l ->
                                            same(l.text, value)
                                                    && l.bottom < p.notationTop
                                                    && l.height() < p.width * .04f)) continue;
            Set<String> evidence = new LinkedHashSet<>();
            Pattern word = Pattern.compile("\\p{Ll}+[°º]\\p{Ll}+");
            for (Line l : p.lines)
                if (l.top > p.notationTop) {
                    Matcher m = word.matcher(l.text);
                    while (m.find()) evidence.add(m.group());
                }
            if (evidence.size() < 2) continue;
            String name =
                    value.replace('°', 'o')
                            .replace('º', 'o')
                            .replaceAll("(?<=\\p{Ll})(?=\\p{Lu})", " ");
            if (properReviewName(name)) return name;
        }
        return value;
    }

    private static List<Line> presentedNameLines(Page p) {
        if (p.photographicCover || p.creditsOnly) return List.of();
        List<Line> result = List.of();
        for (Line label : p.lines) {
            if (!clean(label.text).equalsIgnoreCase("present")) continue;
            for (Line first : p.lines) {
                if (!clean(first.text).matches("\\p{Lu}\\p{Ll}{2,20}")
                        || first.height() < p.width * .04f
                        || first.height() > p.width * .12f
                        || first.top < label.bottom
                        || first.top - label.bottom > label.height()
                        || Math.abs(first.center() - label.center()) > first.height() * .5f)
                    continue;
                for (Line last : p.lines) {
                    if (!clean(last.text).matches("\\p{Lu}{2,20}")
                            || last.top < first.bottom
                            || last.top - first.bottom > first.height() * .3f
                            || last.height() < first.height() * .5f
                            || last.height() > first.height() * 1.5f
                            || Math.abs(last.center() - first.center()) > first.height() * .5f)
                        continue;
                    boolean context =
                            p.lines.stream()
                                    .anyMatch(
                                            l ->
                                                    l.top >= last.bottom
                                                            && l.top - last.bottom < last.height()
                                                            && Math.abs(l.center() - last.center())
                                                                    < last.height()
                                                            && clean(l.text)
                                                                    .matches(
                                                                            "(?iu)^in\\h+a\\h+new\\h*musical\\b.*"));
                    if (!context) continue;
                    if (!result.isEmpty()) return List.of();
                    result = List.of(first, last);
                }
            }
        }
        return result;
    }

    private static List<Line> photographicBandLeader(Page page) {
        if (!page.photographicCover || page.creditsOnly || page.notationTop > 0) return List.of();
        List<Line> result = List.of();
        for (Line band : page.lines) {
            if (!clean(band.text).matches("(?iu)^&\\h+(?:his|her|their)\\h+band$")) continue;
            for (Line last : page.lines) {
                if (!clean(last.text).matches("\\p{Lu}{2,20}")
                        || last.height() < page.width * .04f
                        || Math.abs(band.top - last.bottom) > last.height() * .5f
                        || band.left < last.left
                        || band.right > last.right + last.height() * .3f) continue;
                for (Line first : page.lines) {
                    if (first == last
                            || !clean(first.text).matches("\\p{Lu}{2,20}")
                            || first.top >= last.top
                            || Math.abs(first.bottom - last.top) > last.height() * .35f
                            || first.height() < last.height() * .65f
                            || first.height() > last.height() * 1.5f
                            || Math.abs(first.center() - last.center()) > last.height() * .5f
                            || !properReviewName(first.text + " " + last.text)) continue;
                    if (!result.isEmpty()) return List.of();
                    result = List.of(first, last, band);
                }
            }
        }
        return result;
    }

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
        return String.join(
                " ",
                photographicStackedTitleLines(page, heading, lines, limit).stream()
                        .map(line -> titlePunctuation(line.text))
                        .toList());
    }

    private static List<Line> photographicStackedTitleLines(
            Page page, Line heading, List<Line> lines, float limit) {
        if (!page.photographicCover
                || page.creditsOnly
                || heading.height() < page.width * .06f
                || heading.right - heading.left < page.width * .2f) return List.of();
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
            if (candidates.size() > 1 && candidates.get(1).top < next.bottom) return List.of();
            group.add(next);
            prior = next;
        }
        return group.size() < 2 ? List.of() : List.copyOf(group);
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

    /** A typed version subtitle is work evidence; a protected name remains review evidence. */
    private static Line nearbyVersionTitleCaption(
            Page page, Line heading, List<Line> lines, float limit, Set<String> knownNames) {
        if (heading == null
                || page.creditsOnly
                || knownNames.stream().anyMatch(name -> same(name, heading.text))) return null;
        List<Line> candidates =
                lines.stream()
                        .filter(
                                line ->
                                        line != heading
                                                && versionCaptionText(line.text)
                                                && line.top < limit
                                                && line.top
                                                        >= heading.bottom - heading.height() * .3f
                                                && line.top - heading.bottom
                                                        < heading.height() * 1.2f
                                                && line.height() >= heading.height() * .4f
                                                && line.height() <= heading.height()
                                                && Math.abs(line.center() - heading.center())
                                                        < heading.height() * .8f
                                                && line.right - line.left
                                                        < (heading.right - heading.left) * 2.5f
                                                && knownNames.stream()
                                                        .noneMatch(name -> same(name, line.text))
                                                && page.reviewCredits.stream()
                                                        .noneMatch(
                                                                review ->
                                                                        intersects(
                                                                                        review.line,
                                                                                        line)
                                                                                || intersects(
                                                                                        review.line,
                                                                                        heading)))
                        .toList();
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static boolean intersects(Line first, Line second) {
        return Math.min(first.right, second.right) > Math.max(first.left, second.left)
                && Math.min(first.bottom, second.bottom) > Math.max(first.top, second.top);
    }

    private static boolean versionCaptionText(String text) {
        return clean(text)
                .matches(
                        "(?iu)^(?:(?:live|concert)\\h+performance|solo|easy|original|piano|violin|guitar|tavern|epic|wedding|concert)\\h+version(?:\\h+[0-9]+)?$");
    }

    private static boolean containsVersionTitleCaption(String title, Line heading, Line caption) {
        return heading != null
                && caption != null
                && containsPrintedPhrase(title, heading.text)
                && containsPrintedPhrase(title, caption.text);
    }

    private record JoinedTitle(String text, List<Line> lines) {}

    private static String joinTitle(
            Page page,
            Line heading,
            List<Line> lines,
            float limit,
            boolean openingPage,
            Set<String> knownNames) {
        return joinTitleEvidence(page, heading, lines, limit, openingPage, knownNames).text;
    }

    private static JoinedTitle joinTitleEvidence(
            Page page,
            Line heading,
            List<Line> lines,
            float limit,
            boolean openingPage,
            Set<String> knownNames) {
        JoinedTitle engraved =
                openingPage ? engravedPartWorkTitle(page, heading, lines, knownNames) : null;
        if (engraved != null) return engraved;
        Line ownedVersionCaption =
                openingPage
                        ? nearbyVersionTitleCaption(page, heading, lines, limit, knownNames)
                        : null;
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
            boolean versionCaption = l.equals(ownedVersionCaption);
            boolean closeSourceCaption =
                    openingPage
                            && motionPictureConnector(caption).equals("from")
                            && nearbyMotionPictureSourceCaptions(page, heading).size() == 1
                            && !competingSourceCaptionHeading(page, heading, l)
                            && nearbySourceCaptionGeometry(page, heading, l);
            boolean sourceCaption =
                    captionWords >= 2
                            && captionWords <= 12
                            && (caption.matches(
                                            "(?iu).*\\b(?:soundtrack|opening\\h+[0-9]+|suite\\h+from\\h+the\\h+motion\\h+picture)$")
                                    || captionWords >= 3 && caption.matches("(?iu).+['’]s\\h+.+")
                                    || versionCaption)
                            && l.top
                                    >= heading.bottom
                                            - heading.height() * (closeSourceCaption ? .35f : .3f)
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
        return new JoinedTitle(
                enclosingQuotes.matches() ? enclosingQuotes.group(1) : joined, List.copyOf(title));
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
                                "(?iu)(?<!\\p{L})(?:music\\h+by|lyrics?\\h+by|words\\h+by|arranged\\h+by|performed\\h+by)\\b[\\h.:=–—-]*")
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
                                        || label.startsWith("lyric")
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
        // Only an empty label can refer to a name printed above it. An inline
        // name remains a prefix attribution and is eligible for source rereads.
        return new Credit(
                role,
                attributedValue,
                attributedValue.isBlank()
                        && (label.equals("arrangement") || label.equals("arrangements")));
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

    /** Context separates navigation, prose and notation labels from unlabelled bylines. */
    /** Repeated tempo terms plus aligned prose identify a notation glossary. */

    /** Small instructions can precede an opening staff the page detector missed. */
    private static boolean anchoredPerformanceInstruction(Page page, Line line, List<Line> lines) {
        if (page.photographicCover
                || page.notationTop <= 0
                || line.height() > page.height * .025f
                || line.center() > page.width * .7f
                || credit(line.text) != null) return false;
        String text = clean(line.text);
        if (line.bottom >= page.notationTop - page.height * .065f
                && text.matches(
                        "(?iu)^(?:[mpfM]{1,3}\\h+)?with\\h+(?:driving|gentle|quiet|great|restrained)\\h+(?:intensity|energy|expression)$"))
            return true;
        boolean instruction =
                text.matches("(?iu)^(?:intro|verse|chorus|bridge|outro)(?:\\h+[0-9]+)?$")
                        || text.matches(
                                "(?iu)^solo\\h*[-–]\\h*[0-9]+(?:st|nd|rd|th)\\h+time,\\h*all\\h*[-–]\\h*[0-9]+(?:st|nd|rd|th)\\h+time$")
                        || text.matches("(?iu)^si\\h+deve\\h+suonare\\h+.+");
        if (!instruction) return false;
        for (Line tempo : lines) {
            if (tempo == line
                    || tempo.height() > page.height * .025f
                    || tempo.bottom > line.top
                    || line.top - tempo.bottom > Math.max(tempo.height(), line.height())
                    || Math.abs(tempo.left - line.left)
                            > Math.max(tempo.height(), line.height()) * 2) continue;
            if (clean(tempo.text)
                    .matches(
                            "(?iu)^(?:adagio|andante|allegro|allegretto|largo|lento|presto|moderately)(?:\\h+.*|[-=]\\h*[0-9]+)?$"))
                return true;
        }
        return false;
    }

    private static boolean instructionGlossaryLine(Page page, Line line, List<Line> lines) {
        if (page.photographicCover || page.notationTop > 0 || line.height() > page.height * .025f)
            return false;
        String text = clean(line.text);
        if (!text.matches("(?iu).*\\b(?:play|notes|bow|slow|fast|loud|expressive)\\b.*")
                || text.split("\\h+").length < 3) return false;
        List<Line> terms =
                lines.stream()
                        .filter(
                                l ->
                                        clean(l.text)
                                                        .matches(
                                                                "(?iu)^(?:adagio|andante|allegro|largo|lento|presto|moderato)$")
                                                && l.height() < page.height * .03f)
                        .toList();
        if (terms.stream().map(l -> fold(l.text)).distinct().count() < 4) return false;
        return terms.stream()
                        .anyMatch(
                                term ->
                                        line.left > term.right
                                                && line.left - term.right < page.width * .15f
                                                && Math.abs(line.top - term.top)
                                                        < Math.max(line.height(), term.height())
                                                                * 1.3f)
                || text.matches("(?iu)^.{3,24};\\h*.*\\b(?:play|notes|bow)\\b.*$");
    }

    private static void discardRepeatedRoleSpellingEcho(
            Set<String> review,
            List<Page> pages,
            Set<String> artists,
            Set<String> composers,
            Set<String> arrangers) {
        discardRepeatedRoleSpellingEcho(review, pages, artists, composers, arrangers, Set.of());
    }

    private static void discardRepeatedRoleSpellingEcho(
            Set<String> review,
            List<Page> pages,
            Set<String> artists,
            Set<String> composers,
            Set<String> arrangers,
            Set<String> repeatedInferredNames) {
        Set<String> assigned = new LinkedHashSet<>();
        assigned.addAll(artists);
        assigned.addAll(composers);
        assigned.addAll(arrangers);
        for (String candidate : List.copyOf(review)) {
            if (!properReviewName(candidate) || candidate.split("\\h+").length < 2) continue;
            List<String> close =
                    assigned.stream()
                            .filter(
                                    name ->
                                            properReviewName(name)
                                                    && name.split("\\h+").length
                                                            == candidate.split("\\h+").length
                                                    && fold(name).length() >= 10
                                                    && editDistance(fold(name), fold(candidate))
                                                            == 1)
                            .toList();
            if (close.size() != 1) continue;
            String name = close.get(0);
            int evidencePages = 0;
            for (Page page : pages)
                if (page.lines.stream()
                        .anyMatch(
                                line -> {
                                    Credit role = credit(line.text);
                                    return repeatedInferredNames.contains(name)
                                                    && same(line.text, name)
                                            || role != null
                                                    && role.role != Role.LYRICS
                                                    && creditNames(role.value).stream()
                                                            .anyMatch(value -> same(value, name));
                                })) evidencePages++;
            if (evidencePages >= 2) review.remove(candidate);
        }
    }

    private static boolean layoutReviewCaption(
            Page page, Line heading, Line line, List<Line> lines) {
        String text = clean(line.text);
        if (text.matches("(?iu)^(?:theme\\h+from|sel[eEfF]ctions\\h+from)$")) return true;
        if (text.matches("(?iu)^strum\\h+builder\\h+[0-9]+$")
                && instructionalDocumentHeading(page, heading, lines)) return true;
        if (!page.photographicCover
                && page.notationTop > 0
                && line.height() < page.height * .025f
                && line.bottom >= page.notationTop - page.height * .065f
                && line.top < page.notationTop + page.height * .025f
                && text.matches("(?iu)^(?:click|[S▶►]\\h+play)$")) return true;

        if (!page.photographicCover
                && line.left < page.width * .15f
                && line.height() < page.height * .02f
                && lines.stream()
                        .anyMatch(
                                other ->
                                        other != line
                                                && other.top > line.bottom
                                                && other.top - line.bottom < page.height * .12f
                                                && Math.abs(other.left - line.left)
                                                        < page.width * .025f
                                                && clean(other.text)
                                                        .matches(
                                                                "(?iu)^(?:flute|violin|viola|cello|clarinet|oboe|piano|organ)$")
                                                && editDistance(
                                                                text.toLowerCase(Locale.ROOT),
                                                                clean(other.text)
                                                                        .toLowerCase(Locale.ROOT))
                                                        == 1)) return true;
        if (text.matches("(?iu)^[*]+arranged$")
                && lines.stream()
                        .anyMatch(
                                other ->
                                        other != line
                                                && clean(other.text).matches("(?iu)^by\\h+.+")
                                                && other.top >= line.bottom
                                                && other.top - line.bottom < line.height() * 2
                                                && Math.abs(other.left - line.left)
                                                        < line.height() * 2)) return true;

        if (text.matches("^[\"“].+[\"”]$")
                && lines.stream()
                        .anyMatch(
                                other ->
                                        other != line
                                                && other.top >= line.bottom
                                                && other.top - line.bottom < line.height()
                                                && Math.abs(other.center() - line.center())
                                                        < line.height() * 2
                                                && clean(other.text)
                                                        .matches(
                                                                "(?iu)^(?:written|composed)\\h+by\\h+.+")))
            return true;
        if (page.notationTop > 0
                && !page.photographicCover
                && line.top >= heading.bottom
                && line.bottom < page.notationTop
                && line.height() < heading.height() * .85f
                && line.top - heading.bottom < heading.height() * 1.5f
                && Math.abs(line.center() - heading.center()) < heading.height()
                && text.matches("(?iu)^.+\\b(?:the\\h+movie|the\\h+film|television\\h+series)$"))
            return true;
        if (!page.photographicCover
                && page.notationTop > 0
                && line.left < page.width * .4f
                && line.height() < page.height * .02f
                && line.bottom >= page.notationTop - page.height * .065f
                && line.top < page.notationTop + page.height * .025f) {
            if (text.equalsIgnoreCase("prepare")) return true;
            if (text.matches("(?iu)^.{0,35}\\bsustained,\\h+.+")
                    && lines.stream()
                            .anyMatch(
                                    other ->
                                            other != line
                                                    && other.bottom <= line.top
                                                    && line.top - other.bottom < line.height()
                                                    && Math.abs(other.left - line.left)
                                                            < line.height() * 2
                                                    && clean(other.text)
                                                            .matches(".*[=♩]\\h*[0-9]{2,3}.*")))
                return true;
        }
        if (text.matches("(?iu)^in.{0,12}\\bmusical\\h+p[l\\[]ay$")
                && Math.abs(line.center() - page.width * .5f) < page.width * .12f) return true;
        if (text.matches("(?iu)^re[cç]ords$")
                && lines.stream()
                        .anyMatch(
                                other ->
                                        other != line
                                                && clean(other.text)
                                                        .matches(
                                                                "(?iu)^recorded\\h+by\\h+.+\\h+on\\h+.+\\h+records$")
                                                && intersects(other, line))) return true;
        if (text.equalsIgnoreCase("present")
                && line.height() < heading.height()
                && lines.stream()
                        .anyMatch(
                                other ->
                                        other != line
                                                && other.bottom <= line.top
                                                && line.top - other.bottom < line.height()
                                                && Math.abs(other.center() - line.center())
                                                        < page.width * .1f
                                                && clean(other.text).contains("&"))) return true;
        if (text.matches("(?iu)^s[-.]gt$")
                && line.left < page.width * .15f
                && line.height() < page.height * .03f) {
            for (Line t : lines)
                if (clean(t.text).equals("T")
                        && t.left > line.right
                        && t.left < page.width * .3f
                        && Math.abs(t.top - line.top) < line.height() * 2)
                    for (Line aa : lines)
                        if (clean(aa.text).equals("A")
                                && aa.top > t.top
                                && aa.top - t.top < t.height() * 2
                                && Math.abs(aa.left - t.left) < t.height())
                            for (Line bb : lines)
                                if (clean(bb.text).equals("B")
                                        && bb.top > aa.top
                                        && bb.top - aa.top < aa.height() * 2
                                        && Math.abs(bb.left - aa.left) < aa.height()) return true;
        }
        for (Line publisher : lines) {
            String p = clean(publisher.text);
            if (!p.matches(
                    "(?iu)^(?:adp\\.?|adapted)\\h+from\\h+.+\\b(?:co\\.?|inc\\.?|ltd\\.?|publishing|publishers)\\b.*$"))
                continue;
            if (line == publisher) return true;
            if (line.top >= publisher.bottom
                    && line.top - publisher.bottom < publisher.height() * 2
                    && Math.abs(line.left - publisher.left) < publisher.height() * 2
                    && text.matches(
                            "(?iu)^.*\\bseries\\h+of\\h+.+\\b(?:compositions|pieces|works)$"))
                return true;
        }
        return false;
    }

    private static boolean unlabelledReviewNoise(
            Page page, Line heading, Line line, List<Line> lines) {
        String text = clean(line.text);
        if (scoreFormatCaption(text)) return true;
        if (countedInstrumentAudioCaption(text)) return true;
        if (layoutReviewCaption(page, heading, line, lines)) return true;
        if (!page.photographicCover
                && line.left < page.width * .15f
                && line.height() < page.height * .02f
                && text.matches(
                        "(?iu)^(?:v[iIl]n[.]|vla[.]|vc[.]|fl[.]|cl[.]|ob[.]|bsn[.]|pno[.]|violin|viola|cello|flute|clarinet|oboe|piano|organ)(?:\\h+(?:[1-9]|I{1,3}))?$")
                && lines.stream()
                        .anyMatch(
                                other ->
                                        other != line
                                                && same(other.text, text)
                                                && Math.abs(other.left - line.left)
                                                        < page.width * .025f
                                                && other.height() < page.height * .02f
                                                && Math.abs(other.top - line.top)
                                                        > page.height * .08f)) return true;

        if (instructionGlossaryLine(page, line, lines)) return true;
        if (anchoredPerformanceInstruction(page, line, lines)) return true;
        if (page.notationTop > 0
                && line.height() < page.height * .025f
                && line.bottom >= page.notationTop - page.height * .08f
                && line.center() < page.width * .7f
                && text.matches(
                        "(?iu)^(?:[*]{0,3}muffled,?\\h+with\\h+distortion|count\\h+(?:[0-9]+|one|two|three|four|five|six|seven|eight)\\h+beats?(?:\\h+if)?|solo\\h*[-–]\\h*[0-9]+(?:st|nd|rd|th)\\h+time,\\h*all\\h*[-–]\\h*[0-9]+(?:st|nd|rd|th)\\h+time)$"))
            return true;

        if (page.notationTop > 0
                && line.center() < page.width * .4f
                && line.height() < page.height * .025f
                && line.bottom >= page.notationTop - page.height * .12f
                && text.matches("(?iu)^(?:rock(?:\\h+swing)?|ballad|waltz|fanfare)$")) return true;
        if (medleyContentsSubtitle(page, heading, line, lines)) return true;
        if (paragraphLine(page, line, lines) || collectionListEntry(page, line, lines)) return true;
        if (MOVEMENT_SUBTITLE.matcher(text).matches()) return true;
        if (text.matches(
                "(?iu)^(?:track\\h*[0-9]*|demo\\h+track|the\\h+complete\\h+album|merged\\h+(?:treble|bass)\\h+staves)$"))
            return true;
        if (page.notationTop > 0
                && line.height() < heading.height() * .85f
                && notationReviewLabel(page, line)) return true;
        if (text.indexOf('•') >= 0 && text.split("•").length >= 3) return true;
        if (text.matches("(?iu)^tuning\\h*[:=].*")) return true;
        if (text.matches(
                "(?iu)^\\(?from\\h+the\\h+(?:film|movie|netflix\\h+series|series|musical)\\b.*"))
            return true;
        boolean nearStaff =
                page.notationTop > 0 && line.bottom >= page.notationTop - page.height * .065f;
        if (nearStaff && notationReviewLabel(page, line)) return true;
        if (nearStaff
                && text.matches(
                        "(?iu)^(?:(?:pre[- ]?)?(?:intro|verse|chorus|bridge|outro)(?:\\h+[0-9]+)?|bouncy|misterioso|sostenuto|molto\\h+rubato|(?:mp|mf|p|f)\\h+(?:cresc\\.?|dim\\.?)(?:\\h+.*)?|piano\\h*[-–]\\h*merged\\h+staves|s[-.]gt)$"))
            return true;
        if (nearStaff
                && text.matches(
                        "(?iu)^[A-G][#♯b♭]?(?:(?:maj|min|dim|aug|sus|add|m)[0-9]*)?(?:[0-9]+)?(?:/[A-G][#♯b♭]?)?$"))
            return true;
        for (Line label : lines) {
            String key = clean(label.text);
            if (key.matches("(?iu)^(?:contents|table\\h+of\\h+contents)$")
                    && line.top >= label.top) {
                if (text.matches(
                        "(?iu)^(?:contents|table\\h+of\\h+contents|title|page(?:\\h+number)?)$"))
                    return true;
                for (Line number : lines)
                    if (clean(number.text).matches("[0-9]{1,3}")
                            && number.left > line.right
                            && Math.abs(number.top - line.top)
                                    < Math.max(number.height(), line.height())) return true;
            }
            if (key.matches("(?iu)^(?:postboks|p\\.?o\\.?\\h+box)\\h+[0-9]+$")
                    && Math.abs(line.center() - label.center()) < page.width * .12f
                    && Math.abs(line.top - label.top) < line.height() * 3) {
                if (text.matches("(?iu).*(?:bibliotek|library)$")
                        || text.matches("[0-9]{4,6}\\h+.*")
                        || line == label) return true;
            }
        }
        return false;
    }

    /** An original author block needs work evidence or a separate aligned arrangement label. */

    /** Resolve a split adaptation credit consistently during extraction and inference. */
    private static Line adaptedArrangementPrelude(Line line, List<Line> lines) {
        if (!clean(line.text).matches("(?iu)^by\\h+.+")) return null;
        for (Line prior : lines)
            if (clean(prior.text)
                            .matches(
                                    "(?iu)^(?:adp\\.?|adapted)\\h+from\\h+(?:the\\h+)?arrangement$")
                    && line.top >= prior.bottom
                    && line.top - prior.bottom < prior.height() * 1.5f
                    && Math.abs(line.left - prior.left) < prior.height()) return prior;
        return null;
    }

    private static boolean contextualOriginalAuthor(
            Page page, Line heading, Line name, List<Line> lines) {
        String text =
                clean(name.text)
                        .replaceFirst("(?iu)^(?:and|&)\\h+", "")
                        .replaceFirst("\\h*\\((?:1[5-9]|20)[0-9]{2}\\)\\h*$", "");
        if (page.photographicCover
                || page.notationTop <= 0
                || page.creditsOnly
                        && (heading.height() < page.width * .02f
                                || heading.top > page.height * .2f
                                || heading.bottom > page.notationTop * .8f)
                || name.bottom >= page.notationTop
                || name.top < heading.top
                || name.top < heading.bottom
                        && name.left
                                < heading.right - Math.min(name.height(), heading.height()) * .6f
                || contextualAuthorNames(text).isEmpty()
                || same(text, heading.text)
                || text.matches("(?iu)^traditional\\b.*")
                || text.matches(
                        "(?iu)^(?:latin|english|french|german|italian|spanish|irish|scottish|welsh)\\h+(?:hymn|carol|folk\\h+(?:song|tune))$")
                || text.matches(
                        "(?iu).*\\b(?:from|theme|movement|piano|violin|solo|film|movie|series|produced)\\b.*"))
            return false;
        for (Line context : lines) {
            if (context.top >= page.notationTop
                    || context.bottom > page.notationTop + page.height * .025f
                    || context.top < heading.top) continue;
            String value = clean(context.text);
            if (name.center() > page.width * .55f
                    && value.matches(
                            "(?iu).*(?:\\bop(?:us)?\\.?|\\bWoO|\\bBWV|\\bK(?:V)?|\\bRV|\\bHWV)[.]?\\h*[0-9]+.*"))
                return true;

            if (context == heading
                    && name.center() > page.width * .55f
                    && value.matches(
                            "(?iu)^(?:sonata|symphony|concerto|suite)\\h+(?:no[.]?|number)\\h*[0-9]+(?:\\h+in\\h+[A-G](?:[- ]?(?:flat|sharp))?\\h+(?:major|minor))?$")
                    && lines.stream()
                            .noneMatch(
                                    other ->
                                            other != name
                                                    && other.bottom < page.notationTop
                                                    && credit(other.text) != null
                                                    && credit(other.text).role == Role.COMPOSER
                                                    && adaptedArrangementPrelude(other, lines)
                                                            == null)) return true;

            if (name.center() < page.width * .35f
                    && text.split("\\h+").length >= 2
                    && context.center() > page.width * .65f
                    && Math.abs(context.top - name.top)
                            < Math.max(context.height(), name.height()) * 1.25f
                    && value.matches(
                            "(?iu)^(?:arranged\\h+by|arr[.]?|(?:and\\h+the\\h+)?adaptation\\h+by)\\h+.+")
                    && lines.stream()
                            .noneMatch(
                                    other ->
                                            other.top >= heading.top
                                                    && other.bottom < page.notationTop
                                                    && credit(other.text) != null
                                                    && credit(other.text).role == Role.COMPOSER
                                                    && adaptedArrangementPrelude(other, lines)
                                                            == null)
                    && lines.stream()
                            .noneMatch(
                                    other ->
                                            other != context
                                                    && other != name
                                                    && other.center() > page.width * .55f
                                                    && other.top >= heading.bottom
                                                    && other.bottom < page.notationTop
                                                    && other.height() < heading.height()
                                                    && credit(other.text) == null
                                                    && !contextualAuthorNames(clean(other.text))
                                                            .isEmpty()
                                                    && !notationReviewLabel(page, other)))
                return true;
            Credit role = credit(value);
            if (role != null
                    && role.role == Role.ARRANGER
                    && context != name
                    && !value.matches("(?iu)^(?:transcribed|transcription|transcr\\.?)\\b.*")
                    && name.center() > page.width * .55f
                    && ((context.top
                                            >= name.bottom
                                                    - Math.min(context.height(), name.height())
                                                            * .2f
                                    && context.top - name.bottom < page.height * .07f
                                    && Math.abs(context.right - name.right) < page.width * .12f)
                            || (context.center() < page.width * .45f
                                    && Math.abs(context.top - name.top)
                                            < Math.max(context.height(), name.height()) * 1.5f)))
                return true;
        }
        return false;
    }

    /** A numbered movement name needs a separate larger work heading above it. */
    private static boolean numberedMovementSubtitle(Page page, Line heading, Line line) {
        if (page.notationTop <= 0
                || heading == null
                || heading == line
                || line.top < heading.bottom
                || line.bottom >= page.notationTop
                || line.top - heading.bottom > page.height * .06f
                || line.height() < heading.height() * .5f
                || line.height() > heading.height() * .9f
                || Math.abs(line.center() - heading.center()) > page.width * .12f) return false;
        return clean(line.text)
                        .matches(
                                "(?iu)^[IVX]{1,5}[.]\\h+(?:alborada|allegro|allegretto|andante|andantino|adagio|scherzo|rondo|minuet|menuetto|largo|presto|finale)(?:\\h+(?:molto|moderato|con|brio|ma|non|troppo|vivace|cantabile))*$")
                && clean(heading.text)
                        .matches(
                                "(?iu)^(?:capriccio|concerto|symphony|sonata|suite|quartet|quintet|sextet|overture)\\b.*");
    }

    /** Small labels beside the staff describe instrumentation and execution. */
    private static boolean notationReviewLabel(Page page, Line line) {
        if (line.height() > page.height * .035f) return false;
        String text =
                clean(line.text)
                        .replaceFirst("(?iu)^(?:p{1,3}|mp|mf|f{1,3}|V)\\h+", "")
                        .replaceAll("^[|\\[\\](]+|[|\\[\\]).]+$", "")
                        .strip();
        if (text.matches(
                "(?iu)^(?:[A-G]\\h+string|solo|solo\\h+violin|vocal\\h+parts|guitar/celli|accent;\\h*play\\h+forcefully)$"))
            return true;
        if (text.matches("(?iu)^\\(?cymbal\\)?\\h+\\(?voice\\)?$")) return true;
        String instrument =
                "(?:violin|viola|piano|organ|guitar|cello|flute|clarinet|oboe|bassoon|timpani|brass|synth\\.?|harp|voice|cymbal|bass|drum|electric\\h+piano|glockenspiel|harpsichord|mandolin|vibes|vin\\.)";
        if (text.matches(
                "(?iu)^"
                        + instrument
                        + "(?:\\h*(?:[0-9]+|I{1,3}|solo))?(?:\\h*(?:&|and)\\h*"
                        + instrument
                        + ")?$")) return true;
        if (text.matches("(?iu)^(?:first|second|third|fourth|[1-9](?:st|nd|rd|th))\\h+movement$"))
            return true;
        String word =
                "(?:vivace|misterioso|cresc[.,]?|dim[.,]?|a|lente|grazioso|sempre|mosso|tempo|simply|soul|groove|swing|affettuoso|agitato|animato|legato|espress\\.?|light|and|lively|meno|mezzo|movimento|tranquillo|piz\\.?|port\\.?|sostemuto|sosteńuto|accel\\.?|andante|andantino|allegro|allegretto|adagio|largo|lento|lent|moderato|presto|espressivo|expressively|gently|passionately|sustained|[ec]spressivo|lacrimoso|molto|poco|cantabile|rubato|sostenuto|briskly|delicately|douloureux|con|moto|e|et|free|time)";
        return text.matches(
                "(?iu)^(?:[0-9]+\\h*)?"
                        + word
                        + "(?:[\\h,;]+"
                        + word
                        + ")*(?:\\h*[Jj♩♪=]*\\h*[0-9]+)?$");
    }

    private record RepeatedCredit(String work, Role role, Set<String> names) {}

    /** A missing internal letter is repairable only inside a repeated same-work coauthor block. */
    private static void repairRepeatedCreditDrops(
            Set<String> names, Role role, List<RepeatedCredit> evidence) {
        Set<String> remove = new LinkedHashSet<>();
        for (String shortName : names) {
            List<RepeatedCredit> occurrences =
                    evidence.stream()
                            .filter(e -> e.role == role && e.names.contains(shortName))
                            .toList();
            if (occurrences.isEmpty()) continue;
            Set<String> possible = new LinkedHashSet<>();
            for (String fullName : names) {
                if (!internalLetterDrop(shortName, fullName)) continue;
                boolean everyOccurrence = true;
                for (RepeatedCredit occurrence : occurrences) {
                    if (occurrence.work.isEmpty() || occurrence.names.size() < 2) {
                        everyOccurrence = false;
                        break;
                    }
                    Set<String> repaired = new LinkedHashSet<>(occurrence.names);
                    repaired.remove(shortName);
                    repaired.add(fullName);
                    if (repaired.size() != occurrence.names.size()
                            || evidence.stream()
                                    .noneMatch(
                                            e ->
                                                    e.role == role
                                                            && same(e.work, occurrence.work)
                                                            && e.names.equals(repaired))) {
                        everyOccurrence = false;
                        break;
                    }
                }
                if (everyOccurrence) possible.add(fullName);
            }
            if (possible.size() == 1) remove.add(shortName);
        }
        names.removeAll(remove);
    }

    private static boolean internalLetterDrop(String shorter, String longer) {
        String a = fold(shorter), b = fold(longer);
        if (a.length() < 7 || b.length() != a.length() + 1) return false;
        for (int i = 1; i < b.length() - 1; i++)
            if (Character.isLetter(b.charAt(i))
                    && (b.substring(0, i) + b.substring(i + 1)).equals(a)) return true;
        return false;
    }

    /** Collection captions and aligned contents columns describe works, not contributors. */

    /** A repeated column of page-numbered entries identifies contents independently of OCR labels. */
    private static boolean numberedContentsEntry(Page page, Line candidate, List<Line> lines) {
        if (page.photographicCover
                || candidate.top < page.height * .2f
                || candidate.bottom > page.height * .8f
                || candidate.height() > page.height * .03f
                || credit(candidate.text) != null
                || !clean(candidate.text).matches(".*\\p{L}{2}.*")) return false;
        boolean playback = lines.stream().anyMatch(l -> playbackControlSequence(l.text));
        if (page.notationTop > 0 && !playback) return false;
        List<Line> column =
                lines.stream()
                        .filter(
                                l ->
                                        l.top > page.height * .2f
                                                && l.bottom < page.height * .8f
                                                && l.height() >= candidate.height() * .6f
                                                && l.height() <= candidate.height() * 1.5f
                                                && credit(l.text) == null
                                                && clean(l.text).matches(".*\\p{L}{2}.*")
                                                && Math.abs(l.left - candidate.left)
                                                        < page.width * .035f)
                        .toList();
        int numbered = 0;
        for (Line row : column) {
            if (clean(row.text).matches("^[0-9]{1,3}\\h+\\p{L}.*")) {
                numbered++;
                continue;
            }
            if (lines.stream()
                    .anyMatch(
                            n ->
                                    clean(n.text).matches("^[.·…]*[0-9]{1,3}$")
                                            && Math.abs(
                                                            (n.top
                                                                            + n.bottom
                                                                            - row.top
                                                                            - row.bottom)
                                                                    * .5f)
                                                    < Math.max(n.height(), row.height()) * .65f
                                            && (n.right <= row.left
                                                            && row.left - n.right
                                                                    < page.width * .09f
                                                    || n.left >= row.right
                                                            && n.left - row.right
                                                                    < page.width * .4f)))
                numbered++;
        }
        return column.contains(candidate) && numbered >= 4;
    }

    /** A centered item list beneath a medley heading is work content, above a separate byline. */
    private static boolean medleyContentsSubtitle(
            Page page, Line heading, Line line, List<Line> lines) {
        String text = clean(line.text);
        if (page.notationTop <= 0
                || !clean(heading.text).matches("(?iu).*\\bmedley\\b.*")
                || line.top < heading.bottom
                || line.top - heading.bottom > page.height * .05f
                || line.height() > heading.height() * .7f
                || Math.abs(line.center() - heading.center()) > page.width * .08f
                || credit(text) != null
                || text.split("\\h+").length < 5
                || !(text.indexOf('•') >= 0 || text.chars().filter(c -> c == ',').count() >= 2))
            return false;
        return lines.stream()
                .anyMatch(
                        l ->
                                l.top >= line.bottom
                                        && l.bottom < page.notationTop
                                        && l.top - line.bottom < page.height * .06f
                                        && l.height() < heading.height() * .7f
                                        && (credit(l.text) != null || properReviewName(l.text)));
    }

    private static boolean collectionListEntry(Page page, Line candidate, List<Line> lines) {
        String text = clean(candidate.text);
        if (numberedContentsEntry(page, candidate, lines)) return true;
        // A printed playback panel plus a repeated title column identifies collection front matter,
        // even when the page detector mistakes footer rules for a staff.
        if ((page.notationTop <= 0 || page.notationTop > page.height * .75f)
                && candidate.top > page.height * .2f
                && candidate.bottom < page.height * .72f
                && candidate.height() < page.height * .03f
                && credit(text) == null
                && lines.stream()
                        .anyMatch(
                                l ->
                                        l.top < page.height * .25f
                                                && playbackControlSequence(l.text))) {
            long aligned =
                    lines.stream()
                            .filter(
                                    l ->
                                            l.top > page.height * .2f
                                                    && l.bottom < page.height * .72f
                                                    && l.height() < page.height * .03f
                                                    && credit(l.text) == null
                                                    && clean(l.text).matches(".*\\p{L}.*")
                                                    && Math.abs(l.left - candidate.left)
                                                            < page.width * .06f)
                            .count();
            if (aligned >= 5) return true;
        }

        if (page.notationTop <= 0 && credit(candidate.text) == null) {
            for (Line caption : lines) {
                if (!clean(caption.text)
                        .matches(
                                "(?iu)^p(?:la|a)y\\h*[0-9&]?.*\\b(?:favorites|selections|solos|themes|tunes|songs|pieces)\\b.*sound[- ]alike\\b.*"))
                    continue;
                if (candidate == caption) return true;
                if (candidate.top < caption.bottom
                        || candidate.bottom > page.height * .88f
                        || candidate.height() > page.height * .03f
                        || candidate.right - candidate.left > page.width * .4f) continue;
                long entries =
                        lines.stream()
                                .filter(
                                        l ->
                                                l.top >= caption.bottom
                                                        && l.bottom <= page.height * .88f
                                                        && l.height() <= page.height * .03f
                                                        && l.right - l.left <= page.width * .4f
                                                        && credit(l.text) == null
                                                        && (Math.abs(l.right - candidate.right)
                                                                        < page.width * .025f
                                                                || Math.abs(l.left - candidate.left)
                                                                        < page.width * .025f))
                                .count();
                if (entries >= 5) {
                    boolean pairedCapitalCredit =
                            clean(candidate.text)
                                            .equals(clean(candidate.text).toUpperCase(Locale.ROOT))
                                    && lines.stream()
                                            .anyMatch(
                                                    previous ->
                                                            previous != candidate
                                                                    && previous.top
                                                                            >= caption.bottom
                                                                    && previous.top < candidate.top
                                                                    && Math.abs(
                                                                                    previous.right
                                                                                            - candidate
                                                                                                    .right)
                                                                            < page.width * .025f
                                                                    && candidate.top
                                                                                    - previous.bottom
                                                                            < Math.max(
                                                                                            previous
                                                                                                    .height(),
                                                                                            candidate
                                                                                                    .height())
                                                                                    * .4f
                                                                    && !clean(previous.text)
                                                                            .equals(
                                                                                    clean(
                                                                                                    previous.text)
                                                                                            .toUpperCase(
                                                                                                    Locale
                                                                                                            .ROOT)));
                    if (!pairedCapitalCredit) return true;
                }
            }
        }

        Pattern collectionCaption =
                Pattern.compile(
                        "(?iu)^(?:solo\\h+arrangements\\h+of\\h+[0-9]+\\h+.*(?:songs|hits)\\h+including:?|[0-9]+\\h+solo\\h+arrangements\\h+with\\h+.*accompaniment|musi[ce]\\h+from\\h+the\\h+motion\\h+picture\\h+soundtrack|includes\\h+[0-9]+\\h+(?:hit\\h+)?songs\\h+from\\h+the\\h+soundtrack(?::.*)?|[0-9]+\\h+themes\\h+from\\h+the\\h+(?:game|series|film)|[0-9]+\\h+of\\h+(?:the|her|his|their)\\h+(?:biggest|greatest|favorite)\\h+hits(?::.*)?)$");
        if (collectionCaption.matcher(text).matches()) return true;
        for (Line label : lines)
            if (collectionCaption.matcher(clean(label.text)).matches()
                    && candidate.top >= label.bottom
                    && candidate.top - label.bottom < page.height * .065f
                    && candidate.height() < label.height() * 1.6f
                    && (Math.abs(candidate.center() - label.center()) < page.width * .15f
                            || candidate.text.indexOf('•') >= 0
                                    && candidate.left >= page.width * .2f
                                    && candidate.right <= page.width * .98f)) return true;
        if (page.notationTop > 0) return false;
        boolean heading =
                lines.stream()
                        .anyMatch(
                                l ->
                                        clean(l.text)
                                                .matches(
                                                        "(?iu).*\\b(?:contents|solos|selections|songs|arrangements)\\b.*"));
        if (!heading || candidate.height() > page.height * .025f) return false;
        List<Line> column =
                lines.stream()
                        .filter(
                                l ->
                                        l.height() >= candidate.height() * .75f
                                                && l.height() <= candidate.height() * 1.35f
                                                && Math.abs(l.left - candidate.left)
                                                        < page.width * .025f
                                                && l.top > page.height * .08f
                                                && l.bottom < page.height * .75f
                                                && credit(l.text) == null
                                                && !LEGAL.matcher(l.text).find()
                                                && clean(l.text).matches(".*\\p{L}{2}.*"))
                        .sorted(Comparator.comparingDouble(l -> l.top))
                        .toList();
        if (!column.contains(candidate) || column.size() < 5) return false;
        int adjacent = 0;
        for (int i = 1; i < column.size(); i++) {
            float gap = column.get(i).top - column.get(i - 1).top;
            if (gap > candidate.height() * 1.3f && gap < candidate.height() * 4.5f) adjacent++;
        }
        return adjacent >= 4;
    }

    /** Same-work coauthor context corroborates an optional middle name in an inferred byline. */
    private static void preferExplicitCoauthorNames(
            Set<String> composers, List<RepeatedCredit> inferred, List<RepeatedCredit> explicit) {
        for (String candidate : List.copyOf(composers)) {
            List<RepeatedCredit> occurrences =
                    inferred.stream().filter(e -> e.names.contains(candidate)).toList();
            if (occurrences.isEmpty()
                    || explicit.stream().anyMatch(e -> e.names.contains(candidate))) continue;
            Set<String> matches = new LinkedHashSet<>();
            for (String canonical : composers) {
                if (same(candidate, canonical) || !sameOuterNames(candidate, canonical)) continue;
                boolean all = true;
                for (RepeatedCredit occurrence : occurrences) {
                    Set<String> replaced = new LinkedHashSet<>(occurrence.names);
                    replaced.remove(candidate);
                    replaced.add(canonical);
                    if (replaced.size() < 2
                            || explicit.stream()
                                    .noneMatch(
                                            e ->
                                                    e.role == Role.COMPOSER
                                                            && same(e.work, occurrence.work)
                                                            && e.names.equals(replaced))) {
                        all = false;
                        break;
                    }
                }
                if (all) matches.add(canonical);
            }
            if (matches.size() == 1) composers.remove(candidate);
        }
    }

    private static boolean sameOuterNames(String a, String b) {
        String[] x = clean(a).split("\\h+"), y = clean(b).split("\\h+");
        if (x.length < 2 || y.length < 2 || x.length == y.length) return false;
        return same(x[0], y[0]) && same(x[x.length - 1], y[y.length - 1]);
    }

    /** Overlapping but different lyrical and bare bylines do not establish a music role. */
    private static boolean conflictingLyricByline(Page page, List<Line> lines, List<String> names) {
        for (Line line : lines) {
            if (line.bottom >= page.notationTop) continue;
            Credit label = credit(clean(line.text));
            if (label == null || label.role != Role.LYRICS) continue;
            List<String> lyrics = creditNames(label.value);
            if (lyrics.stream().anyMatch(a -> names.stream().anyMatch(b -> same(a, b)))
                    && (lyrics.size() != names.size()
                            || lyrics.stream()
                                    .anyMatch(a -> names.stream().noneMatch(b -> same(a, b)))))
                return true;
        }
        return false;
    }

    /** A unique expanded byline in the same work resolves a surname or initial-only repeat. */
    private static void preferExpandedInferredName(
            Set<String> composers, List<RepeatedCredit> inferred, List<RepeatedCredit> explicit) {
        for (String shortName : List.copyOf(composers)) {
            if (explicit.stream()
                    .anyMatch(e -> e.role == Role.COMPOSER && e.names.contains(shortName)))
                continue;
            List<RepeatedCredit> uses =
                    inferred.stream().filter(e -> e.names.contains(shortName)).toList();
            if (uses.isEmpty() || uses.stream().anyMatch(e -> e.names.size() != 1)) continue;
            List<String> expansions =
                    composers.stream().filter(n -> abbreviatedPerson(shortName, n)).toList();
            if (expansions.size() != 1) continue;
            String full = expansions.get(0);
            if (uses.stream()
                    .allMatch(
                            u ->
                                    inferred.stream()
                                            .anyMatch(
                                                    e ->
                                                            e.names.size() == 1
                                                                    && e.names.contains(full)
                                                                    && equivalentPartWork(
                                                                            u.work, e.work))))
                composers.remove(shortName);
        }
    }

    private static boolean abbreviatedPerson(String shortName, String fullName) {
        String[] a = clean(shortName).split("\\h+"), b = clean(fullName).split("\\h+");
        if (same(shortName, fullName)
                || b.length < 2
                || a.length > b.length
                || !same(a[a.length - 1], b[b.length - 1])) return false;
        if (a.length == 1) return true;
        if (a.length != b.length) return false;
        boolean abbreviated = false;
        for (int i = 0; i < a.length - 1; i++) {
            String initial = a[i].replace(".", "");
            if (b[i].replace(".", "").length() < 2
                    || initial.length() != 1
                    || !Character.isLetter(initial.charAt(0))
                    || !b[i].regionMatches(true, 0, initial, 0, 1)) return false;
            abbreviated = true;
        }
        return abbreviated;
    }

    private static boolean equivalentPartWork(String a, String b) {
        String x = partWorkKey(a), y = partWorkKey(b);
        return !x.isEmpty() && x.equals(y);
    }

    private static String partWorkKey(String work) {
        return clean(work)
                .toLowerCase(Locale.ROOT)
                .replaceFirst("\\h+(?:[1-9][0-9]*(?:st|nd|rd|th)?|lst|[ivx]+)[.]?\\h+movement$", "")
                .replaceAll("\\b0+([1-9][0-9]*)\\b", "$1")
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** The complete ordered control row is a UI caption; individual words remain valid names. */
    private static boolean playbackControlSequence(String value) {
        return clean(value)
                .replaceAll("[•·|]", " ")
                .replaceAll("\\h+", " ")
                .matches("(?iu)^speed (?:[e&] )?pitch balance loop$");
    }

    /** A repeated work-title/name running header can restore spacing without guessing letters or roles. */
    private static void restoreReviewHeaderSpacing(
            Set<String> review, List<Page> pages, String title, String fallback) {
        Set<String> restored = new LinkedHashSet<>();
        for (String original : review) {
            String value = original;
            String trimmed = clean(value).replaceFirst("[,;]+$", "");
            if (properReviewName(trimmed)) value = trimmed;
            Set<String> matches = new LinkedHashSet<>();
            for (Page page : pages)
                for (Line line : page.lines) {
                    String[] parts = clean(line.text).split("\\h*[|]\\h*", -1);
                    if (parts.length != 2
                            || !(same(parts[0], title) || same(parts[0], fallback))
                            || !properReviewName(parts[1])
                            || !same(parts[1], value)) continue;
                    matches.add(clean(parts[1]));
                }
            if (matches.size() == 1) value = matches.iterator().next();
            restored.add(value);
        }
        review.clear();
        review.addAll(restored);
    }

    /** Role-supported author lists may include a one-word or hyphen-initial stage name. */

    /** An independently recognizable full name anchors unusual case in a coauthor mononym. */
    private static boolean mixedCaseCoauthor(String name) {
        return name.matches("\\p{Lu}[\\p{L}\\p{M}]{2,30}")
                && name.codePoints().filter(Character::isUpperCase).count() >= 2
                && name.codePoints().anyMatch(Character::isLowerCase)
                && !NOT_TITLE.matcher(name).find()
                && !LEGAL.matcher(name).find()
                && !compoundInstrumentationCaption("for " + name);
    }

    private static List<String> contextualAuthorNames(String value) {
        List<String> names =
                Arrays.stream(clean(value).split("\\h*(?:[,;]|\\band\\b|&)\\h*"))
                        .map(n -> clean(n).replaceAll("(?<=\\p{L})[-–]\\h+(?=\\p{Lu})", "-"))
                        .filter(n -> !n.isEmpty())
                        .toList();
        boolean anchored =
                names.size() >= 2
                        && names.size() <= 6
                        && names.stream().anyMatch(n -> n.contains(" ") && properReviewName(n));
        if (names.isEmpty()
                || names.stream()
                        .anyMatch(
                                n ->
                                        !(properReviewName(n)
                                                || anchored && mixedCaseCoauthor(n)
                                                || INITIALLED_NAME.matcher(n).matches()
                                                || n.matches("\\p{Lu}\\p{Ll}{2,30}-\\p{Lu}"))))
            return List.of();
        return names;
    }

    /** Complete format and source captions describe the score rather than a contributor. */
    private static boolean scoreFormatCaption(String value) {
        String text = clean(value);
        if (text.matches("(?iu)^\\(?from\\h+[^()]+\\)$")) return true;
        if (text.matches("(?iu)^(?:theme|music)\\h+from\\h+.+$")) return true;
        String instrument =
                "(?:violin|viola|cello|flute|piano|organ|guitar|voice|clarinet|oboe|bassoon|harp|trumpet)";
        String catalogue =
                "(?:op[.]|no[.])\\h*[0-9]+[a-z]?(?:\\h*[,;]\\h*(?:op[.]|no[.]|act)\\h*(?:[0-9]+[a-z]?|[ivx]+))*";
        if (text.matches(
                "(?iu)^(?:for\\h+)?"
                        + instrument
                        + "(?:\\h*(?:&|and|with)\\h*"
                        + instrument
                        + ")+(?:,\\h*"
                        + catalogue
                        + ")?$")) return true;
        if (text.matches("(?iu)^" + catalogue + "$")) return true;
        if (text.matches("(?iu)^[a-z]{1,4}\\h+[0-9]+:[0-9]+;\\h*" + catalogue + "$")) return true;
        if (text.matches("(?iu)^vocal\\h+parts$")) return true;

        if (text.matches("(?iu)^\\(?from\\h+[\"“‘].+[\"”’](?:\\h+[12][0-9]{3})?\\)?$")) return true;
        if (text.matches(
                "(?iu)^\\(?(?:orchestral|piano\\h+accompaniment|pop|acoustic|instrumental)\\h+version\\)?$"))
            return true;
        if (text.matches("(?iu)^\\(?main\\h+theme\\)?$")) return true;
        if (text.matches("(?iu)^(?:piano|violin|guitar)\\h*[-–]\\h*merged\\h+staves$")) return true;
        if (text.matches("(?iu)^transcribed\\h+melody$")) return true;
        if (text.matches(
                "(?iu)^(?:theme\\h+song\\h+)?for\\h+(?:keyboard|flute|violin|piano|guitar|cello)\\h+solo(?:\\h*/\\h*jazz\\h+organ)?$"))
            return true;
        if (text.matches(
                "(?iu)^playing\\h+with\\h+karaoke$|^also\\h+sounds\\h+good\\h+with\\h+pedal$"))
            return true;
        return text.matches("(?iu)^[*]?optional\\h+intro\\h*\\(mm[.]?\\h*[0-9]+[-–][0-9]+\\)$");
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
