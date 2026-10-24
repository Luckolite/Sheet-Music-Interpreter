// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A broad single shaft must not become two independent sharp spines by choosing columns. */
public final class SpineIndependenceAdverse {
    public static void main(String[] args) {
        var s = new PartialSignatureOriginals.Scene(16, 0);
        for (int y = 133; y <= 171; y++) for (int x = 136; x <= 144; x++) s.ink(x, y, (byte) 5);
        for (int cy : new int[] {144, 160})
            for (int y = cy; y <= cy + 1; y++)
                for (int x = 133; x <= 147; x++) s.ink(x, y, (byte) 5);
        var o = s.observe();
        if (o.state == PartialSignatureConfidence.State.PARTIAL_POSSIBLE_SHARP)
            throw new AssertionError("single shaft invented pair");
        System.out.println(
                "singleBroadShaftWithTwoCrossbars state="
                        + o.state
                        + " reason="
                        + o.reason
                        + " unsafePairedSpineClaim="
                        + (o.state == PartialSignatureConfidence.State.PARTIAL_POSSIBLE_SHARP)
                        + " actualQueries=0 normalCalls=0");
    }
}
