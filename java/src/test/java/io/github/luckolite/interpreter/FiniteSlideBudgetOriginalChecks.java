// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original bounded work, allocation and atomic-refusal assertions; no score data. */
public class FiniteSlideBudgetOriginalChecks {
    static int checks;

    static void check(boolean yes, String name) {
        checks++;
        if (!yes) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }

    static class Counted extends AbstractList<NoteSlideDetector.Head> {
        final List<NoteSlideDetector.Head> data;
        long gets;

        Counted(List<NoteSlideDetector.Head> data) {
            this.data = data;
        }

        public int size() {
            return data.size();
        }

        public NoteSlideDetector.Head get(int i) {
            gets++;
            return data.get(i);
        }
    }

    static List<NoteSlideDetector.Stroke> find(
            FiniteConnectedSlidePortsOriginalChecks.Scene s,
            List<NoteSlideDetector.Head> heads,
            byte[] clean,
            FiniteConnectedSlidePorts.Budget b) {
        return FiniteConnectedSlidePorts.find(
                s.gray, s.w, s.h, s.staffs, heads, clean, null, null, b);
    }

    static List<NoteSlideDetector.Stroke> add(
            List<NoteSlideDetector.Stroke> old,
            FiniteConnectedSlidePortsOriginalChecks.Scene s,
            byte[] clean,
            FiniteConnectedSlidePorts.Budget b) {
        return FiniteConnectedSlidePorts.add(
                old, s.gray, s.w, s.h, s.staffs, s.heads, clean, null, null, b);
    }

    public static void main(String[] args) {
        var s = new FiniteConnectedSlidePortsOriginalChecks.Scene(1, false, -1, 1);
        s.line();
        byte[] clean = NoteSlideDetector.removeStaffLines(s.gray, s.w, s.h, s.staffs);
        var old = List.of(new NoteSlideDetector.Stroke(1, 2, 3, 4, -1, 0, true));
        var counted = new Counted(s.heads);
        var zero = new FiniteConnectedSlidePorts.Budget(0, FiniteConnectedSlidePorts.DEFAULT_BYTES);
        check(find(s, counted, clean, zero).isEmpty(), "zero work gives no certificates");
        check(
                zero.refused() && zero.usedWork() == 0 && counted.gets == 0,
                "zero work refuses before any head snapshot");
        check(
                add(old, s, clean, zero) == old,
                "sticky zero refusal preserves old JOIN list identity");
        var none = new FiniteConnectedSlidePorts.Budget(FiniteConnectedSlidePorts.DEFAULT_WORK, 0);
        counted = new Counted(s.heads);
        check(find(s, counted, clean, none).isEmpty() && none.refused(), "zero allocation refuses");
        check(
                counted.gets == 0 && none.usedBytes() == 0,
                "allocation refused before snapshot or array allocation");
        check(add(old, s, clean, none) == old, "allocation refusal retains old JOIN identity");
        var low = new FiniteConnectedSlidePorts.Budget(1, FiniteConnectedSlidePorts.DEFAULT_BYTES);
        counted = new Counted(s.heads);
        check(
                find(s, counted, clean, low).isEmpty() && low.refused() && counted.gets == 0,
                "low work cannot publish a partial source list");
        var full =
                new FiniteConnectedSlidePorts.Budget(
                        FiniteConnectedSlidePorts.DEFAULT_WORK,
                        FiniteConnectedSlidePorts.DEFAULT_BYTES);
        var accepted = find(s, s.heads, clean, full);
        check(
                accepted.size() == 1 && !full.refused(),
                "original complete port succeeds under concrete default limits");
        check(full.certified() == 1, "one original positive certificate observed");
        var lateWork =
                new FiniteConnectedSlidePorts.Budget(
                        full.usedWork() - 1, FiniteConnectedSlidePorts.DEFAULT_BYTES);
        check(
                find(s, s.heads, clean, lateWork).isEmpty()
                        && lateWork.refused()
                        && lateWork.certified() > 0,
                "work exhaustion after an earlier positive discards ALL new certificates");
        check(
                add(old, s, clean, lateWork) == old,
                "post-positive work refusal retains original JOIN list identity");
        var lateBytes =
                new FiniteConnectedSlidePorts.Budget(
                        FiniteConnectedSlidePorts.DEFAULT_WORK, full.usedBytes() - 1);
        check(
                find(s, s.heads, clean, lateBytes).isEmpty()
                        && lateBytes.refused()
                        && lateBytes.certified() > 0,
                "allocation exhaustion after a positive is atomic");
        check(
                add(old, s, clean, lateBytes) == old,
                "post-positive allocation refusal retains old JOIN identity");
        var mergeFull =
                new FiniteConnectedSlidePorts.Budget(
                        FiniteConnectedSlidePorts.DEFAULT_WORK,
                        FiniteConnectedSlidePorts.DEFAULT_BYTES);
        check(
                add(old, s, clean, mergeFull).size() == 2 && !mergeFull.refused(),
                "completed helper can extend independently accepted old JOIN");
        var mergeLow =
                new FiniteConnectedSlidePorts.Budget(
                        FiniteConnectedSlidePorts.DEFAULT_WORK, mergeFull.usedBytes() - 1);
        check(
                add(old, s, clean, mergeLow) == old
                        && mergeLow.refused()
                        && mergeLow.certified() == 1,
                "refusal during final merge never exposes a partial new result");
        var cross = new FiniteConnectedSlidePortsOriginalChecks.Scene(1, true, -1, 1);
        cross.line();
        byte[] cut = NoteSlideDetector.removeStaffLines(cross.gray, cross.w, cross.h, cross.staffs);
        for (int x = 145; x <= 148; x++)
            for (int y = 108; y <= 118; y++) cut[y * cross.w + x] = (byte) 255;
        var pairFull =
                new FiniteConnectedSlidePorts.Budget(
                        FiniteConnectedSlidePorts.DEFAULT_WORK,
                        FiniteConnectedSlidePorts.DEFAULT_BYTES);
        var joined = find(cross, cross.heads, cut, pairFull);
        check(
                joined.size() == 1 && pairFull.pairAttempts() > 0,
                "original raw-rule gap requires a complete component-pair certificate");
        var pairWork =
                new FiniteConnectedSlidePorts.Budget(
                        pairFull.firstPairWork(), FiniteConnectedSlidePorts.DEFAULT_BYTES);
        check(
                find(cross, cross.heads, cut, pairWork).isEmpty()
                        && pairWork.refused()
                        && pairWork.pairAttempts() == 0,
                "pair loop is debited before its first attempt");
        var pairBytes =
                new FiniteConnectedSlidePorts.Budget(
                        FiniteConnectedSlidePorts.DEFAULT_WORK, pairFull.firstPairBytes());
        check(
                find(cross, cross.heads, cut, pairBytes).isEmpty()
                        && pairBytes.refused()
                        && pairBytes.refusalReason().equals("allocation"),
                "pair pixel-copy allocation is refused before constructing it");
        var huge =
                new AbstractList<NoteSlideDetector.Head>() {
                    public int size() {
                        return Integer.MAX_VALUE;
                    }

                    public NoteSlideDetector.Head get(int i) {
                        throw new AssertionError("must reserve before metadata");
                    }
                };
        var memory =
                new FiniteConnectedSlidePorts.Budget(
                        FiniteConnectedSlidePorts.DEFAULT_WORK,
                        FiniteConnectedSlidePorts.DEFAULT_BYTES);
        check(
                find(s, huge, clean, memory).isEmpty() && memory.refused(),
                "allocation arithmetic is long and refuses before a huge snapshot");
        var cap = new FiniteConnectedSlidePorts.Budget(Long.MAX_VALUE, Long.MAX_VALUE);
        check(
                !cap.refused()
                        && cap.remainingWork() == FiniteConnectedSlidePorts.DEFAULT_WORK
                        && cap.remainingBytes() == FiniteConnectedSlidePorts.DEFAULT_BYTES,
                "requested limits may not increase production ceilings");
        System.out.println(
                "DEFAULT work="
                        + FiniteConnectedSlidePorts.DEFAULT_WORK
                        + " bytes="
                        + FiniteConnectedSlidePorts.DEFAULT_BYTES
                        + " originalUsedWork="
                        + full.usedWork()
                        + " originalUsedBytes="
                        + full.usedBytes());
        // Same real-glyph grids used in independent v1 review, with no metadata safety stop.
        for (int n : new int[] {64, 128, 256}) {
            int w = 400, rows = (n + 15) / 16, h = rows * 60 + 80;
            byte[] gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            var heads = new ArrayList<NoteSlideDetector.Head>();
            var staffs = new ArrayList<NoteSlideDetector.Staff>();
            for (int row = 0; row < rows; row++) {
                int cy = 45 + row * 60;
                staffs.add(new NoteSlideDetector.Staff(cy - 16, cy + 16, 8));
                for (int k = 0; k < 5; k++)
                    for (int x = 10; x < w - 10; x++) gray[(cy - 16 + 8 * k) * w + x] = 35;
                for (int col = 0; col < 16 && heads.size() < n; col++) {
                    int cx = 25 + col * 22;
                    FiniteSlideWorkBoundProbe.drawHead(gray, w, cx, cy);
                    heads.add(new NoteSlideDetector.Head(cx, cy, 8, row, 0));
                }
            }
            var input = new Counted(heads);
            var b =
                    new FiniteConnectedSlidePorts.Budget(
                            FiniteConnectedSlidePorts.DEFAULT_WORK,
                            FiniteConnectedSlidePorts.DEFAULT_BYTES);
            var c = NoteSlideDetector.removeStaffLines(gray, w, h, staffs);
            var result =
                    FiniteConnectedSlidePorts.find(gray, w, h, staffs, input, c, null, null, b);
            check(input.gets == n, "grid snapshots every head exactly once n=" + n);
            check(
                    result.isEmpty() && !b.refused(),
                    "original real-head grid completes without hidden rivals or arbitrary count cap n="
                            + n);
            System.out.println(
                    "GRID heads="
                            + n
                            + " pixels="
                            + (w * h)
                            + " metadataReads="
                            + input.gets
                            + " work="
                            + b.usedWork()
                            + " bytes="
                            + b.usedBytes()
                            + " refused="
                            + b.refused());
        }
        System.out.println("CHECKS=" + checks);
    }
}
