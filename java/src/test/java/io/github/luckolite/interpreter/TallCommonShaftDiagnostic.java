// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original geometric ovals and shaft, generated without commercial score pixels. */
public final class TallCommonShaftDiagnostic {
    static final int W = 320, H = 360, G = 16;
    final byte[] labels = new byte[W * H], gray = new byte[W * H];
    final List<Object> notes = new ArrayList<>();
    final Class<?> component, detected;

    TallCommonShaftDiagnostic(int spread, boolean broken, boolean opposite) throws Exception {
        Arrays.fill(gray, (byte) 255);
        component = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        detected = Class.forName(OmrScoreInterpreter.class.getName() + "$DetectedNote");
        var hc = component.getDeclaredConstructors()[0];
        hc.setAccessible(true);
        var nc = detected.getDeclaredConstructors()[0];
        nc.setAccessible(true);
        int[] ys = {90, 106, 90 + spread};
        for (int i = 0; i < ys.length; i++) {
            int cy = ys[i];
            int area = 0;
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = 100; x <= 120; x++)
                    if (Math.pow((x - 110) / 10d, 2) + Math.pow((y - cy) / 7d, 2) <= 1) {
                        pixel(x, y, 2);
                        area++;
                    }
            var e =
                    new ScoreNoteEvent(
                            2,
                            .4f,
                            10 - (cy - 90) / 8,
                            0,
                            2,
                            cy / (float) H,
                            false,
                            0,
                            i == 0 ? 0 : 1,
                            2,
                            i == 0 ? 1f : 0f);
            Object head = hc.newInstance(area, 100, 120, cy - 7, cy + 7, 110f, (float) cy);
            notes.add(nc.newInstance(e, head, (float) G));
        }
        int end = ys[2] + 54;
        rect(100, 90, 2, end - 89, 1);
        rect(100, end - 5, 80, 6, 1);
        if (broken)
            for (int y = 135; y < 145; y++)
                for (int x = 100; x < 102; x++) {
                    gray[y * W + x] = (byte) 255;
                    labels[y * W + x] = 0;
                }
        if (opposite) {
            rect(120, 35, 2, ys[2] - 34, 1);
            rect(120, 35, 70, 6, 1);
        }
    }

    void pixel(int x, int y, int label) {
        labels[y * W + x] = (byte) label;
        gray[y * W + x] = 0;
    }

    void rect(int x, int y, int w, int h, int label) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) pixel(xx, yy, label);
    }

    List<ScoreNoteEvent> run() throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "reconcileSingleShaftChords",
                        List.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        var e = detected.getDeclaredMethod("event");
        e.setAccessible(true);
        List<ScoreNoteEvent> out = new ArrayList<>();
        for (Object n : (List<?>) m.invoke(null, notes, labels, gray, W, H))
            out.add((ScoreNoteEvent) e.invoke(n));
        return out;
    }

    public static void main(String[] args) throws Exception {
        for (int spread : new int[] {64, 80, 96, 112})
            for (boolean broken : new boolean[] {false, true}) {
                var d = new TallCommonShaftDiagnostic(spread, broken, false);
                var a = d.labels.clone();
                var b = d.gray.clone();
                var result = d.run();
                if (!Arrays.equals(a, d.labels) || !Arrays.equals(b, d.gray))
                    throw new AssertionError("caller pixels changed");
                System.out.println(
                        "spread="
                                + spread
                                + " gaps="
                                + (spread / (float) G)
                                + " broken="
                                + broken
                                + " beams="
                                + result.stream().map(ScoreNoteEvent::beamCount).toList());
            }
        var result = new TallCommonShaftDiagnostic(96, false, true).run();
        System.out.println(
                "opposing=true beams=" + result.stream().map(ScoreNoteEvent::beamCount).toList());
    }
}
