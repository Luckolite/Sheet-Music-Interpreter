// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import java.util.zip.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original Bravura OFL numeral/Cambria Italic suffix from MusicNumeralOctaveWordTest, positioned independently. */
public class PrintedOctaveRoundingTest {
    static final int W = 500, H = 320;

    byte[] page(boolean mark, boolean dashes) throws Exception {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        if (mark)
            try (var in =
                    new GZIPInputStream(
                            new ByteArrayInputStream(
                                    Base64.getDecoder()
                                            .decode(
                                                    "H4sIAAAAAAAC/3XSPUibURQG4DdG1BitWkgCVid/0IpE6BDxb0iXLnWolsaiIoI0IEEHJ1sVKUIlEDVb1ExFhyAUbIYWxYKIg7VLNxWhthDEIEkGY2ixr+feZPxyhnvPd87zHS6XS6pIB1+0PbC5ho6ZL1YdKBkP+50wefOITaD4h+yZFmCNtCLDuB0R/p2rqegP+aRzVgK81nYR6CT9uCAD1gQ9tp0bH0LSmAcwo0kIqCQ/4Yh8NckIvlKWn9IYEPJMkwmggTzGZ149vGTnEykFyu9kHRaCsCQpux53hXVOvGfcpEY/f6r+XVCk4AP/vQQeJ6VgWThtuZVhH8mY5Z0isVJl4GoHuv6oQoPP/Y08xBbpNUf1EaaRi8Cd/nZXjct6XTyWehvEd1XZb8sJ00H2nkYab9S2UtUU3cCjBJN9gMNTqE19xuhm0x3S+cV9uzZ+I7Ik8w9l/92sSJ8RcQI9OonXCnEaESswm82WhXQbkTpgMZutC5kyIh5gIJuNAhXnRuSkHGZ1XO6aUbRn/J6i1Sh6sxYaLEDrl3yvMh3obS2ztQ9u/88D7gG11Xcl3wIAAA==")))) {
                byte[] g = in.readAllBytes();
                for (int y = 0; y < 21; y++) System.arraycopy(g, y * 35, p, (114 + y) * W + 70, 35);
            }
        if (dashes)
            for (int x = 111; x < 300; x++)
                if ((x - 111) % 14 < 7) {
                    p[119 * W + x] = 0;
                    p[120 * W + x] = 0;
                }
        return p;
    }

    List<ScoreNoteEvent> run(boolean mark, boolean dashes, float top, boolean ocr)
            throws Exception {
        var staff = new PlayingTechniqueDetector.Staff(top, top + 72, 18, 0, 1);
        var word = new PlayingTechniqueDetector.Word("8va", 70f / W, 114f / H, 105f / W, 135f / H);
        var notes =
                List.of(
                        new ScoreNoteEvent(0, .4f, 2, 0, 1, (top + 40) / H, false, 0, 0, 1, 1),
                        new ScoreNoteEvent(0, .8f, 2, 0, 1, (top + 40) / H, false, 0, 0, 1, 1));
        return OctaveMarkDetector.apply(
                ocr ? List.of(word) : List.of(),
                List.of(staff),
                List.of(new MeasureRegion(0, 1, 0, 1)),
                notes,
                page(mark, dashes),
                W,
                H);
    }

    @Test
    public void printedDashedMarkUsesIntegerClearance() throws Exception {
        var n = run(true, true, 139, false);
        assertEquals(1, n.get(0).octaveShift());
        assertEquals(0, n.get(1).octaveShift());
    }

    @Test
    public void ocrOnlyMarkKeepsStrictClearance() throws Exception {
        assertEquals(0, run(false, true, 139, true).get(0).octaveShift());
    }

    @Test
    public void noDashDoesNotUseRoundedClearance() throws Exception {
        assertEquals(0, run(true, false, 139, false).get(0).octaveShift());
    }

    @Test
    public void insufficientSeparationStaysRejected() throws Exception {
        assertEquals(0, run(true, true, 136, false).get(0).octaveShift());
    }

    @Test
    public void ordinarySpacingStillWorks() throws Exception {
        assertEquals(1, run(true, true, 160, false).get(0).octaveShift());
    }
}
