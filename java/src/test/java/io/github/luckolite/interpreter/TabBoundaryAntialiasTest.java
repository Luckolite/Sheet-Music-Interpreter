// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raster fixtures; no source score pixels, labels, or expected song data. */
public class TabBoundaryAntialiasTest {
    private static final int W = 640, H = 560;
    private static final float GAP = 26;

    private record Fixture(
            byte[] gray, List<TablatureDecoder.Staff> tabs, ScorePageInterpretation score) {}

    @Test
    public void paleAdjacentTipPixelsKeepAConnectedShortShoulder() {
        var f = fixture();
        incoming(f.gray(), false, 224, 234);
        byte[] before = f.gray().clone();
        var result = run(f);
        assertTrue(result.notes().get(1).tiedFromPrevious());
        assertFalse(result.notes().get(0).tiedFromPrevious());
        assertArrayEquals(before, f.gray());
        assertEquals(f.score().measures(), result.measures());
        assertEquals(f.score().notes().size(), result.notes().size());
    }

    @Test
    public void thinAntialiasedCoverageCanBeDistributedAcrossTwoPixels() {
        var f = fixture();
        incoming(f.gray(), true, 224, 234);
        assertTrue(run(f).notes().get(1).tiedFromPrevious());
    }

    @Test
    public void insufficientInkMassCannotExtendTheShortShoulder() {
        var f = fixture();
        incoming(f.gray(), false, 233, 235); // 22 + 20 = 42, below one supported column.
        assertFalse(run(f).notes().get(1).tiedFromPrevious());
    }

    @Test
    public void whiteColumnStillSeparatesAntialiasedFragments() {
        var f = fixture();
        incoming(f.gray(), true, 224, 234);
        for (int y = 390; y < 412; y++) f.gray()[y * W + 99] = (byte) 255;
        assertFalse(run(f).notes().get(1).tiedFromPrevious());
    }

    @Test
    public void thickGrayStraightBandCannotProveAnIncomingBow() {
        var f = fixture();
        for (int x = 94; x <= 106; x++)
            for (int y = 404; y <= 407; y++) f.gray()[y * W + x] = (byte) 230;
        assertFalse(run(f).notes().get(1).tiedFromPrevious());
    }

    @Test
    public void grayParenthesisCannotProveAnIncomingBow() {
        var f = fixture();
        for (int y = 394; y <= 408; y++) {
            int x = 105 + Math.round(2 * (float) Math.sin((y - 394) * Math.PI / 14));
            f.gray()[y * W + x] = (byte) 220;
            f.gray()[y * W + x + 1] = (byte) 234;
        }
        assertFalse(run(f).notes().get(1).tiedFromPrevious());
    }

    @Test
    public void neighboringStringAntialiasedBowCannotSupplyTheShoulder() {
        var f = fixture();
        incoming(f.gray(), true, 224, 234);
        for (int y = 390; y < 412; y++)
            for (int x = 94; x <= 106; x++) {
                f.gray()[(y - 26) * W + x] = f.gray()[y * W + x];
                f.gray()[y * W + x] = (byte) 255;
            }
        assertFalse(run(f).notes().get(1).tiedFromPrevious());
    }

    @Test
    public void explicitIncomingAttackWinsOverAntialiasedShoulder() {
        var f = fixture();
        incoming(f.gray(), true, 224, 234);
        for (int effect :
                new int[] {
                    TabEffect.encode(TabEffect.TAP, 0),
                    TabEffect.encode(TabEffect.HAMMER, -2),
                    TabEffect.encode(TabEffect.PULL, 2)
                }) {
            var n = f.score().notes().get(1).withArticulations(effect);
            var score =
                    new ScorePageInterpretation(
                            f.score().measures(), List.of(f.score().notes().get(0), n));
            var result = run(new Fixture(f.gray(), f.tabs(), score));
            assertFalse(result.notes().get(1).tiedFromPrevious());
            assertEquals(effect, result.notes().get(1).articulations());
        }
    }

    @Test
    public void paleTouchingGlyphDoesNotInvalidateAnIndependentDarkBow() {
        var f = fixture();
        darkIncomingBesidePaleGlyph(f.gray(), true);
        assertTrue(run(f).notes().get(1).tiedFromPrevious());
    }

    @Test
    public void paleTouchingGlyphAndFlatDarkInkSupplyNeitherProof() {
        var f = fixture();
        darkIncomingBesidePaleGlyph(f.gray(), false);
        assertFalse(run(f).notes().get(1).tiedFromPrevious());
    }

    private static void darkIncomingBesidePaleGlyph(byte[] gray, boolean bowed) {
        for (int x = 94; x <= 106; x++) {
            int y = 406 - (bowed ? Math.round(5 * (float) Math.sin(Math.PI * (x - 94) / 12)) : 0);
            gray[y * W + x] = 0;
            gray[(y + 1) * W + x] = 0;
        }
        // The pale glyph touches the curve at its tip only in the coverage
        // mask. Its steep run must not become a shoulder in either proof.
        for (int y = 389; y <= 408; y++) {
            gray[y * W + 107] = (byte) 224;
            gray[y * W + 108] = (byte) 234;
        }
    }

    private static ScorePageInterpretation run(Fixture f) {
        return TabBoundaryTies.apply(f.score(), f.tabs(), f.gray(), W, H);
    }

    private static Fixture fixture() {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int x = 452; x <= 555; x++) {
            int y = 112 - Math.round(5 + 13 * (float) Math.sin(Math.PI * (x - 452) / 103));
            gray[y * W + x] = 0;
            gray[(y + 1) * W + x] = 0;
        }
        var source =
                new TablatureDecoder.Staff(
                        60,
                        GAP,
                        -1,
                        List.of(new TablatureDecoder.Fret(440, 112, 2, 7)),
                        List.of(80f, 320f, 560f));
        var target =
                new TablatureDecoder.Staff(
                        360,
                        GAP,
                        -1,
                        List.of(new TablatureDecoder.Fret(124, 412, 2, 7)),
                        List.of(80f, 320f, 560f));
        var measures =
                List.of(
                        new MeasureRegion(320f / W, 560f / W, 40f / H, 220f / H),
                        new MeasureRegion(80f / W, 320f / W, 340f / H, 520f / H));
        var a =
                new ScoreNoteEvent(
                        0,
                        .5f,
                        -1,
                        0,
                        1,
                        112f / H,
                        false,
                        0,
                        0,
                        0,
                        4,
                        0,
                        0,
                        0,
                        ScoreNoteEvent.CLEF_TREBLE);
        var b =
                new ScoreNoteEvent(
                        1,
                        44f / 240,
                        -1,
                        0,
                        1,
                        412f / H,
                        false,
                        0,
                        0,
                        0,
                        4,
                        0,
                        0,
                        0,
                        ScoreNoteEvent.CLEF_TREBLE);
        return new Fixture(
                gray,
                List.of(source, target),
                new ScorePageInterpretation(measures, List.of(a, b)));
    }

    private static void incoming(byte[] gray, boolean paleCore, int first, int second) {
        for (int x = 95; x <= 103; x++) {
            int y = 412 - Math.round(6 + 5 * (float) Math.sin(Math.PI * (x - 95) / 8));
            if (x == 95 || x == 103) {
                gray[y * W + x] = (byte) first;
                gray[(y + 1) * W + x] = (byte) second;
            } else {
                int previous = 412 - Math.round(6 + 5 * (float) Math.sin(Math.PI * (x - 96) / 8));
                int next = 412 - Math.round(6 + 5 * (float) Math.sin(Math.PI * (x - 94) / 8));
                int bottom = Math.max(y + 1, Math.max(previous, next));
                for (int row = y; row <= bottom; row++)
                    gray[row * W + x] = (byte) (paleCore ? 230 : 0);
            }
        }
    }
}
