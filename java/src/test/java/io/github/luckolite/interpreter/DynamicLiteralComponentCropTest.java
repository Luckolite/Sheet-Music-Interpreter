// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;

/** Existing SIL OFL music-font templates placed in original notation scenes; no score pixels. */
public class DynamicLiteralComponentCropTest {
    static final int W = 220, H = 260;
    static final List<PlayingTechniqueDetector.Staff> STAFFS =
            List.of(new PlayingTechniqueDetector.Staff(20, 92, 18, 0, 1));

    static byte[] blank() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        return g;
    }

    static void rect(byte[] g, int l, int t, int r, int b) {
        for (int y = t; y < b; y++) for (int x = l; x < r; x++) g[y * W + x] = 0;
    }

    static void glyph(byte[] g, String text, int x, int y) throws Exception {
        var source = GlyphResources.image("dynamic_templates/" + text + ".png");
        BufferedImage im = new BufferedImage(48, 48, BufferedImage.TYPE_BYTE_GRAY);
        var draw = im.createGraphics();
        draw.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        draw.drawImage(source, 0, 0, 48, 48, null);
        draw.dispose();
        for (int yy = 0; yy < 48; yy++)
            for (int xx = 0; xx < 48; xx++)
                g[(y + yy) * W + x + xx] = (byte) ((im.getRGB(xx, yy) >>> 16) & 255);
    }

    static PlayingTechniqueDetector.Word word(String text, int l, int t, int r, int b) {
        return new PlayingTechniqueDetector.Word(
                text, l / (float) W, t / (float) H, r / (float) W, b / (float) H);
    }

    static boolean verified(NativeDynamicGlyphs glyphs, PlayingTechniqueDetector.Word word) {
        try {
            var method =
                    NativeDynamicGlyphs.class.getDeclaredMethod(
                            "match",
                            byte[].class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class);
            method.setAccessible(true);
            var match =
                    method.invoke(
                            glyphs,
                            ACTIVE,
                            W,
                            Math.round(word.left() * W),
                            Math.round(word.top() * H),
                            Math.round(word.right() * W),
                            Math.round(word.bottom() * H));
            var accepted = match.getClass().getDeclaredMethod("accepted");
            accepted.setAccessible(true);
            var text = match.getClass().getDeclaredMethod("text");
            text.setAccessible(true);
            return (boolean) accepted.invoke(match)
                    && text.invoke(match).equals(ScoreDynamicsDetector.glyphLevelText(word.text()));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static byte[] ACTIVE;

    static List<PlayingTechniqueDetector.Word> refine(
            byte[] g, List<PlayingTechniqueDetector.Word> words) throws Exception {
        ACTIVE = g;
        var glyphs = new NativeDynamicGlyphs();
        return DynamicOcrBounds.refineComponents(words, g, W, H, STAFFS, c -> verified(glyphs, c));
    }

    static byte[] neighbor() throws Exception {
        var g = blank();
        glyph(g, "ff", 65, 125);
        // Independent bow and accent after the compound: enough vertical overlap for the old greedy
        // union.
        rect(g, 119, 139, 122, 156);
        rect(g, 119, 139, 130, 142);
        rect(g, 127, 139, 130, 156);
        rect(g, 119, 184, 129, 187);
        return g;
    }

    @Test
    public void uniqueNaturalCompoundExcludesDetachedNotation() throws Exception {
        var g = neighbor();
        var input = word("ff", 58, 118, 133, 193);
        var result = refine(g, List.of(input));
        assertEquals(1, result.size());
        assertEquals("ff", result.get(0).text());
        assertTrue(result.get(0).bottom() < 180f / H);
        assertTrue(result.get(0).right() < 118f / W);
    }

    @Test
    public void ordinaryProducerKeepsCompleteFfInsteadOfContainedTinyF() throws Exception {
        var g = neighbor();
        var result =
                new NativeDynamicGlyphs()
                        .recognize(
                                g,
                                W,
                                H,
                                STAFFS,
                                List.of(
                                        word("ff", 58, 118, 133, 193),
                                        word("f", 80, 142, 85, 153)));
        assertTrue(result.stream().anyMatch(w -> w.text().equals("ff") && w.bottom() < 180f / H));
        assertFalse(
                result.stream()
                        .anyMatch(w -> w.text().equals("f") && w.left() > .3f && w.right() < .5f));
    }

    @Test
    public void SingleFDoesNotVerifyCompoundSpelling() throws Exception {
        var g = blank();
        glyph(g, "f", 65, 125);
        var words = List.of(word("ff", 58, 118, 133, 193));
        assertEquals(words, refine(g, words));
    }

    @Test
    public void twoCompleteCompoundCandidatesAreAmbiguous() throws Exception {
        var g = blank();
        glyph(g, "ff", 35, 125);
        glyph(g, "ff", 120, 125);
        var words = List.of(word("ff", 25, 118, 180, 185));
        assertEquals(words, refine(g, words));
    }

    @Test
    public void clippedConnectedBodyCannotRefineLiteral() {
        var g = blank();
        rect(g, 60, 120, 80, 165);
        rect(g, 68, 160, 72, 210);
        var words = List.of(word("ff", 50, 110, 90, 180));
        assertEquals(words, DynamicOcrBounds.refineComponents(words, g, W, H, STAFFS, c -> true));
    }

    @Test
    public void separatelyPrintedMembersDoNotBecomeCompound() throws Exception {
        var g = blank();
        glyph(g, "f", 35, 125);
        glyph(g, "f", 120, 125);
        var words = List.of(word("f", 30, 118, 90, 185), word("f", 110, 118, 180, 185));
        assertEquals(words, refine(g, words));
    }

    @Test
    public void callerRasterAndDirectionWordsArePreserved() throws Exception {
        var g = neighbor();
        var before = g.clone();
        var crescendo = word("cresc", 10, 100, 40, 130);
        var result = refine(g, List.of(crescendo, word("ff", 58, 118, 133, 193)));
        assertTrue(result.contains(crescendo));
        assertArrayEquals(before, g);
    }

    @Test
    public void oversizedWindowAbstainsBeforeMatcher() {
        byte[] g = new byte[400 * 400];
        Arrays.fill(g, (byte) 255);
        var words = List.of(new PlayingTechniqueDetector.Word("ff", 0, 0, 1, 1));
        int[] calls = {0};
        assertEquals(
                words,
                DynamicOcrBounds.refineComponents(
                        words,
                        g,
                        400,
                        400,
                        STAFFS,
                        c -> {
                            calls[0]++;
                            return true;
                        }));
        assertEquals(0, calls[0]);
    }
}
