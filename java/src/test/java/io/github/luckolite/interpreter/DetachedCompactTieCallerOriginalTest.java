// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Complete caller ownership context around original compact bows. */
public final class DetachedCompactTieCallerOriginalTest {
    private static Class<?> type(String name) throws Exception {
        return Class.forName(OmrScoreInterpreter.class.getName() + "$" + name);
    }

    private static Object make(String name, Object... args) throws Exception {
        var c = type(name).getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(args);
    }

    private static Object note(
            DetachedCompactTieRidge.Head h,
            int step,
            float position,
            int beam,
            float duration,
            int marks,
            boolean foreign)
            throws Exception {
        var e =
                new ScoreNoteEvent(
                        0,
                        position,
                        step,
                        foreign ? 1 : 0,
                        foreign ? 2 : 1,
                        h.y() / DetachedCompactTieOriginalTest.H,
                        false,
                        0,
                        beam,
                        2,
                        duration,
                        1,
                        0,
                        marks,
                        30);
        return make(
                "DetectedNote",
                e,
                make(
                        "Component",
                        150,
                        h.left(),
                        h.right(),
                        Math.round(h.y() - 7),
                        Math.round(h.y() + 7),
                        h.x(),
                        h.y()),
                14f);
    }

    private static boolean result(
            int side,
            boolean grace,
            boolean changePitch,
            boolean interrupt,
            boolean foreign,
            boolean broken)
            throws Exception {
        var s =
                new DetachedCompactTieOriginalTest.Scene(
                        14, side, 0, 0, 0, broken ? "break" : "round");
        var source = new ArrayList<Object>();
        int marks = grace ? NoteOrnament.GRACE : 0;
        source.add(note(s.first, s.step, .2f, 2, 0, marks, false));
        if (interrupt) {
            float x = (s.first.x() + s.last.x()) * .5f, y = s.first.y() + 14;
            for (int yy = Math.round(y - 5); yy <= y + 5; yy++)
                for (int xx = Math.round(x - 7); xx <= x + 7; xx++)
                    if (Math.pow((xx - x) / 7, 2) + Math.pow((yy - y) / 5, 2) <= 1)
                        s.ink(xx, yy, 0, 2);
            source.add(
                    note(
                            new DetachedCompactTieRidge.Head(
                                    Math.round(x - 7), Math.round(x + 7), x, y),
                            s.step - 2,
                            .5f,
                            2,
                            0,
                            0,
                            false));
        }
        source.add(note(s.last, s.step + (changePitch ? 1 : 0), .8f, 0, 1, marks, foreign));
        var measures =
                List.of(
                        new MeasureRegion(
                                .05f,
                                .95f,
                                (s.top - 10) / DetachedCompactTieOriginalTest.H,
                                (s.bottom + 10) / DetachedCompactTieOriginalTest.H));
        var find =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "findStaffs", byte[].class, byte[].class, int.class, int.class, List.class);
        find.setAccessible(true);
        var staffs =
                (List<?>)
                        find.invoke(
                                null,
                                s.labels,
                                s.gray,
                                DetachedCompactTieOriginalTest.W,
                                DetachedCompactTieOriginalTest.H,
                                measures);
        assertEquals(1, staffs.size());
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "markTieContinuations",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        method.setAccessible(true);
        var l = s.labels.clone();
        var g = s.gray.clone();
        var out =
                (List<?>)
                        method.invoke(
                                null,
                                s.labels,
                                s.gray,
                                DetachedCompactTieOriginalTest.W,
                                DetachedCompactTieOriginalTest.H,
                                source,
                                staffs);
        assertEquals(source.size(), out.size());
        assertArrayEquals(l, s.labels);
        assertArrayEquals(g, s.gray);
        var event = out.get(out.size() - 1).getClass().getDeclaredMethod("event");
        event.setAccessible(true);
        return ((ScoreNoteEvent) event.invoke(out.get(out.size() - 1))).tiedFromPrevious();
    }

    @Test
    public void ordinaryAboveBowReachesTieFlag() throws Exception {
        assertTrue(result(-1, false, false, false, false, false));
    }

    @Test
    public void ordinaryBelowBowReachesTieFlag() throws Exception {
        assertTrue(result(1, false, false, false, false, false));
    }

    @Test
    public void graceSlotCannotBorrowOutsideBow() throws Exception {
        assertFalse(result(-1, true, false, false, false, false));
    }

    @Test
    public void differentPitchCannotBorrowOutsideBow() throws Exception {
        assertFalse(result(-1, false, true, false, false, false));
    }

    @Test
    public void interveningAttackCannotBeSkipped() throws Exception {
        assertFalse(result(-1, false, false, true, false, false));
    }

    @Test
    public void foreignPartCannotBorrowOutsideBow() throws Exception {
        assertFalse(result(-1, false, false, false, true, false));
    }

    @Test
    public void brokenBowCannotReachTieFlag() throws Exception {
        assertFalse(result(-1, false, false, false, false, true));
    }
}
