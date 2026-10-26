// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;

/** Original licensed-font/white-paper inputs, independent legacy priority/resource checks. */
public class RecoveryOriginalControls {
    static int checks;

    static void check(boolean b, String name) {
        checks++;
        if (!b) throw new AssertionError(name);
    }

    static Class<?> context;
    static Method core, open, finish;
    static Constructor<?> constructor;
    static List<MeasureRegion> regions = List.of(new MeasureRegion(.02f, .98f, .15f, .8f));
    static List<SixteenthRestDetector.Staff> staffs =
            List.of(new SixteenthRestDetector.Staff(80, 137, 14.25f, 0, 1));

    static Object invoke(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        }
    }

    static SixteenthRestDetector.Detection run(byte[] raw, Object ctx) throws Exception {
        return (SixteenthRestDetector.Detection)
                invoke(core, null, raw, 280, 210, regions, staffs, List.of(), List.of(), ctx);
    }

    static Object context(byte[] raw, FaintQuarterRestInk.Budget budget) throws Exception {
        return constructor.newInstance(raw, 280, 210, regions, List.of(), budget);
    }

    public static void main(String[] args) throws Exception {
        context =
                Class.forName(
                        "io.github.luckolite.interpreter.SixteenthRestDetector$FaintRecovery");
        core =
                SixteenthRestDetector.class.getDeclaredMethod(
                        "detectWithDotsRecovery",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class,
                        List.class,
                        List.class,
                        context);
        core.setAccessible(true);
        open =
                context.getDeclaredMethod(
                        "open",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class,
                        List.class);
        open.setAccessible(true);
        finish = context.getDeclaredMethod("finish", SixteenthRestDetector.Detection.class);
        finish.setAccessible(true);
        constructor =
                context.getDeclaredConstructor(
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class,
                        FaintQuarterRestInk.Budget.class);
        constructor.setAccessible(true);
        Method glyph =
                FadedQuarterGlyphTest.class.getDeclaredMethod(
                        "glyph", char.class, int.class, int.class, int.class, double.class);
        glyph.setAccessible(true);
        int gains = 0;
        for (char symbol : new char[] {'\ue4e5', '\ue261', '\ue262'})
            for (int height : new int[] {40, 44})
                for (int rows : new int[] {12, 16}) {
                    byte[] raw = (byte[]) glyph.invoke(null, symbol, height, 223, rows, .5);
                    byte[] saved = raw.clone();
                    var old = run(raw, null);
                    var meter = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
                    var ctx = context(raw, meter);
                    var actual = run(raw, ctx);
                    check(
                            actual.rests().containsAll(old.rests()),
                            "every legacy geometry preserved");
                    check(actual.dots().equals(old.dots()), "legacy dot list");
                    check(Arrays.equals(raw, saved), "raw unchanged");
                    if (symbol != '\ue4e5')
                        check(actual.equals(old), "sharp/natural negative exact");
                    if (actual.rests().size() > old.rests().size()) gains++;
                    var exhausted = new FaintQuarterRestInk.Budget(0, 0);
                    Object empty = context(raw, exhausted);
                    check(run(raw, empty).equals(old), "zero pool whole legacy result");
                    var legacy = new SixteenthRestDetector.Detection(old.rests(), old.dots());
                    check(
                            invoke(finish, empty, legacy) == legacy,
                            "abort returns SAME legacy Detection");
                    if (symbol == '\ue4e5' && height == 40 && rows == 12) {
                        check(
                                meter.workUsed > 0 && meter.bytesUsed > 0,
                                "real context work charged");
                        var exact = new FaintQuarterRestInk.Budget(meter.workUsed, meter.bytesUsed);
                        check(
                                run(raw, context(raw, exact)).equals(actual),
                                "exact full invocation");
                        check(
                                exact.workUsed == meter.workUsed
                                        && exact.bytesUsed == meter.bytesUsed,
                                "exact counters");
                        var underW =
                                new FaintQuarterRestInk.Budget(meter.workUsed - 1, meter.bytesUsed);
                        check(
                                run(raw, context(raw, underW)).equals(old),
                                "one-under work abort, no accepted prefix");
                        check(
                                underW.exhausted
                                        && underW.workUsed <= underW.workLimit
                                        && underW.bytesUsed <= underW.byteLimit,
                                "atomic work counters");
                        var underB =
                                new FaintQuarterRestInk.Budget(meter.workUsed, meter.bytesUsed - 1);
                        check(
                                run(raw, context(raw, underB)).equals(old),
                                "one-under bytes abort, no accepted prefix");
                        check(
                                underB.exhausted
                                        && underB.workUsed <= underB.workLimit
                                        && underB.bytesUsed <= underB.byteLimit,
                                "atomic bytes counters");
                    }
                }
        // Genuine initial common producer -> stored raw seed -> completed-roster veto.
        Method producer =
                SixteenthRestDetector.class.getDeclaredMethod(
                        "detectWithDots",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class,
                        List.class,
                        boolean.class,
                        boolean.class,
                        java.util.Map.class,
                        context,
                        Class.forName(
                                "io.github.luckolite.interpreter.SixteenthRestDetector$FaintView"));
        producer.setAccessible(true);
        byte[] rivalRaw = (byte[]) glyph.invoke(null, '\ue4e5', 44, 223, 16, .5);
        var declared =
                new SixteenthRestDetector.Detection(
                        List.of(ScoreRestEvent.fullMeasure(0, .9f, .5f, .1f, 0, 1)), List.of());
        Object declaredContext =
                context(rivalRaw, new FaintQuarterRestInk.Budget(64_000_000, 64_000_000));
        invoke(
                producer,
                null,
                rivalRaw,
                280,
                210,
                regions,
                staffs,
                List.of(),
                true,
                false,
                null,
                declaredContext,
                null);
        check(
                invoke(finish, declaredContext, declared) == declared,
                "full-measure roster is authority regardless of position");
        var head = new ScoreNoteEvent(0, .59f, 0, 0, 1, .51f, false, 0);
        Object headed =
                constructor.newInstance(
                        rivalRaw,
                        280,
                        210,
                        regions,
                        List.of(head),
                        new FaintQuarterRestInk.Budget(64_000_000, 64_000_000));
        invoke(
                producer,
                null,
                rivalRaw,
                280,
                210,
                regions,
                staffs,
                List.of(head),
                true,
                false,
                null,
                headed,
                null);
        var noRest = new SixteenthRestDetector.Detection(List.of(), List.of());
        check(
                invoke(finish, headed, noRest) == noRest,
                "same-call head near component vetoes, no fabricated origin");
        var unknownHead = new ScoreNoteEvent(-1, .59f, 0, 0, 1, .51f, false, 0);
        Object unknown =
                constructor.newInstance(
                        rivalRaw,
                        280,
                        210,
                        regions,
                        List.of(unknownHead),
                        new FaintQuarterRestInk.Budget(64_000_000, 64_000_000));
        invoke(
                producer,
                null,
                rivalRaw,
                280,
                210,
                regions,
                staffs,
                List.of(unknownHead),
                true,
                false,
                null,
                unknown,
                null);
        check(invoke(finish, unknown, noRest) == noRest, "unknown head occurrence refused");
        byte[] white = new byte[280 * 210];
        Arrays.fill(white, (byte) 255);
        check(
                run(white, context(white, new FaintQuarterRestInk.Budget(64_000_000, 64_000_000)))
                        .equals(run(white, null)),
                "white unchanged");
        check(
                open.invoke(null, white, 280, 210, regions, List.of(), List.of()) != null,
                "valid admission");
        check(
                open.invoke(null, white, 8193, 210, regions, List.of(), List.of()) == null,
                "unsupported width");
        check(
                open.invoke(
                                null,
                                white,
                                280,
                                210,
                                Collections.nCopies(4097, regions.get(0)),
                                List.of(),
                                List.of())
                        == null,
                "roster bound");
        System.out.println(
                "PASS " + checks + " original recovery controls; new complete quarters=" + gains);
    }
}
