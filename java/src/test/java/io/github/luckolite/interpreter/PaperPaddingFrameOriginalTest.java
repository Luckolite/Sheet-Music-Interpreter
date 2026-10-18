// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import org.junit.Test;

/** Word paper is metadata; unrelated foreground cannot be silently owned by a finite frame. */
public class PaperPaddingFrameOriginalTest {
    @Test
    public void rightPaperPaddingDoesNotChangeOwnedRehearsalRole() throws Exception {
        for (String scene : PaperPaddingFrameOriginalProbe.scenes("paper"))
            if (scene.endsWith("right8") || scene.endsWith("right24"))
                BoxedRehearsalFFixture.rehearsal(scene);
    }

    @Test
    public void extendingAcrossOwnedLeftBorderIntoPaperKeepsRehearsalRole() throws Exception {
        for (String scene : PaperPaddingFrameOriginalProbe.scenes("paper"))
            if (scene.endsWith("left12")) BoxedRehearsalFFixture.rehearsal(scene);
    }

    @Test
    public void paddedGenuineItalicFMezzoForteAndFortissimoRemainDynamics() throws Exception {
        for (String scene : PaperPaddingFrameOriginalProbe.scenes("genuine")) {
            String text =
                    scene.contains("-italic-mf-")
                            ? "mf"
                            : scene.contains("-italic-ff-") ? "ff" : "f";
            BoxedRehearsalFFixture.dynamic(
                    scene, text, text.equals("mf") ? 0 : text.equals("ff") ? 6 : 3);
        }
    }

    @Test
    public void foreignForegroundKeepsFrozenPreV4ApiDecisions() throws Exception {
        var p = new Properties();
        try (var input =
                PaperPaddingFrameOriginalTest.class.getResourceAsStream(
                        "/boxed_rehearsal_f_baselines/public-paper-pre-v4.properties")) {
            assertNotNull(
                    "Missing ROOT-frozen PRE-V4 values; never regenerate from candidate", input);
            p.load(input);
        }
        assertEquals("true", p.getProperty("capture.complete"));
        assertEquals(
                "EXACT_PRE_V4_FOREIGN_FOREGROUND_NO_NEW_BOX_VETO", p.getProperty("oracle.policy"));
        for (String scene : PaperPaddingFrameOriginalProbe.scenes("unknown"))
            for (String text : List.of("F", "f")) {
                String expected = p.getProperty(PaperPaddingFrameOriginalProbe.key(scene, text));
                assertNotNull("Incomplete frozen baseline", expected);
                assertEquals(
                        "Foreign italic/dot/unknown ink is not owned by this frame",
                        expected,
                        PaperPaddingFrameOriginalProbe.actual(scene, text));
            }
    }

    @Test
    public void paperMetadataVariantsRetainIdenticalRawPlanes() throws Exception {
        var names = new ArrayList<>(PaperPaddingFrameOriginalProbe.scenes("paper"));
        names.addAll(PaperPaddingFrameOriginalProbe.scenes("genuine"));
        for (String scene : names) {
            var f = new BoxedRehearsalFFixture(scene);
            assertArrayEquals(
                    BoxedRehearsalFFixture.resource(
                            f.metadata.getProperty("parentScene") + ".gray"),
                    f.gray);
        }
    }

    @Test
    public void everyForeignMarkAddsObservableInkWithoutErasingOriginalFrameOrGlyph()
            throws Exception {
        for (String scene : PaperPaddingFrameOriginalProbe.scenes("unknown")) {
            var f = new BoxedRehearsalFFixture(scene);
            byte[] before =
                    BoxedRehearsalFFixture.resource(
                            f.metadata.getProperty("parentScene") + ".gray");
            int changed = 0;
            for (int i = 0; i < before.length; i++) {
                assertTrue((f.gray[i] & 255) <= (before[i] & 255));
                if (f.gray[i] != before[i]) changed++;
            }
            assertTrue("Foreign mark must actually be visible in API input", changed > 0);
        }
    }
}
