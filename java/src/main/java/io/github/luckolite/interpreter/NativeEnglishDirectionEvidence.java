// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Conservative desktop musical-role admission from independently matched literal dynamic ink. */
final class NativeEnglishDirectionEvidence {
    enum LiteralOrigin {
        INDEPENDENT_TEMPLATE,
        AGREED_LITERAL_2X_3X
    }

    record LiteralEvidence(
            PlayingTechniqueDetector.Word word,
            LiteralOrigin origin,
            NativeDynamicGlyphs.Literal glyph,
            PaperDynamicLiteralReproof.Proof agreement) {}

    record Evidence(
            PlayingTechniqueDetector.Word word,
            LiteralEvidence literal,
            int measureIndex,
            int staffIndex,
            int staffCount,
            float writtenColumn,
            OcrText.Grouping grouping) {}

    static List<Evidence> collect(
            OcrText text,
            float scale,
            int top,
            List<NativeDynamicGlyphs.Literal> literals,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height) {
        return collectCore(
                text,
                scale,
                top,
                literals.stream()
                        .map(
                                p ->
                                        new LiteralEvidence(
                                                p.word(),
                                                LiteralOrigin.INDEPENDENT_TEMPLATE,
                                                p,
                                                null))
                        .toList(),
                staffs,
                measures,
                notes,
                gray,
                width,
                height);
    }

    static List<Evidence> collectAgreed(
            OcrText text,
            float scale,
            int top,
            List<PaperDynamicLiteralReproof.Proof> proofs,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height) {
        return collectCore(
                text,
                scale,
                top,
                proofs.stream()
                        .map(
                                p ->
                                        new LiteralEvidence(
                                                p.word(),
                                                LiteralOrigin.AGREED_LITERAL_2X_3X,
                                                null,
                                                p))
                        .toList(),
                staffs,
                measures,
                notes,
                gray,
                width,
                height);
    }

    private static List<Evidence> collectCore(
            OcrText text,
            float scale,
            int top,
            List<LiteralEvidence> literals,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height) {
        var out = new ArrayList<Evidence>();
        var provedWords = literals.stream().map(LiteralEvidence::word).toList();
        for (var e :
                CharacterDirectionEvidence.collect(
                        text,
                        scale,
                        top,
                        provedWords,
                        staffs,
                        measures,
                        notes,
                        gray,
                        width,
                        height)) {
            var proof = literals.stream().filter(p -> p.word().equals(e.literal())).findFirst();
            if (proof.isPresent())
                out.add(
                        new Evidence(
                                e.word(),
                                proof.get(),
                                e.measureIndex(),
                                e.staffIndex(),
                                e.staffCount(),
                                e.writtenColumn(),
                                e.grouping()));
        }
        return List.copyOf(out);
    }
}
