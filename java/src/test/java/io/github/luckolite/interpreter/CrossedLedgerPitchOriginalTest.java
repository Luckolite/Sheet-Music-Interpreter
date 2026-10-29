// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic interstaff oval, three physical ledgers and an owned opposite shaft. */
public class CrossedLedgerPitchOriginalTest {
    static final int W = 440, H = 380;

    static Object call(String name, Object... args) throws Exception {
        for (var m : OmrScoreInterpreter.class.getDeclaredMethods())
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try {
                    m.setAccessible(true);
                    return m.invoke(null, args);
                } catch (IllegalArgumentException ignored) {
                }
            }
        throw new AssertionError(name);
    }

    static void set(Object o, String name, Object value) throws Exception {
        var f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }

    static class Ink {
        byte[] g = new byte[W * H], l = new byte[W * H];
        Object upper, lower, head;

        Ink(String mode) throws Exception {
            Arrays.fill(g, (byte) 255);
            var sc =
                    Class.forName(OmrScoreInterpreter.class.getName() + "$Staff")
                            .getDeclaredConstructors()[0];
            sc.setAccessible(true);
            upper = sc.newInstance(40f, 96f, 14f);
            lower = sc.newInstance(208f, 264f, 14f);
            set(upper, "index", 0);
            set(upper, "count", 2);
            set(lower, "index", 1);
            set(lower, "count", 2);
            var hc =
                    Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                            .getDeclaredConstructors()[0];
            hc.setAccessible(true);
            head = hc.newInstance(245, 190, 210, 151, 167, 200f, 159f);
            for (int top : new int[] {40, 208})
                for (int y = top; y <= top + 56; y += 14) rect(25, y, 390, 1, 4);
            rect(25, 40, 1, 225, 1);
            rect(180, 110, 41, 1, 5);
            for (int k = 1; k <= 3; k++)
                if (!mode.equals("missing" + k))
                    rect(
                            mode.equals("broad") ? 25 : 180,
                            208 - k * 14,
                            mode.equals("broad") ? 390 : 41,
                            1,
                            5);
            if (mode.equals("competing"))
                for (int y : new int[] {124, 138, 152}) rect(180, y, 41, 1, 5);
            if (!mode.equals("noBody")) oval(200, 159);
            if (!mode.equals("noShaft"))
                rect(
                        mode.equals("detached") ? 230 : 209,
                        mode.equals("reaches") ? 80 : 120,
                        2,
                        mode.equals("reaches") ? 80 : 40,
                        1);
            if (mode.equals("broken")) rect(208, 138, 4, 6, 0);
            if (mode.equals("shifted")) rect(180, 180, 41, 1, 0);
        }

        void rect(int x, int y, int w, int h, int label) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) {
                    l[yy * W + xx] = (byte) label;
                    g[yy * W + xx] = (byte) (label == 0 ? 255 : 0);
                }
        }

        void oval(int cx, int cy) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if (Math.pow((x - cx) / 10., 2) + Math.pow((y - cy) / 8., 2) <= 1) {
                        l[y * W + x] = 2;
                        g[y * W + x] = 0;
                    }
        }

        void addActualCrossStaffPeer() {
            oval(130, 75);
            rect(121, 75, 2, 44, 1);
            for (int x = 121; x <= 210; x++)
                rect(x, Math.round(118 + (x - 121) * 2f / 88), 1, 4, 5);
        }

        Object owner() throws Exception {
            return call("printedLedgerOwner", g, W, H, List.of(upper, lower), head);
        }
    }

    @Test
    public void threeCompletePhysicalRulesRetainBassPitchAgainstOpposedShaft() throws Exception {
        var i = new Ink("valid");
        assertSame(i.lower, i.owner());
    }

    @Test
    public void physicalPitchFrameRemainsConsistentWithDestinationStaff() throws Exception {
        var i = new Ink("valid");
        Object owner = i.owner();
        assertSame(i.lower, owner);
        float[] p = (float[]) call("localStaffPitch", i.l, i.g, W, H, owner, i.head);
        assertEquals(15, (int) call("printedPitchStep", i.g, W, H, i.head, p[0], p[1]));
    }

    @Test
    public void everyLedgerIncludingTheOneBesideOvalIsRequired() throws Exception {
        for (int k = 1; k <= 3; k++) {
            var i = new Ink("missing" + k);
            assertNotSame(i.lower, i.owner());
        }
    }

    @Test
    public void competingCompleteLedgerSeriesCannotInventUniqueDestination() throws Exception {
        assertNull(new Ink("competing").owner());
    }

    @Test
    public void broadParallelLinesDoNotSupplyShortLedgerOwnership() throws Exception {
        var i = new Ink("broad");
        assertNotSame(i.lower, i.owner());
    }

    @Test
    public void completeRawOvalIsIndependentOfSemanticProposal() throws Exception {
        var i = new Ink("noBody");
        assertNotSame(i.lower, i.owner());
    }

    @Test
    public void ownedShaftIsRequired() throws Exception {
        for (String mode : new String[] {"noShaft", "detached", "broken", "reaches"}) {
            var i = new Ink(mode);
            assertNotSame(mode, i.lower, i.owner());
        }
    }

    @Test
    public void sourcePlanesStayUnchanged() throws Exception {
        var i = new Ink("valid");
        byte[] a = i.g.clone(), b = i.l.clone();
        i.owner();
        assertArrayEquals(a, i.g);
        assertArrayEquals(b, i.l);
    }

    @Test
    public void differentAdmittedSystemsCannotBorrowThisCrossedFrame() throws Exception {
        var i = new Ink("valid");
        set(i.lower, "index", 0);
        set(i.lower, "count", 1);
        assertNotSame(i.lower, i.owner());
    }

    @Test
    public void threeStaffSystemsCannotInventTheTwoStaffCrossingVoiceContract() throws Exception {
        var i = new Ink("valid");
        set(i.upper, "count", 3);
        set(i.lower, "count", 3);
        assertNotSame(i.lower, i.owner());
    }

    @Test
    public void unsupportedPhaseCannotBorrowTheThreeRuleCount() throws Exception {
        var i = new Ink("shifted");
        assertNotSame(i.lower, i.owner());
    }

    @Test
    public void mirroredCrossingRetainsUpperPhysicalStaff() throws Exception {
        var i = new Ink("valid");
        byte[] g = i.g.clone(), l = i.l.clone();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                i.g[(H - 1 - y) * W + W - 1 - x] = g[y * W + x];
                i.l[(H - 1 - y) * W + W - 1 - x] = l[y * W + x];
            }
        var sc = i.upper.getClass().getDeclaredConstructors()[0];
        sc.setAccessible(true);
        i.upper = sc.newInstance(115f, 171f, 14f);
        i.lower = sc.newInstance(283f, 339f, 14f);
        set(i.upper, "index", 0);
        set(i.upper, "count", 2);
        set(i.lower, "index", 1);
        set(i.lower, "count", 2);
        var hc = i.head.getClass().getDeclaredConstructors()[0];
        hc.setAccessible(true);
        i.head = hc.newInstance(245, 229, 249, 212, 228, 239f, 220f);
        assertSame(i.upper, i.owner());
    }

    @Test
    public void actualPipelineRetainsPrintedPitchAndProvesCrossStaffVoice() throws Exception {
        var i = new Ink("valid");
        i.addActualCrossStaffPeer();
        var score =
                OmrScoreInterpreter.analyze(
                        i.l, i.g, W, H, List.of(new MeasureRegion(.04f, .96f, .08f, .75f)));
        var target = score.notes().stream().filter(n -> Math.abs(n.pageY() * H - 159) < 2).toList();
        assertEquals(1, target.size());
        assertEquals(1, target.get(0).staffIndex());
        assertEquals(15, target.get(0).staffStep());
        assertTrue(target.get(0).crossStaffBeam());
        assertEquals(2, score.notes().stream().filter(ScoreNoteEvent::crossStaffBeam).count());
    }
}
