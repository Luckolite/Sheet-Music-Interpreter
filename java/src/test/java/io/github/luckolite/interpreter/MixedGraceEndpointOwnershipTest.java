// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original mixed beam stubs and principal-size controls, independent of score geometry. */
public class MixedGraceEndpointOwnershipTest {
    private static final int W = 240, H = 180;

    private static void line(byte[] p, int x, int y0, int y1) {
        for (int y = y0; y <= y1; y++) p[y * W + x] = 20;
    }

    private static byte[] raster(int side, boolean bothFull, boolean detached, boolean flip) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 250);
        line(p, 100, 60, 100);
        line(p, 128, 56, 92);
        for (int x = 100; x <= 128; x++) {
            int top = 60 - Math.round((x - 100) * 4f / 28);
            for (int dy = 0; dy < 5; dy++) p[(top + dy) * W + x] = 20;
            boolean secondary = bothFull || side == 0 && x <= 112 || side == 1 && x >= 116;
            if (detached && (x < 104 || x > 122)) secondary = false;
            if (secondary) for (int dy = 8; dy < 12; dy++) p[(top + dy) * W + x] = 20;
        }
        if (flip) {
            byte[] old = p.clone();
            for (int y = 0; y < H; y++) System.arraycopy(old, y * W, p, (H - 1 - y) * W, W);
        }
        return p;
    }

    private static int[] marked(
            int side, boolean bothFull, boolean detached, boolean flip, boolean principal)
            throws Exception {
        byte[] p = raster(side, bothFull, detached, flip);
        var component =
                Arrays.stream(OmrScoreInterpreter.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("Component"))
                        .findFirst()
                        .orElseThrow();
        var detectedClass =
                Arrays.stream(OmrScoreInterpreter.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("DetectedNote"))
                        .findFirst()
                        .orElseThrow();
        var head = component.getDeclaredConstructors()[0];
        var detected = detectedClass.getDeclaredConstructors()[0];
        head.setAccessible(true);
        detected.setAccessible(true);
        var events = new ArrayList<ScoreNoteEvent>();
        var notes = new ArrayList<Object>();
        int[] xs = flip ? new int[] {105, 133, 153} : new int[] {95, 123, 153};
        int[] ys = {100, 92, 105};
        for (int i = 0; i < 3; i++) {
            int y = flip ? H - 1 - ys[i] : ys[i];
            var event =
                    new ScoreNoteEvent(
                            0,
                            .1f + i * .2f,
                            i,
                            0,
                            1,
                            y / (float) H,
                            false,
                            0,
                            i < 2 ? 2 : 0,
                            2,
                            i < 2 ? 0 : 1);
            events.add(event);
            int halfWidth = i == 2 && principal ? 8 : 4;
            int halfHeight = i == 2 && principal ? 5 : 3;
            var box =
                    head.newInstance(
                            i == 2 && principal ? 180 : 35,
                            xs[i] - halfWidth,
                            xs[i] + halfWidth,
                            y - halfHeight,
                            y + halfHeight,
                            (float) xs[i],
                            (float) y);
            notes.add(detected.newInstance(event, box, 14f));
        }
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "markPairedGraces",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        method.setAccessible(true);
        method.invoke(null, null, p, W, H, notes, events);
        return new int[] {events.get(0).beamCount(), events.get(1).beamCount()};
    }

    @Test
    public void leftStubBelongsOnlyToTheLeftHead() throws Exception {
        assertArrayEquals(new int[] {2, 1}, marked(0, false, false, false, true));
    }

    @Test
    public void rightStubBelongsOnlyToTheRightHead() throws Exception {
        assertArrayEquals(new int[] {1, 2}, marked(1, false, false, false, true));
    }

    @Test
    public void downwardPairRetainsEndpointOwnership() throws Exception {
        assertArrayEquals(new int[] {2, 1}, marked(0, false, false, true, true));
    }

    @Test
    public void completeSecondaryRailBelongsToBothHeads() throws Exception {
        assertArrayEquals(new int[] {2, 2}, marked(0, true, false, false, true));
    }

    @Test
    public void detachedInkCannotEstablishEndpointOwnership() throws Exception {
        assertArrayEquals(new int[] {2, 2}, marked(0, false, true, false, true));
    }

    @Test
    public void missingLargerPrincipalPreservesOriginalValues() throws Exception {
        assertArrayEquals(new int[] {2, 2}, marked(0, false, false, false, false));
    }
}
