// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class PrintedConnectorContactTest {
    @org.junit.Test
    public void originalGeneratedControls() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        int checks = 0;
        for (int gap : new int[] {8, 12, 16})
            for (int offset : new int[] {0, 1, 2}) {
                int w = 700,
                        h = 500,
                        top = 70,
                        lower = top + gap * 10,
                        end = 520,
                        x = offset == 0 ? end : end + gap * 2 + offset - 1;
                byte[] labels = new byte[w * h], gray = new byte[w * h];
                Arrays.fill(gray, (byte) 210);
                for (int y0 : new int[] {top, lower}) {
                    for (int line = 0; line < 5; line++)
                        for (int xx = 60; xx <= x; xx++) {
                            labels[(y0 + line * gap) * w + xx] = OmrMeasurePostProcessor.STAFF;
                            if (xx <= end) gray[(y0 + line * gap) * w + xx] = 0;
                        }
                    for (int xx : new int[] {60, 280, end, x})
                        for (int y = y0; y <= y0 + gap * 4; y++) {
                            labels[y * w + xx] = OmrMeasurePostProcessor.STEM_OR_REST;
                            if (xx <= end) gray[y * w + xx] = 0;
                        }
                }
                for (int y = top; y <= lower + gap * 4; y++) gray[y * w + x] = 0;
                var result = OmrMeasurePostProcessor.process(labels, gray, w, h);
                boolean joined =
                        result.stream()
                                .anyMatch(
                                        m ->
                                                m.top() * h < top + gap
                                                        && m.bottom() * h > lower + gap * 3);
                if (joined != (offset == 0))
                    throw new AssertionError(
                            "gap=" + gap + " offset=" + offset + " result=" + result);
                checks++;
            }
        System.out.println(
                "PASS " + checks + " original printed-contact versus detached-art controls");
    }
}
