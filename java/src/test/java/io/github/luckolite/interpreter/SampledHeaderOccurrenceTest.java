// Copyright 2026 Music Sheets contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static io.github.luckolite.interpreter.AcceptedMeterHeaderBoundsTest.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exact sampled accepted-reading identity; outside components remain unowned. */
public class SampledHeaderOccurrenceTest {
    static Scene crossing() {
        var s = new Scene();
        s.box(220, 96, 222, 102);
        s.box(220, 90, 222, 96);
        return s;
    }

    static void envelope(Scene s) {
        var g = s.gray.clone();
        var l = s.labels.clone();
        var r = s.run();
        assertEquals(199, r.get(0).left());
        assertEquals(301, r.get(0).right());
        assertEquals(A.top(), r.get(0).top());
        assertEquals(B.gap(), r.get(1).gap(), 0f);
        assertArrayEquals(g, s.gray);
        assertArrayEquals(l, s.labels);
    }

    @Test
    public void sameSampledOccurrenceMayContinueOutsideBothCrops() {
        var s =
                crossing(); /* same sampled set requires equal top extent over continuing component */
        s.headers =
                List.of(
                        header(s.source, M, VALUE, A),
                        header(
                                s.source,
                                M,
                                VALUE,
                                new MeterChangeDetector.Crop(199, 97, 301, 169, 101, 16.2f)));
        envelope(s);
    }

    @Test
    public void differentClippedSamplesRemainSeparate() {
        var s = crossing();
        unchanged(s);
    }

    @Test
    public void conflictingValueCannotAliasSameSample() {
        var s = crossing();
        s.headers =
                List.of(
                        s.headers.get(0),
                        header(
                                s.source,
                                M,
                                new ScoreMeterChange(0, 3, 4),
                                new MeterChangeDetector.Crop(199, 97, 301, 169, 101, 16.2f)));
        unchanged(s);
    }

    @Test
    public void foreignInvocationCannotAliasSameSample() {
        var s = crossing();
        var q = AcceptedMeterHeaderBounds.source(s.labels, s.gray, W, H, List.of(M));
        s.headers =
                List.of(
                        s.headers.get(0),
                        header(
                                q,
                                M,
                                VALUE,
                                new MeterChangeDetector.Crop(199, 97, 301, 169, 101, 16.2f)));
        unchanged(s);
    }

    @Test
    public void foreignActualOwnerCannotAlias() {
        var s = crossing();
        s.headers =
                List.of(
                        s.headers.get(0),
                        header(
                                s.source,
                                new MeasureRegion(M.left(), M.right(), M.top(), M.bottom()),
                                VALUE,
                                A));
        unchanged(s);
    }

    @Test
    public void newCornerSupportRejectsEnvelope() {
        var s = crossing();
        s.box(300, 168, 300, 168);
        s.headers =
                List.of(
                        s.headers.get(0),
                        header(
                                s.source,
                                M,
                                VALUE,
                                new MeterChangeDetector.Crop(199, 97, 301, 168, 101, 16.2f)));
        unchanged(s);
    }

    @Test
    public void thirdConflictingOverlappingSampleCannotBeHidden() {
        var s = new Scene();
        s.headers =
                List.of(
                        s.headers.get(0),
                        s.headers.get(1),
                        header(
                                s.source,
                                M,
                                VALUE,
                                new MeterChangeDetector.Crop(211, 96, 300, 169, 100, 16)));
        unchanged(s);
    }

    @Test
    public void sameAuxiliaryWhiteSemanticComponentDoesNotChangeReadingIdentity() {
        var s = new Scene();
        s.labels[125 * W + 260] = 5;
        s.gray[125 * W + 260] = (byte) 255;
        envelope(s);
    }

    @Test
    public void differentAuxiliaryWhiteSemanticComponentStillConflicts() {
        var s = new Scene();
        s.labels[125 * W + 300] = 5;
        s.gray[125 * W + 300] = (byte) 255;
        unchanged(s);
    }
}
