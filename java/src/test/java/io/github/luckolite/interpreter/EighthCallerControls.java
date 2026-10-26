// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/** Original complete-caller and atomic remainder controls; reflection only opens real private seams. */
public final class EighthCallerControls {
    static int checks;

    static void check(boolean b, String s) {
        checks++;
        if (!b) throw new AssertionError(s);
    }

    static final Class<?> C;
    static final Constructor<?> CT;
    static final Method FINISH;

    static {
        try {
            C =
                    Class.forName(
                            "io.github.luckolite.interpreter.SixteenthRestDetector$FaintRecovery");
            CT =
                    C.getDeclaredConstructor(
                            byte[].class,
                            int.class,
                            int.class,
                            List.class,
                            List.class,
                            FaintQuarterRestInk.Budget.class);
            CT.setAccessible(true);
            FINISH =
                    C.getDeclaredMethod(
                            "finishEighth", SixteenthRestDetector.Detection.class, List.class);
            FINISH.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    record Input(byte[] raw, int w, int h, SixteenthRestDetector.Staff frame) {}

    static Input original(Path f) throws Exception {
        try (var in = new DataInputStream(Files.newInputStream(f))) {
            int w = in.readInt(), h = in.readInt();
            float t = in.readFloat(), b = in.readFloat(), g = in.readFloat();
            return new Input(
                    in.readNBytes(w * h), w, h, new SixteenthRestDetector.Staff(t, b, g, 0, 1));
        }
    }

    static List<MeasureRegion> regions(Input a) {
        return List.of(
                new MeasureRegion(
                        .02f,
                        .98f,
                        (a.frame.top() - a.frame.gap()) / a.h,
                        (a.frame.bottom() + a.frame.gap()) / a.h));
    }

    static SixteenthRestDetector.Detection finish(
            Input a,
            List<ScoreNoteEvent> notes,
            SixteenthRestDetector.Detection prior,
            FaintQuarterRestInk.Budget q)
            throws Exception {
        return finishWithStaffs(a, notes, prior, q, List.of(a.frame));
    }

    static SixteenthRestDetector.Detection finishWithStaffs(
            Input a,
            List<ScoreNoteEvent> notes,
            SixteenthRestDetector.Detection prior,
            FaintQuarterRestInk.Budget q,
            List<SixteenthRestDetector.Staff> staffs)
            throws Exception {
        Object ctx = CT.newInstance(a.raw, a.w, a.h, regions(a), notes, q);
        try {
            return (SixteenthRestDetector.Detection) FINISH.invoke(ctx, prior, staffs);
        } catch (InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        int newPositives = 0, already = 0, rivals = 0;
        for (String line : Files.readAllLines(root.resolve("original-matrix-specs-v2.txt"))) {
            String name = line.split("\\|")[0];
            Input a = original(Path.of(args[1]).resolve(name + ".bin"));
            byte[] before = a.raw.clone();
            var prior =
                    SixteenthRestDetector.detectWithDots(
                            a.raw, a.w, a.h, regions(a), List.of(a.frame), List.of());
            boolean old = prior.rests().stream().anyMatch(r -> r.durationBeats() == .5);
            var q = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
            var next = finish(a, List.of(), prior, q);
            check(!q.exhausted, "original full optional quota " + name);
            check(next.rests().containsAll(prior.rests()), "all prior geometry " + name);
            check(next.dots() == prior.dots(), "same dot list " + name);
            check(Arrays.equals(before, a.raw), "raw invariant " + name);
            boolean fresh = next.rests().size() > prior.rests().size();
            if (fresh) newPositives++;
            if (old) already++;
            if (!name.contains("-eighth-")) {
                check(!fresh, "no new rival admission " + name);
                rivals++;
            }
            if (fresh) {
                check(
                        next.rests().get(next.rests().size() - 1).durationBeats() == .5,
                        "literal eighth only");
                var exact = new FaintQuarterRestInk.Budget(q.workUsed, q.bytesUsed);
                check(finish(a, List.of(), prior, exact).equals(next), "exact whole pass");
                check(
                        exact.workUsed == q.workUsed && exact.bytesUsed == q.bytesUsed,
                        "exact counters");
                for (int mode = 0; mode < 2; mode++) {
                    var low =
                            new FaintQuarterRestInk.Budget(
                                    q.workUsed - (mode == 0 ? 1 : 0),
                                    q.bytesUsed - (mode == 1 ? 1 : 0));
                    check(finish(a, List.of(), prior, low) == prior, "oneunder same prior object");
                    check(
                            low.exhausted
                                    && low.workUsed <= low.workLimit
                                    && low.bytesUsed <= low.byteLimit,
                            "oneunder atomic counters");
                }
                var full =
                        new SixteenthRestDetector.Detection(
                                List.of(ScoreRestEvent.fullMeasure(0, .1f, .5f, .1f, 0, 1)),
                                List.of());
                check(
                        finish(
                                        a,
                                        List.of(),
                                        full,
                                        new FaintQuarterRestInk.Budget(64_000_000, 64_000_000))
                                == full,
                        "actual completed full-rest rival");
                var rest = next.rests().get(next.rests().size() - 1);
                var head =
                        new ScoreNoteEvent(
                                0, rest.positionInMeasure(), 0, 0, 1, rest.pageY(), false, 0);
                check(
                        finish(
                                        a,
                                        List.of(head),
                                        prior,
                                        new FaintQuarterRestInk.Budget(64_000_000, 64_000_000))
                                == prior,
                        "same-call head rival");
                var unknown =
                        new ScoreNoteEvent(
                                -1, head.positionInMeasure(), 0, 0, 1, head.pageY(), false, 0);
                check(
                        finish(
                                        a,
                                        List.of(unknown),
                                        prior,
                                        new FaintQuarterRestInk.Budget(64_000_000, 64_000_000))
                                == prior,
                        "unknown actual head occurrence");
            }
            System.out.println(
                    name
                            + "|old="
                            + old
                            + "|new="
                            + fresh
                            + "|work="
                            + q.workUsed
                            + "|bytes="
                            + q.bytesUsed);
        }
        System.out.println(
                "PASS "
                        + checks
                        + " caller assertions; new="
                        + newPositives
                        + " legacy="
                        + already
                        + " rivals="
                        + rivals);
    }
}
