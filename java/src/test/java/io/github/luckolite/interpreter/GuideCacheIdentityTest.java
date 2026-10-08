// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public final class GuideCacheIdentityTest {
    @Test
    public void sameRecordLayoutCannotReuseEarlierRecognition() {
        String source = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String variant = "0|cleanup-v2:0.0:0.0:0.0:0.0:false";
        assertEquals(
                "guide|280|0|" + source + "|" + variant,
                GuideCacheIdentity.portable(280, source, variant, 0));
        assertNotEquals(
                GuideCacheIdentity.portable(280, source, variant, 0),
                GuideCacheIdentity.portable(280, source, variant, 1));
        assertNotEquals(
                GuideCacheIdentity.local("same.pdf|100|200", 0, "clean", 280, 0),
                GuideCacheIdentity.local("same.pdf|100|200", 0, "clean", 280, 1));
        assertEquals(
                GuideCacheIdentity.portable(280, source, variant, 1),
                GuideCacheIdentity.portable(280, source, variant, 1));
    }

    @Test
    public void cleanupSourceAndWireLayoutRemainIndependentInputs() {
        String original = GuideCacheIdentity.portable(280, "source", "0|clean", 1);
        assertNotEquals(original, GuideCacheIdentity.portable(281, "source", "0|clean", 1));
        assertNotEquals(original, GuideCacheIdentity.portable(280, "replacement", "0|clean", 1));
        assertNotEquals(original, GuideCacheIdentity.portable(280, "source", "0|crop", 1));
        assertNotEquals(original, GuideCacheIdentity.portable(280, "source", "1|clean", 1));
    }

    @Test
    public void currentRecognitionTwentyThreeRejectsAllPriorEpochsAtLayout282() {
        assertEquals(23, GuideCacheIdentity.RECOGNITION_REVISION);
        String source = "same source", variant = "0|unchanged cleanup";
        String portable =
                GuideCacheIdentity.portable(
                        282, source, variant, GuideCacheIdentity.RECOGNITION_REVISION);
        String local =
                GuideCacheIdentity.local(
                        source, 0, "clean", 282, GuideCacheIdentity.RECOGNITION_REVISION);
        assertEquals("guide|282|0|same source|0|unchanged cleanup|recognition=23", portable);
        assertEquals("same source|page=0|cleanup=clean|engine=282|recognition=23", local);
        for (int older :
                new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22}) {
            assertNotEquals(portable, GuideCacheIdentity.portable(282, source, variant, older));
            assertNotEquals(local, GuideCacheIdentity.local(source, 0, "clean", 282, older));
        }
        assertNotEquals(portable, GuideCacheIdentity.portable(281, source, variant, 23));
    }
}
