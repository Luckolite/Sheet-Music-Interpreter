// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.HashMap;
import java.util.Map;

/** One immutable page's exact crop readings; bounds and logical votes remain unchanged. */
public final class ExactCoverCropCache<T> {
    private record Bounds(int left, int top, int right, int bottom) {}

    private final Map<Bounds, T> readings = new HashMap<>();

    @FunctionalInterface
    public interface Reader<T> {
        T read() throws Exception;
    }

    public T read(int left, int top, int right, int bottom, Reader<T> reader) throws Exception {
        var key = new Bounds(left, top, right, bottom);
        if (readings.containsKey(key)) return readings.get(key);
        T result = reader.read();
        readings.put(key, result);
        return result;
    }
}
