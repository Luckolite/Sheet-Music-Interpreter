// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original finite and cross-staff wavy strokes plus stable column controls. */
public final class ScoreArpeggioExtentTest {
    ScoreArpeggioTest f = new ScoreArpeggioTest();

    byte[] wave(int first, int last) {
        var b = f.paper();
        for (int y = first; y <= last; y++) {
            double phase = Math.floorMod(y - 50, 12) / 12.;
            int x = 60 + (int) Math.round(phase < .5 ? phase * 12 : (1 - phase) * 12);
            f.line(b, x, y, x, y + 1);
        }
        return b;
    }

    ScorePageInterpretation detect(byte[] g) {
        return ScoreArpeggioDetector.apply(
                f.page(),
                g,
                f.W,
                f.H,
                List.of(
                        new PlayingTechniqueDetector.Staff(52, 100, 12, 0, 2),
                        new PlayingTechniqueDetector.Staff(152, 200, 12, 1, 2)),
                8);
    }

    @Test
    public void finiteRightHandWaveRetainsExactThreeMembers() {
        var s = detect(wave(50, 109));
        assertEquals(f.page().notes(), s.notes());
        assertEquals(1, s.expressiveEvents().size());
        assertEquals(
                List.of(0, 1, 2),
                ScoreArpeggioNotation.plan(s, List.of(0, 1, 2, 3), 4).stream()
                        .map(ScoreArpeggioNotation.Member::sourceIndex)
                        .toList());
    }

    @Test
    public void waveContinuingAcrossBothStavesCannotBeClaimedOnlyByRightHand() {
        assertTrue(detect(wave(10, 220)).expressiveEvents().isEmpty());
    }

    @Test
    public void waveContinuingBelowRightHandCannotBeClippedIntoFiniteGlyph() {
        assertTrue(detect(wave(50, 160)).expressiveEvents().isEmpty());
    }

    @Test
    public void waveContinuingAboveRightHandCannotBeClippedIntoFiniteGlyph() {
        assertTrue(detect(wave(5, 109)).expressiveEvents().isEmpty());
    }

    @Test
    public void reorderedDuplicatedAndRemovedHeadsFailClosed() {
        var a = f.captured();
        var nn = new ArrayList<>(a.notes());
        Collections.reverse(nn);
        a = a.withNotes(nn, List.of(3, 2, 1, 0));
        assertEquals(3, ScoreArpeggioNotation.plan(a, List.of(0, 1, 2, 3), 4).size());
        nn.add(nn.get(1));
        a = a.withNotes(nn, List.of(0, 1, 2, 3, -1));
        assertTrue(ScoreArpeggioNotation.plan(a, List.of(0, 1, 2, 3, 4), 4).isEmpty());
    }

    @Test
    public void oppositeStaffCannotReceiveTarget() {
        var a = f.captured();
        assertTrue(ScoreArpeggioNotation.plan(a, List.of(3), 4).isEmpty());
    }

    @Test
    public void independentWaveSharingStaffRailDoesNotJoinComponent() {
        var b = wave(50, 109);
        for (int y = 80; y <= 160; y++) {
            double phase = Math.floorMod(y - 50, 12) / 12.;
            int x = 125 + (int) Math.round(phase < .5 ? phase * 12 : (1 - phase) * 12);
            f.line(b, x, y, x, y + 1);
        }
        for (int y = 52; y <= 100; y += 12) f.line(b, 10, y, 220, y);
        assertEquals(1, detect(b).expressiveEvents().size());
    }

    @Test
    public void quietGapToIndependentSameColumnWaveIsNotBridged() {
        var b = wave(50, 109);
        for (int y = 125; y <= 160; y++) {
            double phase = Math.floorMod(y - 50, 12) / 12.;
            int x = 60 + (int) Math.round(phase < .5 ? phase * 12 : (1 - phase) * 12);
            f.line(b, x, y, x, y + 1);
        }
        assertEquals(1, detect(b).expressiveEvents().size());
    }

    @Test
    public void finiteGlyphNearOwnershipBoundRemainsRecognized() {
        assertEquals(1, detect(wave(35, 116)).expressiveEvents().size());
        assertTrue(detect(wave(33, 118)).expressiveEvents().isEmpty());
    }

    @Test
    public void pageClippedContinuationHasNoFiniteEndpointProof() {
        var ns = new ArrayList<ScoreNoteEvent>();
        for (int step : new int[] {0, 3, 7})
            ns.add(
                    new ScoreNoteEvent(
                            0, .35f, step, 0, 2, (60 - step * 6f) / f.H, false, 0, 0, 0, 2));
        var score = new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), ns, 1);
        var b = wave(0, 69);
        var out =
                ScoreArpeggioDetector.apply(
                        score,
                        b,
                        f.W,
                        f.H,
                        List.of(new PlayingTechniqueDetector.Staff(12, 60, 12, 0, 2)),
                        0);
        assertTrue(out.expressiveEvents().isEmpty());
        for (int first : new int[] {2, 3}) {
            out =
                    ScoreArpeggioDetector.apply(
                            score,
                            wave(first, 69),
                            f.W,
                            f.H,
                            List.of(new PlayingTechniqueDetector.Staff(12, 60, 12, 0, 2)),
                            0);
            assertEquals(first == 3 ? 1 : 0, out.expressiveEvents().size());
        }
    }

    @Test
    public void staffRailCannotBridgeAWhiteGapIntoIndependentWave() {
        var b = wave(50, 97);
        for (int y = 101; y <= 160; y++) {
            double phase = Math.floorMod(y - 50, 12) / 12.;
            int x = 60 + (int) Math.round(phase < .5 ? phase * 12 : (1 - phase) * 12);
            f.line(b, x, y, x, y + 1);
        }
        for (int y = 52; y <= 100; y += 12) f.line(b, 10, y, 220, y);
        assertTrue(detect(b).expressiveEvents().isEmpty());
    }

    @Test
    public void isolatedSideInkIsNotAStaffRailAcrossWhiteGap() {
        var b = wave(50, 76);
        for (int y = 79; y <= 109; y++) {
            double phase = Math.floorMod(y - 50, 12) / 12.;
            int x = 60 + (int) Math.round(phase < .5 ? phase * 12 : (1 - phase) * 12);
            f.line(b, x, y, x, y + 1);
        }
        f.line(b, 30, 78, 50, 78);
        f.line(b, 78, 78, 105, 78);
        assertTrue(detect(b).expressiveEvents().isEmpty());
    }

    public static void main(String[] args) {
        var t = new ScoreArpeggioExtentTest();
        for (int[] bounds : new int[][] {{50, 109}, {10, 220}, {50, 160}, {5, 109}}) {
            var s = t.detect(t.wave(bounds[0], bounds[1]));
            System.out.println(
                    Arrays.toString(bounds)
                            + ",events="
                            + s.expressiveEvents().size()
                            + ",members="
                            + ScoreArpeggioNotation.plan(s, List.of(0, 1, 2, 3), 4));
        }
    }
}
