// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric ink and an authored rest proposal test adjudication, not rest detection. */
public final class ShortStemBeamAdjudicationOriginalTest {
    static final int W = 400, H = 220;

    static class Ink {
        byte[] l = new byte[W * H], g = new byte[W * H];
        int peerY = 46, peerEnd = 92;

        Ink(String kind) {
            Arrays.fill(g, (byte) 255);
            if (kind.equals("otherSlope")) peerEnd = 104;
            if (kind.equals("crossStaff")) peerY = 32;
            for (int k = 0; k < 5; k++) for (int x = 20; x < 380; x++) put(x, 60 + k * 14, 4);
            stem(145, 67, 94);
            stem(207, peerY, peerEnd - 4);
            if (!kind.equals("quarter")) beam(145, 94, 207, peerEnd - 4, 5, kind);
            if (kind.equals("double") || kind.equals("triple"))
                beam(145, 86, 207, peerEnd - 12, 5, "single");
            if (kind.equals("triple")) beam(145, 78, 207, peerEnd - 20, 5, "single");
            if (kind.equals("hook")) beam(169, 84, 185, 82, 5, "single");
            head(152, 67);
            head(214, peerY);
            if (kind.equals("broken")) g[82 * W + 145] = (byte) 255;
            if (kind.equals("diagonalRest")) {
                for (int y = 75; y < 91; y++) g[y * W + 145] = (byte) 255;
                for (int y = 75; y < 91; y++) g[y * W + 145 - (y - 75) / 3] = 0;
            }
            if (kind.equals("unsupported"))
                for (int y = 40; y <= 52; y++)
                    for (int x = 208; x <= 220; x++)
                        if (l[y * W + x] == 2) g[y * W + x] = (byte) 255;
        }

        void put(int x, int y, int label) {
            l[y * W + x] = (byte) label;
            g[y * W + x] = 0;
        }

        void head(int cx, int cy) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - 9; x <= cx + 9; x++) {
                    double xx = (x - cx) / 7.0, yy = (y - cy + (x - cx) * .35) / 5.5;
                    if (xx * xx + yy * yy <= 1) put(x, y, 2);
                }
        }

        void stem(int x, int from, int to) {
            for (int y = from; y <= to; y++) put(x, y, 1);
        }

        void beam(int x1, int y1, int x2, int y2, int thickness, String kind) {
            for (int x = x1; x <= x2; x++) {
                if (kind.equals("partial") && x > 165 && x < 182) continue;
                int y = Math.round(y1 + (y2 - y1) * (x - x1) / (float) (x2 - x1));
                if (kind.equals("slur"))
                    y += Math.round(12 * Math.sin(Math.PI * (x - x1) / (x2 - x1)));
                for (int d = 0; d < thickness; d++) put(x, y + d, 5);
            }
        }

        boolean certificate(int[] a, int[] b, int ownLeft) throws Exception {
            // The released adjudication had no short-shaft rail certificate; absence is its exact
            // false result.
            Class<?> c;
            try {
                c = Class.forName("io.github.luckolite.interpreter.ShortStemSingleBeamSupport");
            } catch (ClassNotFoundException absent) {
                return false;
            }
            Class<?> head = Class.forName(c.getName() + "$Head");
            Constructor<?> hc =
                    head.getDeclaredConstructor(
                            int.class, int.class, int.class, int.class, float.class);
            hc.setAccessible(true);
            Object own = hc.newInstance(ownLeft, 61, 158, 73, 67f),
                    peer = hc.newInstance(208, peerY - 6, 220, peerY + 6, (float) peerY);
            Method m =
                    c.getDeclaredMethod(
                            "proves",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            head,
                            int[].class,
                            head,
                            int[].class,
                            float.class);
            m.setAccessible(true);
            return (Boolean) m.invoke(null, l, g, W, H, own, a, peer, b, 14f);
        }

        boolean certificate() throws Exception {
            return certificate(new int[] {145, 98, 1}, new int[] {207, peerEnd, 1}, 146);
        }

        boolean mirroredCertificate() throws Exception {
            byte[] ml = l, mg = g;
            l = new byte[ml.length];
            g = new byte[mg.length];
            for (int n = 0; n < l.length; n++) {
                l[l.length - 1 - n] = ml[n];
                g[g.length - 1 - n] = mg[n];
            }
            try {
                Class<?> c;
                try {
                    c = Class.forName("io.github.luckolite.interpreter.ShortStemSingleBeamSupport");
                } catch (ClassNotFoundException absent) {
                    return false;
                }
                Class<?> head = Class.forName(c.getName() + "$Head");
                Constructor<?> hc =
                        head.getDeclaredConstructor(
                                int.class, int.class, int.class, int.class, float.class);
                hc.setAccessible(true);
                Object
                        own =
                                hc.newInstance(
                                        W - 1 - 158,
                                        H - 1 - 73,
                                        W - 1 - 146,
                                        H - 1 - 61,
                                        (float) H - 1 - 67),
                        peer =
                                hc.newInstance(
                                        W - 1 - 220,
                                        H - 1 - (peerY + 6),
                                        W - 1 - 208,
                                        H - 1 - (peerY - 6),
                                        (float) H - 1 - peerY);
                Method m =
                        c.getDeclaredMethod(
                                "proves",
                                byte[].class,
                                byte[].class,
                                int.class,
                                int.class,
                                head,
                                int[].class,
                                head,
                                int[].class,
                                float.class);
                m.setAccessible(true);
                return (Boolean)
                        m.invoke(
                                null,
                                l,
                                g,
                                W,
                                H,
                                own,
                                new int[] {W - 1 - 145, H - 1 - 98, -1},
                                peer,
                                new int[] {W - 1 - 207, H - 1 - peerEnd, -1},
                                14f);
            } finally {
                l = ml;
                g = mg;
            }
        }

        boolean preserves(ScoreRestEvent proposal) throws Exception {
            if (proposal.durationBeats() >= 1
                    || proposal.measureIndex() != 0
                    || proposal.staffIndex() != 0
                    || proposal.staffCount() != 1) return false;
            return FadedUpStemNoteSupport.proves(
                            l,
                            g,
                            W,
                            H,
                            146,
                            61,
                            158,
                            73,
                            14,
                            new FadedUpStemNoteSupport.Budget(262144))
                    || certificate();
        }
    }

    @Test
    public void proceduralShaftsComeFromReleasedRawExtractor() throws Exception {
        Ink i = new Ink("single");
        Class<?> c = Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component");
        Constructor<?> ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object own = ctor.newInstance(117, 146, 158, 61, 73, 152f, 67f),
                peer = ctor.newInstance(117, 208, 220, 40, 52, 214f, 46f);
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "attachedRawStem", byte[].class, int.class, int.class, c, float.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, i.g, W, H, own, 14f));
        assertArrayEquals(
                new int[] {145, 98, 1}, (int[]) m.invoke(null, i.g, W, H, own, 14f * .65f));
        assertArrayEquals(new int[] {207, 92, 1}, (int[]) m.invoke(null, i.g, W, H, peer, 14f));
    }

    @Test
    public void shortRestProposalCannotEraseIndependentlyBeamedHead() throws Exception {
        Ink i = new Ink("single");
        assertTrue(i.preserves(new ScoreRestEvent(0, .36666664f, 67f / H, 24f / H, 0, 1, .5)));
    }

    @Test
    public void rawShortShaftAndFullPeerCertifySingleRail() throws Exception {
        assertTrue(new Ink("single").certificate());
    }

    @Test
    public void oppositeSlopeHasSameIndependentProof() throws Exception {
        assertTrue(new Ink("otherSlope").certificate());
    }

    @Test
    public void rotatedUpShaftHasSameIndependentProof() throws Exception {
        assertTrue(new Ink("single").mirroredCertificate());
    }

    @Test
    public void mirroredOppositeSlopeHasSameProof() throws Exception {
        assertTrue(new Ink("otherSlope").mirroredCertificate());
    }

    @Test
    public void partialSecondaryHookCannotSupplySingleProof() throws Exception {
        assertFalse(new Ink("hook").certificate());
    }

    @Test
    public void conventionalSecondRailSeparationsRemainExcluded() throws Exception {
        for (int separation : new int[] {7, 11}) {
            Ink i = new Ink("single");
            i.beam(145, 94 - separation, 207, 88 - separation, 5, "single");
            assertFalse(i.certificate());
        }
    }

    @Test
    public void mirroredSecondRailRemainsExcluded() throws Exception {
        assertFalse(new Ink("double").mirroredCertificate());
    }

    @Test
    public void mirroredThirdRailRemainsExcluded() throws Exception {
        assertFalse(new Ink("triple").mirroredCertificate());
    }

    @Test
    public void thirdRailCannotSupplySingleProof() throws Exception {
        assertFalse(new Ink("triple").certificate());
    }

    @Test
    public void insufficientHeadFreeDepthCannotGuessSingle() throws Exception {
        assertFalse(
                new Ink("single").certificate(new int[] {145, 88, 1}, new int[] {207, 92, 1}, 146));
    }

    @Test
    public void quarterShaftCannotUseImaginedRail() throws Exception {
        assertFalse(new Ink("quarter").certificate());
    }

    @Test
    public void incompleteRailCannotConnectOwners() throws Exception {
        assertFalse(new Ink("partial").certificate());
    }

    @Test
    public void bowedSlurCannotConnectOwners() throws Exception {
        assertFalse(new Ink("slur").certificate());
    }

    @Test
    public void twoRailsCannotSupplyExclusiveSingleProof() throws Exception {
        assertFalse(new Ink("double").certificate());
    }

    @Test
    public void discontinuousOwnShaftCannotBorrowRail() throws Exception {
        assertFalse(new Ink("broken").certificate());
    }

    @Test
    public void diagonalRestTailCannotBorrowVerticalOwnership() throws Exception {
        assertFalse(new Ink("diagonalRest").certificate());
    }

    @Test
    public void opposingPeerCannotOwnSameRail() throws Exception {
        assertFalse(
                new Ink("single")
                        .certificate(new int[] {145, 98, 1}, new int[] {207, 92, -1}, 146));
    }

    @Test
    public void differentStaffHeadCannotOwnSameRail() throws Exception {
        assertFalse(new Ink("crossStaff").certificate());
    }

    @Test
    public void rawUnsupportedPeerHeadCannotOwnRail() throws Exception {
        assertFalse(new Ink("unsupported").certificate());
    }

    @Test
    public void displacedHeadCannotBorrowNeighborShaft() throws Exception {
        assertFalse(
                new Ink("single").certificate(new int[] {145, 98, 1}, new int[] {207, 92, 1}, 151));
    }

    @Test
    public void secondShortShaftIsNotIndependentFullPeer() throws Exception {
        assertFalse(
                new Ink("single").certificate(new int[] {145, 98, 1}, new int[] {207, 70, 1}, 146));
    }

    @Test
    public void quarterRestProposalIsOutsideAdjudicationGuard() throws Exception {
        assertFalse(
                new Ink("single")
                        .preserves(new ScoreRestEvent(0, .36666664f, 67f / H, 24f / H, 0, 1, 1)));
    }
}
