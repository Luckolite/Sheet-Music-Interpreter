// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Base64;

public class GenerateTupletDigits {
    public static void main(String[] a) throws Exception {
        Font bravura = Font.createFont(Font.TRUETYPE_FONT, Path.of(a[0]).toFile());
        Font noto = Font.createFont(Font.TRUETYPE_FONT, Path.of(a[1]).toFile());
        StringBuilder out = new StringBuilder();
        for (int face = 0; face < 1; face++)
            for (int digit = 0; digit < 10; digit++) {
                Font font =
                        (face == 0 ? bravura : face == 1 ? noto : noto.deriveFont(Font.BOLD))
                                .deriveFont(24f);
                String text = String.valueOf((char) (face == 0 ? 0xE880 + digit : '0' + digit));
                var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 100, 100);
                g.setColor(Color.BLACK);
                g.setFont(font);
                g.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.drawString(text, 20, 60);
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
        Files.writeString(Path.of(a[2]), out.toString());
    }
}
