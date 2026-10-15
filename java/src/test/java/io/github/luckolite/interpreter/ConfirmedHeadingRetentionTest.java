// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfirmedHeadingRetentionTest {
    static final ScoreCreditsDetector.Line L =
            new ScoreCreditsDetector.Line("Silver Harbor", 480, 110, 1100, 220);

    static ScoreCreditsDetector.Page p(boolean photo) {
        return new ScoreCreditsDetector.Page(1800, 1200, -1, List.of(L), false, photo);
    }

    static LocatedCoverHeadingEvidence e(List<String> r, List<Float> c) {
        return LocatedCoverHeadingEvidence.fromExactOriginal(p(true), L, r, c);
    }

    static List<String> r(String s) {
        return List.of(s, s, s);
    }

    @Test
    public void repeatedOriginalIsProtected() {
        var e = e(r(L.text()), List.of(.925f, .932f, .934f));
        assertNotNull(e);
        assertSame(L, e.confirmed());
        assertEquals(List.of(L), LocatedCoverHeadingEvidence.merge(p(true), List.of(e)));
    }

    @Test
    public void replacementStillRequiresStrongConfidence() {
        assertNull(e(r("Silver Harb0r"), List.of(.925f, .932f, .934f)));
    }

    @Test
    public void caseChangeAlsoRequiresStrongConfidence() {
        assertNull(e(r("SILVER HARBOR"), List.of(.925f, .932f, .934f)));
    }

    @Test
    public void weakOriginalIsNotProtected() {
        assertNull(e(r(L.text()), List.of(.899f, .99f, .99f)));
    }

    @Test
    public void disagreementIsNotProtected() {
        assertNull(e(List.of(L.text(), "Silver Harb0r", L.text()), List.of(.99f, .99f, .99f)));
    }

    @Test
    public void invalidConfidenceIsRejected() {
        assertNull(e(r(L.text()), List.of(Float.NaN, .99f, .99f)));
        assertNull(e(r(L.text()), List.of(1.1f, .99f, .99f)));
    }

    @Test
    public void ordinaryPageIsUnchanged() {
        assertNull(
                LocatedCoverHeadingEvidence.fromExactOriginal(
                        p(false), L, r(L.text()), List.of(.93f, .94f, .92f)));
    }
}
