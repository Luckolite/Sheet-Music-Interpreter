// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Newly drawn italic cap/body and counterexamples, with no source score pixels. */
public final class PrintedDynamicInkColumnOriginalTest {
    static final int W = 300, H = 240;
    static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(70, 110, 12, 0, 1);
    static final List<MeasureRegion> BARS = List.of(new MeasureRegion(0, 1, .1f, .6f));
    static final PlayingTechniqueDetector.Word WORD =
            new PlayingTechniqueDetector.Word("f", .4f, .5f, .5f, .65f);

    static ScoreNoteEvent note(float x, int staff, int count) {
        return new ScoreNoteEvent(0, x, 2, staff, count, .4f, false);
    }

    static byte[] raw() {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 122; y < 154; y++) for (int x = 129; x < 134; x++) g[y * W + x] = 0;
        for (int y = 121; y < 126; y++) for (int x = 129; x < 149; x++) g[y * W + x] = 0;
        return g;
    }

    static float anchor(PlayingTechniqueDetector.Word word, List<ScoreNoteEvent> ns, byte[] g) {
        var original = g == null ? null : g.clone();
        float r = PrintedDynamicInkColumn.anchor(word, STAFF, BARS, ns, g, W, H, .4f, 0);
        if (g != null) assertArrayEquals(original, g);
        return r;
    }

    @Test
    public void italicCapOverUniqueRightSideHeadOwnsThatColumn() {
        assertEquals(.49f, anchor(WORD, List.of(note(.49f, 0, 1)), raw()), 0);
    }

    @Test
    public void pianoUsesTheSameCoveredConnectedBodyRule() {
        var p =
                new PlayingTechniqueDetector.Word(
                        "p", WORD.left(), WORD.top(), WORD.right(), WORD.bottom());
        assertEquals(.49f, anchor(p, List.of(note(.49f, 0, 1)), raw()), 0);
    }

    @Test
    public void missingRawPixelsCannotCreateAnAnchor() {
        assertEquals(.4f, anchor(WORD, List.of(note(.49f, 0, 1)), null), 0);
    }

    @Test
    public void isolatedPixelCannotSubstituteForLetterBody() {
        var g = raw();
        Arrays.fill(g, (byte) 255);
        g[123 * W + 147] = 0;
        assertEquals(.4f, anchor(WORD, List.of(note(.49f, 0, 1)), g), 0);
    }

    @Test
    public void horizontalRuleCannotSubstituteForLetterBody() {
        var g = raw();
        Arrays.fill(g, (byte) 255);
        for (int x = 120; x < 150; x++) for (int y = 123; y < 125; y++) g[y * W + x] = 0;
        assertEquals(.4f, anchor(WORD, List.of(note(.49f, 0, 1)), g), 0);
    }

    @Test
    public void verticalRuleCannotSubstituteForLetterBody() {
        var g = raw();
        Arrays.fill(g, (byte) 255);
        for (int y = 120; y < 156; y++) g[y * W + 147] = 0;
        assertEquals(.4f, anchor(WORD, List.of(note(.49f, 0, 1)), g), 0);
    }

    @Test
    public void twoCoveredColumnsRemainAmbiguous() {
        assertEquals(.4f, anchor(WORD, List.of(note(.45f, 0, 1), note(.49f, 0, 1)), raw()), 0);
    }

    @Test
    public void otherPartCannotSupplyCoveredHead() {
        assertEquals(.4f, anchor(WORD, List.of(note(.49f, 1, 2)), raw()), 0);
    }

    @Test
    public void mixedTopologyCannotSupplyCoveredHead() {
        assertEquals(.4f, anchor(WORD, List.of(note(.49f, 0, 2)), raw()), 0);
    }

    @Test
    public void aColumnOutsideGlyphBodyCannotBeBorrowed() {
        assertEquals(.4f, anchor(WORD, List.of(note(.52f, 0, 1)), raw()), 0);
    }

    @Test
    public void unknownOrWideWordRetainsItsEstablishedAnchor() {
        assertEquals(
                .4f,
                anchor(
                        new PlayingTechniqueDetector.Word("forte", .4f, .5f, .5f, .65f),
                        List.of(note(.49f, 0, 1)),
                        raw()),
                0);
        assertEquals(
                .4f,
                anchor(
                        new PlayingTechniqueDetector.Word("f", .2f, .5f, .5f, .65f),
                        List.of(note(.49f, 0, 1)),
                        raw()),
                0);
    }

    @Test
    public void blankColumnInsideBoxCannotBorrowDisconnectedInk() {
        assertEquals(.4f, anchor(WORD, List.of(note(.401f, 0, 1)), raw()), 0);
    }

    @Test
    public void aClippedConnectedGlyphCannotProveItsOwnBody() {
        var g = raw();
        for (int x = 148; x < 153; x++) g[123 * W + x] = 0;
        assertEquals(.4f, anchor(WORD, List.of(note(.49f, 0, 1)), g), 0);
    }

    @Test
    public void connectedInkOutsideTheBottomCannotBeIgnored() {
        var g = raw();
        for (int y = 153; y < 159; y++) g[y * W + 131] = 0;
        assertEquals(.4f, anchor(WORD, List.of(note(.49f, 0, 1)), g), 0);
    }

    @Test
    public void twoQualifyingDisconnectedBodiesOnTheSameColumnAbstain() {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        // Two complete components both reach column 147 but remain 8-disconnected.
        for (int y = 121; y < 149; y++) for (int x = 120; x < 147; x++) g[y * W + x] = 0;
        g[122 * W + 147] = 0;
        for (int y = 127; y < 155; y++) for (int x = 149; x < 160; x++) g[y * W + x] = 0;
        g[150 * W + 148] = 0;
        g[150 * W + 147] = 0;
        var wideBody = new PlayingTechniqueDetector.Word("f", .4f, .5f, 160f / 300f, 160f / 240f);
        var widerStaff = new PlayingTechniqueDetector.Staff(70, 110, 14, 0, 1);
        var original = g.clone();
        assertEquals(
                .4f,
                PrintedDynamicInkColumn.anchor(
                        wideBody, widerStaff, BARS, List.of(note(.49f, 0, 1)), g, W, H, .4f, 0),
                0);
        assertArrayEquals(original, g);
    }

    @Test
    public void oneCompleteOwnerWithUnqualifiedSpeckStillResolves() {
        var g = raw();
        g[140 * W + 145] = 0;
        assertEquals(.49f, anchor(WORD, List.of(note(.49f, 0, 1)), g), 0);
    }
}
