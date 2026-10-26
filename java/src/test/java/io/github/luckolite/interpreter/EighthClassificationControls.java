// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.*;
import java.util.*;

/** Original raw/classification separation and genuine budget-bound metadata controls. */
public final class EighthClassificationControls {
    static int checks;

    static void check(boolean b, String s) {
        checks++;
        if (!b) throw new AssertionError(s);
    }

    static void run(
            EighthHelperControls.Input a,
            int left,
            int right,
            FaintQuarterRestInk.Budget q,
            boolean assertions) {
        byte[] before = a.raw().clone(), target = a.raw().clone();
        var proof = EighthHelperControls.prove(a, left, right, q);
        if (proof == null) throw new AssertionError("original proof");
        int[] indices = new int[proof.w * proof.h];
        byte[] old = new byte[indices.length];
        boolean[] line = new boolean[proof.h];
        int changed = FaintEighthRestInk.classify(proof, target, indices, old, line, q);
        if (assertions) {
            check(changed > 0, "real classification edits");
            check(Arrays.equals(a.raw(), before), "original raw unchanged");
            for (int at = 0; at < target.length; at++)
                if (target[at] != before[at]) {
                    int x = at % a.w() - proof.left, y = at / a.w() - proof.first;
                    check(x >= 0 && x < proof.w && y >= 0 && y < proof.h, "crop bounded");
                    if ((target[at] & 255) == 169)
                        check(
                                (before[at] & 255) < 230 && proof.pixels[y * proof.w + x] != 0,
                                "only observed body ink promoted");
                    else
                        check(
                                (target[at] & 255) == 255
                                        && proof.carrier[y]
                                        && (before[at] & 255) < 170,
                                "only corroborated carrier ink masked");
                }
            check(
                    proof.binds(a.raw(), a.w(), a.h(), a.frame(), proof.l, proof.r, q),
                    "exact binding");
            check(
                    !proof.binds(before, a.w(), a.h(), a.frame(), proof.l, proof.r, q),
                    "copied raw is not authority");
            var equal =
                    new SixteenthRestDetector.Staff(
                            a.frame().top(), a.frame().bottom(), a.frame().gap(), 0, 1);
            check(
                    !proof.binds(a.raw(), a.w(), a.h(), equal, proof.l, proof.r, q),
                    "equal frame is not same actual frame");
        }
        FaintEighthRestInk.restore(target, indices, old, changed, q);
        if (assertions) check(Arrays.equals(target, before), "fully paid restore exact");
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        for (String name :
                List.of(
                        "002-eighth-g10.0-p0.0-t200-b0.6",
                        "015-eighth-g14.25-p0.5-t200-b1.0",
                        "020-eighth-g18.0-p0.5-t170-b0.6")) {
            var a = EighthHelperControls.read(root.resolve(name + ".bin"));
            int right = name.startsWith("002") ? 139 : name.startsWith("015") ? 143 : 148;
            run(a, 130, right, new FaintQuarterRestInk.Budget(64_000_000, 64_000_000), true);
            var measured = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
            run(a, 130, right, measured, false);
            var exact = new FaintQuarterRestInk.Budget(measured.workUsed, measured.bytesUsed);
            run(a, 130, right, exact, false);
            check(
                    exact.workUsed == measured.workUsed && exact.bytesUsed == measured.bytesUsed,
                    "exact complete mechanism/classify/restore");
            for (int axis = 0; axis < 2; axis++) {
                var low =
                        new FaintQuarterRestInk.Budget(
                                measured.workUsed - (axis == 0 ? 1 : 0),
                                measured.bytesUsed - (axis == 1 ? 1 : 0));
                boolean refused = false;
                try {
                    run(a, 130, right, low, false);
                } catch (FaintQuarterRestInk.Refusal e) {
                    refused = true;
                }
                check(refused && low.exhausted, "oneunder real fragment");
                check(
                        low.workUsed <= low.workLimit && low.bytesUsed <= low.byteLimit,
                        "atomic counters");
            }
        }
        System.out.println(
                "PASS " + checks + " original classification/raw/binding/restore assertions");
    }
}
