// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.regex.*;

/** Text-layer tab symbols, including overlapping matches returned by PDF search APIs. */
public final class TabTextSource {
    private TabTextSource() {}

    private static final class TokenPattern {
        private static final Pattern VALUE =
                Pattern.compile(
                        "<[0-9]{1,2}>|\\([0-9]{1,2}\\)|(?<![0-9])[0-9]{1,2}[bB][0-9]{1,2}(?:[rR][0-9]{1,2})?~*|(?<![0-9])(?:[0-9]{1,2}|[xX])(?:[/\\\\hHpP][0-9]{1,2})+~*|(?<![0-9])[0-9]{1,3}(?![0-9])~*|[xXHPT=]|[\\uE1E7\\uE241\\uE243\\uE245\\uE4E3-\\uE4E8\\uE4A2\\uEAB2\\uE080-\\uE089\\uECA5\\uECA6\\uECB7]");
    }

    private static final class DigitPattern {
        private static final Pattern VALUE = Pattern.compile("[0-9]");
    }

    public static List<String> tokens(String text) {
        var found = new LinkedHashSet<String>();
        var m = TokenPattern.VALUE.matcher(text);
        while (m.find()) found.add(m.group());
        for (char c = '0'; c <= '9'; c++)
            if (text.indexOf(c) >= 0) found.add(Character.toString(c));
        var result = new ArrayList<>(found);
        result.sort(Comparator.comparingInt(String::length).reversed());
        return List.copyOf(result);
    }

    public static List<TablatureDecoder.Word> disjoint(List<TablatureDecoder.Word> input) {
        var sorted = new ArrayList<>(input);
        sorted.sort(
                Comparator.comparingInt((TablatureDecoder.Word v) -> v.text().length()).reversed());
        var out = new ArrayList<TablatureDecoder.Word>();
        for (var v : sorted) {
            float area = (v.right() - v.left()) * (v.bottom() - v.top());
            boolean covered = false;
            for (var old : out) {
                float
                        x =
                                Math.max(
                                        0,
                                        Math.min(v.right(), old.right())
                                                - Math.max(v.left(), old.left())),
                        y =
                                Math.max(
                                        0,
                                        Math.min(v.bottom(), old.bottom())
                                                - Math.max(v.top(), old.top()));
                if (x * y > area * .7f) {
                    covered = true;
                    break;
                }
            }
            if (!covered) out.add(v);
        }
        var order = new HashMap<TablatureDecoder.Word, Integer>();
        for (int i = 0; i < input.size(); i++) order.putIfAbsent(input.get(i), i);
        out.sort(Comparator.comparingInt(order::get));
        return List.copyOf(out);
    }

    public static List<TablatureDecoder.Word> joinDigits(
            List<TablatureDecoder.Staff> tabs, List<TablatureDecoder.Word> input, int w, int h) {
        var words = new ArrayList<>(disjoint(input));
        for (var tab : tabs)
            for (int string = 0; string < tab.stringCount(); string++) {
                float cy = tab.top() + string * tab.gap();
                var lane = new ArrayList<TablatureDecoder.Word>();
                for (var v : words)
                    if (DigitPattern.VALUE.matcher(v.text()).matches()
                            && Math.abs((v.top() + v.bottom()) * .5f * h - cy) < tab.gap() * .35f)
                        lane.add(v);
                lane.sort(Comparator.comparingDouble(TablatureDecoder.Word::left));
                for (int i = 0; i + 1 < lane.size(); i++) {
                    var a = lane.get(i);
                    var b = lane.get(i + 1);
                    float gap = (b.left() - a.right()) * w;
                    int fret = Integer.parseInt(a.text() + b.text());
                    if (gap < 0
                            || gap > tab.gap() * .55f
                            || (b.right() - a.left()) * w > tab.gap() * 1.65f
                            || fret > 36
                            || a.text().equals("0")) continue;
                    if (Math.abs((a.top() + a.bottom() - b.top() - b.bottom()) * .5f * h)
                            > tab.gap() * .15f) continue;
                    words.remove(a);
                    words.remove(b);
                    words.add(
                            new TablatureDecoder.Word(
                                    a.text() + b.text(),
                                    a.left(),
                                    Math.min(a.top(), b.top()),
                                    b.right(),
                                    Math.max(a.bottom(), b.bottom())));
                    i++;
                }
            }
        return List.copyOf(words);
    }
}
