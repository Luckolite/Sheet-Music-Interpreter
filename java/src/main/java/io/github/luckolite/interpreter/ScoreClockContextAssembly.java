// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Host/document ordering validation at assembly intake. This is not a clock issuer. */
public final class ScoreClockContextAssembly {
    private ScoreClockContextAssembly() {}

    public record Page(int physicalPage, ScoreClockContextSource context, boolean boundDocument) {}

    public record Intake(String documentSha256, int physicalPageCount, List<Page> pages) {
        public Intake {
            pages = List.copyOf(pages);
        }
    }

    public static ScorePageInterpretation bindDocument(
            ScorePageInterpretation score, String sha, int page, int count) {
        if (sha == null
                || !sha.matches("[0-9a-f]{64}")
                || page < 0
                || page >= count
                || count > 10000)
            throw new IllegalArgumentException("Incomplete source document/page envelope");
        var context = score.clockContextSource();
        var origin = context.origin();
        if (!origin.documentSha256().isEmpty() && !origin.documentSha256().equals(sha)
                || origin.physicalPageIndex() >= 0 && origin.physicalPageIndex() != page
                || origin.physicalPageCount() > 0 && origin.physicalPageCount() != count)
            throw new IllegalArgumentException("Foreign source document/page");
        var bound =
                new ScoreClockContextSource.Origin(
                        sha,
                        page,
                        count,
                        origin.width(),
                        origin.height(),
                        origin.rasterSha256(),
                        origin.labelsSha256(),
                        origin.annotationRasterSha256());
        var missing =
                context.missing().stream()
                        .filter(
                                m ->
                                        !bound.boundDocument()
                                                || m.requirement()
                                                        != ScoreClockContextSource.Requirement
                                                                .DOCUMENT_ORIGIN
                                                || m.measure() != -1)
                        .toList();
        return score.withClockContextSource(
                new ScoreClockContextSource(
                        bound,
                        context.noteCount(),
                        context.measureCount(),
                        context.restCount(),
                        context.partWords(),
                        context.numbers(),
                        context.meters(),
                        context.printedValues(),
                        context.glyphLedgers(),
                        context.restValues(),
                        context.voiceGroups(),
                        context.syncAnchors(),
                        missing));
    }

    /** Validate a stored page against the cache's existing host envelope; unknown stays unknown. */
    public static boolean matchesPage(
            ScorePageInterpretation score, String sha, int page, int count) {
        if (score == null) return false;
        var context = score.clockContextSource();
        var origin = context.origin();
        return (origin.documentSha256().isEmpty() || origin.documentSha256().equals(sha))
                && (origin.physicalPageIndex() < 0 || origin.physicalPageIndex() == page)
                && (count <= 0
                        || origin.physicalPageCount() <= 0
                        || origin.physicalPageCount() == count);
    }

    public static Intake intake(
            String documentSha256, int physicalPageCount, List<ScorePageInterpretation> pages) {
        if (documentSha256 == null
                || !documentSha256.matches("[0-9a-f]{64}")
                || physicalPageCount < 1
                || physicalPageCount > 10000
                || pages.size() != physicalPageCount)
            throw new IllegalArgumentException("Incomplete host document/page envelope");
        var retained = new ArrayList<Page>();
        for (int p = 0; p < pages.size(); p++) {
            var score = Objects.requireNonNull(pages.get(p));
            var context = score.clockContextSource();
            context.validateCounts(
                    score.notes().size(), score.measures().size(), score.rests().size());
            var origin = context.origin();
            if (!origin.documentSha256().isEmpty()
                            && !origin.documentSha256().equals(documentSha256)
                    || origin.physicalPageIndex() >= 0 && origin.physicalPageIndex() != p
                    || origin.physicalPageCount() > 0
                            && origin.physicalPageCount() != physicalPageCount)
                throw new IllegalArgumentException("Foreign cached source document/page");
            retained.add(new Page(p, context, origin.boundDocument()));
        }
        return new Intake(documentSha256, physicalPageCount, retained);
    }
}
