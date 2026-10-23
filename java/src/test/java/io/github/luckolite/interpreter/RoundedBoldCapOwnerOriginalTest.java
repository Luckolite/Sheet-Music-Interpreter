// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoundedBoldCapOwnerOriginalTest {
    @Test
    public void realFractionalHeadCenterUsesExistingRasterCapRounding() {
        var s = new BoldReturningSlurOriginalTest.Scene();
        s.heads.remove(1);
        s.head(1, 390, 180);
        s.heads.set(1, new PrintedSlurOwners.Head(1, 389.8f, 180, 0, 1, 0, 960, 64));
        s.ribbon(false, false);
        assertTrue(s.owns());
    }
}
