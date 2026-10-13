// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original fictional work and edition source channels must compose without losing ownership. */
public final class PublicationCreditCompositionRegression {
    static int passed;

    static void check(boolean b, String label) {
        if (!b) throw new AssertionError(label);
        passed++;
    }

    public static void main(String[] args) {
        var source = new ArrayList<>(CoherentWorkOwnershipRegression.pages("NOCTURNEVIIer"));
        var credit =
                PublishedEditionCreditRegression.pages(
                                "Music by Mira Valem", "Arranged by Adrian Solber")
                        .get(0);
        source.add(
                new ScoreCreditsDetector.Page(
                        credit.width(),
                        credit.height(),
                        credit.notationTop(),
                        credit.lines(),
                        true,
                        false,
                        List.of()));
        var catalogue = CoherentWorkOwnershipRegression.catalogue(Set.of());
        var title = CoherentWorkOwnershipRegression.select(source, catalogue);
        check(title != null, "owned work source selected");
        var facts =
                PublishedEditionCreditRegression.edition(
                        "The Copper Harbor", List.of("Mira Valen"), List.of("Adrian Solberg"));
        var recovered =
                PublishedEditionCreditRecovery.select(
                        "unrelated-import",
                        title.pages(),
                        Set.of(),
                        Map.of("1234567", facts),
                        catalogue);
        check(recovered != null, "later printed edition credits recovered");
        var after = PublishedEditionCreditRecovery.carry(title.after(), title, recovered);
        check(
                after != null
                        && after.title().equals("The Amber Gate NOCTURNE VII")
                        && after.composer().equals("Mira Valen")
                        && after.arranger().equals("Adrian Solberg"),
                "title and later role edits coexist");
        check(
                after.unclassifiedCredits().equals(title.after().unclassifiedCredits())
                        && after.artist().equals(title.after().artist()),
                "upstream reviews and artist preserved");
        check(
                !ScoreCreditsDetector.detect("unrelated-import", title.pages(), Set.of())
                        .equals(title.after()),
                "literal pages do not erase external ownership result");
        check(
                PublishedEditionCreditRecovery.carry(title.after(), null, recovered) == null,
                "unowned title override rejected");
        var forgedAfter =
                new ScoreCreditsDetector.Result(
                        recovered.after().title(),
                        "Unrelated Artist",
                        recovered.after().composer(),
                        recovered.after().arranger(),
                        recovered.after().unclassifiedCredits());
        var forged =
                new PublishedEditionCreditRecovery.Selection(
                        recovered.before(),
                        forgedAfter,
                        recovered.originalPages(),
                        recovered.pages(),
                        recovered.repairs());
        check(
                PublishedEditionCreditRecovery.carry(title.after(), title, forged) == null,
                "non-credit change rejected");
        check(
                PublishedEditionCreditRecovery.carry(recovered.before(), null, recovered)
                        .equals(recovered.after()),
                "plain credit-only composition unchanged");
        var repeatedPage =
                PublishedEditionCreditRegression.pages(
                                "Music by Mira Valem", "Arranged by Adrian Solber")
                        .get(0);
        var duplicatePage =
                new ScoreCreditsDetector.Page(
                        repeatedPage.width(),
                        repeatedPage.height(),
                        repeatedPage.notationTop(),
                        repeatedPage.lines(),
                        true,
                        false,
                        List.of());
        var repeated =
                PublishedEditionCreditRegression.select(
                        List.of(repeatedPage, duplicatePage), facts, Set.of());
        check(
                repeated != null && repeated.repairs().size() == 4,
                "repeated source rows repair independently");
        check(
                repeated.after().composer().equals("Mira Valen")
                        && repeated.after().arranger().equals("Adrian Solberg"),
                "repeated canonical names remain deduplicated");
        var splitRows = new ArrayList<>(duplicatePage.lines());
        var footer = splitRows.get(4);
        splitRows.set(
                4,
                new ScoreCreditsDetector.Line(
                        "01234568", footer.left(), footer.top(), footer.right(), footer.bottom()));
        var secondEditionPage =
                new ScoreCreditsDetector.Page(
                        duplicatePage.width(),
                        duplicatePage.height(),
                        duplicatePage.notationTop(),
                        splitRows,
                        true,
                        false,
                        List.of());
        var secondEdition =
                new PublishedEditionCreditRecovery.Edition(
                        "1234568",
                        facts.title(),
                        List.of("Mira Vales"),
                        facts.arrangers(),
                        "https://www.halleonard.com/product/1234568",
                        "b".repeat(64));
        var split =
                PublishedEditionCreditRecovery.select(
                        "unrelated import",
                        List.of(repeatedPage, secondEditionPage),
                        Set.of(),
                        Map.of("1234567", facts, "1234568", secondEdition),
                        PublishedEditionCreditRegression.catalogue(Set.of()));
        check(split != null, "different exact editions can disambiguate identical clipped names");
        check(
                split.after().composer().equals("Mira Valen; Mira Vales"),
                "both independently printed composers preserved");
        System.out.println("passed=" + passed);
    }
}
