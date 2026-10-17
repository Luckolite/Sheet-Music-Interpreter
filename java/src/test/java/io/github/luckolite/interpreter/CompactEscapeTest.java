// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Compact full-span escapes disambiguate cone proof from global width veto. */
public final class CompactEscapeTest {
    static void wide(byte[] a, int x, int y) {
        for (int i = 0; i < 3; i++) ExtentChallenge.pixel(a, x + i, y, 25);
    }

    static byte[] left() {
        var a = ExtentChallenge.paper();
        for (int y = 25; y <= 99; y++) {
            double p = Math.floorMod(y - 19, 16) / 16.;
            wide(a, 60 + (int) Math.round(p < .5 ? p * 16 : (1 - p) * 16), y);
        }
        rail(a);
        wide(a, 58, 100);
        wide(a, 56, 101);
        wide(a, 54, 102);
        for (int y = 103; y <= 170; y++) {
            double p = (y - 103) % 16 / 16.;
            wide(a, 52 - (int) Math.round(p < .5 ? p * 10 : (1 - p) * 10), y);
        }
        return a;
    }

    static void rail(byte[] a) {
        for (int y = 100; y <= 102; y++)
            for (int x = 10; x <= 220; x++) ExtentChallenge.pixel(a, x, y, 100);
    }

    static byte[] right() {
        var a = ExtentChallenge.paper();
        for (int y = 25; y <= 99; y++) {
            double p = Math.floorMod(y - 27, 16) / 16.;
            RuleEscapeProbe.segment(a, 60 + (int) Math.round(p < .5 ? p * 20 : (1 - p) * 20), y);
        }
        rail(a);
        RuleEscapeProbe.segment(a, 70, 100);
        RuleEscapeProbe.segment(a, 71, 101);
        RuleEscapeProbe.segment(a, 73, 102);
        for (int y = 103; y <= 170; y++) {
            double p = (y - 103) % 16 / 16.;
            RuleEscapeProbe.segment(a, 75 + (int) Math.round(p < .5 ? p * 12 : (1 - p) * 12), y);
        }
        return a;
    }

    @Test
    public void compactLeftwardThreePixelWaveCannotEvadeExitCone() {
        assertEquals(0, FocusedContourTest.detect(left(), RuleEscapeProbe.page(), 16));
    }

    @Test
    public void compactRightwardOriginalShiftProvesTraceNotWidthVeto() {
        assertEquals(0, FocusedContourTest.detect(right(), RuleEscapeProbe.page(), 16));
    }

    public static void main(String[] args) throws Exception {
        java.nio.file.Files.write(java.nio.file.Path.of(args[0]), left());
        java.nio.file.Files.write(java.nio.file.Path.of(args[1]), right());
    }
}
