// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent returning arcs and full head bodies prove the actual outward cap contact. */
public class OutwardHeadRimSlurOriginalsTest {
    static final class Field {
        final int w, h, scale;
        final float gap, phase;
        final boolean above, first;
        final byte[] gray;
        final StaffPitchTrack track;
        final List<PrintedSlurOwners.GeometryHead> heads;
        final PrintedSlurContours.Bow bow;

        Field(boolean above, boolean first, int scale, float phase) {
            this.above = above;
            this.first = first;
            this.scale = scale;
            this.phase = phase;
            w = 420 * scale;
            h = 360 * scale;
            gap = 16 * scale;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            heads =
                    List.of(
                            new PrintedSlurOwners.GeometryHead(
                                    2, (110 + phase) * scale, (190 + phase) * scale, 0, 1),
                            new PrintedSlurOwners.GeometryHead(
                                    6, (310 + phase) * scale, (190 + phase) * scale, 0, 1));
            for (var head : heads) draw(head);
            int left = (first ? 106 : 114) * scale, right = (first ? 306 : 314) * scale;
            var points = new ArrayList<PrintedSlurContours.Point>();
            for (int x = left; x <= right; x++) {
                double t = (x - left) / (double) (right - left);
                float y =
                        (float)
                                (((above ? 181 - 35 * 4 * t * (1 - t) : 199 + 35 * 4 * t * (1 - t))
                                                + phase)
                                        * scale);
                points.add(new PrintedSlurContours.Point(x, y));
                gray[Math.round(y) * w + x] = 30;
            }
            bow = new PrintedSlurContours.Bow(points, above, 2, 0);
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, 330 * scale, gap},
                                    new float[] {w - 1, 330 * scale, gap}));
        }

        void draw(PrintedSlurOwners.GeometryHead head) {
            for (int y = Math.round(head.y() - 10 * scale); y <= head.y() + 10 * scale; y++)
                for (int x = Math.round(head.x() - 13 * scale); x <= head.x() + 13 * scale; x++)
                    if (Math.pow((x - head.x()) / (12. * scale), 2)
                                    + Math.pow((y - head.y()) / (9. * scale), 2)
                            <= 1) gray[y * w + x] = 0;
        }

        PrintedSlurContours.Point cap() {
            return first ? bow.start() : bow.end();
        }

        int rayX() {
            return cap().x() + (first ? -scale : scale);
        }

        int rayY() {
            return Math.round(cap().y() + (above ? 2 : -2) * scale);
        }

        PrintedSlurContours.HeadEvidence hints() {
            return PrintedSlurOwners.headEvidence(heads, gray, w, h, track);
        }

        boolean closes() {
            byte[] before = gray.clone();
            boolean r = hints().closes(cap(), rayX(), rayY(), first);
            assertArrayEquals(before, gray);
            return r;
        }

        void eraseHead(int index) {
            var head = heads.get(index);
            for (int y = Math.round(head.y() - 11 * scale); y <= head.y() + 11 * scale; y++)
                for (int x = Math.round(head.x() - 14 * scale); x <= head.x() + 14 * scale; x++)
                    gray[y * w + x] = (byte) 255;
        }
    }

    @Test
    public void upperStartCanTouchOutwardHeadRim() {
        assertTrue(new Field(true, true, 1, 0).closes());
    }

    @Test
    public void upperEndCanTouchOutwardHeadRim() {
        assertTrue(new Field(true, false, 1, 0).closes());
    }

    @Test
    public void lowerStartCanTouchOutwardHeadRim() {
        assertTrue(new Field(false, true, 1, 0).closes());
    }

    @Test
    public void lowerEndCanTouchOutwardHeadRim() {
        assertTrue(new Field(false, false, 1, 0).closes());
    }

    @Test
    public void doubleDensityPreservesLiteralContact() {
        assertTrue(new Field(true, true, 2, 0).closes());
    }

    @Test
    public void tripleDensityPreservesLiteralContact() {
        assertTrue(new Field(false, false, 3, 0).closes());
    }

    @Test
    public void quarterPixelHeadCentroidPreservesContact() {
        assertTrue(new Field(true, true, 1, .25f).closes());
    }

    @Test
    public void halfPixelHeadCentroidPreservesContact() {
        assertTrue(new Field(false, false, 1, .5f).closes());
    }

    @Test
    public void forwardPixelCannotCloseAStartingTip() {
        var f = new Field(true, true, 1, 0);
        assertFalse(f.hints().closes(f.cap(), f.cap().x() + 1, f.rayY(), true));
    }

    @Test
    public void backwardPixelCannotCloseAnEndingTip() {
        var f = new Field(true, false, 1, 0);
        assertFalse(f.hints().closes(f.cap(), f.cap().x() - 1, f.rayY(), false));
    }

    @Test
    public void whiteRayPixelCannotBorrowAdjacentInk() {
        var f = new Field(true, true, 1, 0);
        f.gray[f.rayY() * f.w + f.rayX()] = (byte) 255;
        assertFalse(f.closes());
    }

    @Test
    public void gray165CannotSupplyStrictContact() {
        var f = new Field(true, true, 1, 0);
        f.gray[f.rayY() * f.w + f.rayX()] = (byte) 165;
        assertFalse(f.closes());
    }

    @Test
    public void gray205FringeCannotSupplyStrictContact() {
        var f = new Field(true, true, 1, 0);
        f.gray[f.rayY() * f.w + f.rayX()] = (byte) 205;
        assertFalse(f.closes());
    }

    @Test
    public void gray164IsObservedStrictInk() {
        var f = new Field(true, true, 1, 0);
        f.gray[f.rayY() * f.w + f.rayX()] = (byte) 164;
        assertTrue(f.closes());
    }

    @Test
    public void absentRawBodyCannotBeReplacedByMetadata() {
        var f = new Field(true, true, 1, 0);
        f.eraseHead(0);
        f.gray[f.rayY() * f.w + f.rayX()] = 0;
        assertFalse(f.closes());
    }

    @Test
    public void loneStaffRowCannotProveHeadBody() {
        var f = new Field(true, true, 1, 0);
        f.eraseHead(0);
        for (int x = 96; x <= 124; x++) f.gray[f.rayY() * f.w + x] = 0;
        assertFalse(f.closes());
    }

    @Test
    public void pixelOutsideHeadEllipseCannotBorrowItsAdjacency() {
        var f = new Field(true, true, 1, 0);
        int x = 99, y = 182;
        f.gray[y * f.w + x] = 0;
        assertFalse(f.hints().closes(f.cap(), x, y, true));
    }

    @Test
    public void distantCapStillRefuses() {
        var f = new Field(true, true, 1, 0);
        assertFalse(
                f.hints().closes(new PrintedSlurContours.Point(92, 181), f.rayX(), f.rayY(), true));
    }

    @Test
    public void existingCorrectSideBodyContactKeepsPrecedence() {
        var f = new Field(true, true, 1, 0);
        assertTrue(f.hints().closes(new PrintedSlurContours.Point(114, 181), 109, 185, true));
    }

    @Test
    public void existingEndingContactStillUsesItsLegacyBodyProof() {
        var f = new Field(true, true, 1, 0);
        assertTrue(f.hints().closes(f.cap(), f.rayX(), f.rayY(), false));
    }

    @Test
    public void fullRawUpperArcAcquiresAndBindsBothHeads() {
        fullRaw(true);
    }

    @Test
    public void fullRawLowerArcAcquiresAndBindsBothHeads() {
        fullRaw(false);
    }

    static void fullRaw(boolean above) {
        var f = new Field(above, true, 1, 0);
        var before = f.gray.clone();
        var result =
                PrintedSlurContours.find(
                        f.gray, null, f.w, f.h, 0, 80, f.w - 1, 270, f.track, null, f.hints());
        boolean found = false;
        for (var bow : result.bows()) {
            var binding =
                    PrintedSlurOwners.bind(
                            "original-full-rim", 0, bow, f.heads, f.gray, f.w, f.h, f.track);
            if (binding.span().isPresent()) {
                var span = binding.span().orElseThrow();
                if (span.start().noteIndex() == 2 && span.end().noteIndex() == 6) found = true;
            }
        }
        assertTrue(found);
        assertFalse(result.limitReached());
        assertArrayEquals(before, f.gray);
    }
}
