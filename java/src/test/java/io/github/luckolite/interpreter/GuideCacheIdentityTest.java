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
}
