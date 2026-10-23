// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original Bravura OFL compound E4B4/E4B5, accent E4A1 and tenuto E4A4
 * rendered at82px with AA before any candidate. Glyph pixel crops only;
 * no private score pixels. Bravura-OFL.txt already accompanies public assets.
 * Four complete-assignment and ambiguity controls.
 */
public class CompoundNoopOwnershipOriginalTest {
    private static final int W = 1280, H = 1280;

    private record Raster(int dx, int dy, int width, int height, String gzip) {}

    private static Raster raster(int code) {
        return switch (code) {
            case 0xe4b4 ->
                    new Raster(
                            -14,
                            -29,
                            29,
                            29,
                            "H4sIAAAAAAAC//v/+9TX/zhBJAN3xPofOCQFGICAL3brL2ySU4UYwEAwedcfTNlfW+L4IfKimQf+Ysr/2BjFA5GXzDv6D1P++9owLoi8bMlpLLZ/XRHIAZFXqryARf7TYm82iLx6/TUs8u/muDBD5PW2Y/PdUzmIrAOm1Ofl/owQyUQ0mS8rgzghMkLJO1E8/W1NKNQ7AgnbUILzx4ZIaEDwx235iRKE2+L5IDJ8MZtQZP7sShaEyPBEbUCNvKdZYhAZ7vC139FdLg+W4QpdjS1BGDEwcAav/II9JZyNX/75/2ACpYoi2IGowaFfrAw4QeoPFtySyf+TeHBIMcrsAQAAKRc7SQMAAA==");
            case 0xe4b5 ->
                    new Raster(
                            -14,
                            0,
                            29,
                            29,
                            "H4sIAAAAAAAC//ufwsuAHTDK7vjBwoATJP5ixS2Z/r9ARgA7ENQ58n8gwNG4qS9wSuowMDA7z3qDXVIO7CUWj/nvsUi+rFCEeJnNd8knLPKnSiDaGTiCVn7FlP53rEAaIs8dvu47FvlD2eIQed6YzT8x5f/sTROGyAsk7viNKf97ewI/RF44bR8W5/3YEMoEkc/A5vWHshBJewyZx32WjBA5zbWoMk8mWkNl1Gouocg8n2ILlVGuvIAi83qGI9QZiuVnUWTeznWDJkL5klMoMh8XeUFToEzhcTTXTeCAyEjlHf2H4XRBkIxEzqG/2HycxiCWuf8vjmTy5/QfnEkIAODL6iZJAwAA");
            case 0xe4a1 ->
                    new Raster(
                            -14,
                            0,
                            28,
                            20,
                            "H4sIAAAAAAAC/5sk7Lvk03/sQIKBgYEjaOVXbHIBDGDAHb7uO6bkgQwxiDRvzOafGLJ/9qQKQ6QFEnf8xpD+vSNRACItnLb3D4b0z80xvBBp8exD/zCkv68L54JISxccx3TZ1xUB7BBp+bInmNIf50Kt9saUez2DHyLngSbxZpYLM0RGpvAessS7ue4sEAmpvCPITv2w0JsVIiGRffAvksSX5f5Q54lm7EeW+LY6lBMiIZK2BzlQ/m2K4oFICCXvRAtMf2goJ2z7heEbKaAEf9yWn9jido5w9MYfONIEAHhwlqQwAgAA");
            case 0xe4a4 ->
                    new Raster(
                            -14,
                            -4,
                            29,
                            4,
                            "H4sIAAAAAAAC//tfqiiCHYgaHPrFyoATpP5gwS2Z/D+JB4cUo8weAFqEGWN0AAAA");
            default -> throw new IllegalArgumentException("glyph");
        };
    }

    private static BufferedImage paper() {
        var b = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Arrays.fill(((DataBufferByte) b.getRaster().getDataBuffer()).getData(), (byte) 255);
        return b;
    }

    private static void glyph(BufferedImage b, int code, int baseline) throws Exception {
        var r = raster(code);
        byte[] pixels;
        try (var in =
                new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(r.gzip)))) {
            pixels = in.readAllBytes();
        }
        assertEquals(r.width * r.height, pixels.length);
        byte[] page = ((DataBufferByte) b.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < r.height; y++)
            for (int x = 0; x < r.width; x++) {
                int at = (baseline + r.dy + y) * W + 480 + r.dx + x;
                page[at] = (byte) Math.min(page[at] & 255, pixels[y * r.width + x] & 255);
            }
    }

    private static BufferedImage compound() throws Exception {
        var p = paper();
        glyph(p, 0xe4b4, 520);
        return p;
    }

    private static void notation(BufferedImage b) {
        var g = b.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        for (int top : new int[] {368, 560})
            for (int i = 0; i < 5; i++) g.drawLine(360, top + 18 * i, 620, top + 18 * i);
        g.fillOval(471, 420, 18, 11);
        g.drawLine(488, 425, 488, 374);
        g.drawArc(488, 375, 20, 35, -90, 180);
        g.drawArc(440, 428, 40, 12, 180, 180);
        g.fillOval(471, 585, 18, 11);
        g.drawLine(472, 590, 472, 638);
        g.dispose();
    }

    private static List<NoteArticulationDetector.Anchor> anchors() {
        return List.of(
                new NoteArticulationDetector.Anchor(480, 425, 18.25f, 0),
                new NoteArticulationDetector.Anchor(480, 590, 18.25f, 1));
    }

    private static void check(BufferedImage b, int... expected) {
        check(b, anchors(), expected);
    }

    private static void check(
            BufferedImage b, List<NoteArticulationDetector.Anchor> notes, int... expected) {
        byte[] gray = ((DataBufferByte) b.getRaster().getDataBuffer()).getData().clone(),
                before = gray.clone();
        byte[] labels = new byte[gray.length];
        var previous = List.copyOf(notes);
        assertArrayEquals(expected, NoteArticulationDetector.detect(labels, gray, W, H, notes));
        assertArrayEquals(before, gray);
        assertEquals(previous, notes);
        assertArrayEquals(new byte[labels.length], labels);
    }

    private static void glyphAt(BufferedImage b, int code, int baseline, int cx) throws Exception {
        var r = raster(code);
        byte[] pixels;
        try (var in =
                new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(r.gzip)))) {
            pixels = in.readAllBytes();
        }
        byte[] page = ((DataBufferByte) b.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < r.height; y++)
            for (int x = 0; x < r.width; x++)
                page[(baseline + r.dy + y) * W + cx + r.dx + x] =
                        (byte)
                                Math.min(
                                        page[(baseline + r.dy + y) * W + cx + r.dx + x] & 255,
                                        pixels[y * r.width + x] & 255);
    }

    @Test
    public void twentyCompoundsOneBorrowedAccentReachCompleteResolution() throws Exception {
        var image = paper();
        List<NoteArticulationDetector.Anchor> notes = new ArrayList<>();
        List<Integer> expected = new ArrayList<>();
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 5; col++) {
                int x = 150 + 240 * col, y = 250 * row;
                glyphAt(image, 0xe4b4, 170 + y, x);
                if (row == 0 && col == 0) {
                    notes.add(new NoteArticulationDetector.Anchor(x, 75 + y, 18.25f, row * 2));
                    expected.add(0);
                }
                notes.add(new NoteArticulationDetector.Anchor(x, 240 + y, 18.25f, row * 2 + 1));
                expected.add(9);
            }
        for (int n = 0; n < 24; n++) {
            notes.add(
                    new NoteArticulationDetector.Anchor(
                            n % 2 == 0 ? 40 : 1240, 50 + n * 45, 18.25f, 100 + n));
            expected.add(0);
        }
        check(image, List.copyOf(notes), expected.stream().mapToInt(Integer::intValue).toArray());
    }

    @Test
    public void unprovedAlreadyAssignedPartnerCannotBeBorrowedByNewPair() throws Exception {
        var p = paper();
        glyph(p, 0xe4a1, 492);
        glyph(p, 0xe4a4, 486);
        glyph(p, 0xe4a4, 524);
        byte[] gray = ((DataBufferByte) p.getRaster().getDataBuffer()).getData();
        // Separate full tenuto at raw155, with an attached AA foreign continuation
        // at raw185. No new authority may borrow the shared accent when this
        // existing same-owner potential partner remains unproved.
        for (int y = 486; y <= 488; y++) gray[y * W + 480] = (byte) 180;
        check(p, 9, 8);
    }

    @Test
    public void sameAttackShortcutRequiresIdenticalWholeChordMembership() throws Exception {
        Class<?> type =
                Class.forName(
                        "io.github.luckolite.interpreter.NoteArticulationDetector$CompoundClaims");
        var c = type.getDeclaredConstructor(int.class);
        c.setAccessible(true);
        Object instance = c.newInstance(3);
        var m = type.getDeclaredMethod("sameAssignment", List.class, int.class, int.class);
        m.setAccessible(true);
        var notes =
                List.of(
                        new NoteArticulationDetector.Anchor(480, 400, 18.25f, 0),
                        new NoteArticulationDetector.Anchor(486, 410, 18.25f, 0),
                        new NoteArticulationDetector.Anchor(493, 420, 18.25f, 0));
        assertTrue((Boolean) m.invoke(instance, notes, 0, 0));
        assertFalse((Boolean) m.invoke(instance, notes, 0, 1));
        assertFalse((Boolean) m.invoke(instance, notes, 1, 2));
    }

    @Test
    public void borrowedBelowCompoundStillRequiresCompleteMirroredProof() throws Exception {
        var p = paper();
        glyph(p, 0xe4b5, 481);
        check(
                p,
                List.of(
                        new NoteArticulationDetector.Anchor(480, 400, 18.25f, 0),
                        new NoteArticulationDetector.Anchor(480, 565, 18.25f, 1)),
                9,
                0);
    }
}
