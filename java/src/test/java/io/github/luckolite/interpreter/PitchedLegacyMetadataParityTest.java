// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original full-record golden output from the released 22-field implementation. */
public class PitchedLegacyMetadataParityTest {
    private String capture(boolean constructorProbe) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var prior = System.out;
        try (var out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(out);
            if (constructorProbe) LegacyNoteContractProbe.main(new String[0]);
            else Pitched902HelperParityProbe.main(new String[0]);
        } finally {
            System.setOut(prior);
        }
        return bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    public void all34LegacyConstructorOutputsRetainEvery22FieldAndPitchIdentity() throws Exception {
        try (var in = getClass().getResourceAsStream("legacy22-constructors.txt")) {
            assertNotNull(in);
            assertEquals(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n"),
                    capture(true));
        }
    }

    @Test
    public void all48PitchedHelperOutputsRetainMetadataAndSourceIndices() throws Exception {
        assertEquals(
                "fixtures=48 sha256=2e28726b9662c0f5c475ad2a5e690538876ad1f28ced94ffbc8721903d198ce8",
                capture(false).lines().findFirst().orElseThrow());
    }
}
