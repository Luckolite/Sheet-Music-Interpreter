// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.*;
import java.util.*;

public final class PrintedBeamGeometryOriginals {
    public static void main(String[] args) throws Exception {
        Path fixtureBase = Path.of(args[0]).toAbsolutePath().getParent();
        int fixtures = 0, links = 0, hooks = 0;
        long work = 0;
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] f = line.split("\\t", -1);
            int w = Integer.parseInt(f[2]),
                    h = Integer.parseInt(f[3]),
                    gap = Integer.parseInt(f[4]),
                    direction = Integer.parseInt(f[5]);
            byte[] raw = Files.readAllBytes(fixtureBase.resolve(f[1]).normalize());
            boolean[] carrier = new boolean[raw.length];
            for (String b : f[8].split(";")) {
                String[] z = b.split(",");
                for (int y = Integer.parseInt(z[0]); y <= Integer.parseInt(z[1]); y++)
                    for (int x = 0; x < w; x++) carrier[y * w + x] = true;
            }
            var columns = new ArrayList<PrintedBeamGeometry.Column>();
            String[] cs = f[9].split(";");
            for (int i = 0; i < cs.length; i++) {
                String[] z = cs[i].split(",");
                columns.add(
                        new PrintedBeamGeometry.Column(
                                i,
                                0,
                                0,
                                1,
                                direction,
                                Integer.parseInt(z[0]),
                                Integer.parseInt(z[1]),
                                Integer.parseInt(z[2]),
                                gap,
                                3));
            }
            var r =
                    PrintedBeamGeometry.detect(
                            raw,
                            w,
                            h,
                            columns,
                            (a, b, x, y) -> carrier[y * w + x],
                            new PrintedBeamGeometry.Budget(64000000));
            if (r.budgetExceeded()) throw new AssertionError("Budget " + f[0]);
            work += r.work();
            Set<String> found = new TreeSet<>(), foundHooks = new TreeSet<>();
            for (var group : r.groups()) {
                for (int i = 0; i < group.bridges().size(); i++)
                    for (int level = 1; level <= 8; level++)
                        if ((group.bridges().get(i) & (1 << (level - 1))) != 0)
                            found.add(
                                    group.columns().get(i)
                                            + ","
                                            + group.columns().get(i + 1)
                                            + ","
                                            + level);
                for (var hook : group.hooks())
                    foundHooks.add(
                            hook.columnId()
                                    + ","
                                    + hook.level()
                                    + ","
                                    + (hook.direction() > 0 ? "forward" : "backward"));
            }
            Set<String>
                    expected =
                            f[10].isEmpty() ? Set.of() : new TreeSet<>(List.of(f[10].split(";"))),
                    expectedHooks =
                            f[11].isEmpty() ? Set.of() : new TreeSet<>(List.of(f[11].split(";")));
            if (!found.equals(expected))
                throw new AssertionError(f[0] + " links expected=" + expected + " actual=" + found);
            if (!foundHooks.equals(expectedHooks))
                throw new AssertionError(
                        f[0] + " hooks expected=" + expectedHooks + " actual=" + foundHooks);
            fixtures++;
            links += found.size();
            hooks += foundHooks.size();
        }
        // Original alternating foreground above/below one carrier must not subsidize
        // a common candidate strip with less than its own required non-carrier core.
        int coreCases = 0;
        for (String name :
                List.of(
                        "thin_staff",
                        "alternating_clutter_plus_staff",
                        "alternating_clutter_only",
                        "true_noncarrier_beam")) {
            int w = 40, h = 80;
            byte[] raw = new byte[w * h];
            Arrays.fill(raw, (byte) 255);
            boolean[] carrier = new boolean[raw.length];
            if (!name.equals("alternating_clutter_only"))
                for (int y = 30; y <= 32; y++)
                    for (int x = 0; x < w; x++) {
                        raw[y * w + x] = 0;
                        carrier[y * w + x] = true;
                    }
            if (name.startsWith("alternating"))
                for (int x = 10; x <= 28; x++) {
                    int lo = x % 2 == 0 ? 27 : 33;
                    for (int y = lo; y < lo + 3; y++) raw[y * w + x] = 0;
                }
            if (name.equals("true_noncarrier_beam"))
                for (int y = 33; y <= 35; y++) for (int x = 0; x < w; x++) raw[y * w + x] = 0;
            boolean accepted =
                    FiniteRawBeamStrip.prove(raw, carrier, w, h, 10, 28, 30, 30, 14, -1, 0, 0)
                            != null;
            if (accepted != name.equals("true_noncarrier_beam"))
                throw new AssertionError("Core " + name);
            coreCases++;
        }
        int terminalCases = 0;
        for (int scale : new int[] {1, 2})
            for (int direction : new int[] {-1, 1})
                for (String kind :
                        List.of(
                                "full",
                                "left_white",
                                "right_white",
                                "both_white",
                                "left_carrier",
                                "right_carrier",
                                "hook",
                                "hook_white",
                                "hook_carrier")) {
                    int w = 96 * scale,
                            h = 112 * scale,
                            left = 20 * scale,
                            right = 72 * scale,
                            thick = 6 * scale,
                            top = 24 * scale,
                            hookTop = 38 * scale;
                    byte[] raw = new byte[w * h];
                    Arrays.fill(raw, (byte) 255);
                    boolean[] carrier = new boolean[raw.length];
                    for (int x : left == right ? new int[] {left} : new int[] {left, right})
                        for (int y = top; y <= 92 * scale; y++) raw[y * w + x] = 0;
                    for (int x = left; x <= right; x++)
                        for (int y = top; y < top + thick; y++) raw[y * w + x] = 0;
                    if (kind.startsWith("hook"))
                        for (int x = left; x <= left + 18 * scale; x++)
                            for (int y = hookTop; y < hookTop + thick; y++) raw[y * w + x] = 0;
                    if (kind.contains("left") || kind.equals("both_white"))
                        for (int y = top; y < top + thick; y++) raw[y * w + left + 1] = (byte) 255;
                    if (kind.contains("right") || kind.equals("both_white"))
                        for (int y = top; y < top + thick; y++) raw[y * w + right - 1] = (byte) 255;
                    if (kind.equals("hook_white") || kind.equals("hook_carrier"))
                        for (int y = hookTop; y < hookTop + thick; y++)
                            raw[y * w + left + 1] = (byte) 255;
                    if (kind.contains("carrier")) {
                        int stripe =
                                kind.equals("hook_carrier") ? hookTop + 2 * scale : top + 2 * scale;
                        for (int x = 0; x < w; x++)
                            for (int y = stripe; y < stripe + 2 * scale; y++) {
                                raw[y * w + x] = 0;
                                carrier[y * w + x] = true;
                            }
                    }
                    int cap = top;
                    if (direction > 0) {
                        byte[] reflected = new byte[raw.length];
                        boolean[] reflectedCarrier = new boolean[carrier.length];
                        for (int y = 0; y < h; y++)
                            for (int x = 0; x < w; x++) {
                                reflected[(h - 1 - y) * w + x] = raw[y * w + x];
                                reflectedCarrier[(h - 1 - y) * w + x] = carrier[y * w + x];
                            }
                        raw = reflected;
                        carrier = reflectedCarrier;
                        cap = h - 1 - top;
                    }
                    final boolean[] mask = carrier;
                    var ns =
                            List.of(
                                    new PrintedBeamGeometry.Column(
                                            0, 0, 0, 1, direction, left, left, cap, 16 * scale, 2),
                                    new PrintedBeamGeometry.Column(
                                            1, 0, 0, 1, direction, right, right, cap, 16 * scale,
                                            2));
                    var result =
                            PrintedBeamGeometry.detect(
                                    raw,
                                    w,
                                    h,
                                    ns,
                                    (a, b, x, y) -> mask[y * w + x],
                                    new PrintedBeamGeometry.Budget(64000000));
                    boolean primary = result.groups().size() == 1,
                            hook =
                                    primary
                                            && result.groups().get(0).hooks().stream()
                                                    .anyMatch(
                                                            z ->
                                                                    z.columnId() == 0
                                                                            && z.level() == 2
                                                                            && z.direction() > 0);
                    boolean expectPrimary = kind.equals("full") || kind.startsWith("hook");
                    if (result.budgetExceeded()
                            || primary != expectPrimary
                            || hook != kind.equals("hook"))
                        throw new AssertionError(
                                "Terminal "
                                        + kind
                                        + " scale="
                                        + scale
                                        + " direction="
                                        + direction
                                        + " primary="
                                        + primary
                                        + " hook="
                                        + hook);
                    terminalCases++;
                }
        System.out.println(
                "{\"status\":\"ORIGINAL_GLOBAL_GEOMETRY_PASS\",\"fixtures\":"
                        + fixtures
                        + ",\"links\":"
                        + links
                        + ",\"hooks\":"
                        + hooks
                        + ",\"extra\":0,\"carrierCoreCases\":"
                        + coreCases
                        + ",\"terminalCases\":"
                        + terminalCases
                        + ",\"work\":"
                        + work
                        + "}");
    }
}
