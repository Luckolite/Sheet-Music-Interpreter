// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original regional rule samples and note endpoints; no notation pixels. */
public class VerifiedRegionalTieAlignmentTest {
    static final int W = 800, H = 400, STEP = 5;

    static StaffPitchTrack verified() {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {0, 250, 20}, new float[] {600, 310, 22}));
    }

    static float y(StaffPitchTrack t, float x, int step) {
        float[] f = t.at(x);
        return f[0] - step * f[1] * .5f;
    }

    @Test
    public void verifiedVariableGapAlignsMatchingWrittenPitch() {
        var t = verified();
        assertTrue(
                LocalTieStaffAlignment.same(t, 150, y(t, 150, STEP), 300, y(t, 300, STEP), STEP));
    }

    @Test
    public void aLinearEstimateDoesNotCountAsVerifiedRules() {
        var t = StaffPitchTrack.linear(W, 280, 21, .1f);
        assertFalse(
                LocalTieStaffAlignment.same(t, 150, y(t, 150, STEP), 300, y(t, 300, STEP), STEP));
    }

    @Test
    public void nullTrackCannotEstablishEndpointAlignment() {
        assertFalse(LocalTieStaffAlignment.same(null, 150, 210, 300, 225, STEP));
    }

    @Test
    public void nearbyDifferentPitchCannotUseTheVerifiedFrame() {
        var t = verified();
        assertFalse(
                LocalTieStaffAlignment.same(
                        t, 150, y(t, 150, STEP), 300, y(t, 300, STEP + 1), STEP));
    }

    @Test
    public void quantizedEventsStillRequireMatchingPrintedPositions() {
        var t = verified();
        assertFalse(
                LocalTieStaffAlignment.same(
                        t, 150, y(t, 150, STEP), 300, y(t, 300, STEP) + 7, STEP));
    }

    @Test
    public void excessiveLocalScaleDifferenceCannotMatch() {
        var t =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 250, 20}, new float[] {600, 310, 32}));
        assertFalse(LocalTieStaffAlignment.same(t, 20, y(t, 20, STEP), 580, y(t, 580, STEP), STEP));
    }

    static Object component(int x, float y) throws Exception {
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor.newInstance(
                100, x - 8, x + 8, Math.round(y) - 5, Math.round(y) + 5, (float) x, y);
    }

    static Object note(int measure, float position, int x, float yy, int step) throws Exception {
        return note(measure, position, x, yy, step, true);
    }

    static Object note(int measure, float position, int x, float yy, int step, boolean sustained)
            throws Exception {
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$DetectedNote");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var e =
                new ScoreNoteEvent(
                        measure,
                        position,
                        step,
                        0,
                        1,
                        yy / H,
                        false,
                        0,
                        sustained ? 0 : 1,
                        0,
                        sustained ? 2f : 0f,
                        1,
                        0,
                        0,
                        -1,
                        false,
                        0,
                        false,
                        0,
                        0,
                        1);
        return ctor.newInstance(e, component(x, yy), 21f);
    }

    static Object staff(StaffPitchTrack track) throws Exception {
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object staff = ctor.newInstance(200f, 280f, 20f);
        var f = c.getDeclaredField("pitchTrack");
        f.setAccessible(true);
        f.set(staff, track);
        return staff;
    }

    static int candidate(List<Object> notes, Object staff, boolean regional) throws Exception {
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        Method m =
                regional
                        ? OmrScoreInterpreter.class.getDeclaredMethod(
                                "previousSamePitch",
                                List.class,
                                int.class,
                                int.class,
                                byte[].class,
                                byte[].class,
                                int.class,
                                List.class)
                        : OmrScoreInterpreter.class.getDeclaredMethod(
                                "previousSamePitch",
                                List.class,
                                int.class,
                                int.class,
                                byte[].class,
                                byte[].class,
                                int.class);
        m.setAccessible(true);
        return (int)
                (regional
                        ? m.invoke(null, notes, 1, W, labels, gray, H, List.of(staff))
                        : m.invoke(null, notes, 1, W, labels, gray, H));
    }

    @Test
    public void regionalCandidateRetainsExistingRawArcRequirement() throws Exception {
        var t = verified();
        var notes =
                List.of(
                        note(0, .8f, 150, y(t, 150, STEP), STEP),
                        note(1, .1f, 300, y(t, 300, STEP), STEP));
        var s = staff(t);
        assertEquals(-1, candidate(notes, s, false));
        assertEquals(0, candidate(notes, s, true));
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "markTieContinuations",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        var marked = (List<?>) m.invoke(null, labels, gray, W, H, notes, List.of(s));
        var f = marked.get(1).getClass().getDeclaredField("event");
        f.setAccessible(true);
        assertFalse(((ScoreNoteEvent) f.get(marked.get(1))).tiedFromPrevious());
    }

    @Test
    public void unverifiedRegionalCandidateDoesNotOverrideTheRawGuard() throws Exception {
        var t = StaffPitchTrack.linear(W, 280, 21, .1f);
        var notes =
                List.of(
                        note(0, .8f, 150, y(t, 150, STEP), STEP),
                        note(1, .1f, 300, y(t, 300, STEP), STEP));
        assertEquals(-1, candidate(notes, staff(t), true));
    }

    @Test
    public void beamedEndpointsRetainTheOriginalLocalGuard() throws Exception {
        var t = verified();
        var notes =
                List.of(
                        note(0, .8f, 150, y(t, 150, STEP), STEP, false),
                        note(1, .1f, 300, y(t, 300, STEP), STEP, false));
        assertEquals(-1, candidate(notes, staff(t), true));
    }

    @Test
    public void writtenPitchDifferenceCannotSelectTheEarlierNote() throws Exception {
        var t = verified();
        var notes =
                List.of(
                        note(0, .8f, 150, y(t, 150, STEP), STEP),
                        note(1, .1f, 300, y(t, 300, STEP), STEP + 1));
        assertEquals(-1, candidate(notes, staff(t), true));
    }
}
