// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original semantic shafts and grayscale heads; no score pixels or musical identities. */
public class SmallHeadFallbackBeamTest {
    static final int W = 240, H = 180;
    static final float GAP = 16;

    static final class Page {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];

        Page() {
            Arrays.fill(gray, (byte) 255);
            rect(94, 76, 95, 99, 185, 1);
            rect(82, 94, 95, 105, 20, 2);
        }

        void rect(int left, int top, int right, int bottom, int ink, int label) {
            for (int y = top; y <= bottom; y++)
                for (int x = left; x <= right; x++) {
                    gray[y * W + x] = (byte) ink;
                    labels[y * W + x] = (byte) label;
                }
        }

        void beam(int top) {
            rect(94, top, 157, top + 4, 20, 5);
        }

        int count() throws Exception {
            Class<?> component = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
            Class<?> staff = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
            var hc =
                    component.getDeclaredConstructor(
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            float.class,
                            float.class);
            var sc = staff.getDeclaredConstructor(float.class, float.class, float.class);
            hc.setAccessible(true);
            sc.setAccessible(true);
            Object head = hc.newInstance(168, 82, 95, 94, 105, 88.5f, 99.5f);
            Object frame = sc.newInstance(68f, 132f, GAP);
            var method =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "detectBeamCount",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            component,
                            staff,
                            boolean.class);
            method.setAccessible(true);
            return (int) method.invoke(null, labels, gray, W, H, head, frame, false);
        }
    }

    @Test
    public void headInkCannotAddASecondBeamToSemanticShaft() throws Exception {
        var p = new Page();
        p.beam(74);
        assertEquals(1, p.count());
    }

    @Test
    public void headInkAloneCannotCreateABeam() throws Exception {
        assertEquals(0, new Page().count());
    }

    @Test
    public void twoPrintedBeamsRemainTwo() throws Exception {
        var p = new Page();
        p.beam(74);
        p.beam(85);
        p.rect(94, 74, 95, 99, 20, 1);
        assertEquals(2, p.count());
    }

    @Test
    public void bandExtendingPastHeadBoundsRemainsEvidence() throws Exception {
        var p = new Page();
        p.beam(74);
        p.rect(82, 97, 157, 101, 20, 5);
        assertEquals(2, p.count());
    }

    @Test
    public void removingSemanticShaftDoesNotBorrowDetachedBeam() throws Exception {
        var p = new Page();
        p.beam(74);
        p.rect(94, 76, 95, 93, 255, 0);
        assertEquals(0, p.count());
    }

    @Test
    public void sourcePixelsAndLabelsRemainUnchanged() throws Exception {
        var p = new Page();
        p.beam(74);
        byte[] gray = p.gray.clone(), labels = p.labels.clone();
        p.count();
        assertArrayEquals(gray, p.gray);
        assertArrayEquals(labels, p.labels);
    }
}
