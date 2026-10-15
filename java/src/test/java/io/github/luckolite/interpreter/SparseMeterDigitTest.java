// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SparseMeterDigitTest {
    static String read(List<String> up, List<String> low, MeterDigitRecognizerTest.Model model)
            throws Exception {
        return MeterDigitRecognizer.readSparse(
                MeterDigitRecognizerTest.pixels(), 30, 44, 22, 10, up, low, model);
    }

    @Test
    public void complementaryDetectionsNeedSixStrictReadings() throws Exception {
        var m = new MeterDigitRecognizerTest.Model();
        assertEquals("7/4", read(List.of("7", "", ""), List.of("", "", "4"), m));
        assertEquals(6, m.calls);
    }

    @Test
    public void conflictingUpperAbstainsBeforeInference() throws Exception {
        var m = new MeterDigitRecognizerTest.Model();
        assertEquals("", read(List.of("7", "2"), List.of("4"), m));
        assertEquals(0, m.calls);
    }

    @Test
    public void conflictingLowerAbstainsBeforeInference() throws Exception {
        var m = new MeterDigitRecognizerTest.Model();
        assertEquals("", read(List.of("7"), List.of("4", "8"), m));
        assertEquals(0, m.calls);
    }

    @Test
    public void missingDetectorDigitAbstains() throws Exception {
        assertEquals("", read(List.of(""), List.of("4"), new MeterDigitRecognizerTest.Model()));
    }

    @Test
    public void strictRecognitionMustAgreeWithUpper() throws Exception {
        assertEquals("", read(List.of("2"), List.of("4"), new MeterDigitRecognizerTest.Model()));
    }

    @Test
    public void strictRecognitionMustAgreeWithLower() throws Exception {
        assertEquals("", read(List.of("7"), List.of("8"), new MeterDigitRecognizerTest.Model()));
    }

    @Test
    public void lowConfidenceStillAbstains() throws Exception {
        var m = new MeterDigitRecognizerTest.Model();
        m.confidence = .97f;
        assertEquals("", read(List.of("7"), List.of("4"), m));
    }

    @Test
    public void marginDisagreementStillAbstains() throws Exception {
        var m = new MeterDigitRecognizerTest.Model();
        m.text[1] = "2";
        assertEquals("", read(List.of("7"), List.of("4"), m));
    }

    @Test
    public void invalidTextIsNotIgnored() throws Exception {
        assertEquals(
                "", read(List.of("7", "x"), List.of("4"), new MeterDigitRecognizerTest.Model()));
    }
}
