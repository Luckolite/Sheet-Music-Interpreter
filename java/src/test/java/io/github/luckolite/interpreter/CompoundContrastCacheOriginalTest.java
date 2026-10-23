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
 * Eight photometric, cache, resource, and preservation controls.
 */
public class CompoundContrastCacheOriginalTest {
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

    private static final Class<?> CC, GLYPH;
    private static final java.lang.reflect.Constructor<?> CTOR, GCTOR;
    private static final java.lang.reflect.Method CONTRAST, OLD;

    static {
        try {
            CC =
                    Class.forName(
                            "io.github.luckolite.interpreter.NoteArticulationDetector$CompoundClaims");
            GLYPH = Class.forName("io.github.luckolite.interpreter.NoteArticulationDetector$Glyph");
            CTOR = CC.getDeclaredConstructor(int.class);
            CTOR.setAccessible(true);
            GCTOR =
                    GLYPH.getDeclaredConstructor(
                            int.class, int.class, int.class, int.class, int.class, int[].class);
            GCTOR.setAccessible(true);
            // The broad caller test remains executable against R5; new private API is lazy.
            OLD =
                    NoteArticulationDetector.class.getDeclaredMethod(
                            "printedBodyContrast", GLYPH, byte[].class, int.class, int.class);
            OLD.setAccessible(true);
            java.lang.reflect.Method m = null;
            try {
                m =
                        CC.getDeclaredMethod(
                                "compoundContrast", GLYPH, byte[].class, int.class, int.class);
                m.setAccessible(true);
            } catch (NoSuchMethodException oldBaseline) {
            }
            CONTRAST = m;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Object claims() throws Exception {
        return CTOR.newInstance(2);
    }

    private static long number(Object c, String name) throws Exception {
        var f = CC.getDeclaredField(name);
        f.setAccessible(true);
        return ((Number) f.get(c)).longValue();
    }

    private static void limit(Object c, String name, long value) throws Exception {
        var f = CC.getDeclaredField(name);
        f.setAccessible(true);
        f.setLong(c, value);
    }

    private static Object body(int width, int count) throws Exception {
        int[] pixels = new int[count];
        for (int n = 0; n < count; n++) pixels[n] = (25 + n / 8) * width + 30 + n % 8;
        return GCTOR.newInstance(30, 25, 37, 25 + (count - 1) / 8, count, pixels);
    }

    private static boolean fresh(Object c, Object g, byte[] gray, int width, int height)
            throws Exception {
        return (Boolean) CONTRAST.invoke(c, g, gray, width, height);
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

    private static void broad(int columns, boolean joint) throws Exception {
        var image = paper();
        List<NoteArticulationDetector.Anchor> notes = new ArrayList<>();
        List<Integer> expected = new ArrayList<>();
        for (int row = 0; row < 2; row++)
            for (int col = 0; col < columns; col++) {
                int x = columns == 2 ? 280 + 600 * col : 100 + 300 * col, y = row * 600;
                glyphAt(image, 0xe4b4, 520 + y, x);
                notes.add(new NoteArticulationDetector.Anchor(x, 425 + y, 18.25f, row * 2));
                notes.add(new NoteArticulationDetector.Anchor(x, 590 + y, 18.25f, row * 2 + 1));
                expected.add(joint ? 0 : 1);
                expected.add(joint ? 9 : 8);
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
    public void broadFourCompoundsResolveWithOrdinaryNoteControls() throws Exception {
        broad(2, true);
    }

    @Test
    public void largerPageExhaustionPreservesEveryLegacyAssignment() throws Exception {
        broad(4, false);
    }

    @Test
    public void histogramMatchesUnchangedSorterAcrossAll8BitPapersAndRandomMedians()
            throws Exception {
        java.util.Random random = new java.util.Random(361);
        int width = 96, height = 80;
        for (int test = 0; test < 768; test++) {
            byte[] gray = new byte[width * height];
            int paper = test % 256;
            Arrays.fill(gray, (byte) paper);
            if (test >= 256)
                for (int at = 0; at < gray.length; at++)
                    if (random.nextInt(4) == 0) gray[at] = (byte) random.nextInt(256);
            int count = test % 2 == 0 ? 56 : 49;
            Object g = body(width, count);
            for (int n = 0; n < count; n++)
                gray[(25 + n / 8) * width + 30 + n % 8] =
                        (byte)
                                (test < 256
                                        ? (n % 3 == 0
                                                ? Math.max(0, paper - 25)
                                                : Math.max(0, paper - 8))
                                        : random.nextInt(256));
            byte[] before = gray.clone();
            boolean expected = (Boolean) OLD.invoke(null, g, gray, width, height);
            assertEquals("case" + test, expected, fresh(claims(), g, gray, width, height));
            assertArrayEquals(before, gray);
        }
    }

    @Test
    public void exactIdentityCacheAvoidsRepeatedWindowWorkButDebitsLookup() throws Exception {
        byte[] gray = new byte[96 * 80];
        Arrays.fill(gray, (byte) 255);
        for (int y = 25; y <= 31; y++) for (int x = 30; x <= 37; x++) gray[y * 96 + x] = 0;
        Object c = claims(), g = body(96, 56);
        assertTrue(fresh(c, g, gray, 96, 80));
        long w = number(c, "work"), b = number(c, "bytes");
        assertTrue(fresh(c, g, gray, 96, 80));
        assertEquals(2, w - number(c, "work"));
        assertEquals(b, number(c, "bytes"));
        assertEquals(1, number(c, "contrastCount"));
        assertTrue(fresh(c, body(96, 56), gray, 96, 80));
        assertEquals(2, number(c, "contrastCount"));
    }

    @Test
    public void otherRawSnapshotCannotBorrowCachedProof() throws Exception {
        byte[] gray = new byte[96 * 80];
        Arrays.fill(gray, (byte) 255);
        for (int y = 25; y <= 31; y++) for (int x = 30; x <= 37; x++) gray[y * 96 + x] = 0;
        Object c = claims(), g = body(96, 56);
        assertTrue(fresh(c, g, gray, 96, 80));
        assertFalse(fresh(c, g, gray.clone(), 96, 80));
    }

    @Test
    public void debitBoundaryIsExactAndOneMissingUnitAbstains() throws Exception {
        byte[] gray = new byte[96 * 80];
        Arrays.fill(gray, (byte) 255);
        for (int y = 25; y <= 31; y++) for (int x = 30; x <= 37; x++) gray[y * 96 + x] = 0;
        Object g = body(96, 56); // padding4; rectangle22..45? actual x26..41 andy21..35 =>16*15.
        long required = 1 + 4L * (16 * 15) + 4 * 56 + 2048 + 16;
        Object exact = claims();
        limit(exact, "work", required);
        assertTrue(fresh(exact, g, gray, 96, 80));
        assertEquals(0, number(exact, "work"));
        Object shortByOne = claims();
        limit(shortByOne, "work", required - 1);
        assertFalse(fresh(shortByOne, g, gray, 96, 80));
        assertEquals(0, number(shortByOne, "contrastCount"));
    }

    @Test
    public void storageRefusesBeforeHistogramsOrCacheEntry() throws Exception {
        byte[] gray = new byte[96 * 80];
        Arrays.fill(gray, (byte) 255);
        Object c = claims();
        limit(c, "bytes", 2111);
        assertFalse(fresh(c, body(96, 56), gray, 96, 80));
        assertEquals(0, number(c, "contrastCount"));
        assertEquals(2111, number(c, "bytes"));
    }

    @Test
    public void unrelatedGenuineAccentContributionCannotBeErased() throws Exception {
        var p = compound();
        glyph(p, 0xe4a1, 380);
        check(p, 1, 9);
    }
}
