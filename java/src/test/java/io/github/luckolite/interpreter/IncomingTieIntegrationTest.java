// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original wide-system controls exercise actual candidate selection and curve ownership. */
public class IncomingTieIntegrationTest {
    static final int W = 1000, H = 1280;
    byte[] gray = WideSystemIncomingTieControlsTest.positive(), labels = new byte[W * H];

    Object make(String name, Object... args) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + name)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(args);
    }

    ScoreNoteEvent event(int m, float pos, int step, int acc, float dur, float y) {
        return new ScoreNoteEvent(
                m, pos, step, 1, 4, y / H, false, 0, 0, acc, dur, 1, 0, 0, 18, false, 0);
    }

    Object detected(ScoreNoteEvent e, int x, int y) throws Exception {
        return make(
                "DetectedNote",
                e,
                make("Component", 150, x - 8, x + 8, y - 4, y + 4, (float) x, (float) y),
                12f);
    }

    Object before() throws Exception {
        return detected(event(0, .7f, -4, 2, 2, 262), 750, 262);
    }

    Object after() throws Exception {
        return detected(event(1, .1f, -4, 2, 2, 912), 240, 912);
    }

    void arc(int left, int right, int cy) {
        for (int x = left; x <= right; x++) {
            float t = (x - left) / (float) (right - left);
            int y = Math.round(cy + 6 + 5 * 4 * t * (1 - t));
            WideSystemIncomingTieControlsTest.box(gray, x, y, x, y + 1, 0);
        }
    }

    void outgoing() {
        arc(762, 900, 262);
    }

    void stem(int x, int y, boolean up) {
        WideSystemIncomingTieControlsTest.box(
                gray, up ? x + 8 : x - 8, up ? y - 40 : y, up ? x + 9 : x - 7, up ? y : y + 40, 0);
    }

    int previous(List<Object> notes) throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "previousSamePitch",
                        List.class,
                        int.class,
                        int.class,
                        byte[].class,
                        byte[].class,
                        int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, notes, notes.size() - 1, W, labels, gray, H);
    }

    @SuppressWarnings("unchecked")
    List<Object> marked(List<Object> notes) throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "markTieContinuations",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class);
        m.setAccessible(true);
        return (List<Object>) m.invoke(null, labels, gray, W, H, notes);
    }

    ScoreNoteEvent read(Object note) throws Exception {
        var m = note.getClass().getDeclaredMethod("event");
        m.setAccessible(true);
        return (ScoreNoteEvent) m.invoke(note);
    }

    boolean tied(List<Object> notes) throws Exception {
        var n = marked(notes);
        return read(n.get(n.size() - 1)).tiedFromPrevious();
    }

    @SuppressWarnings("unchecked")
    List<ScoreNoteEvent> resolved(List<Object> notes) throws Exception {
        var marked = marked(notes);
        var m = OmrScoreInterpreter.class.getDeclaredMethod("applyAccidentalState", List.class);
        m.setAccessible(true);
        var result = (List<Object>) m.invoke(null, marked);
        var out = new ArrayList<ScoreNoteEvent>();
        for (Object n : result) out.add(read(n));
        return out;
    }

    @Test
    public void actualTwoEndedWideFallbackMarksTie() throws Exception {
        outgoing();
        var n = List.of(before(), after());
        assertEquals(0, previous(n));
        assertTrue(tied(n));
    }

    @Test
    public void actualOutgoingOnlyCannotMarkTie() throws Exception {
        gray = WideSystemIncomingTieControlsTest.frame();
        outgoing();
        assertFalse(tied(List.of(before(), after())));
    }

    @Test
    public void actualIncomingOnlyCannotMarkTie() throws Exception {
        assertFalse(tied(List.of(before(), after())));
    }

    @Test
    public void actualMissingPhysicalRuleCannotMarkTie() throws Exception {
        outgoing();
        WideSystemIncomingTieControlsTest.box(gray, 20, 214, 979, 214, 255);
        assertEquals(-1, previous(List.of(before(), after())));
        assertFalse(tied(List.of(before(), after())));
    }

    @Test
    public void actualExplicitAccidentalMismatchRejectsWideFallback() throws Exception {
        outgoing();
        assertFalse(
                tied(
                        List.of(
                                detected(event(0, .7f, -4, 1, 2, 262), 750, 262),
                                detected(event(1, .1f, -4, 0, 2, 912), 240, 912))));
    }

    @Test
    public void actualInheritedAccidentalCarriesAcrossTiedBoundary() throws Exception {
        outgoing();
        var n = resolved(List.of(detected(event(0, .7f, -4, 1, 2, 262), 750, 262), after()));
        assertTrue(n.get(1).tiedFromPrevious());
        assertEquals(1, n.get(1).writtenAccidental());
        assertTrue(
                ScoreTiePitchGuard.apply(
                                n, List.of(new ScoreKeyChange(0, 0), new ScoreKeyChange(1, -1)))
                        .get(1)
                        .tiedFromPrevious());
    }

    @Test
    public void actualKnownKeyContradictionIsRejectedAtNativeConsumerBoundary() throws Exception {
        outgoing();
        var n = resolved(List.of(before(), detected(event(1, .1f, -4, 1, 2, 912), 240, 912)));
        assertTrue(n.get(1).tiedFromPrevious());
        assertFalse(
                ScoreTiePitchGuard.apply(n, List.of(new ScoreKeyChange(0, 0)))
                        .get(1)
                        .tiedFromPrevious());
    }

    @Test
    public void actualOctaveShiftMismatchCannotMarkWideTie() throws Exception {
        outgoing();
        assertFalse(
                tied(
                        List.of(
                                before(),
                                detected(
                                        event(1, .1f, -4, 2, 2, 912).withOctaveShift(1),
                                        240,
                                        912))));
    }

    @Test
    public void actualDifferentClefCannotMarkWideTie() throws Exception {
        outgoing();
        var e =
                new ScoreNoteEvent(
                        1, .1f, -4, 1, 4, 912f / H, false, 0, 0, 2, 2, 1, 0, 0, 17, false, 0);
        assertFalse(tied(List.of(before(), detected(e, 240, 912))));
    }

    @Test
    public void actualNearestRepeatedPitchSupersedesOlderEndpoint() throws Exception {
        outgoing();
        WideSystemIncomingTieControlsTest.box(gray, 892, 258, 908, 266, 0);
        var notes = List.of(before(), detected(event(0, .9f, -4, 2, 1, 262), 900, 262), after());
        assertEquals(1, previous(notes));
        assertFalse(tied(notes));
    }

    @Test
    public void actualHeldVoiceCanPassInterveningDifferentAttack() throws Exception {
        outgoing();
        WideSystemIncomingTieControlsTest.box(gray, 892, 246, 908, 254, 0);
        var notes = List.of(before(), detected(event(0, .9f, -2, 2, 1, 250), 900, 250), after());
        assertEquals(0, previous(notes));
        assertTrue(tied(notes));
    }

    @Test
    public void actualOrdinaryMelodyCannotBorrowOlderWideEndpoint() throws Exception {
        outgoing();
        stem(750, 262, false);
        stem(240, 912, false);
        stem(900, 250, false);
        WideSystemIncomingTieControlsTest.box(gray, 892, 246, 908, 254, 0);
        var notes =
                List.of(
                        detected(event(0, .7f, -4, 2, 1, 262), 750, 262),
                        detected(event(0, .9f, -2, 2, 1, 250), 900, 250),
                        detected(event(1, .1f, -4, 2, 1, 912), 240, 912));
        assertEquals(-1, previous(notes));
        assertFalse(tied(notes));
    }

    @Test
    public void actualWideFallbackCannotInventHiddenStaffTransition() throws Exception {
        outgoing();
        var e =
                new ScoreNoteEvent(
                        1, .1f, -4, 1, 3, 912f / H, false, 0, 0, 2, 2, 1, 0, 0, 18, false, 0);
        assertFalse(tied(List.of(before(), detected(e, 240, 912))));
    }

    @Test
    public void actualWideMarkRetainsSourceArrays() throws Exception {
        outgoing();
        var g = gray.clone();
        var l = labels.clone();
        assertTrue(tied(List.of(before(), after())));
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    boolean onRuleTied(boolean beforeHead, boolean afterHead) throws Exception {
        gray = new IncomingTieHeadOwnershipTest().page(beforeHead, afterHead);
        arc(762, 900, 238);
        return tied(
                List.of(
                        detected(event(0, .7f, 0, 2, 2, 238), 750, 238),
                        detected(event(1, .1f, 0, 2, 2, 888), 240, 888)));
    }

    @Test
    public void actualIncomingRuleAloneCannotMarkTie() throws Exception {
        assertFalse(onRuleTied(true, false));
    }

    @Test
    public void actualOutgoingRuleAloneCannotMarkTie() throws Exception {
        assertFalse(onRuleTied(false, true));
    }

    @Test
    public void actualRulesAloneCannotMarkTie() throws Exception {
        assertFalse(onRuleTied(false, false));
    }
}
