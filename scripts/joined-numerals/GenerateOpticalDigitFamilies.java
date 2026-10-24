// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Base64;

/** Adjacent optical sizes from the existing original-font regression grid, all ten rivals. */
public final class GenerateOpticalDigitFamilies {
    public static void main(String[] args) throws Exception {
        Font noto = Font.createFont(Font.TRUETYPE_FONT, Path.of(args[0]).toFile());
        StringBuilder out = new StringBuilder();
        for (int size : new int[] {20, 28})
            for (int style :
                    new int[] {Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC})
                for (int digit = 0; digit < 10; digit++) {
                    var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
                    var g = image.createGraphics();
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, 100, 100);
                    g.setColor(Color.BLACK);
                    g.setFont(noto.deriveFont(style, (float) size));
                    g.setRenderingHint(
                            RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.drawString("" + digit, 20, 60);
                    g.dispose();
                    int l = 100, t = 100, r = -1, b = -1;
                    for (int y = 0; y < 100; y++)
                        for (int x = 0; x < 100; x++)
                            if ((image.getRGB(x, y) & 255) <= 175) {
                                l = Math.min(l, x);
                                r = Math.max(r, x);
                                t = Math.min(t, y);
                                b = Math.max(b, y);
                            }
                    int w = r - l + 1, h = b - t + 1;
                    byte[] bits = new byte[48];
                    for (int y = 0; y < 24; y++)
                        for (int x = 0; x < 16; x++)
                            if ((image.getRGB(
                                                    l + Math.min(w - 1, x * w / 16),
                                                    t + Math.min(h - 1, y * h / 24))
                                            & 255)
                                    <= 175) {
                                int k = y * 16 + x;
                                bits[k / 8] |= 1 << (k % 8);
                            }
                    out.append("        new Numeral(")
                            .append(digit)
                            .append(", ")
                            .append(w / (float) h)
                            .append("f, \"")
                            .append(Base64.getEncoder().encodeToString(bits))
                            .append("\"),\n");
                }
        Files.writeString(Path.of(args[1]), out, StandardOpenOption.CREATE_NEW);
    }
}
