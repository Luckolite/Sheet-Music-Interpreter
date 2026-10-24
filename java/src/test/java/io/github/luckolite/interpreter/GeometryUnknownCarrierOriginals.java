// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class GeometryUnknownCarrierOriginals {
    public static void main(String[] args) {
        int w = 64, h = 96;
        byte[] raw = new byte[w * h];
        Arrays.fill(raw, (byte) 255);
        for (int x = 12; x <= 48; x++) for (int y = 24; y <= 29; y++) raw[y * w + x] = 0;
        for (int x : new int[] {12, 48}) for (int y = 24; y <= 80; y++) raw[y * w + x] = 0;
        var known =
                new FiniteRawBeamStrip.Carrier() {
                    public boolean at(int x, int y) {
                        return false;
                    }
                };
        var unknown =
                new FiniteRawBeamStrip.Carrier() {
                    public boolean at(int x, int y) {
                        return false;
                    }

                    public boolean unknown(int x, int y) {
                        return y >= 24 && y <= 29;
                    }
                };
        var outside =
                new FiniteRawBeamStrip.Carrier() {
                    public boolean at(int x, int y) {
                        return false;
                    }

                    public boolean unknown(int x, int y) {
                        return y >= 40;
                    }
                };
        if (FiniteRawBeamStrip.prove(raw, known, w, h, 14, 46, 24, 24, 16, -1, 0, 0) == null)
            throw new AssertionError("Known core lost");
        if (FiniteRawBeamStrip.prove(raw, unknown, w, h, 14, 46, 24, 24, 16, -1, 0, 0) != null)
            throw new AssertionError("Unknown core subsidized proof");
        if (FiniteRawBeamStrip.prove(raw, outside, w, h, 14, 46, 24, 24, 16, -1, 0, 0) == null)
            throw new AssertionError("Unselected unknown aborted proof");
        var columns =
                List.of(
                        new PrintedBeamGeometry.Column(0, 0, 0, 1, -1, 12, 12, 24, 16, 1),
                        new PrintedBeamGeometry.Column(1, 0, 0, 1, -1, 48, 48, 24, 16, 1));
        var unknownContact =
                new PrintedBeamGeometry.CarrierPlane() {
                    public boolean at(
                            PrintedBeamGeometry.Column a,
                            PrintedBeamGeometry.Column b,
                            int x,
                            int y) {
                        return false;
                    }

                    public boolean unknown(
                            PrintedBeamGeometry.Column a,
                            PrintedBeamGeometry.Column b,
                            int x,
                            int y) {
                        return x == 13;
                    }
                };
        if (!PrintedBeamGeometry.detect(
                        raw, w, h, columns, unknownContact, new PrintedBeamGeometry.Budget(1000000))
                .groups()
                .isEmpty()) throw new AssertionError("Unknown terminal connector admitted");
        var unknownElsewhere =
                new PrintedBeamGeometry.CarrierPlane() {
                    public boolean at(
                            PrintedBeamGeometry.Column a,
                            PrintedBeamGeometry.Column b,
                            int x,
                            int y) {
                        return false;
                    }

                    public boolean unknown(
                            PrintedBeamGeometry.Column a,
                            PrintedBeamGeometry.Column b,
                            int x,
                            int y) {
                        return y >= 40;
                    }
                };
        if (PrintedBeamGeometry.detect(
                                raw,
                                w,
                                h,
                                columns,
                                unknownElsewhere,
                                new PrintedBeamGeometry.Budget(1000000))
                        .groups()
                        .size()
                != 1) throw new AssertionError("Unknown outside selected proof aborted page");
        System.out.println("{\"status\":\"UNKNOWN_CARRIER_ORIGINALS_PASS\",\"assertions\":5}");
    }
}
