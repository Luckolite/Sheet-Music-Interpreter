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
            return contains(x, y, null);
        }

        boolean contains(int x, int y, FaintQuarterRestInk.Budget quarterBudget) {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

            if (x < 0 || x >= width || y < 0 || y >= height) return false;
            if (source != null) {
                // EXACT existing rectifier sample; no inverse interpolation or AA dilation.
                int sy = Math.round(bottoms[x] + (first + y - staffBottom));
                return source.contains(x, sy, quarterBudget);
            }
            {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 64);
                for (int[] r : runs) {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    if (r[0] == y && x >= r[1] && x <= r[2]) return true;
                }
            }

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

    /** Optional identity-only original plane; never a glyph-role certificate. */
    static final class OriginalFrame {
        final byte[] source, view;
        final int width, height;
        final OwnedNotes notes;
        final Thread thread = Thread.currentThread();

        private OriginalFrame(byte[] source, byte[] view, int width, int height, OwnedNotes notes) {
            this.source = source;
            this.view = view;
            this.width = width;
            this.height = height;
            this.notes = notes;
        }

        boolean binds(
                List<ScoreNoteEvent> actualNotes,
                byte[] actual,
                int w,
                int h,
                ClosedCounterRestInk.Payer payer) {
            payer.take(24, 0);
            return thread == Thread.currentThread()
                    && notes == actualNotes
                    && view == actual
                    && width == w
                    && height == h
                    && source != null
                    && actual != null
                    && source.length == (long) w * h
                    && actual.length == source.length;
        }

        boolean rootedAt(byte[] root, int w, int h, ClosedCounterRestInk.Payer payer) {
            payer.take(24, 0);
            if (source == root && width == w && height == h) return true;
            int known = 0;
            for (var note : notes.values) {
                payer.take(24, 0);
                HeadOrigin origin = notes.origins.get(note);
                if (origin == null) continue;
                if (origin.source == null
                        || origin.source.originalGray != root
                        || origin.source.width != w
                        || origin.source.height != h) return false;
                known++;
            }
            return known > 0;
        }

        boolean intersectsHead(
                byte[] body,
                int bit,
                int left,
                int top,
                int w,
                int h,
                ClosedCounterRestInk.Payer payer) {
            for (var note : notes.values) {
                payer.take(16, 0);
                HeadOrigin origin = notes.origins.get(note);
                if (origin == null) continue;
                if (origin.source == null) {
                    for (int[] run : origin.runs) {
                        payer.take(16, 0);
                        int y = run[0] - top;
                        if (y < 0 || y >= h) continue;
                        int a = Math.max(0, run[1] - left), b = Math.min(w - 1, run[2] - left);
                        if (a > b) continue;
                        payer.take(8L * (b - a + 1), 0);
                        for (int x = a; x <= b; x++) if ((body[y * w + x] & bit) != 0) return true;
                    }
                } else {
                    // Registered rectifier mapping is used exactly, without inverse fitting.
                    payer.take(8L * w * h, 0);
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++)
                            if ((body[y * w + x] & bit) != 0
                                    && containsFunded(origin, left + x, top + y, payer))
                                return true;
                }
            }
            return false;
        }
    }

    private static boolean containsFunded(
            HeadOrigin origin, int x, int y, ClosedCounterRestInk.Payer payer) {
        payer.take(24, 0);
        if (x < 0 || x >= origin.width || y < 0 || y >= origin.height) return false;
        if (origin.source != null) {
            int sourceY = Math.round(origin.bottoms[x] + (origin.first + y - origin.staffBottom));
            return containsFunded(origin.source, x, sourceY, payer);
        }
        for (int[] run : origin.runs) {
            payer.take(16, 0);
            if (run[0] == y && x >= run[1] && x <= run[2]) return true;
        }
        return false;
    }

    static OriginalFrame originalFrame(
            List<ScoreNoteEvent> values,
            byte[] view,
            int w,
            int h,
            ClosedCounterRestInk.Payer payer) {
        if (payer == null) return null;
        try {
            payer.take(64, 0);
            if (!(values instanceof OwnedNotes owned)
                    || view == null
                    || w < 1
                    || h < 1
                    || view.length != (long) w * h) return null;
            byte[] source = null;
            int known = 0;
            for (var note : owned.values) {
                payer.take(24, 0);
                HeadOrigin origin = owned.origins.get(note);
                if (origin == null) continue;
                if (origin.gray != view
                        || origin.width != w
                        || origin.height != h
                        || origin.originalGray == null
                        || origin.originalGray.length != view.length) return null;
                if (source != null && source != origin.originalGray) return null;
                source = origin.originalGray;
                known++;
            }
            if (known == 0) return null;
            payer.take(32, 128);
            return new OriginalFrame(source, view, w, h, owned);
        } catch (ClosedCounterRestInk.Refusal refusal) {
            return null;
        }
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
