// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

public final class RasterIdentityCompatibilityControls {
    static int checks;

    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) throws Exception {
        byte[] all = new byte[256];
        for (int i = 0; i < all.length; i++) all[i] = (byte) i;
        byte[] before = all.clone();
        check(
                RasterEvidenceIdentity.hex(all).equals(java.util.HexFormat.of().formatHex(all)),
                "every_unsigned_byte_preserves_prior_JVM_formatter_identity");
        check(java.util.Arrays.equals(all, before), "digest_formatting_never_mutates_bytes");
        check(RasterEvidenceIdentity.hex(new byte[0]).isEmpty(), "empty_digest_identity");
        byte[] abc =
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        check(
                RasterEvidenceIdentity.hex(abc)
                        .equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
                "standard_SHA256_literal_unchanged");
        System.out.println("identityCompatibilityControls=" + checks + "/" + checks);
    }
}
