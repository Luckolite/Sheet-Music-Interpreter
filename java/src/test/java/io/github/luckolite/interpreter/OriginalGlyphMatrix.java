// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.geom.*;
import java.awt.font.*;
import java.awt.image.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Original SIL-OFL Bravura image matrix. No actual score pixels or interpreter invoked. */
public class OriginalGlyphMatrix {
    static final int W = 320, H = 240;
    static Font font;
    static Path out;
    static int count;

    static BufferedImage blank() {
        var image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.dispose();
        return image;
    }

    static void glyph(BufferedImage image, char symbol, double x, double y, double height) {
        var outline =
                font.createGlyphVector(new FontRenderContext(null, true, true), new char[] {symbol})
                        .getOutline();
        var bounds = outline.getBounds2D();
        double scale = height / bounds.getHeight();
        var t = new AffineTransform();
        t.translate(x - bounds.getMinX() * scale, y - bounds.getMinY() * scale);
        t.scale(scale, scale);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fill(t.createTransformedShape(outline));
        g.dispose();
    }

    static void stroke(
            BufferedImage image, double x0, double y0, double x1, double y1, float width) {
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x0, y0, x1, y1));
        g.dispose();
    }

    static byte[] render(String kind, float gap, float phase, int tail, double blur)
            throws Exception {
        double top = 60 + phase, y = top + 1.1 * gap;
        var image = blank();
        switch (kind) {
            case "eighth",
                            "uniform",
                            "white-gap",
                            "white-rule-gap",
                            "clipped-cap",
                            "clipped-foot" ->
                    glyph(image, '\ue4e6', 130, y, gap * 1.8);
            case "compressed-two" -> glyph(image, '\ue4e7', 130, y, gap * 1.8);
            case "added-small-bulb" -> {
                glyph(image, '\ue4e6', 130, y, gap * 1.8);
                var g = image.createGraphics();
                g.setColor(Color.BLACK);
                g.fill(new Ellipse2D.Double(134, top + gap * 2.05, gap * .55, gap * .4));
                g.dispose();
            }
            case "sixteenth" -> glyph(image, '\ue4e7', 130, y, gap * 2.8);
            case "thirtysecond" -> glyph(image, '\ue4e8', 130, top + gap * .1, gap * 3.8);
            case "quarter" -> glyph(image, '\ue4e5', 130, top + gap * .55, gap * 2.7);
            case "natural" -> glyph(image, '\ue261', 130, y, gap * 2.1);
            case "sharp" -> glyph(image, '\ue262', 130, y, gap * 2.1);
            case "flat" -> glyph(image, '\ue260', 130, y, gap * 2.1);
            case "stem" -> stroke(image, 142, y, 135, top + gap * 3, 2);
            case "head-down" -> {
                glyph(image, '\ue0a4', 130, y, gap * .7);
                stroke(image, 130, y + gap * .35, 130, top + gap * 3, 2);
            }
            case "head-up" -> {
                glyph(image, '\ue0a4', 130, top + gap * 2.3, gap * .7);
                stroke(image, 130 + gap, top + gap * 2.65, 130 + gap, top + gap * .4, 2);
            }
            default -> throw new AssertionError(kind);
        }
        int radius = (int) Math.ceil(blur * 3);
        float[] kernel = new float[(radius * 2 + 1) * (radius * 2 + 1)];
        double sum = 0;
        int at = 0;
        for (int yy = -radius; yy <= radius; yy++)
            for (int xx = -radius; xx <= radius; xx++) {
                kernel[at] = (float) Math.exp(-(xx * xx + yy * yy) / (2 * blur * blur));
                sum += kernel[at++];
            }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= sum;
        image =
                new ConvolveOp(
                                new Kernel(radius * 2 + 1, radius * 2 + 1, kernel),
                                ConvolveOp.EDGE_NO_OP,
                                null)
                        .filter(image, null);
        byte[] raw = new byte[W * H];
        for (int yy = 0; yy < H; yy++)
            for (int xx = 0; xx < W; xx++) {
                int foreground = kind.equals("uniform") ? 220 : (yy < y + gap * .5 ? 80 : tail);
                int value =
                        Math.round(
                                foreground
                                        + (250 - foreground)
                                                * image.getRaster().getSample(xx, yy, 0)
                                                / 255f);
                for (int line = 0; line < 5; line++) {
                    double d = yy - (top + line * gap);
                    int rule = (int) Math.round(250 - 100 * Math.exp(-d * d / (2 * .8 * .8)));
                    value = Math.min(value, rule);
                }
                raw[yy * W + xx] = (byte) value;
            }
        if (kind.equals("white-gap") || kind.equals("white-rule-gap")) {
            int row = (int) Math.round(kind.equals("white-gap") ? y + gap * .85 : top + gap * 2);
            for (int xx = 125; xx <= 155; xx++) raw[row * W + xx] = (byte) 250;
        }
        if (kind.equals("clipped-cap"))
            for (int yy = (int) Math.floor(y - gap * .2); yy < y + gap * .4; yy++)
                for (int xx = 125; xx <= 155; xx++) raw[yy * W + xx] = (byte) 250;
        if (kind.equals("clipped-foot"))
            for (int yy = (int) Math.floor(top + gap * 2.7); yy < top + gap * 3.2; yy++)
                for (int xx = 125; xx <= 155; xx++) raw[yy * W + xx] = (byte) 250;
        return raw;
    }

    static void save(String kind, float gap, float phase, int tail, double blur) throws Exception {
        String id =
                String.format(
                        java.util.Locale.ROOT,
                        "%03d-%s-g%s-p%s-t%d-b%s",
                        count++,
                        kind,
                        gap,
                        phase,
                        tail,
                        blur);
        byte[] raw = render(kind, gap, phase, tail, blur);
        try (var stream =
                new DataOutputStream(
                        Files.newOutputStream(
                                out.resolve(id + ".bin"), StandardOpenOption.CREATE_NEW))) {
            stream.writeInt(W);
            stream.writeInt(H);
            stream.writeFloat(60 + phase);
            stream.writeFloat(60 + phase + 4 * gap);
            stream.writeFloat(gap);
            stream.write(raw);
        }
        System.out.println(id + "|" + kind + "|" + gap + "|" + phase + "|" + tail + "|" + blur);
    }

    public static void main(String[] args) throws Exception {
        font = Font.createFont(Font.TRUETYPE_FONT, new File(args[0])).deriveFont(100f);
        out = Path.of(args[1]);
        Files.createDirectory(out);
        for (float gap : new float[] {10, 14.25f, 18})
            for (float phase : new float[] {0, .5f})
                for (int tail : new int[] {170, 200})
                    for (double blur : new double[] {.6, 1.0})
                        save("eighth", gap, phase, tail, blur);
        for (String kind :
                new String[] {
                    "sixteenth",
                    "thirtysecond",
                    "quarter",
                    "natural",
                    "sharp",
                    "flat",
                    "stem",
                    "head-down",
                    "head-up",
                    "uniform",
                    "white-gap",
                    "white-rule-gap",
                    "clipped-cap",
                    "clipped-foot",
                    "compressed-two",
                    "added-small-bulb"
                }) for (float phase : new float[] {0, .5f}) save(kind, 14.25f, phase, 200, .6);
    }
}
