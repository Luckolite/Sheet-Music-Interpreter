// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import java.math.BigDecimal;

/** Namespace-independent canonical snapshot for original synthetic Analysis regression checks. */
final class OriginalBeamAnalysisSnapshot {
    static String json(Object value) throws Exception {
        if (value == null) return "null";
        if (value instanceof Number n) {
            String s = n.toString();
            return s.equals("NaN") || s.contains("Infinity")
                    ? s
                    : new BigDecimal(s).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean) return value.toString();
        if (value instanceof String || value instanceof Enum<?>) return quote(value.toString());
        if (value instanceof Optional<?> v) return json(v.orElse(null));
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            for (var e : map.entrySet()) sorted.put(String.valueOf(e.getKey()), e.getValue());
            return fields(sorted);
        }
        if (value instanceof Iterable<?> items) {
            var s = new StringJoiner(",", "[", "]");
            for (Object v : items) s.add(json(v));
            return s.toString();
        }
        if (value.getClass().isArray()) {
            var s = new StringJoiner(",", "[", "]");
            for (int i = 0; i < Array.getLength(value); i++) s.add(json(Array.get(value, i)));
            return s.toString();
        }
        var sorted = new TreeMap<String, Object>();
        if (value.getClass().isRecord()) {
            for (var c : value.getClass().getRecordComponents()) {
                var m = c.getAccessor();
                m.setAccessible(true);
                sorted.put(c.getName(), m.invoke(value));
            }
        } else
            for (var f : value.getClass().getDeclaredFields())
                if (!Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    sorted.put(f.getName(), f.get(value));
                }
        return fields(sorted);
    }

    private static String fields(Map<String, Object> map) throws Exception {
        var s = new StringJoiner(",", "{", "}");
        for (var e : map.entrySet()) s.add(quote(e.getKey()) + ":" + json(e.getValue()));
        return s.toString();
    }

    private static String quote(String s) {
        return "\""
                + s.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                        .replace("\b", "\\b")
                        .replace("\f", "\\f")
                + "\"";
    }
}
