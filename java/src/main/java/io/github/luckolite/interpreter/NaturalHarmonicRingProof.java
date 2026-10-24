// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded physical ring and raw-note evidence; never derives musical time from geometry. */
public final class NaturalHarmonicRingProof {
    private static final int MAX_LOCAL_AREA = 16_384;
    private static final int MAX_UNION_PIXELS = 2_000_000;
    private static final int MAX_LOCAL_WORK = 64_000_000;
    private static final int MAX_LOCAL_BYTES = 64 * 1024 * 1024;

    private NaturalHarmonicRingProof() {}

    public record Box(int left, int top, int right, int bottom) {
        public Box {
            if (left < 0 || top < 0 || right < left || bottom < top)
                throw new IllegalArgumentException("Invalid bounded image box");
        }

        public int width() {
            return right - left + 1;
        }

        public int height() {
            return bottom - top + 1;
        }

        public boolean contains(int x, int y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    public static final class Budget {
        private int workRemaining;
        private int bytesRemaining;
        private final int workLimit;

        public Budget(int maxWork, int maxBytes) {
            if (maxWork < 0
                    || maxWork > MAX_LOCAL_WORK
                    || maxBytes < 0
                    || maxBytes > MAX_LOCAL_BYTES)
                throw new IllegalArgumentException("Invalid local proof budget");
            workLimit = workRemaining = maxWork;
            bytesRemaining = maxBytes;
        }

        private void take(long work, long bytes) {
            if (work < 0 || bytes < 0 || work > workRemaining || bytes > bytesRemaining)
                throw new IllegalArgumentException("Natural-harmonic proof budget exhausted");
            workRemaining -= (int) work;
            bytesRemaining -= (int) bytes;
        }

        public int workRemaining() {
            return workRemaining;
        }

        public int workLimit() {
            return workLimit;
        }

        public int bytesRemaining() {
            return bytesRemaining;
        }

        public void consume(int work, int bytes) {
            take(work, bytes);
        }
    }

    public record Ring(
            Box bounds,
            List<Integer> rawPixels,
            float centerX,
            float centerY,
            int counterPixels,
            float foregroundRatio) {
        public Ring {
            rawPixels = List.copyOf(rawPixels);
        }
    }

    public record NoteIdentity(
            String target,
            int measure,
            int staff,
            int staffCount,
            int staffStep,
            float centerX,
            float centerY,
            float staffGap,
            float staffTop,
            float xError,
            float yError) {
        /** Compatibility constructor for source-free callers without an observed Staff tuple. */
        public NoteIdentity(
                String target,
                int measure,
                int staff,
                int staffCount,
                int staffStep,
                float centerX,
                float centerY,
                float staffGap,
                float xError,
                float yError) {
            this(
                    target,
                    measure,
                    staff,
                    staffCount,
                    staffStep,
                    centerX,
                    centerY,
                    staffGap,
                    Float.NaN,
                    xError,
                    yError);
        }

        public NoteIdentity {
            if (target == null
                    || target.isBlank()
                    || measure < 0
                    || staff < 0
                    || staffCount < 1
                    || staff >= staffCount
                    || !Float.isFinite(centerX)
                    || !Float.isFinite(centerY)
                    || !Float.isFinite(staffGap)
                    || staffGap <= 0
                    || (!Float.isNaN(staffTop) && !Float.isFinite(staffTop))
                    || xError < 0
                    || yError < 0)
                throw new IllegalArgumentException("Invalid reconstructed note identity");
        }
    }

    public record MeasureBounds(float left, float right) {
        public MeasureBounds {
            if (!Float.isFinite(left)
                    || !Float.isFinite(right)
                    || left < 0
                    || right > 1
                    || right < left) throw new IllegalArgumentException("Invalid measure bounds");
        }
    }

    public record ReconstructedCenter(float x, float y, float xError, float yError) {}

    /** Invert OmrScoreInterpreter's float32 normalized head-center expressions. */
    public static ReconstructedCenter reconstruct(
            float position, float pageY, MeasureBounds measure, int width, int height) {
        if (measure == null
                || width <= 0
                || height <= 0
                || !Float.isFinite(position)
                || !Float.isFinite(pageY)
                || position < 0
                || position > 1
                || pageY < 0
                || pageY > 1)
            throw new IllegalArgumentException("Invalid normalized note coordinate");
        float span = measure.right - measure.left;
        float denominator = Math.max(.0001f, span);
        float normalizedX = measure.left + position * denominator;
        float x = normalizedX * width;
        float y = pageY * height;
        float xError =
                Math.max(
                        .5f,
                        width
                                * (.5f * Math.ulp(normalizedX)
                                        + .5f * Math.ulp(measure.left)
                                        + .5f * Math.ulp(denominator)
                                        + denominator * .5f * Math.ulp(position)));
        float yError = Math.max(.5f, height * Math.ulp(pageY));
        return new ReconstructedCenter(x, y, xError, yError);
    }

    public record Head(
            Box bounds, List<Integer> labelPixels, int rawDarkPixels, boolean attachedStem) {
        public Head {
            labelPixels = List.copyOf(labelPixels);
        }
    }

    public record OwnedRing(Ring ring, Head head, NoteIdentity note) {}

    private record RuleRun(int y, int left, int right) {}

    private record RuleBand(int top, int bottom, int left, int right) {}

    /**
     * Locate closed components in a caller-bounded gray ROI; an OCR-like circle is still only a
     * candidate.
     */
    public static List<Ring> rings(
            byte[] gray,
            int width,
            int height,
            Box roi,
            int cutoff,
            int minDiameter,
            int maxDiameter,
            Budget budget) {
        checkImage(gray, width, height, roi);
        if (cutoff < 0 || cutoff > 255 || minDiameter < 3 || maxDiameter < minDiameter)
            throw new IllegalArgumentException("Invalid ring limits");
        int area = area(roi);
        reserve(budget, area * 12L, area * 32L);
        byte[] seen = new byte[area];
        int[] queue = new int[area];
        List<Ring> result = new ArrayList<>();
        for (int y = roi.top; y <= roi.bottom; y++)
            for (int x = roi.left; x <= roi.right; x++) {
                int local = (y - roi.top) * roi.width() + x - roi.left;
                int absolute = y * width + x;
                if (seen[local] != 0 || (gray[absolute] & 255) >= cutoff) continue;
                int count = flood(gray, width, roi, x, y, cutoff, seen, queue);
                if (count < 5) continue;
                Box bounds = bounds(queue, count, roi.width(), roi.left, roi.top);
                if (bounds.left == roi.left
                        || bounds.right == roi.right
                        || bounds.top == roi.top
                        || bounds.bottom == roi.bottom) continue;
                if (Math.min(bounds.width(), bounds.height()) < minDiameter
                        || Math.max(bounds.width(), bounds.height()) > maxDiameter
                        || Math.max(bounds.width(), bounds.height())
                                > 1.28 * Math.min(bounds.width(), bounds.height())) continue;
                int boxArea = area(bounds);
                float ratio = (float) count / boxArea;
                if (ratio < .12f || ratio > .72f) continue;
                int counters = enclosedBackground(gray, width, bounds, cutoff, budget);
                if (counters == 0) continue;
                List<Integer> pixels = new ArrayList<>(count);
                double sx = 0, sy = 0;
                for (int i = 0; i < count; i++) {
                    int lx = queue[i] % roi.width(), ly = queue[i] / roi.width();
                    int px = roi.left + lx, py = roi.top + ly;
                    pixels.add(py * width + px);
                    sx += px;
                    sy += py;
                }
                result.add(
                        new Ring(
                                bounds,
                                pixels,
                                (float) (sx / count),
                                (float) (sy / count),
                                counters,
                                ratio));
            }
        return List.copyOf(result);
    }

    private record Span(int left, int right) {}

    private static final class DarkRun {
        final int y, left, right;
        int parent;
        int pixels;
        int minX, maxX, minY, maxY;

        DarkRun(int id, int y, int left, int right) {
            this.y = y;
            this.left = left;
            this.right = right;
            this.parent = id;
            this.pixels = right - left + 1;
            this.minX = left;
            this.maxX = right;
            this.minY = y;
            this.maxY = y;
        }
    }

    private static int root(List<DarkRun> runs, int id, Budget budget) {
        int r = id;
        while (runs.get(r).parent != r) {
            reserve(budget, 1, 0);
            r = runs.get(r).parent;
        }
        while (runs.get(id).parent != id) {
            reserve(budget, 1, 0);
            int next = runs.get(id).parent;
            runs.get(id).parent = r;
            id = next;
        }
        return r;
    }

    private static void join(List<DarkRun> runs, int a, int b, Budget budget) {
        int ra = root(runs, a, budget), rb = root(runs, b, budget);
        if (ra == rb) return;
        reserve(budget, 1, 0);
        DarkRun x = runs.get(ra), y = runs.get(rb);
        y.parent = ra;
        x.pixels += y.pixels;
        x.minX = Math.min(x.minX, y.minX);
        x.maxX = Math.max(x.maxX, y.maxX);
        x.minY = Math.min(x.minY, y.minY);
        x.maxY = Math.max(x.maxY, y.maxY);
    }

    private static boolean inUnion(List<Span>[] rows, int x, int y, Budget budget) {
        if (y < 0 || y >= rows.length || rows[y] == null) return false;
        List<Span> spans = rows[y];
        int lo = 0, hi = spans.size() - 1;
        while (lo <= hi) {
            reserve(budget, 1, 0);
            int mid = (lo + hi) >>> 1;
            Span span = spans.get(mid);
            if (x < span.left) hi = mid - 1;
            else if (x > span.right) lo = mid + 1;
            else return true;
        }
        return false;
    }

    public record Search(Box roi, int minDiameter, int maxDiameter) {
        public Search {
            if (roi == null || minDiameter < 3 || maxDiameter < minDiameter)
                throw new IllegalArgumentException("Invalid ring search");
        }
    }

    /**
     * Data-derived hard work allowance for one whole-page note search, never a per-note multiplier.
     */
    public record WorkPlan(
            long unionPixels,
            long maximumHorizontalRuns,
            long rowEvents,
            long rowSortWork,
            long candidateBound,
            long noteHeadArea,
            int workLimit) {}

    public static WorkPlan pageWorkPlan(
            int width, int height, List<Search> searches, List<NoteIdentity> notes) {
        if (width <= 0
                || height <= 0
                || searches == null
                || searches.isEmpty()
                || searches.size() > 4096
                || notes == null) throw new IllegalArgumentException("Invalid page work plan");
        @SuppressWarnings("unchecked")
        List<Span>[] rows = (List<Span>[]) new List<?>[height];
        Set<Box> boxes = new HashSet<>();
        Set<Search> uniqueSearches = new HashSet<>();
        long events = 0;
        int maxSearchDiameter = 0;
        for (Search search : searches) {
            if (search == null
                    || search.minDiameter < 3
                    || search.maxDiameter < search.minDiameter
                    || search.maxDiameter > 4096)
                throw new IllegalArgumentException("Invalid page search");
            Box box = search.roi;
            if (box.right >= width || box.bottom >= height || area(box) > MAX_LOCAL_AREA)
                throw new IllegalArgumentException("Invalid or unbounded page search");
            uniqueSearches.add(search);
            maxSearchDiameter = Math.max(maxSearchDiameter, search.maxDiameter);
            if (!boxes.add(box)) continue;
            events += box.height();
            for (int y = box.top; y <= box.bottom; y++) {
                if (rows[y] == null) rows[y] = new ArrayList<>();
                rows[y].add(new Span(box.left, box.right));
            }
        }
        if (events > 500_000L)
            throw new IllegalArgumentException("Page row index exceeds local bound");
        long pixels = 0, runBound = 0, sortWork = 0;
        for (List<Span> row : rows)
            if (row != null) {
                int n = row.size();
                int log = 0;
                for (int v = n - 1; v > 0; v >>>= 1) log++;
                sortWork += (long) n * (log + 2);
                row.sort(Comparator.comparingInt(Span::left).thenComparingInt(Span::right));
                int left = row.get(0).left, right = row.get(0).right;
                for (int i = 1; i < n; i++) {
                    Span next = row.get(i);
                    if ((long) next.left <= (long) right + 1) right = Math.max(right, next.right);
                    else {
                        int length = right - left + 1;
                        pixels += length;
                        runBound += (length + 1L) / 2;
                        left = next.left;
                        right = next.right;
                    }
                }
                int length = right - left + 1;
                pixels += length;
                runBound += (length + 1L) / 2;
            }
        if (pixels <= 0 || pixels > MAX_UNION_PIXELS)
            throw new IllegalArgumentException("Unique page search union exceeds hard pixel bound");
        long headArea = 0;
        for (NoteIdentity note : notes) {
            if (note == null) continue;
            int margin = Math.max(2, (int) Math.ceil(note.staffGap * .6f));
            int l = Math.max(0, (int) Math.floor(note.centerX - note.xError) - margin);
            int r = Math.min(width - 1, (int) Math.ceil(note.centerX + note.xError) + margin);
            int t = Math.max(0, (int) Math.floor(note.centerY - note.yError) - margin);
            int b = Math.min(height - 1, (int) Math.ceil(note.centerY + note.yError) + margin);
            long local = (long) (r - l + 1) * (b - t + 1);
            if (local <= MAX_LOCAL_AREA) headArea += local;
        }
        long candidateBound = Math.min(pixels / 5, runBound);
        // 4 passes over unique pixels; bounded run construction/DSU; row-index sort;
        // per-candidate shape/counter work within the declared maximum search box; and one
        // note comparison plus bounded local head proof per possible candidate. The final
        // 64M hard stop prevents adversarial inputs from turning this envelope into unbounded work.
        long derived =
                4L * pixels
                        + 40L * runBound
                        + 8L * events
                        + 4L * sortWork
                        + 8L * uniqueSearches.size()
                        + candidateBound
                                * (uniqueSearches.size()
                                        + notes.size()
                                        + 8L * maxSearchDiameter * maxSearchDiameter)
                        + 32L * headArea;
        int limit = (int) Math.min(MAX_LOCAL_WORK, Math.max(1L, derived));
        return new WorkPlan(pixels, runBound, events, sortWork, candidateBound, headArea, limit);
    }

    /**
     * Scan the exact union of the caller's original ROIs once. A closed component is returned only
     * if it lies wholly inside at least one original ROI and does not touch that ROI edge; this
     * preserves the old per-ROI component boundary semantics.
     */
    public static List<Ring> ringsInUnion(
            byte[] gray,
            int width,
            int height,
            List<Search> searches,
            int cutoff,
            int minDiameter,
            int maxDiameter,
            Budget budget) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || searches == null
                || searches.isEmpty()
                || searches.size() > 4096
                || budget == null
                || cutoff < 0
                || cutoff > 255
                || minDiameter < 3
                || maxDiameter < minDiameter)
            throw new IllegalArgumentException("Invalid ring search union");
        int pixels = Math.multiplyExact(width, height);
        List<Box> unique = new ArrayList<>();
        Set<Box> boxSet = new HashSet<>();
        List<Search> uniqueSearches = new ArrayList<>();
        Set<Search> searchSet = new HashSet<>();
        long rowEvents = 0;
        for (Search search : searches) {
            if (search.minDiameter < minDiameter || search.maxDiameter > maxDiameter)
                throw new IllegalArgumentException("Search diameter escapes declared envelope");
            Box roi = search.roi;
            checkImage(gray, width, height, roi);
            if (area(roi) > MAX_LOCAL_AREA)
                throw new IllegalArgumentException("Ring ROI too large");
            if (boxSet.add(roi)) {
                unique.add(roi);
                rowEvents += roi.height();
            }
            if (searchSet.add(search)) uniqueSearches.add(search);
        }
        if (unique.isEmpty() || rowEvents > 500_000L)
            throw new IllegalArgumentException("Ring union index exceeds local bound");

        // Rows retain every original interval. Reserve both the interval objects and row lists
        // before allocation; merged rows are separately included in this scratch allowance.
        reserve(budget, rowEvents, rowEvents * 48L + height * 24L);
        @SuppressWarnings("unchecked")
        List<Span>[] rows = (List<Span>[]) new List<?>[height];
        for (Box roi : unique)
            for (int y = roi.top; y <= roi.bottom; y++) {
                List<Span> row = rows[y];
                if (row == null) rows[y] = row = new ArrayList<>();
                row.add(new Span(roi.left, roi.right));
            }

        long sortWork = 0;
        for (List<Span> row : rows)
            if (row != null) {
                int n = row.size();
                int log = 0;
                for (int v = n - 1; v > 0; v >>>= 1) log++;
                sortWork += (long) n * (log + 2);
            }
        reserve(budget, sortWork, 0);
        long unionArea = 0;
        for (int y = 0; y < height; y++) {
            List<Span> row = rows[y];
            if (row == null) continue;
            row.sort(Comparator.comparingInt(Span::left).thenComparingInt(Span::right));
            List<Span> merged = new ArrayList<>();
            int left = row.get(0).left, right = row.get(0).right;
            for (int i = 1; i < row.size(); i++) {
                Span next = row.get(i);
                if ((long) next.left <= (long) right + 1) right = Math.max(right, next.right);
                else {
                    merged.add(new Span(left, right));
                    left = next.left;
                    right = next.right;
                }
            }
            merged.add(new Span(left, right));
            rows[y] = merged;
            for (Span span : merged) {
                int length = span.right - span.left + 1;
                unionArea += length;
            }
        }
        if (unionArea <= 0 || unionArea > MAX_UNION_PIXELS)
            throw new IllegalArgumentException("Ring union pixel area exceeds work cap");
        List<DarkRun> runs = new ArrayList<>();
        int rowCapacity = Math.max(1, width / 2 + 1);
        reserve(budget, width * 2L, (long) rowCapacity * Integer.BYTES * 2);
        int[] previous = new int[rowCapacity], current = new int[rowCapacity];
        int previousCount = 0;
        for (int y = 0; y < height; y++) {
            List<Span> row = rows[y];
            if (row == null) {
                previousCount = 0;
                int[] swap = previous;
                previous = current;
                current = swap;
                continue;
            }
            int currentCount = 0, priorAt = 0;
            for (Span span : row) {
                int x = span.left;
                while (x <= span.right) {
                    reserve(budget, 1, 0);
                    if ((gray[y * width + x] & 255) >= cutoff) {
                        x++;
                        continue;
                    }
                    int left = x++;
                    while (x <= span.right) {
                        if ((gray[y * width + x] & 255) >= cutoff) break;
                        reserve(budget, 1, 0);
                        x++;
                    }
                    int right = x - 1;
                    reserve(budget, 1, 72);
                    if (runs.size() >= unionArea || currentCount >= current.length)
                        throw new IllegalArgumentException("Sparse run bound exceeded");
                    int id = runs.size();
                    runs.add(new DarkRun(id, y, left, right));
                    current[currentCount++] = id;
                    while (priorAt < previousCount
                            && runs.get(previous[priorAt]).right < left - 1) {
                        reserve(budget, 1, 0);
                        priorAt++;
                    }
                    for (int p = priorAt; p < previousCount; p++) {
                        DarkRun old = runs.get(previous[p]);
                        reserve(budget, 1, 0);
                        if (old.left > right + 1) break;
                        if (old.right >= left - 1) join(runs, id, previous[p], budget);
                    }
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            previousCount = currentCount;
        }

        reserve(budget, runs.size(), runs.size() * 8L);
        int[] firstRun = new int[runs.size()], nextRun = new int[runs.size()];
        java.util.Arrays.fill(firstRun, -1);
        java.util.Arrays.fill(nextRun, -1);
        List<Integer> rootIds = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            reserve(budget, 1, 0);
            int r = root(runs, i, budget);
            nextRun[i] = firstRun[r];
            firstRun[r] = i;
            if (r == i) {
                reserve(budget, 1, 20);
                rootIds.add(r);
            }
        }
        List<Ring> result = new ArrayList<>();
        for (int rootId : rootIds) {
            DarkRun component = runs.get(rootId);
            if (component.pixels < 5 || component.pixels > MAX_LOCAL_AREA) continue;
            Box bounds = new Box(component.minX, component.minY, component.maxX, component.maxY);
            if (bounds.width() > maxDiameter || bounds.height() > maxDiameter) continue;
            int componentArea = area(bounds);
            reserve(budget, component.pixels + componentArea, componentArea * 5L);
            byte[] member = new byte[componentArea];
            for (int runId = firstRun[rootId]; runId >= 0; runId = nextRun[runId]) {
                DarkRun run = runs.get(runId);
                for (int x = run.left; x <= run.right; x++)
                    member[(run.y - bounds.top) * bounds.width() + x - bounds.left] = 1;
            }
            boolean clipped = false;
            for (int runId = firstRun[rootId]; runId >= 0 && !clipped; runId = nextRun[runId]) {
                DarkRun run = runs.get(runId);
                for (int x = run.left; x <= run.right && !clipped; x++) {
                    for (int ny = Math.max(0, run.y - 1);
                            ny <= Math.min(height - 1, run.y + 1);
                            ny++)
                        for (int nx = Math.max(0, x - 1); nx <= Math.min(width - 1, x + 1); nx++) {
                            reserve(budget, 1, 0);
                            if (member[(run.y - bounds.top) * bounds.width() + x - bounds.left]
                                    == 0)
                                throw new IllegalStateException(
                                        "Sparse body membership invariant failed");
                            if ((gray[ny * width + nx] & 255) < cutoff
                                    && !inUnion(rows, nx, ny, budget)) clipped = true;
                        }
                }
            }
            if (clipped) continue;
            boolean boundedByOriginalRoi = false;
            for (Search search : uniqueSearches) {
                reserve(budget, 1, 0);
                Box roi = search.roi;
                if (bounds.left >= roi.left
                        && bounds.right <= roi.right
                        && bounds.top >= roi.top
                        && bounds.bottom <= roi.bottom
                        && bounds.left != roi.left
                        && bounds.right != roi.right
                        && bounds.top != roi.top
                        && bounds.bottom != roi.bottom
                        && bounds.width() >= search.minDiameter
                        && bounds.height() >= search.minDiameter
                        && Math.max(bounds.width(), bounds.height()) <= search.maxDiameter) {
                    boundedByOriginalRoi = true;
                    break;
                }
            }
            if (!boundedByOriginalRoi) continue;
            if (Math.min(bounds.width(), bounds.height()) < minDiameter
                    || Math.max(bounds.width(), bounds.height()) > maxDiameter
                    || Math.max(bounds.width(), bounds.height())
                            > 1.28 * Math.min(bounds.width(), bounds.height())) continue;
            int boxArea = area(bounds);
            float ratio = (float) component.pixels / boxArea;
            if (ratio < .12f || ratio > .72f) continue;
            int counters = enclosedBackground(gray, width, bounds, cutoff, budget);
            if (counters == 0) continue;
            reserve(budget, component.pixels * 9L, component.pixels * 8L);
            int[] queue = new int[component.pixels];
            byte[] seen = new byte[componentArea];
            int count = 1, firstRunId = firstRun[rootId];
            for (int runId = nextRun[firstRunId]; runId >= 0; ) {
                int following = nextRun[runId];
                DarkRun candidate = runs.get(runId), firstCandidate = runs.get(firstRunId);
                reserve(budget, 1, 0);
                if (candidate.y < firstCandidate.y
                        || candidate.y == firstCandidate.y && candidate.left < firstCandidate.left)
                    firstRunId = runId;
                runId = following;
            }
            DarkRun firstRunValue = runs.get(firstRunId);
            int first =
                    (firstRunValue.y - bounds.top) * bounds.width()
                            + firstRunValue.left
                            - bounds.left;
            queue[0] = first;
            seen[first] = 1;
            for (int read = 0; read < count; read++) {
                int local = queue[read], cx = bounds.left + local % bounds.width();
                int cy = bounds.top + local / bounds.width();
                for (int ny = Math.max(bounds.top, cy - 1);
                        ny <= Math.min(bounds.bottom, cy + 1);
                        ny++)
                    for (int nx = Math.max(bounds.left, cx - 1);
                            nx <= Math.min(bounds.right, cx + 1);
                            nx++) {
                        reserve(budget, 1, 0);
                        int ni = (ny - bounds.top) * bounds.width() + nx - bounds.left;
                        if (member[ni] != 0 && seen[ni] == 0) {
                            seen[ni] = 1;
                            queue[count++] = ni;
                        }
                    }
            }
            if (count != component.pixels) continue;
            List<Integer> raw = new ArrayList<>(count);
            double sx = 0, sy = 0;
            for (int i = 0; i < count; i++) {
                int px = bounds.left + queue[i] % bounds.width();
                int py = bounds.top + queue[i] / bounds.width();
                raw.add(py * width + px);
                sx += px;
                sy += py;
            }
            result.add(
                    new Ring(
                            bounds,
                            raw,
                            (float) (sx / count),
                            (float) (sy / count),
                            counters,
                            ratio));
        }
        return List.copyOf(result);
    }

    public enum HeadDecision {
        OWNED,
        INVALID_INPUT,
        NO_LABELED_COMPONENT,
        HEAD_SHAPE_NOT_PROVEN,
        HEAD_RAW_FILL_NOT_PROVEN,
        ATTACHED_STEM_NOT_PROVEN,
        UNOWNED_DARK_IN_HEAD_BOUNDS,
        AMBIGUOUS_HEAD_COMPONENTS
    }

    /** Diagnostic counts preserve the raw proof boundary without granting partial ownership. */
    public record HeadAssessment(
            HeadDecision decision,
            Head head,
            int labeledComponents,
            int shapeCandidates,
            int bodyCandidates,
            int stemCandidates,
            int unownedDarkPixels,
            int firstUnownedX,
            int firstUnownedY,
            int firstUnownedGray,
            int firstUnownedLabel) {}

    /**
     * Find one labeled head in the reconstructed float32 interval and verify its raw body and stem.
     */
    public static HeadAssessment assessRawHeadAndStem(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            byte headLabel,
            NoteIdentity note,
            Budget budget) {
        if (gray == null
                || labels == null
                || gray.length != (long) width * height
                || labels.length != gray.length
                || width <= 0
                || height <= 0
                || note == null)
            return new HeadAssessment(
                    HeadDecision.INVALID_INPUT, null, 0, 0, 0, 0, 0, -1, -1, -1, -1);
        int margin = Math.max(2, (int) Math.ceil(note.staffGap * .6f));
        int left = Math.max(0, (int) Math.floor(note.centerX - note.xError) - margin);
        int right = Math.min(width - 1, (int) Math.ceil(note.centerX + note.xError) + margin);
        int top = Math.max(0, (int) Math.floor(note.centerY - note.yError) - margin);
        int bottom = Math.min(height - 1, (int) Math.ceil(note.centerY + note.yError) + margin);
        Box roi = new Box(left, top, right, bottom);
        int localArea = area(roi);
        if (localArea > MAX_LOCAL_AREA)
            return new HeadAssessment(
                    HeadDecision.HEAD_SHAPE_NOT_PROVEN, null, 0, 0, 0, 0, 0, -1, -1, -1, -1);
        reserve(budget, localArea * 10L, localArea * 20L);
        byte[] seen = new byte[localArea];
        int[] queue = new int[localArea];
        List<Head> matches = new ArrayList<>();
        int labeled = 0, shapes = 0, bodies = 0, stems = 0, unowned = 0;
        int firstX = -1, firstY = -1, firstGray = -1, firstLabel = -1;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                int local = (y - top) * roi.width() + x - left;
                if (seen[local] != 0 || labels[y * width + x] != headLabel) continue;
                labeled++;
                int n = labelFlood(labels, width, roi, x, y, headLabel, seen, queue, budget);
                if (n < 6) continue;
                Box b = bounds(queue, n, roi.width(), left, top);
                if (b.left == roi.left
                        || b.right == roi.right
                        || b.top == roi.top
                        || b.bottom == roi.bottom) continue;
                float cx = (b.left + b.right) * .5f, cy = (b.top + b.bottom) * .5f;
                if (Math.abs(cx - note.centerX) > note.xError + .5f
                        || Math.abs(cy - note.centerY) > note.yError + .5f) continue;
                if (b.width() > note.staffGap * 1.7f
                        || b.height() > note.staffGap * 1.2f
                        || Math.min(b.width(), b.height()) < 2) continue;
                shapes++;
                reserve(budget, area(b) * 2L, 0);
                int dark = 0, foreground = 0;
                List<Integer> members = new ArrayList<>(n);
                Set<Integer> memberSet = new HashSet<>();
                for (int i = 0; i < n; i++) {
                    int px = left + queue[i] % roi.width(), py = top + queue[i] / roi.width();
                    int value = gray[py * width + px] & 255;
                    if (value < 245) foreground++;
                    if (value < 185) dark++;
                    members.add(py * width + px);
                    memberSet.add(py * width + px);
                }
                if (foreground < Math.ceil(n * .70) || dark < Math.max(3, Math.ceil(n * .18)))
                    continue;
                bodies++;
                Set<Integer> seedStem =
                        rawStem(gray, width, height, b, memberSet, note.staffGap, budget);
                if (seedStem.isEmpty()) continue;
                Set<Integer> headBody =
                        completeRawHeadContour(
                                gray, width, b, memberSet, seedStem, note.staffGap, budget);
                if (headBody.isEmpty()) {
                    // A failed contour must retain the dark residual as an explicit refusal.
                    // Do not let an unsuccessful reconstruction hide foreign foreground.
                    for (int yy = b.top; yy <= b.bottom; yy++)
                        for (int xx = b.left; xx <= b.right; xx++) {
                            reserve(budget, 1, 0);
                            int p = yy * width + xx;
                            if ((gray[p] & 255) >= 185
                                    || memberSet.contains(p)
                                    || seedStem.contains(p)
                                    || nearStemAtRow(seedStem, xx, yy, width)
                                    || provedTwoSidedLedgerContinuation(
                                            gray,
                                            labels,
                                            width,
                                            height,
                                            b,
                                            memberSet,
                                            seedStem,
                                            xx,
                                            yy,
                                            note.staffGap,
                                            budget)) continue;
                            unowned++;
                            if (firstX < 0) {
                                firstX = xx;
                                firstY = yy;
                                firstGray = gray[p] & 255;
                                firstLabel = labels[p] & 255;
                            }
                        }
                    continue;
                }
                Set<Integer> stemPixels =
                        completeAttachedShaft(
                                gray,
                                width,
                                height,
                                b,
                                headBody,
                                seedStem,
                                note.staffGap,
                                note.staffTop,
                                budget);
                if (stemPixels.isEmpty()) continue;
                stems++;
                int candidateUnowned = 0, candidateFirstX = -1, candidateFirstY = -1;
                int candidateFirstGray = -1, candidateFirstLabel = -1;
                for (int yy = b.top; yy <= b.bottom; yy++)
                    for (int xx = b.left; xx <= b.right; xx++) {
                        int p = yy * width + xx;
                        if ((gray[p] & 255) < 185
                                && !headBody.contains(p)
                                && !stemPixels.contains(p)
                                && !nearStemAtRow(stemPixels, xx, yy, width)
                                && !provedTwoSidedLedgerContinuation(
                                        gray,
                                        labels,
                                        width,
                                        height,
                                        b,
                                        memberSet,
                                        stemPixels,
                                        xx,
                                        yy,
                                        note.staffGap,
                                        budget)) {
                            candidateUnowned++;
                            if (candidateFirstX < 0) {
                                candidateFirstX = xx;
                                candidateFirstY = yy;
                                candidateFirstGray = gray[p] & 255;
                                candidateFirstLabel = labels[p] & 255;
                            }
                        }
                    }
                if (candidateUnowned == 0) matches.add(new Head(b, members, dark, true));
                else {
                    unowned += candidateUnowned;
                    if (firstX < 0) {
                        firstX = candidateFirstX;
                        firstY = candidateFirstY;
                        firstGray = candidateFirstGray;
                        firstLabel = candidateFirstLabel;
                    }
                }
            }
        if (matches.size() == 1)
            return new HeadAssessment(
                    HeadDecision.OWNED,
                    matches.get(0),
                    labeled,
                    shapes,
                    bodies,
                    stems,
                    0,
                    -1,
                    -1,
                    -1,
                    -1);
        if (matches.size() > 1)
            return new HeadAssessment(
                    HeadDecision.AMBIGUOUS_HEAD_COMPONENTS,
                    null,
                    labeled,
                    shapes,
                    bodies,
                    stems,
                    unowned,
                    firstX,
                    firstY,
                    firstGray,
                    firstLabel);
        HeadDecision decision =
                unowned > 0
                        ? HeadDecision.UNOWNED_DARK_IN_HEAD_BOUNDS
                        : (bodies > 0
                                ? HeadDecision.ATTACHED_STEM_NOT_PROVEN
                                : (shapes > 0
                                        ? HeadDecision.HEAD_RAW_FILL_NOT_PROVEN
                                        : (labeled > 0
                                                ? HeadDecision.HEAD_SHAPE_NOT_PROVEN
                                                : HeadDecision.NO_LABELED_COMPONENT)));
        return new HeadAssessment(
                decision,
                null,
                labeled,
                shapes,
                bodies,
                stems,
                unowned,
                firstX,
                firstY,
                firstGray,
                firstLabel);
    }

    public static Head rawHeadAndStem(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            byte headLabel,
            NoteIdentity note,
            Budget budget) {
        return assessRawHeadAndStem(gray, labels, width, height, headLabel, note, budget).head();
    }

    public enum OwnerDecision {
        OWNED,
        INVALID_INPUT,
        TEXT_OVERLAP,
        DUPLICATE_NOTE_ID,
        NO_GEOMETRIC_OWNER,
        AMBIGUOUS_GEOMETRIC_OWNER,
        RAW_HEAD_OR_STEM_NOT_PROVEN,
        RAW_HEAD_NO_LABEL,
        RAW_HEAD_SHAPE_UNPROVEN,
        RAW_HEAD_FILL_UNPROVEN,
        RAW_ATTACHED_STEM_UNPROVEN,
        RAW_UNOWNED_DARK,
        RAW_AMBIGUOUS_HEADS
    }

    public record OwnerAssessment(
            OwnerDecision decision, OwnedRing owned, HeadAssessment headAssessment) {
        public OwnerAssessment(OwnerDecision decision, OwnedRing owned) {
            this(decision, owned, null);
        }

        public OwnerAssessment {
            if (decision == null || (decision == OwnerDecision.OWNED) != (owned != null))
                throw new IllegalArgumentException("Inconsistent owner assessment");
        }
    }

    /**
     * Unique spatial relation plus full raw proof; decision reports the first conservative refusal.
     */
    public static OwnerAssessment assessOwner(
            Ring ring,
            List<NoteIdentity> notes,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            byte headLabel,
            List<Box> excludedText,
            Budget budget) {
        if (ring == null || notes == null || notes.isEmpty() || excludedText == null)
            return new OwnerAssessment(OwnerDecision.INVALID_INPUT, null);
        for (Box text : excludedText)
            if (text != null && intersects(ring.bounds, text))
                return new OwnerAssessment(OwnerDecision.TEXT_OVERLAP, null);
        Set<String> ids = new HashSet<>();
        List<NoteIdentity> geometric = new ArrayList<>();
        for (NoteIdentity n : notes) {
            if (n == null || !ids.add(n.target))
                return new OwnerAssessment(OwnerDecision.DUPLICATE_NOTE_ID, null);
            reserve(budget, 1, 0);
            float dx = Math.abs(ring.centerX - n.centerX) / n.staffGap;
            float dy = Math.abs(ring.centerY - n.centerY) / n.staffGap;
            if (dx <= .5f && dy >= .5f && dy <= 4f) geometric.add(n);
        }
        if (geometric.isEmpty()) return new OwnerAssessment(OwnerDecision.NO_GEOMETRIC_OWNER, null);
        if (geometric.size() != 1)
            return new OwnerAssessment(OwnerDecision.AMBIGUOUS_GEOMETRIC_OWNER, null);
        NoteIdentity owner = geometric.get(0);
        HeadAssessment headAssessment =
                assessRawHeadAndStem(gray, labels, width, height, headLabel, owner, budget);
        if (headAssessment.head() == null) {
            OwnerDecision decision =
                    switch (headAssessment.decision()) {
                        case NO_LABELED_COMPONENT -> OwnerDecision.RAW_HEAD_NO_LABEL;
                        case HEAD_SHAPE_NOT_PROVEN -> OwnerDecision.RAW_HEAD_SHAPE_UNPROVEN;
                        case HEAD_RAW_FILL_NOT_PROVEN -> OwnerDecision.RAW_HEAD_FILL_UNPROVEN;
                        case ATTACHED_STEM_NOT_PROVEN -> OwnerDecision.RAW_ATTACHED_STEM_UNPROVEN;
                        case UNOWNED_DARK_IN_HEAD_BOUNDS -> OwnerDecision.RAW_UNOWNED_DARK;
                        case AMBIGUOUS_HEAD_COMPONENTS -> OwnerDecision.RAW_AMBIGUOUS_HEADS;
                        default -> OwnerDecision.RAW_HEAD_OR_STEM_NOT_PROVEN;
                    };
            return new OwnerAssessment(decision, null, headAssessment);
        }
        return new OwnerAssessment(
                OwnerDecision.OWNED,
                new OwnedRing(ring, headAssessment.head(), owner),
                headAssessment);
    }

    /** Compatibility projection for callers that need only the proof object. */
    public static OwnedRing uniqueOwner(
            Ring ring,
            List<NoteIdentity> notes,
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            byte headLabel,
            List<Box> excludedText,
            Budget budget) {
        return assessOwner(
                        ring, notes, gray, labels, width, height, headLabel, excludedText, budget)
                .owned();
    }

    private static Set<Integer> rawStem(
            byte[] gray,
            int width,
            int height,
            Box head,
            Set<Integer> memberSet,
            float gap,
            Budget budget) {
        int length = Math.max(3, (int) Math.ceil(gap * 2.5f));
        int minimumOutside = Math.max(2, (int) Math.ceil(gap * .6f));
        int radius = Math.max(1, (int) Math.floor(gap * .12f));
        reserve(
                budget,
                (long) (head.width() + 2) * (head.height() + 1) * 18,
                (long) memberSet.size() * 16);
        Set<Integer> best = Set.of();
        int bestOutside = -1;
        for (int side : new int[] {-1, 1})
            for (int x = head.left - 1; x <= head.right + 1; x++) {
                for (int attachY = head.top; attachY <= head.bottom; attachY++) {
                    boolean attached = false;
                    for (int yy = Math.max(head.top, attachY - 1);
                            yy <= Math.min(head.bottom, attachY + 1);
                            yy++)
                        for (int xx = Math.max(head.left, x - 1);
                                xx <= Math.min(head.right, x + 1);
                                xx++)
                            if (memberSet.contains(yy * width + xx)
                                    && (gray[yy * width + xx] & 255) < 185) attached = true;
                    if (!attached || (gray[attachY * width + x] & 255) >= 185) continue;
                    boolean all = true;
                    int outside = 0;
                    int previousX = x;
                    List<Integer> path = new ArrayList<>();
                    reserve(budget, 0, (long) (length + 1) * Integer.BYTES);
                    path.add(attachY * width + x);
                    for (int step = 1; step <= length; step++) {
                        int y = attachY + side * step;
                        if (y < 0 || y >= height) {
                            all = false;
                            break;
                        }
                        reserve(budget, radius * 2L + 1, 0);
                        int found = -1;
                        for (int offset = 0; offset <= radius && found < 0; offset++) {
                            int left = previousX - offset, right = previousX + offset;
                            if (left >= 0 && (gray[y * width + left] & 255) < 185) found = left;
                            if (found < 0
                                    && offset > 0
                                    && right < width
                                    && (gray[y * width + right] & 255) < 185) found = right;
                        }
                        if (found < 0 || Math.abs(found - previousX) > 1) {
                            all = false;
                            break;
                        }
                        previousX = found;
                        path.add(y * width + found);
                        if (y < head.top || y > head.bottom) outside++;
                    }
                    // A naturally ending shaft may stop before the bounded 2.5-gap probe
                    // interval. Retain only its longest uninterrupted raw path; never bridge
                    // a paper gap or jump across a horizontal rail.
                    int requiredOutside =
                            Math.max(minimumOutside, (int) Math.ceil((path.size() - 1) * .6f));
                    if (outside >= requiredOutside
                            && (outside > bestOutside
                                    || (outside == bestOutside
                                            && (best.isEmpty() || path.size() < best.size())))) {
                        bestOutside = outside;
                        best = new HashSet<>(path);
                    }
                }
            }
        return best;
    }

    /**
     * A ledger-line pixel is removable only as part of a two-sided raw horizontal rule continuing
     * beyond the exact head on both sides, with the attached shaft excluded.
     */
    private static boolean provedTwoSidedLedgerContinuation(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            Box head,
            Set<Integer> members,
            Set<Integer> stem,
            int x,
            int y,
            float gap,
            Budget budget) {
        if (x < head.left || x > head.right || y < head.top || y > head.bottom) return false;
        return twoSidedRuleRow(gray, width, head, stem, y, gap, budget)
                && ((y > head.top && twoSidedRuleRow(gray, width, head, stem, y - 1, gap, budget))
                        || (y < head.bottom
                                && twoSidedRuleRow(gray, width, head, stem, y + 1, gap, budget)));
    }

    private static boolean twoSidedRuleRow(
            byte[] gray, int width, Box head, Set<Integer> stem, int y, float gap, Budget budget) {
        if (y < head.top || y > head.bottom) return false;
        int reach = Math.max(2, (int) Math.ceil(gap * .22f));
        int left = 0, right = 0;
        for (int step = 1; step <= reach; step++) {
            int lx = head.left - step, rx = head.right + step;
            if (lx < 0 || rx >= width) return false;
            reserve(budget, 2, 0);
            if ((gray[y * width + lx] & 255) >= 185 || stem.contains(y * width + lx)) return false;
            if ((gray[y * width + rx] & 255) >= 185 || stem.contains(y * width + rx)) return false;
            left++;
            right++;
        }
        if (left < reach || right < reach) return false;
        // A paper gap inside the head interval breaks an otherwise claimed rule.
        for (int xx = head.left; xx <= head.right; xx++) {
            reserve(budget, 1, 0);
            int p = y * width + xx;
            if ((gray[p] & 255) >= 245 && !stem.contains(p)) return false;
        }
        return true;
    }

    /**
     * Reconstruct a complete filled head silhouette from full horizontal raw cross-sections.
     * Semantic head pixels anchor the contour; unlabeled raw pixels count only when the entire row
     * profile is single-span, convex and monotone through its endpoints.
     */
    private static Set<Integer> completeRawHeadContour(
            byte[] gray,
            int width,
            Box head,
            Set<Integer> members,
            Set<Integer> shaftSeed,
            float gap,
            Budget budget) {
        int rows = head.height(), cols = head.width();
        reserve(budget, (long) rows * cols * 3, (long) rows * 12);
        int[] left = new int[rows], right = new int[rows], widths = new int[rows];
        Arrays.fill(left, Integer.MAX_VALUE);
        Arrays.fill(right, -1);
        boolean[] ledgerRow = new boolean[rows];
        for (int r = 0; r < rows; r++) {
            int y = head.top + r;
            ledgerRow[r] =
                    twoSidedRuleRow(gray, width, head, shaftSeed, y, gap, budget)
                            && ((y > head.top
                                            && twoSidedRuleRow(
                                                    gray, width, head, shaftSeed, y - 1, gap,
                                                    budget))
                                    || (y < head.bottom
                                            && twoSidedRuleRow(
                                                    gray, width, head, shaftSeed, y + 1, gap,
                                                    budget)));
            for (int x = head.left; x <= head.right; x++) {
                int p = y * width + x;
                // A fully witnessed two-sided ledger contact may bridge the body's
                // column profile for this topology check; those pixels remain separately
                // typed as rule contact and are not added to the returned head body.
                boolean foreground =
                        members.contains(p)
                                || ((gray[p] & 255) < 245
                                        && (ledgerRow[r]
                                                || !nearStemAtRow(shaftSeed, x, y, width)));
                if (foreground) {
                    left[r] = Math.min(left[r], x);
                    right[r] = Math.max(right[r], x);
                }
            }
            if (right[r] < left[r]) return Set.of();
            widths[r] = right[r] - left[r] + 1;
            for (int x = left[r]; x <= right[r]; x++) {
                reserve(budget, 1, 0);
                int p = y * width + x;
                if (!members.contains(p) && (gray[p] & 255) >= 245) return Set.of();
            }
        }
        // A tilted oval's left and right edges reach their extrema on different rows. A
        // verified ledger crossing can also narrow only its own semantic rows. Check each
        // independently observed non-ledger edge through its own turn instead of forcing a
        // single width peak to govern both edges.
        int[] outlineRows = new int[rows];
        int outlineCount = 0;
        for (int r = 0; r < rows; r++) if (!ledgerRow[r]) outlineRows[outlineCount++] = r;
        if (outlineCount < 3) return Set.of();
        int leftTurn = 0, rightTurn = 0;
        for (int i = 1; i < outlineCount; i++) {
            int r = outlineRows[i];
            if (left[r] < left[outlineRows[leftTurn]]) leftTurn = i;
            if (right[r] > right[outlineRows[rightTurn]]) rightTurn = i;
        }
        int maxEdgeStep = (int) Math.ceil(gap * .4f);
        for (int i = 1; i < outlineCount; i++) {
            reserve(budget, 5, 0);
            int prev = outlineRows[i - 1], cur = outlineRows[i];
            int skipped = cur - prev;
            if (Math.abs(left[cur] - left[prev]) > (long) maxEdgeStep * skipped
                    || Math.abs(right[cur] - right[prev]) > (long) maxEdgeStep * skipped)
                return Set.of();
            if (i <= leftTurn && left[cur] > left[prev]) return Set.of();
            if (i > leftTurn && left[cur] < left[prev]) return Set.of();
            if (i <= rightTurn && right[cur] < right[prev]) return Set.of();
            if (i > rightTurn && right[cur] > right[prev]) return Set.of();
        }
        // Row intervals alone admit a connected spur on a single row. Require the same
        // complete silhouette to be column-convex after independently proved ledger rows
        // and the attached shaft are removed from the head profile.
        for (int x = head.left; x <= head.right; x++) {
            boolean entered = false, exited = false;
            for (int r = 0; r < rows; r++) {
                reserve(budget, 1, 0);
                int y = head.top + r, p = y * width + x;
                // A fully witnessed two-sided ledger contact may bridge the body's
                // column profile for this topology check; those pixels remain separately
                // typed as rule contact and are not added to the returned head body.
                boolean foreground =
                        members.contains(p)
                                || ((gray[p] & 255) < 245
                                        && (ledgerRow[r]
                                                || !nearStemAtRow(shaftSeed, x, y, width)));
                if (foreground) {
                    if (exited) return Set.of();
                    entered = true;
                } else if (entered) exited = true;
            }
        }
        Set<Integer> body = new HashSet<>();
        for (int r = 0; r < rows; r++)
            for (int x = left[r]; x <= right[r]; x++) {
                reserve(budget, 1, 0);
                int p = (head.top + r) * width + x;
                if (members.contains(p)
                        || (!ledgerRow[r]
                                && (gray[p] & 255) < 245
                                && !nearStemAtRow(shaftSeed, x, head.top + r, width))) body.add(p);
            }
        if (!body.containsAll(members)) return Set.of();
        return Set.copyOf(body);
    }

    /** Validate the bounded attached raw shaft path without flooding into connected staff rules. */
    private static Set<Integer> completeAttachedShaft(
            byte[] gray,
            int width,
            int height,
            Box head,
            Set<Integer> body,
            Set<Integer> seed,
            float gap,
            float coarseStaffTop,
            Budget budget) {
        int maxWidth = Math.max(2, (int) Math.ceil(gap * .22f));
        if (seed == null || seed.isEmpty()) return Set.of();
        reserve(budget, (long) seed.size() * (2L * maxWidth + 4), (long) seed.size() * 8);
        List<Integer> path = new ArrayList<>(seed);
        path.sort(Integer::compareTo);
        int ownerLeft = head.left, ownerRight = head.right;
        for (int p : path) {
            // The seed is a centerline. Keep clear witnesses outside its complete bounded
            // raw-width envelope so a shaft edge cannot contaminate a five-rail profile.
            ownerLeft = Math.min(ownerLeft, p % width - maxWidth);
            ownerRight = Math.max(ownerRight, p % width + maxWidth);
        }
        ownerLeft = Math.max(0, ownerLeft);
        ownerRight = Math.min(width - 1, ownerRight);
        List<RuleRun> wideRuns = new ArrayList<>();
        List<RuleRun> independentlyProvedStaffRuns = new ArrayList<>();
        int outside = 0, previousY = -1, previousX = -1;
        boolean touchesBody = false;
        for (int p : path) {
            int x = p % width, y = p / width;
            if ((gray[p] & 255) >= 185 || y < 0 || y >= height) return Set.of();
            if (previousY >= 0 && (y != previousY + 1 || Math.abs(x - previousX) > maxWidth))
                return Set.of();
            if (y < head.top || y > head.bottom) {
                outside++;
                RuleRun wide =
                        completeFiveRailCrossing(
                                gray,
                                width,
                                height,
                                ownerLeft,
                                ownerRight,
                                y,
                                coarseStaffTop,
                                gap,
                                budget);
                if (wide != null) {
                    independentlyProvedStaffRuns.add(wide);
                    previousY = y;
                    previousX = x;
                    continue;
                }
                wide = twoSidedRuleRun(gray, width, height, x, y, gap, budget);
                if (wide != null) {
                    wideRuns.add(wide);
                    previousY = y;
                    previousX = x;
                    continue;
                }
                int lo = Math.max(0, x - maxWidth), hi = Math.min(width - 1, x + maxWidth);
                int first = -1, last = -1;
                for (int xx = lo; xx <= hi; xx++) {
                    reserve(budget, 1, 0);
                    if ((gray[y * width + xx] & 255) < 185) {
                        if (first < 0) first = xx;
                        last = xx;
                    }
                }
                if (first < 0 || last - first + 1 > maxWidth) return Set.of();
                for (int xx = first; xx <= last; xx++) {
                    reserve(budget, 1, 0);
                    if ((gray[y * width + xx] & 255) >= 185) return Set.of();
                }
            } else if (body.contains(p)) {
                touchesBody = true;
            } else {
                for (int yy = Math.max(head.top, y - 1); yy <= Math.min(head.bottom, y + 1); yy++)
                    for (int xx = Math.max(head.left, x - 1);
                            xx <= Math.min(head.right, x + 1);
                            xx++) if (body.contains(yy * width + xx)) touchesBody = true;
            }
            previousY = y;
            previousX = x;
        }
        if (!touchesBody || outside < Math.ceil((path.size() - 1) * .6f)) return Set.of();
        if (!coherentStaffRuleCrossings(wideRuns, gap, budget)) return Set.of();
        // Independently coherent broad rule families may coexist with the measured staff crossing.
        return Set.copyOf(path);
    }

    /**
     * Prove a staff crossing from five independently observed literal raw profiles. The coarse
     * Staff tuple only bounds where to look. Matching profiles must be witnessed for two gaps on
     * both sides of the exact head span, then the candidate rail must remain raw-dark across that
     * entire measured corridor. No missing pixel is filled or removed.
     */
    private static RuleRun completeFiveRailCrossing(
            byte[] gray,
            int width,
            int height,
            int ownerLeft,
            int ownerRight,
            int y,
            float coarseTop,
            float gap,
            Budget budget) {
        if (!Float.isFinite(coarseTop) || gap <= 0 || y < 0 || y >= height) return null;
        int witness = Math.max(1, (int) Math.ceil(2 * gap));
        int leftEnd = ownerLeft - 1, leftStart = leftEnd - witness + 1;
        int rightStart = ownerRight + 1, rightEnd = rightStart + witness - 1;
        if (leftStart < 0 || rightEnd >= width) return null;
        int[] leftFrame = null, rightFrame = null;
        for (int x = leftStart; x <= leftEnd; x++) {
            int[] frame = literalFiveRailFrame(gray, width, height, x, coarseTop, gap, budget);
            if (frame == null) return null;
            if (leftFrame == null) leftFrame = frame;
            else if (!Arrays.equals(leftFrame, frame)) return null;
        }
        for (int x = rightStart; x <= rightEnd; x++) {
            int[] frame = literalFiveRailFrame(gray, width, height, x, coarseTop, gap, budget);
            if (frame == null) return null;
            if (rightFrame == null) rightFrame = frame;
            else if (!Arrays.equals(rightFrame, frame)) return null;
        }
        if (!Arrays.equals(leftFrame, rightFrame)) return null;
        int railTop = -1, railBottom = -1;
        for (int r = 0; r < 5; r++) {
            if (y >= leftFrame[2 * r] && y <= leftFrame[2 * r + 1]) {
                if (railTop >= 0) return null;
                railTop = leftFrame[2 * r];
                railBottom = leftFrame[2 * r + 1];
            }
        }
        if (railTop < 0) return null;
        // Confirm unbroken actual ink through both clear witness windows and the owner span.
        for (int x = leftStart; x <= rightEnd; x++) {
            budget.take(1, 0);
            if ((gray[y * width + x] & 255) >= 185) return null;
        }
        return new RuleRun(y, leftStart, rightEnd);
    }

    /** Returns exact [first,last] raw row bounds for all five Staff-tuple-bounded rails. */
    private static int[] literalFiveRailFrame(
            byte[] gray, int width, int height, int x, float coarseTop, float gap, Budget budget) {
        budget.take(0, 10L * Integer.BYTES);
        int[] frame = new int[10];
        int maxThickness = Math.max(2, (int) Math.ceil(.35f * gap));
        for (int rail = 0; rail < 5; rail++) {
            float expected = coarseTop + rail * gap;
            int lo = Math.max(0, (int) Math.floor(expected - .30f * gap));
            int hi = Math.min(height - 1, (int) Math.ceil(expected + .30f * gap));
            int first = -1, last = -1, runs = 0;
            boolean active = false;
            for (int row = lo; row <= hi; row++) {
                budget.take(1, 0);
                boolean ink = (gray[row * width + x] & 255) < 185;
                if (ink && !active) {
                    first = row;
                    active = true;
                    runs++;
                }
                if (!ink && active) {
                    last = row - 1;
                    active = false;
                }
            }
            if (active) last = hi;
            if (runs != 1
                    || first < 0
                    || last < first
                    || (first == lo && lo > 0 && (gray[(lo - 1) * width + x] & 255) < 185)
                    || (last == hi && hi + 1 < height && (gray[(hi + 1) * width + x] & 255) < 185)
                    || last - first + 1 > maxThickness
                    || Math.abs(((first + last) * .5f) - expected) > .22f * gap) return null;
            if (rail > 0) {
                float priorCenter = (frame[2 * (rail - 1)] + frame[2 * (rail - 1) + 1]) * .5f;
                float center = (first + last) * .5f;
                if (Math.abs((center - priorCenter) - gap) > .12f * gap) return null;
            }
            frame[2 * rail] = first;
            frame[2 * rail + 1] = last;
        }
        return frame;
    }

    /** A broad shaft-row run is a rule contact only when it crosses the shaft on both sides. */
    private static RuleRun twoSidedRuleRun(
            byte[] gray, int width, int height, int x, int y, float gap, Budget budget) {
        if (x < 0 || x >= width || y < 0 || y >= height || (gray[y * width + x] & 255) >= 185)
            return null;
        int left = x, right = x;
        while (left > 0 && (gray[y * width + left - 1] & 255) < 185) {
            reserve(budget, 1, 0);
            left--;
        }
        while (right + 1 < width && (gray[y * width + right + 1] & 255) < 185) {
            reserve(budget, 1, 0);
            right++;
        }
        int widthPx = right - left + 1;
        int maxShaftWidth = Math.max(2, (int) Math.ceil(gap * .22f));
        if (widthPx <= maxShaftWidth) return null;
        int minSide = Math.max(1, (int) Math.ceil(gap * .12f));
        int maxRuleSpan = Math.max(maxShaftWidth + 2 * minSide, (int) Math.ceil(gap * 4f));
        if (x - left < minSide || right - x < minSide || widthPx > maxRuleSpan) return null;
        return new RuleRun(y, left, right);
    }

    /** Require every broad contact to be part of the same repeated staff-rule family. */
    private static boolean coherentStaffRuleCrossings(
            List<RuleRun> rows, float gap, Budget budget) {
        if (rows.isEmpty()) return true;
        int maxThickness = Math.max(2, (int) Math.ceil(gap * .22f));
        int maxDrift = Math.max(1, (int) Math.ceil(gap * .12f));
        List<RuleBand> bands = new ArrayList<>();
        int top = rows.get(0).y(),
                bottom = top,
                left = rows.get(0).left(),
                right = rows.get(0).right();
        for (int i = 1; i <= rows.size(); i++) {
            RuleRun next = i < rows.size() ? rows.get(i) : null;
            if (next != null
                    && next.y() == bottom + 1
                    && Math.abs(next.left() - left) <= maxDrift
                    && Math.abs(next.right() - right) <= maxDrift) {
                bottom = next.y();
                continue;
            }
            if (bottom - top + 1 > maxThickness) return false;
            bands.add(new RuleBand(top, bottom, left, right));
            if (next != null) {
                top = bottom = next.y();
                left = next.left();
                right = next.right();
            }
        }
        if (bands.size() < 2) return false;
        boolean[] paired = new boolean[bands.size()];
        for (int i = 0; i < bands.size(); i++)
            for (int j = i + 1; j < bands.size(); j++) {
                reserve(budget, 1, 0);
                RuleBand a = bands.get(i), b = bands.get(j);
                double delta = (b.top() + b.bottom() - a.top() - a.bottom()) / 2.0;
                int multiples = (int) Math.round(delta / gap);
                if (multiples >= 1
                        && Math.abs(delta - multiples * gap) <= maxDrift
                        && Math.abs(a.left() - b.left()) <= maxDrift
                        && Math.abs(a.right() - b.right()) <= maxDrift) {
                    paired[i] = true;
                    paired[j] = true;
                }
            }
        for (boolean value : paired) if (!value) return false;
        return true;
    }

    private static int flood(
            byte[] gray, int width, Box roi, int x, int y, int cutoff, byte[] seen, int[] queue) {
        int w = roi.width(), start = (y - roi.top) * w + x - roi.left, count = 1;
        queue[0] = start;
        seen[start] = 1;
        for (int head = 0; head < count; head++) {
            int cur = queue[head], cx = cur % w, cy = cur / w;
            for (int yy = Math.max(0, cy - 1); yy <= Math.min(roi.height() - 1, cy + 1); yy++)
                for (int xx = Math.max(0, cx - 1); xx <= Math.min(w - 1, cx + 1); xx++) {
                    int j = yy * w + xx;
                    if (seen[j] == 0
                            && (gray[(roi.top + yy) * width + roi.left + xx] & 255) < cutoff) {
                        seen[j] = 1;
                        queue[count++] = j;
                    }
                }
        }
        return count;
    }

    private static int labelFlood(
            byte[] labels,
            int width,
            Box roi,
            int x,
            int y,
            byte label,
            byte[] seen,
            int[] queue,
            Budget budget) {
        int w = roi.width(), start = (y - roi.top) * w + x - roi.left, count = 1;
        queue[0] = start;
        seen[start] = 1;
        for (int head = 0; head < count; head++) {
            reserve(budget, 1, 0);
            int cur = queue[head], cx = cur % w, cy = cur / w;
            for (int yy = Math.max(0, cy - 1); yy <= Math.min(roi.height() - 1, cy + 1); yy++)
                for (int xx = Math.max(0, cx - 1); xx <= Math.min(w - 1, cx + 1); xx++) {
                    int j = yy * w + xx;
                    if (seen[j] == 0 && labels[(roi.top + yy) * width + roi.left + xx] == label) {
                        seen[j] = 1;
                        queue[count++] = j;
                    }
                }
        }
        return count;
    }

    private static int enclosedBackground(
            byte[] gray, int width, Box box, int cutoff, Budget budget) {
        int area = area(box);
        reserve(budget, area * 8L, area);
        int w = box.width(), h = box.height();
        byte[] outside = new byte[area];
        int[] queue = new int[area];
        int size = 0;
        for (int x = 0; x < w; x++) {
            size = enqueueBackground(x, gray, width, box, cutoff, queue, outside, size);
            size =
                    enqueueBackground(
                            (h - 1) * w + x, gray, width, box, cutoff, queue, outside, size);
        }
        for (int y = 1; y + 1 < h; y++) {
            size = enqueueBackground(y * w, gray, width, box, cutoff, queue, outside, size);
            size = enqueueBackground(y * w + w - 1, gray, width, box, cutoff, queue, outside, size);
        }
        int read = 0;
        while (read < size) {
            int i = queue[read++], x = i % w, y = i / w;
            if ((gray[(box.top + y) * width + box.left + x] & 255) < cutoff) continue;
            if (x > 0)
                size = enqueueBackground(i - 1, gray, width, box, cutoff, queue, outside, size);
            if (x + 1 < w)
                size = enqueueBackground(i + 1, gray, width, box, cutoff, queue, outside, size);
            if (y > 0)
                size = enqueueBackground(i - w, gray, width, box, cutoff, queue, outside, size);
            if (y + 1 < h)
                size = enqueueBackground(i + w, gray, width, box, cutoff, queue, outside, size);
        }
        int holes = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (outside[i] == 0 && (gray[(box.top + y) * width + box.left + x] & 255) >= cutoff)
                    holes++;
            }
        return holes;
    }

    private static Box bounds(int[] points, int count, int stride, int left, int top) {
        int minX = Integer.MAX_VALUE, maxX = -1, minY = Integer.MAX_VALUE, maxY = -1;
        for (int i = 0; i < count; i++) {
            int x = left + points[i] % stride, y = top + points[i] / stride;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        return new Box(minX, minY, maxX, maxY);
    }

    private static int area(Box b) {
        return Math.multiplyExact(b.width(), b.height());
    }

    private static boolean intersects(Box a, Box b) {
        return a.left <= b.right && b.left <= a.right && a.top <= b.bottom && b.top <= a.bottom;
    }

    private static boolean nearStemAtRow(Set<Integer> stem, int x, int y, int width) {
        return stem.contains(y * width + x - 1)
                || stem.contains(y * width + x + 1)
                || stem.contains(y * width + x - width - 1)
                || stem.contains(y * width + x - width)
                || stem.contains(y * width + x - width + 1);
    }

    private static void checkImage(byte[] gray, int width, int height, Box box) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || box == null
                || box.right >= width
                || box.bottom >= height
                || area(box) > MAX_LOCAL_AREA)
            throw new IllegalArgumentException("Invalid or unbounded image region");
    }

    private static void reserve(Budget budget, long work, long bytes) {
        if (budget == null)
            throw new IllegalArgumentException("Natural-harmonic proof budget exhausted");
        budget.take(work, bytes);
    }

    private static int enqueueBackground(
            int index,
            byte[] gray,
            int width,
            Box box,
            int cutoff,
            int[] queue,
            byte[] queued,
            int at) {
        if (queued[index] != 0
                || (gray[(box.top + index / box.width()) * width + box.left + index % box.width()]
                                & 255)
                        < cutoff) return at;
        queued[index] = 1;
        if (at >= queue.length)
            throw new IllegalStateException("background traversal exceeded local bound");
        queue[at++] = index;
        return at;
    }
}
