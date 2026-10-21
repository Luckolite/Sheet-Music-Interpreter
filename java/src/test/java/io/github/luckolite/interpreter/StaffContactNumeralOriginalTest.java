// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Original logical-font digits retain their semantic identity under staff contact.
 * The four digit cases are preservation controls; all artwork is generated.
 */
public class StaffContactNumeralOriginalTest {
    private static final int W = 1600, H = 2000, GAP = 16;
    private static final int STAFF_LEFT = 30, STAFF_RIGHT = 400;
    // Existing caret apex=100, feet=112: an interior row and a feet-aligned row.
    private static final int[] FIRST_ROWS = {106, 112};

    private static BufferedImage symbol(boolean caret, int style) {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        var g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);
            g.setColor(Color.BLACK);
            if (caret) {
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D.Double path = new Path2D.Double();
                path.moveTo(121, 112);
                path.lineTo(128, 100);
                path.lineTo(135, 112);
                g.draw(path);
            } else {
                g.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(new Font("Serif", style, 24));
                g.drawString("5", 123, 115);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    @Test
    public void completeDigitsRemainNonMarcatoAtBothStaffContactPositions() {
        List<String> failures = new ArrayList<>();
        for (int firstRow : FIRST_ROWS) {
            for (int role = 0; role < 2; role++) {
                boolean caret = role == 2;
                int style = role == 1 ? Font.ITALIC : Font.PLAIN;
                String id = (caret ? "caret" : "Serif5-" + style) + "-staff-" + firstRow;
                try {
                    BufferedImage image = symbol(caret, style);
                    byte[] gray = new byte[W * H];
                    int support = 0, overlap = 0;
                    int left = W, right = -1, top = H, bottom = -1;
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            int sample = image.getRaster().getSample(x, y, 0);
                            gray[y * W + x] = (byte) sample;
                            if (sample < 255) {
                                support++;
                                left = Math.min(left, x);
                                right = Math.max(right, x);
                                top = Math.min(top, y);
                                bottom = Math.max(bottom, y);
                            }
                        }
                    }
                    // Literal five one-pixel rules, same gap and extent in every role.
                    // Black MIN composition only adds observed ink; no symbol is erased.
                    for (int line = 0; line < 5; line++) {
                        int y = firstRow + line * GAP;
                        for (int x = STAFF_LEFT; x <= STAFF_RIGHT; x++) {
                            int index = y * W + x;
                            if ((gray[index] & 255) < 255) overlap++;
                            gray[index] = 0;
                        }
                    }
                    boolean setup = support > 0 && overlap > 0;
                    int actual =
                            NoteArticulationDetector.detect(
                                    new byte[gray.length],
                                    gray,
                                    W,
                                    H,
                                    List.of(new NoteArticulationDetector.Anchor(128, 164, GAP, 0)))[
                                    0];
                    int expected = caret ? NoteArticulation.MARCATO : 0;
                    int actualMarcato = actual & NoteArticulation.MARCATO;
                    System.out.println(
                            "STAFF_CONTACT_CONTROL id="
                                    + id
                                    + " symbolPixels="
                                    + support
                                    + " symbolBox="
                                    + left
                                    + ","
                                    + top
                                    + ","
                                    + right
                                    + ","
                                    + bottom
                                    + " overlap="
                                    + overlap
                                    + " setup="
                                    + setup
                                    + " actualFlags="
                                    + actual
                                    + " actualMarcato="
                                    + actualMarcato
                                    + " expectedMarcato="
                                    + expected);
                    if (!setup) failures.add(id + ": missing literal symbol/staff overlap");
                    if (actualMarcato != expected)
                        failures.add(id + ": MARCATO " + actualMarcato + " != " + expected);
                } catch (RuntimeException ex) {
                    // Report every instance before aggregate failure; never turn an exception into
                    // PASS.
                    System.out.println("STAFF_CONTACT_CONTROL id=" + id + " exception=" + ex);
                    failures.add(id + ": " + ex);
                }
            }
        }
        assertTrue(String.join("; ", failures), failures.isEmpty());
    }
}
