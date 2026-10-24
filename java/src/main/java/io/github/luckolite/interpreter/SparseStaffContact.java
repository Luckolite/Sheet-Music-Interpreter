// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.security.*;

/** PRIVATE conditional contact prototype. Neither a head recognizer nor a pixel eraser. */
final class SparseStaffContact {
    static final int INK = 185;

    static final class Unknown extends RuntimeException {
        Unknown(String s) {
            super(s);
        }
    }

    static final class Budget {
        long work, bytes;

        Budget(long w, long b) {
            work = w;
            bytes = b;
        }

        void work(long n) {
            if (n < 0 || n > work) throw new Unknown("WORK");
            work -= n;
        }

        void memory(long n) {
            if (n < 0 || n > bytes) throw new Unknown("STORAGE");
            bytes -= n;
        }
    }

    record Rect(int left, int top, int right, int bottom) {
        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }
    }

    record StaffIdentity(int measure, int staff, double top, double gap) {
        StaffIdentity {
            if (measure < 0
                    || staff < 0
                    || !Double.isFinite(top)
                    || !Double.isFinite(gap)
                    || gap < 5) throw new Unknown("STAFF");
        }
    }

    static final class Snapshot {
        final String rawSha;
        final int width, height;
        final Rect scope;
        private final byte[] local;

        private Snapshot(String s, int w, int h, Rect r, byte[] b) {
            rawSha = s;
            width = w;
            height = h;
            scope = r;
            local = b;
        }

        /** Fullpage authentication is separate, prepaid ONCE; local copy/storage alone uses lane budget. */
        static Snapshot capture(
                byte[] raw, int w, int h, String expectedSha, Rect r, Budget auth, Budget lane) {
            if (w < 1
                    || h < 1
                    || (long) w * h != raw.length
                    || r.left < 0
                    || r.top < 0
                    || r.right >= w
                    || r.bottom >= h
                    || r.width() < 1
                    || r.height() < 1) throw new Unknown("DOMAIN");
            auth.work(raw.length);
            auth.memory(4096);
            String sha;
            try {
                sha = RasterEvidenceIdentity.hex(MessageDigest.getInstance("SHA-256").digest(raw));
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError(e);
            }
            if (!sha.equals(expectedSha)) throw new Unknown("RAW_IDENTITY");
            long n = (long) r.width() * r.height();
            if (n > Integer.MAX_VALUE) throw new Unknown("DOMAIN");
            lane.memory(n + 256);
            lane.work(n);
            byte[] b = new byte[(int) n];
            for (int y = r.top; y <= r.bottom; y++)
                System.arraycopy(raw, y * w + r.left, b, (y - r.top) * r.width(), r.width());
            return new Snapshot(sha, w, h, r, b);
        }

        /** Reuse the exact sealed full-plane identity already authenticated by the caller's collector. */
        static Snapshot captureAuthenticated(
                byte[] raw,
                int w,
                int h,
                ExactComponentMembers.AcceptedProbeHead token,
                Rect r,
                Budget lane) {
            if (token == null
                    || !token.authenticates(raw, w, h)
                    || w < 1
                    || h < 1
                    || (long) w * h != raw.length
                    || r.left < 0
                    || r.top < 0
                    || r.right >= w
                    || r.bottom >= h
                    || r.width() < 1
                    || r.height() < 1) throw new Unknown("RAW_IDENTITY");
            long n = (long) r.width() * r.height();
            if (n > Integer.MAX_VALUE) throw new Unknown("DOMAIN");
            lane.memory(n + 256);
            lane.work(n);
            byte[] b = new byte[(int) n];
            for (int y = r.top; y <= r.bottom; y++)
                System.arraycopy(raw, y * w + r.left, b, (y - r.top) * r.width(), r.width());
            return new Snapshot(token.rawSha(), w, h, r, b);
        }

        int index(int x, int y) {
            if (x < scope.left || x > scope.right || y < scope.top || y > scope.bottom)
                throw new Unknown("OUTSIDE_SCOPE");
            return (y - scope.top) * scope.width() + x - scope.left;
        }

        boolean dark(int x, int y) {
            return (local[index(x, y)] & 255) < INK;
        }

        int observedGray(int x, int y) {
            return local[index(x, y)] & 255;
        }
    }

    /** Proposed exact body+shaft footprint, NOT musical head truth. No authority to suppress any pixel. */
    static final class ProposedOwner {
        private final BitSet sites;
        final Rect bounds;
        final Snapshot plane;

        private ProposedOwner(Snapshot p, BitSet s, Rect b) {
            plane = p;
            sites = s;
            bounds = b;
        }

        static ProposedOwner fromExactPixels(Snapshot p, int[] fullPagePixels, Budget b) {
            long n = p.local.length;
            b.memory((n + 7) / 8 + 256);
            b.work(n + 3L * fullPagePixels.length);
            BitSet s = new BitSet((int) n);
            int l = p.width, t = p.height, r = -1, bt = -1;
            for (int z : fullPagePixels) {
                if (z < 0 || z >= (long) p.width * p.height) throw new Unknown("OWNER_PIXEL");
                int x = z % p.width, y = z / p.width, i = p.index(x, y);
                if (s.get(i) || !p.dark(x, y)) throw new Unknown("OWNER_PIXEL");
                s.set(i);
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                bt = Math.max(bt, y);
            }
            if (s.isEmpty()) throw new Unknown("OWNER_EMPTY");
            return new ProposedOwner(p, s, new Rect(l, t, r, bt));
        }
    }

    static final class RuleEvidence {
        final String rawSha;
        final StaffIdentity staff;
        final Rect scope;
        private final BitSet observed;
        private final Snapshot plane;

        private RuleEvidence(Snapshot p, StaffIdentity s, BitSet r) {
            plane = p;
            rawSha = p.rawSha;
            scope = p.scope;
            staff = s;
            observed = r;
        }

        /** Exact observed contacts only; caller prepays each consumption. Never a phase/y-band mask. */
        boolean ownsObservedContact(Snapshot p, int x, int y, Budget b) {
            b.work(1);
            if (plane != p || !rawSha.equals(p.rawSha) || !scope.equals(p.scope))
                throw new Unknown("EVIDENCE_IDENTITY");
            return observed.get(p.index(x, y));
        }
    }

    /** Caller provides genuine measured per-column tracks, never a favorable inferred replacement.
     * All five complete rails must be observed symmetrically >=2g outside the owner bbox.
     * This restricted prototype requires a constant 1..3px vertical thickness/offset per rail;
     * any additional dark branch anywhere in the checked scope refuses, including side guards.
     * Horizontal scope exits are permitted ONLY on these exactly observed complete rails.
     */
    static RuleEvidence certify(
            Snapshot p,
            StaffIdentity staff,
            int[][] measuredCenters,
            ProposedOwner owner,
            Budget b) {
        if (owner.plane != p) throw new Unknown("OWNER_IDENTITY");
        int w = p.scope.width(), h = p.scope.height();
        long n = (long) w * h;
        b.memory(2 * ((n + 7) / 8) + 1024);
        b.work(4 * n + 20L * w + 32);
        if (measuredCenters.length != 5) throw new Unknown("FIVE_TRACKS");
        if (owner.bounds.left - p.scope.left < Math.ceil(2 * staff.gap)
                || p.scope.right - owner.bounds.right < Math.ceil(2 * staff.gap))
            throw new Unknown("SYMMETRIC_WITNESS");
        BitSet rails = new BitSet((int) n);
        int previous = -1;
        for (int r = 0; r < 5; r++) {
            if (measuredCenters[r].length != w) throw new Unknown("TRACK_DOMAIN");
            int first = measuredCenters[r][0];
            if (Math.abs(first - (staff.top + r * staff.gap)) > 1)
                throw new Unknown("STAFF_REGISTRATION");
            int a = first, bt = first;
            if (!p.dark(p.scope.left, first)) throw new Unknown("MISSING_RULE");
            while (a > p.scope.top && p.dark(p.scope.left, a - 1)) a--;
            while (bt < p.scope.bottom && p.dark(p.scope.left, bt + 1)) bt++;
            if (a <= p.scope.top || bt >= p.scope.bottom || bt - a + 1 > 3 || a <= previous)
                throw new Unknown("THICK_OR_MERGED_RULE");
            int lo = a - first, hi = bt - first;
            for (int ix = 0; ix < w; ix++) {
                int x = p.scope.left + ix, c = measuredCenters[r][ix];
                if (Math.abs(c - (staff.top + r * staff.gap)) > 1
                        || (ix > 0 && Math.abs(c - measuredCenters[r][ix - 1]) > 1))
                    throw new Unknown("TRACK_GEOMETRY");
                for (int dy = lo; dy <= hi; dy++) {
                    int y = c + dy;
                    if (y <= p.scope.top || y >= p.scope.bottom || !p.dark(x, y))
                        throw new Unknown("INCOMPLETE_RULE");
                    rails.set(p.index(x, y));
                }
                // Full raw side profiles outside owned body/shaft: no thickness fiction or hidden
                // branches.
                for (int y = c + lo - 1; y <= c + hi + 1; y++)
                    if (p.dark(x, y)
                            && !rails.get(p.index(x, y))
                            && !owner.sites.get(p.index(x, y))) throw new Unknown("RULE_BRANCH");
            }
            previous = bt;
        }
        // Complete 2D raw accounting, not just witness samples. Owner is not modified or erased.
        for (int iy = 0; iy < h; iy++)
            for (int ix = 0; ix < w; ix++) {
                int x = p.scope.left + ix, y = p.scope.top + iy, i = iy * w + ix;
                if (p.dark(x, y) && !rails.get(i) && !owner.sites.get(i))
                    throw new Unknown("UNASSIGNED_RAW_BRANCH");
                if ((iy == 0 || iy == h - 1) && p.dark(x, y))
                    throw new Unknown("VERTICAL_SCOPE_EXIT");
                if ((ix == 0 || ix == w - 1) && p.dark(x, y) && !rails.get(i))
                    throw new Unknown("FOREIGN_SCOPE_EXIT");
            }
        BitSet contacts = (BitSet) rails.clone();
        contacts.and(owner.sites);
        if (contacts.isEmpty()) throw new Unknown("NO_RULE_CONTACT");
        return new RuleEvidence(p, staff, rails);
    }
}
