// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Original SIL-OFL Noto Serif glyphs and procedural crossing curves; no score samples. */
public final class GenerateCertaintyGrid {
    public static void main(String[] args) throws Exception {
        Font noto = Font.createFont(Font.TRUETYPE_FONT, Path.of(args[0]).toFile());
        try (var out =
                new DataOutputStream(
                        new GZIPOutputStream(
                                Files.newOutputStream(
                                        Path.of(args[1]), StandardOpenOption.CREATE_NEW)))) {
            out.writeInt(0x4a444e31);
            out.writeInt(2880);
            for (int style :
                    new int[] {Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC})
                for (int size : new int[] {18, 22, 26, 30})
                    for (int digit = 0; digit < 10; digit++)
                        for (int lift : new int[] {1, 3, 5})
                            for (int shape = 0; shape < 3; shape++)
                                for (int thickness : new int[] {1, 3}) {
                                    var image =
                                            new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
                                    var g = image.createGraphics();
                                    g.setColor(Color.WHITE);
                                    g.fillRect(0, 0, 128, 128);
                                    g.setFont(noto.deriveFont(style, (float) size));
                                    g.setColor(Color.BLACK);
                                    g.setRenderingHint(
                                            RenderingHints.KEY_TEXT_ANTIALIASING,
                                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                                    g.drawString("" + digit, 48, 80);
                                    g.dispose();
                                    byte[] raw = new byte[16384];
                                    int l = 128, t = 128, r = -1, b = -1;
                                    for (int y = 0; y < 128; y++)
                                        for (int x = 0; x < 128; x++) {
                                            int value = image.getRGB(x, y) & 255;
                                            raw[y * 128 + x] = (byte) value;
                                            if (value <= 165) {
                                                l = Math.min(l, x);
                                                r = Math.max(r, x);
                                                t = Math.min(t, y);
                                                b = Math.max(b, y);
                                            }
                                        }
                                    byte[] original = raw.clone();
                                    BitSet curve = new BitSet();
                                    int contact = b - lift;
                                    float mid = (l + r) * .5f;
                                    for (int x = l - 20; x <= r + 20; x++) {
                                        float dx = x - mid;
                                        int y =
                                                contact
                                                        + Math.round(
                                                                shape == 0
                                                                        ? dx * .15f
                                                                        : shape == 1
                                                                                ? -dx * .15f
                                                                                : dx * dx / 180f);
                                        for (int dy = 0; dy < thickness; dy++) {
                                            raw[(y + dy) * 128 + x] = 0;
                                            curve.set((y + dy) * 128 + x);
                                        }
                                    }
                                    int rl = 128, rt = 128, rr = -1, rb = -1;
                                    for (int y = t; y <= b; y++)
                                        for (int x = l; x <= r; x++)
                                            if ((original[y * 128 + x] & 255) <= 165
                                                    && !curve.get(y * 128 + x)) {
                                                rl = Math.min(rl, x);
                                                rr = Math.max(rr, x);
                                                rt = Math.min(rt, y);
                                                rb = Math.max(rb, y);
                                            }
                                    for (int value :
                                            new int[] {
                                                style, size, digit, lift, shape, l, t, r, b, rl, rt,
                                                rr, rb
                                            }) out.writeInt(value);
                                    out.write(raw);
                                    byte[] mask = curve.toByteArray();
                                    out.writeInt(mask.length);
                                    out.write(mask);
                                }
        }
    }
}
