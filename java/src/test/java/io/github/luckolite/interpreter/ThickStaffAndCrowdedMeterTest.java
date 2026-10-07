// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Original generated glyphs, staff rules and late-header ownership cases. */
public class ThickStaffAndCrowdedMeterTest {
    static void check(String name, boolean ok) {
        org.junit.Assert.assertTrue(name, ok);
    }

    static String glyphs(String value) {
        return value.codePoints()
                .map(ch -> 0xe080 + ch - '0')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    static int[] signature(
            Font font, String top, String bottom, int width, boolean staff, int phase) {
        var image = new BufferedImage(width, 73, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, 73);
        g.setColor(Color.BLACK);
        if (staff)
            for (int n = 0; n < 5; n++)
                for (int dy = -1; dy <= 1; dy++) {
                    int y = Math.round(3 + n * 16.5f) + phase + dy;
                    g.drawLine(0, y, width - 1, y);
                }
        g.setFont(font);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        print(g, glyphs(top), width, 3, 36);
        print(g, glyphs(bottom), width, 36, 73);
        g.dispose();
        int[] pixels = image.getRGB(0, 0, width, 73, null, 0, width);
        MeterCropRaster.prepare(pixels, width, 73, 0, 3, 16.5f, false);
        return pixels;
    }

    static void print(Graphics2D g, String glyph, int width, int top, int bottom) {
        var b =
                g.getFont()
                        .createGlyphVector(g.getFontRenderContext(), glyph)
                        .getPixelBounds(null, 0, 0);
        int x = (width - b.width) / 2, y = top + (bottom - top - b.height) / 2;
        g.drawString(glyph, x - b.x, y - b.y);
    }

    static void digit(byte[] gray, int width, int left, int top) {
        for (int base : new int[] {top + 2, top + 35})
            for (int y = 0; y < 28; y++)
                for (int x = 0; x < 19; x++)
                    if (x >= 15 || y < 4 || y >= 12 && y < 16 || y >= 24)
                        gray[(base + y) * width + left + x] = 40;
    }

    static void staff(byte[] labels, byte[] gray, int width, int top) {
        for (int n = 0; n < 5; n++)
            for (int x = 20; x < width - 20; x++) {
                int i = (top + n * 16) * width + x;
                labels[i] = OmrMeasurePostProcessor.STAFF;
                gray[i] = 60;
            }
    }

    static void lateHeader(boolean across) throws Exception {
        int w = across ? 1100 : 4000, h = across ? 1600 : 300;
        byte[] labels = new byte[w * h], gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        var measures = new ArrayList<MeasureRegion>();
        var notes = new ArrayList<ScoreNoteEvent>();
        if (across)
            for (int row = 0; row < 9; row++) {
                int top = 60 + 160 * row;
                staff(labels, gray, w, top);
                for (int n = 0; n < (row == 8 ? 1 : 6); n++) digit(gray, w, 110 + 130 * n, top);
                measures.add(
                        new MeasureRegion(
                                .01f, .99f, (top - 20) / (float) h, (top + 84) / (float) h));
                notes.add(
                        new ScoreNoteEvent(
                                row,
                                row == 8 ? .90f : .025f,
                                4,
                                0,
                                1,
                                (top + 32) / (float) h,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1));
            }
        else {
            staff(labels, gray, w, 60);
            for (int n = 0; n < 50; n++) digit(gray, w, 110 + 60 * n, 60);
            digit(gray, w, 3600, 60);
            measures.add(new MeasureRegion(.005f, .86f, 40f / h, 145f / h));
            measures.add(new MeasureRegion(.86f, .99f, 40f / h, 145f / h));
            notes.add(
                    new ScoreNoteEvent(
                            0,
                            .015f,
                            4,
                            0,
                            1,
                            92f / h,
                            false,
                            0,
                            0,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            1));
            notes.add(
                    new ScoreNoteEvent(
                            1,
                            .95f,
                            4,
                            0,
                            1,
                            92f / h,
                            false,
                            0,
                            0,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            1));
        }
        List<MeterChangeDetector.Crop> selected;
        try {
            var method =
                    MeterChangeDetector.class.getMethod(
                            "candidates",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            List.class,
                            List.class);
            @SuppressWarnings("unchecked")
            var value =
                    (List<MeterChangeDetector.Crop>)
                            method.invoke(null, labels, gray, w, h, measures, notes);
            selected = value;
        } catch (NoSuchMethodException shipping) {
            selected = new ArrayList<>();
            for (var crop : MeterChangeDetector.candidates(labels, gray, w, h)) {
                int measure = MeterChangeDetector.followingMeasure(crop, w, h, measures);
                if (measure >= 0
                        && MeterChangeDetector.precedesNotes(
                                crop, labels, gray, w, h, measure, measures, notes))
                    selected.add(crop);
            }
        }
        check(
                across
                        ? "late header after earlier staffs body ink"
                        : "late header after same-staff body ink",
                selected.size() == 1
                        && (across
                                ? selected.get(0).firstLine() > 1300
                                : selected.get(0).left() > 3500));
        check(
                across
                        ? "legacy raw budget stays bounded across staff"
                        : "legacy raw budget stays bounded in staff",
                MeterChangeDetector.candidates(labels, gray, w, h).size() == 48);
    }

    @org.junit.Test
    public void stackedNumeralsKeepTheirReadingOnThickPrintedRules() throws Exception {
        var file = new File("java/assets/Bravura.otf");
        var matcher = new MeterFontMatcher(file);
        var font = Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(62f);
        for (String meter : new String[] {"2/4", "3/4", "4/4", "6/8", "12/8"}) {
            String[] parts = meter.split("/");
            int w = parts[0].length() > 1 ? 48 : 32;
            for (int mode = 0; mode < 2; mode++) {
                var result =
                        matcher.read(
                                signature(font, parts[0], parts[1], w, mode == 1, 1),
                                w,
                                73,
                                36,
                                3,
                                16.5f);
                check(
                        meter + (mode == 0 ? " clean" : " three-pixel shifted staff"),
                        result.text().equals(meter));
            }
        }
        int[] blank = new int[32 * 73];
        Arrays.fill(blank, 0xffffffff);
        check("empty crop abstains", matcher.read(blank, 32, 73, 36, 3, 16.5f).text().isBlank());
        for (int y = 0; y < 73; y++) blank[y * 32 + 16] = 0xff000000;
        check(
                "full vertical barline abstains",
                matcher.read(blank, 32, 73, 36, 3, 16.5f).text().isBlank());
        check(
                "missing lower numeral abstains",
                matcher.read(signature(font, "3", "", 32, true, 1), 32, 73, 36, 3, 16.5f)
                        .text()
                        .isBlank());
        var image = new BufferedImage(32, 73, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 32, 73);
        g.setColor(Color.BLACK);
        g.drawOval(7, 7, 18, 22);
        g.drawOval(7, 41, 18, 22);
        g.dispose();
        check(
                "two hollow note ovals abstain",
                matcher.read(image.getRGB(0, 0, 32, 73, null, 0, 32), 32, 73, 36, 3, 16.5f)
                        .text()
                        .isBlank());
    }

    @org.junit.Test
    public void earlierStaffBodyInkCannotStarveALaterHeader() throws Exception {
        lateHeader(true);
    }

    @org.junit.Test
    public void earlierBarBodyInkCannotStarveALaterInlineHeader() throws Exception {
        lateHeader(false);
    }
}
