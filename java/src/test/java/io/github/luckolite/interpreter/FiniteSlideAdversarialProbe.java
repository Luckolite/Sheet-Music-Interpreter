// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original adversarial geometry. No score pixels or song identities. */
public class FiniteSlideAdversarialProbe {
    static void ellipse(
            FiniteConnectedSlidePortsOriginalChecks.Scene s,
            int l,
            int r,
            float leftY,
            float rightY,
            float radius) {
        float middle = (l + r) / 2f, rx = (r - l) / 2f;
        for (int x = l; x <= r; x++) {
            float t = (x - l) / (float) (r - l), cy = leftY + (rightY - leftY) * t;
            float vertical =
                    (float)
                            (radius
                                    * Math.sqrt(
                                            Math.max(
                                                    0,
                                                    1 - (x - middle) * (x - middle) / (rx * rx))));
            for (int y = (int) Math.ceil(cy - vertical); y <= Math.floor(cy + vertical); y++)
                s.gray[y * s.w + x] = 35;
        }
    }

    static void loop(
            FiniteConnectedSlidePortsOriginalChecks.Scene s,
            int l,
            int r,
            float leftY,
            float rightY,
            float radius) {
        float middle = (l + r) / 2f, rx = (r - l) / 2f;
        for (int x = l; x <= r; x++) {
            float t = (x - l) / (float) (r - l), cy = leftY + (rightY - leftY) * t;
            float v =
                    (float)
                            (radius
                                    * Math.sqrt(
                                            Math.max(
                                                    0,
                                                    1 - (x - middle) * (x - middle) / (rx * rx))));
            s.gray[Math.round(cy - v) * s.w + x] = 35;
            s.gray[Math.round(cy + v) * s.w + x] = 35;
        }
    }

    public static void main(String[] args) {
        for (int scale : new int[] {1, 2, 3})
            for (int dir : new int[] {-1, 1}) {
                var s = new FiniteConnectedSlidePortsOriginalChecks.Scene(scale, false, dir, -dir);
                ellipse(s, 132 * scale, 160 * scale, 114.1f * scale, 116.9f * scale, 1.5f * scale);
                System.out.println(
                        "filledClosedBody scale="
                                + scale
                                + " opposed="
                                + dir
                                + " port="
                                + s.port()
                                + " all="
                                + s.classify());
                s = new FiniteConnectedSlidePortsOriginalChecks.Scene(scale, false, dir, -dir);
                loop(s, 132 * scale, 160 * scale, 114.1f * scale, 116.9f * scale, 1.5f * scale);
                System.out.println(
                        "hollowClosedBody scale="
                                + scale
                                + " opposed="
                                + dir
                                + " port="
                                + s.port()
                                + " all="
                                + s.classify());
            }
        for (int scale : new int[] {1, 2, 3}) {
            var s = new FiniteConnectedSlidePortsOriginalChecks.Scene(scale, true, -1, 1);
            s.line();
            for (int x = 145 * scale; x <= 147 * scale; x++)
                for (int y = 80 * scale; y <= 150 * scale; y++) s.gray[y * s.w + x] = 35;
            System.out.println(
                    "unknownTallBridge scale="
                            + scale
                            + " port="
                            + s.port()
                            + " all="
                            + s.classify());
        }
    }
}
