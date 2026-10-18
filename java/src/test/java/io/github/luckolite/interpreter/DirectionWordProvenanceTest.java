// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;

/** Original procedural OCR geometry and stub-reader regressions, with no score assets. */
public final class DirectionWordProvenanceTest {
    @Test
    public void preservesPhysicalWordIdentityAcrossActualCropTransforms() {
        DirectionWordProvenanceFixture.contract();
    }

    @Test
    public void usesOutwardF32PixelEnvelopeWithoutTolerance() throws Exception {
        DirectionWordProvenanceFixture.envelope();
    }

    @Test
    public void onlyReusesAlreadyExecutedSameInvocationFullRead() throws Exception {
        DirectionWordProvenanceFixture.cache();
    }
}
