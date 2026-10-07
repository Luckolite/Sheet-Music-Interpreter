// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** Original pairs of printed naturals sharing spine ink in a chord column. */
public class StackedNaturalChordTest extends TouchingChordAccidentalTest {
    @Test
    public void staggeredNaturalsSharingOneSpineCancelBothChordTones() {
        natural(220, 104);
        natural(211, 128);
        head(250, 104);
        head(250, 128);
        var n = notes();
        assertEquals(2, n.size());
        assertEquals(ScoreNoteEvent.ACCIDENTAL_NATURAL, accidental(n, 104));
        assertEquals(ScoreNoteEvent.ACCIDENTAL_NATURAL, accidental(n, 128));
    }

    @Test
    public void verticallyJoinedNaturalsCancelBothChordTones() {
        natural(220, 104);
        natural(220, 128);
        head(250, 104);
        head(250, 128);
        var n = notes();
        assertEquals(2, n.size());
        assertEquals(ScoreNoteEvent.ACCIDENTAL_NATURAL, accidental(n, 104));
        assertEquals(ScoreNoteEvent.ACCIDENTAL_NATURAL, accidental(n, 128));
    }

    @Test
    public void separateColumnsRemainReadable() {
        natural(200, 104);
        natural(220, 128);
        head(250, 104);
        head(250, 128);
        var n = notes();
        assertEquals(ScoreNoteEvent.ACCIDENTAL_NATURAL, accidental(n, 104));
        assertEquals(ScoreNoteEvent.ACCIDENTAL_NATURAL, accidental(n, 128));
    }

    @Test
    public void joinedColumnRequiresBothChordTones() {
        natural(220, 104);
        natural(211, 128);
        head(250, 104);
        assertNotEquals(ScoreNoteEvent.ACCIDENTAL_NATURAL, accidental(notes(), 104));
    }

    @Test
    public void missingConnectorCannotInventTwoNaturals() {
        natural(220, 104);
        natural(211, 128);
        for (int y = 93; y <= 96; y++)
            for (int x = 218; x <= 223; x++) {
                labels[y * W + x] = 0;
                gray[y * W + x] = (byte) 255;
            }
        head(250, 104);
        head(250, 128);
        assertNotEquals(ScoreNoteEvent.ACCIDENTAL_NATURAL, accidental(notes(), 104));
    }

    @Test
    public void overlappingSharpsRemainSharps() {
        sharp(220, 104);
        sharp(220, 128);
        head(250, 104);
        head(250, 128);
        var n = notes();
        assertNotEquals(ScoreNoteEvent.ACCIDENTAL_NATURAL, accidental(n, 104));
        assertNotEquals(ScoreNoteEvent.ACCIDENTAL_NATURAL, accidental(n, 128));
    }

    @Test
    public void sharedSpineRecoveryPreservesInputMasks() {
        natural(220, 104);
        natural(211, 128);
        head(250, 104);
        head(250, 128);
        var l = labels.clone();
        var g = gray.clone();
        notes();
        assertArrayEquals(l, labels);
        assertArrayEquals(g, gray);
    }

    @Test
    public void sharedSpineRecognitionScalesWithTheStaff() {
        natural(220, 104);
        natural(211, 128);
        head(250, 104);
        head(250, 128);
        for (int scale : new int[] {2, 3}) {
            int w = W * scale, h = H * scale;
            byte[] l = new byte[w * h], g = new byte[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    l[y * w + x] = labels[(y / scale) * W + x / scale];
                    g[y * w + x] = gray[(y / scale) * W + x / scale];
                }
            var centers =
                    StackedNaturalColumn.centers(
                            l,
                            w,
                            h,
                            206 * scale,
                            80 * scale,
                            227 * scale - 1,
                            153 * scale - 1,
                            (byte) 3,
                            G * scale);
            assertEquals(2, centers.length);
            assertEquals(104 * scale, centers[0], scale);
            assertEquals(128 * scale, centers[1], scale);
        }
    }
}
