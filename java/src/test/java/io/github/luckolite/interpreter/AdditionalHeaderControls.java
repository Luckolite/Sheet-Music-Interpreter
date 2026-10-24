// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class AdditionalHeaderControls {
    static int checks;

    static void check(boolean b, String reason) {
        checks++;
        if (!b) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        var partial = new PartialSignatureOriginals.Scene(16, 0);
        partial.partial(false, true, false, false);
        var observation = partial.observe();
        check(
                !observation.refusesDecrease(0, 1, true, false, partial.g, partial.a, 400, 320),
                "initial key unaffected");
        check(
                !observation.refusesDecrease(2, -1, true, true, partial.g, partial.a, 400, 320),
                "natural-to-flat cancellation unaffected");
        check(
                !observation.refusesDecrease(2, 1, true, true, partial.g, partial.a, 400, 320),
                "explicit natural+sharp cancellation unaffected");
        // Refuse one staff's unsupported vote, preserving another staff's positive key vote.
        var staffVotes = new ArrayList<Integer>();
        if (!observation.refusesDecrease(2, 1, true, false, partial.g, partial.a, 400, 320))
            staffVotes.add(1);
        staffVotes.add(1);
        check(staffVotes.equals(List.of(1)), "corroborating other staff still votes decrease");
        check(
                PartialSignatureConfidence.inspect(
                                        partial.g, partial.a, 400, 320, 120, 128, 16, 216, 0)
                                .state
                        == PartialSignatureConfidence.State.UNKNOWN,
                "zero budget explicit unknown");
        check(
                PartialSignatureConfidence.inspect(
                                        partial.g, partial.a, 400, 320, 399, 128, 16, 500)
                                .state
                        == PartialSignatureConfidence.State.UNKNOWN,
                "clipped explicit unknown");
        check(
                !observation.sameSource(partial.g, partial.a.clone(), 400, 320),
                "foreign semantic frame refused");
        var clear = new PartialSignatureOriginals.Scene(16, 0);
        // Complete first sharp supplied as existing caller-recognized source context.
        for (int y = 108; y <= 148; y++)
            for (int x : new int[] {116, 117, 124, 125}) clear.ink(x, y, (byte) 3);
        for (int cy : new int[] {120, 136})
            for (int y = cy; y <= cy + 1; y++)
                for (int x = 112; x <= 128; x++) clear.ink(x, y, (byte) 3);
        check(
                !clear.observe().refusesDecrease(2, 1, true, false, clear.g, clear.a, 400, 320),
                "clear complete one-sharp header decrease unblocked");
        for (String name : List.of("oneCue", "twoCueChord", "barline", "oneStem", "twoStems")) {
            var s = new PartialSignatureOriginals.Scene(16, 0);
            if (name.equals("barline"))
                for (int y = 105; y <= 215; y++)
                    for (int x = 140; x <= 142; x++) s.ink(x, y, (byte) 3);
            if (name.endsWith("Stem") || name.endsWith("Stems"))
                for (int x : name.equals("oneStem") ? new int[] {140} : new int[] {140, 148})
                    for (int y = 132; y <= 174; y++) s.ink(x, y, (byte) 1);
            if (name.contains("Cue"))
                for (int cy : name.equals("oneCue") ? new int[] {150} : new int[] {144, 160})
                    for (int y = cy - 3; y <= cy + 3; y++)
                        for (int x = 136; x <= 144; x++)
                            if ((x - 140) * (x - 140) / 16.0 + (y - cy) * (y - cy) / 9.0 <= 1)
                                s.ink(x, y, (byte) 2);
            check(
                    !s.observe().refusesDecrease(2, 1, true, false, s.g, s.a, 400, 320),
                    name + " cannot invent a paired residue");
            System.out.println("PASS noVeto=" + name + " state=" + s.observe().state);
        }
        System.out.println(
                "PASS additionalHeaderChecks="
                        + checks
                        + " scope=conditional-staff-confidence-law-only currentKeyCallerInvocations=0 actualQueries=0 normalCalls=0");
    }
}
