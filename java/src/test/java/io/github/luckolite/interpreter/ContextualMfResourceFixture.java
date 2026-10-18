// Copyright 2026 Luckolite; SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;
import org.junit.Test;

final class ContextualMfResourceFixture {
    static byte[] read(String path) throws IOException {
        try (InputStream raw =
                ContextualMfResourceFixture.class.getResourceAsStream(
                        "/contextual-mf-originals/" + path)) {
            if (raw == null) throw new FileNotFoundException(path);
            try (GZIPInputStream zipped = new GZIPInputStream(raw)) {
                byte[] g = zipped.readAllBytes();
                assertEquals("authored raster dimensions", 700 * 420, g.length);
                return g;
            }
        }
    }
}
