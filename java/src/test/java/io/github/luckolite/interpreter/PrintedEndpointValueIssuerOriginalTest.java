// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PrintedEndpointValueIssuerOriginalTest {
    static ExactComponentMembers.Budget budget() {
        return new ExactComponentMembers.Budget(256000000, 128L * 1024 * 1024);
    }

    static OmrScoreInterpreter.PrintedValueDecision decision(
            TremoloPrintedValueScene.Page p, Object head, List<String> admissions)
            throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectPrintedValue",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        head.getClass(),
                        float.class,
                        int.class,
                        List.class);
        m.setAccessible(true);
        var d =
                (OmrScoreInterpreter.PrintedValueDecision)
                        m.invoke(
                                null,
                                p.labels(),
                                p.gray(),
                                240,
                                150,
                                head,
                                14f,
                                0,
                                new ArrayList<>(p.components().values()));
        var caller =
                d.getClass()
                        .getDeclaredMethod("withCallerState", float.class, int.class, List.class);
        caller.setAccessible(true);
        return (OmrScoreInterpreter.PrintedValueDecision)
                caller.invoke(d, d.value(), d.inputBeamCount(), admissions);
    }

    static ScoreNoteEvent note(float value) {
        return new ScoreNoteEvent(0, .2f, 0, 0, 1, .6f, false, 0, 0, 2, value);
    }

    @Test
    public void realPhysicalScopeAndSourceBodyIssuePrintedQuarter() throws Exception {
        var p = TremoloPrintedValueScene.page(0);
        var d = PairedTremoloInk.discover(p.gray(), 240, 150, p.heads());
        assertEquals(1, d.pairs().size());
        var premises =
                PrintedTremoloEndpointValue.capture(
                        d,
                        d.pairs().get(0),
                        p.components(),
                        p.collector(),
                        p.labels(),
                        p.gray(),
                        240,
                        150,
                        budget());
        assertEquals(2, premises.size());
        for (var e : premises.entrySet()) {
            assertEquals(e.getValue().refusal(), "", e.getValue().refusal());
            var value =
                    PrintedTremoloEndpointValue.issue(
                            e.getValue(),
                            decision(p, e.getKey(), List.of()),
                            note(1),
                            p.collector(),
                            e.getKey());
            assertNotNull(value);
            assertEquals(1, value.baseBeats(), 0);
            assertEquals(
                    "GEOMETRIC_ELLIPSE_AND_SOURCE_BOUND_CONTACT_MODEL", value.contourEvidence());
        }
    }

    @Test
    public void callerMadeEqualPairCannotBorrowDiscoveryScope() throws Exception {
        var p = TremoloPrintedValueScene.page(0);
        var d = PairedTremoloInk.discover(p.gray(), 240, 150, p.heads());
        assertEquals(1, d.pairs().size());
        var pair = d.pairs().get(0);
        var forged =
                new PairedTremoloInk.Pair(
                        pair.firstIds(),
                        pair.secondIds(),
                        pair.railHeadIds(),
                        pair.marks(),
                        pair.strokeCount(),
                        true);
        assertTrue(
                PrintedTremoloEndpointValue.capture(
                                d,
                                forged,
                                p.components(),
                                p.collector(),
                                p.labels(),
                                p.gray(),
                                240,
                                150,
                                budget())
                        .isEmpty());
    }

    @Test
    public void recoveredTransportedChangedCueGraceAndForeignCannotGainQuarterAuthority()
            throws Exception {
        var p = TremoloPrintedValueScene.page(0);
        var d = PairedTremoloInk.discover(p.gray(), 240, 150, p.heads());
        assertEquals(1, d.pairs().size());
        var premises =
                PrintedTremoloEndpointValue.capture(
                        d,
                        d.pairs().get(0),
                        p.components(),
                        p.collector(),
                        p.labels(),
                        p.gray(),
                        240,
                        150,
                        budget());
        assertEquals(2, premises.size());
        var e = premises.entrySet().iterator().next();
        for (String recovery :
                List.of(
                        "RECOVERED_SINGLE_BEAM_ADMISSION",
                        "TRANSPORTED_PAIRED_BEAM_POSITIVE_ADMISSION",
                        "BORROWED_CHORD_RHYTHM_HEAD",
                        "RECOVERED_FILLED_FRAGMENT_OVERRIDE"))
            assertNull(
                    PrintedTremoloEndpointValue.issue(
                            e.getValue(),
                            decision(p, e.getKey(), List.of(recovery)),
                            note(1),
                            p.collector(),
                            e.getKey()));
        var quarter = decision(p, e.getKey(), List.of("TRANSPORTED_PAIRED_BEAM_PATH_CONSULTED"));
        assertNotNull(
                PrintedTremoloEndpointValue.issue(
                        e.getValue(), quarter, note(1), p.collector(), e.getKey()));
        for (var changed :
                List.of(
                        note(2),
                        note(1).withVisualSize(ScoreNoteEvent.VisualSize.CUE),
                        note(1).withArticulations(NoteOrnament.GRACE)))
            assertNull(
                    PrintedTremoloEndpointValue.issue(
                            e.getValue(), quarter, changed, p.collector(), e.getKey()));
        assertNull(
                PrintedTremoloEndpointValue.issue(
                        e.getValue(), quarter, note(1), p.collector(), new Object()));
    }

    @Test
    public void foreignOrStaleRasterNeverBindsSourceScope() throws Exception {
        var p = TremoloPrintedValueScene.page(0);
        var d = PairedTremoloInk.discover(p.gray(), 240, 150, p.heads());
        assertEquals(1, d.pairs().size());
        assertTrue(
                PrintedTremoloEndpointValue.capture(
                                d,
                                d.pairs().get(0),
                                p.components(),
                                p.collector(),
                                p.labels(),
                                p.gray().clone(),
                                240,
                                150,
                                budget())
                        .isEmpty());
        p.gray()[0] = 0;
        assertTrue(
                PrintedTremoloEndpointValue.capture(
                                d,
                                d.pairs().get(0),
                                p.components(),
                                p.collector(),
                                p.labels(),
                                p.gray(),
                                240,
                                150,
                                budget())
                        .isEmpty());
    }
}
