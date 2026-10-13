// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;

public final class PrimaryTitleCorroborationTest {
    @Test
    public void literalTitleAndContributorBoundaries() {
        PrimaryTitleRegression.main(new String[0]);
        PrimaryTitleBoundaryRegression.main(new String[0]);
    }
}
