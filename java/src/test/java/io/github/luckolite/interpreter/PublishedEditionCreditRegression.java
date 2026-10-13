// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original fictional edition metadata, source geometry and intact-name counterexamples. */
public final class PublishedEditionCreditRegression {
    static int passed;

    static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        passed++;
    }

    interface Test {
        void run() throws Exception;
    }

    static void invalid(Test action) throws Exception {
        try {
            action.run();
            throw new AssertionError("invalid evidence accepted");
        } catch (java.io.IOException | IllegalArgumentException expected) {
            passed++;
        }
    }

    static ScoreCreditsDetector.Line line(String text, float l, float t, float r, float b) {
        return new ScoreCreditsDetector.Line(text, l, t, r, b);
    }

    static List<ScoreCreditsDetector.Page> pages(String composer, String arranger) {
        return List.of(
                new ScoreCreditsDetector.Page(
                        1000,
                        1400,
                        350,
                        List.of(
                                line("THE COPPER HARBOR", 200, 80, 800, 150),
                                line(composer, 600, 250, 970, 280),
                                line(arranger, 580, 285, 970, 315),
                                line("Copyright 2020 Example Publisher", 200, 1200, 800, 1230),
                                line("01234567", 50, 1330, 170, 1355))));
    }

    static MusicMetadataCatalogue catalogue(Set<String> known) {
        return new MusicMetadataCatalogue(List.of(), known, Set.of("violin"));
    }

    static PublishedEditionCreditRecovery.Edition edition(
            String title, List<String> composers, List<String> arrangers) {
        return new PublishedEditionCreditRecovery.Edition(
                "1234567",
                title,
                composers,
                arrangers,
                "https://www.halleonard.com/product/1234567",
                "a".repeat(64));
    }

    static PublishedEditionCreditRecovery.Selection select(
            List<ScoreCreditsDetector.Page> pages,
            PublishedEditionCreditRecovery.Edition facts,
            Set<String> known) {
        return PublishedEditionCreditRecovery.select(
                "unrelated import", pages, Set.of(), Map.of("1234567", facts), catalogue(known));
    }

    static String html() {
        return "<html><h1 class='product-title'>Theme from <i>The Copper Harbor</i><span class='subhead'>Example Series</span></h1>"
                + "<li class='pdp-details-list-item contributor-details'><label>Composer:</label><a href='/search/composer/12/mira-valen'>Mira Valen</a></li>"
                + "<li class='pdp-details-list-item contributor-details'><label>Arranger:</label><a href='/search/arranger/13/adrian-solberg'>Adrian Solberg</a></li>"
                + "<li class='pdp-details-list-item inventory-details'><label>Inventory:</label> #HL 1234567</li></html>";
    }

    static PublishedEditionCreditRecovery.Edition parse(String html) throws Exception {
        return HalLeonardPublicationMetadata.parse(
                "1234567",
                "https://www.halleonard.com/product/1234567",
                html.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        var facts =
                edition(
                        "Theme from The Copper Harbor",
                        List.of("Mira Valen"),
                        List.of("Adrian Solberg"));
        var original = pages("Music by Mira Valem", "Arranged by Adrian Solber");
        var chosen = select(original, facts, Set.of());
        check(chosen != null, "exact edition/printed title/role terminal recovery");
        check(chosen.repairs().size() == 2, "only two source rows changed");
        check(
                chosen.after().composer().equals("Mira Valen")
                        && chosen.after().arranger().equals("Adrian Solberg"),
                "publisher literals recover final glyph");
        check(
                chosen.before().title().equals(chosen.after().title())
                        && chosen.before().artist().equals(chosen.after().artist())
                        && chosen.before()
                                .unclassifiedCredits()
                                .equals(chosen.after().unclassifiedCredits()),
                "title artist and reviews preserved");
        check(chosen.originalPages().equals(original), "original evidence immutable");
        check(
                ScoreCreditsDetector.detect("unrelated import", chosen.pages(), Set.of())
                        .equals(chosen.after()),
                "selected source replay equals result");
        check(
                select(original, facts, Set.of("Mira Valem", "Adrian Solber")) == null,
                "positive intact-name veto");
        check(
                select(
                                original,
                                edition("The Silver Garden", facts.composers(), facts.arrangers()),
                                Set.of())
                        == null,
                "different printed work rejected");
        check(
                select(pages("Music by Mira Varen", "Arranged by Adrian Solberg"), facts, Set.of())
                        == null,
                "internal surname error cannot use terminal rule");
        check(
                select(
                                original,
                                edition(
                                        facts.title(),
                                        List.of("Mina Valen"),
                                        List.of("Adrian Solber")),
                                Set.of())
                        == null,
                "given name cannot be rewritten");
        check(
                select(
                                pages("Music by Mira Valem", "Arranged by Adrian Solberg"),
                                edition(
                                        facts.title(),
                                        List.of("Mira Valen", "Mira Vales"),
                                        facts.arrangers()),
                                Set.of())
                        == null,
                "ambiguous final glyph candidates rejected");
        check(
                select(
                                pages("Performed by Mira Valem", "Arranged by Adrian Solberg"),
                                facts,
                                Set.of())
                        == null,
                "performer cannot become composer");
        check(
                select(
                                pages("Music by Mira Valen", "Transcribed by Harbor Service"),
                                facts,
                                Set.of())
                        == null,
                "service transcription stays unresolved");
        var noCopyright = original.get(0);
        var rows = new ArrayList<>(noCopyright.lines());
        rows.remove(3);
        check(
                PublishedEditionCreditRecovery.nominate(
                                List.of(new ScoreCreditsDetector.Page(1000, 1400, 350, rows)))
                        .isEmpty(),
                "copyright context required");
        rows = new ArrayList<>(original.get(0).lines());
        rows.add(line("07654321", 200, 1330, 320, 1355));
        check(
                PublishedEditionCreditRecovery.nominate(
                                List.of(new ScoreCreditsDetector.Page(1000, 1400, 350, rows)))
                        .isEmpty(),
                "ambiguous printed edition rejected");
        var source = original.get(0);
        var protectedPage =
                new ScoreCreditsDetector.Page(
                        source.width(),
                        source.height(),
                        source.notationTop(),
                        source.lines(),
                        false,
                        false,
                        List.of(
                                new ScoreCreditsDetector.ReviewCredit(
                                        source.lines().get(1), "Keep for review"),
                                new ScoreCreditsDetector.ReviewCredit(
                                        source.lines().get(2), "Service review")));
        check(
                select(List.of(protectedPage), facts, Set.of()) == null,
                "owned review credits protected");
        var secondary =
                new ScoreCreditsDetector.Page(
                        source.width(),
                        source.height(),
                        source.notationTop(),
                        source.lines(),
                        true,
                        false,
                        List.of());
        check(
                select(List.of(secondary), facts, Set.of()) != null,
                "secondary score credits remain eligible");
        var parsed = parse(html());
        check(
                parsed.title().equals("Theme from The Copper Harbor")
                        && parsed.composers().equals(List.of("Mira Valen"))
                        && parsed.arrangers().equals(List.of("Adrian Solberg")),
                "primary publisher role metadata parsed");
        var comment = parse("<!--" + html().replace("1234567", "9999999") + "-->" + html());
        check(
                comment.title().equals(parsed.title())
                        && comment.composers().equals(parsed.composers())
                        && comment.arrangers().equals(parsed.arrangers())
                        && !comment.responseSha256().equals(parsed.responseSha256()),
                "comment cannot inject facts and original response remains hashed");
        invalid(() -> parse(html().replace("#HL 1234567", "#HL 1234568")));
        invalid(() -> parse(html().replace("/search/composer/12/", "/search/arranger/12/")));
        invalid(
                () ->
                        parse(
                                html()
                                        + "<li class='pdp-details-list-item inventory-details'><label>Inventory:</label>#HL 7654321</li>"));
        invalid(() -> parse(html().replace("Mira Valen</a>", "Mira &unknown;Valen</a>")));
        invalid(
                () ->
                        HalLeonardPublicationMetadata.parse(
                                "1234567",
                                "https://www.halleonard.com.evil.invalid/product/1234567",
                                html().getBytes(StandardCharsets.UTF_8)));
        invalid(
                () ->
                        HalLeonardPublicationMetadata.parse(
                                "1234567",
                                "https://www.halleonard.com/product/1234567",
                                new byte[] {(byte) 255}));
        invalid(
                () ->
                        new PublishedEditionCreditRecovery.Edition(
                                "1234567",
                                facts.title(),
                                facts.composers(),
                                facts.arrangers(),
                                "http://www.halleonard.com/product/1234567",
                                "a".repeat(64)));
        check(
                !PublishedEditionCreditRecovery.validSourceUrl(
                        "https://www.halleonard.com/product/1234567?pdf=private", "1234567"),
                "query payload forbidden");
        System.out.println("passed=" + passed);
    }
}
