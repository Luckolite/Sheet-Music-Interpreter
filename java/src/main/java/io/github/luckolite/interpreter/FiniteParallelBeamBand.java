// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.math.BigInteger;

/** Exact feasible parallel edges; observations retain the established one-pixel bound. */
final class FiniteParallelBeamBand {
    private record Q(BigInteger n, BigInteger d) implements Comparable<Q> {
        Q {
            if (d.signum() <= 0) throw new IllegalArgumentException();
            BigInteger k = n.gcd(d);
            n = n.divide(k);
            d = d.divide(k);
        }

        static Q of(long n) {
            return new Q(BigInteger.valueOf(n), BigInteger.ONE);
        }

        static Q ratio(long n, long d) {
            return new Q(BigInteger.valueOf(n), BigInteger.valueOf(d));
        }

        Q add(Q q) {
            return new Q(n.multiply(q.d).add(q.n.multiply(d)), d.multiply(q.d));
        }

        Q sub(Q q) {
            return new Q(n.multiply(q.d).subtract(q.n.multiply(d)), d.multiply(q.d));
        }

        Q times(long k) {
            return new Q(n.multiply(BigInteger.valueOf(k)), d);
        }

        public int compareTo(Q q) {
            return n.multiply(q.d).compareTo(q.n.multiply(d));
        }

        int floor() {
            BigInteger[] z = n.divideAndRemainder(d);
            BigInteger value =
                    z[0].subtract(
                            n.signum() < 0 && z[1].signum() != 0
                                    ? BigInteger.ONE
                                    : BigInteger.ZERO);
            // BigInteger.intValueExact requires Android 31; preserve its signed range check.
            if (value.bitLength() > 31)
                throw new ArithmeticException("BigInteger out of int range");
            return value.intValue();
        }
    }

    private final int[] x, lo, hi;
    private final Q lowSlope, highSlope;

    private FiniteParallelBeamBand(int[] x, int[] lo, int[] hi, Q sl, Q sh) {
        this.x = x;
        this.lo = lo;
        this.hi = hi;
        this.lowSlope = sl;
        this.highSlope = sh;
    }

    static FiniteParallelBeamBand prove(int[] top, int[] bottom, int thickness) {
        if (top == null
                || bottom == null
                || top.length != bottom.length
                || top.length < 2
                || thickness < 1
                || top.length > 512) return null; // At most 130816 exact pair constraints.
        int n = top.length;
        int[] x = new int[n];
        for (int i = 0; i < n; i++) x[i] = i;
        return proveAtX(x, top, bottom, thickness);
    }

    /** Same exact edge proof for sparse observations at their original x coordinates. */
    static FiniteParallelBeamBand proveAtX(int[] x, int[] top, int[] bottom, int thickness) {
        if (x == null || top == null || bottom == null || x.length != top.length
                || top.length != bottom.length || top.length < 2 || thickness < 1
                || top.length > 512) return null;
        int n = top.length;
        int[] l = new int[n], u = new int[n];
        Q sl = null, sh = null;
        for (int i = 0; i < n; i++) {
            if (x[i] < 0 || i > 0 && x[i] <= x[i - 1]
                    || top[i] < 0 || bottom[i] < top[i]) return null;
            long a = Math.max((long) bottom[i] - 1, (long) top[i] + thickness - 2);
            long b = Math.min((long) bottom[i] + 1, (long) top[i] + thickness);
            if (a > b || a < Integer.MIN_VALUE || b > Integer.MAX_VALUE) return null;
            l[i] = (int) a;
            u[i] = (int) b;
        }
        return proveBottomIntervals(x, l, u);
    }

    static FiniteParallelBeamBand proveBottomIntervals(int[] x, int[] l, int[] u) {
        if (x == null || l == null || u == null || x.length < 2 || x.length > 512
                || x.length != l.length || x.length != u.length) return null;
        int n = x.length; Q sl = null, sh = null;
        for (int i = 0; i < n; i++)
            if (x[i] < 0 || i > 0 && x[i] <= x[i-1] || l[i] > u[i]) return null;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                long dx = (long) x[j] - x[i];
                Q a = Q.ratio((long) l[j] - u[i], dx), b = Q.ratio((long) u[j] - l[i], dx);
                if (sl == null || a.compareTo(sl) > 0) sl = a;
                if (sh == null || b.compareTo(sh) < 0) sh = b;
            }
        return sl.compareTo(sh) > 0 ? null : new FiniteParallelBeamBand(x.clone(), l.clone(), u.clone(), sl, sh);
    }

    String slopeInterval() { return lowSlope.n + "/" + lowSlope.d + ".." + highSlope.n + "/" + highSlope.d; }

    private record Bounds(Q low, Q high) {}
    private Bounds valuesAt(int sample) {
        Q minimum=null, maximum=null;
        // Every vertex of the bounded (slope, intercept) polygon is an intersection
        // of two observed edge constraints. Check every vertex, never a best fit.
        for(int i=0;i<x.length;i++) for(int j=i+1;j<x.length;j++)
            for(int ai=0;ai<2;ai++) for(int aj=0;aj<2;aj++) {
                int vi=ai==0?lo[i]:hi[i], vj=aj==0?lo[j]:hi[j];
                Q slope=Q.ratio((long)vj-vi,(long)x[j]-x[i]);
                if(slope.compareTo(lowSlope)<0 || slope.compareTo(highSlope)>0) continue;
                Q c=Q.of(vi).sub(slope.times(x[i]));
                boolean feasible=true;
                for(int k=0;k<x.length;k++) {
                    Q v=c.add(slope.times(x[k]));
                    if(v.compareTo(Q.of(lo[k]))<0 || v.compareTo(Q.of(hi[k]))>0){feasible=false;break;}
                }
                if(!feasible)continue;
                Q v=c.add(slope.times(sample));
                if(minimum==null || v.compareTo(minimum)<0)minimum=v;
                if(maximum==null || v.compareTo(maximum)>0)maximum=v;
            }
        return minimum==null?null:new Bounds(minimum,maximum);
    }
    static boolean universallyCoherentFiniteFamily(java.util.List<FiniteParallelBeamBand> bands,
            int[] thickness, int left, int right, int gap) {
        if(bands==null || thickness==null || bands.size()!=thickness.length)return false;
        java.util.List<java.util.List<FiniteParallelBeamBand>> alternatives=new java.util.ArrayList<>();
        java.util.List<java.util.List<Integer>> widths=new java.util.ArrayList<>();
        for(int i=0;i<bands.size();i++){alternatives.add(java.util.List.of(bands.get(i)));widths.add(java.util.List.of(thickness[i]));}
        return universallyCoherentFiniteAlternatives(alternatives,widths,left,right,gap);
    }
    static boolean universallyCoherentFiniteAlternatives(java.util.List<java.util.List<FiniteParallelBeamBand>> bodies,
            java.util.List<java.util.List<Integer>> widths,int left,int right,int gap) {
        if(bodies==null || bodies.size()<2 || bodies.size()>3 || widths==null || widths.size()!=bodies.size()
                || left<0 || right<=left || gap<5 || gap>64)return false;
        int sign=0;
        for(int i=0;i<bodies.size();i++) {
            var alternatives=bodies.get(i);if(alternatives==null || alternatives.isEmpty() || widths.get(i)==null
                || alternatives.size()!=widths.get(i).size())return false;
            for(var band:alternatives) {
                if(band==null)return false;
                int direction=band.lowSlope.compareTo(Q.of(0))>0?1:band.highSlope.compareTo(Q.of(0))<0?-1:0;
                if(direction==0 || sign!=0 && sign!=direction)return false;sign=direction;
            }
        }
        java.util.List<Integer> order=null;
        for(int sample:new int[]{left,right}) {
            java.util.List<Bounds> centers=new java.util.ArrayList<>();
            for(int i=0;i<bodies.size();i++) {
                Q low=null,high=null;
                for(int j=0;j<bodies.get(i).size();j++) {
                    int thickness=widths.get(i).get(j);if(thickness<3||thickness>64)return false;
                    Bounds b=bodies.get(i).get(j).valuesAt(sample);if(b==null)return false;
                    Q half=Q.ratio(thickness-1,2),a=b.low.sub(half),z=b.high.sub(half);
                    if(low==null || a.compareTo(low)<0)low=a;if(high==null||z.compareTo(high)>0)high=z;
                }
                centers.add(new Bounds(low,high));
            }
            if(order==null) {
                order=new java.util.ArrayList<>();for(int i=0;i<centers.size();i++)order.add(i);
                final java.util.List<Bounds> observed=centers;
                order.sort((a,b)->observed.get(a).low.compareTo(observed.get(b).low));
            }
            java.util.List<Bounds> ordered=new java.util.ArrayList<>();for(int i:order)ordered.add(centers.get(i));centers=ordered;
            for(int i=1;i<centers.size();i++) {
                Bounds a=centers.get(i-1),b=centers.get(i);
                if(b.low.sub(a.high).compareTo(Q.ratio(gap,2))<0
                    || b.high.sub(a.low).compareTo(Q.ratio(11L*gap,10))>0)return false;
            }
            if(centers.size()==3) {
                Bounds a=centers.get(0),b=centers.get(1),c=centers.get(2);
                Q low=a.low.add(c.low).sub(b.high.times(2)), high=a.high.add(c.high).sub(b.low.times(2));
                if(low.compareTo(Q.ratio(-gap,5))<0 || high.compareTo(Q.ratio(gap,5))>0)return false;
            }
        }
        return true;
    }

    private Q intercept(Q s, boolean minimum) {
        Q v = null;
        for (int i = 0; i < lo.length; i++) {
            Q a = Q.of(minimum ? lo[i] : hi[i]).sub(s.times(x[i]));
            if (v == null || (minimum ? a.compareTo(v) > 0 : a.compareTo(v) < 0)) v = a;
        }
        return v;
    }

    // Only exterior x is used: every (x-i) has one sign, so extrema occur at slope endpoints.
    private Q edge(int x, boolean minimum) {
        if (x >= this.x[0] && x <= this.x[this.x.length - 1]) throw new IllegalArgumentException("exterior only");
        Q s = (x < this.x[0]) == minimum ? highSlope : lowSlope;
        return intercept(s, minimum).add(s.times(x));
    }

    boolean supportsEnd(int left, int right, int end) {
        if (left > right || left >= this.x[0] || right <= this.x[this.x.length - 1]) return false;
        boolean positive = lowSlope.compareTo(Q.of(0)) >= 0;
        boolean negative = highSlope.compareTo(Q.of(0)) <= 0;
        if (!positive && !negative) return false; // No favorable slope selection.
        int minX = positive ? left : right, maxX = positive ? right : left;
        return Q.of(end).compareTo(edge(minX, false).sub(Q.of(1))) >= 0
                && Q.of(end).compareTo(edge(maxX, true).add(Q.of(1))) <= 0;
    }

    boolean parallelTo(FiniteParallelBeamBand other) {
        return lowSlope.compareTo(other.highSlope) <= 0 && other.lowSlope.compareTo(highSlope) <= 0;
    }

    int terminalFloor(int exteriorX) {
        // Universal contact floor: include the largest feasible exterior edge, as supportsEnd does.
        return edge(exteriorX, false).sub(Q.of(1)).floor();
    }

    int terminalCeiling(int exteriorX) {
        return edge(exteriorX, true).add(Q.of(1)).floor();
    }
}
