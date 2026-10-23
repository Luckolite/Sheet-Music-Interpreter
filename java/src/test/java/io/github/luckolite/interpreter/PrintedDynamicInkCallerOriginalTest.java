// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete detector call, not a direct helper-only contract. */
public final class PrintedDynamicInkCallerOriginalTest {
    static ScoreDynamicChange detect(byte[] raw, List<ScoreNoteEvent> notes) {
        var ds =
                ScoreDynamicsDetector.detectDirectionCore(
                                List.of(PrintedDynamicInkColumnOriginalTest.WORD),
                                List.of(PrintedDynamicInkColumnOriginalTest.STAFF),
                                PrintedDynamicInkColumnOriginalTest.BARS,
                                notes,
                                raw,
                                300,
                                240,
                                raw,
                                false)
                        .changes()
                        .stream()
                        .filter(d -> d.direction() == 0)
                        .toList();
        assertEquals(1, ds.size());
        return ds.get(0);
    }

    @Test
    public void completeRawItalicBodyReachesTheProductionDetector() {
        assertEquals(
                .49f,
                detect(
                                PrintedDynamicInkColumnOriginalTest.raw(),
                                List.of(PrintedDynamicInkColumnOriginalTest.note(.49f, 0, 1)))
                        .positionInMeasure(),
                0);
    }

    @Test
    public void noRawEvidenceRetainsTheEstablishedCoordinate() {
        assertEquals(
                .4f,
                detect(null, List.of(PrintedDynamicInkColumnOriginalTest.note(.49f, 0, 1)))
                        .positionInMeasure(),
                0);
    }

    @Test
    public void ordinaryResolvedColumnKeepsPrecedence() {
        assertEquals(
                .4f,
                detect(
                                PrintedDynamicInkColumnOriginalTest.raw(),
                                List.of(
                                        PrintedDynamicInkColumnOriginalTest.note(.4f, 0, 1),
                                        PrintedDynamicInkColumnOriginalTest.note(.49f, 0, 1)))
                        .positionInMeasure(),
                0);
    }
}
