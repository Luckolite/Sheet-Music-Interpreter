// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import org.junit.Test;
import java.io.*;
import java.util.*;

/** Exterior ink makes new box-role rejection unproved. Unknown capitals have no asserted musical role. */
public class BoxedFrameExteriorOriginalTest {
    @Test
    public void unknownFrameAppendagesPreserveRootFrozenPreV2Outputs() throws Exception {
        var baseline = new Properties();
        try (var input =
                BoxedFrameExteriorOriginalTest.class.getResourceAsStream(
                        "/boxed_rehearsal_f_baselines/public-pre-v2.properties")) {
            assertNotNull(
                    "Missing ROOT-frozen pre-V2 outputs; never regenerate from candidate", input);
            baseline.load(input);
        }
        assertEquals("true", baseline.getProperty("capture.complete"));
        assertEquals(
                "EXACT_PRE_V2_OUTPUT_UNOWNED_EXTERIOR_NO_NEW_BOX_ROLE",
                baseline.getProperty("oracle.policy"));
        for (String scene : BoxedFrameExteriorProbe.unknownScenes())
            for (String text : List.of("F", "f")) {
                String expected = baseline.getProperty(BoxedFrameExteriorProbe.key(scene, text));
                assertNotNull("Incomplete root baseline", expected);
                assertEquals(
                        "Unowned frame ink cannot create a NEW boxed-role veto: " + scene,
                        expected,
                        BoxedFrameExteriorProbe.actual(scene, text));
            }
    }

    @Test
    public void genuineItalicDynamicsSurviveExteriorUnknownInk() throws Exception {
        var scenes = BoxedFrameExteriorProbe.genuineScenes();
        for (String scene : scenes.stringPropertyNames()) {
            String text =
                    scene.contains("italic-mf") ? "mf" : scene.contains("italic-ff") ? "ff" : "f";
            BoxedRehearsalFFixture.dynamic(
                    scene, text, Float.parseFloat(scenes.getProperty(scene)));
        }
    }

    @Test
    public void appendagesNeverEraseTheOriginalFrameOrGlyph() throws Exception {
        var names = new ArrayList<>(BoxedFrameExteriorProbe.unknownScenes());
        names.addAll(BoxedFrameExteriorProbe.genuineScenes().stringPropertyNames());
        for (String name : names) {
            String original =
                    name.contains("boxed-italic-mf")
                            ? "boxed-italic-mf"
                            : name.contains("boxed-italic-ff")
                                    ? "boxed-italic-ff"
                                    : name.contains("boxed-italic-f")
                                            ? "boxed-italic-f"
                                            : "boxed-capital";
            byte[] before = BoxedRehearsalFFixture.resource(original + ".gray"),
                    after = BoxedRehearsalFFixture.resource(name + ".gray");
            assertEquals(before.length, after.length);
            int changed = 0;
            for (int i = 0; i < before.length; i++) {
                assertTrue(
                        "MIN composition may not lighten original ink",
                        (after[i] & 255) <= (before[i] & 255));
                if (after[i] != before[i]) changed++;
            }
            assertTrue("Every challenge must change observable pixels", changed > 0);
        }
    }

    @Test
    public void oneGrayExteriorBranchesAreNotDiscardedByTheFixture() throws Exception {
        for (String scene : BoxedFrameExteriorProbe.genuineScenes().stringPropertyNames())
            if (scene.contains("-254-")) {
                byte[] image = BoxedRehearsalFFixture.resource(scene + ".gray");
                byte[] original = BoxedRehearsalFFixture.resource("boxed-italic-f.gray");
                boolean observed = false;
                for (int i = 0; i < image.length; i++)
                    if ((original[i] & 255) == 255 && (image[i] & 255) == 254) observed = true;
                assertTrue("Nearpaper branch must actually exist in production input", observed);
            }
    }
}
