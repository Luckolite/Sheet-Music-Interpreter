// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedDashSpanTest {
    final List<PrintedDashSpan.SystemBand> rows =
            List.of(
                    new PrintedDashSpan.SystemBand(.1f, .9f, .1f, .2f),
                    new PrintedDashSpan.SystemBand(.1f, .9f, .4f, .5f),
                    new PrintedDashSpan.SystemBand(.1f, .9f, .7f, .8f));
    final PrintedDashSpan.WordBox word = new PrintedDashSpan.WordBox(.3f, .4f, .21f, .24f);

    PrintedDashChains.Run run(float left, float right, float y) {
        return new PrintedDashChains.Run(left, right, y, 20);
    }

    Optional<PrintedDashSpan.Span> follow(List<PrintedDashChains.Run> runs) {
        return PrintedDashSpan.follow(runs, rows, word, .01f, .01f);
    }

    @Test
    public void followsAdjacentEdgesToInternalEnd() {
        var s =
                follow(List.of(run(.4f, .9f, .22f), run(.1f, .9f, .52f), run(.1f, .65f, .82f)))
                        .orElseThrow();
        assertEquals(3, s.runs().size());
        assertEquals(PrintedDashSpan.End.CLOSED, s.end());
    }

    @Test
    public void pageEdgeRemainsOpen() {
        var s =
                follow(List.of(run(.4f, .9f, .22f), run(.1f, .9f, .52f), run(.1f, .9f, .82f)))
                        .orElseThrow();
        assertEquals(PrintedDashSpan.End.PAGE_EDGE, s.end());
    }

    @Test
    public void missingRowDoesNotBridge() {
        var s = follow(List.of(run(.4f, .9f, .22f), run(.1f, .9f, .82f))).orElseThrow();
        assertEquals(1, s.runs().size());
        assertEquals(PrintedDashSpan.End.MISSING_CONTINUATION, s.end());
    }

    @Test
    public void newMidSystemRunIsNotContinuation() {
        var s = follow(List.of(run(.4f, .9f, .22f), run(.4f, .9f, .52f))).orElseThrow();
        assertEquals(PrintedDashSpan.End.MISSING_CONTINUATION, s.end());
    }

    @Test
    public void competingContinuationsRemainUnresolved() {
        assertTrue(
                follow(List.of(run(.4f, .9f, .22f), run(.1f, .9f, .52f), run(.11f, .9f, .53f)))
                        .isEmpty());
    }

    @Test
    public void missingWordConnectionRemainsUnresolved() {
        assertTrue(follow(List.of(run(.5f, .9f, .22f))).isEmpty());
    }

    @Test
    public void trailingEngravingWhitespaceDoesNotEndTheSpan() {
        var bands = List.of(new PrintedDashSpan.SystemBand(.1f, .9f, .1f, .2f, .84f), rows.get(1));
        var span =
                PrintedDashSpan.follow(
                                List.of(run(.4f, .835f, .22f), run(.1f, .65f, .52f)),
                                bands,
                                word,
                                .01f,
                                .01f)
                        .orElseThrow();
        assertEquals(2, span.runs().size());
        assertEquals(PrintedDashSpan.End.CLOSED, span.end());
    }
}
