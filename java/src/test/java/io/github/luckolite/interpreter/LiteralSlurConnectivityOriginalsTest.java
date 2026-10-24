// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently drawn complete arcs and separated chevrons retain their actual topology. */
public class LiteralSlurConnectivityOriginalsTest {
    static final class Field {
        final int w, h, s;
        final float gap, phase;
        final boolean above, leftMark;
        final byte[] gray, labels;
        final StaffPitchTrack track;
        final List<PrintedSlurOwners.GeometryHead> heads;

        Field(boolean above, boolean leftMark, int scale, float phase, boolean mark) {
            this.above = above;
            this.leftMark = leftMark;
            s = scale;
            this.phase = phase;
            gap = 16 * s;
            w = 320 * s;
            h = 260 * s;
            gray = new byte[w * h];
            labels = new byte[gray.length];
            Arrays.fill(gray, (byte) 255);
            for (int x = 60 * s; x <= 180 * s; x++) {
                double t = (x - 60. * s) / (120 * s), y = 120 - 24 * 4 * t * (1 - t);
                stroke(x / (double) s, y, 30);
            }
            if (mark) mark(103, 65);
            float hy = (float) ((above ? 132 : 108) * s + phase);
            heads =
                    List.of(
                            new PrintedSlurOwners.GeometryHead(2, 55 * s + phase, hy, 0, 1),
                            new PrintedSlurOwners.GeometryHead(6, 185 * s + phase, hy, 0, 1));
            for (var n : heads)
                for (int y = Math.round(n.y() - 5 * s); y <= n.y() + 5 * s; y++)
                    for (int x = Math.round(n.x() - 8 * s); x <= n.x() + 8 * s; x++)
                        if (Math.pow((x - n.x()) / (7. * s), 2)
                                        + Math.pow((y - n.y()) / (4. * s), 2)
                                <= 1) gray[y * w + x] = 0;
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, 220 * s, gap},
                                    new float[] {w - 1, 220 * s, gap}));
        }

        int px(double x) {
            return (int) Math.round((leftMark ? 240 - x : x) * s);
        }

        int py(double y) {
            return (int) Math.round((above ? y : 240 - y) * s + phase);
        }

        void stroke(double x, double y, int color) {
            int xx = px(x), yy = py(y);
            for (int dy = -s; dy <= s; dy++)
                if (xx >= 0 && xx < w && yy + dy >= 0 && yy + dy < h)
                    gray[(yy + dy) * w + xx] = (byte) color;
        }

        void mark(double lower, int color) {
            for (int x = 164 * s; x <= 188 * s; x++) {
                double xx = x / (double) s, t = (xx - 164) / 24;
                stroke(xx, lower - 16 + 8 * t, color);
                stroke(xx, lower - 8 * t, color);
            }
        }

        PrintedSlurContours.RawResult find() {
            byte[] before = gray.clone(), originalLabels = labels.clone();
            var hint = PrintedSlurOwners.headEvidence(heads, gray, w, h, track);
            var result =
                    PrintedSlurContours.findWithRawEvidence(
                            gray, labels, w, h, 0, 30 * s, w - 1, 190 * s, track, null, hint);
            assertArrayEquals(before, gray);
            assertArrayEquals(originalLabels, labels);
            assertFalse(result.result().limitReached());
            return result;
        }

        List<PrintedSlurContours.Bow> target(PrintedSlurContours.RawResult result) {
            var out = new ArrayList<PrintedSlurContours.Bow>();
            for (var b : result.result().bows())
                if (Math.abs(b.start().x() - 60 * s) <= s
                        && Math.abs(b.end().x() - 180 * s) <= s
                        && b.above() == above) out.add(b);
            return out;
        }

        void recovered() {
            var r = find();
            assertEquals(1, target(r).size());
            var bow = target(r).get(0);
            var bound =
                    PrintedSlurOwners.bind(
                            "original-separated-curve", 1, bow, heads, gray, w, h, track);
            assertTrue(bound.span().isPresent());
            assertEquals(2, bound.span().orElseThrow().start().noteIndex());
            assertEquals(6, bound.span().orElseThrow().end().noteIndex());
        }
    }

    @Test
    public void upperCurveSurvivesSeparatedTerminalChevron() {
        new Field(true, false, 1, 0, true).recovered();
    }

    @Test
    public void lowerCurveSurvivesSeparatedTerminalChevron() {
        new Field(false, false, 1, 0, true).recovered();
    }

    @Test
    public void leftAttachmentUsesTheSamePhysicalTopology() {
        new Field(true, true, 1, 0, true).recovered();
    }

    @Test
    public void lowerLeftAttachmentUsesTheSamePhysicalTopology() {
        new Field(false, true, 1, 0, true).recovered();
    }

    @Test
    public void doubleDensityKeepsBothLiteralComponents() {
        new Field(true, false, 2, 0, true).recovered();
    }

    @Test
    public void tripleDensityKeepsBothLiteralComponents() {
        new Field(false, true, 3, 0, true).recovered();
    }

    @Test
    public void quarterPixelHeadAndStrokePhaseKeepsOwnership() {
        new Field(true, false, 1, .25f, true).recovered();
    }

    @Test
    public void halfPixelHeadAndStrokePhaseKeepsOwnership() {
        new Field(false, false, 1, .5f, true).recovered();
    }

    @Test
    public void plainUpperLegacyBowIsNotDuplicated() {
        new Field(true, false, 1, 0, false).recovered();
    }

    @Test
    public void plainLowerLegacyBowIsNotDuplicated() {
        new Field(false, false, 1, 0, false).recovered();
    }

    @Test
    public void blackSeparatedMarkCannotStealTheBow() {
        var f = new Field(true, false, 1, 0, false);
        f.mark(103, 0);
        f.recovered();
    }

    @Test
    public void gray150SeparatedMarkCannotStealTheBow() {
        var f = new Field(true, false, 1, 0, false);
        f.mark(103, 150);
        f.recovered();
    }

    @Test
    public void faint206MarkLeavesTheLegacyCurveAlone() {
        var f = new Field(true, false, 1, 0, false);
        f.mark(103, 206);
        f.recovered();
    }

    @Test
    public void realContactDoesNotBecomeASeparateCurve() {
        var f = new Field(true, false, 1, 0, false);
        f.mark(109, 65);
        assertTrue(f.target(f.find()).isEmpty());
    }

    @Test
    public void existingCentralForkRecoveryIsNotDuplicated() {
        var f = new Field(true, false, 1, 0, false);
        for (int y = 70; y <= 96; y++) f.gray[y * f.w + 120] = 0;
        assertEquals(1, f.target(f.find()).size());
    }

    @Test
    public void curvedShapeIsStillRequired() {
        var f = new Field(true, false, 1, 0, false);
        Arrays.fill(f.gray, (byte) 255);
        for (int x = 60; x <= 180; x++) f.stroke(x, 120, 30);
        f.mark(103, 65);
        assertTrue(f.target(f.find()).isEmpty());
    }

    @Test
    public void aSingleMissingRasterColumnKeepsTheLegacyRecovery() {
        var f = new Field(true, false, 1, 0, false);
        for (int y = 30; y <= 190; y++) f.gray[y * f.w + 120] = (byte) 255;
        f.recovered();
    }

    @Test
    public void semanticNoteheadMaskKeepsItsAuthority() {
        var f = new Field(true, false, 1, 0, true);
        for (int y = 80; y <= 122; y++)
            for (int x = 60; x <= 180; x++)
                f.labels[y * f.w + x] = OmrMeasurePostProcessor.NOTEHEAD;
        assertTrue(f.target(f.find()).isEmpty());
    }

    @Test
    public void selectedEvidenceKeepsOriginalRawIntensities() {
        var f = new Field(true, false, 1, 0, true);
        var r = f.find();
        var b = f.target(r).get(0);
        for (var c : r.selectedContourWitnesses().get(b))
            for (int i = 0; i < c.rawProfile().size(); i++)
                assertEquals(
                        f.gray[(c.first() + i) * f.w + c.x()] & 255, (int) c.rawProfile().get(i));
    }

    @Test
    public void separatedMarkPixelsAreNotPartOfTheRecoveredCurve() {
        var f = new Field(true, false, 1, 0, true);
        var r = f.find();
        var b = f.target(r).get(0);
        for (var c : r.witnesses().get(b))
            for (var band : c.members())
                for (int y = band.first(); y <= band.last(); y++)
                    assertNotEquals(65, f.gray[y * f.w + c.x()] & 255);
    }

    @Test
    public void headListPermutationKeepsTheSameGeometricOwners() {
        var f = new Field(true, false, 1, 0, true);
        var r = f.find();
        var b = f.target(r).get(0);
        var reversed = new ArrayList<>(f.heads);
        Collections.reverse(reversed);
        var bound =
                PrintedSlurOwners.bind(
                        "original-reversed-heads", 1, b, reversed, f.gray, f.w, f.h, f.track);
        assertEquals(2, bound.span().orElseThrow().start().noteIndex());
        assertEquals(6, bound.span().orElseThrow().end().noteIndex());
    }

    @Test
    public void foreignNearbyCurveDoesNotDuplicateExistingEvidence() {
        var f = new Field(true, false, 1, 0, false);
        for (int x = 65; x <= 175; x++) {
            double t = (x - 65) / 110.;
            f.stroke(x, 75 - 12 * 4 * t * (1 - t), 80);
        }
        assertEquals(1, f.target(f.find()).size());
    }

    @Test
    public void separatedThickBandsCannotCreateAnImaginaryEnvelopeContact() {
        var f = new Field(true, false, 1, 0, true);
        for (int x = 95; x <= 105; x++) {
            for (int y = 42; y <= 70; y++) f.gray[y * f.w + x] = 0;
            for (int y = 130; y <= 160; y++) f.gray[y * f.w + x] = 0;
        }
        f.recovered();
    }

    @Test
    public void discardedThickTerminalContactCannotCreateANewCap() {
        var f = new Field(true, false, 1, 0, false);
        for (int x = 172; x <= 176; x++) for (int y = 70; y <= 118; y++) f.gray[y * f.w + x] = 0;
        assertTrue(f.target(f.find()).isEmpty());
    }
}
