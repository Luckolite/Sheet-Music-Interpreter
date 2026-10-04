// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import org.junit.Test;

/** Original generated clefs and confusing glyph controls; no score extracts. */
public class PrintedCClefTest {
    static final int W = 240, H = 240;

    private record Raster(
            byte[] gray, int left, int right, int top, int bottom, float staffBottom) {}

    private Raster glyph(char code, float gap, int cLine, float phase, boolean rules)
            throws Exception {
        Font font =
                Font.createFont(Font.TRUETYPE_FONT, new File("java/assets/Bravura.otf"))
                        .deriveFont(100f);
        var shape =
                font.createGlyphVector(new FontRenderContext(null, true, true), new char[] {code})
                        .getOutline();
        var box = shape.getBounds2D();
        double scale = 4 * gap / box.getHeight();
        float x = 45 + phase, center = 110 + phase, top = center - 2 * gap;
        var tx = new AffineTransform();
        tx.translate(x - box.getMinX() * scale, top - box.getMinY() * scale);
        tx.scale(scale, scale);
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fill(tx.createTransformedShape(shape));
        float bottom = center + cLine * gap;
        if (rules)
            for (int line = 0; line < 5; line++)
                g.drawLine(
                        12,
                        Math.round(bottom - line * gap),
                        W - 12,
                        Math.round(bottom - line * gap));
        g.dispose();
        byte[] gray = new byte[W * H];
        for (int yy = 0; yy < H; yy++)
            for (int xx = 0; xx < W; xx++)
                gray[yy * W + xx] = (byte) image.getRaster().getSample(xx, yy, 0);
        return new Raster(
                gray,
                Math.round(x),
                Math.round(x + (float) (box.getWidth() * scale)),
                Math.round(top),
                Math.round(top + 4 * gap),
                bottom);
    }

    private int detect(Raster r, float gap) {
        return PrintedCClef.detect(
                r.gray, W, H, r.left, r.right, r.top, r.bottom, r.staffBottom, gap);
    }

    @Test
    public void altoAndTenorFollowThePrintedCLine() throws Exception {
        for (float gap : new float[] {8, 12, 17, 23})
            for (float phase : new float[] {0, .5f})
                for (boolean rules : new boolean[] {false, true}) {
                    assertEquals(
                            "alto " + gap + "/" + phase + "/" + rules,
                            ScoreNoteEvent.CLEF_ALTO,
                            detect(glyph('\ue05c', gap, 2, phase, rules), gap));
                    assertEquals(
                            "tenor " + gap + "/" + phase + "/" + rules,
                            ScoreNoteEvent.CLEF_TENOR,
                            detect(glyph('\ue05c', gap, 3, phase, rules), gap));
                }
    }

    @Test
    public void rejectsAccidentalsGClefsFClefsAndRests() throws Exception {
        for (char code : new char[] {'\ue050', '\ue062', '\ue260', '\ue261', '\ue262', '\ue4e5'})
            for (float gap : new float[] {8, 17, 23})
                assertEquals(
                        Integer.toHexString(code),
                        ScoreNoteEvent.CLEF_UNKNOWN,
                        detect(glyph(code, gap, 2, 0, true), gap));
    }

    @Test
    public void cClefSemanticsSurviveCopies() {
        var note = new ScoreNoteEvent(0, .25f, 4, 2, 4, .5f, false);
        assertEquals(28, note.withClef(ScoreNoteEvent.CLEF_ALTO).diatonicPitchIdentity());
        assertEquals(
                28,
                new ScoreNoteEvent(0, .25f, 6, 2, 4, .5f, false)
                        .withClef(ScoreNoteEvent.CLEF_TENOR)
                        .diatonicPitchIdentity());
        assertEquals(ScoreNoteEvent.CLEF_UNKNOWN, note.withClef(23).clefBottomDiatonic());
    }
}
