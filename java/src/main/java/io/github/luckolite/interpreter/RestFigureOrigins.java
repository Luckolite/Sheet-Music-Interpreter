// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.AbstractList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Identity-only provenance; semantic membership is never promoted to raw contour closure. */
final class RestFigureOrigins {
    private RestFigureOrigins() {}

    static final class HeadOrigin {
        final Object component;
        final byte[] gray;
        final byte[] originalGray;
        final int width, height;
        private final int[][] runs;
        private final HeadOrigin source;
        private final float[] bottoms;
        private final int first;
        private final float staffBottom;

        private HeadOrigin(
                Object component,
                byte[] gray,
                int width,
                int height,
                int[][] runs,
                HeadOrigin source,
                float[] bottoms,
                int first,
                float staffBottom,
                byte[] originalGray) {
            this.originalGray = originalGray;
            this.component = component;
            this.gray = gray;
            this.width = width;
            this.height = height;
            this.runs = runs;
            this.source = source;
            this.bottoms = bottoms;
            this.first = first;
            this.staffBottom = staffBottom;
        }

        /** Called only for the already visited runs of one accepted semantic Component. */
        static HeadOrigin accepted(
                Object component, byte[] gray, int width, int height, List<int[]> visitedRuns) {
            if (component == null
                    || gray == null
                    || gray.length != (long) width * height
                    || width <= 0
                    || height <= 0
                    || visitedRuns == null
                    || visitedRuns.isEmpty()) return null;
            int[][] runs = new int[visitedRuns.size()][];
            for (int i = 0; i < runs.length; i++) {
                int[] r = visitedRuns.get(i);
                if (r == null
                        || r.length != 3
                        || r[0] < 0
                        || r[0] >= height
                        || r[1] < 0
                        || r[2] >= width
                        || r[1] > r[2]) return null;
                runs[i] = r.clone();
            }
            return new HeadOrigin(component, gray, width, height, runs, null, null, 0, 0, gray);
        }

        private HeadOrigin rectified(
                byte[] flat,
                int bandHeight,
                float[] actualBottoms,
                int first,
                float staffBottom,
                byte[] originalFlat) {
            if (source != null
                    || flat == null
                    || flat.length != (long) width * bandHeight
                    || actualBottoms == null
                    || actualBottoms.length != width) return null;
            return new HeadOrigin(
                    component,
                    flat,
                    width,
                    bandHeight,
                    null,
                    this,
                    actualBottoms.clone(),
                    first,
                    staffBottom,
                    originalFlat);
        }

        boolean contains(int x, int y) {
            if (x < 0 || x >= width || y < 0 || y >= height) return false;
            if (source != null) {
                // EXACT existing rectifier sample; no inverse interpolation or AA dilation.
                int sy = Math.round(bottoms[x] + (first + y - staffBottom));
                return source.contains(x, sy);
            }
            for (int[] r : runs) if (r[0] == y && x >= r[1] && x <= r[2]) return true;
            return false;
        }
    }

    private static final class OwnedNotes extends AbstractList<ScoreNoteEvent> {
        final List<ScoreNoteEvent> values;
        final IdentityHashMap<ScoreNoteEvent, HeadOrigin> origins;

        OwnedNotes(List<ScoreNoteEvent> values, Map<ScoreNoteEvent, HeadOrigin> origins) {
            this.values = List.copyOf(values);
            this.origins = new IdentityHashMap<>();
            for (ScoreNoteEvent n : this.values) {
                // The incoming Map may use record equality; provenance is identity-only.
                for (var entry : origins.entrySet()) {
                    if (entry.getKey() == n && entry.getValue() != null) {
                        this.origins.put(n, entry.getValue());
                        break;
                    }
                }
            }
        }

        @Override
        public ScoreNoteEvent get(int i) {
            return values.get(i);
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    static List<ScoreNoteEvent> bind(
            List<ScoreNoteEvent> values, Map<ScoreNoteEvent, HeadOrigin> origins) {
        return origins == null || origins.isEmpty() ? values : new OwnedNotes(values, origins);
    }

    /** Only real coordinate-preserving rest-tone creation sites issue this local view. */
    static List<ScoreNoteEvent> photometricView(
            List<ScoreNoteEvent> notes, byte[] sourceGray, byte[] view, int w, int h) {
        if (view == sourceGray) return notes;
        if (!(notes instanceof OwnedNotes)
                || sourceGray == null
                || view == null
                || sourceGray.length != (long) w * h
                || view.length != sourceGray.length) return notes;
        var evidence = new IdentityHashMap<ScoreNoteEvent, HeadOrigin>();
        for (var n : notes) {
            HeadOrigin o = origin(notes, n, sourceGray, w, h);
            if (o != null)
                evidence.put(
                        n,
                        new HeadOrigin(
                                o.component,
                                view,
                                w,
                                h,
                                o.runs,
                                o.source,
                                o.bottoms,
                                o.first,
                                o.staffBottom,
                                o.originalGray));
        }
        return bind(notes, evidence);
    }

    record OriginalPair(byte[] gray, List<ScoreNoteEvent> notes) {}

    /** Canonical view of exactly the two already selected identity-bound owners; no pair borrowing. */
    static OriginalPair originalPair(
            List<ScoreNoteEvent> notes,
            ScoreNoteEvent na,
            HeadOrigin a,
            ScoreNoteEvent nb,
            HeadOrigin b,
            byte[] active,
            int w,
            int h) {
        if (!(notes instanceof OwnedNotes owned)
                || na == nb
                || a == null
                || b == null
                || owned.origins.get(na) != a
                || owned.origins.get(nb) != b
                || a.component == b.component
                || a.gray != active
                || b.gray != active
                || a.width != w
                || a.height != h
                || b.width != w
                || b.height != h
                || a.originalGray == null
                || a.originalGray != b.originalGray
                || a.originalGray.length != (long) w * h) return null;
        byte[] original = a.originalGray;
        var origins = new IdentityHashMap<ScoreNoteEvent, HeadOrigin>();
        origins.put(
                na,
                new HeadOrigin(
                        a.component,
                        original,
                        w,
                        h,
                        a.runs,
                        a.source,
                        a.bottoms,
                        a.first,
                        a.staffBottom,
                        original));
        origins.put(
                nb,
                new HeadOrigin(
                        b.component,
                        original,
                        w,
                        h,
                        b.runs,
                        b.source,
                        b.bottoms,
                        b.first,
                        b.staffBottom,
                        original));
        return new OriginalPair(original, bind(List.of(na, nb), origins));
    }

    static HeadOrigin origin(
            List<ScoreNoteEvent> values, ScoreNoteEvent note, byte[] gray, int w, int h) {
        if (!(values instanceof OwnedNotes owned)) return null;
        HeadOrigin o = owned.origins.get(note);
        return o != null && o.gray == gray && o.width == w && o.height == h ? o : null;
    }

    static List<ScoreNoteEvent> rectified(
            List<ScoreNoteEvent> original,
            List<ScoreNoteEvent> mapped,
            List<ScoreNoteEvent> mappedSources,
            byte[] gray,
            int w,
            int h,
            byte[] flat,
            int bandHeight,
            float[] actualBottoms,
            int first,
            float staffBottom) {
        if (!(original instanceof OwnedNotes) || mapped.size() != mappedSources.size())
            return mapped;
        var evidence = new IdentityHashMap<ScoreNoteEvent, HeadOrigin>();
        byte[] canonicalSource = null, canonicalFlat = null;
        for (int i = 0; i < mapped.size(); i++) {
            HeadOrigin source = origin(original, mappedSources.get(i), gray, w, h);
            if (source == null) continue;
            if (source.originalGray == null || source.originalGray.length != (long) w * h) continue;
            byte[] originalFlat = flat;
            if (source.originalGray != gray) {
                if (flat == null || flat.length != (long) w * bandHeight) continue;
                if (actualBottoms == null || actualBottoms.length != w) continue;
                if (canonicalSource == null) {
                    canonicalSource = source.originalGray;
                    canonicalFlat = new byte[flat.length];
                    for (int yy = 0; yy < bandHeight; yy++)
                        for (int xx = 0; xx < w; xx++) {
                            int sy = Math.round(actualBottoms[xx] + (first + yy - staffBottom));
                            canonicalFlat[yy * w + xx] =
                                    sy >= 0 && sy < h ? canonicalSource[sy * w + xx] : (byte) 255;
                        }
                }
                if (canonicalSource != source.originalGray) continue;
                originalFlat = canonicalFlat;
            }
            HeadOrigin view =
                    source.rectified(
                            flat, bandHeight, actualBottoms, first, staffBottom, originalFlat);
            if (view != null) evidence.put(mapped.get(i), view);
        }
        return bind(mapped, evidence);
    }

    /** Original projection and mask BEFORE any dot/whole/half trimming. */
    static final class ProposalOrigin {
        final byte[] gray;
        final int width, height, top, bottom, left, right, firstInk, lastInk;
        final boolean[] mask;
        final boolean direct;

        ProposalOrigin(
                byte[] scan,
                byte[] ownership,
                int width,
                int height,
                int top,
                int bottom,
                boolean[] mask,
                int left,
                int right,
                int firstInk,
                int lastInk) {
            this.gray = ownership;
            this.width = width;
            this.height = height;
            this.top = top;
            this.bottom = bottom;
            this.left = left;
            this.right = right;
            this.firstInk = firstInk;
            this.lastInk = lastInk;
            this.mask = mask == null ? null : mask.clone();
            this.direct = scan == ownership;
        }

        boolean valid(byte[] g, int w, int h, int l, int r, int minY, int maxY) {
            return direct
                    && gray == g
                    && w == width
                    && h == height
                    && g != null
                    && g.length == (long) w * h
                    && top >= 1
                    && bottom < h - 1
                    && top <= bottom
                    && mask != null
                    && mask.length == bottom - top + 1
                    && l == left
                    && r == right
                    && left >= 1
                    && right < w - 1
                    && left <= right
                    && firstInk >= top
                    && lastInk <= bottom
                    && firstInk <= minY
                    && lastInk >= maxY
                    && minY <= maxY;
        }
    }
}
