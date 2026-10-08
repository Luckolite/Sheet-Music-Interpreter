// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original shared-shaft voices, parallel rails, short stubs and ordinary paper rules. */
public class SharedStemBeamOwnershipTest {
    static final int W = 480, H = 520, G = 16, X = 250, Y = 400;
    byte[] gray, labels;

    void rect(int left, int right, int top, int bottom, int label) {
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                gray[y * W + x] = 25;
                labels[y * W + x] = (byte) label;
            }
    }

    void oval(int cx, int cy) {
        for (int y = cy - 8; y <= cy + 8; y++)
            for (int x = cx - 12; x <= cx + 12; x++)
                if (Math.pow((x - cx) / 12., 2) + Math.pow((y - cy) / 8., 2) <= 1)
                    rect(x, x, y, y, 2);
    }

    void draw(int beams, boolean stub, boolean other) {
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        oval(X, Y);
        if (other) oval(X + 24, Y - 160);
        rect(X + 11, X + 13, Y - 160, Y, 1);
        for (int i = 0; i < beams; i++)
            rect(stub && i > 0 ? X - 10 : X - 130, X + 13, Y - 56 - i * 12, Y - 49 - i * 12, 1);
        if (other) {
            rect(X - 130, X + 13, Y - 96, Y - 89, 1);
            rect(X - 10, X + 13, Y - 84, Y - 77, 1);
        }
    }

    int count(boolean mirror) {
        if (!mirror)
            return SharedStemBeamOwnership.count(
                    gray, labels, W, H, G, X - 12, X + 12, Y - 8, Y + 8, X, Y);
        byte[] g = gray.clone(), l = labels.clone();
        for (int i = 0; i < g.length; i++) {
            gray[g.length - 1 - i] = g[i];
            labels[l.length - 1 - i] = l[i];
        }
        return SharedStemBeamOwnership.count(
                gray,
                labels,
                W,
                H,
                G,
                W - 1 - (X + 12),
                W - 1 - (X - 12),
                H - 1 - (Y + 8),
                H - 1 - (Y - 8),
                W - 1 - X,
                H - 1 - Y);
    }

    @Test
    public void lowerRailDoesNotBorrowOpposingVoice() {
        draw(1, false, true);
        assertEquals(1, count(false));
    }

    @Test
    public void mirroredRailKeepsItsOwnVoice() {
        draw(1, false, true);
        assertEquals(1, count(true));
    }

    @Test
    public void fullDoubleRailsStayDouble() {
        draw(2, false, true);
        assertEquals(2, count(false));
    }

    @Test
    public void fullTripleRailsStayTriple() {
        draw(3, false, true);
        assertEquals(3, count(false));
    }

    @Test
    public void genuineShortSecondaryStubIsPreserved() {
        draw(2, true, true);
        assertEquals(2, count(false));
    }

    @Test
    public void ordinarySingleShaftIsOutsideRepair() {
        draw(1, false, false);
        assertEquals(-1, count(false));
    }

    @Test
    public void sharedQuarterWithoutBeamIsUncertain() {
        draw(0, false, true);
        rect(X - 130, X + 13, Y - 96, Y - 77, 0);
        assertEquals(-1, count(false));
    }

    @Test
    public void thinPaperRulesDoNotAddRails() {
        draw(1, false, true);
        for (int y = Y - 144; y < Y; y += G) rect(20, W - 21, y, y + 1, 0);
        assertEquals(1, count(false));
    }

    @Test
    public void thickSegmentedStaffRulesDoNotAddRails() {
        draw(1, false, true);
        for (int y = Y - 144; y < Y; y += G)
            rect(20, W - 21, y, y + 3, OmrMeasurePostProcessor.STAFF);
        assertEquals(1, count(false));
    }

    @Test
    public void sourcePlanesRemainReadOnly() {
        draw(1, false, true);
        byte[] g = gray.clone(), l = labels.clone();
        count(false);
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    void clear() {
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        oval(X, Y);
        oval(X + 24, Y - 160);
        rect(X + 11, X + 13, Y - 160, Y, 1);
    }

    void inclined(int left, int right, float root, float slope) {
        for (int x = left; x <= right; x++) {
            int y = Math.round(root + (x - (X + 12)) * slope);
            rect(x, x, y - 3, y + 4, 1);
        }
    }

    @Test
    public void inclinedRailDoesNotBorrowNearbyFlatVoice() {
        clear();
        inclined(X - 130, X + 13, Y - 56, -.25f);
        inclined(X - 130, X + 13, Y - 66, 0);
        inclined(X - 10, X + 13, Y - 78, 0);
        assertEquals(1, count(false));
    }

    @Test
    public void shallowInclineDoesNotBorrowNearbyFlatVoice() {
        clear();
        inclined(X - 130, X + 13, Y - 56, -.15f);
        inclined(X - 130, X + 13, Y - 66, 0);
        inclined(X - 10, X + 13, Y - 78, 0);
        assertEquals(1, count(false));
    }

    @Test
    public void inclinedDoubleRailsStayDouble() {
        clear();
        inclined(X - 130, X + 13, Y - 56, -.25f);
        inclined(X - 130, X + 13, Y - 68, -.25f);
        inclined(X - 130, X + 13, Y - 100, 0);
        assertEquals(2, count(false));
    }

    @Test
    public void inclinedShortSecondaryStubIsPreserved() {
        clear();
        inclined(X - 130, X + 13, Y - 56, -.25f);
        inclined(X - 10, X + 13, Y - 68, -.25f);
        inclined(X - 130, X + 13, Y - 100, 0);
        assertEquals(2, count(false));
    }

    @Test
    public void shortSharedShaftReadsItsNearestRail() {
        clear();
        oval(X, Y + 48);
        rect(X + 11, X + 13, Y, Y + 48, 1);
        inclined(X - 130, X + 13, Y - 30, -.25f);
        assertEquals(1, count(false));
    }

    @Test
    public void separateQuarterStemIsNotOverwritten() {
        clear();
        rect(X - 13, X - 11, Y, Y + 56, 1);
        inclined(X - 130, X - 11, Y + 56, -.25f);
        assertEquals(-1, count(false));
    }

    @Test
    public void ledgerLinkedForeignStemDoesNotReplaceOwnedBeam() {
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        oval(X, Y);
        oval(X + 27, Y - 112);
        rect(X - 13, X - 11, Y, Y + 56, 1);
        rect(X + 15, X + 17, Y - 112, Y + 40, 1);
        rect(X - 18, X + 17, Y, Y, 0);
        for (int x = X - 130; x <= X - 11; x++) {
            int y = Math.round(Y + 56 + (x - (X - 12)) * (-.25f));
            rect(x, x, y - 3, y + 4, 1);
        }
        assertEquals(1, count(false));
    }

    @Test
    public void shortSecondaryTowardHeadIsPreserved() {
        clear();
        inclined(X - 130, X + 13, Y - 56, 0);
        inclined(X - 10, X + 13, Y - 44, 0);
        inclined(X - 130, X + 13, Y - 100, 0);
        assertEquals(2, count(false));
    }

    @Test
    public void quarterShaftCannotJumpToForeignRail() {
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        oval(X, Y);
        oval(X + 24, Y - 160);
        rect(X + 11, X + 13, Y - 56, Y, 1);
        inclined(X - 130, X + 13, Y - 70, 0);
        assertEquals(-1, count(false));
    }

    @Test
    public void nearlyCoincidentForeignShaftCannotExtendQuarter() {
        gray = new byte[W * H];
        labels = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        oval(X, Y);
        oval(X + 24, Y - 160);
        rect(X + 11, X + 11, Y - 70, Y, 1);
        rect(X + 13, X + 13, Y - 56, Y, 1);
        rect(X - 130, X + 11, Y - 73, Y - 66, 1);
        assertEquals(-1, count(false));
    }

    int caller(boolean mirror) throws Exception {
        if (mirror) count(true);
        Class<?> hc = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = hc.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        float cx = mirror ? W - 1 - X : X, cy = mirror ? H - 1 - Y : Y;
        int left = mirror ? W - 1 - (X + 12) : X - 12, right = mirror ? W - 1 - (X - 12) : X + 12;
        int top = mirror ? H - 1 - (Y + 8) : Y - 8, bottom = mirror ? H - 1 - (Y - 8) : Y + 8;
        Object head = ctor.newInstance(300, left, right, top, bottom, cx, cy);
        Class<?> sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var staffCtor = sc.getDeclaredConstructor(float.class, float.class, float.class);
        staffCtor.setAccessible(true);
        Object localFrame = staffCtor.newInstance(cy - G * 4, cy, (float) G);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        hc,
                        sc,
                        List.class,
                        int.class);
        method.setAccessible(true);
        return (int) method.invoke(null, labels, gray, W, H, head, localFrame, List.of(head), 2);
    }

    @Test
    public void originalSystemCountSurvivesLocalFrame() throws Exception {
        draw(1, false, true);
        assertEquals(1, caller(false));
    }

    @Test
    public void mirroredMultiStaffCallerKeepsOwnership() throws Exception {
        draw(1, false, true);
        assertEquals(1, caller(true));
    }
}
