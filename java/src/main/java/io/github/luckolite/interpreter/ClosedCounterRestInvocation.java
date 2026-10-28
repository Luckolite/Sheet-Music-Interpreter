// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** One explicitly passed optional ledger; no global, thread-local or identity cache. */
final class ClosedCounterRestInvocation {
    private static final class Mark {
        final ScoreRestEvent event;
        final Mark next;

        Mark(ScoreRestEvent event, Mark next) {
            this.event = event;
            this.next = next;
        }
    }

    private Mark marks;
    private boolean finished;
    final ClosedCounterRestInk.Budget budget;
    final byte[] original;
    final int width, height;
    final Thread thread = Thread.currentThread();

    private ClosedCounterRestInvocation(
            ClosedCounterRestInk.Budget budget, byte[] original, int w, int h) {
        this.budget = budget;
        this.original = original;
        width = w;
        height = h;
    }

    static ClosedCounterRestInvocation open(byte[] view, int w, int h, List<ScoreNoteEvent> notes) {
        if (view == null
                || w < 1
                || h < 1
                || view.length != (long) w * h
                || view.length > 20_000_000) return null;
        // New separate feature allowance; all pre-existing detection allowances are unchanged.
        var budget =
                new ClosedCounterRestInk.Budget(Math.max(2_000_000, 2L * view.length), 1_000_000);
        try {
            budget.take(128, 256); // Budget/context setup and retention, before context allocation.
            var frame = RestFigureOrigins.originalFrame(notes, view, w, h, budget);
            if (frame == null) return null;
            return new ClosedCounterRestInvocation(budget, frame.source, w, h);
        } catch (ClosedCounterRestInk.Refusal refusal) {
            return null;
        }
    }

    boolean disproves(
            boolean eighth,
            boolean deepLowered,
            byte[] view,
            int w,
            int h,
            List<ScoreNoteEvent> notes,
            RestFigureOrigins.ProposalOrigin proposal,
            int l,
            int r,
            int t,
            int b,
            float gap) {
        try {
            budget.take(2, 0);
            if (!eighth || !deepLowered || thread != Thread.currentThread()) return false;
            var frame = RestFigureOrigins.originalFrame(notes, view, w, h, budget);
            if (frame == null || !frame.rootedAt(original, width, height, budget)) return false;
            return ClosedCounterRestInk.owns(
                    frame, notes, view, w, h, proposal, l, r, t, b, gap, budget);
        } catch (ClosedCounterRestInk.Refusal refusal) {
            return false;
        }
    }

    private boolean marked(ScoreRestEvent event) {
        for (Mark m = marks; m != null; m = m.next) {
            budget.take(2, 0);
            if (m.event == event) return true;
        }
        return false;
    }

    void record(ScoreRestEvent event) {
        try {
            budget.take(8, 0);
            if (finished || thread != Thread.currentThread() || event == null) return;
            if (!marked(event)) {
                budget.take(16, 48);
                marks = new Mark(event, marks);
            }
        } catch (ClosedCounterRestInk.Refusal refusal) {
        } // Failed ledger makes finish return legacy.
    }

    ScoreRestEvent mapped(ScoreRestEvent source, ScoreRestEvent mapped) {
        try {
            budget.take(8, 0);
            if (!finished && thread == Thread.currentThread() && marked(source)) record(mapped);
        } catch (ClosedCounterRestInk.Refusal refusal) {
        }
        return mapped;
    }

    SixteenthRestDetector.Detection finish(SixteenthRestDetector.Detection legacy) {
        try {
            budget.take(16, 0);
            if (finished || thread != Thread.currentThread()) return legacy;
            finished = true;
            if (marks == null) return legacy;
            long n = (long) legacy.rests().size() + legacy.dots().size();
            // Lists, immutable copies, default-wrapper writeback, and retained result header.
            budget.take(128 + 64 * n, 192 + 64 * n);
            var rests = new java.util.ArrayList<ScoreRestEvent>(legacy.rests().size());
            var dots = new java.util.ArrayList<SixteenthRestDetector.RestDot>(legacy.dots().size());
            for (var rest : legacy.rests()) if (!marked(rest)) rests.add(rest);
            for (var dot : legacy.dots()) if (!marked(dot.rest())) dots.add(dot);
            return new SixteenthRestDetector.Detection(
                    java.util.List.copyOf(rests), java.util.List.copyOf(dots));
        } catch (ClosedCounterRestInk.Refusal refusal) {
            return legacy;
        }
    }
}
