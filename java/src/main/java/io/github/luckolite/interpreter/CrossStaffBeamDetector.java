package io.github.luckolite.interpreter;

/** Proves an inter-staff beam from raw ink and both attached stems, not x spacing. */
final class CrossStaffBeamDetector {
    private CrossStaffBeamDetector() {}

    record Head(float x, float y, float gap) {}

    /** Physical beam ownership is independent of rests printed in another voice. */
    static java.util.List<ScoreNoteEvent> mark(
            java.util.List<ScoreNoteEvent> notes,
            java.util.List<Head> heads,
            byte[] gray,
            int width,
            int height) {
        if (notes.size() != heads.size()) throw new IllegalArgumentException("Head/event mismatch");
        var result = new java.util.ArrayList<>(notes);
        for (int i = 1; i < notes.size(); i++) {
            int prior = i - 1;
            while (prior >= 0 && hasProvedUnbeamedValue(notes.get(prior))) prior--;
            if (prior < 0) continue;
            var a = notes.get(prior);
            var b = notes.get(i);
            if (a.measureIndex() != b.measureIndex()
                    || a.staffCount() != 2
                    || b.staffCount() != 2
                    || a.staffIndex() == b.staffIndex()
                    || hasProvedUnbeamedValue(b)) continue;
            var first = heads.get(prior);
            var second = heads.get(i);
            if (connected(
                    gray,
                    width,
                    height,
                    first.x,
                    first.y,
                    second.x,
                    second.y,
                    (first.gap + second.gap) / 2)) {
                result.set(prior, result.get(prior).withCrossStaffBeam());
                result.set(i, result.get(i).withCrossStaffBeam());
            }
        }
        return markOpposed(result, heads, gray, width, height);
    }

    /** Additive opposed-pair proof requires the original heads' printed stem metadata. */
    static java.util.List<ScoreNoteEvent> markOpposed(java.util.List<ScoreNoteEvent> notes,
            java.util.List<Head> heads, byte[] gray, int width, int height) {
        if (notes.size() != heads.size()) throw new IllegalArgumentException("Head/event mismatch");
        var result = new java.util.ArrayList<>(notes);
        // Other voices may interleave between the two literal owners of one beam.
        // Require unique opposed-stem partners rather than treating list adjacency as a voice.
        int[] partner = new int[notes.size()];
        java.util.Arrays.fill(partner, -1);
        boolean[] ambiguous = new boolean[notes.size()];
        var byMeasure = new java.util.LinkedHashMap<Integer, java.util.List<Integer>>();
        for (int i = 0; i < notes.size(); i++)
            if (opposedSingleBeamValue(notes.get(i)))
                byMeasure
                        .computeIfAbsent(
                                notes.get(i).measureIndex(), ignored -> new java.util.ArrayList<>())
                        .add(i);
        int remainingCalls = 4096; // Optional proof refuses excessive work; legacy results survive.
        for (var owners : byMeasure.values())
            for (int left = 0; left < owners.size(); left++) {
                int i = owners.get(left);
                for (int right = left + 1; right < owners.size(); right++) {
                    int j = owners.get(right);
                    var a = notes.get(i);
                    var b = notes.get(j);
                    if (!opposedSingleBeamPair(a, b)) continue;
                    var first = heads.get(i);
                    var second = heads.get(j);
                    float gap = (first.gap + second.gap) / 2;
                    if (!Float.isFinite(gap + first.x + first.y + second.x + second.y)
                            || gap < 2
                            || second.x <= first.x
                            || second.x - first.x < gap * 1.5f
                            || second.x - first.x > gap * 9
                            || Math.abs(second.y - first.y) < gap * 4) continue;
                    int direction = first.y > second.y ? 1 : -1;
                    if (a.stemDirection() != direction || b.stemDirection() != -direction) continue;
                    if (--remainingCalls < 0) return result;
                    if (!connected(gray, width, height, first.x, first.y, second.x, second.y, gap))
                        continue;
                    if (partner[i] >= 0) ambiguous[i] = true;
                    if (partner[j] >= 0) ambiguous[j] = true;
                    partner[i] = j;
                    partner[j] = i;
                }
            }
        for (int i = 0; i < notes.size(); i++) {
            int j = partner[i];
            if (j >= 0 && !ambiguous[i] && !ambiguous[j] && partner[j] == i)
                result.set(i, result.get(i).withCrossStaffBeam());
        }
        return result;
    }

    /** Supported literal opposed-stem values; this does not infer a connection. */
    static boolean opposedSingleBeamPair(ScoreNoteEvent a, ScoreNoteEvent b) {
        return opposedSingleBeamValue(a)
                && opposedSingleBeamValue(b)
                && a.measureIndex() == b.measureIndex()
                && a.staffCount() == 2
                && b.staffCount() == 2
                && a.staffIndex() != b.staffIndex()
                && a.kind() == ScoreNoteEvent.Kind.PITCHED
                && b.kind() == ScoreNoteEvent.Kind.PITCHED
                && a.visualSize() == ScoreNoteEvent.VisualSize.FULL
                && b.visualSize() == ScoreNoteEvent.VisualSize.FULL
                && a.beamCount() == 1
                && b.beamCount() == 1
                && a.unbeamedDurationBeats() == 0
                && b.unbeamedDurationBeats() == 0
                && a.augmentationDots() >= 0
                && a.augmentationDots() <= 2
                && a.augmentationDots() == b.augmentationDots()
                && a.tupletDivisor() == b.tupletDivisor()
                && a.tupletNormalNotes() == b.tupletNormalNotes()
                && a.stemDirection() != 0
                && a.stemDirection() == -b.stemDirection();
    }

    static boolean opposedSingleBeamValue(ScoreNoteEvent note) {
        return note != null
                && note.measureIndex() >= 0
                && note.staffCount() == 2
                && (note.articulations() & NoteOrnament.GRACE) == 0
                && note.kind() == ScoreNoteEvent.Kind.PITCHED
                && note.visualSize() == ScoreNoteEvent.VisualSize.FULL
                && note.beamCount() == 1
                && note.unbeamedDurationBeats() == 0
                && note.augmentationDots() >= 0
                && note.augmentationDots() <= 2
                && note.stemDirection() != 0;
    }

    private static boolean hasProvedUnbeamedValue(ScoreNoteEvent note) {
        return note.beamCount() == 0 && note.unbeamedDurationBeats() >= 1;
    }

    static boolean connected(
            byte[] gray, int width, int height, float x1, float y1, float x2, float y2, float gap) {
        if (gray == null
                || gap < 2
                || x2 <= x1
                || Math.abs(y2 - y1) < gap * 4
                || x2 - x1 < gap * 1.5
                || x2 - x1 > gap * 9) return false;
        // Up-stems attach to the right of a head; down-stems to the left.
        boolean firstUp = y1 > y2;
        int stem1 = Math.round(x1 + (firstUp ? .6f : -.6f) * gap);
        int stem2 = Math.round(x2 + (firstUp ? -.6f : .6f) * gap);
        if (stem2 - stem1 < gap) return false;
        int top = Math.max(1, Math.round(Math.min(y1, y2) + gap * 2));
        int bottom = Math.min(height - 2, Math.round(Math.max(y1, y2) - gap * 2));
        for (int a = top; a <= bottom; a++) {
            if (!stem(gray, width, height, stem1, y1, a, gap)) continue;
            for (int b = Math.max(top, a - (int) (gap * 1.8));
                    b <= Math.min(bottom, a + (int) (gap * 1.8));
                    b++) {
                if (!stem(gray, width, height, stem2, y2, b, gap)) continue;
                int hits = 0, total = 0, thick = 0;
                for (int x = stem1 + 3; x < stem2 - 2; x++) {
                    int y = Math.round(a + (b - a) * (x - stem1) / (float) (stem2 - stem1));
                    total++;
                    if (dark(gray, width, height, x, y, 1)) hits++;
                    if (dark(gray, width, height, x, y - 1, 0)
                            && dark(gray, width, height, x, y + 1, 0)) thick++;
                }
                if (total > 0
                        && hits >= total * .93
                        && thick >= total * .45
                        && BeamInkConnectivity.connected(
                                gray, width, height, stem1, a, stem2, b, gap)) return true;
            }
        }
        // A hand can cross into the neighbouring staff while both shafts remain
        // above their heads (or below both heads). Its beam is outside the head
        // interval, so the opposed-stem search cannot establish that connection.
        return sameDirection(gray, width, height, x1, y1, x2, y2, gap, true)
                || sameDirection(gray, width, height, x1, y1, x2, y2, gap, false);
    }

    private static boolean sameDirection(
            byte[] gray,
            int width,
            int height,
            float x1,
            float y1,
            float x2,
            float y2,
            float gap,
            boolean up) {
        if (Math.abs(y2 - y1) > gap * 24) return false;
        int stem1 = Math.round(x1 + (up ? .6f : -.6f) * gap);
        int stem2 = Math.round(x2 + (up ? .6f : -.6f) * gap);
        int top =
                Math.max(
                        1,
                        Math.round(
                                up ? Math.min(y1, y2) - gap * 6 : Math.max(y1, y2) + gap * 1.5f));
        int bottom =
                Math.min(
                        height - 2,
                        Math.round(
                                up ? Math.min(y1, y2) - gap * 1.5f : Math.max(y1, y2) + gap * 6));
        for (int a = top; a <= bottom; a++) {
            if (!stem(gray, width, height, stem1, y1, a, gap)) continue;
            for (int b = Math.max(top, a - (int) (gap * 1.8));
                    b <= Math.min(bottom, a + (int) (gap * 1.8));
                    b++) {
                if (!stem(gray, width, height, stem2, y2, b, gap)) continue;
                int hits = 0, total = 0, thick = 0;
                for (int x = stem1 + 3; x < stem2 - 2; x++) {
                    int y = Math.round(a + (b - a) * (x - stem1) / (float) (stem2 - stem1));
                    total++;
                    if (dark(gray, width, height, x, y, 1)) hits++;
                    if (dark(gray, width, height, x, y - 1, 0)
                            && dark(gray, width, height, x, y + 1, 0)) thick++;
                }
                if (total > 0
                        && hits >= total * .93
                        && thick >= total * .45
                        && BeamInkConnectivity.connected(
                                gray, width, height, stem1, a, stem2, b, gap)) return true;
            }
        }
        return false;
    }

    private static boolean stem(byte[] gray, int w, int h, int x, float headY, int end, float gap) {
        int direction = end > headY ? 1 : -1;
        int start = Math.round(headY + direction * gap * .6f);
        int hits = 0, total = 0, blank = 0;
        for (int y = start; direction > 0 ? y < end : y > end; y += direction) {
            total++;
            if (dark(gray, w, h, x, y, 2)) {
                hits++;
                blank = 0;
            } else if (++blank > Math.max(1, Math.round(gap * .16f))) return false;
        }
        return total >= gap * 1.4 && hits >= total * .92;
    }

    private static boolean dark(byte[] gray, int w, int h, int x, int y, int radius) {
        if (y < 0 || y >= h) return false;
        for (int k = Math.max(0, x - radius); k <= Math.min(w - 1, x + radius); k++)
            if ((gray[y * w + k] & 255) < 150) return true;
        return false;
    }
}
