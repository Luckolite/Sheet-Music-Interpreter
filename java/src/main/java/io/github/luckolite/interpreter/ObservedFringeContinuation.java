// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Raw-only continuation evidence. CLEAR retains weak pixels as UNKNOWN; it never owns or erases them. */
final class ObservedFringeContinuation {
    interface Charge {
        void tick();
    }

    enum Kind {
        CLEAR,
        EXPOSED_CONTINUATION,
        CLIPPED_CONTINUATION,
        LOW_CONTRAST,
        INVALID_INPUT,
        RESOURCE_UNKNOWN
    }

    record Assessment(
            Kind kind,
            int paper,
            int core,
            int visited,
            int accepted,
            int reachablePixels,
            int ridgeStates) {}

    private record Node(int x, int y, int direction, int outside) {}

    private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1}, DY = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int MAX_STATES = 4096;

    private ObservedFringeContinuation() {}

    static Assessment inspect(
            byte[] gray,
            int width,
            int height,
            BitSet curve,
            int l,
            int t,
            int r,
            int b,
            int left,
            int top,
            int right,
            int bottom,
            float gap,
            Charge charge) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || curve == null
                || curve.length() > gray.length
                || charge == null
                || !Float.isFinite(gap)
                || gap < 5
                || gap > 100
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || left > right
                || top > bottom
                || l < left
                || t < top
                || r > right
                || b > bottom
                || l > r
                || t > b) return assessed(Kind.INVALID_INPUT, 0, 0, 0, 0, 0, 0);
        int w = right - left + 1, h = bottom - top + 1;
        if (w < 1 || h < 1 || (long) w * h > 16384)
            return assessed(Kind.RESOURCE_UNKNOWN, 0, 0, 0, 0, 0, 0);
        int[] inkHistogram = new int[256], paperHistogram = new int[256];
        int inside = 0, outside = 0;
        // Exact legacy hasGlyphContrast sampling convention and percentile law, with charged reads.
        int margin = Math.max(3, Math.round(gap * .45f));
        for (int y = Math.max(0, t - margin); y <= Math.min(height - 1, b + margin); y++)
            for (int x = Math.max(0, l - margin); x <= Math.min(width - 1, r + margin); x++) {
                charge.tick();
                int v = gray[y * width + x] & 255;
                if (x >= l && x <= r && y >= t && y <= b) {
                    inkHistogram[v]++;
                    inside++;
                } else {
                    paperHistogram[v]++;
                    outside++;
                }
            }
        int paper = percentile(paperHistogram, outside, .85f),
                core = percentile(inkHistogram, inside, .10f);
        if (inside < 6 || outside < 6 || paper - core < 32)
            return assessed(Kind.LOW_CONTRAST, paper, core, 0, 0, 0, 0);
        BitSet reachable = new BitSet(w * h);
        int[] frontier = new int[w * h];
        int take = 0, size = 0, accepted = 0, visited = 0, ridgeStates = 0;
        // Connectivity discovery is deliberately separate from ridge eligibility. A weak AA
        // shoulder may connect the observed core to an independently exposed foreign stroke.
        // Discovery creates no white/AA ownership and is limited by the SAME4096 combined cap.
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                charge.tick();
                if ((gray[y * width + x] & 255) > 165) continue;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < left || xx > right || yy < top || yy > bottom) continue;
                        int id = (yy - top) * w + xx - left;
                        if (reachable.get(id)
                                || !weak(gray, width, xx, yy, curve, l, t, r, b, paper, charge))
                            continue;
                        if (++accepted > MAX_STATES)
                            return assessed(
                                    Kind.RESOURCE_UNKNOWN,
                                    paper,
                                    core,
                                    visited,
                                    accepted,
                                    size,
                                    ridgeStates);
                        reachable.set(id);
                        frontier[size++] = id;
                    }
            }
        while (take < size) {
            charge.tick();
            int id = frontier[take++], x = left + id % w, y = top + id / w;
            visited++;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < left || xx > right || yy < top || yy > bottom) continue;
                    int next = (yy - top) * w + xx - left;
                    if (reachable.get(next)
                            || !weak(gray, width, xx, yy, curve, l, t, r, b, paper, charge))
                        continue;
                    if (++accepted > MAX_STATES)
                        return assessed(
                                Kind.RESOURCE_UNKNOWN,
                                paper,
                                core,
                                visited,
                                accepted,
                                size,
                                ridgeStates);
                    reachable.set(next);
                    frontier[size++] = next;
                }
        }
        BitSet seen = new BitSet(w * h * 32);
        ArrayDeque<Node> queue = new ArrayDeque<>();
        int ray = Math.max(2, Math.round(gap * .30f));
        for (int i = 0; i < size; i++) {
            int id = frontier[i], x = left + id % w, y = top + id / w;
            for (int direction = 0; direction < 8; direction++) {
                charge.tick();
                if (!ridge(gray, width, height, x, y, direction, ray, paper, charge)) continue;
                int outsideCount =
                        nearSupport(gray, x, y, width, height, curve, l, t, r, b, charge) ? 0 : 1;
                int state = (id * 8 + direction) * 4 + outsideCount;
                if (seen.get(state)) continue;
                if (++accepted > MAX_STATES)
                    return assessed(
                            Kind.RESOURCE_UNKNOWN,
                            paper,
                            core,
                            visited,
                            accepted,
                            size,
                            ridgeStates);
                ridgeStates++;
                seen.set(state);
                queue.add(new Node(x, y, direction, outsideCount));
            }
        }
        while (!queue.isEmpty()) {
            charge.tick();
            Node node = queue.remove();
            visited++;
            if (node.outside() >= 3)
                return assessed(
                        Kind.EXPOSED_CONTINUATION,
                        paper,
                        core,
                        visited,
                        accepted,
                        size,
                        ridgeStates);
            for (int turn = -1; turn <= 1; turn++) {
                int direction = (node.direction() + turn + 8) % 8;
                int x = node.x() + DX[direction], y = node.y() + DY[direction];
                if (x < 0 || y < 0 || x >= width || y >= height) continue;
                if (x < left || x > right || y < top || y > bottom) {
                    if (weak(gray, width, x, y, curve, l, t, r, b, paper, charge)
                            && ridge(gray, width, height, x, y, direction, ray, paper, charge))
                        return assessed(
                                Kind.CLIPPED_CONTINUATION,
                                paper,
                                core,
                                visited,
                                accepted,
                                size,
                                ridgeStates);
                    continue;
                }
                int raw = (y - top) * w + x - left;
                if (!reachable.get(raw)
                        || !ridge(gray, width, height, x, y, direction, ray, paper, charge))
                    continue;
                int distance =
                        nearSupport(gray, x, y, width, height, curve, l, t, r, b, charge)
                                ? 0
                                : Math.min(3, node.outside() + 1);
                int id = (raw * 8 + direction) * 4 + distance;
                if (seen.get(id)) continue;
                if (++accepted > MAX_STATES)
                    return assessed(
                            Kind.RESOURCE_UNKNOWN,
                            paper,
                            core,
                            visited,
                            accepted,
                            size,
                            ridgeStates);
                ridgeStates++;
                seen.set(id);
                queue.add(new Node(x, y, direction, distance));
            }
        }
        return assessed(Kind.CLEAR, paper, core, visited, accepted, size, ridgeStates);
    }

    private static Assessment assessed(
            Kind kind,
            int paper,
            int core,
            int visited,
            int accepted,
            int reachablePixels,
            int ridgeStates) {
        JoinedTupletObservation.event(
                "FRINGE_RETURN",
                kind.ordinal(),
                paper,
                core,
                visited,
                accepted,
                reachablePixels,
                ridgeStates);
        return new Assessment(kind, paper, core, visited, accepted, reachablePixels, ridgeStates);
    }

    private static int percentile(int[] histogram, int count, float fraction) {
        int sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += histogram[i];
            if (sum >= count * fraction) return i;
        }
        return 255;
    }

    private static boolean weak(
            byte[] gray,
            int width,
            int x,
            int y,
            BitSet curve,
            int l,
            int t,
            int r,
            int b,
            int paper,
            Charge charge) {
        charge.tick();
        int at = y * width + x, v = gray[at] & 255;
        return v > 165 && v < paper && !(curve.get(at) && !(x >= l && x <= r && y >= t && y <= b));
    }

    private static boolean nearSupport(
            byte[] gray,
            int x,
            int y,
            int width,
            int height,
            BitSet curve,
            int l,
            int t,
            int r,
            int b,
            Charge charge) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= width || yy >= height) continue;
                charge.tick();
                int at = yy * width + xx;
                boolean inBox = xx >= l && xx <= r && yy >= t && yy <= b;
                if (inBox ? (gray[at] & 255) <= 165 : curve.get(at)) return true;
            }
        return false;
    }

    private static boolean ridge(
            byte[] gray,
            int width,
            int height,
            int x,
            int y,
            int direction,
            int maximum,
            int paper,
            Charge charge) {
        charge.tick();
        int nx = -DY[direction], ny = DX[direction], value = gray[y * width + x] & 255;
        for (int side = -1; side <= 1; side += 2) {
            boolean exposed = false;
            for (int distance = 1; distance <= maximum; distance++) {
                int xx = x + side * distance * nx, yy = y + side * distance * ny;
                if (xx < 0 || yy < 0 || xx >= width || yy >= height) return false;
                charge.tick();
                int sample = gray[yy * width + xx] & 255;
                // A normal AA shoulder descends towards its core; it cannot supply this flank.
                if (sample < value) return false;
                if (sample > value && sample >= paper) {
                    exposed = true;
                    break;
                }
            }
            if (!exposed) return false;
        }
        return true;
    }
}
