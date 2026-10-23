// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Source-free whole-box originals; narrow middle arms are authored before the new proof. */
public class CompleteCapitalFTopologyTest {
    static BoxedRehearsalFFixture scene(int middle) throws Exception {
        var s = new BoxedRehearsalFFixture("boxed-capital");
        CapFringeRehearsalFTest.paper(s, 96, 90, 121, 123);
        ink(s, 101, 96, 103, 119);
        ink(s, 101, 96, 118, 98);
        ink(s, 101, 105, 100 + middle, 107);
        return s;
    }

    static void ink(BoxedRehearsalFFixture s, int l, int t, int r, int b) {
        CapFringeRehearsalFTest.ink(s, l, t, r, b);
    }

    static void positive(int width) throws Exception {
        var s = scene(width);
        var word =
                new PlayingTechniqueDetector.Word(
                        "f", 101f / 400, 96f / 320, 119f / 400, 120f / 320);
        assertFalse(
                "Old capital path must actually reject middle width",
                ScoreDynamicsDetector.uprightChordF(word, s.staffs().get(0), s.gray, 400, 320));
        CapFringeRehearsalFTest.rehearsal(s);
    }

    @Test
    public void completeTwoArmsWithEightPixelMiddle() throws Exception {
        positive(8);
    }

    @Test
    public void completeTwoArmsWithTenPixelMiddle() throws Exception {
        positive(10);
    }

    @Test
    public void completeTwoArmsWithTwelvePixelMiddle() throws Exception {
        positive(12);
    }

    @Test
    public void finiteEndCapUsesWholeTopOverhang() throws Exception {
        var s = scene(10);
        ink(s, 100, 96, 100, 98);
        ink(s, 100, 117, 100, 119);
        CapFringeRehearsalFTest.rehearsal(s);
    }

    @Test
    public void leftLowerHookIsNotAPlainStem() throws Exception {
        var s = scene(10);
        ink(s, 97, 113, 101, 115);
        ink(s, 97, 113, 98, 119);
        CapFringeRehearsalFTest.keep(s);
    }

    @Test
    public void slantedStemDoesNotHaveOneFiniteBackbone() throws Exception {
        var s = scene(10);
        CapFringeRehearsalFTest.paper(s, 101, 99, 103, 119);
        for (int y = 99; y <= 119; y++) ink(s, 101 - (y - 99) / 7, y, 103 - (y - 99) / 7, y);
        CapFringeRehearsalFTest.keep(s);
    }

    @Test
    public void counterfeitThirdRightArmIsUnowned() throws Exception {
        var s = scene(10);
        ink(s, 101, 114, 111, 116);
        CapFringeRehearsalFTest.keep(s);
    }

    @Test
    public void attachedForeignInkCannotBeCroppedFromTopArm() throws Exception {
        var s = scene(10);
        ink(s, 116, 98, 117, 103);
        CapFringeRehearsalFTest.keep(s);
    }

    @Test
    public void detachedDotRemainsAnExtraWholeComponent() throws Exception {
        var s = scene(10);
        ink(s, 116, 114, 117, 115);
        CapFringeRehearsalFTest.keep(s);
    }

    @Test
    public void missingMiddleConnectionCannotBeCompleted() throws Exception {
        var s = scene(10);
        CapFringeRehearsalFTest.paper(s, 104, 105, 105, 107);
        CapFringeRehearsalFTest.keep(s);
    }

    @Test
    public void genuineItalicMusicFStaysDynamic() throws Exception {
        CapFringeRehearsalFTest.keep(new BoxedRehearsalFFixture("boxed-italic-f"));
    }

    @Test
    public void genuineBoxedMfIsNotOneCapitalF() throws Exception {
        BoxedRehearsalFFixture.dynamic("boxed-italic-mf", "mf", 0);
    }
}
