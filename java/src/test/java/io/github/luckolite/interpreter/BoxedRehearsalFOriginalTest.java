// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import org.junit.Test;

/** Candidate-independent expectations frozen before any boxed-F implementation or execution. */
public class BoxedRehearsalFOriginalTest {
    @Test
    public void completeBoxedUprightFIsARehearsalLetter() throws Exception {
        BoxedRehearsalFFixture.rehearsal("boxed-capital");
    }

    @Test
    public void outerInnerAndMixedWordBoundsDoNotChangeRehearsalRole() throws Exception {
        BoxedRehearsalFFixture.rehearsal("boxed-capital-inner");
        BoxedRehearsalFFixture.rehearsal("boxed-capital-mixed");
    }

    @Test
    public void sampledScaleAndHalfPixelPhaseRetainRehearsalRole() throws Exception {
        BoxedRehearsalFFixture.rehearsal("boxed-capital-small");
        BoxedRehearsalFFixture.rehearsal("boxed-capital-large");
    }

    @Test
    public void genuineBravuraItalicForteIsPreserved() throws Exception {
        BoxedRehearsalFFixture.dynamic("italic-f", "f", 3);
    }

    @Test
    public void genuineMezzoForteAndFortissimoRemainWholeWords() throws Exception {
        BoxedRehearsalFFixture.dynamic("italic-mf", "mf", 0);
        BoxedRehearsalFFixture.dynamic("italic-ff", "ff", 6);
    }

    @Test
    public void editorialBoxesDoNotCancelGenuineItalicDynamics() throws Exception {
        BoxedRehearsalFFixture.dynamic("boxed-italic-f", "f", 3);
        BoxedRehearsalFFixture.dynamic("boxed-italic-mf", "mf", 0);
        BoxedRehearsalFFixture.dynamic("boxed-italic-ff", "ff", 6);
    }

    @Test
    public void aFaintLowerHookRemainsPartOfTheGenuineForte() throws Exception {
        BoxedRehearsalFFixture.dynamic("faint-tip-f", "f", 3);
    }

    @Test
    public void oldUnboxedCapitalFChordDecisionIsRetained() throws Exception {
        assertTrue(new BoxedRehearsalFFixture("unboxed-capital").detect("F").isEmpty());
    }

    @Test
    public void nearbyUnownedBoxCannotCancelForte() throws Exception {
        BoxedRehearsalFFixture.dynamic("nearby-box-f", "f", 3);
    }

    @Test
    public void croppedAndIncompleteBoxEdgesCannotCancelForte() throws Exception {
        BoxedRehearsalFFixture.dynamic("cropped-box-f", "f", 3);
        BoxedRehearsalFFixture.dynamic("incomplete-box-f", "f", 3);
    }

    @Test
    public void ruleAndShaftRectangleCannotCancelGenuineForte() throws Exception {
        BoxedRehearsalFFixture.dynamic("staff-rectangle-f", "f", 3);
    }

    @Test
    public void foreignStaffRehearsalBoxDoesNotOwnThisForte() throws Exception {
        BoxedRehearsalFFixture.dynamic("foreign-box-f", "f", 3);
    }

    @Test
    public void borderContactAndFaintUnownedGutterInkDoNotCancelForte() throws Exception {
        BoxedRehearsalFFixture.dynamic("contact-box-f", "f", 3);
        BoxedRehearsalFFixture.dynamic("faint-gutter-f", "f", 3);
    }

    @Test
    public void additionalInteriorLetterInkDoesNotCreateCapitalFProof() throws Exception {
        BoxedRehearsalFFixture.dynamic("mixed-interior-f", "f", 3);
    }

    @Test
    public void nearbyHairpinIsNotARehearsalBox() throws Exception {
        BoxedRehearsalFFixture.dynamic("hairpin-near-f", "f", 3);
    }

    @Test
    public void rawAndCleanedOwnershipChannelsPreserveTheGenuineGlyph() throws Exception {
        var scene = new BoxedRehearsalFFixture("channel-f");
        var raw = scene.detect("f");
        var clean = scene.detect("f", BoxedRehearsalFFixture.resource("channel-f-clean.gray"));
        assertEquals(1, raw.size());
        assertEquals(3, raw.get(0).decibels(), 0);
        assertEquals(raw, clean);
    }

    @Test
    public void identicalObservedInputsCannotReceiveDifferentRoles() throws Exception {
        var first = new BoxedRehearsalFFixture("boxed-italic-f");
        var second = new BoxedRehearsalFFixture("boxed-italic-f");
        assertArrayEquals(first.gray, second.gray);
        assertEquals(first.detect("f"), second.detect("f"));
        assertEquals(1, first.detect("f").size());
    }
}
