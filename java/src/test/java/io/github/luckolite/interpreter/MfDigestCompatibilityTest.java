// Copyright 2026 Luckolite; SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.Test;

/** JDK-only reference remains test-only; production needs no API34 HexFormat. */
public final class MfDigestCompatibilityTest {
    @Test
    public void allBytesAndKnownShaDigestsMatchJdkReference() throws Exception {
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) all[i] = (byte) i;
        for (Class<?> c :
                new Class<?>[] {DynamicNotationRawContext.class, ContextualMfBodyEvidence.class}) {
            var hex = c.getDeclaredMethod("lowerHex", byte[].class);
            hex.setAccessible(true);
            assertEquals(HexFormat.of().formatHex(all), hex.invoke(null, (Object) all));
            var digest = c.getDeclaredMethod("digest", byte[].class);
            digest.setAccessible(true);
            byte[][] inputs = {
                new byte[0], "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8), all
            };
            for (byte[] in : inputs)
                assertEquals(
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in)),
                        digest.invoke(null, (Object) in));
            assertEquals(
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    digest.invoke(null, (Object) new byte[0]));
            assertEquals(
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    digest.invoke(
                            null,
                            (Object) "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }
}
