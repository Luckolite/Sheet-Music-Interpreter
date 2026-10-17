// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original SIL-OFL Bravura rasterization and procedural beams; no source-score pixels. */
public class CurledTupletThreeTest {
    record Raster(byte[] gray, int left, int top, int width, int height) {}

    static Path fontFile() {
        for (var p :
                new Path[] {
                    Path.of("java/assets/Bravura.otf"),
                    Path.of("app/src/main/assets/engraving/Bravura.otf"),
                    Path.of("src/main/assets/engraving/Bravura.otf")
                }) if (Files.isRegularFile(p)) return p;
        throw new IllegalStateException("Missing licensed Bravura regression fixture");
    }

    static Raster glyph(int digit, int size, float phase, float shear, float narrow)
            throws Exception {
        var font = Font.createFont(Font.TRUETYPE_FONT, fontFile().toFile()).deriveFont(100f);
        var shape =
                font.createGlyphVector(
                                new FontRenderContext(null, true, true),
                                String.valueOf((char) (0xE080 + digit)))
                        .getOutline();
        shape = AffineTransform.getShearInstance(shear, 0).createTransformedShape(shape);
        var b = shape.getBounds2D();
        double sy = size / b.getHeight(), sx = sy * narrow;
        var t = new AffineTransform();
        t.translate(20 + phase - b.getX() * sx, 20 + phase - b.getY() * sy);
        t.scale(sx, sy);
        var im = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY);
        var g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(t.createTransformedShape(shape));
        g.dispose();
        byte[] gray = new byte[10000];
        int l = 100, r = -1, top = 100, bot = -1;
        for (int y = 0; y < 100; y++)
            for (int x = 0; x < 100; x++) {
                int v = im.getRaster().getSample(x, y, 0);
                gray[y * 100 + x] = (byte) v;
                if (v <= 165) {
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    top = Math.min(top, y);
                    bot = Math.max(bot, y);
                }
            }
        return new Raster(gray, l, top, r - l + 1, bot - top + 1);
    }

    static boolean match(Raster r) throws Exception {
        var m =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "looksLikeThree",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        var before = r.gray.clone();
        boolean result = (boolean) m.invoke(null, r.gray, 100, r.left, r.top, r.width, r.height);
        assertArrayEquals(before, r.gray);
        return result;
    }

    @Test
    public void curledTerminalKeepsBothBowlPocketsAtFractionalBoundary() throws Exception {
        assertTrue(match(glyph(3, 15, .5f, .15f, .8f)));
    }

    @Test
    public void neighboringScaleAndPhaseRetainTheSameGlyph() throws Exception {
        assertTrue(match(glyph(3, 16, 0, .15f, .8f)));
    }

    @Test
    public void closedEightsRemainRejected() throws Exception {
        negatives(8);
    }

    @Test
    public void flatFootTwosRemainRejected() throws Exception {
        negatives(2);
    }

    @Test
    public void solidUpperFiveRemainsRejected() throws Exception {
        negatives(5);
    }

    private void negatives(int digit) throws Exception {
        for (int size = 14; size <= 30; size++)
            for (float phase : new float[] {0, .25f, .5f, .75f})
                for (float shear : new float[] {0, -.15f, .15f})
                    for (float width : new float[] {.8f, 1, 1.2f})
                        assertFalse(
                                "digit" + digit + " size" + size + " phase" + phase,
                                match(glyph(digit, size, phase, shear, width)));
    }

    private List<ScoreNoteEvent> group(int digit, boolean rail) throws Exception {
        int w = 400, h = 280;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int y : new int[] {110, 124, 138, 152, 166})
            for (int x = 20; x < 380; x++) gray[y * w + x] = 0;
        for (int x = 108; x <= 158; x++) for (int y = 105; y <= 108; y++) gray[y * w + x] = 0;
        if (rail)
            for (int x = 108; x <= 158; x++) for (int y = 96; y <= 99; y++) gray[y * w + x] = 0;
        var notes = new java.util.ArrayList<ScoreNoteEvent>();
        for (int x : new int[] {100, 125, 150}) {
            for (int y = 96; y <= 152; y++) gray[y * w + x + 8] = 0;
            for (int yy = -5; yy <= 5; yy++)
                for (int xx = -8; xx <= 8; xx++)
                    if (xx * xx / 64. + yy * yy / 25. <= 1) gray[(152 + yy) * w + x + xx] = 0;
            notes.add(
                    new ScoreNoteEvent(0, x / (float) w, 2, 0, 1, 152f / h, false, 0, 2, 2, 0)
                            .withStemDirection(1));
        }
        if (digit >= 0) {
            var r = glyph(digit, 15, .5f, .15f, .8f);
            for (int y = 0; y < r.height; y++)
                for (int x = 0; x < r.width; x++)
                    gray[(70 + y) * w + 128 + x] = r.gray[(r.top + y) * 100 + r.left + x];
        }
        var before = gray.clone();
        var out =
                TripletRhythmDetector.apply(
                        notes, List.of(new MeasureRegion(0, 1, 70f / h, 182f / h)), gray, w, h);
        assertArrayEquals(before, gray);
        return out;
    }

    @Test
    public void originalCompleteBeamGroupAcquiresPrintedRatio() throws Exception {
        for (var n : group(3, true)) {
            assertEquals(3, n.tupletDivisor());
            assertEquals(2, n.tupletNormalNotes());
        }
    }

    @Test
    public void absentNumeralDoesNotInventRatio() throws Exception {
        for (var n : group(-1, true)) assertEquals(1, n.tupletDivisor());
    }

    @Test
    public void closedEightCannotMarkOtherwiseCompleteBeamGroup() throws Exception {
        for (var n : group(8, true)) assertEquals(1, n.tupletDivisor());
    }
}
