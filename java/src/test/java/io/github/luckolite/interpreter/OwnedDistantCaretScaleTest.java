// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Double the complete original drawing inside a fixed safe canvas, preserving every ink pixel. */
public final class OwnedDistantCaretScaleTest {
    private static final int W = 2048, H = 2048;

    private static void check(boolean faded) {
        var base = new OwnedDistantCaretOriginalTest.Scene();
        if (!faded) base.caret(200, 249, 75);
        // The remaining base canvas is pure background. Its omission cannot crop
        // a source component, model label, musical mark, or possible raw rival.
        for (int y = 0; y < OwnedDistantCaretOriginalTest.H; y++)
            for (int x = 0; x < OwnedDistantCaretOriginalTest.W; x++)
                if (x >= W / 2 || y >= H / 2) {
                    assertEquals(255, base.raw[y * OwnedDistantCaretOriginalTest.W + x] & 255);
                    assertEquals(0, base.labels[y * OwnedDistantCaretOriginalTest.W + x]);
                }
        byte[] raw = new byte[W * H], labels = new byte[raw.length];
        for (int y = 0; y < H / 2; y++)
            for (int x = 0; x < W / 2; x++)
                for (int dy = 0; dy < 2; dy++)
                    for (int dx = 0; dx < 2; dx++) {
                        int source = y * OwnedDistantCaretOriginalTest.W + x,
                                target = (2 * y + dy) * W + 2 * x + dx;
                        raw[target] = base.raw[source];
                        labels[target] = base.labels[source];
                    }
        List<ScoreNoteEvent> notes = new ArrayList<>();
        for (var n : base.notes)
            notes.add(
                    new ScoreNoteEvent(
                            n.measureIndex(),
                            n.positionInMeasure() * OwnedDistantCaretOriginalTest.W * 2 / W,
                            n.staffStep(),
                            n.staffIndex(),
                            n.staffCount(),
                            n.pageY() * OwnedDistantCaretOriginalTest.H * 2 / H,
                            false,
                            0,
                            n.beamCount()));
        var score = new ScorePageInterpretation(base.measures, notes);
        var staffs =
                base.staffs.stream()
                        .map(
                                s ->
                                        new PlayingTechniqueDetector.Staff(
                                                s.top() * 2,
                                                s.bottom() * 2,
                                                s.gap() * 2,
                                                s.index(),
                                                s.count()))
                        .toList();
        var anchors =
                notes.stream()
                        .map(
                                n ->
                                        new NoteArticulationDetector.Anchor(
                                                n.positionInMeasure() * W, n.pageY() * H, 28, 0))
                        .toList();
        var source =
                PrintedBeamSource.capture(
                        score, labels, raw, W, H, staffs, 0, 8_000_000, 256_000_000);
        assertEquals("", source.statistics().refusal());
        assertEquals(2, source.statistics().shaftOwners());
        assertEquals(1, source.source().groups().size());
        assertEquals(List.of(1), source.source().groups().get(0).bridges());
        assertArrayEquals(new int[2], NoteArticulationDetector.detect(labels, raw, W, H, anchors));
        byte[] beforeRaw = raw.clone(), beforeLabels = labels.clone();
        var after = PrintedBeamSource.apply(score, labels, raw, W, H, staffs, 0);
        assertEquals(
                faded ? "faded" : "dark",
                NoteArticulation.MARCATO,
                after.notes().get(0).articulations());
        assertEquals(0, after.notes().get(1).articulations());
        assertEquals(source.source(), after.beamSource());
        for (int i = 0; i < notes.size(); i++)
            assertEquals(
                    notes.get(i),
                    after.notes().get(i).withArticulations(notes.get(i).articulations()));
        assertArrayEquals(beforeRaw, raw);
        assertArrayEquals(beforeLabels, labels);
    }

    @Test
    public void darkCaretUsesTheSameOwnedGeometryAtDoubleScale() {
        check(false);
    }

    @Test
    public void fadedCaretUsesTheSameOwnedGeometryAtDoubleScale() {
        check(true);
    }
}
