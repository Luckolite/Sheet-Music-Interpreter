// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original registered-staff versus finite-mark role oracles, frozen before V2 repair code. */
public class RegisteredTenutoStaffOwnershipTest {
    private static final int W = 1000, H = 400, GAP = 16;
    private static final int[] FOUR = {220, 260, 300, 340};

    private static final class Scene {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];
        final List<NoteArticulationDetector.Anchor> anchors = new ArrayList<>();

        Scene() {
            Arrays.fill(gray, (byte) 255);
        }

        void ink(int x, int y, int tone, int label) {
            if (x < 0 || x >= W || y < 0 || y >= H) throw new AssertionError("fixture bounds");
            int p = y * W + x;
            gray[p] = (byte) Math.min(gray[p] & 255, tone);
            if (label != 0) labels[p] = (byte) label;
        }

        void staff(int top, int tone, int separatedRow, int patchX) {
            for (int line = 0; line < 5; line++)
                for (int x = 80; x <= 620; x++) {
                    int y = top + line * GAP;
                    if (y == separatedRow
                            && (x == patchX - 12
                                    || x == patchX - 11
                                    || x == patchX + 11
                                    || x == patchX + 12)) continue;
                    ink(x, y, tone, OmrMeasurePostProcessor.STAFF);
                }
        }

        void head(int x, int y, StaffPitchTrack track) {
            anchors.add(new NoteArticulationDetector.Anchor(x, y, GAP, 0, track));
            for (int dy = -6; dy <= 6; dy++)
                for (int dx = -9; dx <= 9; dx++)
                    if (dx * dx / 81.0 + dy * dy / 36.0 <= 1)
                        ink(x + dx, y + dy, 0, OmrMeasurePostProcessor.NOTEHEAD);
        }

        void dash(int x, int y) {
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -10; dx <= 10; dx++)
                    ink(x + dx, y + dy, 0, OmrMeasurePostProcessor.SYMBOL);
        }

        int[] detect() {
            byte[] g = gray.clone(), l = labels.clone();
            int[] actual = NoteArticulationDetector.detect(labels, gray, W, H, anchors);
            assertArrayEquals(g, gray);
            assertArrayEquals(l, labels);
            return actual;
        }
    }

    private static StaffPitchTrack registered(float firstX, float lastX, float bottom) {
        // These are fixture-authored independently visible physical rules, not inferred note roles.
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {firstX, bottom, GAP}, new float[] {lastX, bottom, GAP}));
    }

    private static void aboveStaff(int tone, int phase) {
        Scene s = new Scene();
        s.staff(240, tone, -1, -1);
        StaffPitchTrack track = registered(80, 620, 304);
        for (int x : FOUR) {
            s.head(x, 264, track);
            s.dash(x, 224 + phase);
        }
        assertArrayEquals(new int[] {8, 8, 8, 8}, s.detect());
    }

    @Test
    public void fourAboveStaffTenutosHaveTheirOwnRoles() {
        for (int phase : new int[] {-2, 0, 2}) aboveStaff(200, phase);
    }

    @Test
    public void faintRegisteredStaffDoesNotShiftItsTopRuleOntoTenutos() {
        for (int phase : new int[] {-2, 0, 2}) aboveStaff(230, phase);
    }

    @Test
    public void separatedPatchOnRegisteredMemberRuleRemainsStaff() {
        for (int tone : new int[] {200, 230}) {
            Scene s = new Scene();
            s.staff(240, tone, 272, 260);
            s.head(260, 304, registered(80, 620, 304));
            s.dash(260, 272);
            assertEquals(0, s.detect()[0] & NoteArticulation.TENUTO);
        }
    }

    @Test
    public void registeredOwnerDoesNotEraseUnknownForeignStaff() {
        Scene s = new Scene();
        s.staff(224, 200, -1, -1);
        s.staff(160, 230, 192, 260);
        // The owner has a complete registered lower staff; the separate upper group is raw-only.
        s.head(260, 224, registered(80, 620, 288));
        s.dash(260, 192);
        assertEquals(0, s.detect()[0] & NoteArticulation.TENUTO);
    }

    @Test
    public void outOfRangeTrackCannotDisclaimRawFiveRules() {
        Scene s = new Scene();
        s.staff(160, 230, 192, 260);
        // Track samples are genuinely drawn but do not cover this candidate's column.
        for (int line = 0; line < 5; line++)
            for (int x = 400; x <= 620; x++)
                s.ink(x, 240 + line * GAP, 200, OmrMeasurePostProcessor.STAFF);
        s.head(260, 224, registered(400, 620, 304));
        s.dash(260, 192);
        assertEquals(0, s.detect()[0] & NoteArticulation.TENUTO);
    }
}
