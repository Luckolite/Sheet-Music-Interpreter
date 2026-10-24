// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;

/** Original procedural curve/ownership/resource regressions, no score assets. */
public final class PrintedCurveCompletenessControlsTest {
    @Test
    public void finitePorts() throws Exception {
        FiniteConnectedSlidePortsOriginalChecks.main(new String[0]);
    }

    @Test
    public void finiteBudgets() throws Exception {
        FiniteSlideBudgetOriginalChecks.main(new String[0]);
    }

    @Test
    public void ownerRecords() throws Exception {
        FiniteSlideOwnerIndexOriginalChecks.main(new String[0]);
    }

    @Test
    public void endpointFragments() throws Exception {
        FiniteSlideSpanAdversarialChecks.main(new String[0]);
    }

    @Test
    public void pixelFootprints() throws Exception {
        FiniteSlideRasterPhaseOriginalChecks.main(new String[0]);
    }

    @Test
    public void bothCleanViews() throws Exception {
        FiniteSlideCompleteViewsOriginalChecks.main(new String[0]);
    }
}
