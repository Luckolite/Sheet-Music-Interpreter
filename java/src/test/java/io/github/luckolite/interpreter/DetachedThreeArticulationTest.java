// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original cubic threes and licensed SIL-OFL Bravura glyphs; no score pixels. */
public class DetachedThreeArticulationTest {
    /** Resolve the same licensed fixture from either repository root or the Android module. */
    private static Path fontFile() {
        for (Path candidate : new Path[] {
                Path.of("java/assets/Bravura.otf"),
                Path.of("app/src/main/assets/engraving/Bravura.otf"),
                Path.of("src/main/assets/engraving/Bravura.otf")})
            if (Files.isRegularFile(candidate)) return candidate;
        throw new IllegalStateException("Missing licensed Bravura regression fixture");
    }

    private static final int W = 960, H = 1280;

    private record Raster(byte[] gray, int left, int top, int right, int bottom) {}

    private Raster numeral(int kind, int size, float narrow, float cut, float tilt)
            throws Exception {
        Shape shape;
        if (kind == 0) {
            var font =
                    Font.createFont(Font.TRUETYPE_FONT, fontFile().toFile())
                            .deriveFont(100f);
            shape =
                    font.createGlyphVector(new FontRenderContext(null, true, true), "\uE083")
                            .getOutline();
        } else {
            var cubic = new Path2D.Float();
            cubic.moveTo(1, 3);
            cubic.curveTo(6, -1, 13, 1, 11, 6);
            cubic.curveTo(10, 8, 7, 9, 6, 9);
            cubic.curveTo(12, 9, 13, 13, 10, 16);
            cubic.curveTo(8, 19, 3, 20, 0, 17);
            shape =
                    new BasicStroke(1.5f + kind, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                            .createStrokedShape(cubic);
        }
        shape = AffineTransform.getShearInstance(-tilt, 0).createTransformedShape(shape);
        var b = shape.getBounds2D();
        double sy = size / b.getHeight(), sx = sy * narrow;
        var t = new AffineTransform();
        t.translate(140 - b.getX() * sx, 100 - b.getY() * sy);
        t.scale(sx, sy);
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(t.createTransformedShape(shape));
        g.dispose();
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        int left = W, right = -1, top = H, bottom = -1;
        float edge = (float) (140 + b.getWidth() * sx * cut);
        for (int y = 90; y < 140; y++)
            for (int x = 130; x < 185; x++) {
                int tone = x < edge ? 149 : 35,
                        shade = tone + (250 - tone) * image.getRaster().getSample(x, y, 0) / 255;
                gray[y * W + x] = (byte) shade;
                if (shade < 155) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        return new Raster(gray, left, top, right, bottom);
    }

    private NoteArticulationDetector.Anchor owner(Raster r) {
        return new NoteArticulationDetector.Anchor(
                (r.left + r.right) * .5f - 16 * .6f, (r.top + r.bottom) * .5f + 16 * 4.25f, 16, 0);
    }

    private int[] read(byte[] gray, List<NoteArticulationDetector.Anchor> anchors) {
        byte[] labels = new byte[W * H], grayBefore = gray.clone(), labelsBefore = labels.clone();
        int[] marks = NoteArticulationDetector.detect(labels, gray, W, H, anchors);
        assertArrayEquals(grayBefore, gray);
        assertArrayEquals(labelsBefore, labels);
        return marks;
    }

    private void sweep(int first, int last) throws Exception {
        for (int kind = first; kind <= last; kind++)
            for (int size : new int[] {18, 20, 22})
                for (float narrow : new float[] {.85f, 1})
                    for (float cut : new float[] {.4f, .5f, .6f})
                        for (float tilt : new float[] {0, .24f}) {
                            Raster r = numeral(kind, size, narrow, cut, tilt);
                            assertEquals(
                                    kind + "," + size + "," + narrow + "," + cut + "," + tilt,
                                    0,
                                    read(r.gray, List.of(owner(r)))[0]);
                        }
    }

    @Test
    public void engravedThreeOwnsItsSeparatedLeftTerminals() throws Exception {
        sweep(0, 0);
    }

    @Test
    public void originalThinThreesOwnTheirDarkerWedgeCores() throws Exception {
        sweep(1, 2);
    }

    @Test
    public void originalHeavyThreesKeepTheirCompleteShape() throws Exception {
        sweep(3, 4);
    }

    private void wedge(byte[] gray, int x, int y, int height, boolean below, boolean shaded) {
        for (int row = 0; row < height; row++) {
            int span = 1 + Math.round(8 * (height - 1 - row) / (float) (height - 1));
            int left = x - (span - 1) / 2, yy = y + (below ? height - 1 - row : row);
            for (int xx = left; xx < left + span; xx++)
                gray[yy * W + xx] = (byte) (shaded && xx < x ? 149 : 35);
        }
    }

    @Test
    public void realFilledWedgesRemainAboveAndBelowNotes() {
        for (boolean below : new boolean[] {false, true})
            for (int height : new int[] {16, 20}) {
                byte[] gray = new byte[W * H];
                Arrays.fill(gray, (byte) 250);
                wedge(gray, 300, 100, height, below, false);
                assertEquals(
                        NoteArticulation.STACCATISSIMO,
                        read(
                                gray,
                                List.of(
                                        new NoteArticulationDetector.Anchor(
                                                300, below ? 65 : 155, 16, 0)))[0]);
            }
    }

    @Test
    public void contrastingWedgeCoresStillBelongToRealWedges() {
        for (boolean below : new boolean[] {false, true}) {
            byte[] gray = new byte[W * H];
            Arrays.fill(gray, (byte) 250);
            wedge(gray, 300, 100, 20, below, true);
            assertEquals(
                    NoteArticulation.STACCATISSIMO,
                    read(
                            gray,
                            List.of(
                                    new NoteArticulationDetector.Anchor(
                                            300, below ? 65 : 155, 16, 0)))[0]);
        }
    }

    @Test
    public void numeralOwnershipDoesNotClaimANeighboringWedge() throws Exception {
        Raster r = numeral(0, 22, 1, .5f, .24f);
        wedge(r.gray, 120, 100, 20, false, false);
        int[] marks =
                read(
                        r.gray,
                        List.of(owner(r), new NoteArticulationDetector.Anchor(120, 155, 16, 0)));
        assertEquals(0, marks[0]);
        assertEquals(NoteArticulation.STACCATISSIMO, marks[1]);
    }
}
