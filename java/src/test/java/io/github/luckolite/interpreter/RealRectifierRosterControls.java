// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;

/** Original real-rectifier invalid-owner regression, not a Source/physical model claim. */
public class RealRectifierRosterControls {
    static int checks;

    static void check(boolean b, String label) {
        checks++;
        if (!b) throw new AssertionError(label);
    }

    static Object call(Method m, Object target, Object... args) throws Exception {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public static void main(String[] args) throws Exception {
        var regions = List.of(new MeasureRegion(.02f, .98f, .15f, .8f));
        var staffs =
                List.of(
                        new SixteenthRestDetector.Staff(
                                80,
                                137,
                                14.25f,
                                0,
                                1,
                                StaffPitchTrack.linear(280, 137, 14.25f, 0)));
        Method glyph =
                FadedQuarterGlyphTest.class.getDeclaredMethod(
                        "glyph", char.class, int.class, int.class, int.class, double.class);
        glyph.setAccessible(true);
        byte[] raw = (byte[]) glyph.invoke(null, '\ue4e5', 40, 223, 12, .5);
        Class<?> ctx =
                Class.forName(
                        "io.github.luckolite.interpreter.SixteenthRestDetector$FaintRecovery");
        Method open =
                ctx.getDeclaredMethod(
                        "open",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class,
                        List.class);
        open.setAccessible(true);
        Method core =
                SixteenthRestDetector.class.getDeclaredMethod(
                        "detectOnPrintedStaff",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        SixteenthRestDetector.Staff.class,
                        List.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        ctx);
        core.setAccessible(true);
        Method finish = ctx.getDeclaredMethod("finish", SixteenthRestDetector.Detection.class);
        finish.setAccessible(true);
        for (int invalid : new int[] {-1, 1}) {
            var notes = List.of(new ScoreNoteEvent(invalid, .1f, 0, 0, 1, .2f, false, 0));
            Object context = call(open, null, raw, 280, 210, regions, staffs, notes);
            var legacy =
                    (SixteenthRestDetector.Detection)
                            call(
                                    core,
                                    null,
                                    raw,
                                    280,
                                    210,
                                    regions,
                                    staffs.get(0),
                                    notes,
                                    false,
                                    false,
                                    false,
                                    false,
                                    null);
            var transported =
                    (SixteenthRestDetector.Detection)
                            call(
                                    core,
                                    null,
                                    raw,
                                    280,
                                    210,
                                    regions,
                                    staffs.get(0),
                                    notes,
                                    false,
                                    false,
                                    false,
                                    false,
                                    context);
            check(
                    transported.equals(legacy),
                    "actual old rectifier output preserved before optional merge");
            var result = (SixteenthRestDetector.Detection) call(finish, context, transported);
            Field views = ctx.getDeclaredField("views");
            views.setAccessible(true);
            var roster = (List<?>) views.get(context);
            int mappedCount = -1;
            for (Object view : roster) {
                Field source = view.getClass().getDeclaredField("source");
                source.setAccessible(true);
                Field mapped = view.getClass().getDeclaredField("notes");
                mapped.setAccessible(true);
                System.out.println(
                        "VIEW rectified="
                                + (source.get(view) != null)
                                + " mapped="
                                + ((List<?>) mapped.get(view)).size());
                if (source.get(view) != null) mappedCount = ((List<?>) mapped.get(view)).size();
            }
            System.out.println(
                    "REAL_RECTIFIER invalid="
                            + invalid
                            + " legacy="
                            + legacy.rests().size()
                            + " result="
                            + result.rests().size()
                            + " roots="
                            + notes.size()
                            + " mapped="
                            + mappedCount);
            if (args.length > 0 && args[0].equals("repro")) continue;
            check(
                    result.equals(legacy),
                    "filtered original owner cannot disappear from optional admission");
            Field aborted = ctx.getDeclaredField("aborted");
            aborted.setAccessible(true);
            check(aborted.getBoolean(context), "whole optional invocation aborted");
        }
        if (args.length > 0 && args[0].equals("repro")) return;
        var far = new ScoreNoteEvent(0, .1f, 0, 0, 1, .2f, false, 0);
        var notes = List.of(far, far);
        Object context = call(open, null, raw, 280, 210, regions, staffs, notes);
        var transported =
                (SixteenthRestDetector.Detection)
                        call(
                                core,
                                null,
                                raw,
                                280,
                                210,
                                regions,
                                staffs.get(0),
                                notes,
                                false,
                                false,
                                false,
                                false,
                                context);
        var result = (SixteenthRestDetector.Detection) call(finish, context, transported);
        Field aborted = ctx.getDeclaredField("aborted");
        aborted.setAccessible(true);
        check(
                !aborted.getBoolean(context),
                "valid duplicate source occurrences preserve ordinal completeness");
        check(
                result.rests().stream().anyMatch(r -> r.durationBeats() == 1),
                "valid real rectifier positive retained");
        byte[] before = raw.clone();
        var nullNotes = Collections.<ScoreNoteEvent>singletonList(null);
        String first = null, second = null;
        try {
            call(
                    core,
                    null,
                    raw,
                    280,
                    210,
                    regions,
                    staffs.get(0),
                    nullNotes,
                    false,
                    false,
                    false,
                    false,
                    null);
        } catch (RuntimeException e) {
            first = e.getCause().getClass().getName();
        }
        try {
            call(
                    core,
                    null,
                    raw,
                    280,
                    210,
                    regions,
                    staffs.get(0),
                    nullNotes,
                    false,
                    false,
                    false,
                    false,
                    call(open, null, raw, 280, 210, regions, staffs, nullNotes));
        } catch (RuntimeException e) {
            second = e.getCause().getClass().getName();
        }
        check(
                "java.lang.NullPointerException".equals(first) && first.equals(second),
                "legacy null-owner exception preserved");
        check(Arrays.equals(before, raw), "real raw input unchanged");
        System.out.println("PASS " + checks + " real rectifier roster checks");
    }
}
