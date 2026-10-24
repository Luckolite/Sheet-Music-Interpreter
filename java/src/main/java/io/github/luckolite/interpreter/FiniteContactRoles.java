// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Source-free physical partition of ink adjacent to an authenticated finite body. */
final class FiniteContactRoles {
    private FiniteContactRoles() {}

    enum Role {
        STAFF_EDGE,
        BAR_CAP_EXTENSION,
        OTHER_FINITE_STROKE,
        UNKNOWN
    }

    record Result(
            boolean complete,
            int staffEdge,
            int capExtension,
            int otherStroke,
            int unknown,
            String refusal) {}

    private static final int MAX_CONTACTS = 4096;
    private static final int MAX_COMPONENT_PIXELS = 256;

    static Result classify(
            byte[] gray,
            int width,
            int height,
            int laneMid,
            int gap,
            MaximalRawBarExtractor.Body body,
            Set<Integer> authenticatedStaff) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || body == null
                || authenticatedStaff == null
                || gap < 5
                || gap > 64) {
            return refused("INPUT_DOMAIN");
        }
        for (int p : authenticatedStaff)
            if (p < 0 || p >= gray.length) return refused("STAFF_PIXEL_DOMAIN");
        Set<Integer> bodyPixels = new HashSet<>(body.rawPixels());
        Set<Integer> contacts = new HashSet<>();
        for (int p : body.rawPixels()) {
            if (p < 0 || p >= gray.length) return refused("BODY_PIXEL_DOMAIN");
            int x = p % width, y = p / width;
            for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++) {
                for (int xx = Math.max(0, x - 1); xx <= Math.min(width - 1, x + 1); xx++) {
                    int q = yy * width + xx;
                    if (!bodyPixels.contains(q)
                            && !authenticatedStaff.contains(q)
                            && (gray[q] & 255) < MaximalRawBarExtractor.DARK_LIMIT) {
                        contacts.add(q);
                        if (contacts.size() > MAX_CONTACTS) return refused("CONTACT_CAP");
                    }
                }
            }
        }
        if (contacts.isEmpty()) return new Result(true, 0, 0, 0, 0, null);
        Set<Integer> remaining = new HashSet<>(contacts);
        Set<Integer> attributed = new HashSet<>();
        int staff = 0, cap = 0, other = 0, unknown = 0;
        double slope = body.slopeTick() * .05;
        int maxExtension = Math.max(1, (int) Math.ceil(.2 * gap));
        for (int q : contacts) {
            int x = q % width, y = q / width;
            if (staffEdge(gray, authenticatedStaff, x, y, width, height)) {
                remaining.remove(q);
                attributed.add(q);
                staff++;
            }
        }
        while (!remaining.isEmpty()) {
            int seed = remaining.iterator().next();
            List<Integer> component =
                    floodRaw(
                            remaining,
                            gray,
                            bodyPixels,
                            authenticatedStaff,
                            attributed,
                            width,
                            height);
            if (component == null) return refused("CONTACT_COMPONENT_CAP");
            boolean anyCap = false, allCap = true;
            for (int p : component) {
                boolean fits =
                        capExtension(body, laneMid, gap, p % width, p / width, slope, maxExtension);
                anyCap |= fits;
                allCap &= fits;
            }
            if (allCap) {
                cap += component.size();
            } else if (isCompleteNonparallelStroke(component, body, width, height, gap, slope)) {
                other += component.size();
            } else if (anyCap) {
                unknown += component.size();
            } else {
                unknown += component.size();
            }
        }
        boolean complete = unknown == 0;
        return new Result(
                complete, staff, cap, other, unknown, complete ? null : "UNOWNED_CONTACT");
    }

    private static boolean staffEdge(
            byte[] gray, Set<Integer> staff, int x, int y, int width, int height) {
        // A shoulder is staff-owned only beside an authenticated literal rail
        // with horizontal support on both sides. A nearby single dark pixel is insufficient.
        for (int dy = -1; dy <= 1; dy++) {
            int row = y + dy, left = 0, right = 0;
            if (row < 0 || row >= height) continue;
            int coreSum = 0, coreN = 0;
            for (int dx = -3; dx <= -1; dx++) {
                int col = x + dx;
                if (col >= 0 && col < width && staff.contains(row * width + col)) {
                    left++;
                    coreSum += gray[row * width + col] & 255;
                    coreN++;
                }
            }
            for (int dx = 1; dx <= 3; dx++) {
                int col = x + dx;
                if (col >= 0 && col < width && staff.contains(row * width + col)) {
                    right++;
                    coreSum += gray[row * width + col] & 255;
                    coreN++;
                }
            }
            int value = gray[y * width + x] & 255;
            if (left >= 2
                    && right >= 2
                    && coreN > 0
                    && value > coreSum / coreN
                    && value < MaximalRawBarExtractor.DARK_LIMIT) return true;
        }
        return false;
    }

    private static boolean capExtension(
            MaximalRawBarExtractor.Body body,
            int mid,
            int gap,
            int x,
            int y,
            double slope,
            int maxExtension) {
        if (x >= body.firstX() && x <= body.lastX()) return false;
        int endX = x < body.firstX() ? body.firstX() : body.lastX();
        if (Math.abs(x - endX) > maxExtension) return false;
        double axisY = body.centerAt(x, mid);
        double normal = Math.abs((y - axisY) / Math.sqrt(1 + slope * slope));
        return normal <= body.maxWidth() * .5 + .5;
    }

    private static List<Integer> floodRaw(
            Set<Integer> remaining,
            byte[] gray,
            Set<Integer> body,
            Set<Integer> staff,
            Set<Integer> attributed,
            int width,
            int height) {
        int seed = remaining.iterator().next();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        ArrayList<Integer> found = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        remaining.remove(seed);
        seen.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            int p = queue.removeFirst();
            found.add(p);
            if (found.size() > MAX_COMPONENT_PIXELS) return null;
            int x = p % width, y = p / width;
            for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++)
                for (int xx = Math.max(0, x - 1); xx <= Math.min(width - 1, x + 1); xx++) {
                    int q = yy * width + xx;
                    if (body.contains(q)) continue;
                    if (!staff.contains(q)
                            && !attributed.contains(q)
                            && (gray[q] & 255) < MaximalRawBarExtractor.DARK_LIMIT
                            && seen.add(q)) queue.addLast(q);
                }
        }
        remaining.removeAll(found);
        return found;
    }

    private static boolean isCompleteNonparallelStroke(
            List<Integer> pixels,
            MaximalRawBarExtractor.Body body,
            int width,
            int height,
            int gap,
            double bodySlope) {
        if (pixels.size() < 3) return false;
        double mx = 0, my = 0;
        for (int p : pixels) {
            mx += p % width;
            my += p / width;
        }
        mx /= pixels.size();
        my /= pixels.size();
        double xx = 0, yy = 0, xy = 0;
        for (int p : pixels) {
            double dx = p % width - mx, dy = p / width - my;
            xx += dx * dx;
            yy += dy * dy;
            xy += dx * dy;
        }
        double axis = .5 * Math.atan2(2 * xy, xx - yy);
        double strokeSlope = Math.tan(axis);
        if (!Double.isFinite(strokeSlope)) return false;
        // Role is based on a complete finite cross-stroke and its observed caps,
        // not on whether its angle happens to be one of the bar extractor's ticks.
        // Compare with the observed body's own pixel orientation. The extractor's
        // quantized search tick is a representative, not a complete slope proof.
        double bodyAxis = axisOf(body.rawPixels(), width);
        double separation = Math.abs(axis - bodyAxis);
        separation = Math.min(separation, Math.PI - separation);
        if (separation < .10) return false;
        double ux = Math.cos(axis),
                uy = Math.sin(axis),
                min = Double.POSITIVE_INFINITY,
                max = Double.NEGATIVE_INFINITY;
        for (int p : pixels) {
            double projection = (p % width - mx) * ux + (p / width - my) * uy;
            min = Math.min(min, projection);
            max = Math.max(max, projection);
        }
        if (max - min < 2) return false;
        int bins = (int) Math.floor(max - min) + 1;
        if (bins > MAX_COMPONENT_PIXELS) return false;
        double[] lo = new double[bins], hi = new double[bins], sum = new double[bins];
        int[] counts = new int[bins];
        java.util.Arrays.fill(lo, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(hi, Double.NEGATIVE_INFINITY);
        for (int p : pixels) {
            double dx = p % width - mx, dy = p / width - my;
            int band = (int) Math.floor((dx * ux + dy * uy) - min);
            if (band < 0 || band >= bins) return false;
            double normal = -dx * uy + dy * ux;
            lo[band] = Math.min(lo[band], normal);
            hi[band] = Math.max(hi[band], normal);
            sum[band] += normal;
            counts[band]++;
        }
        int maxThickness = Math.max(1, (int) Math.ceil(.35 * gap));
        double priorCenter = Double.NaN;
        boolean started = false;
        double centerSum = 0, centerXS = 0, centerYS = 0, centerXX = 0, centerXY = 0;
        int n = 0, maxBandWidth = 0;
        int[] widths = new int[bins];
        double[] centers = new double[bins];
        for (int i = 0; i < bins; i++) {
            if (counts[i] == 0) return false;
            double center = sum[i] / counts[i];
            int bandWidth = (int) Math.ceil(hi[i] - lo[i] + 1e-6);
            if (bandWidth > maxThickness || hi[i] - lo[i] > maxThickness) return false;
            if (started && Math.abs(center - priorCenter) > 1.5) return false;
            priorCenter = center;
            started = true;
            widths[i] = bandWidth;
            centers[i] = center;
            maxBandWidth = Math.max(maxBandWidth, bandWidth);
            double along = min + i + .5;
            centerSum += center;
            centerXS += along;
            centerYS += center;
            centerXX += along * along;
            centerXY += along * center;
            n++;
        }
        double denom = n * centerXX - centerXS * centerXS;
        if (n < 3 || denom <= 0) return false;
        double fit = (n * centerXY - centerXS * centerYS) / denom;
        double intercept = (centerYS - fit * centerXS) / n;
        for (int i = 0; i < bins; i++)
            if (Math.abs(centers[i] - (intercept + fit * (min + i + .5))) > 1.0) return false;
        int coreStart = -1, coreEnd = -1;
        for (int i = 0; i < bins; i++)
            if (widths[i] >= maxBandWidth - 1) {
                if (coreStart < 0) coreStart = i;
                coreEnd = i;
            }
        if (coreStart < 0 || coreEnd - coreStart + 1 < (bins + 1) / 2) return false;
        for (int i = coreStart; i <= coreEnd; i++) if (widths[i] < maxBandWidth - 1) return false;
        for (int i = 1; i <= coreStart; i++) if (widths[i] < widths[i - 1] - 1) return false;
        for (int i = coreEnd + 1; i < bins; i++)
            if (widths[i] > widths[i - 1] || widths[i - 1] - widths[i] > 2) return false;
        return true;
    }

    private static double axisOf(List<Integer> pixels, int width) {
        double mx = 0, my = 0;
        for (int p : pixels) {
            mx += p % width;
            my += p / width;
        }
        mx /= pixels.size();
        my /= pixels.size();
        double xx = 0, yy = 0, xy = 0;
        for (int p : pixels) {
            double dx = p % width - mx, dy = p / width - my;
            xx += dx * dx;
            yy += dy * dy;
            xy += dx * dy;
        }
        return .5 * Math.atan2(2 * xy, xx - yy);
    }

    private static Result refused(String reason) {
        return new Result(false, 0, 0, 0, 0, reason);
    }
}
