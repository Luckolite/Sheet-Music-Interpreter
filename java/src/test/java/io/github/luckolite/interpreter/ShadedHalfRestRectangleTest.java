// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original shaded paper, five printed rules, complete rectangles and owner controls. */
public class ShadedHalfRestRectangleTest {
    private static final int W = 640, H = 260, GAP = 16, LEFT = 235, RIGHT = 254;

    private static final class Page {
        final byte[] gray = new byte[W * H];
        final float slope;
        final StaffPitchTrack track;

        Page(float slope) {
            this.slope = slope;
            track = slope == 0 ? null : StaffPitchTrack.linear(W, 144, GAP, slope);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    gray[y * W + x] = (byte) (130 + x * 10 / W + (x * 17 + y * 29) % 4);
            for (int line = 0; line < 5; line++)
                box(20, 80 + line * GAP, W - 21, 80 + line * GAP, 80);
        }

        int y(int x, int flat) {
            return Math.round(flat + slope * (x - W * .5f));
        }

        void box(int l, int t, int r, int b, int ink) {
            for (int flat = t; flat <= b; flat++)
                for (int x = l; x <= r; x++) gray[y(x, flat) * W + x] = (byte) ink;
        }

        void erase(int l, int t, int r, int b) {
            for (int flat = t; flat <= b; flat++)
                for (int x = l; x <= r; x++) {
                    int y = y(x, flat);
                    gray[y * W + x] = (byte) (130 + x * 10 / W + (x * 17 + y * 29) % 4);
                }
        }

        void half() {
            box(LEFT, 104, RIGHT, 111, 80);
        }

        List<ScoreRestEvent> read(List<ScoreNoteEvent> notes) {
            return SixteenthRestDetector.detect(
                    gray,
                    W,
                    H,
                    List.of(new MeasureRegion(0, 1, .1f, .9f)),
                    List.of(new SixteenthRestDetector.Staff(80, 144, GAP, 0, 1, track)),
                    notes);
        }

        void assertHalf() {
            var r = read(List.of());
            assertEquals(r.toString(), 1, r.size());
            assertEquals(2, r.get(0).durationBeats(), 0);
        }
    }

    @Test
    public void shadedFullSittingRectangleIsTwoBeats() {
        var p = new Page(0);
        p.half();
        p.assertHalf();
    }

    @Test
    public void singleBroadBlurFringeCannotEraseTheFullBody() {
        var p = new Page(0);
        p.half();
        p.box(LEFT + 3, 103, RIGHT - 3, 103, 80);
        p.assertHalf();
    }

    @Test
    public void positiveTiltUsesTheEstablishedPrintedTrack() {
        var p = new Page(.055f);
        p.half();
        p.assertHalf();
    }

    @Test
    public void negativeTiltUsesTheEstablishedPrintedTrack() {
        var p = new Page(-.055f);
        p.half();
        p.assertHalf();
    }

    @Test
    public void ovalHeadHasTooFewFlatWideRows() {
        var p = new Page(0);
        for (int y = 103; y <= 111; y++)
            for (int x = LEFT; x <= RIGHT; x++)
                if (Math.pow((x - 244.5) / 9.5, 2) + Math.pow((y - 107) / 4., 2) <= 1)
                    p.box(x, y, x, y, 80);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void thinTenutoCannotProvideRectangleHeight() {
        var p = new Page(0);
        p.box(LEFT, 110, RIGHT, 111, 80);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void incompleteBodySeparatedFromItsRuleCannotAddSilence() {
        var p = new Page(0);
        p.box(LEFT, 104, RIGHT, 107, 80);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void hangingRectangleCannotBecomeAHalfRest() {
        var p = new Page(0);
        p.box(LEFT, 97, RIGHT, 104, 80);
        assertTrue(p.read(List.of()).stream().noneMatch(r -> r.durationBeats() == 2));
    }

    @Test
    public void connectedStemRetainsItsOwnedInk() {
        var p = new Page(0);
        p.half();
        p.box(RIGHT - 1, 92, RIGHT, 151, 80);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void detachedLowerInkCannotBeCroppedIntoTheRectangle() {
        var p = new Page(0);
        p.half();
        p.box(LEFT + 5, 132, LEFT + 7, 136, 80);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void missingOuterPrintedRuleCannotValidateTheRectangle() {
        var p = new Page(0);
        p.half();
        p.erase(LEFT - 40, 144, RIGHT + 40, 144);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void shortMiddleUnderlineCannotReplaceTheFiveRuleNeighborhood() {
        var p = new Page(0);
        p.half();
        p.erase(LEFT - 40, 112, LEFT - 3, 112);
        p.erase(RIGHT + 3, 112, RIGHT + 40, 112);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void sparseNoteOwnerStillVetoesTheWholeColumn() {
        var p = new Page(0);
        p.half();
        var owner = new ScoreNoteEvent(0, 244.5f / W, 3, 0, 1, 108f / H, false, 0, 1);
        assertTrue(p.read(List.of(owner)).isEmpty());
    }

    @Test
    public void negativeTiltSparseOwnerStillVetoesTheWholeColumn() {
        var p = new Page(-.055f);
        p.half();
        var owner =
                new ScoreNoteEvent(0, 244.5f / W, 3, 0, 1, p.y(245, 108) / (float) H, false, 0, 1);
        assertTrue(p.read(List.of(owner)).isEmpty());
    }

    @Test
    public void bodyWithoutRuleContactCannotSupplySilence() {
        var p = new Page(0);
        p.box(LEFT, 101, RIGHT, 108, 80);
        assertTrue(p.read(List.of()).isEmpty());
    }

    @Test
    public void paperTextureDoesNotSupplyAnyRectangle() {
        assertTrue(new Page(0).read(List.of()).isEmpty());
    }

    @Test
    public void sourcePixelsRemainUnchanged() {
        var p = new Page(.055f);
        p.half();
        var before = p.gray.clone();
        p.read(List.of());
        assertArrayEquals(before, p.gray);
    }

    @Test
    public void shadedSittingRectangleRetainsItsWrittenDot() {
        var p = new Page(0);
        p.half();
        p.box(267, 102, 271, 106, 80);
        var r = p.read(List.of());
        assertEquals(r.toString(), 1, r.size());
        assertEquals(3, r.get(0).durationBeats(), 0);
    }

    @Test
    public void shadedSittingRectangleRetainsBothWrittenDots() {
        var p = new Page(0);
        p.half();
        p.box(264, 102, 268, 106, 80);
        p.box(277, 102, 281, 106, 80);
        var r = p.read(List.of());
        assertEquals(r.toString(), 1, r.size());
        assertEquals(3.5, r.get(0).durationBeats(), 0);
    }
}
