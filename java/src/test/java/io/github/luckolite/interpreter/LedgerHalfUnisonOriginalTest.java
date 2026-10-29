// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original opposed-shaft half and moving voice above four printed ledgers. */
public class LedgerHalfUnisonOriginalTest {
    static final int W = 440, H = 300;

    static class Page {
        byte[] l = new byte[W * H], g = new byte[W * H];

        Page(boolean hollow, boolean shafts, boolean ledgers) {
            Arrays.fill(g, (byte) 255);
            for (int y = 140; y <= 196; y += 14) rect(20, y, 400, 1, 4);
            if (ledgers) for (int y = 84; y < 140; y += 14) rect(160, y, 64, 1, 5);
            if (shafts) {
                rect(189, 38, 2, 47, 1);
                rect(190, 84, 2, 59, 1);
                for (int x = 190; x <= 270; x++)
                    rect(x, Math.round(142 - (x - 190) * .05f), 1, 4, 5);
                rect(270, 100, 2, 39, 1);
            }
            oval(180, 84, hollow);
            oval(201, 84, false);
            oval(280, 100, false);
        }

        void rect(int x, int y, int w, int h, int kind) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) {
                    l[yy * W + xx] = (byte) kind;
                    g[yy * W + xx] = 0;
                }
        }

        void oval(int cx, int cy, boolean hollow) {
            for (int y = cy - 8; y <= cy + 8; y++)
                for (int x = cx - 10; x <= cx + 10; x++) {
                    double d = Math.pow((x - cx) / 10., 2) + Math.pow((y - cy) / 8., 2);
                    if (d <= 1) {
                        l[y * W + x] = 2;
                        g[y * W + x] = (byte) (hollow && d < .42 ? 255 : 0);
                    }
                }
        }

        OmrScoreInterpreter.Analysis analysis() {
            return OmrScoreInterpreter.analyze(
                    l, g, W, H, List.of(new MeasureRegion(.03f, .97f, .15f, .85f)));
        }

        List<ScoreNoteEvent> target() {
            return analysis().notes().stream().filter(n -> n.staffStep() == 16).toList();
        }
    }

    @Test
    public void ledgerOwnedHeldAndMovingVoicesBothSurvive() {
        var n = new Page(true, true, true).target();
        assertEquals(2, n.size());
        assertEquals(1, n.stream().filter(x -> x.unbeamedDurationBeats() == 2).count());
        assertEquals(1, n.stream().filter(x -> x.beamCount() == 1).count());
    }

    @Test
    public void twoFilledLobesCannotInventHeldVoice() {
        assertTrue(
                new Page(false, true, true)
                        .target().stream().noneMatch(n -> n.unbeamedDurationBeats() == 2));
    }

    @Test
    public void absentShaftCannotAdmitLedgerUnison() {
        assertTrue(new Page(true, false, true).target().isEmpty());
    }

    @Test
    public void missingLedgerSeriesCannotAdmitUnison() {
        assertTrue(new Page(true, true, false).target().isEmpty());
    }

    @Test
    public void bothPartsRetainDisjointExactOriginalMembers() {
        var p = new Page(true, true, true);
        var a = p.analysis();
        var source = a.clockProducerSource();
        var parts =
                source.emissions().stream()
                        .filter(
                                e ->
                                        e.finalNoteOwners().stream()
                                                .anyMatch(i -> a.notes().get(i).staffStep() == 16))
                        .toList();
        assertEquals(2, parts.size());
        assertFalse(parts.get(0).pixels().isEmpty());
        assertFalse(parts.get(1).pixels().isEmpty());
        assertTrue(Collections.disjoint(parts.get(0).pixels(), parts.get(1).pixels()));
        assertEquals(parts.get(0).parent(), parts.get(1).parent());
        assertNotEquals(-1, parts.get(0).parent());
        var union = new HashSet<Integer>();
        for (var e : parts) union.addAll(e.pixels());
        var expected = new HashSet<Integer>();
        for (int y = 76; y <= 92; y++)
            for (int x = 170; x <= 211; x++) if (p.l[y * W + x] == 2) expected.add(y * W + x);
        assertEquals(expected, union);
        assertEquals(source.accounting().population(), source.accounting().unique());
    }

    @Test
    public void heldAndMovingRetainActualValueRoutes() {
        var a = new Page(true, true, true).analysis();
        int checked = 0;
        for (var v : a.clockProducerSource().printedValues())
            if (a.notes().get(v.note()).staffStep() == 16) {
                checked++;
                assertEquals("OBSERVED_ROUTE_NOT_COMPLETE_WRITTEN_VALUE_AUTHORITY", v.status());
                assertTrue(v.observations().contains("EXACT_LEDGER_HALF_UNISON_PART"));
                assertTrue(v.observations().contains("SHARED_LOGICAL_UNISON_ATTACK"));
                assertEquals(v.detectorValue(), v.finalValue(), 0);
                assertEquals(v.detectorBeams(), v.finalBeams());
                assertEquals(
                        v.finalValue() == 2 ? "OPEN_WITH_STEM_HALF" : "BEAMED_INPUT",
                        v.detectorExit());
            }
        assertEquals(2, checked);
    }

    static boolean unfunded(long limit) throws Exception {
        var p = new Page(true, true, true);
        var c = OmrScoreInterpreter.class;
        var f =
                c.getDeclaredMethod(
                        "findComponents", byte[].class, int.class, int.class, byte.class);
        f.setAccessible(true);
        Object parent =
                ((List<?>) f.invoke(null, p.l, W, H, (byte) 2))
                        .stream()
                                .filter(
                                        x -> {
                                            try {
                                                var m = x.getClass().getDeclaredMethod("centerX");
                                                m.setAccessible(true);
                                                return (float) m.invoke(x) < 230;
                                            } catch (Exception e) {
                                                throw new RuntimeException(e);
                                            }
                                        })
                                .findFirst()
                                .orElseThrow();
        var st = Class.forName(c.getName() + "$Staff");
        var ctor = st.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object staff = ctor.newInstance(140f, 196f, 14f);
        var hc = parent.getClass();
        var m =
                c.getDeclaredMethod(
                        "captureLedgerHalfUnison",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        hc,
                        st,
                        List.class,
                        ExactComponentMembers.Collector.class,
                        long[].class);
        m.setAccessible(true);
        long[] work = {limit};
        var out =
                (List<?>) m.invoke(null, p.l, p.g, W, H, parent, staff, List.of(staff), null, work);
        return out.isEmpty() && work[0] == 0;
    }

    @Test
    public void zeroWorkCannotCaptureOrAdmitNewHalf() {
        try {
            assertTrue(unfunded(0));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void exhaustedCandidateWorkCannotCaptureOrAdmitNewHalf() {
        try {
            assertTrue(unfunded(2));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void callerArraysRemainExact() {
        var p = new Page(true, true, true);
        byte[] l = p.l.clone(), g = p.g.clone();
        p.analysis();
        assertArrayEquals(l, p.l);
        assertArrayEquals(g, p.g);
    }
}
