// Copyright 2026 Music Sheets contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.IOException;

/** Bounded shared State3 scalar grammar, independent of either JSON library. */
final class CurrentRestWrittenFields {
    private CurrentRestWrittenFields() {}

    record Row(int owner, CurrentRestWritten.Placement clock) {}

    static Row row(String index, String onset, String duration) throws IOException {
        if (index == null
                || index.isEmpty()
                || index.length() > 6
                || (index.length() > 1 && index.charAt(0) == '0'))
            throw new IOException("Noncanonical Current rest owner");
        int owner = 0;
        for (int i = 0; i < index.length(); i++) {
            char c = index.charAt(i);
            if (c < '0' || c > '9') throw new IOException("Invalid Current rest owner");
            owner = owner * 10 + c - '0';
        }
        try {
            return new Row(owner, new CurrentRestWritten.Placement(bits(onset), bits(duration)));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid Current rest OWN domain", invalid);
        }
    }

    private static double bits(String text) throws IOException {
        if (text == null || text.length() != 16)
            throw new IOException("Current rest OWN bits require 16 lowercase hex digits");
        long raw = 0;
        for (int i = 0; i < 16; i++) {
            char c = text.charAt(i);
            int nibble = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10 : -1;
            if (nibble < 0) throw new IOException("Noncanonical Current rest OWN bits");
            raw = (raw << 4) | nibble;
        }
        return Double.longBitsToDouble(raw);
    }
}
