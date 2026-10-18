// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original feature-level geometry oracles; these do not assert quarter-rest acquisition. */
public final class FiniteBeamQuarterOwnershipTest {
    private static final int W = 480, H = 260;
    private static final float G = 16, BASE = 70;
    private static final MeasureRegion REGION = new MeasureRegion(.04f, .96f, .05f, .95f);

    private static final class Page {
        final byte[] gray = new byte[W * H];
        final List<ScoreNoteEvent> notes = new ArrayList<>();

        Page(boolean peerShaft, boolean beam) {
            Arrays.fill(gray, (byte) 240);
            for (int k = 0; k < 5; k++)
                for (int y = Math.round(BASE + k * G) - 1; y <= BASE + k * G + 1; y++)
                    for (int x = 20; x < W - 20; x++) ink(x, y);
            ellipse(210, 78);
            ellipse(258, 94);
            for (int y = 78; y <= 134; y++) for (int x = 199; x <= 202; x++) ink(x, y);
            if (peerShaft)
                for (int y = 94; y <= 142; y++) for (int x = 247; x <= 250; x++) ink(x, y);
            if (beam)
                for (int x = 199; x <= 250; x++) {
                    double end = 134 + 8 * (x - 199) / 51.;
                    for (int y = (int) Math.ceil(end - 7); y <= Math.floor(end); y++) ink(x, y);
                }
            notes.add(note(210, 78));
            notes.add(note(258, 94));
        }

        void ink(int x, int y) {
            gray[y * W + x] = 0;
        }

        void ellipse(int cx, int cy) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - 11; x <= cx + 11; x++)
                    if ((x - cx) * (x - cx) / 121. + (y - cy) * (y - cy) / 64. <= 1) ink(x, y);
        }

        void stroke(int ax, int ay, int bx, int by) {
            int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
            for (int i = 0; i <= steps; i++) {
                int x = Math.round(ax + (bx - ax) * i / (float) Math.max(1, steps));
                int y = Math.round(ay + (by - ay) * i / (float) Math.max(1, steps));
                ink(x, y);
                ink(x + 1, y);
            }
        }

        void quarterBranch() {
            stroke(208, 94, 215, 103);
            stroke(215, 103, 208, 110);
            stroke(208, 110, 214, 117);
            stroke(214, 117, 208, 124);
            stroke(208, 124, 215, 129);
        }
    }

    private static ScoreNoteEvent note(int x, int y) {
        return new ScoreNoteEvent(
                        0,
                        (x / (float) W - .04f) / .92f,
                        Math.round((134 - y) / 8f),
                        0,
                        1,
                        y / (float) H,
                        false,
                        0,
                        1,
                        2,
                        0)
                .withStemDirection(0);
    }

    private static boolean owns(Page p, int bottom, float center) {
        return FiniteBeamQuarterOwnership.owns(
                p.gray, W, H, REGION, p.notes, 0, 0, 1, center, G, 197, 218, 90, bottom);
    }

    @Test
    public void completeTwoHeadFiniteBeamOwnsItsCroppedCorner() {
        assertTrue(owns(new Page(true, true), 137, 102f / H));
    }

    @Test
    public void aVisibleQuarterBranchTouchingTheFigureIsNotOwned() {
        var p = new Page(true, true);
        p.quarterBranch();
        assertFalse(owns(p, 137, 102f / H));
    }

    @Test
    public void aCrossingRestWithAVisibleLowerFootIsNotOwned() {
        var p = new Page(true, true);
        p.quarterBranch();
        p.stroke(215, 129, 210, 144);
        assertFalse(owns(p, 145, 102f / H));
    }

    @Test
    public void aSpurBeyondTheFiniteRailRemainsResidual() {
        var p = new Page(true, true);
        p.stroke(212, 137, 212, 143);
        assertFalse(owns(p, 144, 102f / H));
    }

    @Test
    public void aMissingOwnedPeerCannotBeReplacedByBeamMetadata() {
        var p = new Page(true, true);
        p.notes.remove(1);
        assertFalse(owns(p, 137, 102f / H));
    }

    @Test
    public void aMissingPeerShaftOrRuleOnlyBridgeDoesNotProveABeam() {
        assertFalse(owns(new Page(false, true), 137, 102f / H));
        assertFalse(owns(new Page(true, false), 137, 102f / H));
    }

    @Test
    public void shiftedHypothesisCannotReplaceTheFivePhysicalRules() {
        assertFalse(owns(new Page(true, true), 137, 118f / H));
    }

    @Test
    public void ownedProofDoesNotMutatePixelsOrTypedOwners() {
        var p = new Page(true, true);
        byte[] before = p.gray.clone();
        var notes = List.copyOf(p.notes);
        assertTrue(owns(p, 137, 102f / H));
        assertArrayEquals(before, p.gray);
        assertEquals(notes, p.notes);
    }
}
