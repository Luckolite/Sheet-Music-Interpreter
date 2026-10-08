// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original antialiased complete bows at two independently specified printed row ends. */
public class PairedSystemTieOwnershipTest {
    static final int W = 800, H = 800, AX = 580, BX = 100, STEP = 11;
    static final float GAP = 16;

    record Raster(
            byte[] labels,
            byte[] gray,
            StaffPitchTrack first,
            StaffPitchTrack last,
            int headRadius,
            int printedStep) {}

    static StaffPitchTrack track(float bottom, boolean verified, float curve) {
        return verified
                ? StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {0, bottom, GAP},
                                new float[] {400, bottom + curve, GAP},
                                new float[] {799, bottom + curve * .4f, GAP}))
                : StaffPitchTrack.linear(W, bottom, GAP, 0);
    }

    static float pitch(StaffPitchTrack t, float x, int step) {
        float[] f = t.at(x);
        return f[0] - step * f[1] * .5f;
    }

    static Raster draw(String outgoing, String incoming, boolean verified, boolean opposite) {
        return draw(outgoing, incoming, verified, opposite, 7, .375, 75, 10);
    }

    static Raster draw(
            String outgoing,
            String incoming,
            boolean verified,
            boolean opposite,
            int headRadius,
            double end,
            double amplitude,
            float curve) {
        return draw(
                outgoing,
                incoming,
                verified,
                opposite,
                headRadius,
                end,
                amplitude,
                curve,
                STEP,
                .55,
                .7,
                48);
    }

    static Raster draw(
            String outgoing,
            String incoming,
            boolean verified,
            boolean opposite,
            int headRadius,
            double end,
            double amplitude,
            float curve,
            int printedStep,
            double offset,
            double rise,
            int span) {
        var a = track(250, verified, curve);
        var b = track(600, verified, curve);
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) {
                double ink = 0, paper = 150 + x * .03;
                for (var row : List.of(a, b))
                    for (int rule = 0; rule < 5; rule++) {
                        double d = y - (row.at(x)[0] - rule * GAP);
                        ink += 65 * Math.exp(-d * d / 1.2);
                        if (Math.abs(d) < 1) labels[y * W + x] = 4;
                    }
                for (int row = 0; row < 2; row++) {
                    var t = row == 0 ? a : b;
                    int hx = row == 0 ? AX : BX;
                    String mode = row == 0 ? outgoing : incoming;
                    int side = row == 1 && opposite ? 1 : -1;
                    double left = row == 0 ? hx - 3 : hx - span,
                            right = row == 0 ? hx + span : hx + end * GAP;
                    if (mode.equals("longSlur")) {
                        left -= 30;
                        right += 30;
                    }
                    double q = (x - left) / (right - left);
                    if (q >= 0
                            && q <= 1
                            && !mode.equals("blank")
                            && !(mode.equals("half") && q > .5)) {
                        double bow = rise * 4 * q * (1 - q);
                        if (mode.equals("beam")) bow = 0;
                        if (mode.equals("hairpin")) bow = .7 * (1 - Math.abs(2 * q - 1));
                        double center = pitch(t, x, printedStep) + side * GAP * (offset + bow),
                                d = y - center;
                        ink += amplitude * Math.exp(-d * d / (2 * .9 * .9));
                        if (mode.equals("ring") || mode.equals("ringWithTail")) {
                            double dy =
                                    y - (pitch(t, x, printedStep) + side * GAP * (offset - bow));
                            ink += amplitude * Math.exp(-dy * dy / (2 * .9 * .9));
                        }
                        if (mode.equals("text")
                                && (Math.abs(x - left - 12) < 2 || Math.abs(x - right + 12) < 2)
                                && side * (y - center) >= 0
                                && side * (y - center) < GAP) ink = 100;
                    }
                    if (mode.equals("oppositeSlur") || mode.equals("oppositeTouchingSlur")) {
                        double qOther = (x - (left - 26)) / (right - left + 52);
                        double headQ = (hx - (left - 26)) / (right - left + 52);
                        double otherOffset =
                                mode.equals("oppositeTouchingSlur")
                                        ? -rise * 4 * headQ * (1 - headQ)
                                        : .4;
                        if (qOther >= 0 && qOther <= 1) {
                            double d =
                                    y
                                            - (pitch(t, x, printedStep)
                                                    - side
                                                            * GAP
                                                            * (otherOffset
                                                                    + rise
                                                                            * 4
                                                                            * qOther
                                                                            * (1 - qOther)));
                            ink += amplitude * Math.exp(-d * d / (2 * .9 * .9));
                        }
                    }
                    if (mode.equals("ringWithTail") && x >= right - 2 && x <= right + 16) {
                        double d =
                                y
                                        - (pitch(t, x, printedStep)
                                                + side
                                                        * GAP
                                                        * (offset
                                                                - 4
                                                                        * rise
                                                                        * (x - right)
                                                                        / (right - left)));
                        ink += amplitude * Math.exp(-d * d / (2 * .9 * .9));
                    }
                    double dx = (x - hx) / (double) headRadius,
                            dy = (y - pitch(t, hx, printedStep)) / 5.;
                    if (dx * dx + dy * dy < 1) {
                        ink = 100;
                        labels[y * W + x] = 2;
                    }
                }
                gray[y * W + x] = (byte) Math.round(Math.max(0, Math.min(255, paper - ink)));
            }
        return new Raster(labels, gray, a, b, headRadius, printedStep);
    }

    static Object make(String name, Object... args) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + name)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(args);
    }

    static Object note(int bar, int x, float y, int step, int headRadius) throws Exception {
        var e =
                new ScoreNoteEvent(
                        bar,
                        bar == 0 ? .9f : .04f,
                        step,
                        0,
                        1,
                        y / H,
                        false,
                        0,
                        1,
                        2,
                        0,
                        1,
                        0,
                        0,
                        30);
        return make(
                "DetectedNote",
                e,
                make(
                        "Component",
                        100,
                        x - headRadius,
                        x + headRadius,
                        Math.round(y) - 5,
                        Math.round(y) + 5,
                        (float) x,
                        y),
                GAP);
    }

    static Object staff(StaffPitchTrack track) throws Exception {
        float bottom = track.at(400)[0];
        Object result = make("Staff", bottom - 4 * GAP, bottom, GAP);
        var f = result.getClass().getDeclaredField("pitchTrack");
        f.setAccessible(true);
        f.set(result, track);
        return result;
    }

    static boolean detect(Raster r, boolean wrongPitch, boolean wrongPhase) throws Exception {
        Object before = note(0, AX, pitch(r.first, AX, r.printedStep), r.printedStep, r.headRadius),
                after =
                        note(
                                1,
                                BX,
                                pitch(r.last, BX, r.printedStep) + (wrongPhase ? 8 : 0),
                                wrongPitch ? r.printedStep + 1 : r.printedStep,
                                r.headRadius);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "verifiedSystemTieContinuation",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        before.getClass(),
                        after.getClass());
        m.setAccessible(true);
        return (boolean)
                m.invoke(
                        null,
                        r.labels,
                        r.gray,
                        W,
                        H,
                        List.of(staff(r.first), staff(r.last)),
                        before,
                        after);
    }

    static boolean proof(String a, String b) throws Exception {
        return detect(draw(a, b, true, false), false, false);
    }

    static boolean pipeline(Raster r) throws Exception {
        Object before = note(0, AX, pitch(r.first, AX, r.printedStep), r.printedStep, r.headRadius),
                after = note(1, BX, pitch(r.last, BX, r.printedStep), r.printedStep, r.headRadius);
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
        List<?> output =
                (List<?>)
                        m.invoke(
                                null,
                                r.labels,
                                r.gray,
                                W,
                                H,
                                List.of(before, after),
                                List.of(staff(r.first), staff(r.last)));
        var e = after.getClass().getDeclaredField("event");
        e.setAccessible(true);
        return ((ScoreNoteEvent) e.get(output.get(1))).tiedFromPrevious();
    }

    public static void main(String[] args) throws Exception {
        for (double ink : new double[] {55, 75})
            System.out.println(
                    "wide returned shoulder amplitude="
                            + ink
                            + " caller="
                            + pipeline(draw("bow", "bow", true, false, 11, .7, ink, -10)));
    }

    @Test
    public void wideAntialiasedReturningShouldersReachTheFullCaller() throws Exception {
        assertTrue(pipeline(draw("bow", "bow", true, false, 11, .7, 75, -10)));
    }

    @Test
    public void shadedWideReturningShouldersReachTheFullCaller() throws Exception {
        assertTrue(pipeline(draw("bow", "bow", true, false, 11, .7, 55, -10)));
    }

    @Test
    public void aWideRingStillCannotProveContinuation() throws Exception {
        assertFalse(detect(draw("ring", "ring", true, false, 11, .7, 75, -10), false, false));
    }

    @Test
    public void aWideLongSlurStillCannotSupplyTheOwnedEnd() throws Exception {
        assertFalse(detect(draw("bow", "longSlur", true, false, 11, .7, 75, -10), false, false));
    }

    @Test
    public void wideClippedShouldersStillCannotProveContinuation() throws Exception {
        assertFalse(detect(draw("bow", "half", true, false, 11, .7, 75, -10), false, false));
    }

    @Test
    public void aWideLetterStillCannotProveContinuation() throws Exception {
        assertFalse(detect(draw("text", "text", true, false, 11, .7, 75, -10), false, false));
    }

    @Test
    public void wideHairpinsStillCannotProveContinuation() throws Exception {
        assertFalse(detect(draw("hairpin", "hairpin", true, false, 11, .7, 75, -10), false, false));
    }

    @Test
    public void aDistinctOppositeSlurBeyondTheFreeTipDoesNotRemoveTheTie() throws Exception {
        assertTrue(proof("oppositeSlur", "oppositeSlur"));
    }

    @Test
    public void aWideDistinctOppositeSlurBeyondTheFreeTipDoesNotRemoveTheTie() throws Exception {
        assertTrue(
                detect(
                        draw("oppositeSlur", "oppositeSlur", true, false, 11, .7, 75, -10),
                        false,
                        false));
    }

    @Test
    public void aClosedOutlineWithAConnectedTailStillCannotSupplyTheTie() throws Exception {
        assertFalse(proof("ringWithTail", "ringWithTail"));
    }

    @Test
    public void allSixCounterPhasesRemainNegativeAtPairedRowEnds() throws Exception {
        for (double[] v :
                new double[][] {
                    {5, 45, .2, 1},
                    {5, 55, .8, .25},
                    {7, 45, .2, 1},
                    {7, 45, .2, 1.25},
                    {7, 55, .8, .25},
                    {7, 55, 1.1, 1}
                })
            assertFalse(
                    Arrays.toString(v),
                    detect(
                            draw(
                                    "ring", "ring", true, false, 7, .375, 75, 10, (int) v[0], v[2],
                                    v[3], (int) v[1]),
                            false,
                            false));
    }

    @Test
    public void twoCompleteOwnedBowsProveContinuation() throws Exception {
        assertTrue(proof("bow", "bow"));
    }

    @Test
    public void loneOutgoingBowCannotProveContinuation() throws Exception {
        assertFalse(proof("bow", "blank"));
    }

    @Test
    public void loneIncomingBowCannotProveContinuation() throws Exception {
        assertFalse(proof("blank", "bow"));
    }

    @Test
    public void clippedIncomingHalfCannotProveContinuation() throws Exception {
        assertFalse(proof("bow", "half"));
    }

    @Test
    public void clippedOutgoingHalfCannotProveContinuation() throws Exception {
        assertFalse(proof("half", "bow"));
    }

    @Test
    public void longSlurCannotSupplyTheHeadOwnedEnd() throws Exception {
        assertFalse(proof("bow", "longSlur"));
    }

    @Test
    public void ringsCannotSupplyContinuation() throws Exception {
        assertFalse(proof("ring", "ring"));
    }

    @Test
    public void beamsCannotSupplyContinuation() throws Exception {
        assertFalse(proof("beam", "beam"));
    }

    @Test
    public void hairpinsCannotSupplyContinuation() throws Exception {
        assertFalse(proof("hairpin", "hairpin"));
    }

    @Test
    public void letterUprightsCannotSupplyContinuation() throws Exception {
        assertFalse(proof("text", "text"));
    }

    @Test
    public void oppositeSidesCannotShareContinuation() throws Exception {
        assertFalse(detect(draw("bow", "bow", true, true), false, false));
    }

    @Test
    public void unverifiedFramesCannotProveContinuation() throws Exception {
        assertFalse(detect(draw("bow", "bow", false, false), false, false));
    }

    @Test
    public void differentWrittenPitchesCannotShareContinuation() throws Exception {
        assertFalse(detect(draw("bow", "bow", true, false), true, false));
    }

    @Test
    public void displacedPrintedPhaseCannotShareContinuation() throws Exception {
        assertFalse(detect(draw("bow", "bow", true, false), false, true));
    }

    @Test
    public void proofPreservesEveryPixel() throws Exception {
        var r = draw("bow", "bow", true, false);
        byte[] l = r.labels.clone(), g = r.gray.clone();
        detect(r, false, false);
        assertArrayEquals(l, r.labels);
        assertArrayEquals(g, r.gray);
    }
}
