// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Original candidate. Numbering follows emitted document order, not musical overlap. */
final class HairpinNumbering {
    private record Endpoint(int measure, int index, boolean stop) {}

    static int[] assign(List<ScoreDynamicChange> dynamics) {
        var endpoints = new ArrayList<Endpoint>();
        for (int i = 0; i < dynamics.size(); i++) {
            var d = dynamics.get(i);
            if (d.direction() == 0) continue;
            endpoints.add(new Endpoint(d.measureIndex(), i, false));
            endpoints.add(new Endpoint(d.endMeasureIndex(), i, true));
        }
        endpoints.sort(
                Comparator.comparingInt(Endpoint::measure)
                        .thenComparingInt(Endpoint::index)
                        .thenComparing(Endpoint::stop));
        int[] result = new int[dynamics.size()];
        boolean[] occupied = new boolean[17];
        for (var e : endpoints) {
            if (e.stop()) {
                occupied[result[e.index()]] = false;
            } else {
                int number = 1;
                while (number <= 16 && occupied[number]) number++;
                if (number > 16)
                    throw new IllegalArgumentException(
                            "MusicXML supports at most 16 concurrent hairpins");
                occupied[number] = true;
                result[e.index()] = number;
            }
        }
        return result;
    }
}
