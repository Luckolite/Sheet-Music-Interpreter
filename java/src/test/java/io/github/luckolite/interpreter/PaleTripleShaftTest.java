// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

public class PaleTripleShaftTest {
    @Test
    public void oneReportedBeamCannotHideThreePrintedRails() throws Exception {
        assertEquals(3, new PaleTripleShaftFixture(135, 195, 3, 0).decoded());
    }

    @Test
    public void twoReportedBeamsCannotHideThreePrintedRails() throws Exception {
        assertEquals(3, new PaleTripleShaftFixture(125, 195, 3, 0).decoded());
    }

    @Test
    public void partialSecondaryBeamsDoNotRegroupAnEighth() throws Exception {
        assertTrue(new PaleTripleShaftFixture(135, 195, 3, 1).decoded() < 3);
    }

    @Test
    public void disconnectedRailsCannotSupplyThreeBeams() throws Exception {
        assertTrue(new PaleTripleShaftFixture(135, 195, 3, 2).decoded() < 3);
    }

    @Test
    public void opposedShaftsCannotShareThreeBeams() throws Exception {
        assertTrue(new PaleTripleShaftFixture(135, 195, 3, 3).decoded() < 3);
    }

    @Test
    public void zeroOneAndTwoRailsCannotBecomeThree() throws Exception {
        for (int n = 0; n < 3; n++)
            assertTrue(new PaleTripleShaftFixture(135, 195, n, 0).decoded() < 3);
    }

    @Test
    public void unsupportedPaleShaftCannotSupplyThreeBeams() throws Exception {
        assertTrue(new PaleTripleShaftFixture(135, 220, 3, 0).decoded() < 3);
    }

    @Test
    public void lowContrastPaperCannotProveShaft() throws Exception {
        var p = new PaleTripleShaftFixture(135, 195, 3, 0);
        for (int i = 0; i < p.gray.length; i++) {
            if ((p.gray[i] & 255) == 250) p.gray[i] = (byte) 150;
            else if ((p.gray[i] & 255) == 195) p.gray[i] = (byte) 145;
        }
        assertTrue(p.decoded() < 3);
    }

    @Test
    public void callerPixelsRemainUnchanged() throws Exception {
        var p = new PaleTripleShaftFixture(135, 195, 3, 0);
        byte[] a = p.gray.clone(), b = p.labels.clone();
        p.decoded();
        assertArrayEquals(a, p.gray);
        assertArrayEquals(b, p.labels);
    }
}
