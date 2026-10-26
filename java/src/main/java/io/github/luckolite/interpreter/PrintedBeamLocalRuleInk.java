// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Exact-column raw carrier contrast after bilateral raw-five lane/sizing proof; never a pitch track. */
final class PrintedBeamLocalRuleInk {
    private record Reference(
            int line, int first, int last, float center, float contrast, float mass) {}

    private record Profile(float center, List<Reference> references) {}

    private final byte[] gray;
    private final int width, height;
    private final float bottom, gap;
    private final java.util.function.LongConsumer singleRowCharge;
    private final Map<Long, Optional<Profile>> profiles = new HashMap<>();
    private final Map<Integer, List<Reference>> columns = new HashMap<>();

    PrintedBeamLocalRuleInk(
            byte[] ignoredLabels, byte[] gray, int width, int height, float bottom, float gap) {
        this(ignoredLabels, gray, width, height, bottom, gap, null);
    }

    PrintedBeamLocalRuleInk(
            byte[] ignoredLabels,
            byte[] gray,
            int width,
            int height,
            float bottom,
            float gap,
            java.util.function.LongConsumer singleRowCharge) {
        this.singleRowCharge = singleRowCharge;
        this.gray = gray;
        this.width = width;
        this.height = height;
        this.bottom = bottom;
        this.gap = gap;
    }

    boolean carrier(int x, int y) {
        int original = gray[y * width + x] & 255;
        if (original >= 165) return false;
        var refs = columns.computeIfAbsent(x, this::references);
        if (fits(refs)) for (var r : refs) if (y >= r.first() && y <= r.last()) return true;
        float top = bottom - 4 * gap;
        int line = Math.round((y - top) / gap);
        if (line < 0 || line > 4 || Math.abs(y - top - line * gap) > gap * .45f) return false;
        long key = ((long) x << 3) | line;
        var cached = profiles.get(key);
        if (cached == null) {
            var other = refs.stream().filter(r -> r.line() != line).toList();
            cached =
                    Optional.ofNullable(
                            fits(other)
                                    ? new Profile(fittedTop(other) + line * fittedGap(other), other)
                                    : null);
            profiles.put(key, cached);
        }
        if (cached.isEmpty()) return false;
        var p = cached.get();
        float[] samples = new float[p.references().size()];
        for (int i = 0; i < samples.length; i++)
            samples[i] = interpolate(x, p.references().get(i).center() + y - p.center());
        Arrays.sort(samples);
        float reference = samples[samples.length / 2];
        return Math.min(255, Math.round(original + Math.max(0, 255 - reference))) >= 165;
    }

    boolean provedAt(int x, int y) {
        float top = bottom - 4 * gap;
        int line = Math.round((y - top) / gap);
        return line >= 0
                && line < 5
                && profiles.getOrDefault(((long) x << 3) | line, Optional.empty()).isPresent();
    }

    /** All closed thin-run rivals are inspected. A second eligible run refuses this reference;
     * foreign thick ink outside the selected closed profile is never folded into its mass. */
    private List<Reference> references(int x) {
        if (!Float.isFinite(gap) || gap < 8) return List.of();
        float top = bottom - 4 * gap;
        int radius = Math.max(3, Math.round(gap * .35f));
        List<Reference> refs = new ArrayList<>();
        for (int line = 0; line < 5; line++) {
            int center = Math.round(top + line * gap), lo = center - radius, hi = center + radius;
            if (lo < 1 || hi >= height - 1) continue;
            Reference found = null;
            boolean ambiguous = false;
            int start = -1;
            for (int y = lo; y <= hi + 1; y++) {
                boolean ink = y <= hi && (gray[y * width + x] & 255) < 165;
                if (ink) {
                    if (start < 0) start = y;
                } else if (start >= 0) {
                    int last = y - 1, n = last - start + 1;
                    if (start > lo
                            && last < hi
                            && n <= Math.ceil(gap * .3f)
                            && (n >= 2
                                    || n == 1
                                            && singleRowCharge != null
                                            && singleRowReference(x, start, lo, hi))) {
                        float contrast = 0, mass = 0;
                        for (int row = start - 1; row <= last + 1; row++) {
                            int v = gray[row * width + x] & 255;
                            mass += 255 - v;
                            contrast = Math.max(contrast, 255 - v);
                        }
                        if (found != null) {
                            ambiguous = true;
                            break;
                        }
                        found =
                                new Reference(
                                        line, start, last, (start + last) * .5f, contrast, mass);
                    }
                    start = -1;
                }
            }
            if (!ambiguous && found != null) refs.add(found);
        }
        if (refs.size() < 3) return List.of();
        float[] cs = new float[refs.size()], ms = new float[refs.size()];
        for (int i = 0; i < refs.size(); i++) {
            cs[i] = refs.get(i).contrast();
            ms[i] = refs.get(i).mass();
        }
        Arrays.sort(cs);
        Arrays.sort(ms);
        float c = cs[cs.length / 2], m = ms[ms.length / 2];
        refs.removeIf(
                r ->
                        r.contrast() < c / 1.35f
                                || r.contrast() > c * 1.35f
                                || r.mass() < m / 1.35f
                                || r.mass() > m * 1.35f);
        return List.copyOf(refs);
    }

    /** A one-row dark core needs an actual unique closed thin rule on both sides.
     * No nearby singleton or interpolated line can supply either flank. New reads
     * and loop operations spend the enclosing Source allowance before use. */
    private boolean singleRowReference(int x, int row, int low, int high) {
        singleRowCharge.accept(16);
        int flank = Math.max(2, Math.round(gap * .45f));
        if (x < flank || x + flank >= width) return false;
        for (int side = -1; side <= 1; side += 2) {
            int sx = x + side * flank, start = -1, foundFirst = -1, foundLast = -1;
            for (int y = low; y <= high + 1; y++) {
                singleRowCharge.accept(4);
                boolean ink = y <= high && singleRowPixel(sx, y) < 165;
                if (ink) {
                    if (start < 0) start = y;
                } else if (start >= 0) {
                    int last = y - 1;
                    if (start <= low
                            || last >= high
                            || last - start + 1 > Math.ceil(gap * .3f)
                            || foundFirst >= 0) return false;
                    foundFirst = start;
                    foundLast = last;
                    start = -1;
                }
            }
            if (foundFirst < 0 || Math.abs((foundFirst + foundLast) * .5f - row) > 2) return false;
        }
        return true;
    }

    private int singleRowPixel(int x, int y) {
        singleRowCharge.accept(1);
        return gray[y * width + x] & 255;
    }

    private boolean fits(List<Reference> refs) {
        if (refs.size() < 3) return false;
        float g = fittedGap(refs), top = fittedTop(refs);
        if (Math.abs(g - gap) > gap * .15f || Math.abs(top - (bottom - 4 * gap)) > gap * .25f)
            return false;
        for (var r : refs) if (Math.abs(r.center() - top - r.line() * g) > .75f) return false;
        return true;
    }

    private static float fittedGap(List<Reference> refs) {
        float il = 0, iy = 0;
        for (var r : refs) {
            il += r.line();
            iy += r.center();
        }
        il /= refs.size();
        iy /= refs.size();
        float co = 0, va = 0;
        for (var r : refs) {
            co += (r.line() - il) * (r.center() - iy);
            va += (r.line() - il) * (r.line() - il);
        }
        return co / va;
    }

    private static float fittedTop(List<Reference> refs) {
        float il = 0, iy = 0;
        for (var r : refs) {
            il += r.line();
            iy += r.center();
        }
        return iy / refs.size() - il / refs.size() * fittedGap(refs);
    }

    private float interpolate(int x, float y) {
        int row = (int) Math.floor(y);
        if (row < 0 || row + 1 >= height) return 255;
        float f = y - row;
        return (gray[row * width + x] & 255) * (1 - f) + (gray[(row + 1) * width + x] & 255) * f;
    }
}
