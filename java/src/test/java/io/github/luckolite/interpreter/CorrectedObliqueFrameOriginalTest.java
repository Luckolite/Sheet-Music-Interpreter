// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import org.junit.Test;

/** Independent scene-role expectations; no future helper, inferred box or crop classifier. */
public class CorrectedObliqueFrameOriginalTest {
    private static List<String> styles(String group) {
        return switch (group) {
            case "sloped" -> List.of("sloped-up", "sloped-down");
            case "rounded" -> List.of("rounded-small", "rounded-large");
            case "cut" -> List.of("cut-small", "cut-large");
            default -> throw new IllegalArgumentException(group);
        };
    }

    private static void rehearsal(String group) throws Exception {
        for (String style : styles(group))
            BoxedRehearsalFFixture.rehearsal("oblique-" + style + "-capital-F-closed");
    }

    private static void dynamics(String group) throws Exception {
        for (String style : styles(group))
            for (String text : List.of("f", "mf", "ff"))
                BoxedRehearsalFFixture.dynamic(
                        "oblique-" + style + "-italic-" + text + "-closed",
                        text,
                        text.equals("mf") ? 0 : text.equals("ff") ? 6 : 3);
    }

    @Test
    public void completeSlopedRehearsalFIsNotForte() throws Exception {
        rehearsal("sloped");
    }

    @Test
    public void completeRoundedRehearsalFIsNotForte() throws Exception {
        rehearsal("rounded");
    }

    @Test
    public void completeCutCornerRehearsalFIsNotForte() throws Exception {
        rehearsal("cut");
    }

    @Test
    public void slopedEditorialItalicDynamicsRemainDynamics() throws Exception {
        dynamics("sloped");
    }

    @Test
    public void roundedEditorialItalicDynamicsRemainDynamics() throws Exception {
        dynamics("rounded");
    }

    @Test
    public void cutCornerEditorialItalicDynamicsRemainDynamics() throws Exception {
        dynamics("cut");
    }

    @Test
    public void incompleteForeignNearbyAndStaffLikeFramesDoNotVetoItalicF() throws Exception {
        for (String style : List.of("sloped-up", "rounded-large", "cut-large"))
            for (String mode : List.of("partial", "nearby", "foreign", "cropped", "staff-like"))
                BoxedRehearsalFFixture.dynamic("oblique-" + style + "-italic-f-" + mode, "f", 3);
    }

    @Test
    public void genuineItalicFWithConnectedExteriorInkRemainsDynamic() throws Exception {
        for (String group : List.of("sloped", "rounded", "cut"))
            for (String style : styles(group))
                for (String extra : List.of("tail", "spur"))
                    BoxedRehearsalFFixture.dynamic(
                            "oblique-" + style + "-italic-f-closed-" + extra, "f", 3);
    }

    @Test
    public void unknownCapitalExteriorPreservesFrozenPreV3Decisions() throws Exception {
        String file = "/boxed_rehearsal_f_baselines/public-corrected-pre-v3.properties";
        var baseline = new Properties();
        try (var input = CorrectedObliqueFrameOriginalTest.class.getResourceAsStream(file)) {
            assertNotNull("Required root-frozen PRE-V3 baseline", input);
            baseline.load(input);
        }
        assertEquals("true", baseline.getProperty("capture.complete"));
        assertEquals(
                "EXACT_CORRECTED_PRE_V3_OUTPUT_UNOWNED_EXTERIOR_NO_NEW_BOX_ROLE",
                baseline.getProperty("oracle.policy"));
        for (String scene : CorrectedObliqueFrameOriginalProbe.unknownScenes())
            for (String text : List.of("F", "f")) {
                String expected =
                        baseline.getProperty(CorrectedObliqueFrameOriginalProbe.key(scene, text));
                assertNotNull("Incomplete root baseline", expected);
                assertEquals(
                        "Whole connected exterior must not be trimmed into a new boxed-role veto",
                        expected,
                        CorrectedObliqueFrameOriginalProbe.actual(scene, text));
            }
    }

    @Test
    public void newFramesNeverEraseTheOriginalGlyphOrStaff() throws Exception {
        for (String scene : CorrectedObliqueFrameOriginalProbe.allScenes()) {
            String base = new BoxedRehearsalFFixture(scene).metadata.getProperty("baseAsset");
            assertNotNull("Every declared scene must identify its own base", base);
            byte[] before = BoxedRehearsalFFixture.resource(base + ".gray"),
                    after = BoxedRehearsalFFixture.resource(scene + ".gray");
            assertEquals(before.length, after.length);
            int changed = 0;
            for (int i = 0; i < before.length; i++) {
                assertTrue(
                        "MIN cannot erase genuine glyph/staff ink",
                        (after[i] & 255) <= (before[i] & 255));
                if (after[i] != before[i]) changed++;
            }
            assertTrue("Every authored frame changes observable raw pixels", changed > 0);
        }
    }

    @Test
    public void completeCapitalGlyphWordKeepsOldUnboxedCapitalGuard() throws Exception {
        for (String group : List.of("sloped", "rounded", "cut"))
            for (String style : styles(group))
                BoxedRehearsalFFixture.rehearsal(
                        "oblique-" + style + "-capital-F-fullglyph-oldguard");
    }
}
