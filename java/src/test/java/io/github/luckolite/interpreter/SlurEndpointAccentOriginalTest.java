// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlurEndpointAccentOriginalTest {
    static final int W = 320, H = 220;

    static final class Scene {
        byte[] gray = new byte[W * H];
        List<NoteArticulationDetector.Anchor> notes = new ArrayList<>();

        Scene() {
            Arrays.fill(gray, (byte) 255);
            notes.add(new NoteArticulationDetector.Anchor(73, 95, 15, 0));
            notes.add(new NoteArticulationDetector.Anchor(193, 135, 15, 0));
        }

        void line(int ax, int ay, int bx, int by) {
            int n = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
            for (int i = 0; i <= n; i++) {
                int x = Math.round(ax + (bx - ax) * i / (float) n),
                        y = Math.round(ay + (by - ay) * i / (float) n);
                for (int d = -1; d <= 1; d++) gray[(y + d) * W + x] = 0;
            }
        }

        void curve(boolean straight) {
            for (int x = 70; x <= 190; x++) {
                double t = (x - 70) / 120d;
                int y = (int) Math.round(75 + 40 * t - (straight ? 0 : 16 * 4 * t * (1 - t)));
                for (int d = -1; d <= 1; d++) gray[(y + d) * W + x] = 0;
            }
        }

        void accent() {
            line(184, 96, 207, 102);
            line(184, 108, 207, 102);
        }

        int[] component() {
            int seed = 102 * W + 206;
            boolean[] seen = new boolean[gray.length];
            int[] q = new int[gray.length];
            int n = 1;
            q[0] = seed;
            seen[seed] = true;
            for (int at = 0; at < n; at++) {
                int p = q[at], x = p % W, y = p / W;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy, k = yy * W + xx;
                        if (xx >= 0
                                && xx < W
                                && yy >= 0
                                && yy < H
                                && !seen[k]
                                && (gray[k] & 255) < 155) {
                            seen[k] = true;
                            q[n++] = k;
                        }
                    }
            }
            return Arrays.copyOf(q, n);
        }

        List<SlurEndpointAccents.Mark> result() {
            return SlurEndpointAccents.partition(gray, W, H, component(), notes);
        }
    }

    static Scene complete() {
        var s = new Scene();
        s.curve(false);
        s.accent();
        return s;
    }

    @Test
    public void steepCompleteSlurAndCompleteAccentRetainRightAttack() {
        var s = complete();
        byte[] before = s.gray.clone();
        var r = s.result();
        assertEquals(1, r.size());
        assertEquals(1, r.get(0).owner());
        assertArrayEquals(before, s.gray);
    }

    @Test
    public void descendingEndpointPitchDoesNotRequireEqualHeadRows() {
        var s = complete();
        assertNotEquals(s.notes.get(0).y(), s.notes.get(1).y(), 0);
        assertEquals(1, s.result().size());
    }

    @Test
    public void straightBeamCannotSupplySlur() {
        var s = new Scene();
        s.curve(true);
        s.accent();
        assertTrue(s.result().isEmpty());
    }

    @Test
    public void missingLeftGenuineAttackAbstains() {
        var s = complete();
        s.notes.remove(0);
        assertTrue(s.result().isEmpty());
    }

    @Test
    public void competingRightAttackAbstains() {
        var s = complete();
        s.notes.add(new NoteArticulationDetector.Anchor(195, 134, 15, 0));
        assertTrue(s.result().isEmpty());
    }

    @Test
    public void foreignBranchRemainsUnexplained() {
        var s = complete();
        s.line(130, 78, 130, 100);
        assertTrue(s.result().isEmpty());
    }

    @Test
    public void partialChevronCannotBorrowSlurArm() {
        var s = complete();
        for (int y = 93; y <= 100; y++)
            for (int x = 184; x < 196; x++) s.gray[y * W + x] = (byte) 255;
        assertTrue(s.result().isEmpty());
    }

    @Test
    public void completeComponentCannotBeFavorablyCropped() {
        var s = complete();
        int[] p = s.component();
        assertTrue(
                SlurEndpointAccents.partition(s.gray, W, H, Arrays.copyOf(p, p.length - 1), s.notes)
                        .isEmpty());
    }

    @Test
    public void differentStaffEndpointsCannotOwnMark() {
        var s = complete();
        s.notes.set(0, new NoteArticulationDetector.Anchor(73, 95, 15, 1));
        assertTrue(s.result().isEmpty());
    }

    @Test
    public void acceptedHeadTouchingCurveCannotBeDonated() {
        var s = complete();
        s.notes.add(new NoteArticulationDetector.Anchor(130, 79, 15, 0));
        assertTrue(s.result().isEmpty());
    }

    @Test
    public void resourceOversizeAbstains() {
        var s = complete();
        assertTrue(SlurEndpointAccents.partition(s.gray, W, H, new int[4097], s.notes).isEmpty());
    }
}
