// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original faint sharp with its dark crossbars erroneously labelled as noteheads. */
public class FaintCrossbarOwnershipTest {
    static final int W = 640, H = 240;

    static final class Page {
        final byte[] gray = new byte[W * H], labels = new byte[W * H];

        Page(int shade, float lean, int noteY) {
            Arrays.fill(gray, (byte) 240);
            for (int y = 100; y <= 164; y += 16)
                for (int x = 20; x < 620; x++) {
                    gray[y * W + x] = 80;
                    labels[y * W + x] = 4;
                }
            for (int y = noteY - 6; y <= noteY + 6; y++)
                for (int x = 422; x <= 438; x++)
                    if ((x - 430) * (x - 430) / 64d + (y - noteY) * (y - noteY) / 36d <= 1) {
                        gray[y * W + x] = 0;
                        labels[y * W + x] = 2;
                    }
            for (int y = 84; y <= noteY; y++) {
                gray[y * W + 438] = 0;
                labels[y * W + 438] = 1;
            }
            // An earlier attack establishes that this is a local accidental,
            // rather than a key signature before the first note of the system.
            for (int y = 126; y <= 138; y++)
                for (int x = 342; x <= 358; x++)
                    if ((x - 350) * (x - 350) / 64d + (y - 132) * (y - 132) / 36d <= 1) {
                        gray[y * W + x] = 0;
                        labels[y * W + x] = 2;
                    }
            for (int y = 84; y <= 132; y++) {
                gray[y * W + 358] = 0;
                labels[y * W + 358] = 1;
            }
            for (int x : new int[] {395, 396, 403, 404})
                for (int y = 110; y <= 154; y++)
                    gray[y * W + x + Math.round((y - 132) * lean)] = (byte) shade;
            for (int cy : new int[] {127, 137})
                for (int y = cy - 2; y <= cy + 2; y++)
                    for (int x = 391; x <= 406; x++) {
                        int xx = x + Math.round((y - 132) * lean);
                        gray[y * W + xx] = 60;
                    }
            // The semantic head mask can be taller than the dark crossbar.
            for (int cy : new int[] {127, 137})
                for (int y = cy - 3; y <= cy + 3; y++)
                    for (int x = 394; x <= 405; x++)
                        labels[y * W + x + Math.round((y - 132) * lean)] = 2;
        }

        OmrScoreInterpreter.Analysis analyze() {
            return OmrScoreInterpreter.analyze(
                    labels,
                    gray,
                    W,
                    H,
                    List.of(new MeasureRegion(180f / W, 620f / W, 65f / H, 199f / H)));
        }
    }

    @Test
    public void faintCrossbarsBecomeOneOwnedAccidentalInsteadOfTwoNotes() {
        for (int shade : new int[] {215, 225, 235})
            for (float lean : new float[] {-.25f, 0, .25f}) {
                var result = new Page(shade, lean, 132).analyze();
                assertEquals("shade=" + shade + " lean=" + lean, 2, result.notes().size());
                assertEquals(
                        "shade=" + shade + " lean=" + lean,
                        ScoreNoteEvent.ACCIDENTAL_SHARP,
                        result.notes().get(1).writtenAccidental());
            }
    }

    @Test
    public void recoveredSharpDoesNotMoveToAdjacentPitch() {
        var result = new Page(225, -.25f, 148).analyze();
        assertEquals(2, result.notes().size());
        assertNotEquals(ScoreNoteEvent.ACCIDENTAL_SHARP, result.notes().get(1).writtenAccidental());
    }

    @Test
    public void inputPixelsRemainUnchanged() {
        var page = new Page(225, -.25f, 132);
        byte[] gray = page.gray.clone(), labels = page.labels.clone();
        page.analyze();
        assertArrayEquals(gray, page.gray);
        assertArrayEquals(labels, page.labels);
    }

    @Test
    public void zeroContrastShaftsCannotProveAnAccidental() {
        for (float lean : new float[] {-.25f, 0, .25f}) {
            var result = new Page(240, lean, 132).analyze();
            assertTrue(
                    result.notes().stream()
                            .noneMatch(
                                    n -> n.writtenAccidental() == ScoreNoteEvent.ACCIDENTAL_SHARP));
        }
    }

    @Test
    public void oneFaintShaftCannotProveAnAccidental() {
        for (float lean : new float[] {-.25f, 0, .25f}) {
            var page = new Page(225, lean, 132);
            for (int x : new int[] {403, 404})
                for (int y = 110; y <= 154; y++) {
                    int i = y * W + x + Math.round((y - 132) * lean);
                    if ((page.gray[i] & 255) == 225) page.gray[i] = (byte) 240;
                }
            assertTrue(
                    page.analyze().notes().stream()
                            .noneMatch(
                                    n -> n.writtenAccidental() == ScoreNoteEvent.ACCIDENTAL_SHARP));
        }
    }

    @Test
    public void twoSmallHeadsOnARealStemAreNotSharpCrossbars() throws Exception {
        var page = new Page(225, 0, 132);
        for (int y = 106; y <= 158; y++)
            for (int x = 383; x <= 416; x++) {
                page.gray[y * W + x] = 240 - 256;
                page.labels[y * W + x] = 0;
            }
        for (int y : new int[] {116, 132, 148})
            for (int x = 383; x <= 416; x++) {
                page.gray[y * W + x] = 80;
                page.labels[y * W + x] = 4;
            }
        for (int cy : new int[] {127, 137})
            for (int y = cy - 4; y <= cy + 4; y++)
                for (int x = 394; x <= 406; x++)
                    if ((x - 400) * (x - 400) / 36d + (y - cy) * (y - cy) / 16d <= 1) {
                        page.gray[y * W + x] = 40;
                        page.labels[y * W + x] = 2;
                    }
        for (int y = 110; y <= 154; y++) {
            page.gray[y * W + 406] = 40;
            page.labels[y * W + 406] = 1;
        }
        var components =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "findComponents", byte[].class, int.class, int.class, byte.class);
        components.setAccessible(true);
        var heads = components.invoke(null, page.labels, W, H, (byte) 2);
        var findStaffs =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "findStaffs", byte[].class, byte[].class, int.class, int.class, List.class);
        findStaffs.setAccessible(true);
        var staffs =
                findStaffs.invoke(
                        null,
                        page.labels,
                        page.gray,
                        W,
                        H,
                        List.of(new MeasureRegion(180f / W, 620f / W, 65f / H, 199f / H)));
        var reject =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "sharpCrossbarHeads",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        reject.setAccessible(true);
        assertTrue(((List<?>) reject.invoke(null, page.gray, W, H, heads, staffs)).isEmpty());
    }
}
