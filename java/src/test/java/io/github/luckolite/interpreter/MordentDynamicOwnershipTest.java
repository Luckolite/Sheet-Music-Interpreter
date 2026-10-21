// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MordentDynamicOwnershipTest {
    static final int W = 240, H = 200;

    byte[] wave() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int x = 0; x < 40; x++) {
            double cy = 68.5 - 3.5 * Math.sin(4 * Math.PI * x / 39);
            for (int y = 60; y < 78; y++) if (Math.abs(y - cy) <= 2) g[y * W + 100 + x] = 60;
        }
        return g;
    }

    List<MeasureRegion> bars = List.of(new MeasureRegion(0, 1, 0, 1));
    List<PlayingTechniqueDetector.Staff> staff =
            List.of(new PlayingTechniqueDetector.Staff(100, 140, 10, 0, 1));

    ScoreNoteEvent note(float x, int marks) {
        return new ScoreNoteEvent(0, x / W, 2, 0, 1, .6f, false, 0, 1, 2, 0, 1, 0, marks);
    }

    PlayingTechniqueDetector.Word word(String text, int top) {
        return new PlayingTechniqueDetector.Word(
                text, 100f / W, top / (float) H, 140f / W, (top + 18f) / H);
    }

    boolean owns(String text, int top, byte[] g, ScoreNoteEvent n) {
        return PrintedDynamicNoteOwnership.containsOwnedMordent(
                word(text, top), staff, bars, List.of(n), g, W, H);
    }

    @Test
    public void observedMordentAndSameOwnedHeadCannotAlsoBeM() {
        assertTrue(owns("M", 60, wave(), note(120, NoteOrnament.MORDENT)));
    }

    @Test
    public void unrelatedHeadCannotOwnLetter() {
        assertFalse(owns("m", 60, wave(), note(180, NoteOrnament.MORDENT)));
    }

    @Test
    public void bareMWithoutOrnamentIsPreserved() {
        assertFalse(owns("m", 60, wave(), note(120, 0)));
    }

    @Test
    public void ordinaryLetterMNotWaveIsPreserved() {
        byte[] g = wave();
        Arrays.fill(g, (byte) 255);
        for (int x : new int[] {102, 120, 138}) for (int y = 62; y < 76; y++) g[y * W + x] = 60;
        assertFalse(owns("m", 60, g, note(120, NoteOrnament.MORDENT)));
    }

    @Test
    public void GenuineMfDoesNotBecomeOrnament() {
        assertFalse(owns("mf", 60, wave(), note(120, NoteOrnament.MORDENT)));
    }

    @Test
    public void belowStaffDynamicKeepsItsVerticalIdentity() {
        byte[] g = wave(), b = new byte[g.length];
        Arrays.fill(b, (byte) 255);
        for (int y = 60; y < 78; y++) System.arraycopy(g, y * W, b, (y + 90) * W, W);
        assertFalse(owns("m", 150, b, note(120, NoteOrnament.MORDENT)));
    }

    @Test
    public void queryDoesNotRewriteInk() {
        byte[] g = wave(), b = g.clone();
        owns("m", 60, g, note(120, NoteOrnament.MORDENT));
        assertArrayEquals(b, g);
    }
}
