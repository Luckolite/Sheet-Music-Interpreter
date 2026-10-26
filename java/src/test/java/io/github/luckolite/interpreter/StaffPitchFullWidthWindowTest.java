// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural crops preserve the detector's unsupported-span and invalid-input contracts. */
public class StaffPitchFullWidthWindowTest {
    @Test
    public void narrowCompleteRulesCannotInventAHorizontalTrack() {
        int width = 80, height = 200;
        float gap = 8, top = 60, bottom = 92;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 235);
        for (int x = 0; x < width; x++)
            for (int line = 0; line < 5; line++) gray[((int) top + line * 8) * width + x] = 45;
        byte[] before = gray.clone();
        assertNull(StaffPitchTrack.detect(gray, width, height, top, bottom, gap));
        assertNull(StaffPitchTrack.detectForSymbols(gray, width, height, top, bottom, gap));
        assertNull(StaffPitchTrack.detectForMeasures(gray, width, height, top, bottom, gap));
        assertArrayEquals(before, gray);
    }

    @Test
    public void invalidRastersKeepTheirExistingRefusalsAndExceptions() {
        assertNull(StaffPitchTrack.detect(null, 80, 200, 60, 108, 12));
        assertThrows(
                ArrayIndexOutOfBoundsException.class,
                () -> StaffPitchTrack.detect(new byte[1], 80, 200, 60, 108, 12));
        assertThrows(
                NegativeArraySizeException.class,
                () -> StaffPitchTrack.detect(new byte[0], -1, 200, 60, 108, 12));
    }
}
