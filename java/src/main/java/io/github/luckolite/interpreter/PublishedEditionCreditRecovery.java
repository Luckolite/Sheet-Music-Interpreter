// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.net.URI;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Explicit printed roles and an exact publisher edition can recover a damaged final name glyph. */
public final class PublishedEditionCreditRecovery {
    private PublishedEditionCreditRecovery() {}

    public enum Role {
        COMPOSER,
        ARRANGER
    }

    public record Edition(
            String productId,
            String title,
            List<String> composers,
            List<String> arrangers,
            String sourceUrl,
            String responseSha256) {
        public Edition {
            if (!validProductId(productId)
                    || !safe(title, 300)
                    || !validNames(composers)
                    || !validNames(arrangers)
                    || !validSourceUrl(sourceUrl, productId)
                    || responseSha256 == null
                    || !responseSha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid edition provenance or credits");
            composers = List.copyOf(composers);
            arrangers = List.copyOf(arrangers);
        }
    }

    public record PrintedCredit(
            Role role,
            ScoreCreditsDetector.Line line,
            String label,
            String value,
            boolean unambiguousRole) {}

    public record Nomination(
            int pageIndex,
            String productId,
            ScoreCreditsDetector.Line identifier,
            List<PrintedCredit> credits) {
        public Nomination {
            credits = List.copyOf(credits);
        }
    }

    public record Repair(
            int pageIndex,
            String productId,
            Role role,
            ScoreCreditsDetector.Line original,
            ScoreCreditsDetector.Line corrected,
            String originalName,
            String correctedName,
            Edition edition) {}

    public record Selection(
            ScoreCreditsDetector.Result before,
            ScoreCreditsDetector.Result after,
            List<ScoreCreditsDetector.Page> originalPages,
            List<ScoreCreditsDetector.Page> pages,
            List<Repair> repairs) {}

    private static final Pattern COMPOSER =
            Pattern.compile("(?iu)^(music\\h+by\\h+|composed\\h+by\\h+|by\\h+)(.+)$");
    private static final Pattern ARRANGER =
            Pattern.compile("(?iu)^(arranged\\h+by\\h+|arr\\.?\\h+by\\h+)(.+)$");
    private static final Set<String> TITLE_FUNCTION_WORDS =
            Set.of(
                    "a", "an", "the", "of", "from", "theme", "themes", "le", "la", "les", "der",
                    "die", "das", "el", "los");

    private static boolean safe(String s, int limit) {
        return s != null
                && !s.isBlank()
                && s.length() <= limit
                && s.codePoints()
                        .noneMatch(
                                c ->
                                        Character.isISOControl(c)
                                                || Character.getType(c) == Character.FORMAT
                                                || c == 0x2028
                                                || c == 0x2029);
    }

    public static boolean validProductId(String id) {
        return id != null && id.matches("[1-9][0-9]{0,7}");
    }

    public static boolean validSourceUrl(String url, String id) {
        if (!safe(url, 1000) || !validProductId(id)) return false;
        try {
            var u = URI.create(url);
            return "https".equals(u.getScheme())
                    && "www.halleonard.com".equals(u.getHost())
                    && u.getPort() == -1
                    && u.getUserInfo() == null
                    && u.getRawQuery() == null
                    && u.getRawFragment() == null
                    && (u.getRawPath().equals("/product/" + id)
                            || u.getRawPath().matches("/product/" + id + "/[a-z0-9-]+"));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean validNames(List<String> names) {
        return names != null
                && names.size() <= 20
                && names.stream()
                        .allMatch(
                                n ->
                                        safe(n, 150)
                                                && n.matches(
                                                        "[\\p{L}\\p{M}.'’–-]+(?:\\h+[\\p{L}\\p{M}.'’–-]+){1,6}"));
    }

    private static String key(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static List<String> words(String s) {
        return Arrays.stream(
                        Normalizer.normalize(s, Normalizer.Form.NFKD)
                                .replaceAll("\\p{M}+", "")
                                .toLowerCase(Locale.ROOT)
                                .split("[^\\p{L}\\p{N}]+"))
                .filter(v -> !v.isEmpty())
                .toList();
    }

    private static boolean validLine(ScoreCreditsDetector.Line l, ScoreCreditsDetector.Page p) {
        return l != null
                && safe(l.text(), 1000)
                && Float.isFinite(l.left())
                && Float.isFinite(l.top())
                && Float.isFinite(l.right())
                && Float.isFinite(l.bottom())
                && l.left() >= 0
                && l.top() >= 0
                && l.right() <= p.width()
                && l.bottom() <= p.height()
                && l.right() > l.left()
                && l.bottom() > l.top();
    }

    private static boolean validPage(ScoreCreditsDetector.Page p) {
        return p != null
                && Float.isFinite(p.width())
                && Float.isFinite(p.height())
                && Float.isFinite(p.notationTop())
                && p.width() > 0
                && p.height() > 0
                && p.notationTop() > 0
                && p.notationTop() <= p.height()
                && !p.photographicCover()
                && p.lines().size() <= 100000
                && p.lines().stream().allMatch(l -> validLine(l, p))
                && p.reviewCredits().size() <= 100000
                && p.reviewCredits().stream()
                        .allMatch(
                                r -> r != null && safe(r.value(), 1000) && validLine(r.line(), p));
    }

    private static boolean protectedCredit(
            ScoreCreditsDetector.Page p, ScoreCreditsDetector.Line l) {
        return p.reviewCredits().stream()
                .anyMatch(
                        r ->
                                Math.min(l.right(), r.line().right())
                                                > Math.max(l.left(), r.line().left())
                                        && Math.min(l.bottom(), r.line().bottom())
                                                > Math.max(l.top(), r.line().top()));
    }

    private static PrintedCredit credit(
            ScoreCreditsDetector.Page p, ScoreCreditsDetector.Line line) {
        if (line.top() >= Math.min(p.notationTop(), p.height() * .3f) || protectedCredit(p, line))
            return null;
        var arranger = ARRANGER.matcher(line.text().strip());
        if (arranger.matches() && validNames(List.of(arranger.group(2).strip())))
            return new PrintedCredit(
                    Role.ARRANGER, line, arranger.group(1), arranger.group(2).strip(), true);
        var composer = COMPOSER.matcher(line.text().strip());
        if (composer.matches() && validNames(List.of(composer.group(2).strip())))
            return new PrintedCredit(
                    Role.COMPOSER,
                    line,
                    composer.group(1),
                    composer.group(2).strip(),
                    !composer.group(1).strip().equalsIgnoreCase("by"));
        return null;
    }

    public static List<Nomination> nominate(List<ScoreCreditsDetector.Page> pages) {
        if (pages == null || pages.size() > 10000) return List.of();
        var out = new ArrayList<Nomination>();
        for (int i = 0; i < pages.size(); i++) {
            var p = pages.get(i);
            if (!validPage(p)) continue;
            var ids =
                    p.lines().stream()
                            .filter(
                                    l ->
                                            l.top() > p.height() * .8f
                                                    && l.text().strip().matches("0[0-9]{7}"))
                            .toList();
            if (ids.size() != 1
                    || p.lines().stream()
                            .noneMatch(
                                    l ->
                                            l.top() > p.height() * .75f
                                                    && l.text()
                                                            .toLowerCase(Locale.ROOT)
                                                            .contains("copyright"))) continue;
            var credits =
                    p.lines().stream().map(l -> credit(p, l)).filter(Objects::nonNull).toList();
            if (credits.stream().noneMatch(PrintedCredit::unambiguousRole)) continue;
            String id = ids.get(0).text().strip().replaceFirst("^0+", "");
            if (validProductId(id)) out.add(new Nomination(i, id, ids.get(0), credits));
        }
        return List.copyOf(out);
    }

    private static boolean titleMatches(
            ScoreCreditsDetector.Page p, Edition edition, PrintedCredit credit) {
        var required =
                words(edition.title()).stream()
                        .filter(w -> !TITLE_FUNCTION_WORDS.contains(w))
                        .toList();
        if (required.isEmpty() || required.stream().mapToInt(String::length).sum() < 4)
            return false;
        return p.lines().stream()
                .filter(
                        l ->
                                l.top() < credit.line().top()
                                        && l.bottom() < p.notationTop()
                                        && l.height() >= p.width() * .015f
                                        && l.height() >= credit.line().height() * 1.1f
                                        && l.left() < p.width() * .75f
                                        && l.right() > p.width() * .25f
                                        && !ScoreCreditsDetector.isPrintedCredit(l.text()))
                .anyMatch(l -> words(l.text()).containsAll(required));
    }

    private static boolean terminalDamage(String raw, String canonical) {
        var a = words(raw);
        var b = words(canonical);
        if (a.size() < 2 || a.size() != b.size() || key(raw).equals(key(canonical))) return false;
        for (int i = 0; i < a.size() - 1; i++) if (!a.get(i).equals(b.get(i))) return false;
        String last = a.get(a.size() - 1), wanted = b.get(b.size() - 1);
        if (last.length() < 4 || wanted.length() < 4) return false;
        return wanted.length() == last.length() + 1 && wanted.startsWith(last)
                || wanted.length() == last.length()
                        && wanted.substring(0, wanted.length() - 1)
                                .equals(last.substring(0, last.length() - 1));
    }

    private static List<String> nameKeys(String field) {
        return Arrays.stream(field.split(";", -1))
                .map(String::strip)
                .filter(v -> !v.isEmpty())
                .map(PublishedEditionCreditRecovery::key)
                .distinct()
                .toList();
    }

    private static Set<String> expected(String field, Role role, List<Repair> repairs) {
        var original = new LinkedHashSet<>(nameKeys(field));
        var replaced = new HashSet<String>();
        var corrected = new LinkedHashSet<String>();
        for (var repair : repairs)
            if (repair.role() == role) {
                String raw = key(repair.originalName());
                if (!original.contains(raw)) return null;
                replaced.add(raw);
                corrected.add(key(repair.correctedName()));
            }
        original.removeAll(replaced);
        original.addAll(corrected);
        return original;
    }

    public static Selection select(
            String hint,
            List<ScoreCreditsDetector.Page> pages,
            Set<String> names,
            Map<String, Edition> editions,
            MusicMetadataCatalogue catalogue) {
        if (hint == null
                || pages == null
                || pages.isEmpty()
                || pages.stream().anyMatch(Objects::isNull)
                || names == null
                || names.stream().anyMatch(Objects::isNull)
                || editions == null
                || editions.size() > 1000
                || catalogue == null) return null;
        var nominations = nominate(pages);
        if (nominations.isEmpty()) return null;
        var before = ScoreCreditsDetector.detect(hint, pages, names);
        var updated = new ArrayList<>(pages);
        var repairs = new ArrayList<Repair>();
        for (var nomination : nominations) {
            var edition = editions.get(nomination.productId());
            if (edition == null || !edition.productId().equals(nomination.productId())) continue;
            var p = pages.get(nomination.pageIndex());
            var lines = new ArrayList<>(p.lines());
            for (var c : nomination.credits()) {
                if (!titleMatches(p, edition, c)
                        || catalogue.isPersonName(c.value())
                        || catalogue.isPersonName(
                                Normalizer.normalize(c.value(), Normalizer.Form.NFKD)
                                        .replaceAll("\\p{M}+", ""))
                        || names.stream().anyMatch(n -> key(n).equals(key(c.value())))) continue;
                var choices =
                        (c.role() == Role.COMPOSER ? edition.composers() : edition.arrangers())
                                .stream()
                                        .filter(v -> terminalDamage(c.value(), v))
                                        .distinct()
                                        .toList();
                if (choices.size() != 1) continue;
                String name = choices.get(0);
                var old = c.line();
                var corrected =
                        new ScoreCreditsDetector.Line(
                                c.label() + name, old.left(), old.top(), old.right(), old.bottom());
                lines.set(lines.indexOf(old), corrected);
                repairs.add(
                        new Repair(
                                nomination.pageIndex(),
                                nomination.productId(),
                                c.role(),
                                old,
                                corrected,
                                c.value(),
                                name,
                                edition));
            }
            updated.set(
                    nomination.pageIndex(),
                    new ScoreCreditsDetector.Page(
                            p.width(),
                            p.height(),
                            p.notationTop(),
                            lines,
                            p.creditsOnly(),
                            p.photographicCover(),
                            p.reviewCredits()));
        }
        if (repairs.isEmpty()) return null;
        var after = ScoreCreditsDetector.detect(hint, updated, names);
        if (!before.title().equals(after.title())
                || !before.artist().equals(after.artist())
                || !before.unclassifiedCredits().equals(after.unclassifiedCredits())
                || !Objects.equals(
                        expected(before.composer(), Role.COMPOSER, repairs),
                        new LinkedHashSet<>(nameKeys(after.composer())))
                || !Objects.equals(
                        expected(before.arranger(), Role.ARRANGER, repairs),
                        new LinkedHashSet<>(nameKeys(after.arranger())))) return null;
        return new Selection(
                before, after, List.copyOf(pages), List.copyOf(updated), List.copyOf(repairs));
    }

    /** Preserve an independently owned title/review while applying only verified credit edits. */
    public static ScoreCreditsDetector.Result carry(
            ScoreCreditsDetector.Result expected,
            CorroboratedCatalogueWorkTitle.Selection titleOwnership,
            Selection credits) {
        if (expected == null
                || credits == null
                || credits.before() == null
                || credits.after() == null
                || credits.originalPages() == null
                || credits.repairs() == null) return null;
        var before = credits.before();
        var after = credits.after();
        if (!before.title().equals(after.title())
                || !before.artist().equals(after.artist())
                || !before.unclassifiedCredits().equals(after.unclassifiedCredits())
                || !Objects.equals(
                        expected(before.composer(), Role.COMPOSER, credits.repairs()),
                        new LinkedHashSet<>(nameKeys(after.composer())))
                || !Objects.equals(
                        expected(before.arranger(), Role.ARRANGER, credits.repairs()),
                        new LinkedHashSet<>(nameKeys(after.arranger())))) return null;
        boolean owned =
                titleOwnership != null
                        && titleOwnership.pages().equals(credits.originalPages())
                        && titleOwnership.after().equals(expected);
        if (!before.equals(expected)
                && (!owned
                        || !before.artist().equals(expected.artist())
                        || !before.composer().equals(expected.composer())
                        || !before.arranger().equals(expected.arranger()))) return null;
        return new ScoreCreditsDetector.Result(
                expected.title(),
                expected.artist(),
                after.composer(),
                after.arranger(),
                expected.unclassifiedCredits(),
                java.util.stream.Stream.concat(
                                expected.creditConflicts().stream(),
                                after.creditConflicts().stream())
                        .distinct()
                        .toList());
    }
}
