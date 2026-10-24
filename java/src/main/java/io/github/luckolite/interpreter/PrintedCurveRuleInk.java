// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Removes only independently corroborated rule contrast from a local curve witness. */
final class PrintedCurveRuleInk {
    private record Reference(int line, float center, float contrast, float mass) {}

    private record Profile(float center, List<Reference> references) {}

    private final byte[] labels, gray;
    private final int width, height, threshold;
    private final StaffPitchTrack track;
    private final Map<Long, Profile> profiles = new HashMap<>();
    private final Set<Integer> used = new HashSet<>();

    PrintedCurveRuleInk(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int threshold,
            StaffPitchTrack track) {
        this.labels = labels;
        this.gray = gray;
        this.width = width;
        this.height = height;
        this.threshold = threshold;
        this.track = track;
    }

    boolean corroboratedCrossing() {
        return used.size() >= 2;
    }

    int sample(int x, int y) {
        int original = gray[y * width + x] & 255;
        if (!track.verifiedAt(x)) return original;
        float[] frame = track.at(x);
        float top = frame[0] - 4 * frame[1];
        int line = Math.round((y - top) / frame[1]);
        if (line < 0 || line > 4 || Math.abs(y - top - line * frame[1]) > frame[1] * .45f)
            return original;
        long key = ((long) x << 3) | line;
        Profile profile;
        if (profiles.containsKey(key)) profile = profiles.get(key);
        else {
            profile = prove(x, line, top, frame[1]);
            profiles.put(key, profile);
        }
        if (profile == null) return original;
        float[] samples = new float[profile.references.size()];
        for (int i = 0; i < samples.length; i++) {
            Reference reference = profile.references.get(i);
            samples[i] = interpolate(x, reference.center + y - profile.center);
        }
        Arrays.sort(samples);
        float reference = samples[samples.length / 2];
        int adjusted = Math.min(255, Math.round(original + Math.max(0, 255 - reference)));
        if (original < threshold && adjusted >= threshold) used.add(x);
        return adjusted;
    }

    private Profile prove(int x, int target, float top, float gap) {
        if (!Float.isFinite(gap) || gap < 8) return null;
        int radius = Math.max(3, Math.round(gap * .35f));
        var references = new ArrayList<Reference>();
        for (int line = 0; line < 5; line++) {
            if (line == target) continue;
            int center = Math.round(top + line * gap), lo = center - radius, hi = center + radius;
            if (lo < 1
                    || hi >= height - 1
                    || (gray[lo * width + x] & 255) < 230
                    || (gray[hi * width + x] & 255) < 230) continue;
            int first = -1, last = -1, runs = 0;
            boolean previous = false, semantic = true;
            float mass = 0, contrast = 0;
            for (int y = lo; y <= hi; y++) {
                int value = gray[y * width + x] & 255;
                mass += 255 - value;
                contrast = Math.max(contrast, 255 - value);
                boolean ink = value < threshold;
                if (ink && !previous) {
                    runs++;
                    first = y;
                }
                if (ink) {
                    last = y;
                    int label = labels == null ? 0 : labels[y * width + x] & 255;
                    if (label != 0 && label != OmrMeasurePostProcessor.STAFF) semantic = false;
                }
                previous = ink;
            }
            if (runs != 1
                    || !semantic
                    || first <= lo
                    || last >= hi
                    || last - first + 1 < 2
                    || last - first + 1 > Math.ceil(gap * .3f)) continue;
            references.add(new Reference(line, (first + last) * .5f, contrast, mass));
        }
        if (references.size() < 3) return null;
        float[] contrasts = new float[references.size()], masses = new float[references.size()];
        for (int i = 0; i < references.size(); i++) {
            contrasts[i] = references.get(i).contrast;
            masses[i] = references.get(i).mass;
        }
        Arrays.sort(contrasts);
        Arrays.sort(masses);
        float contrast = contrasts[contrasts.length / 2], mass = masses[masses.length / 2];
        references.removeIf(
                r ->
                        r.contrast < contrast / 1.35f
                                || r.contrast > contrast * 1.35f
                                || r.mass < mass / 1.35f
                                || r.mass > mass * 1.35f);
        if (references.size() < 3) return null;
        float meanLine = 0, meanCenter = 0;
        for (Reference r : references) {
            meanLine += r.line;
            meanCenter += r.center;
        }
        meanLine /= references.size();
        meanCenter /= references.size();
        float covariance = 0, variance = 0;
        for (Reference r : references) {
            covariance += (r.line - meanLine) * (r.center - meanCenter);
            variance += (r.line - meanLine) * (r.line - meanLine);
        }
        float fittedGap = covariance / variance, fittedTop = meanCenter - fittedGap * meanLine;
        float center = fittedTop + target * fittedGap;
        if (Math.abs(fittedGap - gap) > gap * .15f
                || Math.abs(center - top - target * gap) > gap * .25f) return null;
        for (Reference r : references)
            if (Math.abs(r.center - fittedTop - r.line * fittedGap) > .75f) return null;
        return new Profile(center, references);
    }

    private float interpolate(int x, float y) {
        int row = (int) Math.floor(y);
        if (row < 0 || row + 1 >= height) return 255;
        float fraction = y - row;
        return (gray[row * width + x] & 255) * (1 - fraction)
                + (gray[(row + 1) * width + x] & 255) * fraction;
    }
}
