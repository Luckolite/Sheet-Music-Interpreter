// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StandaloneSlurPageTest {
    private byte[] paper() {
        var gray = new byte[120 * 160];
        Arrays.fill(gray, (byte) 255);
        return gray;
    }

    @Test
    public void legacyCallAndExplicitPagePreserveTheSameBlankScore() throws Exception {
        var mask = new byte[120 * 160];
        var gray = paper();
        var old = SheetInterpreter.analyze(mask, gray, 120, 160);
        var current =
                SheetInterpreter.analyze(
                        mask, gray, 120, 160, SheetInterpreter.Annotations.EMPTY, null, 9);
        assertEquals(old, current);
        assertTrue(current.slurSource().spans().isEmpty());
    }

    @Test
    public void callerPixelsAreNeverMutatedByExplicitPageSupport() throws Exception {
        var mask = new byte[120 * 160];
        var gray = paper();
        var before = gray.clone();
        SheetInterpreter.analyze(mask, gray, 120, 160, SheetInterpreter.Annotations.EMPTY, null, 0);
        assertArrayEquals(before, gray);
        assertArrayEquals(new byte[120 * 160], mask);
    }

    @Test
    public void invalidNegativePageCannotBecomeAnOrigin() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SheetInterpreter.analyze(
                                new byte[1],
                                new byte[1],
                                1,
                                1,
                                SheetInterpreter.Annotations.EMPTY,
                                null,
                                -2));
    }

    @Test
    public void explicitPageDoesNotBypassImageValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SheetInterpreter.analyze(
                                new byte[1],
                                new byte[2],
                                1,
                                1,
                                SheetInterpreter.Annotations.EMPTY,
                                null,
                                2));
    }
}
