// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic engraved ribbons, heads and notation; no score raster pixels. */
public class BoldReturningSlurOriginalTest {
    static final int W = 520, H = 310;
    static final float G = 20;

    static class Scene {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        List<PrintedSlurOwners.Head> heads = new ArrayList<>();
        StaffPitchTrack track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 170, G}, new float[] {W - 1, 170, G}));

        Scene() {
            Arrays.fill(gray, (byte) 255);
            for (int r = 0; r < 5; r++)
                for (int x = 10; x < W - 10; x++) gray[(170 - r * 20) * W + x] = 0;
            head(0, 78, 180);
            head(1, 402, 180);
        }

        void head(int id, int cx, int cy) {
            heads.add(new PrintedSlurOwners.Head(id, cx, cy, 0, 1, 0, id * 960L, 60 + id * 4));
            for (int y = cy - 5; y <= cy + 5; y++)
                for (int x = cx - 9; x <= cx + 9; x++)
                    if ((x - cx) * (x - cx) / 81d + (y - cy) * (y - cy) / 25d <= 1) {
                        gray[y * W + x] = 0;
                        labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                    }
        }

        void ribbon(boolean above, boolean straight) {
            for (int x = 90; x <= 390; x++) {
                double t = 2d * (x - 90) / 300 - 1;
                double y = above ? 55 + 30 * t * t : 225 - 30 * t * t;
                if (straight) y = above ? 55 : 225;
                double half = 1 + 5 * (1 - t * t);
                for (int yy = (int) Math.ceil(y - half); yy <= Math.floor(y + half); yy++)
                    gray[yy * W + x] = 0;
            }
        }

        boolean owns() {
            byte[] g = gray.clone(), l = labels.clone();
            var frame =
                    new PrintedSlurPageCapture.Frame<PrintedSlurOwners.Head>(
                            0, 10, W - 1, H - 1, track, heads);
            var result =
                    PrintedSlurPageCapture.capture(
                            0, 8, gray, labels, W, H, List.of(frame), Set.of());
            assertFalse(result.limitReached());
            assertArrayEquals(g, gray);
            assertArrayEquals(l, labels);
            return !result.source().spans().isEmpty();
        }
    }

    @Test
    public void boldEngravedLowerRibbonOwnsTwoAttacks() {
        var s = new Scene();
        s.ribbon(false, false);
        assertTrue(s.owns());
    }

    @Test
    public void boldEngravedUpperRibbonOwnsTwoAttacks() {
        var s = new Scene();
        s.heads.clear();
        s.head(0, 78, 110);
        s.head(1, 402, 110);
        s.ribbon(true, false);
        assertTrue(s.owns());
    }

    @Test
    public void ordinaryStraightBeamIsNotPhrase() {
        var s = new Scene();
        s.ribbon(false, true);
        assertFalse(s.owns());
    }

    @Test
    public void textArchWithVerticalLegsIsNotPhrase() {
        var s = new Scene();
        s.ribbon(false, false);
        for (int x : new int[] {90, 390})
            for (int y = 195; y < 265; y++)
                for (int dx = -2; dx <= 2; dx++) s.gray[y * W + x + dx] = 0;
        assertFalse(s.owns());
    }

    @Test
    public void fermataWithOnlyOneOwnedHeadIsNotPhrase() {
        var s = new Scene();
        s.ribbon(false, false);
        s.heads.remove(1);
        for (int y = 240; y <= 244; y++) for (int x = 238; x <= 242; x++) s.gray[y * W + x] = 0;
        assertFalse(s.owns());
    }

    @Test
    public void connectedCrossbarRemainsUnknown() {
        var s = new Scene();
        s.ribbon(false, false);
        for (int y = 190; y <= 260; y++)
            for (int dx = -1; dx <= 1; dx++) s.gray[y * W + 240 + dx] = 0;
        assertFalse(s.owns());
    }

    @Test
    public void competingEndpointOwnerAbstains() {
        var s = new Scene();
        s.ribbon(false, false);
        s.head(2, 403, 180);
        assertFalse(s.owns());
    }

    @Test
    public void semanticallyHeadLabelledRibbonIsNotPhrase() {
        var s = new Scene();
        s.ribbon(false, false);
        for (int y = 190; y < 240; y++)
            for (int x = 90; x <= 390; x++)
                if ((s.gray[y * W + x] & 255) < 205)
                    s.labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
        assertFalse(s.owns());
    }

    @Test
    public void clippedRibbonCannotInventMissingCap() {
        var s = new Scene();
        s.ribbon(false, false);
        for (int y = 190; y < 240; y++)
            for (int x = 90; x < 150; x++) s.gray[y * W + x] = (byte) 255;
        assertFalse(s.owns());
    }

    @Test
    public void independentForeignBranchCannotBeCropped() {
        var s = new Scene();
        s.ribbon(false, false);
        for (int x = 240; x <= 320; x++) {
            int y = 225 + (x - 240) / 3;
            s.gray[y * W + x] = 0;
        }
        assertFalse(s.owns());
    }
}
