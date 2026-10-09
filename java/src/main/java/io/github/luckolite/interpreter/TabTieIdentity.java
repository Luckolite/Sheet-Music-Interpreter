// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Typed tab boundary evidence: direction, string, fret and effective open pitch. */
public final class TabTieIdentity {
    public static final int DIRECTIONS = 15;
    public static final int TAG = 1 << 4;
    public static final int ALL = (1 << 22) - 1;

    private TabTieIdentity() {}

    public static int encode(
            int directions, int string, int stringCount, int fret, int effectiveOpen) {
        if (directions < 1
                || directions > DIRECTIONS
                || stringCount < 6
                || stringCount > 7
                || string < 0
                || string >= stringCount
                || fret < 0
                || fret > 36
                || effectiveOpen < 0
                || effectiveOpen > 127 - fret)
            throw new IllegalArgumentException("Invalid tab tie identity");
        return directions
                | TAG
                | string << 5
                | (stringCount - 6) << 8
                | fret << 9
                | effectiveOpen << 15;
    }

    public static boolean typed(int evidence) {
        return (evidence & TAG) != 0;
    }

    /** Legacy staff-direction bits remain valid; untagged metadata never does. */
    public static boolean valid(int evidence) {
        if ((evidence & ~ALL) != 0) return false;
        if (!typed(evidence)) return (evidence & ~DIRECTIONS) == 0;
        int string = evidence >>> 5 & 7;
        int count = 6 + (evidence >>> 8 & 1);
        int fret = evidence >>> 9 & 63;
        int open = evidence >>> 15 & 127;
        return (evidence & DIRECTIONS) != 0 && string < count && fret <= 36 && open + fret <= 127;
    }

    /** A legacy pitch-only shoulder cannot prove the same tab string. */
    public static boolean compatible(int a, int b) {
        if (!valid(a) || !valid(b)) return false;
        return typed(a) || typed(b)
                ? typed(a) && typed(b) && (a & ~DIRECTIONS) == (b & ~DIRECTIONS)
                : true;
    }
}
