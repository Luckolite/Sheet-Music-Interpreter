// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.geom.*;
import java.awt.font.FontRenderContext;
import java.awt.image.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric fours and aliases; licensed Bravura numeral controls, no score pixels. */
public final class RasterMeterReadingsTest {
    /** Resolve the same licensed fixture from either repository root or the Android module. */
    private static Path fontFile() {
        for (Path candidate : new Path[] {
                Path.of("java/assets/Bravura.otf"),
                Path.of("app/src/main/assets/engraving/Bravura.otf"),
                Path.of("src/main/assets/engraving/Bravura.otf")})
            if (Files.isRegularFile(candidate)) return candidate;
        throw new IllegalStateException("Missing licensed Bravura regression fixture");
    }

    static final class Page {
        final int gap, w, h, first;
        final byte[] gray, labels;

        Page(int gap) {
            this.gap = gap;
            w = gap * 24;
            h = gap * 18;
            first = gap * 6;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            labels = new byte[w * h];
        }

        void shape(Shape shape, boolean white) {
            var image = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
            var g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setColor(Color.BLACK);
            g.fill(shape);
            g.dispose();
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (image.getRaster().getSample(x, y, 0) < 155) {
                        gray[y * w + x] = white ? (byte) 255 : 0;
                        labels[y * w + x] = white ? 0 : OmrMeasurePostProcessor.SYMBOL;
                    }
        }

        void four(int index) {
            float x = gap * 8, y = first + gap * (.12f + index * 2);
            var path = new Path2D.Float();
            float[] points = {
                0, 1.2f, .75f, 0, 1, 0, 1, 1.2f, 1.3f, 1.2f, 1.3f, 1.4f, 1, 1.4f, 1, 1.8f, .7f,
                1.8f, .7f, 1.4f, 0, 1.4f
            };
            for (int i = 0; i < points.length; i += 2) {
                if (i == 0) path.moveTo(x + points[i] * gap, y + points[i + 1] * gap);
                else path.lineTo(x + points[i] * gap, y + points[i + 1] * gap);
            }
            path.closePath();
            shape(path, false);
            path = new Path2D.Float();
            path.moveTo(x + .23f * gap, y + 1.2f * gap);
            path.lineTo(x + .7f * gap, y + .48f * gap);
            path.lineTo(x + .7f * gap, y + 1.2f * gap);
            path.closePath();
            shape(path, true);
        }

        void staff() {
            for (int i = 0; i < 5; i++)
                for (int x = gap; x < w - gap; x++) {
                    int y = first + i * gap;
                    gray[y * w + x] = 0;
                    labels[y * w + x] = OmrMeasurePostProcessor.STAFF;
                }
        }

        void digit(int digit, int index) throws Exception {
            var font =
                    Font.createFont(Font.TRUETYPE_FONT, fontFile().toFile())
                            .deriveFont(64f);
            Shape glyph =
                    font.createGlyphVector(
                                    new FontRenderContext(null, true, true),
                                    String.valueOf((char) (0xE080 + digit)))
                            .getOutline();
            var b = glyph.getBounds2D();
            double scale = gap * 1.8 / b.getHeight();
            var transform = new AffineTransform();
            transform.translate(gap * 8, first + gap * (.1 + index * 2));
            transform.scale(scale, scale);
            transform.translate(-b.getX(), -b.getY());
            shape(transform.createTransformedShape(glyph), false);
        }

        void common(boolean cut) {
            int x = gap * 16, y = first + gap * 2;
            for (int yy = first; yy <= first + 4 * gap; yy++)
                for (int xx = x - gap; xx <= x + gap; xx++) {
                    double outer =
                            Math.pow((xx - x) / (double) gap, 2)
                                    + Math.pow((yy - y) / (gap * 1.5), 2);
                    double inner =
                            Math.pow((xx - x) / (gap * .625), 2)
                                    + Math.pow((yy - y) / (gap * 1.125), 2);
                    if (outer <= 1 && inner >= 1 && !(xx > x && Math.abs(yy - y) < gap * .5625))
                        gray[yy * w + xx] = 0;
                }
            if (cut)
                for (int yy = Math.round(first + gap * .25f);
                        yy <= first + Math.round(gap * 3.75f);
                        yy++) for (int xx = x - 1; xx <= x + 1; xx++) gray[yy * w + xx] = 0;
            for (int yy = first - gap * 2; yy < first + gap * 6; yy++)
                for (int xx = gap * 2; xx < gap * 3; xx++) {
                    gray[yy * w + xx] = 0;
                    labels[yy * w + xx] = OmrMeasurePostProcessor.CLEF_OR_KEY;
                }
        }

        List<ScoreMeterChange> read(List<MeasureRegion> measures, List<ScoreNoteEvent> notes) {
            return MeterChangeDetector.rasterMeterReadings(labels, gray, w, h, measures, notes);
        }
    }

    private static final List<MeasureRegion> MEASURES = List.of(new MeasureRegion(0, 1, 0, 1));

    static Page fours(int gap) {
        var p = new Page(gap);
        p.four(0);
        p.four(1);
        p.staff();
        return p;
    }

    @Test
    public void completeFoursReadAtFourIndependentRasterScales() {
        for (int gap : new int[] {10, 16, 17, 24}) {
            var p = fours(gap);
            assertEquals(List.of(new ScoreMeterChange(0, 4, 4)), p.read(MEASURES, List.of()));
        }
    }

    @Test
    public void repeatedRestRectanglesUseFirstLogicalMeasureAndKeepSourceUntouched() {
        var p = fours(17);
        var before = p.labels.clone();
        var ink = p.gray.clone();
        assertEquals(
                List.of(new ScoreMeterChange(0, 4, 4)),
                p.read(List.of(MEASURES.get(0), MEASURES.get(0), MEASURES.get(0)), List.of()));
        assertArrayEquals(before, p.labels);
        assertArrayEquals(ink, p.gray);
    }

    @Test
    public void recognizedHeadAndEarlierNoteOwnTheirMusic() {
        for (float position : new float[] {8.6f / 24, .1f}) {
            var p = fours(17);
            var note =
                    new ScoreNoteEvent(
                            0,
                            position,
                            4,
                            0,
                            1,
                            (p.first + p.gap * 2) / (float) p.h,
                            false,
                            0,
                            0,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            1);
            assertTrue(p.read(MEASURES, List.of(note)).isEmpty());
        }
    }

    @Test
    public void commonAndNumericChoicesAgreeOrAbstainTogether() {
        for (boolean cut : new boolean[] {false, true}) {
            var p = fours(16);
            p.common(cut);
            p.staff();
            assertEquals(
                    List.of(new ScoreMeterChange(0, cut ? 2 : 4, cut ? 2 : 4)),
                    MeterChangeDetector.commonTimeReadings(
                            p.labels, p.gray, p.w, p.h, MEASURES, List.of()));
            var choices =
                    MeterChangeDetector.rasterMeterCandidates(
                            p.labels, p.gray, p.w, p.h, MEASURES, List.of());
            if (cut) {
                assertEquals(
                        List.of(new ScoreMeterChange(0, 2, 2), new ScoreMeterChange(0, 4, 4)),
                        choices);
                assertTrue(p.read(MEASURES, List.of()).isEmpty());
            } else
                assertEquals(List.of(new ScoreMeterChange(0, 4, 4)), p.read(MEASURES, List.of()));
        }
    }

    @Test
    public void otherPrintedMetersCannotBecomeFourFour() throws Exception {
        for (int gap : new int[] {10, 16, 17, 24})
            for (int[] meter : new int[][] {{3, 4}, {6, 8}, {8, 8}}) {
                var p = new Page(gap);
                p.digit(meter[0], 0);
                p.digit(meter[1], 1);
                p.staff();
                assertTrue(p.read(MEASURES, List.of()).isEmpty());
            }
    }

    @Test
    public void triangularPocketsWithoutACompleteFourStayRejected() {
        int w = 160, h = 180;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int index = 0; index < 2; index++) {
            int top = 50 + 35 * index;
            for (int y = top; y < top + 16; y++) for (int x = 49; x <= 68; x++) gray[y * w + x] = 0;
            for (int row = 0; row < 7; row++) {
                int wide = 4 + Math.round(.4f * row);
                for (int x = 64 - wide + 1; x <= 64; x++)
                    gray[(top + 4 + row) * w + x] = (byte) 255;
            }
        }
        assertFalse(
                OmrMeasurePostProcessor.stackedFourMeterCounters(gray, w, h, 65, 40, 110, 17.5f));
    }

    @Test
    public void restsHeadsBarlinesAndSingleDigitsStayRejected() {
        for (int gap : new int[] {10, 16, 17, 24}) {
            for (String alias :
                    List.of(
                            "hollow notes",
                            "barlines",
                            "quarter rests",
                            "eighth rests",
                            "single four")) {
                var falsePage = new Page(gap);
                if (alias.equals("single four")) falsePage.four(0);
                else
                    for (int i = 0; i < 2; i++) {
                        float x = gap * 8, y = falsePage.first + gap * (.15f + i * 2);
                        if (alias.equals("hollow notes")) {
                            var ring =
                                    new Area(
                                            new Ellipse2D.Float(
                                                    x, y + gap * .6f, gap * 1.1f, gap * .7f));
                            ring.subtract(
                                    new Area(
                                            new Ellipse2D.Float(
                                                    x + gap * .2f,
                                                    y + gap * .75f,
                                                    gap * .7f,
                                                    gap * .4f)));
                            falsePage.shape(ring, false);
                            falsePage.shape(
                                    new Rectangle2D.Float(
                                            x + gap, y - gap * .5f, gap * .12f, gap * 1.5f),
                                    false);
                        } else if (alias.equals("barlines"))
                            falsePage.shape(
                                    new Rectangle2D.Float(x, y, gap * .15f, gap * 1.8f), false);
                        else if (alias.equals("quarter rests")) {
                            var zig = new Path2D.Float();
                            zig.moveTo(x, y);
                            zig.lineTo(x + gap, y + gap * .7f);
                            zig.lineTo(x + gap * .3f, y + gap * 1.05f);
                            zig.lineTo(x + gap * .85f, y + gap * 1.6f);
                            falsePage.shape(
                                    new BasicStroke(gap * .22f).createStrokedShape(zig), false);
                        } else {
                            falsePage.shape(new Ellipse2D.Float(x, y, gap * .6f, gap * .5f), false);
                            falsePage.shape(
                                    new Rectangle2D.Float(
                                            x + gap * .4f, y + gap * .2f, gap * .15f, gap * 1.4f),
                                    false);
                        }
                    }
                falsePage.staff();
                assertTrue(alias, falsePage.read(MEASURES, List.of()).isEmpty());
            }
        }
    }
}
