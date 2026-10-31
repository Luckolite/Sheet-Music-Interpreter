// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Original broad beam ownership scenes; no downloaded or commercial raster.
 * Fingerprints freeze already-reviewed synthetic Analysis outputs, not outputs recomputed
 * by the implementation under test. Readable rhythm/lineage assertions explain each gain.
 * This pale-fill repair deliberately does not change non-pale heads or add tremolo recall.
 */
@RunWith(Parameterized.class)
public final class CompleteRailOwnershipOriginalTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(
                new Object[][] {
                    {
                        "positiveUp",
                        "9a53ba6bf80c0da2499e46f04ef1d88cda1184de306decffa655bc66f7012704",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "positiveDown",
                        "3667399b31614b2fbcff2a7bc7d2858b1af519ef2af49469ded3f9c2f244f460",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "positiveSlope",
                        "9f03e3d64b30a016607013ec10da870e265c6121c71bda10c67833906b528f1b",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "compactControl",
                        "01c2d9a1486e83205adbb354be56cdf97c2f2d9372e4a07922b34f06b8081d71",
                        3,
                        2,
                        0.0f,
                        false
                    },
                    {
                        "nearLeft1",
                        "30f1145c7c05833c9bae3e0c78c39f21e6c1fa8918b709a950a5e3c221e0d431",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "nearLeft2",
                        "6818db9fd410e0cc45d1fe459051dc1507e4de6af6cf5c03e4e631dc5f8220d5",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "nearRight1",
                        "cb179a765b4c8260fdaaee19132e254511bd52dec16d9fea9293876747031fa5",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "nearRight2",
                        "0152090c741a136c2bd00109c56c749c590a0cf2785f709329f722519fc4f823",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "brokenInterior",
                        "84eb0868438ac0c4d437ec510f92ad1a01d0cb98613c5f73e82dad1541b5bac4",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "staffBridge",
                        "389d1aac76d30ff6bc6eb7eca512a308a5ba11a9872a08876224070d68ba7b4f",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "slurBridge",
                        "bec145a3ff6e443e8d155866c313324ebeddb36b9edfe01d1296935a13c552ed",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "otherRailOnly",
                        "84eb0868438ac0c4d437ec510f92ad1a01d0cb98613c5f73e82dad1541b5bac4",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "foreignStem",
                        "8cc39eeb533aec830d3573abc2de9f111038f6ff4294e9fc7616109173b54077",
                        3,
                        1,
                        0.0f,
                        false
                    },
                    {
                        "omittedOwner",
                        "47383aecc0a4f8a18f07597aa99c5cef69a760465e02c90369e465c038b80eab",
                        2,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "crossBar",
                        "01f4ee7b044f4fdc33c9def5454c76b19bb38d08e5ac851a6c29d4caf94e2037",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "dualStem",
                        "b41cd0a5a42f553fc5be78d80718f9b0da9cc48483a6e7e714329b9265536e78",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "whole",
                        "1bfc6dbb101dc29586ba19d4a94038fe37ba777040634476efcd5f7daca6e682",
                        3,
                        0,
                        4.0f,
                        false
                    },
                    {
                        "half",
                        "3e41b27b7bc2ab7088e6b6e011ea7e8041bf10c14a4a63c24ee2b11b457a04de",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "paperCounter",
                        "a6fbe5085131306edfb878ee8f491cb71d68a7ce8714191a1b9de1cbf0d5a2d9",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "separateGroups",
                        "c126e063a2918cba483c97303b16be161cf59923c2c5aa49678842a1097803ff",
                        6,
                        2,
                        0.0f,
                        false
                    },
                    {
                        "attachedTremolo",
                        "ef2865696056483bbd654b56dd42a431b0e09ca65b6b49eb3d789eb0dd2a78d4",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "detachedTremolo",
                        "c181e908a8195d9212595d3724936c4c20aa51e8ce2eeb133e472601c60fd560",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "pairedTremolo",
                        "5b56c888edbead46891c29518742eaf8b10f2085abc6448bf60519a2a3cda082",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "pairedTremolo3",
                        "c63fe9a4b905e4ceb3e9db33a1a9996535ec2c03eca6920a51ae4d9ef04a990c",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "sharedShaft",
                        "69d7a6ee3489f921aa0ae482f59f6816105539330f83593e37e8fb4dcddf65cd",
                        4,
                        2,
                        0.0f,
                        false
                    },
                    {
                        "competingHead",
                        "3230d92e9d1777c4534b7442b48591d896c0f6eb04e0d1494b6f67426c71efdb",
                        4,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "railLabelSymbol",
                        "0a92c2af7d4d6bee2ed58183e7df40aaee9863160522743cbe2d6ad61a624642",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "railLabelBackground",
                        "00d9b06803fdf0af7774ceb2dbaff6fd169630cf80a3bb521762d1c7d4d66b55",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "thinInteriorSymbol",
                        "185741b30017b676ecc3e3b8e7d2a386cc3bba2d499918aa977853935c53b9ad",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "thinInteriorBackground",
                        "7f2f0030194cd0dec7b101a2a39abaeee22b51f185d67987023fe1bcc2b37961",
                        3,
                        2,
                        0.0f,
                        true
                    },
                    {
                        "ordinaryTwoShaft",
                        "44c4e233e7c0c556b2bc85f794ae0f2a14a9efb052ed5d54505a7f59e5c0a689",
                        2,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "chordTwoShaft",
                        "fbb33407c3130cd3307ac96a420d9faf4d3c97d9a1f1251d47e2df27e9251883",
                        3,
                        0,
                        2.0f,
                        false
                    },
                    {
                        "pairedIssuerControl",
                        "6aa494faa7d855ed3a711e761cfc8f1346ccb2f87bf69bf36a8572d61e241d26",
                        2,
                        0,
                        1.0f,
                        false
                    },
                });
    }

    private final String name, expected;
    private final int count, beams;
    private final float base;
    private final boolean gain;

    public CompleteRailOwnershipOriginalTest(
            String name, String expected, int count, int beams, float base, boolean gain) {
        this.name = name;
        this.expected = expected;
        this.count = count;
        this.beams = beams;
        this.base = base;
        this.gain = gain;
    }

    @Test
    public void originalNotationAndEntireAnalysisStayQualified() throws Exception {
        var analysis = CompleteRailOriginalScene.scene(name);
        assertEquals(name + " note identities", count, analysis.notes().size());
        if (count > 0) {
            var note = analysis.notes().get(0);
            assertEquals(name + " first written beams", beams, note.beamCount());
            assertEquals(name + " first written base", base, note.unbeamedDurationBeats(), 0);
        }
        if (gain) {
            var context =
                    analysis.clockContextSource().printedValues().stream()
                            .filter(v -> v.noteIndex() == 0)
                            .findFirst()
                            .orElseThrow();
            assertEquals(2, context.beams());
            assertEquals(0, context.baseBeats(), 0);
            assertEquals(0, context.admissionFlags());
            var producer =
                    analysis.clockProducerSource().printedValues().stream()
                            .filter(v -> v.note() == 0)
                            .findFirst()
                            .orElseThrow();
            assertEquals(2, producer.detectorBeams());
            assertEquals(0, producer.detectorValue(), 0);
            assertEquals(2, producer.finalBeams());
            assertEquals(0, producer.finalValue(), 0);
            for (String stale :
                    List.of(
                            "CLOSED_WRITTEN_POCKET_HALF_OVERRIDE",
                            "POST_DETECTOR_VALUE_OR_BEAM_CHANGED",
                            "FINAL_EVENT_VALUE_OR_BEAM_DIFFERS_FROM_DETECTOR"))
                assertFalse(
                        name + " stale lineage " + stale, producer.admissions().contains(stale));
            var original =
                    analysis.clockProducerSource().localAccidentals().choices().stream()
                            .filter(v -> v.note() == 0)
                            .findFirst()
                            .orElseThrow()
                            .originalNote();
            assertEquals(2, original.beamCount());
            assertEquals(0, original.unbeamedDurationBeats(), 0);
        }
        String canonical = OriginalBeamAnalysisSnapshot.json(analysis);
        String actual =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        if (!expected.equals(actual)) {
            var diagnostic = Files.createTempFile("beam-" + name + "-", ".json");
            Files.writeString(diagnostic, canonical, StandardCharsets.UTF_8);
            fail(
                    name
                            + " Analysis changed; notes="
                            + analysis.notes()
                            + "; canonical JSON="
                            + diagnostic
                            + "; expected="
                            + expected
                            + " actual="
                            + actual);
        }
    }
}
