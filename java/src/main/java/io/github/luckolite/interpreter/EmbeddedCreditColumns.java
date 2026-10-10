// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.text.Normalizer;
import io.github.luckolite.interpreter.ScoreCreditsDetector;
import io.github.luckolite.interpreter.ScoreCreditsDetector.Line;
import io.github.luckolite.interpreter.ScoreCreditsDetector.Page;

/** Repair a credit row only when a separately located instrument caption crossed columns. */
public final class EmbeddedCreditColumns {
    private EmbeddedCreditColumns() {}

    public record Evidence(Line row, Line broad, Line caption) {}

    private static String key(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    public static boolean needsWords(Page p, List<Line> embedded) {
        return p.notationTop() > 0
                && embedded.stream()
                        .anyMatch(
                                l ->
                                        ScoreCreditsDetector.isPrintedCredit(l.text())
                                                && l.right() - l.left() > p.width() * .65f
                                                && l.bottom() < p.notationTop());
    }

    private static boolean caption(Line l) {
        return l.text()
                .strip()
                .matches(
                        "(?i)(?:violin|viola|cello|piano|flute|clarinet|oboe|bassoon|trumpet|trombone|guitar|harp|organ|voice)");
    }

    public static List<Evidence> candidates(Page p, List<Line> embedded, List<Line> words) {
        if (!needsWords(p, embedded)) return List.of();
        List<Evidence> result = new ArrayList<>();
        for (Line broad : embedded) {
            if (!ScoreCreditsDetector.isPrintedCredit(broad.text())
                    || broad.right() - broad.left() <= p.width() * .65f
                    || broad.bottom() >= p.notationTop()) continue;
            for (Line cap : words) {
                if (!caption(cap)
                        || cap.left() < broad.left() - 2
                        || cap.right() > broad.right()
                        || Math.min(cap.bottom(), broad.bottom())
                                <= Math.max(cap.top(), broad.top())) continue;
                for (Line seed : words) {
                    if (!seed.text()
                                    .matches(
                                            "(?i)(?:music|composed|arranged|performed|words|lyrics)")
                            || seed.left() - cap.right() < (seed.bottom() - seed.top()) * 4
                            || seed.top() > p.height() * .3f
                            || seed.bottom() >= p.notationTop()
                            || Math.abs(seed.top() - broad.top())
                                    > (broad.bottom() - broad.top()) * 1.5f) continue;
                    List<Line> row = new ArrayList<>();
                    for (Line w : words) {
                        if (w.left() < seed.left() - 1
                                || w.right() > broad.right() + 2
                                || Math.abs(
                                                (w.top() + w.bottom() - seed.top() - seed.bottom())
                                                        * .5f)
                                        > Math.min(
                                                        (w.bottom() - w.top()),
                                                        (seed.bottom() - seed.top()))
                                                * .4f) continue;
                        row.add(w);
                    }
                    row.sort(Comparator.comparingDouble(Line::left));
                    if (row.size() < 4 || row.size() > 24) continue;
                    boolean good = true;
                    StringBuilder text = new StringBuilder();
                    float top = seed.top(), bottom = seed.bottom(), right = seed.left();
                    for (Line w : row) {
                        if (w.left() < right - 1
                                || w.left() - right > (seed.bottom() - seed.top()) * 2) {
                            good = false;
                            break;
                        }
                        if (text.length() > 0) text.append(' ');
                        text.append(w.text());
                        top = Math.min(top, w.top());
                        bottom = Math.max(bottom, w.bottom());
                        right = w.right();
                    }
                    if (!good || !ScoreCreditsDetector.isPrintedCredit(text.toString())) continue;
                    Line located = new Line(text.toString(), seed.left(), top, right, bottom);
                    if (result.stream()
                            .noneMatch(
                                    e ->
                                            key(e.row.text()).equals(key(located.text()))
                                                    && Math.abs(e.row.top() - located.top()) < 2))
                        result.add(new Evidence(located, broad, cap));
                    if (result.size() >= 6) return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    public static List<Line> merge(
            Page p, Evidence e, List<String> readings, List<Float> confidence) {
        if (readings.size() != 3 || confidence.size() != 3) return p.lines();
        for (int i = 0; i < 3; i++)
            if (!Float.isFinite(confidence.get(i))
                    || confidence.get(i) < .98f
                    || !key(readings.get(i)).equals(key(e.row.text()))
                    || !ScoreCreditsDetector.isPrintedCredit(readings.get(i))) return p.lines();
        String confirmed = readings.get(0).strip().replaceAll("\\s+", " ");
        for (String s : readings)
            if (!s.strip().replaceAll("\\s+", " ").equals(confirmed)) return p.lines();
        List<Line> result = new ArrayList<>(p.lines());
        String expected = key(e.row.text()), suffix = key(e.caption.text());
        result.removeIf(
                l -> {
                    if (!ScoreCreditsDetector.isPrintedCredit(l.text())) return false;
                    if (key(l.text()).equals(expected)
                            && Math.abs(l.top() - e.row.top()) < (e.row.bottom() - e.row.top()))
                        return true;
                    return key(l.text()).equals(expected + suffix)
                            && l.left() <= e.caption.left() + 2
                            && l.right() >= e.row.right() - 2
                            && Math.min(l.bottom(), e.row.bottom())
                                    > Math.max(l.top(), e.row.top());
                });
        result.add(new Line(confirmed, e.row.left(), e.row.top(), e.row.right(), e.row.bottom()));
        return List.copyOf(result);
    }
}
