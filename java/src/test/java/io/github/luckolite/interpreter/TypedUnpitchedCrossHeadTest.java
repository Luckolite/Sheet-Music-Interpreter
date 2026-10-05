// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original crosses and oval heads; no score images or learned masks. */
public final class TypedUnpitchedCrossHeadTest {
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

    @Test
    public void partialCrossDoesNotBecomePitchedNote() {
        Page p = new Page();
        p.cross(120, 148, true);
        p.oval(260, 156, false, 11, 7);
        assertEquals(2, p.notes().size());
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, p.notes().get(0).kind());
        assertEquals(ScoreNoteEvent.Kind.PITCHED, p.notes().get(1).kind());
    }

    @Test
    public void completeCrossDoesNotBecomePitchedNote() {
        Page p = new Page();
        p.cross(120, 148, false);
        p.oval(260, 156, false, 11, 7);
        assertEquals(2, p.notes().size());
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, p.notes().get(0).kind());
        assertEquals(ScoreNoteEvent.Kind.PITCHED, p.notes().get(1).kind());
    }

    @Test
    public void crossInStaffSpaceIsUnpitched() {
        Page p = new Page();
        p.cross(120, 156, false);
        p.oval(260, 156, false, 11, 7);
        assertEquals(2, p.notes().size());
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, p.notes().get(0).kind());
        assertEquals(ScoreNoteEvent.Kind.PITCHED, p.notes().get(1).kind());
    }

    @Test
    public void crossesWithoutPitchedNotesKeepFiniteAttacks() {
        Page p = new Page();
        p.cross(120, 148, true);
        p.cross(260, 148, true);
        assertEquals(2, p.notes().size());
        for (var n : p.notes()) {
            assertEquals(ScoreNoteEvent.Kind.UNPITCHED, n.kind());
            assertEquals(1f, n.unbeamedDurationBeats(), 0);
            assertFalse(n.tiedFromPrevious());
            assertEquals(0, n.octaveShift());
        }
    }

    @Test
    public void filledOvalIsRetained() {
        Page p = new Page();
        p.oval(120, 148, false, 11, 7);
        assertEquals(1, p.notes().size());
    }

    @Test
    public void hollowOvalIsRetained() {
        Page p = new Page();
        p.oval(120, 148, true, 11, 7);
        assertEquals(1, p.notes().size());
    }

    @Test
    public void smallGraceHeadIsRetained() {
        Page p = new Page();
        p.oval(120, 156, false, 7, 5);
        assertEquals(1, p.notes().size());
    }

    @Test
    public void unaffectedPitchAndPositionAreRetained() {
        Page a = new Page();
        a.oval(260, 156, false, 11, 7);
        Page b = new Page();
        b.cross(120, 148, true);
        b.oval(260, 156, false, 11, 7);
        var x = a.notes().get(0);
        var y = b.notes().get(1);
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

    @Test
    public void detachedRawCrossHasNoNotationOwner() {
        Page p = new Page();
        p.cross(120, 148, false);
        for (int y = 94; y < 142; y++)
            for (int x = 130; x <= 133; x++) {
                p.a[y * p.w + x] = 0;
                p.g[y * p.w + x] = (byte) 255;
            }
        for (int y = 100; y <= 164; y += 16) p.rect(20, y, 380, 1, 4);
        assertTrue(p.notes().isEmpty());
    }

    @Test
    public void partialAndWholeMasksHaveSameWrittenCrossCentre() {
        Page a = new Page();
        a.cross(120, 148, false);
        Page b = new Page();
        b.cross(120, 148, true);
        assertEquals(a.notes(), b.notes());
    }

    @Test
    public void twoCornersOfOneRawCrossProduceOneAttack() {
        Page p = new Page();
        p.cross(120, 148, true);
        for (int yy = 141; yy <= 147; yy++)
            for (int xx = 124; xx <= 132; xx++) p.a[yy * p.w + xx] = 2;
        assertEquals(1, p.notes().size());
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, p.notes().get(0).kind());
    }

    @Test
    public void beamEvidenceSurvivesCrossClassification() {
        Page p = new Page();
        p.cross(120, 148, true);
        p.cross(260, 148, true);
        p.rect(131, 90, 142, 6, 1);
        var notes = p.notes();
        assertEquals(2, notes.size());
        for (var n : notes) {
            assertEquals(ScoreNoteEvent.Kind.UNPITCHED, n.kind());
            assertEquals(1, n.beamCount());
            assertEquals(0f, n.unbeamedDurationBeats(), 0);
        }
    }

    @Test
    public void retainedCrossRejectsDiatonicPitchQuery() {
        Page p = new Page();
        p.cross(120, 148, false);
        var n = p.notes().get(0);
        assertThrows(IllegalStateException.class, n::diatonicPitchIdentity);
        assertEquals(ScoreNoteEvent.ACCIDENTAL_FROM_KEY, n.writtenAccidental());
    }

    @Test
    public void plusTopologyDoesNotCreateUnpitchedAttack() {
        Page p = new Page();
        p.rect(108, 146, 25, 3, 2);
        p.rect(119, 140, 3, 17, 2);
        p.rect(131, 94, 2, 49, 1);
        for (var n : p.notes()) assertEquals(ScoreNoteEvent.Kind.PITCHED, n.kind());
    }

    @Test
    public void crossDotsRetainPrintedRhythm() {
        Page p = new Page();
        p.cross(120, 156, false);
        p.rect(145, 155, 3, 3, 5);
        var n = p.notes().get(0);
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, n.kind());
        assertEquals(1, n.augmentationDots());
        assertEquals(-1, n.stemDirection());
    }
}
