// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RawReaderSourceOriginalTest {
    @Test
    public void actualReaderBoundsAndAllRivalsPassThroughWithoutReinterpretation() {
        var p = new PartProducerSourceOriginalTest.Page(true, false, false);
        var words =
                p.words.stream()
                        .map(
                                v ->
                                        new ScorePageAnnotationSource.ReaderWord(
                                                v.text(), v.left(), v.top(), v.right(), v.bottom()))
                        .toList();
        var source = ScorePageAnnotationSource.fromRawReader(p.gray, 300, 300, words, p.numbers);
        var data =
                ScorePartProducerSource.capture(
                                source, p.gray, 300, 300, PartProducerSourceOriginalTest.rows)
                        .data();
        assertEquals(p.words.size(), data.words().size());
        assertTrue(data.words().stream().anyMatch(v -> v.canonicalPart().equals("violin")));
        assertTrue(data.parts().stream().anyMatch(v -> v.status().startsWith("UNKNOWN")));
    }

    @Test
    public void absentInvalidAndOutsideBoundsRemainUnknownWordsAndDoNotFailThePage() {
        var p = new PartProducerSourceOriginalTest.Page(false, false, false);
        var words = new ArrayList<ScorePageAnnotationSource.ReaderWord>();
        for (var v : p.words)
            words.add(
                    new ScorePageAnnotationSource.ReaderWord(
                            v.text(), v.left(), v.top(), v.right(), v.bottom()));
        words.add(
                new ScorePageAnnotationSource.ReaderWord(
                        "Pno.", Float.NaN, Float.NaN, Float.NaN, Float.NaN));
        words.add(new ScorePageAnnotationSource.ReaderWord("8", -.1f, .1f, .1f, .2f));
        words.add(new ScorePageAnnotationSource.ReaderWord("3/4", .1f, .2f, .1f, .2f));
        var source = ScorePageAnnotationSource.fromRawReader(p.gray, 300, 300, words, p.numbers);
        var parts =
                ScorePartProducerSource.capture(
                        source, p.gray, 300, 300, PartProducerSourceOriginalTest.rows);
        assertEquals(words.size(), parts.data().words().size());
        assertEquals(
                3,
                parts.data().words().stream()
                        .filter(v -> v.status().equals("UNKNOWN_INVALID_OR_ABSENT_READER_BOUNDS"))
                        .count());
        var score = new ScorePageInterpretation(PartProducerSourceOriginalTest.rows, List.of());
        var context =
                ScoreClockContextCapture.capture(
                        new byte[90000], p.gray, 300, 300, score, null, parts, null);
        assertEquals(words.size(), context.partWords().size());
        assertEquals(
                3,
                context.partWords().stream()
                        .filter(
                                v ->
                                        v.role()
                                                == ScoreClockContextSource.WordRole
                                                        .UNKNOWN_LABEL_RIVAL)
                        .count());
        assertTrue(
                context.missing().stream()
                        .anyMatch(
                                v ->
                                        v.requirement()
                                                == ScoreClockContextSource.Requirement
                                                        .PART_CONTINUITY));
    }

    @Test
    public void unsupportedObservationSizeDoesNotSuppressValidScoreAndRemainsMissing() {
        var p = new PartProducerSourceOriginalTest.Page(false, false, false);
        var word = new ScorePageAnnotationSource.ReaderWord("z".repeat(1025), .1f, .1f, .2f, .2f);
        var source =
                ScorePageAnnotationSource.fromRawReader(p.gray, 300, 300, List.of(word), p.numbers);
        var parts =
                ScorePartProducerSource.capture(
                        source, p.gray, 300, 300, PartProducerSourceOriginalTest.rows);
        var score = new ScorePageInterpretation(PartProducerSourceOriginalTest.rows, List.of());
        var context =
                ScoreClockContextCapture.capture(
                        new byte[90000], p.gray, 300, 300, score, null, parts, null);
        assertEquals(0, context.noteCount());
        assertEquals(4, context.measureCount());
        assertTrue(
                context.missing().stream()
                        .anyMatch(
                                v ->
                                        v.requirement()
                                                == ScoreClockContextSource.Requirement
                                                        .CAPTURE_BUDGET));
        assertTrue(context.printedValues().isEmpty());
    }

    @Test
    public void parsedNumericValueDoesNotReplaceItsActualReaderText() {
        var p = new PartProducerSourceOriginalTest.Page(false, false, false);
        var token = p.numbers.get(0);
        var word =
                new ScorePageAnnotationSource.ReaderWord(
                        "5?", token.left(), token.top(), token.right(), token.bottom());
        var source =
                ScorePageAnnotationSource.fromRawReader(
                        p.gray, 300, 300, List.of(word), List.of(token));
        var data =
                ScorePartProducerSource.capture(
                                source, p.gray, 300, 300, PartProducerSourceOriginalTest.rows)
                        .data();
        assertEquals("5?", data.words().get(0).text());
        assertTrue(data.numbers().get(0).status().startsWith("UNKNOWN"));
    }
}
