// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original primitive geometry; no score pixels, labels, fonts or song identities. */
public final class TallCommonShaftChordTest {
    @Test
    public void sixGapCommonShaftSharesItsExternalEighthBeam() throws Exception {
        assertTrue(
                new TallCommonShaftDiagnostic(96, false, false)
                        .run().stream().allMatch(n -> n.beamCount() == 1));
    }

    @Test
    public void fiveGapExistingChordStillSharesTheBeam() throws Exception {
        assertTrue(
                new TallCommonShaftDiagnostic(80, false, false)
                        .run().stream().allMatch(n -> n.beamCount() == 1));
    }

    @Test
    public void widerThanSixGapCentresStayUnqualified() throws Exception {
        assertEquals(0, new TallCommonShaftDiagnostic(112, false, false).run().get(0).beamCount());
    }

    @Test
    public void lessThanQuarterGapPitchResidualIsAdmitted() throws Exception {
        var d = new TallCommonShaftDiagnostic(100, false, false);
        replaceHead(d, 2, 100, 120, 183, 197, 110f, 90 + 96 + 16 * .249f);
        assertEquals(1, d.run().get(0).beamCount());
    }

    @Test
    public void greaterThanQuarterGapPitchResidualIsRejected() throws Exception {
        var d = new TallCommonShaftDiagnostic(100, false, false);
        replaceHead(d, 2, 100, 120, 183, 197, 110f, 90 + 96 + 16 * .251f);
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void wrongWrittenRowCannotOverridePrintedGeometry() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        var n = d.notes.get(2);
        var e = event(d, n);
        var hc = d.detected.getDeclaredMethod("head");
        hc.setAccessible(true);
        var nc = d.detected.getDeclaredConstructors()[0];
        nc.setAccessible(true);
        var wrong =
                new ScoreNoteEvent(
                        e.measureIndex(),
                        e.positionInMeasure(),
                        -1,
                        e.staffIndex(),
                        e.staffCount(),
                        e.pageY(),
                        false,
                        0,
                        1,
                        2,
                        0f);
        d.notes.set(2, nc.newInstance(wrong, hc.invoke(n), (float) d.G));
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void brokenCommonShaftCannotDonateRhythm() throws Exception {
        assertEquals(0, new TallCommonShaftDiagnostic(96, true, false).run().get(0).beamCount());
    }

    @Test
    public void independentOpposingVoiceStaysQuarter() throws Exception {
        assertEquals(0, new TallCommonShaftDiagnostic(96, false, true).run().get(0).beamCount());
    }

    @Test
    public void oversizedOvalCannotExtendTheWindow() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        for (int y = 78; y <= 102; y++)
            for (int x = 100; x <= 120; x++)
                if (Math.pow((x - 110) / 10d, 2) + Math.pow((y - 90) / 12d, 2) <= 1)
                    d.pixel(x, y, 2);
        replaceHead(d, 0, 100, 120, 78, 102, 110f, 90f);
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void nearbyUnconnectedThirdHeadCannotDonateRhythm() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        for (int y = 179; y <= 193; y++)
            for (int x = 102; x <= 120; x++) {
                d.gray[y * d.W + x] = (byte) 255;
                d.labels[y * d.W + x] = 0;
            }
        for (int y = 179; y <= 193; y++)
            for (int x = 120; x <= 140; x++)
                if (Math.pow((x - 130) / 10d, 2) + Math.pow((y - 186) / 7d, 2) <= 1)
                    d.pixel(x, y, 2);
        replaceHead(d, 2, 120, 140, 179, 193, 130f, 186f);
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void noExternalBeamCannotCreateAnEighth() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        for (int y = 235; y < 241; y++)
            for (int x = 102; x < 180; x++) {
                d.gray[y * d.W + x] = (byte) 255;
                d.labels[y * d.W + x] = 0;
            }
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void graceHeadDoesNotJoinTheExtendedChord() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        replace(d, 0, NoteOrnament.GRACE, 1f, 0);
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void openDurationDoesNotJoinTheExtendedChord() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        replace(d, 0, 0, 2f, 0);
        assertEquals(0, d.run().get(0).beamCount());
    }

    @Test
    public void identityAndCallerPixelsArePreserved() throws Exception {
        var d = new TallCommonShaftDiagnostic(96, false, false);
        var before = new ArrayList<ScoreNoteEvent>();
        var a = d.labels.clone();
        var b = d.gray.clone();
        for (int i = 0; i < 3; i++) {
            replace(d, i, 0, i == 0 ? 1f : 0f, i == 0 ? 0 : 1);
            before.add(event(d, d.notes.get(i)));
        }
        var result = d.run();
        for (int i = 0; i < 3; i++)
            for (var c : ScoreNoteEvent.class.getRecordComponents()) {
                if (c.getName().equals("beamCount") || c.getName().equals("unbeamedDurationBeats"))
                    continue;
                assertEquals(
                        c.getName(),
                        c.getAccessor().invoke(before.get(i)),
                        c.getAccessor().invoke(result.get(i)));
            }
        assertArrayEquals(a, d.labels);
        assertArrayEquals(b, d.gray);
    }

    static ScoreNoteEvent event(TallCommonShaftDiagnostic d, Object n) throws Exception {
        var e = d.detected.getDeclaredMethod("event");
        e.setAccessible(true);
        return (ScoreNoteEvent) e.invoke(n);
    }

    static void replaceHead(
            TallCommonShaftDiagnostic d, int i, int l, int r, int t, int b, float x, float y)
            throws Exception {
        var hc = d.component.getDeclaredConstructors()[0];
        hc.setAccessible(true);
        var nc = d.detected.getDeclaredConstructors()[0];
        nc.setAccessible(true);
        Object h = hc.newInstance(220, l, r, t, b, x, y);
        d.notes.set(i, nc.newInstance(event(d, d.notes.get(i)), h, (float) d.G));
    }

    static void replace(TallCommonShaftDiagnostic d, int i, int art, float duration, int beam)
            throws Exception {
        Object n = d.notes.get(i);
        var old = event(d, n);
        var h = d.detected.getDeclaredMethod("head");
        h.setAccessible(true);
        var nc = d.detected.getDeclaredConstructors()[0];
        nc.setAccessible(true);
        var e =
                new ScoreNoteEvent(
                                old.measureIndex(),
                                old.positionInMeasure(),
                                old.staffStep(),
                                old.staffIndex(),
                                old.staffCount(),
                                old.pageY(),
                                true,
                                0,
                                beam,
                                2,
                                duration,
                                5,
                                .5f,
                                art,
                                30,
                                false,
                                .25f,
                                false,
                                1,
                                2,
                                4)
                        .withStemDirection(-1)
                        .withKind(old.kind());
        d.notes.set(i, nc.newInstance(e, h.invoke(n), (float) d.G));
    }
}
