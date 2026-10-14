// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class RepeatedCreditLetterConsensusTest {
    String read(String a, String b) {
        return RepeatedCreditLetterConsensus.restore(
                a, List.of(b, b, b), List.of(.99f, .99f, .99f));
    }

    @Test
    public void printedDoubleLetter() {
        assertEquals("Mira Bell, Leon Hart", read("Mira Bel, Leon Hart", "Mira Bell, Leon Hart"));
    }

    @Test
    public void internalDoubleLetter() {
        assertEquals("Anna Vale", read("Ana Vale", "Anna Vale"));
    }

    @Test
    public void unicode() {
        assertEquals("Élla Núñez", read("Éla Núñez", "Élla Núñez"));
    }

    @Test
    public void noDifferentName() {
        assertEquals("Mira Bel", read("Mira Bel", "Mira Ben"));
    }

    @Test
    public void noMissingNonRepeatedLetter() {
        assertEquals("Mira Be", read("Mira Be", "Mira Bel"));
    }

    @Test
    public void noPunctuationChange() {
        assertEquals("Mira Bel,", read("Mira Bel,", "Mira Bell"));
    }

    @Test
    public void noRepeatedNumber() {
        assertEquals("Opus 12", read("Opus 12", "Opus 122"));
    }

    @Test
    public void noTripleLetter() {
        assertEquals("Mira Bell", read("Mira Bell", "Mira Belll"));
    }

    @Test
    public void rejectLowConfidence() {
        assertEquals(
                "Ana Vale",
                RepeatedCreditLetterConsensus.restore(
                        "Ana Vale",
                        List.of("Anna Vale", "Anna Vale", "Anna Vale"),
                        List.of(.99f, .979f, .99f)));
    }

    @Test
    public void rejectDisagreement() {
        assertEquals(
                "Ana Vale",
                RepeatedCreditLetterConsensus.restore(
                        "Ana Vale",
                        List.of("Anna Vale", "Ana Vale", "Anna Vale"),
                        List.of(.99f, .99f, .99f)));
    }

    @Test
    public void rejectNan() {
        assertEquals(
                "Ana Vale",
                RepeatedCreditLetterConsensus.restore(
                        "Ana Vale",
                        List.of("Anna Vale", "Anna Vale", "Anna Vale"),
                        List.of(.99f, Float.NaN, .99f)));
    }

    @Test
    public void requireThree() {
        assertEquals(
                "Ana Vale",
                RepeatedCreditLetterConsensus.restore(
                        "Ana Vale", List.of("Anna Vale", "Anna Vale"), List.of(.99f, .99f)));
    }
}
