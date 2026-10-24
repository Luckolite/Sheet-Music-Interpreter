// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Stable lowercase evidence digests without newer Android-only formatter APIs. */
final class RasterEvidenceIdentity {
    private RasterEvidenceIdentity() {}

    static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        String digits = "0123456789abcdef";
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 255;
            out[2 * i] = digits.charAt(value >>> 4);
            out[2 * i + 1] = digits.charAt(value & 15);
        }
        return new String(out);
    }
}
