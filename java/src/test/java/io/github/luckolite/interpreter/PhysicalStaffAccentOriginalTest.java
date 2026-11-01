// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently authored two-staff engravings; source marks, not detector output, define ownership. */
public class PhysicalStaffAccentOriginalTest {
    static final int W = 1200, H = 1000, G = 14;

    static class Scene {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];
        final List<NoteArticulationDetector.Anchor> notes = new ArrayList<>();

        Scene() {
            Arrays.fill(gray, (byte) 255);
        }

        void ink(int x, int y, int label) {
            if (x >= 0 && x < W && y >= 0 && y < H) {
                gray[y * W + x] = 0;
                labels[y * W + x] = (byte) label;
            }
        }

        void staff(int top) {
            for (int k = 0; k < 5; k++)
                for (int x = 60; x < 640; x++) ink(x, top + k * G, OmrMeasurePostProcessor.STAFF);
        }

        void head(int x, int y, int staff, float top, float bottom, boolean supplied) {
            for (int dy = -5; dy <= 5; dy++)
                for (int dx = -8; dx <= 8; dx++)
                    if (dx * dx / 64.0 + dy * dy / 25.0 <= 1)
                        ink(x + dx, y + dy, OmrMeasurePostProcessor.NOTEHEAD);
            if (supplied)
                notes.add(
                        new NoteArticulationDetector.Anchor(
                                x, y, G, staff, null, top, bottom, staff, 2));
            else notes.add(new NoteArticulationDetector.Anchor(x, y, G, staff));
        }

        void accent(int x, int y) {
            for (int dx = -13; dx <= 13; dx++) {
                int dy = Math.round((13 - dx) * .27f);
                for (int z = -1; z <= 1; z++) {
                    ink(x + dx, y - dy + z, OmrMeasurePostProcessor.SYMBOL);
                    ink(x + dx, y + dy + z, OmrMeasurePostProcessor.SYMBOL);
                }
            }
        }

        void tenuto(int x, int y) {
            for (int dx = -9; dx <= 9; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    ink(x + dx, y + dy, OmrMeasurePostProcessor.SYMBOL);
        }

        int[] detect() {
            byte[] g = gray.clone(), l = labels.clone();
            int[] v = NoteArticulationDetector.detect(labels, gray, W, H, notes);
            assertArrayEquals(g, gray);
            assertArrayEquals(l, labels);
            return v;
        }
    }

    static Scene upper(boolean supplied) {
        Scene s = new Scene();
        s.staff(100);
        s.staff(259);
        s.head(300, 92, 0, 100, 156, supplied);
        s.head(300, 230, 1, 259, 315, supplied);
        s.accent(300, 170);
        s.tenuto(300, 216);
        return s;
    }

    @Test
    public void accentBelowUpperStaffBelongsToItsRemoteLedgerAttack() {
        assertArrayEquals(new int[] {1, 8}, upper(true).detect());
    }

    @Test
    public void optionalBoundsAbsentKeepLegacyAttribution() {
        assertArrayEquals(new int[] {0, 9}, upper(false).detect());
    }

    @Test
    public void legitimateBassTenutoRemainsSeparate() {
        Scene s = upper(true);
        s.accent(420, 170);
        s.head(420, 92, 0, 100, 156, true);
        assertArrayEquals(new int[] {1, 8, 1}, s.detect());
    }

    @Test
    public void brokenUpperFiveRuleWitnessPreservesLegacy() {
        Scene s = upper(true);
        for (int x = 60; x < 640; x++) {
            s.gray[100 * W + x] = (byte) 255;
            s.labels[100 * W + x] = 0;
        }
        assertArrayEquals(new int[] {0, 9}, s.detect());
    }

    @Test
    public void contradictoryFramesPreserveLegacy() {
        Scene s = upper(true);
        s.head(500, 120, 0, 101, 157, true);
        assertArrayEquals(new int[] {0, 9, 0}, s.detect());
    }

    @Test
    public void isolatedGenuineTenutoRetained() {
        Scene s = new Scene();
        s.staff(100);
        s.staff(259);
        s.head(300, 130, 0, 100, 156, true);
        s.tenuto(300, 80);
        assertArrayEquals(new int[] {8}, s.detect());
    }
}
