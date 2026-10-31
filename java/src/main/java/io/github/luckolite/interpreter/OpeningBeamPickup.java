// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Source-raster qualification of a complete short opening beam and silent companion staves. */
final class OpeningBeamPickup {
    static ScorePageInterpretation apply(
            ScorePageInterpretation s,
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<ScoreMeterChange> headerMeters,
            List<MeterChangeDetector.Crop> headers) {
        return apply(s, labels, gray, w, h, staffs, headerMeters, headers, null);
    }

    static ScorePageInterpretation apply(
            ScorePageInterpretation s,
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<ScoreMeterChange> headerMeters,
            List<MeterChangeDetector.Crop> headers,
            OmrScoreInterpreter.Analysis analysis) {
        if (s == null
                || s.firstMeasureNumber() > 1
                || s.measures().size() < 3
                || gray == null
                || labels == null
                || gray.length != (long) w * h
                || labels.length != gray.length) return s;
        var meters =
                s.meterChanges().stream().filter(m -> m.measureIndex() == 0).distinct().toList();
        var selectedHeaders =
                headerMeters.stream().filter(m -> m.measureIndex() == 0).distinct().toList();
        if (meters.size() != 1
                || selectedHeaders.size() != 1
                || !selectedHeaders.get(0).equals(meters.get(0))) return s;
        var opening = s.notes().stream().filter(n -> n.measureIndex() == 0).toList();
        if (opening.isEmpty()) return s;
        int count = opening.get(0).staffCount();
        if (count < 1 || count > 16) return s;
        var region = s.measures().get(0);
        if (opening.stream()
                .anyMatch(
                        n ->
                                n.staffCount() != count
                                        || n.staffIndex() < 0
                                        || n.staffIndex() >= count
                                        || n.kind() != ScoreNoteEvent.Kind.PITCHED
                                        || n.beamCount() < 1
                                        || n.beamCount() > 4
                                        || n.tiedFromPrevious()
                                        || n.crossStaffBeam()
                                        || n.boundaryTies() != 0
                                        || n.leadingRestBeats() > 0
                                        || n.followingRestBeats() > 0
                                        || (n.articulations() & NoteOrnament.GRACE) != 0)) return s;
        OwnedLayout layout = null;
        boolean attemptedLayout = false;
        for (int lane = 0; lane < count; lane++) {
            final int staff = lane;
            var notes =
                    opening.stream()
                            .filter(n -> n.staffIndex() == staff)
                            .sorted(Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure))
                            .toList();
            var rests =
                    s.rests().stream()
                            .filter(r -> r.measureIndex() == 0 && r.staffIndex() == staff)
                            .toList();
            if (notes.isEmpty()) {
                if (rests.size() != 1
                        || !rests.get(0).isFullMeasure()
                        || rests.get(0).staffCount() != count) return s;
                continue;
            }
            if (!rests.isEmpty() || notes.size() < 2 || notes.size() > 32) return s;
            var frames =
                    staffs.stream()
                            .filter(
                                    t ->
                                            t.index() == staff
                                                    && t.count() == count
                                                    && t.top() >= region.top() * h
                                                    && t.bottom() <= region.bottom() * h)
                            .toList();
            if (frames.size() != 1) return s;
            var frame = frames.get(0);
            float gap = frame.gap();
            if (!Float.isFinite(gap) || gap < 6) return s;
            var ownedHeaders =
                    headers.stream()
                            .filter(
                                    c ->
                                            Math.abs(c.firstLine() - frame.top()) < gap * .4f
                                                    && MeterChangeDetector.followingMeasure(
                                                                    c, w, h, s.measures())
                                                            == 0)
                            .toList();
            if (ownedHeaders.isEmpty()) return s;
            int
                    headerRight =
                            ownedHeaders.stream()
                                    .mapToInt(MeterChangeDetector.Crop::right)
                                    .min()
                                    .orElseThrow(),
                    direction = notes.get(0).stemDirection();
            float[] xs = new float[notes.size()], ys = new float[notes.size()];
            for (int i = 0; i < notes.size(); i++) {
                var n = notes.get(i);
                if (n.stemDirection() != direction) return s;
                xs[i] =
                        (region.left() + n.positionInMeasure() * (region.right() - region.left()))
                                * w;
                ys[i] = n.pageY() * h;
            }
            for (var header : ownedHeaders)
                if (xs[0] <= header.right() || xs[0] - header.right() > gap * 3) return s;
            boolean extentFallback = region.right() * w - xs[xs.length - 1] > gap * 3;
            if (extentFallback) {
                if (!attemptedLayout) {
                    layout = ownedLayout(s, analysis, labels, gray, w, h, staffs, headers);
                    attemptedLayout = true;
                }
                int terminal = s.notes().indexOf(notes.get(notes.size() - 1));
                if (layout == null
                        || terminal < 0
                        || region.right() * w - layout.maxX[terminal] > gap * 3) return s;
            }
            if (!CompleteBeamGroupInk.proves(gray, w, h, xs, ys, gap, direction)) return s;
            // Every segmented head in the usable opening lane must have a decoded owner.
            int top = Math.max(0, Math.round(frame.top() - gap * 3)),
                    bottom = Math.min(h - 1, Math.round(frame.bottom() + gap * 3));
            for (int y = top; y <= bottom; y++)
                for (int x = Math.max(0, headerRight);
                        x < Math.min(w, Math.round(region.right() * w));
                        x++) {
                    if (labels[y * w + x] != OmrMeasurePostProcessor.NOTEHEAD) continue;
                    if (extentFallback) {
                        int owner = layout.owner(y * w + x);
                        if (owner < 0
                                || s.notes().get(owner).measureIndex() != 0
                                || s.notes().get(owner).staffIndex() != staff) return s;
                    } else {
                        boolean owned = false;
                        for (int i = 0; i < xs.length; i++)
                            if (Math.abs(x - xs[i]) <= gap && Math.abs(y - ys[i]) <= gap * .65f) {
                                owned = true;
                                break;
                            }
                        if (!owned) return s;
                    }
                }
        }
        var edited = new ArrayList<ScoreNoteEvent>();
        var owners = new ArrayList<Integer>();
        for (int i = 0; i < s.notes().size(); i++) {
            var n = s.notes().get(i);
            edited.add(n.measureIndex() == 0 ? n.withCompactOpening() : n);
            owners.add(i);
        }
        var candidate = s.withNotes(edited, owners);
        float nominal = (float) meters.get(0).quarterBeats();
        return Double.isFinite(ScoreOpeningDuration.provedQuarterBeats(candidate, nominal))
                ? candidate
                : s;
    }

    /** Complete unsplit source footprints are local layout evidence, never clock authority. */
    private static final class OwnedLayout {
        final long[] pixels;
        final int[] maxX;

        OwnedLayout(long[] pixels, int[] maxX) {
            this.pixels = pixels;
            this.maxX = maxX;
        }

        int owner(int pixel) {
            int lo = 0, hi = pixels.length - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                long key = pixels[mid] >>> 32;
                if (key < pixel) lo = mid + 1;
                else if (key > pixel) hi = mid - 1;
                else return (int) pixels[mid];
            }
            return -1;
        }
    }

    private static OwnedLayout ownedLayout(
            ScorePageInterpretation score,
            OmrScoreInterpreter.Analysis analysis,
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeterChangeDetector.Crop> headers) {
        if (analysis == null
                || analysis.clockContextSource() == null
                || analysis.clockProducerSource() == null) return null;
        long size = (long) w * h;
        if (w < 1 || h < 1 || size != labels.length || size != gray.length) return null;
        var producer = analysis.clockProducerSource();
        var origin = analysis.clockContextSource().origin();
        if (origin.width() != w
                || origin.height() != h
                || producer.finalNotes() != analysis.notes().size()
                || analysis.notes().size() > size
                || score.notes().size() > size
                || producer.emissions().size() > size
                || score.measures().size() > size
                || staffs.size() > h
                || headers.size() > size) return null;
        if (!origin.rasterSha256().equals(producer.rawSha())
                || !origin.labelsSha256().equals(producer.labelSha())
                || producer.rawSha().isEmpty()
                || producer.labelSha().isEmpty()) return null;
        var budget = new ExactComponentMembers.Budget(64L * size + 4096, 8L * size + 4096);
        if (!budget.take(2L * size, 256)
                || !producer.rawSha().equals(ScoreClockContextCapture.digest(gray))
                || !producer.labelSha().equals(ScoreClockContextCapture.digest(labels)))
            return null;
        if (!budget.take(
                analysis.notes().size() + score.notes().size(),
                128L * (analysis.notes().size() + score.notes().size()))) return null;
        var originalCounts = new HashMap<ScoreNoteEvent, Integer>();
        var finalCounts = new HashMap<ScoreNoteEvent, Integer>();
        var originalIndex = new HashMap<ScoreNoteEvent, Integer>();
        for (int i = 0; i < analysis.notes().size(); i++) {
            var n = analysis.notes().get(i);
            originalCounts.merge(n, 1, Integer::sum);
            originalIndex.put(n, i);
        }
        for (var n : score.notes()) finalCounts.merge(n, 1, Integer::sum);
        if (!budget.take(score.notes().size(), 36L * score.notes().size())) return null;
        int[] finalToOriginal = new int[score.notes().size()];
        Arrays.fill(finalToOriginal, -1);
        int[] selectedEmission = new int[score.notes().size()];
        Arrays.fill(selectedEmission, -1);
        int[] maximum = new int[score.notes().size()];
        Arrays.fill(maximum, -1);
        var region = score.measures().get(0);
        int[] minimumX = new int[score.notes().size()],
                top = new int[score.notes().size()],
                bottom = new int[score.notes().size()];
        float[] gaps = new float[score.notes().size()],
                xs = new float[score.notes().size()],
                ys = new float[score.notes().size()];
        for (int i = 0; i < score.notes().size(); i++) {
            var n = score.notes().get(i);
            if (n.measureIndex() != 0) continue;
            if (originalCounts.getOrDefault(n, 0) != 1 || finalCounts.getOrDefault(n, 0) != 1)
                return null;
            finalToOriginal[i] = originalIndex.get(n);
            PlayingTechniqueDetector.Staff frame = null;
            for (var f : staffs) {
                if (!budget.take(1, 0)) return null;
                if (f.index() == n.staffIndex()
                        && f.count() == n.staffCount()
                        && f.top() >= region.top() * h
                        && f.bottom() <= region.bottom() * h) {
                    if (frame != null) return null;
                    frame = f;
                }
            }
            if (frame == null || !Float.isFinite(frame.gap()) || frame.gap() < 6) return null;
            gaps[i] = frame.gap();
            minimumX[i] = w;
            for (var header : headers) {
                if (!budget.take(score.measures().size() + 1L, 0)) return null;
                if (Math.abs(header.firstLine() - frame.top()) < gaps[i] * .4f
                        && MeterChangeDetector.followingMeasure(header, w, h, score.measures())
                                == 0)
                    minimumX[i] = Math.min(minimumX[i], Math.max(0, header.right()));
            }
            if (minimumX[i] == w) return null;
            top[i] = Math.max(0, Math.round(frame.top() - gaps[i] * 3));
            bottom[i] = Math.min(h - 1, Math.round(frame.bottom() + gaps[i] * 3));
            xs[i] = (region.left() + n.positionInMeasure() * (region.right() - region.left())) * w;
            ys[i] = n.pageY() * h;
        }
        var emissions = producer.emissions();
        long total = 0;
        for (int j = 0; j < emissions.size(); j++) {
            var e = emissions.get(j);
            if (e.index() != j || !budget.take(1, 0)) return null;
            for (int i = 0; i < finalToOriginal.length; i++) {
                if (!budget.take(e.finalNoteOwners().size() + 1L, 0)) return null;
                if (finalToOriginal[i] < 0 || !e.finalNoteOwners().contains(finalToOriginal[i]))
                    continue;
                if (selectedEmission[i] >= 0
                        || e.finalNoteOwners().size() != 1
                        || e.parent() != -1
                        || e.original() != j
                        || e.pixels().isEmpty()
                        || e.nonNoteRole() != null) return null;
                selectedEmission[i] = j;
                total += e.pixels().size();
                if (total > size) return null;
            }
        }
        if (!budget.take(total, 8L * total)) return null;
        long[] members = new long[(int) total];
        int at = 0;
        for (int i = 0; i < selectedEmission.length; i++)
            if (finalToOriginal[i] >= 0) {
                int id = selectedEmission[i];
                if (id < 0) return null;
                for (var e : emissions) {
                    if (!budget.take(1, 0)) return null;
                    if (e.index() != id && (e.parent() == id || e.original() == id)) return null;
                }
                for (int pixel : emissions.get(id).pixels()) {
                    if (!budget.take(1, 0)
                            || pixel < 0
                            || pixel >= size
                            || labels[pixel] != OmrMeasurePostProcessor.NOTEHEAD) return null;
                    int x = pixel % w, y = pixel / w;
                    if (x < minimumX[i]
                            || x >= Math.min(w, Math.round(region.right() * w))
                            || y < top[i]
                            || y > bottom[i]
                            || Math.abs(x - xs[i]) > gaps[i]
                            || Math.abs(y - ys[i]) > gaps[i] * .65f) return null;
                    members[at++] = ((long) pixel << 32) | (i & 0xffffffffL);
                    maximum[i] = Math.max(maximum[i], x);
                }
            }
        if (at != members.length) return null;
        int log = 64 - Long.numberOfLeadingZeros(Math.max(1, total));
        if (!budget.take(total * (log + 1L), 0)) return null;
        Arrays.sort(members);
        for (int i = 1; i < members.length; i++)
            if ((members[i] >>> 32) == (members[i - 1] >>> 32)) return null;
        var result = new OwnedLayout(members, maximum);
        for (var e : emissions)
            for (int pixel : e.pixels()) {
                if (!budget.take(log + 1L, 0) || pixel < 0 || pixel >= size) return null;
                int owner = result.owner(pixel);
                if (owner >= 0 && selectedEmission[owner] != e.index()) return null;
            }
        // Any extent fallback authenticates coverage of every opening lane, independent of lane
        // order.
        var scanned = new HashSet<Integer>();
        for (int i = 0; i < finalToOriginal.length; i++)
            if (finalToOriginal[i] >= 0 && scanned.add(score.notes().get(i).staffIndex())) {
                int lane = score.notes().get(i).staffIndex();
                for (int y = top[i]; y <= bottom[i]; y++)
                    for (int x = minimumX[i];
                            x < Math.min(w, Math.round(region.right() * w));
                            x++) {
                        if (!budget.take(1, 0)) return null;
                        int pixel = y * w + x;
                        if (labels[pixel] != OmrMeasurePostProcessor.NOTEHEAD) continue;
                        if (!budget.take(log + 1L, 0)) return null;
                        int owner = result.owner(pixel);
                        if (owner < 0
                                || score.notes().get(owner).measureIndex() != 0
                                || score.notes().get(owner).staffIndex() != lane) return null;
                    }
            }
        return result;
    }
}
