// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;

/** Declared-helper defense; reflection manufactures impossible private-constructor inputs.
 * These are not claimed reachable from the actual caller or as source occurrence defects. */
public final class EighthAffinityControls {
    static int checks;

    static void check(boolean b, String s) {
        checks++;
        if (!b) throw new AssertionError(s);
    }

    static final Constructor<?> CT;

    static {
        try {
            CT =
                    FaintEighthRestInk.Proof.class.getDeclaredConstructor(
                            byte[].class,
                            int.class,
                            int.class,
                            SixteenthRestDetector.Staff.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            byte[].class,
                            boolean[].class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            FaintQuarterRestInk.Budget.class);
            CT.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static FaintEighthRestInk.Proof copy(
            FaintEighthRestInk.Proof p,
            int width,
            int height,
            byte[] raw,
            SixteenthRestDetector.Staff frame,
            byte[] pixels,
            FaintQuarterRestInk.Budget meter)
            throws Exception {
        return (FaintEighthRestInk.Proof)
                CT.newInstance(
                        raw,
                        width,
                        height,
                        frame,
                        p.left,
                        p.first,
                        p.w,
                        p.h,
                        pixels,
                        p.carrier.clone(),
                        p.l,
                        p.r,
                        p.t,
                        p.b,
                        meter);
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
            var meter = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
            var p = EighthHelperControls.prove(a, 130, right, meter);
            if (p == null) throw new AssertionError("original witness");
            check(p.same(p, meter), "same genuine witness");
            var same = copy(p, p.width, p.height, p.raw, p.frame, p.pixels.clone(), meter);
            check(p.same(same, meter), "completed equal pixel witness under actual same query");
            check(
                    !p.same(copy(p, p.width + 1, p.height, p.raw, p.frame, p.pixels, meter), meter),
                    "different outer width cannot borrow witness");
            check(
                    !p.same(copy(p, p.width, p.height + 1, p.raw, p.frame, p.pixels, meter), meter),
                    "different outer height cannot borrow witness");
            var foreign = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
            check(
                    !p.same(copy(p, p.width, p.height, p.raw, p.frame, p.pixels, foreign), meter),
                    "other proof meter cannot borrow witness");
            check(!p.same(same, foreign), "current query must own both proofs");
            check(
                    !p.same(copy(p, p.width, p.height, p.raw, p.frame, p.pixels, null), meter),
                    "missing other meter refuses");
            check(
                    !copy(p, p.width, p.height, p.raw, p.frame, p.pixels, null).same(p, meter),
                    "missing receiver meter refuses");
            check(!p.same(same, null), "missing query meter refuses");
            check(
                    !p.same(
                            copy(p, p.width, p.height, p.raw.clone(), p.frame, p.pixels, meter),
                            meter),
                    "copied raw is not identity");
            var equalFrame =
                    new SixteenthRestDetector.Staff(
                            p.frame.top(),
                            p.frame.bottom(),
                            p.frame.gap(),
                            p.frame.index(),
                            p.frame.count());
            check(
                    !p.same(copy(p, p.width, p.height, p.raw, equalFrame, p.pixels, meter), meter),
                    "equal frame is not identity");
            byte[] changed = p.pixels.clone();
            changed[0] ^= 1;
            check(
                    !p.same(copy(p, p.width, p.height, p.raw, p.frame, changed, meter), meter),
                    "pixel mismatch refuses");
            check(!p.same(null, meter), "null proof refuses");
        }
        System.out.println(
                "PASS "
                        + checks
                        + " declared-helper affinity defenses; no actual caller reachability claim");
    }
}
