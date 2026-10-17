// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A complete metronome equation below a printed part header can own the opening staff. */
final class OpeningPartTempo {
    // Reuse ScoreCreditsDetector's instrument/voice label vocabulary and its short part aliases.
    private static final String INSTRUMENT =
            "(?iu)^(?:for\\h+)?(?:(?:soprano|alto|tenor|bass|treble|descant)\\h+)?(?:violin|viola|cello|piano|harp|guitar|flute|clarinet|oboe|bassoon|trumpet|trombone|horn|tuba|saxophone|recorder|percussion|drums|voices?|vocals?|vln[.]|vla[.]|vc[.]|fl[.]|cl[.]|ob[.]|bsn[.]|pno[.])(?:\\h+(?:solo|duet|part|[1-9]|I{1,3}))?$";

    static boolean part(String value) {
        return value != null && value.length() <= 80 && value.strip().matches(INSTRUMENT);
    }

    private static boolean valid(PlayingTechniqueDetector.Word w) {
        return w != null
                && w.text() != null
                && w.text().length() <= 300
                && Float.isFinite(w.left() + w.right() + w.top() + w.bottom())
                && w.left() >= 0
                && w.top() >= 0
                && w.right() <= 1
                && w.bottom() <= 1
                && w.left() < w.right()
                && w.top() < w.bottom();
    }

    static boolean equationWord(
            PlayingTechniqueDetector.Word word, MeasureNumberReconciler.NumberToken token) {
        return valid(word)
                && token != null
                && token.value() >= 30
                && token.value() <= 400
                && Float.isFinite(
                        token.left()
                                + token.top()
                                + token.right()
                                + token.bottom()
                                + token.annotationLeft())
                && token.left() >= word.left() - .001f
                && token.right() <= word.right() + .001f
                && token.top() >= word.top() - .001f
                && token.bottom() <= word.bottom() + .001f
                && word.text()
                        .strip()
                        .matches("[\u2669\u266a\u266b\u266c\u221aJ.]?\\h*=\\h*" + token.value());
    }

    private static boolean printedWord(
            PlayingTechniqueDetector.Word word, byte[] gray, int width, int height) {
        int left = Math.round(word.left() * width),
                right = Math.min(width - 1, Math.round(word.right() * width));
        int top = Math.round(word.top() * height),
                bottom = Math.min(height - 1, Math.round(word.bottom() * height));
        long area = (long) (right - left + 1) * (bottom - top + 1);
        if (area < 9 || area > 500000) return false;
        int ink = 0, minX = right, maxX = left, minY = bottom, maxY = top;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++)
                if ((gray[y * width + x] & 255) < 165) {
                    ink++;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
        return ink >= Math.max(8, area * .015f)
                && ink <= area * .75f
                && maxX - minX >= (right - left) * .5f
                && maxY - minY >= (bottom - top) * .4f;
    }

    static int firstMeasure(List<MeasureRegion> measures) {
        if (measures == null || measures.isEmpty()) return -1;
        int first = 0;
        for (int i = 1; i < measures.size(); i++) {
            var a = measures.get(i);
            var b = measures.get(first);
            if (a.top() < b.top() - .002f
                    || Math.abs(a.top() - b.top()) <= .002f && a.left() < b.left()) first = i;
        }
        return first;
    }

    static int owner(
            PlayingTechniqueDetector.Word equation,
            List<PlayingTechniqueDetector.Word> words,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            Set<PlayingTechniqueDetector.Word> ownedLaterEquations) {
        if (!valid(equation)
                || words == null
                || words.size() > 10000
                || gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || measures == null
                || measures.isEmpty()) return -1;
        int first = firstMeasure(measures);
        var opening = measures.get(first);
        var staffs = RawStaffLineDetector.detect(gray, width, height);
        RawStaffLineDetector.StaffLines staff = null;
        for (var s : staffs)
            if (Math.abs(s.top() - opening.top() * height) <= s.gap() * .3f
                    && s.bottom() <= opening.bottom() * height) {
                if (staff != null) return -1;
                staff = s;
            }
        if (staff == null) return -1;
        float gap = staff.gap();
        // A grand-staff/parallel-part header needs its own voice ownership; abstain here.
        for (var s : staffs) if (s != staff && s.top() < staff.bottom() + gap * 6.75f) return -1;
        float rowRight = opening.right();
        for (var m : measures)
            if (Math.abs(m.top() - opening.top()) * height < gap)
                rowRight = Math.max(rowRight, m.right());
        float rowWidth = (rowRight - opening.left()) * width;
        if (equation.bottom() * height >= staff.top() - gap * 2
                || equation.left() * width < opening.left() * width - gap * 2
                || equation.right() > opening.right()) return -1;
        List<PlayingTechniqueDetector.Word> captions = new ArrayList<>();
        for (var w : words)
            if (valid(w)
                    && part(w.text())
                    && w.bottom() < equation.top()
                    && w.left() * width >= opening.left() * width - gap * 10
                    && w.right() <= rowRight
                    && (equation.top() - w.bottom()) * height
                            <= Math.max(gap * 6, (w.bottom() - w.top()) * height * 4)
                    && (w.bottom() - w.top()) * height >= gap * .5f
                    && (w.bottom() - w.top()) <= (equation.bottom() - equation.top()) * 2.5f) {
                if (!captions.contains(w)) captions.add(w);
            }
        if (captions.size() != 1) return -1;
        var caption = captions.get(0);
        if (caption.right() > opening.right() || !printedWord(caption, gray, width, height))
            return -1;
        // Independently observed header rows precede the part caption. No title/artist hints.
        List<PlayingTechniqueDetector.Word> headings = new ArrayList<>();
        for (var w : words)
            if (valid(w)
                    && !part(w.text())
                    && w.bottom() < caption.top()
                    && w.text().codePoints().filter(Character::isLetter).count() >= 3
                    && printedWord(w, gray, width, height)
                    && !w.text().matches(".*[=0-9\u2669\u266a].*")
                    && Math.abs((w.left() + w.right() - opening.left() - rowRight) * width * .5f)
                            <= rowWidth * .2f) headings.add(w);
        boolean pairedHeader = false;
        for (var a : headings)
            for (var b : headings)
                if (a != b
                        && a.bottom() < b.top()
                        && Math.min(a.right(), b.right()) > Math.max(a.left(), b.left()))
                    pairedHeader = true;
        if (!pairedHeader) return -1;
        for (var w : words)
            if (valid(w)
                    && !w.equals(equation)
                    && !w.equals(caption)
                    && (ownedLaterEquations == null || !ownedLaterEquations.contains(w))
                    && w.bottom() > caption.bottom()
                    && (w.top() * height < staff.top() - gap * 2
                            || w.text().codePoints().filter(Character::isLetter).count() >= 2
                                    && w.top() * height < staff.top())
                    && w.right() * width > opening.left() * width - gap * 10
                    && w.left() < rowRight) return -1;
        int left = Math.max(0, Math.round(equation.left() * width)),
                right = Math.min(width - 1, Math.round(equation.right() * width));
        int top = Math.round(equation.bottom() * height) + Math.max(2, Math.round(gap * .25f));
        int bottom = (int) Math.floor(staff.top() - gap * 2);
        if (left >= right
                || top < 0
                || bottom >= height
                || bottom <= top
                || (long) (right - left + 1) * (bottom - top + 1) > 1000000) return -1;
        // Positive, completely clear projected space joins the caption-owned equation to the staff.
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) if ((gray[y * width + x] & 255) < 225) return -1;
        return first;
    }
}
