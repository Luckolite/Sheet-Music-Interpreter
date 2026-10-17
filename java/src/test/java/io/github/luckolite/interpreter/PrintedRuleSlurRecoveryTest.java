// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic slur and terminal rail; no scanned score coordinates or pixels. */
public class PrintedRuleSlurRecoveryTest {
    static final int W = 320, H = 280, X = 164;
    byte[] gray, labels;
    boolean rotated;
    Object lastStaff;
    StaffPitchTrack originalTrack;

    void pixel(int x, int y, int ink, int label) {
        if (rotated) {
            x = W - 1 - x;
            y = H - 1 - y;
        }
        gray[y * W + x] = (byte) ink;
        labels[y * W + x] = (byte) label;
    }

    void curvePixel(int x, int y, int ink) {
        int index = rotated ? (H - 1 - y) * W + W - 1 - x : y * W + x;
        if (ink < (gray[index] & 255)) pixel(x, y, ink, 5);
    }

    void draw(boolean rotate, boolean bowed, boolean connected) {
        rotated = rotate;
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 20; x < 300; x++)
                for (int y = 150 + line * 16; y <= 151 + line * 16; y++) pixel(x, y, 120, 4);
        for (int y = 193; y <= 207; y++)
            for (int x = 144; x <= 164; x++)
                if (Math.pow((x - 154) / 10., 2) + Math.pow((y - 200) / 7., 2) <= 1)
                    pixel(x, y, 20, 2);
        for (int y = 146; y < 200; y++) pixel(X, y, 20, 1);
        for (int x = 110; x <= X; x++) {
            int center = 149 + Math.round((X - x) * .2f);
            for (int y = center - 3; y <= center + 3; y++) pixel(x, y, 20, 5);
        }
        for (int x = X - 48; x <= X + 48; x++) {
            int dx = x - X;
            float center = bowed ? 139 + dx * dx * .0025f - dx * .15f : 139;
            for (int y = (int) Math.floor(center - 4); y <= (int) Math.ceil(center + 4); y++) {
                float distance = Math.abs(y - center);
                int ink = distance <= 2 ? 20 : Math.min(255, 20 + Math.round((distance - 2) * 130));
                if (ink < 255) curvePixel(x, y, ink);
            }
        }
        if (connected) for (int y = 137; y <= 144; y++) pixel(X, y, 20, 1);
    }

    boolean recovery() throws Exception {
        Class<?> type = OmrScoreInterpreter.class;
        Class<?> headType = Class.forName(type.getName() + "$Component");
        Class<?> staffType = Class.forName(type.getName() + "$Staff");
        var headConstructor = headType.getDeclaredConstructors()[0];
        headConstructor.setAccessible(true);
        Object head =
                rotated
                        ? headConstructor.newInstance(210, 155, 175, 72, 86, 165f, 79f)
                        : headConstructor.newInstance(210, 144, 164, 193, 207, 154f, 200f);
        var staffConstructor =
                staffType.getDeclaredConstructor(float.class, float.class, float.class);
        staffConstructor.setAccessible(true);
        Object staff =
                rotated
                        ? staffConstructor.newInstance(65f, 129f, 16f)
                        : staffConstructor.newInstance(150f, 214f, 16f);
        originalTrack =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {0, rotated ? 128.5f : 214.5f, 16},
                                new float[] {W - 1, rotated ? 128.5f : 214.5f, 16}));
        var trackField = staffType.getDeclaredField("pitchTrack");
        trackField.setAccessible(true);
        trackField.set(staff, originalTrack);
        lastStaff = staff;
        var method =
                type.getDeclaredMethod(
                        "fadedDoubleBeamJunction",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        headType,
                        staffType,
                        int[].class);
        method.setAccessible(true);
        int[] pale = rotated ? new int[] {155, 142, 1} : new int[] {164, 137, -1};
        return (boolean) method.invoke(null, labels, gray, W, H, head, staff, pale);
    }

    void referenceInk(int line, int ink, int label) {
        for (int x = 20; x < 300; x++)
            for (int y = 150 + line * 16; y <= 151 + line * 16; y++) pixel(x, y, ink, label);
    }

    @Test
    public void upperCurveCrossingProvedRuleCannotPromoteRail() throws Exception {
        draw(false, true, false);
        assertFalse(recovery());
    }

    @Test
    public void lowerCurveCrossingProvedRuleCannotPromoteRail() throws Exception {
        draw(true, true, false);
        assertFalse(recovery());
    }

    @Test
    public void threeActualReferencesAreRequired() throws Exception {
        draw(false, true, false);
        referenceInk(2, 255, 0);
        referenceInk(3, 255, 0);
        assertTrue(recovery());
    }

    @Test
    public void semanticSecondaryRailsCannotBecomeReferences() throws Exception {
        draw(false, true, false);
        for (int line : new int[] {2, 3, 4}) referenceInk(line, 120, 5);
        assertTrue(recovery());
    }

    @Test
    public void inconsistentReferenceMassCannotSupplyProof() throws Exception {
        draw(false, true, false);
        referenceInk(2, 20, 4);
        referenceInk(3, 20, 4);
        assertTrue(recovery());
    }

    @Test
    public void shiftedReferencePhaseCannotSupplyProof() throws Exception {
        draw(false, true, false);
        for (int line : new int[] {2, 3}) {
            referenceInk(line, 255, 0);
            for (int x = 20; x < 300; x++)
                for (int y = 153 + line * 16; y <= 154 + line * 16; y++) pixel(x, y, 120, 4);
        }
        assertTrue(recovery());
    }

    @Test
    public void connectedUpperShaftRetainsRecovery() throws Exception {
        draw(false, true, true);
        for (int y = 137; y <= 149; y++) pixel(X, y, 20, 1);
        assertTrue(recovery());
    }

    @Test
    public void connectedLowerShaftRetainsRecovery() throws Exception {
        draw(true, true, true);
        for (int y = 137; y <= 149; y++) pixel(X, y, 20, 1);
        assertTrue(recovery());
    }

    @Test
    public void straightSecondaryRailRetainsRecovery() throws Exception {
        draw(false, false, false);
        assertTrue(recovery());
    }

    @Test
    public void brokenCurveCannotBorrowRuleInk() throws Exception {
        draw(false, true, false);
        for (int y = 130; y <= 149; y++) pixel(X + 30, y, 255, 0);
        assertTrue(recovery());
    }

    @Test
    public void sourceGrayAndLabelsStayExact() throws Exception {
        draw(false, true, false);
        byte[] before = gray.clone(), masks = labels.clone();
        assertFalse(recovery());
        assertArrayEquals(before, gray);
        assertArrayEquals(masks, labels);
    }

    @Test
    public void originalPitchFrameIsNotReplaced() throws Exception {
        draw(false, true, false);
        assertFalse(recovery());
        var field = lastStaff.getClass().getDeclaredField("pitchTrack");
        field.setAccessible(true);
        assertSame(originalTrack, field.get(lastStaff));
    }
}
