// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

public final class LocalFrameOriginalsTest {
    @org.junit.Test
    public void physicalFrameAffinityAndUnboundInteriorHead() {
        LocalFrameOriginals.main(new String[0]);
    }

    @org.junit.Test
    public void necessaryFloatRangeNeverRejectsAnAcceptedFrame() {
        PrintedBeamBindingRangeOriginals.main(new String[0]);
    }

    @org.junit.Test
    public void addedChildrenSpendTheActualExistingSourcePool() {
        BeamFrameParentBudgetOriginals.main(new String[0]);
    }
}
