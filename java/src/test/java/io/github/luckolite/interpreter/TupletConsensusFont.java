// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original SIL-OFL Bravura rasterization and procedural beams; no source-score pixels. */
public class TupletConsensusFont {
    record Raster(byte[] gray, int left, int top, int width, int height) {}

    static Path fontFile() {
        for (var p :
                new Path[] {
                    Path.of("java/assets/Bravura.otf"),
                    Path.of("app/src/main/assets/engraving/Bravura.otf"),
                    Path.of("src/main/assets/engraving/Bravura.otf")
                }) if (Files.isRegularFile(p)) return p;
        throw new IllegalStateException("Missing licensed Bravura regression fixture");
    }

    static Raster glyph(int digit, int size, float phase, float shear, float narrow)
            throws Exception {
        var font = Font.createFont(Font.TRUETYPE_FONT, fontFile().toFile()).deriveFont(100f);
        var shape =
                font.createGlyphVector(
                                new FontRenderContext(null, true, true),
                                String.valueOf((char) (0xE880 + digit)))
                        .getOutline();
        shape = AffineTransform.getShearInstance(shear, 0).createTransformedShape(shape);
        var b = shape.getBounds2D();
        double sy = size / b.getHeight(), sx = sy * narrow;
        var t = new AffineTransform();
        t.translate(20 + phase - b.getX() * sx, 20 + phase - b.getY() * sy);
        t.scale(sx, sy);
        var im = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY);
        var g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(t.createTransformedShape(shape));
        g.dispose();
        byte[] gray = new byte[10000];
        int l = 100, r = -1, top = 100, bot = -1;
        for (int y = 0; y < 100; y++)
            for (int x = 0; x < 100; x++) {
                int v = im.getRaster().getSample(x, y, 0);
                gray[y * 100 + x] = (byte) v;
                if (v <= 165) {
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    top = Math.min(top, y);
                    bot = Math.max(bot, y);
                }
            }
        return new Raster(gray, l, top, r - l + 1, bot - top + 1);
    }
}
