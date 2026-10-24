// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete finite raster ports between uniquely observed attacks; legacy routes are independent. */
final class FiniteConnectedSlidePorts {
    // Work units count loop visits, metadata snapshots and conservative called-helper envelopes.
    // Allocation units count requested bytes cumulatively, including temporary arrays/copies.
    static final long DEFAULT_WORK = 256_000_000L;
    static final long DEFAULT_BYTES = 128L * 1024 * 1024;

    static final class Budget {
        private long work, bytes, usedWork, usedBytes;
        private boolean refused;
        private int certified;
        private long pairAttempts, firstPairWork = -1, firstPairBytes = -1;
        private String refusalReason = "";

        Budget(long work, long bytes) {
            this.work = Math.min(DEFAULT_WORK, Math.max(0, work));
            this.bytes = Math.min(DEFAULT_BYTES, Math.max(0, bytes));
        }

        boolean refused() {
            return refused;
        }

        long usedWork() {
            return usedWork;
        }

        long usedBytes() {
            return usedBytes;
        }

        long remainingWork() {
            return work;
        }

        long remainingBytes() {
            return bytes;
        }

        int certified() {
            return certified;
        }

        long pairAttempts() {
            return pairAttempts;
        }

        long firstPairWork() {
            return firstPairWork;
        }

        long firstPairBytes() {
            return firstPairBytes;
        }

        String refusalReason() {
            return refusalReason;
        }

        private void take(long n) {
            if (refused || n < 0 || n > work) fail("work");
            work -= n;
            usedWork += n;
        }

        private void reserve(long n) {
            if (refused || n < 0 || n > bytes) fail("allocation");
            bytes -= n;
            usedBytes += n;
        }

        private void fail() {
            fail("invalid geometry");
        }

        private void fail(String reason) {
            if (!refused) refusalReason = reason;
            refused = true;
            throw Refused.INSTANCE;
        }
    }

    private static final class Refused extends RuntimeException {
        static final Refused INSTANCE = new Refused();

        private Refused() {
            super(null, null, false, false);
        }
    }

    private record Piece(int[] px, int l, int r, int t, int b) {}

    private record View(byte[] clean, List<Piece> pieces, FiniteBarlineSlideInk.Body barline) {
        View(byte[] clean, List<Piece> pieces) {
            this(clean, pieces, null);
        }
    }

    private record Indexed(NoteSlideDetector.Head head, int index) {}

    private record Owners(NoteSlideDetector.Head a, NoteSlideDetector.Head b, int target) {}

    private FiniteConnectedSlidePorts() {}

    static List<NoteSlideDetector.Stroke> add(
            List<NoteSlideDetector.Stroke> old,
            byte[] gray,
            int w,
            int h,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            byte[] first,
            byte[] second,
            StaffPitchTrack[] tracks) {
        return add(
                old,
                gray,
                w,
                h,
                staffs,
                heads,
                first,
                second,
                tracks,
                new Budget(DEFAULT_WORK, DEFAULT_BYTES));
    }

    static List<NoteSlideDetector.Stroke> add(
            List<NoteSlideDetector.Stroke> old,
            byte[] gray,
            int w,
            int h,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            byte[] first,
            byte[] second,
            StaffPitchTrack[] tracks,
            Budget budget) {
        List<NoteSlideDetector.Stroke> extra =
                find(gray, w, h, staffs, heads, first, second, tracks, budget);
        if (budget == null || budget.refused()) return old;
        try {
            budget.reserve(64L + 24L * (old.size() + (long) extra.size()));
            var result = new ArrayList<>(old);
            for (var stroke : extra) {
                budget.take(1);
                boolean represented = false;
                for (int k = 0; k < result.size(); k++) {
                    budget.take(1);
                    if (result.get(k).noteIndex() == stroke.noteIndex()) represented = true;
                }
                if (!represented) result.add(stroke);
            }
            budget.reserve(48L + 16L * result.size());
            return List.copyOf(result);
        } catch (Refused failure) {
            return old;
        }
    }

    static List<NoteSlideDetector.Stroke> find(
            byte[] gray,
            int w,
            int h,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            byte[] first,
            byte[] second,
            StaffPitchTrack[] supplied) {
        return find(
                gray,
                w,
                h,
                staffs,
                heads,
                first,
                second,
                supplied,
                new Budget(DEFAULT_WORK, DEFAULT_BYTES));
    }

    static List<NoteSlideDetector.Stroke> find(
            byte[] gray,
            int w,
            int h,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> heads,
            byte[] first,
            byte[] second,
            StaffPitchTrack[] supplied,
            Budget budget) {
        if (budget == null) return List.of();
        try {
            return findBounded(gray, w, h, staffs, heads, first, second, supplied, budget);
        } catch (Refused failure) {
            return List.of();
        }
    }

    private static List<NoteSlideDetector.Stroke> findBounded(
            byte[] gray,
            int w,
            int h,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> input,
            byte[] first,
            byte[] second,
            StaffPitchTrack[] supplied,
            Budget budget) {
        budget.take(1);
        if (gray == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || first == null
                || first.length != gray.length
                || input == null
                || staffs == null
                || input.size() < 2
                || staffs.isEmpty()) return List.of();
        int n = input.size(), staffCount = staffs.size();
        budget.reserve(256L + n * 320L + staffCount * 64L);
        var heads = new NoteSlideDetector.Head[n];
        var bodies = new boolean[n];
        Map<Long, List<Indexed>> groups = new HashMap<>();
        float maxGap = 0;
        // Snapshot all records before any source search. No count cap or rival suppression.
        var snapshot = input.iterator();
        for (int i = 0; i < n; i++) {
            budget.take(1);
            if (!snapshot.hasNext()) budget.fail();
            var q = snapshot.next();
            heads[i] = q;
            if (q == null
                    || !Float.isFinite(q.x() + q.y() + q.gap())
                    || q.gap() < 6
                    || q.gap() > 64) budget.fail();
            maxGap = Math.max(maxGap, q.gap());
            budget.reserve(64);
            long key = ((long) q.staff() << 32) | (q.measure() & 0xffffffffL);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Indexed(q, i));
        }
        budget.take(1);
        if (snapshot.hasNext()) budget.fail();
        for (var list : groups.values()) {
            budget.take(1);
            budget.reserve(1024L + list.size() * 16L);
            list.sort(
                    (a, b) -> {
                        budget.take(1);
                        return Float.compare(a.head().x(), b.head().x());
                    });
        }
        for (int i = 0; i < n; i++) {
            budget.take(1);
            bodies[i] = body(gray, w, h, heads[i], budget);
        }
        var owners = new ArrayList<Owners>();
        for (int i = 0; i < n; i++) {
            budget.take(1);
            var target = heads[i];
            float gap = target.gap();
            if (!bodies[i] || target.staff() < 0 || target.staff() >= staffCount) continue;
            var list = groups.get(((long) target.staff() << 32) | (target.measure() & 0xffffffffL));
            float threshold = gap * .35f;
            int before = lower(list, target.x() - threshold, budget) - 1;
            if (before < 0) continue;
            float latest = list.get(before).head().x();
            int sourceFirst = lower(list, Math.nextDown(latest - threshold), budget);
            int targetFirst = lower(list, Math.nextDown(target.x() - threshold), budget);
            int targetLast = upper(list, Math.nextUp(target.x() + threshold), budget);
            // Binary windows only bound work; retain v1's exact float predicates inside them.
            int source = -1, sourceRivals = 0, targetRivals = 0;
            for (int j = sourceFirst; j <= before; j++) {
                budget.take(1);
                var q = list.get(j);
                if (latest - q.head().x() <= threshold) {
                    source = q.index();
                    sourceRivals++;
                }
            }
            for (int j = targetFirst; j < targetLast; j++) {
                budget.take(1);
                if (Math.abs(list.get(j).head().x() - target.x()) <= threshold) targetRivals++;
            }
            if (sourceRivals != 1 || targetRivals != 1) continue;
            if (!bodies[source]) continue;
            var a = heads[source];
            float span = target.x() - a.x();
            if (span < gap * 2
                    || span > gap * 12
                    || Math.abs(target.y() - a.y()) < gap * .35f
                    || Math.abs(target.y() - a.y()) > gap * 4) continue;
            budget.reserve(64);
            owners.add(new Owners(a, target, i));
        }
        if (owners.isEmpty()) return List.of();
        budget.reserve(64L + 9L * staffCount);
        var tracks = new StaffPitchTrack[staffCount];
        var prepared = new boolean[staffCount];
        for (var owner : owners) {
            budget.take(1);
            int index = owner.b().staff();
            if (prepared[index]) continue;
            prepared[index] = true;
            if (supplied != null && index < supplied.length && supplied[index] != null) {
                tracks[index] = supplied[index];
                continue;
            }
            budget.take(1);
            var staff = staffs.get(index);
            if (staff == null
                    || !Float.isFinite(staff.top() + staff.bottom() + staff.gap())
                    || staff.gap() < 3
                    || staff.gap() > 64
                    || staff.bottom() <= staff.top()) budget.fail();
            // Missing caller proof is rebuilt only by this helper's budgeted flat-rule scan.
            // General curved tracking stays owned by the existing independently accepted route.
            tracks[index] = flatTrack(gray, w, h, staff, budget);
            if (tracks[index] == null) return List.of();
        }
        budget.reserve(192);
        var views = new ArrayList<View>();
        for (byte[] clean : new byte[][] {first, second}) {
            budget.take(1);
            if (clean != null && clean.length == gray.length)
                views.add(new View(clean, pieces(clean, w, h, maxGap, budget)));
        }
        var result = new ArrayList<NoteSlideDetector.Stroke>();
        for (var view : views) {
            budget.take(1);
            byte[] clean = view.clean();
            var pieces = view.pieces();
            for (var owner : owners) {
                budget.take(1);
                boolean represented = false;
                for (int k = 0; k < result.size(); k++) {
                    budget.take(1);
                    if (result.get(k).noteIndex() == owner.target()) represented = true;
                }
                if (represented) continue;
                float gap = owner.b().gap();
                budget.reserve(128L + pieces.size() * 64L);
                var nearby = new ArrayList<Piece>();
                for (var p : pieces) {
                    budget.take(1);
                    if (p.l >= owner.a().x() + gap * .45f
                            && p.r <= owner.b().x() - gap * .45f
                            && p.t >= Math.min(owner.a().y(), owner.b().y()) - gap
                            && p.b <= Math.max(owner.a().y(), owner.b().y()) + gap) nearby.add(p);
                }
                long proofCount = 0;
                for (var otherView : views) {
                    budget.take(1);
                    proofCount += otherView.pieces().size();
                }
                budget.reserve(128L + proofCount * 64L);
                var proofPieces = new ArrayList<Piece>();
                for (var otherView : views)
                    for (var p : otherView.pieces()) {
                        budget.take(1);
                        if (p.l >= owner.a().x() + gap * .45f
                                && p.r <= owner.b().x() - gap * .45f
                                && p.t >= Math.min(owner.a().y(), owner.b().y()) - gap
                                && p.b <= Math.max(owner.a().y(), owner.b().y()) + gap)
                            proofPieces.add(p);
                    }
                NoteSlideDetector.Stroke found = null;
                for (var p : nearby) {
                    budget.take(1);
                    found =
                            prove(
                                    gray,
                                    clean,
                                    w,
                                    h,
                                    heads,
                                    owner,
                                    p,
                                    tracks,
                                    proofPieces,
                                    views,
                                    false,
                                    budget);
                    if (found != null) break;
                }
                if (found == null) {
                    budget.reserve(1024L + nearby.size() * 8L);
                    nearby.sort(
                            (a, b) -> {
                                budget.take(1);
                                return Integer.compare(a.l, b.l);
                            });
                    for (int a = 0; a < nearby.size() && found == null; a++)
                        for (int b = a + 1; b < nearby.size() && found == null; b++) {
                            if (budget.firstPairWork < 0) budget.firstPairWork = budget.usedWork;
                            budget.take(1);
                            budget.pairAttempts++;
                            var one = nearby.get(a);
                            var two = nearby.get(b);
                            if (two.l <= one.r || two.l - one.r > gap * 2) continue;
                            int length = Math.addExact(one.px.length, two.px.length);
                            budget.take(length);
                            if (budget.firstPairBytes < 0) budget.firstPairBytes = budget.usedBytes;
                            budget.reserve(80L + 4L * length);
                            int[] px = Arrays.copyOf(one.px, length);
                            System.arraycopy(two.px, 0, px, one.px.length, two.px.length);
                            var p =
                                    new Piece(
                                            px,
                                            one.l,
                                            two.r,
                                            Math.min(one.t, two.t),
                                            Math.max(one.b, two.b));
                            found =
                                    prove(
                                            gray,
                                            clean,
                                            w,
                                            h,
                                            heads,
                                            owner,
                                            p,
                                            tracks,
                                            proofPieces,
                                            views,
                                            true,
                                            budget);
                        }
                }
                if (found != null) {
                    budget.take(1);
                    budget.reserve(80);
                    result.add(found);
                    budget.certified++;
                }
            }
        }
        budget.take(1L + result.size());
        budget.reserve(48L + 16L * result.size());
        return List.copyOf(result);
    }

    /** Budgeted flat fallback when the caller supplied no independently verified track. */
    /** This new route abstains independently; it cannot withdraw an existing slide. */
    static List<NoteSlideDetector.Stroke> addAcrossBars(
            List<NoteSlideDetector.Stroke> old,
            byte[] gray,
            int w,
            int h,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> input,
            byte[] first,
            byte[] second,
            StaffPitchTrack[] supplied) {
        var budget = new Budget(DEFAULT_WORK, DEFAULT_BYTES);
        try {
            var added = findAcrossBars(gray, w, h, staffs, input, first, second, supplied, budget);
            if (budget.refused() || added.isEmpty()) return old;
            budget.reserve(64L + (old.size() + added.size()) * 24L);
            var out = new ArrayList<>(old);
            for (var stroke : added) {
                budget.take(1L + out.size());
                if (out.stream().noneMatch(s -> s.noteIndex() == stroke.noteIndex()))
                    out.add(stroke);
            }
            return List.copyOf(out);
        } catch (Refused failure) {
            return old;
        }
    }

    static List<NoteSlideDetector.Stroke> findAcrossBars(
            byte[] gray,
            int w,
            int h,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> input,
            byte[] first,
            byte[] second,
            StaffPitchTrack[] supplied,
            Budget budget) {
        if (budget == null) return List.of();
        try {
            return crossBars(gray, w, h, staffs, input, first, second, supplied, budget);
        } catch (Refused failure) {
            return List.of();
        }
    }

    private static final class CrossWorkspace {
        byte[] clean;
        boolean[] seen;
        int[] queue;

        void ensure(int length, Budget budget) {
            if (seen == null) {
                budget.reserve(128L + 5L * length);
                seen = new boolean[length];
                queue = new int[length];
            }
        }

        void prepare(byte[] base, Budget budget) {
            ensure(base.length, budget);
            if (clean == null) {
                budget.reserve(16L + base.length);
                clean = new byte[base.length];
            }
            budget.take(base.length);
            System.arraycopy(base, 0, clean, 0, base.length);
        }
    }

    private static List<Piece> piecesAroundOwner(
            CrossWorkspace ws, byte[] plane, int w, int h, float gap, Owners owner, Budget budget) {
        ws.ensure(plane.length, budget);
        int left = Math.max(0, (int) Math.ceil(owner.a().x() + gap * .45f));
        int right = Math.min(w - 1, (int) Math.floor(owner.b().x() - gap * .45f));
        int top = Math.max(0, (int) Math.floor(Math.min(owner.a().y(), owner.b().y()) - gap));
        int bottom = Math.min(h - 1, (int) Math.ceil(Math.max(owner.a().y(), owner.b().y()) + gap));
        var result = new ArrayList<Piece>();
        int end = 0;
        try {
            for (int y = top; y <= bottom; y++)
                for (int x = left; x <= right; x++) {
                    budget.take(1);
                    int seed = y * w + x;
                    if (ws.seen[seed] || (plane[seed] & 255) >= 150) continue;
                    int first = end, read = end, l = w, r = 0, t = h, b = 0;
                    ws.queue[end++] = seed;
                    ws.seen[seed] = true;
                    while (read < end) {
                        budget.take(10);
                        int at = ws.queue[read++], xx = at % w, yy = at / w;
                        l = Math.min(l, xx);
                        r = Math.max(r, xx);
                        t = Math.min(t, yy);
                        b = Math.max(b, yy);
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++) {
                                int nx = xx + dx, ny = yy + dy;
                                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                                int next = ny * w + nx;
                                if (!ws.seen[next] && (plane[next] & 255) < 150) {
                                    ws.seen[next] = true;
                                    ws.queue[end++] = next;
                                }
                            }
                    }
                    int n = end - first;
                    budget.take(n);
                    budget.reserve(128L + 4L * n);
                    result.add(new Piece(Arrays.copyOfRange(ws.queue, first, end), l, r, t, b));
                }
            return result;
        } finally {
            budget.take(end);
            for (int i = 0; i < end; i++) ws.seen[ws.queue[i]] = false;
        }
    }

    private static List<NoteSlideDetector.Stroke> crossBars(
            byte[] gray,
            int w,
            int h,
            List<NoteSlideDetector.Staff> staffs,
            List<NoteSlideDetector.Head> input,
            byte[] first,
            byte[] second,
            StaffPitchTrack[] supplied,
            Budget budget) {
        budget.take(1);
        if (gray == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || first == null
                || first.length != gray.length
                || staffs == null
                || staffs.isEmpty()
                || input == null) return List.of();
        int inputCount = input.size();
        if (inputCount < 2) return List.of();
        budget.reserve(256L + inputCount * 96L + staffs.size() * 64L);
        var heads = new NoteSlideDetector.Head[inputCount];
        var iterator = input.iterator();
        for (int i = 0; i < heads.length; i++) {
            budget.take(1);
            if (!iterator.hasNext()) budget.fail();
            var q = iterator.next();
            if (q == null
                    || !Float.isFinite(q.x() + q.y() + q.gap())
                    || q.gap() < 6
                    || q.gap() > 64) budget.fail();
            heads[i] = q;
        }
        if (iterator.hasNext()) budget.fail();
        var pairs = new ArrayList<Owners>();
        float maxGap = 0;
        for (int i = 0; i < heads.length; i++) {
            budget.take(2L * heads.length + 1);
            int previous = previousAcrossBar(Arrays.asList(heads), i);
            if (previous < 0) continue;
            var a = heads[previous];
            var b = heads[i];
            float gap = b.gap(), span = b.x() - a.x();
            if (b.staff() < 0
                    || b.staff() >= staffs.size()
                    || span < 2 * gap
                    || span > 12 * gap
                    || Math.abs(a.y() - b.y()) < gap * .35f
                    || Math.abs(a.y() - b.y()) > 4 * gap) continue;
            budget.reserve(64);
            pairs.add(new Owners(a, b, i));
            maxGap = Math.max(maxGap, gap);
        }
        if (pairs.isEmpty()) return List.of();
        var views = new ArrayList<View>();
        for (byte[] clean : new byte[][] {first, second}) {
            budget.take(1);
            if (clean != null && clean.length == gray.length) views.add(new View(clean, List.of()));
        }
        var tracks = new StaffPitchTrack[staffs.size()];
        var result = new ArrayList<NoteSlideDetector.Stroke>();
        var workspace = new CrossWorkspace();
        for (var owner : pairs) {
            budget.take(1);
            int lane = owner.b().staff();
            if (tracks[lane] == null) {
                if (supplied != null && lane < supplied.length && supplied[lane] != null)
                    tracks[lane] = supplied[lane];
                else {
                    var staff = staffs.get(lane);
                    if (staff == null) continue;
                    tracks[lane] = flatTrack(gray, w, h, staff, budget);
                }
            }
            if (tracks[lane] == null
                    || !body(gray, w, h, owner.a(), budget)
                    || !body(gray, w, h, owner.b(), budget)) continue;
            var ownerViews = new ArrayList<View>();
            for (var view : views) {
                budget.reserve(64);
                ownerViews.add(
                        new View(
                                view.clean(),
                                piecesAroundOwner(
                                        workspace,
                                        view.clean(),
                                        w,
                                        h,
                                        owner.b().gap(),
                                        owner,
                                        budget)));
            }
            var typed =
                    barlineView(gray, w, h, heads, owner, ownerViews, tracks, workspace, budget);
            if (typed == null) continue;
            budget.reserve(64L + 24L * (views.size() + 1));
            var proofViews = new ArrayList<>(ownerViews);
            proofViews.add(typed);
            var near = new ArrayList<Piece>();
            var proofPieces = new ArrayList<Piece>();
            float gap = owner.b().gap();
            for (var view : proofViews)
                for (var p : view.pieces()) {
                    budget.take(1);
                    boolean touches =
                            p.r >= owner.a().x() + gap * .45f
                                    && p.l <= owner.b().x() - gap * .45f
                                    && p.b >= Math.min(owner.a().y(), owner.b().y()) - gap
                                    && p.t <= Math.max(owner.a().y(), owner.b().y()) + gap;
                    if (touches) {
                        budget.reserve(16);
                        proofPieces.add(p);
                    }
                    if (view == typed
                            && p.px.length >= 6
                            && p.r - p.l >= 3
                            && p.r - p.l <= gap * 12
                            && p.b - p.t <= gap * 6
                            && p.l >= owner.a().x() + gap * .45f
                            && p.r <= owner.b().x() - gap * .45f
                            && p.t >= Math.min(owner.a().y(), owner.b().y()) - gap
                            && p.b <= Math.max(owner.a().y(), owner.b().y()) + gap) {
                        budget.reserve(16);
                        near.add(p);
                    }
                }
            NoteSlideDetector.Stroke found = null;
            for (var p : near) {
                found =
                        prove(
                                gray,
                                typed.clean(),
                                w,
                                h,
                                heads,
                                owner,
                                p,
                                tracks,
                                proofPieces,
                                proofViews,
                                false,
                                budget);
                if (found != null) break;
            }
            budget.reserve(1024L + near.size() * 8L);
            near.sort(
                    (a, b) -> {
                        budget.take(1);
                        return Integer.compare(a.l, b.l);
                    });
            for (int i = 0; i < near.size() && found == null; i++)
                for (int j = i + 1; j < near.size() && found == null; j++) {
                    budget.take(1);
                    var one = near.get(i);
                    var two = near.get(j);
                    if (two.l <= one.r || two.l - one.r > gap * 2) continue;
                    int length = Math.addExact(one.px.length, two.px.length);
                    budget.take(length);
                    budget.reserve(80L + length * 4L);
                    int[] px = Arrays.copyOf(one.px, length);
                    System.arraycopy(two.px, 0, px, one.px.length, two.px.length);
                    var p =
                            new Piece(
                                    px,
                                    one.l,
                                    two.r,
                                    Math.min(one.t, two.t),
                                    Math.max(one.b, two.b));
                    found =
                            prove(
                                    gray,
                                    typed.clean(),
                                    w,
                                    h,
                                    heads,
                                    owner,
                                    p,
                                    tracks,
                                    proofPieces,
                                    proofViews,
                                    true,
                                    budget);
                }
            if (found != null) {
                budget.reserve(80);
                result.add(found);
                budget.certified++;
            }
        }
        budget.reserve(48L + 16L * result.size());
        return List.copyOf(result);
    }

    /** Unique previous physical attack, only when the destination opens the adjacent bar. */
    static int previousAcrossBar(List<NoteSlideDetector.Head> heads, int targetIndex) {
        if (heads == null || targetIndex < 0 || targetIndex >= heads.size()) return -1;
        var target = heads.get(targetIndex);
        if (target == null
                || target.staff() < 0
                || target.measure() <= 0
                || !Float.isFinite(target.x() + target.y() + target.gap())
                || target.gap() < 6) return -1;
        float threshold = target.gap() * .35f, latest = -Float.MAX_VALUE;
        int targets = 0;
        for (var h : heads) {
            if (h == null || !Float.isFinite(h.x() + h.y() + h.gap())) return -1;
            if (h.staff() != target.staff()) continue;
            if (Math.abs(h.x() - target.x()) <= threshold) targets++;
            if (h.x() < target.x() - threshold) {
                if (h.measure() == target.measure()) return -1;
                latest = Math.max(latest, h.x());
            }
        }
        if (targets != 1 || latest == -Float.MAX_VALUE) return -1;
        int source = -1;
        for (int i = 0; i < heads.size(); i++) {
            var h = heads.get(i);
            if (h.staff() == target.staff()
                    && h.x() < target.x() - threshold
                    && latest - h.x() <= threshold) {
                if (source >= 0
                        || h.measure() != target.measure() - 1
                        || Math.abs(h.gap() - target.gap()) > target.gap() * .15f) return -1;
                source = i;
            }
        }
        return source;
    }

    private static View barlineView(
            byte[] gray,
            int w,
            int h,
            NoteSlideDetector.Head[] heads,
            Owners owner,
            List<View> views,
            StaffPitchTrack[] tracks,
            CrossWorkspace workspace,
            Budget budget) {
        var a = owner.a();
        var b = owner.b();
        float gap = b.gap();
        var track = tracks[b.staff()];
        if (track == null) return null;
        FiniteBarlineSlideInk.Body body = null;
        for (int x = Math.max(0, (int) Math.ceil(a.x() + gap));
                x <= Math.min(w - 1, (int) Math.floor(b.x() - gap));
                x++) {
            budget.take(1);
            if (!track.verifiedAt(x)) return null;
            float[] f = track.at(x);
            int top = Math.round(f[0] - 4 * f[1]), bottom = Math.round(f[0]);
            if (top < 0 || bottom >= h) return null;
            int hit = 0;
            budget.take(bottom - top + 1L);
            for (int y = top; y <= bottom; y++) if ((gray[y * w + x] & 255) < 150) hit++;
            if (hit < (bottom - top + 1) * .95f) continue;
            if (body != null) {
                if (x < body.left || x > body.right) return null;
                continue;
            }
            long bound = (long) Math.ceil(gap * gap * 128) + heads.length * 8L + 1024;
            budget.take(bound);
            budget.reserve((long) Math.ceil(gap * gap * 32) + 512);
            body = FiniteBarlineSlideInk.find(gray, w, h, track, x, Arrays.asList(heads), bound);
            if (body == null) return null;
        }
        if (body == null) return null;
        budget.reserve(256);
        var rolePixels = new HashSet<Integer>();
        java.util.function.IntConsumer own =
                pixel -> {
                    budget.take(32);
                    // Prepay possible boxing/node/table allocation before mutating the set.
                    // Duplicate entries may conservatively consume the same reservation.
                    budget.reserve(80);
                    rolePixels.add(pixel);
                };
        for (var head : new NoteSlideDetector.Head[] {a, b}) {
            budget.take(4L * w + (long) Math.ceil(gap * gap * 256) + 128);
            budget.reserve(16L * w + (long) Math.ceil(gap * gap * 64) + 1024);
            var evidence =
                    PrintedSlurOwners.headEvidence(
                            List.of(
                                    new PrintedSlurOwners.GeometryHead(
                                            0, head.x(), head.y(), head.staff(), tracks.length)),
                            gray,
                            w,
                            h,
                            track);
            if (evidence.returningOwnerCost() != 1) return null;
            // Contract only the existing one-gap endpoint collar's literal component
            // attached to this independently visible head. Detached ink stays UNKNOWN.
            int l = Math.max(0, (int) Math.ceil(head.x() - gap)),
                    r = Math.min(w - 1, (int) Math.floor(head.x() + gap));
            int t = Math.max(0, (int) Math.ceil(head.y() - gap)),
                    end = Math.min(h - 1, (int) Math.floor(head.y() + gap));
            for (var other : heads) {
                budget.take(1);
                if (other != a
                        && other != b
                        && other.x() >= l
                        && other.x() <= r
                        && other.y() >= t
                        && other.y() <= end) return null;
            }
            int sw = r - l + 1, sh = end - t + 1, area = sw * sh;
            budget.reserve(area * 5L + 64);
            budget.take(area * 16L);
            boolean[] seen = new boolean[area];
            int[] queue = new int[area];
            int read = 0, n = 0;
            for (int y = t; y <= end; y++)
                for (int x = l; x <= r; x++)
                    if ((gray[y * w + x] & 255) < 150 && evidence.contains(x, y)) {
                        int q = (y - t) * sw + x - l;
                        if (!seen[q]) {
                            seen[q] = true;
                            queue[n++] = q;
                        }
                    }
            if (n == 0) return null;
            while (read < n) {
                int q = queue[read++], x = l + q % sw, y = t + q / sw;
                own.accept(y * w + x);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < l || nx > r || ny < t || ny > end) continue;
                        int next = (ny - t) * sw + nx - l;
                        if (!seen[next] && (gray[ny * w + nx] & 255) < 150) {
                            seen[next] = true;
                            queue[n++] = next;
                        }
                    }
            }
        }
        budget.reserve(body.size() * 4L + 16);
        budget.take(body.size());
        int[] mask = body.pixels();
        for (int p : mask) own.accept(p);
        // Co-owned rule ink may be omitted only under the same literal rule proof used
        // by the complete-line verifier. Raw remains unchanged for every crossing check.
        for (int x = Math.max(0, (int) Math.ceil(a.x() + gap * .45f));
                x <= Math.min(w - 1, (int) Math.floor(b.x() - gap * .45f));
                x++) {
            budget.take(1);
            if (!track.verifiedAt(x)) return null;
            float[] f = track.at(x);
            for (int k = 0; k < 5; k++) {
                float center = f[0] - k * f[1];
                for (int y = Math.max(0, (int) Math.ceil(center - f[1] * .2f));
                        y <= Math.min(h - 1, (int) Math.floor(center + f[1] * .2f));
                        y++) {
                    budget.take(1);
                    if ((gray[y * w + x] & 255) < 150
                            && onRule(gray, w, h, x, y, gap, track, budget)) own.accept(y * w + x);
                }
            }
        }
        // Necessary work preflight over the full possible owner corridor. Unknown
        // compound components participate, even when no thin fragment was isolated before.
        int independentColumns = 0;
        for (int x = Math.max(0, (int) Math.ceil(a.x() + gap * .45f));
                x <= Math.min(w - 1, (int) Math.floor(b.x() - gap * .45f));
                x++) {
            boolean possible = false;
            for (int y = Math.max(0, (int) Math.floor(Math.min(a.y(), b.y()) - gap));
                    y <= Math.min(h - 1, (int) Math.ceil(Math.max(a.y(), b.y()) + gap));
                    y++) {
                budget.take(1);
                int at = y * w + x;
                if ((gray[at] & 255) < 150) {
                    budget.take(32);
                    budget.reserve(16); // Boxed lookup key is prepaid even without escape analysis.
                    if (!rolePixels.contains(at)) possible = true;
                }
            }
            if (possible) independentColumns++;
        }
        if (independentColumns < Math.ceil(gap * .65f)) return null;
        workspace.prepare(views.get(views.size() - 1).clean(), budget);
        for (int pixel : rolePixels) {
            budget.take(1);
            workspace.clean[pixel] = (byte) 255;
        }
        return new View(
                workspace.clean,
                piecesAroundOwner(workspace, workspace.clean, w, h, gap, owner, budget),
                body);
    }

    private static StaffPitchTrack flatTrack(
            byte[] gray, int w, int h, NoteSlideDetector.Staff staff, Budget budget) {
        int radius = Math.max(2, (int) Math.ceil(staff.gap() * .2f));
        budget.reserve(128L + 5L * 4);
        float[] centers = new float[5];
        for (int line = 0; line < 5; line++) {
            budget.take(1);
            int center = Math.round(staff.top() + line * staff.gap());
            int top = Math.max(0, center - radius),
                    bottom = Math.min(h - 1, center + radius),
                    length = bottom - top + 1;
            if (length < 1) return null;
            budget.reserve(24L + 4L * length);
            int[] support = new int[length];
            int peak = 0;
            for (int y = top; y <= bottom; y++)
                for (int x = 0; x < w; x++) {
                    budget.take(1);
                    if ((gray[y * w + x] & 255) < 150) support[y - top]++;
                }
            for (int i = 0; i < length; i++) {
                budget.take(1);
                peak = Math.max(peak, support[i]);
            }
            if (peak < w * .6f) return null;
            int first = -1, last = -1;
            double mass = 0, weighted = 0;
            for (int i = 0; i < length; i++) {
                budget.take(1);
                if (support[i] >= peak * .85f) {
                    if (first < 0) first = i;
                    last = i;
                    mass += support[i];
                    weighted += (top + i) * (double) support[i];
                }
            }
            if (first < 0 || last - first + 1 > Math.ceil(staff.gap() * .3f)) return null;
            centers[line] = (float) (weighted / mass);
            if (Math.abs(centers[line] - center) > staff.gap() * .2f) return null;
        }
        float gap = (centers[4] - centers[0]) / 4;
        if (gap < 3 || Math.abs(gap - staff.gap()) > staff.gap() * .12f) return null;
        for (int i = 0; i < 5; i++) {
            budget.take(1);
            if (Math.abs(centers[i] - (centers[0] + gap * i)) > .75f) return null;
        }
        budget.take(64);
        budget.reserve(512);
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {0, centers[4], gap}, new float[] {w - 1, centers[4], gap}));
    }

    private static int lower(List<Indexed> list, float value, Budget budget) {
        int lo = 0, hi = list.size();
        while (lo < hi) {
            budget.take(1);
            int mid = (lo + hi) >>> 1;
            if (list.get(mid).head().x() < value) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static int upper(List<Indexed> list, float value, Budget budget) {
        int lo = 0, hi = list.size();
        while (lo < hi) {
            budget.take(1);
            int mid = (lo + hi) >>> 1;
            if (list.get(mid).head().x() <= value) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static List<Piece> pieces(byte[] clean, int w, int h, float gap, Budget budget) {
        return pieces(clean, w, h, gap, gap * 1.2f, budget);
    }

    private static List<Piece> pieces(
            byte[] clean, int w, int h, float gap, float maxHeight, Budget budget) {
        budget.reserve(64L + 5L * clean.length);
        boolean[] ink = new boolean[clean.length];
        int[] queue = new int[ink.length];
        for (int i = 0; i < ink.length; i++) {
            budget.take(1);
            ink[i] = (clean[i] & 255) < 150;
        }
        var result = new ArrayList<Piece>();
        for (int seed = 0; seed < ink.length; seed++) {
            budget.take(1);
            if (!ink[seed]) continue;
            int read = 0, end = 1, l = w, r = 0, t = h, b = 0;
            queue[0] = seed;
            ink[seed] = false;
            while (read < end) {
                budget.take(1);
                int at = queue[read++], x = at % w, y = at / w;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        budget.take(1);
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                        int next = yy * w + xx;
                        if (ink[next]) {
                            ink[next] = false;
                            queue[end++] = next;
                        }
                    }
            }
            if (end >= 6 && r - l >= 3 && r - l <= gap * 12 && b - t >= 0 && b - t <= maxHeight) {
                budget.take(end);
                budget.reserve(128L + 4L * end);
                result.add(new Piece(Arrays.copyOf(queue, end), l, r, t, b));
            }
        }
        return result;
    }

    private static NoteSlideDetector.Stroke prove(
            byte[] gray,
            byte[] clean,
            int w,
            int h,
            NoteSlideDetector.Head[] heads,
            Owners owner,
            Piece p,
            StaffPitchTrack[] tracks,
            List<Piece> nearby,
            List<View> views,
            boolean joined,
            Budget budget) {
        budget.take(1);
        var a = owner.a();
        var b = owner.b();
        float gap = b.gap();
        int width = p.r - p.l + 1;
        if (width < gap * .65f
                || width > gap * 8
                || p.px.length < 8
                || (p.l - a.x()) / gap < .5f
                || (p.l - a.x()) / gap > 3
                || (b.x() - p.r) / gap < .5f
                || (b.x() - p.r) / gap > 3) return null;
        budget.reserve(64L + 12L * width);
        int[] lo = new int[width], hi = new int[width], count = new int[width];
        budget.take(2L * width);
        Arrays.fill(lo, h);
        Arrays.fill(hi, -1);
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        for (int at : p.px) {
            budget.take(1);
            int x = at % w, y = at / w;
            sx += x;
            sy += y;
            sxx += (double) x * x;
            syy += (double) y * y;
            sxy += (double) x * y;
            lo[x - p.l] = Math.min(lo[x - p.l], y);
            hi[x - p.l] = Math.max(hi[x - p.l], y);
            count[x - p.l]++;
        }
        int n = p.px.length;
        double cx = sx / n,
                cy = sy / n,
                vx = sxx / n - cx * cx,
                vy = syy / n - cy * cy,
                cov = sxy / n - cx * cy;
        if (vx <= 0 || vy <= 0) return null;
        double slope = cov / vx;
        double residual =
                Math.sqrt(
                        Math.max(
                                0,
                                (vx + vy - Math.sqrt((vx - vy) * (vx - vy) + 4 * cov * cov)) / 2));
        double tolerance = Math.max(1.3, gap * .1);
        if (residual > tolerance
                || Math.abs(slope) > 1.5
                || Math.abs(slope) * (width - 1) < Math.max(.7, gap * .04)
                || (b.y() - a.y()) * slope <= 0) return null;
        double leftY = cy + (p.l - cx) * slope, rightY = cy + (p.r - cx) * slope;
        if (Math.abs(a.y() - leftY) > gap || Math.abs(b.y() - rightY) > gap) return null;
        int independent = 0, holes = 0;
        double seen = -Double.MAX_VALUE;
        int sign = slope > 0 ? 1 : -1;
        var track = tracks[b.staff()];
        FiniteBarlineSlideInk.Body barlineRole = null;
        if (a.measure() != b.measure())
            for (var view : views) {
                budget.take(1);
                if (view.clean() == clean) barlineRole = view.barline();
            }
        for (int d = 0; d < width; d++) {
            budget.take(1);
            int x = p.l + d;
            double expected = cy + (x - cx) * slope;
            if (count[d] == 0) {
                holes++;
                if (!joined
                        || (!onRule(gray, w, h, x, (int) Math.round(expected), gap, track, budget)
                                && !onBarline(
                                        gray, w, h, x, expected, tolerance, barlineRole, budget))
                        || !rawNear(gray, w, h, x, expected, tolerance, budget)) return null;
                continue;
            }
            if (hi[d] - lo[d] + 1 > Math.max(3, Math.ceil(gap * .32))
                    // Raster samples denote unit pixel footprints; the fitted line is continuous.
                    // Retain the complete column and component, allowing only its half-pixel phase.
                    || !columnFootprintFits(lo[d], hi[d], expected, tolerance)) return null;
            for (int y = lo[d]; y <= hi[d]; y++) {
                budget.take(1);
                if ((clean[y * w + x] & 255) >= 150
                        && (!onRule(gray, w, h, x, y, gap, track, budget)
                                || (gray[y * w + x] & 255) >= 150)) return null;
            }
            double center = sign * (lo[d] + hi[d]) * .5;
            if (center < seen - tolerance) return null;
            seen = Math.max(seen, center);
            boolean own = false;
            for (int y = lo[d]; y <= hi[d]; y++) {
                budget.take(1);
                if ((clean[y * w + x] & 255) < 150 && !onRule(gray, w, h, x, y, gap, track, budget))
                    own = true;
            }
            if (own) independent++;
        }
        if (joined && holes == 0 || independent < Math.ceil(gap * .65f)) return null;
        // Any separate component joined back through literal verified rule ink is
        // contrary evidence: the proposed complete raster omits its continuation.
        // Both complete cleaned views participate, regardless of pixel-phase need.
        if (uncapturedRuleContinuation(
                gray,
                w,
                h,
                p,
                lo,
                hi,
                nearby,
                track,
                gap,
                a.measure() != b.measure(),
                leftY,
                slope,
                tolerance,
                barlineRole,
                budget)) return null;
        for (int i = 0; i < heads.length; i++) {
            budget.take(1);
            var other = heads[i];
            if (other == a || i == owner.target()) continue;
            if (other.x() > p.l - gap * .6f
                    && other.x() < p.r + gap * .6f
                    && Math.abs(other.y() - (cy + (other.x() - cx) * slope)) < gap * .6f)
                return null;
        }
        budget.reserve(64);
        var stroke =
                new NoteSlideDetector.Stroke(
                        p.l,
                        (float) leftY,
                        p.r,
                        (float) rightY,
                        slope < 0 ? 1 : -1,
                        owner.target(),
                        true,
                        false);
        for (var sourceView : views) {
            budget.take(1);
            if (!completeObservedSpan(
                    gray, sourceView.clean(), w, h, a, b, p, leftY, slope, track, budget))
                return null;
        }
        // Existing complete-span helper has two metadata scans plus bounded pixel collars.
        budget.take(2L * heads.length + 16L * w);
        budget.reserve(96);
        boolean complete =
                a.measure() == b.measure()
                        ? ObservedRuleSlideInk.completeSpan(
                                clean, w, h, Arrays.asList(heads), stroke)
                        : ObservedRuleSlideInk.completeSpan(clean, w, h, a, b, stroke);
        return complete ? stroke : null;
    }

    static boolean columnFootprintFits(int low, int high, double expected, double tolerance) {
        return Math.max(0, Math.abs((low + high) * .5 - expected) - .5) <= tolerance;
    }

    private static boolean onBarline(
            byte[] gray,
            int w,
            int h,
            int x,
            double expected,
            double tolerance,
            FiniteBarlineSlideInk.Body body,
            Budget budget) {
        if (body == null || x < body.left || x > body.right) return false;
        for (int y = Math.max(0, (int) Math.ceil(expected - tolerance));
                y <= Math.min(h - 1, (int) Math.floor(expected + tolerance));
                y++) {
            budget.take(16);
            if ((gray[y * w + x] & 255) < 150 && body.contains(y * w + x)) return true;
        }
        return false;
    }

    private static boolean typedBodyMember(
            FiniteBarlineSlideInk.Body body, int pixel, Budget budget) {
        if (body == null) return false;
        budget.take(16);
        return body.contains(pixel);
    }

    private static boolean uncapturedRuleContinuation(
            byte[] gray,
            int w,
            int h,
            Piece p,
            int[] lo,
            int[] hi,
            List<Piece> nearby,
            StaffPitchTrack track,
            float gap,
            boolean typedRuleRoles,
            double leftY,
            double slope,
            double tolerance,
            FiniteBarlineSlideInk.Body barline,
            Budget budget) {
        int[] ownLo = lo, ownHi = hi;
        if (typedRuleRoles) {
            budget.reserve(64L + 8L * lo.length);
            budget.take(3L * lo.length);
            ownLo = lo.clone();
            ownHi = hi.clone();
            for (int d = 0; d < lo.length; d++)
                if (ownHi[d] < ownLo[d]) {
                    int x = p.l + d;
                    double expected = leftY + d * slope;
                    for (int y = Math.max(0, (int) Math.ceil(expected - tolerance));
                            y <= Math.min(h - 1, (int) Math.floor(expected + tolerance));
                            y++) {
                        budget.take(1);
                        if ((gray[y * w + x] & 255) < 150) {
                            ownLo[d] = Math.min(ownLo[d], y);
                            ownHi[d] = Math.max(ownHi[d], y);
                        }
                    }
                    if (ownHi[d] < ownLo[d]) return true;
                }
        }
        for (var other : nearby) {
            budget.take(1);
            if (other == p || other.r < p.l || other.l > p.r) continue;
            for (int at : other.px) {
                budget.take(1);
                int x = at % w, y = at / w;
                if (typedRuleRoles
                        && ((barline != null && typedBodyMember(barline, at, budget))
                                || onRule(gray, w, h, x, y, gap, track, budget))) continue;
                if (x < p.l || x > p.r) continue;
                int d = x - p.l;
                if (ownHi[d] < ownLo[d]) continue;
                int first, last;
                if (y > ownHi[d]) {
                    first = ownHi[d] + 1;
                    last = y - 1;
                } else if (y < ownLo[d]) {
                    first = y + 1;
                    last = ownLo[d] - 1;
                } else continue;
                if (first > last || last - first + 1 > gap * .65f) continue;
                boolean bridge = true;
                for (int row = first; row <= last; row++) {
                    budget.take(1);
                    if ((gray[row * w + x] & 255) >= 150
                            || (!onRule(gray, w, h, x, row, gap, track, budget)
                                    && !(typedRuleRoles
                                            && barline != null
                                            && typedBodyMember(barline, row * w + x, budget)))) {
                        bridge = false;
                        break;
                    }
                }
                if (bridge) return true;
            }
        }
        return false;
    }

    private static boolean completeObservedSpan(
            byte[] gray,
            byte[] clean,
            int w,
            int h,
            NoteSlideDetector.Head a,
            NoteSlideDetector.Head b,
            Piece p,
            double leftY,
            double slope,
            StaffPitchTrack track,
            Budget budget) {
        float gap = b.gap();
        for (int side = 0; side < 2; side++) {
            budget.take(1);
            int first = side == 0 ? (int) Math.ceil(a.x() + gap) : p.r + 1;
            int last = side == 0 ? p.l - 1 : (int) Math.floor(b.x() - gap);
            int pixels = 0, columns = 0;
            for (int x = Math.max(0, first); x <= Math.min(w - 1, last); x++) {
                budget.take(1);
                double yy = leftY + (x - p.l) * slope;
                boolean found = false;
                for (int y = Math.max(0, (int) Math.ceil(yy - gap * .35f));
                        y <= Math.min(h - 1, (int) Math.floor(yy + gap * .35f));
                        y++) {
                    budget.take(1);
                    if ((clean[y * w + x] & 255) < 150) {
                        pixels++;
                        found = true;
                    }
                }
                if (found) columns++;
            }
            if (pixels >= 6 && columns >= 3) return false;
        }
        return true;
    }

    private static boolean rawNear(
            byte[] gray, int w, int h, int x, double center, double tolerance, Budget budget) {
        for (int y = Math.max(0, (int) Math.ceil(center - tolerance));
                y <= Math.min(h - 1, (int) Math.floor(center + tolerance));
                y++) {
            budget.take(1);
            if ((gray[y * w + x] & 255) < 150) return true;
        }
        return false;
    }

    private static boolean onRule(
            byte[] gray,
            int w,
            int h,
            int x,
            int y,
            float gap,
            StaffPitchTrack track,
            Budget budget) {
        budget.take(1);
        if (track == null || !track.verifiedAt(x)) return false;
        budget.take(32);
        budget.reserve(32);
        float[] f = track.at(x);
        float top = f[0] - 4 * f[1];
        int k = Math.round((y - top) / f[1]);
        if (k < 0 || k > 4 || Math.abs(y - top - k * f[1]) > f[1] * .2f) return false;
        budget.take(((long) Math.ceil(gap * 4) + 8) * ((long) Math.ceil(gap * .2) + 3));
        budget.reserve(32);
        return FlagRootStaffRule.matches(gray, w, h, y, x, gap, 150);
    }

    private static boolean body(
            byte[] gray, int w, int h, NoteSlideDetector.Head q, Budget budget) {
        budget.take(1);
        float gap = q.gap();
        if (q.x() < gap || q.x() + gap >= w || q.y() < gap || q.y() + gap >= h) return false;
        budget.take((long) Math.ceil(gap * 12) * (long) Math.ceil(gap * 12));
        if (PrintedStemDirection.detect(gray, w, h, q.x(), q.y(), gap) == 0) return false;
        int rows = 0;
        for (int y = Math.round(q.y() - gap * .35f); y <= Math.round(q.y() + gap * .35f); y++) {
            budget.take(1);
            int ink = 0;
            for (int x = Math.round(q.x() - gap * .55f); x <= Math.round(q.x() + gap * .55f); x++) {
                budget.take(1);
                if ((gray[y * w + x] & 255) < 150) ink++;
            }
            if (ink >= gap * .55f) rows++;
        }
        return rows >= Math.ceil(gap * .35f);
    }
}
