// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Bounded text strips from agreeing written-head geometry on a gently tilted staff. */
final class TechniqueTextRegions {
    record Region(
            int measure,
            PlayingTechniqueDetector.Staff staff,
            int left,
            int top,
            int right,
            int bottom,
            float meanX,
            float meanBottom,
            float slope,
            float measureLeft,
            float measureRight) {
        float floor(float x) {
            return meanBottom + slope * (x - meanX);
        }
    }

    private record Point(float x, float bottom) {}

    private TechniqueTextRegions() {}

    static List<Region> above(
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int width,
            int height) {
        List<Region> result = new ArrayList<>();
        for (int m = 0; m < measures.size(); m++) {
            var measure = measures.get(m);
            for (var staff : staffs) {
                float gap = staff.gap(), center = (staff.top() + staff.bottom()) * .5f / height;
                if (center < measure.top() - gap / height
                        || center > measure.bottom() + gap / height) continue;
                List<Point> points = new ArrayList<>();
                int columnCount = 0, firstColumn = 0, secondColumn = 0;
                float min = Float.MAX_VALUE, max = -Float.MAX_VALUE, sx = 0, sy = 0;
                for (var note : notes) {
                    if (note.measureIndex() != m
                            || note.staffIndex() != staff.index()
                            || note.staffCount() != staff.count()
                            || note.crossStaffBeam()
                            || note.pageY() <= 0) continue;
                    // Written staff step is optical position. Octave playback shifts remain
                    // separate.
                    float floor = note.pageY() * height + note.staffStep() * gap * .5f;
                    if (Math.abs(floor - staff.bottom()) > gap * 2.5f) continue;
                    float x =
                            (measure.left()
                                            + note.positionInMeasure()
                                                    * (measure.right() - measure.left()))
                                    * width;
                    points.add(new Point(x, floor));
                    // Only three distinct rounded columns are needed as slope witnesses.
                    if (columnCount < 3) {
                        int column = Math.round(x);
                        if (columnCount == 0) {
                            firstColumn = column;
                            columnCount = 1;
                        } else if (column != firstColumn) {
                            if (columnCount == 1) {
                                secondColumn = column;
                                columnCount = 2;
                            } else if (column != secondColumn) columnCount = 3;
                        }
                    }
                    min = Math.min(min, x);
                    max = Math.max(max, x);
                    sx += x;
                    sy += floor;
                }
                // Chord heads at one column are not independent witnesses of a row's slope.
                if (points.size() < 3 || columnCount < 3 || max - min < gap * 4) continue;
                float mx = sx / points.size(), my = sy / points.size();
                double spread = 0, covariance = 0;
                for (var point : points) {
                    double dx = point.x - mx;
                    spread += dx * dx;
                    covariance += dx * (point.bottom - my);
                }
                float slope = (float) (covariance / spread);
                if (!Float.isFinite(slope) || Math.abs(slope) > .12f) continue;
                boolean agree = true;
                for (var point : points)
                    if (Math.abs(point.bottom - my - slope * (point.x - mx)) > gap * .35f)
                        agree = false;
                if (!agree) continue;
                int left = Math.max(0, (int) Math.floor(measure.left() * width - gap * .75f));
                int right = Math.min(width, (int) Math.ceil(measure.right() * width + gap * .75f));
                float a = my + slope * (left - mx), b = my + slope * (right - mx);
                if (Math.max(Math.abs(a - staff.bottom()), Math.abs(b - staff.bottom()))
                        > gap * 2.5f) continue;
                int top = Math.max(0, (int) Math.floor(Math.min(a, b) - gap * 8.4f));
                int bottom = Math.min(height, (int) Math.ceil(Math.max(a, b) - gap * 3.5f));
                if (right <= left || bottom <= top) continue;
                result.add(
                        new Region(
                                m,
                                staff,
                                left,
                                top,
                                right,
                                bottom,
                                mx,
                                my,
                                slope,
                                measure.left() * width,
                                measure.right() * width));
            }
        }
        return List.copyOf(result);
    }

    static PlayingTechniqueDetector.Staff local(
            PlayingTechniqueDetector.Staff original, float x, List<Region> regions) {
        Region best = null;
        float distance = Float.MAX_VALUE;
        for (var region : regions) {
            if (!region.staff.equals(original) || x < region.left || x > region.right) continue;
            float d = Math.max(region.measureLeft - x, Math.max(0, x - region.measureRight));
            if (d < distance) {
                distance = d;
                best = region;
            }
        }
        if (best == null) return original;
        float floor = best.floor(x);
        return new PlayingTechniqueDetector.Staff(
                floor - original.gap() * 4,
                floor,
                original.gap(),
                original.index(),
                original.count());
    }

    static List<PlayingTechniqueDetector.Word> readings(
            OcrText text, Region region, int scale, int width, int height) {
        List<PlayingTechniqueDetector.Word> result = new ArrayList<>();
        for (var block : text.blocks())
            for (var line : block.lines()) {
                if (line.text().toLowerCase(java.util.Locale.ROOT).matches(".*\\b(non|senza)\\b.*"))
                    continue;
                for (var element : line.elements()) {
                    int type = PlayingTechniqueDetector.technique(element.text());
                    if (type != ScoreTechniqueChange.ARCO
                            && type != ScoreTechniqueChange.PIZZICATO
                            && type != ScoreTechniqueChange.ORDINARIO) continue;
                    var box = element.box();
                    if (box == null) continue;
                    float left = region.left + box.left / (float) scale,
                            top = region.top + box.top / (float) scale;
                    float right = region.left + box.right / (float) scale,
                            bottom = region.top + box.bottom / (float) scale;
                    if (right <= left
                            || bottom <= top
                            || left < region.left
                            || right > region.right
                            || top < region.top
                            || bottom > region.bottom) continue;
                    result.add(
                            new PlayingTechniqueDetector.Word(
                                    element.text(),
                                    left / width,
                                    top / height,
                                    right / width,
                                    bottom / height));
                }
            }
        return List.copyOf(result);
    }

    static List<PlayingTechniqueDetector.Word> consensus(
            List<PlayingTechniqueDetector.Word> first,
            List<PlayingTechniqueDetector.Word> second,
            float gap,
            int width,
            int height) {
        List<PlayingTechniqueDetector.Word> result = new ArrayList<>();
        for (var a : first)
            for (var b : second) {
                if (PlayingTechniqueDetector.technique(a.text())
                        != PlayingTechniqueDetector.technique(b.text())) continue;
                if (Math.abs((a.left() + a.right() - b.left() - b.right()) * .5f) * width
                                > gap * .8f
                        || Math.abs((a.top() + a.bottom() - b.top() - b.bottom()) * .5f) * height
                                > gap * .8f) continue;
                if (!result.contains(a)) result.add(a);
                break;
            }
        return List.copyOf(result);
    }
}
