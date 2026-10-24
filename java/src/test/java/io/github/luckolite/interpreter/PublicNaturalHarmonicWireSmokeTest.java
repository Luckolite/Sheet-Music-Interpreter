// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class PublicNaturalHarmonicWireSmokeTest {
    @Test
    public void original() throws Exception {
        Path output = Files.createTempFile("natural-harmonic-wire-", ".bin");
        try {
            PublicJavaNaturalHarmonicWireSmoke.main(new String[] {output.toString()});
        } finally {
            Files.deleteIfExists(output);
        }
    }
}
