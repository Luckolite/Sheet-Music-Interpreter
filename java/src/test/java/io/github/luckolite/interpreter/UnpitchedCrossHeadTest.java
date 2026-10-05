// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original crosses and oval heads; no score images or learned masks. */
public final class UnpitchedCrossHeadTest {
    static class Page {
        int w = 420, h = 250;
        byte[] a = new byte[w * h], g = new byte[w * h];

        Page() {
            Arrays.fill(g, (byte) 255);
            for (int y = 100; y <= 164; y += 16) rect(20, y, 380, 1, 4);
        }

        void rect(int x, int y, int ww, int hh, int label) {
            for (int yy = y; yy < y + hh; yy++)
                for (int xx = x; xx < x + ww; xx++) {
                    a[yy * w + xx] = (byte) label;
                    g[yy * w + xx] = 0;
                }
        }

        void cross(int x, int y, boolean fragment) {
            rect(x + 11, y - 54, 2, 49, 1);
            for (int dx = -12; dx <= 12; dx++)
                for (int dy = -8; dy <= 8; dy++)
                    if (Math.abs(dy - dx * .5f) < 1.7f || Math.abs(dy + dx * .5f) < 1.7f)
                        rect(x + dx, y + dy, 1, 1, fragment ? 5 : 2);
            // Deliberately coarse semantic corner; raw source remains two thin diagonals.
            if (fragment)
                for (int yy = y + 1; yy <= y + 7; yy++)
                    for (int xx = x - 12; xx <= x - 4; xx++) a[yy * w + xx] = 2;
        }

        void oval(int x, int y, boolean hollow, int rx, int ry) {
            rect(x + rx - 1, y - 50, 2, 51, 1);
            for (int yy = y - ry; yy <= y + ry; yy++)
                for (int xx = x - rx; xx <= x + rx; xx++) {
                    float d =
                            (xx - x) * (xx - x) / (float) (rx * rx)
                                    + (yy - y) * (yy - y) / (float) (ry * ry);
                    if (d <= 1 && (!hollow || d >= .45f)) rect(xx, yy, 1, 1, 2);
                }
        }

        List<ScoreNoteEvent> notes() {
            return OmrScoreInterpreter.extract(
                    a, g, w, h, List.of(new MeasureRegion(.04f, .96f, .3f, .85f)));
        }
    }

    private static void assertUnpitchedSource(ScoreNoteEvent note, int x, int y, Page page) {
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, note.kind());
        assertEquals(0, note.measureIndex());
        assertEquals(0, note.staffIndex());
        assertEquals(1, note.staffCount());
        assertEquals((164 - y) / 8, note.staffStep());
        assertEquals((x / (float) page.w - .04f) / (.96f - .04f), note.positionInMeasure(), 0);
        assertEquals(y / (float) page.h, note.pageY(), 0);
        assertEquals(-1, note.stemDirection());
        assertEquals(1f, note.unbeamedDurationBeats(), 0);
        assertFalse(note.tiedFromPrevious());
        assertEquals(0, note.boundaryTies());
        assertEquals(0, note.octaveShift());
        assertThrows(IllegalStateException.class, note::diatonicPitchIdentity);
    }

    private static void assertMixedSourceAttacks(Page page, int crossY) {
        var notes = page.notes();
        assertEquals(2, notes.size());
        assertUnpitchedSource(notes.get(0), 120, crossY, page);
        assertEquals(ScoreNoteEvent.Kind.PITCHED, notes.get(1).kind());
        Page pitchedOnly = new Page();
        pitchedOnly.oval(260, 156, false, 11, 7);
        assertEquals(1, pitchedOnly.notes().size());
        assertEquals(pitchedOnly.notes().get(0), notes.get(1));
    }

    @Test
    public void partialCrossDoesNotBecomePitchedNote() {
        Page p = new Page();
        p.cross(120, 148, true);
        p.oval(260, 156, false, 11, 7);
        assertMixedSourceAttacks(p, 148);
    }

    @Test
    public void completeCrossDoesNotBecomePitchedNote() {
        Page p = new Page();
        p.cross(120, 148, false);
        p.oval(260, 156, false, 11, 7);
        assertMixedSourceAttacks(p, 148);
    }

    @Test
    public void crossInStaffSpaceIsUnpitched() {
        Page p = new Page();
        p.cross(120, 156, false);
        p.oval(260, 156, false, 11, 7);
        assertMixedSourceAttacks(p, 156);
    }

    @Test
    public void crossesWithoutPitchedNotesKeepIndependentUnpitchedAttacks() {
        Page p = new Page();
        p.cross(120, 148, true);
        p.cross(260, 148, true);
        var notes = p.notes();
        assertEquals(2, notes.size());
        assertUnpitchedSource(notes.get(0), 120, 148, p);
        assertUnpitchedSource(notes.get(1), 260, 148, p);
        assertTrue(notes.get(0).positionInMeasure() < notes.get(1).positionInMeasure());
    }

    @Test
    public void filledOvalIsRetained() {
        Page p = new Page();
        p.oval(120, 148, false, 11, 7);
        assertEquals(1, p.notes().size());
        assertEquals(ScoreNoteEvent.Kind.PITCHED, p.notes().get(0).kind());
    }

    @Test
    public void hollowOvalIsRetained() {
        Page p = new Page();
        p.oval(120, 148, true, 11, 7);
        assertEquals(1, p.notes().size());
        assertEquals(ScoreNoteEvent.Kind.PITCHED, p.notes().get(0).kind());
    }

    @Test
    public void smallGraceHeadIsRetained() {
        Page p = new Page();
        p.oval(120, 156, false, 7, 5);
        assertEquals(1, p.notes().size());
        assertEquals(ScoreNoteEvent.Kind.PITCHED, p.notes().get(0).kind());
    }

    @Test
    public void unaffectedPitchAndPositionAreRetained() {
        Page a = new Page();
        a.oval(260, 156, false, 11, 7);
        Page b = new Page();
        b.cross(120, 148, true);
        b.oval(260, 156, false, 11, 7);
        var x = a.notes().get(0);
        assertEquals(1, a.notes().size());
        assertEquals(2, b.notes().size());
        assertUnpitchedSource(b.notes().get(0), 120, 148, b);
        var y = b.notes().get(1);
        assertEquals(ScoreNoteEvent.Kind.PITCHED, x.kind());
        assertEquals(x, y);
        assertEquals(x.staffStep(), y.staffStep());
        assertEquals(x.positionInMeasure(), y.positionInMeasure(), 0);
    }

    @Test
    public void rawInkAndLabelsAreNotModified() {
        Page p = new Page();
        p.cross(120, 148, true);
        byte[] a = p.a.clone(), g = p.g.clone();
        p.notes();
        assertArrayEquals(a, p.a);
        assertArrayEquals(g, p.g);
    }
}
