// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Printed SMuFL meter digits on a tab staff, including a short opening measure. */
public final class TabMeter {
    private TabMeter() {}

    public static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            List<TablatureDecoder.Staff> tabs,
            List<TablatureDecoder.Word> words,
            int w,
            int h) {
        var changes = new ArrayList<>(score.meterChanges());
        for (var tab : tabs)
            if (tab.standardTop() < 0) {
                var digits = new ArrayList<TablatureDecoder.Word>();
                for (var v : words)
                    if (v.text().length() == 1
                            && v.text().charAt(0) >= 0xe080
                            && v.text().charAt(0) <= 0xe089
                            && v.top() * h >= tab.top() - tab.gap()
                            && v.bottom() * h <= tab.bottom() + tab.gap()) digits.add(v);
                for (var a : digits)
                    for (var b : digits)
                        if (a.top() < b.top()
                                && Math.abs((a.left() + a.right() - b.left() - b.right()) * .5f * w)
                                        < tab.gap() * .4f) {
                            int numerator = a.text().charAt(0) - 0xe080,
                                    denominator = b.text().charAt(0) - 0xe080;
                            if (numerator < 1
                                    || !(denominator == 2 || denominator == 4 || denominator == 8))
                                continue;
                            float x = (a.left() + a.right()) * .5f,
                                    y = (tab.top() + tab.bottom()) * .5f / h;
                            for (int i = 0; i < score.measures().size(); i++) {
                                var m = score.measures().get(i);
                                if (x >= m.left()
                                        && x <= m.right()
                                        && y >= m.top()
                                        && y <= m.bottom()) {
                                    final int index = i;
                                    if (changes.stream().noneMatch(c -> c.measureIndex() == index))
                                        changes.add(
                                                new ScoreMeterChange(i, numerator, denominator));
                                    break;
                                }
                            }
                        }
            }
        changes.sort(Comparator.comparingInt(ScoreMeterChange::measureIndex));
        return TabTempo.apply(
                new ScorePageInterpretation(
                        score.measures(),
                        score.notes(),
                        score.firstMeasureNumber(),
                        score.keyChanges(),
                        score.tempoChanges(),
                        changes,
                        score.rests(),
                        score.techniqueChanges(),
                        score.dynamicChanges(),
                        score.playbackDirections(),
                        score.expressiveEvents()),
                tabs,
                words,
                w,
                h);
    }
}
