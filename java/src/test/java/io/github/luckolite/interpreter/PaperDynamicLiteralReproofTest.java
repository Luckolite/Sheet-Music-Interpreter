// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PaperDynamicLiteralReproofTest {
    static OcrText reading(String value, PaperDynamicWord.Crop crop, int scale) {
        var seed = NativeEnglishPositiveTest.candidate("mp");
        var b =
                new OcrText.Box(
                        Math.round((seed.left() * 500 - crop.left()) * scale),
                        Math.round((seed.top() * 300 - crop.top()) * scale),
                        Math.round((seed.right() * 500 - crop.left()) * scale),
                        Math.round((seed.bottom() * 300 - crop.top()) * scale));
        var line = new OcrText.Line(value, b, List.of(new OcrText.Element(value, b, List.of())));
        return new OcrText(
                value,
                List.of(new OcrText.Block(value, b, List.of(line))),
                OcrText.Grouping.DETECTED_LINES);
    }

    @Test
    public void ownExactSeedReproofUsesActualAgreementExactlyOnceEachScale() throws Exception {
        var seed = NativeEnglishPositiveTest.candidate("mp");
        var calls = new ArrayList<Integer>();
        var p =
                PaperDynamicLiteralReproof.verify(
                        List.of(seed),
                        0,
                        seed,
                        (crop, scale) -> {
                            calls.add(scale);
                            return reading("mp", crop, scale);
                        },
                        NativeEnglishPositiveTest.page("mp"),
                        500,
                        300);
        assertNotNull(p);
        assertEquals(List.of(2, 3), calls);
        assertEquals("mp", p.word().text());
        assertSame(seed, p.exactSeed());
    }

    @Test
    public void competingEqualLiteralIsNotRemovedByIdentityShortcut() throws Exception {
        var seed = NativeEnglishPositiveTest.candidate("mp");
        var duplicate = NativeEnglishPositiveTest.candidate("mp");
        int[] calls = {0};
        assertNull(
                PaperDynamicLiteralReproof.verify(
                        List.of(seed, duplicate),
                        0,
                        seed,
                        (c, s) -> {
                            calls[0]++;
                            return reading("mp", c, s);
                        },
                        NativeEnglishPositiveTest.page("mp"),
                        500,
                        300));
        assertEquals(0, calls[0]);
    }

    @Test
    public void staleSeedGeometryCannotBeReproved() throws Exception {
        var seed = NativeEnglishPositiveTest.candidate("mp");
        var changed =
                new PlayingTechniqueDetector.Word(
                        "mp", seed.left() + .001f, seed.top(), seed.right(), seed.bottom());
        assertNull(
                PaperDynamicLiteralReproof.verify(
                        List.of(seed),
                        0,
                        changed,
                        (c, s) -> {
                            fail("no OCR for stale seed");
                            return null;
                        },
                        NativeEnglishPositiveTest.page("mp"),
                        500,
                        300));
    }

    @Test
    public void independentScalesDisagreeingAreRejected() throws Exception {
        var seed = NativeEnglishPositiveTest.candidate("mp");
        assertNull(
                PaperDynamicLiteralReproof.verify(
                        List.of(seed),
                        0,
                        seed,
                        (c, s) -> reading(s == 2 ? "mp" : "mf", c, s),
                        NativeEnglishPositiveTest.page("mp"),
                        500,
                        300));
    }

    @Test
    public void EnglishParserSuccessCannotBecomeLiteralDynamic() throws Exception {
        var seed = NativeEnglishPositiveTest.candidate("mp");
        assertNull(
                PaperDynamicLiteralReproof.verify(
                        List.of(seed),
                        0,
                        seed,
                        (c, s) -> reading("Flowing", c, s),
                        NativeEnglishPositiveTest.page("mp"),
                        500,
                        300));
    }

    @Test
    public void agreementOnPaperCannotInventLiteralInk() throws Exception {
        var seed = NativeEnglishPositiveTest.candidate("mp");
        assertNull(
                PaperDynamicLiteralReproof.verify(
                        List.of(seed),
                        0,
                        seed,
                        (c, s) -> reading("mp", c, s),
                        NativeEnglishPositiveTest.page(null),
                        500,
                        300));
    }
}
