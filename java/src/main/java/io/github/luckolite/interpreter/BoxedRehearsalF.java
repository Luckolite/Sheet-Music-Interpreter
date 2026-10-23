// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Conservative finite-frame ownership in the existing less-than-145 symbol graph. */
final class BoxedRehearsalF {
    private BoxedRehearsalF() {}

    static boolean proved(
            PlayingTechniqueDetector.Word word,
            PlayingTechniqueDetector.Staff owner,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height) {
        if (provedForOwner(word, owner, staffs, gray, width, height)) return true;
        if (!valid(owner) || staffs == null) return false;
        // A dynamic floor can point to the preceding row. Rehearsal identity is
        // instead proved by the complete finite frame and its unique printed row.
        // Each alternate must be the same part; all original competing-row,
        // whole-frame, whole-glyph, paper and capital-letter proofs still apply.
        for (var staff : staffs) {
            if (staff == owner
                    || !valid(staff)
                    || staff.index() != owner.index()
                    || staff.count() != owner.count()) continue;
            if (provedForOwner(word, staff, staffs, gray, width, height)) return true;
        }
        return false;
    }

    private static boolean provedForOwner(
            PlayingTechniqueDetector.Word word,
            PlayingTechniqueDetector.Staff owner,
            List<PlayingTechniqueDetector.Staff> staffs,
            byte[] gray,
            int width,
            int height) {
        if (word == null
                || owner == null
                || staffs == null
                || gray == null
                || word.text() == null
                || !word.text().trim().equalsIgnoreCase("f")
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || !finite(word.left(), word.top(), word.right(), word.bottom())
                || word.left() < 0
                || word.top() < 0
                || word.right() > 1
                || word.bottom() > 1
                || word.left() >= word.right()
                || word.top() >= word.bottom()
                || !valid(owner)) return false;
        // Refined scalar owners are not assumed to be the original printed-staff object.
        // Reciprocal midpoint containment and exact part indices must identify ONE staff.
        PlayingTechniqueDetector.Staff printed = null;
        for (var staff : staffs) {
            if (!valid(staff) || staff.index() != owner.index() || staff.count() != owner.count())
                continue;
            float a = (staff.top() + staff.bottom()) * .5f;
            float b = (owner.top() + owner.bottom()) * .5f;
            if (a >= owner.top()
                    && a <= owner.bottom()
                    && b >= staff.top()
                    && b <= staff.bottom()) {
                if (printed != null) return false;
                printed = staff;
            }
        }
        if (printed == null) return false;
        int wx0 = Math.round(word.left() * width), wy0 = Math.round(word.top() * height);
        int wx1 = Math.round(word.right() * width), wy1 = Math.round(word.bottom() * height);
        if (wx0 >= wx1 || wy0 >= wy1) return false;
        // Spatial admission is bounded; touching this domain never proves a finite object.
        int radius = (int) Math.ceil(printed.gap() * 5.5f);
        if (radius <= 0 || radius > 1024) return false;
        int x0 = Math.max(0, wx0 - radius), y0 = Math.max(0, wy0 - radius);
        int x1 = Math.min(width, wx1 + radius), y1 = Math.min(height, wy1 + radius);
        if ((long) (x1 - x0) * (y1 - y0) > 1_000_000L) return false;
        List<Component> components = components(gray, width, x0, y0, x1, y1);
        int proofs = 0;
        for (var frame : components) {
            // OCR rectangles may include paper outside the physical frame. Association
            // is proved below by WHOLE rectangle foreground membership, not a margin.
            if (frame.clipped || frame.bottom >= Math.min(printed.top(), owner.top())) continue;
            // A frame physically nearer a competing printed staff/part is not ours.
            float distance = printed.top() - frame.bottom;
            boolean competing = false;
            for (var staff : staffs) {
                if (staff == printed || !valid(staff)) continue;
                float other =
                        frame.bottom < staff.top()
                                ? staff.top() - frame.bottom
                                : frame.top > staff.bottom() ? frame.top - staff.bottom() : 0;
                if (other <= distance) {
                    competing = true;
                    break;
                }
            }
            if (competing) continue;
            Hole hole = frame.hole(width);
            if (hole == null) continue;
            Component glyph = null;
            boolean unknown = false;
            for (var component : components) {
                if (component == frame) continue;
                boolean inside = false, outside = false;
                for (int pixel : component.pixels) {
                    int x = pixel % width, y = pixel / width;
                    if (hole.contains(x, y)) inside = true;
                    else outside = true;
                }
                if (!inside) continue;
                // No partial component, extra dot, neighbouring m/f, or contact is discarded.
                if (outside || component.clipped || glyph != null) {
                    unknown = true;
                    break;
                }
                glyph = component;
            }
            if (unknown || glyph == null) continue;
            // The entire glyph rectangle must fit the OBSERVED closed hole. This
            // bounds inward side/cap reach by real glyph geometry, not stroke width.
            boolean separatedGlyph = true;
            for (int y = glyph.top; y <= glyph.bottom && separatedGlyph; y++) {
                for (int x = glyph.left; x <= glyph.right; x++) {
                    if (!hole.contains(x, y)) {
                        separatedGlyph = false;
                        break;
                    }
                }
            }
            // Actual paper separation in the unchanged 145 graph: no frame contact,
            // extra glyph attachment, or foreign component is stripped or ignored.
            for (int pixel : glyph.pixels) {
                if (!separatedGlyph) break;
                int x = pixel % width, y = pixel / width;
                for (int dy = -1; dy <= 1 && separatedGlyph; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        int next = ny * width + nx;
                        if (glyph.pixels.contains(next)) continue;
                        if (!hole.contains(nx, ny) || (gray[next] & 255) < 145) {
                            separatedGlyph = false;
                            break;
                        }
                    }
                }
            }
            if (!separatedGlyph) continue;
            boolean overlapsWord = false;
            for (int pixel : glyph.pixels) {
                int x = pixel % width, y = pixel / width;
                if (x >= wx0 && x < wx1 && y >= wy0 && y < wy1) overlapsWord = true;
            }
            if (!overlapsWord) continue;
            // Every foreground pixel in the ENTIRE OCR rectangle must belong to this
            // SAME complete frame or this SAME complete inside glyph. Paper padding
            // is allowed; a separate foreign word, dot, or other ink prevents ownership.
            boolean foreignWordInk = false;
            for (int y = wy0; y < wy1 && !foreignWordInk; y++) {
                for (int x = wx0; x < wx1; x++) {
                    int pixel = y * width + x;
                    if ((gray[pixel] & 255) < 145
                            && !frame.pixels.contains(pixel)
                            && !glyph.pixels.contains(pixel)) {
                        foreignWordInk = true;
                        break;
                    }
                }
            }
            if (foreignWordInk) continue;
            // The complete separately owned glyph is tested, never a searched F-like subcrop.
            // No original capital-F predicate threshold or unboxed caller is changed.
            var complete =
                    new PlayingTechniqueDetector.Word(
                            word.text(),
                            glyph.left / (float) width,
                            glyph.top / (float) height,
                            (glyph.right + 1) / (float) width,
                            (glyph.bottom + 1) / (float) height,
                            word.admission());
            if (ScoreDynamicsDetector.uprightChordF(complete, printed, gray, width, height)
                    || glyph.completeCapitalF(width, printed.gap())) proofs++;
        }
        return proofs == 1;
    }

    private static boolean finite(float... values) {
        for (float value : values) if (!Float.isFinite(value)) return false;
        return true;
    }

    private static boolean valid(PlayingTechniqueDetector.Staff staff) {
        return staff != null
                && finite(staff.top(), staff.bottom(), staff.gap())
                && staff.gap() > 0
                && staff.top() < staff.bottom()
                && staff.count() > 0
                && staff.index() >= 0
                && staff.index() < staff.count();
    }

    private static List<Component> components(
            byte[] gray, int width, int x0, int y0, int x1, int y1) {
        int stride = x1 - x0;
        boolean[] seen = new boolean[stride * (y1 - y0)];
        var result = new ArrayList<Component>();
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int local = (y - y0) * stride + x - x0;
                if (seen[local] || (gray[y * width + x] & 255) >= 145) continue;
                var component = new Component();
                var queue = new ArrayDeque<Integer>();
                seen[local] = true;
                queue.add(y * width + x);
                while (!queue.isEmpty()) {
                    int pixel = queue.removeFirst(), px = pixel % width, py = pixel / width;
                    component.add(pixel, px, py);
                    component.clipped |= px == x0 || px == x1 - 1 || py == y0 || py == y1 - 1;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = px + dx, ny = py + dy;
                            if (nx < x0 || nx >= x1 || ny < y0 || ny >= y1) continue;
                            int next = (ny - y0) * stride + nx - x0;
                            if (!seen[next] && (gray[ny * width + nx] & 255) < 145) {
                                seen[next] = true;
                                queue.add(ny * width + nx);
                            }
                        }
                }
                result.add(component);
            }
        return result;
    }

    private record Hole(
            int top, int bottom, int[] left, int[] right, Set<Integer> cells, int sourceWidth) {
        Hole(int top, int bottom, int[] left, int[] right) {
            this(top, bottom, left, right, null, 0);
        }

        boolean contains(int x, int y) {
            if (cells != null) return cells.contains(y * sourceWidth + x);
            return y > top && y < bottom && x > left[y - top - 1] && x < right[y - top - 1];
        }
    }

    private static final class Component {
        final Set<Integer> pixels = new HashSet<>();
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = -1, bottom = -1;
        boolean clipped;

        void add(int pixel, int x, int y) {
            pixels.add(pixel);
            left = Math.min(left, x);
            right = Math.max(right, x);
            top = Math.min(top, y);
            bottom = Math.max(bottom, y);
        }

        Hole hole(int width) {
            Hole simple = simpleHole(width);
            return simple != null ? simple : capFringeHole(width);
        }

        Hole simpleHole(int width) {
            // Bounded digital-convex ring grammar over integer PIXEL CENTERS.
            // Not convexity of the union of unit cells; no fractional/pixel allowance.
            // Every observed component pixel remains in an explicitly checked row run.
            var rows = new ArrayList<int[]>();
            var edges = new ArrayList<int[]>();
            var outer = new ArrayList<int[]>();
            int firstMiddle = -1, lastMiddle = -1, phase = 0;
            int[] previousSides = null, topCap = null, bottomCap = null;
            for (int y = top; y <= bottom; y++) {
                var runs = new ArrayList<int[]>();
                for (int x = left; x <= right; ) {
                    if (!pixels.contains(y * width + x)) {
                        x++;
                        continue;
                    }
                    int start = x;
                    while (x <= right && pixels.contains(y * width + x)) x++;
                    runs.add(new int[] {start, x - 1});
                }
                if (runs.size() == 1) {
                    int[] cap = runs.get(0);
                    rows.add(new int[] {cap[0], cap[1]});
                    if (phase == 0) topCap = cap;
                    else if (phase == 1) {
                        // Actual bottom-left and bottom-right 8-connected joins.
                        if (!touches(cap[0], cap[1], previousSides[0], previousSides[1])
                                || !touches(cap[0], cap[1], previousSides[2], previousSides[3]))
                            return null;
                        bottomCap = cap;
                        phase = 2;
                    }
                } else if (runs.size() == 2) {
                    if (phase == 2 || y == top) return null;
                    int[] l = runs.get(0), r = runs.get(1);
                    int[] sides = new int[] {l[0], l[1], r[0], r[1]};
                    if (phase == 0) {
                        if (topCap == null
                                || !touches(topCap[0], topCap[1], l[0], l[1])
                                || !touches(topCap[0], topCap[1], r[0], r[1])) return null;
                        firstMiddle = y;
                    } else {
                        // Separate left/right side continuity, not a common fixed-x spine.
                        if (!touches(previousSides[0], previousSides[1], l[0], l[1])
                                || !touches(previousSides[2], previousSides[3], r[0], r[1]))
                            return null;
                        // Adjacent open rows share at least one x: one 4-connected hole.
                        if (Math.max(previousSides[1] + 1, l[1] + 1)
                                > Math.min(previousSides[2] - 1, r[0] - 1)) return null;
                    }
                    if (l[1] + 1 >= r[0]) return null;
                    phase = 1;
                    lastMiddle = y;
                    previousSides = sides;
                    rows.add(sides);
                    edges.add(new int[] {l[1], r[0]});
                } else return null;
                int[] row = rows.get(rows.size() - 1);
                outer.add(new int[] {row[0] - left, row[row.length - 1] - left});
            }
            if (phase != 2 || bottomCap == null || firstMiddle <= top || lastMiddle >= bottom)
                return null;
            // Exact lattice-convex OUTER silhouette. Inward frame thickness may
            // vary; complete glyph rectangle plus observed paper separation above
            // supplies the inward bound. No frame pixel is removed to convexify it.
            // This is a bounded raster class, not universal semantic ownership.
            if (!latticeConvex(outer)) return null;
            int[] ls = new int[edges.size()], rs = new int[edges.size()];
            for (int i = 0; i < edges.size(); i++) {
                ls[i] = edges.get(i)[0];
                rs[i] = edges.get(i)[1];
            }
            return new Hole(firstMiddle - 1, lastMiddle + 1, ls, rs);
        }

        /** Full observed caps may own a bounded inward raster fringe. No pixel is trimmed. */
        private Hole capFringeHole(int width) {
            int w = right - left + 1, h = bottom - top + 1;
            long area = (long) w * h;
            // Optional new proof has its own finite workspace; the old path is unchanged.
            if (w < 3 || h < 3 || area > 65536) return null;
            var runs = new ArrayList<List<int[]>>(h);
            var outer = new ArrayList<int[]>(h);
            for (int y = top; y <= bottom; y++) {
                var row = new ArrayList<int[]>();
                for (int x = left; x <= right; ) {
                    if (!pixels.contains(y * width + x)) {
                        x++;
                        continue;
                    }
                    int first = x;
                    while (x <= right && pixels.contains(y * width + x)) x++;
                    row.add(new int[] {first, x - 1});
                }
                if (row.isEmpty()) return null;
                runs.add(row);
                outer.add(new int[] {row.get(0)[0] - left, row.get(row.size() - 1)[1] - left});
            }
            // The complete outer silhouette, including every cap tooth, keeps the old law.
            if (!latticeConvex(outer)) return null;
            int topCap = 0, bottomCap = 0;
            while (topCap < h && runs.get(topCap).size() == 1) topCap++;
            while (bottomCap < h && runs.get(h - 1 - bottomCap).size() == 1) bottomCap++;
            if (topCap == 0 || bottomCap == 0 || topCap + bottomCap >= h) return null;
            int first = topCap, last = h - 1 - bottomCap;
            while (first <= last && runs.get(first).size() > 2) first++;
            while (last >= first && runs.get(last).size() > 2) last--;
            if (first > last
                    || first - topCap > topCap
                    || h - 1 - bottomCap - last > bottomCap
                    || first == topCap && last == h - 1 - bottomCap) return null;
            // Every fringe pixel has a straight observed connection to its whole cap.
            // Depth cannot exceed that cap's independently observed complete thickness.
            for (int row = topCap; row < first; row++)
                for (var run : runs.get(row))
                    for (int x = run[0]; x <= run[1]; x++)
                        for (int y = row - 1; y >= topCap - 1; y--)
                            if (!pixels.contains((top + y) * width + x)) return null;
            for (int row = last + 1; row < h - bottomCap; row++)
                for (var run : runs.get(row))
                    for (int x = run[0]; x <= run[1]; x++)
                        for (int y = row + 1; y <= h - bottomCap; y++)
                            if (!pixels.contains((top + y) * width + x)) return null;
            List<int[]> previous = null;
            for (int row = first; row <= last; row++) {
                var sides = runs.get(row);
                if (sides.size() != 2 || sides.get(0)[1] + 1 >= sides.get(1)[0]) return null;
                if (previous != null
                        && (!touches(
                                        previous.get(0)[0],
                                        previous.get(0)[1],
                                        sides.get(0)[0],
                                        sides.get(0)[1])
                                || !touches(
                                        previous.get(1)[0],
                                        previous.get(1)[1],
                                        sides.get(1)[0],
                                        sides.get(1)[1])
                                || Math.max(previous.get(0)[1] + 1, sides.get(0)[1] + 1)
                                        > Math.min(previous.get(1)[0] - 1, sides.get(1)[0] - 1)))
                    return null;
                previous = sides;
            }
            // Enumerate the COMPLETE complement of this frame. A single closed4-connected
            // hole includes its cap-fringe bays, so extra interior ink is never hidden.
            boolean[] seen = new boolean[(int) area];
            int[] queue = new int[(int) area];
            Set<Integer> inside = null;
            for (int seed = 0; seed < seen.length; seed++) {
                int sx = left + seed % w, sy = top + seed / w;
                if (seen[seed] || pixels.contains(sy * width + sx)) continue;
                int read = 0, end = 1;
                boolean exterior = false;
                queue[0] = seed;
                seen[seed] = true;
                while (read < end) {
                    int at = queue[read++], x = at % w, y = at / w;
                    exterior |= x == 0 || x == w - 1 || y == 0 || y == h - 1;
                    for (int direction = 0; direction < 4; direction++) {
                        int nx = x + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
                        int ny = y + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && !pixels.contains((top + ny) * width + left + nx)) {
                            seen[next] = true;
                            queue[end++] = next;
                        }
                    }
                }
                if (exterior) continue;
                if (inside != null) return null;
                inside = new HashSet<>();
                for (int i = 0; i < end; i++)
                    inside.add((top + queue[i] / w) * width + left + queue[i] % w);
            }
            return inside == null
                    ? null
                    : new Hole(top, bottom, new int[0], new int[0], Set.copyOf(inside), width);
        }

        /** A second whole-glyph proof only inside an independently owned closed frame.
         * Every row is one complete run. A fixed finite backbone supports exactly two
         * right-going arms; both close back to plain backbone before the next role.
         * No searched crop, omitted hook, template score or unboxed rule is used.
         */
        private boolean completeCapitalF(int width, float gap) {
            int w = right - left + 1, h = bottom - top + 1;
            if (w < 5
                    || h < 9
                    || w < .35f * h
                    || w > .9f * h
                    || h < .8f * gap
                    || h > 2.5f * gap
                    || (long) w * h > 65536) return false;
            int[] lo = new int[h], hi = new int[h];
            for (int row = 0; row < h; row++) {
                int first = -1, last = -1, count = 0;
                for (int x = left; x <= right; x++)
                    if (pixels.contains((top + row) * width + x)) {
                        if (first < 0) first = x;
                        last = x;
                        count++;
                    }
                if (first < 0 || count != last - first + 1) return false;
                lo[row] = first;
                hi[row] = last;
            }
            // The backbone is the UNIQUE modal full row, not a best-fitting subcrop.
            int best = 0, sl = 0, sr = 0;
            boolean tied = false;
            for (int row = 0; row < h; row++) {
                boolean first = true;
                for (int p = 0; p < row; p++)
                    if (lo[p] == lo[row] && hi[p] == hi[row]) first = false;
                if (!first) continue;
                int count = 0;
                for (int p = 0; p < h; p++) if (lo[p] == lo[row] && hi[p] == hi[row]) count++;
                if (count > best) {
                    best = count;
                    sl = lo[row];
                    sr = hi[row];
                    tied = false;
                } else if (count == best) tied = true;
            }
            int t = sr - sl + 1;
            if (tied || best * 3 < h || t < 2 || t * 3 > h || sl - left >= t || sr >= right)
                return false;
            int[] start = new int[2], end = new int[2];
            int arms = 0;
            for (int row = 0; row < h; ) {
                if (hi[row] <= sr) {
                    row++;
                    continue;
                }
                if (hi[row] < sr + t || arms == 2) return false;
                start[arms] = row;
                while (row < h && hi[row] > sr) {
                    if (hi[row] < sr + t || lo[row] > sl || lo[row] < sl - t + 1) return false;
                    row++;
                }
                end[arms++] = row - 1;
            }
            if (arms != 2
                    || start[0] != 0
                    || end[0] + 1 > t
                    || end[0] < 1
                    || start[1] < Math.ceil(.35 * h)
                    || end[1] > Math.floor(.65 * h)
                    || end[1] - start[1] + 1 > t
                    || end[1] == start[1]
                    || start[1] - end[0] - 1 < t
                    || h - 1 - end[1] < 2 * t) return false;
            int capLeft = sl, topRight = sr, midRight = sr;
            for (int arm = 0; arm < 2; arm++) {
                int min = right, max = left;
                for (int row = start[arm]; row <= end[arm]; row++) {
                    min = Math.min(min, hi[row]);
                    max = Math.max(max, hi[row]);
                    if (arm == 0) capLeft = Math.min(capLeft, lo[row]);
                }
                // Both complete outward arm edges are finite, with cap variation
                // no greater than their observed backbone thickness.
                if (max - min >= t) return false;
                if (arm == 0) topRight = max;
                else midRight = max;
            }
            if (topRight != right || midRight >= topRight) return false;
            for (int row = 0; row < h; row++) {
                boolean arm = row <= end[0] || row >= start[1] && row <= end[1];
                if (arm) continue;
                if (h - 1 - row > t) {
                    if (lo[row] != sl || hi[row] != sr) return false;
                } else {
                    // The finite lower terminal cap may only reuse the observed
                    // upper cap's left overhang; no right arm or lower hook is erased.
                    if (lo[row] < capLeft
                            || lo[row] > sr
                            || hi[row] > sr
                            || hi[row] < sl
                            || hi[row] - lo[row] + 1 < t - (sl - capLeft)) return false;
                }
            }
            return true;
        }

        private static boolean touches(int a0, int a1, int b0, int b1) {
            return (long) a0 <= (long) b1 + 1 && (long) b0 <= (long) a1 + 1;
        }

        private static long cross(int[] a, int[] b, int[] c) {
            return (long) (b[0] - a[0]) * (c[1] - a[1]) - (long) (b[1] - a[1]) * (c[0] - a[0]);
        }

        // Each row is a FULL integer interval. Its endpoints generate the same hull
        // as every included pixel center. Compare exact rational hull scanlines with
        // these actual intervals: conv(S) intersect Z^2 must equal S, without rounding.
        private static boolean latticeConvex(List<int[]> rows) {
            if (rows.size() < 2) return false;
            var points = new ArrayList<int[]>();
            for (int y = 0; y < rows.size(); y++) {
                int[] row = rows.get(y);
                points.add(new int[] {row[0], y});
                if (row[0] != row[1]) points.add(new int[] {row[1], y});
            }
            points.sort(
                    (a, b) ->
                            a[0] == b[0]
                                    ? Integer.compare(a[1], b[1])
                                    : Integer.compare(a[0], b[0]));
            var hull = new ArrayList<int[]>();
            for (int[] p : points) {
                while (hull.size() >= 2
                        && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0)
                    hull.remove(hull.size() - 1);
                hull.add(p);
            }
            int lower = hull.size();
            for (int i = points.size() - 2; i >= 0; i--) {
                int[] p = points.get(i);
                while (hull.size() > lower
                        && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0)
                    hull.remove(hull.size() - 1);
                hull.add(p);
            }
            if (!hull.isEmpty()) hull.remove(hull.size() - 1);
            if (hull.size() < 3) return false;
            for (int y = 0; y < rows.size(); y++) {
                long minN = 0, minD = 1, maxN = 0, maxD = 1;
                boolean found = false;
                for (int i = 0; i < hull.size(); i++) {
                    int[] a = hull.get(i), b = hull.get((i + 1) % hull.size());
                    if (a[1] > b[1]) {
                        int[] swap = a;
                        a = b;
                        b = swap;
                    }
                    if (y < a[1] || y > b[1]) continue;
                    if (a[1] == b[1]) {
                        long lo = Math.min(a[0], b[0]), hi = Math.max(a[0], b[0]);
                        if (!found || lo * minD < minN) {
                            minN = lo;
                            minD = 1;
                        }
                        if (!found || hi * maxD > maxN) {
                            maxN = hi;
                            maxD = 1;
                        }
                        found = true;
                    } else {
                        long d = b[1] - a[1];
                        long n = (long) a[0] * d + (long) (y - a[1]) * (b[0] - a[0]);
                        if (!found || n * minD < minN * d) {
                            minN = n;
                            minD = d;
                        }
                        if (!found || n * maxD > maxN * d) {
                            maxN = n;
                            maxD = d;
                        }
                        found = true;
                    }
                }
                if (!found
                        || -Math.floorDiv(-minN, minD) != rows.get(y)[0]
                        || Math.floorDiv(maxN, maxD) != rows.get(y)[1]) return false;
            }
            return true;
        }
    }
}
