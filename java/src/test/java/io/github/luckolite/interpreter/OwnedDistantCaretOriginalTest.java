// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original finite beam/head rasters with a faded detached caret at a distant attack. */
public final class OwnedDistantCaretOriginalTest {
    static final int W = 2048, H = 1600;
    static final float GAP = 14;

    static final class Scene {
        final byte[] raw = new byte[W * H], labels = new byte[W * H];
        final List<MeasureRegion> measures = List.of(new MeasureRegion(0, 1, 0, 1));
        final List<PlayingTechniqueDetector.Staff> staffs =
                List.of(new PlayingTechniqueDetector.Staff(294.5f, 350.5f, GAP, 0, 1));
        final List<ScoreNoteEvent> notes = new ArrayList<>();

        Scene() {
            Arrays.fill(raw, (byte) 255);
            for (int y : new int[] {294, 308, 322, 336, 350})
                for (int x = 140; x <= 290; x++)
                    for (int dy = 0; dy < 2; dy++) pixel(x, y + dy, 80);
            for (int cx : new int[] {200, 250}) {
                for (int y = 344; y <= 356; y++)
                    for (int x = cx - 10; x <= cx + 10; x++)
                        if (Math.pow((x - cx) / 8.5, 2) + Math.pow((y - 350) / 5.5, 2) <= 1)
                            pixel(x, y, 50);
                for (int y = 281; y <= 350; y++)
                    for (int x = cx + 8; x <= cx + 9; x++) pixel(x, y, 160);
                notes.add(note(cx, 350, 0));
            }
            for (int y = 281; y <= 288; y++) for (int x = 208; x <= 259; x++) pixel(x, y, 60);
            caret(200, 249, 195);
        }

        void pixel(int x, int y, int tone) {
            raw[y * W + x] = (byte) tone;
        }

        void caret(int cx, int top, int leftTone) {
            for (int x = cx - 12; x <= cx + 12; x++) {
                int y = top + Math.abs(x - cx);
                pixel(x, y, x < cx ? leftTone : 75);
                pixel(x, y + 1, x < cx ? leftTone : 75);
            }
        }

        static ScoreNoteEvent note(int x, int y, int marks) {
            return new ScoreNoteEvent(
                            0,
                            x / (float) W,
                            Math.round((350.5f - y) * 2 / GAP),
                            0,
                            1,
                            y / (float) H,
                            false,
                            0,
                            1)
                    .withArticulations(marks);
        }

        ScorePageInterpretation score() {
            return new ScorePageInterpretation(measures, List.copyOf(notes));
        }

        List<NoteArticulationDetector.Anchor> anchors() {
            return notes.stream()
                    .map(
                            n ->
                                    new NoteArticulationDetector.Anchor(
                                            n.positionInMeasure() * W, n.pageY() * H, GAP, 0))
                    .toList();
        }

        int[] rawMarks() {
            return NoteArticulationDetector.detect(labels, raw, W, H, anchors());
        }

        PrintedBeamSource.Capture source() {
            return PrintedBeamSource.capture(
                    score(), labels, raw, W, H, staffs, 0, 8_000_000, 256_000_000);
        }

        ScorePageInterpretation apply() {
            return PrintedBeamSource.apply(score(), labels, raw, W, H, staffs, 0);
        }
    }

    @Test
    public void originalSourceHasARealFinitePrimaryButRawCaretHasNoOwner() {
        Scene s = new Scene();
        var c = s.source();
        assertEquals("", c.statistics().refusal());
        assertEquals(1, c.source().groups().size());
        assertEquals(List.of(1), c.source().groups().get(0).bridges());
        assertArrayEquals(new int[2], s.rawMarks());
    }

    @Test
    public void distantFadedCaretNeedsTheActualOwnedHeadAndTerminalBeam() {
        Scene s = new Scene();
        assertEquals(NoteArticulation.MARCATO, s.apply().notes().get(0).articulations());
        assertEquals(0, s.apply().notes().get(1).articulations());
    }

    @Test
    public void originalDarkCaretAlsoUsesTheSameOwnedDistantAttack() {
        Scene s = new Scene();
        s.caret(200, 249, 75);
        assertArrayEquals(new int[2], s.rawMarks());
        assertEquals(1, s.source().source().groups().size());
        assertEquals(NoteArticulation.MARCATO, s.apply().notes().get(0).articulations());
    }

    @Test
    public void disconnectedHeadCannotBorrowTerminalBeamOwnership() {
        Scene s = new Scene();
        for (int y = 335; y <= 343; y++) for (int x = 207; x <= 210; x++) s.pixel(x, y, 255);
        assertEquals(0, s.apply().notes().get(0).articulations());
    }

    @Test
    public void aLongShaftCrossingABeamDoesNotProveItsTerminal() {
        Scene s = new Scene();
        for (int y = 265; y <= 280; y++) for (int x = 208; x <= 209; x++) s.pixel(x, y, 160);
        assertEquals(0, s.apply().notes().get(0).articulations());
    }

    @Test
    public void missingPrimaryCannotBeReplacedByCarrierInk() {
        Scene s = new Scene();
        for (int y = 281; y <= 288; y++) for (int x = 210; x <= 257; x++) s.pixel(x, y, 255);
        assertEquals(0, s.apply().notes().get(0).articulations());
    }

    @Test
    public void wrongHeadCannotReceiveAProofFromItsNeighbour() {
        Scene s = new Scene();
        s.notes.set(0, Scene.note(187, 350, 0));
        assertEquals(0, s.apply().notes().get(0).articulations());
    }

    @Test
    public void anExistingMarkedAttackRetainsEveryOldBit() {
        Scene s = new Scene();
        s.notes.set(0, Scene.note(200, 350, NoteArticulation.STACCATO));
        assertEquals(NoteArticulation.STACCATO, s.apply().notes().get(0).articulations());
    }

    @Test
    public void rawAndSemanticPlanesStayImmutable() {
        Scene s = new Scene();
        byte[] a = s.raw.clone(), b = s.labels.clone();
        s.apply();
        assertArrayEquals(a, s.raw);
        assertArrayEquals(b, s.labels);
    }

    public static void main(String[] args) {
        Scene s = new Scene();
        System.out.println(
                "source=" + s.source().statistics() + " groups=" + s.source().source().groups());
        System.out.println(
                "rawmarks=" + Arrays.toString(s.rawMarks()) + " applied=" + s.apply().notes());
    }
}
