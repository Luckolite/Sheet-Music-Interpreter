// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.math.BigInteger;
import java.util.*;

/** Exact complete pixel-square strip certificate, independent of a selected local angle.
 * This proves only finite raw geometry, never musical ownership or a slur by itself. */
final class FiniteSlurRibbon {
    private static final int MAX_CELLS = 200000, MAX_STATES = 200000, MAX_CLIPS = 2000000;

    record Certificate(
            boolean complete,
            String reason,
            int pixelCount,
            long coveredAreaTwice,
            long maxCutSquared,
            boolean insideStrip,
            boolean noncrossing,
            boolean positiveOrientation,
            boolean limitReached) {}

    private record P(int x, int y) {}

    private record Cap(int edge, P point) {}

    private record T(long n, long d) implements Comparable<T> {
        T {
            if (d < 0) {
                n = -n;
                d = -d;
            }
        }

        public int compareTo(T o) {
            return Long.compare(n * o.d, o.n * d);
        }
    }

    private static Certificate no(String why, int cells) {
        return new Certificate(false, why, cells, 0, 0, false, false, false, false);
    }

    private static Certificate limit(String why, int cells) {
        return new Certificate(false, why, cells, 0, 0, false, false, false, true);
    }

    static long pixel(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    static Certificate certify(Set<Long> cells, int maximum) {
        if (cells == null || cells.isEmpty() || maximum <= 0)
            return no("undefined", cells == null ? 0 : cells.size());
        if (cells.size() > MAX_CELLS || maximum > Integer.MAX_VALUE / 8)
            return limit("resource-or-integer-limit", cells.size());
        var next = new HashMap<P, P>();
        var indegree = new HashMap<P, Integer>();
        int edges = 0;
        int left = Integer.MAX_VALUE,
                right = Integer.MIN_VALUE,
                minY = Integer.MAX_VALUE,
                maxY = Integer.MIN_VALUE;
        var ordered = new ArrayList<Long>(cells);
        Collections.sort(ordered);
        for (long id : ordered) {
            int x = (int) (id >> 32), y = (int) id;
            if (Math.abs((long) x) > Integer.MAX_VALUE / 8
                    || Math.abs((long) y) > Integer.MAX_VALUE / 8)
                return no("integer-limit", cells.size());
            left = Math.min(left, x);
            right = Math.max(right, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            P a = new P(4 * x - 2, 4 * y - 2),
                    b = new P(4 * x + 2, 4 * y - 2),
                    c = new P(4 * x + 2, 4 * y + 2),
                    d = new P(4 * x - 2, 4 * y + 2);
            P[][] proposed = {{a, b}, {b, c}, {c, d}, {d, a}};
            boolean[] exposed = {
                !cells.contains(pixel(x, y - 1)),
                !cells.contains(pixel(x + 1, y)),
                !cells.contains(pixel(x, y + 1)),
                !cells.contains(pixel(x - 1, y))
            };
            for (int i = 0; i < 4; i++)
                if (exposed[i]) {
                    P from = proposed[i][0], to = proposed[i][1];
                    if (next.put(from, to) != null)
                        return no("ambiguous-boundary-vertex", cells.size());
                    if (indegree.merge(to, 1, Integer::sum) != 1)
                        return no("ambiguous-boundary-vertex", cells.size());
                    edges++;
                }
        }
        if (edges == 0 || next.size() != indegree.size()) return no("open-boundary", cells.size());
        P start =
                Collections.min(
                        next.keySet(), Comparator.comparingInt(P::x).thenComparingInt(P::y));
        var loop = new ArrayList<P>();
        var seen = new HashSet<P>();
        P current = start;
        do {
            if (current == null || !seen.add(current))
                return no("non-simple-boundary", cells.size());
            loop.add(current);
            current = next.get(current);
        } while (!start.equals(current));
        if (loop.size() != edges) return no("hole-or-disconnected", cells.size());
        Cap first = cap(loop, cells, left, -2), last = cap(loop, cells, right, 2);
        if (first == null || last == null || first.edge() == last.edge())
            return no("cap-phase-ambiguity", cells.size());
        var upper = sample(chain(loop, first, last));
        var reverse = chain(loop, last, first);
        Collections.reverse(reverse);
        var lower = sample(reverse);
        if (!monotone(upper) || !monotone(lower)) return no("branched-side", cells.size());
        long budget = 16L * maximum * maximum;
        var reached = new HashMap<Long, Long>();
        int considered = 0;
        int jFirst = 0;
        for (int i = 0; i < upper.size(); i++) {
            P u = upper.get(i);
            while (jFirst < lower.size() && lower.get(jFirst).x() < u.x() - 4L * maximum) jFirst++;
            for (int j = jFirst;
                    j < lower.size() && lower.get(j).x() <= u.x() + 4L * maximum;
                    j++) {
                // Count EVERY inspected pair, including unreachable states. Otherwise a
                // jagged boundary could spend unbounded work before a reachable-state cap.
                if (++considered > MAX_STATES) return limit("state-limit", cells.size());
                long key = key(i, j);
                long[] parents = {key(i - 1, j), key(i, j - 1), key(i - 1, j - 1)};
                boolean origin = i == 0 && j == 0, has = origin;
                for (long p : parents) if (reached.containsKey(p)) has = true;
                if (!has) continue;
                P l = lower.get(j);
                if (distance(u, l) > budget || !insideCut(u, l, cells)) continue;
                if (origin) {
                    reached.put(key, Long.MIN_VALUE);
                    continue;
                }
                for (long parent : parents)
                    if (reached.containsKey(parent)) {
                        P a = upper.get((int) (parent >> 32)), b = lower.get((int) parent);
                        if (strictCross(a, b, u, l) || area2(a, u, l, b) < 0) continue;
                        reached.put(key, parent);
                        break;
                    }
            }
        }
        long end = key(upper.size() - 1, lower.size() - 1);
        if (!reached.containsKey(end)) return no("no-complete-width-path", cells.size());
        var path = new ArrayList<Long>();
        long at = end;
        while (at != Long.MIN_VALUE) {
            path.add(at);
            at = reached.get(at);
        }
        Collections.reverse(path);
        // No non-adjacent cut may cross, even if local free-space reachability was valid.
        int comparisons = 0;
        for (int i = 0; i < path.size(); i++)
            for (int j = i + 2; j < path.size(); j++) {
                if (++comparisons > MAX_CLIPS) return limit("crossing-check-limit", cells.size());
                long a = path.get(i), b = path.get(j);
                if (strictCross(
                        upper.get((int) (a >> 32)),
                        lower.get((int) a),
                        upper.get((int) (b >> 32)),
                        lower.get((int) b))) return no("crossing-strip", cells.size());
            }
        var covered = new HashMap<Long, Q>();
        int[] clips = {0};
        long maxCut = 0;
        for (long p : path)
            maxCut = Math.max(maxCut, distance(upper.get((int) (p >> 32)), lower.get((int) p)));
        for (int i = 1; i < path.size(); i++) {
            long a = path.get(i - 1), b = path.get(i);
            P p = upper.get((int) (a >> 32)),
                    q = upper.get((int) (b >> 32)),
                    r = lower.get((int) b),
                    s = lower.get((int) a);
            long ar = area2(p, q, r, s);
            if (ar < 0) return no("negative-orientation", cells.size());
            // A concave strip step uses its valid interior diagonal. Neither diagonal is
            // a transverse-width assertion; both original crossing cuts remain<=maximum.
            P[][] triangles;
            if (cross(p, q, r) >= 0 && cross(p, r, s) >= 0)
                triangles = new P[][] {{p, q, r}, {p, r, s}};
            else if (cross(p, q, s) >= 0 && cross(q, r, s) >= 0)
                triangles = new P[][] {{p, q, s}, {q, r, s}};
            else return no("folded-strip-step", cells.size());
            for (P[] tri : triangles)
                if (!cover(tri, cells, covered, clips))
                    return clips[0] > MAX_CLIPS
                            ? limit("clip-limit", cells.size())
                            : no("unowned-or-overlapping-area", cells.size());
        }
        Q full = Q.of(32);
        for (long id : cells)
            if (!full.equals(covered.get(id))) return no("incomplete-whole-area", cells.size());
        if (covered.size() != cells.size()) return no("foreign-area", cells.size());
        return new Certificate(
                true,
                "complete",
                cells.size(),
                32L * cells.size(),
                maxCut,
                true,
                true,
                true,
                false);
    }

    private static long key(int i, int j) {
        return ((long) i << 32) | (j & 0xffffffffL);
    }

    private static long distance(P a, P b) {
        long x = (long) a.x() - b.x(), y = (long) a.y() - b.y();
        return x * x + y * y;
    }

    private static long cross(P a, P b, P c) {
        return ((long) b.x() - a.x()) * (c.y() - a.y()) - ((long) b.y() - a.y()) * (c.x() - a.x());
    }

    private static long area2(P... p) {
        long sum = 0;
        for (int i = 0; i < p.length; i++) {
            P a = p[i], b = p[(i + 1) % p.length];
            sum += (long) a.x() * b.y() - (long) a.y() * b.x();
        }
        return sum;
    }

    private static boolean strictCross(P a, P b, P c, P d) {
        return opposite(cross(a, b, c), cross(a, b, d)) && opposite(cross(c, d, a), cross(c, d, b));
    }

    private static boolean opposite(long a, long b) {
        return a < 0 && b > 0 || a > 0 && b < 0;
    }

    private static Cap cap(List<P> loop, Set<Long> cells, int x, int direction) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (long id : cells)
            if ((int) (id >> 32) == x) {
                min = Math.min(min, (int) id);
                max = Math.max(max, (int) id);
            }
        long mid = 2L * (min + max), best = Long.MAX_VALUE;
        Cap result = null;
        boolean tie = false;
        for (int i = 0; i < loop.size(); i++) {
            P a = loop.get(i), b = loop.get((i + 1) % loop.size());
            if (a.x() != 4 * x + direction || b.x() != a.x() || Math.abs(a.y() - b.y()) != 4)
                continue;
            P p = new P(a.x(), (a.y() + b.y()) / 2);
            long d = Math.abs(p.y() - mid);
            if (d < best) {
                best = d;
                result = new Cap(i, p);
                tie = false;
            } else if (d == best) tie = true;
        }
        return tie ? null : result;
    }

    private static ArrayList<P> chain(List<P> loop, Cap a, Cap b) {
        var points = new ArrayList<P>();
        points.add(a.point());
        int k = (a.edge() + 1) % loop.size();
        while (true) {
            points.add(loop.get(k));
            if (k == b.edge()) break;
            k = (k + 1) % loop.size();
        }
        points.add(b.point());
        return points;
    }

    private static List<P> sample(List<P> p) {
        var out = new ArrayList<P>();
        for (int i = 1; i < p.size(); i++) {
            P a = p.get(i - 1), b = p.get(i);
            int steps = Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y())) / 2;
            if (steps == 0) continue;
            for (int s = 0; s < steps; s++)
                out.add(
                        new P(
                                a.x() + (b.x() - a.x()) * s / steps,
                                a.y() + (b.y() - a.y()) * s / steps));
        }
        out.add(p.get(p.size() - 1));
        return out;
    }

    private static boolean monotone(List<P> p) {
        for (int i = 1; i < p.size(); i++) if (p.get(i).x() < p.get(i - 1).x()) return false;
        return true;
    }

    private static boolean insideCut(P a, P b, Set<Long> cells) {
        var ts = new TreeSet<T>();
        ts.add(new T(0, 1));
        ts.add(new T(1, 1));
        for (int axis = 0; axis < 2; axis++) {
            int v = axis == 0 ? a.x() : a.y(), w = axis == 0 ? b.x() : b.y();
            if (v == w) continue;
            for (int k = Math.floorDiv(Math.min(v, w), 4) - 1;
                    k <= Math.floorDiv(Math.max(v, w), 4) + 1;
                    k++) {
                T t = new T(4L * k + 2 - v, w - v);
                if (t.compareTo(new T(0, 1)) > 0 && t.compareTo(new T(1, 1)) < 0) ts.add(t);
            }
        }
        T before = null;
        for (T t : ts) {
            if (!insidePoint(a, b, t, cells)) return false;
            if (before != null
                    && !insidePoint(
                            a,
                            b,
                            new T(before.n() * t.d() + t.n() * before.d(), 2 * before.d() * t.d()),
                            cells)) return false;
            before = t;
        }
        return true;
    }

    private static boolean insidePoint(P a, P b, T t, Set<Long> cells) {
        long xn = (long) a.x() * t.d() + ((long) b.x() - a.x()) * t.n(),
                yn = (long) a.y() * t.d() + ((long) b.y() - a.y()) * t.n();
        int cx = (int) Math.floorDiv(xn + 2 * t.d(), 4 * t.d()),
                cy = (int) Math.floorDiv(yn + 2 * t.d(), 4 * t.d());
        for (int x = cx - 1; x <= cx; x++)
            for (int y = cy - 1; y <= cy; y++)
                if (cells.contains(pixel(x, y))
                        && Math.abs(xn - 4L * x * t.d()) <= 2 * t.d()
                        && Math.abs(yn - 4L * y * t.d()) <= 2 * t.d()) return true;
        return false;
    }

    private record Q(BigInteger n, BigInteger d) implements Comparable<Q> {
        Q {
            if (d.signum() < 0) {
                n = n.negate();
                d = d.negate();
            }
            BigInteger g = n.gcd(d);
            n = n.divide(g);
            d = d.divide(g);
        }

        static Q of(long n) {
            return new Q(BigInteger.valueOf(n), BigInteger.ONE);
        }

        Q add(Q v) {
            return new Q(n.multiply(v.d).add(v.n.multiply(d)), d.multiply(v.d));
        }

        Q sub(Q v) {
            return new Q(n.multiply(v.d).subtract(v.n.multiply(d)), d.multiply(v.d));
        }

        Q mul(Q v) {
            return new Q(n.multiply(v.n), d.multiply(v.d));
        }

        Q div(Q v) {
            return new Q(n.multiply(v.d), d.multiply(v.n));
        }

        public int compareTo(Q v) {
            return n.multiply(v.d).compareTo(v.n.multiply(d));
        }
    }

    private record V(Q x, Q y) {}

    private static boolean cover(P[] triangle, Set<Long> cells, Map<Long, Q> covered, int[] clips) {
        if (cross(triangle[0], triangle[1], triangle[2]) == 0) return true;
        if (cross(triangle[0], triangle[1], triangle[2]) < 0) return false;
        int minX = Integer.MAX_VALUE,
                maxX = Integer.MIN_VALUE,
                minY = Integer.MAX_VALUE,
                maxY = Integer.MIN_VALUE;
        var poly = new ArrayList<V>();
        for (P p : triangle) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
            poly.add(new V(Q.of(p.x()), Q.of(p.y())));
        }
        for (int x = Math.floorDiv(minX + 2, 4) - 1; x <= Math.floorDiv(maxX + 2, 4); x++)
            for (int y = Math.floorDiv(minY + 2, 4) - 1; y <= Math.floorDiv(maxY + 2, 4); y++) {
                if (++clips[0] > MAX_CLIPS) return false;
                List<V> p = clip(poly, 0, 4L * x - 2, true);
                p = clip(p, 0, 4L * x + 2, false);
                p = clip(p, 1, 4L * y - 2, true);
                p = clip(p, 1, 4L * y + 2, false);
                Q area = Q.of(0);
                for (int i = 0; i < p.size(); i++) {
                    V a = p.get(i), b = p.get((i + 1) % p.size());
                    area = area.add(a.x().mul(b.y()).sub(a.y().mul(b.x())));
                }
                if (area.compareTo(Q.of(0)) < 0) return false;
                if (area.compareTo(Q.of(0)) == 0) continue;
                long id = pixel(x, y);
                if (!cells.contains(id)) return false;
                Q sum = covered.getOrDefault(id, Q.of(0)).add(area);
                if (sum.compareTo(Q.of(32)) > 0) return false;
                covered.put(id, sum);
            }
        return true;
    }

    private static List<V> clip(List<V> p, int axis, long boundary, boolean lower) {
        if (p.isEmpty()) return p;
        var result = new ArrayList<V>();
        Q k = Q.of(boundary);
        V a = p.get(p.size() - 1);
        for (V b : p) {
            Q av = axis == 0 ? a.x() : a.y(), bv = axis == 0 ? b.x() : b.y();
            boolean ain = lower ? av.compareTo(k) >= 0 : av.compareTo(k) <= 0,
                    bin = lower ? bv.compareTo(k) >= 0 : bv.compareTo(k) <= 0;
            if (ain != bin) {
                Q t = k.sub(av).div(bv.sub(av));
                result.add(
                        new V(
                                a.x().add(b.x().sub(a.x()).mul(t)),
                                a.y().add(b.y().sub(a.y()).mul(t))));
            }
            if (bin) result.add(b);
            a = b;
        }
        return result;
    }
}
