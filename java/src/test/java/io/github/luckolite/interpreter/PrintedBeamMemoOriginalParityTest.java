// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original actual-API geometry and reviewed122 resource expectations; procedural rasters. */
public final class PrintedBeamMemoOriginalParityTest {
    @Test
    public void originalGeometryMasksAndReviewedRelativePaperDebitsRemainExact() throws Exception {
        var actual = PrintedBeamMemoOriginals.corpus();
        assertEquals(624, actual.cases());
        assertEquals(223, actual.positives());
        assertEquals(85, actual.memoPositives());
        assertEquals(480, actual.defaultCases());
        assertEquals(PrintedBeamMemoOriginals.GEOMETRY_DIGEST, actual.geometry());
        assertEquals(PrintedBeamMemoOriginals.DEFAULT_DEBIT_DIGEST, actual.defaultDebits());
    }

    @Test
    public void relativePaper122DefaultRouteChangesOnlyDeclaredSectionPrepayment()
            throws Exception {
        for (float gap : new float[] {14.25f, 64})
            for (int shade : new int[] {165, 200}) {
                var input = PrintedBeamMemoOriginals.draw(gap, shade, 4, 1);
                var answer = PrintedBeamMemoOriginals.run(input, "at", 8_000_000, 100_000_000);
                assertFalse(answer.refused());
                // Independently reviewed old route rows, reproduced by only restoring
                // the two preserved enclosing-paper redirects. Each row invokes exactly
                // two enclosing sections, whose declared debit changed12r+48 ->16r+56.
                long oldRaw = gap == 14.25f ? 3656 : 63420;
                long oldPage = gap == 14.25f ? 1992 : 8494;
                long radius = Math.max(2, (int) Math.ceil(gap * .75));
                long declared = 2 * (4 * radius + 8);
                assertEquals(oldRaw + declared, answer.raw());
                assertEquals(oldPage + declared, answer.page());
            }
    }

    @Test
    public void unaffectedDefaultRoutesRetainHistoricalDebits() throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        int unchanged = 0, reviewed = 0;
        for (float gap : new float[] {3, 14.25f, 64})
            for (int shade : new int[] {164, 165, 200, 230})
                for (int style = 0; style < 5; style++)
                    for (int direction : new int[] {-1, 1})
                        for (String route : new String[] {"atNull", "at", "strict", "allFalse"}) {
                            var input = PrintedBeamMemoOriginals.draw(gap, shade, style, direction);
                            var answer =
                                    PrintedBeamMemoOriginals.run(
                                            input, route, 8_000_000, 100_000_000);
                            assertFalse(answer.refused());
                            boolean changed =
                                    (gap == 14.25f || gap == 64)
                                            && (shade == 165 || shade == 200)
                                            && style == 4
                                            && direction == 1
                                            && route.equals("at");
                            long delta = 0;
                            if (changed) {
                                reviewed++;
                                delta = 2 * (4 * Math.max(2, (int) Math.ceil(gap * .75)) + 8);
                            } else unchanged++;
                            String key =
                                    gap + "/" + shade + "/" + style + "/" + direction + "/" + route;
                            PrintedBeamMemoOriginals.update(
                                    digest,
                                    key
                                            + "\t"
                                            + answer.refused()
                                            + "\t"
                                            + (answer.raw() - delta)
                                            + "\t"
                                            + (answer.page() - delta)
                                            + "\n");
                        }
        assertEquals(476, unchanged);
        assertEquals(4, reviewed);
        assertEquals(
                PrintedBeamMemoOriginals.PRE_RELATIVE_PAPER_122_DEFAULT_DEBIT_DIGEST,
                PrintedBeamMemoOriginals.hex(digest.digest()));
    }
}
