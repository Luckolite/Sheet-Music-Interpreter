// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently rendered Bravura OFL U+E522 at 33x40; no score pixels.
 * Fading the foot simulates incomplete photocopy ink without removing the printed f. */
public final class ForteRestFontRegressionTest {
    private static byte[] glyph() throws Exception {
        try (var in =
                new GZIPInputStream(
                        new ByteArrayInputStream(
                                Base64.getDecoder()
                                        .decode(
                                                "H4sIAAAAAAAC/3WTP2gUQRTG583cHkf8Q9aYs4nkuDu1sBACAbkUGlAwpBAkSsQqKmJhYSIWNoEUNpL2ghLExiLEmEYw2omNGAtF/IuihQFBxGjEZO9m5o3z5gTZ25lX7C77Pr75vW9mjMkW4o/75/d1xeUT96TxFT7ojjhsOzXWEQ0lnra+UgAQp7+uXo85zGcFjXEOjB9EOcIYEyNZg5u2z6Jnjef0FrX2vv68nf4fRfmQk8PxzADH7H9gdw1+K+RAsMV2wcpWIMEnbfDO3i2VyfU2gRoiY9gl0YrNBtpHmuAjkTF+VbfWQ0wtb+sW9SH3Xqd7/6smyKCoPfnVy9VqZSdzxcuVanXPXEqGlzpsRf8E9L1pOu2z9tLWGUJgMEzfr5I0BSol1eGWYKmhbGUp0ZQoBd71xz+BwXcROYjRQN+oCeEYbwf6uF50BOyN9gv0W3A59iYhhIVWCHUVWuKcmyFeCzHKXrfVg4FtMuoJd4wzoRX0pEOMv4cQzaA7KxdDBIg9dFxhWYcE7h7w3TLkoM8SY37Zs4duLPzdSQY1z4zqS2IJ1QtGF2IhM6OcKefjy02DN8jgSJbgEQjgfFHqQzkG/Gl2hDEX75R6nbd3fryZhT9gzwjwOXXB9vt9uzBKDsNNWQTR+cGX0TxAqb66UuOw47E3oo2BqOfaRHcu6vvpzxiT2f2bC6WTS78CEf8F/Vc8BygFAAA=")))) {
            byte[] result = in.readAllBytes();
            assertEquals(33 * 40, result.length);
            return result;
        }
    }

    private byte[] page(int top, int fadedRows) throws Exception {
        byte[] gray = new byte[420 * 300];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 10; x < 410; x++) gray[(100 + line * 16) * 420 + x] = 0;
        byte[] glyph = glyph();
        for (int y = 0; y < 40; y++)
            for (int x = 0; x < 33; x++) {
                int value = glyph[y * 33 + x] & 255;
                if (y >= 40 - fadedRows && value < 185) value = 185;
                gray[(top + y) * 420 + 190 + x] = (byte) value;
            }
        return gray;
    }

    private List<ScoreRestEvent> read(byte[] gray, boolean heldVoice) {
        var held =
                new ScoreNoteEvent(
                        0, .1f, 4, 0, 1, .4f, false, 0, 0, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, 4);
        return SixteenthRestDetector.detect(
                gray,
                420,
                300,
                List.of(new MeasureRegion(0, 1, .1f, .98f)),
                List.of(new SixteenthRestDetector.Staff(100, 164, 16, 0, 1)),
                heldVoice ? List.of(held) : List.of());
    }

    @Test
    public void fadedForteFootCannotAddSilenceBelowStaff() throws Exception {
        for (int fadedRows : new int[] {8, 10})
            assertTrue(
                    read(page(200, fadedRows), true).toString(),
                    read(page(200, fadedRows), true).isEmpty());
    }

    @Test
    public void deeperForteFootCannotAddSilenceBelowStaff() throws Exception {
        for (int fadedRows : new int[] {8, 10})
            assertTrue(
                    read(page(216, fadedRows), true).toString(),
                    read(page(216, fadedRows), true).isEmpty());
    }

    @Test
    public void darkCompleteForteRemainsSilent() throws Exception {
        assertTrue(read(page(200, 0), true).isEmpty());
    }

    @Test
    public void missingIndependentVoiceDoesNotAuthorizeRest() throws Exception {
        assertTrue(read(page(200, 8), false).isEmpty());
    }

    @Test
    public void sourceRasterRemainsUnchanged() throws Exception {
        byte[] gray = page(200, 8), before = gray.clone();
        read(gray, true);
        assertArrayEquals(before, gray);
    }
}
