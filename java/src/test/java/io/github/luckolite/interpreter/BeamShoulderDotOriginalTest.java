// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original staff crossings, beam shoulders and independently printed dot disks. */
public final class BeamShoulderDotOriginalTest {
    private static final int W = 280, H = 170;
    private final byte[] gray = new byte[W * H];

    public BeamShoulderDotOriginalTest() {
        Arrays.fill(gray, (byte) 250);
    }

    private void pixel(int x, int y) {
        gray[y * W + x] = 0;
    }

    private void shoulder(boolean up) {
        for (int x = 20; x <= 245; x++) pixel(x, 82);
        for (int x = 127; x <= 220; x++) for (int y = 77; y <= 82; y++) pixel(x, y);
        for (int y = up ? 45 : 77; y <= (up ? 82 : 120); y++) pixel(127, y);
    }

    private void disk() {
        for (int y = 76; y <= 84; y++)
            for (int x = 122; x <= 130; x++)
                if ((x - 126) * (x - 126) + (y - 80) * (y - 80) <= 10) pixel(x, y);
    }

    private void tie() {
        for (int x = 126; x <= 210; x++) {
            pixel(x, 80);
            pixel(x, 81);
        }
    }

    private int count(float gap) throws Exception {
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object head = ctor.newInstance(260, 90, 112, 71, 89, 101f, 80f);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "countAugmentationDots",
                        List.class,
                        c,
                        float.class,
                        byte[].class,
                        int.class,
                        int.class,
                        boolean.class,
                        List.class,
                        List.class);
        method.setAccessible(true);
        return (int)
                method.invoke(
                        null, List.of(), head, gap, gray, W, H, false, List.of(), List.of(head));
    }

    @Test
    public void upperStemOwnsTheBeamShoulderAtFractionalSpacing() throws Exception {
        shoulder(true);
        assertEquals(0, count(13.75f));
    }

    @Test
    public void lowerStemOwnsTheBeamShoulderAtFractionalSpacing() throws Exception {
        shoulder(false);
        assertEquals(0, count(13.75f));
    }

    @Test
    public void completeDiskEdgeIsCheckedAtAnotherFractionalSpacing() throws Exception {
        shoulder(true);
        assertEquals(0, count(13.5f));
    }

    @Test
    public void completeDiskEdgeIsCheckedAtIntegerSpacing() throws Exception {
        shoulder(false);
        assertEquals(0, count(13f));
    }

    @Test
    public void aGenuineRoundDotWithThinTieRemains() throws Exception {
        disk();
        tie();
        assertEquals(1, count(13.75f));
    }

    @Test
    public void aDetachedRoundDotRemains() throws Exception {
        disk();
        assertEquals(1, count(13.75f));
    }

    @Test
    public void aDisconnectedNearbyShaftDoesNotOwnTheDot() throws Exception {
        disk();
        tie();
        for (int y = 40; y <= 71; y++) pixel(123, y);
        assertEquals(1, count(13.75f));
    }

    @Test
    public void ownershipDoesNotRewriteTheRaster() throws Exception {
        shoulder(true);
        byte[] before = gray.clone();
        count(13.75f);
        assertArrayEquals(before, gray);
    }
}
