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
 * Twelve cases are the same4+8 frozen originals, not additional duplicated controls.
 */
public class CompoundAccentTenutoOwnershipTest {
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

    @Test
    public void completeAboveCompoundKeepsOneLowerAttack() throws Exception {
        check(compound(), 0, 9);
    }

    @Test
    public void mirroredBelowCompoundKeepsUpperAttack() throws Exception {
        var p = paper();
        glyph(p, 0xe4b5, 481);
        check(p, 9, 0);
    }

    @Test
    public void wholeCompoundWithGenuineFlagAndTie() throws Exception {
        var p = compound();
        notation(p);
        check(p, 0, 9);
    }

    @Test
    public void isolatedBelowAccentStaysGenuine() throws Exception {
        var p = paper();
        glyph(p, 0xe4a1, 490);
        check(p, 1, 0);
    }

    @Test
    public void independentTenutoStaysGenuine() throws Exception {
        var p = paper();
        glyph(p, 0xe4a4, 520);
        check(p, 0, 8);
    }

    @Test
    public void unrelatedIndependentEligibleMarksKeepTheirOwners() throws Exception {
        var p = paper();
        glyph(p, 0xe4a1, 490);
        glyph(p, 0xe4a4, 550);
        check(p, 1, 8);
    }

    @Test
    public void genuineBelowAccentWithFlagTieIsNotRemoved() throws Exception {
        var p = paper();
        glyph(p, 0xe4a1, 490);
        notation(p);
        check(p, 1, 0);
    }

    @Test
    public void genuineFlagAndTieAloneAreNotArticulations() {
        var p = paper();
        notation(p);
        check(p, 0, 0);
    }

    @Test
    public void foreignDashWithinFullEnvelopePreventsNewClaim() throws Exception {
        var p = compound();
        var g = p.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(480, 513, 3, 1);
        g.dispose();
        check(p, 1, 8);
    }

    @Test
    public void foreignStemCrossingMembersIsNotCompound() throws Exception {
        var p = compound();
        var g = p.createGraphics();
        g.setColor(Color.BLACK);
        g.drawLine(480, 505, 480, 540);
        g.dispose();
        check(p, 0, 0);
    }

    @Test
    public void competingAboveAndBelowPartnersAbstain() throws Exception {
        var p = paper();
        glyph(p, 0xe4a1, 492);
        glyph(p, 0xe4a4, 486);
        glyph(p, 0xe4a4, 524);
        check(p, 9, 8);
    }

    @Test
    public void twoEligibleLowerPhysicalStaffOwnersAbstain() throws Exception {
        var p = compound();
        check(
                p,
                List.of(
                        new NoteArticulationDetector.Anchor(480, 425, 18.25f, 0),
                        new NoteArticulationDetector.Anchor(480, 590, 18.25f, 1),
                        new NoteArticulationDetector.Anchor(480, 590, 18.25f, 2)),
                1,
                8,
                0);
    }
}
