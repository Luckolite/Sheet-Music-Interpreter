// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Original ordinary-beam and independent sustained-head notation with frozen complete outputs. */
@RunWith(Parameterized.class)
public final class CompleteOwnedBeamValueOriginalTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(
                new Object[][] {
                    {
                        "paperCounter",
                        "49ecbb6bdf134f85ac70122d8905ff10615ba6611b2ea4eabc1de595eeaf6b69",
                        2,
                        0d,
                        true
                    },
                    {
                        "paperCounterDown",
                        "08253b1104f5b24f1e9150eb9678212cda90285cad896426e82454bca48f30c5",
                        2,
                        0d,
                        true
                    },
                    {
                        "paperCounterSlope",
                        "f08801a4f7bbb92b2c3fffffbaead81f1bfaca24e18d2f9b94099b4a731f02a8",
                        2,
                        0d,
                        true
                    },
                    {
                        "paleCounter230",
                        "b7da2145628d9f85a7f9811309d31dab3f1c6c000b82ac2835eeaf1499bf6fe1",
                        2,
                        0d,
                        false
                    },
                    {
                        "paleCounter245",
                        "be6903251f2c626e5689c5046c3c0ed60d9fc468fa86c91fcafd2ec6623a811e",
                        2,
                        0d,
                        true
                    },
                    {
                        "sustainedHalfBesideForeignGroup",
                        "b1ad16b170fd918e85216c62ab805c2c3c26693dbb9df97313062a0706d4fb52",
                        0,
                        2d,
                        false
                    },
                    {
                        "sustainedWholeBesideForeignGroup",
                        "3c84a2230c25772b48a512d2b4a6b36b4811b0c77fa9d947ea4335a1a3b78f5e",
                        0,
                        4d,
                        false
                    }
                });
    }

    private final String name, expected;
    private final int beams;
    private final double base;
    private final boolean recoveredOpen;

    public CompleteOwnedBeamValueOriginalTest(
            String name, String expected, int beams, double base, boolean recoveredOpen) {
        this.name = name;
        this.expected = expected;
        this.beams = beams;
        this.base = base;
        this.recoveredOpen = recoveredOpen;
    }

    @Test
    public void completeOwnershipArbitratesAppearanceWithoutRewritingDetectorReceipt()
            throws Exception {
        var a = CompleteRailOriginalScene.scene(name);
        assertEquals(beams, a.notes().get(0).beamCount());
        assertEquals(base, a.notes().get(0).unbeamedDurationBeats(), 0);
        var producer =
                a.clockProducerSource().printedValues().stream()
                        .filter(v -> v.note() == 0)
                        .findFirst()
                        .orElseThrow();
        if (recoveredOpen) {
            assertEquals("OPEN_WITH_STEM_HALF", producer.detectorExit().toString());
            assertEquals(2d, producer.detectorValue(), 0);
            assertEquals(2, producer.detectorBeams());
            assertEquals(2, producer.finalBeams());
            assertEquals(0d, producer.finalValue(), 0);
            assertEquals(
                    List.of(
                            "COMPLETE_OWNED_ORDINARY_BEAM_VALUE_OVERRIDE",
                            "POST_DETECTOR_VALUE_OR_BEAM_CHANGED",
                            "FINAL_EVENT_VALUE_OR_BEAM_DIFFERS_FROM_DETECTOR"),
                    producer.admissions());
            var context =
                    a.clockContextSource().printedValues().stream()
                            .filter(v -> v.noteIndex() == 0)
                            .findFirst()
                            .orElseThrow();
            assertEquals(2, context.admissionFlags());
            assertEquals(2, context.beams());
            assertEquals(0d, context.baseBeats(), 0);
            var original =
                    a.clockProducerSource().localAccidentals().choices().stream()
                            .filter(v -> v.note() == 0)
                            .findFirst()
                            .orElseThrow()
                            .originalNote();
            assertEquals(2, original.beamCount());
            assertEquals(0d, original.unbeamedDurationBeats(), 0);
        } else
            assertFalse(
                    producer.admissions().contains("COMPLETE_OWNED_ORDINARY_BEAM_VALUE_OVERRIDE"));
        var canonical = OriginalBeamAnalysisSnapshot.json(a);
        assertEquals(
                name + " complete Analysis",
                expected,
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(canonical.getBytes(StandardCharsets.UTF_8))));
    }
}
