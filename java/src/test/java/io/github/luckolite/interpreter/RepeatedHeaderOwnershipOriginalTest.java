// Copyright 2026 Luckolite

// SPDX-License-Identifier: Apache-2.0

package io.github.luckolite.interpreter;

import java.util.*;

import org.junit.Test;

import static org.junit.Assert.*;

/** Original repeated compact signatures; no score assets. */
public class RepeatedHeaderOwnershipOriginalTest {

    static final int W = 420, H = 600;

    static class Page {

        final byte[] labels = new byte[W * H], gray = new byte[W * H];

        Page(boolean secondClef, boolean local) {
            this(secondClef, local, false, false);
        }

        Page(boolean secondClef, boolean local, boolean carry, boolean firstLocal) {

            Arrays.fill(gray, (byte) 255);

            var first = new HeaderAccidentalOwnershipTest.Page(true, 180);

            var second = new HeaderAccidentalOwnershipTest.Page(secondClef, firstLocal ? 154 : 130);

            if (firstLocal) second.flat(120, 100);

            if (carry) {

                for (int y = 126; y <= 180; y++)
                    for (int x = 272; x <= 288; x++) {
                        second.labels[y * W + x] = 0;
                        second.gray[y * W + x] = (byte) 255;
                    }

                for (int y = 132; y <= 164; y += 16)
                    for (int x = 272; x <= 288; x++) second.ink(x, y, 4);

                second.note(280, 100);
            }

            if (local) second.flat(250, 132);

            System.arraycopy(first.labels, 0, labels, 0, first.labels.length);

            System.arraycopy(first.gray, 0, gray, 0, first.gray.length);

            System.arraycopy(second.labels, 0, labels, 280 * W, second.labels.length);

            System.arraycopy(second.gray, 0, gray, 280 * W, second.gray.length);
        }

        OmrScoreInterpreter.Analysis read() {
            return OmrScoreInterpreter.analyze(
                    labels,
                    gray,
                    W,
                    H,
                    List.of(
                            new MeasureRegion(120f / W, 1, .1f, .43f),
                            new MeasureRegion(120f / W, 1, .56f, .9f)));
        }
    }

    @Test
    public void crowdedRepeatedKeyDoesNotFlattenFirstF() {

        var p = new Page(true, false);
        var a = p.read();

        assertEquals(List.of(new ScoreKeyChange(0, -2)), a.keyChanges());

        assertEquals(4, a.notes().size());

        assertEquals(8, a.notes().get(2).staffStep());

        assertEquals(ScoreNoteEvent.ACCIDENTAL_FROM_KEY, a.notes().get(2).writtenAccidental());
    }

    @Test
    public void separateLocalFlatAfterRepeatedHeaderRemainsExplicit() {

        var a = new Page(true, true).read();
        assertEquals(ScoreNoteEvent.ACCIDENTAL_FLAT, a.notes().get(3).writtenAccidental());
    }

    @Test
    public void repeatedKeyWithoutClefDoesNotAcquireHeaderOwnership() {

        var a = new Page(false, false).read();
        assertEquals(ScoreNoteEvent.ACCIDENTAL_FLAT, a.notes().get(2).writtenAccidental());
    }

    @Test
    public void repeatedKeyDoesNotStartSpuriousFlatCarry() {

        var a = new Page(true, false, true, false).read();
        assertEquals(4, a.notes().size());

        assertEquals(8, a.notes().get(2).staffStep());
        assertEquals(8, a.notes().get(3).staffStep());

        assertEquals(ScoreNoteEvent.ACCIDENTAL_FROM_KEY, a.notes().get(2).writtenAccidental());

        assertEquals(ScoreNoteEvent.ACCIDENTAL_FROM_KEY, a.notes().get(3).writtenAccidental());
    }

    @Test
    public void genuineFirstNoteFlatAfterRepeatedKeyRemainsExplicit() {

        var a = new Page(true, false, false, true).read();

        assertEquals(ScoreNoteEvent.ACCIDENTAL_FLAT, a.notes().get(2).writtenAccidental());
    }

    @Test
    public void inputPlanesRemainImmutable() {
        var p = new Page(true, false);
        var l = p.labels.clone();
        var g = p.gray.clone();
        p.read();
        assertArrayEquals(l, p.labels);
        assertArrayEquals(g, p.gray);
    }
}
