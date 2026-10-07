// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Original staff images generated from the bundled SIL-OFL Bravura font. */
public class FadedQuarterGlyphTest {
    private static final int W = 280, H = 210;
    private static final float GAP = 14.25f;

    private static byte[] glyph(char code, int height, int pale, int fadedRows, double phase)
            throws Exception {
        Font font =
                Font.createFont(Font.TRUETYPE_FONT, new File("java/assets/Bravura.otf"))
                        .deriveFont(100f);
        var outline =
                font.createGlyphVector(new FontRenderContext(null, true, true), new char[] {code})
                        .getOutline();
        var bounds = outline.getBounds2D();
        double scale = height / bounds.getHeight();
        var transform = new AffineTransform();
        transform.translate(160 - bounds.getMinX() * scale + phase, 88 - bounds.getMinY() * scale);
        transform.scale(scale, scale);
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, W, H);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.BLACK);
        graphics.fill(transform.createTransformedShape(outline));
        graphics.dispose();
        byte[] gray = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int shade = image.getRaster().getSample(x, y, 0);
                int darkest = y < 88 + fadedRows ? pale : 80;
                gray[y * W + x] = (byte) Math.round(darkest + (250 - darkest) * shade / 255f);
            }
        rules(gray);
        return gray;
    }

    private static void rules(byte[] gray) {
        for (int line = 0; line < 5; line++) {
            int y = Math.round(80 + line * GAP);
            for (int x = 12; x < W - 12; x++) gray[y * W + x] = 100;
        }
    }

    private static List<ScoreRestEvent> detect(byte[] gray) {
        return SixteenthRestDetector.detect(
                gray,
                W,
                H,
                List.of(new MeasureRegion(.02f, .98f, .15f, .8f)),
                List.of(new SixteenthRestDetector.Staff(80, 80 + 4 * GAP, GAP, 0, 1)),
                List.of());
    }

    private static void quarters(int height) throws Exception {
        for (int pale : new int[] {185, 195, 202})
            for (int rows : new int[] {8, 12, 16})
                for (double phase : new double[] {0, .5}) {
                    var rests = detect(glyph('\ue4e5', height, pale, rows, phase));
                    String label = height + "/" + pale + "/" + rows + "/" + phase;
                    assertEquals(label, 1, rests.size());
                    assertEquals(label, 1, rests.get(0).durationBeats(), 0);
                    assertEquals(label, 0, rests.get(0).measureIndex());
                    assertEquals(label, 0, rests.get(0).staffIndex());
                }
    }

    @Test
    public void smallFadedQuartersRetainTheirDuration() throws Exception {
        quarters(38);
    }

    @Test
    public void mediumFadedQuartersRetainTheirDuration() throws Exception {
        quarters(40);
    }

    @Test
    public void largerFadedQuartersRetainTheirDuration() throws Exception {
        quarters(42);
    }

    @Test
    public void tallFadedQuartersRetainTheirDuration() throws Exception {
        quarters(44);
    }

    @Test
    public void tallerFadedQuartersRetainTheirCompleteContour() throws Exception {
        quarters(46);
    }

    @Test
    public void uniformlyFaintQuartersNeedDarkSeeds() throws Exception {
        for (int shade : new int[] {185, 195, 202})
            assertTrue(detect(glyph('\ue4e5', 40, shade, H, .5)).isEmpty());
    }

    @Test
    public void naturalGlyphsAreNotRests() throws Exception {
        assertTrue(detect(glyph('\ue261', 40, 195, 12, .5)).isEmpty());
    }

    @Test
    public void sharpGlyphsAreNotRests() throws Exception {
        assertTrue(detect(glyph('\ue262', 40, 195, 12, .5)).isEmpty());
    }

    @Test
    public void flatGlyphsAreNotRests() throws Exception {
        assertTrue(detect(glyph('\ue260', 40, 195, 12, .5)).isEmpty());
    }

    @Test
    public void rulesAloneAreNotRests() {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        rules(gray);
        assertTrue(detect(gray).isEmpty());
    }

    @Test
    public void straightStemIsNotAQuarterRest() {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        for (int y = 88; y < 132; y++)
            for (int x = 160; x < 163; x++) gray[y * W + x] = (byte) (y < 100 ? 195 : 80);
        rules(gray);
        assertTrue(detect(gray).isEmpty());
    }

    @Test
    public void clippedLowerHookIsNotAQuarterRest() throws Exception {
        byte[] gray = glyph('\ue4e5', 40, 195, 12, .5);
        for (int y = 114; y < 130; y++)
            for (int x = 154; x < 190; x++) gray[y * W + x] = (byte) 250;
        rules(gray);
        assertTrue(detect(gray).stream().noneMatch(r -> r.durationBeats() == 1));
    }

    @Test
    public void inputRasterIsImmutable() throws Exception {
        byte[] gray = glyph('\ue4e5', 40, 195, 12, .5), before = gray.clone();
        assertEquals(1, detect(gray).size());
        assertArrayEquals(before, gray);
    }
}
