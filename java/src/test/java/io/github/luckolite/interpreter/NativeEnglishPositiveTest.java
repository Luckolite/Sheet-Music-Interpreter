// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeEnglishPositiveTest {
    static final int W = 500, H = 300;

    static List<PlayingTechniqueDetector.Staff> staffs() {
        return List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1));
    }

    static byte[] page(String glyph) throws Exception {
        byte[] g = LexicalContextTest.rails();
        // Original procedural letter bodies; no private source pixels.
        for (int x = 139; x < 239; x += 10)
            for (int y = 54; y < 72; y++) for (int dx = 0; dx < 3; dx++) g[y * W + x + dx] = 35;
        if (glyph != null) {
            var image = GlyphResources.image("dynamic_templates/" + glyph + ".png");
            int left = 130, top = 185, w = Math.round(image.getWidth() * 19f / image.getHeight());
            for (int y = 0; y < 19; y++)
                for (int x = 0; x < w; x++) {
                    int rgb = image.getRGB(x * image.getWidth() / w, y * image.getHeight() / 19);
                    g[(top + y) * W + left + x] = (byte) ((rgb >>> 16) & 255);
                }
        }
        return g;
    }

    static PlayingTechniqueDetector.Word candidate(String text) {
        int w = text.equals("mf") ? 27 : 39;
        return new PlayingTechniqueDetector.Word(
                text, 130f / W, 185f / H, (130 + w) / (float) W, 204f / H);
    }

    static OcrText text(String cue) {
        var line = new OcrText.Line("Flowing", new OcrText.Box(139, 54, 239, 72), List.of());
        var lines = new ArrayList<OcrText.Line>();
        lines.add(line);
        if (cue != null)
            lines.add(new OcrText.Line(cue, new OcrText.Box(130, 76, 280, 92), List.of()));
        return new OcrText(
                "Flowing",
                List.of(new OcrText.Block("", new OcrText.Box(130, 54, 280, 92), lines)),
                OcrText.Grouping.DETECTED_LINES);
    }

    static NativeDynamicGlyphs detector() throws Exception {
        return new NativeDynamicGlyphs();
    }

    static List<NativeDynamicGlyphs.Literal> literals(byte[] g, String value) throws Exception {
        return detector().verifiedLiterals(g, W, H, staffs(), List.of(candidate(value)));
    }

    static List<NativeEnglishDirectionEvidence.Evidence> collect(
            OcrText text, byte[] g, List<NativeDynamicGlyphs.Literal> ls) {
        var s = LexicalContextTest.score();
        return NativeEnglishDirectionEvidence.collect(
                text, 1, 0, ls, staffs(), s.measures(), s.notes(), g, W, H);
    }

    @Test
    public void actualLiteralTemplateAndDynamicDetectorQualifyRichContext() throws Exception {
        var g = page("mp");
        var ls = literals(g, "mp");
        assertEquals("verified literal", 1, ls.size());
        assertEquals("mp", ls.get(0).word().text());
        var ev = collect(text(null), g, ls);
        assertEquals("derived admission", 1, ev.size());
        var out =
                ScoreExpressionDetector.apply(
                        LexicalContextTest.score(), List.of(ev.get(0).word()), staffs(), g, W, H);
        assertEquals(1, out.expressiveEvents().size());
        assertEquals("Flowing", out.expressiveEvents().get(0).evidence().get(0).printedText());
        assertEquals(LexicalContextTest.score().notes(), out.notes());
    }

    @Test
    public void paperCannotValidateOcrMp() throws Exception {
        assertTrue(literals(page(null), "mp").isEmpty());
    }

    @Test
    public void realMfCannotValidateMislabeledMp() throws Exception {
        assertTrue(literals(page("mf"), "mp").isEmpty());
    }

    @Test
    public void sameColumnCreditOrVerseVetoSurvivesPhysicalMp() throws Exception {
        var g = page("mp");
        var ls = literals(g, "mp");
        assertEquals(1, ls.size());
        for (String cue :
                List.of(
                        "Music by Original Author",
                        "Copyright Publisher",
                        "Verse 1: these are sung words"))
            assertTrue(cue, collect(text(cue), g, ls).isEmpty());
    }

    @Test
    public void noLiteralCannotBeInventedFromDynamicScalar() throws Exception {
        assertTrue(collect(text(null), page("mp"), List.of()).isEmpty());
    }

    @Test
    public void ambiguousWrittenColumnsAbstain() throws Exception {
        var g = page("mp");
        var s = LexicalContextTest.score();
        var n = s.notes().get(0);
        var extra = new ScoreNoteEvent(0, .30f, 0, 0, 1, 164f / H, false, 0, 0, 0, 1, 1, 0);
        assertTrue(
                NativeEnglishDirectionEvidence.collect(
                                text(null),
                                1,
                                0,
                                literals(g, "mp"),
                                staffs(),
                                s.measures(),
                                List.of(n, extra),
                                g,
                                W,
                                H)
                        .isEmpty());
    }
}
