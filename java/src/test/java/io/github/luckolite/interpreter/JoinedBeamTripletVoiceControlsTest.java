// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Entirely generated opposed shafts, beams, dyads and repeated numeral outlines. */
public final class JoinedBeamTripletVoiceControlsTest {
    static final int W = 400, H = 460;
    static final List<MeasureRegion> M = List.of(new MeasureRegion(0, 1, .1f, .8f));
    static final String[] THREE = {
        "..#######...",
        ".##########.",
        "###......###",
        "####.....###",
        "####.....###",
        "####.....###",
        ".##.....####",
        ".......####.",
        "......####..",
        "....#####...",
        "....#####...",
        "....#####...",
        "......####..",
        ".......####.",
        "##.....####.",
        "###....####.",
        "###....####.",
        "###....####.",
        ".###....###.",
        "..########..",
        "..########..",
        "....####...."
    };

    static void rect(byte[] g, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) g[y * W + x] = 0;
    }

    static void white(byte[] g, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) g[y * W + x] = (byte) 255;
    }

    static void three(byte[] g, int x, int y) {
        for (int j = 0; j < 22; j++)
            for (int i = 0; i < 12; i++) if (THREE[j].charAt(i) == '#') g[(y + j) * W + x + i] = 0;
    }

    static byte[] image(boolean foreign) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        three(g, 94, 235);
        three(g, 274, 219);
        for (int x : new int[] {60, 100, 140}) rect(g, x - 7, 159, x - 5, 213);
        rect(g, 53, 209, 135, 215);
        for (int x : new int[] {240, 280, 320}) rect(g, x - 7, 159, x - 5, 223);
        rect(g, 233, 222, 315, 228);
        if (foreign) {
            three(g, 254, 258);
            for (int x : new int[] {240, 280, 320}) rect(g, x + 5, 275, x + 7, 332);
            rect(g, 245, 273, 327, 279);
        }
        return g;
    }

    static ScoreNoteEvent note(int x, int y, int staff, int dir) {
        return new ScoreNoteEvent(
                        0, x / (float) W, 0, staff, 2, y / (float) H, false, 0, 1, 2, 0, 1)
                .withStemDirection(dir);
    }

    static List<ScoreNoteEvent> notes(boolean foreign) {
        var n = new ArrayList<ScoreNoteEvent>();
        for (int x : new int[] {60, 100, 140, 240, 280, 320}) {
            n.add(note(x, 160, 0, -1));
            n.add(note(x, 180, 0, -1));
        }
        if (foreign) for (int x : new int[] {240, 280, 320}) n.add(note(x, 330, 1, 1));
        return n;
    }

    static List<ScoreNoteEvent> apply(byte[] g, List<ScoreNoteEvent> n) {
        return TripletRhythmDetector.apply(n, M, g, W, H);
    }

    static void targetRatio(List<ScoreNoteEvent> n, int divisor) {
        for (int i = 6; i < 12; i++) assertEquals("head " + i, divisor, n.get(i).tupletDivisor());
    }

    @Test
    public void threeDyadsOwnTheirJoinedNumeral() {
        targetRatio(apply(image(false), notes(false)), 3);
    }

    @Test
    public void adjacentOpposedTripletsKeepSeparateNumerals() {
        var r = apply(image(true), notes(true));
        targetRatio(r, 3);
        for (int i = 12; i < 15; i++) assertEquals(3, r.get(i).tupletDivisor());
    }

    @Test
    public void localVoiceCannotBorrowOnlyForeignJoinedNumeral() {
        byte[] g = image(true);
        white(g, 274, 219, 285, 240);
        rect(g, 233, 222, 315, 228);
        targetRatio(apply(g, notes(true)), 1);
    }

    @Test
    public void missingMiddleShaftDoesNotCompleteThreeAttackProof() {
        byte[] g = image(false);
        white(g, 273, 185, 275, 218);
        targetRatio(apply(g, notes(false)), 1);
    }

    @Test
    public void interruptedBeamDoesNotCompleteOwnership() {
        byte[] g = image(false);
        white(g, 256, 222, 260, 228);
        targetRatio(apply(g, notes(false)), 1);
    }

    @Test
    public void missingBowlCannotBorrowReference() {
        byte[] g = image(false);
        white(g, 274, 230, 285, 240);
        targetRatio(apply(g, notes(false)), 1);
    }

    @Test
    public void heavyBeamOcclusionRemainsUncertain() {
        byte[] g = image(false);
        rect(g, 233, 222, 315, 237);
        targetRatio(apply(g, notes(false)), 1);
    }

    @Test
    public void closedEightDoesNotMatchReference() {
        byte[] g = image(false);
        rect(g, 274, 221, 276, 238);
        targetRatio(apply(g, notes(false)), 1);
    }

    @Test
    public void ordinaryUnprintedThreeAttacksRemainUnchanged() {
        byte[] g = image(false);
        white(g, 274, 219, 285, 240);
        rect(g, 233, 222, 315, 228);
        targetRatio(apply(g, notes(false)), 1);
    }

    @Test
    public void pixelsAndInputEventListArePreserved() {
        byte[] g = image(true), saved = g.clone();
        var n = notes(true);
        var copy = List.copyOf(n);
        apply(g, n);
        assertArrayEquals(saved, g);
        assertEquals(copy, n);
    }

    @Test
    public void anInterveningDifferentValueAttackBreaksTheTuplet() {
        var n = notes(false);
        n.add(
                new ScoreNoteEvent(0, 260f / W, 0, 0, 2, 160f / H, false, 0, 0, 2, 1, 1)
                        .withStemDirection(-1));
        targetRatio(apply(image(false), n), 1);
    }

    @Test
    public void wrongMiddleStemDirectionBreaksThreeVoiceProof() {
        var n = notes(false);
        n.set(8, n.get(8).withStemDirection(1));
        n.set(9, n.get(9).withStemDirection(1));
        targetRatio(apply(image(false), n), 1);
    }

    @Test
    public void aCrossStaffMiddleAttackBreaksOrdinaryOwnership() {
        var n = notes(false);
        n.set(8, n.get(8).withCrossStaffBeam());
        n.set(9, n.get(9).withCrossStaffBeam());
        targetRatio(apply(image(false), n), 1);
    }

    @Test
    public void aGraceMiddleAttackCannotCompleteTheTuplet() {
        var n = notes(false);
        n.set(8, n.get(8).withArticulations(NoteOrnament.GRACE));
        n.set(9, n.get(9).withArticulations(NoteOrnament.GRACE));
        targetRatio(apply(image(false), n), 1);
    }

    @Test
    public void aForeignNumberWithoutForeignShaftsDoesNotConfuseLocalOwnedTuplet() {
        byte[] g = image(true);
        white(g, 245, 280, 247, 332);
        white(g, 285, 280, 287, 332);
        white(g, 325, 280, 327, 332);
        targetRatio(apply(g, notes(true)), 3);
    }
}
