// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original font/rail scenes, never commercial score crops or coordinate lookup rules. */
public class ElevatedBowOriginalSceneTest {
    static final int W = 600, H = 700;
    static final float GAP = 16;
    byte[] gray = new byte[W * H], labels = new byte[W * H];
    List<PlayingTechniqueDetector.Staff> staffs = new ArrayList<>();
    List<MeasureRegion> measures = new ArrayList<>();
    List<ScoreNoteEvent> notes = new ArrayList<>();
    int lower;

    void scene(int lowerTop, int bottom, char glyph) throws Exception {
        lower = lowerTop;
        Arrays.fill(gray, (byte) 255);
        staffs.add(new PlayingTechniqueDetector.Staff(100, 164, GAP, 0, 1));
        staffs.add(new PlayingTechniqueDetector.Staff(lower, lower + 64, GAP, 0, 1));
        measures.add(new MeasureRegion(.1f, .9f, 90f / H, 180f / H));
        measures.add(new MeasureRegion(.1f, .9f, (lower - 10f) / H, (lower + 80f) / H));
        notes.add(note(0, 120, 132, 4));
        notes.add(note(1, 300, lower + 8, 7));
        for (int top : new int[] {100, lower})
            for (int line = 0; line < 5; line++)
                for (int x = 60; x <= 540; x++) gray[(top + line * 16) * W + x] = 0;
        for (int i = 0; i < 2; i++) {
            int x = i == 0 ? 120 : 300, y = i == 0 ? 132 : lower + 8;
            for (int dy = -3; dy <= 3; dy++)
                for (int dx = -5; dx <= 5; dx++)
                    if (dx * dx / 25d + dy * dy / 9d <= 1) gray[(y + dy) * W + x + dx] = 0;
        }
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(Font.createFont(Font.TRUETYPE_FONT, BowDirectionTest.fontFile()).deriveFont(60f));
        var vector = g.getFont().createGlyphVector(g.getFontRenderContext(), String.valueOf(glyph));
        var bounds = vector.getVisualBounds();
        g.drawGlyphVector(
                vector, (float) (300 - bounds.getCenterX()), (float) (bottom - bounds.getMaxY()));
        g.dispose();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                gray[y * W + x] =
                        (byte)
                                Math.min(
                                        gray[y * W + x] & 255,
                                        image.getRaster().getSample(x, y, 0));
    }

    ScoreNoteEvent note(int measure, float x, float y, int step) {
        var m = measures.get(measure);
        float position = (x / W - m.left()) / (m.right() - m.left());
        return new ScoreNoteEvent(
                measure,
                position,
                step,
                0,
                1,
                y / H,
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

    List<ScoreTechniqueChange> read() {
        var before = gray.clone();
        var semantic = labels.clone();
        var r = BowDirectionDetector.detect(labels, gray, W, H, staffs, measures, notes);
        assertArrayEquals(before, gray);
        assertArrayEquals(semantic, labels);
        return r;
    }

    void positive() {
        var n = notes.get(1);
        assertEquals(
                List.of(
                        new ScoreTechniqueChange(
                                1, n.positionInMeasure(), 0, 1, ScoreTechniqueChange.UP_BOW)),
                read());
    }

    void eraseRails(int top, int lines) {
        for (int line = 0; line < lines; line++)
            for (int x = 60; x <= 540; x++) gray[(top + line * 16) * W + x] = (byte) 255;
    }

    @Test
    public void genuineFiniteGap() throws Exception {
        scene(400, 328, '\uE612');
        positive();
    }

    @Test
    public void widerFiniteGap() throws Exception {
        scene(440, 368, '\uE612');
        positive();
    }

    @Test
    public void translatedLowerRow() throws Exception {
        scene(480, 408, '\uE612');
        positive();
    }

    @Test
    public void upperHalfAbstains() throws Exception {
        scene(400, 250, '\uE612');
        assertTrue(read().isEmpty());
    }

    @Test
    public void componentCrossingMidpointAbstains() throws Exception {
        scene(400, 294, '\uE612');
        assertTrue(read().isEmpty());
    }

    @Test
    public void absentUpperBoundaryAbstains() throws Exception {
        scene(400, 328, '\uE612');
        staffs.remove(0);
        assertTrue(read().isEmpty());
    }

    @Test
    public void duplicateRowAbstains() throws Exception {
        scene(400, 328, '\uE612');
        staffs.add(staffs.get(1));
        assertTrue(read().isEmpty());
    }

    @Test
    public void overlappingUpperRowAbstains() throws Exception {
        scene(400, 328, '\uE612');
        staffs.add(new PlayingTechniqueDetector.Staff(120, 184, GAP, 0, 1));
        assertTrue(read().isEmpty());
    }

    @Test
    public void parallelUpperPartAbstains() throws Exception {
        scene(400, 328, '\uE612');
        staffs.set(0, new PlayingTechniqueDetector.Staff(100, 164, GAP, 0, 2));
        assertTrue(read().isEmpty());
    }

    @Test
    public void distinctLowerAttackAbstains() throws Exception {
        scene(400, 328, '\uE612');
        notes.add(note(1, 305, 408, 7));
        assertTrue(read().isEmpty());
    }

    @Test
    public void chordSameAttackRetained() throws Exception {
        scene(400, 328, '\uE612');
        notes.add(notes.get(1));
        positive();
    }

    @Test
    public void upperSameColumnDoesNotSteal() throws Exception {
        scene(400, 328, '\uE612');
        notes.set(0, note(0, 300, 132, 4));
        positive();
    }

    @Test
    public void absentLowerRailsAbstains() throws Exception {
        scene(400, 328, '\uE612');
        eraseRails(lower, 5);
        assertTrue(read().isEmpty());
    }

    @Test
    public void fourLowerRailsAbstains() throws Exception {
        scene(400, 328, '\uE612');
        eraseRails(lower, 1);
        assertTrue(read().isEmpty());
    }

    @Test
    public void absentUpperRailsAbstains() throws Exception {
        scene(400, 328, '\uE612');
        eraseRails(100, 5);
        assertTrue(read().isEmpty());
    }

    @Test
    public void falseBottomRuleHintAbstains() throws Exception {
        scene(400, 328, '\uE612');
        staffs.set(1, new PlayingTechniqueDetector.Staff(432, 496, GAP, 0, 1));
        assertTrue(read().isEmpty());
    }

    @Test
    public void unsupportedMeasureColumnAbstains() throws Exception {
        scene(400, 328, '\uE612');
        measures.set(0, new MeasureRegion(.1f, .3f, 90f / H, 180f / H));
        notes.set(0, note(0, 120, 132, 4));
        assertTrue(read().isEmpty());
    }

    @Test
    public void downShapeNotExtended() throws Exception {
        scene(400, 328, '\uE610');
        assertTrue(read().isEmpty());
    }

    @Test
    public void connectedTruncatedVRetained() throws Exception {
        scene(400, 328, '\uE612');
        for (int y = 323; y <= 329; y++)
            for (int x = 280; x <= 320; x++) gray[y * W + x] = (byte) 255;
        positive();
    }

    @Test
    public void genuinelyDisjointArmsNotExtended() throws Exception {
        scene(400, 328, '\uE612');
        for (int y = 321; y <= 329; y++)
            for (int x = 280; x <= 320; x++) gray[y * W + x] = (byte) 255;
        assertEquals(2, completeGlyphComponents());
        assertTrue(read().isEmpty());
    }

    int completeGlyphComponents() {
        boolean[] seen = new boolean[gray.length];
        int[] queue = new int[gray.length];
        int components = 0;
        for (int seed = 0; seed < gray.length; seed++) {
            if (seen[seed] || (gray[seed] & 255) >= 155) continue;
            int size = 1, take = 0;
            boolean intersects = false;
            queue[0] = seed;
            seen[seed] = true;
            while (take < size) {
                int at = queue[take++], x = at % W, y = at / W;
                if (x >= 275 && x <= 325 && y >= 280 && y <= 330) intersects = true;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= W || yy < 0 || yy >= H) continue;
                        int next = yy * W + xx;
                        if (!seen[next] && (gray[next] & 255) < 155) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (intersects) components++;
        }
        return components;
    }

    @Test
    public void lowContrastBodyNotExtended() throws Exception {
        scene(400, 328, '\uE612');
        for (int y = 280; y <= 330; y++)
            for (int x = 275; x <= 325; x++)
                if ((gray[y * W + x] & 255) < 155) gray[y * W + x] = (byte) 170;
        assertTrue(read().isEmpty());
    }

    @Test
    public void textRowNotExtended() throws Exception {
        scene(400, 328, '\uE612');
        for (int y = 280; y <= 330; y++)
            for (int x = 275; x <= 325; x++) gray[y * W + x] = (byte) 255;
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Serif", Font.PLAIN, 30));
        g.drawString("Vivo", 289, 328);
        g.dispose();
        for (int y = 280; y <= 330; y++)
            for (int x = 275; x <= 390; x++)
                gray[y * W + x] = (byte) im.getRaster().getSample(x, y, 0);
        assertTrue(read().isEmpty());
    }

    @Test
    public void midpointEqualityIsNotOwnership() throws Exception {
        scene(400, 328, '\uE612');
        var anchors = new ArrayList<NoteArticulationDetector.Anchor>();
        for (var n : notes) {
            var m = measures.get(n.measureIndex());
            float x = (m.left() + (m.right() - m.left()) * n.positionInMeasure()) * W;
            int frame = n.measureIndex();
            anchors.add(new NoteArticulationDetector.Anchor(x, n.pageY() * H, GAP, frame));
        }
        var support = new ElevatedBowSupport(gray, W, H, anchors, notes, staffs, measures);
        assertFalse(support.possible(294, 282, 306, 304, 1));
        assertFalse(support.reserve(64_000_001L));
    }
}
