// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original filled ovals, short stems, independent beam strips and thin expression arms. */
public class ShortStemHeadBeamTest {
    static final int W = 320, H = 280;
    static final float GAP = 16;
    byte[] gray, labels;

    void rect(int left, int right, int top, int bottom, int kind) {
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                gray[y * W + x] = 25;
                if (kind > 0) labels[y * W + x] = (byte) kind;
            }
    }

    void draw(int length, int beams, boolean expression, boolean up) {
        draw(length, beams, expression, up, false);
    }

    void draw(int length, int beams, boolean expression, boolean up, boolean sloping) {
        draw(length, beams, expression, up, sloping, false);
    }

    void draw(
            int length,
            int beams,
            boolean expression,
            boolean up,
            boolean sloping,
            boolean tiltedHead) {
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        for (int line = 80; line <= 144; line += 16) rect(20, 300, line, line, 4);
        for (int y = 150; y <= 170; y++)
            for (int x = 138; x <= 162; x++) {
                double dx = x - 150, dy = y - 160, angle = tiltedHead ? .45 : 0;
                double u = dx * Math.cos(angle) - dy * Math.sin(angle);
                double v = dx * Math.sin(angle) + dy * Math.cos(angle);
                if (Math.pow(u / 12., 2) + Math.pow(v / 9., 2) <= 1) {
                    gray[y * W + x] = 25;
                    labels[y * W + x] = 2;
                }
            }
        int shaftX = sloping ? 137 : 138;
        rect(shaftX, shaftX + 1, 160, 160 + length, 1);
        for (int beam = 0; beam < beams; beam++)
            for (int x = shaftX; x <= 225; x++) {
                int slope = sloping ? Math.round((x - shaftX) * .2f) : 0;
                rect(
                        x,
                        x,
                        160 + length - 6 - beam * 12 - slope,
                        160 + length - beam * 12 - slope,
                        1);
            }
        if (expression) {
            for (int x = 130; x <= 235; x++) {
                int offset = Math.round((x - 130) * .08f);
                rect(x, x, 160 + length + 5 + offset, 161 + length + 5 + offset, 0);
                rect(x, x, 160 + length + 5 - offset, 161 + length + 5 - offset, 0);
            }
        }
        if (up) {
            byte[] g = new byte[gray.length], l = new byte[labels.length];
            for (int i = 0; i < gray.length; i++) {
                g[gray.length - 1 - i] = gray[i];
                l[labels.length - 1 - i] = labels[i];
            }
            gray = g;
            labels = l;
        }
    }

    int count(boolean up) throws Exception {
        Class<?> hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        Constructor<?> ctor = hc.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object head =
                up
                        ? ctor.newInstance(330, 157, 181, 110, 128, 169f, 119f)
                        : ctor.newInstance(330, 138, 162, 151, 169, 150f, 160f);
        Class<?> sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        Constructor<?> staffCtor = sc.getDeclaredConstructor(float.class, float.class, float.class);
        staffCtor.setAccessible(true);
        Object staff =
                up ? staffCtor.newInstance(135f, 199f, GAP) : staffCtor.newInstance(80f, 144f, GAP);
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        hc,
                        sc,
                        List.class);
        m.setAccessible(true);
        return (int) m.invoke(null, labels, gray, W, H, head, staff, List.of(head));
    }

    void endingHairpin(int length, boolean up) {
        draw(length, 1, false, false, true, true);
        int tipX = 230, tipY = 160 + length + 22;
        for (int x = 120; x <= tipX; x++) {
            int offset = Math.round((tipX - x) * .16f);
            rect(x, x, tipY - offset, tipY - offset + 1, 0);
            rect(x, x, tipY + offset, tipY + offset + 1, 0);
        }
        if (up) {
            byte[] g = new byte[gray.length], l = new byte[labels.length];
            for (int i = 0; i < gray.length; i++) {
                g[gray.length - 1 - i] = gray[i];
                l[labels.length - 1 - i] = labels[i];
            }
            gray = g;
            labels = l;
        }
    }

    @Test
    public void shortDownStemDoesNotCountItsOwnOval() throws Exception {
        draw(37, 1, false, false);
        assertEquals(1, count(false));
    }

    @Test
    public void shortUpStemDoesNotCountItsOwnOval() throws Exception {
        draw(37, 1, false, true);
        assertEquals(1, count(true));
    }

    @Test
    public void slopingShortDownBeamDoesNotCountTheOvalTail() throws Exception {
        draw(37, 1, false, false, true);
        assertEquals(1, count(false));
    }

    @Test
    public void slopingShortUpBeamDoesNotCountTheOvalTail() throws Exception {
        draw(37, 1, false, true, true);
        assertEquals(1, count(true));
    }

    @Test
    public void tiltedOvalUnderShortDownStemIsNotASecondBeam() throws Exception {
        draw(37, 1, false, false, true, true);
        assertEquals(1, count(false));
    }

    @Test
    public void tiltedOvalUnderShortUpStemIsNotASecondBeam() throws Exception {
        draw(37, 1, false, true, true, true);
        assertEquals(1, count(true));
    }

    @Test
    public void shorterTiltedDownOvalDoesNotBecomeAnotherBeam() throws Exception {
        draw(35, 1, false, false, true, true);
        assertEquals(1, count(false));
    }

    @Test
    public void shorterTiltedUpOvalDoesNotBecomeAnotherBeam() throws Exception {
        draw(35, 1, false, true, true, true);
        assertEquals(1, count(true));
    }

    @Test
    public void detachedEndingHairpinDoesNotAddADownStemBeam() throws Exception {
        endingHairpin(35, false);
        assertEquals(1, count(false));
    }

    @Test
    public void detachedEndingHairpinDoesNotAddAnUpStemBeam() throws Exception {
        endingHairpin(35, true);
        assertEquals(1, count(true));
    }

    @Test
    public void expressionArmsDoNotAddBeams() throws Exception {
        draw(37, 1, true, false);
        assertEquals(1, count(false));
    }

    @Test
    public void reversedExpressionArmsDoNotAddBeams() throws Exception {
        draw(37, 1, true, true);
        assertEquals(1, count(true));
    }

    @Test
    public void longSingleBeamRetainsItsCount() throws Exception {
        draw(64, 1, false, false);
        assertEquals(1, count(false));
    }

    @Test
    public void genuineDoubleBeamRetainsItsCount() throws Exception {
        draw(64, 2, false, false);
        assertEquals(2, count(false));
    }

    @Test
    public void genuineTripleBeamRetainsItsCount() throws Exception {
        draw(76, 3, false, false);
        assertEquals(3, count(false));
    }

    @Test
    public void inputPixelsRemainUnchanged() throws Exception {
        draw(37, 1, false, false);
        byte[] g = gray.clone(), l = labels.clone();
        count(false);
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }
}
