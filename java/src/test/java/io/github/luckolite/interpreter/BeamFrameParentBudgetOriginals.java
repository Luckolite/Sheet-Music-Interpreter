// Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Resource-only tests against the real Source Work object, never musical authority. */
public final class BeamFrameParentBudgetOriginals {
    static int checks;

    static void check(boolean v, String s) {
        checks++;
        if (!v) throw new AssertionError(s);
    }

    static final class Parent implements FaintQuarterRestInk.ParentCharge {
        final PrintedBeamSource.Work actual;
        int calls;

        Parent(long units) {
            actual = new PrintedBeamSource.Work(units);
        }

        public void take(long work, long bytes) {
            calls++;
            actual.take(Math.addExact(work, Math.addExact(bytes, 7) / 8));
        }
    }

    public static void main(String[] args) {
        var legacy = new FaintQuarterRestInk.Budget(5, 7);
        check(legacy.prepay(5, 7), "legacy exact");
        check(legacy.workUsed == 5 && legacy.bytesUsed == 7, "legacy counters");
        check(!legacy.prepay(1, 0) && legacy.exhausted, "legacy sticky limit");
        check(legacy.workUsed == 5 && legacy.bytesUsed == 7, "legacy no prefix on failed request");
        try {
            new FaintQuarterRestInk.Budget(-1, 0);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        var invalid = new FaintQuarterRestInk.Budget(5, 7);
        try {
            invalid.prepay(-1, 0);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        check(!invalid.exhausted && invalid.workUsed == 0, "legacy invalid not charged");
        Parent exact = new Parent(12);
        var a = new FaintQuarterRestInk.Budget(100, 100, exact);
        var b = new FaintQuarterRestInk.Budget(100, 100, exact);
        check(a.prepay(4, 8), "first child");
        check(b.prepay(6, 8), "second child same parent");
        check(exact.actual.remaining == 0 && exact.calls == 2, "one actual Source pool");
        check(
                a.workUsed == 4 && a.bytesUsed == 8 && b.workUsed == 6 && b.bytesUsed == 8,
                "children genuine counters");
        var c = new FaintQuarterRestInk.Budget(100, 100, exact);
        try {
            c.prepay(0, 1);
            throw new AssertionError();
        } catch (PrintedBeamSource.Limit expected) {
            checks++;
        }
        check(
                c.exhausted && c.workUsed == 0 && c.bytesUsed == 0,
                "parent failure sticky before local advance");
        int calls = exact.calls;
        check(!c.prepay(0, 0) && exact.calls == calls, "failed child cannot retry parent");
        Parent under = new Parent(11);
        var u = new FaintQuarterRestInk.Budget(100, 100, under);
        var v = new FaintQuarterRestInk.Budget(100, 100, under);
        check(u.prepay(4, 8), "under first completed request");
        try {
            v.prepay(6, 8);
            throw new AssertionError();
        } catch (PrintedBeamSource.Limit expected) {
            checks++;
        }
        check(
                v.exhausted && v.workUsed == 0 && v.bytesUsed == 0 && under.actual.remaining == 6,
                "parent one-under refuses atomic request");
        Parent local = new Parent(100);
        var l = new FaintQuarterRestInk.Budget(3, 8, local);
        check(
                !l.prepay(4, 0) && local.calls == 0 && local.actual.remaining == 100,
                "local cap before parent");
        check(l.exhausted && l.workUsed == 0 && l.bytesUsed == 0, "local failure remains exact");
        var error =
                new FaintQuarterRestInk.Budget(
                        100,
                        100,
                        (w, bts) -> {
                            throw new AssertionError("parent error");
                        });
        try {
            error.prepay(1, 1);
            throw new RuntimeException("missing");
        } catch (AssertionError expected) {
            check(expected.getMessage().equals("parent error"), "same parent error");
        }
        check(
                error.exhausted && error.workUsed == 0 && error.bytesUsed == 0,
                "error sticky and counters unchanged");
        try {
            new FaintQuarterRestInk.Budget(1, 1, null);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        for (int i = 0; i < 8; i++) {
            var fork = new FaintQuarterRestInk.Budget(100, 100, exact);
            try {
                fork.prepay(1, 0);
                throw new AssertionError();
            } catch (PrintedBeamSource.Limit expected) {
                checks++;
            }
            check(fork.exhausted && fork.workUsed == 0, "fork cannot mint source units");
        }
        System.out.println("PASS " + checks + " real-parent resource assertions");
    }
}
