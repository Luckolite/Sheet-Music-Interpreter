// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PartProducerSourceOriginalTest {
    static final int W = 300, H = 300;
    static final List<MeasureRegion> rows =
            List.of(
                    new MeasureRegion(.25f, .55f, .2f, .35f),
                    new MeasureRegion(.56f, .9f, .2f, .35f),
                    new MeasureRegion(.25f, .55f, .4f, .55f),
                    new MeasureRegion(.56f, .9f, .4f, .55f));

    static TablatureDecoder.Word word(String text, float y) {
        return new TablatureDecoder.Word(text, .05f, y, .15f, y + .025f);
    }

    static TablatureDecoder.Word number(String text, float y) {
        return new TablatureDecoder.Word(text, .18f, y, .21f, y + .02f);
    }

    static final class Page {
        byte[] gray = new byte[W * H];
        List<TablatureDecoder.Word> words;
        List<MeasureNumberReconciler.NumberToken> numbers;
        ScorePageAnnotationSource source;

        Page(boolean conflict, boolean absent, boolean unbound) {
            Arrays.fill(gray, (byte) 255);
            var list = new ArrayList<TablatureDecoder.Word>();
            list.add(word("Piano", .26f));
            if (!absent) list.add(word("Pno.", .46f));
            if (conflict) list.add(word("Violin", .26f));
            list.add(number("5", .16f));
            list.add(number("7", .36f));
            words = List.copyOf(list);
            numbers = new ArrayList<>();
            for (var word : words) {
                for (int y = (int) (word.top() * H); y <= (int) (word.bottom() * H); y++)
                    for (int x = (int) (word.left() * W); x <= (int) (word.right() * W); x++)
                        if ((x + y) % 3 == 0) gray[y * W + x] = 0;
                if (word.text().matches("[0-9]+"))
                    numbers.add(
                            new MeasureNumberReconciler.NumberToken(
                                    Integer.parseInt(word.text()) + (unbound ? 1 : 0),
                                    word.left(),
                                    word.top(),
                                    word.right(),
                                    word.bottom()));
            }
            source = ScorePageAnnotationSource.fromReader(gray, W, H, words, numbers);
        }

        ScorePartProducerSource.Data data() {
            return ScorePartProducerSource.capture(source, gray, W, H, rows).data();
        }
    }

    @Test
    public void realReaderLabelsAliasesAndNumberRouteRemainSeparate() {
        var page = new Page(false, false, false);
        var before = MeasureNumberReconciler.firstMeasureNumber(rows, page.numbers);
        var data = page.data();
        assertEquals(before, MeasureNumberReconciler.firstMeasureNumber(rows, page.numbers));
        assertEquals(5, data.originalNumberOriginCandidate());
        assertEquals(2, data.parts().size());
        for (var part : data.parts()) {
            assertEquals("piano", part.canonicalPart());
            assertEquals("CONSISTENT_SOURCE_LABEL_OBSERVATION", part.status());
        }
        assertEquals(2, data.numbers().size());
        assertTrue(data.numbers().stream().allMatch(n -> n.status().startsWith("BOUND")));
        assertTrue(
                data.missingAuthorities()
                        .contains("COMPLETE_VOICE_REST_AND_SYNCHRONIZATION_SOURCE"));
    }

    @Test
    public void rivalOrMissingPartLabelNeverProducesPartCompleteness() {
        for (var page : List.of(new Page(true, false, false), new Page(false, true, false)))
            assertTrue(
                    page.data().parts().stream()
                            .anyMatch(part -> part.status().startsWith("UNKNOWN")));
    }

    @Test
    public void numericTripleWithoutActualMatchingWordRemainsUnknown() {
        var page = new Page(false, false, true);
        assertTrue(page.data().numbers().stream().allMatch(n -> n.status().startsWith("UNKNOWN")));
    }

    @Test
    public void foreignEqualRasterOrMutationRefusesSourceBinding() {
        var page = new Page(false, false, false);
        assertFalse(page.source.authenticates(page.gray.clone(), W, H));
        page.gray[0] = 0;
        var data = page.data();
        assertEquals(page.words.size(), data.words().size());
        assertEquals(0, data.originalNumberOriginCandidate());
        assertTrue(data.parts().stream().allMatch(part -> part.status().startsWith("UNKNOWN")));
    }

    @Test
    public void observationsAndSupportsAreImmutable() {
        var data = new Page(false, false, false).data();
        assertThrows(UnsupportedOperationException.class, () -> data.words().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> data.words().get(0).sampledInk().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.numbers().clear());
    }

    @Test
    public void actualSingleAnchorFallbackWitnessIsPreservedRatherThanCalledARival() {
        var regions = new ArrayList<MeasureRegion>();
        int[] counts = {5, 5, 6, 6};
        for (int row = 0; row < 4; row++)
            for (int i = 0; i < counts[row]; i++)
                regions.add(
                        new MeasureRegion(
                                .25f + i * .05f,
                                .295f + i * .05f,
                                .1f + row * .2f,
                                .2f + row * .2f));
        var tokens =
                List.of(
                        new MeasureNumberReconciler.NumberToken(6, .18f, .28f, .21f, .30f),
                        new MeasureNumberReconciler.NumberToken(17, .18f, .68f, .21f, .70f));
        assertEquals(1, MeasureNumberReconciler.firstMeasureNumber(regions, tokens));
        var routes = MeasureNumberReconciler.producerNumberRoutes(regions, tokens);
        assertEquals("ACTUAL_SINGLE_LATER_ANCHOR_MEASURE_ONE_RETURN", routes.get(0).disposition());
        assertTrue(routes.get(0).assigned());
        assertEquals(1, routes.get(0).originCandidate());
        assertFalse(routes.get(1).assigned());
        assertEquals(1, routes.get(1).originCandidate());
    }

    @Test
    public void exhaustedContextWorkRetainsAllReaderRecordsAsUnknown() {
        var page = new Page(false, false, false);
        var many = new ArrayList<MeasureNumberReconciler.NumberToken>();
        for (int i = 0; i < 2001; i++) many.add(page.numbers.get(0));
        var source = ScorePageAnnotationSource.fromReader(page.gray, W, H, page.words, many);
        var data = ScorePartProducerSource.capture(source, page.gray, W, H, rows).data();
        assertEquals(page.words.size(), data.words().size());
        assertEquals(2001, data.numbers().size());
        assertEquals(0, data.originalNumberOriginCandidate());
        assertTrue(
                data.numbers().stream()
                        .allMatch(row -> row.status().equals("UNKNOWN_CONTEXT_WORK_BUDGET")));
    }
}
