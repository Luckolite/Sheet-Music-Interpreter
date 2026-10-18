// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Independent neighboring tenutos do not change the original complete foreign staff member into a mark. */
public class MixedContextForeignTenutoRailTest {
    private static final int W = 1000, H = 400, GAP = 16, X = 260;

    private static final class Scene {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];
        final boolean mirror;

        Scene(boolean mirror) {
            this.mirror = mirror;
            Arrays.fill(gray, (byte) 255);
        }

        int y(int y) {
            return mirror ? H - y : y;
        }

        void ink(int x, int y, int tone, int role) {
            y = y(y);
            assertTrue(x >= 0 && x < W && y >= 0 && y < H);
            int p = y * W + x;
            gray[p] = (byte) Math.min(gray[p] & 255, tone);
            if (role != 0) labels[p] = (byte) role;
        }

        void box(int l, int t, int r, int b, int tone, int role) {
            for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) ink(x, y, tone, role);
        }
    }

    private static int bend(int x) {
        int d = Math.abs(x - X);
        if (d <= 14) return 3;
        if (d >= 24) return 0;
        return Math.round(3 * (24 - d) / 10f);
    }

    private static Scene originalScene(boolean mirror, int foreignTone) {
        Scene s = new Scene(mirror);
        // Independently visible registered owner staff: five complete flat rails.
        for (int line = 0; line < 5; line++)
            for (int x = 80; x <= 620; x++)
                s.ink(x, 243 + line * GAP, 200, OmrMeasurePostProcessor.STAFF);
        // A SECOND actual five-rule staff shares one coherent shallow bend across ALL five rails.
        // Only its candidate member has two bounded paper dropouts beside the dark central patch.
        for (int line = 0; line < 5; line++)
            for (int x = 80; x <= 620; x++) {
                if (line == 0 && Math.abs(x - X) <= 12) continue;
                s.ink(x, 221 + line * GAP + bend(x), foreignTone, OmrMeasurePostProcessor.STAFF);
            }
        // This finite central patch is a genuine rule member, not an independently authored tenuto.
        s.box(X - 10, 223, X + 10, 225, 0, OmrMeasurePostProcessor.SYMBOL);
        int head = 264;
        for (int dy = -6; dy <= 6; dy++)
            for (int dx = -9; dx <= 9; dx++)
                if (dx * dx / 81.0 + dy * dy / 36.0 <= 1)
                    s.ink(X + dx, head + dy, 0, OmrMeasurePostProcessor.NOTEHEAD);
        return s;
    }

    private static boolean neighborFootprint(int x, int y) {
        for (int center : new int[] {220, 300, 340}) {
            int dx = x - center, dy = y - 264;
            if (dx * dx / 81.0 + dy * dy / 36.0 <= 1) return true;
            if (x >= center - 10 && x <= center + 10 && y >= 223 && y <= 225) return true;
        }
        return false;
    }

    private static void addIndependentNeighbors(Scene s) {
        for (int center : new int[] {220, 300, 340}) {
            for (int dy = -6; dy <= 6; dy++)
                for (int dx = -9; dx <= 9; dx++)
                    if (dx * dx / 81.0 + dy * dy / 36.0 <= 1)
                        s.ink(center + dx, 264 + dy, 0, OmrMeasurePostProcessor.NOTEHEAD);
            s.box(center - 10, 223, center + 10, 225, 0, OmrMeasurePostProcessor.SYMBOL);
        }
    }

    private static void assertMixedOwnership(boolean mirror, int foreignTone) {
        Scene s = originalScene(mirror, foreignTone);
        byte[] originalG = s.gray.clone(), originalL = s.labels.clone();
        addIndependentNeighbors(s);
        for (int imageY = 0; imageY < H; imageY++)
            for (int x = 0; x < W; x++) {
                int y = mirror ? H - imageY : imageY, p = imageY * W + x;
                assertTrue(
                        "MIN-only addition cannot lighten existing ink",
                        (s.gray[p] & 255) <= (originalG[p] & 255));
                if (s.gray[p] != originalG[p] || s.labels[p] != originalL[p])
                    assertTrue(
                            "All changes belong to declared new note/mark footprints",
                            neighborFootprint(x, y));
                if (x >= 250 && x <= 270 && y >= 223 && y <= 225
                        || x >= 251 && x <= 269 && y >= 258 && y <= 270) {
                    assertEquals("Original target raw pixels unchanged", originalG[p], s.gray[p]);
                    assertEquals(
                            "Original target semantic pixels unchanged", originalL[p], s.labels[p]);
                }
            }
        float bottom = mirror ? 157 : 307;
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {80, bottom, GAP}, new float[] {620, bottom, GAP}));
        var anchors = new ArrayList<NoteArticulationDetector.Anchor>();
        anchors.add(new NoteArticulationDetector.Anchor(X, s.y(264), GAP, 0, track));
        for (int center : new int[] {220, 300, 340})
            anchors.add(new NoteArticulationDetector.Anchor(center, s.y(264), GAP, 0, track));
        byte[] beforeG = s.gray.clone(), beforeL = s.labels.clone();
        int[] actual = NoteArticulationDetector.detect(s.labels, s.gray, W, H, anchors);
        assertArrayEquals(beforeG, s.gray);
        assertArrayEquals(beforeL, s.labels);
        assertEquals(4, actual.length);
        int[] tenuto = new int[4];
        for (int i = 0; i < 4; i++) tenuto[i] = actual[i] & NoteArticulation.TENUTO;
        assertArrayEquals(
                "Original foreign member stays rule0; three independently drawn neighboring tenutos stay8",
                new int[] {0, 8, 8, 8},
                tenuto);
    }

    @Test
    public void upperTone200KeepsForeignMemberAndThreeGenuineTenutos() {
        assertMixedOwnership(false, 200);
    }

    @Test
    public void upperTone230KeepsForeignMemberAndThreeGenuineTenutos() {
        assertMixedOwnership(false, 230);
    }

    @Test
    public void lowerTone200KeepsForeignMemberAndThreeGenuineTenutos() {
        assertMixedOwnership(true, 200);
    }

    @Test
    public void lowerTone230KeepsForeignMemberAndThreeGenuineTenutos() {
        assertMixedOwnership(true, 230);
    }
}
