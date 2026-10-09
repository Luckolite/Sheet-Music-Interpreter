// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rules and OCR boxes exercise the complete public entry point after tab masking. */
public final class TabBoundaryEntryPointTest {
    private static final int W = 500, H = 420, GAP = 20;

    private record Page(byte[] labels, byte[] gray, List<SheetInterpreter.Word> words) {}

    private static SheetInterpreter.Word word(String text, int x, int y, int width, int height) {
        return new SheetInterpreter.Word(
                text,
                (x - width * .5f) / W,
                (y - height * .5f) / H,
                (x + width * .5f) / W,
                (y + height * .5f) / H);
    }

    private static void rules(byte[] gray, int top) {
        for (int line = 0; line < 6; line++)
            for (int x = 20; x <= 480; x++) gray[(top + line * GAP) * W + x] = 0;
        for (int x : new int[] {20, 250, 480})
            for (int y = top; y <= top + 5 * GAP; y++) gray[y * W + x] = 0;
    }

    private static void bow(byte[] gray, int left, int right, int cy, int rise) {
        int previous = cy - 3;
        for (int x = left; x <= right; x++) {
            int y =
                    Math.round(
                            cy
                                    - 3
                                    - rise
                                            * (float)
                                                    Math.sin(
                                                            Math.PI * (x - left) / (right - left)));
            int steps = Math.max(1, Math.abs(y - previous));
            for (int step = 0; step <= steps; step++) {
                float fraction = step / (float) steps;
                int px = x == left ? x : Math.round(x - 1 + fraction);
                int py = Math.round(previous + (y - previous) * fraction);
                gray[py * W + px] = 0;
            }
            previous = y;
        }
    }

    private static Page page(boolean outgoing, boolean incoming, boolean explicitTap) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        rules(gray, 60);
        rules(gray, 240);
        if (outgoing) bow(gray, 370, 476, 100, 7);
        if (incoming) bow(gray, 26, 45, 280, 4);
        var words = new ArrayList<SheetInterpreter.Word>();
        words.add(word("7", 360, 100, 12, 18));
        words.add(word("7", 55, 280, 12, 18));
        if (explicitTap) words.add(word("T", 55, 210, 10, 12));
        return new Page(new byte[gray.length], gray, List.copyOf(words));
    }

    private static ScorePageInterpretation analyze(Page page) {
        var annotations =
                new SheetInterpreter.Annotations(
                        List.of(), List.of(), List.of(), List.of(), List.of(), page.words());
        return SheetInterpreter.analyze(page.labels(), page.gray(), W, H, annotations);
    }

    @Test
    public void completeEntryPointRetainsBothShouldersAfterStandardOmrMasking() {
        var page = page(true, true, false);
        byte[] original = page.gray().clone();
        var detected = TablatureDecoder.detect(page.gray(), W, H);
        assertEquals(2, detected.size());
        // Establish the integration condition: the normal OMR raster erases
        // both shoulders, but the full entry point must retain the source raster.
        byte[] masked = TablatureDecoder.withoutTabs(page.gray(), W, H, detected, true);
        assertEquals(255, masked[97 * W + 370] & 255);
        assertEquals(255, masked[277 * W + 26] & 255);
        assertEquals(0, original[97 * W + 370] & 255);
        assertEquals(0, original[277 * W + 26] & 255);
        var score = analyze(page);
        assertEquals(2, score.notes().size());
        assertFalse(score.notes().get(0).tiedFromPrevious());
        assertTrue(score.notes().get(1).tiedFromPrevious());
        assertEquals(
                score.notes().get(0).diatonicPitchIdentity(),
                score.notes().get(1).diatonicPitchIdentity());
        assertArrayEquals(original, page.gray());
    }

    @Test
    public void completeEntryPointStillRequiresBothIndependentShoulders() {
        for (boolean outgoing : new boolean[] {false, true}) {
            var score = analyze(page(outgoing, !outgoing, false));
            assertEquals(2, score.notes().size());
            assertTrue(score.notes().stream().noneMatch(ScoreNoteEvent::tiedFromPrevious));
        }
    }

    @Test
    public void completeEntryPointPreservesAnExplicitIncomingTapAsAnAttack() {
        var score = analyze(page(true, true, true));
        assertEquals(2, score.notes().size());
        assertFalse(score.notes().get(1).tiedFromPrevious());
        assertEquals(TabEffect.TAP, TabEffect.kind(score.notes().get(1).articulations()));
    }
}
