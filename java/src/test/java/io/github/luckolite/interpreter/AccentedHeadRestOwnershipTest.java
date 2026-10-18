// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original mathematical chevrons, ovals and shafts; no score/font/model data. */
public class AccentedHeadRestOwnershipTest {
    static final int W = 256, H = 240;
    static final float G = 16;

    static class Page {
        final byte[] gray = new byte[W * H];
        final ScoreNoteEvent note =
                new ScoreNoteEvent(0, 126f / W, 2, 0, 1, 185f / H, false, 0, 0, 2, 2)
                        .withStemDirection(1);
        final List<int[]> runs = new ArrayList<>();
        List<ScoreNoteEvent> notes;

        Page() {
            Arrays.fill(gray, (byte) 240);
            for (int y = 180; y <= 190; y++) {
                int l = W, r = -1;
                for (int x = 119; x <= 133; x++) {
                    double d = Math.pow((x - 126) / 7., 2) + Math.pow((y - 185) / 5., 2);
                    if (d <= 1) {
                        l = Math.min(l, x);
                        r = x;
                        if (d >= .45) pixel(x, y, 40);
                    }
                }
                if (r >= l) runs.add(new int[] {y, l, r});
            }
            for (int y = 135; y <= 185; y++) for (int x = 132; x <= 133; x++) pixel(x, y, 40);
            for (int x = 111; x <= 139; x++) {
                double u = (x - 111) / 28.;
                for (int y = 108; y <= 124; y++)
                    if (Math.abs(y - (109 + 7 * u)) <= 1 || Math.abs(y - (123 - 7 * u)) <= 1)
                        pixel(x, y, 40);
            }
            notes =
                    RestFigureOrigins.bind(
                            List.of(note),
                            Map.of(
                                    note,
                                    RestFigureOrigins.HeadOrigin.accepted(
                                            new Object(), gray, W, H, runs)));
        }

        void pixel(int x, int y, int v) {
            gray[y * W + x] = (byte) v;
        }

        boolean owned() {
            return AccentedHeadRestOwnership.owns(
                    gray, W, H, notes, note, 111, 140, 108, 151, new boolean[44], 108, G);
        }
    }

    @Test
    public void noOriginCannotOwnAHead() {
        Page p = new Page();
        p.notes = List.of(p.note);
        assertFalse(p.owned());
    }

    @Test
    public void disconnectedShaftCannotOwnRest() {
        Page p = new Page();
        for (int y = 157; y <= 160; y++) for (int x = 131; x <= 134; x++) p.pixel(x, y, 240);
        assertFalse(p.owned());
    }

    @Test
    public void extraEighthBulbBesideHeldVoiceMustRemain() {
        Page p = new Page();
        for (int y = 139; y <= 144; y++) for (int x = 117; x <= 125; x++) p.pixel(x, y, 40);
        assertFalse(p.owned());
    }

    @Test
    public void alignedIndependentRestMustRemain() {
        Page p = new Page();
        for (int y = 128; y <= 146; y++) p.pixel(121, y, 40);
        for (int x = 115; x <= 123; x++) p.pixel(x, 129, 40);
        assertFalse(p.owned());
    }

    @Test
    public void unknownBranchMustRemain() {
        Page p = new Page();
        for (int x = 124; x <= 132; x++) p.pixel(x, 143, 40);
        assertFalse(p.owned());
    }

    @Test
    public void faintGenuineRestMustRemain() {
        Page p = new Page();
        for (int y = 138; y <= 146; y++) for (int x = 118; x <= 125; x++) p.pixel(x, y, 160);
        assertFalse(p.owned());
    }

    @Test
    public void genuineStaffTouchingRestMustRemain() {
        Page p = new Page();
        for (int x = 0; x < W; x++) p.pixel(x, 142, 40);
        for (int y = 132; y <= 147; y++) p.pixel(119, y, 40);
        assertFalse(p.owned());
    }

    @Test
    public void nearbyAccentWithGenuineRestMustRemain() {
        Page p = new Page();
        for (int x = 115; x <= 123; x++) for (int y = 132; y <= 136; y++) p.pixel(x, y, 40);
        for (int y = 135; y <= 151; y++) p.pixel(122, y, 40);
        assertFalse(p.owned());
    }
}
