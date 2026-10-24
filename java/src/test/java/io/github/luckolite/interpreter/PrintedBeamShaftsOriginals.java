// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

public final class PrintedBeamShaftsOriginals {
    static int count;

    static void require(boolean value, String name) {
        count++;
        if (!value) throw new AssertionError(name);
    }

    static byte[] blank() {
        byte[] raw = new byte[160 * 260];
        Arrays.fill(raw, (byte) 255);
        return raw;
    }

    static void line(byte[] raw, int x, int first, int last) {
        for (int y = first; y <= last; y++) raw[y * 160 + x] = 30;
    }

    static java.util.List<PrintedBeamShafts.Shaft> at(byte[] raw, float x, float y, float gap) {
        return PrintedBeamShafts.at(raw, 160, 260, x, y, gap, new PrintedBeamShafts.Budget(100000));
    }

    public static void main(String[] args) {
        byte[] raw = blank();
        line(raw, 60, 82, 140);
        line(raw, 61, 82, 142);
        var found = at(raw, 67, 80, 12);
        require(found.size() == 1, "one literal down shaft");
        require(
                found.get(0).endY() == 142 && found.get(0).columns().size() == 2,
                "two-pixel cap difference retained");
        require(found.get(0).columns().get(0).endY() == 140, "individual cap is not overwritten");
        byte[] copy = raw.clone();
        at(raw, 67, 80, 12);
        require(Arrays.equals(raw, copy), "raw immutable");
        raw = blank();
        line(raw, 60, 32, 118);
        line(raw, 61, 30, 118);
        found = at(raw, 53, 120, 12);
        require(
                found.size() == 1 && found.get(0).direction() == -1 && found.get(0).endY() == 30,
                "mirror up shaft");
        raw = blank();
        line(raw, 60, 82, 175);
        line(raw, 61, 82, 173);
        found = at(raw, 67, 80, 12);
        require(
                found.size() == 1 && found.get(0).endY() == 175,
                "ordinary long stem beyond seven gaps");
        raw = blank();
        line(raw, 60, 82, 140);
        line(raw, 61, 82, 140);
        raw[110 * 160 + 60] = (byte) 255;
        require(at(raw, 67, 80, 12).size() == 1, "neighboring path carries single-column hole");
        raw[110 * 160 + 61] = (byte) 255;
        require(at(raw, 67, 80, 12).isEmpty(), "whole white row never filled");
        raw = blank();
        line(raw, 60, 82, 140);
        line(raw, 62, 82, 140);
        require(at(raw, 67, 80, 12).isEmpty(), "disjoint rival columns reject");
        raw = blank();
        for (int x = 58; x <= 63; x++) line(raw, x, 82, 140);
        require(at(raw, 67, 80, 12).isEmpty(), "broad stroke rejects");
        raw = blank();
        line(raw, 60, 82, 240);
        line(raw, 61, 82, 240);
        require(at(raw, 67, 80, 12).isEmpty(), "truncated unbounded stroke is not a cap");
        raw = blank();
        line(raw, 60, 82, 140);
        line(raw, 61, 82, 140);
        require(
                PrintedBeamShafts.at(raw, 160, 260, 67, 80, 12, new PrintedBeamShafts.Budget(0))
                        .isEmpty(),
                "budget refusal preserves unknown");
        raw = blank();
        line(raw, 60, 122, 190);
        line(raw, 61, 122, 190);
        line(raw, 74, 30, 118);
        line(raw, 75, 30, 118);
        require(at(raw, 67, 120, 12).size() == 2, "opposing rivals retained, no direction picked");
        var partial = new PrintedBeamShafts.Budget(2000);
        require(
                PrintedBeamShafts.at(raw, 160, 260, 67, 120, 12, partial).isEmpty(),
                "partial rival scan cannot become a singleton");
        require(partial.refused(), "refusal is explicit for whole-page caller");
        require(
                PrintedBeamShafts.at(raw, 160, 260, 67, 120, 12, partial).isEmpty(),
                "refusal remains sticky");
        byte[] head = new byte[100 * 100];
        Arrays.fill(head, (byte) 255);
        for (int y = 46; y <= 54; y++)
            for (int x = 44; x <= 56; x++)
                if ((x - 50) * (x - 50) / 36.0 + (y - 50) * (y - 50) / 16.0 <= 1)
                    head[y * 100 + x] = 0;
        for (int y = 20; y <= 50; y++) head[y * 100 + 56] = 0;
        for (int y = 50; y <= 80; y++) head[y * 100 + 44] = 0;
        require(
                PrintedBeamShafts.at(
                                        head,
                                        100,
                                        100,
                                        50,
                                        50,
                                        10,
                                        new PrintedBeamShafts.Budget(8_000_000))
                                .size()
                        == 2,
                "independent filled-head opposed rivals");
        var incomplete = new PrintedBeamShafts.Budget(480);
        require(
                PrintedBeamShafts.at(head, 100, 100, 50, 50, 10, incomplete).isEmpty(),
                "independent partial filled-head scan stays unknown");
        require(incomplete.refused(), "independent whole-page refusal evidence");
        System.out.println("PASS " + count + " raw shaft assertions");
    }
}
