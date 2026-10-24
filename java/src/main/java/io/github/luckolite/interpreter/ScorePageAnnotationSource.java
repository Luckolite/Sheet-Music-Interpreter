// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.security.*;

/** Reader-output provenance retained before the existing loader drops its source frame.
 * These are OCR observations, not a claim of correct text, complete parts, or rhythmic coverage.
 */
final class ScorePageAnnotationSource {
    private final byte[] raster;
    private final int width, height;
    private final String rawSha;

    /** Literal reader observations retain invalid or absent bounds as unknown data. */
    record ReaderWord(String text, float left, float top, float right, float bottom) {
        ReaderWord {
            Objects.requireNonNull(text);
        }
    }

    private final List<ReaderWord> words;
    private final List<MeasureNumberReconciler.NumberToken> numbers;

    private ScorePageAnnotationSource(
            byte[] raster,
            int w,
            int h,
            String sha,
            List<ReaderWord> words,
            List<MeasureNumberReconciler.NumberToken> numbers) {
        this.raster = raster;
        width = w;
        height = h;
        rawSha = sha;
        this.words = List.copyOf(words);
        this.numbers = List.copyOf(numbers);
    }

    static ScorePageAnnotationSource unavailable() {
        return new ScorePageAnnotationSource(null, 0, 0, "", List.of(), List.of());
    }

    static ScorePageAnnotationSource fromReader(
            byte[] gray,
            int w,
            int h,
            List<TablatureDecoder.Word> words,
            List<MeasureNumberReconciler.NumberToken> numbers) {
        if (words == null) return unavailable();
        return fromRawReader(
                gray,
                w,
                h,
                words.stream()
                        .map(
                                v ->
                                        new ReaderWord(
                                                v.text(), v.left(), v.top(), v.right(), v.bottom()))
                        .toList(),
                numbers);
    }

    static ScorePageAnnotationSource fromRawReader(
            byte[] gray,
            int w,
            int h,
            List<ReaderWord> words,
            List<MeasureNumberReconciler.NumberToken> numbers) {
        if (gray == null
                || (long) w * h != gray.length
                || w < 1
                || h < 1
                || words == null
                || numbers == null
                || words.size() > 10000
                || numbers.size() > 10000) return unavailable();
        return new ScorePageAnnotationSource(gray, w, h, digest(gray), words, numbers);
    }

    private static String digest(byte[] bytes) {
        try {
            return RasterEvidenceIdentity.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    boolean authenticates(byte[] gray, int w, int h) {
        return gray != null
                && gray == raster
                && w == width
                && h == height
                && rawSha.equals(digest(gray));
    }

    String rawSha() {
        return rawSha;
    }

    List<ReaderWord> words() {
        return words;
    }

    List<MeasureNumberReconciler.NumberToken> numbers() {
        return numbers;
    }
}
