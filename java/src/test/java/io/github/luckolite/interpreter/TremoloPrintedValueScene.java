// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import static org.junit.Assert.*;

/** Original five-rail source image with genuine collector tokens; no commercial fixture. */
final class TremoloPrintedValueScene {
    static final int W = 240, H = 150;

    record Page(
            byte[] gray,
            byte[] labels,
            List<PairedTremoloInk.Head> heads,
            ExactComponentMembers.Collector collector,
            Map<Integer, Object> components) {}

    private static Object component(ExactComponentMembers.Stats st) throws Exception {
        var c =
                Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component")
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(
                st.area(), st.minX(), st.maxX(), st.minY(), st.maxY(), st.centerX(), st.centerY());
    }

    static Page page(int adverse) throws Exception {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y : new int[] {20, 34, 48, 62, 76})
            for (int x = 1; x < W - 1; x++) {
                gray[y * W + x] = 0;
                labels[y * W + x] = OmrMeasurePostProcessor.STAFF;
            }
        int[] centers = {60, 130};
        for (int cx : centers) {
            for (int y = 84; y <= 96; y++)
                for (int x = cx - 7; x <= cx + 8; x++)
                    if (Math.pow((x - cx) / 8.5, 2) + Math.pow((y - 90) / 6.0, 2) <= 1) {
                        gray[y * W + x] = 0;
                        labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                    }
            int sx = cx + 8;
            for (int y = 24; y <= 90; y++) {
                gray[y * W + sx] = 0;
                if (labels[y * W + sx] != OmrMeasurePostProcessor.NOTEHEAD) labels[y * W + sx] = 1;
            }
        }
        int[] bars = {37, 51, 65};
        for (int bi = 0; bi < bars.length; bi++) {
            int cy = bars[bi];
            double ax = 82,
                    ay = cy + 2.1,
                    bx = 122,
                    by = cy - 1.9,
                    dx = bx - ax,
                    dy = by - ay,
                    den = dx * dx + dy * dy;
            Set<Integer> owned = new HashSet<>();
            for (int x = 81; x <= 122; x++) {
                int center = (int) Math.rint(ay + (x - ax) * dy / dx);
                for (int y = center - 1; y <= center + 1; y++) {
                    gray[y * W + x] = 0;
                    owned.add(y * W + x);
                }
            }
            for (int x : new int[] {81, 82, 101, 102, 121, 122}) {
                int lo = H, hi = -1;
                for (int pixel : owned)
                    if (pixel % W == x) {
                        int y = pixel / W;
                        lo = Math.min(lo, y);
                        hi = Math.max(hi, y);
                    }
                if (x > 76 && x <= 122 && hi >= lo) {
                    int center = (lo + hi) / 2;
                    int[] ys = {center - 2, center + 4, center + 2, center + 3};
                    int[] shades = {210, 240, 220, 245};
                    for (int k = 0; k < ys.length; k++) {
                        int at = ys[k] * W + x;
                        if ((gray[at] & 255) == 255) {
                            gray[at] = (byte) shades[k];
                            owned.add(at);
                        }
                    }
                }
            }
            if (adverse == 5 && bi == 0) {
                for (int x = 90; x <= 100; x++) {
                    List<Integer> column = new ArrayList<>();
                    for (int pixel : owned)
                        if (pixel % W == x && (gray[pixel] & 255) < 185) column.add(pixel);
                    for (int pixel : column) {
                        owned.remove(pixel);
                        gray[pixel] = (byte) 255;
                    }
                    for (int pixel : column) {
                        int shifted = pixel + 2 * W;
                        gray[shifted] = 0;
                        owned.add(shifted);
                    }
                }
            }
            for (int pixel : owned) {
                int shade = gray[pixel] & 255, x = pixel % W;
                if (labels[pixel] != OmrMeasurePostProcessor.STAFF
                        && (shade < 185
                                || ((x == 101 || x == 102) && shade >= 210 && shade <= 245)))
                    labels[pixel] = OmrMeasurePostProcessor.NOTEHEAD;
            }
            if (adverse == 1) { // exact observed paper value, not a contrasted fringe
                for (int x = 74; x <= 124; x++) {
                    int center = (int) Math.rint(ay + (x - ax) * dy / dx);
                    gray[(center - 4) * W + x] = (byte) 245;
                    gray[(center + 4) * W + x] = (byte) 245;
                }
            } else if (adverse == 2) {
                int center = (int) Math.rint(ay + (89 - ax) * dy / dx);
                gray[(center + 4) * W + 89] = (byte) 195;
            } // separate pale lobe outside the exact raw body token
            else if (adverse == 3) {
                int center = (int) Math.rint(ay + (89 - ax) * dy / dx);
                gray[(center - 3) * W + 89] = 0;
            } // connected foreign dark branch
            else if (adverse == 6) {
                for (int x = 81; x <= 122; x++) {
                    int lo = H, hi = -1;
                    for (int pixel : owned)
                        if (pixel % W == x && (gray[pixel] & 255) < 185) {
                            int y = pixel / W;
                            lo = Math.min(lo, y);
                            hi = Math.max(hi, y);
                        }
                    if (hi >= lo) {
                        int up = lo - 1, down = hi + 1;
                        if (labels[up * W + x] != OmrMeasurePostProcessor.STAFF) {
                            gray[up * W + x] = (byte) 150;
                            labels[up * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                        }
                        if (labels[down * W + x] != OmrMeasurePostProcessor.STAFF) {
                            gray[down * W + x] = (byte) 150;
                            labels[down * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
                        }
                    }
                }
            } // exterior profile is ink/unknown on both sides, so no independent paper witness
        }
        if (adverse == 4) { // one semantic component aliases two bars through unobserved paper
            int x = 89;
            for (int y = 31; y <= 37; y++) labels[y * W + x] = OmrMeasurePostProcessor.NOTEHEAD;
        }
        // Flood the actual NOTEHEAD-label components, then mint tokens only from
        // their exact row runs. These are the same object-bound inputs used by caller.
        var components = new ArrayList<List<Integer>>();
        boolean[] seen = new boolean[labels.length];
        for (int at = 0; at < labels.length; at++)
            if (!seen[at] && labels[at] == OmrMeasurePostProcessor.NOTEHEAD) {
                var found = new ArrayList<Integer>();
                var q = new ArrayDeque<Integer>();
                q.add(at);
                seen[at] = true;
                while (!q.isEmpty()) {
                    int p = q.removeFirst();
                    found.add(p);
                    int x = p % W, y = p / W;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++)
                            if (dx != 0 || dy != 0) {
                                int xx = x + dx, yy = y + dy;
                                if (xx >= 0 && xx < W && yy >= 0 && yy < H) {
                                    int n = yy * W + xx;
                                    if (!seen[n] && labels[n] == OmrMeasurePostProcessor.NOTEHEAD) {
                                        seen[n] = true;
                                        q.addLast(n);
                                    }
                                }
                            }
                }
                components.add(found);
            }
        var collector =
                new ExactComponentMembers.Collector(
                        gray,
                        labels,
                        W,
                        H,
                        new ExactComponentMembers.Budget(2L * gray.length + 4096, 4096),
                        new ExactComponentMembers.Budget(500_000, 8L * 1024 * 1024));
        assertTrue(collector.bindInput(labels, gray, W, H));
        var ids = new ArrayList<Object>();
        var stats = new ArrayList<ExactComponentMembers.Stats>();
        for (var pixels : components) {

            var b = collector.begin();
            int n = 0, minX = W, maxX = -1, minY = H, maxY = -1;
            long sx = 0, sy = 0;
            for (int y = 0; y < H; y++) {
                int x = 0;
                while (x < W) {
                    while (x < W && !pixels.contains(y * W + x)) x++;
                    if (x >= W) break;
                    int left = x;
                    while (x + 1 < W && pixels.contains(y * W + x + 1)) x++;
                    int right = x;
                    b.add(y, left, right);
                    for (int px = left; px <= right; px++) {
                        n++;
                        minX = Math.min(minX, px);
                        maxX = Math.max(maxX, px);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        sx += px;
                        sy += y;
                    }
                    x++;
                }
            }
            var st =
                    new ExactComponentMembers.Stats(
                            n, minX, maxX, minY, maxY, (float) sx / n, (float) sy / n);
            stats.add(st);
            Object id = component(st);
            ids.add(id);
            collector.record(id, st, OmrMeasurePostProcessor.NOTEHEAD, b);
        }
        assertTrue(collector.seal(new ExactComponentMembers.Budget(2L * gray.length + 4096, 4096)));
        var componentsMap = new HashMap<Integer, Object>();
        var endpoints = new ArrayList<PairedTremoloInk.Head>();
        var middles = new ArrayList<PairedTremoloInk.Head>();
        for (int i = 0; i < components.size(); i++) {
            var token = collector.acceptedProbeHead(ids.get(i), 0, 0);
            assertNotNull(token);
            int[] owned = token.pixels(new ExactComponentMembers.Budget(100_000, 4L * 1024 * 1024));
            assertNotNull(owned);
            List<Integer> footprint = Arrays.stream(owned).boxed().toList();
            var st = stats.get(i);
            if (st.centerY() > 80) {
                int cx = st.centerX() < 100 ? 60 : 130;
                componentsMap.put(cx == 60 ? 1 : 2, ids.get(i));
                endpoints.add(
                        new PairedTremoloInk.Head(
                                cx == 60 ? 1 : 2,
                                0,
                                0,
                                cx,
                                90,
                                14,
                                1,
                                footprint,
                                true,
                                20,
                                token,
                                cx + 8,
                                90,
                                24));
            } else {
                int cy = (int) Math.rint(st.centerY());
                int id = 10 + middles.size();
                componentsMap.put(id, ids.get(i));
                middles.add(
                        new PairedTremoloInk.Head(
                                id, 0, 0, 91, cy, 14, 0, footprint, true, 20, token, -1, -1, -1));
            }
        }
        endpoints.sort(Comparator.comparingDouble(PairedTremoloInk.Head::x));
        middles.sort(Comparator.comparingDouble(PairedTremoloInk.Head::y));
        var all = new ArrayList<PairedTremoloInk.Head>(endpoints);
        all.addAll(middles);
        return new Page(gray, labels, List.copyOf(all), collector, Map.copyOf(componentsMap));
    }
}
