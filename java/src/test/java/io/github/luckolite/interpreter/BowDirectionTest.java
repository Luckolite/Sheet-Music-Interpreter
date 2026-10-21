// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.font.*;
import java.awt.image.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original renderings of bundled OFL Bravura bow glyphs, never score crop fixtures. */
public class BowDirectionTest {
    static final int W = 500, H = 360;
    byte[] g = new byte[W * H], l = new byte[W * H];

    public BowDirectionTest() {
        Arrays.fill(g, (byte) 255);
    }

    static java.io.File fontFile() {
        for (String p :
                new String[] {
                    "java/assets/Bravura.otf",
                    "app/src/main/assets/engraving/Bravura.otf",
                    "src/main/assets/engraving/Bravura.otf"
                }) if (Files.exists(Path.of(p))) return Path.of(p).toFile();
        throw new AssertionError("Missing licensed font");
    }

    void font(char glyph, float size, float phase) throws Exception {
        Font font = Font.createFont(Font.TRUETYPE_FONT, fontFile()).deriveFont(size);
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D c = image.createGraphics();
        c.setColor(Color.WHITE);
        c.fillRect(0, 0, W, H);
        c.setColor(Color.BLACK);
        c.setFont(font);
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        var vector = font.createGlyphVector(c.getFontRenderContext(), String.valueOf(glyph));
        var bounds = vector.getVisualBounds();
        c.drawGlyphVector(
                vector,
                (float) (250 - bounds.getCenterX()) + phase,
                (float) (125 - bounds.getMaxY()) + phase);
        c.dispose();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) g[y * W + x] = (byte) image.getRaster().getSample(x, y, 0);
    }

    ScoreNoteEvent note(float x, int staff) {
        return new ScoreNoteEvent(
                0,
                x / W,
                4,
                staff,
                1,
                180f / H,
                false,
                0,
                0,
                2,
                1,
                1,
                0,
                0,
                30,
                false,
                0,
                false,
                0,
                0,
                1,
                1,
                ScoreNoteEvent.Kind.PITCHED);
    }

    List<ScoreTechniqueChange> read(List<ScoreNoteEvent> notes, float gap) {
        return BowDirectionDetector.detect(
                l,
                g,
                W,
                H,
                List.of(new PlayingTechniqueDetector.Staff(160, 160 + 4 * gap, gap, 0, 1)),
                List.of(new MeasureRegion(0, 1, 0, 1)),
                notes);
    }

    @Test
    public void originalBravuraUpBowExactOnset() throws Exception {
        font('\uE612', 80, 0);
        var n = note(250, 0);
        var gc = g.clone();
        var r = read(List.of(n), 20);
        assertEquals(
                List.of(
                        new ScoreTechniqueChange(
                                0, n.positionInMeasure(), 0, 1, ScoreTechniqueChange.UP_BOW)),
                r);
        assertArrayEquals(gc, g);
    }

    @Test
    public void originalBravuraDownBowExactOnset() throws Exception {
        font('\uE610', 80, 0);
        var n = note(250, 0);
        assertEquals(
                List.of(
                        new ScoreTechniqueChange(
                                0, n.positionInMeasure(), 0, 1, ScoreTechniqueChange.DOWN_BOW)),
                read(List.of(n), 20));
    }

    @Test
    public void largerFractionalUpBowIsSameDirection() throws Exception {
        font('\uE612', 110, .35f);
        assertEquals(6, read(List.of(note(250, 0)), 24).get(0).technique());
    }

    @Test
    public void largerFractionalDownBowIsSameDirection() throws Exception {
        font('\uE610', 110, .35f);
        assertEquals(7, read(List.of(note(250, 0)), 24).get(0).technique());
    }

    @Test
    public void noNearbyOwnedAttackMeansNoBow() throws Exception {
        font('\uE612', 80, 0);
        assertTrue(read(List.of(note(340, 0)), 20).isEmpty());
    }

    @Test
    public void twoDistinctNearbyAttacksAreAmbiguous() throws Exception {
        font('\uE610', 80, 0);
        assertTrue(read(List.of(note(245, 0), note(255, 0)), 20).isEmpty());
    }

    @Test
    public void exactChordRetainsOneDirection() throws Exception {
        font('\uE612', 80, 0);
        var n = note(250, 0);
        assertEquals(1, read(List.of(n, n), 20).size());
    }

    @Test
    public void singleLegIsNotCompleteBow() {
        for (int y = 90; y <= 125; y++) for (int x = 240; x <= 243; x++) g[y * W + x] = 0;
        assertTrue(read(List.of(note(250, 0)), 20).isEmpty());
    }

    @Test
    public void closedRectangleIsNotDownBow() {
        for (int y = 90; y <= 125; y++)
            for (int x = 238; x <= 262; x++)
                if (y < 94 || y > 121 || x < 242 || x > 258) g[y * W + x] = 0;
        assertTrue(read(List.of(note(250, 0)), 20).isEmpty());
    }

    @Test
    public void connectedForeignBranchRemovesFiniteBow() throws Exception {
        font('\uE612', 80, 0);
        for (int x = 250; x < W; x++) g[124 * W + x] = 0;
        assertTrue(read(List.of(note(250, 0)), 20).isEmpty());
    }

    @Test
    public void enumLookupNeverInherits() {
        var n = note(250, 0);
        var next = note(300, 0);
        var c = List.of(new ScoreTechniqueChange(0, n.positionInMeasure(), 0, 1, 6));
        assertEquals(6, ScoreTechniqueChange.bowFor(n, c));
        assertEquals(-1, ScoreTechniqueChange.bowFor(next, c));
        assertFalse(ScoreTechniqueChange.isExpression(6));
        assertTrue(ScoreTechniqueChange.isExpression(3));
    }

    @Test
    public void contradictoryBowAnnotationsAbstain() {
        var n = note(250, 0);
        assertEquals(
                -1,
                ScoreTechniqueChange.bowFor(
                        n,
                        List.of(
                                new ScoreTechniqueChange(0, n.positionInMeasure(), 0, 1, 6),
                                new ScoreTechniqueChange(0, n.positionInMeasure(), 0, 1, 7))));
    }
}
