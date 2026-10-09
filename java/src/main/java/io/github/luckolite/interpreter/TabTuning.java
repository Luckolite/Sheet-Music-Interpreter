// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.regex.*;

/** Explicit low-to-high guitar tuning headers. Returned MIDI values are top-string first. */
public final class TabTuning {
    private TabTuning() {}

    public static List<Integer> parse(String text, int strings) {
        text = text.replaceAll("(?<=[A-Ga-g])[\\p{Zs}\\t]+(?=[#♯♭])", "");
        var m =
                Pattern.compile(
                                "(?i)\\btuning\\s*[:=]?\\s*((?:[A-G](?:[#♯b♭])?[0-9]?\\s+){5,6}[A-G](?:[#♯b♭])?[0-9]?)(?![A-Za-z0-9])")
                        .matcher(text);
        if (!m.find()) return List.of();
        String[] names = m.group(1).trim().split("\\s+");
        if (names.length != strings) return List.of();
        var result = new ArrayList<Integer>();
        int above = 76;
        for (int i = names.length - 1; i >= 0; i--) {
            var note = Pattern.compile("(?i)([A-G])([#♯b♭]?)([0-9]?)").matcher(names[i]);
            if (!note.matches()) return List.of();
            int pc =
                    switch (note.group(1).toUpperCase(Locale.ROOT)) {
                        case "C" -> 0;
                        case "D" -> 2;
                        case "E" -> 4;
                        case "F" -> 5;
                        case "G" -> 7;
                        case "A" -> 9;
                        default -> 11;
                    };
            if (note.group(2).equals("#") || note.group(2).equals("♯")) pc++;
            else if (!note.group(2).isEmpty()) pc--;
            int midi;
            if (!note.group(3).isEmpty()) midi = (Integer.parseInt(note.group(3)) + 1) * 12 + pc;
            else if (i == names.length - 1) {
                pc = Math.floorMod(pc, 12);
                midi = 60 + pc;
                while (midi > 69) midi -= 12;
            } else {
                pc = Math.floorMod(pc, 12);
                midi = (above / 12) * 12 + pc;
                while (midi >= above) midi -= 12;
            }
            if (midi < 21 || midi > 88 || midi >= above) return List.of();
            result.add(midi);
            above = midi;
        }
        return List.copyOf(result);
    }

    public static List<TablatureDecoder.Staff> apply(
            List<TablatureDecoder.Staff> tabs, List<TablatureDecoder.Word> words, int w, int h) {
        if (tabs.isEmpty()) return tabs;
        float limit = tabs.get(0).top() - tabs.get(0).gap();
        var header = new ArrayList<TablatureDecoder.Word>();
        for (var word : words) if (word.bottom() * h < limit) header.add(word);
        header.sort(
                Comparator.comparingDouble(TablatureDecoder.Word::top)
                        .thenComparingDouble(TablatureDecoder.Word::left));
        // Group by baseline before ordering left-to-right: accidental glyph boxes can be taller.
        StringBuilder text = new StringBuilder();
        while (!header.isEmpty()) {
            var first = header.remove(0);
            var line = new ArrayList<TablatureDecoder.Word>();
            line.add(first);
            for (var it = header.iterator(); it.hasNext(); ) {
                var next = it.next();
                if (Math.abs((next.top() + next.bottom() - first.top() - first.bottom()) * .5f)
                        < Math.max(first.bottom() - first.top(), next.bottom() - next.top())
                                * .65f) {
                    line.add(next);
                    it.remove();
                }
            }
            line.sort(Comparator.comparingDouble(TablatureDecoder.Word::left));
            for (var word : line) text.append(word.text()).append(' ');
            text.append('\n');
        }
        return withHeader(tabs, text.toString());
    }

    public static List<TablatureDecoder.Staff> withHeader(
            List<TablatureDecoder.Staff> tabs, String text) {
        var result = new ArrayList<TablatureDecoder.Staff>();
        for (var t : tabs) {
            var tuning = parse(text, t.stringCount());
            result.add(
                    tuning.isEmpty()
                            ? t
                            : new TablatureDecoder.Staff(
                                    t.top(),
                                    t.gap(),
                                    t.standardTop(),
                                    t.frets(),
                                    t.bars(),
                                    t.stringCount(),
                                    tuning));
        }
        return List.copyOf(result);
    }
}
