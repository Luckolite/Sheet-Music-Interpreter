// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SparseTempoDashTest {
    List<PrintedDashChains.Run> detect(int[] xs, int h) {
        byte[] g = new byte[600 * 100];
        Arrays.fill(g, (byte) 255);
        for (int x : xs)
            for (int y = 30; y < 30 + h; y++)
                for (int xx = x; xx < x + 9; xx++) g[y * 600 + xx] = 0;
        return PrintedDashChains.detect(g, 600, 100, 20);
    }

    @Test
    public void sparseSeven() {
        assertEquals(7, detect(new int[] {40, 100, 160, 220, 280, 340, 400}, 3).get(0).dashes());
    }

    @Test
    public void ellipsis() {
        assertTrue(detect(new int[] {40, 100, 160}, 3).isEmpty());
    }

    @Test
    public void irregular() {
        assertTrue(detect(new int[] {40, 70, 140, 170, 240, 270, 340}, 3).isEmpty());
    }

    @Test
    public void thick() {
        assertTrue(detect(new int[] {40, 100, 160, 220, 280, 340, 400}, 6).isEmpty());
    }

    @Test
    public void denseUnique() {
        assertEquals(
                1, detect(new int[] {40, 60, 80, 100, 120, 140, 160, 180, 200, 220}, 2).size());
    }

    List<PrintedDashSpan.SystemBand> bands =
            List.of(
                    new PrintedDashSpan.SystemBand(.1f, .9f, .2f, .3f),
                    new PrintedDashSpan.SystemBand(.1f, .9f, .5f, .6f));

    PrintedDashSpan.Span span() {
        return PrintedDashSpan.follow(
                        List.of(new PrintedDashChains.Run(.4f, .9f, .18f, 7)),
                        bands,
                        new PrintedDashSpan.WordBox(.3f, .4f, .17f, .19f),
                        .01f,
                        .01f)
                .orElseThrow();
    }

    Optional<ScoreAnchor> end(List<ScoreTempoChange> ts) {
        return PrintedDashEndpoint.numeric(
                span(),
                bands,
                List.of(
                        new MeasureRegion(.1f, .9f, .2f, .3f),
                        new MeasureRegion(.1f, .5f, .5f, .6f),
                        new MeasureRegion(.5f, .9f, .5f, .6f)),
                ts,
                new ScoreAnchor(0, 1),
                .01f,
                .01f);
    }

    @Test
    public void aboveStaffAndNextOpening() {
        assertEquals(PrintedDashSpan.End.MISSING_CONTINUATION, span().end());
        assertEquals(
                Optional.of(new ScoreAnchor(1, 0)), end(List.of(new ScoreTempoChange(1, 0, 90))));
    }

    @Test
    public void noTempoNoEndpoint() {
        assertTrue(end(List.of()).isEmpty());
    }

    @Test
    public void laterBarNotOpening() {
        assertTrue(end(List.of(new ScoreTempoChange(2, 0, 90))).isEmpty());
    }

    @Test
    public void midBarNotOpening() {
        assertTrue(end(List.of(new ScoreTempoChange(1, .5f, 90))).isEmpty());
    }

    @Test
    public void conflictingOpening() {
        assertTrue(
                end(List.of(new ScoreTempoChange(1, 0, 90), new ScoreTempoChange(1, 0, 100)))
                        .isEmpty());
    }

    @Test
    public void ambiguousStaffAbstains() {
        assertTrue(
                PrintedDashSpan.follow(
                                List.of(new PrintedDashChains.Run(.4f, .9f, .18f, 7)),
                                List.of(
                                        new PrintedDashSpan.SystemBand(.1f, .9f, .06f, .16f),
                                        bands.get(0)),
                                new PrintedDashSpan.WordBox(.3f, .4f, .17f, .19f),
                                .01f,
                                .01f)
                        .isEmpty());
    }
}
