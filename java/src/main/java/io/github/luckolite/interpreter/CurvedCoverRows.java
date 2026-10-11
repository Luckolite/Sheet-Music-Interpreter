// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/** Pixel-only curved-cover proposals. No text hints or metadata assignment. */
public final class CurvedCoverRows {
    private CurvedCoverRows() {}

    public record Raster(int width, int height, int[] argb) {}

    public record Part(int x, int y, int width, int height, int area) {}

    public record Row(
            int cluster,
            List<Part> parts,
            double[] quadratic,
            double medianHeight,
            double maxResidual,
            double[] mapBounds,
            Raster raw,
            Raster cleaned) {}

    record Fit(List<Integer> indices, double[] q, double median, double residual, int x0, int x1) {}

    public static List<Row> propose(Raster source, boolean photographicCover, double notationTop) {
        if (!photographicCover
                || !Double.isFinite(notationTop)
                || notationTop >= 0
                || source == null
                || source.width < 32
                || source.height < 32
                || (long) source.width * source.height > 2_500_000
                || source.argb == null
                || source.argb.length != (long) source.width * source.height) return List.of();
        // The caller supplies the opening half at 1800px. Copy to keep input ownership stable.
        int w = source.width, h = source.height;
        int[] pixels = source.argb.clone();
        // PdfRenderer input is composited on white. Reject undefined alpha rather
        // than exposing invisible RGB as opaque ink during sampling.
        for (int pixel : pixels) if ((pixel >>> 24) != 255) return List.of();
        byte[] labels = palette(pixels);
        List<List<Part>> groups = components(labels, w, h);
        List<Row> result = new ArrayList<>();
        for (int cluster = 0; cluster < 8; cluster++) {
            List<Part> parts = groups.get(cluster);
            int n = parts.size();
            if (n < 5 || n > 300) continue;
            double[] xx = new double[n], yy = new double[n];
            for (int i = 0; i < n; i++) {
                Part p = parts.get(i);
                xx[i] = p.x + p.width * .5;
                yy[i] = p.y + p.height * .5;
            }
            LinkedHashMap<String, Fit> fits = new LinkedHashMap<>();
            Random random = new Random(0);
            int seeds = n <= 15 ? n * (n - 1) * (n - 2) / 6 : 500;
            int sa = 0, sb = 1, sc = 2;
            for (int seed = 0; seed < seeds; seed++) {
                int a, b, c;
                if (n <= 15) {
                    a = sa;
                    b = sb;
                    c = sc;
                    if (++sc == n) {
                        if (++sb == n - 1) {
                            sa++;
                            sb = sa + 1;
                        }
                        sc = sb + 1;
                    }
                } else {
                    a = random.nextInt(n);
                    do {
                        b = random.nextInt(n);
                    } while (b == a);
                    do {
                        c = random.nextInt(n);
                    } while (c == a || c == b);
                }
                double[] heights = {parts.get(a).height, parts.get(b).height, parts.get(c).height};
                Arrays.sort(heights);
                double med = heights[1];
                double[] sx = {xx[a], xx[b], xx[c]};
                Arrays.sort(sx);
                if (sx[2] - sx[0] < med * 3
                        || Math.min(sx[1] - sx[0], sx[2] - sx[1]) < med * .2
                        || heights[2] > heights[0] * 1.7) continue;
                double[] q = fit(List.of(a, b, c), xx, yy, w);
                if (q == null || Math.abs(q[0]) > w * 1.5) continue;
                List<Integer> keep = new ArrayList<>();
                for (int i = 0; i < n; i++)
                    if (Math.abs(value(q, xx[i] / w) - yy[i]) < med * .22
                            && parts.get(i).height > med * .65
                            && parts.get(i).height < med * 1.5) keep.add(i);
                if (keep.size() < 5) continue;
                keep.sort(Comparator.comparingDouble(i -> xx[i]));
                List<Integer> chain = new ArrayList<>();
                for (int i : keep) {
                    if (!chain.isEmpty()
                            && !neighbors(
                                    parts.get(chain.get(chain.size() - 1)), parts.get(i), med)) {
                        nominate(chain, parts, xx, yy, w, med, fits);
                        chain = new ArrayList<>();
                    }
                    chain.add(i);
                }
                nominate(chain, parts, xx, yy, w, med, fits);
            }
            List<Fit> sorted = new ArrayList<>(fits.values());
            sorted.sort(Comparator.comparingInt((Fit f) -> f.indices.size()).reversed());
            boolean[] consumed = new boolean[n];
            for (Fit seed : sorted) {
                Fit f = closeEndpoints(seed, parts, xx, yy, w, h);
                int overlap = 0;
                for (int i : f.indices) if (consumed[i]) overlap++;
                if (overlap > f.indices.size() * .5 || !validFit(f.q, f.x0, f.x1, f.median, w, h))
                    continue;
                List<Part> chain = new ArrayList<>();
                for (int i : f.indices) chain.add(parts.get(i));
                Row row = rectify(pixels, labels, w, h, cluster, chain, f);
                if (row == null) continue;
                // A failed transform/envelope must not suppress a later valid alternative.
                for (int i : f.indices) consumed[i] = true;
                result.add(row);
                if (result.size() >= 24) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }

    static boolean neighbors(Part a, Part b, double median) {
        double gap = b.x - a.x - a.width;
        return gap >= -Math.min(a.width, b.width) * .75 && gap <= median;
    }

    static double value(double[] q, double x) {
        return (q[0] * x + q[1]) * x + q[2];
    }

    static boolean validFit(double[] q, double x0, double x1, double med, int w, int h) {
        if (q == null
                || q.length != 3
                || w <= 0
                || h <= 0
                || med <= 0
                || !Double.isFinite(med)
                || !Double.isFinite(x0)
                || !Double.isFinite(x1)
                || x0 < 0
                || x1 >= w
                || x1 <= x0) return false;
        for (double v : q) if (!Double.isFinite(v)) return false;
        double vertex = q[0] == 0 ? Double.NaN : -q[1] * w / (2 * q[0]);
        double[] samples =
                Double.isFinite(vertex) && vertex >= x0 && vertex <= x1
                        ? new double[] {x0, x1, vertex}
                        : new double[] {x0, x1};
        for (double x : samples) {
            double slope = (2 * q[0] * x / w + q[1]) / w;
            double curvature =
                    Math.abs(2 * q[0] / ((double) w * w)) / Math.pow(1 + slope * slope, 1.5);
            double y = value(q, x / w);
            if (Math.abs(slope) > 1.5
                    || curvature * med > 1. / 3
                    || y - Math.round(med * .95) < 0
                    || y + Math.round(med * .95) >= h) return false;
        }
        return true;
    }

    private static void nominate(
            List<Integer> chain,
            List<Part> parts,
            double[] x,
            double[] y,
            int w,
            double med,
            LinkedHashMap<String, Fit> fits) {
        Fit f = fitChain(chain, parts, x, y, w, med);
        if (f != null) fits.put(chain.toString(), f);
    }

    private static Fit fitChain(
            List<Integer> chain, List<Part> parts, double[] x, double[] y, int w, double med) {
        if (chain.size() < 5) return null;
        int left = Integer.MAX_VALUE, right = 0;
        for (int i : chain) {
            Part p = parts.get(i);
            left = Math.min(left, p.x);
            right = Math.max(right, p.x + p.width);
        }
        if (right - left < med * 5) return null;
        double[] q = fit(chain, x, y, w);
        if (q == null) return null;
        double residual = 0;
        for (int i : chain) residual = Math.max(residual, Math.abs(value(q, x[i] / w) - y[i]));
        if (residual > med * .22) return null;
        return new Fit(List.copyOf(chain), q, med, residual, left, right);
    }

    /** Refit can make an adjacent endpoint eligible after the initial seed excluded it.
     * Revisit only endpoints, retaining the original height, gap and residual limits. */
    static Fit closeEndpoints(Fit seed, List<Part> parts, double[] x, double[] y, int w, int h) {
        Fit current = seed;
        boolean[] included = new boolean[parts.size()];
        for (int i : current.indices) included[i] = true;
        for (int step = 0; step < parts.size(); step++) {
            int first = current.indices.get(0),
                    last = current.indices.get(current.indices.size() - 1);
            double firstX = x[first], lastX = x[last];
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                Part p = parts.get(i);
                if (included[i]
                        || p.height <= current.median * .65
                        || p.height >= current.median * 1.5
                        || Math.abs(value(current.q, x[i] / w) - y[i]) >= current.median * .22)
                    continue;
                if (x[i] < firstX && neighbors(p, parts.get(first), current.median)
                        || x[i] > lastX && neighbors(parts.get(last), p, current.median))
                    candidates.add(i);
            }
            candidates.sort(
                    Comparator.comparingDouble(
                            i -> Math.min(Math.abs(x[i] - firstX), Math.abs(x[i] - lastX))));
            boolean changed = false;
            for (int i : candidates) {
                List<Integer> trial = new ArrayList<>(current.indices);
                trial.add(i);
                trial.sort(Comparator.comparingDouble(j -> x[j]));
                Fit fitted = fitChain(trial, parts, x, y, w, current.median);
                if (fitted != null
                        && validFit(fitted.q, fitted.x0, fitted.x1, fitted.median, w, h)) {
                    current = fitted;
                    included[i] = true;
                    changed = true;
                    break;
                }
            }
            if (!changed) break;
        }
        return current;
    }

    private static double[] fit(List<Integer> ids, double[] x, double[] y, int w) {
        double[][] a = new double[3][4];
        for (int id : ids) {
            double t = x[id] / w;
            double[] v = {t * t, t, 1};
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) a[r][c] += v[r] * v[c];
                a[r][3] += v[r] * y[id];
            }
        }
        for (int c = 0; c < 3; c++) {
            int pivot = c;
            for (int r = c + 1; r < 3; r++)
                if (Math.abs(a[r][c]) > Math.abs(a[pivot][c])) pivot = r;
            if (Math.abs(a[pivot][c]) < 1e-12) return null;
            double[] tmp = a[c];
            a[c] = a[pivot];
            a[pivot] = tmp;
            double div = a[c][c];
            for (int j = c; j < 4; j++) a[c][j] /= div;
            for (int r = 0; r < 3; r++)
                if (r != c) {
                    double factor = a[r][c];
                    for (int j = c; j < 4; j++) a[r][j] -= factor * a[c][j];
                }
        }
        return new double[] {a[0][3], a[1][3], a[2][3]};
    }

    /** Deterministic weighted Lab clustering of 5-bit RGB bins. Runtime parity is measured separately. */
    private static byte[] palette(int[] pixels) {
        int[] count = new int[32768];
        long[] rr = new long[32768], gg = new long[32768], bb = new long[32768];
        for (int pixel : pixels) {
            int r = (pixel >>> 16) & 255,
                    g = (pixel >>> 8) & 255,
                    b = pixel & 255,
                    key = (r >> 3) * 1024 + (g >> 3) * 32 + (b >> 3);
            count[key]++;
            rr[key] += r;
            gg[key] += g;
            bb[key] += b;
        }
        List<Integer> bins = new ArrayList<>();
        double[][] lab = new double[32768][];
        int first = 0;
        for (int i = 0; i < count.length; i++)
            if (count[i] > 0) {
                bins.add(i);
                lab[i] =
                        lab(
                                rr[i] / (double) count[i],
                                gg[i] / (double) count[i],
                                bb[i] / (double) count[i]);
                if (count[i] > count[first]) first = i;
            }
        double[][] centers = new double[8][3];
        centers[0] = lab[first].clone();
        for (int k = 1; k < 8; k++) {
            double best = -1;
            int chosen = first;
            for (int bin : bins) {
                double dist = Double.POSITIVE_INFINITY;
                for (int j = 0; j < k; j++) dist = Math.min(dist, distance(lab[bin], centers[j]));
                double score = dist * count[bin];
                if (score > best) {
                    best = score;
                    chosen = bin;
                }
            }
            centers[k] = lab[chosen].clone();
        }
        byte[] assigned = new byte[32768];
        for (int iteration = 0; iteration < 30; iteration++) {
            double[][] sums = new double[8][3];
            long[] weights = new long[8];
            int changed = 0;
            for (int bin : bins) {
                int cluster = nearest(lab[bin], centers);
                if (assigned[bin] != cluster) changed++;
                assigned[bin] = (byte) cluster;
                weights[cluster] += count[bin];
                for (int c = 0; c < 3; c++) sums[cluster][c] += lab[bin][c] * count[bin];
            }
            for (int k = 0; k < 8; k++)
                if (weights[k] > 0)
                    for (int c = 0; c < 3; c++) centers[k][c] = sums[k][c] / weights[k];
            if (iteration > 0 && changed == 0) break;
        }
        for (int bin : bins) assigned[bin] = (byte) nearest(lab[bin], centers);
        byte[] labels = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            labels[i] =
                    assigned[
                            (((p >>> 16) & 255) >> 3) * 1024
                                    + (((p >>> 8) & 255) >> 3) * 32
                                    + ((p & 255) >> 3)];
        }
        return labels;
    }

    private static double distance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < 3; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return sum;
    }

    private static int nearest(double[] lab, double[][] centers) {
        int best = 0;
        double score = distance(lab, centers[0]);
        for (int i = 1; i < centers.length; i++) {
            double d = distance(lab, centers[i]);
            if (d < score) {
                score = d;
                best = i;
            }
        }
        return best;
    }

    private static double linear(double c) {
        c /= 255;
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }

    private static double labF(double t) {
        return t > 216. / 24389 ? Math.cbrt(t) : (24389. / 27 * t + 16) / 116;
    }

    private static double[] lab(double r, double g, double b) {
        r = linear(r);
        g = linear(g);
        b = linear(b);
        double x = labF((.4124564 * r + .3575761 * g + .1804375 * b) / .95047),
                y = labF(.2126729 * r + .7151522 * g + .0721750 * b),
                z = labF((.0193339 * r + .1191920 * g + .9503041 * b) / 1.08883);
        return new double[] {(116 * y - 16) * 2.55, 500 * (x - y) + 128, 200 * (y - z) + 128};
    }

    private static List<List<Part>> components(byte[] labels, int w, int h) {
        List<List<Part>> groups = new ArrayList<>();
        for (int k = 0; k < 8; k++) groups.add(new ArrayList<>());
        boolean[] visited = new boolean[labels.length];
        int[] queue = new int[labels.length];
        for (int start = 0; start < labels.length; start++) {
            if (visited[start]) continue;
            byte cluster = labels[start];
            int head = 0, tail = 1;
            queue[0] = start;
            visited[start] = true;
            int x0 = w, x1 = 0, y0 = h, y1 = 0, area = 0;
            while (head < tail) {
                int p = queue[head++], x = p % w, y = p / w;
                area++;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx == 0 && dy == 0)
                                || x + dx < 0
                                || x + dx >= w
                                || y + dy < 0
                                || y + dy >= h) continue;
                        int q = p + dy * w + dx;
                        if (!visited[q] && labels[q] == cluster) {
                            visited[q] = true;
                            queue[tail++] = q;
                        }
                    }
            }
            int cw = x1 - x0 + 1, ch = y1 - y0 + 1;
            if (ch < w * .008
                    || ch > w * .12
                    || cw / (double) ch < .1
                    || cw / (double) ch > 1.8
                    || area < ch * ch * .12
                    || x0 == 0
                    || y0 == 0
                    || x1 == w - 1
                    || y1 == h - 1) continue;
            groups.get(cluster).add(new Part(x0, y0, cw, ch, area));
        }
        return groups;
    }

    private static Row rectify(
            int[] pixels, byte[] labels, int w, int h, int cluster, List<Part> parts, Fit fit) {
        int pad = (int) Math.round(fit.median * .35),
                offset = (int) Math.round(fit.median * .95),
                left = Math.max(0, fit.x0 - pad),
                right = Math.min(w, fit.x1 + pad);
        int columns = right - left;
        if (columns < 2) return null;
        // Padding extends the fitted support; its slope and curvature limits
        // must hold too, independently of the later source-pixel bounds checks.
        if (!validFit(fit.q, left, right - 1, fit.median, w, h)) return null;
        double[] arc = new double[columns];
        for (int i = 1; i < columns; i++) {
            double dy =
                    value(fit.q, (left + i) / (double) w)
                            - value(fit.q, (left + i - 1) / (double) w);
            arc[i] = arc[i - 1] + Math.sqrt(1 + dy * dy);
        }
        int ow = (int) Math.ceil(arc[columns - 1]), oh = offset * 2 + 1;
        if (ow <= 0 || (long) ow * oh > 2_500_000) return null;
        boolean[] selected = new boolean[pixels.length];
        for (Part p : parts)
            for (int y = p.y; y < p.y + p.height; y++)
                for (int x = p.x; x < p.x + p.width; x++)
                    if (labels[y * w + x] == cluster) selected[y * w + x] = true;
        int[] raw = new int[ow * oh], mask = new int[ow * oh];
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
        int segment = 0;
        for (int x = 0; x < ow; x++) {
            while (segment + 1 < columns - 1 && arc[segment + 1] < x) segment++;
            double alpha = (x - arc[segment]) / (arc[segment + 1] - arc[segment]);
            double cx = left + segment + alpha,
                    cy = value(fit.q, cx / w),
                    slope = (2 * fit.q[0] * cx / w + fit.q[1]) / w,
                    norm = Math.sqrt(1 + slope * slope);
            for (int y = 0; y < oh; y++) {
                double d = y - offset, mx = cx - d * slope / norm, my = cy + d / norm;
                if (!Double.isFinite(mx)
                        || !Double.isFinite(my)
                        || mx < 0
                        || mx > w - 1
                        || my < 0
                        || my > h - 1) return null;
                minX = Math.min(minX, mx);
                maxX = Math.max(maxX, mx);
                minY = Math.min(minY, my);
                maxY = Math.max(maxY, my);
                raw[y * ow + x] = bilinear(pixels, w, h, mx, my);
                mask[y * ow + x] = bilinearMask(selected, w, h, mx, my) > 127 ? 255 : 0;
            }
        }
        int[] lo = new int[ow], hi = new int[ow];
        Arrays.fill(lo, Integer.MAX_VALUE);
        Arrays.fill(hi, -1);
        List<Integer> occupied = new ArrayList<>();
        for (int x = 0; x < ow; x++) {
            for (int y = 0; y < oh; y++)
                if (mask[y * ow + x] > 127) {
                    lo[x] = Math.min(lo[x], y);
                    hi[x] = Math.max(hi[x], y);
                }
            if (hi[x] >= 0) occupied.add(x);
        }
        if (occupied.size() < 2) return null;
        int next = 0;
        for (int x = 0; x < ow; x++) {
            while (next + 1 < occupied.size() && occupied.get(next + 1) < x) next++;
            int a = occupied.get(next), b = next + 1 < occupied.size() ? occupied.get(next + 1) : a;
            if (x < a) {
                lo[x] = lo[a];
                hi[x] = hi[a];
            } else if (x > b) {
                lo[x] = lo[b];
                hi[x] = hi[b];
            } else if (a != b) {
                double t = (x - a) / (double) (b - a);
                lo[x] = (int) Math.floor(lo[a] * (1 - t) + lo[b] * t);
                hi[x] = (int) Math.ceil(hi[a] * (1 - t) + hi[b] * t);
            }
        }
        int[] clean = new int[raw.length];
        Arrays.fill(clean, 0xffffffff);
        int radius = (int) Math.round(fit.median * .5),
                padding = Math.max(2, (int) Math.round(fit.median * .1));
        int start = Math.max(0, occupied.get(0) - pad),
                end = Math.min(ow - 1, occupied.get(occupied.size() - 1) + pad);
        for (int x = start; x <= end; x++) {
            int top = oh, bottom = 0;
            for (int j = Math.max(0, x - radius); j <= Math.min(ow - 1, x + radius); j++) {
                top = Math.min(top, lo[j]);
                bottom = Math.max(bottom, hi[j]);
            }
            top -= padding;
            bottom += padding;
            if (top < 0 || bottom >= oh) return null;
            for (int y = top; y <= bottom; y++) clean[y * ow + x] = raw[y * ow + x];
        }
        return new Row(
                cluster,
                List.copyOf(parts),
                fit.q.clone(),
                fit.median,
                fit.residual,
                new double[] {minX, minY, maxX, maxY},
                new Raster(ow, oh, raw),
                new Raster(ow, oh, clean));
    }

    private static int bilinear(int[] pixels, int w, int h, double x, double y) {
        int x0 = (int) x, y0 = (int) y, x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
        double a = x - x0, b = y - y0;
        int out = 0xff000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double c =
                    ((pixels[y0 * w + x0] >>> shift) & 255) * (1 - a) * (1 - b)
                            + ((pixels[y0 * w + x1] >>> shift) & 255) * a * (1 - b)
                            + ((pixels[y1 * w + x0] >>> shift) & 255) * (1 - a) * b
                            + ((pixels[y1 * w + x1] >>> shift) & 255) * a * b;
            out |= ((int) Math.round(c)) << shift;
        }
        return out;
    }

    private static double bilinearMask(boolean[] mask, int w, int h, double x, double y) {
        int x0 = (int) x, y0 = (int) y, x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
        double a = x - x0, b = y - y0;
        return 255
                * ((mask[y0 * w + x0] ? 1 : 0) * (1 - a) * (1 - b)
                        + (mask[y0 * w + x1] ? 1 : 0) * a * (1 - b)
                        + (mask[y1 * w + x0] ? 1 : 0) * (1 - a) * b
                        + (mask[y1 * w + x1] ? 1 : 0) * a * b);
    }
}
