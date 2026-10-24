// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Exact original float role predicates versus indexed discovery, plus same-measure refusal. */
public class FiniteSlideOwnerIndexOriginalChecks {
    static int checks;

    static void check(boolean yes, String text) {
        checks++;
        if (!yes) throw new AssertionError(text);
        System.out.println("PASS " + text);
    }

    static int originalSource(List<NoteSlideDetector.Head> heads, int index) {
        var target = heads.get(index);
        float g = target.gap(), latest = -Float.MAX_VALUE;
        for (var q : heads)
            if (q.staff() == target.staff()
                    && q.measure() == target.measure()
                    && q.x() < target.x() - g * .35f) latest = Math.max(latest, q.x());
        int source = -1;
        for (int i = 0; i < heads.size(); i++) {
            var q = heads.get(i);
            if (q.staff() == target.staff()
                    && q.measure() == target.measure()
                    && q.x() < target.x() - g * .35f
                    && latest - q.x() <= g * .35f) {
                if (source >= 0) return -1;
                source = i;
            }
            if (i != index
                    && q.staff() == target.staff()
                    && q.measure() == target.measure()
                    && Math.abs(q.x() - target.x()) <= g * .35f) return -1;
        }
        return source;
    }

    public static void main(String[] args) {
        var s = new FiniteConnectedSlidePortsOriginalChecks.Scene(1, false, -1, 1);
        s.line();
        var q = s.heads.get(1);
        s.heads.set(1, new NoteSlideDetector.Head(q.x(), q.y(), q.gap(), q.staff(), 1));
        check(
                s.port().isEmpty(),
                "same-measure gate unchanged despite complete cross-bar-looking ink");
        s = new FiniteConnectedSlidePortsOriginalChecks.Scene(1, false, -1, 1);
        s.line();
        Collections.reverse(s.heads);
        check(
                s.port().size() == 1 && s.port().get(0).noteIndex() == 0,
                "snapshot/index retains original target index after metadata reorder");
        float g = 14, cutSource = 110 - g * .35f, cutTarget = 180 - g * .35f;
        for (float x :
                new float[] {
                    Math.nextDown(cutSource),
                    cutSource,
                    Math.nextUp(cutSource),
                    Math.nextDown(cutTarget),
                    cutTarget,
                    Math.nextUp(cutTarget)
                }) {
            s = new FiniteConnectedSlidePortsOriginalChecks.Scene(1, false, -1, 1);
            s.line();
            s.heads.add(new NoteSlideDetector.Head(x, 140, g, 0, 0));
            boolean expected = originalSource(s.heads, 1) == 0;
            var result = s.port();
            check(
                    (result.size() == 1) == expected,
                    "indexed role window matches original exact float predicates x="
                            + x
                            + " expected="
                            + expected);
        }
        System.out.println("CHECKS=" + checks);
    }
}
