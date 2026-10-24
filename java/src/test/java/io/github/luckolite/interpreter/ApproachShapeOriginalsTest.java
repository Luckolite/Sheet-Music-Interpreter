// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Original metadata/geometry controls, independent of any score or page identity. */
public class ApproachShapeOriginalsTest {
    private NoteSlideDetector.Stroke stroke(int dir, boolean connected, boolean curved) {
        return new NoteSlideDetector.Stroke(10, 24, 20, 14, dir, 0, connected, curved);
    }

    @Test
    public void compatibleConstructorIsLinearWithoutInferringOwnership() {
        for (boolean connected : new boolean[] {false, true}) {
            var s = new NoteSlideDetector.Stroke(10, 24, 20, 14, 1, 0, connected);
            assertFalse(s.curved());
            assertEquals(connected, s.connected());
            assertEquals(stroke(1, connected, false), s);
        }
    }

    @Test
    public void straightAndCurvedIsolatedMarksKeepDirection() {
        for (int dir : new int[] {-1, 1})
            for (boolean curved : new boolean[] {false, true}) {
                var s = stroke(dir, false, curved);
                int marks = PortableNoteOrnaments.slideMarks(s);
                assertEquals(NoteOrnament.SLIDE, NoteOrnament.type(marks));
                assertEquals(dir < 0, (marks & NoteOrnament.FROM_ABOVE) != 0);
                assertEquals(!curved, NoteOrnament.straightApproach(marks));
                assertEquals(0, marks & NoteOrnament.FROM_PREVIOUS);
            }
    }

    @Test
    public void connectedMarksRemainExactRegardlessOfShapeFlag() {
        for (int dir : new int[] {-1, 1})
            for (boolean curved : new boolean[] {false, true}) {
                int marks = PortableNoteOrnaments.slideMarks(stroke(dir, true, curved));
                assertEquals(
                        NoteOrnament.SLIDE
                                | NoteOrnament.FROM_PREVIOUS
                                | (dir < 0 ? NoteOrnament.FROM_ABOVE : 0),
                        marks);
                assertFalse(NoteOrnament.straightApproach(marks));
            }
    }

    @Test
    public void qualifierHasExactTypeAndOwnershipPredicate() {
        for (int type = 0; type <= NoteOrnament.TYPE_MASK; type += 32)
            for (boolean connected : new boolean[] {false, true}) {
                int marks =
                        type
                                | NoteOrnament.STRAIGHT_APPROACH
                                | (connected ? NoteOrnament.FROM_PREVIOUS : 0);
                assertEquals(
                        type == NoteOrnament.SLIDE && !connected,
                        NoteOrnament.straightApproach(marks));
            }
        assertEquals(NoteOrnament.DELAYED, NoteOrnament.STRAIGHT_APPROACH);
        assertEquals(
                NoteOrnament.STRAIGHT_APPROACH, NoteOrnament.ALL & NoteOrnament.STRAIGHT_APPROACH);
    }

    @Test
    public void qualifierDoesNotChangeGraceTremoloAccidentalsOrArticulations() {
        int prior = NoteOrnament.SLIDE | NoteOrnament.GRACE | NoteArticulation.ACCENT;
        prior = NoteOrnament.withTremolo(prior, 3);
        prior = NoteOrnament.withAccidental(prior, true, 1);
        int changed = prior | NoteOrnament.STRAIGHT_APPROACH;
        assertEquals(prior, changed & ~NoteOrnament.STRAIGHT_APPROACH);
        assertEquals(3, NoteOrnament.tremoloBeams(changed));
        assertEquals(1, NoteOrnament.accidental(changed, true));
    }

    @Test
    public void actualAttachedCurvesKeepExplicitCurvatureAcrossDensities() {
        for (int scale : new int[] {1, 2}) {
            var scene = new AttachedCurvedApproachTest.Scene(scale, true);
            var result = scene.found();
            assertEquals(1, result.size());
            assertTrue(result.get(0).curved());
            assertFalse(
                    NoteOrnament.straightApproach(PortableNoteOrnaments.slideMarks(result.get(0))));
        }
    }

    @Test
    public void actualFiniteConnectedLinearPortsRemainLinearAndUnqualified() {
        for (int scale : new int[] {1, 2, 3}) {
            var scene = new FiniteConnectedSlidePortsOriginalChecks.Scene(scale, false, -1, 1);
            scene.line();
            var found = scene.port();
            assertEquals(1, found.size());
            assertFalse(found.get(0).curved());
            assertTrue(found.get(0).connected());
            assertFalse(
                    NoteOrnament.straightApproach(PortableNoteOrnaments.slideMarks(found.get(0))));
        }
    }

    @Test
    public void actualCompactLinearApproachesCarryStraightQualifier() throws Exception {
        var found =
                CompactApproachStrokesOriginalsTest.class.getDeclaredMethod(
                        "found", int.class, String.class);
        found.setAccessible(true);
        for (int gap : new int[] {16, 24}) {
            @SuppressWarnings("unchecked")
            var result = (List<NoteSlideDetector.Stroke>) found.invoke(null, gap, "normal");
            assertEquals(1, result.size());
            assertFalse(result.get(0).curved());
            assertFalse(result.get(0).connected());
            assertTrue(
                    NoteOrnament.straightApproach(PortableNoteOrnaments.slideMarks(result.get(0))));
        }
    }
}
