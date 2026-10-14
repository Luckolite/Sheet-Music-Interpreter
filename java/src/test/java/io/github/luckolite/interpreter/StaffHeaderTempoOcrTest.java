// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaffHeaderTempoOcrTest {
    final int W = 640, H = 280;
    byte[] gray = new byte[W * H];

    public StaffHeaderTempoOcrTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void box(int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) gray[yy * W + xx] = 0;
    }

    void staff() {
        for (int y = 160; y <= 200; y += 10) box(20, y, 600, 1);
    }

    OcrText text(OcrText.Box box) {
        var e = new OcrText.Element("96", box, List.of());
        return new OcrText(
                "96",
                List.of(
                        new OcrText.Block(
                                "96", box, List.of(new OcrText.Line("96", box, List.of(e))))));
    }

    @Test
    public void noPrintedStaffMakesNoInferenceCall() throws Exception {
        box(80, 100, 20, 10);
        assertTrue(
                StaffHeaderTempoOcr.words(
                                gray,
                                W,
                                H,
                                (g, w, h) -> {
                                    fail("unproved staff");
                                    return null;
                                })
                        .isEmpty());
    }

    @Test
    public void emptyHeadersMakeNoInferenceCall() throws Exception {
        staff();
        assertTrue(
                StaffHeaderTempoOcr.words(
                                gray,
                                W,
                                H,
                                (g, w, h) -> {
                                    fail("empty header");
                                    return null;
                                })
                        .isEmpty());
    }

    @Test
    public void cropCoordinatesReturnToOriginalPage() throws Exception {
        staff();
        box(80, 100, 20, 10);
        var words =
                StaffHeaderTempoOcr.words(
                        gray,
                        W,
                        H,
                        (g, w, h) -> {
                            assertEquals(0, g[20 * w + 80]);
                            return text(new OcrText.Box(80, 20, 100, 30));
                        });
        assertEquals(1, words.size());
        assertEquals(80f / W, words.get(0).left(), .0001);
        assertEquals(100f / H, words.get(0).top(), .0001);
    }

    @Test
    public void overlappingWindowsRetainBoundaryEvidence() throws Exception {
        staff();
        box(300, 100, 20, 10);
        var words =
                StaffHeaderTempoOcr.words(
                        gray, W, H, (g, w, h) -> text(new OcrText.Box(1, 1, 20, 10)));
        assertEquals(2, words.size());
        assertTrue(words.get(1).left() > words.get(0).left());
    }

    @Test
    public void invalidOcrBoxesCannotEscapeCrop() throws Exception {
        staff();
        box(80, 100, 20, 10);
        assertTrue(
                StaffHeaderTempoOcr.words(
                                gray, W, H, (g, w, h) -> text(new OcrText.Box(-1, 0, w + 1, 20)))
                        .isEmpty());
    }

    @Test
    public void invalidRasterDoesNotCallReader() throws Exception {
        assertTrue(
                StaffHeaderTempoOcr.words(
                                new byte[2],
                                Integer.MAX_VALUE,
                                2,
                                (g, w, h) -> {
                                    fail();
                                    return null;
                                })
                        .isEmpty());
    }

    @Test
    public void readerReceivesOwnedCopy() throws Exception {
        staff();
        box(80, 100, 20, 10);
        var original = gray.clone();
        StaffHeaderTempoOcr.words(
                gray,
                W,
                H,
                (g, w, h) -> {
                    Arrays.fill(g, (byte) 0);
                    return null;
                });
        assertArrayEquals(original, gray);
    }
}
