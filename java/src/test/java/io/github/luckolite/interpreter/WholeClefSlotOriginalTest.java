// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.lang.reflect.*;
import org.junit.Test;

/** Independent OFL complete-G engravings exercise the actual clef attribution and reset. */
public class WholeClefSlotOriginalTest {
    static final int W = 500, H = 300;

    static class Page {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];
        final java.util.List<Object> glyphs = new ArrayList<>();
        final Constructor<?> component, staffCtor, detected;
        final Class<?> root = OmrScoreInterpreter.class, ct, st, dt;

        Page() throws Exception {
            Arrays.fill(gray, (byte) 255);
            ct = Class.forName(root.getName() + "$Component");
            st = Class.forName(root.getName() + "$Staff");
            dt = Class.forName(root.getName() + "$DetectedNote");
            component = ct.getDeclaredConstructors()[0];
            staffCtor = st.getDeclaredConstructors()[0];
            detected = dt.getDeclaredConstructors()[0];
            component.setAccessible(true);
            staffCtor.setAccessible(true);
            detected.setAccessible(true);
        }

        int[] glyph(String text, int left, int top, boolean clef) throws Exception {
            var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);
            g.setColor(Color.BLACK);
            // SMuFL music em is four staff spaces: whole G uses64px at gap16.
            var font =
                    Font.createFont(
                                    Font.TRUETYPE_FONT,
                                    OriginalWideClefEightTest.font("Bravura.otf").toFile())
                            .deriveFont(clef ? 64f : 48f);
            var v = font.createGlyphVector(g.getFontRenderContext(), text);
            var bounds = v.getPixelBounds(null, 0, 0);
            g.drawGlyphVector(v, left - bounds.x, top - bounds.y);
            g.dispose();
            int l = W, r = -1, t = H, b = -1, n = 0;
            long sx = 0, sy = 0;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int raw = image.getRGB(x, y) & 255;
                    if (raw > 175) continue;
                    gray[y * W + x] = (byte) raw;
                    labels[y * W + x] = 3;
                    l = Math.min(l, x);
                    r = Math.max(r, x);
                    t = Math.min(t, y);
                    b = Math.max(b, y);
                    n++;
                    sx += x;
                    sy += y;
                }
            if (clef)
                glyphs.add(component.newInstance(n, l, r, t, b, sx / (float) n, sy / (float) n));
            return new int[] {l, t, r, b, top - bounds.y};
        }

        int[] read(boolean eight, boolean remote, boolean reset) throws Exception {
            int[] body = glyph("\uE050", 75, 80, true);
            assertTrue(
                    "whole G ink",
                    body[3] - body[1] + 1 >= 16 * 4.8f && body[3] - body[1] + 1 <= 16 * 8.8f);
            if (eight)
                glyph("\uE088", remote ? 20 : body[0] + (body[2] - body[0] - 18) / 2, 54, false);
            if (reset) glyph("\uE050", 300, 80, true);
            // G clef origin is G4, the second staff rule from the bottom.
            int staffTop = body[4] - 3 * 16,
                    staffBottom = body[4] + 16,
                    headY = staffBottom - 2 * 16;
            for (int line = 0; line < 5; line++)
                for (int x = 50; x < 470; x++) {
                    gray[(staffTop + line * 16) * W + x] = 0;
                    labels[(staffTop + line * 16) * W + x] = 4;
                }
            Object staff = staffCtor.newInstance((float) staffTop, (float) staffBottom, 16f);
            var notes = new ArrayList<Object>();
            for (int x : reset ? new int[] {220, 420} : new int[] {220}) {
                for (int y = headY - 5; y <= headY + 5; y++)
                    for (int xx = x - 7; xx <= x + 7; xx++)
                        if ((xx - x) * (xx - x) / 49.0 + (y - headY) * (y - headY) / 25.0 <= 1) {
                            gray[y * W + xx] = 0;
                            labels[y * W + xx] = 2;
                        }
                Object head =
                        component.newInstance(
                                100, x - 7, x + 7, headY - 5, headY + 5, (float) x, (float) headY);
                var event =
                        new ScoreNoteEvent(
                                0, x / (float) W, 4, 0, 1, headY / (float) H, false, 0, 0);
                notes.add(detected.newInstance(event, head, 16f));
            }
            byte[] saved = gray.clone(), savedLabels = labels.clone();
            var m =
                    root.getDeclaredMethod(
                            "applyPrintedClefs",
                            java.util.List.class,
                            java.util.List.class,
                            java.util.List.class,
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class);
            m.setAccessible(true);
            var result =
                    (java.util.List<?>)
                            m.invoke(
                                    null,
                                    notes,
                                    java.util.List.of(staff),
                                    glyphs,
                                    labels,
                                    gray,
                                    W,
                                    H);
            var e = dt.getDeclaredMethod("event");
            e.setAccessible(true);
            int[] out = new int[result.size()];
            for (int i = 0; i < out.length; i++)
                out[i] = ((ScoreNoteEvent) e.invoke(result.get(i))).clefBottomDiatonic();
            assertArrayEquals(saved, gray);
            assertArrayEquals(savedLabels, labels);
            return out;
        }
    }

    @Test
    public void wholePrintedGWithWideEightRaisesNote() throws Exception {
        assertArrayEquals(new int[] {37}, new Page().read(true, false, false));
    }

    @Test
    public void plainGMidBarResetsRaisedClef() throws Exception {
        assertArrayEquals(new int[] {37, 30}, new Page().read(true, false, true));
    }

    @Test
    public void ordinaryWholeGStaysUnshifted() throws Exception {
        assertArrayEquals(new int[] {30}, new Page().read(false, false, false));
    }

    @Test
    public void separateFingeringEightOutsideClefSlotDoesNotShift() throws Exception {
        assertArrayEquals(new int[] {30}, new Page().read(true, true, false));
    }
}
