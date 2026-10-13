// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.StringReader;
import java.util.*;

/** Invented work facts and independently constructed page geometry. */
public final class CatalogueWorkTitleRegression {
    private static int passed;

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        passed++;
    }

    private static MusicMetadataCatalogue catalogue(Set<String> people) {
        return new MusicMetadataCatalogue(
                List.of(
                        new MusicMetadataCatalogue.Work(
                                "fictional-work-amber", 7, "piano nocturne", "The Amber Gate"),
                        new MusicMetadataCatalogue.Work(
                                "fictional-work-dawn", 3, "flute sonata", "L’Aurore")),
                people,
                Set.of("flauto"));
    }

    private static ScoreCreditsDetector.Line line(String text, float l, float t, float r, float b) {
        return new ScoreCreditsDetector.Line(text, l, t, r, b);
    }

    private static List<ScoreHeaderReadConsensus.Reading> reads(String text) {
        var result = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        for (int scale : new int[] {1, 2, 4})
            for (String mode : List.of("tight", "horizontal"))
                for (double factor : new double[] {1, 1.5})
                    result.add(
                            new ScoreHeaderReadConsensus.Reading(scale, mode, factor, text, .97));
        return result;
    }

    private static List<ScoreCreditsDetector.Page> pages(String title, String caption) {
        var first =
                new ScoreCreditsDetector.Page(
                        1400,
                        2000,
                        260,
                        List.of(
                                line(title, 110, 212, 320, 245),
                                line("Flauto Solista", 500, 130, 1080, 200),
                                line(caption, 70, 270, 620, 340)));
        var second =
                new ScoreCreditsDetector.Page(
                        1400, 2000, 240, List.of(line("Flauto Solista", 485, 90, 1070, 160)));
        return List.of(first, second);
    }

    private static Map<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>> evidence(
            List<ScoreCreditsDetector.Page> pages, String title, String caption) {
        var rows = pages.get(0).lines();
        return Map.of(
                rows.get(0),
                reads(title),
                rows.get(1),
                reads("Flauto Solista"),
                rows.get(2),
                reads(caption));
    }

    private static CorroboratedCatalogueWorkTitle.Selection select(
            List<ScoreCreditsDetector.Page> pages,
            Map<ScoreCreditsDetector.Line, List<ScoreHeaderReadConsensus.Reading>> evidence,
            MusicMetadataCatalogue catalogue,
            Set<String> names) {
        return CorroboratedCatalogueWorkTitle.select(
                "unrelated-import-name", pages, names, evidence, catalogue);
    }

    public static void main(String[] args) throws Exception {
        var catalogue = catalogue(Set.of());
        check(
                catalogue.corroborate("The Amber Gate", "NOCTURNEVII") != null,
                "exact work/type/ordinal");
        check(
                catalogue.corroborate("The Amber Gate", "Nocturne No. 7") != null,
                "printed decimal ordinal");
        check(catalogue.corroborate("L'Aurore", "SONATA III") != null, "punctuation normalization");
        check(catalogue.corroborate("The Amber Gate", "Nocturne VI") == null, "ordinal mismatch");
        check(catalogue.corroborate("The Amber Gate", "Suite VII") == null, "form mismatch");
        check(
                catalogue.corroborate("The Amber Gote", "Nocturne VII") == null,
                "catalogue never guesses alias letters");
        check(
                catalogue.corroborate("The Amber Gate", "Nocturne IIX") == null,
                "invalid roman ordinal");
        check(catalogue.corroborate("The Amber Gate", "Nocturne 07") == null, "leading zero");
        check(catalogue.corroborate("The Amber Gate", "Nocturne 0") == null, "zero ordinal");
        check(catalogue.corroborate("The Amber Gate", "Nocturne") == null, "missing ordinal");
        check(
                catalogue.corroborate("L’Héritier", "Sonata III") == null,
                "unknown article-named contributor remains unresolved");
        check(
                catalogue(Set.of("The Amber Gate")).corroborate("The Amber Gate", "Nocturne VII")
                        == null,
                "person/work collision veto");
        var ambiguous =
                new MusicMetadataCatalogue(
                        List.of(
                                new MusicMetadataCatalogue.Work(
                                        "fictional-a", 7, "nocturne", "The Amber Gate"),
                                new MusicMetadataCatalogue.Work(
                                        "fictional-b", 7, "nocturne", "The Amber Gate")),
                        Set.of(),
                        Set.of("flauto"));
        check(
                ambiguous.corroborate("The Amber Gate", "Nocturne VII") == null,
                "ambiguous work identity");
        var duplicate =
                new MusicMetadataCatalogue(
                        List.of(
                                new MusicMetadataCatalogue.Work(
                                        "fictional-a", 7, "nocturne", "The Amber Gate"),
                                new MusicMetadataCatalogue.Work(
                                        "fictional-a", 7, "piano nocturne", "The Amber Gate")),
                        Set.of(),
                        Set.of("flauto"));
        check(
                duplicate.corroborate("The Amber Gate", "Nocturne VII") != null,
                "duplicate facts do not invent ambiguity");
        var original = pages("The Amber Gote", "Nocturne VII");
        var supplied = evidence(original, "The Amber Gate", "Nocturne VII");
        var chosen = select(original, supplied, catalogue, Set.of());
        check(chosen != null, "source-bound work selection");
        check(
                chosen.after().title().equals("The Amber Gate Nocturne VII"),
                "printed literals form output");
        check(
                chosen.after().artist().equals(chosen.before().artist())
                        && chosen.after().composer().equals(chosen.before().composer())
                        && chosen.after().arranger().equals(chosen.before().arranger()),
                "roles preserved");
        check(chosen.originalPages().equals(original), "raw source retained");
        check(
                select(original, supplied, catalogue, Set.of("The Amber Gate")) == null,
                "known person veto");
        check(
                select(original, supplied, catalogue(Set.of("The Amber Gate")), Set.of()) == null,
                "public person veto");
        check(select(original, supplied, ambiguous, Set.of()) == null, "ambiguous work veto");
        check(
                select(List.of(original.get(0)), supplied, catalogue, Set.of()) == null,
                "repeated header required");
        var missing = new HashMap<>(supplied);
        missing.remove(original.get(0).lines().get(0));
        check(
                select(original, missing, catalogue, Set.of()) == null,
                "unread heading cannot come from catalogue");
        missing = new HashMap<>(supplied);
        missing.remove(original.get(0).lines().get(2));
        check(
                select(original, missing, catalogue, Set.of()) == null,
                "unread ordinal cannot come from catalogue");
        missing = new HashMap<>(supplied);
        missing.remove(original.get(0).lines().get(1));
        check(
                select(original, missing, catalogue, Set.of()) == null,
                "unread dominant header cannot be excluded");
        var forged = new HashMap<>(supplied);
        forged.put(line("other source", 1, 1, 20, 20), reads("The Amber Gate"));
        check(select(original, forged, catalogue, Set.of()) == null, "foreign source row rejected");
        var incomplete = new HashMap<>(supplied);
        incomplete.put(original.get(0).lines().get(0), reads("The Amber Gate").subList(0, 11));
        check(
                select(original, incomplete, catalogue, Set.of()) == null,
                "incomplete grid rejected");
        var credited = new ArrayList<>(original.get(0).lines());
        credited.add(line("Composed by The Amber Gate", 800, 218, 1350, 245));
        var withCredit =
                List.of(new ScoreCreditsDetector.Page(1400, 2000, 260, credited), original.get(1));
        check(
                select(
                                withCredit,
                                evidence(withCredit, "The Amber Gate", "Nocturne VII"),
                                catalogue,
                                Set.of())
                        == null,
                "explicit contributor ownership protected");
        var duplicateName = new ArrayList<>(original.get(1).lines());
        duplicateName.add(line("The Amber Gate", 80, 190, 390, 230));
        check(
                select(
                                List.of(
                                        original.get(0),
                                        new ScoreCreditsDetector.Page(
                                                1400, 2000, 240, duplicateName)),
                                supplied,
                                catalogue,
                                Set.of())
                        == null,
                "independent same-name row protected");
        var service =
                new ScoreCreditsDetector.ReviewCredit(
                        original.get(0).lines().get(0), "Fictional Transcription Service");
        var withService =
                List.of(
                        new ScoreCreditsDetector.Page(
                                1400,
                                2000,
                                260,
                                original.get(0).lines(),
                                false,
                                false,
                                List.of(service)),
                        original.get(1));
        check(
                select(
                                withService,
                                evidence(withService, "The Amber Gate", "Nocturne VII"),
                                catalogue,
                                Set.of())
                        == null,
                "service review protected");
        var unknown = pages("L’Héritier", "Sonata III");
        check(
                select(unknown, evidence(unknown, "L’Héritier", "Sonata III"), catalogue, Set.of())
                        == null,
                "unknown contributor not forced into title");
        boolean rejected = false;
        try {
            MusicMetadataCatalogue.load(
                    new StringReader("malformed\n"), new StringReader(""), new StringReader(""));
        } catch (java.io.IOException expected) {
            rejected = true;
        }
        check(rejected, "malformed snapshot rejected");
        check(
                catalogue.isInstrumentPart("Flauto Solista"),
                "instrument and recognized part qualifier");
        check(
                !catalogue.isInstrumentPart("Flauto Dreams"),
                "instrument named in a work title is not a part label");
        check(
                !catalogue.isInstrumentPart("Collected Nocturnes"),
                "collection heading is not a part label");
        check(
                !MusicMetadataCatalogue.preservesCaptionIdentifier("Nocturne VI", "Nocturne VII"),
                "ordinal substitution rejected");
        check(
                !MusicMetadataCatalogue.preservesCaptionIdentifier("Nocturne IIX", "Nocturne VII"),
                "invalid printed roman not silently reconstructed");
        check(
                !MusicMetadataCatalogue.preservesCaptionIdentifier("Suite VII", "Nocturne VII"),
                "printed form substitution rejected");
        var numbered = pages("The Amber Gote", "Nocturne VI");
        check(
                select(
                                numbered,
                                evidence(numbered, "The Amber Gate", "Nocturne VII"),
                                catalogue,
                                Set.of())
                        == null,
                "different source number cannot be selected to satisfy catalogue");
        var collectionLines = new ArrayList<>(original.get(0).lines());
        collectionLines.set(1, line("Collected Nocturnes", 500, 130, 1080, 200));
        var collectionSecond =
                new ScoreCreditsDetector.Page(
                        1400, 2000, 240, List.of(line("Collected Nocturnes", 485, 90, 1070, 160)));
        var collection =
                List.of(
                        new ScoreCreditsDetector.Page(1400, 2000, 260, collectionLines),
                        collectionSecond);
        var collectionReads = new HashMap<>(evidence(collection, "The Amber Gate", "Nocturne VII"));
        collectionReads.put(collectionLines.get(1), reads("Collected Nocturnes"));
        check(
                select(collection, collectionReads, catalogue, Set.of()) == null,
                "repeated collection title is protected");
        check(
                select(original, supplied, catalogue, Set.of("The Amber Gote")) == null,
                "raw known contributor survives contrary reread");
        check(
                select(original, supplied, catalogue(Set.of("The Amber Gote")), Set.of()) == null,
                "raw public contributor survives contrary reread");
        var distantTitle = pages("The Copper Arch", "Nocturne VII");
        check(
                select(
                                distantTitle,
                                evidence(distantTitle, "The Amber Gate", "Nocturne VII"),
                                catalogue,
                                Set.of())
                        == null,
                "catalogue cannot supply a different heading");
        var hallucinatedPart = new HashMap<>(collectionReads);
        hallucinatedPart.put(collectionLines.get(1), reads("Flauto Solista"));
        check(
                select(collection, hallucinatedPart, catalogue, Set.of()) == null,
                "catalogue cannot turn unrelated dominant header into instrument");
        System.out.println("passed=" + passed);
    }
}
