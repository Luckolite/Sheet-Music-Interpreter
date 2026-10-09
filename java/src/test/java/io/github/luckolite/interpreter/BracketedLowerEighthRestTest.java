// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural lower eighth rest with a separate bracket and moving beam voice. */
public class BracketedLowerEighthRestTest {
    static final int W = 800, H = 360;

    static class Page {
        final byte[] gray = new byte[W * H];
        final boolean held;

        Page(boolean glyph, boolean tail, boolean bracket, boolean held) {
            this.held = held;
            Arrays.fill(gray, (byte) 255);
            for (int y = 80; y <= 144; y += 16) line(20, y, 780, y, 1);
            if (glyph) oval(180, 165, 7, 5);
            if (tail) line(190, 160, 178, 190, 2);
            oval(255, 128, 11, 7);
            oval(335, 112, 11, 7);
            line(245, 128, 245, 178, 2);
            line(325, 112, 325, 174, 2);
            line(245, 178, 325, 174, 5);
            if (bracket) {
                line(173, 185, 173, 206, 1);
                line(173, 206, 250, 206, 1);
                line(290, 206, 360, 206, 1);
                line(360, 185, 360, 206, 1);
            }
        }

        void line(int x0, int y0, int x1, int y1, int thick) {
            int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
            for (int i = 0; i <= steps; i++) {
                float f = steps == 0 ? 0 : i / (float) steps;
                int x = Math.round(x0 + f * (x1 - x0)), y = Math.round(y0 + f * (y1 - y0));
                for (int dx = 0; dx < thick; dx++)
                    for (int dy = 0; dy < thick; dy++) gray[(y + dy) * W + x + dx] = 0;
            }
        }

        void oval(int cx, int cy, int rx, int ry) {
            for (int y = cy - ry; y <= cy + ry; y++)
                for (int x = cx - rx; x <= cx + rx; x++)
                    if (Math.pow((x - cx) / (double) rx, 2) + Math.pow((y - cy) / (double) ry, 2)
                            <= 1) gray[y * W + x] = 0;
        }

        List<ScoreRestEvent> target() {
            var notes =
                    new ArrayList<>(
                            List.of(
                                    new ScoreNoteEvent(0, 255f / W, 2, 0, 1, 128f / H, false, 0, 1)
                                            .withStemDirection(-1),
                                    new ScoreNoteEvent(0, 335f / W, 4, 0, 1, 112f / H, false, 0, 1)
                                            .withStemDirection(-1)));
            if (held)
                notes.add(new ScoreNoteEvent(0, 100f / W, 5, 0, 1, 100f / H, false, 0, 0, 2, 4));
            return SixteenthRestDetector.detect(
                            gray,
                            W,
                            H,
                            List.of(new MeasureRegion(0, 1, .1f, .9f)),
                            List.of(new SixteenthRestDetector.Staff(80, 144, 16, 0, 1)),
                            notes)
                    .stream()
                    .filter(
                            r ->
                                    Math.abs(r.positionInMeasure() - 180f / W) < .03f
                                            && r.pageY() > 150f / H)
                    .toList();
        }
    }

    @Test
    public void completeLowerEighthRestRemainsReadableInsideItsBracket() {
        var r = new Page(true, true, true, true).target();
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.5, r.get(0).durationBeats(), 0);
    }

    @Test
    public void isolatedLowerEighthRestRemainsReadable() {
        var r = new Page(true, true, false, true).target();
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.5, r.get(0).durationBeats(), 0);
    }

    @Test
    public void bracketWithoutGlyphCannotCreateSilence() {
        assertTrue(new Page(false, false, true, true).target().isEmpty());
    }

    @Test
    public void roundBulbWithoutTailCannotCreateSilence() {
        assertTrue(new Page(true, false, true, true).target().isEmpty());
    }

    @Test
    public void sourcePixelsArePreserved() {
        var p = new Page(true, true, true, true);
        var before = p.gray.clone();
        p.target();
        assertArrayEquals(before, p.gray);
    }

    @Test
    public void printedBracketAndBeamDoNotRequireAnotherHeldPitchedHead() {
        var r = new Page(true, true, true, false).target();
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.5, r.get(0).durationBeats(), 0);
    }
}
