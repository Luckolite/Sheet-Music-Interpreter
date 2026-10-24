// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Literal complete staff and separate cap ink distinguish one-sided glyphs from rail fringes. */
public class FiniteStaffBandSlurOriginalsTest {
    static final class Field {
        final int w, h, scale, x, y;
        final float gap, bottom;
        final byte[] gray, labels;
        final Object frames;
        final StaffPitchTrack track;

        Field(int scale, float phase, int line) throws Exception {
            this.scale = scale;
            w = 320 * scale;
            h = 240 * scale;
            gap = 16 * scale;
            bottom = (208 + phase) * scale;
            x = 96 * scale;
            y = Math.round(bottom - line * gap - 4 * scale);
            gray = new byte[w * h];
            labels = new byte[gray.length];
            Arrays.fill(gray, (byte) 255);
            for (int r = 0; r < 5; r++)
                for (int yy = Math.round(bottom - r * gap) - scale;
                        yy <= Math.round(bottom - r * gap) + scale;
                        yy++) for (int xx = 0; xx < w; xx++) gray[yy * w + xx] = 60;
            for (int xx = x; xx <= 224 * scale; xx++) gray[y * w + xx] = 0;
            float[] bases = new float[w], gaps = new float[w];
            Arrays.fill(bases, bottom);
            Arrays.fill(gaps, gap);
            var type =
                    Arrays.stream(PrintedSlurContours.class.getDeclaredClasses())
                            .filter(c -> c.getSimpleName().equals("Frames"))
                            .findFirst()
                            .orElseThrow();
            var ctor = type.getDeclaredConstructor(float[].class, float[].class);
            ctor.setAccessible(true);
            frames = ctor.newInstance(bases, gaps);
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, bottom, gap},
                                    new float[] {w - 1, bottom, gap}));
        }

        boolean ink(int xx, int yy, PrintedSlurContours.HeadEvidence heads) throws Exception {
            var m =
                    PrintedSlurContours.class.getDeclaredMethod(
                            "ink",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            float.class,
                            float.class,
                            frames.getClass(),
                            PrintedSlurContours.HeadEvidence.class);
            m.setAccessible(true);
            byte[] g0 = gray.clone(), l0 = labels.clone();
            boolean result =
                    (boolean)
                            m.invoke(null, gray, labels, w, h, xx, yy, bottom, gap, frames, heads);
            assertArrayEquals(g0, gray);
            assertArrayEquals(l0, labels);
            return result;
        }

        boolean cap() throws Exception {
            return ink(x, y, null);
        }
    }

    @Test
    public void oneSidedGlyphCapCannotOwnItsOwnStaffFringe() throws Exception {
        assertTrue(new Field(1, 0, 0).cap());
    }

    @Test
    public void halfPixelPhasePreservesSeparateCapInk() throws Exception {
        assertTrue(new Field(1, .5f, 0).cap());
    }

    @Test
    public void quarterPixelPhasePreservesSeparateCapInk() throws Exception {
        assertTrue(new Field(1, .25f, 0).cap());
    }

    @Test
    public void doubledDensityPreservesSeparateCapInk() throws Exception {
        assertTrue(new Field(2, 0, 0).cap());
    }

    @Test
    public void tripledDensityPreservesSeparateCapInk() throws Exception {
        assertTrue(new Field(3, .5f, 0).cap());
    }

    @Test
    public void allFiveRailsUseTheSameFringeRule() throws Exception {
        for (int r = 0; r < 5; r++) assertTrue(new Field(1, 0, r).cap());
    }

    @Test
    public void existingRawThresholdIsNotLowered() throws Exception {
        var f = new Field(1, 0, 0);
        f.gray[f.y * f.w + f.x] = (byte) 206;
        assertFalse(f.cap());
    }

    @Test
    public void boundaryShadeStillRequiresRealInk() throws Exception {
        var f = new Field(1, 0, 0);
        f.gray[f.y * f.w + f.x] = (byte) 205;
        assertTrue(f.cap());
    }

    @Test
    public void paperCannotBecomeMissingCurveInk() throws Exception {
        var f = new Field(1, 0, 0);
        f.gray[f.y * f.w + f.x] = (byte) 255;
        assertFalse(f.cap());
    }

    @Test
    public void allActualStaffCoresRemainExcluded() throws Exception {
        var f = new Field(1, 0, 0);
        for (int r = 0; r < 5; r++) assertFalse(f.ink(40, Math.round(f.bottom - r * f.gap), null));
    }

    @Test
    public void oneSidedActualCoreKeepsItsEstablishedOwner() throws Exception {
        var f = new Field(1, 0, 0);
        int row = Math.round(f.bottom);
        for (int xx = 0; xx < f.x; xx++)
            for (int dy = -1; dy <= 1; dy++) f.gray[(row + dy) * f.w + xx] = (byte) 255;
        assertFalse(f.ink(f.x, row, null));
    }

    @Test
    public void bilateralDarkFringeRetainsStaffOwnership() throws Exception {
        var f = new Field(1, 0, 0);
        for (int yy = f.y; yy <= Math.round(f.bottom); yy++)
            for (int xx = 0; xx < f.w; xx++) f.gray[yy * f.w + xx] = 60;
        assertFalse(f.cap());
    }

    @Test
    public void explicitlyTypedHeadInkRemainsExcluded() throws Exception {
        var f = new Field(1, 0, 0);
        f.labels[f.y * f.w + f.x] = OmrMeasurePostProcessor.NOTEHEAD;
        assertFalse(f.cap());
    }

    @Test
    public void explicitlyTypedClefInkRemainsExcluded() throws Exception {
        var f = new Field(1, 0, 0);
        f.labels[f.y * f.w + f.x] = OmrMeasurePostProcessor.CLEF_OR_KEY;
        assertFalse(f.cap());
    }

    @Test
    public void independentRawHeadHintKeepsPrecedence() throws Exception {
        var f = new Field(1, 0, 0);
        assertFalse(
                f.ink(
                        f.x,
                        f.y,
                        new PrintedSlurContours.HeadEvidence() {
                            public boolean contains(int x, int y) {
                                return x == f.x && y == f.y;
                            }

                            public boolean closes(
                                    PrintedSlurContours.Point cap, int x, int y, boolean first) {
                                return false;
                            }
                        }));
    }

    @Test
    public void absentExteriorContrastRemainsUnproved() throws Exception {
        var f = new Field(1, 0, 0);
        int d = Math.round(f.gap * .65f);
        f.gray[(f.y - d) * f.w + f.x] = f.gray[(f.y + d) * f.w + f.x] = 0;
        assertFalse(f.cap());
    }

    @Test
    public void preservedStraightCapIsNotACompleteBow() throws Exception {
        var f = new Field(1, 0, 0);
        byte[] before = f.gray.clone();
        var r =
                PrintedSlurContours.find(
                        f.gray, f.labels, f.w, f.h, 0, 100, f.w - 1, f.h - 1, f.track);
        assertFalse(r.limitReached());
        assertTrue(r.bows().isEmpty());
        assertArrayEquals(before, f.gray);
    }

    @Test
    public void sparseUnrelatedSideInkCannotEraseTheCap() throws Exception {
        var f = new Field(1, 0, 0);
        for (int xx = f.x + 1; xx <= 224; xx++) f.gray[f.y * f.w + xx] = (byte) 255;
        f.gray[f.y * f.w + f.x + 16] = 0;
        assertTrue(f.cap());
    }
}
