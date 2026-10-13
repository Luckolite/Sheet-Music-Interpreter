// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;

public final class PublishedEditionCreditRecoveryTest {
    @Test
    public void exactEditionAndClippedNames() throws Exception {
        PublishedEditionCreditRegression.main(new String[0]);
    }

    @Test
    public void cacheAndIntactNameBoundaries() throws Exception {
        PublishedEditionCacheRegression.main(new String[0]);
    }

    @Test
    public void priorTitleOwnershipSurvivesCredits() {
        PublicationCreditCompositionRegression.main(new String[0]);
    }
}
