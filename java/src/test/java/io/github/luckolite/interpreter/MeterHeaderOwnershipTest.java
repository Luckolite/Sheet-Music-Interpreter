// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Licensed Bravura clef ink and original brace/digit geometry; no score pixels. */
public final class MeterHeaderOwnershipTest {
    private static final class Page {
        final int gap, width, height, first;
        final byte[] labels, gray;

        Page(int gap) {
            this.gap = gap;
            width = gap * 18;
            height = gap * 30;
            first = gap * 8;
            labels = new byte[width * height];
            gray = new byte[labels.length];
            Arrays.fill(gray, (byte) 255);
        }

        void draw(Shape shape, int label, float stroke) {
            var image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            var graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (stroke > 0) {
                graphics.setStroke(
                        new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.draw(shape);
            } else graphics.fill(shape);
            graphics.dispose();
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) {
                    int shade = image.getRaster().getSample(x, y, 0);
                    if (shade < 205) {
                        gray[y * width + x] = (byte) shade;
                        labels[y * width + x] = (byte) label;
                    }
                }
        }

        MeterChangeDetector.Crop clef() throws Exception {
            Font font =
                    Font.createFont(Font.TRUETYPE_FONT, Path.of("java/assets/Bravura.otf").toFile())
                            .deriveFont(64f);
            Shape glyph =
                    font.createGlyphVector(new FontRenderContext(null, true, true), "\uE050")
                            .getOutline();
            var bounds = glyph.getBounds2D();
            double scale = gap * 7 / bounds.getHeight();
            var transform = new AffineTransform();
            transform.translate(gap * 5, first - gap * 1.5);
            transform.scale(scale, scale);
            transform.translate(-bounds.getX(), -bounds.getY());
            Shape positioned = transform.createTransformedShape(glyph);
            draw(positioned, 3, 0);
            var b = positioned.getBounds();
            return new MeterChangeDetector.Crop(
                    b.x - 2, first - 2, b.x + b.width + 2, first + gap * 4 + 2, first, gap);
        }

        MeterChangeDetector.Crop brace(boolean lower) {
            double x = gap * 6, mid = first + gap * 7, end = first + gap * 14;
            var shape = new Path2D.Double();
            shape.moveTo(x, first);
            shape.curveTo(x - gap, first + gap * .3, x - gap, mid - gap * .8, x - gap * 1.2, mid);
            shape.curveTo(x - gap, mid + gap * .8, x - gap, end - gap * .3, x, end);
            draw(shape, 5, Math.max(2, gap * .12f));
            int line = lower ? first + gap * 10 : first;
            return new MeterChangeDetector.Crop(
                    Math.round(gap * 4.5f),
                    line - 2,
                    Math.round(gap * 6.2f),
                    line + gap * 4 + 2,
                    line,
                    gap);
        }

        boolean accepted(MeterChangeDetector.Crop crop) throws Exception {
            var measures = java.util.List.of(new MeasureRegion(0, 1, 0, 1));
            var notes =
                    java.util.List.of(
                            new ScoreNoteEvent(
                                    0,
                                    .88f,
                                    4,
                                    0,
                                    1,
                                    (first + gap * 2) / (float) height,
                                    false,
                                    0,
                                    0,
                                    ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                    1));
            assertTrue(MeterChangeDetector.precedesNotes(crop, width, height, 0, measures, notes));
            try {
                var method =
                        MeterChangeDetector.class.getMethod(
                                "precedesNotes",
                                MeterChangeDetector.Crop.class,
                                byte[].class,
                                byte[].class,
                                int.class,
                                int.class,
                                int.class,
                                java.util.List.class,
                                java.util.List.class);
                return (boolean)
                        method.invoke(null, crop, labels, gray, width, height, 0, measures, notes);
            } catch (NoSuchMethodException baseline) {
                return MeterChangeDetector.precedesNotes(crop, width, height, 0, measures, notes);
            }
        }
    }

    @Test
    public void completePrintedTrebleDoesNotBecomeNumericMeterAtSeveralScales() throws Exception {
        for (int gap : new int[] {10, 17, 24}) {
            var page = new Page(gap);
            assertFalse("gap=" + gap, page.accepted(page.clef()));
        }
    }

    @Test
    public void bothStaffCropsOfOneGrandStaffBraceStayOwned() throws Exception {
        for (boolean lower : new boolean[] {false, true}) {
            var page = new Page(17);
            assertFalse(page.accepted(page.brace(lower)));
        }
    }

    @Test
    public void separateTallNumeralsRemainEligibleEvenWithClefSemanticLabels() throws Exception {
        var page = new Page(17);
        int x = page.gap * 5;
        page.draw(
                new Rectangle2D.Float(x, page.first - page.gap * .6f, page.gap, page.gap * 2.4f),
                3,
                0);
        page.draw(
                new Rectangle2D.Float(x, page.first + page.gap * 2.2f, page.gap, page.gap * 2.4f),
                3,
                0);
        assertTrue(
                page.accepted(
                        new MeterChangeDetector.Crop(
                                x - 2,
                                page.first - 2,
                                x + page.gap + 2,
                                page.first + page.gap * 4 + 2,
                                page.first,
                                page.gap)));
    }

    @Test
    public void anIndependentMeterBesideACompleteClefRemainsEligible() throws Exception {
        var page = new Page(17);
        page.clef();
        int x = page.gap * 10;
        page.draw(new Rectangle2D.Float(x, page.first, page.gap, page.gap * 1.7f), 5, 0);
        page.draw(
                new Rectangle2D.Float(x, page.first + page.gap * 2.3f, page.gap, page.gap * 1.7f),
                5,
                0);
        assertTrue(
                page.accepted(
                        new MeterChangeDetector.Crop(
                                x - 2,
                                page.first - 2,
                                x + page.gap + 2,
                                page.first + page.gap * 4 + 2,
                                page.first,
                                page.gap)));
    }

    @Test
    public void aSemanticHeaderWithoutPrintedInkCannotClaimACrop() throws Exception {
        var page = new Page(17);
        var crop = page.clef();
        Arrays.fill(page.gray, (byte) 255);
        assertTrue(page.accepted(crop));
    }

    @Test
    public void headerOwnershipPreservesSourcePixels() throws Exception {
        var page = new Page(17);
        var crop = page.clef();
        var labels = page.labels.clone();
        var gray = page.gray.clone();
        page.accepted(crop);
        assertArrayEquals(labels, page.labels);
        assertArrayEquals(gray, page.gray);
    }
}
