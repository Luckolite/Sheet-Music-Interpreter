// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import org.junit.Test;

/** Original deterministic prose negative. The licensed font is test-only. */
public final class ElevatedBowProseTest {
    @Test
    public void explicitDejaVuVivoRemainsProse() throws Exception {
        var f = new ElevatedBowOriginalSceneTest();
        f.scene(400, 328, '\uE612');
        int w = ElevatedBowOriginalSceneTest.W, h = ElevatedBowOriginalSceneTest.H;
        for (int y = 280; y <= 330; y++)
            for (int x = 275; x <= 325; x++) f.gray[y * w + x] = (byte) 255;
        Font font;
        try (InputStream in =
                ElevatedBowProseTest.class.getResourceAsStream("/bow-text-fonts/DejaVuSerif.ttf")) {
            if (in == null) throw new AssertionError("Missing test-only DejaVu Serif");
            font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(30f);
        }
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.drawString("Vivo", 289, 328);
        g.dispose();
        for (int y = 280; y <= 330; y++)
            for (int x = 275; x <= 390; x++)
                f.gray[y * w + x] = (byte) image.getRaster().getSample(x, y, 0);
        assertTrue("Genuine prose must not emit a bow", f.read().isEmpty());
    }
}
